package org.sprautestudio.st_addon_spraute.editor.texture;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.editor.assets.ProjectImages;
import org.sprautestudio.st_addon_spraute.editor.project.Project;
import org.sprautestudio.st_addon_spraute.editor.tabs.EditorTab;
import org.sprautestudio.st_addon_spraute.lang.Analyzer;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.widgets.*;

import java.io.IOException;
import java.util.*;

/**
 * A pixel-art editor for the project's pictures and skins: layers, pencil (pixel-perfect), eraser,
 * fill, lines and shapes, selection, magic wand, moving, a picker, shading that shifts hue like an
 * artist would, and dithered gradients — plain ramps, from one colour to another, or shading the
 * colours already there. Skins get their layout drawn over them.
 */
public class TextureEditor extends EditorTab {
    public final Project.ImageRef image;
    private PixelDoc doc;
    private String loadError;

    // ------------------------------------------------------------------ tools
    enum Tool {
        PENCIL("Карандаш", "B", new String[]{".......##", "......###", ".....###.", "....###..", "...###...", "..###....", ".###.....", "##.......", "#........"}),
        ERASER("Ластик", "E", new String[]{"....####.", "...#...##", "..#...#.#", ".#...#..#", "#####..#.", "#...#.#..", "#...##...", "#####....", "........."}),
        FILL("Заливка", "G", new String[]{"...#.....", "..#.#....", ".#...#...", "#.....#..", ".#...#.#.", "..#.#..#.", "...#...#.", ".......##", "........."}),
        LINE("Линия", "L", new String[]{"........#", ".......#.", "......#..", ".....#...", "....#....", "...#.....", "..#......", ".#.......", "#........"}),
        RECT("Прямоугольник", "R", new String[]{"#########", "#.......#", "#.......#", "#.......#", "#.......#", "#.......#", "#.......#", "#.......#", "#########"}),
        ELLIPSE("Эллипс", "O", new String[]{"...###...", ".##...##.", ".#.....#.", "#.......#", "#.......#", "#.......#", ".#.....#.", ".##...##.", "...###..."}),
        GRADIENT("Градиент (пиксельный, с шейдом)", "D", new String[]{"#########", "#########", "##.###.##", "#.#.#.#.#", ".#.#.#.#.", "#...#...#", "..#...#..", ".........", "........."}),
        SHADE("Шейд: ЛКМ — тень, ПКМ — свет", "S", new String[]{"..#####..", ".###...#.", "####....#", "####....#", "####....#", "####....#", "####....#", ".###...#.", "..#####.."}),
        PICKER("Пипетка (или Alt + клик)", "I", new String[]{"......##.", ".....####", "......##.", ".....#.#.", "....#....", "...#.....", "..#......", ".#.......", "#........"}),
        SELECT("Выделение", "M", new String[]{"##.##.##.", "........#", "#........", "#.......#", ".........", "#.......#", "#........", "........#", ".##.##.##"}),
        WAND("Волшебная палочка", "W", new String[]{"......#..", ".....###.", "......#..", ".....#...", "....#....", "...#.....", "..#......", ".#.......", "#........"}),
        MOVE("Перемещение (выделения или слоя)", "V", new String[]{"....#....", "...###...", "....#....", ".#..#..#.", "#########", ".#..#..#.", "....#....", "...###...", "....#...."});

        final String title, key;
        final String[] glyph;

        Tool(String title, String key, String[] glyph) {
            this.title = title;
            this.key = key;
            this.glyph = glyph;
        }
    }

    private Tool tool = Tool.PENCIL;
    private int primary = 0xFF2B2B2B, secondary = 0xFFFFFFFF;
    private int size = 1;
    private boolean pixelPerfect = true, filled = false, contiguous = true, mirrorX, mirrorY, grid = true, skinGuide = true;
    private int tolerance = 0;
    // gradient
    private int gradColors = 0; // 0 shade ramp of the main colour, 1 main → second, 2 shade the colours already there
    private int gradSteps = 5, gradDither = 2, gradArea = 0; // area: 0 same colour under the cursor, 1 opaque pixels of the layer, 2 whole layer
    private float gradSpread = 0.5f, hueShift = 8;
    private boolean gradRadial, gradReverse;
    // palettes
    private final List<Integer> recent = new ArrayList<>();
    private static final int[] ENDESGA = {0xbe4a2f, 0xd77643, 0xead4aa, 0xe4a672, 0xb86f50, 0x733e39, 0x3e2731, 0xa22633, 0xe43b44, 0xf77622,
            0xfeae34, 0xfee761, 0x63c74d, 0x3e8948, 0x265c42, 0x193c3e, 0x124e89, 0x0099db, 0x2ce8f5, 0xffffff, 0xc0cbdc, 0x8b9bb4, 0x5a6988,
            0x3a4466, 0x262b44, 0x181425, 0xff0044, 0x68386c, 0xb55088, 0xf6757a, 0xe8b796, 0xc28569};
    private boolean paletteFromImage = true;

    // ------------------------------------------------------------------ view
    private int zoom = 8, panX, panY;
    private boolean fitted;
    private int canvasX, canvasY, canvasW, canvasH;
    private static final int TOP = 30, LEFT = 44, RIGHT = 240, STATUS = 20;

    // ------------------------------------------------------------------ the stroke under way
    private boolean drawing, panning, moving;
    private int button, sx, sy, lx, ly, curX = -1, curY = -1;
    private double panFromX, panFromY;
    private int[] base;
    private boolean[] touched;
    private final List<int[]> strokePts = new ArrayList<>();
    private boolean[] selection, lifted, gradMask;
    private int[] floatPx;
    private boolean selectAdd;

    // ------------------------------------------------------------------ texture on screen
    private DynamicTexture tex;
    private ResourceLocation texId;
    private int texW, texH, texVersion = -1;
    private DynamicTexture checker;
    private ResourceLocation checkerId;
    private int checkerW, checkerH;
    private List<Integer> paletteCache;
    private int paletteVersion = -1;
    private int drawVersion;
    private static int counter;

    // ------------------------------------------------------------------ clickable parts (rebuilt every frame)
    private record Hit(int x, int y, int w, int h, Runnable left, Runnable right, String tip) {
    }

    private final List<Hit> hits = new ArrayList<>();
    private int[] clipboard;
    private int clipX, clipY, clipW, clipH;
    private int layerScroll;

    public TextureEditor(Project project, Project.ImageRef image) {
        super(project, null);
        this.image = image;
        focusable = true;
        try {
            doc = PixelDoc.load(image.path());
        } catch (IOException e) {
            loadError = e.getMessage();
        }
    }

    @Override
    public String title() {
        return image.name();
    }

