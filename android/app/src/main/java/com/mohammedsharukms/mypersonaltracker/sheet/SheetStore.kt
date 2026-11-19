package com.mohammedsharukms.mypersonaltracker.sheet

import com.mohammedsharukms.mypersonaltracker.sheet.google.GoogleRetry
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mohammedsharukms.mypersonaltracker.R
import com.mohammedsharukms.mypersonaltracker.TrackerApp
import com.mohammedsharukms.mypersonaltracker.sheet.ui.Money
import com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetDate
import com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** What the web app is sent: the key goes in the POST body, never in the link (links end up in server and proxy logs). */
object WebAppRequest {
    fun body(key: String, action: String, payload: JSONObject = JSONObject(), fields: Map<String, String> = emptyMap()): String =
        JSONObject().put("key", key).put("action", action).put("payload", payload).apply { fields.forEach { (k, v) -> put(k, v) } }.toString()
}

/**
 * Talks to the Google Sheet through its Apps Script web app. Reads come back as
 * one snapshot; every write returns the recalculated snapshot.
 *
 * Everyday writes (days, expenses, salary) never wait for the network: they go
 * into a queue saved on disk, show on screen at once (an estimate until the
 * sheet answers), and are sent in order — straight away when online, later when
 * not. Each carries a client id so a retry never writes twice. Deletes wait a
 * few seconds first, which is what makes Undo possible.
 *
 * Rare, structural writes (settings, currency, new period, recurring rules)
 * still go straight to the sheet and need a connection. Mirrors SheetStore.swift.
 *
 * State lives on the main thread; network work hops to IO itself.
 */
/**
 * Whether a reload's answer is shown. Read before a save landed (or overtaken by a newer refresh),
 * showing it would undo what was just saved — unless nothing is on screen yet (just connected):
 * then it's shown, saves still on their way stay on top, and the newer refresh replaces it.
 */
object RefreshPolicy {
    fun shows(overtaken: Boolean, showingData: Boolean) = !overtaken || !showingData
    fun forgetsLanded(overtaken: Boolean) = !overtaken
}

/** What the queue makes of Google's answers, and of edits to an add that was on its way. Mirrors SheetStore.swift. */
object QueuePolicy {
    /** A lapsed sign-in waits for the next one; no token (offline), a rejected token (401) or a busy Google goes again. */
    fun fromGoogle(e: com.mohammedsharukms.mypersonaltracker.sheet.google.GoogleException): SheetException = when (e) {
        is com.mohammedsharukms.mypersonaltracker.sheet.google.GoogleException.SignedOut -> SignedOutException()
        is com.mohammedsharukms.mypersonaltracker.sheet.google.GoogleException.Failed -> TransientException(e.message ?: "no token")
        is com.mohammedsharukms.mypersonaltracker.sheet.google.GoogleException.Http ->
            if (e.status == 401 || e.status == 408 || e.status == 429 || e.status >= 500) TransientException("HTTP ${e.status}")
            else SheetException(e.message ?: "Google refused.")
    }

    /** The ledger row a queued add got: new in the sheet's answer, with the add's date, amount and description
     *  (a recurring bill may have been posted in the same answer). */
    fun rowAssigned(add: PendingWrite, before: List<SheetExpense>, after: List<SheetExpense>): Int? {
        val taken = before.map { it.row }.toSet()
        val p = JSONObject(add.payloadJson)
        return after.firstOrNull { e ->
            e.row !in taken && e.date == p.optString("date") && kotlin.math.abs((e.qar ?: 0.0) - p.optDouble("qar", 0.0)) < 0.005 &&
                (e.description ?: "") == p.optString("description")
        }?.row
    }

    /** Queued edits and deletes of an add that was on its way, moved to the row it got; dropped when there's none. */
    fun pointing(writes: List<PendingWrite>, from: Int, to: Int?): List<PendingWrite> = writes.mapNotNull { w ->
        val p = JSONObject(w.payloadJson)
        if ((w.action != "updateExpense" && w.action != "deleteExpense") || !p.has("row") || p.optInt("row") != from) w
        else to?.let { w.copy(payloadJson = p.put("row", it).toString()) }
    }
}

class SheetStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("sheet", Context.MODE_PRIVATE)
    /** The API key, encrypted with a Keystore key and left out of backups (Secrets.kt). */
    private val secrets = SecretStore(PrefsValues(context.getSharedPreferences("secrets", Context.MODE_PRIVATE)), AesGcm { AesGcm.keystoreKey("secrets") })
    private val cacheFile = File(context.filesDir, "sheet-snapshot.json")
    private val queueFile = File(context.filesDir, "sheet-queue.json")
    private val landedFile = File(context.filesDir, "sheet-landed.json")
    /** The write being sent right now. It lands even if it's taken out of the queue meanwhile. */
    private var sending: PendingWrite? = null
    /** Saved on the sheet (it answered "got it") but not yet in a downloaded snapshot: still shown on top of it. */
    private var landed = listOf<PendingWrite>()
    private var transientRetries = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** What the screens show: the sheet's last answer with queued writes applied on top. */
    var snapshot by mutableStateOf<SheetSnapshot?>(null)
        private set
    /** The sheet's last answer (raw JSON), without anything still queued. */
    private var confirmed: JSONObject? = null
    var isLoading by mutableStateOf(false)
        private set
    /** What the current load is doing, for screens with nothing to show yet. */
    var loadProgress by mutableStateOf<SheetLoadProgress?>(null)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
    /** Epoch millis of the last successful sync. */
    var lastSynced by mutableStateOf<Long?>(null)
        private set
    /** The sheet refused the key. Cleared by a successful sync or a new key. */
    var keyRejected by mutableStateOf(false)
        private set
    /** Debug `sheetDemo`: sample data, no network, connect card hidden. */
    var isDemo by mutableStateOf(false)
        private set

    /** Writes not yet confirmed by the sheet, oldest first. */
    var pending by mutableStateOf(listOf<PendingWrite>())
        private set
    var isSyncing by mutableStateOf(false)
        private set
    /** The last send failed for lack of a connection; queued writes wait. */
    var isOffline by mutableStateOf(false)
        private set
    /** The sheet is being set up or reset: "5 of 13", or "starting". Cleared when data arrives. */
    var setupProgress by mutableStateOf<String?>(null)
        private set
    private var setupPoll: Job? = null
    /** The last delete, while it can still be undone. */
    var undoMessage by mutableStateOf<String?>(null)
        private set
    private var lastDeleted: PendingWrite? = null
    private var retryJob: Job? = null
    private var nextTempRow = -1

    /** The web app's `/exec` URL. */
    var webAppUrl by mutableStateOf(prefs.getString(URL_KEY, null)?.takeIf { it !in RETIRED_URLS } ?: DEFAULT_URL)
        private set
    /** Unlocks read and write on the whole sheet; kept encrypted (older versions saved it in the clear in "sheet"). */
    var apiKey by mutableStateOf(secrets.apply { adopt(API_KEY, PrefsValues(prefs)) }.get(API_KEY) ?: "")
        private set

    /** Sign in with Google: the tracker sheet in the user's own Drive, run by the sheet's script inside the app. */
    val googleAuth = com.mohammedsharukms.mypersonaltracker.sheet.google.GoogleAuth(context)
    private val googleApi = com.mohammedsharukms.mypersonaltracker.sheet.google.GoogleSheetsApi(googleAuth)
    private val runtime by lazy { com.mohammedsharukms.mypersonaltracker.sheet.google.ScriptRuntime(context) }
    /** The tracker sheet's id when connected by signing in with Google (null: the web app). */
    var googleSheetId by mutableStateOf(prefs.getString(GOOGLE_SHEET_KEY, null))
        private set
    val usesGoogleSheet: Boolean get() = googleSheetId != null
    private val backend get() = googleSheetId?.let { com.mohammedsharukms.mypersonaltracker.sheet.google.ScriptBackend(it, googleApi, runtime) }
    val googleSheetUrl: String? get() = googleSheetId?.let { "https://docs.google.com/spreadsheets/d/$it/edit" }

    /**
     * The Apps Script web app was chosen in Settings ▸ Your sheet ▸ Advanced. Its key alone isn't enough:
     * it stays saved after signing out of Google. First run of this version: a web app that loaded here stays in use.
     */
    var webAppChosen by mutableStateOf(
        if (prefs.contains(WEB_APP_CHOSEN_KEY)) prefs.getBoolean(WEB_APP_CHOSEN_KEY, false)
        else SheetConnection.chosenOnUpgrade(googleSheetId != null, apiKey.isNotEmpty(), cacheFile.exists())
            .also { prefs.edit().putBoolean(WEB_APP_CHOSEN_KEY, it).apply() }
    )
        private set

    val isConfigured: Boolean get() =
        SheetConnection.of(usesGoogleSheet, googleAuth.isSignedIn, webAppChosen, webAppUrl, apiKey) != SheetConnection.NONE
    /** No usable connection: no key yet, or the sheet refused it. */
    val needsConnection: Boolean get() = !isDemo && (!isConfigured || keyRejected)
    val isTodayLogged: Boolean get() = snapshot?.days?.firstOrNull { it.date == SheetDate.today }?.isLogged ?: false

    init {
        Money.store = this
        // Cached data is only shown for a saved key; without one it's stale.
        if (isConfigured) loadCache()
        pending = loadQueue()
        landed = loadQueue(landedFile)
        nextTempRow = (pending.mapNotNull { it.tempRow }.minOrNull() ?: 0) - 1
        recompute()
        // Back online: send whatever waited.
        runCatching {
            context.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch { if (isOffline || pending.isNotEmpty()) flush() }
                }
            })
        }
    }

    /** Saves the connection. A different URL or key may mean a different sheet, so old data is dropped. */
    fun setConnection(url: String, key: String) {
        val changed = url != webAppUrl || key != apiKey || googleSheetId != null
        googleSheetId = null
        prefs.edit().remove(GOOGLE_SHEET_KEY).apply()
        chooseWebApp(true)
        webAppUrl = url
        apiKey = key
        prefs.edit().putString(URL_KEY, url).apply()
        runCatching { secrets.set(API_KEY, key) }
        if (changed) resetData()
    }

    private fun chooseWebApp(chosen: Boolean) {
        webAppChosen = chosen
        prefs.edit().putBoolean(WEB_APP_CHOSEN_KEY, chosen).apply()
    }

    fun enableDemo(json: String) {
        confirmed = JSONObject(json)
        isDemo = true
        recompute()
    }

    // MARK: - Reads

    suspend fun refresh() {
        if (isDemo) return
        if (!isConfigured) {
            resetData()
            return
        }
        // Send queued writes first, so the snapshot fetched after includes them.
        flush()
        isLoading = true
        loadProgress = SheetLoadProgress.reading
        val generation = ++refreshesStarted
        val writesBefore = writesLanded
        // Only the newest load speaks, and only while it's still going.
        fun show(step: SheetLoadProgress) { if (isLoading && generation == refreshesStarted) loadProgress = step }
        try {
            val fetched = backend?.let { b ->
                withContext(GoogleRetry.Listener { show(SheetLoadProgress.waiting(it.toInt(), loadProgress)) }) {
                    viaGoogle { b.snapshot(::show) }
                }
            } ?: run {
                withContext(Dispatchers.IO) { post(webAppUrl, WebAppRequest.body(apiKey, "snapshot"), 60_000) }
            }
            val overtaken = generation != refreshesStarted || writesBefore != writesLanded || isSyncing
            if (!RefreshPolicy.shows(overtaken, showingData = confirmed != null)) return
            if (RefreshPolicy.forgetsLanded(overtaken)) {
                landed = emptyList()
                persistLanded()
            }
            apply(fetched)
            isOffline = false
        } catch (e: Exception) {
            handle(e)
        } finally {
            isLoading = false
            loadProgress = null
        }
    }

    // MARK: - Queued writes

    fun saveDay(day: SheetDay): Boolean {
        enqueue(
            "saveDay",
            JSONObject()
                .put("date", day.date)
                .put("office", day.office ?: "").put("breakfast", day.breakfast ?: "").put("lunch", day.lunch ?: "")
                .put("protein", day.protein ?: "").put("workout", day.workout ?: "")
                .put("water", cell(day.water)).put("sleep", cell(day.sleep)).put("steps", cell(day.steps))
                .put("weight", cell(day.weight)).put("screen", cell(day.screen)).put("notes", day.notes ?: ""),
            "Day ${SheetDate.weekdayDay(day.date)}",
        )
        return true
    }

    fun saveExpense(row: Int?, date: String, description: String, category: String, method: String, qar: Double, notes: String): Boolean {
        val payload = JSONObject()
            .put("date", date).put("description", description).put("category", category)
            .put("method", method).put("qar", qar).put("notes", notes)
        val summary = "${description.ifEmpty { category }} · ${SheetFormat.qar(qar)}"
        val before = spent(date, row)
        // An add already on its way can't be changed any more: the edit follows it, and finds its row when it lands.
        val queuedAdd = if (row != null && row < 0 && sending?.tempRow != row) pending.indexOfFirst { it.tempRow == row } else -1
        when {
            queuedAdd >= 0 -> {
                // Still queued: change what will be sent instead of sending twice.
                pending = pending.toMutableList().also { it[queuedAdd] = it[queuedAdd].copy(payloadJson = payload.toString(), summary = summary) }
                persistQueue()
                recompute()
            }
            row != null -> enqueue("updateExpense", payload.put("row", row), summary)
            else -> enqueue("addExpense", payload, summary, tempRow = nextTempRow--)
        }
        notifyIfOverCap(date, before, before + qar)
        return true
    }

    /** Deletes after a short grace period; [undoDelete] cancels it. */
    fun deleteExpense(row: Int): Boolean {
        val item = snapshot?.expenses?.firstOrNull { it.row == row }
        val summary = item?.let { "${it.description ?: it.category ?: "Expense"} · ${SheetFormat.qar(it.qar)}" } ?: "Expense"
        val queuedAdd = if (row < 0 && sending?.tempRow != row) pending.indexOfFirst { it.tempRow == row } else -1
        if (queuedAdd >= 0) {
            // Never reached the sheet: forget it, but keep it restorable.
            lastDeleted = pending[queuedAdd]
            pending = pending.filterIndexed { i, _ -> i != queuedAdd }
            persistQueue()
            recompute()
        } else {
            enqueue("deleteExpense", JSONObject().put("row", row), summary, delayMs = UNDO_WINDOW_MS)
            lastDeleted = pending.last()
        }
        val message = "Deleted $summary"
        undoMessage = message
        scope.launch {
            delay(UNDO_WINDOW_MS)
            if (undoMessage == message) dismissUndo()
        }
        return true
    }

    fun undoDelete() {
        val item = lastDeleted ?: return
        pending = if (item.action == "deleteExpense") pending.filter { it.id != item.id } else pending + item
        lastDeleted = null
        undoMessage = null
        persistQueue()
        recompute()
        scope.launch { flush() }
    }

    fun dismissUndo() {
        undoMessage = null
        lastDeleted = null
    }

    fun saveSalary(month: String, salary: Double?, otherIncome: Double?, remittance: Double?): Boolean {
        enqueue(
            "saveSalary",
            JSONObject().put("month", month).put("salary", cell(salary)).put("otherIncome", cell(otherIncome))
                .put("remittanceQar", cell(remittance)),
            "Income $month",
        )
        return true
    }

    // MARK: - Food (queued)

    /** Adds or replaces a meal; a new photo travels with the queued write and shows from the phone until saved to Drive. */
    fun saveMeal(id: String, date: String, time: String, type: MealType, items: List<FoodItem>, cost: Double?, notes: String,
                 photo: ByteArray?, removePhoto: Boolean, addToFinance: Boolean, financeCategory: String, method: String): Boolean {
        if (photo != null) MealPhotos.saveLocal(context, photo, id)
        if (removePhoto) MealPhotos.localFile(context, id).delete()
        val payload = JSONObject()
            .put("mealId", id).put("date", date).put("time", time).put("meal", type.label)
            .put("items", JSONArray(items.map { it.toJson() })).put("notes", notes).put("removePhoto", removePhoto)
            .put("finance", JSONObject().put("add", addToFinance).put("category", financeCategory).put("method", method))
        if (cost != null) payload.put("cost", Math.round(cost * 100) / 100.0)
        if (photo != null) payload.put("photo", MealPhotos.base64(photo))
        // Replace an edit of the same meal that hasn't gone yet, keeping its photo if this edit has none.
        val index = pending.indexOfFirst { it.action == "saveMeal" && JSONObject(it.payloadJson).optString("mealId") == id }
        if (index >= 0) {
            val earlier = JSONObject(pending[index].payloadJson)
            if (photo == null && !removePhoto && earlier.has("photo")) payload.put("photo", earlier.getString("photo"))
            pending = pending.filterIndexed { i, _ -> i != index }
        }
        enqueue("saveMeal", payload, "${type.label}: ${items.joinToString { it.food }}")
        return true
    }

    fun deleteMeal(id: String): Boolean {
        val index = pending.indexOfFirst { it.action == "saveMeal" && JSONObject(it.payloadJson).optString("mealId") == id }
        val onSheet = this.onSheet?.foodData?.log?.any { it.mealId == id } ?: false
        if (index >= 0 && !onSheet && !isSending("saveMeal", "mealId", id)) {
            pending = pending.filterIndexed { i, _ -> i != index }
            persistQueue()
            recompute()
        } else {
            enqueue("deleteMeal", JSONObject().put("mealId", id), "Delete meal")
        }
        MealPhotos.localFile(context, id).delete()
        return true
    }

    /** Replaces the planned foods for one date and meal (an empty list clears it). */
    fun savePlan(date: String, type: MealType, items: List<FoodItem>, eaten: Boolean): Boolean {
        pending = pending.filterNot {
            it.action == "savePlan" && JSONObject(it.payloadJson).let { p -> p.optString("date") == date && p.optString("meal") == type.label }
        }
        enqueue("savePlan", JSONObject().put("date", date).put("meal", type.label).put("items", JSONArray(items.map { it.toJson() })).put("eaten", eaten),
            "Plan ${SheetDate.weekdayDay(date)} ${type.label}")
        return true
    }

    // MARK: - People (queued)

    fun savePeopleEntry(entry: PeopleEntry, method: String): Boolean {
        pending = pending.filterNot { it.action == "savePeopleEntry" && JSONObject(it.payloadJson).optString("id") == entry.id }
        enqueue("savePeopleEntry", JSONObject().put("id", entry.id).put("date", entry.date).put("person", entry.person)
            .put("type", entry.type.label).put("amount", entry.amount).put("note", entry.note ?: "").put("method", method),
            "${entry.type.label} · ${entry.person} · ${SheetFormat.qar(entry.amount)}")
        return true
    }

    fun deletePeopleEntry(id: String): Boolean {
        val onSheet = (this.onSheet?.people?.any { it.id == id } ?: false) || isSending("savePeopleEntry", "id", id)
        pending = pending.filterNot { it.action == "savePeopleEntry" && JSONObject(it.payloadJson).optString("id") == id }
        if (onSheet) enqueue("deletePeopleEntry", JSONObject().put("id", id), "Delete entry") else { persistQueue(); recompute() }
        return true
    }

    // MARK: - Study (queued)

    private val confirmedStudy: StudyData? get() = onSheet?.study

    /** Queues a save, replacing an unsent save of the same row. */
    private fun enqueueRowSave(action: String, payload: JSONObject, summary: String) {
        val id = payload.optString("id")
        pending = pending.filterNot { it.action == action && JSONObject(it.payloadJson).optString("id") == id }
        enqueue(action, payload, summary)
    }

    /** Deletes a row: forgets unsent saves, and asks the sheet only if it ever got there. */
    private fun deleteRow(action: String, saveAction: String, id: String, onSheet: Boolean, summary: String) {
        pending = pending.filterNot { it.action == saveAction && JSONObject(it.payloadJson).optString("id") == id }
        if (onSheet || isSending(saveAction, "id", id)) enqueue(action, JSONObject().put("id", id), summary) else { persistQueue(); recompute() }
    }

    /** The save on its way right now is this one: it will reach the sheet, so a delete must follow it. */
    private fun isSending(action: String, key: String, value: String): Boolean =
        sending?.let { it.action == action && JSONObject(it.payloadJson).optString(key) == value } == true

    fun saveStudySubject(subject: StudySubject) = enqueueRowSave("saveStudySubject", subject.toJson(), "Subject ${subject.name}")

    fun deleteStudySubject(id: String) {
        val study = snapshot?.studyData ?: StudyData()
        // Its unsent topics and goals go with it.
        val topicIds = study.topics.filter { it.subjectId == id }.map { it.id }.toSet()
        val goalIds = study.goals.filter { it.subjectId == id }.map { it.id }.toSet()
        pending = pending.filterNot {
            val rowId = JSONObject(it.payloadJson).optString("id")
            (it.action == "saveStudyTopic" && rowId in topicIds) || (it.action == "saveStudyGoal" && rowId in goalIds)
        }
        deleteRow("deleteStudySubject", "saveStudySubject", id, confirmedStudy?.subjects?.any { it.id == id } ?: false, "Delete subject")
    }

    fun saveStudyTopic(topic: StudyTopic) = enqueueRowSave("saveStudyTopic", topic.toJson(), "Topic ${topic.topic}")

    fun deleteStudyTopic(id: String) =
        deleteRow("deleteStudyTopic", "saveStudyTopic", id, confirmedStudy?.topics?.any { it.id == id } ?: false, "Delete topic")

    /** Logs a session, and updates the concept it was on (stage, confidence, next review). */
    fun saveStudySession(entry: StudyEntry, topic: StudyTopic? = null) {
        if (topic != null) saveStudyTopic(topic)
        enqueueRowSave("saveStudySession", entry.toJson(), "Study ${StudyFormat.minutes(entry.minutes)}${entry.subject?.let { " · $it" } ?: ""}")
    }

    fun deleteStudySession(id: String) =
        deleteRow("deleteStudySession", "saveStudySession", id, confirmedStudy?.sessions?.any { it.id == id } ?: false, "Delete session")

    fun saveStudyGoal(goal: StudyGoal) = enqueueRowSave("saveStudyGoal", goal.toJson(), "Goal ${goal.title}")

    fun deleteStudyGoal(id: String) =
        deleteRow("deleteStudyGoal", "saveStudyGoal", id, confirmedStudy?.goals?.any { it.id == id } ?: false, "Delete goal")

    // MARK: - Salary split (queued)

    fun saveSetAside(entry: SetAsideEntry) = enqueueRowSave("saveSetAside", entry.toJson(), "${entry.bucket} · ${SheetFormat.qar(entry.amount)}")

    fun deleteSetAside(id: String) = deleteRow("deleteSetAside", "saveSetAside", id,
        onSheet?.setAside?.any { it.id == id } ?: false, "Delete money put aside")

    // MARK: - Health from the phone (queued)

    /**
     * Steps, sleep, weight and workouts from Health Connect. Steps are never lowered; the rest
     * only fill empty days. A newer sync replaces one still waiting to go.
     */
    fun saveHealth(days: Map<String, com.mohammedsharukms.mypersonaltracker.health.HealthDay>) {
        val merged = days.toMutableMap()
        val inFlight = sending?.id
        val waiting = pending.filter { (it.action == "saveHealth" || it.action == "saveSteps") && it.id != inFlight }
        waiting.forEach { w ->
            val list = JSONObject(w.payloadJson).optJSONArray("days") ?: return@forEach
            for (i in 0 until list.length()) {
                val h = list.optJSONObject(i) ?: continue
                val date = h.optString("date")
                val day = merged[date] ?: com.mohammedsharukms.mypersonaltracker.health.HealthDay()
                merged[date] = day.copy(
                    steps = maxOf(day.steps ?: 0, h.optInt("steps")).takeIf { it > 0 },
                    sleep = day.sleep ?: h.dbl("sleep"), weight = day.weight ?: h.dbl("weight"),
                    workout = day.workout || h.optBoolean("workout"),
                )
            }
        }
        val ids = waiting.map { it.id }.toSet()
        pending = pending.filter { it.id !in ids }
        val list = JSONArray(merged.toSortedMap().map { (date, h) ->
            JSONObject().put("date", date).apply {
                h.steps?.let { put("steps", it) }; h.sleep?.let { put("sleep", it) }; h.weight?.let { put("weight", it) }
                if (h.workout) put("workout", true)
            }
        })
        enqueue("saveHealth", JSONObject().put("days", list), "From Health Connect · ${list.length()} day${if (list.length() == 1) "" else "s"}")
    }

    // MARK: - Your habits (queued)

    fun saveHabits(habits: List<Triple<String, Boolean, String>>, trackers: Map<Tracker, Boolean>) = enqueue("saveHabits",
        JSONObject()
            .put("habits", JSONArray(habits.map { (name, on, icon) -> JSONObject().put("name", name).put("on", on).put("icon", icon) }))
            .put("trackers", JSONObject(trackers.mapKeys { it.key.key })),
        "Your habits")

    /** Renames one of the five, changes its icon or turns it off; the others stay as they are. */
    fun saveHabit(habit: Habit, name: String, on: Boolean, icon: String) {
        val s = snapshot?.settings ?: return
        saveHabits(Habit.entries.map { h -> if (h == habit) Triple(name, on, icon) else Triple(h.name(s), h.isOn(s), h.iconKey(s)) },
            Tracker.entries.associateWith { it.isOn(s) })
    }

    // MARK: - Your own inputs (queued)

    fun inputExists(id: String): Boolean = snapshot?.myInputs?.inputs?.any { it.id == id } ?: false

    fun saveInput(input: MyInput) =
        enqueueRowSave("saveInput", input.toJson(), "${if (input.section == InputSection.HABIT) "Habit" else "Input"}: ${input.name}")

    /** Deletes it and everything logged for it. */
    fun deleteInput(id: String) {
        pending = pending.mapNotNull { w ->
            if (w.action != "saveInputValues") return@mapNotNull w
            val p = JSONObject(w.payloadJson)
            p.optJSONObject("values")?.remove(id)
            if ((p.optJSONObject("values")?.length() ?: 0) == 0) null else w.copy(payloadJson = p.toString())
        }
        deleteRow("deleteInput", "saveInput", id,
            onSheet?.myInputs?.inputs?.any { it.id == id } ?: false, "Delete input")
    }

    /** What you logged for your own inputs on one day ("" clears one); merged into an unsent save for that day. */
    fun saveInputValues(date: String, values: Map<String, String>) {
        if (values.isEmpty()) return
        val inFlight = sending?.id
        val i = pending.indexOfFirst { it.action == "saveInputValues" && JSONObject(it.payloadJson).optString("date") == date && it.id != inFlight }
        if (i >= 0) {
            val p = JSONObject(pending[i].payloadJson)
            val merged = p.optJSONObject("values") ?: JSONObject()
            values.forEach { (k, v) -> merged.put(k, v) }
            pending = pending.toMutableList().also { it[i] = it[i].copy(payloadJson = p.put("values", merged).toString()) }
            persistQueue()
            recompute()
            return
        }
        enqueue("saveInputValues", JSONObject().put("date", date).put("values", JSONObject(values)), "Day ${SheetDate.weekdayDay(date)} · your inputs")
    }

    // MARK: - Renewals and loans (queued)

    fun saveRenewal(r: Renewal) = enqueueRowSave("saveRenewal", r.toJson(), "Renewal: ${r.item}")
    fun deleteRenewal(id: String) = deleteRow("deleteRenewal", "saveRenewal", id,
        onSheet?.renewals?.any { it.id == id } ?: false, "Delete renewal")
    fun saveLoan(l: Loan) = enqueueRowSave("saveLoan", l.toJson(), "Loan: ${l.name}")
    fun deleteLoan(id: String) = deleteRow("deleteLoan", "saveLoan", id,
        onSheet?.loans?.any { it.id == id } ?: false, "Delete loan")

    // MARK: - Parents (queued)

    /** Photos not on Drive yet travel with every save of the request (from their copy on this phone),
     *  so an edit sent before the sheet has them doesn't lose them. */
    fun saveParentAsk(a: ParentAsk) {
        val payload = a.toJson()
        val added = JSONObject()
        a.photos.mapNotNull(ParentPhotos::localKey).forEach { key ->
            ParentPhotos.localFile(context, key).takeIf { it.exists() }?.let { added.put(key, MealPhotos.base64(it.readBytes())) }
        }
        if (added.length() > 0) payload.put("newPhotos", added)
        enqueueRowSave("saveParentAsk", payload, "${a.who.label}: ${a.task}")
    }
    fun saveParentMedicine(m: ParentMedicine) = enqueueRowSave("saveParentMedicine", m.toJson(), "Medicine: ${m.name}")
    fun deleteParentMedicine(id: String) = deleteRow("deleteParentMedicine", "saveParentMedicine", id,
        onSheet?.parentMedicines?.any { it.id == id } ?: false, "Delete medicine")
    fun saveParentDate(d: ParentDate) = enqueueRowSave("saveParentDate", d.toJson(), d.title)
    fun deleteParentDate(id: String) = deleteRow("deleteParentDate", "saveParentDate", id,
        onSheet?.parentDates?.any { it.id == id } ?: false, "Delete date")
    fun saveTakeHomeItem(i: TakeHomeItem) = enqueueRowSave("saveTakeHomeItem", i.toJson(), "Take home: ${i.item}")
    fun deleteTakeHomeItem(id: String) = deleteRow("deleteTakeHomeItem", "saveTakeHomeItem", id,
        onSheet?.takeHome?.items?.any { it.id == id } ?: false, "Delete item")
    /** The next trip home ("yyyy-MM-dd"), or null for none planned. */
    fun saveTakeHomeTrip(date: String?) {
        val inFlight = sending?.id
        pending = pending.filterNot { it.action == "saveTakeHomeTrip" && it.id != inFlight }
        enqueue("saveTakeHomeTrip", JSONObject().put("date", date ?: ""), if (date != null) "Trip home ${SheetDate.short(date)}" else "No trip planned")
    }
    fun saveParentReading(r: ParentReading) = enqueueRowSave("saveParentReading", r.toJson(), "${r.who.raw}'s ${r.kind.short}")
    fun deleteParentReading(id: String) = deleteRow("deleteParentReading", "saveParentReading", id,
        onSheet?.parentHealth?.any { it.id == id } ?: false, "Delete reading")
    fun deleteParentAsk(id: String) = deleteRow("deleteParentAsk", "saveParentAsk", id,
        onSheet?.parents?.any { it.id == id } ?: false, "Delete request")

    // MARK: - Life goals (queued)

    private val confirmedGoals: LifeGoalData?
        get() = onSheet?.lifeGoals

    fun saveLifeGoal(goal: LifeGoal) = enqueueRowSave("saveLifeGoal", goal.toJson(), "Goal: ${goal.title}")

    /** Its steps go too; short-term goals under it stay, unlinked. */
    fun deleteLifeGoal(id: String) =
        deleteRow("deleteLifeGoal", "saveLifeGoal", id, confirmedGoals?.goals?.any { it.id == id } ?: false, "Delete goal")

    fun saveGoalStep(step: GoalStep) = enqueueRowSave("saveGoalStep", step.toJson(), "Step: ${step.step}")

    fun deleteGoalStep(id: String) =
        deleteRow("deleteGoalStep", "saveGoalStep", id, confirmedGoals?.steps?.any { it.id == id } ?: false, "Delete step")

    // MARK: - Planner (queued)

    private val confirmedPlanner: PlannerData?
        get() = onSheet?.planner

    fun savePlan(plan: DayPlan) = enqueueRowSave("saveDayPlan", plan.toJson(), "Plan: ${plan.title}")

    fun deletePlan(id: String) = deleteRow("deleteDayPlan", "saveDayPlan", id, confirmedPlanner?.plans?.any { it.id == id } ?: false, "Delete plan")

    fun saveOutfit(outfit: Outfit) = enqueueRowSave("saveOutfit", outfit.toJson(), "Outfit for ${SheetDate.short(outfit.date)}")

    fun deleteOutfit(id: String) = deleteRow("deleteOutfit", "saveOutfit", id, confirmedPlanner?.outfits?.any { it.id == id } ?: false, "Clear outfit")

    fun saveClothing(item: Clothing) = enqueueRowSave("saveClothing", item.toJson(), "Wardrobe: ${item.name}")

    fun deleteClothing(id: String) =
        deleteRow("deleteClothing", "saveClothing", id, confirmedPlanner?.wardrobe?.any { it.id == id } ?: false, "Remove from wardrobe")

    fun saveSavingsGoal(goal: SavingsGoal) = enqueueRowSave("saveSavingsGoal", goal.toJson(), "Goal ${goal.name}")

    /** The money put aside for it stays; it just stops counting toward a goal. */
    fun deleteSavingsGoal(id: String) = deleteRow("deleteSavingsGoal", "saveSavingsGoal", id,
        onSheet?.savingsGoals?.any { it.id == id } ?: false, "Delete goal")

    // MARK: - Shopping (queued)

    fun saveShoppingItem(item: ShoppingItem) = enqueueRowSave("saveShoppingItem", item.toJson(), "Shopping: ${item.item}")

    fun deleteShoppingItem(id: String) = deleteRow("deleteShoppingItem", "saveShoppingItem", id,
        onSheet?.shopping?.items?.any { it.id == id } ?: false, "Remove from list")

    /** Finishes a trip: the cart's items become bought, and (optionally) one Groceries expense is added. */
    fun checkoutShopping(tripId: String, date: String, store: String, total: Double?, method: String, addToFinance: Boolean,
                         notes: String, items: List<Pair<String, Double?>>) {
        val payload = JSONObject().put("tripId", tripId).put("date", date).put("store", store).put("method", method)
            .put("addToFinance", addToFinance).put("notes", notes)
            .put("items", JSONArray(items.map { (id, price) -> JSONObject().put("id", id).apply { if (price != null) put("price", price) } }))
        if (total != null) payload.put("total", Math.round(total * 100) / 100.0)
        enqueue("checkoutShopping", payload, "Shopping${if (store.isEmpty()) "" else " at $store"} · ${items.size} items")
    }

    fun deleteShoppingTrip(id: String) {
        val onSheet = this.onSheet?.shopping?.trips?.any { it.id == id } ?: false
        val queued = pending.indexOfFirst { it.action == "checkoutShopping" && JSONObject(it.payloadJson).optString("tripId") == id }
        if (queued >= 0 && !onSheet && !isSending("checkoutShopping", "tripId", id)) {
            pending = pending.filterIndexed { i, _ -> i != queued }
            persistQueue()
            recompute()
        } else enqueue("deleteShoppingTrip", JSONObject().put("tripId", id), "Delete shopping trip")
    }

    suspend fun copyPlanWeek(from: String, to: String): Boolean =
        sendNow("copyPlanWeek", JSONObject().put("from", from).put("to", to).put("replace", true))

    suspend fun saveMyFood(food: MyFood): Boolean = sendNow("saveMyFood", JSONObject()
        .put("name", food.name).put("amount", food.amount ?: 1.0).put("unit", food.unit ?: "serving")
        .put("calories", food.calories ?: 0.0).put("protein", food.protein ?: 0.0).put("carbs", food.carbs ?: 0.0).put("fat", food.fat ?: 0.0)
        .apply { food.cost?.let { put("cost", it) }; food.row?.let { put("row", it) } })

    suspend fun deleteMyFood(row: Int): Boolean = sendNow("deleteMyFood", JSONObject().put("row", row))

    /** A meal photo from Drive (the caller caches it). */
    suspend fun photo(id: String, thumb: Boolean): ByteArray? {
        if (!isConfigured) return null
        if (usesGoogleSheet) return runCatching { googleApi.download(id, thumb) }.getOrNull()
        return runCatching {
            withContext(Dispatchers.IO) {
                val body = WebAppRequest.body(apiKey, "photo", fields = mapOf("id" to id, "size" to if (thumb) "thumb" else "full"))
                val base64 = post(webAppUrl, body, 60_000).getString("base64")
                android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            }
        }.getOrNull()
    }

    // MARK: - Your data

    /** One action whose answer isn't a snapshot (export, reset). */
    /** One year added up across every period (28_YearReview.gs). */
    suspend fun yearReview(year: Int): JSONObject {
        if (isDemo) throw SheetException("Not available with sample data.")
        return callRaw("yearReview", payload = JSONObject().put("year", year))
    }

    private suspend fun callRaw(action: String, timeoutMs: Int = 120_000, payload: JSONObject = JSONObject()): JSONObject {
        if (!isConfigured) throw SheetException("Connect the Google Sheet in Settings first.")
        backend?.let { b ->
            if (action == "resetAll") return resetGoogleSheet()
            return viaGoogle { unwrap(b.request(action, payload)) }
        }
        val body = WebAppRequest.body(apiKey, action, payload)
        return withContext(Dispatchers.IO) { post(webAppUrl, body, timeoutMs) }
    }

    /** Every table in the sheet as rows of text (header first); falls back to what's on the phone. */
    suspend fun exportTables(): Pair<List<Pair<String, List<List<String>>>>, Boolean> {
        if (!isDemo) runCatching { callRaw("export") }.getOrNull()?.optJSONArray("files")?.let { files ->
            val tables = (0 until files.length()).map { i ->
                val f = files.getJSONObject(i)
                val rows = f.getJSONArray("rows")
                f.getString("name") to (0 until rows.length()).map { r -> rows.getJSONArray(r).let { row -> (0 until row.length()).map { c -> row.optString(c) } } }
            }
            return tables to true
        }
        return com.mohammedsharukms.mypersonaltracker.sheet.ui.DataExport.tables(snapshot) to false
    }

    /** Wipes everything in the sheet and rebuilds it fresh from today (in the background), and forgets this phone's copy. */
    suspend fun resetAllData(): Boolean {
        if (isDemo) { errorMessage = "Not available with sample data."; return false }
        isSaving = true
        return try {
            callRaw("resetAll", 60_000)
            pending = emptyList()
            persistQueue()
            confirmed = null
            cacheFile.delete()
            java.io.File(context.filesDir, "meal-photos").deleteRecursively()
            context.getSharedPreferences("shopping", Context.MODE_PRIVATE).edit().clear().apply()
            recompute()
            // A Google sign-in sheet is made fresh and ready at once; the web app rebuilds in the background.
            if (usesGoogleSheet) refresh() else noteSetup("starting")
            true
        } catch (e: Exception) {
            errorMessage = if (e is IOException) "You're offline. Resetting needs a connection." else e.message
            false
        } finally {
            isSaving = false
        }
    }

    /** Drops a queued write the sheet will never accept. */
    fun discard(write: PendingWrite) {
        pending = pending.filter { it.id != write.id }
        persistQueue()
        recompute()
    }

    // MARK: - Direct writes (need a connection)

    /** Keys as in the sheet's `CFG` (e.g. "DAILY_LIMIT"); percentages as fractions. */
    suspend fun saveSettings(values: Map<String, Double>): Boolean =
        sendNow("saveSettings", JSONObject().apply { values.forEach { (k, v) -> put(k, v) } })

    /** Body & goal; the sheet works out the calorie and protein targets from it. */
    suspend fun saveBody(values: JSONObject): Boolean = sendNow("saveBody", values)

    /** Sets the workbook's currencies (and optionally 1 main = `rate` home). */
    suspend fun saveCurrency(main: String, home: String, rate: Double?): Boolean =
        sendNow("saveCurrency", JSONObject().put("main", main).put("home", home).apply { if (rate != null) put("rate", rate) })

    /** Archives the current period in the sheet and starts a new one. Slow: the sheet rebuilds. */
    suspend fun startNextPeriod(start: String, end: String): Boolean =
        sendNow("startNextPeriod", JSONObject().put("start", start).put("end", end).put("archive", true), timeoutMs = 300_000)

    suspend fun saveRecurring(rule: RecurringExpense, addThisMonth: Boolean, lastAdded: String? = null): Boolean {
        val payload = JSONObject()
            .put("description", rule.description ?: "").put("category", rule.category ?: "").put("method", rule.method ?: "")
            .put("amount", rule.amount ?: 0.0).put("day", rule.day ?: 1).put("active", rule.active).put("notes", rule.notes ?: "")
            .put("addThisMonth", addThisMonth)
        if (rule.row != null) payload.put("row", rule.row)
        if (rule.row == null && lastAdded != null) payload.put("lastAdded", lastAdded)
        return sendNow("saveRecurring", payload)
    }

    suspend fun deleteRecurring(row: Int): Boolean = sendNow("deleteRecurring", JSONObject().put("row", row))

    /** A number, or an empty cell when there is none. */
    private fun cell(value: Double?): Any = value ?: ""

    private suspend fun sendNow(action: String, payload: JSONObject, timeoutMs: Int = 60_000): Boolean {
        if (isDemo) { errorMessage = "Not available with sample data."; return false }
        if (!isConfigured) {
            errorMessage = "Connect the Google Sheet in Settings first."
            return false
        }
        flush()
        isSaving = true
        return try {
            val result = backend?.let { b ->
                viaGoogle { unwrap(b.request(action, payload, "ack")) }
                viaGoogle { b.snapshot() }
            } ?: run {
                val body = WebAppRequest.body(apiKey, action, payload)
                withContext(Dispatchers.IO) { post(webAppUrl, body, timeoutMs) }
            }
            writesLanded++
            forgetLanded()
            apply(result)
            true
        } catch (e: Exception) {
            handle(e)
            if (e is IOException) errorMessage = "You're offline. This change needs a connection — try again when you're back online."
            if (e is TransientException) errorMessage = "Your sheet didn't answer just now. Try again in a minute."
            false
        } finally {
            isSaving = false
        }
    }

    // MARK: - Queue

    /** Saves the sheet has confirmed, and refreshes begun — so a snapshot read before a save landed is never shown over it. */
    private var writesLanded = 0
    private var refreshesStarted = 0

    private fun enqueue(action: String, payload: JSONObject, summary: String, tempRow: Int? = null, delayMs: Long = 0) {
        val now = System.currentTimeMillis()
        pending = pending + PendingWrite(UUID.randomUUID().toString(), action, payload.toString(), summary, now,
            if (delayMs > 0) now + delayMs else null, tempRow)
        persistQueue()
        recompute()
        scope.launch { flush() }
    }

    /** Sends queued writes in order until done, offline, or one is held for Undo. */
    suspend fun flush() {
        if (isSyncing || isDemo || !isConfigured) return
        isSyncing = true
        var sentQuick = false
        try {
            // Sent writes are removed by id, not position: an edit made while one is
            // on its way can replace it in the queue, and must not cost another write.
            while (true) {
                val write = pending.firstOrNull() ?: break
                val notBefore = write.notBefore
                if (notBefore != null && notBefore > System.currentTimeMillis()) {
                    scheduleRetry(notBefore - System.currentTimeMillis() + 200)
                    return
                }
                if (write.id == lastDeleted?.id) { lastDeleted = null; undoMessage = null }
                sending = write
                try {
                    val payload = JSONObject(write.payloadJson).put("clientId", write.id)
                    // Most saves only need "got it" (a second or two); the snapshot follows once the queue is empty.
                    // An added expense waits for the full reply, which carries the row the sheet gave it.
                    if (write.tempRow == null) payload.put("reply", "ack")
                    val result = backend?.let { b ->
                        val ack = viaGoogle { unwrap(b.request(write.action, payload, if (write.tempRow == null) "ack" else null)) }
                        if (ack.optBoolean(ACK)) ack else viaGoogle { b.snapshot() }
                    } ?: run {
                        val body = WebAppRequest.body(apiKey, write.action, payload)
                        withContext(Dispatchers.IO) { post(webAppUrl, body, 60_000) }
                    }
                    pending = pending.filter { it.id != write.id }
                    var lostEdits = false
                    val tempRow = write.tempRow
                    if (tempRow != null && !result.optBoolean(ACK)) {
                        // Edits and deletes made while it was on its way go to the row the sheet gave it.
                        val after = runCatching { SheetJson.snapshot(result).expenses }.getOrDefault(emptyList())
                        val before = pending.size
                        pending = QueuePolicy.pointing(pending, tempRow, QueuePolicy.rowAssigned(write, onSheet?.expenses.orEmpty(), after))
                        lostEdits = pending.size < before
                    }
                    writesLanded++
                    transientRetries = 0
                    persistQueue()
                    if (result.optBoolean(ACK)) {
                        landed = landed + write
                        persistLanded()
                        recompute()
                        sentQuick = true
                    } else {
                        forgetLanded()
                        apply(result)
                        if (lostEdits) errorMessage = "Couldn't change “${write.summary}” on the sheet. Edit it again there."
                    }
                    isOffline = false
                } catch (e: IOException) {
                    isOffline = true
                    scheduleRetry(30_000)
                    return
                } catch (e: UnauthorizedException) {
                    handle(e)
                    return
                } catch (e: SignedOutException) {
                    // Kept: everything goes once you sign in again.
                    errorMessage = e.message
                    return
                } catch (e: BusyException) {
                    // Being rebuilt: keep the change and try again shortly.
                    noteSetup(e.progress)
                    scheduleRetry(60_000)
                    return
                } catch (e: TransientException) {
                    // Google busy: the change stays queued (a resend is never written twice) and goes again soon.
                    transientRetries++
                    scheduleRetry(minOf(60_000L, 3_000L shl (transientRetries - 1).coerceAtMost(5)))
                    return
                } catch (e: Exception) {
                    // The sheet refused this one (bad date, full ledger…): drop it and say so.
                    pending = pending.filter { it.id != write.id }
                    persistQueue()
                    recompute()
                    errorMessage = "Couldn't save “${write.summary}”: ${e.message}"
                }
            }
        } finally {
            sending = null
            isSyncing = false
        }
        // All sent: download once so the sheet's formulas (score, budget, summaries) catch up.
        // Only after this run sent something — refresh() flushes first, and must not start itself again.
        if (sentQuick && pending.isEmpty()) scope.launch { refresh() }
    }

    /** Waits until the queue is empty (or time runs out) — for shortcuts that close quickly. */
    suspend fun waitForSync(ms: Long) {
        val deadline = System.currentTimeMillis() + ms
        while (pending.isNotEmpty() && !isOffline && System.currentTimeMillis() < deadline) {
            flush()
            if (pending.isEmpty() || isOffline) break
            delay(300)
        }
    }

    private fun scheduleRetry(afterMs: Long) {
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(maxOf(500, afterMs))
            flush()
        }
    }

    private fun persistQueue() {
        runCatching { queueFile.writeText(JSONArray(pending.map { it.toJson() }).toString()) }
    }

    private fun persistLanded() {
        runCatching { landedFile.writeText(JSONArray(landed.map { it.toJson() }).toString()) }
    }

    /** A snapshot downloaded after these saves landed has them: stop drawing them on top. */
    private fun forgetLanded() {
        if (landed.isEmpty()) return
        landed = emptyList()
        persistLanded()
    }

    /** What the sheet has: the last download plus saves it has confirmed since (for "is this row on the sheet yet?"). */
    private val onSheet: SheetSnapshot?
        get() = confirmed?.let { runCatching { SheetJson.snapshot(it) }.getOrNull() }?.let { landed.fold(it) { s, w -> applyLocally(w, s) } }

    private fun loadQueue(file: File = queueFile): List<PendingWrite> = runCatching {
        val array = JSONArray(file.readText())
        (0 until array.length()).map { PendingWrite.fromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    // MARK: - Optimistic view

    /** The confirmed snapshot with every queued write applied. Formula columns are estimated. */
    private fun recompute() {
        val base = confirmed?.let { runCatching { SheetJson.snapshot(it) }.getOrNull() }
        snapshot = base?.let { (landed + pending).fold(it) { s, w -> applyLocally(w, s) } }
        snapshot?.takeIf { !isDemo }?.let { s -> runCatching { BudgetAlerts.check(context, s) } }
        scope.launch { runCatching { com.mohammedsharukms.mypersonaltracker.widget.WidgetUpdater.updateAll(context) } }
    }

    private fun spent(date: String, excludingRow: Int?): Double =
        snapshot?.expenses.orEmpty().filter { it.date == date && it.row != excludingRow }.sumOf { it.qar ?: 0.0 }

    /** A notification the moment an expense pushes today over the daily cap. */
    private fun notifyIfOverCap(date: String, before: Double, after: Double) {
        val cap = snapshot?.settings?.dailyLimit ?: return
        if (date != SheetDate.today || before > cap || after <= cap) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = NotificationCompat.Builder(context, TrackerApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Over today's spending cap")
            .setContentText("${SheetFormat.qar(after)} of ${SheetFormat.number(cap)} ${Money.main} spent today.")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify("overCap$date".hashCode(), notification)
    }

    // MARK: - Transport

    private fun get(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        return read(connection)
    }

    /** Apps Script answers a POST with a redirect to the result, which must be fetched with GET. */
    private fun post(url: String, body: String, timeoutMs: Int): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = timeoutMs
            instanceFollowRedirects = false
            doOutput = true
            setRequestProperty("Content-Type", "text/plain;charset=utf-8")
        }
        connection.outputStream.use { it.write(body.toByteArray()) }
        val code = connection.responseCode
        if (code in 300..399) {
            val location = connection.getHeaderField("Location") ?: throw SheetException("The sheet sent an empty redirect.")
            connection.disconnect()
            return get(location)
        }
        return read(connection)
    }

    private fun read(connection: HttpURLConnection): JSONObject {
        try {
            val code = connection.responseCode
            // Google busy or briefly down: try again later rather than give up on the change.
            if (code == 408 || code == 429 || code >= 500) throw TransientException("HTTP $code")
            if (code !in 200..299) throw SheetException("The sheet answered with HTTP $code.")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val envelope = try {
                JSONObject(text)
            } catch (e: Exception) {
                // An HTML page instead of JSON almost always means the deployment isn't public or the URL is wrong.
                if ("uthorization" in text || "Access denied" in text) throw SheetException("Your sheet needs its one-time setup: open the sheet ▸ Extensions ▸ Apps Script, choose setupNewWorkbook and press Run (allow access when asked).")
                // Google's own "try again later" pages are HTML too: keep queued changes and retry.
                throw TransientException("The web app didn't return data. Check the URL and that it's deployed for \"Anyone\".")
            }
            return unwrap(envelope)
        } finally {
            connection.disconnect()
        }
    }

    /** The web app's reply `{ ok, data | ack | error }` as data, an ack marker, or the matching exception. */
    private fun unwrap(envelope: JSONObject): JSONObject {
        if (!envelope.optBoolean("ok")) {
            val error = envelope.str("error")
            if (error?.startsWith("Unauthorized") == true) throw UnauthorizedException()
            if (error?.startsWith("SETUP_IN_PROGRESS") == true) throw BusyException(error.removePrefix("SETUP_IN_PROGRESS: "))
            if (error != null && TransientException.isTemporary(error)) throw TransientException(error)
            throw SheetException(error ?: "The sheet rejected the request.")
        }
        // A quick save ("reply": "ack") is acknowledged without the snapshot.
        return envelope.optJSONObject("data") ?: if (envelope.optBoolean("ack")) JSONObject().put(ACK, true)
            else throw SheetException("The sheet returned no data.")
    }

    // MARK: - Google sign-in sheet

    /** Google's answers in the terms the queue understands: busy or down is "try again shortly"; the rest is a refusal. */
    private suspend fun <T> viaGoogle(work: suspend () -> T): T = try {
        work()
    } catch (e: com.mohammedsharukms.mypersonaltracker.sheet.google.GoogleException) {
        throw QueuePolicy.fromGoogle(e)
    } catch (e: IllegalStateException) {
        throw SheetException(e.message ?: "The sheet logic failed.")
    }

    /** After the Google sign-in: finds the tracker sheet this app made before, or makes it, and loads it. */
    suspend fun connectGoogleSheet(progress: (String) -> Unit = {}) {
        progress("Looking for your sheet…")
        val id = viaGoogle { com.mohammedsharukms.mypersonaltracker.sheet.google.TrackerWorkbook.findOrCreate(context, googleApi, progress) }
        googleSheetId = id
        prefs.edit().putString(GOOGLE_SHEET_KEY, id).apply()
        chooseWebApp(false)
        resetData()
        progress("Loading it…")
        refresh()
    }

    /**
     * A new, empty tracker sheet from the app's blueprint, used from now on. The one in use stays
     * in Drive, renamed "… (old 19 Oct 2026)", so signing in later finds the new one.
     */
    suspend fun startNewGoogleSheet(progress: (String) -> Unit = {}) {
        val old = googleSheetId
        val created = viaGoogle { com.mohammedsharukms.mypersonaltracker.sheet.google.TrackerWorkbook.create(context, googleApi, progress = progress) }
        if (old != null) {
            val stamp = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
            runCatching { googleApi.rename(old, "${com.mohammedsharukms.mypersonaltracker.sheet.google.TrackerWorkbook.TITLE} (old $stamp)") }
        }
        googleSheetId = created.id
        prefs.edit().putString(GOOGLE_SHEET_KEY, created.id).apply()
        chooseWebApp(false)
        resetData()
        progress("Loading it…")
        refresh()
    }

    /**
     * A backup in Google Drive: a copy of the sheet ("… (backup 19 Oct 2026)") and the same as an
     * Excel file in the "Life & Finance · Backups" folder. Returns the backup's name.
     */
    suspend fun backUpGoogleSheet(): String {
        val id = googleSheetId ?: throw SheetException("Sign in with Google first.")
        val stamp = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
        val name = "${com.mohammedsharukms.mypersonaltracker.sheet.google.TrackerWorkbook.TITLE} (backup $stamp)"
        return viaGoogle {
            googleApi.copy(id, name)
            val xlsx = googleApi.exportXlsx(id)
            val folder = googleApi.folder("Life & Finance · Backups")
            googleApi.upload(xlsx, "$name.xlsx", com.mohammedsharukms.mypersonaltracker.sheet.google.GoogleSheetsApi.XLSX, folder)
            name
        }
    }

    /** Forgets the sheet here (it stays in Drive) and signs out of Google. */
    suspend fun disconnectGoogle() {
        googleSheetId = null
        prefs.edit().remove(GOOGLE_SHEET_KEY).apply()
        resetData()
        googleAuth.signOut()
    }

    /** "Reset all data": a fresh workbook from the app's blueprint replaces the old one, which goes to the Drive trash. */
    private suspend fun resetGoogleSheet(): JSONObject {
        val old = googleSheetId ?: return JSONObject()
        val created = viaGoogle { com.mohammedsharukms.mypersonaltracker.sheet.google.TrackerWorkbook.create(context, googleApi) }
        googleSheetId = created.id
        prefs.edit().putString(GOOGLE_SHEET_KEY, created.id).apply()
        googleApi.trash(old)
        return JSONObject().put("resetting", false)
    }

    /** While the sheet rebuilds, check back every 15 seconds and load it the moment it's ready. */
    private fun noteSetup(progress: String) {
        setupProgress = progress
        errorMessage = null
        if (setupPoll?.isActive == true) return
        setupPoll = scope.launch {
            while (setupProgress != null) {
                delay(15_000)
                refresh()
            }
        }
    }

    private fun apply(data: JSONObject) {
        setupProgress = null
        confirmed = data
        lastSynced = System.currentTimeMillis()
        errorMessage = null
        keyRejected = false
        runCatching { cacheFile.writeText(data.toString()) }
        recompute()
    }

    /** A wrong key resets to the connect state; offline keeps the last data on screen; other failures show above it. */
    private fun handle(e: Exception) {
        if (e is BusyException) {
            noteSetup(e.progress)
            return
        }
        if (e is UnauthorizedException) {
            resetData()
            keyRejected = true
        }
        if (e is IOException) {
            isOffline = true
            errorMessage = null
        } else if (e is TransientException) {
            // Keep showing the last good data; it's tried again shortly. With nothing to show, say so.
            errorMessage = busyMessage(showingData = confirmed != null)
        } else {
            errorMessage = e.message ?: "Something went wrong."
        }
    }

    /** Forgets the sheet's data here and on disk. Settings and queued writes stay. */
    private fun resetData() {
        confirmed = null
        lastSynced = null
        keyRejected = false
        cacheFile.delete()
        recompute()
    }

    private fun loadCache() {
        runCatching {
            confirmed = JSONObject(cacheFile.readText())
            lastSynced = cacheFile.lastModified()
        }
    }

    companion object {
        const val BUSY_MESSAGE = "Google is busy, so your sheet couldn't load just now. Try again in a minute."

        /** A busy moment stays quiet over data on screen (it's tried again shortly); with nothing to show, it's said. */
        fun busyMessage(showingData: Boolean): String? = if (showingData) null else BUSY_MESSAGE

        /** Marks a quick save's reply (no snapshot). */
        private const val ACK = "__ack"
        private const val URL_KEY = "webAppURL"
        private const val API_KEY = "apiKey"
        private const val GOOGLE_SHEET_KEY = "googleSheetId"
        private const val WEB_APP_CHOSEN_KEY = "webAppChosen"
        const val UNDO_WINDOW_MS = 5_000L
        /** Your own Apps Script web app deployment (Resources/Backend), set in Settings. Empty in the public repo. */
        const val DEFAULT_URL = ""
        /** Old deployments whose saved links should be dropped. */
        private val RETIRED_URLS: Set<String> = emptySet()

        /**
         * The sheet's formula: five Y habits + water + sleep targets, out of 7. A day is "logged" once
         * office, breakfast, lunch, protein, water or screen time is in — what the phone fills in
         * (workout, sleep, steps, weight) doesn't count on its own.
         */
        fun rescore(day: SheetDay, settings: SheetSettings, inputs: MyInputData? = null): SheetDay {
            val logged = listOf(Habit.OFFICE, Habit.BREAKFAST, Habit.LUNCH, Habit.PROTEIN).any { day.answer(it) != null } ||
                day.water != null || day.screen != null || (!healthFillsWorkout(settings) && day.answer(Habit.WORKOUT) != null) ||
                (inputs?.log?.any { it.date == day.date } ?: false)
            val outOf = settings.scoreDenominator
            if (!logged || outOf <= 0) return day.copy(habitsMet = null, score = null, status = null)
            val met = activeHabits(settings).count { day.answer(it) == "Y" } + (inputs?.habitsMet(day.date) ?: 0) +
                (if (Tracker.WATER.isOn(settings) && (day.water ?: 0.0) >= (settings.waterTarget ?: Double.MAX_VALUE)) 1 else 0) +
                (if (Tracker.SLEEP.isOn(settings) && (day.sleep ?: 0.0) >= (settings.sleepTarget ?: Double.MAX_VALUE)) 1 else 0)
            val score = met / outOf
            return day.copy(habitsMet = met.toDouble(), score = score,
                status = if (score >= (settings.greatDay ?: 0.85)) "Excellent" else if (score >= 0.7) "On Track" else "Needs Work")
        }

        /** After a habit is renamed, added or turned on or off: the score's out-of and every day so far. */
        private fun rescoreAll(s: SheetSnapshot): SheetSnapshot {
            val set = s.settings.copy(scoreOutOf = s.settings.scoreOutOf(s.myInputs))
            val today = java.time.LocalDate.now().toString()
            return s.copy(settings = set, days = s.days.map { if (it.date <= today) rescore(it, set, s.myInputs) else it })
        }

        /** Applies one queued write to a snapshot, estimating the sheet's formulas. */
        fun applyLocally(write: PendingWrite, s: SheetSnapshot): SheetSnapshot {
            val p = JSONObject(write.payloadJson)
            fun d(key: String): Double? = p.dbl(key)
            fun t(key: String): String? = p.str(key)
            return when (write.action) {
                "saveHealth", "saveSteps" -> {
                    val list = p.optJSONArray("days") ?: return s
                    var days = s.days
                    for (i in 0 until list.length()) {
                        val h = list.optJSONObject(i) ?: continue
                        days = days.map { d ->
                            if (d.date != h.optString("date")) return@map d
                            var day = d
                            h.dbl("steps")?.let { if (it > (day.steps ?: 0.0)) day = day.copy(steps = it) }
                            h.dbl("sleep")?.let { if (it > 0 && day.sleep == null) day = day.copy(sleep = it) }
                            h.dbl("weight")?.let { if (it > 0 && day.weight == null) day = day.copy(weight = it) }
                            if (h.optBoolean("workout") && day.workout == null) day = day.copy(workout = "Y")
                            if (day.score != null) rescore(day, s.settings, s.myInputs) else day
                        }
                    }
                    s.copy(days = days)
                }
                "saveHabits" -> {
                    val list = p.optJSONArray("habits") ?: return s
                    if (list.length() != 5) return s
                    val items = (0 until 5).map { list.getJSONObject(it) }
                    val trackers = p.optJSONObject("trackers")
                    var set = s.settings.copy(
                        habitNames = items.map { it.optString("name").ifBlank { null } },
                        habitOn = items.map { if (it.optBoolean("on", true)) "Y" else "N" },
                        habitIcons = items.map { it.optString("icon").ifBlank { null } },
                        trackersOn = Tracker.entries.associate { t -> t.key to (if (trackers?.has(t.key) == true) (if (trackers.optBoolean(t.key)) "Y" else "N") else s.settings.trackersOn[t.key]) },
                    )
                    rescoreAll(s.copy(settings = set))
                }
                "saveInput" -> {
                    val row = MyInput.from(p) ?: return s
                    val data = s.inputData
                    rescoreAll(s.copy(myInputs = data.copy(inputs = data.inputs.replacing(row) { it.id == row.id })))
                }
                "deleteInput" -> {
                    val id = t("id") ?: return s
                    val data = s.inputData
                    rescoreAll(s.copy(myInputs = MyInputData(data.inputs.filter { it.id != id }, data.log.filter { it.id != id })))
                }
                "saveInputValues" -> {
                    val date = t("date") ?: return s
                    val values = p.optJSONObject("values") ?: return s
                    var log = s.inputData.log
                    values.keys().forEach { id ->
                        log = log.filterNot { it.date == date && it.id == id }
                        val text = values.str(id)
                        if (text != null) log = log + MyInputLog(date, id, text)
                    }
                    val data = s.inputData.copy(log = log)
                    s.copy(myInputs = data, days = s.days.map { if (it.date == date) rescore(it, s.settings, data) else it })
                }
                "saveRenewal" -> {
                    val row = Renewal.from(p)?.copy(pending = true) ?: return s
                    s.copy(renewals = s.renewals.replacing(row) { it.id == row.id })
                }
                "deleteRenewal" -> s.copy(renewals = s.renewals.filter { it.id != t("id") })
                "saveLoan" -> {
                    val row = Loan.from(p)?.copy(pending = true) ?: return s
                    s.copy(loans = s.loans.replacing(row) { it.id == row.id })
                }
                "deleteLoan" -> s.copy(loans = s.loans.filter { it.id != t("id") })
                "saveParentAsk" -> {
                    val row = ParentAsk.from(p)?.copy(pending = true) ?: return s
                    s.copy(parents = s.parents.replacing(row) { it.id == row.id })
                }
                "deleteParentAsk" -> s.copy(parents = s.parents.filter { it.id != t("id") })
                "saveParentMedicine" -> {
                    val row = ParentMedicine.from(p)?.copy(pending = true) ?: return s
                    s.copy(parentMedicines = s.parentMedicines.replacing(row) { it.id == row.id })
                }
                "deleteParentMedicine" -> s.copy(parentMedicines = s.parentMedicines.filter { it.id != t("id") })
                "saveParentDate" -> {
                    val row = ParentDate.from(p)?.copy(pending = true) ?: return s
                    s.copy(parentDates = s.parentDates.replacing(row) { it.id == row.id })
                }
                "deleteParentDate" -> s.copy(parentDates = s.parentDates.filter { it.id != t("id") })
                "saveTakeHomeItem" -> {
                    val row = TakeHomeItem.from(p)?.copy(pending = true) ?: return s
                    s.copy(takeHome = s.takeHomeData.copy(items = s.takeHomeData.items.replacing(row) { it.id == row.id }))
                }
                "deleteTakeHomeItem" -> s.copy(takeHome = s.takeHomeData.copy(items = s.takeHomeData.items.filter { it.id != t("id") }))
                "saveTakeHomeTrip" -> s.copy(takeHome = s.takeHomeData.copy(trip = t("date")))
                "saveParentReading" -> {
                    val row = ParentReading.from(p)?.copy(pending = true) ?: return s
                    s.copy(parentHealth = s.parentHealth.replacing(row) { it.id == row.id })
                }
                "deleteParentReading" -> s.copy(parentHealth = s.parentHealth.filter { it.id != t("id") })
                "saveDay" -> {
                    val date = t("date") ?: return s
                    val i = s.days.indexOfFirst { it.date == date }
                    if (i < 0) return s
                    var day = s.days[i].copy(
                        office = t("office"), breakfast = t("breakfast"), lunch = t("lunch"), protein = t("protein"), workout = t("workout"),
                        water = d("water"), sleep = d("sleep"), steps = d("steps"), weight = d("weight"), screen = d("screen"), notes = t("notes"),
                    )
                    day = rescore(day, s.settings, s.myInputs)
                    val updated = s.copy(days = s.days.toMutableList().also { it[i] = day })
                    // Targets that follow your weight move with a newly logged one (the sheet does the same).
                    val set = updated.settings
                    val weight = updated.latestWeight?.first
                    if (set.followWeight == true && weight != null && set.heightCm != null && set.birthYear != null) {
                        val goal = BodyCalc.Goal.of(set.bodyGoal) ?: BodyCalc.Goal.MAINTAIN
                        val r = BodyCalc.targets(weight, set.heightCm, java.time.LocalDate.now().year - set.birthYear.toInt(),
                            BodyCalc.Sex.of(set.sex) ?: BodyCalc.Sex.MALE, BodyCalc.Activity.of(set.activity) ?: BodyCalc.Activity.LIGHT,
                            goal, set.proteinPerKg ?: goal.proteinPerKg)
                        updated.copy(settings = set.copy(calorieTarget = r.calories, proteinTarget = r.protein, carbTarget = r.carbs, fatTarget = r.fat))
                    } else updated
                }
                "addExpense", "updateExpense" -> {
                    val row = write.tempRow ?: p.optInt("row")
                    val qar = d("qar")
                    val expense = SheetExpense(row, t("date"), t("description"), t("category"), t("method"), qar,
                        qar?.let { it * (s.settings.rate ?: 0.0) }, t("notes"))
                    val i = s.expenses.indexOfFirst { it.row == row }
                    s.copy(expenses = if (i >= 0) s.expenses.toMutableList().also { it[i] = expense } else s.expenses + expense)
                }
                "deleteExpense" -> s.copy(expenses = s.expenses.filter { it.row != p.optInt("row") })
                "saveMeal" -> {
                    val id = t("mealId") ?: return s
                    val date = t("date") ?: return s
                    val meal = t("meal") ?: return s
                    val food = s.foodData
                    val old = food.log.firstOrNull { it.mealId == id }
                    val items = p.optJSONArray("items") ?: JSONArray()
                    val photo = if (p.has("photo")) "local" else if (p.optBoolean("removePhoto")) null else old?.photo
                    val entries = (0 until items.length()).map { n ->
                        val i = items.getJSONObject(n)
                        FoodEntry(-1 - n, id, date, t("time"), meal, i.optString("food"), i.dbl("amount"), i.str("unit"),
                            i.dbl("calories"), i.dbl("protein"), i.dbl("carbs"), i.dbl("fat"),
                            if (n == 0) d("cost") else null, if (n == 0) photo else null, if (n == 0) old?.ledgerRow else null,
                            if (n == 0) t("notes") else null)
                    }
                    s.copy(food = food.copy(log = food.log.filter { it.mealId != id } + entries))
                }
                "deleteMeal" -> {
                    val id = t("mealId") ?: return s
                    s.copy(food = s.foodData.copy(log = s.foodData.log.filter { it.mealId != id }))
                }
                "savePlan" -> {
                    val date = t("date") ?: return s
                    val meal = t("meal") ?: return s
                    val items = p.optJSONArray("items") ?: JSONArray()
                    val planned = (0 until items.length()).map { n ->
                        val i = items.getJSONObject(n)
                        PlanItem(-1 - n, date, meal, i.optString("food"), i.dbl("amount"), i.str("unit"), i.dbl("calories"), i.dbl("protein"),
                            i.dbl("carbs"), i.dbl("fat"), i.dbl("cost"), p.optBoolean("eaten"))
                    }
                    s.copy(food = s.foodData.copy(plan = s.foodData.plan.filterNot { it.date == date && it.meal == meal } + planned))
                }
                "savePeopleEntry" -> {
                    val id = t("id") ?: return s
                    val type = PeopleEntry.Kind.of(t("type")) ?: return s
                    s.copy(people = s.people.filter { it.id != id } +
                        PeopleEntry(id, t("date") ?: SheetDate.today, t("person") ?: "", type, d("amount") ?: 0.0, t("note"), pending = true))
                }
                "deletePeopleEntry" -> s.copy(people = s.people.filter { it.id != t("id") })
                "saveStudySubject" -> {
                    val row = StudyJson.subject(p) ?: return s
                    val st = s.studyData
                    s.copy(study = st.copy(
                        subjects = st.subjects.replacing(row) { it.id == row.id },
                        sessions = st.sessions.map { if (it.subjectId == row.id) it.copy(subject = row.name) else it },
                    ))
                }
                "deleteStudySubject" -> {
                    val id = t("id") ?: return s
                    val st = s.studyData
                    s.copy(study = st.copy(subjects = st.subjects.filter { it.id != id }, topics = st.topics.filter { it.subjectId != id },
                        goals = st.goals.filter { it.subjectId != id }))
                }
                "saveStudyTopic" -> {
                    val row = StudyJson.topic(p) ?: return s
                    val st = s.studyData
                    s.copy(study = st.copy(
                        topics = st.topics.replacing(row) { it.id == row.id },
                        sessions = st.sessions.map { if (it.topicId == row.id) it.copy(topic = row.topic) else it },
                    ))
                }
                "deleteStudyTopic" -> s.copy(study = s.studyData.let { st -> st.copy(topics = st.topics.filter { it.id != t("id") }) })
                "saveStudySession" -> {
                    val st = s.studyData
                    val parsed = StudyJson.entry(p) ?: return s
                    val row = parsed.copy(pending = true, subject = st.subject(parsed.subjectId)?.name ?: parsed.subject,
                        topic = st.topic(parsed.topicId)?.topic ?: parsed.topic)
                    s.copy(study = st.copy(sessions = st.sessions.replacing(row) { it.id == row.id }))
                }
                "deleteStudySession" -> s.copy(study = s.studyData.let { st -> st.copy(sessions = st.sessions.filter { it.id != t("id") }) })
                "saveStudyGoal" -> {
                    val row = StudyJson.goal(p) ?: return s
                    s.copy(study = s.studyData.let { st -> st.copy(goals = st.goals.replacing(row) { it.id == row.id }) })
                }
                "saveSetAside" -> {
                    val row = SetAsideEntry.from(p)?.copy(pending = true) ?: return s
                    s.copy(setAside = s.setAside.replacing(row) { it.id == row.id })
                }
                "deleteSetAside" -> s.copy(setAside = s.setAside.filter { it.id != t("id") })
                "saveLifeGoal" -> {
                    val parsed = LifeGoalJson.goal(p) ?: return s
                    val today = java.time.LocalDate.now().toString()
                    val row = parsed.copy(
                        pending = true, updated = today,
                        doneOn = if (parsed.status == GoalStatus.REACHED) parsed.doneOn ?: today else null,
                        parentId = if (parsed.horizon == GoalHorizon.LONG) null else parsed.parentId,
                    )
                    s.copy(lifeGoals = s.lifeGoalData.let { d -> d.copy(goals = d.goals.replacing(row) { it.id == row.id }) })
                }
                "deleteLifeGoal" -> {
                    val id = t("id")
                    s.copy(lifeGoals = s.lifeGoalData.let { d ->
                        d.copy(goals = d.goals.filter { it.id != id }.map { if (it.parentId == id) it.copy(parentId = null) else it },
                            steps = d.steps.filter { it.goalId != id })
                    })
                }
                "saveGoalStep" -> {
                    val parsed = LifeGoalJson.step(p) ?: return s
                    val today = java.time.LocalDate.now().toString()
                    val row = parsed.copy(pending = true, doneOn = if (parsed.done) parsed.doneOn ?: today else null)
                    s.copy(lifeGoals = s.lifeGoalData.let { d ->
                        d.copy(steps = d.steps.replacing(row) { it.id == row.id },
                            goals = d.goals.map { if (it.id == row.goalId) it.copy(updated = today) else it })
                    })
                }
                "deleteGoalStep" -> s.copy(lifeGoals = s.lifeGoalData.let { d -> d.copy(steps = d.steps.filter { it.id != t("id") }) })
                "saveDayPlan" -> {
                    val row = PlannerJson.plan(p)?.copy(pending = true) ?: return s
                    s.copy(planner = s.plannerData.let { pl -> pl.copy(plans = pl.plans.replacing(row) { it.id == row.id }) })
                }
                "deleteDayPlan" -> s.copy(planner = s.plannerData.let { pl -> pl.copy(plans = pl.plans.filter { it.id != t("id") }) })
                "saveOutfit" -> {
                    val row = PlannerJson.outfit(p)?.copy(pending = true) ?: return s
                    s.copy(planner = s.plannerData.let { pl -> pl.copy(outfits = pl.outfits.replacing(row) { it.id == row.id }) })
                }
                "deleteOutfit" -> s.copy(planner = s.plannerData.let { pl -> pl.copy(outfits = pl.outfits.filter { it.id != t("id") }) })
                "saveClothing" -> {
                    val row = PlannerJson.clothing(p)?.copy(pending = true) ?: return s
                    s.copy(planner = s.plannerData.let { pl -> pl.copy(wardrobe = pl.wardrobe.replacing(row) { it.id == row.id }) })
                }
                "deleteClothing" -> s.copy(planner = s.plannerData.let { pl -> pl.copy(wardrobe = pl.wardrobe.filter { it.id != t("id") }) })
                "saveSavingsGoal" -> {
                    val row = SavingsGoal.from(p) ?: return s
                    s.copy(savingsGoals = s.savingsGoals.replacing(row) { it.id == row.id })
                }
                "deleteSavingsGoal" -> {
                    val id = t("id")
                    s.copy(savingsGoals = s.savingsGoals.filter { it.id != id },
                        setAside = s.setAside.map { if (it.goalId == id) it.copy(goalId = null) else it })
                }
                "saveShoppingItem" -> {
                    val parsed = ShoppingJson.item(p) ?: return s
                    val shop = s.shoppingData
                    val old = shop.items.firstOrNull { it.id == parsed.id }
                    val row = parsed.copy(pending = true,
                        boughtOn = if (parsed.status == ShoppingItem.Status.BOUGHT) old?.boughtOn else null,
                        tripId = if (parsed.status == ShoppingItem.Status.BOUGHT) old?.tripId else null)
                    s.copy(shopping = shop.copy(items = shop.items.replacing(row) { it.id == row.id }))
                }
                "deleteShoppingItem" -> s.copy(shopping = s.shoppingData.let { sh -> sh.copy(items = sh.items.filter { it.id != t("id") }) })
                "checkoutShopping" -> {
                    val tripId = t("tripId") ?: return s
                    val date = t("date") ?: return s
                    val lines = p.optJSONArray("items") ?: JSONArray()
                    val prices = (0 until lines.length()).associate { lines.getJSONObject(it).let { l -> l.optString("id") to l.dbl("price") } }
                    val shop = s.shoppingData
                    val items = shop.items.map { i ->
                        if (i.id in prices) i.copy(status = ShoppingItem.Status.BOUGHT, boughtOn = date, tripId = tripId, price = prices[i.id] ?: i.price) else i
                    }
                    val trip = ShoppingTrip(tripId, date, t("store"), lines.length(), d("total"), t("method"), t("notes"), pending = true)
                    s.copy(shopping = shop.copy(items = items, trips = shop.trips.filter { it.id != tripId } + trip))
                }
                "deleteShoppingTrip" -> {
                    val id = t("tripId") ?: return s
                    s.copy(shopping = s.shoppingData.let { sh -> sh.copy(items = sh.items.filter { it.tripId != id }, trips = sh.trips.filter { it.id != id }) })
                }
                "deleteStudyGoal" -> s.copy(study = s.studyData.let { st -> st.copy(goals = st.goals.filter { it.id != t("id") }) })
                "saveSalary" -> {
                    val month = t("month") ?: return s
                    val i = s.salary.indexOfFirst { it.month == month }
                    if (i < 0) return s
                    val m = s.salary[i]
                    val salary = d("salary"); val other = d("otherIncome"); val remit = d("remittanceQar")
                    val income = (salary ?: 0.0) + (other ?: 0.0)
                    val net = income - (m.expenses ?: 0.0) - (remit ?: 0.0)
                    val updated = m.copy(salary = salary, otherIncome = other, remittanceQar = remit, totalIncome = income,
                        netSavings = net, savingsRate = if (income > 0) net / income else null)
                    s.copy(salary = s.salary.toMutableList().also { it[i] = updated })
                }
                else -> s
            }
        }
    }
}

