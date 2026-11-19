package com.mohammedsharukms.mypersonaltracker.sheet

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mohammedsharukms.mypersonaltracker.sheet.ui.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Currency converter covering every ISO currency, based on the workbook's main
 * currency (Settings ▸ Currency): every rate reads "1 main = x". Where a rate
 * comes from, in priority order: the user's own rate, the sheet's exchange rate
 * (for the home currency), live rates (downloaded and cached for offline use),
 * then the fixed Gulf pegs. Mirrors CurrencyBook in CurrencyConverter.swift.
 */
class CurrencyBook(context: Context, private val sheet: SheetStore) {
    enum class Source { BASE, CUSTOM, SHEET, LIVE, PEG, NONE }

    /** A rate the user typed: 1 [base] = [value] of the currency, kept with its base. */
    data class Override(val base: String, val value: Double)

    private val prefs = context.getSharedPreferences("currencyBook", Context.MODE_PRIVATE)

    var overrides by mutableStateOf(mapOf<String, Override>()); private set
    var customNames by mutableStateOf(mapOf<String, String>()); private set
    var favorites by mutableStateOf(DEFAULT_FAVORITES); private set
    /** Live rates as published: 1 liveBase = x. */
    var live by mutableStateOf(mapOf<String, Double>()); private set
    private var liveBase = "QAR"
    /** When the provider last published the downloaded rates (epoch millis). */
    var liveDate by mutableStateOf<Long?>(null); private set
    private var liveFetched: Long? = null
    var isRefreshing by mutableStateOf(false); private set
    var refreshError by mutableStateOf<String?>(null); private set

    var useSheetRateForHome by mutableStateOf(true); private set
    /** The sheet's currencies when last seen, to notice a change. */
    private var seenMain = Money.main
    private var seenHome = Money.home
    var from by mutableStateOf(Money.main); private set
    var to by mutableStateOf(Money.home); private set

