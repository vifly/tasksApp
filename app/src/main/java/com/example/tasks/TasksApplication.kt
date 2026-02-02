package com.example.tasks

import android.app.Application
import com.example.tasks.data.db.TaskDataSource
import com.example.tasks.data.preferences.SettingsRepository
import com.example.tasks.data.repositories.TaskRepository

class TasksApplication : Application() {

    lateinit var taskDataSource: TaskDataSource
    lateinit var taskRepository: TaskRepository
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        // Load the Rust library for UniFFI
        System.loadLibrary("sync")
        taskDataSource = TaskDataSource(this)
        taskRepository = TaskRepository(taskDataSource)
        settingsRepository = SettingsRepository(this)
    }
}