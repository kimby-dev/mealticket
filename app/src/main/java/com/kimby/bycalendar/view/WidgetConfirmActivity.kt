package com.kimby.bycalendar.view

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog

class WidgetConfirmActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uriStr = intent.getStringExtra(MealTicketWidgetProvider.EXTRA_IMAGE_URI) ?: return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        AlertDialog.Builder(this)
            .setTitle("식권 사용 처리")
            .setMessage("해당 식권을 사용 처리 하시겠습니까?")
            .setPositiveButton("확인") { _, _ ->
                val markIntent = Intent(applicationContext, MealTicketWidgetProvider::class.java).apply {
                    action = MealTicketWidgetProvider.ACTION_MARK_USED
                    putExtra(MealTicketWidgetProvider.EXTRA_IMAGE_URI, uriStr)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)   // ★ 같이 보냄
                }
                sendBroadcast(markIntent)
                finish()
            }
            .setNegativeButton("취소") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}