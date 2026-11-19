package com.mohammedsharukms.mypersonaltracker.sheet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

// The food side of the sheet (Food Log, Meal Plan, My Foods), the built-in food
// list, and meal photos kept in Google Drive with a cache on the phone.
// Mirrors FoodModels.swift.

data class FoodData(
    val log: List<FoodEntry> = emptyList(),
    val plan: List<PlanItem> = emptyList(),
    val myFoods: List<MyFood> = emptyList(),
    /** Set when meal photos can't be saved to Drive yet (needs one-time approval). */
    val photoError: String? = null,
)

/** One food eaten: a Food Log row. Rows with the same mealId are one meal. */
data class FoodEntry(
    val row: Int, val mealId: String, val date: String, val time: String?, val meal: String, val food: String,
    val amount: Double?, val unit: String?, val calories: Double?, val protein: Double?, val carbs: Double?, val fat: Double?,
    /** Meal-level fields, on the meal's first row only. */
    val cost: Double?, val photo: String?, val ledgerRow: Int?, val notes: String?,
)

data class PlanItem(
    val row: Int, val date: String, val meal: String, val food: String, val amount: Double?, val unit: String?,
    val calories: Double?, val protein: Double?, val carbs: Double?, val fat: Double?, val cost: Double?, val eaten: Boolean,
) {
    val asItem: FoodItem get() = FoodItem.of(food, amount ?: 1.0, unit ?: "serving", calories ?: 0.0, protein ?: 0.0, carbs ?: 0.0, fat ?: 0.0, cost)
}

data class MyFood(
    val row: Int? = null, val name: String, val amount: Double?, val unit: String?, val calories: Double?, val protein: Double?,
    val carbs: Double?, val fat: Double?, val cost: Double?,
)

enum class MealType(val label: String) {
    BREAKFAST("Breakfast"), LUNCH("Lunch"), DINNER("Dinner"), SNACK("Snack");

    companion object {
        fun of(label: String?) = entries.firstOrNull { it.label == label } ?: SNACK

        /** The meal people usually mean at this time of day. */
        fun forNow(): MealType = when (java.time.LocalTime.now().hour) {
            in 4..10 -> BREAKFAST
            in 11..15 -> LUNCH
            in 18..22 -> DINNER
            else -> SNACK
        }
    }
}

/**
 * A food with an amount, as edited in a meal or plan. Nutrition is for the
 * whole amount; the per-unit values let a new amount rescale everything.
 */
