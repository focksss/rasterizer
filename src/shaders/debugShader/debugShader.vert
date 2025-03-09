#version 460
layout (location = 0) in vec3 aPos;

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 objectMatrix[1];

void main() {
    gl_Position = projectionMatrix * viewMatrix * objectMatrix[0] * vec4(aPos, 1.0);
}
