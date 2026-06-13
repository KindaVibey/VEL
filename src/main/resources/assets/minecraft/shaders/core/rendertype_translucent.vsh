#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;

// Assembly dynamic shading — 0.0 = off (vanilla), 1.0 = on
uniform float AssemblyEnableNormalLighting;
uniform mat3 AssemblyNormalMat;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    vec3 pos = Position + ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;

    // Rotate the face normal by the assembly's orientation, then dot against
    // a fixed sun vector. mix() means this is a no-op when the flag is 0.
    vec3 worldNormal = normalize(AssemblyNormalMat * Normal);
    float sunDot = clamp(dot(worldNormal, normalize(vec3(0.2, 1.0, 0.4))), 0.0, 1.0);
    float shade = mix(1.0, 0.4 + sunDot * 0.6, AssemblyEnableNormalLighting);
    vertexColor.rgb *= shade;
}
