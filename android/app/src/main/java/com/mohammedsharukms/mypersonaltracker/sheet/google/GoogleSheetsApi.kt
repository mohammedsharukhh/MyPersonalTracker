package com.mohammedsharukms.mypersonaltracker.sheet.google

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * The few Google Sheets and Drive REST calls the app needs, signed with
 * [GoogleAuth]'s token. Mirrors GoogleSheetsAPI.swift.
 */
class GoogleSheetsApi(private val auth: GoogleAuth) {

    enum class Render(val value: String) { RAW("UNFORMATTED_VALUE"), SHOWN("FORMATTED_VALUE"), FORMULAS("FORMULA") }

    data class Tab(
        val id: Int, val title: String, val index: Int, val hidden: Boolean, val rows: Int, val columns: Int,
        val frozenRows: Int, val frozenColumns: Int, val charts: JSONArray, val conditionalRules: Int,
    )
    data class Workbook(val name: String, val timeZone: String, val tabs: List<Tab>)
    data class Created(val id: String, val url: String?)

    /** The spreadsheet's name, time zone and tabs. */
    suspend fun workbook(id: String): Workbook {
        val result = send("GET", "$SHEETS/$id?fields=" + enc("properties(title,timeZone),sheets(properties(sheetId,title,index,hidden," +
            "gridProperties(rowCount,columnCount,frozenRowCount,frozenColumnCount)),charts(chartId,spec),conditionalFormats(ranges(sheetId)))"))
        val properties = result.optJSONObject("properties") ?: JSONObject()
        val sheets = result.optJSONArray("sheets") ?: JSONArray()
        val tabs = (0 until sheets.length()).map { i ->
            val sheet = sheets.getJSONObject(i)
            val p = sheet.getJSONObject("properties")
            val grid = p.optJSONObject("gridProperties") ?: JSONObject()
            Tab(p.getInt("sheetId"), p.getString("title"), p.optInt("index"), p.optBoolean("hidden"),
                grid.optInt("rowCount", 1000), grid.optInt("columnCount", 26), grid.optInt("frozenRowCount"), grid.optInt("frozenColumnCount"),
                sheet.optJSONArray("charts") ?: JSONArray(), sheet.optJSONArray("conditionalFormats")?.length() ?: 0)
        }
        return Workbook(properties.optString("title"), properties.optString("timeZone", "UTC"), tabs)
    }

    /** Several ranges at once, trimmed of trailing empties. `datesAsText`: dates come back as shown, not as serials. */
    suspend fun read(id: String, ranges: List<String>, render: Render, datesAsText: Boolean = false): List<JSONArray> {
        val query = ranges.joinToString("&") { "ranges=" + enc(it) } +
            "&valueRenderOption=${render.value}&dateTimeRenderOption=${if (datesAsText) "FORMATTED_STRING" else "SERIAL_NUMBER"}"
        val result = send("GET", "$SHEETS/$id/values:batchGet?$query")
        val out = result.optJSONArray("valueRanges") ?: JSONArray()
        return (0 until out.length()).map { out.getJSONObject(it).optJSONArray("values") ?: JSONArray() }
    }

    /** Writes several ranges; `typed`: as if typed (formulas, dates). */
    suspend fun write(id: String, data: List<Pair<String, JSONArray>>, typed: Boolean = false) {
        if (data.isEmpty()) return
        val body = JSONObject().put("valueInputOption", if (typed) "USER_ENTERED" else "RAW")
            .put("data", JSONArray(data.map { JSONObject().put("range", it.first).put("majorDimension", "ROWS").put("values", it.second) }))
        send("POST", "$SHEETS/$id/values:batchUpdate", body)
    }

    suspend fun clear(id: String, ranges: List<String>) {
        if (ranges.isNotEmpty()) send("POST", "$SHEETS/$id/values:batchClear", JSONObject().put("ranges", JSONArray(ranges)))
    }

    suspend fun batchUpdate(id: String, requests: List<JSONObject>) {
        if (requests.isNotEmpty()) send("POST", "$SHEETS/$id:batchUpdate", JSONObject().put("requests", JSONArray(requests)))
    }

