package com.mohammedsharukms.mypersonaltracker

import com.mohammedsharukms.mypersonaltracker.data.DailyLog
import com.mohammedsharukms.mypersonaltracker.data.Expense
import com.mohammedsharukms.mypersonaltracker.data.ExpenseCategory
import com.mohammedsharukms.mypersonaltracker.data.LearningSection
import com.mohammedsharukms.mypersonaltracker.data.LearningTopic
import com.mohammedsharukms.mypersonaltracker.data.StudySession
import com.mohammedsharukms.mypersonaltracker.data.TodoItem
import com.mohammedsharukms.mypersonaltracker.data.TopicStatus
import com.mohammedsharukms.mypersonaltracker.data.TrackerDatabase
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug builds only: launch with `adb shell am start -n <pkg>/.MainActivity --ez seedSampleData true`
 * to fill an empty database with a few weeks of demo data.
 */
object SampleData {
    suspend fun seedIfEmpty(db: TrackerDatabase) {
        if (!BuildConfig.DEBUG || db.dailyLogs().getAll().any { it.sessions.isNotEmpty() }) return
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val activities = listOf("DSA", "Compose", "Kotlin Coroutines", "System Design", "Reading")

        for (offset in 1..40) {
            if (offset % 6 == 0) continue
            val day = today.minusDays(offset.toLong())
            val logId = db.dailyLogs().insert(DailyLog(date = day, mood = 2 + offset % 4, showedUp = true))
            activities.forEachIndexed { i, title ->
                if ((offset + i) % 3 == 0 || i == 0) {
                    val at = day.atTime(9 + i, 0).atZone(zone).toInstant()
                    db.sessions().insert(StudySession(logId = logId, title = title, minutes = 15 * (1 + (offset + i) % 6), createdAt = at))
                }
            }
            if (offset == 1) {
                val log = db.dailyLogs().getByDate(day)!!
                db.dailyLogs().update(
                    log.copy(
                        whatIDid = "Solved 3 binary tree problems and refactored the Money tab.",
                        whatILearned = "Coroutines are cancelled cooperatively; suspend points check for it.",
                    ),
                )
                db.todos().insert(
                    TodoItem(
                        title = "Revise BFS vs DFS",
                        isDone = true,
                        createdAt = day.atTime(9, 0).atZone(zone).toInstant(),
                        completedAt = day.atTime(18, 0).atZone(zone).toInstant(),
                    ),
                )
                db.todos().insert(TodoItem(title = "Watch Now in Android episode", createdAt = day.atTime(10, 0).atZone(zone).toInstant()))
            }
        }

        val android = db.learning().insert(LearningSection(name = "Android"))
        val dsa = db.learning().insert(LearningSection(name = "DSA"))
        listOf(
            Triple("Kotlin coroutines", TopicStatus.LEARNING, android),
            Triple("Room", TopicStatus.DONE, android),
            Triple("Baseline profiles", TopicStatus.NOT_STARTED, android),
            Triple("Trees", TopicStatus.DONE, dsa),
            Triple("Graphs", TopicStatus.LEARNING, dsa),
            Triple("Dynamic programming", TopicStatus.NOT_STARTED, dsa),
        ).forEach { (title, status, section) -> db.learning().insert(LearningTopic(sectionId = section, title = title, status = status)) }

        for (offset in 0 until 30) {
            val category = ExpenseCategory.entries[offset % ExpenseCategory.entries.size]
            val date = today.minusDays(offset.toLong()).atTime(13, 0).atZone(zone).toInstant()
            db.expenses().insert(Expense(amountMinor = (80L + (offset * 37) % 400) * 100, category = category, date = date))
        }
    }
}
