#version 460
out vec4 fragColor;
in vec2 texCoord;

uniform sampler2D blurInput;

uniform bool horizontal;
uniform int bloomSamples = 9;
uniform float weight[9] = float[] (
0.153170,  // Center pixel
0.122649, 0.122649,  // First neighbors
0.091575, 0.091575,  // Second neighbors
0.061313, 0.061313,  // Third neighbors
0.031237, 0.031237   // Fourth neighbors
);

void main()
{
    vec2 tex_offset = 1.0 / textureSize(blurInput, 0); // gets size of single texel
    vec3 result = texture(blurInput, texCoord).rgb * weight[0]; // current fragment's contribution
    if(horizontal)
    {
        for(int i = 1; i < bloomSamples; ++i)
        {
            result += texture(blurInput, texCoord + vec2(tex_offset.x * i, 0.0)).rgb * weight[i];
            result += texture(blurInput, texCoord - vec2(tex_offset.x * i, 0.0)).rgb * weight[i];
        }
    }
    else
    {
        for(int i = 1; i < bloomSamples; ++i)
        {
            result += texture(blurInput, texCoord + vec2(0.0, tex_offset.y * i)).rgb * weight[i];
            result += texture(blurInput, texCoord - vec2(0.0, tex_offset.y * i)).rgb * weight[i];
        }
    }
    fragColor = vec4(result, 1.0);
}