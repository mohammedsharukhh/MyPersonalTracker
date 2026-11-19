package com.mohammedsharukms.mypersonaltracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohammedsharukms.mypersonaltracker.data.DayWithSessions
import com.mohammedsharukms.mypersonaltracker.reminders.ReminderScheduler
import com.mohammedsharukms.mypersonaltracker.ui.TrackerViewModel
import com.mohammedsharukms.mypersonaltracker.ui.history.HistoryScreen
import com.mohammedsharukms.mypersonaltracker.sheet.SheetDemoData
import com.mohammedsharukms.mypersonaltracker.sheet.ui.CurrencyConverterScreen
import com.mohammedsharukms.mypersonaltracker.sheet.ui.DailyScreen
import com.mohammedsharukms.mypersonaltracker.sheet.ui.DataToolsScreen
import com.mohammedsharukms.mypersonaltracker.sheet.ui.DashboardScreen
import com.mohammedsharukms.mypersonaltracker.sheet.ui.FinanceScreen
import com.mohammedsharukms.mypersonaltracker.sheet.ui.MoreDestination
import com.mohammedsharukms.mypersonaltracker.sheet.ui.MonthlyReportScreen
import com.mohammedsharukms.mypersonaltracker.sheet.ui.MoreScreen
import com.mohammedsharukms.mypersonaltracker.sheet.ui.UndoBar
import com.mohammedsharukms.mypersonaltracker.sheet.ui.FoodScreen
import com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetSettingsScreen
import com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetTheme
import com.mohammedsharukms.mypersonaltracker.sheet.ui.SummaryScreen
import com.mohammedsharukms.mypersonaltracker.sheet.ui.StudyScreen
import com.mohammedsharukms.mypersonaltracker.sheet.ui.LocalOpenMore
import com.mohammedsharukms.mypersonaltracker.ui.splash.SplashScreen
import com.mohammedsharukms.mypersonaltracker.ui.smile.SmileCheckInScreen
import com.mohammedsharukms.mypersonaltracker.ui.theme.TrackerTheme
import com.mohammedsharukms.mypersonaltracker.ui.today.PastDayScreen
import com.mohammedsharukms.mypersonaltracker.ui.today.TodayScreen
import com.mohammedsharukms.mypersonaltracker.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The five areas of the workbook. More (summaries, tools, settings, the journal) opens from ⋯ on each tab. */
private enum class Tab(val title: String, val icon: ImageVector, val world: com.mohammedsharukms.mypersonaltracker.sheet.ui.TabWorld) {
    DASHBOARD("Dashboard", Icons.Filled.Dashboard, com.mohammedsharukms.mypersonaltracker.sheet.ui.TabWorld.DASHBOARD),
    DAILY("Daily", Icons.Filled.Checklist, com.mohammedsharukms.mypersonaltracker.sheet.ui.TabWorld.DAILY),
    PARENTS("Parents", Icons.Filled.FamilyRestroom, com.mohammedsharukms.mypersonaltracker.sheet.ui.TabWorld.PARENTS),
    STUDY("Study", Icons.Filled.School, com.mohammedsharukms.mypersonaltracker.sheet.ui.TabWorld.STUDY),
    FOOD("Food", Icons.Filled.Restaurant, com.mohammedsharukms.mypersonaltracker.sheet.ui.TabWorld.FOOD),
    FINANCE("Finance", Icons.Filled.Payments, com.mohammedsharukms.mypersonaltracker.sheet.ui.TabWorld.FINANCE),
}

/** Pay with UPI sits in the middle of the tab bar, after this many tabs. */
private const val UPI_AFTER = 3
private val UPI_SLOT_WIDTH = 60.dp

/** Debug launch extras for screenshots: sheetDemo, sheetTab, sheetOpen, converterBrowse. */
private data class DebugLaunch(val tab: Int, val open: String?, val browse: Boolean)

const val ACTION_ADD_EXPENSE = "com.mohammedsharukms.mypersonaltracker.ADD_EXPENSE"
const val ACTION_LOG_TODAY = "com.mohammedsharukms.mypersonaltracker.LOG_TODAY"

class MainActivity : ComponentActivity() {
    companion object {
        /** From the study timer's notification: open on the Study tab. */
        const val EXTRA_OPEN_STUDY = "openStudy"
        /** Where a tapped notification leads: "weeklyReview", "renewals", "parents", "finance", "dashboard". */
        const val EXTRA_OPEN = "openTarget"
    }

