package org.zonarstudio.spraute_engine.core.model;

import org.zonarstudio.spraute_engine.core.math.SpVec3;
import java.util.EnumMap;
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
    /** Per-face UV data. Missing faces won't be rendered. */
    public final Map<SpFace, SpFaceUV> faceUVs;

    public SpCube(SpVec3 origin, SpVec3 size, float inflate, Map<SpFace, SpFaceUV> faceUVs) {
        this.origin = origin;
        this.size = size;
        this.inflate = inflate;
        this.faceUVs = faceUVs;
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
