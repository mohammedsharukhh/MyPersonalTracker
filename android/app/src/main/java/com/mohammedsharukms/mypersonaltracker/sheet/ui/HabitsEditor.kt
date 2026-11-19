package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhonelinkErase
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SmokeFree
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.sheet.Habit
import com.mohammedsharukms.mypersonaltracker.sheet.InputSection
import com.mohammedsharukms.mypersonaltracker.sheet.MyInput
import com.mohammedsharukms.mypersonaltracker.sheet.inputData
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSettings
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.Tracker
import com.mohammedsharukms.mypersonaltracker.sheet.defaultIcon
import com.mohammedsharukms.mypersonaltracker.sheet.healthFillsWorkout
import com.mohammedsharukms.mypersonaltracker.sheet.iconKey
import com.mohammedsharukms.mypersonaltracker.sheet.isOn
import com.mohammedsharukms.mypersonaltracker.sheet.name

// Settings ▸ Your habits, and "Customize" on the day log: rename the five habits,
// pick their icons, turn habits and trackers off. Mirrors HabitsCustomize.swift.

/** Icons a habit can have — the same keys on iPhone. */
object HabitIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "office" to Icons.Filled.Business, "breakfast" to Icons.Filled.WbTwilight, "lunch" to Icons.Filled.Restaurant,
        "protein" to Icons.Filled.MonitorHeart, "workout" to Icons.AutoMirrored.Filled.DirectionsRun, "walk" to Icons.AutoMirrored.Filled.DirectionsWalk,
        "bike" to Icons.AutoMirrored.Filled.DirectionsBike, "swim" to Icons.Filled.Pool, "water" to Icons.Filled.WaterDrop, "sleep" to Icons.Filled.Bed,
        "meditate" to Icons.Filled.SelfImprovement, "pray" to Icons.Filled.VolunteerActivism, "book" to Icons.AutoMirrored.Filled.MenuBook,
        "study" to Icons.Filled.School, "journal" to Icons.Filled.EditNote, "language" to Icons.Filled.Translate, "code" to Icons.Filled.Code,
        "music" to Icons.Filled.MusicNote, "vitamins" to Icons.Filled.Medication, "fruit" to Icons.Filled.Spa, "no-phone" to Icons.Filled.PhonelinkErase,
        "no-sugar" to Icons.Filled.Block, "smoke-free" to Icons.Filled.SmokeFree, "clean" to Icons.Filled.CleaningServices,
        "family" to Icons.Filled.FamilyRestroom, "money" to Icons.Filled.Payments, "sun" to Icons.Filled.WbSunny, "heart" to Icons.Filled.Favorite,
        "star" to Icons.Filled.Star, "checklist" to Icons.Filled.Checklist,
    )

    fun icon(key: String?): ImageVector? = all.firstOrNull { it.first == key }?.second
}

/** A habit's icon, from your choice. */
fun habitIcon(habit: Habit, settings: SheetSettings?): ImageVector =
    settings?.let { HabitIcons.icon(habit.iconKey(it)) } ?: SheetTheme.habitIcon(habit)

