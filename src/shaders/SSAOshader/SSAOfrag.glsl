#version 460
out vec4 fragColor;

in vec2 texCoord;

uniform sampler2D gViewPosition;
uniform sampler2D gNormal;
uniform sampler2D texNoise;

uniform vec3 samples[64];

int kernelSize = 64;
uniform float radius;
uniform float bias;

float width;
float height;
// tile noise texture over screen based on screen dimensions divided by noise size
vec2 noiseScale = vec2(width/4.0, height/4.0);

uniform mat4 projection;

void main()
{
    vec3 fragPos = texture(gViewPosition, texCoord).xyz;
    vec3 normal = normalize(texture(gNormal, texCoord).rgb-0.5)*2;
    vec3 randomVec = normalize(texture(texNoise, texCoord * noiseScale).xyz);
    vec3 tangent = normalize(randomVec - normal * dot(randomVec, normal));
    vec3 bitangent = cross(normal, tangent);
    mat3 TBN = mat3(tangent, bitangent, normal);
    float occlusion = 0.0;
    for(int i = 0; i < kernelSize; ++i)
    {
        vec3 samplePos = TBN * samples[i];
        samplePos = fragPos + samplePos * radius;

        vec4 offset = vec4(samplePos, 1.0);
        offset = projection * offset;
        offset.xyz /= offset.w;
        offset.xyz = offset.xyz * 0.5 + 0.5;

        float sampleDepth = texture(gViewPosition, offset.xy).z;

        float rangeCheck = smoothstep(0.0, 1.0, radius / abs(fragPos.z - sampleDepth));
        occlusion += (sampleDepth >= samplePos.z + bias ? 1.0 : 0.0) * rangeCheck;
    }


    occlusion = 1.0 - (occlusion / kernelSize);

    fragColor = vec4(occlusion);
}