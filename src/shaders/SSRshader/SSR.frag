#version 460
out vec2 hitUV;

in vec2 texCoord;

uniform sampler2D gViewPosition;
uniform sampler2D gViewNormal;
uniform sampler2D test;

uniform mat4 projectionMatrix;

int maxSteps = 30;
float rayStep = 0.1;

vec3 getPos(vec2 sampleLoc) {
    return texture(gViewPosition, sampleLoc).rgb * vec3(1,1,1);
}
vec3 getNorm(vec2 sampleLoc) {
    return normalize(texture(gViewNormal, sampleLoc).rgb-0.5)*2  * vec3(1,1,1);
}
vec2 toScreenSpace(vec3 P) {
    vec4 projUV = projectionMatrix * vec4(P, 1.0);
    projUV /= projUV.w;
    projUV.xy = projUV.xy * 0.5 + 0.5;
    return projUV.xy;
}

void main() {
    vec3 P = getPos(texCoord);
    vec3 V = normalize(P);
    vec3 N = getNorm(texCoord);
    vec3 R = reflect(V, N);

    // get the screen space reflection ray
    vec2 startUV = toScreenSpace(P);
    vec2 endUV = toScreenSpace(P + rayStep*R);
    vec2 ssR = endUV - startUV;
    vec2 rayPos = startUV;

    // setup depth traversal
    float rayDepth = P.z;
    float deltaDepth = (P + rayStep*R).z - rayDepth;

    hitUV = vec2(-deltaDepth);

    for (int i = 0; i < maxSteps; i++) {
        rayDepth += deltaDepth;
        rayPos += ssR;
        if (abs(getPos(rayPos).z - rayDepth) < 0.1) {
            hitUV = rayPos; return;
        }
    }
/*
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

        distToSurface = getPos(projUV.xy).z - P.z;
        if (abs(distToSurface) <= 0.05) {hitUV = projUV.xy; return;}
    }
    */
}