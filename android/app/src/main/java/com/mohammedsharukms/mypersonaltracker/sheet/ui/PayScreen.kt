package com.mohammedsharukms.mypersonaltracker.sheet.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.mohammedsharukms.mypersonaltracker.R
import com.mohammedsharukms.mypersonaltracker.container
import com.mohammedsharukms.mypersonaltracker.sheet.PaymentStatus
import com.mohammedsharukms.mypersonaltracker.sheet.FoundUpiApp
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.UpiAppCatalog
import com.mohammedsharukms.mypersonaltracker.sheet.UpiAppTrust
import com.mohammedsharukms.mypersonaltracker.sheet.UpiOutcome
import com.mohammedsharukms.mypersonaltracker.sheet.UpiPayee
import com.mohammedsharukms.mypersonaltracker.sheet.UpiPayment
import com.mohammedsharukms.mypersonaltracker.sheet.UpiRequest
import com.mohammedsharukms.mypersonaltracker.sheet.UpiSafety
import com.mohammedsharukms.mypersonaltracker.sheet.UpiScan
import com.mohammedsharukms.mypersonaltracker.sheet.rupees

// Pay with UPI: who (scan their QR, a recent payee or a UPI ID), how much, which
// app — then Pay opens that app. Its answer, or yours, decides Paid / Failed; a paid
// one is added to Finance. Mirrors PayView.swift.

/** A UPI app on this phone; [verified] per UpiAppTrust. */
private data class UpiAppChoice(val packageName: String, val label: String, val icon: androidx.compose.ui.graphics.ImageBitmap?,
                                val installed: Boolean = true, val verified: Boolean = true)


