package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.sheet.Habit
import com.mohammedsharukms.mypersonaltracker.sheet.InputSection
import com.mohammedsharukms.mypersonaltracker.sheet.MyInput
import com.mohammedsharukms.mypersonaltracker.sheet.MyInputData
import com.mohammedsharukms.mypersonaltracker.sheet.inputData
import com.mohammedsharukms.mypersonaltracker.sheet.SheetDay
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSettings
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.Tracker
import com.mohammedsharukms.mypersonaltracker.sheet.activeHabits
import com.mohammedsharukms.mypersonaltracker.sheet.name
import com.mohammedsharukms.mypersonaltracker.sheet.stepStreak
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * The Daily Tracker sheet: one row per day of the period. The inputs are edited
 * here; Habits Met, Daily Score, Status and Streak are the sheet's formulas.
 * Mirrors DailyTrackerView.swift.
 */
@Composable
fun DailyScreen(store: SheetStore, section: Int = 0, plannerWeek: java.time.LocalDate? = null, onPlannerWeekUsed: () -> Unit = {},
                onSection: (Int) -> Unit = {}, logToday: Boolean = false) {
    var editing by remember { mutableStateOf<SheetDay?>(null) }
    var openedToday by remember { mutableStateOf(!logToday) }
    if (!openedToday) store.snapshot?.days?.firstOrNull { it.date == SheetDate.today }?.let { editing = it; openedToday = true }
    var onlyMissing by remember { mutableStateOf(false) }
    val planner = remember { PlannerUi() }
    androidx.compose.runtime.LaunchedEffect(plannerWeek) { plannerWeek?.let { planner.weekStart = it; onPlannerWeekUsed() } }
    val goals = remember { GoalUi() }

    SheetScreen("Daily", isRefreshing = store.isLoading, onRefresh = { store.refresh() }) {
        val error = store.errorMessage
        if (error != null && store.isConfigured) item { RetryBanner(store, error) }
        item { SyncStatusBar(store) }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val colors = SegmentedButtonDefaults.colors(activeContainerColor = SheetTheme.navy,
                    activeContentColor = Color.White, inactiveContainerColor = SheetTheme.card, inactiveContentColor = SheetTheme.text,
                    activeBorderColor = SheetTheme.navy, inactiveBorderColor = SheetTheme.input)
                listOf("Habits", "Planner", "Goals", "Wardrobe").forEachIndexed { i, label ->
                    SegmentedButton(section == i, { onSection(i) },
                        SegmentedButtonDefaults.itemShape(i, 4), colors = colors) { Text(label, maxLines = 1, softWrap = false) }
                }
            }
        }
        val snapshot = store.snapshot
        if (snapshot == null) {
            if (store.needsConnection) item { NotConnectedHint() } else item { SheetLoadingCard(store.loadProgress) }
            return@SheetScreen
        }
        if (section == 1) { plannerItems(store, snapshot, planner); return@SheetScreen }
        if (section == 2) { lifeGoalItems(store, snapshot, goals); return@SheetScreen }
        if (section == 3) { wardrobeItems(snapshot, planner); return@SheetScreen }
        val today = SheetDate.today
        val todayRow = snapshot.days.firstOrNull { it.date == today }
        if (isNewPeriodDue(snapshot.settings)) item { NewPeriodCard(store, snapshot.settings) }
        item { StepsConnectCard(store) }
        if (todayRow != null || !isNewPeriodDue(snapshot.settings)) item {
            if (todayRow != null) TodayHero(todayRow, snapshot.settings, snapshot.myInputs, snapshot.stepStreak()) { editing = todayRow }
            else SheetCard {
                Text("Today is outside the period in Settings (${SheetDate.short(snapshot.settings.startDate)} – ${SheetDate.short(snapshot.settings.endDate)}).",
                    fontSize = 15.sp, color = SheetTheme.secondary)
            }
        }
        val allPast = snapshot.days.filter { it.date < today }.reversed()
        val missing = allPast.count { !it.isLogged }
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                SectionTitle("Previous days", modifier = Modifier.weight(1f))
                if (missing > 0 || onlyMissing) {
                    FilterPill("Not logged · $missing", Icons.Filled.ErrorOutline, SheetTheme.warn, onlyMissing) { onlyMissing = !onlyMissing }
                }
            }
        }
        val past = allPast.filter { !onlyMissing || !it.isLogged }
        if (past.isEmpty()) item {
            Text(if (onlyMissing) "Every day is logged. Nice work!" else "No earlier days in this period yet.", fontSize = 15.sp, color = SheetTheme.secondary)
        }
        items(past, key = { it.date }) { day -> DayRow(day, snapshot.settings, snapshot.myInputs) { editing = day } }
    }
    editing?.let { day -> DayLogSheet(store, day) { editing = null } }
    PlannerSheets(store, planner)
    GoalSheets(store, goals)
}

