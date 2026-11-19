package com.mohammedsharukms.mypersonaltracker.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohammedsharukms.mypersonaltracker.data.DayWithSessions
import com.mohammedsharukms.mypersonaltracker.data.StudySession
import com.mohammedsharukms.mypersonaltracker.domain.groupedByDay
import com.mohammedsharukms.mypersonaltracker.ui.TrackerViewModel
import com.mohammedsharukms.mypersonaltracker.ui.components.PhotoViewer
import com.mohammedsharukms.mypersonaltracker.ui.components.SectionCard
import com.mohammedsharukms.mypersonaltracker.ui.components.SelfieAvatar
import com.mohammedsharukms.mypersonaltracker.ui.history.DayRecap
import java.time.format.DateTimeFormatter

private val shortDate = DateTimeFormatter.ofPattern("EEE, d MMM")
val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(vm: TrackerViewModel, onOpenSettings: () -> Unit, onTakeSmile: () -> Unit, onOpenDay: (DayWithSessions) -> Unit) {
    val today by vm.today.collectAsStateWithLifecycle()
    val days by vm.days.collectAsStateWithLifecycle()
    val todos by vm.todos.collectAsStateWithLifecycle()
    val visibleTodos by vm.visibleTodos.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val running by vm.focusTimer.state.collectAsStateWithLifecycle()

    val todayDay = days.firstOrNull { it.log.date == today }
    val yesterday = today.minusDays(1)
    val yesterdayDay = days.firstOrNull { it.log.date == yesterday }
    var viewingSelfie by remember { mutableStateOf(false) }
    var selfieMenu by remember { mutableStateOf(false) }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scroll,
                title = { Text("Today") },
                navigationIcon = { IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings") } },
                actions = {
                    Box {
                        IconButton(onClick = { selfieMenu = true }) {
                            val selfie = todayDay?.log?.selfiePath
                            if (selfie != null) SelfieAvatar(selfie, size = 34.dp) else Icon(Icons.Outlined.Face, "Today's smile")
                        }
                        DropdownMenu(expanded = selfieMenu, onDismissRequest = { selfieMenu = false }) {
                            if (todayDay?.log?.selfiePath != null) {
                                DropdownMenuItem(text = { Text("View Today's Smile") }, leadingIcon = { Icon(Icons.Outlined.Photo, null) }, onClick = { selfieMenu = false; viewingSelfie = true })
                                DropdownMenuItem(text = { Text("Retake") }, leadingIcon = { Icon(Icons.Outlined.CameraAlt, null) }, onClick = { selfieMenu = false; onTakeSmile() })
                            } else {
                                DropdownMenuItem(text = { Text("Take Today's Smile") }, leadingIcon = { Icon(Icons.Outlined.CameraAlt, null) }, onClick = { selfieMenu = false; onTakeSmile() })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (todayDay == null) return@Scaffold
        LogList(
            day = todayDay,
            vm = vm,
            suggestions = suggestions,
            contentPadding = padding,
            header = {
                item(key = "note") {
                    TodoStickyNote(
                        todos = visibleTodos,
                        onAdd = vm::addTodo,
                        onToggle = vm::toggleTodo,
                        onSetPhoto = vm::setTodoPhoto,
                        onDelete = vm::deleteTodos,
                    )
                }
                item(key = "focus") {
                    Column {
                        FocusCard(
                            running = running,
                            suggestions = suggestions,
                            onStart = vm::startFocus,
                            onStop = { onLogged -> vm.stopFocus { onLogged(loggedLabel(it.minutes, it.title)) } },
                            onCancel = vm::cancelFocus,
                        )
                    }
                }
            },
            footer = {
                item(key = "yesterday") {
                    Column {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text("Yesterday", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(yesterday.format(shortDate), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SectionCard {
                            Column {
                                DayRecap(yesterday, yesterdayDay, todos.groupedByDay()[yesterday].orEmpty(), "Nothing logged yesterday. Today's a fresh start.")
                                if (yesterdayDay != null) TextButton(onClick = { onOpenDay(yesterdayDay) }) { Text("Edit yesterday") }
                            }
                        }
                        Text(
                            "See every day in the History tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 6.dp),
                        )
                    }
                }
            },
        )
    }

    if (viewingSelfie) todayDay?.log?.selfiePath?.let { PhotoViewer(it, "Today's smile") { viewingSelfie = false } }
}

/** The editable log list, with optional extra items above and below (used by Today and past days). */
@Composable
fun LogList(
    day: DayWithSessions,
    vm: TrackerViewModel,
    suggestions: List<String>,
    contentPadding: PaddingValues,
    header: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {},
    footer: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {},
) {
    var addingTime by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StudySession?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        header()
        dailyLogEditor(day, vm, onAddTime = { addingTime = true }, onEditSession = { editing = it })
        footer()
    }

    if (addingTime) {
        SessionEditorSheet(null, suggestions, onSave = { t, m -> vm.addSession(day.log, t, m) }, onDelete = null, onDismiss = { addingTime = false })
    }
    editing?.let { session ->
        SessionEditorSheet(
            session,
            suggestions,
            onSave = { t, m -> vm.updateSession(session.copy(title = t, minutes = m)) },
            onDelete = { vm.deleteSession(session) },
            onDismiss = { editing = null },
        )
    }
}

/** Full editable log for a past day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastDayScreen(day: DayWithSessions, vm: TrackerViewModel, onBack: () -> Unit) {
    val days by vm.days.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val current = days.firstOrNull { it.log.id == day.log.id } ?: day
    androidx.activity.compose.BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.log.date.format(longDate)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding -> LogList(current, vm, suggestions, padding) }
}
