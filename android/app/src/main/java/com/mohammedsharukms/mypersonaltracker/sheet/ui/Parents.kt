package com.mohammedsharukms.mypersonaltracker.sheet.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mohammedsharukms.mypersonaltracker.reminders.ReminderScheduler
import com.mohammedsharukms.mypersonaltracker.sheet.MealPhotos
import com.mohammedsharukms.mypersonaltracker.sheet.ParentActions
import com.mohammedsharukms.mypersonaltracker.sheet.ParentAsk
import com.mohammedsharukms.mypersonaltracker.sheet.ParentDate
import com.mohammedsharukms.mypersonaltracker.sheet.ParentMedicine
import com.mohammedsharukms.mypersonaltracker.sheet.TakeHomeItem
import com.mohammedsharukms.mypersonaltracker.sheet.parentMoney
import com.mohammedsharukms.mypersonaltracker.sheet.medicineList
import com.mohammedsharukms.mypersonaltracker.sheet.parentDateList
import com.mohammedsharukms.mypersonaltracker.sheet.ParentContacts
import com.mohammedsharukms.mypersonaltracker.sheet.ParentPhotos
import com.mohammedsharukms.mypersonaltracker.sheet.ParentPrefs
import com.mohammedsharukms.mypersonaltracker.sheet.ParentRepeat
import com.mohammedsharukms.mypersonaltracker.sheet.ParentWho
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.doneParentAsks
import com.mohammedsharukms.mypersonaltracker.sheet.foodData
import com.mohammedsharukms.mypersonaltracker.sheet.openParentAsks
import com.mohammedsharukms.mypersonaltracker.sheet.parentSpend
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

// Mom & Dad: what they asked you to do, on the Dashboard and as its own tab (the
// card and a tapped reminder open it). Each request can carry photos (the
// prescription, the bill) and a cost for Finance, and is a long-press away from
// calling or messaging them. Mirrors ParentsView.swift.

/** On the Dashboard: what's waiting, a tick to mark it done, + to add one. */
@Composable
fun ParentsCard(store: SheetStore, snapshot: SheetSnapshot, onOpen: () -> Unit) {
    var adding by remember { mutableStateOf<ParentAsk?>(null) }
    val open = snapshot.openParentAsks
    val late = open.count { it.isLate() }
    SheetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.FamilyRestroom, if (late > 0) SheetTheme.bad else SheetTheme.purple, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Mom & Dad asked", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                    Text(if (open.isEmpty()) "Nothing waiting" else if (late > 0) "$late late · ${open.size} to do" else "${open.size} to do",
                        fontSize = 12.sp, color = if (late > 0) SheetTheme.bad else SheetTheme.secondary)
                }
            }
            IconButton({ adding = ParentAsk() }) { Icon(Icons.Filled.AddCircle, "Add something Mom or Dad asked for", tint = SheetTheme.navy) }
        }
        if (open.isEmpty()) {
            Text("When Mom or Dad asks you to do something, add it here. You'll get a reminder until it's done.",
                fontSize = 14.sp, color = SheetTheme.secondary, modifier = Modifier.padding(top = 6.dp))
        }
        val low = snapshot.medicineList.filter { it.runsLow() }
        val soon = snapshot.parentDateList.firstOrNull { (it.daysUntil() ?: 99) <= 7 }
        // One line each, with a symbol rather than an emoji in front.
        if (low.isNotEmpty() || soon != null) Column(Modifier.clickable(onClick = onOpen).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            @Composable fun alert(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(icon, null, tint = SheetTheme.warn, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.warn)
            }
            if (low.isNotEmpty()) alert(androidx.compose.material.icons.Icons.Filled.Medication, "${if (low.size == 1) low[0].name else "${low.size} medicines"} to refill")
            soon?.let { alert(it.kind.icon, "${it.title} ${it.whenText().lowercase()}") }
        }
        open.take(3).forEach { ParentAskRow(store, it, onEdit = onOpen) }
        if (open.size > 3) Text("See all ${open.size}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.navy,
            modifier = Modifier.clickable(onClick = onOpen).padding(vertical = 8.dp))
    }
    adding?.let { a -> ParentAskEditorSheet(store, a, isNew = true) { adding = null } }
}

