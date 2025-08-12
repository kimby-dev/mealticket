package com.kimby.bycalendar.model

import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root
import java.time.LocalDate

@Root(name = "response", strict = false)
data class HolidayResponse(
    @field:Element(name = "body")
    var body: Body? = null
)

@Root(name = "body", strict = false)
data class Body(
    @field:Element(name = "items", required = false)
    var items: Items? = null
)

@Root(name = "items", strict = false)
data class Items(
    @field:ElementList(entry = "item", inline = true, required = false)
    var item: List<HolidayItem>? = null
)

@Root(name = "item", strict = false)
data class HolidayItem(
    @field:Element(name = "locdate")
    var locdate: String = "",

    @field:Element(name = "isHoliday", required = false)
    var isHoliday: String? = null,

    @field:Element(name = "dateName")
    var dateName: String = ""
)

data class HolidayLabel(
    val date: LocalDate,
    val isHoliday: Boolean,
    val name: String
)