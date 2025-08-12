package com.kimby.bycalendar.service

import com.kimby.bycalendar.model.HolidayResponse
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface HolidayApiService {
    companion object {
        fun create(): HolidayApiService {
            return Retrofit.Builder()
                .baseUrl("https://apis.data.go.kr/")
                .addConverterFactory(SimpleXmlConverterFactory.create())
                .build()
                .create(HolidayApiService::class.java)
        }
    }

    @GET("B090041/openapi/service/SpcdeInfoService/getHoliDeInfo")
    suspend fun getHolidays(
        @Query("ServiceKey", encoded = true) serviceKey: String,
        @Query("solYear") year: Int,
        @Query("solMonth") month: String
    ): HolidayResponse
}