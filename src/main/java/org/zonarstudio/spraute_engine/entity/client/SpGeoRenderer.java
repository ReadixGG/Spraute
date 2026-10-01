package org.zonarstudio.spraute_engine.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
//? if >=1.20.1 {
import org.joml.Matrix3f;
import org.joml.Matrix4f;
//?} else {
/*import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
*///?}
import org.zonarstudio.spraute_engine.core.math.SpMatrix4;
import org.zonarstudio.spraute_engine.core.math.SpVec3;
import org.zonarstudio.spraute_engine.core.model.*;

/**
 * Renders a {@link SpModelInstance} into Minecraft's vertex pipeline.
 * Handles Bedrock→Minecraft coordinate conversion:
 * Bedrock: +X=right, +Y=up, +Z=forward (player looks toward +Z).
 * Minecraft render: +X=left, +Y=up, +Z=forward after body yaw rotation.
 *
 * Face UV / winding follows GeckoLib's {@code GeoQuad} + {@code VertexSet} so
 * Blockbench per-face and box UVs match the in-game preview.
 */
public final class SpGeoRenderer {

    private SpGeoRenderer() {}

    public static void render(SpModelInstance instance, PoseStack poseStack,
                              VertexConsumer consumer, int packedLight, int packedOverlay,
                              float r, float g, float b, float a, String[] renderBones) {
        render(instance, poseStack, consumer, consumer, packedLight, packedOverlay,
                r, g, b, a, renderBones, 0);
    }

    public static void render(SpModelInstance instance, PoseStack poseStack,
                              VertexConsumer consumer, int packedLight, int packedOverlay,
                              float r, float g, float b, float a, String[] renderBones, int lod) {
        render(instance, poseStack, consumer, consumer, packedLight, packedOverlay,
                r, g, b, a, renderBones, lod);
    }

    /**
     * {@code solid} — closed cubes (may write depth). {@code thin} — planes / incomplete
     * cubes such as glasses and eyes; drawn after solid and sorted far-to-near.
     */
    public static void render(SpModelInstance instance, PoseStack poseStack,
                              VertexConsumer solid, VertexConsumer thin,
                              int packedLight, int packedOverlay,
                              float r, float g, float b, float a, String[] renderBones, int lod) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normalMat = poseStack.last().normal();
        SpGeoModel model = instance.getModel();
        if (model.baked != null && !model.baked.pages.isEmpty()) {
            renderBaked(instance, model.baked, pose, normalMat, solid, thin,
                    packedLight, packedOverlay, r, g, b, a, renderBones, lod);
            return;
        }

