package org.zonarstudio.spraute_engine.client.editor.scene;

import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.client.editor.SprauteEditorState;
import org.zonarstudio.spraute_engine.client.editor.icons.EditorIcons;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeIds;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.SceneRouteStorage;
import org.zonarstudio.spraute_engine.scene.SceneRuntime;
import org.zonarstudio.spraute_engine.scene.SceneTransform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.zonarstudio.spraute_engine.client.editor.EditorI18n;

/**
 * Unity-like hierarchy: recursive tree with drag/drop nesting, context menu,
 * rename popup and capability-aware reparenting.
 */
public final class HierarchyPanel {
    public static final String PAYLOAD_TYPE = "SPRAUTE_SCENE_NODE";
    public static final String SCRIPT_PAYLOAD_TYPE = "SPRAUTE_SCRIPT_ASSET";
    public static final String VSCRIPT_PAYLOAD_TYPE = "SPRAUTE_VSCRIPT_ASSET";
    public static final String PREFAB_PAYLOAD_TYPE = "SPRAUTE_PREFAB_ASSET";
    /** Relative workspace path from the Project explorer (move + drop onto nodes). */
    public static final String PROJECT_PATH_PAYLOAD = "SPRAUTE_PROJECT_PATH";
    public static final String BONE_PAYLOAD_TYPE = "SPRAUTE_GEO_BONE";
    /** Set when a prefab is saved from the Hierarchy so the Project panel can refresh. */
    public static volatile boolean projectNeedsReload;

    private final ImString renameBuffer = new ImString(128);
    private final ImString hierarchySearch = new ImString(96);
    private final ImString newScriptName = new ImString(96);
    private final ImString boneRenameBuffer = new ImString(96);
    private String renameNodeId;
    private String addToNodeId;
    private boolean openNodePickerPopup;
    private String contextNodeId;
    private boolean openContextPopup;
    private String newScriptParentId;
    private boolean openCreateScriptPopup;
    private boolean openCreateVisualScriptPopup;
    private boolean createVisualScriptMode;
    private String createScriptError;
    private SceneNode clipboardNode;
    private java.util.List<SceneNode> clipboardNodes;
    private String boneRenameOwnerId;
    private String boneRenameOldName;
    private boolean openBoneRenamePopup;
    private String boneDeleteOwnerId;
    private String boneDeleteName;
    private boolean boneDeleteChildren;
    private boolean openBoneDeletePopup;
    private String boneEditError;

    public void render() {
        SceneDocument doc = SceneEditorSession.document();
        if (doc == null) {
            ImGui.spacing();
            ImGui.textColored(.72f, .76f, .82f, 1f, EditorI18n.t("СЦЕНА НЕ ОТКРЫТА"));
            ImGui.spacing();
            ImGui.textDisabled(EditorI18n.t("Создайте новую сцену или откройте"));
            ImGui.textDisabled(EditorI18n.t(".scene.json в панели «Проект»."));
            return;
        }

        if (doc.root == null) {
            ImGui.spacing();
            ImGui.textColored(.72f, .76f, .82f, 1f, EditorI18n.t("СЦЕНА НЕ ОТКРЫТА"));
            return;
        }
        // Heal canvases/widgets created without ids (could not be selected/deleted).
        if (SceneNodeIds.ensureAllIds(doc) > 0) {
            SceneEditorSession.markDirty();
        }

        SceneNode selected = SceneEditorSession.selectedNode();
        SceneNode addParent = selected != null && selected.type.acceptsChildren() ? selected : doc.root;
        if (EditorIcons.iconTextButton("plus", EditorI18n.t("Создать"), "hierarchy-create", 14f)) {
            openNodePicker(addParent.id);
        }
        ImGui.sameLine();
        ImGui.textDisabled(countNodes(doc.root) + EditorI18n.t(" объектов")
                + (SceneEditorSession.selectedCount() > 1
                ? EditorI18n.t(" · выбрано ") + SceneEditorSession.selectedCount() : ""));
        ImGui.spacing();
        EditorInspectorUi.hint(EditorI18n.t("Ctrl+клик — добавить · Shift+клик — диапазон"));
        ImGui.spacing();
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.FrameBg, 0.10f, 0.11f, 0.13f, 0.95f);
        ImGui.setNextItemWidth(-1f);
        ImGui.inputTextWithHint("##hierarchy-search", EditorI18n.t("Поиск в иерархии…"), hierarchySearch);
        ImGui.popStyleColor();
        ImGui.spacing();
        String filter = hierarchySearch.get().trim().toLowerCase(java.util.Locale.ROOT);
        renderNode(doc, doc.root, filter, null);

        // Drop zone below the tree: reparent to scene root, or drop a prefab to instantiate.
        ImGui.dummy(ImGui.getContentRegionAvailX(), Math.max(30f, ImGui.getContentRegionAvailY()));
        acceptDropInto(doc, doc.root.id);
        if (ImGui.isItemClicked(0)) {
            SceneEditorSession.clearSelection();
            AnimatorTimelineSession.selectBone(null);
        }
        if (ImGui.isItemClicked(1)) openNodePicker(doc.root.id);

