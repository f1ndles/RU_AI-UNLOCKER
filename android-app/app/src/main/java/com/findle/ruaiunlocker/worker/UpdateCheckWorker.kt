package com.findle.ruaiunlocker.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.findle.ruaiunlocker.MainActivity
import com.findle.ruaiunlocker.R
import com.findle.ruaiunlocker.data.repository.SettingsRepository
import com.findle.ruaiunlocker.domain.BackupManager
import com.findle.ruaiunlocker.domain.UpdateChecker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val updateChecker: UpdateChecker,
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "hosts_updates"
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        val updateResult = updateChecker.checkForUpdate().getOrNull() ?: return Result.retry()

        if (!updateResult.hasUpdate) return Result.success()

        val autoUpdate = settingsRepository.autoUpdateFlow.first()

        if (autoUpdate) {
            backupManager.createBackup()
            val newDate = updateChecker.performUpdate().getOrNull()
            if (newDate != null) {
                showNotification(
                    context.getString(R.string.notification_updated_title),
                    context.getString(R.string.notification_updated_text, newDate)
                )
            }
        } else {
            showNotification(
                context.getString(R.string.notification_update_title),
                context.getString(R.string.notification_update_text, updateResult.remoteDate)
            )
        }

        return Result.success()
    }

    private fun showNotification(title: String, text: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
