# Publishing to Maven Central

The build is wired to produce a Maven Central-compliant artifact set for
`io.github.hayek:appfeedback-android`. Everything up to the credential-gated
upload is done; the steps below need **your** Sonatype Central Portal account
and GPG key.

## What's already configured (`build.gradle.kts`)

- `maven-publish` with a `maven` publication from the JVM `java` component.
- A **sources jar** (`withSourcesJar()`) and a **Dokka HTML javadoc jar** — both required by Central.
- A complete **POM**: name, description, url, MIT license, developer, SCM.
- **Signing, gated on key presence** — `./gradlew test` and CI run fine without a key; signing only activates when you supply one (so it never blocks development).

Verify locally any time (produces an unsigned bundle under `build/staging-deploy/`):

```sh
export JAVA_HOME=/path/to/jdk-21
./gradlew publishMavenPublicationToLocalStagingRepository
```

## One-time setup

1. **Central Portal account** at <https://central.sonatype.com>. The namespace
   `io.github.hayek` verifies automatically through your GitHub account (`hayek`).
2. **A GPG key**, published to a public keyserver. Export the ASCII-armored
   private key for the in-memory signer.

## Cutting a release

1. Set a release version (Central rejects `-SNAPSHOT`):

   ```kotlin
   version = "0.1.0"   // in build.gradle.kts, or pass -Pversion=0.1.0
   ```

2. Build the **signed** bundle (supply the key as Gradle properties or
   `ORG_GRADLE_PROJECT_*` env vars):

   ```sh
   ./gradlew publishMavenPublicationToLocalStagingRepository \
     -PsigningInMemoryKey="$(cat my-key.asc)" \
     -PsigningInMemoryKeyPassword="$GPG_PASSWORD"
   ```

   `build/staging-deploy/` now holds the jar, sources, javadoc, pom, checksums,
   and `.asc` signatures in Maven-repo layout.

3. Zip the `io/` tree under `build/staging-deploy/` and upload it at
   **Central Portal → Publish → Upload a bundle**, then click *Publish*.

## Simpler alternative

If you'd rather have a one-command `./gradlew publishToMavenCentral`, swap the
hand-rolled config for the [vanniktech maven-publish plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/),
which wraps the Central Portal API, signing, and the sources/Dokka-javadoc jars.
The POM/coordinates above port over directly.

## The `:android` Compose module

Both artifacts are wired:

| Artifact | Module | Contents |
| --- | --- | --- |
| `io.github.hayek:appfeedback-android` | root | `com.appfeedback.core` — wire format, transports, `FeedbackClient` |
| `io.github.hayek:appfeedback-android-compose` | `:android` | `FeedbackSheet`, `currentDeviceInfo`, `androidFeedbackClient` |

The Compose module uses AGP single-variant publishing (`release`) with sources +
Dokka-javadoc jars and the same gated signing. Stage it locally with:

```sh
./gradlew :android:publishReleasePublicationToLocalStagingRepository
```

When cutting a release, build and upload both staging bundles (root +
`:android`).