    /** Rotation, dark mode, text size… are handled in place (see the manifest), so open forms keep what you typed. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        enableEdgeToEdge()   // status bar icons follow light / dark again
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Launched on the navy Starting theme (no white flash before the opening animation).
        setTheme(R.style.Theme_MyPersonalTracker)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.DEBUG) com.mohammedsharukms.mypersonaltracker.sheet.ui.LensDebug.demo = intent.getBooleanExtra("lensDemo", false)
        // Debug: run the sheet's script in this phone's WebView on test data and log the outcome ("selftest").
        if (BuildConfig.DEBUG && intent.getBooleanExtra("scriptSelfTest", false)) {
            lifecycleScope.launch { com.mohammedsharukms.mypersonaltracker.sheet.google.ScriptSelfTest.run(applicationContext) }
        }
        if (BuildConfig.DEBUG && intent.getBooleanExtra("seedSampleData", false)) {
            lifecycleScope.launch { SampleData.seedIfEmpty(container.database) }
        }
        if (BuildConfig.DEBUG && intent.getBooleanExtra("sheetDemo", false)) {
            container.sheet.enableDemo(SheetDemoData.json(
                intent.getStringExtra("demoMain") ?: "QAR", intent.getStringExtra("demoHome") ?: "INR",
            ))
        }
        // Home-screen shortcuts (res/xml/shortcuts.xml) arrive as actions.
        val debug = when (intent.action) {
            ACTION_ADD_EXPENSE -> DebugLaunch(0, "expense", false)
            // Tapped notifications say where they lead.
            else -> if (intent.getStringExtra(EXTRA_OPEN) != null) when (val target = intent.getStringExtra(EXTRA_OPEN)) {
                "finance" -> DebugLaunch(Tab.FINANCE.ordinal, null, false)
                "dashboard" -> DebugLaunch(Tab.DASHBOARD.ordinal, null, false)
                "parents" -> DebugLaunch(Tab.PARENTS.ordinal, null, false)
                else -> DebugLaunch(0, target, false)
            } else null
        } ?: when (intent.action) {
            ACTION_LOG_TODAY -> DebugLaunch(Tab.DAILY.ordinal, null, false)
            else -> if (intent.getBooleanExtra(EXTRA_OPEN_STUDY, false)) DebugLaunch(Tab.STUDY.ordinal, null, false) else if (BuildConfig.DEBUG) DebugLaunch(
                intent.getIntExtra("sheetTab", 0), intent.getStringExtra("sheetOpen"), intent.getBooleanExtra("converterBrowse", false),
            ) else DebugLaunch(0, null, false)
        }
        // The opening animation on a fresh launch (not when the screen rotates or the app is restored).
        val splash = savedInstanceState == null && !(BuildConfig.DEBUG && intent.getBooleanExtra("noSplash", false))
        setContent {
            TrackerTheme {
                var showingSplash by rememberSaveable { mutableStateOf(splash) }
                // The app is built behind the splash once it has played, so the animation starts at once.
                var appReady by rememberSaveable { mutableStateOf(!splash) }
                androidx.compose.foundation.layout.Box {
                    if (appReady) {
                        val vm: TrackerViewModel = viewModel { TrackerViewModel(container) }
                        TrackerRoot(vm, debug)
                    }
                    if (showingSplash) SplashScreen(appReady, onReadyForApp = { appReady = true }) { showingSplash = false }
                    // Screenshots of the app's pop-up.
                    var demoDialog by rememberSaveable { mutableStateOf(BuildConfig.DEBUG && intent.getBooleanExtra("dialogDemo", false)) }
                    if (demoDialog) com.mohammedsharukms.mypersonaltracker.sheet.ui.AppDialog(
                        title = "Delete this expense?", message = "It's removed from the sheet. You can undo for a few seconds.",
                        style = com.mohammedsharukms.mypersonaltracker.sheet.ui.DialogStyle.DESTRUCTIVE, confirmText = "Delete",
                        onConfirm = { demoDialog = false }, onDismiss = { demoDialog = false })
                }
            }
        }
    }
}

@Composable
private fun TrackerRoot(vm: TrackerViewModel, debug: DebugLaunch) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sheet = context.container.sheet
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(debug.tab.coerceIn(0, Tab.entries.size - 1)) }
    /** Daily's section: Habits, Planner, Wardrobe (the Dashboard's plan card opens Planner). */
    /** The Planner jumps to this week next ("Plan next week" from the week in review). */
    var plannerWeek by remember { mutableStateOf<LocalDate?>(null) }
    var dailySection by rememberSaveable { mutableIntStateOf(when (debug.open) { "planner" -> 1; "goals" -> 2; "wardrobe" -> 3; else -> 0 }) }
    var more by rememberSaveable { mutableStateOf(when {
        debug.open == "convert" && debug.browse -> MoreDestination.CONVERTER
        debug.open == "renewals" -> MoreDestination.RENEWALS
        debug.open == "cards" -> MoreDestination.CARDS
        debug.open == "weeklyReview" -> MoreDestination.WEEK_REVIEW
        debug.open == "yearReview" -> MoreDestination.YEAR_REVIEW
        else -> null
    }) }
    /** Opened straight from a tab (Settings from the connect card, the converter from Finance), not via More. */
    var openedDirect by rememberSaveable { mutableStateOf(more != null) }
    fun openDirect(destination: MoreDestination) { openedDirect = true; more = destination }
    var pastDay by remember { mutableStateOf<DayWithSessions?>(null) }
    var showingSettings by rememberSaveable { mutableStateOf(false) }
    var showingSmile by rememberSaveable { mutableStateOf(false) }

