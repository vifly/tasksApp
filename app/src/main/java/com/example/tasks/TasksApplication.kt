package com.example.tasks

import android.app.Application
import android.util.Log
import com.example.tasks.data.db.TaskDataSource
import com.example.tasks.data.preferences.SettingsRepository
import com.example.tasks.data.preferences.SyncMetadataRepository
import com.example.tasks.data.repositories.LogRepository
import com.example.tasks.data.repositories.TaskRepository
import com.example.tasks.data.services.TaskImportExportService
import com.example.tasks.data.services.WebDavClient
import com.example.tasks.data.sync.SyncOrchestrator
import com.example.tasks.data.sync.SyncScheduler
import com.example.tasks.utils.AppLog
import uniffi.sync.Logger
import uniffi.sync.initLogger

class TasksApplication : Application() {

    val settingsRepository by lazy { SettingsRepository(this) }
    val syncMetadataRepository by lazy { SyncMetadataRepository(this) }
    val logRepository by lazy { LogRepository(this) }

    val taskDataSource by lazy { TaskDataSource(this) }
    val webDavFactory by lazy { WebDavClient.Factory() }
    val taskRepository by lazy { TaskRepository(taskDataSource) }
    val taskImportExportService by lazy { TaskImportExportService(taskRepository, logRepository) }

    val syncOrchestrator by lazy {
        SyncOrchestrator(
            taskRepository,
            settingsRepository,
            syncMetadataRepository,
            webDavFactory,
            logRepository
        )
    }

    val syncScheduler by lazy { SyncScheduler(this, settingsRepository) }

    override fun onCreate() {
        super.onCreate()

        AppLog.init(logRepository)

        try {
            initLogger(object : Logger {
                override fun log(level: Int, tag: String, msg: String) {
                    logRepository.log(level, tag, msg)
                }
            })
        } catch (e: Exception) {
            Log.e("TasksApp", "Failed to bridge Rust logging", e)
        }
    }

    companion object {
        init {
            System.loadLibrary("sync")
        }
    }
}
