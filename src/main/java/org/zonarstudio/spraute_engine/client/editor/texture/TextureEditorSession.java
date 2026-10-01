package org.zonarstudio.spraute_engine.client.editor.texture;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;
import org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient;
import org.zonarstudio.spraute_engine.client.editor.icons.EditorIcons;
import org.zonarstudio.spraute_engine.client.editor.texture.SelectionMask.Mode;
import org.zonarstudio.spraute_engine.client.editor.texture.TextureDocument.IntRect;
import org.zonarstudio.spraute_engine.client.editor.texture.TextureDocument.PixelClip;

import com.mojang.blaze3d.platform.NativeImage;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-game pixel-art editor: tools, layers, palette, undo, save.
 */
public final class TextureEditorSession {
    public enum Tool { PENCIL, ERASER, FILL, EYEDROPPER, SELECT, WAND, SHAPE, GRADIENT }

    public enum ShapeKind { LINE, RECT, ELLIPSE, TRIANGLE }

    private static final int UNDO_CAP = 64;
    private static final int[] SIZE_PRESETS = {8, 16, 32, 64, 128, 256};
    private static final float[][] PALETTE = {
            {0.11f, 0.11f, 0.13f, 1}, {1, 1, 1, 1},
            {0.55f, 0.55f, 0.55f, 1}, {0.25f, 0.25f, 0.25f, 1},
            {0.69f, 0.18f, 0.15f, 1}, {0.98f, 0.50f, 0.11f, 1},
            {1f, 0.85f, 0.24f, 1}, {0.50f, 0.78f, 0.12f, 1},
            {0.37f, 0.49f, 0.09f, 1}, {0.09f, 0.61f, 0.61f, 1},
            {0.23f, 0.70f, 0.85f, 1}, {0.24f, 0.27f, 0.67f, 1},
            {0.54f, 0.20f, 0.72f, 1}, {0.78f, 0.31f, 0.74f, 1},
            {0.95f, 0.55f, 0.67f, 1}, {0.51f, 0.33f, 0.20f, 1},
            {0.62f, 0.43f, 0.26f, 1}, {0.91f, 0.76f, 0.61f, 1},
            {0.78f, 0.53f, 0.26f, 1}, {0.55f, 0.33f, 0.14f, 1},
    };

    private static final Map<String, TextureEditorSession> OPEN = new LinkedHashMap<>();
    private static TextureEditorSession hotkeyOwner;

    private Path path;
    private TextureDocument doc;
    private String error;
    private boolean dirty;
    private boolean zoomInit;
    private float zoom = 8f;
    private boolean showGrid = true;
    private Tool tool = Tool.PENCIL;
    private ShapeKind shapeKind = ShapeKind.RECT;
    private int brush = 1;
    private boolean shapeFill;
    private final float[] color = {1f, 1f, 1f, 1f};
    private final float[] colorB = {0.14f, 0.10f, 0.22f, 1f};
    private int gradSteps = 6;
    private float hueShift = 0.72f;
    private float ditherAmt = 0.75f;
    private PixelArtGradient.Dither dither = PixelArtGradient.Dither.BAYER4;
    private PixelArtGradient.Shape gradShape = PixelArtGradient.Shape.LINEAR;
    private boolean editShadow;
    private final ImString hexBuf = new ImString(12);
    private final List<float[]> extraPalette = new ArrayList<>();
    private final ImString layerName = new ImString(64);
    private final ArrayDeque<TextureDocument> undo = new ArrayDeque<>();
    private final ArrayDeque<TextureDocument> redo = new ArrayDeque<>();

    private SelectionMask mask;
    private Mode selectMode = Mode.REPLACE;
    private Mode selectStrokeMode = Mode.REPLACE;
    private int wandTolerance;
    private boolean wandContiguous = true;
    private boolean wandSampleAll;
    private boolean selecting;
    private int selX0, selY0;
    private IntRect marquee;
    private boolean moving;
    private boolean movePending;
    private boolean moveCopy;
    private int moveGrabX, moveGrabY, moveDx, moveDy;
    private int[] floatPixels;
    private boolean[] floatMask;
    private int floatX, floatY, floatW, floatH;

    private boolean stroking;
    private int lastPx = -1, lastPy = -1;
    private int lineX0, lineY0;
    private boolean shaping;
    private boolean grading;
    private int gradX0, gradY0;
    private int hoverX = -1, hoverY = -1;
    private float panX, panY;
    private boolean panning;

    private DynamicTexture preview;
    private NativeImage previewPixels;
    private int[] compositeBuf = new int[0];
    private boolean previewDirty = true;

    private static int[] clipPixels;
    private static boolean[] clipMask;
    private static int clipW, clipH;

    private TextureEditorSession(Path path, TextureDocument doc) {
        this.path = path;
        this.doc = doc;
        this.mask = new SelectionMask(doc.width, doc.height);
        OPEN.put(key(path), this);
        syncLayerName();
        syncHex();
        rebuildPreview(true);
    }

    public static TextureEditorSession open(Path path) {
        TextureEditorSession existing = OPEN.get(key(path));
        if (existing != null) return existing;
        try {
            return new TextureEditorSession(path, TextureDocument.load(path));
        } catch (Exception e) {
            TextureEditorSession s = new TextureEditorSession(path, TextureDocument.blank(16, 16));
            s.error = e.getMessage();
            return s;
        }
    }

    public static int[] sizePresets() {
        return SIZE_PRESETS;
    }

    public static boolean claimsEditHotkeys() {
        return hotkeyOwner != null;
    }

    public static void clearHotkeyOwner() {
        hotkeyOwner = null;
    }

    public static boolean canUndoActive() {
        return hotkeyOwner != null && hotkeyOwner.canUndo();
    }

    public static boolean canRedoActive() {
        return hotkeyOwner != null && hotkeyOwner.canRedo();
    }

    public static void undoActive() {
        if (hotkeyOwner != null) hotkeyOwner.undo();
    }

    public static void redoActive() {
        if (hotkeyOwner != null) hotkeyOwner.redo();
    }

    /** Clears the pixel selection. Returns true if there was one. */
    public static boolean clearSelectionIfAny() {
        return hotkeyOwner != null && hotkeyOwner.clearSelection();
    }

    public static void saveAllDirty() {
        for (TextureEditorSession s : List.copyOf(OPEN.values())) {
            if (s.dirty) s.save();
        }
    }

    public static void disposeAll() {
        for (TextureEditorSession s : List.copyOf(OPEN.values())) {
            s.dispose();
        }
        OPEN.clear();
        hotkeyOwner = null;
    }

    public boolean isDirty() {
        return dirty;
    }

    public Path path() {
        return path;
    }

    public void setPath(Path path) {
        if (this.path != null) OPEN.remove(key(this.path));
        this.path = path;
        if (path != null) OPEN.put(key(path), this);
    }

