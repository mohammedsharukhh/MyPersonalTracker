package com.mohammedsharukms.mypersonaltracker.sheet

import com.mohammedsharukms.mypersonaltracker.R

// The UPI apps Pay with UPI offers: every one, always — the ones on this phone
// first, then the usual ones it doesn't have (greyed, to get from Google Play),
// then "Other UPI app" (Android's own picker). Mirrors UpiApp in UpiPay.swift.

/** A UPI app we list even when it isn't installed: its name, and its tile (res/drawable-nodpi). */
data class KnownUpiApp(val packageName: String, val title: String, val tile: Int)

/** An app on this phone that answers upi://pay links; [verified] per UpiAppTrust. */
data class FoundUpiApp(val packageName: String, val label: String, val verified: Boolean = true)

/** One tile in the Pay with grid. [verified] is false only for an installed app UpiAppTrust can't vouch for. */
data class UpiListing(val packageName: String, val label: String, val installed: Boolean, val tile: Int, val verified: Boolean = true)

/**
 * Whether an installed app is the real one. Google Play never has two apps with one
 * package name, but a sideloaded app can take any name — a fake "PhonePe" could ask
 * for the UPI PIN. So only apps from Google Play, or put on the phone by its maker, count.
 */
object UpiAppTrust {
    private const val GOOGLE_PLAY = "com.android.vending"

    fun isVerified(installer: String?, isSystemApp: Boolean): Boolean = isSystemApp || installer == GOOGLE_PLAY
}

object UpiAppCatalog {
    /** "Other UPI app": Android's own picker of whatever can pay. */
    const val ANY = "*"

    /** The usual UPI apps, in the order they're offered. The same packages are in the manifest's <queries>. */
    val known = listOf(
    KnownUpiApp("com.phonepe.app", "PhonePe", R.drawable.upi_phone_pe),
    KnownUpiApp("com.google.android.apps.nbu.paisa.user", "Google Pay", R.drawable.upi_google_pay),
    KnownUpiApp("net.one97.paytm", "Paytm", R.drawable.upi_paytm),
    KnownUpiApp("in.org.npci.upiapp", "BHIM", R.drawable.upi_bhim),
    KnownUpiApp("com.dreamplug.androidapp", "CRED", R.drawable.upi_cred),
    KnownUpiApp("in.amazon.mShop.android.shopping", "Amazon Pay", R.drawable.upi_amazon_pay),
    KnownUpiApp("com.whatsapp", "WhatsApp", R.drawable.upi_whats_app),
    KnownUpiApp("money.super.payments", "super.money", R.drawable.upi_super_money),
    KnownUpiApp("com.naviapp", "Navi", R.drawable.upi_navi),
    KnownUpiApp("com.mobikwik_new", "MobiKwik", R.drawable.upi_mobikwik),
    KnownUpiApp("com.freecharge.android", "Freecharge", R.drawable.upi_freecharge),
    KnownUpiApp("com.myairtelapp", "Airtel", R.drawable.upi_airtel),
    KnownUpiApp("com.tatadigital.tcp", "Tata Neu", R.drawable.upi_tata_neu),
    KnownUpiApp("club.pop.android", "POP", R.drawable.upi_pop),
    KnownUpiApp("in.gokiwi.kiwitap", "Kiwi", R.drawable.upi_kiwi),
    KnownUpiApp("com.fampay.in", "FamPay", R.drawable.upi_fam_pay),
    KnownUpiApp("com.bharatpe.app", "BharatPe", R.drawable.upi_bharat_pe),
    KnownUpiApp("com.snapmint.customerapp", "Snapmint", R.drawable.upi_snapmint),
    KnownUpiApp("indwin.c3.shareapp", "slice", R.drawable.upi_slice),
    KnownUpiApp("money.jupiter", "Jupiter", R.drawable.upi_jupiter),
    KnownUpiApp("com.epifi.paisa", "Fi", R.drawable.upi_fi),
    KnownUpiApp("com.nextbillion.groww", "Groww", R.drawable.upi_groww),
    KnownUpiApp("com.sbi.upi", "BHIM SBI Pay", R.drawable.upi_bhim_sbi),
    KnownUpiApp("com.sbi.lotusintouch", "YONO SBI", R.drawable.upi_yono),
    KnownUpiApp("com.csam.icici.bank.imobile", "iMobile Pay", R.drawable.upi_i_mobile),
    KnownUpiApp("com.axis.mobile", "Axis Mobile", R.drawable.upi_axis),
    KnownUpiApp("com.upi.axispay", "Axis Pay", R.drawable.upi_axis),
    KnownUpiApp("com.msf.kbank.mobile", "Kotak", R.drawable.upi_kotak),
    KnownUpiApp("com.enstage.wibmo.hdfc", "PayZapp", R.drawable.upi_pay_zapp),
    KnownUpiApp("com.snapwork.hdfc", "HDFC Bank", R.drawable.upi_hdfc),
    )

    /** The usual UPI apps first, then the rest. */
    fun order(packageName: String): Int = known.indexOfFirst { it.packageName == packageName }.let { if (it < 0) Int.MAX_VALUE else it }

    /**
     * The grid: what's installed (in the usual order, then A–Z; never this app itself), then the
     * usual ones that aren't, then "Other UPI app".
     */
    fun arrange(found: List<FoundUpiApp>, ownPackage: String): List<UpiListing> {
        val installed = found.filter { it.packageName != ownPackage && it.packageName != ANY }
            .distinctBy { it.packageName }
            .sortedWith(compareBy({ order(it.packageName) }, { it.label.lowercase() }))
            .map { app -> UpiListing(app.packageName, app.label, true, known.firstOrNull { it.packageName == app.packageName }?.tile ?: R.drawable.upi_any_app, app.verified) }
        val have = installed.map { it.packageName }.toSet()
        val missing = known.filter { it.packageName !in have }.map { UpiListing(it.packageName, it.title, false, it.tile) }
        return installed + missing + UpiListing(ANY, "Other UPI app", true, R.drawable.upi_any_app)
    }
}
