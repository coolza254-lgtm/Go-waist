package com.gowaist.app.data.repo

import androidx.room.withTransaction
import com.gowaist.app.data.db.BadgeEntity
import com.gowaist.app.data.db.BodyDao
import com.gowaist.app.data.db.GoWaistDatabase
import com.gowaist.app.data.db.GoalDao
import com.gowaist.app.data.db.GoalEntity
import com.gowaist.app.data.db.GoalWithReward
import com.gowaist.app.data.db.PlanDao
import com.gowaist.app.data.db.RecordDao
import com.gowaist.app.data.db.RewardEntity
import com.gowaist.app.data.db.RunDao
import com.gowaist.app.data.db.SessionDao
import com.gowaist.app.data.db.SuggestionDao
import com.gowaist.app.data.db.SuggestionStateEntity
import com.gowaist.app.data.db.SuggestionStatus
import com.gowaist.app.data.toFacts
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.data.toLocalDateTime
import com.gowaist.core.goals.BadgeFacts
import com.gowaist.core.goals.BadgeKey
import com.gowaist.core.goals.BadgeRules
import com.gowaist.core.goals.BodyPoint
import com.gowaist.core.goals.GoalData
import com.gowaist.core.goals.GoalEvaluator
import com.gowaist.core.goals.GoalSpec
import com.gowaist.core.model.GoalStatus
import com.gowaist.core.model.PlanDayType
import com.gowaist.core.stats.Streaks
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class Achievements(val goals: List<GoalEntity>, val badges: List<BadgeKey>)

@Singleton
class GoalRepository @Inject constructor(
    private val db: GoWaistDatabase,
    private val dao: GoalDao,
    private val runDao: RunDao,
    private val sessionDao: SessionDao,
    private val planDao: PlanDao,
    private val bodyDao: BodyDao,
    private val recordDao: RecordDao,
) {
    val goals: Flow<List<GoalWithReward>> = dao.observeAll()
    val badges: Flow<List<BadgeEntity>> = dao.observeBadges()
    val rewards: Flow<List<RewardEntity>> = dao.observeRewards()

    suspend fun create(goal: GoalEntity, rewardText: String?): Long = db.withTransaction {
        val id = dao.insert(goal)
        if (!rewardText.isNullOrBlank()) dao.insertReward(RewardEntity(goalId = id, text = rewardText.trim()))
        id
    }

    suspend fun update(goal: GoalEntity, rewardText: String?) = db.withTransaction {
        dao.update(goal)
        val r = dao.rewardFor(goal.id)
        when {
            r == null && !rewardText.isNullOrBlank() -> dao.insertReward(RewardEntity(goalId = goal.id, text = rewardText.trim()))
            r != null && !rewardText.isNullOrBlank() -> dao.updateReward(r.copy(text = rewardText.trim()))
            else -> Unit
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun rewardTextFor(goalId: Long): String? = dao.rewardFor(goalId)?.text

    suspend fun claimReward(reward: RewardEntity, claimed: Boolean) =
        dao.updateReward(reward.copy(claimedAt = if (claimed) System.currentTimeMillis() else null))

    private suspend fun data(): GoalData {
        val runs = runDao.getAll()
        val sessions = sessionDao.getAll().filter { !it.isDraft }
        val active = (runs.map { it.localDate } + sessions.map { it.localDate }).map { it.toLocalDate() }.toSet()
        val rest = planDao.getAllDays().filter { it.type == PlanDayType.REST }.map { it.date.toLocalDate() }.toSet()
        return GoalData(
            runs = runs.map { it.toFacts() },
            sets = sessionDao.finishedSetRows().map { it.toFacts() },
            sessions = sessions.associate { it.id to it.localDate.toLocalDate() },
            activeDays = active,
            restDays = rest,
            body = bodyDao.getAll().map { BodyPoint(it.date.toLocalDate(), it.weightKg, it.waistCm) },
        )
    }

    /** Recomputes progress of every active goal and unlocks rewards and badges. Returns what is new. */
    suspend fun evaluate(today: LocalDate = LocalDate.now()): Achievements = db.withTransaction {
        val data = data()
        val newlyAchieved = mutableListOf<GoalEntity>()
        dao.getAll().filter { it.status == GoalStatus.ACTIVE }.forEach { g ->
            val spec = GoalSpec(g.type, g.target, g.startDate.toLocalDate(), g.endDate.toLocalDate(), g.exerciseId, g.baseline)
            val p = GoalEvaluator.progress(spec, data)
            val updated = when {
                p.achieved -> g.copy(progress = 1.0, status = GoalStatus.ACHIEVED, achievedAt = System.currentTimeMillis()).also { newlyAchieved += it }
                today.isAfter(spec.end) -> g.copy(progress = p.fraction, status = GoalStatus.EXPIRED)
                else -> g.copy(progress = p.fraction)
            }
            if (updated != g) dao.update(updated)
            if (p.achieved) dao.rewardFor(g.id)?.takeIf { it.unlockedAt == null }?.let { dao.updateReward(it.copy(unlockedAt = System.currentTimeMillis())) }
        }
        val newBadges = evaluateBadges(data)
        Achievements(newlyAchieved, newBadges)
    }

    private suspend fun evaluateBadges(data: GoalData): List<BadgeKey> {
        val runs = runDao.getAll()
        val facts = BadgeFacts(
            runCount = runs.size,
            totalRunM = runs.sumOf { it.distanceM },
            longestRunM = runs.maxOfOrNull { it.distanceM } ?: 0.0,
            sessionCount = data.sessions.size,
            prCount = recordDao.getAll().size,
            longestStreak = Streaks.longest(data.activeDays, data.restDays),
            chainPromotions = db.suggestionDao().getAll().count { it.key.startsWith(CHAIN_UP_PREFIX) },
            goalsAchieved = dao.getAll().count { it.status == GoalStatus.ACHIEVED },
            hasEarlyRun = runs.any { it.startAt.toLocalDateTime().hour < 6 },
        )
        val have = dao.getBadges().map { it.key }.toSet()
        val now = System.currentTimeMillis()
        return BadgeRules.unlocked(facts).filter { it.name !in have }.onEach { dao.insertBadge(BadgeEntity(it.name, now)) }
    }

    companion object {
        /** Suggestion-state keys recording that the user moved up a progression chain. */
        const val CHAIN_UP_PREFIX = "chainup:"
    }
}

@Singleton
class SuggestionRepository @Inject constructor(private val dao: SuggestionDao) {
    val states: Flow<List<SuggestionStateEntity>> = dao.observeAll()

    suspend fun set(key: String, status: SuggestionStatus, snoozeDays: Int = 0) =
        dao.upsert(
            SuggestionStateEntity(
                key = key, status = status,
                snoozeUntil = if (status == SuggestionStatus.SNOOZED) System.currentTimeMillis() + snoozeDays * 86_400_000L else null,
            ),
        )

    /** Whether a suggestion should still be shown given its stored state. */
    fun isOpen(key: String, states: List<SuggestionStateEntity>, now: Long = System.currentTimeMillis()): Boolean {
        val s = states.firstOrNull { it.key == key } ?: return true
        return s.status == SuggestionStatus.SNOOZED && (s.snoozeUntil ?: 0) < now
    }
}
