package com.appfeedback.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedbackTransportTest {
    @Test fun fake_transport_returns_identifier() = runBlocking {
        val fake = object : FeedbackTransport {
            override suspend fun submit(report: FeedbackReport, deviceInfo: DeviceInfo): Int = 42
        }
        val id = fake.submit(
            FeedbackReport(FeedbackType.BUG, "t", "d"),
            DeviceInfo("A", "1", "1", "M", "Android", "14"),
        )
        assertEquals(42, id)
    }
}
