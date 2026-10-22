package com.aero.browser.features.bookmarks

import androidx.room.withTransaction
import com.aero.browser.core.storage.AppDatabase
import com.aero.browser.core.storage.BookmarkDao
import com.aero.browser.core.storage.BookmarkEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class BookmarkRepository @Inject constructor(
    private val dao: BookmarkDao,
    private val database: AppDatabase,
) {
    fun observeBookmarks(): Flow<List<BookmarkEntity>> = dao.observeAll()
    fun observeBookmarked(url: String): Flow<Boolean> = dao.observeBookmarked(url)

    suspend fun add(title: String, url: String, favicon: String? = null): Boolean {
        if (url.isBlank()) return false
        val existing = dao.findByUrl(url)
        if (existing != null) {
            database.withTransaction {
                dao.replace(existing.copy(title = title.ifBlank { existing.title }, favicon = favicon ?: existing.favicon))
            }
            return true
        }
        return dao.insert(
            BookmarkEntity(title = title.ifBlank { url }, url = url, createdAt = System.currentTimeMillis(), favicon = favicon),
        ) != -1L
    }

    suspend fun remove(url: String) { dao.deleteByUrl(url) }
    suspend fun update(bookmark: BookmarkEntity) { dao.replace(bookmark) }
    suspend fun isBookmarked(url: String): Boolean = dao.findByUrl(url) != null
}
