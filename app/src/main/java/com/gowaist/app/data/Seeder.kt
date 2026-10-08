package com.gowaist.app.data

import androidx.room.withTransaction
import com.gowaist.app.data.db.ChainEntity
import com.gowaist.app.data.db.ChainStepEntity
import com.gowaist.app.data.db.ExerciseEntity
import com.gowaist.app.data.db.GoWaistDatabase
import com.gowaist.app.data.db.TemplateEntity
import com.gowaist.app.data.db.TemplateItemEntity
import com.gowaist.core.seed.ChainSeed
import com.gowaist.core.seed.ExerciseDef
import com.gowaist.core.seed.ExerciseSeed
import com.gowaist.core.seed.TemplateSeed
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs the built-in library. Idempotent: only rows whose seed key is missing are inserted,
 * so new built-in exercises/templates arrive with app updates without touching user edits.
 */
@Singleton
class Seeder @Inject constructor(private val db: GoWaistDatabase) {

    suspend fun ensureSeeded() = db.withTransaction {
        val exDao = db.exerciseDao()
        val ids = mutableMapOf<String, Long>()
        ExerciseSeed.all.forEach { def ->
            val existing = exDao.bySeedKey(def.key)
            ids[def.key] = existing?.id ?: exDao.insert(def.toEntity())
        }

        val chainDao = db.chainDao()
        val existingChains = chainDao.getAll().mapNotNull { it.chain.seedKey }.toSet()
        ChainSeed.all.filter { it.key !in existingChains }.forEach { def ->
            val chainId = chainDao.insert(ChainEntity(seedKey = def.key, name = def.nameTh))
            chainDao.insertSteps(
                def.steps.mapIndexed { i, s ->
                    ChainStepEntity(
                        chainId = chainId, exerciseId = ids.getValue(s.exerciseKey), stepOrder = i,
                        targetSets = s.sets, targetRepsOrSec = s.repsOrSec, sessionsRequired = s.sessions,
                    )
                },
            )
        }

        val tDao = db.templateDao()
        TemplateSeed.all.forEach { def ->
            if (tDao.bySeedKey(def.key) != null) return@forEach
            val tid = tDao.insert(TemplateEntity(seedKey = def.key, name = def.nameTh, description = def.descriptionTh, isCustom = false))
            tDao.insertItems(
                def.items.mapIndexed { i, it ->
                    TemplateItemEntity(
                        templateId = tid, exerciseId = ids.getValue(it.exerciseKey), itemOrder = i,
                        targetSets = it.sets, targetRepsOrSec = it.repsOrSec, restSec = it.restSec, supersetGroup = it.group,
                    )
                },
            )
        }
    }

    private fun ExerciseDef.toEntity() = ExerciseEntity(
        seedKey = key,
        nameTh = nameTh,
        nameEn = nameEn,
        pattern = pattern,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        trackingType = tracking,
        difficulty = difficulty,
        description = description,
        caution = caution,
        equipment = equipment,
        bodyweightFactor = factor,
        defaultRestSec = restSec,
    )
}
