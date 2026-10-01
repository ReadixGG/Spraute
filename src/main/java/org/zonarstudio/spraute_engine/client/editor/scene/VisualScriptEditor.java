package org.zonarstudio.spraute_engine.client.editor.scene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiInputTextCallbackData;
import imgui.ImVec2;
import imgui.callback.ImGuiInputTextCallback;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.zonarstudio.spraute_engine.client.UiTextMarkup;
import org.zonarstudio.spraute_engine.client.editor.UiTextMarkupMenu;
import org.zonarstudio.spraute_engine.client.editor.icons.EditorIcons;
import org.zonarstudio.spraute_engine.client.editor.visual.PluginI18n;
import org.zonarstudio.spraute_engine.client.editor.visual.VisualBlockCodegen;
import org.zonarstudio.spraute_engine.client.editor.visual.VisualBlockDef;
import org.zonarstudio.spraute_engine.client.editor.visual.VisualBlockRegistry;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.ScenePathResolver;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Blockly-style visual script editor for {@code .sprv} files.
 *
 * <p>Block definitions come from plugins ({@code spraute_engine/plugins/*}) via
 * {@link VisualBlockRegistry}. Supports multi-row blocks, dropdowns, checkboxes,
 * value sockets (incl. the Studio «&lt;&gt;» toggle), wrapper C-blocks with inner
 * statement chains, conditional rows and template/JS code generation.
 *
 * <p>Legacy Studio {@code .sprv} files (Blockly XML) are imported transparently;
 * unknown block types get a red «устаревший блок» placeholder instead of being lost.
 */
public final class VisualScriptEditor {

    /** Built-in mark block (mod, not ProCode) — no codegen, listed in the inspector. */
    public static final String MARK_BLOCK_TYPE = "spraute.mark";
    /** Legacy ProCode mark id — rewritten to {@link #MARK_BLOCK_TYPE} on load. */
    private static final String LEGACY_MARK_BLOCK_TYPE = "procode.bookmark";

    // ==================== Document model ====================

    static final class VBlock {
        String id;
        String type;
        float x, y;                                             // canvas coords, roots only
        final Map<String, String> fields = new LinkedHashMap<>();
        final Set<String> socketOn = new LinkedHashSet<>();     // fields switched to sockets
        final Map<String, String> socketChild = new HashMap<>(); // input name -> value block id
        final Map<String, String> stmtChild = new HashMap<>();   // statement input -> first block id
        String next;                                            // next statement in chain

        // per-frame layout
        transient float sx, sy, w, h;
        transient boolean isChild;
        transient List<Row> rows;
        transient int prevTabColor;   // colour of the block above in the chain (0 = none)
        transient int zOrder;        // paint order this frame (higher = on top)
        /** Size cache for the current {@link #layoutEpoch} (avoids re-measure of nested sockets). */
        transient float cachedW, cachedH;
        transient int measureEpoch;

        VisualBlockDef def() { return VisualBlockRegistry.findOrPlaceholder(type); }
    }

    /** Mark entry for the inspector (backed by a {@link #MARK_BLOCK_TYPE} block). */
    public static final class Bookmark {
        public String id;
        public String name;
        public float x, y;
    }

    /** One visible laid-out row: either token row or a statement input area. */
    static final class Row {
        List<VisualBlockDef.Token> tokens;
        VisualBlockDef.StatementInput stmt;   // set for statement areas
        float h;                              // row height (statement: header only, inner area separate)
        float innerH;                         // statement inner region height
    }

    private static final Map<String, VisualScriptEditor> OPEN_BY_PATH = new HashMap<>();

    private final Path sprvPath;
    private final Map<String, VBlock> blocks = new LinkedHashMap<>();
    private int idCounter = 1;
    private boolean dirty;
    private String status = "";
    private final List<String> missingTypes = new ArrayList<>();

    // view / interaction state
    private float panX = 40f, panY = 40f;
    private float lastCanvasW = 800f, lastCanvasH = 500f;
    private final Set<String> selection = new LinkedHashSet<>();
    private boolean draggingBlocks;
    private String dragPrimary;
    private float dragLastX, dragLastY;
    /** Detach nested/stack child only after the mouse actually moves (not on Ctrl/Shift multi-select). */
    private boolean dragPendingDetach;
    private boolean banding;
    private float bandX0, bandY0;
    private String paletteDragType;
    private final Map<String, ImString> fieldBufs = new HashMap<>();
    private final Map<String, VBlock> palettePreview = new HashMap<>();
    private String ctxBlockId;                       // block under the right-click context menu
    private boolean openCanvasCtx;
    private float ctxCanvasX, ctxCanvasY;            // canvas coords of RMB (for new marks)
    private static Set<String> favorites;            // favourite block ids, shared across editors
    /** Shared clipboard for visual blocks (copy between .sprv tabs). */
    private static final Map<String, VBlock> BLOCK_CLIPBOARD = new LinkedHashMap<>();
    private static final List<String> BLOCK_CLIPBOARD_ROOTS = new ArrayList<>();
    /** Previous-frame flag: hierarchy skips Ctrl+C/V/X while a visual tab is open. */
    private static boolean clipboardHotkeysClaimed;
    /** Active visual tab also owns Ctrl+Z/Y (scene undo must not steal them). */
    private static VisualScriptEditor hotkeyOwner;

    private static final int HISTORY_LIMIT = 80;
    private final Deque<DocSnap> undoStack = new ArrayDeque<>();
    private final Deque<DocSnap> redoStack = new ArrayDeque<>();
    /** Field key that already has a coalesced undo entry for this typing session. */
    private String fieldHistoryKey;
    /** One undo step for an entire drag + snap. */
    private boolean dragHistoryPushed;

    private record DocSnap(Map<String, VBlock> blocks, int idCounter, Set<String> selection) {}

    public static boolean claimsClipboardHotkeys() {
        return clipboardHotkeysClaimed;
    }

    public Path path() {
        return sprvPath;
    }

    /** Mark blocks currently on the canvas (for the scene inspector). */
    public List<Bookmark> bookmarks() {
        List<Bookmark> out = new ArrayList<>();
        for (VBlock b : blocks.values()) {
            if (!MARK_BLOCK_TYPE.equals(b.type)) continue;
            Bookmark m = new Bookmark();
            m.id = b.id;
            String name = b.fields.get("name");
            m.name = name == null ? "" : name;
            m.x = b.x;
            m.y = b.y;
            out.add(m);
        }
        out.sort((a, b) -> {
            int byY = Float.compare(a.y, b.y);
            return byY != 0 ? byY : Float.compare(a.x, b.x);
        });
        return out;
    }

    public static VisualScriptEditor findOpen(Path path) {
        if (path == null) return null;
        return OPEN_BY_PATH.get(path.toAbsolutePath().normalize().toString());
    }

    /** Persist every dirty open visual tab so Play / deploy sees a sibling {@code .spr}. */
    public static void saveAllDirty() {
        for (VisualScriptEditor editor : List.copyOf(OPEN_BY_PATH.values())) {
            if (editor.dirty) editor.save();
        }
    }

    /**
     * Runtime always loads {@code .spr}. Visual create/play used to leave only {@code .sprv},
     * so the scene reported «script not found» — especially on a dedicated server.
     */
    public static Path ensureRuntimeSpr(Path sprvPath) {
        if (sprvPath == null) return null;
        String fileName = sprvPath.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".sprv")) return null;
        Path spr = sprvPath.resolveSibling(fileName.substring(0, fileName.length() - 5) + ".spr");
        VisualScriptEditor open = findOpen(sprvPath);
        if (open != null) {
            if (open.dirty || !Files.isRegularFile(spr)) open.save();
            return spr;
        }
        if (!Files.isRegularFile(spr)) {
            try {
                String name = fileName.substring(0, fileName.length() - 5);
                Files.createDirectories(spr.getParent());
                Files.writeString(spr,
                        "# Сгенерировано из " + name + ".sprv — правки затрутся при сохранении блоков\n\n",
                        StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return spr;
            }
        }
        return spr;
    }

    public void dispose() {
        OPEN_BY_PATH.remove(sprvPath.toAbsolutePath().normalize().toString());
        if (hotkeyOwner == this) hotkeyOwner = null;
    }

    /** Pan the canvas so the mark block is centered and selected. */
    public void goToBookmark(String bookmarkId) {
        if (bookmarkId == null) return;
        VBlock b = blocks.get(bookmarkId);
        if (b == null || !MARK_BLOCK_TYPE.equals(b.type)) return;
        goToMarkBlock(b);
    }

    public void goToBookmark(Bookmark m) {
        if (m == null || m.id == null) return;
        goToBookmark(m.id);
    }

    private void goToMarkBlock(VBlock b) {
        panX = lastCanvasW / (2f * Math.max(0.15f, zoom)) - b.x;
        panY = lastCanvasH / (2f * Math.max(0.15f, zoom)) - b.y;
        selection.clear();
        selection.add(b.id);
        String name = b.fields.get("name");
        status = EditorI18n.t("Метка: ") + (name == null || name.isBlank() ? b.id : name);
    }

    public static void setClipboardHotkeysClaimed(boolean claimed) {
        clipboardHotkeysClaimed = claimed;
    }

    public static void clearHotkeyOwner() {
        hotkeyOwner = null;
        clipboardHotkeysClaimed = false;
    }

    /** True only while the visual editor itself has keyboard focus (not merely an open tab). */
    public static boolean claimsEditHotkeys() {
        return hotkeyOwner != null;
    }

    public static boolean canUndoActiveTab() {
        return hotkeyOwner != null && hotkeyOwner.canUndo();
    }

    public static boolean canRedoActiveTab() {
        return hotkeyOwner != null && hotkeyOwner.canRedo();
    }

    public static boolean undoActiveTab() {
        if (hotkeyOwner == null || !hotkeyOwner.canUndo()) return false;
        hotkeyOwner.undo();
        return true;
    }

    public static boolean redoActiveTab() {
        if (hotkeyOwner == null || !hotkeyOwner.canRedo()) return false;
        hotkeyOwner.redo();
        return true;
    }
    private final Map<String, Boolean> categoryOpen = new HashMap<>();
    private final ImString paletteFilter = new ImString(128);
    private String categoryPressName;
    private boolean categoryDragging;
    private static final String CATEGORY_PAYLOAD = "SPRAUTE_VS_CATEGORY";
    private final ImString textureSearch = new ImString(128);
    private final ImString soundSearch = new ImString(128);
    private boolean anyWidgetHovered;
    private int drawSeq; // incrementing paint order for hit-testing topmost block
    private boolean previewMode; // palette preview: widgets drawn statically, no interaction
    private float canvasOriginX, canvasOriginY;
    /** Field key ({@code blockId:fieldName}) being edited — keeps layout width stable while typing/pasting. */
    private String activeFieldKey;
    /** Dropdown/picker kept interactive while its popup is open (mouse may leave the field). */
    private String heldPopupFieldKey;
    /** Right-click coords / text-effects menu on a field (floating window — not ImGui popup). */
    private boolean coordsMenuOpen;
    private boolean coordsMenuJustOpened;
    private boolean coordsMenuNumeric;
    private String coordsMenuFieldKey;
    private String coordsMenuBlockId;
    private String coordsMenuFieldName;
    private float coordsMenuX, coordsMenuY;
    private int coordsMenuSelStart;
    private int coordsMenuSelEnd;
    private int fieldSelStart;
    private int fieldSelEnd;
    private int fieldSelStickyStart;
    private int fieldSelStickyEnd;
    private String fieldMarkupPending;
    private int fieldMarkupPendingCursor = -1;
    private final ImGuiInputTextCallback fieldSelCallback = new ImGuiInputTextCallback() {
        @Override
        public void accept(ImGuiInputTextCallbackData data) {
            String buf = data.getBuf();
            if (buf == null) buf = "";
            if (fieldMarkupPending != null && data.hasEventFlag(ImGuiInputTextFlags.CallbackAlways)) {
                String rewrite = fieldMarkupPending;
                int caret = fieldMarkupPendingCursor;
                fieldMarkupPending = null;
                fieldMarkupPendingCursor = -1;
                int oldLen = data.getBufTextLen();
                if (oldLen > 0) data.deleteChars(0, oldLen);
                data.insertChars(0, rewrite);
                buf = rewrite;
                int b = UiTextMarkupMenu.charToUtf8(buf, Math.max(0, Math.min(caret, buf.length())));
                data.setCursorPos(b);
                data.setSelectionStart(b);
                data.setSelectionEnd(b);
            }
            fieldSelStart = UiTextMarkupMenu.utf8ToChar(buf, data.getSelectionStart());
            fieldSelEnd = UiTextMarkupMenu.utf8ToChar(buf, data.getSelectionEnd());
            if (fieldSelStart != fieldSelEnd) {
                fieldSelStickyStart = fieldSelStart;
                fieldSelStickyEnd = fieldSelEnd;
            }
        }
    };
    private final Map<String, Float> fieldEditWidth = new HashMap<>();
    /** Bumped each canvas layout pass — invalidates {@link VBlock#cachedW}/{@link VBlock#cachedH}. */
    private int layoutEpoch;
    /** Structure/size layout needed (not every pan frame). */
    private boolean layoutDirty = true;
    private float laidOriginX, laidOriginY, laidPanX, laidPanY, laidZoom = Float.NaN;
    private final List<VBlock> laidRoots = new ArrayList<>();
    /** Visible canvas AABB in screen space (for draw/widget culling). */
    private float viewMinX, viewMinY, viewMaxX, viewMaxY;
    /** Palette scroll clip in screen Y (only draw previews that intersect). */
    private float paletteClipMinY, paletteClipMaxY;
    /** Cached palette preview sizes keyed by fullId; invalidated when {@link #paletteSizeCacheScale} changes. */
    private final Map<String, float[]> paletteSizeCache = new HashMap<>();
    private float paletteSizeCacheScale = Float.NaN;
    private final Map<String, Float> textWCache = new HashMap<>();
    private float textWCacheScale = Float.NaN;

    // Metrics are scaled by the canvas zoom each frame (see applyMetricsScale).
    private float zoom = 1f;
    private float ROW_H = 28f;
    private float PAD = 10f;
    private float GAP = 6f;
    private float NOTCH_X = 14f;
    private float NOTCH_W = 16f;
    private float NOTCH_H = 5f;
    private float FIELD_H = 20f;
    private float EMPTY_SOCKET_W = 46f;
    private float SNAP_DIST = 22f;
    private float STMT_INDENT = 16f;
    private float STMT_MIN_INNER = 30f;
    private float STMT_FOOT = 10f;
    private float BLOCK_ROUND = 7f;
    private float metricScale = 1f;
    private float TOGGLE_W = 20f;
    private float TOGGLE_SZ = 16f;
    private float FIELD_PAD_X = 8f;
    private float FIELD_MIN_W = 56f;
    private float REGISTRY_MIN_W = 80f;
    private float DROPDOWN_MIN_W = 70f;
    private float CHECKBOX_W = 16f;
    private float CHECKBOX_SZ = 14f;
    private float CHILD_SOCKET_PAD = 6f;
    private float MOUTH_PAD_TOP = 2f;
    private float MOUTH_PAD_BOT = 6f;
    /** Default / clamp for left palette zoom (categories + block previews). */
    private static final float PALETTE_ZOOM_DEFAULT = 0.78f;
    private static final float PALETTE_ZOOM_MIN = 0.45f;
    private static final float PALETTE_ZOOM_MAX = 1.8f;
    /** Scale for the left palette — Ctrl+wheel over the panel. */
    private float paletteZoom = PALETTE_ZOOM_DEFAULT;

    private void applyMetricsScale(float s) {
        metricScale = s;
        ROW_H = 28f * s; PAD = 10f * s; GAP = 6f * s;
        NOTCH_X = 14f * s; NOTCH_W = 16f * s; NOTCH_H = 5f * s;
        FIELD_H = 20f * s; EMPTY_SOCKET_W = 46f * s; SNAP_DIST = 22f * s;
        STMT_INDENT = 16f * s; STMT_MIN_INNER = 30f * s; STMT_FOOT = 10f * s;
        BLOCK_ROUND = 7f * s;
        TOGGLE_W = 20f * s; TOGGLE_SZ = 16f * s; FIELD_PAD_X = 8f * s;
        FIELD_MIN_W = 56f * s; REGISTRY_MIN_W = 80f * s; DROPDOWN_MIN_W = 70f * s;
        CHECKBOX_W = 16f * s; CHECKBOX_SZ = 14f * s; CHILD_SOCKET_PAD = 6f * s;
        MOUTH_PAD_TOP = 2f * s; MOUTH_PAD_BOT = 6f * s;
    }

    /** Frame padding sized so ImGui widgets match {@link #FIELD_H} at any zoom. */
    private void pushFieldFrameStyle(float rounding) {
        float textH = ImGui.getTextLineHeight();
        float padY = Math.max(1f, (FIELD_H - textH) * 0.5f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, rounding);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, FIELD_PAD_X, padY);
    }

    /** Light tint of the block colour for field backgrounds. */
    private static float[] fieldTint(int blockCol) {
        float[] bc = colF(blockCol);
        return new float[]{bc[0] * 0.24f + 0.76f, bc[1] * 0.24f + 0.76f, bc[2] * 0.24f + 0.76f};
    }

    private void pushFieldTintColors(int blockCol) {
        float[] t = fieldTint(blockCol);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, t[0], t[1], t[2], 0.97f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, Math.min(1f, t[0] + 0.04f), Math.min(1f, t[1] + 0.04f), Math.min(1f, t[2] + 0.04f), 1f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 1f, 1f, 1f, 1f);
        ImGui.pushStyleColor(ImGuiCol.Button, t[0], t[1], t[2], 0.97f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, Math.min(1f, t[0] + 0.04f), Math.min(1f, t[1] + 0.04f), Math.min(1f, t[2] + 0.04f), 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 1f, 1f, 1f, 1f);
        ImGui.pushStyleColor(ImGuiCol.Text, 0.09f, 0.10f, 0.13f, 1f);
    }

    private void popFieldTintColors() {
        ImGui.popStyleColor(7);
    }

    /** Corner radius matching the field fill / ImGui FrameRounding. */
    private float fieldChromeRounding(float height) {
        float h = Math.max(1f, height);
        // Tall multiline uses the same 6px-scale rounding as FrameRounding; single-line = pill.
        if (h > FIELD_H * 1.25f) return Math.min(6f * metricScale, h * 0.5f);
        return Math.min(FIELD_H * 0.5f, h * 0.5f);
    }

    /** 3D chrome drawn around a field widget (matches text inputs). */
    private void strokeFieldChrome(ImDrawList dl, float x0, float y0, float x1, float y1,
                                   int blockCol, boolean focused) {
        float r = fieldChromeRounding(y1 - y0);
        int outline = darken(blockCol, 0.62f);
        float thick = focused ? 2f : 1.5f;
        // Stroke centered on the edge eats half into the fill — expand so the outer
        // rim fully wraps the white/tinted background (esp. multiline fields).
        float pad = thick * 0.5f;
        dl.addRect(x0 - pad, y0 - pad, x1 + pad, y1 + pad,
                focused ? rgba(255, 255, 255, 230) : outline, r + pad, 0, thick);
        dl.addRectFilled(x0 + 3f * metricScale, y0 + 1f, x1 - 3f * metricScale, y0 + 3f * metricScale,
                rgba(0, 0, 0, 26), r);
    }

    private static String registryShortLabel(String id) {
        if (id == null || id.isBlank()) return EditorI18n.t("(выбрать)");
        String s = org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.parseSpec(id).id();
        if (s.isBlank()) s = id.trim();
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(colon + 1);
        if (s.endsWith(".png")) s = s.substring(0, s.length() - 4);
        return s.isEmpty() ? id.trim() : s;
    }

    private static EditorTexturePreview.Tex workspaceTexture(String path) {
        if (path == null || path.isBlank()) return null;
        String p = path.trim().replace('\\', '/');
        EditorTexturePreview.Tex tex = EditorTexturePreview.get(p);
        if (tex != null) return tex;
        int colon = p.indexOf(':');
        if (colon > 0) {
            tex = EditorTexturePreview.get(p.substring(colon + 1));
            if (tex != null) return tex;
        }
        return null;
    }

    private static EditorItemIcons.Icon textureAsItemIcon(String path) {
        if (path == null || path.isBlank()) return null;
        String p = path.trim().replace('\\', '/');
        int itemIdx = p.indexOf("textures/item/");
        if (itemIdx >= 0) {
            String name = p.substring(itemIdx + "textures/item/".length());
            if (name.endsWith(".png")) name = name.substring(0, name.length() - 4);
            return EditorItemIcons.icon("minecraft:" + name);
        }
        return null;
    }

    private void drawPreviewIcon(ImDrawList dl, EditorItemIcons.Icon icon, float x, float y, float size, int blockCol) {
        float pad = 2f * metricScale;
        dl.addRectFilled(x, y, x + size, y + size, rgba(0, 0, 0, 45), 4f * metricScale);
        float iw = size - pad * 2f;
        dl.addImage(icon.atlasGlId(), x + pad, y + pad, x + pad + iw, y + pad + iw,
                icon.u0(), icon.v0(), icon.u1(), icon.v1());
        dl.addRect(x, y, x + size, y + size, darken(blockCol, 0.62f), 4f * metricScale, 0, 1.2f);
    }

    private void drawPreviewTexture(ImDrawList dl, EditorTexturePreview.Tex tex, float x, float y, float size, int blockCol) {
        float[] fit = EditorTexturePreview.fit(tex, size - 4f * metricScale, size - 4f * metricScale);
        float ox = x + (size - fit[0]) * 0.5f;
        float oy = y + (size - fit[1]) * 0.5f;
        dl.addRectFilled(x, y, x + size, y + size, rgba(0, 0, 0, 45), 4f * metricScale);
        dl.addImage(tex.glId(), ox, oy, ox + fit[0], oy + fit[1]);
        dl.addRect(x, y, x + size, y + size, darken(blockCol, 0.62f), 4f * metricScale, 0, 1.2f);
    }

    private void drawTextureOptionIcon(String path, float size) {
        var tex = EditorTexturePreview.cached(path);
        if (tex != null) {
            float[] fit = EditorTexturePreview.fit(tex, size, size);
            ImGui.image(tex.glId(), fit[0], fit[1]);
            if (fit[0] < size) ImGui.sameLine(0f, 0f);
            return;
        }
        var icon = textureAsItemIcon(path);
        if (icon != null) {
            ImGui.image(icon.atlasGlId(), size, size, icon.u0(), icon.v0(), icon.u1(), icon.v1());
            return;
        }
        ImGui.dummy(size, size);
    }

    private static float[] parseColorFieldValue(String v) {
        if (v == null || v.isBlank()) return new float[]{1f, 1f, 1f, 1f};
        String s = v.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) {
            try {
                int rgb = (int) Long.parseLong(s.substring(2), 16);
                return new float[]{
                        ((rgb >> 16) & 0xFF) / 255f,
                        ((rgb >> 8) & 0xFF) / 255f,
                        (rgb & 0xFF) / 255f,
                        1f
                };
            } catch (NumberFormatException ignored) {
                return new float[]{0f, 0f, 0f, 1f};
            }
        }
        return UiStyleEditor.hexToRgba(s);
    }

    private static String formatColorFieldValue(float[] rgba, String previous) {
        int r = Math.round(Math.max(0f, Math.min(1f, rgba[0])) * 255f);
        int g = Math.round(Math.max(0f, Math.min(1f, rgba[1])) * 255f);
        int b = Math.round(Math.max(0f, Math.min(1f, rgba[2])) * 255f);
        // Script lexer treats # as comment — always emit 0xRRGGBB for .spr codegen.
        return String.format("0x%06X", (r << 16) | (g << 8) | b);
    }

    private static final int COL_CANVAS = rgba(18, 20, 26, 255);
    private static final int COL_GRID_DOT = rgba(255, 255, 255, 22);
    private static final int COL_SELECT = rgba(255, 200, 60, 255);
    private static final int COL_BAND = rgba(90, 150, 255, 40);
    private static final int COL_BAND_LINE = rgba(120, 170, 255, 180);
    private static final int COL_TEXT = rgba(245, 246, 250, 255);
    private static final int COL_TEXT_SOFT = rgba(235, 237, 243, 235);
    private static final int COL_SOCKET_BG = rgba(0, 0, 0, 90);
    private static final int COL_SHADOW = rgba(0, 0, 0, 90);
    private static final int COL_PANEL_BG = rgba(24, 27, 34, 255);

    public VisualScriptEditor(Path sprvPath) {
        this.sprvPath = sprvPath;
        OPEN_BY_PATH.put(sprvPath.toAbsolutePath().normalize().toString(), this);
        VisualBlockRegistry.ensureLoaded();
        load();
    }

    public boolean isDirty() { return dirty; }

    public boolean canUndo() { return !undoStack.isEmpty(); }

    public boolean canRedo() { return !redoStack.isEmpty(); }

    // ==================== Undo / redo ====================

    private static VBlock cloneBlockSameId(VBlock src) {
        VBlock c = new VBlock();
        c.id = src.id;
        c.type = src.type;
        c.x = src.x;
        c.y = src.y;
        c.next = src.next;
        c.fields.putAll(src.fields);
        c.socketOn.addAll(src.socketOn);
        c.socketChild.putAll(src.socketChild);
        c.stmtChild.putAll(src.stmtChild);
        return c;
    }

    private DocSnap captureDoc() {
        Map<String, VBlock> copy = new LinkedHashMap<>();
        for (VBlock b : blocks.values()) copy.put(b.id, cloneBlockSameId(b));
        return new DocSnap(copy, idCounter, new LinkedHashSet<>(selection));
    }

    private void restoreDoc(DocSnap snap) {
        blocks.clear();
        for (VBlock b : snap.blocks.values()) blocks.put(b.id, cloneBlockSameId(b));
        idCounter = snap.idCounter;
        selection.clear();
        selection.addAll(snap.selection);
        fieldBufs.clear();
        fieldEditWidth.clear();
        activeFieldKey = null;
        fieldHistoryKey = null;
        dragHistoryPushed = false;
        dirty = true;
        markLayoutDirty();
    }

    private void markLayoutDirty() {
        layoutDirty = true;
    }

    /** Snapshot current document before a mutating edit. */
    private void pushUndo() {
        undoStack.push(captureDoc());
        while (undoStack.size() > HISTORY_LIMIT) undoStack.removeLast();
        redoStack.clear();
        fieldHistoryKey = null;
    }

    /** One undo entry for a whole field typing session. */
    private void noteFieldEdit(String key) {
        if (key == null) {
            pushUndo();
            dirty = true;
            markLayoutDirty();
            return;
        }
        if (!key.equals(fieldHistoryKey)) {
            pushUndo();
            fieldHistoryKey = key;
        }
        dirty = true;
        markLayoutDirty();
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(captureDoc());
        restoreDoc(undoStack.pop());
        status = EditorI18n.t("Отменено");
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(captureDoc());
        restoreDoc(redoStack.pop());
        status = EditorI18n.t("Повторено");
    }

    // ==================== Persistence ====================

    private void load() {
        blocks.clear();
        missingTypes.clear();
        try {
            if (!Files.exists(sprvPath)) return;
            String raw = Files.readString(sprvPath, StandardCharsets.UTF_8);
            if (raw.isBlank()) return;
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            if (root.has("blocklyXml")) {
                importBlocklyXml(root.get("blocklyXml").getAsString());
                dirty = true;
                status = EditorI18n.t("Импортирован .sprv из Spraute Studio — сохраните для конвертации");
                return;
            }
            if (root.has("idCounter")) idCounter = root.get("idCounter").getAsInt();
            JsonArray arr = root.getAsJsonArray("blocks");
            if (arr != null) {
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    VBlock b = new VBlock();
                    b.id = o.get("id").getAsString();
                    b.type = normalizeType(o.get("type").getAsString());
                    if (LEGACY_MARK_BLOCK_TYPE.equals(b.type)) b.type = MARK_BLOCK_TYPE;
                    registerMissingIfNeeded(b.type);
                    b.x = o.has("x") ? o.get("x").getAsFloat() : 0f;
                    b.y = o.has("y") ? o.get("y").getAsFloat() : 0f;
                    if (o.has("next") && !o.get("next").isJsonNull()) b.next = o.get("next").getAsString();
                    if (o.has("fields")) {
                        for (var e : o.getAsJsonObject("fields").entrySet()) {
                            b.fields.put(e.getKey(), e.getValue().getAsString());
                        }
                    }
                    if (o.has("socketOn")) {
                        for (JsonElement s : o.getAsJsonArray("socketOn")) b.socketOn.add(s.getAsString());
                    }
                    if (o.has("sockets")) {
                        for (var e : o.getAsJsonObject("sockets").entrySet()) {
                            b.socketChild.put(e.getKey(), e.getValue().getAsString());
                        }
                    }
                    if (o.has("stmts")) {
                        for (var e : o.getAsJsonObject("stmts").entrySet()) {
                            b.stmtChild.put(e.getKey(), e.getValue().getAsString());
                        }
                    }
                    blocks.put(b.id, b);
                }
            }
            // Legacy pin marks → real bookmark blocks (once; save drops the old array)
            JsonArray marks = root.getAsJsonArray("bookmarks");
            boolean alreadyHasMarkBlocks = false;
            for (VBlock b : blocks.values()) {
                if (MARK_BLOCK_TYPE.equals(b.type)) { alreadyHasMarkBlocks = true; break; }
            }
            if (marks != null && !marks.isEmpty() && !alreadyHasMarkBlocks) {
                VisualBlockDef def = VisualBlockRegistry.find(MARK_BLOCK_TYPE);
                if (def != null) {
                    for (JsonElement el : marks) {
                        if (!el.isJsonObject()) continue;
                        JsonObject o = el.getAsJsonObject();
                        float mx = o.has("x") ? o.get("x").getAsFloat() : 0f;
                        float my = o.has("y") ? o.get("y").getAsFloat() : 0f;
                        String name = o.has("name") ? o.get("name").getAsString() : EditorI18n.t("Метка");
                        VBlock b = addBlock(def, mx, my);
                        b.fields.put("name", name == null || name.isBlank() ? EditorI18n.t("Метка") : name);
                    }
                    dirty = true;
                }
            }
        } catch (Exception e) {
            status = EditorI18n.t("Ошибка чтения: ") + e.getMessage();
        }
    }

    private String normalizeType(String type) {
        // Legacy .sprv could store ns:id instead of ns.id
        if (type != null && type.contains(":")) {
            String dot = type.replace(':', '.');
            if (VisualBlockRegistry.find(dot) != null) return dot;
        }
        return type;
    }

    private void registerMissingIfNeeded(String type) {
        if (VisualBlockRegistry.find(type) == null && !missingTypes.contains(type)) {
            missingTypes.add(type);
        }
    }

    public void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("idCounter", idCounter);
            JsonArray arr = new JsonArray();
            for (VBlock b : blocks.values()) {
                JsonObject o = new JsonObject();
                o.addProperty("id", b.id);
                o.addProperty("type", b.type);
                o.addProperty("x", b.x);
                o.addProperty("y", b.y);
                if (b.next != null) o.addProperty("next", b.next);
                JsonObject f = new JsonObject();
                for (var e : b.fields.entrySet()) f.addProperty(e.getKey(), e.getValue());
                o.add("fields", f);
                JsonArray on = new JsonArray();
                for (String s : b.socketOn) on.add(s);
                o.add("socketOn", on);
                JsonObject sc = new JsonObject();
                for (var e : b.socketChild.entrySet()) sc.addProperty(e.getKey(), e.getValue());
                o.add("sockets", sc);
                if (!b.stmtChild.isEmpty()) {
                    JsonObject st = new JsonObject();
                    for (var e : b.stmtChild.entrySet()) st.addProperty(e.getKey(), e.getValue());
                    o.add("stmts", st);
                }
                arr.add(o);
            }
            root.add("blocks", arr);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(sprvPath, gson.toJson(root), StandardCharsets.UTF_8);
            generateSpr();
            dirty = false;
            status = EditorI18n.t("Сохранено");
            org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.pushIfRemote(sprvPath);
            org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.pushIfRemote(
                    sprvPath.resolveSibling(sprvPath.getFileName().toString().replaceAll("\\.sprv$", "") + ".spr"));
            org.zonarstudio.spraute_engine.script.ScriptManager scripts =
                    org.zonarstudio.spraute_engine.script.ScriptManager.getInstance();
            if (scripts != null) {
                String name = sprvPath.getFileName().toString().replaceAll("\\.sprv$", "");
                scripts.refreshScriptFile(sprvPath.resolveSibling(name + ".spr"));
            }
        } catch (Exception e) {
            status = EditorI18n.t("Ошибка сохранения: ") + e.getMessage();
        }
    }

    // ==================== Legacy Blockly XML import ====================

    /** Converts a Spraute Studio .sprv (Blockly XML) into the mod's block model. */
    private void importBlocklyXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Element root = doc.getDocumentElement();
        float autoY = 20f;
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element el) || !el.getTagName().equals("block")) continue;
            VBlock b = importBlockElement(el);
            if (b == null) continue;
            b.x = parseF(el.getAttribute("x"), 20f);
            b.y = parseF(el.getAttribute("y"), autoY);
            autoY = b.y + 80f;
        }
    }

    private VBlock importBlockElement(Element el) {
        String type = normalizeType(el.getAttribute("type"));
        if (type == null || type.isEmpty()) return null;
        registerMissingIfNeeded(type);
        VBlock b = new VBlock();
        b.id = "b" + (idCounter++);
        b.type = type;
        blocks.put(b.id, b);

        for (Node n = el.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element child)) continue;
            switch (child.getTagName()) {
                case "mutation" -> {
                    var attrs = child.getAttributes();
                    for (int i = 0; i < attrs.getLength(); i++) {
                        Node a = attrs.item(i);
                        String an = a.getNodeName();
                        if (an.startsWith("f_")) {
                            b.fields.putIfAbsent(an.substring(2), a.getNodeValue());
                        } else if (an.startsWith("t_") && "true".equals(a.getNodeValue())) {
                            b.socketOn.add(an.substring(2));
                        } else if (an.startsWith("c_")) {
                            // cached value-input code — keep as field fallback for empty sockets
                            b.fields.putIfAbsent(an.substring(2), a.getNodeValue());
                        }
                    }
                }
                case "field" -> b.fields.put(child.getAttribute("name"), child.getTextContent());
                case "value" -> {
                    Element inner = firstBlockChild(child);
                    if (inner != null) {
                        VBlock vc = importBlockElement(inner);
                        if (vc != null) {
                            String name = child.getAttribute("name");
                            b.socketChild.put(name, vc.id);
                            markSocketIfField(b, name);
                        }
                    }
                }
                case "statement" -> {
                    Element inner = firstBlockChild(child);
                    if (inner != null) {
                        VBlock sc = importBlockElement(inner);
                        if (sc != null) b.stmtChild.put(child.getAttribute("name"), sc.id);
                    }
                }
                case "next" -> {
                    Element inner = firstBlockChild(child);
                    if (inner != null) {
                        VBlock nb = importBlockElement(inner);
                        if (nb != null) b.next = nb.id;
                    }
                }
            }
        }
        return b;
    }

    private void markSocketIfField(VBlock b, String name) {
        VisualBlockDef def = VisualBlockRegistry.find(b.type);
        if (def == null) return;
        for (VisualBlockDef.BodyItem it : def.body) {
            if (it.kind != VisualBlockDef.ItemKind.ROW) continue;
            for (VisualBlockDef.Token t : it.tokens) {
                if (t instanceof VisualBlockDef.Field f && f.name().equals(name)) {
                    b.socketOn.add(name);
                    return;
                }
            }
        }
    }

    private static Element firstBlockChild(Element parent) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el && (el.getTagName().equals("block") || el.getTagName().equals("shadow"))) {
                return el;
            }
        }
        return null;
    }

    private static float parseF(String s, float def) {
        try { return Float.parseFloat(s); } catch (Exception e) { return def; }
    }

    // ==================== .spr generation ====================

    /** Regenerates the sibling .spr from statement chains (top-to-bottom) with write_start prefix. */
    private void generateSpr() throws Exception {
        String name = sprvPath.getFileName().toString().replaceAll("\\.sprv$", "");
        List<VBlock> roots = new ArrayList<>();
        Set<String> referenced = referencedIds();
        for (VBlock b : blocks.values()) {
            if (!referenced.contains(b.id) && b.def().shape != VisualBlockDef.Shape.VALUE) roots.add(b);
        }
        roots.sort((a, b) -> Float.compare(a.y, b.y));

        StringBuilder body = new StringBuilder();
        for (VBlock root : roots) {
            // Mark blocks emit no code but must still walk the chain (first block is often a mark).
            String chain = genChain(root);
            if (!chain.isBlank()) {
                body.append(chain);
                body.append('\n');
            }
        }

        // write_start: import(...) lines first, deduped, from every used block type
        LinkedHashSet<String> importLines = new LinkedHashSet<>();
        LinkedHashSet<String> otherLines = new LinkedHashSet<>();
        for (VBlock b : blocks.values()) {
            VisualBlockDef def = b.def();
            for (String line : def.writeStart) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                if (t.matches("^(?:startScript|import)\\(\\s*[\"'][^\"']+[\"']\\s*\\)$")) {
                    importLines.add(t.replaceFirst("^startScript", "import"));
                } else {
                    otherLines.add(t);
                }
            }
        }
        StringBuilder out = new StringBuilder();
        out.append(EditorI18n.t("# Сгенерировано из ")).append(name).append(EditorI18n.t(".sprv — правки затрутся при сохранении блоков\n\n"));
        for (String l : importLines) out.append(l).append('\n');
        for (String l : otherLines) out.append(l).append('\n');
        if (!importLines.isEmpty() || !otherLines.isEmpty()) out.append('\n');
        out.append(body);

        Path spr = sprvPath.resolveSibling(name + ".spr");
        Files.writeString(spr, out.toString(), StandardCharsets.UTF_8);
    }

    private String genChain(VBlock first) {
        StringBuilder sb = new StringBuilder();
        VBlock cur = first;
        int guard = 0;
        while (cur != null && guard++ < 10000) {
            sb.append(genBlock(cur));
            cur = cur.next == null ? null : blocks.get(cur.next);
        }
        return sb.toString();
    }

    private String genBlock(VBlock b) {
        if (MARK_BLOCK_TYPE.equals(b.type)) return "";
        VisualBlockDef def = b.def();
        return VisualBlockCodegen.generate(def, resolverFor(b, def));
    }

    private VisualBlockCodegen.ValueResolver resolverFor(VBlock b, VisualBlockDef def) {
        Set<String> stmtNames = def.statementInputNames();
        Set<String> valueNames = def.valueInputNames();
        return new VisualBlockCodegen.ValueResolver() {
            @Override
            public String getVal(String name) {
                if (stmtNames.contains(name)) {
                    String childId = b.stmtChild.get(name);
                    VBlock child = childId == null ? null : blocks.get(childId);
                    return child == null ? "" : indent(genChain(child));
                }
                String childId = b.socketChild.get(name);
                if (childId != null) {
                    VBlock child = blocks.get(childId);
                    if (child != null) return genBlock(child);
                }
                return b.fields.getOrDefault(name, "");
            }

            @Override
            public boolean isFilled(String name) {
                if (stmtNames.contains(name)) return b.stmtChild.get(name) != null;
                if (b.socketChild.get(name) != null) return true;
                if (b.socketOn.contains(name)) return false;
                String v = b.fields.get(name);
                return v != null && !v.trim().isEmpty();
            }

            @Override
            public boolean isSocketConnected(String name) {
                if (stmtNames.contains(name)) return false;
                String childId = b.socketChild.get(name);
                return childId != null && blocks.get(childId) != null;
            }

            @Override
            public Map<String, String> fieldValues() {
                return b.fields;
            }
        };
    }

    private static String indent(String code) {
        if (code.isEmpty()) return code;
        StringBuilder sb = new StringBuilder();
        for (String line : code.split("\n", -1)) {
            if (!line.isEmpty()) sb.append("    ").append(line);
            sb.append('\n');
        }
        // trim final extra newline added by split
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private Set<String> referencedIds() {
        Set<String> referenced = new HashSet<>();
        for (VBlock b : blocks.values()) {
            if (b.next != null) referenced.add(b.next);
            referenced.addAll(b.socketChild.values());
            referenced.addAll(b.stmtChild.values());
        }
        return referenced;
    }

    // ==================== Rendering ====================

    public void render() {
        // Own Delete/Ctrl+Z/C/V only while this tab has focus. An open .sprv must not
        // steal hierarchy/inspector edits when the user selects a scene node.
        boolean focused = ImGui.isWindowFocused(imgui.flag.ImGuiFocusedFlags.RootAndChildWindows);
        if (focused) {
            hotkeyOwner = this;
            clipboardHotkeysClaimed = true;
        } else {
            if (hotkeyOwner == this) hotkeyOwner = null;
            clipboardHotkeysClaimed = false;
        }
        renderToolbar();
        float paletteW = Math.max(160f, 230f * (paletteZoom / PALETTE_ZOOM_DEFAULT));
        ImGui.pushStyleColor(ImGuiCol.ChildBg,
                ((COL_PANEL_BG) & 0xFF) / 255f, ((COL_PANEL_BG >> 8) & 0xFF) / 255f,
                ((COL_PANEL_BG >> 16) & 0xFF) / 255f, 1f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 7f, 7f);
        ImGui.beginChild("##vs-palette", paletteW, 0f, false);
        applyMetricsScale(paletteZoom);
        ImGui.setWindowFontScale(paletteZoom);
        renderPalette();
        // Ctrl+wheel over the palette: zoom sections / block previews.
        if (ImGui.isWindowHovered(imgui.flag.ImGuiHoveredFlags.ChildWindows)
                && ImGui.getIO().getKeyCtrl()) {
            float wheel = ImGui.getIO().getMouseWheel();
            if (wheel != 0f) {
                paletteZoom = Math.max(PALETTE_ZOOM_MIN, Math.min(PALETTE_ZOOM_MAX,
                        paletteZoom * (wheel > 0 ? 1.1f : 1f / 1.1f)));
            }
        }
        ImGui.setWindowFontScale(1f);
        applyMetricsScale(1f);
        ImGui.endChild();
        ImGui.popStyleVar();
        ImGui.popStyleColor();
        ImGui.sameLine(0f, 0f);
        renderCanvas(Math.max(120f, ImGui.getContentRegionAvailX()));
    }

    private void renderToolbar() {
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 6f);
        ImGui.pushStyleColor(ImGuiCol.Button, 0.23f, 0.51f, 0.96f, dirty ? 1f : 0.55f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.30f, 0.58f, 1f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.18f, 0.42f, 0.85f, 1f);
        if (ImGui.button(dirty ? EditorI18n.t("  Сохранить *  ") : EditorI18n.t("  Сохранить  "), 0f, 26f)
                || (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.S))) {
            save();
        }
        ImGui.popStyleColor(3);
        ImGui.sameLine();
        ImGui.pushStyleColor(ImGuiCol.Button, 1f, 1f, 1f, 0.08f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 1f, 1f, 1f, 0.16f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 1f, 1f, 1f, 0.24f);
        if (ImGui.button(EditorI18n.t("  Плагины ⟳  "), 0f, 26f)) {
            VisualBlockRegistry.reload();
            palettePreview.clear();
            paletteSizeCache.clear();
            status = EditorI18n.t("Плагины перезагружены");
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(EditorI18n.t("Перечитать spraute_engine/plugins/"));
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("  Менеджер  "), 0f, 26f)) {
            PluginManagerPanel.requestOpen();
        }
        ImGui.popStyleColor(3);
        ImGui.popStyleVar();
        if (ImGui.isItemHovered()) ImGui.setTooltip(EditorI18n.t("Окно управления плагинами"));
        ImGui.sameLine();
        ImGui.alignTextToFramePadding();
        ImGui.textDisabled(EditorI18n.t(
                "СКМ — панорама · Ctrl+колёсико — масштаб · Ctrl+Z/Y · Ctrl+C/V/X/D · Del · Ctrl+A"));
        if (!status.isBlank()) {
            ImGui.sameLine();
            ImGui.textColored(0.55f, 0.85f, 0.6f, 1f, status);
        }
        if (!missingTypes.isEmpty()) {
            ImGui.textColored(1f, 0.45f, 0.4f, 1f,
                    EditorI18n.localize("⚠ Не найдены блоки (показаны красным): ")
                            + String.join(", ", missingTypes));
        }
    }

    // ==================== Favorites ====================

    private static Path favoritesFile() {
        return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                .resolve("spraute_engine").resolve("editor_favorites.txt");
    }

    private static Set<String> favorites() {
        if (favorites == null) {
            favorites = new LinkedHashSet<>();
            try {
                Path f = favoritesFile();
                if (Files.exists(f)) {
                    for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                        if (!line.isBlank()) favorites.add(line.trim());
                    }
                }
            } catch (Exception ignored) {}
        }
        return favorites;
    }

    private static void toggleFavorite(String fullId) {
        Set<String> fav = favorites();
        if (!fav.remove(fullId)) fav.add(fullId);
        try {
            Files.createDirectories(favoritesFile().getParent());
            Files.write(favoritesFile(), fav, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    // ==================== Palette ====================

    private void renderPalette() {
        // Search box with rounded dark frame
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 6f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 8f, 4f);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.055f, 0.065f, 0.085f, 1f);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.inputTextWithHint("##vs-filter", EditorI18n.t("Поиск по блокам"), paletteFilter);
        ImGui.popStyleColor();
        ImGui.popStyleVar(2);
        ImGui.dummy(1f, 3f);
        String filter = paletteFilter.get().trim().toLowerCase(Locale.ROOT);

        // Clip in screen space — skip draw for scrolled-away previews.
        paletteClipMinY = ImGui.getWindowPosY();
        paletteClipMaxY = paletteClipMinY + ImGui.getWindowHeight();
        if (paletteSizeCacheScale != metricScale) {
            paletteSizeCache.clear();
            paletteSizeCacheScale = metricScale;
        }

        // Favorites pinned on top (header always; blocks only when open / filtering)
        if (!favorites().isEmpty()) {
            boolean favOpen = !filter.isEmpty() || categoryOpen.getOrDefault("favorites", true);
            List<VisualBlockDef> favVisible = favOpen ? filterPaletteDefs(favorites(), filter) : List.of();
            if (favOpen && favVisible.isEmpty()) {
                // open with filter and nothing matches — hide section
            } else {
                renderPaletteCategory("favorites", EditorI18n.t("Избранное"), "#f5c542",
                        EditorI18n.t("ПКМ по блоку — убрать из избранного"),
                        favVisible, filter, true, "star", false);
            }
        }

        Map<String, VisualBlockDef> all = VisualBlockRegistry.all();
        for (VisualBlockRegistry.Category cat : VisualBlockRegistry.orderedCategories()) {
            if (cat.blockIds.isEmpty()) continue;
            boolean open = !filter.isEmpty() || categoryOpen.getOrDefault(cat.name, false);
            // Closed: header only — skip filter/layout of every block each frame.
            List<VisualBlockDef> visible = List.of();
            if (open) {
                visible = new ArrayList<>(cat.blockIds.size());
                for (String id : cat.blockIds) {
                    VisualBlockDef d = all.get(id);
                    if (d == null || !paletteMatchesFilter(d, filter)) continue;
                    visible.add(d);
                }
                if (visible.isEmpty()) continue;
            }
            String catNote = cat.note == null ? null : PluginI18n.t(cat.note);
            renderPaletteCategory(cat.name, PluginI18n.t(cat.name), cat.colorHex, catNote,
                    visible, filter, false, null, true);
        }
        if (ImGui.isMouseReleased(0)) {
            categoryDragging = false;
            categoryPressName = null;
        }
    }

    private static List<VisualBlockDef> filterPaletteDefs(Collection<String> ids, String filter) {
        List<VisualBlockDef> out = new ArrayList<>();
        Map<String, VisualBlockDef> all = VisualBlockRegistry.all();
        for (String id : ids) {
            VisualBlockDef d = all.get(id);
            if (d == null) continue;
            if (!paletteMatchesFilter(d, filter)) continue;
            out.add(d);
        }
        return out;
    }

    private static boolean paletteMatchesFilter(VisualBlockDef d, String filter) {
        if (filter == null || filter.isEmpty()) return true;
        return d.displayLabel().toLowerCase(Locale.ROOT).contains(filter)
                || d.displayLabelRaw().toLowerCase(Locale.ROOT).contains(filter)
                || d.fullId.toLowerCase(Locale.ROOT).contains(filter);
    }

    /** Palette category: card header (tinted bg, accent bar, chevron) + block previews. */
    private void renderPaletteCategory(String id, String displayName, String colorHex, String note,
                                       List<VisualBlockDef> visible, String filter, boolean defaultOpen,
                                       String iconName, boolean reorderable) {
        boolean open = !filter.isEmpty() || categoryOpen.getOrDefault(id, defaultOpen);
        int catCol = hexColor(colorHex);
        ImDrawList dl = ImGui.getWindowDrawList();

        float w = ImGui.getContentRegionAvailX();
        float h = ROW_H;
        ImGui.pushID("cat-" + id);
        ImGui.invisibleButton("##cat", w, h);
        float x0 = ImGui.getItemRectMinX();
        float y0 = ImGui.getItemRectMinY();
        boolean hov = ImGui.isItemHovered();
        if (ImGui.isItemClicked(0)) categoryPressName = id;

        boolean dropHover = false;
        boolean dropAfter = false;
        // Skip DnD on idle non-hovered headers (biggest empty-tab cost after open cats).
        if (reorderable && (hov || categoryDragging)) {
            if (ImGui.beginDragDropSource()) {
                categoryDragging = true;
                ImGui.setDragDropPayload(CATEGORY_PAYLOAD, id);
                ImGui.text(displayName);
                ImGui.endDragDropSource();
            }
            if (ImGui.beginDragDropTarget()) {
                float midY = (ImGui.getItemRectMinY() + ImGui.getItemRectMaxY()) * 0.5f;
                dropAfter = ImGui.getMousePosY() > midY;
                String dragged = ImGui.acceptDragDropPayload(CATEGORY_PAYLOAD);
                if (dragged != null && !dragged.equals(id)) {
                    VisualBlockRegistry.moveCategoryRelative(dragged, id, dropAfter);
                }
                dropHover = true;
                ImGui.endDragDropTarget();
            }
        }

        float s = metricScale;
        int tint = withAlpha(catCol, open ? 60 : (hov || dropHover ? 52 : 36));
        dl.addRectFilled(x0, y0, x0 + w, y0 + h, tint, 6f * s);
        if (hov || open || dropHover) dl.addRect(x0, y0, x0 + w, y0 + h, withAlpha(catCol, 110), 6f * s, 0, 1f);
        if (dropHover) {
            float lineY = dropAfter ? y0 + h - 1f : y0;
            dl.addRectFilled(x0, lineY - 1f, x0 + w, lineY + 2f, withAlpha(catCol, 220), 1f);
        }
        dl.addRectFilled(x0 + 5f * s, y0 + 6f * s, x0 + 8f * s, y0 + h - 6f * s, catCol, 2f * s);
        float labelX = x0 + 14f * s;
        if (iconName != null) {
            var icon = EditorIcons.get(iconName);
            if (icon != null) {
                float isize = 12f * s;
                float ix = x0 + 13f * s;
                float iy = y0 + (h - isize) * 0.5f;
                float[] c = colF(catCol);
                dl.addImage(icon.glId(), ix, iy, ix + isize, iy + isize,
                        0f, 0f, 1f, 1f, rgba((int) (c[0] * 255), (int) (c[1] * 255), (int) (c[2] * 255), 255));
                labelX = ix + isize + 5f * s;
            }
        }
        dl.addText(labelX, y0 + (h - ImGui.getTextLineHeight()) * 0.5f, COL_TEXT, displayName);
        float chevSize = 11f * s;
        float chevX = x0 + w - 12f * s - chevSize;
        float chevY = y0 + (h - chevSize) * 0.5f;
        var chev = EditorIcons.get("chevron-right");
        if (chev != null) {
            drawRotatedImage(dl, chev.glId(), chevX, chevY, chevSize, open ? 90f : 0f, withAlpha(catCol, 220));
        } else {
            dl.addText(chevX, y0 + (h - ImGui.getTextLineHeight()) * 0.5f,
                    withAlpha(catCol, 220), open ? "v" : ">");
        }
        if (hov) {
            if (note != null && !note.isBlank()) ImGui.setTooltip(note);
            else if (reorderable) ImGui.setTooltip(EditorI18n.t("Зажмите и перетащите — изменить порядок разделов"));
        }
        // Toggle open on click-release without a drag (so hold-drag reorders instead).
        if (filter.isEmpty() && id.equals(categoryPressName) && ImGui.isMouseReleased(0) && !categoryDragging) {
            categoryOpen.put(id, !open);
            open = !open;
        }
        ImGui.popID();
        ImGui.dummy(1f, 2f);
        if (!open) return;

        for (VisualBlockDef d : visible) {
            renderPaletteBlock(d);
        }
        ImGui.dummy(1f, 4f);
    }

    /** Draw a textured quad rotated clockwise around its centre (degrees). */
    private static void drawRotatedImage(ImDrawList dl, int texId, float x, float y, float size,
                                         float degrees, int col) {
        float cx = x + size * 0.5f;
        float cy = y + size * 0.5f;
        float rad = (float) Math.toRadians(degrees);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float half = size * 0.5f;
        // Local corners: TL, TR, BR, BL
        float[] lx = {-half, half, half, -half};
        float[] ly = {-half, -half, half, half};
        float[] px = new float[4];
        float[] py = new float[4];
        for (int i = 0; i < 4; i++) {
            px[i] = cx + lx[i] * cos - ly[i] * sin;
            py[i] = cy + lx[i] * sin + ly[i] * cos;
        }
        dl.addImageQuad(texId,
                px[0], py[0], px[1], py[1], px[2], py[2], px[3], py[3],
                0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f, col);
    }

    /** Full block preview in the palette (all rows, fields, sockets), draggable. */
    private void renderPaletteBlock(VisualBlockDef d) {
        ImGui.pushID("pal-" + d.fullId);
        ImGui.indent(5f);
        VBlock pb = palettePreview.computeIfAbsent(d.fullId, k -> {
            VBlock nb = new VBlock();
            nb.id = "pal:" + k;
            nb.type = k;
            fillDefaults(nb, d);
            return nb;
        });
        float[] cached = paletteSizeCache.get(d.fullId);
        if (cached == null || pb.rows == null) {
            previewMode = true;
            layoutBlock(pb, 0f, -100000f);
            previewMode = false;
            cached = new float[]{pb.w, pb.h};
            paletteSizeCache.put(d.fullId, cached);
        }
        float bw = cached[0], bh = cached[1];
        float itemH = bh + 2f;
        // Reserve space for scroll; skip expensive draw for off-screen items.
        float y0 = ImGui.getCursorScreenPosY();
        if (y0 + itemH < paletteClipMinY - 8f || y0 > paletteClipMaxY + 8f) {
            ImGui.dummy(Math.max(36f, bw), itemH);
            ImGui.unindent(5f);
            ImGui.popID();
            return;
        }
        previewMode = true;
        // Rows already measured; only place at the button rect.
        if (pb.rows == null) layoutBlock(pb, 0f, -100000f);
        ImGui.invisibleButton("##pal", Math.max(36f, bw), itemH);
        pb.w = bw;
        pb.h = bh;
        pb.sx = ImGui.getItemRectMinX();
        pb.sy = ImGui.getItemRectMinY();
        drawBlock(ImGui.getWindowDrawList(), pb);
        previewMode = false;
        boolean hov = ImGui.isItemHovered();
        if (ImGui.isItemActivated()) paletteDragType = d.fullId;
        if (hov) {
            ImGui.setTooltip(d.fullId + (d.shape == VisualBlockDef.Shape.VALUE
                    ? EditorI18n.t("\nБлок-значение (перетащи на холст)") : EditorI18n.t("\nБлок-действие (перетащи на холст)")));
        }
        pushMenuStyle();
        if (ImGui.beginPopupContextItem("##pal-ctx")) {
            boolean fav = favorites().contains(d.fullId);
            if (ImGui.menuItem(fav ? EditorI18n.t("Убрать из избранного") : EditorI18n.t("В избранное"))) {
                toggleFavorite(d.fullId);
            }
            ImGui.endPopup();
        }
        popMenuStyle();
        ImGui.unindent(5f);
        ImGui.popID();
    }

    private void renderCanvas(float width) {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.094f, 0.10f, 0.117f, 1f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
        ImGui.beginChild("##vs-canvas", width, 0f, true,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse | ImGuiWindowFlags.NoMove);
        applyMetricsScale(zoom);
        ImGui.setWindowFontScale(zoom);

        ImVec2 origin = ImGui.getCursorScreenPos();
        canvasOriginX = origin.x;
        canvasOriginY = origin.y;
        float cw = ImGui.getContentRegionAvailX();
        float chh = ImGui.getContentRegionAvailY();
        lastCanvasW = cw;
        lastCanvasH = chh;
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(origin.x, origin.y, origin.x + cw, origin.y + chh, COL_CANVAS);
        viewMinX = origin.x;
        viewMinY = origin.y;
        viewMaxX = origin.x + cw;
        viewMaxY = origin.y + chh;
        drawDotGrid(dl, origin.x, origin.y, cw, chh);
        dl.pushClipRect(origin.x, origin.y, origin.x + cw, origin.y + chh, true);

        anyWidgetHovered = false;
        ensureCanvasLayout(origin.x, origin.y);
        drawSeq = 0;
        for (VBlock b : blocks.values()) b.zOrder = 0;
        for (VBlock root : laidRoots) drawChain(dl, root);

        if (paletteDragType != null) {
            VisualBlockDef d = VisualBlockRegistry.find(paletteDragType);
            float mx = ImGui.getMousePosX(), my = ImGui.getMousePosY();
            if (d != null) {
                float gw = Math.max(120f * metricScale, textW(d.displayLabel()) + 26f * metricScale);
                float gh = 26f * metricScale;
                dl.addRectFilled(mx + 3f, my + 4f, mx + gw + 3f, my + gh + 4f, withAlpha(COL_SHADOW, 60), BLOCK_ROUND);
                dl.addRectFilled(mx, my, mx + gw, my + gh, withAlpha(hexColor(d.colorHex), 200), BLOCK_ROUND);
                dl.addText(mx + 12f * metricScale, my + 5f * metricScale, COL_TEXT, d.displayLabel());
            }
            if (!ImGui.isMouseDown(0)) {
                boolean inside = mx >= origin.x && mx <= origin.x + cw && my >= origin.y && my <= origin.y + chh;
                if (inside && d != null) {
                    pushUndo();
                    VBlock nb = addBlock(d, (mx - origin.x) / zoom - panX, (my - origin.y) / zoom - panY);
                    selection.clear();
                    selection.add(nb.id);
                    dirty = true;
                }
                paletteDragType = null;
            }
        }

        handleCanvasInput(origin, cw, chh, dl);
        if (activeFieldKey != null && !ImGui.isAnyItemActive()) activeFieldKey = null;
        renderCanvasContextMenu();
        if (hotkeyOwner == this) handleBlockHotkeys();

        dl.popClipRect();
        ImGui.setWindowFontScale(1f);
        ImGui.endChild();
        applyMetricsScale(1f);
        ImGui.popStyleVar();
        ImGui.popStyleColor();
        // After child/clip: floating menu survives mouse move onto its items.
        renderFieldCoordsMenu();
    }

    /** Ctrl+Z/Y/C/V/X/D/A and Delete. Undo works even while a text field is focused. */
    private void handleBlockHotkeys() {
        boolean popupOpen = ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId
                | imgui.flag.ImGuiPopupFlags.AnyPopupLevel);
        boolean ctrl = ImGui.getIO().getKeyCtrl();

        if (!popupOpen && ctrl && ImGui.isKeyPressed(ImGuiKey.Z)) {
            if (ImGui.getIO().getKeyShift()) redo();
            else undo();
            return;
        }
        if (!popupOpen && ctrl && ImGui.isKeyPressed(ImGuiKey.Y)) {
            redo();
            return;
        }

        if (ImGui.isAnyItemActive() || ImGui.getIO().getWantTextInput()) return;
        if (popupOpen) return;

        if (ctrl && ImGui.isKeyPressed(ImGuiKey.C) && !selection.isEmpty()) {
            copySelection();
        }
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.X) && !selection.isEmpty()) {
            cutSelection();
        }
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.V) && hasBlockClipboard()) {
            pasteClipboard();
        }
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.D) && !selection.isEmpty()) {
            duplicateSelection();
        }
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.A)) {
            selectAllBlocks();
        }
        if (ImGui.isKeyPressed(ImGuiKey.Delete) && !selection.isEmpty()) {
            deleteSelection();
        }
    }

    private VBlock addBlock(VisualBlockDef d, float cx, float cy) {
        VBlock b = new VBlock();
        b.id = "b" + (idCounter++);
        b.type = d.fullId;
        b.x = cx;
        b.y = cy;
        fillDefaults(b, d);
        blocks.put(b.id, b);
        markLayoutDirty();
        return b;
    }

    private static void fillDefaults(VBlock b, VisualBlockDef d) {
        for (VisualBlockDef.BodyItem it : d.body) {
            if (it.kind != VisualBlockDef.ItemKind.ROW) continue;
            for (VisualBlockDef.Token t : it.tokens) {
                if (t instanceof VisualBlockDef.Field f) b.fields.put(f.name(), f.defaultValue());
                else if (t instanceof VisualBlockDef.Dropdown dd) b.fields.put(dd.name(), dd.defaultValue());
                else if (t instanceof VisualBlockDef.Checkbox cb) b.fields.put(cb.name(), cb.defaultChecked() ? "TRUE" : "FALSE");
            }
        }
    }

    // ==================== Layout ====================

    /** Visible rows given the block's current field values and filled sockets. */
    private List<Row> computeRows(VBlock b, VisualBlockDef def) {
        Set<String> filled = new LinkedHashSet<>();
        for (var e : b.fields.entrySet()) {
            if (b.socketOn.contains(e.getKey())) continue;
            if (e.getValue() != null && !e.getValue().trim().isEmpty()) filled.add(e.getKey());
        }
        filled.addAll(b.socketChild.keySet());
        filled.addAll(b.stmtChild.keySet());

        List<Row> rows = new ArrayList<>();
        for (VisualBlockDef.BodyItem it : def.body) {
            if (it.kind != VisualBlockDef.ItemKind.ROW) continue;
            boolean visible = true;
            for (VisualBlockDef.Cond c : it.conds) {
                if (!c.eval(b.fields, filled)) { visible = false; break; }
            }
            if (!visible) continue;

            // A row that is a single statement input becomes a statement area
            if (it.tokens.size() == 1 && it.tokens.get(0) instanceof VisualBlockDef.StatementInput si) {
                Row r = new Row();
                r.stmt = si;
                r.h = si.label().isBlank() ? 8f : ROW_H;
                rows.add(r);
                continue;
            }
            Row r = new Row();
            r.tokens = it.tokens;
            r.h = ROW_H;
            rows.add(r);
        }
        if (def.shape == VisualBlockDef.Shape.WRAPPER) {
            Row r = new Row();
            r.stmt = new VisualBlockDef.StatementInput("DO", "");
            r.h = 8f;
            rows.add(r);
        }
        if (rows.isEmpty()) {
            Row r = new Row();
            r.tokens = List.of(new VisualBlockDef.Label(def.rawId));
            r.h = ROW_H;
            rows.add(r);
        }
        return rows;
    }

    /**
     * Relayout only when structure/zoom changes; pan/origin shifts just translate screen coords.
     * Dragging always relayouts so stack heights stay correct.
     */
    private void ensureCanvasLayout(float originX, float originY) {
        boolean zoomChanged = Float.isNaN(laidZoom) || Math.abs(laidZoom - zoom) > 1e-6f;
        boolean needFull = layoutDirty || zoomChanged || draggingBlocks || banding;
        if (!needFull) {
            if (!laidRoots.isEmpty()) {
                float dx = (originX - laidOriginX) + (panX - laidPanX) * zoom;
                float dy = (originY - laidOriginY) + (panY - laidPanY) * zoom;
                if (dx != 0f || dy != 0f) {
                    for (VBlock b : blocks.values()) {
                        b.sx += dx;
                        b.sy += dy;
                    }
                }
            }
            laidOriginX = originX;
            laidOriginY = originY;
            laidPanX = panX;
            laidPanY = panY;
            return;
        }
        layoutEpoch++;
        applyMetricsScale(zoom);
        Set<String> referenced = referencedIds();
        laidRoots.clear();
        for (VBlock b : blocks.values()) {
            b.isChild = referenced.contains(b.id);
            if (!b.isChild) laidRoots.add(b);
        }
        for (VBlock root : laidRoots) {
            layoutChain(root, originX + (panX + root.x) * zoom, originY + (panY + root.y) * zoom);
        }
        laidOriginX = originX;
        laidOriginY = originY;
        laidPanX = panX;
        laidPanY = panY;
        laidZoom = zoom;
        if (!draggingBlocks && !banding) layoutDirty = false;
    }

    /**
     * Dot grid only inside the visible canvas rect ({@code cw×chh}).
     * Step grows when the viewport would otherwise get too many dots.
     */
    private void drawDotGrid(ImDrawList dl, float ox, float oy, float cw, float chh) {
        if (cw < 4f || chh < 4f) return;
        float step = 26f * zoom;
        if (step < 14f) step = 14f;
        // Cap ~400 dots in the visible area (not the infinite canvas).
        float est = (cw / step) * (chh / step);
        if (est > 400f) {
            step *= (float) Math.sqrt(est / 400f);
        }
        float modX = (panX * zoom) % step;
        float modY = (panY * zoom) % step;
        if (modX < 0f) modX += step;
        if (modY < 0f) modY += step;
        float dot = Math.max(1.25f, Math.min(2f, zoom * 1.5f));
        for (float gx = modX; gx < cw; gx += step) {
            for (float gy = modY; gy < chh; gy += step) {
                dl.addRectFilled(ox + gx, oy + gy, ox + gx + dot, oy + gy + dot, COL_GRID_DOT);
            }
        }
    }

    private static boolean mouseInRect(float x, float y, float w, float h) {
        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /** Real ImGui field only while hovered or actively editing — idle fields are painted. */
    private boolean wantsInteractiveField(String key, float x, float y, float w, float h) {
        if (previewMode) return false;
        if (key != null && key.equals(activeFieldKey)) return true;
        return mouseInRect(x, y, w, h);
    }

    /** Full silhouette / mouth detail only for hot blocks (hover, selection, edit). */
    private boolean blockDetailDraw(VBlock b) {
        if (previewMode) return false;
        if (selection.contains(b.id)) return true;
        if (activeFieldKey != null && activeFieldKey.startsWith(b.id + ":")) return true;
        return mouseInRect(b.sx - 6f, b.sy - 6f, b.w + 12f, b.h + NOTCH_H + 12f);
    }

    private void layoutChain(VBlock b, float sx, float sy) {
        layoutBlock(b, sx, sy);
        if (b.next != null) {
            VBlock next = blocks.get(b.next);
            if (next != null) layoutChain(next, sx, sy + b.h - 2f);
        }
    }

    private void layoutBlock(VBlock b, float sx, float sy) {
        VisualBlockDef def = b.def();
        b.sx = sx;
        b.sy = sy;
        b.rows = computeRows(b, def);

        float maxW = 90f * metricScale;
        float y = sy + (def.shape == VisualBlockDef.Shape.VALUE ? 3f : 4f);
        for (Row row : b.rows) {
            if (row.stmt != null) {
                // header (optional label) + inner chain
                float headerH = row.h;
                float innerY = y + headerH;
                String childId = b.stmtChild.get(row.stmt.name());
                VBlock child = childId == null ? null : blocks.get(childId);
                if (child != null) {
                    layoutChain(child, sx + STMT_INDENT, innerY + MOUTH_PAD_TOP);
                    row.innerH = chainHeight(child) + MOUTH_PAD_BOT;
                } else {
                    row.innerH = STMT_MIN_INNER;
                }
                y = innerY + row.innerH + STMT_FOOT;
                maxW = Math.max(maxW, 170f * metricScale);
            } else {
                float w = PAD;
                for (VisualBlockDef.Token t : row.tokens) w += tokenWidth(b, t) + GAP;
                maxW = Math.max(maxW, w + PAD - GAP);
                // the row grows vertically to fit the tallest inserted value block
                row.h = rowHeight(b, row);
                float fx = sx + PAD;
                for (VisualBlockDef.Token t : row.tokens) {
                    float tw = tokenWidth(b, t);
                    String socketName = socketNameOf(b, t);
                    if (socketName != null) {
                        String childId = b.socketChild.get(socketName);
                        VBlock child = childId == null ? null : blocks.get(childId);
                        if (child != null) {
                            float base = (t instanceof VisualBlockDef.ValueInput vi && !vi.label().isBlank())
                                    ? textW(pt(b, vi.label())) + GAP : 0f;
                            float childH = measureBlockH(child);
                            layoutBlock(child, fx + base, y + (row.h - childH) * 0.5f);
                        }
                    }
                    fx += tw + GAP;
                }
                y = y + row.h;
            }
        }
        b.w = maxW;
        b.h = (y - sy) + (def.shape == VisualBlockDef.Shape.VALUE ? 3f : 4f);
        if (def.shape == VisualBlockDef.Shape.VALUE && b.isChild) {
            // compact pill for nested value blocks
            b.h = Math.max(b.h, ROW_H - 4f);
        }
        b.cachedW = b.w;
        b.cachedH = b.h;
        b.measureEpoch = layoutEpoch;
    }

    /** Row height grows to fit multiline fields and nested socket blocks. */
    private float rowHeight(VBlock b, Row row) {
        float h = ROW_H;
        for (VisualBlockDef.Token t : row.tokens) {
            if (t instanceof VisualBlockDef.Field f && isMultilineField(f) && !b.socketOn.contains(f.name())) {
                String v = multilineFieldText(b, f.name());
                float fw = multilineFieldWidth(b, f.name(), v);
                h = Math.max(h, multilineFieldHeight(v, fw) + 6f * metricScale);
            }
            String socketName = socketNameOf(b, t);
            if (socketName == null) continue;
            String childId = b.socketChild.get(socketName);
            VBlock child = childId == null ? null : blocks.get(childId);
            if (child != null) h = Math.max(h, measureBlockH(child) + 4f);
        }
        return h;
    }

    /** Height of a block before final placement (cached per {@link #layoutEpoch}). */
    private float measureBlockH(VBlock b) {
        if (b.measureEpoch == layoutEpoch && b.cachedH > 0f) return b.cachedH;
        layoutBlock(b, 0f, -100000f);
        return b.h;
    }

    private float chainHeight(VBlock first) {
        float h = 0f;
        VBlock cur = first;
        int guard = 0;
        while (cur != null && guard++ < 1000) {
            h += cur.h - 2f;
            cur = cur.next == null ? null : blocks.get(cur.next);
        }
        return h + 2f;
    }

    /** Socket name if the token currently renders as a value socket, else null. */
    private String socketNameOf(VBlock b, VisualBlockDef.Token t) {
        if (t instanceof VisualBlockDef.ValueInput vi) return vi.name();
        if (t instanceof VisualBlockDef.Field f && b.socketOn.contains(f.name())) return f.name();
        if (t instanceof VisualBlockDef.Dropdown d && b.socketOn.contains(d.name())) return d.name();
        if (t instanceof VisualBlockDef.Checkbox c && b.socketOn.contains(c.name())) return c.name();
        return null;
    }

    private float tokenWidth(VBlock b, VisualBlockDef.Token t) {
        String socketName = socketNameOf(b, t);
        if (socketName != null) {
            String childId = b.socketChild.get(socketName);
            VBlock child = childId == null ? null : blocks.get(childId);
            float base = (t instanceof VisualBlockDef.ValueInput vi && !vi.label().isBlank())
                    ? textW(pt(b, vi.label())) + GAP : 0f;
            float toggleW = t instanceof VisualBlockDef.ValueInput ? 0f : TOGGLE_W;
            // CHILD_SOCKET_PAD: the child's ledge/outline sticks out and needs breathing room
            if (child != null) return base + measureBlockW(child) + CHILD_SOCKET_PAD + toggleW;
            return base + EMPTY_SOCKET_W + toggleW;
        }
        if (t instanceof VisualBlockDef.Label l) return textW(pt(b, l.text()));
        if (t instanceof VisualBlockDef.Field f) {
            String v = b.fields.getOrDefault(f.name(), "");
            if (isRegistryField(f) || isTextureField(f)) {
                String shown = registryShortLabel(v.isBlank() ? EditorI18n.t("(выбрать)") : v);
                return Math.max(REGISTRY_MIN_W, FIELD_H + FIELD_PAD_X + textW(shown) + FIELD_PAD_X) + TOGGLE_W;
            }
            if (isColorField(f)) {
                return FIELD_H + TOGGLE_W;
            }
            if (isMultilineField(f)) {
                String ml = multilineFieldText(b, f.name());
                return multilineFieldWidth(b, f.name(), ml) + TOGGLE_W;
            }
            return Math.max(FIELD_MIN_W, fieldContentWidth(b, f.name(), v)) + TOGGLE_W;
        }
        if (t instanceof VisualBlockDef.Dropdown d) {
            String v = b.fields.getOrDefault(d.name(), "");
            String label = dropdownLabel(b, d, v);
            float preview = "texture".equals(d.dynamicKind()) ? FIELD_H + 2f * metricScale : 0f;
            // Same as Studio: all dropdowns (incl. dropdown_npc / model / …) can toggle to a value socket.
            return Math.max(DROPDOWN_MIN_W, preview + textW(label) + FIELD_PAD_X + TOGGLE_SZ) + TOGGLE_W;
        }
        if (t instanceof VisualBlockDef.Checkbox) return CHECKBOX_W + TOGGLE_W;
        return 40f * metricScale;
    }

    /**
     * Width of a block before layout (used for nested measurement).
     * Uses the same {@link #layoutBlock} path as height so nested sockets stay consistent
     * and repeated measures within one epoch are free.
     */
    private float measureBlockW(VBlock b) {
        if (b.measureEpoch == layoutEpoch && b.cachedW > 0f) return b.cachedW;
        layoutBlock(b, 0f, -100000f);
        return b.w;
    }

    private String dropdownLabel(VBlock b, VisualBlockDef.Dropdown d, String value) {
        if ("texture".equals(d.dynamicKind())) {
            return value == null || value.isEmpty() ? "—" : registryShortLabel(value);
        }
        if ("animfile".equals(d.dynamicKind())) {
            return value == null || value.isEmpty() ? "—" : EditorAssetIndex.animationFileLabel(value);
        }
        if ("sound".equals(d.dynamicKind())) {
            if (value == null || value.isEmpty()) return EditorI18n.t("(не выбран)");
            return EditorAssetIndex.soundLabel(value);
        }
        for (String[] opt : d.options()) {
            if (opt[1].equals(value)) return pt(b, opt[0]);
        }
        return value == null || value.isEmpty() ? "—" : value;
    }

    private static String pt(VBlock b, String source) {
        if (source == null) return "";
        // Missing/obsolete placeholders use engine error strings (RU source → localize)
        if (b.def().missing) return EditorI18n.localize(source);
        String viaPlugin = PluginI18n.t(b.def().pluginNamespace, source);
        if (!viaPlugin.equals(source)) return viaPlugin;
        return EditorI18n.localize(source);
    }

    private float textW(String s) {
        String key = s == null ? "" : s;
        if (metricScale != textWCacheScale) {
            textWCache.clear();
            textWCacheScale = metricScale;
        }
        Float cached = textWCache.get(key);
        if (cached != null) return cached;
        float w = ImGui.calcTextSize(key).x;
        textWCache.put(key, w);
        return w;
    }

    /** Width of a text field while editing — only grows until focus leaves, so paste/typing doesn't yank focus. */
    private float fieldContentWidth(VBlock b, String fieldName, String value) {
        String key = b.id + ":" + fieldName;
        float measured = Math.max(FIELD_MIN_W, textW(value) + FIELD_PAD_X + FIELD_PAD_X);
        if (key.equals(activeFieldKey)) {
            float stable = fieldEditWidth.getOrDefault(key, measured);
            stable = Math.max(stable, measured);
            fieldEditWidth.put(key, stable);
            return stable;
        }
        fieldEditWidth.put(key, measured);
        return measured;
    }

    /** ABGR int colour → RGB float components for ImGui.pushStyleColor. */
    private static float[] colF(int abgr) {
        return new float[]{(abgr & 0xFF) / 255f, ((abgr >> 8) & 0xFF) / 255f, ((abgr >> 16) & 0xFF) / 255f};
    }

    // ==================== Drawing ====================

    private void drawChain(ImDrawList dl, VBlock b) {
        // Only paint blocks that intersect the canvas viewport.
        if (shouldDrawBlock(b)) {
            drawBlock(dl, b);
        }
        if (b.next != null) {
            VBlock next = blocks.get(b.next);
            if (next != null) {
                next.prevTabColor = hexColor(b.def().colorHex);
                drawChain(dl, next);
            }
        }
    }

    /**
     * Skip ImGui widgets + draw for blocks outside the canvas (biggest win with large graphs).
     * Still draws selected / actively edited / dragged blocks so interaction doesn't break.
     */
    private boolean shouldDrawBlock(VBlock b) {
        if (previewMode) return true;
        if (selection.contains(b.id)) return true;
        if (activeFieldKey != null && activeFieldKey.startsWith(b.id + ":")) return true;
        float margin = 48f * metricScale;
        return b.sx + b.w >= viewMinX - margin
                && b.sx <= viewMaxX + margin
                && b.sy + b.h >= viewMinY - margin
                && b.sy <= viewMaxY + margin;
    }

    private void drawBlock(ImDrawList dl, VBlock b) {
        if (!previewMode) b.zOrder = ++drawSeq;
        VisualBlockDef def = b.def();
        boolean selected = selection.contains(b.id);
        boolean detail = blockDetailDraw(b);
        int col = hexColor(def.colorHex);
        int edge = darken(col, 0.55f);       // bottom ledge / notch shading
        int outline = darken(col, 0.72f);
        float x0 = b.sx, y0 = b.sy, x1 = b.sx + b.w, y1 = b.sy + b.h;

        if (def.shape == VisualBlockDef.Shape.VALUE) {
            float r = Math.min(b.h * 0.5f, 13f);
            if (detail) {
                dl.addRectFilled(x0 + 2f, y0 + 3f, x1 + 2f, y1 + 3f, COL_SHADOW, r);
                dl.addRectFilled(x0, y0 + 2f, x1, y1 + 2f, edge, r);
            }
            fillBlockBody(dl, x0, y0, x1, y1, col, r);
            dl.addRect(x0, y0, x1, y1 + (detail ? 2f : 0f), outline, r, 0, 1f);
            if (selected) dl.addRect(x0 - 2f, y0 - 2f, x1 + 2f, y1 + 4f, COL_SELECT, r, 0, 2f);
        } else {
            // Statement: connectors (top notch + bottom tab) always; 3D ledge only when hot.
            float yb = y1 + (detail ? 2f : 0f);
            float nx0 = x0 + NOTCH_X, nx1 = x0 + NOTCH_X + NOTCH_W;
            if (detail) {
                dl.addRectFilled(x0 + 2f, y0 + 4f, x1 + 2f, y1 + 4f, COL_SHADOW, BLOCK_ROUND);
                dl.addRectFilled(x0, y0 + 3f, x1, yb, edge, BLOCK_ROUND);
                fillTabPoly(dl, nx0, nx1, y1, yb + NOTCH_H, edge);
            }
            fillBlockBody(dl, x0, y0, x1, y1 - (detail ? 1f : 0f), col, BLOCK_ROUND);
            // Bottom male tab (connector)
            fillTabPoly(dl, nx0 + 1f, nx1 - 1f, y1 - 2f, yb + NOTCH_H - (detail ? 0f : 1f), col);
            // Top female notch — empty punch or filled by previous block's tab
            if (b.prevTabColor == 0) {
                fillTabPoly(dl, nx0 - 0.5f, nx1 + 0.5f, y0 - 1f, y0 + NOTCH_H, COL_CANVAS);
                if (detail) {
                    dl.addRectFilled(nx0 + 2f, y0 + NOTCH_H - 1.5f * metricScale,
                            nx1 - 2f, y0 + NOTCH_H, rgba(0, 0, 0, 55));
                }
            } else {
                int pc = b.prevTabColor;
                float inset = 2f * metricScale;
                fillTabPoly(dl, nx0, nx1, y0 - 1f, y0 + NOTCH_H, darken(pc, 0.55f));
                if (detail) {
                    dl.pathClear();
                    dl.pathLineTo(nx0, y0 - 1f);
                    dl.pathLineTo(nx0 + inset, y0 + NOTCH_H);
                    dl.pathLineTo(nx1 - inset, y0 + NOTCH_H);
                    dl.pathLineTo(nx1, y0 - 1f);
                    dl.pathStroke(darken(pc, 0.72f), 0, 1f);
                }
                fillTabPoly(dl, nx0 + 1f, nx1 - 1f, y0 - 3f, y0 + NOTCH_H - 2f, pc);
            }
            strokeStmtOutline(dl, x0, y0, x1, Math.max(yb, y1), nx0, nx1, outline);
            b.prevTabColor = 0;
            if (selected) {
                dl.addRect(x0 - 2f, y0 - 2f, x1 + 2f, y1 + NOTCH_H + 2f, COL_SELECT, BLOCK_ROUND, 0, 2f);
            }
        }
        if (def.missing) {
            dl.addRect(x0, y0, x1, y1, rgba(255, 120, 110, 220), BLOCK_ROUND, 0, 2f);
        }

        float y = y0 + (def.shape == VisualBlockDef.Shape.VALUE ? 3f : 4f);
        if (b.rows == null) return;
        for (Row row : b.rows) {
            if (row.stmt != null) {
                float headerH = row.h;
                if (!row.stmt.label().isBlank()) {
                    dl.addText(x0 + PAD, y + (headerH - ImGui.getTextLineHeight()) * 0.5f, COL_TEXT,
                            pt(b, row.stmt.label()));
                }
                float innerY = y + headerH;
                float mouthBot = innerY + row.innerH;
                float mouthX = x0 + STMT_INDENT;
                float mouthRight = x1 + (detail ? 2f : 0f);
                float mouthLedge = detail ? 2f * metricScale : 1f * metricScale;
                dl.addRectFilled(mouthX, innerY - 1f, mouthRight, mouthBot, COL_CANVAS, detail ? 5f : 4f);
                if (detail) {
                    dl.addRectFilled(mouthX + 5f * metricScale, innerY - 1f, mouthRight, mouthBot, COL_CANVAS);
                    float mouthCorner = 5f * metricScale;
                    dl.addRectFilled(mouthX, innerY - 1f, mouthRight, innerY - 1f + mouthLedge, edge);
                    dl.addRectFilled(mouthX, innerY - 1f, mouthX + mouthCorner, innerY - 1f + mouthCorner, edge, mouthCorner);
                    dl.pathClear();
                    dl.pathLineTo(mouthX, mouthBot);
                    dl.pathLineTo(mouthX, innerY - 1f + mouthCorner);
                    dl.pathArcTo(mouthX + mouthCorner, innerY - 1f + mouthCorner, mouthCorner,
                            (float) Math.PI, (float) (Math.PI * 1.5));
                    dl.pathLineTo(mouthRight, innerY - 1f);
                    dl.pathStroke(outline, 0, 1.25f);
                }
                String childId = b.stmtChild.get(row.stmt.name());
                VBlock child = childId == null ? null : blocks.get(childId);
                // Mouth connector notch (always — even idle / empty C-blocks)
                if (child == null) {
                    float mouthNx0 = mouthX + NOTCH_X, mouthNx1 = mouthX + NOTCH_X + NOTCH_W;
                    float mouthNotchY = innerY - 1f + mouthLedge;
                    fillTabPoly(dl, mouthNx0, mouthNx1, mouthNotchY, mouthNotchY + NOTCH_H,
                            rgba(8, 10, 14, 220));
                } else {
                    drawChain(dl, child);
                }
                y = innerY + row.innerH + STMT_FOOT;
            } else {
                float tx = x0 + PAD;
                for (VisualBlockDef.Token t : row.tokens) {
                    float tw = tokenWidth(b, t);
                    drawToken(dl, b, t, tx, y, row.h, tw);
                    tx += tw + GAP;
                }
                y += row.h;
            }
        }
    }

    /** Rounded fill + subtle left→right light→dark wash (large, barely visible). */
    private void fillBlockBody(ImDrawList dl, float x0, float y0, float x1, float y1, int col, float rounding) {
        dl.addRectFilled(x0, y0, x1, y1, col, rounding);
        fillBlockGradient(dl, x0, y0, x1, y1);
    }

    /** Soft L→R shade over the block face (white tint → black tint). */
    private static void fillBlockGradient(ImDrawList dl, float x0, float y0, float x1, float y1) {
        if (x1 - x0 < 4f || y1 - y0 < 2f) return;
        int left = rgba(255, 255, 255, 22);
        int right = rgba(0, 0, 0, 30);
        dl.addRectFilledMultiColor(x0, y0, x1, y1, left, right, right, left);
    }

    /** Trapezoid tab fill: wider at yTop, slanted sides down to yBot. */
    private void fillTabPoly(ImDrawList dl, float nx0, float nx1, float yTop, float yBot, int col) {
        float inset = 2f * metricScale;
        dl.pathClear();
        dl.pathLineTo(nx0, yTop);
        dl.pathLineTo(nx1, yTop);
        dl.pathLineTo(nx1 - inset, yBot);
        dl.pathLineTo(nx0 + inset, yBot);
        dl.pathFillConvex(col);
    }

    /**
     * Statement/wrapper silhouette outline: top female notch (where the previous
     * block's tab inserts) + bottom male tab bump.
     */
    private void strokeStmtOutline(ImDrawList dl, float x0, float y0, float x1, float yb,
                                   float nx0, float nx1, int col) {
        float r = BLOCK_ROUND;
        float inset = 2f * metricScale;
        dl.pathClear();
        dl.pathArcTo(x0 + r, y0 + r, r, (float) Math.PI, (float) (Math.PI * 1.5));
        // top female notch
        dl.pathLineTo(nx0, y0);
        dl.pathLineTo(nx0 + inset, y0 + NOTCH_H);
        dl.pathLineTo(nx1 - inset, y0 + NOTCH_H);
        dl.pathLineTo(nx1, y0);
        dl.pathArcTo(x1 - r, y0 + r, r, (float) (Math.PI * 1.5), (float) (Math.PI * 2.0));
        dl.pathArcTo(x1 - r, yb - r, r, 0f, (float) (Math.PI * 0.5));
        // bottom male tab
        dl.pathLineTo(nx1, yb);
        dl.pathLineTo(nx1 - inset, yb + NOTCH_H);
        dl.pathLineTo(nx0 + inset, yb + NOTCH_H);
        dl.pathLineTo(nx0, yb);
        dl.pathArcTo(x0 + r, yb - r, r, (float) (Math.PI * 0.5), (float) Math.PI);
        dl.pathStroke(col, imgui.flag.ImDrawFlags.Closed, 1f);
    }

    private void drawToken(ImDrawList dl, VBlock b, VisualBlockDef.Token t, float tx, float rowY, float rowH, float tw) {
        float midY = rowY + (rowH - FIELD_H) * 0.5f;
        String socketName = socketNameOf(b, t);
        if (socketName != null) {
            float sockX = tx;
            if (t instanceof VisualBlockDef.ValueInput vi && !vi.label().isBlank()) {
                String lab = pt(b, vi.label());
                dl.addText(tx, rowY + (rowH - ImGui.getTextLineHeight()) * 0.5f, COL_TEXT, lab);
                sockX += textW(lab) + GAP;
            }
            String childId = b.socketChild.get(socketName);
            VBlock child = childId == null ? null : blocks.get(childId);
            if (child != null) {
                drawBlock(dl, child);
            } else {
                float r = FIELD_H * 0.5f;
                dl.addRectFilled(sockX, midY, sockX + EMPTY_SOCKET_W, midY + FIELD_H, COL_SOCKET_BG, r);
                // inner top shadow for depth
                dl.addRectFilled(sockX + 2f, midY + 1f, sockX + EMPTY_SOCKET_W - 2f, midY + 4f,
                        rgba(0, 0, 0, 60), r);
                dl.addRect(sockX, midY, sockX + EMPTY_SOCKET_W, midY + FIELD_H,
                        rgba(255, 255, 255, 55), r, 0, 1f);
            }
            // socket toggle (arrow-left-right) only for toggleable fields, not fixed value inputs
            if (!previewMode && !(t instanceof VisualBlockDef.ValueInput)) {
                drawSocketToggle(b, socketName, tx + tw - TOGGLE_SZ, rowY + (rowH - TOGGLE_SZ) * 0.5f, true);
            }
            return;
        }
        if (t instanceof VisualBlockDef.Label l) {
            dl.addText(tx, rowY + (rowH - ImGui.getTextLineHeight()) * 0.5f, COL_TEXT, pt(b, l.text()));
        } else if (t instanceof VisualBlockDef.Field f) {
            float fieldW = previewMode ? tw : tw - TOGGLE_W;
            if (isRegistryField(f)) {
                drawRegistryPicker(b, f, tx, midY, fieldW);
            } else if (isTextureField(f)) {
                drawTexturePicker(b, f, tx, midY, fieldW);
            } else if (isColorField(f)) {
                drawColorPicker(b, f, tx, midY, fieldW);
            } else if (isMultilineField(f)) {
                String mlText = multilineFieldText(b, f.name());
                float fh = multilineFieldHeight(mlText, fieldW);
                float fy = rowY + (rowH - fh) * 0.5f;
                drawMultilineFieldWidget(b, f.name(), tx, fy, fieldW, fh);
            } else {
                drawFieldWidget(b, f.name(), tx, midY, fieldW);
            }
            if (!previewMode) {
                drawSocketToggle(b, f.name(), tx + tw - TOGGLE_SZ, rowY + (rowH - TOGGLE_SZ) * 0.5f, false);
            }
        } else if (t instanceof VisualBlockDef.Dropdown d) {
            drawDropdownWidget(b, d, tx, midY, previewMode ? tw : tw - TOGGLE_W);
            if (!previewMode) {
                drawSocketToggle(b, d.name(), tx + tw - TOGGLE_SZ, rowY + (rowH - TOGGLE_SZ) * 0.5f, false);
            }
        } else if (t instanceof VisualBlockDef.Checkbox cb) {
            drawCheckboxWidget(b, cb, tx, midY);
            if (!previewMode) {
                drawSocketToggle(b, cb.name(), tx + tw - TOGGLE_SZ, rowY + (rowH - TOGGLE_SZ) * 0.5f, false);
            }
        }
    }

    private void paintPassiveTextField(int blockCol, float tr, float tg, float tb,
                                       float x, float y, float w, float h, String shown) {
        var dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x, y, x + w, y + h,
                rgba((int) (tr * 255), (int) (tg * 255), (int) (tb * 255), 245), Math.min(h, FIELD_H) * 0.5f);
        dl.addText(x + FIELD_PAD_X, y + (Math.min(h, FIELD_H) - ImGui.getTextLineHeight()) * 0.5f,
                rgba(23, 26, 33, 255), shown == null ? "" : shown);
        strokeFieldChrome(dl, x, y, x + w, y + h, blockCol, false);
    }

    private void drawFieldWidget(VBlock b, String fieldName, float x, float y, float w) {
        int blockCol = hexColor(b.def().colorHex);
        float[] bc = colF(blockCol);
        // light tint of the block colour instead of plain white
        float tr = bc[0] * 0.24f + 0.76f, tg = bc[1] * 0.24f + 0.76f, tb = bc[2] * 0.24f + 0.76f;
        String key = b.id + ":" + fieldName;
        String current = b.fields.getOrDefault(fieldName, "");
        String shown = b.def().missing ? EditorI18n.localize(current) : current;
        if (!previewMode && mouseInRect(x, y, w, FIELD_H)) {
            anyWidgetHovered = true;
            if (ImGui.isMouseClicked(0)) activeFieldKey = key;
        }
        if (previewMode || !wantsInteractiveField(key, x, y, w, FIELD_H)) {
            paintPassiveTextField(blockCol, tr, tg, tb, x, y, w, FIELD_H, shown);
            return;
        }
        ImGui.setCursorScreenPos(x, y);
        ImGui.pushID(b.id + ":" + fieldName);
        if (b.def().missing && !key.equals(activeFieldKey)) {
            // Show localized error text; keep Russian source in the model for language switches.
            String localized = shown;
            ImGui.setNextItemWidth(fieldContentWidth(b, fieldName, localized));
            ImString buf = fieldBufs.computeIfAbsent(key, k -> {
                ImString s = new ImString(2048);
                s.set(localized);
                return s;
            });
            if (!buf.get().equals(localized)) buf.set(localized);
            pushFieldFrameStyle(FIELD_H * 0.5f);
            ImGui.pushStyleColor(ImGuiCol.FrameBg, tr, tg, tb, 0.97f);
            ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, Math.min(1f, tr + 0.04f), Math.min(1f, tg + 0.04f), Math.min(1f, tb + 0.04f), 1f);
            ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 1f, 1f, 1f, 1f);
            ImGui.pushStyleColor(ImGuiCol.Text, 0.09f, 0.10f, 0.13f, 1f);
            ImGui.inputText("##f", buf, imgui.flag.ImGuiInputTextFlags.ReadOnly);
            ImGui.popStyleColor(4);
            ImGui.popStyleVar(2);
            var dl = ImGui.getWindowDrawList();
            boolean focused = ImGui.isItemActive() || ImGui.isItemHovered();
            strokeFieldChrome(dl, x, y, x + w, y + FIELD_H, blockCol, focused);
            if (ImGui.isItemHovered() || ImGui.isItemActive()) anyWidgetHovered = true;
            ImGui.popID();
            return;
        }
        ImGui.setNextItemWidth(fieldContentWidth(b, fieldName, current));
        ImString buf = fieldBufs.computeIfAbsent(key, k -> {
            ImString s = new ImString(2048);
            s.set(current);
            return s;
        });
        if (!buf.get().equals(current) && !key.equals(activeFieldKey)) {
            buf.set(current);
        }
        pushFieldFrameStyle(FIELD_H * 0.5f);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, tr, tg, tb, 0.97f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, Math.min(1f, tr + 0.04f), Math.min(1f, tg + 0.04f), Math.min(1f, tb + 0.04f), 1f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 1f, 1f, 1f, 1f);
        ImGui.pushStyleColor(ImGuiCol.Text, 0.09f, 0.10f, 0.13f, 1f);
        if (ImGui.inputText("##f", buf, ImGuiInputTextFlags.CallbackAlways, fieldSelCallback)) {
            noteFieldEdit(key);
            b.fields.put(fieldName, buf.get());
        }
        if (ImGui.isItemActive()) activeFieldKey = key;
        if (ImGui.isItemHovered() && ImGui.isMouseClicked(imgui.flag.ImGuiMouseButton.Right)) {
            openFieldContextMenu(b, fieldName, key);
        }
        if (coordsMenuOpen && key.equals(coordsMenuFieldKey)) anyWidgetHovered = true;
        ImGui.popStyleColor(4);
        ImGui.popStyleVar(2);
        // Scratch-style depth: darker outline in the block's colour + inner top shadow
        var dl = ImGui.getWindowDrawList();
        float ix0 = ImGui.getItemRectMinX(), iy0 = ImGui.getItemRectMinY();
        float ix1 = ImGui.getItemRectMaxX(), iy1 = ImGui.getItemRectMaxY();
        int outline = darken(blockCol, 0.62f);
        boolean focused = ImGui.isItemActive();
        dl.addRect(ix0, iy0, ix1, iy1, focused ? rgba(255, 255, 255, 230) : outline, FIELD_H * 0.5f, 0, focused ? 2f : 1.5f);
        dl.addRectFilled(ix0 + 3f * metricScale, iy0 + 1f, ix1 - 3f * metricScale, iy0 + 3f * metricScale,
                rgba(0, 0, 0, 26), FIELD_H * 0.5f);
        if (ImGui.isItemHovered() || ImGui.isItemActive()) anyWidgetHovered = true;
        ImGui.popID();
    }

    private static boolean isRegistryField(VisualBlockDef.Field f) {
        return switch (f.type()) {
            case "item", "block", "mob" -> true;
            default -> false;
        };
    }

    private static boolean isTextureField(VisualBlockDef.Field f) {
        return "texture".equals(f.type());
    }

    private static boolean isColorField(VisualBlockDef.Field f) {
        return "color".equals(f.type());
    }

    private static boolean isMultilineField(VisualBlockDef.Field f) {
        String t = f.type();
        return "textmultiline".equals(t) || "multiline".equals(t);
    }

    /**
     * Width for a multiline field: grows with content so the frame contains the text.
     * ImGui 1.89 InputTextMultiline has no word-wrap — stretching width is the reliable fix.
     * Past {@code maxW} the field stays capped and height grows via soft wrap (see height).
     */
    private float multilineFieldWidth(VBlock b, String fieldName, String value) {
        float minW = 140f * metricScale;
        float maxW = 520f * metricScale;
        String text = value == null ? "" : value;
        // Width follows the longest explicit line (not whole string with \n).
        float content = 0f;
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                content = Math.max(content, textW(text.substring(start, i)));
                start = i + 1;
            }
        }
        float measured = Math.max(minW, content + FIELD_PAD_X * 2f);
        String key = b.id + ":" + fieldName;
        if (key.equals(activeFieldKey)) {
            float stable = fieldEditWidth.getOrDefault(key, measured);
            stable = Math.max(stable, Math.min(maxW, measured));
            fieldEditWidth.put(key, stable);
            return stable;
        }
        float w = Math.min(maxW, measured);
        fieldEditWidth.put(key, w);
        return w;
    }

    /** Live text for layout/draw: buffered value while the field is focused. */
    private String multilineFieldText(VBlock b, String fieldName) {
        String key = b.id + ":" + fieldName;
        ImString buf = fieldBufs.get(key);
        if (buf != null && key.equals(activeFieldKey)) return buf.get();
        return b.fields.getOrDefault(fieldName, "");
    }

    /**
     * Height: starts at 1 line. Grows with Enter; if a line is wider than the capped
     * field width, also grows for soft-wrapped lines (passive paint uses wrap).
     */
    private float multilineFieldHeight(String value, float fieldWidth) {
        float lineH = ImGui.getTextLineHeight();
        float padY = 4f * metricScale;
        int lines = countMultilineFieldLines(value, fieldWidth);
        lines = Math.max(1, Math.min(12, lines));
        return lines * lineH + padY * 2f + 2f * metricScale;
    }

    private int countMultilineFieldLines(String value, float fieldWidth) {
        String text = value == null ? "" : value;
        if (text.isEmpty()) return 1;
        float wrapW = Math.max(8f, fieldWidth - FIELD_PAD_X * 2f);
        // Fast path: fits on one visual line per explicit line → just count \n.
        if (textW(text) <= wrapW && text.indexOf('\n') < 0) return 1;
        ImVec2 size = ImGui.calcTextSize(text, false, wrapW);
        float lineH = ImGui.getTextLineHeight();
        if (lineH <= 0f) return 1;
        return Math.max(1, (int) Math.ceil(size.y / lineH - 0.01f));
    }

    private void pushMultilineFieldFrameStyle(float rounding) {
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, rounding);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, FIELD_PAD_X, 4f * metricScale);
    }

    private void drawMultilineFieldWidget(VBlock b, String fieldName, float x, float y, float w, float h) {
        int blockCol = hexColor(b.def().colorHex);
        float[] bc = colF(blockCol);
        float tr = bc[0] * 0.24f + 0.76f, tg = bc[1] * 0.24f + 0.76f, tb = bc[2] * 0.24f + 0.76f;
        float round = 6f * metricScale;
        String current = b.fields.getOrDefault(fieldName, "");
        String key = b.id + ":" + fieldName;
        if (!previewMode && mouseInRect(x, y, w, h)) {
            anyWidgetHovered = true;
            if (ImGui.isMouseClicked(0)) activeFieldKey = key;
        }
        if (previewMode || !wantsInteractiveField(key, x, y, w, h)) {
            var dl = ImGui.getWindowDrawList();
            dl.addRectFilled(x, y, x + w, y + h,
                    rgba((int) (tr * 255), (int) (tg * 255), (int) (tb * 255), 245), round);
            String shown = b.def().missing ? EditorI18n.localize(current) : current;
            float wrapW = Math.max(8f, w - FIELD_PAD_X * 2f);
            var font = ImGui.getFont();
            int fontSize = (int) Math.max(1, Math.round(ImGui.getFontSize()));
            // wrap_width overload — keeps long description text inside the frame
            dl.addText(font, fontSize, x + FIELD_PAD_X, y + 4f * metricScale,
                    rgba(23, 26, 33, 255), shown, null, wrapW);
            strokeFieldChrome(dl, x, y, x + w, y + h, blockCol, false);
            return;
        }
        ImGui.setCursorScreenPos(x, y);
        ImGui.pushID(b.id + ":" + fieldName);
        ImString buf = fieldBufs.computeIfAbsent(key, k -> {
            ImString s = new ImString(8192);
            s.set(current);
            return s;
        });
        if (!buf.get().equals(current) && !key.equals(activeFieldKey)) {
            buf.set(current);
        }
        pushMultilineFieldFrameStyle(round);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, tr, tg, tb, 0.97f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, Math.min(1f, tr + 0.04f), Math.min(1f, tg + 0.04f), Math.min(1f, tb + 0.04f), 1f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 1f, 1f, 1f, 1f);
        ImGui.pushStyleColor(ImGuiCol.Text, 0.09f, 0.10f, 0.13f, 1f);
        int flags = imgui.flag.ImGuiInputTextFlags.AllowTabInput | ImGuiInputTextFlags.CallbackAlways;
        if (b.def().missing) flags |= imgui.flag.ImGuiInputTextFlags.ReadOnly;
        if (ImGui.inputTextMultiline("##fm", buf, w, h, flags, fieldSelCallback)) {
            if (!b.def().missing) {
                noteFieldEdit(key);
                b.fields.put(fieldName, buf.get());
            }
        }
        if (ImGui.isItemActive()) activeFieldKey = key;
        if (!b.def().missing && ImGui.isItemHovered() && ImGui.isMouseClicked(imgui.flag.ImGuiMouseButton.Right)) {
            openFieldContextMenu(b, fieldName, key);
        }
        if (coordsMenuOpen && key.equals(coordsMenuFieldKey)) anyWidgetHovered = true;
        ImGui.popStyleColor(4);
        ImGui.popStyleVar(2);
        var dl = ImGui.getWindowDrawList();
        float ix0 = ImGui.getItemRectMinX(), iy0 = ImGui.getItemRectMinY();
        float ix1 = ImGui.getItemRectMaxX(), iy1 = ImGui.getItemRectMaxY();
        boolean focused = ImGui.isItemActive();
        // Same chrome as passive path — rounding must match FrameRounding / fill.
        strokeFieldChrome(dl, ix0, iy0, ix1, iy1, blockCol, focused);
        if (ImGui.isItemHovered() || ImGui.isItemActive()) anyWidgetHovered = true;
        ImGui.popID();
    }

    /** Item/block/mob registry picker with icon preview and block-tinted 3D chrome. */
    private void drawRegistryPicker(VBlock b, VisualBlockDef.Field f, float x, float y, float w) {
        int blockCol = hexColor(b.def().colorHex);
        String value = b.fields.getOrDefault(f.name(), "");
        String shown = registryShortLabel(value);
        var icon = EditorItemIcons.icon(value);
        String rpKey = b.id + ":rp:" + f.name();
        boolean keepPopup = rpKey.equals(heldPopupFieldKey);
        if (previewMode || (!keepPopup && !wantsInteractiveField(rpKey, x, y, w, FIELD_H))) {
            if (!previewMode && mouseInRect(x, y, w, FIELD_H)) anyWidgetHovered = true;
            var dl = ImGui.getWindowDrawList();
            float[] t = fieldTint(blockCol);
            dl.addRectFilled(x, y, x + w, y + FIELD_H,
                    rgba((int) (t[0] * 255), (int) (t[1] * 255), (int) (t[2] * 255), 245), FIELD_H * 0.5f);
            float cx = x + FIELD_PAD_X;
            if (icon != null) {
                drawPreviewIcon(dl, icon, cx, y + 2f * metricScale, FIELD_H - 4f * metricScale, blockCol);
                cx += FIELD_H + 2f * metricScale;
            }
            dl.addText(cx, y + (FIELD_H - ImGui.getTextLineHeight()) * 0.5f, rgba(23, 26, 33, 255), shown);
            strokeFieldChrome(dl, x, y, x + w, y + FIELD_H, blockCol, false);
            return;
        }
        ImGui.pushID(rpKey);
        var dl = ImGui.getWindowDrawList();
        float previewSize = FIELD_H - 4f * metricScale;
        float btnX = x;
        if (icon != null) {
            drawPreviewIcon(dl, icon, x + 2f * metricScale, y + 2f * metricScale, previewSize, blockCol);
            btnX = x + FIELD_H + 2f * metricScale;
        }
        ImGui.setCursorScreenPos(btnX, y);
        pushFieldFrameStyle(FIELD_H * 0.5f);
        pushFieldTintColors(blockCol);
        if (ImGui.button(shown + "##pick", Math.max(40f * metricScale, w - (btnX - x)), FIELD_H)) {
            ImGui.openPopup("vs-registry-pick");
        }
        boolean focused = ImGui.isItemActive() || ImGui.isItemHovered();
        popFieldTintColors();
        ImGui.popStyleVar(2);
        strokeFieldChrome(dl, x, y, x + w, y + FIELD_H, blockCol, focused);
        if (ImGui.isItemHovered()) {
            anyWidgetHovered = true;
            if (!value.isBlank()) ImGui.setTooltip(value);
        }
        if (ImGui.beginPopup("vs-registry-pick")) {
            heldPopupFieldKey = rpKey;
            anyWidgetHovered = true;
            List<String> options = switch (f.type()) {
                case "block" -> EditorItemIcons.blocks();
                case "mob" -> EditorItemIcons.mobs();
                default -> EditorItemIcons.items();
            };
            EditorItemPicker.renderPopupList(value, options, true, "item".equals(f.type()), picked -> {
                String prev = b.fields.getOrDefault(f.name(), "");
                var prevSpec = org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.parseSpec(prev);
                var nextSpec = org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.parseSpec(picked);
                if (!prevSpec.id().equals(nextSpec.id()) || picked.isEmpty() != prev.isEmpty()) {
                    pushUndo();
                }
                b.fields.put(f.name(), picked);
                dirty = true;
                markLayoutDirty();
            }, this::pushUndo);
            ImGui.endPopup();
        } else if (rpKey.equals(heldPopupFieldKey)) {
            heldPopupFieldKey = null;
        }
        ImGui.popID();
    }

    /** Workspace / minecraft texture picker with thumbnail preview. */
    private void drawTexturePicker(VBlock b, VisualBlockDef.Field f, float x, float y, float w) {
        int blockCol = hexColor(b.def().colorHex);
        String value = b.fields.getOrDefault(f.name(), "");
        String shown = registryShortLabel(value);
        var tex = workspaceTexture(value);
        var icon = tex == null ? textureAsItemIcon(value) : null;
        String tpKey = b.id + ":tp:" + f.name();
        boolean keepTexPopup = tpKey.equals(heldPopupFieldKey);
        if (previewMode || (!keepTexPopup && !wantsInteractiveField(tpKey, x, y, w, FIELD_H))) {
            if (!previewMode && mouseInRect(x, y, w, FIELD_H)) anyWidgetHovered = true;
            var dl = ImGui.getWindowDrawList();
            float[] t = fieldTint(blockCol);
            dl.addRectFilled(x, y, x + w, y + FIELD_H,
                    rgba((int) (t[0] * 255), (int) (t[1] * 255), (int) (t[2] * 255), 245), FIELD_H * 0.5f);
            float cx = x + FIELD_PAD_X;
            if (tex != null) {
                drawPreviewTexture(dl, tex, cx, y + 2f * metricScale, FIELD_H - 4f * metricScale, blockCol);
                cx += FIELD_H + 2f * metricScale;
            } else if (icon != null) {
                drawPreviewIcon(dl, icon, cx, y + 2f * metricScale, FIELD_H - 4f * metricScale, blockCol);
                cx += FIELD_H + 2f * metricScale;
            }
            dl.addText(cx, y + (FIELD_H - ImGui.getTextLineHeight()) * 0.5f, rgba(23, 26, 33, 255), shown);
            strokeFieldChrome(dl, x, y, x + w, y + FIELD_H, blockCol, false);
            return;
        }
        ImGui.pushID(tpKey);
        var dl = ImGui.getWindowDrawList();
        float previewSize = FIELD_H - 4f * metricScale;
        float btnX = x;
        if (tex != null) {
            drawPreviewTexture(dl, tex, x + 2f * metricScale, y + 2f * metricScale, previewSize, blockCol);
            btnX = x + FIELD_H + 2f * metricScale;
        } else if (icon != null) {
            drawPreviewIcon(dl, icon, x + 2f * metricScale, y + 2f * metricScale, previewSize, blockCol);
            btnX = x + FIELD_H + 2f * metricScale;
        }
        ImGui.setCursorScreenPos(btnX, y);
        pushFieldFrameStyle(FIELD_H * 0.5f);
        pushFieldTintColors(blockCol);
        if (ImGui.button(shown + "##tex", Math.max(40f * metricScale, w - (btnX - x)), FIELD_H)) {
            ImGui.openPopup("vs-texture-pick");
        }
        boolean focused = ImGui.isItemActive() || ImGui.isItemHovered();
        popFieldTintColors();
        ImGui.popStyleVar(2);
        strokeFieldChrome(dl, x, y, x + w, y + FIELD_H, blockCol, focused);
        if (ImGui.isItemHovered()) {
            anyWidgetHovered = true;
            if (!value.isBlank()) {
                ImGui.beginTooltip();
                if (tex != null) {
                    float[] big = EditorTexturePreview.fit(tex, 160f * metricScale, 160f * metricScale);
                    ImGui.image(tex.glId(), big[0], big[1]);
                    ImGui.textDisabled(tex.width() + " x " + tex.height());
                } else if (icon != null) {
                    ImGui.image(icon.atlasGlId(), 64f * metricScale, 64f * metricScale,
                            icon.u0(), icon.v0(), icon.u1(), icon.v1());
                }
                ImGui.text(value);
                ImGui.endTooltip();
            }
        }
        if (ImGui.beginPopup("vs-texture-pick")) {
            heldPopupFieldKey = tpKey;
            anyWidgetHovered = true;
            renderTexturePopupList(value, picked -> {
                pushUndo();
                b.fields.put(f.name(), picked);
                dirty = true;
                markLayoutDirty();
            });
            ImGui.endPopup();
        } else if (tpKey.equals(heldPopupFieldKey)) {
            heldPopupFieldKey = null;
        }
        ImGui.popID();
    }

    private void renderTexturePopupList(String current, Consumer<String> onPick) {
        ImGui.inputTextWithHint("##tex-search", EditorI18n.t("Поиск текстуры…"), textureSearch);
        String filter = textureSearch.get().trim().toLowerCase(Locale.ROOT);
        ImGui.beginChild("##tex-list", 300f * metricScale, 240f * metricScale, true);
        int shown = 0;
        for (String option : EditorAssetIndex.textures()) {
            if (!filter.isEmpty() && !option.toLowerCase(Locale.ROOT).contains(filter)) continue;
            if (++shown > 400) {
                ImGui.textDisabled(EditorI18n.t("…уточните поиск"));
                break;
            }
            drawTextureOptionIcon(option, 18f * metricScale);
            ImGui.sameLine();
            if (ImGui.selectable(option + "##opt", option.equals(current))) {
                onPick.accept(option);
                ImGui.closeCurrentPopup();
            }
            if (ImGui.isItemHovered()) {
                var tex = workspaceTexture(option);
                if (tex != null) {
                    ImGui.beginTooltip();
                    float[] size = EditorTexturePreview.fit(tex, 192f * metricScale, 192f * metricScale);
                    ImGui.image(tex.glId(), size[0], size[1]);
                    ImGui.textDisabled(tex.width() + " x " + tex.height());
                    ImGui.endTooltip();
                }
            }
        }
        ImGui.endChild();
    }

    /** Colour field: one stylized swatch square that opens the picker. */
    private void drawColorPicker(VBlock b, VisualBlockDef.Field f, float x, float y, float w) {
        int blockCol = hexColor(b.def().colorHex);
        String value = b.fields.getOrDefault(f.name(), f.defaultValue());
        float[] rgba = parseColorFieldValue(value);
        float size = Math.min(w, FIELD_H);
        float round = 5f * metricScale;
        var dl = ImGui.getWindowDrawList();

        // base (shows through when colour is translucent)
        dl.addRectFilled(x, y, x + size, y + size, rgba(200, 200, 205, 255), round);
        // half-size checker for alpha readability
        if (rgba[3] < 0.999f) {
            float mid = size * 0.5f;
            dl.addRectFilled(x + mid, y, x + size, y + mid, rgba(150, 150, 155, 255));
            dl.addRectFilled(x, y + mid, x + mid, y + size, rgba(150, 150, 155, 255));
        }
        int fill = rgba((int) (rgba[0] * 255), (int) (rgba[1] * 255), (int) (rgba[2] * 255),
                (int) (rgba[3] * 255));
        dl.addRectFilled(x, y, x + size, y + size, fill, round);
        dl.addRect(x, y, x + size, y + size, darken(blockCol, 0.55f), round, 0, 1.4f);
        dl.addRect(x + 1f * metricScale, y + 1f * metricScale,
                x + size - 1f * metricScale, y + size - 1f * metricScale,
                rgba(255, 255, 255, 45), Math.max(1f, round - 1f), 0, 1f);

        if (previewMode) return;

        ImGui.pushID(b.id + ":cp:" + f.name());
        ImGui.setCursorScreenPos(x, y);
        if (ImGui.invisibleButton("##colsw", size, size)) {
            ImGui.openPopup("##colpick");
        }
        boolean hovered = ImGui.isItemHovered();
        if (hovered || ImGui.isItemActive()) anyWidgetHovered = true;
        if (hovered || ImGui.isItemActive()) {
            dl.addRect(x - 1f, y - 1f, x + size + 1f, y + size + 1f,
                    COL_SELECT, round + 1f, 0, 1.5f);
        }

        if (ImGui.beginPopup("##colpick")) {
            float[] buf = rgba.clone();
            int flags = ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.DisplayHex
                    | ImGuiColorEditFlags.PickerHueBar | ImGuiColorEditFlags.NoSidePreview
                    | ImGuiColorEditFlags.NoSmallPreview;
            if (ImGui.colorPicker4("##picker", buf, flags)) {
                noteFieldEdit(b.id + ":color:" + f.name());
                b.fields.put(f.name(), formatColorFieldValue(buf, value));
            }
            ImGui.endPopup();
        }
        ImGui.popID();
    }

    private void drawDropdownWidget(VBlock b, VisualBlockDef.Dropdown d, float x, float y, float w) {
        int blockCol = hexColor(b.def().colorHex);
        boolean textureMode = "texture".equals(d.dynamicKind());
        boolean soundMode = "sound".equals(d.dynamicKind());
        String value = b.fields.getOrDefault(d.name(), "");
        String label = dropdownLabel(b, d, value);
        float[] darkTint = dropdownTint(blockCol);
        if (previewMode) {
            var dl = ImGui.getWindowDrawList();
            dl.addRectFilled(x, y, x + w, y + FIELD_H,
                    rgba((int) (darkTint[0] * 255), (int) (darkTint[1] * 255), (int) (darkTint[2] * 255), 245),
                    FIELD_H * 0.5f);
            float tx = x + FIELD_PAD_X;
            if (textureMode && !value.isBlank()) {
                var tex = workspaceTexture(value);
                if (tex != null) {
                    drawPreviewTexture(dl, tex, tx, y + 2f * metricScale, FIELD_H - 4f * metricScale, blockCol);
                    tx += FIELD_H + 2f * metricScale;
                }
            }
            dl.addText(tx, y + (FIELD_H - ImGui.getTextLineHeight()) * 0.5f, COL_TEXT, label);
            strokeFieldChrome(dl, x, y, x + w, y + FIELD_H, blockCol, false);
            return;
        }
        ImGui.pushID(b.id + ":dd:" + d.name());
        var dl = ImGui.getWindowDrawList();
        float comboX = x;
        float comboW = w;
        if (textureMode && !value.isBlank()) {
            var tex = workspaceTexture(value);
            var icon = tex == null ? textureAsItemIcon(value) : null;
            if (tex != null) {
                drawPreviewTexture(dl, tex, x + 2f * metricScale, y + 2f * metricScale, FIELD_H - 4f * metricScale, blockCol);
                comboX = x + FIELD_H + 2f * metricScale;
                comboW = w - (FIELD_H + 2f * metricScale);
            } else if (icon != null) {
                drawPreviewIcon(dl, icon, x + 2f * metricScale, y + 2f * metricScale, FIELD_H - 4f * metricScale, blockCol);
                comboX = x + FIELD_H + 2f * metricScale;
                comboW = w - (FIELD_H + 2f * metricScale);
            }
        }
        String ddKey = b.id + ":dd:" + d.name();
        boolean keepOpen = ddKey.equals(heldPopupFieldKey);
        if (!keepOpen && !wantsInteractiveField(ddKey, x, y, w, FIELD_H)) {
            if (mouseInRect(x, y, w, FIELD_H)) anyWidgetHovered = true;
            dl.addRectFilled(comboX, y, x + w, y + FIELD_H,
                    rgba((int) (darkTint[0] * 255), (int) (darkTint[1] * 255), (int) (darkTint[2] * 255), 245),
                    FIELD_H * 0.5f);
            dl.addText(comboX + FIELD_PAD_X, y + (FIELD_H - ImGui.getTextLineHeight()) * 0.5f, COL_TEXT, label);
            strokeFieldChrome(dl, x, y, x + w, y + FIELD_H, blockCol, false);
            ImGui.popID();
            return;
        }
        if (soundMode) {
            drawSoundDropdown(b, d, x, y, w, comboX, comboW, ddKey, label, value, blockCol, darkTint);
            ImGui.popID();
            return;
        }
        ImGui.setCursorScreenPos(comboX, y);
        ImGui.setNextItemWidth(comboW);
        float[] accent = colF(brighten(blockCol, 1.25f));
        pushFieldFrameStyle(FIELD_H * 0.5f);
        ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding, 9f * metricScale);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 6f * metricScale, 6f * metricScale);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 4f * metricScale, 3f * metricScale);
        ImGui.pushStyleVar(ImGuiStyleVar.PopupBorderSize, 1f);
        // Dark field + light text (unlike light text inputs)
        ImGui.pushStyleColor(ImGuiCol.FrameBg, darkTint[0], darkTint[1], darkTint[2], 0.97f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered,
                Math.min(1f, darkTint[0] + 0.06f), Math.min(1f, darkTint[1] + 0.06f), Math.min(1f, darkTint[2] + 0.06f), 1f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive,
                Math.min(1f, darkTint[0] + 0.10f), Math.min(1f, darkTint[1] + 0.10f), Math.min(1f, darkTint[2] + 0.10f), 1f);
        ImGui.pushStyleColor(ImGuiCol.Button, darkTint[0], darkTint[1], darkTint[2], 0.97f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered,
                Math.min(1f, darkTint[0] + 0.06f), Math.min(1f, darkTint[1] + 0.06f), Math.min(1f, darkTint[2] + 0.06f), 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive,
                Math.min(1f, darkTint[0] + 0.10f), Math.min(1f, darkTint[1] + 0.10f), Math.min(1f, darkTint[2] + 0.10f), 1f);
        ImGui.pushStyleColor(ImGuiCol.Text, 0.93f, 0.94f, 0.97f, 1f);
        ImGui.pushStyleColor(ImGuiCol.PopupBg, darkTint[0] * 0.85f, darkTint[1] * 0.85f, darkTint[2] * 0.85f, 0.99f);
        ImGui.pushStyleColor(ImGuiCol.Border, accent[0], accent[1], accent[2], 0.55f);
        ImGui.pushStyleColor(ImGuiCol.Header, accent[0] * 0.35f, accent[1] * 0.35f, accent[2] * 0.35f, 0.55f);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, accent[0] * 0.5f, accent[1] * 0.5f, accent[2] * 0.5f, 0.65f);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, accent[0] * 0.65f, accent[1] * 0.65f, accent[2] * 0.65f, 0.8f);
        if (ImGui.beginCombo("##dd", label)) {
            heldPopupFieldKey = ddKey;
            anyWidgetHovered = true;
            List<String[]> options = optionsFor(b, d);
            if (options.isEmpty()) {
                ImGui.textDisabled(EditorI18n.t("(пусто — введите вручную)"));
            }
            for (String[] opt : options) {
                if (textureMode) {
                    drawTextureOptionIcon(opt[1], 18f * metricScale);
                    ImGui.sameLine();
                }
                boolean sel = opt[1].equals(value);
                if (ImGui.selectable(PluginI18n.t(b.def().pluginNamespace, opt[0]), sel)) {
                    pushUndo();
                    b.fields.put(d.name(), opt[1]);
                    dirty = true;
                    markLayoutDirty();
                }
                if (textureMode && ImGui.isItemHovered()) {
                    var tex = workspaceTexture(opt[1]);
                    if (tex != null) {
                        ImGui.beginTooltip();
                        float[] size = EditorTexturePreview.fit(tex, 192f * metricScale, 192f * metricScale);
                        ImGui.image(tex.glId(), size[0], size[1]);
                        ImGui.textDisabled(tex.width() + " x " + tex.height());
                        ImGui.endTooltip();
                    }
                }
                if (sel && ImGui.isWindowAppearing()) ImGui.setScrollHereY();
            }
            ImGui.endCombo();
        } else if (ddKey.equals(heldPopupFieldKey)) {
            heldPopupFieldKey = null;
        }
        boolean focused = ImGui.isItemActive() || ImGui.isItemHovered();
        ImGui.popStyleColor(12);
        ImGui.popStyleVar(6);
        strokeFieldChrome(dl, x, y, x + w, y + FIELD_H, blockCol, focused);
        if (ImGui.isItemHovered() || ImGui.isItemActive()) anyWidgetHovered = true;
        ImGui.popID();
    }

    private void drawSoundDropdown(VBlock b, VisualBlockDef.Dropdown d, float x, float y, float w,
                                   float comboX, float comboW, String ddKey, String label, String value,
                                   int blockCol, float[] darkTint) {
        var dl = ImGui.getWindowDrawList();
        ImGui.setCursorScreenPos(comboX, y);
        pushFieldFrameStyle(FIELD_H * 0.5f);
        pushFieldTintColors(blockCol);
        if (ImGui.button(label + "##sound", Math.max(40f * metricScale, comboW), FIELD_H)) {
            ImGui.openPopup("vs-sound-pick");
        }
        boolean focused = ImGui.isItemActive() || ImGui.isItemHovered();
        popFieldTintColors();
        ImGui.popStyleVar(2);
        strokeFieldChrome(dl, x, y, x + w, y + FIELD_H, blockCol, focused);
        if (ImGui.isItemHovered() || ImGui.isItemActive()) anyWidgetHovered = true;
        if (ImGui.beginPopup("vs-sound-pick")) {
            heldPopupFieldKey = ddKey;
            anyWidgetHovered = true;
            renderSoundPopupList(value, picked -> {
                pushUndo();
                b.fields.put(d.name(), picked);
                dirty = true;
                markLayoutDirty();
            });
            ImGui.endPopup();
        } else if (ddKey.equals(heldPopupFieldKey)) {
            heldPopupFieldKey = null;
        }
    }

    private void renderSoundPopupList(String current, Consumer<String> onPick) {
        EditorSoundPicker.renderList(current, soundSearch, onPick);
    }

    /** Dark background with a light wash of the block colour — for dropdowns. */
    private static float[] dropdownTint(int blockCol) {
        float[] bc = colF(blockCol);
        return new float[]{
                0.10f + bc[0] * 0.22f,
                0.11f + bc[1] * 0.22f,
                0.14f + bc[2] * 0.22f
        };
    }

    /** Options list; dynamic kinds are resolved from the workspace asset index. */
    private List<String[]> optionsFor(VBlock b, VisualBlockDef.Dropdown d) {
        if (d.dynamicKind() == null) return d.options();
        List<String[]> out = new ArrayList<>();
        switch (d.dynamicKind()) {
            case "model" -> {
                for (String s : EditorAssetIndex.models()) out.add(new String[]{s, s});
            }
            case "texture" -> {
                for (String s : EditorAssetIndex.textures()) {
                    out.add(new String[]{registryShortLabel(s), s});
                }
            }
            case "animfile" -> {
                for (String s : EditorAssetIndex.animations()) {
                    out.add(new String[]{EditorAssetIndex.animationFileLabel(s), s});
                }
            }
            case "anim" -> {
                String animFile = resolveAnimFileForBlock(b);
                if (animFile.isBlank()) {
                    out.add(new String[]{EditorI18n.t("(укажите НИП / префаб — клипы из его .animation.json)"), ""});
                } else {
                    List<String> clips = EditorAssetIndex.animationClips(animFile);
                    if (clips.isEmpty()) {
                        out.add(new String[]{EditorI18n.t("(нет клипов в файле)"), ""});
                    } else {
                        for (String clip : clips) out.add(new String[]{clip, clip});
                    }
                }
            }
            case "npc" -> {
                for (NpcPickerEntry e : listNpcPickerEntries()) {
                    out.add(new String[]{e.label, e.value});
                }
                if (out.isEmpty()) out.add(new String[]{EditorI18n.t("(нет НИП / переменных)"), ""});
            }
            case "hitbox" -> {
                for (NpcPickerEntry e : listHitboxPickerEntries()) {
                    out.add(new String[]{e.label, e.value});
                }
                if (out.isEmpty()) out.add(new String[]{EditorI18n.t("(нет хитбоксов сцены)"), ""});
            }
            case "player" -> {
                out.add(new String[]{EditorI18n.t("первый игрок"), "getFirstPlayer()"});
                out.add(new String[]{EditorI18n.t("любой"), "any"});
            }
            case "prefab" -> {
                for (String id : listScenePrefabIds()) out.add(new String[]{id, id});
                if (out.isEmpty()) out.add(new String[]{EditorI18n.t("(нет префабов)"), ""});
            }
            case "dimension" -> {
                for (String s : List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")) {
                    out.add(new String[]{s, s});
                }
            }
            case "sound" -> {
                out.add(new String[]{EditorI18n.t("(не выбран)"), ""});
                for (String s : EditorAssetIndex.customSounds()) {
                    out.add(new String[]{EditorAssetIndex.soundLabel(s), s});
                }
                for (String s : EditorAssetIndex.sounds()) {
                    if (EditorAssetIndex.customSounds().contains(s)) continue;
                    out.add(new String[]{s, s});
                }
            }
            default -> {}
        }
        return out;
    }

    private record NpcPickerEntry(String label, String value) {}

    /**
     * Animation file for {@code dropdown_anim}: follows the NPC target on this block —
     * field, socket ({@code var_get} / {@code npc_ref} / scene node / prefab spawn), create-npc,
     * open scene hierarchy, or NPC-rooted scene prefab.
     */
    private String resolveAnimFileForBlock(VBlock b) {
        if (b == null) return "";
        VisualBlockDef selfDef = b.def();
        if (selfDef != null && "npc_create".equals(selfDef.rawId)) {
            String onCreate = b.fields.getOrDefault("animation", "").trim();
            if (!onCreate.isEmpty()) return onCreate.replace('\\', '/');
        }
        String onBlock = b.fields.getOrDefault("animation", "").trim();
        if (!onBlock.isEmpty()) return onBlock.replace('\\', '/');

        String childId = b.socketChild.get("npc");
        if (childId != null) {
            String fromSocket = resolveAnimFileFromValueBlock(blocks.get(childId), new HashSet<>());
            if (fromSocket != null && !fromSocket.isBlank()) return fromSocket.replace('\\', '/');
        }

        String npcId = b.fields.getOrDefault("npc", "").trim();
        if (!npcId.isEmpty()) {
            String fromLookup = lookupAnimFileByNpcKey(npcId);
            if (fromLookup != null && !fromLookup.isBlank()) return fromLookup.replace('\\', '/');
        }
        return "";
    }

    /** Walk a value block plugged into an NPC socket and find the .animation.json path. */
    private String resolveAnimFileFromValueBlock(VBlock child, Set<String> visiting) {
        if (child == null) return "";
        if (!visiting.add(child.id)) return "";
        VisualBlockDef def = child.def();
        if (def == null) return "";
        String rid = def.rawId == null ? "" : def.rawId;

        if ("npc_create".equals(rid)) {
            String anim = child.fields.getOrDefault("animation", "").trim();
            if (!anim.isEmpty()) return anim;
            String id = child.fields.getOrDefault("id", "").trim();
            return lookupAnimFileByNpcKey(id);
        }
        if ("npc_ref".equals(rid)) {
            // May itself be socketed
            String nested = child.socketChild.get("npc");
            if (nested != null) {
                String fromNested = resolveAnimFileFromValueBlock(blocks.get(nested), visiting);
                if (fromNested != null && !fromNested.isBlank()) return fromNested;
            }
            return lookupAnimFileByNpcKey(child.fields.getOrDefault("npc", "").trim());
        }
        if ("var_get".equals(rid)) {
            return resolveVariableAnimFile(child.fields.getOrDefault("name", "").trim(), visiting);
        }
        if ("scene_get_node".equals(rid)) {
            return animFileFromSceneRef(child.fields.getOrDefault("path", "").trim());
        }
        if ("scene_find_node".equals(rid)) {
            return animFileFromSceneRef(child.fields.getOrDefault("name", "").trim());
        }
        if ("scene_find_node_by_id".equals(rid)) {
            return animFileFromSceneRef(child.fields.getOrDefault("id", "").trim());
        }
        if ("prefab_spawn".equals(rid) || "prefab_spawn_stmt".equals(rid)) {
            String prefab = child.fields.getOrDefault("prefab", "").trim();
            String nestedPrefab = child.socketChild.get("prefab");
            if (nestedPrefab != null) {
                String fromNested = resolveAnimFileFromValueBlock(blocks.get(nestedPrefab), visiting);
                if (fromNested != null && !fromNested.isBlank()) return fromNested;
            }
            return animFileFromPrefab(prefab);
        }

        // Generic: follow common value sockets / literal fields
        for (String sock : List.of("value", "npc", "entity", "target", "object")) {
            String nested = child.socketChild.get(sock);
            if (nested != null) {
                String fromNested = resolveAnimFileFromValueBlock(blocks.get(nested), visiting);
                if (fromNested != null && !fromNested.isBlank()) return fromNested;
            }
        }
        for (String key : List.of("value", "text", "str", "id", "name", "npc")) {
            String lit = child.fields.getOrDefault(key, "").trim();
            if (lit.isEmpty()) continue;
            String hit = lookupAnimFileByNpcKey(lit);
            if (hit != null && !hit.isBlank()) return hit;
        }
        return "";
    }

    private String resolveVariableAnimFile(String varName, Set<String> visiting) {
        if (varName == null || varName.isBlank()) return "";
        // Latest assignment wins (scan in document order)
        String found = "";
        for (VBlock block : blocks.values()) {
            VisualBlockDef def = block.def();
            if (def == null) continue;
            if (!"var_declare".equals(def.rawId) && !"var_set".equals(def.rawId)) continue;
            String name = block.fields.getOrDefault("name", "").trim();
            if (!varName.equals(name)) continue;
            String valueChild = block.socketChild.get("value");
            if (valueChild == null) continue;
            String anim = resolveAnimFileFromValueBlock(blocks.get(valueChild), visiting);
            if (anim != null && !anim.isBlank()) found = anim;
        }
        if (!found.isBlank()) return found;
        return lookupAnimFileByNpcKey(varName);
    }

    private String lookupAnimFileByNpcKey(String key) {
        if (key == null || key.isBlank()) return "";
        Map<String, String> map = npcAnimFileLookup();
        String direct = map.get(key);
        if (direct != null && !direct.isBlank()) return direct;
        String lower = key.toLowerCase(Locale.ROOT);
        for (var e : map.entrySet()) {
            if (e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).equals(lower)
                    && e.getValue() != null && !e.getValue().isBlank()) {
                return e.getValue();
            }
        }
        return animFileFromSceneRef(key);
    }

    /** Id / display name / prefab id → animation file (create npc + open scene + NPC prefabs). */
    private Map<String, String> npcAnimFileLookup() {
        Map<String, String> map = new LinkedHashMap<>();
        for (VBlock block : blocks.values()) {
            VisualBlockDef def = block.def();
            if (def == null || !"npc_create".equals(def.rawId)) continue;
            String id = block.fields.getOrDefault("id", "").trim();
            String anim = block.fields.getOrDefault("animation", "").trim();
            if (!id.isEmpty() && !anim.isEmpty()) map.put(id, anim);
        }
        SceneDocument doc = SceneEditorSession.document();
        if (doc != null && doc.root != null) {
            collectSceneNpcAnimFiles(doc.root, map);
        }
        Path gameDir = SceneEditorSession.gameDir();
        if (gameDir != null) {
            for (String prefabId : listScenePrefabIds()) {
                String anim = animFileFromPrefab(prefabId);
                if (anim != null && !anim.isBlank()) {
                    map.putIfAbsent(prefabId, anim);
                }
            }
        }
        return map;
    }

    private void collectSceneNpcAnimFiles(SceneNode node, Map<String, String> map) {
        if (node == null) return;
        if (node.type == SceneNodeType.NPC || node.type == SceneNodeType.MODEL) {
            String anim = node.animation == null ? "" : node.animation.trim();
            if (!anim.isEmpty()) {
                if (node.id != null && !node.id.isBlank()) map.putIfAbsent(node.id, anim);
                if (node.name != null && !node.name.isBlank()) map.putIfAbsent(node.name, anim);
            }
        }
        if (node.children != null) {
            for (SceneNode child : node.children) collectSceneNpcAnimFiles(child, map);
        }
    }

    private String animFileFromSceneRef(String pathOrName) {
        if (pathOrName == null || pathOrName.isBlank()) return "";
        SceneDocument doc = SceneEditorSession.document();
        if (doc == null) return "";
        SceneNode node = ScenePathResolver.findNode(doc, pathOrName.trim());
        if (node == null) return "";
        SceneNode npc = findFirstNpcOrModel(node);
        if (npc == null || npc.animation == null) return "";
        return npc.animation.trim();
    }

    private String animFileFromPrefab(String prefabId) {
        if (prefabId == null || prefabId.isBlank()) return "";
        Path gameDir = SceneEditorSession.gameDir();
        if (gameDir == null) return "";
        try {
            SceneNode root = org.zonarstudio.spraute_engine.scene.PrefabRepository.load(gameDir, prefabId);
            SceneNode npc = findFirstNpcOrModel(root);
            if (npc == null || npc.animation == null) return "";
            return npc.animation.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static SceneNode findFirstNpcOrModel(SceneNode node) {
        if (node == null) return null;
        if (node.type == SceneNodeType.NPC || node.type == SceneNodeType.MODEL) return node;
        if (node.children != null) {
            for (SceneNode child : node.children) {
                SceneNode found = findFirstNpcOrModel(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** NPC picker: create-npc ids, scene NPC ids, NPC-rooted prefabs, vars bound to those. */
    private List<NpcPickerEntry> listNpcPickerEntries() {
        LinkedHashMap<String, String> valueToLabel = new LinkedHashMap<>();

        for (VBlock block : blocks.values()) {
            VisualBlockDef def = block.def();
            if (def == null || !"npc_create".equals(def.rawId)) continue;
            String id = block.fields.getOrDefault("id", "").trim();
            if (!id.isEmpty()) valueToLabel.putIfAbsent(id, EditorI18n.t("создать: ") + id);
        }

        SceneDocument doc = SceneEditorSession.document();
        if (doc != null && doc.root != null) {
            collectSceneNpcPicker(doc.root, valueToLabel);
        }

        for (String prefabId : listScenePrefabIds()) {
            if (!animFileFromPrefab(prefabId).isBlank()) {
                valueToLabel.putIfAbsent(prefabId, EditorI18n.t("префаб: ") + prefabId);
            }
        }

        for (VBlock block : blocks.values()) {
            VisualBlockDef def = block.def();
            if (def == null) continue;
            if (!"var_declare".equals(def.rawId) && !"var_set".equals(def.rawId)) continue;
            String name = block.fields.getOrDefault("name", "").trim();
            if (name.isEmpty()) continue;
            String valueChild = block.socketChild.get("value");
            if (valueChild == null) continue;
            String anim = resolveAnimFileFromValueBlock(blocks.get(valueChild), new HashSet<>());
            if (anim != null && !anim.isBlank()) {
                valueToLabel.put(name, EditorI18n.t("перем: ") + name);
            }
        }

        List<NpcPickerEntry> out = new ArrayList<>();
        List<String> keys = new ArrayList<>(valueToLabel.keySet());
        keys.sort(String::compareToIgnoreCase);
        for (String key : keys) out.add(new NpcPickerEntry(valueToLabel.get(key), key));
        return out;
    }

    /**
     * HITBOX picker: create-hitbox ids, scene HITBOX node ids, optional triggerId aliases.
     * Value is always an id string that {@code on/await hitboxTouch} already matches.
     */
    private List<NpcPickerEntry> listHitboxPickerEntries() {
        LinkedHashMap<String, String> valueToLabel = new LinkedHashMap<>();

        for (VBlock block : blocks.values()) {
            VisualBlockDef def = block.def();
            if (def == null || !"hitbox_create".equals(def.rawId)) continue;
            String id = block.fields.getOrDefault("id", "").trim();
            if (!id.isEmpty()) valueToLabel.putIfAbsent(id, EditorI18n.t("создать: ") + id);
            String triggerId = block.fields.getOrDefault("triggerId", "").trim();
            if (!triggerId.isEmpty()) {
                valueToLabel.putIfAbsent(triggerId, EditorI18n.t("триггер: ") + triggerId);
            }
        }

        SceneDocument doc = SceneEditorSession.document();
        if (doc != null && doc.root != null) {
            collectSceneHitboxPicker(doc.root, valueToLabel);
        }

        List<NpcPickerEntry> out = new ArrayList<>();
        List<String> keys = new ArrayList<>(valueToLabel.keySet());
        keys.sort(String::compareToIgnoreCase);
        for (String key : keys) out.add(new NpcPickerEntry(valueToLabel.get(key), key));
        return out;
    }

    private void collectSceneHitboxPicker(SceneNode node, Map<String, String> valueToLabel) {
        if (node == null) return;
        if (node.type == SceneNodeType.HITBOX && node.id != null && !node.id.isBlank()) {
            String label;
            if (node.name != null && !node.name.isBlank()) {
                label = EditorI18n.t("сцена: ") + node.name + " [" + node.id + "]";
            } else {
                label = EditorI18n.t("сцена: ") + node.id;
            }
            valueToLabel.putIfAbsent(node.id, label);
            String triggerId = node.hitboxTriggerId == null ? "" : node.hitboxTriggerId.trim();
            if (!triggerId.isEmpty() && !triggerId.equals(node.id) && !triggerId.equals(node.name)) {
                valueToLabel.putIfAbsent(triggerId, EditorI18n.t("триггер: ") + triggerId
                        + " [" + (node.name == null || node.name.isBlank() ? node.id : node.name) + "]");
            }
        }
        if (node.children != null) {
            for (SceneNode child : node.children) collectSceneHitboxPicker(child, valueToLabel);
        }
    }

    /** Scene NPCs/models: dropdown value is always {@link SceneNode#id} (names break .spr codegen). */
    private void collectSceneNpcPicker(SceneNode node, Map<String, String> valueToLabel) {
        if (node == null) return;
        if (node.type == SceneNodeType.NPC || node.type == SceneNodeType.MODEL) {
            if (node.id != null && !node.id.isBlank()) {
                String label;
                if (node.name != null && !node.name.isBlank()) {
                    label = EditorI18n.t("сцена: ") + node.name + " [" + node.id + "]";
                } else {
                    label = EditorI18n.t("сцена: ") + node.id;
                }
                valueToLabel.putIfAbsent(node.id, label);
            }
        }
        if (node.children != null) {
            for (SceneNode child : node.children) collectSceneNpcPicker(child, valueToLabel);
        }
    }

    private List<String> listNpcIds() {
        List<String> ids = new ArrayList<>();
        for (NpcPickerEntry e : listNpcPickerEntries()) ids.add(e.value);
        return ids;
    }

    /** Saved scene prefabs from {@code spraute_engine/prefabs/*.prefab.json}. */
    private List<String> listScenePrefabIds() {
        try {
            Path gameDir = SceneEditorSession.gameDir();
            if (gameDir == null) gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
            return new ArrayList<>(org.zonarstudio.spraute_engine.scene.PrefabRepository.listIds(gameDir));
        } catch (Exception e) {
            return List.of();
        }
    }

    private void drawCheckboxWidget(VBlock b, VisualBlockDef.Checkbox cb, float x, float y) {
        float size = CHECKBOX_SZ;
        float boxY = y + (FIELD_H - size) * 0.5f;
        boolean checked = "TRUE".equals(b.fields.getOrDefault(cb.name(), cb.defaultChecked() ? "TRUE" : "FALSE"));
        int blockCol = hexColor(b.def().colorHex);
        int fill = darken(blockCol, 0.36f);
        int border = darken(blockCol, 0.26f);
        float round = 3.5f * metricScale;
        var dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x, boxY, x + size, boxY + size, fill, round);
        dl.addRect(x, boxY, x + size, boxY + size, border, round, 0, Math.max(1f, 1.1f * metricScale));
        if (checked) {
            // Compact white tick — not the oversized ImGui checkbox.
            float pad = Math.max(2.5f, 3f * metricScale);
            int tick = rgba(255, 255, 255, 245);
            float thick = Math.max(1.4f, 1.7f * metricScale);
            dl.addLine(x + pad, boxY + size * 0.52f,
                    x + size * 0.42f, boxY + size - pad, tick, thick);
            dl.addLine(x + size * 0.40f, boxY + size - pad,
                    x + size - pad, boxY + pad * 1.15f, tick, thick);
        }
        if (previewMode) return;

        ImGui.setCursorScreenPos(x, boxY);
        ImGui.pushID(b.id + ":cb:" + cb.name());
        if (ImGui.invisibleButton("##cb", size, size)) {
            pushUndo();
            b.fields.put(cb.name(), checked ? "FALSE" : "TRUE");
            dirty = true;
        }
        if (ImGui.isItemHovered()) {
            anyWidgetHovered = true;
            dl.addRect(x, boxY, x + size, boxY + size, rgba(255, 255, 255, 80), round, 0, 1.2f);
        }
        if (ImGui.isItemActive()) anyWidgetHovered = true;
        ImGui.popID();
    }

    private void drawSocketToggle(VBlock b, String fieldName, float x, float y, boolean active) {
        float size = TOGGLE_SZ;
        float round = 6f * metricScale;
        var dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x, y, x + size, y + size, rgba(0, 0, 0, active ? 128 : 56), round);
        var icon = EditorIcons.get("arrow-left-right");
        if (icon != null) {
            float pad = Math.max(1f, 1.5f * metricScale);
            dl.addImage(icon.glId(), x + pad, y + pad, x + size - pad, y + size - pad,
                    0f, 0f, 1f, 1f, rgba(255, 255, 255, active ? 255 : 210));
        }
        if (previewMode || !mouseInRect(x - 4f, y - 4f, size + 8f, size + 8f)) return;

        ImGui.setCursorScreenPos(x, y);
        ImGui.pushID(b.id + ":tg:" + fieldName);
        if (ImGui.invisibleButton("##tg", size, size)) {
            toggleSocket(b, fieldName);
        }
        if (ImGui.isItemHovered()) {
            anyWidgetHovered = true;
            dl.addRect(x, y, x + size, y + size, rgba(255, 255, 255, 90), round, 0, 1.2f);
            ImGui.setTooltip(active ? EditorI18n.t("Вернуть обычное поле") : EditorI18n.t("Превратить поле в сокет для блока"));
        }
        if (ImGui.isItemActive()) anyWidgetHovered = true;
        ImGui.popID();
    }

    private void toggleSocket(VBlock b, String fieldName) {
        pushUndo();
        markLayoutDirty();
        String bufKey = b.id + ":" + fieldName;
        if (b.socketOn.contains(fieldName)) {
            b.socketOn.remove(fieldName);
            String childId = b.socketChild.remove(fieldName);
            if (childId != null) {
                VBlock child = blocks.get(childId);
                if (child != null) {
                    child.x = (b.sx + b.w + 14f - canvasOriginX) / zoom - panX;
                    child.y = (b.sy - canvasOriginY) / zoom - panY;
                    child.isChild = false;
                }
            }
            ImString buf = fieldBufs.get(bufKey);
            if (buf != null) buf.set(b.fields.getOrDefault(fieldName, ""));
            fieldEditWidth.remove(bufKey);
        } else {
            ImString buf = fieldBufs.get(bufKey);
            if (buf != null) b.fields.put(fieldName, buf.get());
            b.socketOn.add(fieldName);
            if (bufKey.equals(activeFieldKey)) activeFieldKey = null;
            fieldEditWidth.remove(bufKey);
        }
        dirty = true;
    }

    // ==================== Input ====================

    private void handleCanvasInput(ImVec2 origin, float cw, float chh, ImDrawList dl) {
        float mx = ImGui.getMousePosX(), my = ImGui.getMousePosY();
        boolean inCanvas = mx >= origin.x && mx <= origin.x + cw && my >= origin.y && my <= origin.y + chh;
        // Block canvas drag when a field is focused/hovered — otherwise clicks steal focus every other frame.
        boolean widgetBusy = anyWidgetHovered || coordsMenuOpen || ImGui.isAnyItemActive() || ImGui.isAnyItemHovered();

        if (inCanvas && ImGui.isMouseDragging(2)) {
            panX += ImGui.getIO().getMouseDeltaX() / zoom;
            panY += ImGui.getIO().getMouseDeltaY() / zoom;
        }

        // Ctrl + wheel: zoom towards the mouse cursor
        float wheel = ImGui.getIO().getMouseWheel();
        if (inCanvas && ImGui.getIO().getKeyCtrl() && wheel != 0f) {
            float oldZoom = zoom;
            zoom = Math.max(0.4f, Math.min(2.5f, zoom * (wheel > 0 ? 1.1f : 1f / 1.1f)));
            if (zoom != oldZoom) {
                panX = (mx - origin.x) / zoom - ((mx - origin.x) / oldZoom - panX);
                panY = (my - origin.y) / zoom - ((my - origin.y) / oldZoom - panY);
                // Field edit widths are in screen px — drop so they remeasure at the new zoom.
                fieldEditWidth.clear();
            }
        }

        boolean ctrlOrShift = isCtrlDown() || isShiftDown();

        if (inCanvas && ImGui.isMouseClicked(0) && !widgetBusy && paletteDragType == null) {
            VBlock hit = hitTest(mx, my);
            if (hit != null) {
                if (ctrlOrShift) {
                    // Toggle multi-select — never detach / never wipe the rest of the selection.
                    if (!selection.remove(hit.id)) selection.add(hit.id);
                } else if (!selection.contains(hit.id)) {
                    selection.clear();
                    selection.add(hit.id);
                }
                if (selection.contains(hit.id)) {
                    draggingBlocks = true;
                    dragPrimary = hit.id;
                    dragLastX = mx;
                    dragLastY = my;
                    // Pull a nested/stack block out only on a plain single-select drag.
                    dragPendingDetach = !ctrlOrShift && hit.isChild && selection.size() == 1;
                } else {
                    draggingBlocks = false;
                    dragPrimary = null;
                    dragPendingDetach = false;
                }
            } else {
                banding = true;
                bandX0 = mx;
                bandY0 = my;
                if (!ctrlOrShift) selection.clear();
            }
        }

        if (draggingBlocks) {
            if (ImGui.isMouseDown(0)) {
                float dx = mx - dragLastX, dy = my - dragLastY;
                dragLastX = mx;
                dragLastY = my;
                if ((Math.abs(dx) + Math.abs(dy) > 3f)) {
                    if (!dragHistoryPushed) {
                        pushUndo();
                        dragHistoryPushed = true;
                    }
                    if (dragPendingDetach) {
                        VBlock primary = dragPrimary == null ? null : blocks.get(dragPrimary);
                        if (primary != null && primary.isChild) detach(primary);
                        dragPendingDetach = false;
                    } else if (selection.size() > 1) {
                        // Free selected nested blocks that aren't kept by another selected parent.
                        for (String id : List.copyOf(selection)) {
                            VBlock b = blocks.get(id);
                            if (b != null && b.isChild && !hasSelectedReferencer(b)) detach(b);
                        }
                    }
                }
                for (String id : selection) {
                    VBlock b = blocks.get(id);
                    if (b != null && !b.isChild) {
                        b.x += dx / zoom;
                        b.y += dy / zoom;
                    }
                }
                if (dx != 0 || dy != 0) dirty = true;
                drawSnapHint(dl);
            } else {
                dragPendingDetach = false;
                trySnap();
                draggingBlocks = false;
                dragPrimary = null;
                dragHistoryPushed = false;
            }
        }

        if (banding) {
            if (ImGui.isMouseDown(0)) {
                float x0 = Math.min(bandX0, mx), y0 = Math.min(bandY0, my);
                float x1 = Math.max(bandX0, mx), y1 = Math.max(bandY0, my);
                dl.addRectFilled(x0, y0, x1, y1, COL_BAND);
                dl.addRect(x0, y0, x1, y1, COL_BAND_LINE);
            } else {
                float x0 = Math.min(bandX0, mx), y0 = Math.min(bandY0, my);
                float x1 = Math.max(bandX0, mx), y1 = Math.max(bandY0, my);
                for (VBlock b : blocks.values()) {
                    if (b.sx < x1 && b.sx + b.w > x0 && b.sy < y1 && b.sy + b.h > y0) {
                        selection.add(b.id);
                    }
                }
                banding = false;
            }
        }

        // Right click: canvas block menu — skip when a field coords menu is open / field is busy.
        if (inCanvas && ImGui.isMouseClicked(1) && !widgetBusy && !coordsMenuOpen && paletteDragType == null) {
            VBlock hit = hitTest(mx, my);
            ctxBlockId = hit == null ? null : hit.id;
            ctxCanvasX = (mx - origin.x) / zoom - panX;
            ctxCanvasY = (my - origin.y) / zoom - panY;
            if (hit != null && !selection.contains(hit.id)) {
                selection.clear();
                selection.add(hit.id);
            }
            openCanvasCtx = true;
        }
    }

    /** Dark rounded theme for context menus (canvas + palette). */
    static void pushMenuStyle() {
        ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding, 10f);
        ImGui.pushStyleVar(ImGuiStyleVar.PopupBorderSize, 1f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 10f, 8f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8f, 7f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 8f, 5f);
        ImGui.pushStyleColor(ImGuiCol.PopupBg, 0.086f, 0.094f, 0.117f, 0.99f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.36f, 0.42f, 0.55f, 0.55f);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.23f, 0.36f, 0.62f, 0.85f);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, 0.27f, 0.42f, 0.72f, 1f);
        ImGui.pushStyleColor(ImGuiCol.Separator, 1f, 1f, 1f, 0.10f);
        ImGui.pushStyleColor(ImGuiCol.Text, 0.93f, 0.94f, 0.97f, 1f);
    }

    static void popMenuStyle() {
        ImGui.popStyleColor(6);
        ImGui.popStyleVar(5);
    }

    private void openFieldContextMenu(VBlock b, String fieldName, String key) {
        coordsMenuOpen = true;
        coordsMenuJustOpened = true;
        coordsMenuFieldKey = key;
        coordsMenuBlockId = b.id;
        coordsMenuFieldName = fieldName;
        coordsMenuX = ImGui.getMousePosX();
        coordsMenuY = ImGui.getMousePosY();
        coordsMenuSelStart = fieldSelStart != fieldSelEnd ? fieldSelStart : fieldSelStickyStart;
        coordsMenuSelEnd = fieldSelStart != fieldSelEnd ? fieldSelEnd : fieldSelStickyEnd;
        coordsMenuNumeric = isNumericField(b, fieldName);
        anyWidgetHovered = true;
    }

    private boolean isNumericField(VBlock b, String fieldName) {
        VisualBlockDef.Field f = findField(b, fieldName);
        return f != null && "number".equals(f.type());
    }

    private static VisualBlockDef.Field findField(VBlock b, String fieldName) {
        VisualBlockDef def = b == null ? null : b.def();
        if (def == null || fieldName == null) return null;
        for (VisualBlockDef.BodyItem it : def.body) {
            if (it.kind != VisualBlockDef.ItemKind.ROW) continue;
            for (VisualBlockDef.Token t : it.tokens) {
                if (t instanceof VisualBlockDef.Field f && fieldName.equals(f.name())) return f;
            }
        }
        return null;
    }

    /** Stays open while moving onto menu items (floating window, not ImGui popup stack). */
    private void renderFieldCoordsMenu() {
        if (!coordsMenuOpen) return;
        VBlock b = coordsMenuBlockId == null ? null : blocks.get(coordsMenuBlockId);
        if (b == null || coordsMenuFieldName == null) {
            coordsMenuOpen = false;
            return;
        }
        String fieldName = coordsMenuFieldName;
        String fieldKey = coordsMenuFieldKey;
        ImString buf = fieldBufs.computeIfAbsent(fieldKey, k -> {
            ImString s = new ImString(2048);
            s.set(b.fields.getOrDefault(fieldName, ""));
            return s;
        });

        ImGui.setNextWindowPos(coordsMenuX, coordsMenuY, imgui.flag.ImGuiCond.Appearing);
        ImGui.setNextWindowFocus();
        pushMenuStyle();
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 10f);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.086f, 0.094f, 0.117f, 0.99f);
        int flags = ImGuiWindowFlags.NoTitleBar
                | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.AlwaysAutoResize
                | ImGuiWindowFlags.NoCollapse
                | ImGuiWindowFlags.NoDocking;
        boolean open = ImGui.begin("##vs-field-coords-menu", flags);
        anyWidgetHovered = true;
        boolean stay = open;
        if (open) {
            String live = b.fields.getOrDefault(fieldName, buf.get());
            boolean hasSel = coordsMenuSelStart != coordsMenuSelEnd;
            boolean showEffects = !coordsMenuNumeric && hasSel;
            boolean showCoords = coordsMenuNumeric || !hasSel;
            if (showEffects) {
                ImGui.textDisabled(EditorI18n.t("Эффекты текста"));
                ImGui.separator();
                String tag = UiTextMarkupMenu.drawItems(live, coordsMenuSelStart, coordsMenuSelEnd);
                if (tag != null) {
                    applyFieldMarkup(fieldKey, buf, b, fieldName, tag);
                    stay = false;
                }
            }
            if (showEffects && showCoords) ImGui.separator();
            if (showCoords) {
            String cx = org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.formatViewCoord('x');
            String cy = org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.formatViewCoord('y');
            String cz = org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.formatViewCoord('z');
            boolean hasBlock = org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.hasViewTargetBlock();
            String bx = org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.formatViewBlockCoord('x');
            String by = org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.formatViewBlockCoord('y');
            String bz = org.zonarstudio.spraute_engine.client.editor.SprauteEditorState.formatViewBlockCoord('z');

            ImGui.textDisabled(EditorI18n.t("Камера: ") + cx + ", " + cy + ", " + cz);
            ImGui.separator();
            if (ImGui.selectable(EditorI18n.t("Камера — X"))) {
                pasteFieldCoord(fieldKey, buf, b, fieldName, cx);
                stay = false;
            }
            if (ImGui.selectable(EditorI18n.t("Камера — Y"))) {
                pasteFieldCoord(fieldKey, buf, b, fieldName, cy);
                stay = false;
            }
            if (ImGui.selectable(EditorI18n.t("Камера — Z"))) {
                pasteFieldCoord(fieldKey, buf, b, fieldName, cz);
                stay = false;
            }

            ImGui.separator();
            ImGui.textDisabled(EditorI18n.t("Блок: ") + (hasBlock ? bx + ", " + by + ", " + bz : "—"));
            ImGui.separator();
            if (hasBlock && ImGui.selectable(EditorI18n.t("Блок — X"))) {
                pasteFieldCoord(fieldKey, buf, b, fieldName, bx);
                stay = false;
            }
            if (hasBlock && ImGui.selectable(EditorI18n.t("Блок — Y"))) {
                pasteFieldCoord(fieldKey, buf, b, fieldName, by);
                stay = false;
            }
            if (hasBlock && ImGui.selectable(EditorI18n.t("Блок — Z"))) {
                pasteFieldCoord(fieldKey, buf, b, fieldName, bz);
                stay = false;
            }
            if (!hasBlock) {
                ImGui.beginDisabled();
                ImGui.selectable(EditorI18n.t("Блок — X"));
                ImGui.selectable(EditorI18n.t("Блок — Y"));
                ImGui.selectable(EditorI18n.t("Блок — Z"));
                ImGui.endDisabled();
            }
            }

            // Click outside closes (not mouse-move). Skip the opening RMB frame.
            if (!coordsMenuJustOpened) {
                if (ImGui.isMouseClicked(0) && !ImGui.isWindowHovered(
                        imgui.flag.ImGuiHoveredFlags.AllowWhenBlockedByActiveItem
                                | imgui.flag.ImGuiHoveredFlags.ChildWindows)) {
                    stay = false;
                }
                if (ImGui.isMouseClicked(1) && !ImGui.isWindowHovered()) {
                    stay = false;
                }
                if (ImGui.isKeyPressed(ImGuiKey.Escape)) stay = false;
            }
            ImGui.end();
        }
        ImGui.popStyleColor();
        ImGui.popStyleVar();
        popMenuStyle();
        coordsMenuJustOpened = false;
        coordsMenuOpen = stay;
        if (!stay) {
            coordsMenuFieldKey = null;
            coordsMenuBlockId = null;
            coordsMenuFieldName = null;
        }
    }

    private void applyFieldMarkup(String fieldKey, ImString buf, VBlock b, String fieldName, String tag) {
        String text = b.fields.getOrDefault(fieldName, buf.get());
        if (text == null) text = "";
        UiTextMarkup.Caret caret = UiTextMarkup.toggle(text, coordsMenuSelStart, coordsMenuSelEnd, tag);
        noteFieldEdit(fieldKey);
        buf.set(caret.text());
        b.fields.put(fieldName, caret.text());
        fieldMarkupPending = caret.text();
        fieldMarkupPendingCursor = caret.cursor();
        coordsMenuSelStart = caret.selStart();
        coordsMenuSelEnd = caret.selEnd();
        fieldSelStart = caret.selStart();
        fieldSelEnd = caret.selEnd();
    }

    private void pasteFieldCoord(String fieldKey, ImString buf, VBlock b, String fieldName, String value) {
        noteFieldEdit(fieldKey);
        buf.set(value);
        b.fields.put(fieldName, value);
    }

    private void renderCanvasContextMenu() {
        if (openCanvasCtx) {
            openCanvasCtx = false;
            ImGui.openPopup("##vs-canvas-ctx");
        }
        pushMenuStyle();
        if (!ImGui.beginPopup("##vs-canvas-ctx")) {
            popMenuStyle();
            return;
        }
        anyWidgetHovered = true;
        VBlock b = ctxBlockId == null ? null : blocks.get(ctxBlockId);
        if (b != null) {
            VisualBlockDef def = b.def();
            // header with the block's colour dot
            int col = hexColor(def.colorHex);
            var dl = ImGui.getWindowDrawList();
            float hx = ImGui.getCursorScreenPosX();
            float hy = ImGui.getCursorScreenPosY() + ImGui.getTextLineHeight() * 0.5f;
            dl.addCircleFilled(hx + 5f, hy, 5f, col);
            ImGui.indent(16f);
            ImGui.textDisabled(def.displayLabel());
            ImGui.unindent(16f);
            ImGui.separator();
            if (ImGui.menuItem(EditorI18n.t("Отменить"), "Ctrl+Z", false, canUndo())) {
                undo();
            }
            if (ImGui.menuItem(EditorI18n.t("Повторить"), "Ctrl+Y", false, canRedo())) {
                redo();
            }
            ImGui.separator();
            if (ImGui.menuItem(EditorI18n.t("Копировать"), "Ctrl+C", false, !selection.isEmpty())) {
                copySelection();
            }
            if (ImGui.menuItem(EditorI18n.t("Вырезать"), "Ctrl+X", false, !selection.isEmpty())) {
                cutSelection();
            }
            if (ImGui.menuItem(EditorI18n.t("Вставить"), "Ctrl+V", false, hasBlockClipboard())) {
                pasteClipboard();
            }
            if (ImGui.menuItem(EditorI18n.t("Дублировать"), "Ctrl+D", false, !selection.isEmpty())) {
                duplicateSelection();
            }
            if (b.isChild && ImGui.menuItem(EditorI18n.t("Отсоединить"))) {
                pushUndo();
                detach(b);
            }
            boolean fav = favorites().contains(b.type);
            if (ImGui.menuItem(fav ? EditorI18n.t("Убрать из избранного") : EditorI18n.t("В избранное"))) {
                toggleFavorite(b.type);
            }
            ImGui.separator();
            ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.55f, 0.5f, 1f);
            boolean del = ImGui.menuItem(EditorI18n.t("Удалить"), "Del");
            ImGui.popStyleColor();
            if (del) deleteSelection();
        } else {
            if (ImGui.menuItem(EditorI18n.t("Отменить"), "Ctrl+Z", false, canUndo())) {
                undo();
            }
            if (ImGui.menuItem(EditorI18n.t("Повторить"), "Ctrl+Y", false, canRedo())) {
                redo();
            }
            ImGui.separator();
            if (ImGui.menuItem(EditorI18n.t("Вставить"), "Ctrl+V", false, hasBlockClipboard())) {
                pasteClipboard();
            }
            if (ImGui.menuItem(EditorI18n.t("Выделить всё"), "Ctrl+A", false, !blocks.isEmpty())) {
                selectAllBlocks();
            }
            ImGui.separator();
            if (ImGui.menuItem(EditorI18n.t("Создать метку"))) {
                createMarkAt(ctxCanvasX, ctxCanvasY, null);
            }
            ImGui.separator();
            if (ImGui.menuItem(EditorI18n.t("Масштаб холста 100%"))) zoom = 1f;
            if (ImGui.menuItem(EditorI18n.t("Масштаб палитры по умолчанию"))) {
                paletteZoom = PALETTE_ZOOM_DEFAULT;
            }
            if (ImGui.menuItem(EditorI18n.t("В начало координат"))) { panX = 40f; panY = 40f; }
            ImGui.separator();
            ImGui.textDisabled(blocks.size() + EditorI18n.t(" блоков · холст ")
                    + Math.round(zoom * 100f) + "%"
                    + EditorI18n.t(" · палитра ") + Math.round(paletteZoom * 100f) + "%");
        }
        ImGui.endPopup();
        popMenuStyle();
    }

    /** Place a built-in mark block at canvas coords (name editable on the block). */
    private void createMarkAt(float cx, float cy, String name) {
        VisualBlockDef def = VisualBlockRegistry.find(MARK_BLOCK_TYPE);
        if (def == null) {
            VisualBlockRegistry.ensureLoaded();
            def = VisualBlockRegistry.find(MARK_BLOCK_TYPE);
        }
        if (def == null) {
            status = EditorI18n.t("Блок метки не найден");
            return;
        }
        String markName = name == null || name.isBlank() ? EditorI18n.t("Метка") : name.trim();
        pushUndo();
        VBlock b = addBlock(def, cx, cy);
        b.fields.put("name", markName);
        selection.clear();
        selection.add(b.id);
        dirty = true;
        status = EditorI18n.t("Метка создана: ") + markName;
    }

    private static boolean hasBlockClipboard() {
        return !BLOCK_CLIPBOARD_ROOTS.isEmpty();
    }

    /** Selected blocks that are not nested under another selected block (avoid double-copy). */
    private List<String> selectionRoots() {
        Set<String> sel = new LinkedHashSet<>(selection);
        Set<String> nestedInSelection = new HashSet<>();
        for (String id : sel) {
            VBlock root = blocks.get(id);
            if (root == null) continue;
            collectDescendantIds(root, nestedInSelection);
            nestedInSelection.remove(id);
        }
        List<String> roots = new ArrayList<>();
        for (String id : sel) {
            if (!nestedInSelection.contains(id) && blocks.containsKey(id)) roots.add(id);
        }
        return roots;
    }

    private void collectDescendantIds(VBlock b, Set<String> out) {
        if (b.next != null) {
            out.add(b.next);
            VBlock n = blocks.get(b.next);
            if (n != null) collectDescendantIds(n, out);
        }
        for (String cid : b.socketChild.values()) {
            out.add(cid);
            VBlock c = blocks.get(cid);
            if (c != null) collectDescendantIds(c, out);
        }
        for (String cid : b.stmtChild.values()) {
            out.add(cid);
            VBlock c = blocks.get(cid);
            if (c != null) collectDescendantIds(c, out);
        }
    }

    /**
     * Nested content only: value sockets + statement mouths (and their internal stacks).
     * Does not follow {@link VBlock#next} of {@code b} itself — that is the outer stack below.
     */
    private void collectNestedIds(VBlock b, Set<String> out) {
        if (b == null) return;
        for (String cid : b.socketChild.values()) {
            if (cid == null || !out.add(cid)) continue;
            VBlock c = blocks.get(cid);
            if (c != null) collectNestedIds(c, out);
        }
        for (String firstId : b.stmtChild.values()) {
            String cur = firstId;
            while (cur != null && out.add(cur)) {
                VBlock c = blocks.get(cur);
                if (c == null) break;
                collectNestedIds(c, out);
                cur = c.next;
            }
        }
    }

    private void selectAllBlocks() {
        selection.clear();
        Set<String> referenced = referencedIds();
        for (VBlock b : blocks.values()) {
            if (!referenced.contains(b.id)) selection.add(b.id);
        }
        // Also allow selecting every block if there are only nested ones somehow
        if (selection.isEmpty()) selection.addAll(blocks.keySet());
    }

    private void copySelection() {
        List<String> roots = selectionRoots();
        if (roots.isEmpty()) return;
        Set<String> selectedIds = new LinkedHashSet<>(selection);
        BLOCK_CLIPBOARD.clear();
        BLOCK_CLIPBOARD_ROOTS.clear();
        for (String id : roots) {
            VBlock src = blocks.get(id);
            if (src == null) continue;
            // Follow next only for blocks that are also selected — single top block ≠ whole stack.
            VBlock copy = deepClone(src, blocks, BLOCK_CLIPBOARD, selectedIds);
            copy.x = src.isChild ? (src.sx - canvasOriginX) / zoom - panX : src.x;
            copy.y = src.isChild ? (src.sy - canvasOriginY) / zoom - panY : src.y;
            copy.isChild = false;
            BLOCK_CLIPBOARD_ROOTS.add(copy.id);
        }
        status = EditorI18n.t("Скопировано: ") + BLOCK_CLIPBOARD_ROOTS.size();
    }

    private void cutSelection() {
        if (selection.isEmpty()) return;
        copySelection();
        deleteSelection();
        status = EditorI18n.t("Вырезано");
    }

    private void pasteClipboard() {
        if (!hasBlockClipboard()) return;
        pushUndo();
        markLayoutDirty();
        selection.clear();
        float ox = 24f;
        float oy = 24f;
        for (String rootId : BLOCK_CLIPBOARD_ROOTS) {
            VBlock src = BLOCK_CLIPBOARD.get(rootId);
            if (src == null) continue;
            VBlock pasted = deepClone(src, BLOCK_CLIPBOARD, blocks, null);
            pasted.x = src.x + ox;
            pasted.y = src.y + oy;
            pasted.isChild = false;
            selection.add(pasted.id);
            // Next paste stacks further so repeated Ctrl+V doesn't overlap.
            src.x += ox;
            src.y += oy;
        }
        dirty = true;
        status = EditorI18n.t("Вставлено: ") + selection.size();
    }

    /**
     * Duplicate selected blocks. A single selected block copies only itself (not the stack below).
     * Several selected blocks keep {@code next} links between those that are selected.
     */
    private void duplicateSelection() {
        List<String> ids = selectionRoots();
        if (ids.isEmpty()) return;
        pushUndo();
        Set<String> selectedIds = new LinkedHashSet<>(selection);
        selection.clear();
        for (String id : ids) {
            VBlock src = blocks.get(id);
            if (src == null) continue;
            VBlock copy = deepClone(src, blocks, blocks, selectedIds);
            copy.x = (src.isChild ? (src.sx - canvasOriginX) / zoom - panX : src.x) + 24f;
            copy.y = (src.isChild ? (src.sy - canvasOriginY) / zoom - panY : src.y) + 24f;
            copy.isChild = false;
            selection.add(copy.id);
        }
        dirty = true;
        markLayoutDirty();
    }

    /**
     * Clone {@code src} (and nested sockets/statements) from {@code sourceMap} into {@code destMap}
     * with fresh ids.
     *
     * @param nextOnlyIfSelected when non-null, follow {@code next} only if that block id is in the set
     *                           (multi-select copy). {@code null} = follow the whole chain (clipboard paste).
     */
    private VBlock deepClone(VBlock src, Map<String, VBlock> sourceMap, Map<String, VBlock> destMap,
                             Set<String> nextOnlyIfSelected) {
        VBlock c = new VBlock();
        c.id = "b" + (idCounter++);
        c.type = src.type;
        c.fields.putAll(src.fields);
        c.socketOn.addAll(src.socketOn);
        for (var e : src.socketChild.entrySet()) {
            VBlock child = sourceMap.get(e.getValue());
            if (child != null) c.socketChild.put(e.getKey(), deepClone(child, sourceMap, destMap, null).id);
        }
        for (var e : src.stmtChild.entrySet()) {
            VBlock child = sourceMap.get(e.getValue());
            // Statement mouths keep their full inner chain (part of the block body).
            if (child != null) c.stmtChild.put(e.getKey(), deepClone(child, sourceMap, destMap, null).id);
        }
        if (src.next != null) {
            boolean includeNext = nextOnlyIfSelected == null || nextOnlyIfSelected.contains(src.next);
            if (includeNext) {
                VBlock next = sourceMap.get(src.next);
                if (next != null) c.next = deepClone(next, sourceMap, destMap, nextOnlyIfSelected).id;
            }
        }
        destMap.put(c.id, c);
        return c;
    }

    private VBlock copyDeep(VBlock src) {
        return deepClone(src, blocks, blocks, null);
    }

    private VBlock hitTest(float mx, float my) {
        VBlock best = null;
        // Prefer last painted (visually on top). Nested children are drawn after
        // their parent, so they still win over wrappers without the old
        // smallest-area rule that preferred an underneath block when stacks overlap.
        for (VBlock b : blocks.values()) {
            if (mx >= b.sx && mx <= b.sx + b.w && my >= b.sy && my <= b.sy + b.h) {
                if (best == null || b.zOrder > best.zOrder) best = b;
            }
        }
        return best;
    }

    private void detach(VBlock child) {
        for (VBlock b : blocks.values()) {
            if (child.id.equals(b.next)) b.next = null;
            b.socketChild.values().removeIf(child.id::equals);
            b.stmtChild.values().removeIf(child.id::equals);
        }
        child.x = (child.sx - canvasOriginX) / zoom - panX;
        child.y = (child.sy - canvasOriginY) / zoom - panY;
        child.isChild = false;
        dirty = true;
        markLayoutDirty();
    }

    private static boolean isCtrlDown() {
        return ImGui.getIO().getKeyCtrl()
                || ImGui.isKeyDown(ImGuiKey.LeftCtrl)
                || ImGui.isKeyDown(ImGuiKey.RightCtrl)
                || ImGui.isKeyDown(ImGuiKey.LeftSuper)
                || ImGui.isKeyDown(ImGuiKey.RightSuper);
    }

    private static boolean isShiftDown() {
        return ImGui.getIO().getKeyShift()
                || ImGui.isKeyDown(ImGuiKey.LeftShift)
                || ImGui.isKeyDown(ImGuiKey.RightShift);
    }

    /** True if another selected block currently owns {@code child} via next/socket/stmt. */
    private boolean hasSelectedReferencer(VBlock child) {
        if (child == null) return false;
        for (VBlock b : blocks.values()) {
            if (!selection.contains(b.id)) continue;
            if (child.id.equals(b.next)) return true;
            if (b.socketChild.containsValue(child.id)) return true;
            if (b.stmtChild.containsValue(child.id)) return true;
        }
        return false;
    }

    // ==================== Snapping ====================

    private void drawSnapHint(ImDrawList dl) {
        VBlock primary = dragPrimary == null ? null : blocks.get(dragPrimary);
        if (primary == null) return;
        VisualBlockDef d = primary.def();
        if (d.shape != VisualBlockDef.Shape.VALUE) {
            Object[] target = findStatementSnapTarget(primary);
            if (target != null) {
                float[] line = (float[]) target[2];
                dl.addLine(line[0], line[1], line[2], line[3], COL_SELECT, 3f);
            }
        } else {
            Object[] socket = findSocketSnapTarget(primary);
            if (socket != null) {
                float[] r = (float[]) socket[2];
                dl.addRect(r[0] - 2f, r[1] - 2f, r[2] + 2f, r[3] + 2f, COL_SELECT, 6f, 0, 2f);
            }
        }
    }

    private void trySnap() {
        VBlock primary = dragPrimary == null ? null : blocks.get(dragPrimary);
        if (primary == null || selection.size() != 1) return;
        VisualBlockDef d = primary.def();
        if (d.shape != VisualBlockDef.Shape.VALUE) {
            Object[] target = findStatementSnapTarget(primary);
            if (target == null) return;
            if (target[0] instanceof VBlock after && "next".equals(target[1])) {
                String oldNext = after.next;
                after.next = primary.id;
                reattachTail(primary, oldNext);
            } else if (target[0] instanceof VBlock before && "before".equals(target[1])) {
                attachBefore(primary, before);
            } else if (target[0] instanceof Object[] stmtOwner) {
                VBlock owner = (VBlock) stmtOwner[0];
                String stmtName = (String) stmtOwner[1];
                String oldFirst = owner.stmtChild.get(stmtName);
                owner.stmtChild.put(stmtName, primary.id);
                reattachTail(primary, oldFirst);
            }
            dirty = true;
            markLayoutDirty();
        } else {
            Object[] socket = findSocketSnapTarget(primary);
            if (socket != null) {
                VBlock owner = (VBlock) socket[0];
                String field = (String) socket[1];
                String oldId = owner.socketChild.get(field);
                if (oldId != null && !oldId.equals(primary.id)) {
                    VBlock old = blocks.get(oldId);
                    if (old != null) {
                        old.x = (primary.sx - canvasOriginX) / zoom - panX - 24f;
                        old.y = (primary.sy - canvasOriginY) / zoom - panY;
                        detach(old);
                    } else {
                        owner.socketChild.remove(field);
                    }
                }
                owner.socketChild.put(field, primary.id);
                dirty = true;
                markLayoutDirty();
            }
        }
    }

    /** Insert {@code dragged} immediately above {@code target} in its chain / statement mouth. */
    private void attachBefore(VBlock dragged, VBlock target) {
        VBlock predViaNext = null;
        VBlock stmtOwner = null;
        String stmtName = null;
        for (VBlock b : blocks.values()) {
            if (target.id.equals(b.next)) predViaNext = b;
            for (var e : b.stmtChild.entrySet()) {
                if (target.id.equals(e.getValue())) {
                    stmtOwner = b;
                    stmtName = e.getKey();
                }
            }
        }
        if (predViaNext != null) {
            predViaNext.next = dragged.id;
        } else if (stmtOwner != null && stmtName != null) {
            stmtOwner.stmtChild.put(stmtName, dragged.id);
        } else {
            // Target was a free root — dragged becomes the new root at the same canvas spot.
            dragged.x = target.x;
            dragged.y = target.y;
        }
        reattachTail(dragged, target.id);
    }

    private void reattachTail(VBlock chainHead, String oldNext) {
        if (oldNext == null || oldNext.equals(chainHead.id)) return;
        VBlock tail = chainHead;
        int guard = 0;
        while (tail.next != null && blocks.get(tail.next) != null && guard++ < 1000) tail = blocks.get(tail.next);
        if (!tail.id.equals(oldNext)) tail.next = oldNext;
    }

    /**
     * @return {targetBlock, "next", lineRect} — attach after target;
     *         {targetBlock, "before", lineRect} — attach above target;
     *         {{owner, stmtName}, "stmt", lineRect} — attach as first block of a statement area.
     */
    private Object[] findStatementSnapTarget(VBlock dragged) {
        Object[] best = null;
        float bestDist = SNAP_DIST;
        for (VBlock b : blocks.values()) {
            if (b == dragged || selection.contains(b.id)) continue;
            VisualBlockDef d = b.def();
            if (d.shape == VisualBlockDef.Shape.VALUE) continue;
            if (isInChain(dragged, b)) continue;
            // 1) under the block
            {
                float dx = Math.abs(b.sx - dragged.sx);
                float dy = Math.abs((b.sy + b.h) - dragged.sy);
                float dist = dx * 0.4f + dy;
                if (dx < 60f && dist < bestDist) {
                    bestDist = dist;
                    float yLine = b.sy + b.h - 2f;
                    best = new Object[]{b, "next", new float[]{b.sx, yLine, b.sx + b.w, yLine}};
                }
            }
            // 2) above the block (insert before)
            {
                float dx = Math.abs(b.sx - dragged.sx);
                float dy = Math.abs(b.sy - (dragged.sy + dragged.h));
                float dist = dx * 0.4f + dy;
                if (dx < 60f && dist < bestDist) {
                    bestDist = dist;
                    float yLine = b.sy;
                    best = new Object[]{b, "before", new float[]{b.sx, yLine, b.sx + b.w, yLine}};
                }
            }
            // 3) into empty statement areas
            if (b.rows != null) {
                float y = b.sy + 4f;
                for (Row row : b.rows) {
                    if (row.stmt == null) { y += row.h; continue; }
                    float innerY = y + row.h;
                    if (b.stmtChild.get(row.stmt.name()) == null || isDescendantOfDragged(dragged, b.stmtChild.get(row.stmt.name()))) {
                        float dx = Math.abs((b.sx + STMT_INDENT) - dragged.sx);
                        float dy = Math.abs(innerY + MOUTH_PAD_TOP - dragged.sy);
                        float dist = dx * 0.4f + dy;
                        if (dx < 60f && dist < bestDist) {
                            bestDist = dist;
                            best = new Object[]{new Object[]{b, row.stmt.name()}, "stmt",
                                    new float[]{b.sx + STMT_INDENT, innerY + MOUTH_PAD_TOP, b.sx + b.w, innerY + MOUTH_PAD_TOP}};
                        }
                    }
                    y = innerY + row.innerH + STMT_FOOT;
                }
            }
        }
        return best;
    }

    private boolean isDescendantOfDragged(VBlock dragged, String id) {
        return dragged.id.equals(id);
    }

    /** target reachable from dragged's chain (защита от циклов). */
    private boolean isInChain(VBlock start, VBlock target) {
        VBlock cur = start;
        int guard = 0;
        while (cur != null && guard++ < 1000) {
            if (cur == target) return true;
            cur = cur.next == null ? null : blocks.get(cur.next);
        }
        return false;
    }

    /** @return {ownerBlock, inputName, screenRect[4]} ближайшего свободного сокета. */
    private Object[] findSocketSnapTarget(VBlock dragged) {
        Object[] best = null;
        float bestDist = SNAP_DIST * 2f;
        for (VBlock b : blocks.values()) {
            if (b == dragged || selection.contains(b.id) || b.rows == null) continue;
            VisualBlockDef d = b.def();
            float y = b.sy + (d.shape == VisualBlockDef.Shape.VALUE ? 3f : 4f);
            for (Row row : b.rows) {
                if (row.stmt != null) {
                    y += row.h + row.innerH + STMT_FOOT;
                    continue;
                }
                float tx = b.sx + PAD;
                for (VisualBlockDef.Token t : row.tokens) {
                    float tw = tokenWidth(b, t);
                    String socketName = socketNameOf(b, t);
                    if (socketName != null) {
                        float sockX = tx;
                        if (t instanceof VisualBlockDef.ValueInput vi && !vi.label().isBlank()) {
                            sockX += textW(pt(b, vi.label())) + GAP;
                        }
                        float sy0 = y + (row.h - FIELD_H) * 0.5f;
                        float hitX0 = sockX, hitY0 = sy0, hitX1, hitY1;
                        String existingId = b.socketChild.get(socketName);
                        if (existingId != null && !existingId.equals(dragged.id)) {
                            VBlock existing = blocks.get(existingId);
                            if (existing != null) {
                                hitX0 = existing.sx;
                                hitY0 = existing.sy;
                                hitX1 = existing.sx + existing.w;
                                hitY1 = existing.sy + existing.h;
                            } else {
                                hitX1 = sockX + EMPTY_SOCKET_W;
                                hitY1 = sy0 + FIELD_H;
                            }
                        } else if (existingId != null) {
                            continue;
                        } else {
                            hitX1 = sockX + EMPTY_SOCKET_W;
                            hitY1 = sy0 + FIELD_H;
                        }
                        float cx = (hitX0 + hitX1) * 0.5f;
                        float cy = (hitY0 + hitY1) * 0.5f;
                        float dcx = dragged.sx - cx;
                        float dcy = dragged.sy + dragged.h * 0.5f - cy;
                        float dist = (float) Math.sqrt(dcx * dcx + dcy * dcy);
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = new Object[]{b, socketName, new float[]{hitX0, hitY0, hitX1, hitY1}};
                        }
                    }
                    tx += tw + GAP;
                }
                y += row.h;
            }
        }
        return best;
    }

    private void deleteSelection() {
        Set<String> toDelete = new LinkedHashSet<>();
        for (String id : selection) {
            VBlock root = blocks.get(id);
            if (root == null) continue;
            toDelete.add(id);
            collectNestedIds(root, toDelete);
        }
        if (toDelete.isEmpty()) return;
        pushUndo();
        markLayoutDirty();
        // Snapshot next-links before removals so stack splicing still works.
        Map<String, String> nextOf = new HashMap<>();
        for (String id : toDelete) {
            VBlock b = blocks.get(id);
            if (b != null) nextOf.put(id, b.next);
        }
        for (String id : toDelete) {
            VBlock removed = blocks.remove(id);
            if (removed == null) continue;
            String spliceNext = nextOf.get(id);
            for (VBlock b : blocks.values()) {
                if (id.equals(b.next)) {
                    // Keep the outer stack below, unless that block is also being deleted.
                    b.next = spliceNext != null && !toDelete.contains(spliceNext) ? spliceNext : null;
                }
                b.socketChild.values().removeIf(id::equals);
                b.stmtChild.values().removeIf(id::equals);
            }
            fieldBufs.keySet().removeIf(k -> k.startsWith(id + ":"));
            fieldEditWidth.keySet().removeIf(k -> k.startsWith(id + ":"));
        }
        selection.clear();
        dirty = true;
    }

    // ==================== Utils ====================

    private static final Map<String, Integer> hexCache = new HashMap<>();

    private static int hexColor(String hex) {
        return hexCache.computeIfAbsent(hex == null ? "#555555" : hex, h -> {
            try {
                String s = h.startsWith("#") ? h.substring(1) : h;
                int v = Integer.parseInt(s, 16);
                return rgba((v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF, 255);
            } catch (Exception e) {
                return rgba(85, 85, 85, 255);
            }
        });
    }

    private static int rgba(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int withAlpha(int abgr, int a) {
        return (abgr & 0x00FFFFFF) | (a << 24);
    }

    private static int brighten(int abgr, float k) {
        int r = Math.min(255, (int) ((abgr & 0xFF) * k));
        int g = Math.min(255, (int) (((abgr >> 8) & 0xFF) * k));
        int b = Math.min(255, (int) (((abgr >> 16) & 0xFF) * k));
        return (abgr & 0xFF000000) | (b << 16) | (g << 8) | r;
    }

    private static int darken(int abgr, float k) {
        return brighten(abgr, k);
    }
}
