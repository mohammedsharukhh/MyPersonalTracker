package com.mohammedsharukms.mypersonaltracker.sheet.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.sheet.PlanItem
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.ShoppingAisle
import com.mohammedsharukms.mypersonaltracker.sheet.ShoppingItem
import com.mohammedsharukms.mypersonaltracker.sheet.ShoppingParse
import com.mohammedsharukms.mypersonaltracker.sheet.ShoppingTrip
import com.mohammedsharukms.mypersonaltracker.sheet.boughtOn
import com.mohammedsharukms.mypersonaltracker.sheet.buyAgain
import com.mohammedsharukms.mypersonaltracker.sheet.inCart
import com.mohammedsharukms.mypersonaltracker.sheet.lastPrice
import com.mohammedsharukms.mypersonaltracker.sheet.lastQuantity
import com.mohammedsharukms.mypersonaltracker.sheet.shoppingData
import com.mohammedsharukms.mypersonaltracker.sheet.spent
import com.mohammedsharukms.mypersonaltracker.sheet.toBuy
import java.time.LocalDate
import java.util.UUID

// Food ▸ Shopping: the grocery list (by aisle), the cart while you shop,
// finishing a trip (one Groceries expense in Finance), and the history of what
// you bought and what it cost. Kept in the sheet, so everyone sees the same list.
// Mirrors Shopping.swift.

object ShoppingActions {
    /** Adds the week's planned foods, added up, that aren't on the list yet. Returns how many were added. */
    fun addPlan(plan: List<PlanItem>, store: SheetStore): Int {
        val onList = store.snapshot?.shoppingData?.items.orEmpty().filter { it.status != ShoppingItem.Status.BOUGHT }.map { it.item.lowercase() }.toSet()
        var added = 0
        plan.groupBy { "${it.food.lowercase()}|${it.unit ?: ""}" }.values.sortedBy { it.first().food }.forEach { group ->
            val food = group.first().food
            if (food.lowercase() in onList) return@forEach
            val amount = group.sumOf { it.amount ?: 0.0 }
            val quantity = if (amount > 0) "${SheetFormat.number(amount, 2)} ${group.first().unit ?: ""}".trim() else null
            store.saveShoppingItem(ShoppingItem(item = food, quantity = quantity, fromPlan = true))
            added++
        }
        return added
    }
}

/** What the Shopping section opens. */
class ShoppingUi {
    var editing by mutableStateOf<ShoppingItem?>(null)
    var checkingOut by mutableStateOf(false)
    var trip by mutableStateOf<ShoppingTrip?>(null)
    var planNote by mutableStateOf<String?>(null)
}

