package com.kimby.bycalendar.model


import com.kimby.bycalendar.BuildConfig
import com.kimby.bycalendar.service.HolidayApiService
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory

class HolidayRepository {
    private val service: HolidayApiService = Retrofit.Builder()
        .baseUrl("https://apis.data.go.kr/")
        .addConverterFactory(SimpleXmlConverterFactory.create())
        .build()
        .create(HolidayApiService::class.java)

    suspend fun getHolidays(year: Int, month: Int): HolidayResponse? {
        val monthStr = month.toString().padStart(2, '0')
        return try {
            service.getHolidays(
                serviceKey = BuildConfig.HOLIDAY_API_KEY,
                year = year,
                month = monthStr
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}