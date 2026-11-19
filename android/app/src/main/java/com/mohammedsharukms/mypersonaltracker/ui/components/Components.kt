package com.mohammedsharukms.mypersonaltracker.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mohammedsharukms.mypersonaltracker.container
import com.mohammedsharukms.mypersonaltracker.ui.theme.TrackerColors

/** Loads a stored photo off the main thread, downscaled for display. */
@Composable
fun rememberPhoto(name: String?, maxDimension: Int): Bitmap? {
    val photos = LocalContext.current.container.photos
    val bitmap by produceState<Bitmap?>(null, name, maxDimension) {
        value = name?.let { photos.loadThumbnail(it, maxDimension) }
    }
    return bitmap
}

@Composable
fun PhotoImage(name: String, modifier: Modifier = Modifier, maxDimension: Int = 300, contentScale: ContentScale = ContentScale.Crop) {
    val bitmap = rememberPhoto(name, maxDimension)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PhotoThumbnail(name: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    PhotoImage(name, modifier.size(size).clip(RoundedCornerShape(6.dp)))
}

/** A round selfie thumbnail. */
@Composable
fun SelfieAvatar(name: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    PhotoImage(
        name,
        modifier.size(size).shadow(2.dp, CircleShape).clip(CircleShape).border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape),
    )
}

/** A Polaroid-style print: photo in a white frame with a deeper bottom edge, slightly tilted. */
@Composable
fun PolaroidPhoto(name: String, width: Dp = 56.dp, tilt: Float = 4f, modifier: Modifier = Modifier) {
    val border = maxOf(3.dp, width * 0.07f)
    Box(
        modifier
            .rotate(tilt)
            .shadow(3.dp, RoundedCornerShape(2.dp))
            .background(Color(0xFFFAFAFA), RoundedCornerShape(2.dp))
            .padding(start = border, end = border, top = border, bottom = border * 3.5f),
    ) {
        PhotoImage(name, Modifier.size(width - border * 2))
    }
}

/** Full-screen photo with pinch-to-zoom and double-tap. */
@Composable
fun PhotoViewer(name: String, title: String = "", onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PhotoImage(
                name,
                maxDimension = 2400,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offset = Offset.Zero
                        })
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
            Row(Modifier.padding(top = 36.dp, start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** A horizontally scrolling row of tappable chips. */
@Composable
fun ChipRow(items: List<String>, isSelected: (String) -> Boolean, onTap: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            FilterChip(selected = isSelected(item), onClick = { onTap(item) }, label = { Text(item) })
        }
    }
}

/** Note outline with the bottom-right corner cut off for the fold. */
class FoldedCornerShape(private val fold: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val f = with(density) { fold.toPx() }
        return Outline.Generic(
            Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height - f)
                lineTo(size.width - f, size.height)
                lineTo(0f, size.height)
                close()
            },
        )
    }
}

/** GitHub-style contribution grid: one column per week (oldest left), one row per weekday. */
@Composable
fun Heatmap(levelsOldestFirst: List<Int>, modifier: Modifier = Modifier, cell: Dp = 14.dp, gap: Dp = 3.dp) {
    val weeks = levelsOldestFirst.chunked(7)
    Column(modifier) {
        Row(Modifier.horizontalScroll(rememberScrollState(Int.MAX_VALUE)), horizontalArrangement = Arrangement.spacedBy(gap)) {
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    week.forEach { level ->
                        Box(Modifier.size(cell).clip(RoundedCornerShape(3.dp)).background(heatColor(level)))
                    }
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Spacer(Modifier.weight(1f))
            Text("Less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (0..4).forEach { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(heatColor(it))) }
            Text("More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Level -1 means a future day (drawn empty). */
fun heatColor(level: Int): Color = when {
    level < 0 -> Color.Transparent
    level == 0 -> TrackerColors.HeatEmpty
    else -> TrackerColors.Heat.copy(alpha = 0.25f * level)
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) { content() }
}
