#version 330 core

in vec2 TexCoord;
out vec4 FragColor;

uniform sampler2D text;
uniform vec3 textColor;

void main() {
    float raw = texture(text, vec2(TexCoord.x, TexCoord.y)).r;
    if (raw < 0.01) discard;
    FragColor = vec4(textColor, raw);
}