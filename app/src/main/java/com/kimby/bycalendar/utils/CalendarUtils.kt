package com.kimby.bycalendar.utils

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

object CalendarUtils {
    fun generateCalendarDays(yearMonth: YearMonth): List<LocalDate?> {
        val firstDay = yearMonth.atDay(1)
        val totalDays = yearMonth.lengthOfMonth()
        val offset = firstDay.dayOfWeek.value % 7  // 일요일 = 0

        val totalCells = offset + totalDays
        val fullWeeks = ((totalCells + 6) / 7) * 7

        return List(fullWeeks) { index ->
            if (index in offset until (offset + totalDays)) {
                firstDay.plusDays((index - offset).toLong())
            } else {
                null
            }
        }
    }

    fun parseLocdateToLocalDate(locdate: String): LocalDate? {
        return try {
            val str = locdate
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            LocalDate.parse(str, formatter)
        } catch (e: Exception) {
            null
        }
    }
}