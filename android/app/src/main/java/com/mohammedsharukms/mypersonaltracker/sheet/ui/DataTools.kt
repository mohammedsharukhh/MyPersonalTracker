package com.mohammedsharukms.mypersonaltracker.sheet.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.studyData
import com.mohammedsharukms.mypersonaltracker.sheet.shoppingData
import com.mohammedsharukms.mypersonaltracker.sheet.subject
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.progress
import com.mohammedsharukms.mypersonaltracker.sheet.plannerData
import com.mohammedsharukms.mypersonaltracker.sheet.lifeGoalData
import com.mohammedsharukms.mypersonaltracker.sheet.renewalList
import com.mohammedsharukms.mypersonaltracker.sheet.readingList
import com.mohammedsharukms.mypersonaltracker.sheet.takeHomeData
import com.mohammedsharukms.mypersonaltracker.sheet.goal
import com.mohammedsharukms.mypersonaltracker.sheet.progressOf
import com.mohammedsharukms.mypersonaltracker.sheet.pieces
import com.mohammedsharukms.mypersonaltracker.sheet.timesWorn
import com.mohammedsharukms.mypersonaltracker.sheet.foodData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Your data: export everything as spreadsheet files (CSV, in one zip) to keep before
// a reset or before uninstalling, and reset everything to start fresh. Mirrors DataTools.swift.

