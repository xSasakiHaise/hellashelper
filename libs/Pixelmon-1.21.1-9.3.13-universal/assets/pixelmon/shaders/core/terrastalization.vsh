#version 330

#moj_import <light.glsl>
#moj_import <fog.glsl>

#define MAX_BONES 200

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 texCoord;
layout(location = 2) in vec3 inputNormal;
layout(location = 3) in vec4 BoneIDs;
layout(location = 4) in vec4 BoneWeights;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform int FogShape;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

uniform vec4 RenderColor;
uniform mat4 bones[MAX_BONES];
uniform mat4 WorldMatrix;
uniform mat3 WorldNormalMatrix;
uniform vec2 u_textureOffset;
uniform vec2 u_textureScale;
uniform float u_textureRotation;
uniform ivec2 overlay;
uniform ivec2 lighting;

uniform float FresnelBias;
uniform float FresnelScale;
uniform float FresnelPower;
uniform vec4 FresnelColor;

out float vertexDistance;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out vec3 outputNormal;
out vec3 outputWorldPosition;
out vec3 viewDirection;
out float fresnel;

void main() {
    mat4 BoneTransform = bones[int(BoneIDs.x)] * BoneWeights.x;
    BoneTransform += bones[int(BoneIDs.y)] * BoneWeights.y;
    BoneTransform += bones[int(BoneIDs.z)] * BoneWeights.z;
    BoneTransform += bones[int(BoneIDs.w)] * BoneWeights.w;

    vec4 pos = BoneTransform * vec4(Position, 1.0);
    vec4 worldPosition = WorldMatrix * pos;
    gl_Position = ProjMat * ModelViewMat * WorldMatrix * pos;
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

    vertexDistance = fog_distance(IViewRotMat * gl_Position.xyz, FogShape);
    lightMapColor = texelFetch(Sampler2, lighting / 16, 0);
    overlayColor = texelFetch(Sampler1, overlay, 0);
    texCoord0 = uvTransformed;
    outputNormal = normal.xyz;
    outputWorldPosition = worldPosition.xyz;
    viewDirection = normalize(abs((IViewRotMat * vec3(3.0, 0.0, 0.0)) - (IViewRotMat * vec3(0.0))));

    vec3 fresnelNormal = normalize(normal.xyz);
    vec3 i = normalize(worldPosition.xyz - (IViewRotMat * vec3(0.0)));
    fresnel = FresnelBias + FresnelScale * pow(1.0 + dot(i, fresnelNormal), FresnelPower);
}
