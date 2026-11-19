package com.mohammedsharukms.mypersonaltracker.sheet.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.sheet.GoalStatus
import com.mohammedsharukms.mypersonaltracker.sheet.Loan
import com.mohammedsharukms.mypersonaltracker.sheet.RecurringExpense
import com.mohammedsharukms.mypersonaltracker.sheet.Renewal
import com.mohammedsharukms.mypersonaltracker.sheet.RenewalKind
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.StudyFormat
import com.mohammedsharukms.mypersonaltracker.sheet.StudyTopic
import com.mohammedsharukms.mypersonaltracker.sheet.WeeklyReview
import com.mohammedsharukms.mypersonaltracker.sheet.YearNumbers
import com.mohammedsharukms.mypersonaltracker.sheet.activeLoans
import com.mohammedsharukms.mypersonaltracker.sheet.lifeGoalData
import com.mohammedsharukms.mypersonaltracker.sheet.monthlyEMI
import com.mohammedsharukms.mypersonaltracker.sheet.renewalList
import com.mohammedsharukms.mypersonaltracker.sheet.renewalsDue
import com.mohammedsharukms.mypersonaltracker.sheet.shoppingData
import com.mohammedsharukms.mypersonaltracker.sheet.studyData
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// More ▸ Renewals, Finance ▸ Budget ▸ Loans & EMIs, the week and year in review,
// and the switches for the sheet's notifications. Mirrors RenewalsLoansView.swift
// and ReviewViews.swift.

// MARK: - Renewals

@Composable
fun RenewalsScreen(store: SheetStore, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Pair<Renewal, Boolean>?>(null) }
    SheetScreen("Renewals", isRefreshing = store.isLoading, onRefresh = { store.refresh() }, onBack = onBack,
        actions = { IconButton({ editing = Renewal() to true }) { Icon(Icons.Filled.AddCircle, "Add a renewal", tint = SheetTheme.navy) } }) {
        val list = store.snapshot?.renewalList.orEmpty()
        if (list.isEmpty()) item {
            SheetCard {
                Text("Never miss an expiry", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                Text("Add your QID, passport, visa, driving licence, car registration, insurance or rent contract with its expiry date. You'll get a reminder 30, 7 and 1 day before.",
                    fontSize = 14.sp, color = SheetTheme.secondary, modifier = Modifier.padding(top = 6.dp))
            }
        }
        items(list, key = { it.id }) { r ->
            val days = r.daysLeft()
            val tint = renewalTint(days)
            SheetCard(onClick = { editing = r to false }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.EventBusy, tint, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.item, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                        Text("${SheetDate.short(r.expires)} · ${Renewal.left(days)}", fontSize = 12.sp, color = if (days <= 7) tint else SheetTheme.secondary)
                    }
                    if (days <= 30) StatusPill(if (days < 0) "Expired" else "Soon", tint)
                }
            }
        }
    }
    editing?.let { (r, isNew) -> RenewalEditorSheet(store, r, isNew) { editing = null } }
}

@Composable
private fun renewalTint(days: Long): Color = when {
    days < 0 -> SheetTheme.bad
    days <= 7 -> SheetTheme.warn
    days <= 30 -> SheetTheme.sky
    else -> SheetTheme.navy
}

