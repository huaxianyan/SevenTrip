package com.neko7ina.wallet.assistant.data

import android.content.Context
import com.neko7ina.wallet.assistant.archive.TripAutoArchiveScheduler
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.TravelDocumentStatus
import com.neko7ina.wallet.assistant.core.model.stableId
import com.neko7ina.wallet.assistant.reminder.TripReminderScheduler
import com.neko7ina.wallet.assistant.settings.AppPreferences

/**
 * 把解析出的行程写进本地记录，并安排好发车提醒与自动归档。
 *
 * 应用内导入和后台自动同步共用这一条路径，避免两份逻辑各自演化。
 *
 * 调用方必须保证「先落库、后推进同步位置」的顺序：中途失败时下次会重新扫到
 * 同一封邮件，而 [TravelDocumentRepository.replaceReservations] 按行程主键覆盖
 * 是幂等的，不会因此写出重复行程；反过来先推进位置就会真的漏掉。
 */
class TravelDocumentImporter(context: Context) {
    private val applicationContext = context.applicationContext
    private val repository = TravelDocumentRepository(
        TravelWalletDatabase.getInstance(applicationContext).travelDocumentDao(),
    )
    private val reminderScheduler = TripReminderScheduler(applicationContext)
    private val autoArchiveScheduler = TripAutoArchiveScheduler(applicationContext)
    private val preferences = AppPreferences(applicationContext)

    suspend fun persist(documents: List<TravelDocument>): List<SavedTravelDocument> {
        repository.getByReservations(documents).forEach { existing ->
            val id = existing.document.stableId()
            reminderScheduler.cancel(id)
            autoArchiveScheduler.cancel(id)
        }
        val savedDocuments = repository.replaceReservations(
            documents = documents,
            defaultReminderEnabled = preferences.newTripsReminderEnabled,
        )
        savedDocuments.forEach { saved ->
            if (
                saved.document.status == TravelDocumentStatus.CONFIRMED &&
                saved.reminderEnabled
            ) {
                reminderScheduler.schedule(saved.document)
            }
            if (saved.document.status == TravelDocumentStatus.CONFIRMED) {
                autoArchiveScheduler.scheduleOrArchive(saved)
            }
        }
        return savedDocuments
    }
}
