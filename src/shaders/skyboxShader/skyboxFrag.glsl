#version 460
#extension GL_ARB_bindless_texture : require
#extension GL_ARB_gpu_shader_int64 : require

out vec4 fragColor;
in vec2 texCoord;

uniform sampler2D skybox;
uniform vec3 camRot;


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
    float cx = cos(rot.x);
    float sx = sin(rot.x);
    float cy = cos(rot.y);
    float sy = sin(rot.y);
    float cz = cos(rot.z);
    float sz = sin(rot.z);
    mat3 rm = rotationMatrix(rot);
    return p * rm;
}

vec3 bgCol(vec3 In) {
    vec2 uv = vec2(
    0.5 + atan(In.z, In.x) / (2.0 * 3.14159),
    0.5 - asin(In.y) / 3.14159
    );
    vec3 skyColor = texture(skybox, uv).rgb;
    return skyColor.rgb;
}

void main() {
    vec3 d = rotate(vec3(((texCoord*2 - 1) * 1), 1), camRot*vec3(1,-1,1));
    fragColor = vec4(bgCol(d), 1);
}
