package com.appfeedback.core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Posts a report to an adopter-operated relay (per the relay contract). The
 *  GitHub credential lives on the relay, never in the app. */
class RelayTransport(
    private val endpoint: String,
    private val httpClient: HttpClient = defaultClient(),
    private val captchaTokenProvider: (suspend () -> String?)? = null,
) : FeedbackTransport {

    @Serializable private data class RelayPayload(
        val type: String,
        val title: String,
        val description: String,
        val contactEmail: String?,
        val extraFields: Map<String, String>,
        val deviceInfo: DeviceInfo,
        val captchaToken: String?,
    )
    @Serializable private data class RelayResult(val issueNumber: Int, val issueUrl: String? = null)

    override suspend fun submit(report: FeedbackReport, deviceInfo: DeviceInfo): Int {
        val payload = RelayPayload(
            type = report.type.rawValue,
            title = report.title,
            description = report.description,
            contactEmail = report.contactEmail,
            extraFields = report.extraFields,
            deviceInfo = deviceInfo,
            captchaToken = captchaTokenProvider?.invoke(),
        )
        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            throw FeedbackSubmissionError("Relay responded ${response.status.value}", response.status.value)
        }
        return response.body<RelayResult>().issueNumber
    }

    companion object {
        internal fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }
}