        for (SpBone bone : model.boneMap.values()) {
            if (renderBones != null && renderBones.length > 0) {
                if (!isBoneOrChildOf(bone, renderBones)) continue;
            }
            if (bone.cubes.isEmpty()) continue;

            SpMatrix4 world = instance.getBoneMatrix(bone.name);
            if (world == null) continue;

            for (SpCube cube : bone.cubes) {
                VertexConsumer dest = cube.faceUVs.size() == 6
                        && cube.size.x > 2f && cube.size.y > 2f && cube.size.z > 2f ? solid : thin;
                renderCube(cube, bone, world, model, pose, normalMat, dest,
                           packedLight, packedOverlay, r, g, b, a);
            }
        }
    }

    private static final ThreadLocal<int[]> FACE_ORDER = ThreadLocal.withInitial(() -> new int[64]);
    private static final ThreadLocal<float[]> FACE_KEYS = ThreadLocal.withInitial(() -> new float[64]);
    //? if >=1.20.1 {
    private static final org.joml.Vector4f POSE_SCRATCH = new org.joml.Vector4f();
    //?} else {
    /*private static final com.mojang.math.Vector4f POSE_SCRATCH = new com.mojang.math.Vector4f();
    *///?}

    private static void renderBaked(SpModelInstance instance, SpGeoBakedModel baked,
                                    Matrix4f pose, Matrix3f normalMat,
                                    VertexConsumer solid, VertexConsumer thin,
                                    int packedLight, int packedOverlay,
                                    float r, float g, float b, float a, String[] renderBones, int lod) {
        float cutoff = 0f;
        if (lod >= 2) {
            float radius = Math.max(.01f, baked.bounds.radius() / 16f);
            cutoff = lod == 2 ? radius * .025f : radius * .06f;
        }
        for (SpGeoBakedModel.Page page : baked.pages) {
            if (solid == thin) {
                emitPageFaces(instance, page, pose, normalMat, thin, packedLight, packedOverlay,
                        r, g, b, a, renderBones, cutoff, false, true, true);
                continue;
            }
            emitPageFaces(instance, page, pose, normalMat, solid, packedLight, packedOverlay,
                    r, g, b, a, renderBones, cutoff, true, false, false);
            emitPageFaces(instance, page, pose, normalMat, thin, packedLight, packedOverlay,
                    r, g, b, a, renderBones, cutoff, false, true, false);
        }
    }

    private static void emitPageFaces(SpModelInstance instance, SpGeoBakedModel.Page page,
                                      Matrix4f pose, Matrix3f normalMat, VertexConsumer consumer,
                                      int packedLight, int packedOverlay,
                                      float r, float g, float b, float a, String[] renderBones,
                                      float cutoff, boolean solidPass, boolean sortFarToNear,
                                      boolean allFaces) {
        int n = 0;
        int[] order = FACE_ORDER.get();
        float[] keys = FACE_KEYS.get();
        int faces = page.faceCount();
        if (order.length < faces) {
            order = new int[faces];
            keys = new float[faces];
            FACE_ORDER.set(order);
            FACE_KEYS.set(keys);
        }
        byte[] boneIndices = page.boneIndices();
        boolean[] cullSafe = page.faceCullSafe();
        for (int face = 0; face < faces; face++) {
            if (!allFaces && cullSafe[face] != solidPass) continue;
            if (cutoff > 0f && page.faceSizes()[face] < cutoff) continue;
            int first = page.faceFirstVertices()[face];
            int boneIndex = boneIndices[first] & 0xff;
            String boneName = page.bones().get(boneIndex);
            SpBone bone = instance.getModel().boneMap.get(boneName);
            if (renderBones != null && renderBones.length > 0
                    && (bone == null || !isBoneOrChildOf(bone, renderBones))) continue;
            SpMatrix4 world = instance.getBoneMatrix(boneName);
            if (world == null) continue;
            if (sortFarToNear) {
                float[] m = world.m;
                float[] c = page.faceCenters();
                int ci = face * 3;
                float x = c[ci], y = c[ci + 1], z = c[ci + 2];
                float px = m[0] * x + m[4] * y + m[8] * z + m[12];
                float py = m[1] * x + m[5] * y + m[9] * z + m[13];
                float pz = m[2] * x + m[6] * y + m[10] * z + m[14];
                keys[n] = poseDistSq(pose, -px / 16f, py / 16f, pz / 16f);
            }
            order[n++] = face;
        }
        if (n == 0) return;
        if (sortFarToNear && n > 1) sortByKeyDesc(order, keys, n);
        float[] positions = page.positions(), normals = page.normals(), uvs = page.uvs();
        for (int i = 0; i < n; i++) {
            int face = order[i];
            int first = page.faceFirstVertices()[face];
            String boneName = page.bones().get(boneIndices[first] & 0xff);
            SpMatrix4 world = instance.getBoneMatrix(boneName);
            if (world == null) continue;
            float[] m = world.m;
            for (int v = first; v < first + 4; v++) {
                int p = v * 3, t = v * 2;
                float x = positions[p], y = positions[p + 1], z = positions[p + 2];
                float px = m[0] * x + m[4] * y + m[8] * z + m[12];
                float py = m[1] * x + m[5] * y + m[9] * z + m[13];
                float pz = m[2] * x + m[6] * y + m[10] * z + m[14];
                float nx = normals[p], ny = normals[p + 1], nz = normals[p + 2];
                float tx = -(m[0] * nx + m[4] * ny + m[8] * nz);
                float ty = m[1] * nx + m[5] * ny + m[9] * nz;
                float tz = m[2] * nx + m[6] * ny + m[10] * nz;
                float inv = (float) (1.0 / Math.sqrt(Math.max(1e-12f, tx * tx + ty * ty + tz * tz)));
                consumer.vertex(pose, -px / 16f, py / 16f, pz / 16f)
                        .color(r, g, b, a).uv(uvs[t], uvs[t + 1])
                        .overlayCoords(packedOverlay).uv2(packedLight)
                        .normal(normalMat, tx * inv, ty * inv, tz * inv).endVertex();
            }
        }
    }

    private static float poseDistSq(Matrix4f pose, float x, float y, float z) {
        //? if >=1.20.1 {
        org.joml.Vector4f v = POSE_SCRATCH.set(x, y, z, 1f);
        pose.transform(v);
        return v.x * v.x + v.y * v.y + v.z * v.z;
        //?} else {
        /*POSE_SCRATCH.set(x, y, z, 1f);
        POSE_SCRATCH.transform(pose);
        float vx = POSE_SCRATCH.x();
        float vy = POSE_SCRATCH.y();
        float vz = POSE_SCRATCH.z();
        return vx * vx + vy * vy + vz * vz;
        *///?}
    }

    private static void sortByKeyDesc(int[] order, float[] keys, int n) {
        for (int i = 1; i < n; i++) {
            int idx = order[i];
            float key = keys[i];
            int j = i - 1;
            while (j >= 0 && keys[j] < key) {
                order[j + 1] = order[j];
                keys[j + 1] = keys[j];
                j--;
            }
            order[j + 1] = idx;
            keys[j + 1] = key;
        }
    }

    private static boolean isBoneOrChildOf(SpBone bone, String[] renderBones) {
        SpBone current = bone;
        while (current != null) {
            for (String rb : renderBones) {
                if (current.name.equalsIgnoreCase(rb)) return true;
            }
            current = current.parent;
        }
        return false;
    }

    private static void renderCube(SpCube cube, SpBone bone, SpMatrix4 boneWorld,
                                    SpGeoModel model,
                                    Matrix4f pose, Matrix3f normalMat,
                                    VertexConsumer consumer,
                                    int packedLight, int packedOverlay,
                                    float r, float g, float b, float a) {
        float inf = cube.inflate;

        // Axis-aligned corners in absolute Bedrock model space (before cube rotation).
        float x0 = cube.origin.x - inf;
        float y0 = cube.origin.y - inf;
        float z0 = cube.origin.z - inf;
        float x1 = x0 + cube.size.x + inf * 2f;
        float y1 = y0 + cube.size.y + inf * 2f;
        float z1 = z0 + cube.size.z + inf * 2f;

        // Paper-thin / coplanar cubes z-fight under Iris/Oculus shadow+TAA passes.
        // Nudge degenerate axes to a tiny thickness (model units; 0.025 ≈ 1/640 block).
        final float minExtent = 0.025f;
        if (x1 - x0 < minExtent) {
            float mid = (x0 + x1) * 0.5f;
            x0 = mid - minExtent * 0.5f;
            x1 = mid + minExtent * 0.5f;
        }
        if (y1 - y0 < minExtent) {
            float mid = (y0 + y1) * 0.5f;
            y0 = mid - minExtent * 0.5f;
            y1 = mid + minExtent * 0.5f;
        }
        if (z1 - z0 < minExtent) {
            float mid = (z0 + z1) * 0.5f;
            z0 = mid - minExtent * 0.5f;
            z1 = mid + minExtent * 0.5f;
        }

        float invTexW = 1f / model.textureWidth;
        float invTexH = 1f / model.textureHeight;

        for (var entry : cube.faceUVs.entrySet()) {
            SpCube.SpFace face = entry.getKey();
            SpFaceUV uv = entry.getValue();

            if (Math.abs(uv.uSize) < 0.001f && Math.abs(uv.vSize) < 0.001f) continue;

            // X-flip at render (Bedrock→MC) is equivalent to GeckoLib's origin X flip:
            // east/west UV faces sample the opposite side unless cube.mirror is set.
            SpCube.SpFace geomFace = cube.geometryFace(face, cube.boxUv);
            float[][] verts = getFaceVertices(geomFace, x0, y0, z0, x1, y1, z1);
            // Normal follows the physical side in Bedrock; mirror is handled by geometryFace.
            float[] fn = getFaceNormal(geomFace);

            // GeckoLib GeoQuad.build UV corners (pixel → 0..1), including the
            // default U-swap when the cube is not mirrored.
            float u = uv.u * invTexW;
            float v = uv.v * invTexH;
            float uWidth = (uv.u + uv.uSize) * invTexW;
            float vHeight = (uv.v + uv.vSize) * invTexH;
            if (!cube.mirror) {
                float tmp = uWidth;
                uWidth = u;
                u = tmp;
            }

            float[][] uvs = rotateUvs(u, v, uWidth, vHeight, uv.uvRotation);

            // Transform normal: cube rot → bone → model space → MC space
            SpVec3 nv = new SpVec3(fn[0], fn[1], fn[2]);
            cube.rotateDirection(nv);
            boneWorld.transformDirection(nv);
            // Bedrock→MC: negate X (Bedrock +X=right, MC +X=left after our 180° Y rotation)
            nv.x = -nv.x;
            nv.normalize();

            float toBlocks = 1f / 16f;

            for (int i = 0; i < 4; i++) {
                SpVec3 p = new SpVec3(verts[i][0], verts[i][1], verts[i][2]);
                cube.modelPointToBoneLocal(bone, p);
                boneWorld.transformPoint(p);

                // Convert Bedrock→MC coordinates:
                // Bedrock: +X=right, +Y=up, +Z=forward
                // MC (after 180° Y rot): +X=left, +Y=up, +Z=forward
                float mx = -p.x * toBlocks;
                float my = p.y * toBlocks;
                float mz = p.z * toBlocks;

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
     * GeckoLib {@code FaceUV.Rotation#rotateUvs}: returns 8 floats as 4 UV pairs
     * for vertex order top-leftish → … (matches {@link #getFaceVertices}).
     */
    private static float[][] rotateUvs(float u, float v, float uWidth, float vHeight, int rotationDeg) {
        int steps = ((rotationDeg % 360) + 360) % 360 / 90;
        float[] flat = switch (steps) {
            case 1 -> new float[]{uWidth, v, uWidth, vHeight, u, vHeight, u, v};           // 90 CW
            case 2 -> new float[]{uWidth, vHeight, u, vHeight, u, v, uWidth, v};           // 180
            case 3 -> new float[]{u, vHeight, u, v, uWidth, v, uWidth, vHeight};           // 270 CW
            default -> new float[]{u, v, uWidth, v, uWidth, vHeight, u, vHeight};          // 0
        };
        return new float[][]{
                {flat[0], flat[1]}, {flat[2], flat[3]}, {flat[4], flat[5]}, {flat[6], flat[7]}
        };
    }

    /**
     * Face vertices in Bedrock space, in GeckoLib {@code VertexSet} order
     * after converting their X-flipped cube back to Bedrock coordinates.
     * Order matches {@link #rotateUvs} (first vertex gets the first UV pair).
     */
    private static float[][] getFaceVertices(SpCube.SpFace face,
                                              float x0, float y0, float z0,
                                              float x1, float y1, float z1) {
        return switch (face) {
            // GeckoLib north (minZ) after X-unflip
            case NORTH -> new float[][]{
                    {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}
            };
            // GeckoLib south (maxZ) after X-unflip
            case SOUTH -> new float[][]{
                    {x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}
            };
            // GeckoLib west-quad (minX flipped) = Bedrock east
            case EAST -> new float[][]{
                    {x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}
            };
            // GeckoLib east-quad (maxX flipped) = Bedrock west
            case WEST -> new float[][]{
                    {x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}
            };
            case UP -> new float[][]{
                    {x1, y1, z1}, {x0, y1, z1}, {x0, y1, z0}, {x1, y1, z0}
            };
            case DOWN -> new float[][]{
                    {x1, y0, z0}, {x0, y0, z0}, {x0, y0, z1}, {x1, y0, z1}
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
