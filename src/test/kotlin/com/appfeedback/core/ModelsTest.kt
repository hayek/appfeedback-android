package com.appfeedback.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelsTest {
    @Test fun feedback_type_raw_values() {
        assertEquals("bug", FeedbackType.BUG.rawValue)
        assertEquals("feature-request", FeedbackType.FEATURE_REQUEST.rawValue)
        assertEquals(FeedbackType.FEATURE_REQUEST, FeedbackType.fromRawValue("feature-request"))
        assertNull(FeedbackType.fromRawValue("nope"))
    }

    @Test fun device_info_renders_block() {
        val d = DeviceInfo("Acme", "1.2.3", "456", "iPhone15,2", "iOS", "Version 18.2 (Build 22C150)")
        assertEquals(
            "App: Acme\nApp Version: 1.2.3 (456)\nDevice: iPhone15,2\niOS Version: Version 18.2 (Build 22C150)",
            d.renderForIssueBody()
        )
    }
}
