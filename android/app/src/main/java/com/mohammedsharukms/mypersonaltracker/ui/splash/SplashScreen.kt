package com.mohammedsharukms.mypersonaltracker.ui.splash

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// The opening animation: the app icon draws itself — the ring sweeps round, the
// three bars pop up with a rising chime, the name settles in — then it opens into
// the app. About two seconds; a tap skips it. The chime only plays when the phone's
// ringer is on, and can be turned off in Settings. Mirrors SplashView.swift.

object SplashPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun soundOn(context: Context) = prefs(context).getBoolean("splashSoundEnabled", true)
    fun setSoundOn(context: Context, on: Boolean) = prefs(context).edit().putBoolean("splashSoundEnabled", on).apply()
}

private val Green = Color(0xFF4ADE80)
private val Teal = Color(0xFF2DD4BF)
private val Lime = Color(0xFF9BC95E)
private val Amber = Color(0xFFFBBF24)
val SplashNavy = Color(0xFF254073)

/**
 * @param appReady whether the app behind is built yet; the splash leaves only once it is.
 * @param onReadyForApp called once the animation has played, to build the app behind it.
 */
@Composable
fun SplashScreen(appReady: Boolean, onReadyForApp: () -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    val ring = remember { Animatable(0f) }
    val bars = remember { List(3) { Animatable(0f) } }
    val title = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    var skipped by remember { mutableStateOf(false) }
    val ready by rememberUpdatedState(appReady)
    val noMotion = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }

    val player = remember { if (SplashPrefs.soundOn(context)) chime(context) else null }
    DisposableEffect(Unit) {
        player?.start()
        // Let the chime ring out under the app before it's released.
        onDispose { player?.let { p -> p.setOnCompletionListener { it.release() }; if (!p.isPlaying) p.release() } }
    }

    LaunchedEffect(Unit) {
        if (noMotion) {
            ring.snapTo(1f); bars.forEach { it.snapTo(1f) }; title.snapTo(1f)
            delay(1100)
        } else {
            launch { ring.animateTo(1f, tween(750, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))) }
            // Bars land with the three rising notes (0.50 / 0.62 / 0.74 s in the chime).
            for (i in 0..2) {
                delay(if (i == 0) 480 else 120)
                launch { bars[i].animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
            }
            delay(200)
            launch { title.animateTo(1f, tween(500)) }
            // Wait out the rest unless tapped away.
            val until = System.currentTimeMillis() + 700
            while (!skipped && System.currentTimeMillis() < until) delay(16)
        }
        onReadyForApp()
        snapshotFlow { ready }.first { it }
        leave.animateTo(1f, tween(350, easing = FastOutLinearInEasing))
        onFinish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - leave.value; scaleX = 1f + 0.12f * leave.value; scaleY = scaleX }
            .background(Brush.linearGradient(listOf(Color(0xFF2F5597), Color(0xFF1A2A4E))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { skipped = true }
            .semantics { contentDescription = "My Personal Tracker" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width / 2, size.height * 0.42f), radius = size.width * 0.8f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(34.dp),
            modifier = Modifier.offset(y = (-20).dp)) {
            Mark(ring.value, bars.map { it.value }, glow = title.value, modifier = Modifier.size(190.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = title.value; translationY = (1 - title.value) * 14.dp.toPx() }) {
                Text("My Personal Tracker", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Habits · Money · Study · Food", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** The icon, drawn live: track, gradient ring from the amber end round to the green, and the bars. */
@Composable
private fun Mark(ring: Float, bars: List<Float>, glow: Float, modifier: Modifier) {
    Canvas(modifier) {
        val side = size.minDimension
        val line = side * 0.145f
        val s = (side - line) / 636f            // icon units → pixels (the ring's centre line is 636 across)
        val inset = line / 2
        val arcSize = Size(side - line, side - line)
        val topLeft = Offset(inset, inset)
        if (glow > 0f) drawCircle(Brush.radialGradient(listOf(Green.copy(alpha = 0.28f * glow), Color.Transparent), radius = side * 0.75f), radius = side * 0.75f)
        drawCircle(Color.White.copy(alpha = 0.18f), radius = (side - line) / 2, style = Stroke(line))
        // Sweep angles run clockwise from 3 o'clock: teal at 3, lime at 6, amber at 9, green at 12.
        val sweep = Brush.sweepGradient(
            0.0f to Teal, 0.25f to Lime, 0.5f to Amber, 0.53f to Amber, 0.72f to Green, 0.75f to Green, 1.0f to Teal,
            center = Offset(side / 2, side / 2),
        )
        // Draws from the amber end (9 o'clock) back round, anticlockwise, to the green at the top.
        drawArc(sweep, startAngle = 180f, sweepAngle = -270f * ring, useCenter = false, topLeft = topLeft, size = arcSize,
            style = Stroke(line, cap = StrokeCap.Round))
        val heights = listOf(150f, 230f, 310f)
        val bottom = side / 2 + 150f * s
        heights.forEachIndexed { i, h ->
            val p = bars[i]
            if (p <= 0f) return@forEachIndexed
            val height = h * s * p.coerceAtLeast(0.02f)
            val x = side / 2 + (i - 1) * 104f * s - 35f * s
            drawRoundRect(Color.White.copy(alpha = p.coerceIn(0f, 1f)), topLeft = Offset(x, bottom - height),
                size = Size(70f * s, height), cornerRadius = CornerRadius(30f * s * minOf(1f, p)))
        }
    }
}

/** The chime, or null when the ringer is silent or vibrate. */
private fun chime(context: Context): MediaPlayer? {
    val audio = context.getSystemService(AudioManager::class.java)
    if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return null
    return runCatching {
        MediaPlayer.create(context, R.raw.splash_chime,
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(), 0)?.apply { setVolume(0.8f, 0.8f) }
    }.getOrNull()
}
