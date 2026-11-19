package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.sheet.SalaryMonth
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import kotlinx.coroutines.launch
import com.mohammedsharukms.mypersonaltracker.sheet.SalarySplit
import androidx.compose.material.icons.filled.AccountBalanceWallet

/**
 * The Salary & Savings sheet: income, remittance to India and savings rate by
 * month. Salary, other income and remittance are inputs; expenses come from the
 * ledger and everything else is the sheet's formulas. Mirrors SalarySavingsView.swift.
 */
@Composable
fun SavingsScreen(store: SheetStore) {
    var editing by remember { mutableStateOf<SalaryMonth?>(null) }
    val goals = remember { GoalsUi() }
    SheetScreen("Savings", isRefreshing = store.isLoading, onRefresh = { store.refresh() }) {
        val error = store.errorMessage
        if (error != null && store.isConfigured) item { RetryBanner(store, error) }
        item { SyncStatusBar(store) }
        val snapshot = store.snapshot
        if (snapshot == null) {
            if (store.needsConnection) item { NotConnectedHint() }
            return@SheetScreen
        }
        savingsItems(store, snapshot, goals) { editing = it }
    }
    editing?.let { m -> SalaryEditorSheet(store, m) { editing = null } }
    GoalSheets(store, goals)
}

/** Savings totals and the month cards — its own screen, and the Savings section of Finance. */
fun androidx.compose.foundation.lazy.LazyListScope.savingsItems(store: SheetStore, snapshot: com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot, goals: GoalsUi, onEdit: (SalaryMonth) -> Unit) {
    item { TotalsCard(snapshot) }
    item { PutAsideCard(store, snapshot.setAside) }
    savingsGoalItems(snapshot, goals)
    item { SectionTitle("Months", "Tap a month to enter salary, other income and money sent home.") }
    if (snapshot.salary.isEmpty()) item { SheetCard { Text("No months in the period yet.", color = SheetTheme.secondary) } }
    items(snapshot.salary.reversed(), key = { it.month }) { month ->
        MonthCard(month, snapshot) { onEdit(month) }
    }
}

