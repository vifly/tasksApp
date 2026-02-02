package com.example.tasks.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tasks.TasksApplication
import com.example.tasks.data.network.WebDavClient
import com.example.tasks.data.preferences.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val _homeScreenTag = MutableStateFlow(repository.homeScreenTag)
    val homeScreenTag: StateFlow<String> = _homeScreenTag

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    // Temporary state for UI, initialized from Repository
    val serverUrl = MutableStateFlow(repository.serverUrl)
    val username = MutableStateFlow(repository.username)
    val password = MutableStateFlow(repository.password)

    fun updateHomeScreenTag(newTag: String) {
        repository.homeScreenTag = newTag
        _homeScreenTag.value = newTag
    }

    fun saveSettings() {
        repository.serverUrl = serverUrl.value
        repository.username = username.value
        repository.password = password.value
        viewModelScope.launch { _toastMessage.emit("Settings saved") }
    }

    fun testConnection() {
        val url = serverUrl.value
        val user = username.value
        val pass = password.value

        if (url.isBlank()) {
            viewModelScope.launch { _toastMessage.emit("Please enter Server URL") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = WebDavClient(url, user, pass)
                if (client.checkConnection()) {
                    _toastMessage.emit("Connection Successful!")
                } else {
                    // Try to create directory if it doesn't exist
                    _toastMessage.emit("Target not found, attempting to create...")
                    if (client.createDirectory()) {
                        _toastMessage.emit("Directory created & Connection Successful!")
                    } else {
                        _toastMessage.emit("Connection Failed: Verify URL/Auth")
                    }
                }
            } catch (e: Exception) {
                _toastMessage.emit("Error: ${e.message}")
            }
        }
    }
}

class SettingsViewModelFactory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
