package com.mohammedsharukms.mypersonaltracker

import com.mohammedsharukms.mypersonaltracker.sheet.AesGcm
import com.mohammedsharukms.mypersonaltracker.sheet.KeyValues
import com.mohammedsharukms.mypersonaltracker.sheet.SecretStore
import com.mohammedsharukms.mypersonaltracker.sheet.WebAppRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The web app's API key, backups and logs: what could leak the sheet's data. */
class SecretsTest {
    private class MemoryValues(vararg pairs: Pair<String, String>) : KeyValues {
        val map = mutableMapOf(*pairs)
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

    private val key = CardVaultTest.aesKey()

    @Test fun theApiKeyIsStoredEncrypted() {
        val values = MemoryValues()
        val store = SecretStore(values, AesGcm { key })
        store.set("apiKey", "f00dfacecafe1234")
        assertFalse(values.map.values.any { it.contains("f00dfacecafe1234") })
        assertEquals("f00dfacecafe1234", store.get("apiKey"))
        store.set("apiKey", null)
        assertNull(store.get("apiKey"))
        assertTrue(values.map.isEmpty())
    }

    @Test fun movesAKeySavedInTheClearByOlderVersions() {
        val old = MemoryValues("apiKey" to "plainkey", "webAppURL" to "https://x")
        val store = SecretStore(MemoryValues(), AesGcm { key })
        store.adopt("apiKey", old)
        assertEquals("plainkey", store.get("apiKey"))
        assertNull(old.get("apiKey"))
        assertEquals("https://x", old.get("webAppURL"))
        // Nothing to move: what's saved stays.
        store.adopt("apiKey", old)
        assertEquals("plainkey", store.get("apiKey"))
    }

    @Test fun aKeyThatCantBeDecryptedReadsAsMissing() {
        // E.g. restored onto a new phone, whose Keystore never had the key.
        val values = MemoryValues()
        SecretStore(values, AesGcm { key }).set("apiKey", "secret")
        assertNull(SecretStore(values, AesGcm { CardVaultTest.aesKey() }).get("apiKey"))
        assertNull(SecretStore(MemoryValues("apiKey" to "not base64!"), AesGcm { key }).get("apiKey"))
    }

    @Test fun backupsLeaveOutTheKeyAndThePaymentHistory() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        fun excluded(file: String, section: String): Set<String> {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/$file"))
            val parent = doc.getElementsByTagName(section).item(0) ?: error("$file has no <$section>")
            val children = parent.childNodes
            return (0 until children.length).map { children.item(it) }
                .filter { it.nodeName == "exclude" && it.attributes.getNamedItem("domain")?.nodeValue == "sharedpref" }
                .map { it.attributes.getNamedItem("path").nodeValue }.toSet()
        }
        val wanted = setOf("secrets.xml", "upi.xml")
        // Android 12+: cloud backup and moving to a new phone; Android 8–11: the older rules file.
        assertTrue(excluded("data_extraction_rules.xml", "cloud-backup").containsAll(wanted))
        assertTrue(excluded("data_extraction_rules.xml", "device-transfer").containsAll(wanted))
        assertTrue(excluded("backup_rules.xml", "full-backup-content").containsAll(wanted))
    }

    @Test fun webAppRequestsKeepTheKeyOutOfTheLink() {
        val body = JSONObject(WebAppRequest.body("k3y", "photo", fields = mapOf("id" to "abc", "size" to "thumb")))
        assertEquals("k3y", body.getString("key"))
        assertEquals("photo", body.getString("action"))
        assertEquals("abc", body.getString("id"))
        assertEquals("thumb", body.getString("size"))
        assertTrue(body.has("payload"))
        // Links end up in server and proxy logs; the key goes in the body.
        val source = File("src/main/java/com/mohammedsharukms/mypersonaltracker/sheet/SheetStore.kt").readText()
        assertFalse("the API key is put in a URL", Regex("""[?&]key=""").containsMatchIn(source))
    }

    @Test fun releaseBuildsLogNothing() {
        // Sheet data in the system log can be read over USB debugging or by a phone maker's log collector.
        val offenders = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "DebugLog.kt" }
            .filter { Regex("""\bLog\.[vdiwe]\(""").containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertEquals("Log through DebugLog, which is silent in release builds", emptyList<String>(), offenders)
    }
}
