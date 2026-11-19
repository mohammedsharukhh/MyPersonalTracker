package com.mohammedsharukms.mypersonaltracker.sheet.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mohammedsharukms.mypersonaltracker.BuildConfig
import com.mohammedsharukms.mypersonaltracker.container
import com.mohammedsharukms.mypersonaltracker.sheet.CardExpiry
import com.mohammedsharukms.mypersonaltracker.sheet.CardFormat
import com.mohammedsharukms.mypersonaltracker.sheet.CardKind
import com.mohammedsharukms.mypersonaltracker.sheet.CardStyle
import com.mohammedsharukms.mypersonaltracker.sheet.CardUnlock
import com.mohammedsharukms.mypersonaltracker.sheet.CardBrand
import com.mohammedsharukms.mypersonaltracker.sheet.SavedCard

// More ▸ Cards: the list (locked until the screen lock is confirmed), one
// card's details with show and copy, and the editor. Mirrors CardsView.swift.

@Composable
fun CardsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vault = context.container.cards
    var adding by remember { mutableStateOf<SavedCard?>(null) }
    var showing by remember { mutableStateOf<String?>(null) }
    val unlock = rememberDeviceUnlock(onSuccess = { vault.unlocked() }, onError = { vault.errorMessage = it })

    // No screenshots, and a blank preview in Recents, while cards can be on screen.
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            vault.lock()   // Leaving the screen locks them at once.
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vault.didEnterBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vault.didBecomeActive() }
    LaunchedEffect(Unit) { if (!vault.isUnlocked) unlock() }
    LaunchedEffect(vault.isUnlocked) { if (!vault.isUnlocked) { adding = null; showing = null } }

    adding?.let { CardEditorSheet(it, isNew = true) { adding = null } }
    showing?.let { id -> CardDetailSheet(id) { showing = null } }

    SheetScreen(
        "Cards", isRefreshing = false, onRefresh = {}, onBack = onBack,
        actions = {
            if (vault.isUnlocked) {
                IconButton(onClick = { adding = SavedCard() }) { Icon(Icons.Filled.Add, "Add card", tint = SheetTheme.navy) }
                IconButton(onClick = { vault.lock() }) { Icon(Icons.Filled.Lock, "Lock", tint = SheetTheme.navy) }
            }
        },
    ) {
        if (!vault.isUnlocked) {
            item {
                SheetCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconBadge(Icons.Filled.Lock, SheetTheme.navy, 56.dp)
                        Text("Cards are locked", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text)
                        Text("Your cards are kept on this phone only. Unlock with your fingerprint, face or screen lock to see them.",
                            fontSize = 15.sp, color = SheetTheme.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        vault.errorMessage?.let { Text(it, fontSize = 14.sp, color = SheetTheme.bad, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                        PrimaryButton("Unlock", Modifier.fillMaxWidth()) { unlock() }
                    }
                }
            }
            return@SheetScreen
        }
        if (vault.cards.isEmpty()) {
            item {
                SheetCard {
                    Text("Keep your cards handy", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                    Spacer(Modifier.size(6.dp))
                    Text("Save the number, expiry, CVV and name of each debit and credit card, so they're here when you pay online. Tap + to add one.",
                        fontSize = 15.sp, color = SheetTheme.secondary)
                }
            }
        }
        items(vault.cards, key = { it.id }) { card ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CardFace(card, revealed = false, Modifier.clickable(onClickLabel = "Show the full details") { showing = card.id })
                when (card.expiryState()) {
                    CardExpiry.EXPIRED -> StatusPill("Expired — add the new card", SheetTheme.bad)
                    CardExpiry.SOON -> StatusPill("Expires soon", SheetTheme.warn)
                    CardExpiry.FINE -> Unit
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Shield, null, tint = SheetTheme.secondary, modifier = Modifier.size(14.dp))
                Text("Saved only on this phone, encrypted with a key that only your fingerprint or screen lock unlocks. Never sent to your Google Sheet, not part of Export everything, and not backed up — your bank can always re-issue the details.",
                    fontSize = 12.sp, color = SheetTheme.secondary)
            }
        }
    }
}

