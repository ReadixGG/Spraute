package org.sprautestudio.spraute_engine.core.model;

import org.sprautestudio.spraute_engine.core.math.SpMatrix4;
import org.sprautestudio.spraute_engine.core.math.SpQuaternion;
import org.sprautestudio.spraute_engine.core.math.SpVec3;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Per-entity runtime state for a {@link SpGeoModel}.
 * Holds computed world matrices and animated transforms for each bone.
 * The model definition itself stays immutable in the cache.
 */
public final class SpModelInstance {
    private final SpGeoModel model;
    /** Per-bone world matrices, keyed by bone name. */
    public final Map<String, SpMatrix4> boneWorldMatrices = new LinkedHashMap<>();
    /** Per-bone animated rotation (degrees). */
    public final Map<String, SpVec3> boneAnimRotation = new LinkedHashMap<>();
    /** Per-bone animated position offset. */
    public final Map<String, SpVec3> boneAnimPosition = new LinkedHashMap<>();
    /** Per-bone animated scale multiplier (1 = rest pose). */
    public final Map<String, SpVec3> boneAnimScale = new LinkedHashMap<>();
    private final Map<String, SpMatrix4> interpolationFrom = new LinkedHashMap<>();
    private final Map<String, SpMatrix4> interpolationTo = new LinkedHashMap<>();
    /** {@code bone} / {@code Body} → canonical geo name. */
    private final Map<String, String> boneByLower = new HashMap<>();
    private final SpMatrix4 identity = new SpMatrix4();
    private final SpMatrix4 local = new SpMatrix4();
    private final SpQuaternion rotation = new SpQuaternion();

    public SpModelInstance(SpGeoModel model) {
        this.model = model;
        for (String name : model.boneMap.keySet()) {
            boneWorldMatrices.put(name, new SpMatrix4());
            boneAnimRotation.put(name, new SpVec3());
            boneAnimPosition.put(name, new SpVec3());
            boneAnimScale.put(name, new SpVec3(1f, 1f, 1f));
            interpolationFrom.put(name, new SpMatrix4());
            interpolationTo.put(name, new SpMatrix4());
            boneByLower.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
        }
    }

    /** Clip bone names often differ only by case from geo ({@code leftArm} vs {@code LeftArm}). */
    public SpVec3 animRotation(String boneName) {
        return lookupAnim(boneAnimRotation, boneName);
    }

    public SpVec3 animPosition(String boneName) {
        return lookupAnim(boneAnimPosition, boneName);
    }

    public SpVec3 animScale(String boneName) {
        return lookupAnim(boneAnimScale, boneName);
    }

    private SpVec3 lookupAnim(Map<String, SpVec3> map, String boneName) {
        if (boneName == null || boneName.isEmpty()) return null;
        SpVec3 exact = map.get(boneName);
        if (exact != null) return exact;
        String canonical = boneByLower.get(boneName.toLowerCase(Locale.ROOT));
        return canonical == null ? null : map.get(canonical);
    }

    public SpGeoModel getModel() {
        return model;
    }

    /** Reset all animated transforms to defaults. */
    public void resetAnims() {
        for (SpVec3 rot : boneAnimRotation.values()) rot.set(0, 0, 0);
        for (SpVec3 pos : boneAnimPosition.values()) pos.set(0, 0, 0);
        for (SpVec3 scale : boneAnimScale.values()) scale.set(1, 1, 1);
    }

    /** Compute world matrices for entire skeleton. Call after setting anim transforms. */
    public void computeTransforms() {
        identity.identity();
        for (SpBone root : model.rootBones) {
            computeRecursive(root, identity);
        }
    }

