package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.sheet.CurrencyBook
import kotlinx.coroutines.launch
import java.text.NumberFormat

// The currency converter: every ISO currency, live rates cached for offline use,
// and the user's own rates. Mirrors CurrencyConverter.swift.

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CurrencyConverterScreen(book: CurrencyBook, onBack: () -> Unit, openBrowse: Boolean = false) {
    var amountText by remember { mutableStateOf("100") }
    var editing by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var browsing by remember { mutableStateOf(openBrowse) }
    val amount = SheetFormat.input(amountText) ?: 0.0
    LaunchedEffect(Unit) { book.refreshIfStale() }

    SheetScreen("Currency converter", isRefreshing = book.isRefreshing, onRefresh = { book.refresh() }, onBack = onBack) {
        item { ConverterCard(book, amountText, { amountText = it }) { editing = it } }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 10, 50, 100, 500, 1000, 5000).forEach { v ->
                    val selected = amount == v.toDouble()
                    Text(
                        NumberFormat.getIntegerInstance().format(v),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = if (selected) Color.White else SheetTheme.text,
                        modifier = Modifier.clip(CircleShape).background(if (selected) SheetTheme.navy else SheetTheme.card)
                            .clickable { amountText = v.toString() }.padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
        }
        book.refreshError?.let { error ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.WifiOff, null, tint = SheetTheme.warn, modifier = Modifier.size(16.dp))
                    Text(error, fontSize = 13.sp, color = SheetTheme.warn)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card)) {
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Favourites", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                        Text(
                            when {
                                book.isRefreshing -> "Updating rates…"
                                book.liveDate == null -> "Pull down to download live rates"
                                else -> "Rates from ${SheetDate.relative(book.liveDate!!)} · long-press to edit"
                            },
                            fontSize = 12.sp, color = SheetTheme.secondary,
                        )
                    }
                    val scope = rememberCoroutineScope()
                    if (book.isRefreshing) CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                    else IconButton({ scope.launch { book.refresh() } }) { Icon(Icons.Filled.Refresh, "Update rates", tint = SheetTheme.navy) }
                }
                book.favorites.filter { it != book.from }.forEach { code ->
                    HorizontalDivider(Modifier.padding(start = 60.dp), color = SheetTheme.input)
                    Row(
                        Modifier.fillMaxWidth().combinedClickable(onClick = { book.chooseTo(code) }, onLongClick = { editing = code })
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(CurrencyBook.flag(code), fontSize = 24.sp, modifier = Modifier.width(36.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(code, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                            Text(book.name(code), fontSize = 12.sp, color = SheetTheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(book.convert(amount, book.from, code)?.let { CurrencyBook.format(it) } ?: "—",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (code == book.to) SheetTheme.navy else SheetTheme.text)
                            SourceLabel(book.source(code))
                        }
                    }
                }
                HorizontalDivider(color = SheetTheme.input)
                Row(Modifier.fillMaxWidth()) {
                    BottomAction(Icons.Filled.Language, "All ${book.allCodes.size} currencies", Modifier.weight(1f)) { browsing = true }
                    BottomAction(Icons.Filled.Tune, "Custom rate", Modifier.weight(1f)) { adding = true }
                }
            }
        }
        item {
            Text(
                "Live rates by Exchange Rate API (open.er-api.com), saved for offline use. Gulf currencies are pegged to the US dollar. Long-press any currency to use your own rate.",
                fontSize = 12.sp, color = SheetTheme.secondary,
            )
        }
    }
    editing?.let { RateEditorSheet(book, it) { editing = null } }
    if (adding) RateEditorSheet(book, null) { adding = false }
    if (browsing) CurrencyPickerSheet(book, book.to, "All currencies", { book.chooseTo(it) }) { browsing = false }
}

@Composable
private fun BottomAction(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clickable(onClick = onClick).padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = SheetTheme.navy, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
    }
}

