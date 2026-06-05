package com.appfeedback.core

/** Bug report or feature request. The raw value is also the GitHub label string. */
enum class FeedbackType(val rawValue: String) {
    BUG("bug"),
    FEATURE_REQUEST("feature-request");

    companion object {
        fun fromRawValue(value: String): FeedbackType? = entries.firstOrNull { it.rawValue == value }
    }
}
