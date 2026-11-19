package com.mohammedsharukms.mypersonaltracker.sheet.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mohammedsharukms.mypersonaltracker.sheet.SalaryMonth
import com.mohammedsharukms.mypersonaltracker.sheet.SheetExpense
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

// Plain-language observations worked out from the sheet's data, and a one-page
// monthly report shared as a PDF. Mirrors Insights.swift.

data class Insight(val id: String, val icon: ImageVector, val tone: Tone, val text: String) {
    enum class Tone { GOOD, BAD, NEUTRAL }
}

object InsightEngine {
    fun insights(s: SheetSnapshot, today: LocalDate = LocalDate.now()): List<Insight> {
        val out = mutableListOf<Insight>()
        val main = s.settings.mainCurrency
        fun spend(from: LocalDate, to: LocalDate) = s.expenses.filter { (it.date ?: "") in from.toString()..to.toString() }
        fun total(items: List<SheetExpense>) = items.sumOf { it.qar ?: 0.0 }
        val todayKey = today.toString()

        // 1. This week so far against the same days last week.
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val now = total(spend(weekStart, today))
        val before = total(spend(weekStart.minusDays(7), today.minusDays(7)))
        if (before > 0 && now > 0) {
            val change = (now - before) / before
            if (abs(change) >= 0.15) out += Insight("week",
                if (change > 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                if (change > 0) Insight.Tone.BAD else Insight.Tone.GOOD,
                "Spending is ${if (change > 0) "up" else "down"} ${(abs(change) * 100).roundToInt()}% on this point last week (${SheetFormat.qar(now)} vs ${SheetFormat.qar(before)} $main).")
        }

        // 2. Where this month's money went.
        val month = todayKey.take(7)
        val monthItems = s.expenses.filter { (it.date ?: "").startsWith(month) }
        val monthTotal = total(monthItems)
        if (monthTotal > 0) {
            val byCategory = monthItems.groupBy { it.category ?: "Other" }.mapValues { total(it.value) }
            val top = byCategory.maxByOrNull { it.value }
            if (top != null && byCategory.size > 1) out += Insight("category", SheetTheme.categoryIcon(top.key), Insight.Tone.NEUTRAL,
                "${top.key} is your biggest spend this month — ${SheetFormat.qar(top.value)} $main, ${(top.value / monthTotal * 100).roundToInt()}% of the total.")
        }

        // 3. Month on pace against the monthly limit.
        val limit = s.settings.monthlyLimit
        if (limit != null && limit > 0 && monthTotal > 0) {
            val projected = monthTotal / today.dayOfMonth * today.lengthOfMonth()
            out += when {
                monthTotal > limit -> Insight("pace", Icons.Filled.Warning, Insight.Tone.BAD,
                    "You're ${SheetFormat.qar(monthTotal - limit)} $main over this month's limit already.")
                projected > limit * 1.05 -> Insight("pace", Icons.Filled.Speed, Insight.Tone.BAD,
                    "At this pace you'll spend about ${SheetFormat.qar(projected)} $main this month — over the ${SheetFormat.number(limit)} limit.")
                else -> Insight("pace", Icons.Filled.Verified, Insight.Tone.GOOD,
                    "On pace for about ${SheetFormat.qar(projected)} $main this month — inside your ${SheetFormat.number(limit)} limit.")
            }
        }

        // 4. Strongest and weakest habit.
        val scored = s.dashboardTables.firstOrNull()?.rows.orEmpty().mapNotNull { row ->
            val rate = SheetFormat.parse(row.lastOrNull() ?: "") ?: return@mapNotNull null
            (row.firstOrNull() ?: return@mapNotNull null) to rate
        }
        val best = scored.maxByOrNull { it.second }
        val worst = scored.minByOrNull { it.second }
        if (best != null && worst != null && best.second - worst.second >= 0.2) out += Insight("habits", Icons.Filled.TrackChanges, Insight.Tone.NEUTRAL,
            "${worst.first} is the habit to work on (${SheetFormat.percent(worst.second, 0)}). ${best.first} is your strongest (${SheetFormat.percent(best.second, 0)}).")

        // 5. Unlogged days in the last week.
        val weekAgo = today.minusDays(7).toString()
        val missed = s.days.count { it.date >= weekAgo && it.date < todayKey && !it.isLogged }
        if (missed >= 2) out += Insight("missed", Icons.Filled.EventBusy, Insight.Tone.BAD,
            "$missed of the last 7 days aren't logged — the Daily tab's “Not logged” filter finds them.")

        // 6. Days over the cap this week.
        val cap = s.settings.dailyLimit
        if (cap != null && cap > 0) {
            val byDay = s.expenses.filter { (it.date ?: "") in weekAgo..todayKey }.groupBy { it.date }.mapValues { total(it.value) }
            val over = byDay.values.count { it > cap }
            if (over == 0 && byDay.isNotEmpty()) out += Insight("cap", Icons.Filled.ThumbUp, Insight.Tone.GOOD, "No days over your daily cap in the last week.")
            else if (over >= 3) out += Insight("cap", Icons.Filled.LocalFireDepartment, Insight.Tone.BAD,
                "$over days over your ${SheetFormat.number(cap)} $main daily cap in the last week.")
        }
        return out.take(5)
    }
}

@Composable
fun InsightsCard(snapshot: SheetSnapshot) {
    val insights = remember(snapshot) { InsightEngine.insights(snapshot) }
    if (insights.isEmpty()) return
    SheetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, tint = SheetTheme.text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Insights", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            insights.forEach { insight ->
                Row(verticalAlignment = Alignment.Top) {
                    IconBadge(insight.icon, when (insight.tone) {
                        Insight.Tone.GOOD -> SheetTheme.good
                        Insight.Tone.BAD -> SheetTheme.bad
                        Insight.Tone.NEUTRAL -> SheetTheme.indigo
                    }, 30.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(insight.text, fontSize = 15.sp, color = SheetTheme.text)
                }
            }
        }
    }
}

