package com.example.tasks.ui.screens

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.tasks.ui.viewmodel.SettingsViewModel
import com.example.tasks.ui.viewmodel.TaskViewModel
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settingsViewModel: SettingsViewModel, taskViewModel: TaskViewModel) {
    val homeScreenTag by settingsViewModel.homeScreenTag.collectAsState()
    var showTagDialog by remember { mutableStateOf(false) }
    var showWebDavDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val tasks by taskViewModel.tasks.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val json = JSONArray()
                tasks.forEach { task ->
                    val jsonObject = JSONObject()
                    jsonObject.put("uuid", task.uuid)
                    jsonObject.put("content", task.content)
                    jsonObject.put("tags", JSONArray(task.tags))
                    jsonObject.put("createdAt", task.createdAt.time)
                    jsonObject.put("updatedAt", task.updatedAt.time)
                    json.put(jsonObject)
                }
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toString(4).toByteArray())
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    taskViewModel.importTasks(jsonString)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        taskViewModel.toastMessage.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    LaunchedEffect(Unit) {
        settingsViewModel.toastMessage.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Settings List Items ---
            
            // WebDAV Config Item
            SettingItem(
                title = "WebDAV 同步配置",
                subtitle = "配置服务器地址、用户名及密码",
                onClick = { showWebDavDialog = true }
            )

            HorizontalDivider()

            // Home Tag Item
            SettingItem(
                title = "主页任务筛选标签",
                subtitle = homeScreenTag,
                onClick = { showTagDialog = true }
            )

            HorizontalDivider()

            // Test Data Action
            Button(
                onClick = { taskViewModel.addTestData() },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("一键添加测试数据")
            }

            HorizontalDivider()

            Button(
                onClick = { showDeleteConfirmDialog = true },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("一键删除测试数据")
            }

            HorizontalDivider()

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                        putExtra(Intent.EXTRA_TITLE, "tasks.json")
                    }
                    exportLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("导出 JSON")
            }

            HorizontalDivider()

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                    }
                    importLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("导入 JSON")
            }
        }
    }

    // Dialogs
    if (showWebDavDialog) {
        WebDavConfigDialog(
            viewModel = settingsViewModel,
            onDismiss = { showWebDavDialog = false }
        )
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

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除所有只包含“test”标签的任务吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        taskViewModel.deleteTestData()
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SettingItem(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WebDavConfigDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WebDAV 服务器配置") },
        text = {
            Column {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { viewModel.serverUrl.value = it },
                    label = { Text("服务器 URL") },
                    placeholder = { Text("https://example.com/webdav/") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { viewModel.username.value = it },
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { viewModel.password.value = it },
                    label = { Text("密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.testConnection() },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("测试连接")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.saveSettings()
                    onDismiss()
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
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