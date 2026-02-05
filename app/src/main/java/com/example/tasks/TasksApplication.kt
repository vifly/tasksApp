package com.example.tasks

import android.app.Application
import com.example.tasks.data.db.TaskDataSource
import com.example.tasks.data.services.WebDavClient
import com.example.tasks.data.preferences.SettingsRepository
import com.example.tasks.data.preferences.SyncMetadataRepository
import com.example.tasks.data.repositories.TaskRepository
import com.example.tasks.data.services.TaskImportService
import com.example.tasks.data.sync.SyncOrchestrator
import com.example.tasks.data.sync.SyncScheduler

class TasksApplication : Application() {

    // Lazy initialization speeds up app startup
    val taskDataSource by lazy { TaskDataSource(this) }
    val taskRepository by lazy { TaskRepository(taskDataSource) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val syncMetadataRepository by lazy { SyncMetadataRepository(this) }
    val taskImportService by lazy { TaskImportService(taskRepository) }
    val syncOrchestrator by lazy { 
        val webDavFactory = WebDavClient.Factory()
        SyncOrchestrator(taskRepository, settingsRepository, syncMetadataRepository, webDavFactory) 
    }
    
    val syncScheduler by lazy { SyncScheduler(this, settingsRepository) }

    override fun onCreate() {
        super.onCreate()
    }

    companion object {
        init {
            System.loadLibrary("sync")
        }
    }
}