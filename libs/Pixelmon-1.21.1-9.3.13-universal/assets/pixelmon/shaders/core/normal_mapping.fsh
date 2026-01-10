#version 150

#moj_import <fog.glsl>
#moj_import <light.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler6;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 worldPosition;
in vec3 outputNormal;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);

    if (color.a < 0.1) {
        discard;
    }

    color *= vertexColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
    fragColor = color;

    vec3 calculated_tangent = dFdx(worldPosition);
    vec3 calculated_bitangent = dFdy(worldPosition);
    mat3 TBN = mat3(normalize(calculated_tangent), normalize(calculated_bitangent), outputNormal);

    vec3 normalRGB = texture(Sampler6, texCoord0).rgb;
    normalRGB = normalRGB * 2.0 - 1.0;
    normalRGB = normalize(TBN * normalRGB);

    fragColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normalRGB, fragColor);
}
