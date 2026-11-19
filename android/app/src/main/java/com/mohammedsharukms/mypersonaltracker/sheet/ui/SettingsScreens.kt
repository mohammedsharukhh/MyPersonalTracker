package com.mohammedsharukms.mypersonaltracker.sheet.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mohammedsharukms.mypersonaltracker.container
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.Refresh
import com.mohammedsharukms.mypersonaltracker.health.HealthSteps
import com.mohammedsharukms.mypersonaltracker.health.rememberStepsConnector
import com.mohammedsharukms.mypersonaltracker.reminders.ReminderScheduler
import com.mohammedsharukms.mypersonaltracker.sheet.BudgetAlerts
import com.mohammedsharukms.mypersonaltracker.sheet.NotificationSummary
import com.mohammedsharukms.mypersonaltracker.sheet.PlanState
import com.mohammedsharukms.mypersonaltracker.sheet.SettingsDraft
import com.mohammedsharukms.mypersonaltracker.sheet.SettingsField
import com.mohammedsharukms.mypersonaltracker.sheet.SettingsPage
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStatus
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.ui.splash.SplashPrefs
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Settings is one screen: your sheet at the top, then "Your plan" (pages that save to
// the sheet, each with its own Save), then "This phone" (applies right away).
// Mirrors SettingsViews.swift; the pages and keys are in sheet/SettingsPages.kt.

private enum class SettingsSpot { SHEET, MONEY, HEALTH_FOOD, STUDY, PERIOD, NOTIFICATIONS, DATA }

@Composable
fun SheetSettingsScreen(store: SheetStore, onBack: () -> Unit, startAtSheet: Boolean = false) {
    var spot by rememberSaveable { mutableStateOf(if (startAtSheet) SettingsSpot.SHEET else null) }
    val close = { spot = null }
    when (spot) {
        SettingsSpot.SHEET -> return SheetConnectionPage(store, startAtSheet, close)
        SettingsSpot.MONEY -> return PlanPage(store, SettingsPage.MONEY, close)
        SettingsSpot.HEALTH_FOOD -> return PlanPage(store, SettingsPage.HEALTH_FOOD, close)
        SettingsSpot.STUDY -> return PlanPage(store, SettingsPage.STUDY, close)
        SettingsSpot.PERIOD -> return PeriodPage(store, close)
        SettingsSpot.NOTIFICATIONS -> return NotificationsPage(store, close)
        SettingsSpot.DATA -> return DataToolsScreen(store, close)
        null -> Unit
    }
    val context = LocalContext.current
    var habits by remember { mutableStateOf(false) }
    if (habits) HabitsEditorSheet(store) { habits = false }
    BackHandler(onBack = onBack)

    SheetScreen("Settings", isRefreshing = store.isLoading, onRefresh = { store.refresh() }, onBack = onBack) {
        item {
            val connected = store.isConfigured
            SheetCard(onClick = { spot = SettingsSpot.SHEET }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.TableChart, if (connected) SheetTheme.good else SheetTheme.warn, 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Your sheet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                        Text(SheetStatus.subtitle(connected, if (store.usesGoogleSheet) store.googleAuth.email else null, store.lastSynced?.let { SheetDate.relative(it) }),
                            fontSize = 12.sp, color = if (connected) SheetTheme.secondary else SheetTheme.warn)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SheetTheme.tertiary)
                }
            }
        }
        val settings = store.snapshot?.settings
        val plan = PlanState.of(store.isConfigured, settings != null, store.isLoading, store.errorMessage)
        if (plan == PlanState.Hidden) item {
            Text("Connect your sheet to set your money, health and study targets.", fontSize = 13.sp, color = SheetTheme.secondary, modifier = Modifier.padding(horizontal = 16.dp))
        } else item {
            val scope = rememberCoroutineScope()
            LaunchedEffect(plan) { if (plan == PlanState.Loading && !store.isLoading) store.refresh() }
            SettingsGroup("Targets", if (settings != null) "Saved to your sheet when you tap Save on a page." else null) {
                when {
                    settings != null -> {
                        SettingsRow(Icons.Filled.AccountBalanceWallet, SheetTheme.navy, SettingsPage.MONEY.title, SettingsPage.MONEY.subtitle) { spot = SettingsSpot.MONEY }
                        RowDivider()
                        SettingsRow(Icons.Filled.Favorite, SheetTheme.navy, SettingsPage.HEALTH_FOOD.title, SettingsPage.HEALTH_FOOD.subtitle) { spot = SettingsSpot.HEALTH_FOOD }
                        RowDivider()
                        SettingsRow(Icons.Filled.School, SheetTheme.navy, SettingsPage.STUDY.title, SettingsPage.STUDY.subtitle) { spot = SettingsSpot.STUDY }
                        RowDivider()
                        SettingsRow(Icons.Filled.Tune, SheetTheme.navy, "Habits", "Names, icons, which ones you track") { habits = true }
                        RowDivider()
                        SettingsRow(Icons.Filled.DateRange, SheetTheme.navy, "Period", "${SheetDate.short(settings.startDate)} – ${SheetDate.short(settings.endDate)}") { spot = SettingsSpot.PERIOD }
                    }
                    plan is PlanState.Failed ->
                        SettingsRow(Icons.Filled.Refresh, SheetTheme.bad, "Couldn't load your plan", "${plan.message} · tap to try again") { scope.launch { store.refresh() } }
                    else -> SheetLoadingCard(store.loadProgress)
                }
            }
        }
        item {
            SettingsGroup("This phone", "Only on this phone. Changes apply right away.") {
                val prefs = context.container.prefs
                val (on, total) = NotificationSummary.count({ prefs.getBoolean(it, it != NotificationSummary.EVENING) }, store.isConfigured)
                SettingsRow(Icons.Filled.Notifications, SheetTheme.navy, "Notifications", NotificationSummary.text(on, total)) { spot = SettingsSpot.NOTIFICATIONS }
                if (HealthSteps.status(context) != androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE) {
                    RowDivider()
                    var stepsOn by remember { mutableStateOf(HealthSteps.isEnabled(context)) }
                    val connect = rememberStepsConnector(store) { ok -> stepsOn = ok }
                    ToggleRow("Health Connect", stepsOn, HealthSteps.today?.let { "%,d steps today".format(it) } ?: "Fills in steps, sleep, weight and workouts") {
                        if (it) connect() else { HealthSteps.setEnabled(context, false); stepsOn = false }
                    }
                }
                RowDivider()
                var sound by remember { mutableStateOf(SplashPrefs.soundOn(context)) }
                ToggleRow("Sound on opening", sound, "A short chime, quiet on silent or vibrate") {
                    sound = it
                    SplashPrefs.setSoundOn(context, it)
                }
            }
        }
        // Last and on its own: it holds Reset.
        item {
            SettingsGroup("Your data", null) {
                SettingsRow(Icons.Filled.Storage, SheetTheme.navy, "Export or reset", "Export everything, or reset and start over") { spot = SettingsSpot.DATA }
            }
        }
    }
}

