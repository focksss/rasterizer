#version 460
#extension GL_ARB_bindless_texture : require
#extension GL_ARB_gpu_shader_int64 : require
in vec3 fragPos;
in vec3 viewFragPos;
in vec2 vertexTexCoord;
in vec3 vertexNormal;
in float vertexMaterial;
in mat3 TBN;
in vec3 vertexViewNormal;
in mat3 viewTBN;
layout (location = 0) out vec4 gPosition;
layout (location = 1) out vec4 gNormal;
layout (location = 2) out float gMaterial;
layout (location = 3) out vec2 gTexCoord;
layout (location = 4) out vec4 gViewPosition;
layout (location = 5) out vec4 gViewNormal;

layout(std430, binding = 0) buffer mtlBuffer {
    float mtlData[];
};
int mtlFields = int(mtlData[0]);
layout(std430, binding = 1) buffer handleBuffer {
    sampler2D textures[];
};


void setGbuffer() {
    float mapN = mtlData[int(mtlFields * vertexMaterial + 36)];
    if (mapN > -1) {
        vec3 mapN = texture(textures[int(mapN)], vertexTexCoord).rgb; //normal is in color space
        mapN = normalize(mapN * 2 - 1); //normal is in world space
        vec3 mapWorldN = normalize(TBN * mapN);
        vec3 mapViewN = normalize(viewTBN * mapN);
        gNormal = vec4((mapWorldN*0.5)+0.5, 1); //normal in color space
        gViewNormal = vec4((mapViewN*0.5)+0.5, 1);
        //gNormal = vec4(vertexNormal,1);
    } else {
        gNormal = vec4(vertexNormal*0.5 + 0.5, 1);
        gViewNormal = vec4(vertexViewNormal*0.5 + 0.5, 1);
    }
    gPosition = vec4(fragPos, 1);
    gViewPosition = vec4(viewFragPos, 1);
    gMaterial = float(vertexMaterial*0.001);
    gTexCoord = vec2(vertexTexCoord.x, vertexTexCoord.y);
}

void main()
{
    float mapKD = mtlData[int(mtlFields * vertexMaterial + 22)];
    if (mapKD > -1) {
        float d = texture(textures[int(mapKD)], vertexTexCoord).a;
        if (d < mtlData[int(mtlFields * vertexMaterial + 50)]) {
            discard;
        } else {
            setGbuffer();
        }
    } else {
        setGbuffer();
    }
}