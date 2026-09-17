package com.neko7ina.wallet.assistant.email

import android.content.Context
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.parser.ChinaRailwayEmailParser
import com.neko7ina.wallet.assistant.core.parser.ParseResult
import com.neko7ina.wallet.assistant.data.TravelDocumentImporter
import com.neko7ina.wallet.assistant.data.TravelDocumentRepository
import com.neko7ina.wallet.assistant.data.TravelWalletDatabase
import com.neko7ina.wallet.assistant.hasDeparted
import com.neko7ina.wallet.assistant.settings.AppPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EmailSyncCoordinator(context: Context) {
    private val applicationContext = context.applicationContext
    private val repository = TravelDocumentRepository(
        TravelWalletDatabase.getInstance(applicationContext).travelDocumentDao(),
    )
    private val importer = TravelDocumentImporter(applicationContext)
    private val accountStore = EmailAccountStore(applicationContext)
    private val preferences = AppPreferences(applicationContext)
    private val parser = ChinaRailwayEmailParser()
    private val imapClient = ImapClient()

    fun canEnableAutomaticSync(): Boolean {
        val account = accountStore.load() ?: return false
        return preferences.imapSyncCheckpoint(
            accountFingerprint = account.fingerprint,
            parserVersion = parser.version,
        ) != null
    }

    suspend fun sync(
        requireExistingCheckpoint: Boolean,
        includeHistoricalTrips: Boolean = false,
        onProgress: (EmailSyncProgress) -> Unit = {},
    ): EmailSyncOutcome = syncMutex.withLock {
        flushLeftoverPendingImportLocked()
        val account = accountStore.load() ?: return EmailSyncOutcome.NoAccount
        val checkpoint = preferences.imapSyncCheckpoint(
            accountFingerprint = account.fingerprint,
            parserVersion = parser.version,
        )
        if (requireExistingCheckpoint && checkpoint == null) {
            return EmailSyncOutcome.InitialSyncRequired
        }
        val searchResult = try {
            imapClient.searchRailwayMessages(
                config = account,
                checkpoint = checkpoint,
                allowFullScan = !requireExistingCheckpoint,
                onProgress = { progress ->
                    onProgress(
                        when (progress) {
                            ImapSyncProgress.Connecting -> EmailSyncProgress.Connecting
                            ImapSyncProgress.CheckingMessages -> EmailSyncProgress.CheckingMessages
                            is ImapSyncProgress.ReadingRailwayMessage ->
                                EmailSyncProgress.ReadingRailwayMessages(
                                    completed = progress.completed,
                                    total = progress.total,
                                )
                        },
                    )
                },
            )
        } catch (_: ImapFullSyncRequiredException) {
            preferences.clearImapSyncCheckpoints(account.fingerprint)
            return EmailSyncOutcome.InitialSyncRequired
        }
        if (searchResult.messages.isEmpty()) {
            saveCheckpoint(account.fingerprint, searchResult.nextCheckpoint)
            return EmailSyncOutcome.NoNewRailwayMessages
        }

        onProgress(EmailSyncProgress.OrganizingTrips)
        return when (
            val result = parser.parseAll(
                documents = searchResult.messages,
                baselineDocuments = if (searchResult.fullScan) {
                    emptyList()
                } else {
                    repository.allDocuments()
                },
            )
        ) {
            is ParseResult.Success -> {
                val documents = result.documents.filterNot { document ->
                    searchResult.fullScan && !includeHistoricalTrips && document.hasDeparted()
                }
                if (documents.isEmpty()) {
                    saveCheckpoint(account.fingerprint, searchResult.nextCheckpoint)
                    EmailSyncOutcome.NoRecognizableTrips
                } else {
                    // 先落库再推进同步位置。中途失败时下次会重新扫到同一批邮件，
                    // 而落库按行程主键覆盖是幂等的，不会写出重复行程；顺序反过来
                    // 就会真的把这几封邮件漏掉。
                    importer.persist(documents)
                    saveCheckpoint(account.fingerprint, searchResult.nextCheckpoint)
                    EmailSyncOutcome.Imported(
                        documents = documents,
                        warnings = result.warnings,
                    )
                }
            }

            is ParseResult.Failure -> {
                saveCheckpoint(account.fingerprint, searchResult.nextCheckpoint)
                EmailSyncOutcome.NoRecognizableTrips
            }
        }
    }

    /**
     * 消化旧版本遗留的待确认候选。
     *
     * 早期策略是把候选存进 `pending_email_import` 等用户确认，确认前不推进同步位置，
     * 期间所有同步都会被挡下来。现在解析出来就直接保存，所以升级后补存一次，
     * 免得那张票永远留在待确认状态。
     *
     * 应用启动和同步开始时都会调用。重复调用是安全的：候选补存后即被删除，
     * 而落库按行程主键覆盖本身幂等。
     */
    suspend fun flushLeftoverPendingImport() = syncMutex.withLock {
        flushLeftoverPendingImportLocked()
    }

    /** [sync] 已持有 [syncMutex]，走这个不加锁的版本。 */
    private suspend fun flushLeftoverPendingImportLocked() {
        val leftover = repository.pendingEmailImport() ?: return
        importer.persist(leftover.documents)
        saveCheckpoint(leftover.accountFingerprint, leftover.checkpoint)
        repository.deletePendingEmailImport()
    }

    private fun saveCheckpoint(
        accountFingerprint: String,
        checkpoint: ImapSyncCheckpoint,
    ) {
        preferences.saveImapSyncCheckpoint(
            accountFingerprint = accountFingerprint,
            parserVersion = parser.version,
            checkpoint = checkpoint,
        )
    }

    private companion object {
        val syncMutex = Mutex()
    }
}

sealed interface EmailSyncProgress {
    data object Connecting : EmailSyncProgress
    data object CheckingMessages : EmailSyncProgress
    data class ReadingRailwayMessages(val completed: Int, val total: Int) : EmailSyncProgress
    data object OrganizingTrips : EmailSyncProgress
}

sealed interface EmailSyncOutcome {
    data object NoAccount : EmailSyncOutcome
    data object InitialSyncRequired : EmailSyncOutcome
    data object NoNewRailwayMessages : EmailSyncOutcome
    data object NoRecognizableTrips : EmailSyncOutcome
    data class Imported(
        val documents: List<TravelDocument>,
        val warnings: List<String>,
    ) : EmailSyncOutcome
}
