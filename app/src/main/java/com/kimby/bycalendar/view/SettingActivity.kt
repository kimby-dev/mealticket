package com.kimby.bycalendar.view

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kimby.bycalendar.R

class SettingActivity : AppCompatActivity() {

    private lateinit var switchDarkMode: Switch
    private lateinit var checkboxPhoto: CheckBox
    private lateinit var checkboxTicket: CheckBox
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)

        prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        switchDarkMode = findViewById(R.id.switch_dark_mode)
        checkboxPhoto = findViewById(R.id.checkbox_photo)
        checkboxTicket = findViewById(R.id.checkbox_ticket)

        // Load values
        switchDarkMode.isChecked = prefs.getBoolean("dark_mode", false)
        checkboxPhoto.isChecked = prefs.getBoolean("use_photo", true)
        checkboxTicket.isChecked = prefs.getBoolean("use_ticket", true)

        switchDarkMode.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        val listener = CompoundButton.OnCheckedChangeListener { _, _ ->
            if (!checkboxPhoto.isChecked && !checkboxTicket.isChecked) {
                Toast.makeText(this, "적어도 하나의 탭은 선택되어야 합니다.", Toast.LENGTH_SHORT).show()
                // 최소 하나 유지
                checkboxPhoto.isChecked = true
            }
            prefs.edit()
                .putBoolean("use_photo", checkboxPhoto.isChecked)
                .putBoolean("use_ticket", checkboxTicket.isChecked)
                .apply()
        }

        checkboxPhoto.setOnCheckedChangeListener(listener)
        checkboxTicket.setOnCheckedChangeListener(listener)
    }

    companion object {
        fun isPhotoEnabled(context: Context): Boolean =
            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getBoolean("use_photo", true)

        fun isTicketEnabled(context: Context): Boolean =
            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getBoolean("use_ticket", true)
    }
}
