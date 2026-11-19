package com.mohammedsharukms.mypersonaltracker

import android.hardware.biometrics.BiometricManager.Authenticators
import com.mohammedsharukms.mypersonaltracker.sheet.AesGcm
import com.mohammedsharukms.mypersonaltracker.sheet.CardFiles
import com.mohammedsharukms.mypersonaltracker.sheet.CardFormat
import com.mohammedsharukms.mypersonaltracker.sheet.CardKeyLost
import com.mohammedsharukms.mypersonaltracker.sheet.CardKeyNotConfirmed
import com.mohammedsharukms.mypersonaltracker.sheet.CardKeys
import com.mohammedsharukms.mypersonaltracker.sheet.CardUnlock
import com.mohammedsharukms.mypersonaltracker.sheet.CardVault
import com.mohammedsharukms.mypersonaltracker.sheet.SavedCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** The card vault's encryption: the cards only open with a key the screen lock unlocks. Mirrors CardVaultTests in CardTests.swift. */
class CardVaultTest {
    private val card = SavedCard(nickname = "Millennia", bank = "HDFC", number = "4111111111111111", holder = "Test", expiryMonth = 8, expiryYear = 2029, cvv = "123")

    private class MemoryFiles : CardFiles {
        val map = mutableMapOf<String, ByteArray>()
        override fun read(name: String) = map[name]
        override fun write(name: String, data: ByteArray) { map[name] = data }
        override fun delete(name: String) { map.remove(name) }
    }

    /** The Keystore stand-in: its key works only while [confirmed] (just after the screen lock). */
    private class FakeKeys(var legacy: SecretKey? = null) : CardKeys {
        private val wrapping = aesKey()
        var confirmed = true
        var lost = false
        var resets = 0
        private fun check() { if (lost) throw CardKeyLost(); if (!confirmed) throw CardKeyNotConfirmed() }
        override fun wrap(dataKey: ByteArray): ByteArray { check(); return AesGcm { wrapping }.seal(dataKey) }
        override fun unwrap(wrapped: ByteArray): ByteArray { check(); return AesGcm { wrapping }.open(wrapped) }
        override fun openLegacy(data: ByteArray): ByteArray? = legacy?.let { key -> runCatching { AesGcm { key }.open(data) }.getOrNull() }
        override fun forgetLegacy() { legacy = null }
        override fun reset() { resets++; lost = false }
    }

    private fun bytesContain(data: ByteArray, text: String) = String(data, Charsets.ISO_8859_1).contains(text)

    @Test fun cardsOnlyOpenRightAfterTheScreenLock() {
        val files = MemoryFiles()
        val keys = FakeKeys()
        CardVault(files, keys).apply { unlocked(); assertTrue(save(card)) }
        assertFalse("card number written in the clear", bytesContain(files.map.getValue("cards.bin"), card.number))
        assertNotNull("the data key is kept only sealed by the Keystore key", files.map["cards.key"])

        // Anything in the app that skips the prompt gets nothing.
        keys.confirmed = false
        val skipped = CardVault(files, keys).apply { unlocked() }
        assertFalse(skipped.isUnlocked)
        assertTrue(skipped.cards.isEmpty())
        assertNotNull(skipped.errorMessage)

        keys.confirmed = true
        assertEquals(listOf(card), CardVault(files, keys).apply { unlocked() }.cards)
    }

    @Test fun savingStillWorksLongAfterUnlocking() {
        // The Keystore key works only for a few seconds; the vault keeps the data key in memory while unlocked.
        val files = MemoryFiles()
        val keys = FakeKeys()
        val vault = CardVault(files, keys).apply { unlocked() }
        keys.confirmed = false
        assertTrue(vault.save(card))
        keys.confirmed = true
        assertEquals(listOf(card), CardVault(files, keys).apply { unlocked() }.cards)
    }

    @Test fun movesCardsSavedBeforeTheScreenLockBinding() {
        val legacy = aesKey()
        val files = MemoryFiles().apply { map["cards.bin"] = AesGcm { legacy }.seal(CardFormat.toJson(listOf(card)).encodeToByteArray()) }
        val keys = FakeKeys(legacy)
        val vault = CardVault(files, keys).apply { unlocked() }
        assertTrue(vault.isUnlocked)
        assertEquals(listOf(card), vault.cards)
        assertNull("the old key that needed no screen lock is deleted", keys.legacy)
        assertNotNull(files.map["cards.key"])
        assertEquals(listOf(card), CardVault(files, keys).apply { unlocked() }.cards)
    }

    @Test fun aMoveCutShortStillReadsBack() {
        // Killed after the new key was saved but before the cards were sealed again.
        val legacy = aesKey()
        val files = MemoryFiles()
        val keys = FakeKeys(legacy)
        CardVault(files, keys).apply { unlocked() }   // Writes cards.key.
        files.map["cards.bin"] = AesGcm { legacy }.seal(CardFormat.toJson(listOf(card)).encodeToByteArray())
        keys.legacy = legacy
        assertEquals(listOf(card), CardVault(files, keys).apply { unlocked() }.cards)
        assertNull(keys.legacy)
    }

    @Test fun aKeyAndroidDeletedStartsOverInsteadOfStayingStuck() {
        // Turning the screen lock off makes Android delete the key: those cards can never be read again.
        val files = MemoryFiles()
        val keys = FakeKeys()
        CardVault(files, keys).apply { unlocked(); save(card) }
        keys.lost = true
        val vault = CardVault(files, keys).apply { unlocked() }
        assertTrue(vault.isUnlocked)
        assertTrue(vault.cards.isEmpty())
        assertTrue(vault.errorMessage.orEmpty().contains("screen lock"))
        assertEquals(1, keys.resets)
        assertNull(files.map["cards.bin"])
        assertTrue(vault.save(card))
    }

    @Test fun lockingForgetsTheCards() {
        val files = MemoryFiles()
        val vault = CardVault(files, FakeKeys()).apply { unlocked(); save(card) }
        vault.lock()
        assertFalse(vault.isUnlocked)
        assertTrue(vault.cards.isEmpty())
        assertFalse(vault.save(card.copy(id = "other")))
    }

    @Test fun locksThirtySecondsAfterLeavingTheApp() {
        var now = 0L
        val vault = CardVault(MemoryFiles(), FakeKeys()) { now }.apply { unlocked() }
        vault.didEnterBackground(); now += 29_000; vault.didBecomeActive()
        assertTrue(vault.isUnlocked)
        vault.didEnterBackground(); now += 31_000; vault.didBecomeActive()
        assertFalse(vault.isUnlocked)
    }

    @Test fun thePromptAcceptsOnlyStrongBiometricsOrTheScreenLock() {
        // Weak (class 2) face unlock can't unlock a Keystore key, and some are fooled by a photo.
        assertEquals(Authenticators.BIOMETRIC_STRONG, CardUnlock.AUTHENTICATORS and Authenticators.BIOMETRIC_WEAK)
        assertEquals(Authenticators.DEVICE_CREDENTIAL, CardUnlock.AUTHENTICATORS and Authenticators.DEVICE_CREDENTIAL)
        assertTrue(CardUnlock.KEY_VALIDITY_SECONDS in 5..30)
    }

    companion object {
        fun aesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }
}
