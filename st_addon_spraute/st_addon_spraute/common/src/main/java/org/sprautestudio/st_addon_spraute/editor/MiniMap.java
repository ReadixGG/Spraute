package org.sprautestudio.st_addon_spraute.editor;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.sprautestudio.st_addon_spraute.ui.Canvas;

/**
 * The world seen from above for the 2D editors (zones, cutscenes): the top block of every column in
 * the colours of a Minecraft map, shaded by height. Built from what the client has loaded into a
 * texture, rebuilt only when the view leaves it, the zoom changes a lot, or a few seconds pass.
 */
public final class MiniMap {
    private static int counter;

    private final ResourceLocation id = new ResourceLocation("st_addon_spraute", "minimap/" + (counter++));
    private DynamicTexture tex;
    private int texW, texH;
    /** World area of the texture: west/north corner and blocks per texel. */
    private double bx0, bz0, step = 1;
    private double builtScale = -1;
    private long builtAt;
    private String builtDim = "";
    public boolean enabled = true;
    public float alpha = 0.85f;

    private static final int MAX = 384, EMPTY = 0xFF15161C;

    /**
     * Draws under a view where world (wx, wz) is at screen {@code x + w/2 + (wx - cx) * scale}.
     */
    public void draw(Canvas c, int x, int y, int w, int h, double cx, double cz, double scale) {
        if (!enabled || w <= 0 || h <= 0 || scale <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        double viewW = w / scale, viewH = h / scale;
        double vx0 = cx - viewW / 2, vz0 = cz - viewH / 2;
        boolean inside = tex != null && vx0 >= bx0 && vz0 >= bz0 && vx0 + viewW <= bx0 + texW * step && vz0 + viewH <= bz0 + texH * step;
        boolean zoomed = builtScale <= 0 || scale > builtScale * 1.8 || scale < builtScale / 1.8;
        String dim = level.dimension().location().toString();
        if (!inside || zoomed || !dim.equals(builtDim) || System.currentTimeMillis() - builtAt > 4000) {
            build(level, cx, cz, viewW, viewH, scale);
            builtDim = dim;
        }
        if (tex == null) return;
        c.flush();
        var g = c.g;
        float sx = (float) (x + w / 2.0 + (bx0 - cx) * scale), sz = (float) (y + h / 2.0 + (bz0 - cz) * scale);
        float sw = (float) (texW * step * scale), sh = (float) (texH * step * scale);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        g.pose().pushPose();
        g.pose().translate(sx, sz, 0);
        g.pose().scale(sw / texW, sh / texH, 1);
        g.blit(id, 0, 0, 0, 0, texW, texH, texW, texH);
        g.pose().popPose();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        g.flush();
    }

    private void build(ClientLevel level, double cx, double cz, double viewW, double viewH, double scale) {
        // twice the view, so panning around stays inside; one texel a block when it fits
        double areaW = viewW * 2, areaH = viewH * 2;
        step = Math.max(1, Math.ceil(Math.max(areaW, areaH) / MAX));
        int tw = (int) Math.max(8, Math.min(MAX, Math.ceil(areaW / step))), th = (int) Math.max(8, Math.min(MAX, Math.ceil(areaH / step)));
        bx0 = Math.floor(cx - tw * step / 2);
        bz0 = Math.floor(cz - th * step / 2);
        builtScale = scale;
        builtAt = System.currentTimeMillis();
        if (tex == null || tw != texW || th != texH) {
            if (tex != null) tex.close();
            texW = tw;
            texH = th;
            tex = new DynamicTexture(tw, th, false);
            Minecraft.getInstance().getTextureManager().register(id, tex);
        }
        NativeImage img = tex.getPixels();
        if (img == null) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int s = (int) step;
        for (int tz = 0; tz < th; tz++) {
            int wz = (int) (bz0 + tz * step);
            int prevNorth = Integer.MIN_VALUE;
            for (int tx = 0; tx < tw; tx++) {
                int wx = (int) (bx0 + tx * step);
                int argb = EMPTY;
                if (level.hasChunk(wx >> 4, wz >> 4)) {
                    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
                    int north = level.hasChunk(wx >> 4, (wz - s) >> 4) ? level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz - s) - 1 : top;
                    pos.set(wx, top, wz);
                    BlockState st = level.getBlockState(pos);
                    MapColor mc = st.getMapColor(level, pos);
                    int rgb = mc == MapColor.NONE ? 0x202020 : mc.col;
                    // like a map: brighter where higher than the block to the north, darker where lower
                    float shade = top > north ? 1.0f : top < north ? 0.7f : 0.86f;
                    if (mc == MapColor.WATER) {
                        int depth = 0;
                        BlockPos.MutableBlockPos d = new BlockPos.MutableBlockPos(wx, top - 1, wz);
                        while (depth < 10 && level.getBlockState(d).getFluidState().isSource()) {
                            depth++;
                            d.move(0, -1, 0);
                        }
                        shade = depth > 6 ? 0.68f : depth > 2 ? 0.82f : 0.95f;
                    }
                    int r = (int) (((rgb >> 16) & 255) * shade), gg = (int) (((rgb >> 8) & 255) * shade), b = (int) ((rgb & 255) * shade);
                    argb = 0xFF000000 | (r << 16) | (gg << 8) | b;
                }
                // NativeImage takes ABGR
                int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
                img.setPixelRGBA(tx, tz, abgr);
            }
        }
        tex.upload();
    }

    public void close() {
        if (tex != null) {
            tex.close();
            Minecraft.getInstance().getTextureManager().release(id);
            tex = null;
        }
    }
}
