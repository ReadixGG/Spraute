package org.zonarstudio.spraute_engine.scene.custom;

import org.zonarstudio.spraute_engine.registry.CustomBlockRegistry;
import org.zonarstudio.spraute_engine.scene.SceneNode;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps {@code declare node type} with {@code creates = item} onto {@code create item}
 * (same registry as RES_ITEM). Field convention:
 * {@code name}, {@code texture}, {@code slot}/{@code armor_type}, {@code tab}.
 */
public final class CustomNodeItemBridge {
    private static final Pattern NAME = Pattern.compile("name\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern TEXTURE = Pattern.compile("texture\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern TAB = Pattern.compile("tab\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern SLOT = Pattern.compile("(?:slot|armor_type)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern HIDE_ARMOR = Pattern.compile("hide_armor\\s*=\\s*(?:\"(true|false)\"|(true|false))", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARMOR_MODEL = Pattern.compile("armor_model\\s*=\\s*(?:\"(true|false)\"|(true|false))", Pattern.CASE_INSENSITIVE);
    private static final Pattern OVERLAY = Pattern.compile("(?:overlay|skin_overlay)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern HIDE_LAYER = Pattern.compile("hide_layer\\s*=\\s*\"([^\"]*)\"");

    private CustomNodeItemBridge() {}

    public static CustomBlockRegistry.CustomItemDef fromNode(SceneNode node) {
        if (node == null) return null;
        return fromFields(node.id, node.customData);
    }

    public static CustomBlockRegistry.CustomItemDef fromCreateBody(String id, String body) {
        if (id == null || id.isBlank() || body == null) return null;
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        Matcher nameM = NAME.matcher(body);
        if (nameM.find()) data.put("name", nameM.group(1));
        Matcher texM = TEXTURE.matcher(body);
        if (texM.find()) data.put("texture", texM.group(1));
        Matcher tabM = TAB.matcher(body);
        if (tabM.find()) data.put("tab", tabM.group(1));
        Matcher slotM = SLOT.matcher(body);
        if (slotM.find()) data.put("slot", slotM.group(1));
        Matcher hideM = HIDE_ARMOR.matcher(body);
        if (hideM.find()) data.put("hide_armor", firstGroup(hideM));
        Matcher modelM = ARMOR_MODEL.matcher(body);
        if (modelM.find()) data.put("armor_model", firstGroup(modelM));
        Matcher ovM = OVERLAY.matcher(body);
        if (ovM.find()) data.put("overlay", ovM.group(1));
        Matcher hideLayerM = HIDE_LAYER.matcher(body);
        if (hideLayerM.find()) data.put("hide_layer", hideLayerM.group(1));
        return fromFields(id, data);
    }

    public static CustomBlockRegistry.CustomItemDef fromFields(String id, Map<String, Object> data) {
        if (id == null || id.isBlank()) return null;
        if (!id.matches("[A-Za-z_][A-Za-z0-9_]*")) return null;
        CustomBlockRegistry.CustomItemDef def = new CustomBlockRegistry.CustomItemDef();
        def.id = id;
        def.displayName = str(data, "name", "displayName", "display_name");
        def.texture = str(data, "texture", "item_texture");
        def.tab = str(data, "tab");
        String slot = str(data, "slot", "armor_type", "armorType");
        def.armorType = normalizeArmorSlot(slot);
        if (def.armorType != null) {
            def.maxStackSize = 1;
            Boolean hide = bool(data, "hide_armor", "hideArmor");
            if (hide != null) {
                def.hideArmor = hide;
            } else {
                Boolean show = bool(data, "armor_model", "show_armor", "armorModel");
                def.hideArmor = show == null || !show;
            }
            def.skinOverlay = str(data, "overlay", "skin_overlay", "skinOverlay");
            def.skinHideLayer = str(data, "hide_layer", "hideLayer");
        }
        return def;
    }

    public static String normalizeArmorSlot(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "helmet", "head", "шлем" -> "helmet";
            case "chestplate", "chest", "body", "нагрудник" -> "chestplate";
            case "leggings", "legs", "поножи" -> "leggings";
            case "boots", "feet", "ботинки" -> "boots";
            default -> null;
        };
    }

    private static String firstGroup(Matcher m) {
        if (m.group(1) != null) return m.group(1);
        return m.group(2);
    }

    private static String str(Map<String, Object> data, String... keys) {
        if (data == null) return null;
        for (String key : keys) {
            Object v = data.get(key);
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty() && !"null".equals(s)) return s;
        }
        return null;
    }

    private static Boolean bool(Map<String, Object> data, String... keys) {
        if (data == null) return null;
        for (String key : keys) {
            Object v = data.get(key);
            if (v == null) continue;
            if (v instanceof Boolean flag) return flag;
            String s = String.valueOf(v).trim();
            if (s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("yes")) return true;
            if (s.equalsIgnoreCase("false") || s.equals("0") || s.equalsIgnoreCase("no")) return false;
        }
        return null;
    }
}
