package com.gowaist.app.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * On-device text recognition with the bundled ML Kit Latin model (no download, no network).
 * Returns visual rows: text elements whose vertical centres line up are joined left-to-right,
 * so "Avg. pace   6'15" /km" stays on one line even when ML Kit splits it into two blocks.
 */
@Singleton
class OcrService @Inject constructor(@ApplicationContext private val context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun readLines(uri: Uri): List<String> {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        return rows(result)
    }

    private data class Piece(val text: String, val top: Int, val bottom: Int, val left: Int) {
        val center get() = (top + bottom) / 2
        val height get() = (bottom - top).coerceAtLeast(1)
    }

    private fun rows(result: Text): List<String> {
        val pieces = result.textBlocks.flatMap { b -> b.lines }.mapNotNull { l ->
            val box = l.boundingBox ?: return@mapNotNull Piece(l.text, 0, 0, 0)
            Piece(l.text, box.top, box.bottom, box.left)
        }.sortedBy { it.center }
        val rows = mutableListOf<MutableList<Piece>>()
        for (p in pieces) {
            val row = rows.lastOrNull()
            if (row != null && abs(row.first().center - p.center) < minOf(row.first().height, p.height) * 0.5) row += p else rows += mutableListOf(p)
        }
        return rows.map { r -> r.sortedBy { it.left }.joinToString("  ") { it.text } }
    }
}