/** The card as a card: bank, number, name, expiry and network on its colour. */
@Composable
private fun CardFace(card: SavedCard, revealed: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 190.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(card.style.from), Color(card.style.to))))
            .clearAndSetSemantics { contentDescription = card.spokenSummary() }
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(card.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 3, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(card.kind.label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
        }
        Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (revealed) card.groupedNumber else card.maskedNumber, fontSize = 21.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold, color = Color.White)
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    if (card.holder.isNotEmpty()) Text(card.holder.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2)
                    if (card.expiryText.isNotEmpty()) Text("VALID THRU ${card.expiryText}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.85f))
                }
                Spacer(Modifier.width(8.dp))
                Text(card.brand.mark, fontSize = 17.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = Color.White)
            }
        }
    }
}

/** One card: show or copy each detail. */
@Composable
private fun CardDetailSheet(id: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val vault = context.container.cards
    val card = vault.cards.firstOrNull { it.id == id }
    var showNumber by remember { mutableStateOf(false) }
    var showCvv by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    // Deleted from the editor, or locked.
    LaunchedEffect(card == null) { if (card == null) onDismiss() }
    if (card == null) return
    if (editing) CardEditorSheet(card, isNew = false) { editing = false }

    fun copy(text: String, what: String) {
        SensitiveClipboard.copy(context, what, text)
        Toast.makeText(context, "$what copied", Toast.LENGTH_SHORT).show()
    }

    EditorSheet(card.title, onDismiss, saveLabel = "Edit", cancelLabel = "Close", onSave = { editing = true }) {
        CardFace(card, revealed = showNumber)
        SheetCard {
            DetailRow("Card number", card.groupedNumber, hidden = card.maskedNumber,
                revealed = showNumber.takeIf { card.number.isNotEmpty() }, onToggle = { showNumber = !showNumber }) { copy(card.number, "Card number") }
            if (card.expiryText.isNotEmpty()) DetailRow("Expiry", card.expiryText) { copy(card.expiryText, "Expiry") }
            if (card.cvv.isNotEmpty()) DetailRow("CVV", card.cvv, hidden = "•••", revealed = showCvv, onToggle = { showCvv = !showCvv }) { copy(card.cvv, "CVV") }
            if (card.holder.isNotEmpty()) DetailRow("Name on card", card.holder) { copy(card.holder, "Name") }
        }
        Text("Copied details are hidden from the clipboard preview.", fontSize = 12.sp, color = SheetTheme.secondary)
        SheetCard {
            if (card.bank.isNotEmpty()) KeyValueRow("Bank", card.bank)
            KeyValueRow("Type", card.kind.label)
            if (card.brand != CardBrand.UNKNOWN) KeyValueRow("Network", card.brand.label)
        }
        if (card.notes.isNotEmpty()) Field("Notes") {
            SheetCard { androidx.compose.foundation.text.selection.SelectionContainer { Text(card.notes, fontSize = 15.sp, color = SheetTheme.text) } }
        }
    }
}

/** Title over a value, with show/hide (when it can be hidden) and copy. */
@Composable
private fun DetailRow(title: String, value: String, hidden: String? = null, revealed: Boolean? = null, onToggle: () -> Unit = {}, onCopy: () -> Unit) {
    val showing = revealed ?: true
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(title, fontSize = 12.sp, color = SheetTheme.secondary)
            Text(if (showing) value else (hidden ?: value), fontSize = 16.sp, fontFamily = FontFamily.Monospace, color = SheetTheme.text)
        }
        if (revealed != null) IconButton(onClick = onToggle) {
            Icon(if (showing) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showing) "Hide $title" else "Show $title", tint = SheetTheme.navy)
        }
        IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, "Copy $title", tint = SheetTheme.navy) }
    }
}

