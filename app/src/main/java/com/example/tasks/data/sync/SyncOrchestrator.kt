package com.example.tasks.data.sync

import android.util.Log
import com.example.tasks.data.preferences.SettingsRepository
import com.example.tasks.data.preferences.SyncMetadataRepository
import com.example.tasks.data.repositories.TaskRepository
import com.example.tasks.data.services.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SyncOrchestrator handles the execution of the synchronization process.
 * Decoupled from WebDavClient construction via Factory.
 */
class SyncOrchestrator(
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val webDavFactory: WebDavClient.Factory
) {

    suspend fun performSync(): Result<String> = withContext(Dispatchers.IO) {
        val serverUrl = settingsRepository.serverUrl
        val username = settingsRepository.username
        val password = settingsRepository.password
        val deviceUuid = syncMetadataRepository.deviceUuid

        if (serverUrl.isBlank()) {
            return@withContext Result.failure(Exception("WebDAV 地址未配置"))
        }

        // Use factory instead of direct instantiation
        val client = webDavFactory.create(serverUrl, username, password)

        try {
            val updateData = taskRepository.getRustUpdate()
            if (updateData.isNotEmpty()) {
                val filename = "update_${deviceUuid}_${System.currentTimeMillis()}.bin"
                val relativePath = "updates/$filename"

                var uploaded = client.putFile(relativePath, updateData)
                if (!uploaded) {
                    if (client.createDirectory("updates")) {
                        uploaded = client.putFile(relativePath, updateData)
                    }
                }
                if (!uploaded) return@withContext Result.failure(Exception("上传失败"))
                addProcessedFile(filename)
            }

            val files = client.listFiles("updates")
            val processed = syncMetadataRepository.processedFiles
            var newCount = 0

            files.forEach { filename ->
                if (!processed.contains(filename) && !filename.contains(deviceUuid)) {
                    val data = client.getFile("updates/$filename")
                    if (data != null) {
                        taskRepository.applyRustUpdate(data)
                        addProcessedFile(filename)
                        newCount++
                    }
                } else if (filename.contains(deviceUuid) && !processed.contains(filename)) {
                    addProcessedFile(filename)
                }
            }

            if (newCount > 0) {
                taskRepository.syncRustToLocal()
            }

            syncMetadataRepository.lastSyncTime = System.currentTimeMillis()
            Result.success("同步成功: 收到 $newCount 条更新")

        } catch (e: Exception) {
            Log.e("SyncOrchestrator", "Sync failed", e)
            Result.failure(e)
        }
    }

    private fun addProcessedFile(filename: String) {
        val current = syncMetadataRepository.processedFiles.toMutableSet()
        current.add(filename)
        syncMetadataRepository.processedFiles = current
    }
}
