package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.container
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSettings
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.StudyData
import com.mohammedsharukms.mypersonaltracker.sheet.StudyEntry
import com.mohammedsharukms.mypersonaltracker.sheet.StudyFormat
import com.mohammedsharukms.mypersonaltracker.sheet.StudyPace
import com.mohammedsharukms.mypersonaltracker.sheet.StudySchedule
import com.mohammedsharukms.mypersonaltracker.sheet.StudySubject
import com.mohammedsharukms.mypersonaltracker.sheet.StudyTopic
import com.mohammedsharukms.mypersonaltracker.sheet.SyllabusParse
import com.mohammedsharukms.mypersonaltracker.sheet.activeSubjects
import com.mohammedsharukms.mypersonaltracker.sheet.currentTopic
import com.mohammedsharukms.mypersonaltracker.sheet.dueTopics
import com.mohammedsharukms.mypersonaltracker.sheet.minutesOn
import com.mohammedsharukms.mypersonaltracker.sheet.studyData
import com.mohammedsharukms.mypersonaltracker.sheet.subject
import com.mohammedsharukms.mypersonaltracker.sheet.topic
import com.mohammedsharukms.mypersonaltracker.sheet.topicsOf
import java.time.LocalDate

// What a learner actually asks the app: "what do I do today?", "help me
// revise", "don't make me type my syllabus", "will I finish in time?" and
// "am I improving?". Mirrors StudyPlan.swift.

private val confidenceLabels = listOf("Forgot", "Shaky", "Okay", "Good", "Solid")

@Composable
private fun confidenceColor(level: Int): Color = when {
    level <= 2 -> SheetTheme.bad
    level == 3 -> SheetTheme.warn
    else -> SheetTheme.good
}

/** ① Revise what's due ② Learn the next concept ③ Reach today's target — each ticks itself off. */
@Composable
fun TodayPlanCard(data: StudyData, settings: SheetSettings, open: (StudySheet) -> Unit) {
    val reminding = LocalContext.current.container.settings.studyReminderEnabled
    val today = LocalDate.now().toString()
    val due = data.dueTopics(today)
    val revisedToday = data.sessions.count { it.date == today && it.kind == StudyEntry.Kind.REVISE }
    val current = data.currentTopic
    val learntToday = current?.let { c -> data.sessions.any { it.date == today && it.topicId == c.id } } ?: false
    val minutes = data.minutesOn(today)
    val target = maxOf(settings.studyDailyTarget ?: 60.0, 1.0)
    val done = listOf(due.isEmpty(), learntToday || current == null, minutes >= target).count { it }
    SheetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Today's plan", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, modifier = Modifier.weight(1f))
            Text("$done of 3 done", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (done == 3) SheetTheme.good else SheetTheme.secondary)
        }
        Spacer(Modifier.height(12.dp))
        PlanStep(1, due.isEmpty(),
            if (due.isEmpty()) (if (revisedToday > 0) "Revision done for today" else "Nothing due for revision") else "Revise ${due.size} concept${if (due.size == 1) "" else "s"}",
            if (due.isEmpty()) "Concepts come back here when they're due." else due.take(3).joinToString { it.topic } + if (due.size > 3) "…" else "",
            if (due.isEmpty()) null else "Start" to { open(StudySheet.ReviewRun(due.map { it.id })) })
        Spacer(Modifier.height(12.dp))
        val subject = current?.let { data.subject(it.subjectId) }
        if (current != null && subject != null) {
            PlanStep(2, learntToday, if (learntToday) "Studied ${current.topic} today" else "Learn: ${current.topic}",
                subject.name + if (current.stage == StudyTopic.Stage.TO_LEARN) " · next up" else " · in progress",
                if (learntToday) null else "Study" to { open(StudySheet.StartTimer) })
        } else {
            PlanStep(2, false, "Pick what to learn next", if (data.subjects.isEmpty()) "Add a subject and its concepts." else "Add concepts to an active subject.",
                "Add" to { open(if (data.subjects.isEmpty()) StudySheet.Subject(StudySubject(), true) else StudySheet.SubjectDetail((data.activeSubjects.firstOrNull() ?: data.subjects.first()).id)) })
        }
        Spacer(Modifier.height(12.dp))
        PlanStep(3, minutes >= target,
            if (minutes >= target) "Today's target reached" else "${StudyFormat.minutes(target - minutes)} left to today's target",
            "${StudyFormat.minutes(minutes)} of ${StudyFormat.minutes(target)} studied",
            if (minutes >= target) null else "Log" to {
                open(StudySheet.Session(StudyEntry(subjectId = current?.subjectId ?: data.activeSubjects.firstOrNull()?.id ?: data.subjects.firstOrNull()?.id ?: "",
                    topicId = current?.id), true))
            })
        if (!reminding) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.clickable { open(StudySheet.Targets) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NotificationsActive, null, tint = SheetTheme.navy, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Get a daily study reminder", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
            }
        }
    }
}

