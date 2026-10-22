# Aero → Android Port — Phase 1 Analysis Report

Source analyzed: [`ashkanr65/Aero`](https://github.com/ashkanr65/Aero) — "Aero Browser 1.0.1", an Electron (Chromium 132-class) desktop privacy browser.
Stack: Electron 44 + Node native addons (Neon/Rust), React 18 + Tailwind + Fluent UI renderer, Rust `adblock-rs` engine, AES-GCM password vault with Windows Hello/DPAPI.
Target: native Android APK — Kotlin + Jetpack Compose + Material 3 + Hilt + Room.

## 1. User Features

### Tabs & windows (source: `src/main/window-manager.ts`)
- Tab lifecycle: create, activate, close, duplicate, reopen recently closed (20-entry stack), reorder by drag, cycle next/previous, jump to tab 1–9, tab search.
- **60-tab hard limit** with user-facing warning.
- Tab state: title, URL, favicon, loading flag, audible flag, muted flag, discarded (asleep) flag, per-tab privacy stats.
- Tabs either across the **top** or in a collapsible **left vertical rail** (icon-only mode); user-configurable.
- **Split-screen view engine**: left/right panes showing two live pages, draggable divider (20–80% ratio), swap panes, move a tab to either pane, pane activation by click; background tabs can be restored into a pane.
- Private windows (separated session partition, `partition`-flagged contexts) — no vault operations, no credential capture in private mode.
- Per-tab **mute/unmute** with a speaker indicator; audio state polled every 0.5 s (covers Web Audio).
- Sleeping tabs / **Aero RAM Manager**: background tabs auto-discarded after 5/15/30/60 min; discarded tabs release their renderer and reload on selection; "discard other tabs"; efficiency mode (background throttling).
- Per-tab zoom 50–200% (global default in settings).

### Navigation & omnibox (`App.tsx`, `normalizeAddress`)
- Address bar accepts URL or search query; first click selects all; **Ctrl+Enter** expands single word to `www.<word>.com`.
- Smart URL normalization: scheme-permitted schemes pass through (`view-source:`, `file:`, `data:`, `about:`), localhost/IP gets `http://`, dot-hosts get `https://`, everything else goes to the search engine.
- Search engines: DuckDuckGo (default), Brave, Bing, Google, Ecosia; optional local address suggestions from favorites + history.
- Back/forward/stop/reload/hard-reload (ignore cache)/home; paste-and-go; paste without formatting; open current address in a new tab.

### Data panels
- **History** (latest-first, 1000-entry cap, `visitedAt` timestamps), **Downloads** (progressing/completed/cancelled/interrupted with byte counts), **Favorites** (bookmarks + favorites bar toggle + "save all tabs as favorites"), tab search, find-in-page (next/previous), clear-data fire panel.
- Address suggestions merge favorites and local history.

### Reader / accessibility / extras
- **Reader mode** (F9): strips nav/aside/forms, selects `article|main|body`, serif style with dark-mode support.
- **Read-aloud** (Ctrl+Shift+U) via speech synthesis; capture page to PNG (Ctrl+Shift+S); save page as HTML (Ctrl+S); print (Ctrl+P); open local file; view source; caret browsing (F7); full screen (F11); DevTools detach (Ctrl+Shift+I).
- Sidebar (**Aero Hub**, right, resizable 230–500px): Shields/privacy panel, private notes (device-local), quick tools (calculator, speed-test link).
- Fire button: one-click clears history, cookies, cache, and site storage; also "clear history and reset" resets per-tab stats and recently-closed.
- Per-site **shield controls**: standard/aggressive/disabled blocking mode + per-site script blocking toggle.
- Browser **import** from Edge/Chrome/Firefox/Opera: bookmarks, history, open tabs, passwords (CSV export parsed locally, ≤5000 entries, HTTPS-only origins).

### Password manager (`docs/PASSWORD_MANAGER.md`, `native/src/password_vault.rs`)
- Isolated, starts locked; unlock/reset via platform biometric (Windows Hello → Android equivalent: BiometricPrompt + Keystore).
- Add/save/update (same origin+username dedupes), edit, delete; each op gated by fresh biometric auth.
- Reveal (30 s then re-mask), copy (clipboard cleared after 30 s), fill (only into visible same-origin HTTPS form fields; no hidden/offscreen/covered/cross-origin fills; **no automatic background autofill**).
- Vault: AES-256-GCM, random key wrapped by OS user key (DPAPI → Android Keystore), atomic flushed temp-file writes, fail-closed on corruption.
- Idle lock at 5 min, lock on minimize/os-lock/suspend; masked metadata display; CSV import.
- Deliberate omissions to preserve: no cloud sync, no plaintext export, no automatic extraction of other browsers' password DBs.

### Settings surface (12 sections, `src/shared/settings.ts`)
- Appearance: theme system/light/dark, vertical tabs, compact tabs, sidebar open, favorites bar, home button, Aero identity branding, disable GPU.
- Startup: home page (HTTPS-validated), open home on startup.
- Search: engine choice, suggestions on/off.
- Privacy: tracking level strict/balanced/off, block 3rd-party cookies, HTTPS upgrades, GPC, DNT, fingerprint protection, clear-on-exit.
- Shields (per-category toggles): scripts, ad networks, analytics, session replay, error monitoring, banner ads, YouTube video ads, hide ad elements (cosmetic).
- Downloads: directory, ask-where-to-save.
- Performance: sleeping tabs, sleep-after minutes (5/15/30/60), efficiency mode.
- Content: spellcheck. Permissions: camera, microphone, geolocation, notifications, fullscreen, clipboard (per-permission defaults listed in §2).
- Accessibility: page zoom 50–200% snapped to 10%. Layout: sidebar widths (left 180–400, right 230–500). Tabs: location top/left, tab groups flag.
- Shortcuts: fully remappable mapping with reset-to-default.

## 2. Security Mechanisms

| Area | Desktop implementation | Android port target |
|---|---|---|
| Telemetry | All Electron/Chromium telemetry disabled: `disable-breakpad`, `disable-background-networking`, `no-report-upload`, `disable-component-update`, `disable-domain-reliability`, `disable-field-trial-config` | Zero Google/Play telemetry; no reporting libs |
| HTTPS enforcement | `http://` requests (except localhost/127.0.0.1) redirect to `https://`; counter `httpsUpgrades` | `usesCleartextTraffic=false` + request interception/disable for `http://` (except localhost) |
| HSTS / smarter encryption | DuckDuckGo-style automatic HTTPS upgrades | HTTPS-only toggle in network config |
| Headers | Injects `Sec-GPC: 1` and `DNT: 1` on all requests when enabled | Custom header injection |
| Cookie isolation | Third-party `Cookie` request headers stripped + `Set-Cookie` response headers stripped when referrer/host are not same-site; Referer truncated to origin | Third-party cookie block + policy_treat_as_cross_origin |
| Fingerprinting | Injected script at dom-ready: GPC property always `true`; canvas `toDataURL` 1-bit pixel perturbation; WebGL vendor/renderer spoofed ("Aero Graphics"/"Generic Renderer"); audio fingerprinting randomized | Between-pages script injection after each navigation |
| Ad blocking | Rust `adblock-rs` (`Engine`, EasyList-style filters) + deterministic JS fallback (`AD_HOSTS`, `ANALYTICS_HOSTS`, `SESSION_REPLAY_HOSTS`, `ERROR_MONITORING_HOSTS`, aggressive regex patterns); optional Ghostery subscription filter engine | Per-request shouldInterceptRequest / shouldBlockRequest |
| Cosmetic filtering | dom-ready + MutationObserver script hides ad containers (generic ad selectors + YouTube-specific `ytd-*-renderer` rules); auto-skip/close YouTube ads when player shows ad | Same selectors via local JS injection |
| Shield modes | Per-site `standard|aggressive|disabled`; aggressive matches `/ads/, /tracking/, /sponsor/ …`, cookie-consent patterns; global default derived from tracking level (strict→aggressive, balanced→standard, off→disabled) | Per-site shield state |
| Script blocking | Per-site toggle (off by default) | Existing rule |
| Permission gating | Explicit allow-list: webpages can never read the clipboard (`clipboard-read` false), display-capture always denied, camera/mic/geolocation/notifications/fullscreen/clipboard-write per settings; everything else denied by default | WebView `onPermissionRequest` handler |
| Renderer sandbox | Per-`BrowserView`: `sandbox: true`, `nodeIntegration: false`, `contextIsolation: true` | WebView out-of-process, multi-process by default |
| WebContents isolation | Each tab = separate BrowserView; preset listeners bound per view; limit 60 tabs max listeners raised accordingly | WebView pooling model |
| User-agent | Chromium UA with Electron tokens stripped + `Aero/1.0` token | Custom header override in WebView |
| WebRTC | `default_public_interface_only` (no local IP leak) | WebRTC policy |
| Module uninstall prevention | N/A desktop (Android-specific) | N/A |
| Search suggestions dedup | Suggestion overlay fresh-window validated against shell URL | N/A |
| Loader UX | Offline/error pages for common navigation failures | N/A |
| Window user plane | shell URL validation on `contextFor` (IPC sender must be main frame at expected URL) | No cross-origin JS bridge |
| Password vault encryption | AES-256-GCM; random data key encrypted with **current-user DPAPI**; atomic flushed file writes; tampered/malformed → fail closed; no fallback-to-empty | Android Keystore (StrongBox where available) + AES/GCM; EncryptedSharedPreferences/EncryptedFile |
| Password vault auth | Windows Hello per sensitive operation; pending auth revoked on lock | BiometricPrompt (BIOMETRIC_STRONG) per sensitive op |
| Vault timing | Idle-lock 5 min; lock on minimize / OS lock / suspend; clipboard auto-clear 30 s | Same via lifecycle observer + clipboard |
| Credential fill | Confirmed origin+URL+tab+main frame+visible same-origin form before fill; isolated world execution; hidden/offscreen/cross-origin fields rejected; re-validates URL after fill | Content-detail policy before injecting into WebView |
| Network privacy floor | `usesCleartextTraffic` not set by electron; HTTP→HTTPS redirect | Crucial: `android:usesCleartextTraffic="false"` |
| No telemetry regression | none added in Phase 1 | N/A |
| Disk writes | Nothing persisted unless `clear-on-exit` off; explicit clear-data & clear-on-exit honoring | N/A |

Additional notes:
- Downloads recorded in-app with no auto-open execution of executables.
- Accessibility: buttons/panels are all labelled for accessibility (aria-label).
- File-save dialogs are native, over-run by user choice.
- **Bookmarks/favorites and history are validated and deduped** (no duplicate URL entries when adding favorites; 1000-cap in history).
- Desktop module has unit/regression tests (keyboard-shortcuts, browser-regression, Rust tests, 19 live Electron checks) — Android port should mirror with unit + instrumented tests.

## 3. UI/UX Patterns (Edge/Fluent 2 → Material 3 mapping)

- **Layout frame**: custom titlebar (logo + draggable empty area + window buttons) → Material 3 `TopAppBar`; collapsible vertical tab rail (icon-only when collapsed) → `NavigationRail`/custom panel with drag-reorder; dynamic right sidebar (Aero Hub: privacy/notes/tools rail buttons) → persistent right-side drawer/modal drawer; content area hosts one or two (split) live pages.
- **Theme**: system/light/dark driven (`prefers-color-scheme` → Material 3 dynamic color: `dynamicLightColorScheme`/`dynamicDarkColorScheme`), Mica/Acrylic translucency → compose blur/tint surfaces.
- **Toolbar**: omnibox (with shield toggle, history/down/favorites buttons, audio mute indicator, per-tab privacy stats badge), back/forward/refresh/home, favorites bar toggle row.
- **Panels & sheets**: Settings, History, Downloads, Favorites, Clear-data, Import, Tab search implemented as overlay panels/sheets centered under the titlebar, with native webpage views detached while overlay shows → Compose `ModalBottomSheet`/`Dialog`.
- **Interactions**: drag-to-reorder tabs (dragging stops on dragend), drag-to-resize sidebars (min/max captured in settings), Ctrl-based shortcut map with remappable bindings (55+ Edge-compatible actions), first-click select-all in omnibox, double-click reset split ratio, context menu on tabs (open-in-pane, move, mute, discard, duplicate, close).
- **Feedback**: toast/warning on 60-tab limit, alert on GPU restart, per-tab counters (blocked count, https upgrades, bandwidth saved estimate: image 45 KB/media 250 KB/script 32 KB/other 8 KB), speaker icon for audible tabs, private-mode visual state.
- **Off-site visual language**: Fluent corner radii, soft shadows, subtle borders (slate-200/zinc-800), sky-blue accents; Android analog: M3 tonal surfaces, `ColorScheme` with sky-blue seed (#1769aa family), `Surface`, `Cards`.

## 4. Feature → Phase mapping (proposal for later phases)

- Authentication: BiometricPrompt + Keystore-wrapped AES-256-GCM vault.
- Data persistence: Room (tabs snapshot, favorites, history, downloads), settings via DataStore (mirrors `settings.json` normalization contract).
- Adblock/privacy: WebView `shouldInterceptRequest` + dotnet-style filter lists + cosmetic-filter JS injection post-render.
- Split view / vertical tabs: Jetpack Compose layout engines; Chrome-style tabs remain a requirement (max 60 tabs, sleeping tabs, discard/restore).
- Omnibox/search: same normalization + `searchUrl` contract.
- Testing: port the desktop shortcut matcher and normalizeAddress/searchUrl unit tests; add instrumented smoke checks.

## 5. Risks / gaps to resolve in later phases

1. **60-tab limit + Toolbar/overlapping BrowserView model** has no direct Android target; Android WebView only guarantees 1 active WebView per layout slot; Compose `AndroidView(WebView)` per tab + `saveState/restoreState` for sleeping tabs is the likely pattern; "discard" ≈ `destroy() + saveState`, restore ≈ new WebView + `loadUrl`; verify listener limits and max webviews on device-class targets before Phase 2+ commits to an approach.
2. Ghostery `@ghostery/adblocker-electron` subscription engine has no Android equivalent; Phase 2+ should pick either local filter lists shipped in assets or a maintained Android adblock library.
3. Desktop "always-on telemetry disabled" flags do not exist on Android; verify no analytics sink lands anywhere.
4. Battery/efficiency-mode translate loosely (`setBackgroundThrottling`); benchmark on-device with lifecycle-aware WebView throttling scaled to sleeping-tabs thresholds.
5. Windows-native WinRT/DPAPI code has no Android counterpart; Android Keystore + BiometricPrompt is the 1:1 mapping, but fail-closed behaviors and idle-lock timing need on-device validation (5-min idle-lock contract is specific).
6. Renderer-isolation guarantee (sandboxed BrowserView, no nodeIntegration) maps to WebView but the PC-level "no cross-world JS execution" requirement maps differently; script injection policy needs security review in Phase 2.