fun LazyListScope.shoppingItems(store: SheetStore, s: SheetSnapshot, weekPlan: List<PlanItem>, ui: ShoppingUi) {
    val data = s.shoppingData
    val toBuy = data.toBuy
    val cart = data.inCart
    item { QuickAdd(store) }
    val again = data.buyAgain
    if (again.isNotEmpty()) item {
        Column {
            Text("Buy again", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
            Spacer(Modifier.size(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                again.forEach { name -> FilterPill(name, Icons.Filled.Add, SheetTheme.navy, false) {
                    store.saveShoppingItem(ShoppingItem(item = name, quantity = data.lastQuantity(name), price = data.lastPrice(name)))
                } }
            }
        }
    }
    if (weekPlan.isNotEmpty()) item {
        Column {
            FilterPill("Add this week's meal plan", Icons.Filled.EventNote, SheetTheme.navy, false) {
                val added = ShoppingActions.addPlan(weekPlan, store)
                ui.planNote = if (added == 0) "Everything planned this week is already on the list." else "Added $added item${if (added == 1) "" else "s"} from this week's plan."
            }
            ui.planNote?.let { Text(it, fontSize = 12.sp, color = SheetTheme.secondary, modifier = Modifier.padding(top = 6.dp)) }
        }
    }

    if (toBuy.isEmpty() && cart.isEmpty()) item {
        SheetCard {
            Text("Your list is empty", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            Text("Add what you need above. At the shop, tap each item as it goes in the cart, then Finish shopping to record what you paid — it's added to Finance as Groceries.",
                fontSize = 14.sp, color = SheetTheme.secondary)
        }
    } else if (toBuy.isNotEmpty()) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                val estimate = toBuy.mapNotNull { it.price }.sum()
                SectionTitle("To buy", "${toBuy.size} item${if (toBuy.size == 1) "" else "s"}" +
                    (if (estimate > 0) " · about ${SheetFormat.qar(estimate)} ${Money.main}" else "") + " · tap ○ as it goes in the cart", Modifier.weight(1f))
                ShareListButton(toBuy)
            }
        }
        ShoppingAisle.all.forEach { aisle ->
            val list = toBuy.filter { it.category == aisle }.sortedBy { it.item }
            if (list.isNotEmpty()) item(key = "aisle-$aisle") {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SheetTheme.card)) {
                    Text(aisle, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SheetTheme.secondary, modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 4.dp))
                    list.forEach { i ->
                        RowDivider()
                        ItemRow(i, onTick = { store.saveShoppingItem(i.copy(status = ShoppingItem.Status.IN_CART)) }, onOpen = { ui.editing = i },
                            onPrice = { p -> if (p != i.price) store.saveShoppingItem(i.copy(price = p)) })
                    }
                }
            }
        }
    }

    if (cart.isNotEmpty()) {
        val priced = cart.mapNotNull { it.price }.sum()
        item { SectionTitle("In the cart", if (priced > 0) "${SheetFormat.qar(priced)} ${Money.main} so far" else "Type what each cost — or just enter the total at the end") }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SheetTheme.card)) {
                cart.forEachIndexed { n, i ->
                    if (n > 0) RowDivider()
                    ItemRow(i, onTick = { store.saveShoppingItem(i.copy(status = ShoppingItem.Status.NEED)) }, onOpen = { ui.editing = i },
                        onPrice = { p -> if (p != i.price) store.saveShoppingItem(i.copy(price = p)) })
                }
            }
        }
        item {
            androidx.compose.material3.Button({ ui.checkingOut = true }, Modifier.fillMaxWidth().padding(top = 2.dp), shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = SheetTheme.good, contentColor = Color.White)) {
                Icon(Icons.Filled.CheckCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("Finish shopping · ${cart.size} item${if (cart.size == 1) "" else "s"}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }

    val trips = data.trips.sortedByDescending { it.date }
    if (trips.isNotEmpty()) {
        val (count, total) = data.spent(LocalDate.now().toString().take(7))
        item { SectionTitle("What you bought", if (count > 0) "This month: $count trip${if (count == 1) "" else "s"} · ${SheetFormat.qar(total)} ${Money.main}" else null) }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SheetTheme.card)) {
                trips.take(20).forEachIndexed { n, t ->
                    if (n > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().clickable { ui.trip = t }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Filled.ShoppingBag, SheetTheme.categoryColor("Groceries"), 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.store ?: "Shopping", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                                if (t.pending) { Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.Schedule, "Waiting to sync", tint = SheetTheme.sky, modifier = Modifier.size(12.dp)) }
                            }
                            Text("${SheetDate.weekdayDay(t.date)} · ${t.items} item${if (t.items == 1) "" else "s"}", fontSize = 12.sp, color = SheetTheme.secondary)
                        }
                        t.total?.takeIf { it > 0 }?.let { Text(SheetFormat.qar(it), fontWeight = FontWeight.Bold, color = SheetTheme.text) }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SheetTheme.tertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAdd(store: SheetStore) {
    // A TextFieldValue, so clearing it also ends the keyboard's half-typed word (else it comes back).
    var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var priceText by remember { mutableStateOf("") }
    val text = field.text
    fun add() {
        val (item, quantity) = ShoppingParse.split(text)
        if (item.isBlank()) return
        val price = SheetFormat.input(priceText)?.takeIf { it > 0 }
        val data = store.snapshot?.shoppingData
        // Already on the list: update it rather than add it twice.
        val existing = data?.items.orEmpty()
            .firstOrNull { it.status != ShoppingItem.Status.BOUGHT && it.item.equals(item, ignoreCase = true) }
        store.saveShoppingItem(existing?.copy(quantity = quantity ?: existing.quantity, price = price ?: existing.price)
            ?: ShoppingItem(item = item, quantity = quantity, price = price ?: data?.lastPrice(item)))
        field = androidx.compose.ui.text.input.TextFieldValue("")
        priceText = ""
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SheetTheme.card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.AddShoppingCart, null, tint = SheetTheme.navy)
        Spacer(Modifier.width(10.dp))
        BasicTextField(field, { field = it }, singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = SheetTheme.text),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { add() }),
            cursorBrush = SolidColor(SheetTheme.navy), modifier = Modifier.weight(1f),
            decorationBox = { inner -> if (text.isEmpty()) Text("Add an item, e.g. milk 2L, rice 5 kg", color = SheetTheme.tertiary, maxLines = 1); inner() })
        Spacer(Modifier.width(8.dp))
        PriceBox(priceText, onDone = { add() }) { priceText = it }
        if (text.isNotBlank()) Text("Add", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy, modifier = Modifier.clickable { add() }.padding(start = 8.dp))
    }
}

