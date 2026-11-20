#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// The Void Tide plane (§5.1): a huge camera-centred quad at the tide height (camera-relative coordinates).
// TextureMat[1].xyz = camera world position.

layout(location = 0) in vec3 Position;
layout(location = 0) out vec3 worldPos;
layout(location = 1) out vec3 viewRel;

void main() {
    vec3 rel = Position + ModelOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(rel, 1.0);
    viewRel = rel;
    worldPos = rel + TextureMat[1].xyz;
}
