#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <worldsfall:wf_noise.glsl>

// The Fracture's sky (§4.7, §18.7): crimson-black, smeared red nebula, and a single colossal magenta-white
// glow at the arena's centre which lights everything. TextureMat[0] = (time, pulse 0..1, calm 0..1, unused);
// TextureMat[1].xyz = direction from the camera to the Core (normalised); TextureMat[1].w = core apparent size.

layout(location = 0) in vec3 skyDir;
layout(location = 0) out vec4 fragColor;

void main() {
    vec3 d = normalize(skyDir);
    float t = TextureMat[0].x;
    float pulse = TextureMat[0].y;
    float calm = TextureMat[0].z;
    vec3 coreDir = normalize(TextureMat[1].xyz + vec3(0.0, 1e-4, 0.0));
    float coreSize = TextureMat[1].w;

    vec3 col = mix(vec3(0.102, 0.020, 0.063), vec3(0.03, 0.005, 0.02), smoothstep(-0.2, 0.9, d.y));
    // smeared red nebula, stretched horizontally and swirling toward the core
    vec3 sp = vec3(d.x * 0.8, d.y * 2.4, d.z * 0.8) * 1.6;
    float neb = wf_nebula(sp + vec3(t * 0.01, 0.0, 0.0), t * 0.003);
    col += vec3(0.45, 0.04, 0.12) * smoothstep(0.35, 0.95, neb) * 0.55;
    col += vec3(0.25, 0.02, 0.18) * smoothstep(0.55, 0.9, wf_fbm(sp * 2.0 + 4.0, 4)) * 0.4;
    // sparse dim embers
    col += vec3(1.0, 0.45, 0.65) * wf_stars(d, 90.0, 0.965, t) * 0.5;

    // the Core: white centre -> hot pink -> magenta halo; bloom-like falloff
    float c = max(dot(d, coreDir), 0.0);
    float ang = acos(clamp(c, -1.0, 1.0));
    float r = ang / max(coreSize, 0.001);
    float throb = 0.85 + 0.15 * pulse;
    vec3 white = vec3(1.0);
    vec3 pink = vec3(1.0, 0.70, 0.925);
    vec3 magenta = mix(vec3(1.0, 0.247, 0.706), vec3(0.85, 0.9, 1.0), calm);
    vec3 core = mix(white, pink, smoothstep(0.0, 0.7, r));
    core = mix(core, magenta, smoothstep(0.6, 1.4, r));
    float body = 1.0 - smoothstep(0.85, 1.05, r);
    float halo = exp(-r * 1.1) * 0.9 * throb + exp(-r * 0.25) * 0.25;
    col = mix(col, core, body);
    col += magenta * halo * (1.0 - body);
    fragColor = vec4(col, 1.0);
}
