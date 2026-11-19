package com.mohammedsharukms.mypersonaltracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** Redraws all home-screen widgets after data changes. */
object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        runCatching {
            StreakWidget().updateAll(context)
            SpendWidget().updateAll(context)
            TodoWidget().updateAll(context)
            SplitWidget().updateAll(context)
            StudyWidget().updateAll(context)
            ShoppingWidget().updateAll(context)
            StepsWidget().updateAll(context)
        }
    }
}
