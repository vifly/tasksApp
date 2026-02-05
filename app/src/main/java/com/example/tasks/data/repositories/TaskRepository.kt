package com.example.tasks.data.repositories

import android.util.Log
import com.example.tasks.data.Task
import com.example.tasks.data.db.TaskDataSource
import com.example.tasks.data.serialization.TaskSerializer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import uniffi.sync.TaskDocument
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TaskRepository handles the coordination between local SQLite and Rust CRDT.
 */
class TaskRepository(private val dataSource: TaskDataSource) {

    // Lazy load Rust core to avoid blocking UI thread during cold start.
    private val taskDocument by lazy { TaskDocument() }

    private val isInitialized = AtomicBoolean(false)
    private val dataMutex = Mutex()

    private val _onDataChanged = MutableSharedFlow<Unit>(replay = 0)
    val onDataChanged: SharedFlow<Unit> = _onDataChanged.asSharedFlow()

    suspend fun initialize() {
        if (isInitialized.compareAndSet(false, true)) {
            dataMutex.withLock {
                cleanUpDuplicatesInternal()
                val tasks = dataSource.getAllTasks()
                if (tasks.isNotEmpty()) {
                    syncLocalToRustInternal(tasks)
                }
                _onDataChanged.emit(Unit)
            }
        }
    }

    suspend fun getAllTasks(): List<Task> = dataMutex.withLock {
        dataSource.getAllTasks()
    }

    suspend fun addTask(task: Task): Unit = dataMutex.withLock {
        dataSource.addTask(task)
        taskDocument.addTask(TaskSerializer.serialize(task))
        _onDataChanged.emit(Unit)
    }

    suspend fun updateTask(task: Task): Unit = dataMutex.withLock {
        dataSource.updateTask(task)
        taskDocument.updateTask(task.uuid, TaskSerializer.serialize(task))
        _onDataChanged.emit(Unit)
    }

    suspend fun deleteTasks(tasks: List<Task>): Unit = dataMutex.withLock {
        dataSource.deleteTasks(tasks)
        tasks.forEach { taskDocument.deleteTask(it.uuid) }
        _onDataChanged.emit(Unit)
    }

    suspend fun syncRustToLocal(): Boolean = dataMutex.withLock {
        val changed = syncRustToLocalInternal()
        if (changed) {
            _onDataChanged.emit(Unit)
        }
        changed
    }

    suspend fun getRustUpdate(): ByteArray = dataMutex.withLock {
        taskDocument.getUpdate()
    }

    suspend fun applyRustUpdate(update: ByteArray): Unit = dataMutex.withLock {
        taskDocument.applyUpdate(update)
    }

    private fun cleanUpDuplicatesInternal(): Boolean {
        val tasks = dataSource.getAllTasks()
        val duplicates = tasks.groupBy { it.uuid }.filter { it.value.size > 1 }
        if (duplicates.isEmpty()) return false

        duplicates.forEach { (_, sameUuidTasks) ->
            val sorted = sameUuidTasks.sortedByDescending { it.updatedAt.time }
            val toKeep = sorted.first()
            dataSource.deleteTasks(sameUuidTasks)
            dataSource.addTask(toKeep)
        }
        return true
    }

    private fun syncLocalToRustInternal(tasks: List<Task>) {
        try {
            val jsonArray = JSONArray()
            tasks.forEach { jsonArray.put(org.json.JSONObject(TaskSerializer.serialize(it))) }
            taskDocument.restoreFromJson(jsonArray.toString())
            Log.d("TaskRepository", "Initialized Rust document with ${tasks.size} tasks")
        } catch (e: Exception) {
            Log.e("TaskRepository", "syncLocalToRustInternal failed", e)
        }
    }

    private fun syncRustToLocalInternal(): Boolean {
        return try {
            val jsonString = taskDocument.getAllTasksJson()
            Log.d("TaskRepository", "Rust JSON: $jsonString")
            val jsonArray = JSONArray(jsonString)
            val currentLocalTasks = dataSource.getAllTasks().associateBy { it.uuid }
            val processedUuids = mutableSetOf<String>()
            var changeCount = 0

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val task = TaskSerializer.deserialize(obj.toString())

                if (task.uuid in processedUuids) continue
                processedUuids.add(task.uuid)

                val localTask = currentLocalTasks[task.uuid]
                val taskWithId = task.copy(id = localTask?.id ?: 0)

                if (taskWithId.id == 0L) {
                    dataSource.addTask(taskWithId)
                    changeCount++
                } else if (hasTaskChanged(localTask!!, taskWithId)) {
                    dataSource.updateTask(taskWithId)
                    changeCount++
                }
            }

            currentLocalTasks.keys.filter { it !in processedUuids }.forEach { uuid ->
                currentLocalTasks[uuid]?.let {
                    dataSource.deleteTask(it)
                    changeCount++
                }
            }
            changeCount > 0
        } catch (e: Exception) {
            Log.e("TaskRepository", "syncRustToLocalInternal failed", e)
            false
        }
    }

    private fun hasTaskChanged(local: Task, remote: Task): Boolean {
        return local.content != remote.content ||
                local.isPinned != remote.isPinned ||
                local.tags != remote.tags ||
                local.customSortOrder != remote.customSortOrder
    }
}