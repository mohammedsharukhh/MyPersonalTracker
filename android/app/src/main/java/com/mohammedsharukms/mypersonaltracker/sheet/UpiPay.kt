package com.mohammedsharukms.mypersonaltracker.sheet

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
import com.mohammedsharukms.mypersonaltracker.container
import com.mohammedsharukms.mypersonaltracker.sheet.ui.Money
import com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetFormat
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate

// Pay with UPI and log it in one go: scan the shop's QR (or type a UPI ID), enter
// the amount, pick PhonePe / Google Pay / Paytm…, pay there and come back. The UPI
// app's answer (success / failure) is used when it sends one; otherwise the app
// asks — on return, and in a notification with Paid / Failed buttons. A paid one
// goes into Finance by itself. Payments stay on this phone. Mirrors UpiPay.swift.

/** Who you're paying: a UPI ID (VPA) and the name to show. */
data class UpiPayee(val vpa: String, val name: String = "", val mcc: String? = null) {
    val label: String get() = name.ifBlank { vpa }

    companion object {
        private val VPA = Regex("^[A-Za-z0-9.\\-_]{2,256}@[A-Za-z][A-Za-z0-9.\\-]{1,63}$")
        fun isValidVpa(text: String): Boolean = VPA.matches(text)
    }
}

/** What a UPI QR (or a pasted "upi://pay?…" link) asks for. */
data class UpiScan(val payee: UpiPayee, val amount: Double? = null, val note: String? = null) {
    companion object {
        /** Reads "upi://pay?pa=shop@okicici&pn=Shop&am=120&tn=Lunch&mc=5812", or a bare UPI ID. */
        fun parse(text: String): UpiScan? {
            val trimmed = text.trim()
            if (UpiPayee.isValidVpa(trimmed)) return UpiScan(UpiPayee(trimmed))
            if (!trimmed.lowercase().startsWith("upi://")) return null
            val query = trimmed.substringAfter('?', "")
            val values = query.split('&').mapNotNull { part ->
                val key = part.substringBefore('=').lowercase()
                val value = runCatching { URLDecoder.decode(part.substringAfter('=', ""), "UTF-8") }.getOrNull()?.trim()
                if (key.isEmpty() || value.isNullOrEmpty()) null else key to value
            }.toMap()
            val vpa = values["pa"]?.takeIf { UpiPayee.isValidVpa(it) } ?: return null
            return UpiScan(UpiPayee(vpa, values["pn"].orEmpty(), values["mc"]), values["am"]?.toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }, values["tn"])
        }
    }
}

/** One payment to hand to a UPI app (rupees: UPI pays in INR only). */
data class UpiRequest(val payee: UpiPayee, val amount: Double, val note: String, val reference: String) {
    /** upi://pay?pa=…&pn=…&am=…&cu=INR&tr=… — every UPI app on the phone answers it. */
    val uri: String
        get() {
            fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("%40", "@")
            val parts = mutableListOf("pa=${enc(payee.vpa)}", "pn=${enc(payee.name.ifBlank { payee.vpa })}",
                "am=${String.format(java.util.Locale.US, "%.2f", amount)}", "cu=INR", "tr=${enc(reference)}")
            if (note.isNotBlank()) parts += "tn=${enc(note.take(50))}"
            payee.mcc?.let { parts += "mc=${enc(it)}" }
            return "upi://pay?" + parts.joinToString("&")
        }
}

/** What a UPI app sends back ("txnId=…&responseCode=00&Status=SUCCESS&txnRef=…"), when it does. */
enum class UpiOutcome { SUCCESS, FAILURE, SUBMITTED, UNKNOWN;

    /** Any app can answer "SUCCESS" without paying: only one we can vouch for (UpiAppTrust) is believed; for the rest, we ask. */
    fun trusted(fromVerifiedApp: Boolean): UpiOutcome = if (this == SUCCESS && !fromVerifiedApp) UNKNOWN else this

    companion object {
        /** [reference] is the payment's tr=: a reply naming another payment (txnRef) isn't about this one. */
        fun parse(response: String?, reference: String? = null): UpiOutcome {
            if (response.isNullOrBlank()) return UNKNOWN
            val fields = response.split('&').associate { it.substringBefore('=').lowercase() to it.substringAfter('=', "") }
            val txnRef = fields["txnref"]?.takeIf { it.isNotBlank() }
            if (reference != null && txnRef != null && txnRef != reference) return UNKNOWN
            val status = fields["status"]?.uppercase()
            return when {
                status == "SUCCESS" || status == "S" -> SUCCESS
                status == "SUBMITTED" || status == "PENDING" -> SUBMITTED
                status == "FAILURE" || status == "FAILED" || status == "F" -> FAILURE
                else -> UNKNOWN
            }
        }
    }
}

