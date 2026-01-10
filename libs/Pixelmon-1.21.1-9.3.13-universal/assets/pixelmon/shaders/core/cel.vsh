#version 330

#moj_import <light.glsl>
#moj_import <fog.glsl>

#define MAX_BONES 200

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 texCoord;
layout(location = 2) in vec3 inputNormal;
layout(location = 3) in vec4 BoneIDs;
layout(location = 4) in vec4 BoneWeights;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

uniform mat4 bones[MAX_BONES];
uniform mat4 WorldMatrix;
uniform mat3 WorldNormalMatrix;
uniform vec2 u_textureOffset;
uniform vec2 u_textureScale;
uniform float u_textureRotation;

out vec2 texCoord0;
out vec3 outputNormal;
out vec4 viewPos;
out vec4 worldPosition;

void main() {
    mat4 BoneTransform = bones[int(BoneIDs.x)] * BoneWeights.x;
    BoneTransform += bones[int(BoneIDs.y)] * BoneWeights.y;
    BoneTransform += bones[int(BoneIDs.z)] * BoneWeights.z;
    BoneTransform += bones[int(BoneIDs.w)] * BoneWeights.w;

    vec4 pos = BoneTransform * vec4(Position, 1.0);
    worldPosition = WorldMatrix * pos;
    gl_Position = ProjMat * ModelViewMat * worldPosition;
    vec4 normal = vec4(WorldNormalMatrix * ((BoneTransform * vec4(inputNormal, 0.0)).xyz), 1.0);

    float rcos = cos(u_textureRotation);
    float rsin = sin(u_textureRotation);
    float translationX = u_textureOffset.x;
    float translationY = u_textureOffset.y;
    float originalTranslationX = (0.75 * u_textureScale.x) * (-rcos + rsin + 1) + translationX;
    float originalTranslationY = ((-0.5 * u_textureScale.y) * (rsin - rcos + 1)) + 1 - translationY - u_textureScale.y;

    mat3 translation = mat3(1,0,0, 0,1,0, originalTranslationX, originalTranslationY, 1);
    mat3 rotation = mat3(
                            cos(u_textureRotation), sin(u_textureRotation), 0.0,
                            -sin(u_textureRotation),  cos(u_textureRotation), 0.0,
                            0.0,        0.0,          1.0
                        );
    mat3 scale = mat3(u_textureScale.x,0,0, 0,u_textureScale.y,0, 0,0,1);

    mat3 uvTransformMatrix = translation * rotation * scale;
    vec2 uvTransformed = ( uvTransformMatrix * vec3(texCoord.x, 1 - texCoord.y, 1) ).xy;

    texCoord0 = uvTransformed;
    outputNormal = normalize(normal.xyz);
    viewPos = gl_Position;
}
