package com.gowaist.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.gowaist.app.MainActivity
import com.gowaist.app.R
import com.gowaist.app.data.db.GoWaistDatabase
import com.gowaist.app.data.toLocalDate
import com.gowaist.core.Format
import com.gowaist.core.stats.Streaks
import com.gowaist.core.stats.weekStart
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun database(): GoWaistDatabase
}

object WidgetUpdater {
    suspend fun update(context: Context) {
        runCatching { GoWaistWidget().updateAll(context) }
    }
}

private data class WidgetData(val weekKm: Double, val lastKm: Double?, val lastDate: LocalDate?, val streak: Int)

/** Home-screen widget: distance this week, the latest run and the running streak. */
class GoWaistWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { GlanceTheme { Content(data) } }
    }

    private suspend fun load(context: Context): WidgetData {
        val db = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).database()
        val today = LocalDate.now()
        val runs = db.runDao().getAll()
        val ws = today.weekStart()
        val week = runs.filter { !it.localDate.toLocalDate().isBefore(ws) }.sumOf { it.distanceM } / 1000
        val last = runs.firstOrNull()
        return WidgetData(week, last?.distanceM?.div(1000), last?.localDate?.toLocalDate(), Streaks.current(runs.map { it.localDate.toLocalDate() }.toSet(), emptySet(), today))
    }

    @Composable
    private fun Content(d: WidgetData) {
        val cream = ColorProvider(Color(0xFFF4ECDD))
        val ink = ColorProvider(Color(0xFF2A2420))
        val muted = ColorProvider(Color(0xFF6B6055))
        val accent = ColorProvider(Color(0xFFC9533A))
        Column(
            GlanceModifier.fillMaxSize().background(cream).cornerRadius(16.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text("Go waist", style = TextStyle(color = ink, fontWeight = FontWeight.Bold, fontSize = 13.sp))
            Spacer(GlanceModifier.height(6.dp))
            Text(LocalContextString(R.string.home_this_week), style = TextStyle(color = muted, fontSize = 12.sp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Format.decimal(d.weekKm, 1), style = TextStyle(color = ink, fontWeight = FontWeight.Bold, fontSize = 26.sp))
                Spacer(GlanceModifier.width(4.dp))
                Text(LocalContextString(R.string.unit_km), style = TextStyle(color = muted, fontSize = 13.sp))
            }
            Spacer(GlanceModifier.defaultWeight())
            if (d.lastKm != null && d.lastDate != null) {
                Text(
                    LocalContextString(R.string.home_last_run) + " " + Format.decimal(d.lastKm, 2) + " " + LocalContextString(R.string.unit_km) + " · " + d.lastDate.dayOfMonth + "/" + d.lastDate.monthValue,
                    style = TextStyle(color = ink, fontSize = 12.sp), maxLines = 1,
                )
            }
            if (d.streak > 0) {
                Text(LocalContextString(R.string.widget_streak) + " " + d.streak + " " + LocalContextString(R.string.widget_streak_days), style = TextStyle(color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp))
            }
        }
    }

    @Composable
    private fun LocalContextString(id: Int): String = androidx.glance.LocalContext.current.getString(id)
}

class GoWaistWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoWaistWidget()
}
