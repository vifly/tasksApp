package com.example.tasks.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tasks.data.Task
import com.example.tasks.data.db.TaskDataSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import java.util.Date
import java.util.UUID

class TaskViewModel(private val dataSource: TaskDataSource) : ViewModel() {

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks

    private val _allTags = MutableStateFlow<List<String>>(emptyList())
    val allTags: StateFlow<List<String>> = _allTags

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    init {
        loadTasks()
    }

    fun loadTasks() {
        viewModelScope.launch {
            var currentTasks = dataSource.getAllTasks()
            if (currentTasks.isEmpty()) {
                addInitialData()
                currentTasks = dataSource.getAllTasks()
            }
            _tasks.value = currentTasks.sortedTasks()
            _allTags.value = currentTasks.flatMap { it.tags }.distinct().sorted()
        }
    }

    private fun addInitialData() {
        val initialTasks = listOf(
            Task(
                id = 1,
                content = "Read a chapter of a book",
                tags = listOf("reading", "deep work"),
                createdAt = Date(System.currentTimeMillis() - 100000)
            ),
            Task(
                id = 2,
                content = "Write a short story",
                tags = listOf("writing", "creative"),
                createdAt = Date(System.currentTimeMillis() - 200000),
                isPinned = true
            ),
            Task(
                id = 3,
                content = "Go for a 10-minute walk",
                tags = listOf("health", "short"),
                createdAt = Date(System.currentTimeMillis() - 300000)
            ),
            Task(
                id = 4,
                content = "Meditate for 5 minutes",
                tags = listOf("health", "short"),
                createdAt = Date(System.currentTimeMillis() - 400000)
            ),
            Task(
                id = 5,
                content = "Plan tomorrow's tasks",
                tags = listOf("planning", "short"),
                createdAt = Date(System.currentTimeMillis() - 500000)
            ),
        ).mapIndexed { index, task -> task.copy(customSortOrder = 5 - index) }
        initialTasks.forEach { dataSource.addTask(it) }
    }

    private fun List<Task>.sortedTasks(): List<Task> {
        return this.sortedWith(compareByDescending<Task> { it.isPinned }.thenByDescending { it.customSortOrder }
            .thenByDescending { it.createdAt })
    }

    fun addTask(task: Task) {
        viewModelScope.launch {
            val maxOrder = _tasks.value.maxOfOrNull { it.customSortOrder } ?: 0
            val newTask = task.copy(customSortOrder = maxOrder + 1)
            dataSource.addTask(newTask)
            loadTasks()
        }
    }

    fun importTasks(jsonString: String) {
        viewModelScope.launch {
            try {
                val jsonArray = JSONArray(jsonString)
                val maxOrder = _tasks.value.maxOfOrNull { it.customSortOrder } ?: 0
                val newTasks = mutableListOf<Task>()
                var skippedCount = 0
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)

                    if (!jsonObject.has("content") || !jsonObject.has("tags")) {
                        skippedCount++
                        continue
                    }

                    val content = jsonObject.getString("content")
                    val tagsJsonArray = jsonObject.getJSONArray("tags")
                    val tags = (0 until tagsJsonArray.length()).map { tagsJsonArray.getString(it) }
                    val createdAt =
                        if (jsonObject.has("createdAt")) Date(jsonObject.getLong("createdAt")) else Date()
                    val updatedAt =
                        if (jsonObject.has("updatedAt")) Date(jsonObject.getLong("updatedAt")) else Date()

                    val task = Task(
                        content = content,
                        tags = tags,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        customSortOrder = maxOrder + newTasks.size + 1
                    )
                    newTasks.add(task)
                }

                if (newTasks.isEmpty() && skippedCount > 0) {
                    _toastMessage.emit("导入失败，请检查文件格式")
                    return@launch
                }

                newTasks.forEach { dataSource.addTask(it) }
                loadTasks()

                val importedCount = newTasks.size
                val message = when {
                    skippedCount > 0 -> "成功导入 $importedCount 个任务，跳过 $skippedCount 个格式不正确的任务"
                    importedCount > 0 -> "成功导入 $importedCount 个任务"
                    else -> "未导入任何任务"
                }
                _toastMessage.emit(message)
            } catch (e: JSONException) {
                _toastMessage.emit("导入失败，请检查文件格式")
                e.printStackTrace()
            }
        }
    }

    fun addTestData() {
        viewModelScope.launch {
            val maxOrder = _tasks.value.maxOfOrNull { it.customSortOrder } ?: 0
            for (i in 1..5) {
                val task = Task(
                    content = "Test task ${UUID.randomUUID()}",
                    tags = listOf("test"),
                    customSortOrder = maxOrder + i
                )
                dataSource.addTask(task)
            }
            loadTasks()
        }
    }

    fun deleteTestData() {
        viewModelScope.launch {
            val tasksToDelete = _tasks.value.filter { it.tags == listOf("test") }
            if (tasksToDelete.isNotEmpty()) {
                dataSource.deleteTasks(tasksToDelete)
                loadTasks()
            }
        }
    }

    fun deleteTasks(tasks: List<Task>) {
        viewModelScope.launch {
            dataSource.deleteTasks(tasks)
            loadTasks()
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            dataSource.updateTask(task)
            loadTasks()
        }
    }

    fun onMove(from: Int, to: Int) {
        val updatedTasks = _tasks.value.toMutableList()
        if (from in updatedTasks.indices && to in updatedTasks.indices) {
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
                    dataSource.updateTask(task.copy(customSortOrder = newSortOrder))
                }
            }
        }
    }

    fun togglePin(task: Task) {
        viewModelScope.launch {
            val isPinningNewTask = !task.isPinned
            if (isPinningNewTask) {
                // Unpin any other task that is currently pinned.
                _tasks.value.find { it.isPinned && it.id != task.id }?.let {
                    dataSource.updateTask(it.copy(isPinned = false))
                }
            }

            // Toggle the pin status of the given task.
            val updatedTask = task.copy(isPinned = !task.isPinned)
            dataSource.updateTask(updatedTask)

            loadTasks()
        }
    }
}

class TaskViewModelFactory(private val dataSource: TaskDataSource) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(dataSource) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