    public void dispose() {
        if (path != null) OPEN.remove(key(path));
        if (hotkeyOwner == this) hotkeyOwner = null;
        if (preview != null) {
            preview.close();
            preview = null;
            previewPixels = null;
        }
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void undo() {
        if (undo.isEmpty() || doc == null) return;
        redo.addLast(doc.snapshot());
        if (redo.size() > UNDO_CAP) redo.removeFirst();
        doc.restore(undo.removeLast());
        resetMask();
        previewDirty = true;
        dirty = true;
        syncLayerName();
    }

    public void redo() {
        if (redo.isEmpty() || doc == null) return;
        undo.addLast(doc.snapshot());
        if (undo.size() > UNDO_CAP) undo.removeFirst();
        doc.restore(redo.removeLast());
        resetMask();
        previewDirty = true;
        dirty = true;
        syncLayerName();
    }

    public boolean save() {
        if (doc == null || path == null) return false;
        try {
            doc.save(path);
            RemoteProjectClient.pushIfRemote(path);
            Path side = TextureDocument.sidecar(path);
            RemoteProjectClient.pushIfRemote(side);
            org.zonarstudio.spraute_engine.client.assetsync.ClientAssetHotReload.afterLocalSave(path);
            dirty = false;
            error = null;
            return true;
        } catch (Exception e) {
            error = e.getMessage();
            return false;
        }
    }

    public void render() {
        boolean focused = ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows);
        if (focused) hotkeyOwner = this;
        else if (hotkeyOwner == this) hotkeyOwner = null;
        if (doc == null) {
            ImGui.textColored(1f, 0.45f, 0.4f, 1f, error == null
                    ? EditorI18n.t("Не удалось загрузить текстуру")
                    : error);
            return;
        }
        if (focused) handleHotkeys();
        renderToolbar();
        if (error != null && !error.isBlank()) {
            ImGui.textColored(1f, 0.45f, 0.4f, 1f, error);
        }
        float availW = Math.max(8f, ImGui.getContentRegionAvailX());
        float availH = Math.max(8f, ImGui.getContentRegionAvailY());
        float side = Math.min(372f, Math.max(336f, availW * 0.32f));
        float gap = 12f;
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 10f);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.086f, 0.094f, 0.118f, 1f);
        ImGui.beginChild("tex-canvas-wrap-" + path, availW - side - gap, availH, false,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        ImGui.popStyleColor();
        renderCanvas();
        ImGui.endChild();
        ImGui.sameLine(0f, gap);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.078f, 0.084f, 0.106f, 1f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 14f, 12f);
        ImGui.beginChild("tex-side-" + path, side, availH, true);
        renderColorPanel();
        sectionGap();
        renderPalette();
        sectionGap();
        renderLayers();
        ImGui.endChild();
        ImGui.popStyleVar(2);
        ImGui.popStyleColor();
    }

    private void handleHotkeys() {
        if (ImGui.getIO().getWantTextInput()) return;
        boolean ctrl = ImGui.getIO().getKeyCtrl();
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.S)) {
            save();
            return;
        }
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.C)) {
            copySelection();
            return;
        }
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.X)) {
            cutSelection();
            return;
        }
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.V)) {
            pasteClipboard();
            return;
        }
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.J)) {
            duplicateSelection();
            return;
        }
        if ((ctrl && ImGui.isKeyPressed(ImGuiKey.D)) || ImGui.isKeyPressed(ImGuiKey.Escape)) {
            clearSelection();
            return;
        }
        if (ImGui.isKeyPressed(ImGuiKey.Delete) || ImGui.isKeyPressed(ImGuiKey.Backspace)) {
            deleteSelection();
            return;
        }
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.E)) {
            if (ImGui.getIO().getKeyShift() && doc.canFlatten()) {
                pushUndo();
                doc.flattenVisible();
                syncLayerName();
                markDirty();
            } else if (doc.canMergeDown()) {
                pushUndo();
                doc.mergeDown();
                syncLayerName();
                markDirty();
            }
            return;
        }
        if (!ctrl) {
            if (ImGui.isKeyPressed(ImGuiKey.B)) tool = Tool.PENCIL;
            if (ImGui.isKeyPressed(ImGuiKey.E)) tool = Tool.ERASER;
            if (ImGui.isKeyPressed(ImGuiKey.G)) tool = Tool.FILL;
            if (ImGui.isKeyPressed(ImGuiKey.I)) tool = Tool.EYEDROPPER;
            if (ImGui.isKeyPressed(ImGuiKey.M)) tool = Tool.SELECT;
            if (ImGui.isKeyPressed(ImGuiKey.W)) tool = Tool.WAND;
            if (ImGui.isKeyPressed(ImGuiKey.L)) { tool = Tool.SHAPE; shapeKind = ShapeKind.LINE; }
            if (ImGui.isKeyPressed(ImGuiKey.U)) { tool = Tool.SHAPE; shapeKind = ShapeKind.RECT; }
            if (ImGui.isKeyPressed(ImGuiKey.O)) { tool = Tool.SHAPE; shapeKind = ShapeKind.ELLIPSE; }
            if (ImGui.isKeyPressed(ImGuiKey.T)) { tool = Tool.SHAPE; shapeKind = ShapeKind.TRIANGLE; }
            if (ImGui.isKeyPressed(ImGuiKey.R)) tool = Tool.GRADIENT;
        }
        if (!moving && hasSelection()) {
            int dx = 0;
            int dy = 0;
            if (ImGui.isKeyPressed(ImGuiKey.LeftArrow)) dx = -1;
            if (ImGui.isKeyPressed(ImGuiKey.RightArrow)) dx = 1;
            if (ImGui.isKeyPressed(ImGuiKey.UpArrow)) dy = -1;
            if (ImGui.isKeyPressed(ImGuiKey.DownArrow)) dy = 1;
            if (dx != 0 || dy != 0) nudgeSelection(dx, dy);
        }
    }

    private void renderToolbar() {
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 7f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 5f, 6f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 16f, 10f);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.078f, 0.084f, 0.106f, 1f);
        float barH = tool == Tool.GRADIENT ? 122f : 92f;
        ImGui.beginChild("tex-toolbar-" + path, 0f, barH, true);
        ImGui.popStyleColor();

        String title = path.getFileName().toString() + (dirty ? " *" : "");
        ImGui.textColored(0.90f, 0.92f, 0.96f, 1f, title);
        ImGui.sameLine();
        ImGui.textDisabled(doc.width + "×" + doc.height);
        if (hoverX >= 0) {
            ImGui.sameLine();
            ImGui.textDisabled("·  " + hoverX + ", " + hoverY);
        }
        ImGui.sameLine(0f, 14f);
        toolButton(Tool.PENCIL, "pencil-line", EditorI18n.t("Карандаш") + "  B");
        ImGui.sameLine();
        toolButton(Tool.ERASER, "eraser", EditorI18n.t("Ластик") + "  E");
        ImGui.sameLine();
        toolButton(Tool.FILL, "paint-bucket", EditorI18n.t("Заливка") + "  G");
        ImGui.sameLine();
        toolButton(Tool.EYEDROPPER, "pipette", EditorI18n.t("Пипетка") + "  I");
        ImGui.sameLine();
        toolButton(Tool.SELECT, "square-dashed",
                EditorI18n.t("Выделение") + "  M   ·  "
                        + EditorI18n.t("Shift — добавить") + "  ·  "
                        + EditorI18n.t("Alt — вычесть") + "  ·  "
                        + EditorI18n.t("Shift+Alt — пересечение") + "  ·  "
                        + EditorI18n.t("Снять выделение") + "  Esc / Ctrl+D   ·  "
                        + EditorI18n.t("Тяни внутри — перенести") + "  ·  "
                        + EditorI18n.t("Shift не двигает") + "  ·  "
                        + EditorI18n.t("Ctrl+тяни — копия"));
        ImGui.sameLine();
        toolButton(Tool.WAND, "wand",
                EditorI18n.t("Волшебная палочка") + "  W   ·  "
                        + EditorI18n.t("Выделяет похожий цвет") + "  ·  "
                        + EditorI18n.t("Shift — добавить") + "  ·  "
                        + EditorI18n.t("Alt — вычесть") + "  ·  "
                        + EditorI18n.t("Shift+Alt — пересечение") + "  ·  "
                        + EditorI18n.t("Тяни внутри — перенести"));
        ImGui.sameLine();
        toolButton(Tool.SHAPE, shapeIcon(),
                EditorI18n.t("Фигуры") + "  L / U / O / T   ·  "
                        + EditorI18n.t("Линия, прямоугольник, эллипс, треугольник") + "  ·  "
                        + EditorI18n.t("Shift — квадрат / круг / ровная линия"));
        ImGui.sameLine();
        toolButton(Tool.GRADIENT, "blend",
                EditorI18n.t("Градиент") + "  R   ·  " + EditorI18n.t("Shift — ровно по 0° / 45° / 90°"));
        ImGui.sameLine(0f, 12f);
        if (EditorIcons.iconToggleButton("grid-3x3", "tex-grid-" + path, 16f, EditorI18n.t("Сетка"), showGrid)) {
            showGrid = !showGrid;
        }
        ImGui.sameLine(0f, 10f);
        if (EditorIcons.iconButton("undo-2", "tex-undo-" + path, 16f, EditorI18n.t("Отменить") + "  Ctrl+Z")) {
            undo();
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("redo-2", "tex-redo-" + path, 16f, EditorI18n.t("Повторить") + "  Ctrl+Y")) {
            redo();
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("save", "tex-save-" + path, 16f, EditorI18n.t("Сохранить") + "  Ctrl+S")) {
            save();
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(118f);
        float[] z = {zoom};
        if (ImGui.sliderFloat("##tex-zoom", z, 1f, 32f, EditorI18n.t("Зум") + " %.0f×")) {
            zoom = z[0];
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        renderToolSettings();

        ImGui.endChild();
        ImGui.popStyleVar(3);
        ImGui.spacing();
    }

    private void renderToolSettings() {
        ImGui.textDisabled(EditorI18n.t("Инструмент"));
        ImGui.sameLine(0f, 10f);
        if (tool == Tool.SHAPE) {
            shapeKindButton(ShapeKind.LINE, "slash",
                    EditorI18n.t("Линия") + "  L   ·  " + EditorI18n.t("Shift — ровно по 0° / 45° / 90°"));
            ImGui.sameLine();
            shapeKindButton(ShapeKind.RECT, "square",
                    EditorI18n.t("Прямоугольник") + "  U   ·  " + EditorI18n.t("Shift — квадрат"));
            ImGui.sameLine();
            shapeKindButton(ShapeKind.ELLIPSE, "circle",
                    EditorI18n.t("Эллипс") + "  O   ·  " + EditorI18n.t("Shift — круг"));
            ImGui.sameLine();
            shapeKindButton(ShapeKind.TRIANGLE, "triangle",
                    EditorI18n.t("Треугольник") + "  T   ·  " + EditorI18n.t("Shift — равносторонний"));
            ImGui.sameLine(0f, 12f);
        }
        if (tool == Tool.PENCIL || tool == Tool.ERASER || tool == Tool.SHAPE || tool == Tool.FILL) {
            ImGui.setNextItemWidth(110f);
            int[] brushBuf = {brush};
            if (ImGui.sliderInt("##tex-brush", brushBuf, 1, 16, EditorI18n.t("Кисть") + " %d")) {
                brush = brushBuf[0];
            }
        }
        if (tool == Tool.SHAPE && shapeKind != ShapeKind.LINE) {
            ImGui.sameLine(0f, 10f);
            if (ImGui.checkbox(EditorI18n.t("Заливка") + "##tex-shape-fill", shapeFill)) {
                shapeFill = !shapeFill;
            }
        }
        if (isSelectTool()) {
            if (tool == Tool.PENCIL || tool == Tool.ERASER || tool == Tool.SHAPE || tool == Tool.FILL) {
                ImGui.sameLine(0f, 12f);
            }
            ImGui.textDisabled(EditorI18n.t("Режим выделения"));
            ImGui.sameLine(0f, 8f);
            modeButton(Mode.REPLACE, "square",
                    EditorI18n.t("Новое") + "   ·  " + EditorI18n.t("Заменяет текущее выделение"));
            ImGui.sameLine();
            modeButton(Mode.ADD, "plus",
                    EditorI18n.t("Добавить") + "   Shift");
            ImGui.sameLine();
            modeButton(Mode.SUBTRACT, "square-minus",
                    EditorI18n.t("Вычесть") + "   Alt");
            ImGui.sameLine();
            modeButton(Mode.INTERSECT, "squares-intersect",
                    EditorI18n.t("Пересечение") + "   Shift+Alt");
            if (tool == Tool.WAND) {
                ImGui.sameLine(0f, 14f);
                ImGui.setNextItemWidth(110f);
                int[] tol = {wandTolerance};
                if (ImGui.sliderInt("##tex-wand-tol", tol, 0, 255, EditorI18n.t("Допуск") + " %d")) {
                    wandTolerance = tol[0];
                }
                ImGui.sameLine();
                if (ImGui.checkbox(EditorI18n.t("Смежные") + "##tex-wand-contig", wandContiguous)) {
                    wandContiguous = !wandContiguous;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(EditorI18n.t("Только соседние пиксели. Выключи — все похожие на холсте."));
                }
                ImGui.sameLine();
                if (ImGui.checkbox(EditorI18n.t("Все слои") + "##tex-wand-all", wandSampleAll)) {
                    wandSampleAll = !wandSampleAll;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(EditorI18n.t("Брать цвет с видимой композиции, а не только с активного слоя."));
                }
            }
            if (hasSelection()) {
                ImGui.sameLine(0f, 14f);
                if (EditorIcons.iconButton("clipboard-copy", "tex-dup-" + path, 16f,
                        EditorI18n.t("Дублировать выделение") + "  Ctrl+J")) {
                    duplicateSelection();
                }
            }
        } else if (hasSelection()) {
            ImGui.sameLine(0f, 12f);
            if (EditorIcons.iconButton("clipboard-copy", "tex-dup-" + path, 16f,
                    EditorI18n.t("Дублировать выделение") + "  Ctrl+J")) {
                duplicateSelection();
            }
        }
        if (tool == Tool.GRADIENT) {
            ImGui.sameLine(0f, 8f);
            renderGradientSettings();
        }
    }

    private void renderColorPanel() {
        sectionLabel(EditorI18n.t("Цвет"));
        float chip = 38f;
        if (colorChip("##tex-fgchip", color, !editShadow || tool != Tool.GRADIENT, chip,
                tool == Tool.GRADIENT ? EditorI18n.t("Свет") : EditorI18n.t("Цвет"))) {
            editShadow = false;
            syncHex();
        }
        if (tool == Tool.GRADIENT) {
            ImGui.sameLine(0f, 8f);
            if (EditorIcons.iconButton("arrow-left-right", "tex-swap-" + path, 16f,
                    EditorI18n.t("Поменять свет и тень"))) {
                float r = color[0], g = color[1], b = color[2], a = color[3];
                System.arraycopy(colorB, 0, color, 0, 4);
                colorB[0] = r;
                colorB[1] = g;
                colorB[2] = b;
                colorB[3] = a;
                syncHex();
                if (grading) previewDirty = true;
            }
            ImGui.sameLine(0f, 8f);
            if (colorChip("##tex-bgchip", colorB, editShadow, chip,
                    EditorI18n.t("Тень") + "  " + EditorI18n.t("ПКМ — пипетка тени"))) {
                editShadow = true;
                syncHex();
            }
        }
        ImGui.sameLine(0f, 10f);
        ImGui.beginGroup();
        ImGui.textColored(0.62f, 0.66f, 0.74f, 1f, hexOf(activeColor()));
        ImGui.textDisabled(rgbaLabel(activeColor()));
        ImGui.endGroup();

        ImGui.spacing();
        ImGui.setNextItemWidth(Math.max(80f, ImGui.getContentRegionAvailX() - 8f));
        int flags = ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.AlphaPreview
                | ImGuiColorEditFlags.PickerHueBar | ImGuiColorEditFlags.NoSidePreview
                | ImGuiColorEditFlags.NoSmallPreview;
        if (ImGui.colorPicker4("##tex-picker", activeColor(), flags)) {
            if (grading) previewDirty = true;
            syncHex();
        }
        ImGui.setNextItemWidth(-1f);
        if (ImGui.inputText("##tex-hex", hexBuf)) {
            applyHex(hexBuf.get());
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(EditorI18n.t("HEX (#RRGGBB или #RRGGBBAA)"));
    }

    private void renderGradientSettings() {
        ImGui.setNextItemWidth(108f);
        int[] steps = {gradSteps};
        if (ImGui.sliderInt("##tex-gsteps", steps, 2, 12, EditorI18n.t("Ступени") + " %d")) {
            gradSteps = steps[0];
            if (grading) previewDirty = true;
        }
        ImGui.sameLine(0f, 8f);
        String ditherLabel = switch (dither) {
            case NONE -> EditorI18n.t("Без дизера");
            case BAYER2 -> "Bayer 2×2";
            case BAYER4 -> "Bayer 4×4";
            case BAYER8 -> "Bayer 8×8";
            case HATCH -> EditorI18n.t("Штриховка");
        };
        ImGui.setNextItemWidth(120f);
        if (ImGui.beginCombo("##tex-gdither", ditherLabel)) {
            if (ImGui.selectable(EditorI18n.t("Без дизера"), dither == PixelArtGradient.Dither.NONE)) {
                dither = PixelArtGradient.Dither.NONE;
                if (grading) previewDirty = true;
            }
            if (ImGui.selectable("Bayer 2×2", dither == PixelArtGradient.Dither.BAYER2)) {
                dither = PixelArtGradient.Dither.BAYER2;
                if (grading) previewDirty = true;
            }
            if (ImGui.selectable("Bayer 4×4", dither == PixelArtGradient.Dither.BAYER4)) {
                dither = PixelArtGradient.Dither.BAYER4;
                if (grading) previewDirty = true;
            }
            if (ImGui.selectable("Bayer 8×8", dither == PixelArtGradient.Dither.BAYER8)) {
                dither = PixelArtGradient.Dither.BAYER8;
                if (grading) previewDirty = true;
            }
            if (ImGui.selectable(EditorI18n.t("Штриховка"), dither == PixelArtGradient.Dither.HATCH)) {
                dither = PixelArtGradient.Dither.HATCH;
                if (grading) previewDirty = true;
            }
            ImGui.endCombo();
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(EditorI18n.t("Дизер между ступенями"));
        ImGui.sameLine(0f, 8f);
        ImGui.setNextItemWidth(100f);
        float[] dith = {ditherAmt * 100f};
        if (ImGui.sliderFloat("##tex-gdamt", dith, 0f, 100f, EditorI18n.t("Дизер") + " %.0f%%")) {
            ditherAmt = dith[0] / 100f;
            if (grading) previewDirty = true;
        }
        ImGui.sameLine(0f, 8f);
        ImGui.setNextItemWidth(108f);
        float[] hue = {hueShift * 100f};
        if (ImGui.sliderFloat("##tex-ghue", hue, 0f, 100f, EditorI18n.t("Шейдинг") + " %.0f%%")) {
            hueShift = hue[0] / 100f;
            if (grading) previewDirty = true;
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(EditorI18n.t("Сдвиг оттенка: тени в холодный, света в тёплый"));
        ImGui.sameLine(0f, 10f);
        if (ImGui.radioButton(EditorI18n.t("Линейный") + "##gsh", gradShape == PixelArtGradient.Shape.LINEAR)) {
            gradShape = PixelArtGradient.Shape.LINEAR;
            if (grading) previewDirty = true;
        }
        ImGui.sameLine();
        if (ImGui.radioButton(EditorI18n.t("Круговой") + "##gsh", gradShape == PixelArtGradient.Shape.RADIAL)) {
            gradShape = PixelArtGradient.Shape.RADIAL;
            if (grading) previewDirty = true;
        }
    }

    private void drawRampStrip() {
        int[] ramp = PixelArtGradient.ramp(currentColor(), shadowColor(), gradSteps, hueShift);
        float w = Math.max(80f, ImGui.getContentRegionAvailX());
        float h = 22f;
        ImVec2 p = ImGui.getCursorScreenPos();
        ImDrawList dl = ImGui.getWindowDrawList();
        float cell = w / ramp.length;
        drawChecker(dl, p.x, p.y, w, h);
        for (int i = 0; i < ramp.length; i++) {
            float[] c = new float[4];
            TextureDocument.unpackRgba(ramp[i], c);
            dl.addRectFilled(p.x + i * cell, p.y, p.x + (i + 1) * cell, p.y + h,
                    pack(c[0], c[1], c[2], c[3]));
        }
        dl.addRect(p.x, p.y, p.x + w, p.y + h, pack(1f, 1f, 1f, 0.16f), 5f, 0, 1f);
        ImGui.dummy(w, h + 2f);
        if (ImGui.isItemHovered()) ImGui.setTooltip(EditorI18n.t("Рампа шейдинга"));
    }

    private void toolButton(Tool t, String icon, String tip) {
        if (EditorIcons.iconToggleButton(icon, "tex-tool-" + t + "-" + path, 16f, tip, tool == t)) {
            tool = t;
        }
    }

    private String shapeIcon() {
        return switch (shapeKind) {
            case LINE -> "slash";
            case RECT -> "square";
            case ELLIPSE -> "circle";
            case TRIANGLE -> "triangle";
        };
    }

    private void shapeKindButton(ShapeKind kind, String icon, String tip) {
        if (EditorIcons.iconToggleButton(icon, "tex-shape-" + kind + "-" + path, 16f, tip, shapeKind == kind)) {
            shapeKind = kind;
            tool = Tool.SHAPE;
        }
    }

    private void modeButton(Mode mode, String icon, String tip) {
        boolean on = effectiveSelectMode() == mode;
        if (EditorIcons.iconToggleButton(icon, "tex-selmode-" + mode + "-" + path, 16f, tip, on)) {
            selectMode = mode;
        }
    }

    private void renderCanvas() {
        float viewW = Math.max(32f, ImGui.getContentRegionAvailX());
        float viewH = Math.max(32f, ImGui.getContentRegionAvailY());
        if (!zoomInit) {
            zoom = Math.max(1f, Math.min(24f, (float) Math.floor(Math.min(viewW / doc.width, viewH / doc.height))));
            panX = (viewW - doc.width * zoom) * 0.5f;
            panY = (viewH - doc.height * zoom) * 0.5f;
            zoomInit = true;
        }
        float dw = doc.width * zoom;
        float dh = doc.height * zoom;
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImGui.invisibleButton("##tex-canvas-" + path, viewW, viewH);
        boolean hovered = ImGui.isItemHovered();
        ImVec2 mouse = ImGui.getMousePos();
        if (hovered && ImGui.isMouseClicked(2)) panning = true;
        if (panning && ImGui.isMouseDown(2)) {
            panX += ImGui.getIO().getMouseDeltaX();
            panY += ImGui.getIO().getMouseDeltaY();
        }
        if (!ImGui.isMouseDown(2)) panning = false;

        float imgX = origin.x + panX;
        float imgY = origin.y + panY;
        int px = (int) Math.floor((mouse.x - imgX) / zoom);
        int py = (int) Math.floor((mouse.y - imgY) / zoom);
        if (hovered && doc.inBounds(px, py) && !panning) {
            hoverX = px;
            hoverY = py;
        } else if (!stroking && !selecting && !shaping && !moving && !grading) {
            hoverX = -1;
            hoverY = -1;
        }
        if (hovered && !panning && ImGui.getIO().getMouseWheel() != 0f) {
            float old = zoom;
            zoom = Math.max(1f, Math.min(32f, zoom + ImGui.getIO().getMouseWheel()));
            dw = doc.width * zoom;
            dh = doc.height * zoom;
            float mx = mouse.x - origin.x;
            float my = mouse.y - origin.y;
            panX = mx - (mx - panX) * (zoom / old);
            panY = my - (my - panY) * (zoom / old);
            imgX = origin.x + panX;
            imgY = origin.y + panY;
        }
        if (!panning) handleCanvasInput(hovered, px, py);
        rebuildPreview(false);
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.pushClipRect(origin.x, origin.y, origin.x + viewW, origin.y + viewH, true);
        drawChecker(dl, imgX, imgY, dw, dh);
        if (preview != null) {
            dl.addImage(preview.getId(), imgX, imgY, imgX + dw, imgY + dh);
        }
        if (showGrid && zoom >= 6f) {
            int gridCol = pack(0.15f, 0.18f, 0.24f, 0.55f);
            for (int x = 0; x <= doc.width; x++) {
                float sx = imgX + x * zoom;
                dl.addLine(sx, imgY, sx, imgY + dh, gridCol, 1f);
            }
            for (int y = 0; y <= doc.height; y++) {
                float sy = imgY + y * zoom;
                dl.addLine(imgX, sy, imgX + dw, sy, gridCol, 1f);
            }
        }
        SelectionMask drawMask = visualMask();
        if (drawMask != null && !drawMask.isEmpty()) {
            drawSelectionMask(dl, drawMask, imgX, imgY);
        }
        if (selecting && marquee != null && !marquee.isEmpty()) {
            float x0 = imgX + marquee.x * zoom;
            float y0 = imgY + marquee.y * zoom;
            drawDashedRect(dl, x0, y0, x0 + marquee.w * zoom, y0 + marquee.h * zoom);
        }
        if (grading && lastPx >= 0) {
            float x0 = imgX + (gradX0 + 0.5f) * zoom;
            float y0 = imgY + (gradY0 + 0.5f) * zoom;
            float x1 = imgX + (lastPx + 0.5f) * zoom;
            float y1 = imgY + (lastPy + 0.5f) * zoom;
            dl.addLine(x0, y0, x1, y1, pack(color[0], color[1], color[2], 0.9f), 1.6f);
            dl.addCircleFilled(x0, y0, 3f, pack(color[0], color[1], color[2], 1f));
            dl.addCircleFilled(x1, y1, 3f, pack(colorB[0], colorB[1], colorB[2], 1f));
        }
        if (hovered && !panning && hoverX >= 0 && tool != Tool.GRADIENT) {
            float hx = imgX + hoverX * zoom;
            float hy = imgY + hoverY * zoom;
            float hs = Math.max(zoom, brush * zoom);
            float ox = imgX + (hoverX - (brush - 1) / 2f) * zoom;
            float oy = imgY + (hoverY - (brush - 1) / 2f) * zoom;
            if (tool == Tool.FILL || tool == Tool.EYEDROPPER || tool == Tool.WAND || tool == Tool.SELECT) {
                dl.addRect(hx, hy, hx + zoom, hy + zoom, pack(1f, 1f, 1f, 0.7f));
            } else {
                dl.addRect(ox, oy, ox + hs, oy + hs, pack(1f, 1f, 1f, 0.55f));
            }
        }
        dl.popClipRect();
    }

    private void handleCanvasInput(boolean hovered, int px, int py) {
        boolean alt = ImGui.getIO().getKeyAlt();
        if (hovered && ImGui.isMouseClicked(1) && doc.inBounds(px, py)) {
            pick(px, py, tool == Tool.GRADIENT);
            return;
        }
        if (hovered && ImGui.isMouseClicked(0) && alt && !isSelectTool() && doc.inBounds(px, py)) {
            pick(px, py, ImGui.getIO().getKeyShift());
            return;
        }
        Tool use = tool;
        if (alt && !isSelectTool()) use = Tool.EYEDROPPER;

        if (hovered && ImGui.isMouseClicked(0) && isSelectTool()
                && !doc.inBounds(px, py) && !mask.contains(px, py)
                && !ImGui.getIO().getKeyShift() && !ImGui.getIO().getKeyAlt()
                && effectiveSelectMode() == Mode.REPLACE) {
            clearSelection();
            return;
        }
        if (hovered && ImGui.isMouseClicked(0) && doc.inBounds(px, py)) {
            if (tryBeginSelectionMove(px, py)) {
                return;
            }
            switch (use) {
                case EYEDROPPER -> pick(px, py, ImGui.getIO().getKeyShift());
                case GRADIENT -> {
                    grading = true;
                    gradX0 = px;
                    gradY0 = py;
                    lastPx = px;
                    lastPy = py;
                    previewDirty = true;
                }
                case FILL -> {
                    pushUndo();
                    doc.floodFill(px, py, currentColor(), clipOrNull());
                    markDirty();
                }
                case SELECT -> beginSelectOrMove(px, py);
                case WAND -> applyWand(px, py);
                case SHAPE -> {
                    shaping = true;
                    lineX0 = px;
                    lineY0 = py;
                    lastPx = px;
                    lastPy = py;
                    previewDirty = true;
                }
                case PENCIL, ERASER -> {
                    pushUndo();
                    stroking = true;
                    lastPx = px;
                    lastPy = py;
                    doc.stamp(px, py, currentColor(), brush, use == Tool.ERASER, clipOrNull());
                    markDirty();
                }
            }
        }
        if ((stroking || shaping || selecting || moving || movePending || grading) && ImGui.isMouseDown(0)) {
            int cx = px;
            int cy = py;
            if ((grading || shaping) && ImGui.getIO().getKeyShift()) {
                int ox = shaping ? lineX0 : gradX0;
                int oy = shaping ? lineY0 : gradY0;
                int[] snapped = constrain45(ox, oy, cx, cy);
                cx = snapped[0];
                cy = snapped[1];
            }
            if (movePending && (cx != moveGrabX || cy != moveGrabY)) {
                beginMove(moveGrabX, moveGrabY, moveCopy);
                movePending = false;
            }
            if (stroking && lastPx >= 0 && (cx != lastPx || cy != lastPy)) {
                doc.line(lastPx, lastPy, cx, cy, currentColor(), brush, tool == Tool.ERASER, clipOrNull());
                markDirty();
            }
            if (grading || shaping) {
                lastPx = cx;
                lastPy = cy;
                previewDirty = true;
            }
            if (selecting) {
                marquee = IntRect.fromCorners(selX0, selY0, cx, cy).clipped(doc.width, doc.height);
            }
            if (moving) {
                moveDx = cx - moveGrabX;
                moveDy = cy - moveGrabY;
                previewDirty = true;
            }
            lastPx = cx;
            lastPy = cy;
        }
        if (stroking && !ImGui.isMouseDown(0)) stroking = false;
        if (shaping && !ImGui.isMouseDown(0)) {
            pushUndo();
            commitShape();
            shaping = false;
            markDirty();
        }
        if (selecting && !ImGui.isMouseDown(0)) {
            finishSelect();
        }
        if (movePending && !ImGui.isMouseDown(0)) {
            movePending = false;
        }
        if (moving && !ImGui.isMouseDown(0)) {
            finishMove();
        }
        if (grading && !ImGui.isMouseDown(0)) {
            commitGradient();
        }
    }

    /** Snap (x1,y1) to the nearest 0° / 45° / 90° axis from (x0,y0). */
    private static int[] constrain45(int x0, int y0, int x1, int y1) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int ax = Math.abs(dx);
        int ay = Math.abs(dy);
        if (ax == 0 && ay == 0) return new int[]{x1, y1};
        if (ay * 2 <= ax) {
            return new int[]{x1, y0};
        }
        if (ax * 2 <= ay) {
            return new int[]{x0, y1};
        }
        int m = Math.max(ax, ay);
        return new int[]{x0 + Integer.signum(dx) * m, y0 + Integer.signum(dy) * m};
    }

    private boolean hasSelection() {
        return mask != null && !mask.isEmpty();
    }

    private void resetMask() {
        mask = new SelectionMask(doc.width, doc.height);
        marquee = null;
        selecting = false;
        moving = false;
        movePending = false;
        floatPixels = null;
        floatMask = null;
    }

    private boolean isSelectTool() {
        return tool == Tool.SELECT || tool == Tool.WAND;
    }

    private void commitShape() {
        paintCurrentShape();
        if (hasSelection() || shapeKind == ShapeKind.LINE) return;
        // Keep the new stamp selected so the next drag moves it instead of
        // drawing another copy on top.
        IntRect bounds = IntRect.fromCorners(lineX0, lineY0, lastPx, lastPy)
                .clipped(doc.width, doc.height);
        if (!bounds.isEmpty()) {
            mask.clear();
            mask.applyRect(bounds, Mode.REPLACE);
        }
    }

    private void paintCurrentShape() {
        int color = currentColor();
        PixelClip clip = clipOrNull();
        switch (shapeKind) {
            case LINE -> doc.line(lineX0, lineY0, lastPx, lastPy, color, brush, false, clip);
            case RECT -> doc.rect(lineX0, lineY0, lastPx, lastPy, color, brush, shapeFill, clip);
            case ELLIPSE -> doc.ellipse(lineX0, lineY0, lastPx, lastPy, color, brush, shapeFill, clip);
            case TRIANGLE -> doc.triangle(lineX0, lineY0, lastPx, lastPy, color, brush, shapeFill, clip);
        }
    }

    private Mode effectiveSelectMode() {
        if (!isSelectTool()) return selectMode;
        boolean shift = ImGui.getIO().getKeyShift();
        boolean alt = ImGui.getIO().getKeyAlt();
        if (shift && alt) return Mode.INTERSECT;
        if (shift) return Mode.ADD;
        if (alt) return Mode.SUBTRACT;
        return selectMode;
    }

    private void applyWand(int px, int py) {
        if (doc == null || !doc.inBounds(px, py)) return;
        if (moving) finishMove();
        movePending = false;
        boolean[] bits = doc.floodSelect(px, py, wandTolerance, wandContiguous, wandSampleAll);
        mask.applyBits(bits, effectiveSelectMode());
    }

    private boolean hitsSelection(int px, int py) {
        return hasSelection() && mask.contains(px, py);
    }

    /**
     * Shift / Alt are mask-edit modifiers and must never start a move.
     * Lift is deferred until the mouse actually changes pixel, so a click
     * on a selected pixel does not steal the next neighbor click.
     */
    private boolean tryBeginSelectionMove(int px, int py) {
        if (!hitsSelection(px, py)) return false;
        if (ImGui.getIO().getKeyShift() || ImGui.getIO().getKeyAlt()) return false;
        movePending = true;
        moveCopy = ImGui.getIO().getKeyCtrl();
        moveGrabX = px;
        moveGrabY = py;
        moveDx = 0;
        moveDy = 0;
        return true;
    }

    private void beginSelectOrMove(int px, int py) {
        selectStrokeMode = effectiveSelectMode();
        selecting = true;
        selX0 = px;
        selY0 = py;
        lastPx = px;
        lastPy = py;
        marquee = new IntRect(px, py, 1, 1);
    }

    private void finishSelect() {
        if (!selecting) return;
        if (marquee != null) {
            mask.applyRect(marquee, selectStrokeMode);
        }
        selecting = false;
        marquee = null;
    }

    private void beginMove(int px, int py, boolean copy) {
        if (!hasSelection()) return;
        liftSelection(copy);
        moving = true;
        moveGrabX = px;
        moveGrabY = py;
        moveDx = 0;
        moveDy = 0;
        previewDirty = true;
    }

    /** Lift selected pixels into a floating stamp. {@code copy} keeps the original in place. */
    private void liftSelection(boolean copy) {
        if (!hasSelection() || floatPixels != null) return;
        pushUndo();
        IntRect b = mask.bounds();
        floatPixels = doc.copyRect(b);
        floatMask = mask.extract(b);
        floatX = b.x;
        floatY = b.y;
        floatW = b.w;
        floatH = b.h;
        zeroUnselected(floatPixels, floatMask);
        if (!copy) doc.clearWhere(mask);
        markDirty();
    }

    private void duplicateSelection() {
        if (!hasSelection()) return;
        if (moving) finishMove();
        pushUndo();
        IntRect b = mask.bounds();
        int[] pixels = doc.copyRect(b);
        boolean[] bits = mask.extract(b);
        zeroUnselected(pixels, bits);
        int dx = b.x + 1 < doc.width ? 1 : (b.x > 0 ? -1 : 0);
        int dy = b.y + 1 < doc.height ? 1 : (b.y > 0 ? -1 : 0);
        doc.pasteRectMasked(b.x + dx, b.y + dy, b.w, b.h, pixels, bits);
        mask.offset(dx, dy);
        markDirty();
    }

    private void deleteSelection() {
        movePending = false;
        if (moving) {
            floatPixels = null;
            floatMask = null;
            moving = false;
            moveDx = 0;
            moveDy = 0;
            markDirty();
            return;
        }
        if (!hasSelection()) return;
        pushUndo();
        doc.clearWhere(mask);
        markDirty();
    }

    private void finishMove() {
        if (!moving) return;
        if (floatPixels != null) {
            doc.pasteRectMasked(floatX + moveDx, floatY + moveDy,
                    floatW, floatH, floatPixels, floatMask);
            mask.offset(moveDx, moveDy);
            markDirty();
        }
        moving = false;
        moveDx = 0;
        moveDy = 0;
        floatPixels = null;
        floatMask = null;
    }

    private void nudgeSelection(int dx, int dy) {
        if (!hasSelection() || (dx == 0 && dy == 0)) return;
        pushUndo();
        IntRect b = mask.bounds();
        int[] pixels = doc.copyRect(b);
        boolean[] bits = mask.extract(b);
        zeroUnselected(pixels, bits);
        doc.clearWhere(mask);
        doc.pasteRectMasked(b.x + dx, b.y + dy, b.w, b.h, pixels, bits);
        mask.offset(dx, dy);
        markDirty();
    }

    private boolean clearSelection() {
        if (moving) finishMove();
        movePending = false;
        selecting = false;
        marquee = null;
        if (!hasSelection()) return false;
        mask.clear();
        return true;
    }

    private SelectionMask visualMask() {
        if (selecting && marquee != null) {
            SelectionMask preview = mask.copy();
            preview.applyRect(marquee, selectStrokeMode);
            return preview;
        }
        if (moving && hasSelection()) {
            SelectionMask preview = mask.copy();
            preview.offset(moveDx, moveDy);
            return preview;
        }
        return mask;
    }

    private static void zeroUnselected(int[] pixels, boolean[] bits) {
        if (pixels == null || bits == null) return;
        int n = Math.min(pixels.length, bits.length);
        for (int i = 0; i < n; i++) {
            if (!bits[i]) pixels[i] = 0;
        }
    }

    private void pick(int x, int y, boolean shadow) {
        TextureDocument.unpackRgba(doc.compositeAt(x, y), shadow ? colorB : color);
        editShadow = shadow && tool == Tool.GRADIENT;
        syncHex();
    }

    private int currentColor() {
        return TextureDocument.packRgba(color[0], color[1], color[2], color[3]);
    }

    private int shadowColor() {
        return TextureDocument.packRgba(colorB[0], colorB[1], colorB[2], colorB[3]);
    }

    private void commitGradient() {
        if (!grading || doc == null) {
            grading = false;
            return;
        }
        int x0 = gradX0;
        int y0 = gradY0;
        int x1 = lastPx;
        int y1 = lastPy;
        boolean tap = x0 == x1 && y0 == y1;
        if (tap && !hasSelection()) {
            grading = false;
            previewDirty = true;
            return;
        }
        if (tap) {
            IntRect b = mask.bounds();
            x0 = b.x;
            y0 = b.y;
            x1 = b.x + Math.max(0, b.w - 1);
            y1 = b.y;
        }
        TextureDocument.Layer layer = doc.activeLayer();
        if (layer == null || layer.pixels == null) {
            grading = false;
            return;
        }
        pushUndo();
        PixelArtGradient.paint(layer.pixels, doc.width, doc.height,
                x0, y0, x1, y1,
                currentColor(), shadowColor(),
                gradSteps, hueShift, dither, ditherAmt,
                gradShape, clipBoundsOrNull(), clipOrNull());
        grading = false;
        markDirty();
    }

    private PixelClip clipOrNull() {
        return hasSelection() ? mask : null;
    }

    private IntRect clipBoundsOrNull() {
        return hasSelection() ? mask.bounds() : null;
    }

    private void copySelection() {
        if (!hasSelection()) return;
        IntRect b = mask.bounds();
        clipPixels = doc.copyRect(b);
        clipMask = mask.extract(b);
        clipW = b.w;
        clipH = b.h;
        zeroUnselected(clipPixels, clipMask);
    }

    private void cutSelection() {
        if (!hasSelection()) return;
        pushUndo();
        copySelection();
        doc.clearWhere(mask);
        markDirty();
    }

    private void pasteClipboard() {
        if (clipPixels == null || clipW <= 0) return;
        pushUndo();
        IntRect at = hasSelection() ? mask.bounds() : new IntRect(0, 0, clipW, clipH);
        int x = at.x;
        int y = at.y;
        doc.pasteRectMasked(x, y, clipW, clipH, clipPixels, clipMask);
        mask.replaceWith(x, y, clipW, clipH, clipMask);
        markDirty();
    }

    private void renderPalette() {
        sectionLabel(EditorI18n.t("Палитра"));
        float avail = Math.max(40f, ImGui.getContentRegionAvailX() - 8f);
        float sw = 22f;
        float gap = 4f;
        int cols = Math.max(5, (int) (avail / (sw + gap)));
        int i = 0;
        for (float[] swatch : PALETTE) {
            paletteSwatch("base-" + i, swatch, sw);
            i++;
            if (i % cols != 0) ImGui.sameLine(0f, gap);
        }
        if (!extraPalette.isEmpty()) {
            ImGui.spacing();
            int e = 0;
            for (float[] swatch : extraPalette) {
                paletteSwatch("extra-" + e, swatch, sw);
                e++;
                if (e % cols != 0) ImGui.sameLine(0f, gap);
            }
        }
        ImGui.spacing();
        if (ImGui.smallButton(EditorI18n.t("Добавить в палитру") + "##tex-pal-add")) {
            extraPalette.add(new float[]{
                    activeColor()[0], activeColor()[1], activeColor()[2], activeColor()[3]});
        }
    }

    private void paletteSwatch(String id, float[] swatch, float size) {
        ImGui.pushID(id);
        boolean selected = colorsEqual(swatch, activeColor());
        if (colorChip("##sw", swatch, selected, size, hexOf(swatch))) {
            applyColor(swatch, editShadow && tool == Tool.GRADIENT);
        }
        if (ImGui.isItemClicked(1)) {
            applyColor(swatch, true);
            if (tool == Tool.GRADIENT) editShadow = true;
        }
        ImGui.popID();
    }

    private void renderLayers() {
        sectionLabel(EditorI18n.t("Слои"));
        if (EditorIcons.iconButton("plus", "tex-layer-add", 14f, EditorI18n.t("Добавить слой"))) {
            pushUndo();
            doc.addLayer(null);
            syncLayerName();
            markDirty();
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("copy", "tex-layer-dup", 14f, EditorI18n.t("Дублировать слой"))) {
            pushUndo();
            doc.duplicateActive();
            syncLayerName();
            markDirty();
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("x", "tex-layer-del", 14f, EditorI18n.t("Удалить слой"))) {
            if (doc.layers.size() > 1) {
                pushUndo();
                doc.removeActive();
                syncLayerName();
                markDirty();
            }
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("chevron-up", "tex-layer-up", 14f, EditorI18n.t("Переместить вверх"))) {
            pushUndo();
            doc.moveActive(1);
            markDirty();
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("chevron-down", "tex-layer-down", 14f, EditorI18n.t("Переместить вниз"))) {
            pushUndo();
            doc.moveActive(-1);
            markDirty();
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("layers", "tex-layer-merge", 14f,
                EditorI18n.t("Объединить с нижним") + "  Ctrl+E")) {
            if (doc.canMergeDown()) {
                pushUndo();
                doc.mergeDown();
                syncLayerName();
                markDirty();
            }
        }
        ImGui.sameLine();
        if (ImGui.smallButton(EditorI18n.t("Свести") + "##tex-flatten")) {
            if (doc.canFlatten()) {
                pushUndo();
                doc.flattenVisible();
                syncLayerName();
                markDirty();
            }
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(EditorI18n.t("Свести видимые слои") + "  Ctrl+Shift+E");
        float listH = Math.max(96f, ImGui.getContentRegionAvailY() - 56f);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.06f, 0.065f, 0.08f, 1f);
        ImGui.beginChild("tex-layer-list", 0f, listH, true);
        for (int i = doc.layers.size() - 1; i >= 0; i--) {
            TextureDocument.Layer layer = doc.layers.get(i);
            ImGui.pushID(i);
            boolean selected = doc.active == i;
            if (selected) {
                ImGui.pushStyleColor(ImGuiCol.Header, 0.22f, 0.38f, 0.62f, 0.85f);
                ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.26f, 0.44f, 0.72f, 1f);
            }
            if (EditorIcons.iconToggleButton(layer.visible ? "eye" : "eye-off",
                    "vis", 14f, EditorI18n.t("Видимость"), layer.visible)) {
                pushUndo();
                layer.visible = !layer.visible;
                markDirty();
            }
            ImGui.sameLine();
            if (ImGui.selectable(layer.name + "##sel", selected)) {
                doc.active = i;
                syncLayerName();
            }
            if (selected) ImGui.popStyleColor(2);
            ImGui.popID();
        }
        ImGui.endChild();
        ImGui.popStyleColor();
        TextureDocument.Layer active = doc.activeLayer();
        if (active != null) {
            ImGui.setNextItemWidth(-1f);
            if (ImGui.inputText("##tex-layer-name", layerName)) {
                active.name = layerName.get();
                dirty = true;
            }
            float[] op = {active.opacity * 100f};
            ImGui.setNextItemWidth(-1f);
            if (ImGui.sliderFloat(EditorI18n.t("Непрозрачность") + "##tex-op", op, 0f, 100f, "%.0f%%")) {
                if (ImGui.isItemActivated()) pushUndo();
                active.opacity = op[0] / 100f;
                markDirty();
            }
        }
    }

    private void syncLayerName() {
        TextureDocument.Layer active = doc == null ? null : doc.activeLayer();
        layerName.set(active == null || active.name == null ? "" : active.name);
    }

    private void pushUndo() {
        if (doc == null) return;
        undo.addLast(doc.snapshot());
        if (undo.size() > UNDO_CAP) undo.removeFirst();
        redo.clear();
    }

    private void markDirty() {
        dirty = true;
        previewDirty = true;
    }

    private void rebuildPreview(boolean force) {
        if (doc == null) return;
        int need = doc.width * doc.height;
        if (compositeBuf.length != need) compositeBuf = new int[need];
        if (preview == null || previewPixels == null
                || previewPixels.getWidth() != doc.width
                || previewPixels.getHeight() != doc.height) {
            if (preview != null) preview.close();
            NativeImage img = new NativeImage(doc.width, doc.height, true);
            preview = new DynamicTexture(img);
            preview.setFilter(false, false);
            previewPixels = preview.getPixels();
            previewDirty = true;
        }
        if (!force && !previewDirty) return;
        TextureDocument.Layer previewLayer = (grading || shaping) ? doc.activeLayer() : null;
        int[] savedPixels = null;
        if (previewLayer != null && previewLayer.pixels != null) {
            savedPixels = previewLayer.pixels;
            previewLayer.pixels = savedPixels.clone();
            if (grading) {
                PixelArtGradient.paint(previewLayer.pixels, doc.width, doc.height,
                        gradX0, gradY0, lastPx, lastPy,
                        currentColor(), shadowColor(),
                        gradSteps, hueShift, dither, ditherAmt,
                        gradShape, clipBoundsOrNull(), clipOrNull());
            } else {
                paintCurrentShape();
            }
        }
        doc.flatten(compositeBuf);
        if (savedPixels != null) previewLayer.pixels = savedPixels;
        if (moving && floatPixels != null) {
            int dx = floatX + moveDx;
            int dy = floatY + moveDy;
            for (int y = 0; y < floatH; y++) {
                for (int x = 0; x < floatW; x++) {
                    int i = y * floatW + x;
                    if (floatMask != null && (i >= floatMask.length || !floatMask[i])) continue;
                    int px = dx + x;
                    int py = dy + y;
                    if (!doc.inBounds(px, py)) continue;
                    compositeBuf[py * doc.width + px] = floatPixels[i];
                }
            }
        }
        for (int y = 0; y < doc.height; y++) {
            for (int x = 0; x < doc.width; x++) {
                previewPixels.setPixelRGBA(x, y, compositeBuf[y * doc.width + x]);
            }
        }
        preview.upload();
        previewDirty = false;
    }

    private void drawSelectionMask(ImDrawList dl, SelectionMask m, float imgX, float imgY) {
        IntRect b = m.bounds();
        if (b == null || b.isEmpty()) return;
        if (zoom >= 3f) {
            int fill = pack(0.35f, 0.62f, 1f, 0.16f);
            for (int y = b.y; y < b.y + b.h; y++) {
                int run = -1;
                for (int x = b.x; x <= b.x + b.w; x++) {
                    boolean on = x < b.x + b.w && m.contains(x, y);
                    if (on && run < 0) run = x;
                    if (!on && run >= 0) {
                        dl.addRectFilled(imgX + run * zoom, imgY + y * zoom,
                                imgX + x * zoom, imgY + (y + 1) * zoom, fill);
                        run = -1;
                    }
                }
            }
        }
        float dash = Math.max(3f, Math.min(6f, zoom));
        float gap = dash;
        float period = dash + gap;
        float phase = (float) (ImGui.getTime() * 28.0 % period);
        float edge = zoom >= 6f ? 1f : 1.25f;
        drawMaskEdgeRuns(dl, m, b, imgX, imgY, 0, -1, pack(0.05f, 0.05f, 0.07f, 0.85f),
                dash, gap, phase + dash, edge + 1f);
        drawMaskEdgeRuns(dl, m, b, imgX, imgY, 0, 1, pack(0.05f, 0.05f, 0.07f, 0.85f),
                dash, gap, phase + dash, edge + 1f);
        drawMaskEdgeRuns(dl, m, b, imgX, imgY, -1, 0, pack(0.05f, 0.05f, 0.07f, 0.85f),
                dash, gap, phase + dash, edge + 1f);
        drawMaskEdgeRuns(dl, m, b, imgX, imgY, 1, 0, pack(0.05f, 0.05f, 0.07f, 0.85f),
                dash, gap, phase + dash, edge + 1f);
        drawMaskEdgeRuns(dl, m, b, imgX, imgY, 0, -1, pack(1f, 1f, 1f, 1f), dash, gap, phase, edge);
        drawMaskEdgeRuns(dl, m, b, imgX, imgY, 0, 1, pack(1f, 1f, 1f, 1f), dash, gap, phase, edge);
        drawMaskEdgeRuns(dl, m, b, imgX, imgY, -1, 0, pack(1f, 1f, 1f, 1f), dash, gap, phase, edge);
        drawMaskEdgeRuns(dl, m, b, imgX, imgY, 1, 0, pack(1f, 1f, 1f, 1f), dash, gap, phase, edge);
    }

    private void drawMaskEdgeRuns(ImDrawList dl, SelectionMask m, IntRect b,
                                 float imgX, float imgY, int nx, int ny,
                                 int col, float dash, float gap, float phase, float thickness) {
        boolean horiz = ny != 0;
        if (horiz) {
            for (int y = b.y; y < b.y + b.h; y++) {
                int run = -1;
                for (int x = b.x; x <= b.x + b.w; x++) {
                    boolean edge = x < b.x + b.w && m.contains(x, y) && !m.contains(x, y + ny);
                    if (edge && run < 0) run = x;
                    if (!edge && run >= 0) {
                        float y0 = imgY + (ny < 0 ? y : y + 1) * zoom;
                        dashedLine(dl, imgX + run * zoom, y0, imgX + x * zoom, y0,
                                col, dash, gap, phase, thickness);
                        run = -1;
                    }
                }
            }
        } else {
            for (int x = b.x; x < b.x + b.w; x++) {
                int run = -1;
                for (int y = b.y; y <= b.y + b.h; y++) {
                    boolean edge = y < b.y + b.h && m.contains(x, y) && !m.contains(x + nx, y);
                    if (edge && run < 0) run = y;
                    if (!edge && run >= 0) {
                        float x0 = imgX + (nx < 0 ? x : x + 1) * zoom;
                        dashedLine(dl, x0, imgY + run * zoom, x0, imgY + y * zoom,
                                col, dash, gap, phase, thickness);
                        run = -1;
                    }
                }
            }
        }
    }

    private static void drawDashedRect(ImDrawList dl, float x0, float y0, float x1, float y1) {
        float dash = 4f;
        float gap = 4f;
        float period = dash + gap;
        float phase = (float) (ImGui.getTime() * 28.0 % period);
        drawDashedRectEdges(dl, x0, y0, x1, y1, pack(0.05f, 0.05f, 0.07f, 0.85f), dash, gap, phase + dash, 2f);
        drawDashedRectEdges(dl, x0, y0, x1, y1, pack(1f, 1f, 1f, 1f), dash, gap, phase, 1f);
    }

    private static void drawDashedRectEdges(ImDrawList dl, float x0, float y0, float x1, float y1,
                                           int col, float dash, float gap, float phase, float thickness) {
        dashedLine(dl, x0, y0, x1, y0, col, dash, gap, phase, thickness);
        dashedLine(dl, x1, y0, x1, y1, col, dash, gap, phase, thickness);
        dashedLine(dl, x1, y1, x0, y1, col, dash, gap, phase, thickness);
        dashedLine(dl, x0, y1, x0, y0, col, dash, gap, phase, thickness);
    }

    private static void dashedLine(ImDrawList dl, float x0, float y0, float x1, float y1,
                                  int col, float dash, float gap, float phase, float thickness) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.5f) return;
        float ux = dx / len;
        float uy = dy / len;
        float period = dash + gap;
        float t = -((phase % period) + period) % period;
        while (t < len) {
            float a = Math.max(0f, t);
            float b = Math.min(len, t + dash);
            if (b > a) {
                dl.addLine(x0 + ux * a, y0 + uy * a, x0 + ux * b, y0 + uy * b, col, thickness);
            }
            t += period;
        }
    }

    private static void drawChecker(ImDrawList dl, float x, float y, float w, float h) {
        dl.addRectFilled(x, y, x + w, y + h, pack(0.18f, 0.19f, 0.22f, 1f));
        float cell = Math.max(6f, Math.min(16f, w / 16f));
        int light = pack(0.28f, 0.29f, 0.33f, 1f);
        int cols = (int) Math.ceil(w / cell);
        int rows = (int) Math.ceil(h / cell);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (((row + col) & 1) == 0) continue;
                float x0 = x + col * cell;
                float y0 = y + row * cell;
                dl.addRectFilled(x0, y0, Math.min(x + w, x0 + cell), Math.min(y + h, y0 + cell), light);
            }
        }
    }

    private float[] activeColor() {
        return editShadow && tool == Tool.GRADIENT ? colorB : color;
    }

    private void applyColor(float[] src, boolean shadow) {
        float[] dst = shadow ? colorB : color;
        dst[0] = src[0];
        dst[1] = src[1];
        dst[2] = src[2];
        dst[3] = src[3];
        if (shadow && tool == Tool.GRADIENT) editShadow = true;
        else if (!shadow) editShadow = false;
        syncHex();
        if (grading) previewDirty = true;
    }

    private void syncHex() {
        hexBuf.set(hexOf(activeColor()));
    }

    private void applyHex(String raw) {
        if (raw == null) return;
        String s = raw.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6 && s.length() != 8) return;
        try {
            int v = (int) Long.parseLong(s, 16);
            float[] dst = activeColor();
            if (s.length() == 6) {
                dst[0] = ((v >> 16) & 0xFF) / 255f;
                dst[1] = ((v >> 8) & 0xFF) / 255f;
                dst[2] = (v & 0xFF) / 255f;
            } else {
                dst[0] = ((v >> 24) & 0xFF) / 255f;
                dst[1] = ((v >> 16) & 0xFF) / 255f;
                dst[2] = ((v >> 8) & 0xFF) / 255f;
                dst[3] = (v & 0xFF) / 255f;
            }
            if (grading) previewDirty = true;
        } catch (NumberFormatException ignored) {
        }
    }

    private static String hexOf(float[] c) {
        int r = Math.round(clamp01(c[0]) * 255f);
        int g = Math.round(clamp01(c[1]) * 255f);
        int b = Math.round(clamp01(c[2]) * 255f);
        int a = Math.round(clamp01(c[3]) * 255f);
        if (a >= 255) return String.format("#%02X%02X%02X", r, g, b);
        return String.format("#%02X%02X%02X%02X", r, g, b, a);
    }

    private static String rgbaLabel(float[] c) {
        return Math.round(clamp01(c[0]) * 255f) + "  "
                + Math.round(clamp01(c[1]) * 255f) + "  "
                + Math.round(clamp01(c[2]) * 255f) + "  "
                + Math.round(clamp01(c[3]) * 100f) + "%";
    }

    private static boolean colorsEqual(float[] a, float[] b) {
        return Math.abs(a[0] - b[0]) < 0.004f
                && Math.abs(a[1] - b[1]) < 0.004f
                && Math.abs(a[2] - b[2]) < 0.004f
                && Math.abs(a[3] - b[3]) < 0.004f;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }

    private static void sectionLabel(String title) {
        ImGui.textColored(0.78f, 0.82f, 0.90f, 1f, title);
        ImVec2 p = ImGui.getCursorScreenPos();
        float w = ImGui.getContentRegionAvailX();
        ImGui.getWindowDrawList().addLine(p.x, p.y, p.x + w, p.y, pack(1f, 1f, 1f, 0.08f), 1f);
        ImGui.dummy(w, 6f);
    }

    private static void sectionGap() {
        ImGui.dummy(1f, 10f);
    }

    private static void helpMark(String id, String tip) {
        ImGui.sameLine(0f, 4f);
        ImGui.pushID(id);
        float size = 14f;
        ImVec2 p = ImGui.getCursorScreenPos();
        ImGui.invisibleButton("##help", size, size);
        var tex = EditorIcons.get("circle-help");
        if (tex != null) {
            float icon = 13f;
            float x = p.x + (size - icon) * 0.5f;
            float y = p.y + (size - icon) * 0.5f;
            ImGui.getWindowDrawList().addImage(tex.glId(), x, y, x + icon, y + icon,
                    0f, 0f, 1f, 1f, pack(0.58f, 0.63f, 0.72f, 1f));
        } else {
            ImGui.getWindowDrawList().addText(p.x + 3f, p.y, pack(0.58f, 0.63f, 0.72f, 1f), "?");
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(tip);
        ImGui.popID();
    }

    private static boolean colorChip(String id, float[] c, boolean selected, float size, String tip) {
        ImGui.pushID(id);
        ImVec2 p = ImGui.getCursorScreenPos();
        boolean clicked = ImGui.invisibleButton("##chip", size, size);
        ImDrawList dl = ImGui.getWindowDrawList();
        drawChecker(dl, p.x, p.y, size, size);
        dl.addRectFilled(p.x, p.y, p.x + size, p.y + size, pack(c[0], c[1], c[2], c[3]), 5f);
        if (selected) {
            dl.addRect(p.x - 1.5f, p.y - 1.5f, p.x + size + 1.5f, p.y + size + 1.5f,
                    pack(0.45f, 0.78f, 1f, 1f), 6f, 0, 1.8f);
        } else {
            dl.addRect(p.x, p.y, p.x + size, p.y + size, pack(1f, 1f, 1f, 0.16f), 5f);
        }
        if (tip != null && ImGui.isItemHovered()) ImGui.setTooltip(tip);
        ImGui.popID();
        return clicked;
    }

    private static int pack(float r, float g, float b, float a) {
        return TextureDocument.packRgba(r, g, b, a);
    }

    private static String key(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }
}
