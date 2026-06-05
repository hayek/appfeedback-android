package com.appfeedback.core

/** The string literals that make up the wire contract. Mirrors the Swift
 *  `BodyMarker`; both ends read from one definition so they can't drift. */
internal object BodyMarker {
    const val DEVICE_HEADER = "Device Information:"
    const val APP_LABEL = "App:"
    const val APP_VERSION_LABEL = "App Version:"
    const val DEVICE_LABEL = "Device:"
    const val OS_VERSION_SUFFIX = " Version:"
    const val CONTACT_EMAIL_LABEL = "Contact Email:"
    const val HORIZONTAL_RULE = "---"
    const val VOTES_FOOTER = "👍 Votes: 0"
    const val ATTACHMENTS_OPEN = "<!-- attachments-v1 -->"
    const val ATTACHMENTS_CLOSE = "<!-- /attachments-v1 -->"
    const val ATTACHMENTS_HEADER = "## Attachments"

    val recognisedOSNames = listOf(
        "OS", "macOS", "iOS", "iPadOS", "watchOS", "tvOS", "visionOS",
        "Android", "Windows", "Linux", "Web", "ChromeOS",
    )

    val osVersionRegex = Regex(
        "^(${recognisedOSNames.joinToString("|")}) Version:",
        RegexOption.IGNORE_CASE,
    )
}
