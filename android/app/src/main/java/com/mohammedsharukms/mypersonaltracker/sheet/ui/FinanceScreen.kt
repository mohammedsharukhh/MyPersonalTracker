package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.mohammedsharukms.mypersonaltracker.container
import androidx.compose.material.icons.filled.Savings
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.sheet.SheetExpense
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSettings
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.SheetTable
import kotlinx.coroutines.launch

/**
 * The Finance Tracker sheet: the expense ledger (one row per purchase) and the
 * sheet's day-by-day budget control. Mirrors FinanceTrackerView.swift.
 */
@Composable
fun FinanceScreen(store: SheetStore, onOpenConverter: () -> Unit) {
    var segment by rememberSaveable { mutableStateOf(0) }
    var editingMonth by remember { mutableStateOf<com.mohammedsharukms.mypersonaltracker.sheet.SalaryMonth?>(null) }
    val peopleUi = remember { PeopleUi() }
    val goalsUi = remember { GoalsUi() }
    val loansUi = remember { LoansUi() }
    var editing by remember { mutableStateOf<SheetExpense?>(null) }
    var adding by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var puttingAside by remember { mutableStateOf<com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<SheetExpense?>(null) }
    val scope = rememberCoroutineScope()

    SheetScreen(
        "Finance",
        isRefreshing = store.isLoading,
        onRefresh = { store.refresh() },
        bottomPadding = 96,
        actions = {
            if (store.snapshot != null) Box {
                IconButton(onClick = { addMenu = true }) { Icon(Icons.Filled.Add, "Add", tint = SheetTheme.navy) }
                androidx.compose.material3.DropdownMenu(addMenu, { addMenu = false }) {
                    androidx.compose.material3.DropdownMenuItem({ Text("Add expense") }, { addMenu = false; adding = true },
                        leadingIcon = { Icon(Icons.Filled.Add, null) })
                    androidx.compose.material3.DropdownMenuItem({ Text("Add to Savings / SIP") }, { addMenu = false; puttingAside = com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket.SAVINGS },
                        leadingIcon = { Icon(bucketIcon(com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket.SAVINGS), null) })
                    androidx.compose.material3.DropdownMenuItem({ Text("Add to Emergency fund") }, { addMenu = false; puttingAside = com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket.EMERGENCY },
                        leadingIcon = { Icon(bucketIcon(com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket.EMERGENCY), null) })
                }
            }
            if (!Money.single) IconButton(onClick = onOpenConverter) { Icon(Icons.Filled.CurrencyExchange, "Currency converter", tint = SheetTheme.navy) }
        },
        floating = {
            // Only on the ledger: on Budget, Savings and People it sat on top of totals (+ in the top bar works everywhere).
            if (store.snapshot != null && segment == 0) ExtendedFloatingActionButton(
                onClick = { adding = true },
                containerColor = SheetTheme.navy,
                contentColor = Color.White,
                shape = CircleShape,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add expense", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) {
        val error = store.errorMessage
        if (error != null && store.isConfigured) item { RetryBanner(store, error) }
        item { SyncStatusBar(store) }
        val snapshot = store.snapshot
        if (snapshot == null) {
            if (store.needsConnection) item { NotConnectedHint() } else item { SheetLoadingCard(store.loadProgress) }
            return@SheetScreen
        }
        item { MonthSummaryCard(snapshot.settings, snapshot.expenses) }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = SheetTheme.navy, activeContentColor = Color.White,
                    inactiveContainerColor = SheetTheme.card, inactiveContentColor = SheetTheme.text,
                    activeBorderColor = SheetTheme.navy, inactiveBorderColor = SheetTheme.input,
                )
                listOf("Ledger", "Budget", "Savings", "People").forEachIndexed { i, label ->
                    SegmentedButton(segment == i, { segment = i }, SegmentedButtonDefaults.itemShape(i, 4), colors = colors) { Text(label, maxLines = 1) }
                }
            }
        }
        if (segment == 1) {
            item { SalarySplitCard(store, snapshot) { editingMonth = it } }
            loanItems(snapshot, loansUi)
            item { BudgetList(snapshot.budget, snapshot.settings.dailyLimit) }
            return@SheetScreen
        }
        if (segment == 2) {
            savingsItems(store, snapshot, goalsUi) { editingMonth = it }
            return@SheetScreen
        }
        if (segment == 3) {
            peopleItems(store, snapshot, onAdd = { peopleUi.adding = it to true }, onOpen = { peopleUi.person = it })
            return@SheetScreen
        }

        item {
            TextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search expenses") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (search.isNotEmpty()) IconButton({ search = "" }) { Icon(Icons.Filled.Close, "Clear") } },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SheetTheme.input, unfocusedContainerColor = SheetTheme.input,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val used = snapshot.expenses.mapNotNull { it.category }.toSortedSet().toList()
        if (used.size > 1) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("All", Icons.Filled.FilterList, SheetTheme.navy, categoryFilter == null) { categoryFilter = null }
                used.forEach { c ->
                    FilterPill(c, SheetTheme.categoryIcon(c), SheetTheme.categoryColor(c), categoryFilter == c) {
                        categoryFilter = if (categoryFilter == c) null else c
                    }
                }
            }
        }

        val query = search.trim()
        val filtered = snapshot.expenses.filter { e ->
            (categoryFilter == null || e.category == categoryFilter) &&
                (query.isEmpty() || listOfNotNull(e.description, e.category, e.method, e.notes, SheetDate.short(e.date)).any { it.contains(query, ignoreCase = true) })
        }
        val isFiltering = categoryFilter != null || query.isNotEmpty()
        if (isFiltering && filtered.isNotEmpty()) item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SheetTheme.navy.copy(alpha = 0.08f)).padding(12.dp)) {
                Text("${filtered.size} ${if (filtered.size == 1) "expense" else "expenses"}", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy, modifier = Modifier.weight(1f))
                Text("${SheetFormat.qar(filtered.sumOf { it.qar ?: 0.0 })} ${Money.main}", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
            }
        }
        val grouped = filtered.groupBy { it.date ?: "" }
        val dates = grouped.keys.sortedDescending()
        if (dates.isEmpty()) item {
            SheetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isFiltering) Icons.Filled.Search else Icons.Filled.Inbox, null, tint = SheetTheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isFiltering) "No expenses match." else "No expenses yet. Tap Add expense to log one.", color = SheetTheme.secondary)
                }
            }
        }
        dates.forEach { date ->
            val items = grouped[date].orEmpty()
            val total = items.sumOf { it.qar ?: 0.0 }
            val over = total > (snapshot.settings.dailyLimit ?: Double.MAX_VALUE)
            item(key = date) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(SheetDate.weekdayDay(date), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, modifier = Modifier.weight(1f))
                        Text("${SheetFormat.qar(total)} ${Money.main}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                        if (!isFiltering) { Spacer(Modifier.width(8.dp)); StatusPill(if (over) "OVER" else "OK") }
                    }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card)) {
                        items.forEachIndexed { i, expense ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 62.dp), color = SheetTheme.input)
                            ExpenseRow(expense, onClick = { editing = expense }, onLongClick = { confirmDelete = expense })
                        }
                    }
                }
            }
        }
    }

    if (adding) ExpenseEditorSheet(store, null) { adding = false }
    puttingAside?.let { PutAsideSheet(store, it) { puttingAside = null } }
    editingMonth?.let { SalaryEditorSheet(store, it) { editingMonth = null } }
    PeopleSheets(store, peopleUi)
    GoalSheets(store, goalsUi)
    LoanSheets(store, loansUi)
    editing?.let { e -> ExpenseEditorSheet(store, e) { editing = null } }
    confirmDelete?.let { e ->
        AppDialog(
        title = "Delete this expense?",
        message = "${e.description ?: e.category ?: "Expense"} · ${SheetFormat.qar(e.qar)} ${Money.main} is removed from the sheet.",
        style = DialogStyle.DESTRUCTIVE,
        confirmText = "Delete",
        onConfirm = { scope.launch { store.deleteExpense(e.row) }; confirmDelete = null },
        onDismiss = { confirmDelete = null },
    )
    }
}

