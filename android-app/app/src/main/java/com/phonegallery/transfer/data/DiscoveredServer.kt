package com.phonegallery.transfer.data

enum class ConnectionType { USB, WIFI }

data class DiscoveredServer(
    val url: String,
    val type: ConnectionType,
    val displayName: String,
)
