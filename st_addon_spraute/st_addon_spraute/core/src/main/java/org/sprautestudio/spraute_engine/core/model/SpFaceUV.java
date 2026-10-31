package org.sprautestudio.spraute_engine.core.model;

/**
 * UV data for a single face of a cube.
 * Stores pixel-space UV origin and size (can be negative for mirrored UVs).
 * {@link #uvRotation} is Blockbench/Bedrock face rotation in degrees (0/90/180/270).
 */
public final class SpFaceUV {
    public final float u, v;
    public final float uSize, vSize;
    /** Clockwise UV rotation on the face: 0, 90, 180, or 270. */
    public final int uvRotation;

    public SpFaceUV(float u, float v, float uSize, float vSize) {
        this(u, v, uSize, vSize, 0);
    }

    public SpFaceUV(float u, float v, float uSize, float vSize, int uvRotation) {
        this.u = u;
        this.v = v;
        this.uSize = uSize;
        this.vSize = vSize;
        int r = uvRotation % 360;
        if (r < 0) r += 360;
        this.uvRotation = (r / 90) * 90;
    }
}