    // ================================================================== painting

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        hits.clear();
        c.fill(x, y, w, h, Theme.BG_DEEP);
        if (doc == null) {
            c.textCentered("Картинка не открылась: " + loadError, x + w / 2, y + h / 2, Theme.ERROR);
            return;
        }
        canvasX = x + LEFT;
        canvasY = y + TOP;
        canvasW = w - LEFT - RIGHT;
        canvasH = h - TOP - STATUS;
        if (!fitted && canvasW > 0) fit();
        paintCanvas(c, mx, my);
        paintTopBar(c, mx, my);
        paintTools(c, mx, my);
        paintRight(c, mx, my);
        paintStatus(c, mx, my);
    }

    private void fit() {
        fitted = true;
        zoom = Math.max(1, Math.min(48, Math.min((canvasW - 40) / doc.w, (canvasH - 40) / doc.h)));
        panX = 0;
        panY = 0;
    }

    private int originX() {
        return canvasX + (canvasW - doc.w * zoom) / 2 + panX;
    }

    private int originY() {
        return canvasY + (canvasH - doc.h * zoom) / 2 + panY;
    }

    private void paintCanvas(Canvas c, int mx, int my) {
        c.pushClip(canvasX, canvasY, canvasW, canvasH);
        c.fill(canvasX, canvasY, canvasW, canvasH, 0xFF0D0E12);
        int ox = originX(), oy = originY(), iw = doc.w * zoom, ih = doc.h * zoom;
        c.shadow(ox, oy, iw, ih, 2, 10);
        uploadTexture();
        // transparency checker: one picture, 2×2 cells per pixel of the image (a single draw)
        if (checkerId != null) c.g.blit(checkerId, ox, oy, iw, ih, 0, 0, doc.w * 2, doc.h * 2, doc.w * 2, doc.h * 2);
        if (texId != null) {
            RenderSystem.enableBlend();
            c.g.blit(texId, ox, oy, iw, ih, 0, 0, doc.w, doc.h, doc.w, doc.h);
        }
        // the skin's parts
        if (skinGuide && isSkinSize()) for (Part p : parts()) c.outline(ox + p.x * zoom, oy + p.y * zoom, p.w * zoom, p.h * zoom, p.color);
        // pixel grid
        if (grid && zoom >= 6) {
            int gc = zoom >= 12 ? 0x22FFFFFF : 0x14FFFFFF;
            for (int i = 1; i < doc.w; i++) {
                int gx = ox + i * zoom;
                if (gx >= canvasX && gx < canvasX + canvasW) c.fill(gx, Math.max(oy, canvasY), 1, Math.min(ih, canvasH), (i % 8 == 0) ? 0x33FFFFFF : gc);
            }
            for (int i = 1; i < doc.h; i++) {
                int gy = oy + i * zoom;
                if (gy >= canvasY && gy < canvasY + canvasH) c.fill(Math.max(ox, canvasX), gy, Math.min(iw, canvasW), 1, (i % 8 == 0) ? 0x33FFFFFF : gc);
            }
        }
        c.outline(ox - 1, oy - 1, iw + 2, ih + 2, 0xFF4A4E62);
        // the selection
        if (selection != null) {
            long t = System.currentTimeMillis() / 120;
            for (int py = 0; py < doc.h; py++)
                for (int px = 0; px < doc.w; px++) {
                    int i = py * doc.w + px;
                    if (!selection[i]) continue;
                    int col = ((px + py + t) & 3) < 2 ? 0xFFFFFFFF : 0xFF111111;
                    if (px == 0 || !selection[i - 1]) c.fill(ox + px * zoom, oy + py * zoom, 1, zoom, col);
                    if (px == doc.w - 1 || !selection[i + 1]) c.fill(ox + (px + 1) * zoom - 1, oy + py * zoom, 1, zoom, col);
                    if (py == 0 || !selection[i - doc.w]) c.fill(ox + px * zoom, oy + py * zoom, zoom, 1, col);
                    if (py == doc.h - 1 || !selection[i + doc.w]) c.fill(ox + px * zoom, oy + (py + 1) * zoom - 1, zoom, 1, col);
                }

        }
        // the brush under the mouse
        if (curX >= 0 && !panning && inCanvas(mx, my) && (tool == Tool.PENCIL || tool == Tool.ERASER || tool == Tool.SHADE)) {
            int half = (size - 1) / 2;
            int bx = ox + (curX - half) * zoom, by = oy + (curY - half) * zoom;
            if (tool == Tool.PENCIL && !drawing) c.fill(bx, by, size * zoom, size * zoom, (primary & 0xFFFFFF) | 0x66000000);
            c.outline(bx, by, size * zoom, size * zoom, 0xCCFFFFFF);
            if (mirrorX) c.outline(ox + (doc.w - 1 - curX - half) * zoom, by, size * zoom, size * zoom, 0x66FFFFFF);
            if (mirrorY) c.outline(bx, oy + (doc.h - 1 - curY - half) * zoom, size * zoom, size * zoom, 0x66FFFFFF);
        } else if (doc.in(curX, curY) && inCanvas(mx, my) && !panning) {
            c.outline(ox + curX * zoom, oy + curY * zoom, zoom, zoom, 0xAAFFFFFF);
        }
        if (mirrorX) c.fill(ox + iw / 2, oy, 1, ih, 0x66FF6BD8);
        if (mirrorY) c.fill(ox, oy + ih / 2, iw, 1, 0x66FF6BD8);
        // the picture at its real size
        int pw = Math.min(doc.w * 2, 160), ph = pw * doc.h / doc.w;
        int px0 = canvasX + canvasW - pw - 10, py0 = canvasY + 10;
        c.roundBorder(px0 - 4, py0 - 4, pw + 8, ph + 8, 4, 0xEE16171D, Theme.BORDER);
        if (texId != null) c.g.blit(texId, px0, py0, pw, ph, 0, 0, doc.w, doc.h, doc.w, doc.h);
        c.popClip();
    }

    private void uploadTexture() {
        int ver = doc.version * 1000 + drawVersion;
        if (tex != null && texW == doc.w && texH == doc.h && texVersion == ver) return;
        try {
            if (tex == null || texW != doc.w || texH != doc.h) {
                if (tex != null) tex.close();
                tex = new DynamicTexture(new NativeImage(doc.w, doc.h, true));
                texId = new ResourceLocation(St_addon_spraute.MODID, "texture_editor/" + (counter++));
                Minecraft.getInstance().getTextureManager().register(texId, tex);
                texW = doc.w;
                texH = doc.h;
            }
            if (checker == null || checkerW != doc.w || checkerH != doc.h) {
                if (checker != null) Minecraft.getInstance().getTextureManager().release(checkerId);
                NativeImage ci = new NativeImage(doc.w * 2, doc.h * 2, false);
                for (int yy = 0; yy < doc.h * 2; yy++)
                    for (int xx = 0; xx < doc.w * 2; xx++) ci.setPixelRGBA(xx, yy, ((xx + yy) & 1) == 0 ? 0xFF473D3A : 0xFF362E2C);
                checker = new DynamicTexture(ci);
                checkerId = new ResourceLocation(St_addon_spraute.MODID, "texture_editor/checker" + (counter++));
                Minecraft.getInstance().getTextureManager().register(checkerId, checker);
                checkerW = doc.w;
                checkerH = doc.h;
            }
            NativeImage ni = tex.getPixels();
            if (ni == null) return;
            int[] flat = doc.flatten();
            for (int i = 0; i < flat.length; i++) {
                int a = flat[i];
                ni.setPixelRGBA(i % doc.w, i / doc.w, (a & 0xFF00FF00) | ((a >> 16) & 0xFF) | ((a & 0xFF) << 16));
            }
            tex.upload();
            texVersion = ver;
        } catch (Throwable e) {
            St_addon_spraute.LOGGER.warn("Texture editor upload", e);
        }
    }

    // ---------------------------------------------------------------- top bar

    private void paintTopBar(Canvas c, int mx, int my) {
        c.fill(x, y, w, TOP, Theme.PANEL);
        c.fill(x, y + TOP - 1, w, 1, Theme.BORDER);
        int bx = x + 8;
        c.text(image.name() + (doc.dirty() ? " •" : ""), bx, y + 11, Theme.TEXT);
        bx += c.width(image.name() + " •") + 6;
        c.text(doc.w + "×" + doc.h, bx, y + 11, Theme.TEXT_MUTED);
        bx += c.width(doc.w + "×" + doc.h) + 14;
        bx = barButton(c, mx, my, bx, "Сохранить", Icon.SAVE, doc.dirty(), this::save, "Ctrl+S");
        bx = barButton(c, mx, my, bx, null, Icon.UNDO, false, this::undo, "Отменить (Ctrl+Z)");
        bx = barButton(c, mx, my, bx, null, Icon.REDO, false, this::redo, "Вернуть (Ctrl+Y)");
        bx += 8;
        bx = barToggle(c, mx, my, bx, "Сетка", grid, () -> grid = !grid, "Сетка пикселей");
        if (isSkinSize()) bx = barToggle(c, mx, my, bx, "Разметка", skinGuide, () -> skinGuide = !skinGuide, "Части скина: голова, тело, руки, ноги, глаза Spraute…");
        bx = barToggle(c, mx, my, bx, "Зеркало ↔", mirrorX, () -> mirrorX = !mirrorX, "Рисовать симметрично слева-справа (H)");
        bx = barToggle(c, mx, my, bx, "Зеркало ↕", mirrorY, () -> mirrorY = !mirrorY, "Рисовать симметрично сверху-снизу");
        bx += 8;
        bx = barButton(c, mx, my, bx, "Слой ▾", null, false, () -> layerMenu(mx, y + TOP), "Отразить, повернуть, сдвинуть слой");
        bx = barButton(c, mx, my, bx, "Холст ▾", null, false, () -> canvasMenu(mx, y + TOP), "Размер холста, вписать в окно");
        // zoom on the right
        String z = zoom + "×";
        int zx = x + w - 8 - 18;
        barButtonAt(c, mx, my, zx, "+", () -> zoomBy(1, canvasX + canvasW / 2, canvasY + canvasH / 2), "Приблизить (колесо)");
        zx -= c.width(z) + 10;
        c.text(z, zx + 4, y + 11, Theme.TEXT_DIM);
        barButtonAt(c, mx, my, zx - 20, "−", () -> zoomBy(-1, canvasX + canvasW / 2, canvasY + canvasH / 2), "Отдалить");
    }

    private int barButton(Canvas c, int mx, int my, int bx, String text, Icon icon, boolean accent, Runnable run, String tip) {
        int bw = (text != null ? c.width(text) + 12 : 0) + (icon != null ? 18 : 0) + (text == null ? 2 : 0);
        boolean hov = mx >= bx && mx < bx + bw && my >= y + 5 && my < y + 25;
        c.round(bx, y + 5, bw, 20, 4, accent ? (hov ? Theme.ACCENT_HOVER : Theme.ACCENT) : hov ? Theme.RAISED_HOVER : Theme.RAISED);
        int tx = bx + 6;
        if (icon != null) {
            c.icon(icon, bx + 5, y + 9, accent ? Theme.TEXT_ON_ACCENT : Theme.TEXT_DIM);
            tx += 16;
        }
        if (text != null) c.text(text, icon != null ? tx - 2 : tx, y + 11, accent ? Theme.TEXT_ON_ACCENT : Theme.TEXT);
        hits.add(new Hit(bx, y + 5, bw, 20, run, null, tip));
        return bx + bw + 4;
    }

    private void barButtonAt(Canvas c, int mx, int my, int bx, String text, Runnable run, String tip) {
        boolean hov = mx >= bx && mx < bx + 18 && my >= y + 5 && my < y + 25;
        c.round(bx, y + 5, 18, 20, 4, hov ? Theme.RAISED_HOVER : Theme.RAISED);
        c.textCentered(text, bx + 9, y + 11, Theme.TEXT);
        hits.add(new Hit(bx, y + 5, 18, 20, run, null, tip));
    }

    private int barToggle(Canvas c, int mx, int my, int bx, String text, boolean on, Runnable run, String tip) {
        int bw = c.width(text) + 14;
        boolean hov = mx >= bx && mx < bx + bw && my >= y + 5 && my < y + 25;
        c.round(bx, y + 5, bw, 20, 4, on ? (hov ? 0xFF4A3E8C : 0xFF3D3375) : hov ? Theme.RAISED_HOVER : Theme.RAISED);
        if (on) c.fill(bx + 4, y + 22, bw - 8, 1, Theme.ACCENT);
        c.text(text, bx + 7, y + 11, on ? Theme.TEXT : Theme.TEXT_DIM);
        hits.add(new Hit(bx, y + 5, bw, 20, run, null, tip));
        return bx + bw + 4;
    }

    // ---------------------------------------------------------------- tools on the left

    private void paintTools(Canvas c, int mx, int my) {
        c.fill(x, y + TOP, LEFT, h - TOP, Theme.PANEL);
        c.fill(x + LEFT - 1, y + TOP, 1, h - TOP, Theme.BORDER);
        int ty = y + TOP + 6;
        for (Tool t : Tool.values()) {
            int tx = x + 6;
            boolean on = t == tool, hov = mx >= tx && mx < tx + 32 && my >= ty && my < ty + 28;
            if (on) c.round(tx, ty, 32, 28, 5, 0xFF3D3375);
            else if (hov) c.round(tx, ty, 32, 28, 5, Theme.RAISED);
            if (on) c.fill(x, ty + 6, 2, 16, Theme.ACCENT);
            glyph(c, t.glyph, tx + 7, ty + 5, 2, on ? 0xFFFFFFFF : hov ? Theme.TEXT : Theme.TEXT_DIM);
            hits.add(new Hit(tx, ty, 32, 28, () -> setTool(t), null, t.title + "  [" + t.key + "]"));
            ty += 30;
            if (t == Tool.FILL || t == Tool.ELLIPSE || t == Tool.PICKER) {
                c.fill(x + 10, ty, LEFT - 20, 1, Theme.BORDER);
                ty += 5;
            }
        }
        // colours at the bottom of the bar
        int cy = Math.max(ty + 8, y + h - STATUS - 52);
        c.fill(x + 16, cy + 14, 20, 20, 0xFF000000);
        swatch(c, x + 17, cy + 15, 18, 18, secondary);
        c.fill(x + 6, cy + 4, 20, 20, 0xFF000000);
        swatch(c, x + 7, cy + 5, 18, 18, primary);
        hits.add(new Hit(x + 6, cy + 4, 20, 20, () -> pickColor(true, x + 30, cy), null, "Основной цвет (ЛКМ) — нажмите, чтобы выбрать"));
        hits.add(new Hit(x + 16, cy + 24, 20, 10, () -> pickColor(false, x + 30, cy), null, "Второй цвет (ПКМ)"));
        c.text("⇄", x + 28, cy - 2, Theme.TEXT_MUTED);
        hits.add(new Hit(x + 26, cy - 4, 14, 12, this::swapColors, null, "Поменять цвета (X)"));
    }

    private static void glyph(Canvas c, String[] g, int gx, int gy, int k, int color) {
        for (int r = 0; r < g.length; r++)
            for (int q = 0; q < g[r].length(); q++)
                if (g[r].charAt(q) == '#') c.fill(gx + q * k, gy + r * k, k, k, color);
    }

    private static void swatch(Canvas c, int sx, int sy, int sw, int sh, int argb) {
        if ((argb >>> 24) < 255) {
            for (int yy = 0; yy < sh; yy += 4)
                for (int xx = 0; xx < sw; xx += 4)
                    c.fill(sx + xx, sy + yy, Math.min(4, sw - xx), Math.min(4, sh - yy), ((xx + yy) / 4 & 1) == 0 ? 0xFF8A8A8A : 0xFF5A5A5A);
        }
        if ((argb >>> 24) > 0) c.fill(sx, sy, sw, sh, argb);
    }

    // ---------------------------------------------------------------- the right panel

    private void paintRight(Canvas c, int mx, int my) {
        int rx = x + w - RIGHT, ry = y + TOP;
        c.fill(rx, ry, RIGHT, h - TOP, Theme.PANEL);
        c.fill(rx, ry, 1, h - TOP, Theme.BORDER);
        int px = rx + 10, pw = RIGHT - 20;
        int yy = ry + 8;
        // colour
        yy = header(c, "ЦВЕТ", px, yy);
        swatch(c, px, yy, 40, 28, primary);
        c.outline(px - 1, yy - 1, 42, 30, Theme.BORDER_STRONG);
        hits.add(new Hit(px, yy, 40, 28, () -> pickColor(true, rx - 230, ry + 20), null, "Основной цвет — выбрать"));
        swatch(c, px + 46, yy + 8, 26, 20, secondary);
        c.outline(px + 45, yy + 7, 28, 22, Theme.BORDER_STRONG);
        hits.add(new Hit(px + 46, yy + 8, 26, 20, () -> pickColor(false, rx - 230, ry + 20), null, "Второй цвет — выбрать"));
        c.text(String.format("#%06X", primary & 0xFFFFFF), px + 80, yy + 2, Theme.TEXT);
        int alpha = Math.round((primary >>> 24) / 2.55f);
        c.text("непрозр. " + alpha + "%", px + 80, yy + 16, Theme.TEXT_MUTED);
        yy += 36;
        // the shading ramp of the main colour
        c.text("Шейды (тень → свет)", px, yy, Theme.TEXT_MUTED);
        yy += 11;
        int[] ramp = PixelOps.ramp(primary, 7, hueShift);
        int cw = (pw - 6) / 7;
        for (int i = 0; i < 7; i++) {
            int col = ramp[i], sxx = px + i * (cw + 1);
            swatch(c, sxx, yy, cw, 16, col);
            if (i == 3) c.outline(sxx - 1, yy - 1, cw + 2, 18, 0xFFFFFFFF);
            hits.add(new Hit(sxx, yy, cw, 16, () -> setPrimary(col), () -> secondary = col, String.format("#%06X — ЛКМ основной, ПКМ второй", col & 0xFFFFFF)));
        }
        yy += 22;
        // palette
        String pal = paletteFromImage ? "Палитра картинки" : "Палитра Endesga 32";
        c.text(pal, px, yy, Theme.TEXT_MUTED);
        String other = paletteFromImage ? "Endesga" : "картинки";
        int ow = c.width(other);
        c.text(other, px + pw - ow, yy, Theme.ACCENT);
        hits.add(new Hit(px + pw - ow - 2, yy - 2, ow + 4, 11, () -> paletteFromImage = !paletteFromImage, null, "Сменить палитру"));
        yy += 11;
        List<Integer> colors = paletteFromImage ? imageColors() : endesga();
        for (int r : recent) if (!colors.contains(r)) colors.add(0, r);
        int per = 12, sw = (pw - (per - 1)) / per;
        int shown = Math.min(colors.size(), per * 4);
        for (int i = 0; i < shown; i++) {
            int col = colors.get(i), sxx = px + (i % per) * (sw + 1), syy = yy + (i / per) * (sw + 1);
            swatch(c, sxx, syy, sw, sw, col);
            if (col == primary) c.outline(sxx - 1, syy - 1, sw + 2, sw + 2, 0xFFFFFFFF);
            hits.add(new Hit(sxx, syy, sw, sw, () -> setPrimary(col), () -> secondary = col, String.format("#%08X", col)));
        }
        yy += ((Math.max(1, shown) + per - 1) / per) * (sw + 1) + 8;
        // tool settings
        yy = header(c, tool.title.toUpperCase(Locale.ROOT).replaceAll(" \\(.*", "").replaceAll(":.*", ""), px, yy);
        yy = toolOptions(c, mx, my, px, yy, pw);
        yy += 6;
        // layers
        paintLayers(c, mx, my, px, yy, pw, y + h - STATUS - yy - 4);
    }

    private int header(Canvas c, String text, int px, int yy) {
        c.text(text, px, yy, Theme.TEXT_DIM);
        c.fill(px + c.width(text) + 6, yy + 4, RIGHT - 26 - c.width(text), 1, Theme.BORDER);
        return yy + 13;
    }

    private int toolOptions(Canvas c, int mx, int my, int px, int yy, int pw) {
        switch (tool) {
            case PENCIL, ERASER -> {
                yy = stepper(c, mx, my, px, yy, pw, "Размер", size + " px", () -> size = Math.max(1, size - 1), () -> size = Math.min(16, size + 1));
                if (tool == Tool.PENCIL) yy = toggle(c, mx, my, px, yy, pw, "Pixel-perfect (без «лесенок»)", pixelPerfect, () -> pixelPerfect = !pixelPerfect);
            }
            case SHADE -> {
                yy = stepper(c, mx, my, px, yy, pw, "Размер", size + " px", () -> size = Math.max(1, size - 1), () -> size = Math.min(16, size + 1));
                yy = stepper(c, mx, my, px, yy, pw, "Сдвиг оттенка", Math.round(hueShift) + "°", () -> hueShift = Math.max(0, hueShift - 2), () -> hueShift = Math.min(30, hueShift + 2));
                c.wrapped("Тени холоднее и насыщеннее, свет теплее. Один проход — один шаг на пиксель.", px, yy, pw, Theme.TEXT_MUTED, 1);
                yy += 22;
            }
            case FILL, WAND -> {
                yy = toggle(c, mx, my, px, yy, pw, "Только соседние пиксели", contiguous, () -> contiguous = !contiguous);
                yy = stepper(c, mx, my, px, yy, pw, "Допуск", String.valueOf(tolerance), () -> tolerance = Math.max(0, tolerance - 4), () -> tolerance = Math.min(128, tolerance + 4));
                if (tool == Tool.WAND) {
                    c.text("Shift — добавить к выделению", px, yy, Theme.TEXT_MUTED);
                    yy += 12;
                }
            }
            case RECT, ELLIPSE -> yy = toggle(c, mx, my, px, yy, pw, "Залитая фигура", filled, () -> filled = !filled);
            case LINE -> {
                c.text("Shift — ровно по 45°", px, yy, Theme.TEXT_MUTED);
                yy += 12;
            }
            case GRADIENT -> {
                yy = choice(c, mx, my, px, yy, pw, "Цвета", new String[]{"Шейды", "Осн.→втор.", "По цветам"}, gradColors, v -> gradColors = v);
                yy = stepper(c, mx, my, px, yy, pw, "Ступеней", String.valueOf(gradSteps), () -> gradSteps = Math.max(2, gradSteps - 1), () -> gradSteps = Math.min(12, gradSteps + 1));
                yy = choice(c, mx, my, px, yy, pw, "Дизеринг", new String[]{"нет", "шахматка", "Байер 4×4"}, gradDither, v -> gradDither = v);
                yy = stepper(c, mx, my, px, yy, pw, "Ширина перехода", Math.round(gradSpread * 100) + "%", () -> gradSpread = Math.max(0, gradSpread - 0.125f),
                        () -> gradSpread = Math.min(1, gradSpread + 0.125f));
                yy = choice(c, mx, my, px, yy, pw, "Форма", new String[]{"Линейный", "Круговой"}, gradRadial ? 1 : 0, v -> gradRadial = v == 1);
                yy = choice(c, mx, my, px, yy, pw, "Где", new String[]{"Цвет под курс.", "Непрозрачное", "Весь слой"}, gradArea, v -> gradArea = v);
                yy = toggle(c, mx, my, px, yy, pw, "Обратно (начало — тень)", gradReverse, () -> gradReverse = !gradReverse);
                // the colours it will use
                int[] cols = gradientColors(0xFF808080);
                int cw = Math.max(4, (pw - cols.length) / cols.length);
                for (int i = 0; i < cols.length; i++) swatch(c, px + i * (cw + 1), yy, cw, 8, cols[i]);
                yy += 12;
                c.wrapped("Тяните от света к тени. Выделение ограничивает область.", px, yy, pw, Theme.TEXT_MUTED, 1);
                yy += 22;
            }
            case SELECT -> {
                c.wrapped("Shift — добавить. Ctrl+C / Ctrl+V, Delete — стереть, Ctrl+D — снять.", px, yy, pw, Theme.TEXT_MUTED, 1);
                yy += 22;
            }
            case MOVE -> {
                c.wrapped("Двигает выделенное или весь слой. Стрелки — на пиксель.", px, yy, pw, Theme.TEXT_MUTED, 1);
                yy += 22;
            }
            case PICKER -> {
                c.wrapped("ЛКМ — основной цвет, ПКМ — второй. В любом инструменте: Alt + клик.", px, yy, pw, Theme.TEXT_MUTED, 1);
                yy += 22;
            }
        }
        return yy;
    }

    private int stepper(Canvas c, int mx, int my, int px, int yy, int pw, String label, String value, Runnable minus, Runnable plus) {
        c.text(label, px, yy + 4, Theme.TEXT_DIM);
        int bx = px + pw - 70;
        smallButton(c, mx, my, bx, yy, 16, "−", minus);
        c.textCentered(value, bx + 35, yy + 4, Theme.TEXT);
        smallButton(c, mx, my, bx + 54, yy, 16, "+", plus);
        return yy + 18;
    }

    private int toggle(Canvas c, int mx, int my, int px, int yy, int pw, String label, boolean on, Runnable flip) {
        int bx = px + pw - 24;
        c.round(bx, yy + 2, 24, 12, 6, on ? Theme.ACCENT : Theme.RAISED);
        c.round(on ? bx + 13 : bx + 1, yy + 3, 10, 10, 5, 0xFFFFFFFF);
        c.textEllipsized(label, px, yy + 4, pw - 30, Theme.TEXT_DIM);
        hits.add(new Hit(px, yy, pw, 16, flip, null, null));
        return yy + 18;
    }

    private int choice(Canvas c, int mx, int my, int px, int yy, int pw, String label, String[] opts, int cur, java.util.function.IntConsumer set) {
        c.text(label, px, yy, Theme.TEXT_MUTED);
        yy += 10;
        int bw = (pw - (opts.length - 1) * 2) / opts.length;
        for (int i = 0; i < opts.length; i++) {
            int bx = px + i * (bw + 2), idx = i;
            boolean on = i == cur, hov = mx >= bx && mx < bx + bw && my >= yy && my < yy + 15;
            c.round(bx, yy, bw, 15, 3, on ? 0xFF3D3375 : hov ? Theme.RAISED_HOVER : Theme.RAISED);
            c.textCentered(c.ellipsize(opts[i], bw - 4), bx + bw / 2, yy + 4, on ? Theme.TEXT : Theme.TEXT_DIM);
            hits.add(new Hit(bx, yy, bw, 15, () -> set.accept(idx), null, opts[i]));
        }
        return yy + 19;
    }

    private void smallButton(Canvas c, int mx, int my, int bx, int by, int bw, String text, Runnable run) {
        boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + 15;
        c.round(bx, by, bw, 15, 3, hov ? Theme.RAISED_HOVER : Theme.RAISED);
        c.textCentered(text, bx + bw / 2, by + 4, Theme.TEXT);
        hits.add(new Hit(bx, by, bw, 15, run, null, null));
    }

    private void paintLayers(Canvas c, int mx, int my, int px, int yy, int pw, int avail) {
        yy = header(c, "СЛОИ", px, yy);
        // buttons
        String[][] btn = {{"+", "Новый слой"}, {"⧉", "Копия слоя"}, {"▲", "Выше"}, {"▼", "Ниже"}, {"⤓", "Слить с нижним"}, {"✕", "Удалить слой"}};
        Runnable[] act = {this::newLayer, this::duplicateLayer, () -> moveLayer(1), () -> moveLayer(-1), this::mergeDown, this::deleteLayer};
        int bw = (pw - 10) / 6;
        for (int i = 0; i < 6; i++) {
            int bx = px + i * (bw + 2);
            boolean hov = mx >= bx && mx < bx + bw && my >= yy && my < yy + 16;
            c.round(bx, yy, bw, 16, 3, hov ? Theme.RAISED_HOVER : Theme.RAISED);
            c.textCentered(btn[i][0], bx + bw / 2, yy + 4, i == 5 ? Theme.ERROR : Theme.TEXT);
            hits.add(new Hit(bx, yy, bw, 16, act[i], null, btn[i][1]));
        }
        yy += 20;
        // opacity of the current layer
        PixelDoc.Layer cur = doc.layer();
        yy = stepper(c, mx, my, px, yy, pw, "Непрозрачность", cur.opacity + "%", () -> setOpacity(cur, cur.opacity - 10), () -> setOpacity(cur, cur.opacity + 10));
        int rows = Math.max(1, (avail - 54) / 22);
        layerScroll = Math.max(0, Math.min(layerScroll, doc.layers.size() - rows));
        int shown = 0;
        // top layer first
        for (int li = doc.layers.size() - 1 - layerScroll; li >= 0 && shown < rows; li--, shown++) {
            PixelDoc.Layer l = doc.layers.get(li);
            int ly0 = yy + shown * 22, idx = li;
            boolean on = li == doc.active, hov = mx >= px && mx < px + pw && my >= ly0 && my < ly0 + 20;
            c.round(px, ly0, pw, 20, 4, on ? 0xFF2F2A5C : hov ? Theme.RAISED : Theme.PANEL_ALT);
            if (on) c.fill(px, ly0 + 4, 2, 12, Theme.ACCENT);
            // eye
            glyph(c, l.visible ? new String[]{"..###..", ".#...#.", "#..#..#", ".#...#."} : new String[]{".......", ".......", "#######", "......."}, px + 6, ly0 + 6, 1,
                    l.visible ? Theme.TEXT : Theme.TEXT_MUTED);
            hits.add(new Hit(px + 2, ly0, 16, 20, () -> {
                doc.checkpoint();
                l.visible = !l.visible;
            }, null, l.visible ? "Скрыть слой" : "Показать слой"));
            c.textEllipsized(l.name, px + 22, ly0 + 6, pw - 70, l.visible ? Theme.TEXT : Theme.TEXT_MUTED);
            if (l.opacity < 100) c.text(l.opacity + "%", px + pw - 44, ly0 + 6, Theme.TEXT_MUTED);
            glyph(c, l.locked ? new String[]{".###.", "#...#", "#####", "#####", "#####"} : new String[]{".###.", "#...#", "....#", "#####", "#####"}, px + pw - 14, ly0 + 7, 1,
                    l.locked ? Theme.WARNING : Theme.TEXT_MUTED);
            hits.add(new Hit(px + pw - 18, ly0, 18, 20, () -> l.locked = !l.locked, null, l.locked ? "Слой закрыт — открыть" : "Закрыть слой от рисования"));
            hits.add(new Hit(px + 18, ly0, pw - 36, 20, () -> doc.active = idx, () -> renameLayer(l), "ЛКМ — выбрать, ПКМ или двойной клик — переименовать"));
        }
        if (doc.layers.size() > rows) c.text("колесо — ещё слои", px, yy + rows * 22 + 2, Theme.TEXT_MUTED);
    }

    private void paintStatus(Canvas c, int mx, int my) {
        int sy0 = y + h - STATUS;
        c.fill(x + LEFT, sy0, w - LEFT - RIGHT, STATUS, Theme.PANEL);
        c.fill(x + LEFT, sy0, w - LEFT - RIGHT, 1, Theme.BORDER);
        StringBuilder s = new StringBuilder();
        if (doc.in(curX, curY) && inCanvas(mx, my)) {
            int col = doc.flatAt(curX, curY);
            s.append(curX).append(", ").append(curY).append("   ");
            s.append((col >>> 24) == 0 ? "прозрачный" : String.format("#%06X", col & 0xFFFFFF) + ((col >>> 24) < 255 ? " (" + Math.round((col >>> 24) / 2.55f) + "%)" : ""));
            if (skinGuide && isSkinSize()) for (Part p : parts())
                if (curX >= p.x && curX < p.x + p.w && curY >= p.y && curY < p.y + p.h) {
                    s.append("   ·  ").append(p.name);
                    break;
                }
        } else {
            s.append("Колесо — масштаб, средняя кнопка или пробел — двигать, Alt — пипетка, [ ] — размер кисти, X — поменять цвета");
        }
        c.textEllipsized(s.toString(), x + LEFT + 8, sy0 + 6, w - LEFT - RIGHT - 16, Theme.TEXT_DIM);
    }

    // ================================================================== the skin's layout

    private record Part(String name, int x, int y, int w, int h, int color) {
    }

    private boolean isSkinSize() {
        return doc.w == 64 && (doc.h == 64 || doc.h == 32) || doc.w == 128 && doc.h == 64;
    }

    private List<Part> parts() {
        List<Part> p = new ArrayList<>();
        int head = 0x88FFD25A, body = 0x884FC3F7, arm = 0x887BE08A, leg = 0x88FF8F5A, over = 0x55B48CFF;
        p.add(new Part("Голова · верх", 8, 0, 8, 8, head));
        p.add(new Part("Голова · низ", 16, 0, 8, 8, head));
        p.add(new Part("Голова · правая сторона", 0, 8, 8, 8, head));
        p.add(new Part("Голова · лицо", 8, 8, 8, 8, head));
        p.add(new Part("Голова · левая сторона", 16, 8, 8, 8, head));
        p.add(new Part("Голова · затылок", 24, 8, 8, 8, head));
        p.add(new Part("Шапка (второй слой головы)", 32, 0, 32, 16, over));
        p.add(new Part("Правая нога", 0, 16, 16, 16, leg));
        p.add(new Part("Тело", 16, 16, 24, 16, body));
        p.add(new Part("Правая рука", 40, 16, 16, 16, arm));
        if (doc.h >= 64) {
            p.add(new Part("Правая нога · второй слой", 0, 32, 16, 16, over));
            p.add(new Part("Куртка (второй слой тела)", 16, 32, 24, 16, over));
            p.add(new Part("Правый рукав", 40, 32, 16, 16, over));
            p.add(new Part("Левая нога · второй слой", 0, 48, 16, 16, over));
            p.add(new Part("Левая нога", 16, 48, 16, 16, leg));
            p.add(new Part("Левая рука", 32, 48, 16, 16, arm));
            p.add(new Part("Левый рукав", 48, 48, 16, 16, over));
        }
        if (doc.w == 128) {
            int st = 0xFFFF6BD8;
            p.add(0, new Part("Spraute · зрачки", 0, 0, 2, 2, st));
            p.add(0, new Part("Spraute · белок левого глаза", 0, 2, 2, 2, st));
            p.add(0, new Part("Spraute · белок правого глаза", 4, 2, 2, 2, st));
            p.add(0, new Part("Spraute · брови", 0, 5, 6, 2, st));
            p.add(0, new Part("Spraute · зубы", 6, 6, 2, 1, st));
            p.add(0, new Part("Spraute · рот", 6, 7, 2, 1, st));
        }
        return p;
    }

    // ================================================================== colours

    /** The picture's colours: worked out again only after a change, not every frame. */
    private List<Integer> imageColors() {
        int ver = doc.version * 1000 + (drawing ? 0 : drawVersion);
        if (paletteCache != null && (paletteVersion == ver || drawing)) return new ArrayList<>(paletteCache);
        paletteVersion = ver;
        paletteCache = computeImageColors();
        return new ArrayList<>(paletteCache);
    }

    private List<Integer> computeImageColors() {
        LinkedHashMap<Integer, Integer> count = new LinkedHashMap<>();
        int[] flat = doc.flatten();
        for (int c : flat) if ((c >>> 24) > 0) count.merge(c, 1, Integer::sum);
        List<Integer> cols = new ArrayList<>(count.keySet());
        if (cols.size() > 256) {
            // too many: the commonest
            cols.sort(Comparator.comparingInt((Integer c) -> -count.get(c)));
            cols = new ArrayList<>(cols.subList(0, 256));
        }
        Map<Integer, float[]> hsvs = new HashMap<>();
        for (int c : cols) hsvs.put(c, PixelOps.hsv(c));
        cols.sort(Comparator.comparingDouble((Integer c) -> Math.round(hsvs.get(c)[0] / 30f)).thenComparingDouble(c -> hsvs.get(c)[2]));
        return cols.size() > 48 ? new ArrayList<>(cols.subList(0, 48)) : cols;
    }

    private List<Integer> endesga() {
        List<Integer> l = new ArrayList<>();
        for (int c : ENDESGA) l.add(0xFF000000 | c);
        return l;
    }

    private void setPrimary(int c) {
        primary = c;
        remember(c);
    }

    private void remember(int c) {
        recent.remove((Integer) c);
        recent.add(0, c);
        while (recent.size() > 8) recent.remove(recent.size() - 1);
    }

    private void swapColors() {
        int t = primary;
        primary = secondary;
        secondary = t;
    }

    private void pickColor(boolean main, int px, int py) {
        ColorPicker.open(main ? primary : secondary, Math.max(4, px), Math.max(4, py), c -> {
            if (main) primary = c;
            else secondary = c;
        });
    }

    private int[] gradientColors(int under) {
        int n = gradSteps;
        int[] cols = switch (gradColors) {
            case 1 -> PixelOps.blend(primary, secondary, n);
            case 2 -> {
                int[] o = new int[n];
                for (int i = 0; i < n; i++) o[i] = PixelOps.shade(under, (n - 1) / 2 - i, hueShift);
                yield o;
            }
            default -> {
                int[] r = PixelOps.ramp(primary, n, hueShift);
                // light first: the gradient starts where the light is
                int[] o = new int[n];
                for (int i = 0; i < n; i++) o[i] = r[n - 1 - i];
                yield o;
            }
        };
        if (gradReverse) {
            int[] o = new int[n];
            for (int i = 0; i < n; i++) o[i] = cols[n - 1 - i];
            cols = o;
        }
        return cols;
    }

    // ================================================================== input

    private boolean inCanvas(double mx, double my) {
        return mx >= canvasX && mx < canvasX + canvasW && my >= canvasY && my < canvasY + canvasH;
    }

    private int toPx(double mx) {
        return (int) Math.floor((mx - originX()) / (double) zoom);
    }

    private int toPy(double my) {
        return (int) Math.floor((my - originY()) / (double) zoom);
    }

    @Override
    public String tooltipText(int mx, int my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hh = hits.get(i);
            if (mx >= hh.x && mx < hh.x + hh.w && my >= hh.y && my < hh.y + hh.h) return hh.tip;
        }
        return null;
    }

    @Override
    public boolean mousePressed(double mx, double my, int b) {
        requestFocus();
        if (doc == null) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hh = hits.get(i);
            if (mx >= hh.x && mx < hh.x + hh.w && my >= hh.y && my < hh.y + hh.h) {
                if (b == 1 && hh.right != null) hh.right.run();
                else if (b == 0 && hh.left != null) hh.left.run();
                return true;
            }
        }
        if (!inCanvas(mx, my)) return true;
        int px = toPx(mx), py = toPy(my);
        boolean space = InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_SPACE);
        if (b == 2 || space) {
            panning = true;
            panFromX = mx - panX;
            panFromY = my - panY;
            return true;
        }
        boolean alt = InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT);
        if (alt || tool == Tool.PICKER) {
            if (doc.in(px, py)) {
                int col = doc.flatAt(px, py);
                if (b == 1) secondary = col;
                else setPrimary(col);
            }
            return true;
        }
        button = b;
        sx = lx = px;
        sy = ly = py;
        selectAdd = UI.shift(modsNow());
        switch (tool) {
            case SELECT -> {
                drawing = true;
                if (!selectAdd) selection = null;
                base = null;
                updateRectSelection(px, py);
            }
            case WAND -> {
                if (!doc.in(px, py)) {
                    if (!selectAdd) selection = null;
                    return true;
                }
                boolean[] m = PixelOps.region(doc.layer().px, doc.w, doc.h, px, py, contiguous, tolerance);
                if (selectAdd && selection != null) for (int i = 0; i < m.length; i++) selection[i] |= m[i];
                else selection = m;
            }
            case MOVE -> {
                if (!editable()) return true;
                doc.checkpoint();
                drawing = moving = true;
                base = doc.layer().px.clone();
                lifted = selection != null ? selection.clone() : null;
                floatPx = base.clone();
            }
            case FILL -> {
                if (!editable() || !doc.in(px, py)) return true;
                doc.checkpoint();
                int col = b == 1 ? secondary : primary;
                boolean[] m = PixelOps.region(doc.layer().px, doc.w, doc.h, px, py, contiguous, tolerance);
                int[] lp = doc.layer().px;
                for (int i = 0; i < lp.length; i++) if (m[i] && allowed(i)) lp[i] = col;
                remember(col);
            }
            default -> {
                if (!editable()) return true;
                doc.checkpoint();
                drawing = true;
                base = doc.layer().px.clone();
                touched = new boolean[doc.w * doc.h];
                strokePts.clear();
                if (tool == Tool.GRADIENT) gradMask = gradientMask(px, py);
                if (tool == Tool.PENCIL || tool == Tool.ERASER || tool == Tool.SHADE) {
                    brushAt(px, py);
                    if (tool == Tool.PENCIL) remember(b == 1 ? secondary : primary);
                } else shapeTo(px, py);
            }
        }
        drawVersion++;
        return true;
    }

    private int modsNow() {
        long win = Minecraft.getInstance().getWindow().getWindow();
        int m = 0;
        if (InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_SHIFT)) m |= GLFW.GLFW_MOD_SHIFT;
        return m;
    }

    private boolean editable() {
        PixelDoc.Layer l = doc.layer();
        UI ui = UI.get();
        if (l.locked) {
            if (ui != null) ui.toastInfo("Слой «" + l.name + "» закрыт от рисования (замок справа)");
            return false;
        }
        if (!l.visible) {
            if (ui != null) ui.toastInfo("Слой «" + l.name + "» скрыт — покажите его, чтобы рисовать");
            return false;
        }
        return true;
    }

    private boolean allowed(int i) {
        return selection == null || selection[i];
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (doc == null) return true;
        curX = toPx(mx);
        curY = toPy(my);
        if (panning) {
            panX = (int) (mx - panFromX);
            panY = (int) (my - panFromY);
            return true;
        }
        if (!drawing) return true;
        int px = curX, py = curY;
        switch (tool) {
            case PENCIL, ERASER, SHADE -> {
                int fx = lx, fy = ly;
                PixelOps.line(fx, fy, px, py, (qx, qy) -> {
                    if (qx != lx || qy != ly) brushAt(qx, qy);
                    lx = qx;
                    ly = qy;
                });
            }
            case SELECT -> updateRectSelection(px, py);
            case MOVE -> moveTo(px - sx, py - sy);
            default -> shapeTo(px, py);
        }
        drawVersion++;
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        if (panning) {
            panning = false;
            return true;
        }
        if (drawing && tool == Tool.MOVE && lifted != null) {
            // the selection goes along with what was moved
            int ddx = toPx(mx) - sx, ddy = toPy(my) - sy;
            boolean[] n = new boolean[lifted.length];
            for (int i = 0; i < lifted.length; i++) {
                if (!lifted[i]) continue;
                int tx = i % doc.w + ddx, ty = i / doc.w + ddy;
                if (doc.in(tx, ty)) n[ty * doc.w + tx] = true;
            }
            selection = n;
        }
        if (drawing && tool == Tool.SELECT && selection != null) {
            boolean any = false;
            for (boolean s : selection) any |= s;
            if (!any) selection = null;
        }
        drawing = moving = false;
        base = null;
        touched = null;
        lifted = null;
        gradMask = null;
        drawVersion++;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (doc == null) return true;
        if (mx >= x + w - RIGHT) {
            layerScroll = Math.max(0, layerScroll - (int) Math.signum(delta));
            return true;
        }
        if (inCanvas(mx, my)) {
            zoomBy(delta > 0 ? 1 : -1, mx, my);
        }
        return true;
    }

    private static final int[] ZOOMS = {1, 2, 3, 4, 5, 6, 8, 10, 12, 14, 16, 20, 24, 28, 32, 40, 48, 56, 64};

    private void zoomBy(int dir, double mx, double my) {
        int i = 0;
        while (i < ZOOMS.length - 1 && ZOOMS[i] < zoom) i++;
        int nz = ZOOMS[Math.max(0, Math.min(ZOOMS.length - 1, i + dir))];
        if (nz == zoom) return;
        // keep the pixel under the mouse in place
        double fx = (mx - originX()) / zoom, fy = (my - originY()) / zoom;
        zoom = nz;
        panX += (int) Math.round(mx - (originX() + fx * zoom));
        panY += (int) Math.round(my - (originY() + fy * zoom));
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void render(Canvas c, int mx, int my, float dt) {
        if (doc != null && !drawing && !panning && inCanvas(mx, my)) {
            curX = toPx(mx);
            curY = toPy(my);
        }
        super.render(c, mx, my, dt);
    }

    // ---------------------------------------------------------------- drawing

    private void plot(int px, int py, java.util.function.IntUnaryOperator f) {
        plotOne(px, py, f);
        if (mirrorX) plotOne(doc.w - 1 - px, py, f);
        if (mirrorY) plotOne(px, doc.h - 1 - py, f);
        if (mirrorX && mirrorY) plotOne(doc.w - 1 - px, doc.h - 1 - py, f);
    }

    private void plotOne(int px, int py, java.util.function.IntUnaryOperator f) {
        if (!doc.in(px, py)) return;
        int i = py * doc.w + px;
        if (!allowed(i)) return;
        doc.layer().px[i] = f.applyAsInt(doc.layer().px[i]);
    }

    private void brushAt(int px, int py) {
        int col = button == 1 ? secondary : primary;
        int half = (size - 1) / 2;
        if (tool == Tool.PENCIL && size == 1 && pixelPerfect) {
            pixelPerfectAdd(px, py, col);
            return;
        }
        for (int yy = 0; yy < size; yy++)
            for (int xx = 0; xx < size; xx++) {
                int qx = px - half + xx, qy = py - half + yy;
                switch (tool) {
                    case ERASER -> plot(qx, qy, old -> 0);
                    case SHADE -> {
                        // each pixel one step per stroke
                        plotShade(qx, qy);
                        if (mirrorX) plotShade(doc.w - 1 - qx, qy);
                        if (mirrorY) plotShade(qx, doc.h - 1 - qy);
                    }
                    default -> plot(qx, qy, old -> col);
                }
            }
    }

    private void plotShade(int qx, int qy) {
        if (!doc.in(qx, qy)) return;
        int i = qy * doc.w + qx;
        if (touched[i] || !allowed(i)) return;
        touched[i] = true;
        int[] lp = doc.layer().px;
        lp[i] = PixelOps.shade(base[i], button == 1 ? 1 : -1, hueShift);
    }

    /** One-pixel lines without the doubled corners of an "L": a corner pixel is taken back. */
    private void pixelPerfectAdd(int px, int py, int col) {
        if (!strokePts.isEmpty()) {
            int[] last = strokePts.get(strokePts.size() - 1);
            if (last[0] == px && last[1] == py) return;
        }
        strokePts.add(new int[]{px, py});
        plot(px, py, old -> col);
        int n = strokePts.size();
        if (n >= 3) {
            int[] a = strokePts.get(n - 3), m = strokePts.get(n - 2), c = strokePts.get(n - 1);
            boolean corner = Math.abs(a[0] - c[0]) == 1 && Math.abs(a[1] - c[1]) == 1
                    && (m[0] == a[0] || m[1] == a[1]) && (m[0] == c[0] || m[1] == c[1]);
            if (corner) {
                restorePx(m[0], m[1]);
                if (mirrorX) restorePx(doc.w - 1 - m[0], m[1]);
                if (mirrorY) restorePx(m[0], doc.h - 1 - m[1]);
                strokePts.remove(n - 2);
            }
        }
    }

    private void restorePx(int px, int py) {
        if (doc.in(px, py)) doc.layer().px[py * doc.w + px] = base[py * doc.w + px];
    }

    private void shapeTo(int px, int py) {
        int[] lp = doc.layer().px;
        System.arraycopy(base, 0, lp, 0, lp.length);
        int col = button == 1 ? secondary : primary;
        switch (tool) {
            case LINE -> {
                int ex = px, ey = py;
                if (UI.shift(modsNow())) {
                    int dx = px - sx, dy = py - sy;
                    if (Math.abs(dx) > 2 * Math.abs(dy)) ey = sy;
                    else if (Math.abs(dy) > 2 * Math.abs(dx)) ex = sx;
                    else {
                        int d = Math.max(Math.abs(dx), Math.abs(dy));
                        ex = sx + Integer.signum(dx) * d;
                        ey = sy + Integer.signum(dy) * d;
                    }
                }
                PixelOps.line(sx, sy, ex, ey, (qx, qy) -> plot(qx, qy, o -> col));
            }
            case RECT -> PixelOps.rect(sx, sy, px, py, filled, (qx, qy) -> plot(qx, qy, o -> col));
            case ELLIPSE -> PixelOps.ellipse(sx, sy, px, py, filled, (qx, qy) -> plot(qx, qy, o -> col));
            case GRADIENT -> gradient(px, py);
            default -> {
            }
        }
    }

    private boolean[] gradientMask(int px, int py) {
        boolean[] m;
        int[] lp = doc.layer().px;
        if (selection != null) m = selection.clone();
        else if (gradArea == 0 && doc.in(px, py)) m = PixelOps.region(lp, doc.w, doc.h, px, py, true, tolerance);
        else if (gradArea == 1) {
            m = new boolean[lp.length];
            for (int i = 0; i < lp.length; i++) m[i] = (lp[i] >>> 24) > 0;
        } else {
            m = new boolean[lp.length];
            Arrays.fill(m, true);
        }
        return m;
    }

    private void gradient(int ex, int ey) {
        int[] lp = doc.layer().px;
        float dx = ex - sx, dy = ey - sy, len2 = dx * dx + dy * dy;
        if (len2 < 1) return;
        float len = (float) Math.sqrt(len2);
        int[] fixed = gradColors == 2 ? null : gradientColors(primary);
        for (int i = 0; i < lp.length; i++) {
            if (gradMask != null && !gradMask[i]) continue;
            int px = i % doc.w, py = i / doc.w;
            float t;
            if (gradRadial) t = (float) Math.sqrt((px - sx) * (px - sx) + (py - sy) * (py - sy)) / len;
            else t = ((px - sx) * dx + (py - sy) * dy) / len2;
            int[] cols = fixed != null ? fixed : gradientColors(base[i]);
            if (fixed == null && (base[i] >>> 24) == 0) continue;
            int idx = PixelOps.gradientIndex(t, cols.length, px, py, gradDither, gradSpread);
            lp[i] = cols[idx];
        }
    }

    private void updateRectSelection(int px, int py) {
        if (selectAdd && lifted == null) lifted = selection == null ? new boolean[doc.w * doc.h] : selection.clone();
        boolean[] n = selectAdd && lifted != null ? lifted.clone() : new boolean[doc.w * doc.h];
        int ax = Math.max(0, Math.min(sx, px)), bx = Math.min(doc.w - 1, Math.max(sx, px)), ay = Math.max(0, Math.min(sy, py)), by = Math.min(doc.h - 1, Math.max(sy, py));
        for (int yy = ay; yy <= by; yy++) for (int xx = ax; xx <= bx; xx++) n[yy * doc.w + xx] = true;
        selection = n;
    }

    private void moveTo(int ddx, int ddy) {
        int[] lp = doc.layer().px;
        int n = lp.length;
        // what stays, then what moves on top
        for (int i = 0; i < n; i++) lp[i] = lifted == null || lifted[i] ? 0 : base[i];
        for (int i = 0; i < n; i++) {
            if (lifted != null && !lifted[i]) continue;
            int tx = i % doc.w + ddx, ty = i / doc.w + ddy;
            if (doc.in(tx, ty) && (base[i] >>> 24) > 0) lp[ty * doc.w + tx] = base[i];
        }
    }

    // ---------------------------------------------------------------- keys

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (doc == null) return false;
        boolean ctrl = UI.ctrl(mods), shift = UI.shift(mods);
        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_S -> save();
                case GLFW.GLFW_KEY_Z -> {
                    if (shift) redo();
                    else undo();
                }
                case GLFW.GLFW_KEY_Y -> redo();
                case GLFW.GLFW_KEY_A -> {
                    selection = new boolean[doc.w * doc.h];
                    Arrays.fill(selection, true);
                }
                case GLFW.GLFW_KEY_D -> selection = null;
                case GLFW.GLFW_KEY_C -> copy();
                case GLFW.GLFW_KEY_X -> {
                    copy();
                    clearSelection();
                }
                case GLFW.GLFW_KEY_V -> paste();
                case GLFW.GLFW_KEY_I -> invertSelection();
                default -> {
                    return false;
                }
            }
            drawVersion++;
            return true;
        }
        for (Tool t : Tool.values()) {
            if (key == InputConstants.getKey("key.keyboard." + t.key.toLowerCase(Locale.ROOT)).getValue()) {
                setTool(t);
                return true;
            }
        }
        switch (key) {
            case GLFW.GLFW_KEY_X -> swapColors();
            case GLFW.GLFW_KEY_H -> mirrorX = !mirrorX;
            case GLFW.GLFW_KEY_LEFT_BRACKET -> size = Math.max(1, size - 1);
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> size = Math.min(16, size + 1);
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> zoomBy(1, canvasX + canvasW / 2.0, canvasY + canvasH / 2.0);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> zoomBy(-1, canvasX + canvasW / 2.0, canvasY + canvasH / 2.0);
            case GLFW.GLFW_KEY_0 -> fit();
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> clearSelection();
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                if (tool != Tool.MOVE || !editable()) return false;
                int ddx = key == GLFW.GLFW_KEY_LEFT ? -1 : key == GLFW.GLFW_KEY_RIGHT ? 1 : 0, ddy = key == GLFW.GLFW_KEY_UP ? -1 : key == GLFW.GLFW_KEY_DOWN ? 1 : 0;
                doc.checkpoint();
                base = doc.layer().px.clone();
                lifted = selection != null ? selection.clone() : null;
                moveTo(ddx, ddy);
                if (lifted != null) {
                    boolean[] n = new boolean[lifted.length];
                    for (int i = 0; i < lifted.length; i++) {
                        if (!lifted[i]) continue;
                        int tx = i % doc.w + ddx, ty = i / doc.w + ddy;
                        if (doc.in(tx, ty)) n[ty * doc.w + tx] = true;
                    }
                    selection = n;
                }
                base = null;
                lifted = null;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (selection == null) return false;
                selection = null;
            }
            default -> {
                return false;
            }
        }
        drawVersion++;
        return true;
    }

    private void setTool(Tool t) {
        tool = t;
    }

    // ---------------------------------------------------------------- selection and clipboard

    private void clearSelection() {
        if (selection == null || !editable()) return;
        doc.checkpoint();
        int[] lp = doc.layer().px;
        for (int i = 0; i < lp.length; i++) if (selection[i]) lp[i] = 0;
    }

    private void invertSelection() {
        if (selection == null) {
            selection = new boolean[doc.w * doc.h];
            return;
        }
        for (int i = 0; i < selection.length; i++) selection[i] = !selection[i];
    }

    private void copy() {
        int ax = doc.w, ay = doc.h, bx = -1, by = -1;
        for (int i = 0; i < doc.w * doc.h; i++) {
            if (selection != null && !selection[i]) continue;
            int px = i % doc.w, py = i / doc.w;
            ax = Math.min(ax, px);
            ay = Math.min(ay, py);
            bx = Math.max(bx, px);
            by = Math.max(by, py);
        }
        if (bx < 0) return;
        clipX = ax;
        clipY = ay;
        clipW = bx - ax + 1;
        clipH = by - ay + 1;
        clipboard = new int[clipW * clipH];
        int[] lp = doc.layer().px;
        for (int yy = 0; yy < clipH; yy++)
            for (int xx = 0; xx < clipW; xx++) {
                int i = (ay + yy) * doc.w + ax + xx;
                clipboard[yy * clipW + xx] = selection == null || selection[i] ? lp[i] : 0;
            }
        UI ui = UI.get();
        if (ui != null) ui.toastInfo("Скопировано " + clipW + "×" + clipH);
    }

    /** Pastes into a new layer at the same place, ready to be moved. */
    private void paste() {
        if (clipboard == null) return;
        doc.checkpoint();
        PixelDoc.Layer l = doc.addLayer("Вставка");
        selection = new boolean[doc.w * doc.h];
        for (int yy = 0; yy < clipH; yy++)
            for (int xx = 0; xx < clipW; xx++) {
                int tx = clipX + xx, ty = clipY + yy;
                if (!doc.in(tx, ty)) continue;
                int c = clipboard[yy * clipW + xx];
                l.px[ty * doc.w + tx] = c;
                if ((c >>> 24) > 0) selection[ty * doc.w + tx] = true;
            }
        tool = Tool.MOVE;
    }

    // ---------------------------------------------------------------- layers

    private void newLayer() {
        doc.checkpoint();
        doc.addLayer("Слой " + (doc.layers.size() + 1));
    }

    private void duplicateLayer() {
        doc.checkpoint();
        PixelDoc.Layer src = doc.layer();
        PixelDoc.Layer l = doc.addLayer(src.name + " копия");
        l.px = src.px.clone();
        l.opacity = src.opacity;
    }

    private void moveLayer(int dir) {
        int to = doc.active + dir;
        if (to < 0 || to >= doc.layers.size()) return;
        doc.checkpoint();
        Collections.swap(doc.layers, doc.active, to);
        doc.active = to;
    }

    private void mergeDown() {
        if (doc.active <= 0) return;
        doc.checkpoint();
        doc.mergeDown();
    }

    private void deleteLayer() {
        if (doc.layers.size() <= 1) {
            UI ui = UI.get();
            if (ui != null) ui.toastInfo("Последний слой не удалить");
            return;
        }
        doc.checkpoint();
        doc.layers.remove(doc.active);
        doc.active = Math.max(0, doc.active - 1);
    }

    private void setOpacity(PixelDoc.Layer l, int v) {
        doc.checkpoint();
        l.opacity = Math.max(0, Math.min(100, v));
    }

    private void renameLayer(PixelDoc.Layer l) {
        Dialog.prompt("Слой", "Имя слоя:", l.name, v -> v.isBlank() ? "Введите имя" : null, v -> {
            doc.checkpoint();
            l.name = v;
        });
    }

    @Override
    public boolean mouseDoubleClicked(double mx, double my, int b) {
        int rx = x + w - RIGHT;
        if (mx >= rx) {
            for (Hit hh : hits) if (mx >= hh.x && mx < hh.x + hh.w && my >= hh.y && my < hh.y + hh.h && hh.right != null && hh.tip != null && hh.tip.startsWith("ЛКМ — выбрать")) {
                hh.right.run();
                return true;
            }
        }
        return mousePressed(mx, my, b);
    }

    private void layerMenu(int mx, int my) {
        PopupMenu pm = new PopupMenu();
        pm.item("Отразить по горизонтали", Icon.REFRESH, null, () -> transform(0));
        pm.item("Отразить по вертикали", Icon.REFRESH, null, () -> transform(1));
        pm.item("Повернуть на 180°", Icon.REFRESH, null, () -> transform(2));
        if (doc.w == doc.h) {
            pm.item("Повернуть на 90° по часовой", Icon.REFRESH, null, () -> transform(3));
            pm.item("Повернуть на 90° против", Icon.REFRESH, null, () -> transform(4));
        }
        pm.separator();
        pm.item("Обводка выделенного (основным цветом)", Icon.EDIT, null, this::outline);
        pm.item("Очистить слой", Icon.TRASH, null, () -> {
            if (!editable()) return;
            doc.checkpoint();
            Arrays.fill(doc.layer().px, 0);
        }).color(Theme.ERROR);
        pm.show(mx, my);
    }

    private void transform(int kind) {
        if (!editable()) return;
        doc.checkpoint();
        int[] src = doc.layer().px.clone(), dst = new int[src.length];
        int W = doc.w, H = doc.h;
        for (int yy = 0; yy < H; yy++)
            for (int xx = 0; xx < W; xx++) {
                int v = src[yy * W + xx];
                int tx, ty;
                switch (kind) {
                    case 0 -> { tx = W - 1 - xx; ty = yy; }
                    case 1 -> { tx = xx; ty = H - 1 - yy; }
                    case 2 -> { tx = W - 1 - xx; ty = H - 1 - yy; }
                    case 3 -> { tx = H - 1 - yy; ty = xx; }
                    default -> { tx = yy; ty = W - 1 - xx; }
                }
                dst[ty * W + tx] = v;
            }
        if (selection != null) {
            // only the selected part changes
            for (int i = 0; i < src.length; i++) if (!selection[i]) dst[i] = src[i];
        }
        doc.layer().px = dst;
    }

    /** A one-pixel outline around what is drawn (or selected), outside of it. */
    private void outline() {
        if (!editable()) return;
        doc.checkpoint();
        int[] lp = doc.layer().px, src = lp.clone();
        for (int py = 0; py < doc.h; py++)
            for (int px = 0; px < doc.w; px++) {
                int i = py * doc.w + px;
                if ((src[i] >>> 24) > 0) continue;
                boolean near = false;
                int[][] n = {{px - 1, py}, {px + 1, py}, {px, py - 1}, {px, py + 1}};
                for (int[] q : n) if (doc.in(q[0], q[1]) && (src[q[1] * doc.w + q[0]] >>> 24) > 0
                        && (selection == null || selection[q[1] * doc.w + q[0]])) near = true;
                if (near) lp[i] = primary;
            }
    }

    private void canvasMenu(int mx, int my) {
        PopupMenu pm = new PopupMenu();
        pm.item("Вписать в окно", Icon.SEARCH, "0", this::fit);
        pm.item("Размер холста…", Icon.EDIT, null, this::resizeDialog);
        pm.separator();
        for (int[] s : new int[][]{{16, 16}, {32, 32}, {64, 64}, {128, 64}}) {
            pm.item("Холст " + s[0] + "×" + s[1] + (s[0] == 64 && s[1] == 64 ? " (скин)" : s[0] == 128 ? " (Spraute)" : ""), Icon.IMAGE, null, () -> {
                doc.checkpoint();
                doc.resize(s[0], s[1], 0, 0);
                selection = null;
                fit();
            });
        }
        pm.show(mx, my);
    }

    private void resizeDialog() {
        Dialog d = new Dialog("Размер холста", Icon.IMAGE);
        d.body.add(new Label("Ширина и высота в пикселях (картинка остаётся в левом верхнем углу или по выбору):").color(Theme.TEXT_DIM));
        TextField fw = new TextField(), fh = new TextField();
        fw.setText(String.valueOf(doc.w));
        fh.setText(String.valueOf(doc.h));
        d.body.add(fw);
        d.body.add(fh);
        int[] anchor = {0};
        d.body.add(new Dropdown<>(() -> List.of(0, 1, 2), () -> anchor[0], v -> v == 0 ? "Привязка: левый верхний угол" : v == 1 ? "Привязка: центр" : "Привязка: правый нижний угол",
                v -> anchor[0] = v));
        d.addButton("Отмена", Button.Style.DEFAULT, d::close);
        d.addButton("OK", Button.Style.PRIMARY, () -> {
            try {
                int nw = Math.max(1, Math.min(1024, Integer.parseInt(fw.text().trim()))), nh = Math.max(1, Math.min(1024, Integer.parseInt(fh.text().trim())));
                d.close();
                doc.checkpoint();
                doc.resize(nw, nh, anchor[0], anchor[0]);
                selection = null;
                fit();
            } catch (NumberFormatException e) {
                UI ui = UI.get();
                if (ui != null) ui.toastError("Нужны числа");
            }
        });
        d.open();
    }

    // ================================================================== saving and the tab

    @Override
    public void undo() {
        if (doc.undo()) {
            selection = null;
            drawVersion++;
        }
    }

    @Override
    public void redo() {
        if (doc.redo()) drawVersion++;
    }

    @Override
    public void save() {
        if (doc == null) return;
        try {
            doc.save(image.path());
            ProjectImages.load(project, image);
            UI ui = UI.get();
            if (ui != null) ui.toastSuccess("Сохранено: " + image.name());
        } catch (IOException e) {
            UI ui = UI.get();
            if (ui != null) ui.toastError("Не удалось сохранить: " + e.getMessage());
        }
    }

    /** The window closes: the texture on the screen is not needed any more. */
    public void closed() {
        if (tex != null) {
            Minecraft.getInstance().getTextureManager().release(texId);
            tex = null;
        }
        if (checker != null) {
            Minecraft.getInstance().getTextureManager().release(checkerId);
            checker = null;
        }
    }

    @Override
    public boolean isDirty() {
        return doc != null && doc.dirty();
    }

    @Override
    public Icon icon() {
        return Icon.IMAGE;
    }

    @Override
    public int iconColor() {
        return Theme.INFO;
    }

    @Override
    public String source() {
        return null;
    }

    @Override
    public int scriptId() {
        return -1;
    }

    @Override
    public List<Problem> problems() {
        return List.of();
    }

    @Override
    public Analyzer.Result analysis() {
        return null;
    }

    @Override
    public void reveal(int offset) {
    }

    @Override
    public void focusContent() {
        requestFocus();
    }
}
