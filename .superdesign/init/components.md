# Shared Jetpack Compose components. Custom Material 3 primitives; no web frontend.

## app\src\main\java\com\example\navisarthi\ui\components\BarChart.kt
~~~kotlin
package com.example.navisarthi.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.navisarthi.ui.theme.*

data class BarEntry(val label: String, val value: Float)

@Composable
fun NaviSarthiBarChart(
    data: List<BarEntry>,
    modifier: Modifier = Modifier,
    height: Dp = 108.dp,
) {
    if (data.isEmpty()) return
    val maxVal = data.maxOf { it.value } * 1.12f

    val accentGradient = listOf(Gold, GoldDeep)
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .height(height)
            .padding(top = 8.dp),
    ) {
        data.forEach { entry ->
            val pct = entry.value / maxVal
            val best = entry.value == data.maxOf { it.value }
            val barH = (height.value - 26f) * pct

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = entry.value.toString(),
                    color = if (best) Gold else TextLo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GeistMonoFamily,
                    modifier = Modifier.padding(bottom = 7.dp),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barH.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .drawBehind {
                            if (best) {
                                drawRect(
                                    brush = Brush.verticalGradient(accentGradient),
                                )
                            } else {
                                drawRect(color = Surf3)
                            }
                        },
                )

                Text(
                    text = entry.label,
                    color = TextLo,
                    fontSize = 10.5.sp,
                    fontFamily = GeistMonoFamily,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }
    }
}

~~~


## app\src\main\java\com\example\navisarthi\ui\components\CircularDash.kt
~~~kotlin
package com.example.navisarthi.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.navisarthi.ui.theme.*
import kotlin.math.*

/**
 * Circular Tripper Dash viewport — the signature element.
 * Mirrors the Royal Enfield Tripper round TFT display.
 *
 * @param size      Diameter of the circle
 * @param pan       Map pan offset (±46 units)
 * @param zoom      Map zoom scale (0.7–2.4)
 * @param distance  Next turn distance string
 * @param street    Current street name
 * @param live      Show live ring pulse (connected state)
 * @param compact   Hide distance/street labels (for small embeds)
 */
@Composable
fun CircularDash(
    size: Dp,
    pan: Offset = Offset.Zero,
    zoom: Float = 1f,
    distance: String = "400 m",
    street: String = "NH-707, Shimla",
    live: Boolean = true,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Live ring pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "live-ring")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 2.0f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "ring-scale",
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "ring-alpha",
    )

    val accent = Gold
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            // Outer bezel rings via border layering
            .border(2.dp, Color(0xFF232729), CircleShape)
            .padding(2.dp)
            .border(6.dp, Color(0xFF0D0F10), CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF14201F),
                        0.7f to Color(0xFF0C1413),
                        1f to Color(0xFF070B0B),
                    )
                )
            ),
    ) {
        // Map layer with pan/zoom
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = pan.x
                    translationY = pan.y
                    scaleX = zoom
                    scaleY = zoom
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
        ) {
            drawMapContents(accent)
        }

        // Tick ring overlay
        Canvas(Modifier.fillMaxSize()) {
            drawTickRing(accent)
        }

        // Live ring pulse (behind the rider marker)
        if (live) {
            Canvas(Modifier.fillMaxSize()) {
                // this.size is DrawScope.size (px)
                drawCircle(
                    color = accent.copy(alpha = ringAlpha * 0.5f),
                    radius = this.size.minDimension * 0.12f * ringScale,
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        // Rider marker (centre, fixed) — 26dp canvas
        Canvas(Modifier.size(26.dp)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val r9 = 9.dp.toPx()
            drawCircle(color = Color(0xFF0C1413), radius = r9, center = Offset(cx, cy))
            drawCircle(color = accent, radius = r9, center = Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
            val path = Path().apply {
                moveTo(cx, 2.dp.toPx())
                lineTo(this@Canvas.size.width - 2.dp.toPx(), this@Canvas.size.height - 2.dp.toPx())
                lineTo(cx, this@Canvas.size.height - 5.dp.toPx())
                lineTo(2.dp.toPx(), this@Canvas.size.height - 2.dp.toPx())
                close()
            }
            drawPath(path, accent)
        }

        // N compass
        Text(
            "N",
            color = accent,
            fontFamily = GeistMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.05f).sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = (size.value * 0.085f).dp),
        )

        // Non-compact overlays: next-turn banner + street label
        if (!compact) {
            // Next-turn banner
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (size.value * 0.17f).dp)
                    .clip(CircleShape)
                    .background(Color(0xD00C0C0C))
                    .border(1.dp, GoldTint2, CircleShape)
                    .padding(horizontal = 13.dp, vertical = 5.dp),
            ) {
                Text(
                    distance,
                    color = TextHi,
                    fontFamily = GeistMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.062f).sp,
                )
            }

            // Street label at bottom
            Text(
                street,
                color = TextHi,
                fontFamily = GeistFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.052f).sp,
                letterSpacing = (-0.1).sp,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (size.value * 0.13f).dp)
                    .widthIn(max = size * 0.74f),
            )
        }

        // Glass highlight
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 0.07f),
                        0.42f to Color.Transparent,
                    ),
                    center = Offset(size.toPx() * 0.3f, size.toPx() * 0.08f),
                    radius = size.toPx() * 0.6f,
                ),
            )
        }
    }
}