@Composable
private fun TodayHero(day: SheetDay, settings: SheetSettings, inputs: MyInputData?, stepStreak: Int = 0, onLog: () -> Unit) {
    HeroBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Today", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
                Text(SheetDate.date(day.date)?.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) ?: day.date,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                day.status?.let {
                    Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.padding(top = 4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            ScoreRing(day.score ?: 0.0, 72.dp) {
                Text(if (day.isLogged) SheetFormat.percent(day.score, 0) else "—", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        val dots = HabitDot.all(day, settings, inputs)
        @Composable fun heroDot(dot: HabitDot, modifier: Modifier) {
            Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AnswerDot(dot.icon, dot.name, dot.answer, onDark = true, size = 38.dp)
                Text(dot.name, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 2, textAlign = TextAlign.Center,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
            }
        }
        // Rows of four rather than one row scrolling sideways: no name, and no habit, is cut off at the edge.
        val perRow = if (dots.size <= 5) dots.size.coerceAtLeast(1) else 4
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            dots.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { heroDot(it, Modifier.weight(1f)) }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (Tracker.WATER.isOn(settings)) TargetBar(Icons.Filled.WaterDrop, "Water", day.water, settings.waterTarget, "L")
            if (Tracker.SLEEP.isOn(settings)) TargetBar(Icons.Filled.Bed, "Sleep", day.sleep, settings.sleepTarget, "h")
            if (Tracker.STEPS.isOn(settings)) TargetBar(Icons.AutoMirrored.Filled.DirectionsWalk, "Steps", day.steps, settings.stepsTarget, "", digits = 0)
            if (stepStreak > 1 && Tracker.STEPS.isOn(settings)) {
                val left = maxOf(0.0, (settings.stepsTarget ?: 8000.0) - (day.steps ?: 0.0)).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (left > 0) "$stepStreak-day step streak · ${"%,d".format(left)} more today keeps it going" else "$stepStreak-day step streak",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).clickable(onClick = onLog).padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (day.isLogged) Icons.Filled.Edit else Icons.Filled.AddCircle, null, tint = SheetTheme.navyDeep, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (day.isLogged) "Edit today's log" else "Log today", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.navyDeep)
        }
    }
}

/** Progress toward a Settings target, on the hero's dark background. */
@Composable
private fun TargetBar(icon: ImageVector, title: String, value: Double?, target: Double?, unit: String, digits: Int = 1) {
    val fraction = (value ?: 0.0) / maxOf(target ?: 1.0, 0.0001)
    val figure = "${SheetFormat.number(value, digits)}${if (value == null) "" else unit} / ${SheetFormat.number(target, digits)}"
    val color = if (fraction >= 1) SheetTheme.heroGreen else Color.White
    // Big text: name and figure on one line, the bar under them, nothing squeezed into fixed widths.
    if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
            Text(figure, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
        }
        Bar(fraction, color, Color.White.copy(alpha = 0.18f), modifier = Modifier.fillMaxWidth())
    } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.width(44.dp))
        Bar(fraction, color, Color.White.copy(alpha = 0.18f), modifier = Modifier.weight(1f))
        Text(figure, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.End, modifier = Modifier.width(92.dp))
    }
}

