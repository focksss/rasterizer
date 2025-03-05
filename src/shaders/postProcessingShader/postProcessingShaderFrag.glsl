#version 460
const float PI = 3.14159265359;
layout (location = 0) out vec4 fragColor;
in vec2 texCoord;

uniform sampler2D ppBuffer;
uniform sampler2D bloomTex;
uniform sampler2D skybox;

uniform float exposure;
uniform float gamma;

uniform vec3 camRot;
uniform float FOV;

uniform int width;
uniform int height;

mat3 rotateX(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat3(
    1.0, 0.0, 0.0,
    0.0,   c,  -s,
    0.0,   s,   c
    );
}
mat3 rotateY(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat3(
    c, 0.0,   s,
    0.0, 1.0, 0.0,
    -s, 0.0,   c
    );
}
mat3 rotateZ(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat3(
    c,  -s, 0.0,
    s,   c, 0.0,
    0.0, 0.0, 1.0
    );
}
mat3 rotationMatrix(vec3 angles) {
    return rotateX(angles.x) * rotateY(angles.y) * (angles.z != 0 ? rotateZ(angles.z) : mat3(1));
}
vec3 rotate(vec3 p, vec3 rot) {
    mat3 rm = rotationMatrix(rot);
    return p * rm;
}

void main() {
    vec4 c = texture(ppBuffer, texCoord).rgba;
    if (c.a == 1) {
        vec3 hdrColor = c.rgb + texture(bloomTex, texCoord).rgb;

        vec3 mapped = vec3(1.0) - exp(-hdrColor * exposure);
        // gamma correction
        mapped = pow(mapped, vec3(1.0 / gamma));
    
        fragColor = vec4(mapped, 1.0);
    } else {
        vec2 uv = texCoord * 2.0 - 1.0;
        uv.x *= width / height;

        //compute view direction before rotation
        float fovRadians = FOV * (PI / 180.0);
        vec3 vDir = normalize(vec3(uv, -1.0 / tan(fovRadians * 0.5)));

        vDir = rotate(vDir, camRot*vec3(-1,1,1));

        //convert to equirectangular uv mapping
        float u = atan(vDir.z, vDir.x) / (2.0 * PI) + 0.5;
        float v = asin(vDir.y) / PI + 0.5;

        fragColor = vec4(vDir, 1.0);
        fragColor = texture(skybox, vec2(u,1-v));
    }
}
