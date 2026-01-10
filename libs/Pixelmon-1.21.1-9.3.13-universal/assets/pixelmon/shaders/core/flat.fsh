#version 150

#moj_import <fog.glsl>
#moj_import <light.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
uniform vec4 RenderColor;

in float vertexDistance;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec4 worldPosition;
in vec3 outputNormal;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }

    vec3 norm = normalize(cross(dFdx(worldPosition.xyz), dFdy(worldPosition.xyz)));
    norm = norm + (outputNormal - outputNormal);
    vec4 vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, norm.xyz, RenderColor);

    color *= vertexColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
    fragColor = color;
}
