package com.mohammedsharukms.mypersonaltracker.health

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.Tracker
import com.mohammedsharukms.mypersonaltracker.sheet.healthFillsWorkout
import java.time.LocalDate
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import kotlinx.coroutines.launch

// Steps, sleep, weight and workouts from Health Connect into the Daily Tracker —
// today and the last few weeks, whenever the app opens. Read only. Steps only go
// up; sleep, weight and Workout only fill an empty day, so what you typed wins.
// None of it on its own makes a day "logged". Mirrors HealthSteps.swift.

/** One day from Health Connect. */
data class HealthDay(
    val steps: Int? = null,
    /** Hours asleep the night before (ending that morning). */
    val sleep: Double? = null,
    val weight: Double? = null,
    /** 20 minutes or more of exercise. */
    val workout: Boolean = false,
)

object HealthSteps {
    val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
    val permissions = setOf(
        stepsPermission,
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )
    /** How far back each sync looks (missed days fill in when you next open the app). */
    const val DAYS_BACK = 30L
    const val WORKOUT_MINUTES = 20L

    /** Today's steps, as of the last sync (for the Settings line and the widget). */
    var today by mutableStateOf<Int?>(null)
        private set
    private var syncing = false

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun isEnabled(context: Context) = prefs(context).getBoolean("healthStepsEnabled", false)
    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("healthStepsEnabled", on).apply()
        if (!on) today = null
    }

    /** Health Connect on this phone: ready, needs installing/updating, or not possible. */
    fun status(context: Context): Int = HealthConnectClient.getSdkStatus(context)
    fun isAvailable(context: Context) = status(context) == HealthConnectClient.SDK_AVAILABLE

    private suspend fun granted(context: Context): Set<String> = runCatching {
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
    }.getOrDefault(emptySet())

    /** Steps at least (the rest are read when allowed). */
    suspend fun hasPermission(context: Context): Boolean = stepsPermission in granted(context)
    suspend fun hasAllPermissions(context: Context): Boolean = granted(context).containsAll(permissions)

    /** Sends what Health Connect has that the sheet doesn't. */
    suspend fun sync(context: Context, store: SheetStore) {
        if (!isEnabled(context) || !isAvailable(context) || syncing || store.isDemo) return
        val snapshot = store.snapshot ?: return
        val allowed = granted(context)
        if (allowed.isEmpty()) return
        syncing = true
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val to = LocalDate.now()
            val from = to.minusDays(DAYS_BACK - 1)
            val days = mutableMapOf<String, HealthDay>()
            fun put(date: String, change: (HealthDay) -> HealthDay) { days[date] = change(days[date] ?: HealthDay()) }
            if (stepsPermission in allowed) runCatching { dailySteps(client, from, to) }.getOrNull()?.forEach { (d, n) -> put(d) { it.copy(steps = n) } }
            if (HealthPermission.getReadPermission(SleepSessionRecord::class) in allowed)
                runCatching { nightlySleep(client, from, to) }.getOrNull()?.forEach { (d, h) -> put(d) { it.copy(sleep = h) } }
            if (HealthPermission.getReadPermission(WeightRecord::class) in allowed)
                runCatching { dailyWeight(client, from, to) }.getOrNull()?.forEach { (d, kg) -> put(d) { it.copy(weight = kg) } }
            if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in allowed)
                runCatching { workoutDays(client, from, to) }.getOrNull()?.forEach { d -> put(d) { it.copy(workout = true) } }
            today = days[to.toString()]?.steps ?: 0
            val changes = changes(days, snapshot)
            if (changes.isNotEmpty()) store.saveHealth(changes)
            com.mohammedsharukms.mypersonaltracker.widget.WidgetUpdater.updateAll(context)
        } catch (_: Exception) {
            // Health Connect unavailable for the moment; the next open tries again.
        } finally {
            syncing = false
        }
    }

    /** Steps-only version, kept for the older callers and tests. */
    fun stepChanges(counts: Map<String, Int>, snapshot: SheetSnapshot): Map<String, Int> =
        changes(counts.mapValues { HealthDay(steps = it.value) }, snapshot).mapNotNull { (d, h) -> h.steps?.let { d to it } }.toMap()

    /** For each day of the sheet's period, only what Health Connect would add. */
    fun changes(health: Map<String, HealthDay>, snapshot: SheetSnapshot): Map<String, HealthDay> =
        snapshot.days.mapNotNull { day ->
            // Never a future day, whatever the sheet's rows say.
            if (day.date > LocalDate.now().toString()) return@mapNotNull null
            val h = health[day.date] ?: return@mapNotNull null
            // Only what you track: trackers you turned off, and a 5th habit that isn't a workout, are left alone.
            val set = snapshot.settings
            val add = HealthDay(
                steps = h.steps?.takeIf { Tracker.STEPS.isOn(set) && it > 0 && it > (day.steps ?: 0.0).toInt() },
                sleep = h.sleep?.takeIf { Tracker.SLEEP.isOn(set) && it > 0 && day.sleep == null }?.let { Math.round(it * 10) / 10.0 },
                weight = h.weight?.takeIf { Tracker.WEIGHT.isOn(set) && it > 0 && day.weight == null }?.let { Math.round(it * 10) / 10.0 },
                workout = healthFillsWorkout(set) && h.workout && day.workout == null,
            )
            if (add == HealthDay()) null else day.date to add
        }.toMap()

    // MARK: Reading Health Connect

    /** Steps per day, as Health Connect adds them up (no double counting across apps). */
    private suspend fun dailySteps(client: HealthConnectClient, from: LocalDate, to: LocalDate): Map<String, Int> =
        client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            ),
        ).associate { it.startTime.toLocalDate().toString() to (it.result[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0) }

    /** Hours asleep each night, filed under the day it ended; overlapping sessions merged, awake stages left out. */
    private suspend fun nightlySleep(client: HealthConnectClient, from: LocalDate, to: LocalDate): Map<String, Double> {
        val zone = ZoneId.systemDefault()
        val sessions = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class,
            TimeRangeFilter.between(from.minusDays(1).atTime(18, 0).atZone(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant()))).records
        val asleep = mutableMapOf<String, MutableList<Pair<Instant, Instant>>>()
        sessions.forEach { s ->
            val key = s.endTime.atZone(zone).toLocalDate().toString()
            val awake = setOf(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED)
            val parts = if (s.stages.isEmpty()) listOf(s.startTime to s.endTime)
                else s.stages.filter { it.stage !in awake }.map { it.startTime to it.endTime }
            asleep.getOrPut(key) { mutableListOf() } += parts
        }
        return asleep.mapValues { (_, list) ->
            val merged = mutableListOf<Pair<Instant, Instant>>()
            list.sortedBy { it.first }.forEach { next ->
                val last = merged.lastOrNull()
                if (last != null && !next.first.isAfter(last.second)) merged[merged.size - 1] = last.first to maxOf(last.second, next.second)
                else merged += next
            }
            merged.sumOf { Duration.between(it.first, it.second).toMinutes() } / 60.0
        }.filterValues { it >= 0.5 }
    }

    /** The last weight recorded each day, in kg. */
    private suspend fun dailyWeight(client: HealthConnectClient, from: LocalDate, to: LocalDate): Map<String, Double> {
        val zone = ZoneId.systemDefault()
        return client.readRecords(ReadRecordsRequest(WeightRecord::class,
            TimeRangeFilter.between(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant()))).records
            .sortedBy { it.time }.associate { it.time.atZone(zone).toLocalDate().toString() to it.weight.inKilograms }
    }

    /** Days with at least [WORKOUT_MINUTES] of exercise. */
    private suspend fun workoutDays(client: HealthConnectClient, from: LocalDate, to: LocalDate): Set<String> {
        val zone = ZoneId.systemDefault()
        return client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class,
            TimeRangeFilter.between(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant()))).records
            .groupBy { it.startTime.atZone(zone).toLocalDate().toString() }
            .filterValues { list -> list.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() } >= WORKOUT_MINUTES }.keys
    }
}

/**
 * Returns a "connect" action: asks Health Connect for steps access (or opens the
 * Play Store when Health Connect needs installing), then turns syncing on.
 */
@androidx.compose.runtime.Composable
fun rememberStepsConnector(store: SheetStore, onResult: (Boolean) -> Unit = {}): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        val ok = HealthSteps.stepsPermission in granted
        HealthSteps.setEnabled(context, ok)
        onResult(ok)
        if (ok) scope.launch { HealthSteps.sync(context, store) }
    }
    return {
        when (HealthSteps.status(context)) {
            HealthConnectClient.SDK_AVAILABLE -> scope.launch {
                if (HealthSteps.hasAllPermissions(context)) {
                    HealthSteps.setEnabled(context, true); onResult(true); HealthSteps.sync(context, store)
                } else launcher.launch(HealthSteps.permissions)
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> runCatching {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            else -> onResult(false)
        }
    }
}