@Composable
private fun PlanStep(number: Int, done: Boolean, title: String, detail: String, action: Pair<String, () -> Unit>?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(if (done) SheetTheme.good else SheetTheme.navy.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            if (done) Icon(Icons.Filled.Check, "Done", tint = Color.White, modifier = Modifier.size(16.dp))
            else Text("$number", fontWeight = FontWeight.Bold, color = SheetTheme.navy)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (done) SheetTheme.secondary else SheetTheme.text,
                textDecoration = if (done) TextDecoration.LineThrough else null, maxLines = 2)
            Text(detail, fontSize = 12.sp, color = SheetTheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            Text(action.first, fontWeight = FontWeight.SemiBold, color = Color.White,
                modifier = Modifier.clip(CircleShape).background(SheetTheme.navy).clickable(onClick = action.second).padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

/** Every due concept, one after another: recall it, peek at your notes, tap how well you knew it. */
@Composable
fun ReviewRunSheet(store: SheetStore, topicIds: List<String>, onDismiss: () -> Unit) {
    val data = store.snapshot?.studyData ?: StudyData()
    val topics = remember(topicIds) { topicIds.mapNotNull { data.topic(it) } }
    var index by remember { mutableIntStateOf(0) }
    var showNotes by remember { mutableStateOf(false) }
    val rated = remember { mutableStateListOf<Pair<StudyTopic, Int>>() }
    var minutesEach by remember { mutableIntStateOf(3) }
    var logTime by remember { mutableStateOf(true) }

    fun finish() {
        if (logTime && rated.isNotEmpty()) {
            rated.groupBy { it.first.subjectId }.forEach { (subjectId, items) ->
                store.saveStudySession(StudyEntry(subjectId = subjectId, subject = data.subject(subjectId)?.name,
                    topicId = if (items.size == 1) items[0].first.id else null, topic = if (items.size == 1) items[0].first.topic else null,
                    minutes = (minutesEach * items.size).toDouble(), kind = StudyEntry.Kind.REVISE,
                    notes = if (items.size == 1) null else "Revised " + items.joinToString { it.first.topic }))
            }
        }
        onDismiss()
    }

    val running = index < topics.size
    EditorSheet(if (running) "Revise · ${index + 1} of ${topics.size}" else "Revision done", { finish() },
        saveLabel = "Done", onSave = if (running) null else ({ finish() })) {
        if (running) {
            val topic = topics[index]
            val subject = data.subject(topic.subjectId)
            Bar((index).toDouble() / topics.size, SheetTheme.navy)
            Text(subject?.name ?: "", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = studyColor(subject?.color))
            Text(topic.topic, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text)
            Text("Say or write down what you remember about it, then check your notes.", color = SheetTheme.secondary)
            topic.notes?.let { notes ->
                SheetCard(onClick = { showNotes = !showNotes }) {
                    Text(if (showNotes) "Hide your notes" else "Show your notes", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
                    if (showNotes) { Spacer(Modifier.height(8.dp)); Text(notes, color = SheetTheme.text) }
                }
            }
            Text("How well did you remember it?", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            (5 downTo 1).forEach { level ->
                val color = confidenceColor(level)
                val days = StudySchedule.days(level, topic.reviews + 1)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.15f)).clickable {
                    var t = topic.studied(LocalDate.now().toString(), level)
                    if (level >= 4 && t.stage == StudyTopic.Stage.LEARNING) t = t.copy(stage = StudyTopic.Stage.REVISING)
                    store.saveStudyTopic(t)
                    rated += topic to level
                    showNotes = false
                    index++
                }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(confidenceLabels[level - 1], fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.weight(1f))
                    Text("next in $days day${if (days == 1) "" else "s"}", fontSize = 12.sp, color = color)
                }
            }
        } else {
            SheetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Verified, null, tint = SheetTheme.good)
                    Spacer(Modifier.width(8.dp))
                    Text("You revised ${rated.size} concept${if (rated.size == 1) "" else "s"}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.good)
                }
                val shaky = rated.filter { it.second <= 2 }.map { it.first.topic }
                if (shaky.isNotEmpty()) Text("Coming back soon: ${shaky.joinToString()}", fontSize = 13.sp, color = SheetTheme.secondary, modifier = Modifier.padding(top = 6.dp))
            }
            ToggleRow("Count it as study time", logTime, if (logTime) "About $minutesEach min each · ${StudyFormat.minutes((minutesEach * rated.size).toDouble())}" else null) { logTime = it }
            if (logTime) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 3, 5, 10).forEach { m -> FilterPill("$m min", null, SheetTheme.navy, m == minutesEach) { minutesEach = m } }
            }
        }
    }
}

