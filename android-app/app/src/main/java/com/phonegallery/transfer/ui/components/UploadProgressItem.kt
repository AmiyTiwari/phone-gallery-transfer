package com.phonegallery.transfer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.phonegallery.transfer.model.Photo
import com.phonegallery.transfer.model.UploadState

@Composable
fun UploadProgressItem(photo: Photo, state: UploadState, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(photo.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            when (state) {
                is UploadState.Uploading -> LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                UploadState.Success -> Text("Uploaded", color = MaterialTheme.colorScheme.primary)
                is UploadState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                UploadState.Skipped -> Text("Already synced")
                UploadState.Idle -> {}
            }
        }
        Spacer(Modifier.width(8.dp))
        when (state) {
            UploadState.Success -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            is UploadState.Error -> Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
}