data class FoodItem(
    val id: String = UUID.randomUUID().toString(),
    val food: String, val amount: Double, val unit: String,
    val calories: Double, val protein: Double, val carbs: Double, val fat: Double, val cost: Double?,
    private val perCalories: Double, private val perProtein: Double, private val perCarbs: Double,
    private val perFat: Double, private val perCost: Double?,
    /** The food's own serving unit ("cup") and its weight in g / ml, when known — what lets you switch to "250 ml". */
    val servingUnit: String = unit,
    val servingGrams: Double? = null,
) {
    /** Units this food can be shown in: its serving, and g / ml when its weight is known. */
    val unitChoices: List<String> get() {
        val out = mutableListOf<String>()
        if (!isWeight(servingUnit)) out += servingUnit
        if (servingGrams != null || isWeight(unit)) out += listOf("g", "ml")
        return out.ifEmpty { listOf(unit) }
    }

    private fun gramsPer(u: String): Double? = if (isWeight(u)) 1.0 else if (u == servingUnit) servingGrams else null

    /** The same food in another unit: "1 cup" → "240 ml", nutrition unchanged. */
    fun withUnit(new: String): FoodItem {
        val from = gramsPer(unit) ?: return this
        val to = gramsPer(new)?.takeIf { it > 0 } ?: return this
        if (new == unit) return this
        val factor = to / from
        val converted = amount / factor
        return copy(unit = new, perCalories = perCalories * factor, perProtein = perProtein * factor, perCarbs = perCarbs * factor,
            perFat = perFat * factor, perCost = perCost?.let { it * factor })
            .withAmount(if (isWeight(new)) Math.round(converted).toDouble() else Math.round(converted * 100) / 100.0)
    }

    /** "250 ml", "2", "1.5 cup": switches unit when it can, then sets the amount. */
    fun withQuantity(q: FoodQuantity): FoodItem {
        var item = this
        q.unit?.let { wanted ->
            val match = unitChoices.firstOrNull { it.equals(wanted, ignoreCase = true) }
                ?: if (isWeight(wanted)) unitChoices.firstOrNull { isWeight(it) } else null
            if (match != null) item = item.withUnit(match)
        }
        return item.withAmount(q.amount)
    }

    fun withAmount(value: Double): FoodItem {
        val a = maxOf(0.0, value)
        return copy(amount = a, calories = perCalories * a, protein = perProtein * a, carbs = perCarbs * a, fat = perFat * a, cost = perCost?.let { it * a })
    }

    /**
     * Your own numbers for this amount (the list's values are averages). They
     * become this food's per-unit values, so a new amount scales from them.
     */
    fun withNutrition(calories: Double? = null, protein: Double? = null, carbs: Double? = null, fat: Double? = null): FoodItem {
        val c = maxOf(0.0, calories ?: this.calories)
        val p = maxOf(0.0, protein ?: this.protein)
        val cb = maxOf(0.0, carbs ?: this.carbs)
        val f = maxOf(0.0, fat ?: this.fat)
        val a = maxOf(amount, 0.0001)
        return copy(calories = c, protein = p, carbs = cb, fat = f, perCalories = c / a, perProtein = p / a, perCarbs = cb / a, perFat = f / a)
    }

    /** Amount steps that make sense for the unit: 50 g, 0.5 cup, 1 piece. */
    val step: Double get() = when (unit.lowercase()) {
        "g", "ml" -> if (amount >= 100) 50.0 else 10.0
        "cup", "bowl", "plate", "glass", "tbsp", "scoop", "fruit" -> 0.5
        else -> 1.0
    }

    fun toJson(): JSONObject = JSONObject().put("food", food).put("amount", amount).put("unit", unit)
        .put("calories", r1(calories)).put("protein", r1(protein)).put("carbs", r1(carbs)).put("fat", r1(fat))
        .apply { if (cost != null) put("cost", r1(cost)) }

    private fun r1(v: Double) = Math.round(v * 10) / 10.0

    companion object {
        /** g and ml count as the same for food (1 ml of milk ≈ 1 g). */
        fun isWeight(unit: String) = unit.lowercase() in setOf("g", "ml")

        /** [grams] is the weight of the whole [amount] (e.g. 240 for "1 cup" of milk). */
        fun of(food: String, amount: Double, unit: String, calories: Double, protein: Double, carbs: Double, fat: Double, cost: Double? = null,
               grams: Double? = null): FoodItem {
            val a = maxOf(amount, 0.0001)
            return FoodItem(food = food, amount = amount, unit = unit, calories = calories, protein = protein, carbs = carbs, fat = fat, cost = cost,
                perCalories = calories / a, perProtein = protein / a, perCarbs = carbs / a, perFat = fat / a, perCost = cost?.let { it / a },
                servingUnit = unit, servingGrams = if (isWeight(unit)) 1.0 else grams?.let { it / a })
        }
    }
}

data class NutritionTotals(val calories: Double = 0.0, val protein: Double = 0.0, val carbs: Double = 0.0, val fat: Double = 0.0) {
    companion object {
        fun ofEntries(e: List<FoodEntry>) = NutritionTotals(e.sumOf { it.calories ?: 0.0 }, e.sumOf { it.protein ?: 0.0 }, e.sumOf { it.carbs ?: 0.0 }, e.sumOf { it.fat ?: 0.0 })
        fun ofItems(i: List<FoodItem>) = NutritionTotals(i.sumOf { it.calories }, i.sumOf { it.protein }, i.sumOf { it.carbs }, i.sumOf { it.fat })
        fun ofPlan(p: List<PlanItem>) = NutritionTotals(p.sumOf { it.calories ?: 0.0 }, p.sumOf { it.protein ?: 0.0 }, p.sumOf { it.carbs ?: 0.0 }, p.sumOf { it.fat ?: 0.0 })
    }
}

/** A logged meal: its Food Log rows grouped. */
data class Meal(val id: String, val date: String, val time: String?, val type: MealType, val entries: List<FoodEntry>) {
    val cost: Double? get() = entries.firstOrNull()?.cost
    val photo: String? get() = entries.firstOrNull()?.photo
    val notes: String? get() = entries.firstOrNull()?.notes
    val hasLedgerExpense: Boolean get() = entries.firstOrNull()?.ledgerRow != null
    val totals: NutritionTotals get() = NutritionTotals.ofEntries(entries)
    val isPending: Boolean get() = entries.any { it.row < 0 }

    companion object {
        fun group(log: List<FoodEntry>): List<Meal> = log.groupBy { it.mealId }.map { (id, e) ->
            val first = e.first()
            Meal(id, first.date, first.time, MealType.of(first.meal), e.sortedBy { it.row })
        }.sortedWith(compareBy({ it.date }, { it.time ?: "" }, { it.id }))
    }
}

val SheetSnapshot.foodData: FoodData get() = food ?: FoodData()
fun SheetSnapshot.meals(on: String): List<Meal> = Meal.group(foodData.log.filter { it.date == on })

