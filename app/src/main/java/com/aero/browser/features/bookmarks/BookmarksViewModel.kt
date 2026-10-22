package com.aero.browser.features.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aero.browser.core.storage.BookmarkEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BookmarksViewModel @Inject constructor(private val repository: BookmarkRepository) : ViewModel() {
    val bookmarks = repository.observeBookmarks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun observeBookmarked(url: String): Flow<Boolean> = repository.observeBookmarked(url)
    fun add(title: String, url: String, favicon: String? = null) = viewModelScope.launch { repository.add(title, url, favicon) }
    fun remove(url: String) = viewModelScope.launch { repository.remove(url) }
    suspend fun isBookmarked(url: String): Boolean = repository.isBookmarked(url)
}
