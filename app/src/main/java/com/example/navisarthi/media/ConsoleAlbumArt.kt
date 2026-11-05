package com.example.navisarthi.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.ByteArrayOutputStream

object ConsoleAlbumArt {
    /** Own the output bitmap: Android media-session artwork is owned by its player. */
    fun encode(source: Bitmap?): ByteArray {
        val output = Bitmap.createBitmap(75, 75, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            canvas.drawColor(Color.BLACK)
            if (source != null && !source.isRecycled) {
                val side = minOf(source.width, source.height)
                val left = (source.width - side) / 2
                val top = (source.height - side) / 2
                canvas.drawBitmap(source, Rect(left, top, left + side, top + side),
                    Rect(0, 0, 75, 75), Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; textSize = 40f; textAlign = Paint.Align.CENTER }
                canvas.drawText("♪", 37.5f, 52f, paint)
            }
            return ByteArrayOutputStream().use {
                check(output.compress(Bitmap.CompressFormat.JPEG, 50, it))
                it.toByteArray()
            }
        } finally { output.recycle() }
    }
}
