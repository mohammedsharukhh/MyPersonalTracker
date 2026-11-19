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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.sheet.GoalArea
import com.mohammedsharukms.mypersonaltracker.sheet.GoalHorizon
import com.mohammedsharukms.mypersonaltracker.sheet.GoalStatus
import com.mohammedsharukms.mypersonaltracker.sheet.GoalStep
import com.mohammedsharukms.mypersonaltracker.sheet.GoalTime
import com.mohammedsharukms.mypersonaltracker.sheet.LifeGoal
import com.mohammedsharukms.mypersonaltracker.sheet.LifeGoalData
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.active
import com.mohammedsharukms.mypersonaltracker.sheet.childrenOf
import com.mohammedsharukms.mypersonaltracker.sheet.goal
import com.mohammedsharukms.mypersonaltracker.sheet.isStale
import com.mohammedsharukms.mypersonaltracker.sheet.lifeGoalData
import com.mohammedsharukms.mypersonaltracker.sheet.nextStep
import com.mohammedsharukms.mypersonaltracker.sheet.progressOf
import com.mohammedsharukms.mypersonaltracker.sheet.reachedInMonth
import com.mohammedsharukms.mypersonaltracker.sheet.stepsOf
import java.time.LocalDate

// Daily ▸ Goals: short-term goals (the next few months) and long-term goals
// (a year or more), each with steps. Mirrors LifeGoalsView.swift.

/** Which goal sheet is open; held by the screen so the list can live in a LazyColumn. */
class GoalUi {
    var editing by mutableStateOf<Pair<LifeGoal, Boolean>?>(null)
    var opened by mutableStateOf<String?>(null)
    var showReached by mutableStateOf(false)
}

private val ShortTint = Color(0xFF4F46E5)
private val LongTint = Color(0xFF7C3AED)

fun LazyListScope.lifeGoalItems(store: SheetStore, snapshot: SheetSnapshot, ui: GoalUi) {
    val data = snapshot.lifeGoalData
    val short = data.active(GoalHorizon.SHORT)
    val long = data.active(GoalHorizon.LONG)
    val reached = data.goals.filter { it.status == GoalStatus.REACHED }.sortedByDescending { it.doneOn ?: "" }
    item { GoalsHero(data, short.size, long.size) { ui.editing = LifeGoal() to true } }
    if (data.goals.isEmpty()) item {
        SheetCard {
            Text("Where do you want to be?", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            Text("Long-term goals are the big picture — a role, a home, a fitness level, a year or more away. Short-term goals are the next few months, and the steps that get you there. Link a short-term goal to a long-term one and it moves the bigger goal along.",
                fontSize = 14.sp, color = SheetTheme.secondary, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("Short-term goal", Icons.Filled.Flag, SheetTheme.navy, false) { ui.editing = LifeGoal(horizon = GoalHorizon.SHORT) to true }
                FilterPill("Long-term goal", Icons.Filled.Flag, SheetTheme.navy, false) { ui.editing = LifeGoal(horizon = GoalHorizon.LONG) to true }
            }
        }
    }
    if (data.goals.isNotEmpty()) {
        item { HorizonHeader(GoalHorizon.SHORT) { ui.editing = LifeGoal(horizon = GoalHorizon.SHORT) to true } }
        if (short.isEmpty()) item { Text("Nothing for the next few months yet.", fontSize = 14.sp, color = SheetTheme.secondary) }
        items(short, key = { "g-${it.id}" }) { GoalCard(it, data) { ui.opened = it.id } }
        item { HorizonHeader(GoalHorizon.LONG) { ui.editing = LifeGoal(horizon = GoalHorizon.LONG) to true } }
        if (long.isEmpty()) item { Text("Add a bigger goal to give the short ones a direction.", fontSize = 14.sp, color = SheetTheme.secondary) }
        items(long, key = { "g-${it.id}" }) { GoalCard(it, data) { ui.opened = it.id } }
    }
    if (reached.isNotEmpty()) item {
        SheetCard(onClick = { ui.showReached = !ui.showReached }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = SheetTheme.good, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reached · ${reached.size}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.good, modifier = Modifier.weight(1f))
                Text(if (ui.showReached) "Hide" else "Show", fontSize = 13.sp, color = SheetTheme.secondary)
            }
            if (ui.showReached) reached.forEach { g ->
                Row(Modifier.fillMaxWidth().clickable { ui.opened = g.id }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AreaLine(g.area, g.title, SheetTheme.text, Modifier.weight(1f))
                    g.doneOn?.let { Text(SheetDate.short(it), fontSize = 12.sp, color = SheetTheme.secondary) }
                }
            }
        }
    }
}

