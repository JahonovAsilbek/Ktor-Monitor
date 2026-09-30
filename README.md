# Ktor Monitor

An in-app inspector for the HTTP calls of a [Ktor](https://ktor.io) client, for Kotlin Multiplatform
and Android apps. The logic is shared; the UI is native on each platform: Compose (foundation only,
no Material) on Android and SwiftUI on iOS.

- **Every network attempt** is its own record: a 401 → token refresh → retry shows as three rows,
  each with the headers that actually went out.
- **Bodies** of every kind, streamed ones and server-sent events included, without consuming them
  for the app. JSON, XML, HTML, forms, multipart, CSS, JavaScript, YAML and Markdown are
  highlighted and fold; images and Markdown render; anything else shows as text or hex.
- **History** that survives restarts (Room KMP), with retention and a maximum count.
- **Search** in URLs, methods, status codes and bodies; filters by host, method, content type,
  status class and duration; sorting.
- **Export** as cURL, wget, text, Markdown, JSON, a URL list or **HAR** (opens in Chrome DevTools,
  Charles, Proxyman).
- **Ways in**: a notification with the latest calls and a shake of the device.
- **Redaction** of headers and JSON body fields, when you want it. Nothing is redacted by default.
- A failure inside the monitor never fails, delays or changes the app's call.

The Maven group is `uz.jahonov`. Another library with a similar name exists
(`ro.cosminmihu.ktor:ktor-monitor`); the two are unrelated.

## Setup

> Not on Maven Central yet. Until the first release, build it from this repository
> (`includeBuild` or `./gradlew publishToMavenLocal`).

### Android

```kotlin
// build.gradle.kts
dependencies {
    implementation("uz.jahonov:ktor-monitor:<version>")
    implementation("uz.jahonov:ktor-monitor-ui:<version>")
}
```

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val monitor = KtorMonitor(this) {
            retention = Retention.OneDay
            sanitizeHeaders("Authorization")
        }
        monitor.attach(httpClient)
        KtorMonitorUi.install(this, monitor)
    }
}
```

`KtorMonitorUi.open(context)` opens the monitor from code.

### Kotlin Multiplatform + iOS

In the shared module, depend on the core and export it from the framework, so Swift sees
`KtorMonitorBridge`:

```kotlin
kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "Shared"
            export("uz.jahonov:ktor-monitor:<version>")
        }
    }
    sourceSets.commonMain.dependencies {
        api("uz.jahonov:ktor-monitor:<version>")
    }
}
```

Create the monitor on the iOS side of the shared code and attach it to your client:

```kotlin
// iosMain
object Monitor {
    private val monitor = KtorMonitor { retention = Retention.OneDay }
    val bridge = KtorMonitorBridge(monitor)

    fun attach(client: HttpClient) = monitor.attach(client)
}
```

In Xcode, add this repository as a Swift package (product `KtorMonitorUI`), then:

```swift
import KtorMonitorUI
import Shared

// The one line that connects the Kotlin bridge to the SwiftUI package.
extension KtorMonitorBridge: @retroactive KtorMonitorUIBridge {}

@main
struct MyApp: App {
    init() {
        KtorMonitorUI.install(bridge: Monitor.shared.bridge)
    }
    // ...
}
```

`KtorMonitorUI.present()` opens the monitor from code; `KtorMonitorView` embeds it in your own
view hierarchy. If the app sets its own `UNUserNotificationCenterDelegate`, pass responses to
`KtorMonitorUI.handleNotificationResponse(_:)` first. `@retroactive` needs Xcode 16; drop it on
older versions.

### Where to attach

Attach the monitor **after** any plugin that retries requests, such as token refresh. It then sees
each attempt with its final headers. Attaching it to a client built earlier is fine; it installs an
`HttpSend` interceptor.

## Configuration

| Option | Default | |
|---|---|---|
| `isActive` | `true` | `false` records nothing |
| `retention` | `OneHour` | `OneHour`, `OneDay`, `OneWeek`, `Forever` |
| `maxCalls` | `1000` | the oldest go first |
| `maxContentLength` | `250000` | bytes kept per body; the rest is counted, not stored |
| `showNotification` | `true` | the notification with the latest calls |
| `appName`, `appVersion` | from the platform | named in exports |
| `onInternalError` | ignore | failures of the monitor itself |
| `filter { request -> … }` | | record only the calls it accepts |
| `sanitizeHeader { name -> … }`, `sanitizeHeaders(…)` | | replace header values |
| `redactBodyFields(…)` | | replace JSON field values at any depth |

## Keeping it out of release builds

The monitor records everything, tokens included. Ship it in debug or internal builds only: on
Android, depend on it with `debugImplementation` (or a flavor's configuration) and create it from a
source set that release builds do not compile; on iOS, export it only from the framework of those
builds.

## How iOS works

Kotlin types reach Swift only through the app's own framework, whose name the SwiftUI package
cannot know. So the package talks to the monitor through `KtorMonitorUIBridge`: strings and
closures only, with states, effects and events as JSON. The Kotlin `KtorMonitorBridge` has exactly
those members, which is why one `extension` line connects them. The JSON contract is pinned by
fixtures in `ios/KtorMonitorUI/Tests/KtorMonitorUITests/Fixtures`, which the Kotlin tests write and
the Swift tests read.

## Samples

- Android: `./gradlew :sample:installDebug`
- iOS: open `ios/Sample/Sample.xcodeproj` and run the `Sample` scheme.

Both make calls against [httpbin.org](https://httpbin.org) that show each kind of body and failure.

## Requirements

Kotlin 2.4, Ktor 3.6, Android minSdk 24, iOS 16.

## License

[Apache 2.0](LICENSE)
