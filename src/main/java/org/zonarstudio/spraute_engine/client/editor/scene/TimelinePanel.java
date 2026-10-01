package org.zonarstudio.spraute_engine.client.editor.scene;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;
import org.zonarstudio.spraute_engine.client.editor.icons.EditorIcons;
import org.zonarstudio.spraute_engine.core.model.SpBone;
import org.zonarstudio.spraute_engine.core.model.SpGeoModel;
import org.zonarstudio.spraute_engine.entity.client.SpModelCache;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;

/**
 * Animation timeline: clip transport, a dope-sheet of bone and inspector-property
 * lanes, and a key/pose inspector for the animator opened via
 * {@link AnimatorTimelineSession}.
 *
 * Horizontal navigation is ours ({@link #viewStart} + {@link #pxPerSec}), not an
 * ImGui scrollbar, so the track-header column can stay pinned while the lanes pan.
 * Everything inside the dope sheet is painted through the window draw list over a
 * single dummy item — never move the ImGui cursor manually there, that extends the
 * window and trips the native SetCursorPos assert inside endChild().
 */
public final class TimelinePanel {
    private static final float RULER_H = 30f;
    private static final float ROW_H = 26f;
    private static final float SUB_H = 19f;
    private static final float NAV_H = 14f;
    private static final float MIN_HEADER_W = 150f;
    private static final float MAX_HEADER_W = 420f;
    private static final float KEY_HIT_R = 6.5f;
    private static final float MIN_PX_PER_SEC = 6f;
    private static final float MAX_PX_PER_SEC = 6000f;
    private static final float[] SPEEDS = {0.1f, 0.25f, 0.5f, 1f, 1.5f, 2f, 4f};
    private static final float[] RULER_STEPS = {
            0.005f, 0.01f, 0.02f, 0.025f, 0.05f, 0.1f, 0.2f, 0.25f, 0.5f,
            1f, 2f, 5f, 10f, 15f, 30f, 60f, 120f, 300f};

    private static final int COL_PANEL = rgba(19, 20, 24, 255);
    private static final int COL_HEADER_BG = rgba(26, 28, 33, 255);
    private static final int COL_TRACK_BG = rgba(21, 22, 26, 255);
    private static final int COL_ROW_ALT = rgba(255, 255, 255, 7);
    private static final int COL_ROW_HOVER = rgba(255, 255, 255, 13);
    private static final int COL_ROW_SEL = rgba(255, 158, 44, 28);
    private static final int COL_GROUP_BG = rgba(255, 255, 255, 11);
    private static final int COL_SPLIT = rgba(0, 0, 0, 170);
    private static final int COL_SPLIT_HL = rgba(255, 158, 44, 200);
    private static final int COL_RULER_BG = rgba(30, 32, 38, 255);
    private static final int COL_GRID_MINOR = rgba(255, 255, 255, 9);
    private static final int COL_GRID_MAJOR = rgba(255, 255, 255, 24);
    private static final int COL_TEXT = rgba(226, 229, 235, 255);
    private static final int COL_TEXT_DIM = rgba(139, 146, 158, 255);
    private static final int COL_TEXT_MUTED = rgba(96, 102, 113, 255);
    private static final int COL_ACCENT = rgba(255, 158, 44, 255);
    private static final int COL_BEYOND = rgba(0, 0, 0, 105);
    private static final int COL_ROT = rgba(124, 216, 143, 255);
    private static final int COL_POS = rgba(255, 107, 107, 255);
    private static final int COL_SCL = rgba(99, 168, 250, 255);
    private static final int COL_PROP = rgba(199, 146, 234, 255);
    private static final int COL_SUMMARY = rgba(203, 210, 222, 255);
    private static final int[] CHANNEL_COLORS = {COL_ROT, COL_POS, COL_SCL};

    private static final int KIND_BONE_GROUP = 0;
    private static final int KIND_BONE_LANE = 1;
    private static final int KIND_PROP_GROUP = 2;
    private static final int KIND_PROP_LANE = 3;

    private static final int DRAG_NONE = 0;
    private static final int DRAG_KEYS = 1;
    private static final int DRAG_BOX = 2;
    private static final int DRAG_PAN = 3;
    private static final int DRAG_SCRUB = 4;
    private static final int DRAG_SPLIT = 5;
    private static final int DRAG_NAV = 6;

    private final ImString newClipName = new ImString(64);
    private final ImString renameClipBuf = new ImString(64);
    private final ImString trackFilter = new ImString(64);
    private final ImString boneFilter = new ImString(64);
    private final ImString propTextBuf = new ImString(256);
    private final float[] vec3 = new float[3];
    private final float[] single = new float[1];

    private float headerW = 218f;
    private float pxPerSec = 180f;
    private float viewStart;
    private boolean fitRequested = true;
    private String lastFittedClip = "";
    private boolean showInspector = true;

    private final Set<String> collapsed = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Lane> laneById = new LinkedHashMap<>();
    private final LinkedHashSet<KeyId> selection = new LinkedHashSet<>();
    private final List<ClipboardEntry> clipboard = new ArrayList<>();

    private int dragMode = DRAG_NONE;
    private boolean dragUndoCaptured;
    private float dragAnchorTime;
    private float dragLastOffset;
    private final Map<KeyId, Float> dragOrigin = new LinkedHashMap<>();
    private float boxAx;
    private float boxAy;
    private float boxBx;
    private float boxBy;
    private float panAnchorX;
    private float panAnchorView;
    private float navGrabOffset;
    private String contextLane;
    private float contextTime;
    private boolean openKeyMenu;
    private String propBufKey = "";

    // ------------------------------------------------------------------ model

    /** One editable stream of keyframes: a bone channel or an inspector property. */
    private static final class Lane {
        final String id;
        final boolean property;
        final String bone;
        final int channel;
        final String targetId;
        final String propId;
        final int color;

        Lane(String bone, int channel) {
            this.id = "b|" + bone + "|" + channel;
            this.property = false;
            this.bone = bone;
            this.channel = channel;
            this.targetId = null;
            this.propId = null;
            this.color = CHANNEL_COLORS[channel];
        }

        Lane(String targetId, String propId) {
            this.id = "p|" + targetId + "|" + propId;
            this.property = true;
            this.bone = null;
            this.channel = -1;
            this.targetId = targetId;
            this.propId = propId;
            this.color = COL_PROP;
        }
    }

    private record KeyId(String lane, float time) {}

    private static final class ClipboardEntry {
        String laneId;
        float offset;
        int lerp;
        float[] vector;
        JsonElement value;
    }

    private static final class Row {
        String id;
        int kind;
        String label;
        String sublabel;
        String bone;
        int channel = -1;
        String targetId;
        String propId;
        int color = COL_TEXT_DIM;
        float y;
        float height;
        final List<Lane> lanes = new ArrayList<>();

        boolean group() {
            return kind == KIND_BONE_GROUP || kind == KIND_PROP_GROUP;
        }
    }

    // ----------------------------------------------------------------- render

    public void render() {
        AnimatorTimelineSession.tickPlayback();

        if (!AnimatorTimelineSession.isOpen()) {
            renderEmptyState();
            return;
        }

        SceneNode target = AnimatorTimelineSession.targetNode();
        EditorAnimationDocument doc = AnimatorTimelineSession.document();
        renderToolbar(doc, target);

        EditorAnimationDocument.Clip clip = AnimatorTimelineSession.clip();
        if (clip == null) {
            ImGui.spacing();
            ImGui.textDisabled(EditorI18n.t("Создайте клип, чтобы редактировать ключи"));
            renderError();
            return;
        }
        if (!AnimatorTimelineSession.clipName().equals(lastFittedClip)) {
            lastFittedClip = AnimatorTimelineSession.clipName();
            renameClipBuf.set(lastFittedClip);
            fitRequested = true;
            selection.clear();
        }

        renderTransport(clip, target);
        buildRows(clip, target);

        float avail = ImGui.getContentRegionAvailY();
        // A short window keeps the dope sheet; the inspector is what gets dropped.
        boolean inspector = showInspector && avail > 300f;
        float bodyH = Math.max(120f, avail - (inspector ? 144f : 0f));
        renderBody(clip, bodyH);
        if (inspector) renderInspector(clip, target);
        handleShortcuts(clip);
        renderError();
    }

    private void renderError() {
        String error = AnimatorTimelineSession.lastError();
        if (error != null) ImGui.textColored(1f, .42f, .42f, 1f, error);
    }

