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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSettings
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.SheetTable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The Dashboard sheet for the phone. Every figure is still the sheet's own formula result. Mirrors CommandDashboardView.swift. */
@Composable
fun DashboardScreen(store: SheetStore, onOpenFood: () -> Unit = {}, onOpenStudy: () -> Unit = {}, onOpenSettings: () -> Unit, onOpenConverter: () -> Unit,
                    debugOpen: String? = null, onOpenPlanner: () -> Unit = {}, onOpenGoals: () -> Unit = {},
                    onOpenRenewals: () -> Unit = {}, onOpenWeekly: () -> Unit = {}, onOpenParents: () -> Unit = {}) {
    SheetScreen("Dashboard", isRefreshing = store.isLoading, onRefresh = { store.refresh() }) {
        if (store.needsConnection) item { ConnectSheetCard(onOpenSettings) }
        val error = store.errorMessage
        if (error != null && store.isConfigured) item { RetryBanner(store, error) }
        item { SyncStatusBar(store) }
        val snapshot = store.snapshot ?: run {
            if (!store.needsConnection) item { SheetLoadingCard(store.loadProgress) }
            return@SheetScreen
        }
        dashboardContent(store, snapshot, onOpenConverter, debugOpen, onOpenFood, onOpenStudy, onOpenPlanner, onOpenGoals, onOpenRenewals, onOpenWeekly, onOpenParents)
    }
}