object DataExport {
    /** What's on the phone, for when the sheet can't be reached (food: last ~2 months). */
    fun tables(s: SheetSnapshot?): List<Pair<String, List<List<String>>>> {
        if (s == null) return emptyList()
        fun n(v: Double?) = SheetFormat.plain(v)
        val main = s.settings.mainCurrency
        val home = s.settings.homeCurrency
        val food = s.foodData
        val study = s.studyData
        val shop = s.shoppingData
        return listOf(
            "Daily Tracker" to listOf(listOf("Date", "Office", "Breakfast", "Lunch", "Protein", "Workout", "Water", "Sleep", "Steps", "Weight", "Screen", "Notes", "Score", "Status")) +
                s.days.map { listOf(it.date, it.office ?: "", it.breakfast ?: "", it.lunch ?: "", it.protein ?: "", it.workout ?: "", n(it.water), n(it.sleep), n(it.steps), n(it.weight), n(it.screen), it.notes ?: "", n(it.score), it.status ?: "") },
            "Expenses" to listOf(listOf("Date", "Description", "Category", "Payment Method", "Amount ($main)", "Amount ($home)", "Notes")) +
                s.expenses.filter { it.row > 0 }.map { listOf(it.date ?: "", it.description ?: "", it.category ?: "", it.method ?: "", n(it.qar), n(it.inr), it.notes ?: "") },
            "Salary & Savings" to listOf(listOf("Month", "Salary", "Other Income", "Total Income", "Expenses", "Sent Home", "Net Savings", "Savings Rate")) +
                s.salary.map { listOf(it.label, n(it.salary), n(it.otherIncome), n(it.totalIncome), n(it.expenses), n(it.remittanceQar), n(it.netSavings), n(it.savingsRate)) },
            "Food Log" to listOf(listOf("Date", "Time", "Meal", "Food", "Amount", "Unit", "Calories", "Protein", "Carbs", "Fat", "Cost", "Notes")) +
                food.log.map { listOf(it.date, it.time ?: "", it.meal, it.food, n(it.amount), it.unit ?: "", n(it.calories), n(it.protein), n(it.carbs), n(it.fat), n(it.cost), it.notes ?: "") },
            "Meal Plan" to listOf(listOf("Date", "Meal", "Food", "Amount", "Unit", "Calories", "Protein", "Carbs", "Fat", "Est. Cost", "Eaten")) +
                food.plan.map { listOf(it.date, it.meal, it.food, n(it.amount), it.unit ?: "", n(it.calories), n(it.protein), n(it.carbs), n(it.fat), n(it.cost), if (it.eaten) "Y" else "") },
            "My Foods" to listOf(listOf("Food", "Amount", "Unit", "Calories", "Protein", "Carbs", "Fat", "Cost")) +
                food.myFoods.map { listOf(it.name, n(it.amount), it.unit ?: "", n(it.calories), n(it.protein), n(it.carbs), n(it.fat), n(it.cost)) },
            "People" to listOf(listOf("Date", "Person", "Type", "Amount", "Note")) +
                s.people.map { listOf(it.date, it.person, it.type.label, n(it.amount), it.note ?: "") },
            "Monthly Bills" to listOf(listOf("Description", "Category", "Payment Method", "Amount", "Day of Month", "Active", "Last Added", "Notes")) +
                s.recurring.map { listOf(it.description ?: "", it.category ?: "", it.method ?: "", n(it.amount), it.day?.toString() ?: "", if (it.active) "Y" else "N", it.lastAdded ?: "", it.notes ?: "") },
            "Set Aside" to listOf(listOf("Date", "Bucket", "Amount", "Note", "Goal")) +
                s.setAside.map { e -> listOf(e.date, e.bucket, n(e.amount), e.note ?: "", s.savingsGoals.firstOrNull { it.id == e.goalId }?.name ?: "") },
            "Savings Goals" to listOf(listOf("Goal", "Target", "Saved", "Due", "Created", "Done", "Done On", "Notes")) +
                s.savingsGoals.map { listOf(it.name, n(it.target), n(s.progress(it).saved), it.due ?: "", it.created ?: "", if (it.done) "Y" else "N", it.doneOn ?: "", it.notes ?: "") },
            "Renewals" to listOf(listOf("Item", "Kind", "Expires", "Remind (days before)", "Renewal cost", "Last Renewed", "Notes")) +
                s.renewalList.map { listOf(it.item, it.kind.label, it.expires, it.remind.joinToString(","), n(it.cost), it.lastRenewed ?: "", it.notes ?: "") },
            "Parents" to listOf(listOf("What they asked", "Who", "Do on", "Remind at", "Repeat", "Keep reminding", "Asked on", "Done on", "Notes", "Photos", "Cost", "Add to Finance")) +
                s.parents.sortedBy { it.date }.map { listOf(it.task, it.who.raw, it.date, it.time ?: "", it.repeats.label, if (it.nag) "Y" else "", it.asked ?: "", it.done ?: "", it.notes ?: "",
                    it.photos.size.toString(), n(it.cost), if (it.finance) "Y" else "") },
            "Parent Medicines" to listOf(listOf("Who", "Medicine", "Dose", "A day", "Left", "Counted on", "Warn (days before)", "Pack of", "Pack cost", "Add to Finance", "Stopped", "Notes")) +
                s.parentMedicines.map { listOf(it.who.raw, it.name, it.dose ?: "", n(it.perDay), n(it.left), it.counted, it.warn.toString(), n(it.pack), n(it.cost),
                    if (it.finance) "Y" else "", if (it.stopped) "Y" else "", it.notes ?: "") },
            "Parent Dates" to listOf(listOf("What", "Who", "Kind", "Day (MM-DD)", "Year", "Remind (days before)", "Notes")) +
                s.parentDates.map { listOf(it.title, it.who.raw, it.kind.label, it.day, it.year?.toString() ?: "", it.remind.joinToString(","), it.notes ?: "") },
            "Take Home" to listOf(listOf("What to bring", "For", "Got it", "Notes", "Added")) +
                s.takeHomeData.sorted.map { listOf(it.item, it.who.raw, if (it.got) "Y" else "", it.notes ?: "", it.added ?: "") },
            "Parent Health" to listOf(listOf("Date", "Time", "Who", "Reading", "Value", "Value 2", "Note")) +
                s.readingList.map { listOf(it.date, it.time ?: "", it.who.raw, it.kind.label, n(it.value), n(it.value2), it.note ?: "") },
            "Loans" to listOf(listOf("Loan", "Lender", "Monthly", "First payment", "Months", "Paid", "Left", "Paid off", "Borrowed", "Interest %", "Closed")) +
                s.loans.map { listOf(it.name, it.lender ?: "", n(it.emi), it.firstPayment, it.months.toString(), it.paidCount().toString(), n(it.remaining()),
                    it.lastPayment, n(it.principal), n(it.rate), if (it.closed) "Y" else "") },
            "Life Goals" to listOf(listOf("Goal", "Horizon", "Area", "Why", "By", "Part of", "Status", "Progress", "Created", "Reached On", "Notes")) +
                s.lifeGoalData.let { d -> d.goals.map { g -> listOf(g.title, g.horizon.label, g.area.label, g.why ?: "", g.due ?: "", d.goal(g.parentId)?.title ?: "",
                    g.status.label, SheetFormat.percent(d.progressOf(g), 0), g.created ?: "", g.doneOn ?: "", g.notes ?: "") } },
            "Goal Steps" to listOf(listOf("Goal", "Step", "Done", "Done On")) +
                s.lifeGoalData.let { d -> d.steps.map { st -> listOf(d.goal(st.goalId)?.title ?: "", st.step, if (st.done) "Y" else "", st.doneOn ?: "") } },
            "Planner" to listOf(listOf("Date", "Time", "Plan", "Where", "Notes", "Done")) +
                s.plannerData.plans.sortedWith(compareBy({ it.date }, { it.time ?: "" }))
                    .map { listOf(it.date, it.time ?: "", it.title, it.place ?: "", it.notes ?: "", if (it.done) "Y" else "") },
            "Outfits" to listOf(listOf("Date", "Clothes", "Note")) +
                s.plannerData.outfits.sortedBy { it.date }.map { listOf(it.date, s.plannerData.pieces(it).joinToString(" + "), it.note ?: "") },
            "Wardrobe" to listOf(listOf("Item", "Type", "Colour", "Notes", "Added", "Times Worn")) +
                s.plannerData.wardrobe.map { listOf(it.name, it.type.label, it.colour ?: "", it.notes ?: "", it.added ?: "", s.plannerData.timesWorn(it.id).toString()) },
            "Shopping" to listOf(listOf("Item", "Quantity", "Aisle", "Status", "Price", "Added", "Bought On", "Notes")) +
                shop.items.map { listOf(it.item, it.quantity ?: "", it.category, it.status.label, n(it.price), it.added ?: "", it.boughtOn ?: "", it.notes ?: "") },
            "Shopping Trips" to listOf(listOf("Date", "Shop", "Items", "Total", "Paid With", "Notes")) +
                shop.trips.map { listOf(it.date, it.store ?: "", it.items.toString(), n(it.total), it.method ?: "", it.notes ?: "") },
            "Study Subjects" to listOf(listOf("Subject", "Why", "Book, course or link", "Weekly Target (h)", "Target Date", "Status", "Started")) +
                study.subjects.map { listOf(it.name, it.why ?: "", it.resource ?: "", n(it.weeklyHours), it.targetDate ?: "", it.status.label, it.started ?: "") },
            "Study Topics" to listOf(listOf("Subject", "Topic", "Stage", "Confidence", "Last Studied", "Next Review", "Reviews", "Notes", "Link")) +
                study.topics.map { listOf(study.subject(it.subjectId)?.name ?: "", it.topic, it.stage.label, it.confidence?.toString() ?: "", it.lastStudied ?: "",
                    it.nextReview ?: "", it.reviews.toString(), it.notes ?: "", it.link ?: "") },
            "Study Log" to listOf(listOf("Date", "Subject", "Topic", "Minutes", "Kind", "Focus", "Notes", "Score")) +
                study.sessions.map { listOf(it.date, it.subject ?: "", it.topic ?: "", n(it.minutes), it.kind.label, it.focus?.toString() ?: "", it.notes ?: "", n(it.score)) },
            "Study Goals" to listOf(listOf("Goal", "Kind", "Subject", "Target", "Start", "Due", "Done", "Done On", "Notes")) +
                study.goals.map { listOf(it.title, it.kind.label, study.subject(it.subjectId)?.name ?: "", n(it.target), it.start ?: "", it.due ?: "",
                    if (it.done) "Y" else "", it.doneOn ?: "", it.notes ?: "") },
        )
    }

