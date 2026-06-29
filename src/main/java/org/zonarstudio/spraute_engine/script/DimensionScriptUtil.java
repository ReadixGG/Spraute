package org.zonarstudio.spraute_engine.script;

import org.zonarstudio.spraute_engine.registry.CustomWorldRegistry;

/** Normalizes dimension ids from scripts ({@code overworld}, {@code lobby}, {@code minecraft:the_nether}). */
public final class DimensionScriptUtil {

    private DimensionScriptUtil() {}

    public static String normalizeDimensionId(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String s = raw.trim();
        if (s.contains(":")) return s;
        return switch (s.toLowerCase()) {
            case "overworld" -> "minecraft:overworld";
            case "nether", "the_nether" -> "minecraft:the_nether";
            case "end", "the_end" -> "minecraft:the_end";
            default -> CustomWorldRegistry.WORLDS.containsKey(s)
                    ? CustomWorldRegistry.dimensionId(s)
                    : "minecraft:" + s;
        };
    }

    public static boolean matchesDimension(String expected, String actual) {
        if (expected == null || expected.isBlank()) return true;
        if (actual == null || actual.isBlank()) return false;
        String e = normalizeDimensionId(expected);
        String a = actual.contains(":") ? actual : normalizeDimensionId(actual);
        if (e.equals(a)) return true;
        if (!expected.contains(":") && a.endsWith(":" + expected.trim())) return true;
        return normalizeDimensionId(expected).equals(normalizeDimensionId(actual));
    }
}
