package com.gowaist.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gowaist.app.R
import com.gowaist.app.data.db.ExerciseDao
import com.gowaist.app.data.db.GoalEntity
import com.gowaist.app.data.key
import com.gowaist.app.data.repo.GoalRepository
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.IconBlob
import com.gowaist.app.ui.components.Mascot
import com.gowaist.app.ui.components.labelRes
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.DistanceUnit
import com.gowaist.core.LengthUnit
import com.gowaist.core.Units
import com.gowaist.core.WeightUnit
import com.gowaist.core.mascot.MascotMood
import com.gowaist.core.model.Equipment
import com.gowaist.core.model.GoalPeriod
import com.gowaist.core.model.GoalType
import com.gowaist.core.stats.weekStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class FirstGoal(@StringRes val label: Int) { RUN(R.string.ob_goal_run), BW(R.string.ob_goal_bw), PLANK(R.string.ob_goal_plank), NONE(R.string.ob_goal_none) }

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val goals: GoalRepository,
    private val exercises: ExerciseDao,
) : ViewModel() {
    var page by mutableStateOf(0)
    var nickname by mutableStateOf("")
    var distanceUnit by mutableStateOf(DistanceUnit.KM)
    var weightUnit by mutableStateOf(WeightUnit.KG)
    var lengthUnit by mutableStateOf(LengthUnit.CM)
    var bodyweight by mutableStateOf("70")
    var equipment by mutableStateOf(setOf(Equipment.NONE))
    var goal by mutableStateOf(FirstGoal.RUN)
    var reward by mutableStateOf("")

    fun finish(goalTitles: Map<FirstGoal, String>) = viewModelScope.launch {
        val bwKg = bodyweight.replace(',', '.').toDoubleOrNull()?.let { Units.toKg(weightUnit, it) }?.takeIf { it in 20.0..300.0 } ?: 70.0
        val today = LocalDate.now()
        val weekEnd = today.weekStart().plusDays(6)
        val monthEnd = YearMonth.from(today).atEndOfMonth()
        val g = when (goal) {
            FirstGoal.RUN -> GoalEntity(type = GoalType.RUN_DISTANCE, title = goalTitles.getValue(goal), target = 10_000.0, period = GoalPeriod.WEEK, startDate = today.weekStart().key(), endDate = weekEnd.key())
            FirstGoal.BW -> GoalEntity(type = GoalType.BW_SESSIONS, title = goalTitles.getValue(goal), target = 3.0, period = GoalPeriod.WEEK, startDate = today.weekStart().key(), endDate = weekEnd.key())
            FirstGoal.PLANK -> exercises.bySeedKey("plank")?.let {
                GoalEntity(type = GoalType.EXERCISE_HOLD, title = goalTitles.getValue(goal), target = 60.0, exerciseId = it.id, period = GoalPeriod.MONTH, startDate = today.key(), endDate = monthEnd.key())
            }
            FirstGoal.NONE -> null
        }
        g?.let { goals.create(it, reward.ifBlank { null }) }
        settings.update {
            it.copy(
                nickname = nickname.trim(), distanceUnit = distanceUnit, weightUnit = weightUnit, lengthUnit = lengthUnit,
                bodyweightKg = bwKg, equipment = equipment + Equipment.NONE, onboardingDone = true,
            )
        }
    }
}

