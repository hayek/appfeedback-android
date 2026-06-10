plugins {
  id("com.android.application")
  // Same Compose compiler plugin version the :android library uses (resolved via
  // the root buildscript's AGP-on-classpath setup; see the root build.gradle.kts).
  id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
}

repositories { google(); mavenCentral() }

android {
  namespace = "com.appfeedback.sample"
  compileSdk = 35
  defaultConfig {
    applicationId = "com.appfeedback.sample"
    minSdk = 24
    targetSdk = 35
    versionCode = 1
    versionName = "0.1.0"
  }
  buildFeatures { compose = true }
  buildTypes {
    release { isMinifyEnabled = false }
  }
}

dependencies {
  // The Compose UI module — api-exposes the core (FeedbackClient/Report/Type/
  // Transport/DeviceInfo) plus FeedbackSheet + androidFeedbackClient.
  implementation(project(":android"))
  implementation(platform("androidx.compose:compose-bom:2026.05.01"))
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.activity:activity-compose:1.13.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
  debugImplementation("androidx.compose.ui:ui-tooling")
}
