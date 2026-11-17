package com.sxdpz23.navisarthi.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as BitmapCanvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sxdpz23.navisarthi.dash.map.IdleWallpaperStore
import com.sxdpz23.navisarthi.dash.map.wallpaperCrop
import kotlinx.coroutines.*

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun DashWallpaperControls() {
    val context = LocalContext.current
    val store = remember { IdleWallpaperStore.get(context) }
    val current by store.bitmap.collectAsState()
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true; error = null
            try {
                source = withContext(Dispatchers.IO) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val ratio = minOf(1.0, 2048.0 / maxOf(info.size.width, info.size.height))
                        decoder.setTargetSize(maxOf(1, (info.size.width * ratio).toInt()), maxOf(1, (info.size.height * ratio).toInt()))
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Could not open this image. Please choose another." }
            finally { busy = false }
        }
    }
    com.sxdpz23.navisarthi.ui.components.NaviSarthiCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Idle wallpaper", style = MaterialTheme.typography.titleMedium)
    Text("Shown on Dash when navigation is inactive. A cropped copy is saved on this phone for future connections.", style = MaterialTheme.typography.bodySmall)
    current?.let {
        androidx.compose.foundation.Image(it.asImageBitmap(), contentDescription = "Saved Dash idle wallpaper",
            modifier = Modifier.size(112.dp).clip(CircleShape))
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Text(if (current == null) "Choose image" else "Change image")
        }
        if (current != null) TextButton(enabled = !busy, onClick = {
            scope.launch {
                busy = true
                try { store.remove() } catch (_: Exception) { error = "Could not remove wallpaper." }
                finally { busy = false }
            }
        }) { Text("Remove") }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    source?.let { image ->
        var zoom by remember(image) { mutableFloatStateOf(1f) }
        var horizontal by remember(image) { mutableFloatStateOf(.5f) }
        var vertical by remember(image) { mutableFloatStateOf(.5f) }
        val crop = wallpaperCrop(image.width, image.height, zoom, horizontal, vertical)
        AlertDialog(
            onDismissRequest = { if (!busy) source = null },
            title = { Text("Crop Dash wallpaper") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("The circle previews the round Dash display. Adjust zoom and position before saving.")
                    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)) {
                        drawImage(image.asImageBitmap(), IntOffset(crop.x, crop.y), IntSize(crop.side, crop.side),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text("Zoom")
                    Slider(zoom, { zoom = it }, valueRange = 1f..4f, enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = "Wallpaper zoom" })
                    Text("Horizontal position")
                    Slider(horizontal, { horizontal = it }, enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = "Wallpaper horizontal position" })
                    Text("Vertical position")
                    Slider(vertical, { vertical = it }, enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = "Wallpaper vertical position" })
                }
            },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true; error = null
                    try {
                        val cropped = withContext(Dispatchers.Default) {
                            Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also {
                                BitmapCanvas(it).drawBitmap(image, Rect(crop.x, crop.y, crop.x + crop.side, crop.y + crop.side),
                                    Rect(0, 0, 512, 512), Paint(Paint.FILTER_BITMAP_FLAG))
                            }
                        }
                        store.save(cropped); source = null
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = "Could not save wallpaper. Please try again." }
                    finally { busy = false }
                }
            }) { Text("Set wallpaper") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { source = null }) { Text("Cancel") } },
        )
    }
}
