package com.mohammedsharukms.mypersonaltracker.sheet.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mohammedsharukms.mypersonaltracker.container
import com.mohammedsharukms.mypersonaltracker.sheet.FoodDatabase
import com.mohammedsharukms.mypersonaltracker.sheet.FoodItem
import com.mohammedsharukms.mypersonaltracker.sheet.FoodRef
import com.mohammedsharukms.mypersonaltracker.sheet.FoodQuantity
import com.mohammedsharukms.mypersonaltracker.sheet.Meal
import com.mohammedsharukms.mypersonaltracker.sheet.MealPhotos
import com.mohammedsharukms.mypersonaltracker.sheet.MealType
import com.mohammedsharukms.mypersonaltracker.sheet.MyFood
import com.mohammedsharukms.mypersonaltracker.sheet.NutritionTotals
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.foodData
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Logging a meal: an optional photo, foods with amounts (nutrition scales as you
// change them), totals against today's targets, and what it cost — which can go
// straight into Finance. Mirrors MealEditor.swift.

/** What the meal editor opens with. */
data class MealRequest(
    val meal: Meal? = null,
    val type: MealType? = null,
    val date: LocalDate = LocalDate.now(),
    val items: List<FoodItem> = emptyList(),
    val fromPlan: Pair<String, MealType>? = null,
    val camera: Boolean = false,
    val search: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealEditorSheet(store: SheetStore, request: MealRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val book = context.container.currency
    val existing = request.meal
    val mealId = remember { existing?.id ?: java.util.UUID.randomUUID().toString() }
    var type by remember { mutableStateOf(existing?.type ?: request.type ?: MealType.forNow()) }
    var date by remember { mutableStateOf(existing?.let { SheetDate.date(it.date) } ?: request.date) }
    val time = remember { existing?.time ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }
    val items = remember {
        mutableStateListOf<FoodItem>().apply {
            addAll(existing?.entries?.map { FoodItem.of(it.food, it.amount ?: 1.0, it.unit ?: "serving", it.calories ?: 0.0, it.protein ?: 0.0, it.carbs ?: 0.0, it.fat ?: 0.0) }
                ?: request.items)
        }
    }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var newPhoto by remember { mutableStateOf<ByteArray?>(null) }
    var removePhoto by remember { mutableStateOf(false) }
    val initialCost = existing?.cost ?: request.items.mapNotNull { it.cost }.sum().takeIf { it > 0 }
    var costText by remember { mutableStateOf(SheetFormat.plain(initialCost)) }
    var currency by remember { mutableStateOf(Money.main) }
    var addToFinance by remember { mutableStateOf(existing?.hasLedgerExpense ?: true) }
    var financeCategory by remember { mutableStateOf("Food & Dining") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var searching by remember { mutableStateOf(request.search) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val settings = store.snapshot?.settings
    val method = store.snapshot?.lists?.methods?.firstOrNull() ?: "Cash"

    val captureFile = remember { File(context.cacheDir, "captures").apply { mkdirs() }.let { File(it, "meal-$mealId.jpg") } }
    val captureUri: Uri = remember { FileProvider.getUriForFile(context, context.packageName + ".fileprovider", captureFile) }
    fun setPhoto(uri: Uri) {
        val data = MealPhotos.prepare(context, uri) ?: return
        newPhoto = data
        bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        removePhoto = false
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) setPhoto(captureUri) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::setPhoto) }
    LaunchedEffect(Unit) {
        if (request.camera) runCatching { camera.launch(captureUri) }
        if (existing != null) bitmap = MealPhotos.bitmap(context, store, existing, thumb = false)
    }

    val totals = NutritionTotals.ofItems(items)
    val cost = SheetFormat.input(costText)?.takeIf { it > 0 }?.let { book.convert(it, currency, Money.main) }
    val canSave = items.isNotEmpty()

    fun save() {
        store.saveMeal(mealId, date.toString(), time, type, items.toList(), cost, notes.trim(), newPhoto, removePhoto,
            addToFinance && cost != null, financeCategory, method)
        request.fromPlan?.let { (planDate, planType) ->
            val planned = store.snapshot?.foodData?.plan.orEmpty().filter { it.date == planDate && it.meal == planType.label }.map { it.asItem }
            if (planned.isNotEmpty()) store.savePlan(planDate, planType, planned, eaten = true)
        }
        onDismiss()
    }

    EditorSheet(
        title = if (existing == null) "Log a meal" else "Edit meal",
        onDismiss = onDismiss,
        canSave = canSave,
        onSave = ::save,
        bottomBar = {
            Column(Modifier.background(SheetTheme.page).padding(horizontal = 16.dp, vertical = 8.dp)) {
                PrimaryButton(
                    if (canSave) "Save ${type.label.lowercase()} · ${SheetFormat.number(totals.calories, 0)} kcal · ${SheetFormat.number(totals.protein, 0)} g protein"
                    else "Add a food to save",
                    enabled = canSave, onClick = ::save,
                )
            }
        },
    ) {
        // Photo (optional)
        val photo = bitmap
        if (photo != null && !removePhoto) {
            Box {
                Image(photo.asImageBitmap(), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp)))
                Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PhotoAction(Icons.Filled.CameraAlt, "Retake") { camera.launch(captureUri) }
                    PhotoAction(Icons.Filled.Delete, "Remove photo") { bitmap = null; newPhoto = null; removePhoto = true }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigChoice("Snap your meal", Icons.Filled.CameraAlt, Modifier.weight(1f)) { camera.launch(captureUri) }
                BigChoice("Choose photo", Icons.Filled.PhotoLibrary, Modifier.weight(1f)) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            Text("A photo is optional — skip it and just add what you ate.", fontSize = 12.sp, color = SheetTheme.secondary)
        }

        // Meal + day
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealType.entries.forEach { meal ->
                val selected = meal == type
                Text(meal.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    color = if (selected) Color.White else SheetTheme.text,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (selected) SheetTheme.navy else SheetTheme.card)
                        .clickable { type = meal }.padding(vertical = 12.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Day", fontSize = 15.sp, color = SheetTheme.secondary, modifier = Modifier.weight(1f))
            FilterPill("Today", null, SheetTheme.navy, date == LocalDate.now()) { date = LocalDate.now() }
            Spacer(Modifier.width(8.dp))
            FilterPill("Yesterday", null, SheetTheme.navy, date == LocalDate.now().minusDays(1)) { date = LocalDate.now().minusDays(1) }
        }

        // Foods
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card)) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
                Text("What you ate", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, modifier = Modifier.weight(1f))
                Text("${items.size} item${if (items.size == 1) "" else "s"}", fontSize = 12.sp, color = SheetTheme.secondary)
            }
            items.forEachIndexed { i, item ->
                RowDivider()
                FoodItemRow(item, onChange = { items[i] = it }, onRemove = { items.removeAt(i) }, onRemember = { food ->
                    scope.launch {
                        store.saveMyFood(MyFood(name = food.food, amount = food.amount, unit = food.unit, calories = food.calories,
                            protein = food.protein, carbs = food.carbs, fat = food.fat, cost = null))
                    }
                })
            }
            RowDivider()
            Row(Modifier.fillMaxWidth().clickable { searching = true }.padding(14.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AddCircle, null, tint = SheetTheme.navy, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (items.isEmpty()) "Add what you ate" else "Add another food", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
            }
        }

        // Totals
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card).padding(vertical = 14.dp)) {
            MacroTotal("Calories", totals.calories, settings?.calorieTarget, "", SheetTheme.warn, Modifier.weight(1f))
            MacroTotal("Protein", totals.protein, settings?.proteinTarget, "g", SheetTheme.good, Modifier.weight(1f))
            MacroTotal("Carbs", totals.carbs, settings?.carbTarget, "g", SheetTheme.sky, Modifier.weight(1f))
            MacroTotal("Fat", totals.fat, settings?.fatTarget, "g", SheetTheme.purple, Modifier.weight(1f))
        }

        // Cost
        SheetCard {
            Text("What it cost", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            Text("Leave empty for home food you've already paid for.", fontSize = 12.sp, color = SheetTheme.secondary)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Money.single) CurrencyPill(Money.main) else CurrencyButton(book, currency) { currency = it }
                Spacer(Modifier.width(12.dp))
                BasicTextField(costText, { costText = it }, singleLine = true,
                    textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text, textAlign = TextAlign.End),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), cursorBrush = SolidColor(SheetTheme.navy),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> Box(contentAlignment = Alignment.CenterEnd) { if (costText.isEmpty()) Text("0.00", fontSize = 26.sp, color = SheetTheme.tertiary); inner() } })
            }
            if (cost != null) {
                if (currency != Money.main) Text("Saved as ${SheetFormat.qar(cost)} ${Money.main}", fontSize = 12.sp, color = SheetTheme.navy)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Add to Finance", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                        Text("Logged as an expense too, kept in step if you edit this meal.", fontSize = 12.sp, color = SheetTheme.secondary)
                    }
                    Switch(addToFinance, { addToFinance = it }, colors = SwitchDefaults.colors(checkedTrackColor = SheetTheme.navy))
                }
                if (addToFinance) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Food & Dining" to "Eating out", "Groceries" to "Groceries").forEach { (category, label) ->
                        FilterPill(label, SheetTheme.categoryIcon(category), SheetTheme.categoryColor(category), financeCategory == category) { financeCategory = category }
                    }
                }
            }
        }

        Field("Notes (optional)") { PlainField(notes, "How was it? Home-made, restaurant…", singleLine = false) { notes = it } }

        if (existing != null) {
            Text("Delete meal", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SheetTheme.bad.copy(alpha = 0.1f))
                    .clickable { confirmDelete = true }.padding(12.dp))
        }
    }

    if (searching) FoodSearchSheet(store, onDismiss = { searching = false }) { items.add(it.item) }
    if (confirmDelete) AppDialog(
        title = "Delete this meal?",
        message = if (existing?.hasLedgerExpense == true) "Its expense in Finance is deleted too." else null,
        style = DialogStyle.DESTRUCTIVE,
        confirmText = "Delete",
        onConfirm = { confirmDelete = false; store.deleteMeal(mealId); onDismiss() },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun PhotoAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun BigChoice(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(SheetTheme.card).clickable(onClick = onClick).padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = SheetTheme.navy, modifier = Modifier.size(28.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
    }
}

@Composable
private fun MacroTotal(title: String, value: Double, target: Double?, unit: String, tint: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
        Text("${SheetFormat.number(value, 0)}$unit", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = tint)
        if (target != null && target > 0) Text("${(value / target * 100).toInt()}% of day", fontSize = 10.sp, color = SheetTheme.secondary)
    }
}

