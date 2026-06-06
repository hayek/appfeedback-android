package com.appfeedback.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TransportsTest {
    private val device = DeviceInfo("Acme", "1.0", "1", "Pixel 8", "Android", "14")
    private val report = FeedbackReport(FeedbackType.BUG, "Crash", "boom", "me@x.com", mapOf("k" to "v"))

    private fun client(status: HttpStatusCode, body: String, capture: (HttpRequestData) -> Unit = {}) =
        HttpClient(MockEngine { req -> capture(req); respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    private fun readBodyBytes(req: HttpRequestData): ByteArray {
        val content = req.body
        return when (content) {
            is io.ktor.http.content.OutgoingContent.ByteArrayContent -> content.bytes()
            is io.ktor.http.content.OutgoingContent.ReadChannelContent ->
                runBlocking { content.readFrom().toByteArray() }
            else -> error("Unexpected OutgoingContent type: ${content::class}")
        }
    }

    @Test fun github_transport_posts_issue_and_returns_number() = runBlocking {
        var seen: HttpRequestData? = null
        val t = GitHubDirectTransport("acme", "feedback", "ghp_x", client(HttpStatusCode.Created, """{"number":77}""") { seen = it })
        assertEquals(77, t.submit(report, device))
        assertEquals("https://api.github.com/repos/acme/feedback/issues", seen!!.url.toString())
        assertEquals("Bearer ghp_x", seen!!.headers[HttpHeaders.Authorization])
        val sent = String(readBodyBytes(seen!!))
        assertTrue(sent.contains("\"labels\":[\"bug\",\"user-submitted\"]"))
        assertTrue(sent.contains("Device Information"))
    }

    @Test fun github_transport_throws_on_error() {
        val t = GitHubDirectTransport("o", "r", "t", client(HttpStatusCode.Unauthorized, "nope"))
        val e = assertFailsWith<FeedbackSubmissionError> { runBlocking { t.submit(report, device) } }
        assertEquals(401, e.status)
    }

    @Test fun relay_transport_posts_payload_and_returns_number() = runBlocking {
        var seen: HttpRequestData? = null
        val t = RelayTransport("https://relay.example/api", client(HttpStatusCode.OK, """{"issueNumber":9,"issueUrl":"u"}""") { seen = it })
        assertEquals(9, t.submit(report, device))
        val sent = String(readBodyBytes(seen!!))
        assertTrue(sent.contains("\"type\":\"bug\""))
        assertTrue(sent.contains("\"osName\":\"Android\""))
    }

    @Test fun feedback_client_threads_device_info() = runBlocking {
        val fake = object : FeedbackTransport { override suspend fun submit(r: FeedbackReport, d: DeviceInfo) = 42 }
        val client = FeedbackClient(fake) { device }
        assertEquals(42, client.submit(report))
    }
}
