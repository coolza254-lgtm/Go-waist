package com.gowaist.app.data.repo

import androidx.room.withTransaction
import com.gowaist.app.data.db.BodyDao
import com.gowaist.app.data.db.GoWaistDatabase
import com.gowaist.app.data.db.GoalDao
import com.gowaist.app.data.db.GoalEntity
import com.gowaist.app.data.db.RunDao
import com.gowaist.app.data.toFacts
import com.gowaist.app.data.toLocalDate
import com.gowaist.core.goals.BodyPoint
import com.gowaist.core.goals.GoalData
import com.gowaist.core.goals.GoalEvaluator
import com.gowaist.core.goals.GoalProgress
import com.gowaist.core.goals.GoalSpec
import com.gowaist.core.model.GoalStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepository @Inject constructor(
    private val db: GoWaistDatabase,
    private val dao: GoalDao,
    private val runDao: RunDao,
    private val bodyDao: BodyDao,
) {
    val goals: Flow<List<GoalEntity>> = dao.observeAll()

    suspend fun get(id: Long) = dao.get(id)
    suspend fun create(goal: GoalEntity): Long = dao.insert(goal)
    suspend fun update(goal: GoalEntity) = dao.update(goal)
    suspend fun delete(id: Long) = dao.delete(id)

    private suspend fun data(): GoalData {
        val runs = runDao.getAll()
        return GoalData(
            runs = runs.map { it.toFacts() },
            activeDays = runs.map { it.localDate.toLocalDate() }.toSet(),
            body = bodyDao.getAll().map { BodyPoint(it.date.toLocalDate(), it.weightKg, it.waistCm) },
        )
    }

    private fun GoalEntity.spec() = GoalSpec(type, target, startDate.toLocalDate(), endDate.toLocalDate(), baseline = baseline)

    /** Current value and progress of every goal, for display. */
    suspend fun liveProgress(): Map<Long, GoalProgress> {
        val data = data()
        return dao.getAll().associate { g -> g.id to GoalEvaluator.progress(g.spec(), data) }
    }

    /** Recomputes progress of active goals. Returns goals achieved by this change. */
    suspend fun evaluate(today: LocalDate = LocalDate.now()): List<GoalEntity> = db.withTransaction {
        val data = data()
        val achieved = mutableListOf<GoalEntity>()
        dao.getAll().filter { it.status == GoalStatus.ACTIVE }.forEach { g ->
            val spec = g.spec()
            val p = GoalEvaluator.progress(spec, data)
            val updated = when {
                p.achieved -> g.copy(progress = 1.0, status = GoalStatus.ACHIEVED, achievedAt = System.currentTimeMillis()).also { achieved += it }
                today.isAfter(spec.end) -> g.copy(progress = p.fraction, status = GoalStatus.EXPIRED)
                else -> g.copy(progress = p.fraction)
            }
            if (updated != g) dao.update(updated)
        }
        achieved
    }
}
