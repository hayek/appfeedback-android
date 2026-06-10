package com.appfeedback.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// Per-type accents matching the Apple sheet's defaults (bug = red, feature = indigo).
private val BugAccent = Color(0xFFF24D66)
private val FeatureAccent = Color(0xFF7373FF)

private data class TypeStyle(
  val accent: Color,
  val icon: ImageVector,
  val label: String,
  val tagline: String,
  val subtitle: String,
)

private fun styleFor(type: FeedbackType): TypeStyle = when (type) {
  FeedbackType.BUG -> TypeStyle(
    BugAccent, Icons.Filled.Warning, "Bug",
    "Isn't working", "Report an issue you've encountered",
  )
  FeedbackType.FEATURE_REQUEST -> TypeStyle(
    FeatureAccent, Icons.Filled.Star, "Feature",
    "Idea or improvement", "Suggest a new feature or improvement",
  )
}

/** A drop-in Compose feedback form, mirroring the Apple sheet: a hero header,
 *  card-based type selector, labelled fields, a privacy notice, an accent submit
 *  button, and a success screen. Themed by the ambient MaterialTheme; the per-type
 *  accent (bug = red, feature = indigo) drives the hero, cards, and button.
 *
 *  Host it in your own container (e.g. a `ModalBottomSheet`); `onDone` fires when
 *  the user taps **Done** on the success screen so the host can dismiss. */
@Composable
fun FeedbackSheet(
  client: FeedbackClient,
  modifier: Modifier = Modifier,
  defaultType: FeedbackType = FeedbackType.BUG,
  onSubmitted: (Int) -> Unit = {},
  onError: (Throwable) -> Unit = {},
  onDone: () -> Unit = {},
) {
  var type by remember { mutableStateOf(defaultType) }
  var title by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var state by remember { mutableStateOf<FeedbackSheetState>(FeedbackSheetState.Idle) }
  val scope = rememberCoroutineScope()

  val style = styleFor(type)
  val accent = style.accent
  val heroGradient = Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.55f)))

  (state as? FeedbackSheetState.Success)?.let { done ->
    SuccessView(gradient = heroGradient, accent = accent, issueNumber = done.issueNumber, onDone = onDone, modifier = modifier)
    return
  }

  val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
  val submitting = state == FeedbackSheetState.Submitting

  Column(
    modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    // ---- Hero ----
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      Box(Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(heroGradient), contentAlignment = Alignment.Center) {
        Icon(style.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
      }
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Send Feedback", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(style.subtitle, fontSize = 13.sp, color = onSurfaceVariant)
      }
    }

    // ---- Type selector ----
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      TypeCard(FeedbackType.BUG, selected = type == FeedbackType.BUG, modifier = Modifier.weight(1f)) { type = FeedbackType.BUG }
      TypeCard(FeedbackType.FEATURE_REQUEST, selected = type == FeedbackType.FEATURE_REQUEST, modifier = Modifier.weight(1f)) { type = FeedbackType.FEATURE_REQUEST }
    }

    // ---- Title ----
    FieldSection(label = "TITLE") {
      AfbTextField(
        value = title,
        onValueChange = { title = it; if (state == FeedbackSheetState.Invalid) state = FeedbackSheetState.Idle },
        placeholder = "Brief summary",
        accent = accent,
        singleLine = true,
        tag = "afb-title",
      )
    }

    // ---- Description ----
    FieldSection(label = "DESCRIPTION") {
      AfbTextField(
        value = description,
        onValueChange = { description = it; if (state == FeedbackSheetState.Invalid) state = FeedbackSheetState.Idle },
        placeholder = "Describe what happened, what you expected, and how to reproduce.",
        accent = accent,
        singleLine = false,
        minHeight = 120.dp,
        tag = "afb-description",
      )
    }

    // ---- Email (optional) ----
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("EMAIL")
        Box(
          Modifier.clip(RoundedCornerShape(50)).background(onSurfaceVariant.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 2.dp),
        ) { Text("OPTIONAL", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, color = onSurfaceVariant) }
      }
      AfbTextField(
        value = email,
        onValueChange = { email = it },
        placeholder = "email@example.com",
        accent = accent,
        singleLine = true,
        keyboardType = KeyboardType.Email,
      )
      Text("So we can reach out for more details", fontSize = 11.sp, color = onSurfaceVariant)
    }

    // ---- Privacy notice ----
    Row(
      modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(onSurfaceVariant.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(Icons.Filled.Info, contentDescription = null, tint = onSurfaceVariant, modifier = Modifier.size(15.dp))
      Text("Device information will be automatically included", fontSize = 11.sp, color = onSurfaceVariant)
    }

    // ---- Validation ----
    if (state == FeedbackSheetState.Invalid) {
      Text("Please add a summary and a description.", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = BugAccent)
    }
    if (state is FeedbackSheetState.Error) {
      Text("Something went wrong. Please try again.", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = BugAccent)
    }

    // ---- Submit ----
    Button(
      onClick = {
        if (title.isBlank() || description.isBlank()) { state = FeedbackSheetState.Invalid; return@Button }
        state = FeedbackSheetState.Submitting
        scope.launch {
          try {
            val n = client.submit(FeedbackReport(type, title.trim(), description.trim(), email.trim().ifBlank { null }))
            state = FeedbackSheetState.Success(n); onSubmitted(n)
          } catch (e: Throwable) {
            state = FeedbackSheetState.Error(e); onError(e)
          }
        }
      },
      enabled = !submitting,
      colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White, disabledContainerColor = accent.copy(alpha = 0.6f), disabledContentColor = Color.White),
      shape = RoundedCornerShape(28.dp),
      modifier = Modifier.fillMaxWidth().height(54.dp),
    ) {
      if (submitting) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
        Spacer(Modifier.size(10.dp))
        Text("Sending…", fontWeight = FontWeight.SemiBold)
      } else {
        Text("Submit", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
      }
    }
  }
}

