package com.neko7ina.wallet.assistant.email

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neko7ina.wallet.assistant.MainActivity
import com.neko7ina.wallet.assistant.R
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.TravelDocumentStatus
import com.neko7ina.wallet.assistant.core.model.railRoute
import com.neko7ina.wallet.assistant.settings.AppPreferences
import com.neko7ina.wallet.assistant.settings.AutomaticEmailSyncStatus
import java.util.concurrent.TimeUnit

class AutomaticEmailSyncScheduler(private val context: Context) {
    fun reconcile() {
        val preferences = AppPreferences(context)
        if (!preferences.automaticEmailSyncEnabled) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutomaticEmailSyncWorker>(
            preferences.automaticEmailSyncInterval.hours,
            TimeUnit.HOURS,
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    companion object {
        private const val WORK_NAME = "automatic_email_sync"
    }
}

class AutomaticEmailSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val preferences = AppPreferences(applicationContext)
        if (!preferences.automaticEmailSyncEnabled) return Result.success()
        return try {
            when (
                val outcome = EmailSyncCoordinator(applicationContext).sync(
                    requireExistingCheckpoint = true,
                )
            ) {
                EmailSyncOutcome.NoAccount,
                EmailSyncOutcome.InitialSyncRequired,
                -> {
                    updateStatus(preferences, AutomaticEmailSyncStatus.INITIAL_SYNC_REQUIRED)
                    preferences.automaticEmailSyncEnabled = false
                    AutomaticEmailSyncScheduler(applicationContext).reconcile()
                    Result.success()
                }

                EmailSyncOutcome.NoNewRailwayMessages,
                EmailSyncOutcome.NoRecognizableTrips,
                -> {
                    updateStatus(preferences, AutomaticEmailSyncStatus.SUCCESS)
                    Result.success()
                }

                // 行程已经落库了，通知只是事后告知，点开就能在行程页看到。
                is EmailSyncOutcome.Imported -> {
                    updateStatus(preferences, AutomaticEmailSyncStatus.SUCCESS)
                    EmailSyncNotification.showImportedTrips(
                        context = applicationContext,
                        documents = outcome.documents,
                    )
                    Result.success()
                }
            }
        } catch (_: ImapAuthenticationException) {
            updateStatus(preferences, AutomaticEmailSyncStatus.FAILED)
            preferences.automaticEmailSyncEnabled = false
            AutomaticEmailSyncScheduler(applicationContext).reconcile()
            Result.failure()
        } catch (_: Exception) {
            updateStatus(preferences, AutomaticEmailSyncStatus.FAILED)
            Result.retry()
        }
    }

    private fun updateStatus(
        preferences: AppPreferences,
        status: AutomaticEmailSyncStatus,
    ) {
        preferences.automaticEmailSyncStatus = status
        preferences.automaticEmailSyncStatusAtEpochMillis = System.currentTimeMillis()
    }
}

object EmailSyncNotification {
    const val EXTRA_OPEN_UPCOMING_TRIPS = "open_upcoming_trips"
    private const val CHANNEL_ID = "email_sync_results"
    private const val NOTIFICATION_ID = 12306

    fun showImportedTrips(context: Context, documents: List<TravelDocument>) {
        if (documents.isEmpty()) return
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_UPCOMING_TRIPS, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_china_railway_notification)
            .setContentTitle(notificationTitle(documents))
            .setContentText(notificationSummary(documents))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** 只有整批都是新购票才说「添加」，改签和退票会让已有行程变状态，说「更新」。 */
    private fun notificationTitle(documents: List<TravelDocument>): String {
        val allNewlyConfirmed = documents.all {
            it.status == TravelDocumentStatus.CONFIRMED
        }
        return if (allNewlyConfirmed) {
            "已添加 ${documents.size} 条行程"
        } else {
            "已更新 ${documents.size} 条行程"
        }
    }

    private fun notificationSummary(documents: List<TravelDocument>): String {
        val firstRoute = documents.first().segments.firstOrNull()?.railRoute
            ?: return "打开「出行」查看"
        return if (documents.size == 1) firstRoute else "$firstRoute 等"
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "邮箱同步",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "自动同步发现新行程或行程变化时通知"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
