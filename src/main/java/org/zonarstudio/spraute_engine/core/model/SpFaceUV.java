package org.zonarstudio.spraute_engine.core.model;

/**
 * UV data for a single face of a cube.
 * Stores pixel-space UV origin and size (can be negative for mirrored UVs).
 */
public final class SpFaceUV {
    public final float u, v;
    public final float uSize, vSize;

    public SpFaceUV(float u, float v, float uSize, float vSize) {
        this.u = u;
        this.v = v;
        this.uSize = uSize;
        this.vSize = vSize;
    }
}
