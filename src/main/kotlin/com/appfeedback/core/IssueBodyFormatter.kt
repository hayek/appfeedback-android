package com.appfeedback.core

/** Renders a report + device info (+ uploaded attachments) into the exact issue
 *  body the parser understands. Port of the Swift `IssueBodyFormatter`. */
object IssueBodyFormatter {
    const val USER_SUBMITTED_LABEL = "user-submitted"

    fun format(
        report: FeedbackReport,
        deviceInfo: DeviceInfo,
        uploaded: List<UploadedAttachment> = emptyList(),
    ): String {
        val sb = StringBuilder(report.description)
        sb.append("\n\n${BodyMarker.HORIZONTAL_RULE}\n**${BodyMarker.DEVICE_HEADER}**\n")
        sb.append(deviceInfo.renderForIssueBody())

        report.contactEmail?.takeIf { it.isNotEmpty() }?.let {
            sb.append("\n\n**${BodyMarker.CONTACT_EMAIL_LABEL}**\n$it")
        }

        for (key in report.extraFields.keys.sortedWith { a, b -> codePointOrder(a, b) }) {
            sb.append("\n\n**$key:**\n${report.extraFields[key]}")
        }

        if (uploaded.isNotEmpty()) {
            sb.append("\n\n${BodyMarker.ATTACHMENTS_OPEN}\n${BodyMarker.ATTACHMENTS_HEADER}\n")
            for (a in uploaded) {
                val prefix = if (a.mimeType.startsWith("image/")) "!" else ""
                val size = DeterministicByteCount.string(a.sizeBytes)
                sb.append("\n$prefix[${a.filename}](${a.url}) — ${a.mimeType}, $size\n")
            }
            sb.append("\n${BodyMarker.ATTACHMENTS_CLOSE}")
        }

        sb.append("\n\n${BodyMarker.HORIZONTAL_RULE}\n${BodyMarker.VOTES_FOOTER}")
        return sb.toString()
    }

    fun labels(type: FeedbackType): List<String> = listOf(type.rawValue, USER_SUBMITTED_LABEL)

    /** Ascending Unicode code-point order. Iterates code points (not UTF-16
     *  chars) so non-BMP keys order identically across platforms. */
    internal fun codePointOrder(a: String, b: String): Int {
        val ai = a.codePoints().iterator()
        val bi = b.codePoints().iterator()
        while (ai.hasNext() && bi.hasNext()) {
            val cmp = ai.nextInt().compareTo(bi.nextInt())
            if (cmp != 0) return cmp
        }
        return ai.hasNext().compareTo(bi.hasNext())  // shorter (prefix) sorts first
    }
}