// MARK: - Monthly report

class MonthlyReport(s: SheetSnapshot, key: String) {
    val month: SalaryMonth? = s.salary.firstOrNull { it.month == key }
    val label: String = month?.label ?: key
    val currency: String = s.settings.mainCurrency
    private val items = s.expenses.filter { (it.date ?: "").startsWith(key) }
    val spent: Double = items.sumOf { it.qar ?: 0.0 }
    val categories: List<Pair<String, Double>> = items.groupBy { it.category ?: "Other" }
        .mapValues { e -> e.value.sumOf { it.qar ?: 0.0 } }.toList().sortedByDescending { it.second }
    private val days = s.days.filter { it.date.startsWith(key) && it.date <= SheetDate.today }
    val daysInData = days.size
    private val logged = days.filter { it.isLogged }
    val daysLogged = logged.size
    val averageScore: Double? = if (logged.isEmpty()) null else logged.sumOf { it.score ?: 0.0 } / logged.size
    val excellentDays = logged.count { it.status == "Excellent" }
    val overCapDays = items.groupBy { it.date }.values.count { d -> d.sumOf { it.qar ?: 0.0 } > (s.settings.dailyLimit ?: Double.MAX_VALUE) }
    val biggest: SheetExpense? = items.maxByOrNull { it.qar ?: 0.0 }
}

