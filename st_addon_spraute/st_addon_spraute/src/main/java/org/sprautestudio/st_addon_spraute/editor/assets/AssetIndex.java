package org.sprautestudio.st_addon_spraute.editor.assets;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.sprautestudio.st_addon_spraute.lang.Specs;
import org.sprautestudio.st_addon_spraute.runtime.Anims;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Client-side catalog of game assets shown in the Spraute asset browser. */
public final class AssetIndex {
    private AssetIndex() {}

    public record Model(ResourceLocation id, EntityType<?> type, String title, boolean storytelling) {
        public String idString() {
            return id.toString();
        }
    }

    public record Sound(ResourceLocation id) {}

    /** Shops of the open project (set by the editor). */
    public static java.util.function.Supplier<List<String>> projectShops = List::of;
    /** Quests of the open project (set by the editor). */
    public static java.util.function.Supplier<List<String>> projectQuests = List::of;
    /** Zones of the open project (set by the editor). */
    public static java.util.function.Supplier<List<String>> projectZones = List::of;
    public static java.util.function.Supplier<List<String>> projectPaths = List::of;
    /** Fighting styles of the open project (set by the editor). */
    public static java.util.function.Supplier<List<String>> projectFights = List::of;

    /** The project's fighting styles, then the ready ones. */
    public static List<String> fightChoices() {
        List<String> out = new java.util.ArrayList<>(projectFights.get());
        for (String p : org.sprautestudio.st_addon_spraute.fight.FightDef.PRESET_TITLES.keySet()) if (!out.contains(p)) out.add(p);
        return out;
    }

    /** Effects of the open project (set by the editor). */
    public static java.util.function.Supplier<List<String>> projectEffects = List::of;

    /** Effect names for pickers: the project's first, then the built-in ones. */
    public static List<String> vfxChoices() {
        List<String> out = new java.util.ArrayList<>(projectEffects.get());
        for (String n : org.sprautestudio.st_addon_spraute.fx.FxPresets.names()) if (!out.contains(n)) out.add(n);
        return out;
    }

    /** GUIs of the open project: name -> element ids (set by the editor). */
    public static java.util.function.Supplier<java.util.Map<String, List<String>>> projectGuis = java.util.Map::of;
    public static java.util.function.Supplier<List<String>> projectCutscenes = List::of;

    /** Pictures of the open project (set by the editor). */
    public static java.util.function.Supplier<List<String>> projectImages = List::of;
    /** Names of the open project's own sounds. */
    public static java.util.function.Supplier<List<String>> projectSounds = List::of;

    /** Project pictures followed by Spraute's built-in speech icons. */
    public static List<String> imageChoices() {
        List<String> list = new ArrayList<>(projectImages.get());
        for (String s : Specs.SPEECH_ICONS) if (!list.contains(s)) list.add(s);
        return list;
    }

    private static List<Model> models;
    private static List<Sound> sounds;

    public static List<Model> models() {
        if (models != null) return models;
        List<Model> list = new ArrayList<>();
        for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(t);
            // projectiles, items and other non-creatures make poor characters
            if (t.getCategory() == MobCategory.MISC && !id.getNamespace().equals(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID) && !id.getPath().equals("player")
                    && !id.getPath().equals("villager") && !id.getPath().equals("iron_golem") && !id.getPath().equals("armor_stand")) continue;
            if (id.getPath().equals("player")) continue;
            String title;
            try {
                title = t.getDescription().getString();
            } catch (RuntimeException e) {
                title = id.getPath();
            }
            list.add(new Model(id, t, title, id.getNamespace().equals(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID)));
        }
        list.sort((a, b) -> {
            boolean ka = Specs.ST_ENTITIES.contains(a.idString()), kb = Specs.ST_ENTITIES.contains(b.idString());
            if (ka != kb) return ka ? -1 : 1;
            if (a.storytelling != b.storytelling) return a.storytelling ? -1 : 1;
            return a.idString().compareTo(b.idString());
        });
        models = Collections.unmodifiableList(list);
        return models;
    }

    public static Model model(String id) {
        for (Model m : models()) if (m.idString().equals(id)) return m;
        return null;
    }

    public static List<Sound> sounds() {
        if (sounds != null) return sounds;
        List<Sound> list = new ArrayList<>();
        for (ResourceLocation rl : BuiltInRegistries.SOUND_EVENT.keySet()) list.add(new Sound(rl));
        list.sort((a, b) -> {
            boolean sa = a.id.getNamespace().equals(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID), sb = b.id.getNamespace().equals(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID);
            if (sa != sb) return sa ? -1 : 1;
            return a.id.toString().compareTo(b.id.toString());
        });
        sounds = Collections.unmodifiableList(list);
        return sounds;
    }

    public static List<Anims.Entry> animations() {
        return Anims.all();
    }

    /** Case-insensitive match of every space-separated word of the query. */
    public static boolean matches(String query, String... fields) {
        if (query == null || query.isBlank()) return true;
        StringBuilder sb = new StringBuilder();
        for (String f : fields) if (f != null) sb.append(f.toLowerCase(Locale.ROOT)).append(' ');
        String hay = sb.toString();
        for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) if (!hay.contains(word)) return false;
        return true;
    }

    /** A good default model for previewing animations: the default body. */
    public static Model defaultPreviewModel() {
        for (String id : List.of(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID + ":model")) {
            Model m = model(id);
            if (m != null) return m;
        }
        return models().isEmpty() ? null : models().get(0);
    }
}
