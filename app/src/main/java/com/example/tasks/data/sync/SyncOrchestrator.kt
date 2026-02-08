package com.example.tasks.data.sync

import android.util.Log
import com.example.tasks.data.preferences.SettingsRepository
import com.example.tasks.data.preferences.SyncMetadataRepository
import com.example.tasks.data.repositories.LogRepository
import com.example.tasks.data.repositories.TaskRepository
import com.example.tasks.data.services.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SyncOrchestrator coordinates the complex data synchronization process.
 */
class SyncOrchestrator(
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val webDavFactory: WebDavClient.Factory,
    private val logRepository: LogRepository
) {

    suspend fun performSync(triggerSource: String = "Unknown"): Result<String> =
        withContext(Dispatchers.IO) {
            logRepository.logNetworkSnapshot()

            logRepository.log(Log.INFO, "SyncOrch", ">>> Sync started [Source: $triggerSource]")

            val serverUrl = settingsRepository.serverUrl
            val username = settingsRepository.username
            val password = settingsRepository.password
            val deviceUuid = syncMetadataRepository.deviceUuid

            if (serverUrl.isBlank()) {
                val msg = "跳过同步: WebDAV 地址未配置"
                logRepository.log(Log.WARN, "SyncOrch", "Sync skipped: WebDAV URL not configured")
                return@withContext Result.failure(Exception(msg))
            }

            val client = webDavFactory.create(serverUrl, username, password)

            try {
                val updateData = taskRepository.getRustUpdate()
                if (updateData.isNotEmpty()) {
                    val filename = "update_${deviceUuid}_${System.currentTimeMillis()}.bin"
                    logRepository.log(
                        Log.INFO,
                        "SyncOrch",
                        "PUSH: Detected local changes (Update Size: ${updateData.size} bytes)"
                    )

                    var uploaded = client.putFile("updates/$filename", updateData)
                    if (!uploaded) {
                        if (client.createDirectory("updates")) {
                            uploaded = client.putFile("updates/$filename", updateData)
                        }
                    }

                    if (!uploaded) {
                        logRepository.log(
                            Log.ERROR,
                            "SyncOrch",
                            "PUSH: Final upload failed, check network or permissions"
                        )
                        return@withContext Result.failure(Exception("上传失败"))
                    }

                    addProcessedFile(filename)
                    logRepository.log(
                        Log.INFO,
                        "SyncOrch",
                        "PUSH: Local changes uploaded successfully"
                    )
                }

                val remoteFiles = client.listFiles("updates")
                val processed = syncMetadataRepository.processedFiles
                val toDownload = remoteFiles.filter { it !in processed && !it.contains(deviceUuid) }

                if (toDownload.isNotEmpty()) {
                    logRepository.log(
                        Log.INFO,
                        "SyncOrch",
                        "PULL: Found ${toDownload.size} new remote updates"
                    )
                    var downloadedCount = 0
                    toDownload.forEach { filename ->
                        val data = client.getFile("updates/$filename")
                        if (data != null) {
                            taskRepository.applyRustUpdate(data)
                            addProcessedFile(filename)
                            downloadedCount++
                        }
                    }
                    logRepository.log(
                        Log.DEBUG,
                        "SyncOrch",
                        "PULL: Successfully downloaded $downloadedCount/${toDownload.size} files"
                    )
                }

                taskRepository.syncRustToLocal()

                syncMetadataRepository.lastSyncTime = System.currentTimeMillis()
                logRepository.log(Log.INFO, "SyncOrch", "<<< Sync completed successfully")

                Result.success("同步成功")

            } catch (e: Exception) {
                logRepository.log(Log.ERROR, "SyncOrch", "FATAL: Sync crashed: ${e.message}")
                Result.failure(e)
            } finally {
                // CRITICAL: Ensure all logs (including Rust logs just flushed) are written to disk
                // before the WorkManager process potentially terminates.
                logRepository.flush()
            }
        }

    private fun addProcessedFile(filename: String) {
        val current = syncMetadataRepository.processedFiles.toMutableSet()
        current.add(filename)
        syncMetadataRepository.processedFiles = current
    }
}
