package com.kimby.bycalendar.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String, // ISO 날짜 형식 예: "2025-07-02"
    val path: String,   // 이미지 URI 문자열
    val category: String = "식권",  // 사진 저장 카테고리
    val used: Boolean = false  // 식권 사용 여부
)