/** The amount, typed: "250". Follows − / + and unit changes while you're not typing. */
@Composable
private fun AmountField(amount: Double, onChange: (Double) -> Unit) {
    var text by remember { mutableStateOf(SheetFormat.plain(amount)) }
    var focused by remember { mutableStateOf(false) }
    if (!focused && SheetFormat.input(text) != amount) text = SheetFormat.plain(amount)
    androidx.compose.foundation.text.BasicTextField(text, { new ->
        text = new.filter { it.isDigit() || it == '.' || it == ',' }
        SheetFormat.input(text)?.let(onChange)
    }, singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, textAlign = TextAlign.Center),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(SheetTheme.navy),
        modifier = Modifier.padding(horizontal = 6.dp).width(64.dp).clip(RoundedCornerShape(8.dp)).background(SheetTheme.input)
            .padding(vertical = 7.dp).onFocusChanged { focused = it.isFocused })
}

/** The unit, with a menu when the food can be shown in others (cup ↔ g ↔ ml). */
@Composable
private fun UnitPicker(item: FoodItem, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val choices = item.unitChoices
    Box {
        Row(Modifier.clip(RoundedCornerShape(8.dp)).then(if (choices.size > 1) Modifier.clickable { open = true } else Modifier)
            .padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.unit, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (choices.size > 1) SheetTheme.navy else SheetTheme.text)
            if (choices.size > 1) Icon(Icons.Filled.ArrowDropDown, "Change unit", tint = SheetTheme.navy, modifier = Modifier.size(18.dp))
        }
        androidx.compose.material3.DropdownMenu(open, { open = false }) {
            choices.forEach { u ->
                androidx.compose.material3.DropdownMenuItem({ Text(u) }, { open = false; onPick(u) },
                    trailingIcon = { if (u == item.unit) Icon(Icons.Filled.Check, null) })
            }
        }
    }
}

