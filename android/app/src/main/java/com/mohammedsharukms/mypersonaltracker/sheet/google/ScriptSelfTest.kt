package com.mohammedsharukms.mypersonaltracker.sheet.google

import android.content.Context
import com.mohammedsharukms.mypersonaltracker.DebugLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Debug builds only (`adb shell am start … --ez scriptSelfTest true`): runs every API action
 * of the sheet's script in this phone's WebView on the blueprint workbook (debug assets),
 * then a whole setupNewWorkbook from an empty sheet, and logs the outcome under "selftest".
 * The same checks as ScriptBackendTests on iOS.
 */
object ScriptSelfTest {
    suspend fun run(context: Context) {
        val tag = "selftest"
        try {
            val runtime = ScriptRuntime(context)
            val workbook = context.assets.open("selftest/blueprint-workbook.json").bufferedReader().use { it.readText() }
            val withUploads = JSONObject(workbook).put("uploads", JSONObject().put("QUJD", "drive-1")).toString()
            val steps = JSONArray(context.assets.open("selftest/all-actions.json").bufferedReader().use { it.readText() })
            val started = System.currentTimeMillis()
            val results = runtime.sequence(withUploads, steps)
            var failed = 0
            results.forEachIndexed { i, r ->
                if (!r.reply.optBoolean("ok")) { failed++; DebugLog.e(tag, "FAIL ${steps.getJSONObject(i).getString("action")}: ${r.reply.optString("error")}") }
            }
            val changed = results.count { it.ops.length() > 0 }
            DebugLog.i(tag, "actions: ${results.size - failed}/${results.size} ok, $changed changed the sheet, in ${System.currentTimeMillis() - started} ms")
            val empty = JSONObject().put("id", "empty").put("name", "Test").put("tz", "Asia/Kolkata").put("sheets", JSONArray().put(
                JSONObject().put("id", 0).put("title", "Sheet1").put("index", 0).put("maxRows", 1000).put("maxCols", 26)
                    .put("values", JSONArray()).put("formulas", JSONArray()).put("display", JSONArray())))
            val setup = runtime.call(empty.toString(), "setupNewWorkbook")
            val requests = (0 until setup.ops.length()).mapNotNull { setup.ops.getJSONObject(it).optJSONObject("req")?.keys()?.next() }
            DebugLog.i(tag, "setup: ${requests.count { it == "addSheet" }} tabs, ${requests.count { it == "addChart" }} charts, ${requests.size} requests")
            DebugLog.i(tag, if (failed == 0 && requests.count { it == "addChart" } == 4) "PASSED" else "FAILED")
        } catch (e: Exception) {
            DebugLog.e(tag, "FAILED: ${e.message}", e)
        }
    }
}
