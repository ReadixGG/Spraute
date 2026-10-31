package org.sprautestudio.spraute_engine.core.model;

import org.sprautestudio.spraute_engine.core.math.SpVec3;

/**
 * Named attachment point on a bone (Bedrock {@code locators}).
 * {@link #offset} is in absolute Bedrock model space (pixels), like cube origins.
 */
public final class SpLocator {
    public final String name;
    /** Absolute model-space position (pixels). */
    public final SpVec3 offset;
    /** Optional locator orientation in degrees (XYZ). */
    public final SpVec3 rotation;
    /** When true, parent bone scale is not applied to this locator. */
    public final boolean ignoreInheritedScale;

    public SpLocator(String name, SpVec3 offset) {
        this(name, offset, new SpVec3(), false);
    }

    public SpLocator(String name, SpVec3 offset, SpVec3 rotation, boolean ignoreInheritedScale) {
        this.name = name;
        this.offset = offset != null ? offset : new SpVec3();
        this.rotation = rotation != null ? rotation : new SpVec3();
        this.ignoreInheritedScale = ignoreInheritedScale;
    }

    public boolean hasRotation() {
        return Math.abs(rotation.x) > 1e-5f
                || Math.abs(rotation.y) > 1e-5f
                || Math.abs(rotation.z) > 1e-5f;
    }
}
