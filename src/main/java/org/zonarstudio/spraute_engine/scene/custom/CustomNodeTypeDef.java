package org.zonarstudio.spraute_engine.scene.custom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Schema for one custom scene-node type declared in .spr. */
public final class CustomNodeTypeDef {
    public String id = "";
    public String section = "Кастом";
    public String label = "";
    public String icon = "plugin";
    /** Script that declared this schema ({@code declare inspector} attach). */
    public String sourceScript = "";
    /** Empty = data-only. */
    public String runtime = "";
    /**
     * When {@code item}, scene / {@code create <type>} instances register as
     * {@code create item} (id ноды = id предмета). Same boot path as RES_ITEM.
     */
    public String creates = "";
    /** List field used by trade runtime (default {@code items}). */
    public String tradeList = "items";

    public boolean createsItem() {
        return "item".equalsIgnoreCase(creates != null ? creates.trim() : "");
    }

    public final List<CustomFieldDef> fields = new ArrayList<>();
    public final List<CustomListDef> lists = new ArrayList<>();

    public String displayLabel() {
        if (label != null && !label.isBlank()) return label;
        return id;
    }

    /** Collapsing-header title for {@code declare inspector} on a parent node. */
    public String inspectorTitle() {
        if (section != null && !section.isBlank() && !"Кастом".equals(section)) return section;
        if (label != null && !label.isBlank()) return label;
        return id == null || id.isBlank() ? "inspector" : id;
    }

    /** Default customData map for a new instance. */
    public Map<String, Object> defaultData() {
        Map<String, Object> data = new LinkedHashMap<>();
        for (CustomFieldDef f : fields) {
            if (f == null || f.kind == CustomFieldKind.BUTTON) continue;
            data.put(f.id, f.resolvedDefault());
        }
        for (CustomListDef list : lists) {
            if (list == null) continue;
            data.put(list.id, new ArrayList<>());
        }
        return data;
    }

    public CustomListDef list(String listId) {
        if (listId == null) return null;
        for (CustomListDef list : lists) {
            if (listId.equals(list.id)) return list;
        }
        return null;
    }

    public CustomFieldDef field(String fieldId) {
        if (fieldId == null) return null;
        for (CustomFieldDef f : fields) {
            if (fieldId.equals(f.id)) return f;
        }
        return null;
    }
}
