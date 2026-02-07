package com.example.tasks.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.tasks.ui.viewmodel.SettingsViewModel
import com.example.tasks.ui.viewmodel.TaskViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    taskViewModel: TaskViewModel,
    navController: NavController
) {
    val homeScreenTag by settingsViewModel.homeScreenTag.collectAsState()
    var showTagDialog by rememberSaveable { mutableStateOf(false) }
    var showWebDavDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        settingsViewModel.toastMessage.collectLatest { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val json = taskViewModel.getExportData()
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(json.toByteArray())
                }
                android.widget.Toast.makeText(
                    context,
                    "Data exported successfully",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val json = context.contentResolver.openInputStream(it)?.bufferedReader()
                    ?.use { it.readText() }
                if (json != null) {
                    taskViewModel.importTasks(json)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedCard(onClick = { showTagDialog = true }) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text("主页任务筛选标签", style = MaterialTheme.typography.titleMedium)
                    Text(homeScreenTag, style = MaterialTheme.typography.bodyMedium)
                }
            }

            OutlinedCard(onClick = { showWebDavDialog = true }) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text("WebDAV 同步配置", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "配置服务器及自动同步频率",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导入 JSON 数据")
                }
                Button(
                    onClick = { exportLauncher.launch("tasks.json") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导出 JSON 数据")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { taskViewModel.addTestData() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("添加测试数据")
                }
                Button(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("删除测试数据")
                }
            }

            Button(
                onClick = { navController.navigate("debug_logs") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("查看调试日志")
            }
        }
    }

    if (showTagDialog) {
        var tempTag by remember { mutableStateOf(homeScreenTag) }
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("修改主页任务筛选标签") },
            text = {
                TextField(
                    value = tempTag,
                    onValueChange = { tempTag = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    settingsViewModel.updateHomeScreenTag(tempTag)
                    showTagDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) { Text("取消") }
            }
        )
    }

    if (showWebDavDialog) {
        var tempUrl by remember { mutableStateOf(settingsViewModel.serverUrl.value) }
        var tempUser by remember { mutableStateOf(settingsViewModel.username.value) }
        var tempPassword by remember { mutableStateOf(settingsViewModel.password.value) }
        var tempAutoSync by remember { mutableStateOf(settingsViewModel.autoSyncEnabled.value) }
        var tempInterval by remember { mutableStateOf(settingsViewModel.syncIntervalMinutes.value.toString()) }

        AlertDialog(
            onDismissRequest = { showWebDavDialog = false },
            title = { Text("WebDAV Configuration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        placeholder = { Text("https://example.com/webdav/") },
                        label = { Text("Server URL") })
                    TextField(
                        value = tempUser,
                        onValueChange = { tempUser = it },
                        label = { Text("Username") })
                    TextField(
                        value = tempPassword,
                        onValueChange = { tempPassword = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = tempAutoSync, onCheckedChange = { tempAutoSync = it })
                        Text("开启自动后台同步")
                    }
                    if (tempAutoSync) {
                        TextField(
                            value = tempInterval,
                            onValueChange = {
                                if (it.all { char -> char.isDigit() }) tempInterval = it
                            },
                            label = { Text("同步间隔（分钟）") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Button(onClick = {
                        settingsViewModel.serverUrl.value = tempUrl
                        settingsViewModel.username.value = tempUser
                        settingsViewModel.password.value = tempPassword
                        settingsViewModel.testConnection()
                    }) {
                        Text("测试连接")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    settingsViewModel.serverUrl.value = tempUrl
                    settingsViewModel.username.value = tempUser
                    settingsViewModel.password.value = tempPassword
                    settingsViewModel.autoSyncEnabled.value = tempAutoSync
                    settingsViewModel.syncIntervalMinutes.value = tempInterval.toLongOrNull() ?: 60L
                    settingsViewModel.saveSettings()
                    showWebDavDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showWebDavDialog = false }) { Text("取消") }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除所有只包含“test”标签的任务吗？") },
            confirmButton = {
                Button(onClick = {
                    taskViewModel.deleteTestData()
                    showDeleteConfirmDialog = false
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("取消") }
            }
        )
    }
}