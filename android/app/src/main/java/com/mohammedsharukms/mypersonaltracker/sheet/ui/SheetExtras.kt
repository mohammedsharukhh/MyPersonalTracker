package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.sheet.PendingWrite
import com.mohammedsharukms.mypersonaltracker.sheet.RecurringExpense
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSettings
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

// The pieces around the main screens: sync status, the Undo bar, starting the
// next period, and monthly bills. Mirrors SheetExtras.swift.

// MARK: - Sync status

/** "2 changes waiting — will sync when you're online", above a screen's content. */
@Composable
fun SyncStatusBar(store: SheetStore) {
    store.setupProgress?.let { SetupProgressCard(it) }
    QueueStatus(store)
}

/** The loading state of a screen with nothing to show yet: what's happening, and how far along. */
@Composable
fun SheetLoadingCard(progress: com.mohammedsharukms.mypersonaltracker.sheet.SheetLoadProgress?) {
    val step = progress ?: com.mohammedsharukms.mypersonaltracker.sheet.SheetLoadProgress.reading
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val fraction = step.fraction
        if (fraction != null) Box(Modifier.width(240.dp)) { Bar(fraction, SheetTheme.navy) }
        else androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(step.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
        step.note?.let { Text(it, fontSize = 13.sp, color = SheetTheme.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}

/** Shown while the sheet rebuilds after a reset (or first setup); the app loads it by itself when done. */
@Composable
private fun SetupProgressCard(progress: String) {
    // "5 of 13 steps done" → 5/13.
    val numbers = Regex("\\d+").findAll(progress).map { it.value.toDouble() }.toList()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SheetTheme.navy.copy(alpha = 0.08f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Rebuilding your sheet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, modifier = Modifier.weight(1f))
            if (numbers.size >= 2) Text("Step ${numbers[0].toInt()} of ${numbers[1].toInt()}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
        }
        if (numbers.size >= 2 && numbers[1] > 0) Bar(numbers[0] / numbers[1], SheetTheme.navy)
        Text("This takes about 3–5 minutes. Keep the app open — your fresh sheet loads by itself as soon as it's ready.",
            fontSize = 12.sp, color = SheetTheme.secondary)
    }
}

@Composable
private fun QueueStatus(store: SheetStore) {
    var showingQueue by remember { mutableStateOf(false) }
    // A save normally lands in a second or two: only say so if one is taking longer (or there's no connection).
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(store.pending.isNotEmpty()) {
        while (store.pending.isNotEmpty()) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1_000) }
    }
    val ready = store.pending.filter { it.notBefore == null || it.notBefore <= now }
    val waiting = ready.size
    val slow = ready.any { now - maxOf(it.createdAt, it.notBefore ?: it.createdAt) > 5_000 }
    if (store.pending.isEmpty() || (waiting == 0 && !store.isOffline) || !(slow || store.isOffline)) return
    val tint = if (store.isOffline) SheetTheme.warn else SheetTheme.sky
    val plural = if (waiting == 1) "" else "s"
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.1f))
            .clickable { showingQueue = true }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (store.isOffline) Icons.Filled.CloudOff else Icons.Filled.CloudSync, null, tint = tint)
        Column(Modifier.weight(1f)) {
            Text(if (store.isOffline) "Offline — $waiting change$plural saved on this phone" else "Syncing $waiting change$plural…",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            Text(if (store.isOffline) "They'll be sent to your sheet automatically when you're back online."
                 else "Figures marked with a clock update once the sheet confirms.",
                fontSize = 12.sp, color = SheetTheme.secondary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SheetTheme.tertiary)
    }
    if (showingQueue) QueueSheet(store) { showingQueue = false }
}

@Composable
private fun QueueSheet(store: SheetStore, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    EditorSheet("Waiting to sync", onDismiss, saveLabel = "Retry", canSave = !store.isSyncing,
        onSave = { scope.launch { store.flush() } }) {
        if (store.pending.isEmpty()) {
            SheetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = SheetTheme.good)
                    Spacer(Modifier.width(8.dp))
                    Text("All synced", fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                }
            }
        }
        FormSection("In order", "Tap the bin to discard a change you no longer want — it will never reach the sheet.") {
            store.pending.forEachIndexed { i, write ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(write.summary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                        Text("${label(write)} · ${SheetDate.relative(write.createdAt)}", fontSize = 12.sp, color = SheetTheme.secondary)
                    }
                    IconButton({ store.discard(write) }) { Icon(Icons.Filled.Delete, "Discard", tint = SheetTheme.bad) }
                }
            }
        }
    }
}

private fun label(write: PendingWrite) = when (write.action) {
    "addExpense" -> "New expense"
    "updateExpense" -> "Edited expense"
    "deleteExpense" -> "Deleted expense"
    "saveDay" -> "Daily log"
    "saveSalary" -> "Income"
    "saveMeal" -> "Meal"
    "deleteMeal" -> "Deleted meal"
    "savePlan" -> "Meal plan"
    "savePeopleEntry" -> "Friends & family"
    "deletePeopleEntry" -> "Deleted entry"
    else -> readable(write.action)
}