private fun LazyListScope.dashboardContent(store: SheetStore, snapshot: SheetSnapshot, onOpenConverter: () -> Unit, debugOpen: String?, onOpenFood: () -> Unit,
                                           onOpenStudy: () -> Unit, onOpenPlanner: () -> Unit, onOpenGoals: () -> Unit,
                                           onOpenRenewals: () -> Unit, onOpenWeekly: () -> Unit, onOpenParents: () -> Unit) {
    fun card(label: String) = snapshot.dashboard.firstOrNull { it.label.contains(label, ignoreCase = true) }?.value ?: "—"
    val settings = snapshot.settings

    if (isNewPeriodDue(settings)) item { NewPeriodCard(store, settings) }
    item { HeroCard(settings, SheetFormat.parse(card("habit score")) ?: 0.0, card("habit score"), card("streak")) }
    item { QuickActions(store, snapshot, onOpenConverter, debugOpen) }
    item { PlannerTodayCard(snapshot, onOpenPlanner) }
    item { ParentsCard(store, snapshot, onOpenParents) }
    item { RenewalsDueCard(snapshot, onOpenRenewals) }
    item { WeeklyReviewCard(onOpenWeekly) }
    item { GoalsFocusCard(snapshot, onOpenGoals) }
    item { StudyTodayCard(snapshot, onOpenStudy) }
    item { FoodTodayCard(snapshot, onOpenFood) }
    item { InsightsCard(snapshot) }

    item { SectionTitle("Habits") }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Icons.Filled.EventAvailable, SheetTheme.sky, "Days logged", card("days logged"), Modifier.weight(1f))
            StatTile(Icons.Filled.Star, SheetTheme.purple, "Excellent days", card("excellent"), Modifier.weight(1f))
        }
    }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Icons.Filled.WaterDrop, SheetTheme.sky, "Avg water", card("water") + " L", Modifier.weight(1f),
                caption = settings.waterTarget?.let { "Target ${SheetFormat.number(it)} L" })
            StatTile(Icons.Filled.Bed, SheetTheme.indigo, "Avg sleep", card("sleep") + " h", Modifier.weight(1f),
                caption = settings.sleepTarget?.let { "Target ${SheetFormat.number(it)} h" })
        }
    }
    snapshot.dashboardTables.getOrNull(0)?.takeIf { it.rows.isNotEmpty() }?.let { rates -> item { HabitRatesCard(rates) } }
    item {
        ChartCard("Daily habit score", "Your score for each logged day") {
            val values = snapshot.days.mapNotNull { it.score }
            if (values.isEmpty()) EmptyChart("Log a day to see your score.")
            else LineAreaChart(values, settings.greatDay, SheetTheme.navy, SheetTheme.good)
        }
    }

    item { SectionTitle("Spending", "Daily cap ${SheetFormat.qar(settings.dailyLimit)} ${Money.main}") }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Icons.Filled.Payments, SheetTheme.teal, "Total spend", card("total spend (${Money.main.lowercase()})") + " ${Money.main}", Modifier.weight(1f),
                caption = if (Money.single) null else Money.homeText(card("total spend (${Money.home.lowercase()})")))
            StatTile(Icons.AutoMirrored.Filled.ShowChart, SheetTheme.warn, "Avg per day", card("avg daily spend") + " ${Money.main}", Modifier.weight(1f))
        }
    }
    item {
        val left = card("budget left")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Icons.Filled.Warning, SheetTheme.bad, "Days over limit", card("days over limit"), Modifier.weight(1f))
            StatTile(Icons.Filled.AccountBalanceWallet, SheetTheme.good, "Budget left", "$left ${Money.main}", Modifier.weight(1f),
                caption = "This month", valueTint = if (left.startsWith("(")) SheetTheme.bad else SheetTheme.text)
        }
    }
    item {
        ChartCard("Daily spend", "Red bars went over your daily cap") {
            val byDay = snapshot.expenses.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.qar ?: 0.0 } }
            val today = SheetDate.today
            val values = snapshot.days.filter { it.date <= today }.map { byDay[it.date] ?: 0.0 }
            if (values.all { it == 0.0 }) EmptyChart("No spending logged yet.")
            else BarChart(values, settings.dailyLimit, SheetTheme.teal, SheetTheme.bad, SheetTheme.secondary)
        }
    }
    snapshot.dashboardTables.getOrNull(1)?.let { item { CategoryCard(it) } }

    item { SectionTitle("Money") }
    item {
        // Saved = what you put aside; spending inside your budget only lowers "left over".
        val saved = com.mohammedsharukms.mypersonaltracker.sheet.SalarySplit.putAside(snapshot)
        val leftOver = snapshot.salary.sumOf { it.netSavings ?: 0.0 } - saved
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Icons.Filled.AccountBalance, SheetTheme.good, "Saved", SheetFormat.qar(saved) + " ${Money.main}", Modifier.weight(1f), caption = "Put aside · emergency + SIP")
            StatTile(Icons.Filled.Percent, SheetTheme.teal, "Left over", SheetFormat.qar(leftOver) + " ${Money.main}", Modifier.weight(1f),
                caption = "Not spent or put aside yet", valueTint = if (leftOver < 0) SheetTheme.bad else SheetTheme.text)
        }
    }
    snapshot.dashboardTables.getOrNull(3)?.let { item { KeyValueCard("Money snapshot", it) } }
    snapshot.dashboardTables.getOrNull(2)?.let { item { KeyValueCard("This period at a glance", it) } }
    item {
        ChartCard("Weekly habit score", "Average score per week") {
            val column = snapshot.weekly.headers.indexOfFirst { it.contains("habit score", ignoreCase = true) }
            val rows = snapshot.weekly.rows.mapNotNull { row ->
                if (column < 0 || row.size <= column) null else SheetFormat.parse(row[column])?.let { "W${row[0]}" to it }
            }
            if (rows.isEmpty()) EmptyChart("Weeks appear as you log days.")
            else LabeledBars(rows) { SheetTheme.tintForRate(it) }
        }
    }
    store.lastSynced?.let { synced ->
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Sync, null, tint = SheetTheme.secondary, modifier = Modifier.size(14.dp))
                Text("Synced ${SheetDate.relative(synced)}", fontSize = 12.sp, color = SheetTheme.secondary)
            }
        }
    }
}

/** Period progress, habit score ring and streak. */
@Composable
private fun HeroCard(settings: SheetSettings, score: Double, scoreText: String, streak: String) {
    val period = dayOfPeriod(settings)
    HeroBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Life & Finance", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
                if (period != null) Text("Day ${period.first} of ${period.second}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${SheetDate.short(settings.startDate)} – ${SheetDate.short(settings.endDate)}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
            }
            ScoreRing(score, 84.dp, 9.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(scoreText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("habits", fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f))
                }
            }
        }
        if (period != null && period.second > 0) {
            Bar(period.first.toDouble() / period.second, Color.White, Color.White.copy(alpha = 0.2f), 4.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroChip(Icons.Filled.LocalFireDepartment, "$streak day streak")
            if (!Money.single) HeroChip(Icons.Filled.SwapHoriz, "1 ${Money.main} = ${SheetFormat.number(settings.rate, 2)} ${Money.home}")
        }
    }
}

