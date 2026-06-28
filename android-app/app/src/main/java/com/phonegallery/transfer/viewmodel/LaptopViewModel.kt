package com.phonegallery.transfer.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phonegallery.transfer.data.ManifestRepository
import com.phonegallery.transfer.data.MediaStoreRepository
import com.phonegallery.transfer.data.UploadService
import com.phonegallery.transfer.model.Photo
import com.phonegallery.transfer.model.UploadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

private const val SERVER_URL = "http://localhost:8080"
private const val PREFS_NAME = "gallery_transfer"

class LaptopViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs: SharedPreferences =
        app.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    private val mediaStore = MediaStoreRepository(app)
    private val manifest = ManifestRepository(httpClient, SERVER_URL)
    private val uploader = UploadService(app, httpClient, SERVER_URL)

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _allPhotos = MutableStateFlow<List<Photo>>(emptyList())
    val allPhotos: StateFlow<List<Photo>> = _allPhotos.asStateFlow()

    // filenames already synced to laptop — used for checkmark badge
    private val _syncedNames = MutableStateFlow<Set<String>>(emptySet())
    val syncedNames: StateFlow<Set<String>> = _syncedNames.asStateFlow()

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    // session-only: cleared on disconnect. Maps photoId → (displayName, state)
    private val _uploadStates = MutableStateFlow<Map<Long, Pair<String, UploadState>>>(emptyMap())
    val uploadStates: StateFlow<Map<Long, Pair<String, UploadState>>> = _uploadStates.asStateFlow()

    private val _overallProgress = MutableStateFlow(0 to 0)
    val overallProgress: StateFlow<Pair<Int, Int>> = _overallProgress.asStateFlow()

    fun checkConnection() {
        viewModelScope.launch {
            val reachable = manifest.isServerReachable()
            if (!reachable && _connected.value) {
                // just disconnected — clear session upload history
                _uploadStates.value = emptyMap()
                _overallProgress.value = 0 to 0
            }
            _connected.value = reachable
            if (reachable) {
                _allPhotos.value = mediaStore.loadPhotos()
                _syncedNames.value = manifest.fetchSyncedFileNames()
            }
        }
    }

    fun toggleSelect(photoId: Long) {
        _selected.value = _selected.value.toMutableSet().apply {
            if (contains(photoId)) remove(photoId) else add(photoId)
        }
    }

    fun selectAll() {
        _selected.value = _allPhotos.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun uploadSelected() {
        val toUpload = _allPhotos.value.filter { it.id in _selected.value }
        if (toUpload.isEmpty()) return

        _overallProgress.value = 0 to toUpload.size

        // seed status map with Idle entries so status page shows all queued
        _uploadStates.value = toUpload.associate { it.id to (it.displayName to UploadState.Idle) }

        var done = 0
        viewModelScope.launch {
            toUpload.forEach { photo ->
                uploader.upload(photo).collect { state ->
                    _uploadStates.value = _uploadStates.value +
                            (photo.id to (photo.displayName to state))
                    if (state is UploadState.Success) {
                        done++
                        _overallProgress.value = done to toUpload.size
                        _syncedNames.value = _syncedNames.value + photo.displayName
                    }
                }
            }
            clearSelection()
        }
    }
}