@Composable
fun MonthlyReportScreen(store: SheetStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val snapshot = store.snapshot
    val months = remember(snapshot) {
        (snapshot?.expenses.orEmpty().mapNotNull { it.date?.take(7) } + snapshot?.days.orEmpty().filter { it.isLogged }.map { it.date.take(7) } +
            SheetDate.today.take(7)).toSortedSet().reversed()
    }
    var month by remember { mutableStateOf(SheetDate.today.take(7)) }
    SheetScreen("Monthly report", isRefreshing = store.isLoading, onRefresh = { store.refresh() }, onBack = onBack, actions = {
        if (snapshot != null) IconButton({ shareReportPdf(context, MonthlyReport(snapshot, month)) }) {
            Icon(Icons.Filled.Share, "Share PDF", tint = SheetTheme.navy)
        }
    }) {
        if (snapshot == null) { item { NotConnectedHint() }; return@SheetScreen }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                months.forEach { key ->
                    FilterPill(snapshot.salary.firstOrNull { it.month == key }?.label ?: key, null, SheetTheme.navy, key == month) { month = key }
                }
            }
        }
        val report = MonthlyReport(snapshot, month)
        item {
            HeroBox {
                Text("Monthly report · ${report.currency}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                Text(report.label, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.AutoMirrored.Filled.TrendingUp, SheetTheme.teal, "Income", SheetFormat.qar(report.month?.totalIncome), Modifier.weight(1f))
                StatTile(Icons.AutoMirrored.Filled.TrendingDown, SheetTheme.bad, "Spent", SheetFormat.qar(report.spent), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Filled.Share, SheetTheme.sky, "Sent home", SheetFormat.qar(report.month?.remittanceQar), Modifier.weight(1f))
                StatTile(Icons.Filled.Verified, SheetTheme.good, "Saved", SheetFormat.qar(report.month?.netSavings), Modifier.weight(1f))
            }
        }
        item {
            SheetCard {
                Text("Habits", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                KeyValueRow("Days logged", "${report.daysLogged} of ${report.daysInData}")
                KeyValueRow("Average daily score", report.averageScore?.let { SheetFormat.percent(it) } ?: "—")
                KeyValueRow("Excellent days", "${report.excellentDays}")
            }
        }
        item {
            SheetCard {
                Text("Spending", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                KeyValueRow("Savings rate", report.month?.savingsRate?.let { SheetFormat.percent(it) } ?: "—")
                KeyValueRow("Days over the daily cap", "${report.overCapDays}", if (report.overCapDays > 0) SheetTheme.bad else SheetTheme.text)
                report.biggest?.let { KeyValueRow("Biggest expense", "${it.description ?: it.category ?: ""} · ${SheetFormat.qar(it.qar)}") }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    report.categories.take(6).forEach { (category, amount) ->
                        ProgressRow(category, SheetFormat.qar(amount), if (report.spent > 0) amount / report.spent else 0.0,
                            SheetTheme.categoryColor(category), SheetTheme.categoryIcon(category))
                    }
                }
                if (report.categories.isEmpty()) Text("No expenses this month.", color = SheetTheme.secondary)
            }
        }
    }
}

/** Draws the report onto an A4 page and opens the share sheet. */
fun shareReportPdf(context: Context, r: MonthlyReport) {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val c = page.canvas
    val navy = android.graphics.Color.rgb(31, 56, 100)
    val title = Paint().apply { color = navy; textSize = 26f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    val head = Paint().apply { color = navy; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    val body = Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 12f; isAntiAlias = true }
    val value = Paint(body).apply { color = android.graphics.Color.BLACK; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
    val bar = Paint().apply { color = android.graphics.Color.rgb(15, 118, 110) }
    val track = Paint().apply { color = android.graphics.Color.rgb(235, 235, 240) }
    var y = 60f
    c.drawText("Life & Finance · Monthly report (${r.currency})", 40f, y, body); y += 32
    c.drawText(r.label, 40f, y, title); y += 36
    fun row(k: String, v: String) { c.drawText(k, 40f, y, body); c.drawText(v, 555f, y, value); y += 20 }
    fun section(name: String) { y += 10; c.drawText(name, 40f, y, head); y += 22 }
    section("Money")
    row("Income", SheetFormat.qar(r.month?.totalIncome))
    row("Spent", SheetFormat.qar(r.spent))
    row("Sent home", SheetFormat.qar(r.month?.remittanceQar))
    row("Saved", SheetFormat.qar(r.month?.netSavings))
    row("Savings rate", r.month?.savingsRate?.let { SheetFormat.percent(it) } ?: "—")
    section("Habits")
    row("Days logged", "${r.daysLogged} of ${r.daysInData}")
    row("Average daily score", r.averageScore?.let { SheetFormat.percent(it) } ?: "—")
    row("Excellent days", "${r.excellentDays}")
    row("Days over the daily cap", "${r.overCapDays}")
    r.biggest?.let { row("Biggest expense", "${it.description ?: it.category ?: ""} · ${SheetFormat.qar(it.qar)}") }
    section("Where the money went")
    r.categories.take(10).forEach { (category, amount) ->
        row(category, SheetFormat.qar(amount))
        val fraction = if (r.spent > 0) (amount / r.spent).toFloat() else 0f
        c.drawRect(40f, y - 12, 555f, y - 6, track)
        c.drawRect(40f, y - 12, 40f + 515f * fraction, y - 6, bar)
        y += 6
    }
    y = 810f
    c.drawText("Generated ${LocalDate.now()} from your Google Sheet.", 40f, y, body)
    doc.finishPage(page)

    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(dir, "Life & Finance ${r.label}.pdf")
    file.outputStream().use { doc.writeTo(it) }
    doc.close()
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share report"))
}
