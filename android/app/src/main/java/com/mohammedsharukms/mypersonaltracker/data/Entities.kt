package com.mohammedsharukms.mypersonaltracker.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.Instant
import java.time.LocalDate

/** One row per calendar day. */
@Entity(tableName = "daily_logs", indices = [Index(value = ["date"], unique = true)])
data class DailyLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val whatIDid: String = "",
    val whatILearned: String = "",
    /** 1 (bad) … 5 (great) */
    val mood: Int = 3,
    val showedUp: Boolean = false,
    /** File name of the day's smile check-in photo, in [PhotoStore]. */
    val selfiePath: String? = null,
)

/** Time spent on one thing during a day, e.g. "DSA — 45m". */
@Entity(
    tableName = "study_sessions",
    foreignKeys = [ForeignKey(DailyLog::class, ["id"], ["logId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("logId")],
)
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val logId: Long,
    val title: String,
    val minutes: Int,
    val createdAt: Instant = Instant.now(),
)

data class DayWithSessions(
    @Embedded val log: DailyLog,
    @Relation(parentColumn = "id", entityColumn = "logId")
    val sessions: List<StudySession>,
) {
    val totalMinutes: Int get() = sessions.sumOf { it.minutes }
    val studyHours: Double get() = totalMinutes / 60.0
    val sortedSessions: List<StudySession> get() = sessions.sortedBy { it.createdAt }
}

@Entity(tableName = "todos")
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val isDone: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val completedAt: Instant? = null,
    val photoPath: String? = null,
)

/** A user-defined group of topics, e.g. "iOS", "DSA", "System Design". */
@Entity(tableName = "learning_sections")
data class LearningSection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Instant = Instant.now(),
)

enum class TopicStatus(val title: String, val sortOrder: Int) {
    NOT_STARTED("Not started", 1),
    LEARNING("Learning", 0),
    DONE("Done", 2);

    /** The status a tap on the status icon moves a topic to. */
    val next: TopicStatus
        get() = when (this) {
            NOT_STARTED -> LEARNING
            LEARNING -> DONE
            DONE -> NOT_STARTED
        }
}

@Entity(
    tableName = "learning_topics",
    foreignKeys = [ForeignKey(LearningSection::class, ["id"], ["sectionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sectionId")],
)
data class LearningTopic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sectionId: Long,
    val title: String,
    val status: TopicStatus = TopicStatus.NOT_STARTED,
    val notes: String = "",
    val createdAt: Instant = Instant.now(),
)

data class SectionWithTopics(
    @Embedded val section: LearningSection,
    @Relation(parentColumn = "id", entityColumn = "sectionId")
    val topics: List<LearningTopic>,
) {
    /** Learning first, then not started, then done; oldest first within each. */
    val sortedTopics: List<LearningTopic>
        get() = topics.sortedWith(compareBy({ it.status.sortOrder }, { it.createdAt }))
    val doneCount: Int get() = topics.count { it.status == TopicStatus.DONE }
}

enum class ExpenseCategory(val title: String) {
    FOOD("Food"), TRANSPORT("Transport"), BILLS("Bills"), LEARNING("Learning"), OTHER("Other")
}

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Amount in minor units (paise/cents) to avoid floating-point money. */
    val amountMinor: Long,
    val category: ExpenseCategory,
    val date: Instant = Instant.now(),
    val note: String = "",
    /** File name of the attached bill/receipt image, in [PhotoStore]. */
    val receiptPath: String? = null,
)
