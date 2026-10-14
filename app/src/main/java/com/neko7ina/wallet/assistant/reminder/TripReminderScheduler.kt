package com.neko7ina.wallet.assistant.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import com.neko7ina.wallet.assistant.BuildConfig
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.TravelDocumentStatus
import com.neko7ina.wallet.assistant.core.model.stableId
import com.neko7ina.wallet.assistant.settings.AppPreferences
import java.time.Duration
import java.time.Instant

internal enum class ReminderKind {
    STANDARD,
    LIVE,
    END,
}

class TripReminderScheduler(context: Context) {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)
    private val appPreferences = AppPreferences(applicationContext)

    fun canScheduleExactReminders(): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    fun schedule(document: TravelDocument) {
        if (document.status != TravelDocumentStatus.CONFIRMED) {
            cancel(document.stableId())
            return
        }
        if (!canScheduleExactReminders()) return
        val departure = document.segments.minOf { it.departureTime.toInstant() }
        if (!departure.isAfter(Instant.now())) {
            cancel(document.stableId())
            return
        }

        val id = document.stableId()
        val standardLead = Duration.ofMinutes(appPreferences.departureReminderMinutes.toLong())
        val liveLead = Duration.ofMinutes(appPreferences.liveStatusMinutes.toLong())
        scheduleAlarm(id, STANDARD_SUFFIX, document, ReminderKind.STANDARD, departure.minus(standardLead), allowLateFire = false)
        scheduleAlarm(id, LIVE_SUFFIX, document, ReminderKind.LIVE, departure.minus(liveLead))
        scheduleAlarm(id, END_SUFFIX, document, ReminderKind.END, departure)
    }

    fun scheduleDebugSequence(document: TravelDocument) {
        check(canScheduleExactReminders()) { "Exact reminder permission is required" }
        val now = Instant.now()
        val id = document.stableId()
        scheduleAlarm(
            id,
            DEBUG_STANDARD_SUFFIX,
            document,
            ReminderKind.STANDARD,
            now.plusSeconds(10),
        )
        scheduleAlarm(
            id,
            DEBUG_LIVE_SUFFIX,
            document,
            ReminderKind.LIVE,
            now.plusSeconds(20),
            displayEnd = now.plusSeconds(50),
        )
        scheduleAlarm(
            id,
            DEBUG_END_SUFFIX,
            document,
            ReminderKind.END,
            now.plusSeconds(50),
        )
    }

    fun cancel(documentId: String) {
        ALL_SUFFIXES.forEach { suffix ->
            val pendingIntent = TripReminderReceiver.pendingIntent(
                context = applicationContext,
                documentId = documentId,
                suffix = suffix,
                flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return@forEach
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        TripReminderReceiver.cancelNotification(applicationContext, documentId)
    }

    /**
     * 出门提醒已经触发过一次后，任何原因的重新调度都不再把它重新架到「现在」，
     * 否则用户读过的提醒会再弹一次。实时倒计时和发车补弹不受影响：
     * 前者还没到触发点时不会被夹到过去，后者本来就该迟到时补弹。
     */
    private fun scheduleAlarm(
        id: String,
        suffix: String,
        document: TravelDocument,
        kind: ReminderKind,
        runAt: Instant,
        displayEnd: Instant = document.segments.minOf { it.departureTime.toInstant() },
        allowLateFire: Boolean = true,
    ) {
        if (!allowLateFire && !runAt.isAfter(Instant.now())) return
        val pendingIntent = TripReminderReceiver.pendingIntent(
            context = applicationContext,
            documentId = id,
            suffix = suffix,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            document = document,
            kind = kind,
            displayEnd = displayEnd,
        ) ?: error("Unable to create reminder PendingIntent")
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            runAt.toEpochMilli().coerceAtLeast(System.currentTimeMillis()),
            pendingIntent,
        )
    }

    private companion object {
        const val STANDARD_SUFFIX = "standard"
        const val LIVE_SUFFIX = "live"
        const val END_SUFFIX = "end"
        const val DEBUG_STANDARD_SUFFIX = "debug-standard"
        const val DEBUG_LIVE_SUFFIX = "debug-live"
        const val DEBUG_END_SUFFIX = "debug-end"
        val ALL_SUFFIXES = buildList {
            add(STANDARD_SUFFIX)
            add(LIVE_SUFFIX)
            add(END_SUFFIX)
            if (BuildConfig.DEBUG) {
                add(DEBUG_STANDARD_SUFFIX)
                add(DEBUG_LIVE_SUFFIX)
                add(DEBUG_END_SUFFIX)
            }
        }
    }
}
