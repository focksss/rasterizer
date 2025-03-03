#version 460
layout (location = 0) in vec3 aPos;

uniform mat4 objectMatrix[100];
uniform mat4 lightSpaceMatrix;
uniform mat4 lightView;

layout(std430, binding = 4) buffer lightSpaceMatrixBuffer {
    mat4 lightSpaceMatrices[];
};

void main() {
    gl_Position = lightSpaceMatrix * objectMatrix[gl_InstanceID] * vec4(aPos, 1.0);
}