/** One request: a tick to mark it done, then what, who and when. Long-press to call or message them. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParentAskRow(store: SheetStore, ask: ParentAsk, onDone: (ParentAsk) -> Unit = {}, onEdit: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val late = ask.isLate()
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton({
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (ask.isOpen) { ParentActions.complete(context, store, ask); onDone(ask.markedDone()) } else ParentActions.reopen(context, store, ask)
        }) {
            Icon(if (ask.isOpen) Icons.Filled.RadioButtonUnchecked else Icons.Filled.CheckCircle,
                if (ask.isOpen) "Mark done: ${ask.task}" else "Mark not done: ${ask.task}",
                tint = if (!ask.isOpen) SheetTheme.good else if (late) SheetTheme.bad else SheetTheme.navy, modifier = Modifier.size(26.dp))
        }
        Box(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().combinedClickable(onClick = onEdit, onLongClick = { menu = true }, onLongClickLabel = "Call or message")
                .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ask.task, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text,
                        textDecoration = if (ask.isOpen) null else TextDecoration.LineThrough)
                    Text(ParentAsk.detail(ask), fontSize = 12.sp, color = if (late) SheetTheme.bad else SheetTheme.secondary)
                }
                if (ask.photos.isNotEmpty()) {
                    Icon(Icons.Filled.Photo, "${ask.photos.size} photos", tint = SheetTheme.secondary, modifier = Modifier.size(14.dp))
                    Text("${ask.photos.size}", fontSize = 11.sp, color = SheetTheme.secondary, modifier = Modifier.padding(start = 2.dp, end = 6.dp))
                }
                ask.cost?.takeIf { it > 0 }?.let { Text(SheetFormat.qar(it), fontSize = 12.sp, color = SheetTheme.secondary, modifier = Modifier.padding(end = 6.dp)) }
                if (ask.pending) Icon(Icons.Filled.Schedule, "Not synced yet", tint = SheetTheme.sky, modifier = Modifier.size(14.dp))
            }
            DropdownMenu(menu, { menu = false }) { ContactItems(context, ask) { menu = false } }
        }
    }
}

/** Call, WhatsApp or text whoever asked — about it, or to say it's done. */
@Composable
private fun ContactItems(context: Context, ask: ParentAsk, close: () -> Unit) {
    val people = ParentContacts.people(ask.who).mapNotNull { who -> ParentPrefs.phone(context, who)?.let { who to it } }
    if (people.isEmpty()) {
        DropdownMenuItem({ Text("Add Mom's and Dad's numbers (phone button in the Parents tab)") }, close)
    }
    people.forEach { (who, phone) ->
        val text = ParentContacts.message(ask, who)
        DropdownMenuItem({ Text("Call ${who.raw}") }, { close(); call(context, who, phone) }, leadingIcon = { Icon(Icons.Filled.Phone, null) })
        DropdownMenuItem({ Text("WhatsApp ${who.raw}") }, { close(); whatsApp(context, who, phone, text) }, leadingIcon = { Icon(Icons.Filled.Chat, null) })
        DropdownMenuItem({ Text("Text ${who.raw}") }, { close(); sms(context, phone, text) }, leadingIcon = { Icon(Icons.Filled.Sms, null) })
    }
}

/** Opens a call, WhatsApp or a text — and says so when this phone has nothing to open it with. */
private fun open(context: Context, intent: Intent, otherwise: String): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: android.content.ActivityNotFoundException) {
    android.widget.Toast.makeText(context, otherwise, android.widget.Toast.LENGTH_LONG).show()
    false
}

/** A call or WhatsApp from here counts as talking to them (for the "call them" nudge). */
private fun talkedIf(context: Context, who: ParentWho, opened: Boolean) {
    if (!opened) return
    ParentPrefs.talked(context, who)
    ReminderScheduler.reschedule(context)
}

private fun call(context: Context, who: ParentWho, phone: String) =
    talkedIf(context, who, open(context, Intent(Intent.ACTION_DIAL, Uri.parse(ParentContacts.callUri(phone))), "This phone has no app to make calls."))
private fun whatsApp(context: Context, who: ParentWho, phone: String, text: String) =
    talkedIf(context, who, open(context, Intent(Intent.ACTION_VIEW, Uri.parse(ParentContacts.whatsAppUri(phone, text))), "Couldn't open WhatsApp."))
private fun sms(context: Context, phone: String, text: String) =
    open(context, Intent(Intent.ACTION_SENDTO, Uri.parse(ParentContacts.smsUri(phone))).putExtra("sms_body", text), "This phone has no app to send texts.")

/** Mom or Dad at the top of the Parents tab: who, when you last talked, and round Call · WhatsApp · Send ₹
 *  buttons. Without a number, Call and WhatsApp ask for it. */
