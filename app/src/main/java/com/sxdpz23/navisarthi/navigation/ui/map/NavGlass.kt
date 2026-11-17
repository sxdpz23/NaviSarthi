package com.sxdpz23.navisarthi.navigation.ui.map

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.sxdpz23.navisarthi.navigation.ui.theme.isAppInDarkTheme
import com.sxdpz23.navisarthi.ui.components.GlassEdge
import com.sxdpz23.navisarthi.ui.components.GlassWash
import com.sxdpz23.navisarthi.ui.components.GlassUnderlay

/** Root coordinates let the shell sample the map without sampling Nav's floating glass. */
internal data class NavGlassSource(val layer: GraphicsLayer, val rootOrigin: Offset)
internal val LocalNavGlassObserver = staticCompositionLocalOf<(NavGlassSource, Boolean) -> Unit> { { _, _ -> } }

private class MapGlassBackdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}
private val LocalMapGlass = staticCompositionLocalOf<MapGlassBackdrop?> { null }

/** Records only the map, excluding every floating control, so glass never samples itself. */
@Composable
internal fun NavGlassHost(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { MapGlassBackdrop(layer) }
    val observer by rememberUpdatedState(LocalNavGlassObserver.current)
    DisposableEffect(layer) {
        onDispose { observer(NavGlassSource(layer, Offset.Zero), false) }
    }
    CompositionLocalProvider(LocalMapGlass provides backdrop) { Box(modifier, content = content) }
}

internal fun Modifier.navGlassBackdrop(): Modifier = composed {
    val backdrop = LocalMapGlass.current
    val rootView = LocalView.current
    val observer = LocalNavGlassObserver.current
    if (backdrop == null) this else onGloballyPositioned {
        val rootOrigin = it.positionInRoot()
        backdrop.origin = rootOrigin + screenOffset(rootView)
        observer(NavGlassSource(backdrop.layer, rootOrigin), true)
    }
        .drawWithContent {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop.layer)
        }
}


/** Background-only blur/refraction; focus, labels and touch targets remain unfiltered. */
@Composable
internal fun NavGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    darkTint: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalMapGlass.current
    val rootView = LocalView.current
    val dark = darkTint || isAppInDarkTheme()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val pressed = interactionSource?.collectIsPressedAsState()?.value ?: false
    val pressure by animateFloatAsState(if (pressed) 1f else 0f, label = "glassPressure")
    val shader = remember { RuntimeShader(com.sxdpz23.navisarthi.ui.components.LIQUID_GLASS_SHADER) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.shadow(6.dp, shape).clip(shape)
        .onGloballyPositioned { origin = it.positionInRoot() + screenOffset(rootView) }
        .border(1.dp, GlassEdge, shape)
    ) {
        if (backdrop != null) Canvas(Modifier.matchParentSize().graphicsLayer {
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("dark", if (dark) 1f else 0f)
            val outline = shape.createOutline(size, layoutDirection, density)
            val radius = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
            val thickness = ((size.height / density.density - 96f) / 224f).coerceIn(0f, 1f)
            shader.setFloatUniform("radius", radius)
            shader.setFloatUniform("thickness", thickness)
            shader.setFloatUniform("pressure", pressure)
            val blur = 8f + thickness * 4f
            renderEffect = RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(shader, "content"),
                RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP),
            ).asComposeRenderEffect()
        }) {
            translate(backdrop.origin.x - origin.x, backdrop.origin.y - origin.y) { drawLayer(backdrop.layer) }
        }
        Box(Modifier.matchParentSize().background(if (backdrop == null) MaterialTheme.colorScheme.surfaceContainerHigh
            else if (dark) GlassUnderlay else Color(0x26FFFFFF)))
        Box(Modifier.matchParentSize().background(GlassWash))
        if (darkTint) MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(
            primary = MaterialTheme.colorScheme.primary,
            secondary = MaterialTheme.colorScheme.secondary,
        )) { content() } else content()
    }
}

@Composable
internal fun NavGlassChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, gap: androidx.compose.ui.unit.Dp = 8.dp, icon: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    NavGlassSurface(modifier.heightIn(min = 48.dp).clickable(interactionSource = interaction,
        indication = androidx.compose.foundation.LocalIndication.current,
        role = Role.Button, onClick = onClick), interactionSource = interaction) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap)) {
            icon()
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun screenOffset(view: android.view.View): Offset {
    val position = IntArray(2)
    view.getLocationOnScreen(position)
    return Offset(position[0].toFloat(), position[1].toFloat())
}

/** Column surface for existing route/navigation cards, preserving their layout contract. */
@Composable
internal fun NavGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    docked: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val portrait = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenHeightDp >= it.screenWidthDp }
    NavGlassSurface(modifier, if (docked) androidx.compose.foundation.shape.RoundedCornerShape(36.dp) else shape, darkTint = true) {
        Column(Modifier.then(if (docked && portrait) Modifier.padding(bottom = 104.dp) else Modifier), content = content)
    }
}

internal fun Modifier.navGlassPanel(landscape: Boolean): Modifier =
    if (landscape) this else padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp).widthIn(max = 480.dp)

@Composable
internal fun NavViewToggle() {
    val display by com.sxdpz23.navisarthi.dash.nav.NavDisplaySession.state.collectAsState()
    com.sxdpz23.navisarthi.navigation.ui.nav.NavFab(
        onClick = { com.sxdpz23.navisarthi.dash.nav.NavDisplaySession.setPerspective(!display.perspective) },
        modifier = Modifier.semantics {
            contentDescription = if (display.perspective) "Switch to top-down view" else "Switch to perspective view"
        },
    ) {
        Text(if (display.perspective) "POV" else "TDV", color = Color(0xFFF3F1EC), style = MaterialTheme.typography.labelLarge)
    }
}