/** The list with [item] in place of the first match, or added at the end. */
private fun <T> List<T>.replacing(item: T, match: (T) -> Boolean): List<T> {
    val i = indexOfFirst(match)
    return if (i >= 0) toMutableList().also { it[i] = item } else this + item
}

/** One write waiting for the sheet; the payload is kept as JSON so the queue survives restarts. */
data class PendingWrite(
    val id: String,
    val action: String,
    val payloadJson: String,
    val summary: String,
    val createdAt: Long,
    /** Held until then (deletes wait for the Undo window). */
    val notBefore: Long?,
    /** The placeholder row a queued add shows under until the sheet assigns one. */
    val tempRow: Int?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("action", action).put("payload", payloadJson).put("summary", summary).put("createdAt", createdAt)
        .put("notBefore", notBefore ?: JSONObject.NULL).put("tempRow", tempRow ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = PendingWrite(
            o.getString("id"), o.getString("action"), o.getString("payload"), o.optString("summary"), o.optLong("createdAt"),
            if (o.isNull("notBefore")) null else o.getLong("notBefore"),
            if (o.isNull("tempRow")) null else o.getInt("tempRow"),
        )
    }
}

open class SheetException(message: String) : Exception(message)

/** The sheet is being set up or reset; try again in a minute. */
class BusyException(val progress: String) : SheetException(
    "Your sheet is being set up — $progress. This takes a few minutes; pull down to refresh.",
)