private fun DrawScope.drawMapContents(accent: Color) {
    val w = size.width
    val h = size.height

    // Terrain fill
    val terrainPath = Path().apply {
        moveTo(0f, h * 0.75f)
        cubicTo(w * 0.2f, h * 0.675f, w * 0.35f, h * 0.825f, w * 0.55f, h * 0.75f)
        cubicTo(w * 0.75f, h * 0.69f, w * 0.875f, h * 0.79f, w, h * 0.75f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(terrainPath, Color(0xFF101B1A))

    // Side roads
    val roadPaint = Paint().apply {
        color = Color(0xFF1D2A2A)
        strokeWidth = 5f
        strokeCap = StrokeCap.Round
        style = PaintingStyle.Stroke
    }
    drawContext.canvas.drawPath(Path().apply {
        moveTo(w * 0.1f, h * 0.2f)
        cubicTo(w * 0.3f, h * 0.35f, w * 0.3f, h * 0.6f, w * 0.15f, h * 0.85f)
    }, roadPaint)
    drawContext.canvas.drawPath(Path().apply {
        moveTo(w * 0.9f, h * 0.15f)
        cubicTo(w * 0.75f, h * 0.35f, w * 0.825f, h * 0.6f, w * 0.75f, h * 0.9f)
    }, roadPaint)
    drawContext.canvas.drawPath(Path().apply {
        moveTo(w * 0.05f, h * 0.55f)
        lineTo(w * 0.4f, h * 0.5f)
        lineTo(w * 0.75f, h * 0.56f)
        lineTo(w, h * 0.5f)
    }, roadPaint)

    // Active route (gold)
    val routePaint = Paint().apply {
        color = accent
        strokeWidth = 7f
        strokeCap = StrokeCap.Round
        strokeJoin = StrokeJoin.Round
        style = PaintingStyle.Stroke
    }
    val routePath = Path().apply {
        moveTo(w * 0.5f, h * 0.875f)
        cubicTo(w * 0.5f, h * 0.75f, w * 0.35f, h * 0.7f, w * 0.36f, h * 0.55f)
        cubicTo(w * 0.37f, h * 0.41f, w * 0.55f, h * 0.39f, w * 0.56f, h * 0.26f)
        cubicTo(w * 0.565f, h * 0.17f, w * 0.5f, h * 0.13f, w * 0.5f, h * 0.06f)
    }
    drawContext.canvas.drawPath(routePath, routePaint)

    // Dashed white overlay
    drawContext.canvas.drawPath(routePath, Paint().apply {
        color = Color(0x80FFF5DD)
        strokeWidth = 1.6f
        strokeCap = StrokeCap.Round
        style = PaintingStyle.Stroke
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 7f))
    })
}

