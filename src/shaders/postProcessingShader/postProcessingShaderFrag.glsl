#version 460
out vec4 fragColor;
in vec2 texCoord;

uniform sampler2D ppBuffer;
uniform sampler2D skybox;

uniform float exposure;
uniform float gamma;


void main() {
    vec4 c = texture(ppBuffer, texCoord).rgba;
    if (c.a > 0) {
        vec3 hdrColor = c.rgb;
    
        vec3 mapped = vec3(1.0) - exp(-hdrColor * exposure);
        // gamma correction
        mapped = pow(mapped, vec3(1.0 / gamma));
    
        fragColor = vec4(mapped, 1.0);
    } else {
        fragColor = vec4(texture(skybox, texCoord).rgb, 1);
    }
}
