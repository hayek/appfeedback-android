plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
}
repositories { google(); mavenCentral() }
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
}
dependencies {
  api(project(":")) // the JVM core: FeedbackReport, DeviceInfo, FeedbackClient, transports
  implementation(platform("androidx.compose:compose-bom:2024.12.01"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.foundation:foundation")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.robolectric:robolectric:4.14.1")
  testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
  testImplementation("androidx.compose.ui:ui-test-junit4")
  testImplementation("androidx.compose.ui:ui-test-manifest")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}