/** "saveShoppingItem" → "Shopping item", "deleteGoalStep" → "Deleted goal step". */
internal fun readable(action: String): String {
    val verbs = mapOf("save" to "", "delete" to "Deleted ", "add" to "New ", "update" to "Edited ")
    val verb = verbs.keys.firstOrNull { action.startsWith(it) } ?: return action
    val words = action.removePrefix(verb).replace(Regex("(?<=.)([A-Z])"), " $1").lowercase()
    return (verbs.getValue(verb) + words).replaceFirstChar { it.uppercase() }
}

/** "Deleted Coffee · 14.00  UNDO" for a few seconds after a delete. */
@Composable
fun UndoBar(store: SheetStore, modifier: Modifier = Modifier) {
    AnimatedVisibility(store.undoMessage != null, modifier,
        enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(CircleShape).background(Color(0xFF1F2937))
                .padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Delete, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(store.undoMessage ?: "", color = Color.White, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
            TextButton({ store.undoDelete() }) { Text("UNDO", color = Color(0xFF93C5FD), fontWeight = FontWeight.Bold) }
        }
    }
}

// MARK: - Next period

fun isNewPeriodDue(settings: SheetSettings): Boolean {
    val end = SheetDate.date(settings.endDate) ?: return false
    return ChronoUnit.DAYS.between(LocalDate.now(), end) <= 3
}

@Composable
fun NewPeriodCard(store: SheetStore, settings: SheetSettings) {
    var starting by remember { mutableStateOf(false) }
    val ended = (SheetDate.date(settings.endDate) ?: LocalDate.MAX) < LocalDate.now()
    SheetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(if (ended) Icons.Filled.EventBusy else Icons.Filled.EditCalendar, if (ended) SheetTheme.warn else SheetTheme.navy, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (ended) "This period has ended" else "Period ends ${SheetDate.short(settings.endDate)}",
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                Text("Start the next one — this period is kept in an archive in your sheet.", fontSize = 12.sp, color = SheetTheme.secondary)
            }
        }
        Spacer(Modifier.size(12.dp))
        PrimaryButton("Start next period") { starting = true }
    }
    if (starting) NewPeriodSheet(store, settings) { starting = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPeriodSheet(store: SheetStore, settings: SheetSettings, onDismiss: () -> Unit) {
    val oldStart = SheetDate.date(settings.startDate) ?: LocalDate.now()
    val oldEnd = SheetDate.date(settings.endDate) ?: LocalDate.now()
    val length = maxOf(1L, ChronoUnit.DAYS.between(oldStart, oldEnd))
    // Next period: the day after this one ends (or a week ago, if that's long gone), same length.
    val firstStart = maxOf(oldEnd.plusDays(1), LocalDate.now().minusDays(7))
    var start by remember { mutableStateOf(firstStart) }
    var end by remember { mutableStateOf(firstStart.plusDays(length)) }
    var picking by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val days = ChronoUnit.DAYS.between(start, end) + 1

    EditorSheet(
        title = "Start next period",
        onDismiss = { if (!working) onDismiss() },
        saveLabel = "Start",
        canSave = days >= 1 && !working,
        saving = working,
        error = store.errorMessage.takeIf { !working },
        onSave = {
            working = true
            scope.launch {
                val ok = store.startNextPeriod(start.toString(), end.toString())
                working = false
                if (ok) onDismiss()
            }
        },
    ) {
        FormSection("New period") {
            DateLine("Starts", start) { picking = "start" }
            RowDivider()
            DateLine("Ends", end) { picking = "end" }
            RowDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { KeyValueRow("Length", "$days days") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill("This calendar month", null, SheetTheme.navy, false) {
                val now = LocalDate.now(); start = now.withDayOfMonth(1); end = now.withDayOfMonth(now.lengthOfMonth())
            }
            FilterPill("Next month", null, SheetTheme.navy, false) {
                val next = LocalDate.now().plusMonths(1); start = next.withDayOfMonth(1); end = next.withDayOfMonth(next.lengthOfMonth())
            }
        }
        SheetCard {
            Bullet(Icons.Filled.Archive, "Your current period (${SheetDate.short(settings.startDate)} – ${SheetDate.short(settings.endDate)}) is copied to “Archive” tabs in the sheet.")
            Spacer(Modifier.size(10.dp))
            Bullet(Icons.Filled.CheckCircle, "Days, expenses and income inside the new dates are kept.")
            Spacer(Modifier.size(10.dp))
            Bullet(Icons.Filled.Tune, "Targets, limits, monthly bills and currency stay as they are.")
        }
        if (working) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Archiving and rebuilding your sheet — up to a minute…", color = SheetTheme.secondary)
            }
        }
    }

    picking?.let { which ->
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (if (which == "start") start else end).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == "start") { start = d; if (end < d) end = d.plusDays(length) } else end = maxOf(d, start)
                    }
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ picking = null }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun DateLine(title: String, date: LocalDate, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, color = SheetTheme.text, modifier = Modifier.weight(1f))
        Text(SheetDate.short(date.toString()), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
    }
}