@Composable
private fun RenewalEditorSheet(store: SheetStore, initial: Renewal, isNew: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var kind by remember { mutableStateOf(initial.kind) }
    var item by remember { mutableStateOf(initial.item) }
    var expires by remember { mutableStateOf(runCatching { LocalDate.parse(initial.expires) }.getOrDefault(LocalDate.now().plusYears(1))) }
    var remind by remember { mutableStateOf(initial.remind) }
    var cost by remember { mutableStateOf(initial.cost?.let { SheetFormat.plain(it) } ?: "") }
    var notes by remember { mutableStateOf(initial.notes ?: "") }
    var renewing by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    if (confirmRemove) AppDialog(
        title = "Delete ${initial.item}?",
        message = "Its reminders stop.",
        style = DialogStyle.DESTRUCTIVE,
        confirmText = "Delete",
        onConfirm = { confirmRemove = false; store.deleteRenewal(initial.id); onDismiss() },
        onDismiss = { confirmRemove = false },
    )
    fun save(r: Renewal) {
        store.saveRenewal(r)
        com.mohammedsharukms.mypersonaltracker.reminders.ReminderScheduler.reschedule(context)
    }
    EditorSheet(if (isNew) "New renewal" else initial.item, onDismiss, onSave = {
        save(initial.copy(kind = kind, item = item.trim().ifEmpty { kind.label }, expires = expires.toString(), remind = remind,
            cost = SheetFormat.input(cost), notes = notes.trim().ifEmpty { null }))
        onDismiss()
    }) {
        Field("What") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RenewalKind.entries.forEach { k -> FilterPill(k.label, null, SheetTheme.navy, kind == k) { kind = k } }
            }
        }
        Field("Name") { PlainField(item, if (kind == RenewalKind.OTHER) "e.g. Gym membership" else "e.g. ${kind.label}") { item = it } }
        DateField("Expires", expires, allowClear = false) { if (it != null) expires = it }
        Text("Only the date is kept — no document numbers.", fontSize = 12.sp, color = SheetTheme.secondary)
        Field("Remind me") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 30, 14, 7, 3, 1, 0).forEach { d ->
                    FilterPill(if (d == 0) "On the day" else "$d day${if (d == 1) "" else "s"}", null, SheetTheme.navy, d in remind) {
                        remind = if (d in remind) remind - d else (remind + d).sortedDescending()
                    }
                }
            }
        }
        NumberField("Renewal cost (optional)", cost, Money.main) { cost = it }
        Field("Notes (optional)") { PlainField(notes, "Anything to remember", singleLine = false) { notes = it } }
        if (!isNew) {
            PrimaryButton("Renewed — set the new date", Modifier.fillMaxWidth()) { renewing = true }
            SheetCard(onClick = { confirmRemove = true }) { Text("Delete", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold) }
        }
    }
    if (renewing) {
        var next by remember { mutableStateOf(runCatching { LocalDate.parse(initial.suggestedNext) }.getOrDefault(LocalDate.now().plusYears(1))) }
        var paidText by remember { mutableStateOf(initial.cost?.let { SheetFormat.plain(it) } ?: "") }
        var toFinance by remember { mutableStateOf(true) }
        AppDialog(
            title = "Renewed ${initial.item}",
            icon = Icons.Filled.Verified,
            confirmText = "Save",
            onConfirm = {
                val paidCost = SheetFormat.input(paidText)?.takeIf { it > 0 }
                save(initial.copy(expires = next.toString(), lastRenewed = LocalDate.now().toString(), cost = paidCost ?: initial.cost))
                if (paidCost != null && toFinance) {
                    store.saveExpense(null, LocalDate.now().toString(), "${initial.item} renewal", initial.kind.category, "", paidCost, "Renewal")
                }
                renewing = false; onDismiss()
            },
            onDismiss = { renewing = false },
        ) {
            DateField("New expiry", next, allowClear = false) { if (it != null) next = it }
            NumberField("It cost", paidText, Money.main) { paidText = it }
            if ((SheetFormat.input(paidText) ?: 0.0) > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { toFinance = !toFinance }) {
                    androidx.compose.material3.Checkbox(toFinance, { toFinance = it })
                    Text("Add to Finance as ${initial.kind.category}", fontSize = 14.sp, color = SheetTheme.text)
                }
            }
        }
    }
}

/** Expired or expiring within 30 days, on the Dashboard. */
@Composable
fun RenewalsDueCard(snapshot: SheetSnapshot, onOpen: () -> Unit) {
    val due = snapshot.renewalsDue(30)
    if (due.isEmpty()) return
    SheetCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(Icons.Filled.EventBusy, if (due.any { it.daysLeft() < 0 }) SheetTheme.bad else SheetTheme.warn, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Renewals coming up", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                due.take(3).forEach { r ->
                    Text("${r.item} · ${Renewal.left(r.daysLeft())}", fontSize = 14.sp, color = if (r.daysLeft() < 0) SheetTheme.bad else SheetTheme.text)
                }
            }
            Text("›", fontSize = 18.sp, color = SheetTheme.tertiary)
        }
    }
}

// MARK: - Loans

class LoansUi { var editing by mutableStateOf<Pair<Loan, Boolean>?>(null) }

