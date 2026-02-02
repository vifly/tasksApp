package com.example.tasks.data.network

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class WebDavClient(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val credential = Credentials.basic(username, password)

    /**
     * Checks connection by performing a PROPFIND request to the base URL.
     * Returns true if successful (2xx or 401/403 with valid server response), false otherwise.
     */
    fun checkConnection(): Boolean {
        if (serverUrl.isBlank()) return false

        val request = Request.Builder()
            .url(serverUrl)
            .header("Authorization", credential)
            .header("Depth", "0") // Only check the folder itself
            .method("PROPFIND", null)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun createDirectory(): Boolean {
        if (serverUrl.isBlank()) return false

        val request = Request.Builder()
            .url(serverUrl)
            .header("Authorization", credential)
            .method("MKCOL", null)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                // 201 Created is success. 405 Method Not Allowed implies it already exists (also fine-ish, but checkConnection handles exists)
                response.code == 201
            }
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Uploads a file to the specified path (relative to serverUrl).
     */
    fun putFile(relativePath: String, data: ByteArray): Boolean {
        val url = normalizeUrl(serverUrl, relativePath)
        val body = data.toRequestBody("application/octet-stream".toMediaType())

        val request = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .put(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 201 || response.code == 204
            }
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Downloads a file from the specified path.
     * Returns ByteArray or null if failed/not found.
     */
    fun getFile(relativePath: String): ByteArray? {
        val url = normalizeUrl(serverUrl, relativePath)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else {
                    null
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun normalizeUrl(base: String, relative: String): String {
        val baseUrl = if (base.endsWith("/")) base else "$base/"
        val relUrl = if (relative.startsWith("/")) relative.substring(1) else relative
        return baseUrl + relUrl
    }
}