/** Where today falls in the period set on the Settings sheet: (day, total). */
fun dayOfPeriod(settings: SheetSettings): Pair<Int, Int>? {
    val start = SheetDate.date(settings.startDate) ?: return null
    val end = SheetDate.date(settings.endDate) ?: return null
    val total = ChronoUnit.DAYS.between(start, end).toInt() + 1
    val today = minOf(LocalDate.now(), end)
    val day = maxOf(0, ChronoUnit.DAYS.between(start, today).toInt() + 1)
    return day to total
}

/** The four things people do most, one tap from the first screen. */
@Composable
private fun QuickActions(store: SheetStore, snapshot: SheetSnapshot, onOpenConverter: () -> Unit, debugOpen: String?) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (debugOpen == "convert") onOpenConverter() else if (debugOpen != null) open = debugOpen }
    val today = snapshot.days.firstOrNull { it.date == SheetDate.today }
    val month = snapshot.salary.firstOrNull { it.month == SheetDate.today.take(7) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickTile(if (today?.isLogged == true) "Edit today" else "Log today", Icons.Filled.Checklist, SheetTheme.navy, Modifier.weight(1f), today != null) { open = "day" }
        QuickTile("Add expense", Icons.Filled.AddCircle, SheetTheme.navy, Modifier.weight(1f)) { open = "expense" }
        QuickTile("Income", Icons.Filled.ArrowCircleDown, SheetTheme.navy, Modifier.weight(1f), month != null) { open = "income" }
        if (Money.single) QuickTile("People", Icons.Filled.People, SheetTheme.navy, Modifier.weight(1f)) { open = "people" }
        else QuickTile("Convert", Icons.Filled.CurrencyExchange, SheetTheme.navy, Modifier.weight(1f)) { onOpenConverter() }
    }
    when (open) {
        "day" -> today?.let { DayLogSheet(store, it) { open = null } }
        "expense" -> ExpenseEditorSheet(store, null) { open = null }
        "income" -> month?.let { SalaryEditorSheet(store, it) { open = null } }
        "people" -> PeopleSheet(store) { open = null }
    }
}

@Composable
private fun QuickTile(title: String, icon: ImageVector, tint: Color, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier.alpha(if (enabled) 1f else 0.45f).clip(RoundedCornerShape(18.dp)).background(SheetTheme.card)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, maxLines = 1)
    }
}

@Composable
private fun HabitRatesCard(table: SheetTable) {
    SheetCard {
        Text("Habit success rates", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            table.rows.forEach { row ->
                val rate = SheetFormat.parse(row.lastOrNull() ?: "") ?: 0.0
                ProgressRow(row.firstOrNull() ?: "", row.lastOrNull() ?: "", rate, SheetTheme.tintForRate(rate), SheetTheme.rateIcon(row.firstOrNull() ?: ""))
            }
        }
    }
}

@Composable
private fun CategoryCard(table: SheetTable) {
    // Rows: Category, QAR, INR, % — only categories with spend, largest first.
    val rows = table.rows
        .filter { it.size >= 4 && it[0].uppercase() != "TOTAL" && (SheetFormat.parse(it[1]) ?: 0.0) > 0 }
        .sortedByDescending { SheetFormat.parse(it[1]) ?: 0.0 }
    val total = table.rows.firstOrNull { it.firstOrNull()?.uppercase() == "TOTAL" }
    SheetCard {
        Row {
            Text("Spend by category", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, modifier = Modifier.weight(1f))
            if (total != null && total.size > 1) Text("${total[1]} ${Money.main}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
        }
        Spacer(Modifier.height(14.dp))
        if (rows.isEmpty()) Text("No spending logged yet.", fontSize = 15.sp, color = SheetTheme.secondary)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { row ->
                ProgressRow(row[0], "${row[1]} · ${row[3]}", SheetFormat.parse(row[3]) ?: 0.0,
                    SheetTheme.categoryColor(row[0]), SheetTheme.categoryIcon(row[0]))
            }
        }
    }
}

@Composable
private fun KeyValueCard(title: String, table: SheetTable) {
    SheetCard {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
        Spacer(Modifier.height(8.dp))
        table.rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = SheetTheme.input)
            val value = row.lastOrNull() ?: ""
            KeyValueRow(row.firstOrNull() ?: "", value, if (value.startsWith("(")) SheetTheme.bad else SheetTheme.text)
        }
    }
}
