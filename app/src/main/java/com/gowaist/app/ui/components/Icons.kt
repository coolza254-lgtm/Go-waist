package com.gowaist.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Outline running shoe used for the shoes tab and shoe cards. */
val ShoeIcon: ImageVector by lazy {
    ImageVector.Builder("Shoe", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(2f, 17f)
            verticalLineTo(13f)
            curveTo(2f, 12f, 2.6f, 11.5f, 3.5f, 11.5f)
            horizontalLineTo(8.5f)
            lineTo(11f, 7.5f)
            curveTo(11.5f, 7f, 12.3f, 6.9f, 13f, 7.3f)
            lineTo(14.8f, 8.4f)
            curveTo(16f, 10.6f, 18.2f, 11.6f, 20f, 12.1f)
            curveTo(21.4f, 12.5f, 22f, 13.4f, 22f, 15f)
            verticalLineTo(17f)
            close()
            moveTo(2f, 17f)
            verticalLineTo(18.5f)
            curveTo(2f, 19f, 2.4f, 19.5f, 3f, 19.5f)
            horizontalLineTo(21f)
            curveTo(21.6f, 19.5f, 22f, 19f, 22f, 18.5f)
            verticalLineTo(17f)
            moveTo(10f, 10f)
            lineTo(11.6f, 11f)
            moveTo(11.2f, 8.6f)
            lineTo(12.8f, 9.6f)
        }
    }.build()
}
