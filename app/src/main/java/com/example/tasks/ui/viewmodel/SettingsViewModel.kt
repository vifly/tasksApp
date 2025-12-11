package com.example.tasks.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tasks.data.preferences.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val _homeScreenTag = MutableStateFlow("")
    val homeScreenTag: StateFlow<String> = _homeScreenTag

    init {
        _homeScreenTag.value = repository.getHomeScreenTag()
    }

    fun updateHomeScreenTag(newTag: String) {
        repository.setHomeScreenTag(newTag)
        _homeScreenTag.value = newTag
    }
}

class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(SettingsRepository(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
