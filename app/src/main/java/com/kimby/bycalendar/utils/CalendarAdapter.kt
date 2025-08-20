package com.kimby.bycalendar.utils

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.kimby.bycalendar.R
import com.kimby.bycalendar.model.HolidayLabel
import java.time.DayOfWeek
import java.time.LocalDate

class CalendarAdapter(
    private var days: List<LocalDate?>,
    private var selectedDate: LocalDate?,
    private val today: LocalDate,  // ✅ 고정된 today 값 (생성 시만)
    private var holidays: Set<LocalDate>,  // ✅ 추가
    private var holidayLabels: Map<LocalDate, HolidayLabel>,
    private val onDateClicked: (LocalDate) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.DateViewHolder>() {

    inner class DateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateText: TextView = itemView.findViewById(R.id.text_date)
        val labelText: TextView = itemView.findViewById(R.id.text_label)
        val container = itemView.findViewById<FrameLayout>(R.id.date_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_date, parent, false)
        return DateViewHolder(view)
    }

    override fun getItemCount(): Int = days.size

    override fun onBindViewHolder(holder: DateViewHolder, position: Int) {
        val date = days[position]
        val context = holder.itemView.context
        val labelText = holder.labelText

        // ✅ 모든 스타일을 초기화 (기존 배경이 남는 것 방지)
        holder.container.setBackgroundResource(0)
        holder.dateText.setTextColor(Color.BLACK)
        holder.labelText.setTextColor(Color.BLACK)

        if (date == null) {
            holder.dateText.text = ""
            holder.dateText.setBackgroundResource(0)
            labelText.visibility = View.GONE
            return
        }

        holder.dateText.text = date.dayOfMonth.toString()
        // 라벨설정
        if (holidayLabels.containsKey(date)) {
            labelText.text = holidayLabels[date]?.name
            labelText.visibility = View.VISIBLE
        } else {
            labelText.visibility = View.GONE
        }

        // 🎯 조건별 스타일 : 배경 및 색상 처리
        when {
            date == selectedDate -> { // 선택한 날
                holder.container.setBackgroundResource(R.drawable.bg_calendar_day_selected)
                holder.dateText.setTextColor(Color.WHITE)
                holder.labelText.setTextColor(Color.WHITE)
            }
            date == today -> {  // 오늘
                holder.container.setBackgroundResource(R.drawable.bg_calendar_day_today)
                holder.dateText.setTextColor(Color.BLACK)
                holder.labelText.setTextColor(Color.BLACK)
            }
            holidays.contains(date) || date.dayOfWeek == DayOfWeek.SUNDAY -> { // 일요일 / 공휴일
                holder.container.setBackgroundResource(R.drawable.bg_calendar_day_holiday)
                holder.dateText.setTextColor(Color.RED)
                holder.labelText.setTextColor(Color.RED)
            }
            date.dayOfWeek == DayOfWeek.SATURDAY -> { // 토요일
                holder.container.setBackgroundResource(R.drawable.bg_calendar_day_saturday)
                holder.dateText.setTextColor(ContextCompat.getColor(context, R.color.blue))
                holder.labelText.setTextColor(ContextCompat.getColor(context, R.color.blue))
            }
            else -> { // 평일
                holder.container.setBackgroundResource(R.drawable.bg_calendar_day_default)
                holder.dateText.setTextColor(Color.BLACK)
                holder.labelText.setTextColor(Color.BLACK)
            }
        }

        holder.itemView.setOnClickListener {
            onDateClicked(date)
        }
    }

    // 외부에서 데이터 갱신용
    fun updateDays(
        newDays: List<LocalDate?>,
        newSelectedDate: LocalDate?,
        newHolidays: Set<LocalDate>,
        newholidayLabels: Map<LocalDate, HolidayLabel>
    ) {
        days = newDays
        selectedDate = newSelectedDate
        holidays = newHolidays
        holidayLabels = newholidayLabels
        notifyDataSetChanged()
    }
}