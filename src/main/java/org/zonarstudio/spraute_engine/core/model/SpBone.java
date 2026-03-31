package org.zonarstudio.spraute_engine.core.model;

import org.zonarstudio.spraute_engine.core.math.SpVec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable definition of a bone in the skeleton hierarchy.
 * Contains cubes and child bones. Pivot and rotation are in Bedrock model space.
 * Runtime state (world matrices, animated transforms) lives in {@link SpModelInstance}.
 */
public final class SpBone {
    public final String name;
    /** Pivot point in absolute Bedrock model space (pixels). */
    public final SpVec3 pivot;
    /** Rest-pose rotation in degrees (Euler XYZ). */
    public final SpVec3 rotation;
    public final List<SpCube> cubes;
    public final List<SpBone> children = new ArrayList<>();
    public SpBone parent;

    public SpBone(String name, SpVec3 pivot, SpVec3 rotation, List<SpCube> cubes) {
        this.name = name;
        this.pivot = pivot;
        this.rotation = rotation;
        this.cubes = cubes;
    }
}
