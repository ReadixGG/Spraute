package org.zonarstudio.spraute_engine.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.zonarstudio.spraute_engine.script.CompiledScript;
import org.zonarstudio.spraute_engine.script.ScriptNode;
import org.zonarstudio.spraute_engine.script.SprauteColors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Result of {@code create ui}: JSON snapshot + server-side click handlers (instruction lists).
 */
public final class UiTemplate {
    private final String json;
    private final Map<String, List<CompiledScript.Instruction>> clickHandlers;

    private UiTemplate(String json, Map<String, List<CompiledScript.Instruction>> clickHandlers) {
        this.json = json;
        this.clickHandlers = clickHandlers;
    }

    public String getJson() {
        return json;
    }

    public Map<String, List<CompiledScript.Instruction>> getClickHandlers() {
        return clickHandlers;
    }

    public List<CompiledScript.Instruction> getWidgetHandler(String prefix, String widgetId) {
        if (clickHandlers == null || prefix == null || widgetId == null || widgetId.isEmpty()) return null;
        return clickHandlers.get(prefix + ":" + widgetId);
    }

    /**
     * Build from root props (evaluated via eval) and a list of runtime-collected widgets.
     */
    public static UiTemplate buildFromRuntime(Function<ScriptNode, Object> eval, Map<String, ScriptNode> rootProps,
                                              List<RuntimeWidget> widgets) {
        int w = 200;
        int h = 150;
        JsonElement wElem = null;
        JsonElement hElem = null;
        String bg = "#C0101010";

        if (rootProps.containsKey("size")) {
            Object sz = eval.apply(rootProps.get("size"));
            if (sz instanceof List<?> list && list.size() >= 2) {
                wElem = rootSizeToJson(list.get(0), w);
                hElem = rootSizeToJson(list.get(1), h);
                w = toInt(list.get(0), w);
                h = toInt(list.get(1), h);
            }
        }
        if (rootProps.containsKey("background")) {
            bg = String.valueOf(eval.apply(rootProps.get("background")));
        } else if (rootProps.containsKey("bg")) {
            bg = String.valueOf(eval.apply(rootProps.get("bg")));
        }

        JsonObject root = new JsonObject();
        if (wElem != null) {
            root.add("w", wElem);
        } else {
            root.addProperty("w", w);
        }
        if (hElem != null) {
            root.add("h", hElem);
        } else {
            root.addProperty("h", h);
        }
        root.addProperty("bg", bg);
        if (rootProps.containsKey("id")) {
            root.addProperty("id", String.valueOf(eval.apply(rootProps.get("id"))));
        }
        if (rootProps.containsKey("pos")) {
            Object posVal = eval.apply(rootProps.get("pos"));
            if (posVal instanceof List<?> posList && posList.size() >= 2) {
                Object xv = posList.get(0);
                Object yv = posList.get(1);
                if (xv instanceof Number n) root.addProperty("x", n);
                else root.addProperty("x", String.valueOf(xv));
                if (yv instanceof Number n) root.addProperty("y", n);
                else root.addProperty("y", String.valueOf(yv));
            }
        }
        if (rootProps.containsKey("canClose")) {
            Object v = eval.apply(rootProps.get("canClose"));
            boolean canClose = !(v instanceof Boolean b) || b;
            root.addProperty("canClose", canClose);
        } else if (rootProps.containsKey("can_close")) {
            Object v = eval.apply(rootProps.get("can_close"));
            boolean canClose = !(v instanceof Boolean b) || b;
            root.addProperty("canClose", canClose);
        }
        if (rootProps.containsKey("dimBackground") || rootProps.containsKey("dim_background")) {
            Object v = eval.apply(rootProps.get(rootProps.containsKey("dimBackground") ? "dimBackground" : "dim_background"));
            boolean dim = v instanceof Boolean b && b;
            root.addProperty("dimBackground", dim);
        }
        // Root panel style: rounding / gradient / border / shadow
        Map<String, Object> rootStyle = new HashMap<>();
        for (String key : new String[]{"radius", "gradient", "gradientDir", "gradient_dir",
                "border", "borderColor", "border_color", "borderGradient", "borderGradientDir",
                "outerBorder", "outerBorderColor", "outer_border", "outer_border_color",
                "outerBorderGradient", "outerBorderGradientDir", "shadow"}) {
            if (rootProps.containsKey(key)) {
                rootStyle.put(key, eval.apply(rootProps.get(key)));
            }
        }
        if (!rootStyle.isEmpty()) putStyle(root, rootStyle);

        JsonArray arr = new JsonArray();
        Map<String, List<CompiledScript.Instruction>> handlers = new HashMap<>();
        int order = 0;
        boolean hasSlots = false;

        for (RuntimeWidget rw : widgets) {
            JsonObject o = buildWidget(rw, w, h, order, handlers);
            if (o != null) {
                arr.add(o);
                order++;
                String t = o.has("type") ? o.get("type").getAsString() : "";
                if ("slot".equals(t) || "playerInventory".equals(t)) hasSlots = true;
            }
        }
        root.add("widgets", arr);
        if (hasSlots) {
            // Слоты работают только через контейнер-меню: SprauteContainerMenu читает type=container и children
            root.addProperty("type", "container");
            root.add("children", arr);
        }
        return new UiTemplate(root.toString(), handlers);
    }

    private static JsonObject buildWidget(RuntimeWidget rw, int pw, int ph, int order,
                                          Map<String, List<CompiledScript.Instruction>> handlers) {
        String kind = rw.kind != null ? rw.kind.toLowerCase() : "";
        return switch (kind) {
            case "text" -> buildText(rw, pw, ph, order);
            case "button" -> buildButton(rw, pw, ph, order, handlers);
            case "entity" -> buildEntity(rw, pw, ph, order);
            case "image" -> buildImage(rw, pw, ph, order);
            case "svg" -> buildSvg(rw, pw, ph, order);
            case "video" -> buildVideo(rw, pw, ph, order);
            case "rect", "panel" -> buildRect(rw, pw, ph, order);
            case "clip" -> buildClip(rw, pw, ph, order, handlers);
            case "group" -> buildGroup(rw, pw, ph, order, handlers);
            case "drag" -> buildDrag(rw, pw, ph, order, handlers);
            case "drop", "droptarget", "drop_target" -> buildDrop(rw, pw, ph, order, handlers);
            case "scroll" -> buildScroll(rw, pw, ph, order, handlers);
            case "divider" -> buildDivider(rw, pw, ph, order);
            case "block" -> buildBlock(rw, pw, ph, order);
            case "item" -> buildItem(rw, pw, ph, order);
            case "slot" -> buildSlot(rw, pw, ph, order);
            case "input" -> buildInput(rw, pw, ph, order);
            case "gridbg", "grid_bg" -> buildGridBg(rw, pw, ph, order);
            case "grid", "row", "column" -> buildGridLayout(rw, pw, ph, order, handlers, kind);
            case "playerInventory", "player_inventory" -> buildPlayerInventory(rw, pw, ph, order);
            default -> null;
        };
    }

