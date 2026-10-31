package org.sprautestudio.spraute_engine.editor.fight;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.sprautestudio.spraute_engine.editor.assets.AssetPicker;
import org.sprautestudio.spraute_engine.editor.assets.ModelView;
import org.sprautestudio.spraute_engine.editor.project.Project;
import org.sprautestudio.spraute_engine.editor.tabs.EditorTab;
import org.sprautestudio.spraute_engine.fight.FightDef;
import org.sprautestudio.spraute_engine.lang.Analyzer;
import org.sprautestudio.spraute_engine.lang.ParamType;
import org.sprautestudio.spraute_engine.runtime.Anims;
import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.Widget;
import org.sprautestudio.spraute_engine.ui.widgets.Button;
import org.sprautestudio.spraute_engine.ui.widgets.Checkbox;
import org.sprautestudio.spraute_engine.ui.widgets.Dropdown;
import org.sprautestudio.spraute_engine.ui.widgets.ScrollView;
import org.sprautestudio.spraute_engine.ui.widgets.TextField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Fighting style editor: the parts on the left (the fighter, defence and reactions, each attack),
 * the model in the middle playing any of the style's animations, the settings on the right.
 * "Подобрать анимации" fills empty slots from the animations the game has, by their names.
 */
public class FightTab extends EditorTab {
    private FightDef doc;
    private String saved;
    /** "" — the fighter, "defense", or "attack:N". */
    private String sel = "";

    private final Form form = new Form();
    private final ScrollView inspector;
    private final PartList list = new PartList();
    private final ModelView view = new ModelView();
    private final List<Button> tools = new ArrayList<>();
    private final List<Button> plays = new ArrayList<>();
    private String previewType = "spraute:npc";
    private String status = "";

    public FightTab(Project project, Project.FileRef file, String content) {
        super(project, file);
        doc = FightDef.fromJson(content);
        saved = doc.toJson();
        add(list);
        add(view);
        inspector = add(new ScrollView(form));
        tool("Атака", Icon.PLUS, Button.Style.PRIMARY, "Новая атака", this::addAttack);
        tool("Копия", Icon.COPY, Button.Style.GHOST, "Копия выбранной атаки", this::duplicate);
        tool("Удалить", Icon.TRASH, Button.Style.GHOST, "Удалить выбранную атаку", this::delete);
        tool("Подобрать анимации", Icon.BOLT, Button.Style.GHOST,
                "Заполнить пустые анимации подходящими из игры (по названиям: punch, attack, block, dodge, hurt, death…)", this::autoAnims);
        tool("Шаблон…", Icon.COPY, Button.Style.GHOST, "Взять готовый стиль: кулачный бой, мечник, громила, слабак", this::presetMenu);
        tool("Модель…", Icon.USER, Button.Style.GHOST, "На ком смотреть анимации", () -> AssetPicker.open(ParamType.ENTITY, previewType, v -> {
            previewType = v;
            setModel();
        }));
        playButton("Стойка", () -> doc.idle, true);
        playButton("Атака", () -> {
            FightDef.Attack a = attack();
            return a != null ? a.anim : doc.attacks.get(0).anim;
        }, false);
        playButton("Блок", () -> doc.block.anim, false);
        playButton("Уклон", () -> doc.dodge.anim, false);
        playButton("Ранен", () -> doc.hurt, false);
        playButton("Поражение", () -> doc.defeat, false);
        setModel();
        buildForm();
    }

    private void tool(String text, Icon icon, Button.Style style, String tip, Runnable r) {
        Button b = add(new Button(text, r).icon(icon).style(style));
        b.tooltip(tip);
        b.autoWidth();
        tools.add(b);
    }

    private void playButton(String text, Supplier<String> anim, boolean loop) {
        Button b = add(new Button(text, () -> play(anim.get(), loop)).icon(Icon.PLAY).style(Button.Style.GHOST));
        b.autoWidth();
        plays.add(b);
    }

