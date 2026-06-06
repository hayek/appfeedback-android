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
}

group = "io.github.hayek"          // finalized in P1c (publishing)
version = "0.1.0-SNAPSHOT"

repositories { mavenCentral() }

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-cio:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    testImplementation("io.ktor:ktor-client-mock:3.0.3")
}

// Only JDK 26 is installed; compile on it but emit broadly-compatible JVM 17
// bytecode. The Java and Kotlin target levels MUST match or Gradle fails with
// "Inconsistent JVM Target Compatibility".
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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
