package com.mohammedsharukms.mypersonaltracker.sheet.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.sheet.FoodItem
import com.mohammedsharukms.mypersonaltracker.sheet.Meal
import com.mohammedsharukms.mypersonaltracker.sheet.MealPhotos
import com.mohammedsharukms.mypersonaltracker.sheet.MealType
import com.mohammedsharukms.mypersonaltracker.sheet.NutritionTotals
import com.mohammedsharukms.mypersonaltracker.sheet.PlanItem
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSettings
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.foodData
import com.mohammedsharukms.mypersonaltracker.sheet.meals
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// The Food tab: today's meals against your targets, the week's meal plan (with
// a shopping list), and trends — protein, calories and food money. Mirrors FoodView.swift.

fun mealIcon(type: MealType): ImageVector = when (type) {
    MealType.BREAKFAST -> Icons.Filled.WbTwilight
    MealType.LUNCH -> Icons.Filled.WbSunny
    MealType.DINNER -> Icons.Filled.Bedtime
    MealType.SNACK -> Icons.Filled.Cookie
}

@Composable
fun FoodScreen(store: SheetStore, debugOpen: String? = null) {
    var section by rememberSaveable { mutableStateOf(0) }
    var day by remember { mutableStateOf(LocalDate.now()) }
    var weekStart by remember { mutableStateOf(LocalDate.now().with(DayOfWeek.MONDAY)) }
    var request by remember { mutableStateOf<MealRequest?>(null) }
    var bodyTargets by remember { mutableStateOf(false) }
    val shoppingUi = remember { ShoppingUi() }
    LaunchedEffect(Unit) { when (debugOpen) { "food-plan" -> section = 1; "shopping" -> section = 2; "food-trends" -> section = 3; "body" -> bodyTargets = true } }
    if (bodyTargets) BodyTargetsSheet(store) { bodyTargets = false }

    SheetScreen("Food", isRefreshing = store.isLoading, onRefresh = { store.refresh() }) {
        val error = store.errorMessage
        if (error != null && store.isConfigured) item { RetryBanner(store, error) }
        item { SyncStatusBar(store) }
        store.snapshot?.foodData?.photoError?.let { message ->
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SheetTheme.warn.copy(alpha = 0.1f)).padding(12.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = SheetTheme.warn, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(message, fontSize = 13.sp, color = SheetTheme.text)
                }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val colors = SegmentedButtonDefaults.colors(activeContainerColor = SheetTheme.navy, activeContentColor = Color.White,
                    inactiveContainerColor = SheetTheme.card, inactiveContentColor = SheetTheme.text,
                    activeBorderColor = SheetTheme.navy, inactiveBorderColor = SheetTheme.input)
                listOf("Today", "Plan", "Shopping", "Trends").forEachIndexed { i, label ->
                    SegmentedButton(section == i, { section = i }, SegmentedButtonDefaults.itemShape(i, 4), colors = colors) { Text(label, maxLines = 1) }
                }
            }
        }
        val snapshot = store.snapshot
        if (snapshot == null) {
            if (store.needsConnection) item { NotConnectedHint() } else item { SheetLoadingCard(store.loadProgress) }
            return@SheetScreen
        }
        when (section) {
            0 -> today(store, snapshot, day, { day = it }, { bodyTargets = true }) { request = it }
            1 -> weekPlan(store, snapshot, weekStart, { weekStart = it }, { section = 2 }) { request = it }
            2 -> {
                val keys = (0..6).map { weekStart.plusDays(it.toLong()).toString() }.toSet()
                shoppingItems(store, snapshot, snapshot.foodData.plan.filter { it.date in keys && !it.eaten }, shoppingUi)
            }
            else -> trends(snapshot)
        }
    }
    ShoppingSheets(store, shoppingUi)
    request?.let { MealEditorSheet(store, it.copy(date = if (it.meal == null && it.fromPlan == null) day else it.date)) { request = null } }
}

// MARK: - Today

