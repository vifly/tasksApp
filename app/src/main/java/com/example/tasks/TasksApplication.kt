package com.example.tasks

import android.app.Application
import com.example.tasks.data.db.TaskDataSource
import com.example.tasks.data.repositories.TaskRepository

class TasksApplication : Application() {

    lateinit var taskDataSource: TaskDataSource
    lateinit var taskRepository: TaskRepository

    override fun onCreate() {
        super.onCreate()
        // Load the Rust library for UniFFI
        System.loadLibrary("sync")
        taskDataSource = TaskDataSource(this)
        taskRepository = TaskRepository(taskDataSource)
    }
}