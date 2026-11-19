package com.mohammedsharukms.mypersonaltracker.sheet.google

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sign in with Google through Google Play services, asking only for `drive.file`:
 * the files this app makes or opens — nothing else in the Drive. Google keeps the
 * grant; each access token (an hour) is fetched again quietly when needed.
 * Mirrors GoogleAuth.swift (the Android OAuth client is matched by package and SHA-1).
 */
class GoogleAuth(private val context: Context) {
    private val prefs = context.getSharedPreferences("google", Context.MODE_PRIVATE)

    var isSignedIn by mutableStateOf(prefs.getBoolean(SIGNED_IN, false))
        private set
    var email by mutableStateOf(prefs.getString(EMAIL, null))
        private set

    private var token: String? = null
    private var tokenAt = 0L

    private val request: AuthorizationRequest
        get() = AuthorizationRequest.builder().setRequestedScopes(SCOPES.map { Scope(it) }).build()

    /**
     * Step one of signing in. Null when access is already granted (signed in now);
     * otherwise Google's consent screen, to launch with StartIntentSenderForResult
     * and finish with [finishSignIn].
     */
    suspend fun startSignIn(): PendingIntent? {
        val result = authorize()
        if (result.hasResolution()) return result.pendingIntent
        keep(result)
        return null
    }

    /** Step two: the consent screen's answer. */
    suspend fun finishSignIn(data: Intent?) {
        val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        keep(result)
    }

    /** A token good for a while yet, fetched again quietly when it's old. */
    suspend fun validAccessToken(): String {
        token?.let { if (System.currentTimeMillis() - tokenAt < 50 * 60_000) return it }
        if (!isSignedIn) throw GoogleException.SignedOut()
        val result = authorize()
        // Needing the consent screen again means the grant was removed in the Google account.
        if (result.hasResolution()) { forget(); throw GoogleException.SignedOut() }
        keep(result)
        return token ?: throw GoogleException.SignedOut()
    }

    /** Forgets the sign-in here and revokes it at Google. */
    suspend fun signOut() {
        val old = token
        forget()
        if (old != null) {
            runCatching {
                suspendCancellableCoroutine<Unit> { cont ->
                    Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(old).build())
                        .addOnCompleteListener { cont.resume(Unit) }
                }
            }
            withContext(Dispatchers.IO) {
                runCatching {
                    (URL("https://oauth2.googleapis.com/revoke?token=" + URLEncoder.encode(old, "UTF-8")).openConnection() as HttpURLConnection).run {
                        requestMethod = "POST"; doOutput = true; outputStream.close(); responseCode; disconnect()
                    }
                }
            }
        }
    }

    private suspend fun authorize(): AuthorizationResult = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(GoogleException.Failed(it.message ?: "Google sign-in isn't available on this phone.")) }
    }

    private suspend fun keep(result: AuthorizationResult) {
        val access = result.accessToken ?: throw GoogleException.Failed("Google didn't grant access.")
        token = access
        tokenAt = System.currentTimeMillis()
        if (!isSignedIn) {
            isSignedIn = true
            prefs.edit().putBoolean(SIGNED_IN, true).apply()
        }
        if (email == null) {
            email = withContext(Dispatchers.IO) {
                runCatching {
                    val c = URL("https://openidconnect.googleapis.com/v1/userinfo").openConnection() as HttpURLConnection
                    c.setRequestProperty("Authorization", "Bearer $access")
                    JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optString("email").ifEmpty { null }
                }.getOrNull()
            }
            prefs.edit().putString(EMAIL, email).apply()
        }
    }

    private fun forget() {
        token = null
        isSignedIn = false
        email = null
        prefs.edit().clear().apply()
    }

    companion object {
        const val DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
        val SCOPES = listOf("openid", "email", DRIVE_FILE)
        private const val SIGNED_IN = "signedIn"
        private const val EMAIL = "email"
    }
}

sealed class GoogleException(message: String) : Exception(message) {
    class SignedOut : GoogleException("Sign in with Google again.")
    class Failed(message: String) : GoogleException(message)
    class Http(val status: Int, message: String, val retryAfter: String? = null) : GoogleException("Google answered $status: $message")
}

/**
 * A busy Google ("too many requests", 429, or 503) answers the same request later: wait and
 * ask again, longer each time, as Google advises. A 429 means nothing was done, so asking
 * again never makes a change twice. Mirrors GoogleRetry in GoogleAuth.swift.
 */
object GoogleRetry {
    /** About a minute in all: the window Sheets counts its per-minute quota over. */
    val waits = listOf(2.0, 4.0, 8.0, 16.0, 32.0)

    /** Told before each pause (the loading screen says "carrying on in 8s"), for the coroutine it's added to. */
    class Listener(val onWait: (Double) -> Unit) : kotlin.coroutines.AbstractCoroutineContextElement(Listener) {
        companion object Key : kotlin.coroutines.CoroutineContext.Key<Listener>
    }

    fun wait(status: Int, attempt: Int, retryAfter: String?, waits: List<Double> = this.waits): Double? {
        if ((status != 429 && status != 503) || attempt >= waits.size) return null
        retryAfter?.toDoubleOrNull()?.takeIf { it >= 0 }?.let { return minOf(it, 60.0) }
        return waits[attempt]
    }
}
