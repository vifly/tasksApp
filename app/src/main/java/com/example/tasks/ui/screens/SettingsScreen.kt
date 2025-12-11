package com.example.tasks.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tasks.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settingsViewModel: SettingsViewModel) {
    val homeScreenTag by settingsViewModel.homeScreenTag.collectAsState()
    var showTagDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTagDialog = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("主页任务筛选标签", style = MaterialTheme.typography.titleLarge)
                    Text(homeScreenTag, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (showTagDialog) {
        EditTagDialog(
            currentTag = homeScreenTag,
            onDismiss = { showTagDialog = false },
            onConfirm = { newTag ->
                settingsViewModel.updateHomeScreenTag(newTag)
                showTagDialog = false
            }
        )
    }
}

@Composable
private fun EditTagDialog(
    currentTag: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentTag) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改主页任务标签") },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
