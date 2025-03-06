#version 460
#extension GL_ARB_bindless_texture : require
#extension GL_ARB_gpu_shader_int64 : require
const float PI = 3.14159265359;
layout (location = 0) out vec4 fragColor;
layout (location = 1) out vec4 bloomColor;
in vec2 texCoord;

uniform bool SSAO;

uniform sampler2D gPosition;
uniform sampler2D gNormal;
uniform sampler2D gMaterial;
uniform sampler2D gTexCoord;
uniform sampler2D gViewFragPos;
uniform sampler2D SSAOtex;

uniform vec3 camPos;
uniform vec3 camRot;

uniform float FOV;
uniform sampler2D skybox;
uniform int width;
uniform int height;


//raw mtl data
layout(std430, binding = 0) buffer mtlBuffer {
    float mtlData[];
};
//property maps (bindless)
layout(std430, binding = 1) buffer handleBuffer {
    sampler2D textures[];
};
//raw light data
layout(std430, binding = 2) buffer lightBuffer {
    float lightData[];
};
//light view * projection matrices
layout(std430, binding = 4) buffer lightSpaceMatrixBuffer {
    mat4 lightSpaceMatrices[];
};
//temp single shadowmap
uniform sampler2D shadowmaps;

int lightFields = 24;
int mtlFields = int(mtlData[0]);
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
struct light {
    int type; // 0 = point, 1 = directional, 2 = spotlight

    vec3 ambient;
    vec3 diffuse;
    vec3 specular;

    vec3 position; // excluded by directionalLight
    vec3 direction; // excluded by pointLight
// pointLight exclusive
    float constantAtten;
    float linearAtten;
    float quadraticAtten;
// spotlight exclusive
    float cutOff;
    float innerCutoff;
    float outerCutoff;

    sampler2D shadowMap;
    mat4 lightSpaceMatrix;
};
struct mtl {
    vec3 Ka; vec3 Kd; vec3 Ks;
    float Ns;// specular exponent
    float d;// dissolved (transparency 1-0, 1 is opaque)
    float Tr;// occasionally used, opposite of d (0 is opaque)
    vec3 Tf;// transmission filter
    float Ni;// refractive index
    vec3 Ke;// emission color
    int illum;// shading model (0-10, each has diff properties)
    int map_Ka; int map_Kd; int map_Ks;
//PBR extension types
    float Pm;// metallicity (0-1, dielectric to metallic)
    float Pr;// roughness (0-1, perfectly smooth to "extremely" rough)
    float Ps;// sheen (0-1, no sheen effect to maximum sheen)
    float Pc;// clearcoat thickness (0-1, smooth clearcoat to rough clearcoat (blurry reflections))
    float Pcr;
    float aniso;// anisotropy (0-1, isotropic surface to fully anisotropic) (uniform-directional reflections)
    float anisor;// rotational anisotropy (0-1, but essentially 0-2pi, rotates pattern of anisotropy)
    int map_Pm; int map_Pr; int map_Ps; int map_Pc; int map_Pcr; int map_norm; int map_d; int map_Tr; int map_Ns; int map_Ke; int map_Disp;
//CUSTOM
    float Density; float subsurface;
    vec3 subsurfaceColor; vec3 subsurfaceRadius;

