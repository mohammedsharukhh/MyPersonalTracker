package com.mohammedsharukms.mypersonaltracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.mohammedsharukms.mypersonaltracker.R
import com.mohammedsharukms.mypersonaltracker.data.ExpenseCategory

/** Handwriting for the sticky note (Caveat, OFL). */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Handwriting = FontFamily(
    Font(R.font.caveat, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(450))),
    Font(R.font.caveat, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

object TrackerColors {
    val Paper = Color(0xFFFFEB85)
    val PaperFold = Color(0xFFE3C567)
    val Ink = Color(0xFF382E0F)
    val Flame = Color(0xFFF97316)
    val Success = Color(0xFF16A34A)
    val HeatEmpty = Color(0x1F808080)
    val Heat = Color(0xFF22C55E)

    fun category(category: ExpenseCategory): Color = when (category) {
        ExpenseCategory.FOOD -> Color(0xFFF59E0B)
        ExpenseCategory.TRANSPORT -> Color(0xFF3B82F6)
        ExpenseCategory.BILLS -> Color(0xFF8B5CF6)
        ExpenseCategory.LEARNING -> Color(0xFF22C55E)
        ExpenseCategory.OTHER -> Color(0xFF9CA3AF)
    }

    /** Distinct colours for the activity chart. */
    val series = listOf(Color(0xFF3B82F6), Color(0xFFF97316), Color(0xFF8B5CF6), Color(0xFF14B8A6), Color(0xFF9CA3AF))
}

@Composable
fun TrackerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
