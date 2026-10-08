package com.gowaist.app.domain

import android.content.Context
import com.gowaist.app.data.db.GoalEntity
import com.gowaist.app.data.db.PersonalRecordEntity
import com.gowaist.app.data.repo.GoalRepository
import com.gowaist.app.data.repo.PlanRepository
import com.gowaist.app.widget.WidgetUpdater
import com.gowaist.core.goals.BadgeKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Something worth a confetti moment, shown by the app-level celebration overlay. */
sealed interface Celebration {
    data class Records(val records: List<PersonalRecordEntity>) : Celebration
    data class GoalAchieved(val goal: GoalEntity, val reward: String?) : Celebration
    data class BadgeUnlocked(val badge: BadgeKey) : Celebration
}

@Singleton
class CelebrationBus @Inject constructor() {
    private val _events = MutableSharedFlow<Celebration>(replay = 0, extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Celebration> = _events

    fun emit(c: Celebration) {
        _events.tryEmit(c)
    }
}

/**
 * Runs everything that depends on new activity: plan matching, goal progress, rewards, badges
 * and the home-screen widget. Call after any run / session is saved, edited or deleted.
 */
@Singleton
class ActivityCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plans: PlanRepository,
    private val goals: GoalRepository,
    private val bus: CelebrationBus,
) {
    suspend fun afterChange(records: List<PersonalRecordEntity> = emptyList(), celebrate: Boolean = true) {
        plans.syncStatuses()
        val achievements = goals.evaluate()
        if (celebrate) {
            if (records.isNotEmpty()) bus.emit(Celebration.Records(records))
            achievements.goals.forEach { g ->
                bus.emit(Celebration.GoalAchieved(g, goalsRewardText(g.id)))
            }
            achievements.badges.forEach { bus.emit(Celebration.BadgeUnlocked(it)) }
        }
        WidgetUpdater.update(context)
    }

    private suspend fun goalsRewardText(goalId: Long): String? =
        goals.rewardTextFor(goalId)
}