    /** The project entity type being previewed ("" — a game entity). */
    private String previewKey = "";

    private void setModel() {
        previewKey = "";
        if (previewType.startsWith("spraute:")) {
            var t = org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.find(project.name(), previewType);
            if (t == null) {
                status = "Нет сущности проекта: " + previewType;
                return;
            }
            String key = t.key();
            previewKey = key;
            view.setType(org.sprautestudio.spraute_engine.runtime.orb.Orbs.MODEL.get(), e -> {
                if (e instanceof org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity me) me.setModel(key);
            });
            status = "Сущность проекта: " + t.name() + " — у неё свои анимации (кнопка «…» у поля анимации)";
            return;
        }
        ResourceLocation rl = ResourceLocation.tryParse(previewType);
        EntityType<?> t = rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
        if (t != null) view.setType(t);
        status = t == null ? "Нет такой сущности: " + previewType : "";
    }

    private void play(String anim, boolean loop) {
        if (anim == null || anim.isBlank()) {
            status = "Анимация не задана";
            return;
        }
        if (!previewKey.isEmpty()) {
            var look = org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.look(previewKey);
            var a = look != null ? look.animation(anim) : java.util.Optional.<org.sprautestudio.spraute_engine.core.anim.Animation>empty();
            if (a.isPresent()) {
                String err = view.play(a.get().configure(), anim);
                status = err != null ? anim + ": " + err : "▶ " + anim;
                return;
            }
        }
        String err = view.play(anim, "action", loop, 1);
        status = err != null ? anim + ": " + err : "▶ " + anim;
    }

    // ================================================================== editing

    private FightDef.Attack attack() {
        if (!sel.startsWith("attack:")) return null;
        int i = Integer.parseInt(sel.substring(7));
        return i >= 0 && i < doc.attacks.size() ? doc.attacks.get(i) : null;
    }

    private void select(String s) {
        sel = s;
        buildForm();
        FightDef.Attack a = attack();
        if (a != null && !a.anim.isBlank()) play(a.anim, false);
    }

    private void changed() {
        doc.fix();
    }

    private void addAttack() {
        FightDef.Attack a = new FightDef.Attack();
        a.name = "Удар " + (doc.attacks.size() + 1);
        doc.attacks.add(a);
        select("attack:" + (doc.attacks.size() - 1));
    }

    private void duplicate() {
        FightDef.Attack a = attack();
        if (a == null) return;
        FightDef tmp = new FightDef();
        tmp.attacks.clear();
        tmp.attacks.add(a);
        FightDef.Attack c = FightDef.fromJson(tmp.toJson()).attacks.get(0);
        c.name = a.name + " (копия)";
        doc.attacks.add(doc.attacks.indexOf(a) + 1, c);
        select("attack:" + (doc.attacks.indexOf(c)));
    }

    private void delete() {
        FightDef.Attack a = attack();
        if (a == null || doc.attacks.size() <= 1) return;
        doc.attacks.remove(a);
        select("");
    }

    private void presetMenu() {
        var pm = new org.sprautestudio.spraute_engine.ui.widgets.PopupMenu();
        for (var e : FightDef.PRESET_TITLES.entrySet()) {
            pm.item(e.getValue(), () -> {
                String title = doc.title;
                doc = FightDef.preset(e.getKey());
                doc.title = title;
                select("");
            });
        }
        Button b = tools.get(4);
        pm.show(b.x, b.y + b.h + 2);
    }

    /** Fills empty slots from the game's animations whose names fit. */
    /** The previewed project entity's own animation names (empty — a game entity). */
    private List<String> modelAnims() {
        if (previewKey.isEmpty()) return List.of();
        var look = org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.look(previewKey);
        if (look == null || look.model() == null) return List.of();
        return org.sprautestudio.spraute_engine.runtime.models.ModelLibrary.animations(look.model());
    }

    private static String findName(List<String> names, String... words) {
        for (String w : words) for (String n : names) if (n.toLowerCase().contains(w)) return n;
        return "";
    }

