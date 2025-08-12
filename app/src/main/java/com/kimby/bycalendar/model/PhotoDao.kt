package com.kimby.bycalendar.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity)

    @Query("SELECT * FROM photos WHERE date = :date AND category = :category")
    suspend fun getPhotosByDate(date: String, category: String): List<PhotoEntity>

    @Query("DELETE FROM photos WHERE date = :date AND category = :category")
    suspend fun deletePhotosByDate(date: String, category: String)

    @Query("DELETE FROM photos WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("SELECT COUNT(*) FROM photos WHERE date = :date AND category = :category")
    suspend fun countByDateAndCategory(date: String, category: String): Int

    @Query("UPDATE photos SET path = :newPath WHERE date = :date AND category = :category AND path = :oldPath")
    suspend fun updatePhotoPath(date: String, category: String, oldPath: String, newPath: String)

    @Query("UPDATE photos SET path = :newPath, used = 1 WHERE path = :oldPath")
    suspend fun updatePhotoPathAndUsed(oldPath: String, newPath: String)
}