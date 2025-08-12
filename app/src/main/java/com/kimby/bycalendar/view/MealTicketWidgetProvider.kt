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
        private const val ACTION_MANUAL_REFRESH = "com.kimby.bycalendar.ACTION_MANUAL_REFRESH"
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

        private const val ACTION_WIDGET_TAP = "com.kimby.bycalendar.ACTION_WIDGET_TAP"
        private const val PREFS_NAME = "meal_widget_prefs"
        private const val KEY_LAST_TOUCH_PREFIX = "last_touch_"

        private fun nowMillis() = System.currentTimeMillis()
        private fun getLastTouch(context: Context, id: Int): Long =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong("$KEY_LAST_TOUCH_PREFIX$id", 0L)

        private fun setLastTouch(context: Context, id: Int, time: Long = nowMillis()) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong("$KEY_LAST_TOUCH_PREFIX$id", time).apply()
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
            ACTION_WIDGET_TAP -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    setLastTouch(context, id)
                    updateAppWidget(context, AppWidgetManager.getInstance(context), id)
                }
            }

            ACTION_MANUAL_REFRESH -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    setLastTouch(context, id)           // 수동만 상호작용으로 간주
                    updateAppWidget(context, AppWidgetManager.getInstance(context), id)
                }
            }
            ACTION_MARK_USED -> {
                // 기존 처리 유지 + 상호작용으로 간주
                val uri = intent.getStringExtra(EXTRA_IMAGE_URI)?.toUri() ?: return
                val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, MealTicketWidgetProvider::class.java))
                ids.forEach { setLastTouch(context, it) }
                // (mark 처리 후 update 호출은 네 기존 로직대로)
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
                    setLastTouch(context, appWidgetId)
                    val direction = if (intent.action == ACTION_SHOW_PREVIOUS) -1 else 1
                    updateAppWidget(
                        context,
                        AppWidgetManager.getInstance(context),
                        appWidgetId,
                        direction
                    )
                }
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                // 시스템이 주기/재배치 등으로 보낸 업데이트
                // 여기서는 절대 setLastTouch() 하지 말 것!
                val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    ?: AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, MealTicketWidgetProvider::class.java))
                onUpdate(context, AppWidgetManager.getInstance(context), ids)
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
        // 앱 위젯이 처음 추가될 때: 바로 선명하게
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, MealTicketWidgetProvider::class.java))
        ids.forEach { setLastTouch(context, it) }
        scheduleNextMidnight(context)
    }

    override fun onDisabled(context: Context) {
        // 모든 위젯 제거 → 알람 해제 + 기록 정리(선택)
        cancelMidnightAlarm(context)
        // prefs 정리는 선택사항
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
//                        // 🔽 교체
//                        val resizedBmp = resizeBitmapToFitWidget(bmp, 400, 400)
//                        views.setImageViewBitmap(R.id.widget_image, resizedBmp)
//                        views.setTextViewText(R.id.widget_title, "$today 오늘 식권 (${newIndex + 1} / ${tickets.size})")

                        // 🔽 교체 시작
                        val resizedBmp = resizeBitmapToFitWidget(bmp, 400, 400)

                        // 30분 경과 여부 판단
                        val lastTouch = getLastTouch(context, appWidgetId)
                        val thirtyMin = 30 * 60 * 1000L

                        // 최초 터치 기록 없으면 지금 시각으로 초기화
                        if (lastTouch == 0L) {
                            setLastTouch(context, appWidgetId)
                        }

                        val shouldBlur =
                            (nowMillis() - getLastTouch(context, appWidgetId)) >= thirtyMin

                        // 💡 여기서 Default로 blur 계산
                        val displayBmp = withContext(Dispatchers.Default) {
                            if (shouldBlur) blurBitmap(resizedBmp, 12) else resizedBmp
                        }

                        // Main에서 RemoteViews 적용
                        withContext(Dispatchers.Main) {
                            // 이미지 적용
                            views.setImageViewBitmap(R.id.widget_image, displayBmp)
                            views.setTextViewText(
                                R.id.widget_title,
                                "$today 오늘 식권 (${newIndex + 1} / ${tickets.size})"
                            )

                            // 이미지 탭하면 선명해지도록 클릭 인텐트 연결
                            val tapIntent =
                                Intent(context, MealTicketWidgetProvider::class.java).apply {
                                    action = ACTION_WIDGET_TAP
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                }
                            val tapPendingIntent = PendingIntent.getBroadcast(
                                context,
                                appWidgetId * 111,
                                tapIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(R.id.widget_image, tapPendingIntent)
                            // 🔽 교체 끝

                            //  ✅ 사용 처리 버튼 🔜 다이얼로그 확인 처리
                            val useIntent =
                                Intent(context, MealTicketWidgetProvider::class.java).apply {
                                    action = ACTION_SHOW_CONFIRM_DIALOG
                                    putExtra(EXTRA_IMAGE_URI, imageFile.toUri().toString())
                                }
                            val usePendingIntent = PendingIntent.getBroadcast(
                                context,
                                appWidgetId * 1000,
                                useIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(
                                R.id.btn_mark_used_container,
                                usePendingIntent
                            )

                            // 사용 완료 여부에 따라 버튼 스타일 변경
                            if (photo.used) {
                                views.setImageViewResource(
                                    R.id.btn_mark_used_bg,
                                    R.drawable.btn_used_128x57_gray
                                ) // 반투명 이미지
                                views.setOnClickPendingIntent(
                                    R.id.btn_mark_used_container,
                                    null
                                ) // 클릭 막기
                                views.setTextViewText(R.id.btn_mark_used_text, "사용 완료") // 텍스트 변경
                            } else {
                                views.setImageViewResource(
                                    R.id.btn_mark_used_bg,
                                    R.drawable.btn_used_128x57
                                )
                                views.setOnClickPendingIntent(
                                    R.id.btn_mark_used_container,
                                    usePendingIntent
                                )
                                views.setTextViewText(R.id.btn_mark_used_text, "사용 처리")
                            }

                            // 좌우 화살표 버튼 표시 여부
                            views.setViewVisibility(
                                R.id.btn_prev,
                                if (newIndex > 0) View.VISIBLE else View.INVISIBLE
                            )
                            views.setViewVisibility(
                                R.id.btn_next,
                                if (newIndex < tickets.size - 1) View.VISIBLE else View.INVISIBLE
                            )

                            // ✅ 이전 버튼
                            val prevIntent =
                                Intent(context, MealTicketWidgetProvider::class.java).apply {
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
                            val nextIntent =
                                Intent(context, MealTicketWidgetProvider::class.java).apply {
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

                            //  ✅ 위젯 갱신 버튼 (수동)
                            val refreshIntent =
                                Intent(context, MealTicketWidgetProvider::class.java).apply {
                                    action = ACTION_MANUAL_REFRESH
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                }
                            val refreshPendingIntent = PendingIntent.getBroadcast(
                                context,
                                appWidgetId * 10000,
                                refreshIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(R.id.btn_refresh_bg, refreshPendingIntent)
                        }
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

    private fun blurBitmap(src: Bitmap, radius: Int = 12): Bitmap {
        require(radius in 1..25)
        val bmp = src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        val w = bmp.width; val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val div = radius * 2 + 1
        val r = IntArray(w * h); val g = IntArray(w * h); val b = IntArray(w * h)
        val vmin = IntArray(maxOf(w, h))
        val dv = IntArray(256 * div).apply {
            val divsum = (div + 1) shr 1
            val divsumSq = divsum * divsum
            for (i in indices) this[i] = i / divsumSq
        }

        var yi = 0; var yw = 0
        for (y in 0 until h) {
            var rinsum = 0; var ginsum = 0; var binsum = 0
            var routsum = 0; var goutsum = 0; var boutsum = 0
            var rsum = 0; var gsum = 0; var bsum = 0

            for (i in -radius..radius) {
                val p = pixels[yi + minOf(w - 1, maxOf(i, 0))]
                val pr = (p shr 16) and 0xFF
                val pg = (p shr 8) and 0xFF
                val pb = p and 0xFF
                val rbs = div - Math.abs(i)
                rsum += pr * rbs; gsum += pg * rbs; bsum += pb * rbs
                if (i > 0) { rinsum += pr; ginsum += pg; binsum += pb }
                else { routsum += pr; goutsum += pg; boutsum += pb }
            }
            var stackPointer = radius

            for (x in 0 until w) {
                r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum]

                val p1 = yi + minOf(x + radius + 1, w - 1)
                val p2 = yi + maxOf(x - radius, 0)
                val p1c = pixels[p1]; val p2c = pixels[p2]

                val pr1 = (p1c shr 16) and 0xFF; val pg1 = (p1c shr 8) and 0xFF; val pb1 = p1c and 0xFF
                val pr2 = (p2c shr 16) and 0xFF; val pg2 = (p2c shr 8) and 0xFF; val pb2 = p2c and 0xFF

                rsum += rinsum - routsum
                gsum += ginsum - goutsum
                bsum += binsum - boutsum

                rinsum += pr1; ginsum += pg1; binsum += pb1
                routsum += pr2; goutsum += pg2; boutsum += pb2

                yi++
            }
            yw += w
        }

        for (x in 0 until w) {
            var rinsum = 0; var ginsum = 0; var binsum = 0
            var routsum = 0; var goutsum = 0; var boutsum = 0
            var rsum = 0; var gsum = 0; var bsum = 0
            var yp = -radius * w
            for (i in -radius..radius) {
                val yi2 = maxOf(0, yp) + x
                rsum += r[yi2] * (div - Math.abs(i))
                gsum += g[yi2] * (div - Math.abs(i))
                bsum += b[yi2] * (div - Math.abs(i))
                if (i > 0) { rinsum += r[yi2]; ginsum += g[yi2]; binsum += b[yi2] }
                else { routsum += r[yi2]; goutsum += g[yi2]; boutsum += b[yi2] }
                if (i < h - 1) yp += w
            }
            var yi3 = x
            for (y in 0 until h) {
                val a = (pixels[yi3] ushr 24) and 0xFF
                pixels[yi3] = (a shl 24) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                val p1y = x + minOf(y + radius + 1, h - 1) * w
                val p2y = x + maxOf(y - radius, 0) * w

                rsum += rinsum - routsum
                gsum += ginsum - goutsum
                bsum += binsum - boutsum

                rinsum += r[p1y]; ginsum += g[p1y]; binsum += b[p1y]
                routsum += r[p2y]; goutsum += g[p2y]; boutsum += b[p2y]

                yi3 += w
            }
        }

        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return bmp
    }

}