/** One food in a meal or plan: name, − amount +, and its nutrition. */
@Composable
fun FoodItemRow(item: FoodItem, onChange: (FoodItem) -> Unit, onRemove: () -> Unit, onRemember: ((FoodItem) -> Unit)? = null) {
    var editing by remember { mutableStateOf(false) }
    var remembered by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.food, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, modifier = Modifier.weight(1f))
            IconButton(onRemove, Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Remove ${item.food}", tint = SheetTheme.tertiary, modifier = Modifier.size(18.dp)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundStep(Icons.Filled.Remove) { onChange(item.withAmount(maxOf(item.step, item.amount - item.step))) }
            AmountField(item.amount) { onChange(item.withAmount(it)) }
            UnitPicker(item) { onChange(item.withUnit(it)) }
            RoundStep(Icons.Filled.Add) { onChange(item.withAmount(item.amount + item.step)) }
            Spacer(Modifier.weight(1f))
            Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { editing = !editing }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("${SheetFormat.number(item.calories, 0)} kcal", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                    Text("P ${SheetFormat.number(item.protein, 0)} · C ${SheetFormat.number(item.carbs, 0)} · F ${SheetFormat.number(item.fat, 0)}",
                        fontSize = 11.sp, color = SheetTheme.secondary)
                }
                Spacer(Modifier.width(6.dp))
                Icon(if (editing) Icons.Filled.Check else Icons.Filled.Edit, if (editing) "Done editing nutrition" else "Edit calories and protein",
                    tint = SheetTheme.navy, modifier = Modifier.size(20.dp))
            }
        }
        if (editing) {
            Text("For ${SheetFormat.number(item.amount, 2)} ${item.unit} — change them to match your food", fontSize = 11.sp,
                color = SheetTheme.secondary, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 8.dp)) {
                NutritionBox("kcal", item.calories, SheetTheme.warn, Modifier.weight(1f)) { onChange(item.withNutrition(calories = it)) }
                NutritionBox("Protein g", item.protein, SheetTheme.good, Modifier.weight(1f)) { onChange(item.withNutrition(protein = it)) }
                NutritionBox("Carbs g", item.carbs, SheetTheme.sky, Modifier.weight(1f)) { onChange(item.withNutrition(carbs = it)) }
                NutritionBox("Fat g", item.fat, SheetTheme.purple, Modifier.weight(1f)) { onChange(item.withNutrition(fat = it)) }
            }
            if (onRemember != null) {
                Text(if (remembered) "✓ Saved to My Foods" else "☆ Remember for next time", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (remembered) SheetTheme.good else SheetTheme.navy,
                    modifier = Modifier.padding(top = 6.dp).clickable(enabled = !remembered) { onRemember(item); remembered = true }.padding(vertical = 4.dp))
            }
        }
    }
}

