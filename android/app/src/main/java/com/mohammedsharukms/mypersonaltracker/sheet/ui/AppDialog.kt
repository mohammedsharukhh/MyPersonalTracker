package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// The app's own pop-up, used for every confirmation and message instead of the
// system AlertDialog: an icon, a title, a line or two of explanation, then the
// buttons — the main one red when it deletes something. A tap outside cancels.
// Mirrors Popup.swift.

enum class DialogStyle { DESTRUCTIVE, STANDARD, INFO }

@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "OK",
    message: String? = null,
    icon: ImageVector? = null,
    style: DialogStyle = DialogStyle.STANDARD,
    dismissText: String? = "Cancel",
    confirmEnabled: Boolean = true,
    /** The second button only (a tap outside still just closes). */
    onDismissButton: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val pop = remember { Animatable(0.88f) }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(if (style == DialogStyle.DESTRUCTIVE) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        pop.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
    }
    val tint = when (style) {
        DialogStyle.DESTRUCTIVE -> SheetTheme.bad
        DialogStyle.INFO -> SheetTheme.warn
        DialogStyle.STANDARD -> SheetTheme.navy
    }
    val shownIcon = icon ?: when (style) {
        DialogStyle.DESTRUCTIVE -> Icons.Filled.DeleteOutline
        DialogStyle.INFO -> Icons.Filled.WarningAmber
        DialogStyle.STANDARD -> null
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - 0.88f) / 0.12f).coerceIn(0f, 1f) }
                .shadow(24.dp, RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
                .background(SheetTheme.card)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            shownIcon?.let {
                Box(Modifier.size(56.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(it, null, tint = tint, modifier = Modifier.size(26.dp))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text, textAlign = TextAlign.Center)
                if (!message.isNullOrBlank()) Text(message, fontSize = 15.sp, color = SheetTheme.secondary, textAlign = TextAlign.Center)
            }
            content?.let { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), content = it) }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val main = if (style == DialogStyle.DESTRUCTIVE) Color(0xFFDC2626) else Color(0xFF2B4A8C)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (confirmEnabled) main else main.copy(alpha = 0.4f))
                        .clickable(enabled = confirmEnabled) { onConfirm() }.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(confirmText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
                dismissText?.let {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SheetTheme.input)
                            .clickable { (onDismissButton ?: onDismiss)() }.padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(it, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text) }
                }
            }
        }
    }
}
