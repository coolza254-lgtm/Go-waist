package com.gowaist.app.ui.run

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.gowaist.app.R
import com.gowaist.app.data.db.RunEntity
import com.gowaist.app.data.repo.RunRepository
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.data.toLocalDateTime
import com.gowaist.app.domain.ActivityCoordinator
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.GwTopBar
import com.gowaist.app.ui.LocalAppSettings
import com.gowaist.app.ui.components.ConfirmDialog
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.LocalImage
import com.gowaist.app.ui.components.Pill
import com.gowaist.app.ui.components.StatTile
import com.gowaist.app.ui.components.label
import com.gowaist.app.ui.nav.RunEditRoute
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.Pace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RunDetailViewModel @Inject constructor(
    private val runs: RunRepository,
    private val coordinator: ActivityCoordinator,
) : ViewModel() {
    fun run(id: Long): Flow<RunEntity?> = runs.observe(id)

    fun delete(id: Long, after: () -> Unit) = viewModelScope.launch {
        runs.delete(id)
        coordinator.afterChange(celebrate = false)
        after()
    }
}

@Composable
fun RunDetailScreen(nav: NavHostController, id: Long, vm: RunDetailViewModel = hiltViewModel()) {
    val flow = remember(id) { vm.run(id) }
    val run by flow.collectAsStateWithLifecycle(null)
    var confirm by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(false) }
    val r = run
    Scaffold(
        topBar = {
            GwTopBar(stringResource(R.string.run_detail_title), onBack = { nav.popBackStack() }) {
                IconButton({ nav.navigate(RunEditRoute(id)) }) { Icon(Icons.Rounded.Edit, stringResource(R.string.action_edit)) }
                IconButton({ confirm = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
            }
        },
    ) { pad ->
        if (r == null) return@Scaffold
        val unit = LocalAppSettings.current.distanceUnit
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(Fmt.dateLong(r.localDate.toLocalDate()) + " · " + Fmt.time(r.startAt.toLocalDateTime()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.distanceValue(r.distanceM), style = com.gowaist.app.ui.theme.NumberStyles.huge, color = Gw.colors.run)
                Text(" " + unit.label(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(R.string.run_duration), Fmt.duration(r.durationSec), Modifier.weight(1f), icon = Icons.Rounded.Timer, color = Gw.colors.train)
                StatTile(stringResource(R.string.run_pace), Fmt.paceValue(r.avgPaceSecPerKm ?: Pace.secPerKm(r.distanceM, r.durationSec)), Modifier.weight(1f), icon = Icons.Rounded.Speed, color = Gw.colors.goal)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(R.string.run_calories), r.calories?.toString() ?: "-", Modifier.weight(1f), unit = stringResource(R.string.unit_kcal), icon = Icons.Rounded.LocalFireDepartment, color = Gw.colors.warning)
                StatTile(stringResource(R.string.run_avg_hr), r.avgHr?.toString() ?: "-", Modifier.weight(1f), unit = stringResource(R.string.unit_bpm), icon = Icons.Rounded.Favorite, color = Gw.colors.danger)
            }
            if (r.tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { r.tags.forEach { Pill(it, Gw.colors.run) } }
            if (r.note.isNotBlank()) GameCard { Text(r.note, style = MaterialTheme.typography.bodyLarge) }
            r.sourceImagePath?.let { path ->
                LocalImage(path, stringResource(R.string.run_view_image), Modifier.fillMaxWidth().heightIn(max = 520.dp).clip(RoundedCornerShape(18.dp)).clickable { zoom = true }, contentScale = ContentScale.FillWidth)
            }
            Spacer(Modifier.height(24.dp))
        }
        if (zoom && r.sourceImagePath != null) {
            Dialog({ zoom = false }, DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxSize().clickable { zoom = false }, contentAlignment = Alignment.Center) {
                    LocalImage(r.sourceImagePath, null, Modifier.fillMaxSize(), maxSize = 2400)
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            stringResource(R.string.run_delete_title), stringResource(R.string.run_delete_text), stringResource(R.string.action_delete),
            onConfirm = { vm.delete(id) { nav.popBackStack() } }, onDismiss = { confirm = false }, destructive = true,
        )
    }
}
