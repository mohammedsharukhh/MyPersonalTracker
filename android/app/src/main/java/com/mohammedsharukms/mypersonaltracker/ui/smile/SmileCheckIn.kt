package com.mohammedsharukms.mypersonaltracker.ui.smile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.mohammedsharukms.mypersonaltracker.container
import com.mohammedsharukms.mypersonaltracker.data.rotated
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.abs

/** What the user should do next to get a good, smiling selfie. */
enum class FaceGuidance(val message: String, val emoji: String) {
    NO_FACE("Look at the camera", "👀"),
    TOO_MANY("Just you, please", "🙋"),
    MOVE_CLOSER("Come a little closer", "🎯"),
    MOVE_BACK("Move back a bit", "🎯"),
    CENTER("Center your face in the oval", "🎯"),
    STRAIGHTEN("Hold your head straight", "📐"),
    OPEN_EYES("Eyes open!", "👁️"),
    SMILE("Now give us a big smile!", "😁"),
    HOLD("Perfect! Hold it…", "📸");

    /** Face is positioned well; only the smile (or holding still) is missing. */
    val isAligned: Boolean get() = this == SMILE || this == HOLD

    companion object {
        /** Same rules as iOS, using ML Kit's smile/eye probabilities and head roll. */
        fun from(faces: List<Face>, imageWidth: Int, imageHeight: Int): FaceGuidance {
            val face = faces.firstOrNull() ?: return NO_FACE
            if (faces.size > 1) return TOO_MANY
            val box = face.boundingBox
            val width = box.width().toFloat() / imageWidth
            if (width < 0.3f) return MOVE_CLOSER
            if (width > 0.8f) return MOVE_BACK
            val cx = box.exactCenterX() / imageWidth
            val cy = box.exactCenterY() / imageHeight
            if (abs(cx - 0.5f) > 0.12f || abs(cy - 0.5f) > 0.14f) return CENTER
            if (abs(face.headEulerAngleZ) > 12f) return STRAIGHTEN
            val left = face.leftEyeOpenProbability
            val right = face.rightEyeOpenProbability
            if (left != null && right != null && left < 0.3f && right < 0.3f) return OPEN_EYES
            if ((face.smilingProbability ?: 0f) < 0.6f) return SMILE
            return HOLD
        }
    }
}

private const val HOLD_MILLIS = 800L

private sealed interface Phase {
    data object Live : Phase
    data object Denied : Phase
    data object Unavailable : Phase
    data class Captured(val bitmap: Bitmap) : Phase
}

/**
 * Daily selfie: guides you to center your face and smile, then captures automatically.
 * [onFinish] receives the saved photo name, or null if skipped.
 */
