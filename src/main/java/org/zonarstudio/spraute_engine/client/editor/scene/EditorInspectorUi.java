package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

/** Engine-style inspector widgets (Unity / Unreal inspired, not raw ImGui). */
public final class EditorInspectorUi {
    @FunctionalInterface
    public interface AnimationFieldAction {
        void add(String label, String scriptApi);
    }

    private static AnimationFieldAction animationFieldAction;
    private static Runnable resetAction;
    private static boolean resetModified;
    private static int resetSeq;

    private static String clipNodeId = "";
    private static FieldClip pendingClip;
    private static String currentSectionId = "";
    private static String currentSectionTitle = "";
    private static List<FieldClip> buildingSection;
    private static String forceOpenSectionId;
    private static String pendingSectionPasteId;
    private static boolean pendingSectionPasteNow;
    private static final Map<String, List<FieldClip>> sectionSnapshots = new LinkedHashMap<>();
    private static Clipboard clipboard;
    private static String clipFlash;
    private static long clipFlashUntil;

    public static void setAnimationFieldAction(AnimationFieldAction action) {
        animationFieldAction = action;
    }

    /** Start of an inspector pass: drop a stale reset binding from a skipped field. */
    public static void beginPass() {
        resetAction = null;
        resetModified = false;
        resetSeq = 0;
        pendingClip = null;
        buildingSection = null;
        currentSectionId = "";
        currentSectionTitle = "";
        pendingSectionPasteNow = false;
        drawClipFlash();
    }

    public static void setClipboardNode(String nodeId) {
        String id = nodeId == null ? "" : nodeId;
        if (!id.equals(clipNodeId)) {
            clipNodeId = id;
            sectionSnapshots.clear();
        }
    }

    /**
     * Next field can be copied / pasted (ПКМ on label or value).
     * Call together with {@link #bindReset} when the field has a setter.
     */
    public static void bindClipboard(Object current, Consumer<Object> apply) {
        pendingClip = apply == null ? null : new FieldClip("", null, current, apply);
        if (pendingClip != null && buildingSection != null) {
            buildingSection.add(pendingClip);
        }
    }

    /**
     * Next {@link #fieldLabel} / {@link #checkbox} / {@link #dragFloat} / {@link #inputText}
     * shows a revert button. Call immediately before that widget.
     */
    public static void bindReset(boolean modified, Runnable onReset) {
        resetAction = onReset;
        resetModified = modified;
    }

    public static void clearReset() {
        resetAction = null;
        resetModified = false;
    }
    public static final float AXIS_BADGE_W = 30f;
    public static final float GROUP_PAD = 10f;

    private EditorInspectorUi() {}

    public enum Axis {
        X("X", 0.93f, 0.32f, 0.34f),
        Y("Y", 0.36f, 0.82f, 0.44f),
        Z("Z", 0.34f, 0.58f, 0.98f),
        W("W", 0.78f, 0.62f, 0.38f),
        H("H", 0.62f, 0.78f, 0.42f);

        public final String label;
        public final float r;
        public final float g;
        public final float b;

