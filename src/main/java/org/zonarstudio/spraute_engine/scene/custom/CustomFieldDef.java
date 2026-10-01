package org.zonarstudio.spraute_engine.scene.custom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One field inside a custom node type or list entry. */
public final class CustomFieldDef {
    public String id = "";
    public CustomFieldKind kind = CustomFieldKind.TEXT;
    public String label = "";
    public Object defaultValue;
    /** For DROPDOWN: label → value. */
    public final Map<String, String> options = new LinkedHashMap<>();
    /** For BUTTON: which list to append a default entry to. */
    public String appendList = "";

    public Object resolvedDefault() {
        if (kind == CustomFieldKind.CURVE) return ScriptCurve.fromDefault(defaultValue);
        if (defaultValue != null) return defaultValue;
        return switch (kind) {
            case NUMBER -> 0;
            case BOOL -> false;
            case COLOR -> "#FFFFFFFF";
            case ITEM, BLOCK, TEXTURE -> "";
            case DROPDOWN -> options.isEmpty() ? "" : options.values().iterator().next();
            case CURVE -> ScriptCurve.identity();
            case BUTTON -> null;
            default -> "";
        };
    }

    public Map<String, Object> emptyEntryDefaults(List<CustomFieldDef> fields) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (fields == null) return row;
        for (CustomFieldDef f : fields) {
            if (f == null || f.kind == CustomFieldKind.BUTTON) continue;
            row.put(f.id, f.resolvedDefault());
        }
        return row;
    }
}
