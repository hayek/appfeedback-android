# appfeedback-android

The **Android (Kotlin)** SDK in the [AppFeedback](https://hayek.github.io/appfeedback-docs/) family. It turns in-app feedback into a GitHub issue — in the exact same byte-for-byte wire format as the [Apple](https://github.com/hayek/AppFeedbackSDK) and [Web](https://github.com/hayek/appfeedback-web) SDKs.

> **Status:** the shared `com.appfeedback.core` module (wire format, transports, `FeedbackClient`) is implemented and passes the cross-platform conformance gate. Maven Central publishing and the Compose UI module are in progress — until then, build from this repo.

## Why byte-exact?

Every AppFeedback SDK emits an identical GitHub issue body. That contract is pinned by a shared spec and a golden-fixture conformance suite — see [`appfeedback-spec`](https://github.com/hayek/appfeedback-spec) — that runs in this repo's CI. A feedback report filed from Android is indistinguishable from one filed on iOS or the web, so a single inbox parses them all.

## Quick start

```kotlin
val client = androidFeedbackClient(
    transport = GitHubDirectTransport(owner = "acme", repo = "feedback", token = token),
    appName = "Acme",
    appVersion = "1.0.0",
)

val issueNumber = client.submit(
    FeedbackReport(type = FeedbackType.BUG, title = "Crash on launch", description = "Steps…")
)
```

- `GitHubDirectTransport` — posts straight to the GitHub API with a token held in your app's secure storage.
- `RelayTransport` — posts to a relay **you** host (Firebase / Appwrite / your own), which holds the token. Recommended when you don't want to ship a writable token in the app. See the [relay guide](https://hayek.github.io/appfeedback-docs/guides/relay/).

### Drop-in Compose UI

The `:android` module ships a themeable `FeedbackSheet`:

```kotlin
FeedbackSheet(client = client, onDismiss = { /* … */ })
```

## Modules

| Module | Coordinates (publishing in progress) | Contents |
| --- | --- | --- |
| core | `io.github.hayek:appfeedback-android` | `FeedbackType`, `FeedbackReport`, `DeviceInfo`, `IssueBodyFormatter`/`IssueBodyParser`, transports, `FeedbackClient` |
| compose | `io.github.hayek:appfeedback-android-compose` | `FeedbackSheet`, `currentDeviceInfo` |

minSdk 24.

## Build & test

```sh
export JAVA_HOME=/path/to/jdk-21   # AGP 9 rejects newer JDKs; 21 compiles both modules
./gradlew test                     # conformance gate (core) + :android Robolectric/Compose tests
```

API reference: <https://hayek.github.io/appfeedback-docs/reference/kotlin/>

## License

MIT © Amir Hayek. See [LICENSE](./LICENSE).
