package com.example.tasks.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tasks.data.preferences.SettingsRepository
import com.example.tasks.data.services.WebDavClient
import com.example.tasks.data.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _homeScreenTag = MutableStateFlow(repository.homeScreenTag)
    val homeScreenTag: StateFlow<String> = _homeScreenTag

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    val serverUrl = MutableStateFlow(repository.serverUrl)
    val username = MutableStateFlow(repository.username)
    val password = MutableStateFlow(repository.password)
    val autoSyncEnabled = MutableStateFlow(repository.autoSyncEnabled)
    val syncIntervalMinutes = MutableStateFlow(repository.syncIntervalMinutes)

    fun updateHomeScreenTag(newTag: String) {
        repository.homeScreenTag = newTag
        _homeScreenTag.value = newTag
    }

    fun saveSettings() {
        repository.serverUrl = serverUrl.value
        repository.username = username.value
        repository.password = password.value
        repository.autoSyncEnabled = autoSyncEnabled.value
        repository.syncIntervalMinutes = syncIntervalMinutes.value

        syncScheduler.updateSchedule()

        viewModelScope.launch { _toastMessage.emit("设置已保存") }
    }

    fun testConnection() {
        val url = serverUrl.value
        val user = username.value
        val pass = password.value

        if (url.isBlank()) {
            viewModelScope.launch { _toastMessage.emit("请输入服务器 URL") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = WebDavClient(url, user, pass)
                if (client.checkConnection()) {
                    _toastMessage.emit("连接成功！")
                } else {
                    _toastMessage.emit("目标未找到，尝试创建目录...")
                    if (client.createDirectory()) {
                        _toastMessage.emit("目录已创建，连接成功！")
                    } else {
                        _toastMessage.emit("连接失败：请检查 URL 或认证信息")
                    }
                }
            } catch (e: Exception) {
                _toastMessage.emit("错误: ${e.message}")
            }
        }
    }
}

class SettingsViewModelFactory(
    private val repository: SettingsRepository,
    private val syncScheduler: SyncScheduler
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository, syncScheduler) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}