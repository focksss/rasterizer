#version 460
#extension GL_ARB_bindless_texture : require
#extension GL_ARB_gpu_shader_int64 : require

out vec4 fragColor;
in vec2 texCoord;

uniform sampler2D screenTexture;

const float offset = 1.0 / 300.0;

layout(std430, binding = 0) readonly buffer textureHandles {
    sampler2D textures[];
};
layout(std430, binding = 4) buffer lightSpaceMatrixBuffer {
    mat4 lightSpaceMatrices[];
};


void main()
{
    vec2 offsets[9] = vec2[](
    vec2(-offset,  offset), // top-left
    vec2( 0.0f,    offset), // top-center
    vec2( offset,  offset), // top-right
    vec2(-offset,  0.0f),   // center-left
    vec2( 0.0f,    0.0f),   // center-center
    vec2( offset,  0.0f),   // center-right
    vec2(-offset, -offset), // bottom-left
    vec2( 0.0f,   -offset), // bottom-center
    vec2( offset, -offset)  // bottom-right
    );

    float kernel[9] = float[](
    -1, -1, -1,
    -1,  9, -1,
    -1, -1, -1
    );

    vec3 sampleTex[9];
    for(int i = 0; i < 9; i++)
    {
        sampleTex[i] = vec3(texture(screenTexture, texCoord.st + offsets[i]));
    }
    vec3 col = vec3(0.0);
    for(int i = 0; i < 9; i++)
    col += sampleTex[i] * kernel[i];

    fragColor = texture(screenTexture, texCoord);
}