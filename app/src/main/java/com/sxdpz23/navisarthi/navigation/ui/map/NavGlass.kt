package com.sxdpz23.navisarthi.navigation.ui.map

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.sxdpz23.navisarthi.navigation.ui.theme.isAppInDarkTheme

private class MapGlassBackdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}
private val LocalMapGlass = staticCompositionLocalOf<MapGlassBackdrop?> { null }

/** Records only the map, excluding every floating control, so glass never samples itself. */
@Composable
internal fun NavGlassHost(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { MapGlassBackdrop(layer) }
    CompositionLocalProvider(LocalMapGlass provides backdrop) { Box(modifier, content = content) }
}

internal fun Modifier.navGlassBackdrop(): Modifier = composed {
    val backdrop = LocalMapGlass.current
    val rootView = LocalView.current
    if (backdrop == null) this else onGloballyPositioned { backdrop.origin = it.positionInRoot() + screenOffset(rootView) }
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
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalMapGlass.current
    val rootView = LocalView.current
    val dark = darkTint || isAppInDarkTheme()
    val shader = remember { RuntimeShader(com.sxdpz23.navisarthi.ui.components.LIQUID_GLASS_SHADER) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.shadow(6.dp, shape).clip(shape)
        .onGloballyPositioned { origin = it.positionInRoot() + screenOffset(rootView) }
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(.55f), Color.White.copy(.12f))), shape)
    ) {
        if (backdrop != null) Canvas(Modifier.matchParentSize().graphicsLayer {
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("dark", if (dark) 1f else 0f)
            shader.setFloatUniform("radius", minOf(28.dp.toPx(), size.width / 2f, size.height / 2f))
            renderEffect = RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(shader, "content"),
                RenderEffect.createBlurEffect(8f, 8f, Shader.TileMode.CLAMP),
            ).asComposeRenderEffect()
        }) {
            translate(backdrop.origin.x - origin.x, backdrop.origin.y - origin.y) { drawLayer(backdrop.layer) }
        }
        Box(Modifier.matchParentSize().background(if (backdrop == null) MaterialTheme.colorScheme.surfaceContainerHigh
            else if (dark) Color(0x223F4855) else Color(0x26FFFFFF)))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x32F3F1EC), Color(0x193F4855), Color(0x30323A46)))))
        if (darkTint) MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(
            primary = MaterialTheme.colorScheme.primary,
            secondary = MaterialTheme.colorScheme.secondary,
        )) { content() } else content()
    }
}

@Composable
internal fun NavGlassChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    NavGlassSurface(modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Column(Modifier.then(if (docked && portrait) Modifier.padding(bottom = 96.dp) else Modifier), content = content)
    }
}

internal fun Modifier.navGlassPanel(landscape: Boolean): Modifier =
    if (landscape) this else padding(horizontal = 16.dp, vertical = 12.dp).widthIn(max = 480.dp)

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
