#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:fog.glsl>
#include <worldsfall:wf_noise.glsl>

// A translucent, slowly flowing teal sheet with a bright cyan crest where it meets the air, faint ripples
// and a soft glow beneath (§5.1 "Visual of the plane"). Seen from below it is darker and denser.
// ColorModulator = base tint (teal or calmed silver-blue) and overall alpha.
// TextureMat[0] = (time seconds, mode: 0 surface / 1 ghost line, motion -1..1 (falling/rising), brightness)

layout(location = 0) in vec3 worldPos;
layout(location = 1) in vec3 viewRel;
layout(location = 0) out vec4 fragColor;

void main() {
    float t = TextureMat[0].x;
    float mode = TextureMat[0].y;
    float motion = TextureMat[0].z;
    float brightness = TextureMat[0].w;
    vec2 p = worldPos.xz;
    vec3 tint = ColorModulator.rgb;
    float dist = length(viewRel);

    if (mode > 0.5) {
        // ghost line: a faint dashed band where the tide was a moment ago
        float dash = step(0.5, fract((p.x + p.y) * 0.125));
        float edge = 1.0 - smoothstep(0.0, 1.0, abs(fract(p.x * 0.0625) - 0.5) * 2.0);
        float a = dash * 0.35 * ColorModulator.a * (1.0 - smoothstep(48.0, 192.0, dist));
        fragColor = vec4(mix(tint, vec3(0.72, 1.0, 0.97), 0.6), a);
        return;
    }

    // flowing layers of fBm, advected in two directions
    float flowA = wf_fbm(vec3(p * 0.035 + vec2(t * 0.020, t * 0.012), t * 0.03), 4);
    float flowB = wf_fbm(vec3(p * 0.090 - vec2(t * 0.035, -t * 0.018), t * 0.05 + 3.0), 3);
    float ripple = sin(dot(p, vec2(0.19, 0.13)) + t * 0.9 + flowA * 6.0) * 0.5 + 0.5;
    float caustic = pow(1.0 - abs(flowB * 2.0 - 1.0), 6.0);
    vec3 deep = tint * 0.35;
    vec3 col = mix(deep, tint, flowA * 0.9 + 0.1);
    col += vec3(0.72, 1.0, 0.97) * caustic * 0.35;
    col += tint * ripple * 0.08;

    // crest: bright cyan where the sheet is seen edge-on (meets the air), plus sparkles
    vec3 viewDir = normalize(viewRel);
    float grazing = 1.0 - abs(viewDir.y);
    float crest = pow(grazing, 7.0);
    vec3 crestCol = vec3(0.722, 1.0, 0.969);
    col = mix(col, crestCol, crest * 0.75);
    float sparkle = step(0.985, wf_hash13(vec3(floor(p * 1.5), floor(t * 3.0)))) * (0.4 + 0.6 * grazing);
    col += crestCol * sparkle * 0.8;

    bool fromBelow = viewRel.y > 0.0;
    // Fresnel-like opacity: see down into the tide (drowned islands) near you, a solid sheet toward the horizon
    float fres = pow(grazing, 2.5);
    float alpha = ColorModulator.a * (fromBelow ? 0.88 : mix(0.30, 0.92, fres) + 0.12 * flowA);
    if (fromBelow) {
        col = mix(col * 0.55, vec3(0.03, 0.10, 0.11), 0.4);
    }
    col *= brightness * 0.82;
    // motion cue: rising tide brightens, falling dims slightly
    col *= 1.0 + 0.15 * motion;

    // distance: melt into the sky's own below-horizon tide glow (end_sky.fsh §5) instead of the fog colour,
    // so the sheet has no visible edge at render distance
    float cyl = fog_cylindrical_distance(viewRel);
    float far = smoothstep(FogRenderDistanceEnd * 0.35, FogRenderDistanceEnd * 0.95, cyl);
    col = mix(col, tint * 0.42 + crestCol * 0.05, far * 0.8);
    alpha *= 1.0 - far;
    fragColor = vec4(col, clamp(alpha, 0.0, 1.0));
}