/** This month's spend against the monthly limit, plus rate and daily cap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthSummaryCard(settings: SheetSettings, expenses: List<SheetExpense>) {
    val month = SheetDate.today.take(7)
    val monthSpend = expenses.filter { (it.date ?: "").startsWith(month) }.sumOf { it.qar ?: 0.0 }
    val todaySpend = expenses.filter { it.date == SheetDate.today }.sumOf { it.qar ?: 0.0 }
    val limit = settings.monthlyLimit ?: 0.0
    val left = limit - monthSpend
    val fraction = if (limit > 0) monthSpend / limit else 0.0
    val barColor = if (fraction > 1) SheetTheme.heroRed else SheetTheme.heroGreen
    HeroBox {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Spent this month", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(SheetFormat.qar(monthSpend), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(Money.main, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 6.dp))
                }
                if (!Money.single) Text("≈ ${Money.homeAmount(monthSpend * (settings.rate ?: 0.0))}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
            }
            ScoreRing(fraction, 62.dp, 7.dp, barColor) {
                Text(SheetFormat.percent(fraction, 0), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Bar(fraction, barColor, Color.White.copy(alpha = 0.18f), 8.dp)
            Text(
                if (left >= 0) "${SheetFormat.qar(left)} ${Money.main} left of ${SheetFormat.qar(limit)}" else "${SheetFormat.qar(-left)} ${Money.main} over the ${SheetFormat.qar(limit)} limit",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroChip(Icons.Filled.CalendarToday, "Today ${SheetFormat.number(todaySpend, 2)}")
            HeroChip(Icons.Filled.Speed, "Cap ${SheetFormat.number(settings.dailyLimit)}/day")
            if (!Money.single) HeroChip(Icons.Filled.SwapHoriz, "1 ${Money.main} = ${SheetFormat.number(settings.rate, 2)} ${Money.home}")
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ExpenseRow(expense: SheetExpense, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(SheetTheme.categoryIcon(expense.category), SheetTheme.categoryColor(expense.category), 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(expense.description?.ifEmpty { null } ?: expense.category ?: "Expense", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = SheetTheme.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (expense.isPending) {
                    androidx.compose.foundation.layout.Spacer(Modifier.width(5.dp))
                    Icon(Icons.Filled.Schedule, "Waiting to sync", tint = SheetTheme.sky, modifier = Modifier.size(13.dp))
                }
            }
            Text(listOfNotNull(expense.category, expense.method).joinToString(" · "), fontSize = 12.sp, color = SheetTheme.secondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            expense.notes?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 11.sp, color = SheetTheme.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(SheetFormat.qar(expense.qar), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text)
            if (!Money.single) Text("${Money.homeAmount(expense.inr)}", fontSize = 12.sp, color = SheetTheme.secondary)
        }
    }
}

/** The sheet's Daily Budget Control block, one row per day. */
@Composable
private fun BudgetList(table: SheetTable, cap: Double?) {
    // Only the days that matter: up to today, with spending (plus today), newest first. OVER is the only flag.
    val parser = java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yy", java.util.Locale.ENGLISH)
    val today = java.time.LocalDate.now()
    data class Day(val date: java.time.LocalDate, val spent: Double, val over: Boolean, val monthToDate: String, val left: String)
    val days = table.rows.mapNotNull { row ->
        val date = runCatching { java.time.LocalDate.parse(row.getOrNull(0).orEmpty(), parser) }.getOrNull() ?: return@mapNotNull null
        if (date.isAfter(today)) return@mapNotNull null
        val spent = SheetFormat.parse(row.getOrNull(1).orEmpty()) ?: 0.0
        if (spent <= 0 && date != today) return@mapNotNull null
        Day(date, spent, row.getOrNull(3).orEmpty().uppercase() == "OVER", row.getOrNull(4) ?: "—", row.getOrNull(5).orEmpty())
    }.sortedByDescending { it.date }
    val overThisMonth = days.count { it.over && it.date.year == today.year && it.date.month == today.month }
    var showAll by remember { mutableStateOf(false) }
    val shown = if (showAll) days else days.take(14)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.padding(top = 8.dp)) {
            Text("Daily spending", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            Text(listOfNotNull(cap?.let { "Cap ${SheetFormat.qar(it)} a day" },
                if (overThisMonth > 0) "$overThisMonth day${if (overThisMonth == 1) "" else "s"} over this month" else "No days over this month").joinToString(" · "),
                fontSize = 12.sp, color = if (overThisMonth > 0) SheetTheme.bad else SheetTheme.secondary)
        }
        if (days.isEmpty()) {
            SheetCard { Text("No spending yet.", color = SheetTheme.secondary) }
            return@Column
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card)) {
            shown.forEachIndexed { i, day ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = SheetTheme.input)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (day.date == today) "Today" else day.date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")),
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                        Text("Month so far ${day.monthToDate} · left ${day.left}", fontSize = 12.sp,
                            color = if (day.left.startsWith("(")) SheetTheme.bad else SheetTheme.secondary)
                    }
                    Text(SheetFormat.qar(day.spent), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (day.over) SheetTheme.bad else SheetTheme.text)
                    if (day.over) { Spacer(Modifier.width(12.dp)); StatusPill("OVER", SheetTheme.bad) }
                }
            }
            if (days.size > 14) {
                HorizontalDivider(color = SheetTheme.input)
                Text(if (showAll) "Show fewer" else "Show all ${days.size} days", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { showAll = !showAll }.padding(14.dp))
            }
        }
        Text("Days you spent money, newest first. OVER means that day went above your daily cap; “left” is what remains of this month's spend limit.",
            fontSize = 13.sp, color = SheetTheme.secondary, modifier = Modifier.padding(horizontal = 4.dp))
    }
}
