package com.appfeedback.core

/** Inverse of `IssueBodyFormatter`: pulls structured fields out of an issue body.
 *  Tolerant of hand-written / legacy bodies. Port of the Swift `IssueBodyParser`. */
object IssueBodyParser {

    fun parse(raw: String): ParsedFeedbackBody {
        val descLines = mutableListOf<String>()
        var inDevice = false
        var expectEmail = false
        var appName: String? = null
        var appVersion: String? = null
        var device: String? = null
        var osVersion: String? = null
        var email: String? = null

        for (line in raw.split("\n")) {
            val trimmed = line.trim().replace("**", "").trim()

            if (trimmed == BodyMarker.DEVICE_HEADER) { inDevice = true; continue }
            if (!inDevice) { descLines.add(line); continue }

            if (expectEmail) {
                if (trimmed.isNotEmpty() && trimmed.contains("@")) email = trimmed
                expectEmail = false
                continue
            }

            val appVer = valueAfter(trimmed, BodyMarker.APP_VERSION_LABEL)
            val app = valueAfter(trimmed, BodyMarker.APP_LABEL)
            val dev = valueAfter(trimmed, BodyMarker.DEVICE_LABEL)
            when {
                appVer != null -> appVersion = appVer
                app != null -> appName = app
                dev != null -> device = dev
                BodyMarker.osVersionRegex.containsMatchIn(trimmed) ->
                    osVersion = trimmed.split(":").drop(1).joinToString(":").trim()
                trimmed == BodyMarker.CONTACT_EMAIL_LABEL -> expectEmail = true
                else -> {
                    val v = valueAfter(trimmed, BodyMarker.CONTACT_EMAIL_LABEL)
                    if (v != null) { if (v.contains("@")) email = v else expectEmail = true }
                }
            }
        }

        val description = descLines
            .filter { it.trim() != BodyMarker.HORIZONTAL_RULE }
            .joinToString("\n")
            .trim()

        return ParsedFeedbackBody(
            description = description,
            appName = appName,
            appVersion = appVersion,
            device = device,
            osVersion = osVersion,
            email = email,
            attachments = parseAttachments(raw),
        )
    }

    private fun valueAfter(s: String, marker: String): String? =
        if (s.startsWith(marker)) s.removePrefix(marker).trim() else null

    private fun parseAttachments(raw: String): List<ParsedAttachment> {
        val openIdx = raw.indexOf(BodyMarker.ATTACHMENTS_OPEN)
        if (openIdx < 0) return emptyList()
        val afterOpen = openIdx + BodyMarker.ATTACHMENTS_OPEN.length
        val closeIdx = raw.indexOf(BodyMarker.ATTACHMENTS_CLOSE, afterOpen)
        val block = if (closeIdx < 0) raw.substring(afterOpen) else raw.substring(afterOpen, closeIdx)

        val results = mutableListOf<ParsedAttachment>()
        for (rawLine in block.split("\n")) {
            parseAttachmentLine(rawLine.trim())?.let { results.add(it) }
        }
        return results
    }

    private fun parseAttachmentLine(line: String): ParsedAttachment? {
        val working = when {
            line.startsWith("![") -> line.removePrefix("![")
            line.startsWith("[") -> line.removePrefix("[")
            else -> return null
        }
        val nameEnd = working.indexOf("](")
        if (nameEnd < 0) return null
        val filename = working.substring(0, nameEnd)
        val afterName = working.substring(nameEnd + 2)
        val urlEnd = afterName.indexOf(')')
        if (urlEnd < 0) return null
        val url = afterName.substring(0, urlEnd)
        if (url.isEmpty()) return null
        val rest = afterName.substring(urlEnd + 1).trim()

        var mime: String? = null
        var size: Int? = null
        if (rest.startsWith("—")) {
            val suffix = rest.removePrefix("—").trim()
            val parts = suffix.split(",", limit = 2).map { it.trim() }
            mime = parts[0]
            if (parts.size > 1) size = parseHumanByteCount(parts[1])
        }
        val resolvedMime = mime ?: inferMimeFromUrl(url)
        return ParsedAttachment(filename, resolvedMime, url, size)
    }

    internal fun parseHumanByteCount(s: String): Int? {
        val parts = s.split(" ", limit = 2)
        val num = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
        val unit = if (parts.size > 1) parts[1].uppercase() else "B"
        return when (unit) {
            "BYTES", "B" -> num.toInt()
            "KB" -> (num * 1_000).toInt()
            "MB" -> (num * 1_000_000).toInt()
            "GB" -> (num * 1_000_000_000).toInt()
            else -> num.toInt()
        }
    }

    /** Best-effort MIME from a URL's file extension. Only used when the body line
     *  omits the MIME (not exercised by the conformance corpus). */
    internal fun inferMimeFromUrl(url: String): String {
        val ext = url.substringAfterLast('/').substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "log", "txt", "text" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "csv" -> "text/csv"
            else -> "application/octet-stream"
        }
    }
}
