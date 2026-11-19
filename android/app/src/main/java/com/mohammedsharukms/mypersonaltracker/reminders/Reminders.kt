package com.mohammedsharukms.mypersonaltracker.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mohammedsharukms.mypersonaltracker.MainActivity
import com.mohammedsharukms.mypersonaltracker.R
import com.mohammedsharukms.mypersonaltracker.TrackerApp
import com.mohammedsharukms.mypersonaltracker.container
import com.mohammedsharukms.mypersonaltracker.domain.ProgressStats
import com.mohammedsharukms.mypersonaltracker.sheet.ParentReminders
import com.mohammedsharukms.mypersonaltracker.sheet.StudyFormat
import com.mohammedsharukms.mypersonaltracker.sheet.openParentAsks
import com.mohammedsharukms.mypersonaltracker.sheet.activeSubjects
import com.mohammedsharukms.mypersonaltracker.sheet.currentTopic
import com.mohammedsharukms.mypersonaltracker.sheet.dueTopics
import com.mohammedsharukms.mypersonaltracker.sheet.minutesOn
import com.mohammedsharukms.mypersonaltracker.sheet.streak
import com.mohammedsharukms.mypersonaltracker.sheet.studyData
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Schedules the next evening and morning reminder with AlarmManager. Unlike iOS, the decision whether to
 * actually notify is made when the alarm fires, so a reminder is skipped if you've already logged.
 */
object ReminderScheduler {
    private const val KIND_EVENING = "evening"
    private const val KIND_MORNING = "morning"
    private const val KIND_STUDY = "study"
    /** The morning briefing, bills due tomorrow and renewal reminders, from the sheet. */
    private const val KIND_BRIEFING = "briefing"
    /** Checked each evening; posts the week in review on Sundays. */
    private const val KIND_WEEKLY = "weekly"
    /** At the next time something Mom or Dad asked for is due; set again after each. */
    private const val KIND_PARENTS = "parents"
    private const val PREF_PARENTS_LAST = "parentRemindersLastRun"
    /** Once a day at its own time: medicines running low, their days coming up, a call that's due. */
    private const val KIND_PARENT_DIGEST = "parentDigest"
    const val EXTRA_KIND = "kind"

    fun reschedule(context: Context) {
        val settings = context.container.settings
        cancel(context, KIND_EVENING)
        cancel(context, KIND_MORNING)
        cancel(context, KIND_STUDY)
        cancel(context, KIND_BRIEFING)
        cancel(context, KIND_WEEKLY)
        cancel(context, KIND_PARENTS)
        cancel(context, KIND_PARENT_DIGEST)
        scheduleParents(context)
        if (com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetAlertPrefs.isOn(context, com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetAlertPrefs.PARENTS)) {
            schedule(context, KIND_PARENT_DIGEST, com.mohammedsharukms.mypersonaltracker.sheet.ParentPrefs.digestMinutes(context))
        }
        // The study reminder has its own switch, separate from the daily-log ones.
        if (settings.studyReminderEnabled) schedule(context, KIND_STUDY, settings.studyReminderMinutes)
        // The sheet's notifications have their own switches too (on by default).
        val alerts = com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetAlertPrefs
        if (listOf(alerts.BRIEFING, alerts.BILLS, alerts.RENEWALS).any { alerts.isOn(context, it) }) schedule(context, KIND_BRIEFING, settings.morningMinutes)
        if (alerts.isOn(context, alerts.WEEKLY)) schedule(context, KIND_WEEKLY, 19 * 60)
        // The evening and morning reminders have a switch each (Settings ▸ Notifications).
        if (settings.remindersEnabled) schedule(context, KIND_EVENING, settings.eveningMinutes)
        if (settings.morningEnabled) schedule(context, KIND_MORNING, settings.morningMinutes)
    }

    private fun schedule(context: Context, kind: String, minutesOfDay: Int) {
        val now = LocalDateTime.now()
        var fire = LocalDate.now().atStartOfDay().plusMinutes(minutesOfDay.toLong())
        if (!fire.isAfter(now)) fire = fire.plusDays(1)
        val millis = fire.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val alarms = context.getSystemService(AlarmManager::class.java)
        // Inexact-while-idle is enough for a daily nudge and needs no special alarm permission.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent(context, kind))
    }

