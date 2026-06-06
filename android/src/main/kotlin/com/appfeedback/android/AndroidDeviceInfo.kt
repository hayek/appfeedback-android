package com.appfeedback.android

import android.os.Build
import com.appfeedback.core.DeviceInfo
import com.appfeedback.core.FeedbackClient
import com.appfeedback.core.FeedbackTransport

/** Browser-of-Android device info: app fields from the caller, platform fields
 *  from android.os.Build. osName is always "Android" (a recognised inbox OS). */
fun currentDeviceInfo(appName: String, appVersion: String, buildNumber: String = "0"): DeviceInfo =
  DeviceInfo(
    appName = appName,
    appVersion = appVersion,
    buildNumber = buildNumber,
    model = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Unknown" },
    osName = "Android",
    osVersion = Build.VERSION.RELEASE ?: "Unknown",
  )

/** Convenience client that fills device info from android.os.Build. */
fun androidFeedbackClient(transport: FeedbackTransport, appName: String, appVersion: String, buildNumber: String = "0"): FeedbackClient =
  FeedbackClient(transport) { currentDeviceInfo(appName, appVersion, buildNumber) }
