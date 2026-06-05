package com.appfeedback.core

/** A single user-supplied feedback submission. Attachments (raw files) and the
 *  device-info collector are platform concerns handled in later phases. */
data class FeedbackReport(
    val type: FeedbackType,
    val title: String,
    val description: String,
    val contactEmail: String? = null,
    val extraFields: Map<String, String> = emptyMap(),
)