fun LazyListScope.loanItems(snapshot: SheetSnapshot, ui: LoansUi) {
    val loans = snapshot.loans.sortedWith(compareBy<Loan>({ it.closed }, { it.lastPayment }))
    item {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                SectionTitle("Loans & EMIs", if (snapshot.activeLoans.isEmpty()) null
                    else "${SheetFormat.qar(snapshot.monthlyEMI)} ${Money.main} a month · ${SheetFormat.qar(snapshot.activeLoans.sumOf { it.remaining() })} left")
            }
            IconButton({ ui.editing = Loan() to true }) { Icon(Icons.Filled.AddCircle, "Add a loan", tint = SheetTheme.navy) }
        }
    }
    if (loans.isEmpty()) item {
        SheetCard {
            Text("Car loan, phone on instalments, a personal loan? Add it with the monthly amount and how many months — you'll see what's left and the date it's paid off.",
                fontSize = 14.sp, color = SheetTheme.secondary)
        }
    }
    items(loans, key = { "loan-${it.id}" }) { loan ->
        val done = loan.closed || loan.left() == 0
        val tint = if (done) SheetTheme.good else SheetTheme.indigo
        SheetCard(onClick = { ui.editing = loan to false }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(if (done) Icons.Filled.Verified else Icons.Filled.CreditCard, tint, 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(loan.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                        Text(listOfNotNull(loan.lender, "${SheetFormat.qar(loan.emi)} a month").joinToString(" · "), fontSize = 12.sp, color = SheetTheme.secondary)
                    }
                    Text(if (done) "Paid off" else "${loan.paidCount()}/${loan.months}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tint)
                }
                Bar(loan.fraction(), tint)
                if (!done) {
                    Row {
                        Text("${SheetFormat.qar(loan.remaining())} ${Money.main} left · ${loan.left()} payment${if (loan.left() == 1) "" else "s"}",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, modifier = Modifier.weight(1f))
                        Text("Paid off ${runCatching { LocalDate.parse(loan.lastPayment).format(DateTimeFormatter.ofPattern("MMM yyyy")) }.getOrDefault(loan.lastPayment)}",
                            fontSize = 12.sp, color = SheetTheme.secondary)
                    }
                    loan.nextPayment()?.let {
                        Text("Next: ${SheetDate.short(it)}" + if (loan.bill) " · added to Finance on the day" else " · not in Finance",
                            fontSize = 12.sp, color = if (loan.bill) SheetTheme.secondary else SheetTheme.warn)
                    }
                }
            }
        }
    }
}

@Composable
fun LoanSheets(store: SheetStore, ui: LoansUi) {
    ui.editing?.let { (l, isNew) -> LoanEditorSheet(store, l, isNew) { ui.editing = null } }
}

@Composable
private fun LoanEditorSheet(store: SheetStore, initial: Loan, isNew: Boolean, onDismiss: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var lender by remember { mutableStateOf(initial.lender ?: "") }
    var emi by remember { mutableStateOf(if (initial.emi > 0) SheetFormat.plain(initial.emi) else "") }
    var months by remember { mutableStateOf(initial.months.toString()) }
    var first by remember { mutableStateOf(runCatching { LocalDate.parse(initial.firstPayment) }.getOrDefault(LocalDate.now())) }
    var paid by remember { mutableStateOf(initial.paid?.toString() ?: "") }
    var principal by remember { mutableStateOf(initial.principal?.let { SheetFormat.plain(it) } ?: "") }
    var rate by remember { mutableStateOf(initial.rate?.let { SheetFormat.plain(it) } ?: "") }
    var closed by remember { mutableStateOf(initial.closed) }
    var bill by remember { mutableStateOf(initial.bill) }
    var confirmDelete by remember { mutableStateOf(false) }
    val amount = SheetFormat.input(emi)?.takeIf { it > 0 }
    val count = months.toIntOrNull()?.takeIf { it in 1..600 }
    EditorSheet(if (isNew) "New loan" else initial.name, onDismiss, canSave = name.isNotBlank() && amount != null && count != null, onSave = {
        val saved = initial.copy(name = name.trim(), lender = lender.trim().ifEmpty { null }, emi = amount!!, months = count!!,
            firstPayment = first.toString(), paid = paid.toIntOrNull(), principal = SheetFormat.input(principal), rate = SheetFormat.input(rate),
            closed = closed, bill = bill)
        store.saveLoan(saved)
        onDismiss()
    }) {
        Field("Loan") { PlainField(name, "e.g. Car loan, iPhone instalments") { name = it } }
        Field("Bank or shop (optional)") { PlainField(lender, "e.g. QNB") { lender = it } }
        NumberField("Monthly instalment", emi, Money.main) { emi = it }
        NumberField("Months", months, "mo") { months = it.filter { c -> c.isDigit() } }
        DateField("First payment", first, allowClear = false) { if (it != null) first = it }
        if (amount != null && count != null) {
            Text("${SheetFormat.qar(amount * count)} ${Money.main} in all · last payment ${SheetDate.short(first.plusMonths((count - 1).toLong()).toString())}",
                fontSize = 12.sp, color = SheetTheme.secondary)
        }
        NumberField("Paid so far (optional)", paid, "of ${count ?: "—"}") { paid = it.filter { c -> c.isDigit() } }
        Text("Leave empty to count one a month from the first payment.", fontSize = 12.sp, color = SheetTheme.secondary)
        NumberField("Amount borrowed (optional)", principal, Money.main) { principal = it }
        NumberField("Interest (optional)", rate, "% a year") { rate = it }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card)) {
            ToggleRow("Monthly bill", bill, if (bill) "Each instalment is added to Finance on its day (Essentials), and stops after the last one"
                else "Instalments won't show in Finance") { bill = it }
        }
        if (!isNew) {
            SheetCard(onClick = { closed = !closed }) { Text(if (closed) "✓ Closed / paid off" else "Mark as closed / paid off", color = SheetTheme.navy, fontWeight = FontWeight.SemiBold) }
            SheetCard(onClick = { confirmDelete = true }) { Text("Delete loan", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold) }
        }
    }
    if (confirmDelete) AppDialog(
        title = "Delete ${initial.name}?",
        message = if (initial.bill) "Its monthly bill stops. Instalments already logged stay in Finance." else null,
        style = DialogStyle.DESTRUCTIVE,
        confirmText = "Delete",
        onConfirm = { confirmDelete = false; store.deleteLoan(initial.id); onDismiss() },
        onDismiss = { confirmDelete = false },
    )
}

