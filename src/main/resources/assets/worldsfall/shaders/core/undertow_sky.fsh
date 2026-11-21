#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <worldsfall:wf_noise.glsl>

// The Undertow's sky (expansion 2.0): the tide seen from the inside. An inverted Drift: teal-white light all around,
// brightest overhead where the tide plane glows, near-black silhouettes of drifting debris, and slow caustic ripples.
// TextureMat[0].x = time (seconds); TextureMat[1].xyz = camera position (for slow parallax of the silhouettes).

layout(location = 0) in vec3 skyDir;
layout(location = 0) out vec4 fragColor;

void main() {
    vec3 d = normalize(skyDir);
    float t = TextureMat[0].x;
    vec3 cam = TextureMat[1].xyz;
    float h = clamp(d.y, -1.0, 1.0);

    // haze: pale teal-white overhead, deeper teal below (#CFF3EC -> #4FA8A0 -> #123A3A)
    vec3 top = vec3(0.81, 0.95, 0.92);
    vec3 mid = vec3(0.31, 0.66, 0.63);
    vec3 low = vec3(0.07, 0.23, 0.23);
    vec3 col = h >= 0.0 ? mix(mid, top, pow(h, 0.7)) : mix(mid, low, pow(-h, 0.6));

    // the tide plane far overhead: a bright, rippling ceiling of light
    vec2 uv = d.xz / max(d.y, 0.08);
    float caustic = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        vec3 q = vec3(uv * (1.4 + fi * 0.9) + vec2(t * 0.03 * (1.0 + fi), -t * 0.02), t * 0.05 + fi * 3.7);
        caustic += pow(1.0 - abs(wf_noise3(q) * 2.0 - 1.0), 6.0) * (0.6 - fi * 0.15);
    }
    col += vec3(0.75, 1.0, 0.95) * caustic * smoothstep(0.05, 0.6, h) * 0.55;

    // slow drifting light shafts
    float shaft = wf_fbm(vec3(atan(d.z, d.x) * 3.0, t * 0.02, 0.0), 3);
    col += vec3(0.6, 0.9, 0.85) * smoothstep(0.55, 0.9, shaft) * smoothstep(-0.2, 0.5, h) * 0.18;

    // near-black silhouettes of things that sank: soft blobs drifting near the horizon
    vec3 par = normalize(d + cam * 0.00003);
    float sil = wf_fbm(par * 2.6 + vec3(t * 0.004, 0.0, -t * 0.003), 4);
    float mask = smoothstep(0.62, 0.70, sil) * (1.0 - smoothstep(0.15, 0.55, abs(h + 0.05)));
    col = mix(col, vec3(0.03, 0.07, 0.08), mask * 0.85);

    // glimmers: tiny lights scattered in the haze
    col += vec3(0.7, 1.0, 0.95) * wf_stars(d, 70.0, 0.975, t * 2.0) * 0.6;

    fragColor = vec4(col, 1.0);
}