private fun DrawScope.drawTickRing(accent: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    for (i in 0 until 60) {
        val angle = (i.toFloat() / 60f) * 2f * PI.toFloat() - PI.toFloat() / 2f
        val major = i % 5 == 0
        val r1 = (if (major) 0.9f else 0.93f) * (size.width / 2f)
        val r2 = 0.96f * (size.width / 2f)
        drawLine(
            color = if (major) accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.13f),
            start = Offset(cx + cos(angle) * r1, cy + sin(angle) * r1),
            end   = Offset(cx + cos(angle) * r2, cy + sin(angle) * r2),
            strokeWidth = if (major) 1.4f else 0.8f,
            cap = StrokeCap.Butt,
        )
    }
}

~~~


## app\src\main\java\com\example\navisarthi\ui\components\Joystick.kt
~~~kotlin
package com.example.navisarthi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.navisarthi.ui.theme.*
import kotlin.math.*

/**
 * Draggable joystick pad.
 * onMove receives normalized X/Y in [-1, 1]. Zero when released.
 */
@Composable
fun Joystick(
    size: Dp = 132.dp,
    onMove: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val maxPx = remember(size) { size.value / 2f - 24f }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colorStops = arrayOf(0f to Surf2, 1f to Surf1),
                )
            )
            .border(1.dp, Line2, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val halfPx = size.toPx() / 2f
                        var dx = offset.x - halfPx
                        var dy = offset.y - halfPx
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist > maxPx) {
                            dx = (dx / dist) * maxPx
                            dy = (dy / dist) * maxPx
                        }
                        knobOffset = Offset(dx, dy)
                        onMove(Offset(dx / maxPx, dy / maxPx))
                    },
                    onDrag = { change, _ ->
                        val halfPx = size.toPx() / 2f
                        var dx = change.position.x - halfPx
                        var dy = change.position.y - halfPx
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist > maxPx) {
                            dx = (dx / dist) * maxPx
                            dy = (dy / dist) * maxPx
                        }
                        knobOffset = Offset(dx, dy)
                        onMove(Offset(dx / maxPx, dy / maxPx))
                    },
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        onMove(Offset.Zero)
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        onMove(Offset.Zero)
                    },
                )
            },
    ) {
        // Guide cross-hairs
        Canvas(Modifier.fillMaxSize()) {
            val s = size.toPx()
            val guides = listOf(
                Offset(s * 0.5f, s * 0.14f) to Offset(s * 0.5f, s * 0.32f),
                Offset(s * 0.5f, s * 0.86f) to Offset(s * 0.5f, s * 0.68f),
                Offset(s * 0.14f, s * 0.5f) to Offset(s * 0.32f, s * 0.5f),
                Offset(s * 0.86f, s * 0.5f) to Offset(s * 0.68f, s * 0.5f),
            )
            guides.forEach { (start, end) ->
                drawLine(Line3, start, end, strokeWidth = 2f, cap = StrokeCap.Round)
            }
        }

        val knobColors = listOf(GoldBright, GoldDeep)
        // Knob
        Canvas(
            Modifier
                .size(44.dp)
                .offset { androidx.compose.ui.unit.IntOffset(knobOffset.x.toInt(), knobOffset.y.toInt()) }
        ) {
            val r = 22.dp.toPx()
            drawCircle(
                brush = Brush.linearGradient(
                    knobColors,
                    start = Offset(r * 0.4f, r * 0.4f),
                    end = Offset(r * 1.6f, r * 1.6f),
                ),
                radius = r,
            )
            // Shadow highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = r,
                style = Stroke(1.dp.toPx()),
            )
            // Dimple
            drawCircle(Color(0xFF1A1402).copy(alpha = 0.5f), radius = 6.dp.toPx())
        }
    }
}

~~~


## app\src\main\java\com\example\navisarthi\ui\components\NaviSarthiComponents.kt
~~~kotlin
package com.example.navisarthi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.navisarthi.ui.theme.*

// ---- Shape constants ----
val CardShape    = RoundedCornerShape(18.dp)
val BtnShape     = RoundedCornerShape(14.dp)
val InputShape   = RoundedCornerShape(12.dp)
val ChipShape    = CircleShape
val IconBtnShape = RoundedCornerShape(14.dp)

