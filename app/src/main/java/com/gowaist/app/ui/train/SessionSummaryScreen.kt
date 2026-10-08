package com.gowaist.app.ui.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.gowaist.app.R
import com.gowaist.app.data.db.ExerciseDao
import com.gowaist.app.data.db.PersonalRecordEntity
import com.gowaist.app.data.db.RecordDao
import com.gowaist.app.data.db.SessionDao
import com.gowaist.app.data.db.SessionEntity
import com.gowaist.app.data.repo.BodyRepository
import com.gowaist.app.data.toFacts
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.components.Confetti
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.Mascot
import com.gowaist.app.ui.components.SectionHeader
import com.gowaist.app.ui.components.StatTile
import com.gowaist.app.ui.nav.SessionSummaryRoute
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.Format
import com.gowaist.core.mascot.MascotMood
import com.gowaist.core.stats.WorkoutMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummaryData(
    val session: SessionEntity,
    val summary: WorkoutMath.Summary,
    val records: List<Pair<PersonalRecordEntity, String>>,
)

@HiltViewModel
class SessionSummaryViewModel @Inject constructor(
    handle: SavedStateHandle,
    sessions: SessionDao,
    exercises: ExerciseDao,
    records: RecordDao,
    body: BodyRepository,
) : ViewModel() {
    private val id = handle.toRoute<SessionSummaryRoute>().id
    var data by mutableStateOf<SummaryData?>(null)

    init {
        viewModelScope.launch {
            val s = sessions.get(id) ?: return@launch
            val ex = exercises.getAll().associateBy { it.id }
            val rows = sessions.setRowsForSession(id)
            val summary = WorkoutMath.summarize(rows.map { it.toFacts() }, ex.mapValues { it.value.toFacts() }, body.currentBodyweight())
            val recs = records.getAll().filter { it.sessionId == id }.map { it to (ex[it.exerciseId]?.nameTh ?: "") }
            data = SummaryData(s, summary, recs)
        }
    }
}

@Composable
fun SessionSummaryScreen(nav: NavHostController, vm: SessionSummaryViewModel = hiltViewModel()) {
    val d = vm.data ?: return
    val haptics = LocalHapticFeedback.current
    val hasRecords = d.records.isNotEmpty()
    LaunchedEffect(hasRecords) { if (hasRecords) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.sum_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Mascot(if (hasRecords) MascotMood.PROUD else MascotMood.HAPPY, Modifier.size(170.dp))
            Text(stringResource(R.string.sum_great), style = MaterialTheme.typography.headlineMedium)
            val dur = ((d.session.endAt ?: d.session.startAt) - d.session.startAt) / 1000
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(R.string.sum_duration), Fmt.duration(dur), Modifier.weight(1f), icon = Icons.Rounded.Timer, color = Gw.colors.goal)
                StatTile(stringResource(R.string.sum_volume), Format.decimal(d.summary.volumeKg, 0), Modifier.weight(1f), unit = stringResource(R.string.unit_kg), icon = Icons.Rounded.FitnessCenter, color = Gw.colors.train)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(R.string.sum_sets), "${d.summary.sets}", Modifier.weight(1f), icon = Icons.Rounded.Repeat, color = Gw.colors.plan)
                StatTile(stringResource(R.string.sum_reps), "${d.summary.reps}", Modifier.weight(1f), icon = Icons.Rounded.Whatshot, color = Gw.colors.run)
                if (d.summary.holdSec > 0) StatTile(stringResource(R.string.sum_hold), Fmt.duration(d.summary.holdSec.toLong()), Modifier.weight(1f), color = Gw.colors.warning)
            }
            SectionHeader(stringResource(R.string.sum_records))
            if (!hasRecords) Text(stringResource(R.string.sum_no_records), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            d.records.forEach { (r, name) -> RecordRow(r, name) }
            Spacer(Modifier.height(8.dp))
            GameButton(stringResource(R.string.sum_done), { nav.popBackStack() }, color = Gw.colors.success, icon = Icons.Rounded.CheckCircle, big = true, modifier = Modifier.fillMaxWidth())
        }
        if (hasRecords) Confetti(key = d.session.id)
    }
}
