#version 460

layout (location = 0) vec2 pos;
uniform float pointSize;

void main() {
  gl_PointSize = pointSize;
  gl_Position = vec4(pos*2 - 1, 0, 0);
}
