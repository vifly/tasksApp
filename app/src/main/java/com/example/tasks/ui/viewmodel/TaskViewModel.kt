package com.example.tasks.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tasks.data.Task
import com.example.tasks.data.repositories.TaskRepository
import com.example.tasks.data.services.TaskImportService
import com.example.tasks.data.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID

class TaskViewModel(
    private val repository: TaskRepository,
    private val syncScheduler: SyncScheduler,
    private val importService: TaskImportService
) : ViewModel() {

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks

    private val _allTags = MutableStateFlow<List<String>>(emptyList())
    val allTags: StateFlow<List<String>> = _allTags

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private var isInteracting = false

    init {
        // Load cached data immediately so UI is not empty
        loadTasks()

        // Initialize Rust engine in background
        viewModelScope.launch {
            repository.initialize()
            // If initialization changed data (e.g. cleaned duplicates), signal handles refresh
        }

        repository.onDataChanged
            .onEach { if (!isInteracting) loadTasks() }
            .launchIn(viewModelScope)
    }

    fun loadTasks() {
        viewModelScope.launch {
            val list = repository.getAllTasks()
            _tasks.value = list.distinctBy { it.uuid }.sortedTasks()
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
        return this.sortedWith(compareByDescending<Task> { it.isPinned }.thenByDescending { it.customSortOrder }
            .thenByDescending { it.createdAt })
    }

    fun addTask(task: Task) {
        viewModelScope.launch { repository.addTask(task) }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch { repository.updateTask(task) }
    }

    fun deleteTasks(tasks: List<Task>) {
        viewModelScope.launch { repository.deleteTasks(tasks) }
    }

    fun togglePin(task: Task) {
        viewModelScope.launch {
            val isPinningNewTask = !task.isPinned
            if (isPinningNewTask) {
                tasks.value.find { it.isPinned && it.uuid != task.uuid }?.let {
                    repository.updateTask(it.copy(isPinned = false))
                }
            }
            repository.updateTask(task.copy(isPinned = !task.isPinned))
        }
    }

    fun onMove(from: Int, to: Int) {
        isInteracting = true
        val updatedTasks = _tasks.value.toMutableList()
        if (from in updatedTasks.indices && to in updatedTasks.indices) {
            if (updatedTasks[from].isPinned || updatedTasks[to].isPinned) return
            updatedTasks.add(to, updatedTasks.removeAt(from))
            _tasks.value = updatedTasks
        }
    }

    fun onDragEnd() {
        viewModelScope.launch {
            val updatedTasks = _tasks.value
            val total = updatedTasks.size
            // Update sort order for all tasks to match current list order
            updatedTasks.forEachIndexed { index, task ->
                val newSortOrder = total - index
                if (task.customSortOrder != newSortOrder) {
                    repository.updateTask(task.copy(customSortOrder = newSortOrder))
                }
            }
            isInteracting = false
            loadTasks()
        }
    }

    fun importTasks(jsonString: String) {
        viewModelScope.launch {
            val count = importService.importFromJson(jsonString)
            if (count > 0) {
                _toastMessage.emit("成功导入 $count 个任务")
            } else {
                _toastMessage.emit("未导入任何任务或导入失败")
            }
        }
    }

    fun addTestData() {
        viewModelScope.launch {
            for (i in 1..5) {
                repository.addTask(
                    Task(
                        content = "Test task ${UUID.randomUUID()}",
                        tags = listOf("test")
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
    private val importService: TaskImportService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(repository, syncScheduler, importService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
