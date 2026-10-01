package org.zonarstudio.spraute_engine.client.editor.scene;

import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.core.math.SpVec3;
import org.zonarstudio.spraute_engine.core.model.SpBone;
import org.zonarstudio.spraute_engine.core.model.SpCube;
import org.zonarstudio.spraute_engine.core.model.SpGeoModel;
import org.zonarstudio.spraute_engine.core.model.SpModelInstance;
import org.zonarstudio.spraute_engine.entity.client.SpModelCache;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.SceneTransform;

/**
 * Picks individual bones of an NPC/MODEL preview with a mouse ray.
 * The ray is transformed into model-pixel space (the inverse of the preview
 * transform chain: translate → rotY(180−ry) → rotX → rotZ → scale → бedrock
 * X-mirror → 1/16), then tested against per-cube AABBs of each posed bone.
 */
public final class EditorBonePicker {

    private EditorBonePicker() {}

    /** World position of a posed bone pivot in the editor preview. */
    public static Vec3 boneWorldPosition(SceneNode node, String boneName) {
        if (node == null || boneName == null) return null;
        SceneDocument doc = SceneEditorSession.document();
        if (doc == null) return null;
        SpModelInstance instance = ScenePreviewManager.instanceFor(node.id);
        if (instance == null) return null;
        var matrix = instance.getBoneMatrix(boneName);
        SceneTransform world = doc.worldTransform(node.id);
        if (matrix == null || world == null) return null;

        double[] p = {
                -matrix.m[12] / 16.0 * world.sx,
                 matrix.m[13] / 16.0 * world.sy,
                 matrix.m[14] / 16.0 * world.sz
        };
        rotZ(p, Math.toRadians(world.rz));
        rotX(p, Math.toRadians(world.rx));
        rotY(p, Math.toRadians(180.0 - world.ry));
        return new Vec3(world.px + p[0], world.py + p[1], world.pz + p[2]);
    }

    /** @return bone name under the cursor, or null. */
    public static String pickBone(String nodeId, double guiMouseX, double guiMouseY) {
        if (nodeId == null) return null;
        SceneDocument doc = SceneEditorSession.document();
        if (doc == null) return null;
        SceneNode node = doc.findNode(nodeId);
        if (node == null || (node.type != SceneNodeType.NPC && node.type != SceneNodeType.MODEL)) return null;
        if (node.model == null || node.model.isEmpty()) return null;
        SpGeoModel model = SpModelCache.getOrLoad(node.model);
        if (model.boneMap.isEmpty()) return null;

        SpModelInstance instance = ScenePreviewManager.instanceFor(nodeId);
        if (instance == null) {
            instance = new SpModelInstance(model);
            instance.computeTransforms();
        }

        SceneTransform world = doc.worldTransform(nodeId);
        Vec3[] ray = EditorGizmo.mouseRay(guiMouseX, guiMouseY);
        double[] o = toModelSpace(ray[0], world, true);
        double[] d = toModelSpace(ray[0].add(ray[1]), world, true);
        d[0] -= o[0];
        d[1] -= o[1];
        d[2] -= o[2];

        String best = null;
        double bestT = Double.MAX_VALUE;
        for (SpBone bone : model.boneMap.values()) {
            if (bone.cubes.isEmpty()) continue;
            var boneWorld = instance.getBoneMatrix(bone.name);
            if (boneWorld == null) continue;
            for (SpCube cube : bone.cubes) {
                double[] box = cubeModelBounds(cube, bone, boneWorld);
                double t = rayAabb(o, d, box);
                if (t >= 0 && t < bestT) {
                    bestT = t;
                    best = bone.name;
                }
            }
        }
        return best;
    }

    /** Model-space AABB (Bedrock pixels) of one posed cube: bounds of its 8 transformed corners. */
    static double[] cubeModelBounds(SpCube cube, SpBone bone,
                                    org.zonarstudio.spraute_engine.core.math.SpMatrix4 boneWorld) {
        float inf = cube.inflate;
        float x0 = cube.origin.x - inf;
        float y0 = cube.origin.y - inf;
        float z0 = cube.origin.z - inf;
        float x1 = x0 + cube.size.x + inf * 2f;
        float y1 = y0 + cube.size.y + inf * 2f;
        float z1 = z0 + cube.size.z + inf * 2f;

        double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < 8; i++) {
            SpVec3 v = new SpVec3(
                    (i & 1) == 0 ? x0 : x1,
                    (i & 2) == 0 ? y0 : y1,
                    (i & 4) == 0 ? z0 : z1);
            cube.modelPointToBoneLocal(bone, v);
            boneWorld.transformPoint(v);
            box[0] = Math.min(box[0], v.x);
            box[1] = Math.min(box[1], v.y);
            box[2] = Math.min(box[2], v.z);
            box[3] = Math.max(box[3], v.x);
            box[4] = Math.max(box[4], v.y);
            box[5] = Math.max(box[5], v.z);
        }
        return box;
    }

    /**
     * World point → model-pixel space.
     * Forward chain: w = P + Ry(180−ry)·Rx(rx)·Rz(rz)·S·(−vx, vy, vz)/16.
     */
    private static double[] toModelSpace(Vec3 w, SceneTransform world, boolean point) {
        double x = w.x, y = w.y, z = w.z;
        if (point) {
            x -= world.px;
            y -= world.py;
            z -= world.pz;
        }
        // Inverse rotations in reverse order.
        double[] p = {x, y, z};
        rotY(p, -Math.toRadians(180.0 - world.ry));
        rotX(p, -Math.toRadians(world.rx));
        rotZ(p, -Math.toRadians(world.rz));
        double sx = world.sx == 0 ? 1 : world.sx;
        double sy = world.sy == 0 ? 1 : world.sy;
        double sz = world.sz == 0 ? 1 : world.sz;
        p[0] /= sx;
        p[1] /= sy;
        p[2] /= sz;
        // Undo Bedrock→MC mirror and pixel scale.
        p[0] = -p[0] * 16.0;
        p[1] = p[1] * 16.0;
        p[2] = p[2] * 16.0;
        return p;
    }

    private static void rotY(double[] p, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        double x = p[0] * c + p[2] * s;
        double z = -p[0] * s + p[2] * c;
        p[0] = x;
        p[2] = z;
    }

    private static void rotX(double[] p, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        double y = p[1] * c - p[2] * s;
        double z = p[1] * s + p[2] * c;
        p[1] = y;
        p[2] = z;
    }

    private static void rotZ(double[] p, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        double x = p[0] * c - p[1] * s;
        double y = p[0] * s + p[1] * c;
        p[0] = x;
        p[1] = y;
    }

    /** Slab test; returns entry distance or −1. */
    private static double rayAabb(double[] o, double[] d, double[] box) {
        double tMin = 0, tMax = Double.MAX_VALUE;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(d[axis]) < 1e-9) {
                if (o[axis] < box[axis] || o[axis] > box[axis + 3]) return -1;
                continue;
            }
            double inv = 1.0 / d[axis];
            double t1 = (box[axis] - o[axis]) * inv;
            double t2 = (box[axis + 3] - o[axis]) * inv;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return -1;
        }
        return tMin;
    }
}
