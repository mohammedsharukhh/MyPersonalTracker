package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.container
import com.mohammedsharukms.mypersonaltracker.sheet.PeopleEntry
import com.mohammedsharukms.mypersonaltracker.sheet.PersonSummary
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

// Money with friends & family: lent, borrowed, got back, paid back and gifts —
// with a running balance per person. Mirrors People.swift.

private fun icon(kind: PeopleEntry.Kind): ImageVector = when (kind) {
    PeopleEntry.Kind.LENT, PeopleEntry.Kind.PAID_BACK -> Icons.Filled.CallMade
    PeopleEntry.Kind.GOT_BACK, PeopleEntry.Kind.BORROWED -> Icons.Filled.CallReceived
    PeopleEntry.Kind.GIFT_GIVEN, PeopleEntry.Kind.GIFT_GOT -> Icons.Filled.CardGiftcard
}

@Composable
private fun tint(kind: PeopleEntry.Kind): Color = when (kind) {
    PeopleEntry.Kind.LENT, PeopleEntry.Kind.PAID_BACK -> SheetTheme.warn
    PeopleEntry.Kind.GOT_BACK, PeopleEntry.Kind.BORROWED -> SheetTheme.sky
    PeopleEntry.Kind.GIFT_GIVEN -> SheetTheme.categoryColor("Gifts & Family")
    PeopleEntry.Kind.GIFT_GOT -> SheetTheme.good
}

/** The People section (Finance ▸ People): who owes whom, then each person. */
fun LazyListScope.peopleItems(store: SheetStore, snapshot: SheetSnapshot, onAdd: (PeopleEntry) -> Unit, onOpen: (String) -> Unit) {
    val people = PersonSummary.all(snapshot.people)
    item { PeopleHeader(people, onAdd) }
    people.forEach { p -> item(key = "person-${p.name.lowercase()}") { PersonRow(p) { onOpen(p.name) } } }
}

@Composable
private fun PeopleHeader(people: List<PersonSummary>, onAdd: (PeopleEntry) -> Unit) {
    val owed = people.map { it.balance }.filter { it > 0 }.sum()
    val owe = people.filter { it.balance < 0 }.sumOf { -it.balance }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Icons.Filled.CallReceived, SheetTheme.good, "Owed to you", SheetFormat.qar(owed), Modifier.weight(1f),
                caption = "${people.count { it.balance > 0 }} people · ${Money.main}")
            StatTile(Icons.Filled.CallMade, SheetTheme.bad, "You owe", SheetFormat.qar(owe), Modifier.weight(1f),
                caption = "${people.count { it.balance < 0 }} people · ${Money.main}")
        }
        PrimaryButton("Add money with someone") { onAdd(newEntry("", PeopleEntry.Kind.LENT, 0.0)) }
        if (people.isEmpty()) SheetCard {
            Text("Track money you lend, borrow and give to friends and family. Each person gets a running balance, and gifts you give count as spending in Finance.", color = SheetTheme.secondary)
        }
    }
}

@Composable
private fun PersonRow(p: PersonSummary, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(SheetTheme.indigo), contentAlignment = Alignment.Center) {
            Text(p.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            Text(when { p.balance > 0 -> "Owes you"; p.balance < 0 -> "You owe"; p.gifted > 0 -> "Gifts ${SheetFormat.qar(p.gifted)}"; else -> "Settled up" },
                fontSize = 12.sp, color = SheetTheme.secondary)
        }
        if (p.balance != 0.0) Text(SheetFormat.qar(abs(p.balance)), fontSize = 17.sp, fontWeight = FontWeight.Bold,
            color = if (p.balance > 0) SheetTheme.good else SheetTheme.bad)
        else Icon(Icons.Filled.CheckCircle, null, tint = SheetTheme.good)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SheetTheme.tertiary)
    }
}

private fun newEntry(person: String, kind: PeopleEntry.Kind, amount: Double) =
    PeopleEntry(UUID.randomUUID().toString(), LocalDate.now().toString(), person, kind, amount, null)

/** Hosts the add/edit and per-person sheets for a screen that shows peopleItems. */
class PeopleUi {
    var adding by mutableStateOf<Pair<PeopleEntry, Boolean>?>(null)
    var person by mutableStateOf<String?>(null)
}

@Composable
fun PeopleSheets(store: SheetStore, ui: PeopleUi) {
    ui.person?.let { name -> PersonDetailSheet(store, name, onDismiss = { ui.person = null }) { entry, isNew -> ui.adding = entry to isNew } }
    ui.adding?.let { (entry, isNew) -> PeopleEntrySheet(store, entry, isNew) { ui.adding = null } }
}

/** Full sheet from the Dashboard or More. */
@Composable
fun PeopleSheet(store: SheetStore, onDismiss: () -> Unit) {
    val ui = remember { PeopleUi() }
    EditorSheet("Friends & family", onDismiss, onSave = null) {
        SyncStatusBar(store)
        val snapshot = store.snapshot
        if (snapshot == null) NotConnectedHint() else {
            val people = PersonSummary.all(snapshot.people)
            PeopleHeader(people) { ui.adding = it to true }
            people.forEach { p -> PersonRow(p) { ui.person = p.name } }
        }
    }
    PeopleSheets(store, ui)
}