    vec3 normal;
};
vec4 sampleTexture(int textureIndex, vec2 uv) {
    return texture(textures[textureIndex], uv).rgba;
}
mtl newMtl(int m) {
    mtl Out;
    Out.Ka = vec3(mtlData[mtlFields*m + 1], mtlData[mtlFields*m + 2], mtlData[mtlFields*m + 3]);
    Out.Kd = vec3(mtlData[mtlFields*m + 4], mtlData[mtlFields*m + 5], mtlData[mtlFields*m + 6]);
    Out.Ks = vec3(mtlData[mtlFields*m + 7], mtlData[mtlFields*m + 8], mtlData[mtlFields*m + 9]);
    Out.Ns = mtlData[mtlFields*m + 10];
    Out.d = mtlData[mtlFields*m + 11];
    Out.Tr = mtlData[mtlFields*m + 12];
    Out.Tf = vec3(mtlData[mtlFields*m + 13], mtlData[mtlFields*m + 14], mtlData[mtlFields*m + 15]);
    Out.Ni = mtlData[mtlFields*m + 16];
    Out.Ke = vec3(mtlData[mtlFields*m + 17], mtlData[mtlFields*m + 18], mtlData[mtlFields*m + 19]);
    Out.illum = int(mtlData[mtlFields*m + 20]);
    Out.map_Ka = int(mtlData[mtlFields*m + 21]);
    Out.map_Kd = int(mtlData[mtlFields*m + 22]);
    Out.map_Ks = int(mtlData[mtlFields*m + 23]);
    //pbr extension
    Out.Pm = mtlData[mtlFields*m + 24];
    Out.Pr = mtlData[mtlFields*m + 25];
    Out.Ps = mtlData[mtlFields*m + 26];
    Out.Pc = mtlData[mtlFields*m + 27];
    Out.Pcr = mtlData[mtlFields*m + 28];
    Out.aniso = mtlData[mtlFields*m + 29];
    Out.anisor = mtlData[mtlFields*m + 30];
    Out.map_Pm = int(mtlData[mtlFields*m + 31]);
    Out.map_Pr = int(mtlData[mtlFields*m + 32]);
    Out.map_Ps = int(mtlData[mtlFields*m + 33]);
    Out.map_Pc = int(mtlData[mtlFields*m + 34]);
    Out.map_Pcr = int(mtlData[mtlFields*m + 35]);
    Out.map_norm = int(mtlData[mtlFields*m + 36]);
    Out.map_d = int(mtlData[mtlFields*m + 37]);
    Out.map_Tr = int(mtlData[mtlFields*m + 38]);
    Out.map_Ns = int(mtlData[mtlFields*m + 39]);
    Out.map_Ke = int(mtlData[mtlFields*m + 40]);
    Out.map_Disp = int(mtlData[mtlFields*m + 41]);
    //CUSTOM
    Out.Density = mtlData[mtlFields*m + 42];
    Out.subsurface = mtlData[mtlFields*m + 43];
    Out.subsurfaceColor = vec3(mtlData[mtlFields*m + 44], mtlData[mtlFields*m + 45], mtlData[mtlFields*m + 46]);
    Out.subsurfaceRadius = vec3(mtlData[mtlFields*m + 47], mtlData[mtlFields*m + 48], mtlData[mtlFields*m + 49]);

    Out.normal = vec3(0);
    return Out;
}
mtl mapMtl(mtl M, vec2 uv) {
    // Mapped materials are reset to mapped values, otherwise are unchanged
    mtl m = M;
    m.Ka = ((M.map_Ka > -1) ? sampleTexture(M.map_Ka, uv).xyz * M.Ka: M.Ka);
    if (M.map_Kd > -1) {
        vec4 sampled = sampleTexture(M.map_Kd, uv);
        m.Kd = sampled.rgb;
        m.d = sampled.a;
        m.Tr = sampled.a;
    } else {
        vec4 sampled = sampleTexture(M.map_d, uv);
        m.Kd = M.Kd;
        m.d = ((M.map_d > -1) ? sampled.r : M.d);
        m.Tr = ((M.map_Tr > -1) ? sampled.r : M.Tr);
    }
    m.Ks = ((M.map_Ks > -1) ? sampleTexture(M.map_Ks, uv).rgb : M.Ks);
    m.Ke = ((M.map_Ke > -1) ? sampleTexture(M.map_Ke, uv).rgb : M.Ke);
    m.Ns = ((M.map_Ns > -1) ? sampleTexture(M.map_Ns, uv).r : M.Ns);
    m.Pm = ((M.map_Pm > -1) ? sampleTexture(M.map_Pm, uv).r : M.Pm);
    m.Pr = ((M.map_Pr > -1) ? sampleTexture(M.map_Pr, uv).r : M.Pr);
    m.Ps = ((M.map_Ps > -1) ? sampleTexture(M.map_Ps, uv).r : M.Ps);
    m.Pc = ((M.map_Pc > -1) ? sampleTexture(M.map_Pc, uv).r : M.Pc);
    return m;
}
light newLight(int n) {
    light Out;
    Out.type = int(lightData[lightFields*n + 0]);

    Out.ambient = vec3(lightData[lightFields*n + 1], lightData[lightFields*n + 2], lightData[lightFields*n + 3]);
    Out.diffuse = vec3(lightData[lightFields*n + 4], lightData[lightFields*n + 5], lightData[lightFields*n + 6]);
    Out.specular = vec3(lightData[lightFields*n + 7], lightData[lightFields*n + 8], lightData[lightFields*n + 9]);

    Out.position = vec3(lightData[lightFields*n + 10], lightData[lightFields*n + 11], lightData[lightFields*n + 12]);
    Out.direction = vec3(lightData[lightFields*n + 13], lightData[lightFields*n + 14], lightData[lightFields*n + 15]);

    Out.constantAtten = lightData[lightFields*n + 16];
    Out.linearAtten = lightData[lightFields*n + 17];
    Out.quadraticAtten = lightData[lightFields*n + 18];

    Out.cutOff = lightData[lightFields*n + 19];
    Out.innerCutoff = lightData[lightFields*n + 20];
    Out.outerCutoff = lightData[lightFields*n + 21];

    Out.shadowMap = shadowmaps;
    Out.lightSpaceMatrix = lightSpaceMatrices[n];
    return Out;
}
float attenuation(vec3 lPos, vec3 pos, float constant, float linear, float quadratic) {
    float distance = length(lPos - pos);
    return 1 / (constant + linear*distance + quadratic*(distance*distance));
}
vec4 getLighting(light l, vec3 pos) {
    vec4 Out;
    if (l.type == 0) {
        Out.w = attenuation(l.position, pos, l.constantAtten, l.linearAtten, l.quadraticAtten);
        Out.xyz = normalize(l.position - pos);
    } else if (l.type == 1) {
        Out.w = 1;
        Out.xyz = -l.direction;
    } else if (l.type == 2) {
        float theta = dot(normalize(pos - l.position), normalize(l.direction));
        float epsilon = l.cutOff - l.innerCutoff;
        float intensity = clamp((l.innerCutoff - theta) / epsilon, 0, 1);
        if (theta > l.cutOff) {
            Out.w = intensity;
            Out.xyz = normalize(l.position - pos);
        }
    } else if (l.type == 3) {
        float theta = dot(normalize(pos - camPos*vec3(-1,1,-1)), normalize(rotate(vec3(0,0,-1), camRot*vec3(-1,1,1))));
        float epsilon = l.cutOff - l.innerCutoff;
        float intensity = clamp((l.innerCutoff - theta) / epsilon, 0, 1);
        if (theta > l.cutOff) {
            Out.w = intensity;
            Out.xyz = normalize(camPos - pos);
        }
    }
    return Out;
}
float calculateShadow(light l, vec3 fragPos, vec3 normal) {
    vec4 fragPosLightSpace = l.lightSpaceMatrix * vec4(fragPos, 1);
    vec3 projCoords = (fragPosLightSpace.xyz / fragPosLightSpace.w) * 0.5 + 0.5;
    float closestDepth = texture(l.shadowMap, projCoords.xy).r;
    float currentDepth = projCoords.z;
    vec3 lightDir = normalize(fragPos - l.position);
    float bias = max(0.0001 * (1.0 - dot(normal, -lightDir)), 0.0001);

    float shadow = 0.0;
    vec2 texelSize = 1.0 / textureSize(l.shadowMap, 0);
    for(int x = -1; x <= 1; ++x)
    {
        for(int y = -1; y <= 1; ++y)
        {
            float pcfDepth = texture(l.shadowMap, projCoords.xy + vec2(x, y) * texelSize).r;
            shadow += currentDepth - bias > pcfDepth  ? 1.0 : 0.0;
        }
    }
    shadow /= 9.0;

    if(projCoords.z > 1.0)
    shadow = 0.0;

    return 1-shadow;
}
vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}  
float DistributionGGX(vec3 N, vec3 H, float roughness) {
    float a      = roughness*roughness;
    float a2     = a*a;
    float NdotH  = max(dot(N, H), 0.0);
    float NdotH2 = NdotH*NdotH;
	
    float num   = a2;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = PI * denom * denom;
	
    return num / denom;
}
float GeometrySchlickGGX(float NdotV, float roughness) {
    float r = (roughness + 1.0);
    float k = (r*r) / 8.0;

    float num   = NdotV;
    float denom = NdotV * (1.0 - k) + k;
	
    return num / denom;
}
float GeometrySmith(vec3 N, vec3 V, vec3 L, float roughness) {
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float ggx2  = GeometrySchlickGGX(NdotV, roughness);
    float ggx1  = GeometrySchlickGGX(NdotL, roughness);
	
    return ggx1 * ggx2;
}

