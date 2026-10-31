package org.sprautestudio.spraute_engine.core.model;

/** Conservative model-space bounds in Bedrock pixels. */
public record SpGeoBounds(float minX, float minY, float minZ,
                          float maxX, float maxY, float maxZ,
                          float radius) {
    public static final SpGeoBounds EMPTY = new SpGeoBounds(0, 0, 0, 0, 0, 0, 0);

    public boolean isEmpty() {
        return maxX <= minX && maxY <= minY && maxZ <= minZ;
    }

    public float width() { return maxX - minX; }
    public float height() { return maxY - minY; }
    public float depth() { return maxZ - minZ; }
}
