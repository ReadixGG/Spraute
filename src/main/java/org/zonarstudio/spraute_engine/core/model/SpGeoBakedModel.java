package org.zonarstudio.spraute_engine.core.model;

import java.util.List;

/**
 * Immutable, render-ready geometry. Vertices are bone-local and therefore reusable by every
 * entity that references the same geo file.
 */
public final class SpGeoBakedModel {
    public static final int PALETTE_BONES = 48;

    public final List<Page> pages;
    public final SpGeoBounds bounds;
    public final int faceCount;
    public final int vertexCount;

    public SpGeoBakedModel(List<Page> pages, SpGeoBounds bounds,
                           int faceCount, int vertexCount) {
        this.pages = List.copyOf(pages);
        this.bounds = bounds;
        this.faceCount = faceCount;
        this.vertexCount = vertexCount;
    }

    public record Page(List<String> bones, float[] positions, float[] normals, float[] uvs,
                       byte[] boneIndices, int[] faceFirstVertices, float[] faceCenters,
                       float[] faceSizes, boolean[] faceCullSafe) {
        public Page {
            bones = List.copyOf(bones);
        }

        public int vertexCount() { return boneIndices.length; }
        public int faceCount() { return faceFirstVertices.length; }
    }
}
