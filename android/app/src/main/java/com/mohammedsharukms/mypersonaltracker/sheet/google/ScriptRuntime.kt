package com.mohammedsharukms.mypersonaltracker.sheet.google

import android.annotation.SuppressLint
import android.content.Context
import com.mohammedsharukms.mypersonaltracker.DebugLog
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * The sheet's own Apps Script (assets/backend: gas-shim.js and the .js copies of
 * the .gs files) running in a hidden WebView — Android's JavaScript engine — for
 * a Google sign-in sheet. One request at a time. Mirrors ScriptRuntime in ScriptBackend.swift.
 */
class ScriptRuntime(private val context: Context) {
    data class Result(val reply: JSONObject, val ops: JSONArray)

    private var webView: WebView? = null
    private val mutex = Mutex()
    /** The workbook for the next request, handed to the page through the bridge (it can be megabytes). */
    @Volatile private var workbook: String = "{}"
    private val errors = mutableListOf<String>()

    private inner class Bridge {
        @JavascriptInterface fun workbook(): String = this@ScriptRuntime.workbook
        @JavascriptInterface fun log(message: String) { DebugLog.d("sheet", message) }
    }

    /** One API request, answered as the web app would answer it, with the changes it made. */
    suspend fun request(workbookJson: String, action: String, payload: JSONObject, reply: String?): Result =
        evaluate(workbookJson, "__request(${JSONObject.quote(action)}, $payload, ${reply?.let { JSONObject.quote(it) } ?: "null"})")

    /** A script function by name (setupNewWorkbook…), with the changes it made. */
    suspend fun call(workbookJson: String, function: String): Result = evaluate(workbookJson, "__call(${JSONObject.quote(function)})")

    /** Several requests in a row on one loaded workbook (each sees the ones before): the replies and changes. */
    suspend fun sequence(workbookJson: String, requests: JSONArray): List<Result> {
        val all = evaluate(workbookJson, "JSON.stringify({reply:{ok:true},ops:$requests.map(function(r){return JSON.parse(__request(r.action, r.payload, null));})})")
        return (0 until all.ops.length()).map { i ->
            val one = all.ops.getJSONObject(i)
            Result(one.optJSONObject("reply") ?: JSONObject(), one.optJSONArray("ops") ?: JSONArray())
        }
    }

    private suspend fun evaluate(workbookJson: String, expression: String): Result = mutex.withLock {
        withContext(Dispatchers.Main) {
            val view = loaded()
            workbook = workbookJson
            val script = "(function(){try{__load(JSON.parse(__native.workbook()));return $expression;}" +
                "catch(e){return JSON.stringify({reply:{ok:false,error:String(e&&e.message||e)},ops:[],thrown:true});}})()"
            val raw = suspendCoroutine { cont -> view.evaluateJavascript(script) { cont.resume(it) } }
            workbook = "{}"
            // evaluateJavascript hands back the returned string JSON-encoded.
            val text = runCatching { JSONArray("[$raw]").getString(0) }.getOrNull()
                ?: throw IllegalStateException("The sheet logic didn't answer.")
            val out = JSONObject(text)
            if (out.optBoolean("thrown")) throw IllegalStateException(out.getJSONObject("reply").optString("error"))
            Result(out.optJSONObject("reply") ?: JSONObject(), out.optJSONArray("ops") ?: JSONArray())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun loaded(): WebView {
        webView?.let { return it }
        val scripts = (context.assets.list("backend") ?: emptyArray()).filter { Regex("^\\d\\d_.*\\.js$").matches(it) }.sorted()
        if (scripts.size < 30) throw IllegalStateException("Part of the app's sheet logic is missing.")
        val html = "<!doctype html><meta charset=utf-8><script>var console={log:function(){__native.log(Array.prototype.join.call(arguments,' '))}};</script>" +
            (listOf("gas-shim.js") + scripts).joinToString("") { "<script src=\"$it\"></script>" } +
            "<script>__overrides();</script>"
        val view = WebView(context.applicationContext)
        view.settings.javaScriptEnabled = true
        view.addJavascriptInterface(Bridge(), "__native")
        val ready = CompletableDeferred<Unit>()
        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) errors += "${message.sourceId()?.substringAfterLast('/')}: ${message.message()}"
                return true
            }
        }
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView?, url: String?) { ready.complete(Unit) }
        }
        view.loadDataWithBaseURL("file:///android_asset/backend/", html, "text/html", "utf-8", null)
        ready.await()
        val ok = suspendCoroutine { cont -> view.evaluateJavascript("typeof handleApi_ === 'function' && typeof __request === 'function'") { cont.resume(it) } }
        if (ok != "true") throw IllegalStateException("The app's sheet logic didn't load: ${errors.joinToString("; ").ifEmpty { "unknown error" }}")
        webView = view
        return view
    }
}
