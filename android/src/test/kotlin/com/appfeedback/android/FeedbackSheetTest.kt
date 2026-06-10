package com.appfeedback.android

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.appfeedback.core.DeviceInfo
import com.appfeedback.core.FeedbackClient
import com.appfeedback.core.FeedbackReport
import com.appfeedback.core.FeedbackTransport
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedbackSheetTest {
  @get:Rule val rule = createComposeRule()

  private class CapturingTransport : FeedbackTransport {
    var report: FeedbackReport? = null
    override suspend fun submit(report: FeedbackReport, deviceInfo: DeviceInfo): Int { this.report = report; return 99 }
  }

  @Test fun submitting_calls_the_transport_and_shows_success() {
    val transport = CapturingTransport()
    val client = FeedbackClient(transport) { DeviceInfo("A", "1", "1", "Pixel", "Android", "14") }
    rule.setContent { FeedbackSheet(client) }

    rule.onNodeWithTag("afb-title").performTextInput("Crash")
    rule.onNodeWithTag("afb-description").performTextInput("It broke")
    // The form scrolls; bring the submit button on-screen so the tap lands.
    rule.onNodeWithText("Submit").performScrollTo().performClick()
    rule.waitForIdle()

    assertEquals("Crash", transport.report?.title)
    assertEquals("It broke", transport.report?.description)
    // The success screen replaces the form once the (async) submit resolves.
    rule.waitUntil(timeoutMillis = 3_000) {
      rule.onAllNodesWithText("Thanks!").fetchSemanticsNodes().isNotEmpty()
    }
    rule.onNodeWithText("Thanks!").assertExists()
  }
}
