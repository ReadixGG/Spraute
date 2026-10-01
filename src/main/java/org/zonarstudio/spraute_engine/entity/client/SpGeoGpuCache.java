package org.zonarstudio.spraute_engine.entity.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.zonarstudio.spraute_engine.core.model.SpGeoBakedModel;
import org.zonarstudio.spraute_engine.compat.SpGeoDrawCompat;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Render-thread-owned cache. Geometry remains bone-local and is uploaded once per baked model.
 */
public final class SpGeoGpuCache {
    private static final Map<SpGeoBakedModel, SpGeoGpuMesh> MESHES = new IdentityHashMap<>();
    private static final int[] MIRRORED_QUAD_ORDER={0,3,2,1};

    private SpGeoGpuCache() {}

    public static SpGeoGpuMesh getOrCreate(SpGeoBakedModel baked) {
        RenderSystem.assertOnRenderThread();
        SpGeoGpuMesh existing = MESHES.get(baked);
        if (existing != null) return existing;

        SpGeoGpuMesh created = SpGeoGpuMesh.upload(baked);
        MESHES.put(baked, created);
        return created;
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(SpGeoGpuCache::clear);
            return;
        }
        for (SpGeoGpuMesh mesh : MESHES.values()) mesh.close();
        MESHES.clear();
    }

    public static final class SpGeoGpuMesh implements AutoCloseable {
        private final List<PageMesh> pages;

        private SpGeoGpuMesh(List<PageMesh> pages) {
            this.pages = pages;
        }

        public List<PageMesh> pages() {
            return pages;
        }

        private static SpGeoGpuMesh upload(SpGeoBakedModel baked) {
            List<PageMesh> uploaded = new ArrayList<>(baked.pages.size());
            try {
                for (SpGeoBakedModel.Page page : baked.pages) {
                    uploaded.add(PageMesh.upload(page,Math.max(.01f,baked.bounds.radius()/16f)));
                }
                return new SpGeoGpuMesh(List.copyOf(uploaded));
            } catch (RuntimeException | Error failure) {
                for (PageMesh page : uploaded) page.close();
                throw failure;
            }
        }

        @Override
        public void close() {
            for (PageMesh page : pages) page.close();
        }
    }

    public static final class PageMesh implements AutoCloseable {
        private final VertexBuffer[] solid;
        private final VertexBuffer[] thin;
        private final List<String> bones;

        private PageMesh(VertexBuffer[] solid, VertexBuffer[] thin, List<String> bones) {
            this.solid = solid;
            this.thin = thin;
            this.bones = bones;
        }

        public VertexBuffer solid(int lod) {
            return pick(solid, lod);
        }

        public VertexBuffer thin(int lod) {
            return pick(thin, lod);
        }

        private static VertexBuffer pick(VertexBuffer[] buffers, int lod) {
            return buffers[lod >= 3 ? 2 : lod >= 2 ? 1 : 0];
        }

        public List<String> bones() {
            return bones;
        }

        private static PageMesh upload(SpGeoBakedModel.Page page, float radius) {
            if (page.bones().size() > SpGeoBakedModel.PALETTE_BONES) {
                throw new IllegalArgumentException("Geo page exceeds bone palette: " + page.bones().size());
            }
            VertexBuffer[] solid = new VertexBuffer[3];
            VertexBuffer[] thin = new VertexBuffer[3];
            try {
                solid[0] = uploadVbo(page, 0f, true);
                solid[1] = uploadVbo(page, radius * .025f, true);
                solid[2] = uploadVbo(page, radius * .06f, true);
                thin[0] = uploadVbo(page, 0f, false);
                thin[1] = uploadVbo(page, radius * .025f, false);
                thin[2] = uploadVbo(page, radius * .06f, false);
                return new PageMesh(solid, thin, page.bones());
            } catch (RuntimeException | Error failure) {
                closeAll(solid);
                closeAll(thin);
                throw failure;
            }
        }

        private static VertexBuffer uploadVbo(SpGeoBakedModel.Page page, float cutoff, boolean solidPass) {
            int selected = 0;
            for (int face = 0; face < page.faceCount(); face++) {
                if (page.faceCullSafe()[face] != solidPass) continue;
                if (page.faceSizes()[face] < cutoff) continue;
                selected++;
            }
            if (selected == 0) return null;
            BufferBuilder builder = new BufferBuilder(Math.max(256, selected * 4 * 40));
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            float[] positions = page.positions(), normals = page.normals(), uvs = page.uvs();
            byte[] boneIndices = page.boneIndices();
            for (int face = 0; face < page.faceCount(); face++) {
                if (page.faceCullSafe()[face] != solidPass) continue;
                if (page.faceSizes()[face] < cutoff) continue;
                int first = page.faceFirstVertices()[face];
                for (int corner = 0; corner < 4; corner++) {
                    int source = first + (solidPass ? MIRRORED_QUAD_ORDER[corner] : corner);
                    int p = source * 3, t = source * 2, bone = boneIndices[source] & 0xff;
                    builder.vertex(positions[p], positions[p + 1], positions[p + 2]).color(bone, 0, 0, 255)
                            .uv(uvs[t], uvs[t + 1]).overlayCoords(0).uv2(0)
                            .normal(normals[p], normals[p + 1], normals[p + 2]).endVertex();
                }
            }
            //? if >=1.20.1 {
            VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            //?} else {
            /*VertexBuffer vbo = new VertexBuffer();
            *///?}
            try {
                SpGeoDrawCompat.upload(vbo, builder.end());
                return vbo;
            } catch (RuntimeException | Error failure) {
                vbo.close();
                throw failure;
            }
        }

        private static void closeAll(VertexBuffer[] buffers) {
            for (VertexBuffer buffer : buffers) if (buffer != null) buffer.close();
        }

        @Override
        public void close() {
            closeAll(solid);
            closeAll(thin);
        }
    }
}
