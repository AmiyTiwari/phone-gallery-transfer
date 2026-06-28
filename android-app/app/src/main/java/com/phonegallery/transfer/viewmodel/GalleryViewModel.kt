package com.phonegallery.transfer.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phonegallery.transfer.data.MediaStoreRepository
import com.phonegallery.transfer.model.Photo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val mediaStore = MediaStoreRepository(app)

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    private val _loading = MutableStateFlow(true)  // start true — spinner until first load
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun loadPhotos() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val result = mediaStore.loadPhotos()
                Log.d("GalleryVM", "loadPhotos: found ${result.size} photos")
                result.forEach { Log.d("GalleryVM", "  photo: ${it.displayName} uri=${it.uri}") }
                _photos.value = result
            } catch (e: Exception) {
                Log.e("GalleryVM", "loadPhotos failed", e)
            } finally {
                _loading.value = false
            }
        }
    }
}
