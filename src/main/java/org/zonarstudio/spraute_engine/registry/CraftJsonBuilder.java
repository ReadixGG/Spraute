package org.zonarstudio.spraute_engine.registry;

import org.zonarstudio.spraute_engine.Spraute_engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds Minecraft recipe JSON for script {@code create craft} and scene {@code RES_CRAFT} nodes.
 * Writes synthetic item tags into {@link CustomBlockRegistry#CUSTOM_CRAFT_TAGS_JSON}.
 */
public final class CraftJsonBuilder {
    private CraftJsonBuilder() {}

    public static String normalizeType(String type) {
        if (type == null) return "shaped";
        return switch (type.toLowerCase()) {
            case "any", "shapeless" -> "shapeless";
            case "slots", "shaped", "grid" -> "shaped";
            default -> type.toLowerCase();
        };
    }

    public static String resolveItemId(String item) {
        if (item == null || item.isEmpty()) return "minecraft:barrier";
        String id = org.zonarstudio.spraute_engine.script.ItemStackScriptUtil.parseSpec(item).id();
        if (id.isEmpty()) return "minecraft:barrier";
        if (!id.contains(":")) return Spraute_engine.MODID + ":" + id;
        return id;
    }

    public static String resolveTagId(String tag) {
        String t = tag.startsWith("#") ? tag.substring(1) : tag;
        if (!t.contains(":")) return "minecraft:" + t;
        return t;
    }

    /**
     * Ingredient spec: bare item id, {@code #tag}, or JSON-ish {@code ["a","b"]}.
     * Also accepts quoted forms from script parsing.
     */
    public static String ingredientToJson(String spec, String recipeId, String slotKey) {
        String raw = spec == null ? "" : spec.trim();
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            raw = raw.substring(1, raw.length() - 1);
        }
        if (raw.startsWith("#")) {
            return "{\"tag\": \"" + resolveTagId(raw) + "\"}";
        }
        if (raw.startsWith("[")) {
            List<String> items = parseQuotedStrings(raw);
            if (items.isEmpty()) return "{\"item\": \"minecraft:barrier\"}";
            if (items.size() == 1) {
                return "{\"item\": \"" + resolveItemId(items.get(0)) + "\"}";
            }
            String tagId = "craft_" + recipeId + "_" + slotKey;
            StringBuilder tagJson = new StringBuilder("{\"replace\": false, \"values\": [");
            boolean first = true;
            for (String item : items) {
                if (!first) tagJson.append(",");
                first = false;
                tagJson.append("\"").append(resolveItemId(item)).append("\"");
            }
            tagJson.append("]}");
            CustomBlockRegistry.CUSTOM_CRAFT_TAGS_JSON.put(tagId, tagJson.toString());
            return "{\"tag\": \"spraute_engine:" + tagId + "\"}";
        }
        return "{\"item\": \"" + resolveItemId(raw) + "\"}";
    }

    /** Shaped recipe from a 9-slot grid (index 0 = top-left). Empty/blank slots are holes. */
    public static String shapedFromSlots(String recipeId, String[] slots, String result, int count) {
        String[] grid = new String[9];
        for (int i = 0; i < 9; i++) {
            grid[i] = slots != null && i < slots.length && slots[i] != null ? slots[i].trim() : "";
        }
        StringBuilder json = new StringBuilder();
        if (!appendShapedFromGrid(json, recipeId, grid, resolveItemId(result), Math.max(1, count))) {
            return null;
        }
        return json.toString();
    }

    public static String shapeless(String recipeId, List<String> ingredients, String result, int count) {
        if (ingredients == null || ingredients.isEmpty()) return null;
        StringBuilder json = new StringBuilder();
        json.append("{\"type\": \"minecraft:crafting_shapeless\", \"ingredients\": [");
        boolean first = true;
        int i = 0;
        for (String ing : ingredients) {
            if (ing == null || ing.isBlank()) continue;
            if (!first) json.append(",");
            first = false;
            json.append(ingredientToJson(ing.trim(), recipeId, "ing_" + (i++)));
        }
        if (first) return null;
        json.append("], \"result\": {\"item\": \"").append(resolveItemId(result))
                .append("\", \"count\": ").append(Math.max(1, count)).append("}}");
        return json.toString();
    }

    public static String cooking(String type, String recipeId, String ingredient, String result,
                                 float xp, int time) {
        String t = normalizeType(type);
        if (!t.equals("smelting") && !t.equals("blasting") && !t.equals("smoking") && !t.equals("campfire_cooking")) {
            return null;
        }
        StringBuilder json = new StringBuilder();
        json.append("{\"type\": \"minecraft:").append(t).append("\", \"ingredient\": ");
        String ing = ingredient == null || ingredient.isBlank() ? "minecraft:cobblestone" : ingredient.trim();
        json.append(ingredientToJson(ing, recipeId, "ingredient"));
        json.append(", \"result\": \"").append(resolveItemId(result))
                .append("\", \"experience\": ").append(xp)
                .append(", \"cookingtime\": ").append(Math.max(1, time)).append("}");
        return json.toString();
    }

    /** Script body path: parse slot_N from create craft body text. */
    public static boolean appendShapedCraftFromSlots(StringBuilder json, String body,
                                                     String recipeId, String result, int count) {
        String[] grid = new String[9];
        Matcher slotM = Pattern.compile("slot_(\\d+)\\s*=\\s*\"([^\"]*)\"").matcher(body);
        while (slotM.find()) {
            int idx = Integer.parseInt(slotM.group(1));
            if (idx >= 1 && idx <= 9) {
                grid[idx - 1] = slotM.group(2).trim();
            }
        }
        return appendShapedFromGrid(json, recipeId, grid, result, count);
    }

    private static boolean appendShapedFromGrid(StringBuilder json, String recipeId,
                                                String[] grid, String result, int count) {
        Map<String, Character> itemToLetter = new HashMap<>();
        char next = 'A';
        char[] letters = new char[9];
        for (int i = 0; i < 9; i++) {
            String item = grid[i];
            if (item == null || item.isEmpty()) {
                letters[i] = ' ';
                continue;
            }
            if (!itemToLetter.containsKey(item)) {
                if (next > 'Z') return false;
                itemToLetter.put(item, next++);
            }
            letters[i] = itemToLetter.get(item);
        }

        String[] rows = new String[3];
        for (int r = 0; r < 3; r++) {
            rows[r] = "" + letters[r * 3] + letters[r * 3 + 1] + letters[r * 3 + 2];
        }

        int startRow = 0, endRow = 2;
        while (startRow <= endRow && rows[startRow].trim().isEmpty()) startRow++;
        while (endRow >= startRow && rows[endRow].trim().isEmpty()) endRow--;
        if (startRow > endRow) return false;

        int startCol = 0, endCol = 2;
        outer:
        while (startCol <= endCol) {
            for (int r = startRow; r <= endRow; r++) {
                if (rows[r].charAt(startCol) != ' ') break outer;
            }
            startCol++;
        }
        outer2:
        while (endCol >= startCol) {
            for (int r = startRow; r <= endRow; r++) {
                if (rows[r].charAt(endCol) != ' ') break outer2;
            }
            endCol--;
        }

        json.append("{\"type\": \"minecraft:crafting_shaped\", \"pattern\": [");
        for (int r = startRow; r <= endRow; r++) {
            if (r > startRow) json.append(",");
            json.append("\"").append(rows[r].substring(startCol, endCol + 1)).append("\"");
        }
        json.append("], \"key\": {");

        Map<Character, String> letterToItem = new HashMap<>();
        for (Map.Entry<String, Character> e : itemToLetter.entrySet()) {
            letterToItem.put(e.getValue(), e.getKey());
        }
        boolean firstKey = true;
        for (char c = 'A'; c < next; c++) {
            String itemSpec = letterToItem.get(c);
            if (itemSpec == null) continue;
            if (!firstKey) json.append(",");
            firstKey = false;
            json.append("\"").append(c).append("\": ")
                    .append(ingredientToJson("\"" + itemSpec + "\"", recipeId, "slot_" + c));
        }
        json.append("}, \"result\": {\"item\": \"").append(result)
                .append("\", \"count\": ").append(count).append("}}");
        return true;
    }

    private static List<String> parseQuotedStrings(String listContent) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(listContent);
        while (m.find()) out.add(m.group(1));
        return out;
    }
}