// MARK: - Parsing

object FoodJson {
    fun parse(o: JSONObject?): FoodData? {
        if (o == null) return null
        return FoodData(
            log = o.optJSONArray("log").objs().map { e ->
                FoodEntry(e.dbl("row")?.toInt() ?: 0, e.str("mealId") ?: "", e.str("date") ?: "", e.str("time"), e.str("meal") ?: "Snack",
                    e.str("food") ?: "", e.dbl("amount"), e.str("unit"), e.dbl("calories"), e.dbl("protein"), e.dbl("carbs"), e.dbl("fat"),
                    e.dbl("cost"), e.str("photo"), e.dbl("ledgerRow")?.toInt(), e.str("notes"))
            },
            plan = o.optJSONArray("plan").objs().map { p ->
                PlanItem(p.dbl("row")?.toInt() ?: 0, p.str("date") ?: "", p.str("meal") ?: "Snack", p.str("food") ?: "", p.dbl("amount"),
                    p.str("unit"), p.dbl("calories"), p.dbl("protein"), p.dbl("carbs"), p.dbl("fat"), p.dbl("cost"), p.optBoolean("eaten"))
            },
            myFoods = o.optJSONArray("myFoods").objs().map { f ->
                MyFood(f.dbl("row")?.toInt(), f.str("name") ?: "", f.dbl("amount"), f.str("unit"), f.dbl("calories"), f.dbl("protein"),
                    f.dbl("carbs"), f.dbl("fat"), f.dbl("cost"))
            },
            photoError = o.str("photoError"),
        )
    }

    private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}

// MARK: - Food search

/** Something that can be added to a meal: a built-in food, one of yours, or one eaten recently. */
data class FoodRef(
    val id: String, val name: String, val detail: String?, val amount: Double, val unit: String,
    val calories: Double, val protein: Double, val carbs: Double, val fat: Double, val cost: Double?, val mine: MyFood? = null,
    /** Weight of [amount] in g / ml, when known. */
    val grams: Double? = null,
    /** An amount typed with the search ("milk 250ml"), applied when it's added. */
    val wanted: FoodQuantity? = null,
) {
    val item: FoodItem get() = FoodItem.of(name, amount, unit, calories, protein, carbs, fat, cost, grams).let { i -> wanted?.let { i.withQuantity(it) } ?: i }
}

/** An amount typed with a food: "250 ml", "2", "1.5 cups" (numbers only). */
data class FoodQuantity(val amount: Double, val unit: String?) {
    companion object {
        private val units: List<Triple<Set<String>, String, Double>> = listOf(
            Triple(setOf("ml", "millilitre", "millilitres", "milliliter", "milliliters"), "ml", 1.0),
            Triple(setOf("l", "ltr", "litre", "litres", "liter", "liters"), "ml", 1000.0),
            Triple(setOf("g", "gm", "gms", "gram", "grams", "gr"), "g", 1.0),
            Triple(setOf("kg", "kgs", "kilo", "kilos"), "g", 1000.0),
            Triple(setOf("cup", "cups"), "cup", 1.0), Triple(setOf("glass", "glasses"), "glass", 1.0), Triple(setOf("bowl", "bowls"), "bowl", 1.0),
            Triple(setOf("piece", "pieces", "pc", "pcs"), "piece", 1.0), Triple(setOf("slice", "slices"), "slice", 1.0),
            Triple(setOf("plate", "plates"), "plate", 1.0), Triple(setOf("tbsp", "tablespoon", "tablespoons"), "tbsp", 1.0),
            Triple(setOf("tsp", "teaspoon", "teaspoons"), "tsp", 1.0), Triple(setOf("scoop", "scoops"), "scoop", 1.0),
            Triple(setOf("serving", "servings"), "serving", 1.0),
        )
        private val pattern = Regex("""(?i)(?:^|\s)(\d+(?:[.,]\d+)?)\s*([a-z]+)?(?=\s|$)""")

        /** Splits "milk 250ml" / "250 ml milk" / "2 eggs" into the food words and the quantity. */
        fun split(text: String): Pair<String, FoodQuantity?> {
            val m = pattern.find(text) ?: return text to null
            val number = m.groupValues[1].replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 } ?: return text to null
            val word = m.groups[2]?.value?.lowercase()
            val known = word?.let { w -> units.firstOrNull { w in it.first } }
            val q = if (known != null) FoodQuantity(number * known.third, known.second) else FoodQuantity(number, null)
            val range = if (known != null) m.range else m.groups[1]!!.range
            val name = text.removeRange(range).split(" ").filter { it.isNotBlank() }.joinToString(" ")
            return name to q
        }
    }
}