/** Paste the contents page of a book or course: one concept per line. */
@Composable
fun SyllabusSheet(store: SheetStore, subjectId: String, onDismiss: () -> Unit) {
    val data = store.snapshot?.studyData ?: StudyData()
    var text by remember { mutableStateOf("") }
    val existing = data.topicsOf(subjectId).map { it.topic.lowercase() }.toSet()
    val lines = SyllabusParse.lines(text).filter { it.lowercase() !in existing }
    EditorSheet("Add your syllabus", onDismiss, saveLabel = "Add ${lines.size}", canSave = lines.isNotEmpty(), onSave = {
        lines.forEach { store.saveStudyTopic(StudyTopic(subjectId = subjectId, topic = it)) }
        onDismiss()
    }) {
        Text("Paste or type one concept per line", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
        BasicTextField(text, { text = it }, textStyle = TextStyle(fontSize = 16.sp, color = SheetTheme.text), cursorBrush = SolidColor(SheetTheme.navy),
            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).clip(RoundedCornerShape(12.dp)).background(SheetTheme.card).padding(14.dp),
            decorationBox = { inner ->
                if (text.isEmpty()) Text("For example:\n1. Arrays and strings\n2. Two pointers\n3. Sliding window\n4. Binary search", color = SheetTheme.tertiary)
                inner()
            })
        Text("Copy the contents page of your book or course. Bullets and numbering are removed; concepts already in ${data.subject(subjectId)?.name ?: "this subject"} are skipped.",
            fontSize = 12.sp, color = SheetTheme.secondary)
        if (lines.isNotEmpty()) FormSection("${lines.size} concept${if (lines.size == 1) "" else "s"} to add") {
            lines.forEachIndexed { i, line ->
                if (i > 0) RowDivider()
                Text(line, color = SheetTheme.text, modifier = Modifier.fillMaxWidth().padding(14.dp))
            }
        }
    }
}

/** On pace or behind for the target date, and what it takes to finish. */
@Composable
fun PaceRow(pace: StudyPace) {
    val tint = when (pace.state) {
        StudyPace.State.DONE -> SheetTheme.good
        StudyPace.State.ON_TRACK -> SheetTheme.teal
        StudyPace.State.BEHIND -> SheetTheme.warn
        StudyPace.State.PASSED -> SheetTheme.bad
    }
    Column(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Speed, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(when (pace.state) { StudyPace.State.BEHIND -> "Behind pace"; StudyPace.State.ON_TRACK -> "On pace"; StudyPace.State.DONE -> "Finished"; else -> "Past the date" },
                fontWeight = FontWeight.SemiBold, color = tint, modifier = Modifier.weight(1f))
            Text("${pace.total - pace.left} of ${pace.total} mastered", fontSize = 12.sp, color = SheetTheme.secondary)
        }
        Text(pace.summary, color = SheetTheme.text)
        if (pace.state == StudyPace.State.ON_TRACK || pace.state == StudyPace.State.BEHIND) {
            Text("Lately you've got through about ${StudyFormat.number(pace.perWeekRecent)} a week.", fontSize = 12.sp, color = SheetTheme.secondary)
        }
    }
}

/** Mock tests over time: latest, average, change, and a small bar for each. */
@Composable
fun TestScores(tests: List<StudyEntry>) {
    val scores = tests.mapNotNull { it.score }
    val trend = if (scores.size >= 2) scores.last() - scores[scores.size - 2] else 0.0
    SheetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Latest ${StudyFormat.number(scores.lastOrNull() ?: 0.0, 0)}%", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                Text("Average ${StudyFormat.number(scores.average(), 0)}% over ${scores.size} test${if (scores.size == 1) "" else "s"}", fontSize = 12.sp, color = SheetTheme.secondary)
            }
            if (scores.size >= 2) Text("${if (trend >= 0) "↑ +" else "↓ "}${StudyFormat.number(trend, 0)}", fontWeight = FontWeight.Bold,
                color = if (trend >= 0) SheetTheme.good else SheetTheme.bad)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            tests.takeLast(8).forEach { t ->
                val s = t.score ?: 0.0
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(StudyFormat.number(s, 0), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                    Box(Modifier.fillMaxWidth().height((s * 0.7).coerceAtLeast(6.0).dp).clip(RoundedCornerShape(4.dp))
                        .background(confidenceColor(if (s >= 75) 5 else if (s >= 50) 3 else 1)))
                    Text(SheetDate.short(t.date).substringBeforeLast(" "), fontSize = 9.sp, color = SheetTheme.secondary, maxLines = 1)
                }
            }
        }
    }
}
