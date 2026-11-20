#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <worldsfall:wf_noise.glsl>

// Layered End sky (design doc §18.7): base gradient, three star layers, biome-tinted drifting nebula,
// distant debris silhouettes near the horizon, and the tide's teal glow from below.
// ColorModulator.rgb = nebula tint (blended per biome), ColorModulator.a = nebula strength.
// TextureMat[0] = (time seconds, tide glow 0..1, calmed 0..1, underside haze 0..1)
// TextureMat[1].xyz = camera position (for slight parallax of the near star layer)

layout(location = 0) in vec3 skyDir;
layout(location = 0) out vec4 fragColor;

void main() {
    vec3 d = normalize(skyDir);
    float t = TextureMat[0].x;
    float tideGlow = TextureMat[0].y;
    float calmed = TextureMat[0].z;
    float haze = TextureMat[0].w;
    vec3 cam = TextureMat[1].xyz;

    // 1. base gradient: near-black violet zenith (#05030C) -> lighter violet horizon (#160A28)
    float h = clamp(d.y, -1.0, 1.0);
    vec3 zenith = vec3(0.020, 0.012, 0.047);
    vec3 horizon = vec3(0.086, 0.039, 0.157);
    vec3 nadir = vec3(0.012, 0.010, 0.030);
    vec3 col = h >= 0.0 ? mix(horizon, zenith, pow(h, 0.55)) : mix(horizon, nadir, pow(-h, 0.5));

    // 2. nebula: large soft drifting clouds, tinted by the biome the player stands in
    float drift = t * 0.004;
    vec3 np = d * 1.7 + vec3(drift, 0.0, -drift * 0.6);
    float neb = wf_nebula(np, t * 0.002);
    float filaments = smoothstep(0.42, 0.85, neb);
    float glow = smoothstep(0.30, 0.95, neb);
    vec3 tint = ColorModulator.rgb;
    vec3 secondary = vec3(0.129, 0.267, 0.420); // navy #21446B
    float split = wf_fbm(d * 3.1 + 11.0, 3);
    vec3 nebColor = mix(tint, secondary, smoothstep(0.35, 0.75, split) * 0.55);
    float bandMask = 0.55 + 0.45 * smoothstep(-0.25, 0.35, h); // a little denser above the horizon
    col += nebColor * (glow * 0.55 + filaments * 0.65) * ColorModulator.a * bandMask;
    // dark dust lanes inside the nebula
    float dust = smoothstep(0.55, 0.80, wf_fbm(d * 6.0 + vec3(3.0, drift * 2.0, 1.0), 4));
    col *= 1.0 - dust * 0.45 * glow;

    // 3. stars: three layers; the nearest drifts very slightly with the camera (depth)
    float s = 0.0;
    s += wf_stars(d, 220.0, 0.86, t) * 0.55;
    s += wf_stars(d, 120.0, 0.90, t * 1.3) * 0.85;
    vec3 par = normalize(d + cam * 0.00002);
    s += wf_stars(par, 60.0, 0.955, t * 0.7) * 1.25;
    float starFade = smoothstep(-0.25, 0.05, h);
    vec3 starCol = mix(vec3(0.80, 0.94, 0.99), vec3(1.0), wf_hash13(floor(d * 120.0)));
    col += starCol * s * starFade * (1.0 - glow * 0.35);

    // 4. distant debris silhouettes: a faint ring of blocky island shapes near the horizon
    float az = atan(d.z, d.x);
    float stepAz = floor(az * 38.0) / 38.0;
    float islandTop = 0.035 + 0.11 * pow(wf_hash13(vec3(stepAz * 17.0, 3.0, 1.0)), 3.0);
    float islandOn = step(0.58, wf_hash13(vec3(floor(az * 9.0), 7.0, 2.0)));
    float sil = islandOn * (step(h, islandTop) * step(-0.02, h));
    col = mix(col, vec3(0.035, 0.022, 0.06), sil * 0.65);

    // 5. tide glow from below (teal), silver-blue once calmed by the Still Throne
    vec3 tideCol = mix(vec3(0.227, 0.878, 0.816), vec3(0.725, 0.827, 1.0), calmed);
    float below = smoothstep(0.05, -0.65, h);
    col += tideCol * below * tideGlow * 0.35;
    // Underside / Drift: teal haze rising from below
    col += vec3(0.04, 0.20, 0.19) * haze * smoothstep(0.3, -0.6, h) * 0.6;

    fragColor = vec4(col, 1.0);
}
