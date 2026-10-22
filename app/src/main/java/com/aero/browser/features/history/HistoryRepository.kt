package com.aero.browser.features.history

import androidx.room.withTransaction
import com.aero.browser.core.storage.AppDatabase
import com.aero.browser.core.storage.HistoryDao
import com.aero.browser.core.storage.HistoryEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class HistoryRepository @Inject constructor(
    private val dao: HistoryDao,
    private val database: AppDatabase,
) {
    fun observeHistory(): Flow<List<HistoryEntity>> = dao.observeRecent()

    suspend fun record(url: String, title: String?) {
        val scheme = url.substringBefore(':', "").lowercase()
        if (url.isBlank() || scheme !in setOf("http", "https")) return
        database.withTransaction {
            val now = System.currentTimeMillis()
            val previous = dao.findByUrl(url)
            dao.upsert(
                HistoryEntity(
                    id = previous?.id ?: 0,
                    title = title?.takeIf(String::isNotBlank) ?: previous?.title ?: url,
                    url = url,
                    visitedAt = now,
                    visitCount = (previous?.visitCount ?: 0) + 1,
                ),
            )
            dao.trimToRecentLimit()
        }
    }

    suspend fun remove(url: String) { dao.deleteByUrl(url) }

    suspend fun clear() { dao.clear() }

    suspend fun clearSince(cutoff: Long) { dao.deleteSince(cutoff) }
}
