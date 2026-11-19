package com.mohammedsharukms.mypersonaltracker

import com.mohammedsharukms.mypersonaltracker.sheet.google.GoogleSheetsApi
import com.mohammedsharukms.mypersonaltracker.sheet.google.ScriptBackend
import com.mohammedsharukms.mypersonaltracker.sheet.google.TrackerWorkbook
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The Google sign-in sheet's plumbing on Android (the script itself is tested on iOS, in the same JavaScript). */
class GoogleSheetTest {
    @Test fun numbersShownAsTextAreDates() {
        val marked = ScriptBackend.markDates(JSONArray("[[46314, 12, \"x\"]]"), JSONArray("[[\"19-Oct-26\", 12, \"x\"]]"))
        assertEquals(46314.0, marked.getJSONArray(0).getJSONObject(0).getDouble("\$s"), 0.0)
        assertEquals(12, marked.getJSONArray(0).getInt(1))
        assertEquals("x", marked.getJSONArray(0).getString(2))
    }

    @Test fun datesAreWrittenAsTyped() {
        val typed = ScriptBackend.typed(JSONArray(listOf(JSONArray(listOf(JSONObject().put("\$d", "2026-10-19"), "Coffee", 12)))))
        assertEquals("2026-10-19", typed.getJSONArray(0).getString(0))
        assertEquals("Coffee", typed.getJSONArray(0).getString(1))
        assertEquals(12, typed.getJSONArray(0).getInt(2))
    }

    @Test fun cellAddresses() {
        assertEquals("'Salary & Savings'!B5", ScriptBackend.a1("Salary & Savings", 5, 2))
        assertEquals("'Dad''s'!A4:C5", ScriptBackend.a1("Dad's", 4, 1, rows = 2, cols = 3))
        assertEquals("AA", ScriptBackend.column(27))
        assertEquals("Ledger!A1:C4", GoogleSheetsApi.a1("Ledger", "A1:C4"))
        assertEquals("'Mom & Dad'!A1", GoogleSheetsApi.a1("Mom & Dad", "A1"))
    }

    @Test fun googleErrorBodies() {
        assertEquals("File not found.", GoogleSheetsApi.message(JSONObject("{\"error\":{\"message\":\"File not found.\"}}"), ""))
        assertEquals("invalid_grant: Revoked", GoogleSheetsApi.message(JSONObject("{\"error\":\"invalid_grant\",\"error_description\":\"Revoked\"}"), ""))
    }

    @Test fun workbookPeriodAndBookkeeping() {
        assertEquals("2026-10-19" to "2026-11-17", TrackerWorkbook.period(LocalDate.of(2026, 10, 19)))
        val data = TrackerWorkbook.appData("2026-10-19")
        assertEquals("Key", data.getJSONArray(0).getString(0))
        val rows = (1 until data.length()).map { data.getJSONArray(it) }
        assertTrue(rows.any { it.getString(0) == "doc:ROWS_START" && it.getString(1) == "'2026-10-19" })
        assertTrue(rows.all { it.getString(1).startsWith("'") })
    }

    @Test fun habitScoreChartReadsDateAndScore() {
        val chart = TrackerWorkbook.habitScoreChart(7, 3).getJSONObject("addChart").getJSONObject("chart")
        val basic = chart.getJSONObject("spec").getJSONObject("basicChart")
        val series = basic.getJSONArray("series").getJSONObject(0).getJSONObject("series").getJSONObject("sourceRange").getJSONArray("sources").getJSONObject(0)
        assertEquals("LINE", basic.getString("chartType"))
        assertEquals(14, series.getInt("startColumnIndex"))
        assertEquals(4, series.getInt("startRowIndex"))
        assertEquals(34, series.getInt("endRowIndex"))
        assertEquals(7, chart.getJSONObject("position").getJSONObject("overlayPosition").getJSONObject("anchorCell").getInt("sheetId"))
    }
}
