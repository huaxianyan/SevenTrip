package com.neko7ina.wallet.assistant

import android.app.Application
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neko7ina.wallet.assistant.archive.TripAutoArchiveScheduler
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.TravelDocumentStatus
import com.neko7ina.wallet.assistant.core.model.stableId
import com.neko7ina.wallet.assistant.data.SavedTravelDocument
import com.neko7ina.wallet.assistant.data.TravelDocumentImporter
import com.neko7ina.wallet.assistant.data.TravelDocumentRepository
import com.neko7ina.wallet.assistant.data.TravelWalletDatabase
import com.neko7ina.wallet.assistant.email.AutomaticEmailSyncScheduler
import com.neko7ina.wallet.assistant.email.EmailAccountStore
import com.neko7ina.wallet.assistant.email.EmailSyncCoordinator
import com.neko7ina.wallet.assistant.email.EmailSyncOutcome
import com.neko7ina.wallet.assistant.email.EmailSyncProgress
import com.neko7ina.wallet.assistant.email.ImapAccessException
import com.neko7ina.wallet.assistant.email.ImapAccountConfig
import com.neko7ina.wallet.assistant.email.ImapClient
import com.neko7ina.wallet.assistant.email.ImapFolderOption
import com.neko7ina.wallet.assistant.reminder.TripReminderScheduler
import com.neko7ina.wallet.assistant.settings.AppPreferences
import com.neko7ina.wallet.assistant.settings.AutomaticEmailSyncInterval
import com.neko7ina.wallet.assistant.settings.AutomaticEmailSyncStatus
import com.neko7ina.wallet.assistant.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TravelWalletViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TravelDocumentRepository(
        TravelWalletDatabase.getInstance(application).travelDocumentDao(),
    )
    private val imapClient = ImapClient()
    private val emailAccountStore = EmailAccountStore(application)
    private val emailSyncCoordinator = EmailSyncCoordinator(application)
    private val importer = TravelDocumentImporter(application)
    private val appPreferences = AppPreferences(application)
    private val automaticEmailSyncScheduler = AutomaticEmailSyncScheduler(application)
    private val reminderScheduler = TripReminderScheduler(application)
    private val autoArchiveScheduler = TripAutoArchiveScheduler(application)
    private val mutableEmailImportState = MutableStateFlow<EmailImportState>(EmailImportState.Idle)
    private val mutableEmailAccountSummary = MutableStateFlow(emailAccountStore.summary())
    private val mutableEmailAccountTestState =
        MutableStateFlow<EmailAccountTestState>(EmailAccountTestState.Idle)
    private val mutableEmailFolderState =
        MutableStateFlow<EmailFolderState>(EmailFolderState.Idle)
    private val mutableAutomaticEmailSyncEnabled = MutableStateFlow(
        appPreferences.automaticEmailSyncEnabled,
    )
    private val mutableAutomaticEmailSyncInterval = MutableStateFlow(
        appPreferences.automaticEmailSyncInterval,
    )
    private val mutableAutomaticEmailSyncStatus = MutableStateFlow(
        appPreferences.automaticEmailSyncStatus,
    )
    private val mutableAutomaticEmailSyncStatusAt = MutableStateFlow(
        appPreferences.automaticEmailSyncStatusAtEpochMillis,
    )
    private val mutableNewTripsReminderEnabled = MutableStateFlow(
        appPreferences.newTripsReminderEnabled,
    )
    private val mutableAutoArchiveDepartedTrips = MutableStateFlow(
        appPreferences.autoArchiveDepartedTrips,
    )
    private val mutableDepartureReminderMinutes = MutableStateFlow(
        appPreferences.departureReminderMinutes,
    )
    private val mutableLiveStatusMinutes = MutableStateFlow(appPreferences.liveStatusMinutes)
    private val mutableGoogleWalletActionVisible = MutableStateFlow(
        appPreferences.googleWalletActionVisible,
    )
    private val mutableThemeMode = MutableStateFlow(appPreferences.themeMode)

    val emailImportState = mutableEmailImportState.asStateFlow()
    val emailAccountSummary = mutableEmailAccountSummary.asStateFlow()
    val emailAccountTestState = mutableEmailAccountTestState.asStateFlow()
    val emailFolderState = mutableEmailFolderState.asStateFlow()
    val automaticEmailSyncEnabled = mutableAutomaticEmailSyncEnabled.asStateFlow()
    val automaticEmailSyncInterval = mutableAutomaticEmailSyncInterval.asStateFlow()
    val automaticEmailSyncStatus = mutableAutomaticEmailSyncStatus.asStateFlow()
    val automaticEmailSyncStatusAt = mutableAutomaticEmailSyncStatusAt.asStateFlow()
    val newTripsReminderEnabled = mutableNewTripsReminderEnabled.asStateFlow()
    val autoArchiveDepartedTrips = mutableAutoArchiveDepartedTrips.asStateFlow()
    val departureReminderMinutes = mutableDepartureReminderMinutes.asStateFlow()
    val liveStatusMinutes = mutableLiveStatusMinutes.asStateFlow()
    val googleWalletActionVisible = mutableGoogleWalletActionVisible.asStateFlow()
    val themeMode = mutableThemeMode.asStateFlow()
    val documents = repository.observeDocuments().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val archivedDocuments = repository.observeArchivedDocuments().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        viewModelScope.launch {
            autoArchiveScheduler.reconcile()
            // 升级后第一次打开时，把旧版本留下的待确认候选补存进本地记录，
            // 这样那张票不用等到下次同步就能在行程页看到。
            emailSyncCoordinator.flushLeftoverPendingImport()
        }
        automaticEmailSyncScheduler.reconcile()
    }

    fun needsEmailHistoryChoice(): Boolean = !emailSyncCoordinator.canEnableAutomaticSync()

    fun loadFromEmail(includeHistoricalTrips: Boolean = false) {
        mutableEmailImportState.value = EmailImportState.Loading(EmailSyncProgress.Connecting)
        viewModelScope.launch {
            val wakeLock = acquireEmailWakeLock()
            try {
                when (
                    val outcome = emailSyncCoordinator.sync(
                        requireExistingCheckpoint = false,
                        includeHistoricalTrips = includeHistoricalTrips,
                        onProgress = { progress ->
                            mutableEmailImportState.value = EmailImportState.Loading(progress)
                        },
                    )
                ) {
                    EmailSyncOutcome.NoAccount -> mutableEmailImportState.value =
                        EmailImportState.Error("请先在设置中配置邮箱。")

                    EmailSyncOutcome.InitialSyncRequired -> mutableEmailImportState.value =
                        EmailImportState.Error("请先完成一次手动邮箱同步。")

                    EmailSyncOutcome.NoNewRailwayMessages -> {
                        updateAutomaticEmailSyncStatus(AutomaticEmailSyncStatus.SUCCESS)
                        mutableEmailImportState.value = emptyEmailImportSuccess(
                            "没有发现新的铁路订单通知。",
                        )
                    }

                    EmailSyncOutcome.NoRecognizableTrips -> {
                        updateAutomaticEmailSyncStatus(AutomaticEmailSyncStatus.SUCCESS)
                        mutableEmailImportState.value = emptyEmailImportSuccess(
                            "本次同步没有发现新的可识别行程。",
                        )
                    }

                    is EmailSyncOutcome.Imported -> {
                        updateAutomaticEmailSyncStatus(AutomaticEmailSyncStatus.SUCCESS)
                        mutableEmailImportState.value = EmailImportState.Success(
                            documents = outcome.documents,
                            warnings = outcome.warnings,
                        )
                    }
                }
            } catch (error: ImapAccessException) {
                updateAutomaticEmailSyncStatus(AutomaticEmailSyncStatus.FAILED)
                mutableEmailImportState.value = EmailImportState.Error(requireNotNull(error.message))
            } catch (_: Exception) {
                updateAutomaticEmailSyncStatus(AutomaticEmailSyncStatus.FAILED)
                mutableEmailImportState.value = EmailImportState.Error(
                    "暂时无法读取邮箱，请检查网络和邮箱配置后重试。",
                )
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    fun testAndSaveEmailAccount(config: ImapAccountConfig) {
        mutableEmailAccountTestState.value = EmailAccountTestState.Testing
        viewModelScope.launch {
            val wakeLock = acquireEmailWakeLock()
            try {
                imapClient.testConnection(config)
                val currentAccount = emailAccountStore.load()
                val sameMailbox = currentAccount != null &&
                    currentAccount.emailAddress == config.emailAddress &&
                    currentAccount.username == config.username &&
                    currentAccount.host == config.host &&
                    currentAccount.port == config.port
                val savedConfig = config.copy(
                    folderName = currentAccount?.folderName.takeIf { sameMailbox },
                )
                currentAccount?.takeIf {
                    it.fingerprint != savedConfig.fingerprint
                }?.let {
                    appPreferences.clearImapSyncCheckpoints(it.fingerprint)
                    repository.deletePendingEmailImport()
                }
                emailAccountStore.save(savedConfig)
                mutableEmailAccountSummary.value = emailAccountStore.summary()
                mutableEmailFolderState.value = EmailFolderState.Idle
                mutableEmailAccountTestState.value = EmailAccountTestState.Success
            } catch (error: ImapAccessException) {
                mutableEmailAccountTestState.value = EmailAccountTestState.Error(
                    requireNotNull(error.message),
                )
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    fun deleteEmailAccount() {
        emailAccountStore.load()?.let {
            appPreferences.clearImapSyncCheckpoints(it.fingerprint)
        }
        emailAccountStore.delete()
        appPreferences.automaticEmailSyncEnabled = false
        mutableAutomaticEmailSyncEnabled.value = false
        automaticEmailSyncScheduler.reconcile()
        viewModelScope.launch { repository.deletePendingEmailImport() }
        mutableEmailAccountSummary.value = null
        mutableEmailAccountTestState.value = EmailAccountTestState.Idle
        mutableEmailFolderState.value = EmailFolderState.Idle
    }

    fun resetEmailAccountTestState() {
        mutableEmailAccountTestState.value = EmailAccountTestState.Idle
        mutableEmailFolderState.value = EmailFolderState.Idle
    }

    fun loadEmailFolders() {
        val account = emailAccountStore.load() ?: return
        mutableEmailFolderState.value = EmailFolderState.Loading
        viewModelScope.launch {
            val wakeLock = acquireEmailWakeLock()
            try {
                mutableEmailFolderState.value = EmailFolderState.Success(
                    imapClient.listSelectableFolders(account),
                )
            } catch (error: ImapAccessException) {
                mutableEmailFolderState.value = EmailFolderState.Error(
                    requireNotNull(error.message),
                )
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    fun selectEmailFolder(folderName: String?) {
        viewModelScope.launch {
            val current = emailAccountStore.load() ?: return@launch
            val updated = current.copy(folderName = folderName)
            if (updated.fingerprint == current.fingerprint) return@launch
            appPreferences.clearImapSyncCheckpoints(current.fingerprint)
            emailAccountStore.save(updated)
            mutableEmailAccountSummary.value = emailAccountStore.summary()
            if (appPreferences.automaticEmailSyncEnabled) {
                appPreferences.automaticEmailSyncEnabled = false
                mutableAutomaticEmailSyncEnabled.value = false
                updateAutomaticEmailSyncStatus(AutomaticEmailSyncStatus.INITIAL_SYNC_REQUIRED)
                automaticEmailSyncScheduler.reconcile()
            }
        }
    }

    fun save(documents: List<TravelDocument>) {
        viewModelScope.launch { importer.persist(documents) }
    }

    fun setAutomaticEmailSyncEnabled(enabled: Boolean): Boolean {
        if (enabled && !emailSyncCoordinator.canEnableAutomaticSync()) return false
        appPreferences.automaticEmailSyncEnabled = enabled
        mutableAutomaticEmailSyncEnabled.value = enabled
        automaticEmailSyncScheduler.reconcile()
        return true
    }

    fun setAutomaticEmailSyncInterval(interval: AutomaticEmailSyncInterval) {
        appPreferences.automaticEmailSyncInterval = interval
        mutableAutomaticEmailSyncInterval.value = interval
        automaticEmailSyncScheduler.reconcile()
    }

    fun setNewTripsReminderEnabled(enabled: Boolean) {
        appPreferences.newTripsReminderEnabled = enabled
        mutableNewTripsReminderEnabled.value = enabled
    }

    fun setAutoArchiveDepartedTrips(enabled: Boolean) {
        appPreferences.autoArchiveDepartedTrips = enabled
        mutableAutoArchiveDepartedTrips.value = enabled
        viewModelScope.launch { autoArchiveScheduler.reconcile() }
    }

    fun setDepartureReminderMinutes(minutes: Int) {
        appPreferences.departureReminderMinutes = minutes
        mutableDepartureReminderMinutes.value = appPreferences.departureReminderMinutes
        rescheduleEnabledDocuments()
    }

    fun setLiveStatusMinutes(minutes: Int) {
        appPreferences.liveStatusMinutes = minutes
        mutableLiveStatusMinutes.value = appPreferences.liveStatusMinutes
        rescheduleEnabledDocuments()
    }

    fun setGoogleWalletActionVisible(visible: Boolean) {
        appPreferences.googleWalletActionVisible = visible
        mutableGoogleWalletActionVisible.value = visible
    }

    fun setThemeMode(mode: ThemeMode) {
        appPreferences.themeMode = mode
        mutableThemeMode.value = mode
    }

    fun setReminderEnabled(saved: SavedTravelDocument, enabled: Boolean) {
        if (saved.document.status != TravelDocumentStatus.CONFIRMED) return
        viewModelScope.launch {
            val updated = repository.setReminderEnabled(saved.document.stableId(), enabled) ?: return@launch
            if (enabled) {
                reminderScheduler.schedule(updated.document)
            } else {
                reminderScheduler.cancel(updated.document.stableId())
            }
        }
    }

    fun setArchived(saved: SavedTravelDocument, archived: Boolean) {
        if (!archived && saved.document.status != TravelDocumentStatus.CONFIRMED) return
        viewModelScope.launch {
            val documentId = saved.document.stableId()
            val updated = repository.setArchived(documentId, archived) ?: return@launch
            if (archived) {
                reminderScheduler.cancel(documentId)
                autoArchiveScheduler.cancel(documentId)
            } else {
                autoArchiveScheduler.scheduleOrArchive(updated)
            }
        }
    }

    fun delete(saved: SavedTravelDocument) {
        viewModelScope.launch {
            val documentId = saved.document.stableId()
            reminderScheduler.cancel(documentId)
            autoArchiveScheduler.cancel(documentId)
            repository.delete(documentId)
        }
    }

    fun scheduleReminderTest(document: TravelDocument) {
        reminderScheduler.scheduleDebugSequence(document)
    }

    private fun emptyEmailImportSuccess(message: String): EmailImportState.Success =
        EmailImportState.Success(
            documents = emptyList(),
            warnings = listOf(message),
        )

    fun refreshAutomaticEmailSyncStatus() {
        mutableAutomaticEmailSyncEnabled.value = appPreferences.automaticEmailSyncEnabled
        mutableAutomaticEmailSyncStatus.value = appPreferences.automaticEmailSyncStatus
        mutableAutomaticEmailSyncStatusAt.value =
            appPreferences.automaticEmailSyncStatusAtEpochMillis
    }

    private fun updateAutomaticEmailSyncStatus(status: AutomaticEmailSyncStatus) {
        val now = System.currentTimeMillis()
        appPreferences.automaticEmailSyncStatus = status
        appPreferences.automaticEmailSyncStatusAtEpochMillis = now
        mutableAutomaticEmailSyncStatus.value = status
        mutableAutomaticEmailSyncStatusAt.value = now
    }

    private fun rescheduleEnabledDocuments() {
        viewModelScope.launch {
            repository.getReminderEnabledDocuments().forEach(reminderScheduler::schedule)
        }
    }

    private fun acquireEmailWakeLock(): PowerManager.WakeLock =
        getApplication<Application>()
            .getSystemService(PowerManager::class.java)
            .newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "${BuildConfig.APPLICATION_ID}:EmailSync",
            )
            .apply {
                setReferenceCounted(false)
                acquire(EMAIL_WAKE_LOCK_TIMEOUT_MILLIS)
            }

    private companion object {
        const val EMAIL_WAKE_LOCK_TIMEOUT_MILLIS = 5 * 60 * 1_000L
    }
}

sealed interface EmailImportState {
    data object Idle : EmailImportState
    data class Loading(val progress: EmailSyncProgress) : EmailImportState
    data class Success(
        val documents: List<TravelDocument>,
        val warnings: List<String>,
    ) : EmailImportState
    data class Error(val message: String) : EmailImportState
}

sealed interface EmailFolderState {
    data object Idle : EmailFolderState
    data object Loading : EmailFolderState
    data class Success(val folders: List<ImapFolderOption>) : EmailFolderState
    data class Error(val message: String) : EmailFolderState
}

sealed interface EmailAccountTestState {
    data object Idle : EmailAccountTestState
    data object Testing : EmailAccountTestState
    data object Success : EmailAccountTestState
    data class Error(val message: String) : EmailAccountTestState
}
