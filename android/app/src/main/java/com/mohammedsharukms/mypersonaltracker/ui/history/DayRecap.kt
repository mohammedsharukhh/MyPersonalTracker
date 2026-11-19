package com.mohammedsharukms.mypersonaltracker.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mohammedsharukms.mypersonaltracker.data.DayWithSessions
import com.mohammedsharukms.mypersonaltracker.data.TodoItem
import com.mohammedsharukms.mypersonaltracker.domain.Mood
import com.mohammedsharukms.mypersonaltracker.domain.durationText
import com.mohammedsharukms.mypersonaltracker.domain.hasContent
import com.mohammedsharukms.mypersonaltracker.domain.wasCompletedOn
import com.mohammedsharukms.mypersonaltracker.ui.components.PhotoThumbnail
import com.mohammedsharukms.mypersonaltracker.ui.components.SelfieAvatar
import com.mohammedsharukms.mypersonaltracker.ui.theme.TrackerColors
import java.time.LocalDate

/** Read-only summary of one day: selfie, time spent, sticky-note to-dos, notes and mood. */
@Composable
fun DayRecap(date: LocalDate, day: DayWithSessions?, todos: List<TodoItem>, emptyMessage: String = "Nothing logged.") {
    if (!hasContent(day, todos)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(emptyMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (day != null) {
            val log = day.log
            Row(verticalAlignment = Alignment.CenterVertically) {
                log.selfiePath?.let {
                    SelfieAvatar(it, size = 36.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (log.showedUp) TrackerColors.Flame else MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.width(6.dp))
                val status = when {
                    log.showedUp -> "Showed up"
                    date == LocalDate.now() -> "Not yet"
                    else -> "Missed"
                }
                Text(status, fontWeight = FontWeight.SemiBold)
                if (day.totalMinutes > 0) {
                    Text(" · ${day.totalMinutes.durationText}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Text(Mood.emoji(log.mood))
            }
            day.sortedSessions.forEach { session ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(8.dp))
                    Text(session.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(session.minutes.durationText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (todos.isNotEmpty()) {
            val done = todos.count { it.wasCompletedOn(date) }
            RecapBlock("To-do · $done/${todos.size} done", Icons.AutoMirrored.Outlined.StickyNote2) {
                todos.forEach { TodoRecapLine(it, date) }
            }
        }
        day?.log?.whatIDid?.takeIf { it.isNotBlank() }?.let {
            RecapBlock("What I did", Icons.Outlined.Build) { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        day?.log?.whatILearned?.takeIf { it.isNotBlank() }?.let {
            RecapBlock("What I learned", Icons.Outlined.Lightbulb) { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun TodoRecapLine(item: TodoItem, day: LocalDate) {
    val done = item.wasCompletedOn(day)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (done) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
            contentDescription = if (done) "Done" else "Not done",
            tint = if (done) TrackerColors.Success else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        if (!done && item.isDone) {
            Spacer(Modifier.width(6.dp))
            Text(
                "done later",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        item.photoPath?.let {
            Spacer(Modifier.weight(1f))
            PhotoThumbnail(it, size = 32.dp)
        }
    }
}

@Composable
private fun RecapBlock(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

