package org.zonarstudio.spraute_engine.entity.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
//? if >=1.20.1 {
import org.joml.Matrix4f;
//?} else {
/*import com.mojang.math.Matrix4f;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.zonarstudio.spraute_engine.core.math.SpMatrix4;
import org.zonarstudio.spraute_engine.core.model.SpGeoBakedModel;
import org.zonarstudio.spraute_engine.core.model.SpModelInstance;
import org.zonarstudio.spraute_engine.compat.SpGeoDrawCompat;

/**
 * Optional immediate GPU skinning path. Callers must render through the normal CPU path when
 * this class returns {@code false}.
 */
public final class SpGeoGpuRenderer {
    private static final int PALETTE_FLOATS = SpGeoBakedModel.PALETTE_BONES * 16;
    private static final float MODEL_SCALE = 1f / 16f;
    private static boolean failed;
    private static boolean loggedFailure;
    private static final ThreadLocal<float[]> PALETTE =
            ThreadLocal.withInitial(() -> new float[PALETTE_FLOATS]);

    private SpGeoGpuRenderer() {}

    public static boolean render(SpModelInstance instance, PoseStack poseStack,
                                 ResourceLocation texture, int packedLight, int packedOverlay,
                                 float[] rgba, boolean translucent, String[] renderBones) {
        if (rgba == null || rgba.length < 4) return false;
        return render(instance, poseStack, texture, packedLight, packedOverlay,
                rgba[0], rgba[1], rgba[2], rgba[3], translucent, renderBones);
    }

    public static boolean render(SpModelInstance instance, PoseStack poseStack,
                                 ResourceLocation texture, int packedLight, int packedOverlay,
                                 float red, float green, float blue, float alpha,
                                 boolean translucent, String[] renderBones) {
        return render(instance,poseStack,texture,packedLight,packedOverlay,red,green,blue,alpha,
                translucent,renderBones,0);
    }

    public static boolean render(SpModelInstance instance, PoseStack poseStack,
                                 ResourceLocation texture, int packedLight, int packedOverlay,
                                 float red, float green, float blue, float alpha,
                                 boolean translucent, String[] renderBones, int lod) {
        if (instance == null || poseStack == null || texture == null || failed) return false;
        if (!RenderSystem.isOnRenderThread() || compatibilityModLoaded()) return false;
        if (renderBones != null && renderBones.length != 0) return false;
        // Static VBO order cannot sort translucent faces back-to-front. In particular,
        // glasses can write depth before eye planes and hide them completely.
        if (translucent) return false;

        SpGeoBakedModel baked = instance.getModel().baked;
        ShaderInstance shader = SpGeoShaderManager.skinned();
        if (baked == null || baked.pages.isEmpty() || shader == null) return false;
        if (shader.getUniform("BoneMatrices") == null) return false;

        try {
            SpGeoGpuCache.SpGeoGpuMesh mesh = SpGeoGpuCache.getOrCreate(baked);
            // drawWithShader copies Sampler0..11 from RenderSystem shader texture
            // slots, overwriting shader.setSampler. Without this, NPCs sample
            // whatever atlas/entity was bound last (looks like random skins).
            var glTexture = Minecraft.getInstance().getTextureManager().getTexture(texture);
            RenderSystem.setShaderTexture(0, glTexture.getId());
            shader.setSampler("Sampler0", glTexture);
            set4(shader, "TintColor", red, green, blue, alpha);
            set2(shader, "LightUv",
                    (packedLight & 0xffff) / 256f,
                    ((packedLight >>> 16) & 0xffff) / 256f);
            if ((packedOverlay & 0xffff) == 0) set4(shader,"OverlayColor",1f,1f,1f,0f);
            else set4(shader,"OverlayColor",1f,.25f,.25f,.35f);
            set1(shader, "AlphaCutoff", translucent ? 0f : 0.1f);

            Matrix4f modelView = poseStack.last().pose();
            Matrix4f projection = RenderSystem.getProjectionMatrix();
            RenderSystem.enableDepthTest();

            for (SpGeoGpuCache.PageMesh page : mesh.pages()) {
                shader.getUniform("BoneMatrices").set(buildPalette(instance, page.bones()));

                VertexBuffer solid = page.solid(lod);
                if (solid != null) {
                    if (translucent) {
                        RenderSystem.enableBlend();
                        RenderSystem.defaultBlendFunc();
                    } else {
                        RenderSystem.disableBlend();
                    }
                    RenderSystem.depthMask(true);
                    if (!translucent) RenderSystem.enableCull();
                    else RenderSystem.disableCull();
                    SpGeoDrawCompat.draw(solid, modelView, projection, shader);
                }

                VertexBuffer thin = page.thin(lod);
                if (thin != null) {
                    // Geometry shape does not define transparency. Incomplete cubes
                    // participate in depth like every other opaque/cutout model part.
                    RenderSystem.disableBlend();
                    RenderSystem.depthMask(true);
                    RenderSystem.disableCull();
                    set1(shader, "AlphaCutoff", 0.1f);
                    SpGeoDrawCompat.draw(thin, modelView, projection, shader);
                }
            }
            return true;
        } catch (Throwable failure) {
            failed = true;
            if (!loggedFailure) {
                loggedFailure = true;
                com.mojang.logging.LogUtils.getLogger()
                        .error("[Spraute Geo GPU] Disabled after render failure: {}", failure.toString());
            }
            return false;
        } finally {
            VertexBuffer.unbind();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    public static boolean isAvailable() {
        return !failed && !compatibilityModLoaded() && SpGeoShaderManager.skinned() != null;
    }

    public static void clear() {
        SpGeoGpuCache.clear();
    }

    private static boolean compatibilityModLoaded() {
        ModList mods = ModList.get();
        return mods != null && (mods.isLoaded("oculus") || mods.isLoaded("iris"));
    }

    private static float[] buildPalette(SpModelInstance instance, java.util.List<String> bones) {
        float[] palette = PALETTE.get();
        java.util.Arrays.fill(palette,0f);
        for (int slot = 0; slot < SpGeoBakedModel.PALETTE_BONES; slot++) {
            SpMatrix4 matrix = slot < bones.size() ? instance.getBoneMatrix(bones.get(slot)) : null;
            writeConverted(palette, slot * 16, matrix);
        }
        return palette;
    }

    private static void writeConverted(float[] target, int offset, SpMatrix4 matrix) {
        if (matrix == null) {
            target[offset] = -MODEL_SCALE;
            target[offset + 5] = MODEL_SCALE;
            target[offset + 10] = MODEL_SCALE;
            target[offset + 15] = 1f;
            return;
        }
        float[] source = matrix.m;
        for (int column = 0; column < 4; column++) {
            int base = column * 4;
            target[offset + base] = -source[base] * MODEL_SCALE;
            target[offset + base + 1] = source[base + 1] * MODEL_SCALE;
            target[offset + base + 2] = source[base + 2] * MODEL_SCALE;
            target[offset + base + 3] = source[base + 3];
        }
    }

    private static void set1(ShaderInstance shader, String name, float value) {
        var uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }

    private static void set2(ShaderInstance shader, String name, float x, float y) {
        var uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(x, y);
    }

    private static void set4(ShaderInstance shader, String name,
                             float x, float y, float z, float w) {
        var uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(x, y, z, w);
    }

    private static void set4(ShaderInstance shader, String name, float[] values) {
        set4(shader, name, values[0], values[1], values[2], values[3]);
    }
}
