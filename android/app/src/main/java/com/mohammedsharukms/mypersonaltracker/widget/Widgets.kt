package com.mohammedsharukms.mypersonaltracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.mohammedsharukms.mypersonaltracker.MainActivity
import com.mohammedsharukms.mypersonaltracker.container
import com.mohammedsharukms.mypersonaltracker.data.TodoItem
import com.mohammedsharukms.mypersonaltracker.domain.ProgressStats
import com.mohammedsharukms.mypersonaltracker.domain.durationText
import com.mohammedsharukms.mypersonaltracker.domain.isVisibleOn
import com.mohammedsharukms.mypersonaltracker.domain.toggled
import com.mohammedsharukms.mypersonaltracker.domain.wasCompletedOn
import java.time.LocalDate

/** Everything the widgets show, computed straight from the database at render time. */
private data class WidgetData(
    val streak: Int,
    val showedUpToday: Boolean,
    val todayMinutes: Int,
    val openTodos: List<TodoItem>,
    val doneToday: Int,
    /** Oldest first, one per day ending today. */
    val recentLevels: List<Int>,
    /** Today from the Google Sheet, when one is connected; preferred over the local journal. */
    val sheet: SheetToday? = null,
) {
    data class SheetToday(
        val logged: Boolean, val score: Double?, val streak: Int,
        val spentToday: Double, val dailyCap: Double?, val monthSpent: Double, val monthlyLimit: Double?, val currency: String,
    )

    companion object {
        const val HEATMAP_WEEKS = 10 // RemoteViews rows/columns allow at most 10 children.

        suspend fun load(context: Context): WidgetData {
            val db = context.container.database
            val days = db.dailyLogs().getAll()
            val todos = db.todos().getAll()
            val today = LocalDate.now()
            val stats = ProgressStats(days, emptyList(), today = today)
            val todayLog = days.firstOrNull { it.log.date == today }
            val levels = (HEATMAP_WEEKS * 7 - 1 downTo 0).map { stats.heatLevels[today.minusDays(it.toLong())] ?: 0 }
            val snapshot = context.container.sheet.snapshot
            val sheet = snapshot?.let { s ->
                val key = today.toString()
                val day = s.days.firstOrNull { it.date == key }
                SheetToday(
                    logged = day?.isLogged == true,
                    score = day?.score,
                    streak = (day?.streak ?: s.days.lastOrNull { it.date < key && it.isLogged }?.streak ?: 0.0).toInt(),
                    spentToday = s.expenses.filter { it.date == key }.sumOf { it.qar ?: 0.0 },
                    dailyCap = s.settings.dailyLimit,
                    monthSpent = s.expenses.filter { (it.date ?: "").startsWith(key.take(7)) }.sumOf { it.qar ?: 0.0 },
                    monthlyLimit = s.settings.monthlyLimit,
                    currency = s.settings.mainCurrency,
                )
            }
            return WidgetData(
                sheet = sheet,
                streak = stats.currentStreak,
                showedUpToday = todayLog?.log?.showedUp == true,
                todayMinutes = todayLog?.totalMinutes ?: 0,
                openTodos = todos.filter { !it.isDone && it.isVisibleOn(today) },
                doneToday = todos.count { it.wasCompletedOn(today) },
                recentLevels = levels,
            )
        }
    }
}

private fun color(value: Color) = ColorProvider(day = value, night = value)

private val Paper = Color(0xFFFFEB85)
private val Ink = Color(0xFF382E0F)
private val Flame = Color(0xFFF97316)
private val Green = Color(0xFF16A34A)

// MARK: - Streak

class StreakWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        provideContent { StreakContent(data) }
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}

@Composable
private fun StreakContent(data: WidgetData) {
    // With a sheet connected, its great-day streak and "logged" win over the local journal.
    val sheet = data.sheet
    val streak = sheet?.streak ?: data.streak
    val logged = sheet?.logged ?: data.showedUpToday
    val todayText = when {
        sheet != null -> if (sheet.logged) "Logged · ${((sheet.score ?: 0.0) * 100).toInt()}%" else "Not logged yet"
        data.todayMinutes > 0 -> "Today ${data.todayMinutes.durationText}"
        data.showedUpToday -> "Showed up today"
        else -> "Not logged yet"
    }
    Column(
        modifier = GlanceModifier.fillMaxSize().background(color(Color(0xFFFFF4E6))).cornerRadius(20.dp)
            .padding(14.dp).clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(if (logged) "🔥" else "⏳", style = TextStyle(fontSize = 22.sp))
        Spacer(GlanceModifier.defaultWeight())
        Text(
            "$streak",
            style = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, color = color(Color(0xFF1C1917))),
        )
        Text("day streak", style = TextStyle(fontSize = 13.sp, color = color(Color(0xFF78716C))))
        Text(
            todayText,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color(if (logged) Green else Flame)),
        )
    }
}

