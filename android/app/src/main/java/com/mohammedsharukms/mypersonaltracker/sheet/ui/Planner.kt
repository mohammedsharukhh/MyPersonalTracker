package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.sheet.Clothing
import com.mohammedsharukms.mypersonaltracker.sheet.ClothingType
import com.mohammedsharukms.mypersonaltracker.sheet.DayPlan
import com.mohammedsharukms.mypersonaltracker.sheet.Outfit
import com.mohammedsharukms.mypersonaltracker.sheet.PlannerData
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.clothing
import com.mohammedsharukms.mypersonaltracker.sheet.lastWorn
import com.mohammedsharukms.mypersonaltracker.sheet.nextPlanned
import com.mohammedsharukms.mypersonaltracker.sheet.outfitOn
import com.mohammedsharukms.mypersonaltracker.sheet.pieces
import com.mohammedsharukms.mypersonaltracker.sheet.plannerData
import com.mohammedsharukms.mypersonaltracker.sheet.plansOn
import com.mohammedsharukms.mypersonaltracker.sheet.recentRepeats
import com.mohammedsharukms.mypersonaltracker.sheet.timesWorn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// Daily ▸ Planner: each day of the week with its plans and what you'll wear.
// Daily ▸ Wardrobe: the clothes outfits are picked from. Mirrors PlannerView.swift.

/** Which planner sheet is open; held by the screen so the content can live in a LazyColumn. */
class PlannerUi {
    var weekStart by mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    var editingPlan by mutableStateOf<Pair<DayPlan, Boolean>?>(null)
    var outfitDate by mutableStateOf<String?>(null)
    var editingClothing by mutableStateOf<Pair<Clothing, Boolean>?>(null)
}

private fun timeText(t: String?): String? = t?.let {
    runCatching { LocalTime.parse(it.padStart(5, '0')).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) }.getOrDefault(it)
}

