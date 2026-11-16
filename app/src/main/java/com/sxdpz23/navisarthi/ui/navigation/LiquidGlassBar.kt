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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sxdpz23.navisarthi.ui.theme.*

internal data class GlassTab(val route: String, val icon: ImageVector, val label: String)

// Only the background goes through this shader. Labels and hit targets remain sharp.
private const val GLASS_SHADER = """
uniform shader content;
uniform float2 resolution;
half4 main(float2 p) {
    float r = resolution.y * .5;
    float2 c = float2(clamp(p.x, r, resolution.x-r), r);
    float2 delta = p-c;
    float d = length(delta);
    float edge = pow(clamp(1.0-(r-d)/18.0, 0.0, 1.0), 1.65);
    float2 normal = delta / max(d, .001);
    half4 color = content.eval(clamp(p + normal*edge*11.0, float2(0), resolution));
    float luminance = dot(float3(color.rgb), float3(.2126,.7152,.0722));
    float tint = mix(.42, .88, smoothstep(.30,.85,luminance));
    return half4(mix(color.rgb, half3(.08,.09,.11), tint), color.a);
}
"""

@Composable
internal fun LiquidGlassBar(
    tabs: List<GlassTab>, selectedRoute: String, backdrop: GraphicsLayer,
    backdropOrigin: Offset, compact: Boolean,
    modifier: Modifier = Modifier, onSelect: (String) -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shader = remember { RuntimeShader(GLASS_SHADER) }
    val height by animateDpAsState(if (compact) 60.dp else 72.dp, label = "glassHeight")
    val labelAlpha by animateFloatAsState(if (compact) 0f else 1f, label = "glassLabels")
    val selected = tabs.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    BoxWithConstraints(modifier.widthIn(max = 480.dp).fillMaxWidth().height(height)
        .shadow(16.dp, CircleShape).clip(CircleShape)
        .onGloballyPositioned { origin = it.positionInRoot() }
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(.55f), Color.White.copy(.12f))), CircleShape)
    ) {
        Canvas(Modifier.matchParentSize().graphicsLayer {
            shader.setFloatUniform("resolution", size.width, size.height)
            renderEffect = RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(shader, "content"),
                RenderEffect.createBlurEffect(8f, 8f, Shader.TileMode.CLAMP),
            ).asComposeRenderEffect()
        }) {
            translate(backdropOrigin.x - origin.x, backdropOrigin.y - origin.y) { drawLayer(backdrop) }
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
            Color(0x32F3F1EC), Color(0x193F4855), Color(0x30323A46),
        ))))
        val slot = (maxWidth - 12.dp) / tabs.size
        // Compose's animation clock honours the system animator duration scale.
        val left by animateDpAsState(slot * selected, spring(dampingRatio = .88f, stiffness = 480f), label = "glassSelection")
        Box(Modifier.padding(6.dp).offset(x = left).width(slot).fillMaxHeight()
            .clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0x32FFFFFF), Color(0x10FFFFFF))))
            .border(1.dp, Color(0x38FFFFFF), CircleShape))
        Row(Modifier.fillMaxSize().padding(6.dp).selectableGroup()) {
            tabs.forEach { tab ->
                val active = tab.route == selectedRoute
                val tint by animateColorAsState(if (active) Gold else TextHi.copy(alpha = .84f), label = "tabTint")
                Column(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                    .selectable(active, role = Role.Tab, onClick = { onSelect(tab.route) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Text(tab.label, color = tint.copy(alpha = labelAlpha), fontFamily = GeistFamily, fontSize = 11.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}
