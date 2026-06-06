package com.appfeedback.core

/** Thrown by transports when a submission fails. `status` is the HTTP status when applicable. */
class FeedbackSubmissionError(message: String, val status: Int? = null) : Exception(message)