@Composable
private fun TypeCard(type: FeedbackType, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val s = styleFor(type)
  val borderColor = if (selected) s.accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant
  val bg = if (selected) s.accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(bg)
      .border(if (selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(11.dp),
  ) {
    Box(
      Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(if (selected) s.accent else s.accent.copy(alpha = 0.12f)),
      contentAlignment = Alignment.Center,
    ) { Icon(s.icon, contentDescription = null, tint = if (selected) Color.White else s.accent, modifier = Modifier.size(16.dp)) }
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
      Text(s.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
      Text(s.tagline, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FieldSection(label: String, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionLabel(label)
    content()
  }
}

@Composable
private fun AfbTextField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  accent: Color,
  singleLine: Boolean,
  minHeight: androidx.compose.ui.unit.Dp = 0.dp,
  keyboardType: KeyboardType = KeyboardType.Text,
  tag: String = "",
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
    singleLine = singleLine,
    shape = RoundedCornerShape(10.dp),
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    colors = OutlinedTextFieldDefaults.colors(
      focusedBorderColor = accent,
      unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
      cursorColor = accent,
    ),
    modifier = Modifier.fillMaxWidth()
      .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)
      .then(if (minHeight > 0.dp) Modifier.heightIn(min = minHeight) else Modifier),
  )
}

@Composable
private fun SuccessView(gradient: Brush, accent: Color, issueNumber: Int, onDone: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(130.dp)) {
      Box(Modifier.size(130.dp).border(2.dp, accent.copy(alpha = 0.4f), CircleShape))
      Box(Modifier.size(96.dp).clip(CircleShape).background(gradient), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
      }
    }
    Text("Thanks!", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    Text(
      "Your feedback was submitted. We'll take a look soon.",
      fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
    )
    Box(
      Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Text("Issue #$issueNumber", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Spacer(Modifier.height(4.dp))
    Button(
      onClick = onDone,
      colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
      shape = RoundedCornerShape(28.dp),
      modifier = Modifier.fillMaxWidth().height(54.dp),
    ) { Text("Done", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
  }
}
