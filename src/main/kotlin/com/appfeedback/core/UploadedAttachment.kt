package com.appfeedback.core

/** An attachment already uploaded; drives the body's attachment block. */
data class UploadedAttachment(
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val url: String,
)
