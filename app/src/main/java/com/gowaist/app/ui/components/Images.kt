package com.gowaist.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes a local file path or content URI off the main thread, downsampled to [maxSize]. */
@Composable
fun LocalImage(source: Any?, contentDescription: String?, modifier: Modifier = Modifier, maxSize: Int = 1440, contentScale: ContentScale = ContentScale.Fit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                fun open() = when (source) {
                    is Uri -> context.contentResolver.openInputStream(source)
                    is String -> java.io.FileInputStream(source)
                    else -> null
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize * 2) sample *= 2
                open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription, modifier, contentScale = contentScale) }
}
