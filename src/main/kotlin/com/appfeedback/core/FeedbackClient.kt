package com.appfeedback.core

/** Entry point: holds a transport and a DeviceInfo provider, and submits reports.
 *  The Android-specific provider (via android.os.Build) is added in the Android
 *  library module; here the provider is supplied explicitly. */
class FeedbackClient(
    private val transport: FeedbackTransport,
    private val deviceInfoProvider: () -> DeviceInfo,
) {
    suspend fun submit(report: FeedbackReport): Int = transport.submit(report, deviceInfoProvider())
}
