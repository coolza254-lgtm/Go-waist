package com.gowaist.app.data.repo

import android.net.Uri
import androidx.room.withTransaction
import com.gowaist.app.data.db.GoWaistDatabase
import com.gowaist.app.data.db.RunDao
import com.gowaist.app.data.db.ShoeDao
import com.gowaist.app.data.db.ShoeEntity
import com.gowaist.core.shoes.ShoeMath
import com.gowaist.core.shoes.ShoeStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ShoeWithStats(val shoe: ShoeEntity, val stats: ShoeStats) {
    val kmLeft: Double? get() = ShoeMath.kmLeft(stats.distanceM, shoe.durabilityPct)
}

@Singleton
class ShoeRepository @Inject constructor(
    private val db: GoWaistDatabase,
    private val dao: ShoeDao,
    private val runDao: RunDao,
    private val runs: RunRepository,
) {
    val shoes: Flow<List<ShoeWithStats>> = combine(dao.observeAll(), runDao.observeAll()) { shoes, all ->
        val byShoe = all.groupBy { it.shoeId }
        shoes.map { s -> ShoeWithStats(s, ShoeMath.stats(s.priceBaht, byShoe[s.id].orEmpty().map { it.distanceM })) }
    }

    suspend fun get(id: Long) = dao.get(id)
    suspend fun main() = dao.main()

    /** Saves a shoe, copying a newly picked photo into app storage; the first active pair becomes the main one. */
    suspend fun save(shoe: ShoeEntity, newImage: Uri?): Long = db.withTransaction {
        var s = shoe
        if (newImage != null) {
            runs.keepImage(newImage)?.let { path ->
                shoe.imagePath?.let { runCatching { File(it).delete() } }
                s = s.copy(imagePath = path)
            }
        }
        val id = if (s.id == 0L) dao.insert(s) else s.id.also { dao.update(s) }
        if (s.isMain && !s.retired) dao.setMain(id)
        if (dao.main() == null) dao.getAll().firstOrNull { !it.retired }?.let { dao.setMain(it.id) }
        id
    }

    suspend fun setMain(id: Long) = dao.setMain(id)

    suspend fun delete(id: Long) = db.withTransaction {
        val s = dao.get(id) ?: return@withTransaction
        runDao.clearShoe(id)
        dao.delete(id)
        s.imagePath?.let { runCatching { File(it).delete() } }
        if (dao.main() == null) dao.getAll().firstOrNull { !it.retired }?.let { dao.setMain(it.id) }
    }
}