// ---- Card ----
@Composable
fun NaviSarthiCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    padding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val border = if (glow)
        Modifier.border(1.dp, GoldTint2, CardShape)
    else
        Modifier.border(1.dp, Line, CardShape)

    Column(
        modifier = modifier
            .clip(CardShape)
            .background(Surf1)
            .then(border)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

// ---- Buttons ----
enum class BtnVariant { Primary, Secondary, Ghost, Danger }
enum class BtnSize { Lg, Md, Sm }

@Composable
fun NaviSarthiBtn(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    variant: BtnVariant = BtnVariant.Primary,
    size: BtnSize = BtnSize.Md,
    enabled: Boolean = true,
) {
    val (bgColor, fgColor, borderColor, hasShadow) = when (variant) {
        BtnVariant.Primary   -> listOf(Gold, OnGold, Color.Transparent, true)
        BtnVariant.Secondary -> listOf(Surf2, TextHi, Line2, false)
        BtnVariant.Ghost     -> listOf(Color.Transparent, TextMid, Line2, false)
        BtnVariant.Danger    -> listOf(Color.Transparent, Alert, Alert.copy(alpha = 0.4f), false)
    }
    val height = when (size) { BtnSize.Lg -> 58.dp; BtnSize.Md -> 50.dp; BtnSize.Sm -> 40.dp }
    val fontSize = when (size) { BtnSize.Lg -> 16.sp; BtnSize.Md -> 15.sp; BtnSize.Sm -> 13.5.sp }
    val iconSize = when (size) { BtnSize.Sm -> 17.dp; else -> 20.dp }

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(height)
            .clip(BtnShape)
            .background(bgColor as Color)
            .border(1.dp, borderColor as Color, BtnShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fgColor as Color,
                modifier = Modifier.size(iconSize),
            )
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = label,
            color = fgColor as Color,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GeistFamily,
            letterSpacing = (-0.15).sp,
            maxLines = 1,
        )
    }
}

// ---- Icon button (square rounded) ----
@Composable
fun NaviSarthiIconBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    active: Boolean = false,
    tint: Color? = null,
) {
    val bg = if (active) GoldTint else Surf2
    val bd = if (active) GoldTint2 else Line2
    val iconTint = tint ?: if (active) Gold else TextMid

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(IconBtnShape)
            .background(bg)
            .border(1.dp, bd, IconBtnShape)
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
    }
}

// ---- Chip ----
enum class ChipTone { Gold, Warn, Alert, Off, Neutral }

@Composable
fun NaviSarthiChip(
    label: String,
    tone: ChipTone = ChipTone.Neutral,
    dot: Boolean = false,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val (bg, fg, bd) = when (tone) {
        ChipTone.Gold    -> Triple(GoldTint, Gold, GoldTint2)
        ChipTone.Warn    -> Triple(Warn.copy(alpha = 0.13f), Warn, Warn.copy(alpha = 0.3f))
        ChipTone.Alert   -> Triple(Alert.copy(alpha = 0.13f), Alert, Alert.copy(alpha = 0.32f))
        ChipTone.Off     -> Triple(Surf1, TextLo, Line)
        ChipTone.Neutral -> Triple(Surf2, TextMid, Line2)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = modifier
            .height(30.dp)
            .clip(ChipShape)
            .background(bg)
            .border(1.dp, bd, ChipShape)
            .padding(horizontal = 12.dp),
    ) {
        if (dot) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(fg))
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        }
        Text(
            label, color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
            fontFamily = GeistFamily, maxLines = 1,
        )
    }
}

// ---- Toggle ----
@Composable
fun NaviSarthiToggle(on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val track = if (on) Gold else Surf3
    val bd    = if (on) Gold else Line3
    val knob  = if (on) OnGold else TextMid
    val knobOffset = if (on) 22.dp else 2.5.dp

    Box(
        modifier = Modifier
            .width(50.dp).height(30.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, bd, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) { onChange(!on) },
    ) {
        Box(
            Modifier
                .padding(start = knobOffset, top = 2.5.dp)
                .size(23.dp)
                .clip(CircleShape)
                .background(knob)
        )
    }
}

