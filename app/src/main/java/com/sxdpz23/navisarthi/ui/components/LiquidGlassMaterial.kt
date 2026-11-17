package com.sxdpz23.navisarthi.ui.components

internal const val LIQUID_GLASS_SHADER = """
uniform shader content;
uniform float2 resolution;
uniform float dark;
uniform float radius;
half4 main(float2 p) {
    float r = radius;
    float2 c = clamp(p, float2(r), max(float2(r), resolution-float2(r)));
    float2 delta = p-c;
    float edge = pow(clamp(1.0-(r-length(delta))/18.0, 0.0, 1.0), 1.65);
    float2 normal = delta / max(length(delta), .001);
    half4 color = content.eval(clamp(p + normal*edge*11.0, float2(0), resolution));
    float luma = dot(float3(color.rgb), float3(.2126,.7152,.0722));
    float tint = mix(.66, mix(.76,.91,smoothstep(.30,.85,luma)), dark);
    half3 base = mix(half3(.96,.97,.98), half3(.025,.03,.04), dark);
    return half4(mix(color.rgb, base, tint), 1);
}
"""

