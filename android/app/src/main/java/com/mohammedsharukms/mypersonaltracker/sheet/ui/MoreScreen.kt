package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.container
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSettings
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.SheetTable
import com.mohammedsharukms.mypersonaltracker.sheet.share
import kotlinx.coroutines.launch

/** Places More can open, full screen. (Learning moved to the Study tab, backed by the sheet.) */
enum class MoreDestination { MORE, SETTINGS, SETTINGS_SHEET, TABLES, CONVERTER, REPORT, TODAY, HISTORY, RENEWALS, CARDS, WEEK_REVIEW, YEAR_REVIEW }

/**
 * Everything that isn't a tab, grouped by what it's for: money, reviews and reports, the journal on this phone.
 * Settings isn't here: it has its own gear on every tab. Opens from ⋯ on every tab. Mirrors SheetMoreView.swift.
 */
@Composable
fun MoreScreen(store: SheetStore, onBack: () -> Unit, onOpen: (MoreDestination) -> Unit) {
    var showingBills by remember { mutableStateOf(false) }
    var showingPeople by remember { mutableStateOf(false) }
    if (showingPeople) PeopleSheet(store) { showingPeople = false }
    if (showingBills) RecurringListSheet(store) { showingBills = false }
    SheetScreen("More", isRefreshing = store.isLoading, onRefresh = { store.refresh() }, onBack = onBack) {
        item {
            MoreGroup("Money") {
                MoreRow(Icons.Filled.Repeat, "Monthly bills", "Rent, phone, gym, added automatically") { showingBills = true }
                MoreRow(Icons.Filled.People, "Friends and family", "Money lent, borrowed and given") { showingPeople = true }
                MoreRow(Icons.Filled.EventBusy, "Renewals", "Reminded before documents expire") { onOpen(MoreDestination.RENEWALS) }
                MoreRow(Icons.Filled.CreditCard, "Cards", "Kept on this phone only") { onOpen(MoreDestination.CARDS) }
                if (!Money.single) MoreRow(Icons.Filled.CurrencyExchange, "Currency converter", "Every currency, live or your own rates") { onOpen(MoreDestination.CONVERTER) }
            }
        }
        item {
            MoreGroup("Reviews and reports") {
                MoreRow(Icons.Filled.InsertChart, "Week in review", "How this week went") { onOpen(MoreDestination.WEEK_REVIEW) }
                MoreRow(Icons.Filled.Description, "Monthly report", "Share a month as a PDF") { onOpen(MoreDestination.REPORT) }
                MoreRow(Icons.Filled.AutoAwesome, "Year in review", "Your year, added up") { onOpen(MoreDestination.YEAR_REVIEW) }
                MoreRow(Icons.Filled.TableChart, "Tables", "Week by week and month by month") { onOpen(MoreDestination.TABLES) }
            }
        }
        item {
            MoreGroup("Journal", "Kept on this phone, not in your sheet.") {
                MoreRow(Icons.Filled.WbSunny, "Journal", "To-dos, focus time and mood") { onOpen(MoreDestination.TODAY) }
                MoreRow(Icons.Filled.History, "Journal history") { onOpen(MoreDestination.HISTORY) }
            }
        }
    }
}

@Composable
private fun MoreGroup(title: String?, footer: String? = null, rows: @Composable () -> Unit) {
    Column {
        if (title != null) Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp, top = 8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card)) { rows() }
        if (footer != null) Text(footer, fontSize = 12.sp, color = SheetTheme.secondary, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
    }
}

@Composable
private fun MoreRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, SheetTheme.navy, 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = SheetTheme.text)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = SheetTheme.secondary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SheetTheme.tertiary)
    }
}

@Composable
fun SummaryScreen(store: SheetStore, onBack: () -> Unit) {
    var weekly by remember { mutableStateOf(true) }
    val footnote = if (weekly) "Weekly Budget = Daily Spend Limit × 7. Variance is budget minus actual: green is money left over, red is an overspend."
    else "Habit percentages are days marked Y divided by days you logged that habit, so skipping a day doesn't punish the rate."
    SheetScreen("Tables", isRefreshing = store.isLoading, onRefresh = { store.refresh() }, onBack = onBack) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = SheetTheme.navy, activeContentColor = Color.White,
                    inactiveContainerColor = SheetTheme.card, inactiveContentColor = SheetTheme.text,
                    activeBorderColor = SheetTheme.navy, inactiveBorderColor = SheetTheme.input,
                )
                listOf("Week by week", "Month by month").forEachIndexed { i, label ->
                    SegmentedButton(weekly == (i == 0), { weekly = i == 0 },
                        SegmentedButtonDefaults.itemShape(i, 2), colors = colors) { Text(label, maxLines = 1) }
                }
            }
        }
        val table: SheetTable? = store.snapshot?.let { if (weekly) it.weekly else it.monthly }
        item { if (table != null) SheetTableView(table, 84.dp) else NotConnectedHint() }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Info, null, tint = SheetTheme.secondary, modifier = Modifier.size(14.dp))
                Text(footnote, fontSize = 12.sp, color = SheetTheme.secondary)
            }
        }
    }
}