@Composable
fun PayScreen(store: SheetStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val upi = context.container.upi
    val categories = store.snapshot?.lists?.categories.orEmpty()
    // Every UPI app, always; checked again on coming back (e.g. from installing one on Google Play).
    var apps by remember { mutableStateOf(allUpiApps(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { apps = allUpiApps(context) }
    var notInstalled by remember { mutableStateOf<UpiAppChoice?>(null) }
    /** Picked an app UpiAppTrust can't vouch for: asked before it opens. */
    var unverified by remember { mutableStateOf<UpiAppChoice?>(null) }
    var vpa by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    /** The name was filled in from a QR code (anyone can print one): said so under it. */
    var nameFromQr by remember { mutableStateOf(false) }
    var mcc by remember { mutableStateOf<String?>(null) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categories.firstOrNull { it == "Other" } ?: categories.lastOrNull().orEmpty()) }
    var chosen by remember { mutableStateOf<String?>(apps.firstOrNull { it.installed && it.packageName != UpiAppCatalog.ANY }?.packageName ?: UpiAppCatalog.ANY) }
    var message by remember { mutableStateOf<String?>(null) }
    var paying by remember { mutableStateOf<String?>(null) }
    /** The payment went to an app we can vouch for, so its "SUCCESS" is believed. */
    var payingVerified by remember { mutableStateOf(false) }

    val amount = amountText.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
    val payee = vpa.trim().takeIf { UpiPayee.isValidVpa(it) }?.let { UpiPayee(it, name.trim(), mcc) }

    fun choose(p: UpiPayee) {
        vpa = p.vpa; name = p.name; mcc = p.mcc; nameFromQr = false
        category = upi.lastCategory(p.vpa) ?: CategoryGuess.guess(p.name, categories) ?: p.mcc?.let { categoryForMcc(it, categories) } ?: category
    }
    // "Send money" to Mom: her UPI ID, Remittance and a note, ready for the amount.
    LaunchedEffect(Unit) {
        upi.payTo?.let { to ->
            vpa = to.payee.vpa; name = to.payee.name
            if (to.category in categories) category = to.category
            note = to.note
            upi.payTo = null
        }
    }

    // The UPI app's answer comes back here (success / failure); anything else, we ask.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = paying ?: return@rememberLauncherForActivityResult
        paying = null
        val data = result.data
        val response = data?.getStringExtra("response") ?: data?.getStringExtra("Status")?.let { "Status=$it" }
        upi.answered(id, UpiOutcome.parse(response, reference = id).trusted(payingVerified))
    }

    var scanning by remember { mutableStateOf(false) }
    fun scan() { scanning = true }
    if (scanning) QrScanner(
        onFound = { found ->
            scanning = false
            choose(found.payee)
            nameFromQr = found.payee.name.isNotBlank()
            found.amount?.let { amountText = SheetFormat.plain(it) }
            if (note.isBlank()) found.note?.let { note = it }
        },
        onDismiss = { scanning = false },
    )

    fun pay(trustAnyway: Boolean = false) {
        val p = payee ?: return
        val a = amount ?: return
        val picked = apps.firstOrNull { it.packageName == chosen }
        if (picked != null && !picked.installed) { notInstalled = picked; return }
        if (picked != null && !picked.verified && !trustAnyway) { unverified = picked; return }
        val app = picked?.takeIf { it.packageName != UpiAppCatalog.ANY }
        val request = UpiRequest(p, Math.round(a * 100) / 100.0, note.trim(), "MPT" + System.currentTimeMillis())
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(request.uri)).apply { app?.let { setPackage(it.packageName) } }
        upi.started(request, category, app?.label ?: "UPI app")
        paying = request.reference
        // Android's picker could hand it to any app: only an app we chose and can vouch for is believed.
        payingVerified = app != null && app.verified
        try {
            launcher.launch(if (app == null) Intent.createChooser(intent, "Pay with") else intent)
            amountText = ""; note = ""
        } catch (e: Exception) {
            upi.cancel(request.reference)
            paying = null
            message = "${app?.label ?: "The UPI app"} didn't open. Pick another app."
        }
    }

    // From the widget: the camera first.
    LaunchedEffect(Unit) { if (upi.payWithScan) { upi.payWithScan = false; scan() } }

    EditorSheet("Pay with UPI", onDismiss, onSave = null) {
        Field("Pay to") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SheetTheme.navy).clickable { scan() }.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.QrCodeScanner, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan their QR code", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                if (upi.recentPayees.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        upi.recentPayees.forEach { p -> FilterPill(p.label, Icons.Filled.Person, SheetTheme.navy, p.vpa == vpa) { choose(p) } }
                    }
                }
                BasicTextField(vpa, { vpa = it.trim() }, singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = SheetTheme.text),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None),
                    cursorBrush = SolidColor(SheetTheme.navy),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SheetTheme.input).padding(14.dp),
                    decorationBox = { inner -> Box { if (vpa.isEmpty()) Text("UPI ID, e.g. shop@okicici", fontSize = 16.sp, color = SheetTheme.tertiary); inner() } })
                if (vpa.isNotEmpty() && payee == null) Text("That doesn't look like a UPI ID (name@bank).", fontSize = 12.sp, color = SheetTheme.bad)
                PlainField(name, "Name (optional)") { name = it; nameFromQr = false }
                if (nameFromQr) Text(UpiSafety.QR_NAME, fontSize = 12.sp, color = SheetTheme.secondary)
            }
        }
        Field("Amount") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SheetTheme.card).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("₹", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = SheetTheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(amountText, { amountText = it }, singleLine = true,
                        textStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), cursorBrush = SolidColor(SheetTheme.navy),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner -> Box { if (amountText.isEmpty()) Text("0", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = SheetTheme.tertiary); inner() } })
                }
                if (amount != null && Money.main != "INR") {
                    val converted = context.container.currency.convert(amount, "INR", Money.main)
                    Text(if (converted != null) "Goes into Finance as ${SheetFormat.qar(converted)} ${Money.main}"
                         else "No INR rate yet — set one in the currency converter so it can go into Finance.",
                        fontSize = 12.sp, color = if (converted != null) SheetTheme.secondary else SheetTheme.warn)
                }
                PlainField(note, "What for? (optional)") { note = it }
                if (categories.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { c -> FilterPill(c, null, SheetTheme.navy, c == category) { category = c } }
                    }
                }
            }
        }
        Field("Pay with") {
            run {
                apps.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { app ->
                            val selected = app.packageName == chosen
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) SheetTheme.navy.copy(alpha = 0.12f) else SheetTheme.card)
                                    .border(2.dp, if (selected) SheetTheme.navy else Color.Transparent, RoundedCornerShape(14.dp))
                                    .clickable { chosen = app.packageName }
                                    .semantics {
                                        if (!app.installed) stateDescription = "Not on this phone"
                                        else if (!app.verified) stateDescription = "Not from Google Play"
                                        this.selected = selected
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val dim = Modifier.alpha(if (app.installed) 1f else 0.45f)
                                if (app.icon != null) Image(app.icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).then(dim))
                                else Icon(Icons.Filled.CurrencyRupee, null, tint = SheetTheme.navy, modifier = Modifier.size(40.dp))
                                Text(app.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (app.installed) SheetTheme.text else SheetTheme.secondary, maxLines = 1)
                                if (!app.installed) Text("Not found", fontSize = 11.sp, color = SheetTheme.secondary)
                                else if (!app.verified) Text("Unverified", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.warn)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Text("Your UPI app opens with everything filled in — check it, then enter your PIN there. A paid one is added to Finance. “Not found” ones aren't on this phone: pick one to get it from Google Play. “Unverified” ones weren't installed from Google Play.",
                fontSize = 12.sp, color = SheetTheme.secondary)
            Spacer(Modifier.size(6.dp))
            Text(UpiSafety.PIN, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
        }
        PrimaryButton(amount?.let { "Pay ${rupees(it)}" } ?: "Pay", Modifier.fillMaxWidth(), enabled = payee != null && amount != null && chosen != null) { pay() }
        if (upi.payments.isNotEmpty()) {
            Field("Recent payments") {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card)) {
                    upi.payments.take(15).forEachIndexed { i, p ->
                        if (i > 0) RowDivider()
                        PaymentRow(p)
                    }
                }
            }
        }
    }
    notInstalled?.let { app ->
        AppDialog(
            title = "${app.label} isn't on this phone",
            message = "Get it from Google Play, then come back and pay — or pick another app.",
            confirmText = "Google Play",
            dismissText = "Not now",
            onConfirm = { notInstalled = null; openPlayStore(context, app.label) },
            onDismiss = { notInstalled = null },
        )
    }
    unverified?.let { app ->
        AppDialog(
            title = "Is this the real ${app.label}?",
            message = UpiSafety.unverified(app.label),
            confirmText = "Pay anyway",
            dismissText = "Cancel",
            onConfirm = { unverified = null; pay(trustAnyway = true) },
            onDismiss = { unverified = null },
        )
    }
    message?.let { AppDialog(title = "Pay with UPI", message = it, style = DialogStyle.INFO, dismissText = null, onConfirm = { message = null }, onDismiss = { message = null }) }
}