/** The ~140 common foods shipped with the app (assets/foods.json), plus searching. */
object FoodDatabase {
    @Volatile private var cached: List<FoodRef>? = null

    fun common(context: Context): List<FoodRef> = cached ?: runCatching {
        val array = JSONArray(context.assets.open("foods.json").bufferedReader().use { it.readText() })
        (0 until array.length()).map { i ->
            val f = array.getJSONObject(i)
            FoodRef("common-${f.getString("name")}", f.getString("name"), f.optString("group"), f.getDouble("amount"), f.getString("unit"),
                f.getDouble("calories"), f.getDouble("protein"), f.getDouble("carbs"), f.getDouble("fat"), null,
                grams = f.optDouble("grams").takeIf { !it.isNaN() })
        }
    }.getOrDefault(emptyList()).also { cached = it }

    fun mine(foods: List<MyFood>): List<FoodRef> = foods.map {
        FoodRef("mine-${it.row ?: it.name}", it.name, "My food", it.amount ?: 1.0, it.unit ?: "serving", it.calories ?: 0.0,
            it.protein ?: 0.0, it.carbs ?: 0.0, it.fat ?: 0.0, it.cost, it)
    }

    /** Foods eaten recently, most frequent first — one tap to log again. */
    fun recent(log: List<FoodEntry>, limit: Int = 12, common: List<FoodRef> = emptyList()): List<FoodRef> = log.groupBy { it.food.lowercase() }.values
        .sortedWith(compareByDescending<List<FoodEntry>> { it.size }.thenByDescending { g -> g.maxOf { it.date } })
        .take(limit)
        .map { g ->
            val e = g.maxBy { it.date }
            // A common food logged in its usual unit still knows its weight.
            val known = common.firstOrNull { it.name.equals(e.food, ignoreCase = true) && it.unit == e.unit }
            FoodRef("recent-${e.food}", e.food, "Eaten ${g.size}×", e.amount ?: 1.0, e.unit ?: "serving", e.calories ?: 0.0,
                e.protein ?: 0.0, e.carbs ?: 0.0, e.fat ?: 0.0, null,
                grams = known?.grams?.let { it / maxOf(known.amount, 0.0001) * (e.amount ?: 1.0) })
        }

    /** Matches every word of the query, best (starts-with) first. */
    fun filter(foods: List<FoodRef>, query: String): List<FoodRef> {
        val words = query.lowercase().split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return foods
        // "eggs" finds "Egg, boiled", "chapatis" finds "Chapati".
        fun matches(name: String, w: String) = w in name || (w.endsWith("es") && w.dropLast(2) in name) ||
            (w.endsWith("s") && w.length > 3 && w.dropLast(1) in name)
        return foods.filter { f -> words.all { matches(f.name.lowercase(), it) } }
            .sortedWith(compareBy({ if (it.name.lowercase().startsWith(words[0])) 0 else 1 }, { it.name }))
    }
}

// MARK: - Meal photos

/** Meal photos: kept in Google Drive by the sheet, cached on the phone; a photo taken offline shows from its local copy. */
object MealPhotos {
    private fun folder(context: Context) = File(context.filesDir, "meal-photos").apply { mkdirs() }
    fun localFile(context: Context, mealId: String) = File(folder(context), "local-$mealId.jpg")
    private fun cacheFile(context: Context, id: String, thumb: Boolean) = File(folder(context), "$id-${if (thumb) "t" else "f"}.jpg")

    /** Shrinks a photo for upload: about 1280 px on the long side, JPEG, upright. */
    fun prepare(context: Context, uri: Uri): ByteArray? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rotation = when (ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val scale = minOf(1f, 1280f / maxOf(bitmap.width, bitmap.height))
        if (rotation != 0f || scale < 1f) {
            val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        }
        ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 72, out); out.toByteArray() }
    }.getOrNull()

    fun saveLocal(context: Context, data: ByteArray, mealId: String) { runCatching { localFile(context, mealId).writeBytes(data) } }

    /** The photo for a meal: its local copy, the cache, or downloaded from the sheet. */
    suspend fun bitmap(context: Context, store: SheetStore, meal: Meal, thumb: Boolean): Bitmap? {
        localFile(context, meal.id).takeIf { it.exists() }?.let { return BitmapFactory.decodeFile(it.path) }
        val id = meal.photo?.takeIf { it.isNotEmpty() && it != "local" } ?: return null
        val cached = cacheFile(context, id, thumb)
        if (cached.exists()) return BitmapFactory.decodeFile(cached.path)
        val data = store.photo(id, thumb) ?: return null
        runCatching { cached.writeBytes(data) }
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }

    fun base64(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)
}
