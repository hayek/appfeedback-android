package com.appfeedback.core

/** Inverse of `IssueBodyFormatter`: pulls structured fields out of an issue body.
 *  Tolerant of hand-written / legacy bodies. Port of the Swift `IssueBodyParser`. */
object IssueBodyParser {

    /** Canonical ASCII whitespace set the parser trims, per the wire-format spec:
     *  `{ U+0009, U+000A, U+000B, U+000C, U+000D, U+0020 }` and nothing else.
     *  Deliberately NOT `String.trim()`, which strips the whole Unicode-defined
     *  whitespace set (NBSP, NEL, BOM, …) and would diverge from Swift/TS. Non-ASCII
     *  whitespace is preserved verbatim, identically across all three ports. */
    private val ASCII_WS = charArrayOf('\u0009', '\u000A', '\u000B', '\u000C', '\u000D', '\u0020')
    private fun String.trimAscii(): String = trim { it in ASCII_WS }

    /** ASCII-decimal magnitude grammar, per the wire-format spec. Tokens that
     *  don't match (`0x10`, `0b1010`, `0o17`, `0xAp2`, `Infinity`, `NaN`, …) are
     *  rejected so the size is treated as absent. The JVM's `Double` parser accepts
     *  hex-float forms, so we MUST gate on this regex to match Swift/TS. */
    private val DECIMAL_MAGNITUDE = Regex("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?$")

    fun parse(raw: String): ParsedFeedbackBody {
        // Normalize CRLF / lone CR to LF so web-UI-authored bodies parse identically.
        val normalized = raw.replace("\r\n", "\n").replace("\r", "\n")
        val descLines = mutableListOf<String>()
        var inDevice = false
        var expectEmail = false
        var appName: String? = null
        var appVersion: String? = null
        var device: String? = null
        var osVersion: String? = null
        var email: String? = null

        for (line in normalized.split("\n")) {
            val trimmed = line.trimAscii().replace("**", "").trimAscii()

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
                    osVersion = trimmed.split(":").drop(1).joinToString(":").trimAscii()
                trimmed == BodyMarker.CONTACT_EMAIL_LABEL -> expectEmail = true
                else -> {
                    val v = valueAfter(trimmed, BodyMarker.CONTACT_EMAIL_LABEL)
                    if (v != null) { if (v.contains("@")) email = v else expectEmail = true }
                }
            }
        }

        val description = descLines
            .filter { it.trimAscii() != BodyMarker.HORIZONTAL_RULE }
            .joinToString("\n")
            .trimAscii()

        return ParsedFeedbackBody(
            description = description,
            appName = appName,
            appVersion = appVersion,
            device = device,
            osVersion = osVersion,
            email = email,
            attachments = parseAttachments(normalized),
        )
    }

    private fun valueAfter(s: String, marker: String): String? =
        if (s.startsWith(marker)) s.removePrefix(marker).trimAscii() else null

    private fun parseAttachments(raw: String): List<ParsedAttachment> {
        val openIdx = raw.indexOf(BodyMarker.ATTACHMENTS_OPEN)
        if (openIdx < 0) return emptyList()
        val afterOpen = openIdx + BodyMarker.ATTACHMENTS_OPEN.length
        val closeIdx = raw.indexOf(BodyMarker.ATTACHMENTS_CLOSE, afterOpen)
        val block = if (closeIdx < 0) raw.substring(afterOpen) else raw.substring(afterOpen, closeIdx)

        val results = mutableListOf<ParsedAttachment>()
        for (rawLine in block.split("\n")) {
            parseAttachmentLine(rawLine.trimAscii())?.let { results.add(it) }
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
        val rest = afterName.substring(urlEnd + 1).trimAscii()

        var mime: String? = null
        var size: Long? = null
        if (rest.startsWith("—")) {
            val suffix = rest.removePrefix("—").trimAscii()
            val parts = suffix.split(",", limit = 2).map { it.trimAscii() }
            mime = parts[0].takeIf { it.isNotEmpty() }
            if (parts.size > 1) size = parseHumanByteCount(parts[1])
        }
        val resolvedMime = mime ?: inferMimeFromUrl(url)
        return ParsedAttachment(filename, resolvedMime, url, size)
    }

    internal fun parseHumanByteCount(s: String): Long? {
        // Split on the FIRST ASCII space: magnitude is the substring before it,
        // unit is everything after with the canonical ASCII whitespace trimmed from
        // both ends (so `4000  KB` collapses to magnitude `4000`, unit `KB`).
        // Deliberately NOT `split(limit = 2)`, which leaves the extra leading space
        // on the unit field and silently demotes `4000  KB` to bytes.
        val sp = s.indexOf(' ')
        // Trim the magnitude too (per the spec: the decimal grammar is checked
        // *after* canonical trimming), so e.g. "4\t KB" matches like TS does.
        val numStr = (if (sp < 0) s else s.substring(0, sp)).trimAscii()
        val unit = if (sp < 0) "B" else s.substring(sp + 1).trimAscii().uppercase()
        if (numStr.isEmpty()) return null
        // Reject any non-decimal token before native parsing (the JVM's `Double`
        // would otherwise accept hex-float forms and diverge from Swift/TS).
        if (!DECIMAL_MAGNITUDE.matches(numStr)) return null
        val num = numStr.toDoubleOrNull() ?: return null
        if (!num.isFinite()) return null
        val mult = when (unit) {
            "KB" -> 1_000.0
            "MB" -> 1_000_000.0
            "GB" -> 1_000_000_000.0
            else -> 1.0   // BYTES, B, or unknown -> bytes
        }
        val scaled = num * mult
        if (!scaled.isFinite() || scaled < 0 || scaled > 100_000_000_000_000.0) return null
        return scaled.toLong()
    }

    /** Best-effort MIME from a URL's file extension, using the fixed, canonical
     *  extension→MIME table from the wire-format spec — identical across the
     *  Swift and TypeScript ports (no platform type database). Used when the body
     *  line omits the MIME. */
    internal fun inferMimeFromUrl(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
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