    private void autoAnims() {
        List<String> own = modelAnims();
        if (!own.isEmpty()) {
            // a project entity: its own animations by their names
            String before = doc.toJson();
            if (doc.idle.isBlank()) doc.idle = findName(own, "fight_idle", "combat", "stance", "battle", "idle_fight", "guard");
            if (doc.block.anim.isBlank()) doc.block.anim = findName(own, "block", "defend", "guard", "shield");
            if (doc.dodge.anim.isBlank()) doc.dodge.anim = findName(own, "dodge", "roll", "evade", "jump_back");
            if (doc.hurt.isBlank()) doc.hurt = findName(own, "hurt", "hit_react", "damage", "stagger", "flinch", "hit");
            if (doc.defeat.isBlank()) doc.defeat = findName(own, "death", "die", "dead", "fall", "knock");
            List<String> attacks = new ArrayList<>();
            for (String n : own) {
                String l = n.toLowerCase();
                if (l.contains("attack") || l.contains("punch") || l.contains("slash") || l.contains("strike") || l.contains("kick") || l.contains("bite")
                        || l.contains("swing") || l.contains("stab") || l.contains("атак") || l.contains("удар")) attacks.add(n);
            }
            int ai = 0;
            for (FightDef.Attack a : doc.attacks) if (a.anim.isBlank() && ai < attacks.size()) a.anim = attacks.get(ai++);
            // more attacks than slots: add them
            while (ai < attacks.size()) {
                FightDef.Attack a = new FightDef.Attack();
                a.name = attacks.get(ai);
                a.anim = attacks.get(ai++);
                doc.attacks.add(a);
            }
            doc.fix();
            status = doc.toJson().equals(before) ? "У модели не нашлось подходящих анимаций (по названиям)" : "Анимации модели подобраны — проверьте кнопками под моделью";
            buildForm();
            return;
        }
        List<Anims.Entry> all = Anims.all();
        String before = doc.toJson();
        if (doc.idle.isBlank()) doc.idle = find(all, "fistfight_idle", "fight_idle", "combatstand", "fight_pose", "fightpose");
        if (doc.block.anim.isBlank()) doc.block.anim = find(all, "fistfight_block", "blockattack", "keepblock", "defend", "block");
        if (doc.dodge.anim.isBlank()) doc.dodge.anim = find(all, "dodge_side", "dodge_back", "dodge", "roll");
        if (doc.hurt.isBlank()) doc.hurt = find(all, "knockback_face", "stagger", "hurt");
        if (doc.defeat.isBlank()) doc.defeat = find(all, "fallingtobackfromhit", "gentle_death", "death");
        String[][] hits = {{"fistfight_punch_left", "punch_left"}, {"fistfight_punch_right", "punch_right"}, {"sword_attack_right", "swordattack"},
                {"strongattack"}, {"swinging_punch"}, {"attack"}, {"punch"}};
        int hi = 0;
        for (FightDef.Attack a : doc.attacks) {
            if (!a.anim.isBlank()) continue;
            String f = "";
            while (f.isEmpty() && hi < hits.length) f = find(all, hits[hi++]);
            a.anim = f;
        }
        doc.fix();
        status = doc.toJson().equals(before) ? "Пустых мест не осталось (или ничего подходящего нет)" : "Анимации подобраны — проверьте кнопками под моделью";
        buildForm();
    }

    private static String find(List<Anims.Entry> all, String... words) {
        for (String w : words) for (Anims.Entry e : all) if (e.id().toLowerCase().contains(w)) return shortId(e);
        return "";
    }

    private static String shortId(Anims.Entry e) {
        String c = e.container().toString();
        return c.startsWith("spraute_engine:") ? c.substring("spraute_engine:".length()) : c;
    }

    // ================================================================== inspector

    private static final List<String> MODES = List.of("knockout", "die", "stand");