@Composable
private fun ShareListButton(items: List<ShoppingItem>) {
    val context = LocalContext.current
    Row(Modifier.clip(CircleShape).clickable {
        val lines = mutableListOf("Shopping list")
        ShoppingAisle.all.forEach { aisle ->
            val list = items.filter { it.category == aisle }
            if (list.isNotEmpty()) { lines += "\n$aisle"; lines += list.map { "☐ ${it.label}" } }
        }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, lines.joinToString("\n")), "Share list"))
    }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Share, null, tint = SheetTheme.navy, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Share", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
    }
}

/** A small price box: "Price" until you type. */
@Composable
private fun PriceBox(value: String, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null, onChange: (String) -> Unit) {
    BasicTextField(value, { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) }, singleLine = true,
        textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, textAlign = TextAlign.End),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        cursorBrush = SolidColor(SheetTheme.navy),
        modifier = modifier.width(76.dp).clip(RoundedCornerShape(8.dp)).background(SheetTheme.input).padding(horizontal = 8.dp, vertical = 7.dp),
        decorationBox = { inner -> androidx.compose.foundation.layout.Box(contentAlignment = Alignment.CenterEnd) {
            if (value.isEmpty()) Text("Price", fontSize = 15.sp, color = SheetTheme.tertiary); inner() } })
}

@Composable
private fun ItemRow(item: ShoppingItem, onTick: () -> Unit, onOpen: () -> Unit, onPrice: (Double?) -> Unit) {
    val inCart = item.status == ShoppingItem.Status.IN_CART
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (inCart) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            if (inCart) "Take ${item.item} out of the cart" else "Put ${item.item} in the cart",
            tint = if (inCart) SheetTheme.good else SheetTheme.secondary, modifier = Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onTick).padding(2.dp))
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f).clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.item, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text,
                        textDecoration = if (inCart) TextDecoration.LineThrough else null)
                    if (item.pending) { Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.Schedule, "Waiting to sync", tint = SheetTheme.sky, modifier = Modifier.size(12.dp)) }
                }
                val detail = listOfNotNull(item.quantity, item.notes, if (item.fromPlan) "from plan" else null).joinToString(" · ")
                if (detail.isNotEmpty()) Text(detail, fontSize = 12.sp, color = SheetTheme.secondary, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        // Saved a moment after you stop typing, and when you leave the box.
        var text by remember(item.id) { mutableStateOf(SheetFormat.plain(item.price)) }
        var focused by remember { mutableStateOf(false) }
        if (!focused && SheetFormat.input(text) != item.price) text = SheetFormat.plain(item.price)
        androidx.compose.runtime.LaunchedEffect(text) {
            if (!focused) return@LaunchedEffect
            kotlinx.coroutines.delay(800)
            onPrice(SheetFormat.input(text)?.takeIf { it > 0 })
        }
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        PriceBox(text, Modifier.onFocusChanged { state ->
            if (focused && !state.isFocused) onPrice(SheetFormat.input(text)?.takeIf { it > 0 })
            focused = state.isFocused
        }, onDone = { focusManager.clearFocus() }) { text = it }
    }
}

