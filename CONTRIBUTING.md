# Contributing to AppFeedback (Android/Kotlin SDK)

Thanks for helping improve AppFeedback! This is the Android/Kotlin SDK
(Gradle). Bug reports, docs fixes, and pull requests are all welcome.

## Prerequisites

- JDK 21
- The Gradle wrapper (`./gradlew`) — no system Gradle install required
- Android SDK / command-line tools for instrumentation builds

## Build & test

```sh
export JAVA_HOME=/path/to/jdk-21
./gradlew test
```

Please make sure the full test suite passes before opening a PR.

## The golden rule: the wire format is pinned by the spec

The cross-platform wire format is defined by the golden fixtures in
[**appfeedback-spec**](https://github.com/hayek/appfeedback-spec). The Swift,
Android, and Web SDKs must all encode and decode byte-for-byte identical
payloads, and the conformance tests in this repo run against fixtures synced
from the spec.

**Any change to the wire format must update the spec fixtures first.** Never
edit a synced fixture just to make a test pass — that silently breaks the
other platforms. The correct flow is:

1. Propose the change in `appfeedback-spec` and update its fixtures.
2. Run the spec's `scripts/sync-to-android.sh` to bring the fixtures here.
3. Update this SDK to satisfy the new fixtures.

If a conformance test fails, treat it as a real cross-platform contract
mismatch, not a fixture to be massaged.

## Conventions

- Match the existing Kotlin style; keep public API additions documented.
- Add a `CHANGELOG.md` entry under `[Unreleased]` for user-facing changes.

## Questions

See the docs at <https://hayek.github.io/appfeedback-docs/>. For security
issues, follow [SECURITY.md](SECURITY.md) — do not open a public issue.