@Composable
private fun GoalsHero(data: LifeGoalData, short: Int, long: Int, onAdd: () -> Unit) {
    val active = data.active
    val average = if (active.isEmpty()) 0.0 else active.sumOf { data.progressOf(it) } / active.size
    val thisMonth = data.reachedInMonth().size
    HeroBox(Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF6D28D9)))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Goals", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
                Text("$short short term · $long long term", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(if (thisMonth > 0) "$thisMonth reached this month 🎉" else "Keep the next step small.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            }
            ScoreRing(average, 62.dp) {
                Text(SheetFormat.percent(average, 0), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Box(Modifier.fillMaxWidth().clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onAdd).padding(vertical = 10.dp),
            contentAlignment = Alignment.Center) {
            Text("＋  New goal", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

@Composable
private fun HorizonHeader(horizon: GoalHorizon, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) { SectionTitle(horizon.label, horizon.hint) }
        IconButton(onAdd) { Icon(Icons.Filled.AddCircle, "Add a ${horizon.label.lowercase()} goal", tint = SheetTheme.navy) }
    }
}

@Composable
private fun GoalCard(goal: LifeGoal, data: LifeGoalData, onClick: () -> Unit) {
    val progress = data.progressOf(goal)
    val tint = if (goal.horizon == GoalHorizon.SHORT) ShortTint else LongTint
    val steps = data.stepsOf(goal.id)
    val kids = data.childrenOf(goal.id)
    val due = GoalTime.due(goal.due)
    SheetCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(goal.area.icon, goal.area.label, tint = tint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(goal.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                    Text(listOfNotNull(goal.area.label, due).joinToString(" · "), fontSize = 12.sp,
                        color = if (due?.contains("overdue") == true) SheetTheme.bad else SheetTheme.secondary)
                }
                Text(SheetFormat.percent(progress, 0), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = tint)
            }
            Bar(progress, tint)
            val next = data.nextStep(goal)
            if (next != null) Text("→ Next: ${next.step}", fontSize = 12.sp, color = SheetTheme.text, maxLines = 1)
            else if (steps.isEmpty() && kids.isEmpty()) Text("＋ Add the first step", fontSize = 12.sp, color = SheetTheme.navy)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (steps.isNotEmpty()) Text("${steps.count { it.done }}/${steps.size} steps", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
                if (kids.isNotEmpty()) Text("${kids.size} short-term goal${if (kids.size == 1) "" else "s"}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
                data.goal(goal.parentId)?.let { Text("⛰ ${it.title}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LongTint, maxLines = 1) }
                if (data.isStale(goal)) Text("⏳ No progress in 2 weeks", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.warn)
            }
        }
    }
}

/** The goal sheets: editor and detail. Put next to the screen's other sheets. */
@Composable
fun GoalSheets(store: SheetStore, ui: GoalUi) {
    ui.opened?.let { id -> GoalDetailSheet(store, id, onEdit = { g, isNew -> ui.editing = g to isNew }, onOpen = { ui.opened = it }) { ui.opened = null } }
    ui.editing?.let { (g, isNew) -> GoalEditorSheet(store, g, isNew) { ui.editing = null } }
}

@Composable
private fun GoalDetailSheet(store: SheetStore, goalId: String, onEdit: (LifeGoal, Boolean) -> Unit, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val data = store.snapshot?.lifeGoalData
    val goal = data?.goal(goalId)
    var newStep by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    if (data == null || goal == null) { onDismiss(); return }
    val progress = data.progressOf(goal)
    val steps = data.stepsOf(goal.id)
    val kids = data.childrenOf(goal.id)
    val tint = if (goal.horizon == GoalHorizon.SHORT) ShortTint else LongTint
    fun addStep() {
        val text = newStep.trim()
        if (text.isEmpty()) return
        store.saveGoalStep(GoalStep(goalId = goal.id, step = text, order = (steps.mapNotNull { it.order }.maxOrNull() ?: steps.size.toDouble()) + 1))
        newStep = ""
    }
    EditorSheet(goal.horizon.label, onDismiss, saveLabel = "Edit", onSave = { onEdit(goal, false) }) {
        SheetCard {
            AreaLine(goal.area, goal.area.label + if (goal.status != GoalStatus.ACTIVE) " · ${goal.status.label}" else "",
                tint, fontSize = 12.sp, weight = FontWeight.SemiBold)
            Text(goal.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text, modifier = Modifier.padding(top = 4.dp))
            GoalTime.due(goal.due)?.let { Text(it, fontSize = 14.sp, color = SheetTheme.secondary) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                Box(Modifier.weight(1f)) { Bar(progress, tint) }
                Spacer(Modifier.width(10.dp))
                Text(SheetFormat.percent(progress, 0), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text)
            }
            goal.why?.let { Text("Why: $it", fontSize = 14.sp, fontStyle = FontStyle.Italic, color = SheetTheme.secondary, modifier = Modifier.padding(top = 8.dp)) }
            data.goal(goal.parentId)?.let { p ->
                Text("⛰ Part of: ${p.title}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LongTint,
                    modifier = Modifier.padding(top = 6.dp).clickable { onOpen(p.id) })
            }
        }
        FormSection("Steps", footer = if (steps.isEmpty() && kids.isEmpty()) "Break it into small, concrete steps — progress moves as you tick them off." else null) {
            steps.forEachIndexed { i, step ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().clickable { store.saveGoalStep(step.copy(done = !step.done)) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (step.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                        tint = if (step.done) SheetTheme.good else SheetTheme.tertiary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(step.step, color = if (step.done) SheetTheme.secondary else SheetTheme.text,
                            textDecoration = if (step.done) TextDecoration.LineThrough else null)
                        if (step.done) step.doneOn?.let { Text("Done ${SheetDate.short(it)}", fontSize = 11.sp, color = SheetTheme.secondary) }
                    }
                    Text("✕", fontSize = 14.sp, color = SheetTheme.tertiary, modifier = Modifier.clickable { store.deleteGoalStep(step.id) }.padding(6.dp))
                }
            }
            if (steps.isNotEmpty()) RowDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PlainField(newStep, "Add a step") { newStep = it } }
                Spacer(Modifier.width(8.dp))
                SmallTextButton("Add") { addStep() }
            }
        }
        if (goal.horizon == GoalHorizon.LONG) FormSection("Short-term goals toward it", footer = "Each counts toward this goal's progress.") {
            kids.forEachIndexed { i, kid ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().clickable { onOpen(kid.id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        AreaLine(kid.area, kid.title, SheetTheme.text, fontSize = 16.sp)
                        GoalTime.due(kid.due)?.let { Text(it, fontSize = 12.sp, color = SheetTheme.secondary) }
                    }
                    Text(SheetFormat.percent(data.progressOf(kid), 0), fontWeight = FontWeight.SemiBold,
                        color = if (kid.status == GoalStatus.REACHED) SheetTheme.good else ShortTint)
                }
            }
            if (kids.isNotEmpty()) RowDivider()
            Text("⚑ Add a short-term goal for this", color = SheetTheme.navy, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clickable { onEdit(LifeGoal(horizon = GoalHorizon.SHORT, area = goal.area, parentId = goal.id), true) }.padding(14.dp))
        }
        if (steps.isEmpty() && kids.isEmpty() && goal.status == GoalStatus.ACTIVE) {
            SheetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Progress", color = SheetTheme.text)
                    Spacer(Modifier.width(10.dp))
                    var value by remember(goal.progress) { mutableStateOf((goal.progress ?: 0.0).toFloat()) }
                    Slider(value, { value = it }, Modifier.weight(1f), valueRange = 0f..1f, steps = 19,
                        onValueChangeFinished = { store.saveLifeGoal(goal.copy(progress = Math.round(value * 20) / 20.0)) })
                    Text(SheetFormat.percent(value.toDouble(), 0), color = SheetTheme.text, modifier = Modifier.width(44.dp))
                }
                Text("No steps yet, so set how far along you are by hand.", fontSize = 12.sp, color = SheetTheme.secondary)
            }
        }
        goal.notes?.let { SheetCard { Text(it, color = SheetTheme.text) } }
        if (goal.status == GoalStatus.ACTIVE) {
            PrimaryButton("Mark as reached", Modifier.fillMaxWidth()) {
                store.saveLifeGoal(goal.copy(status = GoalStatus.REACHED, doneOn = LocalDate.now().toString()))
            }
            SheetCard(onClick = { store.saveLifeGoal(goal.copy(status = GoalStatus.DROPPED)) }) {
                Text("Drop this goal", color = SheetTheme.secondary, fontWeight = FontWeight.SemiBold)
            }
        } else {
            SheetCard(onClick = { store.saveLifeGoal(goal.copy(status = GoalStatus.ACTIVE, doneOn = null)) }) {
                Text("Back to active", color = SheetTheme.navy, fontWeight = FontWeight.SemiBold)
            }
        }
        SheetCard(onClick = { confirmDelete = true }) { Text("Delete goal", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold) }
        if (goal.horizon == GoalHorizon.LONG && kids.isNotEmpty()) Text("Deleting it keeps its short-term goals.", fontSize = 12.sp, color = SheetTheme.secondary)
    }
    if (confirmDelete) AppDialog(
        title = "Delete ${goal.title}?",
        message = if (goal.horizon == GoalHorizon.LONG) "Its steps are deleted too. Short-term goals under it stay." else "Its steps are deleted too.",
        style = DialogStyle.DESTRUCTIVE,
        confirmText = "Delete goal",
        onConfirm = { confirmDelete = false; store.deleteLifeGoal(goal.id); onDismiss() },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun GoalEditorSheet(store: SheetStore, initial: LifeGoal, isNew: Boolean, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var horizon by remember { mutableStateOf(initial.horizon) }
    var area by remember { mutableStateOf(initial.area) }
    var due by remember {
        mutableStateOf(initial.due?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
            ?: if (isNew) LocalDate.now().plusMonths(if (initial.horizon == GoalHorizon.SHORT) 3 else 12) else null)
    }
    var parentId by remember { mutableStateOf(initial.parentId) }
    var why by remember { mutableStateOf(initial.why ?: "") }
    var notes by remember { mutableStateOf(initial.notes ?: "") }
    var firstSteps by remember { mutableStateOf("") }
    val longGoals = store.snapshot?.lifeGoalData?.active(GoalHorizon.LONG).orEmpty().filter { it.id != initial.id }
    EditorSheet(if (isNew) "New goal" else "Edit goal", onDismiss, canSave = title.isNotBlank(), onSave = {
        val saved = initial.copy(title = title.trim(), horizon = horizon, area = area, due = due?.toString(),
            parentId = if (horizon == GoalHorizon.SHORT) parentId else null, why = why.trim().ifEmpty { null }, notes = notes.trim().ifEmpty { null })
        store.saveLifeGoal(saved)
        firstSteps.lines().map { it.trim().trimStart('-', '•', '*', '·').trim() }.filter { it.isNotEmpty() }
            .forEachIndexed { i, s -> store.saveGoalStep(GoalStep(goalId = saved.id, step = s, order = (i + 1).toDouble())) }
        onDismiss()
    }) {
        Field("Goal") { PlainField(title, if (horizon == GoalHorizon.SHORT) "e.g. Run 5 km without stopping" else "e.g. Become a senior iOS developer") { title = it } }
        Field("Horizon") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalHorizon.entries.forEach { h -> FilterPill(h.label, null, SheetTheme.navy, horizon == h) { horizon = h } }
            }
            Text("${horizon.label}: ${horizon.hint.lowercase()}.", fontSize = 12.sp, color = SheetTheme.secondary)
        }
        Field("Area") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalArea.entries.forEach { a -> FilterPill(a.label, a.icon, SheetTheme.navy, area == a) { area = a } }
            }
        }
        DateField("By a date", due, allowClear = true) { due = it }
        due?.let { d ->
            val suggested = GoalTime.suggestedHorizon(d)
            if (suggested != horizon) Text("That's ${if (suggested == GoalHorizon.SHORT) "only a few months" else "a long way"} off — make it ${suggested.label.lowercase()}?",
                fontSize = 13.sp, color = SheetTheme.navy, modifier = Modifier.clickable { horizon = suggested })
        }
        if (horizon == GoalHorizon.SHORT && longGoals.isNotEmpty()) Field("Builds toward") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("Nothing in particular", null, LongTint, parentId == null) { parentId = null }
                longGoals.forEach { g -> FilterPill(g.title, g.area.icon, LongTint, parentId == g.id) { parentId = g.id } }
            }
        }
        Field("Why does it matter? (optional)") { PlainField(why, "Shown on the goal, for the days it's hard", singleLine = false) { why = it } }
        if (isNew) Field("First steps (optional)") { PlainField(firstSteps, "One step per line", singleLine = false) { firstSteps = it } }
        Field("Notes (optional)") { PlainField(notes, "Anything else", singleLine = false) { notes = it } }
    }
}