private fun LazyListScope.today(store: SheetStore, s: SheetSnapshot, day: LocalDate, setDay: (LocalDate) -> Unit, onBodyTargets: () -> Unit, open: (MealRequest) -> Unit) {
    val key = day.toString()
    val meals = s.meals(key)
    val totals = NutritionTotals.ofEntries(meals.flatMap { it.entries })
    val cost = meals.mapNotNull { it.cost }.sum()
    item {
        Stepper(
            title = when (day) { LocalDate.now() -> "Today"; LocalDate.now().minusDays(1) -> "Yesterday"; else -> day.format(DateTimeFormatter.ofPattern("EEEE")) },
            subtitle = day.format(DateTimeFormatter.ofPattern("d MMMM")),
            onBack = { setDay(day.minusDays(1)) },
            onForward = if (day < LocalDate.now()) ({ setDay(day.plusDays(1)) }) else null,
        )
    }
    item { NutritionHero(totals, s.settings, cost) }
    item { BodyTargetsLink(s) { onBodyTargets() } }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickFood("Snap meal", Icons.Filled.CameraAlt, primary = false, Modifier.weight(1f)) { open(MealRequest(type = MealType.forNow(), camera = true)) }
            QuickFood("Add food", Icons.Filled.AddCircle, primary = true, Modifier.weight(1f)) { open(MealRequest(type = MealType.forNow(), search = true)) }
        }
    }
    val planned = s.foodData.plan.filter { it.date == key && !it.eaten }
    if (planned.isNotEmpty()) item {
        SheetCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.EventAvailable, null, tint = SheetTheme.text, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Planned for ${if (day == LocalDate.now()) "today" else "this day"}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            }
            MealType.entries.forEach { type ->
                val items = planned.filter { it.meal == type.label }
                if (items.isNotEmpty()) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(type.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                        Text(items.joinToString { it.food }, fontSize = 12.sp, color = SheetTheme.secondary, maxLines = 2)
                    }
                    Text("Log it", color = SheetTheme.good, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(SheetTheme.good.copy(alpha = 0.12f))
                            .clickable { open(MealRequest(type = type, date = day, items = items.map { it.asItem }, fromPlan = key to type)) }
                            .padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
    }
    MealType.entries.forEach { type ->
        val ofType = meals.filter { it.type == type }
        item(key = "head-${type.name}") {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(mealIcon(type), null, tint = SheetTheme.text, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(type.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, modifier = Modifier.weight(1f))
                if (ofType.isNotEmpty()) {
                    val t = NutritionTotals.ofEntries(ofType.flatMap { it.entries })
                    Text("${SheetFormat.number(t.calories, 0)} kcal · ${SheetFormat.number(t.protein, 0)} g protein", fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
                }
            }
        }
        ofType.forEach { meal -> item(key = meal.id) { MealCard(store, meal) { open(MealRequest(meal = meal)) } } }
        if (ofType.isEmpty()) item(key = "add-${type.name}") {
            Text("+  Add ${type.label.lowercase()}", fontSize = 15.sp, color = SheetTheme.secondary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, SheetTheme.input, RoundedCornerShape(16.dp))
                    .clickable { open(MealRequest(type = type, date = day, search = true)) }.padding(14.dp))
        }
    }
}

@Composable
private fun Stepper(title: String, subtitle: String, onBack: () -> Unit, onForward: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", tint = SheetTheme.navy) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
            Text(subtitle, fontSize = 12.sp, color = SheetTheme.secondary)
        }
        IconButton({ onForward?.invoke() }, enabled = onForward != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Forward", tint = if (onForward != null) SheetTheme.navy else SheetTheme.tertiary)
        }
    }
}

/** Calories as a ring, protein/carbs/fat as bars, and what food cost. */
@Composable
fun NutritionHero(t: NutritionTotals, settings: SheetSettings, cost: Double) {
    val target = settings.calorieTarget ?: 2200.0
    val left = target - t.calories
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(SheetTheme.heroGradient).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(t.calories / maxOf(target, 1.0), 110.dp, 10.dp, if (t.calories > target * 1.05) SheetTheme.heroRed else SheetTheme.heroAmber) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(SheetFormat.number(t.calories, 0), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("of ${SheetFormat.number(target, 0)} kcal", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroMacro("Protein", t.protein, settings.proteinTarget ?: 120.0, Color(0xFF4ADE80))
                HeroMacro("Carbs", t.carbs, settings.carbTarget ?: 250.0, Color(0xFF7DD3FC))
                HeroMacro("Fat", t.fat, settings.fatTarget ?: 70.0, Color(0xFFC4B5FD))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroChip(if (left >= 0) Icons.Filled.LocalFireDepartment else Icons.Filled.Warning,
                if (left >= 0) "${SheetFormat.number(left, 0)} kcal left" else "${SheetFormat.number(-left, 0)} kcal over")
            if (t.protein >= (settings.proteinTarget ?: 120.0)) HeroChip(Icons.Filled.CheckCircle, "Protein goal hit")
            if (cost > 0) HeroChip(Icons.Filled.Payments, "${SheetFormat.qar(cost)} ${Money.main}")
        }
    }
}

