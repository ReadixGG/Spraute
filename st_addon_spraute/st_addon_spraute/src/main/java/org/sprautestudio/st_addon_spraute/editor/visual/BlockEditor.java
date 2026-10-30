package org.sprautestudio.st_addon_spraute.editor.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.editor.visual.VisualScript.Block;
import org.sprautestudio.st_addon_spraute.editor.visual.VisualScript.Stack;
import org.sprautestudio.st_addon_spraute.lang.ParamType;
import org.sprautestudio.st_addon_spraute.lang.Specs;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;
import org.sprautestudio.st_addon_spraute.ui.widgets.Dialog;
import org.sprautestudio.st_addon_spraute.ui.widgets.PopupMenu;
import org.sprautestudio.st_addon_spraute.ui.widgets.TextField;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Scratch-style block editor: palette on the left, zoomable canvas with stacks of puzzle
 * blocks on the right. Layout is cached per stack and only visible blocks are drawn.
 */
public class BlockEditor extends Widget implements UI.CursorProvider {
    // ------------------------------------------------------------- geometry
    static final int ROW = 24, HAT = 10, PAD = 8, GAP = 5, PILL_H = 15, ARM = 14;
    static final int SLOT_MIN = 18, MID = 20, BOTTOM = 14, NOTCH_X = 12, NOTCH_W = 14, NOTCH_D = 3;
    static final int PALETTE_W = 210, CAT_W = 50, CAT_STEP = 40;
    /** Width of the ‹› toggle in front of a field, and of an empty socket. */
    static final int ARROW_W = 9, SOCKET_W = 26;
    private static final int SNAP_DIST = 24;

    private VisualScript script;
    public Consumer<BlockEditor> onChange;
    public Supplier<String> positionProvider;

    /** Opens a browser for game assets (animations, models, sounds) and reports the chosen id. */
    public interface AssetPicker {
        void pick(ParamType kind, String current, java.util.function.Consumer<String> onPick);
    }

    public AssetPicker assetPicker;

    private double viewX, viewY, zoom = 1;
    private final List<String> undo = new ArrayList<>(), redo = new ArrayList<>();
    private Block selected;

    // palette
    private final TextField search = new TextField().placeholder("Поиск блоков");
    private final List<PaletteEntry> palette = new ArrayList<>();
    private double paletteScroll, paletteTarget;
    private int paletteHeight;
    private String activeCategory = BlockTypes.CATEGORIES.get(0);

    private record PaletteEntry(Block block, String category, int y, boolean header) {}

    // interaction
    private enum Mode { NONE, PAN, PRESS_BLOCK, DRAG, PRESS_PALETTE, PRESS_PILL, PRESS_VALUE }

    private Mode mode = Mode.NONE;
    private double pressX, pressY;
    private Hit pressHit;
    private Block pressPaletteBlock;
    private Stack dragStack;          // floating group while dragging
    private double dragOffX, dragOffY;
    private Snap snap;
    private BlockCodegen.Result generated;
    private long lastChange;
    private boolean generatedDirty = true;

    private record Hit(Block block, List<Block> list, int index, Stack stack) {}

    private record Snap(List<Block> list, int index, int x, int y, Stack stack) {}

    /** A value block plugged into {@code field} of {@code parent}. */
    private record ValueHit(Block parent, String field, Block value) {}

    /** A field a dragged value block would be plugged into. */
    private record SocketTarget(Block block, String field, int x, int y, int w, int h) {}

    private Block pressBlock;          // block whose pill/arrow was pressed
    private ValueHit pressValue;       // nested value being pressed
    private SocketTarget socketTarget; // where the dragged value would go
    private double catScroll;

    public BlockEditor(VisualScript script) {
        this.script = script;
        this.focusable = true;
        this.viewX = script.viewX;
        this.viewY = script.viewY;
        this.zoom = script.zoom;
        add(search);
        search.onChange = s -> buildPalette();
        buildPalette();
    }

    public VisualScript script() {
        script.viewX = viewX;
        script.viewY = viewY;
        script.zoom = zoom;
        return script;
    }

    public BlockCodegen.Result generated() {
        if (generatedDirty || generated == null) {
            generated = BlockCodegen.generate(script);
            generatedDirty = false;
        }
        return generated;
    }

    private Font font() {
        return Minecraft.getInstance().font;
    }

    // ================================================================ changes

    private void snapshot() {
        undo.add(script().toJson());
        if (undo.size() > 100) undo.remove(0);
        redo.clear();
    }

    private void changed() {
        for (Stack s : script.stacks) s.dirty = true;
        generatedDirty = true;
        lastChange = System.currentTimeMillis();
        if (onChange != null) onChange.accept(this);
    }

    public void undo() {
        if (undo.isEmpty()) return;
        redo.add(script().toJson());
        restore(undo.remove(undo.size() - 1));
    }

    public void redo() {
        if (redo.isEmpty()) return;
        undo.add(script().toJson());
        restore(redo.remove(redo.size() - 1));
    }

    private void restore(String json) {
        script = VisualScript.fromJson(json);
        selected = null;
        changed();
    }

    // ================================================================ layout

    private int textW(String s) {
        return font().width(s);
    }

    private static String shown(Block b, BlockTypes.Field f) {
        String v = b.field(f.name()).trim();
        if (v.isEmpty()) return f.label();
        if (f.kind() == ParamType.SYSTEM) return Specs.systemTitle(v);
        if (f.kind() == ParamType.SPEAKER && v.equals("player")) return "игрок";
        if (f.kind() == ParamType.EASE) return org.sprautestudio.st_addon_spraute.cutscene.Ease.TITLES.getOrDefault(v, v);
        if (f.kind() == ParamType.TARGET) {
            if (v.equals("player")) return "игрок";
            if (v.equals("\"mob\"")) return "ближайший моб";
            if (v.startsWith("\"")) return "моб " + v.replace("\"", "").replace("minecraft:", "");
        }
        return v;
    }

    private static org.sprautestudio.st_addon_spraute.engine.client.ChatHeads.Params headParams(Block b) {
        return new org.sprautestudio.st_addon_spraute.engine.client.ChatHeads.Params(num(b.field("headscale"), 1), (int) num(b.field("headx"), 0),
                (int) num(b.field("heady"), 0), (int) num(b.field("headyaw"), 0), b.field("headbone").isBlank() ? "auto" : b.field("headbone").replace("\"", ""));
    }

