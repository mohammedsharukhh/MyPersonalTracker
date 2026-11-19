package com.mohammedsharukms.mypersonaltracker

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Android carries its own copy of the sheet's script and workbook design; it must stay byte for byte
 * the iOS one (the auto-deploy hook copies both), or the two apps work out different numbers.
 */
class BackendCopiesTest {
    private val ios = File("../../MyPersonalTracker")

    @Test fun androidRunsTheSameScriptAsIOS() {
        val android = File("src/main/assets/backend")
        val iosScript = File(ios, "Resources/Backend")
        val names = android.list()!!.sorted()
        assertEquals(iosScript.list()!!.sorted(), names)
        names.forEach { assertArrayEquals(it, File(iosScript, it).readBytes(), File(android, it).readBytes()) }
    }

    @Test fun androidStartsSheetsFromTheSameDesignAndFoods() {
        assertArrayEquals(File(ios, "Resources/Blueprint.xlsx").readBytes(), File("src/main/assets/Blueprint.xlsx").readBytes())
        assertArrayEquals(File(ios, "Sheet/Foods.json").readBytes(), File("src/main/assets/foods.json").readBytes())
    }
}