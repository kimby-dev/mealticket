package com.kimby.bycalendar.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.kimby.bycalendar.view.MealTicketWidgetProvider

/**
 * 자정 브로드캐스트 수신 → Worker 실행
 */
class MidnightTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val mgr = AppWidgetManager.getInstance(context)
        val cn = ComponentName(context, MealTicketWidgetProvider::class.java)
        val ids = mgr.getAppWidgetIds(cn)

        // 1) Provider 표준 액션으로 전체 갱신
        val update = Intent(context, MealTicketWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        context.sendBroadcast(update)

        // 2) 다음 자정 재예약
        MealTicketWidgetProvider.scheduleNextMidnight(context)
    }
}
