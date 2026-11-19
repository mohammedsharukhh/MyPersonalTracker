package com.mohammedsharukms.mypersonaltracker.sheet.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.mohammedsharukms.mypersonaltracker.R
import com.mohammedsharukms.mypersonaltracker.sheet.UpiScan
import com.mohammedsharukms.mypersonaltracker.sheet.rupees
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// The QR scanner. Calm until it matters:
//  • Opening — the camera fades in and the four corners draw themselves.
//  • Looking — aurora-lit corners breathing gently, a soft band of light drifting
//    through the frame, the picture's edges softly darkened.
//  • A code in view — the corners glide onto it, wherever and however tilted, and follow it.
//  • A UPI code — they lock on in white and green, a crystal chime and a buzz, and it
//    goes straight into Pay with who you're paying (and the amount, if the QR has one) filled in.
//  Also: scan from Photos (a QR someone sent), pinch or double-tap to zoom, the torch.
//  Animations off (Settings ▸ Accessibility): no breathing or drifting light. Mirrors LensScanner.swift.

private val Aurora = listOf(Color(0xFF40F2B3), Color(0xFF4DCCFF), Color(0xFF9E8CFF), Color(0xFF40F2B3))
private val LockGreen = Color(0xFF38E68C)
private val Amber = Color(0xFFFFA43A)
private val Glass = Color(0x8C16181D)
private val GlassEdge = Color(0x24FFFFFF)
private const val CENTER_Y = 0.42f

/** Four corners of a code (or the frame) on screen, in pixels. */
private data class Quad(val pts: List<Offset>) {
    val center get() = Offset(pts.sumOf { it.x.toDouble() }.toFloat() / 4, pts.sumOf { it.y.toDouble() }.toFloat() / 4)

    /** Grown outward from the centre, so the corners sit just outside the code. */
    fun padded(by: Float): Quad {
        val c = center
        return Quad(pts.map { p ->
            val d = hypot(p.x - c.x, p.y - c.y)
            if (d == 0f) p else Offset(p.x + (p.x - c.x) / d * by * 1.41f, p.y + (p.y - c.y) / d * by * 1.41f)
        })
    }

    companion object {
        /** The frame where nothing's in view: a square a little above the middle. */
        fun home(size: Size, density: Float): Quad {
            val side = min(size.width * 0.70f, 290 * density)
            val c = Offset(size.width / 2, size.height * CENTER_Y)
            val h = side / 2
            return Quad(listOf(Offset(c.x - h, c.y - h), Offset(c.x + h, c.y - h), Offset(c.x + h, c.y + h), Offset(c.x - h, c.y + h)))
        }
    }
}

/** Debug builds: `lensDemo` shows a code already read, for screenshots (the emulator's camera can't see a real QR). */
object LensDebug { var demo = false }

private sealed interface LensResult {
    data class Upi(val scan: UpiScan) : LensResult
    data class Other(val text: String) : LensResult
}

