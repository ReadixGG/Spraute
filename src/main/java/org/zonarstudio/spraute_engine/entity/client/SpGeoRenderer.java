package org.zonarstudio.spraute_engine.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import org.zonarstudio.spraute_engine.core.math.SpMatrix4;
import org.zonarstudio.spraute_engine.core.math.SpVec3;
import org.zonarstudio.spraute_engine.core.model.*;

/**
 * Renders a {@link SpModelInstance} into Minecraft's vertex pipeline.
 * Handles Bedrock→Minecraft coordinate conversion:
 * Bedrock: +X=right, +Y=up, +Z=forward (player looks toward +Z).
 * Minecraft render: +X=left, +Y=up, +Z=forward after body yaw rotation.
 *
 * The renderer expects the PoseStack to already include body yaw rotation.
 * It applies the Bedrock→MC axis flip internally.
 */
public final class SpGeoRenderer {

    private SpGeoRenderer() {}

    public static void render(SpModelInstance instance, PoseStack poseStack,
                              VertexConsumer consumer, int packedLight, int packedOverlay,
                              float r, float g, float b, float a) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normalMat = poseStack.last().normal();
        SpGeoModel model = instance.getModel();

        for (SpBone bone : model.boneMap.values()) {
            if (bone.cubes.isEmpty()) continue;

            SpMatrix4 world = instance.getBoneMatrix(bone.name);
            if (world == null) continue;

            for (SpCube cube : bone.cubes) {
                renderCube(cube, bone, world, model, pose, normalMat, consumer,
                           packedLight, packedOverlay, r, g, b, a);
            }
        }
    }

    private static void renderCube(SpCube cube, SpBone bone, SpMatrix4 boneWorld,
                                    SpGeoModel model,
                                    Matrix4f pose, Matrix3f normalMat,
                                    VertexConsumer consumer,
                                    int packedLight, int packedOverlay,
                                    float r, float g, float b, float a) {
        float inf = cube.inflate;

        // Cube coords in bone-local space (Bedrock pixels, 16px = 1 block).
        float x0 = cube.origin.x - inf - bone.pivot.x;
        float y0 = cube.origin.y - inf - bone.pivot.y;
        float z0 = cube.origin.z - inf - bone.pivot.z;
        float x1 = x0 + cube.size.x + inf * 2f;
        float y1 = y0 + cube.size.y + inf * 2f;
        float z1 = z0 + cube.size.z + inf * 2f;

        float invTexW = 1f / model.textureWidth;
        float invTexH = 1f / model.textureHeight;

        for (var entry : cube.faceUVs.entrySet()) {
            SpCube.SpFace face = entry.getKey();
            SpFaceUV uv = entry.getValue();

            if (Math.abs(uv.uSize) < 0.001f && Math.abs(uv.vSize) < 0.001f) continue;

            // 4 vertices in bone-local Bedrock space, CCW winding when viewed from outside
            float[][] verts = getFaceVertices(face, x0, y0, z0, x1, y1, z1);
            float[] fn = getFaceNormal(face);

            // Compute normalized UV rect (handle negative uv_size for mirroring)
            float texU0 = uv.u * invTexW;
            float texV0 = uv.v * invTexH;
            float texU1 = (uv.u + uv.uSize) * invTexW;
            float texV1 = (uv.v + uv.vSize) * invTexH;

            // UV corners mapped to quad vertices.
            // Bedrock per-face UV: (u,v) is top-left corner, uv_size goes right and down.
            // Vertex order is CCW from bottom-left when looking at the face:
            //   v0=bottom-right, v1=bottom-left, v2=top-left, v3=top-right
            // UV mapping: v0=(u1,v1), v1=(u0,v1), v2=(u0,v0), v3=(u1,v0)
            float[][] uvs = faceUVs(face, texU0, texV0, texU1, texV1);

            // Transform normal: bone-local → model space → MC space
            // bone world matrix rotates the normal, then we negate X for Bedrock→MC mirror
            SpVec3 nv = new SpVec3(fn[0], fn[1], fn[2]);
            boneWorld.transformDirection(nv);
            // Bedrock→MC: negate X (Bedrock +X=right, MC +X=left after our 180° Y rotation)
            nv.x = -nv.x;
            nv.normalize();

            float toBlocks = 1f / 16f;

            for (int i = 0; i < 4; i++) {
                // Transform vertex: bone-local → model space (Bedrock pixels)
                SpVec3 v = new SpVec3(verts[i][0], verts[i][1], verts[i][2]);
                boneWorld.transformPoint(v);

                // Convert Bedrock→MC coordinates:
                // Bedrock: +X=right, +Y=up, +Z=forward
                // MC (after 180° Y rot): +X=left, +Y=up, +Z=forward
                // So just negate X.
                float mx = -v.x * toBlocks;
                float my = v.y * toBlocks;
                float mz = v.z * toBlocks;

                consumer.vertex(pose, mx, my, mz)
                        .color(r, g, b, a)
                        .uv(uvs[i][0], uvs[i][1])
                        .overlayCoords(packedOverlay)
                        .uv2(packedLight)
                        .normal(normalMat, nv.x, nv.y, nv.z)
                        .endVertex();
            }
        }
    }

    /**
     * 4 vertices in CCW order (as seen from outside the face), in bone-local Bedrock space.
     * Because we later negate X, the winding will be reversed to CW in MC space,
     * but Minecraft's entity shaders render both sides (NoCull), so this is fine.
     * We provide them in the order that, after X-negate, gives correct CW for MC's back-face.
     */
    private static float[][] getFaceVertices(SpCube.SpFace face,
                                              float x0, float y0, float z0,
                                              float x1, float y1, float z1) {
        // After X-negate, CCW in Bedrock becomes CW in MC.
        // MC entity shaders are NoCull so both sides render, but for correct normals
        // and UV we want the "outside" face. Since we negate X, we reverse winding here
        // to compensate (CW in Bedrock → CCW in MC after X-negate).
        return switch (face) {
            case NORTH -> new float[][]{ // -Z face
                {x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}
            };
            case SOUTH -> new float[][]{ // +Z face
                {x1, y0, z1}, {x0, y0, z1}, {x0, y1, z1}, {x1, y1, z1}
            };
            case EAST -> new float[][]{ // +X face
                {x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}
            };
            case WEST -> new float[][]{ // -X face
                {x0, y0, z1}, {x0, y0, z0}, {x0, y1, z0}, {x0, y1, z1}
            };
            case UP -> new float[][]{ // +Y face
                {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}
            };
            case DOWN -> new float[][]{ // -Y face
                {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}
            };
        };
    }

    /**
     * Map UV rect to the 4 vertices of each face.
     * Bedrock UV: (u,v) = top-left of texture region, uv_size = (width, height).
     * Vertex order from getFaceVertices: bottom-left, bottom-right, top-right, top-left
     * (when looking at the face from outside, after X-negate).
     */
    private static float[][] faceUVs(SpCube.SpFace face, float u0, float v0, float u1, float v1) {
        // For all side faces (north, south, east, west):
        //   v0 = bottom-left  → (u0, v1)
        //   v1 = bottom-right → (u1, v1)
        //   v2 = top-right    → (u1, v0)
        //   v3 = top-left     → (u0, v0)
        // For UP/DOWN the mapping is different because they're horizontal faces.
        return switch (face) {
            case NORTH, SOUTH -> new float[][]{
                {u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}
            };
            case EAST, WEST -> new float[][]{
                {u1, v1}, {u0, v1}, {u0, v0}, {u1, v0}
            };
            case UP -> new float[][]{
                {u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}
            };
            case DOWN -> new float[][]{
                {u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}
            };
        };
    }

    private static float[] getFaceNormal(SpCube.SpFace face) {
        return switch (face) {
            case NORTH -> new float[]{0, 0, -1};
            case SOUTH -> new float[]{0, 0, 1};
            case EAST  -> new float[]{1, 0, 0};
            case WEST  -> new float[]{-1, 0, 0};
            case UP    -> new float[]{0, 1, 0};
            case DOWN  -> new float[]{0, -1, 0};
        };
    }
}
