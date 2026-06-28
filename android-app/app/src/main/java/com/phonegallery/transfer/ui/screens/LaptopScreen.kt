package com.phonegallery.transfer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phonegallery.transfer.model.UploadState
import com.phonegallery.transfer.ui.components.PhotoGrid
import com.phonegallery.transfer.viewmodel.LaptopViewModel

private enum class LaptopPage { GRID, STATUS }

@Composable
fun LaptopScreen(modifier: Modifier = Modifier, vm: LaptopViewModel = viewModel()) {
    val connected by vm.connected.collectAsState()
    val allPhotos by vm.allPhotos.collectAsState()
    val syncedNames by vm.syncedNames.collectAsState()
    val selected by vm.selected.collectAsState()
    val uploadStates by vm.uploadStates.collectAsState()
    val overallProgress by vm.overallProgress.collectAsState()

    val isUploading = uploadStates.values.any { (_, state) -> state is UploadState.Uploading }
    val hasSessionUploads = uploadStates.isNotEmpty()

    var page by remember { mutableStateOf(LaptopPage.GRID) }

    LaunchedEffect(Unit) { vm.checkConnection() }

    Column(modifier = modifier.fillMaxSize()) {

        // ── Header ────────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            if (page == LaptopPage.STATUS) {
                IconButton(onClick = { page = LaptopPage.GRID }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            } else {
                Icon(
                    Icons.Filled.Laptop,
                    contentDescription = null,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            Text(
                text = when (page) {
                    LaptopPage.GRID -> "Laptop  •  ${if (connected) "Connected" else "Disconnected"}"
                    LaptopPage.STATUS -> "Upload Status"
                },
                color = if (page == LaptopPage.GRID) {
                    if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                } else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        HorizontalDivider()

        // ── Content ───────────────────────────────────────────────────────────
        when (page) {

            LaptopPage.GRID -> {
                if (!connected) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Laptop not reachable", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Connect via USB and run start.sh on laptop.",
                                modifier = Modifier.padding(16.dp),
                            )
                            Button(onClick = { vm.checkConnection() }) { Text("Retry") }
                        }
                    }
                } else {
                    PhotoGrid(
                        photos = allPhotos,
                        selected = selected,
                        onToggle = vm::toggleSelect,
                        syncedNames = syncedNames,
                        modifier = Modifier.weight(1f),
                    )
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { vm.selectAll() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Select All")
                        }
                        if (hasSessionUploads) {
                            OutlinedButton(
                                onClick = { page = LaptopPage.STATUS },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("View Status")
                            }
                        }
                        Button(
                            onClick = {
                                vm.uploadSelected()
                                page = LaptopPage.STATUS
                            },
                            enabled = selected.isNotEmpty() && !isUploading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Upload (${selected.size})")
                        }
                    }
                }
            }

            LaptopPage.STATUS -> {
                if (overallProgress.second > 0) {
                    Text(
                        "${overallProgress.first} / ${overallProgress.second} done",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(uploadStates.entries.toList(), key = { it.key }) { (_, entry) ->
                        val (name, state) = entry
                        ListItem(
                            headlineContent = { Text(name, maxLines = 1) },
                            trailingContent = {
                                when (state) {
                                    is UploadState.Uploading -> CircularProgressIndicator()
                                    UploadState.Success -> Text(
                                        "✓ Done",
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    is UploadState.Error -> Text(
                                        "✗ ${state.message}",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    UploadState.Idle -> Text("Queued")
                                    UploadState.Skipped -> Text("Skipped")
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
                Text(
                    "Status resets on disconnect",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}