/** "Put money aside", and the last few times you did — press and hold one to delete it. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PutAsideCard(store: SheetStore, entries: List<com.mohammedsharukms.mypersonaltracker.sheet.SetAsideEntry>) {
    var putting by remember { mutableStateOf<com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket?>(null) }
    var deleting by remember { mutableStateOf<com.mohammedsharukms.mypersonaltracker.sheet.SetAsideEntry?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket.SAVINGS, com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket.EMERGENCY).forEach { bucket ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (bucket == com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket.SAVINGS) SheetTheme.navy else bucketTint(bucket))
                        .clickable { putting = bucket }.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Filled.AddCircle, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text("Add to ${bucket.label}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        val recent = entries.sortedByDescending { it.date }.take(5)
        if (recent.isNotEmpty()) SheetCard {
            Text("Recently put aside", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            recent.forEach { e ->
                val bucket = if (e.bucket == com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket.EMERGENCY.label)
                    com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket.EMERGENCY else com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket.SAVINGS
                Row(Modifier.fillMaxWidth().padding(top = 10.dp).combinedClickable(onClick = {}, onLongClick = { deleting = e }),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(bucketIcon(bucket), bucketTint(bucket), 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.note ?: e.bucket, fontSize = 15.sp, color = SheetTheme.text, maxLines = 1)
                        Text("${SheetDate.short(e.date)} · ${e.bucket}", fontSize = 12.sp, color = SheetTheme.secondary)
                    }
                    Text(SheetFormat.qar(e.amount), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                }
            }
        }
    }
    putting?.let { PutAsideSheet(store, it) { putting = null } }
    deleting?.let { e ->
        AppDialog(title = "Delete ${SheetFormat.qar(e.amount)} put aside?", message = "It comes off your savings.", style = DialogStyle.DESTRUCTIVE,
            confirmText = "Delete", onConfirm = { store.deleteSetAside(e.id); deleting = null }, onDismiss = { deleting = null })
    }
}

/** Saved = what you put aside. Spending inside your plan never counts against it; it only lowers "left over". */
@Composable
private fun TotalsCard(snapshot: com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot) {
    val months = snapshot.salary
    val income = months.sumOf { it.totalIncome ?: 0.0 }
    val expenses = months.sumOf { it.expenses ?: 0.0 }
    val remit = months.sumOf { it.remittanceQar ?: 0.0 }
    val saved = SalarySplit.putAside(snapshot)
    val emergency = SalarySplit.emergencySaved(snapshot)
    val leftOver = months.sumOf { it.netSavings ?: 0.0 } - saved
    val plan = SalarySplit.savingsShare(snapshot.settings)
    val savedRate = if (income > 0) saved / income else 0.0
    val onPlan = savedRate + 0.0005 >= plan
    HeroBox(SheetTheme.savingsGradient) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Saved · put aside", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(SheetFormat.qar(saved), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Text(Money.main, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 6.dp))
                }
                Text("Emergency fund ${SheetFormat.qar(emergency)} · Savings / SIP ${SheetFormat.qar(saved - emergency)}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            }
            ScoreRing(if (plan > 0) savedRate / plan else 0.0, 66.dp, 7.dp, if (onPlan) SheetTheme.heroGreen else SheetTheme.heroAmber) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(SheetFormat.percent(savedRate, 0), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("saved", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
        Row { HeroChip(if (onPlan) Icons.Filled.GppGood else Icons.Filled.TrackChanges,
            if (onPlan) "On plan: ${SheetFormat.percent(plan, 0)} of income" else "Plan: put aside ${SheetFormat.percent(plan, 0)} of income") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroStat("Income", income, Icons.Filled.ArrowCircleDown, Modifier.weight(1f))
            HeroStat("Spent", expenses, Icons.Filled.ShoppingCart, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroStat("Sent home", remit, Icons.AutoMirrored.Filled.Send, Modifier.weight(1f))
            HeroStat("Left over", leftOver, Icons.Filled.AccountBalanceWallet, Modifier.weight(1f))
        }
        Text("Spending inside your budget doesn't touch what you've saved — it only lowers Left over (income you haven't spent or put aside yet).",
            fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
    }
}

@Composable
private fun HeroStat(title: String, qar: Double, icon: ImageVector, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(12.dp))
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
        Text(SheetFormat.qar(qar), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun MonthCard(month: SalaryMonth, snapshot: com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot, onClick: () -> Unit) {
    val income = month.totalIncome ?: 0.0
    val putAside = SalarySplit.putAside(snapshot, month.month)
    val planned = income * SalarySplit.savingsShare(snapshot.settings)
    val leftOver = (month.netSavings ?: 0.0) - putAside
    SheetCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.CalendarMonth, SheetTheme.teal, 34.dp)
            Spacer(Modifier.width(12.dp))
            Text(month.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, modifier = Modifier.weight(1f))
            when (val state = SalarySplit.state(snapshot, month.month)) {
                SalarySplit.MonthState.None -> {}
                SalarySplit.MonthState.Saved -> StatusPill("Saved ✓", SheetTheme.good)
                is SalarySplit.MonthState.ToPutAside -> StatusPill("${SheetFormat.qar(state.amount)} to put aside", SheetTheme.warn)
                is SalarySplit.MonthState.OverBudget -> StatusPill(
                    "${if (state.buckets.size == 1) state.buckets[0].label else "${state.buckets.size} buckets"} over by ${SheetFormat.qar(state.amount)}", SheetTheme.bad)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SheetTheme.tertiary)
        }
        Spacer(Modifier.size(14.dp))
        Row(Modifier.fillMaxWidth()) {
            Value("Income", month.totalIncome, SheetTheme.text, Modifier.weight(1f))
            Value("Spent", month.expenses, SheetTheme.bad, Modifier.weight(1f))
            Value("Sent", month.remittanceQar, SheetTheme.sky, Modifier.weight(1f))
            Value("Put aside", putAside, SheetTheme.good, Modifier.weight(1f))
            Value("Left over", leftOver, if (leftOver < 0) SheetTheme.bad else SheetTheme.text, Modifier.weight(1f))
        }
        if (planned > 0) {
            Spacer(Modifier.size(14.dp))
            ProgressRow("Put aside vs plan", "${SheetFormat.qar(putAside)} / ${SheetFormat.qar(planned)}", putAside / planned,
                if (putAside >= planned) SheetTheme.good else SheetTheme.teal)
        }
    }
}

@Composable
private fun Value(title: String, amount: Double?, tint: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
        Text(SheetFormat.qar(amount), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
    }
}

/** Edit one month's inputs: salary, other income and money sent home. */
@Composable
fun SalaryEditorSheet(store: SheetStore, month: SalaryMonth, onDismiss: () -> Unit) {
    var salary by remember { mutableStateOf(month.salary) }
    var otherIncome by remember { mutableStateOf(month.otherIncome) }
    var remittance by remember { mutableStateOf(month.remittanceQar) }
    // Bumped to make the salary field show a value filled in by the button.
    var filled by remember { mutableStateOf(0) }
    val rate = store.snapshot?.settings?.rate ?: 0.0
    val scope = rememberCoroutineScope()
    EditorSheet(
        title = month.label,
        onDismiss = onDismiss,
        saving = store.isSaving,
        error = store.errorMessage.takeIf { !store.isSaving && store.snapshot != null },
        onSave = { scope.launch { if (store.saveSalary(month.month, salary, otherIncome, remittance)) onDismiss() } },
    ) {
        FormSection("Income") {
            key(filled) { NumberRow("Salary", Money.main, salary, "This month only · blank = no salary") { salary = it } }
            val usual = store.snapshot?.settings?.salary
            if (salary == null && usual != null && usual > 0) {
                SmallTextButton("Use my usual salary · ${SheetFormat.qar(usual)}") { salary = usual; filled++ }
            }
            RowDivider()
            NumberRow("Other income", Money.main, otherIncome) { otherIncome = it }
        }
        FormSection("Money sent home") {
            NumberRow("Sent home", Money.main, remittance, if (Money.single) null else remittance?.let { "≈ ${Money.homeAmount(it * rate)}" }) { remittance = it }
        }
        FormSection("From the sheet") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                KeyValueRow("Expenses (from ledger)", SheetFormat.qar(month.expenses))
                KeyValueRow("Net savings", SheetFormat.qar(month.netSavings))
                KeyValueRow("Savings rate", SheetFormat.percent(month.savingsRate))
                KeyValueRow("Goal savings", SheetFormat.qar(month.goalSavings))
            }
        }
    }
}