    private static float num(String s, float def) {
        try {
            return s == null || s.isBlank() ? def : Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean isDropdown(BlockTypes.Field f) {
        if (f.kind() == null) return false;
        return switch (f.kind()) {
            case CHAR, LABEL, EMOTION, KEY, BOOL, TARGET, SPEAKER, LAYER, MOVETYPE, ANIM, ENTITY, SOUND, ITEM, FUNC, BRANCH, IMAGE, GUI, GUIEL, VAR, CHOICE, SYSTEM, EASE, CUTSCENE, VFX, SHOP, QUEST, ZONE, FIGHT, PATH -> true;
            default -> false;
        };
    }

    private int pillW(Block b, BlockTypes.Field f) {
        return Math.max(isDropdown(f) ? 30 : 22, textW(shown(b, f)) + (isDropdown(f) ? 20 : 12));
    }

    private static boolean optionRows(Block b) {
        return b.type.equals("choice") || b.type.equals("switch");
    }

    private static String optionLabel(Block b) {
        return b.type.equals("switch") ? "если =" : "ответ";
    }

    private static int arrowW(BlockTypes.Field f) {
        return f.acceptsValue() ? ARROW_W : 0;
    }

    /** False for a field the selected mode of a block does not use (e.g. the target of "stops looking"). */
    private static boolean shown(Block b, BlockTypes.Part p) {
        if (p.field() == null) return true;
        BlockTypes.Variant v = BlockTypes.variant(b.type);
        if (v == null || p.field().name().equals(v.field())) return true;
        return v.template(b.field(v.field()).trim()).contains("%" + p.field().name());
    }

    /** Width of the field area after the toggle: plugged value, empty socket or the pill. */
    private int fieldW(Block b, BlockTypes.Field f) {
        Block in = b.inputs.get(f.name());
        if (in != null) return in.lw;
        if (b.sockets.contains(f.name())) return SOCKET_W;
        return pillW(b, f);
    }

    /** Lays out a block list starting at (x, y); returns the total height. */
    private int layoutList(List<Block> list, int x, int y) {
        int yy = y;
        for (Block b : list) {
            layoutBlock(b, x, yy);
            yy += b.lh;
        }
        return yy - y;
    }

    /** Width after which a block set to "several lines" moves its next part to a new line. */
    private static final int WRAP_W = 250;

    /** Text of a folded block: its first words, then how much is hidden inside. */
    private String collapsedLabel(Block b) {
        String head = "";
        for (BlockTypes.Part p : b.typeInfo().parts()) {
            if (p.field() == null) {
                head = p.text().trim();
                break;
            }
        }
        int inside = 0;
        for (List<Block> slot : b.slots) inside += countBlocks(slot);
        return "▸ " + head + " …" + (inside > 0 ? "  (" + inside + " " + plural(inside) + ")" : "");
    }

    private static int countBlocks(List<Block> list) {
        int n = 0;
        for (Block b : list) {
            n++;
            for (List<Block> slot : b.slots) n += countBlocks(slot);
        }
        return n;
    }

    private static String plural(int n) {
        int m = n % 100;
        if (m >= 11 && m <= 14) return "блоков";
        return switch (n % 10) {
            case 1 -> "блок";
            case 2, 3, 4 -> "блока";
            default -> "блоков";
        };
    }

    /** Width of a header part (laying out a plugged value block first). */
    private int partWidth(Block b, BlockTypes.Part p) {
        if (p.field() == null) return textW(p.text());
        Block in = b.inputs.get(p.field().name());
        if (in != null) layoutBlock(in, 0, 0);
        return arrowW(p.field()) + fieldW(b, p.field());
    }

    private void layoutBlock(Block b, int x, int y) {
        BlockTypes.Type t = b.typeInfo();
        boolean value = t.isValue();
        List<BlockTypes.Part> parts = t.parts();
        int n = parts.size();
        b.partX = new int[n];
        b.partW = new int[n];
        b.partY = new int[n];
        b.partRH = new int[n];
        int[] rowOf = new int[n];
        boolean folded = b.collapsed && !value;
        boolean wrap = b.multiline && !value && !folded;
        int pad = value ? (t.shape() == BlockTypes.Shape.BOOLEAN ? 10 : 7) : PAD;
        int gap = value ? 4 : GAP;
        int cx = pad, row = 0, maxX = pad;
        List<Integer> rowInner = new ArrayList<>();
        rowInner.add(PILL_H);
        for (int i = 0; i < n; i++) {
            BlockTypes.Part p = parts.get(i);
            rowOf[i] = row;
            if (folded || !shown(b, p)) {
                b.partX[i] = cx;
                b.partW[i] = 0;
                continue;
            }
            int pw = partWidth(b, p);
            // several lines: a label starts a new line when it and its field would not fit
            if (wrap && p.field() == null && cx > pad) {
                int look = pw + gap;
                if (i + 1 < n && parts.get(i + 1).field() != null && shown(b, parts.get(i + 1))) look += partWidth(b, parts.get(i + 1));
                if (cx + look > WRAP_W) {
                    row++;
                    rowInner.add(PILL_H);
                    cx = pad;
                    rowOf[i] = row;
                }
            }
            if (p.field() != null) {
                Block in = b.inputs.get(p.field().name());
                if (in != null) rowInner.set(row, Math.max(rowInner.get(row), in.lh));
            }
            b.partX[i] = cx;
            b.partW[i] = pw;
            cx += pw + gap;
            maxX = Math.max(maxX, cx);
        }
        b.lx = x;
        b.ly = y;
        if (value) {
            b.lh = Math.max(PILL_H + 4, rowInner.get(0) + 4);
            b.headerH = b.lh;
            b.lw = Math.max(24, cx - 4 + pad);
            for (int i = 0; i < n; i++) {
                b.partY[i] = 0;
                b.partRH[i] = b.lh;
            }
        } else {
            int hat = t.shape() == BlockTypes.Shape.HAT ? HAT : 0;
            int[] top = new int[rowInner.size()], rh = new int[rowInner.size()];
            int yy0 = hat;
            for (int r = 0; r < rowInner.size(); r++) {
                // later lines sit a little closer together
                rh[r] = Math.max(r == 0 ? ROW : ROW - 4, rowInner.get(r) + (r == 0 ? 9 : 5));
                top[r] = yy0;
                yy0 += rh[r];
            }
            b.headerH = yy0 + (rowInner.size() > 1 ? 3 : 0);
            if (rowInner.size() > 1) rh[rh.length - 1] += 3;
            for (int i = 0; i < n; i++) {
                b.partY[i] = top[rowOf[i]];
                b.partRH[i] = rh[rowOf[i]];
            }
            int minW = t.shape() == BlockTypes.Shape.C && !folded ? 120 : 60;
            b.lw = folded ? Math.max(minW, PAD + textW(collapsedLabel(b)) + PAD) : Math.max(minW, maxX - GAP + PAD);
        }
        // place plugged value blocks inside their line
        for (int i = 0; i < n; i++) {
            BlockTypes.Field f = parts.get(i).field();
            if (f == null || b.partW[i] == 0) continue;
            Block in = b.inputs.get(f.name());
            if (in != null) layoutBlock(in, x + b.partX[i] + arrowW(f), y + b.partY[i] + (b.partRH[i] - in.lh) / 2);
        }
        if (value) return;
        int yy = y + b.headerH;
        if (b.collapsed) {
            b.slotTop = null;
            b.slotH = null;
        }
        if (t.shape() == BlockTypes.Shape.C && !b.collapsed) {
            int ns = b.slots.size();
            b.slotTop = new int[ns];
            b.slotH = new int[ns];
            boolean options = optionRows(b);
            for (int i = 0; i < ns; i++) {
                if (options || i > 0) {
                    if (options && i < b.options.size()) {
                        b.lw = Math.max(b.lw, PAD + textW(optionLabel(b)) + GAP + Math.max(22, textW(b.options.get(i)) + 12) + PAD);
                    }
                    yy += MID;
                }
                b.slotTop[i] = yy;
                int hgt = layoutList(b.slots.get(i), x + ARM, yy);
                b.slotH[i] = Math.max(SLOT_MIN, hgt);
                yy += b.slotH[i];
            }
            yy += BOTTOM;
        }
        b.lh = yy - y;
    }

    private void layoutStack(Stack s) {
        if (!s.dirty) return;
        int hgt = layoutList(s.blocks, (int) s.x, (int) s.y);
        s.bh = hgt;
        s.bw = 0;
        for (Block b : s.blocks) s.bw = Math.max(s.bw, extentRight(b) - (int) s.x);
        s.dirty = false;
    }

    private int extentRight(Block b) {
        int r = b.lx + b.lw;
        if (!b.collapsed) for (List<Block> slot : b.slots) for (Block c : slot) r = Math.max(r, extentRight(c));
        return r;
    }

    // ================================================================ coords

    private int canvasX() {
        return x + PALETTE_W;
    }

    private int canvasW() {
        return w - PALETTE_W;
    }

    private double toWorldX(double mx) {
        return (mx - canvasX()) / zoom + viewX;
    }

    private double toWorldY(double my) {
        return (my - y) / zoom + viewY;
    }

    private int toScreenX(double wx) {
        return (int) Math.round(canvasX() + (wx - viewX) * zoom);
    }

    private int toScreenY(double wy) {
        return (int) Math.round(y + (wy - viewY) * zoom);
    }

    @Override
    public void layout() {
        search.bounds(x + CAT_W + 4, y + 4, PALETTE_W - CAT_W - 8, 16);
    }

    @Override
    public int cursor(int mx, int my) {
        if (mode == Mode.PAN) return UI.CURSOR_MOVE;
        if (mode == Mode.DRAG) return UI.CURSOR_HAND;
        return mx >= canvasX() ? UI.CURSOR_ARROW : UI.CURSOR_HAND;
    }

    // ================================================================ palette

    private void buildPalette() {
        palette.clear();
        String q = search.text().trim().toLowerCase();
        int yy = 0;
        for (String cat : BlockTypes.CATEGORIES) {
            List<BlockTypes.Type> types = BlockTypes.inCategory(cat);
            List<BlockTypes.Type> shownTypes = new ArrayList<>();
            for (BlockTypes.Type t : types) {
                if (q.isEmpty() || searchText(t).contains(q)) shownTypes.add(t);
            }
            if (shownTypes.isEmpty()) continue;
            palette.add(new PaletteEntry(null, cat, yy, true));
            yy += 20;
            for (BlockTypes.Type t : shownTypes) {
                Block b = Block.create(t.key());
                layoutBlock(b, 0, 0);
                palette.add(new PaletteEntry(b, cat, yy, false));
                yy += b.lh + (t.shape() == BlockTypes.Shape.C ? 8 : 6);
            }
            yy += 6;
        }
        paletteHeight = yy;
        paletteTarget = paletteScroll = 0;
    }

    private static String searchText(BlockTypes.Type t) {
        StringBuilder sb = new StringBuilder(t.key()).append(' ');
        for (BlockTypes.Part p : t.parts()) if (p.text() != null) sb.append(p.text()).append(' ');
        return sb.toString().toLowerCase();
    }

    private int paletteTop() {
        return y + 24;
    }

    private void scrollPaletteTo(String category) {
        for (PaletteEntry e : palette) {
            if (e.header && e.category.equals(category)) {
                paletteTarget = Math.max(0, Math.min(maxPaletteScroll(), e.y));
                return;
            }
        }
    }

    private int maxPaletteScroll() {
        return Math.max(0, paletteHeight - (h - 24) + 20);
    }

    // ================================================================ render

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        if (Math.abs(paletteTarget - paletteScroll) > 0.5) paletteScroll += (paletteTarget - paletteScroll) * Math.min(1, dt * 16);
        else paletteScroll = paletteTarget;
        if (generatedDirty && System.currentTimeMillis() - lastChange > 250) generated();

        paintCanvas(c, mx, my);
        paintPalette(c, mx, my);
    }

    private void paintCanvas(Canvas c, int mx, int my) {
        int cx = canvasX(), cw = canvasW();
        c.fill(cx, y, cw, h, Theme.CODE_BG);
        // dotted grid
        double step = 24 * zoom;
        if (step >= 8) {
            double ox = -((viewX * zoom) % step);
            double oy = -((viewY * zoom) % step);
            for (double gx = ox; gx < cw; gx += step) {
                for (double gy = oy; gy < h; gy += step) {
                    c.fill(cx + (int) gx, y + (int) gy, 1, 1, 0x22FFFFFF);
                }
            }
        }
        c.pushClip(cx, y, cw, h);
        c.pushTransform(cx - (float) (viewX * zoom), y - (float) (viewY * zoom), (float) zoom);
        double vx0 = viewX, vy0 = viewY, vx1 = viewX + cw / zoom, vy1 = viewY + h / zoom;
        Stack main = script.mainStack();
        for (Stack s : script.stacks) {
            layoutStack(s);
            if (s.x > vx1 || s.y > vy1 || s.x + s.bw < vx0 || s.y + s.bh + NOTCH_D < vy0) continue; // stack culled
            boolean dim = s != main && !(!s.blocks.isEmpty() && s.blocks.get(0).type.equals("func"));
            paintList(c, s.blocks, vy0, vy1, dim);
        }
        if (snap != null && dragStack != null) {
            int sw = Math.max(60, dragStack.bw);
            c.round(snap.x, snap.y - 2, sw, 4, 2, 0xCCFFFFFF);
            c.round(snap.x, snap.y + 2, sw, Math.min(dragStack.bh, 60), 4, 0x22FFFFFF);
        }
        if (socketTarget != null && dragStack != null) {
            SocketTarget st = socketTarget;
            c.outline(st.x() - 2, st.y() - 2, st.w() + 4, st.h() + 4, 0xFFFFFFFF);
            c.outline(st.x() - 1, st.y() - 1, st.w() + 2, st.h() + 2, 0x88FFFFFF);
        }
        if (dragStack != null) {
            layoutStack(dragStack);
            paintShadow(c, dragStack);
            paintList(c, dragStack.blocks, -1e9, 1e9, false);
        }
        paintFlash(c);
        c.popTransform();
        c.popClip();

        // canvas HUD
        String zoomText = Math.round(zoom * 100) + "%";
        c.text(zoomText, cx + cw - c.width(zoomText) - 8, y + h - 14, Theme.TEXT_MUTED);
        if (main == null) {
            c.textCentered("Перетащите блок «когда начинается сюжет» из категории «Поток»", cx + cw / 2, y + 12, Theme.WARNING);
        }
        if (dragStack != null && mx < cx) {
            c.fill(x, y, PALETTE_W, h, 0x33F0607A);
        }
    }

    private void paintShadow(Canvas c, Stack s) {
        c.round(s.blocks.get(0).lx + 4, s.blocks.get(0).ly + 6, Math.max(40, s.bw), s.bh, 4, 0x44000000);
    }

    /** Draws a list of blocks, skipping those outside [top, bottom] in world space. */
    private void paintList(Canvas c, List<Block> list, double top, double bottom, boolean dim) {
        for (Block b : list) {
            if (b.ly > bottom) break;
            if (b.ly + b.lh + NOTCH_D < top) continue;
            paintBlock(c, b, top, bottom, dim);
        }
    }

    /** Hexagon used by yes/no blocks and sockets. */
    private static void hexagon(Canvas c, int x, int y, int w, int h, int color) {
        float half = h / 2f;
        for (int i = 0; i < h; i++) {
            int inset = Math.round(Math.abs(i + 0.5f - half) * Math.min(half, w / 4f) / half);
            c.fill(x + inset, y + i, w - 2 * inset, 1, color);
        }
    }

    private void paintValue(Canvas c, Block b, boolean dim) {
        BlockTypes.Type t = b.typeInfo();
        int color = Theme.category(t.category());
        if (dim) color = Theme.lerp(color, 0xFF3A3D4D, 0.45f);
        int dark = Theme.darker(color, 0.72f);
        if (t.shape() == BlockTypes.Shape.BOOLEAN) {
            hexagon(c, b.lx, b.ly + 1, b.lw, b.lh, dark);
            hexagon(c, b.lx, b.ly, b.lw, b.lh, color);
        } else {
            c.round(b.lx, b.ly + 1, b.lw, b.lh, b.lh / 2, dark);
            c.round(b.lx, b.ly, b.lw, b.lh, b.lh / 2, color);
        }
        paintParts(c, b, b.ly, b.lh, dark, dim);
        if (b == selected) c.outline(b.lx - 1, b.ly - 1, b.lw + 2, b.lh + 2, 0xCCFFFFFF);
        if (b.error) c.outline(b.lx - 1, b.ly - 1, b.lw + 2, b.lh + 2, Theme.ERROR);
    }

    /** Header texts, toggles, pills, sockets and plugged values of a block row. */
    private void paintParts(Canvas c, Block b, int hy, int hh, int dark, boolean dim) {
        List<BlockTypes.Part> parts = b.typeInfo().parts();
        int hy0 = hy, hh0 = hh;
        for (int i = 0; i < parts.size(); i++) {
            BlockTypes.Part p = parts.get(i);
            if (b.partW[i] == 0) continue;
            hy = b.partY != null ? b.ly + b.partY[i] : hy0;
            hh = b.partRH != null ? b.partRH[i] : hh0;
            int py = hy + (hh - PILL_H) / 2;
            int px = b.lx + b.partX[i];
            if (p.field() == null) {
                c.text(p.text(), px, hy + (hh - 8) / 2, 0xFFFFFFFF);
                continue;
            }
            BlockTypes.Field f = p.field();
            int aw = arrowW(f);
            Block in = b.inputs.get(f.name());
            boolean socket = in != null || b.sockets.contains(f.name());
            if (aw > 0) {
                // ‹› toggle: literal field <-> socket for a value block
                int ay = hy + hh / 2;
                int col = socket ? 0xFFFFFFFF : 0x99FFFFFF;
                c.fill(px + 1, ay - 1, 1, 3, col);
                c.fill(px + 2, ay - 2, 1, 1, col);
                c.fill(px + 2, ay + 2, 1, 1, col);
                c.fill(px + 6, ay - 1, 1, 3, col);
                c.fill(px + 5, ay - 2, 1, 1, col);
                c.fill(px + 5, ay + 2, 1, 1, col);
            }
            int fx = px + aw;
            if (in != null) {
                paintValue(c, in, dim);
            } else if (socket) {
                int sh = PILL_H;
                if (f.booleanSocket()) hexagon(c, fx, py, SOCKET_W, sh, Theme.alpha(dark, 0.85f));
                else c.round(fx, py, SOCKET_W, sh, sh / 2, Theme.alpha(dark, 0.85f));
                c.textCentered("…", fx + SOCKET_W / 2, py + 3, 0x88FFFFFF);
            } else {
                String v = b.field(f.name()).trim();
                pill(c, fx, py, b.partW[i] - aw, shown(b, f), isDropdown(f), v.isEmpty(), dark);
            }
        }
    }

    private void paintBlock(Canvas c, Block b, double top, double bottom, boolean dim) {
        BlockTypes.Type t = b.typeInfo();
        if (t.isValue()) {
            paintValue(c, b, dim);
            return;
        }
        int color = Theme.category(t.category());
        if (dim) color = Theme.lerp(color, 0xFF3A3D4D, 0.45f);
        int dark = Theme.darker(color, 0.72f);
        int x = b.lx, y = b.ly, w = b.lw;
        boolean topNotch = t.shape() != BlockTypes.Shape.HAT;
        boolean bottomBump = t.shape() != BlockTypes.Shape.CAP;

        if (t.shape() == BlockTypes.Shape.HAT) {
            c.round(x, y, Math.min(w, 96), HAT * 2 + 2, 10, color);
        }
        int hy = y + (t.shape() == BlockTypes.Shape.HAT ? HAT : 0);
        int hh = b.headerH - (hy - y);
        if (t.shape() == BlockTypes.Shape.C && !b.slots.isEmpty() && !b.collapsed) {
            boolean choice = optionRows(b);
            // header (plus the first answer row of a choice) with an inner bump for the nested blocks
            int headerExtent = b.slotTop[0] - hy;
            shape(c, x, hy, w, headerExtent, color, dark, topNotch ? x + NOTCH_X : -1, x + ARM + NOTCH_X);
            for (int i = 0; i < b.slots.size(); i++) {
                int st = b.slotTop[i], sh = b.slotH[i];
                c.fill(x, st, ARM, sh, color);
                c.fill(x, st, 2, sh, Theme.lerp(color, 0xFFFFFFFF, 0.08f));
                boolean last = i == b.slots.size() - 1;
                int barY = st + sh;
                int barH = last ? BOTTOM : MID;
                shape(c, x, barY, w, barH, color, dark, x + ARM + NOTCH_X, last ? (bottomBump ? x + NOTCH_X : -1) : x + ARM + NOTCH_X);
            }
            // slot labels (else / choice answers)
            for (int i = 0; i < b.slots.size(); i++) {
                if (!choice && i == 0) continue;
                int rowY = b.slotTop[i] - MID;
                if (choice && i >= b.options.size()) {
                    c.text("иначе", x + PAD, rowY + 6, 0xFFFFFFFF);
                } else if (choice) {
                    c.text(optionLabel(b), x + PAD, rowY + 6, 0xFFFFFFFF);
                    String opt = b.options.get(i);
                    int px = x + PAD + textW(optionLabel(b)) + GAP;
                    pill(c, px, rowY + (MID - PILL_H) / 2, Math.max(22, textW(opt) + 12), opt.isEmpty() ? "…" : opt, false, false, dark);
                } else {
                    c.text(t.slotLabels().get(i), x + PAD, rowY + 6, 0xFFFFFFFF);
                }
            }
            for (List<Block> slot : b.slots) paintList(c, slot, top, bottom, dim);
        } else {
            shape(c, x, hy, w, hh, color, dark, topNotch ? x + NOTCH_X : -1, bottomBump ? x + NOTCH_X : -1);
        }

        paintParts(c, b, hy, hh, dark, dim);
        if (b.collapsed) c.text(collapsedLabel(b), x + PAD, hy + (hh - 8) / 2, 0xFFFFFFFF);
        if (b == selected) c.outline(x - 1, y - 1, w + 2, b.lh + 2, 0xCCFFFFFF);
        if (b.error) {
            c.outline(x - 1, hy - 1, w + 2, hh + 2, Theme.ERROR);
            c.round(x + w - 6, hy - 5, 11, 11, 5, Theme.ERROR);
            c.text("!", x + w - 2, hy - 3, 0xFFFFFFFF);
        }
    }

    /**
     * Block body: rounded rect whose top has a dent at {@code dentX} and whose bottom has a
     * bump at {@code bumpX} (-1 = none).
     */
    private void shape(Canvas c, int x, int y, int w, int h, int color, int dark, int dentX, int bumpX) {
        c.round(x, y + NOTCH_D, w, h - NOTCH_D, 3, color);
        for (int i = 0; i < NOTCH_D; i++) {
            int inset = i == 0 ? 2 : i == 1 ? 1 : 0;
            if (dentX < 0) {
                c.fill(x + inset, y + i, w - 2 * inset, 1, color);
            } else {
                c.fill(x + inset, y + i, dentX + i - x - inset, 1, color);
                int r = dentX + NOTCH_W - i;
                c.fill(r, y + i, x + w - inset - r, 1, color);
            }
        }
        // bottom shading
        c.fill(x + 2, y + h - 1, w - 4, 1, dark);
        if (bumpX >= 0) {
            for (int j = 0; j < NOTCH_D; j++) {
                c.fill(bumpX + j, y + h + j, NOTCH_W - 2 * j, 1, j == NOTCH_D - 1 ? dark : color);
            }
        }
        c.fill(x + 2, y + NOTCH_D, 1, 1, 0x22FFFFFF);
    }

    private void pill(Canvas c, int x, int y, int w, String text, boolean dropdown, boolean empty, int dark) {
        if (dropdown) {
            c.round(x, y, w, PILL_H, 4, empty ? Theme.alpha(dark, 0.6f) : dark);
            c.text(text, x + 6, y + 4, empty ? 0xAAFFFFFF : 0xFFFFFFFF);
            c.icon(Icon.CHEVRON_DOWN, x + w - 12, y + 3, 0xCCFFFFFF);
        } else {
            c.round(x, y, w, PILL_H, 7, empty ? 0x66FFFFFF : 0xFFFFFFFF);
            c.text(text, x + 6, y + 4, empty ? 0xFF8A8FA6 : 0xFF3B4054);
        }
    }

    private void paintPalette(Canvas c, int mx, int my) {
        c.fill(x, y, PALETTE_W, h, Theme.PANEL);
        c.fill(x + PALETTE_W - 1, y, 1, h, Theme.BORDER);
        // category strip
        c.fill(x, y, CAT_W, h, Theme.BG_DEEP);
        catScroll = Math.max(0, Math.min(maxCatScroll(), catScroll));
        int cy = y + 6 - (int) Math.round(catScroll);
        int step = catStep();
        c.pushClip(x, y, CAT_W, h);
        for (String cat : BlockTypes.CATEGORIES) {
            boolean active = cat.equals(activeCategory);
            boolean hov = mx >= x && mx < x + CAT_W && my >= cy && my < cy + step - 2 && isHoveredDeep();
            if (active) c.fill(x, cy, 2, 36, Theme.category(cat));
            if (hov || active) c.round(x + 3, cy, CAT_W - 6, 38, 4, active ? 0x22FFFFFF : 0x11FFFFFF);
            int col = Theme.category(cat);
            c.round(x + CAT_W / 2 - 8, cy + 4, 16, 16, 8, col);
            c.round(x + CAT_W / 2 - 4, cy + 8, 8, 8, 4, Theme.lerp(col, 0xFFFFFFFF, 0.35f));
            String label = shortName(cat);
            c.pushTransform(x + CAT_W / 2f, cy + 25, 0.75f);
            c.textCentered(label, 0, 0, active ? Theme.TEXT : Theme.TEXT_DIM);
            c.popTransform();
            cy += step;
        }
        if (maxCatScroll() > 0) {
            // scroll hint: a thin bar on the strip
            int track = h - 8;
            int bar = Math.max(16, track * h / (BlockTypes.CATEGORIES.size() * step + 12));
            int by = y + 4 + (int) ((track - bar) * catScroll / maxCatScroll());
            c.round(x + CAT_W - 3, by, 2, bar, 1, 0x55FFFFFF);
        }
        c.popClip();
        // block list
        int lx = x + CAT_W + 6;
        int top = paletteTop();
        c.pushClip(x + CAT_W, top, PALETTE_W - CAT_W, h - (top - y));
        c.pushTransform(lx, top - (float) paletteScroll, 1);
        double viewTop = paletteScroll, viewBottom = paletteScroll + h;
        String visibleCat = null;
        for (PaletteEntry e : palette) {
            int ey = e.y;
            int eh = e.header ? 20 : e.block.lh;
            if (ey > viewBottom) break;
            if (ey + eh < viewTop) continue;
            if (visibleCat == null && ey + eh > viewTop + 4) visibleCat = e.category;
            if (e.header) {
                c.text(e.category, 0, ey + 7, Theme.category(e.category));
                c.fill(c.width(e.category) + 6, ey + 11, PALETTE_W - CAT_W - 24 - c.width(e.category), 1, Theme.BORDER);
            } else {
                e.block.lx = 0;
                layoutBlock(e.block, 0, ey);
                paintBlock(c, e.block, -1e9, 1e9, false);
            }
        }
        c.popTransform();
        c.popClip();
        if (visibleCat != null && mode == Mode.NONE) activeCategory = visibleCat;
    }

    /** Height of one category button; the strip scrolls with the mouse wheel when they do not fit. */
    private int catStep() {
        return CAT_STEP;
    }

    private int maxCatScroll() {
        return Math.max(0, BlockTypes.CATEGORIES.size() * catStep() + 12 - h);
    }

    private static String shortName(String cat) {
        return switch (cat) {
            case "Поток" -> "Поток";
            case "Речь" -> "Речь";
            case "Персонажи" -> "Перс.";
            case "Ожидание" -> "Ждать";
            case "Игрок и мир" -> "Мир";
            case "Экран" -> "Экран";
            case "Логика" -> "Логика";
            case "Объявления" -> "Объявл.";
            case "Функции" -> "Функции";
            case "Интерфейс" -> "GUI";
            case "События" -> "События";
            case "Переменные" -> "Перем.";
            case "Операторы" -> "Операт.";
            case "Значения" -> "Знач.";
            case "Анимации" -> "Аним.";
            default -> cat;
        };
    }

    // ================================================================ hit test

    private Hit blockAt(double wx, double wy) {
        for (int i = script.stacks.size() - 1; i >= 0; i--) {
            Stack s = script.stacks.get(i);
            layoutStack(s);
            if (wx < s.x - 2 || wy < s.y - 2 || wx > s.x + s.bw + 2 || wy > s.y + s.bh + 2) continue;
            Hit h = hitList(s.blocks, wx, wy, s);
            if (h != null) return h;
        }
        return null;
    }

    private Hit hitList(List<Block> list, double wx, double wy, Stack stack) {
        for (int i = 0; i < list.size(); i++) {
            Block b = list.get(i);
            if (wy < b.ly || wy >= b.ly + b.lh) continue;
            if (!b.collapsed) {
                for (List<Block> slot : b.slots) {
                    Hit inner = hitList(slot, wx, wy, stack);
                    if (inner != null) return inner;
                }
            }
            if (wx >= b.lx && wx < b.lx + b.lw) {
                // inside a C-block: only the arm, header and bars count
                if (b.slotTop != null) {
                    for (int s = 0; s < b.slots.size(); s++) {
                        if (wy >= b.slotTop[s] && wy < b.slotTop[s] + b.slotH[s] && wx >= b.lx + ARM) return null;
                    }
                }
                return new Hit(b, list, i, stack);
            }
        }
        return null;
    }

    /** Deepest value block under the point inside b's header (b itself excluded). */
    private ValueHit valueAt(Block b, double wx, double wy) {
        for (BlockTypes.Part p : b.typeInfo().parts()) {
            if (p.field() == null) continue;
            Block in = b.inputs.get(p.field().name());
            if (in == null) continue;
            if (wx >= in.lx && wx < in.lx + in.lw && wy >= in.ly && wy < in.ly + in.lh) {
                ValueHit deeper = valueAt(in, wx, wy);
                return deeper != null ? deeper : new ValueHit(b, p.field().name(), in);
            }
        }
        return null;
    }

    /** The ‹› toggle under the point. */
    private String arrowAt(Block b, double wx, double wy) {
        BlockTypes.Type t = b.typeInfo();
        List<BlockTypes.Part> parts = t.parts();
        for (int i = 0; i < parts.size(); i++) {
            BlockTypes.Field f = parts.get(i).field();
            if (f == null || arrowW(f) == 0 || b.partW[i] == 0) continue;
            if (wy < b.ly + b.partY[i] || wy >= b.ly + b.partY[i] + b.partRH[i]) continue;
            int px = b.lx + b.partX[i];
            if (wx >= px - 1 && wx < px + ARROW_W) return f.name();
        }
        return null;
    }

    /** Field under the point: a header input or a choice answer ("opt:N"). */
    private String fieldAt(Block b, double wx, double wy) {
        BlockTypes.Type t = b.typeInfo();
        {
            List<BlockTypes.Part> parts = t.parts();
            for (int i = 0; i < parts.size(); i++) {
                BlockTypes.Field f = parts.get(i).field();
                if (f == null || b.partW[i] == 0 || b.inputs.containsKey(f.name()) || b.sockets.contains(f.name())) continue;
                if (wy < b.ly + b.partY[i] || wy >= b.ly + b.partY[i] + b.partRH[i]) continue;
                int px = b.lx + b.partX[i] + arrowW(f);
                if (wx >= px && wx < b.lx + b.partX[i] + b.partW[i]) return f.name();
            }
        }
        if (optionRows(b) && b.slotTop != null) {
            for (int i = 0; i < b.options.size() && i < b.slotTop.length; i++) {
                int rowY = b.slotTop[i] - MID;
                int px = b.lx + PAD + textW(optionLabel(b)) + GAP;
                int pw = Math.max(22, textW(b.options.get(i)) + 12);
                if (wy >= rowY && wy < rowY + MID && wx >= px && wx < px + pw) return "opt:" + i;
            }
        }
        return null;
    }

    private PaletteEntry paletteAt(double mx, double my) {
        double py = my - paletteTop() + paletteScroll;
        double px = mx - (x + CAT_W + 6);
        for (PaletteEntry e : palette) {
            if (e.header) continue;
            Block b = e.block;
            if (py >= e.y && py < e.y + b.lh && px >= 0 && px < b.lw) return e;
        }
        return null;
    }

    // ================================================================ tooltip

    @Override
    public String tooltipText(int mx, int my) {
        if (mode != Mode.NONE) return null;
        if (mx < x + PALETTE_W) {
            PaletteEntry e = paletteAt(mx, my);
            return e != null ? e.block.typeInfo().doc() : null;
        }
        Hit h = blockAt(toWorldX(mx), toWorldY(my));
        if (h == null) return null;
        Block b = h.block;
        ValueHit vh = valueAt(b, toWorldX(mx), toWorldY(my));
        if (vh != null) b = vh.value();
        if (arrowAt(b, toWorldX(mx), toWorldY(my)) != null) return "‹› — превратить поле в гнездо для блока-значения (или обратно)";
        if (b.error && b.errorText != null) return "Ошибка: " + b.errorText;
        String f = fieldAt(b, toWorldX(mx), toWorldY(my));
        if (f != null && !f.startsWith("opt:")) {
            BlockTypes.Field fl = b.typeInfo().field(f);
            if (fl != null && fl.kind() != null) return fl.label() + " — " + fl.kind().doc;
        }
        return null;
    }

    // ================================================================ mouse

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        pressX = mx;
        pressY = my;
        if (mx < x + CAT_W) {
            int idx = (int) ((my - y - 6 + catScroll) / catStep());
            if (idx >= 0 && idx < BlockTypes.CATEGORIES.size()) {
                activeCategory = BlockTypes.CATEGORIES.get(idx);
                scrollPaletteTo(activeCategory);
            }
            return true;
        }
        if (mx < x + PALETTE_W) {
            if (my < paletteTop()) return false;
            PaletteEntry e = paletteAt(mx, my);
            if (e != null && button == 0) {
                mode = Mode.PRESS_PALETTE;
                pressPaletteBlock = e.block;
                dragOffX = (mx - (x + CAT_W + 6)) - e.block.lx;
                dragOffY = (my - paletteTop() + paletteScroll) - e.block.ly;
            }
            return true;
        }
        double wx = toWorldX(mx), wy = toWorldY(my);
        Hit hit = blockAt(wx, wy);
        if (button == 1) {
            if (hit != null) {
                ValueHit vh = valueAt(hit.block, wx, wy);
                if (vh != null) {
                    selected = vh.value();
                    blockMenu(new Hit(vh.value(), null, -1, hit.stack), (int) mx, (int) my);
                    return true;
                }
                selected = hit.block;
                blockMenu(hit, (int) mx, (int) my);
            } else {
                canvasMenu((int) mx, (int) my, wx, wy);
            }
            return true;
        }
        if (button == 2 || hit == null) {
            mode = Mode.PAN;
            selected = button == 0 ? null : selected;
            return true;
        }
        ValueHit vh = valueAt(hit.block, wx, wy);
        Block target = vh != null ? vh.value() : hit.block;
        String arrow = arrowAt(target, wx, wy);
        if (arrow != null) {
            toggleSocket(target, arrow);
            return true;
        }
        // double click on a block (not on its fields) folds or unfolds it
        long now = System.currentTimeMillis();
        if (button == 0 && vh == null && fieldAt(target, wx, wy) == null && target == lastClickBlock && now - lastClickAt < 320
                && !target.typeInfo().isValue()) {
            lastClickBlock = null;
            toggleFold(target);
            return true;
        }
        lastClickBlock = target;
        lastClickAt = now;
        selected = target;
        pressHit = hit;
        pressBlock = target;
        pressValue = vh;
        String field = fieldAt(target, wx, wy);
        mode = field != null ? Mode.PRESS_PILL : vh != null ? Mode.PRESS_VALUE : Mode.PRESS_BLOCK;
        dragOffX = wx - target.lx;
        dragOffY = wy - target.ly;
        return true;
    }

