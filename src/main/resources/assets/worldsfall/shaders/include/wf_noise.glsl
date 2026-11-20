#ifndef WORLDSFALL_NOISE_GLSL
#define WORLDSFALL_NOISE_GLSL
// Hash / value noise / fBm helpers for the Worldsfall sky and tide shaders.
float wf_hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}
vec3 wf_hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}
float wf_noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float n000 = wf_hash13(i), n100 = wf_hash13(i + vec3(1, 0, 0)), n010 = wf_hash13(i + vec3(0, 1, 0)), n110 = wf_hash13(i + vec3(1, 1, 0));
    float n001 = wf_hash13(i + vec3(0, 0, 1)), n101 = wf_hash13(i + vec3(1, 0, 1)), n011 = wf_hash13(i + vec3(0, 1, 1)), n111 = wf_hash13(i + vec3(1, 1, 1));
    return mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y), mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
}
float wf_fbm(vec3 p, int octaves) {
    float a = 0.5, s = 0.0, n = 0.0;
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) break;
        s += a * wf_noise3(p);
        n += a;
        p = p * 2.03 + vec3(17.1, 9.3, 3.7);
        a *= 0.5;
    }
    return s / n;
}
// Domain-warped fBm for soft nebula filaments.
float wf_nebula(vec3 p, float t) {
    vec3 q = vec3(wf_fbm(p + vec3(0.0, t, 0.0), 4), wf_fbm(p + vec3(5.2, 1.3 - t, 2.8), 4), wf_fbm(p + vec3(1.7, 9.2, t), 4));
    return wf_fbm(p + 2.6 * q, 5);
}
// Star field: one star per cell, size/brightness randomised, with gentle twinkle.
float wf_stars(vec3 dir, float scale, float threshold, float t) {
    vec3 p = dir * scale;
    vec3 cell = floor(p);
    vec3 h = wf_hash33(cell);
    if (h.x < threshold) return 0.0;
    vec3 center = cell + 0.25 + 0.5 * wf_hash33(cell + 7.0);
    float d = length(p - center);
    float size = mix(0.035, 0.11, h.y * h.y);
    float twinkle = 0.75 + 0.25 * sin(t * (1.5 + 3.0 * h.z) + h.x * 40.0);
    return smoothstep(size, 0.0, d) * twinkle * (0.4 + 0.6 * h.z);
}
#endif
