# KtorMonitor — plan

An open-source, in-app inspector for the HTTP calls of a Ktor client: Kotlin Multiplatform core
(Android, iOS), native UI on each platform: Compose on Android, SwiftUI on iOS. Published for anyone
to use.

This file records the decisions, the scope and the migration. Work follows it phase by phase.

## Decisions

1. **A new project from scratch.** Not a fork or a module of any app. No history is carried over.
2. **No relation to MavridKids.** No shared code, no shared build logic, no dependency in either
   direction. MavridKids is only the source of the first version of the code, copied by hand and
   cleaned. After that it is a consumer like any other.
3. **Name `ktor-monitor`.** Artifacts `uz.jahonov:ktor-monitor` and `uz.jahonov:ktor-monitor-ui`,
   package `uz.jahonov.ktormonitor` (UI under `uz.jahonov.ktormonitor.ui`), repo
   https://github.com/JahonovAsilbek/Ktor-Monitor.
4. **UI on Compose foundation only.** No MKBUICore, no Material/Material3, no custom design system.
   Screens are built from what foundation gives (`BasicText`, `BasicTextField`, `Box`/`Row`/`Column`,
   `LazyColumn`, `HorizontalPager`, `clickable`, `Canvas`). The only styling is a small internal set
   of light and dark colours and text styles.
5. **No DI framework and no logging library.** Koin and Kermit go. The public API is plain Kotlin;
   an app wires it into whatever DI it uses.
6. **Logic shared, UI native.** Capture, storage, export, body analysis and view models are KMP.
   The UI is Compose on Android and SwiftUI on iOS. Both UIs are part of v1 and show the same
   screens with the same behaviour.

## Scope

### v1 (in)

- Capture: one record per network attempt (refresh and retry as separate rows), streamed bodies,
  server-sent events, truncation, `filter`, header sanitising, JSON body field redaction. A failure
  inside the monitor never touches the app's call.
- Storage: Room KMP with bundled SQLite, retention and max-call limits, delete and clear.
- List: search (URL, method, status, bodies), filters, sort, only-errors, selection, delete, share.
- Detail: Summary, Request, Response; body views Stream → Preview → Code → Text → Hex.
- Export: cURL, wget, text, Markdown, JSON, HAR, URL list.
- Android: activity, notification with the latest calls, shake to open, sharing via FileProvider.
- iOS: SwiftUI list and detail, local notification with the latest calls, shake to open, share sheet.
- Sample apps, Android and iOS, that make real calls against a public API.
- All existing unit tests carried over and green.

### Out of v1

- A `-no-op` artifact.
- Publishing to Maven Central (planned for after v1 works locally; see Phase 5).
- Compose Multiplatform UI.

## Architecture

| Module | Kind | Contents |
|---|---|---|
| `:ktor-monitor` | KMP library (android, iosArm64, iosSimulatorArm64) | `capture`, `data`, `body`, `export`, `model`, `presentation` |
| `:ktor-monitor-ui` | Android library | Compose screens, activity, notification, shake, sharing |
| `:sample` | Android app | Ktor client + the monitor, a few buttons that make calls |
| `:sample-shared` | KMP library | The sample calls both apps make; on iOS the `SampleShared` framework |
| `ios/KtorMonitorUI` | Swift package (SPM) | SwiftUI screens, notification, shake, share sheet. `Package.swift` sits at the repo root (SPM resolves packages only there) and points to these sources |
| `ios/Sample` | Xcode app | The iOS sample: a small KMP framework with the client + the monitor, and the Swift package |

Public entry point (shape, to be settled in Phase 2):

```kotlin
val monitor = KtorMonitor(context /* Android */) {
    retention = Retention.OneHour
    sanitizeHeaders("Authorization")
}
monitor.attach(httpClient)          // installs the HttpSend interceptor
KtorMonitorUi.install(application, monitor)  // notification + shake (ui module)
```

`attach` is called after any auth that retries requests, so every attempt is seen with its final
headers — the same rule MavridKids enforced through `HttpClientExtension`.

### iOS

**The constraint.** A KMP library does not reach iOS as a framework of its own. Each app links it
into its own umbrella framework (`Shared`, `SharedLogic`, …), with its own name and its own copy of
the Kotlin runtime. So:

- the monitor must live in the app's framework, next to the app's `HttpClient` — a second,
  prebuilt framework would have a separate Kotlin runtime and could not see that client;
- a prebuilt Swift package cannot `import` the Kotlin types, because it does not know the
  framework's name.

**The approach.** The SwiftUI package does not import any Kotlin framework. It talks to
the monitor through a narrow bridge of Swift-native types:

- `:ktor-monitor` (`iosMain`) has one facade, `KtorMonitorBridge`: a few methods taking and giving
  `String` and closures — observe the list state, observe a call's state, send an event, export.
  States and events cross as JSON (kotlinx.serialization, already a dependency). No `Flow`, no
  SKIE needed on the app's side.
- `KtorMonitorUI` declares the same methods as a Swift protocol and has `Codable` models for the
  JSON. The app adds one line, `extension KtorMonitorBridge: KtorMonitorUIBridge {}`, and hands it
  to the UI.
- The overhead of JSON is irrelevant for a debug tool; bodies are already capped at
  `maxContentLength`.

Consumer setup on iOS: add `uz.jahonov:ktor-monitor` to the shared module, `export` it from the
framework, add the `KtorMonitorUI` package in Xcode, write the one-line conformance.

