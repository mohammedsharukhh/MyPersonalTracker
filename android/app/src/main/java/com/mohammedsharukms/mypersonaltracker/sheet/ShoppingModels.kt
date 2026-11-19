package com.mohammedsharukms.mypersonaltracker.sheet

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

// The grocery list as the sheet keeps it (20_Shopping.gs): items to buy, what
// went in the cart, and every finished trip with what it cost. Mirrors ShoppingModels.swift.

data class ShoppingData(val items: List<ShoppingItem> = emptyList(), val trips: List<ShoppingTrip> = emptyList())

val SheetSnapshot.shoppingData: ShoppingData get() = shopping ?: ShoppingData()

data class ShoppingItem(
    val id: String = UUID.randomUUID().toString(),
    val item: String,
    /** As typed: "2 L", "5 kg", "30". */
    val quantity: String? = null,
    val category: String = ShoppingAisle.guess(item),
    val status: Status = Status.NEED,
    val price: Double? = null,
    val added: String? = LocalDate.now().toString(),
    val boughtOn: String? = null,
    val tripId: String? = null,
    val fromPlan: Boolean = false,
    val notes: String? = null,
    val pending: Boolean = false,
) {
    enum class Status(val label: String) {
        NEED("Need"), IN_CART("In cart"), BOUGHT("Bought");
        companion object { fun of(label: String?) = entries.firstOrNull { it.label == label } }
    }

    val label: String get() = listOfNotNull(item, quantity).joinToString(" · ")

    fun toJson(): JSONObject = JSONObject().put("id", id).put("item", item).put("quantity", quantity ?: "").put("category", category)
        .put("status", status.label).put("price", price ?: "").put("added", added ?: "").put("fromPlan", fromPlan).put("notes", notes ?: "")
}

data class ShoppingTrip(
    val id: String, val date: String, val store: String?, val items: Int, val total: Double?, val method: String?, val notes: String?,
    val pending: Boolean = false,
)

/** Aisles, in the order you walk a shop, and a guess from the item's name. */
object ShoppingAisle {
    val all = listOf("Vegetables", "Fruits", "Dairy & Eggs", "Meat & Fish", "Grains & Bread", "Spices & Oils",
        "Snacks & Drinks", "Frozen", "Household", "Personal care", "Other")

    private val words = listOf(
        "Vegetables" to listOf("onion", "tomato", "potato", "carrot", "spinach", "palak", "cucumber", "capsicum", "pepper", "garlic", "ginger",
            "chilli", "chili", "coriander", "mint", "cabbage", "cauliflower", "beans", "peas", "brinjal", "okra", "bhindi",
            "lettuce", "broccoli", "mushroom", "beetroot", "radish", "lemon", "lime", "pumpkin", "gourd", "veg"),
        "Fruits" to listOf("apple", "banana", "orange", "mango", "grape", "papaya", "pineapple", "watermelon", "melon", "pomegranate",
            "kiwi", "pear", "berry", "berries", "dates", "guava", "fruit", "avocado"),
        "Dairy & Eggs" to listOf("milk", "egg", "curd", "yogurt", "yoghurt", "laban", "cheese", "paneer", "butter", "ghee", "cream", "labneh"),
        "Meat & Fish" to listOf("chicken", "mutton", "beef", "lamb", "fish", "prawn", "shrimp", "meat", "tuna", "salmon", "keema"),
        "Grains & Bread" to listOf("rice", "atta", "flour", "maida", "bread", "roti", "khubz", "oats", "pasta", "noodle", "dal", "lentil",
            "chana", "rajma", "poha", "rava", "sooji", "semolina", "cereal", "cornflakes", "quinoa", "besan"),
        "Spices & Oils" to listOf("oil", "salt", "sugar", "masala", "turmeric", "haldi", "jeera", "cumin", "spice", "ketchup", "sauce",
            "vinegar", "honey", "jam", "pickle", "mayonnaise", "mustard", "tea", "coffee"),
        "Snacks & Drinks" to listOf("biscuit", "chips", "chocolate", "juice", "water", "soda", "cola", "snack", "nuts", "almond", "cashew",
            "peanut", "namkeen", "cake", "cookie", "drink"),
        "Frozen" to listOf("frozen", "ice cream", "nuggets", "fries"),
        "Household" to listOf("detergent", "soap", "dishwash", "tissue", "toilet", "cleaner", "bin bag", "garbage", "foil", "cling",
            "sponge", "bulb", "battery", "batteries", "napkin", "liquid"),
        "Personal care" to listOf("shampoo", "toothpaste", "toothbrush", "deodorant", "razor", "lotion", "facewash", "sanitary", "pad",
            "conditioner", "cream", "body wash"),
    )

