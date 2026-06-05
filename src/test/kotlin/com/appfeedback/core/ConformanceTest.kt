package com.appfeedback.core

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Runs the canonical appfeedback-spec golden fixtures (vendored under
 *  resources/conformance) through the Kotlin formatter and parser. This is the
 *  cross-language gate: identical corpus to the Swift (and future TS) SDKs. */
class ConformanceTest {
    private val gson = Gson()

    private data class Corpus<T>(val version: Int, val cases: List<T>)
    private data class ReportFx(val type: String, val title: String, val description: String,
                                val contactEmail: String?, val extraFields: Map<String, String>?)
    private data class DeviceFx(val appName: String, val appVersion: String, val buildNumber: String,
                                val model: String, val osName: String, val osVersion: String)
    private data class UploadedFx(val filename: String, val mimeType: String, val sizeBytes: Int, val url: String)
    private data class FormatCase(val name: String, val report: ReportFx, val deviceInfo: DeviceFx,
                                  val uploaded: List<UploadedFx>?, val expectedBody: String, val expectedLabels: List<String>?)
    private data class AttachmentExp(val filename: String, val mimeType: String, val url: String, val sizeBytes: Int?)
    private data class ParsedExp(val description: String, val appName: String?, val appVersion: String?,
                                 val device: String?, val osVersion: String?, val email: String?, val attachments: List<AttachmentExp>?)
    private data class ParseCase(val name: String, val body: String, val expected: ParsedExp)

    private fun load(resource: String): String =
        (javaClass.getResourceAsStream("/conformance/$resource.json") ?: fail("missing $resource.json"))
            .bufferedReader().use { it.readText() }

    @Test fun format_golden_fixtures() {
        val type = object : TypeToken<Corpus<FormatCase>>() {}.type
        val corpus: Corpus<FormatCase> = gson.fromJson(load("format-cases"), type)
        assertTrue(corpus.cases.isNotEmpty(), "no format fixtures")
        for (c in corpus.cases) {
            val fType = FeedbackType.fromRawValue(c.report.type) ?: fail("unknown type in ${c.name}")
            val report = FeedbackReport(fType, c.report.title, c.report.description,
                c.report.contactEmail, c.report.extraFields ?: emptyMap())
            val device = DeviceInfo(c.deviceInfo.appName, c.deviceInfo.appVersion, c.deviceInfo.buildNumber,
                c.deviceInfo.model, c.deviceInfo.osName, c.deviceInfo.osVersion)
            val uploaded = (c.uploaded ?: emptyList()).map {
                UploadedAttachment(it.filename, it.mimeType, it.sizeBytes, it.url)
            }
            assertEquals(c.expectedBody, IssueBodyFormatter.format(report, device, uploaded), "format mismatch in '${c.name}'")
            c.expectedLabels?.let { assertEquals(it, IssueBodyFormatter.labels(fType), "labels mismatch in '${c.name}'") }
        }
    }

    @Test fun parse_golden_fixtures() {
        val type = object : TypeToken<Corpus<ParseCase>>() {}.type
        val corpus: Corpus<ParseCase> = gson.fromJson(load("parse-cases"), type)
        assertTrue(corpus.cases.isNotEmpty(), "no parse fixtures")
        for (c in corpus.cases) {
            val p = IssueBodyParser.parse(c.body)
            assertEquals(c.expected.description, p.description, "description in '${c.name}'")
            assertEquals(c.expected.appName, p.appName, "appName in '${c.name}'")
            assertEquals(c.expected.appVersion, p.appVersion, "appVersion in '${c.name}'")
            assertEquals(c.expected.device, p.device, "device in '${c.name}'")
            assertEquals(c.expected.osVersion, p.osVersion, "osVersion in '${c.name}'")
            assertEquals(c.expected.email, p.email, "email in '${c.name}'")
            val exp = c.expected.attachments ?: emptyList()
            assertEquals(exp.size, p.attachments.size, "attachment count in '${c.name}'")
            exp.forEachIndexed { i, ea ->
                assertEquals(ea.filename, p.attachments[i].filename, "att filename in '${c.name}'[$i]")
                assertEquals(ea.mimeType, p.attachments[i].mimeType, "att mime in '${c.name}'[$i]")
                assertEquals(ea.url, p.attachments[i].url, "att url in '${c.name}'[$i]")
                assertEquals(ea.sizeBytes, p.attachments[i].sizeBytes, "att size in '${c.name}'[$i]")
            }
        }
    }
}
