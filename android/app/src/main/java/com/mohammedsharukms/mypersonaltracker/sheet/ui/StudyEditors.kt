package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.container
import com.mohammedsharukms.mypersonaltracker.data.TopicStatus
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.StudyData
import com.mohammedsharukms.mypersonaltracker.sheet.StudyEntry
import com.mohammedsharukms.mypersonaltracker.sheet.StudyFormat
import com.mohammedsharukms.mypersonaltracker.sheet.StudyGoal
import com.mohammedsharukms.mypersonaltracker.sheet.StudySchedule
import com.mohammedsharukms.mypersonaltracker.sheet.StudySubject
import com.mohammedsharukms.mypersonaltracker.sheet.StudyTopic
import com.mohammedsharukms.mypersonaltracker.sheet.currentTopic
import com.mohammedsharukms.mypersonaltracker.sheet.daysUntil
import com.mohammedsharukms.mypersonaltracker.sheet.progress
import com.mohammedsharukms.mypersonaltracker.sheet.sessionsOf
import com.mohammedsharukms.mypersonaltracker.sheet.studyData
import com.mohammedsharukms.mypersonaltracker.sheet.subject
import com.mohammedsharukms.mypersonaltracker.sheet.topic
import com.mohammedsharukms.mypersonaltracker.sheet.topicsOf
import com.mohammedsharukms.mypersonaltracker.sheet.pace
import com.mohammedsharukms.mypersonaltracker.sheet.testScores
import com.mohammedsharukms.mypersonaltracker.sheet.weekMinutes
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// One subject in full, and the forms: subject, concept, session, goal, a quick
// revision check, study targets, starting the timer, and bringing in the old
// on-phone Learning journal. Mirrors StudyEditors.swift.

private val confidenceLabels = listOf("Forgot", "Shaky", "Okay", "Good", "Solid")

@Composable
private fun confidenceTint(value: Int): Color = when {
    value <= 2 -> SheetTheme.bad
    value == 3 -> SheetTheme.warn
    else -> SheetTheme.good
}

fun stageIcon(stage: StudyTopic.Stage): ImageVector = when (stage) {
    StudyTopic.Stage.TO_LEARN -> Icons.Filled.RadioButtonUnchecked
    StudyTopic.Stage.LEARNING -> Icons.Filled.Contrast
    StudyTopic.Stage.REVISING -> Icons.Filled.Autorenew
    StudyTopic.Stage.MASTERED -> Icons.Filled.Verified
}

/** Five buttons: Forgot · Shaky · Okay · Good · Solid. */
@Composable
fun ConfidencePicker(value: Int?, onChange: (Int?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..5).forEach { i ->
            val selected = value == i
            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (selected) confidenceTint(i) else SheetTheme.input)
                .clickable { onChange(if (selected) null else i) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$i", fontWeight = FontWeight.Bold, color = if (selected) Color.White else SheetTheme.text)
                Text(confidenceLabels[i - 1], fontSize = 10.sp, color = if (selected) Color.White else SheetTheme.secondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ConfidenceDots(value: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { i -> Box(Modifier.size(6.dp).clip(CircleShape).background(if (i <= value) confidenceTint(value) else SheetTheme.input)) }
    }
}

/** A choice from a short list, as wrapping pills. */
@Composable
private fun <T> Choices(items: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item -> FilterPill(label(item), null, SheetTheme.navy, item == selected) { onSelect(item) } }
    }
}

/** A row showing a date (or "None"), opening a date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(title: String, date: LocalDate?, allowClear: Boolean, onChange: (LocalDate?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SheetTheme.card).clickable { picking = true }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, color = SheetTheme.text, modifier = Modifier.weight(1f))
        Text(date?.let { SheetDate.short(it.toString()) } ?: "None", fontWeight = FontWeight.SemiBold, color = if (date == null) SheetTheme.secondary else SheetTheme.navy)
        if (allowClear && date != null) {
            Spacer(Modifier.width(10.dp))
            Text("Clear", fontSize = 13.sp, color = SheetTheme.bad, modifier = Modifier.clickable { onChange(null) })
        }
    }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ picking = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/** A number typed into a small field. */
