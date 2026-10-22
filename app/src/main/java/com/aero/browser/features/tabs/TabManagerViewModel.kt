package com.aero.browser.features.tabs

import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import androidx.webkit.WebViewFeature
import android.util.Log
import com.aero.browser.core.storage.AppDatabase
import com.aero.browser.core.storage.HomepageSource
import com.aero.browser.core.storage.SettingsEntity
import com.aero.browser.core.storage.StartPage
import com.aero.browser.core.storage.TabEntity
import com.aero.browser.features.tabs.model.Tab
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class TabManagerViewModel @Inject constructor(
    private val database: AppDatabase,
    homepageSource: HomepageSource,
) : ViewModel() {
    /** Page Aero opens by itself (cold start, new tab, replacement tab); follows Settings → Homepage. */
    @Volatile private var startPage: String = homepageSource.homepage()

    fun startPage(): String = startPage

    /** Keeps tabs opened from now on on the configured homepage once the user edits it. */
    fun updateStartPage(homepage: String) {
        startPage = StartPage.normalize(homepage)
    }

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()

    private val _normalTabs = MutableStateFlow<List<Tab>>(emptyList())
    val normalTabs: StateFlow<List<Tab>> = _normalTabs.asStateFlow()

    private val _privateTabs = MutableStateFlow<List<Tab>>(emptyList())
    val privateTabs: StateFlow<List<Tab>> = _privateTabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    private val _isTabOverviewVisible = MutableStateFlow(false)
    val isTabOverviewVisible: StateFlow<Boolean> = _isTabOverviewVisible.asStateFlow()

    private val _isPrivateMode = MutableStateFlow(false)
    val isPrivateMode: StateFlow<Boolean> = _isPrivateMode.asStateFlow()

    private val persistMutex = Mutex()
    private val databaseReady = MutableStateFlow(false)
    private val persistenceJob = java.util.concurrent.atomic.AtomicBoolean(false)
    private val pendingSnapshot = java.util.concurrent.atomic.AtomicReference<List<TabEntity>?>(null)
    private val databaseInitialization = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val attachedPrivateWebViews = mutableSetOf<String>()
    private val persistenceLock = Any()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val rows = database.tabDao().getNormalTabs()
            val restored = rows.map { it.toTab() }
            val savedActiveTabId = database.settingsDao().get(ACTIVE_TAB_ID_SETTING)?.value
            val previousStartPage = database.settingsDao().get(LAST_START_PAGE_SETTING)?.value
            val restoredIds = restored.mapTo(mutableSetOf()) { it.id }
            _normalTabs.value = restored + _normalTabs.value.filterNot { it.id in restoredIds }
            if (_normalTabs.value.isEmpty()) {
                _normalTabs.value = listOf(newTab(isPrivate = false))
            } else {
                followChangedStartPage(savedActiveTabId, previousStartPage)
            }
            publishTabs()
            if (_activeTabId.value == null || _tabs.value.none { it.id == _activeTabId.value }) {
                _activeTabId.value = _normalTabs.value.firstOrNull { it.id == savedActiveTabId }?.id
                    ?: _normalTabs.value.first().id
            }
            persistNormalTabs()
            database.settingsDao().put(
                SettingsEntity(LAST_START_PAGE_SETTING, startPage, System.currentTimeMillis()),
            )
            databaseReady.value = true
            databaseInitialization.complete(Unit)
            persistNormalTabsIfReady()
        }
    }

    /** Currently visible list, kept separate from [tabs] for the two modes. */
    fun visibleTabs(): List<Tab> = if (_isPrivateMode.value) _privateTabs.value else _normalTabs.value

    fun activeTab(): Tab? = visibleTabs().firstOrNull { it.id == _activeTabId.value }

    fun createTab(url: String = startPage): String {
        if (!_isPrivateMode.value && _normalTabs.value.isEmpty()) _normalTabs.value = listOf(newTab(isPrivate = false))
        val tab = newTab(url = url, isPrivate = _isPrivateMode.value)
        if (tab.isPrivate) _privateTabs.value = _privateTabs.value + tab
        else _normalTabs.value = _normalTabs.value + tab
        publishTabs()
        _activeTabId.value = tab.id
        _isTabOverviewVisible.value = false
        persistNormalTabsIfReady()
        return tab.id
    }

    fun closeTab(id: String) {
        val privateTab = _privateTabs.value.any { it.id == id }
        val source = if (privateTab) _privateTabs.value else _normalTabs.value
        val index = source.indexOfFirst { it.id == id }
        if (index < 0) return
        val remaining = source.filterNot { it.id == id }
        if (privateTab) _privateTabs.value = remaining else _normalTabs.value = remaining
        publishTabs()

        if (_activeTabId.value == id) {
            if (remaining.isEmpty()) {
                if (privateTab) {
                    _isPrivateMode.value = false
                    val replacement = _normalTabs.value.firstOrNull() ?: newTab(isPrivate = false).also {
                        _normalTabs.value = listOf(it)
                    }
                    _activeTabId.value = replacement.id
                } else {
                    val replacement = newTab(isPrivate = false)
                    _normalTabs.value = listOf(replacement)
                    _activeTabId.value = replacement.id
                }
                publishTabs()
            } else {
                _activeTabId.value = remaining[index.coerceAtMost(remaining.lastIndex)].id
            }
        }
        if (privateTab) cleanupClosedPrivateProfile(id)
        persistNormalTabsIfReady()
    }

    fun closeAllPrivateTabs() {
        val closedPrivateIds = _privateTabs.value.map { it.id }
        _privateTabs.value = emptyList()
        closedPrivateIds.forEach(::cleanupClosedPrivateProfile)
        val wasViewingPrivateTab = _activeTabId.value in closedPrivateIds
        _isPrivateMode.value = false
        if (wasViewingPrivateTab || _normalTabs.value.none { it.id == _activeTabId.value }) {
            val normalTab = _normalTabs.value.firstOrNull() ?: newTab(isPrivate = false).also {
                _normalTabs.value = listOf(it)
            }
            _activeTabId.value = normalTab.id
        }
        _isTabOverviewVisible.value = false
        publishTabs()
        persistNormalTabsIfReady()
    }

    fun closeAllTabs() {
        if (_isPrivateMode.value) {
            closeAllPrivateTabs()
        } else {
            val replacement = newTab(isPrivate = false)
            _normalTabs.value = listOf(replacement)
            _activeTabId.value = replacement.id
            _isTabOverviewVisible.value = false
            publishTabs()
            persistNormalTabsIfReady()
        }
    }

    fun switchTab(id: String) {
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return
        _isPrivateMode.value = tab.isPrivate
        _activeTabId.value = id
        _isTabOverviewVisible.value = false
        if (!tab.isPrivate) persistNormalTabsIfReady()
    }

    suspend fun awaitDatabaseInitialization() {
        databaseInitialization.await()
    }

    /** Waits until all snapshots scheduled before this call have been committed. */
    suspend fun awaitPendingPersistence() {
        awaitDatabaseInitialization()
        while (true) {
            persistMutex.withLock { }
            if (!persistenceJob.get() && pendingSnapshot.get() == null) return
            kotlinx.coroutines.yield()
        }
    }

    fun saveTabState(id: String, bundle: Bundle) {
        updateTab(id) { it.copy(webViewState = Bundle(bundle)) }
    }

    fun restoreTabState(id: String): Bundle? {
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return null
        return tab.webViewState?.let(::copyBundle)
    }

    fun updateTab(id: String, transform: (Tab) -> Tab) {
        _normalTabs.value = _normalTabs.value.map { if (it.id == id) transform(it) else it }
        _privateTabs.value = _privateTabs.value.map { if (it.id == id) transform(it).copy(webViewState = null) else it }
        publishTabs()
        persistNormalTabsIfReady()
    }

    fun updatePage(id: String, url: String, title: String?, isLoading: Boolean) {
        updateTab(id) { tab ->
            tab.copy(
                url = url,
                title = title?.takeIf(String::isNotBlank) ?: tab.title,
                favicon = tab.favicon,
                isLoading = isLoading,
            )
        }
    }

    fun toggleTabOverview() {
        _isTabOverviewVisible.value = !_isTabOverviewVisible.value
    }

    fun togglePrivateMode() {
        setPrivateMode(!_isPrivateMode.value)
        _isTabOverviewVisible.value = false
    }

    /** Selects a mode while keeping tab overview open for its segmented toggle. */
    fun setPrivateMode(privateMode: Boolean) {
        if (_isPrivateMode.value == privateMode) return
        _isPrivateMode.value = privateMode
        val destination = if (privateMode) _privateTabs.value else _normalTabs.value
        if (destination.isEmpty()) {
            val tab = newTab(isPrivate = privateMode)
            if (privateMode) _privateTabs.value = listOf(tab) else _normalTabs.value = listOf(tab)
            publishTabs()
            _activeTabId.value = tab.id
            persistNormalTabsIfReady()
        } else {
            _activeTabId.value = destination.first().id
            persistNormalTabsIfReady()
        }
    }

    fun isTabOpen(id: String): Boolean = _tabs.value.any { it.id == id }

    fun hasPrivateTabs(): Boolean = _privateTabs.value.isNotEmpty()

    fun attachPrivateWebView(tabId: String) {
        if (_privateTabs.value.any { it.id == tabId }) synchronized(attachedPrivateWebViews) { attachedPrivateWebViews.add(tabId) }
    }

    fun detachPrivateWebView(tabId: String) {
        synchronized(attachedPrivateWebViews) { attachedPrivateWebViews.remove(tabId) }
        if (_privateTabs.value.none { it.id == tabId } && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            deletePrivateProfileSafely(tabId)
        }
    }

    private fun cleanupClosedPrivateProfile(tabId: String) {
        val isAttached = synchronized(attachedPrivateWebViews) { tabId in attachedPrivateWebViews }
        if (!isAttached && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            deletePrivateProfileSafely(tabId)
        }
    }

    private fun deletePrivateProfileSafely(tabId: String) {
        try {
            androidx.webkit.ProfileStore.getInstance().deleteProfile("aero_private_$tabId")
        } catch (failure: IllegalStateException) {
            // Chromium can still have the profile attached to a WebView while Compose disposes it.
            // Do not let profile cleanup crash the browser during quick mode switches.
            Log.w("TabManager", "Private profile cleanup deferred for tab $tabId", failure)
        }
    }

    private fun publishTabs() {
        _tabs.value = _normalTabs.value + _privateTabs.value
    }

    private fun persistNormalTabsIfReady() {
        if (!databaseReady.value) return
        synchronized(persistenceLock) {
            pendingSnapshot.set(_normalTabs.value.mapIndexed { index, tab -> tab.toEntity(index) })
            if (!persistenceJob.compareAndSet(false, true)) return
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    while (true) {
                        val snapshot = pendingSnapshot.getAndSet(null) ?: break
                        persistMutex.withLock { persistTabSnapshot(snapshot) }
                    }
                } finally {
                    persistenceJob.set(false)
                    synchronized(persistenceLock) {
                        if (pendingSnapshot.get() != null) persistNormalTabsIfReady()
                    }
                }
            }
        }
    }

    /** Serialize full snapshots and reread the latest state after each write. */
    private suspend fun persistNormalTabs() = persistMutex.withLock {
        persistTabSnapshot(_normalTabs.value.mapIndexed { index, tab -> tab.toEntity(index) })
    }

    private suspend fun persistTabSnapshot(snapshot: List<TabEntity>) {
        database.withTransaction {
            val existingIds = database.tabDao().getNormalTabs().mapTo(mutableSetOf()) { it.id }
            val desiredIds = snapshot.mapTo(mutableSetOf()) { it.id }
            (existingIds - desiredIds).forEach { database.tabDao().deleteById(it) }
            if (snapshot.isNotEmpty()) database.tabDao().replaceAll(snapshot)
            val activeNormalTabId = _normalTabs.value.firstOrNull { it.id == _activeTabId.value }?.id
            if (activeNormalTabId != null) {
                database.settingsDao().put(
                    SettingsEntity(
                        key = ACTIVE_TAB_ID_SETTING,
                        value = activeNormalTabId,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            } else {
                database.settingsDao().get(ACTIVE_TAB_ID_SETTING)?.let {
                    database.settingsDao().put(it.copy(value = snapshot.firstOrNull()?.id.orEmpty(), updatedAt = System.currentTimeMillis()))
                }
            }
        }
    }

    /**
     * A tab left untouched on the previous start page follows a changed homepage; anything the
     * user navigated to keeps its page (and its saved WebView state).
     */
    private fun followChangedStartPage(savedActiveTabId: String?, previousStartPage: String?) {
        val candidate = _normalTabs.value.firstOrNull { it.id == savedActiveTabId } ?: _normalTabs.value.first()
        val target = StartPage.restoredUrl(candidate.url, previousStartPage, startPage) ?: return
        _normalTabs.value = _normalTabs.value.map { tab ->
            if (tab.id == candidate.id) tab.copy(url = target, title = target, isLoading = true, webViewState = null) else tab
        }
    }

    private fun newTab(url: String = startPage, isPrivate: Boolean): Tab = Tab(
        id = UUID.randomUUID().toString(),
        url = url,
        title = "New tab",
        isPrivate = isPrivate,
    )

    private fun Tab.toEntity(position: Int) = TabEntity(
        id = id,
        url = url,
        title = title,
        favicon = favicon,
        position = position,
        isPrivate = false,
        webViewState = webViewState?.let(::marshallBundle),
    )

    private fun TabEntity.toTab() = Tab(
        id = id,
        url = url,
        title = title,
        favicon = favicon,
        isPrivate = false,
        webViewState = webViewState?.let(::unmarshallBundle),
    )

    private fun marshallBundle(bundle: Bundle): ByteArray {
        val parcel = Parcel.obtain()
        return try {
            bundle.writeToParcel(parcel, 0)
            parcel.marshall()
        } finally {
            parcel.recycle()
        }
    }

    private fun unmarshallBundle(bytes: ByteArray): Bundle? = try {
        val parcel = Parcel.obtain()
        try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            Bundle.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    } catch (_: RuntimeException) {
        null
    }

    private fun copyBundle(bundle: Bundle): Bundle = Bundle(bundle)

    companion object {
        private const val ACTIVE_TAB_ID_SETTING = "tabs.active.normal"
        /** Homepage that was in effect when this session's start tab was opened. */
        private const val LAST_START_PAGE_SETTING = "startup.lastStartPage"
    }
}