@Composable
fun HabitsEditorSheet(store: SheetStore, onDismiss: () -> Unit) {
    val s = store.snapshot?.settings
    val names = remember { mutableStateListOf(*Habit.entries.map { h -> s?.let { h.name(it) } ?: h.title }.toTypedArray()) }
    val on = remember { mutableStateListOf(*Habit.entries.map { h -> s?.let { h.isOn(it) } ?: true }.toTypedArray()) }
    val icons = remember { mutableStateListOf(*Habit.entries.map { h -> s?.let { h.iconKey(it) } ?: h.defaultIcon }.toTypedArray()) }
    val trackers = remember { mutableStateMapOf(*Tracker.entries.map { t -> t to (s?.let { t.isOn(it) } ?: true) }.toTypedArray()) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<InputEditTarget?>(null) }
    val mine = (store.snapshot?.inputData?.inputs ?: emptyList()).sortedWith(compareBy({ it.section.ordinal }, { it.order ?: 0.0 }))
    val mineHabitsOn = store.snapshot?.inputData?.habitsOn ?: 0
    val outOf = on.count { it } + (if (trackers[Tracker.WATER] == true) 1 else 0) + (if (trackers[Tracker.SLEEP] == true) 1 else 0) + mineHabitsOn

    EditorSheet("Customize", onDismiss, canSave = outOf > 0, onSave = {
        store.saveHabits(Habit.entries.indices.map { Triple(names[it].trim(), on[it], icons[it]) }, trackers.toMap())
        onDismiss()
    }) {
        FormSection("Your habits", "Name them what you actually do — “Read 20 pages”, “No sugar”, “Pray Fajr”. Tap an icon to change it. " +
            "A habit that's off is hidden and doesn't count toward your score; what you logged before stays.") {
            Habit.entries.forEachIndexed { i, habit ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background((if (on[i]) SheetTheme.navy else SheetTheme.secondary).copy(alpha = 0.12f))
                        .clickable { picking = i }, contentAlignment = Alignment.Center) {
                        Icon(HabitIcons.icon(icons[i]) ?: SheetTheme.habitIcon(habit), "Icon for ${names[i]}",
                            tint = if (on[i]) SheetTheme.navy else SheetTheme.secondary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(names[i], { names[i] = it }, singleLine = true,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(SheetTheme.input).padding(horizontal = 10.dp, vertical = 9.dp),
                        textStyle = TextStyle(fontSize = 16.sp, color = if (on[i]) SheetTheme.text else SheetTheme.secondary),
                        cursorBrush = SolidColor(SheetTheme.navy))
                    Spacer(Modifier.width(8.dp))
                    Switch(on[i], { on[i] = it }, colors = SwitchDefaults.colors(checkedTrackColor = SheetTheme.navy))
                }
            }
        }
        FormSection("Added by you", "Anything you want to track — “Read 20 pages”, “Pages read”, “Cups of tea”. Your habits count toward the score " +
            "while they're on; health numbers are tracked, not scored. Changes here save straight away.") {
            mine.forEach { input ->
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { editing = InputEditTarget.Mine(input) }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background((if (input.on) SheetTheme.navy else SheetTheme.secondary).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center) {
                            Icon(HabitIcons.icon(input.icon) ?: Icons.Filled.Checklist, null, tint = if (input.on) SheetTheme.navy else SheetTheme.secondary, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(input.name, fontSize = 16.sp, color = if (input.on) SheetTheme.text else SheetTheme.secondary)
                            Text(if (input.section == InputSection.HABIT) "Habit" else listOfNotNull("Health", input.unit?.takeIf { it.isNotBlank() },
                                input.target?.let { "target ${SheetFormat.plain(it)}" }).joinToString(" · "), fontSize = 12.sp, color = SheetTheme.secondary)
                        }
                    }
                    Switch(input.on, { store.saveInput(input.copy(on = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = SheetTheme.navy))
                }
                RowDivider()
            }
            AddRow("Add a habit") { editing = InputEditTarget.Mine(MyInput.new(InputSection.HABIT, mine.size + 1)) }
            RowDivider()
            AddRow("Add to Health") { editing = InputEditTarget.Mine(MyInput.new(InputSection.HEALTH, mine.size + 1)) }
        }
        FormSection("Track", "Water and sleep count toward the score when they're on. Steps, weight and screen time are tracked but not scored.") {
            Tracker.entries.forEachIndexed { i, t ->
                if (i > 0) RowDivider()
                ToggleRow(t.title, trackers[t] == true) { trackers[t] = it }
            }
        }
        SheetCard {
            Row {
                Text("Daily Score is out of", color = SheetTheme.text, modifier = Modifier.weight(1f))
                Text("$outOf", fontWeight = FontWeight.Bold, color = SheetTheme.text)
            }
            val preview = (s ?: return@SheetCard).copy(habitNames = s.habitNames.toMutableList().also { it[4] = names[4] }, habitOn = s.habitOn.toMutableList().also { it[4] = if (on[4]) "Y" else "N" })
            if (healthFillsWorkout(preview)) {
                Text("“${names[4]}” can be ticked by Health Connect when you work out for 20 minutes or more.", fontSize = 12.sp, color = SheetTheme.secondary,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    editing?.let { target -> InputEditorSheet(store, target) { editing = null } }
    picking?.let { i -> IconPickerDialog(icons[i], onDismiss = { picking = null }) { icons[i] = it; picking = null } }
}

@Composable
private fun AddRow(title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.AddCircle, null, tint = SheetTheme.navy, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
    }
}

/** What the editor is editing: one of the five, or one of yours (new or not). */
sealed interface InputEditTarget {
    data class BuiltIn(val habit: Habit) : InputEditTarget
    data class Mine(val input: MyInput) : InputEditTarget
}

/** Rename a habit or input, change its icon, turn it off; for one of yours, its unit and target, or delete it. */
@Composable
fun InputEditorSheet(store: SheetStore, target: InputEditTarget, onDismiss: () -> Unit) {
    val s = store.snapshot?.settings
    val input = (target as? InputEditTarget.Mine)?.input
    val builtIn = (target as? InputEditTarget.BuiltIn)?.habit
    val isNew = input != null && !store.inputExists(input.id)
    val isHealth = input?.section == InputSection.HEALTH
    var name by remember { mutableStateOf(input?.name ?: builtIn?.let { h -> s?.let { h.name(it) } ?: h.title } ?: "") }
    var icon by remember { mutableStateOf(input?.icon ?: builtIn?.let { h -> s?.let { h.iconKey(it) } ?: h.defaultIcon } ?: "checklist") }
    var on by remember { mutableStateOf(input?.on ?: builtIn?.let { h -> s?.let { h.isOn(it) } } ?: true) }
    var unit by remember { mutableStateOf(input?.unit ?: "") }
    var goal by remember { mutableStateOf(input?.target?.let { SheetFormat.plain(it) } ?: "") }
    var step by remember { mutableStateOf(SheetFormat.plain(input?.stepSize ?: 1.0)) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val title = when {
        isNew -> if (isHealth) "Add to Health" else "Add a habit"
        isHealth -> "Edit input"
        else -> "Edit habit"
    }
    EditorSheet(title, onDismiss, saveLabel = if (isNew) "Add" else "Save", canSave = name.isNotBlank(), onSave = {
        val trimmed = name.trim()
        if (builtIn != null) store.saveHabit(builtIn, trimmed, on, icon)
        if (input != null) store.saveInput(input.copy(name = trimmed, icon = icon, on = on,
            unit = if (isHealth) unit.trim() else input.unit,
            target = if (isHealth) SheetFormat.input(goal) else input.target,
            step = if (isHealth) SheetFormat.input(step)?.takeIf { it > 0 } ?: 1.0 else input.step))
        onDismiss()
    }) {
        SheetCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(SheetTheme.navy).clickable { picking = true }, contentAlignment = Alignment.Center) {
                    Icon(HabitIcons.icon(icon) ?: Icons.Filled.Checklist, "Change icon", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                BasicTextField(name, { name = it }, singleLine = true,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(SheetTheme.input).padding(horizontal = 12.dp, vertical = 12.dp),
                    textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text),
                    cursorBrush = SolidColor(SheetTheme.navy),
                    decorationBox = { inner ->
                        if (name.isEmpty()) Text(if (isHealth) "e.g. Pages read" else "e.g. Read 20 pages", fontSize = 17.sp, color = SheetTheme.tertiary)
                        inner()
                    })
            }
            Text("Tap the icon to change it.", fontSize = 12.sp, color = SheetTheme.secondary, modifier = Modifier.padding(top = 8.dp))
        }
        if (isHealth) {
            Field("Unit") { PlainField(unit, "pages, min, cups…") { unit = it } }
            NumberField("Daily target (optional)", goal, unit) { goal = it }
            NumberField("Each + adds", step, unit) { step = it }
            Text("Tracked every day; not part of the Daily Score.", fontSize = 12.sp, color = SheetTheme.secondary)
        }
        FormSection("", if (on) (if (isHealth) "Turn off to hide it. What you logged stays."
            else "Counts toward your Daily Score while it's on. Turn off to hide it; what you logged stays.")
            else "Hidden and not counted. Turn on again any time.") {
            ToggleRow("Show on Daily", on) { on = it }
        }
        if (input != null && !isNew) {
            SheetCard(onClick = { confirmDelete = true }) { Text("Delete ${input.name}", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold) }
        }
    }
    if (confirmDelete && input != null) AppDialog(
        title = "Delete ${input.name}?",
        message = "Everything logged for it is deleted too. To keep it, turn it off instead.",
        style = DialogStyle.DESTRUCTIVE,
        confirmText = "Delete",
        onConfirm = { confirmDelete = false; store.deleteInput(input.id); onDismiss() },
        onDismiss = { confirmDelete = false },
    )
    if (picking) IconPickerDialog(icon, onDismiss = { picking = false }) { icon = it; picking = false }
}

/** The icon grid, shared by Customize and the editor. */
@Composable
fun IconPickerDialog(selected: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AppDialog(title = "Icon", onDismiss = onDismiss, onConfirm = onDismiss, confirmText = "Done", dismissText = null) {
        HabitIcons.all.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (key, icon) ->
                    val chosen = selected == key
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (chosen) SheetTheme.navy else SheetTheme.navy.copy(alpha = 0.1f))
                        .clickable { onPick(key) }, contentAlignment = Alignment.Center) {
                        Icon(icon, key, tint = if (chosen) Color.White else SheetTheme.navy, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}