    /** Switches a field between typed text and a socket; a plugged value pops out next to the block. */
    private void toggleSocket(Block b, String field) {
        snapshot();
        Block in = b.inputs.remove(field);
        if (in != null) {
            b.sockets.remove(field);
            Stack s = new Stack();
            s.x = in.lx + 8;
            s.y = b.ly + b.lh + 12;
            s.blocks.add(in);
            script.stacks.add(s);
        } else if (b.sockets.contains(field)) {
            b.sockets.remove(field);
        } else {
            b.sockets.add(field);
        }
        changed();
    }

    /** Pulls a plugged value out into a new floating stack being dragged. */
    private void startDragValue(ValueHit vh) {
        snapshot();
        vh.parent().inputs.remove(vh.field());
        if (!vh.parent().sockets.contains(vh.field())) vh.parent().sockets.add(vh.field());
        dragStack = new Stack();
        dragStack.blocks.add(vh.value());
        mode = Mode.DRAG;
        changed();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        double moved = Math.abs(mx - pressX) + Math.abs(my - pressY);
        switch (mode) {
            case PAN -> {
                viewX -= dx / zoom;
                viewY -= dy / zoom;
            }
            case PRESS_BLOCK -> {
                if (moved > 4) startDragFromCanvas(UI.ctrl(modsNow()));
            }
            case PRESS_PILL -> {
                if (moved > 4) {
                    if (pressValue != null) startDragValue(pressValue);
                    else startDragFromCanvas(UI.ctrl(modsNow()));
                }
            }
            case PRESS_VALUE -> {
                if (moved > 4 && pressValue != null) startDragValue(pressValue);
            }
            case PRESS_PALETTE -> {
                if (moved > 4) {
                    Block copy = pressPaletteBlock.deepCopy();
                    dragStack = new Stack();
                    dragStack.blocks.add(copy);
                    mode = Mode.DRAG;
                }
            }
            default -> {
            }
        }
        if (mode == Mode.DRAG && dragStack != null) {
            dragStack.x = toWorldX(mx) - dragOffX;
            dragStack.y = toWorldY(my) - dragOffY;
            dragStack.dirty = true;
            layoutStack(dragStack);
            boolean value = dragStack.blocks.size() == 1 && dragStack.blocks.get(0).typeInfo().isValue();
            snap = mx < x + PALETTE_W || value ? null : findSnap();
            socketTarget = mx < x + PALETTE_W || !value ? null : findSocket(dragStack.blocks.get(0));
            autoScroll(mx, my);
        }
        return true;
    }

