#version 460

uniform vec3 pointPosition;

void main() {
    vec2 pos = pointPosition.xy*2 - 1;
    gl_Position = vec4(pos, 0.0, 1.0);
}