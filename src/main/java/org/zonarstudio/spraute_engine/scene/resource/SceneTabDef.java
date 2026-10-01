package org.zonarstudio.spraute_engine.scene.resource;

import java.util.ArrayList;
import java.util.List;

/** Scene payload mirroring {@code create tab}. */
public final class SceneTabDef {
    public String id = "";
    public String displayName = "";
    public String icon = "minecraft:stone";
    public List<String> items = new ArrayList<>();

    public SceneTabDef copy() {
        SceneTabDef c = new SceneTabDef();
        c.id = id;
        c.displayName = displayName;
        c.icon = icon;
        c.items = new ArrayList<>(items == null ? List.of() : items);
        return c;
    }
}
