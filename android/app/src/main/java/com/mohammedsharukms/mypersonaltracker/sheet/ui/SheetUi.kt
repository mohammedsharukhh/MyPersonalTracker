package com.mohammedsharukms.mypersonaltracker.sheet.ui

import com.mohammedsharukms.mypersonaltracker.container

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.sheet.Habit
import com.mohammedsharukms.mypersonaltracker.sheet.SheetTable
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

// Design system for the Command Center screens: one calm palette built on the
// workbook's navy, semantic good/bad/warn colours for light and dark mode, and
// a handful of building blocks so every screen reads the same way.
// Mirrors SheetComponents.swift.

object SheetTheme {
    @Composable @ReadOnlyComposable private fun pick(light: Long, dark: Long) = Color(if (isSystemInDarkTheme()) dark else light)

    val navy @Composable @ReadOnlyComposable get() = pick(0xFF1F3864, 0xFF6F8FD8)
    val navyDeep = Color(0xFF16294A)
    val good @Composable @ReadOnlyComposable get() = pick(0xFF16A34A, 0xFF4ADE80)
    val bad @Composable @ReadOnlyComposable get() = pick(0xFFDC2626, 0xFFF87171)
    val warn @Composable @ReadOnlyComposable get() = pick(0xFFD97706, 0xFFFBBF24)
    val teal @Composable @ReadOnlyComposable get() = pick(0xFF0F766E, 0xFF2DD4BF)
    val purple @Composable @ReadOnlyComposable get() = pick(0xFF7C3AED, 0xFFA78BFA)
    val sky @Composable @ReadOnlyComposable get() = pick(0xFF0284C7, 0xFF38BDF8)
    val indigo @Composable @ReadOnlyComposable get() = pick(0xFF4F46E5, 0xFF818CF8)

    val page @Composable @ReadOnlyComposable get() = pick(0xFFF2F2F7, 0xFF000000)
    /** Frosted: translucent, so the tab's colour wash glows through. */
    val card @Composable @ReadOnlyComposable get() = pick(0xC7FFFFFF, 0xC71C1C1E)
    /** Opaque, for bars that sit over content. */
    val cardSolid @Composable @ReadOnlyComposable get() = pick(0xFFFFFFFF, 0xFF1C1C1E)
    /** The light edge on a frosted card. */
    val glassEdge @Composable @ReadOnlyComposable get() = pick(0x99FFFFFF, 0x14FFFFFF)
    val input @Composable @ReadOnlyComposable get() = pick(0xFFEBEBF0, 0xFF2C2C2E)
    val text @Composable @ReadOnlyComposable get() = pick(0xFF000000, 0xFFFFFFFF)
    val secondary @Composable @ReadOnlyComposable get() = pick(0x993C3C43, 0x99EBEBF5)
    val tertiary @Composable @ReadOnlyComposable get() = pick(0x4D3C3C43, 0x4DEBEBF5)