@Composable
private fun CardEditorSheet(initial: SavedCard, isNew: Boolean, onDismiss: () -> Unit) {
    val vault = LocalContext.current.container.cards
    var card by remember { mutableStateOf(initial) }
    /** Up to four digits, MMYY. */
    var expiry by remember { mutableStateOf(initial.expiryText.filter(Char::isDigit)) }
    var showCvv by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vault.errorMessage = null }

    val expiryValid = expiry.isEmpty() || CardFormat.expiry(expiry) != null
    val looksMistyped = card.number.length >= 12 && !CardFormat.passesLuhn(card.number)
    val canSave = expiryValid && (card.nickname.isNotBlank() || card.number.length >= 4)

    if (confirmDelete) AppDialog(
        title = "Delete ${card.title}?",
        message = "It's removed from this phone. This can't be undone.",
        style = DialogStyle.DESTRUCTIVE,
        confirmText = "Delete",
        onConfirm = { confirmDelete = false; vault.delete(initial.id); onDismiss() },
        onDismiss = { confirmDelete = false },
    )

    EditorSheet(if (isNew) "New card" else initial.title, onDismiss, canSave = canSave, error = vault.errorMessage, onSave = {
        val parsed = CardFormat.expiry(expiry)
        val saved = card.copy(
            nickname = card.nickname.trim(), bank = card.bank.trim(), holder = card.holder.trim(), notes = card.notes.trim(),
            expiryMonth = parsed?.first, expiryYear = parsed?.second,
        )
        if (vault.save(saved)) onDismiss()
    }) {
        Field("Nickname") { CardField(card.nickname, "e.g. HDFC Millennia") { card = card.copy(nickname = it) } }
        Field("Bank") { CardField(card.bank, "Bank") { card = card.copy(bank = it) } }
        Field("Type") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardKind.entries.forEach { k -> FilterPill(k.label, null, SheetTheme.navy, card.kind == k) { card = card.copy(kind = k) } }
            }
        }
        Field("Card number") {
            CardField(card.number, "Card number", keyboard = KeyboardType.Number, monospace = true, transformation = CardNumberTransformation) { text ->
                val digits = text.filter(Char::isDigit)
                card = card.copy(number = digits.take(CardBrand.of(digits).maxLength))
            }
            when {
                looksMistyped -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WarningAmber, null, tint = SheetTheme.warn, modifier = Modifier.size(14.dp))
                    Text("This number doesn't look right — check for a typo.", fontSize = 12.sp, color = SheetTheme.warn)
                }
                card.brand != CardBrand.UNKNOWN && card.number.isNotEmpty() -> Text(card.brand.label, fontSize = 12.sp, color = SheetTheme.secondary)
            }
        }
        Field("Name on card") {
            CardField(card.holder, "Name on card", capitalization = KeyboardCapitalization.Characters) { card = card.copy(holder = it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                Field("Expiry") {
                    CardField(expiry, "MM/YY", keyboard = KeyboardType.Number, monospace = true, transformation = ExpiryTransformation) {
                        expiry = it.filter(Char::isDigit).take(4)
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                Field("CVV") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            CardField(card.cvv, "CVV", keyboard = KeyboardType.NumberPassword, monospace = true,
                                transformation = if (showCvv) VisualTransformation.None else PasswordVisualTransformation()) {
                                card = card.copy(cvv = it.filter(Char::isDigit).take(4))
                            }
                        }
                        IconButton(onClick = { showCvv = !showCvv }) {
                            Icon(if (showCvv) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showCvv) "Hide CVV" else "Show CVV", tint = SheetTheme.navy)
                        }
                    }
                }
            }
        }
        if (!expiryValid) Text("Expiry is the month (01–12) and year, e.g. 08/29.", fontSize = 12.sp, color = SheetTheme.bad)
        Field("Colour") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CardStyle.entries.forEach { style ->
                    val selected = card.style == style
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).clickable { card = card.copy(style = style) }
                            .semantics { contentDescription = style.name.lowercase().replaceFirstChar(Char::uppercase); this.selected = selected },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(style.from), Color(style.to))))
                                .then(if (selected) Modifier.border(2.dp, SheetTheme.navy, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        }
        Field("Notes (optional)") { CardField(card.notes, "Bill date, limit, customer care number…", singleLine = false) { card = card.copy(notes = it) } }
        if (!isNew) SheetCard(onClick = { confirmDelete = true }) { Text("Delete card", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold) }
    }
}

