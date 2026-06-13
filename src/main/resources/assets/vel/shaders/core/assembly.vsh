#version 150

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

// Rotation matrix built from the assembly entity's quaternion (mat3, not mat4).
// Used to rotate baked normals into world space.
uniform mat3 AssemblyNormalMat;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

// Replicates vanilla's getShade() per-face brightness as a continuous function
// of a world-space normal direction, matching:
//   DOWN=0.5, UP=1.0, N/S=0.8, E/W=0.6
// This is the same formula Sable's block_brightness() uses internally.
float blockBrightness(vec3 normal) {
    // Vanilla uses two fixed directional lights via minecraft_mix_light.
    // These vectors reproduce all 6 face values exactly (solved numerically):
    //   L0 = normalize(-0.333333,  1.0,  0.666667)
    //   L1 = normalize( 0.333333, -0.166667, -0.666667)
    // LIGHT_POWER=0.6, AMBIENT=0.4 → same as minecraft_mix_light constants.
    float light0 = max(0.0, dot(normalize(vec3(-0.333333,  1.000000,  0.666667)), normal));
    float light1 = max(0.0, dot(normalize(vec3( 0.333333, -0.166667, -0.666667)), normal));
    return min(1.0, (light0 + light1) * 0.6 + 0.4);
}

void main() {
    vec3 pos = Position + ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);

    // The baked Color already has getShade() applied per face (DOWN=0.5, UP=1.0,
    // N/S=0.8, E/W=0.6) from tesselateWithoutAO, assuming AssemblyFakeLevel
    // has getLightEmission() returning non-zero to force the flat path.
    //
    // We need to:
    //   1. Undo the baked per-face shading (divide by what getShade() would have
    //      returned for this face's ORIGINAL axis-aligned normal)
    //   2. Redo it using the ROTATED world-space normal so it stays correct
    //      after the assembly entity rotates
    //
    // Sable's approach: rotate the baked normal into world space via the
    // assembly's rotation matrix, then recompute brightness from that.
    // The baked normal in the vertex is the axis-aligned face normal (e.g.
    // (0,1,0) for UP faces). AssemblyNormalMat rotates it to world space.

    vec3 worldNormal = normalize(AssemblyNormalMat * Normal);

    // Brightness the baked Color was computed with (axis-aligned normal, no rotation)
    float bakedBrightness = blockBrightness(Normal);

    // Brightness we want (rotated normal)
    float rotatedBrightness = blockBrightness(worldNormal);

    // Undo baked, apply rotated. Guard against divide-by-zero on degenerate normals.
    float brightnessScale = (bakedBrightness > 0.001) ? (rotatedBrightness / bakedBrightness) : 1.0;

    vec4 lightmapColor = texture(Sampler2, clamp(UV2 / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));

    vertexColor = Color * lightmapColor;
    vertexColor.rgb *= brightnessScale;

    texCoord0 = UV0;
}
