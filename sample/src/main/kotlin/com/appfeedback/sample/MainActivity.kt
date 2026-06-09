package com.appfeedback.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.appfeedback.android.FeedbackSheet
import com.appfeedback.android.androidFeedbackClient
import com.appfeedback.core.DeviceInfo
import com.appfeedback.core.FeedbackReport
import com.appfeedback.core.FeedbackTransport
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * A mock transport so the sample runs with no GitHub token: it simulates network
 * latency and returns a fake issue number. In a real app, swap this for
 * `GitHubDirectTransport(owner, repo, token)` or `RelayTransport(endpoint)`.
 */
private class MockTransport : FeedbackTransport {
  override suspend fun submit(report: FeedbackReport, deviceInfo: DeviceInfo): Int {
    delay(700)
    return 100 + Random.nextInt(900)
  }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { MaterialTheme { SampleApp() } }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleApp() {
  // Build the client once; androidFeedbackClient fills device info from android.os.Build.
  val client = remember {
    androidFeedbackClient(
      transport = MockTransport(),
      appName = "AppFeedback Sample",
      appVersion = "0.1.0",
    )
  }
  var showSheet by remember { mutableStateOf(false) }
  var lastIssue by remember { mutableStateOf<Int?>(null) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  Scaffold { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text("AppFeedback for Android", style = MaterialTheme.typography.headlineSmall)
      Spacer(Modifier.height(8.dp))
      Text(
        "Tap below to open the drop-in FeedbackSheet. Submissions are mocked — no real GitHub call.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(24.dp))
      Button(onClick = { showSheet = true }) { Text("Send feedback") }
      lastIssue?.let {
        Spacer(Modifier.height(16.dp))
        Text("✓ Created issue #$it", style = MaterialTheme.typography.bodyLarge)
      }
    }
  }

  if (showSheet) {
    ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
      FeedbackSheet(
        client = client,
        onSubmitted = { n -> lastIssue = n; showSheet = false },
      )
    }
  }
}

/** Static preview of the form (Android Studio). Submission is inert at preview time. */
@Preview(showBackground = true)
@Composable
private fun FeedbackSheetPreview() {
  val previewClient = remember {
    androidFeedbackClient(
      transport = object : FeedbackTransport {
        override suspend fun submit(report: FeedbackReport, deviceInfo: DeviceInfo): Int = 1
      },
      appName = "Preview",
      appVersion = "1.0",
    )
  }
  MaterialTheme { FeedbackSheet(client = previewClient) }
}