@Composable
private fun PersonDetailSheet(store: SheetStore, name: String, onDismiss: () -> Unit, onEdit: (PeopleEntry, Boolean) -> Unit) {
    val entries = store.snapshot?.people.orEmpty().filter { it.person.lowercase() == name.lowercase() }.sortedByDescending { it.date }
    val summary = PersonSummary(name, entries)
    EditorSheet(name, onDismiss, onSave = null) {
        SheetCard {
            Text(when { summary.balance > 0 -> "$name owes you"; summary.balance < 0 -> "You owe $name"; else -> "All settled" }, color = SheetTheme.secondary)
            Text("${SheetFormat.qar(abs(summary.balance))} ${Money.main}", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                color = when { summary.balance > 0 -> SheetTheme.good; summary.balance < 0 -> SheetTheme.bad; else -> SheetTheme.text })
            if (summary.gifted > 0) Text("Gifts given: ${SheetFormat.qar(summary.gifted)}", fontSize = 12.sp, color = SheetTheme.secondary)
        }
        if (summary.balance != 0.0) PrimaryButton(if (summary.balance > 0) "They paid me back" else "I paid them back") {
            onEdit(newEntry(name, if (summary.balance > 0) PeopleEntry.Kind.GOT_BACK else PeopleEntry.Kind.PAID_BACK, abs(summary.balance)), true)
        }
        Text("+ Add entry with $name", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy,
            modifier = Modifier.clickable { onEdit(newEntry(name, PeopleEntry.Kind.LENT, 0.0), true) }.padding(vertical = 6.dp))
        FormSection("History") {
            entries.forEachIndexed { i, e ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().clickable { onEdit(e, false) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon(e.type), null, tint = tint(e.type), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.type.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                            if (e.pending) { Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.Schedule, "Waiting to sync", tint = SheetTheme.sky, modifier = Modifier.size(12.dp)) }
                        }
                        Text(listOfNotNull(SheetDate.short(e.date), e.note).joinToString(" · "), fontSize = 12.sp, color = SheetTheme.secondary, maxLines = 1)
                    }
                    Text(SheetFormat.qar(e.amount), fontWeight = FontWeight.Bold, color = SheetTheme.text)
                }
            }
        }
    }
}

/** Add or edit one entry: who, what kind, how much, when. */
@Composable
fun PeopleEntrySheet(store: SheetStore, initial: PeopleEntry, isNew: Boolean, onDismiss: () -> Unit) {
    val book = LocalContext.current.container.currency
    var person by remember { mutableStateOf(initial.person) }
    var kind by remember { mutableStateOf(initial.type) }
    var amountText by remember { mutableStateOf(if (initial.amount > 0) SheetFormat.plain(initial.amount) else "") }
    var currency by remember { mutableStateOf(Money.main) }
    var date by remember { mutableStateOf(SheetDate.date(initial.date) ?: LocalDate.now()) }
    var note by remember { mutableStateOf(initial.note ?: "") }
    val method = store.snapshot?.lists?.methods?.firstOrNull() ?: "Cash"
    val known = PersonSummary.all(store.snapshot?.people.orEmpty()).map { it.name }
    val amount = SheetFormat.input(amountText)?.takeIf { it > 0 }?.let { book.convert(it, currency, Money.main) }
    val canSave = person.isNotBlank() && amount != null

    EditorSheet(if (isNew) "Money with someone" else "Edit entry", onDismiss, canSave = canSave, onSave = {
        store.savePeopleEntry(initial.copy(person = person.trim(), type = kind, amount = Math.round(amount!! * 100) / 100.0,
            date = date.toString(), note = note.trim().ifEmpty { null }), method)
        onDismiss()
    }) {
        Field("Who") {
            PlainField(person, "Name, e.g. Ammi, Rahul") { person = it }
            if (known.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                known.forEach { name -> FilterPill(name, null, SheetTheme.navy, person == name) { person = name } }
            }
        }
        FormSection("What happened") {
            PeopleEntry.Kind.entries.forEachIndexed { i, k ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().clickable { kind = k }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon(k), null, tint = tint(k), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(k.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                        Text(k.explanation, fontSize = 12.sp, color = SheetTheme.secondary)
                    }
                    if (kind == k) Icon(Icons.Filled.Check, "Selected", tint = SheetTheme.navy)
                }
            }
        }
        SheetCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Money.single) CurrencyPill(Money.main) else CurrencyButton(book, currency) { currency = it }
                Spacer(Modifier.width(12.dp))
                BasicTextField(amountText, { amountText = it }, singleLine = true,
                    textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text, textAlign = TextAlign.End),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), cursorBrush = SolidColor(SheetTheme.navy),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> Box(contentAlignment = Alignment.CenterEnd) { if (amountText.isEmpty()) Text("0.00", fontSize = 26.sp, color = SheetTheme.tertiary); inner() } })
            }
            if (currency != Money.main && amount != null) Text("Saved as ${SheetFormat.qar(amount)} ${Money.main}", fontSize = 12.sp, color = SheetTheme.navy)
            if (kind == PeopleEntry.Kind.GIFT_GIVEN) Text("Also added to Finance as Gifts & Family spending.", fontSize = 12.sp, color = SheetTheme.secondary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Day", color = SheetTheme.secondary, modifier = Modifier.weight(1f))
            FilterPill("Today", null, SheetTheme.navy, date == LocalDate.now()) { date = LocalDate.now() }
            Spacer(Modifier.width(8.dp))
            FilterPill("Yesterday", null, SheetTheme.navy, date == LocalDate.now().minusDays(1)) { date = LocalDate.now().minusDays(1) }
        }
        Field("Note (optional)") { PlainField(note, "e.g. for rent, Eid gift") { note = it } }
        if (!isNew) Text("Delete entry", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { store.deletePeopleEntry(initial.id); onDismiss() }.padding(vertical = 8.dp))
    }
}