        Axis(String label, float r, float g, float b) {
            this.label = label;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    public static void nodeHeader(String typeLabel, float[] typeColor, String name, String id, boolean showIdField) {
        float w = ImGui.getContentRegionAvailX();
        float h = 54f;
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImDrawList dl = ImGui.getWindowDrawList();

        dl.addRectFilled(x, y, x + w, y + h, rgba(0.11f, 0.12f, 0.15f, 0.95f), 6f);
        dl.addRect(x, y, x + w, y + h, rgba(0.45f, 0.50f, 0.58f, 0.35f), 6f, 0, 1.2f);
        dl.addRectFilled(x, y + 6f, x + 4f, y + h - 6f, rgba(typeColor[0], typeColor[1], typeColor[2], 1f), 2f);

        float badgeW = Math.max(72f, ImGui.calcTextSizeX(typeLabel) + 18f);
        dl.addRectFilled(x + 14f, y + 10f, x + 14f + badgeW, y + 28f,
                rgba(typeColor[0], typeColor[1], typeColor[2], 0.22f), 5f);
        dl.addRect(x + 14f, y + 10f, x + 14f + badgeW, y + 28f,
                rgba(typeColor[0], typeColor[1], typeColor[2], 0.55f), 5f, 0, 1f);
        float badgeTextY = y + 10f + (18f - ImGui.getTextLineHeight()) * 0.5f;
        dl.addText(x + 22f, badgeTextY,
                rgba(typeColor[0], typeColor[1], typeColor[2], 1f), typeLabel);

        String shownName = name == null || name.isBlank() ? EditorI18n.t("(без имени)") : name;
        dl.addText(x + 14f + badgeW + 10f, y + 13f, rgba(0.92f, 0.93f, 0.96f, 1f), shownName);

        if (id != null && !id.isBlank()) {
            dl.addText(x + 16f, y + 34f, rgba(0.55f, 0.58f, 0.64f, 1f), showIdField ? "ID · " + id : id);
        }

        ImGui.dummy(w, h);
        ImGui.spacing();
    }

    /** Collapsible inspector section. Pair with {@link #endSection()} inside the if-block. */
    public static boolean beginSection(String id, String title, boolean defaultOpen) {
        if (id != null && id.equals(forceOpenSectionId)) {
            ImGui.setNextItemOpen(true, imgui.flag.ImGuiCond.Always);
            forceOpenSectionId = null;
        }
        int flags = ImGuiTreeNodeFlags.Framed | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (defaultOpen) flags |= ImGuiTreeNodeFlags.DefaultOpen;

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 8f, 7f);
        ImGui.pushStyleColor(ImGuiCol.Header, 0.15f, 0.17f, 0.20f, 0.95f);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.19f, 0.21f, 0.25f, 1f);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, 0.22f, 0.24f, 0.29f, 1f);
        currentSectionId = id == null ? "" : id;
        currentSectionTitle = title == null ? "" : title;
        buildingSection = new ArrayList<>();
        boolean open = ImGui.collapsingHeader(title.toUpperCase(Locale.ROOT) + "##sec-" + id, flags);
        ImGui.popStyleColor(3);
        ImGui.popStyleVar();

        sectionHeaderClipboard(id, title, open);

