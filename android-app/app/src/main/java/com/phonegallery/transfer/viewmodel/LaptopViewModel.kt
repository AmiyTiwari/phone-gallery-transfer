package com.phonegallery.transfer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phonegallery.transfer.data.DiscoveredServer
import com.phonegallery.transfer.data.ManifestRepository
import com.phonegallery.transfer.data.MediaStoreRepository
import com.phonegallery.transfer.data.NetworkDiscovery
import com.phonegallery.transfer.data.UploadService
import com.phonegallery.transfer.model.Photo
import com.phonegallery.transfer.model.UploadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

sealed class LaptopUiState {
    object Idle : LaptopUiState()
    object Scanning : LaptopUiState()
    data class ServerList(val servers: List<DiscoveredServer>) : LaptopUiState()
    object NoneFound : LaptopUiState()
    object Ready : LaptopUiState()
}

class LaptopViewModel(app: Application) : AndroidViewModel(app) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    private val mediaStore = MediaStoreRepository(app)
    private val discovery = NetworkDiscovery(app, httpClient)

    private val _uiState = MutableStateFlow<LaptopUiState>(LaptopUiState.Idle)
    val uiState: StateFlow<LaptopUiState> = _uiState.asStateFlow()

    private val _discoveredServers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val discoveredServers: StateFlow<List<DiscoveredServer>> = _discoveredServers.asStateFlow()

    private val _selectedServerUrls = MutableStateFlow<Set<String>>(emptySet())
    val selectedServerUrls: StateFlow<Set<String>> = _selectedServerUrls.asStateFlow()

    private var manifests: Map<String, ManifestRepository> = emptyMap()
    private var uploaders: Map<String, UploadService> = emptyMap()

    private val _allPhotos = MutableStateFlow<List<Photo>>(emptyList())
    val allPhotos: StateFlow<List<Photo>> = _allPhotos.asStateFlow()

    private val _syncedNames = MutableStateFlow<Set<String>>(emptySet())
    val syncedNames: StateFlow<Set<String>> = _syncedNames.asStateFlow()

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    // photoId → displayName (for status page headers)
    private val _photoNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    val photoNames: StateFlow<Map<Long, String>> = _photoNames.asStateFlow()

    // photoId → (serverUrl → (serverDisplayName, UploadState))
    private val _uploadStates =
        MutableStateFlow<Map<Long, Map<String, Pair<String, UploadState>>>>(emptyMap())
    val uploadStates: StateFlow<Map<Long, Map<String, Pair<String, UploadState>>>> =
        _uploadStates.asStateFlow()

    private val _overallProgress = MutableStateFlow(0 to 0)
    val overallProgress: StateFlow<Pair<Int, Int>> = _overallProgress.asStateFlow()

    fun scan() {
        viewModelScope.launch {
            _uiState.value = LaptopUiState.Scanning
            _selectedServerUrls.value = emptySet()
            val servers = discovery.discoverAll()
            _discoveredServers.value = servers
            _uiState.value = if (servers.isEmpty()) LaptopUiState.NoneFound
                             else LaptopUiState.ServerList(servers)
        }
    }

    fun toggleServer(url: String) {
        _selectedServerUrls.update { if (it.contains(url)) it - url else it + url }
    }

    fun confirmSelection() {
        val urls = _selectedServerUrls.value
        if (urls.isEmpty()) return
        manifests = urls.associateWith { ManifestRepository(httpClient, it) }
        uploaders = urls.associateWith { UploadService(getApplication(), httpClient, it) }

        viewModelScope.launch {
            _allPhotos.value = mediaStore.loadPhotos()
            val allSynced = mutableSetOf<String>()
            manifests.values.forEach { allSynced += it.fetchSyncedFileNames() }
            _syncedNames.value = allSynced
            _uiState.value = LaptopUiState.Ready
        }
    }

    fun disconnect() {
        manifests = emptyMap()
        uploaders = emptyMap()
        _allPhotos.value = emptyList()
        _syncedNames.value = emptySet()
        _selected.value = emptySet()
        _uploadStates.value = emptyMap()
        _photoNames.value = emptyMap()
        _overallProgress.value = 0 to 0
        _discoveredServers.value = emptyList()
        _selectedServerUrls.value = emptySet()
        _uiState.value = LaptopUiState.Idle
    }

    fun toggleSelect(photoId: Long) {
        _selected.update { if (it.contains(photoId)) it - photoId else it + photoId }
    }

    fun selectAll() { _selected.value = _allPhotos.value.map { it.id }.toSet() }
    fun clearSelection() { _selected.value = emptySet() }

    fun uploadSelected() {
        val toUpload = _allPhotos.value.filter { it.id in _selected.value }
        if (toUpload.isEmpty() || uploaders.isEmpty()) return

        val serverUrls = uploaders.keys.toList()
        val totalPairs = toUpload.size * serverUrls.size
        _overallProgress.value = 0 to totalPairs

        _photoNames.value = toUpload.associate { it.id to it.displayName }
        _uploadStates.value = toUpload.associate { photo ->
            photo.id to serverUrls.associate { url ->
                url to (displayName(url) to UploadState.Idle)
            }
        }

        var donePairs = 0
        viewModelScope.launch {
            serverUrls.forEach { serverUrl ->
                launch {
                    toUpload.forEach { photo ->
                        uploaders[serverUrl]!!.upload(photo).collect { state ->
                            _uploadStates.update { map ->
                                val photoMap = (map[photo.id] ?: emptyMap()).toMutableMap()
                                photoMap[serverUrl] = displayName(serverUrl) to state
                                map + (photo.id to photoMap)
                            }
                            if (state is UploadState.Success) {
                                donePairs++
                                _overallProgress.value = donePairs to totalPairs
                                _syncedNames.update { it + photo.displayName }
                            }
                        }
                    }
                }
            }
            clearSelection()
        }
    }

    private fun displayName(url: String): String =
        _discoveredServers.value.find { it.url == url }?.displayName ?: url
}
