package com.example.tasks.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.tasks.data.Task
import com.example.tasks.ui.viewmodel.TaskViewModel
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.ReorderableState
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import java.text.SimpleDateFormat
import java.util.Locale


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyItemScope.ReorderableItemCustom(
    reorderableState: ReorderableState<*>,
    key: Any?,
    modifier: Modifier = Modifier,
    index: Int? = null,
    orientationLocked: Boolean = true,
    content: @Composable BoxScope.(isDragging: Boolean) -> Unit,
) = ReorderableItem(
    reorderableState,
    key,
    modifier,
    Modifier.animateItem(),
    orientationLocked,
    index,
    content
)


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TaskListScreen(navController: NavController, taskViewModel: TaskViewModel) {
    LaunchedEffect(Unit) {
        taskViewModel.loadTasks()
    }

    val tasks by taskViewModel.tasks.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<List<Task>?>(null) }
    var showDetailsDialog by remember { mutableStateOf<Task?>(null) }
    val selectedTasks = remember { mutableStateListOf<Task>() }
    val isSelectionMode = selectedTasks.isNotEmpty()

    BackHandler(enabled = isSelectionMode) {
        selectedTasks.clear()
    }

    val filteredTasks = tasks.filter {
        it.content.contains(searchQuery, ignoreCase = true) ||
                it.tags.any { tag -> tag.equals(searchQuery, ignoreCase = true) }
    }

    val reorderableState = rememberReorderableLazyListState(onMove = { from, to ->
        taskViewModel.onMove(from.index, to.index)
    })

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedTasks.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedTasks.clear() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Clear selection"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteDialog = selectedTasks.toList() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected tasks")
                        }
                    }
                )
            } else {
                TopAppBar(title = { Text("Tasks") })
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search or filter by tag") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
            LazyColumn(
                state = reorderableState.listState,
                modifier = Modifier
                    .fillMaxSize()
                    .reorderable(reorderableState)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTasks, key = { it.id }) { task ->
                    ReorderableItemCustom(reorderableState, key = task.id) {
                        val isSelected = selectedTasks.contains(task)
                        TaskListItem(
                            task = task,
                            isSelected = isSelected,
                            dragHandleModifier = Modifier.detectReorderAfterLongPress(
                                reorderableState
                            ),
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) selectedTasks.remove(task) else selectedTasks.add(
                                        task
                                    )
                                } else {
                                    navController.navigate("taskEdit/${task.id}")
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    selectedTasks.add(task)
                                }
                            },
                            onPinClick = { taskViewModel.togglePin(task) },
                            onDetailsClick = { showDetailsDialog = task },
                            onDeleteClick = { showDeleteDialog = listOf(task) }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog != null) {
        DeleteConfirmationDialog(
            tasks = showDeleteDialog!!,
            onConfirm = {
                taskViewModel.deleteTasks(it)
                showDeleteDialog = null
                selectedTasks.clear()
            },
            onDismiss = { showDeleteDialog = null }
        )
    }

    if (showDetailsDialog != null) {
        TaskDetailsDialog(
            task = showDetailsDialog!!,
            onDismiss = { showDetailsDialog = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskListItem(
    task: Task,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPinClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val viewConfiguration = LocalViewConfiguration.current
        val newViewConfiguration = remember(viewConfiguration) {
            object : ViewConfiguration by viewConfiguration {
                override val longPressTimeoutMillis: Long = 200L
            }
        }
        CompositionLocalProvider(LocalViewConfiguration provides newViewConfiguration) {
            Box(
                modifier = dragHandleModifier,
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Drag to reorder",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Clickable content area
        Row(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(vertical = 8.dp), // Removed horizontal padding
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (task.isPinned) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Pinned",
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(
                text = task.content,
                modifier = Modifier.weight(1f)
            )
        }

        // More options menu
        Box(modifier = Modifier.padding(end = 16.dp)) {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (task.isPinned) "Unpin" else "Pin") },
                    onClick = {
                        onPinClick()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Details") },
                    onClick = {
                        onDetailsClick()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        onDeleteClick()
                        showMenu = false
                    }
                )
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    tasks: List<Task>,
    onConfirm: (List<Task>) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Task(s)") },
        text = { Text("Are you sure you want to delete ${tasks.size} task(s)?") },
        confirmButton = {
            Button(onClick = { onConfirm(tasks) }) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun TaskDetailsDialog(task: Task, onDismiss: () -> Unit) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val wordCount = task.content.split("\\s+".toRegex()).count { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Task Details") },
        text = {
            Column {
                Text(text = "Content: ${task.content}")
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Created: ${dateFormat.format(task.createdAt)}")
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Last Updated: ${dateFormat.format(task.updatedAt)}")
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Word Count: $wordCount")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
