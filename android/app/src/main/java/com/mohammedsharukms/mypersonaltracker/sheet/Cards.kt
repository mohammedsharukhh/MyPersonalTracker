package com.mohammedsharukms.mypersonaltracker.sheet

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

// More ▸ Cards: your debit and credit cards, kept on this phone only —
// encrypted with a key that only the fingerprint or screen lock unlocks (Android
// Keystore), never sent to the Google Sheet, left out of Export everything and of
// backups. Mirrors CardModels.swift.

enum class CardKind(val label: String) { CREDIT("Credit"), DEBIT("Debit"), PREPAID("Prepaid"), FOREX("Forex") }

/** The card's colour in the app (white text sits on all of them). */
enum class CardStyle(val from: Long, val to: Long) {
    NAVY(0xFF1F3864, 0xFF2F5597),
    TEAL(0xFF0F5132, 0xFF0F766E),
    PURPLE(0xFF4C1D95, 0xFF7C3AED),
    GRAPHITE(0xFF1F2937, 0xFF4B5563),
    GOLD(0xFF78350F, 0xFFB45309),
    ROSE(0xFF881337, 0xFFBE123C),
}

enum class CardExpiry { FINE, SOON, EXPIRED }

data class SavedCard(
    val id: String = UUID.randomUUID().toString(),
    val nickname: String = "",
    val bank: String = "",
    val kind: CardKind = CardKind.CREDIT,
    /** Digits only. */
    val number: String = "",
    val holder: String = "",
    val expiryMonth: Int? = null,
    /** Four digits, e.g. 2029. */
    val expiryYear: Int? = null,
    val cvv: String = "",
    val notes: String = "",
    val style: CardStyle = CardStyle.NAVY,
) {
    val brand: CardBrand get() = CardBrand.of(number)
    val last4: String get() = number.takeLast(4)

    /** "HDFC Millennia", else "HDFC Visa", else "Visa credit card". */
    val title: String get() {
        nickname.trim().takeIf { it.isNotEmpty() }?.let { return it }
        val brandName = brand.label
        bank.trim().takeIf { it.isNotEmpty() }?.let { return listOf(it, brandName).filter(String::isNotEmpty).joinToString(" ") }
        return listOf(brandName, "${kind.label.lowercase()} card").filter(String::isNotEmpty).joinToString(" ")
    }

    val maskedNumber: String get() = if (number.length >= 4) "•••• $last4" else "••••"
    val groupedNumber: String get() = CardFormat.grouped(number)

    /** "08/29", or "" when not set. */
    val expiryText: String get() =
        if (expiryMonth != null && expiryYear != null) "%02d/%02d".format(expiryMonth, expiryYear % 100) else ""

    /** Soon is within 60 days of the last day of its month. */
    fun expiryState(today: LocalDate = LocalDate.now()): CardExpiry {
        if (expiryMonth == null || expiryYear == null) return CardExpiry.FINE
        val firstAfter = LocalDate.of(expiryYear, expiryMonth, 1).plusMonths(1)
        if (!today.isBefore(firstAfter)) return CardExpiry.EXPIRED
        return if (ChronoUnit.DAYS.between(today, firstAfter) <= 60) CardExpiry.SOON else CardExpiry.FINE
    }

    /** What TalkBack reads for a card in the list. */
    fun spokenSummary(today: LocalDate = LocalDate.now()): String {
        val parts = mutableListOf(title, "${kind.label} card")
        if (number.length >= 4) parts += "ending ${last4.toList().joinToString(" ")}"
        if (expiryText.isNotEmpty()) parts += "expires $expiryText"
        when (expiryState(today)) {
            CardExpiry.EXPIRED -> parts += "expired"
            CardExpiry.SOON -> parts += "expires soon"
            CardExpiry.FINE -> Unit
        }
        return parts.joinToString(", ")
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("nickname", nickname).put("bank", bank).put("kind", kind.name)
        .put("number", number).put("holder", holder)
        .put("expiryMonth", expiryMonth ?: JSONObject.NULL).put("expiryYear", expiryYear ?: JSONObject.NULL)
        .put("cvv", cvv).put("notes", notes).put("style", style.name)

    companion object {
        fun fromJson(o: JSONObject) = SavedCard(
            id = o.getString("id"),
            nickname = o.optString("nickname"),
            bank = o.optString("bank"),
            kind = runCatching { CardKind.valueOf(o.optString("kind")) }.getOrDefault(CardKind.CREDIT),
            number = o.optString("number"),
            holder = o.optString("holder"),
            expiryMonth = if (o.isNull("expiryMonth")) null else o.optInt("expiryMonth"),
            expiryYear = if (o.isNull("expiryYear")) null else o.optInt("expiryYear"),
            cvv = o.optString("cvv"),
            notes = o.optString("notes"),
            style = runCatching { CardStyle.valueOf(o.optString("style")) }.getOrDefault(CardStyle.NAVY),
        )
    }
}

enum class CardBrand(val label: String, val mark: String, val maxLength: Int = 19) {
    VISA("Visa", "VISA"),
    MASTERCARD("Mastercard", "MASTERCARD"),
    AMEX("American Express", "AMEX", 15),
    RUPAY("RuPay", "RUPAY"),
    DISCOVER("Discover", "DISCOVER"),
    DINERS("Diners Club", "DINERS", 14),
    JCB("JCB", "JCB"),
    MAESTRO("Maestro", "MAESTRO"),
    UNKNOWN("", "");

    val cvvLength: Int get() = if (this == AMEX) 4 else 3

    companion object {
        /** From the card's first digits. */
        fun of(number: String): CardBrand {
            val d = number.filter(Char::isDigit)
            fun starts(vararg values: String) = values.any { d.startsWith(it) }
            fun within(length: Int, range: IntRange) = d.length >= length && d.take(length).toInt() in range
            return when {
                starts("34", "37") -> AMEX
                starts("4") -> VISA
                within(2, 51..55) || within(4, 2221..2720) -> MASTERCARD
                starts("6011") || within(3, 644..649) -> DISCOVER
                starts("60", "81", "82", "508", "6521", "6522") -> RUPAY
                starts("65") -> DISCOVER
                starts("36", "38", "39") || within(3, 300..305) -> DINERS
                within(4, 3528..3589) -> JCB
                starts("50", "56", "57", "58", "63", "67") -> MAESTRO
                else -> UNKNOWN
            }
        }
    }
}

object CardFormat {
    /** Digit counts after which a space goes: 4-4-4-4-…, American Express 4-6-5, Diners 4-6-4. */
    fun groupEnds(number: String): List<Int> {
        val length = number.count(Char::isDigit)
        return when (CardBrand.of(number)) {
            CardBrand.AMEX -> listOf(4, 10)
            CardBrand.DINERS -> if (length <= 14) listOf(4, 10) else listOf(4, 8, 12, 16)
            else -> listOf(4, 8, 12, 16)
        }.filter { it < length }
    }

    /** "4111 1111 1111 1111"; American Express "3782 822463 10005". */
    fun grouped(number: String): String {
        val digits = number.filter(Char::isDigit)
        val ends = groupEnds(digits)
        return buildString {
            digits.forEachIndexed { i, c -> if (i in ends) append(' '); append(c) }
        }
    }

    /** The Luhn check every real card number passes (catches most typos). */
    fun passesLuhn(number: String): Boolean {
        val digits = number.filter(Char::isDigit).map { it - '0' }
        if (digits.size < 12) return false
        val sum = digits.reversed().withIndex().sumOf { (i, digit) ->
            if (i % 2 == 1) (digit * 2).let { if (it > 9) it - 9 else it } else digit
        }
        return sum % 10 == 0
    }

    /** "0829" or "08/29" → (8, 2029). Null unless the month is 1–12. */
    fun expiry(text: String): Pair<Int, Int>? {
        val digits = text.filter(Char::isDigit)
        if (digits.length != 4) return null
        val month = digits.take(2).toInt()
        return if (month in 1..12) month to 2000 + digits.takeLast(2).toInt() else null
    }

    /** What the expiry field shows while typing: "0", "08", "08/2", "08/29". */
    fun expiryField(text: String): String {
        val digits = text.filter(Char::isDigit).take(4)
        return if (digits.length > 2) "${digits.take(2)}/${digits.drop(2)}" else digits
    }

    fun toJson(cards: List<SavedCard>): String = JSONArray(cards.map { it.toJson() }).toString()

    fun fromJson(json: String): List<SavedCard> {
        val array = JSONArray(json)
        return (0 until array.length()).map { SavedCard.fromJson(array.getJSONObject(it)) }
    }
}

/** Where the vault keeps its two files: no-backup storage on the phone, a map in tests. */
interface CardFiles {
    fun read(name: String): ByteArray?
    fun write(name: String, data: ByteArray)
    fun delete(name: String)
}

class NoBackupCardFiles(context: Context) : CardFiles {
    private val folder = context.noBackupFilesDir
    private fun file(name: String) = AtomicFile(File(folder, name))
    override fun read(name: String): ByteArray? = file(name).takeIf { it.baseFile.exists() }?.readFully()
    override fun write(name: String, data: ByteArray) {
        val file = file(name)
        val stream = file.startWrite()
        try {
            stream.write(data)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream); throw e
        }
    }
    override fun delete(name: String) { file(name).delete() }
}

