package com.mohammedsharukms.mypersonaltracker.sheet.google

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Makes the tracker's workbook in the signed-in person's own Drive, with no Apps
 * Script: assets/Blueprint.xlsx (exported from a freshly set-up sheet) is imported
 * as a Google Sheet, then given this person's dates, the app's bookkeeping tab, and
 * the one chart an .xlsx import leaves out. Mirrors TrackerWorkbook.swift.
 */
object TrackerWorkbook {
    const val TITLE = "Life & Finance Command Center"
    /** The blueprint's Daily Tracker and budget hold this many dated rows (5…34). */
    const val PERIOD_DAYS = 30
    private const val FIRST_ROW = 5
    const val APP_DATA_TAB = "App Data"
    /** Cells whose formulas exist only in Google Sheets; the import must restore them. */
    private val googleOnlyFormulaCells = listOf("Daily Tracker" to "N5", "Daily Tracker" to "N34", "Settings" to "B60")

    /** The tracker this app made in the user's Drive before (on any device), or a new one. */
    suspend fun findOrCreate(context: Context, api: GoogleSheetsApi, progress: (String) -> Unit = {}): String {
        api.spreadsheets(TITLE).firstOrNull()?.let { progress("Found your sheet in Google Drive"); return it }
        return create(context, api, LocalDate.now(), progress).id
    }

    suspend fun create(context: Context, api: GoogleSheetsApi, startDay: LocalDate = LocalDate.now(), progress: (String) -> Unit = {}): GoogleSheetsApi.Created {
        val blueprint = context.assets.open("Blueprint.xlsx").use { it.readBytes() }
        val created = api.importSpreadsheet(TITLE, blueprint)
        progress("Created “$TITLE” in your Drive")

        val (start, end) = period(startDay)
        api.write(created.id, listOf(GoogleSheetsApi.a1("Settings", "B6:B7") to JSONArray(listOf(JSONArray(listOf(start)), JSONArray(listOf(end))))), typed = true)
        progress("Period set: $start to $end")

        val tabs = api.tabIds(created.id).toMap()
        val requests = mutableListOf(JSONObject().put("addSheet", JSONObject().put("properties",
            JSONObject().put("title", APP_DATA_TAB).put("hidden", true).put("index", tabs.size))))
        val dashboard = tabs["Dashboard"]
        val daily = tabs["Daily Tracker"]
        if (dashboard != null && daily != null) requests += habitScoreChart(dashboard, daily)
        api.batchUpdate(created.id, requests)
        api.write(created.id, listOf(GoogleSheetsApi.a1(APP_DATA_TAB, "A1") to appData(start)), typed = true)
        progress("Added the Daily Habit Score chart and the app's bookkeeping")

        for ((tab, cell) in googleOnlyFormulaCells) {
            val formula = api.readFormulas(created.id, GoogleSheetsApi.a1(tab, cell)).optJSONArray(0)?.optString(0).orEmpty()
            if ("DUMMYFUNCTION" in formula || !formula.startsWith("=")) {
                throw IllegalStateException("Google didn't restore the formula in $tab!$cell after the import.")
            }
        }
        progress("Checked the Google-only formulas came through")
        return created
    }

    /** "2026-10-19" and the day PERIOD_DAYS - 1 later. */
    fun period(day: LocalDate): Pair<String, String> = day.toString() to day.plusDays(PERIOD_DAYS - 1L).toString()

    /** What setupNewWorkbook leaves in the document properties, kept as text ('…). */
    fun appData(start: String): JSONArray = JSONArray(
        listOf(JSONArray(listOf("Key", "Value — kept by the app, please leave as it is"))) + listOf(
            "SALARY_DEFAULTS_FIXED" to "1", "SPLIT_FORMULAS" to "2", "DAILY_FORMULAS" to "3", "HABIT_FORMULAS" to "2",
            "CURRENCY_FORMULAS_DYNAMIC" to "1", "CURRENCY_LABELS" to "{\"main\":\"QAR\",\"home\":\"INR\"}", "RATE_FOR" to "QAR/INR",
            "ROWS_START" to start,
        ).map { JSONArray(listOf("doc:${it.first}", "'${it.second}")) },
    )

    /** The Dashboard's line chart of the daily score (Daily Tracker column O by date in A), at row 34. */
    fun habitScoreChart(dashboard: Int, daily: Int): JSONObject {
        fun column(index: Int) = JSONObject().put("sourceRange", JSONObject().put("sources", JSONArray(listOf(JSONObject()
            .put("sheetId", daily).put("startRowIndex", FIRST_ROW - 1).put("endRowIndex", FIRST_ROW - 1 + PERIOD_DAYS)
            .put("startColumnIndex", index).put("endColumnIndex", index + 1)))))
        val spec = JSONObject().put("title", "Daily Habit Score").put("basicChart", JSONObject()
            .put("chartType", "LINE").put("legendPosition", "NO_LEGEND").put("headerCount", 0)
            .put("axis", JSONArray(listOf(JSONObject().put("position", "BOTTOM_AXIS"),
                JSONObject().put("position", "LEFT_AXIS").put("viewWindowOptions", JSONObject().put("viewWindowMin", 0).put("viewWindowMax", 1)))))
            .put("domains", JSONArray(listOf(JSONObject().put("domain", column(0)))))
            .put("series", JSONArray(listOf(JSONObject().put("series", column(14)).put("targetAxis", "LEFT_AXIS")
                .put("colorStyle", JSONObject().put("rgbColor", JSONObject().put("red", 0.184).put("green", 0.333).put("blue", 0.592)))))))
        val position = JSONObject().put("overlayPosition", JSONObject()
            .put("anchorCell", JSONObject().put("sheetId", dashboard).put("rowIndex", 33).put("columnIndex", 0))
            .put("widthPixels", 620).put("heightPixels", 260))
        return JSONObject().put("addChart", JSONObject().put("chart", JSONObject().put("spec", spec).put("position", position)))
    }
}
