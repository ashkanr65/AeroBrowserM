# Aero Browser for Android

Aero is a privacy-focused Android browser built with Kotlin, Jetpack Compose, the latest stable Material 3, and Android WebView. It supports Android 8.0+ (API 26), adapts its browser chrome to compact and larger window widths, compiles against API 37, and currently targets API 35.

## Project overview

The app includes multi-tab browsing, private tabs, a tab overview, bookmarks, history, downloads, configurable search and homepage, HTTPS-only mode, tracking protection, browser-scoped DNS over HTTPS, theme/dynamic color settings, HTTP(S) link handling, and shared-URL intents. On wider windows it uses a vertical tab/navigation rail. It also provides offline retry and renderer recovery screens.

Settings → Homepage is the page Aero opens by itself: a cold start with no session to restore, a tab left untouched on the previous start page when the homepage changed, the New tab button, and the replacement tab after the last tab is closed. Pages you actually navigated to are restored as they were. Invalid values (anything that is not http/https) fall back to `https://duckduckgo.com/`.

Long-pressing a link opens Aero's own context menu: open in a new tab, open in a private tab, copy the link, or share it. Long-pressing an image adds open/copy/download image actions, and an image that sits inside a link shows both sets.

## Build and run

Requirements: Android SDK API 37, JDK 17 or newer, and the Gradle wrapper. From the repository root:

```bash
./gradlew :app:installDebug
```

### Debug APK

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/AeroMobile.apk` (release builds produce
`AeroMobile-release.apk`).

### Test device (emulator)

The AVD `Aero_Pixel7_API35` (Pixel 7, Android 15, Google APIs, x86_64, 2 GB RAM,
6 GB data, hardware GPU) is installed in the standard AVD location, so it also
appears in Android Studio's Device Manager. Launch it normally:

```powershell
.\Start-Emulator.bat
```

It is a plain emulator you can use for any app; it is not tied to Aero. Install
the current build with `adb install -r app\build\outputs\apk\debug\AeroMobile.apk`.

### Free space without deleting project files

Preview the reusable cleanup included with the build kit:

```bash
node Aero/scripts/clean-build-kit.cjs --dry-run
```

Run it without `--dry-run` to remove regenerable Android and desktop build
outputs, Gradle caches, unpacked installers, and the generated bug-report
extract and installed desktop npm dependencies (reinstall with `npm --prefix
Aero ci` before building the desktop app). It preserves source, Git history,
the Android debug APK when present, the desktop `AeroData` browser profile,
and an emulator PID marker. Build outputs and local caches can be recreated
with the commands below.

### Release APK and Play Store bundle

The release build enables R8 minification/obfuscation and resource shrinking. To sign the build, use a private release keystore and set all four variables in the build environment; never place the key or values in source control:

```bash
export KEYSTORE_PATH="/secure/path/aero-release.jks"
export KEYSTORE_PASSWORD="<your-store-password>"
export KEY_ALIAS="<your-key-alias>"
export KEY_PASSWORD="<your-key-password>"
./gradlew clean
./gradlew assembleRelease
./gradlew bundleRelease
```

Run these commands from the repository root. `KEYSTORE_PATH` may be an absolute path or a path relative to the app module's Gradle project directory. Gradle rejects partially supplied signing credentials. If none are provided, the release tasks produce an **unsigned** APK and an unsigned AAB for local review only—not distributable or Play-ready artifacts.

- Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk` when no signing credentials are configured; `app-release.apk` when signed.
- Play Store AAB: `app/build/outputs/bundle/release/app-release.aab`.
- R8 mapping: `app/build/outputs/mapping/release/mapping.txt` (retain privately for each distributed release to symbolicate crash reports).

Use a secret manager or CI environment variables for signing material. The repository ignores `*.jks` and `*.keystore` files. `clean` deletes generated build outputs; it does not delete sources.

## Secure DNS (DNS over HTTPS)

Settings → Privacy → **Secure DNS** sends name lookups for Aero's WebView through an encrypted
DoH resolver (Cloudflare, Google, Quad9, or a custom HTTPS endpoint). It is off by default.

How it stays browser-only: Android WebView has no per-request DNS hook, so Aero starts a
loopback HTTP proxy that resolves every hostname with DoH and registers it with
`androidx.webkit.ProxyController`. That override only affects WebView instances owned by this
app. The system resolver, the system proxy/VPN settings, and other apps are never modified.
HTTPS is forwarded as an opaque `CONNECT` tunnel, so TLS still terminates in the browser and
certificates are validated there; the proxy never sees plaintext.

Limitations to keep in mind:

- The DoH server's own hostname is resolved once by the platform resolver to bootstrap the
enabled connection. Everything else goes through DoH.
- If the DoH endpoint is unreachable, "Fall back to system DNS" (on by default) keeps pages
  loading by falling back to the device resolver. Turn it off for strict privacy: pages then fail
  instead of leaking lookups.
- Secure DNS applies to WebView traffic. Downloads handed to Android's `DownloadManager` use the
  system network stack and system DNS, and WebView-internal services such as Safe Browsing may use
  their own network path rather than the in-app proxy.
- The Settings screen shows live encrypted-lookup and fallback counts so the tunnel's state is
  visible; the local proxy binds an ephemeral loopback port and is not authenticated, so other
  apps on the device can reach it while Aero runs (it only forwards WebView-shaped traffic).

## Security controls

- Manifest blocks cleartext traffic. HTTPS-only setting upgrades or blocks insecure navigation.
- WebView file/content access, mixed content, multiple windows, automatic JavaScript windows, geolocation, and third-party cookies are disabled; Safe Browsing is enabled when supported. There are no `addJavascriptInterface` registrations.
- WebView debugging is configured from `BuildConfig.DEBUG`; this evaluates to `false` in the non-debuggable release variant.
- TLS certificate errors fail closed. Main-frame schemes are restricted and tracker resources are filtered.
- Room database data uses SQLCipher. Its random passphrase is wrapped with an Android Keystore AES-GCM key. Browser settings are in encrypted preferences.
- `android:allowBackup="false"`; cloud backup and device-transfer rules exclude app data as defense in depth.
- Release signing secrets come from environment variables; no signing passwords or keys are hardcoded.
- R8 rules preserve Room reflection entry points, Hilt entry points, and any annotated JavaScript interface methods. This module currently uses no Gson, Moshi, or Kotlin serialization models, so there are no model-specific keep rules.

### Final security audit checklist

- [x] `android:usesCleartextTraffic="false"` is set.
- [x] Release WebView debugging resolves to disabled (`BuildConfig.DEBUG == false`).
- [x] Source scan found no hardcoded API key/password/secret assignments; the database key is randomly generated and Keystore-wrapped.
- [x] The only app component in the manifest is `MainActivity` and it explicitly declares `android:exported="true"` for launcher/link intents.
- [x] `android:allowBackup="false"`; backup/device-transfer rules exclude app databases and preferences.

The HTTP(S) intent filter handles ordinary links and shares. Verified Android App Links additionally require publishing a domain `assetlinks.json` and validating ownership; no domain association is configured here.

## Permissions

| Permission | Purpose |
| --- | --- |
| `android.permission.INTERNET` | Load browser pages, search results, and web resources. |
| `android.permission.ACCESS_NETWORK_STATE` | Detect validated connectivity for the offline/retry UI. |

The app does not request `POST_NOTIFICATIONS`; download notifications are shown by Android's system `DownloadManager`. No storage permission is requested; downloads use the system download manager.

## Quality checks

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Device testing is still recommended for actual downloads, window resizing/folding, external link resolution, and offline/renderer failure recovery.
