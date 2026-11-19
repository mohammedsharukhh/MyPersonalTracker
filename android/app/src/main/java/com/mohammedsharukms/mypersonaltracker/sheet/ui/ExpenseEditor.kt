package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.container
import com.mohammedsharukms.mypersonaltracker.sheet.CurrencyBook
import com.mohammedsharukms.mypersonaltracker.sheet.SheetExpense
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Add or edit one ledger row — the Finance Tracker's yellow columns. Built for
 * quick entry: amount first (in any currency), then taps for the date, category
 * and payment method, with recent expenses one tap away. Mirrors SheetExpenseEditor.swift.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorSheet(store: SheetStore, expense: SheetExpense?, onDismiss: () -> Unit) {
    val book = LocalContext.current.container.currency
    val lists = store.snapshot?.lists
    var date by remember { mutableStateOf(SheetDate.date(expense?.date) ?: LocalDate.now()) }
    var description by remember { mutableStateOf(expense?.description ?: "") }
    var category by remember { mutableStateOf(expense?.category ?: lists?.categories?.firstOrNull() ?: "") }
    var method by remember { mutableStateOf(expense?.method ?: lists?.methods?.firstOrNull() ?: "") }
    var amountText by remember { mutableStateOf(SheetFormat.plain(expense?.qar)) }
    var currency by remember { mutableStateOf(Money.main) }
    var notes by remember { mutableStateOf(expense?.notes ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var repeatMonthly by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val amountFocus = remember { FocusRequester() }

    val amount = SheetFormat.input(amountText)
    val qar = amount?.let { book.convert(it, currency, Money.main) }
    val rate = store.snapshot?.settings?.rate ?: 0.0
    val canSave = (qar ?: 0.0) > 0 && !store.isSaving

    /** The last few distinct expenses, newest first, to repeat with one tap. */
    val recents = remember(store.snapshot) {
        val seen = mutableSetOf<String>()
        store.snapshot?.expenses.orEmpty()
            .sortedWith(compareByDescending<SheetExpense> { it.date ?: "" }.thenByDescending { it.row })
            .filter { e -> e.description?.takeIf { it.isNotEmpty() }?.let { seen.add(it.lowercase()) } ?: false }
            .take(8)
    }

    fun save() {
        val q = qar ?: return
        val a = amount ?: return
        var finalNotes = notes.trim()
        if (currency != Money.main) {
            val original = "Paid ${CurrencyBook.format(a)} $currency"
            finalNotes = if (finalNotes.isEmpty()) original else "$finalNotes · $original"
        }
        scope.launch {
            val saved = store.saveExpense(
                row = expense?.row, date = date.toString(), description = description.trim(),
                category = category, method = method, qar = Math.round(q * 100) / 100.0, notes = finalNotes,
            )
            if (saved && repeatMonthly) {
                // This month's is the expense just saved, so the bill starts next month.
                store.saveRecurring(
                    com.mohammedsharukms.mypersonaltracker.sheet.RecurringExpense(
                        description = description.trim(), category = category, method = method,
                        amount = Math.round(q * 100) / 100.0, day = date.dayOfMonth, active = true),
                    addThisMonth = false, lastAdded = date.toString().take(7))
            }
            if (saved) onDismiss()
        }
    }

    LaunchedEffect(Unit) { if (expense == null) runCatching { amountFocus.requestFocus() } }
    LaunchedEffect(currency) { if (currency != Money.main) book.refreshIfStale() }

    EditorSheet(
        title = if (expense == null) "Add expense" else "Edit expense",
        onDismiss = onDismiss,
        canSave = canSave,
        saving = store.isSaving,
        error = store.errorMessage.takeIf { !store.isSaving && store.snapshot != null },
        onSave = ::save,
        bottomBar = {
            if (!WindowInsets.isImeVisible) {
                Column(Modifier.background(SheetTheme.page).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    PrimaryButton(
                        if (expense == null) "Add ${qar?.let { SheetFormat.qar(it) + " ${Money.main}" } ?: "expense"}" else "Save changes",
                        enabled = canSave, loading = store.isSaving, onClick = ::save,
                    )
                }
            }
        },
    ) {
        // Amount
        SheetCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Amount", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary, modifier = Modifier.weight(1f))
                if (expense == null) ReceiptScanButton { result ->
                    result.amount?.let { currency = Money.main; amountText = SheetFormat.plain(it) }
                    result.date?.let { date = it }
                    if (description.isEmpty()) result.merchant?.let { description = it }
                    result.merchant?.let { m -> CategoryGuess.guess(m, lists?.categories.orEmpty())?.let { category = it } }
                }
            }
            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Money.single) CurrencyPill(Money.main) else CurrencyButton(book, currency) { currency = it }
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text, textAlign = TextAlign.End),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(SheetTheme.navy),
                    modifier = Modifier.weight(1f).focusRequester(amountFocus),
                    decorationBox = { inner ->
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.CenterEnd) {
                            if (amountText.isEmpty()) Text("0.00", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = SheetTheme.tertiary)
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    currency != Money.main && qar != null -> Hint(Icons.Filled.Sync, "Saved as ${SheetFormat.qar(qar)} ${Money.main}", SheetTheme.navy)
                    currency != Money.main && book.rate(currency) == null ->
                        Hint(Icons.Filled.ErrorOutline, if (book.isRefreshing) "Getting the $currency rate…" else "No $currency rate yet — set one in the converter", SheetTheme.warn)
                }
                Spacer(Modifier.weight(1f))
                if (qar != null && !Money.single) Text("≈ ${Money.homeAmount(qar * rate)}", fontSize = 13.sp, color = SheetTheme.secondary)
            }
            val cap = store.snapshot?.settings?.dailyLimit
            val spent = store.snapshot?.expenses.orEmpty().filter { it.date == date.toString() && it.row != expense?.row }.sumOf { it.qar ?: 0.0 }
            if (qar != null && cap != null && qar + spent > cap) {
                Spacer(Modifier.size(6.dp))
                Hint(Icons.Filled.Warning, "This puts ${SheetDate.weekdayDay(date.toString())} over your ${SheetFormat.number(cap)} ${Money.main} daily cap.", SheetTheme.warn)
            }
        }

        if (expense == null && recents.isNotEmpty()) {
            Field("Repeat a recent one") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recents.forEach { item ->
                        Row(
                            Modifier.clip(CircleShape).background(SheetTheme.card).clickable {
                                description = item.description ?: ""
                                item.category?.let { category = it }
                                item.method?.let { method = it }
                                currency = Money.main
                                amountText = SheetFormat.plain(item.qar)
                            }.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconBadge(SheetTheme.categoryIcon(item.category), SheetTheme.categoryColor(item.category), 28.dp)
                            Column {
                                Text(item.description ?: "", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, maxLines = 1)
                                Text("${SheetFormat.qar(item.qar)} ${Money.main}", fontSize = 11.sp, color = SheetTheme.secondary)
                            }
                        }
                    }
                }
            }
        }

        Field("What was it?") { PlainField(description, "e.g. Groceries at Lulu") { description = it } }

        Field("When?") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val today = LocalDate.now()
                DateChip("Today", date == today) { date = today }
                DateChip("Yesterday", date == today.minusDays(1)) { date = today.minusDays(1) }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(CircleShape).background(SheetTheme.input).clickable { pickingDate = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.EditCalendar, null, tint = SheetTheme.navy, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(SheetDate.short(date.toString()), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                }
            }
        }

        Field("Category") {
            ChipGrid(lists?.categories.orEmpty(), category, { category = it }) { SheetTheme.categoryIcon(it) to SheetTheme.categoryColor(it) }
        }
        Field("Paid with") {
            ChipGrid(lists?.methods.orEmpty(), method, { method = it }) { SheetTheme.methodIcon(it) to SheetTheme.navy }
        }
        if (expense == null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SheetTheme.card).padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Repeat every month", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                    Text("Added for you on the ${ordinal(date.dayOfMonth)} of each month — for rent, phone, gym.", fontSize = 12.sp, color = SheetTheme.secondary)
                }
                androidx.compose.material3.Switch(repeatMonthly, { repeatMonthly = it },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = SheetTheme.purple))
            }
        }
        Field("Notes (optional)") { PlainField(notes, "Anything to remember", singleLine = false) { notes = it } }

        if (expense != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SheetTheme.bad.copy(alpha = 0.1f))
                    .clickable { confirmDelete = true }.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Delete, null, tint = SheetTheme.bad, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete expense", fontWeight = FontWeight.SemiBold, color = SheetTheme.bad)
            }
        }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (confirmDelete && expense != null) {
        AppDialog(
            title = "Delete this expense?",
            message = "It's removed from the sheet. You can undo for a few seconds.",
            style = DialogStyle.DESTRUCTIVE,
            confirmText = "Delete",
            onConfirm = {
                confirmDelete = false
                scope.launch { if (store.deleteExpense(expense.row)) onDismiss() }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Hint(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

@Composable
fun Field(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
        content()
    }
}

/** A filled rounded text field matching the cards. */
@Composable
fun PlainField(value: String, placeholder: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        textStyle = TextStyle(fontSize = 16.sp, color = SheetTheme.text),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        cursorBrush = SolidColor(SheetTheme.navy),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SheetTheme.input).padding(14.dp),
        decorationBox = { inner ->
            androidx.compose.foundation.layout.Box {
                if (value.isEmpty()) Text(placeholder, fontSize = 16.sp, color = SheetTheme.tertiary)
                inner()
            }
        },
    )
}

@Composable
private fun DateChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        title,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) Color.White else SheetTheme.text,
        modifier = Modifier.clip(CircleShape).background(if (selected) SheetTheme.navy else SheetTheme.input).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
