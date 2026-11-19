package com.mohammedsharukms.mypersonaltracker.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.data.DayWithSessions
import com.mohammedsharukms.mypersonaltracker.data.StudySession
import com.mohammedsharukms.mypersonaltracker.domain.Mood
import com.mohammedsharukms.mypersonaltracker.domain.durationText
import com.mohammedsharukms.mypersonaltracker.ui.TrackerViewModel
import com.mohammedsharukms.mypersonaltracker.ui.components.ChipRow
import com.mohammedsharukms.mypersonaltracker.ui.components.SectionCard
import com.mohammedsharukms.mypersonaltracker.ui.theme.TrackerColors

/** Edits one day's log in place (Room writes happen as you go, like SwiftData autosave). */
fun LazyListScope.dailyLogEditor(day: DayWithSessions, vm: TrackerViewModel, onAddTime: () -> Unit, onEditSession: (StudySession) -> Unit) {
    val log = day.log
    item(key = "showedUp") {
        Column {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (log.showedUp) Icons.Filled.LocalFireDepartment else Icons.Outlined.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (log.showedUp) TrackerColors.Flame else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("I showed up", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = log.showedUp, onCheckedChange = { vm.updateLog(log.copy(showedUp = it)) })
                }
            }
            Text(
                "At least one focused session of study or practice counts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            )
        }
    }
    item(key = "time") {
        Column {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("Time spent", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (day.totalMinutes > 0) {
                    Text("Total ${day.totalMinutes.durationText}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    day.sortedSessions.forEach { session ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onEditSession(session) }.padding(vertical = 8.dp),
                        ) {
                            Text(session.title, modifier = Modifier.weight(1f))
                            Text(session.minutes.durationText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onAddTime).padding(vertical = 8.dp),
                    ) {
                        Icon(Icons.Filled.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Add time", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
    item(key = "mood") {
        LabeledCard("Mood") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Mood.range.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = log.mood == value,
                        onClick = { vm.updateLog(log.copy(mood = value)) },
                        shape = SegmentedButtonDefaults.itemShape(index, Mood.range.count()),
                        icon = {},
                        label = { Text(Mood.emoji(value), fontSize = 20.sp) },
                    )
                }
            }
        }
    }
    item(key = "did-${log.id}") {
        NotesField("What I did", log.whatIDid, "Solved two tree problems, built a Compose screen…") { vm.updateLog(log.copy(whatIDid = it)) }
    }
    item(key = "learned-${log.id}") {
        NotesField("What I learned", log.whatILearned, "Coroutines are cancelled cooperatively…") { vm.updateLog(log.copy(whatILearned = it)) }
    }
}

@Composable
fun LabeledCard(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        SectionCard { content() }
    }
}

/** Keeps its own text while typing so the cursor doesn't jump when Room re-emits. */
@Composable
private fun NotesField(title: String, saved: String, placeholder: String, onSave: (String) -> Unit) {
    var text by rememberSaveable(title) { mutableStateOf(saved) }
    LabeledCard(title) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onSave(it) },
            placeholder = { Text(placeholder) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        )
    }
}

/** Adds time spent on something (`session == null`), or edits an existing entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditorSheet(
    session: StudySession?,
    suggestions: List<String>,
    onSave: (title: String, minutes: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(session?.title.orEmpty()) }
    var minutes by remember { mutableIntStateOf(session?.minutes ?: 30) }
    val focus = remember { FocusRequester() }
    val presets = listOf(15, 30, 45, 60, 90, 120, 180)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Text(
                    if (session == null) "Add Time" else "Edit Time",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                TextButton(onClick = { onSave(title.trim(), minutes); onDismiss() }, enabled = title.isNotBlank()) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
            Text("What did you work on?", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("e.g. DSA, Compose, Reading") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (suggestions.isNotEmpty()) {
                ChipRow(suggestions, isSelected = { it.equals(title.trim(), ignoreCase = true) }, onTap = { title = it })
            }
            Text("How long?", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(minutes.durationText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { minutes = (minutes - 5).coerceAtLeast(5) }) { Text("−5m") }
                TextButton(onClick = { minutes = (minutes + 5).coerceAtMost(16 * 60) }) { Text("+5m") }
            }
            ChipRow(presets.map { it.durationText }, isSelected = { it == minutes.durationText }, onTap = { text ->
                presets.firstOrNull { it.durationText == text }?.let { minutes = it }
            })
            if (onDelete != null) {
                TextButton(onClick = { onDelete(); onDismiss() }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Entry", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (session == null) runCatching { focus.requestFocus() } }
}

