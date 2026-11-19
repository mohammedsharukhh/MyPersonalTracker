package com.mohammedsharukms.mypersonaltracker.sheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MonitorWeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.sheet.BodyCalc
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.StudyFormat
import com.mohammedsharukms.mypersonaltracker.sheet.latestWeight
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate

// Calorie and protein targets worked out from your weight (Mifflin–St Jeor),
// saved to the sheet — which keeps them following your weight. Mirrors BodyTargets.swift.

/** Your details in, suggested daily targets out — and saved to the sheet. */
@Composable
fun BodyTargetsSheet(store: SheetStore, onDismiss: () -> Unit) {
    val snapshot = store.snapshot
    val set = snapshot?.settings
    val thisYear = LocalDate.now().year
    var sex by remember { mutableStateOf(BodyCalc.Sex.of(set?.sex) ?: BodyCalc.Sex.MALE) }
    var age by remember { mutableStateOf(set?.birthYear?.let { (thisYear - it.toInt()).toString() } ?: "") }
    var height by remember { mutableStateOf(SheetFormat.plain(set?.heightCm, 1)) }
    var weight by remember { mutableStateOf(SheetFormat.plain(snapshot?.latestWeight?.first, 1)) }
    var activity by remember { mutableStateOf(BodyCalc.Activity.of(set?.activity) ?: BodyCalc.Activity.LIGHT) }
    var goal by remember { mutableStateOf(BodyCalc.Goal.of(set?.bodyGoal) ?: BodyCalc.Goal.MAINTAIN) }
    var perKg by remember { mutableStateOf(set?.proteinPerKg ?: goal.proteinPerKg) }
    var follow by remember { mutableStateOf(set?.followWeight ?: true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val a = age.toIntOrNull()
    val h = SheetFormat.input(height)
    val w = SheetFormat.input(weight)
    val result = if (a != null && a > 0 && h != null && h > 50 && w != null && w > 20)
        BodyCalc.targets(w, h, a, sex, activity, goal, perKg) else null

    EditorSheet("Targets from my weight", onDismiss, saveLabel = "Use these", canSave = result != null, saving = saving, error = error, onSave = {
        saving = true
        error = null
        scope.launch {
            val ok = store.saveBody(JSONObject().put("sex", sex.label).put("birthYear", thisYear - a!!).put("height", h!!).put("weight", w!!)
                .put("activity", activity.label).put("goal", goal.label).put("proteinPerKg", Math.round(perKg * 10) / 10.0).put("follow", follow))
            saving = false
            if (ok) onDismiss() else error = store.errorMessage ?: "Couldn't save."
        }
    }) {
        Field("About you") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BodyCalc.Sex.entries.forEach { s -> FilterPill(s.label, null, SheetTheme.navy, sex == s) { sex = s } }
            }
        }
        BodyNumber("Age", age, "years") { age = it }
        BodyNumber("Height", height, "cm") { height = it }
        BodyNumber("Weight", weight, "kg") { weight = it }
        snapshot?.latestWeight?.second?.let {
            Text("Weight from your Daily Tracker on ${SheetDate.short(it)}. Log your weight there and the targets follow it.", fontSize = 12.sp, color = SheetTheme.secondary)
        }
        FormSection("How active are you?") {
            BodyCalc.Activity.entries.forEachIndexed { i, level ->
                if (i > 0) RowDivider()
                Choice(level.label, level.explanation, activity == level) { activity = level }
            }
        }
        FormSection("Your goal") {
            BodyCalc.Goal.entries.forEachIndexed { i, g ->
                if (i > 0) RowDivider()
                Choice(g.label, g.explanation, goal == g) { goal = g; perKg = g.proteinPerKg }
            }
        }
        Field("Protein: ${StudyFormat.number(perKg)} g per kg") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1.2, 1.6, 1.8, 2.0, 2.2).forEach { v -> FilterPill(StudyFormat.number(v), null, SheetTheme.navy, Math.abs(perKg - v) < 0.01) { perKg = v } }
            }
            Text("1.6 g/kg suits most people; 1.8–2.2 when losing fat or lifting, to keep muscle.", fontSize = 12.sp, color = SheetTheme.secondary)
        }
        if (result != null) {
            SheetCard {
                Text("Your daily targets", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.secondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(SheetFormat.number(result.calories, 0), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = SheetTheme.text)
                    Spacer(Modifier.width(6.dp))
                    Text("kcal a day", color = SheetTheme.secondary, modifier = Modifier.padding(bottom = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Macro("Protein", result.protein, SheetTheme.good, Modifier.weight(1f))
                    Macro("Carbs", result.carbs, SheetTheme.sky, Modifier.weight(1f))
                    Macro("Fat", result.fat, SheetTheme.purple, Modifier.weight(1f))
                }
                KeyValueRow("Burned at rest (BMR)", "${SheetFormat.number(result.bmr, 0)} kcal")
                KeyValueRow("Maintenance (× ${activity.factor})", "${SheetFormat.number(result.maintenance, 0)} kcal")
                KeyValueRow(goal.label, if (goal.adjustment == 0.0) "±0" else "${if (goal.adjustment > 0) "+" else ""}${goal.adjustment.toInt()} kcal")
            }
            ToggleRow("Follow my weight", follow,
                if (follow) "When you log a new weight, the sheet recalculates these targets." else "Saved once as fixed numbers.") { follow = it }
        } else {
            Text("Enter your age, height and weight to see your targets.", color = SheetTheme.secondary)
        }
    }
}

@Composable
private fun BodyNumber(title: String, value: String, unit: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SheetTheme.card).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, color = SheetTheme.text, modifier = Modifier.weight(1f))
        Column(Modifier.width(110.dp)) { PlainField(value, "0") { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) } }
        Spacer(Modifier.width(8.dp))
        Text(unit, color = SheetTheme.secondary, modifier = Modifier.width(44.dp))
    }
}

@Composable
private fun Choice(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            Text(detail, fontSize = 12.sp, color = SheetTheme.secondary)
        }
        if (selected) Icon(Icons.Filled.Check, "Selected", tint = SheetTheme.navy)
    }
}

@Composable
private fun Macro(title: String, grams: Double, tint: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${SheetFormat.number(grams, 0)} g", fontWeight = FontWeight.Bold, color = SheetTheme.text)
        Text(title, fontSize = 11.sp, color = SheetTheme.secondary)
    }
}

/** Under the Food hero: where the targets come from, and the way to set them from your weight. */
@Composable
fun BodyTargetsLink(snapshot: SheetSnapshot, onClick: () -> Unit) {
    val s = snapshot.settings
    val following = s.followWeight == true && s.heightCm != null
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SheetTheme.card).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Icons.Filled.MonitorWeight, SheetTheme.teal, 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (following) "Targets follow your weight" else "Set targets from your weight", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetTheme.text)
            Text(if (following) listOfNotNull(snapshot.latestWeight?.let { "${SheetFormat.number(it.first)} kg" }, s.bodyGoal,
                "${SheetFormat.number(s.proteinTarget, 0)} g protein").joinToString(" · ") else "Calories and protein worked out for your body and goal",
                fontSize = 12.sp, color = SheetTheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SheetTheme.tertiary)
    }
}