// ---- Segmented control ----
@Composable
fun NaviSarthiSegmented(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surf1)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { opt ->
            val active = opt == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) Surf3 else Color.Transparent)
                    .clickable { onSelect(opt) },
            ) {
                Text(
                    opt, color = if (active) TextHi else TextLo,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = GeistFamily,
                )
            }
        }
    }
}

// ---- Divider ----
@Composable
fun NaviSarthiDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Line))
}

// ---- Eyebrow label ----
/** Consistent hierarchy for groups of controls on a scrolling screen. */
@Composable
fun ControlSection(title: String, modifier: Modifier = Modifier) {
    Eyebrow(title, modifier.padding(top = 22.dp, bottom = 8.dp))
}

@Composable
fun ControlHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(top = 6.dp),
        color = TextLo,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = TextLo,
        fontSize = 11.sp,
        letterSpacing = 0.16.sp,
        fontFamily = GeistMonoFamily,
        fontWeight = FontWeight.Normal,
        modifier = modifier,
    )
}

// ---- List row ----
enum class IconTone { Neutral, Gold, Warn, Alert }

@Composable
fun NaviSarthiRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTone: IconTone = IconTone.Neutral,
    sub: String? = null,
    right: String? = null,
    rightSub: String? = null,
    accentRight: Boolean = false,
    trailingIcon: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val (iconBg, iconFg) = when (iconTone) {
        IconTone.Gold   -> GoldTint to Gold
        IconTone.Alert  -> Alert.copy(alpha = 0.13f) to Alert
        IconTone.Warn   -> Warn.copy(alpha = 0.13f) to Warn
        IconTone.Neutral -> Surf2 to TextMid
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp, horizontal = 4.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (icon != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg)
                    .border(1.dp, Line, RoundedCornerShape(12.dp)),
            ) {
                Icon(icon, contentDescription = null, tint = iconFg, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title, color = TextHi, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = GeistFamily, letterSpacing = (-0.15).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (sub != null) {
                Text(sub, color = TextLo, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (right != null || rightSub != null) {
            Column(horizontalAlignment = Alignment.End) {
                if (right != null) {
                    Text(
                        right,
                        color = if (accentRight) Gold else TextHi,
                        fontSize = 14.5.sp, fontWeight = FontWeight.Medium,
                        fontFamily = GeistMonoFamily,
                    )
                }
                if (rightSub != null) {
                    Text(rightSub, color = TextLo, fontSize = 11.5.sp)
                }
            }
        }
        if (trailingIcon || (onClick != null && right == null)) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null, tint = TextLo, modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ---- Screen scaffold (scrollable body) ----
@Composable
fun ScreenHeader(
    modifier: Modifier = Modifier,
    title: String? = null,
    eyebrow: String? = null,
    hint: String? = null,
    wordmark: Boolean = false,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp)
            .defaultMinSize(minHeight = 44.dp),
    ) {
        if (onBack != null) {
            NaviSarthiIconBtn(
                icon = Icons.Outlined.ChevronLeft,
                onClick = onBack,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Box(Modifier.weight(1f)) {
            if (wordmark) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(com.example.navisarthi.R.drawable.navisarthi_logo),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "NaviSarthi",
                        color = TextHi, fontFamily = GeistMonoFamily,
                        fontWeight = FontWeight.Bold, fontSize = 21.sp,
                        letterSpacing = 0.14.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Gold))
                }
            } else {
                Column {
                    if (eyebrow != null) Eyebrow(eyebrow, Modifier.padding(bottom = 3.dp))
                    if (title != null) {
                        Text(
                            title, color = TextHi, fontSize = 23.sp,
                            fontWeight = FontWeight.Bold, fontFamily = GeistFamily,
                            letterSpacing = (-0.46).sp,
                        )
                    }
                    // One-line "what this screen is for" guidance, shown right under the title.
                    if (hint != null) {
                        Text(
                            hint, color = TextLo, fontSize = 12.sp, fontFamily = GeistFamily,
                            lineHeight = 15.sp, modifier = Modifier.padding(top = 3.dp, end = 8.dp),
                        )
                    }
                }
            }
        }
        trailing?.invoke()
    }
}

