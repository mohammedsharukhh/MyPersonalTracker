package com.mohammedsharukms.mypersonaltracker.data

import android.content.Context
import com.mohammedsharukms.mypersonaltracker.domain.toggled
import com.mohammedsharukms.mypersonaltracker.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** All reads as flows and all writes in one place; every write refreshes the home-screen widgets. */
class TrackerRepository(private val context: Context, private val db: TrackerDatabase, val photos: PhotoStore) {
    val days: Flow<List<DayWithSessions>> = db.dailyLogs().observeAll()
    val todos: Flow<List<TodoItem>> = db.todos().observeAll()
    val sessions: Flow<List<StudySession>> = db.sessions().observeAll()
    val sections: Flow<List<SectionWithTopics>> = db.learning().observeSections()
    val expenses: Flow<List<Expense>> = db.expenses().observeAll()

    private suspend fun changed() = WidgetUpdater.updateAll(context)

    // Daily logs

    suspend fun logForDay(date: LocalDate): DailyLog = db.dailyLogs().forDay(date)

    suspend fun updateLog(log: DailyLog) {
        db.dailyLogs().update(log)
        changed()
    }

    suspend fun setSelfie(date: LocalDate, photoName: String) {
        val log = logForDay(date)
        photos.delete(log.selfiePath)
        db.dailyLogs().update(log.copy(selfiePath = photoName))
        changed()
    }

    suspend fun addSession(log: DailyLog, title: String, minutes: Int) {
        db.sessions().insert(StudySession(logId = log.id, title = title, minutes = minutes))
        if (!log.showedUp) db.dailyLogs().update(log.copy(showedUp = true))
        changed()
    }

    suspend fun updateSession(session: StudySession) {
        db.sessions().update(session)
        changed()
    }

    suspend fun deleteSession(session: StudySession) {
        db.sessions().delete(session)
        changed()
    }

    // To-dos

    suspend fun addTodo(title: String, photoName: String?) {
        db.todos().insert(TodoItem(title = title, photoPath = photoName))
        changed()
    }

    suspend fun toggleTodo(todo: TodoItem) {
        db.todos().update(todo.toggled())
        changed()
    }

    suspend fun setTodoPhoto(todo: TodoItem, photoName: String?) {
        photos.delete(todo.photoPath)
        db.todos().update(todo.copy(photoPath = photoName))
        changed()
    }

    suspend fun deleteTodos(todos: List<TodoItem>) {
        todos.forEach { photos.delete(it.photoPath) }
        db.todos().deleteAll(todos)
        changed()
    }

    // Learning

    suspend fun addSection(name: String) = db.learning().insert(LearningSection(name = name))
    suspend fun renameSection(section: LearningSection, name: String) = db.learning().update(section.copy(name = name))
    suspend fun deleteSection(section: LearningSection) = db.learning().delete(section)
    suspend fun addTopic(sectionId: Long, title: String) = db.learning().insert(LearningTopic(sectionId = sectionId, title = title))
    suspend fun updateTopic(topic: LearningTopic) = db.learning().update(topic)
    suspend fun deleteTopic(topic: LearningTopic) = db.learning().delete(topic)

    // Money

    suspend fun saveExpense(expense: Expense, previousReceipt: String?) {
        if (previousReceipt != null && previousReceipt != expense.receiptPath) photos.delete(previousReceipt)
        if (expense.id == 0L) db.expenses().insert(expense) else db.expenses().update(expense)
        changed()
    }

    suspend fun deleteExpense(expense: Expense) {
        photos.delete(expense.receiptPath)
        db.expenses().delete(expense)
        changed()
    }
}