    /** Each tab's name and sheetId, in order. */
    suspend fun tabIds(id: String): List<Pair<String, Int>> {
        val sheets = send("GET", "$SHEETS/$id?fields=sheets.properties(title,sheetId)").optJSONArray("sheets") ?: JSONArray()
        return (0 until sheets.length()).map { sheets.getJSONObject(it).getJSONObject("properties").let { p -> p.getString("title") to p.getInt("sheetId") } }
    }

    suspend fun chartIds(id: String, sheetId: Int): List<Int> {
        val sheets = send("GET", "$SHEETS/$id?fields=" + enc("sheets(properties.sheetId,charts.chartId)")).optJSONArray("sheets") ?: JSONArray()
        for (i in 0 until sheets.length()) {
            val sheet = sheets.getJSONObject(i)
            if (sheet.getJSONObject("properties").getInt("sheetId") != sheetId) continue
            val charts = sheet.optJSONArray("charts") ?: return emptyList()
            return (0 until charts.length()).map { charts.getJSONObject(it).getInt("chartId") }
        }
        return emptyList()
    }

    /** The cells' formulas as typed, or their values where there's no formula. */
    suspend fun readFormulas(id: String, range: String): JSONArray =
        send("GET", "$SHEETS/$id/values/${enc(range)}?valueRenderOption=FORMULA").optJSONArray("values") ?: JSONArray()

    /** Uploads an .xlsx and has Drive turn it into a Google Sheet in the user's Drive. */
    suspend fun importSpreadsheet(name: String, xlsx: ByteArray): Created {
        val meta = JSONObject().put("name", name).put("mimeType", "application/vnd.google-apps.spreadsheet")
        val result = upload("$UPLOAD?uploadType=multipart&fields=id,webViewLink", meta,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx)
        return Created(result.getString("id"), result.optString("webViewLink").ifEmpty { null })
    }

    /** Spreadsheets with this name that the app made, newest first. */
    suspend fun spreadsheets(named: String): List<String> {
        val q = "name = '${named.replace("'", "\\'")}' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
        val files = send("GET", "$DRIVE?orderBy=${enc("modifiedTime desc")}&fields=files(id)&q=${enc(q)}").optJSONArray("files") ?: JSONArray()
        return (0 until files.length()).map { files.getJSONObject(it).getString("id") }
    }

    /** A folder this app made, by name, or a new one. */
    suspend fun folder(named: String): String {
        val q = "name = '${named.replace("'", "\\'")}' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        send("GET", "$DRIVE?fields=files(id)&q=${enc(q)}").optJSONArray("files")?.optJSONObject(0)?.optString("id")?.ifEmpty { null }?.let { return it }
        return send("POST", "$DRIVE?fields=id", JSONObject().put("name", named).put("mimeType", "application/vnd.google-apps.folder")).getString("id")
    }

    suspend fun uploadJpeg(data: ByteArray, name: String, folder: String): String =
        upload("$UPLOAD?uploadType=multipart&fields=id", JSONObject().put("name", name).put("parents", JSONArray(listOf(folder)))
            .put("mimeType", "image/jpeg"), "image/jpeg", data).getString("id")

    /** A file's bytes (`thumbnail`: Drive's small preview when there is one). */
    suspend fun download(fileId: String, thumbnail: Boolean): ByteArray {
        if (thumbnail) {
            val link = runCatching { send("GET", "$DRIVE/$fileId?fields=thumbnailLink").optString("thumbnailLink") }.getOrNull()
            if (!link.isNullOrEmpty()) runCatching { return bytes(link) }
        }
        return bytes("$DRIVE/$fileId?alt=media")
    }

    /** Drive's files.copy, as this app's own file. */
    suspend fun copy(fileId: String, name: String): String =
        send("POST", "$DRIVE/$fileId/copy?fields=id", JSONObject().put("name", name)).getString("id")

    /** The spreadsheet as an Excel file. */
    suspend fun exportXlsx(fileId: String): ByteArray = bytes("$DRIVE/$fileId/export?mimeType=${enc(XLSX)}")

