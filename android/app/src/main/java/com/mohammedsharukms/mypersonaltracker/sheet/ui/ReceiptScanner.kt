package com.mohammedsharukms.mypersonaltracker.sheet.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Reads a receipt on the device (ML Kit text recognition) and pulls out the
 * total, the date and the shop's name to fill in Add expense. Mirrors ReceiptScanner.swift.
 */
object ReceiptReader {
    data class Result(val amount: Double? = null, val date: LocalDate? = null, val merchant: String? = null)

    suspend fun read(context: Context, uri: Uri): Result {
        val lines = suspendCancellableCoroutine { cont ->
            val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
            if (image == null) { cont.resume(emptyList()); return@suspendCancellableCoroutine }
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                .addOnSuccessListener { text ->
                    // Top to bottom, as printed.
                    cont.resume(text.textBlocks.flatMap { it.lines }.sortedBy { it.boundingBox?.top ?: 0 }.map { it.text })
                }
                .addOnFailureListener { cont.resume(emptyList()) }
        }
        return parse(lines)
    }

    private val totalWords = listOf("grand total", "total due", "amount due", "total amount", "net total", "balance due",
        "total", "net amount", "amount", "إجمالي", "المجموع")
    private val skipWords = listOf("subtotal", "sub total", "sub-total", "vat", "tax", "change", "cash", "tendered", "discount", "saving")
    /** Amounts with two decimals: "1,234.50", "12.00", "45,50". */
    private val money = Regex("""(?<![\d.,])(\d{1,3}(?:[,\s]\d{3})+|\d+)[.,](\d{2})(?!\d)""")

    fun amounts(line: String): List<Double> = money.findAll(line).mapNotNull { m ->
        "${m.groupValues[1].replace(",", "").replace(" ", "")}.${m.groupValues[2]}".toDoubleOrNull()
    }.toList()

    private val dateFormats: List<DateTimeFormatter> = listOf("d/M/yyyy", "d-M-yyyy", "d.M.yyyy", "yyyy-M-d", "d/M/yy", "d-M-yy", "d MMM yyyy", "d-MMM-yyyy", "d MMM yy")
        .map { DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH) }
    private val dateLike = Regex("""\b(\d{1,4}[/.\-]\d{1,2}[/.\-]\d{2,4}|\d{1,2}[ \-][A-Za-z]{3}[ \-]\d{2,4})\b""")

    fun parse(lines: List<String>, today: LocalDate = LocalDate.now()): Result {
        // 1. A "total" line (skipping subtotal/VAT/change), its amount or the next line's.
        var amount: Double? = null
        outer@ for (keyword in totalWords) {
            for ((i, line) in lines.withIndex()) {
                val lower = line.lowercase()
                if (keyword !in lower || skipWords.any { it in lower }) continue
                val value = amounts(line).lastOrNull() ?: lines.getOrNull(i + 1)?.let { amounts(it).firstOrNull() }
                if (value != null && value > 0) { amount = value; break@outer }
            }
        }
        // 2. Otherwise the largest amount on the receipt.
        if (amount == null) amount = lines.flatMap { amounts(it) }.filter { it > 0 && it < 1_000_000 }.maxOrNull()

        // Date: the first one in the last 90 days.
        val date = lines.asSequence().flatMap { dateLike.findAll(it).map { m -> m.value } }.mapNotNull { text ->
            dateFormats.firstNotNullOfOrNull { f -> runCatching { LocalDate.parse(text, f) }.getOrNull() }
        }.firstOrNull { !it.isBefore(today.minusDays(90)) && !it.isAfter(today.plusDays(1)) }

        // Shop: the first line near the top that reads like a name.
        val merchant = lines.take(6).firstOrNull { line ->
            val letters = line.count { it.isLetter() }
            val lower = line.lowercase()
            letters >= 3 && letters * 2 >= line.length && line.length <= 32 &&
                listOf("receipt", "invoice", "tax", "welcome", "tel", "phone", "vat").none { it in lower }
        }?.trim()?.split(" ")?.joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.titlecase() } }
        return Result(amount, date, merchant)
    }
}

/** "Scan receipt" for Add expense: the document scanner (with gallery import), read on the device. */
@Composable
fun ReceiptScanButton(onRead: (ReceiptReader.Result) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun read(uri: Uri) {
        reading = true
        scope.launch {
            val result = ReceiptReader.read(context, uri)
            reading = false
            message = if (result.amount == null) "Couldn't find a total — type it in." else "Filled from the receipt — check it."
            onRead(result)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::read) }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.firstOrNull()?.imageUri?.let(::read)
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.clip(CircleShape).background(SheetTheme.navy.copy(alpha = 0.1f)).clickable(enabled = !reading) {
                val activity = context as? Activity ?: return@clickable
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(1)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
                    .build()
                GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                    .addOnSuccessListener { scanner.launch(IntentSenderRequest.Builder(it).build()) }
                    // Scanner unavailable (no Play services): pick a photo instead.
                    .addOnFailureListener { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.DocumentScanner, null, tint = SheetTheme.navy, modifier = Modifier.size(16.dp))
            Text(if (reading) "Reading receipt…" else "Scan receipt", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.navy)
        }
        if (reading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        message?.let { Text(it, fontSize = 11.sp, color = SheetTheme.secondary, maxLines = 2) }
    }
}

/** Picks a category from words in a description or shop name ("coffee" → Food & Dining). */
object CategoryGuess {
    private val words = listOf(
        "Groceries" to listOf("grocery", "groceries", "supermarket", "lulu", "carrefour", "vegetable", "milk"),
        "Food & Dining" to listOf("coffee", "lunch", "dinner", "breakfast", "restaurant", "cafe", "tea", "pizza", "burger", "food", "snack"),
        "Transport" to listOf("uber", "careem", "taxi", "metro", "bus", "fuel", "petrol", "parking", "karwa"),
        "Rent & Utilities" to listOf("rent", "electricity", "water bill", "utility", "kahramaa"),
        "Mobile & Internet" to listOf("recharge", "mobile", "internet", "wifi", "ooredoo", "vodafone", "data"),
        "Health & Pharmacy" to listOf("pharmacy", "medicine", "doctor", "clinic", "hospital"),
        "Personal Care" to listOf("haircut", "salon", "barber", "shampoo"),
        "Clothing" to listOf("shirt", "shoes", "clothes", "jeans", "dress"),
        "Remittance" to listOf("remittance", "sent home", "transfer home"),
        "Entertainment" to listOf("movie", "cinema", "netflix", "game", "concert"),
        "Gifts & Family" to listOf("gift", "family", "birthday"),
        "Education" to listOf("course", "book", "udemy", "class", "exam"),
        "Travel" to listOf("flight", "hotel", "airbnb", "ticket"),
    )

    fun guess(text: String, among: List<String>): String? {
        val lower = text.lowercase()
        if (lower.isBlank()) return null
        return words.firstOrNull { (category, keys) -> category in among && keys.any { it in lower } }?.first
    }
}