/** A small labelled number box for one nutrition value; follows − / + rescaling when not being typed in. */
@Composable
private fun NutritionBox(title: String, value: Double, tint: Color, modifier: Modifier, onChange: (Double) -> Unit) {
    var text by remember { mutableStateOf(SheetFormat.plain(value, 1)) }
    var focused by remember { mutableStateOf(false) }
    if (!focused && SheetFormat.input(text) != Math.round(value * 10) / 10.0) text = SheetFormat.plain(value, 1)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicTextField(text, { t -> text = t; SheetFormat.input(t)?.let(onChange) }, singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tint, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), cursorBrush = SolidColor(tint),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.1f))
                .onFocusChanged { focused = it.isFocused }.padding(vertical = 8.dp))
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
    }
}

@Composable
private fun RoundStep(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(SheetTheme.input).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = SheetTheme.navy, modifier = Modifier.size(16.dp))
    }
}

// MARK: - Search

/** Recent foods, your foods and the built-in list — in one search. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchSheet(store: SheetStore, onDismiss: () -> Unit, onPick: (FoodRef) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val food = store.snapshot?.foodData
    val common = remember { FoodDatabase.common(context) }
    // "milk 250ml" → search "milk", add 250 ml.
    val (name, quantity) = FoodQuantity.split(query.trim())
    val mine = FoodDatabase.filter(FoodDatabase.mine(food?.myFoods.orEmpty()), name)
    val recent = if (name.isEmpty()) FoodDatabase.recent(food?.log.orEmpty(), common = common) else emptyList()
    val matches = FoodDatabase.filter(common, name)
    fun pick(ref: FoodRef) { onPick(ref.copy(wanted = quantity)); onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetTheme.page, dragHandle = null) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallTextButton("Close", onClick = onDismiss)
                Text("Add food", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = SheetTheme.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(72.dp))
            }
            TextField(query, { query = it }, placeholder = { Text("e.g. milk 250ml, 2 eggs, dal") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Filled.Close, "Clear") } },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = SheetTheme.card, unfocusedContainerColor = SheetTheme.card,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 24.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(SheetTheme.card)
                        .clickable { creating = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AddCircle, null, tint = SheetTheme.navy)
                        Spacer(Modifier.width(10.dp))
                        Text(if (name.isEmpty()) "Create a food" else "Create “$name”", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
                    }
                }
                if (quantity != null) item {
                    Text("Adds ${SheetFormat.number(quantity.amount, 2)}${quantity.unit?.let { " $it" } ?: ""} of the food you pick",
                        color = SheetTheme.navy, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
                } else if (query.isEmpty()) item {
                    Text("Type an amount with the food — “milk 250ml”, “rice 150g”, “2 chapati” — or change it after adding.",
                        color = SheetTheme.secondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
                }
                if (recent.isNotEmpty()) { item { SearchHeader("Recent") }; items(recent, key = { "r" + it.id }) { FoodRefRow(it) { pick(it) } } }
                if (mine.isNotEmpty()) { item { SearchHeader("My foods") }; items(mine, key = { "m" + it.id }) { FoodRefRow(it) { pick(it) } } }
                if (matches.isNotEmpty()) { item { SearchHeader("Common foods") }; items(matches.take(if (query.isEmpty()) 200 else 50), key = { "c" + it.id }) { FoodRefRow(it) { pick(it) } } }
                if (mine.isEmpty() && matches.isEmpty() && query.isNotEmpty()) item {
                    Text("Not in the list — create it once and it's saved to My Foods.", color = SheetTheme.secondary, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
    if (creating) CustomFoodSheet(store, name, onDismiss = { creating = false }) { pick(it) }
}

@Composable
private fun SearchHeader(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp))
}

@Composable
private fun FoodRefRow(food: FoodRef, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(12.dp)).background(SheetTheme.card).clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(food.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text, maxLines = 2)
            Text("${SheetFormat.number(food.amount, 2)} ${food.unit}${food.detail?.let { " · $it" } ?: ""}", fontSize = 12.sp, color = SheetTheme.secondary, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${SheetFormat.number(food.calories, 0)} kcal", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            Text("${SheetFormat.number(food.protein, 0)} g protein", fontSize = 11.sp, color = SheetTheme.good)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.AddCircle, null, tint = SheetTheme.navy)
    }
}

/** Your own food (a home recipe, a café item): saved to My Foods in the sheet. */
@Composable
fun CustomFoodSheet(store: SheetStore, name: String, onDismiss: () -> Unit, onSaved: (FoodRef) -> Unit) {
    var foodName by remember { mutableStateOf(name) }
    var amount by remember { mutableStateOf<Double?>(1.0) }
    var unit by remember { mutableStateOf("serving") }
    var calories by remember { mutableStateOf<Double?>(null) }
    var protein by remember { mutableStateOf<Double?>(null) }
    var carbs by remember { mutableStateOf<Double?>(null) }
    var fat by remember { mutableStateOf<Double?>(null) }
    var cost by remember { mutableStateOf<Double?>(null) }
    val scope = rememberCoroutineScope()
    EditorSheet("New food", onDismiss, saveLabel = "Add", canSave = foodName.isNotBlank() && (calories ?: 0.0) > 0, onSave = {
        val food = MyFood(name = foodName.trim(), amount = amount ?: 1.0, unit = unit, calories = calories, protein = protein ?: 0.0,
            carbs = carbs ?: 0.0, fat = fat ?: 0.0, cost = cost)
        onSaved(FoodDatabase.mine(listOf(food)).first())
        // Added to the meal even if offline; saved to My Foods when the sheet is reachable.
        scope.launch { store.saveMyFood(food) }
        onDismiss()
    }) {
        FormSection("Food", "Nutrition below is for this amount.") {
            Column(Modifier.padding(12.dp)) { PlainField(foodName, "Name, e.g. Mum's chicken curry") { foodName = it } }
            RowDivider()
            NumberRow("Amount", "", amount) { amount = it }
        }
        Field("Unit") {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("serving", "g", "ml", "piece", "cup", "bowl", "plate", "slice", "tbsp", "scoop").forEach { u ->
                    FilterPill(u, null, SheetTheme.navy, u == unit) { unit = u }
                }
            }
        }
        FormSection("Nutrition") {
            NumberRow("Calories", "kcal", calories) { calories = it }
            RowDivider(); NumberRow("Protein", "g", protein) { protein = it }
            RowDivider(); NumberRow("Carbs", "g", carbs) { carbs = it }
            RowDivider(); NumberRow("Fat", "g", fat) { fat = it }
        }
        FormSection("Cost") { NumberRow("Usual cost", Money.main, cost, "Optional — fills in a meal's cost") { cost = it } }
    }
}