/** The Keystore key has gone for good (Android deletes it when the screen lock is turned off). */
class CardKeyLost : Exception()
/** The Keystore key wasn't unlocked: the screen lock wasn't confirmed just now. */
class CardKeyNotConfirmed : Exception()

/**
 * The Keystore side. The cards are encrypted with a random data key; this seals
 * that data key with a Keystore key that works only for a few seconds after the
 * fingerprint or screen lock is confirmed, so nothing in the app can read the
 * cards without the person.
 */
interface CardKeys {
    fun wrap(dataKey: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray
    /** Cards saved by earlier versions, sealed straight with a Keystore key that needed no screen lock. Null without it. */
    fun openLegacy(data: ByteArray): ByteArray?
    fun forgetLegacy()
    /** A fresh Keystore key, after the old one was lost. */
    fun reset()
}

/** What may unlock the cards: strong (class 3) biometrics or the screen lock. Weak face unlock can't unlock a Keystore key. */
object CardUnlock {
    const val AUTHENTICATORS = android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
        android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
    /** How long after the prompt the Keystore key works: just enough to open the data key. */
    const val KEY_VALIDITY_SECONDS = 15
}

class KeystoreCardKeys(private val context: Context) : CardKeys {
    override fun wrap(dataKey: ByteArray): ByteArray = keystore { AesGcm { wrappingKey() }.seal(dataKey) }
    override fun unwrap(wrapped: ByteArray): ByteArray = keystore { AesGcm { wrappingKey() }.open(wrapped) }