@Composable
private fun Bullet(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = SheetTheme.navy, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, color = SheetTheme.text)
    }
}

// MARK: - Monthly bills

fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

@Composable
fun RecurringListSheet(store: SheetStore, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<RecurringExpense?>(null) }
    var adding by remember { mutableStateOf(false) }
    EditorSheet("Monthly bills", onDismiss, saveLabel = "Add", onSave = { adding = true }) {
        val rules = store.snapshot?.recurring.orEmpty()
        if (rules.isEmpty()) {
            SheetCard {
                Text("No monthly bills yet", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                Text("Add rent, your phone plan or a gym membership once — it's added to your ledger on its day every month.",
                    fontSize = 14.sp, color = SheetTheme.secondary)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card)) {
            rules.forEachIndexed { i, rule ->
                if (i > 0) RowDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { editing = rule }.padding(14.dp).alpha(if (rule.active) 1f else 0.55f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(SheetTheme.categoryIcon(rule.category), SheetTheme.categoryColor(rule.category), 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(rule.description ?: rule.category ?: "Bill", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                        Text("Every month on the ${ordinal(rule.day ?: 1)}${if (rule.active) "" else " · paused"}",
                            fontSize = 12.sp, color = if (rule.active) SheetTheme.secondary else SheetTheme.warn)
                    }
                    Text(SheetFormat.qar(rule.amount), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text)
                }
            }
        }
    }
    editing?.let { RecurringEditorSheet(store, it) { editing = null } }
    if (adding) RecurringEditorSheet(store, null) { adding = false }
}

@Composable
fun RecurringEditorSheet(store: SheetStore, original: RecurringExpense?, onDismiss: () -> Unit) {
    val lists = store.snapshot?.lists
    var rule by remember {
        mutableStateOf(original ?: RecurringExpense(
            category = lists?.categories?.firstOrNull { it == "Rent & Utilities" } ?: lists?.categories?.firstOrNull(),
            method = lists?.methods?.firstOrNull(), day = 1))
    }
    var addThisMonth by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    EditorSheet(
        title = if (original == null) "New monthly bill" else "Monthly bill",
        onDismiss = onDismiss,
        canSave = (rule.amount ?: 0.0) > 0,
        saving = working,
        error = store.errorMessage.takeIf { !working },
        onSave = {
            working = true
            scope.launch {
                val ok = store.saveRecurring(rule, addThisMonth)
                working = false
                if (ok) onDismiss()
            }
        },
    ) {
        FormSection("Bill", "Days that don't exist in a month (like the 31st) use the last day instead.") {
            NumberRow("Amount", Money.main, rule.amount) { rule = rule.copy(amount = it) }
            RowDivider()
            Column(Modifier.padding(12.dp)) { PlainField(rule.description ?: "", "What is it? e.g. Rent") { rule = rule.copy(description = it) } }
            RowDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Day of month", fontSize = 15.sp, color = SheetTheme.text, modifier = Modifier.weight(1f))
                IconButton({ rule = rule.copy(day = maxOf(1, (rule.day ?: 1) - 1)) }) { Text("−", fontSize = 22.sp, color = SheetTheme.navy) }
                Text(ordinal(rule.day ?: 1), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                IconButton({ rule = rule.copy(day = minOf(31, (rule.day ?: 1) + 1)) }) { Text("+", fontSize = 22.sp, color = SheetTheme.navy) }
            }
        }
        Field("Category") {
            ChipGrid(lists?.categories.orEmpty(), rule.category ?: "", { rule = rule.copy(category = it) }) {
                SheetTheme.categoryIcon(it) to SheetTheme.categoryColor(it)
            }
        }
        Field("Paid with") {
            ChipGrid(lists?.methods.orEmpty(), rule.method ?: "", { rule = rule.copy(method = it) }) { SheetTheme.methodIcon(it) to SheetTheme.navy }
        }
        FormSection("Schedule", if (original == null) "Leave off if you've already logged this month's payment." else "Pause a bill to stop it being added without deleting it.") {
            ToggleRow("Active", rule.active) { rule = rule.copy(active = it) }
            if (original == null) { RowDivider(); ToggleRow("Also add this month's now", addThisMonth) { addThisMonth = it } }
        }
        if (original?.row != null) {
            Text("Delete bill", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SheetTheme.card).clickable { confirmDelete = true }.padding(14.dp))
        }
    }
    if (confirmDelete) {
        AppDialog(
            title = "Delete this bill?",
            message = "Expenses it already added stay in your ledger.",
            style = DialogStyle.DESTRUCTIVE,
            confirmText = "Delete",
            onConfirm = {
                confirmDelete = false
                original?.row?.let { row -> scope.launch { if (store.deleteRecurring(row)) onDismiss() } }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = SheetTheme.text)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = SheetTheme.secondary)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = SheetTheme.navy))
    }
}
