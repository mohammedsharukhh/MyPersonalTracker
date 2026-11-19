package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.container
import com.mohammedsharukms.mypersonaltracker.sheet.GoalProgress
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSettings
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.StudyData
import com.mohammedsharukms.mypersonaltracker.sheet.StudyEntry
import com.mohammedsharukms.mypersonaltracker.sheet.StudyFormat
import com.mohammedsharukms.mypersonaltracker.sheet.StudyGoal
import com.mohammedsharukms.mypersonaltracker.sheet.StudySubject
import com.mohammedsharukms.mypersonaltracker.sheet.StudyTopic
import com.mohammedsharukms.mypersonaltracker.sheet.activeSubjects
import com.mohammedsharukms.mypersonaltracker.sheet.currentTopic
import com.mohammedsharukms.mypersonaltracker.sheet.daysUntil
import com.mohammedsharukms.mypersonaltracker.sheet.dueTopics
import com.mohammedsharukms.mypersonaltracker.sheet.minutesOn
import com.mohammedsharukms.mypersonaltracker.sheet.progress
import com.mohammedsharukms.mypersonaltracker.sheet.streak
import com.mohammedsharukms.mypersonaltracker.sheet.studyData
import com.mohammedsharukms.mypersonaltracker.sheet.subject
import com.mohammedsharukms.mypersonaltracker.sheet.topic
import com.mohammedsharukms.mypersonaltracker.sheet.weekMinutes
import kotlinx.coroutines.delay
import java.time.LocalDate

// The Study tab. Overview: today against your target, streak, what you're on
// now (with a timer), concepts due for revision, goals and subjects at a
// glance. Then every subject, every goal, and the log of sessions. Mirrors StudyView.swift.

/** Every editor the Study screens open. */
sealed interface StudySheet {
    data class Subject(val subject: StudySubject, val isNew: Boolean) : StudySheet
    data class SubjectDetail(val id: String) : StudySheet
    data class Topic(val topic: StudyTopic, val isNew: Boolean) : StudySheet
    data class Session(val entry: StudyEntry, val isNew: Boolean) : StudySheet
    data class Goal(val goal: StudyGoal, val isNew: Boolean) : StudySheet
    data class Review(val topic: StudyTopic) : StudySheet
    data class ReviewRun(val topicIds: List<String>) : StudySheet
    data class Syllabus(val subjectId: String) : StudySheet
    data object Targets : StudySheet
    data object StartTimer : StudySheet
    data object ImportJournal : StudySheet
}

@Composable
fun studyColor(index: Int?): Color {
    val colors = listOf(SheetTheme.indigo, SheetTheme.teal, SheetTheme.purple, SheetTheme.warn,
        SheetTheme.sky, SheetTheme.good, SheetTheme.bad, SheetTheme.navy)
    return colors[Math.floorMod(index ?: 0, colors.size)]
}

const val STUDY_PALETTE_SIZE = 8

@Composable
fun stageTint(stage: StudyTopic.Stage): Color = when (stage) {
    StudyTopic.Stage.TO_LEARN -> SheetTheme.secondary
    StudyTopic.Stage.LEARNING -> SheetTheme.sky
    StudyTopic.Stage.REVISING -> SheetTheme.warn
    StudyTopic.Stage.MASTERED -> SheetTheme.good
}

fun kindIcon(kind: StudyEntry.Kind): ImageVector = when (kind) {
    StudyEntry.Kind.LEARN -> Icons.AutoMirrored.Filled.MenuBook
    StudyEntry.Kind.PRACTICE -> Icons.Filled.EditNote
    StudyEntry.Kind.REVISE -> Icons.Filled.Autorenew
    StudyEntry.Kind.TEST -> Icons.Filled.Quiz
    StudyEntry.Kind.PROJECT -> Icons.Filled.Build
}