/** The editors the Shopping section opens. */
@Composable
fun ShoppingSheets(store: SheetStore, ui: ShoppingUi) {
    ui.editing?.let { ShoppingItemSheet(store, it) { ui.editing = null } }
    if (ui.checkingOut) CheckoutSheet(store) { ui.checkingOut = false }
    ui.trip?.let { TripSheet(store, it) { ui.trip = null } }
}

@Composable
private fun ShoppingItemSheet(store: SheetStore, initial: ShoppingItem, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.item) }
    var quantity by remember { mutableStateOf(initial.quantity ?: "") }
    var aisle by remember { mutableStateOf(initial.category) }
    var price by remember { mutableStateOf(SheetFormat.plain(initial.price)) }
    var notes by remember { mutableStateOf(initial.notes ?: "") }
    EditorSheet(if (initial.status == ShoppingItem.Status.IN_CART) "In the cart" else "To buy", onDismiss, canSave = name.isNotBlank(), onSave = {
        store.saveShoppingItem(initial.copy(item = name.trim(), quantity = quantity.trim().ifEmpty { null }, category = aisle,
            price = SheetFormat.input(price), notes = notes.trim().ifEmpty { null }))
        onDismiss()
    }) {
        Field("Item") { PlainField(name, "e.g. Milk") { name = it } }
        Field("Quantity") { PlainField(quantity, "e.g. 2 L, 5 kg, 30") { quantity = it } }
        Field("Aisle") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShoppingAisle.all.forEach { a -> FilterPill(a, null, SheetTheme.navy, a == aisle) { aisle = a } }
            }
        }
        Field("Price (${Money.main}, optional)") { PlainField(price, "0.00") { price = it.filter { c -> c.isDigit() || c == '.' || c == ',' } } }
        Field("Note") { PlainField(notes, "e.g. brand, low fat") { notes = it } }
        Text("Remove from list", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { store.deleteShoppingItem(initial.id); onDismiss() }.padding(vertical = 8.dp))
    }
}