// MARK: - Week in review

@Composable
fun WeeklyReviewScreen(store: SheetStore, onBack: () -> Unit, onPlanNextWeek: (LocalDate) -> Unit) {
    var monday by remember { mutableStateOf(WeeklyReview.reviewWeek()) }
    SheetScreen("Week in review", isRefreshing = store.isLoading, onRefresh = { store.refresh() }, onBack = onBack) {
        val s = store.snapshot ?: return@SheetScreen
        val r = WeeklyReview(s, monday)
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ monday = monday.minusWeeks(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous week", tint = SheetTheme.navy) }
                Text("${monday.format(DateTimeFormatter.ofPattern("d MMM"))} – ${monday.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM"))}",
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.navy, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                val thisMonday = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())
                IconButton({ if (monday < thisMonday) monday = monday.plusWeeks(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next week", tint = SheetTheme.navy) }
            }
        }
        item {
            HeroBox {
                val a = r.averageScore
                Text(when { a == null -> "A quiet week"; a >= 0.85 -> "A great week 🌟"; a >= 0.7 -> "A solid week 👍"; else -> "A week to build on 💪" },
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(r.summary, fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
            }
        }
        item {
            ReviewTile(Icons.Filled.Checklist, SheetTheme.navy, "Habits", r.averageScore?.let { SheetFormat.percent(it, 0) } ?: "—",
                "${r.logged} of 7 days logged · ${r.greatDays} great day${if (r.greatDays == 1) "" else "s"}" + (r.bestDay?.let { " · best ${SheetDate.weekdayDay(it.first)}" } ?: ""))
        }
        item {
            ReviewTile(Icons.Filled.Payments, if (r.budget?.let { r.spent <= it } != false) SheetTheme.good else SheetTheme.bad, "Money",
                "${SheetFormat.qar(r.spent)} ${Money.main}",
                listOfNotNull(r.budget?.let { b -> if (r.spent <= b) "${SheetFormat.qar(b - r.spent)} under the week's budget" else "${SheetFormat.qar(r.spent - b)} over the week's budget" },
                    r.topCategory?.let { "most on ${it.first} (${SheetFormat.qar(it.second)})" }).joinToString(" · "))
        }
        item {
            ReviewTile(Icons.Filled.School, SheetTheme.purple, "Study", StudyFormat.minutes(r.studyMinutes),
                "${r.studySessions} session${if (r.studySessions == 1) "" else "s"}" + (r.studyTarget?.let { " · target ${StudyFormat.minutes(it)}" } ?: ""),
                r.studyTarget?.let { if (it > 0) r.studyMinutes / it else 0.0 })
        }
        item {
            ReviewTile(Icons.AutoMirrored.Filled.DirectionsWalk, SheetTheme.good, "Steps", "%,d".format(r.steps),
                (if (r.stepDays > 0) "${"%,d".format(r.steps / r.stepDays)} a day · goal hit ${r.stepGoalDays} day${if (r.stepGoalDays == 1) "" else "s"}" else "No steps recorded") +
                    (r.averageSleep?.let { " · sleep ${"%.1f".format(it)} h" } ?: ""))
        }
        item {
            ReviewTile(Icons.Filled.Flag, SheetTheme.indigo, "Goals", "${r.goalStepsDone} step${if (r.goalStepsDone == 1) "" else "s"}",
                if (r.goalsReached.isEmpty()) "ticked off toward your goals" else "Reached: ${r.goalsReached.joinToString(", ")} 🎉")
        }
        if (r.plansTotal > 0) item { ReviewTile(Icons.Filled.CalendarMonth, SheetTheme.teal, "Plans", "${r.plansDone}/${r.plansTotal}", "plans done") }
        item {
            PrimaryButton("Plan next week", Modifier.fillMaxWidth()) {
                val thisMonday = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())
                onPlanNextWeek(maxOf(monday.plusWeeks(1), thisMonday))
            }
        }
    }
}

