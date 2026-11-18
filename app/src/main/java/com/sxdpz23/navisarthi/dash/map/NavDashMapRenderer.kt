package com.sxdpz23.navisarthi.dash.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.sxdpz23.navisarthi.dash.nav.GeoPoint
import com.sxdpz23.navisarthi.navigation.car.CarFraming
import com.sxdpz23.navisarthi.navigation.car.FrontClip
import com.sxdpz23.navisarthi.navigation.car.CarStyle
import com.sxdpz23.navisarthi.navigation.core.model.LatLng
import com.sxdpz23.navisarthi.navigation.core.model.destinationPoint
import com.sxdpz23.navisarthi.navigation.offline.LocalBasemapTiles
import com.sxdpz23.navisarthi.navigation.ui.MapColors
import com.sxdpz23.navisarthi.navigation.ui.GoogleFree
import com.sxdpz23.navisarthi.navigation.ui.MapLook
import com.sxdpz23.navisarthi.navigation.ui.SatelliteLayer
import com.sxdpz23.navisarthi.navigation.ui.Traffic
import com.sxdpz23.navisarthi.navigation.ui.map.MapFonts
import com.sxdpz23.navisarthi.navigation.ui.map.NavSnapshotStyle
import com.sxdpz23.navisarthi.navigation.ui.map.SnapshotterHost
import com.sxdpz23.navisarthi.navigation.ui.map.applyLookTheme
import com.sxdpz23.navisarthi.navigation.ui.map.applyMapTheme
import com.sxdpz23.navisarthi.navigation.ui.map.navTiltCap
import kotlinx.coroutines.*
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng as MLLatLng
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import kotlin.math.cos

/** A second display of Nav's map, not a second navigation/routing engine.
 *
 * The existing Dash loop decides when a frame is needed (moving/idle cadence, screen-off,
 * encoder and RTP stay unchanged). This object has no ticker and never captures the phone UI.
 * Main owns MapLibre, the stream worker draws the latest immutable offscreen snapshot. */
