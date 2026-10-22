package com.aero.browser

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModelProvider
import com.aero.browser.features.tabs.TabManagerViewModel
import dagger.hilt.android.AndroidEntryPoint
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aero.browser.features.browser.ui.BrowserScreen
import com.aero.browser.ui.theme.AeroTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var incomingRequestId by mutableIntStateOf(0)
    private var sharedUrl by mutableStateOf<String?>(null)

    private val tabManager by lazy { ViewModelProvider(this)[TabManagerViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        createPrivateTabsNotificationChannel()
        if (intent?.action == ACTION_CLOSE_PRIVATE_TABS) {
            tabManager.closeAllPrivateTabs()
            finish()
            return
        }
        refreshPrivateTabsNotification()
        applyIncomingIntent(intent)
        enableEdgeToEdge()
        setContent {
            var notificationPermissionRequested by remember { mutableStateOf(false) }
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { refreshPrivateTabsNotification() }
            LaunchedEffect(tabManager) {
                tabManager.privateTabs.collect { tabs ->
                    if (tabs.isNotEmpty() && !notificationPermissionRequested &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionRequested = true
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    refreshPrivateTabsNotification()
                }
            }
            AeroTheme {
                BrowserScreen(
                    sharedUrl = sharedUrl,
                    sharedUrlRequestId = incomingRequestId,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_CLOSE_PRIVATE_TABS) {
            tabManager.closeAllPrivateTabs()
            refreshPrivateTabsNotification()
            return
        }
        applyIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshPrivateTabsNotification()
    }

    private fun createPrivateTabsNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PRIVATE_TABS_CHANNEL,
                "Private tabs",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "A quick action for closing open private tabs." }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun refreshPrivateTabsNotification() {
        if (!tabManager.hasPrivateTabs()) {
            NotificationManagerCompat.from(this).cancel(PRIVATE_TABS_NOTIFICATION_ID)
            return
        }
        val closeIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_CLOSE_PRIVATE_TABS
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val closePendingIntent = PendingIntent.getActivity(
            this,
            CLOSE_PRIVATE_TABS_REQUEST_CODE,
            closeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, PRIVATE_TABS_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Private tabs are open")
            .setContentText("Tap to close all private tabs now")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(closePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close private tabs", closePendingIntent)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(PRIVATE_TABS_NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val ACTION_CLOSE_PRIVATE_TABS = "com.aero.browser.action.CLOSE_PRIVATE_TABS"
        const val PRIVATE_TABS_CHANNEL = "private_tabs"
        const val PRIVATE_TABS_NOTIFICATION_ID = 7001
        const val CLOSE_PRIVATE_TABS_REQUEST_CODE = 7002
    }

    private fun applyIncomingIntent(intent: Intent) {
        sharedUrl = intent.extractBrowserUrl()
        incomingRequestId++
    }
}

private fun Intent.extractBrowserUrl(): String? {
    val raw = when (action) {
        Intent.ACTION_VIEW -> dataString
        Intent.ACTION_SEND -> getStringExtra(Intent.EXTRA_TEXT)
        else -> null
    }?.trim()?.takeIf(String::isNotBlank) ?: return null
    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
    return raw.takeIf { uri.scheme.equals("http", true) || uri.scheme.equals("https", true) }
}
