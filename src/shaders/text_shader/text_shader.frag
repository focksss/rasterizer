#version 330 core

in vec2 TexCoord;
out vec4 FragColor;

uniform sampler2D text;
uniform vec3 textColor;

uniform vec3 clipMin;
uniform vec3 clipMax;

void main() {
    if (gl_FragCoord.x > clipMin.x && gl_FragCoord.x < clipMax.x) {
        if (gl_FragCoord.y > clipMin.y && gl_FragCoord.y < clipMax.y) {
            float raw = texture(text, vec2(TexCoord.x, TexCoord.y)).r;
            if (raw < 0.01) discard;
            FragColor = vec4(textColor, raw);
        } else discard;
    } else discard;
}