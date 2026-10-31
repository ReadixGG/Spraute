package org.sprautestudio.spraute_engine.editor.assets;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.sprautestudio.spraute_engine.editor.project.Project;
import org.sprautestudio.spraute_engine.editor.tabs.EditorTab;
import org.sprautestudio.spraute_engine.lang.Analyzer;
import org.sprautestudio.spraute_engine.lang.ParamType;
import org.sprautestudio.spraute_engine.runtime.Images;
import org.sprautestudio.spraute_engine.runtime.models.EntityLibrary;
import org.sprautestudio.spraute_engine.runtime.models.ModelLibrary;
import org.sprautestudio.spraute_engine.runtime.models.PartShape;
import org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity;
import org.sprautestudio.spraute_engine.runtime.orb.Orbs;
import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.Widget;
import org.sprautestudio.spraute_engine.ui.widgets.Button;
import org.sprautestudio.spraute_engine.ui.widgets.Dropdown;
import org.sprautestudio.spraute_engine.ui.widgets.TextField;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * An entity type of the project: which model it is (one of the project's model files or a built-in
 * Spraute model), its skin, its idle / walk / run animations, size and speed — with a live
 * preview. Stories spawn it as a character: character x = "spraute:<type>". Saved as it changes.
 */
public class EntityTab extends EditorTab {
    public final Project.EntityRef ref;
    private EntityLibrary.Def def;
    private final ModelView view = new ModelView();
    private final List<Widget> rows = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    /** The tab each row belongs to: 0 — model, 1 — held item, 2 — hitbox. */
    private final List<Integer> rowTabs = new ArrayList<>();
    private static final String[] TABS = {"Модель", "Предмет", "Хитбокс"};
    private final List<Button> tabButtons = new ArrayList<>();
    private int tab, buildTab;
    private final Button character, playWalk, playIdle;
    private static List<String> textureCache;

    /** Opens the "add model files" dialog of the IDE. */
    private final Runnable importModels;

    /** Called by the IDE after model files were added while this tab is active. */
    public void useModel(String name) {
        def.model = name;
        def.texture = "model";
        changed();
    }

