package com.mohammedsharukms.mypersonaltracker.sheet.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.google.TrackerWorkbook
import kotlinx.coroutines.launch

/**
 * Settings ▸ Your sheet: sign in with Google and the tracker sheet is made in (or
 * found again in) the user's own Drive. No Apps Script, link or key. Mirrors GoogleSheetSection.swift.
 */
@Composable
fun GoogleSheetCard(store: SheetStore, onConnected: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = store.googleAuth
    var working by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmNewSheet by remember { mutableStateOf(false) }
    var backedUp by remember { mutableStateOf<String?>(null) }
    val connected = store.usesGoogleSheet && auth.isSignedIn

    suspend fun finish() {
        try {
            store.connectGoogleSheet { step = it }
            onConnected()
        } catch (e: Exception) {
            error = e.message ?: "Couldn't connect your sheet."
        } finally {
            working = false
            step = null
        }
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                runCatching { auth.finishSignIn(result.data) }.onFailure { error = it.message; working = false }.onSuccess { finish() }
            }
        } else {
            working = false   // Closed the consent screen: nothing to say.
        }
    }

    if (confirmSignOut) AppDialog(
        title = "Sign out of Google?",
        message = "Your sheet stays in your Google Drive. Sign in again to see it here.",
        confirmText = "Sign out",
        onConfirm = { confirmSignOut = false; scope.launch { store.disconnectGoogle() } },
        onDismiss = { confirmSignOut = false },
    )

    if (confirmNewSheet) AppDialog(
        title = "Make a new, empty sheet?",
        message = "The app starts using a fresh sheet with a period from today. Your current sheet stays in Google Drive, renamed “(old)”.",
        confirmText = "Make it",
        onConfirm = {
            confirmNewSheet = false
            working = true
            error = null
            scope.launch {
                try { store.startNewGoogleSheet { step = it }; onConnected() } catch (e: Exception) { error = e.message }
                working = false
                step = null
            }
        },
        onDismiss = { confirmNewSheet = false },
    )

    FormSection(
        "Your sheet",
        if (connected) "“${TrackerWorkbook.TITLE}” in your Google Drive. The app can only see the files it makes — nothing else in your Drive."
        else "Sign in and the app makes your tracker sheet in your own Google Drive. Nothing to set up. Signed in before? It finds your sheet again.",
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (connected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = SheetTheme.good, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Connected", color = SheetTheme.good, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(auth.email ?: "Google account", color = SheetTheme.secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                store.googleSheetUrl?.let { url ->
                    SmallTextButton("Open in Google Sheets") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
                if (working) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = SheetTheme.navy)
                        Spacer(Modifier.width(8.dp))
                        Text(step ?: "Making your new sheet…", color = SheetTheme.secondary, fontSize = 12.sp)
                    }
                } else {
                    SmallTextButton("Make a new sheet") { confirmNewSheet = true }
                    SmallTextButton("Back up to Google Drive") {
                        working = true
                        error = null
                        backedUp = null
                        step = "Copying your sheet…"
                        scope.launch {
                            try { backedUp = store.backUpGoogleSheet() } catch (e: Exception) { error = e.message }
                            working = false
                            step = null
                        }
                    }
                }
                backedUp?.let { Text("Backed up: “$it” and an Excel copy in “Life & Finance · Backups”.", color = SheetTheme.good, fontSize = 12.sp) }
                SmallTextButton("Sign out", color = SheetTheme.bad) { confirmSignOut = true }
                store.errorMessage?.let { Text(it, color = SheetTheme.bad, fontSize = 12.sp) }
            } else {
                GoogleSignInButton(store, onConnected)
            }
            error?.let { Text(it, color = SheetTheme.bad, fontSize = 12.sp) }
        }
    }
}


/**
 * Sign in with Google, then find or make the sheet and load it, with progress and errors under it.
 * The same button is on the welcome screen, the Connect card and Settings. Mirrors ConnectGoogleButton (WelcomeView.swift).
 */
@Composable
fun GoogleSignInButton(store: SheetStore, onConnected: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val auth = store.googleAuth
    var working by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun finish() {
        try {
            store.connectGoogleSheet { step = it }
            onConnected()
        } catch (e: Exception) {
            error = e.message ?: "Couldn't connect your sheet."
        } finally {
            working = false
            step = null
        }
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch { runCatching { auth.finishSignIn(result.data) }.onFailure { error = it.message; working = false }.onSuccess { finish() } }
        } else {
            working = false   // Closed the consent screen: nothing to say.
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(if (working) "Setting up your sheet…" else "Sign in with Google", Modifier.fillMaxWidth(), loading = working) {
            working = true
            error = null
            scope.launch {
                try {
                    val intent = auth.startSignIn()
                    if (intent != null) consent.launch(IntentSenderRequest.Builder(intent.intentSender).build()) else finish()
                } catch (e: Exception) {
                    error = e.message ?: "Google sign-in isn't available on this phone."
                    working = false
                }
            }
        }
        // Once the sheet is found, the same progress as the screens ("Building your habits…").
        if (working && store.isLoading) SheetLoadingCard(store.loadProgress)
        else step?.let { Text(it, color = SheetTheme.secondary, fontSize = 13.sp) }
        error?.let { Text(it, color = SheetTheme.bad, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}

/** First launch with no sheet connected: what the app is, and Sign in with Google. Mirrors WelcomeView.swift. */
@Composable
fun WelcomeScreen(store: SheetStore, onNotNow: () -> Unit, onWebApp: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onNotNow)
    Column(
        Modifier.fillMaxSize().background(SheetTheme.page).verticalScroll(rememberScrollState()).systemBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(SheetTheme.navy), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.TableChart, null, tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Your life, in one sheet", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("Habits, money, food, study and Mom & Dad — tracked in the app, kept in a Google Sheet that's yours.",
                fontSize = 16.sp, color = SheetTheme.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            WelcomePoint(Icons.Filled.Lock, "In your own Google Drive",
                "The app makes “${TrackerWorkbook.TITLE}” for you. It can only see the files it makes — nothing else in your Drive.")
            WelcomePoint(Icons.Filled.GridView, "Ready in seconds", "Every tab, formula and chart is set up. No scripts, links or keys.")
            WelcomePoint(Icons.Filled.Sync, "Both ways", "What you add here goes to the sheet; what you change in the sheet shows here.")
            WelcomePoint(Icons.Filled.PhoneAndroid, "On every phone", "Sign in on another phone and your sheet is there.")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GoogleSignInButton(store)
            SmallTextButton("Not now", onClick = onNotNow)
            SmallTextButton("I set up my sheet's web app myself", color = SheetTheme.secondary, onClick = onWebApp)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun WelcomePoint(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        IconBadge(icon, SheetTheme.navy, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            Text(text, fontSize = 15.sp, color = SheetTheme.secondary)
        }
    }
}