    private void buildForm() {
        form.clear();
        FightDef.Attack a = attack();
        if (a != null) attackForm(a);
        else if (sel.equals("defense")) defenseForm();
        else fighterForm();
        if (w > 0) layout();
    }

    private String name() {
        return file != null ? file.name() : "стиль";
    }

    private void fighterForm() {
        form.header("Боец");
        form.text("Название", null, () -> doc.title, v -> doc.title = v);
        form.number("Здоровье", "Очки здоровья в бою (не своё здоровье сущности)", () -> doc.health, v -> doc.health = (float) v);
        form.number("Броня", "Доля урона, которая не проходит: 0.25 — четверть", () -> doc.armor, v -> doc.armor = (float) v);
        form.number("Скорость", "1 — шаг, 1.3 — быстрый шаг, 1.6 — бег", () -> doc.speed, v -> doc.speed = (float) v);
        form.number("Бросает бой дальше", "Если цель убежала дальше стольких блоков", () -> doc.giveUp, v -> doc.giveUp = (float) v);
        form.anim("Боевая стойка", "Между атаками, когда стоит перед целью (пусто — без стойки)", () -> doc.idle, v -> doc.idle = v);
        form.check("Стойка и на ходу", "Выключено — пока догоняет, видна обычная ходьба", () -> doc.stanceMoving, v -> doc.stanceMoving = v);
        form.header("Поражение");
        form.anim("Падение", null, () -> doc.defeat, v -> doc.defeat = v);
        form.choice("Потом", null, () -> MODES, () -> doc.defeatMode, v -> switch (v) {
            case "die" -> "исчезает";
            case "stand" -> "просто прекращает бой";
            default -> "лежит (сюжет решает: fight heal)";
        }, v -> doc.defeatMode = v);
        form.number("Убегает при", "Доля здоровья, ниже которой бежит прочь (0 — никогда)", () -> doc.fleeAt, v -> doc.fleeAt = (float) v);
        form.header("Экран");
        form.check("Полоса здоровья", "Как у босса — сверху экрана", () -> doc.bossBar, v -> {
            doc.bossBar = v;
            buildForm();
        });
        if (doc.bossBar) form.text("Подпись полосы", "Пусто — имя персонажа", () -> doc.bossName, v -> doc.bossName = v);
        form.text("Звук удара", "Звук попадания по умолчанию", () -> doc.hitSound, v -> doc.hitSound = v.trim());
        form.header("В сюжете");
        form.note("fight alice style \"" + name() + "\"   ·   fight alice style \"" + name() + "\" target bob");
        form.note("wait defeated alice   ·   fight stop alice   ·   fight heal alice");
        form.note("on fighthit \"alice\" { log event(\"damage\") }   ·   on fightdown \"alice\" { … }");
        form.note("fighthp(alice)   ·   fighting(alice)   ·   defeated(alice)");
    }

    private void defenseForm() {
        form.header("Блок");
        form.anim("Анимация", null, () -> doc.block.anim, v -> doc.block.anim = v);
        form.number("Шанс", "0.3 — блокирует треть ударов", () -> doc.block.chance, v -> doc.block.chance = (float) v);
        form.number("Гасит урона", "1 — весь, 0.7 — 70%", () -> doc.block.power, v -> doc.block.power = (float) v);
        form.header("Уклонение");
        form.anim("Анимация", null, () -> doc.dodge.anim, v -> doc.dodge.anim = v);
        form.number("Шанс", "Удар не проходит вовсе", () -> doc.dodge.chance, v -> doc.dodge.chance = (float) v);
        form.number("Отскок (блоков)", null, () -> doc.dodge.power, v -> doc.dodge.power = (float) v);
        form.header("Ранен");
        form.anim("Анимация", "Вздрагивает от удара", () -> doc.hurt, v -> doc.hurt = v);
        form.number("Сбивает атаку", "Шанс, что удар прервёт его замах и он вздрогнет", () -> doc.staggerChance, v -> doc.staggerChance = (float) v);
    }

