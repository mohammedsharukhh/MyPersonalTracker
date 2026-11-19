package com.mohammedsharukms.mypersonaltracker.sheet

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mohammedsharukms.mypersonaltracker.MainActivity
import com.mohammedsharukms.mypersonaltracker.R
import com.mohammedsharukms.mypersonaltracker.TrackerApp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/**
 * "Start studying" from the Study tab: a stopwatch for one subject (and
 * concept), kept in preferences so it survives the app closing, with an
 * ongoing notification showing the running time. Stopping hands back a
 * session to review and save. Mirrors StudyTimer.swift.
 */
class StudyTimer(private val context: Context) {
    private val prefs = context.getSharedPreferences("studyTimer", Context.MODE_PRIVATE)

    data class Running(val subjectId: String, val topicId: String?, val label: String, val startedAt: Long)

    var running by mutableStateOf(load())
        private set

    fun start(subject: StudySubject, topic: StudyTopic?) {
        running = Running(subject.id, topic?.id, listOfNotNull(subject.name, topic?.topic).joinToString(" · "), System.currentTimeMillis())
        persist()
        showNotification()
    }

    /** Stops the clock and returns the session it timed (not saved yet). */
    fun stop(data: StudyData, at: Long = System.currentTimeMillis()): StudyEntry? {
        val r = running ?: return null
        clear()
        val minutes = maxOf(1L, ((at - r.startedAt) / 60_000.0).roundToLong()).toDouble()
        val topic = data.topic(r.topicId)
        val date = LocalDate.ofInstant(Instant.ofEpochMilli(r.startedAt), ZoneId.systemDefault()).toString()
        return StudyEntry(date = date, subjectId = r.subjectId, subject = data.subject(r.subjectId)?.name, topicId = topic?.id,
            topic = topic?.topic, minutes = minutes,
            kind = if (topic?.stage == StudyTopic.Stage.REVISING || topic?.stage == StudyTopic.Stage.MASTERED) StudyEntry.Kind.REVISE else StudyEntry.Kind.LEARN)
    }

    fun cancel() = clear()

    private fun clear() {
        running = null
        persist()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun load(): Running? {
        val subject = prefs.getString("subject", null) ?: return null
        val started = prefs.getLong("started", -1).takeIf { it > 0 } ?: return null
        return Running(subject, prefs.getString("topic", null), prefs.getString("label", "") ?: "", started)
    }

    private fun persist() {
        val r = running
        prefs.edit().putString("subject", r?.subjectId).putString("topic", r?.topicId).putString("label", r?.label)
            .putLong("started", r?.startedAt ?: -1).apply()
    }

    private fun showNotification() {
        val r = running ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_STUDY, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, TrackerApp.CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Studying ${r.label}")
            .setContentText("Open the Study tab and tap Stop to save the session.")
            .setWhen(r.startedAt)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private companion object { const val NOTIFICATION_ID = 43 }
}