@Composable
fun goalTint(state: GoalProgress.State): Color = when (state) {
    GoalProgress.State.DONE -> SheetTheme.good
    GoalProgress.State.ON_TRACK -> SheetTheme.teal
    GoalProgress.State.BEHIND -> SheetTheme.warn
    GoalProgress.State.OVERDUE -> SheetTheme.bad
    GoalProgress.State.OPEN -> SheetTheme.sky
}

private fun newSession(data: StudyData): StudyEntry {
    val current = data.currentTopic
    return StudyEntry(subjectId = current?.subjectId ?: data.activeSubjects.firstOrNull()?.id ?: data.subjects.firstOrNull()?.id ?: "",
        topicId = current?.id)
}

@Composable
fun StudyScreen(store: SheetStore, debugOpen: String? = null) {
    var section by rememberSaveable { mutableStateOf(0) }
    var open by remember { mutableStateOf<StudySheet?>(null) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val data = store.snapshot?.studyData ?: return@LaunchedEffect
        when (debugOpen) {
            "study-subjects" -> section = 1
            "study-goals" -> section = 2
            "study-log" -> section = 3
            "study-session" -> open = StudySheet.Session(newSession(data), true)
            "study-subject" -> data.subjects.firstOrNull()?.let { open = StudySheet.SubjectDetail(it.id) }
            "study-review" -> data.dueTopics().firstOrNull()?.let { open = StudySheet.Review(it) }
            "revise-all" -> open = StudySheet.ReviewRun(data.dueTopics().map { it.id })
            "syllabus" -> data.subjects.firstOrNull()?.let { open = StudySheet.Syllabus(it.id) }
        }
    }

    SheetScreen("Study", isRefreshing = store.isLoading, onRefresh = { store.refresh() }, actions = {
        val data = store.snapshot?.studyData
        if (data != null) Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.AddCircle, "Add", tint = SheetTheme.navy) }
            DropdownMenu(menu, { menu = false }) {
                if (data.subjects.isNotEmpty()) {
                    DropdownMenuItem({ Text("Log a study session") }, { menu = false; open = StudySheet.Session(newSession(data), true) }, leadingIcon = { Icon(Icons.Filled.Schedule, null) })
                    DropdownMenuItem({ Text("Add a concept") }, {
                        menu = false
                        open = StudySheet.Topic(StudyTopic(subjectId = data.activeSubjects.firstOrNull()?.id ?: data.subjects.first().id), true)
                    }, leadingIcon = { Icon(Icons.Filled.EditNote, null) })
                }
                DropdownMenuItem({ Text("Add a subject") }, { menu = false; open = StudySheet.Subject(StudySubject(color = data.subjects.size), true) },
                    leadingIcon = { Icon(Icons.Filled.School, null) })
                DropdownMenuItem({ Text("Add a goal") }, { menu = false; open = StudySheet.Goal(StudyGoal(), true) }, leadingIcon = { Icon(Icons.Filled.Flag, null) })
            }
        }
    }) {
        val error = store.errorMessage
        if (error != null && store.isConfigured) item { RetryBanner(store, error) }
        item { SyncStatusBar(store) }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val colors = SegmentedButtonDefaults.colors(activeContainerColor = SheetTheme.navy, activeContentColor = Color.White,
                    inactiveContainerColor = SheetTheme.card, inactiveContentColor = SheetTheme.text,
                    activeBorderColor = SheetTheme.navy, inactiveBorderColor = SheetTheme.input)
                listOf("Overview", "Subjects", "Goals", "Log").forEachIndexed { i, label ->
                    SegmentedButton(section == i, { section = i }, SegmentedButtonDefaults.itemShape(i, 4), colors = colors) { Text(label, maxLines = 1) }
                }
            }
        }
        val snapshot = store.snapshot
        if (snapshot == null) {
            if (store.needsConnection) item { NotConnectedHint() } else item { SheetLoadingCard(store.loadProgress) }
            return@SheetScreen
        }
        val data = snapshot.studyData
        val go: (StudySheet) -> Unit = { open = it }
        when (section) {
            0 -> {
                item { StudyHero(data, snapshot.settings, go) }
                if (data.subjects.isEmpty()) item { StudyEmptyCard(go) } else {
                    item { TodayPlanCard(data, snapshot.settings, go) }
                    item { WeekChartCard(data) }
                    val goals = data.goals.filter { !it.done }.sortedBy { it.due ?: "9999" }
                    if (goals.isNotEmpty()) item {
                        SheetCard {
                            CardHeader("Goals", "See all") { section = 2 }
                            goals.take(3).forEach { g -> Spacer(Modifier.height(12.dp)); GoalRow(g, data) { open = StudySheet.Goal(g, false) } }
                        }
                    }
                    item {
                        SheetCard {
                            CardHeader("What you're studying", "All subjects") { section = 1 }
                            data.activeSubjects.forEach { s -> Spacer(Modifier.height(12.dp)); SubjectRow(s, data) { open = StudySheet.SubjectDetail(s.id) } }
                            if (data.activeSubjects.isEmpty()) Text("No active subjects. Resume one from Subjects, or add a new one.", fontSize = 14.sp, color = SheetTheme.secondary)
                        }
                    }
                    val recent = data.sessions.sortedByDescending { it.date }.take(4)
                    if (recent.isNotEmpty()) item {
                        SheetCard {
                            CardHeader("Recent sessions", "Full log") { section = 3 }
                            recent.forEach { e -> Spacer(Modifier.height(10.dp)); SessionRow(e, data, showDate = true) { open = StudySheet.Session(e, false) } }
                        }
                    }
                }
            }
            1 -> {
                item { PrimaryButton("Add a subject") { open = StudySheet.Subject(StudySubject(color = data.subjects.size), true) } }
                if (data.subjects.isEmpty()) item { StudyEmptyCard(go) }
                StudySubject.Status.entries.forEach { status ->
                    val list = data.subjects.filter { it.status == status }
                    if (list.isNotEmpty()) {
                        item { SectionTitle(if (status == StudySubject.Status.ACTIVE) "Studying now" else status.label) }
                        item {
                            SheetCard {
                                list.forEachIndexed { i, s -> if (i > 0) Spacer(Modifier.height(14.dp)); SubjectRow(s, data) { open = StudySheet.SubjectDetail(s.id) } }
                            }
                        }
                    }
                }
            }
            2 -> {
                item { PrimaryButton("Add a goal") { open = StudySheet.Goal(StudyGoal(), true) } }
                val active = data.goals.filter { !it.done }.sortedBy { it.due ?: "9999" }
                val done = data.goals.filter { it.done }.sortedByDescending { it.doneOn ?: "" }
                if (data.goals.isEmpty()) item {
                    SheetCard {
                        Text("Set what you're aiming for: hours to put in (40 hours of DSA by December), concepts to master (15 SwiftUI topics), or a milestone like passing an exam. Progress fills in from your sessions and concepts.",
                            fontSize = 14.sp, color = SheetTheme.secondary)
                    }
                }
                listOf("In progress" to active, "Achieved" to done).forEach { (title, list) ->
                    if (list.isNotEmpty()) {
                        item { SectionTitle(title) }
                        item { SheetCard { list.forEachIndexed { i, g -> if (i > 0) Spacer(Modifier.height(16.dp)); GoalRow(g, data) { open = StudySheet.Goal(g, false) } } } }
                    }
                }
            }
            else -> {
                item { PrimaryButton("Log a study session", enabled = data.subjects.isNotEmpty()) { open = StudySheet.Session(newSession(data), true) } }
                val byDay = data.sessions.groupBy { it.date }
                val days = byDay.keys.sortedDescending()
                if (days.isEmpty()) item {
                    SheetCard {
                        Text(if (data.subjects.isEmpty()) "Add a subject first, then log the time you study." else "No sessions yet. Use Start studying for a timer, or log time you've already done.",
                            fontSize = 14.sp, color = SheetTheme.secondary)
                    }
                }
                days.take(60).forEach { day ->
                    val list = byDay[day].orEmpty()
                    item(key = "day-$day") {
                        SheetCard {
                            Row {
                                Text(SheetDate.weekdayDay(day), fontWeight = FontWeight.Bold, color = SheetTheme.text, modifier = Modifier.weight(1f))
                                Text(StudyFormat.minutes(list.sumOf { it.minutes }), fontWeight = FontWeight.Bold, color = SheetTheme.navy)
                            }
                            list.forEach { e -> Spacer(Modifier.height(10.dp)); SessionRow(e, data) { open = StudySheet.Session(e, false) } }
                        }
                    }
                }
            }
        }
    }
    open?.let { StudySheetContent(store, it) { open = null } }
}

