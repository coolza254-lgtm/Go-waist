package com.gowaist.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.gowaist.app.R
import com.gowaist.app.domain.Celebration
import com.gowaist.app.domain.CelebrationBus
import com.gowaist.app.ui.components.Confetti
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.Mascot
import com.gowaist.app.ui.components.labelRes
import com.gowaist.app.ui.goals.badgeDescRes
import com.gowaist.app.ui.goals.badgeTitleRes
import com.gowaist.core.mascot.MascotMood
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CelebrationViewModel @Inject constructor(val bus: CelebrationBus) : ViewModel() {
    val queue = mutableStateListOf<Celebration>()
}

/** Shows queued celebrations (records, achieved goals, badges) one at a time with confetti. */
@Composable
fun CelebrationHost(vm: CelebrationViewModel = hiltViewModel()) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        vm.bus.events.collect { c ->
            // Several records from one session become one celebration.
            val last = vm.queue.lastOrNull()
            if (c is Celebration.Records && last is Celebration.Records) {
                vm.queue[vm.queue.lastIndex] = Celebration.Records(last.records + c.records)
            } else {
                vm.queue.add(c)
            }
        }
    }
    val current = vm.queue.firstOrNull() ?: return
    LaunchedEffect(current) { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    Dialog(onDismissRequest = { vm.queue.removeAt(0) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Confetti(key = current)
            Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.padding(28.dp).fillMaxWidth(), tonalElevation = 6.dp) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mascot(MascotMood.PROUD, Modifier.size(140.dp))
                    when (current) {
                        is Celebration.Records -> {
                            Text(stringResource(R.string.celebrate_pb_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                            current.records.distinctBy { it.type to it.exerciseId }.take(4).forEach {
                                Text("🏅 " + stringResource(it.type.labelRes()), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            }
                        }
                        is Celebration.GoalAchieved -> {
                            Text(stringResource(R.string.celebrate_goal_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                            Text(current.goal.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                            current.reward?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.celebrate_reward_unlocked, it), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        is Celebration.BadgeUnlocked -> {
                            Text(stringResource(R.string.celebrate_badge_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                            Text(stringResource(badgeTitleRes(current.badge)), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                            Text(stringResource(badgeDescRes(current.badge)), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    GameButton(stringResource(R.string.celebrate_yay), { vm.queue.removeAt(0) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