    /** The hero banner in the colours of the tab it's on (navy elsewhere): deep corner to a bright glow. */
    val heroGradient: Brush @Composable @ReadOnlyComposable get() = LocalTabWorld.current.let { w ->
        Brush.linearGradient(0f to Color(w.deep), 0.55f to Color(w.mid), 1f to Color(w.bright),
            start = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY), end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f))
    }
    /** Behind a tab's page: the page colour with the tab's colour glowing in from the top. */
    val pageWash: Brush @Composable @ReadOnlyComposable get() = LocalTabWorld.current.accent.let { a ->
        val base = page
        Brush.verticalGradient(0f to androidx.compose.ui.graphics.lerp(base, a, 0.18f), 0.3f to androidx.compose.ui.graphics.lerp(base, a, 0.06f), 0.6f to base)
    }
    val savingsGradient = Brush.linearGradient(listOf(Color(0xFF0F5132), Color(0xFF0F766E)))
    val heroGreen = Color(0xFF4ADE80)
    val heroRed = Color(0xFFF87171)
    val heroAmber = Color(0xFFFBBF24)

    /** Colour for the sheet's status words. */
    @Composable @ReadOnlyComposable
    fun tintForStatus(status: String?): Color = when (status?.lowercase()) {
        "excellent", "goal met", "ok" -> good
        "on track" -> warn
        "needs work", "below goal", "over" -> bad
        else -> secondary
    }

    /** Colour for a 0…1 rate: green from 80%, amber from 50%, red below. */
    @Composable @ReadOnlyComposable
    fun tintForRate(rate: Double): Color = if (rate >= 0.8) good else if (rate >= 0.5) warn else bad

    @Composable @ReadOnlyComposable
    fun categoryColor(category: String?): Color = when (category) {
        "Groceries" -> pick(0xFF16A34A, 0xFF4ADE80)
        "Food & Dining" -> pick(0xFFEA580C, 0xFFFB923C)
        "Transport" -> pick(0xFF2563EB, 0xFF60A5FA)
        "Rent & Utilities" -> pick(0xFF7C3AED, 0xFFA78BFA)
        "Mobile & Internet" -> pick(0xFF0891B2, 0xFF22D3EE)
        "Health & Pharmacy" -> pick(0xFFDC2626, 0xFFF87171)
        "Personal Care" -> pick(0xFFDB2777, 0xFFF472B6)
        "Household" -> pick(0xFF92400E, 0xFFD6A26B)
        "Clothing" -> pick(0xFFC026D3, 0xFFE879F9)
        "Remittance" -> pick(0xFF0F766E, 0xFF2DD4BF)
        "Entertainment" -> pick(0xFF9333EA, 0xFFC084FC)
        "Gifts & Family" -> pick(0xFFE11D48, 0xFFFB7185)
        "Education" -> pick(0xFF4F46E5, 0xFF818CF8)
        "Travel" -> pick(0xFF0284C7, 0xFF38BDF8)
        else -> pick(0xFF8E8E93, 0xFF8E8E93)
    }

    fun categoryIcon(category: String?): ImageVector = when (category) {
        "Groceries" -> Icons.Filled.ShoppingCart
        "Food & Dining" -> Icons.Filled.Restaurant
        "Transport" -> Icons.Filled.DirectionsCar
        "Rent & Utilities" -> Icons.Filled.Home
        "Mobile & Internet" -> Icons.Filled.Wifi
        "Health & Pharmacy" -> Icons.Filled.LocalHospital
        "Personal Care" -> Icons.Filled.Spa
        "Household" -> Icons.Filled.Chair
        "Clothing" -> Icons.Filled.Checkroom
        "Remittance" -> Icons.AutoMirrored.Filled.Send
        "Entertainment" -> Icons.Filled.Movie
        "Gifts & Family" -> Icons.Filled.CardGiftcard
        "Education" -> Icons.Filled.School
        "Travel" -> Icons.Filled.Flight
        else -> Icons.Filled.LocalOffer
    }

    fun methodIcon(method: String): ImageVector = when (method) {
        "Cash" -> Icons.Filled.Payments
        "Debit Card", "Credit Card" -> Icons.Filled.CreditCard
        "Apple Pay / Google Pay" -> Icons.Filled.Smartphone
        "Bank Transfer" -> Icons.Filled.AccountBalance
        else -> Icons.Filled.MoreHoriz
    }

    fun habitIcon(habit: Habit): ImageVector = when (habit) {
        Habit.OFFICE -> Icons.Filled.Business
        Habit.BREAKFAST -> Icons.Filled.WbTwilight
        Habit.LUNCH -> Icons.Filled.Restaurant
        Habit.PROTEIN -> Icons.Filled.MonitorHeart
        Habit.WORKOUT -> Icons.AutoMirrored.Filled.DirectionsRun
    }

    /** Icons for the habit-rates rows, matched by name. */
    fun rateIcon(name: String): ImageVector {
        val h = name.lowercase()
        return when {
            "office" in h -> Icons.Filled.Business
            "breakfast" in h -> Icons.Filled.WbTwilight
            "protein" in h -> Icons.Filled.MonitorHeart
            "lunch" in h -> Icons.Filled.Restaurant
            "workout" in h -> Icons.AutoMirrored.Filled.DirectionsRun
            "water" in h -> Icons.Filled.WaterDrop
            "sleep" in h -> Icons.Filled.Bed
            "steps" in h -> Icons.AutoMirrored.Filled.DirectionsWalk
            "screen" in h -> Icons.Filled.PhoneAndroid
            else -> Icons.Filled.CheckCircle
        }
    }
}