@Composable
internal fun NumberField(title: String, value: String, unit: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SheetTheme.card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, color = SheetTheme.text, modifier = Modifier.weight(1f))
        BasicTextField(value, onChange, singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), cursorBrush = SolidColor(SheetTheme.navy),
            modifier = Modifier.width(80.dp),
            decorationBox = { inner -> Box(contentAlignment = Alignment.CenterEnd) { if (value.isEmpty()) Text("0", color = SheetTheme.tertiary); inner() } })
        Spacer(Modifier.width(6.dp))
        Text(unit, color = SheetTheme.secondary)
    }
}

private fun date(text: String?): LocalDate? = SheetDate.date(text)

// MARK: - Subject detail

@Composable
fun SubjectDetailSheet(store: SheetStore, subjectId: String, onDismiss: () -> Unit) {
    val data = store.snapshot?.studyData ?: StudyData()
    val subject = data.subject(subjectId)
    val timer = LocalContext.current.container.studyTimer
    val uri = LocalUriHandler.current
    var open by remember { mutableStateOf<StudySheet?>(null) }
    var showMastered by remember { mutableStateOf(false) }
    EditorSheet(subject?.name ?: "Subject", onDismiss, saveLabel = "Edit", onSave = subject?.let { { open = StudySheet.Subject(it, false) } }) {
        if (subject == null) { Text("This subject was deleted.", color = SheetTheme.secondary); return@EditorSheet }
        val tint = studyColor(subject.color)
        val topics = data.topicsOf(subject.id)
        val sessions = data.sessionsOf(subject.id)
        SheetCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(data.progress(subject.id), 70.dp, 8.dp, tint, tint.copy(alpha = 0.15f)) {
                    Text(SheetFormat.percent(data.progress(subject.id), 0), fontWeight = FontWeight.Bold, color = SheetTheme.text)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(if (subject.status == StudySubject.Status.ACTIVE) "Studying" else subject.status.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tint)
                    Text("${topics.count { it.stage == StudyTopic.Stage.MASTERED }} of ${topics.size} concepts mastered", fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                    Text("${StudyFormat.minutes(sessions.sumOf { it.minutes })} in total · ${StudyFormat.minutes(data.weekMinutes(subjectId = subject.id))} this week",
                        fontSize = 12.sp, color = SheetTheme.secondary)
                }
            }
            subject.why?.let { Spacer(Modifier.height(10.dp)); KeyValueRow("Why", it) }
            subject.resource?.let { r ->
                if (r.startsWith("http")) LinkText(r, SheetTheme.navy, 14.sp) { runCatching { uri.openUri(r) } } else KeyValueRow("Using", r)
            }
            subject.weeklyHours?.takeIf { it > 0 }?.let { h ->
                val week = data.weekMinutes(subjectId = subject.id)
                Spacer(Modifier.height(10.dp))
                ProgressRow("This week", "${StudyFormat.minutes(week)} of ${StudyFormat.number(h)}h", week / (h * 60), tint)
            }
            subject.targetDate?.let { t -> daysUntil(t)?.let { d -> KeyValueRow("Target date", "${SheetDate.short(t)} · ${StudyFormat.countdown(d)}") } }
            data.pace(subject)?.let { PaceRow(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Study now", Modifier.weight(1f), enabled = timer.running == null) {
                timer.start(subject, topics.firstOrNull { it.stage == StudyTopic.Stage.LEARNING }); onDismiss()
            }
            PrimaryButton("Log time", Modifier.weight(1f)) { open = StudySheet.Session(StudyEntry(subjectId = subject.id, subject = subject.name), true) }
        }
        FormSection("Concepts", footer = "Tap the circle to move a concept on: To learn → Learning → Revising → Mastered.") {
            Text("+ Add a concept", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy,
                modifier = Modifier.fillMaxWidth().clickable { open = StudySheet.Topic(StudyTopic(subjectId = subject.id), true) }.padding(16.dp))
            RowDivider()
            Text("+ Paste a syllabus (many at once)", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy,
                modifier = Modifier.fillMaxWidth().clickable { open = StudySheet.Syllabus(subject.id) }.padding(16.dp))
            if (topics.isEmpty()) Text("List the concepts or chapters in ${subject.name} — e.g. “Closures”, “Binary search”, “Chapter 3: Tenses”.",
                fontSize = 13.sp, color = SheetTheme.secondary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
            listOf(StudyTopic.Stage.LEARNING, StudyTopic.Stage.REVISING, StudyTopic.Stage.TO_LEARN, StudyTopic.Stage.MASTERED).forEach { stage ->
                if (stage != StudyTopic.Stage.MASTERED || showMastered) topics.filter { it.stage == stage }.forEach { t ->
                    RowDivider()
                    ConceptRow(store, t) { open = StudySheet.Topic(t, false) }
                }
            }
            val mastered = topics.count { it.stage == StudyTopic.Stage.MASTERED }
            if (mastered > 0) {
                RowDivider()
                Text(if (showMastered) "Hide mastered" else "Show $mastered mastered", color = SheetTheme.navy,
                    modifier = Modifier.fillMaxWidth().clickable { showMastered = !showMastered }.padding(16.dp))
            }
        }
        FormSection("Goals") {
            data.goals.filter { it.subjectId == subject.id }.forEach { g ->
                Box(Modifier.padding(14.dp)) { GoalRow(g, data) { open = StudySheet.Goal(g, false) } }
                RowDivider()
            }
            Text("+ Add a goal", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy,
                modifier = Modifier.fillMaxWidth().clickable { open = StudySheet.Goal(StudyGoal(subjectId = subject.id), true) }.padding(16.dp))
        }
        val tests = data.testScores(subject.id)
        if (tests.isNotEmpty()) Field("Test scores") { TestScores(tests) }
        if (sessions.isNotEmpty()) FormSection("Sessions") {
            sessions.take(15).forEachIndexed { i, e ->
                if (i > 0) RowDivider()
                Box(Modifier.padding(12.dp)) { SessionRow(e, data, showDate = true) { open = StudySheet.Session(e, false) } }
            }
        }
    }
    open?.let { StudySheetContent(store, it) { open = null } }
}

@Composable
private fun ConceptRow(store: SheetStore, topic: StudyTopic, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(stageIcon(topic.stage), "${topic.stage.label}. Move to ${topic.stage.next.label}", tint = stageTint(topic.stage),
            modifier = Modifier.size(28.dp).clip(CircleShape).clickable {
                var t = topic.copy(stage = topic.stage.next)
                if (t.stage == StudyTopic.Stage.REVISING && t.nextReview == null) {
                    t = t.copy(nextReview = LocalDate.now().plusDays(StudySchedule.days(t.confidence ?: 3, maxOf(t.reviews, 1)).toLong()).toString())
                }
                store.saveStudyTopic(t)
            }.padding(2.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable(onClick = onClick)) {
            Text(topic.topic, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            val detail = listOfNotNull(topic.stage.label,
                if (topic.isDue()) "revise now" else topic.nextReview?.let { "revise ${SheetDate.short(it)}" }, topic.notes).joinToString(" · ")
            Text(detail, fontSize = 12.sp, color = if (topic.isDue()) SheetTheme.warn else SheetTheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        topic.confidence?.let { ConfidenceDots(it) }
    }
}

// MARK: - Subject editor

@Composable
fun SubjectEditorSheet(store: SheetStore, initial: StudySubject, isNew: Boolean, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var why by remember { mutableStateOf(initial.why ?: "") }
    var resource by remember { mutableStateOf(initial.resource ?: "") }
    var hours by remember { mutableStateOf(SheetFormat.plain(initial.weeklyHours)) }
    var target by remember { mutableStateOf(date(initial.targetDate)) }
    var status by remember { mutableStateOf(initial.status) }
    var color by remember { mutableStateOf(initial.color ?: 0) }
    var confirmDelete by remember { mutableStateOf(false) }
    EditorSheet(if (isNew) "New subject" else "Edit subject", onDismiss, canSave = name.isNotBlank(), onSave = {
        store.saveStudySubject(initial.copy(name = name.trim(), why = why.trim().ifEmpty { null }, resource = resource.trim().ifEmpty { null },
            weeklyHours = SheetFormat.input(hours), targetDate = target?.toString(), status = status, color = color))
        onDismiss()
    }) {
        Field("What you're studying") { PlainField(name, "Subject or course, e.g. Swift, IELTS, DSA") { name = it } }
        Field("Why are you learning it? (optional)") { PlainField(why, "e.g. move into iOS, exam in March", singleLine = false) { why = it } }
        Field("Book, course or link (optional)") { PlainField(resource, "e.g. https://… or a book title") { resource = it } }
        NumberField("Weekly target", hours, "h/week") { hours = it }
        DateField("Exam or deadline", target, allowClear = true) { target = it }
        Field("Status") { Choices(StudySubject.Status.entries, status, { it.label }) { status = it } }
        Field("Colour") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                (0 until STUDY_PALETTE_SIZE).forEach { i ->
                    Box(Modifier.size(30.dp).clip(CircleShape).background(studyColor(i)).clickable { color = i }, contentAlignment = Alignment.Center) {
                        if (Math.floorMod(color, STUDY_PALETTE_SIZE) == i) Icon(Icons.Filled.Check, "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        if (!isNew) {
            Text("Delete subject", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { confirmDelete = true }.padding(vertical = 8.dp))
            Text("Its concepts and goals are deleted too. Study time you logged stays in your history.", fontSize = 12.sp, color = SheetTheme.secondary)
        }
    }
    if (confirmDelete) AppDialog(
        title = "Delete ${initial.name}?",
        message = "Its concepts and goals are deleted too. Your logged study time stays.",
        style = DialogStyle.DESTRUCTIVE,
        confirmText = "Delete subject",
        onConfirm = { store.deleteStudySubject(initial.id); confirmDelete = false; onDismiss() },
        onDismiss = { confirmDelete = false },
    )
}

// MARK: - Concept editor

@Composable
fun ConceptEditorSheet(store: SheetStore, initial: StudyTopic, isNew: Boolean, onDismiss: () -> Unit) {
    val data = store.snapshot?.studyData ?: StudyData()
    var topic by remember { mutableStateOf(initial) }
    var addAnother by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<StudySheet?>(null) }
    val uri = LocalUriHandler.current
    EditorSheet(if (isNew) "New concept" else "Concept", onDismiss, canSave = topic.topic.isNotBlank() && topic.subjectId.isNotEmpty(), onSave = {
        store.saveStudyTopic(topic.copy(topic = topic.topic.trim(), notes = topic.notes?.trim()?.ifEmpty { null }, link = topic.link?.trim()?.ifEmpty { null }))
        if (addAnother) topic = StudyTopic(subjectId = topic.subjectId) else onDismiss()
    }) {
        Field("Concept") { PlainField(topic.topic, "e.g. Closures, Binary search") { topic = topic.copy(topic = it) } }
        Field("Subject") { Choices(data.subjects, data.subject(topic.subjectId), { it.name }) { topic = topic.copy(subjectId = it.id) } }
        FormSection("Where you are with it") {
            StudyTopic.Stage.entries.forEachIndexed { i, stage ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().clickable { topic = topic.copy(stage = stage) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(stageIcon(stage), null, tint = stageTint(stage), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stage.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                        Text(stage.explanation, fontSize = 12.sp, color = SheetTheme.secondary)
                    }
                    if (topic.stage == stage) Icon(Icons.Filled.Check, "Selected", tint = SheetTheme.navy)
                }
            }
        }
        Field("How well do you know it?") { ConfidencePicker(topic.confidence) { topic = topic.copy(confidence = it) } }
        Field("Notes") { PlainField(topic.notes ?: "", "Key points, formulas, examples, what confused you…", singleLine = false) { topic = topic.copy(notes = it) } }
        Field("Link (video, article, docs)") { PlainField(topic.link ?: "", "https://…") { topic = topic.copy(link = it) } }
        topic.link?.takeIf { it.startsWith("http") }?.let { link -> LinkText("Open link", SheetTheme.navy, 14.sp) { runCatching { uri.openUri(link) } } }
        DateField("Next revision", date(topic.nextReview), allowClear = true) { topic = topic.copy(nextReview = it?.toString()) }
        Text(topic.lastStudied?.let { "Last studied ${SheetDate.short(it)} · revised ${topic.reviews} time${if (topic.reviews == 1) "" else "s"}. Logging a session on it sets the next date for you." }
            ?: "Logging a session on it sets the next revision date for you, sooner if you're shaky on it.", fontSize = 12.sp, color = SheetTheme.secondary)
        if (isNew) ToggleRow("Add another after saving", addAnother) { addAnother = it }
        else {
            val sessions = data.sessions.filter { it.topicId == topic.id }.sortedByDescending { it.date }
            FormSection("Study on this concept · ${StudyFormat.minutes(sessions.sumOf { it.minutes })}") {
                Text("+ Log time on it", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy, modifier = Modifier.fillMaxWidth()
                    .clickable { open = StudySheet.Session(StudyEntry(subjectId = topic.subjectId, topicId = topic.id, topic = topic.topic), true) }.padding(16.dp))
                sessions.take(10).forEach { e -> RowDivider(); Box(Modifier.padding(12.dp)) { SessionRow(e, data, showDate = true) { open = StudySheet.Session(e, false) } } }
            }
            Text("Delete concept", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { store.deleteStudyTopic(topic.id); onDismiss() }.padding(vertical = 8.dp))
        }
    }
    open?.let { StudySheetContent(store, it) { open = null } }
}

// MARK: - Session editor

@Composable
fun StudySessionSheet(store: SheetStore, initial: StudyEntry, isNew: Boolean, onDismiss: () -> Unit) {
    val data = store.snapshot?.studyData ?: StudyData()
    var entry by remember { mutableStateOf(initial) }
    var minutesText by remember { mutableStateOf(SheetFormat.plain(initial.minutes, 0)) }
    var scoreText by remember { mutableStateOf(SheetFormat.plain(initial.score)) }
    var newTopic by remember { mutableStateOf("") }
    var confidence by remember { mutableStateOf(data.topic(initial.topicId)?.confidence) }
    var stage by remember { mutableStateOf(data.topic(initial.topicId)?.stage) }
    val minutes = SheetFormat.input(minutesText)?.takeIf { it > 0 }
    val topics = data.topicsOf(entry.subjectId).filter { it.stage != StudyTopic.Stage.MASTERED || it.id == entry.topicId }
    val topic = data.topic(entry.topicId)

    EditorSheet(if (isNew) "Log study" else "Session", onDismiss, canSave = entry.subjectId.isNotEmpty() && minutes != null, onSave = {
        var saved = entry.copy(minutes = Math.round(minutes!!).toDouble(), subject = data.subject(entry.subjectId)?.name,
            score = if (entry.kind == StudyEntry.Kind.TEST) SheetFormat.input(scoreText) else entry.score, notes = entry.notes?.trim()?.ifEmpty { null })
        var t = topic
        if (t == null && newTopic.isNotBlank()) {
            t = StudyTopic(subjectId = entry.subjectId, topic = newTopic.trim(), stage = StudyTopic.Stage.LEARNING)
            saved = saved.copy(topicId = t.id)
        }
        saved = saved.copy(topic = t?.topic)
        var updated: StudyTopic? = null
        if (t != null) {
            // A new session counts as a review and reschedules; an edit only updates what you changed.
            var u = if (isNew) t.studied(saved.date, confidence) else t.copy(confidence = confidence ?: t.confidence)
            stage?.let { s -> u = u.copy(stage = if (isNew && s == StudyTopic.Stage.TO_LEARN) StudyTopic.Stage.LEARNING else s) }
            if (u != t) updated = u
        }
        store.saveStudySession(saved, updated)
        onDismiss()
    }) {
        Field("Subject") {
            Choices(data.subjects, data.subject(entry.subjectId), { it.name }) {
                entry = entry.copy(subjectId = it.id, topicId = if (data.topic(entry.topicId)?.subjectId == it.id) entry.topicId else null)
            }
        }
        if (entry.subjectId.isNotEmpty()) Field("Concept") {
            Choices(listOf<StudyTopic?>(null) + topics, topic, { it?.topic ?: "General / none" }) {
                entry = entry.copy(topicId = it?.id); confidence = it?.confidence; stage = it?.stage
            }
            if (entry.topicId == null) { Spacer(Modifier.height(8.dp)); PlainField(newTopic, "Or a new concept…") { newTopic = it } }
        }
        Field("Kind") { Choices(StudyEntry.Kind.entries, entry.kind, { it.label }) { entry = entry.copy(kind = it) } }
        Field("How long") {
            NumberField("Minutes", minutesText, "min · ${StudyFormat.minutes(minutes ?: 0.0)}") { minutesText = it }
            Spacer(Modifier.height(8.dp))
            Choices(listOf(15, 25, 30, 45, 60, 90, 120), minutes?.toInt(), { StudyFormat.minutes(it.toDouble()) }) { minutesText = it.toString() }
        }
        DateField("Date", date(entry.date), allowClear = false) { d -> if (d != null && !d.isAfter(LocalDate.now())) entry = entry.copy(date = d.toString()) }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SheetTheme.card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Focus", fontSize = 15.sp, color = SheetTheme.text, modifier = Modifier.weight(1f))
            (1..5).forEach { i ->
                Icon(if (i <= (entry.focus ?: 0)) Icons.Filled.Star else Icons.Filled.StarBorder, "Focus $i", tint = SheetTheme.warn,
                    modifier = Modifier.size(30.dp).clickable { entry = entry.copy(focus = if (entry.focus == i) null else i) }.padding(3.dp))
            }
        }
        if (entry.kind == StudyEntry.Kind.TEST) NumberField("Score", scoreText, "%") { scoreText = it }
        Field("Notes") { PlainField(entry.notes ?: "", "What did you cover? What's still unclear?", singleLine = false) { entry = entry.copy(notes = it) } }
        if (entry.topicId != null || newTopic.isNotBlank()) {
            Field("How well do you know it now?") {
                ConfidencePicker(confidence) { confidence = it }
                Spacer(Modifier.height(8.dp))
                Choices(StudyTopic.Stage.entries, stage ?: StudyTopic.Stage.LEARNING, { it.label }) { stage = it }
            }
            val reviews = (topic?.reviews ?: 0) + (if (isNew) 1 else 0)
            Text("Next revision ${StudyFormat.countdown(StudySchedule.days(confidence ?: 3, maxOf(reviews, 1)))} — sooner when you're shaky, further apart as it sticks.",
                fontSize = 12.sp, color = SheetTheme.secondary)
        }
        if (!isNew) Text("Delete session", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { store.deleteStudySession(entry.id); onDismiss() }.padding(vertical = 8.dp))
    }
}

// MARK: - Goal editor

@Composable
fun StudyGoalSheet(store: SheetStore, initial: StudyGoal, isNew: Boolean, onDismiss: () -> Unit) {
    val data = store.snapshot?.studyData ?: StudyData()
    var goal by remember { mutableStateOf(initial) }
    var targetText by remember { mutableStateOf(SheetFormat.plain(initial.target)) }
    val target = SheetFormat.input(targetText)
    val canSave = goal.title.isNotBlank() && (goal.kind == StudyGoal.Kind.MILESTONE || (target ?: 0.0) > 0)
    EditorSheet(if (isNew) "New goal" else "Goal", onDismiss, canSave = canSave, onSave = {
        val done = goal.done
        store.saveStudyGoal(goal.copy(title = goal.title.trim(), target = if (goal.kind == StudyGoal.Kind.MILESTONE) null else target,
            doneOn = if (done) goal.doneOn ?: LocalDate.now().toString() else null))
        onDismiss()
    }) {
        FormSection("Kind of goal") {
            StudyGoal.Kind.entries.forEachIndexed { i, kind ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().clickable { goal = goal.copy(kind = kind) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(when (kind) { StudyGoal.Kind.HOURS -> Icons.Filled.Schedule; StudyGoal.Kind.TOPICS -> Icons.Filled.Verified; StudyGoal.Kind.MILESTONE -> Icons.Filled.Flag },
                        null, tint = SheetTheme.indigo, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(kind.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                        Text(kind.explanation, fontSize = 12.sp, color = SheetTheme.secondary)
                    }
                    if (goal.kind == kind) Icon(Icons.Filled.Check, "Selected", tint = SheetTheme.navy)
                }
            }
        }
        Field("Goal") {
            PlainField(goal.title, when (goal.kind) { StudyGoal.Kind.HOURS -> "e.g. 40 hours of DSA"; StudyGoal.Kind.TOPICS -> "e.g. Master SwiftUI basics"; StudyGoal.Kind.MILESTONE -> "e.g. Pass AWS exam" }) {
                goal = goal.copy(title = it)
            }
        }
        Field("Subject") { Choices(listOf<String?>(null) + data.subjects.map { it.id }, goal.subjectId, { id -> data.subject(id)?.name ?: "All subjects" }) { goal = goal.copy(subjectId = it) } }
        if (goal.kind != StudyGoal.Kind.MILESTONE) NumberField(if (goal.kind == StudyGoal.Kind.HOURS) "Hours" else "Concepts mastered", targetText, "") { targetText = it }
        if (goal.kind == StudyGoal.Kind.HOURS) DateField("Counting from", date(goal.start), allowClear = false) { goal = goal.copy(start = it?.toString() ?: goal.start) }
        DateField("Due", date(goal.due), allowClear = true) { goal = goal.copy(due = it?.toString()) }
        ToggleRow("Achieved", goal.done) { goal = goal.copy(done = it) }
        Field("Notes (optional)") { PlainField(goal.notes ?: "", "Anything to remember", singleLine = false) { goal = goal.copy(notes = it.ifEmpty { null }) } }
        if (!isNew && goal.kind != StudyGoal.Kind.MILESTONE) Text("Now: ${data.progress(goal).detail}. It fills in from your sessions and concepts.", fontSize = 12.sp, color = SheetTheme.secondary)
        if (!isNew) Text("Delete goal", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { store.deleteStudyGoal(goal.id); onDismiss() }.padding(vertical = 8.dp))
    }
}

// MARK: - Quick revision

/** A due concept: read your notes, say how well you remembered, done. */
@Composable
fun QuickReviewSheet(store: SheetStore, topic: StudyTopic, onDismiss: () -> Unit) {
    val subject = store.snapshot?.studyData?.subject(topic.subjectId)
    var confidence by remember { mutableStateOf<Int?>(null) }
    var minutes by remember { mutableStateOf(10) }
    var logSession by remember { mutableStateOf(true) }
    var showNotes by remember { mutableStateOf(false) }
    EditorSheet("Revise", onDismiss, saveLabel = "Done", canSave = confidence != null, onSave = {
        val c = confidence!!
        var t = topic.studied(LocalDate.now().toString(), c)
        if (c >= 4 && t.stage == StudyTopic.Stage.LEARNING) t = t.copy(stage = StudyTopic.Stage.REVISING)
        if (logSession) store.saveStudySession(StudyEntry(subjectId = topic.subjectId, subject = subject?.name, topicId = topic.id, topic = topic.topic,
            minutes = minutes.toDouble(), kind = StudyEntry.Kind.REVISE), t)
        else store.saveStudyTopic(t)
        onDismiss()
    }) {
        SheetCard {
            Text(subject?.name ?: "", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = studyColor(subject?.color))
            Text(topic.topic, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text)
            Text("Try to recall it before you look at your notes.", fontSize = 12.sp, color = SheetTheme.secondary)
            topic.notes?.let { notes ->
                Spacer(Modifier.height(10.dp))
                if (showNotes) Text(notes, fontSize = 14.sp, color = SheetTheme.text)
                else LinkText("Show your notes", SheetTheme.navy, 14.sp) { showNotes = true }
            }
        }
        Field("How well did you remember it?") { ConfidencePicker(confidence) { confidence = it } }
        confidence?.let { Text("Next revision ${StudyFormat.countdown(StudySchedule.days(it, topic.reviews + 1))}.", fontSize = 12.sp, color = SheetTheme.secondary) }
        ToggleRow("Count it as study time", logSession) { logSession = it }
        if (logSession) Choices(listOf(5, 10, 15, 20, 30, 45), minutes, { "$it min" }) { minutes = it }
    }
}

// MARK: - Targets, timer, import

@Composable
fun StudyTargetsSheet(store: SheetStore, onDismiss: () -> Unit) {
    var daily by remember { mutableStateOf(SheetFormat.plain(store.snapshot?.settings?.studyDailyTarget)) }
    var weekly by remember { mutableStateOf(SheetFormat.plain(store.snapshot?.settings?.studyWeeklyTarget)) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val appSettings = context.container.settings
    var remind by remember { mutableStateOf(appSettings.studyReminderEnabled) }
    var remindAt by remember { mutableStateOf(appSettings.studyReminderMinutes) }
    val askPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    fun applyReminder() {
        appSettings.studyReminderEnabled = remind
        appSettings.studyReminderMinutes = remindAt
        com.mohammedsharukms.mypersonaltracker.reminders.ReminderScheduler.reschedule(context)
    }
    val d = SheetFormat.input(daily)
    val w = SheetFormat.input(weekly)
    EditorSheet("Study targets", onDismiss, canSave = (d ?: 0.0) > 0 && (w ?: 0.0) > 0, saving = saving,
        error = store.errorMessage.takeIf { !saving }, onSave = {
            saving = true
            scope.launch {
                val ok = store.saveSettings(mapOf("STUDY_DAILY_TARGET" to d!!, "STUDY_WEEKLY_TARGET" to w!!))
                saving = false
                if (ok) onDismiss()
            }
        }) {
        NumberField("Every day", daily, "min") { daily = it }
        NumberField("Every week", weekly, "hours") { weekly = it }
        Text("Saved to Settings ▸ Study in your sheet. Each subject can have its own weekly target too.", fontSize = 12.sp, color = SheetTheme.secondary)
        ToggleRow("Remind me to study", remind, "Tells you what's due for revision and what's next. Skipped on days you've already reached your target.") {
            remind = it
            if (it && android.os.Build.VERSION.SDK_INT >= 33) askPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            applyReminder()
        }
        if (remind) Field("Every day at") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(6 * 60, 7 * 60, 13 * 60, 18 * 60, 19 * 60, 20 * 60, 21 * 60, 22 * 60).forEach { m ->
                    FilterPill(java.time.LocalTime.of(m / 60, m % 60).format(java.time.format.DateTimeFormatter.ofPattern("h a")), null, SheetTheme.navy, m == remindAt) {
                        remindAt = m; applyReminder()
                    }
                }
            }
        }
    }
}

/** Pick the subject (and concept) the timer is for. */
@Composable
fun StartStudySheet(store: SheetStore, onDismiss: () -> Unit) {
    val data = store.snapshot?.studyData ?: StudyData()
    val timer = LocalContext.current.container.studyTimer
    val subjects = data.subjects.filter { it.status != StudySubject.Status.COMPLETED }
    var subjectId by remember { mutableStateOf(data.currentTopic?.subjectId ?: subjects.firstOrNull()?.id ?: "") }
    var topicId by remember { mutableStateOf(data.currentTopic?.id) }
    EditorSheet("Start studying", onDismiss, saveLabel = "Start", canSave = data.subject(subjectId) != null, onSave = {
        data.subject(subjectId)?.let { timer.start(it, data.topic(topicId)) }
        onDismiss()
    }) {
        Field("Subject") { Choices(subjects, data.subject(subjectId), { it.name }) { subjectId = it.id; if (data.topic(topicId)?.subjectId != it.id) topicId = null } }
        Field("Concept") {
            Choices(listOf<StudyTopic?>(null) + data.topicsOf(subjectId).filter { it.stage != StudyTopic.Stage.MASTERED }, data.topic(topicId),
                { it?.topic ?: "General / none" }) { topicId = it?.id }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayArrow, null, tint = SheetTheme.secondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("The timer keeps running in your notifications. Stop it on the Study tab to save the session.", fontSize = 12.sp, color = SheetTheme.secondary)
        }
    }
}

/** Copies the on-phone Learning journal into the sheet: sections become subjects, topics become concepts. */
@Composable
fun JournalImportSheet(store: SheetStore, onDismiss: () -> Unit) {
    val database = LocalContext.current.container.database
    val sections by remember { database.learning().observeSections() }.collectAsState(initial = emptyList())
    EditorSheet("Bring in your journal", onDismiss, saveLabel = "Import", canSave = sections.isNotEmpty(), onSave = {
        val existing = store.snapshot?.studyData?.subjects.orEmpty().map { it.name.lowercase() }.toSet()
        sections.forEachIndexed { i, s ->
            if (s.section.name.lowercase() in existing) return@forEachIndexed
            val started = s.section.createdAt.atZone(ZoneId.systemDefault()).toLocalDate().toString()
            val subject = StudySubject(name = s.section.name, color = i, started = started)
            store.saveStudySubject(subject)
            s.sortedTopics.forEach { t ->
                val stage = when (t.status) {
                    TopicStatus.NOT_STARTED -> StudyTopic.Stage.TO_LEARN
                    TopicStatus.LEARNING -> StudyTopic.Stage.LEARNING
                    TopicStatus.DONE -> StudyTopic.Stage.MASTERED
                }
                store.saveStudyTopic(StudyTopic(subjectId = subject.id, topic = t.title, stage = stage, notes = t.notes.ifEmpty { null },
                    added = t.createdAt.atZone(ZoneId.systemDefault()).toLocalDate().toString()))
            }
        }
        onDismiss()
    }) {
        FormSection("From this phone", footer = "Each section becomes a subject and its topics become concepts (Done → Mastered). Your journal on this phone stays as it is.") {
            sections.forEachIndexed { i, s ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = SheetTheme.good, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(s.section.name, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                        Text("${s.topics.size} topics · ${s.topics.count { it.status == TopicStatus.DONE }} done", fontSize = 12.sp, color = SheetTheme.secondary)
                    }
                }
            }
        }
    }
}
