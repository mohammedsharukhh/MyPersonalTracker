package com.mohammedsharukms.mypersonaltracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mohammedsharukms.mypersonaltracker.AppContainer
import com.mohammedsharukms.mypersonaltracker.data.DailyLog
import com.mohammedsharukms.mypersonaltracker.data.DayWithSessions
import com.mohammedsharukms.mypersonaltracker.data.Expense
import com.mohammedsharukms.mypersonaltracker.data.LearningSection
import com.mohammedsharukms.mypersonaltracker.data.LearningTopic
import com.mohammedsharukms.mypersonaltracker.data.SectionWithTopics
import com.mohammedsharukms.mypersonaltracker.data.StudySession
import com.mohammedsharukms.mypersonaltracker.data.TodoItem
import com.mohammedsharukms.mypersonaltracker.domain.ActivitySuggestions
import com.mohammedsharukms.mypersonaltracker.domain.isVisibleOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Activity-scoped state shared by all tabs: live data from Room plus the actions screens can take.
 * Derived numbers (streaks, summaries, grouping) come from the pure functions in `domain/`.
 */
class TrackerViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    val photos = container.photos
    val focusTimer = container.focusTimer
    val settings = container.settings

    private val _today = MutableStateFlow(LocalDate.now())
    /** Rolls over when the app returns to the foreground after midnight. */
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    val days: StateFlow<List<DayWithSessions>> = repo.days.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val todos: StateFlow<List<TodoItem>> = repo.todos.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sections: StateFlow<List<SectionWithTopics>> = repo.sections.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val expenses: StateFlow<List<Expense>> = repo.expenses.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Open to-dos first, then the ones ticked today. */
    val visibleTodos: StateFlow<List<TodoItem>> = combine(todos, today) { all, day ->
        val visible = all.filter { it.isVisibleOn(day) }
        visible.filterNot { it.isDone } + visible.filter { it.isDone }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val suggestions: StateFlow<List<String>> = combine(repo.sessions, sections) { sessions, sections ->
        ActivitySuggestions.make(sessions, sections)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun refreshToday() {
        _today.value = LocalDate.now()
        viewModelScope.launch { repo.logForDay(_today.value) }
    }

    fun dayOf(date: LocalDate): DayWithSessions? = days.value.firstOrNull { it.log.date == date }

    // Logs
    fun updateLog(log: DailyLog) = launch { repo.updateLog(log) }
    fun addSession(log: DailyLog, title: String, minutes: Int) = launch { repo.addSession(log, title, minutes) }
    fun updateSession(session: StudySession) = launch { repo.updateSession(session) }
    fun deleteSession(session: StudySession) = launch { repo.deleteSession(session) }
    fun saveSelfie(photoName: String) = launch { repo.setSelfie(LocalDate.now(), photoName) }

    // To-dos
    fun addTodo(title: String, photoName: String?) = launch { repo.addTodo(title, photoName) }
    fun toggleTodo(todo: TodoItem) = launch { repo.toggleTodo(todo) }
    fun setTodoPhoto(todo: TodoItem, photoName: String?) = launch { repo.setTodoPhoto(todo, photoName) }
    fun deleteTodos(todos: List<TodoItem>) = launch { repo.deleteTodos(todos) }

    // Learning
    fun addSection(name: String) = launch { repo.addSection(name) }
    fun renameSection(section: LearningSection, name: String) = launch { repo.renameSection(section, name) }
    fun deleteSection(section: LearningSection) = launch { repo.deleteSection(section) }
    fun addTopic(sectionId: Long, title: String) = launch { repo.addTopic(sectionId, title) }
    fun updateTopic(topic: LearningTopic) = launch { repo.updateTopic(topic) }
    fun deleteTopic(topic: LearningTopic) = launch { repo.deleteTopic(topic) }

    // Money
    fun saveExpense(expense: Expense, previousReceipt: String?) = launch { repo.saveExpense(expense, previousReceipt) }
    fun deleteExpense(expense: Expense) = launch { repo.deleteExpense(expense) }

    // Focus timer
    fun startFocus(activity: String) = focusTimer.start(activity)
    fun cancelFocus() = focusTimer.cancel()
    fun stopFocus(onLogged: (StudySession) -> Unit) = launch { focusTimer.stop()?.let(onLogged) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
