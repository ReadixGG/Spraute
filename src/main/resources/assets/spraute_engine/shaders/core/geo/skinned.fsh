#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;
uniform vec4 TintColor;
uniform vec2 LightUv;
uniform vec4 OverlayColor;
uniform float AlphaCutoff;

in vec2 texCoord;
in vec3 viewNormal;

out vec4 fragColor;

void main() {
    vec4 sampled = texture(Sampler0, texCoord);
    float alpha = sampled.a * TintColor.a;
    if (alpha <= AlphaCutoff) {
        discard;
    }

    vec3 lightDirection = normalize(vec3(0.2, 1.0, 0.35));
    float diffuse = 0.55 + 0.45 * max(dot(normalize(viewNormal), lightDirection), 0.0);
    vec3 lightColor = texture(Sampler2, LightUv).rgb;
    vec3 color = sampled.rgb * TintColor.rgb * lightColor * diffuse;
    color = mix(color, OverlayColor.rgb, OverlayColor.a);
    fragColor = vec4(color, alpha);
}
