package com.gowaist.app.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.withTransaction
import com.gowaist.app.data.db.GoWaistDatabase
import com.gowaist.app.data.db.PersonalRecordEntity
import com.gowaist.app.data.db.RunDao
import com.gowaist.app.data.db.RunEntity
import com.gowaist.app.data.toFacts
import com.gowaist.app.data.toLocalDate
import com.gowaist.core.run.RunChecks
import com.gowaist.core.stats.RunStats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: GoWaistDatabase,
    private val dao: RunDao,
) {
    val runs: Flow<List<RunEntity>> = dao.observeAll()

    fun observe(id: Long): Flow<RunEntity?> = dao.observe(id)

    suspend fun get(id: Long) = dao.get(id)

    suspend fun all() = dao.getAll()

    suspend fun findDuplicate(startAt: Long, localDate: String, distanceM: Double, timeKnown: Boolean, excludeId: Long?): RunEntity? {
        val all = dao.getAll()
        val hit = RunChecks.findDuplicate(startAt, localDate.toLocalDate(), distanceM, timeKnown, all.map { it.toFacts() }, excludeId)
        return hit?.let { h -> all.firstOrNull { it.id == h.id } }
    }

    /**
     * Inserts or updates a run and refreshes its personal records.
     * Returns the id and the records this run set.
     */
    suspend fun save(run: RunEntity): Pair<Long, List<PersonalRecordEntity>> = db.withTransaction {
        val id = if (run.id == 0L) dao.insert(run) else run.id.also { dao.update(run) }
        val saved = run.copy(id = id)
        val recordDao = db.recordDao()
        recordDao.deleteForRun(id)
        val history = dao.getAll().filter { it.id != id && it.startAt < saved.startAt }.map { it.toFacts() }
        val records = RunStats.newRecords(saved.toFacts(), history).map {
            PersonalRecordEntity(
                type = it.type, value = it.value, previous = it.previous,
                achievedAt = saved.startAt, localDate = saved.localDate, runId = id,
            )
        }
        if (records.isNotEmpty()) recordDao.insertAll(records)
        id to records
    }

    suspend fun delete(id: Long) {
        val run = dao.get(id) ?: return
        dao.delete(id)
        run.sourceImagePath?.let { runCatching { File(it).delete() } }
    }

    /** Copies a picked image into app storage (downscaled JPEG) so it survives the gallery entry being deleted. */
    suspend fun keepImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "run_images").apply { mkdirs() }
            val out = File(dir, "${UUID.randomUUID()}.jpg")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 3200) sample *= 2
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bmp.recycle()
            out.absolutePath
        }.getOrNull()
    }

    fun deleteImageFile(path: String?) {
        path?.let { runCatching { File(it).delete() } }
    }
}
