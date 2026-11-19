package com.mohammedsharukms.mypersonaltracker.sheet

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mohammedsharukms.mypersonaltracker.R
import com.mohammedsharukms.mypersonaltracker.TrackerApp
import com.mohammedsharukms.mypersonaltracker.sheet.ui.Money
import com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetFormat
import java.time.LocalDate

// A notification when a spending bucket of the salary split (Essentials, Family
// support, Lifestyle) reaches 80% of this month's share, and again when it goes
// over — once per bucket per level per month. Checked every time the sheet data
// changes, so it covers expenses added here, bills the sheet posts by itself and
// changes made on other devices. Mirrors BudgetAlerts.swift.
object BudgetAlerts {
    const val ENABLED_KEY = "budgetAlertsEnabled"
    private const val SENT_KEY = "budgetAlertsSent"

    data class Alert(val bucket: SplitBucket, val over: Boolean, val used: Double, val planned: Double, val month: String) {
        val key: String get() = key(month, bucket, over)
        val title: String get() = if (over) "${bucket.label} is over budget" else "${bucket.label}: ${SheetFormat.percent(used / planned, 0)} used"
        val body: String get() = if (over) "${SheetFormat.qar(used - planned)} ${Money.main} over this month's ${SheetFormat.qar(planned)} — it comes out of what's left over."
            else "${SheetFormat.qar(planned - used)} ${Money.main} left of ${SheetFormat.qar(planned)} this month."
    }

    fun key(month: String, bucket: SplitBucket, over: Boolean) = "$month|${bucket.label}|${if (over) 100 else 80}"

    /** The alerts [month] has earned that haven't been sent — only the highest level per bucket. */
    fun due(s: SheetSnapshot, month: String, sent: Set<String>): List<Alert> =
        SalarySplit.lines(s, month).mapNotNull { line ->
            if (line.bucket.isSaving || line.planned <= 0) return@mapNotNull null
            val over = when {
                line.used > line.planned + 0.005 -> true
                line.fraction >= 0.8 -> false
                else -> return@mapNotNull null
            }
            Alert(line.bucket, over, line.used, line.planned, month).takeUnless { it.key in sent }
        }

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(ENABLED_KEY, true)
    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean(ENABLED_KEY, on).apply()

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Sends this month's new alerts for the latest snapshot. */
    fun check(context: Context, s: SheetSnapshot, today: LocalDate = LocalDate.now()) {
        if (!isEnabled(context)) return
        val month = today.toString().take(7)
        val prefs = prefs(context)
        val sent = prefs.getStringSet(SENT_KEY, emptySet()).orEmpty().filter { it.startsWith(month) }.toMutableSet()
        val alerts = due(s, month, sent)
        if (alerts.isEmpty()) return
        alerts.forEach {
            sent += it.key
            // Over implies nearly: don't send the 80% one later if spending is undone and redone.
            if (it.over) sent += key(month, it.bucket, false)
        }
        prefs.edit().putStringSet(SENT_KEY, sent).apply()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        alerts.forEach { alert ->
            val notification = NotificationCompat.Builder(context, TrackerApp.CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(alert.title)
                .setContentText(alert.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify("budget${alert.key}".hashCode(), notification)
        }
    }
}
