package com.example.tasks.data.services

import android.util.Xml
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class WebDavClient(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {

    class Factory {
        fun create(url: String, user: String, pass: String): WebDavClient {
            return WebDavClient(url, user, pass)
        }
    }

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

    /**
     * Tries to create the directory (collection) at the server URL or relative path.
     * Checks if it exists first to avoid ambiguity with 405 errors.
     */

    fun createDirectory(relativePath: String = ""): Boolean {

        if (serverUrl.isBlank()) return false

        val url =
            if (relativePath.isNotEmpty()) normalizeUrl(serverUrl, relativePath) else serverUrl

        if (checkPathExists(url)) return true

        val request = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .method("MKCOL", null)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                // 201 Created is the only definitive success for MKCOL
                response.code == 201
            }

        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun checkPathExists(url: String): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .header("Depth", "0")
            .method("PROPFIND", null)
            .build()

        return try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: IOException) {
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

    /**
     * Lists files in the directory.
     * Returns a list of filenames (or relative paths) ending in .bin.
     */
    fun listFiles(relativePath: String = ""): List<String> {
        if (serverUrl.isBlank()) return emptyList()

        val url =
            if (relativePath.isNotEmpty()) normalizeUrl(serverUrl, relativePath) else serverUrl

        val request = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .header("Depth", "1")
            .method("PROPFIND", null)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                parseWebDavXml(body)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseWebDavXml(xml: String): List<String> {
        val files = mutableListOf<String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name.contains(
                        "href",
                        ignoreCase = true
                    )
                ) {
                    val href = parser.nextText()
                    val decoded = URLDecoder.decode(href, "UTF-8")
                    if (decoded.endsWith(".bin", ignoreCase = true)) {
                        val filename = decoded.substringAfterLast('/')
                        if (filename.isNotEmpty()) {
                            files.add(filename)
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return files
    }

    private fun normalizeUrl(base: String, relative: String): String {
        val baseUrl = if (base.endsWith("/")) base else "$base/"
        val relUrl = if (relative.startsWith("/")) relative.substring(1) else relative
        return baseUrl + relUrl
    }
}