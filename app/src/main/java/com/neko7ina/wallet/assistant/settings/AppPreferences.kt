package com.neko7ina.wallet.assistant.settings

import android.content.Context
import com.neko7ina.wallet.assistant.email.ImapSyncCheckpoint
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class AutomaticEmailSyncInterval(val hours: Long) {
    ONE_HOUR(1),
    THREE_HOURS(3),
    SIX_HOURS(6),
    TWELVE_HOURS(12),
    TWENTY_FOUR_HOURS(24),
}

enum class AutomaticEmailSyncStatus {
    NEVER,
    SUCCESS,
    FAILED,
    INITIAL_SYNC_REQUIRED,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

object ReminderTimingConstraints {
    const val DEPARTURE_STEP_MINUTES = 15
    const val LIVE_STEP_MINUTES = 5
    const val DEPARTURE_MIN_MINUTES = 30
    const val DEPARTURE_MAX_MINUTES = 12 * 60
    const val DEPARTURE_DEFAULT_MINUTES = 3 * 60
    const val LIVE_MIN_MINUTES = 15
    const val LIVE_MAX_MINUTES = 60
    const val LIVE_DEFAULT_MINUTES = 30
}

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    init {
        val obsoleteKeys = preferences.all.keys.filter {
            it.startsWith("gmail_processed_") ||
                it.startsWith("imap_processed_") ||
                it == "ignore_departed_trips_on_import"
        }
        if (obsoleteKeys.isNotEmpty()) {
            preferences.edit().apply {
                obsoleteKeys.forEach(::remove)
            }.apply()
        }
    }

