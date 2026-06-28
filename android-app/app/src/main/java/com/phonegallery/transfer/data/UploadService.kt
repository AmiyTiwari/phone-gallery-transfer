package com.phonegallery.transfer.data

import android.content.Context
import com.phonegallery.transfer.model.Photo
import com.phonegallery.transfer.model.UploadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class UploadService(
    private val context: Context,
    private val client: OkHttpClient,
    private val baseUrl: String,
) {

    fun upload(photo: Photo): Flow<UploadState> = flow {
        emit(UploadState.Uploading(0f))

        val bytes = try {
            context.contentResolver.openInputStream(photo.uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }

        if (bytes == null) {
            emit(UploadState.Error("Cannot read photo"))
            return@flow
        }

        emit(UploadState.Uploading(0.5f))

        val fileBody = bytes.toRequestBody("application/octet-stream".toMediaType())

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", photo.displayName, fileBody)
            .addFormDataPart("original_name", photo.displayName)
            .addFormDataPart("modified_ts", photo.dateModified.toString())
            .build()

        val request = Request.Builder()
            .url("$baseUrl/upload")
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                emit(UploadState.Success)
            } else {
                emit(UploadState.Error("Server error ${response.code}"))
            }
        } catch (e: IOException) {
            emit(UploadState.Error(e.message ?: "Network error"))
        }
    }.flowOn(Dispatchers.IO)
}
