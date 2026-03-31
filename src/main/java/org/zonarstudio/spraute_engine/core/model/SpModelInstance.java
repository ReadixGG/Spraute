package org.zonarstudio.spraute_engine.core.model;

import org.zonarstudio.spraute_engine.core.math.SpMatrix4;
import org.zonarstudio.spraute_engine.core.math.SpQuaternion;
import org.zonarstudio.spraute_engine.core.math.SpVec3;

import java.util.LinkedHashMap;
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

    public SpModelInstance(SpGeoModel model) {
        this.model = model;
        for (String name : model.boneMap.keySet()) {
            boneWorldMatrices.put(name, new SpMatrix4());
            boneAnimRotation.put(name, new SpVec3());
            boneAnimPosition.put(name, new SpVec3());
        }
    }

    public SpGeoModel getModel() {
        return model;
    }

    /** Reset all animated transforms to defaults. */
    public void resetAnims() {
        for (SpVec3 rot : boneAnimRotation.values()) rot.set(0, 0, 0);
        for (SpVec3 pos : boneAnimPosition.values()) pos.set(0, 0, 0);
    }

    /** Compute world matrices for entire skeleton. Call after setting anim transforms. */
    public void computeTransforms() {
        SpMatrix4 identity = new SpMatrix4();
        for (SpBone root : model.rootBones) {
            computeRecursive(root, identity);
        }
    }

    private void computeRecursive(SpBone bone, SpMatrix4 parentWorld) {
        SpVec3 animRot = boneAnimRotation.get(bone.name);
        SpVec3 animPos = boneAnimPosition.get(bone.name);

        // Negate X and Y to convert Bedrock left-handed rotations to right-handed (MC).
        // Bedrock is left-handed, so X (pitch) and Y (yaw) are inverted relative to
        // the right-handed quaternion math. Z (roll) stays the same.
        // This matches GeckoLib's convention: -rotation.x, -rotation.y, +rotation.z.
        float rotX = -(bone.rotation.x + (animRot != null ? animRot.x : 0));
        float rotY = -(bone.rotation.y + (animRot != null ? animRot.y : 0));
        float rotZ =  (bone.rotation.z + (animRot != null ? animRot.z : 0));

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

        SpQuaternion q = new SpQuaternion().setEulerDeg(rotX, rotY, rotZ);
        SpMatrix4 local = new SpMatrix4().compose(new SpVec3(localX, localY, localZ), q, 1f);

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
}
