package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.reminders.ReminderScheduler
import com.mohammedsharukms.mypersonaltracker.sheet.ParentActions
import com.mohammedsharukms.mypersonaltracker.sheet.ParentDate
import com.mohammedsharukms.mypersonaltracker.sheet.ParentDateKind
import com.mohammedsharukms.mypersonaltracker.sheet.ParentMedicine
import com.mohammedsharukms.mypersonaltracker.sheet.ParentWho
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Parents ▸ Medicines (what they take, how many are left, Refilled) and
// Parents ▸ Dates (birthdays, anniversaries, remembrance days). Mirrors ParentCareViews.swift.

// MARK: - Medicines

@Composable
fun ParentMedicineRow(m: ParentMedicine, onEdit: () -> Unit, onRefill: () -> Unit) {
    val low = m.runsLow()
    val out = (m.daysLeft() ?: 1) < 1
    val tint = if (m.stopped) SheetTheme.secondary else if (out) SheetTheme.bad else if (low) SheetTheme.warn else SheetTheme.teal
    SheetCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Medication, tint, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                Text(listOfNotNull(m.who.label, m.dose).joinToString(" · "), fontSize = 12.sp, color = SheetTheme.secondary)
                Text(m.status(), fontSize = 12.sp, fontWeight = if (low) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (out) SheetTheme.bad else if (low) SheetTheme.warn else SheetTheme.secondary)
            }
            if (!m.stopped) SmallTextButton("Refilled", color = if (low) SheetTheme.warn else SheetTheme.navy, onClick = onRefill)
        }
    }
}

