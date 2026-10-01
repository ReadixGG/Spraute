package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImGui;
import imgui.ImGuiInputTextCallbackData;
import imgui.callback.ImGuiInputTextCallback;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import org.zonarstudio.spraute_engine.client.UiTextMarkup;
import org.zonarstudio.spraute_engine.client.UiTextStyle;
import org.zonarstudio.spraute_engine.network.CameraPacket;
import org.zonarstudio.spraute_engine.scene.PlayerCameraPose;
import org.zonarstudio.spraute_engine.scene.PostProcessSettings;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeIds;
import org.zonarstudio.spraute_engine.scene.SceneNodeTags;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.ScenePostProcessRuntime;
import org.zonarstudio.spraute_engine.scene.SceneTransform;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.zonarstudio.spraute_engine.client.UiImageFill;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;
import org.zonarstudio.spraute_engine.client.editor.icons.EditorIcons;
import org.zonarstudio.spraute_engine.ui.UiChildAlign;
import org.zonarstudio.spraute_engine.ui.UiGridLayout;

/**
 * Inspector: transform fields first (for every transformable node),
 * then NPC name, asset pickers with search, preview toggle and 3D model preview.
 */
public final class InspectorPanel {
    private static final long PLAYER_DRAG_GRACE_MS = 900L;
    private static final Map<String, PlayerDragHold> PLAYER_DRAG = new HashMap<>();

    private static final class PlayerDragHold {
        float value;
        boolean active;
        long graceUntil;
    }
    private final ResourceInspector resourceInspector = new ResourceInspector();
    private final CustomNodeInspector customNodeInspector = new CustomNodeInspector();
    private final DropRulesEditor dropRulesEditor = new DropRulesEditor();
    private final PlayerVarsInspector playerVarsInspector = new PlayerVarsInspector();
    private final ImString nameBuffer = new ImString(128);
    private final ImString modelSearch = new ImString(64);
    private final ImString textureSearch = new ImString(64);
    private final ImString shapeTextBuffer = new ImString(256);
    private final ImString attractorTargetBuffer = new ImString(64);
    private final ImString videoSearch = new ImString(64);
    private final ImString animationSearch = new ImString(64);
    private final ImString groupBuffer = new ImString(128);
    private final ImString headBoneBuffer = new ImString(128);
    private final ImString idleBuffer = new ImString(128);
    private final ImString walkBuffer = new ImString(128);
    private final ImString flyIdleBuffer = new ImString(128);
    private final ImString flyBuffer = new ImString(128);
    private final ImString swimIdleBuffer = new ImString(128);
    private final ImString swimBuffer = new ImString(128);
    private final ImString deathBuffer = new ImString(128);
    private final ImString skinBuffer = new ImString(128);
    private final ImString attackHandBuffer = new ImString(32);
    private final ImString attackAnimationsBuffer = new ImString(256);
    private final ImString routeFileBuffer = new ImString(256);
    private final ImString idBuffer = new ImString(128);
    private final ImString hitboxTriggerIdBuffer = new ImString(128);
    private final ImString uiTextBuffer = new ImString(2048);
    private int uiMarkSelStart;
    private int uiMarkSelEnd;
    private int uiMarkStickyStart;
    private int uiMarkStickyEnd;
    private String uiMarkPending;
    private int uiMarkPendingCursor = -1;
    private boolean uiMarkFieldActive;
    private boolean uiMarkShowTags;
    private final float[] uiMarkColor = {1f, 0.333f, 0.333f, 1f};
    private final float[] uiMarkGlowColor = {1f, 1f, 1f, 0.4f};
    private final float[] uiMarkOutlineColor = {0f, 0f, 0f, 1f};
    private final float[] uiMarkUnderlineColor = {1f, 1f, 1f, 1f};
    private final float[] uiMarkShadowColor = {0f, 0f, 0f, 0.55f};
    private final float[] uiMarkGradientA = {1f, 0.33f, 0.33f, 1f};
    private final float[] uiMarkGradientB = {0.33f, 0.67f, 1f, 1f};
    private final float[] uiMarkSpeed = {1f};
    private final float[] uiMarkAmount = {1f};
    private final float[] uiMarkGlowSize = {3f};
    private final float[] uiMarkOutlineWidth = {1f};
    private final float[] uiMarkUnderlineThickness = {1f};
    private final float[] uiMarkUnderlineOffset = {0f};
    private final float[] uiMarkShadowX = {1f};
    private final float[] uiMarkShadowY = {1f};
    private final float[] uiMarkShadowSize = {2f};
    private boolean uiMarkPixelGlow;
    private boolean uiMarkPixelOutline = true;
    private boolean uiMarkPixelShadow = true;
    private final ImGuiInputTextCallback uiMarkCallback = new ImGuiInputTextCallback() {
        @Override
        public void accept(ImGuiInputTextCallbackData data) {
            String buf = data.getBuf();
            if (buf == null) buf = "";
            if (uiMarkPending != null && data.hasEventFlag(ImGuiInputTextFlags.CallbackAlways)) {
                String rewrite = uiMarkPending;
                int caret = uiMarkPendingCursor;
                uiMarkPending = null;
                uiMarkPendingCursor = -1;
                int oldLen = data.getBufTextLen();
                if (oldLen > 0) data.deleteChars(0, oldLen);
                data.insertChars(0, rewrite);
                buf = rewrite;
                int b = markCharToUtf8(buf, Math.max(0, Math.min(caret, buf.length())));
                data.setCursorPos(b);
                data.setSelectionStart(b);
                data.setSelectionEnd(b);
            }
            uiMarkSelStart = markUtf8ToChar(buf, data.getSelectionStart());
            uiMarkSelEnd = markUtf8ToChar(buf, data.getSelectionEnd());
            if (uiMarkSelStart != uiMarkSelEnd) {
                uiMarkStickyStart = uiMarkSelStart;
                uiMarkStickyEnd = uiMarkSelEnd;
            }
        }
    };
    private final ImString uiColorBuffer = new ImString(32);
    private final ImString uiHoverBuffer = new ImString(32);
    private final ImString uiGradientBuffer = new ImString(32);
    private final ImString uiBorderColorBuffer = new ImString(32);
    private final ImString uiPlaceholderBuffer = new ImString(128);
    private final ImString uiTooltipBuffer = new ImString(256);
    private final ImString uiDragGroupBuffer = new ImString(128);
    private final ImString uiDragPayloadBuffer = new ImString(128);
    private final ImString tagsBuffer = new ImString(512);
    private final ImString newTagBuffer = new ImString(64);
    private final ImString mobSearch = new ImString(64);
    private final float[] colorTmp = new float[4];

    private final float[] position = new float[3];
    private final float[] rotation = new float[3];
    private final float[] scale = new float[3];
    private final float[] single = new float[1];
    private final float[] hitboxSize = new float[2];
    private final float[] hitboxDepth = new float[1];
    private final float[] cameraOrbitAngles = new float[2];
    private final float[] cameraPivot = new float[3];
    private final float[] cameraHoldWorld = new float[3];
    private final float[] hitboxOffset = new float[3];
    private final float[] particleSpread = new float[3];
    private final float[] particleLine = new float[3];
    private final float[] particleDir = new float[3];
    private final ImString particleSearch = new ImString(64);
    private final ImString soundSearch = new ImString(64);

    private boolean cropDragActive;
    private int cropDragKind;
    private int cropResizeCorner;
    private float cropDragU0;
    private float cropDragV0;
    private int cropOrigU, cropOrigV, cropOrigW, cropOrigH;

    /** Targets for the current inspector pass (1 = single edit, N = multi-edit). */
    private List<SceneNode> editTargets = List.of();
    private SceneNode multiSnap;
    /** Factory-default node of the same type — reset target for inspector fields. */
    private SceneNode factoryNode;
    private boolean hasResetDefault;
    private Object resetDefault;

    private static final Set<String> NO_MULTI_PROPAGATE = Set.of("name", "id", "children", "transform");

    public void render() {
        EditorInspectorUi.setAnimationFieldAction(null);
        EditorInspectorUi.beginPass();
        factoryNode = null;
        hasResetDefault = false;
        resetDefault = null;
        if (SceneEditorSession.document() == null) {
            ImGui.textDisabled(EditorI18n.t("Выберите ноду в иерархии"));
            return;
        }

        List<SceneNode> selected = SceneEditorSession.selectedNodes();
        if (selected.isEmpty()) {
            ImGui.textDisabled(EditorI18n.t("Выберите ноду в иерархии"));
            return;
        }

        int selCount = selected.size();
        boolean multi = selCount > 1;
        SceneNodeType commonType = selected.get(0).type;
        boolean sameType = true;
        for (SceneNode n : selected) {
            if (n.type != commonType) {
                sameType = false;
                break;
            }
        }

        SceneNode primary = SceneEditorSession.selectedNode();
        if (primary == null) primary = selected.get(0);
        // Multi-edit snap/propagate assumes index 0 is the node the UI mutates (primary).
        if (multi && primary != selected.get(0)) {
            List<SceneNode> ordered = new ArrayList<>(selCount);
            ordered.add(primary);
            for (SceneNode n : selected) {
                if (n != primary) ordered.add(n);
            }
            editTargets = ordered;
        } else {
            editTargets = selected;
        }
        final SceneNode node = primary;
        EditorInspectorUi.setClipboardNode(node.id);
        factoryNode = node.type == SceneNodeType.CUSTOM
                ? SceneNode.custom(node.customTypeId, "")
                : SceneNode.typeTemplate(node.type);
        if (!multi && node.type != SceneNodeType.ANIMATOR && node.type != SceneNodeType.SCENE
                && node.type != SceneNodeType.PLAYERS && node.type != SceneNodeType.PLAYER
                && node.type != SceneNodeType.CAMERA && node.type != SceneNodeType.AUDIO
                && node.type != SceneNodeType.POST_PROCESS
                && node.type != SceneNodeType.ROUTE_POINT
                && node.type != SceneNodeType.GLOBAL_WORLD && node.type != SceneNodeType.LOCAL_WORLD) {
            EditorInspectorUi.setAnimationFieldAction((label, script) ->
                    AnimatorTimelineSession.addInspectorProperty(node, label, script));
        }

        if (multi) {
            ImGui.textColored(0.75f, 0.82f, 0.95f, 1f, EditorI18n.t("Выбрано объектов: ") + selCount
                    + (sameType ? " · " + typeLabel(commonType) : EditorI18n.t(" · разные типы")));
            EditorInspectorUi.hint(sameType
                    ? EditorI18n.t("Общие параметры · разные значения → правка ставит одно у всех (кроме имени)")
                    : EditorI18n.t("Только общие поля (active / lock / transform) · имя не меняется"));
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
        }

        String selectedBone = AnimatorTimelineSession.selectedBone();
        SceneNode boneOwner = boneEditorOwner(node);
        if (!multi && selectedBone != null && boneOwner != null) {
            EditorInspectorUi.nodeHeader(EditorI18n.t("КОСТЬ"), new float[]{1f, .62f, .17f},
                    selectedBone, boneOwner.id + "/" + selectedBone, false);
            renderBoneEditor(boneOwner);
            ImGui.dummy(1f, 12f);
            return;
        }

        float[] typeColor = typeColor(node.type);
        if (multi) {
            EditorInspectorUi.nodeHeader(sameType ? typeLabel(commonType) : "MULTI",
                    typeColor, selCount + EditorI18n.t(" выбрано"), "", false);
        } else {
            EditorInspectorUi.nodeHeader(typeLabel(node.type), typeColor, node.name, node.id,
                    node.type != SceneNodeType.SCENE
                            && node.type != SceneNodeType.PLAYERS
                            && node.type != SceneNodeType.ROUTE_POINT
                            && node.type != SceneNodeType.GLOBAL_WORLD
                            && node.type != SceneNodeType.LOCAL_WORLD);
        }

        if (!multi && node.type != SceneNodeType.SCENE
                && node.type != SceneNodeType.PLAYERS
                && node.type != SceneNodeType.ROUTE_POINT
                && node.type != SceneNodeType.GLOBAL_WORLD && node.type != SceneNodeType.LOCAL_WORLD) {
            idBuffer.set(node.id);
            if (EditorInspectorUi.inputText(EditorI18n.t("Идентификатор"), "node-id", idBuffer,
                    "_node.id\n_scene.child(\"" + node.id + "\")")) {
                String candidate = idBuffer.get().trim();
                if (!candidate.isEmpty() && !candidate.equals(node.id)) {
                    SceneEditorSession.renameSelectedNodeId(candidate);
                    ResourceInspector.onIdRenamed(SceneEditorSession.selectedNode());
                }
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.undoOnActivate();
            if (node.type.isUi()) {
                EditorInspectorUi.hint(EditorI18n.t("Наведите на поле — подсказка · _node.uiText · uiUpdate(player, id, …)"));
            } else {
                EditorInspectorUi.hint(EditorI18n.t("Наведите на поле — подсказка скрипта · _node.x · _node.transform.x"));
            }
        }

        boolean allNonScene = selected.stream().allMatch(n -> n.type != SceneNodeType.SCENE
                && n.type != SceneNodeType.PLAYERS && n.type != SceneNodeType.PLAYER
                && n.type != SceneNodeType.CAMERA && n.type != SceneNodeType.AUDIO
                && n.type != SceneNodeType.POST_PROCESS
                && n.type != SceneNodeType.ROUTE_POINT
                && n.type != SceneNodeType.GLOBAL_WORLD && n.type != SceneNodeType.LOCAL_WORLD);
        if (allNonScene) {
            rst(factoryNode.active).checkbox(EditorI18n.t("Активна (active)"), node.active, value -> {
                node.active = value;
                ClientPlayerSkinEdits.pushIfSkinRelated(SceneEditorSession.document(), node);
            },
                    "_node.active = true");
            if (!node.active) {
                EditorInspectorUi.hint(EditorI18n.t("Нода выключена: при запуске сцены она и все её дети ничего не делают"));
            }
            rst(factoryNode.locked).checkbox(EditorI18n.t("Замок (не выбирать в мире)"), node.locked, value -> node.locked = value,
                    "_node.locked = true");
            if (node.locked) {
                EditorInspectorUi.hint(EditorI18n.t("Нода и все дети не выбираются кликом во вьюпорте — только через иерархию"));
            } else if (!multi) {
                SceneDocument doc = SceneEditorSession.document();
                if (doc != null && doc.isEffectivelyLocked(node)) {
                    EditorInspectorUi.hint(EditorI18n.t("Замок унаследован от родителя — во вьюпорте не выбирается"));
                }
            }
        }

        if (!multi && node.type != SceneNodeType.PLAYERS && node.type != SceneNodeType.PLAYER
                && node.type != SceneNodeType.CAMERA && node.type != SceneNodeType.AUDIO
                && node.type != SceneNodeType.POST_PROCESS
                && node.type != SceneNodeType.ROUTE_POINT
                && node.type != SceneNodeType.GLOBAL_WORLD && node.type != SceneNodeType.LOCAL_WORLD) {
            renderTags(node);
        } else if (multi && sameType
                && node.type != SceneNodeType.PLAYERS && node.type != SceneNodeType.PLAYER
                && node.type != SceneNodeType.CAMERA && node.type != SceneNodeType.AUDIO
                && node.type != SceneNodeType.POST_PROCESS
                && node.type != SceneNodeType.ROUTE_POINT
                && node.type != SceneNodeType.GLOBAL_WORLD && node.type != SceneNodeType.LOCAL_WORLD) {
            renderTags(node);
        }

        // GUI nodes are laid out in canvas pixels, not world units — except a world-space canvas,
        // which is a real object in the world and gets the normal position/rotation/scale block.
        boolean allTransform = selected.stream().allMatch(n -> n.hasTransform()
                && (!n.type.isUi() || (n.type == SceneNodeType.CANVAS && n.canvasWorldSpace))
                && n.type != SceneNodeType.ROUTE_POINT && n.type != SceneNodeType.CAMERA
                && n.type != SceneNodeType.AUDIO);
        if (allTransform) {
            renderTransform(node);
        } else if (!multi && node.type == SceneNodeType.SCENE) {
            renderSceneRoot(node);
        }

        // Type-specific: only when every selected node shares the type.
        if (!sameType) {
            ImGui.dummy(1f, 12f);
            return;
        }

        if (!isMulti() && (node.type == SceneNodeType.NPC || node.type == SceneNodeType.MODEL)) {
            ensureAnimatorChild(node);
        }
        if (node.type == SceneNodeType.NPC) renderNpc(node);
        if (node.type == SceneNodeType.MODEL) renderModel(node);
        if (node.type == SceneNodeType.BILLBOARD) renderBillboard(node);
        if (node.type == SceneNodeType.HITBOX) renderHitbox(node);
        if (node.type == SceneNodeType.PARTICLE) renderParticle(node);
        if (node.type == SceneNodeType.LIGHT) renderLight(node);
        if (node.type == SceneNodeType.AUDIO_SOURCE) renderAudioSource(node);
        if (node.type == SceneNodeType.POST_PROCESS) renderPostProcess(node);
        if (node.type == SceneNodeType.ROUTE) renderRoute(node);
        if (node.type == SceneNodeType.ROUTE_POINT) renderRoutePoint(node);
        if (node.type == SceneNodeType.PLAYERS) renderPlayers(node);
        if (node.type == SceneNodeType.GLOBAL_WORLD) renderWorldContainer(node, true);
        if (node.type == SceneNodeType.LOCAL_WORLD) renderWorldContainer(node, false);
        if (node.type == SceneNodeType.PLAYER) renderPlayer(node);
        if (node.type == SceneNodeType.CAMERA) renderCamera(node);
        if (node.type == SceneNodeType.AUDIO) renderPlayerAudio(node);
        if (node.type == SceneNodeType.SKIN_OVERLAY) renderSkinOverlay(node);
        if (node.type == SceneNodeType.CUSTOM) customNodeInspector.render(node);
        if (!multi) customNodeInspector.renderAttached(node);
        if (node.type.isResourceDef()) resourceInspector.render(node);
        if (node.type == SceneNodeType.SCRIPT) renderScript(node);
        if (node.type == SceneNodeType.VISUAL_SCRIPT) renderVisualScript(node);
        if (node.type == SceneNodeType.ANIMATOR) renderAnimator(node);
        if (node.type == SceneNodeType.CANVAS) renderCanvas(node);
        if (node.type.isUiWidget()) renderUiWidget(node);
        ImGui.dummy(1f, 12f);
    }

    private void renderRoute(SceneNode node) {
        if (!section("route", EditorI18n.t("Путь"))) return;
        rst(factoryNode.routeClosed).checkbox(EditorI18n.t("Соединить последнюю точку с первой"), node.routeClosed,
                value -> node.routeClosed = value,
                EditorI18n.t("Замыкает маршрут в кольцо."), "_node.closed = true");

        String curve = org.zonarstudio.spraute_engine.scene.SceneRouteStorage.normalizeCurve(node.routeCurve);
        EditorInspectorUi.fieldLabel(EditorI18n.t("Сглаживание"),
                EditorI18n.t("Как у камеры: прямые сегменты или плавная кривая без резких углов."),
                "_node.curve = \"smooth\"");
        ImGui.setNextItemWidth(-1f);
        String curvePreview = switch (curve) {
            case "smooth" -> EditorI18n.t("Плавная кривая");
            case "smooth_camera" -> EditorI18n.t("Плавная камера");
            default -> EditorI18n.t("По линиям");
        };
        if (ImGui.beginCombo("##route-curve", curvePreview)) {
            if (ImGui.selectable(EditorI18n.t("По линиям"), "linear".equals(curve))) {
                applyEdit(() -> node.routeCurve = "linear");
            }
            if (ImGui.selectable(EditorI18n.t("Плавная кривая"), "smooth".equals(curve))) {
                applyEdit(() -> node.routeCurve = "smooth");
            }
            if (ImGui.selectable(EditorI18n.t("Плавная камера"), "smooth_camera".equals(curve))) {
                applyEdit(() -> node.routeCurve = "smooth_camera");
            }
            ImGui.endCombo();
        }
        if (!"linear".equals(curve)) {
            hitboxDepth[0] = node.routeSmoothness;
            if (applyDrag(EditorI18n.t("Сила сглаживания"), "route-smooth",
                    hitboxDepth, 0.01f, 0f, 1f, factoryNode.routeSmoothness,
                    EditorI18n.t("0 — почти прямые углы, 1 — полная Catmull-Rom кривая."),
                    "_node.smoothness = 1")) {
                applyEdit(() -> node.routeSmoothness =
                        org.zonarstudio.spraute_engine.scene.SceneRouteStorage.clampSmoothnessEdit(hitboxDepth[0]));
            }
        }

        ImGui.separator();
        EditorInspectorUi.fieldLabel(EditorI18n.t("Раскладка дочерних объектов"),
                EditorI18n.t("Объекты идут сверху вниз по иерархии. Их локальный Transform остаётся индивидуальным смещением от места на пути."),
                "_node.spacing = 2");
        single[0] = node.routeLayoutSpacing;
        if (applyDrag(EditorI18n.t("Расстояние между объектами"), "route-layout-spacing",
                single, 0.05f, 0f, 1000f, factoryNode.routeLayoutSpacing, "_node.spacing = 2")) {
            applyEdit(() -> node.routeLayoutSpacing = Math.max(0f, single[0]));
        }
        single[0] = node.routeLayoutOffset;
        if (applyDrag(EditorI18n.t("Смещение вдоль пути"), "route-layout-offset",
                single, 0.05f, -10000f, 10000f, factoryNode.routeLayoutOffset, "_node.offset = 0")) {
            applyEdit(() -> node.routeLayoutOffset = single[0]);
        }

        String anchor = org.zonarstudio.spraute_engine.scene.SceneRouteLayout
                .normalizeAnchor(node.routeLayoutAnchor);
        String anchorLabel = switch (anchor) {
            case "center" -> EditorI18n.t("По центру пути");
            case "end" -> EditorI18n.t("От конца пути");
            default -> EditorI18n.t("От начала пути");
        };
        EditorInspectorUi.fieldLabel(EditorI18n.t("Выравнивание группы"), "_node.anchor = \"center\"");
        ImGui.setNextItemWidth(-1f);
        if (ImGui.beginCombo("##route-layout-anchor", anchorLabel)) {
            if (ImGui.selectable(EditorI18n.t("От начала пути"), "start".equals(anchor))) {
                applyEdit(() -> node.routeLayoutAnchor = "start");
            }
            if (ImGui.selectable(EditorI18n.t("По центру пути"), "center".equals(anchor))) {
                applyEdit(() -> node.routeLayoutAnchor = "center");
            }
            if (ImGui.selectable(EditorI18n.t("От конца пути"), "end".equals(anchor))) {
                applyEdit(() -> node.routeLayoutAnchor = "end");
            }
            ImGui.endCombo();
        }

        String overflow = org.zonarstudio.spraute_engine.scene.SceneRouteLayout
                .normalizeOverflow(node.routeLayoutOverflow);
        String overflowLabel = switch (overflow) {
            case "loop" -> EditorI18n.t("Повторять по кругу");
            case "extend" -> EditorI18n.t("Продолжать за краями");
            default -> EditorI18n.t("Остановить у края");
        };
        EditorInspectorUi.fieldLabel(EditorI18n.t("Если не помещаются"), "_node.overflow = \"loop\"");
        ImGui.setNextItemWidth(-1f);
        if (ImGui.beginCombo("##route-layout-overflow", overflowLabel)) {
            if (ImGui.selectable(EditorI18n.t("Остановить у края"), "clamp".equals(overflow))) {
                applyEdit(() -> node.routeLayoutOverflow = "clamp");
            }
            if (ImGui.selectable(EditorI18n.t("Повторять по кругу"), "loop".equals(overflow))) {
                applyEdit(() -> node.routeLayoutOverflow = "loop");
            }
            if (ImGui.selectable(EditorI18n.t("Продолжать за краями"), "extend".equals(overflow))) {
                applyEdit(() -> node.routeLayoutOverflow = "extend");
            }
            ImGui.endCombo();
        }
        rst(factoryNode.routeLayoutReverse).checkbox(EditorI18n.t("В обратном направлении"), node.routeLayoutReverse,
                value -> node.routeLayoutReverse = value,
                EditorI18n.t("Первый объект ставится у конца и движется к началу."), "_node.reverse = true");
        rst(factoryNode.routeLayoutAlignYaw).checkbox(EditorI18n.t("Поворачивать вдоль пути"), node.routeLayoutAlignYaw,
                value -> node.routeLayoutAlignYaw = value,
                EditorI18n.t("Yaw объекта следует направлению участка пути."), "_node.alignYaw = true");
        rst(factoryNode.routeLayoutAlignPitch).checkbox(EditorI18n.t("Наклонять по высоте пути"), node.routeLayoutAlignPitch,
                value -> node.routeLayoutAlignPitch = value,
                EditorI18n.t("Pitch следует подъёмам и спускам. Обычно выключено для стоящих персонажей."),
                "_node.alignPitch = true");
        ImGui.textDisabled(EditorI18n.t("Объектов: ")
                + org.zonarstudio.spraute_engine.scene.SceneRouteLayout.children(node).size());

        routeFileBuffer.set(node.routeFile == null || node.routeFile.isBlank()
                ? "routes/" + node.id + ".route.json" : node.routeFile);
        if (EditorInspectorUi.inputText(EditorI18n.t("Файл маршрута"), "route-file", routeFileBuffer,
                EditorI18n.t("Файл в spraute_engine/routes."),
                "_node.file = \"routes/" + node.id + ".route.json\"")) {
            applyEdit(() -> node.routeFile = routeFileBuffer.get().trim());
        }

        ImGui.textDisabled(EditorI18n.t("Точек: ") + org.zonarstudio.spraute_engine.scene.SceneRouteOps.points(node).size());
        boolean fast = RouteFastPlace.isActiveFor(node);
        if (EditorInspectorUi.primaryButton(fast
                ? EditorI18n.t("Выйти из быстрого режима")
                : EditorI18n.t("Быстрый режим"), -1f)) {
            if (fast) RouteFastPlace.stop();
            else RouteFastPlace.start(node);
        }
        if (fast) {
            EditorInspectorUi.hint(
                    EditorI18n.t("Точка ставится там, где камера, и смотрит туда же.")
                    + "  " + RouteFastPlace.hudHintLine());
        }
        if (EditorInspectorUi.primaryButton(EditorI18n.t("Добавить точку у игрока"), -1f)) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                applyEdit(() -> org.zonarstudio.spraute_engine.scene.SceneRouteOps.addPoint(
                        SceneEditorSession.document(), node, player.getX(), player.getY(), player.getZ()));
            }
        }
        EditorInspectorUi.hint(EditorI18n.t(
                "Прямые или плавные сегменты между точками."));
        EditorInspectorUi.scriptApi(
                "_node.curve = \"smooth\"\n_node.spacing = 2\n_node.anchor = \"center\"\n_node.overflow = \"loop\"\n_node.reverse = false\n_node.addPoint(x, y, z)");
        EditorInspectorUi.hint(EditorI18n.t(
                "НПС (followRoute) идёт патсфайндингом от точки к точке. Кривая — для камеры и flyRoute."));
        endSection();
    }

    private void renderRoutePoint(SceneNode node) {
        if (!section("route-point", EditorI18n.t("Точка маршрута"))) return;
        SceneTransform world = SceneEditorSession.document().worldTransform(node.id);
        if (world == null) world = node.transform.copy();
        dragFloat("X", (float) world.px, .05f, -30_000_000f, 30_000_000f,
                value -> setRoutePointWorld(node, "x", value), "_node.x");
        dragFloat("Y", (float) world.py, .05f, -2048f, 2048f,
                value -> setRoutePointWorld(node, "y", value), "_node.y");
        dragFloat("Z", (float) world.pz, .05f, -30_000_000f, 30_000_000f,
                value -> setRoutePointWorld(node, "z", value), "_node.z");
        ImGui.spacing();
        EditorInspectorUi.hint(EditorI18n.t(
                "Поворот точки = взгляд камеры. Гизмо: E (Rotate). Без флажка — взгляд вдоль пути."));
        dragFloat(EditorI18n.t("Yaw"), (float) node.transform.ry, .5f, -180f, 180f, value -> {
            node.transform.ry = value;
            node.routeHasLook = true;
            SceneEditorSession.markDirty();
            org.zonarstudio.spraute_engine.scene.SceneRouteOps.saveOwningRoute(
                    SceneEditorSession.document(), node);
        }, "_node.yaw");
        dragFloat(EditorI18n.t("Pitch"), (float) node.transform.rx, .5f, -90f, 90f, value -> {
            node.transform.rx = value;
            node.routeHasLook = true;
            SceneEditorSession.markDirty();
            org.zonarstudio.spraute_engine.scene.SceneRouteOps.saveOwningRoute(
                    SceneEditorSession.document(), node);
        }, "_node.pitch");
        boolean hasLook = applyToggle(EditorI18n.t("Взгляд из поворота"), node.routeHasLook, factoryNode.routeHasLook,
                EditorI18n.t("followRoute(..., lookAlong): yaw/pitch точки. Выкл — вдоль касательной пути."),
                "_node.hasLook = true");
        if (hasLook != node.routeHasLook) {
            node.routeHasLook = hasLook;
            SceneEditorSession.markDirty();
            org.zonarstudio.spraute_engine.scene.SceneRouteOps.saveOwningRoute(
                    SceneEditorSession.document(), node);
        }
        endSection();
    }

    private static void setRoutePointWorld(SceneNode node, String axis, double value) {
        SceneDocument document = SceneEditorSession.document();
        if (document == null || node == null) return;
        SceneTransform world = document.worldTransform(node.id);
        if (world == null) world = node.transform.copy();
        if ("x".equals(axis)) world.px = value;
        if ("y".equals(axis)) world.py = value;
        if ("z".equals(axis)) world.pz = value;
        SceneNode parent = document.findParent(node.id);
        SceneTransform parentWorld = parent == null || parent.type == SceneNodeType.SCENE
                ? null : document.worldTransform(parent.id);
        node.transform = SceneTransform.toLocal(parentWorld, world);
        SceneEditorSession.markDirty();
        org.zonarstudio.spraute_engine.scene.SceneRouteOps.saveOwningRoute(document, node);
    }

    private void renderPlayers(SceneNode node) {
        if (!section("players-system", EditorI18n.t("Игроки сцены"))) return;
        EditorInspectorUi.hint(EditorI18n.t(
                "Системная нода. Список автоматически повторяет всех игроков онлайн и не сохраняет их как объекты сцены."));
        ImGui.text(EditorI18n.t("Сейчас онлайн: ") + node.children.size());
        EditorInspectorUi.scriptApi(
                EditorI18n.t("Доступ из скрипта"),
                "_scene.node(\"players\").children()\n_scene.node(\"players\").child(\"PlayerName\")");
        endSection();
        playerVarsInspector.renderDefaults(node, this::applyEdit);
        renderCameraFields(node, true);
    }

    private void renderWorldContainer(SceneNode node, boolean global) {
        if (!section("world-container", EditorI18n.t("Мир"))) return;
        EditorInspectorUi.hint(global
                ? EditorI18n.t("Системный контейнер. Всё внутри спавнится один раз и видно/доступно "
                        + "сразу всем игрокам — так же, как работала сцена раньше.")
                : EditorI18n.t("Системный контейнер. Это шаблон: у каждого подключённого игрока "
                        + "будет своя приватная копия всего, что внутри (NPC, модели, хитбоксы, частицы, "
                        + "свет, звук, скрипты, canvas) — недоступная и невидимая для остальных игроков. "
                        + "Пока это только структура — реальный per-player спавн ещё не подключён."));
        ImGui.text(EditorI18n.t("Дочерних нод: ") + node.children.size());
        endSection();
    }

    private void renderCamera(SceneNode node) {
        EditorInspectorUi.scriptApi(EditorI18n.t("Камера игрока"),
                "player.camera\n_eventPlayer.camera.distance = 4\ncam.moveTo(x, y, z, yaw, pitch, time)\ncam.lookAt(target, true)\ncam.followRoute(route, duration)\ncam.returnToPlayer(0.5)");
        renderCameraFields(node, false);
    }

    private void renderPlayerAudio(SceneNode node) {
        EditorInspectorUi.scriptApi(EditorI18n.t("Аудио игрока"),
                "player.audio\nplayer.audio.play(\"minecraft:entity.experience_orb.pickup\", 1, 1, false)\nplayer.audio.volume = 0.8\nplayer.audio.stopAll()");
        if (!section("player-audio", EditorI18n.t("Аудио игрока"))) return;
        EditorInspectorUi.hint(EditorI18n.t(
                "Личный звук (не позиционный). playSound и блоки «Аудио» идут через эту ноду."));
        rst(factoryNode.audioEnabled).checkbox(EditorI18n.t("Включён"), node.audioEnabled, value -> {
            node.audioEnabled = value;
            ClientScenePlayerNodes.pushPlayerAudio(SceneEditorSession.document(), node);
        }, "_node.enabled = true\nplayer.audio.enabled = true");
        rst(factoryNode.audioVolume).dragFloat(EditorI18n.t("Громкость (master)"), node.audioVolume, 0.01f, 0f, 4f, value -> {
            node.audioVolume = value;
            ClientScenePlayerNodes.pushPlayerAudio(SceneEditorSession.document(), node);
        }, "_node.volume = 1\nplayer.audio.volume = 1");
        rst(factoryNode.audioPitch).dragFloat(EditorI18n.t("Тон (master pitch)"), node.audioPitch, 0.01f, 0.1f, 2f, value -> {
            node.audioPitch = value;
            ClientScenePlayerNodes.pushPlayerAudio(SceneEditorSession.document(), node);
        }, "_node.pitch = 1\nplayer.audio.pitch = 1");
        endSection();
    }

    private void renderSkinOverlay(SceneNode node) {
        EditorInspectorUi.scriptApi(EditorI18n.t("Наложение скина"),
                "overlay.texture = \"textures/skins/hat.png\"\noverlay.hideHat = true\noverlay.hideSkinLayer(\"jacket\")\nplayer.addOverlay(\"hat\", \"textures/skins/hat.png\")");
        if (!section("skin-overlay", EditorI18n.t("Наложение на скин"))) return;
        EditorInspectorUi.hint(EditorI18n.t(
                "Полная текстура скина 64×64 поверх текущего скина (включая руку от 1 лица). Прозрачные пиксели основу не трогают. Руки Steve/Alex (wide/slim) подгоняются под модель игрока. Порядок нод = порядок слоёв."));
        boolean changed = assetCombo(EditorI18n.t("Текстура скина"), node.texture, EditorAssetIndex.textures(),
                textureSearch, value -> node.texture = value, "_node.texture = \"textures/skins/hat.png\"");
        if (ImGui.smallButton(EditorI18n.t("Обновить список ассетов"))) EditorAssetIndex.refresh();
        EditorInspectorUi.hint(EditorI18n.t(
                "Второй слой этой текстуры наложения. Не зависит от hideHat у игрока."));
        int hideMask = node.hiddenSkinLayers;
        if (overlayLayerToggle(node, EditorI18n.t("Скрыть шапку (2 слой)"), "hideHat",
                org.zonarstudio.spraute_engine.scene.PlayerSkinLayers.HAT, hideMask)) changed = true;
        hideMask = node.hiddenSkinLayers;
        if (overlayLayerToggle(node, EditorI18n.t("Скрыть куртку (2 слой)"), "hideJacket",
                org.zonarstudio.spraute_engine.scene.PlayerSkinLayers.JACKET, hideMask)) changed = true;
        hideMask = node.hiddenSkinLayers;
        if (overlayLayerToggle(node, EditorI18n.t("Скрыть левый рукав (2 слой)"), "hideLeftSleeve",
                org.zonarstudio.spraute_engine.scene.PlayerSkinLayers.LEFT_SLEEVE, hideMask)) changed = true;
        hideMask = node.hiddenSkinLayers;
        if (overlayLayerToggle(node, EditorI18n.t("Скрыть правый рукав (2 слой)"), "hideRightSleeve",
                org.zonarstudio.spraute_engine.scene.PlayerSkinLayers.RIGHT_SLEEVE, hideMask)) changed = true;
        hideMask = node.hiddenSkinLayers;
        if (overlayLayerToggle(node, EditorI18n.t("Скрыть левую штанину (2 слой)"), "hideLeftPants",
                org.zonarstudio.spraute_engine.scene.PlayerSkinLayers.LEFT_PANTS, hideMask)) changed = true;
        hideMask = node.hiddenSkinLayers;
        if (overlayLayerToggle(node, EditorI18n.t("Скрыть правую штанину (2 слой)"), "hideRightPants",
                org.zonarstudio.spraute_engine.scene.PlayerSkinLayers.RIGHT_PANTS, hideMask)) changed = true;
        EditorInspectorUi.scriptApi(
                "overlay.hideHat = true\noverlay.hideJacket = true\noverlay.hideSkinLayer(\"sleeves\")");
        endSection();
        if (changed) {
            SceneEditorSession.markDirty();
            ClientPlayerSkinEdits.pushIfSkinRelated(SceneEditorSession.document(), node);
        }
    }

    private void renderCameraFields(SceneNode node, boolean defaults) {
        String title = defaults ? EditorI18n.t("Камера по умолчанию") : EditorI18n.t("Камера игрока");
        if (!section(defaults ? "players-camera" : "player-camera", title)) return;
        if (!defaults) {
            EditorInspectorUi.hint(EditorI18n.t(
                    "В мире: фиолетовая пирамида — куда смотрит камера (FOV и взгляд)."));
        }

        boolean wasFollow = node.cameraFollowPlayer;
        boolean followPlayer = wasFollow;
        boolean changed = false;
        { boolean _n = applyToggle(EditorI18n.t("Следовать за игроком"), followPlayer, factoryNode.cameraFollowPlayer, EditorI18n.t("Вкл — смещение от глаз. Выкл — мировые координаты и свой взгляд."), "cam.followPlayer = true"); if (_n != followPlayer) { followPlayer = _n; changed = true; } }

        float[] off = position;
        float[] rot = rotation;
        loadCameraPoseBuffers(node, defaults, wasFollow, followPlayer, off, rot);

        boolean lockX = node.cameraLockX;
        boolean lockY = node.cameraLockY;
        boolean lockZ = node.cameraLockZ;
        float[] hold = cameraHoldWorld;
        hold[0] = (float) node.cameraWorldX;
        hold[1] = (float) node.cameraWorldY;
        hold[2] = (float) node.cameraWorldZ;

        if (followPlayer) {
            EditorInspectorUi.hint(EditorI18n.t(
                    "Смещение: X вправо, Y вверх, Z назад от глаз. Дистанция отодвигает камеру вдоль взгляда. Нули — вид от первого лица."));
            EditorInspectorUi.fieldLabel(EditorI18n.t("Смещение от глаз"), "cam.x / cam.y / cam.z");
            if (EditorInspectorUi.vector3("cam-off", off, 0.05f, -64f, 64f)) changed = true;
            hitboxDepth[0] = node.cameraDistance;
            if (applyDrag(EditorI18n.t("Дистанция (третье лицо)"), "cam-dist",
                    hitboxDepth, 0.05f, 0f, 32f, factoryNode.cameraDistance, "cam.distance = 4")) changed = true;
            ImGui.separator();
            EditorInspectorUi.hint(EditorI18n.t(
                    "Не следить по оси — камера остаётся на мировой координате, остальные оси едут за игроком."));
            { boolean _n = applyToggle(EditorI18n.t("Не следить по X"), lockX, factoryNode.cameraLockX, "cam.lockX = true\ncam.worldX = 100"); if (_n != lockX) { lockX = _n; if (lockX) seedCameraHoldAxis(node, defaults, 0, hold); changed = true; } }
            if (lockX) {
                EditorInspectorUi.fieldLabel(EditorI18n.t("Мир X"), "cam.worldX = 100");
                if (EditorInspectorUi.axisDrag("cam-hold-x", EditorInspectorUi.Axis.X, hold, 0,
                        0.05f, -30_000_000f, 30_000_000f)) changed = true;
            }
            { boolean _n = applyToggle(EditorI18n.t("Не следить по Y"), lockY, factoryNode.cameraLockY, "cam.lockY = true\ncam.worldY = 70"); if (_n != lockY) { lockY = _n; if (lockY) seedCameraHoldAxis(node, defaults, 1, hold); changed = true; } }
            if (lockY) {
                EditorInspectorUi.fieldLabel(EditorI18n.t("Мир Y"), "cam.worldY = 70");
                if (EditorInspectorUi.axisDrag("cam-hold-y", EditorInspectorUi.Axis.Y, hold, 1,
                        0.05f, -30_000_000f, 30_000_000f)) changed = true;
            }
            { boolean _n = applyToggle(EditorI18n.t("Не следить по Z"), lockZ, factoryNode.cameraLockZ, "cam.lockZ = true\ncam.worldZ = 0"); if (_n != lockZ) { lockZ = _n; if (lockZ) seedCameraHoldAxis(node, defaults, 2, hold); changed = true; } }
            if (lockZ) {
                EditorInspectorUi.fieldLabel(EditorI18n.t("Мир Z"), "cam.worldZ = 0");
                if (EditorInspectorUi.axisDrag("cam-hold-z", EditorInspectorUi.Axis.Z, hold, 2,
                        0.05f, -30_000_000f, 30_000_000f)) changed = true;
            }
        } else {
            EditorInspectorUi.hint(EditorI18n.t(
                    "Камера стоит в мире и не едет за игроком. Координаты — глобальные."));
            EditorInspectorUi.fieldLabel(EditorI18n.t("Позиция в мире"), "cam.x / cam.y / cam.z");
            if (EditorInspectorUi.vector3("cam-world", off, 0.05f, -30_000_000f, 30_000_000f)) changed = true;
        }

        float[] fov = colorTmp;
        fov[0] = node.cameraFov;
        if (applyDrag(EditorI18n.t("FOV (0 = ванильный)"), "cam-fov",
                fov, 0.5f, 0f, 170f, factoryNode.cameraFov, "cam.fov = 70")) changed = true;

        ImGui.separator();
        boolean followLook = followPlayer && !node.cameraLockYaw && !node.cameraLockPitch;
        boolean lockYaw = node.cameraLockYaw;
        boolean lockPitch = node.cameraLockPitch;
        if (followPlayer) {
            boolean nextFollowLook = applyToggle(EditorI18n.t("Следовать взгляду игрока"), followLook,
                    !factoryNode.cameraLockYaw && !factoryNode.cameraLockPitch,
                    EditorI18n.t("Выкл — камера сама смотрит куда задано ниже (мировой yaw/pitch)."),
                    "cam.lockRotation = false");
            if (nextFollowLook != followLook) {
                followLook = nextFollowLook;
                lockYaw = !followLook;
                lockPitch = !followLook;
                convertCameraLookBuffers(node, defaults, followLook, rot);
                changed = true;
            }
        }

        boolean showLook = !followPlayer || lockYaw || lockPitch;
        if (showLook) {
            EditorInspectorUi.fieldLabel(EditorI18n.t("Куда смотрит камера"),
                    EditorI18n.t("Мировой yaw / pitch."), "cam.yaw / cam.pitch");
            if (EditorInspectorUi.axisDrag("cam-look-yaw", EditorInspectorUi.Axis.Y, rot, 1, 0.5f, -180f, 180f)) {
                changed = true;
            }
            if (EditorInspectorUi.axisDrag("cam-look-pitch", EditorInspectorUi.Axis.X, rot, 0, 0.5f, -90f, 90f)) {
                changed = true;
            }
            if (followPlayer && (lockYaw != lockPitch)) {
                EditorInspectorUi.hint(EditorI18n.t(
                        "Заморожена только одна ось — вторая всё ещё следует за игроком."));
                boolean nextYaw = applyToggle(EditorI18n.t("Заморозить поворот (yaw)"), lockYaw,
                        factoryNode.cameraLockYaw, "cam.lockYaw = true");
                if (nextYaw != lockYaw) { lockYaw = nextYaw; changed = true; }
                boolean nextPitch = applyToggle(EditorI18n.t("Заморозить наклон (pitch)"), lockPitch,
                        factoryNode.cameraLockPitch, "cam.lockPitch = true");
                if (nextPitch != lockPitch) { lockPitch = nextPitch; changed = true; }
            }
        } else {
            EditorInspectorUi.fieldLabel(EditorI18n.t("Добавка к повороту"),
                    "cam.pitch / cam.yaw");
            if (EditorInspectorUi.axisDrag("cam-pitch", EditorInspectorUi.Axis.X, rot, 0, 0.5f, -90f, 90f)) changed = true;
            if (EditorInspectorUi.axisDrag("cam-yaw", EditorInspectorUi.Axis.Y, rot, 1, 0.5f, -180f, 180f)) changed = true;
        }

        boolean orbit = node.cameraOrbit;
        float[] orbitAngles = cameraOrbitAngles;
        float[] pivot = cameraPivot;
        orbitAngles[0] = node.cameraOrbitPitch;
        orbitAngles[1] = node.cameraOrbitYaw;
        pivot[0] = node.cameraPivotX;
        pivot[1] = node.cameraPivotY;
        pivot[2] = node.cameraPivotZ;
        if (followPlayer) {
            ImGui.separator();
            { boolean _n = applyToggle(EditorI18n.t("Орбита вокруг точки"), orbit, factoryNode.cameraOrbit, EditorI18n.t("Камера висит на точке рядом с игроком и всегда смотрит на неё, поэтому цель остаётся в центре кадра."), "cam.setOrbit(45, 20, 5)"); if (_n != orbit) { orbit = _n; changed = true; } }
            if (orbit) {
                EditorInspectorUi.fieldLabel(EditorI18n.t("Углы орбиты"), "cam.orbitPitch / cam.orbitYaw");
                if (EditorInspectorUi.axisDrag("cam-orbit-pitch", EditorInspectorUi.Axis.X,
                        orbitAngles, 0, 0.5f, -89f, 89f)) changed = true;
                if (EditorInspectorUi.axisDrag("cam-orbit-yaw", EditorInspectorUi.Axis.Y,
                        orbitAngles, 1, 0.5f, -180f, 180f)) changed = true;
                EditorInspectorUi.fieldLabel(EditorI18n.t("Точка вращения от глаз"),
                        "cam.pivotX / cam.pivotY / cam.pivotZ");
                if (EditorInspectorUi.vector3("cam-pivot", pivot, 0.05f, -32f, 32f)) changed = true;
            }
        } else {
            orbit = false;
        }

        boolean collide = node.cameraCollide;
        boolean showHand = node.cameraShowHand;
        if (followPlayer) {
            { boolean _n = applyToggle(EditorI18n.t("Не проходить сквозь блоки"), collide, factoryNode.cameraCollide, EditorI18n.t("Камера подтягивается к игроку и останавливается перед стеной вместо того, чтобы уходить внутрь неё."), "cam.collide = true"); if (_n != collide) { collide = _n; changed = true; } }
        } else {
            collide = false;
        }
        { boolean _n = applyToggle(EditorI18n.t("Показывать руку"), showHand, factoryNode.cameraShowHand, EditorI18n.t("При смещении или дистанции камеры по умолчанию рука и предмет в ней скрыты (как в третьем лице). Включите, чтобы оставить вид от первого лица."), "cam.showHand = true"); if (_n != showHand) { showHand = _n; changed = true; } }
        byte hideGui = node.cameraHideGui;
        byte pickedHideGui = cameraHideGuiCombo(hideGui);
        if (pickedHideGui != hideGui) {
            hideGui = pickedHideGui;
            changed = true;
        }
        ImGui.separator();
        boolean moveRelative = node.cameraMoveRelative;
        { boolean _n = applyToggle(EditorI18n.t("Игрок идёт относительно камеры"), moveRelative, factoryNode.cameraMoveRelative, EditorI18n.t("WASD считаются от взгляда камеры, а не от поворота тела. Если камера не следует за мышью, персонаж поворачивается в сторону ходьбы."), "cam.moveRelative = true"); if (_n != moveRelative) { moveRelative = _n; changed = true; } }
        boolean separateTurn = node.cameraSeparateTurn;
        { boolean _n = applyToggle(EditorI18n.t("Игрок поворачивается отдельно от камеры"), separateTurn, factoryNode.cameraSeparateTurn, EditorI18n.t("Мышь крутит камеру. Тело смотрит туда, куда идёт. WASD считаются от камеры."), "cam.separateTurn = true"); if (_n != separateTurn) { separateTurn = _n; changed = true; } }
        single[0] = node.cameraTurnSmooth;
        if (separateTurn) {
            if (applyDrag(EditorI18n.t("Плавность поворота игрока"), "cam-turn-s",
                    single, 0.01f, 0f, 1f, factoryNode.cameraTurnSmooth,
                    EditorI18n.t("0 — тело сразу смотрит куда идёт (или на цель). 1 — медленно доворачивается."),
                    "cam.turnSmooth = 0.4")) changed = true;
        }
        float turnSmooth = single[0];
        boolean screenRaycast = node.cameraScreenRaycast;
        { boolean _n = applyToggle(EditorI18n.t("Рейкаст в центр экрана"), screenRaycast, factoryNode.cameraScreenRaycast, EditorI18n.t("Ломание, постановка и прицел идут по центру экрана (куда смотрит камера), а не по повороту тела игрока."), "cam.screenRaycast = true"); if (_n != screenRaycast) { screenRaycast = _n; changed = true; } }
        boolean lookAtInteract = node.cameraLookAtInteract;
        { boolean _n = applyToggle(EditorI18n.t("Игрок смотрит на взаимодействие"), lookAtInteract, factoryNode.cameraLookAtInteract, EditorI18n.t("Пока игрок рубит, ставит, атакует или использует блок, тело смотрит на эту цель. Камера при отдельном повороте не следует за ним."), "cam.lookAtInteract = true"); if (_n != lookAtInteract) { lookAtInteract = _n; changed = true; } }
        ImGui.separator();
        boolean disableEffects = node.cameraDisableEffects;
        { boolean _n = applyToggle(EditorI18n.t("Отключить эффекты камеры"), disableEffects, factoryNode.cameraDisableEffects, EditorI18n.t("Ванильная ходьба (bobbing), тряска от урона и тошнота портала. Свои shake/punch и ход головы не трогает."), "cam.disableEffects = true"); if (_n != disableEffects) { disableEffects = _n; changed = true; } }
        boolean walkBob = node.cameraWalkBob;
        { boolean _n = applyToggle(EditorI18n.t("Ход головы при ходьбе"), walkBob, factoryNode.cameraWalkBob, EditorI18n.t("Как в Unity/Unreal: покачивание и удар при приземлении. Ванильный bob выключается. Не постпроцесс — двигается сама камера."), "cam.walkBob = true"); if (_n != walkBob) { walkBob = _n; changed = true; } }
        single[0] = node.cameraWalkBobAmount <= 0f ? 1f : node.cameraWalkBobAmount;
        float walkBobAmount = single[0];
        if (walkBob) {
            if (applyDrag(EditorI18n.t("Сила хода головы"), "cam-walk-bob",
                    single, 0.01f, 0f, 2f, factoryNode.cameraWalkBobAmount <= 0f ? 1f : factoryNode.cameraWalkBobAmount, "cam.walkBobAmount = 1")) changed = true;
            walkBobAmount = single[0];
        }
        boolean clearOcc = node.cameraClearOcclusion;
        { boolean _n = applyToggle(EditorI18n.t("Скрывать блоки перед целью"), clearOcc, factoryNode.cameraClearOcclusion, EditorI18n.t("Блоки между камерой и целью на клиенте пропадают (или становятся полупрозрачными), чтобы не закрывать кадр."), "cam.clearOcclusion = true"); if (_n != clearOcc) { clearOcc = _n; changed = true; } }
        single[0] = node.cameraOcclusionAlpha;
        if (clearOcc) {
            if (applyDrag(EditorI18n.t("Прозрачность скрытых (0 = невидимы)"), "cam-occ-a",
                    single, 0.01f, 0f, 1f, factoryNode.cameraOcclusionAlpha, "cam.occlusionAlpha = 0.35")) changed = true;
        }
        float occAlpha = single[0];
        single[0] = node.cameraOcclusionRadius;
        if (clearOcc) {
            if (applyDrag(EditorI18n.t("Толщина скрытия (блоки)"), "cam-occ-r",
                    single, 0.05f, 0f, 8f, factoryNode.cameraOcclusionRadius,
                    EditorI18n.t("Насколько широко вокруг луча камера→цель прячутся блоки. 0 — только прямая."),
                    "cam.occlusionRadius = 1.5")) changed = true;
        }
        float occRadius = single[0];
        EditorInspectorUi.scriptApi(
                "player.camera.disableEffects = true\nplayer.camera.walkBob = true\nplayer.camera.shake(0.5, 0.4)\nplayer.camera.punch(2, -4)\nplayer.camera.stopShake()");
        endSection();
        if (!changed) return;
        SceneEditorSession.pushUndo();
        node.cameraFollowPlayer = followPlayer;
        node.cameraLockYaw = followPlayer && lockYaw;
        node.cameraLockPitch = followPlayer && lockPitch;
        node.cameraLockX = followPlayer && lockX;
        node.cameraLockY = followPlayer && lockY;
        node.cameraLockZ = followPlayer && lockZ;
        node.cameraWorldX = hold[0];
        node.cameraWorldY = hold[1];
        node.cameraWorldZ = hold[2];
        node.cameraOrbit = followPlayer && orbit;
        node.cameraOrbitPitch = orbitAngles[0];
        node.cameraOrbitYaw = orbitAngles[1];
        node.cameraPivotX = pivot[0];
        node.cameraPivotY = pivot[1];
        node.cameraPivotZ = pivot[2];
        node.cameraCollide = followPlayer && collide;
        node.cameraShowHand = showHand;
        node.cameraHideGui = hideGui;
        node.cameraMoveRelative = moveRelative;
        node.cameraSeparateTurn = separateTurn;
        node.cameraTurnSmooth = turnSmooth;
        node.cameraScreenRaycast = screenRaycast;
        node.cameraLookAtInteract = lookAtInteract;
        node.cameraDisableEffects = disableEffects;
        node.cameraWalkBob = walkBob;
        node.cameraWalkBobAmount = walkBobAmount;
        node.cameraClearOcclusion = clearOcc;
        node.cameraOcclusionAlpha = clearOcc ? occAlpha : 0f;
        node.cameraOcclusionRadius = occRadius;
        if (defaults) {
            node.cameraRestX = off[0];
            node.cameraRestY = off[1];
            node.cameraRestZ = off[2];
            node.cameraRestPitch = rot[0];
            node.cameraRestYaw = rot[1];
            if (followPlayer) node.cameraDistance = hitboxDepth[0];
            node.cameraFov = fov[0];
            ClientScenePlayerNodes.pushDefaults(SceneEditorSession.document());
        } else {
            node.transform.px = off[0];
            node.transform.py = off[1];
            node.transform.pz = off[2];
            node.transform.rx = rot[0];
            node.transform.ry = rot[1];
            if (followPlayer) node.cameraDistance = hitboxDepth[0];
            node.cameraFov = fov[0];
            ClientScenePlayerNodes.pushAuthoring(SceneEditorSession.document(), node);
        }
        SceneEditorSession.markDirty();
    }

    private static byte cameraHideGuiCombo(byte current) {
        String shown = switch (current) {
            case CameraPacket.HIDE_ALL -> EditorI18n.t("всё");
            case CameraPacket.HIDE_MINECRAFT -> EditorI18n.t("майнкрафт");
            default -> EditorI18n.t("не скрывать");
        };
        EditorInspectorUi.fieldLabel(EditorI18n.t("Скрыть GUI"),
                EditorI18n.t("Майнкрафт — сердца, хотбар, рука. Всё — плюс интерфейсы Spraute (диалоги, UI). Затемнение/осветление экрана отдельно: не прячется здесь, только Ctrl+Shift+U."),
                "cam.hideGui = \"minecraft\"");
        ImGui.setNextItemWidth(-1f);
        byte next = current;
        if (ImGui.beginCombo("##cam-hide-gui", shown)) {
            if (ImGui.selectable(EditorI18n.t("не скрывать"), current == CameraPacket.HIDE_NONE)) {
                next = CameraPacket.HIDE_NONE;
            }
            if (ImGui.selectable(EditorI18n.t("майнкрафт"), current == CameraPacket.HIDE_MINECRAFT)) {
                next = CameraPacket.HIDE_MINECRAFT;
            }
            if (ImGui.selectable(EditorI18n.t("всё"), current == CameraPacket.HIDE_ALL)) {
                next = CameraPacket.HIDE_ALL;
            }
            ImGui.endCombo();
        }
        return next;
    }

    /** Fill inspector buffers; on follow-toggle, convert eye-local ↔ world without mutating the node for long. */
    private static void loadCameraPoseBuffers(SceneNode node, boolean defaults,
                                              boolean wasFollow, boolean followPlayer,
                                              float[] off, float[] rot) {
        if (defaults) {
            off[0] = node.cameraRestX;
            off[1] = node.cameraRestY;
            off[2] = node.cameraRestZ;
            rot[0] = node.cameraRestPitch;
            rot[1] = node.cameraRestYaw;
            if (wasFollow == followPlayer) return;
            net.minecraft.world.entity.player.Player player = Minecraft.getInstance().player;
            if (player == null) return;
            if (wasFollow) {
                PlayerCameraPose.World world = PlayerCameraPose.compute(player,
                        PlayerCameraPose.Rest.fromPlayers(node));
                off[0] = (float) world.x();
                off[1] = (float) world.y();
                off[2] = (float) world.z();
                rot[0] = world.pitch();
                rot[1] = world.yaw();
            } else {
                SceneNode tmp = new SceneNode();
                tmp.transform = new SceneTransform();
                tmp.cameraFollowPlayer = true;
                tmp.cameraDistance = node.cameraDistance;
                tmp.cameraLockYaw = false;
                tmp.cameraLockPitch = false;
                tmp.cameraOrbit = false;
                SceneTransform world = new SceneTransform();
                world.px = node.cameraRestX;
                world.py = node.cameraRestY;
                world.pz = node.cameraRestZ;
                world.rx = node.cameraRestPitch;
                world.ry = node.cameraRestYaw;
                PlayerCameraPose.snapLook(player, (float) world.ry, (float) world.rx);
                PlayerCameraPose.writeLocalFromWorld(player, tmp, world);
                off[0] = (float) tmp.transform.px;
                off[1] = (float) tmp.transform.py;
                off[2] = (float) tmp.transform.pz;
                rot[0] = (float) tmp.transform.rx;
                rot[1] = (float) tmp.transform.ry;
            }
            return;
        }

        SceneDocument doc = SceneEditorSession.document();
        if (wasFollow && !followPlayer) {
            SceneTransform world = doc == null ? node.transform : PlayerCameraPose.worldTransform(doc, node);
            off[0] = (float) world.px;
            off[1] = (float) world.py;
            off[2] = (float) world.pz;
            rot[0] = (float) world.rx;
            rot[1] = (float) world.ry;
            return;
        }
        if (!wasFollow && followPlayer && doc != null) {
            SceneTransform world = node.transform.copy();
            SceneTransform backup = node.transform.copy();
            boolean savedFollow = node.cameraFollowPlayer;
            boolean savedLockY = node.cameraLockYaw;
            boolean savedLockP = node.cameraLockPitch;
            boolean savedOrbit = node.cameraOrbit;
            // Body was free to turn (and walk-facing overwrote yaw) while the
            // camera sat in the world. Snap look to the camera first so the
            // restored follow pose has ~0 yaw additive and WASD matches the view.
            PlayerCameraPose.snapLook(PlayerCameraPose.findPlayer(doc, node),
                    (float) world.ry, (float) world.rx);
            // Rest additives must be 0 so boom axes match player look while decomposing world pose.
            node.cameraFollowPlayer = true;
            node.cameraLockYaw = false;
            node.cameraLockPitch = false;
            node.cameraOrbit = false;
            node.transform.px = 0;
            node.transform.py = 0;
            node.transform.pz = 0;
            node.transform.rx = 0;
            node.transform.ry = 0;
            PlayerCameraPose.writeLocalFromWorld(doc, node, world);
            off[0] = (float) node.transform.px;
            off[1] = (float) node.transform.py;
            off[2] = (float) node.transform.pz;
            rot[0] = (float) node.transform.rx;
            rot[1] = (float) node.transform.ry;
            node.transform.px = backup.px;
            node.transform.py = backup.py;
            node.transform.pz = backup.pz;
            node.transform.rx = backup.rx;
            node.transform.ry = backup.ry;
            node.cameraFollowPlayer = savedFollow;
            node.cameraLockYaw = savedLockY;
            node.cameraLockPitch = savedLockP;
            node.cameraOrbit = savedOrbit;
            return;
        }
        off[0] = (float) node.transform.px;
        off[1] = (float) node.transform.py;
        off[2] = (float) node.transform.pz;
        rot[0] = (float) node.transform.rx;
        rot[1] = (float) node.transform.ry;
    }

    private static void seedCameraHoldAxis(SceneNode node, boolean defaults, int axis, float[] hold) {
        net.minecraft.world.entity.Entity player = defaults
                ? Minecraft.getInstance().player
                : PlayerCameraPose.findPlayer(SceneEditorSession.document(), node);
        if (player == null) return;
        PlayerCameraPose.World world = defaults
                ? PlayerCameraPose.compute(player, PlayerCameraPose.Rest.fromPlayers(node))
                : PlayerCameraPose.compute(player, node);
        if (axis == 0) hold[0] = (float) world.x();
        else if (axis == 1) hold[1] = (float) world.y();
        else hold[2] = (float) world.z();
    }

    /** Additive ↔ absolute look when toggling «следовать взгляду». */
    private static void convertCameraLookBuffers(SceneNode node, boolean defaults,
                                                 boolean nowFollowLook, float[] rot) {
        net.minecraft.world.entity.Entity player = defaults
                ? Minecraft.getInstance().player
                : PlayerCameraPose.findPlayer(SceneEditorSession.document(), node);
        if (player == null) return;
        float pyaw = player.getYRot();
        float ppitch = player.getXRot();
        if (nowFollowLook) {
            PlayerCameraPose.snapLook(player, rot[1], rot[0]);
            rot[1] = 0f;
            rot[0] = 0f;
        } else {
            rot[1] += pyaw;
            rot[0] += ppitch;
        }
    }

    private void renderPlayer(SceneNode node) {
        net.minecraft.world.entity.player.Player player = clientPlayer(node);
        if (section("player-identity", EditorI18n.t("Игрок"))) {
            ImGui.text(node.name == null || node.name.isBlank() ? EditorI18n.t("Неизвестный игрок") : node.name);
            if (node.id != null && !node.id.isBlank()) {
                ImGui.textDisabled(EditorI18n.t("ID · ") + node.id);
            }
            ImGui.textDisabled(node.playerUuid == null ? "" : node.playerUuid);
            if (player == null) {
                EditorInspectorUi.hint(EditorI18n.t(
                        "Игрок находится в другом измерении: имя и UUID доступны, живые значения появятся рядом с ним."));
            }
            EditorInspectorUi.scriptApi(EditorI18n.t("Эта PLAYER-нода использует тот же API, что блоки «Игрок»"),
                    "player.skin = \"textures/skins/hero.png\"\nplayer.addOverlay(\"hat\", \"textures/skins/hat.png\")\nplayer.camera.distance = 4");
            endSection();
        }
        playerVarsInspector.renderLive(node);
        if (section("player-skin", EditorI18n.t("Скин"))) {
            String current = node.texture == null ? "" : node.texture;
            if (player != null) {
                Object live = org.zonarstudio.spraute_engine.script.ScriptPropertyAccess.get(
                        player, "skin", null);
                if (live instanceof String text) current = text;
            }
            boolean changed = assetCombo(EditorI18n.t("Скин (полный PNG)"), current, EditorAssetIndex.textures(),
                    textureSearch, value -> {
                        node.texture = value;
                        sendPlayerEdit(node, "skin", new com.google.gson.JsonPrimitive(value == null ? "" : value).toString());
                    }, "player.skin = \"textures/skins/hero.png\"");
            if (ImGui.smallButton(EditorI18n.t("Ванильный скин"))) {
                node.texture = "";
                sendPlayerEdit(node, "skin", "\"\"");
                changed = true;
            }
            EditorInspectorUi.hint(EditorI18n.t(
                    "Пусто — текущий скин игрока (лаунчер или Mojang). Замена сюда ложится снизу, наложения всегда сверху. Порядок Overlay-нод = порядок слоёв, включая руку от 1 лица."));
            EditorInspectorUi.hint(EditorI18n.t(
                    "Второй слой оригинального или установленного скина. Наложения не затрагивает — у Overlay свои флаги."));
            int hideMask = player != null
                    ? org.zonarstudio.spraute_engine.client.PlayerSkinLayersClient.mask(player.getUUID())
                    : node.hiddenSkinLayers;
            if (playerLayerToggle(node, EditorI18n.t("Скрыть шапку (2 слой)"), "hideHat",
                    org.zonarstudio.spraute_engine.scene.PlayerSkinLayers.HAT, hideMask)) changed = true;
            if (playerLayerToggle(node, EditorI18n.t("Скрыть куртку (2 слой)"), "hideJacket",
                    org.zonarstudio.spraute_engine.scene.PlayerSkinLayers.JACKET, hideMask)) changed = true;
            if (playerLayerToggle(node, EditorI18n.t("Скрыть левый рукав (2 слой)"), "hideLeftSleeve",
                    org.zonarstudio.spraute_engine.scene.PlayerSkinLayers.LEFT_SLEEVE, hideMask)) changed = true;
            if (playerLayerToggle(node, EditorI18n.t("Скрыть правый рукав (2 слой)"), "hideRightSleeve",
                    org.zonarstudio.spraute_engine.scene.PlayerSkinLayers.RIGHT_SLEEVE, hideMask)) changed = true;
            if (playerLayerToggle(node, EditorI18n.t("Скрыть левую штанину (2 слой)"), "hideLeftPants",
                    org.zonarstudio.spraute_engine.scene.PlayerSkinLayers.LEFT_PANTS, hideMask)) changed = true;
            if (playerLayerToggle(node, EditorI18n.t("Скрыть правую штанину (2 слой)"), "hideRightPants",
                    org.zonarstudio.spraute_engine.scene.PlayerSkinLayers.RIGHT_PANTS, hideMask)) changed = true;
            if (playerLayerToggle(node, EditorI18n.t("Скрыть плащ"), "hideCape",
                    org.zonarstudio.spraute_engine.scene.PlayerSkinLayers.CAPE, hideMask)) changed = true;
            EditorInspectorUi.scriptApi(
                    "player.hideHat = true\nplayer.hideJacket = true\nplayer.hideSkinLayer(\"sleeves\")");
            if (changed) SceneEditorSession.markDirty();
            endSection();
        }
        if (player == null) return;

        if (section("player-transform", EditorI18n.t("Положение"))) {
            playerNumber(node, "X", "x", player.getX(), .05f, -30_000_000f, 30_000_000f);
            playerNumber(node, "Y", "y", player.getY(), .05f, -2048f, 2048f);
            playerNumber(node, "Z", "z", player.getZ(), .05f, -30_000_000f, 30_000_000f);
            playerNumber(node, EditorI18n.t("Поворот"), "yaw", player.getYRot(), .5f, -360f, 360f);
            playerNumber(node, EditorI18n.t("Наклон"), "pitch", player.getXRot(), .5f, -90f, 90f);
            var look = player.getLookAngle();
            playerNumber(node, EditorI18n.t("Взгляд X"), "lookX", look.x, .01f, -1f, 1f);
            playerNumber(node, EditorI18n.t("Взгляд Y"), "lookY", look.y, .01f, -1f, 1f);
            playerNumber(node, EditorI18n.t("Взгляд Z"), "lookZ", look.z, .01f, -1f, 1f);
            EditorInspectorUi.hint(EditorI18n.t(
                    "Поворот/наклон — градусы. Взгляд XYZ — направление камеры. player.x / setPlayerPos / setPlayerLook"));
            endSection();
        }

        if (section("player-vitals", EditorI18n.t("Состояние"))) {
            playerNumber(node, EditorI18n.t("Здоровье"), "hp", player.getHealth(), .1f, 0f, 2048f);
            playerNumber(node, EditorI18n.t("Максимальное здоровье"), "maxHp", player.getMaxHealth(), .1f, 1f, 2048f);
            playerInteger(node, EditorI18n.t("Голод"), "hunger", player.getFoodData().getFoodLevel(), 0, 20);
            playerNumber(node, EditorI18n.t("Насыщение"), "saturation",
                    player.getFoodData().getSaturationLevel(), .1f, 0f, 20f);
            playerInteger(node, EditorI18n.t("Воздух"), "air", player.getAirSupply(), 0, player.getMaxAirSupply());
            playerNumber(node, EditorI18n.t("Поглощение"), "absorption",
                    player.getAbsorptionAmount(), .1f, 0f, 2048f);
            playerInteger(node, EditorI18n.t("Огонь (тики)"), "fireTicks", player.getRemainingFireTicks(), 0, 100000);
            playerInteger(node, EditorI18n.t("Замерзание (тики)"), "frozenTicks", player.getTicksFrozen(), 0, 100000);
            playerBoolActions(node, EditorI18n.t("Урон и регенерация"), "healthEnabled");
            playerBoolActions(node, EditorI18n.t("Голод"), "hungerEnabled");
            playerBoolActions(node, EditorI18n.t("Полоска опыта"), "xpVisible");
            endSection();
        }

        if (section("player-experience", EditorI18n.t("Опыт и инвентарь"))) {
            playerInteger(node, EditorI18n.t("Уровень опыта"), "xpLevel", player.experienceLevel, 0, Integer.MAX_VALUE);
            playerNumber(node, EditorI18n.t("Прогресс опыта"), "xpProgress",
                    player.experienceProgress, .01f, 0f, 1f);
            playerInteger(node, EditorI18n.t("Всего опыта"), "xpTotal", player.totalExperience, 0, Integer.MAX_VALUE);
            playerInteger(node, EditorI18n.t("Слот хотбара"), "selectedSlot",
                    player.getInventory().selected, 0, 8);
            ImGui.textDisabled(EditorI18n.t("Броня: ") + player.getArmorValue());
            endSection();
        }

        if (section("player-abilities", EditorI18n.t("Режим и способности"))) {
            String mode = "";
            try {
                var info = Minecraft.getInstance().getConnection().getPlayerInfo(player.getUUID());
                if (info != null && info.getGameMode() != null) mode = info.getGameMode().getName();
            } catch (RuntimeException ignored) {}
            playerGamemode(node, mode);
            playerToggle(node, EditorI18n.t("Разрешить полёт"), "flightEnabled", player.getAbilities().mayfly);
            playerToggle(node, EditorI18n.t("Сейчас летит"), "flying", player.getAbilities().flying);
            playerNumber(node, EditorI18n.t("Скорость полёта"), "flySpeed",
                    player.getAbilities().getFlyingSpeed(), .005f, 0f, 4f);
            playerNumber(node, EditorI18n.t("Скорость ходьбы"), "walkSpeed",
                    player.getAbilities().getWalkingSpeed(), .005f, 0f, 4f);
            playerToggle(node, EditorI18n.t("Неуязвим"), "invulnerable", player.getAbilities().invulnerable);
            playerToggle(node, EditorI18n.t("Бесконечное строительство"), "instabuild", player.getAbilities().instabuild);
            playerToggle(node, EditorI18n.t("Бежит"), "sprinting", player.isSprinting());
            playerToggle(node, EditorI18n.t("Крадётся"), "sneaking", player.isShiftKeyDown());
            if (player == Minecraft.getInstance().player) {
                playerToggle(node, EditorI18n.t("Запретить смену вида (F5)"), "lockPerspective",
                        org.zonarstudio.spraute_engine.client.PlayerControlHandler.isPerspectiveLocked());
            }
            endSection();
        }

        if (section("player-attributes", EditorI18n.t("Характеристики"))) {
            Double movement = org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.getAttributeValue(
                    player, org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.movementSpeedAttr());
            Double attack = org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.getAttributeValue(
                    player, org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.attackDamageAttr());
            boolean local = player == Minecraft.getInstance().player;
            double jump = local
                    ? org.zonarstudio.spraute_engine.client.ClientPlayerExtrasHolder.jumpPower()
                    : org.zonarstudio.spraute_engine.script.PlayerJumpStrengthOverrides.VANILLA;
            double dig = local
                    ? org.zonarstudio.spraute_engine.client.ClientPlayerExtrasHolder.digSpeed()
                    : 1d;
            playerNumber(node, EditorI18n.t("Скорость движения"), "movementSpeed",
                    movement == null ? 0d : movement, .005f, 0f, 32f);
            playerNumber(node, EditorI18n.t("Сила прыжка"), "jumpStrength",
                    jump, .01f, 0f, 32f);
            playerNumber(node, EditorI18n.t("Высота шага"), "stepHeight",
                    org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.getStepHeight(player),
                    .05f, 0f, 32f);
            playerNumber(node, EditorI18n.t("Урон атаки"), "attackDamage",
                    attack == null ? 0d : attack, .1f, 0f, 2048f);
            playerNumber(node, EditorI18n.t("Множитель копания"), "digSpeed", dig, .05f, 0f, 100f);
            Double reach = org.zonarstudio.spraute_engine.compat.SprautePlayerCompat.getBlockReach(player);
            playerNumber(node, EditorI18n.t("Дальность блоков"), "blockReach",
                    reach == null ? 4.5d : reach, .05f, 0f, 64f);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(EditorI18n.t("Как далеко можно ломать и ставить блоки. Ваниль ≈ 4.5."));
            }
            endSection();
        }
    }

    private static net.minecraft.world.entity.player.Player clientPlayer(SceneNode node) {
        if (node == null || node.playerUuid == null || node.playerUuid.isBlank()) return null;
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        try {
            return level.getPlayerByUUID(java.util.UUID.fromString(node.playerUuid));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String playerDragKey(SceneNode node, String property) {
        String uuid = node == null || node.playerUuid == null ? "" : node.playerUuid;
        return uuid + ":" + property;
    }

    private static float playerDragShown(String key, float current) {
        PlayerDragHold hold = PLAYER_DRAG.get(key);
        if (hold == null) return current;
        if (hold.active) return hold.value;
        if (System.currentTimeMillis() < hold.graceUntil) return hold.value;
        PLAYER_DRAG.remove(key);
        return current;
    }

    private static void playerDragAfter(SceneNode node, String property, float value, boolean changed) {
        String key = playerDragKey(node, property);
        boolean active = ImGui.isItemActive();
        if (changed) {
            sendPlayerEdit(node, property, Double.toString(value));
        }
        PlayerDragHold hold = PLAYER_DRAG.get(key);
        if (active) {
            if (hold == null) {
                hold = new PlayerDragHold();
                PLAYER_DRAG.put(key, hold);
            }
            hold.value = value;
            hold.active = true;
            hold.graceUntil = 0;
        } else if (hold != null && hold.active) {
            hold.value = value;
            hold.active = false;
            hold.graceUntil = System.currentTimeMillis() + PLAYER_DRAG_GRACE_MS;
        } else if (changed) {
            if (hold == null) {
                hold = new PlayerDragHold();
                PLAYER_DRAG.put(key, hold);
            }
            hold.value = value;
            hold.active = false;
            hold.graceUntil = System.currentTimeMillis() + PLAYER_DRAG_GRACE_MS;
        }
    }

    private static void playerNumber(SceneNode node, String label, String property,
                                     double current, float speed, float min, float max) {
        String key = playerDragKey(node, property);
        float[] value = {playerDragShown(key, (float) current)};
        EditorInspectorUi.fieldLabel(label, null, "player." + property);
        ImGui.setNextItemWidth(-1f);
        boolean changed = ImGui.dragFloat("##player-" + property, value, speed, min, max, "%.3f");
        playerDragAfter(node, property, value[0], changed);
    }

    private static void playerInteger(SceneNode node, String label, String property,
                                      int current, int min, int max) {
        String key = playerDragKey(node, property);
        int[] value = {Math.round(playerDragShown(key, current))};
        EditorInspectorUi.fieldLabel(label, null, "player." + property);
        ImGui.setNextItemWidth(-1f);
        boolean changed = ImGui.dragInt("##player-" + property, value, 1, min, max);
        playerDragAfter(node, property, value[0], changed);
    }

    private static boolean playerLayerToggle(SceneNode node, String label, String property, int bit, int mask) {
        boolean current = (mask & bit) == bit;
        boolean[] next = {current};
        EditorInspectorUi.bindReset(current, () -> next[0] = false);
        if (EditorInspectorUi.checkbox(label, current,
                EditorI18n.t("Второй слой оригинального или установленного скина. Не трогает наложения."),
                "player." + property + " = true")) {
            next[0] = !current;
        }
        if (next[0] == current) return false;
        sendPlayerEdit(node, property, Boolean.toString(next[0]));
        node.hiddenSkinLayers = next[0] ? (mask | bit) : (mask & ~bit);
        return true;
    }

    private static boolean overlayLayerToggle(SceneNode node, String label, String property, int bit, int mask) {
        boolean current = (mask & bit) == bit;
        boolean[] next = {current};
        EditorInspectorUi.bindReset(current, () -> next[0] = false);
        if (EditorInspectorUi.checkbox(label, current,
                EditorI18n.t("Второй слой этой текстуры наложения."),
                "overlay." + property + " = true")) {
            next[0] = !current;
        }
        if (next[0] == current) return false;
        node.hiddenSkinLayers = next[0] ? (mask | bit) : (mask & ~bit);
        return true;
    }

    private static void playerToggle(SceneNode node, String label, String property, boolean current) {
        boolean[] next = {current};
        EditorInspectorUi.bindReset(current, () -> next[0] = false);
        if (EditorInspectorUi.checkbox(label, current, null, "_node." + property)) {
            next[0] = !current;
        }
        if (next[0] != current) sendPlayerEdit(node, property, Boolean.toString(next[0]));
    }

    private static void playerBoolActions(SceneNode node, String label, String property) {
        EditorInspectorUi.fieldLabel(label, null, "_node." + property);
        if (ImGui.button(EditorI18n.t("Включить") + "##" + property)) {
            sendPlayerEdit(node, property, "true");
        }
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Выключить") + "##" + property)) {
            sendPlayerEdit(node, property, "false");
        }
    }

    private static void playerGamemode(SceneNode node, String current) {
        String shown = current == null || current.isBlank() ? "unknown" : current;
        EditorInspectorUi.fieldLabel(EditorI18n.t("Режим игры"), null, "_node.gamemode");
        ImGui.setNextItemWidth(-1f);
        if (ImGui.beginCombo("##player-gamemode", shown)) {
            for (String mode : new String[]{"survival", "creative", "adventure", "spectator"}) {
                if (ImGui.selectable(mode, mode.equals(current))) {
                    sendPlayerEdit(node, "gamemode", "\"" + mode + "\"");
                }
            }
            ImGui.endCombo();
        }
    }

    private static void sendPlayerEdit(SceneNode node, String property, String json) {
        if (node == null || node.playerUuid == null || node.playerUuid.isBlank()) return;
        org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.sendToServer(
                new org.zonarstudio.spraute_engine.network.PlayerNodeEditPacket(
                        node.playerUuid, property, json));
    }

    private void renderTags(SceneNode node) {
        if (!section("tags", EditorI18n.t("Теги"))) return;
        renderTagsBody(node);
        endSection();
    }

    private void renderTagsBody(SceneNode node) {
        SceneNodeTags.ensureTags(node);

        if (node.tags.isEmpty()) {
            EditorInspectorUi.hint(EditorI18n.t("Нет тегов — добавьте для поиска в скриптах"));
        } else {
            EditorInspectorUi.tagFlow(node.tags, index -> {
                applyEdit(() -> {
                    if (index >= 0 && index < node.tags.size()) {
                        node.tags.remove(index);
                    }
                });
            });
        }

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 84f);
        ImGui.inputTextWithHint("##new-tag", EditorI18n.t("новый тег…"), newTagBuffer);
        ImGui.sameLine();
        if (EditorInspectorUi.primaryButton(EditorI18n.t("Добавить"), 76f)) {
            String candidate = newTagBuffer.get().trim();
            if (!candidate.isEmpty()) {
                applyEdit(() -> {
                    if (SceneNodeTags.addTag(node, candidate)) {
                        newTagBuffer.set("");
                    }
                });
            }
        }
        EditorInspectorUi.undoOnActivate();

        tagsBuffer.set(String.join(", ", node.tags));
        if (EditorInspectorUi.inputText(EditorI18n.t("Список через запятую"), "tags-csv", tagsBuffer,
                "_node.tags = [\"tag1\", \"tag2\"]\n_node.addTag(\"tag\")\n_node.hasTag(\"tag\")")) {
            applyEdit(() -> node.tags = SceneNodeTags.parseTags(tagsBuffer.get()));
        }
        EditorInspectorUi.undoOnActivate();
    }

    private void renderSceneRoot(SceneNode node) {
        var doc = SceneEditorSession.document();
        if (!section("scene-root", EditorI18n.t("Сцена"))) return;
        EditorInspectorUi.fieldLabel(EditorI18n.t("Имя файла"));
        ImGui.textColored(0.88f, 0.90f, 0.94f, 1f, doc.id);
        EditorInspectorUi.hint(String.valueOf(SceneEditorSession.documentPath()));
        checkbox(EditorI18n.t("Автозапуск при старте мира/сервера"), doc.autoStart,
                value -> doc.autoStart = value);
        endSection();
    }

    private boolean isMulti() {
        return editTargets != null && editTargets.size() > 1;
    }

    private void renderTransform(SceneNode node) {
        SceneDocument doc = SceneEditorSession.document();
        SceneNode parent = doc.findParent(node.id);
        SceneTransform parentWorld = doc.parentWorldTransform(node);
        SceneTransform world = doc.worldTransform(node.id);
        if (world == null) world = node.transform.copy();
        boolean showLocal = EditorGizmo.isLocalSpace()
                && parent != null && parent.type != SceneNodeType.SCENE
                && !isMulti();
        SceneTransform shown = showLocal ? node.transform : world;

        boolean mixedPos = false, mixedRot = false, mixedScl = false;
        if (isMulti()) {
            for (SceneNode other : editTargets) {
                SceneTransform ow = doc.worldTransform(other.id);
                if (ow == null) ow = other.transform;
                if (ow.px != shown.px || ow.py != shown.py || ow.pz != shown.pz) mixedPos = true;
                if (ow.rx != shown.rx || ow.ry != shown.ry || ow.rz != shown.rz) mixedRot = true;
                if (ow.sx != shown.sx || ow.sy != shown.sy || ow.sz != shown.sz) mixedScl = true;
            }
        }

        position[0] = (float) shown.px;
        position[1] = (float) shown.py;
        position[2] = (float) shown.pz;
        rotation[0] = (float) shown.rx;
        rotation[1] = (float) shown.ry;
        rotation[2] = (float) shown.rz;
        scale[0] = (float) shown.sx;
        scale[1] = (float) shown.sy;
        scale[2] = (float) shown.sz;

        if (!section("transform", "Transform")) return;

        String spaceLabel = showLocal ? EditorI18n.t("локально") : EditorI18n.t("мир");
        boolean[] changed = {false};
        SceneTransform factoryT = factoryNode != null ? factoryNode.transform : new SceneTransform();
        EditorInspectorUi.bindReset(
                position[0] != (float) factoryT.px || position[1] != (float) factoryT.py || position[2] != (float) factoryT.pz,
                () -> {
                    position[0] = (float) factoryT.px;
                    position[1] = (float) factoryT.py;
                    position[2] = (float) factoryT.pz;
                    changed[0] = true;
                });
        EditorInspectorUi.bindClipboard(vec3Clip(position), v -> {
            if (applyVec3Clip(position, v)) changed[0] = true;
        });
        EditorInspectorUi.fieldLabel("Position (" + spaceLabel + ")" + (mixedPos ? " *" : ""),
                "_node.x = 10\n_node.y = 64\n_node.z = -5\n_node.transform.x = 10");
        if (EditorInspectorUi.vector3("pos", position, 0.05f, -1_000_000f, 1_000_000f)) changed[0] = true;
        EditorInspectorUi.scriptApi("_node.x / .y / .z\n_node.transform.x = 10");
        EditorInspectorUi.undoOnActivate();
        EditorInspectorUi.finishClipboardField();

        EditorInspectorUi.bindReset(
                rotation[0] != (float) factoryT.rx || rotation[1] != (float) factoryT.ry || rotation[2] != (float) factoryT.rz,
                () -> {
                    rotation[0] = (float) factoryT.rx;
                    rotation[1] = (float) factoryT.ry;
                    rotation[2] = (float) factoryT.rz;
                    changed[0] = true;
                });
        EditorInspectorUi.bindClipboard(vec3Clip(rotation), v -> {
            if (applyVec3Clip(rotation, v)) changed[0] = true;
        });
        EditorInspectorUi.fieldLabel("Rotation ° (" + spaceLabel + ")" + (mixedRot ? " *" : ""),
                "_node.yaw = 90\n_node.pitch = 0\n_node.rotationX = 0\n_node.rotationY = 90\n_node.rotationZ = 0\n_node.transform.yaw = 90");
        if (node.type == SceneNodeType.HITBOX
                || (isMulti() && editTargets.stream().allMatch(n -> n.type == SceneNodeType.HITBOX))) {
            if (EditorInspectorUi.axisDrag("rot-y", EditorInspectorUi.Axis.Y,
                    rotation, 1, 0.5f, -360f, 360f)) changed[0] = true;
            EditorInspectorUi.scriptApi("_node.yaw = 90\n_node.transform.yaw = 90");
            EditorInspectorUi.hint(EditorI18n.t("Для HITBOX используется только Y (yaw)"));
        } else if (EditorInspectorUi.vector3("rot", rotation, 0.5f, -360f, 360f)) {
            changed[0] = true;
        }
        EditorInspectorUi.scriptApi("_node.yaw / .pitch / .rotationX / .rotationY / .rotationZ\n_node.transform.yaw = 90");
        EditorInspectorUi.undoOnActivate();
        EditorInspectorUi.finishClipboardField();

        EditorInspectorUi.bindReset(
                scale[0] != (float) factoryT.sx || scale[1] != (float) factoryT.sy || scale[2] != (float) factoryT.sz,
                () -> {
                    scale[0] = (float) factoryT.sx;
                    scale[1] = (float) factoryT.sy;
                    scale[2] = (float) factoryT.sz;
                    changed[0] = true;
                });
        EditorInspectorUi.bindClipboard(vec3Clip(scale), v -> {
            if (applyVec3Clip(scale, v)) changed[0] = true;
        });
        EditorInspectorUi.fieldLabel("Scale (" + spaceLabel + ")" + (mixedScl ? " *" : ""),
                "_node.scale = 1.5\n_node.scaleX = 1\n_node.scaleY = 1\n_node.scaleZ = 1\n_node.transform.scale = 1.5");
        if (EditorInspectorUi.vector3("scl", scale, 0.02f, 0.01f, 100f)) changed[0] = true;
        EditorInspectorUi.scriptApi("_node.scale = 1.5\n_node.scaleX / .scaleY / .scaleZ");
        EditorInspectorUi.undoOnActivate();
        EditorInspectorUi.finishClipboardField();

        if (isMulti()) {
            EditorInspectorUi.hint(EditorI18n.t("* — значения различаются · правка ставит одно у всех"));
        } else if (showLocal) {
            EditorInspectorUi.hint(EditorI18n.t("Относительно родителя: ") + parent.name
                    + EditorI18n.t(" · движок использует вычисленный мировой transform"));
        } else {
            EditorInspectorUi.hint(EditorI18n.t("Совпадает с gizmo и хитбоксом в мире"));
        }
        endSection();

        if (changed[0]) {
            SceneEditorSession.pushUndo();
            SceneTransform edited = shown.copy();
            edited.px = position[0];
            edited.py = position[1];
            edited.pz = position[2];
            edited.rx = rotation[0];
            edited.ry = rotation[1];
            edited.rz = rotation[2];
            edited.sx = scale[0];
            edited.sy = scale[1];
            edited.sz = scale[2];
            if (isMulti()) {
                // Same absolute (world) transform for every selected node.
                for (SceneNode n : editTargets) {
                    SceneTransform pw = doc.parentWorldTransform(n);
                    n.transform = SceneTransform.toLocal(pw, edited.copy());
                }
            } else {
                node.transform = showLocal ? edited : SceneTransform.toLocal(parentWorld, edited);
            }
            SceneEditorSession.markDirty();
        }
        EditorInspectorUi.softSeparator();
    }

    private void renderNpc(SceneNode node) {
        if (!isMulti() && section("npc-object", EditorI18n.t("Объект"))) {
            nameBuffer.set(node.name);
            if (EditorInspectorUi.inputText(EditorI18n.t("Имя"), "npc-name", nameBuffer, "_node.name = \"Guard\"")) {
                node.name = nameBuffer.get();
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.undoOnActivate();
            endSection();
        }

        if (section("npc-assets", EditorI18n.t("Ассеты"))) {
            boolean assetsChanged = false;
            assetsChanged |= assetCombo(EditorI18n.t("Модель"), node.model, EditorAssetIndex.models(), modelSearch,
                    value -> node.model = value, "_node.model = \"geo/foo.geo.json\"");
            assetsChanged |= assetCombo(EditorI18n.t("Текстура"), node.texture, EditorAssetIndex.textures(), textureSearch,
                    value -> node.texture = value, "_node.texture = \"textures/entity/foo.png\"");
            assetsChanged |= assetCombo(EditorI18n.t("Анимации"), node.animation, EditorAssetIndex.animations(), animationSearch,
                    value -> {
                        node.animation = value;
                        syncAnimatorFile(node, value);
                    }, "_node.animation = \"animations/foo.animation.json\"");
            if (assetsChanged) {
                animatorClipsFile = null;
                ScenePreviewManager.invalidateInstance(node.id);
                EditorModelPreviewRenderer.invalidate();
                EditorAnimClipPreview.invalidate();
                SceneEditorSession.syncLiveNode(node.id);
            }
            if (ImGui.smallButton(EditorI18n.t("Обновить список ассетов"))) EditorAssetIndex.refresh();
            endSection();
        }

        if (section("npc-main", EditorI18n.t("Основные параметры"))) {
            rst(factoryNode.nameVisible).checkbox(EditorI18n.t("Показывать имя"), node.nameVisible, value -> node.nameVisible = value,
                    "_node.showName = true");
            rst(factoryNode.maxHealth).dragFloat(EditorI18n.t("Максимальное здоровье"), node.maxHealth, 0.5f, 1f, 100000f, value -> node.maxHealth = value,
                    "_node.maxHp = 20\n_node.hp = 20");
            rst(factoryNode.movementSpeed).dragFloat(EditorI18n.t("Скорость движения"), node.movementSpeed, 0.01f, 0f, 100f, value -> node.movementSpeed = value,
                    "_node.speed = 0.3");
            rst(factoryNode.flyingSpeed).dragFloat(EditorI18n.t("Скорость полёта"), node.flyingSpeed, 0.01f, 0f, 100f, value -> node.flyingSpeed = value,
                    "_node.flyingSpeed = 0.4");
            rst(factoryNode.flySmoothing).dragFloat(EditorI18n.t("Сглаживание полёта"), node.flySmoothing, 0.01f, 0.02f, 1f, value -> node.flySmoothing = value,
                    "_node.flySmoothing = 0.35");
            rst(factoryNode.walkTurnSpeed).dragFloat(EditorI18n.t("Скорость поворота (ходьба)"), node.walkTurnSpeed, 0.1f, 0.1f, 180f,
                    value -> node.walkTurnSpeed = value,
                    "_node.walkTurnSpeed = 6");
            rst(factoryNode.idleTurnSpeed).dragFloat(EditorI18n.t("Скорость поворота (стойка)"), node.idleTurnSpeed, 0.1f, 0.1f, 180f,
                    value -> node.idleTurnSpeed = value,
                    "_node.idleTurnSpeed = 6");
            rst(factoryNode.group).stringField(EditorI18n.t("Группа"), node.group, groupBuffer, value -> node.group = value,
                    "_node.group = \"guards\"");
            rst(factoryNode.playerSkinOverlay).stringField(EditorI18n.t("UUID скина игрока"), node.playerSkinOverlay, skinBuffer, value -> node.playerSkinOverlay = value,
                    "_node.playerSkinOverlay = \"uuid\"");
            endSection();
        }

        if (section("npc-path", EditorI18n.t("Поиск пути (земля)"))) {
            rst(factoryNode.pathArriveRadius).dragFloat(EditorI18n.t("Точность прибытия"), node.pathArriveRadius, 0.01f, 0.02f, 4f,
                    value -> node.pathArriveRadius = value,
                    "_node.pathArriveRadius = 0.1");
            rst(factoryNode.pathArriveVert).dragFloat(EditorI18n.t("Допуск по высоте"), node.pathArriveVert, 0.05f, 0.25f, 16f,
                    value -> node.pathArriveVert = value,
                    "_node.pathArriveVert = 1.5");
            rst(factoryNode.pathStepHeight).dragFloat(EditorI18n.t("Высота шага"), node.pathStepHeight, 0.01f, 0.1f, 2f,
                    value -> node.pathStepHeight = value,
                    "_node.pathStepHeight = 0.6");
            rst(factoryNode.pathMaxJump).dragInt(EditorI18n.t("Макс. прыжок"), node.pathMaxJump, 1, 0, 4,
                    value -> node.pathMaxJump = value,
                    "_node.pathMaxJump = 1");
            rst(factoryNode.pathMaxDrop).dragInt(EditorI18n.t("Макс. спуск"), node.pathMaxDrop, 1, 0, 16,
                    value -> node.pathMaxDrop = value,
                    "_node.pathMaxDrop = 3");
            rst(factoryNode.pathMaxRange).dragFloat(EditorI18n.t("Дальность поиска"), node.pathMaxRange, 1f, 4f, 256f,
                    value -> node.pathMaxRange = value,
                    "_node.pathMaxRange = 48");
            rst(factoryNode.pathMaxNodes).dragInt(EditorI18n.t("Лимит узлов A*"), node.pathMaxNodes, 256, 256, 65536,
                    value -> node.pathMaxNodes = value,
                    "_node.pathMaxNodes = 8192");
            rst(factoryNode.pathDiagonal).checkbox(EditorI18n.t("Диагонали"), node.pathDiagonal, value -> node.pathDiagonal = value,
                    "_node.pathDiagonal = true");
            rst(factoryNode.pathAvoidWater).checkbox(EditorI18n.t("Обходить воду"), node.pathAvoidWater, value -> node.pathAvoidWater = value,
                    "_node.pathAvoidWater = true");
            rst(factoryNode.pathAvoidLava).checkbox(EditorI18n.t("Обходить лаву"), node.pathAvoidLava, value -> node.pathAvoidLava = value,
                    "_node.pathAvoidLava = true");
            rst(factoryNode.pathSnapOnArrive).checkbox(EditorI18n.t("Snap к точке по прибытии"), node.pathSnapOnArrive, value -> node.pathSnapOnArrive = value,
                    "_node.pathSnapOnArrive = true");
            endSection();
        }

        if (section("npc-fly-path", EditorI18n.t("Поиск пути (воздух)"))) {
            rst(factoryNode.flyArriveRadius).dragFloat(EditorI18n.t("Точность прибытия"), node.flyArriveRadius, 0.01f, 0.02f, 4f,
                    value -> node.flyArriveRadius = value,
                    "_node.flyArriveRadius = 0.1");
            rst(factoryNode.flyMaxRange).dragFloat(EditorI18n.t("Дальность поиска"), node.flyMaxRange, 1f, 4f, 256f,
                    value -> node.flyMaxRange = value,
                    "_node.flyMaxRange = 48");
            rst(factoryNode.flyMaxNodes).dragInt(EditorI18n.t("Лимит узлов A*"), node.flyMaxNodes, 256, 256, 65536,
                    value -> node.flyMaxNodes = value,
                    "_node.flyMaxNodes = 8192");
            rst(factoryNode.flyDiagonal).checkbox(EditorI18n.t("Диагонали"), node.flyDiagonal, value -> node.flyDiagonal = value,
                    "_node.flyDiagonal = true");
            rst(factoryNode.flyAvoidWater).checkbox(EditorI18n.t("Обходить воду"), node.flyAvoidWater, value -> node.flyAvoidWater = value,
                    "_node.flyAvoidWater = false");
            rst(factoryNode.flyAvoidLava).checkbox(EditorI18n.t("Обходить лаву"), node.flyAvoidLava, value -> node.flyAvoidLava = value,
                    "_node.flyAvoidLava = true");
            rst(factoryNode.flySnapOnArrive).checkbox(EditorI18n.t("Snap к точке по прибытии"), node.flySnapOnArrive, value -> node.flySnapOnArrive = value,
                    "_node.flySnapOnArrive = true");
            endSection();
        }

        if (section("npc-state", EditorI18n.t("Состояние и поведение"))) {
            rst(factoryNode.invulnerable).checkbox(EditorI18n.t("Неуязвимый"), node.invulnerable, value -> node.invulnerable = value,
                    "_node.invulnerable = true");
            rst(factoryNode.silent).checkbox(EditorI18n.t("Без звуков"), node.silent, value -> node.silent = value,
                    "_node.silent = true");
            rst(factoryNode.glowing).checkbox(EditorI18n.t("Свечение"), node.glowing, value -> node.glowing = value,
                    "_node.glowing = true");
            rst(factoryNode.translucent).checkbox(EditorI18n.t("Прозрачная текстура"), node.translucent, value -> node.translucent = value,
                    EditorI18n.t("true — альфа PNG смешивается; false — вырезание (cutout, стабильнее с шейдерами)"),
                    "_node.translucent = true");
            rst(factoryNode.opacity).dragFloat(EditorI18n.t("Прозрачность модели (0–1)"), node.opacity, 0.01f, 0f, 1f, value -> node.opacity = value,
                    EditorI18n.t("0 — невидимо, 1 — непрозрачно. Ниже 1 включает blend."),
                    "_node.opacity = 0.5");
            rst(factoryNode.lodEnabled).checkbox(EditorI18n.t("Автоматический LOD"), node.lodEnabled, value -> node.lodEnabled = value,
                    EditorI18n.t("Уменьшает детализацию и частоту анимации только когда модель мала на экране."),
                    "_node.lod = true");
            rst(factoryNode.renderDistance).dragFloat(EditorI18n.t("Дальность рендера (0 = общая)"), node.renderDistance,
                    1f, 0f, 1024f, value -> node.renderDistance = value, "_node.renderDistance = 64");
            rst(factoryNode.forceVisible).checkbox(EditorI18n.t("Приоритет катсцены"), node.forceVisible, value -> node.forceVisible = value,
                    EditorI18n.t("Отключает LOD-скрытие и лимит дальности, но сохраняет frustum culling."),
                    "_node.forceVisible = true");
            rst(factoryNode.loadChunks).checkbox(EditorI18n.t("Прогружать чанки"), node.loadChunks, value -> node.loadChunks = value,
                    EditorI18n.t("Держит чанки вокруг NPC загруженными, чтобы он продолжал ходить без игрока рядом."),
                    "_node.loadChunks = true");
            rst(factoryNode.noGravity).checkbox(EditorI18n.t("Без гравитации"), node.noGravity, value -> node.noGravity = value,
                    "_node.noGravity = true");
            rst(factoryNode.collision).checkbox(EditorI18n.t("Столкновения"), node.collision, value -> node.collision = value,
                    "_node.collision = true");
            rst(factoryNode.flying).checkbox(EditorI18n.t("Летающий NPC"), node.flying, value -> node.flying = value,
                    "_node.flying = true");
            rst(factoryNode.swimming).checkbox(EditorI18n.t("Плавающий NPC"), node.swimming, value -> node.swimming = value,
                    "_node.swimming = true");
            endSection();
        }

        if (section("npc-look", EditorI18n.t("Взгляд и подпись"))) {
            rst(factoryNode.headBone).stringField(EditorI18n.t("Кость головы"), node.headBone, headBoneBuffer, value -> node.headBone = value,
                    "_node.headBone = \"Head\"");
            rst(factoryNode.unlimitedHeadLook).checkbox(EditorI18n.t("Взгляд без ограничений"), node.unlimitedHeadLook, value -> node.unlimitedHeadLook = value,
                    "_node.unlimitedHeadLook = true");
            EditorInspectorUi.fieldLabel(EditorI18n.t("Смещение имени по Y"), "_node.nameYOffset = 0.2");
            single[0] = node.nameYOffset;
            if (EditorInspectorUi.axisDrag("name-y", EditorInspectorUi.Axis.Y, single, 0, 0.02f, -100f, 100f)) {
                node.nameYOffset = single[0];
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.scriptApi("_node.nameYOffset = 0.2");
            EditorInspectorUi.undoOnActivate();
            endSection();
        }

        if (section("npc-anims", EditorI18n.t("Анимации состояний"))) {
            List<String> clips = animatorClips(node.animation);
            animationClipCombo(EditorI18n.t("Ожидание"), node.idleAnimation, clips, value -> node.idleAnimation = value,
                    "_node.idleAnim = \"idle\"", node);
            animationClipCombo(EditorI18n.t("Ходьба"), node.walkAnimation, clips, value -> node.walkAnimation = value,
                    "_node.walkAnim = \"walk\"", node);
            animationClipCombo(EditorI18n.t("Ожидание в полёте"), node.flyIdleAnimation, clips, value -> node.flyIdleAnimation = value,
                    "_node.flyIdleAnim = \"fly_idle\"", node);
            animationClipCombo(EditorI18n.t("Полёт"), node.flyAnimation, clips, value -> node.flyAnimation = value,
                    "_node.flyWalkAnim = \"fly\"", node);
            animationClipCombo(EditorI18n.t("Ожидание в воде"), node.swimIdleAnimation, clips, value -> node.swimIdleAnimation = value,
                    "_node.swimIdleAnim = \"swim_idle\"", node);
            animationClipCombo(EditorI18n.t("Плавание"), node.swimAnimation, clips, value -> node.swimAnimation = value,
                    "_node.swimWalkAnim = \"swim\"", node);
            animationClipCombo(EditorI18n.t("Смерть"), node.deathAnimation, clips, value -> node.deathAnimation = value,
                    "_node.deathAnim = \"death\"", node);
            endSection();
        }

        if (section("npc-combat", EditorI18n.t("Бой"))) {
            rst(factoryNode.attackRange).dragFloat(EditorI18n.t("Дальность атаки"), node.attackRange, 0.05f, 0f, 100f, value -> node.attackRange = value,
                    "_node.attackRange = 2.5");
            rst(factoryNode.attackCooldown).dragInt(EditorI18n.t("Перезарядка атаки (тики)"), node.attackCooldown, 1, 0, 72000,
                    value -> node.attackCooldown = value, "_node.attackCooldown = 20");
            rst(factoryNode.attackHitDelay).dragInt(EditorI18n.t("Задержка удара (тики)"), node.attackHitDelay, 1, 0, 72000,
                    value -> node.attackHitDelay = value, "_node.attackHitDelay = 8");
            rst(factoryNode.attackHand).stringField(EditorI18n.t("Рука атаки"), node.attackHand, attackHandBuffer, value -> node.attackHand = value,
                    "_node.attackHand = \"right\"");
            animationMultiClipCombo(EditorI18n.t("Анимации атаки"), node.attackAnimations, animatorClips(node.animation),
                    value -> node.attackAnimations = value, "_node.attackAnimations = \"attack,attack2\"", node);
            endSection();
        }

        if (section("npc-drops", EditorI18n.t("Дроп при смерти"))) {
            if (node.drops == null) node.drops = new ArrayList<>();
            dropRulesEditor.renderPanel(node.drops, false, node.dropXp, v -> node.dropXp = v);
            endSection();
        }

        if (section("npc-hitbox", EditorI18n.t("Физика и хитбокс"))) {
            EditorInspectorUi.fieldLabel(EditorI18n.t("Размер"),
                    "_node.w = 0.6\n_node.h = 1.8\n_node.setHitbox(0.6, 1.8)");
            hitboxSize[0] = node.hitboxWidth;
            hitboxSize[1] = node.hitboxHeight;
            if (EditorInspectorUi.vector2("hb-size", EditorInspectorUi.Axis.W, EditorInspectorUi.Axis.H,
                    hitboxSize, 0.02f, 0.05f, 100f)) {
                node.hitboxWidth = hitboxSize[0];
                node.hitboxHeight = hitboxSize[1];
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.scriptApi("_node.w = 0.6\n_node.h = 1.8");
            EditorInspectorUi.undoOnActivate();

            EditorInspectorUi.fieldLabel(EditorI18n.t("Смещение"),
                    "_node.offsetX = 0\n_node.offsetY = 0\n_node.offsetZ = 0\n_node.setHitboxOffset(0, 0, 0)");
            hitboxOffset[0] = node.hitboxOffsetX;
            hitboxOffset[1] = node.hitboxOffsetY;
            hitboxOffset[2] = node.hitboxOffsetZ;
            if (EditorInspectorUi.vector3("hb-off", hitboxOffset, 0.02f, -100f, 100f)) {
                node.hitboxOffsetX = hitboxOffset[0];
                node.hitboxOffsetY = hitboxOffset[1];
                node.hitboxOffsetZ = hitboxOffset[2];
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.scriptApi("_node.offsetX / .offsetY / .offsetZ");
            EditorInspectorUi.undoOnActivate();
            rst(factoryNode.showHitbox).checkbox(EditorI18n.t("Показывать хитбокс"), node.showHitbox, value -> node.showHitbox = value,
                    "_node.showHitbox = true");
            endSection();
        }

        if (section("npc-preview", EditorI18n.t("Предпросмотр"))) {
            rst(factoryNode.previewEnabled).checkbox(EditorI18n.t("Предпросмотр в мире"), node.previewEnabled,
                    value -> node.previewEnabled = value);
            EditorInspectorUi.hint(EditorI18n.t("Виден только в редакторе."));
            renderModelPreview(node);
            endSection();
        }
    }

    private void renderModel(SceneNode node) {
        if (!isMulti() && section("model-object", EditorI18n.t("Объект"))) {
            nameBuffer.set(node.name);
            if (EditorInspectorUi.inputText(EditorI18n.t("Имя"), "model-name", nameBuffer, "_node.name = \"Prop\"")) {
                node.name = nameBuffer.get();
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.undoOnActivate();
            endSection();
        }

        if (section("model-assets", EditorI18n.t("Ассеты"))) {
            boolean assetsChanged = false;
            assetsChanged |= assetCombo(EditorI18n.t("Модель"), node.model, EditorAssetIndex.models(), modelSearch,
                    value -> node.model = value, "_node.model = \"geo/foo.geo.json\"");
            assetsChanged |= assetCombo(EditorI18n.t("Текстура"), node.texture, EditorAssetIndex.textures(), textureSearch,
                    value -> node.texture = value, "_node.texture = \"textures/entity/foo.png\"");
            assetsChanged |= assetCombo(EditorI18n.t("Анимации (необяз.)"), node.animation, EditorAssetIndex.animations(), animationSearch,
                    value -> {
                        node.animation = value;
                        syncAnimatorFile(node, value);
                    }, "_node.animation = \"animations/foo.animation.json\"");
            if (assetsChanged) {
                animatorClipsFile = null;
                ScenePreviewManager.invalidateInstance(node.id);
                EditorModelPreviewRenderer.invalidate();
                EditorAnimClipPreview.invalidate();
                SceneEditorSession.syncLiveNode(node.id);
            }
            if (ImGui.smallButton(EditorI18n.t("Обновить список ассетов"))) EditorAssetIndex.refresh();
            endSection();
        }

        if (section("model-params", EditorI18n.t("Параметры"))) {
            rst(factoryNode.nameVisible).checkbox(EditorI18n.t("Показывать имя"), node.nameVisible, value -> node.nameVisible = value,
                    "_node.showName = true");
            animationClipCombo(EditorI18n.t("Idle-анимация"), node.idleAnimation, animatorClips(node.animation),
                    value -> node.idleAnimation = value, "_node.idleAnim = \"idle\"", node);
            rst(factoryNode.invulnerable).checkbox(EditorI18n.t("Неуязвимый"), node.invulnerable, value -> node.invulnerable = value,
                    "_node.invulnerable = true");
            rst(factoryNode.silent).checkbox(EditorI18n.t("Без звуков"), node.silent, value -> node.silent = value,
                    "_node.silent = true");
            rst(factoryNode.glowing).checkbox(EditorI18n.t("Свечение"), node.glowing, value -> node.glowing = value,
                    "_node.glowing = true");
            rst(factoryNode.translucent).checkbox(EditorI18n.t("Прозрачная текстура"), node.translucent, value -> node.translucent = value,
                    EditorI18n.t("true — альфа PNG смешивается; false — вырезание (cutout, стабильнее с шейдерами)"),
                    "_node.translucent = true");
            rst(factoryNode.opacity).dragFloat(EditorI18n.t("Прозрачность модели (0–1)"), node.opacity, 0.01f, 0f, 1f, value -> node.opacity = value,
                    EditorI18n.t("0 — невидимо, 1 — непрозрачно. Ниже 1 включает blend."),
                    "_node.opacity = 0.5");
            rst(factoryNode.lodEnabled).checkbox(EditorI18n.t("Автоматический LOD"), node.lodEnabled, value -> node.lodEnabled = value,
                    EditorI18n.t("Уменьшает детализацию и частоту анимации только когда модель мала на экране."),
                    "_node.lod = true");
            rst(factoryNode.renderDistance).dragFloat(EditorI18n.t("Дальность рендера (0 = общая)"), node.renderDistance,
                    1f, 0f, 1024f, value -> node.renderDistance = value, "_node.renderDistance = 64");
            rst(factoryNode.forceVisible).checkbox(EditorI18n.t("Приоритет катсцены"), node.forceVisible, value -> node.forceVisible = value,
                    EditorI18n.t("Отключает LOD-скрытие и лимит дальности, но сохраняет frustum culling."),
                    "_node.forceVisible = true");
            rst(factoryNode.noGravity).checkbox(EditorI18n.t("Без гравитации"), node.noGravity, value -> node.noGravity = value,
                    "_node.noGravity = true");
            endSection();
        }

        if (section("model-preview", EditorI18n.t("Предпросмотр"))) {
            rst(factoryNode.previewEnabled).checkbox(EditorI18n.t("Предпросмотр в мире"), node.previewEnabled,
                    value -> node.previewEnabled = value);
            renderModelPreview(node);
            endSection();
        }
    }

    private void renderBillboard(SceneNode node) {
        if (!isMulti() && section("billboard-object", EditorI18n.t("Объект"))) {
            nameBuffer.set(node.name);
            if (EditorInspectorUi.inputText(EditorI18n.t("Имя"), "billboard-name", nameBuffer)) {
                node.name = nameBuffer.get();
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.undoOnActivate();
            endSection();
        }

        if (section("billboard-assets", EditorI18n.t("Текстура"))) {
            rst(factoryNode.texture).assetCombo(EditorI18n.t("Текстура"), node.texture, EditorAssetIndex.textures(), textureSearch,
                    value -> node.texture = value, "_node.texture = \"textures/gui/sign.png\"");
            if (ImGui.smallButton(EditorI18n.t("Обновить список ассетов"))) EditorAssetIndex.refresh();
            endSection();
        }

        if (section("billboard-params", EditorI18n.t("Параметры"))) {
            EditorInspectorUi.fieldLabel(EditorI18n.t("Размер (ширина × высота)"),
                    "_node.w = 1\n_node.h = 1");
            hitboxSize[0] = node.billboardWidth;
            hitboxSize[1] = node.billboardHeight;
            if (EditorInspectorUi.vector2("bb-size", EditorInspectorUi.Axis.W, EditorInspectorUi.Axis.H,
                    hitboxSize, 0.02f, 0.01f, 100f)) {
                node.billboardWidth = hitboxSize[0];
                node.billboardHeight = hitboxSize[1];
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.scriptApi("_node.w = 1\n_node.h = 1");
            EditorInspectorUi.undoOnActivate();
            rst(factoryNode.billboardSeeThrough).checkbox(EditorI18n.t("Виден сквозь блоки"), node.billboardSeeThrough, value -> node.billboardSeeThrough = value,
                    "_node.seeThrough = true");
            EditorInspectorUi.hint(EditorI18n.t("Всегда повёрнут к камере · масштаб ноды умножает размер"));
            endSection();
        }

        if (section("billboard-preview", EditorI18n.t("Предпросмотр"))) {
            rst(factoryNode.previewEnabled).checkbox(EditorI18n.t("Предпросмотр в мире"), node.previewEnabled,
                    value -> node.previewEnabled = value);
            EditorInspectorUi.hint(EditorI18n.t("Виден только в редакторе."));
            endSection();
        }
    }

    private void renderHitbox(SceneNode node) {
        if (!isMulti() && section("hitbox-object", EditorI18n.t("Объект"))) {
            nameBuffer.set(node.name);
            if (EditorInspectorUi.inputText(EditorI18n.t("Имя"), "hitbox-name", nameBuffer, "_node.name = \"zone\"")) {
                node.name = nameBuffer.get();
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.undoOnActivate();
            endSection();
        }

        if (section("hitbox-collision", EditorI18n.t("Коллизия"))) {
            rst(factoryNode.hitboxSolid).checkbox(EditorI18n.t("Твёрдый (solid)"), node.hitboxSolid, value -> node.hitboxSolid = value,
                    "_node.solid = true");
            rst(factoryNode.hitboxTrigger).checkbox(EditorI18n.t("Триггер (касание)"), node.hitboxTrigger, value -> node.hitboxTrigger = value,
                    "_node.trigger = true");
            if (node.hitboxTrigger) {
                rst(factoryNode.hitboxTriggerId).stringField(EditorI18n.t("Id триггера"), node.hitboxTriggerId, hitboxTriggerIdBuffer,
                        value -> node.hitboxTriggerId = value == null ? "" : value.trim(),
                        EditorI18n.t("Имя для on/await hitboxTouch. Пусто — используется id ноды (hitbox1). В блоке события выбирай этот хитбокс из списка, как НИП."),
                        "_node.triggerId = \"zone_gate\"\n"
                                + "on hitboxTouch(any, \"*\", \"hitbox1\") -> h { … }\n"
                                + "await hitboxTouch(getFirstPlayer(), \"hitbox1\")");
                EditorInspectorUi.hint(EditorI18n.t("Касание: on hitboxTouch / await hitboxTouch · выход: hitboxExit. Галочка «Триггер» обязательна."));
            } else {
                EditorInspectorUi.hint(EditorI18n.t("Solid блокирует игроков · Trigger — on/await hitboxTouch и hitboxExit"));
            }
            endSection();
        }

        if (section("hitbox-volume", EditorI18n.t("Объём"))) {
            EditorInspectorUi.fieldLabel(EditorI18n.t("Размер"), "_node.w = 1\n_node.h = 2\n_node.depth = 1");
            hitboxSize[0] = node.hitboxWidth;
            hitboxSize[1] = node.hitboxHeight;
            if (EditorInspectorUi.vector2("hb-wh", EditorInspectorUi.Axis.W, EditorInspectorUi.Axis.H,
                    hitboxSize, 0.02f, 0.05f, 100f)) {
                node.hitboxWidth = hitboxSize[0];
                node.hitboxHeight = hitboxSize[1];
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.scriptApi("_node.w / .h");
            EditorInspectorUi.undoOnActivate();

            single[0] = node.hitboxDepth;
            EditorInspectorUi.fieldLabel("Depth", "_node.depth = 1");
            if (EditorInspectorUi.axisDrag("hb-depth", EditorInspectorUi.Axis.Z, single, 0, 0.02f, 0.05f, 100f)) {
                node.hitboxDepth = single[0];
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.scriptApi("_node.depth = 1");
            EditorInspectorUi.undoOnActivate();

            EditorInspectorUi.fieldLabel(EditorI18n.t("Смещение (от точки transform)"),
                    "_node.offsetX = 0\n_node.offsetY = 0\n_node.offsetZ = 0");
            hitboxOffset[0] = node.hitboxOffsetX;
            hitboxOffset[1] = node.hitboxOffsetY;
            hitboxOffset[2] = node.hitboxOffsetZ;
            if (EditorInspectorUi.vector3("hb-off", hitboxOffset, 0.02f, -100f, 100f)) {
                node.hitboxOffsetX = hitboxOffset[0];
                node.hitboxOffsetY = hitboxOffset[1];
                node.hitboxOffsetZ = hitboxOffset[2];
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.scriptApi("_node.offsetX / .offsetY / .offsetZ");
            EditorInspectorUi.undoOnActivate();
            rst(factoryNode.showHitbox).checkbox(EditorI18n.t("Показывать хитбокс"), node.showHitbox, value -> node.showHitbox = value,
                    "_node.showHitbox = true");
            endSection();
        }
    }

    private void renderParticle(SceneNode node) {
        if (!isMulti() && section("particle-object", EditorI18n.t("Объект"))) {
            nameBuffer.set(node.name);
            if (EditorInspectorUi.inputText(EditorI18n.t("Имя"), "particle-name", nameBuffer)) {
                node.name = nameBuffer.get();
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.undoOnActivate();
            endSection();
        }

        if (node.particles(false) != null && node.particles(false).custom) {
            renderCustomParticle(node);
            return;
        }

        if (section("particle-type", EditorI18n.t("Частица | OLD"))) {
            if (ImGui.button(EditorI18n.t("Перевести в свой Particle System"))) {
                applyEdit(() -> {
                    var spec = node.particles(true);
                    spec.custom = true;
                    spec.renderer.texture = "minecraft:textures/particle/generic_0.png";
                    particleChanged(node);
                });
            }
            EditorInspectorUi.hint(EditorI18n.t(
                    "Legacy Minecraft-частица. Перевод создаст inline-систему без регистрации и перезахода."));
            rst(factoryNode.particleType).assetCombo(EditorI18n.t("Тип частицы | OLD"), node.particleType, EditorAssetIndex.particles(), particleSearch,
                    v -> node.particleType = v);
            drawParticlePreview(node.particleType);
            if (ImGui.smallButton(EditorI18n.t("Обновить список частиц"))) EditorAssetIndex.refreshParticles();
            EditorInspectorUi.hint(EditorI18n.t("Все зарегистрированные простые частицы (vanilla + моды + свои)"));
            endSection();
        }

        if (section("particle-shape", EditorI18n.t("Форма"))) {
            EditorInspectorUi.fieldLabel(EditorI18n.t("Форма эмиттера"));
            String shape = org.zonarstudio.spraute_engine.scene.SceneParticleEmitter.normalizeShape(node.particleShape);
            String shown = switch (shape) {
                case "line" -> EditorI18n.t("Линия");
                case "sphere" -> EditorI18n.t("Шар");
                case "spiral" -> EditorI18n.t("Спираль");
                default -> EditorI18n.t("Обычная (точка)");
            };
            if (ImGui.beginCombo("##particle-shape", shown)) {
                if (ImGui.selectable(EditorI18n.t("Обычная (точка)"), "point".equals(shape))) {
                    applyEdit(() -> node.particleShape = "point");
                }
                if (ImGui.selectable(EditorI18n.t("Линия"), "line".equals(shape))) {
                    applyEdit(() -> node.particleShape = "line");
                }
                if (ImGui.selectable(EditorI18n.t("Шар"), "sphere".equals(shape))) {
                    applyEdit(() -> node.particleShape = "sphere");
                }
                if (ImGui.selectable(EditorI18n.t("Спираль"), "spiral".equals(shape))) {
                    applyEdit(() -> node.particleShape = "spiral");
                }
                ImGui.endCombo();
            }

            rst(factoryNode.particleCount).dragInt(EditorI18n.t("Количество"), node.particleCount, 1, 1, 256, value -> node.particleCount = value);

            if ("line".equals(shape)) {
                EditorInspectorUi.fieldLabel(EditorI18n.t("Конец линии (локально)"));
                particleLine[0] = node.particleLineX;
                particleLine[1] = node.particleLineY;
                particleLine[2] = node.particleLineZ;
                if (EditorInspectorUi.vector3("pt-line", particleLine, 0.05f, -100f, 100f)) {
                    applyEdit(() -> {
                        node.particleLineX = particleLine[0];
                        node.particleLineY = particleLine[1];
                        node.particleLineZ = particleLine[2];
                    });
                }
                EditorInspectorUi.undoOnActivate();
            }
            if ("sphere".equals(shape) || "spiral".equals(shape)) {
                single[0] = node.particleRadius;
                if (EditorInspectorUi.dragFloat(EditorI18n.t("Радиус"), "pt-radius", single, 0.02f, 0.05f, 50f)) {
                    applyEdit(() -> node.particleRadius = single[0]);
                }
                EditorInspectorUi.undoOnActivate();
            }
            if ("spiral".equals(shape)) {
                single[0] = node.particleHeight;
                if (EditorInspectorUi.dragFloat(EditorI18n.t("Высота спирали"), "pt-height", single, 0.05f, 0.1f, 100f)) {
                    applyEdit(() -> node.particleHeight = single[0]);
                }
                EditorInspectorUi.undoOnActivate();
            }
            endSection();
        }

        if (section("particle-motion", EditorI18n.t("Движение"))) {
            EditorInspectorUi.fieldLabel(EditorI18n.t("Режим вылета"));
            String mode = org.zonarstudio.spraute_engine.scene.SceneParticleEmitter.normalizeEmitMode(node.particleEmitMode);
            String modeShown = "directional".equals(mode) ? EditorI18n.t("В одну сторону (конус)") : EditorI18n.t("Взрыв (во все стороны)");
            if (ImGui.beginCombo("##particle-emit-mode", modeShown)) {
                if (ImGui.selectable(EditorI18n.t("Взрыв (во все стороны)"), "burst".equals(mode))) {
                    applyEdit(() -> node.particleEmitMode = "burst");
                }
                if (ImGui.selectable(EditorI18n.t("В одну сторону (конус)"), "directional".equals(mode))) {
                    applyEdit(() -> node.particleEmitMode = "directional");
                }
                ImGui.endCombo();
            }
            EditorInspectorUi.hint(EditorI18n.t("Форма = где появляются · Режим = куда летят"));

            if ("directional".equals(mode)) {
                EditorInspectorUi.fieldLabel(EditorI18n.t("Направление (локально)"));
                particleDir[0] = node.particleDirX;
                particleDir[1] = node.particleDirY;
                particleDir[2] = node.particleDirZ;
                if (EditorInspectorUi.vector3("pt-dir", particleDir, 0.05f, -10f, 10f)) {
                    applyEdit(() -> {
                        node.particleDirX = particleDir[0];
                        node.particleDirY = particleDir[1];
                        node.particleDirZ = particleDir[2];
                    });
                }
                EditorInspectorUi.undoOnActivate();
                EditorInspectorUi.hint(EditorI18n.t("Крутится с нодой · (0,1,0)=вверх · (0,0,1)=вперёд по Z"));

                single[0] = node.particleCone;
                if (EditorInspectorUi.dragFloat(EditorI18n.t("Конус / разброс °"), "pt-cone", single, 0.5f, 0f, 180f)) {
                    applyEdit(() -> node.particleCone = single[0]);
                }
                EditorInspectorUi.undoOnActivate();
                EditorInspectorUi.hint(EditorI18n.t("0° = точный луч · 15–40° = веер · 90°+ = широкий конус"));
            } else {
                single[0] = node.particleCone;
                if (EditorInspectorUi.dragFloat(EditorI18n.t("Джиттер углов °"), "pt-burst-jitter", single, 0.5f, 0f, 45f)) {
                    applyEdit(() -> node.particleCone = single[0]);
                }
                EditorInspectorUi.undoOnActivate();
                EditorInspectorUi.hint(EditorI18n.t("Шар + взрыв: разлёт от центра"));
            }

            EditorInspectorUi.fieldLabel(EditorI18n.t("Объём спавна (позиция)"));
            particleSpread[0] = node.particleSpreadX;
            particleSpread[1] = node.particleSpreadY;
            particleSpread[2] = node.particleSpreadZ;
            if (EditorInspectorUi.vector3("pt-spread", particleSpread, 0.01f, 0f, 5f)) {
                applyEdit(() -> {
                    node.particleSpreadX = particleSpread[0];
                    node.particleSpreadY = particleSpread[1];
                    node.particleSpreadZ = particleSpread[2];
                });
            }
            EditorInspectorUi.undoOnActivate();
            EditorInspectorUi.hint(EditorI18n.t("Случайный сдвиг точки появления"));

            single[0] = node.particleSpeed;
            if (EditorInspectorUi.dragFloat(EditorI18n.t("Скорость вылета"), "pt-speed", single, 0.005f, 0f, 3f)) {
                applyEdit(() -> node.particleSpeed = single[0]);
            }
            EditorInspectorUi.undoOnActivate();

            single[0] = node.particleSpeedSpread;
            if (EditorInspectorUi.dragFloat(EditorI18n.t("Разброс скорости"), "pt-speed-spread", single, 0.01f, 0f, 1f)) {
                applyEdit(() -> node.particleSpeedSpread = single[0]);
            }
            EditorInspectorUi.undoOnActivate();
            EditorInspectorUi.hint(EditorI18n.t("0 = одинаковая · 0.5 = ±50% к скорости каждой частицы"));

            single[0] = node.particleGravity;
            if (EditorInspectorUi.dragFloat(EditorI18n.t("Гравитация"), "pt-grav", single, 0.01f, 0f, 5f)) {
                applyEdit(() -> node.particleGravity = single[0]);
            }
            EditorInspectorUi.undoOnActivate();
            EditorInspectorUi.hint(EditorI18n.t("После вылета постепенно тянет вниз"));

            rst(factoryNode.particleCollide).checkbox(EditorI18n.t("Коллизия с блоками"), node.particleCollide, value -> node.particleCollide = value,
                    "_node.collide = true\n_node.collision = true\n_node.particleCollide = true");
            EditorInspectorUi.hint(EditorI18n.t("Выкл = пролетают сквозь блоки · Вкл = ударяются / гаснут о стены"));
            endSection();
        }

        if (section("particle-timing", EditorI18n.t("Время / цикл"))) {
            rst(factoryNode.particleLifetime).dragInt(EditorI18n.t("Исчезновение (тики)"), node.particleLifetime, 1, 0, 400, value -> node.particleLifetime = value);
            EditorInspectorUi.hint(EditorI18n.t("0 = как у типа частицы · 20 ≈ 1 сек · 40 ≈ 2 сек"));
            rst(factoryNode.particleFade).dragInt(EditorI18n.t("Плавное затухание (тики)"), node.particleFade, 1, 0, 200, value -> node.particleFade = value,
                    "_node.fade = 10\n_node.particleFade = 10");
            EditorInspectorUi.hint(EditorI18n.t("0 = резко пропасть · 8–15 = мягко гаснут в конце жизни"));
            rst(factoryNode.particleLoop).checkbox(EditorI18n.t("Зациклить"), node.particleLoop, value -> node.particleLoop = value);
            rst(factoryNode.particleInterval).dragInt(EditorI18n.t("Интервал вспышек (тики)"), node.particleInterval, 1, 1, 200, value -> node.particleInterval = value);
            if (ImGui.button(EditorI18n.t("Запустить") + "##particle-play")) {
                if (isMulti()) {
                    for (SceneNode t : editTargets) {
                        if (t.type == SceneNodeType.PARTICLE) fireParticleOnce(t);
                    }
                } else {
                    fireParticleOnce(node);
                }
            }
            EditorInspectorUi.hint(EditorI18n.t(
                    "Разовая вспышка. В игре: node.emit() / particleEmit(node) / блок «запустить частицы»"));
            endSection();
        }

        if (section("particle-preview", EditorI18n.t("Предпросмотр"))) {
            rst(factoryNode.previewEnabled).checkbox(EditorI18n.t("Предпросмотр в мире"), node.previewEnabled,
                    value -> node.previewEnabled = value);
            EditorInspectorUi.hint(node.particleLoop
                    ? EditorI18n.t("Зацикл: вспышки по интервалу · ANIMATOR-ребёнок двигает transform")
                    : EditorI18n.t("Без зацикла: только кнопка «Запустить» / particleEmit · ANIMATOR двигает transform"));
            endSection();
        }
    }

    private void renderCustomParticle(SceneNode node) {
        var spec = node.particles(false);
        if (spec == null) return;

        if (section("particle-renderer-v1", EditorI18n.t("Renderer · Текстура и материал"))) {
            boolean modelMode = "model".equals(spec.renderer.mode);
            // Model mode's texture is a full entity/model skin (same asset category as an NPC's
            // texture), not a particle sprite — pointing the picker at particleTextures() here
            // would show the wrong folder and the user couldn't find their skin file.
            assetCombo(modelMode ? EditorI18n.t("Текстура (скин модели)") : EditorI18n.t("Текстура"),
                    spec.renderer.texture,
                    modelMode ? EditorAssetIndex.textures() : EditorAssetIndex.particleTextures(),
                    textureSearch, value -> {
                        spec.renderer.texture = value;
                        particleChanged(node);
                    }, modelMode ? "_node.particles.renderer.texture = \"textures/entity/gem.png\""
                            : "_node.particles.renderer.texture = \"minecraft:textures/particle/generic_0.png\"");
            if (ImGui.smallButton(EditorI18n.t("Обновить список ассетов"))) EditorAssetIndex.refresh();

            EditorInspectorUi.fieldLabel(EditorI18n.t("Геометрия"));
            if (ImGui.beginCombo("##particle-render-mode", particleRenderModeLabel(spec.renderer.mode))) {
                particleRenderModeOption(node, spec, "billboard", "Billboard");
                particleRenderModeOption(node, spec, "stretched", "Stretched billboard");
                particleRenderModeOption(node, spec, "velocity", "Velocity aligned");
                particleRenderModeOption(node, spec, "axis", "Axis aligned");
                particleRenderModeOption(node, spec, "cross", "Cross");
                particleRenderModeOption(node, spec, "mesh", "Simple mesh");
                particleRenderModeOption(node, spec, "model", "3D модель");
                ImGui.endCombo();
            }
            if ("model".equals(spec.renderer.mode)) {
                assetCombo(EditorI18n.t("Модель частицы"), spec.renderer.model, EditorAssetIndex.models(),
                        modelSearch, value -> {
                            spec.renderer.model = value;
                            particleChanged(node);
                        }, "_node.particles.renderer.model = \"geo/spark.geo.json\"");
                EditorInspectorUi.hint(EditorI18n.t(
                        "Частица рендерится как настоящий .geo меш (как у NPC), а не как billboard-спрайт — "
                        + "вращается своим spin, не смотрит на камеру. Пустая текстура = ничего не нарисуется "
                        + "(в отличие от billboard-режимов, где пустая текстура даёт частицу-заглушку)."));
            }
            EditorInspectorUi.fieldLabel(EditorI18n.t("Смешивание"));
            if (ImGui.beginCombo("##particle-blend", spec.renderer.blend)) {
                for (String value : new String[]{"alpha", "additive", "cutout"}) {
                    if (ImGui.selectable(value, value.equals(spec.renderer.blend))) {
                        particleEdit(node, () -> spec.renderer.blend = value);
                    }
                }
                ImGui.endCombo();
            }
            if (ImGui.checkbox(EditorI18n.t("Fullbright / emissive"), spec.renderer.fullbright)) {
                particleEdit(node, () -> spec.renderer.fullbright = !spec.renderer.fullbright);
            }
            if (ImGui.checkbox(EditorI18n.t("Писать глубину"), spec.renderer.depthWrite)) {
                particleEdit(node, () -> spec.renderer.depthWrite = !spec.renderer.depthWrite);
            }
            particleFloat(node, EditorI18n.t("Glow (яркость цвета)"), "pt-glow",
                    spec.renderer.glowStrength, 0.05f, 0.1f, 8f, value -> spec.renderer.glowStrength = value);
            EditorInspectorUi.hint(EditorI18n.t(
                    "Умножает RGB частицы перед блендингом. Сильнее всего заметно с additive: "
                    + "яркий насыщенный цвет + additive = свечение при наложении частиц друг на друга."));
            particleFloat(node, EditorI18n.t("Мягкий край"), "pt-soft-edge",
                    spec.renderer.softEdge, 0.02f, 0f, 1f, value -> spec.renderer.softEdge = value);
            EditorInspectorUi.hint(EditorI18n.t(
                    "0 = обычный жёсткий силуэт спрайта (без доп. затрат). Выше — альфа плавно спадает "
                    + "от центра к краю, спрайт выглядит круглым/мягким даже с резкой текстурой. "
                    + "Это не depth-fade по геометрии — жёсткая линия на пересечении с ландшафтом остаётся."));
            particleFloat(node, EditorI18n.t("Растяжение по скорости"), "pt-stretch",
                    spec.renderer.stretch, 0.05f, 0f, 20f, value -> spec.renderer.stretch = value);
            particleInt(node, EditorI18n.t("Sprite sheet · колонки"), "pt-sheet-x",
                    spec.renderer.sheetColumns, 1, 1, 32, value -> spec.renderer.sheetColumns = value);
            particleInt(node, EditorI18n.t("Sprite sheet · строки"), "pt-sheet-y",
                    spec.renderer.sheetRows, 1, 1, 32, value -> spec.renderer.sheetRows = value);
            particleFloat(node, "FPS", "pt-sheet-fps", spec.renderer.sheetFps,
                    0.5f, 0f, 120f, value -> spec.renderer.sheetFps = value);
            endSection();
        }

        if (section("particle-emission-v1", EditorI18n.t("Emission"))) {
            if (ImGui.checkbox(EditorI18n.t("Излучение включено"), spec.emission.enabled)) {
                particleEdit(node, () -> spec.emission.enabled = !spec.emission.enabled);
            }
            if (ImGui.checkbox(EditorI18n.t("Зациклить"), spec.emission.loop)) {
                particleEdit(node, () -> {
                    spec.emission.loop = !spec.emission.loop;
                    node.particleLoop = spec.emission.loop;
                });
            }
            particleFloat(node, EditorI18n.t("Частиц / сек"), "pt-rate", spec.emission.rate,
                    0.5f, 0f, 10000f, value -> spec.emission.rate = value);
            particleFloat(node, EditorI18n.t("Задержка (сек)"), "pt-delay", spec.emission.delay,
                    0.05f, 0f, 300f, value -> spec.emission.delay = value);
            particleFloat(node, EditorI18n.t("Длительность (сек)"), "pt-duration", spec.emission.duration,
                    0.1f, 0.05f, 3600f, value -> spec.emission.duration = value);
            particleInt(node, EditorI18n.t("Лимит системы"), "pt-cap", spec.emission.maxParticles,
                    16, 1, 32768, value -> spec.emission.maxParticles = value);
            particleInt(node, "Seed", "pt-seed", spec.emission.seed,
                    1, 0, Integer.MAX_VALUE, value -> spec.emission.seed = value);
            endSection();
        }

        if (section("particle-shape-v1", EditorI18n.t("Shape · Форма и направление"))) {
            EditorInspectorUi.fieldLabel(EditorI18n.t("Форма"));
            if (ImGui.beginCombo("##particle-shape-v1", spec.shape.type)) {
                for (String value : new String[]{"point", "line", "box", "sphere", "shell",
                        "circle", "disc", "cone", "cylinder", "spiral", "text", "image"}) {
                    if (ImGui.selectable(value, value.equals(spec.shape.type))) {
                        particleEdit(node, () -> spec.shape.type = value);
                    }
                }
                ImGui.endCombo();
            }
            if ("text".equals(spec.shape.type) || "image".equals(spec.shape.type)) {
                if ("text".equals(spec.shape.type)) {
                    shapeTextBuffer.set(spec.shape.text == null ? "" : spec.shape.text);
                    if (EditorInspectorUi.inputText(EditorI18n.t("Текст"), "pt-shape-text", shapeTextBuffer,
                            "HELLO")) {
                        particleEdit(node, () -> spec.shape.text = shapeTextBuffer.get());
                    }
                } else {
                    assetCombo(EditorI18n.t("Картинка (маска)"), spec.shape.image, EditorAssetIndex.particleTextures(),
                            textureSearch, value -> {
                                spec.shape.image = value;
                                particleChanged(node);
                            }, "_node.particles.shape.image = \"textures/particle/mask.png\"");
                }
                particleFloat(node, EditorI18n.t("Порог альфы"), "pt-shape-threshold", spec.shape.sampleThreshold,
                        0.01f, 0.01f, 1f, value -> spec.shape.sampleThreshold = value);
                particleInt(node, EditorI18n.t("Разрешение сэмплинга"), "pt-shape-resolution", spec.shape.sampleResolution,
                        1, 4, 256, value -> spec.shape.sampleResolution = value);
                EditorInspectorUi.hint(EditorI18n.t("Точки частиц берутся из формы буквы/маски; sizeX/sizeY (ниже) — масштаб"));
            }
            if (ImGui.checkbox(EditorI18n.t("Только поверхность"), spec.shape.surfaceOnly)) {
                particleEdit(node, () -> spec.shape.surfaceOnly = !spec.shape.surfaceOnly);
            }
            particleDir[0] = spec.shape.dirX;
            particleDir[1] = spec.shape.dirY;
            particleDir[2] = spec.shape.dirZ;
            if (EditorInspectorUi.vector3("pt-custom-dir", particleDir, 0.05f, -10f, 10f)) {
                particleEdit(node, () -> {
                    spec.shape.dirX = particleDir[0];
                    spec.shape.dirY = particleDir[1];
                    spec.shape.dirZ = particleDir[2];
                });
            }
            EditorInspectorUi.hint(EditorI18n.t("Направление локальное: (0,1,0) вверх, крутится вместе с нодой"));
            EditorInspectorUi.hint(EditorI18n.t(shapeRelevanceHint(spec.shape.type)));
            particleFloat(node, EditorI18n.t("Угол конуса °"), "pt-custom-angle", spec.shape.angle,
                    0.5f, 0f, 180f, value -> spec.shape.angle = value);
            particleFloat(node, EditorI18n.t("Радиус"), "pt-custom-radius", spec.shape.radius,
                    0.02f, 0f, 100f, value -> spec.shape.radius = value);
            particleFloat(node, EditorI18n.t("Высота"), "pt-custom-height", spec.shape.height,
                    0.05f, 0f, 100f, value -> spec.shape.height = value);
            particleFloat(node, EditorI18n.t("Длина"), "pt-custom-length", spec.shape.length,
                    0.05f, 0f, 100f, value -> spec.shape.length = value);
            EditorInspectorUi.fieldLabel(EditorI18n.t(shapeSizeLabel(spec.shape.type)));
            particleSpread[0] = spec.shape.sizeX;
            particleSpread[1] = spec.shape.sizeY;
            particleSpread[2] = spec.shape.sizeZ;
            if (EditorInspectorUi.vector3("pt-custom-size", particleSpread, 0.05f, 0f, 100f)) {
                particleEdit(node, () -> {
                    spec.shape.sizeX = particleSpread[0];
                    spec.shape.sizeY = particleSpread[1];
                    spec.shape.sizeZ = particleSpread[2];
                });
            }
            EditorInspectorUi.hint(EditorI18n.t(shapeSizeHint(spec.shape.type)));
            endSection();
        }

        if (section("particle-initial-v1", EditorI18n.t("Initial · Старт"))) {
            particleFloat(node, EditorI18n.t("Жизнь min (сек)"), "pt-life-min", spec.initial.lifetimeMin,
                    0.05f, 0.05f, 300f, value -> spec.initial.lifetimeMin = value);
            particleFloat(node, EditorI18n.t("Жизнь max (сек)"), "pt-life-max", spec.initial.lifetimeMax,
                    0.05f, 0.05f, 300f, value -> spec.initial.lifetimeMax = value);
            particleFloat(node, EditorI18n.t("Скорость min"), "pt-speed-min", spec.initial.speedMin,
                    0.005f, 0f, 100f, value -> spec.initial.speedMin = value);
            particleFloat(node, EditorI18n.t("Скорость max"), "pt-speed-max", spec.initial.speedMax,
                    0.005f, 0f, 100f, value -> spec.initial.speedMax = value);
            particleFloat(node, EditorI18n.t("Размер min"), "pt-size-min", spec.initial.sizeMin,
                    0.01f, 0f, 100f, value -> spec.initial.sizeMin = value);
            particleFloat(node, EditorI18n.t("Размер max"), "pt-size-max", spec.initial.sizeMax,
                    0.01f, 0f, 100f, value -> spec.initial.sizeMax = value);
            particleFloat(node, EditorI18n.t("Соотношение сторон"), "pt-aspect", spec.initial.aspect,
                    0.01f, 0.01f, 100f, value -> spec.initial.aspect = value);
            endSection();
        }

        if (section("particle-lifetime-v1", EditorI18n.t("Over Lifetime"))) {
            particleFloat(node, EditorI18n.t("Размер в начале"), "pt-size-start",
                    spec.lifetime.sizeStart, 0.01f, 0f, 20f, value -> spec.lifetime.sizeStart = value);
            particleFloat(node, EditorI18n.t("Размер в конце"), "pt-size-end",
                    spec.lifetime.sizeEnd, 0.01f, 0f, 20f, value -> spec.lifetime.sizeEnd = value);
            lifetimeCurveCombo(node, EditorI18n.t("Кривая размера"), "pt-curve-size", spec.lifetime.sizeCurve,
                    value -> spec.lifetime.sizeCurve = value);
            if (UiStyleEditor.colorEditRgba(EditorI18n.t("Цвет начала · от"), "pt-color-start-min",
                    spec.initial.colorMin, hex -> {
                        spec.initial.colorMin = hex;
                        spec.lifetime.colorStart = hex;
                    })) {
                particleChanged(node);
            }
            if (UiStyleEditor.colorEditRgba(EditorI18n.t("Цвет начала · до"), "pt-color-start-max",
                    spec.initial.colorMax, hex -> spec.initial.colorMax = hex)) {
                particleChanged(node);
            }
            if (UiStyleEditor.colorEditRgba(EditorI18n.t("Цвет конца · от"), "pt-color-end-min",
                    spec.lifetime.colorEndMin, hex -> {
                        spec.lifetime.colorEndMin = hex;
                        spec.lifetime.colorEnd = hex;
                    })) {
                particleChanged(node);
            }
            if (UiStyleEditor.colorEditRgba(EditorI18n.t("Цвет конца · до"), "pt-color-end-max",
                    spec.lifetime.colorEndMax, hex -> spec.lifetime.colorEndMax = hex)) {
                particleChanged(node);
            }
            lifetimeCurveCombo(node, EditorI18n.t("Кривая цвета/альфы"), "pt-curve-color", spec.lifetime.colorCurve,
                    value -> spec.lifetime.colorCurve = value);
            particleFloat(node, EditorI18n.t("Скорость в начале"), "pt-speed-life-start",
                    spec.lifetime.speedStart, 0.01f, 0f, 20f, value -> spec.lifetime.speedStart = value);
            particleFloat(node, EditorI18n.t("Скорость в конце"), "pt-speed-life-end",
                    spec.lifetime.speedEnd, 0.01f, 0f, 20f, value -> spec.lifetime.speedEnd = value);
            lifetimeCurveCombo(node, EditorI18n.t("Кривая скорости"), "pt-curve-speed", spec.lifetime.speedCurve,
                    value -> spec.lifetime.speedCurve = value);
            endSection();
        }

        if (EditorInspectorUi.beginSection("particle-motion-v1", EditorI18n.t("Motion · Силы"), true)) {
            boolean noGravity = spec.motion.gravityX == 0f && spec.motion.gravityY == 0f && spec.motion.gravityZ == 0f;
            if (ImGui.checkbox(EditorI18n.t("Без гравитации") + "##motion", noGravity)) {
                particleEdit(node, () -> {
                    if (noGravity) {
                        spec.motion.gravityY = -0.04f;
                    } else {
                        spec.motion.gravityX = 0f;
                        spec.motion.gravityY = 0f;
                        spec.motion.gravityZ = 0f;
                    }
                });
            }
            particleFloat(node, EditorI18n.t("Гравитация (Y)"), "pt-gravity-y-motion", spec.motion.gravityY,
                    0.005f, -2f, 2f, value -> spec.motion.gravityY = value);
            EditorInspectorUi.fieldLabel(EditorI18n.t("Гравитация XYZ"));
            particleDir[0] = spec.motion.gravityX;
            particleDir[1] = spec.motion.gravityY;
            particleDir[2] = spec.motion.gravityZ;
            if (EditorInspectorUi.vector3("pt-gravity-v1", particleDir, 0.005f, -10f, 10f)) {
                particleEdit(node, () -> {
                    spec.motion.gravityX = particleDir[0];
                    spec.motion.gravityY = particleDir[1];
                    spec.motion.gravityZ = particleDir[2];
                });
            }
            EditorInspectorUi.hint(EditorI18n.t("0 = нет · минус = вниз · плюс = вверх"));
            particleFloat(node, EditorI18n.t("Drag"), "pt-drag-v1", spec.motion.drag,
                    0.01f, 0f, 100f, value -> spec.motion.drag = value);
            particleFloat(node, EditorI18n.t("Turbulence"), "pt-noise-v1", spec.motion.noiseStrength,
                    0.01f, 0f, 100f, value -> spec.motion.noiseStrength = value);
            particleFloat(node, EditorI18n.t("Noise frequency"), "pt-noise-freq-v1", spec.motion.noiseFrequency,
                    0.05f, 0f, 100f, value -> spec.motion.noiseFrequency = value);
            particleFloat(node, EditorI18n.t("Vortex"), "pt-vortex-v1", spec.motion.vortexStrength,
                    0.01f, -100f, 100f, value -> spec.motion.vortexStrength = value);
            particleFloat(node, EditorI18n.t("Attractor"), "pt-attractor-v1", spec.motion.attractorStrength,
                    0.01f, -100f, 100f, value -> spec.motion.attractorStrength = value);
            attractorTargetBuffer.set(spec.motion.attractorTarget == null ? "" : spec.motion.attractorTarget);
            if (EditorInspectorUi.inputText(EditorI18n.t("Цель attractor'а"), "pt-attractor-target",
                    attractorTargetBuffer, "origin")) {
                particleEdit(node, () -> spec.motion.attractorTarget = attractorTargetBuffer.get());
            }
            EditorInspectorUi.hint(EditorI18n.t(
                    "пусто/\"origin\" = точка спавна частицы · или \"x,y,z\" = фиксированная точка в мире. "
                    + "Attractor + Vortex вместе = вращающийся шар частиц по стабильной орбите."));
            particleFloat(node, EditorI18n.t("Max speed (0 = нет)"), "pt-max-speed-v1", spec.motion.maxSpeed,
                    0.01f, 0f, 100f, value -> spec.motion.maxSpeed = value);
            endSection();
        }

        if (section("particle-collision-v1", EditorI18n.t("Collision"))) {
            if (ImGui.checkbox(EditorI18n.t("Коллизия с блоками"), spec.collision.enabled)) {
                particleEdit(node, () -> spec.collision.enabled = !spec.collision.enabled);
            }
            if (ImGui.checkbox(EditorI18n.t("Уничтожать при столкновении"), spec.collision.killOnCollision)) {
                particleEdit(node, () -> spec.collision.killOnCollision = !spec.collision.killOnCollision);
            }
            particleFloat(node, "Bounce", "pt-bounce-v1", spec.collision.bounce,
                    0.01f, 0f, 1f, value -> spec.collision.bounce = value);
            particleFloat(node, "Friction", "pt-friction-v1", spec.collision.friction,
                    0.01f, 0f, 1f, value -> spec.collision.friction = value);
            endSection();
        }

        if (section("particle-trail-v1", EditorI18n.t("Trails"))) {
            if (ImGui.checkbox(EditorI18n.t("След включён"), spec.trail.enabled)) {
                particleEdit(node, () -> spec.trail.enabled = !spec.trail.enabled);
            }
            particleInt(node, EditorI18n.t("Точек следа"), "pt-trail-points",
                    spec.trail.maxPoints, 1, 2, 128, value -> spec.trail.maxPoints = value);
            endSection();
        }

        if (section("particle-gameplay-v1", EditorI18n.t("Gameplay Proxy"))) {
            if (ImGui.checkbox(EditorI18n.t("Серверные позиции и события"), spec.gameplay.enabled)) {
                particleEdit(node, () -> spec.gameplay.enabled = !spec.gameplay.enabled);
            }
            particleInt(node, EditorI18n.t("Лимит proxy"), "pt-proxy-cap",
                    spec.gameplay.maxProxies, 1, 1, 4096, value -> spec.gameplay.maxProxies = value);
            particleFloat(node, EditorI18n.t("Радиус overlap"), "pt-proxy-radius",
                    spec.gameplay.radius, 0.01f, 0.01f, 16f, value -> spec.gameplay.radius = value);
            EditorInspectorUi.hint(EditorI18n.t(
                    "События: particleBirth, particleDeath, particleCollision, particleEnter, particleExit"));
            endSection();
        }

        if (section("particle-light-v1", EditorI18n.t("Particle-as-Light"))) {
            if (ImGui.checkbox(EditorI18n.t("Частицы светят (реальный block-light)"), spec.light.enabled)) {
                particleEdit(node, () -> {
                    spec.light.enabled = !spec.light.enabled;
                    if (spec.light.enabled) spec.gameplay.enabled = true;
                });
            }
            particleInt(node, EditorI18n.t("Уровень света (0-15)"), "pt-light-level",
                    spec.light.level, 1, 0, 15, value -> spec.light.level = value);
            particleInt(node, EditorI18n.t("Кол-во источников света"), "pt-light-count",
                    spec.light.maxLights, 1, 0, 16, value -> spec.light.maxLights = value);
            EditorInspectorUi.hint(EditorI18n.t(
                    "Только ванильный minecraft:light (белый, 0-15) на позиции gameplay-proxy частиц. "
                    + "Включает Gameplay Proxy автоматически. Свет капается и гаснет с небольшой задержкой "
                    + "(~0.5с) после смерти/остановки частицы — дорого пересчитывать точно каждый тик."));
            endSection();
        }

        if (section("particle-subemitters-v1", EditorI18n.t("Sub-Emitters"))) {
            EditorInspectorUi.hint(EditorI18n.t(
                    "Каждый sub-emitter — своя мини-система частиц, рождающаяся в точке родительской частицы"));
            int[] pendingRemove = {-1};
            for (int i = 0; i < spec.subEmitters.size(); i++) {
                ImGui.pushID("pt-sub-" + i);
                var sub = spec.subEmitters.get(i);
                var childSpec = sub.spec;
                ImGui.separator();
                EditorInspectorUi.fieldLabel(EditorI18n.t("Событие"));
                if (ImGui.beginCombo("##pt-sub-trigger", sub.trigger)) {
                    for (String value : new String[]{"birth", "death", "collision"}) {
                        if (ImGui.selectable(value, value.equals(sub.trigger))) {
                            particleEdit(node, () -> sub.trigger = value);
                        }
                    }
                    ImGui.endCombo();
                }
                particleFloat(node, EditorI18n.t("Вероятность"), "pt-sub-prob", sub.probability,
                        0.01f, 0f, 1f, value -> sub.probability = value);
                particleInt(node, EditorI18n.t("Частиц за событие"), "pt-sub-count", sub.count,
                        1, 0, 256, value -> sub.count = value);
                particleFloat(node, EditorI18n.t("Наследование скорости"), "pt-sub-inherit", sub.inheritVelocity,
                        0.01f, 0f, 1f, value -> sub.inheritVelocity = value);

                assetCombo(EditorI18n.t("Текстура"), childSpec.renderer.texture, EditorAssetIndex.particleTextures(),
                        textureSearch, value -> {
                            childSpec.renderer.texture = value;
                            particleChanged(node);
                        }, "_node.particles.subEmitters[" + i + "].spec.renderer.texture = \"...\"");
                EditorInspectorUi.fieldLabel(EditorI18n.t("Форма"));
                if (ImGui.beginCombo("##pt-sub-shape", childSpec.shape.type)) {
                    for (String value : new String[]{"point", "line", "box", "sphere", "shell",
                            "circle", "disc", "cone", "cylinder", "spiral"}) {
                        if (ImGui.selectable(value, value.equals(childSpec.shape.type))) {
                            particleEdit(node, () -> childSpec.shape.type = value);
                        }
                    }
                    ImGui.endCombo();
                }
                particleFloat(node, EditorI18n.t("Размер min"), "pt-sub-size-min", childSpec.initial.sizeMin,
                        0.01f, 0f, 100f, value -> childSpec.initial.sizeMin = value);
                particleFloat(node, EditorI18n.t("Размер max"), "pt-sub-size-max", childSpec.initial.sizeMax,
                        0.01f, 0f, 100f, value -> childSpec.initial.sizeMax = value);
                particleFloat(node, EditorI18n.t("Жизнь min (сек)"), "pt-sub-life-min", childSpec.initial.lifetimeMin,
                        0.05f, 0.05f, 300f, value -> childSpec.initial.lifetimeMin = value);
                particleFloat(node, EditorI18n.t("Жизнь max (сек)"), "pt-sub-life-max", childSpec.initial.lifetimeMax,
                        0.05f, 0.05f, 300f, value -> childSpec.initial.lifetimeMax = value);
                if (UiStyleEditor.colorEditRgba(EditorI18n.t("Цвет"), "pt-sub-color", childSpec.initial.colorMin,
                        hex -> {
                            childSpec.initial.colorMin = hex;
                            childSpec.initial.colorMax = hex;
                            childSpec.lifetime.colorStart = hex;
                        })) {
                    particleChanged(node);
                }
                particleInt(node, EditorI18n.t("Лимит sub-системы"), "pt-sub-cap", childSpec.emission.maxParticles,
                        16, 1, 4096, value -> childSpec.emission.maxParticles = value);

                if (ImGui.smallButton(EditorI18n.t("Удалить sub-emitter"))) {
                    pendingRemove[0] = i;
                }
                ImGui.popID();
            }
            if (pendingRemove[0] >= 0) {
                int idx = pendingRemove[0];
                particleEdit(node, () -> spec.subEmitters.remove(idx));
            }
            ImGui.separator();
            if (ImGui.smallButton(EditorI18n.t("+ Добавить sub-emitter"))) {
                particleEdit(node, () -> spec.subEmitters.add(new org.zonarstudio.spraute_engine.particle.ParticleSystemSpec.SubEmitter()));
            }
            endSection();
        }

        if (section("particle-preview-v1", EditorI18n.t("Preview и бюджет"))) {
            var stats = org.zonarstudio.spraute_engine.client.particle.SprauteParticleEngine.stats();
            ImGui.text(EditorI18n.t("Живых: ") + stats.alive()
                    + EditorI18n.t(" · Создано: ") + stats.spawned()
                    + EditorI18n.t(" · Отброшено: ") + stats.dropped());
            ImGui.textDisabled(EditorI18n.t("В этой системе: ")
                    + org.zonarstudio.spraute_engine.client.particle.SprauteParticleEngine.count(node.id));
            if (ImGui.button(EditorI18n.t("Запустить") + "##particle-custom-play")) {
                fireParticleOnce(node);
            }
            ImGui.sameLine();
            if (ImGui.button(EditorI18n.t("Очистить"))) {
                org.zonarstudio.spraute_engine.client.particle.SprauteParticleEngine.clear(node.id);
            }
            EditorInspectorUi.hint(EditorI18n.t(
                    "Разовая вспышка. В игре: node.emit() / particleEmit(node) / блок «запустить частицы»"));
            endSection();
        }
    }

    private String particleRenderModeLabel(String mode) {
        return mode == null || mode.isBlank() ? "billboard" : mode;
    }

    private void particleRenderModeOption(SceneNode node,
                                          org.zonarstudio.spraute_engine.particle.ParticleSystemSpec spec,
                                          String value, String label) {
        if (ImGui.selectable(label, value.equals(spec.renderer.mode))) {
            particleEdit(node, () -> spec.renderer.mode = value);
        }
    }

    private void particleFloat(SceneNode node, String label, String id, float current,
                               float speed, float min, float max,
                               java.util.function.Consumer<Float> setter) {
        single[0] = current;
        if (EditorInspectorUi.dragFloat(label, id, single, speed, min, max)) {
            float value = single[0];
            particleEdit(node, () -> setter.accept(value));
        }
        EditorInspectorUi.undoOnActivate();
    }

    private void particleInt(SceneNode node, String label, String id, int current,
                             int speed, int min, int max,
                             java.util.function.IntConsumer setter) {
        int[] value = {current};
        if (ImGui.dragInt(label + "##" + id, value, speed, min, max)) {
            int next = value[0];
            particleEdit(node, () -> setter.accept(next));
        }
        EditorInspectorUi.undoOnActivate();
    }

    private static final String[] LIFETIME_CURVE_PRESETS =
            {"linear", "ease_in", "ease_out", "ease_in_out", "smooth", "pulse", "step"};

    /** Shared combo for a per-parameter over-lifetime curve preset (size/color/speed/…). */
    private void lifetimeCurveCombo(SceneNode node, String label, String id, String current,
                                    java.util.function.Consumer<String> setter) {
        EditorInspectorUi.fieldLabel(label);
        if (ImGui.beginCombo("##" + id, current)) {
            for (String value : LIFETIME_CURVE_PRESETS) {
                if (ImGui.selectable(value, value.equals(current))) {
                    particleEdit(node, () -> setter.accept(value));
                }
            }
            ImGui.endCombo();
        }
    }

    /** Which of the shape-specific fields above (angle/radius/height/length) actually matter for the
     *  currently selected shape kind — those fields are always shown together, so it isn't obvious
     *  which ones apply to which shape without this. */
    private static String shapeRelevanceHint(String type) {
        String kind = type == null ? "point" : type.trim().toLowerCase();
        return switch (kind) {
            case "point" -> "Точка: только направление/угол конуса — радиус/высота/длина не используются";
            case "line" -> "Линия использует свои X/Y/Z из скрипта (particles.shape.lineX/Y/Z) — не редактируется здесь";
            case "box" -> "Box: использует размер ниже (X/Y/Z) — радиус/высота/длина не используются";
            case "sphere", "shell" -> "Сфера/оболочка: использует «Радиус» — высота/длина не используются";
            case "circle", "disc" -> "Круг/диск: использует «Радиус» — высота/длина не используются";
            case "cone" -> "Конус: использует «Угол конуса» и «Длина» (высота конуса)";
            case "cylinder" -> "Цилиндр: использует «Радиус» и «Высота»";
            case "spiral" -> "Спираль: использует «Радиус» и «Высота» (шаг за виток)";
            case "text", "image" -> "Текст/картинка: форма берётся из текста/маски выше, размер — X/Y ниже";
            default -> "";
        };
    }

    /** Label above the size vector3 — "box size" was shown for every shape, including ones it
     *  doesn't apply to at all. */
    private static String shapeSizeLabel(String type) {
        String kind = type == null ? "point" : type.trim().toLowerCase();
        return switch (kind) {
            case "text", "image" -> "Масштаб формы (X / Y, Z не используется)";
            case "box" -> "Размер box (X / Y / Z)";
            default -> "Размер (X / Y / Z) — используется только формами box, text и image";
        };
    }

    private static String shapeSizeHint(String type) {
        String kind = type == null ? "point" : type.trim().toLowerCase();
        return switch (kind) {
            case "text" -> "X/Y — ширина/высота текста в блоках; Z не используется. Плотность точек — «Разрешение сэмплинга» выше";
            case "image" -> "X/Y — ширина/высота картинки в блоках; Z не используется. Плотность точек — «Разрешение сэмплинга» выше";
            case "box" -> "Размер box по X / Y / Z";
            default -> "Не влияет на текущую форму — используется только box/text/image";
        };
    }

    private void particleEdit(SceneNode node, Runnable edit) {
        applyEdit(() -> {
            edit.run();
            particleChanged(node);
        });
    }

    private void particleChanged(SceneNode node) {
        SceneEditorSession.markDirty();
        var spec = node.particles(false);
        if (spec != null) {
            org.zonarstudio.spraute_engine.scene.SceneParticleEmitter.invalidateSpecJson(spec);
            org.zonarstudio.spraute_engine.client.particle.SprauteParticleEngine.update(node.id, spec);
        }
        SceneEditorSession.syncLiveNode(node.id);
    }

    private void fireParticleOnce(SceneNode node) {
        if (node == null || node.type != SceneNodeType.PARTICLE) return;
        if (SceneEditorSession.isLiveRuntime()) {
            SceneEditorSession.emitLiveParticle(node.id);
            return;
        }
        ScenePreviewManager.playParticlePreviewOnce(node.id);
    }

    private void renderLight(SceneNode node) {
        if (!isMulti() && section("light-object", EditorI18n.t("Объект"))) {
            nameBuffer.set(node.name);
            if (EditorInspectorUi.inputText(EditorI18n.t("Имя"), "light-name", nameBuffer, "_node.name = \"lamp\"")) {
                node.name = nameBuffer.get();
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.undoOnActivate();
            endSection();
        }

        if (section("light-props", EditorI18n.t("Свет"))) {
            rst(factoryNode.lightEnabled).checkbox(EditorI18n.t("Включён"), node.lightEnabled, value -> node.lightEnabled = value,
                    "_node.enabled = true\n_node.lightEnabled = true");
            rst(factoryNode.lightLevel).dragInt(EditorI18n.t("Уровень (0–15)"), node.lightLevel, 1, 0, 15, value -> node.lightLevel = value,
                    "_node.level = 15\n_node.light = 15");
            EditorInspectorUi.hint(EditorI18n.t("Ванильный minecraft:light — белый свет, радиус ≈ уровень блоков"));
            rst(factoryNode.previewEnabled).checkbox(EditorI18n.t("Предпросмотр в мире"), node.previewEnabled,
                    value -> node.previewEnabled = value);
            EditorInspectorUi.hint(EditorI18n.t("В одиночной игре ставит реальный minecraft:light в позиции узла"));
            endSection();
        }
    }

    private void renderAudioSource(SceneNode node) {
        if (!isMulti() && section("audio-object", EditorI18n.t("Объект"))) {
            nameBuffer.set(node.name);
            if (EditorInspectorUi.inputText(EditorI18n.t("Имя"), "audio-name", nameBuffer, "_node.name = \"music\"")) {
                node.name = nameBuffer.get();
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.undoOnActivate();
            endSection();
        }

        if (section("audio-props", EditorI18n.t("Аудио сурс"))) {
            rst(factoryNode.audioEnabled).checkbox(EditorI18n.t("Включён"), node.audioEnabled, value -> node.audioEnabled = value,
                    "_node.audioEnabled = true\n_node.enabled = true");
            EditorInspectorUi.fieldLabel(EditorI18n.t("Звук"),
                    EditorI18n.t("Свои файлы сверху: spraute_engine/sounds/ (.ogg и .mp3)"),
                    "_node.audioSound = \"minecraft:ambient.cave\"\n_node.sound = \"spraute_engine:mysound\"");
            String soundShown = node.audioSound == null || node.audioSound.isEmpty()
                    ? EditorI18n.t("(не выбран)")
                    : EditorAssetIndex.soundLabel(node.audioSound);
            if (ImGui.button(soundShown + "##audio-sound", Math.max(180f, ImGui.calcItemWidth()), 0f)) {
                ImGui.openPopup("insp-sound-pick");
            }
            if (ImGui.beginPopup("insp-sound-pick")) {
                EditorSoundPicker.renderList(node.audioSound, soundSearch, picked -> {
                    applyEdit(() -> node.audioSound = picked == null ? "" : picked);
                });
                ImGui.endPopup();
            }
            if (ImGui.smallButton(EditorI18n.t("Обновить список звуков"))) EditorAssetIndex.refreshSounds();
            EditorInspectorUi.hint(EditorI18n.t("Свои файлы сверху: spraute_engine/sounds/ (.ogg и .mp3)"));
            EditorInspectorUi.fieldLabel(EditorI18n.t("Форма"),
                    EditorI18n.t("sphere — эллипсоид, cube — куб. Размер тянется scale X/Y/Z."),
                    "_node.audioShape = \"sphere\"");
            String shape = org.zonarstudio.spraute_engine.scene.SceneAudioAttenuation.normalizeShape(node.audioShape);
            String shapeShown = "cube".equals(shape) ? EditorI18n.t("Куб") : EditorI18n.t("Сфера");
            if (ImGui.beginCombo("##audio-shape", shapeShown)) {
                if (ImGui.selectable(EditorI18n.t("Сфера"), !"cube".equals(shape))) {
                    SceneEditorSession.pushUndo();
                    node.audioShape = "sphere";
                    SceneEditorSession.markDirty();
                }
                if (ImGui.selectable(EditorI18n.t("Куб"), "cube".equals(shape))) {
                    SceneEditorSession.pushUndo();
                    node.audioShape = "cube";
                    SceneEditorSession.markDirty();
                }
                ImGui.endCombo();
            }
            rst(factoryNode.audioFullRadius).dragFloat(EditorI18n.t("Полная громкость до (блоки)"), node.audioFullRadius, 0.1f, 0.05f, 256f,
                    value -> node.audioFullRadius = value,
                    "_node.audioFullRadius = 4\n_node.fullRadius = 4");
            rst(factoryNode.audioMaxDistance).dragFloat(EditorI18n.t("Дальность (блоки)"), node.audioMaxDistance, 0.1f, 0.1f, 512f,
                    value -> node.audioMaxDistance = Math.max(node.audioFullRadius, value),
                    "_node.audioMaxDistance = 16\n_node.maxDistance = 16");
            rst(factoryNode.audioVolume).dragFloat(EditorI18n.t("Громкость"), node.audioVolume, 0.01f, 0f, 4f,
                    value -> node.audioVolume = value,
                    "_node.audioVolume = 1");
            rst(factoryNode.audioPitch).dragFloat(EditorI18n.t("Тон (pitch)"), node.audioPitch, 0.01f, 0.1f, 2f,
                    value -> node.audioPitch = value,
                    "_node.audioPitch = 1");
            rst(factoryNode.audioLoop).checkbox(EditorI18n.t("Зациклить"), node.audioLoop, value -> node.audioLoop = value,
                    "_node.audioLoop = true");
            rst(factoryNode.audioReplayOnEnter).checkbox(EditorI18n.t("Воспроизводить заново при входе в зону"), node.audioReplayOnEnter,
                    value -> node.audioReplayOnEnter = value,
                    "_node.audioReplayOnEnter = true\n_node.replayOnEnter = true\n_node.play()");
            EditorInspectorUi.hint(EditorI18n.t("Если звук не зациклен — при повторном входе в зону слышимости он стартует снова. Метод: _node.play() / startPlay()"));
            rst(factoryNode.showAudio).checkbox(EditorI18n.t("Показывать зоны всегда"), node.showAudio, value -> node.showAudio = value,
                    "_node.showAudio = true");
            EditorInspectorUi.hint(EditorI18n.t("showAudio — только в редакторе: рисовать голубую/оранжевую зону, даже если нода не выделена. На игру не влияет."));
            EditorInspectorUi.hint(EditorI18n.t("Голубая зона — 100% громкости; оранжевая — до куда слышно. Scale растягивает форму."));
            endSection();
        }
    }

    private void renderPostProcess(SceneNode node) {
        PostProcessSettings s = node.post();
        SceneDocument doc = SceneEditorSession.document();
        boolean cameraBound = ScenePostProcessRuntime.isCameraBound(doc, node);

        if (!isMulti() && section("post-object", EditorI18n.t("Объект"))) {
            nameBuffer.set(node.name);
            if (EditorInspectorUi.inputText(EditorI18n.t("Имя"), "post-name", nameBuffer, "_node.name = \"look\"")) {
                node.name = nameBuffer.get();
                SceneEditorSession.markDirty();
            }
            EditorInspectorUi.undoOnActivate();
            endSection();
        }

        if (section("post-volume", EditorI18n.t("Камера"))) {
            rst(factoryNode.post.enabled).checkbox(EditorI18n.t("Включён"), s.enabled, value -> s.enabled = value,
                    "_node.enabled = true");
            if (cameraBound) {
                EditorInspectorUi.hint(EditorI18n.t(
                        "Привязано к CAMERA игрока — эффект только у этого игрока. Сохраняется в «Игроки», как камера: живые PLAYER при запуске сцены в файл не пишутся."));
                s.global = true;
            } else {
                EditorInspectorUi.hint(EditorI18n.t(
                        "Перетащи ноду под CAMERA игрока (Игроки → Игрок → Камера), иначе эффект не применяется."));
            }
            rst(factoryNode.post.priority).dragInt(EditorI18n.t("Приоритет"), s.priority, 1, -100, 100, value -> s.priority = value,
                    "_node.priority = 0");
            EditorInspectorUi.hint(EditorI18n.t("Меньший priority блендится первым, больший перекрывает."));
            rst(factoryNode.post.weight).dragFloat(EditorI18n.t("Вес"), s.weight, 0.01f, 0f, 1f, value -> s.weight = value,
                    "_node.weight = 1");
            endSection();
        }

        if (section("post-grade", EditorI18n.t("Color grading"))) {
            rst(factoryNode.post.grade).checkbox(EditorI18n.t("Override"), s.grade, value -> s.grade = value,
                    "_node.grade = true");
            ImGui.beginDisabled(!s.grade);
            rst(factoryNode.post.exposure).dragFloat(EditorI18n.t("Exposure"), s.exposure, 0.01f, -4f, 4f, value -> s.exposure = value,
                    "_node.exposure = 0");
            rst(factoryNode.post.contrast).dragFloat(EditorI18n.t("Contrast"), s.contrast, 0.01f, -1f, 2f, value -> s.contrast = value,
                    "_node.contrast = 0");
            rst(factoryNode.post.saturation).dragFloat(EditorI18n.t("Saturation"), s.saturation, 0.01f, -1f, 2f, value -> s.saturation = value,
                    "_node.saturation = 0");
            rst(factoryNode.post.temperature).dragFloat(EditorI18n.t("Temperature"), s.temperature, 0.01f, -1f, 1f, value -> s.temperature = value,
                    "_node.temperature = 0");
            rst(factoryNode.post.tint).dragFloat(EditorI18n.t("Tint"), s.tint, 0.01f, -1f, 1f, value -> s.tint = value,
                    "_node.tint = 0");
            rst(factoryNode.post.filterR).dragFloat(EditorI18n.t("Filter R"), s.filterR, 0.01f, 0f, 2f, value -> s.filterR = value,
                    "_node.filterR = 1");
            rst(factoryNode.post.filterG).dragFloat(EditorI18n.t("Filter G"), s.filterG, 0.01f, 0f, 2f, value -> s.filterG = value,
                    "_node.filterG = 1");
            rst(factoryNode.post.filterB).dragFloat(EditorI18n.t("Filter B"), s.filterB, 0.01f, 0f, 2f, value -> s.filterB = value,
                    "_node.filterB = 1");
            ImGui.endDisabled();
            endSection();
        }

        if (section("post-bloom", EditorI18n.t("Bloom"))) {
            rst(factoryNode.post.bloom).checkbox(EditorI18n.t("Override##bloom"), s.bloom, value -> s.bloom = value,
                    "_node.bloom = 0.35");
            ImGui.beginDisabled(!s.bloom);
            rst(factoryNode.post.bloomIntensity).dragFloat(EditorI18n.t("Intensity##bloom"), s.bloomIntensity, 0.01f, 0f, 4f,
                    value -> s.bloomIntensity = value, "_node.bloom = 0.35");
            rst(factoryNode.post.bloomThreshold).dragFloat(EditorI18n.t("Threshold"), s.bloomThreshold, 0.01f, 0f, 2f,
                    value -> s.bloomThreshold = value, "_node.bloomThreshold = 0.75");
            ImGui.endDisabled();
            endSection();
        }

        if (section("post-vignette", EditorI18n.t("Vignette"))) {
            rst(factoryNode.post.vignette).checkbox(EditorI18n.t("Override##vignette"), s.vignette, value -> s.vignette = value,
                    "_node.vignette = 0.4");
            ImGui.beginDisabled(!s.vignette);
            rst(factoryNode.post.vignetteIntensity).dragFloat(EditorI18n.t("Intensity##vignette"), s.vignetteIntensity, 0.01f, 0f, 1f,
                    value -> s.vignetteIntensity = value, "_node.vignette = 0.4");
            rst(factoryNode.post.vignetteSmooth).dragFloat(EditorI18n.t("Smooth"), s.vignetteSmooth, 0.01f, 0f, 1.5f,
                    value -> s.vignetteSmooth = value, "_node.vignetteSmooth = 0.45");
            ImGui.endDisabled();
            endSection();
        }

        if (section("post-ca", EditorI18n.t("Chromatic aberration"))) {
            rst(factoryNode.post.chromatic).checkbox(EditorI18n.t("Override##ca"), s.chromatic, value -> s.chromatic = value,
                    "_node.chromatic = 0.004");
            ImGui.beginDisabled(!s.chromatic);
            rst(factoryNode.post.chromaticAmount).dragFloat(EditorI18n.t("Amount"), s.chromaticAmount, 0.0001f, 0f, 0.05f,
                    value -> s.chromaticAmount = value, "_node.chromatic = 0.004");
            ImGui.endDisabled();
            endSection();
        }

        if (section("post-grain", EditorI18n.t("Grain"))) {
            rst(factoryNode.post.grain).checkbox(EditorI18n.t("Override##grain"), s.grain, value -> s.grain = value,
                    "_node.grain = 0.06");
            ImGui.beginDisabled(!s.grain);
            rst(factoryNode.post.grainAmount).dragFloat(EditorI18n.t("Amount##grain"), s.grainAmount, 0.001f, 0f, 0.4f,
                    value -> s.grainAmount = value, "_node.grain = 0.06");
            ImGui.endDisabled();
            endSection();
        }

        if (section("post-sharpen", EditorI18n.t("Sharpen"))) {
            rst(factoryNode.post.sharpen).checkbox(EditorI18n.t("Override##sharpen"), s.sharpen, value -> s.sharpen = value,
                    "_node.sharpen = 0.2");
            ImGui.beginDisabled(!s.sharpen);
            rst(factoryNode.post.sharpenAmount).dragFloat(EditorI18n.t("Amount##sharpen"), s.sharpenAmount, 0.01f, 0f, 2f,
                    value -> s.sharpenAmount = value, "_node.sharpen = 0.2");
            ImGui.endDisabled();
            endSection();
        }

        if (cameraBound) {
            ClientScenePlayerNodes.pushPostAuthoring(doc, node);
        }
    }

    private void renderScript(SceneNode node) {
        if (!section("script", EditorI18n.t("Скрипт сцены"))) return;
        EditorInspectorUi.hint("scripts/" + node.script + ".spr");
        EditorInspectorUi.scriptApi("_node.script\nimport(\"" + (node.script == null ? "" : node.script) + "\")");
        if (EditorInspectorUi.primaryButton(EditorI18n.t("Открыть скрипт"), -1f)) {
            java.nio.file.Path path = SceneEditorSession.gameDir()
                    .resolve("spraute_engine").resolve("scripts")
                    .resolve(node.script + ".spr").normalize();
            if (java.nio.file.Files.isRegularFile(path)) {
                SceneEditorSession.requestOpenScript(path);
            } else {
                EditorInspectorUi.hint(EditorI18n.t("Файл скрипта не найден: scripts/") + node.script + ".spr");
            }
        }
        rst(!factoryNode.runOnSceneStart).checkbox(EditorI18n.t("Не запускать при старте сцены"), !node.runOnSceneStart,
                value -> node.runOnSceneStart = !value,
                "_node.runOnSceneStart = false");

        SceneNode parent = SceneEditorSession.document().findParent(node.id);
        if (parent != null) {
            EditorInspectorUi.hint("_node: " + parent.name + " [" + parent.type + "]");
            EditorInspectorUi.hint(EditorI18n.t("ID объекта: ") + parent.id);
        } else {
            EditorInspectorUi.hint(EditorI18n.t("_node: сцена"));
        }
        endSection();
    }

    private void renderVisualScript(SceneNode node) {
        if (!section("vscript", EditorI18n.t("Визуальный скрипт"))) return;
        EditorInspectorUi.hint(EditorI18n.t("Редактор: scripts/") + node.script + ".sprv");
        EditorInspectorUi.hint(EditorI18n.t("Рантайм: scripts/") + node.script + EditorI18n.t(".spr (генерируется при сохранении)"));
        java.nio.file.Path sprvPath = SceneEditorSession.gameDir()
                .resolve("spraute_engine").resolve("scripts")
                .resolve(node.script + ".sprv").normalize();
        java.nio.file.Path sprPath = SceneEditorSession.gameDir()
                .resolve("spraute_engine").resolve("scripts")
                .resolve(node.script + ".spr").normalize();
        if (!java.nio.file.Files.isRegularFile(sprPath)) {
            EditorInspectorUi.hint(EditorI18n.t("Рантайм .spr ещё нет — сохраните визуальный скрипт (Ctrl+S)"));
        }
        if (EditorInspectorUi.primaryButton(EditorI18n.t("Открыть визуальный скрипт"), -1f)) {
            if (java.nio.file.Files.isRegularFile(sprvPath)) {
                SceneEditorSession.requestOpenVisualScript(sprvPath);
            } else {
                EditorInspectorUi.hint(EditorI18n.t("Файл не найден: scripts/") + node.script + ".sprv");
            }
        }
        rst(!factoryNode.runOnSceneStart).checkbox(EditorI18n.t("Не запускать при старте сцены"), !node.runOnSceneStart,
                value -> node.runOnSceneStart = !value,
                "_node.runOnSceneStart = false");

        SceneNode parent = SceneEditorSession.document().findParent(node.id);
        if (parent != null) {
            EditorInspectorUi.hint("_node: " + parent.name + " [" + parent.type + "]");
            EditorInspectorUi.hint(EditorI18n.t("ID объекта: ") + parent.id);
        } else {
            EditorInspectorUi.hint(EditorI18n.t("_node: сцена"));
        }

        ImGui.spacing();
        ImGui.textColored(0.95f, 0.82f, 0.45f, 1f, EditorI18n.t("Метки"));
        VisualScriptEditor vs = VisualScriptEditor.findOpen(sprvPath);
        if (vs == null) {
            EditorInspectorUi.hint(EditorI18n.t("Откройте визуальный скрипт — метки появятся здесь"));
        } else {
            List<VisualScriptEditor.Bookmark> marks = vs.bookmarks();
            if (marks.isEmpty()) {
                EditorInspectorUi.hint(EditorI18n.t("ПКМ по пустому месту холста → «Создать метку»"));
            } else {
                for (VisualScriptEditor.Bookmark m : marks) {
                    ImGui.pushID("insp-bm-" + m.id);
                    ImGui.alignTextToFramePadding();
                    ImGui.text(m.name == null || m.name.isBlank() ? EditorI18n.t("(без имени)") : m.name);
                    ImGui.sameLine();
                    if (ImGui.smallButton(EditorI18n.t("Перейти"))) {
                        SceneEditorSession.requestOpenVisualScript(sprvPath);
                        vs.goToBookmark(m);
                    }
                    ImGui.popID();
                }
            }
        }
        endSection();
    }

    private void renderAnimator(SceneNode node) {
        if (section("animator", EditorI18n.t("Анимации"))) {
            var doc = SceneEditorSession.document();
            SceneNode owner = doc != null ? doc.findParent(node.id) : null;
            float cardX = ImGui.getCursorScreenPosX();
            float cardY = ImGui.getCursorScreenPosY();
            float cardW = ImGui.getContentRegionAvailX();
            float cardH = 72f;
            var draw = ImGui.getWindowDrawList();
            draw.addRectFilled(cardX, cardY, cardX + cardW, cardY + cardH,
                    rgba(0.12f, 0.075f, 0.16f, 0.98f), 8f);
            draw.addRect(cardX, cardY, cardX + cardW, cardY + cardH,
                    rgba(0.78f, 0.38f, 0.92f, 0.62f), 8f, 0, 1.2f);
            draw.addRectFilled(cardX, cardY, cardX + 4f, cardY + cardH,
                    rgba(0.86f, 0.42f, 0.96f, 1f), 8f);
            draw.addText(cardX + 15f, cardY + 10f, rgba(0.96f, 0.87f, 1f, 1f),
                    EditorI18n.t("УНИВЕРСАЛЬНЫЙ АНИМАТОР"));
            String targetName = owner == null ? EditorI18n.t("цель не найдена")
                    : (owner.name == null || owner.name.isBlank() ? owner.id : owner.name);
            draw.addText(cardX + 15f, cardY + 32f, rgba(0.72f, 0.74f, 0.82f, 1f),
                    EditorI18n.t("Цель: ") + targetName);
            int channels = animatorPropertyCount(node);
            draw.addText(cardX + 15f, cardY + 50f, rgba(0.72f, 0.74f, 0.82f, 1f),
                    EditorI18n.t("Каналы параметров: ") + channels
                            + EditorI18n.t("  ·  кости и свойства в одном .animation.json"));
            ImGui.dummy(cardW, cardH);
            ImGui.spacing();
            EditorInspectorUi.hint(EditorI18n.t(
                    "Выберите объект, нажмите ◆ у любого параметра в инспекторе — канал и ключ появятся в таймлайне."));
            ImGui.spacing();
            if (owner != null && owner.type == SceneNodeType.CANVAS) {
                EditorInspectorUi.hint(EditorI18n.t("GUI-аниматор канваса: имя кости в клипе = идентификатор виджета. position → X/Y (px), rotation.z → поворот (°), scale → множитель размера"));
            } else if (owner != null && owner.type.isUiWidget()) {
                EditorInspectorUi.hint(EditorI18n.t("Аниматор виджета «") + owner.id + EditorI18n.t("»: кости клипа двигают этот виджет (position → X/Y, rotation.z → поворот, scale → размер)"));
            }
            rst(factoryNode.animatorFile).assetCombo(EditorI18n.t("Файл анимаций"), node.animatorFile, EditorAssetIndex.animations(), animationSearch,
                    value -> {
                        node.animatorFile = value;
                        EditorAnimClipPreview.invalidate();
                    });

            // Clip list straight from the selected file.
            java.util.List<String> clips = animatorClips(node.animatorFile);
            EditorInspectorUi.fieldLabel(EditorI18n.t("Клип"));
            String shownClip = node.animatorClip == null || node.animatorClip.isEmpty()
                    ? EditorI18n.t("(не выбран)") : node.animatorClip;
            String previewModel = owner != null ? owner.model : null;
            String previewTexture = owner != null ? owner.texture : null;
            if (ImGui.beginCombo("##animator-clip", shownClip)) {
                for (int i = 0; i < clips.size(); i++) {
                    String clip = clips.get(i);
                    boolean selected = clip.equals(node.animatorClip);
                    if (animationClipSelectable(clip, selected, previewModel, previewTexture, node.animatorFile, i)
                            && !selected) {
                        boolean alreadyOpen = node.id.equals(AnimatorTimelineSession.animatorNodeId());
                        if (!alreadyOpen) SceneEditorSession.pushUndo();
                        node.animatorClip = clip;
                        SceneEditorSession.markDirty();
                        if (alreadyOpen) AnimatorTimelineSession.selectClip(clip);
                    }
                }
                if (clips.isEmpty()) ImGui.textDisabled(EditorI18n.t("В файле нет клипов — создайте в таймлайне"));
                ImGui.endCombo();
            }

            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 84f);
            ImGui.inputTextWithHint("##new-anim-file", EditorI18n.t("имя нового файла…"), newTagBuffer);
            ImGui.sameLine();
            if (EditorInspectorUi.primaryButton(EditorI18n.t("Создать"), 76f)) {
                String base = newTagBuffer.get().trim().replaceAll("[^A-Za-z0-9_\\-]+", "_");
                if (!base.isEmpty()) {
                    java.nio.file.Path file = AnimatorTimelineSession.workspaceRoot()
                            .resolve("animations").resolve(base + ".animation.json");
                    if (!java.nio.file.Files.exists(file)) {
                        try {
                            java.nio.file.Files.createDirectories(file.getParent());
                            java.nio.file.Files.writeString(file,
                                    "{\n  \"format_version\": \"1.8.0\",\n  \"animations\": {}\n}",
                                    java.nio.charset.StandardCharsets.UTF_8);
                            SceneEditorSession.pushUndo();
                            node.animatorFile = "animations/" + base + ".animation.json";
                            newTagBuffer.set("");
                            EditorAssetIndex.refresh();
                            SceneEditorSession.markDirty();
                        } catch (java.io.IOException ignored) {
                        }
                    }
                }
            }

            ImGui.spacing();
            boolean openHere = node.id.equals(AnimatorTimelineSession.animatorNodeId());
            if (openHere) {
                if (EditorInspectorUi.primaryButton(EditorI18n.t("Закрыть таймлайн"), -1f)) AnimatorTimelineSession.close();
            } else if (EditorInspectorUi.primaryButton(EditorI18n.t("Открыть в таймлайне"), -1f)) {
                AnimatorTimelineSession.open(node);
            }
            if (AnimatorTimelineSession.lastError() != null) {
                ImGui.textColored(1f, .4f, .4f, 1f, AnimatorTimelineSession.lastError());
            }
            endSection();
        }
    }

    private String animatorCountKey = "";
    private long animatorCountReadMs;
    private int animatorCountValue;

    private int animatorPropertyCount(SceneNode animator) {
        if (animator == null || animator.animatorFile == null || animator.animatorFile.isBlank()) return 0;
        if (animator.id.equals(AnimatorTimelineSession.animatorNodeId())
                && AnimatorTimelineSession.document() != null) {
            EditorAnimationDocument.Clip current =
                    AnimatorTimelineSession.document().clips.get(AnimatorTimelineSession.clipName());
            if (current == null) return 0;
            int count = 0;
            for (java.util.Map<String, EditorAnimationDocument.PropertyChannel> target
                    : current.properties.values()) count += target.size();
            return count;
        }
        String key = animator.animatorFile + "\0" + animator.animatorClip;
        long now = System.currentTimeMillis();
        if (key.equals(animatorCountKey) && now - animatorCountReadMs < 2000L) return animatorCountValue;
        try {
            EditorAnimationDocument doc = EditorAnimationDocument.load(
                    AnimatorTimelineSession.workspaceRoot().resolve(animator.animatorFile).normalize(),
                    animator.animatorFile);
            EditorAnimationDocument.Clip clip = doc.clips.get(animator.animatorClip);
            if (clip == null) return 0;
            int count = 0;
            for (java.util.Map<String, EditorAnimationDocument.PropertyChannel> target
                    : clip.properties.values()) count += target.size();
            animatorCountKey = key;
            animatorCountReadMs = now;
            animatorCountValue = count;
            return animatorCountValue;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int rgba(float r, float g, float b, float a) {
        int ri = Math.round(Math.max(0f, Math.min(1f, r)) * 255f);
        int gi = Math.round(Math.max(0f, Math.min(1f, g)) * 255f);
        int bi = Math.round(Math.max(0f, Math.min(1f, b)) * 255f);
        int ai = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        return (ai << 24) | (bi << 16) | (gi << 8) | ri;
    }

    /** Clip names inside an .animation.json workspace file (lightweight, cached per file). */
    private String animatorClipsFile;
    private java.util.List<String> animatorClipsCache = java.util.List.of();
    private long animatorClipsReadMs;

    private java.util.List<String> animatorClips(String workspacePath) {
        if (workspacePath == null || workspacePath.isBlank()) return java.util.List.of();
        long now = System.currentTimeMillis();
        if (workspacePath.equals(animatorClipsFile) && now - animatorClipsReadMs < 2000) {
            return animatorClipsCache;
        }
        animatorClipsFile = workspacePath;
        animatorClipsReadMs = now;
        try {
            java.nio.file.Path file = AnimatorTimelineSession.workspaceRoot()
                    .resolve(workspacePath.replace('\\', '/')).normalize();
            var doc = EditorAnimationDocument.load(file, workspacePath);
            animatorClipsCache = new java.util.ArrayList<>(doc.clips.keySet());
        } catch (Exception e) {
            animatorClipsCache = java.util.List.of();
        }
        return animatorClipsCache;
    }

    /** NPC/MODEL with an animation file: make sure an ANIMATOR child exists and points at the same file. */
    private static void ensureAnimatorChild(SceneNode node) {
        if (node.animation == null || node.animation.isEmpty()) return;
        for (SceneNode child : node.children) {
            if (child.type == SceneNodeType.ANIMATOR) {
                if (child.animatorFile == null || child.animatorFile.isEmpty()
                        || !child.animatorFile.equals(node.animation)) {
                    child.animatorFile = node.animation;
                }
                return;
            }
        }
        SceneNode animator = SceneNode.animator(EditorI18n.t("Аниматор"));
        animator.animatorFile = node.animation;
        SceneDocument doc = SceneEditorSession.document();
        if (doc != null) {
            try {
                SceneNodeIds.assignTree(doc, animator);
            } finally {
                SceneNodeIds.clearPending();
            }
        }
        node.children.add(animator);
        SceneEditorSession.markDirty();
    }

    private static void syncAnimatorFile(SceneNode node, String animationPath) {
        if (node == null || node.children == null) return;
        for (SceneNode child : node.children) {
            if (child.type == SceneNodeType.ANIMATOR) {
                child.animatorFile = animationPath;
                return;
            }
        }
    }

    // Virtual bone-node transform at the current animation frame.
    private static final float ANIMATION_FPS = 20f;
    private final float[] bonePosition = new float[3];
    private final float[] boneRotation = new float[3];
    private final float[] boneScale = new float[3];
    private final float[] boneFrame = new float[1];
    private final float[] geoBonePivot = new float[3];
    private final float[] geoBoneRotation = new float[3];
    private final float[] geoBoneSize = new float[3];
    private String geoBoneError;

    /** NPC/MODEL that owns the selected bone, including when the ANIMATOR node is selected. */
    private static SceneNode boneEditorOwner(SceneNode node) {
        if (node == null) return null;
        if (node.type == SceneNodeType.NPC || node.type == SceneNodeType.MODEL) return node;
        if (node.type != SceneNodeType.ANIMATOR) return null;
        SceneDocument doc = SceneEditorSession.document();
        SceneNode parent = doc == null ? null : doc.findParent(node.id);
        if (parent != null && (parent.type == SceneNodeType.NPC || parent.type == SceneNodeType.MODEL)) {
            return parent;
        }
        SceneNode target = AnimatorTimelineSession.targetNode();
        if (target != null && (target.type == SceneNodeType.NPC || target.type == SceneNodeType.MODEL)
                && node.id.equals(AnimatorTimelineSession.animatorNodeId())) {
            return target;
        }
        return null;
    }

    private void renderBoneEditor(SceneNode node) {
        String bone = AnimatorTimelineSession.selectedBone();
        if (bone == null || node.model == null || node.model.isEmpty()) return;

        boolean hasAnimation = AnimatorTimelineSession.targets(node.id)
                && AnimatorTimelineSession.clip() != null;
        boolean restMode = AnimatorTimelineSession.isRestPoseEditing() || !hasAnimation;
        EditorInspectorUi.fieldLabel(EditorI18n.t("Режим редактирования"));
        ImGui.setNextItemWidth(-1f);
        if (ImGui.beginCombo("##bone-edit-mode",
                restMode ? EditorI18n.t("Модель (.geo.json)") : EditorI18n.t("Анимация (ключи)"))) {
            if (ImGui.selectable(EditorI18n.t("Модель (.geo.json)"), restMode)) {
                AnimatorTimelineSession.setRestPoseEditing(true);
            }
            if (hasAnimation && ImGui.selectable(EditorI18n.t("Анимация (ключи)"), !restMode)) {
                AnimatorTimelineSession.setRestPoseEditing(false);
            }
            ImGui.endCombo();
        }

        if (restMode) {
            renderGeoBoneEditor(node, bone);
            if (ImGui.smallButton(EditorI18n.t("Снять выбор кости"))) AnimatorTimelineSession.selectBone(null);
            return;
        }

        if (section("bone-frame", EditorI18n.t("Кадр"))) {
            float maxFrame = Math.max(1f, AnimatorTimelineSession.clip().lengthSec * ANIMATION_FPS);
            boneFrame[0] = AnimatorTimelineSession.time() * ANIMATION_FPS;
            EditorInspectorUi.fieldLabel(EditorI18n.t("Кадр (20 FPS)"));
            if (ImGui.dragFloat("##bone-frame-value", boneFrame, 1f, 0f, maxFrame, "%.0f")) {
                AnimatorTimelineSession.setPlaying(false);
                AnimatorTimelineSession.setTime(boneFrame[0] / ANIMATION_FPS);
            }
            EditorInspectorUi.hint(String.format(Locale.ROOT, EditorI18n.t("%.3f сек · клип: %s"),
                    AnimatorTimelineSession.time(), AnimatorTimelineSession.clipName()));
            endSection();
        }

        float[] transform = AnimatorTimelineSession.sampleBoneTransform(bone);
        System.arraycopy(transform, 0, bonePosition, 0, 3);
        System.arraycopy(transform, 3, boneRotation, 0, 3);
        System.arraycopy(transform, 6, boneScale, 0, 3);

        if (section("bone-transform", "Transform")) {
            EditorInspectorUi.fieldLabel("Position");
            boolean posChanged = EditorInspectorUi.vector3(
                    "bone-animation-position", bonePosition, .1f, -100000f, 100000f,
                    AnimatorTimelineSession::pushUndo);
            ImGui.sameLine();
            boolean posKey = ImGui.smallButton(EditorI18n.t("Ключ##bone-pos-key"));

            EditorInspectorUi.fieldLabel("Rotation °");
            boolean rotChanged = EditorInspectorUi.vector3(
                    "bone-animation-rotation", boneRotation, .5f, -100000f, 100000f,
                    AnimatorTimelineSession::pushUndo);
            ImGui.sameLine();
            boolean rotKey = ImGui.smallButton(EditorI18n.t("Ключ##bone-rot-key"));

            EditorInspectorUi.fieldLabel("Scale");
            boolean scaleChanged = EditorInspectorUi.vector3(
                    "bone-animation-scale", boneScale, .01f, .001f, 10000f,
                    AnimatorTimelineSession::pushUndo);
            ImGui.sameLine();
            boolean scaleKey = ImGui.smallButton(EditorI18n.t("Ключ##bone-scale-key"));

            if (posChanged || posKey) {
                if (posKey) AnimatorTimelineSession.pushUndo();
                AnimatorTimelineSession.putBonePosition(
                        bone, bonePosition[0], bonePosition[1], bonePosition[2]);
            }
            if (rotChanged || rotKey) {
                if (rotKey) AnimatorTimelineSession.pushUndo();
                AnimatorTimelineSession.putBoneRotation(
                        bone, boneRotation[0], boneRotation[1], boneRotation[2]);
            }
            if (scaleChanged || scaleKey) {
                if (scaleKey) AnimatorTimelineSession.pushUndo();
                AnimatorTimelineSession.putBoneScale(
                        bone, boneScale[0], boneScale[1], boneScale[2]);
            }

            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, .78f, .45f, .10f, 1f);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, .92f, .56f, .16f, 1f);
            if (ImGui.button(EditorI18n.t("+ Базовые ключи Position / Rotation / Scale"))) {
                AnimatorTimelineSession.addBoneWithBaseKeys(bone);
            }
            ImGui.popStyleColor(2);
            EditorInspectorUi.hint(EditorI18n.t("W / E / R — двигать, вращать и масштабировать кость в мире"));
            endSection();
        }

        if (ImGui.smallButton(EditorI18n.t("Снять выбор кости"))) {
            AnimatorTimelineSession.selectBone(null);
        }
    }

    private void renderGeoBoneEditor(SceneNode node, String bone) {
        float[] rest = GeoBoneEditor.readBone(node.model, bone);
        if (rest == null) {
            EditorInspectorUi.hint(EditorI18n.t("Файл модели недоступен для редактирования или кость не найдена"));
            EditorInspectorUi.hint(EditorI18n.t("Встроенные ресурсы нельзя сохранять — скопируйте модель в workspace."));
            return;
        }
        System.arraycopy(rest, 0, geoBonePivot, 0, 3);
        System.arraycopy(rest, 3, geoBoneRotation, 0, 3);
        float[] size = GeoBoneEditor.readBoneSize(node.model, bone);
        if (size != null) System.arraycopy(size, 0, geoBoneSize, 0, 3);

        if (section("geo-bone-transform", "Transform .geo.json")) {
            Runnable pushGeoUndo = () -> GeoBoneEditor.pushUndo(node.model);
            EditorInspectorUi.fieldLabel(EditorI18n.t("Position / Pivot (пиксели)"));
            boolean pivotChanged = EditorInspectorUi.vector3(
                    "geo-bone-pivot", geoBonePivot, .1f, -100000f, 100000f, pushGeoUndo);
            EditorInspectorUi.fieldLabel("Rotation °");
            boolean rotationChanged = EditorInspectorUi.vector3(
                    "geo-bone-rotation", geoBoneRotation, .5f, -36000f, 36000f, pushGeoUndo);
            EditorInspectorUi.fieldLabel(EditorI18n.t("Size геометрии (пиксели)"));
            boolean sizeChanged = EditorInspectorUi.vector3(
                    "geo-bone-size", geoBoneSize, .1f, .001f, 100000f, pushGeoUndo);
            if (pivotChanged || rotationChanged) {
                geoBoneError = GeoBoneEditor.writeBone(
                        node.model, bone, geoBonePivot, geoBoneRotation);
            }
            if (sizeChanged) {
                for (int i = 0; i < 3; i++) {
                    geoBoneSize[i] = EditorGrid.snapModelPixels(geoBoneSize[i], true);
                }
                geoBoneError = GeoBoneEditor.writeBoneSize(node.model, bone, geoBoneSize);
            }
            EditorInspectorUi.hint(EditorI18n.t("W / E / R — перемещение, поворот и размер; изменения сразу сохраняются."));
            EditorInspectorUi.hint(EditorI18n.t("Перемещение двигает геометрию кости и всю её дочернюю иерархию."));
            EditorInspectorUi.hint(EditorI18n.t("Размер хранится в cubes[].size; сетка переводится из блоков в пиксели (1 блок = 16 px)."));
            if (geoBoneError != null) {
                ImGui.textColored(1f, .35f, .3f, 1f, geoBoneError);
            } else {
                EditorInspectorUi.hint(EditorI18n.t("Файл: ") + GeoBoneEditor.resolveGeoFile(node.model));
            }
            endSection();
        }
    }

    private void renderModelPreview(SceneNode node) {
        int texId = EditorModelPreviewRenderer.render(node);
        if (texId < 0) {
            EditorInspectorUi.hint(node.model == null || node.model.isEmpty()
                    ? EditorI18n.t("Модель не выбрана") : EditorI18n.t("Модель не найдена: ") + node.model);
            return;
        }
        float size = Math.max(120f, Math.min(ImGui.getContentRegionAvailX(), 320f));
        ImGui.image(texId, size, size, 0f, 1f, 1f, 0f);
        if (ImGui.isItemHovered()) {
            if (ImGui.isMouseDragging(0)) {
                EditorModelPreviewRenderer.rotate(
                        ImGui.getIO().getMouseDeltaX() * 0.6f,
                        ImGui.getIO().getMouseDeltaY() * 0.6f);
            }
            float wheel = ImGui.getIO().getMouseWheel();
            if (wheel != 0f) EditorModelPreviewRenderer.addZoom(wheel * 0.4f);
        }
        EditorInspectorUi.hint(EditorI18n.t("ЛКМ — вращение · Колесо — масштаб"));
    }

    private interface BoolSetter { void set(boolean value); }
    private interface FloatSetter { void set(float value); }
    private interface IntSetter { void set(int value); }
    private interface AssetSetter { void set(String value); }

    private static void animationClipCombo(String label, String current, List<String> clips,
                                           AssetSetter setter) {
        animationClipCombo(label, current, clips, setter, null, null);
    }

    private static void animationClipCombo(String label, String current, List<String> clips,
                                           AssetSetter setter, String script) {
        animationClipCombo(label, current, clips, setter, script, null);
    }

    private static void animationClipCombo(String label, String current, List<String> clips,
                                           AssetSetter setter, String script, SceneNode previewNode) {
        String value = current == null ? "" : current;
        String shown = value.isEmpty() ? EditorI18n.t("(не выбрана)") : value;
        String model = previewNode != null ? previewNode.model : null;
        String texture = previewNode != null ? previewNode.texture : null;
        String animFile = previewNode != null ? previewNode.animation : null;
        EditorInspectorUi.fieldLabel(label, script);
        ImGui.setNextItemWidth(-1f);
        if (!ImGui.beginCombo("##animation-clip-" + label, shown)) {
            EditorInspectorUi.scriptApi(script);
            return;
        }
        if (ImGui.selectable(EditorI18n.t("(не выбрана)"), value.isEmpty()) && !value.isEmpty()) {
            SceneEditorSession.pushUndo();
            setter.set("");
            SceneEditorSession.markDirty();
        }
        if (clips.isEmpty()) {
            ImGui.textDisabled(EditorI18n.t("В выбранном файле нет клипов"));
        } else {
            for (int i = 0; i < clips.size(); i++) {
                String clip = clips.get(i);
                boolean selected = clip.equals(value);
                if (animationClipSelectable(clip, selected, model, texture, animFile, i) && !selected) {
                    SceneEditorSession.pushUndo();
                    setter.set(clip);
                    SceneEditorSession.markDirty();
                }
            }
        }
        ImGui.endCombo();
        EditorInspectorUi.scriptApi(script);
    }

    private static void animationMultiClipCombo(String label, String current, List<String> clips,
                                                AssetSetter setter) {
        animationMultiClipCombo(label, current, clips, setter, null, null);
    }

    private static void animationMultiClipCombo(String label, String current, List<String> clips,
                                                AssetSetter setter, String script) {
        animationMultiClipCombo(label, current, clips, setter, script, null);
    }

    private static void animationMultiClipCombo(String label, String current, List<String> clips,
                                                AssetSetter setter, String script, SceneNode previewNode) {
        Set<String> selected = new LinkedHashSet<>();
        if (current != null) {
            for (String part : current.split(",")) {
                String clip = part.trim();
                if (!clip.isEmpty()) selected.add(clip);
            }
        }
        String model = previewNode != null ? previewNode.model : null;
        String texture = previewNode != null ? previewNode.texture : null;
        String animFile = previewNode != null ? previewNode.animation : null;
        EditorInspectorUi.fieldLabel(label, script);
        ImGui.setNextItemWidth(-1f);
        String shown = selected.isEmpty() ? EditorI18n.t("(не выбраны)") : String.join(", ", selected);
        if (!ImGui.beginCombo("##animation-clips-" + label, shown)) {
            EditorInspectorUi.scriptApi(script);
            return;
        }
        if (!selected.isEmpty() && ImGui.smallButton(EditorI18n.t("Очистить выбор"))) {
            SceneEditorSession.pushUndo();
            selected.clear();
            setter.set("");
            SceneEditorSession.markDirty();
        }
        if (clips.isEmpty()) {
            ImGui.textDisabled(EditorI18n.t("В выбранном файле нет клипов"));
        } else {
            float preview = 40f;
            float gap = 6f;
            for (int i = 0; i < clips.size(); i++) {
                String clip = clips.get(i);
                ImGui.pushID(i);
                ImBoolean enabled = new ImBoolean(selected.contains(clip));
                if (ImGui.checkbox("##cb", enabled)) {
                    SceneEditorSession.pushUndo();
                    if (enabled.get()) selected.add(clip); else selected.remove(clip);
                    setter.set(String.join(", ", selected));
                    SceneEditorSession.markDirty();
                }
                ImGui.sameLine(0f, gap);
                ImGui.textUnformatted(clip);
                ImGui.sameLine(0f, gap);
                int tid = EditorAnimClipPreview.renderFreeze(model, texture, animFile, clip);
                if (tid >= 0) {
                    ImGui.image(tid, preview, preview, 0f, 1f, 1f, 0f);
                } else {
                    ImGui.dummy(preview, preview);
                }
                ImGui.popID();
            }
        }
        ImGui.endCombo();
        EditorInspectorUi.scriptApi(script);
    }

    /** Selectable clip row: name on the left, freeze-frame preview on the right. */
    private static boolean animationClipSelectable(String clip, boolean selected,
                                                   String modelPath, String texturePath, String animationPath,
                                                   int index) {
        float preview = 40f;
        float gap = 6f;
        float avail = ImGui.getContentRegionAvailX();
        float textW = Math.max(48f, avail - preview - gap);
        // Numeric ID: Blockbench meta names contain [, ], ##, --- and break ImGui string IDs.
        ImGui.pushID(index);
        boolean clicked = ImGui.selectable(clip.replace("#", "*") + "##c", selected, 0, textW, preview);
        ImGui.sameLine(0f, gap);
        int tid = EditorAnimClipPreview.renderFreeze(modelPath, texturePath, animationPath, clip);
        if (tid >= 0) {
            ImGui.image(tid, preview, preview, 0f, 1f, 1f, 0f);
        } else {
            ImGui.dummy(preview, preview);
        }
        ImGui.popID();
        return clicked;
    }

    private void renderCanvas(SceneNode node) {
        if (node.canvasWorldSpace) {
            renderWorldCanvas(node);
            return;
        }
        if (section("canvas-object", EditorI18n.t("Канвас"))) {
            canvasRenderModeField(node);
            canvasUnitToggles(node);
            rst(factoryNode.uiCanvasW).dragInt(node.uiPercentW ? EditorI18n.t("Ширина (% экрана)") : EditorI18n.t("Ширина (px)"),
                    node.uiCanvasW, 1, 1, 4096, value -> node.uiCanvasW = value,
                    EditorI18n.t("Ширина панели: пиксели или % от экрана."),
                    "_node.uiCanvasW = 300\n_node.uiW = 300\n_node.percentW = true");
            rst(factoryNode.uiCanvasH).dragInt(node.uiPercentH ? EditorI18n.t("Высота (% экрана)") : EditorI18n.t("Высота (px)"),
                    node.uiCanvasH, 1, 1, 4096, value -> node.uiCanvasH = value,
                    EditorI18n.t("Высота панели: пиксели или % от экрана."),
                    "_node.uiCanvasH = 180\n_node.uiH = 180\n_node.percentH = true");
            rst(0).dragInt(node.uiPercentX ? EditorI18n.t("Позиция X (%, 0 = центр)") : EditorI18n.t("Позиция X (px, 0 = центр)"),
                    (int) Math.round(node.transform.px), 1, -4096, 4096,
                    value -> node.transform.px = value,
                    EditorI18n.t("Смещение панели по X от центра экрана. Отрицательное — отступ справа."),
                    "_node.x = 0\n_node.transform.x = 0\n_node.percentX = true");
            rst(0).dragInt(node.uiPercentY ? EditorI18n.t("Позиция Y (%, 0 = центр)") : EditorI18n.t("Позиция Y (px, 0 = центр)"),
                    (int) Math.round(node.transform.py), 1, -4096, 4096,
                    value -> node.transform.py = value,
                    EditorI18n.t("Смещение панели по Y от центра экрана. Отрицательное — отступ снизу."),
                    "_node.y = 0\n_node.transform.y = 0\n_node.percentY = true");
            EditorInspectorUi.bindReset(!java.util.Objects.equals(node.uiColor == null ? "" : node.uiColor, factoryNode.uiColor == null ? "" : factoryNode.uiColor), () -> applyEdit(() -> node.uiColor = factoryNode.uiColor));
            UiStyleEditor.colorField(EditorI18n.t("Фон"), "canvas-bg", node.uiColor, value -> node.uiColor = value,
                    EditorI18n.t("Цвет фона панели (#AARRGGBB). AA — прозрачность."),
                    "_node.uiColor = \"#C0101010\"\n_node.color = \"#C0101010\"");
            EditorInspectorUi.hint(EditorI18n.t("Отрицательные значения — отступ от правого/нижнего края"));
            endSection();
        }

        renderUiStyle(node, "canvas");

        if (section("canvas-show", EditorI18n.t("Показ"))) {
            rst(factoryNode.canvasOverlay).checkbox(EditorI18n.t("Оверлей (HUD, поверх игры)"), node.canvasOverlay, value -> node.canvasOverlay = value,
                    EditorI18n.t("HUD поверх игры (true) или полноэкранное окно (false)."),
                    "_node.canvasOverlay = true\n_node.overlay = true");
            rst(factoryNode.canvasLayer).dragInt(EditorI18n.t("Слой (z-порядок)"), node.canvasLayer, 1, -100, 100, value -> node.canvasLayer = value,
                    EditorI18n.t("Чем больше слой, тем выше канвас. У оверлея < 0 — под хотбаром/чатом Minecraft."),
                    "_node.canvasLayer = 0\n_node.layer = 0");
            if (node.canvasOverlay) {
                EditorInspectorUi.hint(EditorI18n.t("Канвасы рисуются по возрастанию слоя; слой < 0 — под интерфейсом Minecraft (хотбар, чат и т.п.)"));
            }
            rst(factoryNode.canvasAutoShow).checkbox(EditorI18n.t("Показывать при загрузке сцены"), node.canvasAutoShow, value -> node.canvasAutoShow = value,
                    EditorI18n.t("Автопоказ игрокам при загрузке сцены."),
                    "_node.canvasAutoShow = true\n_node.autoShow = true");
            if (!node.canvasOverlay) {
                rst(factoryNode.canvasCanClose).checkbox(EditorI18n.t("Можно закрыть по ESC"), node.canvasCanClose, value -> node.canvasCanClose = value,
                        EditorI18n.t("Разрешить закрытие окна клавишей Esc."),
                        "_node.canvasCanClose = true\n_node.canClose = true");
                rst(factoryNode.canvasDim).checkbox(EditorI18n.t("Затемнять фон игры"), node.canvasDim, value -> node.canvasDim = value,
                        EditorI18n.t("Затемнять мир позади окна (только полноэкранный UI)."),
                        "_node.canvasDim = true\n_node.dim = true");
            }
            EditorInspectorUi.hint(EditorI18n.t("Из скриптов: uiOpen(player, \"") + node.id + "\") · uiClose(player, ...)");
            endSection();
        }
    }

    /** Screen space ⇄ world space, the one switch that turns a canvas into a 3D panel. */
    private void canvasRenderModeField(SceneNode node) {
        EditorInspectorUi.fieldLabel(EditorI18n.t("Режим отрисовки"));
        String current = node.canvasWorldSpace ? EditorI18n.t("В мире (3D-панель)") : EditorI18n.t("На экране (GUI)");
        if (ImGui.beginCombo("##canvas-render-mode", current)) {
            if (ImGui.selectable(EditorI18n.t("На экране (GUI)"), !node.canvasWorldSpace) && node.canvasWorldSpace) {
                applyEdit(() -> node.canvasWorldSpace = false);
            }
            if (ImGui.selectable(EditorI18n.t("В мире (3D-панель)"), node.canvasWorldSpace) && !node.canvasWorldSpace) {
                applyEdit(() -> {
                    node.canvasWorldSpace = true;
                    node.canvasAutoShow = false;
                    // A world panel must not depend on each player's window size.
                    node.uiPercentW = false;
                    node.uiPercentH = false;
                    node.uiPercentX = false;
                    node.uiPercentY = false;
                    if (node.uiCanvasW < 16) node.uiCanvasW = 320;
                    if (node.uiCanvasH < 16) node.uiCanvasH = 180;
                });
            }
            ImGui.endCombo();
        }
    }

    /** World-space canvas: pixel resolution of the panel + how it sits and is drawn in the world. */
    private void renderWorldCanvas(SceneNode node) {
        if (section("canvas-object", EditorI18n.t("3D-канвас"))) {
            canvasRenderModeField(node);
            EditorInspectorUi.hint(EditorI18n.t(
                    "Виджеты кладутся прямо в эту ноду. Позиция/поворот — в блоке «Трансформ», "
                    + "двигать можно прямо в 3D-виде."));
            rst(320).dragInt(EditorI18n.t("Разрешение панели, ширина (px)"),
                    node.uiCanvasW, 1, 16, 4096, value -> node.uiCanvasW = value,
                    EditorI18n.t("Внутренняя сетка канваса в пикселях — в них считаются координаты виджетов."),
                    "_node.uiCanvasW = 320");
            rst(180).dragInt(EditorI18n.t("Разрешение панели, высота (px)"),
                    node.uiCanvasH, 1, 16, 4096, value -> node.uiCanvasH = value,
                    EditorI18n.t("Внутренняя сетка канваса в пикселях."),
                    "_node.uiCanvasH = 180");
            EditorInspectorUi.bindReset(!java.util.Objects.equals(node.uiColor == null ? "" : node.uiColor, factoryNode.uiColor == null ? "" : factoryNode.uiColor), () -> applyEdit(() -> node.uiColor = factoryNode.uiColor));
            UiStyleEditor.colorField(EditorI18n.t("Фон"), "canvas-bg", node.uiColor, value -> node.uiColor = value,
                    EditorI18n.t("Цвет фона панели (#AARRGGBB). AA — прозрачность."),
                    "_node.uiColor = \"#C0101010\"\n_node.color = \"#C0101010\"");
            endSection();
        }

        renderUiStyle(node, "canvas");

        if (section("canvas-world", EditorI18n.t("В мире"))) {
            EditorInspectorUi.fieldLabel(EditorI18n.t("Ориентация"));
            String orientation = node.canvasWorldOrientation == null || node.canvasWorldOrientation.isBlank()
                    ? "billboard" : node.canvasWorldOrientation;
            if (ImGui.beginCombo("##canvas-world-orientation", worldOrientationLabel(orientation))) {
                for (String value : new String[]{"billboard", "yaw", "fixed"}) {
                    if (ImGui.selectable(worldOrientationLabel(value), value.equals(orientation))) {
                        applyEdit(() -> node.canvasWorldOrientation = value);
                    }
                }
                ImGui.endCombo();
            }
            rst(2f).dragFloat(EditorI18n.t("Ширина (блоки)"), node.canvasWorldWidth, 0.05f, 0.05f, 128f,
                    value -> node.canvasWorldWidth = value,
                    EditorI18n.t("Физический размер панели в мире. Умножается на масштаб X ноды."),
                    "_node.canvasWorldWidth = 2");
            rst(0f).dragFloat(EditorI18n.t("Высота (блоки, 0 = по пропорциям)"), node.canvasWorldHeight, 0.05f, 0f, 128f,
                    value -> node.canvasWorldHeight = value,
                    EditorI18n.t("0 — высота берётся из пропорций пиксельного разрешения панели."),
                    "_node.canvasWorldHeight = 0");
            rst(2f).dragFloat(EditorI18n.t("Чёткость (1-4)"), node.canvasWorldResolution, 0.1f, 0.5f, 4f,
                    value -> node.canvasWorldResolution = value,
                    EditorI18n.t("Суперсэмплинг текстуры панели. 2 — вдвое чётче, дороже по видеопамяти."),
                    "_node.canvasWorldResolution = 2");
            rst(48f).dragFloat(EditorI18n.t("Дальность отрисовки (блоки)"), node.canvasWorldMaxDistance, 1f, 1f, 128f,
                    value -> node.canvasWorldMaxDistance = value,
                    EditorI18n.t("Дальше этой дистанции панель не рисуется вообще."),
                    "_node.canvasWorldMaxDistance = 48");
            rst(5).dragInt(EditorI18n.t("Обновление контента (тиков)"), node.canvasWorldRefresh, 1, 1, 200,
                    value -> node.canvasWorldRefresh = value,
                    EditorI18n.t("Как часто виджеты пересобираются в текстуру панели. node.emit() — обновить сейчас."),
                    "_node.canvasWorldRefresh = 5");
            endSection();
        }
    }

    private static String worldOrientationLabel(String orientation) {
        return switch (orientation == null ? "" : orientation) {
            case "fixed" -> EditorI18n.t("Фиксированная (поворот ноды)");
            case "yaw" -> EditorI18n.t("К камере только по Y (табличка)");
            default -> EditorI18n.t("К камере (billboard)");
        };
    }

    private void renderUiWidget(SceneNode node) {
        switch (node.type) {
            case UI_TEXT -> {
                if (section("uiw-text", EditorI18n.t("Текст"))) {
                uiLayoutFields(node, false);
                richTextField(EditorI18n.t("Текст"), node,
                        EditorI18n.t("Строка текста на канвасе. Выделите фрагмент и нажмите эффект на панели."),
                        uiPropApi(node, "_node.uiText = \"Привет, [glow]герой[/glow]!\"\n_node.text = \"Привет\"",
                                "text", "\"Привет, [glow]герой[/glow]!\""));
                EditorInspectorUi.bindReset(!java.util.Objects.equals(node.uiColor == null ? "" : node.uiColor, factoryNode.uiColor == null ? "" : factoryNode.uiColor), () -> applyEdit(() -> node.uiColor = factoryNode.uiColor));
                UiStyleEditor.colorField(EditorI18n.t("Цвет"), "uiw-text-color", node.uiColor, value -> node.uiColor = value,
                        EditorI18n.t("Цвет текста (#AARRGGBB)."),
                        uiPropApi(node, "_node.uiColor = \"#FFEAEAEA\"\n_node.color = \"#FFEAEAEA\"", "color", "\"#FFEAEAEA\""));
                rst(factoryNode.uiTextScale).dragFloat(EditorI18n.t("Масштаб текста"), node.uiTextScale, 0.02f, 0.1f, 8f, value -> node.uiTextScale = value,
                        EditorI18n.t("1 — обычный размер, 0.5 — вдвое меньше."),
                        uiPropApi(node, "_node.uiTextScale = 1\n_node.textScale = 1", "scale", "1"));
                rst(factoryNode.uiAutoWrap).checkbox(EditorI18n.t("Автоперенос (по ширине)"), node.uiAutoWrap, value -> node.uiAutoWrap = value,
                        EditorI18n.t("Переносить текст по ширине виджета, если wrap не задан."),
                        "_node.uiAutoWrap = true\n_node.autoWrap = true");
                wrapModeCombo(node);
                if (!node.uiAutoWrap) {
                    rst(factoryNode.uiWrap).dragInt(EditorI18n.t("Перенос (px, 0 = выкл)"), node.uiWrap, 1, 0, 4096, value -> node.uiWrap = value,
                            EditorI18n.t("Ширина переноса в пикселях. 0 — не переносить."),
                            uiPropApi(node, "_node.uiWrap = 80\n_node.wrap = 80", "wrap", "80"));
                } else {
                    EditorInspectorUi.hint(EditorI18n.t("Текст переносится, когда не помещается в ширину виджета"));
                }
                uiTextOffsetFields(node);
                alignCombo(node);
                uiTooltipField(node);
                    endSection();
                }
                if (section("uiw-text-effects", EditorI18n.t("Эффекты"))) {
                    textEffectFields(node);
                    endSection();
                }
            }
            case UI_BUTTON -> {
                if (section("uiw-button", EditorI18n.t("Кнопка"))) {
                uiLayoutFields(node, true);
                richTextField(EditorI18n.t("Надпись"), node,
                        EditorI18n.t("Надпись на кнопке. Выделите фрагмент и нажмите эффект на панели."),
                        uiPropApi(node, "_node.uiText = \"OK\"\n_node.label = \"OK\"", "label", "\"OK\""));
                EditorInspectorUi.bindReset(!java.util.Objects.equals(node.uiColor == null ? "" : node.uiColor, factoryNode.uiColor == null ? "" : factoryNode.uiColor), () -> applyEdit(() -> node.uiColor = factoryNode.uiColor));
                UiStyleEditor.colorField(EditorI18n.t("Цвет"), "uiw-btn-color", node.uiColor, value -> node.uiColor = value,
                        EditorI18n.t("Цвет кнопки (#AARRGGBB)."),
                        uiPropApi(node, "_node.uiColor = \"#55336688\"", "color", "\"#55336688\""));
                EditorInspectorUi.bindReset(!java.util.Objects.equals(node.uiHoverColor == null ? "" : node.uiHoverColor, factoryNode.uiHoverColor == null ? "" : factoryNode.uiHoverColor), () -> applyEdit(() -> node.uiHoverColor = factoryNode.uiHoverColor));
                UiStyleEditor.colorField(EditorI18n.t("Цвет при наведении"), "uiw-btn-hover", node.uiHoverColor, value -> node.uiHoverColor = value,
                        EditorI18n.t("Цвет при наведении курсора (#AARRGGBB)."),
                        uiPropApi(node, "_node.uiHoverColor = \"#66447799\"\n_node.hoverColor = \"#66447799\"", "hover", "\"#66447799\""));
                rst(factoryNode.uiTextScale).dragFloat(EditorI18n.t("Масштаб надписи"), node.uiTextScale, 0.02f, 0.1f, 8f, value -> node.uiTextScale = value,
                        EditorI18n.t("Масштаб надписи кнопки (1 — обычный)."),
                        uiPropApi(node, "_node.uiTextScale = 1", "labelScale", "1"));
                rst(factoryNode.uiAutoWrap).checkbox(EditorI18n.t("Автоперенос надписи"), node.uiAutoWrap, value -> node.uiAutoWrap = value,
                        EditorI18n.t("Переносить надпись по ширине кнопки."),
                        "_node.uiAutoWrap = true");
                wrapModeCombo(node);
                if (!node.uiAutoWrap) {
                    rst(factoryNode.uiWrap).dragInt(EditorI18n.t("Перенос надписи (px, 0 = выкл)"), node.uiWrap, 1, 0, 4096, value -> node.uiWrap = value,
                            EditorI18n.t("Ширина переноса надписи. 0 — не переносить."),
                            uiPropApi(node, "_node.uiWrap = 0", "labelWrap", "0"));
                }
                rst(factoryNode.uiTexture).assetCombo(EditorI18n.t("Текстура (вместо цвета)"), node.uiTexture, EditorAssetIndex.textures(), textureSearch,
                        value -> node.uiTexture = value,
                        "_node.uiTexture = \"textures/gui/button.png\"");
                if (node.uiTexture != null && !node.uiTexture.isBlank()) nineSliceFields(node);
                alignCombo(node, "center");
                uiTextOffsetFields(node);
                uiTooltipField(node);
                EditorInspectorUi.hint(EditorI18n.t("Клик в скриптах: on uiClick { if (_uiWidget == \"") + node.id + "\") ... }");
                    endSection();
                }
                if (section("uiw-button-effects", EditorI18n.t("Эффекты"))) {
                    textEffectFields(node);
                    endSection();
                }
                renderUiStyle(node, "uiw");
            }
            case UI_IMAGE -> {
                if (section("uiw-image", EditorI18n.t("Картинка"))) {
                    uiLayoutFields(node, true);
                    rst(factoryNode.uiTexture).assetCombo(EditorI18n.t("Текстура"), node.uiTexture, EditorAssetIndex.textures(), textureSearch,
                            value -> node.uiTexture = value,
                            "_node.uiTexture = \"textures/gui/icon.png\"");
                    if (ImGui.smallButton(EditorI18n.t("Обновить список ассетов"))) EditorAssetIndex.refresh();
                    nineSliceFields(node);
                    cropShapeCombo(node);
                    imageFillFields(node);
                    imageSrcFields(node);
                    uiTooltipField(node);
                    endSection();
                }
            }
            case UI_SVG -> {
                if (section("uiw-svg", EditorI18n.t("SVG"))) {
                    uiLayoutFields(node, true);
                    rst(factoryNode.uiTexture).assetCombo(EditorI18n.t("Файл SVG"), node.uiTexture, EditorAssetIndex.svgs(), textureSearch,
                            value -> node.uiTexture = value,
                            "_node.source = \"textures/gui/icon.svg\"\n_node.svg = \"textures/gui/icon.svg\"");
                    if (ImGui.smallButton(EditorI18n.t("Обновить список ассетов"))) EditorAssetIndex.refresh();
                    EditorInspectorUi.bindReset(!java.util.Objects.equals(node.uiColor == null ? "" : node.uiColor, factoryNode.uiColor == null ? "" : factoryNode.uiColor), () -> applyEdit(() -> node.uiColor = factoryNode.uiColor));
                    UiStyleEditor.colorField(EditorI18n.t("Цвет (currentColor)"), "uiw-svg-color", node.uiColor, value -> node.uiColor = value,
                            EditorI18n.t("Подменяет currentColor в SVG (#AARRGGBB). Lucide-иконки красятся целиком."),
                            uiPropApi(node, "_node.color = \"#FFFFFFFF\"\nuiUpdate(player, \"" + node.id + "\", \"color\", \"#FF4455\")",
                                    "color", "\"#FFFFFFFF\""));
                    boolean strokeOn = node.uiSvgStroke != null && !node.uiSvgStroke.isBlank();
                    if (EditorInspectorUi.checkbox(EditorI18n.t("Своя обводка"), strokeOn)) {
                        SceneEditorSession.pushUndo();
                        node.uiSvgStroke = strokeOn ? "" : "#FF000000";
                        SceneEditorSession.markDirty();
                    }
                    if (node.uiSvgStroke != null && !node.uiSvgStroke.isBlank()) {
                        EditorInspectorUi.bindReset(!java.util.Objects.equals(node.uiSvgStroke, factoryNode.uiSvgStroke),
                                () -> applyEdit(() -> node.uiSvgStroke = factoryNode.uiSvgStroke));
                        UiStyleEditor.colorField(EditorI18n.t("Обводка SVG"), "uiw-svg-stroke", node.uiSvgStroke, value -> node.uiSvgStroke = value,
                                EditorI18n.t("Цвет обводки (#AARRGGBB). Пусто — как в файле / currentColor."),
                                uiPropApi(node, "_node.stroke = \"#FF000000\"", "stroke", "\"#FF000000\""));
                    }
                    rst(factoryNode.uiSvgStrokeWidth).dragFloat(EditorI18n.t("Толщина обводки (0 = из файла)"), node.uiSvgStrokeWidth, 0.1f, 0f, 16f,
                            value -> node.uiSvgStrokeWidth = Math.max(0f, value),
                            EditorI18n.t("Переопределить stroke-width. 0 — значение из SVG."),
                            uiPropApi(node, "_node.strokeWidth = 2", "strokeWidth", "2"));
                    rst(factoryNode.uiSvgRecolor).checkbox(EditorI18n.t("Перекрасить заливки"), node.uiSvgRecolor,
                            value -> node.uiSvgRecolor = value,
                            EditorI18n.t("Заменить все заливки цветом виджета, не только currentColor."),
                            uiPropApi(node, "_node.recolor = true", "recolor", "true"));
                    cropShapeCombo(node);
                    imageFillFields(node);
                    uiTooltipField(node);
                    EditorInspectorUi.hint(EditorI18n.t("Вектор SVG из textures/**.svg. Цвет подменяет currentColor. uiUpdate: source, color, stroke, strokeWidth, recolor"));
                    endSection();
                }
            }
            case UI_VIDEO -> {
                if (section("uiw-video", EditorI18n.t("Видео"))) {
                    uiLayoutFields(node, true);
                    rst(factoryNode.uiVideo).assetCombo(EditorI18n.t("MP4 / H.264"), node.uiVideo, EditorAssetIndex.videos(), videoSearch,
                            value -> node.uiVideo = value,
                            "_node.video = \"videos/intro.mp4\"");
                    if (ImGui.smallButton(EditorI18n.t("Обновить список ассетов"))) EditorAssetIndex.refresh();
                    ImGui.textDisabled(EditorI18n.t("Управление"));
                    ImGui.sameLine();
                    float videoBtn = 16f;
                    if (EditorIcons.iconButton("circle-play", "video-play-" + node.id, videoBtn,
                            EditorI18n.t("Воспроизвести"))) {
                        SceneEditorSession.pushUndo();
                        node.uiVideoEnabled = true;
                        node.uiVideoPlaying = true;
                        SceneEditorSession.markDirty();
                    }
                    ImGui.sameLine();
                    if (EditorIcons.iconButton("circle-pause", "video-pause-" + node.id, videoBtn,
                            EditorI18n.t("Пауза"))) {
                        SceneEditorSession.pushUndo();
                        node.uiVideoPlaying = false;
                        SceneEditorSession.markDirty();
                    }
                    ImGui.sameLine();
                    if (EditorIcons.iconButton("circle-stop", "video-stop-" + node.id, videoBtn,
                            EditorI18n.t("Остановить"))) {
                        SceneEditorSession.pushUndo();
                        node.uiVideoEnabled = false;
                        node.uiVideoPlaying = false;
                        SceneEditorSession.markDirty();
                    }
                    rst(factoryNode.uiVideoEnabled).checkbox(EditorI18n.t("Плеер включён"), node.uiVideoEnabled,
                            value -> node.uiVideoEnabled = value,
                            "_node.playerEnabled = true\nuiVideoEnable(player, _node, true)");
                    rst(factoryNode.uiVideoPlaying).checkbox(EditorI18n.t("Воспроизводить"), node.uiVideoPlaying,
                            value -> node.uiVideoPlaying = value,
                            "_node.playing = true\nuiVideoPlay(player, _node)");
                    rst(factoryNode.uiVideoLoop).checkbox(EditorI18n.t("Зациклить"), node.uiVideoLoop,
                            value -> node.uiVideoLoop = value,
                            "_node.videoLoop = true");
                    uiTooltipField(node);
                    EditorInspectorUi.hint(EditorI18n.t(
                            "MP4/H.264 без звука · uiVideoPlay / uiVideoPause / uiVideoStop / uiVideoRestart / uiVideoTime / uiVideoEnded"));
                    endSection();
                }
            }
            case UI_RECT -> {
                if (section("uiw-rect", EditorI18n.t("Панель"))) {
                    uiLayoutFields(node, true);
                    childAlignCombo(node);
                    EditorInspectorUi.bindReset(!java.util.Objects.equals(node.uiColor == null ? "" : node.uiColor, factoryNode.uiColor == null ? "" : factoryNode.uiColor), () -> applyEdit(() -> node.uiColor = factoryNode.uiColor));
                    UiStyleEditor.colorField(EditorI18n.t("Цвет"), "uiw-rect-color", node.uiColor, value -> node.uiColor = value,
                            EditorI18n.t("Цвет прямоугольника / фона (#AARRGGBB)."),
                            uiPropApi(node, "_node.uiColor = \"#80FFFFFF\"", "color", "\"#80FFFFFF\""));
                    cropShapeCombo(node);
                    imageFillFields(node);
                    uiTooltipField(node);
                    EditorInspectorUi.hint(EditorI18n.t("Дочерние виджеты позиционируются относительно панели"));
                    endSection();
                }
                renderUiStyle(node, "uiw");
            }
            case UI_INPUT -> {
                if (section("uiw-input", EditorI18n.t("Поле ввода"))) {
                uiLayoutFields(node, true);
                rst(factoryNode.uiPlaceholder).stringField(EditorI18n.t("Подсказка (placeholder)"), node.uiPlaceholder, uiPlaceholderBuffer,
                        value -> node.uiPlaceholder = value,
                        EditorI18n.t("Серая подсказка, пока поле пустое."),
                        uiPropApi(node, "_node.uiPlaceholder = \"…\"\n_node.placeholder = \"…\"", "placeholder", "\"…\""));
                richTextField(EditorI18n.t("Начальный текст"), node,
                        EditorI18n.t("Начальный текст в поле ввода."),
                        uiPropApi(node, "_node.uiText = \"\"\n_node.text = \"\"", "text", "\"\""));
                EditorInspectorUi.bindReset(!java.util.Objects.equals(node.uiColor == null ? "" : node.uiColor, factoryNode.uiColor == null ? "" : factoryNode.uiColor), () -> applyEdit(() -> node.uiColor = factoryNode.uiColor));
                UiStyleEditor.colorField(EditorI18n.t("Цвет текста"), "uiw-input-color", node.uiColor, value -> node.uiColor = value,
                        EditorI18n.t("Цвет текста поля (#AARRGGBB)."),
                        uiPropApi(node, "_node.uiColor = \"#FFFFFFFF\"", "color", "\"#FFFFFFFF\""));
                rst(factoryNode.uiMaxChars).dragInt(EditorI18n.t("Макс. символов"), node.uiMaxChars, 1, 1, 1024, value -> node.uiMaxChars = value,
                        EditorI18n.t("Максимум символов (по умолчанию 32)."),
                        "_node.uiMaxChars = 32\n_node.maxChars = 32");
                rst(factoryNode.uiTextScale).dragFloat(EditorI18n.t("Масштаб текста"), node.uiTextScale, 0.02f, 0.1f, 8f, value -> node.uiTextScale = value,
                        EditorI18n.t("Масштаб текста в поле."),
                        "_node.uiTextScale = 1");
                uiTooltipField(node);
                EditorInspectorUi.hint(EditorI18n.t("Ввод в скриптах: on uiInput / await uiInput · uiGetInput / uiSetInput"));
                    endSection();
                }
                if (section("uiw-input-effects", EditorI18n.t("Эффекты"))) {
                    textEffectFields(node);
                    endSection();
                }
                renderUiStyle(node, "uiw");
            }
            case UI_GRID -> {
                if (section("uiw-grid", EditorI18n.t("Сетка (раскладка)"))) {
                    uiLayoutFields(node, false);
                    gridModeCombo(node);
                    gridAlignCombo(node);
                    gridFlowCombo(node);
                    rst(factoryNode.gridHugContent).checkbox(EditorI18n.t("Размер от детей (hug)"), node.gridHugContent, value -> node.gridHugContent = value,
                            EditorI18n.t("Ячейки/слоты берут размер у дочерних виджетов (для ряда и колонки обычно включено)."),
                            "_node.gridHugContent = true\n_node.hug = true");
                    String mode = UiGridLayout.normalizeMode(node.gridMode);
                    if ("grid".equals(mode)) {
                        rst(factoryNode.gridColumns).dragInt(EditorI18n.t("Колонок"), node.gridColumns, 1, 1, 64, value -> node.gridColumns = value,
                                EditorI18n.t("Число колонок сетки."),
                                "_node.gridColumns = 2\n_node.columns = 2");
                    }
                    if (!node.gridHugContent || "grid".equals(mode)) {
                        rst(factoryNode.gridCellW).dragInt(EditorI18n.t("Ширина ячейки (px)"), node.gridCellW, 1, 1, 4096, value -> node.gridCellW = value,
                                EditorI18n.t("Ширина одной ячейки в пикселях."),
                                "_node.gridCellW = 100\n_node.cellW = 100");
                        rst(factoryNode.gridCellH).dragInt(EditorI18n.t("Высота ячейки (px)"), node.gridCellH, 1, 1, 4096, value -> node.gridCellH = value,
                                EditorI18n.t("Высота одной ячейки в пикселях."),
                                "_node.gridCellH = 24\n_node.cellH = 24");
                    }
                    rst(factoryNode.gridGapX).dragInt(EditorI18n.t("Отступ по X (px)"), node.gridGapX, 1, 0, 512, value -> node.gridGapX = value,
                            EditorI18n.t("Горизонтальный зазор между ячейками / элементами ряда."),
                            "_node.gridGapX = 4\n_node.gapX = 4");
                    rst(factoryNode.gridGapY).dragInt(EditorI18n.t("Отступ по Y (px)"), node.gridGapY, 1, 0, 512, value -> node.gridGapY = value,
                            EditorI18n.t("Вертикальный зазор между ячейками / элементами колонки."),
                            "_node.gridGapY = 4\n_node.gapY = 4");
                    rst(factoryNode.gridStretchCells).checkbox(EditorI18n.t("Растягивать детей до ячейки"), node.gridStretchCells, value -> node.gridStretchCells = value,
                            EditorI18n.t("Принудительно подогнать размер детей под ячейку/слот."),
                            "_node.gridStretchCells = true\n_node.stretchCells = true");
                    EditorInspectorUi.hint(EditorI18n.t("Сетка — ячейки; ряд/колонка — поток с учётом размеров детей"));
                    endSection();
                }
            }
            case UI_SCROLL -> {
                if (section("uiw-scroll", EditorI18n.t("Скролл"))) {
                    uiLayoutFields(node, true);
                    childAlignCombo(node);
                    rst(factoryNode.uiContentH).dragInt(EditorI18n.t("Высота контента (px)"), node.uiContentH, 1, 1, 8192, value -> node.uiContentH = value,
                            EditorI18n.t("Полная высота содержимого. Если больше высоты скролла — появится прокрутка."),
                            uiPropApi(node, "_node.uiContentH = 200\n_node.contentH = 200", "contentH", "200"));
                    EditorInspectorUi.bindReset(!java.util.Objects.equals(node.uiColor == null ? "" : node.uiColor, factoryNode.uiColor == null ? "" : factoryNode.uiColor), () -> applyEdit(() -> node.uiColor = factoryNode.uiColor));
                    UiStyleEditor.colorField(EditorI18n.t("Фон"), "uiw-scroll-color", node.uiColor, value -> node.uiColor = value,
                            EditorI18n.t("Цвет фона области скролла."),
                            uiPropApi(node, "_node.uiColor = \"#40000000\"", "color", "\"#40000000\""));
                    rst(factoryNode.uiScrollbar).checkbox(EditorI18n.t("Показывать полосу прокрутки"), node.uiScrollbar, value -> node.uiScrollbar = value,
                            EditorI18n.t("Показывать полосу прокрутки."),
                            "_node.uiScrollbar = true\n_node.scrollbar = true");
                    uiTooltipField(node);
                    EditorInspectorUi.hint(EditorI18n.t("Дочерние виджеты живут внутри области и прокручиваются · uiScrollSet"));
                    endSection();
                }
                renderUiStyle(node, "uiw");
            }
            case UI_DIVIDER -> {
                if (section("uiw-divider", EditorI18n.t("Разделитель"))) {
                    uiLayoutFields(node, true);
                    EditorInspectorUi.bindReset(!java.util.Objects.equals(node.uiColor == null ? "" : node.uiColor, factoryNode.uiColor == null ? "" : factoryNode.uiColor), () -> applyEdit(() -> node.uiColor = factoryNode.uiColor));
                    UiStyleEditor.colorField(EditorI18n.t("Цвет"), "uiw-div-color", node.uiColor, value -> node.uiColor = value,
                            EditorI18n.t("Цвет линии-разделителя."),
                            uiPropApi(node, "_node.uiColor = \"#44FFFFFF\"", "color", "\"#44FFFFFF\""));
                    endSection();
                }
            }
            case UI_ITEM, UI_BLOCK -> {
                // UI_BLOCK is a legacy alias: blocks are items too, one picker covers both.
                if (section("uiw-item", EditorI18n.t("Предмет / Блок"))) {
                    uiLayoutFields(node, false);
                    EditorItemPicker.combo(EditorI18n.t("Предмет / Блок"), node.uiItem, EditorItemIcons.items(), true, true,
                            value -> node.uiItem = value);
                    EditorInspectorUi.scriptApi(EditorI18n.t("ID предмета/блока, например minecraft:diamond или minecraft:diamond_sword{Enchantments:[…]}."),
                            uiPropApi(node, "_node.uiItem = \"minecraft:diamond\"\n_node.item = \"minecraft:diamond\"",
                                    "item", "\"minecraft:diamond\""));
                    rst(factoryNode.uiW).dragInt(node.uiPercentW ? EditorI18n.t("Размер (%)") : EditorI18n.t("Размер (px)"), node.uiW, 1, 1, 512,
                            value -> { node.uiW = value; node.uiH = value; },
                            EditorI18n.t("Размер иконки (квадрат) в игровых пикселях."),
                            uiPropApi(node, "_node.uiW = 16\n_node.uiH = 16", "size", "16"));
                    uiTooltipField(node);
                    endSection();
                }
            }
            case UI_SLOT -> {
                if (section("uiw-slot", EditorI18n.t("Слот"))) {
                    uiLayoutFields(node, false);
                    uiTooltipField(node);
                    EditorInspectorUi.hint(EditorI18n.t("Слот контейнера 18x18 px. Идентификатор ноды — id слота:\n") +
                            "uiSlotItem(player, \"" + node.id + EditorI18n.t("\"), uiSetSlot, uiSlotHasItem и т.д.\n") +
                            EditorI18n.t("Канвас со слотами открывается как контейнер (не оверлей)"));
                    endSection();
                }
            }
            case UI_INVENTORY -> {
                if (section("uiw-inv", EditorI18n.t("Инвентарь игрока"))) {
                    uiLayoutFields(node, false);
                    EditorInspectorUi.hint(EditorI18n.t("Сетка 9x3 + хотбар (162x76 px). Позиция — левый верхний угол.\n") +
                            EditorI18n.t("Слоты привязаны к настоящему инвентарю игрока"));
                    endSection();
                }
            }
            case UI_CROP -> {
                if (section("uiw-crop", EditorI18n.t("Обрезка (crop)"))) {
                    uiLayoutFields(node, true);
                    childAlignCombo(node);
                    rst(factoryNode.uiCropFeather).dragFloat(EditorI18n.t("Размыть края (px)"), node.uiCropFeather, 0.5f, 0f, 256f,
                            value -> node.uiCropFeather = Math.max(0f, value),
                            EditorI18n.t("Радиус мягкого затухания по краям. 0 — жёсткая обрезка."),
                            uiPropApi(node, "_node.feather = 24\n_node.uiCropFeather = 24", "feather", "24"));
                    if (node.uiCropFeather > 0.25f) {
                        rst(factoryNode.uiCropFeatherSmooth).checkbox(EditorI18n.t("Плавная кривая затухания"), node.uiCropFeatherSmooth,
                                value -> node.uiCropFeatherSmooth = value,
                                EditorI18n.t("smoothstep вместо линейного падения прозрачности."),
                                uiPropApi(node, "_node.featherSmooth = true", "featherSmooth", "true"));
                        rst(factoryNode.uiCropFeatherL).dragFloat(EditorI18n.t("Слева (0–1)"), node.uiCropFeatherL, 0.01f, 0f, 1f,
                                value -> node.uiCropFeatherL = Math.max(0f, Math.min(1f, value)),
                                EditorI18n.t("Доля радиуса на левом крае. 0 — край жёсткий, 1 — полный радиус."),
                                uiPropApi(node, "_node.featherL = 1", "featherL", "1"));
                        rst(factoryNode.uiCropFeatherT).dragFloat(EditorI18n.t("Сверху (0–1)"), node.uiCropFeatherT, 0.01f, 0f, 1f,
                                value -> node.uiCropFeatherT = Math.max(0f, Math.min(1f, value)),
                                EditorI18n.t("Доля радиуса на верхнем крае. 0 — край жёсткий, 1 — полный радиус."),
                                uiPropApi(node, "_node.featherT = 1", "featherT", "1"));
                        rst(factoryNode.uiCropFeatherR).dragFloat(EditorI18n.t("Справа (0–1)"), node.uiCropFeatherR, 0.01f, 0f, 1f,
                                value -> node.uiCropFeatherR = Math.max(0f, Math.min(1f, value)),
                                EditorI18n.t("Доля радиуса на правом крае. 0 — край жёсткий, 1 — полный радиус."),
                                uiPropApi(node, "_node.featherR = 1", "featherR", "1"));
                        rst(factoryNode.uiCropFeatherB).dragFloat(EditorI18n.t("Снизу (0–1)"), node.uiCropFeatherB, 0.01f, 0f, 1f,
                                value -> node.uiCropFeatherB = Math.max(0f, Math.min(1f, value)),
                                EditorI18n.t("Доля радиуса на нижнем крае. 0 — край жёсткий, 1 — полный радиус."),
                                uiPropApi(node, "_node.featherB = 1", "featherB", "1"));
                    }
                    EditorInspectorUi.hint(EditorI18n.t("Дочерние виджеты позиционируются относительно области; всё за её границами обрезается"));
                    endSection();
                }
            }
            case UI_GROUP -> {
                if (section("uiw-group", EditorI18n.t("Пустая нода (группа)"))) {
                    uiLayoutFields(node, true);
                    childAlignCombo(node);
                    rst(factoryNode.uiAlpha).dragFloat(EditorI18n.t("Прозрачность детей (0-1)"), node.uiAlpha, 0.01f, 0f, 1f,
                            value -> node.uiAlpha = value,
                            EditorI18n.t("Прозрачность всего содержимого группы: 1 — непрозрачно, 0 — невидимо."),
                            "_node.uiAlpha = 1\n_node.alpha = 1");
                    EditorInspectorUi.hint(EditorI18n.t("Невидимый контейнер: смещает и группирует дочерние виджеты"));
                    endSection();
                }
            }
            case UI_DRAG -> {
                if (section("uiw-drag", EditorI18n.t("Перетаскивание (drag)"))) {
                    uiLayoutFields(node, true);
                    childAlignCombo(node);
                    rst(factoryNode.uiAlpha).dragFloat(EditorI18n.t("Прозрачность детей (0-1)"), node.uiAlpha, 0.01f, 0f, 1f,
                            value -> node.uiAlpha = value,
                            EditorI18n.t("Прозрачность всего содержимого: 1 — непрозрачно, 0 — невидимо."),
                            "_node.uiAlpha = 1\n_node.alpha = 1");
                    rst(factoryNode.uiDragEnabled).checkbox(EditorI18n.t("Можно тащить"), node.uiDragEnabled,
                            value -> node.uiDragEnabled = value,
                            EditorI18n.t("Если выключено, контейнер не подхватывается мышью."),
                            "_node.dragEnabled = true");
                    dragAxisCombo(node);
                    rst(factoryNode.uiDragGroup).stringField(EditorI18n.t("Группа"), node.uiDragGroup, uiDragGroupBuffer,
                            value -> node.uiDragGroup = value,
                            EditorI18n.t("Совпадает с группой зоны drop. Пусто — любая зона."),
                            uiPropApi(node, "_node.group = \"items\"\n_node.dragGroup = \"items\"", "group", "\"items\""));
                    rst(factoryNode.uiDragPayload).stringField(EditorI18n.t("Payload"), node.uiDragPayload, uiDragPayloadBuffer,
                            value -> node.uiDragPayload = value,
                            EditorI18n.t("Строка, которая придёт в скрипт как _eventPayload при drop."),
                            uiPropApi(node, "_node.payload = \"sword\"", "payload", "\"sword\""));
                    rst(factoryNode.uiDragConstrain).checkbox(EditorI18n.t("Не выходить за родителя"), node.uiDragConstrain,
                            value -> node.uiDragConstrain = value,
                            EditorI18n.t("Держать контейнер внутри родителя во время перетаскивания."),
                            "_node.constrain = true");
                    rst(factoryNode.uiDragRevert).checkbox(EditorI18n.t("Вернуть, если не зона"), node.uiDragRevert,
                            value -> node.uiDragRevert = value,
                            EditorI18n.t("Если отпустить мимо подходящей drop-зоны — вернуть на место."),
                            "_node.revert = true");
                    rst(factoryNode.uiDragThreshold).dragInt(EditorI18n.t("Порог (px)"), node.uiDragThreshold, 1, 0, 64,
                            value -> node.uiDragThreshold = Math.max(0, value),
                            EditorI18n.t("Сколько пикселей сдвинуть мышь, прежде чем начнётся drag. Меньше — клик по кнопке внутри."),
                            "_node.threshold = 4");
                    EditorInspectorUi.hint(EditorI18n.t("Дети едут вместе с нодой. on uiDragStart / on uiDrop / on uiDragEnd"));
                    endSection();
                }
            }
            case UI_DROPTARGET -> {
                if (section("uiw-drop", EditorI18n.t("Зона сброса (drop)"))) {
                    uiLayoutFields(node, true);
                    childAlignCombo(node);
                    rst(factoryNode.uiAlpha).dragFloat(EditorI18n.t("Прозрачность детей (0-1)"), node.uiAlpha, 0.01f, 0f, 1f,
                            value -> node.uiAlpha = value,
                            EditorI18n.t("Прозрачность всего содержимого: 1 — непрозрачно, 0 — невидимо."),
                            "_node.uiAlpha = 1\n_node.alpha = 1");
                    rst(factoryNode.uiDropEnabled).checkbox(EditorI18n.t("Принимает drop"), node.uiDropEnabled,
                            value -> node.uiDropEnabled = value,
                            EditorI18n.t("Если выключено, зона игнорирует перетаскивание."),
                            "_node.dropEnabled = true");
                    rst(factoryNode.uiDropGroup).stringField(EditorI18n.t("Группа"), node.uiDropGroup, uiDragGroupBuffer,
                            value -> node.uiDropGroup = value,
                            EditorI18n.t("Принимает drag с той же группой. Пусто — любой drag."),
                            uiPropApi(node, "_node.group = \"items\"\n_node.dropGroup = \"items\"", "group", "\"items\""));
                    EditorInspectorUi.hint(EditorI18n.t("При наведении совместимого drag подсвечивается. _eventWidget — эта зона, _eventDrag — что бросили"));
                    endSection();
                }
            }
            case UI_ENTITY -> {
                if (section("uiw-entity", EditorI18n.t("Рендер"))) {
                    uiLayoutFields(node, true);
                    entityModeCombo(node);
                    switch (node.uiEntityMode == null ? "player" : node.uiEntityMode) {
                        case "npc" -> {
                            npcNodeCombo(node);
                            rst(factoryNode.uiEntityAlways).checkbox(EditorI18n.t("Рендерить без спавна / мёртвым"), node.uiEntityAlways,
                                    value -> node.uiEntityAlways = value,
                                    EditorI18n.t("Рисовать модель НПС даже если он не заспавнен или мёртв."),
                                    "_node.uiEntityAlways = true");
                            EditorInspectorUi.hint(node.uiEntityAlways
                                    ? EditorI18n.t("Модель НПС рисуется всегда (даже если не заспавнен или мёртв)")
                                    : EditorI18n.t("Рендер только если живой NPC сейчас в мире"));
                        }
                        case "mob" -> {
                            rst(factoryNode.uiEntityRef).assetCombo(EditorI18n.t("Моб"), node.uiEntityRef, EditorItemIcons.mobs(), mobSearch,
                                    value -> node.uiEntityRef = value,
                                    "_node.uiEntityRef = \"minecraft:zombie\"");
                            EditorInspectorUi.hint(EditorI18n.t("Ванильный или модовый моб по registry id"));
                        }
                        default -> EditorInspectorUi.hint(EditorI18n.t("Рендерится игрок, который смотрит на GUI"));
                    }
                    rst(factoryNode.uiEntityScale).dragFloat(EditorI18n.t("Масштаб"), node.uiEntityScale, 0.02f, 0.05f, 16f, value -> node.uiEntityScale = value,
                            EditorI18n.t("Масштаб 3D-модели в GUI."),
                            "_node.uiEntityScale = 1");
                    rst(factoryNode.uiEntityAnchorX).dragFloat(EditorI18n.t("Смещение X (якорь)"), node.uiEntityAnchorX, 0.01f, 0f, 1f,
                            value -> node.uiEntityAnchorX = value,
                            EditorI18n.t("Точка привязки по X (0..1): 0 — лево, 0.5 — центр, 1 — право."),
                            "_node.uiEntityAnchorX = 0.5");
                    rst(factoryNode.uiEntityAnchorY).dragFloat(EditorI18n.t("Смещение Y (якорь)"), node.uiEntityAnchorY, 0.01f, -2f, 3f,
                            value -> node.uiEntityAnchorY = value,
                            EditorI18n.t("Точка привязки по Y: 0 — верх, 0.5 — середина, 1 — низ."),
                            "_node.uiEntityAnchorY = 0.5");
                    EditorInspectorUi.hint(EditorI18n.t("Якорь модели в ячейке: X 0=лево…1=право, Y 0=верх…1=низ\n") +
                            EditorI18n.t("(Y < 0 или > 1 — за пределами ячейки). Как anchor в create ui"));
                    if (node.uiEntityAnchorY < 0f) {
                        rst(factoryNode.uiEntityFeetCrop).dragFloat("Feet crop (legacy)", node.uiEntityFeetCrop, 0.01f, 0f, 1f,
                                value -> node.uiEntityFeetCrop = value,
                                EditorI18n.t("Обрезка модели снизу (0..1), legacy при anchorY < 0."),
                                "_node.uiEntityFeetCrop = 0.38");
                    }
                    uiTooltipField(node);
                    endSection();
                }
            }
            default -> {}
        }
    }


    private void uiTextOffsetFields(SceneNode node) {
        rst(factoryNode.uiTextOffsetX).dragFloat(EditorI18n.t("Смещение текста X (px)"), node.uiTextOffsetX, 0.5f, -512f, 512f,
                value -> node.uiTextOffsetX = value,
                EditorI18n.t("Сдвиг текста внутри виджета по горизонтали (пиксели)."),
                uiPropApi(node, "_node.uiTextOffsetX = 2\n_node.textOffsetX = 2", "textOffsetX", "2"));
        rst(factoryNode.uiTextOffsetY).dragFloat(EditorI18n.t("Смещение текста Y (px)"), node.uiTextOffsetY, 0.5f, -512f, 512f,
                value -> node.uiTextOffsetY = value,
                EditorI18n.t("Сдвиг текста внутри виджета по вертикали (пиксели)."),
                uiPropApi(node, "_node.uiTextOffsetY = 1\n_node.textOffsetY = 1", "textOffsetY", "1"));
    }

    private void uiTooltipField(SceneNode node) {
        rst(factoryNode.uiTooltip).stringField(EditorI18n.t("Подсказка (tooltip)"), node.uiTooltip, uiTooltipBuffer, value -> node.uiTooltip = value,
                EditorI18n.t("Всплывающая подсказка в игре при наведении курсора на виджет."),
                uiPropApi(node, "_node.uiTooltip = \"Подсказка\"\n_node.tooltip = \"Подсказка\"",
                        "tooltip", "\"Подсказка\""));
    }

    /** Shared style block: rounding / gradient editor / border / shadow (same props as create ui). */
    private void renderUiStyle(SceneNode node, String idPrefix) {
        if (!section(idPrefix + "-style", EditorI18n.t("Стиль"))) return;
        rst(factoryNode.uiRadius).dragFloat(EditorI18n.t("Скругление углов (px)"), node.uiRadius, 0.1f, 0f, 256f, value -> node.uiRadius = value,
                EditorI18n.t("Радиус скругления углов в пикселях. 0 — прямые углы."),
                uiPropApi(node, "_node.uiRadius = 4\n_node.radius = 4", "radius", "4"));
        UiStyleEditor.gradientEditor(node, idPrefix);
        EditorInspectorUi.scriptApi(EditorI18n.t("Градиент от start к end (#AARRGGBB). Заменяет сплошной цвет."),
                "_node.uiGradient = \"#FF2B4C7E\"\n_node.uiGradientStart = \"#FFFFFFFF\"\n_node.gradient = \"#FF2B4C7E\"");
        rst(factoryNode.uiBorder).dragFloat(EditorI18n.t("Внутренняя рамка (px)"), node.uiBorder, 0.1f, 0f, 64f, value -> node.uiBorder = value,
                EditorI18n.t("Толщина внутренней рамки (внутрь от края). 0 — без рамки."),
                uiPropApi(node, "_node.uiBorder = 1\n_node.border = 1", "border", "1"));
        if (node.uiBorder > 0f) {
            boolean borderGradient = node.uiBorderGradient != null && !node.uiBorderGradient.isBlank();
            if (!borderGradient) {
                EditorInspectorUi.bindReset(!java.util.Objects.equals(node.uiBorderColor == null ? "" : node.uiBorderColor, factoryNode.uiBorderColor == null ? "" : factoryNode.uiBorderColor), () -> applyEdit(() -> node.uiBorderColor = factoryNode.uiBorderColor));
                UiStyleEditor.colorField(EditorI18n.t("Цвет рамки"), idPrefix + "-border", node.uiBorderColor, value -> node.uiBorderColor = value,
                        EditorI18n.t("Цвет внутренней рамки (#AARRGGBB)."),
                        uiPropApi(node, "_node.uiBorderColor = \"#66FFFFFF\"\n_node.borderColor = \"#66FFFFFF\"",
                                "borderColor", "\"#66FFFFFF\""));
            }
            UiStyleEditor.borderGradientEditor(node, idPrefix);
        }
        rst(factoryNode.uiOuterBorder).dragFloat(EditorI18n.t("Наружная обводка (px)"), node.uiOuterBorder, 0.1f, 0f, 64f, value -> node.uiOuterBorder = value,
                EditorI18n.t("Толщина наружной обводки (снаружи от края). 0 — без обводки."),
                uiPropApi(node, "_node.uiOuterBorder = 2\n_node.outerBorder = 2", "outerBorder", "2"));
        if (node.uiOuterBorder > 0f) {
            boolean outerGradient = node.uiOuterBorderGradient != null && !node.uiOuterBorderGradient.isBlank();
            if (!outerGradient) {
                EditorInspectorUi.bindReset(!java.util.Objects.equals(node.uiOuterBorderColor == null ? "" : node.uiOuterBorderColor, factoryNode.uiOuterBorderColor == null ? "" : factoryNode.uiOuterBorderColor), () -> applyEdit(() -> node.uiOuterBorderColor = factoryNode.uiOuterBorderColor));
                UiStyleEditor.colorField(EditorI18n.t("Цвет обводки"), idPrefix + "-outer-border", node.uiOuterBorderColor,
                        value -> node.uiOuterBorderColor = value,
                        EditorI18n.t("Цвет наружной обводки (#AARRGGBB)."),
                        uiPropApi(node, "_node.uiOuterBorderColor = \"#66FFFFFF\"\n_node.outerBorderColor = \"#66FFFFFF\"",
                                "outerBorderColor", "\"#66FFFFFF\""));
            }
            UiStyleEditor.outerBorderGradientEditor(node, idPrefix);
        }
        rst(factoryNode.uiShadow).dragFloat(EditorI18n.t("Тень (px)"), node.uiShadow, 0.1f, 0f, 128f, value -> node.uiShadow = value,
                EditorI18n.t("Размер мягкой тени под виджетом. 0 — без тени."),
                uiPropApi(node, "_node.uiShadow = 4\n_node.shadow = 4", "shadow", "4"));
        endSection();
    }

    /** Units + position (+ size) + rotation, shown at the top of the widget's main section. */
    private void uiLayoutFields(SceneNode node, boolean withSize) {
        unitToggles(node);
        uiPositionFields(node);
        if (withSize) uiSizeFields(node);
        dragFloat(EditorI18n.t("Поворот (°)"), (float) node.transform.rz, 0.5f, -360f, 360f,
                value -> node.transform.rz = value,
                EditorI18n.t("Поворот виджета в градусах. Можно анимировать через uiAnimate."),
                uiPropApi(node, "_node.rotationZ = 0\n_node.transform.rotationZ = 0", "rotation", "0"));
        rst(factoryNode.canvasLayer).dragInt(EditorI18n.t("Слой (z-порядок)"), node.canvasLayer, 1, -100, 100, value -> node.canvasLayer = value,
                EditorI18n.t("Чем больше слой, тем выше элемент. При равном слое — порядок в иерархии."),
                uiPropApi(node, "_node.layer = 0\n_node.canvasLayer = 0", "layer", "0"));
    }

    private void uiSizeFields(SceneNode node) {
        rst(factoryNode.uiW).dragInt(node.uiPercentW ? EditorI18n.t("Ширина (%)") : EditorI18n.t("Ширина (px)"), node.uiW, 1, 0, 4096,
                value -> node.uiW = value,
                EditorI18n.t("Ширина в игровых пикселях или % от родителя."),
                uiPropApi(node, "_node.uiW = 100\n_node.w = 100", "w", "100"));
        rst(factoryNode.uiH).dragInt(node.uiPercentH ? EditorI18n.t("Высота (%)") : EditorI18n.t("Высота (px)"), node.uiH, 1, 0, 4096,
                value -> node.uiH = value,
                EditorI18n.t("Высота в игровых пикселях или % от родителя."),
                uiPropApi(node, "_node.uiH = 22\n_node.h = 22", "h", "22"));
    }

    private void uiPositionFields(SceneNode node) {
        dragInt(node.uiPercentX ? EditorI18n.t("Позиция X (%)") : EditorI18n.t("Позиция X (px)"),
                (int) Math.round(node.transform.px), 1, -4096, 4096, value -> node.transform.px = value,
                EditorI18n.t("Позиция по X от левого края родителя. Можно % от ширины родителя."),
                uiPropApi(node, "_node.x = 10\n_node.transform.x = 10", "x", "10"));
        dragInt(node.uiPercentY ? EditorI18n.t("Позиция Y (%)") : EditorI18n.t("Позиция Y (px)"),
                (int) Math.round(node.transform.py), 1, -4096, 4096, value -> node.transform.py = value,
                EditorI18n.t("Позиция по Y от верхнего края родителя. Можно % от высоты родителя."),
                uiPropApi(node, "_node.y = 10\n_node.transform.y = 10", "y", "10"));
    }

    /**
     * px/% unit switches. Toggling converts the stored value so the widget
     * stays exactly where it is (px -> % of parent and back).
     */
    private void unitToggles(SceneNode node) {
        int[] parent = parentSizePx(node);
        EditorInspectorUi.fieldLabel(EditorI18n.t("Единицы (px / % от родителя)"),
                EditorI18n.t("Переключатель: пиксели или проценты от родителя для X/Y/W/H."),
                "_node.percentX / .percentY / .percentW / .percentH = true");
        unitButton("X", node.uiPercentX, toPercent -> {
            node.transform.px = convertUnit(node.transform.px, parent[0], toPercent);
            node.uiPercentX = toPercent;
        });
        ImGui.sameLine();
        unitButton("Y", node.uiPercentY, toPercent -> {
            node.transform.py = convertUnit(node.transform.py, parent[1], toPercent);
            node.uiPercentY = toPercent;
        });
        ImGui.sameLine();
        unitButton("W", node.uiPercentW, toPercent -> {
            node.uiW = (int) Math.round(convertUnit(node.uiW, parent[0], toPercent));
            node.uiPercentW = toPercent;
        });
        ImGui.sameLine();
        unitButton("H", node.uiPercentH, toPercent -> {
            node.uiH = (int) Math.round(convertUnit(node.uiH, parent[1], toPercent));
            node.uiPercentH = toPercent;
        });
    }

    /** px/% switches for the canvas itself: percent is relative to the game screen. */
    private void canvasUnitToggles(SceneNode node) {
        int[] screen = screenSizePx();
        EditorInspectorUi.fieldLabel(EditorI18n.t("Единицы (px / % от экрана)"),
                EditorI18n.t("Переключатель: пиксели или проценты от экрана для X/Y/W/H канваса."),
                "_node.percentX / .percentY / .percentW / .percentH = true");
        unitButton("X", node.uiPercentX, toPercent -> {
            node.transform.px = convertUnit(node.transform.px, screen[0], toPercent);
            node.uiPercentX = toPercent;
        });
        ImGui.sameLine();
        unitButton("Y", node.uiPercentY, toPercent -> {
            node.transform.py = convertUnit(node.transform.py, screen[1], toPercent);
            node.uiPercentY = toPercent;
        });
        ImGui.sameLine();
        unitButton("W", node.uiPercentW, toPercent -> {
            node.uiCanvasW = (int) Math.round(convertUnit(node.uiCanvasW, screen[0], toPercent));
            node.uiPercentW = toPercent;
        });
        ImGui.sameLine();
        unitButton("H", node.uiPercentH, toPercent -> {
            node.uiCanvasH = (int) Math.round(convertUnit(node.uiCanvasH, screen[1], toPercent));
            node.uiPercentH = toPercent;
        });
    }

    private static int[] screenSizePx() {
        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        return new int[]{Math.max(1, window.getGuiScaledWidth()), Math.max(1, window.getGuiScaledHeight())};
    }

    private static double convertUnit(double value, int parentSize, boolean toPercent) {
        if (parentSize <= 0) return value;
        return toPercent ? value / parentSize * 100.0 : value / 100.0 * parentSize;
    }

    /** Pixel size of the widget's parent container (canvas / rect / scroll / grid cell). */
    private static int[] parentSizePx(SceneNode node) {
        SceneDocument doc = SceneEditorSession.document();
        if (doc == null) return new int[]{300, 180};
        SceneNode parent = doc.findParent(node.id);
        return nodePixelSize(parent, doc);
    }

    private static int[] nodePixelSize(SceneNode node, SceneDocument doc) {
        if (node == null || !node.type.isUi()) return new int[]{300, 180};
        if (node.type == SceneNodeType.CANVAS) {
            int[] screen = screenSizePx();
            int w = node.uiPercentW ? Math.round(node.uiCanvasW / 100f * screen[0]) : node.uiCanvasW;
            int h = node.uiPercentH ? Math.round(node.uiCanvasH / 100f * screen[1]) : node.uiCanvasH;
            return new int[]{Math.max(4, w), Math.max(4, h)};
        }
        if (node.type == SceneNodeType.UI_GRID) {
            return new int[]{Math.max(1, node.gridCellW), Math.max(1, node.gridCellH)};
        }
        int[] parent = nodePixelSize(doc.findParent(node.id), doc);
        int w = node.uiPercentW ? Math.round(node.uiW / 100f * parent[0]) : node.uiW;
        int h = node.uiPercentH ? Math.round(node.uiH / 100f * parent[1]) : node.uiH;
        return new int[]{Math.max(1, w), Math.max(1, h)};
    }

    private void unitButton(String axis, boolean percent, BoolSetter setter) {
        String label = axis + ": " + (percent ? "%" : "px") + "##unit-" + axis;
        if (percent) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.25f, 0.42f, 0.65f, 1f);
        if (ImGui.smallButton(label)) {
            SceneEditorSession.pushUndo();
            setter.set(!percent);
            SceneEditorSession.markDirty();
        }
        if (percent) ImGui.popStyleColor();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(percent
                    ? EditorI18n.t("Проценты от родителя (клик — пересчитать в пиксели)")
                    : EditorI18n.t("Пиксели (клик — пересчитать в проценты)"));
        }
    }

    /** 9-slice controls + visual preview of the texture with the border guides. */
    private void nineSliceFields(SceneNode node) {
        rst(factoryNode.uiSliceBorders).dragInt(EditorI18n.t("9-slice: рамка (px текстуры, 0 = выкл)"), node.uiSliceBorders, 1, 0, 128,
                value -> node.uiSliceBorders = value,
                EditorI18n.t("Ширина неизменяемых краёв текстуры. Углы фиксированы, края тайлятся. 0 — растянуть всю текстуру."),
                "_node.uiSliceBorders = 4\n_node.sliceBorders = 4");
        if (node.uiSliceBorders <= 0) return;
        rst(factoryNode.uiSliceScale).dragFloat(EditorI18n.t("9-slice: масштаб рамки"), node.uiSliceScale, 0.02f, 0.1f, 8f,
                value -> node.uiSliceScale = value,
                EditorI18n.t("Множитель толщины края на экране. 0.42 — тонкая рамка, 1 — 1:1 с borders."),
                "_node.uiSliceScale = 1\n_node.sliceScale = 1");
        var tex = node.uiTexture == null ? null : EditorTexturePreview.get(node.uiTexture);
        if (tex == null) return;
        float[] size = EditorTexturePreview.fit(tex, 170f, 170f);
        float px = ImGui.getCursorScreenPosX();
        float py = ImGui.getCursorScreenPosY();
        ImGui.image(tex.glId(), size[0], size[1]);
        // Guide lines: border offsets scaled from texture pixels to preview pixels.
        float bx = node.uiSliceBorders * size[0] / Math.max(1, tex.width());
        float by = node.uiSliceBorders * size[1] / Math.max(1, tex.height());
        var dl = ImGui.getWindowDrawList();
        int guide = ImGui.getColorU32(0.35f, 0.85f, 1f, 0.9f);
        dl.addLine(px + bx, py, px + bx, py + size[1], guide, 1f);
        dl.addLine(px + size[0] - bx, py, px + size[0] - bx, py + size[1], guide, 1f);
        dl.addLine(px, py + by, px + size[0], py + by, guide, 1f);
        dl.addLine(px, py + size[1] - by, px + size[0], py + size[1] - by, guide, 1f);
        EditorInspectorUi.hint(EditorI18n.t("Углы не растягиваются, стороны тянутся, центр заполняет остальное"));
    }

    private void imageSrcFields(SceneNode node) {
        var tex = node.uiTexture == null ? null : EditorTexturePreview.get(node.uiTexture);
        if (tex != null && tex.glId() > 0) {
            EditorInspectorUi.fieldLabel(EditorI18n.t("Область на картинке"),
                    EditorI18n.t("Потяни рамку на превью — так задаётся кроп. Углы меняют размер, середина двигает."),
                    "_node.src = [0, 0, 32, 32]");
            float[] size = EditorTexturePreview.fit(tex, 220f, 220f);
            float px = ImGui.getCursorScreenPosX();
            float py = ImGui.getCursorScreenPosY();
            ImGui.invisibleButton("##crop-preview", size[0], size[1]);
            ImGui.getWindowDrawList().addImage(tex.glId(), px, py, px + size[0], py + size[1]);
            handleCropPreview(node, tex, px, py, size[0], size[1]);
            drawCropOverlay(node, tex, px, py, size[0], size[1]);
            if (ImGui.smallButton(EditorI18n.t("По контуру картинки"))) {
                int[] bounds = EditorTexturePreview.opaqueBounds(node.uiTexture, 8);
                if (bounds != null) {
                    SceneEditorSession.pushUndo();
                    node.uiSrcU = bounds[0];
                    node.uiSrcV = bounds[1];
                    node.uiSrcW = bounds[2];
                    node.uiSrcH = bounds[3];
                    SceneEditorSession.markDirty();
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(EditorI18n.t("Поставить кроп по непрозрачным пикселям текстуры."));
            }
            ImGui.sameLine();
            if (ImGui.smallButton(EditorI18n.t("Вся текстура"))) {
                SceneEditorSession.pushUndo();
                node.uiSrcU = 0;
                node.uiSrcV = 0;
                node.uiSrcW = 0;
                node.uiSrcH = 0;
                SceneEditorSession.markDirty();
            }
        }
        rst(factoryNode.uiSrcU).dragInt(EditorI18n.t("Обрезка U (px)"), node.uiSrcU, 1, 0, 4096, value -> node.uiSrcU = value,
                EditorI18n.t("Левый край области в пикселях текстуры."),
                "_node.srcU = 0\n_node.src = [0, 0, 32, 32]");
        rst(factoryNode.uiSrcV).dragInt(EditorI18n.t("Обрезка V (px)"), node.uiSrcV, 1, 0, 4096, value -> node.uiSrcV = value,
                EditorI18n.t("Верхний край области в пикселях текстуры."),
                "_node.srcV = 0");
        rst(factoryNode.uiSrcW).dragInt(EditorI18n.t("Обрезка ширина (0 = вся)"), node.uiSrcW, 1, 0, 4096, value -> node.uiSrcW = value,
                EditorI18n.t("Ширина области. 0 — рисовать всю текстуру."),
                "_node.srcW = 32\n_node.src = [0, 0, 32, 32]");
        rst(factoryNode.uiSrcH).dragInt(EditorI18n.t("Обрезка высота (px)"), node.uiSrcH, 1, 0, 4096, value -> node.uiSrcH = value,
                EditorI18n.t("Высота области в пикселях текстуры."),
                "_node.srcH = 32");
        EditorInspectorUi.hint(EditorI18n.t("Форма кропа рисуется на превью. Круг — эллипс внутри рамки."));
    }

    private void cropShapeCombo(SceneNode node) {
        EditorInspectorUi.fieldLabel(EditorI18n.t("Форма кропа"),
                EditorI18n.t("rect — прямоугольник, circle — круг/эллипс по картинке."),
                "_node.cropShape = \"circle\"");
        String shape = UiImageFill.normalizeShape(node.uiCropShape);
        String shown = UiImageFill.SHAPE_CIRCLE.equals(shape)
                ? EditorI18n.t("Круг / эллипс")
                : EditorI18n.t("Прямоугольник");
        if (ImGui.beginCombo("##uiw-crop-shape", shown)) {
            if (ImGui.selectable(EditorI18n.t("Прямоугольник"), UiImageFill.SHAPE_RECT.equals(shape))) {
                SceneEditorSession.pushUndo();
                node.uiCropShape = "rect";
                if (UiImageFill.isRadial(node.uiFill)) node.uiFill = UiImageFill.LTR;
                SceneEditorSession.markDirty();
            }
            if (ImGui.selectable(EditorI18n.t("Круг / эллипс"), UiImageFill.SHAPE_CIRCLE.equals(shape))) {
                SceneEditorSession.pushUndo();
                node.uiCropShape = "circle";
                if (UiImageFill.isLinear(node.uiFill)) node.uiFill = UiImageFill.CW;
                SceneEditorSession.markDirty();
            }
            ImGui.endCombo();
        }
        EditorInspectorUi.scriptApi(
                EditorI18n.t("rect — прямоугольник, circle — круг/эллипс по картинке."),
                "_node.cropShape = \"circle\"");
    }

    private void handleCropPreview(SceneNode node, EditorTexturePreview.Tex tex,
                                   float px, float py, float pw, float ph) {
        if (pw <= 1f || ph <= 1f || tex.width() <= 0 || tex.height() <= 0) return;
        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        boolean hovered = mx >= px && mx <= px + pw && my >= py && my <= py + ph;
        float[] crop = cropPreviewRect(node, tex, px, py, pw, ph);
        float hs = 6f;

        if (!cropDragActive && hovered && ImGui.isMouseClicked(0)) {
            SceneEditorSession.pushUndo();
            cropDragActive = true;
            cropDragU0 = (mx - px) / pw * tex.width();
            cropDragV0 = (my - py) / ph * tex.height();
            cropOrigU = node.uiSrcU;
            cropOrigV = node.uiSrcV;
            cropOrigW = node.uiSrcW;
            cropOrigH = node.uiSrcH;
            cropResizeCorner = hitCropHandle(crop, mx, my, hs);
            if (cropResizeCorner >= 0) {
                cropDragKind = 2;
            } else if (node.uiSrcW > 0 && mx >= crop[0] && mx <= crop[2] && my >= crop[1] && my <= crop[3]) {
                cropDragKind = 1;
            } else {
                cropDragKind = 0;
            }
        }
        if (cropDragActive && ImGui.isMouseDown(0)) {
            float u = clamp((mx - px) / pw * tex.width(), 0f, tex.width());
            float v = clamp((my - py) / ph * tex.height(), 0f, tex.height());
            if (cropDragKind == 0) {
                if (Math.abs(u - cropDragU0) < 2f && Math.abs(v - cropDragV0) < 2f) {
                    node.uiSrcU = cropOrigU;
                    node.uiSrcV = cropOrigV;
                    node.uiSrcW = cropOrigW;
                    node.uiSrcH = cropOrigH;
                } else {
                    int u0 = Math.round(Math.min(cropDragU0, u));
                    int v0 = Math.round(Math.min(cropDragV0, v));
                    int u1 = Math.round(Math.max(cropDragU0, u));
                    int v1 = Math.round(Math.max(cropDragV0, v));
                    node.uiSrcU = u0;
                    node.uiSrcV = v0;
                    node.uiSrcW = Math.max(1, u1 - u0);
                    node.uiSrcH = Math.max(1, v1 - v0);
                }
            } else if (cropDragKind == 1) {
                int du = Math.round(u - cropDragU0);
                int dv = Math.round(v - cropDragV0);
                int w = Math.max(1, cropOrigW);
                int h = Math.max(1, cropOrigH);
                node.uiSrcU = clampInt(cropOrigU + du, 0, Math.max(0, tex.width() - w));
                node.uiSrcV = clampInt(cropOrigV + dv, 0, Math.max(0, tex.height() - h));
                node.uiSrcW = w;
                node.uiSrcH = h;
            } else {
                float left = cropOrigU;
                float top = cropOrigV;
                float right = cropOrigU + Math.max(1, cropOrigW);
                float bottom = cropOrigV + Math.max(1, cropOrigH);
                if (cropResizeCorner == 0 || cropResizeCorner == 2) left = u;
                if (cropResizeCorner == 1 || cropResizeCorner == 3) right = u;
                if (cropResizeCorner == 0 || cropResizeCorner == 1) top = v;
                if (cropResizeCorner == 2 || cropResizeCorner == 3) bottom = v;
                int u0 = Math.round(Math.max(0f, Math.min(left, right)));
                int v0 = Math.round(Math.max(0f, Math.min(top, bottom)));
                int u1 = Math.round(Math.min(tex.width(), Math.max(left, right)));
                int v1 = Math.round(Math.min(tex.height(), Math.max(top, bottom)));
                node.uiSrcU = u0;
                node.uiSrcV = v0;
                node.uiSrcW = Math.max(1, u1 - u0);
                node.uiSrcH = Math.max(1, v1 - v0);
            }
            SceneEditorSession.markDirty();
        }
        if (cropDragActive && !ImGui.isMouseDown(0)) {
            cropDragActive = false;
        }
    }

    private static int hitCropHandle(float[] crop, float mx, float my, float hs) {
        if (crop == null) return -1;
        float[][] pts = {
                {crop[0], crop[1]}, {crop[2], crop[1]},
                {crop[0], crop[3]}, {crop[2], crop[3]}
        };
        for (int i = 0; i < 4; i++) {
            if (Math.abs(mx - pts[i][0]) <= hs && Math.abs(my - pts[i][1]) <= hs) return i;
        }
        return -1;
    }

    private static float[] cropPreviewRect(SceneNode node, EditorTexturePreview.Tex tex,
                                           float px, float py, float pw, float ph) {
        int su = node.uiSrcW > 0 ? node.uiSrcU : 0;
        int sv = node.uiSrcW > 0 ? node.uiSrcV : 0;
        int sw = node.uiSrcW > 0 ? node.uiSrcW : tex.width();
        int sh = node.uiSrcW > 0 ? node.uiSrcH : tex.height();
        if (sh <= 0) sh = tex.height();
        float x0 = px + su * pw / tex.width();
        float y0 = py + sv * ph / tex.height();
        float x1 = px + (su + sw) * pw / tex.width();
        float y1 = py + (sv + sh) * ph / tex.height();
        return new float[]{x0, y0, x1, y1};
    }

    private void drawCropOverlay(SceneNode node, EditorTexturePreview.Tex tex,
                                 float px, float py, float pw, float ph) {
        float[] r = cropPreviewRect(node, tex, px, py, pw, ph);
        var dl = ImGui.getWindowDrawList();
        int dim = ImGui.getColorU32(0f, 0f, 0f, 0.45f);
        int line = ImGui.getColorU32(1f, 0.85f, 0.25f, 0.95f);
        int handle = ImGui.getColorU32(1f, 0.95f, 0.55f, 1f);
        dl.addRectFilled(px, py, pw + px, r[1], dim);
        dl.addRectFilled(px, r[3], pw + px, py + ph, dim);
        dl.addRectFilled(px, r[1], r[0], r[3], dim);
        dl.addRectFilled(r[2], r[1], px + pw, r[3], dim);
        if (UiImageFill.isCircle(node.uiCropShape)) {
            float cx = (r[0] + r[2]) * 0.5f;
            float cy = (r[1] + r[3]) * 0.5f;
            float rx = Math.max(1f, (r[2] - r[0]) * 0.5f);
            float ry = Math.max(1f, (r[3] - r[1]) * 0.5f);
            int segs = 48;
            float prevX = cx + rx;
            float prevY = cy;
            for (int i = 1; i <= segs; i++) {
                float a = (float) (i * Math.PI * 2.0 / segs);
                float nx = cx + (float) Math.cos(a) * rx;
                float ny = cy + (float) Math.sin(a) * ry;
                dl.addLine(prevX, prevY, nx, ny, line, 1.6f);
                prevX = nx;
                prevY = ny;
            }
            dl.addRect(r[0], r[1], r[2], r[3], ImGui.getColorU32(1f, 0.85f, 0.25f, 0.35f), 0f, 0, 1f);
        } else {
            dl.addRect(r[0], r[1], r[2], r[3], line, 0f, 0, 1.6f);
        }
        float hs = 4f;
        float[][] pts = {{r[0], r[1]}, {r[2], r[1]}, {r[0], r[3]}, {r[2], r[3]}};
        for (float[] p : pts) {
            dl.addRectFilled(p[0] - hs, p[1] - hs, p[0] + hs, p[1] + hs, handle);
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private void imageFillFields(SceneNode node) {
        boolean circle = UiImageFill.isCircle(node.uiCropShape);
        EditorInspectorUi.fieldLabel(EditorI18n.t("Заполнение"),
                circle
                        ? EditorI18n.t("Круг: по часовой или против. Доля — видимый сектор.")
                        : EditorI18n.t("Прямоугольник: слева/справа/сверху/снизу. Доля — видимая часть."),
                "_node.fill = \"ltr\"\n_node.fillAmount = 0.5\nuiUpdate(player, \"" + node.id + "\", \"fillAmount\", 0.5)");
        String mode = UiImageFill.normalize(node.uiFill);
        String shown = fillModeLabel(mode);
        if (ImGui.beginCombo("##uiw-fill-mode", shown)) {
            fillModeOption(node, UiImageFill.NONE, EditorI18n.t("Полная"));
            if (circle) {
                fillModeOption(node, UiImageFill.CW, EditorI18n.t("По часовой"));
                fillModeOption(node, UiImageFill.CCW, EditorI18n.t("Против часовой"));
            } else {
                fillModeOption(node, UiImageFill.LTR, EditorI18n.t("Слева направо"));
                fillModeOption(node, UiImageFill.RTL, EditorI18n.t("Справа налево"));
                fillModeOption(node, UiImageFill.TTB, EditorI18n.t("Сверху вниз"));
                fillModeOption(node, UiImageFill.BTT, EditorI18n.t("Снизу вверх"));
            }
            ImGui.endCombo();
        }
        EditorInspectorUi.scriptApi(
                circle
                        ? EditorI18n.t("clockwise / counterclockwise")
                        : EditorI18n.t("ltr / rtl / ttb / btt"),
                "_node.fill = \"rtl\"\n_node.fillAmount = 0.5");
        rst(factoryNode.uiFillAmount).dragFloat(EditorI18n.t("Доля (0-1)"), node.uiFillAmount, 0.01f, 0f, 1f, value -> {
                    node.uiFillAmount = value;
                    if (value < 0.999f && (node.uiFill == null || node.uiFill.isBlank()
                            || "none".equalsIgnoreCase(node.uiFill.trim()))) {
                        node.uiFill = circle ? UiImageFill.CW : UiImageFill.LTR;
                    }
                },
                EditorI18n.t("Видимая доля картинки или панели. 1 — полностью."),
                uiPropApi(node, "_node.fillAmount = 0.5", "fillAmount", "0.5"));
        if (UiImageFill.isRadial(node.uiFill)) {
            EditorInspectorUi.fieldLabel(EditorI18n.t("Начало круга"),
                    EditorI18n.t("Откуда начинается круговое заполнение."),
                    "_node.fillOrigin = \"bottom\"");
            String origin = UiImageFill.normalizeOrigin(node.uiFillOrigin);
            String originShown = fillOriginLabel(origin);
            if (ImGui.beginCombo("##uiw-fill-origin", originShown)) {
                fillOriginOption(node, "bottom", EditorI18n.t("Снизу"));
                fillOriginOption(node, "right", EditorI18n.t("Справа"));
                fillOriginOption(node, "top", EditorI18n.t("Сверху"));
                fillOriginOption(node, "left", EditorI18n.t("Слева"));
                ImGui.endCombo();
            }
            EditorInspectorUi.scriptApi(
                    EditorI18n.t("bottom / right / top / left"),
                    "_node.fillOrigin = \"top\"");
        }
    }

    private void fillModeOption(SceneNode node, String value, String label) {
        String current = UiImageFill.normalize(node.uiFill);
        boolean selected = value.equals(current);
        if (ImGui.selectable(label, selected)) {
            SceneEditorSession.pushUndo();
            node.uiFill = UiImageFill.NONE.equals(value) ? "" : value;
            if (UiImageFill.NONE.equals(value)) node.uiFillAmount = 1f;
            SceneEditorSession.markDirty();
        }
    }

    private void fillOriginOption(SceneNode node, String value, String label) {
        String current = UiImageFill.normalizeOrigin(node.uiFillOrigin);
        if (ImGui.selectable(label, value.equals(current))) {
            SceneEditorSession.pushUndo();
            node.uiFillOrigin = value;
            SceneEditorSession.markDirty();
        }
    }

    private static String fillModeLabel(String mode) {
        return switch (mode) {
            case UiImageFill.LTR -> EditorI18n.t("Слева направо");
            case UiImageFill.RTL -> EditorI18n.t("Справа налево");
            case UiImageFill.TTB -> EditorI18n.t("Сверху вниз");
            case UiImageFill.BTT -> EditorI18n.t("Снизу вверх");
            case UiImageFill.CW -> EditorI18n.t("По часовой");
            case UiImageFill.CCW -> EditorI18n.t("Против часовой");
            default -> EditorI18n.t("Полная");
        };
    }

    private static String fillOriginLabel(String origin) {
        return switch (origin) {
            case "right" -> EditorI18n.t("Справа");
            case "top" -> EditorI18n.t("Сверху");
            case "left" -> EditorI18n.t("Слева");
            default -> EditorI18n.t("Снизу");
        };
    }

    private void entityModeCombo(SceneNode node) {
        EditorInspectorUi.fieldLabel(EditorI18n.t("Что рендерить"),
                EditorI18n.t("Источник модели: игрок, НПС сцены или моб."),
                "_node.uiEntityMode = \"player\" | \"npc\" | \"mob\"");
        String mode = node.uiEntityMode == null || node.uiEntityMode.isBlank() ? "player" : node.uiEntityMode;
        String[][] options = {{"player", EditorI18n.t("Игрок")}, {"npc", EditorI18n.t("НПС (нода сцены)")}, {"mob", EditorI18n.t("Моб")}};
        String shownLabel = mode;
        for (String[] o : options) if (o[0].equals(mode)) shownLabel = o[1];
        if (ImGui.beginCombo("##uiw-entity-mode", shownLabel)) {
            for (String[] o : options) {
                if (ImGui.selectable(o[1], o[0].equals(mode))) {
                    SceneEditorSession.pushUndo();
                    node.uiEntityMode = o[0];
                    node.uiEntityRef = "";
                    SceneEditorSession.markDirty();
                }
            }
            ImGui.endCombo();
        }
        EditorInspectorUi.scriptApi(EditorI18n.t("Источник модели: игрок, НПС сцены или моб."),
                "_node.uiEntityMode = \"player\" | \"npc\" | \"mob\"");
    }

    /** Combo listing all NPC nodes of the current scene by id/name. */
    private void npcNodeCombo(SceneNode node) {
        SceneDocument doc = SceneEditorSession.document();
        List<SceneNode> npcs = new ArrayList<>();
        if (doc != null) collectNpcs(doc.root, npcs);
        String shown = node.uiEntityRef == null || node.uiEntityRef.isBlank() ? EditorI18n.t("(не выбрано)") : node.uiEntityRef;
        EditorInspectorUi.fieldLabel(EditorI18n.t("НПС"),
                EditorI18n.t("ID ноды НПС из этой сцены."),
                "_node.uiEntityRef = \"npc_id\"");
        if (ImGui.beginCombo("##uiw-entity-npc", shown)) {
            for (SceneNode npc : npcs) {
                String label = npc.id + (npc.name != null && !npc.name.isBlank() ? " (" + npc.name + ")" : "");
                if (ImGui.selectable(label, npc.id.equals(node.uiEntityRef))) {
                    SceneEditorSession.pushUndo();
                    node.uiEntityRef = npc.id;
                    SceneEditorSession.markDirty();
                }
            }
            if (npcs.isEmpty()) ImGui.textDisabled(EditorI18n.t("В сцене нет НПС-нод"));
            ImGui.endCombo();
        }
        EditorInspectorUi.scriptApi("_node.uiEntityRef = \"npc_id\"");
    }

    private static void collectNpcs(SceneNode node, List<SceneNode> out) {
        if (node.type == SceneNodeType.NPC) out.add(node);
        for (SceneNode child : node.children) collectNpcs(child, out);
    }

    private void childAlignCombo(SceneNode node) {
        EditorInspectorUi.fieldLabel(EditorI18n.t("Выравнивание детей"),
                EditorI18n.t("Куда крепится (0,0) дочерних виджетов внутри контейнера."),
                "_node.uiChildAlign = \"center\"\n_node.childAlign = \"center\"");
        String current = UiChildAlign.normalize(node.uiChildAlign);
        String shown = childAlignLabel(current);
        if (ImGui.beginCombo("##uiw-child-align", shown)) {
            for (String option : UiChildAlign.OPTIONS) {
                if (ImGui.selectable(childAlignLabel(option), option.equals(current))) {
                    SceneEditorSession.pushUndo();
                    node.uiChildAlign = option;
                    SceneEditorSession.markDirty();
                }
            }
            ImGui.endCombo();
        }
        EditorInspectorUi.scriptApi(
                EditorI18n.t("Куда крепится (0,0) дочерних виджетов внутри контейнера."),
                "_node.uiChildAlign = \"center\"\n_node.childAlign = \"center\"");
    }

    private void dragAxisCombo(SceneNode node) {
        EditorInspectorUi.fieldLabel(EditorI18n.t("Ось"),
                EditorI18n.t("Куда можно тащить: обе оси, только X или только Y."),
                "_node.axis = \"xy\" | \"x\" | \"y\"");
        String axis = node.uiDragAxis == null || node.uiDragAxis.isBlank() ? "xy" : node.uiDragAxis.trim().toLowerCase(Locale.ROOT);
        if (!axis.equals("x") && !axis.equals("y")) axis = "xy";
        String shown = switch (axis) {
            case "x" -> EditorI18n.t("Только X");
            case "y" -> EditorI18n.t("Только Y");
            default -> EditorI18n.t("X и Y");
        };
        if (ImGui.beginCombo("##uiw-drag-axis", shown)) {
            if (ImGui.selectable(EditorI18n.t("X и Y"), "xy".equals(axis))) {
                SceneEditorSession.pushUndo();
                node.uiDragAxis = "xy";
                SceneEditorSession.markDirty();
            }
            if (ImGui.selectable(EditorI18n.t("Только X"), "x".equals(axis))) {
                SceneEditorSession.pushUndo();
                node.uiDragAxis = "x";
                SceneEditorSession.markDirty();
            }
            if (ImGui.selectable(EditorI18n.t("Только Y"), "y".equals(axis))) {
                SceneEditorSession.pushUndo();
                node.uiDragAxis = "y";
                SceneEditorSession.markDirty();
            }
            ImGui.endCombo();
        }
        EditorInspectorUi.scriptApi(EditorI18n.t("Куда можно тащить: обе оси, только X или только Y."),
                "_node.axis = \"xy\"\n_node.dragAxis = \"x\"");
    }

    private void gridModeCombo(SceneNode node) {
        EditorInspectorUi.fieldLabel(EditorI18n.t("Режим раскладки"),
                EditorI18n.t("grid — сетка, row — ряд, column — колонка."),
                "_node.gridMode = \"row\"");
        String mode = UiGridLayout.normalizeMode(node.gridMode);
        String shown = switch (mode) {
            case "row" -> EditorI18n.t("Ряд");
            case "column" -> EditorI18n.t("Колонка");
            default -> EditorI18n.t("Сетка");
        };
        if (ImGui.beginCombo("##uiw-grid-mode", shown)) {
            if (ImGui.selectable(EditorI18n.t("Сетка"), "grid".equals(mode))) {
                SceneEditorSession.pushUndo();
                node.gridMode = "grid";
                SceneEditorSession.markDirty();
            }
            if (ImGui.selectable(EditorI18n.t("Ряд"), "row".equals(mode))) {
                SceneEditorSession.pushUndo();
                node.gridMode = "row";
                SceneEditorSession.markDirty();
            }
            if (ImGui.selectable(EditorI18n.t("Колонка"), "column".equals(mode))) {
                SceneEditorSession.pushUndo();
                node.gridMode = "column";
                SceneEditorSession.markDirty();
            }
            ImGui.endCombo();
        }
        EditorInspectorUi.scriptApi(
                EditorI18n.t("grid — сетка, row — ряд, column — колонка."),
                "_node.gridMode = \"row\"");
    }

    private void gridAlignCombo(SceneNode node) {
        EditorInspectorUi.fieldLabel(EditorI18n.t("Выравнивание"),
                EditorI18n.t("Выравнивание пакета / детей в ячейках (top_left, center, …)."),
                "_node.gridAlign = \"center\"");
        String current = UiChildAlign.normalize(node.gridAlign);
        String shown = childAlignLabel(current);
        if (ImGui.beginCombo("##uiw-grid-align", shown)) {
            for (String option : UiChildAlign.OPTIONS) {
                if (ImGui.selectable(childAlignLabel(option), option.equals(current))) {
                    SceneEditorSession.pushUndo();
                    node.gridAlign = option;
                    SceneEditorSession.markDirty();
                }
            }
            ImGui.endCombo();
        }
        EditorInspectorUi.scriptApi(
                EditorI18n.t("Выравнивание пакета / детей в ячейках (top_left, center, …)."),
                "_node.gridAlign = \"center\"");
    }

    private void gridFlowCombo(SceneNode node) {
        String mode = UiGridLayout.normalizeMode(node.gridMode);
        if ("grid".equals(mode)) return;
        EditorInspectorUi.fieldLabel(EditorI18n.t("Направление"),
                EditorI18n.t("row: ltr/rtl · column: ttb/btt"),
                "_node.gridFlow = \"rtl\"");
        String flow = UiGridLayout.normalizeFlow(node.gridFlow);
        String shown;
        if ("row".equals(mode)) {
            shown = "rtl".equals(flow) ? EditorI18n.t("Справа налево") : EditorI18n.t("Слева направо");
        } else {
            shown = "btt".equals(flow) ? EditorI18n.t("Снизу вверх") : EditorI18n.t("Сверху вниз");
        }
        if (ImGui.beginCombo("##uiw-grid-flow", shown)) {
            if ("row".equals(mode)) {
                if (ImGui.selectable(EditorI18n.t("Слева направо"), !"rtl".equals(flow))) {
                    SceneEditorSession.pushUndo();
                    node.gridFlow = "ltr";
                    SceneEditorSession.markDirty();
                }
                if (ImGui.selectable(EditorI18n.t("Справа налево"), "rtl".equals(flow))) {
                    SceneEditorSession.pushUndo();
                    node.gridFlow = "rtl";
                    SceneEditorSession.markDirty();
                }
            } else {
                if (ImGui.selectable(EditorI18n.t("Сверху вниз"), !"btt".equals(flow))) {
                    SceneEditorSession.pushUndo();
                    node.gridFlow = "ttb";
                    SceneEditorSession.markDirty();
                }
                if (ImGui.selectable(EditorI18n.t("Снизу вверх"), "btt".equals(flow))) {
                    SceneEditorSession.pushUndo();
                    node.gridFlow = "btt";
                    SceneEditorSession.markDirty();
                }
            }
            ImGui.endCombo();
        }
        EditorInspectorUi.scriptApi(
                EditorI18n.t("row: ltr/rtl · column: ttb/btt"),
                "_node.gridFlow = \"rtl\"");
    }

    private static String childAlignLabel(String align) {
        return switch (UiChildAlign.normalize(align)) {
            case "top" -> EditorI18n.t("Сверху");
            case "top_right" -> EditorI18n.t("Справа сверху");
            case "left" -> EditorI18n.t("Слева");
            case "center" -> EditorI18n.t("По центру");
            case "right" -> EditorI18n.t("Справа");
            case "bottom_left" -> EditorI18n.t("Слева снизу");
            case "bottom" -> EditorI18n.t("Снизу");
            case "bottom_right" -> EditorI18n.t("Справа снизу");
            default -> EditorI18n.t("Слева сверху");
        };
    }

    private void alignCombo(SceneNode node) {
        alignCombo(node, "left");
    }

    private void alignCombo(SceneNode node, String defaultAlign) {
        String fallback = defaultAlign == null || defaultAlign.isBlank() ? "left" : defaultAlign;
        EditorInspectorUi.fieldLabel(EditorI18n.t("Выравнивание текста"),
                EditorI18n.t("left — слева, center — по центру, right — справа."),
                uiPropApi(node, "_node.uiAlign = \"center\"\n_node.align = \"center\"", "align", "\"center\""));
        String current = node.uiAlign == null || node.uiAlign.isBlank() ? fallback : node.uiAlign;
        if (ImGui.beginCombo("##uiw-align", current)) {
            for (String option : new String[]{"left", "center", "right"}) {
                if (ImGui.selectable(option, option.equals(current))) {
                    SceneEditorSession.pushUndo();
                    node.uiAlign = option;
                    SceneEditorSession.markDirty();
                }
            }
            ImGui.endCombo();
        }
        EditorInspectorUi.scriptApi(
                EditorI18n.t("left — слева, center — по центру, right — справа."),
                uiPropApi(node, "_node.uiAlign = \"center\"\n_node.align = \"center\"", "align", "\"center\""));
    }

    private void wrapModeCombo(SceneNode node) {
        EditorInspectorUi.fieldLabel(EditorI18n.t("Режим переноса"),
                EditorI18n.t("word — по словам (по умолчанию), char — по буквам."),
                "_node.uiWrapMode = \"word\"\n_node.wrapMode = \"char\"");
        String mode = node.uiWrapMode == null || node.uiWrapMode.isBlank() ? "word" : node.uiWrapMode;
        String shown = "char".equalsIgnoreCase(mode) ? EditorI18n.t("По буквам") : EditorI18n.t("По словам");
        if (ImGui.beginCombo("##uiw-wrap-mode", shown)) {
            if (ImGui.selectable(EditorI18n.t("По словам"), !"char".equalsIgnoreCase(mode))) {
                SceneEditorSession.pushUndo();
                node.uiWrapMode = "word";
                SceneEditorSession.markDirty();
            }
            if (ImGui.selectable(EditorI18n.t("По буквам"), "char".equalsIgnoreCase(mode))) {
                SceneEditorSession.pushUndo();
                node.uiWrapMode = "char";
                SceneEditorSession.markDirty();
            }
            ImGui.endCombo();
        }
        EditorInspectorUi.scriptApi(
                EditorI18n.t("word — по словам (по умолчанию), char — по буквам."),
                "_node.uiWrapMode = \"word\"\n_node.wrapMode = \"char\"");
    }

    private void textEffectFields(SceneNode node) {
        ImGui.textDisabled(EditorI18n.t("Начертание"));
        EditorInspectorUi.hint(EditorI18n.t("На весь виджет. Для фрагмента используйте панель в разделе «Текст»."));
        effectCheckbox(node, "bold", EditorI18n.t("Жирный"));
        effectCheckbox(node, "italic", EditorI18n.t("Курсив"));
        effectCheckbox(node, "underline", EditorI18n.t("Подчёркивание"));
        if (UiTextStyle.hasNamed(textEffects(node), "underline")) {
            ImGui.indent(12f);
            UiStyleEditor.colorField(EditorI18n.t("Цвет подчёркивания"), "ui-text-underline",
                    node.uiTextUnderlineColor == null || node.uiTextUnderlineColor.isBlank()
                            ? node.uiColor : node.uiTextUnderlineColor,
                    value -> node.uiTextUnderlineColor = value,
                    null, "_node.uiTextUnderlineColor = \"#FFFFFFFF\"");
            rst(factoryNode.uiTextUnderlineThickness).dragFloat(EditorI18n.t("Толщина подчёркивания"), node.uiTextUnderlineThickness,
                    0.05f, 0.5f, 6f, value -> node.uiTextUnderlineThickness = value,
                    "_node.uiTextUnderlineThickness = 1");
            rst(factoryNode.uiTextUnderlineOffset).dragFloat(EditorI18n.t("Смещение подчёркивания Y"), node.uiTextUnderlineOffset,
                    0.05f, -4f, 6f, value -> node.uiTextUnderlineOffset = value,
                    "_node.uiTextUnderlineOffset = 0");
            ImGui.unindent(12f);
        }
        effectCheckbox(node, "strikethrough", EditorI18n.t("Зачёркивание"));
        effectCheckbox(node, "code", EditorI18n.t("Код / моноширинный"));

        ImGui.spacing();
        ImGui.separator();
        ImGui.textDisabled(EditorI18n.t("Контур и свет"));
        effectCheckbox(node, "shadow", EditorI18n.t("Тень"));
        if (UiTextStyle.hasNamed(textEffects(node), "shadow")) {
            ImGui.indent(12f);
            pixelModeRow(node, "shadow", true);
            UiStyleEditor.colorField(EditorI18n.t("Цвет тени"), "ui-text-shadow",
                    node.uiTextShadowColor == null || node.uiTextShadowColor.isBlank()
                            ? "#88000000" : node.uiTextShadowColor,
                    value -> node.uiTextShadowColor = value,
                    null, "_node.uiTextShadowColor = \"#88000000\"");
            rst(factoryNode.uiTextShadowOffsetX).dragFloat(EditorI18n.t("Смещение тени X"), node.uiTextShadowOffsetX,
                    0.05f, -12f, 12f, value -> node.uiTextShadowOffsetX = value,
                    "_node.uiTextShadowOffsetX = 1");
            rst(factoryNode.uiTextShadowOffsetY).dragFloat(EditorI18n.t("Смещение тени Y"), node.uiTextShadowOffsetY,
                    0.05f, -12f, 12f, value -> node.uiTextShadowOffsetY = value,
                    "_node.uiTextShadowOffsetY = 1");
            if (!UiTextStyle.pixelMode(textEffects(node), "shadow", true)) {
                rst(factoryNode.uiTextShadowSize).dragFloat(EditorI18n.t("Размытие / размер"), node.uiTextShadowSize,
                        0.05f, 0.5f, 12f, value -> node.uiTextShadowSize = value,
                        "_node.uiTextShadowSize = 2");
            }
            ImGui.unindent(12f);
        }
        effectCheckbox(node, "outline", EditorI18n.t("Обводка"));
        if (UiTextStyle.hasNamed(textEffects(node), "outline")) {
            ImGui.indent(12f);
            pixelModeRow(node, "outline", true);
            UiStyleEditor.colorField(EditorI18n.t("Цвет обводки"), "ui-text-outline",
                    node.uiTextOutlineColor, value -> node.uiTextOutlineColor = value,
                    null, "_node.uiTextOutlineColor = \"#FF000000\"");
            rst(factoryNode.uiTextOutlineWidth).dragFloat(EditorI18n.t("Толщина обводки"), node.uiTextOutlineWidth, 0.05f, 0.5f, 8f,
                    value -> node.uiTextOutlineWidth = value,
                    "_node.uiTextOutlineWidth = 1\nuiUpdate(player, \"" + node.id + "\", \"textOutlineWidth\", 1)");
            ImGui.unindent(12f);
        }
        effectCheckbox(node, "glow", EditorI18n.t("Свечение"));
        if (UiTextStyle.hasNamed(textEffects(node), "glow")) {
            ImGui.indent(12f);
            pixelModeRow(node, "glow", false);
            EditorInspectorUi.hint(EditorI18n.t("Мягкое свечение с затуханием; пиксельное рисует силуэт по клеткам"));
            UiStyleEditor.colorField(EditorI18n.t("Цвет свечения"), "ui-text-glow",
                    node.uiTextGlowColor, value -> node.uiTextGlowColor = value,
                    null, "_node.uiTextGlowColor = \"#66FFFFFF\"");
            rst(factoryNode.uiTextGlowSize).dragFloat(EditorI18n.t("Размер свечения"), node.uiTextGlowSize, 0.05f, 0.5f, 12f,
                    value -> node.uiTextGlowSize = value,
                    "_node.uiTextGlowSize = 3\nuiUpdate(player, \"" + node.id + "\", \"textGlowSize\", 3)");
            ImGui.unindent(12f);
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.textDisabled(EditorI18n.t("Цвет"));
        UiStyleEditor.textGradientEditor(node, "ui-text-" + node.id);

        ImGui.spacing();
        ImGui.separator();
        ImGui.textDisabled(EditorI18n.t("Анимация текста"));
        effectCheckbox(node, "rainbow", EditorI18n.t("Радуга"));
        effectCheckbox(node, "pulse", EditorI18n.t("Пульсация"));
        effectCheckbox(node, "wave", EditorI18n.t("Волна"));
        effectCheckbox(node, "shake", EditorI18n.t("Тряска"));
        effectCheckbox(node, "wiggle", EditorI18n.t("Покачивание"));
        Set<String> motion = textEffects(node);
        if (UiTextStyle.hasNamed(motion, "rainbow") || UiTextStyle.hasNamed(motion, "pulse")
                || UiTextStyle.hasNamed(motion, "wave") || UiTextStyle.hasNamed(motion, "shake")
                || UiTextStyle.hasNamed(motion, "wiggle")) {
            rst(factoryNode.uiTextEffectSpeed).dragFloat(EditorI18n.t("Скорость эффекта"), node.uiTextEffectSpeed, 0.02f, 0.05f, 10f,
                    value -> node.uiTextEffectSpeed = value,
                    "_node.uiTextEffectSpeed = 1");
            rst(factoryNode.uiTextEffectAmount).dragFloat(EditorI18n.t("Сила эффекта"), node.uiTextEffectAmount, 0.02f, 0.1f, 5f,
                    value -> node.uiTextEffectAmount = value,
                    "_node.uiTextEffectAmount = 1");
        }
    }

    private void effectCheckbox(SceneNode node, String key, String label) {
        Set<String> active = textEffects(node);
        checkbox(label, UiTextStyle.hasNamed(active, key),
                value -> setTextEffect(node, key, value),
                "_node.uiTextEffects = \"" + key + "\"\n"
                        + "uiUpdate(player, \"" + node.id + "\", \"effects\", \"" + key + "\")");
    }

    private void pixelModeRow(SceneNode node, String base, boolean defaultPixel) {
        boolean pixel = UiTextStyle.pixelMode(textEffects(node), base, defaultPixel);
        EditorInspectorUi.fieldLabel(EditorI18n.t("Режим"));
        if (ImGui.radioButton(EditorI18n.t("Пиксельный") + "##px-" + base + "-" + node.id, pixel) && !pixel) {
            applyEdit(() -> setPixelMode(node, base, true));
        }
        ImGui.sameLine();
        if (ImGui.radioButton(EditorI18n.t("Мягкий") + "##sm-" + base + "-" + node.id, !pixel) && pixel) {
            applyEdit(() -> setPixelMode(node, base, false));
        }
    }

    private static Set<String> textEffects(SceneNode node) {
        return UiTextStyle.parseEffects(node.uiTextEffects);
    }

    private static void setTextEffect(SceneNode node, String effect, boolean enabled) {
        Set<String> effects = textEffects(node);
        boolean pixel = UiTextStyle.pixelMode(effects, effect, !"glow".equals(effect));
        effects.remove(effect);
        effects.remove(effect + "_pixel");
        effects.remove(effect + "_soft");
        if (enabled) {
            String token = UiTextStyle.modeToken(effect, true, pixel);
            if (token != null) effects.add(token);
            else effects.add(effect);
        }
        node.uiTextEffects = String.join(",", effects);
    }

    private static void setPixelMode(SceneNode node, String base, boolean pixel) {
        Set<String> effects = textEffects(node);
        if (!UiTextStyle.hasNamed(effects, base)) return;
        effects.remove(base);
        effects.remove(base + "_pixel");
        effects.remove(base + "_soft");
        String token = UiTextStyle.modeToken(base, true, pixel);
        if (token != null) effects.add(token);
        node.uiTextEffects = String.join(",", effects);
    }

    private static boolean section(String id, String title) {
        return EditorInspectorUi.beginSection(id, title, false);
    }

    private static void endSection() {
        EditorInspectorUi.endSection();
    }

    private InspectorPanel rst(Object defaultValue) {
        hasResetDefault = true;
        resetDefault = defaultValue;
        return this;
    }

    /** Checkbox that can revert to {@code factory}. Returns the value after click/reset. */
    private boolean applyToggle(String label, boolean current, boolean factory,
                                String description, String script) {
        boolean[] next = {current};
        EditorInspectorUi.bindReset(current != factory, () -> next[0] = factory);
        EditorInspectorUi.bindClipboard(current, v -> {
            Object c = coerceClipBool(v);
            if (c instanceof Boolean b) next[0] = b;
        });
        if (EditorInspectorUi.checkbox(label, current, description, script)) next[0] = !current;
        return next[0];
    }

    private boolean applyToggle(String label, boolean current, boolean factory, String script) {
        return applyToggle(label, current, factory, null, script);
    }

    private boolean applyDrag(String label, String id, float[] buf, float speed, float min, float max,
                              float factory, String description, String script) {
        boolean[] reset = {false};
        EditorInspectorUi.bindReset(Math.abs(buf[0] - factory) > 1e-4f, () -> {
            buf[0] = factory;
            reset[0] = true;
        });
        EditorInspectorUi.bindClipboard(buf[0], v -> {
            if (v instanceof Number n) {
                buf[0] = n.floatValue();
                reset[0] = true;
            }
        });
        boolean changed = EditorInspectorUi.dragFloat(label, id, buf, speed, min, max, description, script);
        return changed || reset[0];
    }

    private boolean applyDrag(String label, String id, float[] buf, float speed, float min, float max,
                              float factory, String script) {
        return applyDrag(label, id, buf, speed, min, max, factory, null, script);
    }

    private static boolean resetEquals(Object current, Object def) {
        if (current instanceof Number cn && def instanceof Number dn) {
            return Math.abs(cn.doubleValue() - dn.doubleValue()) < 1e-4;
        }
        if (current instanceof Boolean || def instanceof Boolean) {
            return toResetBool(current) == toResetBool(def);
        }
        String a = current == null ? "" : String.valueOf(current);
        String b = def == null ? "" : String.valueOf(def);
        return a.equals(b);
    }

    private static boolean toResetBool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        if (v == null) return false;
        String s = String.valueOf(v);
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    private static Object coerceClipBool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        if (v == null) return false;
        String s = String.valueOf(v).trim();
        if ("true".equalsIgnoreCase(s) || "1".equals(s)) return true;
        if ("false".equalsIgnoreCase(s) || "0".equals(s)) return false;
        return null;
    }

    private static String vec3Clip(float[] v) {
        return v[0] + ", " + v[1] + ", " + v[2];
    }

    private static boolean applyVec3Clip(float[] dest, Object incoming) {
        if (incoming == null) return false;
        String s = String.valueOf(incoming).trim().replace(';', ',');
        String[] parts = s.split("[,\\s]+");
        if (parts.length < 3) return false;
        try {
            dest[0] = Float.parseFloat(parts[0]);
            dest[1] = Float.parseFloat(parts[1]);
            dest[2] = Float.parseFloat(parts[2]);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static float toResetFloat(Object v) {
        if (v instanceof Number n) return n.floatValue();
        if (v == null) return 0f;
        try {
            return Float.parseFloat(String.valueOf(v).trim());
        } catch (Exception e) {
            return 0f;
        }
    }

    private static int toResetInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return 0;
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(v).trim()));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String toResetString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private void consumeReset(Object current, java.util.function.Consumer<Object> applyDefault) {
        if (!hasResetDefault) return;
        hasResetDefault = false;
        Object def = resetDefault;
        resetDefault = null;
        boolean modified = !resetEquals(current, def);
        EditorInspectorUi.bindReset(modified, () -> applyEdit(() -> applyDefault.accept(def)));
        EditorInspectorUi.bindClipboard(current, v -> applyEdit(() -> applyDefault.accept(v)));
    }

    private void checkbox(String label, boolean current, BoolSetter setter) {
        checkbox(label, current, setter, null, null);
    }

    private void checkbox(String label, boolean current, BoolSetter setter, String script) {
        checkbox(label, current, setter, null, script);
    }

    private void checkbox(String label, boolean current, BoolSetter setter,
                          String description, String script) {
        consumeReset(current, v -> setter.set(toResetBool(v)));
        if (EditorInspectorUi.checkbox(label, current, description, script)) {
            SceneEditorSession.pushUndo();
            beginMultiSnap();
            setter.set(!current);
            endMultiSnap();
            SceneEditorSession.markDirty();
        }
    }

    private void dragFloat(String label, float current, float speed, float min, float max, FloatSetter setter) {
        dragFloat(label, current, speed, min, max, setter, null, null);
    }

    private void dragFloat(String label, float current, float speed, float min, float max,
                           FloatSetter setter, String script) {
        dragFloat(label, current, speed, min, max, setter, null, script);
    }

    private void dragFloat(String label, float current, float speed, float min, float max,
                           FloatSetter setter, String description, String script) {
        consumeReset(current, v -> setter.set(toResetFloat(v)));
        single[0] = current;
        String shown = label;
        if (isMulti() && isMixedFloat(label, current)) {
            shown = label + " *";
        }
        if (EditorInspectorUi.dragFloat(shown, "f-" + label, single, speed, min, max, description, script)) {
            beginMultiSnap();
            setter.set(single[0]);
            endMultiSnap();
            SceneEditorSession.markDirty();
        }
        EditorInspectorUi.undoOnActivate();
    }

    private void dragInt(String label, int current, int speed, int min, int max, IntSetter setter) {
        dragInt(label, current, speed, min, max, setter, null, null);
    }

    private void dragInt(String label, int current, int speed, int min, int max,
                         IntSetter setter, String script) {
        dragInt(label, current, speed, min, max, setter, null, script);
    }

    private void dragInt(String label, int current, int speed, int min, int max,
                         IntSetter setter, String description, String script) {
        consumeReset(current, v -> setter.set(toResetInt(v)));
        int[] value = {current};
        EditorInspectorUi.fieldLabel(
                isMulti() && isMixedInt(current, setter) ? label + " *" : label,
                description, script);
        ImGui.setNextItemWidth(-1f);
        if (ImGui.dragInt("##i-" + label, value, speed, min, max)) {
            beginMultiSnap();
            setter.set(value[0]);
            endMultiSnap();
            SceneEditorSession.markDirty();
        }
        EditorInspectorUi.scriptApi(description, script);
        EditorInspectorUi.undoOnActivate();
        EditorInspectorUi.finishClipboardField();
    }

    private void richTextField(String label, SceneNode node, String description, String script) {
        String source = node.uiText == null ? "" : node.uiText;
        String shown = uiMarkShowTags ? source : UiTextMarkup.plainText(source);
        uiTextBuffer.set(shown);
        EditorInspectorUi.fieldLabel(label, description, script);
        if (EditorInspectorUi.checkbox(EditorI18n.t("Показывать теги форматирования"), uiMarkShowTags,
                EditorI18n.t("Включите для ручного редактирования тегов. В визуальном режиме показывается только текст."),
                null)) {
            uiMarkShowTags = !uiMarkShowTags;
            uiMarkSelStart = 0;
            uiMarkSelEnd = 0;
            uiMarkStickyStart = 0;
            uiMarkStickyEnd = 0;
            uiMarkPending = null;
            shown = uiMarkShowTags ? source : UiTextMarkup.plainText(source);
            uiTextBuffer.set(shown);
        }
        markupToolbar(node, source);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.12f, 0.13f, 0.16f, 1f);
        float h = Math.max(ImGui.getTextLineHeight() * 4.2f + 12f, 72f);
        boolean changed = ImGui.inputTextMultiline("##rich-text-" + node.id, uiTextBuffer,
                0f, h,
                ImGuiInputTextFlags.CallbackAlways | ImGuiInputTextFlags.AllowTabInput,
                uiMarkCallback);
        boolean fieldHovered = ImGui.isItemHovered();
        ImGui.popStyleColor();
        EditorInspectorUi.scriptApi(description, script);
        if (changed) {
            beginMultiSnap();
            node.uiText = uiMarkShowTags
                    ? uiTextBuffer.get()
                    : UiTextMarkup.replacePlain(source, shown, uiTextBuffer.get());
            endMultiSnap();
            SceneEditorSession.markDirty();
        }
        uiMarkFieldActive = ImGui.isItemActive() || ImGui.isItemFocused();
        EditorInspectorUi.undoOnActivate();
        if (fieldHovered && ImGui.isMouseClicked(1)) {
            if (uiMarkSelStart == uiMarkSelEnd && uiMarkStickyStart != uiMarkStickyEnd) {
                uiMarkSelStart = uiMarkStickyStart;
                uiMarkSelEnd = uiMarkStickyEnd;
            }
            if (uiMarkSelStart != uiMarkSelEnd) {
                ImGui.openPopup("##rich-text-fx-" + node.id);
            }
        }
        VisualScriptEditor.pushMenuStyle();
        if (ImGui.beginPopup("##rich-text-fx-" + node.id)) {
            ImGui.textDisabled(EditorI18n.t("Эффекты текста"));
            ImGui.separator();
            String live = node.uiText == null ? "" : node.uiText;
            int a = uiMarkShowTags ? uiMarkSelStart : UiTextMarkup.rawStartForPlain(live, uiMarkSelStart);
            int b = uiMarkShowTags ? uiMarkSelEnd : UiTextMarkup.rawIndexForPlain(live, uiMarkSelEnd);
            String tag = org.zonarstudio.spraute_engine.client.editor.UiTextMarkupMenu.drawItems(live, a, b);
            if (tag != null) {
                applyMarkup(node, configuredMarkupTag(tag), false);
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
        VisualScriptEditor.popMenuStyle();
        if (uiMarkShowTags) {
            EditorInspectorUi.hint(EditorI18n.t("Теги: [glow]огонь[/glow]  [#FFFF5555]цвет[/#]  [[ → ["));
        } else {
            EditorInspectorUi.hint(EditorI18n.t("Выделите текст и нажмите ПКМ — эффекты. Повторный клик снимает тег."));
        }
    }

    private void markupToolbar(SceneNode node, String source) {
        int a = uiMarkShowTags ? uiMarkSelStart : UiTextMarkup.rawStartForPlain(source, uiMarkSelStart);
        int b = uiMarkShowTags ? uiMarkSelEnd : UiTextMarkup.rawIndexForPlain(source, uiMarkSelEnd);
        markupChip("B", "bold", EditorI18n.t("Жирный"), source, a, b, node);
        markupChip("I", "italic", EditorI18n.t("Курсив"), source, a, b, node);
        markupChip("U", "underline", EditorI18n.t("Подчёркивание"), source, a, b, node);
        markupChip("S", "strikethrough", EditorI18n.t("Зачёркивание"), source, a, b, node);
        markupSep();
        ImGui.setNextItemWidth(28f);
        ImGui.colorEdit4("##mk-col-" + node.id, uiMarkColor,
                ImGuiColorEditFlags.NoInputs | ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.AlphaPreviewHalf);
        if (ImGui.isItemHovered()) ImGui.setTooltip(EditorI18n.t("Цвет выделения"));
        ImGui.sameLine(0f, 4f);
        if (ImGui.smallButton(EditorI18n.t("Цвет") + "##mk-color-btn")) {
            applyMarkup(node, UiStyleEditor.rgbaToHex(uiMarkColor));
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(EditorI18n.t("Обернуть выделение цветом"));
        ImGui.sameLine(0f, 4f);
        markupChip(EditorI18n.t("Град."), "gradient", EditorI18n.t("Градиент на фрагмент"), source, a, b, node);
        markupSep();
        markupChip(EditorI18n.t("Тень"), "shadow", EditorI18n.t("Тень"), source, a, b, node);
        markupChip(EditorI18n.t("Обводка"), "outline", EditorI18n.t("Обводка"), source, a, b, node);
        markupChip(EditorI18n.t("Свечение"), "glow", EditorI18n.t("Свечение"), source, a, b, node);
        markupSep();
        markupChip(EditorI18n.t("Радуга"), "rainbow", EditorI18n.t("Радуга"), source, a, b, node);
        markupChip(EditorI18n.t("Волна"), "wave", EditorI18n.t("Волна"), source, a, b, node);
        markupChip(EditorI18n.t("Пульс"), "pulse", EditorI18n.t("Пульсация"), source, a, b, node);
        markupChip(EditorI18n.t("Тряска"), "shake", EditorI18n.t("Тряска"), source, a, b, node);
        markupChip(EditorI18n.t("Кач."), "wiggle", EditorI18n.t("Покачивание"), source, a, b, node);
        ImGui.newLine();
        ImGui.dummy(1f, 2f);
    }

    private void markupSep() {
        ImGui.sameLine(0f, 8f);
        ImGui.textDisabled("|");
        ImGui.sameLine(0f, 8f);
    }

    private void markupChip(String label, String tag, String tooltip, String text, int a, int b, SceneNode node) {
        boolean on = UiTextMarkup.selectionWrapped(text, a, b, tag);
        String popupId = "##mk-settings-" + tag + "-" + node.id;
        if (on) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.25f, 0.45f, 0.68f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.30f, 0.52f, 0.78f, 1f);
        }
        float chipW = ImGui.calcTextSizeX(label) + 14f;
        float rowStart = ImGui.getCursorPosX();
        float maxX = ImGui.getWindowPosX() + ImGui.getContentRegionMaxX();
        if (ImGui.getCursorScreenPosX() + chipW > maxX && rowStart > 8f) {
            ImGui.newLine();
        }
        if (ImGui.smallButton(label + "##mk-" + tag + "-" + node.id)) {
            applyMarkup(node, configuredMarkupTag(tag), false);
        }
        if (ImGui.isItemClicked(1)) ImGui.openPopup(popupId);
        if (on) ImGui.popStyleColor(2);
        if (tooltip != null && ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip + "\n" + EditorI18n.t("ЛКМ — включить/снять · ПКМ — параметры"));
        }
        renderMarkupSettingsPopup(popupId, tag, node);
        ImGui.sameLine(0f, 4f);
    }

    private void applyMarkup(SceneNode node, String tag) {
        applyMarkup(node, tag, false);
    }

    private void applyMarkup(SceneNode node, String tag, boolean configure) {
        String text = node.uiText == null ? "" : node.uiText;
        int start = uiMarkShowTags
                ? uiMarkSelStart
                : UiTextMarkup.rawStartForPlain(text, uiMarkSelStart);
        int end = uiMarkShowTags
                ? uiMarkSelEnd
                : UiTextMarkup.rawIndexForPlain(text, uiMarkSelEnd);
        UiTextMarkup.Caret caret = configure
                ? UiTextMarkup.configure(text, start, end, tag)
                : UiTextMarkup.toggle(text, start, end, tag);
        applyEdit(() -> node.uiText = caret.text());
        String shown = uiMarkShowTags ? caret.text() : UiTextMarkup.plainText(caret.text());
        uiTextBuffer.set(shown);
        if (uiMarkFieldActive) {
            uiMarkPending = shown;
            uiMarkPendingCursor = uiMarkShowTags
                    ? caret.cursor()
                    : UiTextMarkup.plainIndexForRaw(caret.text(), caret.cursor());
        } else {
            uiMarkPending = null;
        }
        uiMarkSelStart = uiMarkShowTags
                ? caret.selStart()
                : UiTextMarkup.plainIndexForRaw(caret.text(), caret.selStart());
        uiMarkSelEnd = uiMarkShowTags
                ? caret.selEnd()
                : UiTextMarkup.plainIndexForRaw(caret.text(), caret.selEnd());
    }

    private String configuredMarkupTag(String base) {
        return switch (base) {
            case "underline" -> "underline=" + UiStyleEditor.rgbaToHex(uiMarkUnderlineColor)
                    + "/" + uiMarkUnderlineThickness[0] + "/" + uiMarkUnderlineOffset[0];
            case "outline" -> "outline=" + UiStyleEditor.rgbaToHex(uiMarkOutlineColor)
                    + "/" + uiMarkOutlineWidth[0] + "/" + (uiMarkPixelOutline ? "pixel" : "soft");
            case "glow" -> "glow=" + UiStyleEditor.rgbaToHex(uiMarkGlowColor)
                    + "/" + uiMarkGlowSize[0] + "/" + (uiMarkPixelGlow ? "pixel" : "soft");
            case "shadow" -> "shadow=" + UiStyleEditor.rgbaToHex(uiMarkShadowColor)
                    + "/" + uiMarkShadowX[0] + "/" + uiMarkShadowY[0] + "/" + uiMarkShadowSize[0]
                    + "/" + (uiMarkPixelShadow ? "pixel" : "soft");
            case "gradient" -> "gradient=" + UiStyleEditor.rgbaToHex(uiMarkGradientA)
                    + "," + UiStyleEditor.rgbaToHex(uiMarkGradientB);
            case "rainbow", "pulse", "wave", "shake", "wiggle" ->
                    base + "=" + uiMarkSpeed[0] + "/" + uiMarkAmount[0];
            default -> base;
        };
    }

    private void renderMarkupSettingsPopup(String popupId, String base, SceneNode node) {
        if (!ImGui.beginPopup(popupId)) return;
        ImGui.text(EditorI18n.t("Параметры эффекта") + " · " + base);
        ImGui.separator();
        switch (base) {
            case "underline" -> {
                inlineColorPicker("underline", EditorI18n.t("Цвет подчёркивания"), uiMarkUnderlineColor);
                popupDrag(EditorI18n.t("Толщина"), "underline-thickness", uiMarkUnderlineThickness, 0.05f, 0.5f, 6f);
                popupDrag(EditorI18n.t("Смещение Y"), "underline-offset", uiMarkUnderlineOffset, 0.05f, -4f, 6f);
            }
            case "outline" -> {
                inlineColorPicker("outline", EditorI18n.t("Цвет обводки"), uiMarkOutlineColor);
                popupDrag(EditorI18n.t("Толщина"), "outline-width", uiMarkOutlineWidth, 0.05f, 0.5f, 8f);
                uiMarkPixelOutline = popupMode("outline", uiMarkPixelOutline);
            }
            case "glow" -> {
                inlineColorPicker("glow", EditorI18n.t("Цвет свечения"), uiMarkGlowColor);
                popupDrag(EditorI18n.t("Радиус"), "glow-size", uiMarkGlowSize, 0.05f, 0.5f, 12f);
                uiMarkPixelGlow = popupMode("glow", uiMarkPixelGlow);
            }
            case "shadow" -> {
                inlineColorPicker("shadow", EditorI18n.t("Цвет тени"), uiMarkShadowColor);
                popupDrag(EditorI18n.t("Смещение X"), "shadow-x", uiMarkShadowX, 0.05f, -12f, 12f);
                popupDrag(EditorI18n.t("Смещение Y"), "shadow-y", uiMarkShadowY, 0.05f, -12f, 12f);
                popupDrag(EditorI18n.t("Размытие / размер"), "shadow-size", uiMarkShadowSize, 0.05f, 0.5f, 12f);
                uiMarkPixelShadow = popupMode("shadow", uiMarkPixelShadow);
            }
            case "gradient" -> {
                inlineColorPicker("gradient-a", EditorI18n.t("Начальный цвет"), uiMarkGradientA);
                inlineColorPicker("gradient-b", EditorI18n.t("Конечный цвет"), uiMarkGradientB);
                java.util.List<String> preview = java.util.List.of(
                        UiStyleEditor.rgbaToHex(uiMarkGradientA),
                        UiStyleEditor.rgbaToHex(uiMarkGradientB));
                UiStyleEditor.drawInlineGradientPreview(preview);
            }
            case "rainbow" -> popupDrag(EditorI18n.t("Скорость"), "rainbow-speed", uiMarkSpeed, 0.02f, 0.05f, 10f);
            case "pulse", "wave", "shake", "wiggle" -> {
                popupDrag(EditorI18n.t("Скорость"), base + "-speed", uiMarkSpeed, 0.02f, 0.05f, 10f);
                popupDrag(EditorI18n.t("Амплитуда"), base + "-amount", uiMarkAmount, 0.02f, 0.05f, 10f);
            }
            default -> EditorInspectorUi.hint(EditorI18n.t("У этого эффекта нет дополнительных параметров"));
        }
        ImGui.spacing();
        if (EditorInspectorUi.primaryButton(EditorI18n.t("Применить параметры"), -1f)) {
            applyMarkup(node, configuredMarkupTag(base), true);
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private static void inlineColorPicker(String id, String label, float[] color) {
        EditorInspectorUi.fieldLabel(label);
        ImGui.setNextItemWidth(240f);
        ImGui.colorEdit4("##mk-setting-color-" + id, color,
                ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.AlphaPreviewHalf
                        | ImGuiColorEditFlags.DisplayHex | ImGuiColorEditFlags.PickerHueBar);
    }

    private static void popupDrag(String label, String id, float[] value,
                                  float speed, float min, float max) {
        EditorInspectorUi.fieldLabel(label);
        ImGui.setNextItemWidth(240f);
        ImGui.dragFloat("##mk-setting-" + id, value, speed, min, max);
    }

    private static boolean popupMode(String id, boolean pixel) {
        EditorInspectorUi.fieldLabel(EditorI18n.t("Режим"));
        if (ImGui.radioButton(EditorI18n.t("Пиксельный") + "##mk-pixel-" + id, pixel)) pixel = true;
        ImGui.sameLine();
        if (ImGui.radioButton(EditorI18n.t("Мягкий") + "##mk-soft-" + id, !pixel)) pixel = false;
        return pixel;
    }

    private static int markUtf8ToChar(String text, int bytePos) {
        if (text == null || bytePos <= 0) return 0;
        int bytes = 0;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            int len = markUtf8Bytes(cp);
            if (bytes + len > bytePos) break;
            bytes += len;
            i += Character.charCount(cp);
            if (bytes == bytePos) break;
        }
        return i;
    }

    private static int markCharToUtf8(String text, int charPos) {
        if (text == null || charPos <= 0) return 0;
        int c = Math.min(charPos, text.length());
        int bytes = 0;
        int i = 0;
        while (i < c) {
            int cp = text.codePointAt(i);
            bytes += markUtf8Bytes(cp);
            i += Character.charCount(cp);
        }
        return bytes;
    }

    private static int markUtf8Bytes(int codePoint) {
        if (codePoint <= 0x7F) return 1;
        if (codePoint <= 0x7FF) return 2;
        if (codePoint <= 0xFFFF) return 3;
        return 4;
    }

    private void stringField(String label, String current, ImString buffer, AssetSetter setter) {
        stringField(label, current, buffer, setter, null, null);
    }

    private void stringField(String label, String current, ImString buffer, AssetSetter setter, String script) {
        stringField(label, current, buffer, setter, null, script);
    }

    private void stringField(String label, String current, ImString buffer, AssetSetter setter,
                             String description, String script) {
        consumeReset(current, v -> setter.set(toResetString(v)));
        buffer.set(current == null ? "" : current);
        if (EditorInspectorUi.inputText(label, "s-" + label, buffer, description, script)) {
            beginMultiSnap();
            setter.set(buffer.get());
            endMultiSnap();
            SceneEditorSession.markDirty();
        }
        EditorInspectorUi.undoOnActivate();
    }

    /** Script tip for a GUI widget property: scene node + live uiUpdate. */
    private static String uiPropApi(SceneNode node, String nodeAssign, String updateKey, String example) {
        String id = node.id == null ? "" : node.id;
        return nodeAssign + "\nuiUpdate(player, \"" + id + "\", \"" + updateKey + "\", " + example + ")";
    }

    private boolean assetCombo(String label, String current, List<String> options,
                               ImString search, AssetSetter setter) {
        return assetCombo(label, current, options, search, setter, null);
    }

    private boolean assetCombo(String label, String current, List<String> options,
                               ImString search, AssetSetter setter, String script) {
        boolean texturePreview = options == EditorAssetIndex.textures()
                || options == EditorAssetIndex.particleTextures();
        boolean particlePreview = options == EditorAssetIndex.particles();
        boolean changed = false;
        boolean mixed = isMulti() && isMixedString(current, setter);
        String shown = current == null || current.isEmpty() ? EditorI18n.t("(не выбрано)") : assetComboLabel(current);
        if (mixed) shown = EditorI18n.t("— разные —");
        consumeReset(current, v -> setter.set(toResetString(v)));
        EditorInspectorUi.fieldLabel(label + (mixed ? " *" : ""), script);
        if (ImGui.beginCombo("##combo-" + label, shown)) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.FrameBg, 0.08f, 0.10f, 0.14f, 1f);
            ImGui.inputTextWithHint("##search-" + label, EditorI18n.t("Поиск…"), search);
            ImGui.popStyleColor();
            String filter = search.get().toLowerCase(Locale.ROOT);

            if (ImGui.selectable(EditorI18n.t("(не выбрано)"), shown.equals(EditorI18n.t("(не выбрано)")))) {
                SceneEditorSession.pushUndo();
                beginMultiSnap();
                setter.set("");
                endMultiSnap();
                SceneEditorSession.markDirty();
                changed = true;
            }
            for (String option : options) {
                if (!assetComboMatches(option, filter)) continue;
                String optionLabel = assetComboLabel(option);
                if (texturePreview) {
                    if (!drawAssetTextureThumb(option, 18f)) {
                        ImGui.dummy(18f, 18f);
                    }
                    ImGui.sameLine();
                } else if (particlePreview) {
                    drawParticleIcon(option, 18f);
                    ImGui.sameLine();
                }
                if (ImGui.selectable(optionLabel, option.equals(current))) {
                    SceneEditorSession.pushUndo();
                    beginMultiSnap();
                    setter.set(option);
                    endMultiSnap();
                    SceneEditorSession.markDirty();
                    changed = true;
                }
                if (texturePreview && ImGui.isItemHovered()) {
                    drawAssetTextureTooltip(option);
                } else if (particlePreview && ImGui.isItemHovered()) {
                    ImGui.beginTooltip();
                    drawParticleIcon(option, 64f);
                    ImGui.sameLine();
                    ImGui.text(option);
                    ImGui.endTooltip();
                }
            }
            ImGui.endCombo();
        }
        EditorInspectorUi.scriptApi(script);
        EditorInspectorUi.finishClipboardField();
        if (texturePreview && current != null && !current.isEmpty() && !mixed) {
            if (drawAssetTextureThumb(current, 96f)) {
                ImGui.sameLine();
                if (EditorAssetIndex.VANILLA_SQUARE_PARTICLE.equals(current)) {
                    ImGui.textDisabled(EditorI18n.t("стандартный квадрат"));
                } else {
                    var tex = EditorTexturePreview.cached(current);
                    if (tex != null) {
                        ImGui.textDisabled(tex.width() + " x " + tex.height());
                    }
                }
            }
        }
        return changed;
    }

    private static String assetComboLabel(String option) {
        if (EditorAssetIndex.VANILLA_SQUARE_PARTICLE.equals(option)) {
            return EditorI18n.t("Квадрат");
        }
        return option;
    }

    private static boolean assetComboMatches(String option, String filter) {
        if (filter == null || filter.isEmpty()) return true;
        if (option.toLowerCase(Locale.ROOT).contains(filter)) return true;
        return assetComboLabel(option).toLowerCase(Locale.ROOT).contains(filter);
    }

    private static boolean drawAssetTextureThumb(String option, float size) {
        Integer glId = vanillaTextureGlId(option);
        if (glId != null) {
            ImGui.image(glId, size, size);
            return true;
        }
        var tex = size > 32f ? EditorTexturePreview.get(option) : EditorTexturePreview.cached(option);
        if (tex == null) return false;
        if (size > 32f) {
            float[] fit = EditorTexturePreview.fit(tex, size, size);
            ImGui.image(tex.glId(), fit[0], fit[1]);
        } else {
            ImGui.image(tex.glId(), size, size);
        }
        return true;
    }

    private static void drawAssetTextureTooltip(String option) {
        Integer glId = vanillaTextureGlId(option);
        if (glId != null) {
            ImGui.beginTooltip();
            ImGui.image(glId, 96f, 96f);
            ImGui.textDisabled(option);
            ImGui.endTooltip();
            return;
        }
        var tex = EditorTexturePreview.get(option);
        if (tex == null) return;
        ImGui.beginTooltip();
        float[] size = EditorTexturePreview.fit(tex, 192f, 192f);
        ImGui.image(tex.glId(), size[0], size[1]);
        ImGui.textDisabled(tex.width() + " x " + tex.height());
        ImGui.endTooltip();
    }

    private static Integer vanillaTextureGlId(String option) {
        if (!EditorAssetIndex.VANILLA_SQUARE_PARTICLE.equals(option)) return null;
        try {
            var loc = net.minecraft.resources.ResourceLocation.tryParse(option);
            if (loc == null) return null;
            var tex = Minecraft.getInstance().getTextureManager().getTexture(loc);
            if (tex == null) return null;
            return tex.getId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void drawParticleIcon(String particleId, float size) {
        var icon = EditorParticleIcons.icon(particleId);
        if (icon != null) {
            ImGui.image(icon.glId(), size, size, icon.u0(), icon.v0(), icon.u1(), icon.v1());
        } else {
            ImGui.dummy(size, size);
        }
    }

    private void drawParticlePreview(String particleId) {
        if (particleId == null || particleId.isBlank()) return;
        if (isMulti() && isMixedField(n -> n.particleType)) {
            ImGui.textDisabled(EditorI18n.t("текстуры различаются"));
            return;
        }
        var icon = EditorParticleIcons.icon(particleId);
        if (icon == null) {
            ImGui.textDisabled(EditorI18n.t("нет превью текстуры"));
            return;
        }
        ImGui.image(icon.glId(), 72f, 72f, icon.u0(), icon.v0(), icon.u1(), icon.v1());
        ImGui.sameLine();
        ImGui.beginGroup();
        ImGui.textDisabled(particleId);
        var def = org.zonarstudio.spraute_engine.registry.CustomParticleRegistry.PARTICLES
                .get(particleId.contains(":") ? particleId.substring(particleId.indexOf(':') + 1) : particleId);
        if (def != null && def.texture != null && !def.texture.isBlank()) {
            ImGui.textDisabled(def.texture);
        }
        ImGui.endGroup();
    }

    private boolean isMixedField(java.util.function.Function<SceneNode, ?> getter) {
        if (!isMulti()) return false;
        Object v0 = getter.apply(editTargets.get(0));
        for (int i = 1; i < editTargets.size(); i++) {
            if (!Objects.equals(v0, getter.apply(editTargets.get(i)))) return true;
        }
        return false;
    }

    private boolean isMixedString(String primary, AssetSetter ignored) {
        if (!isMulti() || editTargets.isEmpty()) return false;
        SceneNode first = editTargets.get(0);
        if (Objects.equals(primary, first.particleType)) return isMixedField(n -> n.particleType);
        if (Objects.equals(primary, first.model)) return isMixedField(n -> n.model);
        if (Objects.equals(primary, first.texture)) return isMixedField(n -> n.texture);
        if (Objects.equals(primary, first.animation)) return isMixedField(n -> n.animation);
        if (Objects.equals(primary, first.animatorFile)) return isMixedField(n -> n.animatorFile);
        if (Objects.equals(primary, first.uiTexture)) return isMixedField(n -> n.uiTexture);
        if (Objects.equals(primary, first.uiItem)) return isMixedField(n -> n.uiItem);
        if (Objects.equals(primary, first.script)) return isMixedField(n -> n.script);
        if (Objects.equals(primary, first.group)) return isMixedField(n -> n.group);
        return false;
    }

    private boolean isMixedFloat(String ignoredLabel, float primary) {
        return false;
    }

    private boolean isMixedInt(int primary, IntSetter ignored) {
        return false;
    }

    private void applyEdit(Runnable mutation) {
        SceneEditorSession.pushUndo();
        beginMultiSnap();
        mutation.run();
        endMultiSnap();
        SceneEditorSession.markDirty();
    }

    private void beginMultiSnap() {
        multiSnap = null;
        if (!isMulti()) return;
        SceneNode primary = multiEditPrimary();
        if (primary == null) return;
        multiSnap = snapshotNode(primary);
    }

    private void endMultiSnap() {
        if (multiSnap == null || !isMulti()) {
            multiSnap = null;
            return;
        }
        SceneNode primary = multiEditPrimary();
        if (primary != null) {
            propagateDiff(multiSnap, primary, editTargets);
        }
        multiSnap = null;
    }

    /** Node the inspector widgets mutate — must match multi-edit snap source. */
    private SceneNode multiEditPrimary() {
        if (editTargets == null || editTargets.isEmpty()) return null;
        SceneNode primary = SceneEditorSession.selectedNode();
        if (primary != null) {
            for (SceneNode n : editTargets) {
                if (n == primary) return primary;
            }
        }
        return editTargets.get(0);
    }

    private static SceneNode snapshotNode(SceneNode src) {
        SceneNode dst = new SceneNode();
        copyPublicFields(src, dst, true);
        return dst;
    }

    private static void propagateDiff(SceneNode before, SceneNode after, List<SceneNode> targets) {
        for (Field f : SceneNode.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (NO_MULTI_PROPAGATE.contains(f.getName())) continue;
            try {
                Object a = f.get(before);
                Object b = f.get(after);
                if (Objects.deepEquals(a, b)) continue;
                for (SceneNode target : targets) {
                    if (target == after) continue;
                    f.set(target, copyFieldValue(b));
                }
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    private static void copyPublicFields(SceneNode src, SceneNode dst, boolean deep) {
        for (Field f : SceneNode.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if ("children".equals(f.getName())) continue;
            try {
                Object v = f.get(src);
                f.set(dst, deep ? copyFieldValue(v) : v);
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object copyFieldValue(Object v) {
        if (v == null) return null;
        if (v instanceof SceneTransform t) return t.copy();
        if (v instanceof List<?> list) return new ArrayList<>(list);
        return v;
    }

    private static float[] typeColor(SceneNodeType type) {
        return switch (type) {
            case SCENE -> new float[]{.41f, .66f, 1f, 1f};
            case PLAYERS -> new float[]{.35f, .78f, 1f, 1f};
            case GLOBAL_WORLD -> new float[]{.4f, .85f, .5f, 1f};
            case LOCAL_WORLD -> new float[]{.95f, .58f, .58f, 1f};
            case PLAYER -> new float[]{.45f, .9f, .75f, 1f};
            case CAMERA -> new float[]{.72f, .55f, 1f, 1f};
            case AUDIO -> new float[]{.35f, .92f, .78f, 1f};
            case SKIN_OVERLAY -> new float[]{.95f, .62f, .42f, 1f};
            case NPC -> new float[]{.46f, .82f, .68f, 1f};
            case MODEL -> new float[]{.95f, .72f, .38f, 1f};
            case BILLBOARD -> new float[]{.45f, .85f, .95f, 1f};
            case HITBOX -> new float[]{1f, .45f, .35f, 1f};
            case PARTICLE -> new float[]{.98f, .72f, .28f, 1f};
            case LIGHT -> new float[]{1f, .92f, .45f, 1f};
            case AUDIO_SOURCE -> new float[]{.45f, .88f, 1f, 1f};
            case POST_PROCESS -> new float[]{.78f, .45f, 1f, 1f};
            case CUSTOM -> new float[]{.55f, .78f, .98f, 1f};
            case RES_BLOCK, RES_ITEM, RES_CRAFT, RES_TAB, RES_DROP, RES_WORLD, RES_PARTICLE, RES_PROJECTILE -> new float[]{.75f, .55f, .95f, 1f};
            case SCRIPT -> new float[]{.72f, .58f, .96f, 1f};
            case VISUAL_SCRIPT -> new float[]{.80f, .40f, 1f, 1f};
            case ANIMATOR -> new float[]{.98f, .62f, .85f, 1f};
            case ROUTE -> new float[]{.25f, .82f, 1f, 1f};
            case ROUTE_POINT -> new float[]{.4f, 1f, .65f, 1f};
            case EMPTY -> new float[]{.72f, .75f, .80f, 1f};
            case CANVAS -> new float[]{.98f, .80f, .35f, 1f};
            case UI_GRID, UI_TEXT, UI_BUTTON, UI_IMAGE, UI_VIDEO, UI_SVG, UI_RECT, UI_INPUT,
                    UI_SCROLL, UI_DIVIDER, UI_ITEM, UI_BLOCK, UI_ENTITY, UI_CROP, UI_GROUP,
                    UI_DRAG, UI_DROPTARGET, UI_SLOT, UI_INVENTORY -> new float[]{.85f, .78f, .55f, 1f};
        };
    }

    private static String typeLabel(SceneNodeType type) {
        return switch (type) {
            case SCENE -> "SCENE";
            case PLAYERS -> "PLAYERS";
            case GLOBAL_WORLD -> "GLOBAL WORLD";
            case LOCAL_WORLD -> "LOCAL WORLD";
            case PLAYER -> "PLAYER";
            case CAMERA -> "CAMERA";
            case AUDIO -> "AUDIO";
            case SKIN_OVERLAY -> "OVERLAY";
            case EMPTY -> "EMPTY";
            case NPC -> "NPC";
            case MODEL -> "MODEL";
            case BILLBOARD -> "BILLBOARD";
            case HITBOX -> "HITBOX";
            case PARTICLE -> "PARTICLE";
            case LIGHT -> "LIGHT";
            case AUDIO_SOURCE -> "WORLD AUDIO";
            case POST_PROCESS -> "POST";
            case CUSTOM -> "CUSTOM";
            case RES_BLOCK -> "BLOCK";
            case RES_ITEM -> "ITEM";
            case RES_CRAFT -> "CRAFT";
            case RES_TAB -> "TAB";
            case RES_DROP -> "DROP";
            case RES_WORLD -> "WORLD";
            case RES_PARTICLE -> "PARTICLE";
            case RES_PROJECTILE -> "PROJECTILE";
            case SCRIPT -> "SCRIPT";
            case VISUAL_SCRIPT -> "VISUAL SCRIPT";
            case ANIMATOR -> "ANIMATOR";
            case ROUTE -> "ROUTE";
            case ROUTE_POINT -> "ROUTE POINT";
            case CANVAS -> "CANVAS";
            case UI_GRID -> "GRID";
            case UI_TEXT -> "TEXT";
            case UI_BUTTON -> "BUTTON";
            case UI_IMAGE -> "IMAGE";
            case UI_VIDEO -> "VIDEO";
            case UI_SVG -> "SVG";
            case UI_RECT -> "PANEL";
            case UI_INPUT -> "INPUT";
            case UI_SCROLL -> "SCROLL";
            case UI_DIVIDER -> "DIVIDER";
            case UI_ITEM, UI_BLOCK -> "ITEM";
            case UI_ENTITY -> EditorI18n.t("Рендер");
            case UI_CROP -> "CROP";
            case UI_GROUP -> "GROUP";
            case UI_DRAG -> "DRAG";
            case UI_DROPTARGET -> "DROP";
            case UI_SLOT -> "SLOT";
            case UI_INVENTORY -> "INVENTORY";
        };
    }
}
