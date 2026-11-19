package com.mohammedsharukms.mypersonaltracker.sheet

import android.content.Context
import com.mohammedsharukms.mypersonaltracker.container
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.time.LocalDate
import java.util.UUID

// What the Parents tab keeps on this phone: photos not uploaded yet and downloaded
// copies, Mom's and Dad's numbers, and "Tomorrow" snoozes. Mirrors ParentPhotos,
// ParentContacts and ParentSnooze in ParentModels.swift.

object ParentPhotos {
    private fun dir(base: File) = File(base, "parent-photos").apply { mkdirs() }
    fun localFile(context: Context, key: String) = File(dir(context.filesDir), "$key.jpg")
    private fun cacheFile(context: Context, id: String, thumb: Boolean) = File(dir(context.cacheDir), "$id-${if (thumb) "t" else "f"}.jpg")

    /** Keeps a new photo; its reference is "local:<key>" until it's on Drive. */
    fun add(context: Context, jpeg: ByteArray): String {
        val key = UUID.randomUUID().toString()
        runCatching { localFile(context, key).writeBytes(jpeg) }
        return "local:$key"
    }

    fun localKey(ref: String): String? = if (ref.startsWith("local:")) ref.removePrefix("local:") else null

    /** The photo for [ref]: on this phone, cached, or downloaded from Drive (then cached). */
    suspend fun bitmap(context: Context, store: SheetStore, ref: String, thumb: Boolean): Bitmap? {
        localKey(ref)?.let { key -> return localFile(context, key).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }
        val cached = cacheFile(context, ref, thumb)
        if (cached.exists()) return BitmapFactory.decodeFile(cached.path)
        val data = store.photo(ref, thumb) ?: return null
        runCatching { cached.writeBytes(data) }
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }
}

object ParentPrefs {
    const val MOM_PHONE = "parentMomPhone"
    const val DAD_PHONE = "parentDadPhone"
    private const val SNOOZES = "parentSnoozes"
    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun phone(context: Context, who: ParentWho): String? =
        prefs(context).getString(if (who == ParentWho.DAD) DAD_PHONE else MOM_PHONE, null)?.takeIf { ParentContacts.digits(it).isNotEmpty() }
    fun setPhone(context: Context, who: ParentWho, phone: String) =
        prefs(context).edit().putString(if (who == ParentWho.DAD) DAD_PHONE else MOM_PHONE, phone.trim()).apply()

    const val DIGEST_MINUTES = "parentDigestMinutes"
    private fun upiKey(who: ParentWho) = if (who == ParentWho.DAD) "parentDadUpi" else "parentMomUpi"

    /** Their UPI ID (name@bank), to send them money with Pay. */
    fun upi(context: Context, who: ParentWho): String? = prefs(context).getString(upiKey(who), null)?.trim()?.takeIf { UpiPayee.isValidVpa(it) }
    fun upiRaw(context: Context, who: ParentWho): String = prefs(context).getString(upiKey(who), null) ?: ""
    fun setUpi(context: Context, who: ParentWho, id: String) = prefs(context).edit().putString(upiKey(who), id.trim()).apply()
    private fun lastKey(who: ParentWho) = if (who == ParentWho.DAD) "parentDadLastTalk" else "parentMomLastTalk"
    private fun everyKey(who: ParentWho) = if (who == ParentWho.DAD) "parentDadCallEvery" else "parentMomCallEvery"

    /** When you last talked (a Call or WhatsApp from the app, or "Talked ✓"). */
    fun lastTalk(context: Context, who: ParentWho): String? = prefs(context).getString(lastKey(who), null)
    fun talked(context: Context, who: ParentWho) = prefs(context).edit().putString(lastKey(who), LocalDate.now().toString()).apply()
    /** Days between calls you'd like; 0 = no reminder. */
    fun callEvery(context: Context, who: ParentWho) = prefs(context).getInt(everyKey(who), 0)
    fun setCallEvery(context: Context, who: ParentWho, days: Int) = prefs(context).edit().putInt(everyKey(who), days).apply()
    fun digestMinutes(context: Context) = prefs(context).getInt(DIGEST_MINUTES, ParentDigest.DEFAULT_MINUTES)
    fun setDigestMinutes(context: Context, minutes: Int) = prefs(context).edit().putInt(DIGEST_MINUTES, minutes).apply()

