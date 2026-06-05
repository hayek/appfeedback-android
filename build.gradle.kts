plugins {
    kotlin("jvm") version "2.2.20"
}

group = "io.github.hayek"          // finalized in P1c (publishing)
version = "0.1.0-SNAPSHOT"

repositories { mavenCentral() }

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("com.google.code.gson:gson:2.11.0")
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
