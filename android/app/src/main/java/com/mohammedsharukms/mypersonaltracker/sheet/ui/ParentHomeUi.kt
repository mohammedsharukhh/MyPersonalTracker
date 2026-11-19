package com.mohammedsharukms.mypersonaltracker.sheet.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.reminders.ReminderScheduler
import com.mohammedsharukms.mypersonaltracker.sheet.ParentHealthShare
import com.mohammedsharukms.mypersonaltracker.sheet.ParentMoney
import com.mohammedsharukms.mypersonaltracker.sheet.ParentReading
import com.mohammedsharukms.mypersonaltracker.sheet.ParentWho
import com.mohammedsharukms.mypersonaltracker.sheet.ReadingKind
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.TakeHomeItem
import com.mohammedsharukms.mypersonaltracker.sheet.readings
import com.mohammedsharukms.mypersonaltracker.sheet.takeHomeData
import java.time.LocalDate
import java.time.LocalTime

// Parents ▸ Trip (what to bring home, counting down to the trip), Parents ▸ Health
// (their readings, a chart, a summary for the doctor), and "this month for Mom & Dad".
// Mirrors ParentHomeViews.swift.

@Composable
fun ParentMoneyCard(money: ParentMoney, canSend: List<ParentWho> = emptyList(), onSend: (ParentWho) -> Unit = {}) {
    SheetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This month for Mom & Dad", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, modifier = Modifier.weight(1f))
            Text("${SheetFormat.qar(money.total)} ${Money.main}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text)
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column {
                Text("Sent home", fontSize = 12.sp, color = SheetTheme.secondary)
                Text("${SheetFormat.qar(money.sent)} ${Money.main}", fontSize = 14.sp, color = SheetTheme.text)
                if (money.sentHome > 0 && !Money.single) Text("≈ ${SheetFormat.inr(money.sentHome)} ${Money.home}", fontSize = 11.sp, color = SheetTheme.secondary)
            }
            Column {
                Text("Spent for them", fontSize = 12.sp, color = SheetTheme.secondary)
                Text("${SheetFormat.qar(money.spent)} ${Money.main}", fontSize = 14.sp, color = SheetTheme.text)
            }
        }
        if (canSend.isNotEmpty()) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            canSend.forEach { w -> PrimaryButton("Send to ${w.raw}", Modifier.weight(1f)) { onSend(w) } }
        }
        Text("Send money with ₹ next to Mom or Dad — it's logged as Remittance, so it counts as sent home.",
            fontSize = 11.sp, color = SheetTheme.secondary, modifier = Modifier.padding(top = 6.dp))
    }
}

// MARK: - Trip home

class TakeHomeUi {
    var newItem by mutableStateOf("")
    var editing by mutableStateOf<TakeHomeItem?>(null)
    var choosingTrip by mutableStateOf(false)
}

fun LazyListScope.takeHomeItems(store: SheetStore, s: SheetSnapshot, who: ParentWho?, ui: TakeHomeUi, onChanged: () -> Unit) {
    val data = s.takeHomeData
    val items = data.sorted.filter { who == null || it.who == who || it.who == ParentWho.BOTH }
    item {
        SheetCard(onClick = { ui.choosingTrip = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Flight, if ((data.daysToTrip() ?: 99) <= 7) SheetTheme.warn else SheetTheme.sky, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(data.countdown() ?: "When's your next trip home?", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                    Text(if (data.trip == null || data.daysToTrip() == null) "Tap to set the date for a countdown"
                        else "${SheetDate.short(data.trip)} · " + if (data.toGet == 0) (if (data.items.isEmpty()) "nothing on the list yet" else "everything's got ✓") else "${data.toGet} still to get",
                        fontSize = 12.sp, color = SheetTheme.secondary)
                }
            }
        }
    }
    item {
        SheetCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { PlainField(ui.newItem, "Add something to bring, e.g. dates and saffron") { ui.newItem = it } }
                if (ui.newItem.isNotBlank()) SmallTextButton("Add") {
                    store.saveTakeHomeItem(TakeHomeItem(item = ui.newItem.trim(), who = who ?: ParentWho.BOTH)); ui.newItem = ""; onChanged()
                }
            }
            items.forEach { it ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ store.saveTakeHomeItem(it.copy(got = !it.got)); onChanged() }) {
                        Icon(if (it.got) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, if (it.got) "Got it: ${it.item}" else "Not got yet: ${it.item}",
                            tint = if (it.got) SheetTheme.good else SheetTheme.navy)
                    }
                    Column(Modifier.weight(1f).clickable { ui.editing = it }.padding(vertical = 6.dp)) {
                        Text(it.item, fontSize = 15.sp, color = SheetTheme.text, textDecoration = if (it.got) TextDecoration.LineThrough else null)
                        Text(listOfNotNull(it.who.label, it.notes).joinToString(" · "), fontSize = 12.sp, color = SheetTheme.secondary)
                    }
                }
            }
        }
    }
    item {
        Text("The daily Mom & Dad notification reminds you two weeks, a week, 3 days and a day before the trip if there's still something to get.",
            fontSize = 12.sp, color = SheetTheme.secondary)
    }
}

