package com.kimby.bycalendar.view

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.kimby.bycalendar.database.PhotoDatabase
import com.kimby.bycalendar.model.PhotoEntity
import com.kimby.bycalendar.model.PhotoRepository
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = PhotoDatabase.getDatabase(application).photoDao()
    private val repository = PhotoRepository(dao)

    private val _photoUris = MutableLiveData<List<File>>()
    val photoUris: LiveData<List<File>> = _photoUris

    fun loadPhotos(date: String, category: String) {
        viewModelScope.launch {
            val result = repository.getPhotos(date, category)
            _photoUris.value = result.map { File(it.path) }
        }
    }

    fun insert(photo: PhotoEntity) = viewModelScope.launch {
        repository.insertPhoto(photo)
    }

    fun delete(path: String) = viewModelScope.launch {
        repository.deleteByPath(path)
    }

    fun deleteByDate(date: String, category: String) = viewModelScope.launch {
        repository.deleteByDate(date, category)
    }

    suspend fun countByDateAndCategory(date: String, category: String) : Int {
        return repository.countByDateAndCategory(date, category)
    }
}