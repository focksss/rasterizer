#version 460

uniform vec3 position;
uniform vec3 scale;

void main() {
    vec2 pos = position.xy;
    vec2 size = scale.xy;
    vec2 vertices[6] = vec2[](
    pos + size*vec2(0,0), // Bottom-left
    pos + size*vec2(1,0), // Bottom-right
    pos + size*vec2(0,1), // Top-left
    pos + size*vec2(0,1), // Top-left
    pos + size*vec2(1,0), // Bottom-right
    pos + size*vec2(1,1)  // Top-right
    );

    gl_Position = vec4(vertices[gl_VertexID]*2 - 1, 0, 1.0);
}