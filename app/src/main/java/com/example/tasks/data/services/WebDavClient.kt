package com.example.tasks.data.services

import com.example.tasks.utils.AppLog
import com.example.tasks.utils.NetworkUtils
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
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
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val credential = Credentials.basic(username, password)

    /**
     * Checks connection by performing a PROPFIND request to the base URL.
     * Returns true if successful (2xx or 401/403 with valid server response), false otherwise.
     */
    fun checkConnection(): Boolean {
        if (serverUrl.isBlank()) return false

        if (!NetworkUtils.isServerReachable(serverUrl)) {
            AppLog.w("WebDAV", "Connection check: Server port unreachable")
            return false
        }

        val request = Request.Builder()
            .url(serverUrl)
            .header("Authorization", credential)
            .header("Depth", "0") // Only check the folder itself
            .method("PROPFIND", null)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                AppLog.d("WebDAV", "Connection check: HTTP ${response.code}")
                response.isSuccessful
            }
        } catch (e: Exception) {
            AppLog.e("WebDAV", "Connection check failed: ${e.message}")
            false
        }
    }

    /**
     * Tries to create the directory (collection) at the server URL or relative path.
     * Checks if it exists first to avoid ambiguity with 405 errors.
     */
    suspend fun createDirectory(relativePath: String = ""): Boolean {
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
                AppLog.i("WebDAV", "MKCOL ($relativePath): ${response.code}")
                response.code == 201
            }
        } catch (e: Exception) {
            AppLog.e("WebDAV", "MKCOL exception: ${e.message}")
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
        } catch (e: Exception) {
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
                if (!response.isSuccessful) {
                    AppLog.e("WebDAV", "PUT failed ($relativePath): ${response.code}")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            AppLog.e("WebDAV", "PUT exception ($relativePath): ${e.message}")
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
                    AppLog.e("WebDAV", "GET failed ($relativePath): ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            AppLog.e("WebDAV", "GET exception ($relativePath): ${e.message}")
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

        val files = mutableListOf<String>()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLog.w("WebDAV", "LIST failed: ${response.code}")
                    return emptyList()
                }
                val body = response.body?.string() ?: return emptyList()
                parsePropfindResponse(body, files)
            }
        } catch (e: Exception) {
            AppLog.e("WebDAV", "LIST exception: ${e.message}")
        }
        return files
    }

    private fun parsePropfindResponse(xml: String, fileList: MutableList<String>) {
        try {
            val factory = XmlPullParserFactory.newInstance()
            val xpp = factory.newPullParser()
            xpp.setInput(StringReader(xml))

            var eventType = xpp.eventType
            var currentHref = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && xpp.name.contains(
                        "href",
                        ignoreCase = true
                    )
                ) {
                    currentHref = xpp.nextText()
                    val filename = currentHref.substringBeforeLast("/")
                        .let { currentHref.substringAfterLast("/") }
                    if (filename.isNotEmpty() && filename.endsWith(".bin")) {
                        fileList.add(filename)
                    }
                }
                eventType = xpp.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun normalizeUrl(baseUrl: String, path: String): String {
        val base = baseUrl.trim().removeSuffix("/")
        val sub = path.trim().removePrefix("/")
        return "$base/$sub"
    }
}