package com.gowaist.app.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.gowaist.app.R
import com.gowaist.app.ui.components.label
import com.gowaist.app.ui.components.paceLabel
import com.gowaist.core.DistanceUnit
import com.gowaist.core.Format
import com.gowaist.core.Pace
import com.gowaist.core.Units
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.chrono.ThaiBuddhistChronology
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GwTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Unit-aware formatting helpers that read the user's settings. */
object Fmt {
    @Composable
    fun distanceValue(meters: Double, digits: Int = 2): String = Format.distance(meters, LocalAppSettings.current.distanceUnit, digits)

    @Composable
    fun distance(meters: Double, digits: Int = 2): String = distanceValue(meters, digits) + " " + LocalAppSettings.current.distanceUnit.label()

    @Composable
    fun pace(secPerKm: Double?): String {
        val unit = LocalAppSettings.current.distanceUnit
        return if (secPerKm == null) "-" else Format.pace(Pace.secPerUnit(secPerKm, unit)) + " " + unit.paceLabel()
    }

    @Composable
    fun paceValue(secPerKm: Double?): String {
        val unit = LocalAppSettings.current.distanceUnit
        return if (secPerKm == null) "-" else Format.pace(Pace.secPerUnit(secPerKm, unit))
    }

    @Composable
    fun weight(kg: Double, digits: Int = 1): String {
        val u = LocalAppSettings.current.weightUnit
        return Format.decimal(Units.kgTo(u, kg), digits) + " " + u.label()
    }

    @Composable
    fun length(cm: Double, digits: Int = 1): String {
        val u = LocalAppSettings.current.lengthUnit
        return Format.decimal(Units.cmTo(u, cm), digits) + " " + u.label()
    }

    fun duration(sec: Long): String = Format.duration(sec)

    private val thai: Boolean get() = Locale.getDefault().language == "th"

    private fun fmt(pattern: String): DateTimeFormatter {
        val f = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
        return if (thai) f.withChronology(ThaiBuddhistChronology.INSTANCE) else f
    }

    fun dateShort(d: LocalDate): String = fmt("EEE d MMM").format(d)
    fun dateLong(d: LocalDate): String = fmt("EEEE d MMMM yyyy").format(d)
    fun dateMedium(d: LocalDate): String = fmt("d MMM yyyy").format(d)
    fun monthYear(d: LocalDate): String = fmt("MMMM yyyy").format(d)
    fun dayMonth(d: LocalDate): String = fmt("d/M").format(d)
    fun time(t: LocalDateTime): String = DateTimeFormatter.ofPattern("HH:mm").format(t)

    fun unitLabelRes(u: DistanceUnit) = if (u == DistanceUnit.KM) R.string.unit_km else R.string.unit_mi
}