/** Rounded numbers for big figures, like SF Rounded on iOS. */
val Rounded: FontFamily = FontFamily.SansSerif

// MARK: - Formatting (as the sheet formats them)

object SheetFormat {
    private fun nf(min: Int, max: Int) = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = min
        maximumFractionDigits = max
    }

    fun qar(value: Double?): String = nf(2, 2).format(value ?: 0.0)
    fun inr(value: Double?): String = nf(0, 0).format(value ?: 0.0)

    /** A fraction as "75.0%". */
    fun percent(fraction: Double?, digits: Int = 1): String = nf(digits, digits).format((fraction ?: 0.0) * 100) + "%"

    fun number(value: Double?, digits: Int = 1): String = value?.let { nf(0, digits).format(it) } ?: "—"

    /** Plain text for an input field ("12.5", no grouping). */
    fun plain(value: Double?, digits: Int = 2): String =
        value?.let { java.math.BigDecimal(it).setScale(digits, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() } ?: ""

    /** Parses the sheet's display text ("2,015.00", "75.0%", "(1,915.00)") back to a number. */
    fun parse(text: String): Double? {
        var t = text.replace(",", "").trim()
        var sign = 1.0
        if (t.startsWith("(") && t.endsWith(")")) { sign = -1.0; t = t.drop(1).dropLast(1) }
        if (t.endsWith("%")) return t.dropLast(1).toDoubleOrNull()?.let { sign * it / 100 }
        return t.toDoubleOrNull()?.let { sign * it }
    }

    /** Lenient user input: "12,5" and "12.5" both work. */
    fun input(text: String): Double? = text.replace(",", ".").trim().toDoubleOrNull()?.takeIf { it.isFinite() }   // "Infinity" would crash JSONObject.put
}

/** The workbook's two currencies, from its Settings sheet. Read in composition, they follow changes. */
object Money {
    internal var store: com.mohammedsharukms.mypersonaltracker.sheet.SheetStore? = null

    val main: String get() = store?.snapshot?.settings?.mainCurrency ?: "QAR"
    val home: String get() = store?.snapshot?.settings?.homeCurrency ?: "INR"

    /** "₹", "$", "€" — or the code itself when there's no distinct symbol. */
    fun symbol(code: String): String =
        runCatching { java.util.Currency.getInstance(code).getSymbol(java.util.Locale.ENGLISH) }.getOrNull() ?: code

    /** An already formatted amount in the home currency: "₹2,610" or "PKR 2,610". */
    fun homeText(formatted: String): String = symbol(home).let { if (it.length == 1) it + formatted else "$home $formatted" }

    fun homeAmount(value: Double?): String = homeText(SheetFormat.inr(value))

    /** Everything in one currency (main = home): no converter, no "≈ other currency". */
    val single: Boolean get() = main == home
}

/** Where a currency picker would be, when there's only one currency. */
@Composable
fun CurrencyPill(code: String) {
    Row(Modifier.clip(CircleShape).background(SheetTheme.input).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(com.mohammedsharukms.mypersonaltracker.sheet.CurrencyBook.flag(code), fontSize = 18.sp)
        Text(code, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
    }
}

object SheetDate {
    fun date(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val today: String get() = LocalDate.now().toString()

    /** "15 Aug 2026" */
    fun short(text: String?): String = date(text)?.format(DateTimeFormatter.ofPattern("d MMM yyyy")) ?: text.orEmpty()

    /** "Mon, 15 Aug" */
    fun weekdayDay(text: String?): String = date(text)?.format(DateTimeFormatter.ofPattern("EEE, d MMM")) ?: text.orEmpty()

    fun relative(millis: Long): String {
        val minutes = (System.currentTimeMillis() - millis) / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 60 * 24 -> "${minutes / 60} h ago"
            else -> "${minutes / (60 * 24)} days ago"
        }
    }
}

// MARK: - Building blocks

/** The standard surface: rounded, filled with the card colour. */
@Composable
fun SheetCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SheetTheme.card)
            .border(0.75.dp, SheetTheme.glassEdge, RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** A gradient hero card at the top of a screen. */
@Composable
fun HeroBox(brush: Brush = SheetTheme.heroGradient, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(24.dp)).background(brush).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 8.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text)
        if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = SheetTheme.secondary)
    }
}