/**
 * Picks the workbook's main and home currencies and applies them to the sheet,
 * which relabels every header. Optionally sets the exchange rate from live rates.
 */
@Composable
internal fun CurrencySection(store: SheetStore, settings: SheetSettings, onSaved: () -> Unit) {
    val book = androidx.compose.ui.platform.LocalContext.current.container.currency
    var oneCurrency by remember(settings.mainCurrency, settings.homeCurrency) { mutableStateOf(settings.mainCurrency == settings.homeCurrency) }
    var main by remember(settings.mainCurrency) { mutableStateOf(settings.mainCurrency) }
    var home by remember(settings.homeCurrency) { mutableStateOf(settings.homeCurrency) }
    var rate by remember { mutableStateOf(if (settings.rateIsCurrent) settings.rate else null) }
    var confirming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val chosenHome = if (oneCurrency) main else home
    val changed = main != settings.mainCurrency || chosenHome != settings.homeCurrency
    val liveRate = book.liveRate(main, chosenHome)
    val canApply = changed && (oneCurrency || (main != home && (rate ?: 0.0) > 0))
    androidx.compose.runtime.LaunchedEffect(Unit) { book.refreshIfStale() }
    // A new pair starts with today's rate (you can change it).
    androidx.compose.runtime.LaunchedEffect(main, home, book.isRefreshing) {
        if (changed && !oneCurrency && liveRate != null) rate = Math.round(liveRate * 10_000) / 10_000.0
    }

    FormSection(
        "Currency",
        if (!oneCurrency && main == home) "Pick two different currencies, or switch on “I only use one currency”."
        else "Every sheet and screen switches to this. Amounts you already entered are not converted, so change it before you start logging.",
    ) {
        ToggleRow("I only use one currency", oneCurrency, "E.g. everything in INR — no converter, no exchange rate") { oneCurrency = it }
        RowDivider()
        CurrencyPickRow(if (oneCurrency) "Currency" else "Main currency",
            if (oneCurrency) "Everything you earn, spend and send" else "What you earn and spend in", main) { main = it }
        if (!oneCurrency) {
            RowDivider()
            CurrencyPickRow("Home currency", "What money sent home is shown in", home) { home = it }
            if (changed) {
                RowDivider()
                NumberRow("Exchange rate", "$chosenHome per $main", rate,
                    liveRate?.let { "Today: 1 $main = ${com.mohammedsharukms.mypersonaltracker.sheet.CurrencyBook.format(it)} $chosenHome" } ?: "Offline — type the rate you use") { rate = it }
            }
        }
        // The sheet's rate belongs to an older pair: say so and offer today's.
        if (!changed && !oneCurrency && !settings.rateIsCurrent) {
            RowDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your exchange rate (${SheetFormat.number(settings.rate, 2)}) was set for ${(settings.rateFor ?: "").replace("/", " → ")}, not $main → $home. Until you update it, the app uses today's rate.",
                    fontSize = 12.sp, color = SheetTheme.warn)
                if (liveRate != null) Text("Use today's rate: 1 $main = ${com.mohammedsharukms.mypersonaltracker.sheet.CurrencyBook.format(liveRate)} $home",
                    fontWeight = FontWeight.SemiBold, color = SheetTheme.navy,
                    modifier = Modifier.clickable { scope.launch { if (store.saveSettings(mapOf("RATE" to Math.round(liveRate * 10_000) / 10_000.0))) onSaved() } })
            }
        }
        if (changed) Column(Modifier.padding(12.dp)) {
            PrimaryButton(if (oneCurrency) "Use only $main" else "Apply $main · $chosenHome to the sheet", enabled = canApply, loading = store.isSaving) { confirming = true }
        }
    }
    if (confirming) {
        AppDialog(
            title = if (oneCurrency) "Use only $main?" else "Switch to $main and $chosenHome?",
            message = "Existing amounts keep their numbers — they won't be converted.",
            icon = androidx.compose.material.icons.Icons.Filled.CurrencyExchange,
            confirmText = "Switch currency",
            onConfirm = {
                confirming = false
                scope.launch { if (store.saveCurrency(main, chosenHome, if (oneCurrency) 1.0 else rate)) onSaved() }
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun CurrencyPickRow(title: String, subtitle: String, code: String, onSelect: (String) -> Unit) {
    val book = androidx.compose.ui.platform.LocalContext.current.container.currency
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = SheetTheme.text)
            Text(subtitle, fontSize = 12.sp, color = SheetTheme.secondary)
        }
        CurrencyButton(book, code, onSelect = onSelect)
    }
}

