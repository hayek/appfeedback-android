package com.appfeedback.core

data class ParsedAttachment(
    val filename: String,
    val mimeType: String,
    val url: String,
    val sizeBytes: Long?,
)

data class ParsedFeedbackBody(
    val description: String = "",
    val appName: String? = null,
    val appVersion: String? = null,
    val device: String? = null,
    val osVersion: String? = null,
    val email: String? = null,
    val attachments: List<ParsedAttachment> = emptyList(),
)
