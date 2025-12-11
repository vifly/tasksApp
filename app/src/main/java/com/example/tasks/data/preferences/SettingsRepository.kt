package com.example.tasks.data.preferences

import android.content.Context
import androidx.core.content.edit

class SettingsRepository(context: Context) {

    private val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun getHomeScreenTag(): String {
        return sharedPreferences.getString("home_screen_tag", "short") ?: "short"
    }

    fun setHomeScreenTag(tag: String) {
        sharedPreferences.edit {
            putString("home_screen_tag", tag)
        }
    }
}