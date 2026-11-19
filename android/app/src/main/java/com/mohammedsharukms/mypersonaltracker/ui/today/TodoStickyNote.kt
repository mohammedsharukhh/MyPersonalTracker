package com.mohammedsharukms.mypersonaltracker.ui.today

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import com.mohammedsharukms.mypersonaltracker.data.TodoItem
import com.mohammedsharukms.mypersonaltracker.ui.components.FoldedCornerShape
import com.mohammedsharukms.mypersonaltracker.ui.components.PhotoViewer
import com.mohammedsharukms.mypersonaltracker.ui.components.PolaroidPhoto
import com.mohammedsharukms.mypersonaltracker.ui.components.rememberPhotoCapture
import com.mohammedsharukms.mypersonaltracker.ui.theme.Handwriting
import com.mohammedsharukms.mypersonaltracker.ui.theme.TrackerColors

private val Ink = TrackerColors.Ink
private val Fold = 22.dp

/**
 * A paper sticky note with today's to-dos. Type a line and press return to add it; the camera button
 * attaches a photo to the next to-do, or long-press a to-do to attach one later.
 */
@Composable
fun TodoStickyNote(
    todos: List<TodoItem>,
    onAdd: (title: String, photo: String?) -> Unit,
    onToggle: (TodoItem) -> Unit,
    onSetPhoto: (TodoItem, String?) -> Unit,
    onDelete: (List<TodoItem>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var stagedPhoto by remember { mutableStateOf<String?>(null) }
    var photoTarget by remember { mutableStateOf<TodoItem?>(null) }
    var viewing by remember { mutableStateOf<TodoItem?>(null) }
    var attachMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val doneCount = todos.count { it.isDone }

    val capture = rememberPhotoCapture { name ->
        val target = photoTarget
        if (target != null) onSetPhoto(target, name) else stagedPhoto = name
        photoTarget = null
    }

    fun add() {
        val title = text.trim().ifEmpty { if (stagedPhoto != null) "Photo" else "" }
        if (title.isEmpty()) return
        onAdd(title, stagedPhoto)
        text = ""
        stagedPhoto = null
        focus.requestFocus()
    }

    val handwriting = TextStyle(fontFamily = Handwriting, fontSize = 22.sp, color = Ink)

    Box(modifier.padding(horizontal = 4.dp, vertical = 14.dp)) {
        Column(
            Modifier
                .rotate(-1f)
                .fillMaxWidth()
                .shadow(6.dp, FoldedCornerShape(Fold))
                .background(
                    Brush.verticalGradient(listOf(TrackerColors.Paper, TrackerColors.Paper.copy(red = 0.97f, green = 0.88f))),
                    FoldedCornerShape(Fold),
                )
                .drawBehind {
                    // The curled-up flap filling the cut corner.
                    val f = Fold.toPx()
                    drawPath(
                        Path().apply {
                            moveTo(size.width, size.height - f)
                            lineTo(size.width - f, size.height - f)
                            lineTo(size.width - f, size.height)
                            close()
                        },
                        TrackerColors.PaperFold,
                        style = Fill,
                    )
                }
                .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 20.dp + Fold / 2)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("To-do", style = handwriting.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold))
                if (todos.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text("$doneCount/${todos.size}", style = handwriting.copy(fontSize = 20.sp, color = Ink.copy(alpha = 0.7f)))
                }
                Spacer(Modifier.weight(1f))
                if (doneCount > 0) {
                    TextButton(onClick = { onDelete(todos.filter { it.isDone }) }) {
                        Text("Clear done", color = Ink.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            todos.forEach { item ->
                TodoLine(
                    item = item,
                    style = handwriting,
                    onToggle = { onToggle(item) },
                    onShowPhoto = { viewing = item },
                    onTakePhoto = { photoTarget = item; capture.takePhoto() },
                    onPickPhoto = { photoTarget = item; capture.pickPhoto() },
                    onRemovePhoto = { onSetPhoto(item, null) },
                    onDelete = { onDelete(listOf(item)) },
                )
            }

            stagedPhoto?.let { photo ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 34.dp)) {
                    PolaroidPhoto(photo, width = 56.dp, tilt = -3f)
                    Spacer(Modifier.width(10.dp))
                    Text("Photo will be attached to your next to-do", fontSize = 12.sp, color = Ink.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                    IconButton(onClick = { stagedPhoto = null }) { Icon(Icons.Default.Close, "Remove photo", tint = Ink) }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text("Type a to-do and press return", style = handwriting.copy(color = Ink.copy(alpha = 0.45f)))
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = handwriting,
                        cursorBrush = SolidColor(Ink),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                Box {
                    IconButton(onClick = { attachMenu = true }) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = "Attach photo", tint = Ink)
                    }
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Take Photo") },
                            leadingIcon = { Icon(Icons.Outlined.CameraAlt, null) },
                            onClick = { attachMenu = false; photoTarget = null; capture.takePhoto() },
                        )
                        DropdownMenuItem(
                            text = { Text("Choose from Library") },
                            leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                            onClick = { attachMenu = false; photoTarget = null; capture.pickPhoto() },
                        )
                    }
                }
            }
        }
        // A strip of tape holding the note down.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-10).dp)
                .rotate(-4f)
                .size(width = 90.dp, height = 24.dp)
                .background(Color.White.copy(alpha = 0.55f)),
        )
    }

    viewing?.photoPath?.let { PhotoViewer(it, title = viewing?.title.orEmpty(), onDismiss = { viewing = null }) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoLine(
    item: TodoItem,
    style: TextStyle,
    onToggle: () -> Unit,
    onShowPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = {
                    if (!item.isDone) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onToggle()
                },
                onLongClick = { menu = true },
            ),
        ) {
            Icon(
                if (item.isDone) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                contentDescription = if (item.isDone) "Done" else "Not done",
                tint = Ink,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                item.title,
                style = style,
                textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                color = Ink.copy(alpha = if (item.isDone) 0.5f else 1f),
                modifier = Modifier.weight(1f),
            )
            item.photoPath?.let {
                // Alternate the tilt so a stack of prints looks hand-placed.
                val tilt = listOf(4f, -3f, 2f)[(item.createdAt.toEpochMilli() % 3).toInt()]
                Box(Modifier.combinedClickable(onClick = onShowPhoto)) {
                    PolaroidPhoto(it, width = 52.dp, tilt = tilt, modifier = Modifier.padding(4.dp))
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val hasPhoto = item.photoPath != null
            DropdownMenuItem(text = { Text(if (hasPhoto) "Retake Photo" else "Take Photo") }, leadingIcon = { Icon(Icons.Outlined.CameraAlt, null) }, onClick = { menu = false; onTakePhoto() })
            DropdownMenuItem(text = { Text(if (hasPhoto) "Replace Photo" else "Attach Photo") }, leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) }, onClick = { menu = false; onPickPhoto() })
            if (hasPhoto) {
                DropdownMenuItem(text = { Text("Remove Photo") }, leadingIcon = { Icon(Icons.Outlined.HideImage, null) }, onClick = { menu = false; onRemovePhoto() })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
        }
    }
}
