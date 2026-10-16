package com.neko7ina.wallet.assistant.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.neko7ina.wallet.assistant.MainActivity
import com.neko7ina.wallet.assistant.R
import com.neko7ina.wallet.assistant.archive.TripAutoArchiveScheduler
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.TravelDocumentStatus
import com.neko7ina.wallet.assistant.core.model.railRoute
import com.neko7ina.wallet.assistant.core.model.stableId
import com.neko7ina.wallet.assistant.data.TravelDocumentRepository
import com.neko7ina.wallet.assistant.data.TravelWalletDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

class TripReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REMINDER -> handleReminder(context, intent)
            ACTION_BOARDED -> handleNotificationAction(context, intent, archive = true)
            ACTION_CANCEL_REMINDER -> handleNotificationAction(context, intent, archive = false)
            ACTION_STANDARD_READ -> handleStandardRead(context, intent)
            else -> rescheduleEnabledTrips(context)
        }
    }

    /**
     * 出门提醒的「已读」只关这条通知。它的报警是一次性的，触发点过后
     * 调度器不再重架，所以这里不写任何状态，也不碰实时提醒与自动归档。
     */
    private fun handleStandardRead(context: Context, intent: Intent) {
        val documentId = intent.getStringExtra(KEY_DOCUMENT_ID) ?: return
        cancelNotification(context, documentId)
    }

    private fun handleReminder(context: Context, intent: Intent) {
        val documentId = intent.getStringExtra(KEY_DOCUMENT_ID) ?: return
        val kind = intent.getStringExtra(KEY_KIND)?.let(ReminderKind::valueOf) ?: return
        if (kind == ReminderKind.END) {
            cancelNotification(context, documentId)
            return
        }
        if (!canPostNotifications(context)) return

        createNotificationChannel(context)
        val serviceNumber = intent.getStringExtra(KEY_SERVICE_NUMBER) ?: return
        val route = intent.getStringExtra(KEY_ROUTE) ?: return
        val seat = intent.getStringExtra(KEY_SEAT) ?: return
        val departure = intent.getStringExtra(KEY_DEPARTURE) ?: return
        val displayEnd = intent.getLongExtra(KEY_DISPLAY_END, 0L)
        val title = if (kind == ReminderKind.LIVE) {
            "$serviceNumber 即将发车"
        } else {
            "$serviceNumber 乘车提醒"
        }
        val seatDescription = seat.ifBlank { "待确认" }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_china_railway_notification)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setContentTitle(title)
            .setContentText("$departure · $route")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText("$departure\n$route\n$seatDescription"),
            )
            .setContentIntent(contentIntent(context, documentId))
            .apply {
                if (kind == ReminderKind.STANDARD) {
                    addAction(0, "已读", notificationAction(context, documentId, ACTION_STANDARD_READ))
                } else {
                    addAction(0, "已上车", notificationAction(context, documentId, ACTION_BOARDED))
                    addAction(
                        0,
                        "取消提醒",
                        notificationAction(context, documentId, ACTION_CANCEL_REMINDER),
                    )
                }
            }
            .setAutoCancel(kind == ReminderKind.STANDARD)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply {
                if (kind == ReminderKind.LIVE) {
                    // 倒计时基准是固定的出发时刻，重新发布通知只重启画面，不会算错数值。
                    // 状态栏胶囊的走秒平滑度属于平台侧，缺陷号 450909625，本应用不做规避。
                    // 排查记录见 docs/handoff/2026-10-16.md。
                    setOngoing(true)
                    setWhen(displayEnd)
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                    if (Build.VERSION.SDK_INT >= 36) {
                        setRequestPromotedOngoing(true)
                    }
                }
            }
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(documentId), notification)
    }

    private fun handleNotificationAction(context: Context, intent: Intent, archive: Boolean) {
        val documentId = intent.getStringExtra(KEY_DOCUMENT_ID) ?: return
        cancelNotification(context, documentId)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = TravelDocumentRepository(
                    TravelWalletDatabase.getInstance(context).travelDocumentDao(),
                )
                if (archive) {
                    repository.setArchived(documentId, true)
                    TripAutoArchiveScheduler(context).cancel(documentId)
                } else {
                    repository.setReminderEnabled(documentId, false)
                }
                TripReminderScheduler(context).cancel(documentId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun rescheduleEnabledTrips(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = TravelDocumentRepository(
                    TravelWalletDatabase.getInstance(context).travelDocumentDao(),
                )
                val scheduler = TripReminderScheduler(context)
                repository.getReminderEnabledDocuments().forEach(scheduler::schedule)
                TripAutoArchiveScheduler(context).reconcile()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_REMINDER =
            "com.neko7ina.wallet.assistant.action.TRIP_REMINDER"
        private const val ACTION_BOARDED =
            "com.neko7ina.wallet.assistant.action.TRIP_BOARDED"
        private const val ACTION_CANCEL_REMINDER =
            "com.neko7ina.wallet.assistant.action.CANCEL_TRIP_REMINDER"
        private const val ACTION_STANDARD_READ =
            "com.neko7ina.wallet.assistant.action.STANDARD_TRIP_REMINDER_READ"
        private const val CHANNEL_ID = "trip_departure_reminders"
        private const val KEY_DOCUMENT_ID = "document_id"
        private const val KEY_KIND = "kind"
        private const val KEY_SERVICE_NUMBER = "service_number"
        private const val KEY_ROUTE = "route"
        private const val KEY_SEAT = "seat"
        private const val KEY_DEPARTURE = "departure"
        private const val KEY_DISPLAY_END = "display_end"
        private val DEPARTURE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日 HH:mm")

        internal fun pendingIntent(
            context: Context,
            documentId: String,
            suffix: String,
            flags: Int,
            document: TravelDocument? = null,
            kind: ReminderKind? = null,
            displayEnd: Instant? = null,
        ): PendingIntent? {
            val intent = Intent(context, TripReminderReceiver::class.java).apply {
                action = ACTION_REMINDER
                data = Uri.parse("walletassistant://reminder/$documentId/$suffix")
                if (document != null && kind != null && displayEnd != null) {
                    val segment = document.segments.first()
                    val seat = segment.seatAssignments
                        .filter { it.status == TravelDocumentStatus.CONFIRMED }
                        .joinToString("、") { "${it.section} 车 ${it.seat}" }
                    putExtra(KEY_DOCUMENT_ID, documentId)
                    putExtra(KEY_KIND, kind.name)
                    putExtra(KEY_SERVICE_NUMBER, segment.serviceNumber)
                    putExtra(KEY_ROUTE, segment.railRoute)
                    putExtra(KEY_SEAT, seat)
                    putExtra(KEY_DEPARTURE, segment.departureTime.format(DEPARTURE_FORMAT))
                    putExtra(KEY_DISPLAY_END, displayEnd.toEpochMilli())
                }
            }
            return PendingIntent.getBroadcast(
                context,
                "$documentId:$suffix".hashCode(),
                intent,
                flags,
            )
        }

        fun cancelNotification(context: Context, documentId: String) {
            NotificationManagerCompat.from(context).cancel(notificationId(documentId))
        }

        private fun notificationId(documentId: String): Int = documentId.hashCode() and Int.MAX_VALUE

        private fun canPostNotifications(context: Context): Boolean =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "乘车提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "在行程即将开始时显示乘车信息"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun notificationAction(
            context: Context,
            documentId: String,
            action: String,
        ): PendingIntent {
            val intent = Intent(context, TripReminderReceiver::class.java).apply {
                this.action = action
                data = Uri.parse("walletassistant://reminder-action/$documentId/$action")
                putExtra(KEY_DOCUMENT_ID, documentId)
            }
            return PendingIntent.getBroadcast(
                context,
                "$documentId:$action".hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun contentIntent(context: Context, documentId: String): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("document_id", documentId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context,
                notificationId(documentId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
