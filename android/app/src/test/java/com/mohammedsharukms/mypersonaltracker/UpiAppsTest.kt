package com.mohammedsharukms.mypersonaltracker

import com.mohammedsharukms.mypersonaltracker.sheet.FoundUpiApp
import com.mohammedsharukms.mypersonaltracker.sheet.UpiAppCatalog
import com.mohammedsharukms.mypersonaltracker.sheet.UpiAppTrust
import com.mohammedsharukms.mypersonaltracker.sheet.UpiOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Pay with UPI's app list, and the manifest entries it (and Cards) depend on. Mirrors UpiAppTests.swift. */
class UpiAppsTest {
    private val own = "com.mohammedsharukms.mypersonaltracker"
    // Unit tests run from the module folder (android/app).
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test fun listsEveryKnownAppEvenWithNoneInstalled() {
        val list = UpiAppCatalog.arrange(emptyList(), own)
        assertEquals(UpiAppCatalog.known.size + 1, list.size)
        assertEquals(UpiAppCatalog.known.map { it.packageName }, list.dropLast(1).map { it.packageName })
        assertTrue(list.dropLast(1).none { it.installed })
        assertEquals(UpiAppCatalog.ANY, list.last().packageName)
        assertTrue(list.last().installed)
    }

    @Test fun installedOnesComeFirstInTheUsualOrder() {
        val found = listOf(
            FoundUpiApp("net.one97.paytm", "Paytm"),
            FoundUpiApp("com.phonepe.app", "PhonePe"),
            FoundUpiApp("com.google.android.apps.nbu.paisa.user", "GPay"),
        )
        val list = UpiAppCatalog.arrange(found, own)
        assertEquals(listOf("com.phonepe.app", "com.google.android.apps.nbu.paisa.user", "net.one97.paytm"), list.take(3).map { it.packageName })
        assertTrue(list.take(3).all { it.installed })
        // The phone's own name for the app wins over ours.
        assertEquals("GPay", list[1].label)
        // Not repeated among the missing ones.
        assertEquals(1, list.count { it.packageName == "com.phonepe.app" })
        assertFalse(list[3].installed)
    }

    @Test fun unknownInstalledAppsFollowTheUsualOnesAlphabetically() {
        val found = listOf(
            FoundUpiApp("com.example.zeta", "Zeta Pay"),
            FoundUpiApp("com.example.alpha", "alpha bank"),
            FoundUpiApp("in.org.npci.upiapp", "BHIM"),
        )
        val list = UpiAppCatalog.arrange(found, own)
        assertEquals(listOf("in.org.npci.upiapp", "com.example.alpha", "com.example.zeta"), list.take(3).map { it.packageName })
        assertEquals(R.drawable.upi_any_app, list[1].tile)
    }

    @Test fun leavesOutThisAppAndDuplicates() {
        val found = listOf(FoundUpiApp(own, "MyPersonalTracker"), FoundUpiApp("com.phonepe.app", "PhonePe"), FoundUpiApp("com.phonepe.app", "PhonePe"))
        val list = UpiAppCatalog.arrange(found, own)
        assertTrue(list.none { it.packageName == own })
        assertEquals(1, list.count { it.packageName == "com.phonepe.app" })
        assertEquals(1, list.count { it.packageName == UpiAppCatalog.ANY })
    }

    @Test fun catalogueHasNoRepeats() {
        val packages = UpiAppCatalog.known.map { it.packageName }
        val titles = UpiAppCatalog.known.map { it.title }
        assertEquals(packages.size, packages.toSet().size)
        assertEquals(titles.size, titles.toSet().size)
        assertTrue(UpiAppCatalog.known.all { it.title.isNotBlank() })
    }

    @Test fun everyTileIsAnImageInTheApp() {
        val names = R.drawable::class.java.fields.associate { it.getInt(null) to it.name }
        (UpiAppCatalog.known.map { it.tile } + R.drawable.upi_any_app).forEach { id ->
            val name = names[id] ?: error("tile $id isn't a drawable")
            assertTrue("$name.png missing", File("src/main/res/drawable-nodpi/$name.png").isFile)
        }
    }

    @Test fun manifestLetsTheAppSeeEveryKnownApp() {
        UpiAppCatalog.known.forEach { assertTrue("${it.packageName} not in <queries>", manifest.contains("<package android:name=\"${it.packageName}\" />")) }
        // Google Pay, PhonePe… only register upi://pay, so the host must be named.
        assertTrue(manifest.contains("<data android:scheme=\"upi\" android:host=\"pay\" />"))
    }

    @Test fun onlyAppsFromGooglePlayOrThePhoneMakerCountAsVerified() {
        // Google Play never has two apps with one package name; a sideloaded copy can take any name.
        assertTrue(UpiAppTrust.isVerified(installer = "com.android.vending", isSystemApp = false))
        assertTrue(UpiAppTrust.isVerified(installer = null, isSystemApp = true))
        assertFalse(UpiAppTrust.isVerified(installer = "com.android.chrome", isSystemApp = false))
        assertFalse(UpiAppTrust.isVerified(installer = "com.google.android.packageinstaller", isSystemApp = false))
        assertFalse(UpiAppTrust.isVerified(installer = null, isSystemApp = false))
    }

    @Test fun unverifiedAppsAreStillListedButMarked() {
        val found = listOf(FoundUpiApp("com.phonepe.app", "PhonePe", verified = false), FoundUpiApp("net.one97.paytm", "Paytm"))
        val list = UpiAppCatalog.arrange(found, own)
        assertEquals(listOf(false, true), list.take(2).map { it.verified })
        assertTrue(list[0].installed)
        // "Other UPI app" is Android's own picker, not an app to vouch for.
        assertTrue(list.last().verified)
    }

    @Test fun aSuccessReplyIsTrustedOnlyFromAVerifiedApp() {
        val success = UpiOutcome.parse("txnId=1&responseCode=00&Status=SUCCESS&txnRef=MPT1", reference = "MPT1")
        assertEquals(UpiOutcome.SUCCESS, success.trusted(fromVerifiedApp = true))
        // Any app can say "SUCCESS" without paying: then we ask instead of adding it to Finance.
        assertEquals(UpiOutcome.UNKNOWN, success.trusted(fromVerifiedApp = false))
        assertEquals(UpiOutcome.FAILURE, UpiOutcome.parse("Status=FAILURE").trusted(fromVerifiedApp = false))
    }

    @Test fun aReplyAboutAnotherPaymentIsNotTrusted() {
        assertEquals(UpiOutcome.UNKNOWN, UpiOutcome.parse("Status=SUCCESS&txnRef=MPT2", reference = "MPT1"))
        assertEquals(UpiOutcome.SUCCESS, UpiOutcome.parse("Status=SUCCESS&TXNREF=MPT1", reference = "MPT1"))
        // Many apps leave txnRef out.
        assertEquals(UpiOutcome.SUCCESS, UpiOutcome.parse("Status=SUCCESS", reference = "MPT1"))
    }

    @Test fun manifestAllowsTheFingerprintPromptForCards() {
        // Without it, opening Cards crashed on every phone with a screen lock.
        assertTrue(manifest.contains("android.permission.USE_BIOMETRIC"))
    }
}
