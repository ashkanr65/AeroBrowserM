package com.aero.browser

import androidx.room.Room
import com.aero.browser.core.storage.AppDatabase
import com.aero.browser.core.storage.HomepageSource
import com.aero.browser.features.tabs.TabManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TabManagerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun newViewModel(homepage: String = "https://duckduckgo.com/") =
        TabManagerViewModel(database, HomepageSource { homepage })

    @Test
    fun coldStartOpensTheConfiguredHomepage() = runTest(dispatcher) {
        val viewModel = newViewModel("https://example.com/start")
        viewModel.awaitDatabaseInitialization()
        advanceUntilIdle()

        assertEquals("https://example.com/start", viewModel.normalTabs.value.single().url)
        assertEquals("https://example.com/start", viewModel.startPage())
    }

    @Test
    fun changedHomepageReplacesThePreviousStartPageButKeepsBrowsedPages() = runTest(dispatcher) {
        val firstLaunch = newViewModel("https://duckduckgo.com/")
        firstLaunch.awaitDatabaseInitialization()
        advanceUntilIdle()
        firstLaunch.awaitPendingPersistence()

        val secondLaunch = newViewModel("https://example.com/")
        secondLaunch.awaitDatabaseInitialization()
        advanceUntilIdle()
        assertEquals("https://example.com/", secondLaunch.normalTabs.value.single().url)
        secondLaunch.awaitPendingPersistence()

        secondLaunch.updatePage(secondLaunch.normalTabs.value.single().id, "https://iana.org/", "IANA", isLoading = false)
        secondLaunch.awaitPendingPersistence()

        val thirdLaunch = newViewModel("https://example.net/")
        thirdLaunch.awaitDatabaseInitialization()
        advanceUntilIdle()
        assertEquals("https://iana.org/", thirdLaunch.normalTabs.value.single().url)
        thirdLaunch.awaitPendingPersistence()
    }

    @Test
    fun createsAndPersistsNormalTabsButKeepsPrivateTabsEphemeral() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.awaitDatabaseInitialization()
        advanceUntilIdle()
        val first = viewModel.normalTabs.value.single()
        val secondId = viewModel.createTab("https://example.org/")
        advanceUntilIdle()
        viewModel.awaitPendingPersistence()

        assertEquals(2, viewModel.normalTabs.value.size)
        assertEquals(2, database.tabDao().getNormalTabs().size)
        assertEquals(secondId, viewModel.activeTabId.value)

        viewModel.togglePrivateMode()
        val privateId = viewModel.createTab("https://private.example/")
        viewModel.saveTabState(privateId, android.os.Bundle().apply { putInt("position", 12) })

        assertEquals(2, viewModel.privateTabs.value.size)
        assertTrue(viewModel.privateTabs.value.all { it.isPrivate })
        assertTrue(viewModel.privateTabs.value.any { it.id == privateId })
        assertEquals(2, database.tabDao().getNormalTabs().size)
        assertNotEquals(first.id, privateId)
        assertFalse(viewModel.privateTabs.value.first { it.id == privateId }.webViewState?.containsKey("position") ?: false)
        viewModel.awaitPendingPersistence()
    }

    @Test
    fun closingActiveTabSelectsNeighborAndLastTabGetsReplacement() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.awaitDatabaseInitialization()
        advanceUntilIdle()
        val first = viewModel.normalTabs.value.single()
        val second = viewModel.createTab("https://second.example/")
        viewModel.closeTab(second)
        assertEquals(first.id, viewModel.activeTabId.value)
        advanceUntilIdle()
        viewModel.awaitPendingPersistence()
        viewModel.closeTab(first.id)
        advanceUntilIdle()
        viewModel.awaitPendingPersistence()
        assertEquals(1, viewModel.normalTabs.value.size)
        assertNotEquals(first.id, viewModel.activeTabId.value)
    }

    @Test
    fun rapidModeSwitchesKeepActiveTabInTheSelectedMode() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.awaitDatabaseInitialization()
        advanceUntilIdle()
        val normalId = viewModel.activeTabId.value

        repeat(12) { index ->
            viewModel.setPrivateMode(true)
            assertTrue(viewModel.activeTab()?.isPrivate == true)
            val privateId = viewModel.activeTabId.value
            viewModel.createTab("https://private-$index.example/")
            viewModel.setPrivateMode(false)
            assertTrue(viewModel.activeTab()?.isPrivate == false)
            assertEquals(normalId, viewModel.activeTabId.value)
            viewModel.setPrivateMode(true)
            assertEquals(privateId, viewModel.activeTabId.value)
            viewModel.setPrivateMode(false)
            assertTrue(viewModel.activeTab()?.isPrivate == false)
        }
        assertEquals(13, viewModel.privateTabs.value.size)
        assertEquals(1, viewModel.normalTabs.value.size)
        viewModel.awaitPendingPersistence()
    }

    @Test
    fun closeAllPrivateTabsPreservesNormalTabsAndSelectsNormalTab() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.awaitDatabaseInitialization()
        advanceUntilIdle()
        val normalId = viewModel.activeTabId.value
        viewModel.setPrivateMode(true)
        viewModel.createTab("https://private.example/")

        viewModel.closeAllPrivateTabs()
        advanceUntilIdle()

        assertTrue(viewModel.privateTabs.value.isEmpty())
        assertEquals(1, viewModel.normalTabs.value.size)
        assertEquals(normalId, viewModel.activeTabId.value)
        assertFalse(viewModel.isPrivateMode.value)
        viewModel.awaitPendingPersistence()
    }

    @Test
    fun togglesOverviewAndPrivateMode() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.awaitDatabaseInitialization()
        advanceUntilIdle()
        viewModel.toggleTabOverview()
        assertTrue(viewModel.isTabOverviewVisible.value)
        viewModel.togglePrivateMode()
        assertTrue(viewModel.isPrivateMode.value)
        assertFalse(viewModel.isTabOverviewVisible.value)
        assertTrue(viewModel.activeTab()?.isPrivate == true)
        viewModel.awaitPendingPersistence()
    }
}