    private void computeRecursive(SpBone bone, SpMatrix4 parentWorld) {
        SpVec3 animRot = boneAnimRotation.get(bone.name);
        SpVec3 animPos = boneAnimPosition.get(bone.name);
        SpVec3 animScale = boneAnimScale.get(bone.name);

        // Blockbench/GeckoLib bake into an X-mirrored bone space with Euler (−X,−Y,+Z).
        // We keep Bedrock pivots and mirror X only at vertex output, so rotations must be the
        // conjugation F·R·F (F = diag(−1,1,1)) → (−X, +Y, −Z). File Euler stays unchanged
        // in the animation parser (no per-bone hacks).
        float rx = bone.rotation.x + (animRot != null ? animRot.x : 0);
        float ry = bone.rotation.y + (animRot != null ? animRot.y : 0);
        float rz = bone.rotation.z + (animRot != null ? animRot.z : 0);
        float rotX = -rx;
        float rotY =  ry;
        float rotZ = -rz;

        float offX = (animPos != null ? animPos.x : 0);
        float offY = (animPos != null ? animPos.y : 0);
        float offZ = (animPos != null ? animPos.z : 0);

        // Bedrock bone transform: pivots are in absolute model space.
        // To get bone-local transform relative to parent:
        // local = Translate(pivot - parentPivot + animOffset) * Rotate(euler)
        float parentPivotX = bone.parent != null ? bone.parent.pivot.x : 0;
        float parentPivotY = bone.parent != null ? bone.parent.pivot.y : 0;
        float parentPivotZ = bone.parent != null ? bone.parent.pivot.z : 0;

        float localX = bone.pivot.x - parentPivotX + offX;
        float localY = bone.pivot.y - parentPivotY + offY;
        float localZ = bone.pivot.z - parentPivotZ + offZ;

        float scaleX = animScale != null ? animScale.x : 1f;
        float scaleY = animScale != null ? animScale.y : 1f;
        float scaleZ = animScale != null ? animScale.z : 1f;

        rotation.setEulerDeg(rotX, rotY, rotZ);
        local.compose(localX, localY, localZ, rotation, scaleX, scaleY, scaleZ);

        SpMatrix4 world = boneWorldMatrices.get(bone.name);
        if (world == null) {
            world = new SpMatrix4();
            boneWorldMatrices.put(bone.name, world);
        }
        world.set(parentWorld).mul(local);

        for (SpBone child : bone.children) {
            computeRecursive(child, world);
        }
    }

    /** Get world matrix for a named bone. */
    public SpMatrix4 getBoneMatrix(String boneName) {
        return boneWorldMatrices.get(boneName);
    }

    /** Capture the currently displayed pose before a throttled animation sample. */
    public void captureInterpolationFrom() {
        for (var e : boneWorldMatrices.entrySet()) interpolationFrom.get(e.getKey()).set(e.getValue());
    }

    /** Capture a freshly computed target pose. */
    public void captureInterpolationTarget() {
        for (var e : boneWorldMatrices.entrySet()) interpolationTo.get(e.getKey()).set(e.getValue());
    }

    /** Allocation-free matrix interpolation for distant animation LOD. */
    public void applyInterpolation(float alpha) {
        float t=Math.max(0f,Math.min(1f,alpha)), inv=1f-t;
        for (var e : boneWorldMatrices.entrySet()) {
            SpMatrix4 from=interpolationFrom.get(e.getKey()), to=interpolationTo.get(e.getKey());
            if (from == null || to == null) continue;
            float[] out=e.getValue().m, a=from.m, b=to.m;
            for (int i=0;i<16;i++) out[i]=a[i]*inv+b[i]*t;
        }
    }

    /**
     * Locator position in Bedrock model space (pixels) after bone animation.
     * Offset is absolute model-space at rest; relative to bone pivot is animated with the bone.
     */
    public SpVec3 getLocatorModelPosition(String locatorName) {
        SpGeoModel.LocatorRef ref = model.findLocator(locatorName);
        if (ref == null) return null;
        SpBone bone = ref.bone();
        SpLocator locator = ref.locator();
        SpMatrix4 world = boneWorldMatrices.get(bone.name);
        if (world == null) return null;
        SpVec3 p = new SpVec3(locator.offset);
        p.x -= bone.pivot.x;
        p.y -= bone.pivot.y;
        p.z -= bone.pivot.z;
        if (locator.hasRotation()) {
            // Same conjugation as bones: (−x, +y, −z).
            new SpQuaternion().setEulerDeg(
                    -locator.rotation.x,
                    locator.rotation.y,
                    -locator.rotation.z).rotate(p);
        }
        world.transformPoint(p);
        return p;
    }
}