        // Клик по пустому фону иерархии (не по строке/кнопке) снимает выделение.
        if (ImGui.isWindowHovered()
                && ImGui.isMouseClicked(0)
                && !ImGui.isAnyItemHovered()
                && !ImGui.getIO().getKeyCtrl()
                && !ImGui.getIO().getKeyShift()
                && !ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId
                        | imgui.flag.ImGuiPopupFlags.AnyPopupLevel)) {
            SceneEditorSession.clearSelection();
            AnimatorTimelineSession.selectBone(null);
        }

        handleHotkeys(doc);
        renderRenamePopup(doc);
        renderNodePicker(doc);
        renderCreateScriptPopup(doc);
        renderCreateVisualScriptPopup(doc);
        renderBonePopups(doc);
        if (openContextPopup) {
            // Open at the same root ID stack where beginPopup is called. Opening
            // inside treeNode creates a different ImGui ID and the popup is lost.
            ImGui.openPopup(EditorI18n.t("Контекст ноды"));
            openContextPopup = false;
        }
        renderNodeContextMenu(doc);
    }

    private void renderNode(SceneDocument doc, SceneNode node, String filter, TreeGuides parentGuide) {
        if (!filter.isEmpty() && !nodeOrDescendantMatches(node, filter)) return;
        boolean selected = SceneEditorSession.isSelected(node.id);
        boolean hasBones = filter.isEmpty() && nodeHasBones(node);
        int flags = ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (selected) flags |= ImGuiTreeNodeFlags.Selected;
        if (node.children.isEmpty() && !hasBones) flags |= ImGuiTreeNodeFlags.Leaf;
        if (node.type == SceneNodeType.SCENE || node.type == SceneNodeType.PLAYERS
                || node.type == SceneNodeType.PLAYER || node.type == SceneNodeType.CAMERA
                || node.type == SceneNodeType.GLOBAL_WORLD || node.type == SceneNodeType.LOCAL_WORLD) {
            flags |= ImGuiTreeNodeFlags.DefaultOpen;
        }
        if (!filter.isEmpty()) flags |= ImGuiTreeNodeFlags.DefaultOpen;

        float[] tint = switch (node.type) {
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
        // Inactive nodes are grayed out (they do nothing at runtime).
        if (!node.active) tint = new float[]{.48f, .50f, .54f, 1f};
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, tint[0], tint[1], tint[2], tint[3]);
        boolean locked = doc.isEffectivelyLocked(node);
        // Leading spaces reserve room for the Lucide icon drawn over the label.
        boolean open = ImGui.treeNodeEx(node.id, flags, "     " + displayName(node));
        // A world-space canvas is still a CANVAS, but it reads as a panel in the world.
        String icon = org.zonarstudio.spraute_engine.scene.SceneWorldCanvasRuntime.isWorldCanvas(node)
                ? "monitor" : EditorIcons.forNodeType(node.type);
        EditorIcons.drawOverTreeLabel(icon, 14f, tint);
        if (locked) {
            float lockX = ImGui.getItemRectMaxX() - 16f;
            float lockY = ImGui.getItemRectMinY() + (ImGui.getItemRectSizeY() - 12f) * 0.5f;
            var lockTex = EditorIcons.get("lock");
            if (lockTex != null) {
                ImGui.getWindowDrawList().addImage(lockTex.glId(), lockX, lockY, lockX + 12f, lockY + 12f,
                        0f, 0f, 1f, 1f, ImGui.getColorU32(0.85f, 0.70f, 0.35f, 1f));
            }
        }
        ImGui.popStyleColor();
        // Tick from parent trunk to this row — must run before children are drawn.
        if (parentGuide != null) parentGuide.markChild();

        boolean treeClicked = ImGui.isItemClicked();
        boolean treeToggled = ImGui.isItemToggledOpen();
        boolean treeRightClicked = ImGui.isItemClicked(1);

        if (treeClicked && !treeToggled) {
            applyHierarchyClick(doc, node.id);
        }
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
            if (node.type == SceneNodeType.SCRIPT) {
                Path path = scriptPath(node.script);
                if (Files.isRegularFile(path)) SceneEditorSession.requestOpenScript(path);
            } else if (node.type == SceneNodeType.VISUAL_SCRIPT) {
                Path path = visualScriptPath(node.script);
                if (Files.isRegularFile(path)) SceneEditorSession.requestOpenVisualScript(path);
            } else if (node.hasTransform() && !node.type.isUi()) {
                focusEditorCameraOnNode(doc, node);
            }
        }
        if (treeRightClicked) {
            if (!SceneEditorSession.isSelected(node.id)) {
                SceneEditorSession.select(node.id);
            }
            contextNodeId = node.id;
            openContextPopup = true;
        }
        if (node.type == SceneNodeType.SCENE) {
            renderSceneControl(node);
        }

        if (node.type.canReparent() && ImGui.beginDragDropSource()) {
            List<SceneNode> group = SceneEditorSession.isSelected(node.id)
                    && SceneEditorSession.selectedCount() > 1
                    ? SceneEditorSession.selectedReparentableNodes()
                    : List.of();
            if (group.size() > 1) {
                StringBuilder payload = new StringBuilder();
                for (int i = 0; i < group.size(); i++) {
                    if (i > 0) payload.append('\n');
                    payload.append(group.get(i).id);
                }
                ImGui.setDragDropPayload(PAYLOAD_TYPE, payload.toString());
                ImGui.text(group.size() + EditorI18n.t(" объектов"));
                for (int i = 0; i < Math.min(group.size(), 6); i++) {
                    ImGui.textDisabled(displayName(group.get(i)));
                }
                if (group.size() > 6) ImGui.textDisabled("…");
            } else {
                ImGui.setDragDropPayload(PAYLOAD_TYPE, node.id);
                ImGui.text(displayName(node));
            }
            ImGui.endDragDropSource();
        }
        acceptDropAtNode(doc, node);

        if (open) {
            var guide = beginTreeGuides();
            // Copy: context-menu deletes may mutate the list during iteration.
            for (SceneNode child : java.util.List.copyOf(node.children)) {
                renderNode(doc, child, filter, guide);
            }
            if (hasBones) renderBoneTree(node, guide);
            guide.finish();
            ImGui.treePop();
        }
    }

    /**
     * Virtual per-bone sub-tree parsed from the node's .geo.json. Bones are not
     * scene nodes; selection edits either the bind pose in geo or animation keys.
     */
    private void renderBoneTree(SceneNode node, TreeGuides parentGuide) {
        if (!nodeHasBones(node)) return;
        var model = org.zonarstudio.spraute_engine.entity.client.SpModelCache.getOrLoad(node.model);
        int flags = ImGuiTreeNodeFlags.SpanAvailWidth;
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, .60f, .66f, .74f, 1f);
        boolean open = ImGui.treeNodeEx("bones-" + node.id, flags, EditorI18n.t("     Кости (") + model.boneMap.size() + ")");
        EditorIcons.drawOverTreeLabel("bone", 14f, new float[]{.95f, .72f, .38f, 1f});
        ImGui.popStyleColor();
        if (ImGui.beginPopupContextItem("bones-context-" + node.id)) {
            if (ImGui.menuItem(EditorI18n.t("Добавить корневую кость"))) {
                addGeoBone(node, null);
            }
            ImGui.endPopup();
        }
        acceptBoneDrop(node, null);
        if (parentGuide != null) parentGuide.markChild();
        if (open) {
            var guide = beginTreeGuides();
            for (var root : model.rootBones) {
                renderBone(node, root, guide);
            }
            guide.finish();
            ImGui.treePop();
        }
    }

    private void renderBone(SceneNode owner, org.zonarstudio.spraute_engine.core.model.SpBone bone, TreeGuides parentGuide) {
        int flags = ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.SpanAvailWidth;
        boolean selected = bone.name.equals(AnimatorTimelineSession.selectedBone())
                && (owner.id.equals(SceneEditorSession.selectedNodeId())
                || AnimatorTimelineSession.targets(owner.id));
        if (selected) flags |= ImGuiTreeNodeFlags.Selected;
        if (bone.children.isEmpty()) flags |= ImGuiTreeNodeFlags.Leaf;
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, .58f, .74f, .88f, 1f);
        boolean open = ImGui.treeNodeEx("bone-" + owner.id + "-" + bone.name, flags, "     " + bone.name);
        EditorIcons.drawOverTreeLabel("bone", 12f, new float[]{.80f, .78f, .72f, 1f});
        ImGui.popStyleColor();
        boolean boneClicked = ImGui.isItemClicked();
        boolean boneToggled = ImGui.isItemToggledOpen();
        if (ImGui.beginPopupContextItem("bone-context-" + owner.id + "-" + bone.name)) {
            if (ImGui.menuItem(EditorI18n.t("Добавить дочернюю кость"))) {
                addGeoBone(owner, bone.name);
            }
            if (ImGui.menuItem(EditorI18n.t("Переименовать кость…"))) {
                boneEditError = null;
                boneRenameOwnerId = owner.id;
                boneRenameOldName = bone.name;
                boneRenameBuffer.set(bone.name);
                openBoneRenamePopup = true;
            }
            ImGui.separator();
            if (ImGui.menuItem(EditorI18n.t("Удалить кость, оставить дочерние…"))) {
                boneEditError = null;
                boneDeleteOwnerId = owner.id;
                boneDeleteName = bone.name;
                boneDeleteChildren = false;
                openBoneDeletePopup = true;
            }
            if (ImGui.menuItem(EditorI18n.t("Удалить кость с дочерними…"))) {
                boneEditError = null;
                boneDeleteOwnerId = owner.id;
                boneDeleteName = bone.name;
                boneDeleteChildren = true;
                openBoneDeletePopup = true;
            }
            ImGui.endPopup();
        }
        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(BONE_PAYLOAD_TYPE, owner.id + "\n" + bone.name);
            ImGui.text(EditorI18n.t("Переместить кость: ") + bone.name);
            ImGui.endDragDropSource();
        }
        acceptBoneDrop(owner, bone.name);
        if (parentGuide != null) parentGuide.markChild();
        if (boneClicked && !boneToggled) {
            SceneEditorSession.select(owner.id);
            AnimatorTimelineSession.selectBone(owner, bone.name);
        }
        if (open) {
            var guide = beginTreeGuides();
            for (var child : bone.children) {
                renderBone(owner, child, guide);
            }
            guide.finish();
            ImGui.treePop();
        }
    }

    private void addGeoBone(SceneNode owner, String parentName) {
        if (owner == null || owner.model == null || owner.model.isBlank()) return;
        boneEditError = null;
        String name = GeoBoneEditor.uniqueBoneName(owner.model, parentName == null ? "bone" : parentName + "_bone");
        boneEditError = GeoBoneEditor.addBone(owner.model, name, parentName);
        if (boneEditError == null) {
            SceneEditorSession.select(owner.id);
            AnimatorTimelineSession.selectBone(owner, name);
            AnimatorTimelineSession.setRestPoseEditing(true);
        }
    }

    private void acceptBoneDrop(SceneNode targetOwner, String targetBone) {
        if (!ImGui.beginDragDropTarget()) return;
        String payload = ImGui.acceptDragDropPayload(BONE_PAYLOAD_TYPE);
        if (payload != null) {
            String[] parts = payload.split("\n", 2);
            if (parts.length == 2 && targetOwner.id.equals(parts[0])) {
                String boneName = parts[1];
                boneEditError = GeoBoneEditor.reparentBone(targetOwner.model, boneName, targetBone);
                if (boneEditError == null) {
                    SceneEditorSession.select(targetOwner.id);
                    AnimatorTimelineSession.selectBone(targetOwner, boneName);
                    AnimatorTimelineSession.setRestPoseEditing(true);
                }
            }
        }
        ImGui.endDragDropTarget();
    }

    private void renderBonePopups(SceneDocument doc) {
        if (openBoneRenamePopup) {
            ImGui.openPopup(EditorI18n.t("Переименовать кость###geo-bone-rename"));
            openBoneRenamePopup = false;
        }
        if (ImGui.beginPopupModal(EditorI18n.t("Переименовать кость###geo-bone-rename"),
                imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.textDisabled(boneRenameOldName == null ? "" : boneRenameOldName);
            ImGui.setNextItemWidth(320f);
            ImGui.inputText("##geo-bone-name", boneRenameBuffer);
            if (boneEditError != null) ImGui.textColored(1f, .35f, .3f, 1f, boneEditError);
            if (ImGui.button(EditorI18n.t("Переименовать"))) {
                SceneNode owner = doc.findNode(boneRenameOwnerId);
                if (owner != null) {
                    String next = boneRenameBuffer.get().trim();
                    boneEditError = GeoBoneEditor.renameBone(owner.model, boneRenameOldName, next);
                    if (boneEditError == null) {
                        AnimatorTimelineSession.selectBone(owner, sanitizeBoneSelection(next));
                        AnimatorTimelineSession.setRestPoseEditing(true);
                        ImGui.closeCurrentPopup();
                    }
                }
            }
            ImGui.sameLine();
            if (ImGui.button(EditorI18n.t("Отмена"))) {
                boneEditError = null;
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }

        if (openBoneDeletePopup) {
            ImGui.openPopup(EditorI18n.t("Удалить кость###geo-bone-delete"));
            openBoneDeletePopup = false;
        }
        if (ImGui.beginPopupModal(EditorI18n.t("Удалить кость###geo-bone-delete"),
                imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.textWrapped(boneDeleteChildren
                    ? EditorI18n.t("Кость и вся дочерняя ветка будут удалены из .geo.json.")
                    : EditorI18n.t("Кость будет удалена, а дочерние кости перейдут к её родителю."));
            ImGui.textColored(1f, .65f, .25f, 1f, boneDeleteName == null ? "" : boneDeleteName);
            if (boneEditError != null) ImGui.textColored(1f, .35f, .3f, 1f, boneEditError);
            if (ImGui.button(EditorI18n.t("Удалить"))) {
                SceneNode owner = doc.findNode(boneDeleteOwnerId);
                if (owner != null) {
                    boneEditError = GeoBoneEditor.deleteBone(owner.model, boneDeleteName, boneDeleteChildren);
                    if (boneEditError == null) {
                        AnimatorTimelineSession.selectBone(null);
                        ImGui.closeCurrentPopup();
                    }
                }
            }
            ImGui.sameLine();
            if (ImGui.button(EditorI18n.t("Отмена"))) {
                boneEditError = null;
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private static String sanitizeBoneSelection(String requested) {
        String value = requested == null ? "" : requested.trim();
        value = value.replaceAll("[^A-Za-z0-9_.-]+", "_").replaceAll("^_+|_+$", "");
        return value.isEmpty() ? "bone" : value;
    }

    private static boolean nodeHasBones(SceneNode node) {
        if (node.type != SceneNodeType.NPC && node.type != SceneNodeType.MODEL) return false;
        if (node.model == null || node.model.isEmpty()) return false;
        var model = org.zonarstudio.spraute_engine.entity.client.SpModelCache.getOrLoad(node.model);
        return model != null && (!model.rootBones.isEmpty() || GeoBoneEditor.resolveGeoFile(node.model) != null);
    }

    /** Indent guides like the Project panel: vertical trunk + tick to each child. */
    private static TreeGuides beginTreeGuides() {
        return new TreeGuides(
                ImGui.getWindowDrawList(),
                ImGui.getCursorScreenPosX() - 16f,
                ImGui.getCursorScreenPosY() - 1f,
                ImGui.getColorU32(1f, 1f, 1f, 0.13f)
        );
    }

    private static final class TreeGuides {
        private final imgui.ImDrawList dl;
        private final float x;
        private final float top;
        private final int col;
        private float bottom;

        TreeGuides(imgui.ImDrawList dl, float x, float top, int col) {
            this.dl = dl;
            this.x = x;
            this.top = top;
            this.col = col;
            this.bottom = top;
        }

        void markChild() {
            float cy = (ImGui.getItemRectMinY() + ImGui.getItemRectMaxY()) * 0.5f;
            dl.addLine(x, cy, x + 9f, cy, col, 1f);
            bottom = cy;
        }

        void finish() {
            if (bottom > top) dl.addLine(x, top, x, bottom, col, 1f);
        }
    }

    private static void renderSceneControl(SceneNode node) {
        SceneDocument document = SceneEditorSession.document();
        if (document == null) return;
        String sceneId = document.id;
        boolean running = SceneRuntime.isActive(sceneId)
                || org.zonarstudio.spraute_engine.client.ClientSceneRuntime.isActive(sceneId);
        float btn = 22f;
        ImGui.sameLine();
        ImGui.setCursorPosX(Math.max(ImGui.getCursorPosX(),
                ImGui.getWindowContentRegionMaxX() - btn - 4f));
        if (EditorIcons.iconButton(
                running ? "circle-stop" : "play",
                "scene-control-" + node.id,
                16f,
                running ? EditorI18n.t("Остановить сцену, её объекты и скрипты") : EditorI18n.t("Запустить сцену"),
                running ? 1f : 0.45f,
                running ? 0.45f : 0.85f,
                running ? 0.40f : 0.55f,
                1f)) {
            SceneEditorSession.requestPlayToggle();
        }
    }

    private void renderNodeContextMenu(SceneDocument doc) {
        if (!ImGui.beginPopup(EditorI18n.t("Контекст ноды"))) return;
        SceneNode node = contextNodeId == null ? null : doc.findNode(contextNodeId);
        if (node == null) {
            ImGui.endPopup();
            return;
        }

        int selCount = SceneEditorSession.selectedCount();
        boolean multi = selCount > 1;
        java.util.List<SceneNode> actionRoots = SceneEditorSession.selectedRootNodes();
        java.util.List<SceneNode> movableRoots = actionRoots.stream()
                .filter(n -> n.type.canReparent()).toList();
        java.util.List<SceneNode> deletable = SceneEditorSession.selectedDeletableRoots();
        boolean systemNode = node.type == SceneNodeType.PLAYERS || node.type == SceneNodeType.PLAYER
                || node.type == SceneNodeType.CAMERA || node.type == SceneNodeType.AUDIO
                || node.type == SceneNodeType.POST_PROCESS
                || node.type == SceneNodeType.GLOBAL_WORLD || node.type == SceneNodeType.LOCAL_WORLD;
        // System nodes are normally locked down (no add/paste), but PLAYER, GLOBAL_WORLD
        // and LOCAL_WORLD are containers whose whole purpose is to hold child content, so
        // they still need "Add child node..." / "Paste into" even though they can't be
        // renamed/reparented/deleted themselves.
        boolean playerHost = node.type == SceneNodeType.PLAYER
                || node.type == SceneNodeType.GLOBAL_WORLD || node.type == SceneNodeType.LOCAL_WORLD;

        if (multi) {
            ImGui.textDisabled(selCount + EditorI18n.t(" выбрано"));
        } else {
            ImGui.textDisabled(displayName(node));
            String linked = linkedPrefabId(node);
            if (linked != null) {
                ImGui.textDisabled(EditorI18n.t("Префаб: ") + linked);
            }
        }
        ImGui.separator();
        if (!multi && node.type == SceneNodeType.SCRIPT && ImGui.menuItem(EditorI18n.t("Открыть скрипт"))) {
            Path path = scriptPath(node.script);
            if (Files.isRegularFile(path)) SceneEditorSession.requestOpenScript(path);
        }
        if (!multi && node.type == SceneNodeType.VISUAL_SCRIPT && ImGui.menuItem(EditorI18n.t("Открыть визуальный скрипт"))) {
            Path path = visualScriptPath(node.script);
            if (Files.isRegularFile(path)) SceneEditorSession.requestOpenVisualScript(path);
        }
        if ((!systemNode || playerHost) && node.type.acceptsChildren()
                && ImGui.menuItem(EditorI18n.t("Добавить дочернюю ноду..."))) {
            ImGui.closeCurrentPopup();
            openNodePicker(node.id);
        }
        if (ImGui.menuItem(multi ? EditorI18n.t("Копировать (") + actionRoots.size() + ")"
                        : EditorI18n.t("Копировать"), "Ctrl+C", false, !systemNode)) {
            copySelectedNodes();
        }
        boolean canPaste = (!systemNode || playerHost) && canPasteInto(node);
        if (ImGui.menuItem(EditorI18n.t("Вставить внутрь"), "Ctrl+V", false, canPaste)) {
            pasteInto(doc, node);
        }
        boolean canDup = !systemNode && !actionRoots.isEmpty();
        if (ImGui.menuItem(multi ? EditorI18n.t("Дублировать (") + actionRoots.size() + ")" : EditorI18n.t("Дублировать"),
                "Ctrl+D", false, canDup)) {
            duplicateSelectedNodes(doc);
        }
        String linkedPrefab = linkedPrefabId(node);
        if (!multi && linkedPrefab != null
                && ImGui.menuItem(EditorI18n.t("Сохранить префаб") + " (" + linkedPrefab + ")")) {
            if (savePrefabChanges(doc, node.id) != null) projectNeedsReload = true;
        }
        if (!multi && node.type != SceneNodeType.SCENE && !systemNode
                && ImGui.menuItem(EditorI18n.t("Сохранить как префаб"))) {
            if (saveNodeAsPrefab(doc, node.id) != null) projectNeedsReload = true;
        }
        if (!actionRoots.isEmpty() && !systemNode) {
            boolean anyLocked = actionRoots.stream().anyMatch(n -> n.locked);
            String lockLabel = multi
                    ? (anyLocked ? EditorI18n.t("Разблокировать (") + actionRoots.size() + ")" : EditorI18n.t("Заблокировать (") + actionRoots.size() + ")")
                    : (node.locked ? EditorI18n.t("Разблокировать (выбор в мире)") : EditorI18n.t("Заблокировать (выбор в мире)"));
            if (ImGui.menuItem(lockLabel)) {
                SceneEditorSession.pushUndo();
                boolean target = !anyLocked;
                for (SceneNode n : actionRoots) n.locked = target;
                SceneEditorSession.markDirty();
            }
        }
        if (!multi && !systemNode && ImGui.menuItem(EditorI18n.t("Переименовать"), "F2")) {
            beginRename(node);
        }
        if (!movableRoots.isEmpty()) {
            if (canMoveSiblings(doc, movableRoots, -1)
                    && ImGui.menuItem(multi ? EditorI18n.t("Переместить выше (") + movableRoots.size() + ")" : EditorI18n.t("Переместить выше"))) {
                SceneEditorSession.pushUndo();
                moveSelectedSiblings(doc, movableRoots, -1);
            }
            if (canMoveSiblings(doc, movableRoots, +1)
                    && ImGui.menuItem(multi ? EditorI18n.t("Переместить ниже (") + movableRoots.size() + ")" : EditorI18n.t("Переместить ниже"))) {
                SceneEditorSession.pushUndo();
                moveSelectedSiblings(doc, movableRoots, +1);
            }
        }
        if (!deletable.isEmpty()) {
            ImGui.separator();
            if (ImGui.menuItem(multi ? EditorI18n.t("Удалить (") + deletable.size() + ")" : EditorI18n.t("Удалить"), "Delete")) {
                deleteSelectedNodes(doc);
            }
        }
        ImGui.endPopup();
    }

    /** Keyboard shortcuts matching the context-menu labels (Ctrl+C/V/D, F2, Delete). */
    private void handleHotkeys(SceneDocument doc) {
        if (ImGui.getIO().getWantTextInput()) return;
        if (renameNodeId != null || openCreateScriptPopup || openCreateVisualScriptPopup) return;
        if (ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId
                | imgui.flag.ImGuiPopupFlags.AnyPopupLevel)) return;

        SceneNode selected = SceneEditorSession.selectedNode();
        if (selected == null) return;
        String selectedBone = AnimatorTimelineSession.selectedBone();
        if (selectedBone != null && AnimatorTimelineSession.targets(selected.id)) {
            if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.F2)) {
                boneEditError = null;
                boneRenameOwnerId = selected.id;
                boneRenameOldName = selectedBone;
                boneRenameBuffer.set(selectedBone);
                openBoneRenamePopup = true;
            }
            if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.Delete)) {
                boneEditError = null;
                boneDeleteOwnerId = selected.id;
                boneDeleteName = selectedBone;
                boneDeleteChildren = true;
                openBoneDeletePopup = true;
            }
            return;
        }

        boolean ctrl = ImGui.getIO().getKeyCtrl();
        boolean timelineOwnsClipboard = AnimatorTimelineSession.isTimelineFocused();
        boolean visualOwnsClipboard = VisualScriptEditor.claimsClipboardHotkeys();

        if (ctrl && ImGui.isKeyPressed(imgui.flag.ImGuiKey.C) && !timelineOwnsClipboard && !visualOwnsClipboard) {
            copySelectedNodes();
        }
        if (ctrl && ImGui.isKeyPressed(imgui.flag.ImGuiKey.V) && !timelineOwnsClipboard && !visualOwnsClipboard) {
            SceneNode target = pasteTarget(doc, selected);
            if (target != null) pasteInto(doc, target);
        }
        if (ctrl && ImGui.isKeyPressed(imgui.flag.ImGuiKey.D) && !visualOwnsClipboard) {
            duplicateSelectedNodes(doc);
        }
        if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.F2) && SceneEditorSession.selectedCount() == 1) {
            beginRename(selected);
        }
        if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.Delete) && !visualOwnsClipboard) {
            deleteSelectedNodes(doc);
        }
    }

    private static boolean canMoveSiblings(SceneDocument doc, java.util.List<SceneNode> nodes, int delta) {
        for (SceneNode n : nodes) {
            SceneNode parent = doc.findParent(n.id);
            if (parent == null) continue;
            int index = parent.children.indexOf(n);
            if (index < 0) continue;
            int next = index + delta;
            if (next >= 0 && next < parent.children.size()) return true;
        }
        return false;
    }

    private static void moveSelectedSiblings(SceneDocument doc, java.util.List<SceneNode> nodes, int delta) {
        boolean moved = false;
        java.util.Set<SceneNode> affectedRoutes = new java.util.LinkedHashSet<>();
        java.util.Set<SceneNode> selected = new java.util.HashSet<>(nodes);
        java.util.List<SceneNode> ordered = new java.util.ArrayList<>(nodes);
        java.util.Map<String, Integer> hierarchyOrder = new java.util.HashMap<>();
        int[] order = {0};
        doc.walk((node, ignored) -> hierarchyOrder.put(node.id, order[0]++));
        ordered.sort(java.util.Comparator.comparingInt(
                node -> hierarchyOrder.getOrDefault(node.id, Integer.MAX_VALUE)));
        if (delta > 0) java.util.Collections.reverse(ordered);
        for (SceneNode n : ordered) {
            SceneNode parent = doc.findParent(n.id);
            if (parent == null) continue;
            int index = parent.children.indexOf(n);
            if (index < 0) continue;
            int next = index + delta;
            if (next < 0 || next >= parent.children.size()) continue;
            if (selected.contains(parent.children.get(next))) continue;
            java.util.Collections.swap(parent.children, index, next);
            moved = true;
            if (parent.type == SceneNodeType.ROUTE) affectedRoutes.add(parent);
        }
        if (moved) {
            SceneEditorSession.markDirty();
            for (SceneNode n : nodes) {
                ClientPlayerSkinEdits.pushIfSkinRelated(doc, n);
            }
            for (SceneNode route : affectedRoutes) SceneEditorSession.syncLiveNode(route.id);
        }
    }

    private void copySelectedNodes() {
        java.util.List<SceneNode> roots = new java.util.ArrayList<>(SceneEditorSession.selectedRootNodes());
        roots.removeIf(HierarchyPanel::isSystemPlayerNode);
        if (roots.isEmpty()) return;
        if (roots.size() == 1) {
            clipboardNode = roots.get(0).deepCopyWithNewIds();
            clipboardNodes = null;
        } else {
            clipboardNodes = new java.util.ArrayList<>();
            for (SceneNode n : roots) clipboardNodes.add(n.deepCopyWithNewIds());
            clipboardNode = null;
        }
    }

    private void duplicateSelectedNodes(SceneDocument doc) {
        java.util.List<SceneNode> roots = new java.util.ArrayList<>(SceneEditorSession.selectedRootNodes());
        roots.removeIf(HierarchyPanel::isSystemPlayerNode);
        if (roots.isEmpty()) return;
        SceneEditorSession.pushUndo();
        java.util.List<String> newIds = new java.util.ArrayList<>();
        try {
            for (SceneNode node : roots) {
                SceneNode parent = doc.findParent(node.id);
                if (parent == null) continue;
                SceneNode copy = node.deepCopyWithNewIds();
                copy.name = uniqueName(doc, node.name);
                SceneNodeIds.assignTree(doc, copy);
                // Nudge world objects so lights/hitboxes don't share the same block cell.
                nudgeDuplicate(doc, parent, copy);
                parent.children.add(copy);
                newIds.add(copy.id);
            }
        } finally {
            SceneNodeIds.clearPending();
        }
        if (!newIds.isEmpty()) {
            SceneEditorSession.selectMultiple(newIds);
            SceneEditorSession.markDirty();
            for (String id : newIds) {
                ClientPlayerSkinEdits.pushIfSkinRelated(doc, doc.findNode(id));
            }
        }
    }

    /** Offset a duplicated world node by +1 X so stacked lights don't fight over one cell. */
    private static void nudgeDuplicate(SceneDocument doc, SceneNode parent, SceneNode copy) {
        if (copy == null || parent == null) return;
        if (!copy.hasTransform() || copy.type.isUi()) return;
        var parentWorld = parent.type == SceneNodeType.SCENE ? null : doc.worldTransform(parent.id);
        var world = org.zonarstudio.spraute_engine.scene.SceneTransform.composeWorld(parentWorld, copy.transform);
        world.px += 1.0;
        copy.transform = org.zonarstudio.spraute_engine.scene.SceneTransform.toLocal(parentWorld, world);
    }

    private void deleteSelectedNodes(SceneDocument doc) {
        java.util.List<SceneNode> targets = SceneEditorSession.selectedDeletableRoots();
        if (targets.isEmpty()) return;
        java.util.List<SceneNode> skinParents = new java.util.ArrayList<>();
        for (SceneNode n : targets) {
            if (n.type != SceneNodeType.SKIN_OVERLAY) continue;
            SceneNode parent = doc.findParent(n.id);
            if (parent != null && parent.type == SceneNodeType.PLAYER && !skinParents.contains(parent)) {
                skinParents.add(parent);
            }
        }
        if (SceneEditorSession.isLiveRuntime()) {
            for (SceneNode n : targets) {
                if (n.type == SceneNodeType.SKIN_OVERLAY) {
                    SceneNode parent = doc.findParent(n.id);
                    if (parent != null) parent.children.remove(n);
                } else {
                    SceneEditorSession.removeLiveNode(n.id);
                }
            }
            SceneEditorSession.pruneSelection();
        } else {
            SceneEditorSession.pushUndo();
            for (SceneNode n : targets) {
                SceneNode parent = doc.findParent(n.id);
                if (parent != null) parent.children.remove(n);
            }
            SceneEditorSession.pruneSelection();
            SceneEditorSession.markDirty();
        }
        for (SceneNode parent : skinParents) {
            ClientPlayerSkinEdits.pushPlayer(doc, parent);
        }
    }

    private boolean canPasteInto(SceneNode node) {
        if (node == null || !node.type.acceptsChildren()) return false;
        if (clipboardNode != null) {
            return node.type.allowedChildTypes().contains(clipboardNode.type);
        }
        if (clipboardNodes != null && !clipboardNodes.isEmpty()) {
            for (SceneNode n : clipboardNodes) {
                if (!node.type.allowedChildTypes().contains(n.type)) return false;
            }
            return true;
        }
        return false;
    }

    /** Selected node if it accepts the clipboard, otherwise its parent, otherwise null. */
    private SceneNode pasteTarget(SceneDocument doc, SceneNode selected) {
        if (canPasteInto(selected)) return selected;
        SceneNode parent = doc.findParent(selected.id);
        if (canPasteInto(parent)) return parent;
        return canPasteInto(doc.root) ? doc.root : null;
    }

    private void pasteInto(SceneDocument doc, SceneNode parent) {
        if (!canPasteInto(parent)) return;
        SceneEditorSession.pushUndo();
        java.util.List<String> pastedIds = new java.util.ArrayList<>();
        try {
            if (clipboardNodes != null && !clipboardNodes.isEmpty()) {
                for (SceneNode template : clipboardNodes) {
                    SceneNode pasted = template.deepCopyWithNewIds();
                    pasted.name = uniqueName(doc, template.name);
                    SceneNodeIds.assignTree(doc, pasted);
                    nudgeDuplicate(doc, parent, pasted);
                    if (parent.type == SceneNodeType.PLAYER && pasted.type == SceneNodeType.CAMERA) {
                        org.zonarstudio.spraute_engine.scene.PlayerCameraNodes.installCamera(parent, pasted);
                        ClientScenePlayerNodes.pushPlayerCamera(doc, pasted);
                    } else {
                        resetForRouteLayout(parent, pasted);
                        parent.children.add(pasted);
                    }
                    pastedIds.add(pasted.id);
                }
            } else if (clipboardNode != null) {
                SceneNode pasted = clipboardNode.deepCopyWithNewIds();
                pasted.name = uniqueName(doc, clipboardNode.name);
                SceneNodeIds.assignTree(doc, pasted);
                nudgeDuplicate(doc, parent, pasted);
                if (parent.type == SceneNodeType.PLAYER && pasted.type == SceneNodeType.CAMERA) {
                    org.zonarstudio.spraute_engine.scene.PlayerCameraNodes.installCamera(parent, pasted);
                    ClientScenePlayerNodes.pushPlayerCamera(doc, pasted);
                } else {
                    resetForRouteLayout(parent, pasted);
                    parent.children.add(pasted);
                }
                pastedIds.add(pasted.id);
            }
        } finally {
            SceneNodeIds.clearPending();
        }
        if (!pastedIds.isEmpty()) {
            SceneEditorSession.selectMultiple(pastedIds);
            SceneEditorSession.markDirty();
            ClientPlayerSkinEdits.pushIfSkinRelated(doc, parent);
            for (String id : pastedIds) {
                ClientPlayerSkinEdits.pushIfSkinRelated(doc, doc.findNode(id));
            }
        }
    }

    private void beginRename(SceneNode node) {
        if (node == null || isSystemPlayerNode(node)) return;
        renameNodeId = node.id;
        renameBuffer.set(node.name);
    }

    private static boolean isSystemPlayerNode(SceneNode node) {
        return node != null && (node.type == SceneNodeType.PLAYERS || node.type == SceneNodeType.PLAYER
                || node.type == SceneNodeType.CAMERA || node.type == SceneNodeType.AUDIO
                || node.type == SceneNodeType.POST_PROCESS
                || node.type == SceneNodeType.GLOBAL_WORLD || node.type == SceneNodeType.LOCAL_WORLD);
    }

    private enum NodeDrop { BEFORE, INTO, AFTER }

    /** Row target: upper/lower quarters reorder siblings; the middle nests into the node. */
    private void acceptDropAtNode(SceneDocument doc, SceneNode target) {
        if (!ImGui.beginDragDropTarget()) return;
        float minY = ImGui.getItemRectMinY();
        float maxY = ImGui.getItemRectMaxY();
        float mouseY = ImGui.getMousePosY();
        float edge = Math.max(3f, (maxY - minY) * 0.27f);
        NodeDrop placement = target.type == SceneNodeType.SCENE
                ? NodeDrop.INTO
                : mouseY < minY + edge ? NodeDrop.BEFORE
                : mouseY > maxY - edge ? NodeDrop.AFTER
                : NodeDrop.INTO;
        drawDropIndicator(placement, minY, maxY);

        String draggedPayload = ImGui.acceptDragDropPayload(PAYLOAD_TYPE);
        if (draggedPayload != null) {
            moveDroppedNodes(doc, draggedPayload, target, placement);
        }
        if (placement == NodeDrop.INTO) acceptAssetPayloads(doc, target.id);
        ImGui.endDragDropTarget();
    }

    /** Background/root target always nests. */
    private void acceptDropInto(SceneDocument doc, String targetId) {
        if (!ImGui.beginDragDropTarget()) return;
        String draggedPayload = ImGui.acceptDragDropPayload(PAYLOAD_TYPE);
        SceneNode target = doc.findNode(targetId);
        if (draggedPayload != null && target != null) {
            moveDroppedNodes(doc, draggedPayload, target, NodeDrop.INTO);
        }
        acceptAssetPayloads(doc, targetId);
        ImGui.endDragDropTarget();
    }

    private void acceptAssetPayloads(SceneDocument doc, String targetId) {
        String scriptName = ImGui.acceptDragDropPayload(SCRIPT_PAYLOAD_TYPE);
        if (scriptName != null) {
            SceneNode parent = doc.findNode(targetId);
            if (parent != null && parent.type.acceptsChildren()
                    && parent.type.allowedChildTypes().contains(SceneNodeType.SCRIPT)) {
                addScriptNode(doc, parent, scriptName);
            }
        }
        String vscriptName = ImGui.acceptDragDropPayload(VSCRIPT_PAYLOAD_TYPE);
        if (vscriptName != null) {
            SceneNode parent = doc.findNode(targetId);
            if (parent != null && parent.type.acceptsChildren()
                    && parent.type.allowedChildTypes().contains(SceneNodeType.VISUAL_SCRIPT)) {
                addVisualScriptNode(doc, parent, vscriptName);
            }
        }
        String prefabName = ImGui.acceptDragDropPayload(PREFAB_PAYLOAD_TYPE);
        if (prefabName != null) {
            instantiatePrefab(prefabName, targetId);
        }
        String projectRel = ImGui.acceptDragDropPayload(PROJECT_PATH_PAYLOAD);
        if (projectRel != null) {
            acceptProjectPath(doc, targetId, projectRel);
        }
    }

    private void acceptProjectPath(SceneDocument doc, String targetId, String rel) {
        if (rel == null || rel.isBlank()) return;
        String path = rel.replace('\\', '/');
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".sprv")) {
            String name = projectScriptAssetName(path);
            SceneNode parent = doc.findNode(targetId);
            if (parent != null && parent.type.acceptsChildren()
                    && parent.type.allowedChildTypes().contains(SceneNodeType.VISUAL_SCRIPT)) {
                addVisualScriptNode(doc, parent, name);
            }
        } else if (lower.endsWith(".spr")) {
            String name = projectScriptAssetName(path);
            SceneNode parent = doc.findNode(targetId);
            if (parent != null && parent.type.acceptsChildren()
                    && parent.type.allowedChildTypes().contains(SceneNodeType.SCRIPT)) {
                addScriptNode(doc, parent, name);
            }
        } else if (lower.endsWith(".prefab.json")) {
            instantiatePrefab(projectPrefabName(path), targetId);
        }
    }

    private static String projectScriptAssetName(String rel) {
        String name = rel.replace('\\', '/');
        if (name.startsWith("scripts/")) name = name.substring("scripts/".length());
        if (name.endsWith(".sprv")) name = name.substring(0, name.length() - 5);
        else if (name.endsWith(".spr")) name = name.substring(0, name.length() - 4);
        if (name.startsWith("plugins/")) {
            int slash = name.lastIndexOf('/');
            if (slash >= 0) name = name.substring(slash + 1);
        }
        return name;
    }

    private static String projectPrefabName(String rel) {
        String name = rel.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        String ext = org.zonarstudio.spraute_engine.scene.PrefabRepository.EXTENSION;
        if (name.endsWith(ext)) name = name.substring(0, name.length() - ext.length());
        return name;
    }

    private static void drawDropIndicator(NodeDrop placement, float minY, float maxY) {
        var draw = ImGui.getWindowDrawList();
        float minX = ImGui.getItemRectMinX();
        float maxX = ImGui.getItemRectMaxX();
        int color = ImGui.getColorU32(0.30f, 0.72f, 1f, 0.95f);
        if (placement == NodeDrop.BEFORE) {
            draw.addLine(minX, minY, maxX, minY, color, 2.5f);
        } else if (placement == NodeDrop.AFTER) {
            draw.addLine(minX, maxY, maxX, maxY, color, 2.5f);
        } else {
            draw.addRect(minX, minY, maxX, maxY, color, 3f, 0, 1.5f);
        }
    }

    private void moveDroppedNodes(SceneDocument doc, String payload, SceneNode target, NodeDrop placement) {
        if (doc == null || payload == null || target == null || isPayloadId(payload, target.id)) return;
        SceneNode newParent;
        int insertion;
        if (placement == NodeDrop.INTO) {
            newParent = target;
            insertion = target.children.size();
        } else {
            newParent = doc.findParent(target.id);
            if (newParent == null) return;
            int targetIndex = newParent.children.indexOf(target);
            if (targetIndex < 0) return;
            insertion = targetIndex + (placement == NodeDrop.AFTER ? 1 : 0);
        }

        String before = org.zonarstudio.spraute_engine.scene.SceneRepository.toJson(doc);
        java.util.List<SceneNode> skinPlayers = new java.util.ArrayList<>();
        java.util.Set<SceneNode> affectedRoutes = new java.util.LinkedHashSet<>();
        if (newParent.type == SceneNodeType.PLAYER) skinPlayers.add(newParent);
        if (newParent.type == SceneNodeType.ROUTE) affectedRoutes.add(newParent);
        for (String raw : payload.split("\n")) {
            if (raw == null || raw.isBlank()) continue;
            SceneNode oldParent = doc.findParent(raw.trim());
            if (oldParent != null && oldParent.type == SceneNodeType.PLAYER && !skinPlayers.contains(oldParent)) {
                skinPlayers.add(oldParent);
            }
            if (oldParent != null && oldParent.type == SceneNodeType.ROUTE) affectedRoutes.add(oldParent);
        }

        boolean moved = false;
        for (String raw : payload.split("\n")) {
            if (raw == null || raw.isBlank()) continue;
            String id = raw.trim();
            if (doc.move(id, newParent.id, insertion)) {
                moved = true;
                SceneNode movedNode = doc.findNode(id);
                int actual = movedNode == null ? -1 : newParent.children.indexOf(movedNode);
                insertion = actual < 0 ? insertion + 1 : actual + 1;
            }
        }
        if (!moved) return;

        SceneEditorSession.pushUndoJson(before);
        SceneEditorSession.markDirty();
        for (SceneNode player : skinPlayers) ClientPlayerSkinEdits.pushPlayer(doc, player);
        for (SceneNode route : affectedRoutes) {
            SceneEditorSession.syncLiveNode(route.id);
        }
    }

    private static boolean isPayloadId(String payload, String id) {
        if (payload == null || id == null) return false;
        for (String part : payload.split("\n")) {
            if (id.equals(part.trim())) return true;
        }
        return false;
    }

    /** Inserts a fresh prefab instance under targetId (falls back to the scene root). */
    public static void instantiatePrefab(String prefabName, String targetId) {
        if (org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.isRemote()) {
            String sane = org.zonarstudio.spraute_engine.scene.PrefabRepository.sanitize(prefabName);
            String rel = "prefabs/" + sane + org.zonarstudio.spraute_engine.scene.PrefabRepository.EXTENSION;
            org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.ensureLocal(rel,
                    () -> instantiatePrefabLocal(prefabName, targetId));
            return;
        }
        instantiatePrefabLocal(prefabName, targetId);
    }

    private static void instantiatePrefabLocal(String prefabName, String targetId) {
        SceneDocument doc = SceneEditorSession.document();
        if (doc == null) return;
        SceneNode fresh = org.zonarstudio.spraute_engine.scene.PrefabRepository
                .instantiate(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get(), prefabName);
        if (fresh == null) return;
        SceneNode dropTarget = targetId == null ? null : doc.findNode(targetId);
        SceneNode playerHost = playerAncestor(doc, dropTarget);
        SceneNode cameraPrefab = org.zonarstudio.spraute_engine.scene.PlayerCameraNodes.findCameraIn(fresh);
        // Dropping a camera prefab onto a player (or its CAMERA) replaces that player's camera.
        if (playerHost != null && cameraPrefab != null) {
            SceneEditorSession.pushUndo();
            try {
                SceneNodeIds.assignTree(doc, fresh);
                if (cameraPrefab != fresh) {
                    org.zonarstudio.spraute_engine.scene.PlayerCameraNodes.detachInTree(fresh, cameraPrefab);
                }
                if (cameraPrefab.name == null || cameraPrefab.name.isBlank()) {
                    cameraPrefab.name = prefabName;
                }
                org.zonarstudio.spraute_engine.scene.PlayerCameraNodes.installCamera(playerHost, cameraPrefab);
            } finally {
                SceneNodeIds.clearPending();
            }
            SceneEditorSession.select(cameraPrefab.id);
            SceneEditorSession.markDirty();
            ClientScenePlayerNodes.pushPlayerCamera(doc, cameraPrefab);
            return;
        }
        SceneNode parent = dropTarget;
        // Climb to a parent that actually accepts this node type.
        while (parent != null && (!parent.type.acceptsChildren()
                || !parent.type.allowedChildTypes().contains(fresh.type))) {
            parent = doc.findParent(parent.id);
        }
        if (parent == null) parent = doc.root;
        SceneEditorSession.pushUndo();
        fresh.name = uniqueName(doc, fresh.name == null || fresh.name.isBlank() ? prefabName : fresh.name);
        try {
            SceneNodeIds.assignTree(doc, fresh);
            resetForRouteLayout(parent, fresh);
            parent.children.add(fresh);
        } finally {
            SceneNodeIds.clearPending();
        }
        SceneEditorSession.select(fresh.id);
        SceneEditorSession.markDirty();
    }

    /** PLAYER under the drop target, if any (drop on CAMERA counts). */
    private static SceneNode playerAncestor(SceneDocument doc, SceneNode node) {
        SceneNode cur = node;
        while (cur != null) {
            if (cur.type == SceneNodeType.PLAYER) return cur;
            cur = doc.findParent(cur.id);
        }
        return null;
    }

    /** Saves a node subtree as a prefab file; returns the prefab name or null. */
    public static String saveNodeAsPrefab(SceneDocument doc, String nodeId) {
        if (nodeId != null && nodeId.indexOf('\n') >= 0) {
            nodeId = nodeId.split("\n", 2)[0].trim();
        }
        SceneNode node = doc == null || nodeId == null ? null : doc.findNode(nodeId);
        if (node == null || node.type == SceneNodeType.SCENE) return null;
        try {
            var gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
            String base = node.name == null || node.name.isBlank() ? node.type.name().toLowerCase() : node.name;
            String name = org.zonarstudio.spraute_engine.scene.PrefabRepository.save(gameDir, node, base);
            if (name != null) {
                node.prefabId = name;
                SceneEditorSession.markDirty();
                projectNeedsReload = true;
                org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.pushIfRemote(
                        org.zonarstudio.spraute_engine.scene.PrefabRepository.prefabPath(gameDir, name));
            }
            return name;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** Overwrites the linked prefab asset with the current node edits. */
    public static String savePrefabChanges(SceneDocument doc, String nodeId) {
        if (nodeId != null && nodeId.indexOf('\n') >= 0) {
            nodeId = nodeId.split("\n", 2)[0].trim();
        }
        SceneNode node = doc == null || nodeId == null ? null : doc.findNode(nodeId);
        String prefab = linkedPrefabId(node);
        if (node == null || prefab == null) return null;
        try {
            var gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
            String name = org.zonarstudio.spraute_engine.scene.PrefabRepository.overwrite(gameDir, node, prefab);
            if (name != null) {
                node.prefabId = name;
                projectNeedsReload = true;
                org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.pushIfRemote(
                        org.zonarstudio.spraute_engine.scene.PrefabRepository.prefabPath(gameDir, name));
            }
            return name;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** Prefab asset id for a scene instance, or null if not linked / file missing. */
    public static String linkedPrefabId(SceneNode node) {
        if (node == null || node.type == SceneNodeType.SCENE) return null;
        String id = node.prefabId == null ? "" : node.prefabId.trim();
        if (id.isEmpty()) return null;
        var gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
        return org.zonarstudio.spraute_engine.scene.PrefabRepository.exists(gameDir, id)
                ? org.zonarstudio.spraute_engine.scene.PrefabRepository.sanitize(id)
                : null;
    }

    private void openNodePicker(String parentId) {
        // Deferred: openPopup must run at the same ID-stack level as beginPopup,
        // not from inside another (context) popup where the ID would not match.
        addToNodeId = parentId;
        openNodePickerPopup = true;
    }

    private void renderNodePicker(SceneDocument doc) {
        if (openNodePickerPopup) {
            ImGui.openPopup(EditorI18n.t("Добавить ноду"));
            openNodePickerPopup = false;
        }
        if (!ImGui.beginPopup(EditorI18n.t("Добавить ноду"))) return;
        SceneNode parent = addToNodeId == null ? null : doc.findNode(addToNodeId);
        if (parent == null || !parent.type.acceptsChildren()) {
            ImGui.textDisabled(EditorI18n.t("Эта нода не принимает дочерние элементы"));
            ImGui.endPopup();
            return;
        }

        ImGui.text(EditorI18n.t("Добавить ноду в: ") + displayName(parent));
        ImGui.separator();

        boolean canOverlay = parent.type.allowedChildTypes().contains(SceneNodeType.SKIN_OVERLAY);
        if (canOverlay && nodeMenuItem(EditorI18n.t("Наложение скина"),
                EditorI18n.t("Полный скин поверх текущего, включая руку от 1 лица"))) {
            addNode(doc, parent, SceneNode.skinOverlay(uniqueName(doc, EditorI18n.t("Наложение"))));
        }

        boolean structEmpty = parent.type.allowedChildTypes().contains(SceneNodeType.EMPTY);
        if (ImGui.beginMenu(EditorI18n.t("Структура"), structEmpty)) {
            if (nodeMenuItem(EditorI18n.t("Пустая нода"), EditorI18n.t("Контейнер/точка привязки без визуала"))) {
                addNode(doc, parent, SceneNode.empty(uniqueName(doc, EditorI18n.t("Пустая нода"))));
            }
            ImGui.endMenu();
        }

        boolean objNpc = parent.type.allowedChildTypes().contains(SceneNodeType.NPC);
        boolean objModel = parent.type.allowedChildTypes().contains(SceneNodeType.MODEL);
        boolean objBillboard = parent.type.allowedChildTypes().contains(SceneNodeType.BILLBOARD);
        if (ImGui.beginMenu(EditorI18n.t("Объекты"), objNpc || objModel || objBillboard)) {
            if (objNpc && nodeMenuItem("NPC", EditorI18n.t("Живой персонаж: ИИ, здоровье и бой"))) {
                addNode(doc, parent, SceneNode.npc(uniqueName(doc, "NPC")));
            }
            if (objModel && nodeMenuItem(EditorI18n.t("Модель"), EditorI18n.t("Гео-модель без ИИ"))) {
                addNode(doc, parent, SceneNode.model(uniqueName(doc, EditorI18n.t("Модель"))));
            }
            if (objBillboard && nodeMenuItem(EditorI18n.t("Билборд"), EditorI18n.t("Картинка, всегда повёрнутая к камере"))) {
                addNode(doc, parent, SceneNode.billboard(uniqueName(doc, EditorI18n.t("Билборд"))));
            }
            ImGui.endMenu();
        }

        boolean physHitbox = parent.type.allowedChildTypes().contains(SceneNodeType.HITBOX);
        boolean fxParticle = parent.type.allowedChildTypes().contains(SceneNodeType.PARTICLE);
        boolean fxLight = parent.type.allowedChildTypes().contains(SceneNodeType.LIGHT);
        boolean fxAudio = parent.type.allowedChildTypes().contains(SceneNodeType.AUDIO_SOURCE);
        boolean fxPost = parent.type.allowedChildTypes().contains(SceneNodeType.POST_PROCESS);
        if (ImGui.beginMenu(EditorI18n.t("Физика"), physHitbox)) {
            if (nodeMenuItem(EditorI18n.t("Хитбокс"), EditorI18n.t("Твёрдая коллизия и/или триггер"))) {
                addNode(doc, parent, SceneNode.hitbox(uniqueName(doc, EditorI18n.t("Хитбокс"))));
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu(EditorI18n.t("Эффекты"), fxParticle || fxLight || fxAudio || fxPost)) {
            if (fxParticle && nodeMenuItem(EditorI18n.t("Частицы"), EditorI18n.t("Эмиттер: форма, разброс, гравитация, цикл"))) {
                addNode(doc, parent, SceneNode.particle(uniqueName(doc, EditorI18n.t("Частицы"))));
            }
            if (fxLight && nodeMenuItem(EditorI18n.t("Свет"), EditorI18n.t("Невидимый minecraft:light 0–15 (белый)"))) {
                addNode(doc, parent, SceneNode.light(uniqueName(doc, EditorI18n.t("Свет"))));
            }
            if (fxAudio && nodeMenuItem(EditorI18n.t("Аудио сурс"), EditorI18n.t("Позиционный звук: громче у центра, куб/сфера, затухание"))) {
                addNode(doc, parent, SceneNode.audioSource(uniqueName(doc, EditorI18n.t("Аудио сурс"))));
            }
            if (fxPost && nodeMenuItem(EditorI18n.t("Пост-обработка"), EditorI18n.t("Эффект камеры игрока: bloom, vignette, цветокор — только у владельца"))) {
                addNode(doc, parent, SceneNode.postProcess(uniqueName(doc, EditorI18n.t("Пост-обработка"))));
            }
            ImGui.endMenu();
        }

        boolean resBlock = parent.type.allowedChildTypes().contains(SceneNodeType.RES_BLOCK);
        if (ImGui.beginMenu(EditorI18n.t("Ресурсы"), resBlock)) {
            if (nodeMenuItem(EditorI18n.t("Блок"), EditorI18n.t("create block — регистрируется при перезапуске MC"))) {
                addNode(doc, parent, SceneNode.resBlock(uniqueName(doc, EditorI18n.t("Блок"))));
            }
            if (nodeMenuItem(EditorI18n.t("Предмет"), EditorI18n.t("create item — инструмент / броня / geo"))) {
                addNode(doc, parent, SceneNode.resItem(uniqueName(doc, EditorI18n.t("Предмет"))));
            }
            if (nodeMenuItem(EditorI18n.t("Крафт"), EditorI18n.t("create craft — верстак / печь / без формы"))) {
                addNode(doc, parent, SceneNode.resCraft(uniqueName(doc, EditorI18n.t("Крафт"))));
            }
            if (nodeMenuItem(EditorI18n.t("Вкладка"), EditorI18n.t("create tab — вкладка креатива"))) {
                addNode(doc, parent, SceneNode.resTab(uniqueName(doc, EditorI18n.t("Вкладка"))));
            }
            if (nodeMenuItem(EditorI18n.t("Дроп"), EditorI18n.t("create drop — дроп с блока или моба, replace = отключить базовый"))) {
                addNode(doc, parent, SceneNode.resDrop(uniqueName(doc, EditorI18n.t("Дроп"))));
            }
            if (nodeMenuItem(EditorI18n.t("Мир"), EditorI18n.t("create world — измерение, небо, туман, облака"))) {
                addNode(doc, parent, SceneNode.resWorld(uniqueName(doc, EditorI18n.t("Мир"))));
            }
            if (nodeMenuItem(EditorI18n.t("Частица (тип) | OLD"), EditorI18n.t("create particle — текстура · spraute_engine:id · перезапуск MC"))) {
                addNode(doc, parent, SceneNode.resParticle(uniqueName(doc, EditorI18n.t("Частица"))));
            }
            if (nodeMenuItem(EditorI18n.t("Снаряд (тип)"), EditorI18n.t("create projectile — shootProjectile / spawnProjectile"))) {
                addNode(doc, parent, SceneNode.resProjectile(uniqueName(doc, EditorI18n.t("Снаряд"))));
            }
            ImGui.endMenu();
        }

        // Custom sections: declare only from SCRIPT nodes of the open/active scene
        boolean allowCustom = parent.type.allowedChildTypes().contains(SceneNodeType.CUSTOM);
        if (allowCustom) {
            org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.bindEditorScene(doc);
            for (String section : org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.sections()) {
                var types = org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.typesInSection(section);
                if (types.isEmpty()) continue;
                if (ImGui.beginMenu(section)) {
                    for (var type : types) {
                        String tip = "declare node type " + type.id
                                + (type.runtime == null || type.runtime.isBlank() ? "" : " · runtime=" + type.runtime);
                        if (nodeMenuItem(type.displayLabel(), tip)) {
                            SceneNode created = SceneNode.custom(type.id, uniqueName(doc, type.displayLabel()));
                            org.zonarstudio.spraute_engine.scene.custom.GenericNodeTypeRegistry.fireEditorCreate(created);
                            addNode(doc, parent, created);
                        }
                    }
                    ImGui.endMenu();
                }
            }
        }

        boolean guiCanvas = parent.type.allowedChildTypes().contains(SceneNodeType.CANVAS);
        boolean guiWidgets = parent.type.allowedChildTypes().contains(SceneNodeType.UI_TEXT);
        if (ImGui.beginMenu("GUI", guiCanvas || guiWidgets)) {
            if (guiCanvas && nodeMenuItem(EditorI18n.t("Канвас"), EditorI18n.t("Основа GUI: панель, на которую ставятся виджеты"))) {
                addNode(doc, parent, SceneNode.canvas(uniqueName(doc, EditorI18n.t("Канвас"))));
            }
            // Same canvas, same widgets — drawn as a plane in the world instead of on the screen.
            if (guiCanvas && nodeMenuItem(EditorI18n.t("3D-канвас (GUI в мире)"),
                    EditorI18n.t("Тот же канвас, но панелью в мире: виджеты кладутся прямо в него, правятся в 3D-виде"))) {
                addNode(doc, parent, SceneNode.worldCanvas(uniqueName(doc, EditorI18n.t("3D-канвас"))));
            }
            // Widgets are always available: if the parent can't hold them
            // directly, a canvas is auto-created as their container.
            if (guiCanvas && !guiWidgets) ImGui.separator();
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_GRID, EditorI18n.t("Сетка"), EditorI18n.t("Контейнер: дети выравниваются по ячейкам"));
            uiLayoutMenuItem(doc, parent, "row", EditorI18n.t("Ряд"), EditorI18n.t("Горизонтальный поток: дети встают в ряд с учётом своих размеров"));
            uiLayoutMenuItem(doc, parent, "column", EditorI18n.t("Колонка"), EditorI18n.t("Вертикальный поток: дети встают в колонку с учётом своих размеров"));
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_TEXT, EditorI18n.t("Текст"), EditorI18n.t("Надпись на канвасе"));
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_BUTTON, EditorI18n.t("Кнопка"), EditorI18n.t("Кликабельная кнопка (on uiClick в скриптах)"));
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_IMAGE, EditorI18n.t("Картинка"), EditorI18n.t("Текстура из папки проекта"));
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_SVG, EditorI18n.t("SVG"), EditorI18n.t("Векторная картинка (.svg) из папки проекта"));
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_VIDEO, EditorI18n.t("Видео"), EditorI18n.t("MP4/H.264 без звука"));
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_RECT, EditorI18n.t("Панель"), EditorI18n.t("Прямоугольник/фон, может содержать виджеты"));
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_INPUT, EditorI18n.t("Поле ввода"), EditorI18n.t("Текстовое поле (on uiInput в скриптах)"));
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_SCROLL, EditorI18n.t("Скролл"), EditorI18n.t("Прокручиваемая область с виджетами"));
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_DIVIDER, EditorI18n.t("Разделитель"), EditorI18n.t("Горизонтальная линия"));
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_ITEM, EditorI18n.t("Предмет / Блок"), EditorI18n.t("Предмет или блок Minecraft (блоки рендерятся в 3D)"));
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_ENTITY, EditorI18n.t("Рендер"), EditorI18n.t("Рендер игрока, НПС сцены или моба в GUI"));
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_SLOT, EditorI18n.t("Слот"), EditorI18n.t("Слот контейнера 18x18: игрок кладёт/забирает предметы"));
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_INVENTORY, EditorI18n.t("Инвентарь игрока"), EditorI18n.t("Сетка 9x3 + хотбар инвентаря игрока"));
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_CROP, EditorI18n.t("Обрезка (crop)"), EditorI18n.t("Контейнер: всё за его границами обрезается"));
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_GROUP, EditorI18n.t("Пустая нода"), EditorI18n.t("Невидимый контейнер: группировка виджетов, общая прозрачность"));
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_DRAG, EditorI18n.t("Перетаскивание (drag)"), EditorI18n.t("Контейнер, который игрок может тащить мышью. Дети едут вместе с ним"));
            uiWidgetMenuItem(doc, parent, SceneNodeType.UI_DROPTARGET, EditorI18n.t("Зона сброса (drop)"), EditorI18n.t("Область, куда можно отпустить drag. Совпадает по группе"));
            ImGui.endMenu();
        }

        boolean logicScript = parent.type.allowedChildTypes().contains(SceneNodeType.SCRIPT);
        boolean logicVisual = parent.type.allowedChildTypes().contains(SceneNodeType.VISUAL_SCRIPT);
        boolean logicAnimator = parent.type.allowedChildTypes().contains(SceneNodeType.ANIMATOR);
        boolean logicRoute = parent.type.allowedChildTypes().contains(SceneNodeType.ROUTE);
        boolean logicRoutePoint = parent.type.allowedChildTypes().contains(SceneNodeType.ROUTE_POINT);
        if (ImGui.beginMenu(EditorI18n.t("Логика"),
                logicScript || logicVisual || logicAnimator || logicRoute || logicRoutePoint)) {
            if (logicScript && nodeMenuItem(EditorI18n.t("Скрипт"), EditorI18n.t(".spr-файл, запускаемый вместе со сценой"))) {
                newScriptParentId = parent.id;
                newScriptName.set(uniqueScriptBase("script"));
                createScriptError = null;
                createVisualScriptMode = false;
                openCreateScriptPopup = true;
                ImGui.closeCurrentPopup();
            }
            if (logicVisual && nodeMenuItem(EditorI18n.t("Визуальный скрипт"), EditorI18n.t(".sprv — блоки; рантайм читает парный .spr"))) {
                newScriptParentId = parent.id;
                newScriptName.set(uniqueScriptBase("vscript"));
                createScriptError = null;
                createVisualScriptMode = true;
                openCreateVisualScriptPopup = true;
                ImGui.closeCurrentPopup();
            }
            if (logicAnimator && nodeMenuItem(EditorI18n.t("Универсальный аниматор"),
                    EditorI18n.t("Любые параметры объекта и кости модели в одном .animation.json"))) {
                addNode(doc, parent, SceneNode.animator(uniqueName(doc, EditorI18n.t("Аниматор"))));
            }
            if (logicRoute && nodeMenuItem(EditorI18n.t("Путь"),
                    EditorI18n.t("Точки, прямые или плавные линии"))) {
                addNode(doc, parent, SceneNode.route(uniqueName(doc, EditorI18n.t("Путь"))));
            }
            if (logicRoutePoint && nodeMenuItem(EditorI18n.t("Точка пути"),
                    EditorI18n.t("Точка с координатами X/Y/Z"))) {
                var player = net.minecraft.client.Minecraft.getInstance().player;
                double x = player == null ? 0d : player.getX();
                double y = player == null ? 0d : player.getY();
                double z = player == null ? 0d : player.getZ();
                SceneNode point = SceneNode.routePoint(uniqueName(doc, EditorI18n.t("Точка")), 0, 0, 0);
                SceneTransform world = new SceneTransform();
                world.px = x;
                world.py = y;
                world.pz = z;
                point.transform = SceneTransform.toLocal(doc.worldTransform(parent.id), world);
                addNode(doc, parent, point);
            }
            ImGui.endMenu();
        }
        ImGui.endPopup();
    }

    private void renderCreateScriptPopup(SceneDocument doc) {
        if (openCreateScriptPopup) {
            ImGui.openPopup(EditorI18n.t("Создать скрипт"));
            openCreateScriptPopup = false;
        }
        if (!org.zonarstudio.spraute_engine.client.editor.EditorPopups.beginModal(EditorI18n.t("Создать скрипт"), 380f)) return;
        ImGui.text(EditorI18n.t("Новый файл в spraute_engine/scripts"));
        ImGui.setNextItemWidth(-32f);
        ImGui.inputText("##new-script-name", newScriptName);
        ImGui.sameLine();
        ImGui.textDisabled(".spr");
        if (createScriptError != null) {
            ImGui.textColored(1f, .35f, .3f, 1f, createScriptError);
        }
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        if (ImGui.button(EditorI18n.t("Создать"), 120f, 0f)) {
            createScriptFileAndNode(doc);
        }
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Отмена"), 100f, 0f)) {
            newScriptParentId = null;
            createScriptError = null;
            ImGui.closeCurrentPopup();
        }
        org.zonarstudio.spraute_engine.client.editor.EditorPopups.endModal();
    }

    private void renderCreateVisualScriptPopup(SceneDocument doc) {
        if (openCreateVisualScriptPopup) {
            ImGui.openPopup(EditorI18n.t("Создать визуальный скрипт"));
            openCreateVisualScriptPopup = false;
        }
        if (!org.zonarstudio.spraute_engine.client.editor.EditorPopups.beginModal(EditorI18n.t("Создать визуальный скрипт"), 400f)) return;
        ImGui.text(EditorI18n.t("Новый файл в spraute_engine/scripts"));
        ImGui.setNextItemWidth(-40f);
        ImGui.inputText("##new-vscript-name", newScriptName);
        ImGui.sameLine();
        ImGui.textDisabled(".sprv");
        ImGui.textDisabled(EditorI18n.t("Парный .spr появится при сохранении визуального редактора."));
        if (createScriptError != null) {
            ImGui.textColored(1f, .35f, .3f, 1f, createScriptError);
        }
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        if (ImGui.button(EditorI18n.t("Создать"), 120f, 0f)) {
            createVisualScriptFileAndNode(doc);
        }
        ImGui.sameLine();
        if (ImGui.button(EditorI18n.t("Отмена"), 100f, 0f)) {
            newScriptParentId = null;
            createScriptError = null;
            createVisualScriptMode = false;
            ImGui.closeCurrentPopup();
        }
        org.zonarstudio.spraute_engine.client.editor.EditorPopups.endModal();
    }

    private void createScriptFileAndNode(SceneDocument doc) {
        SceneNode parent = newScriptParentId == null ? null : doc.findNode(newScriptParentId);
        if (parent == null || !parent.type.allowedChildTypes().contains(SceneNodeType.SCRIPT)) {
            createScriptError = EditorI18n.t("Родительская нода больше недоступна");
            return;
        }
        String base = sanitizeScriptName(newScriptName.get());
        if (base.isEmpty()) {
            createScriptError = EditorI18n.t("Введите имя скрипта");
            return;
        }
        Path file = scriptPath(base);
        if (Files.exists(file) || Files.exists(visualScriptPath(base))) {
            createScriptError = EditorI18n.t("Файл ") + base + EditorI18n.t(".spr / .sprv уже существует");
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "", StandardCharsets.UTF_8);
        } catch (IOException e) {
            createScriptError = EditorI18n.t("Не удалось создать файл: ") + e.getMessage();
            return;
        }
        org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.pushIfRemote(file);

        SceneEditorSession.pushUndo();
        SceneNode node = SceneNode.script(uniqueName(doc, base), base);
        try {
            SceneNodeIds.assignTree(doc, node);
            parent.children.add(node);
        } finally {
            SceneNodeIds.clearPending();
        }
        SceneEditorSession.select(node.id);
        SceneEditorSession.markDirty();
        SceneEditorSession.requestOpenScript(file);
        newScriptParentId = null;
        createScriptError = null;
        ImGui.closeCurrentPopup();
    }

    private void createVisualScriptFileAndNode(SceneDocument doc) {
        SceneNode parent = newScriptParentId == null ? null : doc.findNode(newScriptParentId);
        if (parent == null || !parent.type.allowedChildTypes().contains(SceneNodeType.VISUAL_SCRIPT)) {
            createScriptError = EditorI18n.t("Родительская нода больше недоступна");
            return;
        }
        String base = sanitizeScriptName(newScriptName.get());
        if (base.isEmpty()) {
            createScriptError = EditorI18n.t("Введите имя скрипта");
            return;
        }
        Path sprv = visualScriptPath(base);
        Path spr = scriptPath(base);
        if (Files.exists(sprv) || Files.exists(spr)) {
            createScriptError = EditorI18n.t("Файл ") + base + EditorI18n.t(".sprv / .spr уже существует");
            return;
        }
        try {
            Files.createDirectories(sprv.getParent());
            Files.writeString(sprv, "{\"version\":1,\"blocks\":[]}\n", StandardCharsets.UTF_8);
            VisualScriptEditor.ensureRuntimeSpr(sprv);
        } catch (IOException e) {
            createScriptError = EditorI18n.t("Не удалось создать файл: ") + e.getMessage();
            return;
        }
        org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.pushIfRemote(sprv);
        org.zonarstudio.spraute_engine.client.editor.RemoteProjectClient.pushIfRemote(spr);

        SceneEditorSession.pushUndo();
        SceneNode node = SceneNode.visualScript(uniqueName(doc, base), base);
        try {
            SceneNodeIds.assignTree(doc, node);
            parent.children.add(node);
        } finally {
            SceneNodeIds.clearPending();
        }
        SceneEditorSession.select(node.id);
        SceneEditorSession.markDirty();
        SceneEditorSession.requestOpenVisualScript(sprv);
        newScriptParentId = null;
        createScriptError = null;
        createVisualScriptMode = false;
        ImGui.closeCurrentPopup();
    }

    private static Path scriptPath(String scriptName) {
        String relative = scriptName == null ? "" : scriptName.replace('\\', '/');
        if (relative.endsWith(".sprv")) relative = relative.substring(0, relative.length() - 5);
        if (relative.endsWith(".spr")) relative = relative.substring(0, relative.length() - 4);
        return SceneEditorSession.gameDir().resolve("spraute_engine").resolve("scripts")
                .resolve(relative + ".spr").normalize();
    }

    private static Path visualScriptPath(String scriptName) {
        String relative = scriptName == null ? "" : scriptName.replace('\\', '/');
        if (relative.endsWith(".sprv")) relative = relative.substring(0, relative.length() - 5);
        if (relative.endsWith(".spr")) relative = relative.substring(0, relative.length() - 4);
        return SceneEditorSession.gameDir().resolve("spraute_engine").resolve("scripts")
                .resolve(relative + ".sprv").normalize();
    }

    private static String sanitizeScriptName(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.endsWith(".sprv")) value = value.substring(0, value.length() - 5);
        if (value.endsWith(".spr")) value = value.substring(0, value.length() - 4);
        value = value.replaceAll("[^A-Za-z0-9_\\-]+", "_");
        value = value.replaceAll("^_+|_+$", "");
        return value;
    }

    private static String uniqueScriptBase(String preferred) {
        String base = sanitizeScriptName(preferred);
        if (base.isEmpty()) base = "script";
        if (!Files.exists(scriptPath(base)) && !Files.exists(visualScriptPath(base))) return base;
        for (int i = 2; ; i++) {
            String candidate = base + "_" + i;
            if (!Files.exists(scriptPath(candidate)) && !Files.exists(visualScriptPath(candidate))) {
                return candidate;
            }
        }
    }

    private static boolean nodeMenuItem(String label, String hint) {
        boolean clicked = ImGui.menuItem(label);
        if (ImGui.isItemHovered() && hint != null && !hint.isEmpty()) {
            ImGui.beginTooltip();
            ImGui.textDisabled(hint);
            ImGui.endTooltip();
        }
        return clicked;
    }

    /**
     * Menu item for a UI widget. If the parent can hold widgets directly the
     * widget goes there; otherwise a canvas is created automatically first.
     */
    private void uiWidgetMenuItem(SceneDocument doc, SceneNode parent,
                                  SceneNodeType type, String label, String hint) {
        boolean direct = parent.type.allowedChildTypes().contains(type);
        boolean viaCanvas = !direct && parent.type.allowedChildTypes().contains(SceneNodeType.CANVAS);
        if (!direct && !viaCanvas) return;
        if (!nodeMenuItem(label, viaCanvas ? hint + EditorI18n.t(" (канвас создастся автоматически)") : hint)) return;
        SceneNode widget = SceneNode.uiWidget(type, uniqueName(doc, label));
        addUiWidgetNode(doc, parent, widget, direct);
    }

    private void uiLayoutMenuItem(SceneDocument doc, SceneNode parent,
                                  String mode, String label, String hint) {
        boolean direct = parent.type.allowedChildTypes().contains(SceneNodeType.UI_GRID);
        boolean viaCanvas = !direct && parent.type.allowedChildTypes().contains(SceneNodeType.CANVAS);
        if (!direct && !viaCanvas) return;
        if (!nodeMenuItem(label, viaCanvas ? hint + EditorI18n.t(" (канвас создастся автоматически)") : hint)) return;
        SceneNode widget = "column".equals(mode)
                ? SceneNode.uiColumn(uniqueName(doc, label))
                : SceneNode.uiRow(uniqueName(doc, label));
        addUiWidgetNode(doc, parent, widget, direct);
    }

    private void addUiWidgetNode(SceneDocument doc, SceneNode parent, SceneNode widget, boolean direct) {
        if (direct) {
            addNode(doc, parent, widget);
            return;
        }
        SceneEditorSession.pushUndo();
        SceneNode canvas = SceneNode.canvas(uniqueName(doc, EditorI18n.t("Канвас")));
        canvas.children.add(widget);
        try {
            SceneNodeIds.assignTree(doc, canvas);
            parent.children.add(canvas);
        } finally {
            SceneNodeIds.clearPending();
        }
        SceneEditorSession.select(widget.id);
        SceneEditorSession.markDirty();
        ImGui.closeCurrentPopup();
    }

    private static void addNode(SceneDocument doc, SceneNode parent, SceneNode node) {
        SceneEditorSession.pushUndo();
        // UI nodes use pixel coordinates on the canvas, not world coordinates — except a
        // world-space canvas, which is placed in front of the camera like any world object.
        boolean worldPlaced = !node.type.isUi()
                || org.zonarstudio.spraute_engine.scene.SceneWorldCanvasRuntime.isWorldCanvas(node);
        if (node.hasTransform() && worldPlaced && node.type != SceneNodeType.ROUTE_POINT
                && parent.type != SceneNodeType.ROUTE) {
            placeAtCamera(doc, parent, node);
        }
        try {
            SceneNodeIds.assignTree(doc, node);
            if (node.type == SceneNodeType.ROUTE && (node.routeFile == null || node.routeFile.isBlank())) {
                node.routeFile = "routes/" + node.id + SceneRouteStorage.EXTENSION;
            }
            parent.children.add(node);
        } finally {
            SceneNodeIds.clearPending();
        }
        SceneEditorSession.select(node.id);
        SceneEditorSession.markDirty();
        ClientPlayerSkinEdits.pushIfSkinRelated(doc, node);
        ImGui.closeCurrentPopup();
    }

    private static void resetForRouteLayout(SceneNode parent, SceneNode child) {
        if (parent == null || parent.type != SceneNodeType.ROUTE
                || !org.zonarstudio.spraute_engine.scene.SceneRouteLayout.isLayoutChild(child)) return;
        child.transform.px = 0;
        child.transform.py = 0;
        child.transform.pz = 0;
        child.transform.rx = 0;
        child.transform.ry = 0;
        child.transform.rz = 0;
    }

    private static void addScriptNode(SceneDocument doc, SceneNode parent, String scriptName) {
        SceneEditorSession.pushUndo();
        String display = scriptName;
        int slash = Math.max(display.lastIndexOf('/'), display.lastIndexOf('\\'));
        if (slash >= 0) display = display.substring(slash + 1);
        SceneNode node = SceneNode.script(uniqueName(doc, display), scriptName);
        try {
            SceneNodeIds.assignTree(doc, node);
            parent.children.add(node);
        } finally {
            SceneNodeIds.clearPending();
        }
        SceneEditorSession.select(node.id);
        SceneEditorSession.markDirty();
    }

    private static void addVisualScriptNode(SceneDocument doc, SceneNode parent, String scriptName) {
        SceneEditorSession.pushUndo();
        String display = scriptName;
        int slash = Math.max(display.lastIndexOf('/'), display.lastIndexOf('\\'));
        if (slash >= 0) display = display.substring(slash + 1);
        SceneNode node = SceneNode.visualScript(uniqueName(doc, display), scriptName);
        try {
            SceneNodeIds.assignTree(doc, node);
            parent.children.add(node);
        } finally {
            SceneNodeIds.clearPending();
        }
        SceneEditorSession.select(node.id);
        SceneEditorSession.markDirty();
    }

    private void applyHierarchyClick(SceneDocument doc, String nodeId) {
        boolean ctrl = ImGui.getIO().getKeyCtrl();
        boolean shift = ImGui.getIO().getKeyShift();
        if (shift) {
            String filter = hierarchySearch.get().trim().toLowerCase(java.util.Locale.ROOT);
            java.util.List<String> order = new java.util.ArrayList<>();
            collectVisibleIds(doc.root, filter, order);
            SceneEditorSession.selectRange(order, nodeId);
        } else if (ctrl) {
            SceneEditorSession.toggleSelect(nodeId);
        } else if (SceneEditorSession.selectedCount() > 1 && SceneEditorSession.isSelected(nodeId)) {
            // Keep multi-select when pressing an already-selected node (multi-drag).
            // Plain click on an unselected node still replaces the selection below.
            SceneEditorSession.setPrimarySelection(nodeId);
        } else {
            SceneEditorSession.select(nodeId);
        }
        AnimatorTimelineSession.selectBone(null);
    }

    private void collectVisibleIds(SceneNode node, String filter, java.util.List<String> out) {
        if (!filter.isEmpty() && !nodeOrDescendantMatches(node, filter)) return;
        out.add(node.id);
        for (SceneNode child : node.children) {
            collectVisibleIds(child, filter, out);
        }
    }

    private void renderRenamePopup(SceneDocument doc) {
        if (renameNodeId != null) {
            ImGui.openPopup(EditorI18n.t("Переименовать ноду"));
        }
        if (org.zonarstudio.spraute_engine.client.editor.EditorPopups.beginModal(EditorI18n.t("Переименовать ноду"), 320f)) {
            ImGui.text(EditorI18n.t("Новое имя ноды:"));
            ImGui.setNextItemWidth(-1f);
            ImGui.inputText("##rename", renameBuffer);
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            if (ImGui.button("OK", 100f, 0f) || ImGui.isKeyPressed(imgui.flag.ImGuiKey.Enter)) {
                SceneNode node = renameNodeId != null ? doc.findNode(renameNodeId) : null;
                String newName = renameBuffer.get().trim();
                if (node != null && !newName.isEmpty()) {
                    SceneEditorSession.pushUndo();
                    node.name = newName;
                    SceneEditorSession.markDirty();
                    ClientPlayerSkinEdits.pushIfSkinRelated(doc, node);
                }
                renameNodeId = null;
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(EditorI18n.t("Отмена"), 100f, 0f)) {
                renameNodeId = null;
                ImGui.closeCurrentPopup();
            }
            org.zonarstudio.spraute_engine.client.editor.EditorPopups.endModal();
        }
    }

    /** New scene objects appear a few blocks in front of the editor fly-camera (not the real player). */
    private static void placeAtCamera(SceneDocument doc, SceneNode parent, SceneNode npc) {
        Vec3 eye = SprauteEditorState.viewPosition();
        Vec3 look = editorLookAngle();
        if (look == null) return;
        var world = new org.zonarstudio.spraute_engine.scene.SceneTransform();
        world.px = eye.x + look.x * 4;
        world.py = eye.y + look.y * 4 - 1;
        world.pz = eye.z + look.z * 4;
        var parentWorld = parent.type == SceneNodeType.SCENE ? null : doc.worldTransform(parent.id);
        npc.transform = org.zonarstudio.spraute_engine.scene.SceneTransform.toLocal(parentWorld, world);
    }

    /** Double-click in hierarchy: fly-cam jumps so the node is in view (same offset as placeAtCamera). */
    private static void focusEditorCameraOnNode(SceneDocument doc, SceneNode node) {
        if (!SprauteEditorState.hasDetachedCamera()) return;
        var world = doc.worldTransform(node.id);
        if (world == null) return;
        Entity cam = SprauteEditorState.cameraEntity();
        if (cam == null) return;
        Vec3 look = cam.getLookAngle();
        // Node sits ~4 blocks in front of the eye (placeAtCamera uses eye + look*4 - 1 on Y).
        double x = world.px - look.x * 4;
        double y = world.py - look.y * 4 + 1;
        double z = world.pz - look.z * 4;
        SprauteEditorState.moveCameraTo(x, y, z, cam.getYRot(), cam.getXRot());
    }

    private static Vec3 editorLookAngle() {
        Entity cam = SprauteEditorState.cameraEntity();
        if (cam != null) return cam.getLookAngle();
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null && mc.level != null) {
            var main = mc.gameRenderer.getMainCamera();
            return new Vec3(main.getLookVector().x(), main.getLookVector().y(), main.getLookVector().z());
        }
        if (mc.player != null) return mc.player.getLookAngle();
        return null;
    }

    private static String displayName(SceneNode node) {
        String name = node.name == null || node.name.isEmpty() ? EditorI18n.t("(без имени)") : node.name;
        if (node.id != null && !node.id.isBlank()
                && (node.type == SceneNodeType.PLAYER || node.type == SceneNodeType.CAMERA
                || node.type == SceneNodeType.AUDIO || node.type == SceneNodeType.POST_PROCESS)) {
            return name + "  ·  " + node.id;
        }
        return name;
    }

    private static int countNodes(SceneNode node) {
        int count = 1;
        for (SceneNode child : node.children) count += countNodes(child);
        return count;
    }

    private static boolean nodeOrDescendantMatches(SceneNode node, String filter) {
        String name = node.name == null ? "" : node.name.toLowerCase(java.util.Locale.ROOT);
        if (name.contains(filter) || node.type.name().toLowerCase(java.util.Locale.ROOT).contains(filter)
                || node.id.toLowerCase(java.util.Locale.ROOT).contains(filter)) return true;
        for (SceneNode child : node.children) {
            if (nodeOrDescendantMatches(child, filter)) return true;
        }
        return false;
    }

    /**
     * Next free display name. {@code EditorI18n.t("нода 2")} → {@code EditorI18n.t("нода 3")}, not {@code EditorI18n.t("нода 2 2")}.
     * Bare names ({@code "NPC"}) become {@code "NPC 2"}, {@code "NPC 3"}, …
     */
    private static String uniqueName(SceneDocument doc, String base) {
        java.util.Set<String> names = new java.util.HashSet<>();
        doc.walk((n, t) -> names.add(n.name));
        String trimmed = base == null || base.isBlank() ? EditorI18n.t("Нода") : base.trim();
        if (!names.contains(trimmed)) return trimmed;

        String stem = trimmed;
        int next = 2;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(.*)\\s+(\\d+)$")
                .matcher(trimmed);
        if (m.matches()) {
            stem = m.group(1).trim();
            if (stem.isEmpty()) stem = EditorI18n.t("Нода");
            next = Integer.parseInt(m.group(2)) + 1;
        }
        for (int i = next; ; i++) {
            String candidate = stem + " " + i;
            if (!names.contains(candidate)) return candidate;
        }
    }
}
