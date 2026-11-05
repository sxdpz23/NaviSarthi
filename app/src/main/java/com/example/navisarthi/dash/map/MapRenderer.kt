package com.example.navisarthi.dash.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.DashPathEffect
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.example.navisarthi.dash.nav.GeoPoint

/**
 * Draws the navigation frame for the Tripper Dash (526 × 300).
 *
 * Layers: OSM tiles (already dark-filtered by TileProvider) → road route polyline
 * → destination pin → rider marker → top banner (name + remaining) → maneuver chip.
 * Optional heading-up rotation. Paint/Path/Rect objects are reused across frames
 * to avoid per-frame allocation churn.
 */
class MapRenderer internal constructor(private val tiles: TileProvider, private val trafficTiles: TrafficTiles? = null) {

    enum class Style { Light, Dark, Black }

    data class Frame(
        val centerLat: Double,
        val centerLng: Double,
        val zoom: Int,
        val panX: Float = 0f,
        val panY: Float = 0f,
        val headingUp: Boolean = false,
        val heading: Float = 0f,           // travel bearing, degrees
        val riderLat: Double? = null,
        val riderLng: Double? = null,
        val destLat: Double? = null,
        val destLng: Double? = null,
        val destName: String? = null,
        val route: List<GeoPoint> = emptyList(),
        val maneuverText: String? = null,  // e.g. "Turn left · 400 m"
        val remainingText: String? = null, // e.g. "186 km"
        val style: Style = Style.Light,
        val arrived: Boolean = false,
        val arrivalDuration: String? = null,
        val journeyAverageKmh: Double? = null,
        val journeyMaxKmh: Double? = null,
        val goldenRoute: Boolean = false,
        val traffic: Boolean = false,
        val tilt3d: Boolean = false,       // perspective 3D view (nav heading-up only)
        val etaPrimary: String? = null,    // big glance value, e.g. "24 min" (nav only)
        val etaSecondary: String? = null,  // smaller line, e.g. "18 km · 13:32"
        val gpsWeak: Boolean = false,      // imprecise/slow fix → amber marker + "GPS weak" pill
        val gpsLost: Boolean = false,      // no fresh fix → grey marker + "GPS lost" pill
    )

    private val bgColor   = Color.rgb(229, 227, 223) // Google Maps land colour, behind missing tiles
    private val routeBlue = Color.rgb(66, 133, 244)  // Google Maps directions blue (#4285F4)
    private val googleRed = Color.rgb(234, 67, 53)   // Google destination pin red (#EA4335)

