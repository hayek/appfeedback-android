package com.appfeedback.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IssueBodyFormatterTest {
    private val device = DeviceInfo("A", "1", "1", "M", "macOS", "Version 15.1")

    @Test fun labels() {
        assertEquals(listOf("bug", "user-submitted"), IssueBodyFormatter.labels(FeedbackType.BUG))
        assertEquals(listOf("feature-request", "user-submitted"), IssueBodyFormatter.labels(FeedbackType.FEATURE_REQUEST))
    }

    @Test fun minimal_body() {
        val report = FeedbackReport(FeedbackType.BUG, "t", "Desc")
        assertEquals(
            "Desc\n\n---\n**Device Information:**\nApp: A\nApp Version: 1 (1)\nDevice: M\nmacOS Version: Version 15.1\n\n---\n👍 Votes: 0",
            IssueBodyFormatter.format(report, device)
        )
    }

    @Test fun extra_fields_codepoint_order() {
        val report = FeedbackReport(FeedbackType.BUG, "t", "Desc",
            extraFields = mapOf("Zeta" to "z", "alpha" to "a", "Beta" to "b"))
        val body = IssueBodyFormatter.format(report, device)
        assertTrue(body.indexOf("**Beta:**") < body.indexOf("**Zeta:**"))
        assertTrue(body.indexOf("**Zeta:**") < body.indexOf("**alpha:**"))
    }

    @Test fun prefix_key_orders_first() {
        val report = FeedbackReport(FeedbackType.BUG, "t", "Desc",
            extraFields = mapOf("ab" to "2", "a" to "1"))
        val body = IssueBodyFormatter.format(report, device)
        assertTrue(body.indexOf("**a:**") < body.indexOf("**ab:**"))
    }
}