@Composable
private fun ReviewTile(icon: ImageVector, tint: Color, title: String, value: String, detail: String, fraction: Double? = null) {
    SheetCard {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(icon, tint, 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary, modifier = Modifier.weight(1f))
                    Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text)
                }
                fraction?.let { Bar(it.coerceIn(0.0, 1.0), tint) }
                if (detail.isNotEmpty()) Text(detail, fontSize = 12.sp, color = SheetTheme.secondary)
            }
        }
    }
}

/** On the Dashboard from Friday evening to Monday. */
@Composable
fun WeeklyReviewCard(onOpen: () -> Unit) {
    val now = LocalDateTime.now()
    val show = now.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY) || (now.dayOfWeek == DayOfWeek.FRIDAY && now.hour >= 17)
    if (!show) return
    SheetCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.InsertChart, SheetTheme.indigo, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Your week in review", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                Text("Habits, money, study, steps and goals — then plan next week.", fontSize = 12.sp, color = SheetTheme.secondary)
            }
            Text("›", fontSize = 18.sp, color = SheetTheme.tertiary)
        }
    }
}

// MARK: - Year in review

@Composable
fun YearReviewScreen(store: SheetStore, onBack: () -> Unit) {
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    var numbers by remember { mutableStateOf<YearNumbers?>(null) }
    var loading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(year) {
        loading = true
        note = null
        numbers = runCatching { YearNumbers.from(store.yearReview(year)) }.getOrNull() ?: store.snapshot?.let {
            note = if (store.isDemo) "Sample data: this period only." else "Couldn't reach the sheet — showing this period only."
            YearNumbers.local(it, year)
        }
        loading = false
    }
    SheetScreen("Year in review", isRefreshing = false, onRefresh = {}, onBack = onBack) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ year-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous year", tint = SheetTheme.navy) }
                Text(year.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SheetTheme.navy, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton({ if (year < LocalDate.now().year) year++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next year", tint = SheetTheme.navy) }
            }
        }
        note?.let { item { Text(it, fontSize = 12.sp, color = SheetTheme.secondary) } }
        if (loading) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        val n = numbers
        val s = store.snapshot
        if (!loading && n != null && s != null) yearCards(n, s)
    }
}

