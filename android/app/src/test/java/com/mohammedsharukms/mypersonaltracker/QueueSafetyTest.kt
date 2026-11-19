package com.mohammedsharukms.mypersonaltracker

import com.mohammedsharukms.mypersonaltracker.sheet.PendingWrite
import com.mohammedsharukms.mypersonaltracker.sheet.QueuePolicy
import com.mohammedsharukms.mypersonaltracker.sheet.SheetExpense
import com.mohammedsharukms.mypersonaltracker.sheet.SignedOutException
import com.mohammedsharukms.mypersonaltracker.sheet.TransientException
import com.mohammedsharukms.mypersonaltracker.sheet.UpiScan
import com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetFormat
import com.mohammedsharukms.mypersonaltracker.sheet.google.GoogleException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** What keeps a queued change from being lost or sent to the wrong row. Mirrors QueueSafetyTests.swift. */
class QueueSafetyTest {
    private fun write(action: String, payload: JSONObject, tempRow: Int? = null) =
        PendingWrite(UUID.randomUUID().toString(), action, payload.toString(), "", 0, null, tempRow)

    private fun expense(row: Int, description: String, qar: Double) =
        SheetExpense(row, "2026-10-27", description, null, null, qar, null, null)

    /** "Infinity" and "NaN" parse as numbers, and JSONObject.put throws on them: the save would crash. */
    @Test fun infinityOrNaNTypedIntoAFieldIsNotANumber() {
        listOf("Infinity", "-Infinity", "NaN", "infinity").forEach { assertNull(it, SheetFormat.input(it)) }
        assertEquals(12.5, SheetFormat.input("12,5")!!, 0.0)
    }

    @Test fun aQrCodeCantAskForAnEndlessAmount() {
        listOf("Infinity", "NaN", "-5").forEach { assertNull(it, UpiScan.parse("upi://pay?pa=shop@okicici&am=$it")?.amount) }
        assertEquals(120.5, UpiScan.parse("upi://pay?pa=shop@okicici&am=120.5")?.amount!!, 0.0)
    }

    @Test fun anAddGetsItsOwnNewRowNotABillPostedAlongside() {
        val add = write("addExpense", JSONObject().put("date", "2026-10-27").put("description", "Lunch").put("qar", 12.5), tempRow = -1)
        val before = listOf(expense(5, "Rent", 900.0))
        // A recurring bill fell due in the same reply.
        val after = before + expense(6, "Netflix", 45.0) + expense(7, "Lunch", 12.5)
        assertEquals(7, QueuePolicy.rowAssigned(add, before, after))
        assertNull(QueuePolicy.rowAssigned(add, after, after))
    }

    @Test fun editsAndDeletesOfAnAddOnItsWayMoveToItsRow() {
        val edit = write("updateExpense", JSONObject().put("row", -1).put("qar", 13))
        val delete = write("deleteExpense", JSONObject().put("row", -1))
        val other = write("deleteExpense", JSONObject().put("row", 8))
        val moved = QueuePolicy.pointing(listOf(edit, delete, other), from = -1, to = 7)
        assertEquals(listOf(7, 7, 8), moved.map { JSONObject(it.payloadJson).getInt("row") })
        // No row found: they can't be sent anywhere safe.
        assertEquals(listOf(other.id), QueuePolicy.pointing(listOf(edit, delete, other), from = -1, to = null).map { it.id })
    }

    @Test fun aLapsedSignInOrNoConnectionIsNotARefusal() {
        // A refusal drops the change; these must keep it queued.
        assertTrue(QueuePolicy.fromGoogle(GoogleException.SignedOut()) is SignedOutException)
        // Play services couldn't reach Google for a token (offline for over 50 minutes).
        assertTrue(QueuePolicy.fromGoogle(GoogleException.Failed("Google sign-in isn't available on this phone.")) is TransientException)
        assertTrue(QueuePolicy.fromGoogle(GoogleException.Http(401, "Invalid Credentials")) is TransientException)
        assertTrue(QueuePolicy.fromGoogle(GoogleException.Http(503, "")) is TransientException)
        val refused = QueuePolicy.fromGoogle(GoogleException.Http(400, "Bad range"))
        assertFalse(refused is TransientException || refused is SignedOutException)
    }
}