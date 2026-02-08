package com.example.tasks.workers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.tasks.R
import com.example.tasks.TasksApplication

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val channelId = "sync_channel"
    private val notificationId = 1001

    override suspend fun doWork(): Result {
        val application = applicationContext as TasksApplication
        val orchestrator = application.syncOrchestrator

        val source = inputData.getString("sync_source") ?: "Unknown"

        application.logRepository.log(
            Log.DEBUG,
            "SyncWorker",
            "Starting sync work from source: $source"
        )

        return try {
            val result = orchestrator.performSync(source)
            if (result.isSuccess) {
                Result.success()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "未知错误"
                showFailureNotification(errorMsg)
                Result.failure()
            }
        } catch (e: Exception) {
            showFailureNotification(e.message ?: "发生异常")
            Result.failure()
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "WebDAV 同步",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setAutoCancel(true)
            .build()
    }

    private fun showFailureNotification(error: String) {
        val notification = createNotification("同步失败", "点击查看详情: $error")
        notificationManager.notify(notificationId, notification)
    }
}
