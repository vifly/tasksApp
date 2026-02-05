package com.example.tasks.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.automirrored.sharp.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.tasks.data.Task
import com.example.tasks.ui.viewmodel.TaskViewModel
import java.util.Date

private data class TaskEditState(val content: String, val tags: List<String>)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun TaskEditScreen(taskId: String?, navController: NavController, taskViewModel: TaskViewModel) {
    val allTags by taskViewModel.allTags.collectAsState()

    var task by remember { mutableStateOf<Task?>(null) }
    var initialTask by remember { mutableStateOf<Task?>(null) }
    var isNewTask by remember { mutableStateOf(false) }

    var content by remember { mutableStateOf("") }
    val tags = remember { mutableStateListOf<String>() }

    val undoStack = remember { mutableStateListOf<TaskEditState>() }
    val redoStack = remember { mutableStateListOf<TaskEditState>() }

    LaunchedEffect(key1 = taskViewModel.tasks, key2 = taskId) {
        if (taskId == "new" || taskId == null) {
            isNewTask = true
            val newTask = Task(
                id = System.currentTimeMillis(),
                content = "",
                tags = emptyList(),
                createdAt = Date(),
                updatedAt = Date()
            )
            task = newTask
            initialTask = newTask
            content = newTask.content
            tags.clear()
            tags.addAll(newTask.tags)
        } else {
            isNewTask = false
            val existingTask = taskViewModel.tasks.value.find { it.id.toString() == taskId }
            task = existingTask
            initialTask = existingTask
            if (existingTask != null) {
                content = existingTask.content
                tags.clear()
                tags.addAll(existingTask.tags)
            }
        }
        undoStack.clear()
        redoStack.clear()
        undoStack.add(TaskEditState(content, tags.toList()))
    }

    val currentTask = task
    if (currentTask != null) {
        var newTag by remember { mutableStateOf("") }
        var showConfirmDialog by remember { mutableStateOf(false) }

        val hasUnsavedChanges =
            content != initialTask?.content || tags.toList() != initialTask?.tags

        fun saveChanges() {
            val taskToSave = currentTask.copy(
                content = content,
                tags = tags.toList(),
                updatedAt = Date()
            )
            if (isNewTask) {
                if (taskToSave.content.isNotBlank() || taskToSave.tags.isNotEmpty()) {
                    taskViewModel.addTask(taskToSave)
                }
            } else {
                if (taskToSave.content.isBlank() && taskToSave.tags.isEmpty()) {
                    taskViewModel.deleteTasks(listOf(taskToSave))
                } else {
                    taskViewModel.updateTask(taskToSave)
                }
            }
        }

        val onBackPress: () -> Unit = {
            if (hasUnsavedChanges) {
                showConfirmDialog = true
            } else {
                navController.popBackStack()
            }
        }

        BackHandler(onBack = onBackPress)

        if (showConfirmDialog) {
            ConfirmExitDialog(
                onSave = {
                    saveChanges()
                    showConfirmDialog = false
                    navController.popBackStack()
                },
                onDiscard = {
                    showConfirmDialog = false
                    navController.popBackStack()
                },
                onCancel = { showConfirmDialog = false }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isNewTask) "New Task" else "Edit Task") },
                    navigationIcon = {
                        IconButton(onClick = onBackPress) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (undoStack.size > 1) {
                                redoStack.add(undoStack.removeAt(undoStack.lastIndex))
                                val prevState = undoStack.last()
                                content = prevState.content
                                tags.clear()
                                tags.addAll(prevState.tags)
                            }
                        }, enabled = undoStack.size > 1) {
                            Icon(Icons.AutoMirrored.Sharp.ArrowBack, contentDescription = "Undo")
                        }
                        IconButton(onClick = {
                            if (redoStack.isNotEmpty()) {
                                val nextState = redoStack.removeAt(redoStack.lastIndex)
                                undoStack.add(nextState)
                                content = nextState.content
                                tags.clear()
                                tags.addAll(nextState.tags)
                            }
                        }, enabled = redoStack.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Sharp.ArrowForward, contentDescription = "Redo")
                        }
                        TextButton(onClick = {
                            saveChanges()
                            navController.popBackStack()
                        }) {
                            Text("Save")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                TextField(
                    value = content,
                    onValueChange = {
                        if (content != it) {
                            content = it
                            undoStack.add(TaskEditState(it, tags.toList()))
                            redoStack.clear()
                        }
                    },
                    label = { Text("Task Content") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Current Tags (click to remove):",
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                FlowRow {
                    tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = {
                                tags.remove(tag)
                                undoStack.add(TaskEditState(content, tags.toList()))
                                redoStack.clear()
                            },
                            label = { Text(tag) },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text("Available Tags (click to add):", modifier = Modifier.padding(bottom = 8.dp))
                FlowRow {
                    allTags.filter { it !in tags }.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = {
                                tags.add(tag)
                                undoStack.add(TaskEditState(content, tags.toList()))
                                redoStack.clear()
                            },
                            label = { Text(tag) },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    TextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        label = { Text("New Tag") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (newTag.isNotBlank() && !tags.contains(newTag)) {
                            tags.add(newTag)
                            newTag = ""
                            undoStack.add(TaskEditState(content, tags.toList()))
                            redoStack.clear()
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Tag")
                    }
                }
            }
        }
    } else if (!isNewTask) {
        Text("Task not found")
    }
}

@Composable
private fun ConfirmExitDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Unsaved changes") },
        text = { Text("Do you want to save your changes?") },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard")
            }
        }
    )
}
