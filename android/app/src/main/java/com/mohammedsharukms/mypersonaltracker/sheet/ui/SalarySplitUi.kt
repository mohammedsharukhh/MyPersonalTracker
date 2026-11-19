package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.sheet.SalaryMonth
import com.mohammedsharukms.mypersonaltracker.sheet.SalarySplit
import com.mohammedsharukms.mypersonaltracker.sheet.SetAsideEntry
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket
import com.mohammedsharukms.mypersonaltracker.sheet.SplitLine
import java.time.LocalDate

// Finance ▸ Budget: this month's salary, split into the five buckets. Mirrors SalarySplit.swift.

fun bucketIcon(b: SplitBucket): ImageVector = when (b) {
    SplitBucket.ESSENTIALS -> Icons.Filled.Home
    SplitBucket.EMERGENCY -> Icons.Filled.HealthAndSafety
    SplitBucket.SAVINGS -> Icons.AutoMirrored.Filled.TrendingUp
    SplitBucket.FAMILY -> Icons.Filled.FamilyRestroom
    SplitBucket.LIFESTYLE -> Icons.Filled.AutoAwesome
}

@Composable
fun bucketTint(b: SplitBucket): Color = when (b) {
    SplitBucket.ESSENTIALS -> SheetTheme.navy
    SplitBucket.EMERGENCY -> SheetTheme.bad
    SplitBucket.SAVINGS -> SheetTheme.good
    SplitBucket.FAMILY -> SheetTheme.purple
    SplitBucket.LIFESTYLE -> SheetTheme.warn
}

@Composable
fun SalarySplitCard(store: SheetStore, snapshot: SheetSnapshot, onEditSalary: (SalaryMonth) -> Unit) {
    val months = snapshot.salary.map { it.month }
    val thisMonth = LocalDate.now().toString().take(7)
    var month by rememberSaveable { mutableStateOf(if (thisMonth in months) thisMonth else months.lastOrNull { it <= thisMonth } ?: months.firstOrNull() ?: thisMonth) }
    var putAside by remember { mutableStateOf<SplitBucket?>(null) }
    val lines = SalarySplit.lines(snapshot, month)
    val income = SalarySplit.income(snapshot, month)
    val label = snapshot.salary.firstOrNull { it.month == month }?.label ?: month
    val total = lines.sumOf { it.share }
    val index = months.indexOf(month)

    SheetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ if (index > 0) month = months[index - 1] }, enabled = index > 0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Salary split · $label", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                Text(if (income > 0) "${SheetFormat.qar(income)} ${Money.main} income" else "No salary entered for this month", fontSize = 12.sp, color = SheetTheme.secondary)
            }
            IconButton({ if (index in 0 until months.size - 1) month = months[index + 1] }, enabled = index in 0 until months.size - 1) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
            }
        }
        if (income <= 0) snapshot.salary.firstOrNull { it.month == month }?.let { m ->
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Enter this month's salary to split it") { onEditSalary(m) }
        }
        lines.forEach { line -> Spacer(Modifier.height(14.dp)); SplitRow(line) { putAside = line.bucket } }
        snapshot.settings.emergencyGoal?.takeIf { it > 0 }?.let { goal ->
            val saved = SalarySplit.emergencySaved(snapshot)
            Spacer(Modifier.height(14.dp))
            ProgressRow("Emergency fund", "${SheetFormat.qar(saved)} of ${SheetFormat.qar(goal)}", saved / goal, bucketTint(SplitBucket.EMERGENCY), Icons.Filled.Shield)
        }
        if (Math.abs(total - 1) > 0.001) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, tint = SheetTheme.warn, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Your shares add up to ${SheetFormat.percent(total, 0)}, not 100% — change them in Settings ▸ Salary split.", fontSize = 12.sp, color = SheetTheme.warn)
            }
        }
    }
    putAside?.let { PutAsideSheet(store, it) { putAside = null } }
}