/** PlainField with a keyboard type, a display format and no autocorrect or suggestions. */
@Composable
private fun CardField(
    value: String,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    monospace: Boolean = false,
    singleLine: Boolean = true,
    transformation: VisualTransformation = VisualTransformation.None,
    onChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        textStyle = TextStyle(fontSize = 16.sp, color = SheetTheme.text, fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, autoCorrectEnabled = false, keyboardType = keyboard),
        visualTransformation = transformation,
        cursorBrush = SolidColor(SheetTheme.navy),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SheetTheme.input).padding(14.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, fontSize = 16.sp, color = SheetTheme.tertiary)
                inner()
            }
        },
    )
}

/** Shows the digits grouped like the card ("4111 1111 …"), keeping the cursor in step. */
private object CardNumberTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val ends = CardFormat.groupEnds(text.text)
        return TransformedText(AnnotatedString(CardFormat.grouped(text.text)), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + ends.count { it < offset }
            override fun transformedToOriginal(offset: Int) = offset - ends.withIndex().count { (i, end) -> end + i < offset }
        })
    }
}

/** "0829" shows as "08/29". */
private object ExpiryTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val slash = text.length > 2
        return TransformedText(AnnotatedString(CardFormat.expiryField(text.text)), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (slash && offset > 2) offset + 1 else offset
            override fun transformedToOriginal(offset: Int) = if (slash && offset > 2) offset - 1 else offset
        })
    }
}

/** Fingerprint, face or the screen lock, whichever the phone has. */
@Composable
private fun rememberDeviceUnlock(onSuccess: () -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val success by rememberUpdatedState(onSuccess)
    val error by rememberUpdatedState(onError)
    // Android 8–9: the system's confirm-your-screen-lock screen.
    val keyguardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) success()
    }
    return remember(context) {
        {
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            when {
                !keyguard.isDeviceSecure ->
                    if (BuildConfig.DEBUG && isEmulator()) success()   // Emulators usually have no screen lock.
                    else error("Set a screen lock in Settings ▸ Security first. Cards only open behind it.")
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    val builder = BiometricPrompt.Builder(context).setTitle("Show your saved cards")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        builder.setAllowedAuthenticators(CardUnlock.AUTHENTICATORS)
                    } else {
                        @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true)
                    }
                    // Never crash on the way in: anything the prompt can't do just stays locked, with a message.
                    try {
                        builder.build().authenticate(CancellationSignal(), context.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = success()
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                                // Closed the prompt: stay locked, say nothing.
                                if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED && errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED) {
                                    error(errString?.toString() ?: "Couldn't unlock.")
                                }
                            }
                        })
                    } catch (e: Exception) {
                        error("Couldn't ask for your fingerprint or screen lock. Try again.")
                    }
                }
                else -> {
                    @Suppress("DEPRECATION")
                    val intent = keyguard.createConfirmDeviceCredentialIntent("Cards", "Show your saved cards")
                    if (intent != null) runCatching { keyguardLauncher.launch(intent) }.onFailure { error("Couldn't ask for your screen lock.") }
                    else error("Couldn't ask for your screen lock.")
                }
            }
        }
    }
}

private fun isEmulator() = Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") || Build.FINGERPRINT.startsWith("generic")

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Copies marked sensitive (no clipboard preview), cleared after a minute if they're still there. */
private object SensitiveClipboard {
    private val handler = Handler(Looper.getMainLooper())

    fun copy(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
        }
        clipboard.setPrimaryClip(clip)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            // Only ours: never wipe something copied since. (Unreadable from the background on Android 10+.)
            val current = runCatching { clipboard.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
            if (current == text) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }, 60_000)
    }
}
