package com.example.tasks.data.repositories

import android.util.Log
import com.example.tasks.data.Task
import com.example.tasks.data.db.TaskDataSource
import org.json.JSONArray
import org.json.JSONException
import uniffi.sync.TaskDocument
import java.util.Date

class TaskRepository(private val dataSource: TaskDataSource) {

    // Rust CRDT Document
    // Held here to maintain state across ViewModel lifecycles if scoped to App,
    // but typically Repository is singleton-like.
    private val taskDocument = TaskDocument()

    init {
        // We can't use coroutines easily here without a scope,
        // usually initialization happens on first access or explicit init.
        // For now, we'll keep the suspend methods and let ViewModel/UseCase call them.
    }

    fun getAllTasks(): List<Task> {
        return dataSource.getAllTasks()
    }

    /**
     * Initializes Rust document from local SQLite.
     * Should be called once at app startup.
     */
    suspend fun initialize() {
        val tasks = dataSource.getAllTasks()
        if (tasks.isNotEmpty()) {
            syncLocalToRust(tasks)
        }
    }

    // Pushes all local SQLite data to Rust (Overwrites Rust state)
    suspend fun syncLocalToRust(tasks: List<Task>) {
        try {
            val jsonArray = JSONArray()
            tasks.forEach { task ->
                jsonArray.put(taskToJsonObject(task))
            }
            taskDocument.restoreFromJson(jsonArray.toString())
            Log.d("TaskRepository", "Initialized Rust document with ${tasks.size} tasks")
        } catch (e: Exception) {
            Log.e("TaskRepository", "Failed to sync local to Rust", e)
        }
    }

    // Pulls data from Rust to SQLite (Diff Logic)
    // Returns true if changes were made
    suspend fun syncRustToLocal(): Boolean {
        return try {
            val jsonString = taskDocument.getAllTasksJson()
            Log.d("TaskRepository", "Rust JSON: $jsonString")
            val jsonArray = JSONArray(jsonString)

            val currentLocalTasks = dataSource.getAllTasks().associateBy { it.uuid }
            val newTasks = mutableListOf<Task>()
            val processedUuids = mutableSetOf<String>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val uuid = obj.getString("uuid")
                processedUuids.add(uuid)

                val localTask = currentLocalTasks[uuid]

                val task = Task(
                    id = localTask?.id ?: 0,
                    uuid = uuid,
                    content = obj.getString("content"),
                    isPinned = obj.optBoolean("is_pinned", false),
                    createdAt = Date(obj.optLong("created_at", System.currentTimeMillis())),
                    updatedAt = Date(obj.optLong("updated_at", System.currentTimeMillis())),
                    tags = List(obj.getJSONArray("tags").length()) { idx ->
                        obj.getJSONArray("tags").getString(idx)
                    },
                    customSortOrder = i
                )
                newTasks.add(task)
            }

            var changeCount = 0

            currentLocalTasks.keys.filter { it !in processedUuids }.forEach { uuid ->
                currentLocalTasks[uuid]?.let {
                    dataSource.deleteTask(it)
                    changeCount++
                }
            }

            newTasks.forEach { task ->
                if (task.id == 0L) {
                    dataSource.addTask(task)
                    changeCount++
                } else {
                    val local = currentLocalTasks[task.uuid]
                    if (local != null && hasTaskChanged(local, task)) {
                        dataSource.updateTask(task)
                        changeCount++
                    }
                }
            }

            if (changeCount > 0) {
                Log.d("TaskRepository", "Synced Rust -> Local ($changeCount changes)")
            }
            changeCount > 0

        } catch (e: JSONException) {
            Log.e("TaskRepository", "Failed to parse Rust JSON", e)
            false
        }
    }

    suspend fun addTask(task: Task) {
        dataSource.addTask(task)
        taskDocument.addTask(taskToJsonObject(task).toString())
        Log.d("TaskRepository", "addTask: Added to Rust & SQLite")
    }

    suspend fun updateTask(task: Task) {
        dataSource.updateTask(task)
        taskDocument.updateTask(task.uuid, taskToJsonObject(task).toString())
        Log.d("TaskRepository", "updateTask: Updated Rust & SQLite")
    }

    suspend fun deleteTasks(tasks: List<Task>) {
        dataSource.deleteTasks(tasks)
        tasks.forEach { taskDocument.deleteTask(it.uuid) }
        Log.d("TaskRepository", "deleteTasks: Deleted from Rust & SQLite")
    }

    // Helper: Rust Object Access (for future Sync Manager)
    fun getRustUpdate(): ByteArray {
        return taskDocument.getUpdate()
    }

    fun applyRustUpdate(update: ByteArray) {
        taskDocument.applyUpdate(update)
    }

    private fun hasTaskChanged(local: Task, remote: Task): Boolean {
        return local.content != remote.content ||
                local.isPinned != remote.isPinned ||
                local.tags != remote.tags ||
                local.customSortOrder != remote.customSortOrder
    }

    private fun taskToJsonObject(task: Task): org.json.JSONObject {
        val jsonObject = org.json.JSONObject()
        jsonObject.put("uuid", task.uuid)
        jsonObject.put("content", task.content)
        jsonObject.put("is_pinned", task.isPinned)
        jsonObject.put("created_at", task.createdAt.time)
        jsonObject.put("updated_at", task.updatedAt.time)
        jsonObject.put("tags", JSONArray(task.tags))
        return jsonObject
    }
}