@Composable
fun OnboardingScreen(vm: OnboardingViewModel = hiltViewModel()) {
    val titles = mapOf(
        FirstGoal.RUN to stringResource(R.string.ob_goal_title_run),
        FirstGoal.BW to stringResource(R.string.ob_goal_title_bw),
        FirstGoal.PLANK to stringResource(R.string.ob_goal_title_plank),
        FirstGoal.NONE to "",
    )
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.finish(titles) }
    val pages = 4
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(pages) { i ->
                Box(
                    Modifier.padding(4.dp).height(10.dp).width(if (i == vm.page) 28.dp else 10.dp).clip(CircleShape)
                        .background(if (i <= vm.page) MaterialTheme.colorScheme.primary else Gw.colors.track),
                )
            }
        }
        AnimatedContent(
            targetState = vm.page,
            modifier = Modifier.weight(1f),
            transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
            label = "page",
        ) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (page) {
                    0 -> Welcome(vm)
                    1 -> UnitsPage(vm)
                    2 -> EquipmentPage(vm)
                    else -> GoalPage(vm)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (vm.page > 0) TextButton(onClick = { vm.page-- }) { Text(stringResource(R.string.action_back)) }
            Spacer(Modifier.weight(1f))
            GameButton(
                text = stringResource(if (vm.page == pages - 1) R.string.ob_finish else R.string.action_next),
                onClick = {
                    if (vm.page < pages - 1) {
                        vm.page++
                    } else if (Build.VERSION.SDK_INT >= 33) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.finish(titles)
                    }
                },
                modifier = Modifier.width(200.dp),
            )
        }
    }
}

@Composable
private fun Welcome(vm: OnboardingViewModel) {
    Mascot(MascotMood.CHEER, Modifier.size(200.dp))
    Text(stringResource(R.string.ob_welcome_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.ob_welcome_text), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.ob_nickname), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(vm.nickname, { vm.nickname = it.take(24) }, placeholder = { Text(stringResource(R.string.ob_nickname_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun <T> Segmented(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(selected == value, { onSelect(value) }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label) }
        }
    }
}

@Composable
private fun UnitsPage(vm: OnboardingViewModel) {
    Mascot(MascotMood.HAPPY, Modifier.size(120.dp))
    Text(stringResource(R.string.ob_units_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.ob_units_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Segmented(stringResource(R.string.ob_distance), listOf(DistanceUnit.KM to stringResource(R.string.unit_km), DistanceUnit.MI to stringResource(R.string.unit_mi)), vm.distanceUnit) { vm.distanceUnit = it }
    Segmented(stringResource(R.string.ob_weight), listOf(WeightUnit.KG to stringResource(R.string.unit_kg), WeightUnit.LB to stringResource(R.string.unit_lb)), vm.weightUnit) { vm.weightUnit = it }
    Segmented(stringResource(R.string.ob_length), listOf(LengthUnit.CM to stringResource(R.string.unit_cm), LengthUnit.IN to stringResource(R.string.unit_in)), vm.lengthUnit) { vm.lengthUnit = it }
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        vm.bodyweight, { vm.bodyweight = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) },
        label = { Text(stringResource(R.string.ob_bodyweight)) },
        suffix = { Text(stringResource(if (vm.weightUnit == WeightUnit.KG) R.string.unit_kg else R.string.unit_lb)) },
        supportingText = { Text(stringResource(R.string.ob_bodyweight_hint)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EquipmentPage(vm: OnboardingViewModel) {
    Mascot(MascotMood.CALM, Modifier.size(120.dp))
    Text(stringResource(R.string.ob_equipment_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.ob_equipment_text), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    Equipment.entries.filter { it != Equipment.NONE }.forEach { e ->
        val on = e in vm.equipment
        ChoiceRow(stringResource(e.labelRes()), on) { vm.equipment = if (on) vm.equipment - e else vm.equipment + e }
    }
}

@Composable
private fun GoalPage(vm: OnboardingViewModel) {
    Mascot(MascotMood.PROUD, Modifier.size(120.dp))
    Text(stringResource(R.string.ob_goal_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.ob_goal_text), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    FirstGoal.entries.forEach { g -> ChoiceRow(stringResource(g.label), vm.goal == g) { vm.goal = g } }
    if (vm.goal != FirstGoal.NONE) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(vm.reward, { vm.reward = it.take(60) }, placeholder = { Text(stringResource(R.string.ob_reward_hint)) }, leadingIcon = { Text("🎁") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp))
            .border(2.dp, if (selected) c else Gw.colors.cardBorder, RoundedCornerShape(18.dp))
            .background(if (selected) c.copy(alpha = 0.1f) else Gw.colors.card)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (selected) IconBlob(Icons.Rounded.CheckCircle, c, size = 32.dp) else Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = Gw.colors.muted)
    }
}