    override fun openLegacy(data: ByteArray): ByteArray? {
        val key = store().getKey(LEGACY_ALIAS, null) as? SecretKey ?: return null
        return runCatching { AesGcm { key }.open(data) }.getOrNull()
    }

    override fun forgetLegacy() { runCatching { store().deleteEntry(LEGACY_ALIAS) } }
    override fun reset() { runCatching { store().deleteEntry(WRAP_ALIAS) } }

    private fun store() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun <T> keystore(work: () -> T): T = try {
        work()
    } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
        throw CardKeyLost()
    } catch (e: android.security.keystore.UserNotAuthenticatedException) {
        throw CardKeyNotConfirmed()
    }

    private fun wrappingKey(): SecretKey {
        (store().getKey(WRAP_ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(WRAP_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        // Only the debug build on an emulator without a screen lock gets this far without one (CardsScreen).
        if (context.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure) {
            spec.setUserAuthenticationRequired(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                spec.setUserAuthenticationParameters(CardUnlock.KEY_VALIDITY_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
            } else {
                @Suppress("DEPRECATION") spec.setUserAuthenticationValidityDurationSeconds(CardUnlock.KEY_VALIDITY_SECONDS)
            }
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec.build()) }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val LEGACY_ALIAS = "saved_cards"
        const val WRAP_ALIAS = "saved_cards_locked"
    }
}

/**
 * The saved cards, decrypted only after the screen lock is confirmed, and
 * dropped from memory (with their data key) again when locked.
 */
class CardVault(private val files: CardFiles, private val keys: CardKeys, private val now: () -> Long = System::currentTimeMillis) {
    var cards by mutableStateOf<List<SavedCard>>(emptyList())
        private set
    var isUnlocked by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
    private var backgroundedAt: Long? = null
    /** Only while unlocked. */
    private var dataKey: ByteArray? = null

    /** Called once the fingerprint, face or screen lock was confirmed. */
    fun unlocked() = open(mayStartOver = true)

    private fun open(mayStartOver: Boolean) {
        try {
            val key = openDataKey()
            val sealed = files.read(CARDS)
            val list = when {
                sealed == null -> emptyList()
                else -> runCatching { open(sealed, key) }.getOrNull()
                    // Saved by an earlier version (or moved over only halfway): seal it again with the data key.
                    ?: keys.openLegacy(sealed)?.let { plain -> CardFormat.fromJson(plain.decodeToString()).also { files.write(CARDS, seal(it, key)) } }
                    ?: throw IllegalStateException("unreadable")
            }
            keys.forgetLegacy()
            dataKey = key
            cards = list
            isUnlocked = true
            errorMessage = null
        } catch (e: CardKeyLost) {
            if (!mayStartOver) { errorMessage = "Your saved cards couldn't be read."; return }
            // Never readable again: start over rather than stay locked for good.
            files.delete(CARDS); files.delete(DATA_KEY); keys.reset(); keys.forgetLegacy()
            open(mayStartOver = false)
            if (isUnlocked) errorMessage = "Your saved cards were removed by Android because the screen lock was turned off or reset. Add them again."
        } catch (e: CardKeyNotConfirmed) {
            errorMessage = "Unlock with your fingerprint or screen lock to see your cards."
        } catch (e: Exception) {
            // Never open (and later overwrite) a list that didn't read back whole.
            errorMessage = "Your saved cards couldn't be read."
        }
    }

    fun lock() {
        cards = emptyList()
        dataKey?.fill(0)
        dataKey = null
        isUnlocked = false
        backgroundedAt = null
    }

    fun didEnterBackground() { if (isUnlocked) backgroundedAt = now() }

    fun didBecomeActive() {
        backgroundedAt?.let { if (now() - it > BACKGROUND_GRACE_MS) lock() }
        backgroundedAt = null
    }

    /** Adds or replaces it by id. False (with a message) if it couldn't be written. */
    fun save(card: SavedCard): Boolean {
        val list = if (cards.any { it.id == card.id }) cards.map { if (it.id == card.id) card else it } else cards + card
        return write(list)
    }

    fun delete(id: String) { write(cards.filterNot { it.id == id }) }

    /** The data key, made (and sealed by the Keystore) the first time. */
    private fun openDataKey(): ByteArray {
        files.read(DATA_KEY)?.let { return keys.unwrap(it) }
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        files.write(DATA_KEY, keys.wrap(key))
        return key
    }

    private fun write(list: List<SavedCard>): Boolean {
        val key = dataKey
        if (!isUnlocked || key == null) return false
        val ok = runCatching { if (list.isEmpty()) files.delete(CARDS) else files.write(CARDS, seal(list, key)) }.isSuccess
        if (ok) { cards = list; errorMessage = null } else errorMessage = "The card couldn't be saved. Try again."
        return ok
    }

    private fun cipher(key: ByteArray) = AesGcm { SecretKeySpec(key, "AES") }
    private fun seal(list: List<SavedCard>, key: ByteArray) = cipher(key).seal(CardFormat.toJson(list).encodeToByteArray())
    private fun open(sealed: ByteArray, key: ByteArray) = CardFormat.fromJson(cipher(key).open(sealed).decodeToString())

    private companion object {
        const val CARDS = "cards.bin"
        const val DATA_KEY = "cards.key"
        /** Long enough to copy a number into another app and come back for the next one. */
        const val BACKGROUND_GRACE_MS = 30_000L
    }
}
