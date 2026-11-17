package com.sxdpz23.navisarthi.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Shared neutral lighting keeps controls and docked panels on the same material.
internal val GlassEdge = Brush.linearGradient(listOf(Color.White.copy(.42f), Color.White.copy(.10f)))
internal val GlassWash = Brush.verticalGradient(listOf(Color(0x20F3F1EC), Color(0x123F4855), Color(0x28323A46)))
internal val GlassUnderlay = Color(0x223F4855)

internal const val LIQUID_GLASS_SHADER = """
uniform shader content;
uniform float2 resolution;
uniform float dark;
uniform float radius;
uniform float thickness;
uniform float pressure;
half4 main(float2 p) {
    float r = radius;
    float2 c = clamp(p, float2(r), max(float2(r), resolution-float2(r)));
    float2 delta = p-c;
    float edge = pow(clamp(1.0-(r-length(delta))/18.0, 0.0, 1.0), 1.65);
    float2 normal = delta / max(length(delta), .001);
    half4 color = content.eval(clamp(p + normal*edge*mix(11.0,6.0,thickness)*(1.0+pressure*.12), float2(0), resolution));
    float luma = dot(float3(color.rgb), float3(.2126,.7152,.0722));
    float tint = mix(.66, mix(.79,.92,smoothstep(.30,.85,luma)), dark);
    tint = min(.94, tint + thickness*.035);
    half3 base = mix(half3(.96,.97,.98), half3(.025,.03,.04), dark);
    half3 result = mix(color.rgb, base, tint);
    return half4(result + half3(edge*pressure*.025), 1);
}
"""