    /** Request id → the first day it reminds again; past ones are dropped. */
    fun snoozes(context: Context): Map<String, String> {
        val today = LocalDate.now().toString()
        val raw = runCatching { org.json.JSONObject(prefs(context).getString(SNOOZES, "{}") ?: "{}") }.getOrDefault(org.json.JSONObject())
        return raw.keys().asSequence().associateWith { raw.optString(it) }.filterValues { it > today }
    }

    fun snoozeUntilTomorrow(context: Context, id: String) {
        val list = snoozes(context).toMutableMap()
        list[id] = LocalDate.now().plusDays(1).toString()
        prefs(context).edit().putString(SNOOZES, org.json.JSONObject(list as Map<*, *>).toString()).apply()
    }
}

/** Ticking off and putting back, from a row, the editor or a reminder's Done button. */
object ParentActions {
    /** Done: a repeating one moves to its next date; its cost goes to Finance if asked. */
    fun complete(context: Context, store: SheetStore, a: ParentAsk) {
        store.saveParentAsk(a.markedDone())
        val cost = a.cost
        if (a.finance && cost != null && cost > 0) {
            val lists = store.snapshot?.lists
            val category = if (lists?.categories?.contains("Gifts & Family") == true) "Gifts & Family" else lists?.categories?.lastOrNull() ?: "Other"
            store.saveExpense(null, LocalDate.now().toString(), "${a.who.label}: ${a.task}", category,
                lists?.methods?.firstOrNull() ?: "Cash", cost, ParentReminders.FINANCE_NOTE)
        }
        com.mohammedsharukms.mypersonaltracker.reminders.ReminderScheduler.reschedule(context)
    }

    /** Refilled: the new pack goes onto what's left; its cost to Finance if asked. */
    fun refill(context: Context, store: SheetStore, m: ParentMedicine, bought: Double, cost: Double?, toFinance: Boolean) {
        store.saveParentMedicine(m.refilled(bought).let { if (cost != null && cost > 0) it.copy(cost = cost) else it })
        if (cost != null && cost > 0 && toFinance) {
            val lists = store.snapshot?.lists
            val category = if (lists?.categories?.contains("Gifts & Family") == true) "Gifts & Family" else lists?.categories?.lastOrNull() ?: "Other"
            store.saveExpense(null, LocalDate.now().toString(), "${m.who.label}: ${m.name}", category,
                lists?.methods?.firstOrNull() ?: "Cash", cost, ParentReminders.FINANCE_NOTE)
        }
        com.mohammedsharukms.mypersonaltracker.reminders.ReminderScheduler.reschedule(context)
    }

    /** Opens Pay with their UPI ID filled in; a paid one goes to Finance as Remittance (so it counts as sent home). */
    /** False when their UPI ID isn't saved yet (ask for it, then call again). */
    fun sendMoney(context: Context, who: ParentWho): Boolean {
        val id = ParentPrefs.upi(context, who) ?: return false
        val upi = context.container.upi
        upi.payWithScan = false
        upi.payTo = PayPrefill(UpiPayee(id, who.raw, null), "Remittance", "For ${who.raw}")
        upi.showPay = true
        return true
    }

    fun reopen(context: Context, store: SheetStore, a: ParentAsk) {
        store.saveParentAsk(a.copy(done = null))
        com.mohammedsharukms.mypersonaltracker.reminders.ReminderScheduler.reschedule(context)
    }
}
