package com.example.tasks.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tasks.data.repositories.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DebugLogViewModel(
    private val logRepository: LogRepository
) : ViewModel() {

    private val _logs = MutableStateFlow("")
    val logs: StateFlow<String> = _logs.asStateFlow()

    init {
        refreshLogs()
    }

    fun refreshLogs() {
        viewModelScope.launch {
            val logLines = logRepository.getPersistentLogs()

            // Reverse so newest is at the top
            val reversedLogs = logLines
                .filter { it.isNotBlank() }
                .reversed()
                .joinToString(System.lineSeparator())

            _logs.value = reversedLogs
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            logRepository.clearLogs()
            refreshLogs()
        }
    }
}

class DebugLogViewModelFactory(
    private val logRepository: LogRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DebugLogViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DebugLogViewModel(logRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