@Composable
private fun HeroMacro(title: String, value: Double, target: Double, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
            Text("${SheetFormat.number(value, 0)} / ${SheetFormat.number(target, 0)} g", fontSize = 11.sp, color = Color.White)
        }
        Bar(value / maxOf(target, 1.0), tint, Color.White.copy(alpha = 0.2f), 7.dp)
    }
}

@Composable
/** One filled main action per screen; the other is the quieter tinted style. */
private fun QuickFood(title: String, icon: ImageVector, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ink = if (primary) Color.White else SheetTheme.navy
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(if (primary) SheetTheme.navy else SheetTheme.navy.copy(alpha = 0.12f))
        .clickable(onClick = onClick).padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink)
    }
}

@Composable
fun MealCard(store: SheetStore, meal: Meal, onClick: () -> Unit) {
    val context = LocalContext.current
    var photo by remember(meal.id, meal.photo) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(meal.id, meal.photo) { photo = MealPhotos.bitmap(context, store, meal, thumb = true) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card).clickable(onClick = onClick).padding(12.dp)) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(SheetTheme.good.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            val p = photo
            if (p != null) Image(p.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp))
            else Icon(mealIcon(meal.type), null, tint = SheetTheme.good)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.entries.joinToString { it.food }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, maxLines = 2,
                    modifier = Modifier.weight(1f, fill = false))
                if (meal.isPending) { Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.Schedule, "Waiting to sync", tint = SheetTheme.sky, modifier = Modifier.size(13.dp)) }
            }
            val t = meal.totals
            Text("${SheetFormat.number(t.calories, 0)} kcal · P ${SheetFormat.number(t.protein, 0)} g · C ${SheetFormat.number(t.carbs, 0)} g · F ${SheetFormat.number(t.fat, 0)} g",
                fontSize = 12.sp, color = SheetTheme.secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                meal.time?.let { Text(it, fontSize = 11.sp, color = SheetTheme.tertiary) }
                meal.cost?.takeIf { it > 0 }?.let { Text("${SheetFormat.qar(it)} ${Money.main}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.teal) }
            }
        }
    }
}

// MARK: - Week plan

private fun LazyListScope.weekPlan(store: SheetStore, s: SheetSnapshot, weekStart: LocalDate, setWeek: (LocalDate) -> Unit, onShopping: () -> Unit, open: (MealRequest) -> Unit) {
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val keys = days.map { it.toString() }.toSet()
    val weekItems = s.foodData.plan.filter { it.date in keys }
    val totals = NutritionTotals.ofPlan(weekItems)
    val cost = weekItems.mapNotNull { it.cost }.sum()
    item {
        Stepper(
            title = if (weekStart == LocalDate.now().with(DayOfWeek.MONDAY)) "This week" else "Week of ${weekStart.format(DateTimeFormatter.ofPattern("d MMM"))}",
            subtitle = "${weekStart.format(DateTimeFormatter.ofPattern("d MMM"))} – ${days.last().format(DateTimeFormatter.ofPattern("d MMM"))}",
            onBack = { setWeek(weekStart.minusDays(7)) }, onForward = { setWeek(weekStart.plusDays(7)) },
        )
    }
    item { WeekSummary(store, weekItems, totals, cost, weekStart, onShopping) }
    days.forEach { date -> item(key = "day-$date") { DayPlanCard(store, s, date) { open(it) } } }
}

