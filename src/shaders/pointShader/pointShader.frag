#version 460

out vec4 FragColor;

uniform vec4 pointColor;

void main() {
    FragColor = pointColor;
}