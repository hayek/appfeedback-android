package com.appfeedback.core

/** Where a report gets delivered. Concrete implementations (GitHub direct,
 *  relay) are added in P1b. Returns the backend-assigned identifier
 *  (the GitHub issue number for the GitHub transport). */
interface FeedbackTransport {
    suspend fun submit(report: FeedbackReport, deviceInfo: DeviceInfo): Int
}
