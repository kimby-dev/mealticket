package com.kimby.bycalendar.model

class PhotoRepository(private val dao: PhotoDao) {
    suspend fun getPhotos(date: String, category: String) =
        dao.getPhotosByDate(date, category)

    suspend fun insertPhoto(photo: PhotoEntity) =
        dao.insert(photo)

    suspend fun deleteByPath(path: String) =
        dao.deleteByPath(path)

    suspend fun deleteByDate(date: String, category: String) =
        dao.deletePhotosByDate(date, category)

    suspend fun countByDateAndCategory(date: String, category: String) =
        dao.countByDateAndCategory(date, category)
}