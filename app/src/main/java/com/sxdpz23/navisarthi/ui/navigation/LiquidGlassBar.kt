package com.sxdpz23.navisarthi.ui.navigation

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sxdpz23.navisarthi.ui.theme.*
import com.sxdpz23.navisarthi.ui.components.GlassEdge
import com.sxdpz23.navisarthi.ui.components.GlassWash
import com.sxdpz23.navisarthi.ui.components.GlassUnderlay
import com.sxdpz23.navisarthi.navigation.ui.map.NavGlassSource
import kotlin.math.roundToInt

internal data class GlassTab(val route: String, val icon: ImageVector, val label: String)

// Only the background goes through this shader. Labels and hit targets remain sharp.

@Composable
internal fun LiquidGlassBar(
    tabs: List<GlassTab>, selectedRoute: String, backdrop: GraphicsLayer,
    backdropOrigin: Offset, compact: Boolean,
    modifier: Modifier = Modifier, mapSource: NavGlassSource? = null, onSelect: (String) -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shader = remember { RuntimeShader(com.sxdpz23.navisarthi.ui.components.LIQUID_GLASS_SHADER) }
    val interactions = tabs.map { tab -> key(tab.route) { remember { MutableInteractionSource() } } }
    val pressed = interactions.map { it.collectIsPressedAsState().value }.any { it }
    val pressure by animateFloatAsState(if (pressed) 1f else 0f, label = "tabPressure")
    // One progress value keeps the shell, selection and label space in proportion throughout collapse.
    val animatedExpansion by animateFloatAsState(if (compact) 0f else 1f, label = "glassExpansion")
    val expansion = animatedExpansion.coerceIn(0f, 1f)
    val height = 60.dp + 12.dp * expansion
    val selected = tabs.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    BoxWithConstraints(modifier.widthIn(max = 480.dp).fillMaxWidth().height(height)
        .shadow(16.dp, CircleShape).clip(CircleShape)
        .onGloballyPositioned { origin = it.positionInRoot() }
        .border(1.dp, GlassEdge, CircleShape)
    ) {
        Canvas(Modifier.matchParentSize().graphicsLayer {
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("dark", 1f)
            shader.setFloatUniform("radius", size.height / 2f)
            shader.setFloatUniform("thickness", 0f)
            shader.setFloatUniform("pressure", pressure)
            renderEffect = RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(shader, "content"),
                RenderEffect.createBlurEffect(8f, 8f, Shader.TileMode.CLAMP),
            ).asComposeRenderEffect()
        }) {
            val sourceOrigin = mapSource?.rootOrigin ?: backdropOrigin
            translate(sourceOrigin.x - origin.x, sourceOrigin.y - origin.y) { drawLayer(mapSource?.layer ?: backdrop) }
        }
        Box(Modifier.matchParentSize().background(GlassUnderlay))
        Box(Modifier.matchParentSize().background(GlassWash))
        val slot = (maxWidth - 12.dp) / tabs.size
        // Compose's animation clock honours the system animator duration scale.
        val left by animateDpAsState(slot * selected, spring(dampingRatio = .88f, stiffness = 480f), label = "glassSelection")
        val selectionWidth = slot
        Box(Modifier.fillMaxSize().padding(6.dp)) {
            // Keep the same full tab boundary when labels collapse.
            Box(Modifier.offset(x = left + (slot - selectionWidth) / 2).width(selectionWidth).fillMaxHeight()
                .clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0x32FFFFFF), Color(0x10FFFFFF))))
                .border(1.dp, Color(0x38FFFFFF), CircleShape))
            Row(Modifier.fillMaxSize().selectableGroup()) {
                tabs.forEachIndexed { index, tab ->
                    val active = tab.route == selectedRoute
                    val tint by animateColorAsState(if (active) Gold else TextHi.copy(alpha = .84f), label = "tabTint")
                    Column(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .semantics { contentDescription = tab.label }
                        .selectable(active, interactionSource = interactions[index],
                            indication = androidx.compose.foundation.LocalIndication.current,
                            role = Role.Tab, onClick = { onSelect(tab.route) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(4.dp * expansion))
                        Text(tab.label, color = tint.copy(alpha = expansion), fontFamily = GeistFamily, fontSize = 11.sp,
                            modifier = Modifier.clearAndSetSemantics {}.clipToBounds().layout { measurable, constraints ->
                                val label = measurable.measure(constraints)
                                layout(label.width, (label.height * expansion).roundToInt()) { label.placeRelative(0, 0) }
                            },
                            fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}