~~~


## app\src\main\java\com\example\navisarthi\ui\components\NaviSarthiMap.kt
~~~kotlin
package com.example.navisarthi.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.navisarthi.dash.nav.GeoPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.LineManager
import org.maplibre.android.plugins.annotation.LineOptions
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions

// Free, keyless, redistributable vector basemap (look-first, per the distribution decision).
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val FOLLOW_ZOOM = 15.5
private const val NAV_ZOOM = 17.5
private const val NAV_TILT = 45.0
private const val RIDER_ICON = "rider-chevron"
private const val DEST_ICON = "dest-pin"

/**
 * In-app phone map (MapLibre + OpenFreeMap). Keyless and redistributable — no Google
 * Maps SDK / API key. The physical dash still uses the off-screen power-efficient renderer.
 *
 * Modes: [fitRoute] frames the whole route; [navMode] tilts/zooms/rotates to heading with a
 * rider chevron; default follows the rider north-up.
 */
@Composable
fun NaviSarthiMap(
    riderLat: Double?,
    riderLng: Double?,
    dest: Pair<Double, Double>?,
    routePoints: List<GeoPoint>,
    hasLocationPermission: Boolean,
    modifier: Modifier = Modifier,
    fitRoute: Boolean = false,
    navMode: Boolean = false,
    riderBearing: Float = 0f,
) {
    val context = LocalContext.current
    remember { MapLibre.getInstance(context) }
    // textureMode → render into a TextureView, not a SurfaceView. A SurfaceView is a
    // separate window that doesn't clip/animate with Compose navigation, so the old map
    // lingers on screen during the transition; a TextureView composes normally.
    // onCreate(null) runs HERE, exactly once at construction — NOT via the lifecycle observer.
    // Routing it through the observer let onStart/onResume dispatch before onCreate, and pairing
    // an observer ON_DESTROY with the onDispose teardown destroyed the native MapView TWICE — a
    // use-after-free that SIGSEGVs when navigating quickly between map screens (Dash↔Route↔Home).
    val mapView = remember {
        MapView(context, MapLibreMapOptions().textureMode(true)).apply { onCreate(null) }
    }
    // Set the instant we start tearing down (BEFORE onDestroy). The style loads over the NETWORK, so
    // getMapAsync/setStyle callbacks can fire LATE — after the user has already navigated away and the
    // native MapView was freed. Touching the map/style then derefs freed memory → SIGSEGV in
    // libmaplibre.so (the #1 "crashes while switching pages" crash). Every async callback bails on this.
    val destroyed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var lineMgr by remember { mutableStateOf<LineManager?>(null) }
    var symbolMgr by remember { mutableStateOf<SymbolManager?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    // Bind the MapView to the composition lifecycle. Only start/resume/pause/stop go through the
    // observer; create happened at construction and destroy happens ONCE in onDispose — never via
    // the observer, so the native MapView is freed exactly once.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            destroyed.set(true)   // block any in-flight async callback from touching the freed map
            lifecycleOwner.lifecycle.removeObserver(obs)
            // Do NOT call lineMgr/symbolMgr.onDestroy() here. The symbolicated tombstone shows THAT is
            // the page-switch crash: AnnotationManager.onDestroy() → DraggableAnnotationController →
            // getLayerId → native Layer::getId, which SIGSEGVs when the map's native peer is already
            // gone during fast navigation (and runCatching can't catch a native crash). MapView
            // .onDestroy() frees the style and every layer/manager on it, so the managers are torn
            // down safely for us — manually destroying them first is both redundant and crash-prone.
            runCatching { mapView.onStop() }
            runCatching { mapView.onDestroy() }
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            if (destroyed.get()) return@getMapAsync   // navigated away before the map was ready
            map = m
            m.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                isAttributionEnabled = true   // OSM/OpenFreeMap attribution (keep — licensing)
                isLogoEnabled = false
            }
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                // The style loads over the network — by the time it lands the screen may be gone.
                // Creating Line/SymbolManager on a destroyed MapView is the native crash; bail first.
                if (destroyed.get()) return@setStyle
                style.addImage(RIDER_ICON, chevronBitmap())
                style.addImage(DEST_ICON, destPinBitmap())
                lineMgr = LineManager(mapView, m, style)
                symbolMgr = SymbolManager(mapView, m, style).apply {
                    iconAllowOverlap = true; iconIgnorePlacement = true
                }
                styleReady = true
            }
        }
    }

    // Redraw route + markers whenever the data or mode changes.
    LaunchedEffect(styleReady, routePoints.size, dest, riderLat, riderLng, riderBearing, navMode) {
        if (destroyed.get()) return@LaunchedEffect
        val lm = lineMgr ?: return@LaunchedEffect
        val sm = symbolMgr ?: return@LaunchedEffect
        lm.deleteAll(); sm.deleteAll()
        if (routePoints.size >= 2) {
            lm.create(
                LineOptions().withLatLngs(routePoints.map { LatLng(it.lat, it.lng) })
                    .withLineColor("#4285F4").withLineWidth(5.5f)
            )
        }
        dest?.let { sm.create(SymbolOptions().withLatLng(LatLng(it.first, it.second)).withIconImage(DEST_ICON).withIconSize(1.1f)) }
        if (navMode && riderLat != null && riderLng != null) {
            sm.create(
                SymbolOptions().withLatLng(LatLng(riderLat, riderLng))
                    .withIconImage(RIDER_ICON).withIconRotate(riderBearing).withIconSize(1.0f)
            )
        }
    }

    // Camera control.
    LaunchedEffect(styleReady, riderLat, riderLng, riderBearing, navMode, fitRoute, routePoints.size) {
        if (destroyed.get()) return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        when {
            fitRoute && routePoints.size >= 2 -> {
                val b = LatLngBounds.Builder()
                routePoints.forEach { b.include(LatLng(it.lat, it.lng)) }
                runCatching { m.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 110)) }
            }
            riderLat != null && riderLng != null -> {
                val target = LatLng(riderLat, riderLng)
                val pos = if (navMode)
                    CameraPosition.Builder().target(target).zoom(NAV_ZOOM).tilt(NAV_TILT).bearing(riderBearing.toDouble()).build()
                else
                    CameraPosition.Builder().target(target).zoom(FOLLOW_ZOOM).tilt(0.0).bearing(0.0).build()
                runCatching { m.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 600) }
            }
            dest != null -> runCatching {
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(dest.first, dest.second), 13.0))
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Google-style blue chevron-in-a-circle, pointing "up" (rotated to heading by the symbol). */
private fun chevronBitmap(): Bitmap {
    val s = 84
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = android.graphics.Color.WHITE
    c.drawCircle(s / 2f, s / 2f, s * 0.34f, p)
    p.color = android.graphics.Color.rgb(66, 133, 244)
    c.drawCircle(s / 2f, s / 2f, s * 0.30f, p)
    p.color = android.graphics.Color.WHITE
    val cx = s / 2f
    c.drawPath(Path().apply {
        moveTo(cx, s * 0.24f); lineTo(s * 0.72f, s * 0.70f); lineTo(cx, s * 0.58f); lineTo(s * 0.28f, s * 0.70f); close()
    }, p)
    return bmp
}