    /** One CSV per table (UTF-8 with BOM, so Excel shows ₹ and Arabic correctly), zipped. */
    fun zip(context: Context, tables: List<Pair<String, List<List<String>>>>): File {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "Life & Finance export ${LocalDate.now()}.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            tables.forEach { (name, rows) ->
                zip.putNextEntry(ZipEntry("Life & Finance export ${LocalDate.now()}/$name.csv"))
                val csv = rows.joinToString("\r\n") { row -> row.joinToString(",") { escape(it) } }
                zip.write(("﻿" + csv).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Save or share your data"))
    }

    private fun escape(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

/** More ▸ Your data: export, and reset everything. */
@Composable
fun DataToolsScreen(store: SheetStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var resetting by remember { mutableStateOf(false) }

    fun export() {
        exporting = true
        scope.launch {
            val (tables, complete) = store.exportTables()
            if (tables.isEmpty()) {
                note = "Nothing to export yet — connect your sheet first."
            } else {
                val file = withContext(Dispatchers.IO) { DataExport.zip(context, tables) }
                note = if (complete) "Saved ${file.name}. Choose where to keep it." else "Couldn't reach the sheet, so this export is what's on this phone (food: the last two months)."
                DataExport.share(context, file)
            }
            exporting = false
        }
    }

    SheetScreen("Your data", isRefreshing = false, onRefresh = {}, onBack = onBack) {
        item {
            FormSection("Export", "Every table — days, expenses, budget, salary & savings, summaries, food log, meal plan, your foods and monthly bills — as spreadsheet files (CSV) in one zip. Save it to Files, Google Drive or email before resetting or uninstalling. Meal photos stay in your Google Drive folder.") {
                Row(Modifier.fillMaxWidth().clickable(enabled = !exporting) { export() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Download, null, tint = SheetTheme.navy)
                    Spacer(Modifier.width(12.dp))
                    Text("Export all my data", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.navy, modifier = Modifier.weight(1f))
                    if (exporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
        note?.let { item { Text(it, fontSize = 13.sp, color = SheetTheme.secondary) } }
        item {
            FormSection("Start fresh", "Erases everything in your Google Sheet and on this phone, and starts a new 30-day period from today. Settings go back to defaults; the connection stays.") {
                Row(Modifier.fillMaxWidth().clickable { resetting = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.RestartAlt, null, tint = SheetTheme.bad)
                    Spacer(Modifier.width(12.dp))
                    Text("Reset all data…", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.bad)
                }
            }
        }
    }
    if (resetting) ResetDataSheet(store, onExport = { resetting = false; export() }) { resetting = false; onBack() }
}

@Composable
private fun ResetDataSheet(store: SheetStore, onExport: () -> Unit, onDone: () -> Unit) {
    var confirmation by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ready = confirmation.trim().uppercase() == "RESET"
    EditorSheet("Reset all data", onDismiss = { if (!working) onDone() }, saveLabel = "Erase", canSave = ready && !working, saving = working,
        error = store.errorMessage.takeIf { !working }, onSave = {
            working = true
            scope.launch {
                val ok = store.resetAllData()
                working = false
                if (ok) onDone()
            }
        }) {
        FormSection("This permanently erases", if (store.usesGoogleSheet) "You get a fresh, empty sheet with a 30-day period from today. The old one goes to your Google Drive trash, where you can still get it back for 30 days." else "Your sheet is rebuilt with a fresh 30-day period from today — it takes a few minutes. This can't be undone.") {
            Column(Modifier.padding(16.dp)) {
                Line(Icons.Filled.Checklist, "Every day you logged")
                Line(Icons.Filled.Payments, "Every expense, income entry and monthly bill")
                Line(Icons.Filled.Restaurant, "Every meal, meal plan, saved food and shopping list")
                Line(Icons.Filled.People, "Everything with friends & family")
                Line(Icons.Filled.School, "Every subject, concept, study session and goal")
                Line(Icons.Filled.Archive, "Old period archives, targets and currencies")
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SheetTheme.card).clickable(onClick = onExport).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Download, null, tint = SheetTheme.navy)
            Spacer(Modifier.width(12.dp))
            Text("Export my data first", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
        }
        Field("Type RESET to confirm") { PlainField(confirmation, "RESET") { confirmation = it } }
    }
}

@Composable
private fun Line(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = SheetTheme.bad, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 15.sp, color = SheetTheme.text)
    }
}
