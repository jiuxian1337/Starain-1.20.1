#version 330 core

precision mediump float;

in vec2 uv;
out vec4 color;

uniform sampler2D uMask;
uniform vec2 uTexelSize;
uniform vec4 uColorA;
uniform vec4 uColorB;
uniform float uRadius;
uniform float uStrength;

const int DIRECTIONS = 24;
const int STEPS = 4;
const float TAU = 6.28318530718;

void main() {
    float center = texture(uMask, uv).a;
    float best = 0.0;

    for (int d = 0; d < DIRECTIONS; d++) {
        float a = TAU * float(d) / float(DIRECTIONS);
        vec2 dir = vec2(cos(a), sin(a));
        for (int s = 1; s <= STEPS; s++) {
            float t = float(s) / float(STEPS);
            float m = texture(uMask, uv + dir * uTexelSize * uRadius * t).a;
            best = max(best, m * (1.0 - t));
        }
    }

    float glow = clamp(best * (1.0 - center) * uStrength, 0.0, 1.0);
    if (glow <= 0.002) discard;

    vec4 tint = mix(uColorA, uColorB, uv.x);
    color = vec4(tint.rgb, glow * tint.a);
}