@Composable
private fun PaymentRow(p: UpiPayment) {
    val upi = LocalContext.current.container.upi
    val (icon, tint, status) = when (p.status) {
        PaymentStatus.PAID -> Triple(Icons.Filled.CheckCircle, SheetTheme.good, "Paid")
        PaymentStatus.FAILED -> Triple(Icons.Filled.Cancel, SheetTheme.bad, "Failed")
        PaymentStatus.WAITING -> Triple(Icons.Filled.Schedule, SheetTheme.warn, "Did it go through?")
    }
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.payee.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, maxLines = 1)
                val time = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(p.date))
                Text("$time · ${p.app} · $status", fontSize = 12.sp, color = SheetTheme.secondary)
            }
            Text(rupees(p.amount), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text)
        }
        if (p.status == PaymentStatus.WAITING) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(SheetTheme.good).clickable { upi.markPaid(p.id) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center) { Text("Paid", color = Color.White, fontWeight = FontWeight.SemiBold) }
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(SheetTheme.bad.copy(alpha = 0.12f)).clickable { upi.markFailed(p.id) }
                    .padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text("Failed", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** Back from the UPI app without an answer: "Did the payment go through?" */
@Composable
fun PaymentQuestion() {
    val upi = LocalContext.current.container.upi
    val p = upi.asking ?: return
    AppDialog(
        title = "Did the payment go through?",
        message = "${rupees(p.amount)} to ${p.payee.label} with ${p.app}.",
        icon = Icons.Filled.CurrencyRupee,
        confirmText = "Yes, paid",
        dismissText = "No, it failed",
        onConfirm = { upi.markPaid(p.id) },
        onDismissButton = { upi.markFailed(p.id) },
        onDismiss = { upi.asking = null },
    )
}

private fun tile(context: Context, resource: Int) = runCatching { BitmapFactory.decodeResource(context.resources, resource).asImageBitmap() }.getOrNull()

/** Every UPI app (see UpiAppCatalog.arrange): the installed ones with their own name and icon, the rest with their tile. */
private fun allUpiApps(context: Context): List<UpiAppChoice> {
    val pm = context.packageManager
    val link = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay?pa=test@upi&pn=Test&am=1&cu=INR"))
    fun query(intent: Intent): List<ResolveInfo> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
    }.getOrDefault(emptyList())
    // Whatever answers the link, plus the usual ones asked by name.
    val resolved = (query(link) + UpiAppCatalog.known.flatMap { query(Intent(link).setPackage(it.packageName)) })
        .filter { it.activityInfo.exported }
        .associateBy { it.activityInfo.packageName }
    val found = resolved.map { (pkg, info) -> FoundUpiApp(pkg, info.loadLabel(pm).toString(), isVerified(pm, pkg, info)) }
    return UpiAppCatalog.arrange(found, context.packageName).map { listing ->
        val icon = resolved[listing.packageName]?.let { runCatching { it.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
        UpiAppChoice(listing.packageName, listing.label, icon ?: tile(context, listing.tile), listing.installed, listing.verified)
    }
}

/** From Google Play, or put on the phone by its maker (UpiAppTrust). */
private fun isVerified(pm: PackageManager, packageName: String, info: ResolveInfo): Boolean {
    val installer = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) pm.getInstallSourceInfo(packageName).installingPackageName
        else @Suppress("DEPRECATION") pm.getInstallerPackageName(packageName)
    }.getOrNull()
    val system = (info.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
    return UpiAppTrust.isVerified(installer, system)
}

/** Google Play, searching for the app by name. */
private fun openPlayStore(context: Context, name: String) {
    val query = Uri.encode(name)
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$query&c=apps")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(market) }.onFailure {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$query&c=apps")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** A few common merchant codes on shop QRs. */
fun categoryForMcc(mcc: String, categories: List<String>): String? {
    val wanted = when (mcc) {
        "5411", "5422", "5499" -> "Groceries"
        "5812", "5813", "5814" -> "Food & Dining"
        "5541", "5542", "4121", "4111" -> "Transport"
        "5912" -> "Health & Pharmacy"
        "4814", "4899" -> "Mobile & Internet"
        "5651", "5691", "5699" -> "Clothing"
        else -> null
    }
    return wanted?.takeIf { it in categories }
}

/** Pay with UPI, raised in the tab bar: tap to scan a QR code, touch and hold to type a UPI ID.
 *  A badge counts payments still to confirm. Mirrors UpiTabButton in PayView.swift. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun UpiTabButton(modifier: Modifier = Modifier) {
    val upi = LocalContext.current.container.upi
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val waiting = upi.payments.count { it.status == PaymentStatus.WAITING }
    var menu by remember { mutableStateOf(false) }
    fun open(scan: Boolean) { upi.payWithScan = scan; upi.showPay = true }
    // Sits in the bar like a tab, in the app's action colour: the one thing in the bar that does rather than goes.
    Box(modifier) {
        Box(
            Modifier.size(48.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(SheetTheme.navy)
                .combinedClickable(
                    onClickLabel = "Scan a QR code",
                    onLongClickLabel = "Pay to a UPI ID",
                    onClick = { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); open(scan = true) },
                    onLongClick = { menu = true },
                )
                .semantics { contentDescription = if (waiting > 0) "Pay with UPI, $waiting payment${if (waiting == 1) "" else "s"} to confirm" else "Pay with UPI" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        if (waiting > 0) Text("$waiting", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(50)).background(Color(0xFFFFC75A))
                .padding(horizontal = 6.dp, vertical = 1.dp))
        androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
            androidx.compose.material3.DropdownMenuItem({ Text("Scan a QR code") }, { menu = false; open(scan = true) },
                leadingIcon = { Icon(Icons.Filled.QrCodeScanner, null) })
            androidx.compose.material3.DropdownMenuItem({ Text("Pay to a UPI ID") }, { menu = false; open(scan = false) },
                leadingIcon = { Icon(Icons.Filled.CurrencyRupee, null) })
        }
    }
}