@Composable
private fun CheckoutSheet(store: SheetStore, onDismiss: () -> Unit) {
    val data = store.snapshot?.shoppingData
    val cart = data?.inCart.orEmpty()
    val priced = cart.mapNotNull { it.price }.sum()
    var shop by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf(if (priced > 0) SheetFormat.plain(priced) else "") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    val methods = store.snapshot?.lists?.methods ?: listOf("Cash")
    var method by remember { mutableStateOf(methods.first()) }
    var addToFinance by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    val total = SheetFormat.input(totalText)?.takeIf { it > 0 }
    val shops = data?.trips.orEmpty().mapNotNull { it.store }.distinct().sorted()

    EditorSheet("Finish shopping", onDismiss, saveLabel = "Done", canSave = cart.isNotEmpty(), onSave = {
        store.checkoutShopping(UUID.randomUUID().toString(), date.toString(), shop.trim(), total, method, addToFinance && total != null,
            notes.trim(), cart.map { it.id to it.price })
        onDismiss()
    }) {
        Field("Where") { PlainField(shop, "Shop, e.g. Lulu, DMart, Carrefour") { shop = it } }
        if (shops.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shops.forEach { s -> FilterPill(s, null, SheetTheme.navy, s == shop) { shop = s } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Day", color = SheetTheme.secondary, modifier = Modifier.weight(1f))
            FilterPill("Today", null, SheetTheme.navy, date == LocalDate.now()) { date = LocalDate.now() }
            Spacer(Modifier.width(8.dp))
            FilterPill("Yesterday", null, SheetTheme.navy, date == LocalDate.now().minusDays(1)) { date = LocalDate.now().minusDays(1) }
        }
        SheetCard {
            Text("Total paid", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CurrencyPill(Money.main)
                Spacer(Modifier.width(12.dp))
                BasicTextField(totalText, { totalText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, singleLine = true,
                    textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text, textAlign = TextAlign.End),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), cursorBrush = SolidColor(SheetTheme.navy),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> androidx.compose.foundation.layout.Box(contentAlignment = Alignment.CenterEnd) {
                        if (totalText.isEmpty()) Text("0.00", fontSize = 26.sp, color = SheetTheme.tertiary); inner() } })
            }
        }
        Field("Paid with") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                methods.forEach { m -> FilterPill(m, null, SheetTheme.navy, m == method) { method = m } }
            }
        }
        ToggleRow("Add to Finance as Groceries", addToFinance,
            if (addToFinance) "One Groceries expense for the whole trip. Deleting the trip later takes it out of Finance too." else "Recorded here only.") { addToFinance = it }
        FormSection("${cart.size} item${if (cart.size == 1) "" else "s"}") {
            cart.forEachIndexed { n, i ->
                if (n > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(i.label, color = SheetTheme.text, modifier = Modifier.weight(1f))
                    i.price?.let { Text(SheetFormat.qar(it), color = SheetTheme.secondary) }
                }
            }
        }
        Field("Note (optional)") { PlainField(notes, "e.g. weekly shop") { notes = it } }
    }
}

@Composable
private fun TripSheet(store: SheetStore, trip: ShoppingTrip, onDismiss: () -> Unit) {
    val items = store.snapshot?.shoppingData?.boughtOn(trip.id).orEmpty().sortedBy { it.item }
    var confirmDelete by remember { mutableStateOf(false) }
    EditorSheet(trip.store ?: "Shopping trip", onDismiss, onSave = null) {
        SheetCard {
            Text(SheetDate.short(trip.date), color = SheetTheme.secondary)
            Text(trip.total?.let { "${SheetFormat.qar(it)} ${Money.main}" } ?: "No total", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text)
            Text(listOfNotNull(trip.method, "${trip.items} items", trip.notes).joinToString(" · "), fontSize = 12.sp, color = SheetTheme.secondary)
        }
        FormSection("Bought") {
            items.forEachIndexed { n, i ->
                if (n > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(i.label, color = SheetTheme.text, modifier = Modifier.weight(1f))
                    i.price?.let { Text(SheetFormat.qar(it), color = SheetTheme.secondary) }
                }
            }
        }
        Text("Put these on the list again", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy, modifier = Modifier.clickable {
            items.forEach { store.saveShoppingItem(ShoppingItem(item = it.item, quantity = it.quantity, category = it.category)) }
            onDismiss()
        }.padding(vertical = 8.dp))
        Text("Delete this trip", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { confirmDelete = true }.padding(vertical = 8.dp))
    }
    if (confirmDelete) AppDialog(
        title = "Delete this trip?",
        message = "Its items leave your history, and its Groceries expense is taken out of Finance.",
        style = DialogStyle.DESTRUCTIVE,
        confirmText = "Delete trip",
        onConfirm = { store.deleteShoppingTrip(trip.id); confirmDelete = false; onDismiss() },
        onDismiss = { confirmDelete = false },
    )
}
