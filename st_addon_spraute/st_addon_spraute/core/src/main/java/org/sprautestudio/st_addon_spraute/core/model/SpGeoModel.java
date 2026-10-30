package org.sprautestudio.st_addon_spraute.core.model;

import java.util.*;

/**
 * Immutable parsed Bedrock .geo.json model definition.
 * Holds the bone tree and texture dimensions.
 * Runtime state lives in {@link SpModelInstance}.
 */
public final class SpGeoModel {
    public final String identifier;
    public final int textureWidth;
    public final int textureHeight;
    /** Root bones (bones without a parent). */
    public final List<SpBone> rootBones;
    /** All bones indexed by name for quick lookup. */
    public final Map<String, SpBone> boneMap;
    /** Immutable render mesh and conservative visual bounds, baked once per parsed geo. */
    public final SpGeoBakedModel baked;

    public SpGeoModel(String identifier, int textureWidth, int textureHeight,
                      List<SpBone> rootBones, Map<String, SpBone> boneMap) {
        this.identifier = identifier;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.rootBones = rootBones;
        this.boneMap = boneMap;
        this.baked = SpGeoMeshBaker.bake(this);
    }

    public SpBone getBone(String name) {
        return boneMap.get(name);
    }

    /** Bone + locator pair for a named attachment point, or null. */
    public record LocatorRef(SpBone bone, SpLocator locator) {}

    /** Find a locator by name across all bones. */
    public LocatorRef findLocator(String name) {
        if (name == null || name.isBlank()) return null;
        for (SpBone bone : boneMap.values()) {
            SpLocator loc = bone.locators.get(name);
            if (loc != null) return new LocatorRef(bone, loc);
        }
        return null;
    }
}