private fun LazyListScope.yearCards(n: YearNumbers, s: SheetSnapshot) {
    val y = n.year.toString()
    val study = s.studyData
    val studyMinutes = study.sessions.filter { it.date.startsWith(y) }.sumOf { it.minutes }
    val learned = study.topics.count { (it.lastStudied ?: "").startsWith(y) && (it.stage == StudyTopic.Stage.MASTERED || it.stage == StudyTopic.Stage.REVISING) }
    val saved = s.setAside.filter { it.date.startsWith(y) }.sumOf { it.amount }
    val goalsReached = s.lifeGoalData.goals.filter { it.status == GoalStatus.REACHED && (it.doneOn ?: "").startsWith(y) }.map { it.title } +
        s.savingsGoals.filter { it.done && (it.doneOn ?: "").startsWith(y) }.map { it.name }
    val trips = s.shoppingData.trips.filter { it.date.startsWith(y) }
    item {
        HeroBox(Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF1F3864)))) {
            Text("Your $y", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("${n.logged} days logged · ${n.greatDays} great days · best streak ${n.bestStreak}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
            n.averageScore?.let { Text("Average habit score ${SheetFormat.percent(it, 0)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
        }
    }
    item {
        BigStat(Icons.AutoMirrored.Filled.DirectionsWalk, SheetTheme.good, "%,d".format(n.steps.toLong()), "steps", listOfNotNull(
            if (n.stepDays > 0) "${"%,d".format((n.steps / n.stepDays).toLong())} a day" else null,
            n.bestStepsDay?.let { "best ${"%,d".format(it.second.toLong())} on ${SheetDate.short(it.first)}" },
            if (n.workouts > 0) "${n.workouts} workouts" else null).joinToString(" · "))
    }
    if (n.averageSleep != null || n.weightFrom != null) item {
        val weight = n.weightFrom?.let { a -> n.weightTo?.let { b ->
            if (kotlin.math.abs(b - a) < 0.05) "Weight ${"%.1f".format(b)} kg" else "Weight ${"%.1f".format(a)} → ${"%.1f".format(b)} kg (${if (b - a >= 0) "+" else ""}${"%.1f".format(b - a)})" } } ?: ""
        BigStat(Icons.Filled.Bedtime, SheetTheme.indigo, n.averageSleep?.let { "%.1f h".format(it) } ?: "—", "average sleep", weight)
    }
    item {
        BigStat(Icons.Filled.TrendingDown, SheetTheme.bad, "${SheetFormat.qar(n.spent)} ${Money.main}", "spent", listOfNotNull(
            n.byCategory.firstOrNull()?.let { "most on ${it.first} (${SheetFormat.qar(it.second)})" },
            n.biggest?.let { "biggest: ${it.first} ${SheetFormat.qar(it.second)}" }, "${n.noSpendDays} no-spend days").joinToString(" · "))
    }
    if (n.byMonth.isNotEmpty()) item { MonthBars(n.byMonth) }
    item { BigStat(Icons.Filled.Payments, SheetTheme.teal, "${SheetFormat.qar(n.income)} ${Money.main}", "earned", "${SheetFormat.qar(n.remitted)} sent home · ${SheetFormat.qar(saved)} put aside") }
    item { BigStat(Icons.Filled.School, SheetTheme.purple, StudyFormat.minutes(studyMinutes), "studied", "$learned concepts learned") }
    item { BigStat(Icons.Filled.Flag, SheetTheme.indigo, goalsReached.size.toString(), "goals reached", goalsReached.take(4).joinToString(", ")) }
    if (trips.isNotEmpty()) item {
        BigStat(Icons.Filled.ShoppingCart, SheetTheme.good, trips.size.toString(), "shopping trips", "${SheetFormat.qar(trips.sumOf { it.total ?: 0.0 })} ${Money.main} on groceries")
    }
}

@Composable
private fun BigStat(icon: ImageVector, tint: Color, value: String, label: String, detail: String) {
    SheetCard {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(icon, tint, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Text(label, fontSize = 14.sp, color = SheetTheme.secondary)
                }
                if (detail.isNotEmpty()) Text(detail, fontSize = 12.sp, color = SheetTheme.secondary)
            }
        }
    }
}

@Composable
private fun MonthBars(byMonth: List<Pair<Int, Double>>) {
    val top = maxOf(byMonth.maxOfOrNull { it.second } ?: 1.0, 1.0)
    val names = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
    SheetCard {
        Text("Spending by month", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(100.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..12).forEach { m ->
                val v = byMonth.firstOrNull { it.first == m }?.second ?: 0.0
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(maxOf(4.0, 80 * v / top).dp).clip(RoundedCornerShape(50))
                        .background(if (v > 0) SheetTheme.bad.copy(alpha = 0.75f) else SheetTheme.input))
                    Text(names[m - 1], fontSize = 10.sp, color = SheetTheme.secondary)
                }
            }
        }
    }
}

// MARK: - Notification switches

object SheetAlertPrefs {
    const val BRIEFING = "morningBriefingEnabled"
    const val BILLS = "billRemindersEnabled"
    const val RENEWALS = "renewalRemindersEnabled"
    const val WEEKLY = "weeklyReviewEnabled"
    const val PARENTS = "parentRemindersEnabled"
    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun isOn(context: Context, key: String) = prefs(context).getBoolean(key, true)
    fun set(context: Context, key: String, on: Boolean) = prefs(context).edit().putBoolean(key, on).apply()
}