    private static JsonObject buildText(RuntimeWidget rw, int pw, int ph, int order) {
        if (rw.evaluatedArgs.size() < 2) return null;
        String id = String.valueOf(rw.evaluatedArgs.get(0));
        String text = String.valueOf(rw.evaluatedArgs.get(1));
        JsonObject o = new JsonObject();
        o.addProperty("type", "text");
        o.addProperty("id", id);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        o.addProperty("text", text);
        o.addProperty("color", propColor(rw.evaluatedProps, "color", "#EAEAEA"));
        o.addProperty("scale", propFloat(rw.evaluatedProps, "scale", 1f));
        Object wrapVal = rw.evaluatedProps.get("wrap");
        if (wrapVal != null) {
            if (wrapVal instanceof Number n) {
                o.addProperty("wrap", n.intValue());
            } else {
                String ws = String.valueOf(wrapVal).trim();
                if (ws.endsWith("%")) {
                    o.addProperty("wrap", ws);
                } else {
                    try { o.addProperty("wrap", Integer.parseInt(ws)); } catch (NumberFormatException ignored) {}
                }
            }
        } else if (propBool(rw.evaluatedProps, "autoWrap", false)
                || propBool(rw.evaluatedProps, "auto_wrap", false)) {
            // Auto-wrap to widget width from size / w when wrap is omitted.
            Object sz = rw.evaluatedProps.get("size");
            Object wProp = sz instanceof List<?> list && !list.isEmpty() ? list.get(0) : rw.evaluatedProps.get("w");
            if (wProp != null) {
                if (wProp instanceof Number || (String.valueOf(wProp).trim().endsWith("%"))) {
                    if (wProp instanceof Number n) o.addProperty("wrap", n.intValue());
                    else o.addProperty("wrap", String.valueOf(wProp).trim());
                } else {
                    int ww = resolveDimForParent(wProp, pw, 0);
                    if (ww > 0) o.addProperty("wrap", ww);
                }
            }
        }
        String wrapMode = propStr(rw.evaluatedProps, "wrapMode",
                propStr(rw.evaluatedProps, "wrap_mode", null));
        if (wrapMode != null && !wrapMode.isBlank() && !"word".equalsIgnoreCase(wrapMode.trim())) {
            o.addProperty("wrapMode", wrapMode.trim().toLowerCase(Locale.ROOT));
        }
        o.addProperty("align", propStr(rw.evaluatedProps, "align", "left"));
        if (rw.evaluatedProps.containsKey("anchor")) {
            Object a = rw.evaluatedProps.get("anchor");
            if (a instanceof List<?> list && list.size() >= 2) {
                o.addProperty("anchorX", clamp01(toFloat(list.get(0))));
                o.addProperty("anchorY", clamp01(toFloat(list.get(1))));
            } else if (a instanceof String str) {
                o.addProperty("anchor", str);
            }
        } else {
            if (rw.evaluatedProps.containsKey("anchorX")) {
                o.addProperty("anchorX", propFloat(rw.evaluatedProps, "anchorX", 0f));
            }
            if (rw.evaluatedProps.containsKey("anchorY")) {
                o.addProperty("anchorY", propFloat(rw.evaluatedProps, "anchorY", 0f));
            }
        }
        if (rw.evaluatedProps.containsKey("maxLines")) {
            o.addProperty("maxLines", propInt(rw.evaluatedProps, "maxLines", 0));
        } else if (rw.evaluatedProps.containsKey("max_lines")) {
            o.addProperty("maxLines", propInt(rw.evaluatedProps, "max_lines", 0));
        }
        if (rw.evaluatedProps.containsKey("maxChars")) {
            o.addProperty("maxChars", propInt(rw.evaluatedProps, "maxChars", 0));
        } else if (rw.evaluatedProps.containsKey("max_chars")) {
            o.addProperty("maxChars", propInt(rw.evaluatedProps, "max_chars", 0));
        }
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);
        String tooltip = propStr(rw.evaluatedProps, "tooltip", null);
        if (tooltip != null && !tooltip.isEmpty()) o.addProperty("tooltip", tooltip);
        if (rw.evaluatedProps.containsKey("textOffsetX") || rw.evaluatedProps.containsKey("text_offset_x")) {
            o.addProperty("textOffsetX", propInt(rw.evaluatedProps, "textOffsetX",
                    propInt(rw.evaluatedProps, "text_offset_x", 0)));
        }
        if (rw.evaluatedProps.containsKey("textOffsetY") || rw.evaluatedProps.containsKey("text_offset_y")) {
            o.addProperty("textOffsetY", propInt(rw.evaluatedProps, "textOffsetY",
                    propInt(rw.evaluatedProps, "text_offset_y", 0)));
        }
        putTextStyle(o, rw.evaluatedProps);
        return o;
    }

    private static JsonObject buildButton(RuntimeWidget rw, int pw, int ph, int order,
                                          Map<String, List<CompiledScript.Instruction>> handlers) {
        if (rw.evaluatedArgs.size() < 2) return null;
        String id = String.valueOf(rw.evaluatedArgs.get(0));
        String label = String.valueOf(rw.evaluatedArgs.get(1));
        List<CompiledScript.Instruction> onClick = rw.eventHandlers != null ? rw.eventHandlers.get("onClick") : null;
        if (onClick != null && !onClick.isEmpty()) {
            handlers.put(id, onClick);
        }
        JsonObject o = new JsonObject();
        o.addProperty("type", "button");
        o.addProperty("id", id);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        putWH(o, rw.evaluatedProps, "size", 100, 22, pw, ph);
        o.addProperty("label", label);
        o.addProperty("color", propStr(rw.evaluatedProps, "color", "#55336688"));
        o.addProperty("hover", propStr(rw.evaluatedProps, "hover", "#66447799"));
        String tex = propStr(rw.evaluatedProps, "texture", null);
        if (tex != null && !tex.isEmpty()) {
            o.addProperty("texture", tex);
        }
        int labelWrap = propInt(rw.evaluatedProps, "labelWrap", 0);
        if (labelWrap <= 0 && (propBool(rw.evaluatedProps, "autoWrap", false)
                || propBool(rw.evaluatedProps, "auto_wrap", false))) {
            // Use button width minus padding (matches canvas builder).
            Object sz = rw.evaluatedProps.get("size");
            if (sz instanceof List<?> list && list.size() >= 1) {
                labelWrap = Math.max(4, toInt(list.get(0), pw) - 6);
            } else {
                int bw = propInt(rw.evaluatedProps, "w", 100);
                labelWrap = Math.max(4, bw - 6);
            }
        }
        if (labelWrap > 0) {
            o.addProperty("labelWrap", labelWrap);
        }
        String wrapMode = propStr(rw.evaluatedProps, "wrapMode",
                propStr(rw.evaluatedProps, "wrap_mode", null));
        if (wrapMode != null && !wrapMode.isBlank() && !"word".equalsIgnoreCase(wrapMode.trim())) {
            o.addProperty("wrapMode", wrapMode.trim().toLowerCase(Locale.ROOT));
        }
        if (rw.evaluatedProps.containsKey("labelScale")) {
            o.addProperty("labelScale", propFloat(rw.evaluatedProps, "labelScale", 1f));
        }
        if (rw.evaluatedProps.containsKey("labelOffsetX") || rw.evaluatedProps.containsKey("label_offset_x")) {
            o.addProperty("labelOffsetX", propInt(rw.evaluatedProps, "labelOffsetX",
                    propInt(rw.evaluatedProps, "label_offset_x", 0)));
        }
        if (rw.evaluatedProps.containsKey("labelOffsetY") || rw.evaluatedProps.containsKey("label_offset_y")) {
            o.addProperty("labelOffsetY", propInt(rw.evaluatedProps, "labelOffsetY",
                    propInt(rw.evaluatedProps, "label_offset_y", 0)));
        }
        String sub = propStr(rw.evaluatedProps, "subLabel", null);
        if (sub != null && !sub.isEmpty()) {
            o.addProperty("subLabel", sub);
        }
        if (rw.evaluatedProps.containsKey("subScale")) {
            o.addProperty("subScale", propFloat(rw.evaluatedProps, "subScale", 0.65f));
        }
        if (rw.evaluatedProps.containsKey("slice_borders")) {
            o.addProperty("slice_borders", propInt(rw.evaluatedProps, "slice_borders", 0));
        } else if (rw.evaluatedProps.containsKey("sliceBorders")) {
            o.addProperty("slice_borders", propInt(rw.evaluatedProps, "sliceBorders", 0));
        }
        if (rw.evaluatedProps.containsKey("slice_scale")) {
            o.addProperty("slice_scale", propFloat(rw.evaluatedProps, "slice_scale", 1f));
        } else if (rw.evaluatedProps.containsKey("sliceScale")) {
            o.addProperty("slice_scale", propFloat(rw.evaluatedProps, "sliceScale", 1f));
        }
        String btnAlign = propStr(rw.evaluatedProps, "align",
                propStr(rw.evaluatedProps, "labelAlign", "center"));
        if (btnAlign != null && !btnAlign.isBlank()) {
            o.addProperty("align", btnAlign.trim().toLowerCase(Locale.ROOT));
        }
        putTextStyle(o, rw.evaluatedProps);
        putStyle(o, rw.evaluatedProps);
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);
        String tooltip = propStr(rw.evaluatedProps, "tooltip", null);
        if (tooltip != null && !tooltip.isEmpty()) o.addProperty("tooltip", tooltip);
        if (rw.children != null && !rw.children.isEmpty()) {
            int hpw = 210;
            int hph = 96;
            Object hs = rw.evaluatedProps.get("hover_size");
            if (hs == null) hs = rw.evaluatedProps.get("hoverSize");
            if (hs instanceof List<?> l && l.size() >= 2) {
                hpw = toInt(l.get(0), pw);
                hph = toInt(l.get(1), ph);
            }
            JsonObject hpRoot = new JsonObject();
            hpRoot.addProperty("w", hpw);
            hpRoot.addProperty("h", hph);
            JsonArray hpChildren = new JsonArray();
            int childOrder = 0;
            for (RuntimeWidget child : rw.children) {
                JsonObject co = buildWidget(child, hpw, hph, childOrder++, handlers);
                if (co != null) hpChildren.add(co);
            }
            hpRoot.add("children", hpChildren);
            o.add("hover_panel", hpRoot);
        }
        return o;
    }

    private static JsonObject buildEntity(RuntimeWidget rw, int pw, int ph, int order) {
        if (rw.evaluatedArgs.isEmpty()) return null;
        Object entityArg = rw.evaluatedArgs.get(0);
        if (entityArg instanceof org.zonarstudio.spraute_engine.scene.SceneNodeView view) {
            net.minecraft.world.entity.Entity spawned = view.getEntity(null);
            if (spawned != null) entityArg = spawned;
        }
        String entity;
        if (entityArg instanceof net.minecraft.world.entity.Entity ent) {
            entity = ent.getUUID().toString();
        } else {
            entity = String.valueOf(entityArg);
        }
        JsonObject o = new JsonObject();
        o.addProperty("type", "entity");
        String defaultId = rw.evaluatedArgs.size() >= 2 ? String.valueOf(rw.evaluatedArgs.get(1)) : "entity_" + order;
        String wid = propStr(rw.evaluatedProps, "id", defaultId);
        o.addProperty("id", wid);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        putWH(o, rw.evaluatedProps, "size", 64, 96, pw, ph);
        o.addProperty("entity", entity);
        // Model spec — lets the client render the NPC even when the live entity is unloaded (chat history).
        if (entityArg instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity npc) {
            if (npc.getModel() != null) o.addProperty("modelGeo", npc.getModel());
            if (npc.getTexture() != null) o.addProperty("modelTexture", npc.getTexture());
            if (npc.getAnimation() != null) o.addProperty("modelAnim", npc.getAnimation());
            if (npc.getIdleAnim() != null) o.addProperty("modelIdle", npc.getIdleAnim());
        }
        if (rw.evaluatedProps.containsKey("modelGeo") || rw.evaluatedProps.containsKey("model_geo")) {
            o.addProperty("modelGeo", propStr(rw.evaluatedProps,
                    rw.evaluatedProps.containsKey("modelGeo") ? "modelGeo" : "model_geo", null));
        }
        if (rw.evaluatedProps.containsKey("modelTexture") || rw.evaluatedProps.containsKey("model_texture")) {
            o.addProperty("modelTexture", propStr(rw.evaluatedProps,
                    rw.evaluatedProps.containsKey("modelTexture") ? "modelTexture" : "model_texture", null));
        }
        if (rw.evaluatedProps.containsKey("modelAnim") || rw.evaluatedProps.containsKey("model_anim")) {
            o.addProperty("modelAnim", propStr(rw.evaluatedProps,
                    rw.evaluatedProps.containsKey("modelAnim") ? "modelAnim" : "model_anim", null));
        }
        if (rw.evaluatedProps.containsKey("modelIdle") || rw.evaluatedProps.containsKey("model_idle")) {
            o.addProperty("modelIdle", propStr(rw.evaluatedProps,
                    rw.evaluatedProps.containsKey("modelIdle") ? "modelIdle" : "model_idle", null));
        }
        o.addProperty("scale", propFloat(rw.evaluatedProps, "scale", 1f));
        if (rw.evaluatedProps.containsKey("autoScale") || rw.evaluatedProps.containsKey("auto_scale")) {
            String key = rw.evaluatedProps.containsKey("autoScale") ? "autoScale" : "auto_scale";
            if (propBool(rw.evaluatedProps, key, false)) {
                o.addProperty("autoScale", true);
            }
        }
        o.addProperty("feetCrop", propFloat(rw.evaluatedProps,
                rw.evaluatedProps.containsKey("feetCrop") ? "feetCrop" : "feet_crop", 0.38f));
        putCropAndAnchor(o, rw.evaluatedProps);
        if (rw.evaluatedProps.containsKey("nameTag")) {
            o.addProperty("nameTag", propBool(rw.evaluatedProps, "nameTag", false));
        }
        if (rw.evaluatedProps.containsKey("noLookAt")) {
            o.addProperty("noLookAt", propBool(rw.evaluatedProps, "noLookAt", false));
        }
        if (rw.evaluatedProps.containsKey("noFollowCursor") || rw.evaluatedProps.containsKey("no_follow_cursor")) {
            String key = rw.evaluatedProps.containsKey("noFollowCursor") ? "noFollowCursor" : "no_follow_cursor";
            o.addProperty("noFollowCursor", propBool(rw.evaluatedProps, key, false));
        }
        if (rw.evaluatedProps.containsKey("noHurtAnim") || rw.evaluatedProps.containsKey("no_hurt_anim")) {
            String key = rw.evaluatedProps.containsKey("noHurtAnim") ? "noHurtAnim" : "no_hurt_anim";
            o.addProperty("noHurtAnim", propBool(rw.evaluatedProps, key, false));
        }
        if (rw.evaluatedProps.containsKey("animation")) {
            Object animVal = rw.evaluatedProps.get("animation");
            if (animVal instanceof Boolean b) o.addProperty("animation", b);
            else o.addProperty("animation", String.valueOf(animVal));
        }
        if (rw.evaluatedProps.containsKey("renderBones")) {
            Object rbVal = rw.evaluatedProps.get("renderBones");
            if (rbVal instanceof List<?> list) {
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Object item : list) arr.add(String.valueOf(item));
                o.add("renderBones", arr);
            } else {
                String rb = String.valueOf(rbVal);
                if (!rb.isEmpty() && !"null".equalsIgnoreCase(rb)) {
                    o.addProperty("renderBones", rb);
                }
            }
        }
        if (rw.evaluatedProps.containsKey("skinPlayer")) {
            Object spVal = rw.evaluatedProps.get("skinPlayer");
            if (spVal instanceof net.minecraft.server.level.ServerPlayer sp) {
                o.addProperty("skinPlayerUuid", sp.getUUID().toString());
            } else if (spVal instanceof net.minecraft.world.entity.Entity ent) {
                o.addProperty("skinPlayerUuid", ent.getUUID().toString());
            } else if (spVal != null) {
                String s = String.valueOf(spVal);
                if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) {
                    o.addProperty("skinPlayer", s);
                }
            }
        }
        if (rw.evaluatedProps.containsKey("clip") || rw.evaluatedProps.containsKey("clipEntity")) {
            String key = rw.evaluatedProps.containsKey("clip") ? "clip" : "clipEntity";
            o.addProperty("clipEntity", propBool(rw.evaluatedProps, key, false));
        }
        // Default true: render from model even if the NPC is dead / not in the world.
        boolean always = true;
        if (rw.evaluatedProps.containsKey("alwaysRender") || rw.evaluatedProps.containsKey("always_render")) {
            String key = rw.evaluatedProps.containsKey("alwaysRender") ? "alwaysRender" : "always_render";
            always = propBool(rw.evaluatedProps, key, true);
        }
        o.addProperty("alwaysRender", always);
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);
        String tooltip = propStr(rw.evaluatedProps, "tooltip", null);
        if (tooltip != null && !tooltip.isEmpty()) o.addProperty("tooltip", tooltip);
        return o;
    }

    private static void putCropAndAnchor(JsonObject o, Map<String, Object> props) {
        if (props.containsKey("crop")) {
            Object v = props.get("crop");
            if (v instanceof List<?> list && list.size() >= 4) {
                JsonArray arr = new JsonArray();
                for (int i = 0; i < 4; i++) {
                    Object e = list.get(i);
                    float f = e instanceof Number n ? n.floatValue() : Float.parseFloat(String.valueOf(e).trim());
                    arr.add(clamp01(f));
                }
                o.add("crop", arr);
            }
        } else if (props.containsKey("viewport")) {
            Object v = props.get("viewport");
            if (v instanceof List<?> list && list.size() >= 4) {
                float[] vp = new float[]{
                        clamp01(toFloat(list.get(0))),
                        clamp01(toFloat(list.get(1))),
                        clamp01(toFloat(list.get(2))),
                        clamp01(toFloat(list.get(3)))
                };
                normalizeViewportCorners(vp);
                JsonArray arr = new JsonArray();
                arr.add(vp[0]);
                arr.add(vp[1]);
                arr.add(1f - vp[2]);
                arr.add(1f - vp[3]);
                o.add("crop", arr);
            }
        }
        if (props.containsKey("anchor")) {
            Object a = props.get("anchor");
            if (a instanceof List<?> list && list.size() >= 2) {
                o.addProperty("anchorX", clamp01(toFloat(list.get(0))));
                float ay = toFloat(list.get(1));
                o.addProperty("anchorY", ay < 0f ? -1f : ay);
            } else if (a instanceof String str) {
                o.addProperty("anchor", str);
            }
        } else {
            if (props.containsKey("anchorX") || props.containsKey("anchor_x")) {
                o.addProperty("anchorX", propFloat(props, props.containsKey("anchorX") ? "anchorX" : "anchor_x", 0.5f));
            }
            if (props.containsKey("anchorY") || props.containsKey("anchor_y")) {
                o.addProperty("anchorY", propFloat(props, props.containsKey("anchorY") ? "anchorY" : "anchor_y", 0.5f));
            }
        }
    }

    private static float toFloat(Object o) {
        if (o instanceof Number n) return n.floatValue();
        return Float.parseFloat(String.valueOf(o).trim());
    }

    private static void normalizeViewportCorners(float[] xy) {
        if (xy[0] > xy[2]) { float t = xy[0]; xy[0] = xy[2]; xy[2] = t; }
        if (xy[1] > xy[3]) { float t = xy[1]; xy[1] = xy[3]; xy[3] = t; }
    }

    private static float clamp01(float f) {
        return Math.min(1f, Math.max(0f, f));
    }

    private static JsonObject buildImage(RuntimeWidget rw, int pw, int ph, int order) {
        String id;
        String texture;
        if (rw.evaluatedArgs.size() >= 2) {
            id = String.valueOf(rw.evaluatedArgs.get(0));
            texture = String.valueOf(rw.evaluatedArgs.get(1));
        } else if (rw.evaluatedArgs.size() == 1) {
            id = String.valueOf(rw.evaluatedArgs.get(0));
            texture = propStr(rw.evaluatedProps, "texture", "");
        } else {
            return null;
        }
        if (texture.isEmpty()) texture = propStr(rw.evaluatedProps, "texture", "minecraft:textures/misc/unknown_pack.png");
        JsonObject o = new JsonObject();
        o.addProperty("type", "image");
        o.addProperty("id", id);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        putWH(o, rw.evaluatedProps, "size", 32, 32, pw, ph);
        o.addProperty("texture", texture);
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);
        
        if (rw.evaluatedProps.containsKey("slice_borders")) {
            o.addProperty("slice_borders", propInt(rw.evaluatedProps, "slice_borders", 0));
        } else if (rw.evaluatedProps.containsKey("sliceBorders")) {
            o.addProperty("slice_borders", propInt(rw.evaluatedProps, "sliceBorders", 0));
        }
        if (rw.evaluatedProps.containsKey("slice_scale")) {
            o.addProperty("slice_scale", propFloat(rw.evaluatedProps, "slice_scale", 1f));
        } else if (rw.evaluatedProps.containsKey("sliceScale")) {
            o.addProperty("slice_scale", propFloat(rw.evaluatedProps, "sliceScale", 1f));
        }
        if (rw.evaluatedProps.containsKey("src")) {
            Object v = rw.evaluatedProps.get("src");
            if (v instanceof List<?> list && list.size() >= 4) {
                JsonArray arr = new JsonArray();
                for (Object el : list) arr.add(new JsonPrimitive(el instanceof Number n ? n.floatValue() : Float.parseFloat(String.valueOf(el))));
                o.add("src", arr);
            }
        } else if (rw.evaluatedProps.containsKey("srcW") || rw.evaluatedProps.containsKey("src_w")) {
            JsonArray arr = new JsonArray();
            arr.add(propInt(rw.evaluatedProps, "srcU", propInt(rw.evaluatedProps, "src_u", 0)));
            arr.add(propInt(rw.evaluatedProps, "srcV", propInt(rw.evaluatedProps, "src_v", 0)));
            arr.add(propInt(rw.evaluatedProps, "srcW", propInt(rw.evaluatedProps, "src_w", 0)));
            arr.add(propInt(rw.evaluatedProps, "srcH", propInt(rw.evaluatedProps, "src_h", 0)));
            o.add("src", arr);
        }
        putFillProps(o, rw.evaluatedProps);
        putCropShapeProp(o, rw.evaluatedProps);
        
        return o;
    }

    /** svg(id) / svg(id, source) — workspace .svg rasterized on the client. */
    private static JsonObject buildSvg(RuntimeWidget rw, int pw, int ph, int order) {
        String id;
        String source;
        if (rw.evaluatedArgs.size() >= 2) {
            id = String.valueOf(rw.evaluatedArgs.get(0));
            source = String.valueOf(rw.evaluatedArgs.get(1));
        } else if (rw.evaluatedArgs.size() == 1) {
            id = String.valueOf(rw.evaluatedArgs.get(0));
            source = firstProp(rw, "", "source", "svg", "texture");
        } else {
            return null;
        }
        if (source == null || source.isBlank()) {
            source = firstProp(rw, "", "source", "svg", "texture");
        }
        JsonObject o = new JsonObject();
        o.addProperty("type", "svg");
        o.addProperty("id", propStr(rw.evaluatedProps, "id", id));
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        putWH(o, rw.evaluatedProps, "size", 32, 32, pw, ph);
        o.addProperty("source", source == null ? "" : source);
        String color = propStr(rw.evaluatedProps, "color", null);
        if (color != null && !color.isBlank()) o.addProperty("color", color);
        String stroke = propStr(rw.evaluatedProps, "stroke", null);
        if (stroke != null && !stroke.isBlank()) o.addProperty("stroke", stroke);
        if (rw.evaluatedProps.containsKey("strokeWidth") || rw.evaluatedProps.containsKey("stroke_width")) {
            o.addProperty("strokeWidth", propFloat(rw.evaluatedProps,
                    rw.evaluatedProps.containsKey("strokeWidth") ? "strokeWidth" : "stroke_width", 0f));
        }
        if (rw.evaluatedProps.containsKey("recolor")) {
            o.addProperty("recolor", propBool(rw.evaluatedProps, "recolor", false));
        }
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);
        String tooltip = propStr(rw.evaluatedProps, "tooltip", null);
        if (tooltip != null && !tooltip.isEmpty()) o.addProperty("tooltip", tooltip);
        putFillProps(o, rw.evaluatedProps);
        putCropShapeProp(o, rw.evaluatedProps);
        return o;
    }

    private static String firstProp(RuntimeWidget rw, String def, String... keys) {
        for (String key : keys) {
            if (rw.evaluatedProps.containsKey(key)) {
                String v = propStr(rw.evaluatedProps, key, null);
                if (v != null && !v.isBlank()) return v;
            }
        }
        return def;
    }

    /** video(id, source) — video-only MP4/H.264 widget decoded on the client. */
    private static JsonObject buildVideo(RuntimeWidget rw, int pw, int ph, int order) {
        if (rw.evaluatedArgs.size() < 2) return null;
        JsonObject o = new JsonObject();
        o.addProperty("type", "video");
        o.addProperty("id", propStr(rw.evaluatedProps, "id", String.valueOf(rw.evaluatedArgs.get(0))));
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        putWH(o, rw.evaluatedProps, "size", 160, 90, pw, ph);
        o.addProperty("source", propStr(rw.evaluatedProps, "source",
                String.valueOf(rw.evaluatedArgs.get(1))));
        o.addProperty("playerEnabled", propBool(rw.evaluatedProps,
                rw.evaluatedProps.containsKey("playerEnabled") ? "playerEnabled" : "enabled", true));
        o.addProperty("playing", propBool(rw.evaluatedProps,
                rw.evaluatedProps.containsKey("playing") ? "playing" : "autoplay", true));
        o.addProperty("loop", propBool(rw.evaluatedProps, "loop", true));
        boolean audio = propBool(rw.evaluatedProps, "audio",
                propBool(rw.evaluatedProps, "videoAudio", true));
        o.addProperty("audio", audio);
        float volume = rw.evaluatedProps.containsKey("volume")
                ? propFloat(rw.evaluatedProps, "volume", 1f)
                : propFloat(rw.evaluatedProps, "videoVolume", 1f);
        o.addProperty("volume", Math.max(0f, Math.min(1f, volume)));
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);
        String tooltip = propStr(rw.evaluatedProps, "tooltip", null);
        if (tooltip != null && !tooltip.isEmpty()) o.addProperty("tooltip", tooltip);
        return o;
    }

    private static JsonObject buildRect(RuntimeWidget rw, int pw, int ph, int order) {
        String id = rw.evaluatedArgs.isEmpty() ? "rect_" + order : String.valueOf(rw.evaluatedArgs.get(0));
        JsonObject o = new JsonObject();
        o.addProperty("type", "rect");
        o.addProperty("id", id);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        putWH(o, rw.evaluatedProps, "size", 32, 32, pw, ph);
        o.addProperty("color", propStr(rw.evaluatedProps, "color", "#FFFFFFFF"));
        putStyle(o, rw.evaluatedProps);
        putFillProps(o, rw.evaluatedProps);
        putCropShapeProp(o, rw.evaluatedProps);
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);
        return o;
    }

    private static JsonObject buildClip(RuntimeWidget rw, int pw, int ph, int order, Map<String, List<CompiledScript.Instruction>> handlers) {
        String id = rw.evaluatedArgs.isEmpty() ? "clip_" + order : String.valueOf(rw.evaluatedArgs.get(0));
        JsonObject o = new JsonObject();
        o.addProperty("type", "clip");
        o.addProperty("id", id);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        putWH(o, rw.evaluatedProps, "size", pw, ph, pw, ph);
        o.addProperty("alpha", propFloat(rw.evaluatedProps, "alpha", 1.0f));
        putCropFeatherProps(o, rw.evaluatedProps);
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);

        if (rw.children != null && !rw.children.isEmpty()) {
            JsonArray children = new JsonArray();
            int childOrder = 0;
            int sw = pw;
            int sh = ph;
            if (rw.evaluatedProps.containsKey("size")) {
                Object sz = rw.evaluatedProps.get("size");
                if (sz instanceof List<?> l && l.size() >= 2) {
                    sw = toInt(l.get(0), pw);
                    sh = toInt(l.get(1), ph);
                }
            } else {
                if (rw.evaluatedProps.containsKey("w")) sw = toInt(rw.evaluatedProps.get("w"), pw);
                if (rw.evaluatedProps.containsKey("h")) sh = toInt(rw.evaluatedProps.get("h"), ph);
            }

            String childAlign = resolveChildAlignProp(rw.evaluatedProps);
            for (RuntimeWidget child : rw.children) {
                JsonObject co = buildWidget(child, sw, sh, childOrder, handlers);
                if (co != null) {
                    applyChildAlign(co, sw, sh, childAlign);
                    children.add(co);
                    childOrder++;
                }
            }
            o.add("children", children);
        }
        return o;
    }

    private static JsonObject buildGroup(RuntimeWidget rw, int pw, int ph, int order, Map<String, List<CompiledScript.Instruction>> handlers) {
        String id = rw.evaluatedArgs.isEmpty() ? "group_" + order : String.valueOf(rw.evaluatedArgs.get(0));
        JsonObject o = new JsonObject();
        o.addProperty("type", "group");
        o.addProperty("id", id);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        putWH(o, rw.evaluatedProps, "size", pw, ph, pw, ph);
        o.addProperty("alpha", propFloat(rw.evaluatedProps, "alpha", 1.0f));
        if (rw.evaluatedProps.containsKey("rotation")) {
            o.addProperty("rotation", propFloat(rw.evaluatedProps, "rotation", 0f));
        }
        if (rw.evaluatedProps.containsKey("pivotX")) {
            o.addProperty("pivotX", propFloat(rw.evaluatedProps, "pivotX", 0.5f));
        }
        if (rw.evaluatedProps.containsKey("pivotY")) {
            o.addProperty("pivotY", propFloat(rw.evaluatedProps, "pivotY", 0.5f));
        }
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);

        if (rw.children != null && !rw.children.isEmpty()) {
            JsonArray children = new JsonArray();
            int childOrder = 0;
            int sw = pw;
            int sh = ph;
            if (rw.evaluatedProps.containsKey("size")) {
                Object sz = rw.evaluatedProps.get("size");
                if (sz instanceof List<?> l && l.size() >= 2) {
                    sw = toInt(l.get(0), pw);
                    sh = toInt(l.get(1), ph);
                }
            } else {
                if (rw.evaluatedProps.containsKey("w")) sw = toInt(rw.evaluatedProps.get("w"), pw);
                if (rw.evaluatedProps.containsKey("h")) sh = toInt(rw.evaluatedProps.get("h"), ph);
            }

            String childAlign = resolveChildAlignProp(rw.evaluatedProps);
            for (RuntimeWidget child : rw.children) {
                JsonObject co = buildWidget(child, sw, sh, childOrder, handlers);
                if (co != null) {
                    applyChildAlign(co, sw, sh, childAlign);
                    children.add(co);
                    childOrder++;
                }
            }
            o.add("children", children);
        }
        return o;
    }

    private static JsonObject buildDrag(RuntimeWidget rw, int pw, int ph, int order,
                                        Map<String, List<CompiledScript.Instruction>> handlers) {
        String id = rw.evaluatedArgs.isEmpty() ? "drag_" + order : String.valueOf(rw.evaluatedArgs.get(0));
        putPrefixedEvent(handlers, rw, "onDragStart", "dragStart:" + id);
        putPrefixedEvent(handlers, rw, "onDragEnd", "dragEnd:" + id);
        JsonObject o = new JsonObject();
        o.addProperty("type", "drag");
        o.addProperty("id", id);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        putWH(o, rw.evaluatedProps, "size", pw, ph, pw, ph);
        o.addProperty("alpha", propFloat(rw.evaluatedProps, "alpha", 1.0f));
        if (!propBoolAny(rw.evaluatedProps, true, "enabled")) o.addProperty("enabled", false);
        String axis = propStrAny(rw.evaluatedProps, "xy", "axis");
        if (axis != null && !axis.isBlank() && !"xy".equalsIgnoreCase(axis)) o.addProperty("axis", axis);
        String group = propStrAny(rw.evaluatedProps, "", "group");
        if (group != null && !group.isEmpty()) o.addProperty("group", group);
        String payload = propStrAny(rw.evaluatedProps, "", "payload");
        if (payload != null && !payload.isEmpty()) o.addProperty("payload", payload);
        if (!propBoolAny(rw.evaluatedProps, true, "constrain")) o.addProperty("constrain", false);
        if (!propBoolAny(rw.evaluatedProps, true, "revert")) o.addProperty("revert", false);
        int threshold = propIntAny(rw.evaluatedProps, 4, "threshold");
        if (threshold != 4) o.addProperty("threshold", Math.max(0, threshold));
        if (rw.evaluatedProps.containsKey("rotation")) {
            o.addProperty("rotation", propFloat(rw.evaluatedProps, "rotation", 0f));
        }
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);
        putContainerChildren(o, rw, pw, ph, handlers);
        return o;
    }

    private static JsonObject buildDrop(RuntimeWidget rw, int pw, int ph, int order,
                                        Map<String, List<CompiledScript.Instruction>> handlers) {
        String id = rw.evaluatedArgs.isEmpty() ? "drop_" + order : String.valueOf(rw.evaluatedArgs.get(0));
        putPrefixedEvent(handlers, rw, "onDrop", "drop:" + id);
        JsonObject o = new JsonObject();
        o.addProperty("type", "drop");
        o.addProperty("id", id);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        putWH(o, rw.evaluatedProps, "size", pw, ph, pw, ph);
        o.addProperty("alpha", propFloat(rw.evaluatedProps, "alpha", 1.0f));
        if (!propBoolAny(rw.evaluatedProps, true, "enabled")) o.addProperty("enabled", false);
        String group = propStrAny(rw.evaluatedProps, "", "group");
        if (group != null && !group.isEmpty()) o.addProperty("group", group);
        if (rw.evaluatedProps.containsKey("rotation")) {
            o.addProperty("rotation", propFloat(rw.evaluatedProps, "rotation", 0f));
        }
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);
        putContainerChildren(o, rw, pw, ph, handlers);
        return o;
    }

    private static void putPrefixedEvent(Map<String, List<CompiledScript.Instruction>> handlers, RuntimeWidget rw,
                                         String eventName, String key) {
        if (rw.eventHandlers == null) return;
        List<CompiledScript.Instruction> body = rw.eventHandlers.get(eventName);
        if (body != null && !body.isEmpty()) handlers.put(key, body);
    }

    private static void putContainerChildren(JsonObject o, RuntimeWidget rw, int pw, int ph,
                                             Map<String, List<CompiledScript.Instruction>> handlers) {
        if (rw.children == null || rw.children.isEmpty()) return;
        JsonArray children = new JsonArray();
        int childOrder = 0;
        int sw = pw;
        int sh = ph;
        if (rw.evaluatedProps.containsKey("size")) {
            Object sz = rw.evaluatedProps.get("size");
            if (sz instanceof List<?> l && l.size() >= 2) {
                sw = toInt(l.get(0), pw);
                sh = toInt(l.get(1), ph);
            }
        } else {
            if (rw.evaluatedProps.containsKey("w")) sw = toInt(rw.evaluatedProps.get("w"), pw);
            if (rw.evaluatedProps.containsKey("h")) sh = toInt(rw.evaluatedProps.get("h"), ph);
        }
        String childAlign = resolveChildAlignProp(rw.evaluatedProps);
        for (RuntimeWidget child : rw.children) {
            JsonObject co = buildWidget(child, sw, sh, childOrder, handlers);
            if (co != null) {
                applyChildAlign(co, sw, sh, childAlign);
                children.add(co);
                childOrder++;
            }
        }
        o.add("children", children);
    }

    private static JsonObject buildScroll(RuntimeWidget rw, int pw, int ph, int order,
                                          Map<String, List<CompiledScript.Instruction>> handlers) {
        String id = rw.evaluatedArgs.isEmpty() ? "scroll_" + order : String.valueOf(rw.evaluatedArgs.get(0));
        JsonObject o = new JsonObject();
        o.addProperty("type", "scroll");
        o.addProperty("id", id);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        putWH(o, rw.evaluatedProps, "size", pw, ph, pw, ph);
        int contentH = ph;
        if (rw.evaluatedProps.containsKey("contentH")) {
            contentH = propInt(rw.evaluatedProps, "contentH", ph);
        } else if (rw.evaluatedProps.containsKey("content_h")) {
            contentH = propInt(rw.evaluatedProps, "content_h", ph);
        }
        o.addProperty("contentH", contentH);
        o.addProperty("color", propStr(rw.evaluatedProps, "color", "#00000000"));
        Object scrollbarVal = rw.evaluatedProps.get("scrollbar");
        if (scrollbarVal != null) {
            o.addProperty("scrollbar", isTruthy(scrollbarVal));
        }
        Object autoBarVal = rw.evaluatedProps.get("autoScrollbar");
        if (autoBarVal != null) {
            o.addProperty("autoScrollbar", isTruthy(autoBarVal));
        }
        putStyle(o, rw.evaluatedProps);
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);

        int sw = pw;
        Object szVal = rw.evaluatedProps.get("size");
        if (szVal instanceof List<?> list && list.size() >= 2) {
            sw = resolveDimForParent(list.get(0), pw, pw);
        }
        int sh = contentH;

        if (rw.children != null && !rw.children.isEmpty()) {
            JsonArray children = new JsonArray();
            int childOrder = 0;
            String childAlign = resolveChildAlignProp(rw.evaluatedProps);
            for (RuntimeWidget child : rw.children) {
                JsonObject co = buildWidget(child, sw, sh, childOrder, handlers);
                if (co != null) {
                    applyChildAlign(co, sw, sh, childAlign);
                    children.add(co);
                    childOrder++;
                }
            }
            o.add("children", children);
        }
        return o;
    }

    private static String resolveChildAlignProp(Map<String, Object> props) {
        String v = propStr(props, "childAlign", null);
        if (v == null) v = propStr(props, "child_align", null);
        if (v == null) v = propStr(props, "alignChildren", null);
        if (v == null) v = propStr(props, "align_children", null);
        if (v == null) v = propStr(props, "align", "top_left");
        return v;
    }

    /**
     * Layout container (same as scene UI_GRID): emits an invisible {@code group}
     * with children already placed by mode/align/flow/hug.
     */
    private static JsonObject buildGridLayout(RuntimeWidget rw, int pw, int ph, int order,
                                             Map<String, List<CompiledScript.Instruction>> handlers,
                                             String kind) {
        String id = rw.evaluatedArgs.isEmpty() ? "grid_" + order : String.valueOf(rw.evaluatedArgs.get(0));
        JsonObject o = new JsonObject();
        o.addProperty("type", "group");
        o.addProperty("id", id);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);

        String mode = propStr(rw.evaluatedProps, "mode", kind);
        if ("grid".equalsIgnoreCase(kind)) {
            mode = propStr(rw.evaluatedProps, "mode", "grid");
        } else if ("row".equalsIgnoreCase(kind) || "column".equalsIgnoreCase(kind)) {
            mode = kind.toLowerCase(Locale.ROOT);
        }
        mode = UiGridLayout.normalizeMode(mode);
        String align = propStr(rw.evaluatedProps, "align",
                propStr(rw.evaluatedProps, "gridAlign", "top_left"));
        String flow = propStr(rw.evaluatedProps, "flow", "ltr");
        boolean hug = propBool(rw.evaluatedProps, "hug",
                propBool(rw.evaluatedProps, "hugContent",
                        "row".equals(mode) || "column".equals(mode)));
        boolean stretch = propBool(rw.evaluatedProps, "stretch",
                propBool(rw.evaluatedProps, "stretchCells", !"row".equals(mode) && !"column".equals(mode)));
        int columns = propInt(rw.evaluatedProps, "columns", propInt(rw.evaluatedProps, "gridColumns", 2));
        int cellW = propInt(rw.evaluatedProps, "cellW", propInt(rw.evaluatedProps, "cellw", 100));
        int cellH = propInt(rw.evaluatedProps, "cellH", propInt(rw.evaluatedProps, "cellh", 24));
        int gapX = propInt(rw.evaluatedProps, "gapX", propInt(rw.evaluatedProps, "gapx", 4));
        int gapY = propInt(rw.evaluatedProps, "gapY", propInt(rw.evaluatedProps, "gapy", 4));

        int boxW = pw;
        int boxH = ph;
        if (rw.evaluatedProps.containsKey("size")) {
            Object sz = rw.evaluatedProps.get("size");
            if (sz instanceof List<?> l && l.size() >= 2) {
                boxW = resolveDimForParent(l.get(0), pw, pw);
                boxH = resolveDimForParent(l.get(1), ph, ph);
            }
        } else {
            if (rw.evaluatedProps.containsKey("w")) boxW = resolveDimForParent(rw.evaluatedProps.get("w"), pw, pw);
            if (rw.evaluatedProps.containsKey("h")) boxH = resolveDimForParent(rw.evaluatedProps.get("h"), ph, ph);
        }

        List<RuntimeWidget> kids = rw.children != null ? rw.children : List.of();
        List<UiGridLayout.Item> items = new ArrayList<>();
        for (RuntimeWidget child : kids) {
            items.add(measureRuntimeWidget(child, Math.max(1, boxW), Math.max(1, boxH)));
        }
        int[] content = UiGridLayout.contentSize(mode, hug, columns, cellW, cellH, gapX, gapY, items);
        int layoutW = Math.max(1, Math.max(boxW, content[0]));
        int layoutH = Math.max(1, Math.max(boxH, content[1]));
        o.addProperty("w", layoutW);
        o.addProperty("h", layoutH);
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);

        if (!kids.isEmpty()) {
            List<UiGridLayout.Placement> places = UiGridLayout.compute(
                    mode, align, flow, hug, stretch,
                    columns, cellW, cellH, gapX, gapY, layoutW, layoutH, items);
            JsonArray children = new JsonArray();
            int childOrder = 0;
            for (UiGridLayout.Placement p : places) {
                RuntimeWidget child = kids.get(p.index());
                Map<String, Object> props = child.evaluatedProps;
                Object prevPos = props.get("pos");
                Object prevSize = props.get("size");
                Object prevX = props.get("x");
                Object prevY = props.get("y");
                Object prevW = props.get("w");
                Object prevH = props.get("h");
                boolean hadPos = props.containsKey("pos");
                boolean hadSize = props.containsKey("size");
                boolean hadX = props.containsKey("x");
                boolean hadY = props.containsKey("y");
                boolean hadW = props.containsKey("w");
                boolean hadH = props.containsKey("h");
                props.put("pos", List.of(Math.round(p.x()), Math.round(p.y())));
                props.remove("x");
                props.remove("y");
                if (stretch) {
                    props.put("size", List.of(p.cellW(), p.cellH()));
                    props.remove("w");
                    props.remove("h");
                }
                try {
                    JsonObject co = buildWidget(child, layoutW, layoutH, childOrder, handlers);
                    if (co != null) {
                        children.add(co);
                        childOrder++;
                    }
                } finally {
                    restoreProp(props, "pos", prevPos, hadPos);
                    restoreProp(props, "size", prevSize, hadSize);
                    restoreProp(props, "x", prevX, hadX);
                    restoreProp(props, "y", prevY, hadY);
                    restoreProp(props, "w", prevW, hadW);
                    restoreProp(props, "h", prevH, hadH);
                }
            }
            if (children.size() > 0) o.add("children", children);
        }
        return o;
    }

    private static UiGridLayout.Item measureRuntimeWidget(RuntimeWidget child, int pw, int ph) {
        String kind = child.kind != null ? child.kind.toLowerCase(Locale.ROOT) : "";
        int dw = 32;
        int dh = 20;
        if ("slot".equals(kind)) return new UiGridLayout.Item(18, 18);
        if ("playerinventory".equals(kind) || "player_inventory".equals(kind)) {
            return new UiGridLayout.Item(162, 76);
        }
        if ("divider".equals(kind)) {
            dw = 40;
            dh = 1;
        } else if ("video".equals(kind)) {
            dw = 160;
            dh = 90;
        }
        int w = dw;
        int h = dh;
        Map<String, Object> props = child.evaluatedProps;
        if (props.containsKey("size")) {
            Object sz = props.get("size");
            if (sz instanceof List<?> l && l.size() >= 2) {
                w = resolveDimForParent(l.get(0), pw, dw);
                h = resolveDimForParent(l.get(1), ph, dh);
            }
        } else {
            if (props.containsKey("w")) w = resolveDimForParent(props.get("w"), pw, dw);
            if (props.containsKey("h")) h = resolveDimForParent(props.get("h"), ph, dh);
        }
        return new UiGridLayout.Item(w, h);
    }

    private static void restoreProp(Map<String, Object> props, String key, Object prev, boolean had) {
        if (had) props.put(key, prev);
        else props.remove(key);
    }

    private static void applyChildAlign(JsonObject co, int pw, int ph, String align) {
        if (co == null || UiChildAlign.isOrigin(align)) return;
        if (!co.has("x") || !co.has("y")) return;
        JsonElement xe = co.get("x");
        JsonElement ye = co.get("y");
        if (xe == null || ye == null || !xe.isJsonPrimitive() || !ye.isJsonPrimitive()) return;
        if (!xe.getAsJsonPrimitive().isNumber() || !ye.getAsJsonPrimitive().isNumber()) return;
        float x = xe.getAsFloat();
        float y = ye.getAsFloat();
        float w = 0f;
        float h = 0f;
        if (co.has("w") && co.get("w").isJsonPrimitive() && co.get("w").getAsJsonPrimitive().isNumber()) {
            w = co.get("w").getAsFloat();
        } else if (co.has("size") && co.get("size").isJsonPrimitive() && co.get("size").getAsJsonPrimitive().isNumber()) {
            w = co.get("size").getAsFloat();
            h = w;
        }
        if (co.has("h") && co.get("h").isJsonPrimitive() && co.get("h").getAsJsonPrimitive().isNumber()) {
            h = co.get("h").getAsFloat();
        }
        float ax = UiChildAlign.axisX(align);
        float ay = UiChildAlign.axisY(align);
        co.addProperty("x", Math.round(ax * pw + x - ax * w));
        co.addProperty("y", Math.round(ay * ph + y - ay * h));
    }

    private static JsonObject buildDivider(RuntimeWidget rw, int pw, int ph, int order) {
        String id = rw.evaluatedArgs.isEmpty() ? "div_" + order : String.valueOf(rw.evaluatedArgs.get(0));
        JsonObject o = new JsonObject();
        o.addProperty("type", "divider");
        o.addProperty("id", id);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        putWH(o, rw.evaluatedProps, "size", pw, 1, pw, ph);
        o.addProperty("color", propStr(rw.evaluatedProps, "color", "#44FFFFFF"));
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);
        return o;
    }

    private static JsonObject buildBlock(RuntimeWidget rw, int pw, int ph, int order) {
        String id = rw.evaluatedArgs.isEmpty() ? "block_" + order : String.valueOf(rw.evaluatedArgs.get(0));
        String widId = propStr(rw.evaluatedProps, "id", id);
        
        String defaultBlock = "minecraft:stone";
        if (rw.evaluatedArgs.size() >= 2) {
            defaultBlock = String.valueOf(rw.evaluatedArgs.get(1));
        } else if (rw.evaluatedArgs.size() == 1) {
            if (!rw.evaluatedProps.containsKey("item") && !rw.evaluatedProps.containsKey("block")) {
                defaultBlock = String.valueOf(rw.evaluatedArgs.get(0));
            }
        }
        String blockId = propStr(rw.evaluatedProps, "block", propStr(rw.evaluatedProps, "item", defaultBlock));

        JsonObject o = new JsonObject();
        o.addProperty("type", "block");
        o.addProperty("id", widId);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        int sz = propInt(rw.evaluatedProps, "size", propInt(rw.evaluatedProps, "w", 16));
        o.addProperty("w", sz);
        o.addProperty("block", blockId);
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);
        return o;
    }

    private static JsonObject buildItem(RuntimeWidget rw, int pw, int ph, int order) {
        String id = rw.evaluatedArgs.isEmpty() ? "item_" + order : String.valueOf(rw.evaluatedArgs.get(0));
        String widId = propStr(rw.evaluatedProps, "id", id);
        
        String defaultItem = "minecraft:stone";
        if (rw.evaluatedArgs.size() >= 2) {
            defaultItem = String.valueOf(rw.evaluatedArgs.get(1));
        } else if (rw.evaluatedArgs.size() == 1) {
            if (!rw.evaluatedProps.containsKey("item") && !rw.evaluatedProps.containsKey("block")) {
                defaultItem = String.valueOf(rw.evaluatedArgs.get(0));
            }
        }
        String itemId = propStr(rw.evaluatedProps, "item", propStr(rw.evaluatedProps, "block", defaultItem));
        
        JsonObject o = new JsonObject();
        o.addProperty("type", "item");
        o.addProperty("id", widId);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        int sz = propInt(rw.evaluatedProps, "size", propInt(rw.evaluatedProps, "w", 16));
        o.addProperty("size", sz);
        o.addProperty("item", itemId);
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);
        String tooltip = propStr(rw.evaluatedProps, "tooltip", null);
        if (tooltip != null && !tooltip.isEmpty()) o.addProperty("tooltip", tooltip);
        return o;
    }

    /** input(id[, placeholder]) — текстовое поле; клиент читает text/placeholder/color/bgColor/outlineColor/scale/maxChars/inputType. */
    private static JsonObject buildInput(RuntimeWidget rw, int pw, int ph, int order) {
        String id = rw.evaluatedArgs.isEmpty() ? "input_" + order : String.valueOf(rw.evaluatedArgs.get(0));
        JsonObject o = new JsonObject();
        o.addProperty("type", "input");
        o.addProperty("id", propStr(rw.evaluatedProps, "id", id));
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        putWH(o, rw.evaluatedProps, "size", 100, 16, pw, ph);
        String placeholder = rw.evaluatedArgs.size() >= 2
                ? String.valueOf(rw.evaluatedArgs.get(1))
                : propStr(rw.evaluatedProps, "placeholder", "");
        if (!placeholder.isEmpty()) o.addProperty("placeholder", placeholder);
        String text = propStr(rw.evaluatedProps, "text", null);
        if (text != null && !text.isEmpty()) o.addProperty("text", text);
        o.addProperty("color", propStr(rw.evaluatedProps, "color", "#FFFFFF"));
        o.addProperty("bgColor", propStr(rw.evaluatedProps, "bgColor", "#FF000000"));
        o.addProperty("outlineColor", propStr(rw.evaluatedProps, "outlineColor", "#FFAAAAAA"));
        o.addProperty("scale", propFloat(rw.evaluatedProps, "scale", 1f));
        o.addProperty("maxChars", propInt(rw.evaluatedProps,
                rw.evaluatedProps.containsKey("maxChars") ? "maxChars" : "max_chars", 32));
        String inputType = propStr(rw.evaluatedProps, "inputType", null);
        if (inputType != null && !inputType.isEmpty()) o.addProperty("inputType", inputType);
        putTextStyle(o, rw.evaluatedProps);
        putStyle(o, rw.evaluatedProps);
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);
        String tooltip = propStr(rw.evaluatedProps, "tooltip", null);
        if (tooltip != null && !tooltip.isEmpty()) o.addProperty("tooltip", tooltip);
        return o;
    }

    /** gridBg(id) — сетка-фон; клиент читает gridType (hv/h/v), cellSize, thickness, color. */
    private static JsonObject buildGridBg(RuntimeWidget rw, int pw, int ph, int order) {
        String id = rw.evaluatedArgs.isEmpty() ? "grid_" + order : String.valueOf(rw.evaluatedArgs.get(0));
        JsonObject o = new JsonObject();
        o.addProperty("type", "gridBg");
        o.addProperty("id", propStr(rw.evaluatedProps, "id", id));
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        putWH(o, rw.evaluatedProps, "size", pw, ph, pw, ph);
        o.addProperty("gridType", propStr(rw.evaluatedProps, "gridType", "hv"));
        o.addProperty("cellSize", propInt(rw.evaluatedProps, "cellSize", 20));
        o.addProperty("thickness", propInt(rw.evaluatedProps, "thickness", 1));
        o.addProperty("color", propStr(rw.evaluatedProps, "color", "#44FFFFFF"));
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);
        return o;
    }

    private static JsonObject buildSlot(RuntimeWidget rw, int pw, int ph, int order) {        String id = rw.evaluatedArgs.isEmpty() ? "slot_" + order : String.valueOf(rw.evaluatedArgs.get(0));
        JsonObject o = new JsonObject();
        o.addProperty("type", "slot");
        o.addProperty("id", propStr(rw.evaluatedProps, "id", id));
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        o.addProperty("order", order);
        return o;
    }

    private static JsonObject buildPlayerInventory(RuntimeWidget rw, int pw, int ph, int order) {
        String id = rw.evaluatedArgs.isEmpty() ? "inv_" + order : String.valueOf(rw.evaluatedArgs.get(0));
        JsonObject o = new JsonObject();
        o.addProperty("type", "playerInventory");
        o.addProperty("id", propStr(rw.evaluatedProps, "id", id));
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        o.addProperty("order", order);
        return o;
    }

    /** Image/panel fill: direction + amount + radial origin. */
    private static void putFillProps(JsonObject o, Map<String, Object> props) {
        String fill = propStr(props, props.containsKey("fill") ? "fill"
                : (props.containsKey("fillMode") ? "fillMode" : "fill_mode"), null);
        if (fill != null && !fill.isBlank()) o.addProperty("fill", fill.trim());
        if (props.containsKey("fillAmount") || props.containsKey("fill_amount")) {
            o.addProperty("fillAmount", propFloat(props,
                    props.containsKey("fillAmount") ? "fillAmount" : "fill_amount", 1f));
        }
        String origin = propStr(props, props.containsKey("fillOrigin") ? "fillOrigin" : "fill_origin", null);
        if (origin != null && !origin.isBlank()) o.addProperty("fillOrigin", origin.trim());
    }

    private static void putCropShapeProp(JsonObject o, Map<String, Object> props) {
        String key = props.containsKey("cropShape") ? "cropShape"
                : (props.containsKey("crop_shape") ? "crop_shape"
                : (props.containsKey("srcShape") ? "srcShape" : "src_shape"));
        String shape = propStr(props, key, null);
        if (shape != null && !shape.isBlank() && !"rect".equalsIgnoreCase(shape.trim())) {
            o.addProperty("cropShape", shape.trim());
        }
    }

    private static void putCropFeatherProps(JsonObject o, Map<String, Object> props) {
        String featherKey = firstPropKey(props, "feather", "cropFeather", "crop_feather", "edgeBlur", "edge_blur");
        if (featherKey != null) {
            float v = propFloat(props, featherKey, 0f);
            if (v > 0.01f) o.addProperty("feather", v);
        }
        putOptionalFloat(o, props, "featherL", "featherLeft", "feather_l", "feather_left");
        putOptionalFloat(o, props, "featherT", "featherTop", "feather_t", "feather_top");
        putOptionalFloat(o, props, "featherR", "featherRight", "feather_r", "feather_right");
        putOptionalFloat(o, props, "featherB", "featherBottom", "feather_b", "feather_bottom");
        String smoothKey = firstPropKey(props, "featherSmooth", "feather_smooth");
        if (smoothKey != null) o.addProperty("featherSmooth", propBool(props, smoothKey, true));
    }

    private static void putOptionalFloat(JsonObject o, Map<String, Object> props, String... keys) {
        String key = firstPropKey(props, keys);
        if (key == null) return;
        o.addProperty(keys[0], propFloat(props, key, 1f));
    }

    private static String firstPropKey(Map<String, Object> props, String... keys) {
        if (props == null) return null;
        for (String key : keys) {
            if (props.containsKey(key)) return key;
        }
        return null;
    }

    /** Copies extra style props (rounding, gradient, border, shadow) into the widget JSON. */
    private static void putTextStyle(JsonObject o, Map<String, Object> props) {
        String effects = propStr(props, props.containsKey("effects") ? "effects"
                : (props.containsKey("textEffects") ? "textEffects" : "text_effects"), null);
        if (effects != null && !effects.isBlank()) o.addProperty("effects", effects);
        String gradient = propStr(props, props.containsKey("textGradient") ? "textGradient" : "text_gradient", null);
        if (gradient != null && !gradient.isBlank()) o.addProperty("textGradient", gradient);
        String outline = propStr(props, props.containsKey("textOutlineColor") ? "textOutlineColor"
                : (props.containsKey("outlineColor") ? "outlineColor" : "outline_color"), null);
        if (outline != null && !outline.isBlank()) o.addProperty("textOutlineColor", outline);
        String glow = propStr(props, props.containsKey("textGlowColor") ? "textGlowColor"
                : (props.containsKey("glowColor") ? "glowColor" : "glow_color"), null);
        if (glow != null && !glow.isBlank()) o.addProperty("textGlowColor", glow);
        if (props.containsKey("effectSpeed") || props.containsKey("effect_speed")) {
            o.addProperty("textEffectSpeed", propFloat(props,
                    props.containsKey("effectSpeed") ? "effectSpeed" : "effect_speed", 1f));
        }
        if (props.containsKey("effectAmount") || props.containsKey("effect_amount")) {
            o.addProperty("textEffectAmount", propFloat(props,
                    props.containsKey("effectAmount") ? "effectAmount" : "effect_amount", 1f));
        }
        if (props.containsKey("textGlowSize") || props.containsKey("text_glow_size")
                || props.containsKey("glowSize") || props.containsKey("glow_size")) {
            String key = props.containsKey("textGlowSize") ? "textGlowSize"
                    : (props.containsKey("text_glow_size") ? "text_glow_size"
                    : (props.containsKey("glowSize") ? "glowSize" : "glow_size"));
            o.addProperty("textGlowSize", propFloat(props, key, 3f));
        }
        if (props.containsKey("textOutlineWidth") || props.containsKey("text_outline_width")
                || props.containsKey("outlineWidth") || props.containsKey("outline_width")) {
            String key = props.containsKey("textOutlineWidth") ? "textOutlineWidth"
                    : (props.containsKey("text_outline_width") ? "text_outline_width"
                    : (props.containsKey("outlineWidth") ? "outlineWidth" : "outline_width"));
            o.addProperty("textOutlineWidth", propFloat(props, key, 1f));
        }
        copyTextColorProp(o, props, "textUnderlineColor", "textUnderlineColor", "text_underline_color",
                "underlineColor", "underline_color");
        copyTextFloatProp(o, props, "textUnderlineThickness", 1f,
                "textUnderlineThickness", "text_underline_thickness",
                "underlineThickness", "underline_thickness");
        copyTextFloatProp(o, props, "textUnderlineOffset", 0f,
                "textUnderlineOffset", "text_underline_offset",
                "underlineOffset", "underline_offset");
        copyTextColorProp(o, props, "textShadowColor", "textShadowColor", "text_shadow_color",
                "shadowColor", "shadow_color");
        copyTextFloatProp(o, props, "textShadowOffsetX", 1f,
                "textShadowOffsetX", "text_shadow_offset_x",
                "shadowOffsetX", "shadow_offset_x");
        copyTextFloatProp(o, props, "textShadowOffsetY", 1f,
                "textShadowOffsetY", "text_shadow_offset_y",
                "shadowOffsetY", "shadow_offset_y");
        copyTextFloatProp(o, props, "textShadowSize", 2f,
                "textShadowSize", "text_shadow_size");
        for (String flag : List.of("bold", "italic", "underline", "strikethrough", "code", "shadow",
                "outline", "glow", "rainbow", "pulse", "wave", "shake", "wiggle",
                "glow_pixel", "glow_soft", "outline_pixel", "outline_soft", "shadow_pixel", "shadow_soft")) {
            if (props.containsKey(flag)) o.addProperty(flag, propBool(props, flag, false));
        }
        if (props.containsKey("textShadow")) {
            o.addProperty("shadow", propBool(props, "textShadow", false));
        }
    }

    private static void copyTextColorProp(JsonObject o, Map<String, Object> props,
                                          String jsonKey, String... keys) {
        for (String key : keys) {
            if (!props.containsKey(key)) continue;
            String value = propStr(props, key, null);
            if (value != null && !value.isBlank()) o.addProperty(jsonKey, value);
            return;
        }
    }

    private static void copyTextFloatProp(JsonObject o, Map<String, Object> props,
                                          String jsonKey, float fallback, String... keys) {
        for (String key : keys) {
            if (!props.containsKey(key)) continue;
            o.addProperty(jsonKey, propFloat(props, key, fallback));
            return;
        }
    }

    /** Copies extra style props (rounding, gradient, border, shadow) into the widget JSON. */
    private static void putStyle(JsonObject o, Map<String, Object> props) {
        if (props.containsKey("radius")) {
            o.addProperty("radius", propFloat(props, "radius", 0f));
        }
        String gradient = propStr(props, "gradient", null);
        if (gradient != null && !gradient.isEmpty()) {
            o.addProperty("gradient", gradient);
            String dir = propStr(props, props.containsKey("gradientDir") ? "gradientDir" : "gradient_dir", null);
            if (dir != null && !dir.isEmpty()) o.addProperty("gradientDir", dir);
        }
        if (props.containsKey("border")) {
            o.addProperty("border", propFloat(props, "border", 0f));
        }
        String borderColor = propStr(props, props.containsKey("borderColor") ? "borderColor" : "border_color", null);
        if (borderColor != null && !borderColor.isEmpty()) {
            o.addProperty("borderColor", borderColor);
        }
        String borderGradient = propStr(props, "borderGradient", null);
        if (borderGradient != null && !borderGradient.isEmpty()) {
            o.addProperty("borderGradient", borderGradient);
            String dir = propStr(props, "borderGradientDir", null);
            if (dir != null && !dir.isEmpty()) o.addProperty("borderGradientDir", dir);
        }
        if (props.containsKey("outerBorder") || props.containsKey("outer_border")) {
            o.addProperty("outerBorder", propFloat(props,
                    props.containsKey("outerBorder") ? "outerBorder" : "outer_border", 0f));
        }
        String outerBorderColor = propStr(props,
                props.containsKey("outerBorderColor") ? "outerBorderColor" : "outer_border_color", null);
        if (outerBorderColor != null && !outerBorderColor.isEmpty()) {
            o.addProperty("outerBorderColor", outerBorderColor);
        }
        String outerBorderGradient = propStr(props, "outerBorderGradient", null);
        if (outerBorderGradient != null && !outerBorderGradient.isEmpty()) {
            o.addProperty("outerBorderGradient", outerBorderGradient);
            String dir = propStr(props, "outerBorderGradientDir", null);
            if (dir != null && !dir.isEmpty()) o.addProperty("outerBorderGradientDir", dir);
        }
        if (props.containsKey("shadow")) {
            o.addProperty("shadow", propFloat(props, "shadow", 0f));
        }
    }

    private static boolean isTruthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        String s = String.valueOf(v).trim().toLowerCase();
        return !s.isEmpty() && !"false".equals(s) && !"0".equals(s);
    }

    private static void putXY(JsonObject o, Map<String, Object> props, String key, int pw, int ph) {
        Object p = props.get(key);
        if (p instanceof List<?> list && list.size() >= 2) {
            putCoord(o, "x", list.get(0), pw);
            putCoord(o, "y", list.get(1), ph);
        } else {
            if (props.containsKey("x")) {
                putCoord(o, "x", props.get("x"), pw);
            } else {
                o.addProperty("x", 0);
            }
            if (props.containsKey("y")) {
                putCoord(o, "y", props.get("y"), ph);
            } else {
                o.addProperty("y", 0);
            }
        }
    }

    private static void putWH(JsonObject o, Map<String, Object> props, String key,
                              int dw, int dh, int pw, int ph) {
        Object p = props.get(key);
        if (p instanceof List<?> list && list.size() >= 2) {
            putCoord(o, "w", list.get(0), pw);
            putCoord(o, "h", list.get(1), ph);
        } else {
            if (props.containsKey("w")) {
                putCoord(o, "w", props.get("w"), pw);
            } else {
                o.addProperty("w", dw);
            }
            if (props.containsKey("h")) {
                putCoord(o, "h", props.get("h"), ph);
            } else {
                o.addProperty("h", dh);
            }
        }
    }

    private static void putCoord(JsonObject o, String axis, Object v, int panelSize) {
        if (v == null) {
            o.addProperty(axis, 0);
            return;
        }
        if (v instanceof Number n) {
            o.addProperty(axis, n.intValue());
            return;
        }
        String s = String.valueOf(v).trim();
        if (s.endsWith("%") && panelSize > 0) {
            o.addProperty(axis, s);
            return;
        }
        try {
            o.addProperty(axis, Integer.parseInt(s));
        } catch (NumberFormatException e) {
            o.addProperty(axis, s);
        }
    }

    private static int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Root {@code size = [ ... ]} entry for JSON {@code w}/{@code h}: percentages must stay as strings so the client
     * can resolve them against the real window (server-side {@link #toInt} would fall back to 200×150 and shrink the UI).
     */
    private static JsonElement rootSizeToJson(Object o, int intFallback) {
        if (o == null) {
            return new JsonPrimitive(intFallback);
        }
        if (o instanceof Number n) {
            return new JsonPrimitive(n.intValue());
        }
        String s = String.valueOf(o).trim();
        if (s.endsWith("%")) {
            return new JsonPrimitive(s);
        }
        try {
            return new JsonPrimitive(Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return new JsonPrimitive(toInt(o, intFallback));
        }
    }

    /** Resolve widget width/height from literal number or {@code "25%"} against parent size (for child widget JSON). */
    private static int resolveDimForParent(Object o, int parentSize, int def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        String s = String.valueOf(o).trim();
        if (s.endsWith("%") && parentSize > 0) {
            try {
                float pct = Float.parseFloat(s.substring(0, s.length() - 1).trim());
                return Math.max(0, (int) (pct / 100f * parentSize));
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return toInt(o, def);
    }

    private static float propFloat(Map<String, Object> props, String key, float def) {
        Object v = props.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n.floatValue();
        try {
            return Float.parseFloat(String.valueOf(v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int propInt(Map<String, Object> props, String key, int def) {
        Object v = props.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String propStr(Map<String, Object> props, String key, String def) {
        Object v = props.get(key);
        if (v == null) return def;
        String s = String.valueOf(v);
        return s.isEmpty() ? def : s;
    }

    /** Normalize 0xRRGGBB / #RGB / packed ints to {@code #AARRGGBB} for the client. */
    private static String propColor(Map<String, Object> props, String key, String def) {
        Object v = props.get(key);
        if (v == null) return def;
        if (v instanceof String s && s.isBlank()) return def;
        int fallback = SprauteColors.toArgbInt(def, 0xFFEAEAEA);
        return String.format("#%08X", SprauteColors.toArgbInt(v, fallback));
    }

    private static boolean propBool(Map<String, Object> props, String key, boolean def) {
        Object v = props.get(key);
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim().toLowerCase();
        return "true".equals(s) || "1".equals(s);
    }

    private static String propStrAny(Map<String, Object> props, String def, String... keys) {
        for (String key : keys) {
            if (props.containsKey(key)) return propStr(props, key, def);
        }
        return def;
    }

    private static boolean propBoolAny(Map<String, Object> props, boolean def, String... keys) {
        for (String key : keys) {
            if (props.containsKey(key)) return propBool(props, key, def);
        }
        return def;
    }

    private static int propIntAny(Map<String, Object> props, int def, String... keys) {
        for (String key : keys) {
            if (props.containsKey(key)) return propInt(props, key, def);
        }
        return def;
    }
}