@Composable
private fun DayRow(day: SheetDay, settings: SheetSettings, inputs: MyInputData?, onClick: () -> Unit) {
    val date = SheetDate.date(day.date)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date?.format(DateTimeFormatter.ofPattern("EEE"))?.uppercase() ?: "", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
            Text(date?.dayOfMonth?.toString() ?: "", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text)
            Text(date?.format(DateTimeFormatter.ofPattern("MMM")) ?: "", fontSize = 11.sp, color = SheetTheme.secondary)
        }
        if (day.isLogged) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val dots = HabitDot.all(day, settings, inputs)
                    dots.take(if (dots.size > 7) 6 else 7).forEach { AnswerDot(it.icon, it.name, it.answer, onDark = false, size = 26.dp) }
                    if (dots.size > 7) Text("+${dots.size - 6}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (Tracker.WATER.isOn(settings)) Mini(Icons.Filled.WaterDrop, day.water, "L")
                    if (Tracker.SLEEP.isOn(settings)) Mini(Icons.Filled.Bed, day.sleep, "h")
                    if (Tracker.STEPS.isOn(settings)) Mini(Icons.AutoMirrored.Filled.DirectionsWalk, day.steps, "", 0)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(SheetFormat.percent(day.score, 0), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SheetTheme.tintForStatus(day.status))
                day.status?.let { StatusPill(it) }
            }
        } else {
            Text("Not logged", fontSize = 15.sp, color = SheetTheme.secondary, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.AddCircle, null, tint = SheetTheme.navy, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun Mini(icon: ImageVector, value: Double?, unit: String, digits: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, null, tint = SheetTheme.secondary, modifier = Modifier.size(12.dp))
        Text(value?.let { SheetFormat.number(it, digits) + unit } ?: "—", fontSize = 12.sp, color = SheetTheme.secondary)
    }
}

/** A habit's icon: filled green for Y, red for N, outlined grey when blank. */
@Composable
fun HabitIcon(habit: Habit, answer: String?, onDark: Boolean, size: Dp, settings: SheetSettings? = null) =
    AnswerDot(habitIcon(habit, settings), settings?.let { habit.name(it) } ?: habit.title, answer, onDark, size)

/** One habit on Daily — one of the five or one of yours. */
data class HabitDot(val id: String, val icon: ImageVector, val name: String, val answer: String?) {
    companion object {
        /** The five that are on, then yours. */
        fun all(day: SheetDay, settings: SheetSettings, inputs: MyInputData?): List<HabitDot> {
            val values = inputs?.values(day.date) ?: emptyMap()
            return activeHabits(settings).map { HabitDot(it.name, habitIcon(it, settings), it.name(settings), day.answer(it)) } +
                (inputs?.list(InputSection.HABIT) ?: emptyList()).map { HabitDot(it.id, HabitIcons.icon(it.icon) ?: Icons.Filled.Checklist, it.name, values[it.id]) }
        }
    }
}

