package com.phonegallery.transfer.model

sealed class UploadState {
    object Idle : UploadState()
    data class Uploading(val progress: Float) : UploadState()   // 0f..1f
    object Success : UploadState()
    data class Error(val message: String) : UploadState()
    object Skipped : UploadState()   // already synced per manifest
}