vec3 getSkyboxCol() {
    vec2 uv = texCoord * 2.0 - 1.0;
    uv.x *= width / height;

    //compute view direction before rotation
    float fovRadians = FOV * (PI / 180.0);
    vec3 vDir = normalize(vec3(uv, -1.0 / tan(fovRadians * 0.5)));

    vDir = rotate(vDir, camRot*vec3(-1,1,1));

    //convert to equirectangular uv mapping
    float u = atan(vDir.z, vDir.x) / (2.0 * PI) + 0.5;
    float v = asin(vDir.y) / PI + 0.5;

    return texture(skybox, vec2(u,1-v)).rgb;
}

void main() {
    vec4 initSample = texture(gPosition, texCoord).rgba;
    if (isinf(initSample.r)) {
        fragColor = vec4(getSkyboxCol()*0.5,1);
    } else {
        vec3 thisPosition = initSample.rgb;
        vec3 thisNormal = (texture(gNormal, texCoord).rgb - 0.5) * 2;
        int thisMtlID = int(texture(gMaterial, texCoord).r*1000);
        vec2 thisTexCoord = texture(gTexCoord, texCoord).rg;
        mtl thisMtl = newMtl(thisMtlID);
        thisMtl = mapMtl(thisMtl, thisTexCoord);
        vec3 p = thisPosition;
        vec3 albedo = thisMtl.Kd;
        vec3 N = thisNormal;
        float metallic = thisMtl.Pm;
        float roughness = thisMtl.Pr;
        //ambient occlusion
        float ao = texture(SSAOtex, texCoord).r;
        //user-set scene ambient (Sa) approximation
        float Sa = 0.1;

        vec3 Lo = (albedo * Sa * (SSAO ? ao : 1)) + thisMtl.Ke; //ambient preset
        vec3 V = normalize(camPos - p);
	    //approximating the hemisphere integral by assuming each vector to light to be a solid angle on the hemisphere
        for (int i = 0; i < int(lightData.length()/lightFields)+1; i++) {
            light l = newLight(i);
            vec4 thisLighting = getLighting(l,p);
	        float atten = thisLighting.w;

            vec3 Wi = thisLighting.xyz;
	        //Wi = vector from frag to light
            float cosTheta = max(dot(N, Wi), 0);
	        //angle between normal and Wi
            vec3 radiance = l.diffuse * atten;
	        //radiance of the current light
            vec3 H = normalize(V+Wi);
	        //halfway between vector to cam and light
            vec3 F0 = vec3(0.04); 
            F0 = mix(F0, albedo, metallic);
            vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);
            float NDF = DistributionGGX(N, H, roughness);
            float G   = GeometrySmith(N, V, Wi, roughness);
            vec3 numerator = NDF * G * F;
            float denominator = 4.0 * max(dot(N, V), 0.0) * max(dot(N, Wi), 0.0)  + 0.0001;
            vec3 specular = numerator / denominator;
            vec3 kS = F;
            vec3 kD = vec3(1.0) - kS;
            kD *= 1.0 - metallic;
            float NdotL = max(dot(N, Wi), 0.0);
            //Lo += (specular*5) * radiance * NdotL * calculateShadow(l, p, N);
            Lo += (kD * albedo / PI + specular) * radiance * NdotL * calculateShadow(l, p, N);
        }
	float brightness = dot(Lo, vec3(0.2126, 0.7152, 0.0722));
        if (brightness > 1) {
            bloomColor = vec4(Lo,1);
        } else {
            bloomColor = vec4(0,0,0,1);
        }
	//gamma correct
        //Lo = Lo/(Lo+vec3(1));
        //Lo = pow(Lo, vec3(1/gamma));
        fragColor = vec4(Lo,1);
    }
}
