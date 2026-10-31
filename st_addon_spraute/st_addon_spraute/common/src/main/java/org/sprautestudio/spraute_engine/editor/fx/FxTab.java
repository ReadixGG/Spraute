package org.sprautestudio.spraute_engine.editor.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.sprautestudio.spraute_engine.editor.project.Project;
import org.sprautestudio.spraute_engine.editor.tabs.EditorTab;
import org.sprautestudio.spraute_engine.fx.FxDef;
import org.sprautestudio.spraute_engine.fx.FxPresets;
import org.sprautestudio.spraute_engine.fx.client.FxEngine;
import org.sprautestudio.spraute_engine.fx.client.FxTextures;
import org.sprautestudio.spraute_engine.lang.Analyzer;
import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.Widget;
import org.sprautestudio.spraute_engine.ui.widgets.Button;
import org.sprautestudio.spraute_engine.ui.widgets.Checkbox;
import org.sprautestudio.spraute_engine.ui.widgets.Dialog;
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
 * Effect editor: the layers on the left, the selected layer's settings on the right, and the effect
 * playing in the world in front of the player — again after every change when "Повтор" is on.
 */
public class FxTab extends EditorTab {
    private static final String PREVIEW = "__editor_preview";
    private FxDef doc;
    private String saved;
    /** -1 — the effect's own settings. */
    private int sel = -1;
    private boolean loop = true;
    private float previewScale = 1, distance = 6;
    private long lastPlay;

    private final Form form = new Form();
    private final ScrollView inspector;
    private final Layers layers = new Layers();
    private final Viewport view = new Viewport();
    /** Also play it in the world in front of the player after every change. */
    private boolean inWorld;
    private final List<Widget> tools = new ArrayList<>();

    public FxTab(Project project, Project.FileRef file, String content) {
        super(project, file);
        doc = FxDef.fromJson(content);
        saved = doc.toJson();
        add(layers);
        add(view);
        inspector = add(new ScrollView(form));
        tool(new Button("Проиграть", this::play).icon(Icon.PLAY).style(Button.Style.SUCCESS), "Проиграть заново (пробел)");
        tool(new Button("Стоп", this::stopAll).icon(Icon.STOP).style(Button.Style.GHOST), "Остановить");
        tool(new Checkbox("Повтор", () -> loop, v -> loop = v), "Проигрывать снова, когда закончится");
        tool(new Checkbox("В мире", () -> inWorld, v -> {
            inWorld = v;
            if (v) playInWorld();
            else FxEngine.stop(PREVIEW, true);
        }), "Ещё и в мире перед игроком (видно, если окно не закрывает мир)");
        tools.get(tools.size() - 1).prefW = 72;
        tool(new Dropdown<>(FxPresets::names, () -> null, n -> n == null ? "Шаблон…" : FxPresets.ALL.get(n).title(), this::fromPreset),
                "Заменить эффект готовым: ядерный взрыв, луч в небо, орбы…");
        tools.get(tools.size() - 1).prefW = 150;
        tool(new Button("Слой", this::addLayer).icon(Icon.PLUS).style(Button.Style.PRIMARY), "Новый слой частиц");
        tool(new Button("Копия", this::duplicate).icon(Icon.COPY).style(Button.Style.GHOST), "Копия выбранного слоя");
        tool(new Button("Удалить", this::deleteLayer).icon(Icon.TRASH).style(Button.Style.GHOST), "Удалить выбранный слой");
        buildForm();
    }

    private void tool(Widget w, String tip) {
        add(w);
        w.tooltip(tip);
        if (w instanceof Button b) b.autoWidth();
        if (w instanceof Checkbox) w.prefW = 70;
        tools.add(w);
    }

    // ================================================================== actions

    private FxDef.Layer selected() {
        return sel >= 0 && sel < doc.layers.size() ? doc.layers.get(sel) : null;
    }

    private void select(int i) {
        sel = i;
        buildForm();
    }

    /** Every change replays the effect, so it is seen at once. */
    private void changed() {
        play();
    }

    private void play() {
        FxEngine.previewPlay(project.name(), doc.copy(), previewScale);
        lastPlay = System.currentTimeMillis();
        if (inWorld) playInWorld();
    }

    private void stopAll() {
        FxEngine.previewStop();
        FxEngine.stop(PREVIEW, true);
        loop = false;
    }

