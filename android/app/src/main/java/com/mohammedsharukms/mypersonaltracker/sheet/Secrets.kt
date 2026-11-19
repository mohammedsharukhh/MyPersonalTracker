package com.mohammedsharukms.mypersonaltracker.sheet

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Secrets on this phone (the web app's API key): encrypted with a key in the
// Android Keystore, in their own prefs file (secrets.xml) that backups leave out
// (res/xml/*backup*_rules.xml). The iOS app keeps the same key in the Keychain.

/** AES-GCM with the key it's given: the 12-byte IV, then the ciphertext and tag. */
class AesGcm(private val key: () -> SecretKey) {
    fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    fun open(sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_LENGTH)) }
        return cipher.doFinal(sealed, IV_LENGTH, sealed.size - IV_LENGTH)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12

        /** A Keystore AES key by name, made on first use. It can't be read out of the phone, nor backed up. */
        fun keystoreKey(alias: String): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(alias, null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }.generateKey()
        }
    }
}

/** A few strings by name: a SharedPreferences file on the phone, a map in tests. */
interface KeyValues {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

class PrefsValues(private val prefs: SharedPreferences) : KeyValues {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String?) { prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply() }
}

class SecretStore(private val values: KeyValues, private val cipher: AesGcm) {
    /** Null when missing, or when it can't be decrypted (restored onto another phone, whose Keystore never had the key). */
    fun get(name: String): String? = values.get(name)?.let { stored ->
        runCatching { cipher.open(Base64.getDecoder().decode(stored)).decodeToString() }.getOrNull()
    }

    fun set(name: String, value: String?) {
        values.put(name, value?.takeIf { it.isNotEmpty() }?.let { Base64.getEncoder().encodeToString(cipher.seal(it.encodeToByteArray())) })
    }

    /** Moves a value older versions saved in the clear in [from], and deletes it there. */
    fun adopt(name: String, from: KeyValues) {
        val plain = from.get(name) ?: return
        if (get(name) == null) set(name, plain)
        if (get(name) != null) from.put(name, null)
    }
}
