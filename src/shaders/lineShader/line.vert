#version 460

uniform vec3 p1;
uniform vec3 p2;

void main() {
    vec2[] points = {p1.xy, p2.xy};
    gl_Position = vec4(points[gl_VertexID]*2 - 1, 0, 1.0);
}
