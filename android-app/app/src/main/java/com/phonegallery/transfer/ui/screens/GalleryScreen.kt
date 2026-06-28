package com.phonegallery.transfer.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phonegallery.transfer.model.Photo
import com.phonegallery.transfer.ui.components.PhotoGrid
import com.phonegallery.transfer.ui.components.PhotoViewer
import com.phonegallery.transfer.viewmodel.GalleryViewModel

@Composable
fun GalleryScreen(modifier: Modifier = Modifier, vm: GalleryViewModel = viewModel()) {
    val photos by vm.photos.collectAsState()
    val loading by vm.loading.collectAsState()
    var viewingPhoto by remember { mutableStateOf<Photo?>(null) }

    LaunchedEffect(Unit) { vm.loadPhotos() }

    if (loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        PhotoGrid(
            photos = photos,
            selected = emptySet(),
            onToggle = { id -> viewingPhoto = photos.find { it.id == id } },
            modifier = modifier.fillMaxSize(),
        )
    }

    viewingPhoto?.let { photo ->
        PhotoViewer(photo = photo, onDismiss = { viewingPhoto = null })
    }
}
