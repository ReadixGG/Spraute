package org.zonarstudio.spraute_engine.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
//? if >=1.20.1 {
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
//?} else {
/*import com.mojang.math.Matrix4f;
*///?}
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.zonarstudio.spraute_engine.compat.SprauteRenderCompat;
import org.zonarstudio.spraute_engine.entity.SprauteWorldCanvasEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Client side of world-space canvases (runtime {@code worldCanvas} entities and the editor ghost
 * preview): keeps one off-screen texture per canvas, filled by the exact same widget-tree draw
 * an on-screen canvas uses ({@link SprauteScriptScreen#renderWorldCanvas}), and draws it as a
 * plane in the world ({@link #drawPanel}).
 *
 * <p>Cost model:
 * <ul>
 *   <li>Drawing the plane every frame is one textured quad — basically free.</li>
 *   <li>Re-rendering the GUI into the texture only happens when needed: immediately when the
 *       canvas content/resolution changed; while it has running UI animations/video — every
 *       frame up close, 30 fps at mid range, 10 fps far away; otherwise a 2 fps safety refresh.
 *       At most {@value #MAX_REDRAWS_PER_FRAME} canvases are re-rendered per frame (changed
 *       ones first, then nearest).</li>
 *   <li>Canvases not seen for {@value #EVICT_AFTER_SECONDS}s release their texture and cached
 *       widget tree; everything is released on disconnect.</li>
 * </ul>
 *
 * <p>GL safety: the GUI draw runs at {@code RenderTickEvent} START — before the frame's level
 * and GUI passes, both of which set their own projection (and independent of F1 / hidden HUD in
 * cutscenes) — and saves/restores the projection matrix, the model-view stack and the bound
 * framebuffer with the same sequence {@code EditorModelPreviewRenderer} already uses.
 */
@Mod.EventBusSubscriber(modid = org.zonarstudio.spraute_engine.Spraute_engine.MODID, value = Dist.CLIENT)
public final class WorldCanvasTextureManager {
    public static final String KEY_PREFIX = "worldcanvas:";

    private static final int MAX_REDRAWS_PER_FRAME = 4;
    private static final int MAX_TEXTURE_SIDE = 2048;
    private static final int EVICT_AFTER_SECONDS = 5;
    private static final long EVICT_AFTER_NS = EVICT_AFTER_SECONDS * 1_000_000_000L;
    private static final long STATIC_REFRESH_NS = 500_000_000L;
    /**
     * Extra canvas pixels rendered on every edge of the off-screen texture (and included in the
     * ortho projection) beyond the panel's own content box. Styles that paint outside a widget's
     * nominal rect — {@code outerBorder}/drop shadows — need this room or they get clipped by the
     * render target's exact-content-size bounds (unlike an on-screen canvas, which has the whole
     * screen to spill into).
     */
    private static final int BORDER_MARGIN_PX = 24;

    /** Texture id + the canvas' own content-only panel size in GUI pixels (for aspect ratio). */
    public record Handle(int textureId, int panelW, int panelH, int marginPx) {}

    private static final class Entry {
        final String key;
        String json;
        float resolution = 2f;
        boolean dirty = true;
        boolean queued;
        boolean animating;
        double distSq;
        long lastSeenNs;
        long lastDrawNs;
        TextureTarget target;
        int texW, texH;
        Handle handle;

        Entry(String key) { this.key = key; }
    }

    // Render thread only (request() from entity/preview rendering, redraw at RenderTickEvent).
    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static final List<Entry> PENDING = new ArrayList<>();
    private static long lastEvictNs;

    private WorldCanvasTextureManager() {}

    /**
     * Called by whoever draws a world canvas this frame. Returns the current texture (possibly
     * from a previous frame — or {@code null} on the very first frame, before it was rendered)
     * and schedules a re-render if one is due.
     */
    public static Handle request(String key, String json, float resolution, double distSq) {
        if (key == null || json == null || json.isEmpty()) return null;
        long now = System.nanoTime();
        Entry e = ENTRIES.get(key);
        if (e == null) {
            e = new Entry(key);
            ENTRIES.put(key, e);
        }
        e.lastSeenNs = now;
        e.distSq = distSq;
        if (json != e.json && !json.equals(e.json)) {
            e.json = json;
            e.dirty = true;
        }
        float res = Math.max(0.5f, Math.min(4f, resolution));
        if (res != e.resolution) {
            e.resolution = res;
            e.dirty = true;
        }
        if (!e.queued && needsRedraw(e, now)) {
            e.queued = true;
            PENDING.add(e);
        }
        return e.handle;
    }

    private static boolean needsRedraw(Entry e, long now) {
        if (e.dirty || e.handle == null) return true;
        long interval;
        if (e.animating) {
            if (e.distSq < 12 * 12) interval = 0L;
            else if (e.distSq < 32 * 32) interval = 33_000_000L;
            else interval = 100_000_000L;
        } else {
            interval = STATIC_REFRESH_NS;
        }
        return now - e.lastDrawNs >= interval;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!ENTRIES.isEmpty()) disposeAll();
            return;
        }
        long now = System.nanoTime();
        if (now - lastEvictNs > 1_000_000_000L) {
            lastEvictNs = now;
            evictStale(now);
        }
        if (PENDING.isEmpty()) return;

        // Changed/new content first, then nearest.
        PENDING.sort((a, b) -> {
            boolean ua = a.dirty || a.handle == null;
            boolean ub = b.dirty || b.handle == null;
            if (ua != ub) return ua ? -1 : 1;
            return Double.compare(a.distSq, b.distSq);
        });
        int count = Math.min(MAX_REDRAWS_PER_FRAME, PENDING.size());
        List<Entry> batch = new ArrayList<>(PENDING.subList(0, count));
        PENDING.subList(0, count).clear();
        for (Entry e : batch) {
            e.queued = false;
            try {
                redraw(mc, e, event.renderTickTime, now);
            } catch (Throwable t) {
                e.dirty = false; // don't retry a broken canvas every frame
                e.lastDrawNs = now;
                com.mojang.logging.LogUtils.getLogger().debug("[Spraute World Canvas] render failed: {}", t.toString());
            }
        }
    }

    private static void redraw(Minecraft mc, Entry e, float partialTick, long now) {
        int[] panel = SprauteScriptScreen.measureWorldCanvasPanel(e.key, e.json);
        int pw = Math.max(1, panel[0]);
        int ph = Math.max(1, panel[1]);
        int margin = BORDER_MARGIN_PX;
        int tw = Math.max(1, Math.min(MAX_TEXTURE_SIDE, Math.round((pw + margin * 2) * e.resolution)));
        int th = Math.max(1, Math.min(MAX_TEXTURE_SIDE, Math.round((ph + margin * 2) * e.resolution)));
        if (e.target == null || e.texW != tw || e.texH != th) {
            if (e.target != null) e.target.destroyBuffers();
            // Depth attachment: UI_ITEM / UI_ENTITY widgets draw real 3D models.
            e.target = new TextureTarget(tw, th, true, Minecraft.ON_OSX);
            e.target.setClearColor(0f, 0f, 0f, 0f);
            e.target.setFilterMode(GL11.GL_LINEAR);
            e.texW = tw;
            e.texH = th;
        }

        // Same save/swap/restore sequence as the (already shipping) EditorModelPreviewRenderer.
        //? if >=1.20.1 {
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        //?} else {
        /*Matrix4f savedProjection = RenderSystem.getProjectionMatrix().copy();
        *///?}
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.translate(0.0D, 0.0D, -11000.0D);
        RenderSystem.applyModelViewMatrix();
        // GUI-space ortho in canvas pixels; the (bigger) target makes it supersampled. Widened by
        // the border margin on every edge so outer borders/shadows painted just outside the
        // widget tree's 0..pw/0..ph content box still land inside the visible/rendered area.
        //? if >=1.20.1 {
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-margin, pw + margin, ph + margin, -margin, 1000f, 21000f), VertexSorting.ORTHOGRAPHIC_Z);
        //?} else {
        /*RenderSystem.setProjectionMatrix(Matrix4f.orthographic(-margin, pw + margin, -margin, ph + margin, 1000f, 21000f));
        *///?}
        try {
            // clear() binds, clears and UNBINDS — bind for drawing afterwards.
            e.target.clear(Minecraft.ON_OSX);
            e.target.bindWrite(true);
            RenderSystem.enableDepthTest();
            Lighting.setupFor3DItems();
            //? if >=1.20.1 {
            GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            SprauteScriptScreen.renderWorldCanvas(e.key, e.json, graphics, partialTick);
            //?} else {
            /*SprauteScriptScreen.renderWorldCanvas(e.key, e.json, new PoseStack(), partialTick);
            mc.renderBuffers().bufferSource().endBatch();
            *///?}
        } finally {
            mc.getMainRenderTarget().bindWrite(true);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            //? if >=1.20.1 {
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
            //?} else {
            /*RenderSystem.setProjectionMatrix(savedProjection);
            *///?}
        }

        e.handle = new Handle(e.target.getColorTextureId(), pw, ph, margin);
        e.dirty = false;
        e.lastDrawNs = now;
        e.animating = SprauteScriptScreen.worldCanvasAnimating(e.key);
    }

    private static void evictStale(long now) {
        for (Iterator<Entry> it = ENTRIES.values().iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (now - e.lastSeenNs < EVICT_AFTER_NS) continue;
            release(e);
            PENDING.remove(e);
            it.remove();
        }
    }

    private static void release(Entry e) {
        if (e.target != null) e.target.destroyBuffers();
        e.target = null;
        e.handle = null;
        SprauteScriptScreen.disposeWorldCanvas(e.key);
    }

    public static void disposeAll() {
        for (Entry e : ENTRIES.values()) release(e);
        ENTRIES.clear();
        PENDING.clear();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        disposeAll();
    }

    // ---- drawing the plane -------------------------------------------------------------------

    /** Turns the pose from world axes to the panel's own axes (local +X right, +Y up on screen). */
    private static void applyOrientation(PoseStack pose, int mode, float yaw, float pitch, float roll,
                                         Camera camera) {
        switch (mode) {
            case SprauteWorldCanvasEntity.MODE_FIXED -> {
                SprauteRenderCompat.rotateY(pose, -yaw);
                SprauteRenderCompat.rotateX(pose, pitch);
            }
            case SprauteWorldCanvasEntity.MODE_YAW -> SprauteRenderCompat.rotateY(pose, 180.0F - camera.getYRot());
            default -> {
                pose.mulPose(camera.rotation());
                SprauteRenderCompat.rotateY(pose, 180.0F);
            }
        }
        if (roll != 0f) SprauteRenderCompat.rotateZ(pose, roll);
    }

    /**
     * World-space unit axes of the panel plane: {@code [0]} points along the panel's own +X (the
     * canvas' left→right), {@code [1]} along its +Y (canvas' bottom→top, i.e. <em>up</em> the
     * screen — canvas pixel Y grows the other way). Derived by replaying {@link #applyOrientation}
     * so the editor's in-world widget overlay always lands exactly where the panel is drawn.
     */
    public static Vec3[] panelAxes(int mode, float yaw, float pitch, float roll, Camera camera) {
        PoseStack pose = new PoseStack();
        applyOrientation(pose, mode, yaw, pitch, roll, camera);
        return new Vec3[]{
                SprauteRenderCompat.transformDirection(pose, 1f, 0f, 0f),
                SprauteRenderCompat.transformDirection(pose, 0f, 1f, 0f)
        };
    }

    /**
     * Draws a canvas texture as a plane centered on the pose origin.
     *
     * @param height    blocks; {@code <= 0} = from the canvas' aspect ratio
     * @param yaw/pitch/roll degrees, used by {@code MODE_FIXED} (roll by every mode); front faces
     *                  the yaw direction, like an NPC with that yaw
     * @param depthSeed separates coplanar panels (entity id / node hash)
     */
    public static void drawPanel(PoseStack pose, Handle handle, float width, float height, int mode,
                                 float yaw, float pitch, float roll, Camera camera, int depthSeed) {
        if (handle == null) return;
        float w = Math.max(0.01f, width);
        float h = height > 0f ? height : w * handle.panelH() / (float) Math.max(1, handle.panelW());

        // The texture also holds a margin band around the content (see BORDER_MARGIN_PX) so
        // outer borders/shadows aren't clipped. Grow the quad by the same proportion — and keep
        // sampling the whole texture (uv 0..1) — so that band shows up just outside the panel's
        // configured width/height instead of being cropped off.
        float qw = w * (1f + 2f * handle.marginPx() / (float) Math.max(1, handle.panelW()));
        float qh = h * (1f + 2f * handle.marginPx() / (float) Math.max(1, handle.panelH()));

        pose.pushPose();
        applyOrientation(pose, mode, yaw, pitch, roll, camera);
        // Coplanar panels need slightly different depths or they z-fight. The seed differs
        // between the editor ghost (node id hash) and the runtime entity (entity id, new every
        // launch), so this must stay small enough to be invisible — a centered ±0.05 blocks
        // made the panel visibly jump backwards by up to 9cm on play, and into a wall it was
        // mounted on. Forward-only, 16 planes, 0.4mm apart: max 6mm, never behind the node.
        pose.translate(0.0D, 0.0D, (depthSeed & 0x0F) * 0.0004f);

        RenderSystem.enableBlend();
        // The texture holds premultiplied color (GUI alpha-blended onto a transparent target).
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, handle.textureId());

        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        var m = pose.last().pose();
        // Framebuffer textures are bottom-up: the GUI's top row sits at v = 1.
        buf.vertex(m, -qw / 2f, -qh / 2f, 0f).uv(0f, 0f).endVertex();
        buf.vertex(m, qw / 2f, -qh / 2f, 0f).uv(1f, 0f).endVertex();
        buf.vertex(m, qw / 2f, qh / 2f, 0f).uv(1f, 1f).endVertex();
        buf.vertex(m, -qw / 2f, qh / 2f, 0f).uv(0f, 1f).endVertex();
        BufferUploader.drawWithShader(buf.end());

        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        pose.popPose();
    }
}
