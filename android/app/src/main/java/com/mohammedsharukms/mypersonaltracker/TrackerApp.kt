package com.mohammedsharukms.mypersonaltracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import com.mohammedsharukms.mypersonaltracker.data.PhotoStore
import com.mohammedsharukms.mypersonaltracker.data.TrackerRepository
import com.mohammedsharukms.mypersonaltracker.data.TrackerDatabase
import com.mohammedsharukms.mypersonaltracker.focus.FocusTimer
import com.mohammedsharukms.mypersonaltracker.sheet.CurrencyBook
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/** Simple manual dependency container, created once per process. */
class AppContainer(context: Context) {
    val database = TrackerDatabase.create(context)
    val photos = PhotoStore(context)
    val repository = TrackerRepository(context, database, photos)
    val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val settings = AppSettings(prefs)
    val focusTimer = FocusTimer(context, prefs, database)
    /** The Google Sheet ("Life & Finance Command Center") behind the main tabs. */
    val sheet = SheetStore(context)
    val currency = CurrencyBook(context, sheet)
    /** Pay with UPI: payments made from here, and Finance once they're paid. */
    val upi = com.mohammedsharukms.mypersonaltracker.sheet.UpiPayments(context, sheet, currency)
    val studyTimer = com.mohammedsharukms.mypersonaltracker.sheet.StudyTimer(context)
    /** More ▸ Cards: encrypted on this phone, opened behind the screen lock. */
    val cards = com.mohammedsharukms.mypersonaltracker.sheet.CardVault(
        com.mohammedsharukms.mypersonaltracker.sheet.NoBackupCardFiles(context),
        com.mohammedsharukms.mypersonaltracker.sheet.KeystoreCardKeys(context),
    )
    /** For work that should outlive a screen, e.g. saving from a notification action. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class TrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.settings.separateMorning()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Daily reminders to log your day and take your smile."
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FOCUS, "Focus timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the running focus timer."
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PAYMENTS, "Payments", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Whether a UPI payment went through, and Paid / Failed to confirm it."
            },
        )
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_FOCUS = "focus"
        const val CHANNEL_PAYMENTS = "payments"
    }
}

val Context.container: AppContainer get() = (applicationContext as TrackerApp).container

/** User preferences (the Android counterpart of the iOS @AppStorage keys). */
class AppSettings(private val prefs: SharedPreferences) {
    var remindersEnabled: Boolean
        get() = prefs.getBoolean("remindersEnabled", false)
        set(value) = prefs.edit().putBoolean("remindersEnabled", value).apply()

    /** Minutes since midnight. */
    var eveningMinutes: Int
        get() = prefs.getInt("eveningReminderMinutes", 21 * 60)
        set(value) = prefs.edit().putInt("eveningReminderMinutes", value).apply()

    var morningEnabled: Boolean
        get() = prefs.getBoolean("morningReminderEnabled", true)
        set(value) = prefs.edit().putBoolean("morningReminderEnabled", value).apply()

    var morningMinutes: Int
        get() = prefs.getInt("morningReminderMinutes", 8 * 60)
        set(value) = prefs.edit().putInt("morningReminderMinutes", value).apply()

    /** A daily "time to study" nudge (Study ▸ targets), separate from the daily-log reminders. */
    var studyReminderEnabled: Boolean
        get() = prefs.getBoolean("studyReminderEnabled", false)
        set(value) = prefs.edit().putBoolean("studyReminderEnabled", value).apply()

    var studyReminderMinutes: Int
        get() = prefs.getInt("studyReminderMinutes", 19 * 60)
        set(value) = prefs.edit().putInt("studyReminderMinutes", value).apply()

    var smileCheckInEnabled: Boolean
        get() = prefs.getBoolean("smileCheckInEnabled", true)
        set(value) = prefs.edit().putBoolean("smileCheckInEnabled", value).apply()

    /** Epoch day the smile check-in was skipped, so it isn't shown again that day. */
    var smileSkippedEpochDay: Long
        get() = prefs.getLong("smileSkippedDay", Long.MIN_VALUE)
        set(value) = prefs.edit().putLong("smileSkippedDay", value).apply()

    /** Once: the morning reminder gets its own switch instead of hanging off Daily reminders (the evening one). */
    fun separateMorning() {
        if (prefs.contains("morningReminderSeparate")) return
        prefs.edit()
            .putBoolean("morningReminderEnabled", com.mohammedsharukms.mypersonaltracker.sheet.MorningSlot.upgraded(remindersEnabled, morningEnabled))
            .putBoolean("morningReminderSeparate", true)
            .apply()
    }
}
