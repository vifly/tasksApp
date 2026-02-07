package com.example.tasks.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.tasks.data.preferences.SettingsRepository
import com.example.tasks.workers.SyncWorker
import java.util.concurrent.TimeUnit

/**
 * SyncScheduler manages the scheduling of sync tasks using WorkManager.
 */
class SyncScheduler(
    context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Triggers a manual sync immediately.
     */
    fun triggerNow(source: String = "ManualUI") {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = workDataOf("sync_source" to source)

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .addTag("ManualSync")
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        workManager.enqueueUniqueWork(
            "ManualSync",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Schedules or cancels the periodic background sync based on current settings.
     */
    fun updateSchedule() {
        if (settingsRepository.autoSyncEnabled) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = workDataOf("sync_source" to "PeriodicAuto")

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                settingsRepository.syncIntervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "AutoSync",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            workManager.cancelUniqueWork("AutoSync")
        }
    }
}