@Composable
private fun PersonRow(who: ParentWho, onTalked: () -> Unit, onAddNumber: () -> Unit, onSend: (ParentWho) -> Unit) {
    val context = LocalContext.current
    val phone = ParentPrefs.phone(context, who)
    val last = ParentPrefs.lastTalk(context, who)
    val since = com.mohammedsharukms.mypersonaltracker.sheet.ParentTalk.daysSince(last, LocalDate.now())
    val every = ParentPrefs.callEvery(context, who)
    val due = every > 0 && (since ?: Int.MAX_VALUE) >= every
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (who == ParentWho.MOM) SheetTheme.purple else SheetTheme.indigo), contentAlignment = Alignment.Center) {
            Text(who.raw.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(who.raw, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            Text(if (since == 0) "Talked today ✓" else com.mohammedsharukms.mypersonaltracker.sheet.ParentTalk.text(last) ?: "Not noted yet",
                fontSize = 12.sp, color = if (due) SheetTheme.warn else SheetTheme.secondary, maxLines = 1)
            if (since != 0) Text("Mark as talked", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.navy,
                modifier = Modifier.clickable(onClickLabel = "Mark talked to ${who.raw} today") {
                    ParentPrefs.talked(context, who); ReminderScheduler.reschedule(context); onTalked()
                }.padding(vertical = 2.dp))
        }
        RoundAction(Icons.Filled.Phone, SheetTheme.good, "Call ${who.raw}") { if (phone != null) call(context, who, phone) else onAddNumber() }
        RoundAction(Icons.Filled.Chat, Color(0xFF26AD61), "WhatsApp ${who.raw}") { if (phone != null) whatsApp(context, who, phone, "Hi ${who.raw}, ") else onAddNumber() }
        RoundAction(Icons.Filled.CurrencyRupee, SheetTheme.teal, "Send money to ${who.raw}") { onSend(who) }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, description: String, onClick: () -> Unit) {
    IconButton(onClick) {
        androidx.compose.foundation.layout.Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center) { Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp)) }
    }
}

/** Mom or Dad with Call · WhatsApp · Text (a request's editor, after ticking one off). */
@Composable
private fun ContactStrip(who: ParentWho, phone: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(who.raw, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, modifier = Modifier.weight(1f))
        Pills(who, phone, text)
    }
}

@Composable
private fun Pills(who: ParentWho, phone: String, text: String) {
    val context = LocalContext.current
    ContactPill("Call", Icons.Filled.Phone, SheetTheme.good, "Call ${who.raw}") { call(context, who, phone) }
    ContactPill("WhatsApp", Icons.Filled.Chat, Color(0xFF26AD61), "WhatsApp ${who.raw}") { whatsApp(context, who, phone, text) }
    ContactPill("Text", Icons.Filled.Sms, SheetTheme.sky, "Text ${who.raw}") { sms(context, phone, text) }
}

@Composable
private fun ContactPill(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, description: String, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.13f)).clickable(onClickLabel = description, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tint, maxLines = 1, modifier = Modifier.padding(start = 4.dp))
    }
}