    fun guess(name: String): String {
        val n = name.lowercase()
        return words.firstOrNull { (_, list) -> list.any { it in n } }?.first ?: "Other"
    }
}

/** "milk 2L", "rice 5 kg", "30 eggs" → the item and the quantity as written. */
object ShoppingParse {
    private val units = setOf("kg", "kgs", "g", "gm", "gms", "gram", "grams", "l", "ltr", "litre", "litres", "liter", "liters", "ml",
        "pc", "pcs", "piece", "pieces", "pack", "packs", "packet", "packets", "dozen", "doz", "bottle", "bottles", "can", "cans",
        "box", "boxes", "bunch", "bunches", "loaf", "loaves", "tray", "trays", "bag", "bags", "jar", "jars", "tin", "tins")
    private val pattern = Regex("""(?i)(?:^|\s)(\d+(?:[.,]\d+)?)\s*([a-z]+)?(?=\s|$)""")

    fun split(text: String): Pair<String, String?> {
        val trimmed = text.trim()
        val m = pattern.find(trimmed)
        var item = trimmed
        var quantity: String? = null
        if (m != null) {
            val number = m.groupValues[1]
            val unit = m.groups[2]?.value?.lowercase()
            val range = if (unit != null && unit in units) m.range else m.groups[1]!!.range
            quantity = if (unit != null && unit in units) "$number ${if (unit == "l" || unit == "ltr") "L" else unit}" else number
            item = trimmed.removeRange(range).split(" ").filter { it.isNotBlank() }.joinToString(" ")
            if (item.isEmpty()) { item = trimmed; quantity = null }
        }
        return item.replaceFirstChar { it.uppercase() } to quantity
    }
}

val ShoppingData.toBuy: List<ShoppingItem> get() = items.filter { it.status == ShoppingItem.Status.NEED }
val ShoppingData.inCart: List<ShoppingItem> get() = items.filter { it.status == ShoppingItem.Status.IN_CART }
fun ShoppingData.boughtOn(tripId: String): List<ShoppingItem> = items.filter { it.tripId == tripId }

/** Things you buy often that aren't on the list now, most bought first. */
val ShoppingData.buyAgain: List<String> get() {
    val onList = items.filter { it.status != ShoppingItem.Status.BOUGHT }.map { it.item.lowercase() }.toSet()
    return items.filter { it.status == ShoppingItem.Status.BOUGHT }.groupBy { it.item.lowercase() }
        .filterKeys { it !in onList }.entries
        .sortedWith(compareByDescending<Map.Entry<String, List<ShoppingItem>>> { it.value.size }.thenBy { it.key })
        .take(12).map { it.value.first().item }
}

fun ShoppingData.lastQuantity(name: String): String? =
    items.filter { it.item.equals(name, ignoreCase = true) }.maxByOrNull { it.boughtOn ?: it.added ?: "" }?.quantity

/** The last price paid (or expected) for an item, to fill it in next time. */
fun ShoppingData.lastPrice(name: String): Double? =
    items.filter { it.item.equals(name, ignoreCase = true) && it.price != null }.maxByOrNull { it.boughtOn ?: it.added ?: "" }?.price

fun ShoppingData.spent(month: String): Pair<Int, Double> = trips.filter { it.date.startsWith(month) }.let { it.size to it.sumOf { t -> t.total ?: 0.0 } }

object ShoppingJson {
    fun parse(o: JSONObject?): ShoppingData? {
        if (o == null) return null
        return ShoppingData(
            items = o.optJSONArray("items").objs().mapNotNull(::item),
            trips = o.optJSONArray("trips").objs().mapNotNull { t ->
                ShoppingTrip(t.str("id") ?: return@mapNotNull null, t.str("date") ?: return@mapNotNull null, t.str("store"),
                    t.dbl("items")?.toInt() ?: 0, t.dbl("total"), t.str("method"), t.str("notes"))
            },
        )
    }

    fun item(i: JSONObject): ShoppingItem? = ShoppingItem(
        id = i.str("id") ?: return null, item = i.str("item") ?: return null, quantity = i.str("quantity"),
        category = i.str("category") ?: "Other", status = ShoppingItem.Status.of(i.str("status")) ?: ShoppingItem.Status.NEED,
        price = i.dbl("price"), added = i.str("added"), boughtOn = i.str("boughtOn"), tripId = i.str("tripId"),
        fromPlan = i.optBoolean("fromPlan"), notes = i.str("notes"),
    )

    private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