/** Routes a [StudySheet] to its screen. */
@Composable
fun StudySheetContent(store: SheetStore, sheet: StudySheet, onDismiss: () -> Unit) {
    when (sheet) {
        is StudySheet.Subject -> SubjectEditorSheet(store, sheet.subject, sheet.isNew, onDismiss)
        is StudySheet.SubjectDetail -> SubjectDetailSheet(store, sheet.id, onDismiss)
        is StudySheet.Topic -> ConceptEditorSheet(store, sheet.topic, sheet.isNew, onDismiss)
        is StudySheet.Session -> StudySessionSheet(store, sheet.entry, sheet.isNew, onDismiss)
        is StudySheet.Goal -> StudyGoalSheet(store, sheet.goal, sheet.isNew, onDismiss)
        is StudySheet.Review -> QuickReviewSheet(store, sheet.topic, onDismiss)
        is StudySheet.ReviewRun -> ReviewRunSheet(store, sheet.topicIds, onDismiss)
        is StudySheet.Syllabus -> SyllabusSheet(store, sheet.subjectId, onDismiss)
        StudySheet.Targets -> StudyTargetsSheet(store, onDismiss)
        StudySheet.StartTimer -> StartStudySheet(store, onDismiss)
        StudySheet.ImportJournal -> JournalImportSheet(store, onDismiss)
    }
}