@Composable
fun ParentMedicineEditorSheet(store: SheetStore, initial: ParentMedicine, isNew: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var who by remember { mutableStateOf(initial.who) }
    var name by remember { mutableStateOf(initial.name) }
    var dose by remember { mutableStateOf(initial.dose ?: "") }
    var perDay by remember { mutableStateOf(SheetFormat.plain(initial.perDay, 2)) }
    var leftNow by remember { mutableStateOf(SheetFormat.plain(initial.leftOn(), 1)) }
    var warn by remember { mutableStateOf(initial.warn.toString()) }
    var pack by remember { mutableStateOf(initial.pack?.let { SheetFormat.plain(it) } ?: "") }
    var cost by remember { mutableStateOf(initial.cost?.let { SheetFormat.plain(it) } ?: "") }
    var finance by remember { mutableStateOf(initial.finance) }
    var stopped by remember { mutableStateOf(initial.stopped) }
    var notes by remember { mutableStateOf(initial.notes ?: "") }
    var confirmRemove by remember { mutableStateOf(false) }
    if (confirmRemove) AppDialog(
        title = "Delete ${initial.name}?", message = "Its refill reminders stop.", style = DialogStyle.DESTRUCTIVE, confirmText = "Delete",
        onConfirm = { confirmRemove = false; store.deleteParentMedicine(initial.id); ReminderScheduler.reschedule(context); onDismiss() },
        onDismiss = { confirmRemove = false },
    )
    EditorSheet(if (isNew) "New medicine" else initial.name, onDismiss, canSave = name.isNotBlank(), onSave = {
        val amount = SheetFormat.input(cost)?.takeIf { it > 0 }
        store.saveParentMedicine(initial.copy(who = who, name = name.trim(), dose = dose.trim().ifEmpty { null },
            perDay = SheetFormat.input(perDay)?.coerceAtLeast(0.0) ?: initial.perDay,
            // What's left now becomes the new count.
            left = SheetFormat.input(leftNow)?.coerceAtLeast(0.0) ?: initial.leftOn(), counted = LocalDate.now().toString(),
            warn = warn.toIntOrNull()?.coerceIn(1, 60) ?: 7, pack = SheetFormat.input(pack)?.takeIf { it > 0 },
            cost = amount, finance = amount != null && finance, stopped = stopped, notes = notes.trim().ifEmpty { null }))
        ReminderScheduler.reschedule(context)
        onDismiss()
    }) {
        Field("For") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ParentWho.entries.forEach { w -> FilterPill(w.label, null, SheetTheme.navy, who == w) { who = w } } } }
        Field("Medicine") { PlainField(name, "e.g. Telmisartan 40 mg") { name = it } }
        Field("How they take it (optional)") { PlainField(dose, "e.g. 1 morning, 1 night") { dose = it } }
        NumberField("A day", perDay, "tablets") { perDay = it }
        NumberField("Left now", leftNow, "") { leftNow = it }
        NumberField("Warn me", warn, "days before") { warn = it }
        val a = SheetFormat.input(perDay) ?: 0.0
        val l = SheetFormat.input(leftNow) ?: 0.0
        Text(if (a > 0) "Counts down by itself — at ${SheetFormat.plain(a, 2)} a day, ${SheetFormat.plain(l, 1)} last ${(l / a).toInt()} days. The daily Mom & Dad notification warns you before they run out."
            else "Set how many a day so it can count down.", fontSize = 12.sp, color = SheetTheme.secondary)
        NumberField("Pack of (optional)", pack, "") { pack = it }
        NumberField("A pack costs (optional)", cost, Money.main) { cost = it }
        if ((SheetFormat.input(cost) ?: 0.0) > 0) SheetCard(padding = 4.dp) { ToggleRow("Add to Finance at each refill", finance) { finance = it } }
        Field("Notes (optional)") { PlainField(notes, "Doctor, pharmacy…", singleLine = false) { notes = it } }
        if (!isNew) {
            SheetCard(padding = 4.dp) { ToggleRow("They've stopped taking it", stopped) { stopped = it } }
            SheetCard(onClick = { confirmRemove = true }) { Text("Delete", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Adds the new pack to what's left (and its cost to Finance, if set). */
@Composable
fun ParentRefillDialog(store: SheetStore, m: ParentMedicine, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var bought by remember { mutableStateOf(SheetFormat.plain(m.pack ?: 30.0)) }
    var cost by remember { mutableStateOf(m.cost?.let { SheetFormat.plain(it) } ?: "") }
    var toFinance by remember { mutableStateOf(m.finance) }
    val add = SheetFormat.input(bought) ?: 0.0
    AppDialog(
        title = "Refilled ${m.name}",
        icon = Icons.Filled.Medication,
        message = "${SheetFormat.plain(m.leftOn(), 1)} left + ${SheetFormat.plain(add, 1)} = ${SheetFormat.plain(m.leftOn() + add, 1)}" +
            if (m.perDay > 0) ", about ${((m.leftOn() + add) / m.perDay).toInt()} days." else ".",
        confirmText = "Save",
        onConfirm = { ParentActions.refill(context, store, m, add.coerceAtLeast(0.0), SheetFormat.input(cost), toFinance); onDismiss() },
        onDismiss = onDismiss,
    ) {
        NumberField("Bought", bought, "") { bought = it }
        NumberField("It cost (optional)", cost, Money.main) { cost = it }
        if ((SheetFormat.input(cost) ?: 0.0) > 0) ToggleRow("Add to Finance", toFinance) { toFinance = it }
    }
}

// MARK: - Dates

internal val ParentDateKind.icon: ImageVector get() = when (this) {
    ParentDateKind.BIRTHDAY -> Icons.Filled.Cake
    ParentDateKind.ANNIVERSARY -> Icons.Filled.Favorite
    ParentDateKind.REMEMBRANCE -> Icons.Filled.LocalFireDepartment
    ParentDateKind.OTHER -> Icons.Filled.CalendarMonth
}

@Composable
fun ParentDateRow(d: ParentDate, onEdit: () -> Unit) {
    val soon = (d.daysUntil() ?: 99) <= 7
    SheetCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(d.kind.icon, if (soon) SheetTheme.warn else SheetTheme.purple, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.headline(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                Text("${d.who.label} · ${d.next()?.format(DateTimeFormatter.ofPattern("EEE, d MMM")) ?: d.day}", fontSize = 12.sp, color = SheetTheme.secondary)
            }
            StatusPill(d.whenText(), if (soon) SheetTheme.warn else SheetTheme.navy)
        }
    }
}

@Composable
fun ParentDateEditorSheet(store: SheetStore, initial: ParentDate, isNew: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial.title) }
    var who by remember { mutableStateOf(initial.who) }
    var kind by remember { mutableStateOf(initial.kind) }
    val parts = initial.day.split("-").mapNotNull { it.toIntOrNull() }
    var picked by remember {
        mutableStateOf(runCatching { LocalDate.of(initial.year ?: LocalDate.now().year, parts[0], parts[1]) }.getOrDefault(LocalDate.now()))
    }
    var knowYear by remember { mutableStateOf(initial.year != null) }
    var remind by remember { mutableStateOf(initial.remind) }
    var notes by remember { mutableStateOf(initial.notes ?: "") }
    var confirmRemove by remember { mutableStateOf(false) }
    if (confirmRemove) AppDialog(
        title = "Delete ${initial.title}?", message = "Its reminders stop.", style = DialogStyle.DESTRUCTIVE, confirmText = "Delete",
        onConfirm = { confirmRemove = false; store.deleteParentDate(initial.id); ReminderScheduler.reschedule(context); onDismiss() },
        onDismiss = { confirmRemove = false },
    )
    EditorSheet(if (isNew) "New date" else initial.title, onDismiss, canSave = title.isNotBlank() && remind.isNotEmpty(), onSave = {
        store.saveParentDate(initial.copy(title = title.trim(), who = who, kind = kind, day = "%02d-%02d".format(picked.monthValue, picked.dayOfMonth),
            year = if (knowYear) picked.year else null, remind = remind, notes = notes.trim().ifEmpty { null }))
        ReminderScheduler.reschedule(context)
        onDismiss()
    }) {
        Field("Kind") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParentDateKind.entries.forEach { k -> FilterPill(k.label, k.icon, SheetTheme.navy, kind == k) { kind = k } }
            }
        }
        Field("Whose") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ParentWho.entries.forEach { w -> FilterPill(w.label, null, SheetTheme.navy, who == w) { who = w } } } }
        Field("What") {
            PlainField(title, when (kind) {
                ParentDateKind.BIRTHDAY -> if (who == ParentWho.DAD) "e.g. Dad's birthday" else "e.g. Mom's birthday"
                ParentDateKind.ANNIVERSARY -> "e.g. Their wedding anniversary"
                ParentDateKind.REMEMBRANCE -> "e.g. Grandpa's remembrance day"
                ParentDateKind.OTHER -> "e.g. Housewarming day"
            }) { title = it }
        }
        DateField("Day", picked, allowClear = false) { if (it != null) picked = it }
        SheetCard(padding = 4.dp) {
            ToggleRow(when (kind) { ParentDateKind.BIRTHDAY -> "I know the birth year"; ParentDateKind.ANNIVERSARY -> "I know the year they married"; else -> "I know the year" },
                knowYear, if (knowYear) "It says how many years it'll be" else "Every year on this day") { knowYear = it }
        }
        Field("Remind me") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 14, 7, 3, 1, 0).forEach { n ->
                    FilterPill(if (n == 0) "On the day" else "$n day${if (n == 1) "" else "s"}", null, SheetTheme.navy, n in remind) {
                        remind = if (n in remind) remind - n else (remind + n).sortedDescending()
                    }
                }
            }
        }
        Field("Notes (optional)") { PlainField(notes, "Gift ideas, where…", singleLine = false) { notes = it } }
        if (!isNew) SheetCard(onClick = { confirmRemove = true }) { Text("Delete", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold) }
    }
}
