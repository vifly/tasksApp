package com.example.tasks.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.example.tasks.R
import com.example.tasks.data.Task
import com.example.tasks.ui.viewmodel.TaskViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ReorderableLazyListState
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun TaskListScreen(navController: NavController, taskViewModel: TaskViewModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        taskViewModel.toastMessage.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val tasks by taskViewModel.tasks.collectAsState()
    val allTags by taskViewModel.allTags.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<List<Task>?>(null) }
    var showDetailsDialog by remember { mutableStateOf<Task?>(null) }
    val selectedTasks = remember { mutableStateListOf<Task>() }
    val selectedTags = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedTasks.isNotEmpty()

    BackHandler(enabled = isSelectionMode) {
        selectedTasks.clear()
    }

    val filteredTasks = tasks.filter {
        val matchesSearch = it.content.contains(searchQuery, ignoreCase = true)
        val matchesTags =
            if (selectedTags.isEmpty()) true else selectedTags.all { tag -> tag in it.tags }
        matchesSearch && matchesTags
    }

    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            taskViewModel.onMove(from.index, to.index)
        },
        onDragEnd = { _, _ ->
            taskViewModel.onDragEnd()
        }
    )

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(reorderableState.draggingItemKey) {
        if (reorderableState.draggingItemKey != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedTasks.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedTasks.clear() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.arrow_back),
                                contentDescription = "Clear selection"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteDialog = selectedTasks.toList() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.delete),
                                contentDescription = "Delete selected tasks"
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Tasks") },
                    actions = {
                        IconButton(onClick = { taskViewModel.sync() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.sync),
                                contentDescription = "Sync"
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search task content") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )

            // Tag Filters
            if (allTags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    allTags.forEach { tag ->
                        FilterChip(
                            selected = tag in selectedTags,
                            onClick = {
                                if (tag in selectedTags) {
                                    selectedTags.remove(tag)
                                } else {
                                    selectedTags.add(tag)
                                }
                            },
                            label = { Text(tag) }
                        )
                    }
                }
            }

            AutoScrollBox(reorderableState = reorderableState) {
                LazyColumn(
                    state = reorderableState.listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .reorderable(reorderableState)
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredTasks,
                        key = { it.uuid }
                    ) { task ->
                        val isDragging = reorderableState.draggingItemKey == task.uuid
                        val elevation by animateDpAsState(
                            if (isDragging) 8.dp else 0.dp,
                            label = "elevation",
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        )
                        val scale by animateFloatAsState(
                            if (isDragging) 1.05f else 1f,
                            label = "scale",
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        )
                        val isSelected = selectedTasks.contains(task)

                        Box(
                            modifier = Modifier
                                .then(
                                    if (isDragging) {
                                        Modifier
                                    } else {
                                        Modifier.animateItem(
                                            placementSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                    }
                                )
                                .scale(scale)
                                .zIndex(if (isDragging) 1f else 0f)
                        ) {
                            TaskListItem(
                                task = task,
                                isSelected = isSelected,
                                modifier = Modifier
                                    .shadow(elevation)
                                    .background(MaterialTheme.colorScheme.surface),
                                dragHandleModifier = if (searchQuery.isEmpty() && selectedTags.isEmpty() && !task.isPinned) {
                                    Modifier.detectReorder(reorderableState)
                                } else {
                                    Modifier
                                },
                                onClick = {
                                    if (isSelectionMode) {
                                        if (isSelected) selectedTasks.remove(task) else selectedTasks.add(
                                            task
                                        )
                                    } else {
                                        navController.navigate("edit_task/${task.id}")
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

@Composable
fun AutoScrollBox(
    reorderableState: ReorderableLazyListState,
    content: @Composable () -> Unit
) {
    var dragPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull()
                        if (change != null && change.pressed) {
                            dragPosition = change.position
                        } else {
                            dragPosition = Offset.Zero
                        }
                    }
                }
            }
    ) {
        content()

        // Auto-scroll logic
        LaunchedEffect(reorderableState.draggingItemKey) {
            if (reorderableState.draggingItemKey == null) return@LaunchedEffect

            launch {
                while (reorderableState.draggingItemKey != null) {
                    val viewPortHeight = reorderableState.listState.layoutInfo.viewportSize.height
                    if (viewPortHeight > 0 && dragPosition != Offset.Zero) {
                        val topThreshold = viewPortHeight * 0.15f
                        val bottomThreshold = viewPortHeight * 0.85f

                        val scrollSpeed = when {
                            dragPosition.y < topThreshold -> -15f // Slightly slower for stability
                            dragPosition.y > bottomThreshold -> 15f
                            else -> 0f
                        }

                        if (scrollSpeed != 0f) {
                            reorderableState.listState.scrollBy(scrollSpeed)
                        }
                    }
                    delay(16) // ~60fps check
                }
            }
        }
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
        // Shared leading column for perfect alignment
        Box(
            modifier = dragHandleModifier.width(48.dp),
            contentAlignment = Alignment.Center
        ) {
            if (task.isPinned) {
                Icon(
                    painter = painterResource(id = R.drawable.keep),
                    contentDescription = "Pinned",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.drag_handle),
                    contentDescription = "Drag handle",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = task.content,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(modifier = Modifier.padding(end = 16.dp)) {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    painter = painterResource(id = R.drawable.more_vert),
                    contentDescription = "More options"
                )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Task Details") },
        text = {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Tags: ${task.tags.joinToString(", ")}")
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Created: ${dateFormat.format(task.createdAt)}")
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Last Updated: ${dateFormat.format(task.updatedAt)}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
