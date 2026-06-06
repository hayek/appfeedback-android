package com.appfeedback.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.appfeedback.core.FeedbackClient
import com.appfeedback.core.FeedbackReport
import com.appfeedback.core.FeedbackType
import kotlinx.coroutines.launch

/** State of an in-flight submission, surfaced to tests + the status line. */
sealed interface FeedbackSheetState {
  data object Idle : FeedbackSheetState
  data object Submitting : FeedbackSheetState
  data object Invalid : FeedbackSheetState
  data class Success(val issueNumber: Int) : FeedbackSheetState
  data class Error(val cause: Throwable) : FeedbackSheetState
}

/** A drop-in Compose feedback form mirroring the Apple sheet / web widget:
 *  type toggle, summary, description, optional email, submit, status. Themed by
 *  the ambient MaterialTheme. */
@Composable
fun FeedbackSheet(
  client: FeedbackClient,
  modifier: Modifier = Modifier,
  defaultType: FeedbackType = FeedbackType.BUG,
  onSubmitted: (Int) -> Unit = {},
  onError: (Throwable) -> Unit = {},
) {
  var type by remember { mutableStateOf(defaultType) }
  var title by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var state by remember { mutableStateOf<FeedbackSheetState>(FeedbackSheetState.Idle) }
  val scope = rememberCoroutineScope()

  Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(selected = type == FeedbackType.BUG, onClick = { type = FeedbackType.BUG }, label = { Text("Bug") })
      FilterChip(selected = type == FeedbackType.FEATURE_REQUEST, onClick = { type = FeedbackType.FEATURE_REQUEST }, label = { Text("Feature request") })
    }
    OutlinedTextField(title, { title = it }, label = { Text("Summary") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(description, { description = it }, label = { Text("What happened?") }, modifier = Modifier.fillMaxWidth().height(120.dp))
    OutlinedTextField(email, { email = it }, label = { Text("Email (optional)") }, singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
    Button(
      onClick = {
        if (title.isBlank() || description.isBlank()) { state = FeedbackSheetState.Invalid; return@Button }
        state = FeedbackSheetState.Submitting
        scope.launch {
          try {
            val n = client.submit(FeedbackReport(type, title.trim(), description.trim(), email.trim().ifBlank { null }))
            state = FeedbackSheetState.Success(n); onSubmitted(n)
          } catch (e: Throwable) { state = FeedbackSheetState.Error(e); onError(e) }
        }
      },
      enabled = state != FeedbackSheetState.Submitting,
      modifier = Modifier.fillMaxWidth(),
    ) { Text(if (state == FeedbackSheetState.Submitting) "Sending…" else "Send feedback") }

    when (val s = state) {
      FeedbackSheetState.Invalid -> Text("Please add a summary and a description.")
      is FeedbackSheetState.Success -> Text("Thanks for the feedback!")
      is FeedbackSheetState.Error -> Text("Something went wrong. Please try again.")
      else -> {}
    }
  }
}