/** Filled green for Y, red for N, outlined grey when blank. */
@Composable
fun AnswerDot(icon: ImageVector, name: String, answer: String?, onDark: Boolean, size: Dp) {
    val fill = when (answer) { "Y" -> SheetTheme.good; "N" -> SheetTheme.bad; else -> Color.Transparent }
    val stroke = if (onDark) Color.White.copy(alpha = 0.35f) else SheetTheme.tertiary
    Box(
        Modifier.size(size).clip(CircleShape).background(fill)
            .then(if (answer == null) Modifier.border(1.5.dp, stroke, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (answer == "N") Icons.Filled.Close else icon,
            contentDescription = "$name: ${when (answer) { "Y" -> "done"; "N" -> "missed"; else -> "not logged" }}",
            tint = if (answer == null) (if (onDark) Color.White.copy(alpha = 0.6f) else SheetTheme.secondary) else Color.White,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

// MARK: - Editor

/** Log or edit one day. Writes exactly the Daily Tracker's input cells, and what you logged for your own inputs. */
@Composable
fun DayLogSheet(store: SheetStore, initial: SheetDay, onDismiss: () -> Unit) {
    var day by remember(initial.date) { mutableStateOf(initial) }
    val savedValues = remember(initial.date) { store.snapshot?.inputData?.values(initial.date) ?: emptyMap() }
    var values by remember(initial.date) { mutableStateOf(savedValues) }
    val settings = store.snapshot?.settings
    val inputs = store.snapshot?.inputData ?: MyInputData()
    val scope = rememberCoroutineScope()
    var customizing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<InputEditTarget?>(null) }
    val habits = settings?.let { activeHabits(it) } ?: Habit.entries
    val myHabits = inputs.list(InputSection.HABIT)
    val myHealth = inputs.list(InputSection.HEALTH)
    fun on(t: Tracker) = settings?.let { t.isOn(it) } ?: true
    fun setValue(id: String, value: String?) { values = if (value == null) values - id else values + (id to value) }
    EditorSheet(
        title = SheetDate.weekdayDay(day.date),
        onDismiss = onDismiss,
        saving = store.isSaving,
        error = store.errorMessage.takeIf { !store.isSaving && store.snapshot != null },
        onSave = {
            scope.launch {
                val ok = store.saveDay(day)
                store.saveInputValues(day.date, (values.keys + savedValues.keys).filter { values[it] != savedValues[it] }.associateWith { values[it] ?: "" })
                if (ok) onDismiss()
            }
        },
    ) {
        FormSection("Habits", "Tap once for done, again for missed, a third time to clear. Tap a name to rename it or change its icon.",
            onAdd = { editing = InputEditTarget.Mine(MyInput.new(InputSection.HABIT, inputs.inputs.size + 1)) }) {
            if (habits.isEmpty() && myHabits.isEmpty()) {
                Text("No habits on. Tap + to add one.", fontSize = 15.sp, color = SheetTheme.secondary, modifier = Modifier.padding(16.dp))
            }
            habits.forEachIndexed { i, habit ->
                if (i > 0) RowDivider()
                HabitLogRow(habitIcon(habit, settings), settings?.let { habit.name(it) } ?: habit.title, day.answer(habit),
                    onRename = { editing = InputEditTarget.BuiltIn(habit) }) { day = day.withAnswer(habit, it) }
            }
            myHabits.forEachIndexed { i, input ->
                if (i > 0 || habits.isNotEmpty()) RowDivider()
                HabitLogRow(HabitIcons.icon(input.icon) ?: Icons.Filled.Checklist, input.name, values[input.id],
                    onRename = { editing = InputEditTarget.Mine(input) }) { setValue(input.id, it) }
            }
            if (habits.size + myHabits.size > 1) {
                RowDivider()
                Row(
                    Modifier.fillMaxWidth().clickable {
                        habits.forEach { day = day.withAnswer(it, "Y") }
                        myHabits.forEach { setValue(it.id, "Y") }
                    }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = SheetTheme.good, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Mark all done", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.good)
                }
            }
        }
        val health = listOfNotNull<@Composable () -> Unit>(
            if (on(Tracker.WATER)) ({ StepperRow(Icons.Filled.WaterDrop, SheetTheme.sky, "Water", "L", day.water, 0.5, settings?.waterTarget) { day = day.copy(water = it) } }) else null,
            if (on(Tracker.SLEEP)) ({ StepperRow(Icons.Filled.Bed, SheetTheme.indigo, "Sleep", "h", day.sleep, 0.5, settings?.sleepTarget) { day = day.copy(sleep = it) } }) else null,
            if (on(Tracker.STEPS)) ({ StepperRow(Icons.AutoMirrored.Filled.DirectionsWalk, SheetTheme.good, "Steps", "", day.steps, 500.0, settings?.stepsTarget) { day = day.copy(steps = it) } }) else null,
            if (on(Tracker.SCREEN)) ({ StepperRow(Icons.Filled.PhoneAndroid, SheetTheme.warn, "Screen time", "h", day.screen, 0.5, settings?.screenLimit, isLimit = true) { day = day.copy(screen = it) } }) else null,
            if (on(Tracker.WEIGHT)) ({ NumberRow("Weight", "kg", day.weight) { day = day.copy(weight = it) } }) else null,
        ) + myHealth.map { input ->
            @Composable {
                StepperRow(HabitIcons.icon(input.icon) ?: Icons.Filled.Star, SheetTheme.navy, input.name, spacedUnit(input.unit),
                    values[input.id]?.toDoubleOrNull(), input.stepSize, input.target, onTitleClick = { editing = InputEditTarget.Mine(input) }) { v ->
                    setValue(input.id, v?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() })
                }
            }
        }
        FormSection("Health", onAdd = { editing = InputEditTarget.Mine(MyInput.new(InputSection.HEALTH, inputs.inputs.size + 1)) }) {
            if (health.isEmpty()) {
                Text("Nothing to track here. Tap + to add something — pages read, cups of tea, minutes meditated.",
                    fontSize = 15.sp, color = SheetTheme.secondary, modifier = Modifier.padding(16.dp))
            }
            health.forEachIndexed { i, row -> if (i > 0) RowDivider(); row() }
        }
        FormSection("Notes") {
            OutlinedTextField(
                value = day.notes ?: "",
                onValueChange = { day = day.copy(notes = it) },
                placeholder = { Text("Anything worth remembering") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
        FormSection("") {
            Row(Modifier.fillMaxWidth().clickable { customizing = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tune, null, tint = SheetTheme.navy, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text("Customize habits & trackers", fontSize = 15.sp, color = SheetTheme.navy, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (customizing) HabitsEditorSheet(store) { customizing = false }
    editing?.let { target -> InputEditorSheet(store, target) { editing = null } }
}

/** "cups" reads "2 cups"; short ones like "L" or "kg" stay "2L". */
internal fun spacedUnit(unit: String?): String = unit?.trim()?.let { if (it.length > 2) " $it" else it } ?: ""

/** A habit on the day log: tap the icon or name to rename it, the answer to cycle it. */
@Composable
private fun HabitLogRow(icon: ImageVector, name: String, answer: String?, onRename: () -> Unit, onChange: (String?) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onRename).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, SheetTheme.navy, 32.dp)
            Spacer(Modifier.width(12.dp))
            Text(name, fontSize = 15.sp, color = SheetTheme.text)
        }
        AnswerToggle(answer, onChange)
    }
}

/** A titled white group of rows, like an iOS form section. */
@Composable
fun FormSection(title: String, footer: String? = null, onAdd: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column {
        if (title.isNotEmpty() || onAdd != null) Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary, modifier = Modifier.weight(1f))
            onAdd?.let {
                Row(Modifier.clip(CircleShape).background(SheetTheme.navy).clickable(onClick = it).padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, "Add to $title", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card)) { content() }
        if (footer != null) Text(footer, fontSize = 12.sp, color = SheetTheme.secondary, modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp))
    }
}

@Composable
fun RowDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = SheetTheme.input)

/** Cycles blank → Y → N → blank, shown as a coloured check or cross. */
@Composable
private fun AnswerToggle(answer: String?, onChange: (String?) -> Unit) {
    val (icon, tint, text) = when (answer) {
        "Y" -> Triple(Icons.Filled.CheckCircle, SheetTheme.good, "Done")
        "N" -> Triple(Icons.Filled.Cancel, SheetTheme.bad, "Missed")
        else -> Triple(Icons.Filled.RadioButtonUnchecked, SheetTheme.secondary, "—")
    }
    Row(
        Modifier.clip(CircleShape).clickable { onChange(if (answer == null) "Y" else if (answer == "Y") "N" else null) }.padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = tint)
        Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
    }
}

/** − value + for quick entry, with the target; tap the number to type it. */
@Composable
fun StepperRow(icon: ImageVector, tint: Color, title: String, unit: String, value: Double?, step: Double, target: Double?, isLimit: Boolean = false,
               onTitleClick: (() -> Unit)? = null, onChange: (Double?) -> Unit) {
    val digits = if (step >= 1) 0 else 1
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, 32.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, color = SheetTheme.text)
                if (target != null) {
                    val met = value?.let { if (isLimit) it <= target else it >= target } ?: false
                    Text("${if (isLimit) "Limit" else "Target"} ${SheetFormat.number(target, digits)}$unit${if (met) " ✓" else ""}",
                        fontSize = 12.sp, color = if (met) SheetTheme.good else SheetTheme.secondary)
                } else if (unit.isNotEmpty() && onTitleClick != null) {
                    Text(unit.trim(), fontSize = 12.sp, color = SheetTheme.secondary)
                }
            }
        }
        StepButton(Icons.Filled.Remove, enabled = (value ?: 0.0) > 0) { onChange(maxOf(0.0, (value ?: 0.0) - step)) }
        InlineNumber(value, digits, Modifier.width(if (step >= 100) 72.dp else 48.dp), onChange)
        StepButton(Icons.Filled.Add) { onChange((value ?: 0.0) + step) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(SheetTheme.input).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) SheetTheme.navy else SheetTheme.tertiary, modifier = Modifier.size(18.dp)) }
}

