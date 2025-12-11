package com.example.tasks.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tasks.data.Task
import com.example.tasks.ui.viewmodel.SettingsViewModel
import com.example.tasks.ui.viewmodel.TaskViewModel

@Composable
fun HomeScreen(taskViewModel: TaskViewModel, settingsViewModel: SettingsViewModel) {
    val homeScreenTag by settingsViewModel.homeScreenTag.collectAsState()
    val tasks by taskViewModel.tasks.collectAsState()
    var randomTask by remember { mutableStateOf<Task?>(null) }

    fun getRandomTask() {
        val filteredTasks = tasks.filter { it.tags.contains(homeScreenTag) }
        randomTask = if (filteredTasks.isNotEmpty()) {
            if (filteredTasks.size == 1) {
                filteredTasks.first()
            } else {
                var newRandomTask = filteredTasks.random()
                while (newRandomTask == randomTask) {
                    newRandomTask = filteredTasks.random()
                }
                newRandomTask
            }
        } else {
            null
        }
    }

    LaunchedEffect(tasks, homeScreenTag) {
        if (tasks.isNotEmpty()) {
            getRandomTask()
        } else {
            randomTask = null
        }
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
                Text(text = randomTask!!.content)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { getRandomTask() }) {
                    Text("Next Task")
                }
            } else {
                Text("恭喜你，已经解决了全部事情！")
            }
        }
    }
}