    private void playInWorld() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            UI ui = UI.get();
            if (ui != null) ui.toastInfo("Войдите в мир, чтобы увидеть эффект в мире");
            return;
        }
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        Vec3 at = p.position().add(look.normalize().scale(distance * Math.max(1, previewScale)));
        FxEngine.play(PREVIEW, project.name(), doc.copy(), at.x, at.y, at.z, -1, previewScale);
    }

    private void fromPreset(String name) {
        if (name == null) return;
        Dialog.confirm("Шаблон", "Заменить эффект шаблоном «" + FxPresets.ALL.get(name).title() + "»?", "Заменить", false, () -> {
            doc = FxPresets.get(name);
            sel = -1;
            buildForm();
            play();
        });
    }

    private void addLayer() {
        FxDef.Layer l = FxDef.layer("Слой " + (doc.layers.size() + 1));
        doc.layers.add(l);
        select(doc.layers.size() - 1);
        changed();
    }

    private void duplicate() {
        FxDef.Layer l = selected();
        if (l == null) return;
        FxDef tmp = new FxDef();
        tmp.layers.add(l);
        FxDef.Layer c = FxDef.fromJson(tmp.toJson()).layers.get(0);
        c.name = l.name + " (копия)";
        doc.layers.add(sel + 1, c);
        select(sel + 1);
        changed();
    }

    private void deleteLayer() {
        if (selected() == null) return;
        doc.layers.remove(sel);
        select(Math.min(sel, doc.layers.size() - 1));
        changed();
    }

    private void move(int d) {
        int j = sel + d;
        if (selected() == null || j < 0 || j >= doc.layers.size()) return;
        FxDef.Layer l = doc.layers.remove(sel);
        doc.layers.add(j, l);
        select(j);
    }

    @Override
    public void tick() {
        super.tick();
        // keep it playing while editing
        if (loop && !FxEngine.previewRunning() && System.currentTimeMillis() - lastPlay > 500) play();
    }

    // ================================================================== inspector

    private static final List<String> TYPES = List.of("particles", "flash", "shake");
    private static final List<String> SHAPES = List.of("point", "sphere", "shell", "disc", "ring", "cone", "box", "column", "line");
    private static final List<String> DIRS = List.of("out", "up", "down", "random", "tangent", "cone", "none");
    private static final List<String> BLENDS = List.of("add", "alpha");
    private static final List<String> RENDERS = List.of("billboard", "stretch", "flat", "beam");

    private static String title(String v) {
        return switch (v) {
            case "particles" -> "частицы";
            case "flash" -> "вспышка экрана";
            case "shake" -> "тряска камеры";
            case "point" -> "точка";
            case "sphere" -> "шар (внутри)";
            case "shell" -> "сфера (поверхность)";
            case "disc" -> "круг на земле";
            case "ring" -> "кольцо";
            case "cone" -> "конус";
            case "box" -> "коробка";
            case "column" -> "столб";
            case "line" -> "линия вверх";
            case "out" -> "от центра";
            case "up" -> "вверх";
            case "down" -> "вниз";
            case "random" -> "в стороны";
            case "tangent" -> "по кругу";
            case "none" -> "стоят на месте";
            case "add" -> "свечение (добавление)";
            case "alpha" -> "непрозрачные (дым)";
            case "billboard" -> "к камере";
            case "stretch" -> "вытянуты по движению";
            case "flat" -> "лежат (кольца, круги)";
            case "beam" -> "луч";
            case "glow" -> "свечение";
            case "spark" -> "искра";
            case "smoke" -> "дым";
            case "star" -> "звезда";
            case "flare" -> "блик";
            case "circle" -> "круг";
            case "square" -> "квадрат";
            default -> v.startsWith("image:") ? "картинка: " + v.substring(6) : v;
        };
    }

    private static String texTitle(String v) {
        return v.equals("ring") ? "кольцо" : v.equals("beam") ? "луч" : title(v);
    }

    private List<String> textures() {
        List<String> out = new ArrayList<>(FxTextures.BUILT_IN);
        for (Project.ImageRef img : project.images()) out.add("image:" + img.name());
        return out;
    }

    private void buildForm() {
        form.clear();
        FxDef.Layer l = selected();
        if (l == null) {
            form.header("Эффект");
            form.number("Длительность, с", "Сколько эффект выпускает частицы. 0 — пока его не остановят (fx stop)", () -> doc.duration,
                    v -> doc.duration = (float) Math.max(0, v));
            form.text("Звук", "Id звука при старте: minecraft:entity.generic.explode", () -> doc.sound, v -> doc.sound = v.trim());
            form.number("Громкость", null, () -> doc.soundVolume, v -> doc.soundVolume = (float) Math.max(0, v));
            form.number("Высота звука", null, () -> doc.soundPitch, v -> doc.soundPitch = (float) Math.max(0.1, Math.min(2, v)));
            form.header("Просмотр");
            form.number("Масштаб", "Как fx play ... scale N", () -> previewScale, v -> previewScale = (float) Math.max(0.05, v));
            form.number("Расстояние", "Как далеко перед игроком показывать", () -> distance, v -> distance = (float) Math.max(0, v));
            form.note("В сюжете: fx play \"" + (file != null ? file.name() : "имя") + "\" at player");
            form.note("Выберите слой слева, чтобы настроить его");
            relayout();
            return;
        }
        form.header("Слой");
        form.text("Имя", null, () -> l.name, v -> l.name = v);
        form.toggle("Включён", null, () -> l.enabled, v -> l.enabled = v);
        form.choice("Тип", null, TYPES, () -> l.type, v -> {
            l.type = v;
            buildForm();
        });
        form.header("Время");
        form.number("Задержка, с", "Через сколько секунд после начала слой включается", () -> l.delay, v -> l.delay = (float) Math.max(0, v));
        form.number("Длится, с", "0 — до конца эффекта", () -> l.time, v -> l.time = (float) Math.max(0, v));
        if (l.type.equals("flash") || l.type.equals("shake")) {
            form.number("Сила", "0..1", () -> l.strength, v -> l.strength = (float) Math.max(0, Math.min(1, v)));
            if (l.type.equals("flash")) form.colors("Цвет", "Цвет вспышки — нажмите, чтобы выбрать", () -> new String[]{l.flashColor},
                    v -> l.flashColor = v[0], false);
            form.number("Дальность", "Чувствуется в этом радиусе (блоков, умножается на масштаб). 0 — везде", () -> l.range,
                    v -> l.range = (float) Math.max(0, v));
            relayout();
            return;
        }
        form.header("Выпуск");
        form.integer("Залп", "Частиц сразу при включении слоя", () -> l.burst, v -> l.burst = Math.max(0, v));
        form.number("Залп каждые, с", "Повторять залп (0 — один раз)", () -> l.burstEvery, v -> l.burstEvery = (float) Math.max(0, v));
        form.number("Поток, в секунду", "Частиц в секунду, пока слой длится", () -> l.rate, v -> l.rate = (float) Math.max(0, v));
        form.integer("Не больше", "Сколько частиц слоя живёт одновременно", () -> l.max, v -> l.max = Math.max(1, Math.min(20000, v)));
        form.header("Форма");
        form.choice("Откуда", null, SHAPES, () -> l.shape, v -> {
            l.shape = v;
            buildForm();
        });
        if (!l.shape.equals("point") && !l.shape.equals("line") && !l.shape.equals("cone")) {
            form.number("Радиус", null, () -> l.radius, v -> l.radius = (float) Math.max(0, v));
            if (!l.shape.equals("ring") && !l.shape.equals("box")) form.number("Внутр. радиус", "Пустота в центре", () -> l.radiusInner,
                    v -> l.radiusInner = (float) Math.max(0, v));
        }
        if (l.shape.equals("ring")) form.toggle("Ровно по кругу", "Залп встаёт через равные промежутки (орбы, руны)", () -> l.even, v -> l.even = v);
        if (l.shape.equals("column") || l.shape.equals("box") || l.shape.equals("line")) form.number("Высота", null, () -> l.height, v -> l.height = (float) v);
        form.number("Поворот X, °", "Наклон слоя вперёд-назад: 90 — кольцо встаёт вертикально", () -> l.rotX, v -> l.rotX = (float) v);
        form.number("Поворот Y, °", "Поворот слоя вокруг вертикали", () -> l.rotY, v -> l.rotY = (float) v);
        form.number("Поворот Z, °", "Наклон слоя вбок: 90 — кольцо встаёт боком", () -> l.rotZ, v -> l.rotZ = (float) v);
        form.number("Сдвиг X", null, () -> l.offsetX, v -> l.offsetX = (float) v);
        form.number("Сдвиг Y", "Например, 30 — шляпка гриба над центром", () -> l.offsetY, v -> l.offsetY = (float) v);
        form.number("Сдвиг Z", null, () -> l.offsetZ, v -> l.offsetZ = (float) v);
        form.header("Движение");
        form.choice("Куда", null, DIRS, () -> l.direction, v -> {
            l.direction = v;
            buildForm();
        });
        if (l.direction.equals("cone") || l.shape.equals("cone")) form.number("Угол конуса", "Градусы от вертикали", () -> l.angle, v -> l.angle = (float) v);
        form.number("Скорость", "Блоков в секунду", () -> l.speed, v -> l.speed = (float) v);
        form.number("Разброс скорости", "0..1", () -> l.speedRandom, v -> l.speedRandom = (float) Math.max(0, v));
        form.number("Гравитация", "Вниз (минус — частицы всплывают)", () -> l.gravity, v -> l.gravity = (float) v);
        form.number("Торможение", "Сколько скорости теряется в секунду", () -> l.drag, v -> l.drag = (float) Math.max(0, v));
        form.number("Вращение вокруг, °/с", "Частицы кружат вокруг центра (орбы, вихри)", () -> l.orbit, v -> l.orbit = (float) v);
        form.number("Турбулентность", "Случайное колыхание (дым, огонь)", () -> l.turbulence, v -> l.turbulence = (float) v);
        form.number("Притяжение", "К центру (минус — от центра): воронки, порталы", () -> l.attract, v -> l.attract = (float) v);
        form.toggle("Движутся с эффектом", "С follow частицы едут вместе с целью; выключено — остаются, где родились (шлейф)", () -> l.local,
                v -> l.local = v);
        form.header("Вид");
        form.choice("Текстура", null, this::textures, () -> l.texture, FxTab::texTitle, v -> l.texture = v);
        form.choice("Смешивание", null, () -> BLENDS, () -> l.blend, FxTab::title, v -> l.blend = v);
        form.choice("Отрисовка", null, () -> RENDERS, () -> l.render, FxTab::title, v -> {
            l.render = v;
            buildForm();
        });
        if (l.render.equals("stretch")) form.number("Вытягивание", "Длина на единицу скорости", () -> l.stretch, v -> l.stretch = (float) v);
        if (l.render.equals("beam")) form.number("Длина луча", "Блоков", () -> l.beamLength, v -> l.beamLength = (float) Math.max(0, v));
        form.number("Живёт, с", null, () -> l.life, v -> l.life = (float) Math.max(0.02, v));
        form.number("Разброс жизни", "0..1", () -> l.lifeRandom, v -> l.lifeRandom = (float) Math.max(0, v));
        form.curve("Размер", "Размер за жизнь: значения через запятую (от рождения к смерти), например 0.5, 2, 0", () -> l.size,
                v -> l.size = v);
        form.number("Разброс размера", "0..1", () -> l.sizeRandom, v -> l.sizeRandom = (float) Math.max(0, v));
        form.curve("Прозрачность", "0..1 за жизнь, например 0, 1, 1, 0 — плавно появляется и исчезает", () -> l.alpha, v -> l.alpha = v);
        form.colors("Цвета", "Цвет за жизнь частицы, слева направо. Клик — выбрать цвет, ＋ — добавить, правый клик — убрать",
                () -> l.color, v -> l.color = v, true);
        form.toggle("Случайный цвет", "Каждая частица берёт один цвет из списка (салют)", () -> l.colorRandom, v -> l.colorRandom = v);
        form.number("Яркость", "Больше 1 — сильнее светится", () -> l.intensity, v -> l.intensity = (float) Math.max(0, v));
        form.number("Кручение, °/с", null, () -> l.spin, v -> l.spin = (float) v);
        form.number("Разброс кручения", null, () -> l.spinRandom, v -> l.spinRandom = (float) Math.max(0, v));
        relayout();
    }

    private void relayout() {
        if (w > 0) layout();
    }

    private static final int LABEL_W = 118;

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

        void toggle(String label, String doc, BooleanSupplier get, Consumer<Boolean> set) {
            Row r = row(label, doc);
            r.editor = r.add(new Checkbox("", get, v -> {
                set.accept(v);
                changed();
            }));
        }

        private TextField field(Supplier<String> get, java.util.function.Predicate<String> apply) {
            TextField f = new TextField() {
                @Override
                public void tick() {
                    super.tick();
                    if (!isFocused()) {
                        String s = get.get();
                        if (!s.equals(text())) setText(s);
                    }
                }
            };
            f.setText(get.get());
            f.onChange = s -> {
                if (apply.test(s)) changed();
            };
            return f;
        }

        void number(String label, String doc, DoubleSupplier get, DoubleConsumer set) {
            Row r = row(label, doc);
            TextField f = field(() -> fmt(get.getAsDouble()), s -> {
                try {
                    set.accept(Double.parseDouble(s.trim()));
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            });
            f.filter = ch -> Character.isDigit(ch) || ch == '-' || ch == '.';
            r.editor = r.add(f);
        }

        void integer(String label, String doc, java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
            number(label, doc, get::getAsInt, v -> set.accept((int) Math.round(v)));
        }

        void curve(String label, String doc, Supplier<float[]> get, Consumer<float[]> set) {
            Row r = row(label, doc);
            r.editor = r.add(field(() -> FxDef.curveText(get.get()), s -> {
                float[] k = FxDef.parseCurve(s, null);
                if (k == null) return false;
                set.accept(k);
                return true;
            }));
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

        /** Colour swatches: a click opens the picker; with {@code many}: + adds a colour, right click removes one. */
        void colors(String label, String doc, Supplier<String[]> get, Consumer<String[]> set, boolean many) {
            Row r = row(label, doc);
            r.prefH = 24;
            r.editor = r.add(new Swatches(get, v -> {
                set.accept(v);
                changed();
            }, many));
        }

        void choice(String label, String doc, List<String> options, Supplier<String> get, Consumer<String> set) {
            choice(label, doc, () -> options, get, FxTab::title, set);
        }

        void choice(String label, String doc, Supplier<List<String>> options, Supplier<String> get, java.util.function.Function<String, String> labeler,
                    Consumer<String> set) {
            Row r = row(label, doc);
            r.editor = r.add(new Dropdown<>(options, get, labeler, v -> {
                set.accept(v);
                changed();
            }));
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

    private static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    private final class Swatches extends Widget implements UI.CursorProvider {
        private final Supplier<String[]> get;
        private final Consumer<String[]> set;
        private final boolean many;
        private static final int S = 18, GAP = 4;

        Swatches(Supplier<String[]> get, Consumer<String[]> set, boolean many) {
            this.get = get;
            this.set = set;
            this.many = many;
        }

        private int count() {
            return get.get().length + (many ? 1 : 0);
        }

        private int at(double mx) {
            int i = (int) ((mx - x) / (S + GAP));
            return mx >= x && i < count() && (mx - x) % (S + GAP) < S ? i : -1;
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            String[] cs = get.get();
            // the gradient itself under the swatches
            int gw = Math.max(0, Math.min(w, count() * (S + GAP) - GAP));
            for (int i = 0; i < gw; i++) {
                int[] ints = new int[cs.length];
                for (int k = 0; k < cs.length; k++) ints[k] = FxDef.parseColor(cs[k]);
                c.fill(x + i, y + S + 1, 1, 2, 0xFF000000 | FxDef.gradient(ints, gw <= 1 ? 0 : i / (float) (gw - 1)));
            }
            for (int i = 0; i < cs.length; i++) {
                int sx = x + i * (S + GAP);
                boolean hov = isHovered() && at(mx) == i;
                c.round(sx - 1, y - 1, S + 2, S + 2, 3, hov ? Theme.ACCENT : Theme.BORDER_STRONG);
                c.round(sx, y, S, S, 2, 0xFF000000 | FxDef.parseColor(cs[i]));
            }
            if (many) {
                int sx = x + cs.length * (S + GAP);
                boolean hov = isHovered() && at(mx) == cs.length;
                c.round(sx, y, S, S, 3, hov ? Theme.RAISED : Theme.INPUT);
                c.icon(Icon.PLUS, sx + 5, y + 5, Theme.TEXT_DIM);
            }
        }

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            String[] cs = get.get();
            int i = at(mx);
            if (i < 0) return true;
            if (many && i == cs.length) {
                String[] n = java.util.Arrays.copyOf(cs, cs.length + 1);
                n[cs.length] = cs.length > 0 ? cs[cs.length - 1] : "#FFFFFF";
                set.accept(n);
                return true;
            }
            if (button == 1 && many && cs.length > 1) {
                List<String> n = new ArrayList<>(java.util.Arrays.asList(cs));
                n.remove(i);
                set.accept(n.toArray(new String[0]));
                return true;
            }
            int sx = x + i * (S + GAP);
            org.sprautestudio.spraute_engine.ui.widgets.ColorPicker.open(0xFF000000 | FxDef.parseColor(cs[i]), sx - 80, y + S + 4, v -> {
                String[] now = get.get().clone();
                if (i < now.length) {
                    now[i] = hex(v);
                    set.accept(now);
                }
            });
            return true;
        }

        @Override
        public int cursor(int mx, int my) {
            return at(mx) >= 0 ? UI.CURSOR_HAND : UI.CURSOR_ARROW;
        }
    }

    // ================================================================== preview viewport

    /** The effect played in a small scene: drag to turn around it, wheel to zoom, double click to fit. */
    private final class Viewport extends Widget implements UI.CursorProvider {
        float yaw = 35, pitch = 22, ppb = 24, fitTo = -1;
        boolean autoFit = true, dragging, paused;
        double lastX, lastY;
        long lastClick;

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.gradient(x, y, w, h, 0xFF1D2030, 0xFF0D0E14);
            // fit the view to the effect while it grows (smoothly, never jumping in)
            if (autoFit) {
                float ext = Math.max(2.2f, FxEngine.previewExtent());
                float want = Math.min(80, Math.min(w, h) * 0.42f / ext);
                if (fitTo < 0 || want < fitTo) fitTo = want;
                ppb += (fitTo - ppb) * Math.min(1, dt * 3);
            }
            c.flush();
            c.pushClip(x, y, w, h);
            try {
                FxEngine.renderPreview(c.g, x + w / 2f, y + h * 0.68f, ppb, yaw, pitch, x, y, w, h, paused);
            } catch (Throwable ignored) {
            }
            c.flush();
            c.popClip();
            double t = FxEngine.previewTime();
            String info = (t >= 0 ? String.format("%.1f с", t) : "закончился") + (doc.duration > 0 ? " / " + fmt(doc.duration) + " с" : " / пока не остановят")
                    + "   ·   " + FxEngine.previewParticles() + " частиц" + (paused ? "   ·   пауза" : "");
            c.text(info, x + 8, y + 8, Theme.TEXT_DIM);
            c.textEllipsized("Мышь — повернуть · колесо — приблизить · двойной клик — вписать · пробел — заново · P — пауза", x + 8, y + h - 14,
                    w - 16, Theme.TEXT_MUTED);
            if (doc.layers.isEmpty()) c.textCentered("Добавьте слой или возьмите шаблон", x + w / 2, y + h / 2, Theme.TEXT_MUTED);
        }

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            long now = System.currentTimeMillis();
            if (now - lastClick < 300) {
                autoFit = true;
                fitTo = -1;
                yaw = 35;
                pitch = 22;
            }
            lastClick = now;
            dragging = true;
            lastX = mx;
            lastY = my;
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (!dragging) return false;
            yaw += (float) (mx - lastX) * 0.6f;
            pitch = Math.max(-10, Math.min(89, pitch + (float) (my - lastY) * 0.5f));
            lastX = mx;
            lastY = my;
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            dragging = false;
            return true;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            autoFit = false;
            ppb = (float) Math.max(0.3, Math.min(200, ppb * Math.pow(1.15, delta)));
            return true;
        }

        @Override
        public int cursor(int mx, int my) {
            return UI.CURSOR_HAND;
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (super.keyPressed(key, scan, mods)) return true;
        if (!view.isHoveredDeep() && !layers.isHoveredDeep()) return false;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
            view.paused = false;
            play();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_P) {
            view.paused = !view.paused;
            return true;
        }
        return false;
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

    // ================================================================== layer list

    private final class Layers extends Widget {
        private static final int ROW = 22;

        private int rowAt(double my) {
            return (int) Math.floor((my - y - 4) / ROW) - 1;
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.fill(x, y, w, h, Theme.PANEL);
            c.fill(x + w - 1, y, 1, h, Theme.BORDER);
            int ry = y + 4;
            boolean hovEffect = isHovered() && rowAt(my) == -1;
            c.round(x + 4, ry, w - 8, ROW - 2, 3, sel == -1 ? Theme.ACCENT_DIM : hovEffect ? Theme.RAISED : 0);
            c.icon(Icon.BOLT, x + 10, ry + 6, Theme.ACCENT);
            c.textEllipsized(file != null ? file.name() : "Эффект", x + 24, ry + 6, w - 32, Theme.TEXT);
            ry += ROW;
            for (int i = 0; i < doc.layers.size(); i++) {
                FxDef.Layer l = doc.layers.get(i);
                boolean hov = isHovered() && rowAt(my) == i;
                c.round(x + 4, ry, w - 8, ROW - 2, 3, i == sel ? Theme.ACCENT_DIM : hov ? Theme.RAISED : 0);
                Icon ic = l.type.equals("flash") ? Icon.INFO : l.type.equals("shake") ? Icon.WARNING : Icon.BOLT;
                int col = 0xFF000000 | FxDef.parseColor(l.type.equals("flash") ? l.flashColor : l.color[0]);
                c.icon(ic, x + 18, ry + 6, l.enabled ? col : Theme.TEXT_MUTED);
                // the eye: on / off
                c.icon(l.enabled ? Icon.CHECK : Icon.CLOSE, x + 6, ry + 6, l.enabled ? Theme.TEXT_DIM : Theme.TEXT_MUTED);
                c.textEllipsized(l.name, x + 32, ry + 6, w - 60, l.enabled ? Theme.TEXT : Theme.TEXT_MUTED);
                if (i == sel) {
                    c.text("▲", x + w - 26, ry + 6, Theme.TEXT_DIM);
                    c.text("▼", x + w - 14, ry + 6, Theme.TEXT_DIM);
                }
                ry += ROW;
            }
            if (doc.layers.isEmpty()) c.textEllipsized("Нет слоёв: «Слой» или «Шаблон…»", x + 10, ry + 6, w - 16, Theme.TEXT_MUTED);
            c.textEllipsized(FxEngine.particles() + " частиц в мире", x + 10, y + h - 14, w - 16, Theme.TEXT_MUTED);
        }

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            int i = rowAt(my);
            if (i < -1 || i >= doc.layers.size()) return true;
            if (i >= 0 && i == sel && mx >= x + w - 28) {
                move(mx < x + w - 15 ? -1 : 1);
                return true;
            }
            if (i >= 0 && mx < x + 16) {
                doc.layers.get(i).enabled = !doc.layers.get(i).enabled;
                buildForm();
                changed();
                return true;
            }
            select(i);
            return true;
        }
    }

    // ================================================================== layout and tab

    private static final int LIST_W = 180, INSPECTOR_W = 320;
    private int toolbarH = 28;

    @Override
    public void layout() {
        int bx = x + 6, by = y + 4;
        for (Widget b : tools) {
            int bw = Math.max(b.prefW, 40);
            if (bx + bw > x + w - 6 && bx > x + 6) {
                bx = x + 6;
                by += 24;
            }
            b.bounds(bx, by, bw, 20);
            bx += bw + 4;
        }
        toolbarH = by - y + 24;
        int lw = Math.min(LIST_W, w / 4), iw = Math.min(INSPECTOR_W, w / 3);
        layers.bounds(x, y + toolbarH, lw, h - toolbarH);
        view.bounds(x + lw, y + toolbarH, w - lw - iw, h - toolbarH);
        inspector.bounds(x + w - iw, y + toolbarH, iw, h - toolbarH);
        form.layout();
        super.layout();
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.fill(x, y, w, h, Theme.CODE_BG);
        c.fill(x, y, w, toolbarH, Theme.PANEL);
        c.fill(x, y + toolbarH - 1, w, 1, Theme.BORDER);
    }

    @Override
    public String title() {
        return file != null ? file.name() : "Эффект";
    }

    @Override
    public Icon icon() {
        return Icon.BOLT;
    }

    @Override
    public int iconColor() {
        return 0xFFFFC94A;
    }

    @Override
    public boolean isDirty() {
        return !doc.toJson().equals(saved);
    }

    @Override
    public void save() throws IOException {
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
