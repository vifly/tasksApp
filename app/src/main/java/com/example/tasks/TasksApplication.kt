package com.example.tasks

import android.app.Application
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

class TasksApplication : Application() {

    // Lazy initialization speeds up app startup
    val taskDataSource by lazy { TaskDataSource(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val syncMetadataRepository by lazy { SyncMetadataRepository(this) }
    val logRepository by lazy { LogRepository(this, syncMetadataRepository) }
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
        // Bind the global facade to the repository
        AppLog.init(logRepository)
    }

    companion object {
        init {
            System.loadLibrary("sync")
        }
    }
}
