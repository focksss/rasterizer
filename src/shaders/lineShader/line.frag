#version 460

uniform vec3 color;

uniform vec3 clipMin;
uniform vec3 clipMax;

void main() {
    if (gl_FragCoord.x > clipMin.x && gl_FragCoord.x < clipMax.x) {
        if (gl_FragCoord.y > clipMin.y && gl_FragCoord.y < clipMax.y) {
            gl_FragColor = vec4(color, 1.0);
        } else discard;
    } else discard;
}