/** Google or the sheet couldn't take it just now (busy, quota, timeout): retried, never dropped. */
class TransientException(detail: String) : SheetException("Your sheet didn't answer just now ($detail). Your changes are kept and sent again shortly.") {
    companion object {
        /** Apps Script's wording for "try again later", as opposed to "this change is wrong". */
        fun isTemporary(error: String): Boolean {
            val e = error.lowercase()
            return listOf("lock timeout", "too many times", "timed out", "exceeded maximum execution time", "internal error",
                "service unavailable", "try again", "server error", "rate limit", "temporarily").any { it in e }
        }
    }
}

/** The Google sign-in ended (revoked, or expired): queued changes wait for the next sign-in. */
class SignedOutException : SheetException("Sign in with Google again. Your changes are kept and sent once you do.")

class UnauthorizedException : SheetException(
    "The API key is wrong or was replaced. Run createApiKey in the sheet's Apps Script and paste the new key in Settings.",
)

/** Which sheet the app talks to. The web app only when chosen: a key left saved (after signing out of Google) doesn't switch to it. */
enum class SheetConnection {
    GOOGLE, WEB_APP, NONE;

    companion object {
        fun of(googleSheet: Boolean, signedIn: Boolean, webAppChosen: Boolean, webAppUrl: String, apiKey: String): SheetConnection = when {
            googleSheet -> if (signedIn) GOOGLE else NONE
            webAppChosen && webAppUrl.startsWith("https://") && apiKey.isNotEmpty() -> WEB_APP
            else -> NONE
        }

        /** Before this choice was saved, a key meant "use the web app". Kept only where it had loaded on this install. */
        fun chosenOnUpgrade(googleSheet: Boolean, hasKey: Boolean, hadData: Boolean) = !googleSheet && hasKey && hadData
    }
}