/** Simple red destination pin (white ring + red fill). */
private fun destPinBitmap(): Bitmap {
    val s = 72
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = android.graphics.Color.WHITE; c.drawCircle(s / 2f, s / 2f, s * 0.30f, p)
    p.color = android.graphics.Color.rgb(234, 67, 53); c.drawCircle(s / 2f, s / 2f, s * 0.24f, p)
    p.color = android.graphics.Color.WHITE; c.drawCircle(s / 2f, s / 2f, s * 0.09f, p)
    return bmp
}

~~~


## app\src\main\java\com\example\navisarthi\ui\components\UpdatePrompt.kt
~~~kotlin
package com.example.navisarthi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.navisarthi.data.UpdateChecker
import com.example.navisarthi.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drop-in overlay: checks GitHub Releases once when the app opens and, if a newer version is
 * out, shows a dialog with the changelog + a one-tap Update (download → system installer).
 * Renders nothing until/unless an update is found. Safe to place at the app root.
 */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var release by remember { mutableStateOf<UpdateChecker.ReleaseInfo?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var failed by remember { mutableStateOf(false) }

    // One background check per app open. Offer by APK checksum when the release publishes one,
    // else by version (see UpdateChecker.shouldOffer) — so the prompt reflects the actual installed
    // build, not just the version string.
    LaunchedEffect(Unit) {
        val latest = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest(context) }
        if (latest != null && UpdateChecker.shouldOffer(context, latest)) {
            release = latest
        }
    }

    val r = release ?: return
    if (dismissed) return

    AlertDialog(
        onDismissRequest = { if (!downloading) dismissed = true },
        containerColor = Surf1,
        titleContentColor = TextHi,
        textContentColor = TextMid,
        title = {
            Text(
                "Update available",
                color = TextHi,
                fontWeight = FontWeight.Bold,
                fontFamily = GeistFamily,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "NaviSarthi ${r.versionName}",
                    color = Gold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GeistFamily,
                )
                if (r.notes.isNotBlank()) {
                    Column(
                        Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            prettifyNotes(r.notes),
                            color = TextMid,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = GeistFamily,
                        )
                    }
                }
                if (downloading) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.padding(top = 2.dp),
                        color = Gold,
                        trackColor = Line,
                    )
                    Text(
                        if (progress > 0f) "Downloading… ${(progress * 100).toInt()}%" else "Downloading…",
                        color = TextLo,
                        fontSize = 12.sp,
                        fontFamily = GeistFamily,
                    )
                }
                if (failed) {
                    Text(
                        "Download failed — opening the Releases page instead.",
                        color = Warn,
                        fontSize = 12.sp,
                        fontFamily = GeistFamily,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !downloading,
                onClick = {
                    scope.launch {
                        downloading = true
                        failed = false
                        progress = 0f
                        val file = withContext(Dispatchers.IO) {
                            UpdateChecker.download(context, r.apkUrl) { p -> progress = p }
                        }
                        downloading = false
                        if (file != null) {
                            // Returns false if it had to send the user to the unknown-sources
                            // setting first; keep the dialog up so they can tap Update again.
                            if (UpdateChecker.install(context, file)) dismissed = true
                        } else {
                            failed = true
                            UpdateChecker.openReleasesPage(context)
                        }
                    }
                },
            ) {
                Text(
                    if (downloading) "Downloading…" else "Update",
                    color = if (downloading) TextLo else Gold,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GeistFamily,
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !downloading,
                onClick = { dismissed = true },
            ) {
                Text("Later", color = TextLo, fontFamily = GeistFamily)
            }
        },
    )
}

