package com.example.tasks.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tasks.data.Task
import com.example.tasks.data.repositories.TaskRepository
import com.example.tasks.data.services.TaskImportExportService
import com.example.tasks.data.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * TaskViewModel manages the UI state for the task list.
 * Uses a "Floating Weight" (customSortOrder) algorithm for efficient O(1) reordering.
 */
class TaskViewModel(
    private val repository: TaskRepository,
    private val syncScheduler: SyncScheduler,
    private val importExportService: TaskImportExportService
) : ViewModel() {

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks

    private val _allTags = MutableStateFlow<List<String>>(emptyList())
    val allTags: StateFlow<List<String>> = _allTags

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    val isSyncing: StateFlow<Boolean> = syncScheduler.isSyncing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var isInteracting = false
    private var lastMovedTaskUuid: String? = null

    init {
        // Load cached data immediately so UI is not empty
        loadTasks()

        // Initialize Rust engine in background
        viewModelScope.launch {
            repository.initialize()
            // If initialization changed data (e.g. cleaned duplicates), signal handles refresh
        }

        // React to repository changes (including background syncs!)
        repository.onDataChanged
            .onEach { if (!isInteracting) loadTasks() }
            .launchIn(viewModelScope)
    }

    fun loadTasks() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.getAllTasks()
            _tasks.value = list.sortedTasks()
            _allTags.value = list.flatMap { it.tags }.distinct().sorted()
        }
    }

    fun sync() {
        syncScheduler.triggerNow()
        viewModelScope.launch {
            _toastMessage.emit("后台同步已请求")
        }
    }

    private fun List<Task>.sortedTasks(): List<Task> {
        return this.sortedWith(
            compareByDescending<Task> { it.isPinned }
                .thenByDescending { it.customSortOrder }
                .thenByDescending { it.createdAt }
        )
    }

    fun addTask(task: Task) {
        viewModelScope.launch {
            // New tasks default to highest weight so they appear at top
            val newTask = task.copy(customSortOrder = System.currentTimeMillis())
            repository.addTask(newTask)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch { repository.updateTask(task) }
    }

    fun deleteTasks(tasks: List<Task>) {
        viewModelScope.launch { repository.deleteTasks(tasks) }
    }

    fun togglePin(task: Task) {
        viewModelScope.launch {
            val becomingPinned = !task.isPinned

            if (becomingPinned) {
                // Pinning: Unpin existing (if any) and set high weight
                tasks.value.find { it.isPinned && it.uuid != task.uuid }?.let {
                    repository.updateTask(
                        it.copy(
                            isPinned = false,
                            customSortOrder = System.currentTimeMillis()
                        )
                    )
                }
                repository.updateTask(
                    task.copy(
                        isPinned = true,
                        customSortOrder = System.currentTimeMillis()
                    )
                )
            } else {
                // Unpinning: Set current time as weight so it stays at the top of unpinned list
                repository.updateTask(
                    task.copy(
                        isPinned = false,
                        customSortOrder = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun onMove(from: Int, to: Int) {
        isInteracting = true
        val list = _tasks.value.toMutableList()
        if (from in list.indices && to in list.indices) {
            // Don't allow moving pinned tasks OR moving items into/out of pinned zone
            if (list[from].isPinned || list[to].isPinned) return

            val movedItem = list.removeAt(from)
            list.add(to, movedItem)
            lastMovedTaskUuid = movedItem.uuid
            _tasks.value = list
        }
    }

    fun onDragEnd() {
        val uuid = lastMovedTaskUuid ?: return
        val currentList = _tasks.value
        val index = currentList.indexOfFirst { it.uuid == uuid }

        if (index != -1) {
            val movedTask = currentList[index]
            val newOrder = calculateFloatingOrder(index, currentList)

            viewModelScope.launch {
                repository.updateTask(movedTask.copy(customSortOrder = newOrder))
                lastMovedTaskUuid = null
                isInteracting = false
                // Force refresh to get the updated timestamp from DB, 
                // because the signal during updateTask was ignored due to isInteracting=true
                loadTasks()
            }
        } else {
            isInteracting = false
        }
    }

    private fun calculateFloatingOrder(index: Int, list: List<Task>): Long {
        val step = 1000000L

        val prev = if (index > 0 && !list[index - 1].isPinned) list[index - 1] else null
        val next = if (index < list.size - 1) list[index + 1] else null

        return when {
            prev != null && next != null -> (prev.customSortOrder + next.customSortOrder) / 2
            prev == null && next != null -> next.customSortOrder + step
            prev != null && next == null -> prev.customSortOrder - step
            else -> System.currentTimeMillis()
        }
    }

    fun importTasks(jsonString: String) {
        viewModelScope.launch {
            val count = importExportService.importFromJson(jsonString)
            if (count > 0) _toastMessage.emit("成功导入 $count 条任务")
        }
    }

    suspend fun getExportData(): String {
        return importExportService.exportToJson()
    }

    fun addTestData() {
        viewModelScope.launch {
            val baseOrder = System.currentTimeMillis()
            val step = 1_000_000L
            for (i in 1..5) {
                repository.addTask(
                    Task(
                        content = "Test task ${UUID.randomUUID()}",
                        tags = listOf("test"),
                        customSortOrder = baseOrder + (i * step)
                    )
                )
            }
        }
    }

    fun deleteTestData() {
        viewModelScope.launch {
            val toDelete = tasks.value.filter { it.tags.contains("test") }
            repository.deleteTasks(toDelete)
        }
    }
}

class TaskViewModelFactory(
    private val repository: TaskRepository,
    private val syncScheduler: SyncScheduler,
    private val importExportService: TaskImportExportService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(repository, syncScheduler, importExportService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