// MARK: - Spending today

class SpendWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        provideContent { SpendContent(data) }
    }
}

class SpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpendWidget()
}

@Composable
private fun SpendContent(data: WidgetData) {
    val sheet = data.sheet
    val modifier = GlanceModifier.fillMaxSize().background(color(Color(0xFFE6F4F1))).cornerRadius(20.dp)
        .padding(14.dp).clickable(actionStartActivity<MainActivity>())
    if (sheet == null) {
        Column(modifier) { Text("Connect your sheet in the app", style = TextStyle(fontSize = 13.sp, color = color(Color(0xFF44403C)))) }
        return
    }
    val cap = sheet.dailyCap ?: 0.0
    val over = cap > 0 && sheet.spentToday > cap
    fun amount(v: Double) = java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(v)
    Column(modifier) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text("💳", style = TextStyle(fontSize = 20.sp))
            Spacer(GlanceModifier.defaultWeight())
            if (over) Text("OVER", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color(Color(0xFFDC2626))))
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(amount(sheet.spentToday), style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, color = color(Color(0xFF1C1917))))
        Text("of ${amount(cap)} ${sheet.currency} today", style = TextStyle(fontSize = 12.sp, color = color(Color(0xFF57534E))))
        val limit = sheet.monthlyLimit ?: 0.0
        Text(
            if (limit > 0) "Month: ${amount(sheet.monthSpent)} / ${amount(limit)}" else "Month: ${amount(sheet.monthSpent)}",
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = color(if (limit > 0 && sheet.monthSpent > limit) Color(0xFFDC2626) else Color(0xFF0F766E))),
        )
    }
}

// MARK: - Sticky note

class TodoWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        provideContent { TodoContent(data) }
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget()
}

private val TodoIdKey = ActionParameters.Key<Long>("todoId")

/** Ticks a to-do straight from the home screen. */
class ToggleTodoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TodoIdKey] ?: return
        val dao = context.container.database.todos()
        val item = dao.get(id) ?: return
        dao.update(item.toggled())
        WidgetUpdater.updateAll(context)
    }
}

@Composable
private fun TodoContent(data: WidgetData) {
    val tall = LocalSize.current.height >= 220.dp
    val maxTodos = if (tall) 6 else 3
    val handwriting = TextStyle(fontFamily = FontFamily.Cursive, fontSize = 17.sp, color = color(Ink))

    Column(modifier = GlanceModifier.fillMaxSize().background(color(Paper)).cornerRadius(18.dp).padding(14.dp)) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "To-do",
                style = handwriting.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
            )
            if (data.doneToday > 0) {
                Text("  ${data.doneToday} done", style = handwriting.copy(fontSize = 14.sp))
            }
            Spacer(GlanceModifier.defaultWeight())
            Text("🔥 ${data.streak}", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color(Flame)))
        }
        Spacer(GlanceModifier.height(6.dp))

        if (data.openTodos.isEmpty()) {
            Text(if (data.doneToday > 0) "All done for today 🎉" else "Nothing on the note yet.", style = handwriting)
        } else {
            data.openTodos.take(maxTodos).forEach { todo ->
                Text(
                    "☐  ${todo.title}",
                    maxLines = 1,
                    style = handwriting,
                    modifier = GlanceModifier.padding(vertical = 2.dp)
                        .clickable(actionRunCallback<ToggleTodoAction>(actionParametersOf(TodoIdKey to todo.id))),
                )
            }
            if (data.openTodos.size > maxTodos) {
                Text("+ ${data.openTodos.size - maxTodos} more", style = TextStyle(fontSize = 12.sp, color = color(Ink.copy(alpha = 0.6f))))
            }
        }

        if (tall) {
            Spacer(GlanceModifier.defaultWeight())
            MiniHeatmap(data.recentLevels)
            Spacer(GlanceModifier.height(4.dp))
            Text(
                if (data.todayMinutes > 0) "Today ${data.todayMinutes.durationText}" else "No time logged today",
                style = TextStyle(fontSize = 12.sp, color = color(Ink.copy(alpha = 0.75f)), textDecoration = TextDecoration.None),
            )
        }
    }
}

@Composable
private fun MiniHeatmap(levels: List<Int>) {
    val weeks = levels.chunked(7)
    Row {
        weeks.forEach { week ->
            Column(modifier = GlanceModifier.padding(end = 3.dp)) {
                week.forEach { level ->
                    Box(
                        modifier = GlanceModifier.size(11.dp)
                            .background(color(if (level == 0) Color(0x14000000) else Green.copy(alpha = 0.25f * level)))
                            .cornerRadius(2.dp),
                    ) {}
                    Spacer(GlanceModifier.height(3.dp).width(1.dp))
                }
            }
        }
    }
}
