package org.sprautestudio.spraute_engine.core.model;

import org.sprautestudio.spraute_engine.core.math.SpVec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable definition of a bone in the skeleton hierarchy.
 * Contains cubes, locators and child bones. Pivot and rotation are in Bedrock model space.
 * Runtime state (world matrices, animated transforms) lives in {@link SpModelInstance}.
 */
public final class SpBone {
    public final String name;
    /** Pivot point in absolute Bedrock model space (pixels). */
    public final SpVec3 pivot;
    /** Rest-pose rotation in degrees (Euler XYZ). */
    public final SpVec3 rotation;
    /**
     * Default inflate for cubes that omit their own {@code inflate}
     * (already baked into each {@link SpCube#inflate} at parse time).
     */
    public final float inflate;
    /**
     * Default mirror for cubes that omit their own {@code mirror}
     * (already baked into each {@link SpCube#mirror} at parse time).
     */
    public final boolean mirror;
    public final List<SpCube> cubes;
    /** Locators keyed by name (Bedrock {@code locators}). */
    public final Map<String, SpLocator> locators;
    public final List<SpBone> children = new ArrayList<>();
    public SpBone parent;

    public SpBone(String name, SpVec3 pivot, SpVec3 rotation, List<SpCube> cubes) {
        this(name, pivot, rotation, 0f, false, cubes, Collections.emptyMap());
    }

    public SpBone(String name, SpVec3 pivot, SpVec3 rotation,
                  float inflate, boolean mirror,
                  List<SpCube> cubes, Map<String, SpLocator> locators) {
        this.name = name;
        this.pivot = pivot;
        this.rotation = rotation;
        this.inflate = inflate;
        this.mirror = mirror;
        this.cubes = cubes;
        this.locators = locators == null || locators.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(locators));
    }
}
