package com.aero.browser.features.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aero.browser.core.storage.HistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(private val repository: HistoryRepository) : ViewModel() {
    val history = repository.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun record(url: String, title: String?) = viewModelScope.launch { repository.record(url, title) }
    fun clear() = viewModelScope.launch { repository.clear() }
    fun clearSince(cutoff: Long) = viewModelScope.launch { repository.clearSince(cutoff) }
    fun remove(url: String) = viewModelScope.launch { repository.remove(url) }
    fun observeHistory() = repository.observeHistory()

    fun grouped(items: List<HistoryEntity>, query: String): List<Pair<String, List<HistoryEntity>>> =
        items.filter { query.isBlank() || it.title.contains(query, true) || it.url.contains(query, true) }
            .groupBy { HistoryDates.label(it.visitedAt) }
            .toList()
            .sortedBy { (label, entries) -> HistoryDates.sortOrder(label, entries.maxOfOrNull { it.visitedAt } ?: 0L) }
}
