package com.mohammedsharukms.mypersonaltracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mohammedsharukms.mypersonaltracker.MainActivity
import com.mohammedsharukms.mypersonaltracker.container
import com.mohammedsharukms.mypersonaltracker.sheet.SalarySplit
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.activeSubjects
import com.mohammedsharukms.mypersonaltracker.sheet.currentTopic
import com.mohammedsharukms.mypersonaltracker.sheet.dueTopics
import com.mohammedsharukms.mypersonaltracker.sheet.inCart
import com.mohammedsharukms.mypersonaltracker.sheet.lastPrice
import com.mohammedsharukms.mypersonaltracker.sheet.minutesOn
import com.mohammedsharukms.mypersonaltracker.sheet.shoppingData
import com.mohammedsharukms.mypersonaltracker.sheet.streak
import com.mohammedsharukms.mypersonaltracker.sheet.studyData
import com.mohammedsharukms.mypersonaltracker.sheet.subject
import com.mohammedsharukms.mypersonaltracker.sheet.stepsWeek
import com.mohammedsharukms.mypersonaltracker.sheet.stepStreak
import com.mohammedsharukms.mypersonaltracker.sheet.toBuy
import java.time.LocalDate

// Salary split, study today and the shopping list, from the Google Sheet.
// Mirrors TrackerWidgets/SheetWidgets.swift.

private fun c(value: Color) = ColorProvider(day = value, night = value)
private val InkDark = Color(0xFF1C1917)
private val InkSoft = Color(0xFF57534E)
private val Red = Color(0xFFDC2626)
private val Amber = Color(0xFFD97706)
private val Green = Color(0xFF16A34A)
private val Indigo = Color(0xFF4F46E5)
private val Teal = Color(0xFF0F766E)
private val Purple = Color(0xFF7C3AED)
private val Track = Color(0x1F000000)

private fun amount(v: Double) = java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits = 0 }.format(v)

private fun durationText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

@Composable
private fun SheetBox(background: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(GlanceModifier.fillMaxSize().background(c(background)).cornerRadius(20.dp).padding(14.dp)
        .clickable(actionStartActivity<MainActivity>())) { content() }
}

@Composable
private fun Hint(text: String) = Text(text, style = TextStyle(fontSize = 13.sp, color = c(Color(0xFF44403C))))

@Composable
private fun Bar(fraction: Double, tint: Color) =
    LinearProgressIndicator(fraction.coerceIn(0.0, 1.0).toFloat(), GlanceModifier.fillMaxWidth().height(5.dp), c(tint), c(Track))

private suspend fun snapshotOf(context: Context): SheetSnapshot? = context.container.sheet.snapshot

// MARK: - Salary split

class SplitWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshotOf(context)
        provideContent { SplitContent(s) }
    }
}

class SplitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SplitWidget()
}

@Composable
private fun SplitContent(s: SheetSnapshot?) {
    SheetBox(Color(0xFFEEF0FC)) {
        if (s == null) { Hint("Connect your sheet in the app"); return@SheetBox }
        val month = LocalDate.now().toString().take(7)
        if (SalarySplit.income(s, month) <= 0) { Hint("Enter this month's salary to see your split"); return@SheetBox }
        val lines = SalarySplit.lines(s, month)
        val wide = LocalSize.current.width >= 200.dp
        val shown = if (wide) lines else lines.filter { !it.bucket.isSaving }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Salary split", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c(Indigo)))
            Spacer(GlanceModifier.defaultWeight())
            Text(s.settings.mainCurrency, style = TextStyle(fontSize = 11.sp, color = c(InkSoft)))
        }
        if (!wide) {
            val left = shown.sumOf { it.left }
            Text(amount(left), style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c(if (left < 0) Red else InkDark)))
            Text(if (left < 0) "over to spend" else "left to spend", style = TextStyle(fontSize = 11.sp, color = c(InkSoft)))
        }
        Spacer(GlanceModifier.height(4.dp))
        shown.forEach { line ->
            val over = !line.bucket.isSaving && line.used > line.planned + 0.005
            val tint = when {
                line.bucket.isSaving -> if (line.left <= 0.005) Green else Teal
                over -> Red
                line.fraction >= 0.8 -> Amber
                else -> Indigo
            }
            val status = when {
                line.bucket.isSaving -> if (line.left > 0.005) "${amount(line.left)} to put aside" else "Done ✓"
                over -> "${amount(-line.left)} over"
                else -> "${amount(line.left)} left"
            }
            Column(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(GlanceModifier.fillMaxWidth()) {
                    Text(line.bucket.label, maxLines = 1, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c(InkDark)))
                    Spacer(GlanceModifier.defaultWeight())
                    if (wide) Text(status, maxLines = 1, style = TextStyle(fontSize = 11.sp, color = c(if (over) Red else InkSoft)))
                }
                Spacer(GlanceModifier.height(2.dp))
                Bar(line.fraction, tint)
            }
        }
    }
}

// MARK: - Study today

class StudyWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshotOf(context)
        provideContent { StudyContent(s) }
    }
}

class StudyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StudyWidget()
}

