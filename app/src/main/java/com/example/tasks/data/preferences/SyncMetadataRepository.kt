package com.example.tasks.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID

/**
 * SyncMetadataRepository handles internal synchronization state that is NOT user-configurable.
 */
class SyncMetadataRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sync_metadata", Context.MODE_PRIVATE)

    var deviceUuid: String
        get() {
            var uuid = prefs.getString("device_uuid", null)
            if (uuid == null) {
                uuid = UUID.randomUUID().toString()
                prefs.edit { putString("device_uuid", uuid) }
            }
            return uuid
        }
        private set(value) { /* Read only */ }

    var lastSyncTime: Long
        get() = prefs.getLong("last_sync_time", 0L)
        set(value) = prefs.edit { putLong("last_sync_time", value) }

    var processedFiles: Set<String>
        get() = prefs.getStringSet("processed_files", emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet("processed_files", value) }
}
