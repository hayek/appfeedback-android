plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
  // Dokka 2.x: HTML API reference for the public Compose UI API. The version is
  // resolved from the root project's plugins block (it loads Dokka 2.0.0 onto the
  // shared buildscript classloader), so no version is needed here.
  id("org.jetbrains.dokka")
  `maven-publish`
  signing
}
repositories { google(); mavenCentral() }

// Mirror the root module's coordinates so this artifact publishes under the same
// namespace (the AGP library uses artifactId `appfeedback-android-compose`).
group = "io.github.hayek"
// Follow the root's version (single source of truth in gradle.properties) so the
// core and compose modules always publish at the same version. -Pversion overrides both.
version = rootProject.version

android {
  namespace = "com.appfeedback.android"
  compileSdk = 35
  defaultConfig { minSdk = 24 }
  buildFeatures { compose = true }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
  // Maven Central requires a sources jar + a javadoc jar alongside the .aar. AGP
  // produces the `release` software component used by the publication below.
  publishing {
    singleVariant("release") {
      withSourcesJar()
      withJavadocJar()
    }
  }
}
dependencies {
  api(project(":")) // the JVM core: FeedbackReport, DeviceInfo, FeedbackClient, transports
  // The BOM must be `api` too: it carries the versions for the versionless `api`
  // compose deps below, so an `implementation` BOM leaves them version-less (and
  // thus absent) in the published api variant / POM compile scope.
  api(platform("androidx.compose:compose-bom:2026.05.01"))
  // FeedbackSheet is a public @Composable that takes a Modifier, so the types in
  // its signature must be on the consumer's compile classpath: `@Composable`
  // comes from compose.runtime, `Modifier` from compose.ui — promote both to
  // `api`. material3 / foundation are internal to the sheet's implementation and
  // appear in no public signature, so they stay `implementation`.
  api("androidx.compose.runtime:runtime")
  api("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.foundation:foundation")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.robolectric:robolectric:4.16.1")
  testImplementation(platform("androidx.compose:compose-bom:2026.05.01"))
  testImplementation("androidx.compose.ui:ui-test-junit4")
  testImplementation("androidx.compose.ui:ui-test-manifest")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// ---- Dokka (HTML API reference for the Compose UI module) ----
dokka {
  // URL-safe module name: Dokka derives the output sub-directory from this, so
  // keep it all-lowercase + hyphenated to match the published artifactId and
  // keep hosted (GitHub Pages) URLs clean.
  moduleName.set("appfeedback-android-compose")
  dokkaPublications.html {
    outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
  }
  // Dokka 2.0.0's Android adapter (AndroidExtensionWrapper) cannot read AGP 9.x's
  // extension, so it auto-registers NO source set and renders an empty site.
  // Register the Compose UI's main Kotlin source set explicitly so the public API
  // (FeedbackSheet, FeedbackSheetState, currentDeviceInfo, androidFeedbackClient)
  // is documented.
  dokkaSourceSets.register("main") {
    sourceRoots.from(file("src/main/kotlin"))
  }
}

// ---- Publishing (Maven Central via the Central Portal — see PUBLISHING.md) ----
// AGP only registers the `release` software component after the project is
// evaluated, so the publication that consumes it must be created in afterEvaluate.
afterEvaluate {
  publishing {
    publications {
      create<MavenPublication>("release") {
        from(components["release"]) // .aar + sources jar + javadoc jar
        artifactId = "appfeedback-android-compose"
        pom {
          name.set("AppFeedback for Android — Compose UI")
          description.set(
            "Jetpack Compose UI for the AppFeedback Android SDK — a drop-in " +
              "FeedbackSheet and Android device-info helpers on top of the " +
              "com.appfeedback.core wire format.",
          )
          url.set("https://github.com/hayek/appfeedback-android")
          licenses {
            license {
              name.set("MIT License")
              url.set("https://github.com/hayek/appfeedback-android/blob/main/LICENSE")
            }
          }
          developers {
            developer {
              id.set("hayek")
              name.set("Amir Hayek")
              url.set("https://github.com/hayek")
            }
          }
          scm {
            url.set("https://github.com/hayek/appfeedback-android")
            connection.set("scm:git:https://github.com/hayek/appfeedback-android.git")
            developerConnection.set("scm:git:ssh://git@github.com/hayek/appfeedback-android.git")
          }
        }
      }
    }
  }
}

publishing {
  repositories {
    // Local staging dir. The real upload is a Central Portal bundle built from
    // here (see PUBLISHING.md) — Sonatype OSSRH direct deploy is retired.
    maven {
      name = "localStaging"
      url = uri(layout.buildDirectory.dir("staging-deploy"))
    }
  }
}

// Sign only when a key is supplied, so `test`/CI run without GPG. Provide via
// -PsigningInMemoryKey=<ascii-armored-key> -PsigningInMemoryKeyPassword=<pw>
// (or the matching ORG_GRADLE_PROJECT_ env vars) when publishing a release.
signing {
  val signingKey = providers.gradleProperty("signingInMemoryKey").orNull
  val signingPassword = providers.gradleProperty("signingInMemoryKeyPassword").orNull
  isRequired = signingKey != null
  if (signingKey != null) {
    useInMemoryPgpKeys(signingKey, signingPassword)
    // The `release` publication is created in afterEvaluate, so wire signing up
    // there too (it does not exist yet at configuration time).
    afterEvaluate {
      sign(publishing.publications["release"])
    }
  }
}