/** The Parents tab: everything they asked for — late, today, coming up, then what's done. */
@Composable
fun ParentsScreen(store: SheetStore) {
    var editing by remember { mutableStateOf<Pair<ParentAsk, Boolean>?>(null) }
    var editingMedicine by remember { mutableStateOf<Pair<ParentMedicine, Boolean>?>(null) }
    var refilling by remember { mutableStateOf<ParentMedicine?>(null) }
    var editingDate by remember { mutableStateOf<Pair<ParentDate, Boolean>?>(null) }
    /** To do, Medicines, Dates, Trip home or Health. */
    var page by remember { mutableStateOf(0) }
    val tripUi = remember { TakeHomeUi() }
    val healthUi = remember { ParentHealthUi() }
    var who by remember { mutableStateOf<ParentWho?>(null) }
    var editingContacts by remember { mutableStateOf(false) }
    /** "Send" before their UPI ID is saved: ask for it, then open Pay. */
    var askingUpi by remember { mutableStateOf<ParentWho?>(null) }
    /** Bumped when the numbers change, so the contact card redraws. */
    var contactsVersion by remember { mutableStateOf(0) }
    /** Just ticked off: offer to tell them. */
    var justDone by remember { mutableStateOf<ParentAsk?>(null) }
    val context = LocalContext.current
    fun send(w: ParentWho) { if (!ParentActions.sendMoney(context, w)) askingUpi = w }
    LaunchedEffect(justDone?.id) { if (justDone != null) { kotlinx.coroutines.delay(10_000); justDone = null } }
    SheetScreen("Mom & Dad", isRefreshing = store.isLoading, onRefresh = { store.refresh() },
        floating = { justDone?.let { done -> TellBanner(done) { justDone = null } } },
        actions = {
            IconButton({ editingContacts = true }) { Icon(Icons.Filled.Phone, "Calls and reminders", tint = SheetTheme.navy) }
            IconButton({
                store.snapshot?.let { s ->
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, com.mohammedsharukms.mypersonaltracker.sheet.ParentShare.text(s, who))
                    runCatching { context.startActivity(Intent.createChooser(send, "Share what's still to do").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }) { Icon(Icons.Filled.Share, "Share what's still to do", tint = SheetTheme.navy) }
            IconButton({
                when (page) {
                    0 -> editing = ParentAsk(who = who ?: ParentWho.MOM) to true
                    1 -> editingMedicine = ParentMedicine(who = who ?: ParentWho.DAD) to true
                    2 -> editingDate = ParentDate(who = who ?: ParentWho.MOM) to true
                    3 -> tripUi.editing = TakeHomeItem(who = who ?: ParentWho.BOTH)
                    else -> healthUi.editing = com.mohammedsharukms.mypersonaltracker.sheet.ParentReading(who = if (who == ParentWho.MOM) ParentWho.MOM else ParentWho.DAD, kind = healthUi.kind) to true
                }
            }) { Icon(Icons.Filled.AddCircle, "Add", tint = SheetTheme.navy) }
        }) {
        val s = store.snapshot
        if (s == null) { item { if (store.needsConnection) NotConnectedHint() else SheetLoadingCard(store.loadProgress) }; return@SheetScreen }
        val match = { a: ParentAsk -> who == null || a.who == who || a.who == ParentWho.BOTH }
        val open = s.openParentAsks.filter(match)
        val today = LocalDate.now().toString()
        s.foodData.photoError?.takeIf { s.parents.any { it.photos.isNotEmpty() } }?.let { error -> item { SheetErrorBanner(error) {} } }
        item {
            @Suppress("UNUSED_EXPRESSION") contactsVersion
            SheetCard(padding = 12.dp) {
                listOf(ParentWho.MOM, ParentWho.DAD).forEachIndexed { i, w ->
                    if (i > 0) androidx.compose.material3.HorizontalDivider(color = SheetTheme.input, modifier = Modifier.padding(start = 52.dp, top = 6.dp, bottom = 6.dp))
                    PersonRow(w, onTalked = { contactsVersion++ }, onAddNumber = { editingContacts = true }, onSend = ::send)
                }
            }
        }
        val money = s.parentMoney()
        if (money.total > 0) item { ParentMoneyCard(money) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("To do", "Medicines", "Dates", "Trip home", "Health").forEachIndexed { i, title -> FilterPill(title, null, SheetTheme.navy, page == i) { page = i } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null to "Everyone", ParentWho.MOM to "Mom", ParentWho.DAD to "Dad").forEach { (w, title) ->
                        FilterPill(title, null, SheetTheme.purple, who == w) { who = w }
                    }
                }
            }
        }
        fun forWho(w: ParentWho) = who == null || w == who || w == ParentWho.BOTH
        if (page == 1) {
            val list = s.medicineList.filter { forWho(it.who) }
            if (list.isEmpty()) item {
                SheetCard {
                    Text("Never let their tablets run out", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                    Text("Add what Mom and Dad take, how many a day and how many are left. It counts down by itself and the daily Mom & Dad notification warns you a week before they run out.",
                        fontSize = 14.sp, color = SheetTheme.secondary, modifier = Modifier.padding(top = 6.dp))
                }
            }
            val low = list.filter { it.runsLow() }
            if (low.isNotEmpty()) item { SectionTitle("Refill soon") }
            low.forEach { m -> item(key = "m-" + m.id) { ParentMedicineRow(m, onEdit = { editingMedicine = m to false }, onRefill = { refilling = m }) } }
            val rest = list.filter { !it.runsLow() }
            if (rest.isNotEmpty() && low.isNotEmpty()) item { SectionTitle("Others") }
            rest.forEach { m -> item(key = "m-" + m.id) { ParentMedicineRow(m, onEdit = { editingMedicine = m to false }, onRefill = { refilling = m }) } }
            return@SheetScreen
        }
        if (page == 3) {
            takeHomeItems(store, s, who, tripUi) { ReminderScheduler.reschedule(context) }
            return@SheetScreen
        }
        if (page == 4) {
            parentHealthItems(s, if (who == ParentWho.MOM) ParentWho.MOM else ParentWho.DAD, healthUi)
            return@SheetScreen
        }
        if (page == 2) {
            val list = s.parentDateList.filter { forWho(it.who) }
            if (list.isEmpty()) item {
                SheetCard {
                    Text("Their days, every year", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                    Text("Birthdays, their anniversary, a grandparent's remembrance day. You're reminded a week and a day before, and on the day.",
                        fontSize = 14.sp, color = SheetTheme.secondary, modifier = Modifier.padding(top = 6.dp))
                }
            }
            list.forEach { d -> item(key = "d-" + d.id) { ParentDateRow(d) { editingDate = d to false } } }
            return@SheetScreen
        }
        if (s.parents.isEmpty()) item {
            SheetCard {
                Text("Never forget what they asked", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                Text("Book Dad's eye check-up, send Mom the photos, refill the tablets every month… Add it when they ask, pick a day and a time, and you'll get a reminder every day until you tick it off. Snap the prescription or the bill with it.",
                    fontSize = 14.sp, color = SheetTheme.secondary, modifier = Modifier.padding(top = 6.dp))
            }
        }
        fun group(title: String, list: List<ParentAsk>) {
            if (list.isEmpty()) return
            item { SectionTitle(title) }
            item {
                SheetCard(padding = 4.dp) {
                    list.forEach { a ->
                        ParentAskRow(store, a, onDone = { done ->
                            if (ParentContacts.people(done.who).any { ParentPrefs.phone(context, it) != null }) justDone = done
                        }) { editing = a to false }
                    }
                }
            }
        }
        group("Late", open.filter { it.date < today })
        group("Today", open.filter { it.date == today })
        group("Coming up", open.filter { it.date > today })
        group("Done", s.doneParentAsks.filter(match).take(30))
    }
    editing?.let { (a, isNew) -> ParentAskEditorSheet(store, a, isNew) { editing = null } }
    if (editingContacts) ParentContactsDialog { editingContacts = false; contactsVersion++ }
    askingUpi?.let { w -> ParentUpiDialog(w, onDismiss = { askingUpi = null }) { askingUpi = null; contactsVersion++; ParentActions.sendMoney(context, w) } }
    editingMedicine?.let { (m, isNew) -> ParentMedicineEditorSheet(store, m, isNew) { editingMedicine = null } }
    refilling?.let { m -> ParentRefillDialog(store, m) { refilling = null } }
    editingDate?.let { (d, isNew) -> ParentDateEditorSheet(store, d, isNew) { editingDate = null } }
    tripUi.editing?.let { i -> TakeHomeItemEditorSheet(store, i) { tripUi.editing = null; ReminderScheduler.reschedule(context) } }
    if (tripUi.choosingTrip) TripDateDialog(store, store.snapshot?.takeHome?.trip) { tripUi.choosingTrip = false }
    healthUi.editing?.let { (r, isNew) -> ParentReadingEditorSheet(store, r, isNew) { healthUi.editing = null } }
}

/** "Done — tell Dad?" for a few seconds after ticking one off. */
@Composable
private fun TellBanner(done: ParentAsk, onClose: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.padding(start = 32.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = SheetTheme.good, modifier = Modifier.size(18.dp))
            Text("Done — tell ${done.who.label}?", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.good,
                modifier = Modifier.weight(1f).padding(start = 6.dp))
            Text("Close", fontSize = 13.sp, color = SheetTheme.secondary, modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
        }
        ParentContacts.people(done.who).forEach { w ->
            ParentPrefs.phone(context, w)?.let { phone -> ContactStrip(w, phone, ParentContacts.message(done, w)) }
        }
    }
}

/** "Send" before their UPI ID is saved: ask for it once, then straight on to Pay. */
@Composable
private fun ParentUpiDialog(who: ParentWho, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var id by remember { mutableStateOf("") }
    val valid = com.mohammedsharukms.mypersonaltracker.sheet.UpiPayee.isValidVpa(id.trim())
    AppDialog(
        title = "Send money to ${who.raw}",
        message = "Their UPI ID — ask ${who.raw}, or find it in their UPI app (PhonePe, Google Pay, Paytm…). It's saved on this phone, so next time Send goes straight to Pay.",
        icon = Icons.Filled.CurrencyRupee,
        confirmText = "Next",
        confirmEnabled = valid,
        onConfirm = { ParentPrefs.setUpi(context, who, id); onSaved() },
        onDismiss = onDismiss,
    ) {
        PlainField(id, "e.g. name@okicici") { id = it }
        if (id.isNotBlank() && !valid) Text("That doesn't look like a UPI ID (name@bank).", fontSize = 12.sp, color = SheetTheme.bad)
    }
}

/** Mom's and Dad's numbers, kept on this phone only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentContactsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var mom by remember { mutableStateOf(ParentPrefs.phone(context, ParentWho.MOM) ?: "") }
    var dad by remember { mutableStateOf(ParentPrefs.phone(context, ParentWho.DAD) ?: "") }
    var momEvery by remember { mutableStateOf(ParentPrefs.callEvery(context, ParentWho.MOM)) }
    var dadEvery by remember { mutableStateOf(ParentPrefs.callEvery(context, ParentWho.DAD)) }
    var momUpi by remember { mutableStateOf(ParentPrefs.upiRaw(context, ParentWho.MOM)) }
    var dadUpi by remember { mutableStateOf(ParentPrefs.upiRaw(context, ParentWho.DAD)) }
    var minutes by remember { mutableStateOf(ParentPrefs.digestMinutes(context)) }
    var pickingTime by remember { mutableStateOf(false) }
    AppDialog(
        title = "Calls & reminders",
        message = "Numbers with the country code, e.g. +91 98765 43210. They stay on this phone.",
        icon = Icons.Filled.Phone,
        confirmText = "Save",
        onConfirm = {
            ParentPrefs.setPhone(context, ParentWho.MOM, mom); ParentPrefs.setPhone(context, ParentWho.DAD, dad)
            ParentPrefs.setCallEvery(context, ParentWho.MOM, momEvery); ParentPrefs.setCallEvery(context, ParentWho.DAD, dadEvery)
            ParentPrefs.setDigestMinutes(context, minutes)
            ParentPrefs.setUpi(context, ParentWho.MOM, momUpi); ParentPrefs.setUpi(context, ParentWho.DAD, dadUpi)
            ReminderScheduler.reschedule(context)
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        Field("Mom") { PlainField(mom, "+91 …") { mom = it } }
        Field("Dad") { PlainField(dad, "+91 …") { dad = it } }
        Field("Mom's UPI ID (to send money)") { PlainField(momUpi, "e.g. name@okicici") { momUpi = it } }
        Field("Dad's UPI ID (to send money)") { PlainField(dadUpi, "e.g. name@oksbi") { dadUpi = it } }
        listOf(momUpi, dadUpi).filter { it.isNotBlank() && !com.mohammedsharukms.mypersonaltracker.sheet.UpiPayee.isValidVpa(it.trim()) }.firstOrNull()?.let {
            Text("“$it” doesn't look like a UPI ID (name@bank).", fontSize = 12.sp, color = SheetTheme.bad)
        }
        val choices = listOf(0, 1, 2, 3, 7)
        fun label(n: Int) = if (n == 0) "Off" else if (n == 1) "Daily" else "${n}d"
        Field("Remind me to call Mom") { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { choices.forEach { n -> FilterPill(label(n), null, SheetTheme.navy, momEvery == n) { momEvery = n } } } }
        Field("Remind me to call Dad") { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { choices.forEach { n -> FilterPill(label(n), null, SheetTheme.navy, dadEvery == n) { dadEvery = n } } } }
        Row(Modifier.fillMaxWidth().clickable { pickingTime = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Daily Mom & Dad reminder", fontSize = 15.sp, color = SheetTheme.text, modifier = Modifier.weight(1f))
            Text(ParentAsk.timeText("%02d:%02d".format(minutes / 60, minutes % 60)) ?: "", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
        }
        Text("Medicines running low, their days coming up, a call that's due — one notification a day, only when there's something.", fontSize = 12.sp, color = SheetTheme.secondary)
    }
    if (pickingTime) {
        val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60)
        AppDialog(title = "Daily reminder at", confirmText = "Set time", onConfirm = { minutes = state.hour * 60 + state.minute; pickingTime = false },
            onDismiss = { pickingTime = false }) { androidx.compose.material3.TimeInput(state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentAskEditorSheet(store: SheetStore, initial: ParentAsk, isNew: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var who by remember { mutableStateOf(initial.who) }
    var task by remember { mutableStateOf(initial.task) }
    var day by remember { mutableStateOf(runCatching { LocalDate.parse(initial.date) }.getOrDefault(LocalDate.now())) }
    var repeats by remember { mutableStateOf(initial.repeats) }
    var remind by remember { mutableStateOf(initial.time != null) }
    var time by remember { mutableStateOf(initial.time ?: ParentAsk.DEFAULT_TIME) }
    var nag by remember { mutableStateOf(initial.nag) }
    var notes by remember { mutableStateOf(initial.notes ?: "") }
    var photos by remember { mutableStateOf(initial.photos) }
    var cost by remember { mutableStateOf(initial.cost?.let { SheetFormat.plain(it) } ?: "") }
    var finance by remember { mutableStateOf(initial.finance) }
    var pickingTime by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun attach(uri: Uri) {
        if (photos.size >= ParentAsk.MAX_PHOTOS) return
        val jpeg = MealPhotos.prepare(context, uri) ?: return
        photos = photos + ParentPhotos.add(context, jpeg)
    }
    val captureFile = remember { File(context.cacheDir, "captures").apply { mkdirs() }.let { File(it, "parent-${initial.id}.jpg") } }
    val captureUri: Uri = remember { FileProvider.getUriForFile(context, context.packageName + ".fileprovider", captureFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) attach(captureUri) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ParentAsk.MAX_PHOTOS)) { uris ->
        uris.forEach(::attach)
    }

    fun current(): ParentAsk {
        val amount = SheetFormat.input(cost)?.takeIf { it > 0 }
        return initial.copy(who = who, task = task.trim(), date = day.toString(), repeats = repeats, time = if (remind) time else null,
            nag = nag, notes = notes.trim().ifEmpty { null }, photos = photos, cost = amount, finance = amount != null && finance)
    }
    fun save(a: ParentAsk) {
        store.saveParentAsk(a)
        // Asked here, when it's first needed.
        if (a.time != null && android.os.Build.VERSION.SDK_INT >= 33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        ReminderScheduler.reschedule(context)
        onDismiss()
    }
    if (confirmRemove) AppDialog(
        title = "Delete this request?",
        message = "Its reminders and photos go too.",
        style = DialogStyle.DESTRUCTIVE,
        confirmText = "Delete",
        onConfirm = { confirmRemove = false; store.deleteParentAsk(initial.id); ReminderScheduler.reschedule(context); onDismiss() },
        onDismiss = { confirmRemove = false },
    )
    EditorSheet(if (isNew) "Mom & Dad asked" else "${initial.who.label} asked", onDismiss, canSave = task.isNotBlank(), onSave = { save(current()) }) {
        Field("Who asked") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParentWho.entries.forEach { w -> FilterPill(w.label, null, SheetTheme.navy, who == w) { who = w } }
            }
        }
        Field("What did they ask for?") { PlainField(task, "e.g. Book Dad's eye check-up", singleLine = false) { task = it } }
        if (!isNew) initial.asked?.let { Text("Asked on ${SheetDate.short(it)}.", fontSize = 12.sp, color = SheetTheme.secondary) }
        if (!isNew) Field("Let them know") {
            val people = ParentContacts.people(who).mapNotNull { w -> ParentPrefs.phone(context, w)?.let { w to it } }
            SheetCard(padding = 12.dp) {
                if (people.isEmpty()) Text("Add ${if (who == ParentWho.BOTH) "their numbers" else who.raw + "'s number"} with the phone button in the Parents tab to call or message from here.",
                    fontSize = 13.sp, color = SheetTheme.secondary)
                people.forEach { (w, phone) -> ContactStrip(w, phone, ParentContacts.message(current(), w)) }
            }
        }

        Field("Photos") {
            if (photos.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                photos.forEach { ref ->
                    Box {
                        ParentPhotoThumb(store, ref, Modifier.size(84.dp).clickable { viewing = ref })
                        Icon(Icons.Filled.Cancel, "Remove photo", tint = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.55f)).clickable { photos = photos - ref })
                    }
                }
            }
            if (photos.size < ParentAsk.MAX_PHOTOS) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigChoice("Camera", Icons.Filled.CameraAlt, Modifier.weight(1f)) { camera.launch(captureUri) }
                BigChoice("Photos", Icons.Filled.PhotoLibrary, Modifier.weight(1f)) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            Text(if (photos.isEmpty()) "Optional — the prescription, the product they want, the bill. Up to ${ParentAsk.MAX_PHOTOS}."
                else "Tap one to see it full size and share it.", fontSize = 12.sp, color = SheetTheme.secondary)
        }

        DateField(if (repeats == ParentRepeat.ONCE) "Do it on" else "Next on", day, allowClear = false) { if (it != null) day = it }
        Field("Repeat") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParentRepeat.entries.forEach { r -> FilterPill(r.label, null, SheetTheme.navy, repeats == r) { repeats = r } }
            }
        }
        if (repeats != ParentRepeat.ONCE) Text("When you tick it off, it moves to the next date.", fontSize = 12.sp, color = SheetTheme.secondary)
        SheetCard(padding = 4.dp) {
            ToggleRow("Remind me", remind) { remind = it }
            if (remind) {
                Row(Modifier.fillMaxWidth().clickable { pickingTime = true }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("At", fontSize = 15.sp, color = SheetTheme.text, modifier = Modifier.weight(1f))
                    Text(ParentAsk.timeText(time) ?: time, fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
                }
                ToggleRow("Keep reminding until it's done", nag) { nag = it }
            }
        }
        Text(when {
            !remind -> "It stays on your list without a notification."
            nag -> "A notification at this time from that day on, every day, until you tick it off. “Tomorrow” on the notification gives you a day."
            else -> "A notification at this time on the day${if (repeats == ParentRepeat.ONCE) "" else " each time it comes round"}."
        }, fontSize = 12.sp, color = SheetTheme.secondary)

        NumberField("It costs (optional)", cost, Money.main) { cost = it }
        if ((SheetFormat.input(cost) ?: 0.0) > 0) {
            SheetCard(padding = 4.dp) { ToggleRow("Add to Finance when done", finance) { finance = it } }
            if (finance) Text("Logged as Gifts & Family each time you tick it off, so it counts toward Family support.", fontSize = 12.sp, color = SheetTheme.secondary)
        }
        Field("Notes (optional)") { PlainField(notes, "Anything to remember", singleLine = false) { notes = it } }
        if (!isNew) {
            if (initial.isOpen) {
                PrimaryButton(if (initial.repeats == ParentRepeat.ONCE) "Mark as done" else "Done — move to the next date", Modifier.fillMaxWidth()) {
                    ParentActions.complete(context, store, current()); onDismiss()
                }
            } else {
                PrimaryButton("Not done yet", Modifier.fillMaxWidth()) { ParentActions.reopen(context, store, current()); onDismiss() }
            }
            SheetCard(onClick = { confirmRemove = true }) { Text("Delete", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold) }
        }
    }
    if (pickingTime) {
        val start = runCatching { LocalTime.parse(time.padStart(5, '0')) }.getOrDefault(LocalTime.of(9, 0))
        val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute)
        AppDialog(
            title = "Remind me at",
            confirmText = "Set time",
            onConfirm = { time = "%02d:%02d".format(state.hour, state.minute); pickingTime = false },
            onDismiss = { pickingTime = false },
        ) { androidx.compose.material3.TimeInput(state) }
    }
    viewing?.let { ref -> ParentPhotoViewer(store, ref) { viewing = null } }
}

/** One photo, square: on this phone, cached, or downloaded from Drive. */
@Composable
private fun ParentPhotoThumb(store: SheetStore, ref: String, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(ref) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(ref) { bitmap = ParentPhotos.bitmap(context, store, ref, thumb = true) }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(SheetTheme.input), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f)) }
            ?: androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

/** Full size, with Share (to the pharmacy, a sibling…). */
@Composable
private fun ParentPhotoViewer(store: SheetStore, ref: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(ref) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(ref) { bitmap = ParentPhotos.bitmap(context, store, ref, thumb = false) }
    AppDialog(
        title = "Photo",
        icon = Icons.Filled.Share,
        confirmText = "Share",
        confirmEnabled = bitmap != null,
        dismissText = "Close",
        onConfirm = { bitmap?.let { share(context, it) } },
        onDismiss = onDismiss,
    ) {
        bitmap?.let { Image(it.asImageBitmap(), "Photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()) }
            ?: androidx.compose.material3.CircularProgressIndicator()
    }
}

private fun share(context: Context, bitmap: Bitmap) {
    runCatching {
        val file = File(File(context.cacheDir, "captures").apply { mkdirs() }, "share-photo.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share photo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
