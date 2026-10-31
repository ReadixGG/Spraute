package org.sprautestudio.spraute_engine.runtime.models;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.runtime.Anims;
import org.sprautestudio.spraute_engine.runtime.Images;
import org.sprautestudio.spraute_engine.core.anim.Animation;
import org.sprautestudio.spraute_engine.core.model.SpGeoModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Entity types of Spraute projects: a project's "entities" folder holds one .sent file per type —
 * which model it uses (a project model or the built-in one), its skin, its idle / walk /
 * run animations, size and speed. Stories spawn them as characters: character x = "spraute:<type>".
 */
public final class EntityLibrary {
    private EntityLibrary() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** The key of the default body, which every story has. */
    public static final String BUILTIN = "builtin/humanoid";
    /** The same body with narrow arms. */
    public static final String BUILTIN_SLIM = "builtin/humanoid_slim";
    public static final String BUILTIN_MODEL = SprauteEngine.MODID + ":entity/humanoid";
    public static final String BUILTIN_TEXTURE = SprauteEngine.MODID + ":textures/entity/humanoid.png";

    /** The settings of an entity type, as stored in its file. */
    public static final class Def {
        /** A project model's name, or "st:<geometry id>" for the built-in one. */
        public String model = "st:" + BUILTIN_MODEL;
        /** "model" — the project model's own picture, "image:<project picture>", or "st:<texture id>". */
        public String texture = "st:" + BUILTIN_TEXTURE;
        public String idle = "", walk = "", run = "";
        public float scale = 1;
        public double speed = 1;
        /** Bones the held items sit at ("" — found by name: Right_placeholder, RightHand…). */
        public String itemBone = "", offhandBone = "";
        /** How the held item sits on its bone: turn (degrees), shift (pixels) and size — on top of the usual hold. */
        public float itemTurnX = 0, itemTurnY = 0, itemTurnZ = 0, itemX = 0, itemY = 0, itemZ = 0, itemScale = 1;
        /** Health (20 — like a player); 0 — the game's own. */
        public float health = 0;
        /** Hitbox words given to every character of this type: "solid damage", "mortal"… ("" — as usual). */
        public String hitbox = "";
        /** Main hitbox size in blocks (0 — the model's usual 0.6 × 1.8); multiplied by scale. */
        public float width = 0, height = 0;
        /**
         * Extra hitboxes ("parts": head, tail, weak spot…). Each is hit on its own — by a hand, an arrow,
         * a cast — and tells the story which part was hit; its damage multiplies the blow.
         */
        public List<Part> parts = new ArrayList<>();

        /** One extra hitbox, placed from the feet in blocks: +y up, +z forward (where it looks), +x to its left. */
        /** The model is drawn this many blocks higher (minus — lower), when its feet do not stand where the hitbox does. */
        public float modelY = 0;

        public static final class Part {
            public String name = "часть";
            /** Anchor: the middle of the part's bottom face, from the feet (or from the bone). */
            public float x = 0, y = 1, z = 0;
            /** Width (x), height (y) and depth (z; 0 — as wide). */
            public float width = 0.5f, height = 0.5f, depth = 0;
            /** Turn of the part around its anchor, degrees. */
            public float rx = 0, ry = 0, rz = 0;
            /** A bone of the model the part rides on (it follows the animation), "" — fixed to the body. */
            public String bone = "";
            /** The blow is multiplied by this: 2 — a weak spot, 0.5 — armour, 0 — no harm (still reported). */
            public float damage = 1;
            public boolean enabled = true;
        }

        public String toJson() {
            return GSON.toJson(this);
        }

        public static Def fromJson(String json) {
            Def d = null;
            try {
                d = GSON.fromJson(json, Def.class);
            } catch (Exception ignored) {
            }
            if (d == null) d = new Def();
            if (d.model == null) d.model = "";
            if (d.texture == null) d.texture = "model";
            if (d.idle == null) d.idle = "";
            if (d.walk == null) d.walk = "";
            if (d.run == null) d.run = "";
            if (d.itemBone == null) d.itemBone = "";
            if (d.hitbox == null) d.hitbox = "";
            if (d.offhandBone == null) d.offhandBone = "";
            if (d.scale <= 0) d.scale = 1;
            if (d.parts == null) d.parts = new ArrayList<>();
            d.parts.removeIf(java.util.Objects::isNull);
            for (Part p : d.parts) {
                if (p.name == null || p.name.isBlank()) p.name = "часть";
                if (p.bone == null) p.bone = "";
                p.depth = Math.max(0, p.depth);
                p.width = Math.max(0.05f, p.width);
                p.height = Math.max(0.05f, p.height);
            }
            return d;
        }
    }

