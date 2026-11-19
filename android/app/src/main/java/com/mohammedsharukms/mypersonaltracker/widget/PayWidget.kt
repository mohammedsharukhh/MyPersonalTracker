package com.mohammedsharukms.mypersonaltracker.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import com.mohammedsharukms.mypersonaltracker.R
import androidx.glance.layout.size
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.Spacer
import androidx.glance.layout.Row
import androidx.glance.layout.Box
import androidx.glance.ImageProvider
import androidx.glance.Image
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mohammedsharukms.mypersonaltracker.MainActivity

/** One tap to Pay with UPI: opens the app on the Pay screen (scan, amount, app). Mirrors PayWidget.swift. */
class PayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, "scan")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val white = ColorProvider(Color.White, Color.White)
        val soft = ColorProvider(Color(0xBFFFFFFF), Color(0xBFFFFFFF))
        provideContent {
            // Dark card, "Scan. Pay. Track it." top left, ₹ top right, a round green scan button bottom left.
            Column(
                GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_pay_bg)).cornerRadius(24.dp)
                    .padding(14.dp).clickable(actionStartActivity(open)),
            ) {
                Row(GlanceModifier.fillMaxWidth()) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text("Scan. Pay.", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = soft))
                        Text("Track it.", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = white))
                    }
                    Image(ImageProvider(R.drawable.ic_widget_rupee), "Pay with UPI", GlanceModifier.size(22.dp))
                }
                Spacer(GlanceModifier.defaultWeight())
                Box(GlanceModifier.size(52.dp).background(ImageProvider(R.drawable.widget_pay_button)).cornerRadius(26.dp),
                    contentAlignment = Alignment.Center) {
                    Image(ImageProvider(R.drawable.ic_widget_qr), "Scan and pay", GlanceModifier.size(26.dp))
                }
            }
        }
    }
}

class PayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PayWidget()
}