/** Tinted icon in a soft rounded square. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 34.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

/** One KPI: icon, label, big value, caption. */
@Composable
fun StatTile(icon: ImageVector, tint: Color, title: String, value: String, modifier: Modifier = Modifier, caption: String? = null, valueTint: Color = SheetTheme.text) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(SheetTheme.card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBadge(icon, tint, 30.dp)
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary, maxLines = 2)
        }
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = valueTint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(caption ?: " ", fontSize = 11.sp, color = SheetTheme.tertiary, maxLines = 1)
    }
}

@Composable
fun Bar(fraction: Double, tint: Color, track: Color = SheetTheme.input, height: Dp = 7.dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.0, 1.0).toFloat()).height(height).clip(CircleShape).background(tint))
    }
}

/** Title, trailing detail, and a rounded bar filled to `fraction`. */
@Composable
fun ProgressRow(title: String, detail: String, fraction: Double, tint: Color, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) IconBadge(icon, tint, 30.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(title, fontSize = 15.sp, color = SheetTheme.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            }
            Bar(fraction, tint)
        }
    }
}

/** Label ··· value, as in the sheet's two-column tables. */
@Composable
fun KeyValueRow(key: String, value: String, valueTint: Color = SheetTheme.text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(key, fontSize = 15.sp, color = SheetTheme.secondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = valueTint)
    }
}

