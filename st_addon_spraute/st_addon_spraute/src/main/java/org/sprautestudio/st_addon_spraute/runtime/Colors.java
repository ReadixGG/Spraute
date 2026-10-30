package org.sprautestudio.st_addon_spraute.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/** Colours written in stories: "#FF8800", "FF8800" or a name ("red", "золотой"…). */
public final class Colors {
    private Colors() {
    }

    public static final Map<String, Integer> NAMES = new LinkedHashMap<>();

    static {
        name(0xFFFFFF, "white", "белый");
        name(0xAAAAAA, "gray", "серый");
        name(0x555555, "dark_gray", "тёмно-серый");
        name(0x000000, "black", "чёрный");
        name(0xFF5555, "red", "красный");
        name(0xAA0000, "dark_red", "тёмно-красный");
        name(0xFFAA00, "gold", "золотой");
        name(0xFF8C1A, "orange", "оранжевый");
        name(0xFFFF55, "yellow", "жёлтый");
        name(0x55FF55, "green", "зелёный");
        name(0x00AA00, "dark_green", "тёмно-зелёный");
        name(0x55FFFF, "aqua", "голубой");
        name(0x00AAAA, "dark_aqua", "бирюзовый");
        name(0x5555FF, "blue", "синий");
        name(0x0000AA, "dark_blue", "тёмно-синий");
        name(0xFF55FF, "pink", "розовый");
        name(0xFF55FF, "light_purple", "сиреневый");
        name(0xAA00AA, "purple", "фиолетовый");
    }

    private static void name(int rgb, String... names) {
        for (String n : names) NAMES.put(n, rgb);
    }

    /** The colour as 0xRRGGBB, or null for "", "default", "reset" (the usual colour). */
    public static Integer parse(String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase(java.util.Locale.ROOT).replace('ё', 'е');
        if (t.isEmpty() || t.equals("default") || t.equals("reset") || t.equals("обычный")) return null;
        for (Map.Entry<String, Integer> e : NAMES.entrySet()) if (e.getKey().replace('ё', 'е').equals(t)) return e.getValue();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.startsWith("0x")) t = t.substring(2);
        if (t.length() == 3) t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1) + t.charAt(2) + t.charAt(2);
        if (t.length() == 8) t = t.substring(2);
        if (t.length() != 6) throw new SprauteRuntimeException("Цвет «" + s + "»: нужен #RRGGBB или имя (red, gold, aqua, белый, золотой…)");
        try {
            return Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            throw new SprauteRuntimeException("Цвет «" + s + "»: нужен #RRGGBB или имя (red, gold, aqua, белый, золотой…)");
        }
    }
}