@Composable
private fun WeekSummary(store: SheetStore, weekItems: List<PlanItem>, totals: NutritionTotals, cost: Double, weekStart: LocalDate, onShopping: () -> Unit) {
    var addedNote by remember { mutableStateOf<String?>(null) }
    var copying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SheetCard {
        Row {
            Column(Modifier.weight(1f)) {
                Text("Week planned", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                Text("${SheetFormat.number(totals.calories / 7, 0)} kcal · ${SheetFormat.number(totals.protein / 7, 0)} g protein a day on average",
                    fontSize = 12.sp, color = SheetTheme.secondary)
            }
            if (cost > 0) Column(horizontalAlignment = Alignment.End) {
                Text(SheetFormat.qar(cost), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text)
                Text("${Money.main} est.", fontSize = 11.sp, color = SheetTheme.secondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill("Add to shopping list", Icons.Filled.ShoppingCart, SheetTheme.navy, false) {
                val added = ShoppingActions.addPlan(weekItems.filter { !it.eaten }, store)
                addedNote = if (added == 0) "Already on your shopping list." else "Added $added to your shopping list."
            }
            FilterPill("Copy last week", Icons.Filled.ContentCopy, SheetTheme.navy, false) { copying = true }
        }
        addedNote?.let { note ->
            Text("$note Open it ›", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.navy,
                modifier = Modifier.padding(top = 8.dp).clickable(onClick = onShopping))
        }
    }
    if (copying) AppDialog(
        title = "Copy last week's plan?",
        message = "This week's plan is replaced with last week's meals.",
        icon = Icons.Filled.ContentCopy,
        confirmText = "Copy last week",
        onConfirm = {
            copying = false
            scope.launch { store.copyPlanWeek(weekStart.minusDays(7).toString(), weekStart.toString()) }
        },
        onDismiss = { copying = false },
    )
}

@Composable
private fun DayPlanCard(store: SheetStore, s: SheetSnapshot, date: LocalDate, open: (MealRequest) -> Unit) {
    var editing by remember { mutableStateOf<MealType?>(null) }
    val key = date.toString()
    val items = s.foodData.plan.filter { it.date == key }
    val t = NutritionTotals.ofPlan(items)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card)) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(date.format(DateTimeFormatter.ofPattern("EEEE d MMM")), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text)
            if (date == LocalDate.now()) { Spacer(Modifier.width(8.dp)); StatusPill("Today", SheetTheme.navy) }
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty()) Text("${SheetFormat.number(t.calories, 0)} kcal · ${SheetFormat.number(t.protein, 0)} g P", fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, color = if (t.protein >= (s.settings.proteinTarget ?: 120.0)) SheetTheme.good else SheetTheme.secondary)
        }
        MealType.entries.forEach { type ->
            val mealItems = items.filter { it.meal == type.label }
            RowDivider()
            Row(Modifier.fillMaxWidth().clickable { editing = type }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(mealIcon(type), null, tint = SheetTheme.good, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(type.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary, modifier = Modifier.width(70.dp))
                Text(if (mealItems.isEmpty()) "Plan…" else mealItems.joinToString { it.food }, fontSize = 15.sp, maxLines = 1,
                    color = if (mealItems.isEmpty()) SheetTheme.tertiary else SheetTheme.text, modifier = Modifier.weight(1f))
                if (mealItems.any { it.eaten }) Icon(Icons.Filled.CheckCircle, "Eaten", tint = SheetTheme.good, modifier = Modifier.size(18.dp))
            }
        }
    }
    editing?.let { type ->
        PlanEditorSheet(store, key, type, onDismiss = { editing = null }) { items: List<FoodItem> ->
            open(MealRequest(type = type, date = date, items = items, fromPlan = key to type))
        }
    }
}

/** Everything planned for the week, added up by food, with tick boxes; shareable as text. */
private fun LazyListScope.trends(s: SheetSnapshot) {
    item { TrendsContent(s) }
}

@Composable
private fun TrendsContent(s: SheetSnapshot) {
    var range by rememberSaveable { mutableStateOf(7) }
    val start = LocalDate.now().minusDays((range - 1).toLong())
    val log = s.foodData.log.filter { it.date >= start.toString() }
    val byDay = log.groupBy { it.date }
    val points = (0 until range).map { offset ->
        val d = start.plusDays(offset.toLong()).toString()
        NutritionTotals.ofEntries(byDay[d].orEmpty())
    }
    val logged = points.filter { it.calories > 0 }
    val avgKcal = if (logged.isEmpty()) 0.0 else logged.sumOf { it.calories } / logged.size
    val avgProtein = if (logged.isEmpty()) 0.0 else logged.sumOf { it.protein } / logged.size
    val proteinTarget = s.settings.proteinTarget ?: 120.0
    val hit = logged.count { it.protein >= proteinTarget }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill("7 days", null, SheetTheme.navy, range == 7) { range = 7 }
            FilterPill("30 days", null, SheetTheme.navy, range == 30) { range = 30 }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Icons.Filled.LocalFireDepartment, SheetTheme.warn, "Avg calories", SheetFormat.number(avgKcal, 0), Modifier.weight(1f),
                caption = "Target ${SheetFormat.number(s.settings.calorieTarget, 0)}")
            StatTile(Icons.Filled.MonitorHeart, SheetTheme.good, "Avg protein", "${SheetFormat.number(avgProtein, 0)} g", Modifier.weight(1f),
                caption = "$hit of ${logged.size} days hit ${SheetFormat.number(proteinTarget, 0)} g")
        }
        ChartCard("Protein per day", "Dashed line: your target") {
            if (logged.isEmpty()) EmptyChart("Log meals to see this.")
            else BarChart(points.map { it.protein }, proteinTarget, SheetTheme.good.copy(alpha = 0.5f), SheetTheme.good, SheetTheme.secondary)
        }
        ChartCard("Calories per day", "Red bars went over your target") {
            if (logged.isEmpty()) EmptyChart("Log meals to see this.")
            else BarChart(points.map { it.calories }, s.settings.calorieTarget, SheetTheme.warn, SheetTheme.bad, SheetTheme.secondary)
        }
        FoodMoney(s, log)
        TopFoods(log)
    }
}

