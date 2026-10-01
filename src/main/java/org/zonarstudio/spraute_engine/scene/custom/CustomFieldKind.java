package org.zonarstudio.spraute_engine.scene.custom;

/** Inspector widget kinds for custom node fields. */
public enum CustomFieldKind {
    TEXT,
    NUMBER,
    BOOL,
    COLOR,
    ITEM,
    BLOCK,
    TEXTURE,
    DROPDOWN,
    BUTTON,
    CURVE;

    public static CustomFieldKind parse(String raw) {
        if (raw == null || raw.isBlank()) return TEXT;
        String k = raw.trim().toLowerCase();
        return switch (k) {
            case "text", "string" -> TEXT;
            case "number", "num", "int", "float" -> NUMBER;
            case "bool", "boolean", "checkbox" -> BOOL;
            case "color", "colour" -> COLOR;
            case "item" -> ITEM;
            case "block" -> BLOCK;
            case "texture", "tex", "image" -> TEXTURE;
            case "dropdown", "select", "enum" -> DROPDOWN;
            case "button" -> BUTTON;
            case "curve", "animationcurve", "animcurve", "graph" -> CURVE;
            default -> TEXT;
        };
    }
}
