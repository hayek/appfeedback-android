package com.appfeedback.core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Posts a report directly to the GitHub Issues REST API. The token is held by
 *  the caller (acceptable for a native app's secure storage). */
class GitHubDirectTransport(
    private val owner: String,
    private val repo: String,
    private val token: String,
    private val httpClient: HttpClient = defaultClient(),
) : FeedbackTransport {

    @Serializable private data class CreateIssueRequest(val title: String, val body: String, val labels: List<String>)
    @Serializable private data class IssueResponse(val number: Int)

    override suspend fun submit(report: FeedbackReport, deviceInfo: DeviceInfo): Int {
        val url = "https://api.github.com/repos/${owner.encodeURLPathPart()}/${repo.encodeURLPathPart()}/issues"
        val payload = CreateIssueRequest(
            title = report.title,
            body = IssueBodyFormatter.format(report, deviceInfo),
            labels = IssueBodyFormatter.labels(report.type),
        )
        val response = httpClient.post(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            throw FeedbackSubmissionError("GitHub responded ${response.status.value}", response.status.value)
        }
        return response.body<IssueResponse>().number
    }

    companion object {
        internal fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }
}
