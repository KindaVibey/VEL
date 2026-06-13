#version 150

#moj_import <fog.glsl>
#moj_import <light.glsl>

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

// Rotation matrix built from the assembly entity's quaternion.
// Transforms face normals from local block space into world space
// so the dot product against the sun vector is meaningful.
uniform mat3 AssemblyNormalMat;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    vec3 pos = Position + ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);

    // Lightmap — same as vanilla solid.
    vec4 lightColor = minecraft_sample_lightmap(Sampler2, UV2);

    // Rotate normal into world space and dot against sun.
    // Sun direction: roughly from the upper south-east, matching MC's default
    // directional lighting angle. Normalized in the shader for correctness.
    vec3 worldNormal = normalize(AssemblyNormalMat * Normal);
    vec3 sunDir = normalize(vec3(0.2, 1.0, 0.4));
    float sunDot = max(dot(worldNormal, sunDir), 0.0);

    // Remap: faces pointing at sun are full bright, faces pointing away
    // bottom out at 0.4 so nothing is ever fully black.
    float shade = 0.4 + sunDot * 0.6;

    vertexColor = Color * lightColor;
    vertexColor.rgb *= shade;

    texCoord0 = UV0;
}
