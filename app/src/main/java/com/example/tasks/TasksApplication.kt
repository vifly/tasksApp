package com.example.tasks

import android.app.Application
import com.example.tasks.data.db.TaskDataSource

class TasksApplication : Application() {

    lateinit var taskDataSource: TaskDataSource

    override fun onCreate() {
        super.onCreate()
        // Load the Rust library for UniFFI
        System.loadLibrary("sync")
        taskDataSource = TaskDataSource(this)
    }
}