@Composable
private fun StudyContent(s: SheetSnapshot?) {
    SheetBox(Color(0xFFF3EEFD)) {
        if (s == null) { Hint("Connect your sheet in the app"); return@SheetBox }
        val data = s.studyData
        if (data.activeSubjects.isEmpty()) { Hint("Add a subject in the Study tab"); return@SheetBox }
        val minutes = data.minutesOn(LocalDate.now().toString()).toInt()
        val target = maxOf((s.settings.studyDailyTarget ?: 60.0).toInt(), 1)
        val done = minutes >= target
        val streak = data.streak()
        val due = data.dueTopics().size
        val next = data.currentTopic
        val wide = LocalSize.current.width >= 200.dp
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("📚 Study", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c(Purple)))
            Spacer(GlanceModifier.defaultWeight())
            if (streak > 0) Text("🔥 $streak", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c(Color(0xFFF97316))))
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.Bottom) {
            Text(durationText(minutes), style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = c(InkDark)))
            Spacer(GlanceModifier.width(6.dp))
            Text(if (done) "target reached ✓" else "of ${durationText(target)}",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c(if (done) Green else InkSoft)))
        }
        Spacer(GlanceModifier.height(4.dp))
        Bar(minutes.toDouble() / target, if (done) Green else Purple)
        Spacer(GlanceModifier.height(6.dp))
        if (due > 0) Text("$due concept${if (due == 1) "" else "s"} to revise",
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c(Amber)))
        if (next != null) {
            val subject = data.subject(next.subjectId)?.name
            Text(if (wide && subject != null) "Next · $subject: ${next.topic}" else "Next: ${next.topic}", maxLines = if (wide) 2 else 1,
                style = TextStyle(fontSize = 12.sp, color = c(InkDark)))
        }
    }
}

// MARK: - Shopping list

class ShoppingWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshotOf(context)
        provideContent { ShoppingContent(s) }
    }
}

class ShoppingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShoppingWidget()
}

@Composable
private fun ShoppingContent(s: SheetSnapshot?) {
    SheetBox(Color(0xFFE8F6EC)) {
        if (s == null) { Hint("Connect your sheet in the app"); return@SheetBox }
        val list = s.shoppingData
        val toBuy = list.toBuy
        val size = LocalSize.current
        val shown = if (size.height >= 200.dp) 7 else 3
        val estimate = toBuy.mapNotNull { it.price ?: list.lastPrice(it.item) }.takeIf { it.isNotEmpty() }?.sum()
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (toBuy.isEmpty()) "🛒 All bought" else "🛒 ${toBuy.size} to buy",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c(InkDark)))
            Spacer(GlanceModifier.defaultWeight())
            if (estimate != null && size.width >= 200.dp) Text("≈ ${amount(estimate)} ${s.settings.mainCurrency}",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c(InkSoft)))
        }
        Spacer(GlanceModifier.height(6.dp))
        if (toBuy.isEmpty()) {
            Text("Nothing on your list.", style = TextStyle(fontSize = 12.sp, color = c(InkSoft)))
        } else {
            toBuy.take(shown).forEach { item ->
                Text("○  ${item.item}${item.quantity?.let { " · $it" } ?: ""}", maxLines = 1,
                    style = TextStyle(fontSize = 13.sp, color = c(InkDark)), modifier = GlanceModifier.padding(vertical = 1.dp))
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth()) {
                if (toBuy.size > shown) Text("+${toBuy.size - shown} more", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c(InkSoft)))
                Spacer(GlanceModifier.defaultWeight())
                val inCart = list.inCart.size
                if (inCart > 0) Text("$inCart in cart", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c(Green)))
            }
        }
    }
}

// MARK: - Steps

class StepsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshotOf(context)
        val phone = com.mohammedsharukms.mypersonaltracker.health.HealthSteps.today
        provideContent { StepsContent(s, phone) }
    }
}

class StepsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StepsWidget()
}

@Composable
private fun StepsContent(s: SheetSnapshot?, phoneToday: Int?) {
    SheetBox(Color(0xFFE6F6EC)) {
        if (s == null) { Hint("Connect your sheet in the app"); return@SheetBox }
        val todayKey = LocalDate.now().toString()
        val today = maxOf(phoneToday ?: 0, (s.days.firstOrNull { it.date == todayKey }?.steps ?: 0.0).toInt())
        val target = maxOf((s.settings.stepsTarget ?: 8000.0).toInt(), 1)
        val streak = s.stepStreak(todaySteps = today)
        val done = today >= target
        val wide = LocalSize.current.width >= 200.dp
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("👣 Steps", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c(Green)))
            Spacer(GlanceModifier.defaultWeight())
            if (streak > 0) Text("🔥 $streak", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c(Color(0xFFF97316))))
        }
        Spacer(GlanceModifier.defaultWeight())
        Text("%,d".format(today), style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = c(InkDark)))
        Text(if (done) "goal reached ✓" else "of ${"%,d".format(target)} · ${"%,d".format(target - today)} to go",
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c(if (done) Green else InkSoft)))
        Spacer(GlanceModifier.height(4.dp))
        Bar(today.toDouble() / target, Green)
        if (wide) {
            Spacer(GlanceModifier.height(8.dp))
            val week = s.stepsWeek(todaySteps = today)
            val top = maxOf(week.maxOrNull() ?: 0, target)
            Row(GlanceModifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.Bottom) {
                week.forEachIndexed { i, v ->
                    androidx.glance.layout.Box(GlanceModifier.defaultWeight().height(maxOf(3, 36 * v / top).dp).padding(horizontal = 2.dp)) {
                        androidx.glance.layout.Box(GlanceModifier.fillMaxSize().cornerRadius(3.dp)
                            .background(c(if (v >= target) Green else Track))) {}
                    }
                }
            }
        }
    }
}