    private void attackForm(FightDef.Attack a) {
        form.header("Атака");
        form.text("Название", null, () -> a.name, v -> a.name = v);
        form.anim("Анимация", null, () -> a.anim, v -> a.anim = v);
        String len = "";
        try {
            var an = Anims.resolve(a.anim);
            if (an.isPresent()) len = "Длина анимации: " + Anims.lengthTicks(an.get(), 1) + " тиков (20 = 1 сек)";
        } catch (RuntimeException ignored) {
        }
        if (!len.isEmpty()) form.note(len);
        form.number("Урон", "Сердечко = 2", () -> a.damage, v -> a.damage = (float) v);
        form.number("Дальность", "В блоках", () -> a.range, v -> a.range = (float) v);
        form.integer("Момент удара", "Тик анимации, когда бьёт (0 — на 45% анимации)", () -> a.windup, v -> a.windup = Math.max(0, v));
        form.integer("Длится", "Тиков всего (0 — вся анимация)", () -> a.duration, v -> a.duration = Math.max(0, v));
        form.integer("Отдых после", "Тиков до следующей атаки", () -> a.cooldown, v -> a.cooldown = Math.max(0, v));
        form.number("Отбрасывание", null, () -> a.knockback, v -> a.knockback = (float) v);
        form.number("Рывок вперёд", "Шаг к цели в момент удара (блоков)", () -> a.lunge, v -> a.lunge = (float) v);
        form.integer("Частота", "Как часто выбирается рядом с другими атаками", () -> a.weight, v -> a.weight = Math.max(1, v));
        form.text("Звук", "Пусто — звук стиля", () -> a.sound, v -> a.sound = v.trim());
    }

    private static final int LABEL_W = 132;

    private final class Form extends Widget {
        void header(String text) {
            add(new Widget() {
                {
                    prefH = 24;
                }

                @Override
                protected void paint(Canvas c, int mx, int my, float dt) {
                    c.fill(x + 8, y + 7, w - 16, 1, Theme.BORDER);
                    c.text(text.toUpperCase(), x + 10, y + 13, Theme.ACCENT);
                }
            });
        }

        void note(String text) {
            add(new Widget() {
                {
                    prefH = 14;
                }

                @Override
                protected void paint(Canvas c, int mx, int my, float dt) {
                    c.textEllipsized(text, x + 10, y + 3, w - 16, Theme.TEXT_MUTED);
                }
            }).tooltip(text);
        }

        Row row(String label, String doc) {
            Row r = add(new Row(label));
            if (doc != null) r.tooltip(doc);
            return r;
        }

        void text(String label, String doc, Supplier<String> get, Consumer<String> set) {
            Row r = row(label, doc);
            TextField f = new TextField();
            f.setText(get.get());
            f.onChange = s -> {
                set.accept(s);
                changed();
            };
            r.editor = r.add(f);
        }

        void number(String label, String doc, DoubleSupplier get, DoubleConsumer set) {
            Row r = row(label, doc);
            TextField f = new TextField();
            f.setText(fmt(get.getAsDouble()));
            f.filter = ch -> Character.isDigit(ch) || ch == '-' || ch == '.';
            f.onChange = s -> {
                try {
                    set.accept(Double.parseDouble(s.trim()));
                    changed();
                } catch (NumberFormatException ignored) {
                }
            };
            r.editor = r.add(f);
        }

        void integer(String label, String doc, java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
            number(label, doc, get::getAsInt, v -> set.accept((int) Math.round(v)));
        }

        void check(String label, String doc, BooleanSupplier get, Consumer<Boolean> set) {
            Row r = row(label, doc);
            r.editor = r.add(new Checkbox("", get, v -> {
                set.accept(v);
                changed();
            }));
        }

