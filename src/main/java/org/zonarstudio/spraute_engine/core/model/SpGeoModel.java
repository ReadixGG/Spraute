package org.zonarstudio.spraute_engine.core.model;

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

    public SpGeoModel(String identifier, int textureWidth, int textureHeight,
                      List<SpBone> rootBones, Map<String, SpBone> boneMap) {
        this.identifier = identifier;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.rootBones = rootBones;
        this.boneMap = boneMap;
    }

    public SpBone getBone(String name) {
        return boneMap.get(name);
    }
}