    suspend fun upload(data: ByteArray, name: String, mimeType: String, folder: String): String =
        upload("$UPLOAD?uploadType=multipart&fields=id", JSONObject().put("name", name).put("parents", JSONArray(listOf(folder)))
            .put("mimeType", mimeType), mimeType, data).getString("id")

    suspend fun rename(fileId: String, name: String) {
        send("PATCH", "$DRIVE/$fileId", JSONObject().put("name", name))
    }

    /** Moves a file to the Drive trash (quietly nothing for files this app can't see). */
    suspend fun trash(fileId: String) {
        runCatching { send("PATCH", "$DRIVE/$fileId", JSONObject().put("trashed", true)) }
    }

    // ---- Transport ----------------------------------------------------------------

    private suspend fun send(method: String, url: String, body: JSONObject? = null): JSONObject {
        var attempt = 0
        while (true) {
            try {
                return sendOnce(method, url, body)
            } catch (e: GoogleException.Http) {
                val wait = GoogleRetry.wait(e.status, attempt, e.retryAfter) ?: throw e
                kotlin.coroutines.coroutineContext[GoogleRetry.Listener]?.onWait?.invoke(wait)
                delay((wait * 1000).toLong())
                attempt++
            }
        }
    }

    private suspend fun sendOnce(method: String, url: String, body: JSONObject?): JSONObject {
        val token = auth.validAccessToken()
        return withContext(Dispatchers.IO) {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                if (method == "PATCH") { requestMethod = "POST"; setRequestProperty("X-HTTP-Method-Override", "PATCH") } else requestMethod = method
                connectTimeout = 30_000
                readTimeout = 120_000
                setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.use { it.write(body.toString().toByteArray()) }
                }
            }
            finish(c)
        }
    }

    private suspend fun upload(url: String, meta: JSONObject, type: String, data: ByteArray): JSONObject {
        val token = auth.validAccessToken()
        return withContext(Dispatchers.IO) {
            val boundary = UUID.randomUUID().toString()
            val body = ByteArrayOutputStream().apply {
                write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: $type\r\n\r\n".toByteArray())
                write(data)
                write("\r\n--$boundary--".toByteArray())
            }.toByteArray()
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                outputStream.use { it.write(body) }
            }
            finish(c)
        }
    }

    private suspend fun bytes(url: String): ByteArray {
        val token = auth.validAccessToken()
        return withContext(Dispatchers.IO) {
            val c = (URL(url).openConnection() as HttpURLConnection).apply { setRequestProperty("Authorization", "Bearer $token") }
            try {
                if (c.responseCode != 200) throw GoogleException.Http(c.responseCode, "Couldn't download the photo.")
                c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
        }
    }

    private fun finish(c: HttpURLConnection): JSONObject {
        try {
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            val json = runCatching { JSONObject(text.ifEmpty { "{}" }) }.getOrDefault(JSONObject())
            if (code !in 200..299) throw GoogleException.Http(code, message(json, text), c.getHeaderField("Retry-After"))
            return json
        } finally {
            c.disconnect()
        }
    }

    companion object {
        private const val SHEETS = "https://sheets.googleapis.com/v4/spreadsheets"
        private const val DRIVE = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        /** `{"error": {"message": …}}` (APIs) or `{"error": "…", "error_description": …}` (OAuth). */
        fun message(json: JSONObject, text: String): String {
            json.optJSONObject("error")?.optString("message")?.ifEmpty { null }?.let { return it }
            json.optString("error").ifEmpty { null }?.let { e -> return listOfNotNull(e, json.optString("error_description").ifEmpty { null }).joinToString(": ") }
            return text.take(200).ifEmpty { "no details" }
        }

        /** "'Mom & Dad'!A1" when the tab name needs quoting. */
        fun a1(tab: String, cells: String): String =
            if (Regex("^[A-Za-z_][A-Za-z0-9_]*$").matches(tab)) "$tab!$cells" else "'${tab.replace("'", "''")}'!$cells"
    }
}