/** Coloured capsule for a status word ("Excellent", "OVER", "Goal met"). */
@Composable
fun StatusPill(text: String, tint: Color? = null) {
    val color = tint ?: SheetTheme.tintForStatus(text)
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Small translucent chip for the hero cards. */
@Composable
fun HeroChip(icon: ImageVector, text: String) {
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

/** Ring showing a 0…1 value, with content in the middle. */
@Composable
fun ScoreRing(
    fraction: Double,
    size: Dp,
    stroke: Dp = 8.dp,
    tint: Color = Color.White,
    track: Color = Color.White.copy(alpha = 0.2f),
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val inset = s / 2
            val arcSize = Size(this.size.width - s, this.size.height - s)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(s))
            drawArc(tint, -90f, 360f * fraction.coerceIn(0.0, 1.0).toFloat(), false, Offset(inset, inset), arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Selectable capsule used for filters. */
@Composable
fun FilterPill(title: String, icon: ImageVector?, tint: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(if (selected) tint else SheetTheme.card).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (selected) Color.White else tint, modifier = Modifier.size(15.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else tint, maxLines = 1, softWrap = false)
    }
}

/** Tap-to-select chips that wrap, each with an icon. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGrid(items: List<String>, selection: String, onSelect: (String) -> Unit, style: @Composable (String) -> Pair<ImageVector, Color>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val (icon, tint) = style(item)
            val selected = item == selection
            Row(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (selected) tint else tint.copy(alpha = 0.1f))
                    .clickable { onSelect(item) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, null, tint = if (selected) Color.White else tint, modifier = Modifier.size(16.dp))
                Text(item, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else tint)
            }
        }
    }
}

/** Any sheet table as the sheet displays it — for the wide Weekly and Monthly summaries. */
@Composable
fun SheetTableView(table: SheetTable, minColumnWidth: Dp = 84.dp) {
    val good = SheetTheme.good
    val bad = SheetTheme.bad
    val warn = SheetTheme.warn
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SheetTheme.card)) {
        if (table.rows.isEmpty()) {
            Text("Nothing here yet.", fontSize = 13.sp, color = SheetTheme.secondary, modifier = Modifier.padding(24.dp).align(Alignment.Center))
        } else {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row(Modifier.background(SheetTheme.navy.copy(alpha = 0.08f))) {
                    table.headers.forEachIndexed { i, h -> Cell(h, i, minColumnWidth, header = true, color = SheetTheme.navy) }
                }
                table.rows.forEach { row ->
                    HorizontalDivider(Modifier.width(minColumnWidth * table.headers.size + 24.dp * table.headers.size))
                    Row {
                        row.forEachIndexed { i, value ->
                            val color = when (value.lowercase()) {
                                "excellent", "goal met", "ok" -> good
                                "on track" -> warn
                                "needs work", "below goal", "over" -> bad
                                else -> if (value.startsWith("(")) bad else SheetTheme.text
                            }
                            Cell(value, i, minColumnWidth, header = false, color = color)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(text: String, column: Int, width: Dp, header: Boolean, color: Color) {
    Text(
        text.ifEmpty { " " },
        fontSize = if (header) 12.sp else 15.sp,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
        color = color,
        textAlign = if (column == 0) TextAlign.Start else TextAlign.End,
        maxLines = 2,
        modifier = Modifier.widthIn(min = width).width(width).padding(horizontal = 12.dp, vertical = 11.dp),
    )
}

/** Shown until the app can reach the sheet. */
@Composable
fun ConnectSheetCard(onOpenSettings: () -> Unit) {
    SheetCard(padding = 20.dp) {
        IconBadge(Icons.Filled.TableChart, SheetTheme.navy, 44.dp)
        Spacer(Modifier.height(14.dp))
        Text("Connect your Google Sheet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text)
        Spacer(Modifier.height(8.dp))
        Text(
            "Everything here reads from and saves to your Life & Finance Command Center sheet. Sign in with Google and the app makes it in your own Google Drive — or finds the one you already have.",
            fontSize = 15.sp, color = SheetTheme.secondary,
        )
        Spacer(Modifier.height(14.dp))
        GoogleSignInButton(androidx.compose.ui.platform.LocalContext.current.container.sheet)
        SmallTextButton("Other ways to connect", onClick = onOpenSettings)
    }
}

@Composable
fun NotConnectedHint() {
    SheetCard { Text("Sign in with Google in More ▸ Settings & connection to connect your sheet.", fontSize = 15.sp, color = SheetTheme.secondary) }
}

/** Error line with a retry. */
@Composable
fun SheetErrorBanner(message: String, retry: () -> Unit) {
    val bad = SheetTheme.bad
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bad.copy(alpha = 0.1f)).padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, null, tint = bad, modifier = Modifier.size(18.dp))
        Text(message, fontSize = 13.sp, color = SheetTheme.text, modifier = Modifier.weight(1f))
        Text("Retry", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = bad, modifier = Modifier.clickable(onClick = retry))
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SheetTheme.navy, contentColor = Color.White),
    ) {
        if (loading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SmallTextButton(text: String, color: Color = SheetTheme.navy, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(text, color = color, fontWeight = FontWeight.SemiBold) }
}

// MARK: - Charts (drawn on Canvas; the iOS app uses Swift Charts)

@Composable
fun ChartCard(title: String, subtitle: String?, content: @Composable () -> Unit) {
    SheetCard {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
        if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = SheetTheme.secondary)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(190.dp)) { content() }
    }
}

@Composable
fun EmptyChart(text: String) {
    Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = SheetTheme.secondary)
    }
}

/** Line + area of 0…1 values, with an optional dashed threshold. */
@Composable
fun LineAreaChart(values: List<Double>, threshold: Double?, lineColor: Color, thresholdColor: Color) {
    val label = SheetTheme.tertiary
    Canvas(Modifier.fillMaxWidth().height(190.dp)) {
        val left = 36.dp.toPx()
        val w = size.width - left
        val h = size.height - 8.dp.toPx()
        fun y(v: Double) = (h - (v.coerceIn(0.0, 1.0) * h)).toFloat() + 4.dp.toPx()
        listOf(0.0, 0.5, 1.0).forEach { v ->
            drawLine(label.copy(alpha = 0.25f), Offset(left, y(v)), Offset(size.width, y(v)), 1f)
            drawContext.canvas.nativeCanvas.drawText(
                "${(v * 100).toInt()}%", 0f, y(v) + 4.dp.toPx(),
                android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 10.sp.toPx(); isAntiAlias = true },
            )
        }
        if (values.isEmpty()) return@Canvas
        val step = if (values.size > 1) w / (values.size - 1) else 0f
        val points = values.mapIndexed { i, v -> Offset(left + i * step, y(v)) }
        val line = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                val p0 = points[i - 1]
                val p1 = points[i]
                val cx = (p0.x + p1.x) / 2
                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, y(0.0))
            lineTo(points.first().x, y(0.0))
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0.02f))))
        drawPath(line, lineColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        if (threshold != null) {
            drawLine(thresholdColor.copy(alpha = 0.7f), Offset(left, y(threshold)), Offset(size.width, y(threshold)), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
    }
}

/** Bars with an optional dashed cap; bars above the cap use `overColor`. */
@Composable
fun BarChart(values: List<Double>, cap: Double?, color: Color, overColor: Color, capColor: Color) {
    Canvas(Modifier.fillMaxWidth().height(190.dp)) {
        if (values.isEmpty()) return@Canvas
        val top = max(values.maxOrNull() ?: 0.0, cap ?: 0.0).takeIf { it > 0 } ?: 1.0
        val slot = size.width / values.size
        val barW = max(2f, slot * 0.6f)
        values.forEachIndexed { i, v ->
            val bh = (v / top * size.height).toFloat()
            drawRoundRect(
                if (cap != null && v > cap) overColor else color,
                topLeft = Offset(i * slot + (slot - barW) / 2, size.height - bh),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
        if (cap != null) {
            val y = (size.height - cap / top * size.height).toFloat()
            drawLine(capColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
    }
}

/** Labelled 0…1 bars, value shown above each. */
@Composable
fun LabeledBars(items: List<Pair<String, Double>>, colorFor: @Composable (Double) -> Color) {
    Row(Modifier.fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        items.forEach { (label, value) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(SheetFormat.percent(value, 0), fontSize = 11.sp, color = SheetTheme.secondary)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().height((140 * min(value, 1.0)).dp.coerceAtLeast(2.dp))
                        .clip(RoundedCornerShape(6.dp)).background(colorFor(value)),
                )
                Spacer(Modifier.height(6.dp))
                Text(label, fontSize = 12.sp, color = SheetTheme.secondary)
            }
        }
    }
}

/** A plain inline text button for links like "Retry". */
@Composable
fun LinkText(text: String, color: Color, size: TextUnit = 13.sp, onClick: () -> Unit) {
    Text(text, fontSize = size, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.clickable(onClick = onClick))
}

/**
 * Each tab's colour world: its hero, the wash behind its page and its tab-bar highlight.
 * White text stays above 4.5:1 on every hero colour. Mirrors TabWorld in SheetComponents.swift.
 */
enum class TabWorld(val deep: Long, val mid: Long, val bright: Long, private val darkAccent: Long) {
    NEUTRAL(0xFF16294A, 0xFF1F3864, 0xFF2F5597, 0xFF6F8FD8),
    DASHBOARD(0xFF1E1B4B, 0xFF3730A3, 0xFF6D28D9, 0xFFA78BFA),
    DAILY(0xFF042F2E, 0xFF115E59, 0xFF0F766E, 0xFF2DD4BF),
    PARENTS(0xFF4C0519, 0xFF881337, 0xFFBE123C, 0xFFFB7185),
    STUDY(0xFF431407, 0xFF7C2D12, 0xFFC2410C, 0xFFFB923C),
    FOOD(0xFF052E16, 0xFF14532D, 0xFF15803D, 0xFF4ADE80),
    FINANCE(0xFF172554, 0xFF1E3A8A, 0xFF1D4ED8, 0xFF60A5FA);

    /** The tab's colour on the page: bright in light mode, lifted in dark. */
    val accent: Color @Composable @ReadOnlyComposable get() = Color(if (androidx.compose.foundation.isSystemInDarkTheme()) darkAccent else bright)
}

/** Set per tab in MainActivity; screens opened elsewhere stay neutral (navy). */
val LocalTabWorld = androidx.compose.runtime.staticCompositionLocalOf { TabWorld.NEUTRAL }
