package com.mohammedsharukms.mypersonaltracker.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohammedsharukms.mypersonaltracker.data.DayWithSessions
import com.mohammedsharukms.mypersonaltracker.data.TodoItem
import com.mohammedsharukms.mypersonaltracker.domain.durationText
import com.mohammedsharukms.mypersonaltracker.domain.groupedByDay
import com.mohammedsharukms.mypersonaltracker.domain.hasContent
import com.mohammedsharukms.mypersonaltracker.ui.TrackerViewModel
import com.mohammedsharukms.mypersonaltracker.ui.components.SectionCard
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private data class HistoryDay(val day: DayWithSessions, val todos: List<TodoItem>)

/** Every logged day in one place, newest first, grouped by month and searchable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: TrackerViewModel, onOpenDay: (DayWithSessions) -> Unit) {
    val days by vm.days.collectAsStateWithLifecycle()
    val todos by vm.todos.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    val months = remember(days, todos, query) {
        val todosByDay = todos.groupedByDay()
        days.map { HistoryDay(it, todosByDay[it.log.date].orEmpty()) }
            .filter { hasContent(it.day, it.todos) }
            .filter { query.isBlank() || matches(it, query.trim()) }
            .groupBy { YearMonth.from(it.day.log.date) }
            .toSortedMap(compareByDescending { it })
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("History") }, scrollBehavior = scroll) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search activities, notes, to-dos") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (months.isEmpty()) {
                item {
                    Column(Modifier.fillMaxSize().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (query.isBlank()) "No logs yet" else "No results for “$query”", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                        if (query.isBlank()) {
                            Text("Days you log on the Today tab show up here.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            months.forEach { (month, monthDays) ->
                item(key = month.toString()) {
                    Column {
                        Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) {
                            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            val minutes = monthDays.sumOf { it.day.totalMinutes }
                            val shownUp = monthDays.count { it.day.log.showedUp }
                            Text("$shownUp days · ${minutes.durationText}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SectionCard {
                            Column {
                                monthDays.sortedByDescending { it.day.log.date }.forEachIndexed { index, entry ->
                                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                                    Column(Modifier.fillMaxWidth().clickable { onOpenDay(entry.day) }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        DayTitle(entry.day.log.date)
                                        DayRecap(entry.day.log.date, entry.day, entry.todos)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTitle(date: LocalDate) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE"))
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(date.format(DateTimeFormatter.ofPattern("d MMM")), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun matches(entry: HistoryDay, query: String): Boolean {
    val log = entry.day.log
    val fields = listOf(log.whatIDid, log.whatILearned) + entry.day.sessions.map { it.title } + entry.todos.map { it.title }
    return fields.any { it.contains(query, ignoreCase = true) }
}

