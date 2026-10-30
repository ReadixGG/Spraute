package org.sprautestudio.st_addon_spraute.core.model;

import org.sprautestudio.st_addon_spraute.core.math.SpQuaternion;
import org.sprautestudio.st_addon_spraute.core.math.SpVec3;
import java.util.Map;

/**
 * A single cube (box) within a bone.
 * Coordinates are in Bedrock model space (1 unit = 1 pixel = 1/16 block).
 */
public final class SpCube {
    /** World-space origin of the cube (min corner). */
    public final SpVec3 origin;
    /** Size in each axis. */
    public final SpVec3 size;
    /** Inflate (outward expansion on each axis). */
    public final float inflate;
    /**
     * Cube rotation pivot in absolute Bedrock model space.
     * Used with {@link #rotation}; defaults to {@code [0,0,0]} when omitted in JSON.
     */
    public final SpVec3 pivot;
    /** Cube rest rotation in degrees (Blockbench / Bedrock). */
    public final SpVec3 rotation;
    /** Bedrock/Blockbench {@code mirror} — flips east/west (and per-face up/down). */
    public final boolean mirror;
    /** True when UVs came from legacy box {@code "uv": [u,v]} rather than per-face objects. */
    public final boolean boxUv;
    /** Per-face UV data. Missing faces won't be rendered. */
    public final Map<SpFace, SpFaceUV> faceUVs;

    private final boolean hasRotation;
    /**
     * Precomputed quaternion for {@link #rotation}.
     * Applied in Bedrock model space before the render-time X flip.
     * Signs: −X, +Y, +Z. Cube Y keeps the geo.json sign (unlike bones, which negate Y)
     * because breast/chest cubes use near-±180° Euler triples where Y is not a pure yaw.
     */
    private final SpQuaternion rotationQuat;

    public SpCube(SpVec3 origin, SpVec3 size, float inflate, Map<SpFace, SpFaceUV> faceUVs) {
        this(origin, size, inflate, new SpVec3(), new SpVec3(), false, false, faceUVs);
    }

    public SpCube(SpVec3 origin, SpVec3 size, float inflate,
                  SpVec3 pivot, SpVec3 rotation, Map<SpFace, SpFaceUV> faceUVs) {
        this(origin, size, inflate, pivot, rotation, false, false, faceUVs);
    }

    public SpCube(SpVec3 origin, SpVec3 size, float inflate,
                  SpVec3 pivot, SpVec3 rotation, boolean mirror, boolean boxUv,
                  Map<SpFace, SpFaceUV> faceUVs) {
        this.origin = origin;
        this.size = size;
        this.inflate = inflate;
        this.pivot = pivot != null ? pivot : new SpVec3();
        this.rotation = rotation != null ? rotation : new SpVec3();
        this.mirror = mirror;
        this.boxUv = boxUv;
        this.faceUVs = faceUVs;
        this.hasRotation = Math.abs(this.rotation.x) > 1e-5f
                || Math.abs(this.rotation.y) > 1e-5f
                || Math.abs(this.rotation.z) > 1e-5f;
        if (this.hasRotation) {
            // Same conjugation as SpModelInstance: (−X, +Y, −Z) for Bedrock + vertex X flip.
            this.rotationQuat = new SpQuaternion().setEulerDeg(
                    -this.rotation.x, this.rotation.y, -this.rotation.z);
        } else {
            this.rotationQuat = null;
        }
    }

    public boolean hasRotation() {
        return hasRotation;
    }

    /**
     * Convert a point from absolute Bedrock model space into bone-local space,
     * applying cube pivot/rotation when present.
     */
    public void modelPointToBoneLocal(SpBone bone, SpVec3 p) {
        if (hasRotation && rotationQuat != null) {
            p.x -= pivot.x;
            p.y -= pivot.y;
            p.z -= pivot.z;
            rotationQuat.rotate(p);
            p.x += pivot.x;
            p.y += pivot.y;
            p.z += pivot.z;
        }
        p.x -= bone.pivot.x;
        p.y -= bone.pivot.y;
        p.z -= bone.pivot.z;
    }

    /** Rotate a direction by the cube's rest rotation (no translation). */
    public void rotateDirection(SpVec3 dir) {
        if (hasRotation && rotationQuat != null) {
            rotationQuat.rotate(dir);
        }
    }

    /**
     * Which physical side to sample for a JSON UV face.
     * Matches GeckoLib {@code VertexSet#verticesForQuad}: our render-time X-negate
     * is equivalent to their cube-origin X flip, so east/west are swapped by default
     * and only kept when {@link #mirror} is set. Per-face up/down swap when mirrored
     * and not using box UV.
     */
    public SpFace geometryFace(SpFace face, boolean boxUv) {
        return switch (face) {
            case EAST -> mirror ? SpFace.EAST : SpFace.WEST;
            case WEST -> mirror ? SpFace.WEST : SpFace.EAST;
            case UP -> (mirror && !boxUv) ? SpFace.DOWN : SpFace.UP;
            case DOWN -> (mirror && !boxUv) ? SpFace.UP : SpFace.DOWN;
            default -> face;
        };
    }

    public enum SpFace {
        NORTH, EAST, SOUTH, WEST, UP, DOWN;

        public static SpFace fromString(String s) {
            return switch (s.toLowerCase()) {
                case "north" -> NORTH;
                case "east" -> EAST;
                case "south" -> SOUTH;
                case "west" -> WEST;
                case "up" -> UP;
                case "down" -> DOWN;
                default -> null;
            };
        }
    }
}
