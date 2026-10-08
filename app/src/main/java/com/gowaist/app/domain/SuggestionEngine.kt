package com.gowaist.app.domain

import com.gowaist.app.data.db.ExerciseEntity
import com.gowaist.app.data.db.PlanEntity
import com.gowaist.app.data.db.SuggestionStatus
import com.gowaist.app.data.db.TemplateDao
import com.gowaist.app.data.repo.BodyRepository
import com.gowaist.app.data.repo.LibraryRepository
import com.gowaist.app.data.repo.PlanRepository
import com.gowaist.app.data.repo.SuggestionRepository
import com.gowaist.app.data.repo.WorkoutRepository
import com.gowaist.app.data.toFacts
import com.gowaist.core.model.TrackingType
import com.gowaist.core.plan.PlanAdjustment
import com.gowaist.core.progression.ExerciseHistory
import com.gowaist.core.progression.ProgressionAdvisor
import com.gowaist.core.progression.ProgressionTarget
import com.gowaist.core.progression.Suggestion
import com.gowaist.core.progression.SuggestionType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

sealed interface SuggestionItem {
    val key: String

    data class Progression(
        val suggestion: Suggestion,
        val exercise: ExerciseEntity?,
        val next: ExerciseEntity?,
        val chainId: Long?,
        val nextStep: Int?,
    ) : SuggestionItem {
        override val key: String get() = suggestion.key
    }

    data class PlanChange(val plan: PlanEntity, val adjustment: PlanAdjustment) : SuggestionItem {
        override val key: String get() = "plan:${plan.id}:${adjustment.type}:${adjustment.weekStart}"
    }
}

/** Builds the open rule-based suggestions and applies the user's answer to them. */
@Singleton
class SuggestionEngine @Inject constructor(
    private val library: LibraryRepository,
    private val workouts: WorkoutRepository,
    private val plans: PlanRepository,
    private val body: BodyRepository,
    private val states: SuggestionRepository,
    private val templateDao: TemplateDao,
) {
    suspend fun open(today: LocalDate = LocalDate.now()): List<SuggestionItem> {
        val rows = workouts.allSetRows()
        val exercises = library.allExercises().associateBy { it.id }
        val chains = library.chains.first()
        val stepOf = chains.flatMap { c -> c.steps.mapIndexed { i, s -> s.exercise.id to Triple(c, i, s) } }.toMap()
        val sessionTargets = rows.groupBy { it.exerciseId }
        val histories = sessionTargets.keys.mapNotNull { exId ->
            val ex = exercises[exId] ?: return@mapNotNull null
            val chainStep = stepOf[exId]
            val target = chainStep?.third?.step?.let { ProgressionTarget(it.targetSets, it.targetRepsOrSec, it.sessionsRequired) }
                ?: defaultTarget(ex)
            val next = chainStep?.let { (c, i, _) -> c.steps.getOrNull(i + 1)?.exercise?.id }
            ExerciseHistory(ex.toFacts(), sessionTargets.getValue(exId).map { it.toFacts() }, target, next)
        }
        val advisor = ProgressionAdvisor(body.currentBodyweight())
        val progression = advisor.suggest(histories, today).map { s ->
            val step = s.exerciseId?.let { stepOf[it] }
            SuggestionItem.Progression(
                suggestion = s,
                exercise = s.exerciseId?.let { exercises[it] },
                next = s.nextExerciseId?.let { exercises[it] },
                chainId = step?.first?.chain?.id,
                nextStep = step?.second?.plus(1),
            )
        }
        val planItems = plans.adjustments(today).map { (p, a) -> SuggestionItem.PlanChange(p, a) }
        val stored = states.states.first()
        return (planItems + progression).filter { states.isOpen(it.key, stored) }
    }

    private fun defaultTarget(ex: ExerciseEntity) = when (ex.trackingType) {
        TrackingType.HOLD -> ProgressionTarget(3, 30)
        TrackingType.WEIGHTED, TrackingType.ASSISTED -> ProgressionTarget(3, 8)
        TrackingType.CARDIO -> ProgressionTarget(3, 60, consecutiveSessions = 3)
        TrackingType.REPS -> ProgressionTarget(3, 12)
    }

    suspend fun accept(item: SuggestionItem) {
        when (item) {
            is SuggestionItem.PlanChange -> plans.apply(item.plan, item.adjustment)
            is SuggestionItem.Progression -> {
                val s = item.suggestion
                when (s.type) {
                    SuggestionType.PROMOTE -> if (item.chainId != null && item.nextStep != null) library.setChainStep(item.chainId, item.nextStep)
                    SuggestionType.INCREASE_REPS, SuggestionType.INCREASE_HOLD -> s.exerciseId?.let { raiseTemplateTargets(it, s.evidence.suggestedValue.roundToInt()) }
                    else -> Unit
                }
            }
        }
        states.set(item.key, SuggestionStatus.ACCEPTED)
    }

    suspend fun reject(item: SuggestionItem) = states.set(item.key, SuggestionStatus.REJECTED)

    suspend fun snooze(item: SuggestionItem, days: Int = 3) = states.set(item.key, SuggestionStatus.SNOOZED, days)

    /** Raises the target reps/seconds of this exercise in every template that uses it. */
    private suspend fun raiseTemplateTargets(exerciseId: Long, newTarget: Int) {
        val items = templateDao.getAllItems().filter { it.exerciseId == exerciseId && it.targetRepsOrSec < newTarget }
        if (items.isNotEmpty()) templateDao.insertAllItems(items.map { it.copy(targetRepsOrSec = newTarget) })
    }
}