/** Light touch-up of the GitHub markdown body so it reads cleanly as plain text. */
private fun prettifyNotes(md: String): String =
    md.lineSequence()
        .joinToString("\n") { line ->
            line.trimEnd()
                .removePrefix("### ").removePrefix("## ").removePrefix("# ")
                .replace(Regex("^\\s*[-*] "), "• ")
                .replace("**", "")
        }
        .trim()

~~~


## app\src\main\java\com\example\navisarthi\ui\components\VehiclePicker.kt
~~~kotlin
package com.example.navisarthi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.navisarthi.ui.theme.VehicleModel

@Composable
internal fun VehiclePicker(
    current: VehicleModel?,
    onSelect: (VehicleModel) -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    var choice by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        title = { Text("Choose your vehicle") },
        text = {
            Column(Modifier.selectableGroup()) {
                ControlHint("Your vehicle sets the app accent. You can change it in Settings.")
                Spacer(Modifier.height(10.dp))
                VehicleModel.entries.forEach { vehicle ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = choice == vehicle,
                            onClick = { choice = vehicle },
                            role = Role.RadioButton,
                        ).heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == vehicle, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(vehicle.label, Modifier.weight(1f))
                        Box(Modifier.size(14.dp).background(vehicle.accent, CircleShape))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = choice != null, onClick = { choice?.let(onSelect) }) {
                Text(if (current == null) "Continue" else "Save")
            }
        },
        dismissButton = {
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

~~~

