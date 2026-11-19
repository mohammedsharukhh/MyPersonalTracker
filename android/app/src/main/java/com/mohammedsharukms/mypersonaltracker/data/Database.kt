package com.mohammedsharukms.mypersonaltracker.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter fun localDateToLong(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun longToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}

@Dao
interface DailyLogDao {
    @Transaction
    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    fun observeAll(): Flow<List<DayWithSessions>>

    @Transaction
    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    suspend fun getAll(): List<DayWithSessions>

    @Query("SELECT * FROM daily_logs WHERE date = :date")
    suspend fun getByDate(date: LocalDate): DailyLog?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: DailyLog): Long

    @Update suspend fun update(log: DailyLog)

    /** The log for [date], created if it doesn't exist yet. */
    @Transaction
    suspend fun forDay(date: LocalDate): DailyLog {
        getByDate(date)?.let { return it }
        insert(DailyLog(date = date))
        return checkNotNull(getByDate(date))
    }
}

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<StudySession>>

    @Insert suspend fun insert(session: StudySession): Long
    @Update suspend fun update(session: StudySession)
    @Delete suspend fun delete(session: StudySession)
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY createdAt")
    fun observeAll(): Flow<List<TodoItem>>

    @Query("SELECT * FROM todos ORDER BY createdAt")
    suspend fun getAll(): List<TodoItem>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun get(id: Long): TodoItem?

    @Insert suspend fun insert(todo: TodoItem): Long
    @Update suspend fun update(todo: TodoItem)
    @Delete suspend fun delete(todo: TodoItem)
    @Delete suspend fun deleteAll(todos: List<TodoItem>)
}

@Dao
interface LearningDao {
    @Transaction
    @Query("SELECT * FROM learning_sections ORDER BY createdAt")
    fun observeSections(): Flow<List<SectionWithTopics>>

    @Insert suspend fun insert(section: LearningSection): Long
    @Update suspend fun update(section: LearningSection)
    @Delete suspend fun delete(section: LearningSection)

    @Insert suspend fun insert(topic: LearningTopic): Long
    @Update suspend fun update(topic: LearningTopic)
    @Delete suspend fun delete(topic: LearningTopic)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    suspend fun getAll(): List<Expense>

    @Insert suspend fun insert(expense: Expense): Long
    @Update suspend fun update(expense: Expense)
    @Delete suspend fun delete(expense: Expense)
}

@Database(
    entities = [DailyLog::class, StudySession::class, TodoItem::class, LearningSection::class, LearningTopic::class, Expense::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun dailyLogs(): DailyLogDao
    abstract fun sessions(): StudySessionDao
    abstract fun todos(): TodoDao
    abstract fun learning(): LearningDao
    abstract fun expenses(): ExpenseDao

    companion object {
        fun create(context: Context): TrackerDatabase =
            Room.databaseBuilder(context, TrackerDatabase::class.java, "tracker.db").build()
    }
}
