package com.mohammedsharukms.mypersonaltracker.focus

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mohammedsharukms.mypersonaltracker.MainActivity
import com.mohammedsharukms.mypersonaltracker.R
import com.mohammedsharukms.mypersonaltracker.TrackerApp
import com.mohammedsharukms.mypersonaltracker.container
import com.mohammedsharukms.mypersonaltracker.data.StudySession
import com.mohammedsharukms.mypersonaltracker.data.TrackerDatabase
import com.mohammedsharukms.mypersonaltracker.domain.loggedMinutes
import com.mohammedsharukms.mypersonaltracker.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A single running stopwatch for "time spent on X". State lives in SharedPreferences so it survives the
 * process being killed, and it's mirrored to an ongoing notification with a live chronometer and a Stop
 * button (the Android counterpart of the iOS Live Activity).
 */
class FocusTimer(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val database: TrackerDatabase,
) {
    data class Running(val activity: String, val startedAt: Instant)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<Running?> = _state.asStateFlow()

    fun start(activity: String, at: Instant = Instant.now()) {
        _state.value = Running(activity, at)
        persist()
        showNotification()
    }

    /** Stops the timer and logs the elapsed time on today's log. */
    suspend fun stop(at: Instant = Instant.now()): StudySession? {
        val running = _state.value ?: return null
        clear()
        val minutes = loggedMinutes(at.toEpochMilli() - running.startedAt.toEpochMilli())
        val today = LocalDate.ofInstant(at, ZoneId.systemDefault())
        val log = database.dailyLogs().forDay(today)
        database.dailyLogs().update(log.copy(showedUp = true))
        val session = StudySession(logId = log.id, title = running.activity, minutes = minutes, createdAt = at)
        val id = database.sessions().insert(session)
        WidgetUpdater.updateAll(context)
        return session.copy(id = id)
    }

    fun cancel() = clear()

    /** Re-posts the notification if a timer is running (e.g. after the app process restarted). */
    fun syncNotification() {
        if (_state.value != null) showNotification() else NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun clear() {
        _state.value = null
        persist()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun load(): Running? {
        val activity = prefs.getString(KEY_ACTIVITY, null) ?: return null
        val started = prefs.getLong(KEY_STARTED, -1).takeIf { it > 0 } ?: return null
        return Running(activity, Instant.ofEpochMilli(started))
    }

    private fun persist() {
        val running = _state.value
        prefs.edit()
            .putString(KEY_ACTIVITY, running?.activity)
            .putLong(KEY_STARTED, running?.startedAt?.toEpochMilli() ?: -1)
            .apply()
    }

    private fun showNotification() {
        val running = _state.value ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            context, 1, Intent(context, FocusActionReceiver::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, TrackerApp.CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Focusing on ${running.activity}")
            .setContentText("Tap Stop to log this time.")
            .setWhen(running.startedAt.toEpochMilli())
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(open)
            .addAction(0, "Stop & log", stop)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(running.activity.take(7))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_STOP = "com.mohammedsharukms.mypersonaltracker.FOCUS_STOP"
        private const val NOTIFICATION_ID = 42
        private const val KEY_ACTIVITY = "focusTimer.activity"
        private const val KEY_STARTED = "focusTimer.startedAt"
    }
}

/** Handles "Stop & log" from the notification without opening the app. */
class FocusActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != FocusTimer.ACTION_STOP) return
        val pending = goAsync()
        val container = context.container
        container.appScope.launch {
            try {
                container.focusTimer.stop()
            } finally {
                pending.finish()
            }
        }
    }
}