Rejected: Compose Multiplatform on iOS (decision 6); an XCFramework of the monitor (separate
Kotlin runtime, cannot see the app's client); a Swift package that imports a fixed framework name
(works only for one app).

## Migration

Source: MavridKids, branch `feature/network-monitor`, commit `2801142`.
~5,200 lines of main code, ~3,000 lines of tests.

### Where the code goes

| From (MavridKids) | To |
|---|---|
| `shared/debug/netmonitor` (commonMain 34, androidMain 2, iosMain 3 files) | `:ktor-monitor` |
| its tests (commonTest 14, androidHostTest 1, iosTest 1) | `:ktor-monitor` tests |
| `android/debug/netmonitor` (25 files + manifest + `res/xml`) | `:ktor-monitor-ui` |
| `docs/NETWORK_MONITOR.md` (behaviour sections only) | `README.md` + `docs/` |
| `docs/NETWORK_MONITOR.md`, "For the iOS developer" | spec for the SwiftUI screens; no Swift code exists yet |
| `sharedLogic/src/iosDevOnly/DebugTools.kt` (Swift entry points) | replaced by `KtorMonitorBridge` |
| `uz.mkb.mavridkids.debug.netmonitor.*` | `uz.jahonov.ktormonitor.*` |
| `uz.mkb.mavridkids.android.debug.netmonitor.*` | `uz.jahonov.ktormonitor.ui.*` |

Not carried over: MavridKids' `HttpClientExtension`, `DebugTools` twins, prod-exclusion checks,
`sharedLogic` and Xcode changes. They stay app concerns.

### Dependencies to cut

| MavridKids dependency | Replacement |
|---|---|
| `core.network.HttpClientExtension` | `KtorMonitor.attach(client)` |
| `core.mvi.MviViewModel`, `Loadable` | small internal equivalents in `:ktor-monitor` |
| `core.platform.AppInfo`, `EXPORT_APP_NAME` | app name and version read from the platform, overridable in config |
| `core.storage.SettingsStore` + `isDark()` | follow the system theme |
| `android.core.ui` (`AppBottomSheet`, `CollectEffects`) | simple foundation versions inside `:ktor-monitor-ui` |
| MKBUICore (`MkbTheme`, `Brand`, `Button`, `CircleButton`, `Toolbar`, `TabRow`, `Input`, `ListItem`, `Loader`, `MkbIcons`) — 19 UI files | foundation composables + internal colours; icons as a few vector drawables or text glyphs |
| Koin (`netMonitorModule`, `KoinPlatform.getKoin()` in UI) | constructor wiring; the UI gets view models from the `KtorMonitor` instance |
| Kermit | none; internal failures are dropped silently or passed to an optional callback |
| `build-logic` convention plugins, `libs.versions.toml` entries | the project's own Gradle setup |

### Cleaning

- Replace every real domain, path and app name in previews, samples and tests with neutral
  examples (`api.example.com`, `ExampleApp`).
- Remove MavridKids-specific wording from KDoc and comments.

### Known risks

- `ResponseAdapterAttributeKey` and `@InternalAPI` (SSE capture) are Ktor internals and may change
  in a minor release. Declare the supported Ktor range and test against it.
- Kotlin, Ktor, Room and Compose versions pass on to users; keep the minimums as low as the code
  allows.
- The JSON bridge is a second API to keep in step: a Kotlin state change must reach the Swift
  models. Both sides get tests over the same JSON fixtures.
- Kotlin/Native exports the bridge under the consumer's framework; its Swift name must not clash
  with app types. The name is checked in the iOS sample.

## Phases

Each phase ends with a build and the tests, and waits for approval before the next.

0. **Project opened.** This file. ✅
1. **Gradle skeleton.** Wrapper, version catalog, the three modules empty, builds on Android and iOS
   targets. ✅
2. **Core.** Copy `shared/debug/netmonitor`, rename packages, cut core/Koin/Kermit, settle the public
   API, all tests green. ✅
3. **Android UI.** Copy `android/debug/netmonitor`, rename, replace MKBUICore with foundation
   composables, clean the previews. ✅
4. **iOS bridge.** `KtorMonitorBridge` in `iosMain`, the JSON state and event models, tests. ✅
5. **iOS UI.** `ios/KtorMonitorUI` Swift package: list, detail, body views, notification, shake,
   share sheet — the same screens as Android. ✅
6. **Samples + docs.** Android and iOS sample apps on devices, `README.md`, licence. ✅
7. **Publishing** (separate decision): GitHub repo, Maven Central, the Swift package by git tag,
   CI on macOS.
8. **MavridKids switch** (in MavridKids, separately): depend on the library, drop its own modules.

## Settled

- **Maven groupId `uz.jahonov`.** The domain `jahonov.uz` is owned (served from GitHub Pages), so
  Maven Central verification goes through a DNS TXT record.
- **Licence Apache-2.0.**
- **minSdk 24.** For v1 the minimums are the versions in `gradle/libs.versions.toml` (Kotlin 2.4.20,
  Ktor 3.6.0, Room 2.8.5, Compose BOM 2026.02.01). Lowering them is revisited before publishing.
- **Name `ktor-monitor`**, kept although another library uses the same name
  (`ro.cosminmihu.ktor:ktor-monitor`). The Maven coordinates differ by groupId, so there is no clash
  in dependency resolution; the README names the groupId prominently.
- **iOS UI through a JSON bridge** (see Architecture → iOS).