@Composable
private fun SplitRow(line: SplitLine, onPutAside: () -> Unit) {
    val tint = bucketTint(line.bucket)
    val over = !line.bucket.isSaving && line.left < 0
    val detail = when {
        line.bucket.isSaving && line.planned > 0 -> "${SheetFormat.qar(line.used)} put aside · ${if (line.left > 0) "${SheetFormat.qar(line.left)} to go" else "done ✓"}"
        line.bucket.isSaving -> "${SheetFormat.qar(line.used)} put aside"
        line.planned <= 0 -> "${SheetFormat.qar(line.used)} used"
        over -> "${SheetFormat.qar(line.used)} used · ${SheetFormat.qar(-line.left)} over"
        else -> "${SheetFormat.qar(line.used)} used · ${SheetFormat.qar(line.left)} left"
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(bucketIcon(line.bucket), tint, 30.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${line.bucket.label} · ${SheetFormat.percent(line.share, 0)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                Text(detail, fontSize = 12.sp, color = if (over) SheetTheme.bad else SheetTheme.secondary)
            }
            Text(SheetFormat.qar(line.planned), fontWeight = FontWeight.Bold, color = SheetTheme.text)
        }
        Spacer(Modifier.height(6.dp))
        Bar(line.fraction, when {
            line.bucket.isSaving && line.fraction >= 1 -> SheetTheme.good
            over -> SheetTheme.bad
            else -> tint
        })
        if (line.bucket.isSaving) Row(Modifier.padding(top = 6.dp).clickable(onClick = onPutAside), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AddCircleOutline, null, tint = SheetTheme.navy, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Put money aside", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
        }
    }
}

/** Move money into the emergency fund or savings / SIP. */
@Composable
fun PutAsideSheet(store: SheetStore, initialBucket: SplitBucket, goalId: String? = null, onDismiss: () -> Unit) {
    var bucket by remember { mutableStateOf(initialBucket) }
    var goal by remember { mutableStateOf(goalId) }
    val goals = store.snapshot?.savingsGoals.orEmpty().filter { !it.done || it.id == goalId }
    var amountText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    val amount = SheetFormat.input(amountText)?.takeIf { it > 0 }
    val entries = store.snapshot?.setAside.orEmpty().filter { it.bucket == bucket.label }.sortedByDescending { it.date }
    EditorSheet("Put money aside", onDismiss, canSave = amount != null, onSave = {
        store.saveSetAside(SetAsideEntry(date = date.toString(), bucket = bucket.label, amount = Math.round(amount!! * 100) / 100.0, note = note.trim().ifEmpty { null }, goalId = goal))
        onDismiss()
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Into", color = SheetTheme.secondary, modifier = Modifier.weight(1f))
            listOf(SplitBucket.SAVINGS, SplitBucket.EMERGENCY).forEach { b ->
                Spacer(Modifier.width(8.dp))
                FilterPill(b.label, null, SheetTheme.navy, bucket == b) { bucket = b }
            }
        }
        if (goals.isNotEmpty()) {
            Text("For a goal", color = SheetTheme.secondary)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("None", null, SheetTheme.indigo, goal == null) { goal = null }
                goals.forEach { g -> FilterPill(g.name, null, SheetTheme.indigo, goal == g.id) { goal = g.id } }
            }
        }
        SheetCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CurrencyPill(Money.main)
                Spacer(Modifier.width(12.dp))
                BasicTextField(amountText, { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, singleLine = true,
                    textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text, textAlign = TextAlign.End),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), cursorBrush = SolidColor(SheetTheme.navy),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> Box(contentAlignment = Alignment.CenterEnd) { if (amountText.isEmpty()) Text("0.00", fontSize = 26.sp, color = SheetTheme.tertiary); inner() } })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Day", color = SheetTheme.secondary, modifier = Modifier.weight(1f))
            FilterPill("Today", null, SheetTheme.navy, date == LocalDate.now()) { date = LocalDate.now() }
            Spacer(Modifier.width(8.dp))
            FilterPill("Yesterday", null, SheetTheme.navy, date == LocalDate.now().minusDays(1)) { date = LocalDate.now().minusDays(1) }
        }
        Field("Note (optional)") { PlainField(note, if (bucket == SplitBucket.SAVINGS) "e.g. Nifty SIP, FD" else "e.g. bank account") { note = it } }
        Text("Saving, not spending — it doesn't go into your expenses.", fontSize = 12.sp, color = SheetTheme.secondary)
        if (entries.isNotEmpty()) FormSection("Put aside so far · ${SheetFormat.qar(entries.sumOf { it.amount })} ${Money.main}") {
            entries.take(12).forEachIndexed { i, e ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(SheetDate.short(e.date), color = SheetTheme.text)
                        e.note?.let { Text(it, fontSize = 12.sp, color = SheetTheme.secondary) }
                    }
                    Text(SheetFormat.qar(e.amount), color = SheetTheme.text)
                    Spacer(Modifier.width(12.dp))
                    Text("Delete", fontSize = 13.sp, color = SheetTheme.bad, modifier = Modifier.clickable { store.deleteSetAside(e.id) })
                }
            }
        }
    }
}