    /** The next reminder time of an open request (the alarm posts whatever is due by then). */
    private fun scheduleParents(context: Context) {
        val alerts = com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetAlertPrefs
        val store = context.container.sheet
        val s = store.snapshot ?: return
        if (store.isDemo || !alerts.isOn(context, alerts.PARENTS)) return
        val next = ParentReminders.slots(s.openParentAsks, LocalDateTime.now(), snoozed = com.mohammedsharukms.mypersonaltracker.sheet.ParentPrefs.snoozes(context))
            .firstOrNull()?.first ?: return
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent(context, KIND_PARENTS))
    }

    /** Everything due since the last run (an inexact alarm can be a few minutes late), one notification per time. */
    private fun fireParents(context: Context) {
        val store = context.container.sheet
        val s = store.snapshot ?: return
        if (store.isDemo) return
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val now = LocalDateTime.now()
        val last = prefs.getString(PREF_PARENTS_LAST, null)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            ?.takeIf { it.isAfter(now.minusDays(1)) } ?: now.minusHours(1)
        prefs.edit().putString(PREF_PARENTS_LAST, now.toString()).apply()
        ParentReminders.slots(s.openParentAsks, last, days = 2, snoozed = com.mohammedsharukms.mypersonaltracker.sheet.ParentPrefs.snoozes(context)).filter { !it.first.isAfter(now.plusMinutes(1)) }.forEach { (fire, asks) ->
            val (title, body) = ParentReminders.message(asks, fire.toLocalDate())
            post(context, "parents.$fire", title, body, "parents", parentAsk = asks.singleOrNull()?.id)
        }
    }

    private fun fireParentDigest(context: Context) {
        val store = context.container.sheet
        val s = store.snapshot ?: return
        if (store.isDemo) return
        val prefs = com.mohammedsharukms.mypersonaltracker.sheet.ParentPrefs
        val who = listOf(com.mohammedsharukms.mypersonaltracker.sheet.ParentWho.MOM, com.mohammedsharukms.mypersonaltracker.sheet.ParentWho.DAD)
        val lines = com.mohammedsharukms.mypersonaltracker.sheet.ParentDigest.lines(s, LocalDate.now(),
            who.mapNotNull { w -> prefs.lastTalk(context, w)?.let { w to it } }.toMap(), who.associateWith { prefs.callEvery(context, it) })
        if (lines.isEmpty()) return
        val (title, body) = com.mohammedsharukms.mypersonaltracker.sheet.ParentDigest.message(lines)
        post(context, "parentDigest", title, body, "parents")
    }

    private fun cancel(context: Context, kind: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, kind))
    }

    private fun pendingIntent(context: Context, kind: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        kind.hashCode(),
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_KIND, kind),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Several notifications from one alarm: briefing, bills due tomorrow, renewals on their reminder days. */
    private fun fireSheetAlerts(context: Context, kind: String) {
        val store = context.container.sheet
        val s = store.snapshot ?: return
        if (store.isDemo) return
        val alerts = com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetAlertPrefs
        val today = LocalDate.now()
        if (kind == KIND_WEEKLY) {
            if (today.dayOfWeek != java.time.DayOfWeek.SUNDAY) return
            val review = com.mohammedsharukms.mypersonaltracker.sheet.WeeklyReview(s, today.minusDays(6))
            post(context, "weekly", "Your week in review 📊", review.summary, "weeklyReview")
            return
        }
        if (alerts.isOn(context, alerts.BRIEFING)) {
            val (title, body) = com.mohammedsharukms.mypersonaltracker.sheet.Briefing.text(s, today, includeLive = true)
            post(context, "briefing", title, body, "dashboard")
        }
        if (alerts.isOn(context, alerts.BILLS)) {
            val bills = com.mohammedsharukms.mypersonaltracker.sheet.Bills.due(s, today.plusDays(1))
            if (bills.isNotEmpty()) {
                val fmt = { v: Double? -> com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetFormat.qar(v) }
                val main = com.mohammedsharukms.mypersonaltracker.sheet.ui.Money.main
                post(context, "bills", if (bills.size == 1) "Due tomorrow: ${bills[0].description ?: "a bill"}" else "${bills.size} bills due tomorrow",
                    bills.joinToString("\n") { "${it.description ?: "Bill"} · ${fmt(it.amount)} $main" } +
                        if (bills.size > 1) "\nTotal ${fmt(bills.sumOf { it.amount ?: 0.0 })} $main" else "", "finance")
            }
        }
        if (alerts.isOn(context, alerts.RENEWALS)) {
            s.renewals.forEach { r ->
                val days = r.daysLeft(today)
                if (days.toInt() in r.remind) {
                    post(context, "renewal.${r.id}", if (days == 0L) "${r.item} expires today" else "${r.item} expires in $days day${if (days == 1L) "" else "s"}",
                        "On ${com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetDate.short(r.expires)}. Renew it, then mark it renewed in the app.", "renewals")
                }
            }
        }
    }

    private fun post(context: Context, id: String, title: String, body: String, open: String, parentAsk: String? = null) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val launch = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, open)
        val tap = PendingIntent.getActivity(context, id.hashCode(), launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(context, TrackerApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(tap)
        // One request: a Done button that marks it done without opening the app.
        if (parentAsk != null) {
            val done = Intent(context, ParentDoneReceiver::class.java)
                .putExtra(ParentDoneReceiver.EXTRA_ID, parentAsk).putExtra(ParentDoneReceiver.EXTRA_NOTIFICATION, id.hashCode())
            builder.addAction(0, "Done ✓", PendingIntent.getBroadcast(context, id.hashCode(), done, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            val tomorrow = Intent(context, ParentDoneReceiver::class.java).putExtra(ParentDoneReceiver.EXTRA_ID, parentAsk)
                .putExtra(ParentDoneReceiver.EXTRA_NOTIFICATION, id.hashCode()).putExtra(ParentDoneReceiver.EXTRA_SNOOZE, true)
            builder.addAction(0, "Tomorrow", PendingIntent.getBroadcast(context, id.hashCode() + 1, tomorrow, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        NotificationManagerCompat.from(context).notify(id.hashCode(), builder.build())
    }

    internal suspend fun fire(context: Context, kind: String) {
        if (kind == KIND_BRIEFING || kind == KIND_WEEKLY) { fireSheetAlerts(context, kind); return }
        if (kind == KIND_PARENTS) { fireParents(context); return }
        if (kind == KIND_PARENT_DIGEST) { fireParentDigest(context); return }
        val db = context.container.database
        val today = LocalDate.now()
        val days = db.dailyLogs().getAll()
        val todayLog = days.firstOrNull { it.log.date == today }?.log

        // With the Google Sheet connected, "logged today" and the streak come from it.
        val sheet = context.container.sheet.snapshot
        val (title, body) = when (kind) {
            KIND_EVENING -> {
                if (sheet != null) {
                    val key = today.toString()
                    if (sheet.days.firstOrNull { it.date == key }?.isLogged == true) return
                    val streak = (sheet.days.lastOrNull { it.date < key && it.isLogged }?.streak ?: 0.0).toInt()
                    if (streak > 0) "Don't break your $streak-day streak 🔥" to "You haven't logged today's habits yet — it takes a few taps."
                    else "Time to log your day ✍️" to "Log today's habits and anything you spent."
                } else {
                    if (todayLog?.showedUp == true) return
                    val streak = ProgressStats(days, emptyList(), today = today).currentStreak
                    if (streak > 0) {
                        "Don't break your $streak-day streak 🔥" to "You haven't logged today yet. What did you do, what did you learn?"
                    } else {
                        "Time to log your day ✍️" to "What did you do, what did you learn, and how long did you study?"
                    }
                }
            }
            KIND_STUDY -> {
                // What's due, what's next, how much is left — skipped once today's target is reached.
                val snapshot = sheet ?: return
                val data = snapshot.studyData
                if (data.activeSubjects.isEmpty()) return
                val key = today.toString()
                val studied = data.minutesOn(key)
                val target = maxOf(snapshot.settings.studyDailyTarget ?: 60.0, 1.0)
                if (studied >= target) return
                val due = data.dueTopics(key).size
                val parts = listOfNotNull(
                    if (due > 0) "$due concept${if (due == 1) "" else "s"} to revise" else null,
                    data.currentTopic?.let { "next: ${it.topic}" },
                    if (studied > 0) "${StudyFormat.minutes(target - studied)} to go today" else "${StudyFormat.minutes(target)} today",
                )
                val streak = data.streak(today)
                (if (streak > 0) "Keep your $streak-day study streak 📚" else "Time to study 📚") to
                    parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
            }
            else -> {
                // With the sheet connected, the morning slot is the briefing (or nothing, if that's off).
                val alerts = com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetAlertPrefs
                if (com.mohammedsharukms.mypersonaltracker.sheet.MorningSlot.of(sheet != null, alerts.isOn(context, alerts.BRIEFING), true) !=
                    com.mohammedsharukms.mypersonaltracker.sheet.MorningSlot.SMILE) return
                if (todayLog?.selfiePath != null) return
                "Good morning! 😁" to "Take your daily smile and write today's to-dos on your sticky note."
            }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val launch = Intent(context, MainActivity::class.java).apply { if (kind == KIND_STUDY) putExtra(MainActivity.EXTRA_OPEN_STUDY, true) }
        val open = PendingIntent.getActivity(context, kind.hashCode(), launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, TrackerApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(kind.hashCode(), notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: return
        val pending = goAsync()
        context.container.appScope.launch {
            try {
                ReminderScheduler.fire(context, kind)
                ReminderScheduler.reschedule(context) // next day
            } finally {
                pending.finish()
            }
        }
    }
}

/** "Done ✓" on a reminder ticks the request off (a repeating one moves to its next date);
 *  "Tomorrow" keeps it quiet until tomorrow without moving its date. */
class ParentDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
        if (intent.getBooleanExtra(EXTRA_SNOOZE, false)) {
            com.mohammedsharukms.mypersonaltracker.sheet.ParentPrefs.snoozeUntilTomorrow(context, id)
            ReminderScheduler.reschedule(context)
            return
        }
        val store = context.container.sheet
        val ask = store.snapshot?.parents?.firstOrNull { it.id == id }?.takeIf { it.isOpen } ?: return
        com.mohammedsharukms.mypersonaltracker.sheet.ParentActions.complete(context, store, ask)
        // Give the save a moment to reach the sheet.
        val pending = goAsync()
        context.container.appScope.launch {
            try { store.waitForSync(15_000) } finally { pending.finish() }
        }
    }

    companion object {
        const val EXTRA_ID = "parentAskId"
        const val EXTRA_NOTIFICATION = "notificationId"
        const val EXTRA_SNOOZE = "snooze"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.reschedule(context)
            context.container.focusTimer.syncNotification()
        }
    }
}
