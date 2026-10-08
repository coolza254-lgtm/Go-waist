package com.gowaist.app.data.repo

import com.gowaist.app.data.db.BodyDao
import com.gowaist.app.data.db.BodyMetricEntity
import com.gowaist.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BodyRepository @Inject constructor(
    private val dao: BodyDao,
    private val settings: SettingsRepository,
) {
    val metrics: Flow<List<BodyMetricEntity>> = dao.observeAll()

    /** Latest logged body weight, falling back to the weight from settings. */
    val bodyweightKg: Flow<Double> = combine(dao.observeLatestWeight(), settings.settings) { latest, s ->
        latest?.weightKg ?: s.bodyweightKg
    }

    suspend fun currentBodyweight(): Double = bodyweightKg.first()

    suspend fun all() = dao.getAll()

    /** One entry per day: saving on a day that already has values merges into it. */
    suspend fun save(m: BodyMetricEntity) {
        val existing = dao.byDate(m.date)
        if (existing != null && m.id == 0L) {
            dao.upsert(
                existing.copy(
                    weightKg = m.weightKg ?: existing.weightKg,
                    waistCm = m.waistCm ?: existing.waistCm,
                    hipCm = m.hipCm ?: existing.hipCm,
                    chestCm = m.chestCm ?: existing.chestCm,
                    armCm = m.armCm ?: existing.armCm,
                    thighCm = m.thighCm ?: existing.thighCm,
                    note = m.note.ifBlank { existing.note },
                ),
            )
        } else {
            dao.upsert(m)
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