    // Keep things outside the app (reminders, widgets, the focus notification) in step with the data.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Pick up edits made in the sheet itself since last time (currencies included).
        scope.launch {
            sheet.refresh(); context.container.currency.followSheet()
            com.mohammedsharukms.mypersonaltracker.health.HealthSteps.sync(context, sheet)
        }
        vm.refreshToday()
        context.container.upi.askIfWaiting()
        ReminderScheduler.reschedule(context)
        vm.focusTimer.syncNotification()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        context.container.appScope.launch { WidgetUpdater.updateAll(context) }
    }
    val days by vm.days.collectAsStateWithLifecycle()
    // First launch with no sheet: sign in with Google. "Not now" isn't asked again at launch.
    val welcomePrefs = remember { context.getSharedPreferences("welcome", android.content.Context.MODE_PRIVATE) }
    var welcomeDismissed by remember { mutableStateOf(welcomePrefs.getBoolean("dismissed", false)) }
    fun dismissWelcome() { welcomeDismissed = true; welcomePrefs.edit().putBoolean("dismissed", true).apply() }

    when {
        !welcomeDismissed && !sheet.isDemo && !sheet.isConfigured && more == null -> com.mohammedsharukms.mypersonaltracker.sheet.ui.WelcomeScreen(
            sheet, onNotNow = { dismissWelcome() }, onWebApp = { dismissWelcome(); openDirect(MoreDestination.SETTINGS_SHEET) })
        showingSmile -> SmileCheckInScreen { photo ->
            if (photo != null) vm.saveSelfie(photo)
            showingSmile = false
        }
        showingSettings -> SheetSettingsScreen(sheet, onBack = { showingSettings = false })
        pastDay != null -> PastDayScreen(pastDay!!, vm, onBack = { pastDay = null })
        more != null -> {
            // Screens opened from More go back to More; More itself goes back to the tabs.
            val back = { more = if (more == MoreDestination.MORE || openedDirect) null else MoreDestination.MORE; openedDirect = false }
            when (more!!) {
                MoreDestination.MORE -> MoreScreen(sheet, onBack = { more = null }) { more = it }
                MoreDestination.SETTINGS -> SheetSettingsScreen(sheet, back)
                MoreDestination.SETTINGS_SHEET -> SheetSettingsScreen(sheet, back, startAtSheet = true)
                MoreDestination.TABLES -> SummaryScreen(sheet, onBack = back)
                MoreDestination.CONVERTER -> CurrencyConverterScreen(context.container.currency, onBack = back, openBrowse = debug.browse)
                MoreDestination.REPORT -> MonthlyReportScreen(sheet, back)
                MoreDestination.TODAY -> TodayScreen(vm, onOpenSettings = { showingSettings = true }, onTakeSmile = { showingSmile = true }, onOpenDay = { pastDay = it })
                MoreDestination.HISTORY -> HistoryScreen(vm, onOpenDay = { pastDay = it })
                MoreDestination.RENEWALS -> com.mohammedsharukms.mypersonaltracker.sheet.ui.RenewalsScreen(sheet, back)
                MoreDestination.CARDS -> com.mohammedsharukms.mypersonaltracker.sheet.ui.CardsScreen(back)
                MoreDestination.WEEK_REVIEW -> com.mohammedsharukms.mypersonaltracker.sheet.ui.WeeklyReviewScreen(sheet, back) { week ->
                    plannerWeek = week; dailySection = 1; tab = Tab.DAILY.ordinal; more = null; openedDirect = false
                }
                MoreDestination.YEAR_REVIEW -> com.mohammedsharukms.mypersonaltracker.sheet.ui.YearReviewScreen(sheet, back)
            }
        }
        else -> {
        // `tab` (taps, links into a tab) and the pager (swipes) follow each other.
        val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = tab) { Tab.entries.size }
        LaunchedEffect(tab) { if (pager.currentPage != tab) pager.animateScrollToPage(tab) }
        LaunchedEffect(pager.settledPage) { tab = pager.settledPage }
        Scaffold(
            bottomBar = {
                // Pay with UPI sits raised in the middle; the bar clips its content, so it's drawn on top.
                // The tabs either side each take half the bar, so the button is dead centre.
                androidx.compose.foundation.layout.Box {
                    NavigationBar(containerColor = SheetTheme.cardSolid) {
                        @Composable
                        fun androidx.compose.foundation.layout.RowScope.tabItem(index: Int, item: Tab) = NavigationBarItem(
                            // Follows the page being swiped to, not only the one settled on.
                            selected = pager.targetPage == index,
                            onClick = { tab = index },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.title, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = item.world.accent,
                                selectedTextColor = item.world.accent,
                                indicatorColor = item.world.accent.copy(alpha = 0.15f),
                            ),
                        )
                        androidx.compose.foundation.layout.Row(Modifier.weight(1f)) {
                            Tab.entries.take(UPI_AFTER).forEachIndexed { index, item -> tabItem(index, item) }
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.width(UPI_SLOT_WIDTH))
                        androidx.compose.foundation.layout.Row(Modifier.weight(1f)) {
                            Tab.entries.drop(UPI_AFTER).forEachIndexed { i, item -> tabItem(UPI_AFTER + i, item) }
                        }
                    }
                    // Level with the tab icons, inside the bar.
                    com.mohammedsharukms.mypersonaltracker.sheet.ui.UpiTabButton(
                        Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(top = 8.dp))
                }
            },
        ) { padding ->
            val bottom = androidx.compose.foundation.layout.PaddingValues(bottom = padding.calculateBottomPadding())
            val modifier = Modifier.padding(bottom).consumeWindowInsets(bottom)
            androidx.compose.foundation.layout.Box(modifier) {
                androidx.compose.runtime.CompositionLocalProvider(LocalOpenMore provides { more = MoreDestination.MORE }, com.mohammedsharukms.mypersonaltracker.sheet.ui.LocalOpenSettings provides { openDirect(MoreDestination.SETTINGS) }) {
                    // Swipe sideways between tabs (Study → Food); a row that scrolls
                    // sideways itself scrolls first. Pages either side stay ready.
                    androidx.compose.foundation.pager.HorizontalPager(pager, beyondViewportPageCount = 1, key = { it }) { page ->
                    androidx.compose.runtime.CompositionLocalProvider(com.mohammedsharukms.mypersonaltracker.sheet.ui.LocalTabWorld provides Tab.entries[page].world) {
                    when (Tab.entries[page]) {
                        Tab.DASHBOARD -> DashboardScreen(
                            sheet,
                            onOpenFood = { tab = Tab.FOOD.ordinal },
                            onOpenStudy = { tab = Tab.STUDY.ordinal },
                            onOpenSettings = { openDirect(MoreDestination.SETTINGS) },
                            onOpenConverter = { openDirect(MoreDestination.CONVERTER) },
                            debugOpen = debug.open,
                            onOpenPlanner = { dailySection = 1; tab = Tab.DAILY.ordinal },
                            onOpenGoals = { dailySection = 2; tab = Tab.DAILY.ordinal },
                            onOpenRenewals = { openDirect(MoreDestination.RENEWALS) },
                            onOpenWeekly = { openDirect(MoreDestination.WEEK_REVIEW) },
                            onOpenParents = { tab = Tab.PARENTS.ordinal },
                        )
                        Tab.DAILY -> DailyScreen(sheet, dailySection, plannerWeek, onPlannerWeekUsed = { plannerWeek = null }, logToday = debug.open == "logToday", onSection = { dailySection = it })
                        Tab.PARENTS -> com.mohammedsharukms.mypersonaltracker.sheet.ui.ParentsScreen(sheet)
                        Tab.STUDY -> StudyScreen(sheet, debug.open)
                        Tab.FOOD -> FoodScreen(sheet, debug.open)
                        Tab.FINANCE -> FinanceScreen(sheet, onOpenConverter = { openDirect(MoreDestination.CONVERTER) })
                    }
                    }
                    }
                }
                UndoBar(sheet, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 96.dp))
            }
        }
        }
    }
    // Pay with UPI (Finance ▸ +, the Pay widget, a payment notification), and "did it go through?" on return.
    val upi = context.container.upi
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (debug.open == "scan") upi.payWithScan = true
        if (debug.open == "pay" || debug.open == "scan") upi.showPay = true
    }
    if (upi.showPay) com.mohammedsharukms.mypersonaltracker.sheet.ui.PayScreen(sheet) { upi.showPay = false }
    com.mohammedsharukms.mypersonaltracker.sheet.ui.PaymentQuestion()
    if (pastDay != null && !showingSettings && !showingSmile) BackHandler { pastDay = null }
    if (more != null && more != MoreDestination.SETTINGS && more != MoreDestination.SETTINGS_SHEET && pastDay == null && !showingSettings && !showingSmile) BackHandler {
        more = if (more == MoreDestination.MORE || openedDirect) null else MoreDestination.MORE
        openedDirect = false
    }
}
