#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec3 Light0_Direction;

in vec4 viewPos;
in vec2 texCoord0;
in vec3 outputNormal;
in vec3 lightDirection;
in vec4 worldPosition;

out vec4 fragColor;

void main() {
    vec3 lightDir = normalize(vec3(0,0,0) - worldPosition.xyz);

    // Cel shading steps
    float diff = max(dot(outputNormal, lightDir), 0.0);
    float intensity = 5.0;
    diff = floor(diff * intensity) / intensity;

    // Specular lighting
    vec3 viewDir = normalize(viewPos.xyz - worldPosition.xyz).xyz;
    vec3 reflectDir = reflect(-Light0_Direction, outputNormal);
    float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32.0);

    // Combine results
    vec3 color = vec3(1.0, 1.0, 1.0);
    vec3 ambient = 0.1 * color;
    vec3 diffuse = diff * color;
    vec3 specular = spec * vec3(0.5);
    vec4 textureColor = texture(Sampler0, texCoord0);

    fragColor = vec4((ambient + diffuse + specular) * textureColor.rgb, textureColor.a);
}