/** What Pay with UPI says to keep a payment safe. Mirrors UpiSafety in UpiPay.swift. */
object UpiSafety {
    const val PIN = "Enter your UPI PIN only in your UPI or bank app — never on a website, in a chat or on a call."
    const val QR_NAME = "This name came from the QR code, which anyone can print. Before entering your PIN, check the name your UPI app shows — that one comes from the bank."
    fun unverified(app: String) =
        "This $app wasn't installed from Google Play, so it may not be the real $app. A fake one could take your UPI PIN. Only continue if you installed it yourself and trust it."
}

enum class PaymentStatus { WAITING, PAID, FAILED }

/** A payment made from here, kept on this phone. */
data class UpiPayment(
    val id: String,
    val date: Long,
    val payee: UpiPayee,
    val amount: Double,
    val note: String,
    val category: String,
    val app: String,
    val status: PaymentStatus = PaymentStatus.WAITING,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("date", date).put("vpa", payee.vpa).put("name", payee.name)
        .put("mcc", payee.mcc ?: "").put("amount", amount).put("note", note).put("category", category).put("app", app).put("status", status.name)

    companion object {
        fun from(o: JSONObject) = runCatching {
            UpiPayment(o.getString("id"), o.getLong("date"), UpiPayee(o.getString("vpa"), o.optString("name"), o.optString("mcc").ifBlank { null }),
                o.getDouble("amount"), o.optString("note"), o.optString("category"), o.optString("app"), PaymentStatus.valueOf(o.optString("status", "WAITING")))
        }.getOrNull()
    }
}

fun rupees(amount: Double): String = "₹" + SheetFormat.number(amount, if (amount == Math.floor(amount)) 0 else 2)

/** Who to pay, filled in when Pay opens. */
data class PayPrefill(val payee: UpiPayee, val category: String, val note: String)

class UpiPayments(private val context: Context, private val store: SheetStore, private val currency: CurrencyBook) {
    private val prefs = context.getSharedPreferences("upi", Context.MODE_PRIVATE)

    var payments by mutableStateOf(load())
        private set
    /** Waiting for "Paid?" — asked when you come back to the app. */
    var asking by mutableStateOf<UpiPayment?>(null)
    /** The Pay with UPI screen is open. */
    var showPay by mutableStateOf(false)
    /** Pay opens straight on the QR scanner (the Pay widget). */
    var payWithScan = false
    /** Pay opens with this payee filled in ("Send money" to Mom from the Parents tab). */
    var payTo: PayPrefill? = null

    val recentPayees: List<UpiPayee>
        get() = payments.map { it.payee }.distinctBy { it.vpa.lowercase() }.take(8)

    fun lastCategory(vpa: String): String? = payments.firstOrNull { it.payee.vpa.equals(vpa, true) && it.status == PaymentStatus.PAID }?.category

    /** Before the UPI app opens: the payment waits for its answer (or yours). */
    fun started(request: UpiRequest, category: String, app: String) {
        val payment = UpiPayment(request.reference, System.currentTimeMillis(), request.payee, request.amount, request.note, category, app)
        payments = listOf(payment) + payments.filter { it.id != payment.id }
        save()
        postQuestion(payment)
    }

    /** The UPI app couldn't be opened. */
    fun cancel(id: String) {
        payments = payments.filter { it.id != id }
        save()
        NotificationManagerCompat.from(context).cancel(askId(id))
    }

    /** What the UPI app said: success and failure count; anything else, we ask. */
    fun answered(id: String, outcome: UpiOutcome) {
        when (outcome) {
            UpiOutcome.SUCCESS -> markPaid(id)
            UpiOutcome.FAILURE -> markFailed(id)
            else -> payments.firstOrNull { it.id == id && it.status == PaymentStatus.WAITING }?.let { asking = it }
        }
    }

    /** Back in the app: ask about the latest payment still waiting (from the last day). */
    fun askIfWaiting() {
        if (asking != null) return
        asking = payments.firstOrNull { it.status == PaymentStatus.WAITING && System.currentTimeMillis() - it.date < 86_400_000 }
    }