/** The short-term goals closest to their date, on the Dashboard. */
@Composable
fun GoalsFocusCard(snapshot: SheetSnapshot, onOpen: () -> Unit) {
    val data = snapshot.lifeGoalData
    val focus = data.active(GoalHorizon.SHORT).take(3)
    if (focus.isEmpty()) return
    SheetCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Flag, null, tint = ShortTint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Goals in focus", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, modifier = Modifier.weight(1f))
            Text("›", fontSize = 18.sp, color = SheetTheme.tertiary)
        }
        focus.forEach { g ->
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    AreaLine(g.area, g.title, SheetTheme.text, Modifier.weight(1f), weight = FontWeight.Medium)
                    Text(SheetFormat.percent(data.progressOf(g), 0), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ShortTint)
                }
                Bar(data.progressOf(g), ShortTint)
                data.nextStep(g)?.let { Text("Next: ${it.step}", fontSize = 12.sp, color = SheetTheme.secondary, maxLines = 1) }
            }
        }
    }
}

/** Each life area's symbol (in place of the emoji it used to have). */
internal val GoalArea.icon: androidx.compose.ui.graphics.vector.ImageVector get() = when (this) {
    GoalArea.HEALTH -> Icons.Filled.Favorite
    GoalArea.CAREER -> Icons.Filled.Work
    GoalArea.MONEY -> Icons.Filled.Payments
    GoalArea.LEARNING -> Icons.Filled.School
    GoalArea.PERSONAL -> Icons.Filled.Spa
    GoalArea.RELATIONSHIPS -> Icons.Filled.People
    GoalArea.HOME -> Icons.Filled.Home
    GoalArea.TRAVEL -> Icons.Filled.Flight
    GoalArea.OTHER -> Icons.Filled.Star
}

/** A goal area's symbol, then some text on one line. */
@Composable
private fun AreaLine(area: GoalArea, text: String, color: Color, modifier: Modifier = Modifier,
                     fontSize: androidx.compose.ui.unit.TextUnit = 14.sp, weight: FontWeight? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(area.icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = fontSize, fontWeight = weight, color = color, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
