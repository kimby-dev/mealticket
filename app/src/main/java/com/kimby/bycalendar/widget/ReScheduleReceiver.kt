package com.kimby.bycalendar.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kimby.bycalendar.view.MealTicketWidgetProvider

/**
 * 재부팅/시간 변경 시 재예약
 */
class ReScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        MealTicketWidgetProvider.scheduleNextMidnight(context)
    }
}