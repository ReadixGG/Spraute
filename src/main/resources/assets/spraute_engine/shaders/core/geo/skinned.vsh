#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 BoneMatrices[48];

out vec2 texCoord;
out vec3 viewNormal;

void main() {
    int boneIndex = clamp(int(floor(Color.r * 255.0 + 0.5)), 0, 47);
    mat4 skin = BoneMatrices[boneIndex];
    vec4 skinnedPosition = skin * vec4(Position, 1.0);
    gl_Position = ProjMat * ModelViewMat * skinnedPosition;
    viewNormal = normalize(transpose(inverse(mat3(ModelViewMat * skin))) * Normal);
    texCoord = UV0;
}