    private val tilePaint  = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        // Google tiles already carry the right colours/contrast — only a gentle
        // saturation nudge to help against the dash TFT's daylight wash-out. No
        // brightness/contrast tricks (those flattened or clipped the map before).
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.2f) })
    }
    private val routeCasing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE
        strokeWidth = 13f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = routeBlue; style = Paint.Style.STROKE
        strokeWidth = 9f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f; isFakeBoldText = true }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = routeBlue; textSize = 19f; isFakeBoldText = true }
    private val bannerPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(215, 13, 15, 17) }
    private val standbyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 64, 67); textSize = 22f; isFakeBoldText = true }

    // ETA pill (drawn in screen space, bottom-centre, inside the round safe zone)
    private val etaBgPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(232, 20, 22, 26) }
    private val etaBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val etaBigPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(126, 217, 87); textSize = 20f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }   // Google-nav green
    private val etaSmallPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(196, 201, 208); textSize = 12f; textAlign = Paint.Align.CENTER }
    // GPS-health pill (top-centre, shown only when the signal is weak/lost)
    private val gpsPillText    = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }

    // Reused across frames
    private val trafficClip = Path()
    private val arrivalMessagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val trafficMessagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 15f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private fun hasSlowTraffic(bitmap: Bitmap, x: Int, y: Int, radius: Int): Boolean {
        var matches = 0
        for (row in maxOf(0, y - radius)..minOf(bitmap.height - 1, y + radius)) {
            for (column in maxOf(0, x - radius)..minOf(bitmap.width - 1, x + radius)) {
                val pixel = bitmap.getPixel(column, row)
                if (Color.alpha(pixel) < 200) continue
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                val orange = kotlin.math.abs(red - 241) <= 10 && kotlin.math.abs(green - 130) <= 10 &&
                    kotlin.math.abs(blue - 55) <= 10
                val severe = kotlin.math.abs(red - 231) <= 10 && green <= 17 && blue <= 14
                if ((orange || severe) && ++matches >= 3) return true
            }
        }
        return false
    }
    private val trafficPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val trafficStroke = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val routePath = Path()
    private val riderPath = Path()
    private val tmpRect = RectF()
    private val pillRect = RectF()
    private val tileSrc = Rect()      // sub-region of an ancestor tile when scaling up
    private val childDst = RectF()    // quadrant target when scaling children down
    private val textBounds = Rect()
    private val tiltMatrix = Matrix()

    private val lightFilter = tilePaint.colorFilter
    private fun nightFilter(red: Float, green: Float, blue: Float) = ColorMatrixColorFilter(floatArrayOf(
        -.21f, -.72f, -.07f, 0f, red,
        -.21f, -.72f, -.07f, 0f, green,
        -.21f, -.72f, -.07f, 0f, blue,
        0f, 0f, 0f, 1f, 0f,
    ))
    private val darkFilter = nightFilter(247f, 265f, 295f)
    private val miniClip = Path()
    private val miniBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
    }
    private val journeyStatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 22f
    }
    private val journeyStatLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.LTGRAY
        textSize = 11f
    }
    private val perspectiveCamera = android.graphics.Camera()

    fun draw(canvas: Canvas, f: Frame) {
        val w = canvas.width
        val h = canvas.height
        canvas.drawColor(when (f.style) {
            Style.Light -> bgColor
            Style.Dark -> Color.rgb(24, 43, 73)
            Style.Black -> Color.BLACK
        })
        tilePaint.colorFilter = when (f.style) {
            Style.Light -> lightFilter
            Style.Dark -> darkFilter
            Style.Black -> null
        }
        val labelFree = f.style == Style.Black
        val accent = when {
            labelFree && f.goldenRoute -> Color.rgb(212, 177, 92)
            f.style == Style.Light -> routeBlue
            else -> Color.rgb(80, 198, 250)
        }
        routePaint.color = accent
        if (f.arrived) {
            canvas.drawColor(Color.BLACK)
            canvas.save()
            miniClip.reset()
            val radius = h * .57f
            miniClip.addCircle(w / 2f, h.toFloat(), radius, Path.Direction.CW)
            canvas.clipPath(miniClip)
        }
        routeCasing.color = if (f.style == Style.Light) Color.WHITE else Color.rgb(16, 37, 56)

        val rotate = f.headingUp
        val tilt = rotate && f.tilt3d
        // Nav view: bias the rider toward the lower third so the road AHEAD fills the
        // screen (like Google Maps navigation). 3D pushes it lower still. North-up
        // view keeps the rider centred.
        val pivotY = if (f.arrived) h * .73f else if (rotate) (if (tilt) h * 0.70f else h * 0.66f) else h / 2f

        val ts = Mercator.TILE_SIZE
        val cx = Mercator.lngToTileX(f.centerLng, f.zoom) * ts + f.panX
        val cy = Mercator.latToTileY(f.centerLat, f.zoom) * ts + f.panY
        val left = cx - w / 2.0
        val top  = cy - pivotY

        fun sx(lng: Double) = (Mercator.lngToTileX(lng, f.zoom) * ts - left).toFloat()
        fun sy(lat: Double) = (Mercator.latToTileY(lat, f.zoom) * ts - top).toFloat()

        if (rotate) {
            canvas.save()
            if (tilt) {
                perspectiveCamera.save()
                perspectiveCamera.setLocation(0f, 0f, -14f)
                perspectiveCamera.rotateX(28f)
                perspectiveCamera.getMatrix(tiltMatrix)
                perspectiveCamera.restore()
                tiltMatrix.preTranslate(-w / 2f, -pivotY)
                tiltMatrix.postTranslate(w / 2f, pivotY)
                canvas.concat(tiltMatrix)
            }
            canvas.rotate(-f.heading, w / 2f, pivotY)
        }

        // ── Tiles (padded when rotating so corners are covered) ──
        val pad = if (rotate) (maxOf(w, h) * 0.45).toInt() else 0
        val txMin = Math.floorDiv((left - pad).toInt(), ts)
        val tyMin = Math.floorDiv((top - pad).toInt(), ts)
        val txMax = Math.floorDiv((left + w + pad).toInt(), ts)
        val tyMax = Math.floorDiv((top + h + pad).toInt(), ts)
        for (tx in txMin..txMax) for (ty in tyMin..tyMax) {
            val dstL = (tx * ts - left).toFloat()
            val dstT = (ty * ts - top).toFloat()
            tmpRect.set(dstL, dstT, dstL + ts, dstT + ts)
            drawTileBestEffort(canvas, f.zoom, tx, ty, tmpRect, labelFree)
        }

        var congestionNearRider = false
        // ── Road route polyline ──
        if (!f.arrived && f.route.size >= 2) {
            routePath.reset()
            routePath.moveTo(sx(f.route[0].lng), sy(f.route[0].lat))
            for (i in 1 until f.route.lastIndex) {
                val prev = f.route[i - 1]
                val point = f.route[i]
                val next = f.route[i + 1]
                val x = sx(point.lng); val y = sy(point.lat)
                val dx1 = sx(prev.lng) - x; val dy1 = sy(prev.lat) - y
                val dx2 = sx(next.lng) - x; val dy2 = sy(next.lat) - y
                val a = (4f / kotlin.math.hypot(dx1, dy1).coerceAtLeast(1f)).coerceAtMost(.25f)
                val b = (4f / kotlin.math.hypot(dx2, dy2).coerceAtLeast(1f)).coerceAtMost(.25f)
                routePath.lineTo(x + dx1 * a, y + dy1 * a)
                routePath.quadTo(x, y, x + dx2 * b, y + dy2 * b)
            }
            routePath.lineTo(sx(f.route.last().lng), sy(f.route.last().lat))
            canvas.drawPath(routePath, routeCasing)
            canvas.drawPath(routePath, routePaint)
            if (f.traffic && trafficTiles != null) {
                trafficStroke.getFillPath(routePath, trafficClip)
                canvas.save()
                if (canvas.clipPath(trafficClip)) {
                    val zoom = f.zoom.coerceAtMost(19)
                    val scale = 1 shl (f.zoom - zoom)
                    val span = ts * scale
                    val riderX = f.riderLng?.let(::sx)
                    val riderY = f.riderLat?.let(::sy)
                    for (x in Math.floorDiv(txMin, scale)..Math.floorDiv(txMax, scale)) {
                        for (y in Math.floorDiv(tyMin, scale)..Math.floorDiv(tyMax, scale)) {
                            val tileX = (x * span - left).toFloat()
                            val tileY = (y * span - top).toFloat()
                            tmpRect.set(tileX, tileY, tileX + span, tileY + span)
                            if (canvas.quickReject(tmpRect)) continue
                            trafficTiles.get(zoom, x, y)?.let { tile ->
                                canvas.drawBitmap(tile, null, tmpRect, trafficPaint)
                                if (!congestionNearRider && !f.gpsLost && !f.gpsWeak && riderX != null &&
                                    riderY != null && tmpRect.contains(riderX, riderY)) {
                                    val px = ((riderX - tileX) * tile.width / span).toInt()
                                    val py = ((riderY - tileY) * tile.height / span).toInt()
                                    congestionNearRider = hasSlowTraffic(tile, px, py, (7 * tile.width / span).coerceAtLeast(1))
                                }
                            }
                        }
                    }
                }
                canvas.restore()
            }
        }

        // ── Destination pin ──
        if (f.destLat != null && f.destLng != null) {
            val dx = sx(f.destLng); val dy = sy(f.destLat)
            // Google-style red destination pin (white ring + red fill).
            dotPaint.color = Color.WHITE; canvas.drawCircle(dx, dy, 12f, dotPaint)
            dotPaint.color = googleRed; canvas.drawCircle(dx, dy, 9f, dotPaint)
            dotPaint.color = Color.WHITE; canvas.drawCircle(dx, dy, 3.5f, dotPaint)
        }

        // ── Rider marker — colour tracks GPS health (blue good, amber weak, grey lost) ──
        if (!f.arrived && f.riderLat != null && f.riderLng != null) {
            val rx = sx(f.riderLng); val ry = sy(f.riderLat)
            val markerColor = when {
                f.gpsLost -> Color.rgb(150, 154, 160)  // grey: position is stale
                f.gpsWeak -> Color.rgb(251, 188, 5)    // amber: imprecise
                else      -> accent                  // blue: good
            }
            val haloColor = Color.argb(60, Color.red(markerColor), Color.green(markerColor), Color.blue(markerColor))
            dotPaint.color = haloColor; canvas.drawCircle(rx, ry, 17f, dotPaint)
            if (rotate) {
                // Heading-up: chevron pointing up (travel direction)
                riderPath.reset()
                riderPath.moveTo(rx, ry - 16f)
                riderPath.lineTo(rx - 11f, ry + 10f)
                riderPath.lineTo(rx, ry + 5f)
                riderPath.lineTo(rx + 11f, ry + 10f)
                riderPath.close()
                canvas.save(); canvas.rotate(f.heading, rx, ry)
                dotPaint.color = markerColor; canvas.drawCircle(rx, ry, 18f, dotPaint)
                dotPaint.color = Color.WHITE; canvas.drawPath(riderPath, dotPaint)
                canvas.restore()
            } else {
                dotPaint.color = Color.WHITE; canvas.drawCircle(rx, ry, 8f, dotPaint)
                dotPaint.color = markerColor; canvas.drawCircle(rx, ry, 5.5f, dotPaint)
            }
        }

        if (rotate) canvas.restore()
        if (f.arrived) canvas.restore()
        if (f.traffic) {
            val label = trafficTiles?.status?.ifBlank { "Traffic © TomTom" } ?: "Traffic unavailable"
            val cx = w / 2f
            val half = etaSmallPaint.measureText(label) / 2f + 8f
            pillRect.set(cx - half, h - 23f, cx + half, h - 3f)
            canvas.drawRoundRect(pillRect, 8f, 8f, etaBgPaint)
            canvas.drawText(label, cx, h - 9f, etaSmallPaint)
        }
        if (labelFree) canvas.drawText("© OpenStreetMap contributors · OpenFreeMap", w / 2f, h - 7f, etaSmallPaint)

        // ── ETA pill (screen-space so it stays upright; bottom-centre safe zone) ──
        // The dash is round, so it's kept narrow and centred. Shows ETA only (time +
        // arrival clock) — distance lives on the dash's own widget.
        f.etaPrimary?.takeUnless { f.arrived }?.let { primary ->
            val secondary = f.etaSecondary
            val padH = 18f; val padV = 8f; val gap = 1f
            val bigFm = etaBigPaint.fontMetrics
            val smallFm = etaSmallPaint.fontMetrics
            val bigH = bigFm.descent - bigFm.ascent
            val smallH = if (secondary != null) smallFm.descent - smallFm.ascent else 0f
            val contentW = maxOf(etaBigPaint.measureText(primary), secondary?.let { etaSmallPaint.measureText(it) } ?: 0f)
            val pillW = (contentW + padH * 2).coerceAtMost(w * 0.6f)
            val pillH = padV * 2 + bigH + (if (secondary != null) gap + smallH else 0f)
            val cxp = w / 2f
            val bottom = h - 26f
            val top = bottom - pillH
            pillRect.set(cxp - pillW / 2f, top, cxp + pillW / 2f, bottom)
            val r = pillH / 2f
            canvas.drawRoundRect(pillRect, r, r, etaBgPaint)
            canvas.drawRoundRect(pillRect, r, r, etaBorderPaint)
            var baseline = top + padV - bigFm.ascent
            canvas.drawText(primary, cxp, baseline, etaBigPaint)
            if (secondary != null) {
                baseline += bigFm.descent + gap - smallFm.ascent
                canvas.drawText(secondary, cxp, baseline, etaSmallPaint)
            }
        }

        // ── GPS-health pill (top-centre safe zone) — only when degraded, so the rider knows the
        // marker is imprecise/held rather than wondering why the map stopped tracking ──
        if (f.gpsLost || f.gpsWeak) {
            val label = if (f.gpsLost) "GPS lost" else "GPS weak"
            gpsPillText.color = if (f.gpsLost) googleRed else Color.rgb(251, 188, 5)
            val padH = 14f; val padV = 6f
            val fm = gpsPillText.fontMetrics
            val textH = fm.descent - fm.ascent
            val pillW = gpsPillText.measureText(label) + padH * 2
            val cxp = w / 2f
            val top = 14f
            val bottom = top + padV * 2 + textH
            pillRect.set(cxp - pillW / 2f, top, cxp + pillW / 2f, bottom)
            val r = (bottom - top) / 2f
            canvas.drawRoundRect(pillRect, r, r, etaBgPaint)
            canvas.drawRoundRect(pillRect, r, r, etaBorderPaint)
            canvas.drawText(label, cxp, top + padV - fm.ascent, gpsPillText)
        }

        if (congestionNearRider) {
            val message = "Please be patient, and ensure safety!"
            val cx = w / 2f
            val half = trafficMessagePaint.measureText(message) / 2f + 14f
            pillRect.set(cx - half, 82f, cx + half, 126f)
            canvas.drawRoundRect(pillRect, 11f, 11f, etaBgPaint)
            canvas.drawText("One step at a time 🌤️", cx, 99f, etaSmallPaint)
            canvas.drawText(message, cx, 118f, trafficMessagePaint)
        }

        f.maneuverText?.let { cue ->
            val center = w / 2f
            val halfWidth = etaSmallPaint.measureText(cue) / 2f + 13f
            pillRect.set(center - halfWidth, 47f, center + halfWidth, 76f)
            canvas.drawRoundRect(pillRect, 14f, 14f, etaBgPaint)
            canvas.drawText(cue, center, 66f, etaSmallPaint)
        }

        if (f.arrived) {
            val radius = h * .57f
            miniBorderPaint.color = Color.rgb(75, 79, 84)
            pillRect.set(w / 2f - radius, h - radius, w / 2f + radius, h + radius)
            canvas.drawArc(pillRect, 180f, 180f, false, miniBorderPaint)
            journeyStatPaint.color = accent
            fun drawJourneyStat(x: Float, label: String, value: Double?) {
                canvas.drawText(label, x, h * .68f, journeyStatLabelPaint)
                canvas.drawText(value?.takeIf { it.isFinite() }?.let { "%.0f".format(it) } ?: "—",
                    x, h * .78f, journeyStatPaint)
                canvas.drawText("km/h", x, h * .85f, journeyStatLabelPaint)
            }
            drawJourneyStat((w / 2f - radius) / 2f, "AVG", f.journeyAverageKmh)
            drawJourneyStat(w - (w / 2f - radius) / 2f, "MAX", f.journeyMaxKmh)
            arrivalMessagePaint.color = accent
            canvas.drawText("You have", w / 2f, h * .22f, arrivalMessagePaint)
            canvas.drawText("Reached Your Destination ❤️", w / 2f, h * .32f, arrivalMessagePaint)
            f.arrivalDuration?.let { canvas.drawText(it, w / 2f, h * .90f, etaBigPaint) }
            return
        }

        pillRect.set(w * .70f, h - 29f, w * .90f, h - 7f)
        canvas.drawRoundRect(pillRect, 10f, 10f, etaBgPaint)
        canvas.drawText("−  ZOOM  +", pillRect.centerX(), h - 14f, etaSmallPaint)

        // ── Standby when nothing to show (dark text on the light map bg) ──
        if (f.riderLat == null && f.destLat == null) {
            val msg = "NAVISARTHI · waiting for GPS"
            standbyPaint.getTextBounds(msg, 0, msg.length, textBounds)
            canvas.drawText(msg, (w - textBounds.width()) / 2f, h / 2f, standbyPaint)
        }
    }

    /**
     * Draws one map tile, never leaving a blank cell on a cache miss. Slippy-map trick:
     * 1. exact tile if cached (also kicks off the async load);
     * 2. else scale UP the matching sub-region of a cached lower-zoom ancestor — the level
     *    we just left is still in memory, so a zoom-IN renders instantly and only sharpens
     *    once the real tile arrives (no blank flash, which is what made zoom feel slow);
     * 3. else scale DOWN the four cached higher-zoom children into quadrants (zoom-OUT case).
     */
    private fun drawTileBestEffort(canvas: Canvas, z: Int, tx: Int, ty: Int, dst: RectF, labelFree: Boolean) {
        tiles.get(z, tx, ty, labelFree)?.let { canvas.drawBitmap(it, null, dst, tilePaint); return }

        var up = 1
        while (up <= 4 && z - up >= 0) {
            val anc = tiles.getCached(z - up, tx shr up, ty shr up, labelFree)
            if (anc != null) {
                val cells = 1 shl up
                // Sub-region in the bitmap's OWN pixel space (512 px for hi-DPI tiles, 256 for any
                // legacy tile) — not the logical TILE_SIZE, or it would sample the wrong quadrant.
                val sub = anc.width / cells
                val sxOff = (tx and (cells - 1)) * sub
                val syOff = (ty and (cells - 1)) * sub
                tileSrc.set(sxOff, syOff, sxOff + sub, syOff + sub)
                canvas.drawBitmap(anc, tileSrc, dst, tilePaint)
                return
            }
            up++
        }

        val halfW = (dst.right - dst.left) / 2f
        val halfH = (dst.bottom - dst.top) / 2f
        for (qx in 0..1) for (qy in 0..1) {
            val child = tiles.getCached(z + 1, tx * 2 + qx, ty * 2 + qy, labelFree) ?: continue
            childDst.set(
                dst.left + qx * halfW, dst.top + qy * halfH,
                dst.left + qx * halfW + halfW, dst.top + qy * halfH + halfH,
            )
            canvas.drawBitmap(child, null, childDst, tilePaint)
        }
        // Deep miss across all levels → the land-colour bg shows through (rare).
    }
}
