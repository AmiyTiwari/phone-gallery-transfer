package com.phonegallery.transfer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phonegallery.transfer.data.ConnectionType
import com.phonegallery.transfer.data.DiscoveredServer
import com.phonegallery.transfer.model.UploadState
import com.phonegallery.transfer.ui.components.PhotoGrid
import com.phonegallery.transfer.viewmodel.LaptopUiState
import com.phonegallery.transfer.viewmodel.LaptopViewModel

private enum class LaptopPage { SELECT, GRID, STATUS }

@Composable
fun LaptopScreen(modifier: Modifier = Modifier, vm: LaptopViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsState()
    val discoveredServers by vm.discoveredServers.collectAsState()
    val selectedServerUrls by vm.selectedServerUrls.collectAsState()
    val allPhotos by vm.allPhotos.collectAsState()
    val syncedNames by vm.syncedNames.collectAsState()
    val selected by vm.selected.collectAsState()
    val uploadStates by vm.uploadStates.collectAsState()
    val photoNames by vm.photoNames.collectAsState()
    val overallProgress by vm.overallProgress.collectAsState()

    val isUploading = uploadStates.values.any { m -> m.values.any { (_, s) -> s is UploadState.Uploading } }
    val hasSessionUploads = uploadStates.isNotEmpty()

    var page by remember { mutableStateOf(LaptopPage.SELECT) }

    LaunchedEffect(Unit) { if (uiState is LaptopUiState.Idle) vm.scan() }
    LaunchedEffect(uiState) { if (uiState is LaptopUiState.Idle) page = LaptopPage.SELECT }

    Column(modifier = modifier.fillMaxSize()) {

        // ── Header ────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            when (page) {
                LaptopPage.STATUS -> IconButton(onClick = { page = LaptopPage.GRID }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                LaptopPage.GRID -> IconButton(onClick = { vm.disconnect(); page = LaptopPage.SELECT }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Change servers")
                }
                else -> Icon(
                    Icons.Filled.Laptop,
                    contentDescription = null,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            val headerTitle = when (page) {
                LaptopPage.SELECT -> "Select Server"
                LaptopPage.GRID -> {
                    val n = selectedServerUrls.size
                    if (n == 1) discoveredServers.find { it.url == selectedServerUrls.first() }?.displayName ?: "Laptop"
                    else "$n servers"
                }
                LaptopPage.STATUS -> "Upload Status"
            }
            Text(headerTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        HorizontalDivider()

        // ── Content ───────────────────────────────────────────────────────
        when (page) {
            LaptopPage.SELECT -> SelectPage(
                uiState = uiState,
                servers = discoveredServers,
                selectedUrls = selectedServerUrls,
                onToggle = vm::toggleServer,
                onRescan = vm::scan,
                onConfirm = { vm.confirmSelection(); page = LaptopPage.GRID },
                modifier = Modifier.weight(1f),
            )

            LaptopPage.GRID -> {
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
                    OutlinedButton(onClick = vm::selectAll, modifier = Modifier.weight(1f)) {
                        Text("Select All")
                    }
                    if (hasSessionUploads) {
                        OutlinedButton(
                            onClick = { page = LaptopPage.STATUS },
                            modifier = Modifier.weight(1f),
                        ) { Text("View Status") }
                    }
                    Button(
                        onClick = { vm.uploadSelected(); page = LaptopPage.STATUS },
                        enabled = selected.isNotEmpty() && !isUploading,
                        modifier = Modifier.weight(1f),
                    ) { Text("Upload (${selected.size})") }
                }
            }

            LaptopPage.STATUS -> StatusPage(
                uploadStates = uploadStates,
                photoNames = photoNames,
                overallProgress = overallProgress,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SelectPage(
    uiState: LaptopUiState,
    servers: List<DiscoveredServer>,
    selectedUrls: Set<String>,
    onToggle: (String) -> Unit,
    onRescan: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is LaptopUiState.Scanning -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Looking for servers...", style = MaterialTheme.typography.bodyMedium)
                }
            }

            is LaptopUiState.NoneFound -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("No servers found", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Run 'bash start.sh' on your laptop.\nFor Wi-Fi: same network required.\nFor USB: use 'bash start.sh --usb' with cable.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRescan) { Text("Scan Again") }
                }
            }

            is LaptopUiState.ServerList -> {
                Text(
                    "Select one or more servers:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(servers, key = { it.url }) { server ->
                        ListItem(
                            headlineContent = { Text(server.displayName) },
                            leadingContent = {
                                Icon(
                                    if (server.type == ConnectionType.USB) Icons.Filled.Cable else Icons.Filled.Wifi,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            trailingContent = {
                                Checkbox(
                                    checked = server.url in selectedUrls,
                                    onCheckedChange = { onToggle(server.url) },
                                )
                            },
                        )
                        HorizontalDivider()
                    }
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onRescan, modifier = Modifier.weight(1f)) { Text("Rescan") }
                    Button(
                        onClick = onConfirm,
                        enabled = selectedUrls.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Continue (${selectedUrls.size})") }
                }
            }

            else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun StatusPage(
    uploadStates: Map<Long, Map<String, Pair<String, UploadState>>>,
    photoNames: Map<Long, String>,
    overallProgress: Pair<Int, Int>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (overallProgress.second > 0) {
            Text(
                "${overallProgress.first} / ${overallProgress.second} done",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            uploadStates.entries.forEach { (photoId, serverMap) ->
                item(key = "${photoId}_header") {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = photoNames[photoId] ?: "Photo #$photoId",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
                items(serverMap.entries.toList(), key = { "${photoId}_${it.key}" }) { (_, entry) ->
                    val (serverDisplayName, state) = entry
                    ListItem(
                        headlineContent = {
                            Text(serverDisplayName, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        },
                        trailingContent = {
                            when (state) {
                                is UploadState.Uploading -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                UploadState.Success -> Text("✓ Done", color = MaterialTheme.colorScheme.primary)
                                is UploadState.Error -> Text("✗ ${state.message}", color = MaterialTheme.colorScheme.error)
                                UploadState.Idle -> Text("Queued", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                UploadState.Skipped -> Text("Skipped")
                            }
                        },
                        modifier = Modifier.padding(start = 16.dp),
                    )
                    HorizontalDivider()
                }
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
