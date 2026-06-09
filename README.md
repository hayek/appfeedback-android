# appfeedback-android

[![CI](https://github.com/hayek/appfeedback-android/actions/workflows/ci.yml/badge.svg)](https://github.com/hayek/appfeedback-android/actions/workflows/ci.yml) [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

The **Android (Kotlin)** SDK in the [AppFeedback](https://hayek.github.io/appfeedback-docs/) family. It turns in-app feedback into a GitHub issue — in the exact same byte-for-byte wire format as the [Apple](https://github.com/hayek/AppFeedbackSDK) and [Web](https://github.com/hayek/appfeedback-web) SDKs.

> **Status:** the shared `com.appfeedback.core` module (wire format, transports, `FeedbackClient`) **and** the `:android` Compose UI module (`FeedbackSheet`, `currentDeviceInfo`, `androidFeedbackClient`) are implemented and pass the cross-platform conformance gate plus the Robolectric/Compose tests. Only the Maven Central registry release is still in progress — until it lands, build from this repo.

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
FeedbackSheet(
    client = client,
    defaultType = FeedbackType.BUG,
    onSubmitted = { issueNumber -> /* dismiss, show a toast, … */ },
    onError = { error -> /* surface the failure */ },
)
```

## Modules

Both modules are implemented, tested, and wired for publishing (see [PUBLISHING.md](./PUBLISHING.md)); only the Maven Central registry release is pending.

| Module | Coordinates (registry release pending) | Contents |
| --- | --- | --- |
| core | `io.github.hayek:appfeedback-android` | `FeedbackType`, `FeedbackReport`, `DeviceInfo`, `IssueBodyFormatter`/`IssueBodyParser`, transports, `FeedbackClient` |
| compose | `io.github.hayek:appfeedback-android-compose` | `FeedbackSheet`, `currentDeviceInfo` |

minSdk 24.

## Sample app

The `:sample` module is a tiny runnable app that hosts the real `FeedbackSheet`
in a modal bottom sheet, wired to a mock transport (so it runs with no GitHub
token — submissions return a fake issue number). It's the quickest way to see
how the SDK looks and behaves.

```sh
export JAVA_HOME=/path/to/jdk-21
./gradlew :sample:assembleDebug                 # build the APK
# install + launch on a running emulator/device:
./gradlew :sample:installDebug
adb shell am start -n com.appfeedback.sample/.MainActivity
```

Or just open the project in Android Studio and run the **sample** configuration
(the `FeedbackSheet` also has an `@Preview`). Swap `MockTransport` for
`GitHubDirectTransport` / `RelayTransport` to talk to a real backend.

## Build & test

```sh
export JAVA_HOME=/path/to/jdk-21   # AGP 9 rejects newer JDKs; 21 compiles both modules
./gradlew test                     # conformance gate (core) + :android Robolectric/Compose tests
```

API reference: <https://hayek.github.io/appfeedback-docs/reference/kotlin/>

## License

MIT © Amir Hayek. See [LICENSE](./LICENSE).
