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
        if (rootProps.containsKey("can_close")) {
            Object v = eval.apply(rootProps.get("can_close"));
            boolean canClose = !(v instanceof Boolean b) || b;
            root.addProperty("can_close", canClose);
        }

        JsonArray arr = new JsonArray();
        Map<String, List<CompiledScript.Instruction>> handlers = new HashMap<>();
        int order = 0;

        for (RuntimeWidget rw : widgets) {
            JsonObject o = buildWidget(rw, w, h, order, handlers);
            if (o != null) {
                arr.add(o);
                order++;
            }
        }
        root.add("widgets", arr);
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
            case "scroll" -> buildScroll(rw, pw, ph, order, handlers);
            case "divider" -> buildDivider(rw, pw, ph, order);
            case "block" -> buildBlock(rw, pw, ph, order);
            case "item" -> buildItem(rw, pw, ph, order);
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
                o.addProperty("anchor_x", clamp01(toFloat(list.get(0))));
                o.addProperty("anchor_y", clamp01(toFloat(list.get(1))));
            } else if (a instanceof String str) {
                o.addProperty("anchor", str);
            }
        } else {
            if (rw.evaluatedProps.containsKey("anchor_x")) {
                o.addProperty("anchor_x", propFloat(rw.evaluatedProps, "anchor_x", 0f));
            }
            if (rw.evaluatedProps.containsKey("anchor_y")) {
                o.addProperty("anchor_y", propFloat(rw.evaluatedProps, "anchor_y", 0f));
            }
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
        List<CompiledScript.Instruction> onClick = rw.eventHandlers != null ? rw.eventHandlers.get("on_click") : null;
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
        int labelWrap = propInt(rw.evaluatedProps, "label_wrap", 0);
        if (labelWrap > 0) {
            o.addProperty("label_wrap", labelWrap);
        }
        if (rw.evaluatedProps.containsKey("label_scale")) {
            o.addProperty("label_scale", propFloat(rw.evaluatedProps, "label_scale", 1f));
        }
        String sub = propStr(rw.evaluatedProps, "sub_label", null);
        if (sub != null && !sub.isEmpty()) {
            o.addProperty("sub_label", sub);
        }
        if (rw.evaluatedProps.containsKey("sub_scale")) {
            o.addProperty("sub_scale", propFloat(rw.evaluatedProps, "sub_scale", 0.65f));
        }
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);
        String tooltip = propStr(rw.evaluatedProps, "tooltip", null);
        if (tooltip != null && !tooltip.isEmpty()) o.addProperty("tooltip", tooltip);
        return o;
    }

    private static JsonObject buildEntity(RuntimeWidget rw, int pw, int ph, int order) {
        if (rw.evaluatedArgs.isEmpty()) return null;
        String entity = String.valueOf(rw.evaluatedArgs.get(0));
        JsonObject o = new JsonObject();
        o.addProperty("type", "entity");
        String wid = propStr(rw.evaluatedProps, "id", "entity_" + order);
        o.addProperty("id", wid);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        putWH(o, rw.evaluatedProps, "size", 64, 96, pw, ph);
        o.addProperty("entity", entity);
        o.addProperty("scale", propFloat(rw.evaluatedProps, "scale", 1f));
        o.addProperty("feet_crop", propFloat(rw.evaluatedProps, "feet_crop", 0.38f));
        putCropAndAnchor(o, rw.evaluatedProps);
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
                o.addProperty("anchor_x", clamp01(toFloat(list.get(0))));
                float ay = toFloat(list.get(1));
                o.addProperty("anchor_y", ay < 0f ? -1f : clamp01(ay));
            } else if (a instanceof String str) {
                o.addProperty("anchor", str);
            }
        } else {
            if (props.containsKey("anchor_x")) {
                o.addProperty("anchor_x", propFloat(props, "anchor_x", 0.5f));
            }
            if (props.containsKey("anchor_y")) {
                o.addProperty("anchor_y", propFloat(props, "anchor_y", 0.5f));
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
        if (rw.evaluatedArgs.size() < 2) return null;
        String id = String.valueOf(rw.evaluatedArgs.get(0));
        String texture = String.valueOf(rw.evaluatedArgs.get(1));
        JsonObject o = new JsonObject();
        o.addProperty("type", "image");
        o.addProperty("id", id);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        putWH(o, rw.evaluatedProps, "size", 32, 32, pw, ph);
        o.addProperty("texture", texture);
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);
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

    private static JsonObject buildScroll(RuntimeWidget rw, int pw, int ph, int order,
                                          Map<String, List<CompiledScript.Instruction>> handlers) {
        String id = rw.evaluatedArgs.isEmpty() ? "scroll_" + order : String.valueOf(rw.evaluatedArgs.get(0));
        JsonObject o = new JsonObject();
        o.addProperty("type", "scroll");
        o.addProperty("id", id);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        putWH(o, rw.evaluatedProps, "size", pw, ph, pw, ph);
        int contentH = propInt(rw.evaluatedProps, "content_h", ph);
        o.addProperty("content_h", contentH);
        o.addProperty("color", propStr(rw.evaluatedProps, "color", "#00000000"));
        Object scrollbarVal = rw.evaluatedProps.get("scrollbar");
        if (scrollbarVal != null) {
            o.addProperty("scrollbar", isTruthy(scrollbarVal));
        }
        Object autoBarVal = rw.evaluatedProps.get("auto_scrollbar");
        if (autoBarVal != null) {
            o.addProperty("auto_scrollbar", isTruthy(autoBarVal));
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
                    case "divider" -> buildDivider(child, sw, sh, childOrder);
                    case "block" -> buildBlock(child, sw, sh, childOrder);
                    case "item" -> buildItem(child, sw, sh, childOrder);
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
        }
        String blockId = propStr(rw.evaluatedProps, "block", propStr(rw.evaluatedProps, "item", defaultBlock));

        JsonObject o = new JsonObject();
        o.addProperty("type", "block");
        o.addProperty("id", widId);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        int sz = propInt(rw.evaluatedProps, "size", 16);
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
        }
        String itemId = propStr(rw.evaluatedProps, "item", propStr(rw.evaluatedProps, "block", defaultItem));
        
        JsonObject o = new JsonObject();
        o.addProperty("type", "item");
        o.addProperty("id", widId);
        putXY(o, rw.evaluatedProps, "pos", pw, ph);
        int sz = propInt(rw.evaluatedProps, "size", 16);
        o.addProperty("size", sz);
        o.addProperty("item", itemId);
        o.addProperty("layer", propInt(rw.evaluatedProps, "layer", 0));
        o.addProperty("order", order);
        String tooltip = propStr(rw.evaluatedProps, "tooltip", null);
        if (tooltip != null && !tooltip.isEmpty()) o.addProperty("tooltip", tooltip);
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
            o.addProperty("x", 0);
            o.addProperty("y", 0);
        }
    }

    private static void putWH(JsonObject o, Map<String, Object> props, String key,
                              int dw, int dh, int pw, int ph) {
        Object p = props.get(key);
        if (p instanceof List<?> list && list.size() >= 2) {
            putCoord(o, "w", list.get(0), pw);
            putCoord(o, "h", list.get(1), ph);
        } else {
            o.addProperty("w", dw);
            o.addProperty("h", dh);
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
            o.addProperty(axis, 0);
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
}