        if (open) {
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 6f, 5f);
            ImGui.indent(GROUP_PAD);
        } else {
            buildingSection = null;
        }
        return open;
    }

    public static void endSection() {
        if (buildingSection != null && currentSectionId != null && !currentSectionId.isBlank()) {
            sectionSnapshots.put(currentSectionId, new ArrayList<>(buildingSection));
            if (pendingSectionPasteNow || currentSectionId.equals(pendingSectionPasteId)) {
                pasteInto(buildingSection, true);
                pendingSectionPasteId = null;
                pendingSectionPasteNow = false;
            }
        }
        buildingSection = null;
        currentSectionId = "";
        currentSectionTitle = "";
        ImGui.unindent(GROUP_PAD);
        ImGui.popStyleVar();
        ImGui.spacing();
    }

    public static void fieldLabel(String text) {
        ImGui.textColored(0.58f, 0.62f, 0.68f, 1f, text);
        attachClipLabel(text, null);
        if (resetAction != null) trailingFieldButtons(text, null);
    }

    /** Field label with hover tooltip showing the matching script API. */
    public static void fieldLabel(String text, String scriptApi) {
        fieldLabel(text, null, scriptApi);
    }

    /** Field label with description + script API on hover. */
    public static void fieldLabel(String text, String description, String scriptApi) {
        ImGui.textColored(0.58f, 0.62f, 0.68f, 1f, text);
        attachClipLabel(text, scriptApi);
        scriptApi(description, scriptApi);
        trailingFieldButtons(text, scriptApi);
    }

    private static boolean hasAnimButton(String scriptApi) {
        return animationFieldAction != null && scriptApi != null && scriptApi.contains("_node.");
    }

    private static void trailingFieldButtons(String text, String scriptApi) {
        boolean hasReset = resetAction != null;
        boolean hasAnim = hasAnimButton(scriptApi);
        if (!hasReset && !hasAnim) return;

        float size = 14f;
        float btn = size + 4f;
        float gap = 2f;
        int n = (hasReset ? 1 : 0) + (hasAnim ? 1 : 0);
        float total = n * btn + Math.max(0, n - 1) * gap;

        ImGui.sameLine();
        float x = ImGui.getCursorPosX();
        float right = ImGui.getWindowContentRegionMaxX() - total;
        if (right > x) ImGui.setCursorPosX(right);

        if (hasReset) {
            drawResetButton(text, size);
            if (hasAnim) ImGui.sameLine(0f, gap);
        }
        if (hasAnim) {
            drawAnimButton(text, scriptApi, size);
        }
    }

    private static void drawResetButton(String text, float size) {
        Runnable action = resetAction;
        boolean modified = resetModified;
        resetAction = null;
        resetModified = false;
        if (action == null) return;

        int id = resetSeq++;
        ImGui.pushID("reset-" + id + "-" + (text == null ? "" : text));
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 2f, 2f);
        float a = modified ? 1f : 0.35f;
        if (modified) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.22f, 0.16f, 0.10f, 0.85f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.34f, 0.24f, 0.12f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.42f, 0.30f, 0.14f, 1f);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.07f, 0.08f, 0.09f, 0.55f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.11f, 0.12f, 0.14f, 0.85f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.14f, 0.15f, 0.18f, 1f);
        }
        boolean clicked;
        var tex = org.zonarstudio.spraute_engine.client.editor.icons.EditorIcons.get("undo-2");
        if (tex != null) {
            float tr = modified ? 0.98f : 0.50f;
            float tg = modified ? 0.78f : 0.52f;
            float tb = modified ? 0.42f : 0.56f;
            clicked = ImGui.imageButton("##rst", tex.glId(), size, size,
                    0f, 0f, 1f, 1f,
                    0f, 0f, 0f, 0f,
                    tr, tg, tb, a);
        } else {
            clicked = ImGui.smallButton("↺");
        }
        ImGui.popStyleColor(3);
        ImGui.popStyleVar();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(modified
                    ? EditorI18n.t("Сбросить к первоначальному")
                    : EditorI18n.t("Уже первоначальное значение"));
        }
        if (clicked) action.run();
        ImGui.popID();
    }

    private static void drawAnimButton(String text, String scriptApi, float size) {
        ImGui.pushID("animate-" + text + "-" + scriptApi);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 2f, 2f);
        ImGui.pushStyleColor(ImGuiCol.Button, 0.07f, 0.08f, 0.09f, 0.75f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.11f, 0.12f, 0.14f, 0.95f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.14f, 0.15f, 0.18f, 1f);
        boolean clicked;
        var tex = org.zonarstudio.spraute_engine.client.editor.icons.EditorIcons.get("rhomb");
        if (tex != null) {
            clicked = ImGui.imageButton("##rhomb", tex.glId(), size, size,
                    0f, 0f, 1f, 1f,
                    0f, 0f, 0f, 0f,
                    0.52f, 0.55f, 0.60f, 1f);
        } else {
            clicked = ImGui.smallButton("◆");
        }
        ImGui.popStyleColor(3);
        ImGui.popStyleVar();
        if (clicked) animationFieldAction.add(text, scriptApi);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(EditorI18n.t("Добавить параметр в анимацию на текущем кадре"));
        }
        ImGui.popID();
    }

    public static void hint(String text) {
        ImGui.textColored(0.48f, 0.51f, 0.56f, 1f, text);
    }

    /**
     * Hover tooltip for the previous ImGui item: how to read/write this in a .spr script.
     * Example: {@code _node.model = "geo/foo.geo.json"}
     */
    public static void scriptApi(String code) {
        scriptApi(null, code);
    }

    /**
     * Hover tooltip: optional human description (PROP_HINTS-style) + script API lines.
     */
    public static void scriptApi(String description, String code) {
        boolean hasDesc = description != null && !description.isBlank();
        boolean hasCode = code != null && !code.isBlank();
        boolean hasClip = pendingClip != null;
        if (!hasDesc && !hasCode && !hasClip) return;
        if (!ImGui.isItemHovered()) return;
        ImGui.beginTooltip();
        ImGui.pushTextWrapPos(ImGui.getFontSize() * 34f);
        if (hasDesc) {
            ImGui.textColored(0.82f, 0.85f, 0.90f, 1f, description);
            if (hasCode || hasClip) ImGui.separator();
        }
        if (hasCode) {
            ImGui.textColored(0.55f, 0.72f, 0.95f, 1f, EditorI18n.t("В скрипте"));
            ImGui.separator();
            for (String line : code.split("\n", -1)) {
                if (line.isEmpty()) ImGui.spacing();
                else ImGui.textColored(0.88f, 0.92f, 0.78f, 1f, line);
            }
        }
        if (hasClip) {
            if (hasDesc || hasCode) ImGui.separator();
            ImGui.textColored(0.62f, 0.66f, 0.72f, 1f, EditorI18n.t("ПКМ — копировать / вставить параметр"));
        }
        ImGui.popTextWrapPos();
        ImGui.endTooltip();
    }

    public static boolean vector3(String id, float[] values, float speed, float min, float max) {
        return vector3(id, values, speed, min, max, null);
    }

    public static boolean vector3(String id, float[] values, float speed, float min, float max,
                                  Runnable onActivate) {
        boolean changed = false;
        changed |= axisDrag(id + "-x", Axis.X, values, 0, speed, min, max, onActivate);
        changed |= axisDrag(id + "-y", Axis.Y, values, 1, speed, min, max, onActivate);
        changed |= axisDrag(id + "-z", Axis.Z, values, 2, speed, min, max, onActivate);
        return changed;
    }

    public static boolean vector2(String id, Axis axisA, Axis axisB, float[] values,
                                   float speed, float min, float max) {
        boolean changed = false;
        changed |= axisDrag(id + "-a", axisA, values, 0, speed, min, max);
        changed |= axisDrag(id + "-b", axisB, values, 1, speed, min, max);
        return changed;
    }

    public static boolean axisDrag(String id, Axis axis, float[] values, int index,
                                   float speed, float min, float max) {
        return axisDrag(id, axis, values, index, speed, min, max, null);
    }

    private static boolean axisDrag(String id, Axis axis, float[] values, int index,
                                    float speed, float min, float max, Runnable onActivate) {
        ImGui.pushID(id);
        float rowH = ImGui.getFrameHeight();

        ImGui.pushStyleColor(ImGuiCol.Button,
                axis.r * 0.22f + 0.10f, axis.g * 0.22f + 0.10f, axis.b * 0.22f + 0.10f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered,
                axis.r * 0.30f + 0.12f, axis.g * 0.30f + 0.12f, axis.b * 0.30f + 0.12f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive,
                axis.r * 0.38f + 0.14f, axis.g * 0.38f + 0.14f, axis.b * 0.38f + 0.14f, 1f);
        ImGui.button(axis.label, AXIS_BADGE_W, rowH);
        ImGui.popStyleColor(3);

        ImGui.sameLine();
        ImGui.setNextItemWidth(Math.max(48f, ImGui.getContentRegionAvailX()));
        float[] slot = {values[index]};
        ImGui.pushStyleColor(ImGuiCol.FrameBg,
                axis.r * 0.12f + 0.08f, axis.g * 0.12f + 0.08f, axis.b * 0.12f + 0.08f, 1f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered,
                axis.r * 0.18f + 0.10f, axis.g * 0.18f + 0.10f, axis.b * 0.18f + 0.10f, 1f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive,
                axis.r * 0.24f + 0.12f, axis.g * 0.24f + 0.12f, axis.b * 0.24f + 0.12f, 1f);
        boolean changed = ImGui.dragFloat("##val", slot, speed, min, max, "%.3f");
        if (onActivate != null && ImGui.isItemActivated()) onActivate.run();
        ImGui.popStyleColor(3);
        if (changed) values[index] = slot[0];
        ImGui.popID();
        return changed;
    }

    public static boolean dragFloat(String label, String id, float[] value,
                                    float speed, float min, float max) {
        return dragFloat(label, id, value, speed, min, max, null);
    }

    public static boolean dragFloat(String label, String id, float[] value,
                                    float speed, float min, float max, String scriptApi) {
        return dragFloat(label, id, value, speed, min, max, null, scriptApi);
    }

    public static boolean dragFloat(String label, String id, float[] value,
                                    float speed, float min, float max,
                                    String description, String scriptApi) {
        fieldLabel(label, description, scriptApi);
        ImGui.setNextItemWidth(-1f);
        boolean changed = ImGui.dragFloat("##" + id, value, speed, min, max, "%.3f");
        scriptApi(description, scriptApi);
        finishClipboardField();
        return changed;
    }

    public static boolean inputText(String label, String id, ImString buffer) {
        return inputText(label, id, buffer, null, null);
    }

    public static boolean inputText(String label, String id, ImString buffer, String scriptApi) {
        return inputText(label, id, buffer, null, scriptApi);
    }

    public static boolean inputText(String label, String id, ImString buffer,
                                    String description, String scriptApi) {
        fieldLabel(label, description, scriptApi);
        ImGui.setNextItemWidth(-1f);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.12f, 0.13f, 0.16f, 1f);
        boolean changed = ImGui.inputText("##" + id, buffer);
        ImGui.popStyleColor();
        scriptApi(description, scriptApi);
        finishClipboardField();
        return changed;
    }

    public static boolean checkbox(String label, boolean value) {
        return checkbox(label, value, null, null);
    }

    public static boolean checkbox(String label, boolean value, String scriptApi) {
        return checkbox(label, value, null, scriptApi);
    }

    public static boolean checkbox(String label, boolean value,
                                   String description, String scriptApi) {
        ImGui.pushStyleColor(ImGuiCol.CheckMark, 0.41f, 0.66f, 1f, 1f);
        boolean clicked = ImGui.checkbox(label, value);
        ImGui.popStyleColor();
        attachClipLabel(label, scriptApi);
        scriptApi(description, scriptApi);
        trailingFieldButtons(label, scriptApi);
        finishClipboardField();
        return clicked;
    }

    /** Tag chips that wrap to the next line as needed. */
    public static void tagFlow(List<String> tags, IntConsumer onRemove) {
        if (tags.isEmpty()) return;
        float rowStartX = ImGui.getCursorPosX();
        float maxX = rowStartX + ImGui.getContentRegionAvailX();
        float spacing = 6f;

        for (int i = 0; i < tags.size(); i++) {
            String tag = tags.get(i);
            float chipW = ImGui.calcTextSizeX(tag) + 34f;
            float removeW = 22f;
            float totalW = chipW + removeW + spacing;

            if (ImGui.getCursorPosX() + totalW > maxX && ImGui.getCursorPosX() > rowStartX + 1f) {
                ImGui.newLine();
            }

            ImGui.pushStyleColor(ImGuiCol.Button, 0.18f, 0.30f, 0.48f, 0.55f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.22f, 0.36f, 0.56f, 0.75f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.26f, 0.42f, 0.64f, 0.9f);
            ImGui.button(tag + "##tag-" + i, 0f, 0f);
            ImGui.popStyleColor(3);

            ImGui.sameLine(0f, 2f);
            ImGui.pushStyleColor(ImGuiCol.Button, 0.35f, 0.18f, 0.18f, 0.55f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.55f, 0.22f, 0.22f, 0.85f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.70f, 0.25f, 0.25f, 1f);
            if (ImGui.button("×##rm-tag-" + i, removeW, 0f)) {
                onRemove.accept(i);
                return;
            }
            ImGui.popStyleColor(3);
            ImGui.sameLine(0f, spacing);
        }
        ImGui.newLine();
    }

    public static void softSeparator() {
        ImGui.spacing();
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY() + 2f;
        float w = ImGui.getContentRegionAvailX();
        ImGui.getWindowDrawList().addLine(x, y, x + w, y, rgba(0.45f, 0.50f, 0.58f, 0.22f), 1f);
        ImGui.spacing();
    }

    public static boolean primaryButton(String label, float width) {
        ImGui.pushStyleColor(ImGuiCol.Button, 0.18f, 0.32f, 0.52f, 0.85f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.24f, 0.42f, 0.66f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.30f, 0.50f, 0.78f, 1f);
        boolean pressed = width > 0f ? ImGui.button(label, width, 0f) : ImGui.button(label);
        ImGui.popStyleColor(3);
        return pressed;
    }

    public static void undoOnActivate() {
        if (ImGui.isItemActivated()) SceneEditorSession.pushUndo();
    }

    /** Call after a custom inspector widget that follows {@link #fieldLabel}. */
    public static void finishClipboardField() {
        clipItemUi();
        pendingClip = null;
    }

    private static void attachClipLabel(String text, String scriptApi) {
        if (pendingClip != null) {
            pendingClip.label = clipKey(text);
            pendingClip.scriptApi = scriptApi;
        }
        clipItemUi();
    }

    private static void clipItemUi() {
        if (pendingClip == null) return;
        boolean hovered = ImGui.isItemHovered();
        if (hovered && ImGui.isMouseClicked(1)) {
            ImGui.openPopup("##insp-field-clip");
        }
        VisualScriptEditor.pushMenuStyle();
        if (ImGui.beginPopup("##insp-field-clip")) {
            FieldClip clip = pendingClip;
            if (ImGui.menuItem(EditorI18n.t("Копировать параметр"))) {
                if (clip != null) copyField(clip);
            }
            boolean canPaste = canPasteInto(clip);
            if (ImGui.menuItem(EditorI18n.t("Вставить параметр"), "", false, canPaste)) {
                if (clip != null) pasteInto(List.of(clip), false);
            }
            ImGui.endPopup();
        }
        VisualScriptEditor.popMenuStyle();
    }

    private static void sectionHeaderClipboard(String id, String title, boolean open) {
        List<FieldClip> snap = snapshotFor(id);
        boolean hovered = ImGui.isItemHovered();
        if (hovered && ImGui.isMouseClicked(1)) {
            ImGui.openPopup("##insp-sec-clip-" + id);
        }

        boolean canCopy = snap != null && !snap.isEmpty();
        VisualScriptEditor.pushMenuStyle();
        if (ImGui.beginPopup("##insp-sec-clip-" + id)) {
            if (ImGui.menuItem(EditorI18n.t("Копировать раздел"), "", false, canCopy)) {
                if (canCopy) copySection(id, title, snap);
            }
            if (ImGui.menuItem(EditorI18n.t("Вставить раздел"), "", false, clipboard != null)) {
                requestSectionPaste(id, open);
            }
            ImGui.endPopup();
        }
        VisualScriptEditor.popMenuStyle();
    }

    private static List<FieldClip> snapshotFor(String id) {
        return sectionSnapshots.get(id);
    }

    private static void requestSectionPaste(String id, boolean open) {
        if (clipboard == null) return;
        if (open) pendingSectionPasteNow = true;
        else {
            pendingSectionPasteId = id;
            forceOpenSectionId = id;
        }
    }

    private static void copyField(FieldClip clip) {
        if (clip == null) return;
        clipboard = Clipboard.field(clip);
        String text = clip.scriptApi != null && !clip.scriptApi.isBlank()
                ? clip.scriptApi
                : String.valueOf(clip.current == null ? "" : clip.current);
        ImGui.setClipboardText(text);
        flash(EditorI18n.t("Скопировано"));
    }

    private static void copySection(String id, String title, List<FieldClip> fields) {
        if (fields == null || fields.isEmpty()) return;
        clipboard = Clipboard.section(id, title, fields);
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) sb.append("# ").append(title).append('\n');
        for (FieldClip f : fields) {
            if (f.scriptApi != null && !f.scriptApi.isBlank()) {
                sb.append(f.scriptApi).append('\n');
            } else if (f.label != null) {
                sb.append(f.label).append(" = ").append(f.current).append('\n');
            }
        }
        ImGui.setClipboardText(sb.toString().trim());
        flash(EditorI18n.t("Раздел скопирован"));
    }

    private static boolean canPasteInto(FieldClip target) {
        if (target == null || clipboard == null || clipboard.entries.isEmpty()) return false;
        if (clipboard.section) {
            for (ClipEntry e : clipboard.entries) {
                if (clipKey(target.label).equals(e.key) && coerce(target.current, e.value) != COERCE_FAIL) {
                    return true;
                }
            }
            return clipboard.entries.size() == 1
                    && coerce(target.current, clipboard.entries.get(0).value) != COERCE_FAIL;
        }
        return coerce(target.current, clipboard.entries.get(0).value) != COERCE_FAIL;
    }

    private static void pasteInto(List<FieldClip> targets, boolean sectionPaste) {
        if (targets == null || targets.isEmpty() || clipboard == null) return;
        boolean any = false;
        if (sectionPaste || clipboard.section) {
            Map<String, Object> byKey = new LinkedHashMap<>();
            for (ClipEntry e : clipboard.entries) byKey.put(e.key, e.value);
            for (FieldClip t : targets) {
                String key = clipKey(t.label);
                if (!byKey.containsKey(key)) continue;
                Object coerced = coerce(t.current, byKey.get(key));
                if (coerced == COERCE_FAIL) continue;
                t.apply.accept(coerced);
                any = true;
            }
        } else {
            FieldClip t = targets.get(0);
            Object coerced = coerce(t.current, clipboard.entries.get(0).value);
            if (coerced != COERCE_FAIL) {
                t.apply.accept(coerced);
                any = true;
            }
        }
        if (any) flash(EditorI18n.t("Вставлено"));
        else flash(EditorI18n.t("Нечего вставлять"));
    }

    private static final Object COERCE_FAIL = new Object();

    private static Object coerce(Object current, Object incoming) {
        if (incoming == null) return current instanceof String ? "" : incoming;
        if (current instanceof Boolean) {
            if (incoming instanceof Boolean b) return b;
            if (incoming instanceof Number n) return n.doubleValue() != 0;
            String s = String.valueOf(incoming).trim();
            if ("true".equalsIgnoreCase(s) || "1".equals(s)) return true;
            if ("false".equalsIgnoreCase(s) || "0".equals(s)) return false;
            return COERCE_FAIL;
        }
        if (current instanceof Number) {
            if (incoming instanceof Number n) {
                if (current instanceof Integer || current instanceof Long) return n.intValue();
                return n.floatValue();
            }
            try {
                double d = Double.parseDouble(String.valueOf(incoming).trim());
                if (current instanceof Integer || current instanceof Long) return (int) Math.round(d);
                return (float) d;
            } catch (Exception e) {
                return COERCE_FAIL;
            }
        }
        return String.valueOf(incoming);
    }

    private static String clipKey(String label) {
        if (label == null) return "";
        String s = label.trim();
        if (s.endsWith(" *")) s = s.substring(0, s.length() - 2).trim();
        return s;
    }

    private static void flash(String text) {
        clipFlash = text;
        clipFlashUntil = System.currentTimeMillis() + 1400L;
    }

    private static void drawClipFlash() {
        if (clipFlash == null) return;
        if (System.currentTimeMillis() > clipFlashUntil) {
            clipFlash = null;
            return;
        }
        ImGui.textColored(0.45f, 0.85f, 0.55f, 1f, clipFlash);
    }

    private static final class FieldClip {
        String label;
        String scriptApi;
        final Object current;
        final Consumer<Object> apply;

        FieldClip(String label, String scriptApi, Object current, Consumer<Object> apply) {
            this.label = clipKey(label);
            this.scriptApi = scriptApi;
            this.current = current;
            this.apply = apply;
        }
    }

    private static final class ClipEntry {
        final String key;
        final Object value;

        ClipEntry(String key, Object value) {
            this.key = key == null ? "" : key;
            this.value = value;
        }
    }

    private static final class Clipboard {
        final boolean section;
        final List<ClipEntry> entries;

        private Clipboard(boolean section, List<ClipEntry> entries) {
            this.section = section;
            this.entries = entries;
        }

        static Clipboard field(FieldClip clip) {
            return new Clipboard(false, List.of(new ClipEntry(clipKey(clip.label), copyValue(clip.current))));
        }

        static Clipboard section(String id, String title, List<FieldClip> fields) {
            List<ClipEntry> entries = new ArrayList<>();
            for (FieldClip f : fields) {
                entries.add(new ClipEntry(clipKey(f.label), copyValue(f.current)));
            }
            return new Clipboard(true, entries);
        }

        private static Object copyValue(Object v) {
            if (v instanceof Number n) return n.doubleValue();
            return v;
        }
    }

    private static int rgba(float r, float g, float b, float a) {
        return ImGui.colorConvertFloat4ToU32(r, g, b, a);
    }
}