    var automaticEmailSyncEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTOMATIC_EMAIL_SYNC_ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_AUTOMATIC_EMAIL_SYNC_ENABLED, value).apply()
        }

    var automaticEmailSyncInterval: AutomaticEmailSyncInterval
        get() = runCatching {
            AutomaticEmailSyncInterval.valueOf(
                preferences.getString(
                    KEY_AUTOMATIC_EMAIL_SYNC_INTERVAL,
                    AutomaticEmailSyncInterval.SIX_HOURS.name,
                ) ?: AutomaticEmailSyncInterval.SIX_HOURS.name,
            )
        }.getOrDefault(AutomaticEmailSyncInterval.SIX_HOURS)
        set(value) {
            preferences.edit().putString(KEY_AUTOMATIC_EMAIL_SYNC_INTERVAL, value.name).apply()
        }

    var automaticEmailSyncStatus: AutomaticEmailSyncStatus
        get() = runCatching {
            AutomaticEmailSyncStatus.valueOf(
                preferences.getString(
                    KEY_AUTOMATIC_EMAIL_SYNC_STATUS,
                    AutomaticEmailSyncStatus.NEVER.name,
                ) ?: AutomaticEmailSyncStatus.NEVER.name,
            )
        }.getOrDefault(AutomaticEmailSyncStatus.NEVER)
        set(value) {
            preferences.edit().putString(KEY_AUTOMATIC_EMAIL_SYNC_STATUS, value.name).apply()
        }

    var automaticEmailSyncStatusAtEpochMillis: Long
        get() = preferences.getLong(KEY_AUTOMATIC_EMAIL_SYNC_STATUS_AT, 0L)
        set(value) {
            preferences.edit().putLong(KEY_AUTOMATIC_EMAIL_SYNC_STATUS_AT, value).apply()
        }

    var newTripsReminderEnabled: Boolean
        get() = preferences.getBoolean(KEY_NEW_TRIPS_REMINDER, false)
        set(value) {
            preferences.edit().putBoolean(KEY_NEW_TRIPS_REMINDER, value).apply()
        }

    var autoArchiveDepartedTrips: Boolean
        get() = preferences.getBoolean(KEY_AUTO_ARCHIVE_DEPARTED_TRIPS, false)
        set(value) {
            preferences.edit().putBoolean(KEY_AUTO_ARCHIVE_DEPARTED_TRIPS, value).apply()
        }

    var departureReminderMinutes: Int
        get() = preferences.getInt(
            KEY_DEPARTURE_REMINDER_MINUTES,
            ReminderTimingConstraints.DEPARTURE_DEFAULT_MINUTES,
        ).coerceIn(
            ReminderTimingConstraints.DEPARTURE_MIN_MINUTES,
            ReminderTimingConstraints.DEPARTURE_MAX_MINUTES,
        )
        set(value) {
            preferences.edit().putInt(
                KEY_DEPARTURE_REMINDER_MINUTES,
                value.coerceIn(
                    ReminderTimingConstraints.DEPARTURE_MIN_MINUTES,
                    ReminderTimingConstraints.DEPARTURE_MAX_MINUTES,
                ),
            ).apply()
        }

    var liveStatusMinutes: Int
        get() = preferences.getInt(
            KEY_LIVE_STATUS_MINUTES,
            ReminderTimingConstraints.LIVE_DEFAULT_MINUTES,
        ).coerceIn(
            ReminderTimingConstraints.LIVE_MIN_MINUTES,
            ReminderTimingConstraints.LIVE_MAX_MINUTES,
        )
        set(value) {
            preferences.edit().putInt(
                KEY_LIVE_STATUS_MINUTES,
                value.coerceIn(
                    ReminderTimingConstraints.LIVE_MIN_MINUTES,
                    ReminderTimingConstraints.LIVE_MAX_MINUTES,
                ),
            ).apply()
        }

    fun imapSyncCheckpoint(
        accountFingerprint: String,
        parserVersion: Int,
    ): ImapSyncCheckpoint? {
        val key = imapCheckpointKey(accountFingerprint, parserVersion)
        preferences.getString(key, null)?.let { value ->
            return runCatching { Json.decodeFromString<ImapSyncCheckpoint>(value) }.getOrNull()
        }
        val legacyPrefix = "imap_checkpoint_${accountFingerprint}_v${parserVersion}_"
        val migrated = preferences.all
            .filterKeys { it.startsWith(legacyPrefix) }
            .values
            .filterIsInstance<String>()
            .mapNotNull { value ->
                runCatching { Json.decodeFromString<ImapSyncCheckpoint>(value) }.getOrNull()
            }
            .maxByOrNull(ImapSyncCheckpoint::lastScannedUid)
            ?: return null
        preferences.edit().apply {
            putString(key, Json.encodeToString(migrated))
            preferences.all.keys
                .filter { it.startsWith(legacyPrefix) }
                .forEach(::remove)
        }.apply()
        return migrated
    }

    fun saveImapSyncCheckpoint(
        accountFingerprint: String,
        parserVersion: Int,
        checkpoint: ImapSyncCheckpoint,
    ) {
        preferences.edit().putString(
            imapCheckpointKey(accountFingerprint, parserVersion),
            Json.encodeToString(checkpoint),
        ).apply()
    }

    fun clearImapSyncCheckpoints(accountFingerprint: String) {
        val prefix = "imap_checkpoint_${accountFingerprint}_"
        val keys = preferences.all.keys.filter { it.startsWith(prefix) }
        if (keys.isNotEmpty()) {
            preferences.edit().apply { keys.forEach(::remove) }.apply()
        }
    }

    private fun imapCheckpointKey(
        accountFingerprint: String,
        parserVersion: Int,
    ): String = "imap_checkpoint_${accountFingerprint}_v${parserVersion}"

    var googleWalletActionVisible: Boolean
        get() = preferences.getBoolean(KEY_GOOGLE_WALLET_VISIBLE, true)
        set(value) {
            preferences.edit().putBoolean(KEY_GOOGLE_WALLET_VISIBLE, value).apply()
        }

    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(
                preferences.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name,
            )
        }.getOrDefault(ThemeMode.SYSTEM)
        set(value) {
            preferences.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    private companion object {
        const val PREFERENCES_NAME = "trip_reminders"
        const val KEY_AUTOMATIC_EMAIL_SYNC_ENABLED = "automatic_email_sync_enabled"
        const val KEY_AUTOMATIC_EMAIL_SYNC_INTERVAL = "automatic_email_sync_interval"
        const val KEY_AUTOMATIC_EMAIL_SYNC_STATUS = "automatic_email_sync_status"
        const val KEY_AUTOMATIC_EMAIL_SYNC_STATUS_AT = "automatic_email_sync_status_at"
        const val KEY_NEW_TRIPS_REMINDER = "new_trips_enabled"
        const val KEY_AUTO_ARCHIVE_DEPARTED_TRIPS = "auto_archive_departed_trips"
        const val KEY_DEPARTURE_REMINDER_MINUTES = "departure_reminder_minutes"
        const val KEY_LIVE_STATUS_MINUTES = "live_status_minutes"
        const val KEY_GOOGLE_WALLET_VISIBLE = "google_wallet_visible"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
