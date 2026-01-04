package com.example.tasks.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tasks.data.Task
import com.example.tasks.ui.viewmodel.SettingsViewModel
import com.example.tasks.ui.viewmodel.TaskViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(taskViewModel: TaskViewModel, settingsViewModel: SettingsViewModel) {
    val homeScreenTag by settingsViewModel.homeScreenTag.collectAsState()
    val tasks by taskViewModel.tasks.collectAsState()

    // 1. Calculate base tasks (matching home tag)
    val baseTasks = remember(tasks, homeScreenTag) {
        tasks.filter { it.tags.contains(homeScreenTag) }
    }

    // 2. Extract available extra tags from these tasks
    val extraTags = remember(baseTasks, homeScreenTag) {
        baseTasks.flatMap { it.tags }
            .filter { it != homeScreenTag }
            .distinct()
            .sorted()
    }

    // 3. State for selected extra tags (Set for efficiency)
    val selectedTags = remember { mutableStateListOf<String>() }

    // Clear selected tags if they are no longer available
    LaunchedEffect(extraTags) {
        selectedTags.retainAll(extraTags)
    }

    // 4. Calculate effective tasks based on filter (AND logic)
    val effectiveTasks = remember(baseTasks, selectedTags.toList()) {
        if (selectedTags.isEmpty()) baseTasks
        else baseTasks.filter { task -> selectedTags.all { it in task.tags } }
    }

    var randomTask by remember { mutableStateOf<Task?>(null) }

    fun pickRandomTask() {
        randomTask = if (effectiveTasks.isEmpty()) {
            null
        } else if (effectiveTasks.size == 1) {
            effectiveTasks.first()
        } else {
            var candidate = effectiveTasks.random()
            // Try to pick a new one different from current
            var attempts = 0
            while (candidate.id == randomTask?.id && attempts < 5) {
                candidate = effectiveTasks.random()
                attempts++
            }
            candidate
        }
    }

    // Auto-pick when pool changes
    LaunchedEffect(effectiveTasks) {
        pickRandomTask()
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (randomTask != null) {
                Text(
                    text = randomTask!!.content,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(onClick = { pickRandomTask() }) {
                    Text("Next Task")
                }
            } else {
                Text(
                    text = if (baseTasks.isEmpty()) "恭喜你，已经解决了全部事情！" else "没有符合条件的任务～",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Dynamic Tag Buttons
            if (extraTags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    extraTags.forEach { tag ->
                        FilterChip(
                            selected = tag in selectedTags,
                            onClick = {
                                if (tag in selectedTags) {
                                    selectedTags.remove(tag)
                                } else {
                                    selectedTags.add(tag)
                                }
                            },
                            label = { Text(tag) },
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}