#version 460
layout (location = 0) out vec4 fragColor;
in vec2 texCoord;

uniform sampler2D ppBuffer;
uniform sampler2D bloomTex;

uniform float exposure;
uniform float gamma;

uniform float bloomIntensity;
uniform bool doBloom;

uniform vec3 camRot;
uniform float FOV;

uniform int width;
uniform int height;

void main() {
    vec4 c = texture(ppBuffer, texCoord).rgba;
    if (c.a == 1) {
        vec3 hdrColor;
        if (doBloom) {
            vec3 bloom = texture(bloomTex, texCoord).rgb;
            hdrColor = mix(c.rgb, bloom, bloomIntensity);
        } else {
            hdrColor = c.rgb;
        }

        vec3 mapped = vec3(1.0) - exp(-hdrColor * exposure);
        // gamma correction
        mapped = pow(mapped, vec3(1.0 / gamma));

        fragColor = vec4(mapped, 1.0);
    }
}
