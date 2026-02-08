package com.example.tasks.data.repositories

import com.example.tasks.data.Task
import com.example.tasks.data.db.TaskDataSource
import com.example.tasks.data.serialization.TaskSerializer
import com.example.tasks.utils.AppLog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import uniffi.sync.TaskDocument
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TaskRepository handles the coordination between local SQLite and Rust CRDT.
 */
class TaskRepository(
    private val dataSource: TaskDataSource
) {

    private val taskDocument by lazy { TaskDocument() }
    private val isInitialized = AtomicBoolean(false)
    private val dataMutex = Mutex()

    private val _onDataChanged = MutableSharedFlow<Unit>(replay = 0)
    val onDataChanged: SharedFlow<Unit> = _onDataChanged.asSharedFlow()

    suspend fun initialize() {
        if (isInitialized.compareAndSet(false, true)) {
            validateAndRepairData()
        }
    }

    suspend fun getAllTasks(): List<Task> = dataMutex.withLock {
        dataSource.getAllTasks()
    }

    suspend fun addTask(task: Task): Unit = dataMutex.withLock {
        dataSource.addTask(task)
        taskDocument.addTask(TaskSerializer.serialize(task))
        AppLog.d("Repository", "Task added: ${task.uuid.take(8)}")
        _onDataChanged.emit(Unit)
    }

    suspend fun updateTask(task: Task): Unit = dataMutex.withLock {
        dataSource.updateTask(task)
        taskDocument.updateTask(task.uuid, TaskSerializer.serialize(task))
        AppLog.d("Repository", "Task updated: ${task.uuid.take(8)}")
        _onDataChanged.emit(Unit)
    }

    suspend fun deleteTasks(tasks: List<Task>): Unit = dataMutex.withLock {
        dataSource.deleteTasks(tasks)
        tasks.forEach {
            taskDocument.deleteTask(it.uuid)
            AppLog.d("Repository", "Task deleted: ${it.uuid.take(8)}")
        }
        _onDataChanged.emit(Unit)
    }

    /**
     * Scans database for inconsistencies (duplicate UUIDs, corrupted sort weights)
     * and performs automatic repair. Synchronizes changes to the Rust engine.
     */
    suspend fun validateAndRepairData() = dataMutex.withLock {
        val cleaned = cleanUpDuplicatesInternal()
        val rebalanced = checkAndRebalanceWeightsInternal()

        if (cleaned || rebalanced) {
            // Data structure changed, force sync state to Rust engine
            syncLocalToRustInternal(dataSource.getAllTasks())
            _onDataChanged.emit(Unit)
        }
    }

    suspend fun syncRustToLocal(): Boolean = dataMutex.withLock {
        val stats = syncRustToLocalInternal()
        val totalChanges = stats.added + stats.updated + stats.deleted
        if (totalChanges > 0) {
            AppLog.i(
                "Repository",
                "Merged changes from Rust: +${stats.added}, ~${stats.updated}, -${stats.deleted}"
            )
            _onDataChanged.emit(Unit)
        }
        totalChanges > 0
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

        duplicates.forEach { (uuid, sameUuidTasks) ->
            val sorted = sameUuidTasks.sortedByDescending { it.updatedAt.time }
            val toKeep = sorted.first()
            AppLog.w("Repository", "Cleaning duplicate UUID: $uuid")
            dataSource.deleteTasks(sameUuidTasks)
            dataSource.addTask(toKeep)
        }
        return true
    }

    private fun checkAndRebalanceWeightsInternal(): Boolean {
        val tasks = dataSource.getAllTasks()
        val weights = tasks.map { it.customSortOrder }
        val hasZero = weights.any { it == 0L }
        val hasDuplicates = weights.size != weights.distinct().size

        if (hasZero || hasDuplicates) {
            AppLog.w("Repository", "Rebalancing weights: Zero=$hasZero, Duplicates=$hasDuplicates")

            // Sort by current weight (descending) to preserve relative order where possible.
            // Fallback to update time if weights are identical (e.g. all 0).
            val sortedTasks = tasks.sortedWith(
                compareByDescending<Task> { it.isPinned }
                    .thenByDescending { it.customSortOrder }
                    .thenByDescending { it.updatedAt }
            )

            val baseTime = System.currentTimeMillis()
            val step = 1_000_000L
            val now = Date()

            sortedTasks.forEachIndexed { index, task ->
                // New weight: Decreasing from baseTime, with large gaps
                val newWeight = baseTime - (index * step)
                if (task.customSortOrder != newWeight) {
                    val rebalancedTask = task.copy(
                        customSortOrder = newWeight,
                        updatedAt = now // Must update timestamp to propagate change!
                    )
                    dataSource.updateTask(rebalancedTask)
                }
            }
            AppLog.i("Repository", "Rebalanced ${sortedTasks.size} tasks successfully")
            return true
        }
        return false
    }

    private fun syncLocalToRustInternal(tasks: List<Task>) {
        try {
            val jsonArray = JSONArray()
            tasks.forEach { jsonArray.put(org.json.JSONObject(TaskSerializer.serialize(it))) }
            taskDocument.restoreFromJson(jsonArray.toString())
            AppLog.d("Repository", "Rust engine refreshed with ${tasks.size} tasks")
        } catch (e: Exception) {
            AppLog.e("Repository", "syncLocalToRustInternal failed: ${e.message}")
        }
    }

    private data class SyncStats(val added: Int = 0, val updated: Int = 0, val deleted: Int = 0)

    private fun syncRustToLocalInternal(): SyncStats {
        var added = 0
        var updated = 0
        var deleted = 0

        return try {
            val jsonString = taskDocument.getAllTasksJson()
            val jsonArray = JSONArray(jsonString)
            val currentLocalTasks = dataSource.getAllTasks().associateBy { it.uuid }
            val processedUuids = mutableSetOf<String>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val task = TaskSerializer.deserialize(obj.toString())

                if (task.uuid in processedUuids) continue
                processedUuids.add(task.uuid)

                val localTask = currentLocalTasks[task.uuid]
                val taskWithId = task.copy(id = localTask?.id ?: 0)

                if (taskWithId.id == 0L) {
                    dataSource.addTask(taskWithId)
                    added++
                } else if (hasTaskChanged(localTask!!, taskWithId)) {
                    dataSource.updateTask(taskWithId)
                    updated++
                }
            }

            currentLocalTasks.keys.filter { it !in processedUuids }.forEach { uuid ->
                currentLocalTasks[uuid]?.let {
                    dataSource.deleteTask(it)
                    deleted++
                }
            }
            SyncStats(added, updated, deleted)
        } catch (e: Exception) {
            AppLog.e("Repository", "syncRustToLocalInternal failed: ${e.message}")
            SyncStats()
        }
    }

    private fun hasTaskChanged(local: Task, remote: Task): Boolean {
        return local.content != remote.content ||
                local.isPinned != remote.isPinned ||
                local.tags != remote.tags ||
                local.customSortOrder != remote.customSortOrder ||
                local.updatedAt.time != remote.updatedAt.time ||
                local.createdAt.time != remote.createdAt.time
    }
}
