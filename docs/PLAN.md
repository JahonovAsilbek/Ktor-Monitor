# Ktor Monitor — design and plan

An open-source, in-app inspector for the HTTP calls of a Ktor client: a Kotlin Multiplatform core
(Android, iOS) with native UI on each platform, Compose on Android and SwiftUI on iOS.

This file records the decisions behind the library, how it is put together, and what is left before
and after the first release. How to use it is in the [README](../README.md).

## Decisions

1. **Logic shared, UI native.** Capture, storage, export, body analysis and the screens' view models
   are Kotlin Multiplatform. Android draws them with Compose, iOS with SwiftUI; both show the same
   screens with the same behaviour.
2. **Compose foundation only.** No Material, no design system: the screens are built from what
   foundation gives, with a small set of light and dark colours and text styles of the monitor's
   own. An app's theme never shows through, and the library brings no Material to an app.
3. **No DI framework and no logging library.** The API is plain Kotlin; an app wires the monitor
   into whatever it uses. Failures of the monitor itself go to `onInternalError`.
4. **The monitor never hurts the app.** No failure inside it (the database, a parser, the bridge)
   fails, delays or changes the app's call, or crashes the app.
5. **Nothing is redacted by default.** It is a debug tool, pointed at development backends, where
   the tokens are what a developer wants to see. Headers, query parameters and JSON or form fields
   can be redacted when needed.
6. **Debug builds only.** The library records everything; the README shows how to keep it out of
   release builds on both platforms. A no-op artifact is not planned for v1.
7. **Names.** Maven group `uz.jahonov` (the `jahonov.uz` domain is verified by a DNS record),
   artifacts `ktor-monitor` and `ktor-monitor-ui`, package `uz.jahonov.ktormonitor`, Swift package
   and product `KtorMonitorUI`. Another library is also called ktor-monitor
   (`ro.cosminmihu.ktor:ktor-monitor`); the group tells them apart.
8. **Licence Apache-2.0.**

## Architecture

| Module | Kind | Contents |
|---|---|---|
| `:ktor-monitor` | KMP library (android, iosArm64, iosSimulatorArm64) | capture, Room history, body analysis, exports, view models, the iOS bridge |
| `:ktor-monitor-ui` | Android library | Compose screens, activity, notification, shake, sharing |
| `ios/KtorMonitorUI` | Swift package | SwiftUI screens, notification, shake, share sheet; `Package.swift` is at the repository root, where SwiftPM looks |
| `:sample`, `:sample-shared`, `ios/Sample` | samples | Android and iOS apps making the same calls |

### Capture

The monitor installs an `HttpSend` interceptor on each client it is attached to. Attached after any
plugin that retries (auth refresh, `HttpRequestRetry`), it records each network attempt with the
headers that went out; the attempts of one request share a group. Bodies are copied as they stream,
without consuming them for the app; server-sent events are copied inside the engine, through its
response adapter. Writes go through one queue; a streaming body is written a few times a second, not
per chunk.

### Storage

Room KMP, one database per process, in the cache directory: the history is disposable. A schema
change starts it afresh instead of migrating; the schemas are exported to `ktor-monitor/schemas`.
Bodies are the last columns, so list queries never read them. Android uses the platform's SQLite,
iOS a bundled one.

### iOS

A KMP library reaches Swift only through the app's own framework, whose name a prebuilt Swift
package cannot know, and the monitor must live in that framework, next to the app's client. So the
SwiftUI package imports no Kotlin at all. It talks to `KtorMonitorBridge` (in `iosMain`) through a
protocol of strings and closures: a session per open screen sends its states and effects as JSON and
takes events as JSON. The app connects the two with one line,
`extension KtorMonitorBridge: @retroactive KtorMonitorUIBridge {}`.

The JSON contract is pinned by fixtures in `ios/KtorMonitorUI/Tests/KtorMonitorUITests/Fixtures`:
the Kotlin tests write them, the Swift tests decode them, and CI fails when they drift. Kinds a newer
Kotlin side sends are skipped by an older Swift side rather than breaking it.

### API

`explicitApi()` is on. What apps use is `KtorMonitor`, `KtorMonitorConfig`, `Retention`,
`KtorMonitorUi` and `KtorMonitorBridge`. The types the UI module needs from the core (states,
events, view models, the models they show) are public but marked `@InternalKtorMonitorApi`, an
opt-in only the monitor's own modules use; they may change in any release. The core's ABI is
recorded in `ktor-monitor/api` and checked in CI.

### Compatibility

The library modules' dependencies are the minimums their users get, so they are kept low:
Kotlin 2.3 language and API level (Ktor 3.6 needs 2.3 anyway), Compose foundation 1.8,
activity-compose 1.10, core 1.13, lifecycle 2.8, minSdk 24, iOS 16. Intel iOS simulators are not
supported.

## Known risks

- Capturing server-sent events relies on Ktor internals (`ResponseAdapterAttributeKey`,
  `replaceResponse`), which may change in a minor Ktor release. CI runs against the Ktor version in
  the catalog; a Ktor upgrade needs the SSE tests to pass.
- Search in bodies runs in SQLite: it ignores letter case for Latin letters only, and does not see
  bodies in encodings other than UTF-8.
- The Swift package and the Kotlin artifacts are versioned together by hand: a release tags the
  repository with the same version as the Maven artifacts.

## Status

Done:

- Core, Android UI, iOS bridge, SwiftUI package, samples, README, licence.
- A pre-release review by twenty reviewers, and its fixes: safety (nothing reaches the app), speed
  under heavy traffic, privacy and exports, the Android screens, the iOS screens, and the build.
- Publishing setup (Maven Central through `com.vanniktech.maven.publish`, signing from CI) and a
  macOS CI workflow running the Kotlin, Swift and sample builds.

Next:

1. **First release.** The namespace is verified and the secrets are set; a `v*` tag runs
   `.github/workflows/release.yml`, which checks the tag against `VERSION_NAME`, tests, publishes to
   Maven Central and makes a GitHub release. `v0.1.0` is the first.
2. **Adopt it in the first app** that uses it, from Maven Central.

Later, when asked for: a no-op artifact, WebSocket frames, request mocking.