    private static int modsNow() {
        long win = Minecraft.getInstance().getWindow().getWindow();
        int mods = 0;
        if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) mods |= GLFW.GLFW_MOD_CONTROL;
        return mods;
    }

    private void autoScroll(double mx, double my) {
        double edge = 20;
        if (mx > x + w - edge) viewX += 6 / zoom;
        if (my > y + h - edge) viewY += 6 / zoom;
        if (my < y + edge) viewY -= 6 / zoom;
    }

    private void startDragFromCanvas(boolean single) {
        Hit h = pressHit;
        snapshot();
        dragStack = new Stack();
        if (single) {
            dragStack.blocks.add(h.list.remove(h.index));
        } else {
            List<Block> tail = new ArrayList<>(h.list.subList(h.index, h.list.size()));
            h.list.subList(h.index, h.list.size()).clear();
            dragStack.blocks.addAll(tail);
        }
        if (h.stack.blocks.isEmpty()) script.stacks.remove(h.stack);
        mode = Mode.DRAG;
        changed();
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        Mode m = mode;
        mode = Mode.NONE;
        if (m == Mode.DRAG && dragStack != null) {
            // dropping onto the palette deletes the blocks (canvas drags already took a snapshot)
            if (mx >= x + PALETTE_W) {
                if (pressPaletteBlock != null) snapshot();
                if (socketTarget != null) {
                    Block value = dragStack.blocks.get(0);
                    Block old = socketTarget.block().inputs.put(socketTarget.field(), value);
                    if (!socketTarget.block().sockets.contains(socketTarget.field())) socketTarget.block().sockets.add(socketTarget.field());
                    if (old != null) {
                        Stack s = new Stack();
                        s.x = socketTarget.x() + 10;
                        s.y = socketTarget.y() + 30;
                        s.blocks.add(old);
                        script.stacks.add(s);
                    }
                } else if (snap != null) {
                    snap.list.addAll(snap.index, dragStack.blocks);
                } else {
                    dragStack.dirty = true;
                    script.stacks.add(dragStack);
                }
                selected = dragStack.blocks.get(0);
            }
            dragStack = null;
            snap = null;
            socketTarget = null;
            pressPaletteBlock = null;
            pressValue = null;
            changed();
            return true;
        }
        if (m == Mode.PRESS_PILL && pressBlock != null) {
            String field = fieldAt(pressBlock, toWorldX(mx), toWorldY(my));
            if (field != null) editField(pressBlock, field);
        }
        pressValue = null;
        if (m == Mode.PRESS_PALETTE && pressPaletteBlock != null) {
            // click on a palette block: append it to the end of the story
            Stack main = script.mainStack();
            snapshot();
            Block copy = pressPaletteBlock.deepCopy();
            if (main != null && copy.typeInfo().shape() != BlockTypes.Shape.HAT && !copy.typeInfo().isValue()) {
                main.blocks.add(copy);
                selected = copy;
                changed();
                revealBlock(copy);
            } else {
                Stack s = new Stack();
                s.x = viewX + 40 / zoom;
                s.y = viewY + 40 / zoom;
                s.blocks.add(copy);
                script.stacks.add(s);
                changed();
            }
        }
        pressPaletteBlock = null;
        pressHit = null;
        return true;
    }

    private void revealBlock(Block b) {
        Stack main = script.mainStack();
        if (main != null) layoutStack(main);
        double bx = b.lx, by = b.ly;
        double vw = canvasW() / zoom, vh = h / zoom;
        if (by + b.lh > viewY + vh - 20) viewY = by + b.lh - vh + 40;
        if (by < viewY) viewY = by - 20;
        if (bx < viewX || bx > viewX + vw - 40) viewX = bx - 40;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < x + CAT_W) {
            catScroll = Math.max(0, Math.min(maxCatScroll(), catScroll - delta * 30));
            return true;
        }
        if (mx < x + PALETTE_W) {
            paletteTarget = Math.max(0, Math.min(maxPaletteScroll(), paletteTarget - delta * 40));
            return true;
        }
        if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
            double wx = toWorldX(mx), wy = toWorldY(my);
            zoom = Math.max(0.4, Math.min(2.0, zoom * (delta > 0 ? 1.1 : 1 / 1.1)));
            viewX = wx - (mx - canvasX()) / zoom;
            viewY = wy - (my - y) / zoom;
        } else if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            viewX -= delta * 40 / zoom;
        } else {
            viewY -= delta * 40 / zoom;
        }
        return true;
    }

    // ================================================================ snapping

    /** The value-accepting field nearest to the dragged value's left edge. */
    private SocketTarget findSocket(Block value) {
        double px = dragStack.x, py = dragStack.y + value.lh / 2.0;
        SocketTarget[] best = {null};
        double[] bestD = {22 / Math.max(0.5, zoom)};
        for (Stack s : script.stacks) {
            layoutStack(s);
            if (px > s.x + s.bw + 40 || px < s.x - 60 || py < s.y - 40 || py > s.y + s.bh + 40) continue;
            for (Block b : s.blocks) socketsIn(b, px, py, best, bestD);
        }
        return best[0];
    }

    private void socketsIn(Block b, double px, double py, SocketTarget[] best, double[] bestD) {
        BlockTypes.Type t = b.typeInfo();
        int hy = b.ly + (t.shape() == BlockTypes.Shape.HAT ? HAT : 0);
        int hh = b.headerH - (hy - b.ly);
        List<BlockTypes.Part> parts = t.parts();
        for (int i = 0; i < parts.size() && b.partX != null && i < b.partX.length; i++) {
            BlockTypes.Field f = parts.get(i).field();
            if (f == null || !f.acceptsValue() || b.partW[i] == 0) continue;
            Block in = b.inputs.get(f.name());
            if (in != null) {
                socketsIn(in, px, py, best, bestD);
            }
            int fx = b.lx + b.partX[i] + arrowW(f), fw = b.partW[i] - arrowW(f);
            int fh = in != null ? in.lh : PILL_H, fy = b.ly + b.partY[i] + (b.partRH[i] - fh) / 2;
            double d = Math.hypot(px - fx, py - (fy + fh / 2.0));
            if (px > fx - 6 && px < fx + fw) d = Math.min(d, Math.abs(py - (fy + fh / 2.0)));
            if (d < bestD[0]) {
                bestD[0] = d;
                best[0] = new SocketTarget(b, f.name(), fx, fy, fw, fh);
            }
        }
        if (!b.collapsed) for (List<Block> slot : b.slots) for (Block c : slot) socketsIn(c, px, py, best, bestD);
    }

    private Snap findSnap() {
        Block first = dragStack.blocks.get(0);
        if (first.typeInfo().isValue()) return null;
        Block last = dragStack.blocks.get(dragStack.blocks.size() - 1);
        boolean hat = first.typeInfo().shape() == BlockTypes.Shape.HAT;
        boolean cap = last.typeInfo().shape() == BlockTypes.Shape.CAP;
        double px = dragStack.x, py = dragStack.y;
        Snap best = null;
        double bestD = SNAP_DIST / Math.max(0.5, zoom) * 1.0;
        for (Stack s : script.stacks) {
            layoutStack(s);
            if (px > s.x + s.bw + 60 || px + 80 < s.x - 40 || py > s.y + s.bh + 60 || py + dragStack.bh < s.y - 60) continue;
            // above the stack top (not above a hat)
            if (!s.blocks.isEmpty() && s.blocks.get(0).typeInfo().shape() != BlockTypes.Shape.HAT && !cap) {
                int ty = s.blocks.get(0).ly - dragStack.bh;
                double d = Math.hypot(px - s.x, py - ty);
                if (d < bestD) {
                    bestD = d;
                    best = new Snap(s.blocks, 0, (int) s.x, s.blocks.get(0).ly, s);
                }
            }
            if (hat) continue;
            best = snapList(s.blocks, s, px, py, cap, best, bestD);
            if (best != null) bestD = Math.hypot(px - best.x, py - best.y);
        }
        return best;
    }

    private Snap snapList(List<Block> list, Stack s, double px, double py, boolean cap, Snap best, double bestD) {
        for (int i = 0; i < list.size(); i++) {
            Block b = list.get(i);
            BlockTypes.Shape shape = b.typeInfo().shape();
            // after this block
            boolean canAfter = shape != BlockTypes.Shape.CAP && (!cap || i == list.size() - 1);
            if (canAfter) {
                int ay = b.ly + b.lh;
                double d = Math.hypot(px - b.lx, py - ay);
                if (d < bestD) {
                    bestD = d;
                    best = new Snap(list, i + 1, b.lx, ay, s);
                }
            }
            // inside C slots
            for (int k = 0; k < b.slots.size() && b.slotTop != null; k++) {
                List<Block> slot = b.slots.get(k);
                if (!cap || slot.isEmpty()) {
                    int sy = b.slotTop[k];
                    double d = Math.hypot(px - (b.lx + ARM), py - sy);
                    if (d < bestD) {
                        bestD = d;
                        best = new Snap(slot, 0, b.lx + ARM, sy, s);
                    }
                }
                Snap inner = snapList(slot, s, px, py, cap, best, bestD);
                if (inner != best && inner != null) {
                    best = inner;
                    bestD = Math.hypot(px - inner.x, py - inner.y);
                }
            }
        }
        return best;
    }

    // ================================================================ editing

    private void editField(Block b, String field) {
        if (field.startsWith("opt:")) {
            int idx = Integer.parseInt(field.substring(4));
            openTextEditor(b, field, b.options.get(idx), v -> b.options.set(idx, v));
            return;
        }
        BlockTypes.Field f = b.typeInfo().field(field);
        if (f == null) return;
        if (b.type.equals("character") && field.startsWith("head")) {
            org.sprautestudio.st_addon_spraute.editor.assets.HeadEditor.open(b.field("type"), headParams(b), p -> {
                setField(b, "headscale", p.scale() == 1 ? "" : String.valueOf(p.scale()));
                setField(b, "headx", p.dx() == 0 ? "" : String.valueOf(p.dx()));
                setField(b, "heady", p.dy() == 0 ? "" : String.valueOf(p.dy()));
                setField(b, "headyaw", p.yaw() == 0 ? "" : String.valueOf(p.yaw()));
                setField(b, "headbone", p.bone().equals("auto") ? "" : p.bone());
            });
            return;
        }
        if (field.equals("color") && (b.type.equals("character") || b.type.equals("namecolor"))) {
            PopupMenu pm = new PopupMenu();
            pm.item("Выбрать цвет…", Icon.EDIT, null, () -> {
                int argb = 0xFFFFAA00;
                try {
                    Integer c = org.sprautestudio.st_addon_spraute.runtime.Colors.parse(b.field(field).replace("\"", ""));
                    if (c != null) argb = 0xFF000000 | c;
                } catch (RuntimeException ignored) {
                }
                int[] r = pillScreenRect(b, field);
                snapshot();
                org.sprautestudio.st_addon_spraute.ui.widgets.ColorPicker.open(argb, r[0], r[1] + r[3] + 2, c -> {
                    b.fields.put(field, String.format("#%06X", c & 0xFFFFFF));
                    changed();
                });
            });
            pm.separator();
            for (String n : List.of("gold", "orange", "yellow", "red", "pink", "purple", "aqua", "blue", "green", "white", "gray"))
                pm.item(n, () -> setField(b, field, n)).checked(() -> b.field(field).replace("\"", "").equals(n));
            pm.separator();
            pm.item(b.type.equals("character") ? "Обычный (из настроек движка)" : "Сбросить (reset)", Icon.CLOSE, null,
                    () -> setField(b, field, b.type.equals("character") ? "" : "reset"));
            int[] r = pillScreenRect(b, field);
            pm.show(r[0], r[1] + r[3] + 2);
            return;
        }
        if (f.kind() == ParamType.CHOICE) {
            BlockTypes.Variant v = BlockTypes.variant(b.type);
            if (v == null) return;
            PopupMenu pm = new PopupMenu();
            for (String mode : v.modes()) pm.item(mode, () -> setField(b, field, mode)).checked(() -> b.field(field).equals(mode));
            int[] r = pillScreenRect(b, field);
            pm.show(r[0], r[1] + r[3] + 2);
            return;
        }
        if (f.kind() == ParamType.TARGET) {
            PopupMenu pm = new PopupMenu();
            pm.item("игрок", Icon.USER, null, () -> setField(b, field, "player")).checked(() -> b.field(field).equals("player"));
            List<String> chars = names("character");
            if (!chars.isEmpty()) pm.separator();
            for (String ch : chars) pm.item("нпс: " + ch, Icon.USER, null, () -> setField(b, field, ch)).color(Theme.CODE_CHARACTER).checked(() -> b.field(field).equals(ch));
            pm.separator();
            pm.item("моб… (ближайший выбранного типа)", Icon.CUBE, null, () -> {
                if (assetPicker != null) assetPicker.pick(ParamType.ENTITY, b.field(field).replace("\"", ""), v -> setField(b, field, "\"" + v + "\""));
            });
            pm.item("любой ближайший моб", Icon.CUBE, null, () -> setField(b, field, "\"mob\""));
            List<String> locs = names("location");
            if (!locs.isEmpty()) pm.separator();
            for (String l : locs) pm.item("точка: " + l, Icon.PIN, null, () -> setField(b, field, l)).color(Theme.CODE_LOCATION).checked(() -> b.field(field).equals(l));
            if (positionProvider != null && positionProvider.get() != null) {
                String pos = positionProvider.get();
                pm.item("моя позиция " + pos, Icon.PIN, null, () -> setField(b, field, pos));
            }
            pm.separator();
            pm.item("Ввести вручную…", Icon.EDIT, null, () -> openTextEditor(b, field, b.field(field), v -> b.fields.put(field, v)));
            int[] r = pillScreenRect(b, field);
            pm.show(r[0], r[1] + r[3] + 2);
            return;
        }
        if (b.type.equals("screen:notify") && field.equals("icon")) {
            String mode = b.field("mode");
            if (mode.startsWith("картинка")) {
                PopupMenu pm = new PopupMenu();
                for (String ch : org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectImages.get())
                    pm.item(ch, Icon.IMAGE, null, () -> setField(b, field, ch)).checked(() -> b.field(field).equals(ch));
                if (org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectImages.get().isEmpty())
                    pm.item("В проекте нет картинок — добавьте в «Картинки»", Icon.INFO, null, () -> {
                    });
                pm.separator();
                pm.item("Текстура игры (minecraft:textures/…)…", Icon.EDIT, null, () -> openTextEditor(b, field, b.field(field), v -> b.fields.put(field, v)));
                int[] r = pillScreenRect(b, field);
                pm.show(r[0], r[1] + r[3] + 2);
                return;
            }
            if (mode.startsWith("без иконки")) return;
            if (assetPicker != null) {
                assetPicker.pick(ParamType.ITEM, b.field(field), v -> setField(b, field, v));
                return;
            }
        }
        if (assetPicker != null && (f.kind() == ParamType.ANIM || f.kind() == ParamType.ENTITY || f.kind() == ParamType.SOUND || f.kind() == ParamType.ITEM)) {
            // an animation of a character: the picker shows that character
            if (f.kind() == ParamType.ANIM) org.sprautestudio.st_addon_spraute.editor.assets.AssetPicker.previewEntity = characterType(b.field("who"));
            assetPicker.pick(f.kind(), b.field(field), v -> setField(b, field, v));
            return;
        }
        List<String> choices = choicesFor(f.kind());
        if (choices != null) {
            PopupMenu pm = new PopupMenu();
            for (String ch : choices) {
                pm.item(ch, () -> setField(b, field, ch.startsWith("моя позиция ") ? ch.substring(12) : ch))
                        .checked(() -> b.field(field).equals(ch));
            }
            if (!choices.isEmpty()) pm.separator();
            pm.item("Ввести вручную…", Icon.EDIT, null, () -> openTextEditor(b, field, b.field(field), v -> b.fields.put(field, v)));
            if (f.optional() && !b.field(field).isEmpty()) pm.item("Очистить", Icon.CLOSE, null, () -> setField(b, field, ""));
            int[] r = pillScreenRect(b, field);
            pm.show(r[0], r[1] + r[3] + 2);
            return;
        }
        openTextEditor(b, field, b.field(field), v -> b.fields.put(field, v));
    }

    private void setField(Block b, String field, String value) {
        snapshot();
        b.fields.put(field, value);
        changed();
    }

    private List<String> choicesFor(ParamType kind) {
        if (kind == null) return null;
        return switch (kind) {
            case CHAR -> names("character");
            case LABEL -> names("label");
            case TARGET -> {
                List<String> l = new ArrayList<>(names("character"));
                l.addAll(names("location"));
                l.add("player");
                yield l;
            }
            case SPEAKER -> {
                List<String> l = new ArrayList<>(names("character"));
                l.add("player");
                l.add("0");
                l.add("1");
                yield l;
            }
            case LOC -> {
                List<String> l = new ArrayList<>(names("location"));
                if (positionProvider != null && positionProvider.get() != null) l.add("моя позиция " + positionProvider.get());
                yield l;
            }
            case EMOTION -> Specs.EMOTIONS;
            case KEY -> List.of("F8", "F9", "F10", "G", "H", "SPACE", "ENTER");
            case BOOL -> List.of("да", "нет");
            case LAYER -> Specs.LAYERS;
            case FUNC -> names("func");
            case VAR -> names("var");
            case IMAGE -> org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.imageChoices();
            case GUI -> new ArrayList<>(org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectGuis.get().keySet());
            case GUIEL -> {
                Set<String> set = new LinkedHashSet<>();
                for (List<String> l : org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectGuis.get().values()) set.addAll(l);
                yield new ArrayList<>(set);
            }
            case BRANCH -> {
                Set<String> set = new LinkedHashSet<>();
                for (Stack s : script.stacks) collectBranches(s.blocks, set);
                yield new ArrayList<>(set);
            }
            case MOVETYPE -> Specs.MOVE_TYPES;
            case SYSTEM -> Specs.ENGINE_SYSTEMS;
            case EASE -> org.sprautestudio.st_addon_spraute.cutscene.Ease.names();
            case CUTSCENE -> org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectCutscenes.get();
            case VFX -> org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.vfxChoices();
            case SHOP -> org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectShops.get();
            case QUEST -> org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectQuests.get();
            case ZONE -> org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectZones.get();
            case PATH -> org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.projectPaths.get();
            case FIGHT -> org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex.fightChoices();
            case ENTITY -> Specs.ST_ENTITIES;
            default -> null;
        };
    }

    /** Names declared by blocks of a type anywhere in the script. */
    private List<String> names(String type) {
        Set<String> set = new LinkedHashSet<>();
        for (Stack s : script.stacks) collectNames(s.blocks, type, set);
        return new ArrayList<>(set);
    }

    private void collectBranches(List<Block> list, Set<String> out) {
        for (Block b : list) {
            if ((b.type.equals("async") || b.type.equals("parallel")) && !b.field("name").isBlank()) out.add(b.field("name").trim());
            if (b.type.equals("asynccall") && !b.field("as").isBlank()) out.add(b.field("as").trim());
            for (List<Block> slot : b.slots) collectBranches(slot, out);
        }
    }

    /** The entity type of a declared character ("st_addon_spraute:…", "spraute:…"); null — unknown. */
    private String characterType(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        String[] found = {null};
        for (Stack s : script.stacks) findCharacter(s.blocks, n, found);
        return found[0];
    }

    private void findCharacter(List<Block> list, String name, String[] out) {
        for (Block b : list) {
            if (out[0] != null) return;
            if (b.type.equals("character") && b.field("name").trim().equals(name)) {
                out[0] = b.field("type").replace("\"", "").trim();
                return;
            }
            for (List<Block> slot : b.slots) findCharacter(slot, name, out);
        }
    }

    private void collectNames(List<Block> list, String type, Set<String> out) {
        for (Block b : list) {
            if (b.type.equals(type) && !b.field("name").isBlank()) out.add(b.field("name").trim());
            for (List<Block> slot : b.slots) collectNames(slot, type, out);
        }
    }

    private int[] pillScreenRect(Block b, String field) {
        BlockTypes.Type t = b.typeInfo();
        int hy = b.ly + (t.shape() == BlockTypes.Shape.HAT ? HAT : 0);
        int hh = b.headerH - (hy - b.ly);
        int wx = b.lx, ww = 60, wy = hy + (hh - PILL_H) / 2;
        if (field.startsWith("opt:")) {
            int idx = Integer.parseInt(field.substring(4));
            wx = b.lx + PAD + textW(optionLabel(b)) + GAP;
            ww = Math.max(22, textW(b.options.get(idx)) + 12);
            wy = b.slotTop[idx] - MID + (MID - PILL_H) / 2;
        } else {
            List<BlockTypes.Part> parts = t.parts();
            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i).field() != null && parts.get(i).field().name().equals(field)) {
                    int aw = arrowW(parts.get(i).field());
                    wx = b.lx + b.partX[i] + aw;
                    ww = b.partW[i] - aw;
                    wy = b.ly + b.partY[i] + (b.partRH[i] - PILL_H) / 2;
                }
            }
        }
        return new int[]{toScreenX(wx), toScreenY(wy), (int) Math.round(ww * zoom), (int) Math.round(PILL_H * zoom)};
    }

    private void openTextEditor(Block b, String field, String value, Consumer<String> apply) {
        UI ui = UI.get();
        if (ui == null) return;
        int[] r = pillScreenRect(b, field);
        InlineEditor ed = new InlineEditor(r[0] - 2, r[1] - 2, Math.max(120, r[2] + 60), Math.max(16, r[3] + 4));
        ed.setText(value);
        ed.selectAll();
        ed.commit = v -> {
            if (v.equals(value)) return;
            snapshot();
            apply.accept(v);
            changed();
        };
        ui.openOverlay(ed, false, true, ed::commitNow);
        ed.requestFocus();
    }

    /** Text field floating over a block input; commits on Enter, click outside or focus loss. */
    private static final class InlineEditor extends TextField implements UI.OverlayLayout {
        private final int ex, ey, ew, eh;
        Consumer<String> commit;
        private boolean done;

        InlineEditor(int x, int y, int w, int h) {
            this.ex = x;
            this.ey = y;
            this.ew = w;
            this.eh = h;
            onSubmit = s -> close();
            onEscape = () -> {
                done = true;
                UI ui = UI.get();
                if (ui != null) ui.closeOverlay(this);
            };
        }

        void commitNow() {
            if (done) return;
            done = true;
            if (commit != null) commit.accept(text().trim());
        }

        private void close() {
            commitNow();
            UI ui = UI.get();
            if (ui != null) ui.closeOverlay(this);
        }

        @Override
        public void focusLost() {
            close();
        }

        @Override
        public void layoutOverlay(int vw, int vh) {
            bounds(Math.min(ex, vw - ew - 2), ey, ew, eh);
        }
    }

    // ================================================================ menus

    private void blockMenu(Hit hit, int mx, int my) {
        Block b = hit.block;
        PopupMenu pm = new PopupMenu();
        pm.item("Дублировать", Icon.COPY, "Ctrl+D", () -> duplicate(b));
        pm.item("Копировать", Icon.COPY, "Ctrl+C", () -> clipboard = b.deepCopy());
        if (!b.typeInfo().isValue()) {
            pm.separator();
            pm.item(b.collapsed ? "Развернуть" : "Свернуть", b.collapsed ? Icon.CHEVRON_DOWN : Icon.CHEVRON_RIGHT, "двойной клик", () -> toggleFold(b));
            boolean hasFields = b.typeInfo().parts().stream().anyMatch(p -> p.field() != null);
            if (hasFields && !b.collapsed) pm.item(b.multiline ? "В одну строку" : "В несколько строк", Icon.LIST, null, () -> {
                snapshot();
                b.multiline = !b.multiline;
                changed();
            });
        }
        jumpItems(pm);
        if (optionRows(b)) {
            boolean sw = b.type.equals("switch");
            pm.separator();
            pm.item(sw ? "Добавить вариант" : "Добавить вариант ответа", Icon.PLUS, null, () -> {
                snapshot();
                b.options.add(sw ? "\"значение" + (b.options.size() + 1) + "\"" : "Вариант " + (b.options.size() + 1));
                b.slots.add(b.options.size() - 1, new ArrayList<>());
                changed();
            });
            pm.item("Убрать последний вариант", Icon.CLOSE, null, () -> {
                if (b.options.size() <= 1) return;
                snapshot();
                int i = b.options.size() - 1;
                b.options.remove(i);
                b.slots.remove(i);
                changed();
            }).enabled(() -> b.options.size() > 1);
        }
        if (hit.list == null) {
            pm.separator();
            pm.item("Удалить", Icon.TRASH, "Del", () -> deleteValue(b)).color(Theme.ERROR);
            pm.show(mx, my);
            return;
        }
        pm.separator();
        pm.item("Справка", Icon.BOOK, null, () -> Dialog.info(b.typeInfo().key(), b.typeInfo().doc()));
        pm.separator();
        pm.item("Удалить блок", Icon.TRASH, "Del", () -> deleteBlock(hit, false)).color(Theme.ERROR);
        if (hit.index < hit.list.size() - 1) {
            pm.item("Удалить вместе с нижними", Icon.TRASH, null, () -> deleteBlock(hit, true)).color(Theme.ERROR);
        }
        pm.show(mx, my);
    }

    private Block clipboard;

    private void canvasMenu(int mx, int my, double wx, double wy) {
        PopupMenu pm = new PopupMenu();
        pm.item("Вставить", Icon.FILE, "Ctrl+V", () -> paste(wx, wy)).enabled(() -> clipboard != null);
        pm.item("Упорядочить блоки", Icon.LIST, null, this::cleanUp);
        pm.item("К началу сюжета", Icon.HOME, "Home", this::goHome);
        pm.item("Масштаб 100%", Icon.SEARCH, "Ctrl+0", () -> zoom = 1);
        pm.item("Свернуть всё", Icon.CHEVRON_RIGHT, null, () -> foldAll(true));
        pm.item("Развернуть всё", Icon.CHEVRON_DOWN, null, () -> foldAll(false));
        jumpItems(pm);
        pm.show(mx, my);
    }

    private Block lastClickBlock;
    private long lastClickAt;

    private void toggleFold(Block b) {
        snapshot();
        b.collapsed = !b.collapsed;
        changed();
    }

    /** Folds (or unfolds) every C-block: event handlers, functions, ifs, loops. */
    private void foldAll(boolean fold) {
        snapshot();
        for (Stack s : script.stacks) foldIn(s.blocks, fold);
        changed();
    }

    private static void foldIn(List<Block> list, boolean fold) {
        for (Block b : list) {
            if (!b.slots.isEmpty()) b.collapsed = fold;
            else if (!fold) b.collapsed = false;
            for (List<Block> slot : b.slots) foldIn(slot, fold);
        }
    }

    /** "Go to" items: every label and function of the script. */
    private void jumpItems(PopupMenu pm) {
        List<Block> marks = new ArrayList<>();
        for (Stack s : script.stacks) collectMarks(s.blocks, marks);
        pm.separator();
        if (marks.isEmpty()) {
            pm.item("Перейти: меток нет", Icon.FLAG, null, () -> {
            }).enabled(() -> false);
            return;
        }
        for (Block m : marks) {
            boolean label = m.type.equals("label");
            pm.item((label ? "→ метка " : "→ функция ") + m.field("name").trim(), label ? Icon.FLAG : Icon.CODE, null, () -> jumpTo(m))
                    .color(label ? Theme.category(BlockTypes.CAT_FLOW) : Theme.category(BlockTypes.CAT_FUNC));
        }
    }

    private static void collectMarks(List<Block> list, List<Block> out) {
        for (Block b : list) {
            if ((b.type.equals("label") || b.type.equals("func")) && !b.field("name").isBlank()) out.add(b);
            for (List<Block> slot : b.slots) collectMarks(slot, out);
        }
    }

    /** Moves the view to a block (unfolding what hides it) and selects it. */
    private void jumpTo(Block target) {
        boolean unfolded = false;
        for (Stack s : script.stacks) unfolded |= unfoldPath(s.blocks, target);
        if (unfolded) changed();
        for (Stack s : script.stacks) {
            s.dirty = true;
            layoutStack(s);
        }
        viewX = target.lx + target.lw / 2.0 - canvasW() / 2.0 / zoom;
        viewY = target.ly - h / 3.0 / zoom;
        selected = target;
        flash = target;
        flashUntil = System.currentTimeMillis() + 1200;
    }

    /** Unfolds the blocks containing {@code target}; true when it is inside this list. */
    private static boolean unfoldPath(List<Block> list, Block target) {
        for (Block b : list) {
            if (b == target) return true;
            for (List<Block> slot : b.slots) {
                if (unfoldPath(slot, target)) {
                    b.collapsed = false;
                    return true;
                }
            }
        }
        return false;
    }

    /** Block found with "go to", blinking for a moment. */
    private Block flash;
    private long flashUntil;

    private void paintFlash(Canvas c) {
        if (flash == null || System.currentTimeMillis() > flashUntil) return;
        float k = (flashUntil - System.currentTimeMillis()) / 1200f;
        int a = (int) (Math.abs(Math.sin(k * Math.PI * 3)) * 200);
        c.outline(flash.lx - 3, flash.ly - 3, flash.lw + 6, flash.lh + 6, (a << 24) | 0xFFD54F);
        c.outline(flash.lx - 2, flash.ly - 2, flash.lw + 4, flash.lh + 4, (a << 24) | 0xFFD54F);
    }

    private void duplicate(Block b) {
        snapshot();
        Stack s = new Stack();
        s.x = b.lx + 30;
        s.y = b.ly + 30;
        s.blocks.add(b.deepCopy());
        script.stacks.add(s);
        changed();
    }

    private void paste(double wx, double wy) {
        if (clipboard == null) return;
        snapshot();
        Stack s = new Stack();
        s.x = wx;
        s.y = wy;
        s.blocks.add(clipboard.deepCopy());
        script.stacks.add(s);
        changed();
    }

    /** Removes a value block wherever it is plugged. */
    private void deleteValue(Block value) {
        snapshot();
        for (Stack s : script.stacks) removeValueIn(s.blocks, value);
        script.stacks.removeIf(s -> s.blocks.isEmpty() || (s.blocks.size() == 1 && s.blocks.get(0) == value));
        if (selected == value) selected = null;
        changed();
    }

    private static boolean removeValueIn(List<Block> list, Block value) {
        for (Block b : list) {
            for (java.util.Iterator<Map.Entry<String, Block>> it = b.inputs.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Block> e = it.next();
                if (e.getValue() == value) {
                    it.remove();
                    return true;
                }
                if (removeValueIn(List.of(e.getValue()), value)) return true;
            }
            for (List<Block> slot : b.slots) if (removeValueIn(slot, value)) return true;
        }
        return false;
    }

    private void deleteBlock(Hit hit, boolean withBelow) {
        snapshot();
        if (withBelow) hit.list.subList(hit.index, hit.list.size()).clear();
        else hit.list.remove(hit.index);
        script.stacks.removeIf(s -> s.blocks.isEmpty());
        if (selected == hit.block) selected = null;
        changed();
    }

    private void cleanUp() {
        snapshot();
        double yy = 40;
        Stack main = script.mainStack();
        List<Stack> ordered = new ArrayList<>(script.stacks);
        if (main != null) {
            ordered.remove(main);
            ordered.add(0, main);
        }
        for (Stack s : ordered) {
            s.x = 40;
            s.y = yy;
            s.dirty = true;
            layoutStack(s);
            yy += s.bh + 40;
        }
        changed();
    }

    public void goHome() {
        Stack main = script.mainStack();
        if (main == null) {
            viewX = viewY = 0;
            return;
        }
        viewX = main.x - 40;
        viewY = main.y - 40;
    }

    private Hit findHit(Block target) {
        for (Stack s : script.stacks) {
            Hit h = findIn(s.blocks, target, s);
            if (h != null) return h;
        }
        return null;
    }

    private Hit findIn(List<Block> list, Block target, Stack s) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == target) return new Hit(target, list, i, s);
            for (List<Block> slot : list.get(i).slots) {
                Hit h = findIn(slot, target, s);
                if (h != null) return h;
            }
        }
        return null;
    }

    // ================================================================ keys

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean ctrl = UI.ctrl(mods);
        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_Z -> {
                    if (UI.shift(mods)) redo();
                    else undo();
                    return true;
                }
                case GLFW.GLFW_KEY_Y -> {
                    redo();
                    return true;
                }
                case GLFW.GLFW_KEY_D -> {
                    if (selected != null) duplicate(selected);
                    return true;
                }
                case GLFW.GLFW_KEY_C -> {
                    if (selected != null) clipboard = selected.deepCopy();
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    UI ui = UI.get();
                    if (ui != null) paste(toWorldX(ui.mouseX()), toWorldY(ui.mouseY()));
                    return true;
                }
                case GLFW.GLFW_KEY_0 -> {
                    zoom = 1;
                    return true;
                }
                case GLFW.GLFW_KEY_F -> {
                    search.requestFocus();
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }
        switch (key) {
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
                if (selected != null) {
                    Hit h = findHit(selected);
                    if (h != null) deleteBlock(h, false);
                    else deleteValue(selected);
                }
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                goHome();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (selected != null) {
                    selected = null;
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }
}
