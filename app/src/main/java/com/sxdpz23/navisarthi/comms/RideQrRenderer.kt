package com.sxdpz23.navisarthi.comms

import android.content.Context
import android.graphics.*
import androidx.core.graphics.ColorUtils
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.sxdpz23.navisarthi.R
import org.json.JSONObject

internal fun renderRideQr(context: Context, token: String, accent: Int): Bitmap {
    val payload = JSONObject().put("v", 1).put("type", "navisarthi-ride").put("inviteToken", token).toString()
    val matrix = Encoder.encode(payload, ErrorCorrectionLevel.H).matrix
    val count = matrix.width
    val unit = 640 / (count + 8)
    val offset = (640 - count * unit) / 2f
    val result = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val ink = Color.rgb(14, 17, 22)
    var finder = accent
    while (ColorUtils.calculateContrast(finder, Color.WHITE) < 5.0) {
        finder = ColorUtils.blendARGB(finder, Color.BLACK, 0.12f)
    }
    fun square(x: Int, y: Int, size: Int, radius: Float, color: Int) {
        paint.color = color
        canvas.drawRoundRect(
            offset + x * unit, offset + y * unit,
            offset + (x + size) * unit, offset + (y + size) * unit,
            radius * unit, radius * unit, paint,
        )
    }
    fun drawCode() {
        canvas.drawColor(Color.WHITE)
        for (y in 0 until count) for (x in 0 until count) {
            if (matrix.get(x, y).toInt() == 1) square(x, y, 1, 0.12f, ink)
        }
        for ((x, y) in listOf(0 to 0, count - 7 to 0, 0 to count - 7)) {
            square(x, y, 7, 0f, Color.WHITE)
            square(x, y, 7, 1.45f, finder)
            square(x + 1, y + 1, 5, 0.8f, Color.WHITE)
            square(x + 2, y + 2, 3, 0.62f, finder)
        }
    }
    drawCode()
    // Isolate the gold linework from the existing launcher artwork.
    val artwork = BitmapFactory.decodeResource(context.resources, R.drawable.navisarthi_logo)
    if (artwork != null) {
        val pixels = IntArray(artwork.width * artwork.height)
        artwork.getPixels(pixels, 0, artwork.width, 0, 0, artwork.width, artwork.height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val alpha = ((Color.red(p) - Color.blue(p) - 12) * 3).coerceIn(0, 255)
            pixels[i] = ColorUtils.setAlphaComponent(finder, alpha)
        }
        val mark = Bitmap.createBitmap(pixels, artwork.width, artwork.height, Bitmap.Config.ARGB_8888)
        val source = Rect(mark.width / 10, mark.height / 4, mark.width * 9 / 10, mark.height * 3 / 4)
        val width = minOf(unit * 9f, 112f)
        val height = width * source.height() / source.width()
        val target = RectF(320 - width / 2, 320 - height / 2, 320 + width / 2, 320 + height / 2)
        paint.color = Color.WHITE
        canvas.drawRect(target.left - 3, target.top - 3, target.right + 3, target.bottom + 3, paint)
        canvas.drawBitmap(mark, source, target, paint)
        // Keep the logo only when the actual styled image decodes to the original payload.
        val rendered = IntArray(640 * 640)
        result.getPixels(rendered, 0, 640, 0, 0, 640, 640)
        val decoded = try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(640, 640, rendered)))).text
        } catch (_: ReaderException) {
            null
        }
        if (decoded != payload) drawCode()
    }
    return result
}
