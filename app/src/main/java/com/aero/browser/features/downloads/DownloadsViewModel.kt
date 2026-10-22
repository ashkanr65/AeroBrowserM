package com.aero.browser.features.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadsViewModel @Inject constructor(private val repository: DownloadRepository) : ViewModel() {
    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    fun refresh() { viewModelScope.launch(Dispatchers.IO) { _downloads.value = repository.queryAll() } }
    fun start(
        url: String,
        userAgent: String?,
        disposition: String?,
        mimeType: String?,
        title: String?,
        onStarted: (Long) -> Unit = {},
        onFailure: (Throwable) -> Unit = {},
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val id = repository.enqueue(url, userAgent, disposition, mimeType, title)
                _downloads.value = repository.queryAll()
                onStarted(id)
            } catch (failure: Throwable) {
                onFailure(failure)
            }
        }
    }
    fun remove(id: Long) { viewModelScope.launch(Dispatchers.IO) { repository.remove(id); _downloads.value = repository.queryAll() } }

    init { refresh() }
}
