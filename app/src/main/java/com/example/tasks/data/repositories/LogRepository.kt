package com.example.tasks.data.repositories

import android.content.Context
import android.util.Log
import com.example.tasks.data.preferences.SyncMetadataRepository
import com.example.tasks.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class LogRepository(
    context: Context,
    private val metadataRepository: SyncMetadataRepository
) {
    private val appContext = context.applicationContext
    private val logFile = File(context.filesDir, "debug_logs.txt")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val logcatTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private val maxFileSize = 1 * 1024 * 1024 // 1MB
    private val logChannel = Channel<String>(capacity = 500)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val pendingCount = AtomicInteger(0)

    init {
        scope.launch {
            for (entry in logChannel) {
                try {
                    writeEntryToFile(entry)
                } finally {
                    pendingCount.decrementAndGet()
                }
            }
        }
    }

    fun log(priority: Int, tag: String, message: String) {
        Log.println(priority, tag, message)
        val timestamp = dateFormat.format(Date())
        val levelStr = getLevelString(priority)
        val entry = "$timestamp $levelStr/$tag: $message"

        pendingCount.incrementAndGet()
        scope.launch {
            logChannel.send(entry)
        }
    }

    /**
     * Captures and logs the current network environment.
     */
    fun logNetworkSnapshot() {
        val snapshot = NetworkUtils.getNetworkSnapshot(appContext)
        log(Log.INFO, "Network", "Current State: $snapshot")
    }

    suspend fun flush() {
        withTimeoutOrNull(5000) { // 5s safety timeout
            while (pendingCount.get() > 0) {
                delay(50)
            }
        }
    }

    private fun writeEntryToFile(entry: String) {
        try {
            logFile.appendText(entry + System.lineSeparator())
            checkAndPruneLogs()
        } catch (e: Exception) {
            Log.e("LogRepository", "File write failed", e)
        }
    }

    suspend fun flushRustLogs() = withContext(Dispatchers.IO) {
        fetchNewRustLogs().forEach {
            writeEntryToFile(it)
        }
    }

    private fun fetchNewRustLogs(): List<String> {
        val logs = mutableListOf<String>()
        val lastFetchTime = metadataRepository.lastLogFetchTime
        val currentYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())

        try {
            val pid = android.os.Process.myPid()
            val cmd =
                "logcat -d -v time RustStdout:V sync:V uniffi:V android_logger:V *:S --pid=$pid"
            val process = Runtime.getRuntime().exec(cmd)
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var maxTimeSeen = lastFetchTime

            while (true) {
                val line = reader.readLine() ?: break
                if (line.length > 18) {
                    try {
                        val timePart = line.substring(0, 18)
                        val fullTimeStr = "$currentYear-$timePart"
                        val parseDate = logcatTimeFormat.parse(fullTimeStr)

                        if (parseDate != null && parseDate.time > lastFetchTime) {
                            logs.add(fullTimeStr + line.substring(18))
                            if (parseDate.time > maxTimeSeen) {
                                maxTimeSeen = parseDate.time
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
            }
            metadataRepository.lastLogFetchTime = maxTimeSeen
        } catch (e: Exception) {
            Log.e("LogRepository", "Failed to fetch rust logs", e)
        }
        return logs
    }

    suspend fun getPersistentLogs(): List<String> = withContext(Dispatchers.IO) {
        if (logFile.exists()) logFile.readLines() else emptyList()
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        if (logFile.exists()) logFile.writeText("")
        metadataRepository.lastLogFetchTime = System.currentTimeMillis()
    }

    private fun checkAndPruneLogs() {
        if (logFile.exists() && logFile.length() > maxFileSize) {
            try {
                val lines = logFile.readLines()
                val half = lines.size / 2
                logFile.writeText("--- Log pruned due to size limit ---\n")
                lines.drop(half).forEach { logFile.appendText(it + System.lineSeparator()) }
            } catch (e: Exception) {
            }
        }
    }

    private fun getLevelString(priority: Int): String {
        return when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "?"
        }
    }
}