/** A borderless centred number field that follows outside changes (the steppers). */
@Composable
private fun InlineNumber(value: Double?, digits: Int, modifier: Modifier, onChange: (Double?) -> Unit) {
    var text by remember { mutableStateOf(SheetFormat.plain(value, digits)) }
    if (SheetFormat.input(text) != value && !(text.isEmpty() && value == null)) text = SheetFormat.plain(value, maxOf(digits, 2))
    BasicTextField(
        value = text,
        onValueChange = { text = it; onChange(SheetFormat.input(it)) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = SolidColor(SheetTheme.navy),
        modifier = modifier,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (text.isEmpty()) Text("—", fontSize = 17.sp, color = SheetTheme.tertiary)
                inner()
            }
        },
    )
}

/** A numeric input with a unit; empty means an empty cell in the sheet. */
@Composable
fun NumberRow(title: String, unit: String, value: Double?, hint: String? = null, onChange: (Double?) -> Unit) {
    var text by remember { mutableStateOf(SheetFormat.plain(value)) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = SheetTheme.text)
            if (hint != null) Text(hint, fontSize = 12.sp, color = SheetTheme.secondary)
        }
        Row(
            Modifier.width(140.dp).clip(RoundedCornerShape(10.dp)).background(SheetTheme.input).padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it; onChange(SheetFormat.input(it)) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, textAlign = TextAlign.End),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(SheetTheme.navy),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterEnd) {
                        if (text.isEmpty()) Text("0", fontSize = 16.sp, color = SheetTheme.tertiary)
                        inner()
                    }
                },
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(unit, fontSize = 12.sp, color = SheetTheme.secondary)
            }
        }
    }
}

