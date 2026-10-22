package com.aero.browser.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY location ASC, createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String): Int

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): BookmarkEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun observeBookmarked(url: String): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun count(): Int
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 1000")
    fun observeRecent(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: HistoryEntity): Long

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE visitedAt < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long): Int

    @Query("DELETE FROM history WHERE visitedAt >= :cutoff")
    suspend fun deleteSince(cutoff: Long): Int

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteByUrl(url: String): Int

    @Query("SELECT * FROM history WHERE url = :url ORDER BY visitedAt DESC LIMIT 1")
    suspend fun findByUrl(url: String): HistoryEntity?

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY visitedAt DESC LIMIT 1000)")
    suspend fun trimToRecentLimit()

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun get(key: String): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SettingsEntity)

    @Query("DELETE FROM settings")
    suspend fun clear()
}

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs WHERE isPrivate = 0 ORDER BY position ASC")
    suspend fun getNormalTabs(): List<TabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(tabs: List<TabEntity>)

    @Query("DELETE FROM tabs")
    suspend fun clearAll()

    @Query("DELETE FROM tabs WHERE id = :id")
    suspend fun deleteById(id: String)
}
