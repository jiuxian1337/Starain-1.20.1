#version 330 core

precision mediump float;

in vec2 uv;
out vec4 color;

const int DIRECTIONS = 24;
const int STEPS = 6;
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

    float sumOuter = 0.0;
    float sumInner = 0.0;
    float sumWeight = 0.0;
    float peakOuter = 0.0;
    float peakInner = 0.0;

    for (int d = 0; d < DIRECTIONS; d++) {
        float a = TAU * (float(d) + 0.5) / float(DIRECTIONS);
        vec2 dir = vec2(cos(a), sin(a)) * uTexelSize * uRadius;
        for (int s = 1; s <= STEPS; s++) {
            float t = float(s) / float(STEPS);
            float inv = 1.0 - t;
            float w = inv * inv;
            float m = texture(uMask, uv + dir * t).a;
            float e = 1.0 - m;
            sumOuter += m * w;
            sumInner += e * w;
            sumWeight += w;
            peakOuter = max(peakOuter, m * inv);
            peakInner = max(peakInner, e * inv);
        }
    }

    float fieldOuter = sumOuter / max(sumWeight, 0.0001);
    float fieldInner = sumInner / max(sumWeight, 0.0001);

    float outer = mix(fieldOuter, peakOuter, 0.35) * (1.0 - center);
    float inner = mix(fieldInner, peakInner, 0.35) * center;

    float raw = (outer + inner) * uStrength;
    float glow = 1.0 - exp(-raw * 4.5);
    glow = glow * glow * (3.0 - 2.0 * glow);
    glow = clamp(glow, 0.0, 1.0);
    if (glow <= 0.003) discard;

    int last = max(uCount - 1, 1);
    float pos = clamp(uv.x, 0.0, 1.0) * float(last);
    int i0 = int(floor(pos));
    int i1 = min(i0 + 1, last);
    vec3 tint = mix(uColors[i0], uColors[i1], fract(pos));

    color = vec4(tint, glow * uAlpha);
}
