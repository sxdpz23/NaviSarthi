package com.sxdpz23.navisarthi.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream

object ConsoleAlbumArt {
    internal data class EncodedArtwork(val jpeg: ByteArray, val source: String)
    /** Resolve local player artwork off Main; URI decoding is bounded to a thumbnail. */
    internal fun encodeTrack(context: Context, track: NowPlaying?, notification: MusicNotificationArtwork.Entry?): EncodedArtwork {
        var bitmapFailure: String? = null
        if (track?.art?.isRecycled == false) {
            val encoded = runCatching { encode(track.art) }
            encoded.getOrNull()?.let { return EncodedArtwork(it, "session bitmap") }
            bitmapFailure = encoded.exceptionOrNull()?.javaClass?.simpleName
        }
        val uriBitmap = runCatching {
            val uri = track?.artUri?.let(Uri::parse) ?: return@runCatching null
            if (uri.scheme !in setOf("content", "android.resource")) return@runCatching null
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                decoder.setTargetSize(75, 75)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull()
        if (uriBitmap != null) return try { EncodedArtwork(encode(uriBitmap), "local artwork URI") } finally { uriBitmap.recycle() }
        val drawable = if (track != null && notification != null && artworkTitleMatches(track.title, notification.title))
            runCatching { notification.icon.loadDrawable(context) }.getOrNull() else null
        if (drawable == null) return EncodedArtwork(encode(null),
            bitmapFailure?.let { "placeholder (bitmap encode failed: $it)" } ?: "placeholder (no usable image)")
        val bitmap = Bitmap.createBitmap(75, 75, Bitmap.Config.ARGB_8888)
        return try {
            drawable.setBounds(0, 0, 75, 75)
            drawable.draw(Canvas(bitmap))
            EncodedArtwork(encode(bitmap), "matching media notification")
        } finally { bitmap.recycle() }
    }

    /** Own the output bitmap: Android media-session artwork is owned by its player. */
    fun encode(source: Bitmap?): ByteArray {
        val output = Bitmap.createBitmap(75, 75, Bitmap.Config.ARGB_8888)
        // Session images can be hardware-backed; a software thumbnail canvas cannot draw those.
        val softwareCopy = source?.takeIf { !it.isRecycled && it.config == Bitmap.Config.HARDWARE }
            ?.copy(Bitmap.Config.ARGB_8888, false)
        val drawableSource = softwareCopy ?: source
        try {
            val canvas = Canvas(output)
            canvas.drawColor(Color.BLACK)
            if (drawableSource != null && !drawableSource.isRecycled) {
                val side = minOf(drawableSource.width, drawableSource.height)
                val left = (drawableSource.width - side) / 2
                val top = (drawableSource.height - side) / 2
                canvas.drawBitmap(drawableSource, Rect(left, top, left + side, top + side),
                    Rect(0, 0, 75, 75), Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; textSize = 40f; textAlign = Paint.Align.CENTER }
                canvas.drawText("♪", 37.5f, 52f, paint)
            }
            return ByteArrayOutputStream().use {
                check(output.compress(Bitmap.CompressFormat.JPEG, 50, it))
                it.toByteArray()
            }
        } finally { softwareCopy?.recycle(); output.recycle() }
    }
}