// MARK: - Overview

@Composable
private fun StudyHero(data: StudyData, settings: SheetSettings, open: (StudySheet) -> Unit) {
    val timer = LocalContext.current.container.studyTimer
    val today = data.minutesOn(LocalDate.now().toString())
    val target = maxOf(settings.studyDailyTarget ?: 60.0, 1.0)
    val week = data.weekMinutes()
    val weekTarget = (settings.studyWeeklyTarget ?: 7.0) * 60
    val current = data.currentTopic
    HeroBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Studied today", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
                Text(StudyFormat.minutes(today), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Target ${StudyFormat.minutes(target)} a day", fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
            }
            Box(Modifier.clip(CircleShape).clickable { open(StudySheet.Targets) }) {
                ScoreRing(today / target, 84.dp, 9.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(SheetFormat.percent(minOf(today / target, 9.99), 0), fontWeight = FontWeight.Bold, color = Color.White)
                        Text("of goal", fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroChip(Icons.Filled.LocalFireDepartment, "${data.streak()} day streak")
            HeroChip(Icons.Filled.CalendarMonth, "${StudyFormat.minutes(week)} / ${StudyFormat.minutes(weekTarget)} this week")
        }
        val running = timer.running
        if (running != null) {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(running.startedAt) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
            val secs = ((now - running.startedAt) / 1000).coerceAtLeast(0)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.14f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(running.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("%d:%02d:%02d".format(secs / 3600, secs / 60 % 60, secs % 60), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                IconButton(onClick = { timer.cancel() }) { Icon(Icons.Filled.Close, "Cancel timer", tint = Color.White.copy(alpha = 0.7f)) }
                Text("Stop", fontWeight = FontWeight.Bold, color = SheetTheme.navyDeep,
                    modifier = Modifier.clip(CircleShape).background(Color.White).clickable {
                        timer.stop(data)?.let { open(StudySheet.Session(it, true)) }
                    }.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        } else if (data.subjects.isNotEmpty()) {
            val subject = current?.let { data.subject(it.subjectId) }
            if (current != null && subject != null) Column {
                Text("Now studying", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f))
                Text(current.topic, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2)
                Text(subject.name, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroButton("Start studying", Icons.Filled.PlayArrow, filled = true, modifier = Modifier.weight(1f)) { open(StudySheet.StartTimer) }
                HeroButton("Log time", Icons.Filled.Schedule, filled = false, modifier = Modifier.weight(1f)) { open(StudySheet.Session(newSession(data), true)) }
            }
        }
    }
}

@Composable
private fun HeroButton(text: String, icon: ImageVector, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clip(CircleShape).background(if (filled) Color.White else Color.White.copy(alpha = 0.18f)).clickable(onClick = onClick)
        .padding(vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (filled) SheetTheme.navyDeep else Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontWeight = FontWeight.Bold, color = if (filled) SheetTheme.navyDeep else Color.White, maxLines = 1)
    }
}

@Composable
private fun StudyEmptyCard(open: (StudySheet) -> Unit) {
    val database = LocalContext.current.container.database
    val sections by remember { database.learning().observeSections() }.collectAsState(initial = emptyList())
    SheetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.School, null, tint = SheetTheme.navy)
            Spacer(Modifier.width(8.dp))
            Text("What are you studying?", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
        }
        Spacer(Modifier.height(8.dp))
        Text("Add each subject or course you're learning. Inside it, list the concepts — then log your study time, mark how well you know each one, and the app tells you when to revise it.",
            fontSize = 14.sp, color = SheetTheme.secondary)
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Add your first subject") { open(StudySheet.Subject(StudySubject(), true)) }
        if (sections.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LinkText("Bring in your Learning journal (${sections.size} sections, ${sections.sumOf { it.topics.size }} topics)", SheetTheme.navy, 14.sp) {
                open(StudySheet.ImportJournal)
            }
        }
    }
}

/** Minutes per day for the last 7 days, stacked by subject colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekChartCard(data: StudyData) {
    val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    val first = days.first().toString()
    val sessions = data.sessions.filter { it.date >= first }
    val total = sessions.sumOf { it.minutes }
    val perDay = days.map { d -> sessions.filter { it.date == d.toString() } }
    val top = maxOf(perDay.maxOfOrNull { list -> list.sumOf { it.minutes } } ?: 0.0, 1.0)
    SheetCard {
        Text("Last 7 days", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
        Text("${StudyFormat.minutes(total)} · avg ${StudyFormat.minutes(total / 7)} a day", fontSize = 12.sp, color = SheetTheme.secondary)
        Spacer(Modifier.height(12.dp))
        if (total == 0.0) {
            Text("Log a session to see your week.", fontSize = 13.sp, color = SheetTheme.secondary, modifier = Modifier.padding(vertical = 40.dp))
        } else {
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                days.forEachIndexed { i, d ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp))) {
                            perDay[i].forEach { e ->
                                Box(Modifier.fillMaxWidth().height((120 * e.minutes / top).dp).background(studyColor(data.subject(e.subjectId)?.color)))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(d.dayOfWeek.name.take(1), fontSize = 11.sp, color = SheetTheme.secondary)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                data.subjects.filter { s -> sessions.any { it.subjectId == s.id } }.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(studyColor(s.color)))
                        Spacer(Modifier.width(4.dp))
                        Text(s.name, fontSize = 11.sp, color = SheetTheme.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardHeader(title: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, modifier = Modifier.weight(1f))
        LinkText(action, SheetTheme.navy, 14.sp, onClick)
    }
}

// MARK: - Rows

@Composable
fun SubjectRow(subject: StudySubject, data: StudyData, onClick: () -> Unit) {
    val tint = studyColor(subject.color)
    val topics = data.topics.filter { it.subjectId == subject.id }
    val week = data.weekMinutes(subjectId = subject.id)
    val days = daysUntil(subject.targetDate)
    val progress = data.progress(subject.id)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        ScoreRing(progress, 44.dp, 5.dp, tint, tint.copy(alpha = 0.15f)) {
            Text(subject.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(subject.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (topics.isEmpty()) "No concepts yet" else "${topics.count { it.stage == StudyTopic.Stage.MASTERED }} of ${topics.size} concepts mastered",
                fontSize = 12.sp, color = SheetTheme.secondary)
            val hours = subject.weeklyHours
            val parts = listOfNotNull(
                if (hours != null && hours > 0) "${StudyFormat.minutes(week)} / ${StudyFormat.number(hours)}h this week" else if (week > 0) "${StudyFormat.minutes(week)} this week" else null,
                if (days != null && subject.status == StudySubject.Status.ACTIVE) (if (days < 0) "Target passed" else "Target ${StudyFormat.countdown(days)}") else null,
            )
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = if (days != null && days in 0..14) SheetTheme.warn else SheetTheme.secondary)
        }
        Text(SheetFormat.percent(progress, 0), fontWeight = FontWeight.Bold, color = tint)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SheetTheme.tertiary)
    }
}

@Composable
fun GoalRow(goal: StudyGoal, data: StudyData, onClick: () -> Unit) {
    val p = data.progress(goal)
    val tint = goalTint(p.state)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(when (goal.kind) { StudyGoal.Kind.HOURS -> Icons.Filled.Schedule; StudyGoal.Kind.TOPICS -> Icons.Filled.Verified; StudyGoal.Kind.MILESTONE -> Icons.Filled.Flag },
                null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(goal.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, modifier = Modifier.weight(1f), maxLines = 2)
            StatusPill(p.state.label, tint)
        }
        if (goal.kind != StudyGoal.Kind.MILESTONE) Bar(p.fraction, tint)
        Text(listOfNotNull(p.detail, data.subject(goal.subjectId)?.name, daysUntil(goal.due)?.let { "due ${StudyFormat.countdown(it)}" }).joinToString(" · "),
            fontSize = 12.sp, color = SheetTheme.secondary)
    }
}

@Composable
fun SessionRow(entry: StudyEntry, data: StudyData, showDate: Boolean = false, onClick: () -> Unit) {
    val subject = data.subject(entry.subjectId)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(kindIcon(entry.kind), studyColor(subject?.color), 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.topic ?: subject?.name ?: entry.subject ?: "Study", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (entry.pending) { Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.Schedule, "Waiting to sync", tint = SheetTheme.sky, modifier = Modifier.size(12.dp)) }
            }
            Text(listOfNotNull(if (showDate) SheetDate.weekdayDay(entry.date) else null, if (entry.topic == null) null else (subject?.name ?: entry.subject),
                entry.kind.label, entry.score?.let { "${StudyFormat.number(it, 0)}%" }, entry.notes).joinToString(" · "),
                fontSize = 12.sp, color = SheetTheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(StudyFormat.minutes(entry.minutes), fontWeight = FontWeight.Bold, color = SheetTheme.text)
    }
}

/** Small Dashboard card: today's study against the target, streak, what's due. */
@Composable
fun StudyTodayCard(snapshot: SheetSnapshot, onOpen: () -> Unit) {
    val data = snapshot.studyData
    val today = data.minutesOn(LocalDate.now().toString())
    val target = maxOf(snapshot.settings.studyDailyTarget ?: 60.0, 1.0)
    val due = data.dueTopics().size
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card).clickable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Icons.Filled.School, SheetTheme.indigo, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text("Study today", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, modifier = Modifier.weight(1f))
                Text("${data.streak()} day streak", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.warn)
            }
            ProgressRow(data.currentTopic?.topic ?: (if (data.subjects.isEmpty()) "Add what you're studying" else "Studied"),
                "${StudyFormat.minutes(today)} / ${StudyFormat.minutes(target)}", today / target, SheetTheme.indigo)
            if (due > 0) Text("$due concept${if (due == 1) "" else "s"} due for revision", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.warn)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SheetTheme.tertiary)
    }
}