        void choice(String label, String doc, Supplier<List<String>> options, Supplier<String> get, java.util.function.Function<String, String> labeler,
                    Consumer<String> set) {
            Row r = row(label, doc);
            r.editor = r.add(new Dropdown<>(options, get, labeler, v -> {
                set.accept(v);
                changed();
            }));
        }

        /** An animation: the picker, a play button and a clear button. */
        void anim(String label, String doc, Supplier<String> get, Consumer<String> set) {
            Row r = row(label, doc != null ? doc : "Нажмите, чтобы выбрать анимацию");
            r.editor = r.add(new Widget() {
                final Button pick = add(new Button(() -> get.get().isBlank() ? "нет — выбрать…" : get.get(), () -> {
                    List<String> own = modelAnims();
                    if (own.isEmpty()) {
                        AssetPicker.open(ParamType.ANIM, get.get(), v -> {
                            set.accept(v);
                            changed();
                            play(v, false);
                        });
                        return;
                    }
                    // a project entity: its own animations first
                    var pm = new org.sprautestudio.spraute_engine.ui.widgets.PopupMenu();
                    for (String n : own) pm.item(n, () -> {
                        set.accept(n);
                        changed();
                        play(n, false);
                    }).checked(() -> get.get().equals(n));
                    pm.separator();
                    pm.item("Все анимации игры…", Icon.FILM, null, () -> AssetPicker.open(ParamType.ANIM, get.get(), v -> {
                        set.accept(v);
                        changed();
                        play(v, false);
                    }));
                    pm.show(x, y + h + 2);
                }).style(Button.Style.GHOST));
                final Button go = add(Button.icon(Icon.PLAY, "Проиграть на модели", () -> play(get.get(), false)));
                final Button clear = add(Button.icon(Icon.CLOSE, "Убрать", () -> {
                    set.accept("");
                    changed();
                }));

                @Override
                public void layout() {
                    pick.bounds(x, y, w - 44, h);
                    go.bounds(x + w - 42, y, 20, h);
                    clear.bounds(x + w - 20, y, 20, h);
                    super.layout();
                }
            });
        }

        @Override
        public void layout() {
            int cy = y;
            for (Widget c : children()) {
                c.bounds(x, cy, w, c.prefH);
                c.layout();
                cy += c.prefH;
            }
            prefH = cy - y + 8;
        }
    }

    private static String fmt(double v) {
        double r = Math.round(v * 1000) / 1000.0;
        return r == (long) r ? String.valueOf((long) r) : String.valueOf(r);
    }

    private final class Row extends Widget {
        final String label;
        Widget editor;

        Row(String label) {
            this.label = label;
            prefH = 22;
        }

        @Override
        public void layout() {
            if (editor != null) {
                editor.bounds(x + LABEL_W, y + 2, w - LABEL_W - 10, 18);
                editor.layout();
            }
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.textEllipsized(label, x + 10, y + 7, LABEL_W - 14, Theme.TEXT_DIM);
        }
    }

    // ================================================================== the parts

    private final class PartList extends Widget implements UI.CursorProvider {
        private static final int ROW = 18;

        private List<String> rows() {
            List<String> out = new ArrayList<>(List.of("", "defense"));
            for (int i = 0; i < doc.attacks.size(); i++) out.add("attack:" + i);
            return out;
        }

