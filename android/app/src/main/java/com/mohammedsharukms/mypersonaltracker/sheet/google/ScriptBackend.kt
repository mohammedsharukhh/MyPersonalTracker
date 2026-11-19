package com.mohammedsharukms.mypersonaltracker.sheet.google

import com.mohammedsharukms.mypersonaltracker.sheet.SheetLoadProgress
import android.util.Base64
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * The sheet's own Apps Script, run in the app for a workbook in the user's Drive:
 * the workbook is loaded through the Sheets API, the script answers the request
 * as the web app would, and its changes go back to the sheet in the same order.
 * Mirrors ScriptBackend.swift.
 */
class ScriptBackend(private val spreadsheetId: String, private val api: GoogleSheetsApi, private val runtime: ScriptRuntime) {

    class ResetNeeded : Exception("Reset is handled by the app.")

    /** The web app's reply to one request: { ok, data | ack | error }. */
    suspend fun request(action: String, payload: JSONObject = JSONObject(), reply: String? = null): JSONObject = lane.withLock {
        val workbook = loadWorkbook().put("uploads", uploadPhotos(payload))
        val result = runtime.request(workbook.toString(), action, payload, reply)
        apply(result.ops)
        if (result.reply.optString("error") == "RESET_NATIVE") throw ResetNeeded()
        result.reply
    }

    /** The snapshot with every formula worked out. Looking may post bills that fell due; then it looks again. */
    suspend fun snapshot(progress: ((SheetLoadProgress) -> Unit)? = null): JSONObject = lane.withLock {
        progress?.invoke(SheetLoadProgress.reading)
        val workbook = loadWorkbook().toString()
        progress?.invoke(SheetLoadProgress.checking)
        var result = runtime.request(workbook, "snapshot", JSONObject(), null)
        apply(result.ops, progress)
        if (result.ops.length() > 0) {
            progress?.invoke(SheetLoadProgress.finishing)
            result = runtime.request(loadWorkbook().toString(), "snapshot", JSONObject(), null)
            apply(result.ops)
        }
        if (!result.reply.optBoolean("ok")) throw IllegalStateException(result.reply.optString("error").ifEmpty { "The sheet didn't answer." })
        result.reply.getJSONObject("data")
    }

    suspend fun runFunction(name: String) = lane.withLock {
        apply(runtime.call(loadWorkbook().toString(), name).ops)
    }

