# Release Notes

## Version 1.0.0 — First Release

### Overview

First public release of **Aero Browser for Android**, a privacy-focused Android
browser built with Kotlin, Jetpack Compose, Material 3, and Android WebView.
Supports Android 8.0+ (API 26), compiles against API 37, and targets API 35.

This is the initial release of the Android port of the Aero desktop browser. The
port design notes are in [docs/ANDROID_PORT_ANALYSIS.md](docs/ANDROID_PORT_ANALYSIS.md).

### Browsing

- Multi-tab browsing with **normal tabs** and **private tabs** (private tabs are ephemeral and are never persisted)
- Tab overview with a search field, per-mode filters, close-all, and add-tab
- Vertical tab/navigation rail on wide windows, collapsible to icon-only
- Address bar that accepts a URL or a search term, with scheme and host normalization
- Back / forward / reload, homepage button, new tab, and new private tab
- Find in page, desktop-site request, and share
- Long-press a link or image for Aero's own context menu (open in a new tab, open in a private tab, copy, share, download image)
- HTTP(S) link handling and shared-URL intents
- Offline retry and renderer-recovery screens

### Data

- **Bookmarks** with a dedicated screen
- **History** with browsing-data clearing over Today, Last week, Last month, or Full
- **Downloads** handed to the system `DownloadManager`
- **Clear browsing data** panel covering history, cookies, cache, and site storage

### Privacy and security

- **Tracking protection** at three levels — Off, Balanced, Strict — backed by a host filter, plus optional blocking of banner-like images
- **Block ads and trackers**, **Block banner-like images**
- **Block third-party cookies**
- **Block WebRTC**
- **Do Not Track** signal
- **Safe Browsing** (Android WebView's supported threat protection)
- **Fingerprinting protection** (canvas / WebGL / audio randomization)
- **HTTPS-only mode** to upgrade insecure connections
- **Secure DNS (DNS over HTTPS)** scoped to Aero's own WebView: a loopback proxy resolves names over DoH and is registered with `androidx.webkit.ProxyController`, so the system resolver, system proxy/VPN settings, and other apps are never modified. Choose Cloudflare, Google, Quad9, or a custom HTTPS endpoint, with an optional fallback to system DNS
- **Site permissions** for camera, microphone, geolocation, and notifications, reviewable and revocable from Settings
- Per-site and global toggles for **JavaScript**, **pop-up windows**, **autoplay media**, and **load images**
- **DOM storage** toggle
- Cleartext traffic is blocked (`usesCleartextTraffic="false"`); no `addJavascriptInterface` registrations are used
- TLS certificate errors fail closed

### Appearance and search

- Theme: **System / Light / Dark**, with Material You **dynamic color**
- **Smooth transitions** toggle
- **Page zoom**
- Search engine: **DuckDuckGo** (default), Brave, Google, or Ecosia
- Configurable **homepage**; invalid values fall back to `https://duckduckgo.com/`

### Local data protection

- Room database is encrypted with **SQLCipher**; its random passphrase is wrapped with an Android Keystore AES-GCM key
- Browser settings are held in **encrypted preferences**
- `android:allowBackup="false"`; backup and device-transfer rules exclude app data
- Release builds enable R8 minification and shrink resources, and disable WebView debugging
- Release signing credentials are read from environment variables only — no keys or passwords are stored in the repository

### Technical

- Kotlin, Jetpack Compose, Material 3, Hilt, Room, Coroutines
- Minimum SDK 26, target SDK 35, compile SDK 37
- JVM unit tests and instrumented tests (`./gradlew :app:testDebugUnitTest`)

### Not included in this release

These exist in the Aero desktop browser but are **not** part of the Android
1.0.0 release:

- Password manager with biometric unlock
- Global Privacy Control (GPC) request header
- Reader mode and read-aloud
- Split-screen view
- Sleeping tabs and the RAM manager
- Desktop-only chrome: custom titlebar, Aero Hub sidebar, and favorites bar

### Known issues

None reported for this release.

### Upgrade notes

This is the first release, so there is no upgrade path from a previous version.

---

*Aero Browser for Android — privacy-first browsing with enterprise-grade ergonomics.*