    private void renderEmptyState() {
        float w = ImGui.getContentRegionAvailX();
        float h = Math.max(90f, ImGui.getContentRegionAvailY() - 4f);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, .078f, .082f, .096f, 1f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 8f);
        if (ImGui.beginChild("##tl-empty", w, h, true)) {
            ImGui.dummy(1f, Math.max(6f, h * 0.28f));
            centeredIcon("anim", 34f);
            centeredText(EditorI18n.t("ТАЙМЛАЙН АНИМАЦИЙ"), COL_TEXT);
            ImGui.dummy(1f, 6f);
            centeredText(EditorI18n.t("Выберите любой объект и нажмите ◆ у параметра в инспекторе."), COL_TEXT_DIM);
            centeredText(EditorI18n.t("Нода «Аниматор», файл .animation.json и первый ключ создадутся автоматически."),
                    COL_TEXT_MUTED);
        if (AnimatorTimelineSession.lastError() != null) {
                ImGui.dummy(1f, 8f);
                centeredText(AnimatorTimelineSession.lastError(), rgba(255, 107, 107, 255));
            }
        }
        ImGui.endChild();
        ImGui.popStyleVar();
        ImGui.popStyleColor();
    }

    private static void centeredText(String text, int color) {
        float w = ImGui.calcTextSize(text).x;
        float x = ImGui.getCursorScreenPosX() + Math.max(0f, (ImGui.getContentRegionAvailX() - w) * 0.5f);
        float y = ImGui.getCursorScreenPosY();
        ImGui.getWindowDrawList().addText(x, y, color, text);
        ImGui.dummy(1f, ImGui.getTextLineHeightWithSpacing());
    }

    private static void centeredIcon(String icon, float size) {
        float x = Math.max(0f, (ImGui.getContentRegionAvailX() - size) * 0.5f);
        ImGui.dummy(x, 1f);
        ImGui.sameLine();
        EditorIcons.image(icon, size, .55f, .60f, .70f, 1f);
    }

    // ---------------------------------------------------------------- toolbar

    private void renderToolbar(EditorAnimationDocument doc, SceneNode target) {
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 7f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8f, 5f);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, .107f, .113f, .131f, 1f);
        ImGui.beginChild("##tl-toolbar", 0f, ImGui.getFrameHeight() + 12f, true,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);

        EditorIcons.image("anim", 15f, 1f, .62f, .17f, 1f);
        ImGui.sameLine();
        ImGui.alignTextToFramePadding();
        ImGui.textColored(1f, .69f, .28f, 1f, shortPath(doc.workspacePath));
        if (ImGui.isItemHovered()) ImGui.setTooltip(doc.workspacePath);
        if (target != null) {
            ImGui.sameLine();
            ImGui.textDisabled("·  " + target.name);
        }

        toolbarSeparator();
        ImGui.setNextItemWidth(196f);
        String current = AnimatorTimelineSession.clipName().isEmpty()
                ? EditorI18n.t("(клип)") : AnimatorTimelineSession.clipName();
        if (ImGui.beginCombo("##clip", current)) {
            int clipIndex = 0;
            for (String name : new ArrayList<>(doc.clips.keySet())) {
                EditorAnimationDocument.Clip entry = doc.clips.get(name);
                ImGui.pushID(clipIndex++);
                if (ImGui.selectable(name.replace("#", "*") + "##c",
                        name.equals(AnimatorTimelineSession.clipName()))) {
                    AnimatorTimelineSession.selectClip(name);
                }
                ImGui.popID();
                if (entry != null) {
                    ImGui.sameLine();
                    ImGui.textDisabled(String.format(Locale.ROOT, "  %.2f c · %d %s",
                            entry.lengthSec, entry.bones.size(), EditorI18n.t("кост.")));
                }
            }
            ImGui.endCombo();
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("plus", "tl-clip-add", 14f, EditorI18n.t("Новый клип"))) {
            ImGui.openPopup("##tl-new-clip");
        }
        if (ImGui.beginPopup("##tl-new-clip")) {
            ImGui.textDisabled(EditorI18n.t("Имя нового клипа"));
            ImGui.setNextItemWidth(230f);
            boolean submit = ImGui.inputTextWithHint("##name", "animation.walk", newClipName,
                    imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue);
        ImGui.sameLine();
            if (ImGui.button(EditorI18n.t("Создать")) || submit) {
            String name = newClipName.get().trim().replaceAll("[^A-Za-z0-9_.\\-]+", "_");
            if (!name.isEmpty() && !doc.clips.containsKey(name)) {
                AnimatorTimelineSession.pushUndo();
                doc.clips.put(name, new EditorAnimationDocument.Clip());
                AnimatorTimelineSession.selectClip(name);
                newClipName.set("");
                    ImGui.closeCurrentPopup();
            }
            }
            ImGui.endPopup();
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("copy-plus", "tl-clip-dup", 14f, EditorI18n.t("Дублировать клип"))) {
            AnimatorTimelineSession.duplicateClip();
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("trash-2", "tl-clip-del", 14f, EditorI18n.t("Удалить клип"),
                .95f, .48f, .48f, 1f) && AnimatorTimelineSession.clip() != null) {
            AnimatorTimelineSession.pushUndo();
            doc.clips.remove(AnimatorTimelineSession.clipName());
            AnimatorTimelineSession.selectClip(AnimatorTimelineSession.pickDefaultClip(doc, null));
            selection.clear();
        }

        float buttons = 5f * (14f + 12f) + 4f * ImGui.getStyle().getItemSpacingX();
        float rightX = ImGui.getWindowContentRegionMaxX() - buttons;
        if (rightX > ImGui.getCursorPosX() + 16f) {
        ImGui.sameLine();
            ImGui.setCursorPosX(rightX);
        } else {
            toolbarSeparator();
        }
        boolean canUndo = AnimatorTimelineSession.canUndo();
        if (EditorIcons.iconButton("undo-2", "tl-undo", 14f, EditorI18n.t("Отменить (Ctrl+Z)"),
                .86f, .88f, .92f, canUndo ? 1f : .35f) && canUndo) {
            AnimatorTimelineSession.undo();
            selection.clear();
        }
        ImGui.sameLine();
        boolean canRedo = AnimatorTimelineSession.canRedo();
        if (EditorIcons.iconButton("redo-2", "tl-redo", 14f, EditorI18n.t("Повторить (Ctrl+Y)"),
                .86f, .88f, .92f, canRedo ? 1f : .35f) && canRedo) {
            AnimatorTimelineSession.redo();
            selection.clear();
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("save", "tl-save", 14f, EditorI18n.t("Сохранить файл (Ctrl+S)"),
                1f, .72f, .30f, 1f)) {
            AnimatorTimelineSession.save();
        }
        ImGui.sameLine();
        if (EditorIcons.iconToggleButton("panel-right", "tl-inspector", 14f,
                EditorI18n.t("Панель ключа"), showInspector)) {
            showInspector = !showInspector;
        }
        ImGui.sameLine();
        if (EditorIcons.iconButton("x", "tl-close", 14f, EditorI18n.t("Закрыть аниматор"))) {
            AnimatorTimelineSession.close();
        }

        ImGui.endChild();
        ImGui.popStyleColor();
        ImGui.popStyleVar(2);
    }

    private static void toolbarSeparator() {
        ImGui.sameLine();
        float x = ImGui.getCursorScreenPosX() + 4f;
        float y = ImGui.getCursorScreenPosY();
        ImGui.getWindowDrawList().addLine(x, y + 2f, x, y + ImGui.getFrameHeight() - 2f,
                rgba(255, 255, 255, 26));
        ImGui.dummy(9f, ImGui.getFrameHeight());
        ImGui.sameLine();
    }

    private static String shortPath(String path) {
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        return slash >= 0 && slash + 1 < path.length() ? path.substring(slash + 1) : path;
    }

    // -------------------------------------------------------------- transport

    private void renderTransport(EditorAnimationDocument.Clip clip, SceneNode target) {
        float h = 38f;
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 7f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 7f, 4f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 5f, 4f);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, .088f, .094f, .110f, 1f);
        ImGui.beginChild("##tl-transport", 0f, h, true,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);

        float bh = 26f;
        if (transportButton("tl-start", 28f, bh, Glyph.TO_START, false)) {
            AnimatorTimelineSession.setPlaying(false);
            AnimatorTimelineSession.setTime(0f);
        }
        tip(EditorI18n.t("В начало (Home)"));
        ImGui.sameLine();
        if (transportButton("tl-prev-key", 28f, bh, Glyph.PREV_KEY, false)) {
            AnimatorTimelineSession.jumpToKey(-1);
        }
        tip(EditorI18n.t("Предыдущий ключ"));
        ImGui.sameLine();
        boolean playing = AnimatorTimelineSession.isPlaying();
        if (transportButton("tl-play", 40f, bh, playing ? Glyph.PAUSE : Glyph.PLAY, true)) {
            AnimatorTimelineSession.setPlaying(!playing);
        }
        tip(playing ? EditorI18n.t("Пауза (Пробел)") : EditorI18n.t("Воспроизвести (Пробел)"));
        ImGui.sameLine();
        if (transportButton("tl-next-key", 28f, bh, Glyph.NEXT_KEY, false)) {
            AnimatorTimelineSession.jumpToKey(1);
        }
        tip(EditorI18n.t("Следующий ключ"));
        ImGui.sameLine();
        if (transportButton("tl-end", 28f, bh, Glyph.TO_END, false)) {
            AnimatorTimelineSession.setPlaying(false);
            AnimatorTimelineSession.setTime(clip.lengthSec);
        }
        tip(EditorI18n.t("В конец (End)"));

        ImGui.sameLine();
        timeReadout(clip);

        ImGui.sameLine();
        ImGui.setNextItemWidth(74f);
        single[0] = clip.lengthSec;
        boolean lengthChanged = ImGui.dragFloat("##len", single, 0.01f, 0.01f, 3600f,
                EditorI18n.t("длина %.2f"));
        if (ImGui.isItemActivated()) AnimatorTimelineSession.pushUndo();
        if (lengthChanged) clip.lengthSec = Math.max(0.01f, single[0]);
        tip(EditorI18n.t("Длина клипа в секундах"));

        ImGui.sameLine();
        if (EditorIcons.iconToggleButton("refresh-cw", "tl-loop", 14f,
                clip.loop ? EditorI18n.t("Зациклен") : EditorI18n.t("Один проход"), clip.loop)) {
            AnimatorTimelineSession.pushUndo();
            clip.loop = !clip.loop;
        }

        ImGui.sameLine();
        ImGui.setNextItemWidth(62f);
        if (ImGui.beginCombo("##speed", speedLabel(AnimatorTimelineSession.playbackSpeed()))) {
            for (float s : SPEEDS) {
                if (ImGui.selectable(speedLabel(s), s == AnimatorTimelineSession.playbackSpeed())) {
                    AnimatorTimelineSession.setPlaybackSpeed(s);
                }
            }
            ImGui.endCombo();
        }
        tip(EditorI18n.t("Скорость воспроизведения"));

        ImGui.sameLine();
        if (EditorIcons.iconToggleButton("grid-3x3", "tl-snap", 14f, snapTooltip(),
                AnimatorTimelineSession.snapMode() != AnimatorTimelineSession.SNAP_OFF)) {
            ImGui.openPopup("##tl-snap");
        }
        if (ImGui.beginPopup("##tl-snap")) {
            ImGui.textDisabled(EditorI18n.t("Привязка ключей"));
            if (ImGui.selectable(EditorI18n.t("Выключена"),
                    AnimatorTimelineSession.snapMode() == AnimatorTimelineSession.SNAP_OFF)) {
                AnimatorTimelineSession.setSnapMode(AnimatorTimelineSession.SNAP_OFF);
            }
            if (ImGui.selectable(EditorI18n.t("По кадрам"),
                    AnimatorTimelineSession.snapMode() == AnimatorTimelineSession.SNAP_FRAME)) {
                AnimatorTimelineSession.setSnapMode(AnimatorTimelineSession.SNAP_FRAME);
            }
            if (ImGui.selectable(EditorI18n.t("По полкадра"),
                    AnimatorTimelineSession.snapMode() == AnimatorTimelineSession.SNAP_HALF_FRAME)) {
                AnimatorTimelineSession.setSnapMode(AnimatorTimelineSession.SNAP_HALF_FRAME);
            }
            ImGui.separator();
            ImGui.setNextItemWidth(120f);
            single[0] = AnimatorTimelineSession.fps();
            if (ImGui.dragFloat("##fps", single, 1f, 1f, 240f, EditorI18n.t("%.0f кадр/с"))) {
                AnimatorTimelineSession.setFps(single[0]);
            }
            ImGui.endPopup();
        }

        ImGui.sameLine();
        if (EditorIcons.iconButton("crop", "tl-fit", 14f, EditorI18n.t("Вписать клип (F)"))) {
            fitRequested = true;
        }

        ImGui.sameLine();
        if (EditorIcons.iconButton("plus", "tl-add-bone", 14f, EditorI18n.t("Добавить кость на таймлайн"),
                .62f, .90f, .70f, 1f)) {
            boneFilter.set("");
            ImGui.openPopup("##tl-add-bone");
        }
        renderAddBonePopup(clip, target);

        float filterW = 150f;
        float filterX = ImGui.getWindowContentRegionMaxX() - filterW;
        if (filterX > ImGui.getCursorPosX() + 12f) {
            ImGui.sameLine();
            ImGui.setCursorPosX(filterX);
            ImGui.setNextItemWidth(filterW);
            ImGui.inputTextWithHint("##track-filter", EditorI18n.t("фильтр дорожек…"), trackFilter);
        }

        ImGui.endChild();
        ImGui.popStyleColor();
        ImGui.popStyleVar(3);
    }

    private void timeReadout(EditorAnimationDocument.Clip clip) {
        float fps = AnimatorTimelineSession.fps();
        float t = AnimatorTimelineSession.time();
        String text = String.format(Locale.ROOT, "%s / %s", timeLabel(t), timeLabel(clip.lengthSec));
        String frames = String.format(Locale.ROOT, EditorI18n.t("кадр %d / %d"),
                Math.round(t * fps), Math.round(clip.lengthSec * fps));
        float w = Math.max(ImGui.calcTextSize(text).x, ImGui.calcTextSize(frames).x) + 18f;
        float h = 26f;
        ImGui.invisibleButton("##tl-readout", w, h);
        boolean active = ImGui.isItemActive();
        ImDrawList dl = ImGui.getWindowDrawList();
        float x0 = ImGui.getItemRectMinX();
        float y0 = ImGui.getItemRectMinY();
        dl.addRectFilled(x0, y0, x0 + w, y0 + h, rgba(12, 13, 16, 255), 5f);
        dl.addRect(x0, y0, x0 + w, y0 + h, rgba(255, 255, 255, 18), 5f, 0, 1f);
        dl.addText(x0 + 9f, y0 + 2f, COL_ACCENT, text);
        dl.addText(x0 + 9f, y0 + 13f, COL_TEXT_MUTED, frames);
        if (active) {
            float delta = ImGui.getIO().getMouseDeltaX();
            if (delta != 0f) {
                AnimatorTimelineSession.setPlaying(false);
                AnimatorTimelineSession.setTime(AnimatorTimelineSession.snapTime(
                        AnimatorTimelineSession.time() + delta / Math.max(4f, pxPerSec)));
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
            ImGui.setTooltip(EditorI18n.t("Текущее время — тяните, чтобы перемотать"));
        }
    }

    private void renderAddBonePopup(EditorAnimationDocument.Clip clip, SceneNode target) {
        if (!ImGui.beginPopup("##tl-add-bone")) return;
        ImGui.textDisabled(EditorI18n.t("Кость на таймлайн"));
        ImGui.setNextItemWidth(240f);
        ImGui.inputTextWithHint("##bone-filter", EditorI18n.t("поиск…"), boneFilter);
        ImGui.separator();
        String filter = boneFilter.get().trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        if (ImGui.beginChild("##bone-list", 240f, 220f)) {
            for (String bone : boneNames(target)) {
                EditorAnimationDocument.BoneChannels ch = clip.bones.get(bone);
                if (ch != null && !ch.isEmpty()) continue;
                if (!filter.isEmpty() && !bone.toLowerCase(Locale.ROOT).contains(filter)) continue;
                shown++;
                if (ImGui.selectable(bone)) {
                    AnimatorTimelineSession.addBoneWithBaseKeys(bone);
                    ImGui.closeCurrentPopup();
                }
            }
            if (shown == 0) ImGui.textDisabled(EditorI18n.t("Все кости уже на таймлайне"));
        }
        ImGui.endChild();
        ImGui.endPopup();
    }

    private static String snapTooltip() {
        return switch (AnimatorTimelineSession.snapMode()) {
            case AnimatorTimelineSession.SNAP_FRAME -> EditorI18n.t("Привязка: по кадрам");
            case AnimatorTimelineSession.SNAP_HALF_FRAME -> EditorI18n.t("Привязка: по полкадра");
            default -> EditorI18n.t("Привязка: выключена");
        };
    }

    private static String speedLabel(float speed) {
        return (speed < 1f
                ? String.format(Locale.ROOT, "%.2f", speed).replaceAll("0+$", "").replaceAll("\\.$", "")
                : String.format(Locale.ROOT, "%.0f", speed)) + "x";
    }

    private static void tip(String text) {
        if (text != null && ImGui.isItemHovered()) ImGui.setTooltip(text);
    }

    // ------------------------------------------------------- transport glyphs

    private enum Glyph { TO_START, PREV_KEY, PLAY, PAUSE, NEXT_KEY, TO_END }

    private static boolean transportButton(String id, float w, float h, Glyph glyph, boolean accent) {
        boolean clicked = ImGui.invisibleButton("##" + id, w, h);
        boolean hovered = ImGui.isItemHovered();
        boolean held = ImGui.isItemActive();
        ImDrawList dl = ImGui.getWindowDrawList();
        float x0 = ImGui.getItemRectMinX();
        float y0 = ImGui.getItemRectMinY();
        float cx = x0 + w * 0.5f;
        float cy = y0 + h * 0.5f;
        int bg = accent
                ? (held ? rgba(214, 124, 20, 255) : hovered ? rgba(255, 172, 68, 255) : rgba(238, 148, 38, 255))
                : (held ? rgba(64, 70, 82, 255) : hovered ? rgba(52, 57, 67, 255) : rgba(38, 41, 49, 255));
        dl.addRectFilled(x0, y0, x0 + w, y0 + h, bg, 5f);
        if (!accent) dl.addRect(x0, y0, x0 + w, y0 + h, rgba(255, 255, 255, 16), 5f, 0, 1f);
        int fg = accent ? rgba(26, 18, 6, 255) : rgba(226, 231, 240, 255);
        float s = 5.2f;
        switch (glyph) {
            case TO_START -> {
                dl.addRectFilled(cx - s - 2f, cy - s, cx - s + 1.6f, cy + s, fg, 1f);
                dl.addTriangleFilled(cx + s, cy - s, cx + s, cy + s, cx - s + 2.4f, cy, fg);
            }
            case TO_END -> {
                dl.addTriangleFilled(cx - s, cy - s, cx - s, cy + s, cx + s - 2.4f, cy, fg);
                dl.addRectFilled(cx + s - 1.6f, cy - s, cx + s + 2f, cy + s, fg, 1f);
            }
            case PREV_KEY -> {
                dl.addTriangleFilled(cx + 1.5f, cy - s, cx + 1.5f, cy + s, cx - s + 0.5f, cy, fg);
                diamond(dl, cx + s, cy, 3.4f, fg);
            }
            case NEXT_KEY -> {
                dl.addTriangleFilled(cx - 1.5f, cy - s, cx - 1.5f, cy + s, cx + s - 0.5f, cy, fg);
                diamond(dl, cx - s, cy, 3.4f, fg);
            }
            case PLAY -> dl.addTriangleFilled(cx - 4f, cy - 6.5f, cx - 4f, cy + 6.5f, cx + 6.5f, cy, fg);
            case PAUSE -> {
                dl.addRectFilled(cx - 5.5f, cy - 6.5f, cx - 1.5f, cy + 6.5f, fg, 1f);
                dl.addRectFilled(cx + 1.5f, cy - 6.5f, cx + 5.5f, cy + 6.5f, fg, 1f);
            }
        }
        return clicked;
    }

    // ------------------------------------------------------------- rows model

    private void buildRows(EditorAnimationDocument.Clip clip, SceneNode target) {
        rows.clear();
        laneById.clear();
        String filter = trackFilter.get().trim().toLowerCase(Locale.ROOT);

        for (String bone : trackBones(clip, target)) {
            boolean match = filter.isEmpty() || bone.toLowerCase(Locale.ROOT).contains(filter);
            if (!match) continue;
            Row group = new Row();
            group.kind = KIND_BONE_GROUP;
            group.id = "b|" + bone;
            group.bone = bone;
            group.label = bone;
            group.height = ROW_H;
            group.color = COL_SUMMARY;
            for (int c = 0; c < 3; c++) group.lanes.add(register(new Lane(bone, c)));
            rows.add(group);
            if (collapsed.contains(group.id)) continue;
            for (int c = 0; c < 3; c++) {
                Row lane = new Row();
                lane.kind = KIND_BONE_LANE;
                lane.id = group.id + "|" + c;
                lane.bone = bone;
                lane.channel = c;
                lane.label = channelName(c);
                lane.height = SUB_H;
                lane.color = CHANNEL_COLORS[c];
                lane.lanes.add(register(new Lane(bone, c)));
                rows.add(lane);
            }
        }

        SceneDocument scene = SceneEditorSession.document();
        for (Map.Entry<String, Map<String, EditorAnimationDocument.PropertyChannel>> entry
                : clip.properties.entrySet()) {
            SceneNode node = scene == null ? null : scene.findNode(entry.getKey());
            String name = node == null || node.name == null || node.name.isBlank()
                    ? entry.getKey() : node.name;
            List<String> visible = new ArrayList<>();
            for (String property : entry.getValue().keySet()) {
                if (filter.isEmpty()
                        || property.toLowerCase(Locale.ROOT).contains(filter)
                        || name.toLowerCase(Locale.ROOT).contains(filter)) {
                    visible.add(property);
                }
            }
            if (visible.isEmpty()) continue;
            Row group = new Row();
            group.kind = KIND_PROP_GROUP;
            group.id = "p|" + entry.getKey();
            group.targetId = entry.getKey();
            group.label = name;
            group.sublabel = EditorI18n.t("параметры");
            group.height = ROW_H;
            group.color = COL_PROP;
            for (String property : visible) group.lanes.add(register(new Lane(entry.getKey(), property)));
            rows.add(group);
            if (collapsed.contains(group.id)) continue;
            for (String property : visible) {
                Row lane = new Row();
                lane.kind = KIND_PROP_LANE;
                lane.id = group.id + "|" + property;
                lane.targetId = entry.getKey();
                lane.propId = property;
                lane.label = property;
                EditorAnimationDocument.PropertyChannel channel = entry.getValue().get(property);
                lane.sublabel = channel == null ? null : channel.type;
                lane.height = SUB_H;
                lane.color = COL_PROP;
                lane.lanes.add(register(new Lane(entry.getKey(), property)));
                rows.add(lane);
            }
        }

        float y = 0f;
        for (Row row : rows) {
            row.y = y;
            y += row.height;
        }
    }

    private Lane register(Lane lane) {
        return laneById.computeIfAbsent(lane.id, ignored -> lane);
    }

    /** Track rows: bones that have keys in the clip, plus the bone selected in the hierarchy. */
    private List<String> trackBones(EditorAnimationDocument.Clip clip, SceneNode target) {
        List<String> ordered = boneNames(target);
        List<String> result = new ArrayList<>();
        if (ordered.isEmpty()) {
            result.addAll(clip.bones.keySet());
        } else {
            for (String bone : ordered) {
                EditorAnimationDocument.BoneChannels ch = clip.bones.get(bone);
                if (ch != null && !ch.isEmpty()) result.add(bone);
            }
            // Keys referencing bones missing from the geo still need to be visible.
            for (String bone : clip.bones.keySet()) {
                if (!result.contains(bone) && !clip.bones.get(bone).isEmpty()) result.add(bone);
            }
        }
        String selected = AnimatorTimelineSession.selectedBone();
        if (selected != null && !result.contains(selected)) result.add(selected);
        return result;
    }

    // ----------------------------------------------------------------- body

    private void renderBody(EditorAnimationDocument.Clip clip, float bodyH) {
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 7f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, .075f, .078f, .094f, 1f);
        ImGui.beginChild("##tl-body", 0f, bodyH, true,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);

        float w = ImGui.getContentRegionAvailX();
        float rowsH = Math.max(40f, bodyH - RULER_H - NAV_H - 4f);
        float totalRowsH = rows.isEmpty() ? 0f : rows.get(rows.size() - 1).y + rows.get(rows.size() - 1).height;
        boolean vscroll = totalRowsH > rowsH;
        float trackW = w - (vscroll ? ImGui.getStyle().getScrollbarSize() : 0f);
        headerW = Math.max(MIN_HEADER_W, Math.min(Math.min(MAX_HEADER_W, trackW - 90f), headerW));
        float areaW = Math.max(60f, trackW - headerW);

        applyView(clip, areaW);

        renderRuler(clip, w, trackW, areaW);
        renderRowsArea(clip, w, rowsH, trackW, areaW, totalRowsH);
        renderNavigator(clip, w, trackW, areaW);

        ImGui.endChild();
        ImGui.popStyleColor();
        ImGui.popStyleVar(3);
    }

    private void applyView(EditorAnimationDocument.Clip clip, float areaW) {
        float length = Math.max(0.01f, clip.lengthSec);
        if (fitRequested) {
            fitRequested = false;
            pxPerSec = clamp(areaW / (length * 1.06f), MIN_PX_PER_SEC, MAX_PX_PER_SEC);
            viewStart = -6f / pxPerSec;
        }
        pxPerSec = clamp(pxPerSec, MIN_PX_PER_SEC, MAX_PX_PER_SEC);
        float span = areaW / pxPerSec;
        float maxStart = Math.max(-6f / pxPerSec, length + span * 0.25f - span);
        viewStart = clamp(viewStart, -span * 0.5f, maxStart);
    }

    private float timeToX(float areaX, float time) {
        return areaX + (time - viewStart) * pxPerSec;
    }

    private float xToTime(float areaX, float x) {
        return viewStart + (x - areaX) / pxPerSec;
    }

    // ----------------------------------------------------------------- ruler

    private void renderRuler(EditorAnimationDocument.Clip clip, float w, float trackW, float areaW) {
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImGui.dummy(w, RULER_H);
        boolean hovered = ImGui.isItemHovered();
        float areaX = origin.x + headerW;
        ImDrawList dl = ImGui.getWindowDrawList();
        float y0 = origin.y;
        float y1 = origin.y + RULER_H;
        float length = Math.max(0.01f, clip.lengthSec);

        dl.addRectFilled(origin.x, y0, origin.x + trackW, y1, COL_RULER_BG);
        dl.addRectFilled(origin.x, y0, areaX, y1, COL_HEADER_BG);

        // Clip name badge in the header corner.
        dl.pushClipRect(origin.x + 8f, y0, areaX - 8f, y1, true);
        dl.addText(origin.x + 10f, y0 + (RULER_H - ImGui.getTextLineHeight()) * 0.5f,
                COL_TEXT_DIM, AnimatorTimelineSession.clipName());
        dl.popClipRect();

        dl.pushClipRect(areaX, y0, areaX + areaW, y1, true);
        float beyond = timeToX(areaX, length);
        if (beyond < areaX + areaW) {
            dl.addRectFilled(Math.max(areaX, beyond), y0, areaX + areaW, y1, COL_BEYOND);
        }

        float major = pickStep(56f);
        float minor = pickStep(9f);
        float fps = AnimatorTimelineSession.fps();
        float frameStep = 1f / fps;
        if (frameStep * pxPerSec >= 7f) minor = frameStep;

        float from = Math.max(0f, viewStart);
        float to = Math.min(length, viewStart + areaW / pxPerSec);
        if (minor > 0f && minor < major) {
            for (int i = (int) Math.floor(from / minor); i * minor <= to + minor; i++) {
                float t = i * minor;
                if (t < -1e-4f) continue;
                float x = timeToX(areaX, t);
                if (x < areaX - 2f || x > areaX + areaW) continue;
                dl.addLine(x, y1 - 5f, x, y1 - 1f, rgba(255, 255, 255, 34));
            }
        }
        for (int i = (int) Math.floor(from / major); i * major <= to + major; i++) {
            float t = i * major;
            if (t < -1e-4f) continue;
            float x = timeToX(areaX, t);
            if (x < areaX - 40f || x > areaX + areaW) continue;
            dl.addLine(x, y1 - 11f, x, y1 - 1f, rgba(255, 255, 255, 70));
            dl.addText(x + 4f, y0 + 2f, COL_TEXT_DIM, timeLabel(t));
        }

        // Clip end marker.
        if (beyond >= areaX - 1f && beyond <= areaX + areaW) {
            dl.addLine(beyond, y0, beyond, y1, rgba(255, 90, 90, 190), 1.5f);
        }
        dl.popClipRect();

        drawPlayheadHandle(dl, areaX, areaW, y0, y1);

        dl.addLine(origin.x, y1 - 0.5f, origin.x + trackW, y1 - 0.5f, rgba(0, 0, 0, 180));
        dl.addLine(areaX - 0.5f, y0, areaX - 0.5f, y1, COL_SPLIT);

        handleRulerInput(hovered, origin, areaX);
    }

    private void drawPlayheadHandle(ImDrawList dl, float areaX, float areaW, float y0, float y1) {
        float px = timeToX(areaX, AnimatorTimelineSession.time());
        if (px < areaX - 30f || px > areaX + areaW + 30f) return;
        String label = timeLabel(AnimatorTimelineSession.time());
        float tw = ImGui.calcTextSize(label).x;
        float badgeW = Math.min(areaW, Math.max(34f, tw + 14f));
        float bx0 = clamp(px - badgeW * 0.5f, areaX, Math.max(areaX, areaX + areaW - badgeW));
        float badgeBottom = y0 + 17f;
        dl.pushClipRect(areaX, y0, areaX + areaW, y1 + 1f, true);
        dl.addRectFilled(bx0, y0 + 1f, bx0 + badgeW, badgeBottom, COL_ACCENT, 4f);
        dl.addRectFilled(px - 1f, badgeBottom - 2f, px + 1f, y1 - 4f, COL_ACCENT);
        dl.addTriangleFilled(px - 4.5f, y1 - 6f, px + 4.5f, y1 - 6f, px, y1, COL_ACCENT);
        dl.addText(bx0 + (badgeW - tw) * 0.5f, y0 + 1f + (16f - ImGui.getTextLineHeight()) * 0.5f,
                rgba(28, 19, 4, 255), label);
        dl.popClipRect();
    }

    private void handleRulerInput(boolean hovered, ImVec2 origin, float areaX) {
        float mx = ImGui.getMousePosX();
        boolean overSplit = hovered && Math.abs(mx - areaX) <= 4f;
        if (overSplit || dragMode == DRAG_SPLIT) ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        if (overSplit && ImGui.isMouseClicked(0)) {
            dragMode = DRAG_SPLIT;
            return;
        }
        if (dragMode == DRAG_SPLIT) {
            if (ImGui.isMouseDown(0)) {
                headerW = clamp(mx - origin.x, MIN_HEADER_W, MAX_HEADER_W);
                        } else {
                dragMode = DRAG_NONE;
            }
            return;
        }
        if (hovered && mx >= areaX && ImGui.isMouseClicked(0)) {
            dragMode = DRAG_SCRUB;
            AnimatorTimelineSession.setPlaying(false);
        }
        if (dragMode == DRAG_SCRUB) {
            if (ImGui.isMouseDown(0)) {
                AnimatorTimelineSession.setTime(AnimatorTimelineSession.snapTime(xToTime(areaX, mx)));
            } else {
                dragMode = DRAG_NONE;
            }
        }
        if (hovered && ImGui.isMouseClicked(2)) {
            dragMode = DRAG_PAN;
            panAnchorX = mx;
            panAnchorView = viewStart;
        }
    }

    /** Ruler step (seconds) so that labels/ticks keep at least {@code minPx} apart. */
    private float pickStep(float minPx) {
        for (float step : RULER_STEPS) {
            if (step * pxPerSec >= minPx) return step;
        }
        return RULER_STEPS[RULER_STEPS.length - 1];
    }

    // ------------------------------------------------------------------ rows

    private void renderRowsArea(EditorAnimationDocument.Clip clip, float w, float rowsH,
                                float trackW, float areaW, float totalRowsH) {
        ImGui.beginChild("##tl-rows", w, rowsH, false, ImGuiWindowFlags.NoScrollWithMouse);
        ImVec2 origin = ImGui.getCursorScreenPos();
        float canvasH = Math.max(totalRowsH, rowsH);
        ImGui.dummy(trackW, canvasH);
        boolean hovered = ImGui.isItemHovered();
        float areaX = origin.x + headerW;
        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        ImDrawList dl = ImGui.getWindowDrawList();
        float viewTop = ImGui.getWindowPosY();
        float viewBottom = viewTop + rowsH;
        float length = Math.max(0.01f, clip.lengthSec);

        dl.addRectFilled(origin.x, origin.y, origin.x + trackW, origin.y + canvasH, COL_TRACK_BG);
        dl.addRectFilled(origin.x, origin.y, areaX, origin.y + canvasH, COL_HEADER_BG);

        if (rows.isEmpty()) {
            String text = trackFilter.get().trim().isEmpty()
                    ? EditorI18n.t("Дорожек пока нет — добавьте кость кнопкой + или ключ ◆ в инспекторе")
                    : EditorI18n.t("Ничего не найдено по фильтру");
            dl.addText(areaX + 16f, origin.y + 14f, COL_TEXT_MUTED, text);
        }

        // Vertical grid over the lane area.
        dl.pushClipRect(areaX, origin.y, areaX + areaW, origin.y + canvasH, true);
        float beyondX = timeToX(areaX, length);
        if (beyondX < areaX + areaW) {
            dl.addRectFilled(Math.max(areaX, beyondX), origin.y, areaX + areaW, origin.y + canvasH, COL_BEYOND);
        }
        float major = pickStep(56f);
        float minor = pickStep(9f);
        float from = Math.max(0f, viewStart);
        float to = Math.min(length, viewStart + areaW / pxPerSec);
        if (minor > 0f && minor < major) {
            for (int i = (int) Math.floor(from / minor); i * minor <= to + minor; i++) {
                float x = timeToX(areaX, i * minor);
                if (i * minor < -1e-4f || x < areaX || x > areaX + areaW) continue;
                dl.addLine(x, origin.y, x, origin.y + canvasH, COL_GRID_MINOR);
            }
        }
        for (int i = (int) Math.floor(from / major); i * major <= to + major; i++) {
            float x = timeToX(areaX, i * major);
            if (i * major < -1e-4f || x < areaX || x > areaX + areaW) continue;
            dl.addLine(x, origin.y, x, origin.y + canvasH, COL_GRID_MAJOR);
        }
        dl.popClipRect();

        int hoverRow = -1;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float ry = origin.y + row.y;
            if (ry > viewBottom || ry + row.height < viewTop) continue;
            if (hovered && my >= ry && my < ry + row.height) hoverRow = i;
        }

        String selectedBone = AnimatorTimelineSession.selectedBone();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float ry = origin.y + row.y;
            if (ry > viewBottom + 4f || ry + row.height < viewTop - 4f) continue;
            drawRow(dl, clip, row, i, origin.x, ry, areaX, areaW, trackW,
                    i == hoverRow, row.bone != null && row.bone.equals(selectedBone), mx, my, hovered);
        }

        // Playhead over everything.
        float px = timeToX(areaX, AnimatorTimelineSession.time());
        if (px >= areaX && px <= areaX + areaW) {
            dl.addLine(px, origin.y, px, origin.y + canvasH, rgba(255, 158, 44, 210), 1.4f);
        }

        dl.addLine(areaX - 0.5f, origin.y, areaX - 0.5f, origin.y + canvasH,
                dragMode == DRAG_SPLIT ? COL_SPLIT_HL : COL_SPLIT);

        if (dragMode == DRAG_BOX) {
            float x0 = Math.min(boxAx, boxBx);
            float x1 = Math.max(boxAx, boxBx);
            float sy0 = Math.min(boxAy, boxBy);
            float sy1 = Math.max(boxAy, boxBy);
            dl.addRectFilled(x0, sy0, x1, sy1, rgba(255, 158, 44, 38), 2f);
            dl.addRect(x0, sy0, x1, sy1, rgba(255, 178, 80, 190), 2f, 0, 1.2f);
        }

        handleRowsInput(clip, hovered, hoverRow, origin, areaX, areaW, mx, my, rowsH, canvasH);
        ImGui.endChild();
    }

    private void drawRow(ImDrawList dl, EditorAnimationDocument.Clip clip, Row row, int index,
                         float x0, float y, float areaX, float areaW, float trackW,
                         boolean hovered, boolean boneSelected, float mx, float my, boolean canHover) {
        float y1 = y + row.height;
        int bg = row.group() ? COL_GROUP_BG : (index % 2 == 0 ? COL_ROW_ALT : 0);
        if (bg != 0) dl.addRectFilled(x0, y, x0 + trackW, y1 - 1f, bg);
        if (boneSelected) dl.addRectFilled(x0, y, x0 + trackW, y1 - 1f, COL_ROW_SEL);
        if (hovered) dl.addRectFilled(x0, y, x0 + trackW, y1 - 1f, COL_ROW_HOVER);
        dl.addLine(x0, y1 - 0.5f, x0 + trackW, y1 - 0.5f, rgba(0, 0, 0, 60));

        // --- header cell
        float textY = y + (row.height - ImGui.getTextLineHeight()) * 0.5f;
        int keyCount = 0;
        for (Lane lane : row.lanes) keyCount += laneTimes(clip, lane).size();
        String count = String.valueOf(keyCount);
        float countW = ImGui.calcTextSize(count).x;
        float actionX = areaX - 22f;

        dl.pushClipRect(x0, y, Math.max(x0 + 20f, actionX - 12f - countW), y1, true);
        if (row.group()) {
            boolean open = !collapsed.contains(row.id);
            drawChevron(dl, x0 + 12f, y + row.height * 0.5f, open, hovered ? COL_TEXT : COL_TEXT_DIM);
        } else {
            dl.addRectFilled(x0 + 14f, y + 4f, x0 + 16.5f, y1 - 5f, row.color, 1f);
        }
        int labelColor = row.group() ? COL_TEXT : withAlpha(row.color, 235);
        dl.addText(x0 + 24f, textY, labelColor, row.label);
        if (row.sublabel != null) {
            dl.addText(x0 + 24f + ImGui.calcTextSize(row.label).x + 7f, textY, COL_TEXT_MUTED, row.sublabel);
        }
        dl.popClipRect();

        dl.addText(actionX - 10f - countW, textY, COL_TEXT_MUTED, count);

        boolean overKeyBtn = canHover && my >= y && my < y1
                && mx >= actionX - 2f && mx <= actionX + 16f;
        diamond(dl, actionX + 7f, y + row.height * 0.5f, overKeyBtn ? 5.2f : 4.2f,
                overKeyBtn ? COL_ACCENT : rgba(150, 157, 170, 255));

        // --- lanes
        dl.pushClipRect(areaX, y, areaX + areaW, y1, true);
        if (row.group()) {
            drawSummaryKeys(dl, clip, row, areaX, areaW, y + row.height * 0.5f);
        } else {
            drawLaneKeys(dl, clip, row.lanes.get(0), areaX, areaW, y + row.height * 0.5f, mx, my, canHover);
        }
        dl.popClipRect();
    }

    private void drawSummaryKeys(ImDrawList dl, EditorAnimationDocument.Clip clip, Row row,
                                 float areaX, float areaW, float cy) {
        Set<Float> times = new LinkedHashSet<>();
        boolean anySelected = false;
        for (Lane lane : row.lanes) {
            for (Float t : laneTimes(clip, lane)) {
                times.add(t);
                if (selection.contains(new KeyId(lane.id, t))) anySelected = true;
            }
        }
        if (times.isEmpty()) return;
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (Float t : times) {
            min = Math.min(min, t);
            max = Math.max(max, t);
        }
        float xa = timeToX(areaX, min);
        float xb = timeToX(areaX, max);
        if (xb > xa) {
            dl.addRectFilled(xa, cy - 1.5f, xb, cy + 1.5f, rgba(255, 255, 255, 26), 1.5f);
        }
        for (Float t : times) {
            float x = timeToX(areaX, t);
            if (x < areaX - 8f || x > areaX + areaW + 8f) continue;
            diamond(dl, x, cy, 5f, rgba(0, 0, 0, 190));
            diamond(dl, x, cy, 3.6f, anySelected ? rgba(255, 214, 150, 255) : COL_SUMMARY);
        }
    }

    private void drawLaneKeys(ImDrawList dl, EditorAnimationDocument.Clip clip, Lane lane,
                              float areaX, float areaW, float cy, float mx, float my, boolean canHover) {
        NavigableSet<Float> times = laneTimes(clip, lane);
        if (times.isEmpty()) return;
        float xa = timeToX(areaX, times.first());
        float xb = timeToX(areaX, times.last());
        if (xb > xa) dl.addRectFilled(xa, cy - 1.2f, xb, cy + 1.2f, withAlpha(lane.color, 70), 1.2f);

        boolean rowHovered = canHover && Math.abs(my - cy) <= SUB_H * 0.5f;
        for (Float t : new ArrayList<>(times)) {
            float x = timeToX(areaX, t);
            if (x < areaX - 10f || x > areaX + areaW + 10f) continue;
            boolean selected = selection.contains(new KeyId(lane.id, t));
            boolean keyHovered = rowHovered && Math.abs(mx - x) <= KEY_HIT_R;
            float r = selected ? 5.6f : keyHovered ? 5.4f : 4.6f;
            int color = selected ? rgba(255, 214, 150, 255) : lane.color;
            drawKeyShape(dl, x, cy, r + 1.3f, laneLerp(clip, lane, t), rgba(0, 0, 0, 200));
            drawKeyShape(dl, x, cy, r, laneLerp(clip, lane, t), color);
            if (selected) drawKeyOutline(dl, x, cy, r + 3f, laneLerp(clip, lane, t));
        }
    }

    private static void drawKeyShape(ImDrawList dl, float x, float y, float r, int lerp, int color) {
        switch (lerp) {
            case EditorAnimationDocument.Key.LERP_STEP ->
                    dl.addRectFilled(x - r * 0.82f, y - r * 0.82f, x + r * 0.82f, y + r * 0.82f, color, 1.2f);
            case EditorAnimationDocument.Key.LERP_CATMULLROM -> dl.addCircleFilled(x, y, r * 0.92f, color, 14);
            default -> diamond(dl, x, y, r, color);
        }
    }

    private static void drawKeyOutline(ImDrawList dl, float x, float y, float r, int lerp) {
        int color = rgba(255, 255, 255, 235);
        switch (lerp) {
            case EditorAnimationDocument.Key.LERP_STEP ->
                    dl.addRect(x - r * 0.82f, y - r * 0.82f, x + r * 0.82f, y + r * 0.82f, color, 1.5f, 0, 1.4f);
            case EditorAnimationDocument.Key.LERP_CATMULLROM -> dl.addCircle(x, y, r * 0.92f, color, 16, 1.4f);
            default -> {
                dl.pathLineTo(x, y - r);
                dl.pathLineTo(x + r, y);
                dl.pathLineTo(x, y + r);
                dl.pathLineTo(x - r, y);
                dl.pathStroke(color, imgui.flag.ImDrawFlags.Closed, 1.4f);
            }
        }
    }

    private static void drawChevron(ImDrawList dl, float cx, float cy, boolean open, int color) {
        float s = 3.6f;
        if (open) {
            dl.addTriangleFilled(cx - s, cy - s * 0.6f, cx + s, cy - s * 0.6f, cx, cy + s * 0.8f, color);
            } else {
            dl.addTriangleFilled(cx - s * 0.6f, cy - s, cx - s * 0.6f, cy + s, cx + s * 0.8f, cy, color);
        }
    }

    private static void diamond(ImDrawList dl, float x, float y, float r, int color) {
        dl.addQuadFilled(x, y - r, x + r, y, x, y + r, x - r, y, color);
    }

    // ----------------------------------------------------------- rows input

    private void handleRowsInput(EditorAnimationDocument.Clip clip, boolean hovered,
                                 int hoverRow, ImVec2 origin, float areaX, float areaW,
                                 float mx, float my, float rowsH, float canvasH) {
        // The popup lives in this child, so it has to be submitted every frame.
        if (openKeyMenu) {
            openKeyMenu = false;
            ImGui.openPopup("##tl-key-menu");
        }
        renderKeyMenu(clip);

        // Wheel: ctrl = zoom, shift = pan, plain = vertical scroll.
        float wheel = ImGui.getIO().getMouseWheel();
        if (hovered && wheel != 0f) {
            if (ImGui.getIO().getKeyCtrl()) {
                zoomAt(mx, areaX, wheel);
            } else if (ImGui.getIO().getKeyShift()) {
                viewStart -= wheel * (areaW * 0.12f) / pxPerSec;
            } else {
                ImGui.setScrollY(clamp(ImGui.getScrollY() - wheel * 46f, 0f, Math.max(0f, canvasH - rowsH)));
            }
        }

        if (dragMode == DRAG_PAN) {
            if (ImGui.isMouseDown(2)) {
                viewStart = panAnchorView - (mx - panAnchorX) / pxPerSec;
            } else {
                dragMode = DRAG_NONE;
            }
            return;
        }
        if (hovered && ImGui.isMouseClicked(2)) {
            dragMode = DRAG_PAN;
            panAnchorX = mx;
            panAnchorView = viewStart;
            return;
        }
        if (dragMode == DRAG_KEYS) {
            updateKeyDrag(clip, areaX, mx);
            return;
        }
        if (dragMode == DRAG_BOX) {
            updateBoxSelect(clip, origin, areaX, mx, my);
            return;
        }
        if (dragMode == DRAG_SCRUB) {
            if (ImGui.isMouseDown(0)) {
                AnimatorTimelineSession.setTime(AnimatorTimelineSession.snapTime(xToTime(areaX, mx)));
            } else {
                dragMode = DRAG_NONE;
            }
            return;
        }
        if (!hovered) return;

        Row row = hoverRow >= 0 ? rows.get(hoverRow) : null;
        boolean lmb = ImGui.isMouseClicked(0);
        boolean rmb = ImGui.isMouseClicked(1);
        boolean dbl = ImGui.isMouseDoubleClicked(0);
        boolean ctrl = ImGui.getIO().getKeyCtrl();

        // --- header column
        if (mx < areaX) {
            if (row == null || !lmb) return;
            float actionX = areaX - 22f;
            if (mx >= actionX - 2f && mx <= actionX + 16f) {
                keyRowAtPlayhead(clip, row);
                return;
            }
            if (row.group() && mx <= origin.x + 22f) {
                if (!collapsed.remove(row.id)) collapsed.add(row.id);
                return;
            }
            if (row.bone != null) AnimatorTimelineSession.selectBone(row.bone);
            selectRowKeys(clip, row, ctrl);
            return;
        }

        // --- key hit test
        if (row != null && (lmb || rmb)) {
            KeyId hit = hitTestKey(clip, row, areaX, mx);
            if (hit != null) {
                if (rmb) {
                    if (!selection.contains(hit)) {
                        selection.clear();
                        selection.add(hit);
                    }
                    contextLane = hit.lane();
                    contextTime = hit.time();
                    syncPrimarySelection();
                    openKeyMenu = true;
            return;
                }
                if (ctrl) {
                    if (!selection.remove(hit)) selection.add(hit);
                } else if (!selection.contains(hit)) {
                    selection.clear();
                    selection.add(hit);
                }
                if (row.bone != null) AnimatorTimelineSession.selectBone(row.bone);
                syncPrimarySelection();
                AnimatorTimelineSession.setTime(hit.time());
                beginKeyDrag(clip, areaX, mx);
                return;
            }
        }

        if (row != null && dbl) {
            insertKey(clip, row, AnimatorTimelineSession.snapTime(xToTime(areaX, mx)));
            return;
        }
                        if (rmb) {
            contextLane = null;
            contextTime = AnimatorTimelineSession.snapTime(xToTime(areaX, mx));
            openKeyMenu = true;
            return;
        }
        if (lmb) {
            if (ImGui.getIO().getKeyAlt()) {
                dragMode = DRAG_SCRUB;
                AnimatorTimelineSession.setPlaying(false);
                AnimatorTimelineSession.setTime(AnimatorTimelineSession.snapTime(xToTime(areaX, mx)));
                        } else {
                dragMode = DRAG_BOX;
                boxAx = mx;
                boxAy = my;
                boxBx = mx;
                boxBy = my;
                if (!ctrl) selection.clear();
            }
        }
    }

    private void zoomAt(float mx, float areaX, float wheel) {
        float anchor = xToTime(areaX, mx);
        pxPerSec = clamp(pxPerSec * (float) Math.pow(1.18f, wheel), MIN_PX_PER_SEC, MAX_PX_PER_SEC);
        viewStart = anchor - (mx - areaX) / pxPerSec;
    }

    private KeyId hitTestKey(EditorAnimationDocument.Clip clip, Row row, float areaX, float mx) {
        KeyId best = null;
        float bestDist = KEY_HIT_R;
        for (Lane lane : row.lanes) {
            for (Float t : new ArrayList<>(laneTimes(clip, lane))) {
                float dist = Math.abs(timeToX(areaX, t) - mx);
                if (dist <= bestDist) {
                    bestDist = dist;
                    best = new KeyId(lane.id, t);
                }
            }
        }
        return best;
    }

    private void selectRowKeys(EditorAnimationDocument.Clip clip, Row row, boolean additive) {
        if (!additive) selection.clear();
        for (Lane lane : row.lanes) {
            for (Float t : new ArrayList<>(laneTimes(clip, lane))) selection.add(new KeyId(lane.id, t));
        }
        syncPrimarySelection();
    }

    private void beginKeyDrag(EditorAnimationDocument.Clip clip, float areaX, float mx) {
        dragMode = DRAG_KEYS;
                            dragUndoCaptured = false;
        dragAnchorTime = xToTime(areaX, mx);
        dragLastOffset = 0f;
        dragOrigin.clear();
        for (KeyId key : selection) dragOrigin.put(key, key.time());
                        }

    private void updateKeyDrag(EditorAnimationDocument.Clip clip, float areaX, float mx) {
        if (!ImGui.isMouseDown(0)) {
            dragMode = DRAG_NONE;
            dragOrigin.clear();
                        return;
                    }
        float raw = xToTime(areaX, mx) - dragAnchorTime;
        float offset = AnimatorTimelineSession.snapTime(raw);
        if (offset == dragLastOffset) return;

        // Rebuild from the drag-start snapshot so partial overlaps never lose keys.
        if (!dragUndoCaptured) {
            AnimatorTimelineSession.pushUndo();
            dragUndoCaptured = true;
        }
        Map<KeyId, Float> current = new LinkedHashMap<>();
        for (Map.Entry<KeyId, Float> entry : dragOrigin.entrySet()) {
            current.put(entry.getKey(), entry.getKey().time() + dragLastOffset);
        }
        boolean moved = false;
        Map<String, List<float[]>> perLane = new LinkedHashMap<>();
        for (Map.Entry<KeyId, Float> entry : dragOrigin.entrySet()) {
            float wanted = Math.max(0f, AnimatorTimelineSession.quantize(entry.getValue() + offset));
            perLane.computeIfAbsent(entry.getKey().lane(), ignored -> new ArrayList<>())
                    .add(new float[]{current.get(entry.getKey()), wanted});
        }
        LinkedHashSet<KeyId> next = new LinkedHashSet<>();
        for (Map.Entry<String, List<float[]>> laneEntry : perLane.entrySet()) {
            Lane lane = laneById.get(laneEntry.getKey());
            if (lane == null) continue;
            List<float[]> moves = laneEntry.getValue();
            // Move in the direction of travel first so keys never overwrite each other.
            moves.sort((a, b) -> offset >= dragLastOffset
                    ? Float.compare(b[0], a[0]) : Float.compare(a[0], b[0]));
            for (float[] move : moves) {
                if (move[0] != move[1] && laneMoveKey(clip, lane, move[0], move[1])) moved = true;
                next.add(new KeyId(lane.id, move[1]));
            }
        }
        if (!moved && next.isEmpty()) return;
        selection.clear();
        selection.addAll(next);
        dragLastOffset = offset;
        Float earliest = null;
        for (KeyId key : selection) earliest = earliest == null ? key.time() : Math.min(earliest, key.time());
        if (earliest != null) AnimatorTimelineSession.setTime(earliest);
        syncPrimarySelection();
        growClipToSelection(clip);
    }

    private void updateBoxSelect(EditorAnimationDocument.Clip clip, ImVec2 origin, float areaX,
                                 float mx, float my) {
        boxBx = mx;
        boxBy = my;
        if (ImGui.isMouseDown(0)) return;
        dragMode = DRAG_NONE;
        float x0 = Math.min(boxAx, boxBx);
        float x1 = Math.max(boxAx, boxBx);
        float y0 = Math.min(boxAy, boxBy);
        float y1 = Math.max(boxAy, boxBy);
        if (x1 - x0 < 3f && y1 - y0 < 3f) {
            if (!ImGui.getIO().getKeyCtrl()) selection.clear();
            syncPrimarySelection();
                return;
            }
        float t0 = xToTime(areaX, x0);
        float t1 = xToTime(areaX, x1);
        for (Row row : rows) {
            float ry = origin.y + row.y;
            if (ry + row.height < y0 || ry > y1) continue;
            for (Lane lane : row.lanes) {
                for (Float t : new ArrayList<>(laneTimes(clip, lane))) {
                    if (t >= t0 && t <= t1) selection.add(new KeyId(lane.id, t));
                }
            }
        }
        syncPrimarySelection();
    }

    private void renderKeyMenu(EditorAnimationDocument.Clip clip) {
        if (!ImGui.beginPopup("##tl-key-menu")) return;
        int count = selection.size();
        if (count > 0) {
            ImGui.textDisabled(String.format(Locale.ROOT, EditorI18n.t("Ключей выбрано: %d"), count));
            ImGui.separator();
            if (ImGui.menuItem(EditorI18n.t("Линейная"))) setSelectionLerp(clip, EditorAnimationDocument.Key.LERP_LINEAR);
            if (ImGui.menuItem(EditorI18n.t("Плавная (catmullrom)"))) {
                setSelectionLerp(clip, EditorAnimationDocument.Key.LERP_CATMULLROM);
            }
            if (ImGui.menuItem(EditorI18n.t("Шаг (step)"))) setSelectionLerp(clip, EditorAnimationDocument.Key.LERP_STEP);
            ImGui.separator();
            if (ImGui.menuItem(EditorI18n.t("Копировать"), "Ctrl+C")) copySelection(clip);
            if (ImGui.menuItem(EditorI18n.t("Удалить"), "Del")) deleteSelection(clip);
            ImGui.separator();
        }
        boolean pastable = !clipboard.isEmpty();
        if (ImGui.menuItem(EditorI18n.t("Вставить на текущее время"), "Ctrl+V", false, pastable)) {
            pasteClipboard(clip);
        }
        if (contextLane == null && ImGui.menuItem(EditorI18n.t("Перемотать сюда"))) {
            AnimatorTimelineSession.setTime(contextTime);
        }
        ImGui.endPopup();
    }

    // ------------------------------------------------------------- navigator

    private void renderNavigator(EditorAnimationDocument.Clip clip, float w, float trackW, float areaW) {
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImGui.dummy(w, NAV_H);
        boolean hovered = ImGui.isItemHovered();
        ImDrawList dl = ImGui.getWindowDrawList();
        float areaX = origin.x + headerW;
        float y0 = origin.y + 2f;
        float y1 = origin.y + NAV_H - 2f;
        float length = Math.max(0.01f, clip.lengthSec);
        float span = areaW / pxPerSec;
        float total = Math.max(length, viewStart + span);

        dl.addRectFilled(origin.x, origin.y, origin.x + trackW, origin.y + NAV_H, COL_PANEL);
        dl.addRectFilled(areaX, y0, areaX + areaW, y1, rgba(14, 15, 18, 255), 3f);

        // Key density preview.
        for (Lane lane : laneById.values()) {
            for (Float t : laneTimes(clip, lane)) {
                float x = areaX + (t / total) * areaW;
                dl.addLine(x, y0 + 2f, x, y1 - 2f, withAlpha(lane.color, 120));
            }
        }
        float clipEnd = areaX + (length / total) * areaW;
        dl.addLine(clipEnd, y0, clipEnd, y1, rgba(255, 90, 90, 150));

        float thumbX0 = areaX + (Math.max(0f, viewStart) / total) * areaW;
        float thumbX1 = areaX + (Math.min(total, viewStart + span) / total) * areaW;
        thumbX1 = Math.max(thumbX1, thumbX0 + 16f);
        boolean overThumb = hovered && ImGui.getMousePosX() >= thumbX0 && ImGui.getMousePosX() <= thumbX1;
        dl.addRectFilled(thumbX0, y0, thumbX1, y1,
                overThumb || dragMode == DRAG_NAV ? rgba(255, 158, 44, 140) : rgba(255, 255, 255, 46), 3f);
        dl.addRect(thumbX0, y0, thumbX1, y1, rgba(255, 255, 255, 60), 3f, 0, 1f);

        float ph = areaX + (Math.max(0f, AnimatorTimelineSession.time()) / total) * areaW;
        dl.addLine(ph, y0, ph, y1, COL_ACCENT, 1.5f);

        if (dragMode == DRAG_NAV) {
            if (ImGui.isMouseDown(0)) {
                float fraction = clamp((ImGui.getMousePosX() - navGrabOffset - areaX) / areaW, 0f, 1f);
                viewStart = fraction * total;
            } else {
                dragMode = DRAG_NONE;
            }
        } else if (hovered && ImGui.isMouseClicked(0) && ImGui.getMousePosX() >= areaX) {
            dragMode = DRAG_NAV;
            navGrabOffset = overThumb ? ImGui.getMousePosX() - thumbX0 : (thumbX1 - thumbX0) * 0.5f;
        }
        if (hovered) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
    }

    // ------------------------------------------------------------- inspector

    private void renderInspector(EditorAnimationDocument.Clip clip, SceneNode target) {
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 7f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 9f, 7f);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, .088f, .094f, .110f, 1f);
        ImGui.beginChild("##tl-inspector", 0f, 132f, true);
        if (ImGui.beginTabBar("##tl-inspector-tabs")) {
            if (ImGui.beginTabItem(EditorI18n.t("Ключ"))) {
                renderKeyTab(clip);
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(EditorI18n.t("Поза"))) {
                renderPoseTab(clip);
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(EditorI18n.t("Клип"))) {
                renderClipTab(clip, target);
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
        ImGui.endChild();
        ImGui.popStyleColor();
        ImGui.popStyleVar(2);
    }

    private void renderKeyTab(EditorAnimationDocument.Clip clip) {
        if (selection.isEmpty()) {
            ImGui.spacing();
            ImGui.textDisabled(EditorI18n.t("Кликните по ключу, чтобы редактировать значение и интерполяцию"));
            ImGui.textDisabled(EditorI18n.t("ЛКМ по пустому месту — рамка выделения · Alt+ЛКМ — перемотка · СКМ — панорама · Ctrl+колесо — зум"));
            return;
        }
        if (selection.size() > 1) {
            ImGui.alignTextToFramePadding();
            ImGui.textColored(1f, .62f, .17f, 1f,
                    String.format(Locale.ROOT, EditorI18n.t("Выбрано ключей: %d"), selection.size()));
            ImGui.sameLine();
            if (ImGui.button(EditorI18n.t("Линейная##multi"))) setSelectionLerp(clip, EditorAnimationDocument.Key.LERP_LINEAR);
            ImGui.sameLine();
            if (ImGui.button(EditorI18n.t("Плавная##multi"))) setSelectionLerp(clip, EditorAnimationDocument.Key.LERP_CATMULLROM);
            ImGui.sameLine();
            if (ImGui.button(EditorI18n.t("Шаг##multi"))) setSelectionLerp(clip, EditorAnimationDocument.Key.LERP_STEP);
            ImGui.sameLine();
            if (ImGui.button(EditorI18n.t("Копировать##multi"))) copySelection(clip);
            ImGui.sameLine();
            if (ImGui.button(EditorI18n.t("Удалить##multi"))) deleteSelection(clip);
            ImGui.spacing();
            ImGui.textDisabled(EditorI18n.t("Тяните ключи мышью, чтобы сдвинуть выделение по времени"));
            return;
        }

        KeyId id = selection.iterator().next();
        Lane lane = laneById.get(id.lane());
        if (lane == null) {
            ImGui.textDisabled(EditorI18n.t("Ключ больше не существует"));
            return;
        }
        ImGui.alignTextToFramePadding();
        ImGui.textColored(1f, .62f, .17f, 1f, laneTitle(lane));
        ImGui.sameLine();
        ImGui.textDisabled(String.format(Locale.ROOT, "%s c · %s %d",
                timeLabel(id.time()), EditorI18n.t("кадр"), Math.round(id.time() * AnimatorTimelineSession.fps())));

        ImGui.sameLine();
        ImGui.setNextItemWidth(88f);
        single[0] = id.time();
        if (ImGui.dragFloat("##key-time", single, 0.01f, 0f, 3600f, "%.3f")) {
            float wanted = Math.max(0f, AnimatorTimelineSession.quantize(single[0]));
            if (wanted != id.time() && laneMoveKey(clip, lane, id.time(), wanted)) {
                selection.clear();
                selection.add(new KeyId(lane.id, wanted));
                AnimatorTimelineSession.setTime(wanted);
                syncPrimarySelection();
                growClipToSelection(clip);
            }
        }
        if (ImGui.isItemActivated()) AnimatorTimelineSession.pushUndo();

        ImGui.sameLine();
        renderLerpCombo(clip, lane, id.time());
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Копировать"))) copySelection(clip);
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Удалить"))) {
            deleteSelection(clip);
            return;
        }

        ImGui.spacing();
        if (lane.property) {
            renderPropertyValue(clip, lane, id.time());
        } else {
            EditorAnimationDocument.BoneChannels channels = clip.bones.get(lane.bone);
            EditorAnimationDocument.Key key = channels == null ? null : channels.channel(lane.channel).get(id.time());
            if (key == null) return;
            vec3[0] = key.v[0];
            vec3[1] = key.v[1];
            vec3[2] = key.v[2];
            ImGui.setNextItemWidth(Math.min(420f, ImGui.getContentRegionAvailX()));
            boolean edited = ImGui.dragFloat3("##key-value", vec3, lane.channel == 2 ? 0.01f : 0.5f);
            if (ImGui.isItemActivated()) AnimatorTimelineSession.pushUndo();
            if (edited) {
                key.v[0] = vec3[0];
                key.v[1] = vec3[1];
                key.v[2] = vec3[2];
            }
            ImGui.sameLine();
            ImGui.textDisabled(lane.channel == 2 ? EditorI18n.t("масштаб XYZ")
                    : lane.channel == 1 ? EditorI18n.t("смещение XYZ") : EditorI18n.t("градусы XYZ"));
        }
    }

    private void renderLerpCombo(EditorAnimationDocument.Clip clip, Lane lane, float time) {
        int lerp = laneLerp(clip, lane, time);
        String label = switch (lerp) {
                case EditorAnimationDocument.Key.LERP_CATMULLROM -> EditorI18n.t("Плавная");
                case EditorAnimationDocument.Key.LERP_STEP -> EditorI18n.t("Шаг");
                default -> EditorI18n.t("Линейная");
            };
        ImGui.setNextItemWidth(130f);
        if (!ImGui.beginCombo("##lerp", label)) return;
        if (ImGui.selectable(EditorI18n.t("Линейная"), lerp == EditorAnimationDocument.Key.LERP_LINEAR)) {
            setSelectionLerp(clip, EditorAnimationDocument.Key.LERP_LINEAR);
        }
        if (ImGui.selectable(EditorI18n.t("Плавная (catmullrom)"), lerp == EditorAnimationDocument.Key.LERP_CATMULLROM)) {
            setSelectionLerp(clip, EditorAnimationDocument.Key.LERP_CATMULLROM);
        }
        if (ImGui.selectable(EditorI18n.t("Шаг (step)"), lerp == EditorAnimationDocument.Key.LERP_STEP)) {
            setSelectionLerp(clip, EditorAnimationDocument.Key.LERP_STEP);
                }
                ImGui.endCombo();
            }

    private void renderPropertyValue(EditorAnimationDocument.Clip clip, Lane lane, float time) {
        EditorAnimationDocument.PropertyChannel channel = propertyChannel(clip, lane);
        EditorAnimationDocument.PropertyKey key = channel == null ? null : channel.keys.get(time);
        if (key == null || channel == null) return;
        JsonElement value = key.value;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            boolean current = value.getAsBoolean();
            if (ImGui.checkbox(EditorI18n.t("значение"), current)) {
                AnimatorTimelineSession.pushUndo();
                channel.keys.put(time, new EditorAnimationDocument.PropertyKey(
                        new JsonPrimitive(!current), key.lerp));
            }
            return;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            single[0] = value.getAsFloat();
            ImGui.setNextItemWidth(200f);
            boolean edited = ImGui.dragFloat("##prop-value", single, 0.05f);
            if (ImGui.isItemActivated()) AnimatorTimelineSession.pushUndo();
            if (edited) {
                channel.keys.put(time, new EditorAnimationDocument.PropertyKey(
                        new JsonPrimitive(single[0]), key.lerp));
            }
            ImGui.sameLine();
            ImGui.textDisabled(channel.type);
            return;
        }
        String bufKey = lane.id + "@" + time;
        if (!bufKey.equals(propBufKey)) {
            propBufKey = bufKey;
            propTextBuf.set(value.isJsonNull() ? "" : value.getAsString());
        }
        ImGui.setNextItemWidth(Math.min(380f, ImGui.getContentRegionAvailX()));
        if (ImGui.inputText("##prop-text", propTextBuf)) {
            AnimatorTimelineSession.pushUndo();
            channel.keys.put(time, new EditorAnimationDocument.PropertyKey(
                    new JsonPrimitive(propTextBuf.get()), key.lerp));
        }
        ImGui.sameLine();
        ImGui.textDisabled(channel.type);
    }

    private void renderPoseTab(EditorAnimationDocument.Clip clip) {
        String bone = AnimatorTimelineSession.selectedBone();
        if (bone == null) {
            ImGui.spacing();
            ImGui.textDisabled(EditorI18n.t("Выберите кость в дорожках или в иерархии, чтобы ставить ключи"));
            return;
        }
        ImGui.alignTextToFramePadding();
        ImGui.textColored(.46f, .82f, .68f, 1f, bone);
        ImGui.sameLine();
        ImGui.textDisabled(String.format(Locale.ROOT, EditorI18n.t("на %s c"),
                timeLabel(AnimatorTimelineSession.time())));
        EditorAnimationDocument.BoneChannels channels = clip.bones.get(bone);
        if (channels == null || channels.isEmpty()) {
            ImGui.sameLine();
            if (ImGui.smallButton(EditorI18n.t("+ На таймлайн (базовая точка)"))) {
                AnimatorTimelineSession.addBoneWithBaseKeys(bone);
            }
        }

        float t = AnimatorTimelineSession.time();
        for (int c = 0; c < 3; c++) {
            TreeMap<Float, EditorAnimationDocument.Key> channel =
                    channels == null ? new TreeMap<>() : channels.channel(c);
            float[] value = EditorAnimationDocument.sample(channel, t, c == 2 ? 1f : 0f);
            vec3[0] = value[0];
            vec3[1] = value[1];
            vec3[2] = value[2];
            ImGui.pushID("pose-" + c);
            ImGui.pushStyleColor(ImGuiCol.Text, CHANNEL_COLORS[c]);
            ImGui.alignTextToFramePadding();
            ImGui.text(channelName(c));
            ImGui.popStyleColor();
            ImGui.sameLine(84f);
            ImGui.setNextItemWidth(Math.max(180f, ImGui.getContentRegionAvailX() - 70f));
            boolean edited = ImGui.dragFloat3("##pose", vec3, c == 2 ? 0.01f : 0.5f);
            if (ImGui.isItemActivated()) AnimatorTimelineSession.pushUndo();
            ImGui.sameLine();
            boolean keyed = ImGui.smallButton(EditorI18n.t("Ключ"));
            if (edited || keyed) {
                if (keyed) AnimatorTimelineSession.pushUndo();
                AnimatorTimelineSession.putKey(bone, c, new float[]{vec3[0], vec3[1], vec3[2]});
                selection.clear();
                selection.add(new KeyId("b|" + bone + "|" + c, AnimatorTimelineSession.quantize(t)));
            }
            ImGui.popID();
        }
    }

    private void renderClipTab(EditorAnimationDocument.Clip clip, SceneNode target) {
        ImGui.alignTextToFramePadding();
        ImGui.text(EditorI18n.t("Имя"));
        ImGui.sameLine(84f);
        ImGui.setNextItemWidth(240f);
        if (renameClipBuf.get().isEmpty()) renameClipBuf.set(AnimatorTimelineSession.clipName());
        ImGui.inputText("##clip-name", renameClipBuf);
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Переименовать"))) {
            AnimatorTimelineSession.renameClip(renameClipBuf.get());
        }

        ImGui.alignTextToFramePadding();
        ImGui.text(EditorI18n.t("Длина"));
        ImGui.sameLine(84f);
        ImGui.setNextItemWidth(120f);
        single[0] = clip.lengthSec;
        boolean changed = ImGui.dragFloat("##clip-len", single, 0.01f, 0.01f, 3600f, "%.2f c");
        if (ImGui.isItemActivated()) AnimatorTimelineSession.pushUndo();
        if (changed) clip.lengthSec = Math.max(0.01f, single[0]);
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("По последнему ключу"))) {
            AnimatorTimelineSession.pushUndo();
            clip.lengthSec = Math.max(0.01f, lastKeyTime(clip));
        }
        ImGui.sameLine();
        if (ImGui.checkbox(EditorI18n.t("Зациклить"), clip.loop)) {
            AnimatorTimelineSession.pushUndo();
            clip.loop = !clip.loop;
        }

        ImGui.alignTextToFramePadding();
        ImGui.text(EditorI18n.t("Кадры"));
        ImGui.sameLine(84f);
        ImGui.setNextItemWidth(120f);
        single[0] = AnimatorTimelineSession.fps();
        if (ImGui.dragFloat("##clip-fps", single, 1f, 1f, 240f, EditorI18n.t("%.0f кадр/с"))) {
            AnimatorTimelineSession.setFps(single[0]);
        }
        ImGui.sameLine();
        ImGui.textDisabled(String.format(Locale.ROOT, EditorI18n.t("дорожек: %d · костей: %d · параметров: %d"),
                rows.size(), clip.bones.size(), propertyChannelCount(clip)));
        if (target != null) {
            ImGui.sameLine();
            ImGui.textDisabled("·  " + target.name);
        }
    }

    private static float lastKeyTime(EditorAnimationDocument.Clip clip) {
        float last = 0f;
        for (EditorAnimationDocument.BoneChannels channels : clip.bones.values()) {
            for (int c = 0; c < 3; c++) {
                TreeMap<Float, EditorAnimationDocument.Key> channel = channels.channel(c);
                if (!channel.isEmpty()) last = Math.max(last, channel.lastKey());
            }
        }
        for (Map<String, EditorAnimationDocument.PropertyChannel> target : clip.properties.values()) {
            for (EditorAnimationDocument.PropertyChannel channel : target.values()) {
                if (!channel.keys.isEmpty()) last = Math.max(last, channel.keys.lastKey());
            }
        }
        return last;
    }

    private static int propertyChannelCount(EditorAnimationDocument.Clip clip) {
        int count = 0;
        for (Map<String, EditorAnimationDocument.PropertyChannel> target : clip.properties.values()) {
            count += target.size();
        }
        return count;
    }

    // -------------------------------------------------------------- commands

    private void keyRowAtPlayhead(EditorAnimationDocument.Clip clip, Row row) {
        AnimatorTimelineSession.pushUndo();
        float t = AnimatorTimelineSession.quantize(AnimatorTimelineSession.time());
        selection.clear();
        for (Lane lane : row.lanes) {
            if (lane.property) {
                AnimatorTimelineSession.putPropertyKeyFromScene(lane.targetId, lane.propId);
            } else {
                float[] sampled = AnimatorTimelineSession.sampleBoneTransform(lane.bone);
                AnimatorTimelineSession.putKey(lane.bone, lane.channel, channelValue(sampled, lane.channel));
            }
            selection.add(new KeyId(lane.id, t));
        }
        syncPrimarySelection();
        growClipToSelection(clip);
    }

    private void insertKey(EditorAnimationDocument.Clip clip, Row row, float time) {
        AnimatorTimelineSession.pushUndo();
        float restore = AnimatorTimelineSession.time();
        AnimatorTimelineSession.setTime(time);
        float t = AnimatorTimelineSession.quantize(time);
        selection.clear();
        for (Lane lane : row.lanes) {
            if (lane.property) {
                EditorAnimationDocument.PropertyChannel channel = propertyChannel(clip, lane);
                if (channel == null) continue;
                JsonElement sampled = EditorAnimationDocument.sampleProperty(channel, t);
                if (sampled == null) continue;
                channel.keys.put(t, new EditorAnimationDocument.PropertyKey(sampled,
                        channel.keys.isEmpty() ? EditorAnimationDocument.Key.LERP_LINEAR
                                : channel.keys.firstEntry().getValue().lerp));
            } else {
                float[] sampled = AnimatorTimelineSession.sampleBoneTransform(lane.bone);
                AnimatorTimelineSession.putKey(lane.bone, lane.channel, channelValue(sampled, lane.channel));
            }
            selection.add(new KeyId(lane.id, t));
        }
        if (row.bone != null) AnimatorTimelineSession.selectBone(row.bone);
        syncPrimarySelection();
        growClipToSelection(clip);
        if (AnimatorTimelineSession.isPlaying()) AnimatorTimelineSession.setTime(restore);
    }

    private static float[] channelValue(float[] transform, int channel) {
        return switch (channel) {
            case 1 -> new float[]{transform[0], transform[1], transform[2]};
            case 2 -> new float[]{transform[6], transform[7], transform[8]};
            default -> new float[]{transform[3], transform[4], transform[5]};
        };
    }

    private void setSelectionLerp(EditorAnimationDocument.Clip clip, int lerp) {
        if (selection.isEmpty()) return;
        AnimatorTimelineSession.pushUndo();
        for (KeyId id : selection) {
            Lane lane = laneById.get(id.lane());
            if (lane == null) continue;
            if (lane.property) {
                EditorAnimationDocument.PropertyChannel channel = propertyChannel(clip, lane);
                EditorAnimationDocument.PropertyKey key = channel == null ? null : channel.keys.get(id.time());
                if (key != null) {
                    channel.keys.put(id.time(), new EditorAnimationDocument.PropertyKey(key.value, lerp));
                }
            } else {
                EditorAnimationDocument.BoneChannels channels = clip.bones.get(lane.bone);
                EditorAnimationDocument.Key key = channels == null
                        ? null : channels.channel(lane.channel).get(id.time());
                if (key != null) key.lerp = lerp;
            }
        }
    }

    private void deleteSelection(EditorAnimationDocument.Clip clip) {
        if (selection.isEmpty()) return;
        AnimatorTimelineSession.pushUndo();
        for (KeyId id : new ArrayList<>(selection)) {
            Lane lane = laneById.get(id.lane());
            if (lane == null) continue;
            if (lane.property) {
                EditorAnimationDocument.PropertyChannel channel = propertyChannel(clip, lane);
                if (channel != null) channel.keys.remove(id.time());
            } else {
                EditorAnimationDocument.BoneChannels channels = clip.bones.get(lane.bone);
                if (channels != null) channels.channel(lane.channel).remove(id.time());
            }
        }
        selection.clear();
        AnimatorTimelineSession.clearKeySelection();
        clip.bones.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        clip.properties.values().forEach(target -> target.entrySet().removeIf(e -> e.getValue().keys.isEmpty()));
        clip.properties.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private void copySelection(EditorAnimationDocument.Clip clip) {
        clipboard.clear();
        if (selection.isEmpty()) return;
        float earliest = Float.MAX_VALUE;
        for (KeyId id : selection) earliest = Math.min(earliest, id.time());
        for (KeyId id : selection) {
            Lane lane = laneById.get(id.lane());
            if (lane == null) continue;
            ClipboardEntry entry = new ClipboardEntry();
            entry.laneId = lane.id;
            entry.offset = id.time() - earliest;
            if (lane.property) {
                EditorAnimationDocument.PropertyChannel channel = propertyChannel(clip, lane);
                EditorAnimationDocument.PropertyKey key = channel == null ? null : channel.keys.get(id.time());
                if (key == null) continue;
                entry.value = key.value.deepCopy();
                entry.lerp = key.lerp;
            } else {
                EditorAnimationDocument.BoneChannels channels = clip.bones.get(lane.bone);
                EditorAnimationDocument.Key key = channels == null
                        ? null : channels.channel(lane.channel).get(id.time());
                if (key == null) continue;
                entry.vector = key.v.clone();
                entry.lerp = key.lerp;
            }
            clipboard.add(entry);
        }
    }

    private void pasteClipboard(EditorAnimationDocument.Clip clip) {
        if (clipboard.isEmpty()) return;
        AnimatorTimelineSession.pushUndo();
        float base = AnimatorTimelineSession.quantize(AnimatorTimelineSession.time());
        selection.clear();
        for (ClipboardEntry entry : clipboard) {
            Lane lane = laneById.get(entry.laneId);
            if (lane == null) continue;
            float t = AnimatorTimelineSession.quantize(base + entry.offset);
            if (lane.property) {
                EditorAnimationDocument.PropertyChannel channel = propertyChannel(clip, lane);
                if (channel == null || entry.value == null) continue;
                channel.keys.put(t, new EditorAnimationDocument.PropertyKey(entry.value.deepCopy(), entry.lerp));
            } else {
                if (entry.vector == null) continue;
                clip.bones.computeIfAbsent(lane.bone, ignored -> new EditorAnimationDocument.BoneChannels())
                        .channel(lane.channel)
                        .put(t, new EditorAnimationDocument.Key(entry.vector.clone(), entry.lerp));
            }
            selection.add(new KeyId(lane.id, t));
        }
        syncPrimarySelection();
        growClipToSelection(clip);
    }

    private void growClipToSelection(EditorAnimationDocument.Clip clip) {
        for (KeyId id : selection) {
            if (id.time() > clip.lengthSec) clip.lengthSec = id.time();
        }
    }

    /** Mirror the newest selected key into the session so preview / hierarchy stay in sync. */
    private void syncPrimarySelection() {
        KeyId last = null;
        for (KeyId id : selection) last = id;
        if (last == null) {
            AnimatorTimelineSession.clearKeySelection();
            return;
        }
        Lane lane = laneById.get(last.lane());
        if (lane == null || lane.property) {
            AnimatorTimelineSession.clearKeySelection();
            return;
        }
        AnimatorTimelineSession.selectKey(lane.bone, lane.channel, last.time());
    }

    private void handleShortcuts(EditorAnimationDocument.Clip clip) {
        if (!AnimatorTimelineSession.isTimelineFocused()
                || ImGui.getIO().getWantTextInput() || ImGui.isAnyItemActive()) return;
        boolean ctrl = ImGui.getIO().getKeyCtrl();
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.C)) copySelection(clip);
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.V)) pasteClipboard(clip);
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.S)) AnimatorTimelineSession.save();
        if (ctrl && ImGui.isKeyPressed(ImGuiKey.A)) {
            selection.clear();
            for (Lane lane : laneById.values()) {
                for (Float t : laneTimes(clip, lane)) selection.add(new KeyId(lane.id, t));
            }
            syncPrimarySelection();
        }
        if (ctrl && (ImGui.isKeyPressed(ImGuiKey.Z) || ImGui.isKeyPressed(ImGuiKey.Y))) {
            selection.clear();
        }
        if (ImGui.isKeyPressed(ImGuiKey.Delete)) deleteSelection(clip);
        if (ImGui.isKeyPressed(ImGuiKey.Space)) {
            AnimatorTimelineSession.setPlaying(!AnimatorTimelineSession.isPlaying());
        }
        if (ImGui.isKeyPressed(ImGuiKey.F)) fitRequested = true;
        if (ImGui.isKeyPressed(ImGuiKey.Home)) {
            AnimatorTimelineSession.setPlaying(false);
            AnimatorTimelineSession.setTime(0f);
        }
        if (ImGui.isKeyPressed(ImGuiKey.End)) {
            AnimatorTimelineSession.setPlaying(false);
            AnimatorTimelineSession.setTime(clip.lengthSec);
        }
        if (ImGui.isKeyPressed(ImGuiKey.LeftArrow)) AnimatorTimelineSession.stepFrames(-1);
        if (ImGui.isKeyPressed(ImGuiKey.RightArrow)) AnimatorTimelineSession.stepFrames(1);
    }

    // ---------------------------------------------------------- lane helpers

    private static NavigableSet<Float> laneTimes(EditorAnimationDocument.Clip clip, Lane lane) {
        if (lane.property) {
            EditorAnimationDocument.PropertyChannel channel = propertyChannel(clip, lane);
            return channel == null ? Collections.emptyNavigableSet() : channel.keys.navigableKeySet();
        }
        EditorAnimationDocument.BoneChannels channels = clip.bones.get(lane.bone);
        return channels == null ? Collections.emptyNavigableSet() : channels.channel(lane.channel).navigableKeySet();
    }

    private static EditorAnimationDocument.PropertyChannel propertyChannel(
            EditorAnimationDocument.Clip clip, Lane lane) {
        Map<String, EditorAnimationDocument.PropertyChannel> target = clip.properties.get(lane.targetId);
        return target == null ? null : target.get(lane.propId);
    }

    private static int laneLerp(EditorAnimationDocument.Clip clip, Lane lane, float time) {
        if (lane.property) {
            EditorAnimationDocument.PropertyChannel channel = propertyChannel(clip, lane);
            EditorAnimationDocument.PropertyKey key = channel == null ? null : channel.keys.get(time);
            return key == null ? EditorAnimationDocument.Key.LERP_LINEAR : key.lerp;
        }
        EditorAnimationDocument.BoneChannels channels = clip.bones.get(lane.bone);
        EditorAnimationDocument.Key key = channels == null ? null : channels.channel(lane.channel).get(time);
        return key == null ? EditorAnimationDocument.Key.LERP_LINEAR : key.lerp;
    }

    private static boolean laneMoveKey(EditorAnimationDocument.Clip clip, Lane lane, float from, float to) {
        if (from == to) return false;
        if (lane.property) {
            EditorAnimationDocument.PropertyChannel channel = propertyChannel(clip, lane);
            if (channel == null || !channel.keys.containsKey(from)) return false;
            channel.keys.put(to, channel.keys.remove(from));
            return true;
        }
        EditorAnimationDocument.BoneChannels channels = clip.bones.get(lane.bone);
        if (channels == null) return false;
        TreeMap<Float, EditorAnimationDocument.Key> channel = channels.channel(lane.channel);
        if (!channel.containsKey(from)) return false;
        channel.put(to, channel.remove(from));
        return true;
    }

    private static String laneTitle(Lane lane) {
        if (!lane.property) return lane.bone + "  ·  " + channelName(lane.channel);
        SceneDocument scene = SceneEditorSession.document();
        SceneNode node = scene == null ? null : scene.findNode(lane.targetId);
        String name = node == null || node.name == null || node.name.isBlank() ? lane.targetId : node.name;
        return name + "  ·  " + lane.propId;
    }

    private static String channelName(int channel) {
        return switch (channel) {
            case 1 -> EditorI18n.t("Позиция");
            case 2 -> EditorI18n.t("Масштаб");
            default -> EditorI18n.t("Поворот");
        };
    }

    // --------------------------------------------------------------- utility

    private static String timeLabel(float time) {
        if (time >= 60f) {
            int minutes = (int) (time / 60f);
            return String.format(Locale.ROOT, "%d:%05.2f", minutes, time - minutes * 60f);
        }
        return String.format(Locale.ROOT, "%.2f", time);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    private static int withAlpha(int abgr, int alpha) {
        return (abgr & 0x00FFFFFF) | (alpha << 24);
    }

    /** ImGui packs colors as ABGR; safe to compute without an active context. */
    private static int rgba(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    /** Bone names of the animation target's geo model, in hierarchy order. */
    private static List<String> boneNames(SceneNode target) {
        List<String> result = new ArrayList<>();
        if (target == null) return result;
        if (target.type != SceneNodeType.NPC && target.type != SceneNodeType.MODEL) return result;
        if (target.model == null || target.model.isEmpty()) return result;
        SpGeoModel model = SpModelCache.getOrLoad(target.model);
        for (SpBone root : model.rootBones) collectBones(root, result);
        return result;
    }

    private static void collectBones(SpBone bone, List<String> out) {
        out.add(bone.name);
        for (SpBone child : bone.children) collectBones(child, out);
    }
}
