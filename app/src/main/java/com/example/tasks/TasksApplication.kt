package com.example.tasks

import android.app.Application
import com.example.tasks.data.db.TaskDataSource

class TasksApplication : Application() {

    lateinit var taskDataSource: TaskDataSource

    override fun onCreate() {
        super.onCreate()
        taskDataSource = TaskDataSource(this)
    }
}
