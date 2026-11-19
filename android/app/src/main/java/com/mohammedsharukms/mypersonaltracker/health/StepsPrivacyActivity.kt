package com.mohammedsharukms.mypersonaltracker.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsharukms.mypersonaltracker.ui.theme.TrackerTheme

/** What Health Connect shows when you ask why this app wants your steps. */
class StepsPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrackerTheme {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Your steps", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("My Personal Tracker reads your daily step count from Health Connect and writes it into the Steps column of your own Google Sheet, so you don't have to type it in. It reads today and the last 30 days, each time you open the app.")
                    Text("It only reads steps — nothing else — and never writes anything to Health Connect. A day with only steps still counts as not logged, and a higher number you typed yourself is kept.")
                    Text("You can stop it any time in the app's Settings, or in Health Connect ▸ App permissions.")
                    TextButton(onClick = { finish() }) { Text("Close") }
                }
            }
        }
    }
}