/** Offers to fill steps in from Health Connect, until it's connected (or dismissed). */
@Composable
private fun StepsConnectCard(store: SheetStore) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var hidden by remember {
        mutableStateOf(store.isDemo || prefs.getBoolean("healthStepsCardDismissed", false) ||
            com.mohammedsharukms.mypersonaltracker.health.HealthSteps.isEnabled(context) ||
            com.mohammedsharukms.mypersonaltracker.health.HealthSteps.status(context) == androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE)
    }
    var failed by remember { mutableStateOf(false) }
    val connect = com.mohammedsharukms.mypersonaltracker.health.rememberStepsConnector(store) { ok -> if (ok) hidden = true else failed = true }
    if (hidden) return
    SheetCard {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(Icons.AutoMirrored.Filled.DirectionsWalk, SheetTheme.good, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Fill in steps, sleep and more automatically", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                Text(if (failed) "Steps access wasn't allowed. You can allow it in Health Connect ▸ App permissions, then try again."
                     else "Your phone already tracks them. Connect Health Connect and each day's steps, sleep, weight and workouts fill in here by themselves.",
                    fontSize = 13.sp, color = SheetTheme.secondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterPill("Connect Health Connect", null, SheetTheme.good, true) { connect() }
                    Spacer(Modifier.width(14.dp))
                    Text("Not now", fontSize = 14.sp, color = SheetTheme.secondary, modifier = Modifier.clickable {
                        prefs.edit().putBoolean("healthStepsCardDismissed", true).apply(); hidden = true
                    })
                }
            }
        }
    }
}
