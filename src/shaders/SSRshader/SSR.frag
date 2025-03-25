#version 460
out vec2 hitUV;

in vec2 texCoord;

uniform sampler2D gViewPosition;
uniform sampler2D gViewNormal;

uniform mat4 projectionMatrix;

int maxSteps = 30;
float rayStep = 0.1;

void main() {
    vec3 P = texture(gViewPosition, texCoord).rgb * vec3(1,1,-1);
    vec3 V = -normalize(P);
    vec3 N = normalize(texture(gViewNormal, texCoord).rgb-0.5)*2;
    vec3 R = reflect(V, N);

    //raycast
    R *= rayStep;
    float distToSurface = 0;
    float diff = 0;
    int steps = 0;
    vec4 projUV = vec4(0);

    float dDepth;
    for (int i = 0; i < maxSteps; i++) {
        P += R;

        projUV = projectionMatrix * vec4(P, 1.0);
        projUV /= projUV.w;
        projUV.xy = projUV.xy * 0.5 + 0.5;

        distToSurface = -texture(gViewPosition, projUV.xy).z - P.z;
        if (abs(distToSurface) <= 0.1) {hitUV = projUV.xy; return;}
    }
    hitUV = vec2(1);
}