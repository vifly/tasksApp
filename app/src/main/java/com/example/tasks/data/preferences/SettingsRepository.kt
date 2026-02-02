package com.example.tasks.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("server_url", "") ?: ""
        set(value) = prefs.edit { putString("server_url", value) }

    var username: String
        get() = prefs.getString("username", "") ?: ""
        set(value) = prefs.edit { putString("username", value) }

    var password: String
        get() = prefs.getString("password", "") ?: ""
        set(value) = prefs.edit { putString("password", value) }

    var homeScreenTag: String
        get() = prefs.getString("home_screen_tag", "Inbox") ?: "Inbox"
        set(value) = prefs.edit { putString("home_screen_tag", value) }
    
    var lastSyncTime: Long
        get() = prefs.getLong("last_sync_time", 0L)
        set(value) = prefs.edit { putLong("last_sync_time", value) }
}
