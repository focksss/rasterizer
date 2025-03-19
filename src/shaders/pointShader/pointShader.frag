#version 460

out vec4 FragColor;

uniform vec3 color;

void main() {
    vec2 temp = gl_PointCoord - vec2(0.5);
    float f = dot(temp, temp);
    if (f>0.25) discard;
    FragColor = vec4(color,1);
}