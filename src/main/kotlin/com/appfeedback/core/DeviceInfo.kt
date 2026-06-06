package com.appfeedback.core

import kotlinx.serialization.Serializable

/** Per-submission device/app metadata. `current()` (via android.os.Build) is added in P1b. */
@Serializable
data class DeviceInfo(
    val appName: String,
    val appVersion: String,
    val buildNumber: String,
    val model: String,
    val osName: String,
    val osVersion: String,
) {
    fun renderForIssueBody(): String =
        "${BodyMarker.APP_LABEL} $appName\n" +
        "${BodyMarker.APP_VERSION_LABEL} $appVersion ($buildNumber)\n" +
        "${BodyMarker.DEVICE_LABEL} $model\n" +
        "$osName${BodyMarker.OS_VERSION_SUFFIX} $osVersion"
}