@Composable
fun SmileCheckInScreen(onFinish: (String?) -> Unit) {
    val context = LocalContext.current
    val hasFrontCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    var captured by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    val photos = context.container.photos

    LaunchedEffect(Unit) { if (hasFrontCamera && !granted) permission.launch(Manifest.permission.CAMERA) }
    BackHandler { onFinish(null) }

    val phase: Phase = when {
        !hasFrontCamera -> Phase.Unavailable
        captured != null -> Phase.Captured(captured!!)
        granted -> Phase.Live
        asked -> Phase.Denied
        else -> Phase.Live
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (phase) {
            Phase.Unavailable -> Message("📷", "Camera not available", "The smile check-in needs a front camera. You can still use the rest of the app.", onContinue = { onFinish(null) })
            Phase.Denied -> Message("🔒", "Camera access is off", "Allow camera access in Settings to take your daily smile.", onContinue = { onFinish(null) }, showSettings = true)
            Phase.Live -> if (granted) LiveCamera(onCaptured = { captured = it }, onSkip = { onFinish(null) })
            is Phase.Captured -> Review(
                phase.bitmap,
                onRetake = { captured = null },
                onSave = { scope.launch { onFinish(photos.save(phase.bitmap, maxDimension = 1200)) } },
            )
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun LiveCamera(onCaptured: (Bitmap) -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    var guidance by remember { mutableStateOf(FaceGuidance.NO_FACE) }
    var progress by remember { mutableFloatStateOf(0f) }
    var capturing by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(progress, label = "hold")

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val detector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build(),
        )
    }
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE or LifecycleCameraController.IMAGE_ANALYSIS)
        }
    }

    fun capture() {
        if (capturing) return
        capturing = true
        controller.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                // Upright and mirrored, so the saved photo matches what you saw.
                val bitmap = image.toBitmap().rotated(image.imageInfo.rotationDegrees.toFloat(), mirror = true)
                image.close()
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onCaptured(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                capturing = false
            }
        })
    }

    DisposableEffect(lifecycleOwner) {
        var readySince = 0L
        var lastAnalysis = 0L
        controller.setImageAnalysisAnalyzer(analysisExecutor) { proxy ->
            val now = System.currentTimeMillis()
            val media = proxy.image
            // ~10 checks per second is plenty and keeps the phone cool.
            if (media == null || now - lastAnalysis < 100 || capturing) {
                proxy.close()
                return@setImageAnalysisAnalyzer
            }
            lastAnalysis = now
            val rotation = proxy.imageInfo.rotationDegrees
            val (w, h) = if (rotation % 180 == 0) proxy.width to proxy.height else proxy.height to proxy.width
            detector.process(InputImage.fromMediaImage(media, rotation))
                .addOnSuccessListener { faces ->
                    val next = FaceGuidance.from(faces, w, h)
                    if (next == FaceGuidance.HOLD) {
                        if (readySince == 0L) readySince = now
                        progress = ((now - readySince).toFloat() / HOLD_MILLIS).coerceAtMost(1f)
                        if (progress >= 1f) capture()
                    } else {
                        readySince = 0L
                        progress = 0f
                    }
                    if (next.isAligned && !guidance.isAligned) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    guidance = next
                }
                .addOnCompleteListener { proxy.close() }
        }
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
            detector.close()
            analysisExecutor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { PreviewView(it).apply { this.controller = controller; scaleType = PreviewView.ScaleType.FILL_CENTER } },
            modifier = Modifier.fillMaxSize(),
        )
        FaceOval(guidance, animatedProgress)
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 24.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Daily smile check-in", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleSmall)
            Text(guidance.emoji, fontSize = 44.sp)
            Text(guidance.message, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("Skip today", color = Color.White) }
            Box(
                Modifier.size(78.dp).border(4.dp, Color.White.copy(alpha = 0.5f), CircleShape).padding(7.dp).clip(CircleShape).background(Color.White)
                    .clickable(onClickLabel = "Take photo") { capture() },
            )
            Text("Auto", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

/** Darkens everything except a face-shaped oval; the outline shows alignment and fills while holding. */
@Composable
private fun FaceOval(guidance: FaceGuidance, progress: Float) {
    Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val width = size.width * 0.72f
        val oval = Size(width, width * 1.32f)
        val topLeft = Offset((size.width - oval.width) / 2, (size.height - oval.height) / 2)
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawOval(Color.Transparent, topLeft, oval, blendMode = BlendMode.Clear)
        drawOval(if (guidance.isAligned) Color(0xFFFACC15) else Color.White.copy(alpha = 0.8f), topLeft, oval, style = Stroke(4.dp.toPx()))
        if (progress > 0f) {
            drawArc(Color(0xFF22C55E), -90f, 360f * progress, false, topLeft, oval, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun Review(bitmap: Bitmap, onRetake: () -> Unit, onSave: () -> Unit) {
    var saving by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Nice smile! 😄", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Image(bitmap.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onRetake, enabled = !saving) { Text("Retake", color = Color.White) }
            Button(onClick = { saving = true; onSave() }, enabled = !saving, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))) {
                if (saving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp) else Text("Save")
            }
        }
    }
}

@Composable
private fun Message(emoji: String, title: String, detail: String, onContinue: () -> Unit, showSettings: Boolean = false) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.size(16.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(12.dp))
        Text(detail, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Spacer(Modifier.size(24.dp))
        if (showSettings) {
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }) { Text("Open Settings") }
            Spacer(Modifier.size(8.dp))
        }
        OutlinedButton(onClick = onContinue) { Text("Continue", color = Color.White) }
    }
}