/** Sign in with Google, or (advanced) your own Apps Script web app. */
@Composable
private fun SheetConnectionPage(store: SheetStore, showWebApp: Boolean, onBack: () -> Unit) {
    var url by remember { mutableStateOf(store.webAppUrl) }
    var key by remember { mutableStateOf(store.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var ownWebApp by rememberSaveable { mutableStateOf(showWebApp || (store.webAppUrl.isNotEmpty() && !store.usesGoogleSheet)) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)
    SheetScreen("Your sheet", isRefreshing = store.isLoading, onRefresh = { store.refresh() }, onBack = onBack) {
        item { GoogleSheetCard(store) {} }
        if (!store.usesGoogleSheet) item {
            FormSection("Advanced", if (ownWebApp) "For a sheet you set up yourself: in the sheet, Extensions ▸ Apps Script ▸ run setupNewWorkbook once (or createApiKey for a new key) and copy the key it shows." else null) {
                ToggleRow("Use my own Apps Script web app", ownWebApp, "Only if you set up the sheet yourself") { ownWebApp = it }
                if (ownWebApp) Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(url, { url = it }, label = { Text("Web app URL") }, textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.size(10.dp))
                    OutlinedTextField(
                        key, { key = it }, label = { Text("API key") }, singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton({ showKey = !showKey }) { Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showKey) "Hide key" else "Show key") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.size(10.dp))
                    PrimaryButton("Connect & sync", enabled = url.isNotBlank() && key.isNotBlank(), loading = store.isLoading) {
                        store.setConnection(url.trim(), key.trim())
                        scope.launch {
                            store.refresh()
                            message = if (store.errorMessage == null) "Connected ✓" else null
                        }
                    }
                    message?.let {
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = SheetTheme.good, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(it, color = SheetTheme.good, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    store.errorMessage?.let { Text(it, fontSize = 12.sp, color = SheetTheme.bad, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
    }
}

/** Money, Health & food or Study: targets saved to the sheet with this page's Save. */
@Composable
private fun PlanPage(store: SheetStore, page: SettingsPage, onBack: () -> Unit) {
    val draft = remember(page) { SettingsDraft(page).also { it.seed(store.snapshot?.settings) } }
    // Bumped when the fields are filled again (after a currency switch or weight targets), so they show it.
    var generation by remember { mutableIntStateOf(0) }
    fun reseed() { draft.seed(store.snapshot?.settings); generation++ }
    var confirmDiscard by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var bodyTargets by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val close = { if (draft.hasChanges) confirmDiscard = true else onBack() }
    BackHandler(onBack = close)
    if (confirmDiscard) AppDialog(title = "Discard your changes?", message = "What you changed on ${page.title} isn't saved to your sheet.",
        style = DialogStyle.DESTRUCTIVE, confirmText = "Discard", onConfirm = { confirmDiscard = false; onBack() }, onDismiss = { confirmDiscard = false })
    saveError?.let { AppDialog(title = "Couldn't save", message = it, style = DialogStyle.INFO, dismissText = null, onConfirm = { saveError = null }, onDismiss = { saveError = null }) }
    if (bodyTargets) BodyTargetsSheet(store) { bodyTargets = false; reseed() }

    SheetScreen(page.title, isRefreshing = store.isLoading, onRefresh = { store.refresh() }, onBack = close, actions = {
        if (store.isSaving) CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), color = SheetTheme.navy, strokeWidth = 2.dp)
        else TextButton(enabled = draft.hasChanges, onClick = {
            scope.launch { if (store.saveSettings(draft.changes)) onBack() else saveError = store.errorMessage ?: "The sheet didn't answer. Try again in a minute." }
        }) { Text("Save", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (draft.hasChanges) SheetTheme.navy else SheetTheme.tertiary) }
    }) {
        val settings = store.snapshot?.settings ?: return@SheetScreen item { NotConnectedHint() }
        when (page) {
            SettingsPage.MONEY -> {
                item { CurrencySection(store, settings) { reseed() } }
                item {
                    key(generation) {
                        val follows = draft.values["LIMIT_FROM_SPLIT"] != "0"
                        FormSection("Spending", if (follows) "Until this month's salary is in, your own monthly limit is used." else "Your own monthly limit is used every month.") {
                            Fields(SettingsPage.spending(Money.single), draft)
                            RowDivider()
                            ToggleRow("Monthly limit from salary split", follows, "This month's income × (Essentials + Lifestyle)") {
                                draft.values["LIMIT_FROM_SPLIT"] = if (it) "1" else "0"
                            }
                            if (!follows) { RowDivider(); Fields(SettingsPage.monthlyLimit.drop(1), draft) }
                            RowDivider()
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { KeyValueRow("This month's limit", "${SheetFormat.qar(settings.monthlyLimit)} ${Money.main}") }
                        }
                    }
                }
                item {
                    key(generation) {
                        val total = SettingsPage.split.filter { it.percent }.sumOf { SheetFormat.input(draft.values[it.key].orEmpty()) ?: 0.0 }
                        val adds = Math.abs(total - 100) < 0.01
                        FormSection("Salary split", "Each month's income is shared out like this. Essentials and Lifestyle fill from your expenses, Family support from money sent home, and the emergency fund and Savings / SIP from what you put aside.") {
                            Fields(SettingsPage.split, draft)
                            RowDivider()
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                KeyValueRow("Total", if (adds) "✓ 100%" else "${SheetFormat.number(total, 0)}% — should be 100%", if (adds) SheetTheme.good else SheetTheme.warn)
                            }
                        }
                    }
                }
                store.snapshot?.lists?.let { lists ->
                    item {
                        FormSection("Expense categories", "To change these, edit the Lists tab in your sheet.") {
                            Text(lists.categories.joinToString(" · "), fontSize = 12.sp, color = SheetTheme.secondary, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
            SettingsPage.HEALTH_FOOD -> {
                item { key(generation) { FormSection("Health targets") { Fields(SettingsPage.health, draft) } } }
                store.snapshot?.let { snap -> item { BodyTargetsLink(snap) { bodyTargets = true } } }
                item {
                    key(generation) {
                        FormSection("Food & nutrition", if (settings.followWeight == true) "Typing a calorie or macro target here stops them following your weight." else null) {
                            Fields(SettingsPage.nutrition, draft)
                        }
                    }
                }
            }
            SettingsPage.STUDY -> item { key(generation) { FormSection("Study goals") { Fields(SettingsPage.study, draft) } } }
        }
        item {
            Text("Tap Save to update your sheet.", fontSize = 12.sp, color = SheetTheme.secondary, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun Fields(fields: List<SettingsField>, draft: SettingsDraft) {
    fields.forEachIndexed { i, f ->
        if (i > 0) RowDivider()
        NumberRow(f.title, f.unit, SheetFormat.input(draft.values[f.key].orEmpty())) { draft.values[f.key] = SheetFormat.plain(it, 4) }
    }
}

@Composable
private fun PeriodPage(store: SheetStore, onBack: () -> Unit) {
    var starting by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    val settings = store.snapshot?.settings
    if (starting && settings != null) NewPeriodSheet(store, settings) { starting = false }
    SheetScreen("Period", isRefreshing = store.isLoading, onRefresh = { store.refresh() }, onBack = onBack) {
        if (settings == null) { item { NotConnectedHint() }; return@SheetScreen }
        item {
            FormSection("This period", "Starting a new period copies this one to “Archive” tabs in your sheet and starts fresh with the new dates.") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    KeyValueRow("Starts", SheetDate.short(settings.startDate))
                    HorizontalDivider(color = SheetTheme.input)
                    KeyValueRow("Ends", SheetDate.short(settings.endDate))
                    HorizontalDivider(color = SheetTheme.input)
                    Text("Start the next period…", color = SheetTheme.navy, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clickable { starting = true }.padding(vertical = 10.dp))
                }
            }
        }
    }
}

/** Every reminder in one place. They apply right away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsPage(store: SheetStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = context.container.settings
    val connected = store.isConfigured
    val alerts = SheetAlertPrefs
    var denied by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var morning by remember { mutableIntStateOf(settings.morningMinutes) }
    var evening by remember { mutableIntStateOf(settings.eveningMinutes) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> denied = !granted }
    BackHandler(onBack = onBack)

    /** Saves a switch, asks for permission the first time one goes on, and reschedules. */
    fun changed(on: Boolean, save: () -> Unit) {
        save()
        if (on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        ReminderScheduler.reschedule(context)
    }

    @Composable
    fun SwitchRow(title: String, subtitle: String?, initial: Boolean, save: (Boolean) -> Unit) {
        var on by remember { mutableStateOf(initial) }
        ToggleRow(title, on, subtitle) { on = it; changed(it) { save(it) } }
    }

    SheetScreen("Notifications", isRefreshing = false, onRefresh = {}, onBack = onBack) {
        if (denied) item {
            Text("Notifications are off for this app, so none of these can come. Tap here to allow them.", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = SheetTheme.bad, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                })
        }
        item {
            FormSection("Every day") {
                if (connected) SwitchRow("Morning briefing", "Today's plans, bills, money left, what to revise", alerts.isOn(context, alerts.BRIEFING)) { alerts.set(context, alerts.BRIEFING, it) }
                else SwitchRow("Morning reminder", "Take your smile and write today's to-dos", settings.morningEnabled) { settings.morningEnabled = it }
                RowDivider()
                TimeRow("Morning at", morning) { editing = "morning" }
                RowDivider()
                SwitchRow("Evening: log my day", "Skipped once you've logged · warns you when your streak is at risk", settings.remindersEnabled) { settings.remindersEnabled = it }
                RowDivider()
                TimeRow("Evening at", evening) { editing = "evening" }
            }
        }
        if (connected) {
            item {
                FormSection("When something's due") {
                    SwitchRow("Bills due tomorrow", null, alerts.isOn(context, alerts.BILLS)) { alerts.set(context, alerts.BILLS, it) }
                    RowDivider()
                    SwitchRow("Renewals", "QID, passport, insurance — before they expire", alerts.isOn(context, alerts.RENEWALS)) { alerts.set(context, alerts.RENEWALS, it) }
                    RowDivider()
                    SwitchRow("What Mom & Dad asked for", "At the time each one is due", alerts.isOn(context, alerts.PARENTS)) { alerts.set(context, alerts.PARENTS, it) }
                    RowDivider()
                    SwitchRow("Budget alerts", "At 80% of a bucket and when it goes over", BudgetAlerts.isEnabled(context)) { BudgetAlerts.setEnabled(context, it) }
                }
            }
            item {
                FormSection("Every week") {
                    SwitchRow("Week in review", "Sunday evening", alerts.isOn(context, alerts.WEEKLY)) { alerts.set(context, alerts.WEEKLY, it) }
                }
            }
        }
        item {
            Text(if (connected) "Changes apply right away." else "Connect your sheet for bill, renewal and budget reminders. Changes apply right away.",
                fontSize = 12.sp, color = SheetTheme.secondary, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }

    editing?.let { which ->
        val initial = if (which == "evening") evening else morning
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60)
        AppDialog(
            title = if (which == "evening") "Evening reminder" else "Morning reminder",
            onDismiss = { editing = null },
            confirmText = "Set time",
            onConfirm = {
                val minutes = state.hour * 60 + state.minute
                if (which == "evening") { evening = minutes; settings.eveningMinutes = minutes } else { morning = minutes; settings.morningMinutes = minutes }
                ReminderScheduler.reschedule(context)
                editing = null
            },
        ) { TimeInput(state) }
    }
}

@Composable
private fun SettingsGroup(title: String, footer: String?, rows: @Composable () -> Unit) {
    Column {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp, top = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card)) { rows() }
        if (footer != null) Text(footer, fontSize = 12.sp, color = SheetTheme.secondary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint, 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = SheetTheme.text)
            Text(subtitle, fontSize = 12.sp, color = SheetTheme.secondary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SheetTheme.tertiary)
    }
}

@Composable
private fun TimeRow(title: String, minutes: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, color = SheetTheme.text, modifier = Modifier.weight(1f))
        Text(LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)), color = SheetTheme.navy, fontWeight = FontWeight.SemiBold)
    }
}
