package com.mohammedsharukms.mypersonaltracker.ui.today

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mohammedsharukms.mypersonaltracker.domain.durationText
import com.mohammedsharukms.mypersonaltracker.focus.FocusTimer
import com.mohammedsharukms.mypersonaltracker.ui.components.ChipRow
import com.mohammedsharukms.mypersonaltracker.ui.theme.TrackerColors
import kotlinx.coroutines.delay

/** Start a stopwatch on an activity; stopping it adds the time to today's log. */
@Composable
fun FocusCard(
    running: FocusTimer.Running?,
    suggestions: List<String>,
    onStart: (String) -> Unit,
    onStop: (onLogged: (String) -> Unit) -> Unit,
    onCancel: () -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    var lastLogged by remember { mutableStateOf<String?>(null) }
    // Ask for notification permission so the timer can show in the notification shade.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LabeledCard("Focus") {
        if (running != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = TrackerColors.Flame)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(running.activity, style = MaterialTheme.typography.titleMedium)
                    ElapsedClock(running.startedAt.toEpochMilli())
                }
                IconButton(onClick = onCancel) { Icon(Icons.Filled.Cancel, contentDescription = "Cancel timer", tint = MaterialTheme.colorScheme.outline) }
                Button(onClick = { onStop { lastLogged = it } }, colors = ButtonDefaults.buttonColors(containerColor = TrackerColors.Success)) {
                    Text("Stop")
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable {
                    lastLogged = null
                    if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    choosing = true
                },
            ) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Start focus timer", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    val footer = when {
        lastLogged != null -> "Logged $lastLogged ✓"
        running != null -> "Keeps running in your notifications. Stop to add the time to today."
        else -> null
    }
    footer?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
    }

    if (choosing) {
        FocusStartSheet(suggestions, onStart = { onStart(it); choosing = false }, onDismiss = { choosing = false })
    }
}

@Composable
private fun ElapsedClock(startMillis: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val seconds = ((now - startMillis) / 1000).coerceAtLeast(0)
    val text = if (seconds >= 3600) {
        "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
    } else {
        "%d:%02d".format(seconds / 60, seconds % 60)
    }
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusStartSheet(suggestions: List<String>, onStart: (String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Text("Start Focus", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                TextButton(onClick = { onStart(title.trim()) }, enabled = title.isNotBlank()) { Text("Start", fontWeight = FontWeight.Bold) }
            }
            Text("What are you focusing on?", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("e.g. DSA, Compose") }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus))
            if (suggestions.isNotEmpty()) {
                ChipRow(suggestions, isSelected = { it.equals(title.trim(), ignoreCase = true) }, onTap = { title = it })
            }
        }
    }
}

/** "30m of DSA" */
fun loggedLabel(minutes: Int, title: String) = "${minutes.durationText} of $title"