fun LazyListScope.plannerItems(store: SheetStore, snapshot: SheetSnapshot, ui: PlannerUi) {
    val planner = snapshot.plannerData
    val monday = ui.weekStart
    val days = (0L until 7L).map { monday.plusDays(it).toString() }
    val thisWeek = monday == LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ ui.weekStart = monday.minusWeeks(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous week", tint = SheetTheme.navy) }
            Column(Modifier.weight(1f).clickable { ui.weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (thisWeek) "This week" else "Week of ${monday.format(DateTimeFormatter.ofPattern("d MMM"))}",
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
                Text("${SheetDate.short(days.first())} – ${SheetDate.short(days.last())}", fontSize = 12.sp, color = SheetTheme.secondary)
            }
            IconButton({ ui.weekStart = monday.plusWeeks(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next week", tint = SheetTheme.navy) }
        }
    }
    if (days.none { planner.plansOn(it).isNotEmpty() || planner.outfitOn(it) != null }) item {
        SheetCard {
            Text("Plan your week", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            Text("Add what's on each day — a dinner, the gym, a trip on the weekend — and pick what to wear. Add your clothes in Wardrobe once and outfits are a few taps.",
                fontSize = 14.sp, color = SheetTheme.secondary)
        }
    }
    items(days, key = { "day-$it" }) { date ->
        DayCard(date, planner,
            onAdd = { ui.editingPlan = DayPlan(date = date) to true },
            onOpen = { ui.editingPlan = it to false },
            onToggle = { store.savePlan(it.copy(done = !it.done)) },
            onOutfit = { ui.outfitDate = date })
    }
}

@Composable
private fun DayCard(date: String, planner: PlannerData, onAdd: () -> Unit, onOpen: (DayPlan) -> Unit, onToggle: (DayPlan) -> Unit, onOutfit: () -> Unit) {
    val day = LocalDate.parse(date)
    val today = LocalDate.now()
    val plans = planner.plansOn(date)
    val outfit = planner.outfitOn(date)
    val past = day.isBefore(today)
    SheetCard(Modifier.alpha(if (past) 0.7f else 1f), padding = 0.dp) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(day.format(DateTimeFormatter.ofPattern("EEEE, d MMM")), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            Spacer(Modifier.width(8.dp))
            when {
                day == today -> StatusPill("Today", SheetTheme.navy)
                day == today.plusDays(1) -> StatusPill("Tomorrow", SheetTheme.teal)
                day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY -> StatusPill("Weekend", SheetTheme.purple)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onAdd) { Icon(Icons.Filled.AddCircle, "Add a plan", tint = SheetTheme.navy) }
        }
        if (plans.isEmpty()) {
            Text(if (past) "Nothing planned" else "Nothing planned yet — add something", fontSize = 14.sp, color = SheetTheme.tertiary,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(horizontal = 16.dp, vertical = 6.dp))
        }
        plans.forEach { plan ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(plan) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                Icon(if (plan.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, if (plan.done) "Done" else "Mark done",
                    tint = if (plan.done) SheetTheme.good else SheetTheme.tertiary, modifier = Modifier.size(24.dp).clickable { onToggle(plan) })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(plan.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = if (plan.done) SheetTheme.secondary else SheetTheme.text,
                        textDecoration = if (plan.done) TextDecoration.LineThrough else null)
                    val detail = listOfNotNull(plan.place?.let { "📍 $it" }, plan.notes).joinToString(" · ")
                    if (detail.isNotEmpty()) Text(detail, fontSize = 12.sp, color = SheetTheme.secondary, maxLines = 2)
                }
                Text(timeText(plan.time) ?: "Any time", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (plan.time == null) SheetTheme.secondary else SheetTheme.navy)
            }
        }
        HorizontalDivider(Modifier.padding(start = 16.dp, top = 4.dp), color = SheetTheme.input)
        Row(Modifier.fillMaxWidth().clickable(onClick = onOutfit).padding(16.dp), verticalAlignment = Alignment.Top) {
            Text(outfit?.items?.firstOrNull()?.let { planner.clothing(it)?.type?.emoji } ?: "👔", fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (outfit == null) {
                    Text("What will you wear?", fontSize = 15.sp, color = SheetTheme.navy)
                } else {
                    val pieces = planner.pieces(outfit)
                    Text("Wear", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
                    Text(if (pieces.isEmpty()) outfit.note ?: "Outfit" else pieces.joinToString(" + "), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = SheetTheme.text)
                    if (pieces.isNotEmpty()) outfit.note?.let { Text(it, fontSize = 12.sp, color = SheetTheme.secondary) }
                    planner.recentRepeats(outfit).forEach { (piece, gap) ->
                        Text("↻ ${piece.label} worn ${if (gap == 1) "the day before" else "$gap days before"}", fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold, color = SheetTheme.warn)
                    }
                }
            }
            Text("›", fontSize = 18.sp, color = SheetTheme.tertiary)
        }
    }
}

fun LazyListScope.wardrobeItems(snapshot: SheetSnapshot, ui: PlannerUi) {
    val planner = snapshot.plannerData
    item {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) { SectionTitle("Wardrobe", if (planner.wardrobe.isEmpty()) null else "${planner.wardrobe.size} pieces") }
            Row(Modifier.clickable { ui.editingClothing = Clothing() to true }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AddCircle, null, tint = SheetTheme.navy)
                Spacer(Modifier.width(4.dp))
                Text("Add clothes", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = SheetTheme.navy)
            }
        }
    }
    if (planner.wardrobe.isEmpty()) item {
        SheetCard {
            Text("Add the clothes you have — shirts, trousers, shoes, a kurta for occasions. Then picking a day's outfit in Planner is a few taps, and you'll see when you last wore each piece.",
                fontSize = 14.sp, color = SheetTheme.secondary)
        }
    }
    // Not worn for a month (or never), and nothing planned for them.
    val tomorrow = LocalDate.now().plusDays(1).toString()
    val idle = planner.wardrobe.filter { piece ->
        if (planner.nextPlanned(piece.id) != null) return@filter false
        val last = planner.lastWorn(piece.id, tomorrow) ?: return@filter true
        ChronoUnit.DAYS.between(LocalDate.parse(last), LocalDate.now()) >= 30
    }
    if (planner.outfits.isNotEmpty() && idle.isNotEmpty() && idle.size < planner.wardrobe.size) item {
        SheetCard {
            Text("Not worn in a month", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            Text(idle.take(6).joinToString(", ") { it.label } + if (idle.size > 6) " and ${idle.size - 6} more" else "", fontSize = 12.sp, color = SheetTheme.secondary)
        }
    }
    ClothingType.entries.forEach { type ->
        val pieces = planner.wardrobe.filter { it.type == type }.sortedBy { it.label }
        if (pieces.isNotEmpty()) item(key = "type-${type.name}") {
            SheetCard(padding = 0.dp) {
                Text("${type.emoji}  ${type.label} · ${pieces.size}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                pieces.forEach { piece ->
                    Row(Modifier.fillMaxWidth().clickable { ui.editingClothing = piece to false }.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(piece.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = SheetTheme.text)
                            Text(wornSummary(planner, piece), fontSize = 12.sp, color = SheetTheme.secondary)
                        }
                        Text("›", fontSize = 18.sp, color = SheetTheme.tertiary)
                    }
                }
                Spacer(Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

/** "Worn 3× · last 2 Oct · next Sat" — up to today, then what's planned. */
private fun wornSummary(planner: PlannerData, piece: Clothing): String {
    val today = LocalDate.now().toString()
    val worn = planner.timesWorn(piece.id)
    val parts = mutableListOf(if (worn == 0) "Not worn yet" else "Worn $worn×")
    planner.lastWorn(piece.id, LocalDate.now().plusDays(1).toString())?.let { parts += if (it == today) "today" else "last ${SheetDate.short(it)}" }
    planner.nextPlanned(piece.id)?.takeIf { it != today }?.let { parts += "next " + LocalDate.parse(it).format(DateTimeFormatter.ofPattern("EEE d")) }
    return parts.joinToString(" · ")
}

/** The planner's editors. Put next to the screen's other sheets. */
@Composable
fun PlannerSheets(store: SheetStore, ui: PlannerUi) {
    ui.editingPlan?.let { (plan, isNew) -> PlanEditorSheet(store, plan, isNew) { ui.editingPlan = null } }
    ui.outfitDate?.let { date -> OutfitPickerSheet(store, date) { ui.outfitDate = null } }
    ui.editingClothing?.let { (item, isNew) -> ClothingEditorSheet(store, item, isNew) { ui.editingClothing = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanEditorSheet(store: SheetStore, initial: DayPlan, isNew: Boolean, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var date by remember { mutableStateOf(LocalDate.parse(initial.date)) }
    var time by remember { mutableStateOf(initial.time) }
    var place by remember { mutableStateOf(initial.place ?: "") }
    var notes by remember { mutableStateOf(initial.notes ?: "") }
    var done by remember { mutableStateOf(initial.done) }
    var pickingTime by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    if (confirmRemove) AppDialog(
        title = "Delete this plan?",
        message = initial.title,
        style = DialogStyle.DESTRUCTIVE,
        confirmText = "Delete",
        onConfirm = { confirmRemove = false; store.deletePlan(initial.id); onDismiss() },
        onDismiss = { confirmRemove = false },
    )
    EditorSheet(if (isNew) "New plan" else "Plan", onDismiss, canSave = title.isNotBlank(), onSave = {
        store.savePlan(initial.copy(title = title.trim(), date = date.toString(), time = time, place = place.trim().ifEmpty { null },
            notes = notes.trim().ifEmpty { null }, done = done))
        onDismiss()
    }) {
        Field("What's the plan?") { PlainField(title, "e.g. Dinner with Arun") { title = it } }
        DateField("Day", date, allowClear = false) { if (it != null) date = it }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SheetTheme.card).clickable { pickingTime = true }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Time", fontSize = 15.sp, color = SheetTheme.text, modifier = Modifier.weight(1f))
            Text(timeText(time) ?: "Any time", fontWeight = FontWeight.SemiBold, color = if (time == null) SheetTheme.secondary else SheetTheme.navy)
            if (time != null) {
                Spacer(Modifier.width(10.dp))
                Text("Clear", fontSize = 13.sp, color = SheetTheme.bad, modifier = Modifier.clickable { time = null })
            }
        }
        Field("Where (optional)") { PlainField(place, "e.g. City Centre") { place = it } }
        Field("Notes (optional)") { PlainField(notes, "Anything to remember", singleLine = false) { notes = it } }
        if (!isNew) {
            SheetCard(onClick = { done = !done }) { Text(if (done) "✓ Done — tap to undo" else "Mark as done", color = SheetTheme.navy, fontWeight = FontWeight.SemiBold) }
            SheetCard(onClick = { confirmRemove = true }) { Text("Delete plan", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold) }
        }
    }
    if (pickingTime) {
        val start = time?.let { runCatching { LocalTime.parse(it.padStart(5, '0')) }.getOrNull() } ?: LocalTime.of(18, 0)
        val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute)
        AppDialog(
            title = "Time",
            confirmText = "Set time",
            onConfirm = { time = "%02d:%02d".format(state.hour, state.minute); pickingTime = false },
            onDismiss = { pickingTime = false },
        ) { androidx.compose.material3.TimeInput(state) }
    }
}

/** Picks a day's outfit from the wardrobe, with when each piece was last worn. */
@Composable
private fun OutfitPickerSheet(store: SheetStore, date: String, onDismiss: () -> Unit) {
    val planner = store.snapshot?.plannerData ?: PlannerData()
    val existing = planner.outfitOn(date)
    val selected = remember { mutableStateListOf<String>().apply { addAll(existing?.items.orEmpty()) } }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var newName by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf(ClothingType.TOP) }
    val day = LocalDate.parse(date)
    EditorSheet("What to wear", onDismiss, canSave = selected.isNotEmpty() || note.isNotBlank(), onSave = {
        store.saveOutfit(Outfit(date, selected.toList(), note.trim().ifEmpty { null }))
        onDismiss()
    }) {
        Text("Wear on ${day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
        SheetCard {
            Text(if (selected.isEmpty()) "Tap clothes below to put the outfit together." else selected.mapNotNull { planner.clothing(it)?.label }.joinToString(" + "),
                fontSize = 15.sp, fontWeight = if (selected.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
                color = if (selected.isEmpty()) SheetTheme.secondary else SheetTheme.text)
        }
        Field("Note (optional)") { PlainField(note, "e.g. iron the shirt") { note = it } }
        val last = planner.outfits.filter { it.date < date && it.items.isNotEmpty() }.maxByOrNull { it.date }
        if (last != null && selected.isEmpty()) {
            SheetCard(onClick = { selected.addAll(last.items.filter { planner.clothing(it) != null }) }) {
                Text("↺ Same as ${SheetDate.short(last.date)}: ${planner.pieces(last).joinToString(" + ")}", color = SheetTheme.navy, fontSize = 14.sp)
            }
        }
        ClothingType.entries.forEach { type ->
            val pieces = planner.wardrobe.filter { it.type == type }.sortedBy { it.label }
            if (pieces.isNotEmpty()) FormSection("${type.emoji} ${type.label}") {
                pieces.forEachIndexed { i, piece ->
                    if (i > 0) RowDivider()
                    val on = piece.id in selected
                    Row(Modifier.fillMaxWidth().clickable { if (on) selected.remove(piece.id) else selected.add(piece.id) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(piece.label, color = SheetTheme.text)
                            val lastDay = planner.lastWorn(piece.id, date)
                            val gap = lastDay?.let { ChronoUnit.DAYS.between(LocalDate.parse(it), day) }
                            Text(when {
                                gap == null -> "Not worn yet"
                                gap <= 1 -> "Worn the day before"
                                else -> "Last worn $gap days before · ${SheetDate.short(lastDay)}"
                            }, fontSize = 12.sp, color = SheetTheme.secondary)
                        }
                        Icon(if (on) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                            tint = if (on) SheetTheme.navy else SheetTheme.tertiary)
                    }
                }
            }
        }
        FormSection(if (planner.wardrobe.isEmpty()) "Add your first clothes" else "Not in your wardrobe yet?",
            footer = "It's added to your Wardrobe too, for next time.") {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlainField(newName, "e.g. White linen shirt") { newName = it }
                TypePills(newType) { newType = it }
                PrimaryButton("Add and wear it", Modifier.fillMaxWidth(), enabled = newName.isNotBlank()) {
                    val item = Clothing(name = newName.trim(), type = newType)
                    store.saveClothing(item)
                    selected.add(item.id)
                    newName = ""
                }
            }
        }
        if (existing != null) {
            SheetCard(onClick = { store.deleteOutfit(Outfit.idFor(date)); onDismiss() }) { Text("Clear outfit", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun TypePills(selected: ClothingType, onSelect: (ClothingType) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClothingType.entries.forEach { t -> FilterPill("${t.emoji} ${t.label}", null, SheetTheme.navy, selected == t) { onSelect(t) } }
    }
}

@Composable
private fun ClothingEditorSheet(store: SheetStore, initial: Clothing, isNew: Boolean, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var type by remember { mutableStateOf(initial.type) }
    var colour by remember { mutableStateOf(initial.colour ?: "") }
    var notes by remember { mutableStateOf(initial.notes ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    EditorSheet(if (isNew) "Add clothes" else "Edit", onDismiss, canSave = name.isNotBlank(), onSave = {
        store.saveClothing(initial.copy(name = name.trim(), type = type, colour = colour.trim().ifEmpty { null }, notes = notes.trim().ifEmpty { null }))
        onDismiss()
    }) {
        Field("Item") { PlainField(name, "e.g. Oxford shirt, chinos, white sneakers") { name = it } }
        Field("Type") { TypePills(type) { type = it } }
        Field("Colour (optional)") { PlainField(colour, "e.g. Navy") { colour = it } }
        Field("Notes (optional)") { PlainField(notes, "e.g. dry clean only", singleLine = false) { notes = it } }
        if (!isNew) {
            SheetCard(onClick = { confirmDelete = true }) { Text("Remove from wardrobe", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold) }
            Text("Past outfits keep it.", fontSize = 12.sp, color = SheetTheme.secondary)
        }
    }
    if (confirmDelete) AppDialog(
        title = "Remove ${initial.label}?",
        message = "Past outfits keep it.",
        style = DialogStyle.DESTRUCTIVE,
        confirmText = "Remove",
        onConfirm = { confirmDelete = false; store.deleteClothing(initial.id); onDismiss() },
        onDismiss = { confirmDelete = false },
    )
}

/** Today's plans and outfit, on the Dashboard. */
@Composable
fun PlannerTodayCard(snapshot: SheetSnapshot, onOpen: () -> Unit) {
    val planner = snapshot.plannerData
    val today = LocalDate.now().toString()
    val plans = planner.plansOn(today)
    val outfit = planner.outfitOn(today)
    if (plans.isEmpty() && outfit == null) return
    SheetCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(Icons.Filled.CalendarMonth, SheetTheme.purple, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Today's plan", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                plans.take(3).forEach { plan ->
                    Row {
                        Text(timeText(plan.time) ?: "Any time", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.navy,
                            modifier = Modifier.width(64.dp))
                        Text(plan.title, fontSize = 14.sp, maxLines = 1, color = if (plan.done) SheetTheme.secondary else SheetTheme.text,
                            textDecoration = if (plan.done) TextDecoration.LineThrough else null)
                    }
                }
                if (plans.size > 3) Text("+${plans.size - 3} more", fontSize = 12.sp, color = SheetTheme.secondary)
                outfit?.let {
                    Text("👕 ${planner.pieces(it).joinToString(" + ")}${it.note?.let { n -> " · $n" } ?: ""}", fontSize = 12.sp,
                        fontWeight = FontWeight.Medium, color = SheetTheme.secondary, maxLines = 2)
                }
            }
            Text("›", fontSize = 18.sp, color = SheetTheme.tertiary)
        }
    }
}