    init {
        prefs.getString(STORAGE_KEY, null)?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                overrides = o.optJSONObject("overrides")?.let { m ->
                    m.keys().asSequence().associateWith { k -> m.getJSONObject(k).let { Override(it.getString("base"), it.getDouble("value")) } }
                } ?: emptyMap()
                customNames = o.optJSONObject("customNames")?.let { n -> n.keys().asSequence().associateWith { n.getString(it) } } ?: emptyMap()
                favorites = o.optJSONArray("favorites")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: DEFAULT_FAVORITES
                live = o.optJSONObject("live").doubles()
                liveBase = o.optString("liveBase", "QAR")
                liveDate = o.optLong("liveDate").takeIf { it > 0 }
                liveFetched = o.optLong("liveFetched").takeIf { it > 0 }
                useSheetRateForHome = o.optBoolean("useSheetRateForHome", true)
                from = o.optString("from", Money.main)
                to = o.optString("to", Money.home)
                seenMain = o.optString("seenMain", Money.main)
                seenHome = o.optString("seenHome", Money.home)
            }
        }
    }

    private fun save() {
        val o = JSONObject()
            .put("overrides", JSONObject().apply { overrides.forEach { (k, v) -> put(k, JSONObject().put("base", v.base).put("value", v.value)) } })
            .put("customNames", JSONObject(customNames))
            .put("favorites", JSONArray(favorites))
            .put("live", JSONObject(live))
            .put("liveBase", liveBase)
            .put("liveDate", liveDate ?: 0)
            .put("liveFetched", liveFetched ?: 0)
            .put("useSheetRateForHome", useSheetRateForHome)
            .put("from", from)
            .put("to", to)
            .put("seenMain", seenMain)
            .put("seenHome", seenHome)
        prefs.edit().putString(STORAGE_KEY, o.toString()).apply()
    }

    /**
     * The sheet is the source of truth for currencies: when its main or home
     * currency changes, the converter's pair follows and rates re-base.
     */
    fun followSheet() {
        val main = Money.main
        val home = Money.home
        if (main == seenMain && home == seenHome) return
        if (from == seenMain || from == seenHome) from = main
        if (to == seenHome || to == seenMain) to = home
        favorites = (listOf(main, home).filter { it !in favorites }) + favorites
        seenMain = main
        seenHome = home
        liveFetched = null   // fetch rates based on the new main currency
        save()
    }

    fun chooseFrom(code: String) { from = code; save() }
    fun chooseTo(code: String) { to = code; save() }
    fun swap() { val f = from; from = to; to = f; save() }
    fun useSheetRate(value: Boolean) { useSheetRateForHome = value; save() }

    // MARK: Currencies

    /** The workbook's main currency: every rate is "1 main = x". */
    val main: String get() = Money.main

    /** Every currency we can name: the ISO list, whatever the rate feed has, and the user's own. */
    val allCodes: List<String>
        get() = (Currency.getAvailableCurrencies().map { it.currencyCode } + live.keys + overrides.keys + customNames.keys + main + Money.home)
            .toSet()
            .filter { it !in OBSOLETE }
            .sortedBy { name(it).lowercase() }

    fun name(code: String): String =
        customNames[code] ?: runCatching { Currency.getInstance(code).getDisplayName(Locale.getDefault()) }.getOrNull() ?: code

    fun isFavorite(code: String) = code in favorites

    fun toggleFavorite(code: String) {
        favorites = if (code in favorites) favorites - code else favorites + code
        save()
    }

    // MARK: Rates

    val sheetRate: Double? get() = sheet.snapshot?.settings?.rate

    fun source(code: String): Source = when {
        code == main -> Source.BASE
        overrides[code] != null -> Source.CUSTOM
        // The sheet's rate only counts while it was set for the current pair.
        code == Money.home && useSheetRateForHome && sheetRate != null && sheet.snapshot?.settings?.rateIsCurrent == true -> Source.SHEET
        live[code] != null && live[main] != null -> Source.LIVE
        PEGS_PER_USD[code] != null && PEGS_PER_USD[main] != null -> Source.PEG
        else -> Source.NONE
    }

    /** 1 main = rate(code), or null when no rate is known yet. */
    fun rate(code: String): Double? = when (source(code)) {
        Source.BASE -> 1.0
        Source.CUSTOM -> overrides[code]?.let { o -> if (o.base == main) o.value else marketRate(main, o.base)?.times(o.value) }
        Source.SHEET -> sheetRate
        Source.LIVE, Source.PEG -> marketRate(main, code)
        Source.NONE -> null
    }

    /** Live (or pegged) rate for any pair, ignoring the user's and the sheet's rates. */
    fun liveRate(from: String, to: String): Double? = marketRate(from, to)

    private fun marketRate(from: String, to: String): Double? {
        if (from == to) return 1.0
        val lf = live[from]; val lt = live[to]
        if (lf != null && lt != null && lf > 0) return lt / lf
        val pf = PEGS_PER_USD[from]; val pt = PEGS_PER_USD[to]
        if (pf != null && pt != null) return pt / pf
        return null
    }

    fun convert(amount: Double, from: String, to: String): Double? {
        val fromRate = rate(from) ?: return null
        val toRate = rate(to) ?: return null
        if (fromRate <= 0) return null
        return amount / fromRate * toRate
    }

    /** Sets (or with null, clears) the user's rate: 1 main = [perMain] of [code]. */
    fun setOverride(code: String, perMain: Double?, name: String? = null) {
        overrides = if (perMain == null) overrides - code else overrides + (code to Override(main, perMain))
        val isoName = runCatching { Currency.getInstance(code) }.isSuccess
        if (!name.isNullOrBlank() && !isoName) customNames = customNames + (code to name)
        if (code !in favorites) favorites = favorites + code
        save()
    }

    // MARK: Live rates

    suspend fun refreshIfStale() {
        followSheet()
        val stale = liveFetched?.let { System.currentTimeMillis() - it > 6 * 3600_000L } ?: true
        if (live.isEmpty() || stale) refresh()
    }

    suspend fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        try {
            val base = main
            val body = withContext(Dispatchers.IO) {
                val connection = (URL(RATES_URL + base).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 20_000
                }
                try {
                    JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }
            }
            val rates = body.optJSONObject("rates").doubles()
            if (body.optString("result") != "success" || rates.isEmpty()) throw IllegalStateException()
            live = rates
            liveBase = body.optString("base_code", base)
            liveDate = (body.optLong("time_last_update_unix") * 1000).takeIf { it > 0 } ?: System.currentTimeMillis()
            liveFetched = System.currentTimeMillis()
            refreshError = null
            save()
        } catch (e: Exception) {
            refreshError = if (live.isEmpty()) "Couldn't download rates. Check your connection, or set a rate yourself."
            else "Couldn't update. Showing the last saved rates."
        } finally {
            isRefreshing = false
        }
    }

    companion object {
        private const val STORAGE_KEY = "currencyBook.v3"
        private const val RATES_URL = "https://open.er-api.com/v6/latest/"
        val DEFAULT_FAVORITES = listOf("QAR", "INR", "USD", "AED", "SAR", "EUR", "GBP")

        /** Units per US dollar for currencies pegged to it, so exact offline. */
        private val PEGS_PER_USD = mapOf(
            "USD" to 1.0, "QAR" to 3.64, "AED" to 3.6725, "SAR" to 3.75, "OMR" to 0.3845, "BHD" to 0.376, "JOD" to 0.709,
        )

        /** Codes Java still lists that no longer circulate, plus fund/metal codes. */
        private val OBSOLETE = setOf(
            "ADP", "AFA", "ATS", "AYM", "AZM", "BEF", "BGL", "BOV", "BYB", "BYR", "CHE", "CHW", "CLF", "COU", "CSD", "CUC", "CYP",
            "DEM", "EEK", "ESP", "FIM", "FRF", "GHC", "GRD", "GWP", "HRK", "IEP", "ITL", "LTL", "LUF", "LVL", "MGF", "MRO", "MTL",
            "MXV", "MZM", "NLG", "PTE", "ROL", "RUR", "SDD", "SIT", "SKK", "SLL", "SRG", "STD", "TMM", "TPE", "TRL", "USN", "USS",
            "UYI", "VEB", "VEF", "XAG", "XAU", "XBA", "XBB", "XBC", "XBD", "XFO", "XFU", "XPD", "XPT", "XSU", "XTS", "XUA", "XXX",
            "YUM", "ZMK", "ZWD", "ZWN", "ZWR", "ZWL",
        )

        fun flag(code: String): String {
            val special = mapOf("EUR" to "🇪🇺", "XAF" to "🌍", "XOF" to "🌍", "XCD" to "🌴", "XPF" to "🌊", "ANG" to "🇨🇼", "XDR" to "🌐")
            special[code]?.let { return it }
            val letters = code.uppercase().take(2)
            if (letters.length != 2 || !letters.all { it in 'A'..'Z' } || letters[0] == 'X') return "💱"
            return letters.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
        }

        fun format(amount: Double): String {
            val digits = if (kotlin.math.abs(amount) >= 100) 2 else if (kotlin.math.abs(amount) < 1 && amount != 0.0) 4 else 2
            return NumberFormat.getNumberInstance().apply { minimumFractionDigits = 0; maximumFractionDigits = digits }.format(amount)
        }
    }
}

private fun JSONObject?.doubles(): Map<String, Double> =
    if (this == null) emptyMap() else keys().asSequence().mapNotNull { k -> optDouble(k).takeIf { !it.isNaN() }?.let { k to it } }.toMap()