@Composable
fun TakeHomeItemEditorSheet(store: SheetStore, initial: TakeHomeItem, onDismiss: () -> Unit) {
    var item by remember { mutableStateOf(initial.item) }
    var who by remember { mutableStateOf(initial.who) }
    var got by remember { mutableStateOf(initial.got) }
    var notes by remember { mutableStateOf(initial.notes ?: "") }
    EditorSheet(initial.item.ifBlank { "To take home" }, onDismiss, canSave = item.isNotBlank(), onSave = {
        store.saveTakeHomeItem(initial.copy(item = item.trim(), who = who, got = got, notes = notes.trim().ifEmpty { null })); onDismiss()
    }) {
        Field("What to bring") { PlainField(item, "e.g. Dates and saffron") { item = it } }
        Field("For") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ParentWho.entries.forEach { w -> FilterPill(w.label, null, SheetTheme.navy, who == w) { who = w } } } }
        SheetCard(padding = 4.dp) { ToggleRow("Got it", got) { got = it } }
        Field("Notes (optional)") { PlainField(notes, "Brand, size, where to buy…", singleLine = false) { notes = it } }
        SheetCard(onClick = { store.deleteTakeHomeItem(initial.id); onDismiss() }) { Text("Delete", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun TripDateDialog(store: SheetStore, current: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(current?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now().plusDays(30)) }
    AppDialog(
        title = "Next trip home",
        icon = Icons.Filled.Flight,
        confirmText = "Save",
        dismissText = if (current != null) "No trip planned" else "Cancel",
        onDismissButton = if (current != null) ({ store.saveTakeHomeTrip(null); ReminderScheduler.reschedule(context); onDismiss() }) else null,
        onConfirm = { store.saveTakeHomeTrip(date.toString()); ReminderScheduler.reschedule(context); onDismiss() },
        onDismiss = onDismiss,
    ) {
        DateField("Trip home", date, allowClear = false) { if (it != null) date = it }
    }
}

// MARK: - Health

class ParentHealthUi {
    var kind by mutableStateOf(ReadingKind.BLOOD_PRESSURE)
    var editing by mutableStateOf<Pair<ParentReading, Boolean>?>(null)
}

fun LazyListScope.parentHealthItems(s: SheetSnapshot, who: ParentWho, ui: ParentHealthUi) {
    val list = s.readings(ui.kind, who)
    item { SectionTitle("${who.raw}'s health", "As they tell you, to see the trend and share with their doctor. It doesn't judge the numbers — ask the doctor what's right for them.") }
    item {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadingKind.entries.forEach { k -> FilterPill(k.short, null, SheetTheme.navy, ui.kind == k) { ui.kind = k } }
        }
    }
    item {
        SheetCard {
            val recent = list.takeLast(30)
            if (recent.size >= 2) ReadingChart(recent.map { it.value }, if (ui.kind.hasSecond) recent.map { it.value2 } else null,
                "${ui.kind.label} over time: " + recent.joinToString(", ") { "${SheetDate.weekdayDay(it.date)} ${it.display}" })
            else Text(if (list.isEmpty()) "No ${ui.kind.short.lowercase()} readings for ${who.raw} yet. Add one when they tell you — after two you'll see the trend here."
                else "Add one more reading to see the trend.", fontSize = 14.sp, color = SheetTheme.secondary)
            SmallTextButton("+ Add ${who.raw}'s ${ui.kind.short.lowercase()}") { ui.editing = ParentReading(who = who, kind = ui.kind) to true }
        }
    }
    if (list.isNotEmpty()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Readings", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary, modifier = Modifier.weight(1f))
                ShareForDoctor(s, who)
            }
        }
        item {
            SheetCard(padding = 4.dp) {
                list.reversed().take(40).forEach { r ->
                    Column(Modifier.fillMaxWidth().clickable { ui.editing = r to false }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(r.display, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                        Text(listOfNotNull(SheetDate.weekdayDay(r.date), r.time, r.note).joinToString(" · "), fontSize = 12.sp, color = SheetTheme.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareForDoctor(s: SheetSnapshot, who: ParentWho) {
    val context = LocalContext.current
    Row(Modifier.clickable {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ParentHealthShare.text(s, who))
        runCatching { context.startActivity(Intent.createChooser(send, "Share for the doctor").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Share, null, tint = SheetTheme.navy, modifier = Modifier.size(16.dp))
        Text("Share for the doctor", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.navy, modifier = Modifier.padding(start = 4.dp))
    }
}

/** One or two lines (blood pressure's top and bottom), scaled to the readings. */
@Composable
private fun ReadingChart(first: List<Double>, second: List<Double?>?, description: String) {
    val top = SheetTheme.bad
    val bottom = SheetTheme.sky
    val grid = SheetTheme.tertiary
    val all = first + (second?.filterNotNull() ?: emptyList())
    val lo = (all.minOrNull() ?: 0.0).let { it - (all.maxOrNull()!! - it).coerceAtLeast(10.0) * 0.15 }
    val hi = (all.maxOrNull() ?: 1.0).let { it + (it - lo).coerceAtLeast(10.0) * 0.15 }
    Canvas(Modifier.fillMaxWidth().height(170.dp).semantics { contentDescription = description }) {
        val left = 34.dp.toPx()
        val w = size.width - left
        val h = size.height - 8.dp.toPx()
        fun y(v: Double) = (h - ((v - lo) / (hi - lo)) * h).toFloat() + 4.dp.toPx()
        listOf(lo, (lo + hi) / 2, hi).forEach { v ->
            drawLine(grid.copy(alpha = 0.3f), Offset(left, y(v)), Offset(size.width, y(v)), 1f)
            drawContext.canvas.nativeCanvas.drawText(SheetFormat.plain(v, 0), 0f, y(v) + 4.dp.toPx(),
                android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 10.sp.toPx(); isAntiAlias = true })
        }
        val step = if (first.size > 1) w / (first.size - 1) else 0f
        fun line(values: List<Double?>, color: Color) {
            val points = values.mapIndexedNotNull { i, v -> v?.let { Offset(left + i * step, y(it)) } }
            points.zipWithNext { a, b -> drawLine(color, a, b, 3.dp.toPx()) }
            points.forEach { drawCircle(color, 4.dp.toPx(), it) }
        }
        line(first, top)
        second?.let { line(it, bottom) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentReadingEditorSheet(store: SheetStore, initial: ParentReading, isNew: Boolean, onDismiss: () -> Unit) {
    var who by remember { mutableStateOf(initial.who) }
    var kind by remember { mutableStateOf(initial.kind) }
    var value by remember { mutableStateOf(if (isNew) "" else SheetFormat.plain(initial.value, 1)) }
    var value2 by remember { mutableStateOf(initial.value2?.let { SheetFormat.plain(it, 1) } ?: "") }
    var day by remember { mutableStateOf(runCatching { LocalDate.parse(initial.date) }.getOrDefault(LocalDate.now())) }
    var time by remember { mutableStateOf(initial.time ?: if (isNew) "%02d:%02d".format(LocalTime.now().hour, LocalTime.now().minute) else null) }
    var note by remember { mutableStateOf(initial.note ?: "") }
    var pickingTime by remember { mutableStateOf(false) }
    val ok = (SheetFormat.input(value) ?: 0.0) > 0 && (!kind.hasSecond || (SheetFormat.input(value2) ?: 0.0) > 0)
    EditorSheet(if (isNew) "New reading" else kind.label, onDismiss, canSave = ok, onSave = {
        store.saveParentReading(initial.copy(who = who, kind = kind, value = SheetFormat.input(value) ?: 0.0,
            value2 = if (kind.hasSecond) SheetFormat.input(value2) else null, date = day.toString(), time = time, note = note.trim().ifEmpty { null }))
        onDismiss()
    }) {
        Field("Whose") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(ParentWho.MOM, ParentWho.DAD).forEach { w -> FilterPill(w.raw, null, SheetTheme.navy, who == w) { who = w } } } }
        Field("Reading") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingKind.entries.forEach { k -> FilterPill(k.short, null, SheetTheme.navy, kind == k) { kind = k } }
            }
        }
        NumberField(if (kind.hasSecond) "Top (systolic)" else kind.label, value, kind.unit) { value = it }
        if (kind.hasSecond) NumberField("Bottom (diastolic)", value2, kind.unit) { value2 = it }
        DateField("Day", day, allowClear = false) { if (it != null) day = it }
        Row(Modifier.fillMaxWidth().clickable { pickingTime = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Time", fontSize = 15.sp, color = SheetTheme.text, modifier = Modifier.weight(1f))
            Text(time?.let { com.mohammedsharukms.mypersonaltracker.sheet.ParentAsk.timeText(it) } ?: "Any time", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
            if (time != null) Text("Clear", fontSize = 13.sp, color = SheetTheme.bad, modifier = Modifier.padding(start = 10.dp).clickable { time = null })
        }
        Field("Note (optional)") { PlainField(note, "After a walk, missed a tablet…", singleLine = false) { note = it } }
        if (!isNew) SheetCard(onClick = { store.deleteParentReading(initial.id); onDismiss() }) { Text("Delete", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold) }
    }
    if (pickingTime) {
        val start = time?.let { runCatching { LocalTime.parse(it.padStart(5, '0')) }.getOrNull() } ?: LocalTime.of(8, 0)
        val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute)
        AppDialog(title = "Time", confirmText = "Set time", onConfirm = { time = "%02d:%02d".format(state.hour, state.minute); pickingTime = false },
            onDismiss = { pickingTime = false }) { androidx.compose.material3.TimeInput(state) }
    }
}