class NavDashMapRenderer(context: Context, private val onFrameReady: () -> Unit = {}) : AutoCloseable {
    private val context = context.applicationContext
    private val comms = com.sxdpz23.navisarthi.comms.CommsRepository.get(context)
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val renderQueue = DeferredMapRender({ work -> main.post { work() } }, ::renderLatest)
    private data class Request(val frame: MapRenderer.Frame, val width: Int, val height: Int)
    private data class Ready(val snapshot: MapSnapshot, val camera: CameraPosition, val key: String, val at: Long, val clip: FrontClip)
    @Volatile private var wanted: Request? = null
    @Volatile private var ready: Ready? = null
    @Volatile private var mapError: String? = null
    @Volatile private var closed = false
    private var snapshotter: MapSnapshotter? = null
    private var styleJob: Job? = null
    private var styleKey = ""
    private var generation = 0L
    private var rendering = false
    private var renderStartedAt = 0L
    private var themed = false
    private var spec: NavSnapshotStyle? = null
    private var trafficRoute: com.sxdpz23.navisarthi.navigation.core.model.Route? = null
    private var trafficSegments: List<Pair<List<GeoPoint>, Int>> = emptyList()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    /** Called by the same stream worker that already paints the encoder frame. */
    fun draw(canvas: Canvas, frame: MapRenderer.Frame) {
        val dark = frame.style != MapRenderer.Style.Light
        canvas.drawColor(if (frame.style == MapRenderer.Style.Black) Color.BLACK else if (dark) Color.rgb(17, 19, 21) else Color.rgb(248, 249, 250))
        val session = com.sxdpz23.navisarthi.dash.nav.NavDisplaySession.state.value
        if (!session.active || session.target != com.sxdpz23.navisarthi.console.ConsoleType.TRIPPER_DASH) {
            text.color = if (dark) Color.LTGRAY else Color.DKGRAY
            val wallpaper = IdleWallpaperStore.get(context).bitmap.value
            if (wallpaper != null) {
                // Encoder surfaces need not be square. Fill by cropping, never by
                // independently stretching the saved square crop along each axis.
                val scale = maxOf(canvas.width.toFloat() / wallpaper.width, canvas.height.toFloat() / wallpaper.height)
                val width = wallpaper.width * scale
                val height = wallpaper.height * scale
                val left = (canvas.width - width) / 2f
                val top = (canvas.height - height) / 2f
                rect.set(left, top, left + width, top + height)
                canvas.drawBitmap(wallpaper, null, rect, bitmapPaint)
                return
            }
            drawDefaultIdle(canvas)
            return
        }
        val current = ready
        if (current != null && !current.snapshot.bitmap.isRecycled) {
            rect.set(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
            val save = canvas.save()
            if (frame.arrived) {
                path.reset(); path.addCircle(canvas.width / 2f, canvas.height.toFloat(), canvas.height * .57f, Path.Direction.CW)
                canvas.clipPath(path)
            }
            canvas.drawBitmap(current.snapshot.bitmap, null, rect, bitmapPaint)
            val selectedInk = frame.routeColor ?: if (frame.goldenRoute) Color.rgb(212, 177, 92) else Color.rgb(66, 133, 244)
            val sourceRoute = session.nav.route
            val ratio = sourceRoute?.trafficRatio
            val color = if (Traffic.on.value && sourceRoute?.trafficSpans.isNullOrEmpty() && ratio != null) when {
                ratio > 1.4 -> 0xFFD93838.toInt()
                ratio > 1.15 -> 0xFFE8923D.toInt()
                else -> selectedInk
            } else selectedInk
            for (route in frame.alternativeRoutes) drawRoute(canvas, current, route, Color.rgb(128, 139, 150), 5f)
            drawRoute(canvas, current, frame.route, Color.rgb(8, 20, 38), 17f)
            drawRoute(canvas, current, frame.route, Color.WHITE, 14f)
            drawRoute(canvas, current, frame.route, color, 10f)
            if (Traffic.on.value && !GoogleFree.on.value) {
                val source = session.nav.route
                if (trafficRoute !== source) {
                    trafficRoute = source
                    trafficSegments = source?.let(::trafficSegmentsOf).orEmpty()
                }
                for ((geometry, ink) in trafficSegments) drawRoute(canvas, current, geometry, ink, 10f)
            }
            if (frame.destLat != null && frame.destLng != null) {
                point(current, frame.destLat, frame.destLng)?.let { p ->
                    fill.color = Color.WHITE; canvas.drawCircle(p.x, p.y, 10f, fill)
                    fill.color = Color.rgb(234, 67, 53); canvas.drawCircle(p.x, p.y, 7f, fill)
                }
            }
            for (rider in com.sxdpz23.navisarthi.comms.commsMapRiders(comms.state.value)) {
                point(current, rider.position.lat, rider.position.lng)?.let { p ->
                    fill.color = if (rider.stale) Color.GRAY else Color.rgb(62, 175, 164)
                    canvas.drawCircle(p.x, p.y, 7f, fill)
                    text.color = Color.WHITE
                    text.setShadowLayer(2f, 0f, 1f, Color.BLACK)
                    canvas.drawText(rider.nickname.take(16), p.x, p.y - 11f, text)
                    text.clearShadowLayer()
                }
            }
            if (!frame.arrived && frame.riderLat != null && frame.riderLng != null) {
                point(current, frame.riderLat, frame.riderLng)?.let { p ->
                    val marker = when { frame.gpsLost -> Color.GRAY; frame.gpsWeak -> Color.rgb(251, 188, 5); else -> color }
                    // Larger route-coloured disc keeps the white rider arrow readable over imagery.
                    fill.color = Color.argb(100, 0, 0, 0); canvas.drawCircle(p.x, p.y + 2f, 22f, fill)
                    fill.color = marker; canvas.drawCircle(p.x, p.y, 20f, fill)
                    val saveMarker = canvas.save()
                    canvas.rotate(frame.heading - current.camera.bearing.toFloat(), p.x, p.y)
                    path.reset(); path.moveTo(p.x, p.y - 15f); path.lineTo(p.x - 10f, p.y + 10f)
                    path.lineTo(p.x, p.y + 5f); path.lineTo(p.x + 10f, p.y + 10f); path.close()
                    fill.color = Color.WHITE; canvas.drawPath(path, fill); canvas.restoreToCount(saveMarker)
                }
            }
            canvas.restoreToCount(save)
        } else {
            text.color = if (dark) Color.LTGRAY else Color.DKGRAY
            canvas.drawText(mapError ?: "Preparing Nav map…", canvas.width / 2f, canvas.height / 2f, text)
        }
        text.color = if (dark) Color.LTGRAY else Color.DKGRAY
        val attribution = buildString {
            append("© OpenStreetMap · OpenFreeMap")
            if (SatelliteLayer.on.value) append(" · Esri")
            if (Traffic.on.value && !GoogleFree.on.value) append(" · Traffic © Google")
        }
        canvas.drawText(attribution, canvas.width / 2f, canvas.height - 7f, text)
        wanted = Request(frame, canvas.width, canvas.height)
        enqueue()
    }

    /** Only the reference's upper display artwork; the bike retains its native lower instruments. */
    private fun drawDefaultIdle(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val cx = w / 2f
        val gold = Color.rgb(224, 188, 89)
        text.textAlign = Paint.Align.CENTER
        text.typeface = android.graphics.Typeface.DEFAULT_BOLD
        text.color = gold
        text.textSize = w * .025f
        canvas.drawText("N A V I S A R T H I", cx, h * .23f, text)
        line.color = gold; line.strokeWidth = 1.2f
        canvas.drawLine(cx - w * .055f, h * .29f, cx + w * .055f, h * .29f, line)

        text.textSize = w * .052f
        text.textAlign = Paint.Align.LEFT
        // Use one face, weight, size and baseline for the whole headline;
        // only Adventure's colour changes, avoiding italic font metric differences.
        text.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val lead = "Ready for "
        val leadWidth = text.measureText(lead)
        val tailWidth = text.measureText("Adventure")
        val left = cx - (leadWidth + tailWidth) / 2f
        text.typeface = android.graphics.Typeface.DEFAULT_BOLD
        text.color = Color.WHITE
        canvas.drawText(lead, left, h * .48f, text)
        text.color = Color.rgb(48, 218, 40)
        canvas.drawText("Adventure", left + leadWidth, h * .48f, text)

        text.typeface = android.graphics.Typeface.DEFAULT
        text.textAlign = Paint.Align.CENTER
        text.textSize = w * .030f
        text.color = Color.LTGRAY
        canvas.drawText("Start a route on your phone.", cx, h * .59f, text)
        text.textSize = w * .025f
        val labels = listOf("Open Nav", "Choose destination", "Tap Start")
        val centres = listOf(w * .22f, w * .5f, w * .78f)
        for (index in labels.indices) {
            val number = "0${index + 1}  "
            val numberWidth = text.measureText(number)
            val labelWidth = text.measureText(labels[index])
            val x = centres[index] - (numberWidth + labelWidth) / 2f
            text.textAlign = Paint.Align.LEFT
            text.color = gold
            canvas.drawText(number, x, h * .79f, text)
            text.color = Color.LTGRAY
            canvas.drawText(labels[index], x + numberWidth, h * .79f, text)
        }
        for (x in listOf(w * .345f, w * .655f))
            canvas.drawLine(x, h * .735f, x, h * .81f, line)
        text.textAlign = Paint.Align.CENTER
        text.textSize = 11f
    }

    private fun point(ready: Ready, lat: Double, lng: Double): android.graphics.PointF? =
        if (ready.clip.inFront(lat, lng)) projectRaw(ready, lat, lng) else null

    private fun projectRaw(ready: Ready, lat: Double, lng: Double): android.graphics.PointF? = runCatching {
        ready.snapshot.pixelForLatLng(MLLatLng(lat, lng)).takeIf { it.x.isFinite() && it.y.isFinite() }
    }.getOrNull()

    private fun drawRoute(canvas: Canvas, ready: Ready, route: List<GeoPoint>, color: Int, width: Float) {
        if (route.size < 2) return
        path.reset()
        var started = false
        var previous: GeoPoint? = null
        var previousIn = false
        for (coordinate in route) {
            val inside = ready.clip.inFront(coordinate.lat, coordinate.lng)
            val before = previous
            if (before != null && previousIn != inside) {
                val t = ready.clip.crossing(before.lat, before.lng, coordinate.lat, coordinate.lng)
                val edge = projectRaw(ready, before.lat + (coordinate.lat - before.lat) * t,
                    before.lng + (coordinate.lng - before.lng) * t)
                if (edge != null) {
                    if (previousIn && started) path.lineTo(edge.x, edge.y)
                    else if (inside) path.moveTo(edge.x, edge.y)
                }
                started = inside && edge != null
            }
            previous = coordinate
            previousIn = inside
            val p = if (inside) projectRaw(ready, coordinate.lat, coordinate.lng) else null
            if (p == null || kotlin.math.abs(p.x) > 100_000 || kotlin.math.abs(p.y) > 100_000) { started = false; continue }
            if (started) path.lineTo(p.x, p.y) else { path.moveTo(p.x, p.y); started = true }
        }
        line.color = color; line.strokeWidth = width; canvas.drawPath(path, line)
    }

    /** Cache actual Nav traffic offsets on its geometry; never estimate traffic independently. */
    private fun trafficSegmentsOf(route: com.sxdpz23.navisarthi.navigation.core.model.Route): List<Pair<List<GeoPoint>, Int>> {
        val points = route.polyline
        if (points.size < 2) return emptyList()
        val projection = com.sxdpz23.navisarthi.navigation.core.nav.RouteProjection
        val cumulative = projection.cumulative(points)
        val length = cumulative.lastOrNull() ?: return emptyList()
        if (route.distanceMeters <= 0.0 || length <= 0.0) return emptyList()
        return route.trafficSpans.mapNotNull { span ->
            val start = (span.startMeters / route.distanceMeters * length).coerceIn(0.0, length)
            val end = ((span.startMeters + span.lengthMeters) / route.distanceMeters * length).coerceIn(start, length)
            if (end <= start) return@mapNotNull null
            val geometry = listOf(projection.pointAt(points, cumulative, start)) +
                points.filterIndexed { index, _ -> cumulative[index] > start && cumulative[index] < end } +
                projection.pointAt(points, cumulative, end)
            val ink = when { span.level <= 1 -> 0xFFE8923D.toInt(); span.level == 2 -> 0xFFD93838.toInt(); else -> 0xFFA11D1D.toInt() }
            geometry.map { GeoPoint(it.lat, it.lng) } to ink
        }
    }

    private fun enqueue() {
        renderQueue.request()
    }

    private fun renderLatest() {
        val request = wanted ?: return
        val f = request.frame
        val look = MapLook.style.value
        val effective = MapFonts.effective(look.uri)
        val satellite = SatelliteLayer.on.value
        val traffic = Traffic.on.value && !GoogleFree.on.value && !com.sxdpz23.navisarthi.dash.nav.NavDisplaySession.state.value.active
        val key = "$effective|${look.key}|${f.style}|${MapColors.current()}|${f.navBasemapArchive}|$satellite|$traffic|${request.width}x${request.height}"
        if (styleKey != key) {
            styleKey = key; generation++; rendering = false; themed = false
            ready = null // never present the previous look as the newly selected one
            onFrameReady()
            runCatching { snapshotter?.cancel() }; snapshotter = null
            styleJob?.cancel()
            val token = generation
            styleJob = scope.launch {
                try {
                    val resolved = withContext(Dispatchers.IO) { NavSnapshotStyle.resolve(context, look, effective, f.navBasemapArchive, satellite) }
                    if (closed || token != generation) return@launch
                    MapLibre.getInstance(context)
                    LocalBasemapTiles.installIntoMapLibre()
                    val options = MapSnapshotter.Options(request.width, request.height)
                        .let { if (resolved.json != null) it.withStyleJson(resolved.json) else it.withStyle(resolved.uri) }
                        .withPixelRatio(1f).withLogo(false)
                    spec = resolved
                    snapshotter = QuietNavSnapshotter(context, options)
                    enqueue()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    if (token == generation) {
                        styleKey = ""
                        mapError = "Map unavailable · open Nav online"
                        onFrameReady()
                    }
                    android.util.Log.w("NaviSarthiDashMap", "Nav style unavailable", error)
                }
            }
            return
        }
        val snapshotter = snapshotter ?: return
        if (rendering) {
            if (SystemClock.elapsedRealtime() - renderStartedAt > 20_000L) {
                // Native snapshots may wait indefinitely on tiles requested during Wi-Fi handover.
                generation++
                runCatching { snapshotter.cancel() }
                this.snapshotter = null
                rendering = false
                styleKey = ""
                enqueue()
            }
            return
        }
        val zoom = (f.zoom - 1.0).coerceIn(1.0, 22.0) // preserve existing 256px-based Dash zoom steps
        val bearing = if (f.headingUp) f.heading.toDouble() else 0.0
        val tilt = if (f.headingUp && f.tilt3d) navTiltCap(zoom) else 0.0
        val base = LatLng(
            Mercator.tileYToLat(Mercator.latToTileY(f.centerLat, f.zoom) + f.panY / Mercator.TILE_SIZE, f.zoom),
            Mercator.tileXToLng(Mercator.lngToTileX(f.centerLng, f.zoom) + f.panX / Mercator.TILE_SIZE, f.zoom),
        )
        val pivot = if (f.headingUp) request.height * (if (tilt > 0) .70 else .66) else request.height / 2.0
        val ahead = CarFraming.targetOffset(pivot - request.height / 2.0, 0.0, tilt, request.height.toDouble()).first
        val metersPerPixel = 78271.517 * cos(Math.toRadians(base.lat)) / Math.pow(2.0, zoom)
        val target = base.destinationPoint(ahead * metersPerPixel, bearing)
        val camera = CameraPosition.Builder().target(MLLatLng(target.lat, target.lng)).zoom(zoom).bearing(bearing).tilt(tilt).build()
        val clip = FrontClip(target.lat, target.lng, bearing,
            CarFraming.behindLimitPx(tilt, request.height.toDouble()) *
                (78271.517 * cos(Math.toRadians(target.lat)) / Math.pow(2.0, zoom)))
        val last = ready
        if (last?.key == key && last.camera == camera && SystemClock.elapsedRealtime() - last.at < 30_000L) return
        rendering = true
        renderStartedAt = SystemClock.elapsedRealtime()
        val token = generation
        runCatching {
            snapshotter.setCameraPosition(camera)
            snapshotter.start({ result ->
                if (closed || token != generation) return@start
                rendering = false
                if (!themed) {
                    val host = SnapshotterHost(snapshotter, spec?.layerIds.orEmpty())
                    runCatching {
                        if (spec?.painted == true) applyMapTheme(host, dark = f.style != MapRenderer.Style.Light, amoled = f.style == MapRenderer.Style.Black)
                        else applyLookTheme(host, dark = f.style != MapRenderer.Style.Light)
                        host.getLayer(CarStyle.TRAFFIC_LAYER)?.setProperties(PropertyFactory.visibility(if (traffic) Property.VISIBLE else Property.NONE))
                        if (satellite) for (layer in host.layers) if (layer is SymbolLayer) layer.setProperties(
                            PropertyFactory.textColor("#ffffff"), PropertyFactory.textHaloColor("#000000"), PropertyFactory.textHaloWidth(1.3f),
                        )
                    }.onFailure { android.util.Log.w("NaviSarthiDashMap", "Nav palette could not be applied", it) }
                    themed = true
                    enqueue()
                    return@start
                }
                ready = Ready(result, camera, key, SystemClock.elapsedRealtime(), clip)
                mapError = null
                onFrameReady()
                if (wanted !== request) enqueue()
            }, { message ->
                if (token == generation) {
                    rendering = false
                    mapError = "Map unavailable · retrying"
                    onFrameReady()
                }
                android.util.Log.w("NaviSarthiDashMap", "Nav snapshot failed: $message")
            })
        }.onFailure { rendering = false; android.util.Log.w("NaviSarthiDashMap", "Nav snapshot unavailable", it) }
    }

    override fun close() {
        closed = true; wanted = null; ready = null
        renderQueue.close()
        main.post { generation++; styleJob?.cancel(); runCatching { snapshotter?.cancel() }; snapshotter = null; scope.cancel() }
    }
}

private class QuietNavSnapshotter(context: Context, options: MapSnapshotter.Options) : MapSnapshotter(context, options) {
    override fun addOverlay(mapSnapshot: MapSnapshot) {} // one explicit attribution line is drawn in the Dash safe area
}
