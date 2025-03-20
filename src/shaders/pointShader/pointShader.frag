#version 460

out vec4 FragColor;

uniform vec3 color;

uniform vec3 clipMin;
uniform vec3 clipMax;

void main() {
    if (gl_FragCoord.x > clipMin.x && gl_FragCoord.x < clipMax.x) {
        if (gl_FragCoord.y > clipMin.y && gl_FragCoord.y < clipMax.y) {
                vec2 temp = gl_PointCoord - vec2(0.5);
                float f = dot(temp, temp);
                if (f>0.25) discard;
                FragColor = vec4(color,1);  
        } else discard;
    } else discard;
}