    /** A loaded entity type of a project. */
    public record Type(String project, String name, Path file, Def def) {
        public String key() {
            return project + "/" + name;
        }
    }

    private static final Map<String, Type> TYPES = new LinkedHashMap<>();

    /** Reads the entity types of these project folders (called with the model scan). */
    static void scan(List<Path> projects) {
        Map<String, Type> found = new LinkedHashMap<>();
        for (Path pd : projects) {
            Path ed = pd.resolve("entities");
            if (!Files.isDirectory(ed)) continue;
            String project = ModelLibrary.projectNameOf(pd);
            try (Stream<Path> files = Files.list(ed)) {
                for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".sent")).sorted()::iterator) {
                    String name = f.getFileName().toString();
                    name = name.substring(0, name.length() - 5);
                    try {
                        found.put(project + "/" + name, new Type(project, name, f, Def.fromJson(Files.readString(f, StandardCharsets.UTF_8))));
                    } catch (IOException ignored) {
                    }
                }
            } catch (IOException e) {
                SprauteEngine.LOGGER.warn("Spraute: cannot scan entity types", e);
            }
        }
        TYPES.clear();
        TYPES.putAll(found);
    }

    public static Collection<Type> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    public static Type byKey(String key) {
        return key == null ? null : TYPES.get(key);
    }

    public static Type find(String project, String name) {
        if (name == null) return null;
        String n = name.startsWith("spraute:") ? name.substring(8) : name;
        Type t = TYPES.get(n);
        if (t == null && project != null) t = TYPES.get(project + "/" + n);
        if (t != null) return t;
        for (Type o : TYPES.values()) if (o.name().equalsIgnoreCase(n)) return o;
        return null;
    }

    // ================================================================== what an entity looks like

    /** How an entity with a given key is drawn: an entity type key or a plain project model key. */
    public record Look(SpGeoModel geometry, ResourceLocation texture, ModelLibrary.Model model, Def def) {
        public Optional<Animation> animation(String name) {
            if (name == null || name.isBlank()) return Optional.empty();
            if (name.contains(":") || name.contains("/")) {
                Optional<Animation> a = Anims.resolve(name);
                if (a.isPresent()) return a;
            }
            return model != null ? ModelLibrary.animation(model, name) : Anims.humanoid(name);
        }

        public float scale() {
            return def != null ? def.scale : 1;
        }
    }

    private static final ResourceLocation MISSING = new ResourceLocation("minecraft", "textures/misc/unknown_pack.png");

    public static Look look(String key) {
        if (key != null && key.equals(BUILTIN_SLIM)) {
            SpGeoModel geo = ModelLibrary.builtinGeometry("humanoid_slim");
            return geo == null ? null : new Look(geo, ModelLibrary.builtinTexture(), null, null);
        }
        if (key == null || key.isEmpty() || key.equals(BUILTIN)) {
            SpGeoModel geo = ModelLibrary.builtinGeometry("humanoid");
            return geo == null ? null : new Look(geo, ModelLibrary.builtinTexture(), null, null);
        }
        Type t = byKey(key);
        if (t != null) {
            Def d = t.def();
            ModelLibrary.Model m = null;
            SpGeoModel geo;
            if (d.model.startsWith("st:")) {
                geo = ModelLibrary.builtinGeometry(d.model.endsWith("_slim") ? "humanoid_slim" : "humanoid");
            } else {
                m = ModelLibrary.find(t.project(), d.model);
                geo = m != null ? m.geometry() : null;
            }
            ResourceLocation tex;
            if (d.texture.startsWith("st:")) tex = ResourceLocation.tryParse(d.texture.substring(3));
            else if (d.texture.startsWith("image:")) tex = Images.texture(t.project(), d.texture.substring(6));
            else tex = m != null && m.texture() != null ? m.textureId() : null;
            return geo == null ? null : new Look(geo, tex != null ? tex : MISSING, m, d);
        }
        ModelLibrary.Model m = ModelLibrary.byKey(key);
        SpGeoModel geo = m != null ? m.geometry() : null;
        return geo == null ? null : new Look(geo, m.texture() != null ? m.textureId() : MISSING, m, null);
    }

    /** Built-in entity models (geometry ids), for the editor. */
    public static List<String> builtinModels() {
        return List.of(BUILTIN_MODEL, BUILTIN_MODEL + "_slim");
    }
}
