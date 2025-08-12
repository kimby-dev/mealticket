package com.kimby.bycalendar.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object MidnightAlarm {
    private const val ACTION = "com.example.ACTION_MIDNIGHT_TICK"
    private const val REQ_CODE = 1001

    fun scheduleNextMidnight(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val triggerAt = nextMidnightMillis()
        val pi = PendingIntent.getBroadcast(
            context, REQ_CODE,
            Intent(ACTION).setClass(context, MidnightTickReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 정확 알람 가능하면 exact, 아니면 best-effort
        if (canExact(alarmManager)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            // 정확 권한 없으면 setWindow로 근사치 예약(또는 WorkManager로 fallback)
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, /*windowLength*/ 15 * 60_000L, pi)
        }
    }

    private fun canExact(alarmManager: AlarmManager): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 31) alarmManager.canScheduleExactAlarms() else true

    private fun nextMidnightMillis(): Long {
        val now = java.time.ZonedDateTime.now()
        val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone) // 내일 00:00
        return next.toInstant().toEpochMilli()
    }
}