    /** Paid: into Finance (in your main currency) and a notification saying so. */
    fun markPaid(id: String) {
        val p = payments.firstOrNull { it.id == id && it.status == PaymentStatus.WAITING } ?: return
        val main = Money.main
        val amount = if (main == "INR") p.amount else currency.convert(p.amount, "INR", main)
        if (amount != null) {
            val methods = store.snapshot?.lists?.methods.orEmpty()
            val method = methods.firstOrNull { it.contains("UPI", true) } ?: methods.firstOrNull { it == "Bank Transfer" } ?: methods.firstOrNull() ?: "Other"
            val notes = "UPI · ${p.app}" + (if (main == "INR") "" else " · ${rupees(p.amount)}") + (if (p.note.isBlank()) "" else " · ${p.note}")
            val date = java.time.Instant.ofEpochMilli(p.date).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
            store.saveExpense(null, date, p.payee.label, p.category, method, Math.round(amount * 100) / 100.0, notes)
        }
        update(id, PaymentStatus.PAID)
        resolve(p, paid = true, logged = amount != null)
    }

    fun markFailed(id: String) {
        val p = payments.firstOrNull { it.id == id && it.status == PaymentStatus.WAITING } ?: return
        update(id, PaymentStatus.FAILED)
        resolve(p, paid = false, logged = false)
    }

    fun delete(id: String) {
        payments = payments.filter { it.id != id }
        save()
        NotificationManagerCompat.from(context).cancel(askId(id))
    }

    private fun update(id: String, status: PaymentStatus) {
        payments = payments.map { if (it.id == id) it.copy(status = status) else it }
        if (asking?.id == id) asking = null
        save()
    }

    // MARK: - Notifications

    private fun askId(id: String) = ("upi-ask-$id").hashCode()

    private fun canNotify() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** "Did your payment go through?" with Paid / It failed — for when you don't come back to the app. */
    private fun postQuestion(p: UpiPayment) {
        if (!canNotify()) return
        fun action(paid: Boolean) = PendingIntent.getBroadcast(context, (p.id + paid).hashCode(),
            Intent(context, PaymentActionReceiver::class.java).putExtra(PaymentActionReceiver.EXTRA_ID, p.id).putExtra(PaymentActionReceiver.EXTRA_PAID, paid),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(context, askId(p.id), Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "pay"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, TrackerApp.CHANNEL_PAYMENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Did your payment go through?")
            .setContentText("${rupees(p.amount)} to ${p.payee.label} with ${p.app}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${rupees(p.amount)} to ${p.payee.label} with ${p.app}. Tap Paid to add it to Finance."))
            .setContentIntent(open)
            .addAction(0, "Paid ✓", action(true))
            .addAction(0, "It failed", action(false))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(askId(p.id), notification) }
    }

    /** The result, always as a notification too. */
    private fun resolve(p: UpiPayment, paid: Boolean, logged: Boolean) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(askId(p.id))
        if (!canNotify()) return
        val title = if (paid) "Paid ${rupees(p.amount)} ✓" else "Payment failed"
        val body = when {
            paid && logged -> "To ${p.payee.label} — added to Finance as ${p.category}."
            paid -> "To ${p.payee.label}. Add it in Finance: there's no INR rate yet to convert it."
            else -> "${rupees(p.amount)} to ${p.payee.label} didn't go through — nothing was added to Finance."
        }
        val open = PendingIntent.getActivity(context, ("upi-result-${p.id}").hashCode(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "pay"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, TrackerApp.CHANNEL_PAYMENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(("upi-result-${p.id}").hashCode(), notification) }
    }

    private fun load(): List<UpiPayment> = runCatching {
        val array = JSONArray(prefs.getString("payments", "[]"))
        (0 until array.length()).mapNotNull { UpiPayment.from(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun save() {
        payments = payments.take(60)
        prefs.edit().putString("payments", JSONArray(payments.map { it.toJson() }).toString()).apply()
    }
}

/** Paid / It failed, tapped on the notification. */
class PaymentActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val upi = context.container.upi
        if (intent.getBooleanExtra(EXTRA_PAID, false)) upi.markPaid(id) else upi.markFailed(id)
    }

    companion object {
        const val EXTRA_ID = "paymentId"
        const val EXTRA_PAID = "paid"
    }
}
