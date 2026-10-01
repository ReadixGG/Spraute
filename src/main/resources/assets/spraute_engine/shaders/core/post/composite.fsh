#version 150

uniform sampler2D Sampler0;
uniform vec2 InSize;
uniform float Exposure;
uniform float Contrast;
uniform float Saturation;
uniform float Temperature;
uniform float Tint;
uniform vec3 Filter;
uniform float Vignette;
uniform float VignetteSmooth;
uniform float BloomIntensity;
uniform float BloomThreshold;
uniform float Chromatic;
uniform float Grain;
uniform float Sharpen;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// Stable in [0,1]. The old sin(dot(uv*Time)) hash loses GPU precision toward
// the top of the screen, so grain only showed up along the bottom edge.
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec2 px = 1.0 / max(InSize, vec2(1.0));
    vec2 uv = texCoord;
    vec3 color;
    if (Chromatic > 0.00005) {
        vec2 dir = (uv - 0.5) * Chromatic;
        color = vec3(
            texture(Sampler0, uv + dir).r,
            texture(Sampler0, uv).g,
            texture(Sampler0, uv - dir).b
        );
    } else {
        color = texture(Sampler0, uv).rgb;
    }

    vec3 acc = color;
    if (BloomIntensity > 0.001 || Sharpen > 0.001) {
        acc = texture(Sampler0, uv).rgb;
        acc += texture(Sampler0, uv + px * vec2( 1.0,  0.0)).rgb;
        acc += texture(Sampler0, uv + px * vec2(-1.0,  0.0)).rgb;
        acc += texture(Sampler0, uv + px * vec2( 0.0,  1.0)).rgb;
        acc += texture(Sampler0, uv + px * vec2( 0.0, -1.0)).rgb;
        acc += texture(Sampler0, uv + px * vec2( 1.0,  1.0)).rgb;
        acc += texture(Sampler0, uv + px * vec2(-1.0,  1.0)).rgb;
        acc += texture(Sampler0, uv + px * vec2( 1.0, -1.0)).rgb;
        acc += texture(Sampler0, uv + px * vec2(-1.0, -1.0)).rgb;
        acc /= 9.0;
    }

    if (BloomIntensity > 0.001) {
        float brightness = luma(acc);
        vec3 bright = acc * max((brightness - BloomThreshold) / max(brightness, 0.0001), 0.0);
        color += bright * BloomIntensity;
    }
    if (Sharpen > 0.001) {
        color += (color - acc) * Sharpen;
    }

    color *= exp2(Exposure);
    color = (color - 0.5) * (1.0 + Contrast) + 0.5;
    float g = luma(color);
    color = mix(vec3(g), color, 1.0 + Saturation);
    color.r += Temperature * 0.08;
    color.b -= Temperature * 0.08;
    color.g += Tint * 0.08;
    color *= Filter;

    if (Vignette > 0.001) {
        vec2 vc = uv * 2.0 - 1.0;
        float d = length(vc);
        float vig = smoothstep(VignetteSmooth, 1.2, d);
        color *= 1.0 - vig * Vignette;
    }

    if (Grain > 0.001) {
        vec2 gp = uv * InSize + vec2(fract(Time * 1.03), fract(Time * 1.17)) * 64.0;
        float n = hash12(gp);
        color += (n - 0.5) * Grain;
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
