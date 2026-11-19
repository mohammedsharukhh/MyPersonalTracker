package com.mohammedsharukms.mypersonaltracker

import com.mohammedsharukms.mypersonaltracker.sheet.CardBrand
import com.mohammedsharukms.mypersonaltracker.sheet.CardExpiry
import com.mohammedsharukms.mypersonaltracker.sheet.CardFormat
import com.mohammedsharukms.mypersonaltracker.sheet.CardKind
import com.mohammedsharukms.mypersonaltracker.sheet.CardStyle
import com.mohammedsharukms.mypersonaltracker.sheet.SavedCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Mirrors CardTests.swift. */
class CardTest {
    @Test fun detectsTheNetwork() {
        assertEquals(CardBrand.VISA, CardBrand.of("4111111111111111"))
        assertEquals(CardBrand.MASTERCARD, CardBrand.of("5555555555554444"))
        assertEquals(CardBrand.MASTERCARD, CardBrand.of("2223003122003222"))
        assertEquals(CardBrand.AMEX, CardBrand.of("378282246310005"))
        assertEquals(CardBrand.RUPAY, CardBrand.of("6070000000000000"))
        assertEquals(CardBrand.RUPAY, CardBrand.of("6521000000000000"))
        assertEquals(CardBrand.DISCOVER, CardBrand.of("6011111111111117"))
        assertEquals(CardBrand.DISCOVER, CardBrand.of("6500000000000000"))
        assertEquals(CardBrand.DINERS, CardBrand.of("30569309025904"))
        assertEquals(CardBrand.JCB, CardBrand.of("3530111333300000"))
        assertEquals(CardBrand.UNKNOWN, CardBrand.of(""))
    }

    @Test fun groupsTheNumberLikeTheCard() {
        assertEquals("4111 1111 1111 1111", CardFormat.grouped("4111111111111111"))
        assertEquals("3782 822463 10005", CardFormat.grouped("378282246310005"))
        assertEquals("3056 930902 5904", CardFormat.grouped("30569309025904"))
        assertEquals("4111 1", CardFormat.grouped("41111"))
        assertEquals("4111 1111 1111", CardFormat.grouped("4111 1111-1111"))
        assertEquals(listOf(4), CardFormat.groupEnds("41111"))
        assertEquals(emptyList<Int>(), CardFormat.groupEnds("4111"))
    }

    @Test fun luhnCatchesTypos() {
        assertTrue(CardFormat.passesLuhn("4111111111111111"))
        assertTrue(CardFormat.passesLuhn("378282246310005"))
        assertFalse(CardFormat.passesLuhn("4111111111111112"))
        assertFalse(CardFormat.passesLuhn("4111"))
    }

    @Test fun readsTheExpiry() {
        assertEquals(8 to 2029, CardFormat.expiry("08/29"))
        assertNull(CardFormat.expiry("1329"))
        assertNull(CardFormat.expiry("082"))
        assertEquals("0", CardFormat.expiryField("0"))
        assertEquals("08", CardFormat.expiryField("08"))
        assertEquals("08/29", CardFormat.expiryField("0829"))
        assertEquals("08/2", CardFormat.expiryField("08/2"))
    }

    @Test fun warnsBeforeItExpires() {
        val today = LocalDate.of(2026, 10, 19)
        val card = SavedCard(number = "4111111111111111", expiryMonth = 11, expiryYear = 2026)
        assertEquals(CardExpiry.SOON, card.expiryState(today))
        assertEquals(CardExpiry.SOON, card.copy(expiryMonth = 10).expiryState(today))   // valid to the end of October
        assertEquals(CardExpiry.EXPIRED, card.copy(expiryMonth = 9).expiryState(today))
        assertEquals(CardExpiry.FINE, card.copy(expiryMonth = 9, expiryYear = 2029).expiryState(today))
        assertEquals(CardExpiry.FINE, card.copy(expiryMonth = null).expiryState(today))
    }

    @Test fun showsOnlyTheLastFour() {
        val card = SavedCard(bank = "HDFC", number = "4111111111111111", expiryMonth = 8, expiryYear = 2029)
        assertEquals("•••• 1111", card.maskedNumber)
        assertEquals("HDFC Visa", card.title)
        assertEquals("08/29", card.expiryText)
        assertFalse(card.spokenSummary().contains("4111"))
        assertEquals("Millennia", SavedCard(nickname = " Millennia ", bank = "HDFC").title)
        assertEquals("debit card", SavedCard(kind = CardKind.DEBIT).title)
    }

    @Test fun roundTripsThroughJson() {
        val cards = listOf(
            SavedCard(nickname = "Travel", bank = "ICICI", kind = CardKind.FOREX, number = "5555555555554444", holder = "A KUMAR",
                expiryMonth = 1, expiryYear = 2030, cvv = "123", notes = "Limit 2L", style = CardStyle.GOLD),
            SavedCard(number = "4111"),
        )
        assertEquals(cards, CardFormat.fromJson(CardFormat.toJson(cards)))
    }
}
