#version 460
//to screen space
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoords;
layout (location = 2) in vec3 aNormal;
layout (location = 3) in float aMaterial;
layout (location = 4) in vec3 aTangent;
layout (location = 5) in vec3 aBitangent;

//layout(std430, binding = 4) buffer lightSpaceMatrixBuffer {
//    mat4 lightSpaceMatrices[];
//};
const int NUM_LIGHTS = 1;

out vec3 fragPos;
out vec3 viewFragPos;
out vec2 vertexTexCoord;
out vec3 vertexNormal;
out float vertexMaterial;
out mat3 TBN;
out vec3 vertexViewNormal;
out mat3 viewTBN;
//layout(std430, binding = 5) buffer FragPosBuffer {
//    vec4 fragPosLightSpace[];
//};

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 objectMatrix[100];


void main() {
    gl_Position = projectionMatrix * viewMatrix * objectMatrix[gl_InstanceID] * vec4(aPos, 1.0);
    fragPos = vec3(objectMatrix[gl_InstanceID] * vec4(aPos, 1));
    viewFragPos = vec3(1,1,1) * vec3(viewMatrix * objectMatrix[gl_InstanceID] * vec4(aPos, 1));
    vertexTexCoord = aTexCoords;
    mat3 normalMatrix = mat3(transpose(inverse(objectMatrix[gl_InstanceID])));
    mat3 viewNormalMatrix = transpose(inverse(mat3(viewMatrix * objectMatrix[gl_InstanceID])));
    vertexNormal = normalize(normalMatrix * aNormal);
    vertexViewNormal = normalize(viewNormalMatrix * aNormal);
    vertexMaterial = aMaterial;

    vec3 T = normalize(normalMatrix * aTangent);
    vec3 B = normalize(normalMatrix * aBitangent);
    TBN = mat3(T, B, vertexNormal);
    vec3 viewT = normalize(viewNormalMatrix * aTangent);
    vec3 viewB = normalize(viewNormalMatrix * aBitangent);
    viewTBN = mat3(viewT, viewB, vertexViewNormal);
//    vertexNormal = cross(T,B);

//    for (int i = 0; i < lightSpaceMatrices.length(); i++) {
//        fragPosLightSpace[i] = lightSpaceMatrices[i] * vec4(fragPos, 1.0);
//    }
//    test = lightSpaceMatrices[0] * vec4(fragPos, 1.0);
}