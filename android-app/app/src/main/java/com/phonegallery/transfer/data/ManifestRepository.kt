package com.phonegallery.transfer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ManifestRepository(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {

    /**
     * Returns set of original filenames already synced to the laptop.
     * Empty set on any network failure (fail-open: let user re-upload if needed).
     */
    suspend fun fetchSyncedFileNames(): Set<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/manifest").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptySet()
                val body = response.body?.string() ?: return@withContext emptySet()
                val json = JSONObject(body)
                val arr = json.getJSONArray("files")
                buildSet {
                    for (i in 0 until arr.length()) {
                        val entry = arr.getJSONObject(i)
                        if (entry.has("original_name")) add(entry.getString("original_name"))
                    }
                }
            }
        } catch (_: IOException) {
            emptySet()
        }
    }

    suspend fun isServerReachable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/ping").build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: IOException) {
            false
        }
    }
}
