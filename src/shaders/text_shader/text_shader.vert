#version 330 core

layout(location = 0) in vec4 vertex;
layout(location = 1) in vec2 texCoord;

out vec2 TexCoord;

uniform int width;
uniform int height;

void main() {
    mat4 test = mat4(
    vec4(2.0 / width, 0.0, 0.0, 0), // First column
    vec4(0.0, -2.0 / height, 0.0, 0), // Second column
    vec4(0.0, 0.0, 0, 0.0),         // Third column
    vec4(0.0, 0.0, 0.0, 1.0)           // Fourth column
    );

    gl_Position = test * vec4(vec2(vertex.x, vertex.y), 0, 1.0);
    TexCoord = texCoord;
    //TexCoord = (vertices[gl_VertexID] + 1.0) * 0.5;
    //gl_Position = vec4(vertices[gl_VertexID], 0, 1.0);
}