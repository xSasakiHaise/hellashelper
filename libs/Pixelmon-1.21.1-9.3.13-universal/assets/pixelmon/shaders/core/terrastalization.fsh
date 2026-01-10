#version 150

#moj_import <fog.glsl>
#moj_import <light.glsl>

#define M_PI 3.1415926535897932384626433832795

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
uniform vec4 RenderColor;
uniform float RealTime;

uniform float FresnelBias;
uniform float FresnelScale;
uniform float FresnelPower;
uniform vec4 FresnelColor;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 outputNormal;
in vec3 outputWorldPosition;
in vec3 viewDirection;
in float fresnel;

out vec4 fragColor;

float fresnelSchlick(float cosTheta, float R0) {
    return R0 * pow(1.0 - cosTheta, 5.0);
}

float randomNumber(float seed, float min, float max) {
    float number = fract(sin(dot(vec2(1.0, seed), vec2(12.9898, 78.233))) * 43758.5453);
    return min + (max - min) * number;
}

float frag() {
    return randomNumber(gl_PrimitiveID, 0.0f, 1.0f);
}

vec3 hsv2rgb(vec3 c)
{
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    float randomNumber = frag();
    vec3 randomColor = vec3(randomNumber, randomNumber, randomNumber);
    float basicReflectionAmount = dot(normalize(viewDirection), FresnelColor.rgb * randomNumber);
    float cycleSpeed = 0.5;
    float currentX = basicReflectionAmount + (cycleSpeed * RealTime);
    float cycle = sin(((2 * randomNumber) * randomNumber) + (currentX * M_PI));
    float reducedCycle = (cycle * 2) - 1;

    vec3 hsvColor = vec3(randomColor.r, 1.0, 1.0);
    vec3 rgbColor = FresnelColor.rgb * randomNumber;

    float colorReflectionStrength = 0.25 * reducedCycle;
    vec3 lerped = mix(vec3(reducedCycle), rgbColor, vec3(colorReflectionStrength));

    float reflectionStrength = 1.0;
    lerped = lerped * vec3(reflectionStrength);

    if (lerped.r == 0 && lerped.g == 0 && lerped.b == 0) {
        fragColor = vec4((FresnelColor * fresnel).rgb + vec3(1.0), 0.15);
    } else {
        fragColor = vec4((FresnelColor * fresnel).rgb + lerped, 0.5);
    }
}
