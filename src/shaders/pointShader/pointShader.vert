#version 460

uniform vec2 pointPosition;

void main() {
    gl_Position = vec4(pointPosition*2 - 1, 0.0, 1.0);
}