        /** The row under the mouse; slot 2 is the "АТАКИ" caption. */
        private int rowAt(double my) {
            int i = (int) Math.floor((my - y - 4) / ROW);
            return i < 2 ? i : i == 2 ? -1 : i - 1;
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.fill(x, y, w, h, Theme.PANEL);
            c.fill(x + w - 1, y, 1, h, Theme.BORDER);
            List<String> rows = rows();
            int ry = y + 4;
            for (int i = 0; i < rows.size(); i++) {
                if (i == 2) {
                    c.text("АТАКИ", x + 10, ry + 5, Theme.TEXT_MUTED);
                    ry += ROW;
                }
                String r = rows.get(i);
                boolean on = r.equals(sel), hov = isHovered() && rowAt(my) == i;
                c.round(x + 4, ry, w - 8, ROW - 1, 3, on ? Theme.ACCENT_DIM : hov ? Theme.RAISED : 0);
                if (r.isEmpty()) {
                    c.icon(Icon.USER, x + 8, ry + 5, 0xFFFF6B6B);
                    c.textEllipsized(doc.title + " · " + fmt(doc.health) + " HP", x + 22, ry + 5, w - 30, Theme.TEXT);
                } else if (r.equals("defense")) {
                    c.icon(Icon.INFO, x + 8, ry + 5, 0xFF5DA9F6);
                    c.textEllipsized("Защита и реакции", x + 22, ry + 5, w - 30, Theme.TEXT_DIM);
                } else {
                    FightDef.Attack a = doc.attacks.get(Integer.parseInt(r.substring(7)));
                    c.icon(Icon.BOLT, x + 8, ry + 5, a.anim.isBlank() ? Theme.TEXT_MUTED : 0xFFFFC94A);
                    c.textEllipsized(a.name, x + 22, ry + 5, w - 64, Theme.TEXT_DIM);
                    String dmg = fmt(a.damage);
                    c.text(dmg, x + w - 8 - c.width(dmg), ry + 5, Theme.TEXT_MUTED);
                }
                ry += ROW;
            }
        }

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            List<String> rows = rows();
            int i = rowAt(my);
            if (i >= 0 && i < rows.size()) select(rows.get(i));
            return true;
        }

        @Override
        public int cursor(int mx, int my) {
            return UI.CURSOR_HAND;
        }
    }

    // ================================================================== layout and tab

    private static final int LIST_W = 180, INSPECTOR_W = 340;
    private int toolbarH = 28;

    @Override
    public void layout() {
        int bx = x + 6, by = y + 4;
        for (Button b : tools) {
            if (bx + b.prefW > x + w - 6 && bx > x + 6) {
                bx = x + 6;
                by += 24;
            }
            b.bounds(bx, by, b.prefW, 20);
            bx += b.prefW + 4;
        }
        toolbarH = by - y + 24;
        int lw = Math.min(LIST_W, w / 4), iw = Math.min(INSPECTOR_W, w / 3);
        list.bounds(x, y + toolbarH, lw, h - toolbarH);
        int vx = x + lw, vw = w - lw - iw;
        view.bounds(vx, y + toolbarH, vw, h - toolbarH - 48);
        int px = vx + 6, py = y + h - 44;
        for (Button b : plays) {
            if (px + b.prefW > vx + vw - 6 && px > vx + 6) {
                px = vx + 6;
                py += 22;
            }
            b.bounds(px, py, b.prefW, 18);
            px += b.prefW + 4;
        }
        inspector.bounds(x + w - iw, y + toolbarH, iw, h - toolbarH);
        form.layout();
        super.layout();
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.fill(x, y, w, h, Theme.CODE_BG);
        c.fill(x, y, w, toolbarH, Theme.PANEL);
        c.fill(x, y + toolbarH - 1, w, 1, Theme.BORDER);
        int lw = Math.min(LIST_W, w / 4), iw = Math.min(INSPECTOR_W, w / 3);
        c.fill(x + lw, y + h - 48, w - lw - iw, 48, Theme.PANEL);
        if (!status.isEmpty()) c.textEllipsized(status, x + lw + 8, y + toolbarH + 6, w - lw - iw - 16, Theme.TEXT_MUTED);
    }

    @Override
    public String title() {
        return file != null ? file.name() : "Стиль боя";
    }

    @Override
    public Icon icon() {
        return Icon.BOLT;
    }

    @Override
    public int iconColor() {
        return 0xFFFF6B6B;
    }

    @Override
    public boolean isDirty() {
        return !doc.toJson().equals(saved);
    }

    @Override
    public void save() throws IOException {
        doc.fix();
        String json = doc.toJson();
        project.write(file, json);
        saved = json;
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
