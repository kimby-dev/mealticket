package com.kimby.bycalendar.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimby.bycalendar.BuildConfig
import com.kimby.bycalendar.service.HolidayApiService
import com.kimby.bycalendar.utils.CalendarUtils.parseLocdateToLocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class CalendarViewModel : ViewModel() {

    private val _selectedDate = MutableLiveData<LocalDate>(LocalDate.now())
    val selectedDate: LiveData<LocalDate> get() = _selectedDate

    private val _days = MutableLiveData<List<LocalDate?>>()
    val days: LiveData<List<LocalDate?>> get() = _days

    private val _holidays = MutableLiveData<Set<LocalDate>>(emptySet())
    val holidays: LiveData<Set<LocalDate>> get() = _holidays

    private val holidayService by lazy {
        Retrofit.Builder()
            .baseUrl("https://apis.data.go.kr/")
            .addConverterFactory(SimpleXmlConverterFactory.create())
            .build()
            .create(HolidayApiService::class.java)
    }

    private val _holidayLabels = MutableLiveData<Map<LocalDate, HolidayLabel>>(emptyMap())
    val holidayLabels: LiveData<Map<LocalDate, HolidayLabel>> get() = _holidayLabels

    fun setSelectedDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun setDaysForMonth(yearMonth: YearMonth) {
        val firstDay = yearMonth.atDay(1)
        val totalDays = yearMonth.lengthOfMonth()
        val offset = firstDay.dayOfWeek.value % 7  // 일요일=0 맞추기

        val totalCells = offset + totalDays
        val fullWeeks = ((totalCells + 6) / 7) * 7

        val daysList = MutableList<LocalDate?>(fullWeeks) { null }
        for (i in 0 until totalDays) {
            daysList[offset + i] = firstDay.plusDays(i.toLong())
        }
        _days.value = daysList

        // 동시에 휴일 정보도 로드
        loadHolidays(yearMonth)
    }

    fun setHolidays(dates: Set<LocalDate>) {
        _holidays.value = dates
    }

    // ✅ 여기에 추가
    fun setHolidayLabels(items: List<HolidayItem>) {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val map = mutableMapOf<LocalDate, HolidayLabel>()

        for (item in items) {
            try {
                val date = LocalDate.parse(item.locdate.toString(), formatter)
                val name = item.dateName ?: continue
                val isHoliday = item.isHoliday == "Y"
                map[date] = HolidayLabel(date, isHoliday, name)
            } catch (_: Exception) {
                continue
            }
        }

        _holidayLabels.value = map
    }

    private fun loadHolidays(yearMonth: YearMonth) {
        val year = yearMonth.year
        val month = String.format("%02d", yearMonth.monthValue)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = holidayService.getHolidays(
                    serviceKey = BuildConfig.HOLIDAY_API_KEY,
                    year = year,
                    month = month
                )

                val items = response.body?.items?.item ?: emptyList()
                val labels = items.mapNotNull { item ->
                    val date = item.locdate?.let { parseLocdateToLocalDate(it) } ?: return@mapNotNull null
                    val name = item.dateName ?: return@mapNotNull null
                    val isHoliday = item.isHoliday == "Y"
                    HolidayLabel(date, isHoliday, name)
                }

                val holidays = labels.filter { it.isHoliday }.map { it.date }.toSet()
                val labelMap = labels.associateBy { it.date }

                _holidays.postValue(holidays)
                _holidayLabels.postValue(labelMap)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}