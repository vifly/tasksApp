package com.example.tasks.data.db

import android.content.ContentValues
import android.content.Context
import com.example.tasks.data.Task
import java.util.Date

class TaskDataSource(context: Context) {

    private val dbHelper = TaskDbHelper(context)

    fun getAllTasks(): List<Task> {
        val db = dbHelper.readableDatabase
        val cursor = db.query("tasks", null, null, null, null, null, null)
        val tasks = mutableListOf<Task>()
        with(cursor) {
            while (moveToNext()) {
                val task = Task(
                    id = getLong(getColumnIndexOrThrow("id")),
                    uuid = getString(getColumnIndexOrThrow("uuid")),
                    content = getString(getColumnIndexOrThrow("content")),
                    tags = getString(getColumnIndexOrThrow("tags")).split(",").map { it.trim() }
                        .filter { it.isNotEmpty() },
                    createdAt = Date(getLong(getColumnIndexOrThrow("created_at"))),
                    updatedAt = Date(getLong(getColumnIndexOrThrow("updated_at"))),
                    isPinned = getInt(getColumnIndexOrThrow("is_pinned")) == 1,
                    customSortOrder = getInt(getColumnIndexOrThrow("custom_sort_order"))
                )
                tasks.add(task)
            }
        }
        cursor.close()
        db.close()
        return tasks
    }

    fun addTask(task: Task): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("uuid", task.uuid)
            put("content", task.content)
            put("tags", task.tags.joinToString(","))
            put("created_at", task.createdAt.time)
            put("updated_at", task.updatedAt.time)
            put("is_pinned", if (task.isPinned) 1 else 0)
            put("custom_sort_order", task.customSortOrder)
        }
        val newRowId = db.insert("tasks", null, values)
        db.close()
        return newRowId
    }

    fun updateTask(task: Task) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("content", task.content)
            put("tags", task.tags.joinToString(","))
            put("updated_at", Date().time)
            put("is_pinned", if (task.isPinned) 1 else 0)
            put("custom_sort_order", task.customSortOrder)
        }
        db.update("tasks", values, "uuid = ?", arrayOf(task.uuid))
        db.close()
    }

    fun deleteTask(task: Task) {
        val db = dbHelper.writableDatabase
        db.delete("tasks", "uuid = ?", arrayOf(task.uuid))
        db.close()
    }

    fun deleteTasks(tasks: List<Task>) {
        val db = dbHelper.writableDatabase
        val ids = tasks.map { it.id.toString() }.toTypedArray()
        db.delete("tasks", "id IN (${ids.joinToString(",") { "?" }})", ids)
        db.close()
    }
}