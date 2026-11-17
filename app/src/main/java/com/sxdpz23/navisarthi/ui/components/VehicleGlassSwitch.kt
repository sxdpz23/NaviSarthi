package com.sxdpz23.navisarthi.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sxdpz23.navisarthi.ui.theme.LocalVehicle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

/** Shared phone switch: one animation clock for travel, colour and elastic glass shape. */
@Composable
fun VehicleGlassSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val travel = remember { Animatable(if (checked) 1f else 0f) }
    val lens = remember { Animatable(0f) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(checked) {
        if (!initialized) { initialized = true; return@LaunchedEffect }
        val from = travel.value
        val initialLens = lens.value
        val target = if (checked) 1f else 0f
        // Lift, travel and settle overlap so the switch responds immediately.
        // Keyframes use Compose's duration scale, including instant reduced-motion updates.
        coroutineScope {
            launch { lens.animateTo(0f, keyframes {
                durationMillis = 360
                initialLens at 0 using FastOutSlowInEasing
                1f at 80 using FastOutSlowInEasing
                1f at 220 using FastOutSlowInEasing
                0f at 360
            }) }
            launch { travel.animateTo(target, keyframes {
                durationMillis = 360
                from at 0 using FastOutSlowInEasing
                target at 220
                target at 360
            }) }
        }
    }
    val pressure by animateFloatAsState(if (pressed && enabled) 1f else 0f,
        spring(dampingRatio = .8f, stiffness = 700f), label = "thumbPressure")
    val accent = LocalVehicle.current.accent
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val interaction = if (onCheckedChange != null) Modifier.toggleable(checked, interactions, indication = null,
        enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    else Modifier.semantics {
        role = Role.Switch
        toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
        if (!enabled) disabled()
    }
    Box(modifier.sizeIn(minWidth = 56.dp, minHeight = 48.dp).then(interaction), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(52.dp, 32.dp)) {
            val progress = travel.value.coerceIn(0f, 1f)
            val inflation = max(lens.value.coerceIn(0f, 1f), pressure * .45f)
            val alpha = if (enabled) 1f else .38f
            val track = lerp(Color(0xFF353A40), accent, progress).copy(alpha = alpha)
            val r = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(.12f * alpha), Color.Transparent, Color.Black.copy(.16f * alpha))), cornerRadius = r)
            drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(.32f * alpha), Color.White.copy(.07f * alpha))), cornerRadius = r, style = Stroke(1.dp.toPx()))
            val stretch = inflation * 18.dp.toPx()
            val thumbWidth = 24.dp.toPx() + stretch
            val thumbHeight = 24.dp.toPx() + inflation * 12.dp.toPx()
            val fraction = if (rtl) 1f - progress else progress
            val center = 16.dp.toPx() + (size.width - 32.dp.toPx()) * fraction
            val x = center - thumbWidth / 2
            val y = (size.height - thumbHeight) / 2
            val thumbSize = Size(thumbWidth, thumbHeight)
            val radius = CornerRadius(thumbHeight / 2)
            drawRoundRect(Color.Black.copy(.24f * alpha), Offset(x, y + 1.5.dp.toPx()), thumbSize, radius)
            val glass = lerp(Color.White, track.copy(alpha = 1f), inflation * .84f)
            drawRoundRect(Brush.linearGradient(listOf(glass.copy(alpha = .96f * alpha), glass.copy(alpha = .82f * alpha))), Offset(x,y), thumbSize, radius)
            drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha), Color.White.copy(.24f * alpha))), Offset(x,y), thumbSize, radius, style = Stroke((1f + inflation).dp.toPx()))
            drawArc(Color.White.copy(.85f * alpha * inflation), -85f, 145f, false, Offset(x + 2.dp.toPx(), y + 2.dp.toPx()), Size(thumbWidth - 4.dp.toPx(), thumbHeight - 4.dp.toPx()), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            drawRoundRect(Color.White.copy(.55f * alpha), Offset(x + 4.dp.toPx(), y + 2.dp.toPx()), Size(thumbWidth - 8.dp.toPx(), 2.dp.toPx()), CornerRadius(1.dp.toPx()))
        }
    }
}
