#version 330 core

precision mediump float;

in vec2 uv;
out vec4 color;

const int DIRECTIONS = 24;
const int STEPS = 4;
const int MAX_STOPS = 128;
const float TAU = 6.28318530718;

uniform sampler2D uMask;
uniform vec2 uTexelSize;
uniform vec3 uColors[MAX_STOPS];
uniform int uCount;
uniform float uAlpha;
uniform float uRadius;
uniform float uStrength;

void main() {
    float center = texture(uMask, uv).a;
    float bestFilled = 0.0;
    float bestEmpty = 0.0;

    for (int d = 0; d < DIRECTIONS; d++) {
        float a = TAU * float(d) / float(DIRECTIONS);
        vec2 dir = vec2(cos(a), sin(a));
        for (int s = 1; s <= STEPS; s++) {
            float t = float(s) / float(STEPS);
            float w = 1.0 - t;
            float m = texture(uMask, uv + dir * uTexelSize * uRadius * t).a;
            bestFilled = max(bestFilled, m * w);
            bestEmpty = max(bestEmpty, (1.0 - m) * w);
        }
    }

    float outer = bestFilled * (1.0 - center);
    float inner = bestEmpty * center;
    float glow = clamp((outer + inner) * uStrength, 0.0, 1.0);
    if (glow <= 0.002) discard;

    int last = max(uCount - 1, 1);
    float pos = clamp(uv.x, 0.0, 1.0) * float(last);
    int i0 = int(floor(pos));
    int i1 = min(i0 + 1, last);
    vec3 tint = mix(uColors[i0], uColors[i1], fract(pos));

    color = vec4(tint, glow * uAlpha);
}