    public EntityTab(Project project, Project.EntityRef ref, Consumer<String> insertCode, Runnable importModels) {
        super(project, null);
        this.ref = ref;
        this.importModels = importModels;
        String json = "";
        try {
            json = Files.readString(ref.path(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
        def = EntityLibrary.Def.fromJson(json);
        add(view);
        view.emptyText = "Войдите в мир, чтобы увидеть сущность";
        Dropdown<String> modelBox = new Dropdown<>(this::models, () -> def.model, EntityTab::modelTitle, v -> {
            def.model = v;
            // a project model brings its own picture
            if (!v.startsWith("st:")) def.texture = "model";
            else if (def.texture.equals("model")) def.texture = firstSkin(v);
            changed();
        });
        row("Модель", new Widget() {
            final Dropdown<String> dd = add(modelBox);
            final Button add = add(Button.icon(Icon.PLUS, "Добавить свою модель: файлы Blockbench (.geo.json, .png, .animation.json). "
                    + "Можно и просто перетащить их в окно", importModels));

            @Override
            public void layout() {
                dd.bounds(x, y, w - 22, h);
                add.bounds(x + w - 20, y, 20, h);
                super.layout();
            }
        });
        Dropdown<String> skinBox = new Dropdown<>(this::skins, () -> def.texture, EntityTab::skinTitle, v -> {
            def.texture = v;
            changed();
        });
        row("Скин", new Widget() {
            final Dropdown<String> dd = add(skinBox);
            final Button fromMc = add(Button.icon(Icon.USER, "Из скина Minecraft (64×64): сделать текстуру персонажа 64×64 — "
                    + "с двигающимися глазами, бровями и ртом", () -> org.sprautestudio.spraute_engine.editor.SprauteScreen.pickFaceSkin(name -> {
                def.texture = "image:" + name;
                changed();
            })));

            @Override
            public void layout() {
                dd.bounds(x, y, w - 22, h);
                fromMc.bounds(x + w - 20, y, 20, h);
                super.layout();
            }
        });
        animRow("Стоит", () -> def.idle, v -> def.idle = v);
        animRow("Ходит", () -> def.walk, v -> def.walk = v);
        animRow("Бежит", () -> def.run, v -> def.run = v);
        buildTab = 1;
        row("Предмет в руке", new Dropdown<>(() -> bones(def.itemBone), () -> def.itemBone, v -> v.isEmpty() ? "авто (Right_placeholder, RightHand…)" : v, v -> {
            def.itemBone = v;
            changed();
        }));
        row("Во второй руке", new Dropdown<>(() -> bones(def.offhandBone), () -> def.offhandBone, v -> v.isEmpty() ? "авто (Left_placeholder, LeftHand…)" : v, v -> {
            def.offhandBone = v;
            changed();
        }));
        signedRow("Предмет: поворот X", () -> def.itemTurnX, v -> def.itemTurnX = (float) v);
        signedRow("Предмет: поворот Y", () -> def.itemTurnY, v -> def.itemTurnY = (float) v);
        signedRow("Предмет: поворот Z", () -> def.itemTurnZ, v -> def.itemTurnZ = (float) v);
        signedRow("Предмет: сдвиг X", () -> def.itemX, v -> def.itemX = (float) v);
        signedRow("Предмет: сдвиг Y", () -> def.itemY, v -> def.itemY = (float) v);
        signedRow("Предмет: сдвиг Z", () -> def.itemZ, v -> def.itemZ = (float) v);
        numberRow("Предмет: размер", () -> def.itemScale, v -> def.itemScale = (float) Math.max(0.05, Math.min(10, v)));
        buildTab = 0;
        numberRow("Размер", () -> def.scale, v -> def.scale = (float) Math.max(0.05, Math.min(10, v)));
        numberRow("Скорость", () -> def.speed, v -> def.speed = Math.max(0, Math.min(10, v)));
        signedRow("Сдвиг модели вверх", () -> def.modelY, v -> def.modelY = (float) Math.max(-10, Math.min(10, v)));
        numberRow("Здоровье", () -> def.health, v -> def.health = (float) Math.max(0, Math.min(10000, v)));
        buildTab = 2;
        row("Поведение тела", new Dropdown<>(() -> List.of("", "solid", "fixed", "ghost", "damage", "mortal", "solid damage", "solid mortal", "nodamage", "nopick"),
                () -> def.hitbox, v -> v.isEmpty() ? "как обычно" : v, v -> {
                    def.hitbox = v;
                    changed();
                }));
        hitboxRows();
        character = add(new Button("Персонаж с этой сущностью", () -> {
            String var = Images.slug(ref.name());
            insertCode.accept("character " + var + " = \"spraute:" + ref.name() + "\" name \"" + ref.name().replace("\"", "'") + "\"");
        }).icon(Icon.USER).style(Button.Style.PRIMARY));
        playIdle = add(new Button("Стоит", () -> play(def.idle, "idle")).icon(Icon.PLAY).style(Button.Style.GHOST));
        playWalk = add(new Button("Ходит", () -> play(def.walk, "walk")).icon(Icon.PLAY).style(Button.Style.GHOST));
        for (Button b : List.of(character, playIdle, playWalk)) b.autoWidth();
        for (int i = 0; i < TABS.length; i++) {
            int t = i;
            Button tb = add(new Button(TABS[i], () -> {
                tab = t;
                formScroll = 0;
                layout();
            }).toggle(() -> tab == t).style(Button.Style.GHOST));
            tb.autoWidth();
            tabButtons.add(tb);
        }
        changed();
    }

    // ================================================================== hitboxes

    /** The part being edited (-1 — none). */
    private int selPart = -1;
    /** The main hitbox is picked in the preview: dragging it changes its width and height. */
    private boolean mainSel;
    /** Fields of the selected part: they show its values again when another part is picked. */
    private final List<Runnable> partRefresh = new ArrayList<>();

    private EntityLibrary.Def.Part part() {
        return selPart >= 0 && selPart < def.parts.size() ? def.parts.get(selPart) : null;
    }

    private void pickPart(int i) {
        selPart = i;
        for (Runnable r : partRefresh) r.run();
    }

    /** Main hitbox size, the list of extra hitboxes and the fields of the one picked. */
    /** Fields of the main hitbox: shown again when it is resized by dragging. */
    private final List<Runnable> mainRefresh = new ArrayList<>();

    private void hitboxRows() {
        mainNumber("Основной: ширина", () -> def.width, v -> def.width = (float) Math.max(0, Math.min(20, v)));
        mainNumber("Основной: высота", () -> def.height, v -> def.height = (float) Math.max(0, Math.min(40, v)));
        row("Части хитбокса", new Widget() {
            final Dropdown<Integer> dd = add(new Dropdown<>(() -> {
                List<Integer> out = new ArrayList<>();
                out.add(-1);
                for (int i = 0; i < def.parts.size(); i++) out.add(i);
                return out;
            }, () -> selPart, i -> i < 0 ? (def.parts.isEmpty() ? "нет — добавьте +" : "основной хитбокс")
                    : i < def.parts.size() ? def.parts.get(i).name : "?", EntityTab.this::pickPart));
            final Button plus = add(Button.icon(Icon.PLUS, "Добавить часть хитбокса (голова, хвост, уязвимое место…). "
                    + "Её можно таскать мышью прямо в превью", () -> {
                if (def.parts.size() >= SprauteModelEntity.MAX_PARTS) {
                    UI ui = UI.get();
                    if (ui != null) ui.toastError("Не больше " + SprauteModelEntity.MAX_PARTS + " частей");
                    return;
                }
                EntityLibrary.Def.Part np = new EntityLibrary.Def.Part();
                np.name = def.parts.isEmpty() ? "голова" : "часть" + (def.parts.size() + 1);
                np.y = def.parts.isEmpty() ? 1.5f : 1;
                def.parts.add(np);
                saveOnly();
                pickPart(def.parts.size() - 1);
            }));
            final Button minus = add(Button.icon(Icon.TRASH, "Убрать выбранную часть", () -> {
                if (part() == null) return;
                def.parts.remove(selPart);
                saveOnly();
                pickPart(Math.min(selPart, def.parts.size() - 1));
            }));

            @Override
            public void layout() {
                dd.bounds(x, y, w - 44, h);
                plus.bounds(x + w - 42, y, 20, h);
                minus.bounds(x + w - 20, y, 20, h);
                super.layout();
            }
        });
        partText("  имя", () -> part() == null ? "" : part().name, v -> {
            String n = v.trim().replace(",", "");
            if (part() != null && !n.isEmpty()) part().name = n;
        });
        partNumber("  X (влево)", () -> part() == null ? 0 : part().x, v -> part().x = (float) v, true);
        partNumber("  Y (вверх)", () -> part() == null ? 0 : part().y, v -> part().y = (float) v, true);
        partNumber("  Z (вперёд)", () -> part() == null ? 0 : part().z, v -> part().z = (float) v, true);
        partNumber("  ширина", () -> part() == null ? 0 : part().width, v -> part().width = (float) Math.max(0.05, v), false);
        partNumber("  высота", () -> part() == null ? 0 : part().height, v -> part().height = (float) Math.max(0.05, v), false);
        partNumber("  глубина", () -> part() == null ? 0 : part().depth, v -> part().depth = (float) Math.max(0, v), false);
        partNumber("  поворот X", () -> part() == null ? 0 : part().rx, v -> part().rx = (float) v, true);
        partNumber("  поворот Y", () -> part() == null ? 0 : part().ry, v -> part().ry = (float) v, true);
        partNumber("  поворот Z", () -> part() == null ? 0 : part().rz, v -> part().rz = (float) v, true);
        row("  кость", new Dropdown<>(() -> bones(part() == null ? "" : part().bone), () -> part() == null ? "" : part().bone,
                v -> v.isEmpty() ? "нет — стоит от ног" : v, v -> {
            if (part() == null) return;
            part().bone = v;
            saveOnly();
        }));
        partNumber("  урон ×", () -> part() == null ? 0 : part().damage, v -> part().damage = (float) Math.max(0, v), false);
        row("  включена", new Dropdown<>(() -> List.of(true, false), () -> part() == null || part().enabled,
                v -> v ? "да" : "нет (в сюжете: part … enabled true)", v -> {
            if (part() == null) return;
            part().enabled = v;
            saveOnly();
        }));

        view.boxes = () -> {
            List<ModelView.Box> out = new ArrayList<>();
            if (tab != 2) return out;
            float sc = def.scale;
            float mw = (def.width > 0 ? def.width : 0.6f) * sc, mh = (def.height > 0 ? def.height : 1.8f) * sc;
            out.add(new ModelView.Box(new PartShape(new org.joml.Vector3f(0, mh / 2, 0), new org.joml.Quaternionf(),
                    new org.joml.Vector3f(mw / 2, mh / 2, mw / 2)), 0xAAFFFFFF, "основной " + fmt(mw / sc) + "×" + fmt(mh / sc), mainSel));
            for (int i = 0; i < def.parts.size(); i++) {
                EntityLibrary.Def.Part pt = def.parts.get(i);
                int col = !pt.enabled ? 0x88888888 : pt.damage > 1 ? 0xFFFF6060 : pt.damage < 1 ? 0xFF60A0FF : 0xFF60FF90;
                out.add(new ModelView.Box(PartShape.of(pt, sc, 0, previewPose(pt.bone)), col,
                        pt.name + (pt.damage != 1 ? " ×" + fmt(pt.damage) : "") + (pt.bone.isEmpty() ? "" : " → " + pt.bone), i == selPart));
            }
            return out;
        };
        view.onPickBox = i -> {
            // index 0 is the main hitbox; a click on empty space keeps the picked part
            mainSel = i == 0;
            if (i >= 0) pickPart(i - 1);
        };
        view.onMoveBox = new ModelView.MoveListener() {
            @Override
            public void moved(float dx, float dy, float dz, float sx, float sy) {
                float sc = def.scale > 0 ? def.scale : 1;
                if (mainSel) {
                    // the main hitbox always stands centred on the feet (the game's rule): it can only grow or shrink —
                    // sideways for width, up and down for height
                    float w0 = def.width > 0 ? def.width : 0.6f, h0 = def.height > 0 ? def.height : 1.8f;
                    def.width = snap(Math.max(0.1f, Math.min(20, w0 + 2 * sx / sc)));
                    def.height = snap(Math.max(0.1f, Math.min(40, h0 - sy / sc)));
                    for (Runnable r : mainRefresh) r.run();
                    return;
                }
                EntityLibrary.Def.Part pt = part();
                if (pt == null) return;
                // on a bone the anchor is in the bone's frame: the drag turned into it
                PartShape.BonePose bp = previewPose(pt.bone);
                org.joml.Vector3f d = new org.joml.Vector3f(dx, dy, dz);
                if (bp != null) new org.joml.Quaternionf(bp.rot()).conjugate().transform(d);
                pt.x = snap(pt.x + d.x / sc);
                pt.y = snap(pt.y + d.y / sc);
                pt.z = snap(pt.z + d.z / sc);
                for (Runnable r : partRefresh) r.run();
            }

            @Override
            public void released() {
                if (mainSel || part() != null) saveOnly();
            }
        };
    }

    /** Where a bone of the preview body is (its parts ride on it), or null. */
    private PartShape.BonePose previewPose(String bone) {
        if (bone == null || bone.isEmpty()) return null;
        return view.previewEntity() instanceof SprauteModelEntity me ? me.bonePoses.get(bone) : null;
    }

    /** Positions keep two decimals, so the file stays readable. */
    private static float snap(float v) {
        return Math.round(v * 100) / 100f;
    }

    private void mainNumber(String label, java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set) {
        TextField f = new TextField();
        f.setText(fmt(get.getAsDouble()));
        f.filter = ch -> Character.isDigit(ch) || ch == '.';
        f.onChange = s -> {
            try {
                set.accept(Double.parseDouble(s.trim()));
                saveOnly();
            } catch (NumberFormatException ignored) {
            }
        };
        mainRefresh.add(() -> f.setText(fmt(get.getAsDouble())));
        row(label, f);
    }

    private void partText(String label, java.util.function.Supplier<String> get, Consumer<String> set) {
        TextField f = new TextField();
        f.setText(get.get());
        f.onChange = s -> {
            if (part() == null) return;
            set.accept(s);
            saveOnly();
        };
        partRefresh.add(() -> f.setText(get.get()));
        row(label, f);
    }

    private void partNumber(String label, java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set, boolean signed) {
        TextField f = new TextField();
        f.setText(part() == null ? "" : fmt(get.getAsDouble()));
        f.filter = ch -> Character.isDigit(ch) || ch == '.' || (signed && ch == '-');
        f.onChange = s -> {
            if (part() == null) return;
            try {
                set.accept(Double.parseDouble(s.trim()));
                saveOnly();
            } catch (NumberFormatException ignored) {
            }
        };
        partRefresh.add(() -> f.setText(part() == null ? "" : fmt(get.getAsDouble())));
        row(label, f);
    }

    /** Writes the file and lets the world see it, without rebuilding the preview (hitboxes are drawn from the settings). */
    private void saveOnly() {
        try {
            Files.writeString(ref.path(), def.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            UI ui = UI.get();
            if (ui != null) ui.toastError("Не удалось сохранить: " + e.getMessage());
        }
        ModelLibrary.scan();
    }

    /** "" (auto) and the bones of the model, for where held items sit. */
    private List<String> bones(String cur) {
        List<String> out = new java.util.ArrayList<>(List.of(""));
        try {
            EntityLibrary.Type t = EntityLibrary.find(project.name(), ref.name());
            EntityLibrary.Look l = t != null ? EntityLibrary.look(t.key()) : null;
            if (l != null && l.geometry() != null) {
                if (boneCache == null || boneCacheFor != l.geometry()) {
                    boneCache = new java.util.ArrayList<>(new java.util.TreeSet<>(l.geometry().boneMap.keySet()));
                    boneCacheFor = l.geometry();
                }
                out.addAll(boneCache);
            }
        } catch (Throwable ignored) {
        }
        if (!cur.isEmpty() && !out.contains(cur)) out.add(cur);
        return out;
    }

    private List<String> boneCache;
    private Object boneCacheFor;

    // ================================================================== rows

    private void row(String label, Widget editor) {
        labels.add(label);
        rowTabs.add(buildTab);
        rows.add(add(editor));
    }

    private void numberRow(String label, java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set) {
        TextField f = new TextField();
        f.setText(fmt(get.getAsDouble()));
        f.filter = ch -> Character.isDigit(ch) || ch == '.';
        f.onChange = s -> {
            try {
                set.accept(Double.parseDouble(s.trim()));
                changed();
            } catch (NumberFormatException ignored) {
            }
        };
        row(label, f);
    }

    /** A number that may be below zero (degrees, pixels). */
    private void signedRow(String label, java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set) {
        TextField f = new TextField();
        f.setText(fmt(get.getAsDouble()));
        f.filter = ch -> Character.isDigit(ch) || ch == '.' || ch == '-';
        f.onChange = s -> {
            try {
                set.accept(Double.parseDouble(s.trim()));
                changed();
            } catch (NumberFormatException ignored) {
            }
        };
        row(label, f);
    }

    private void animRow(String label, java.util.function.Supplier<String> get, Consumer<String> set) {
        Widget box = new Widget() {
            final Dropdown<String> dd = add(new Dropdown<>(() -> anims(get.get()), get, a -> a.isEmpty() ? "авто (по имени idle/walk/run)" : animTitle(a), v -> {
                set.accept(v);
                changed();
            }));
            final Button pick = add(Button.icon(Icon.SEARCH, "Анимация Spraute…", () -> AssetPicker.open(ParamType.ANIM, get.get(), v -> {
                set.accept(v);
                changed();
            }, "spraute:" + ref.name())));

            @Override
            public void layout() {
                dd.bounds(x, y, w - 22, h);
                pick.bounds(x + w - 20, y, 20, h);
                super.layout();
            }
        };
        row(label, box);
    }

    private static String fmt(double v) {
        return v == (long) v ? String.valueOf((long) v) : String.valueOf(Math.round(v * 100) / 100.0);
    }

    // ================================================================== choices

    private List<String> models() {
        List<String> out = new ArrayList<>();
        for (Project.ModelRef m : project.models()) out.add(m.name());
        for (String b : EntityLibrary.builtinModels()) out.add("st:" + b);
        if (!out.contains(def.model)) out.add(0, def.model);
        return out;
    }

    private static String modelTitle(String m) {
        if (m.startsWith("st:")) return "Spraute · " + m.substring(m.lastIndexOf('/') + 1);
        return m + "  (файлы проекта)";
    }

    /** Built-in skins: the ones in the model's own folder first (textures/entity/<model>/). */
    private List<String> skins() {
        List<String> out = new ArrayList<>();
        if (!def.model.startsWith("st:")) out.add("model");
        for (Project.ImageRef img : project.images()) out.add("image:" + img.name());
        String folder = def.model.startsWith("st:") ? def.model.substring(def.model.lastIndexOf('/') + 1) : null;
        List<String> all = textures(), own = new ArrayList<>(), rest = new ArrayList<>();
        for (String t : all) {
            if (folder != null && t.contains("/entity/" + folder + "/")) own.add("st:" + t);
            else rest.add("st:" + t);
        }
        out.addAll(own);
        out.addAll(rest);
        if (!out.contains(def.texture)) out.add(0, def.texture);
        return out;
    }

    private static String skinTitle(String s) {
        if (s.equals("model")) return "картинка из файлов модели";
        if (s.startsWith("image:")) return "картинка проекта: " + s.substring(6);
        String p = s.substring(s.indexOf("textures/entity/") >= 0 ? s.indexOf("textures/entity/") + 16 : 0).replace(".png", "");
        return p;
    }

    private static List<String> textures() {
        if (textureCache != null) return textureCache;
        List<String> out = new ArrayList<>();
        try {
            for (ResourceLocation rl : Minecraft.getInstance().getResourceManager()
                    .listResources("textures/entity", r -> r.getPath().endsWith(".png")).keySet()) {
                if (rl.getNamespace().equals(org.sprautestudio.spraute_engine.SprauteEngine.MODID)) out.add(rl.toString());
            }
        } catch (Throwable ignored) {
        }
        java.util.Collections.sort(out);
        textureCache = out;
        return out;
    }

    private static String firstSkin(String model) {
        String folder = model.substring(model.lastIndexOf('/') + 1);
        for (String t : textures()) if (t.contains("/entity/" + folder + "/")) return "st:" + t;
        return "st:" + EntityLibrary.BUILTIN_TEXTURE;
    }

    private List<String> anims(String current) {
        List<String> out = new ArrayList<>();
        out.add("");
        if (!def.model.startsWith("st:")) out.addAll(ModelLibrary.animations(ModelLibrary.find(project.name(), def.model)));
        if (current != null && !current.isEmpty() && !out.contains(current)) out.add(current);
        return out;
    }

    private static String animTitle(String a) {
        int bang = a.indexOf('!');
        String s = bang >= 0 ? a.substring(0, bang) : a;
        return s.substring(s.lastIndexOf('/') + 1);
    }

    // ================================================================== saving and preview

    private void changed() {
        try {
            Files.writeString(ref.path(), def.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            UI ui = UI.get();
            if (ui != null) ui.toastError("Не удалось сохранить: " + e.getMessage());
        }
        ModelLibrary.scan();
        EntityLibrary.Type t = EntityLibrary.find(project.name(), ref.name());
        String key = t != null ? t.key() : "";
        view.setType(Orbs.MODEL.get(), e -> {
            if (e instanceof SprauteModelEntity me) {
                me.setModel(key);
                // something in the hand, to see how items sit
                me.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            }
        });
        play(def.idle, "idle");
    }

    private void play(String anim, String fallback) {
        EntityLibrary.Type t = EntityLibrary.find(project.name(), ref.name());
        if (t == null) return;
        EntityLibrary.Look l = EntityLibrary.look(t.key());
        if (l == null) return;
        java.util.Optional<org.sprautestudio.spraute_engine.core.anim.Animation> a = l.animation(anim);
        if (a.isEmpty()) a = l.animation(fallback);
        org.sprautestudio.spraute_engine.core.anim.ConfiguredAnimation ca = a.map(org.sprautestudio.spraute_engine.core.anim.Animation::configure).orElse(null);
        String err = ca != null ? view.play(ca, fallback) : "У модели нет анимации «" + fallback + "» — выберите её в поле выше";
        if (err != null && !fallback.equals("idle")) {
            UI ui = UI.get();
            if (ui != null) ui.toastError(err);
        }
    }

    @Override
    public String title() {
        return ref.name();
    }

    private static final int FORM_W = 320;
    /** Where the rows start: under the title and the tab bar. */
    private static final int TOP = 62;

    private int tabRows() {
        int n = 0;
        for (int t : rowTabs) if (t == tab) n++;
        return n;
    }
    private int formScroll;

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < x + Math.min(FORM_W, w / 2)) {
            formScroll = Math.max(0, formScroll - (int) Math.round(delta * 26));
            layout();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void layout() {
        int fw = Math.min(FORM_W, w / 2);
        formScroll = Math.max(0, Math.min(formScroll, Math.max(0, tabRows() * 26 + 150 - (h - 100))));
        int tx = x + 10;
        for (Button tb : tabButtons) {
            tb.bounds(tx, y + 34, tb.prefW, 20);
            tx += tb.prefW + 4;
        }
        int ry = y + TOP - formScroll;
        for (int i = 0; i < rows.size(); i++) {
            Widget r = rows.get(i);
            if (rowTabs.get(i) != tab) {
                r.bounds(-10000, 0, fw - 106, 18);
                r.layout();
                continue;
            }
            // rows scrolled out of the form go out of sight
            boolean seen = ry >= y + TOP - 4 && ry + 18 <= y + h - 36;
            r.bounds(seen ? x + 116 : -10000, ry, fw - 126, 18);
            r.layout();
            ry += 26;
        }
        view.bounds(x + fw, y + 30, w - fw, h - 30 - 34);
        int bx = x + 10, by = y + h - 27;
        for (Button b : List.of(character, playIdle, playWalk)) {
            b.bounds(bx, by, b.prefW, 20);
            bx += b.prefW + 6;
        }
        super.layout();
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        int fw = Math.min(FORM_W, w / 2);
        c.fill(x, y, w, h, Theme.CODE_BG);
        c.fill(x, y, fw, h - 34, Theme.PANEL);
        c.fill(x + fw, y + 30, 1, h - 64, Theme.BORDER);
        c.text(ref.name(), x + 10, y + 10, Theme.TEXT);
        c.text("в сюжете: \"spraute:" + ref.name() + "\"", x + 20 + c.width(ref.name()), y + 10, Theme.TEXT_MUTED);
        c.fill(x, y + 29, w, 1, Theme.BORDER);
        c.fill(x, y + 58, fw, 1, Theme.BORDER);
        int ry = y + TOP - formScroll;
        c.pushClip(x, y + 59, fw, h - 93);
        for (int i = 0; i < labels.size(); i++) {
            if (rowTabs.get(i) != tab) continue;
            if (ry >= y + TOP - 4 && ry + 18 <= y + h - 36) c.text(labels.get(i), x + 10, ry + 5, Theme.TEXT_DIM);
            ry += 26;
        }
        if (tab == 0) c.wrapped("Сущность — это тип персонажа. Настройте её тут, а в сюжете создайте персонажа: character x = \"spraute:" + ref.name()
                + "\". Анимации «авто» берутся из файлов модели по именам idle, walk и run.", x + 10, ry + 8, fw - 20, Theme.TEXT_MUTED, 2);
        if (tab == 1) c.wrapped("Как сидит предмет в руке: кость, поворот, сдвиг (в пикселях модели) и размер. В превью у сущности меч.",
                x + 10, ry + 8, fw - 20, Theme.TEXT_MUTED, 2);
        if (tab == 2) c.wrapped("Белая рамка — основной хитбокс: он всегда стоит по центру у ног (так устроена игра), его можно только "
                + "растянуть — тащите его мышью вбок (ширина) и вверх-вниз (высота). 0 в полях — обычный размер 0.6×1.8.\n"
                + "Цветные — части: кликните и тащите куда угодно. Красная — урон больше, синяя — меньше, серая — выключена. "
                + "Часть можно повернуть и посадить на кость модели — тогда она ездит за анимацией (рука, хвост, оружие).\n"
                + "Если ноги модели ниже хитбокса — вкладка «Модель», «Сдвиг модели вверх». В игре F3+B показывает части жёлтым.\n"
                + "Чтобы удар в голову отличался от удара в тело, сделайте основной пониже, а голову поставьте над ним.\n"
                + "В сюжете: on parthit x { event(\"part\") }, lastpart(x), partpos(x, \"голова\"), part x \"голова\" enabled false.",
                x + 10, ry + 8, fw - 20, Theme.TEXT_MUTED, 2);
        c.popClip();
        if (formScroll > 0 || tabRows() * 26 + 150 > h - 100) c.text("колесо — прокрутка", x + fw - 10 - c.width("колесо — прокрутка"), y + 10, Theme.TEXT_MUTED);
        c.fill(x, y + h - 34, w, 34, Theme.PANEL);
        c.fill(x, y + h - 34, w, 1, Theme.BORDER);
    }

    @Override
    public Icon icon() {
        return Icon.USER;
    }

    @Override
    public int iconColor() {
        return Theme.CODE_CHARACTER;
    }

    @Override
    public boolean isDirty() {
        return false;
    }

    @Override
    public void save() {
    }

    @Override
    public String source() {
        return null;
    }

    @Override
    public int scriptId() {
        return -1;
    }

    @Override
    public List<Problem> problems() {
        return List.of();
    }

    @Override
    public Analyzer.Result analysis() {
        return null;
    }

    @Override
    public void reveal(int offset) {
    }

    @Override
    public void focusContent() {
    }
}