    /** Every tab: values (dates marked), formulas, and the text the sheet shows. */
    suspend fun loadWorkbook(): JSONObject = coroutineScope {
        val info = api.workbook(spreadsheetId)
        val ranges = info.tabs.map { "'${it.title.replace("'", "''")}'" }
        val raw = async { api.read(spreadsheetId, ranges, GoogleSheetsApi.Render.RAW) }
        val rawText = async { api.read(spreadsheetId, ranges, GoogleSheetsApi.Render.RAW, datesAsText = true) }
        val shown = async { api.read(spreadsheetId, ranges, GoogleSheetsApi.Render.SHOWN) }
        val formulas = async { api.read(spreadsheetId, ranges, GoogleSheetsApi.Render.FORMULAS) }
        val (values, texts, display, typed) = listOf(raw.await(), rawText.await(), shown.await(), formulas.await())
        val sheets = JSONArray()
        info.tabs.forEachIndexed { i, tab ->
            sheets.put(JSONObject()
                .put("id", tab.id).put("title", tab.title).put("index", tab.index).put("hidden", tab.hidden)
                .put("maxRows", tab.rows).put("maxCols", tab.columns).put("frozenRows", tab.frozenRows).put("frozenCols", tab.frozenColumns)
                .put("charts", tab.charts).put("cfCount", tab.conditionalRules)
                .put("values", markDates(values.getOrElse(i) { JSONArray() }, texts.getOrElse(i) { JSONArray() }))
                .put("formulas", JSONArray(rows(typed.getOrElse(i) { JSONArray() }).map { row -> JSONArray(row.map { (it as? String)?.takeIf { s -> s.startsWith("=") } ?: "" }) }))
                .put("display", JSONArray(rows(display.getOrElse(i) { JSONArray() }).map { row -> JSONArray(row.map { it?.toString() ?: "" }) })))
        }
        JSONObject().put("id", spreadsheetId).put("name", info.name).put("tz", info.timeZone).put("sheets", sheets)
    }

    /** Uploads a meal's `photo` or Mom & Dad's `newPhotos`; returns base64 → Drive id for the script. */
    private suspend fun uploadPhotos(payload: JSONObject): JSONObject {
        val uploads = JSONObject()
        payload.optString("photo").takeIf { it.isNotEmpty() }?.let { photo ->
            val folder = api.folder("Life & Finance · Meal photos")
            uploads.put(photo, api.uploadJpeg(Base64.decode(photo, Base64.DEFAULT), "${payload.optString("date")} ${payload.optString("meal", "meal")}.jpg", folder))
        }
        payload.optJSONObject("newPhotos")?.takeIf { it.length() > 0 }?.let { added ->
            val folder = api.folder("Life & Finance · Mom & Dad photos")
            added.keys().forEach { key ->
                val base64 = added.optString(key)
                if (base64.isNotEmpty()) uploads.put(base64, api.uploadJpeg(Base64.decode(base64, Base64.DEFAULT),
                    "${payload.optString("date")} ${payload.optString("task", "photo")}.jpg", folder))
            }
        }
        return uploads
    }

    /** Sends the script's changes in order: runs of value writes, clears and Sheets requests each as one call. */
    /** [progress] hears how far along it is, by the part of the sheet being written. */
    suspend fun apply(ops: JSONArray, progress: ((SheetLoadProgress) -> Unit)? = null) {
        if (ops.length() == 0) return
        val used = api.workbook(spreadsheetId).tabs.map { it.id }.toMutableSet()
        val realId = mutableMapOf<Int, Int>()
        fun resolve(id: Int): Int {
            if (id >= 0) return id
            return realId.getOrPut(id) {
                var real = Random.nextInt(100_000, 2_000_000_000)
                while (real in used) real = Random.nextInt(100_000, 2_000_000_000)
                used += real
                real
            }
        }
        fun resolved(value: Any?): Any? = when (value) {
            is JSONObject -> JSONObject().also { out ->
                value.keys().forEach { key ->
                    val inner = value.get(key)
                    out.put(key, if (key in ID_KEYS && inner is Number) resolve(inner.toInt()) else resolved(inner))
                }
            }
            is JSONArray -> JSONArray().also { out -> for (i in 0 until value.length()) out.put(resolved(value.get(i))) }
            else -> value
        }

        val batch = mutableListOf<JSONObject>()
        val sets = mutableListOf<Pair<String, JSONArray>>()
        val clears = mutableListOf<String>()
        val trash = mutableListOf<String>()
        suspend fun flush() {
            if (sets.isNotEmpty()) { api.write(spreadsheetId, sets.toList(), typed = true); sets.clear() }
            if (clears.isNotEmpty()) { api.clear(spreadsheetId, clears.toList()); clears.clear() }
            batch.chunked(400).forEach { api.batchUpdate(spreadsheetId, it) }
            batch.clear()
        }

        var tab: String? = null
        for (i in 0 until ops.length()) {
            val op = ops.getJSONObject(i)
            val type = op.optString("t")
            // Keep the order: a different kind of change sends what's waiting first.
            val switching = (type == "set" && (clears.isNotEmpty() || batch.isNotEmpty())) ||
                (type == "clear" && (sets.isNotEmpty() || batch.isNotEmpty())) ||
                (type == "req" && (sets.isNotEmpty() || clears.isNotEmpty()))
            if (switching) {
                progress?.invoke(SheetLoadProgress.writing(tab, i, ops.length()))
                flush()
            }
            op.optString("sheet").takeIf { it.isNotEmpty() }?.let { tab = it }
            when (type) {
                "set" -> sets += a1(op.optString("sheet"), op.optInt("row", 1), op.optInt("col", 1)) to typed(op.optJSONArray("values") ?: JSONArray())
                "clear" -> clears += a1(op.optString("sheet"), op.optInt("row", 1), op.optInt("col", 1), op.optInt("rows", 1), op.optInt("cols", 1))
                "req" -> (resolved(op.optJSONObject("req")) as? JSONObject)?.let { batch += it }
                "dropChartsOf" -> {
                    // A tab copied in this run: its charts' ids are only known once the copy exists.
                    flush()
                    val ids = api.chartIds(spreadsheetId, resolve(op.optInt("s")))
                    api.batchUpdate(spreadsheetId, ids.map { JSONObject().put("deleteEmbeddedObject", JSONObject().put("objectId", it)) })
                }
                "trash" -> op.optString("id").takeIf { it.isNotEmpty() }?.let { trash += it }
            }
        }
        progress?.invoke(SheetLoadProgress.writing(tab, ops.length() - 1, ops.length()))
        flush()
        trash.forEach { api.trash(it) }
    }

    companion object {
        /** One script run at a time on this phone. A Google sign-in sheet has no server lock (gas-shim's LockService
         *  does nothing): two runs reading the workbook at once would both write the same free row.
         *  shortcut: guards one phone only; two phones writing the same sheet at once can still collide, use the web app for that. */
        private val lane = Mutex()
        private val ID_KEYS = setOf("sheetId", "newSheetId", "sourceSheetId")

        private fun rows(grid: JSONArray): List<List<Any?>> =
            (0 until grid.length()).map { r -> grid.optJSONArray(r)?.let { row -> (0 until row.length()).map { row.opt(it) } } ?: emptyList() }

        /** A number that the date-as-text read shows as text is a date or time: sent as {"$s": serial}. */
        fun markDates(values: JSONArray, texts: JSONArray): JSONArray = JSONArray(rows(values).mapIndexed { r, row ->
            val textRow = texts.optJSONArray(r)
            JSONArray(row.mapIndexed { c, v ->
                if (v is Number && v !is Boolean && textRow?.opt(c) is String) JSONObject().put("\$s", v.toDouble()) else v
            })
        })

        /** Cells as typed: dates as their wall-clock text, which the sheet reads like typing. */
        fun typed(values: JSONArray): JSONArray = JSONArray(rows(values).map { row ->
            JSONArray(row.map { (it as? JSONObject)?.optString("\$d")?.takeIf { d -> d.isNotEmpty() } ?: it })
        })

        fun a1(sheet: String, row: Int, col: Int, rows: Int = 1, cols: Int = 1): String {
            val from = "${column(col)}$row"
            val cells = if (rows == 1 && cols == 1) from else "$from:${column(col + cols - 1)}${row + rows - 1}"
            return "'${sheet.replace("'", "''")}'!$cells"
        }

        fun column(n: Int): String {
            var x = n
            val s = StringBuilder()
            while (x > 0) { val r = (x - 1) % 26; s.insert(0, ('A' + r)); x = (x - 1) / 26 }
            return s.toString()
        }
    }
}