@Composable
private fun FoodMoney(s: SheetSnapshot, log: List<com.mohammedsharukms.mypersonaltracker.sheet.FoodEntry>) {
    val month = SheetDate.today.take(7)
    val spent = s.expenses.filter { (it.date ?: "").startsWith(month) && it.category in setOf("Groceries", "Food & Dining") }
    val groceries = spent.filter { it.category == "Groceries" }.sumOf { it.qar ?: 0.0 }
    val eatingOut = spent.filter { it.category == "Food & Dining" }.sumOf { it.qar ?: 0.0 }
    val total = groceries + eatingOut
    val budget = s.settings.foodBudget ?: 0.0
    val meals = Meal.group(log)
    val paid = meals.mapNotNull { it.cost }.filter { it > 0 }
    val paidProtein = meals.filter { (it.cost ?: 0.0) > 0 }.sumOf { it.totals.protein }
    SheetCard {
        Text("Food money this month", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
        Spacer(Modifier.height(10.dp))
        if (budget > 0) {
            ProgressRow("Spent of ${SheetFormat.number(budget, 0)} ${Money.main} budget", SheetFormat.qar(total), total / budget,
                if (total > budget) SheetTheme.bad else SheetTheme.teal)
            Spacer(Modifier.height(8.dp))
        }
        KeyValueRow("Groceries", "${SheetFormat.qar(groceries)} ${Money.main}")
        KeyValueRow("Eating out", "${SheetFormat.qar(eatingOut)} ${Money.main}")
        if (paid.isNotEmpty()) {
            KeyValueRow("Average paid meal", "${SheetFormat.qar(paid.sum() / paid.size)} ${Money.main}")
            KeyValueRow("Protein per 10 ${Money.main} (paid meals)", "${SheetFormat.number(paidProtein / paid.sum() * 10, 1)} g")
        }
        Text("From Finance (Groceries + Food & Dining). Meals with a cost added to Finance count here automatically.",
            fontSize = 11.sp, color = SheetTheme.secondary)
    }
}

@Composable
private fun TopFoods(log: List<com.mohammedsharukms.mypersonaltracker.sheet.FoodEntry>) {
    if (log.isEmpty()) return
    val byFood = log.groupBy { it.food }
    SheetCard {
        Text("Your foods", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
        Text("Most eaten", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary, modifier = Modifier.padding(top = 8.dp))
        byFood.map { it.key to it.value.size }.sortedByDescending { it.second }.take(5).forEach { KeyValueRow(it.first, "${it.second}×") }
        Text("Biggest protein sources", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary, modifier = Modifier.padding(top = 8.dp))
        byFood.map { it.key to it.value.sumOf { e -> e.protein ?: 0.0 } }.sortedByDescending { it.second }.take(5)
            .forEach { KeyValueRow(it.first, "${SheetFormat.number(it.second, 0)} g", SheetTheme.good) }
    }
}

/** Small Dashboard card: today's calories and protein, opens the Food tab. */
@Composable
fun FoodTodayCard(snapshot: SheetSnapshot, onOpen: () -> Unit) {
    val t = NutritionTotals.ofEntries(snapshot.foodData.log.filter { it.date == SheetDate.today })
    val s = snapshot.settings
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card).clickable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Icons.Filled.Restaurant, SheetTheme.good, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Food today", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            ProgressRow("Calories", "${SheetFormat.number(t.calories, 0)} / ${SheetFormat.number(s.calorieTarget, 0)}", t.calories / maxOf(s.calorieTarget ?: 1.0, 1.0), SheetTheme.warn)
            ProgressRow("Protein", "${SheetFormat.number(t.protein, 0)} / ${SheetFormat.number(s.proteinTarget, 0)} g", t.protein / maxOf(s.proteinTarget ?: 1.0, 1.0), SheetTheme.good)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SheetTheme.tertiary)
    }
}
