package com.gowaist.app.data.repo

import androidx.room.withTransaction
import com.gowaist.app.data.db.ChainDao
import com.gowaist.app.data.db.ChainEntity
import com.gowaist.app.data.db.ChainStepEntity
import com.gowaist.app.data.db.ExerciseDao
import com.gowaist.app.data.db.ExerciseEntity
import com.gowaist.app.data.db.GoWaistDatabase
import com.gowaist.app.data.db.SuggestionDao
import com.gowaist.app.data.db.SuggestionStateEntity
import com.gowaist.app.data.db.SuggestionStatus
import com.gowaist.app.data.db.TemplateDao
import com.gowaist.app.data.db.TemplateEntity
import com.gowaist.app.data.db.TemplateItemEntity
import com.gowaist.core.model.Equipment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class ChainStepView(val step: ChainStepEntity, val exercise: ExerciseEntity)

data class ChainView(val chain: ChainEntity, val steps: List<ChainStepView>) {
    val current: ChainStepView? get() = steps.getOrNull(chain.currentStep.coerceIn(0, (steps.size - 1).coerceAtLeast(0)))
    val next: ChainStepView? get() = steps.getOrNull(chain.currentStep + 1)
}

data class TemplateItemView(val item: TemplateItemEntity, val exercise: ExerciseEntity)

data class TemplateView(val template: TemplateEntity, val items: List<TemplateItemView>) {
    val equipment: Set<Equipment> get() = items.flatMap { it.exercise.equipment }.toSet() - Equipment.NONE
    fun doableWith(owned: Set<Equipment>): Boolean = items.all { it.exercise.equipment.all { e -> e == Equipment.NONE || e in owned } }
    val estimatedMinutes: Int
        get() = (items.sumOf { it.item.targetSets * (45 + it.item.restSec) } / 60).coerceAtLeast(5)
}

@Singleton
class LibraryRepository @Inject constructor(
    private val db: GoWaistDatabase,
    private val exerciseDao: ExerciseDao,
    private val chainDao: ChainDao,
    private val templateDao: TemplateDao,
    private val suggestionDao: SuggestionDao,
) {
    val exercises: Flow<List<ExerciseEntity>> = exerciseDao.observeAll()

    fun observeExercise(id: Long) = exerciseDao.observe(id)

    suspend fun exercise(id: Long) = exerciseDao.get(id)

    suspend fun allExercises() = exerciseDao.getAll()

    suspend fun saveExercise(e: ExerciseEntity): Long =
        if (e.id == 0L) exerciseDao.insert(e.copy(isCustom = true)) else e.id.also { exerciseDao.update(e) }

    suspend fun setFavorite(id: Long, fav: Boolean) = exerciseDao.setFavorite(id, fav)

    suspend fun setHidden(id: Long, hidden: Boolean) = exerciseDao.setHidden(id, hidden)

    // ------------------------------------------------------------------ chains

    val chains: Flow<List<ChainView>> = combine(chainDao.observeAll(), exerciseDao.observeAll()) { chains, exercises ->
        val byId = exercises.associateBy { it.id }
        chains.map { c ->
            ChainView(c.chain, c.steps.sortedBy { it.stepOrder }.mapNotNull { s -> byId[s.exerciseId]?.let { ChainStepView(s, it) } })
        }
    }

    /** Moves the user's position in a chain; moving up is remembered for the level-up badge. */
    suspend fun setChainStep(chainId: Long, step: Int) {
        val current = chainDao.getAll().firstOrNull { it.chain.id == chainId }?.chain?.currentStep ?: 0
        chainDao.setCurrentStep(chainId, step)
        if (step > current) {
            suggestionDao.upsert(SuggestionStateEntity("${GoalRepository.CHAIN_UP_PREFIX}$chainId:$step", SuggestionStatus.ACCEPTED))
        }
    }

    suspend fun saveChain(chain: ChainEntity, steps: List<ChainStepEntity>): Long = db.withTransaction {
        val id = if (chain.id == 0L) chainDao.insert(chain.copy(isCustom = true)) else chain.id.also { chainDao.update(chain) }
        chainDao.deleteSteps(id)
        chainDao.insertSteps(steps.mapIndexed { i, s -> s.copy(id = 0, chainId = id, stepOrder = i) })
        id
    }

    suspend fun updateStepRule(step: ChainStepEntity) = chainDao.updateStep(step)

    suspend fun deleteChain(id: Long) = chainDao.delete(id)

    // ------------------------------------------------------------------ templates

    val templates: Flow<List<TemplateView>> = combine(templateDao.observeAll(), exerciseDao.observeAll()) { ts, exercises ->
        val byId = exercises.associateBy { it.id }
        ts.map { t -> TemplateView(t.template, t.items.sortedBy { it.itemOrder }.mapNotNull { i -> byId[i.exerciseId]?.let { TemplateItemView(i, it) } }) }
    }

    fun observeTemplate(id: Long): Flow<TemplateView?> = templates.map { list -> list.firstOrNull { it.template.id == id } }

    suspend fun template(id: Long): TemplateView? {
        val t = templateDao.get(id) ?: return null
        val byId = exerciseDao.getAll().associateBy { it.id }
        return TemplateView(t.template, t.items.sortedBy { it.itemOrder }.mapNotNull { i -> byId[i.exerciseId]?.let { TemplateItemView(i, it) } })
    }

    suspend fun saveTemplate(t: TemplateEntity, items: List<TemplateItemEntity>): Long = db.withTransaction {
        val id = if (t.id == 0L) templateDao.insert(t) else t.id.also { templateDao.update(t) }
        templateDao.deleteItems(id)
        templateDao.insertItems(items.mapIndexed { i, it -> it.copy(id = 0, templateId = id, itemOrder = i) })
        id
    }

    suspend fun deleteTemplate(id: Long) = templateDao.delete(id)

    suspend fun templateBySeedKey(key: String) = templateDao.bySeedKey(key)
}
