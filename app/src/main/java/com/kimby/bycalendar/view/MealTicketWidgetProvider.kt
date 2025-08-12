package com.kimby.bycalendar.view

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.kimby.bycalendar.R
import com.kimby.bycalendar.database.PhotoDatabase
import com.kimby.bycalendar.widget.MidnightTickReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MealTicketWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_MARK_USED = "com.kimby.bycalendar.ACTION_MARK_USED"
        const val ACTION_SHOW_CONFIRM_DIALOG = "com.kimby.bycalendar.ACTION_SHOW_CONFIRM_DIALOG"
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val ACTION_SHOW_PREVIOUS = "com.kimby.bycalendar.ACTION_SHOW_PREVIOUS"
        const val ACTION_SHOW_NEXT = "com.kimby.bycalendar.ACTION_SHOW_NEXT"
        const val ACTION_MIDNIGHT_TICK = "com.example.ACTION_MIDNIGHT_TICK"
        private const val REQ_CODE_MIDNIGHT = 1001
        private var widgetIndexMap = mutableMapOf<Int, Int>()

        // 날짜 바뀌면 인덱스 초기화 용(선택)
        private var lastShownDate: LocalDate? = null

        @SuppressLint("ServiceCast")
        fun scheduleNextMidnight(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                context,
                REQ_CODE_MIDNIGHT,
                Intent(ACTION_MIDNIGHT_TICK).setClass(context, MidnightTickReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = nextMidnightMillis()
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                // 정확 권한 없으면 근사 예약
                am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 15 * 60_000L, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }

        fun cancelMidnightAlarm(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                context,
                REQ_CODE_MIDNIGHT,
                Intent(ACTION_MIDNIGHT_TICK).setClass(context, MidnightTickReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
        }

        private fun nextMidnightMillis(): Long {
            val now = ZonedDateTime.now()
            val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            return next.toInstant().toEpochMilli()
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 기존 로직 유지
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        // 자정 예약 보강
        scheduleNextMidnight(context)
    }

    override fun onReceive(context: Context, intent: Intent) { 
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_MARK_USED -> {
                val uri = intent.getStringExtra(EXTRA_IMAGE_URI)?.toUri() ?: return
                markImageAsUsed(context, uri)
            }
            ACTION_SHOW_CONFIRM_DIALOG -> {
                val uriStr = intent.getStringExtra(EXTRA_IMAGE_URI) ?: return
                val dialogIntent = Intent(context, WidgetConfirmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(EXTRA_IMAGE_URI, uriStr)
                }
                context.startActivity(dialogIntent)
            }
            ACTION_SHOW_PREVIOUS, ACTION_SHOW_NEXT -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val direction = if (intent.action == ACTION_SHOW_PREVIOUS) -1 else 1
                    updateAppWidget(
                        context,
                        AppWidgetManager.getInstance(context),
                        appWidgetId,
                        direction
                    )
                }
            }
        }
    }

    fun resizeBitmapToFitWidget(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        // RemoteViews 제한: 1장의 최대 크기는 약 1.5MB 정도로 추정됨
        val maxPixels = maxWidth * maxHeight

        // 원본 크기 가져오기
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        // 축소 비율 계산 (비율 유지)
        val widthRatio = maxWidth.toFloat() / originalWidth
        val heightRatio = maxHeight.toFloat() / originalHeight
        val ratio = minOf(widthRatio, heightRatio, 1.0f) // 확대는 방지

        // 최종 크기 계산
        val newWidth = (originalWidth * ratio).toInt().coerceAtLeast(1)
        val newHeight = (originalHeight * ratio).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun markImageAsUsed(context: Context, uri: Uri) {
        val file = File(uri.path ?: return)
        if (!file.exists()) return

        val baseBitmap = BitmapFactory.decodeFile(file.absolutePath)
        val mutableBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        // 오버레이 이미지 불러오기
        val overlay = BitmapFactory.decodeResource(context.resources, R.drawable.used_stamp_red)

        // 오버레이를 원본 이미지 너비의 60% 크기의 정사각형으로 조정
        val overlaySize = (baseBitmap.width * 0.6f).toInt()
        val scaledOverlay = Bitmap.createScaledBitmap(overlay, overlaySize, overlaySize, true)

        // 원본 이미지 중앙에 위치
        val left = (baseBitmap.width - overlaySize) / 2f
        val top = (baseBitmap.height - overlaySize) / 2f
        canvas.drawBitmap(scaledOverlay, left, top, null)

        // ✅ 2. 현재 시간 가져오기
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val now = LocalDateTime.now().format(formatter)
        val message = "사용완료 : $now"

        // ✅ 3. 텍스트 페인트 설정
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = baseBitmap.width * 0.06f  // 이미지 너비 기준으로 글자 크기 설정
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(5f, 3f, 3f, Color.WHITE) // 글자 가독성 향상용 그림자
            isAntiAlias = true
        }

        // ✅ 4. 텍스트 위치 계산 (우측 하단 기준 padding)
        val padding = 20f
        val textWidth = paint.measureText(message)
        val x = baseBitmap.width - textWidth - padding
        val y = baseBitmap.height - padding

        // ✅ 5. 텍스트 그리기
        canvas.drawText(message, x, y, paint)

        // 새 파일로 저장
        val newFile = File(file.parent, file.nameWithoutExtension + "_used.jpg")
        FileOutputStream(newFile).use {
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }

        // DB 경로 갱신
        CoroutineScope(Dispatchers.IO).launch {
            val dao = PhotoDatabase.getDatabase(context).photoDao()
            dao.updatePhotoPathAndUsed(file.absolutePath, newFile.absolutePath)
            withContext(Dispatchers.Main) {
                // 위젯 갱신
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(
                        context,
                        MealTicketWidgetProvider::class.java
                    )
                )
                ids.forEach {
                    updateAppWidget(context, manager, it)
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            // 삭제 시 처리할 로직 필요 시 구현
        }
    }

    override fun onEnabled(context: Context) {
        scheduleNextMidnight(context)
    }

    override fun onDisabled(context: Context) {
        // 모든 위젯이 제거될 때 알람 해제
        cancelMidnightAlarm(context)
    }

    @SuppressLint("RemoteViewLayout")
    internal fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        direction: Int = 0
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_meal_ticket)
        val today = LocalDate.now().toString()
        val photoDao = PhotoDatabase.getDatabase(context).photoDao()
        // 날짜 바뀌면 인덱스 리셋
        if (lastShownDate?.toString() != today) {
            widgetIndexMap.clear()
            lastShownDate = LocalDate.parse(today)
        }

        CoroutineScope(Dispatchers.IO).launch {
            val tickets = photoDao.getPhotosByDate(today, "식권")
            withContext(Dispatchers.Main) {
                if (tickets.isNotEmpty()) {

                    val currentIndex = widgetIndexMap.getOrDefault(appWidgetId, 0)
                    val newIndex = (currentIndex + direction).coerceIn(0, tickets.size - 1)
                    widgetIndexMap[appWidgetId] = newIndex

                    val photo = tickets[newIndex]
                    val imageFile = File(photo.path)

                    if (imageFile.exists()) {
                        val bmp = BitmapFactory.decodeFile(imageFile.absolutePath)
                        // 🔽 교체
                        val resizedBmp = resizeBitmapToFitWidget(bmp, 400, 400)
                        views.setImageViewBitmap(R.id.widget_image, resizedBmp)
                        views.setTextViewText(R.id.widget_title, "$today 오늘 식권 (${newIndex + 1} / ${tickets.size})")

                        //  ✅ 사용 처리 버튼 🔜 다이얼로그 확인 처리
                        val useIntent = Intent(context, MealTicketWidgetProvider::class.java).apply {
                            action = ACTION_SHOW_CONFIRM_DIALOG
                            putExtra(EXTRA_IMAGE_URI, imageFile.toUri().toString())
                        }
                        val usePendingIntent = PendingIntent.getBroadcast(
                            context,
                            appWidgetId * 1000,
                            useIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.btn_mark_used_container, usePendingIntent)

                        // 사용 완료 여부에 따라 버튼 스타일 변경
                        if (photo.used) {
                            views.setImageViewResource(R.id.btn_mark_used_bg, R.drawable.btn_used_128x57_gray) // 반투명 이미지
                            views.setOnClickPendingIntent(R.id.btn_mark_used_container, null) // 클릭 막기
                            views.setTextViewText(R.id.btn_mark_used_text, "사용 완료") // 텍스트 변경
                        } else {
                            views.setImageViewResource(R.id.btn_mark_used_bg, R.drawable.btn_used_128x57)
                            views.setOnClickPendingIntent(R.id.btn_mark_used_container, usePendingIntent)
                            views.setTextViewText(R.id.btn_mark_used_text, "사용 처리")
                        }

                        // 좌우 화살표 버튼 표시 여부
                        views.setViewVisibility(R.id.btn_prev, if (newIndex > 0) View.VISIBLE else View.INVISIBLE)
                        views.setViewVisibility(R.id.btn_next, if (newIndex < tickets.size - 1) View.VISIBLE else View.INVISIBLE)

                        // ✅ 이전 버튼
                        val prevIntent = Intent(context, MealTicketWidgetProvider::class.java).apply {
                            action = ACTION_SHOW_PREVIOUS
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        val prevPendingIntent = PendingIntent.getBroadcast(
                            context,
                            appWidgetId * 10,
                            prevIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.btn_prev, prevPendingIntent)

                        // ✅ 다음 버튼
                        val nextIntent = Intent(context, MealTicketWidgetProvider::class.java).apply {
                            action = ACTION_SHOW_NEXT
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        val nextPendingIntent = PendingIntent.getBroadcast(
                            context,
                            appWidgetId * 10 + 1,
                            nextIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.btn_next, nextPendingIntent)

                        //  ✅ 위젯 갱신 버튼
                        val refreshIntent = Intent(context, MealTicketWidgetProvider::class.java).apply {
                            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                        }
                        val refreshPendingIntent = PendingIntent.getBroadcast(
                            context,
                            appWidgetId * 10000,
                            refreshIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.btn_refresh_bg, refreshPendingIntent)
                    }
                } else {
                    views.setTextViewText(R.id.widget_title, "오늘 식권 (0/0)")
                    views.setImageViewBitmap(R.id.widget_image, null)
                    views.setViewVisibility(R.id.btn_prev, View.GONE)
                    views.setViewVisibility(R.id.btn_next, View.GONE)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }


}
