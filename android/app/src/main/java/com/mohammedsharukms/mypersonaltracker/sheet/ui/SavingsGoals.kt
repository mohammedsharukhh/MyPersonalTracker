package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.sheet.SavingsGoal

import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket
import com.mohammedsharukms.mypersonaltracker.sheet.progress
import java.time.LocalDate
import java.time.Period

// Named things you're saving for (22_Goals.gs): a target, an optional date,
// and the money you put aside for each. Finance ▸ Savings. Mirrors SavingsGoals.swift.

/** Which goal sheet is open; held by the screen so the list can live in a LazyColumn. */
class GoalsUi {
    var editing by mutableStateOf<SavingsGoal?>(null)
    var opened by mutableStateOf<String?>(null)
}

fun LazyListScope.savingsGoalItems(snapshot: SheetSnapshot, ui: GoalsUi) {
    val active = snapshot.savingsGoals.filter { !it.done }
    val reached = snapshot.savingsGoals.filter { it.done }
    item {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) { SectionTitle("Savings goals", if (active.isEmpty()) "Save toward something you want" else null) }
            Row(Modifier.clickable { ui.editing = SavingsGoal() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AddCircle, null, tint = SheetTheme.navy)
                Spacer(Modifier.width(4.dp))
                Text("New goal", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = SheetTheme.navy)
            }
        }
    }
    if (snapshot.savingsGoals.isEmpty()) item {
        SheetCard {
            Text("Give your savings a purpose — a trip, a phone, a deposit. Set a target and a date, then choose the goal when you put money aside; it fills up as you go.",
                fontSize = 14.sp, color = SheetTheme.secondary)
        }
    }
    items(active, key = { "goal-${it.id}" }) { goal -> GoalRow(goal, snapshot) { ui.opened = goal.id } }
    if (reached.isNotEmpty()) item {
        SheetCard {
            Text("Reached", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
            reached.forEach { goal ->
                Row(Modifier.fillMaxWidth().clickable { ui.opened = goal.id }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Verified, null, tint = SheetTheme.good)
                    Spacer(Modifier.width(8.dp))
                    Text(goal.name, color = SheetTheme.text, modifier = Modifier.weight(1f))
                    Text(SheetFormat.qar(goal.target), color = SheetTheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun GoalRow(goal: SavingsGoal, snapshot: SheetSnapshot, onClick: () -> Unit) {
    val p = snapshot.progress(goal)
    val done = p.fraction >= 1
    val tint = if (done) SheetTheme.good else SheetTheme.indigo
    val detail = when {
        done -> "Target reached — mark it done"
        goal.due == null -> "${SheetFormat.qar(p.left)} to go"
        LocalDate.parse(goal.due.take(10)).isBefore(LocalDate.now()) -> "Date passed · ${SheetFormat.qar(p.left)} to go"
        else -> "By ${SheetDate.short(goal.due)} · ${SheetFormat.qar(p.left)} to go"
    }
    SheetCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(if (done) Icons.Filled.Verified else Icons.Filled.TrackChanges, tint)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(goal.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = SheetTheme.text)
                    Text(detail, fontSize = 12.sp, color = SheetTheme.secondary)
                }
                Text(SheetFormat.percent(minOf(p.fraction, 9.99), 0), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = tint)
            }
            Bar(p.fraction.coerceIn(0.0, 1.0), tint)
            Row {
                Text("${SheetFormat.qar(p.saved)} of ${SheetFormat.qar(goal.target)} ${Money.main}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = SheetTheme.text, modifier = Modifier.weight(1f))
                p.perMonth?.let { Text("about ${SheetFormat.qar(it)} a month", fontSize = 12.sp, color = SheetTheme.secondary) }
            }
        }
    }
}

/** The goal sheets: editor and detail. Put next to the screen's other sheets. */
@Composable
fun GoalSheets(store: SheetStore, ui: GoalsUi) {
    ui.editing?.let { g -> GoalEditorSheet(store, g, isNew = store.snapshot?.savingsGoals?.none { it.id == g.id } ?: true) { ui.editing = null } }
    ui.opened?.let { id -> GoalDetailSheet(store, id, onEdit = { ui.editing = it }) { ui.opened = null } }
}

@Composable
private fun GoalDetailSheet(store: SheetStore, goalId: String, onEdit: (SavingsGoal) -> Unit, onDismiss: () -> Unit) {
    val snapshot = store.snapshot
    val goal = snapshot?.savingsGoals?.firstOrNull { it.id == goalId }
    var putting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (snapshot == null || goal == null) { onDismiss(); return }
    val p = snapshot.progress(goal)
    val entries = snapshot.setAside.filter { it.goalId == goal.id }.sortedByDescending { it.date }
    EditorSheet(goal.name, onDismiss, saveLabel = "Edit", onSave = { onEdit(goal) }) {
        SheetCard {
            Text("${SheetFormat.qar(p.saved)} ${Money.main}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text)
            Text("of ${SheetFormat.qar(goal.target)} · ${SheetFormat.percent(minOf(p.fraction, 9.99), 0)}", color = SheetTheme.secondary)
            Spacer(Modifier.padding(top = 8.dp))
            Bar(p.fraction.coerceIn(0.0, 1.0), if (p.fraction >= 1) SheetTheme.good else SheetTheme.indigo)
            Spacer(Modifier.padding(top = 6.dp))
            goal.due?.let { KeyValueRow("By", SheetDate.short(it)) }
            if (p.perMonth != null && p.monthsLeft != null) {
                KeyValueRow("To make it", "${SheetFormat.qar(p.perMonth)} a month · ${p.monthsLeft} month${if (p.monthsLeft == 1) "" else "s"}")
            }
            goal.notes?.let { Text(it, fontSize = 14.sp, color = SheetTheme.secondary) }
        }
        PrimaryButton("Put money towards it", Modifier.fillMaxWidth()) { putting = true }
        SheetCard(onClick = {
            store.saveSavingsGoal(if (goal.done) goal.copy(done = false, doneOn = null) else goal.copy(done = true, doneOn = LocalDate.now().toString()))
        }) {
            Text(if (goal.done) "Not reached yet" else "Mark as reached", color = SheetTheme.navy, fontWeight = FontWeight.SemiBold)
        }
        if (entries.isNotEmpty()) FormSection("Put aside for it") {
            entries.forEachIndexed { i, e ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(SheetDate.short(e.date), color = SheetTheme.text)
                        Text(listOfNotNull(e.bucket, e.note).joinToString(" · "), fontSize = 12.sp, color = SheetTheme.secondary)
                    }
                    Text(SheetFormat.qar(e.amount), color = SheetTheme.text)
                    Spacer(Modifier.width(12.dp))
                    Text("Delete", fontSize = 13.sp, color = SheetTheme.bad, modifier = Modifier.clickable { store.deleteSetAside(e.id) })
                }
            }
        }
        SheetCard(onClick = { confirmDelete = true }) { Text("Delete goal", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold) }
        Text("Money you put aside stays in your savings — it just stops counting toward this goal.", fontSize = 12.sp, color = SheetTheme.secondary)
    }
    if (putting) PutAsideSheet(store, SplitBucket.SAVINGS, goalId = goal.id) { putting = false }
    if (confirmDelete) AppDialog(
        title = "Delete ${goal.name}?",
        message = "The money you put aside for it stays in your savings.",
        style = DialogStyle.DESTRUCTIVE,
        confirmText = "Delete goal",
        onConfirm = { confirmDelete = false; store.deleteSavingsGoal(goal.id); onDismiss() },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun GoalEditorSheet(store: SheetStore, initial: SavingsGoal, isNew: Boolean, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var target by remember { mutableStateOf(if (initial.target > 0) SheetFormat.plain(initial.target) else "") }
    var due by remember { mutableStateOf(initial.due?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }) }
    var notes by remember { mutableStateOf(initial.notes ?: "") }
    val amount = SheetFormat.input(target)?.takeIf { it > 0 }
    EditorSheet(if (isNew) "New goal" else "Edit goal", onDismiss, canSave = name.isNotBlank() && amount != null, onSave = {
        store.saveSavingsGoal(initial.copy(name = name.trim(), target = amount!!, due = due?.toString(), notes = notes.trim().ifEmpty { null }))
        onDismiss()
    }) {
        Field("What for?") { PlainField(name, "e.g. Trip to Kerala, new phone") { name = it } }
        NumberField("Target", target, Money.main) { target = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }
        DateField("By a date", due, allowClear = true) { due = it }
        Field("Notes (optional)") { PlainField(notes, "Anything to remember", singleLine = false) { notes = it } }
        val d = due
        if (d != null && amount != null) {
            val months = maxOf(1, Period.between(LocalDate.now(), d).let { it.years * 12 + it.months } + 1)
            Text("About ${SheetFormat.qar(amount / months)} ${Money.main} a month over $months month${if (months == 1) "" else "s"}.",
                fontSize = 12.sp, color = SheetTheme.secondary)
        }
    }
}
