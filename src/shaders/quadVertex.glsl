#version 460
const vec2 vertices[6] = vec2[](
vec2(-1.0, -1.0), // Bottom-left
vec2( 1.0, -1.0), // Bottom-right
vec2(-1.0,  1.0), // Top-left
vec2(-1.0,  1.0), // Top-left
vec2( 1.0, -1.0), // Bottom-right
vec2( 1.0,  1.0)  // Top-right
);

out vec2 texCoord;

void main() {
    texCoord = (vertices[gl_VertexID] + 1.0) * 0.5;
    gl_Position = vec4(vertices[gl_VertexID], 0, 1.0);
}