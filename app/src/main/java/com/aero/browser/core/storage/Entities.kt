package com.aero.browser.core.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Bookmark/favorite record. Desktop parity: `aero.favorites` localStorage key. */
@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["url"], unique = true)],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,          // deduplicated by URL
    val createdAt: Long,      // epoch millis
    val location: Int = 0,    // ordering in favorites bar
    val favicon: String? = null,
)

/** Browsing history. Desktop parity: `aero.history`, capped at 1000 entries. */
@Entity(
    tableName = "history",
    indices = [Index(value = ["url"]), Index(value = ["visitedAt"])],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long,      // epoch millis, newer rows first
    val visitCount: Int = 1,
)

/**
 * Generic settings row. Sensitive settings live in [SecurePreferences]
 * instead; this table carries ordinary UI state that is safe on disk.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    val value: String,        // JSON-encoded payload, schema owned by caller
    val updatedAt: Long,
)

/** Persistent normal-tab snapshot. Private tabs are never written to Room. */
@Entity(tableName = "tabs", indices = [Index(value = ["position"]), Index(value = ["isPrivate"])])
data class TabEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val favicon: String?,
    val position: Int,
    val isPrivate: Boolean = false,
    /** Opaque WebView saveState Bundle bytes; nullable for tabs not yet loaded. */
    val webViewState: ByteArray? = null,
)