/** Plan one meal: pick foods and amounts; "I ate this" logs it. */
@Composable
fun PlanEditorSheet(store: SheetStore, date: String, type: MealType, onDismiss: () -> Unit, onLogNow: (List<FoodItem>) -> Unit) {
    val items = remember {
        mutableStateListOf<FoodItem>().apply {
            addAll(store.snapshot?.foodData?.plan.orEmpty().filter { it.date == date && it.meal == type.label }.map { it.asItem })
        }
    }
    var searching by remember { mutableStateOf(false) }
    val t = NutritionTotals.ofItems(items)
    EditorSheet("Plan ${type.label.lowercase()} · ${SheetDate.weekdayDay(date)}", onDismiss, onSave = {
        store.savePlan(date, type, items.toList(), eaten = false)
        onDismiss()
    }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card)) {
            items.forEachIndexed { i, item ->
                if (i > 0) RowDivider()
                FoodItemRow(item, onChange = { items[i] = it }, onRemove = { items.removeAt(i) })
            }
            if (items.isNotEmpty()) RowDivider()
            Row(Modifier.fillMaxWidth().clickable { searching = true }.padding(14.dp), horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.AddCircle, null, tint = SheetTheme.navy, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add food", fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
            }
        }
        Text("${SheetFormat.number(t.calories, 0)} kcal · ${SheetFormat.number(t.protein, 0)} g protein · ${SheetFormat.number(t.carbs, 0)} g carbs · ${SheetFormat.number(t.fat, 0)} g fat",
            fontSize = 13.sp, color = SheetTheme.secondary)
        if (items.isNotEmpty()) {
            PrimaryButton("I ate this — log it") { onDismiss(); onLogNow(items.toList()) }
            Text("Clear this meal", color = SheetTheme.bad, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { items.clear() }.padding(8.dp))
        }
    }
    if (searching) FoodSearchSheet(store, onDismiss = { searching = false }) { items.add(it.item) }
}
