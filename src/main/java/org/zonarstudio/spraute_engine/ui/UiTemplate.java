package org.zonarstudio.spraute_engine.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.zonarstudio.spraute_engine.script.CompiledScript;
import org.zonarstudio.spraute_engine.script.ScriptNode;

import java.util.HashMap;
import java.util.List;
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
            case "rect", "panel" -> buildRect(rw, pw, ph, order);
            case "clip" -> buildClip(rw, pw, ph, order, handlers);
            case "group" -> buildGroup(rw, pw, ph, order, handlers);
            case "scroll" -> buildScroll(rw, pw, ph, order, handlers);
            case "divider" -> buildDivider(rw, pw, ph, order);
            case "block" -> buildBlock(rw, pw, ph, order);
            case "item" -> buildItem(rw, pw, ph, order);
            case "slot" -> buildSlot(rw, pw, ph, order);
            case "input" -> buildInput(rw, pw, ph, order);
            case "gridbg", "grid_bg" -> buildGridBg(rw, pw, ph, order);
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
        o.addProperty("color", propStr(rw.evaluatedProps, "color", "#EAEAEA"));
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
        if (labelWrap > 0) {
            o.addProperty("labelWrap", labelWrap);
        }
        if (rw.evaluatedProps.containsKey("labelScale")) {
            o.addProperty("labelScale", propFloat(rw.evaluatedProps, "labelScale", 1f));
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
        }
        
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

            for (RuntimeWidget child : rw.children) {
                String ck = child.kind != null ? child.kind.toLowerCase() : "";
                JsonObject co = switch (ck) {
                    case "text" -> buildText(child, sw, sh, childOrder);
                    case "button" -> buildButton(child, sw, sh, childOrder, handlers);
                    case "entity" -> buildEntity(child, sw, sh, childOrder);
                    case "image" -> buildImage(child, sw, sh, childOrder);
                    case "rect", "panel" -> buildRect(child, sw, sh, childOrder);
                    case "clip" -> buildClip(child, sw, sh, childOrder, handlers);
                    case "group" -> buildGroup(child, sw, sh, childOrder, handlers);
                    case "scroll" -> buildScroll(child, sw, sh, childOrder, handlers);
                    case "divider" -> buildDivider(child, sw, sh, childOrder);
                    case "block" -> buildBlock(child, sw, sh, childOrder);
                    case "item" -> buildItem(child, sw, sh, childOrder);
                    case "slot" -> buildSlot(child, sw, sh, childOrder);
                    case "input" -> buildInput(child, sw, sh, childOrder);
                    case "gridbg", "grid_bg" -> buildGridBg(child, sw, sh, childOrder);
                    case "playerInventory", "player_inventory" -> buildPlayerInventory(child, sw, sh, childOrder);
                    default -> null;
                };
                if (co != null) {
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

            for (RuntimeWidget child : rw.children) {
                String ck = child.kind != null ? child.kind.toLowerCase() : "";
                JsonObject co = switch (ck) {
                    case "text" -> buildText(child, sw, sh, childOrder);
                    case "button" -> buildButton(child, sw, sh, childOrder, handlers);
                    case "entity" -> buildEntity(child, sw, sh, childOrder);
                    case "image" -> buildImage(child, sw, sh, childOrder);
                    case "rect", "panel" -> buildRect(child, sw, sh, childOrder);
                    case "clip" -> buildClip(child, sw, sh, childOrder, handlers);
                    case "group" -> buildGroup(child, sw, sh, childOrder, handlers);
                    case "scroll" -> buildScroll(child, sw, sh, childOrder, handlers);
                    case "divider" -> buildDivider(child, sw, sh, childOrder);
                    case "block" -> buildBlock(child, sw, sh, childOrder);
                    case "item" -> buildItem(child, sw, sh, childOrder);
                    case "slot" -> buildSlot(child, sw, sh, childOrder);
                    case "input" -> buildInput(child, sw, sh, childOrder);
                    case "gridbg", "grid_bg" -> buildGridBg(child, sw, sh, childOrder);
                    case "playerInventory", "player_inventory" -> buildPlayerInventory(child, sw, sh, childOrder);
                    default -> null;
                };
                if (co != null) {
                    children.add(co);
                    childOrder++;
                }
            }
            o.add("children", children);
        }
        return o;
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
            for (RuntimeWidget child : rw.children) {
                String ck = child.kind != null ? child.kind.toLowerCase() : "";
                JsonObject co = switch (ck) {
                    case "text" -> buildText(child, sw, sh, childOrder);
                    case "button" -> buildButton(child, sw, sh, childOrder, handlers);
                    case "entity" -> buildEntity(child, sw, sh, childOrder);
                    case "image" -> buildImage(child, sw, sh, childOrder);
                    case "rect", "panel" -> buildRect(child, sw, sh, childOrder);
                    case "clip" -> buildClip(child, sw, sh, childOrder, handlers);
                    case "divider" -> buildDivider(child, sw, sh, childOrder);
                    case "block" -> buildBlock(child, sw, sh, childOrder);
                    case "item" -> buildItem(child, sw, sh, childOrder);
                    case "slot" -> buildSlot(child, sw, sh, childOrder);
                    case "input" -> buildInput(child, sw, sh, childOrder);
                    case "gridbg", "grid_bg" -> buildGridBg(child, sw, sh, childOrder);
                    case "playerInventory", "player_inventory" -> buildPlayerInventory(child, sw, sh, childOrder);
                    default -> null;
                };
                if (co != null) {
                    children.add(co);
                    childOrder++;
                }
            }
            o.add("children", children);
        }
        return o;
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

    private static boolean propBool(Map<String, Object> props, String key, boolean def) {
        Object v = props.get(key);
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim().toLowerCase();
        return "true".equals(s) || "1".equals(s);
    }
}
