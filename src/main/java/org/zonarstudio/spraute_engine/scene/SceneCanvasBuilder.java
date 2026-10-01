package org.zonarstudio.spraute_engine.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.zonarstudio.spraute_engine.ui.UiChildAlign;
import org.zonarstudio.spraute_engine.ui.UiGridLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts a CANVAS scene node (with UI_* children) into the same UI JSON that
 * {@code create ui} produces, so the client renders it via SprauteScriptScreen.
 *
 * Coordinate convention for UI nodes: transform.position.x/y = pixels on the
 * canvas (or percent of the parent when the node's percent flag is set),
 * rotation.z = degrees, scale.x/y = size multiplier. Widget ids are the node
 * names, so scripts can use {@code on uiClick}, {@code uiUpdate}, etc.
 */
public final class SceneCanvasBuilder {
    private SceneCanvasBuilder() {}

    /** Reference GUI-scaled screen used to resolve percent canvases for nested layout math. */
    public static final int REF_SCREEN_W = 427;
    public static final int REF_SCREEN_H = 240;

    public static String buildJson(SceneNode canvas) {
        return buildJson(canvas, null);
    }

    /** sceneRoot (optional) lets UI_ENTITY "npc" mode resolve NPC nodes of the same scene. */
    public static String buildJson(SceneNode canvas, SceneNode sceneRoot) {
        SCENE_ROOT.set(sceneRoot);
        try {
            return buildJsonInner(canvas);
        } finally {
            SCENE_ROOT.remove();
        }
    }

    private static final ThreadLocal<SceneNode> SCENE_ROOT = new ThreadLocal<>();

    private static String buildJsonInner(SceneNode canvas) {
        JsonObject root = new JsonObject();
        // Percent canvases are sent as "70%" strings: the client resolves them
        // against its actual GUI-scaled screen, so the UI adapts per player.
        if (canvas.uiPercentW) root.addProperty("w", canvas.uiCanvasW + "%");
        else root.addProperty("w", Math.max(4, canvas.uiCanvasW));
        if (canvas.uiPercentH) root.addProperty("h", canvas.uiCanvasH + "%");
        else root.addProperty("h", Math.max(4, canvas.uiCanvasH));
        // Widget layout inside the canvas still needs pixel reference sizes.
        int cw = Math.max(4, canvas.uiPercentW
                ? Math.round(canvas.uiCanvasW / 100f * REF_SCREEN_W) : canvas.uiCanvasW);
        int ch = Math.max(4, canvas.uiPercentH
                ? Math.round(canvas.uiCanvasH / 100f * REF_SCREEN_H) : canvas.uiCanvasH);
        root.addProperty("bg", orDefault(canvas.uiColor, "#C0101010"));
        // The node identifier (editable at the top of the Inspector) is the
        // single id used by scripts, exactly like NPC/model nodes.
        root.addProperty("id", canvas.id);
        root.addProperty("canClose", canvas.canvasCanClose);
        if (canvas.canvasLayer != 0) root.addProperty("layer", canvas.canvasLayer);
        root.addProperty("dimBackground", canvas.canvasDim);
        putStyle(root, canvas);
        // A world-space canvas is positioned in the world by its transform, and always drawn
        // flush at 0,0 inside its own texture — emitting x/y here would both offset it and
        // rewrite the JSON (forcing a full re-render + entity resync) on every move.
        double px = canvas.canvasWorldSpace || canvas.transform == null ? 0 : canvas.transform.px;
        double py = canvas.canvasWorldSpace || canvas.transform == null ? 0 : canvas.transform.py;
        if (px != 0) {
            if (canvas.uiPercentX) root.addProperty("x", (int) Math.round(px) + "%");
            else root.addProperty("x", (int) Math.round(px));
        }
        if (py != 0) {
            if (canvas.uiPercentY) root.addProperty("y", (int) Math.round(py) + "%");
            else root.addProperty("y", (int) Math.round(py));
        }

        // Animator nodes inside the canvas: canvas-level animators address widgets
        // by bone name (bone = widget id), widget-level animators drive their parent.
        JsonArray animators = new JsonArray();
        collectAnimators(canvas, null, animators);
        if (animators.size() > 0) root.add("animators", animators);
        // Rotation must always be present on animated canvases so every widget
        // gets a rotation wrapper the animator can drive.
        FORCE_ROTATION.set(animators.size() > 0);

        try {
            JsonArray widgets = new JsonArray();
            int[] order = {0};
            for (SceneNode child : canvas.children) {
                emitWidget(child, 0, 0, 1f, 1f, cw, ch, true, widgets, order);
            }
            root.add("widgets", widgets);
            // Slots only work through a container menu: SprauteContainerMenu reads
            // type=container and the flat "children" list (same as create ui does).
            JsonArray slotChildren = new JsonArray();
            collectSlotWidgets(widgets, slotChildren);
            if (slotChildren.size() > 0) {
                root.addProperty("type", "container");
                root.add("children", slotChildren);
                // AbstractContainerScreen centers by "size", the panel by w/h —
                // both must match or slots drift away from the drawn slot cells.
                JsonArray size = new JsonArray();
                size.add(cw);
                size.add(ch);
                root.add("size", size);
            }
        } finally {
            FORCE_ROTATION.remove();
        }
        return root.toString();
    }

    /**
     * Grid layout uses a temporary mutable copy. Its IDs must remain stable so
     * editor preview caching does not rebuild media widgets every render frame.
     */
    private static void restoreIds(SceneNode source, SceneNode copy) {
        copy.id = source.id;
        int count = Math.min(source.children.size(), copy.children.size());
        for (int i = 0; i < count; i++) {
            restoreIds(source.children.get(i), copy.children.get(i));
        }
    }

    /** Recursively pulls slot/playerInventory widgets into a flat list for the container menu. */
    private static void collectSlotWidgets(JsonArray widgets, JsonArray out) {
        for (var el : widgets) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String type = o.has("type") ? o.get("type").getAsString() : "";
            if ("slot".equals(type) || "playerInventory".equals(type)) out.add(o);
            if (o.has("children") && o.get("children").isJsonArray()) {
                collectSlotWidgets(o.getAsJsonArray("children"), out);
            }
        }
    }

    private static final ThreadLocal<Boolean> FORCE_ROTATION = new ThreadLocal<>();

    private static void collectAnimators(SceneNode node, String targetWidgetId, JsonArray out) {
        for (SceneNode child : node.children) {
            if (!child.active) continue;
            if (child.type == SceneNodeType.ANIMATOR) {
                if (child.animatorFile == null || child.animatorFile.isBlank()) continue;
                JsonObject o = new JsonObject();
                o.addProperty("file", child.animatorFile.trim());
                if (child.animatorClip != null && !child.animatorClip.isBlank()) {
                    o.addProperty("clip", child.animatorClip.trim());
                }
                if (targetWidgetId != null) o.addProperty("target", targetWidgetId);
                out.add(o);
            } else if (child.type.isUiWidget()) {
                collectAnimators(child, child.id, out);
            }
        }
    }

    /**
     * Emits one widget (and recursively its children) at parent-local
     * coordinates. offX/offY come from parent containers, scale from parents,
     * parentW/parentH resolve percent-based units. {@code keepPercent} (the
     * {@code directOnCanvas} flag) keeps "%" strings so the client resolves
     * them against the real parent — matching editor gizmos.
     */
    private static void emitWidget(SceneNode node, float offX, float offY,
                                   float parentSx, float parentSy,
                                   int parentW, int parentH,
                                   JsonArray out, int[] order) {
        emitWidget(node, offX, offY, parentSx, parentSy, parentW, parentH, false, "top_left", out, order);
    }

    private static void emitWidget(SceneNode node, float offX, float offY,
                                   float parentSx, float parentSy,
                                   int parentW, int parentH, boolean directOnCanvas,
                                   JsonArray out, int[] order) {
        emitWidget(node, offX, offY, parentSx, parentSy, parentW, parentH, directOnCanvas, "top_left", out, order);
    }

    private static void emitWidget(SceneNode node, float offX, float offY,
                                   float parentSx, float parentSy,
                                   int parentW, int parentH, boolean directOnCanvas,
                                   String parentChildAlign,
                                   JsonArray out, int[] order) {
        if (node == null || !node.type.isUiWidget() || !node.active) return;
        float sx = parentSx * (node.transform != null ? (float) node.transform.sx : 1f);
        float sy = parentSy * (node.transform != null ? (float) node.transform.sy : 1f);
        float rawX = node.transform != null ? (float) node.transform.px : 0f;
        float rawY = node.transform != null ? (float) node.transform.py : 0f;
        if (node.uiPercentX) rawX = rawX / 100f * parentW;
        if (node.uiPercentY) rawY = rawY / 100f * parentH;
        float rawW = node.uiPercentW ? node.uiW / 100f * parentW : node.uiW;
        float rawH = node.uiPercentH ? node.uiH / 100f * parentH : node.uiH;
        int w = Math.round(rawW * sx);
        int h = Math.round(rawH * sy);
        // Local coords only. childAlign is emitted on the parent and applied
        // on the client against the real parent size (same as editor gizmos).
        float x = offX + rawX * parentSx;
        float y = offY + rawY * parentSy;
        float rotation = node.transform != null ? (float) node.transform.rz : 0f;

        switch (node.type) {
            case UI_GRID -> {
                List<SceneNode> kids = new ArrayList<>();
                List<UiGridLayout.Item> items = new ArrayList<>();
                for (SceneNode child : node.children) {
                    if (!child.type.isUiWidget() || !child.active) continue;
                    kids.add(child);
                    items.add(measureUiChild(child, Math.max(1, w), Math.max(1, h)));
                }
                String mode = node.gridMode;
                boolean hug = node.gridHugContent;
                int[] content = UiGridLayout.contentSize(
                        mode, hug, node.gridColumns, node.gridCellW, node.gridCellH,
                        node.gridGapX, node.gridGapY, items);
                int boxW = Math.max(1, Math.max(w, content[0]));
                int boxH = Math.max(1, Math.max(h, content[1]));
                List<UiGridLayout.Placement> places = UiGridLayout.compute(
                        mode, node.gridAlign, node.gridFlow,
                        hug, node.gridStretchCells,
                        node.gridColumns, node.gridCellW, node.gridCellH,
                        node.gridGapX, node.gridGapY,
                        boxW, boxH, items);
                for (UiGridLayout.Placement p : places) {
                    SceneNode child = kids.get(p.index());
                    float cx = x + p.x() * sx;
                    float cy = y + p.y() * sy;
                    SceneNode copy = child.deepCopyWithNewIds();
                    restoreIds(child, copy);
                    copy.name = child.name;
                    copy.transform.px = 0;
                    copy.transform.py = 0;
                    copy.uiPercentX = false;
                    copy.uiPercentY = false;
                    if (node.gridStretchCells) {
                        copy.uiW = p.cellW();
                        copy.uiH = p.cellH();
                        copy.uiPercentW = false;
                        copy.uiPercentH = false;
                    }
                    emitWidget(copy, cx, cy, sx, sy, p.cellW(), p.cellH(), out, order);
                }
                return;
            }
            case UI_RECT -> {
                JsonObject o = base(node, directOnCanvas, "rect", x, y, w, h, rotation, order);
                o.addProperty("color", orDefault(node.uiColor, "#80FFFFFF"));
                putStyle(o, node);
                putFill(o, node);
                putCropShape(o, node);
                putChildAlign(o, node);
                JsonArray children = new JsonArray();
                int[] childOrder = {0};
                String childAlign = node.uiChildAlign;
                for (SceneNode child : node.children) {
                    emitWidget(child, 0, 0, sx, sy, w, h, true, childAlign, children, childOrder);
                }
                if (children.size() > 0) o.add("children", children);
                out.add(o);
            }
            case UI_TEXT -> {
                JsonObject o = base(node, directOnCanvas, "text", x, y, w, h, rotation, order);
                o.addProperty("text", node.uiText == null ? "" : node.uiText);
                o.addProperty("color", orDefault(node.uiColor, "#FFEAEAEA"));
                o.addProperty("scale", node.uiTextScale * sx);
                // Auto-wrap uses the widget width; manual wrap overrides when auto is off.
                if (node.uiAutoWrap && (directOnCanvas ? node.uiPercentW || w > 0 : w > 0)) {
                    if (directOnCanvas && node.uiPercentW) o.addProperty("wrap", node.uiW + "%");
                    else o.addProperty("wrap", w);
                } else if (node.uiWrap > 0) {
                    o.addProperty("wrap", Math.round(node.uiWrap * sx));
                }
                if (node.uiWrapMode != null && !node.uiWrapMode.isBlank()
                        && !"word".equalsIgnoreCase(node.uiWrapMode)) {
                    o.addProperty("wrapMode", node.uiWrapMode.trim().toLowerCase());
                }
                o.addProperty("align", orDefault(node.uiAlign, "left"));
                if (node.uiTextOffsetX != 0f) o.addProperty("textOffsetX", Math.round(node.uiTextOffsetX * sx));
                if (node.uiTextOffsetY != 0f) o.addProperty("textOffsetY", Math.round(node.uiTextOffsetY * sy));
                putTextStyle(o, node);
                out.add(o);
            }
            case UI_BUTTON -> {
                JsonObject o = base(node, directOnCanvas, "button", x, y, w, h, rotation, order);
                o.addProperty("label", node.uiText == null ? "" : node.uiText);
                o.addProperty("color", orDefault(node.uiColor, "#55336688"));
                o.addProperty("hover", orDefault(node.uiHoverColor, "#66447799"));
                if (node.uiTexture != null && !node.uiTexture.isBlank()) {
                    o.addProperty("texture", node.uiTexture.trim());
                    putSlice(o, node);
                }
                if (node.uiTextScale != 1f) o.addProperty("labelScale", node.uiTextScale);
                if (node.uiTextOffsetX != 0f) o.addProperty("labelOffsetX", Math.round(node.uiTextOffsetX * sx));
                if (node.uiTextOffsetY != 0f) o.addProperty("labelOffsetY", Math.round(node.uiTextOffsetY * sy));
                if (node.uiAutoWrap && (directOnCanvas && node.uiPercentW || w > 0)) {
                    if (directOnCanvas && node.uiPercentW) o.addProperty("labelWrap", node.uiW + "%");
                    else o.addProperty("labelWrap", Math.max(4, w - 6));
                } else if (node.uiWrap > 0) {
                    o.addProperty("labelWrap", Math.round(node.uiWrap * sx));
                }
                if (node.uiWrapMode != null && !node.uiWrapMode.isBlank()
                        && !"word".equalsIgnoreCase(node.uiWrapMode)) {
                    o.addProperty("wrapMode", node.uiWrapMode.trim().toLowerCase());
                }
                // Button default is center (legacy look); text widgets default left.
                String btnAlign = node.uiAlign == null || node.uiAlign.isBlank() ? "center" : node.uiAlign.trim().toLowerCase();
                o.addProperty("align", btnAlign);
                putTextStyle(o, node);
                putStyle(o, node);
                out.add(o);
            }
            case UI_IMAGE -> {
                JsonObject o = base(node, directOnCanvas, "image", x, y, w, h, rotation, order);
                o.addProperty("texture", node.uiTexture == null ? "" : node.uiTexture.trim());
                putSlice(o, node);
                putSrc(o, node);
                putFill(o, node);
                putCropShape(o, node);
                out.add(o);
            }
            case UI_VIDEO -> {
                JsonObject o = base(node, directOnCanvas, "video", x, y, w, h, rotation, order);
                o.addProperty("source", node.uiVideo == null ? "" : node.uiVideo.trim());
                o.addProperty("playerEnabled", node.uiVideoEnabled);
                o.addProperty("playing", node.uiVideoPlaying);
                o.addProperty("loop", node.uiVideoLoop);
                out.add(o);
            }
            case UI_SVG -> {
                JsonObject o = base(node, directOnCanvas, "svg", x, y, w, h, rotation, order);
                o.addProperty("source", node.uiTexture == null ? "" : node.uiTexture.trim());
                if (node.uiColor != null && !node.uiColor.isBlank()) {
                    o.addProperty("color", node.uiColor.trim());
                }
                if (node.uiSvgStroke != null && !node.uiSvgStroke.isBlank()) {
                    o.addProperty("stroke", node.uiSvgStroke.trim());
                }
                if (node.uiSvgStrokeWidth > 0.001f) {
                    o.addProperty("strokeWidth", node.uiSvgStrokeWidth);
                }
                if (node.uiSvgRecolor) o.addProperty("recolor", true);
                putFill(o, node);
                putCropShape(o, node);
                out.add(o);
            }
            case UI_INPUT -> {
                JsonObject o = base(node, directOnCanvas, "input", x, y, w, h, rotation, order);
                if (node.uiPlaceholder != null && !node.uiPlaceholder.isBlank()) {
                    o.addProperty("placeholder", node.uiPlaceholder);
                }
                if (node.uiText != null && !node.uiText.isBlank()) {
                    o.addProperty("text", node.uiText);
                }
                o.addProperty("color", orDefault(node.uiColor, "#FFFFFFFF"));
                o.addProperty("outlineColor", orDefault(node.uiBorderColor, "#FFAAAAAA"));
                o.addProperty("scale", node.uiTextScale);
                o.addProperty("maxChars", Math.max(1, node.uiMaxChars));
                putTextStyle(o, node);
                putStyle(o, node);
                out.add(o);
            }
            case UI_SCROLL -> {
                JsonObject o = base(node, directOnCanvas, "scroll", x, y, w, h, rotation, order);
                o.addProperty("contentH", Math.max(h, Math.round(node.uiContentH * sy)));
                o.addProperty("color", orDefault(node.uiColor, "#00000000"));
                o.addProperty("scrollbar", node.uiScrollbar);
                putStyle(o, node);
                putChildAlign(o, node);
                // Scroll children live in a nested array with scroll-local coordinates.
                JsonArray children = new JsonArray();
                int[] childOrder = {0};
                String childAlign = node.uiChildAlign;
                int contentParentH = Math.max(h, node.uiContentH);
                for (SceneNode child : node.children) {
                    emitWidget(child, 0, 0, sx, sy, w, contentParentH, true, childAlign, children, childOrder);
                }
                if (children.size() > 0) o.add("children", children);
                out.add(o);
            }
            case UI_CROP -> {
                // Clipping container: children render in crop-local coordinates
                // and everything outside the box is scissored away.
                JsonObject o = base(node, directOnCanvas, "clip", x, y, w, h, rotation, order);
                putCropFeather(o, node);
                putChildAlign(o, node);
                JsonArray children = new JsonArray();
                int[] childOrder = {0};
                String childAlign = node.uiChildAlign;
                for (SceneNode child : node.children) {
                    emitWidget(child, 0, 0, sx, sy, w, h, true, childAlign, children, childOrder);
                }
                if (children.size() > 0) o.add("children", children);
                out.add(o);
            }
            case UI_GROUP -> {
                // Invisible container: groups children (offset + shared alpha),
                // draws nothing itself.
                JsonObject o = base(node, directOnCanvas, "group", x, y, w, h, rotation, order);
                if (node.uiAlpha < 1f) o.addProperty("alpha", Math.max(0f, node.uiAlpha));
                putChildAlign(o, node);
                JsonArray children = new JsonArray();
                int[] childOrder = {0};
                String childAlign = node.uiChildAlign;
                for (SceneNode child : node.children) {
                    emitWidget(child, 0, 0, sx, sy, w, h, true, childAlign, children, childOrder);
                }
                if (children.size() > 0) o.add("children", children);
                out.add(o);
            }
            case UI_DRAG -> {
                JsonObject o = base(node, directOnCanvas, "drag", x, y, w, h, rotation, order);
                if (node.uiAlpha < 1f) o.addProperty("alpha", Math.max(0f, node.uiAlpha));
                putDrag(o, node);
                putChildAlign(o, node);
                JsonArray children = new JsonArray();
                int[] childOrder = {0};
                String childAlign = node.uiChildAlign;
                for (SceneNode child : node.children) {
                    emitWidget(child, 0, 0, sx, sy, w, h, true, childAlign, children, childOrder);
                }
                if (children.size() > 0) o.add("children", children);
                out.add(o);
            }
            case UI_DROPTARGET -> {
                JsonObject o = base(node, directOnCanvas, "drop", x, y, w, h, rotation, order);
                if (node.uiAlpha < 1f) o.addProperty("alpha", Math.max(0f, node.uiAlpha));
                putDrop(o, node);
                putChildAlign(o, node);
                JsonArray children = new JsonArray();
                int[] childOrder = {0};
                String childAlign = node.uiChildAlign;
                for (SceneNode child : node.children) {
                    emitWidget(child, 0, 0, sx, sy, w, h, true, childAlign, children, childOrder);
                }
                if (children.size() > 0) o.add("children", children);
                out.add(o);
            }
            case UI_DIVIDER -> {
                JsonObject o = base(node, directOnCanvas, "divider", x, y, w, Math.max(1, h), rotation, order);
                o.addProperty("color", orDefault(node.uiColor, "#44FFFFFF"));
                out.add(o);
            }
            case UI_ITEM -> {
                JsonObject o = base(node, directOnCanvas, "item", x, y, 0, 0, rotation, order);
                if (directOnCanvas && node.uiPercentW) o.addProperty("size", node.uiW + "%");
                else o.addProperty("size", Math.max(1, w));
                o.addProperty("item", orDefault(node.uiItem, "minecraft:diamond"));
                out.add(o);
            }
            case UI_BLOCK -> {
                JsonObject o = base(node, directOnCanvas, "block", x, y, 0, 0, rotation, order);
                if (directOnCanvas && node.uiPercentW) o.addProperty("w", node.uiW + "%");
                else o.addProperty("w", Math.max(1, w));
                o.addProperty("block", orDefault(node.uiItem, "minecraft:stone"));
                out.add(o);
            }
            case UI_SLOT -> {
                // Keep percent x/y as "%" when directly on the canvas so the client
                // resolves against the real panel (matches editor gizmos). Size is
                // always 18×18 px — vanilla slot hitbox.
                JsonObject o = base(node, directOnCanvas, "slot", x, y, 18, 18, 0f, order);
                out.add(o);
            }
            case UI_INVENTORY -> {
                JsonObject o = base(node, directOnCanvas, "playerInventory", x, y, 162, 76, 0f, order);
                out.add(o);
            }
            case UI_ENTITY -> {
                JsonObject o = base(node, directOnCanvas, "entity", x, y, w, h, rotation, order);
                String mode = node.uiEntityMode == null ? "player" : node.uiEntityMode;
                switch (mode) {
                    case "npc" -> {
                        // Model spec of an NPC node in the same scene: renders even
                        // when the live entity is not loaded on the client.
                        SceneNode npc = findNode(SCENE_ROOT.get(), node.uiEntityRef);
                        o.addProperty("entity", "");
                        if (npc != null) {
                            if (npc.model != null && !npc.model.isBlank()) o.addProperty("modelGeo", npc.model.trim());
                            if (npc.texture != null && !npc.texture.isBlank()) o.addProperty("modelTexture", npc.texture.trim());
                            if (npc.animation != null && !npc.animation.isBlank()) o.addProperty("modelAnim", npc.animation.trim());
                            // Prefer looking up a live entity by node id when present.
                            o.addProperty("entity", "npc:" + npc.id);
                        }
                    }
                    case "mob" -> o.addProperty("entity",
                            "mob:" + orDefault(node.uiEntityRef, "minecraft:zombie"));
                    default -> o.addProperty("entity", "player");
                }
                o.addProperty("scale", node.uiEntityScale * sx);
                // Default true: render from model even if NPC is dead / not spawned.
                if (!node.uiEntityAlways) o.addProperty("alwaysRender", false);
                else o.addProperty("alwaysRender", true);
                o.addProperty("anchorX", node.uiEntityAnchorX);
                o.addProperty("anchorY", node.uiEntityAnchorY);
                if (node.uiEntityAnchorY < 0f) {
                    o.addProperty("feetCrop", node.uiEntityFeetCrop);
                }
                out.add(o);
            }
            default -> {}
        }
    }

    private static JsonObject base(SceneNode node, String type, float x, float y,
                                   int w, int h, float rotation, int[] order) {
        return base(node, false, type, x, y, w, h, rotation, order);
    }

    private static JsonObject base(SceneNode node, boolean directOnCanvas, String type,
                                   float x, float y, int w, int h, float rotation, int[] order) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        o.addProperty("id", node.id);
        // Widgets on the canvas or nested in clip/rect/drag/… keep percent as
        // "%" strings so the client resolves them against the real parent size
        // (same math as editor gizmos). Flattened grid cells stay as pixels.
        boolean t = node.transform != null;
        if (directOnCanvas && node.uiPercentX && t) {
            o.addProperty("x", (int) Math.round(node.transform.px) + "%");
        } else {
            o.addProperty("x", Math.round(x));
        }
        if (directOnCanvas && node.uiPercentY && t) {
            o.addProperty("y", (int) Math.round(node.transform.py) + "%");
        } else {
            o.addProperty("y", Math.round(y));
        }
        if (directOnCanvas && node.uiPercentW) {
            o.addProperty("w", node.uiW + "%");
        } else if (w > 0) {
            o.addProperty("w", w);
        }
        if (directOnCanvas && node.uiPercentH) {
            o.addProperty("h", node.uiH + "%");
        } else if (h > 0) {
            o.addProperty("h", h);
        }
        if (rotation != 0f || Boolean.TRUE.equals(FORCE_ROTATION.get())) o.addProperty("rotation", rotation);
        if (node.uiTooltip != null && !node.uiTooltip.isBlank()) {
            o.addProperty("tooltip", node.uiTooltip);
        }
        o.addProperty("layer", node.canvasLayer);
        o.addProperty("order", order[0]++);
        return o;
    }

    private static UiGridLayout.Item measureUiChild(SceneNode child, int parentW, int parentH) {
        int cw = child.uiPercentW
                ? Math.max(1, Math.round(child.uiW / 100f * parentW))
                : Math.max(1, child.uiW);
        int ch = child.uiPercentH
                ? Math.max(1, Math.round(child.uiH / 100f * parentH))
                : Math.max(1, child.uiH);
        if (child.type == SceneNodeType.UI_SLOT) {
            cw = 18;
            ch = 18;
        } else if (child.type == SceneNodeType.UI_INVENTORY) {
            cw = 162;
            ch = 76;
        }
        return new UiGridLayout.Item(cw, ch);
    }

    private static void putChildAlign(JsonObject o, SceneNode node) {
        if (UiChildAlign.isOrigin(node.uiChildAlign)) return;
        o.addProperty("childAlign", UiChildAlign.normalize(node.uiChildAlign));
    }

    private static void putStyle(JsonObject o, SceneNode node) {
        if (node.uiRadius > 0f) o.addProperty("radius", node.uiRadius);
        if (node.uiGradient != null && !node.uiGradient.isBlank()) {
            // Full stop chain: start color, mid colors, end color ("#start,#mid...,#end").
            // When the start color is set, the widget's base color is not part of the gradient.
            StringBuilder gradient = new StringBuilder();
            if (node.uiGradientStart != null && !node.uiGradientStart.isBlank()) {
                gradient.append(node.uiGradientStart.trim());
            }
            String stops = node.uiGradientStops == null ? "" : node.uiGradientStops.trim();
            if (!stops.isEmpty()) {
                if (gradient.length() > 0) gradient.append(',');
                gradient.append(stops);
            }
            if (gradient.length() > 0) gradient.append(',');
            gradient.append(node.uiGradient.trim());
            o.addProperty("gradient", gradient.toString());
            o.addProperty("gradientDir", String.valueOf(gradientAngle(node)));
        }
        if (node.uiBorder > 0f) {
            o.addProperty("border", node.uiBorder);
            o.addProperty("borderColor", orDefault(node.uiBorderColor, "#66FFFFFF"));
            if (node.uiBorderGradient != null && !node.uiBorderGradient.isBlank()) {
                o.addProperty("borderGradient", node.uiBorderGradient.trim());
                o.addProperty("borderGradientDir", String.valueOf(node.uiBorderGradientAngle));
            }
        }
        if (node.uiOuterBorder > 0f) {
            o.addProperty("outerBorder", node.uiOuterBorder);
            o.addProperty("outerBorderColor", orDefault(node.uiOuterBorderColor, "#66FFFFFF"));
            if (node.uiOuterBorderGradient != null && !node.uiOuterBorderGradient.isBlank()) {
                o.addProperty("outerBorderGradient", node.uiOuterBorderGradient.trim());
                o.addProperty("outerBorderGradientDir", String.valueOf(node.uiOuterBorderGradientAngle));
            }
        }
        if (node.uiShadow > 0f) o.addProperty("shadow", node.uiShadow);
    }

    private static void putTextStyle(JsonObject o, SceneNode node) {
        if (node.uiTextEffects != null && !node.uiTextEffects.isBlank()) {
            o.addProperty("effects", node.uiTextEffects.trim());
        }
        if (node.uiTextGradient != null && !node.uiTextGradient.isBlank()) {
            o.addProperty("textGradient", node.uiTextGradient.trim());
        }
        if (node.uiTextOutlineColor != null && !node.uiTextOutlineColor.isBlank()) {
            o.addProperty("textOutlineColor", node.uiTextOutlineColor.trim());
        }
        if (node.uiTextGlowColor != null && !node.uiTextGlowColor.isBlank()) {
            o.addProperty("textGlowColor", node.uiTextGlowColor.trim());
        }
        if (node.uiTextEffectSpeed != 1f) o.addProperty("textEffectSpeed", node.uiTextEffectSpeed);
        if (node.uiTextEffectAmount != 1f) o.addProperty("textEffectAmount", node.uiTextEffectAmount);
        if (node.uiTextGlowSize != 3f) o.addProperty("textGlowSize", node.uiTextGlowSize);
        if (node.uiTextOutlineWidth != 1f) o.addProperty("textOutlineWidth", node.uiTextOutlineWidth);
        if (node.uiTextUnderlineColor != null && !node.uiTextUnderlineColor.isBlank()) {
            o.addProperty("textUnderlineColor", node.uiTextUnderlineColor.trim());
        }
        if (node.uiTextUnderlineThickness != 1f) {
            o.addProperty("textUnderlineThickness", node.uiTextUnderlineThickness);
        }
        if (node.uiTextUnderlineOffset != 0f) o.addProperty("textUnderlineOffset", node.uiTextUnderlineOffset);
        if (node.uiTextShadowColor != null && !node.uiTextShadowColor.isBlank()) {
            o.addProperty("textShadowColor", node.uiTextShadowColor.trim());
        }
        if (node.uiTextShadowOffsetX != 1f) o.addProperty("textShadowOffsetX", node.uiTextShadowOffsetX);
        if (node.uiTextShadowOffsetY != 1f) o.addProperty("textShadowOffsetY", node.uiTextShadowOffsetY);
        if (node.uiTextShadowSize != 2f) o.addProperty("textShadowSize", node.uiTextShadowSize);
    }

    /** Angle in degrees; falls back to legacy v/h direction for old scenes. */
    private static float gradientAngle(SceneNode node) {
        if (node.uiGradientAngle >= 0f) return node.uiGradientAngle;
        return "h".equalsIgnoreCase(node.uiGradientDir) ? 0f : 90f;
    }

    private static void putSlice(JsonObject o, SceneNode node) {
        if (node.uiSliceBorders <= 0) return;
        o.addProperty("slice_borders", node.uiSliceBorders);
        if (node.uiSliceScale > 0f && node.uiSliceScale != 1f) {
            o.addProperty("slice_scale", node.uiSliceScale);
        }
    }

    private static void putSrc(JsonObject o, SceneNode node) {
        if (node.uiSrcW <= 0) return;
        JsonArray arr = new JsonArray();
        arr.add(node.uiSrcU);
        arr.add(node.uiSrcV);
        arr.add(node.uiSrcW);
        arr.add(node.uiSrcH);
        o.add("src", arr);
    }

    private static void putCropShape(JsonObject o, SceneNode node) {
        String shape = node.uiCropShape == null ? "" : node.uiCropShape.trim();
        if (shape.isEmpty() || "rect".equalsIgnoreCase(shape) || "rectangle".equalsIgnoreCase(shape)) return;
        o.addProperty("cropShape", shape);
    }

    private static void putDrag(JsonObject o, SceneNode node) {
        if (!node.uiDragEnabled) o.addProperty("enabled", false);
        String axis = node.uiDragAxis == null ? "xy" : node.uiDragAxis.trim().toLowerCase();
        if (!axis.isEmpty() && !"xy".equals(axis)) o.addProperty("axis", axis);
        if (node.uiDragGroup != null && !node.uiDragGroup.isBlank()) o.addProperty("group", node.uiDragGroup.trim());
        if (node.uiDragPayload != null && !node.uiDragPayload.isBlank()) o.addProperty("payload", node.uiDragPayload);
        if (!node.uiDragConstrain) o.addProperty("constrain", false);
        if (!node.uiDragRevert) o.addProperty("revert", false);
        if (node.uiDragThreshold != 4) o.addProperty("threshold", Math.max(0, node.uiDragThreshold));
    }

    private static void putDrop(JsonObject o, SceneNode node) {
        if (!node.uiDropEnabled) o.addProperty("enabled", false);
        if (node.uiDropGroup != null && !node.uiDropGroup.isBlank()) o.addProperty("group", node.uiDropGroup.trim());
    }

    private static void putCropFeather(JsonObject o, SceneNode node) {
        if (node.uiCropFeather <= 0.01f) return;
        o.addProperty("feather", node.uiCropFeather);
        if (Math.abs(node.uiCropFeatherL - 1f) > 0.001f) o.addProperty("featherL", node.uiCropFeatherL);
        if (Math.abs(node.uiCropFeatherT - 1f) > 0.001f) o.addProperty("featherT", node.uiCropFeatherT);
        if (Math.abs(node.uiCropFeatherR - 1f) > 0.001f) o.addProperty("featherR", node.uiCropFeatherR);
        if (Math.abs(node.uiCropFeatherB - 1f) > 0.001f) o.addProperty("featherB", node.uiCropFeatherB);
        if (!node.uiCropFeatherSmooth) o.addProperty("featherSmooth", false);
    }

    private static void putFill(JsonObject o, SceneNode node) {
        String fill = node.uiFill == null ? "" : node.uiFill.trim();
        float amount = node.uiFillAmount;
        if ((fill.isEmpty() || "none".equalsIgnoreCase(fill)) && amount >= 0.999f) return;
        if (!fill.isEmpty()) o.addProperty("fill", fill);
        if (amount < 0.999f || amount > 1.001f) o.addProperty("fillAmount", amount);
        if (node.uiFillOrigin != null && !node.uiFillOrigin.isBlank()
                && !"bottom".equalsIgnoreCase(node.uiFillOrigin.trim())) {
            o.addProperty("fillOrigin", node.uiFillOrigin.trim());
        }
    }

    private static SceneNode findNode(SceneNode root, String id) {
        if (root == null || id == null || id.isBlank()) return null;
        if (id.equals(root.id)) return root;
        for (SceneNode child : root.children) {
            SceneNode found = findNode(child, id);
            if (found != null) return found;
        }
        return null;
    }

    private static String orDefault(String value, String def) {
        return value == null || value.isBlank() ? def : value;
    }
}
