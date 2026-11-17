package com.sxdpz23.navisarthi.dash.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Private, cropped copy survives gallery deletion and console reconnects. */
internal class IdleWallpaperStore private constructor(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, "dash-idle-wallpaper.png"))
    private val mutable = MutableStateFlow<Bitmap?>(null)
    val bitmap = mutable.asStateFlow()
    private val ready = CoroutineScope(SupervisorJob() + Dispatchers.IO).async {
        mutable.value = runCatching { file.openRead().use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }
    suspend fun save(bitmap: Bitmap) = withContext(Dispatchers.IO) {
        ready.await()
        val stream = file.startWrite()
        try {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            file.finishWrite(stream)
            mutable.value = bitmap
        } catch (error: Throwable) { file.failWrite(stream); throw error }
    }
    suspend fun remove() = withContext(Dispatchers.IO) {
        ready.await(); file.delete(); mutable.value = null
    }
    companion object {
        @Volatile private var instance: IdleWallpaperStore? = null
        fun get(context: Context): IdleWallpaperStore = instance ?: synchronized(this) {
            instance ?: IdleWallpaperStore(context).also { instance = it }
        }
    }
}

internal data class WallpaperCrop(val x: Int, val y: Int, val side: Int)
internal fun wallpaperCrop(width: Int, height: Int, zoom: Float, horizontal: Float, vertical: Float): WallpaperCrop {
    require(width > 0 && height > 0)
    val side = (minOf(width, height) / zoom.coerceIn(1f, 4f)).toInt().coerceAtLeast(1)
    return WallpaperCrop(((width - side) * horizontal.coerceIn(0f, 1f)).toInt(),
        ((height - side) * vertical.coerceIn(0f, 1f)).toInt(), side)
}
