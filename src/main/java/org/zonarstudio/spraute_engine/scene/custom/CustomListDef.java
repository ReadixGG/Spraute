package org.zonarstudio.spraute_engine.scene.custom;

import java.util.ArrayList;
import java.util.List;

/** Repeatable card group (e.g. trade items). */
public final class CustomListDef {
    public String id = "";
    public String label = "";
    public String addLabel = "Добавить";
    public final List<CustomFieldDef> fields = new ArrayList<>();
}