@Composable
private fun ConverterCard(book: CurrencyBook, amountText: String, onAmount: (String) -> Unit, onSetRate: (String) -> Unit) {
    val amount = SheetFormat.input(amountText) ?: 0.0
    val result = book.convert(amount, book.from, book.to)
    val unit = book.convert(1.0, book.from, book.to)
    Column(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(24.dp)).background(SheetTheme.heroGradient)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("You have", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CurrencyButton(book, book.from, onDark = true) { book.chooseFrom(it) }
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = amountText,
                    onValueChange = onAmount,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(book.name(book.from), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Box(
                Modifier.size(44.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Color.White).clickable { book.swap() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.SwapVert, "Swap currencies", tint = SheetTheme.navyDeep) }
        }
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("You get", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CurrencyButton(book, book.to, onDark = true) { book.chooseTo(it) }
                Spacer(Modifier.weight(1f))
                Text(result?.let { CurrencyBook.format(it) } ?: "—", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            }
            Row {
                Text(book.name(book.to), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                if (unit != null) Text("1 ${book.from} = ${CurrencyBook.format(unit)} ${book.to}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f))
            }
            if (result == null) {
                val missing = if (book.rate(book.from) == null) book.from else book.to
                Row(
                    Modifier.align(Alignment.End).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable { onSetRate(missing) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Edit, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (book.isRefreshing) "Downloading rates…" else "No rate for $missing yet — set one", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SourceLabel(source: CurrencyBook.Source) {
    val (text, tint) = when (source) {
        CurrencyBook.Source.BASE -> "Base" to SheetTheme.secondary
        CurrencyBook.Source.CUSTOM -> "Your rate" to SheetTheme.purple
        CurrencyBook.Source.SHEET -> "From your sheet" to SheetTheme.good
        CurrencyBook.Source.LIVE -> "Live" to SheetTheme.sky
        CurrencyBook.Source.PEG -> "Fixed peg" to SheetTheme.secondary
        CurrencyBook.Source.NONE -> "No rate" to SheetTheme.warn
    }
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tint)
}

/** A flag + code pill that opens the searchable list of every currency. */
@Composable
fun CurrencyButton(book: CurrencyBook, selection: String, onDark: Boolean = false, onSelect: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(
        Modifier.clip(CircleShape).background(if (onDark) Color.White.copy(alpha = 0.18f) else SheetTheme.input)
            .clickable { picking = true }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(CurrencyBook.flag(selection), fontSize = 18.sp)
        Text(selection, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (onDark) Color.White else SheetTheme.text)
        Icon(Icons.Filled.KeyboardArrowDown, "Change currency", tint = if (onDark) Color.White else SheetTheme.text, modifier = Modifier.size(18.dp))
    }
    if (picking) CurrencyPickerSheet(book, selection, "Choose currency", onSelect) { picking = false }
}

/** Every currency, searchable by code or name, with favourites first. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CurrencyPickerSheet(book: CurrencyBook, selection: String, title: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { book.refreshIfStale() }
    val query = search.trim()
    fun matches(code: String) = query.isEmpty() || code.contains(query, true) || book.name(code).contains(query, true)
    val favorites = book.favorites.filter(::matches)
    val others = book.allCodes.filter { !book.isFavorite(it) && matches(it) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetTheme.page,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallTextButton("Close", onClick = onDismiss)
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = SheetTheme.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(72.dp))
            }
            TextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search code or name, e.g. peso") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (search.isNotEmpty()) IconButton({ search = "" }) { Icon(Icons.Filled.Close, "Clear") } },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SheetTheme.card, unfocusedContainerColor = SheetTheme.card,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Text("Tap to choose · tap ☆ to favourite · long-press to set your own rate", fontSize = 11.sp, color = SheetTheme.secondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 24.dp)) {
                if (favorites.isNotEmpty()) {
                    item { PickerHeader("Favourites") }
                    items(favorites, key = { "f$it" }) { code ->
                        PickerRow(book, code, code == selection, onPick = { onSelect(code); onDismiss() }, onEdit = { editing = code })
                    }
                }
                if (others.isNotEmpty()) {
                    item { PickerHeader(if (query.isEmpty()) "All currencies" else "Results") }
                    items(others, key = { "a$it" }) { code ->
                        PickerRow(book, code, code == selection, onPick = { onSelect(code); onDismiss() }, onEdit = { editing = code })
                    }
                }
                if (favorites.isEmpty() && others.isEmpty()) item {
                    Text("No currency matches “$query”.", color = SheetTheme.secondary, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center)
                }
            }
        }
    }
    editing?.let { RateEditorSheet(book, it) { editing = null } }
}

@Composable
private fun PickerHeader(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickerRow(book: CurrencyBook, code: String, selected: Boolean, onPick: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(12.dp)).background(SheetTheme.card)
            .combinedClickable(onClick = onPick, onLongClick = { if (code != book.main) onEdit() })
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(CurrencyBook.flag(code), fontSize = 24.sp, modifier = Modifier.width(36.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(code, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            Text(book.name(code), fontSize = 12.sp, color = SheetTheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (code != book.main) Column(horizontalAlignment = Alignment.End) {
            book.rate(code)?.let { Text("1 ${book.main} = ${CurrencyBook.format(it)}", fontSize = 12.sp, color = SheetTheme.secondary) }
            SourceLabel(book.source(code))
        }
        if (selected) Icon(Icons.Filled.Check, "Selected", tint = SheetTheme.navy, modifier = Modifier.padding(start = 8.dp).size(20.dp))
        IconButton({ book.toggleFavorite(code) }) {
            Icon(if (book.isFavorite(code)) Icons.Filled.Star else Icons.Filled.StarBorder,
                if (book.isFavorite(code)) "Remove from favourites" else "Add to favourites",
                tint = if (book.isFavorite(code)) Color(0xFFF5B400) else SheetTheme.tertiary)
        }
    }
}

/** Set your own rate for a currency (entered either way round), or create a custom one when `code` is null. */
@Composable
fun RateEditorSheet(book: CurrencyBook, code: String?, onDismiss: () -> Unit) {
    var newCode by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    val initial = code?.let { book.rate(it) }
    var perQar by remember { mutableStateOf(SheetFormat.plain(initial, 6)) }
    var inQar by remember { mutableStateOf(SheetFormat.plain(initial?.let { 1 / it }, 6)) }
    val effective = (code ?: newCode).trim().uppercase()
    val label = effective.ifEmpty { "?" }
    val fromSheet = effective == Money.home && book.useSheetRateForHome && book.sheetRate != null
    val hasOverride = code != null && book.source(code) == CurrencyBook.Source.CUSTOM
    val locked = fromSheet && !hasOverride
    val perValue = SheetFormat.input(perQar)
    val canSave = effective.length >= 2 && (perValue ?: 0.0) > 0

    EditorSheet(
        title = if (code == null) "Custom rate" else "Set rate",
        onDismiss = onDismiss,
        canSave = canSave || locked,
        onSave = {
            if (!locked) book.setOverride(effective, perValue, newName.trim())
            onDismiss()
        },
    ) {
        if (code == null) {
            FormSection("Currency", "Use this for a rate you got from a money exchange, or anything not in the list.") {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlainField(newCode, "Code, e.g. GOLD or PHP") { newCode = it.uppercase().take(8) }
                    PlainField(newName, "Name (optional)") { newName = it }
                }
            }
        } else {
            SheetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(CurrencyBook.flag(code), fontSize = 34.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(code, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
                        Text(book.name(code), fontSize = 14.sp, color = SheetTheme.secondary)
                    }
                    SourceLabel(book.source(code))
                }
            }
        }
        val sheetRate = book.sheetRate
        if (effective == Money.home && sheetRate != null) {
            FormSection("Sheet", "Your sheet says 1 ${Money.main} = ${SheetFormat.number(sheetRate, 2)} ${Money.home}. Change it in Settings to update every ${Money.home} figure in the sheet.") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Use the sheet's rate", fontSize = 15.sp, color = SheetTheme.text, modifier = Modifier.weight(1f))
                    Switch(book.useSheetRateForHome, { book.useSheetRate(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = SheetTheme.good))
                }
            }
        }
        FormSection("My rate", "Fill either line — the other updates itself.") {
            RateLine("1 ${book.main} =", label, perQar, enabled = !locked) { t ->
                perQar = t
                inQar = SheetFormat.input(t)?.takeIf { it > 0 }?.let { SheetFormat.plain(1 / it, 6) } ?: ""
            }
            RowDivider()
            RateLine("1 $label =", book.main, inQar, enabled = !locked) { t ->
                inQar = t
                perQar = SheetFormat.input(t)?.takeIf { it > 0 }?.let { SheetFormat.plain(1 / it, 6) } ?: ""
            }
        }
        if (hasOverride && code != null) {
            Text(
                "Go back to the ${if (code == Money.home && sheetRate != null) "sheet" else "live"} rate",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.bad, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SheetTheme.card)
                    .clickable { book.setOverride(code, null); onDismiss() }.padding(14.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RateLine(prefix: String, suffix: String, text: String, enabled: Boolean, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(prefix, fontSize = 15.sp, color = SheetTheme.secondary)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = text,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) SheetTheme.text else SheetTheme.tertiary, textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, capitalization = KeyboardCapitalization.None),
            cursorBrush = SolidColor(SheetTheme.navy),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (text.isEmpty()) Text("0", fontSize = 16.sp, color = SheetTheme.tertiary)
                    inner()
                }
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(suffix, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
    }
}
