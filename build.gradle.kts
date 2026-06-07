buildscript {
    repositories { google(); mavenCentral() }
    dependencies {
        // AGP must be in the root classpath so that KotlinAndroidTarget (in the
        // Kotlin Gradle Plugin) can resolve BaseVariant when the :android
        // subproject's plugin classloader inherits from the root.
        classpath("com.android.tools.build:gradle:9.2.1")
    }
}

plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    // Dokka 2.x: HTML API reference generation for the root JVM module only.
    // Scoped to this build file so the :android subproject is not pulled in
    // (that would require Dokka's Android support + the Android toolchain).
    id("org.jetbrains.dokka") version "2.0.0"
    `maven-publish`
    signing
}

group = "io.github.hayek"          // finalized in P1c (publishing)
version = "0.1.0-SNAPSHOT"

repositories { mavenCentral() }

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // `HttpClient` is the documented injection point on RelayTransport /
    // GitHubDirectTransport public constructors, so ktor-client-core must be on
    // the consumer's compile classpath — promote ONLY this artifact to `api`.
    // The remaining ktor / serialization deps stay `implementation` (genuine
    // internals; no other ktor type appears in a public signature).
    api("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-cio:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    testImplementation("io.ktor:ktor-client-mock:3.0.3")
}

// Build on JDK 21 (AGP 9 rejects JDK 26; CI pins Temurin 21). Emit
// broadly-compatible JVM 17 bytecode — Java and Kotlin target levels MUST match
// or Gradle fails with "Inconsistent JVM Target Compatibility".
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar() // sources jar is added to the `java` component (required by Maven Central)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.test { useJUnitPlatform() }

// Dokka HTML output directory. Generate with:
//   ./gradlew :dokkaGeneratePublicationHtml
// (run on the root project only; do not invoke on :android).
dokka {
    // URL-safe module name: Dokka derives the output sub-directory from this,
    // and any spaces/parens/uppercase would leak into hosted URLs. Keep it
    // all-lowercase + hyphenated so the path stays clean on GitHub Pages.
    moduleName.set("appfeedback-android")
    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
    }
}

// ---- Publishing (Maven Central via the Central Portal — see PUBLISHING.md) ----
// A Dokka-generated HTML javadoc jar (Maven Central requires a javadoc artifact).
val dokkaJavadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.named("dokkaGeneratePublicationHtml"))
    from(layout.buildDirectory.dir("dokka/html"))
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"]) // main jar + sources jar (via withSourcesJar)
            artifact(dokkaJavadocJar)
            artifactId = "appfeedback-android"
            pom {
                name.set("AppFeedback for Android")
                description.set(
                    "Android (Kotlin) feedback SDK — turn in-app feedback into a GitHub issue, " +
                        "in one byte-exact wire format shared across Apple, Android & Web.",
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
    repositories {
        // Local staging dir. The real upload is a Central Portal bundle built
        // from here (see PUBLISHING.md) — Sonatype OSSRH direct deploy is retired.
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
        sign(publishing.publications["maven"])
    }
}
