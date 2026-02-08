package com.example.tasks.data.sync

import android.content.Context
import androidx.lifecycle.asFlow
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.tasks.data.preferences.SettingsRepository
import com.example.tasks.workers.SyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
     * Observable flow indicating if any sync work is currently running.
     */
    val isSyncing: Flow<Boolean> = combine(
        workManager.getWorkInfosForUniqueWorkLiveData("ManualSync").asFlow(),
        workManager.getWorkInfosForUniqueWorkLiveData("AutoSync").asFlow()
    ) { manualInfos, autoInfos ->
        val manualRunning =
            manualInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        val autoRunning = autoInfos.any { it.state == WorkInfo.State.RUNNING }
        manualRunning || autoRunning
    }

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