@Composable
fun QrScanner(onFound: (UpiScan) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val haptics = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val density = context.resources.displayMetrics.density
        val reduceMotion = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
        var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
        var asked by remember { mutableStateOf(false) }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
        LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
        val sound = remember { LockSound(context) }
        DisposableEffect(Unit) { onDispose { sound.release() } }

        var tracked by remember { mutableStateOf<Quad?>(null) }
        var result by remember { mutableStateOf<LensResult?>(null) }
        var torch by remember { mutableStateOf(false) }
        var zoom by remember { mutableFloatStateOf(1f) }
        var ignored by remember { mutableStateOf<Pair<String, Long>?>(null) }
        val countdown = remember { Animatable(0f) }
        val appeared = remember { Animatable(if (reduceMotion) 1f else 0f) }
        LaunchedEffect(Unit) { appeared.animateTo(1f, tween(700)) }
        LaunchedEffect(Unit) {
            if (!LensDebug.demo) return@LaunchedEffect
            delay(1200)
            result = LensResult.Upi(UpiScan(com.mohammedsharukms.mypersonaltracker.sheet.UpiPayee("freshmart@okicici", "FreshMart Supermarket"), 250.0))
            countdown.animateTo(0.6f, tween(1100))
        }

        val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
        DisposableEffect(Unit) { onDispose { scanner.close() } }
        val controller = remember {
            LifecycleCameraController(context).apply {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            }
        }

        fun lock(scan: UpiScan) {
            result = LensResult.Upi(scan)
            sound.play()
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            // Straight into Pay with it filled in: no card to tap, nothing timing out. Pay's own Scan button rescans.
            controller.enableTorch(false)
            onFound(scan)
        }

        fun other(text: String) {
            result = LensResult.Other(text)
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            scope.launch { delay(1800); if (result is LensResult.Other) result = null }
        }

        /** What the camera sees (already in screen pixels): follow the first code; read it once. */
        fun see(codes: List<Barcode>) {
            if (result is LensResult.Upi) return
            val first = codes.firstOrNull { it.rawValue != null && it.cornerPoints?.size == 4 }
            if (first == null) { tracked = null; return }
            tracked = Quad(first.cornerPoints!!.map { Offset(it.x.toFloat(), it.y.toFloat()) }).padded(14 * density)
            if (result != null) return
            val text = first.rawValue!!
            val scan = UpiScan.parse(text)
            when {
                scan != null -> lock(scan)
                ignored?.first != text || System.currentTimeMillis() - (ignored?.second ?: 0) > 4000 -> {
                    ignored = text to System.currentTimeMillis()
                    other(text)
                }
            }
        }

        if (granted) DisposableEffect(lifecycleOwner) {
            val executor = Executors.newSingleThreadExecutor()
            controller.setImageAnalysisAnalyzer(executor, MlKitAnalyzer(listOf(scanner), CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
                ContextCompat.getMainExecutor(context)) { r -> see(r.getValue(scanner).orEmpty()) })
            controller.bindToLifecycle(lifecycleOwner)
            onDispose {
                controller.enableTorch(false)
                controller.clearImageAnalysisAnalyzer()
                controller.unbind()
                executor.shutdown()
            }
        }

        // A QR from Photos (a screenshot someone sent).
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
            if (image == null) { other(""); return@rememberLauncherForActivityResult }
            scanner.process(image)
                .addOnSuccessListener { codes ->
                    val text = codes.firstNotNullOfOrNull { it.rawValue }
                    val scan = text?.let { UpiScan.parse(it) }
                    if (scan != null) lock(scan) else other(text.orEmpty())
                }
                .addOnFailureListener { other("") }
        }

        fun setZoom(value: Float) {
            val state = controller.zoomState.value
            zoom = value.coerceIn(max(1f, state?.minZoomRatio ?: 1f), min(8f, state?.maxZoomRatio ?: 1f))
            controller.setZoomRatio(zoom)
        }

        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) { detectTransformGestures { _, _, scale, _ -> if (scale != 1f) setZoom(zoom * scale) } }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { setZoom(if (zoom < 1.8f) 2f else 1f) }) },
        ) {
            if (granted) AndroidView(
                factory = {
                    PreviewView(it).apply {
                        // The fast (SurfaceView) preview; it fades in under a black layer below.
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        this.controller = controller
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (appeared.value < 1f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - appeared.value }.background(Color.Black))
            Finder(tracked, result, appeared.value, reduceMotion)

            // Top: close, title, torch.
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp).graphicsLayer { alpha = appeared.value },
                verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.Filled.Close, "Close", onClick = onDismiss)
                Text("Scan & Pay", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                if (granted && controller.cameraInfo?.hasFlashUnit() != false) {
                    RoundButton(if (torch) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff, if (torch) "Torch off" else "Torch on", lit = torch) {
                        torch = !torch
                        controller.enableTorch(torch)
                    }
                } else Spacer(Modifier.size(48.dp))
            }

            // Bottom: the hint and tools, or the card.
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 20.dp)
                .graphicsLayer { alpha = appeared.value }, horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(result != null, enter = slideInVertically { it / 2 } + fadeIn(), exit = slideOutVertically { it / 2 } + fadeOut()) {
                    when (val r = result) {
                        is LensResult.Upi -> PayeeCard(r.scan, countdown.value,
                            onPay = { controller.enableTorch(false); onFound(r.scan) },
                            onAgain = { result = null; tracked = null; scope.launch { countdown.snapTo(0f) } })
                        is LensResult.Other -> NotUpiCard(r.text)
                        null -> Spacer(Modifier.height(1.dp))
                    }
                }
                if (result == null) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (!granted && asked) "Camera not allowed — use Photos, or allow it in Settings" else "Point at any UPI QR",
                        color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(50)).background(Glass)
                            .border(1.dp, GlassEdge, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.height(48.dp).clip(RoundedCornerShape(50)).background(Glass).border(1.dp, GlassEdge, RoundedCornerShape(50))
                            .clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Text("From Photos", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        if (granted) Box(Modifier.size(48.dp).clip(CircleShape).background(Glass).border(1.dp, GlassEdge, CircleShape)
                            .clickable { setZoom(if (zoom < 1.8f) 2f else 1f) }, contentAlignment = Alignment.Center) {
                            Text(if (zoom < 1.8f) "2×" else "1×", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

/** The corners and the light, drawn each frame. */
@Composable
private fun Finder(tracked: Quad?, result: LensResult?, appeared: Float, reduceMotion: Boolean) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1_000_000_000f }
    }
    var size by remember { mutableStateOf(Size.Zero) }
    val density = LocalContext.current.resources.displayMetrics.density
    val home = Quad.home(size, density)
    val target = (tracked ?: home).pts
    // The corners glide to wherever the code is: a spring per coordinate.
    val glide = spring<Float>(dampingRatio = 0.82f, stiffness = if (reduceMotion) Spring.StiffnessHigh else Spring.StiffnessMediumLow)
    val xs = target.mapIndexed { i, p -> animateFloatAsState(p.x, glide, label = "x$i").value }
    val ys = target.mapIndexed { i, p -> animateFloatAsState(p.y, glide, label = "y$i").value }
    val quad = Quad(xs.zip(ys) { x, y -> Offset(x, y) })
    val locked = result is LensResult.Upi
    val wrong = result is LensResult.Other
    val looking = tracked == null && result == null

    Canvas(Modifier.fillMaxSize()) {
        if (size != this.size) size = this.size
        val homeCenter = Offset(this.size.width / 2, this.size.height * CENTER_Y)
        val homeSide = home.pts[1].x - home.pts[0].x
        // The edges of the picture, softly darkened around the frame.
        drawRect(Brush.radialGradient(0.0f to Color.Transparent, 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f),
            center = homeCenter, radius = max(this.size.width, this.size.height) * 0.78f))

        // A soft band of light drifting through the empty frame.
        if (looking && !reduceMotion && homeSide > 0) {
            val frame = Path().apply { addRoundRect(RoundRect(Rect(home.pts[0] + Offset(4f, 4f), home.pts[2] - Offset(4f, 4f)), CornerRadius(30.dp.toPx()))) }
            val band = homeSide * 0.45f
            val y = homeCenter.y + (homeSide / 2 - band / 3) * sin(t * 1.1f)
            clipPath(frame) {
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Aurora[1].copy(alpha = 0.16f), Aurora[0].copy(alpha = 0.22f), Color.Transparent),
                    y - band / 2, y + band / 2), Offset(home.pts[0].x, y - band / 2), Size(homeSide, band))
            }
        }
        // Locked: a soft glow over the code.
        if (locked) drawPath(Path().apply {
            moveTo(quad.pts[0].x, quad.pts[0].y); quad.pts.drop(1).forEach { lineTo(it.x, it.y) }; close()
        }, LockGreen.copy(alpha = 0.10f))

        val breathe = if (looking && !reduceMotion) 1 + 0.018f * sin(t * 2.2f) else 1f
        val c = quad.center
        val pts = quad.pts.map { c + (it - c) * breathe }
        val corners = corners(pts, appeared)
        val brush = when {
            locked -> Brush.linearGradient(listOf(Color.White, LockGreen), pts[0], pts[2])
            wrong -> Brush.linearGradient(listOf(Amber, Amber))
            !looking -> Brush.linearGradient(listOf(Color.White, Color.White))
            else -> {
                // Aurora light, slowly turning.
                val a = t * 0.7f
                val r = homeSide.coerceAtLeast(1f)
                Brush.linearGradient(Aurora, c + Offset(cos(a) * r, sin(a) * r), c - Offset(cos(a) * r, sin(a) * r))
            }
        }
        val glow = when { locked -> LockGreen; wrong -> Amber; !looking -> Color.White; else -> Aurora[1] }
        for ((w, alpha) in listOf(20f to 0.10f, 13f to 0.18f)) drawPath(corners, glow, alpha = alpha, style = Stroke(w.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(corners, brush, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The four corners as rounded L's along the quad's edges; `drawn` 0 → 1 draws them in. */
private fun DrawScope.corners(pts: List<Offset>, drawn: Float): Path = Path().apply {
    for (i in 0 until 4) {
        val corner = pts[i]
        fun along(q: Offset, length: Float): Offset {
            val d = hypot(q.x - corner.x, q.y - corner.y).coerceAtLeast(1f)
            return corner + (q - corner) / d * length
        }
        val prev = pts[(i + 3) % 4]
        val next = pts[(i + 1) % 4]
        val la = min(hypot(prev.x - corner.x, prev.y - corner.y) * 0.26f, 46.dp.toPx()) * drawn.coerceIn(0.01f, 1f)
        val lb = min(hypot(next.x - corner.x, next.y - corner.y) * 0.26f, 46.dp.toPx()) * drawn.coerceIn(0.01f, 1f)
        val r = min(22.dp.toPx(), min(la, lb) * 0.7f)
        val a = along(prev, la)
        val b = along(next, lb)
        val a2 = along(prev, r)
        val b2 = along(next, r)
        moveTo(a.x, a.y)
        lineTo(a2.x, a2.y)
        quadraticTo(corner.x, corner.y, b2.x, b2.y)
        lineTo(b.x, b.y)
    }
}

@Composable
private fun PayeeCard(scan: UpiScan, countdown: Float, onPay: () -> Unit, onAgain: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color(0xE616181D))
        .border(1.dp, GlassEdge, RoundedCornerShape(30.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF2E9E6B), Color(0xFF1F4D9E)))),
                contentAlignment = Alignment.Center) {
                Text(initials(scan.payee.label), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(scan.payee.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Verified, "UPI code", tint = LockGreen, modifier = Modifier.size(18.dp))
                }
                Text(scan.payee.vpa, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            scan.amount?.let {
                Spacer(Modifier.width(8.dp))
                Text(rupees(it), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f)).clickable(onClick = onAgain),
                contentAlignment = Alignment.Center) { Text("Scan again", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
            Row(Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(50)).background(Brush.verticalGradient(listOf(Color(0xFF45EB9A), Color(0xFF26C97A))))
                .clickable(onClick = onPay), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Pay", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.width(10.dp))
                Canvas(Modifier.size(18.dp)) {
                    drawCircle(Color.White.copy(alpha = 0.3f), style = Stroke(2.5.dp.toPx()))
                    drawArc(Color.White, -90f, 360f * countdown, false, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
    }
}

@Composable
private fun NotUpiCard(text: String) {
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color(0xE616181D))
        .border(1.dp, GlassEdge, RoundedCornerShape(30.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Warning, null, tint = Amber, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(if (text.isEmpty()) "No QR code in that photo" else "Not a UPI payment code", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(when {
                text.isEmpty() -> "Try a clearer screenshot."
                text.startsWith("http") -> "It's a website link."
                else -> "Point at a shop's or a person's UPI QR."
            }, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, lit: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(Glass).border(1.dp, GlassEdge, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (lit) Color(0xFFFFD54A) else Color.White, modifier = Modifier.size(22.dp))
    }
}

private fun initials(name: String): String =
    name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "₹" }

/** The lock-on chime, quiet — only when the phone isn't on silent or vibrate. */
private class LockSound(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(1)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val id = pool.load(context, R.raw.portal_lock, 1)

    fun play() {
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        pool.play(id, 0.7f, 0.7f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
