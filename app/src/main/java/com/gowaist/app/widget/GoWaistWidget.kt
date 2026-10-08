package com.gowaist.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.gowaist.app.MainActivity
import com.gowaist.app.R
import com.gowaist.app.data.db.GoWaistDatabase
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.ui.components.labelRes
import com.gowaist.core.Format
import com.gowaist.core.model.PlanDayStatus
import com.gowaist.core.model.PlanDayType
import com.gowaist.core.stats.Streaks
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

private data class WidgetData(val plan: String, val status: String?, val streak: Int)

/** Home-screen widget: today's plan and the current streak. */
class GoWaistWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { GlanceTheme { Content(data) } }
    }

    private suspend fun load(context: Context): WidgetData {
        val db = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).database()
        val today = LocalDate.now()
        val days = db.planDao().activeDays(today.toString(), today.toString())
        val day = days.firstOrNull()
        val plan = when {
            day == null -> context.getString(R.string.widget_no_plan)
            else -> context.getString(day.type.labelRes()) + (day.targetDistanceM?.let { " · " + Format.decimal(it / 1000.0, 1) + " " + context.getString(R.string.unit_km) } ?: "")
        }
        val status = day?.status?.takeIf { it == PlanDayStatus.DONE || it == PlanDayStatus.OVER }?.let { context.getString(R.string.status_done) }
        val active = (db.runDao().getAll().map { it.localDate } + db.sessionDao().getAll().filter { !it.isDraft }.map { it.localDate })
            .map { it.toLocalDate() }.toSet()
        val rest = db.planDao().getAllDays().filter { it.type == PlanDayType.REST }.map { it.date.toLocalDate() }.toSet()
        return WidgetData(plan, status, Streaks.current(active, rest, today))
    }


    @Composable
    private fun Content(d: WidgetData) {
        val orange = ColorProvider(Color(0xFFFB5B35))
        val white = ColorProvider(Color.White)
        val cream = ColorProvider(Color(0xFFFFE8C2))
        Column(
            GlanceModifier.fillMaxSize().background(orange).cornerRadius(24.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_stat_gowaist), contentDescription = null, modifier = GlanceModifier.size(22.dp))
                Spacer(GlanceModifier.width(6.dp))
                Text("Go waist", style = TextStyle(color = white, fontWeight = FontWeight.Bold, fontSize = 14.sp))
            }
            Spacer(GlanceModifier.height(8.dp))
            Text(
                LocalContextString(R.string.widget_today),
                style = TextStyle(color = cream, fontSize = 12.sp),
            )
            Text(d.plan, style = TextStyle(color = white, fontWeight = FontWeight.Bold, fontSize = 16.sp), maxLines = 2)
            d.status?.let { Text("✓ $it", style = TextStyle(color = cream, fontSize = 12.sp, fontWeight = FontWeight.Bold)) }
            Spacer(GlanceModifier.defaultWeight())
            Text("🔥 " + d.streak + " " + LocalContextString(R.string.widget_streak_days), style = TextStyle(color = white, fontWeight = FontWeight.Bold, fontSize = 15.sp))
        }
    }

    @Composable
    private fun LocalContextString(id: Int): String = androidx.glance.LocalContext.current.getString(id)
}

class GoWaistWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoWaistWidget()
}
