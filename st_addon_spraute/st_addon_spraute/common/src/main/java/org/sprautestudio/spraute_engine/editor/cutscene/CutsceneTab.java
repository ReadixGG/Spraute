package org.sprautestudio.spraute_engine.editor.cutscene;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.spraute_engine.cutscene.CutsceneDoc;
import org.sprautestudio.spraute_engine.cutscene.Ease;
import org.sprautestudio.spraute_engine.cutscene.client.CameraDirector;
import org.sprautestudio.spraute_engine.editor.ClientState;
import org.sprautestudio.spraute_engine.editor.SprauteScreen;
import org.sprautestudio.spraute_engine.editor.project.Project;
import org.sprautestudio.spraute_engine.editor.tabs.EditorTab;
import org.sprautestudio.spraute_engine.lang.Analyzer;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Cutscene editor: a top view of the camera path with draggable frames and view directions, a
 * timeline, an inspector for the selected frame and the scene, frames taken from the player's
 * view (also while flying around in the game) and previews in the game.
 */
public class CutsceneTab extends EditorTab {
    private CutsceneDoc doc;
    private String saved;
    private int sel = -1;
    private final Deque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

    private final Form form = new Form();
    private final ScrollView inspector;
    private final MapView map = new MapView();
    private final Timeline timeline = new Timeline();
    private final List<Button> tools;
    /** Anchor of a relative cutscene while editing: frames from the player are stored relative to it. */
    private Vec3 relAnchor;
    /** In-editor playback of the timeline (seconds), or -1. */
    private double playhead = 0;
    private long playStarted = -1;
    private double playFrom;

    public CutsceneTab(Project project, Project.FileRef file, String content) {
        super(project, file);
        doc = CutsceneDoc.fromJson(content);
        saved = doc.toJson();
        add(map);
        add(timeline);
        inspector = add(new ScrollView(form));
        tools = List.of(
                tool("Редактор в мире", Icon.CUBE, "Летать камерой по миру и править кадры прямо там: виден весь путь, кадры и куда они смотрят",
                        () -> CutsceneWorldEditor.start(this), Button.Style.PRIMARY),
                tool("Кадр с игрока", Icon.PLUS, "Добавить кадр там, где стоит и куда смотрит игрок (позиция глаз)", this::addFromPlayer, Button.Style.DEFAULT),
                tool("Запись в игре", Icon.FILM, "Закрыть редактор и летать по миру: K — добавить кадр, N — вернуться", this::record, Button.Style.DEFAULT),
                tool("Обновить кадр", Icon.REFRESH, "Поставить выбранный кадр в позицию и взгляд игрока", this::updateFromPlayer, Button.Style.GHOST),
                tool("К кадру", Icon.PIN, "Телепортировать игрока в выбранный кадр (посмотреть ракурс)", this::teleport, Button.Style.GHOST),
                tool("Дублировать", Icon.COPY, "Копия выбранного кадра через секунду", this::duplicate, Button.Style.GHOST),
                tool("Удалить", Icon.TRASH, "Удалить выбранный кадр (Delete)", this::deleteSelected, Button.Style.GHOST),
                tool("Просмотр", Icon.PLAY, "Проиграть катсцену в игре (пробел — пропустить)", () -> preview(0), Button.Style.SUCCESS),
                tool("С кадра", Icon.SKIP, "Проиграть в игре начиная с выбранного кадра", () -> preview(sel), Button.Style.GHOST));
        buildForm();
    }

    private Button tool(String text, Icon icon, String tip, Runnable r, Button.Style style) {
        Button b = add(new Button(text, r).icon(icon).style(style));
        b.tooltip(tip);
        b.autoWidth();
        return b;
    }

    // ================================================================== API for the world editor

    CutsceneDoc doc() {
        return doc;
    }

    /** Where relative frames are measured from while editing; zero for absolute ones. */
    Vec3 origin() {
        if (!doc.relative) return Vec3.ZERO;
        if (relAnchor == null) {
            LocalPlayer p = Minecraft.getInstance().player;
            relAnchor = p != null ? p.position() : Vec3.ZERO;
        }
        return relAnchor;
    }

    CutsceneDoc.Key selectedKey() {
        return selected();
    }

    int selectedIndex() {
        return selected() != null ? sel : -1;
    }

    void selectIndex(int i) {
        select(Math.max(-1, Math.min(doc.keys.size() - 1, i)));
    }

    void snapshotNow() {
        snapshot();
    }

    void changed() {
        resort();
        buildForm();
    }

    void toggleFlow() {
        snapshot();
        doc.flow = !doc.flow;
        buildForm();
    }

    private CutsceneDoc.Key keyOf(CutsceneDoc.Pose p) {
        Vec3 o = origin();
        CutsceneDoc.Key k = new CutsceneDoc.Key();
        k.x = round(p.x - o.x);
        k.y = round(p.y - o.y);
        k.z = round(p.z - o.z);
        k.yaw = (float) round(CutsceneDoc.wrap(p.yaw));
        k.pitch = (float) round(p.pitch);
        k.fov = Minecraft.getInstance().options.fov().get();
        return k;
    }

    /** A frame at a camera pose, right after the selected one (between it and the next). */
    void addFromPose(CutsceneDoc.Pose p) {
        CutsceneDoc.Key k = keyOf(p);
        snapshot();
        CutsceneDoc.Key s = selected();
        if (s != null && sel < doc.keys.size() - 1) {
            CutsceneDoc.Key next = doc.keys.get(sel + 1);
            k.t = (float) (Math.round((s.t + next.t) / 2 * 100) / 100.0);
            k.ease = s.ease;
            k.fov = s.fov;
        } else {
            CutsceneDoc.Key last = doc.keys.isEmpty() ? null : doc.keys.get(doc.keys.size() - 1);
            k.t = last == null ? 0 : last.t + 2;
            if (last != null) {
                k.ease = last.ease;
                k.fov = last.fov;
            }
        }
        doc.keys.add(k);
        resort();
        sel = doc.keys.indexOf(k);
        buildForm();
    }

    /** Moves the selected frame to a camera pose. */
    void setSelectedTo(CutsceneDoc.Pose p, boolean undoable) {
        CutsceneDoc.Key s = selected();
        if (s == null) return;
        if (undoable) snapshot();
        CutsceneDoc.Key k = keyOf(p);
        s.x = k.x;
        s.y = k.y;
        s.z = k.z;
        s.yaw = k.yaw;
        s.pitch = k.pitch;
    }

    // ================================================================== editing

    private void snapshot() {
        undo.push(doc.toJson());
        if (undo.size() > 100) undo.removeLast();
        redo.clear();
    }

    @Override
    public void undo() {
        if (undo.isEmpty()) return;
        redo.push(doc.toJson());
        doc = CutsceneDoc.fromJson(undo.pop());
        sel = Math.min(sel, doc.keys.size() - 1);
        buildForm();
    }

    @Override
    public void redo() {
        if (redo.isEmpty()) return;
        undo.push(doc.toJson());
        doc = CutsceneDoc.fromJson(redo.pop());
        sel = Math.min(sel, doc.keys.size() - 1);
        buildForm();
    }

    private CutsceneDoc.Key selected() {
        return sel >= 0 && sel < doc.keys.size() ? doc.keys.get(sel) : null;
    }

    private void select(int i) {
        sel = i;
        buildForm();
    }

    /** Re-sorts by time keeping the same frame selected. */
    private void resort() {
        CutsceneDoc.Key k = selected();
        doc.sort();
        if (k != null) sel = doc.keys.indexOf(k);
    }

    private static UI ui() {
        return UI.get();
    }

    /** A frame where the player is and looks. */
    private CutsceneDoc.Key fromPlayer() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            if (ui() != null) ui().toastError("Войдите в мир, чтобы брать кадры с игрока");
            return null;
        }
        CutsceneDoc.Key k = new CutsceneDoc.Key();
        Vec3 e = p.getEyePosition(1);
        if (doc.relative) {
            if (relAnchor == null) relAnchor = doc.keys.isEmpty() ? p.position() : p.position().subtract(doc.keys.get(doc.keys.size() - 1).x, 0, doc.keys.get(doc.keys.size() - 1).z);
            e = e.subtract(relAnchor);
        }
        k.x = round(e.x);
        k.y = round(e.y);
        k.z = round(e.z);
        k.yaw = (float) round(CutsceneDoc.wrap(p.getYRot()));
        k.pitch = (float) round(p.getXRot());
        k.fov = Minecraft.getInstance().options.fov().get();
        return k;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    public void addFromPlayer() {
        CutsceneDoc.Key k = fromPlayer();
        if (k == null) return;
        snapshot();
        CutsceneDoc.Key last = doc.keys.isEmpty() ? null : doc.keys.get(doc.keys.size() - 1);
        k.t = last == null ? 0 : last.t + 2;
        if (last != null) k.ease = last.ease;
        doc.keys.add(k);
        resort();
        sel = doc.keys.indexOf(k);
        buildForm();
    }

    public int frames() {
        return doc.keys.size();
    }

    private void updateFromPlayer() {
        CutsceneDoc.Key s = selected(), k = fromPlayer();
        if (s == null || k == null) return;
        snapshot();
        s.x = k.x;
        s.y = k.y;
        s.z = k.z;
        s.yaw = k.yaw;
        s.pitch = k.pitch;
        buildForm();
    }

    private void teleport() {
        CutsceneDoc.Key s = selected();
        if (s == null) return;
        double ox = 0, oy = 0, oz = 0;
        if (doc.relative && relAnchor != null) {
            ox = relAnchor.x;
            oy = relAnchor.y;
            oz = relAnchor.z;
        }
        ClientState.get().command(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", s.x + ox, s.y + oy - 1.62, s.z + oz, s.yaw, s.pitch));
    }

    private void duplicate() {
        CutsceneDoc.Key s = selected();
        if (s == null) return;
        snapshot();
        CutsceneDoc.Key k = s.copy();
        k.t = s.t + 1;
        for (CutsceneDoc.Key o : doc.keys) if (o.t > s.t) o.t += 1;
        k.mark = "";
        doc.keys.add(k);
        resort();
        sel = doc.keys.indexOf(k);
        buildForm();
    }

    void deleteSelected() {
        if (selected() == null) return;
        snapshot();
        doc.keys.remove(sel);
        sel = Math.min(sel, doc.keys.size() - 1);
        buildForm();
    }

    private void record() {
        if (Minecraft.getInstance().player == null) {
            if (ui() != null) ui().toastError("Войдите в мир");
            return;
        }
        CutsceneRecorder.start(this);
        Minecraft.getInstance().setScreen(null);
    }

    /** Plays the cutscene in the game from frame {@code from}, then reopens the editor. */
    private void preview(int from) {
        previewFrom(from, SprauteScreen::open);
    }

    void previewFrom(int from, Runnable onEnd) {
        if (Minecraft.getInstance().player == null) {
            if (ui() != null) ui().toastError("Войдите в мир, чтобы посмотреть катсцену");
            return;
        }
        if (doc.keys.isEmpty()) return;
        CutsceneDoc d = CutsceneDoc.fromJson(doc.toJson());
        if (from > 0 && from < d.keys.size()) {
            float t0 = d.keys.get(from).t;
            d.keys = new java.util.ArrayList<>(d.keys.subList(from, d.keys.size()));
            for (CutsceneDoc.Key k : d.keys) k.t -= t0;
            d.fadeIn = 0;
        }
        Vec3 origin = doc.relative ? origin() : Vec3.ZERO;
        Minecraft.getInstance().setScreen(null);
        CameraDirector.play(-1, file != null ? file.name() : "preview", d, origin, Map.of(), (kind, name) -> {
            if (kind.equals("end")) onEnd.run();
            else if (kind.equals("mark")) Minecraft.getInstance().gui.setOverlayMessage(net.minecraft.network.chat.Component.literal("метка: " + name), false);
        });
    }

    // ================================================================== inspector

    private void buildForm() {
        form.clear();
        CutsceneDoc.Key k = selected();
        if (k != null) {
            form.header("Кадр " + (sel + 1) + " из " + doc.keys.size());
            form.decimal("Время, с", () -> k.t, v -> {
                k.t = (float) Math.max(0, v);
                resort();
            });
            form.decimal("X", () -> k.x, v -> k.x = v);
            form.decimal("Y", () -> k.y, v -> k.y = v);
            form.decimal("Z", () -> k.z, v -> k.z = v);
            form.decimal("Поворот (yaw)", () -> k.yaw, v -> k.yaw = (float) v);
            form.decimal("Наклон (pitch)", () -> k.pitch, v -> k.pitch = (float) Math.max(-90, Math.min(90, v)));
            form.decimal("Крен (roll)", () -> k.roll, v -> k.roll = (float) v);
            form.decimal("Обзор (FOV)", () -> k.fov, v -> k.fov = (float) Math.max(5, Math.min(170, v)));
            form.dropdown("Плавность", "Как камера движется от этого кадра к следующему", Ease::names, () -> k.ease,
                    e -> Ease.TITLES.getOrDefault(e, e), e -> {
                        snapshot();
                        k.ease = e;
                    });
            form.text("Смотреть на", "player — на игрока, имя персонажа сюжета или моба (\"minecraft:wolf\"). Пусто — заданный поворот",
                    () -> k.look, v -> k.look = v.replace("\"", "").trim());
            form.text("Субтитр", "Текст внизу экрана, пока камера летит от этого кадра", () -> k.caption, v -> k.caption = v);
            form.text("Метка", "Событие при достижении кадра: on cutscenemark \"имя\" { ... }", () -> k.mark, v -> k.mark = v.trim());
            form.decimal("Тряска 0..1", () -> k.shake, v -> k.shake = (float) Math.max(0, Math.min(1, v)));
        } else {
            form.header("Кадры");
            form.note(doc.keys.isEmpty() ? "Нет кадров: нажмите «Кадр с игрока» или «Запись в игре»" : "Выберите кадр на карте или таймлайне");
        }
        form.header("Катсцена");
        form.toggle("Плавный путь", "Камера летит по кривой через кадры; выключено — по прямым", () -> doc.smooth, v -> doc.smooth = v);
        form.toggle("Сплошное движение", "Одно плавное движение по всей линии с ровной скоростью, без замедлений у кадров. "
                + "Время кадров задаёт только общую длину", () -> doc.flow, v -> {
            doc.flow = v;
            buildForm();
        });
        if (doc.flow) {
            form.dropdown("Общая плавность", "Как движение разгоняется в начале и тормозит в конце", Ease::names, () -> doc.flowEase,
                    e -> Ease.TITLES.getOrDefault(e, e), e -> {
                        snapshot();
                        doc.flowEase = e;
                    });
        }
        form.decimal("Кинополосы", "Доля высоты экрана сверху и снизу: 0.11 — как в кино, 0 — без полос", () -> doc.bars,
                v -> doc.bars = (float) Math.max(0, Math.min(0.45, v)));
        form.dropdown("Скрыть интерфейс", "Что прячется на время катсцены. Речь, титры и субтитры видны всегда",
                () -> CutsceneDoc.HIDE_MODES, doc::hideMode, CutsceneDoc::hideTitle, v -> {
                    doc.hide = v;
                    doc.hideHud = !v.equals("none");
                });
        form.toggle("Игрок не двигается", null, () -> doc.lockPlayer, v -> doc.lockPlayer = v);
        form.toggle("Можно пропустить", "Пробел или Enter пропускают катсцену (событие cutsceneskip)", () -> doc.skippable, v -> doc.skippable = v);
        form.decimal("Вход, с", "Плавный переход от вида игрока к первому кадру", () -> doc.blendIn, v -> doc.blendIn = (float) Math.max(0, v));
        form.decimal("Выход, с", "Плавный возврат камеры игроку после конца", () -> doc.blendOut, v -> doc.blendOut = (float) Math.max(0, v));
        form.decimal("Из затемнения, с", "Начать с затемнённого экрана и проявиться", () -> doc.fadeIn, v -> doc.fadeIn = (float) Math.max(0, v));
        form.decimal("В затемнение, с", "Затемнить экран к концу", () -> doc.fadeOut, v -> doc.fadeOut = (float) Math.max(0, v));
        form.text("Цвет затемнения", "#RRGGBB", () -> doc.fadeColor, v -> doc.fadeColor = v.trim());
        form.toggle("Относительно точки", "Координаты кадров — смещения от точки, где играют катсцену (cutscene play \"имя\" at alice)",
                () -> doc.relative, v -> {
                    snapshot();
                    toggleRelative(v);
                });
        form.note("Длина: " + String.format(java.util.Locale.ROOT, "%.1f", doc.duration()) + " с   ·   в сюжете: cutscene play \"" + (file != null ? file.name() : "") + "\"");
        if (w > 0) layout();
    }

    /** Switching to relative moves the frames so the first one sits at the play point. */
    private void toggleRelative(boolean on) {
        if (on == doc.relative) return;
        if (on && !doc.keys.isEmpty()) {
            CutsceneDoc.Key f = doc.keys.get(0);
            LocalPlayer p = Minecraft.getInstance().player;
            Vec3 a = new Vec3(f.x, f.y - 1.62, f.z);
            relAnchor = p != null ? a : null;
            for (CutsceneDoc.Key k : doc.keys) {
                k.x = round(k.x - a.x);
                k.y = round(k.y - a.y);
                k.z = round(k.z - a.z);
            }
        } else if (!on && relAnchor != null) {
            for (CutsceneDoc.Key k : doc.keys) {
                k.x = round(k.x + relAnchor.x);
                k.y = round(k.y + relAnchor.y);
                k.z = round(k.z + relAnchor.z);
            }
            relAnchor = null;
        }
        doc.relative = on;
        buildForm();
    }

    private static final int LABEL_W = 108;

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
                snapshot();
                set.accept(v);
            }));
        }

        void decimal(String label, DoubleSupplier get, DoubleConsumer set) {
            decimal(label, null, get, set);
        }

        void decimal(String label, String doc, DoubleSupplier get, DoubleConsumer set) {
            Row r = row(label, doc);
            TextField f = new TextField() {
                @Override
                public void tick() {
                    super.tick();
                    if (!isFocused()) {
                        String s = fmt(get.getAsDouble());
                        if (!s.equals(text())) setText(s);
                    }
                }

                @Override
                public void focusGained() {
                    super.focusGained();
                    snapshot();
                }
            };
            f.setText(fmt(get.getAsDouble()));
            f.filter = ch -> Character.isDigit(ch) || ch == '-' || ch == '.';
            f.onChange = s -> {
                try {
                    set.accept(Double.parseDouble(s.trim()));
                } catch (NumberFormatException ignored) {
                }
            };
            r.editor = r.add(f);
        }

        void text(String label, String doc, java.util.function.Supplier<String> get, Consumer<String> set) {
            Row r = row(label, doc);
            TextField f = new TextField() {
                @Override
                public void focusGained() {
                    super.focusGained();
                    snapshot();
                }
            };
            f.setText(get.get());
            f.onChange = set;
            r.editor = r.add(f);
        }

        <T> void dropdown(String label, String doc, java.util.function.Supplier<List<T>> options, java.util.function.Supplier<T> get,
                          java.util.function.Function<T, String> labeler, Consumer<T> set) {
            Row r = row(label, doc);
            r.editor = r.add(new Dropdown<>(options, get, labeler, set));
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
        double r = Math.round(v * 100) / 100.0;
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

    // ================================================================== layout

    private static final int INSPECTOR_W = 290, TIMELINE_H = 74;
    /** Height of the tool rows: buttons that do not fit wrap onto the next row. */
    private int toolbarH = 28;

    @Override
    public void layout() {
        int iw = Math.min(INSPECTOR_W, w / 2);
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
        int TOOLBAR_H = toolbarH;
        map.bounds(x, y + TOOLBAR_H, w - iw, h - TOOLBAR_H - TIMELINE_H);
        timeline.bounds(x, y + h - TIMELINE_H, w - iw, TIMELINE_H);
        inspector.bounds(x + w - iw, y + TOOLBAR_H, iw, h - TOOLBAR_H);
        form.layout();
        super.layout();
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.fill(x, y, w, h, Theme.CODE_BG);
        int TOOLBAR_H = toolbarH;
        c.fill(x, y, w, TOOLBAR_H, Theme.PANEL);
        c.fill(x, y + TOOLBAR_H - 1, w, 1, Theme.BORDER);
        int iw = Math.min(INSPECTOR_W, w / 2);
        c.fill(x + w - iw, y + TOOLBAR_H, iw, h - TOOLBAR_H, Theme.PANEL);
        c.fill(x + w - iw, y + TOOLBAR_H, 1, h - TOOLBAR_H, Theme.BORDER);
        if (playStarted >= 0) {
            playhead = playFrom + (System.nanoTime() - playStarted) / 1e9;
            if (playhead >= doc.duration()) {
                playhead = doc.duration();
                playStarted = -1;
            }
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (super.keyPressed(key, scan, mods)) return true;
        if (!isHoveredDeep()) return false;
        switch (key) {
            case GLFW.GLFW_KEY_DELETE -> deleteSelected();
            case GLFW.GLFW_KEY_SPACE -> togglePlay();
            case GLFW.GLFW_KEY_LEFT -> {
                if (sel > 0) select(sel - 1);
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (sel < doc.keys.size() - 1) select(sel + 1);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Plays the timeline inside the editor (the camera marker moves on the map). */
    private void togglePlay() {
        if (playStarted >= 0) {
            playStarted = -1;
            return;
        }
        if (playhead >= doc.duration()) playhead = 0;
        playFrom = playhead;
        playStarted = System.nanoTime();
    }

    // ================================================================== top view

    private final class MapView extends Widget {
        double cx, cz, scale = 8;
        boolean fitted;
        final org.sprautestudio.spraute_engine.editor.MiniMap minimap = new org.sprautestudio.spraute_engine.editor.MiniMap();
        int dragKey = -1, dragYaw = -1;
        boolean panning;

        int sx(double wx) {
            return (int) Math.round(x + w / 2.0 + (wx - cx) * scale);
        }

        int sy(double wz) {
            return (int) Math.round(y + h / 2.0 + (wz - cz) * scale);
        }

        double wx(double px) {
            return cx + (px - x - w / 2.0) / scale;
        }

        double wz(double py) {
            return cz + (py - y - h / 2.0) / scale;
        }

        void fit() {
            if (doc.keys.isEmpty()) {
                LocalPlayer p = Minecraft.getInstance().player;
                if (p != null && !doc.relative) {
                    cx = p.getX();
                    cz = p.getZ();
                }
                return;
            }
            double x0 = 1e9, x1 = -1e9, z0 = 1e9, z1 = -1e9;
            for (CutsceneDoc.Key k : doc.keys) {
                x0 = Math.min(x0, k.x);
                x1 = Math.max(x1, k.x);
                z0 = Math.min(z0, k.z);
                z1 = Math.max(z1, k.z);
            }
            cx = (x0 + x1) / 2;
            cz = (z0 + z1) / 2;
            double span = Math.max(Math.max(x1 - x0, z1 - z0), 8);
            scale = Math.max(1, Math.min(60, Math.min(w, h) * 0.8 / span));
        }

        int[] yawTip(CutsceneDoc.Key k) {
            double a = Math.toRadians(k.yaw);
            int px = sx(k.x), py = sy(k.z);
            return new int[]{(int) Math.round(px - Math.sin(a) * 22), (int) Math.round(py + Math.cos(a) * 22)};
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            if (!fitted && w > 0) {
                fit();
                fitted = true;
            }
            c.pushClip(x, y, w, h);
            c.fill(x, y, w, h, 0xFF15161C);
            // the world from above (frames of a relative scene are drawn from where it plays)
            Vec3 off = doc.relative ? origin() : Vec3.ZERO;
            minimap.draw(c, x, y, w, h, cx + off.x, cz + off.z, scale);
            // grid: lines every 1, 5 or 10+ blocks depending on zoom
            double step = scale >= 20 ? 1 : scale >= 6 ? 5 : scale >= 2 ? 10 : 50;
            double gx0 = Math.floor(wx(x) / step) * step, gz0 = Math.floor(wz(y) / step) * step;
            for (double gx = gx0; sx(gx) < x + w; gx += step) {
                boolean major = Math.round(gx / step) % 5 == 0;
                c.fill(sx(gx), y, 1, h, major ? 0x40FFFFFF : 0x16FFFFFF);
            }
            for (double gz = gz0; sy(gz) < y + h; gz += step) {
                boolean major = Math.round(gz / step) % 5 == 0;
                c.fill(x, sy(gz), w, 1, major ? 0x40FFFFFF : 0x16FFFFFF);
            }
            // the player
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null && !doc.relative) {
                int px = sx(p.getX()), py = sy(p.getZ());
                c.round(px - 4, py - 4, 8, 8, 4, 0xFFFFFFFF);
                c.text("игрок", px + 6, py - 4, 0x99FFFFFF);
            } else if (doc.relative) {
                int px = sx(0), py = sy(0);
                c.round(px - 3, py - 3, 6, 6, 3, 0xFFFFCB6B);
                c.text("точка запуска", px + 6, py - 4, 0x99FFCB6B);
            }
            // the path
            if (doc.keys.size() > 1) {
                double dur = doc.duration();
                int n = Math.max(20, (int) (dur * 20));
                CutsceneDoc.Pose prev = doc.at(0);
                for (int i = 1; i <= n; i++) {
                    CutsceneDoc.Pose cur = doc.at(dur * i / n);
                    c.line(sx(prev.x), sy(prev.z), sx(cur.x), sy(cur.z), 1.5f, 0xFF4FC3F7);
                    prev = cur;
                }
            }
            // frames
            for (int i = 0; i < doc.keys.size(); i++) {
                CutsceneDoc.Key k = doc.keys.get(i);
                int px = sx(k.x), py = sy(k.z);
                int[] tip = yawTip(k);
                boolean s = i == sel;
                c.line(px, py, tip[0], tip[1], s ? 2f : 1.2f, s ? 0xFFFFCB6B : 0xAAFFCB6B);
                if (s) c.round(tip[0] - 3, tip[1] - 3, 6, 6, 3, 0xFFFFCB6B);
                c.round(px - 6, py - 6, 12, 12, 6, s ? Theme.ACCENT : 0xFF3D4157);
                c.outline(px - 6, py - 6, 12, 12, s ? 0xFFFFFFFF : 0xFF5A5F7A);
                String num = String.valueOf(i + 1);
                c.text(num, px - c.width(num) / 2 + 1, py - 3, 0xFFFFFFFF);
                if (!k.mark.isEmpty()) c.text("⚑ " + k.mark, px + 9, py + 4, 0xFFFF8F5A);
            }
            // camera at the playhead
            if (!doc.keys.isEmpty()) {
                CutsceneDoc.Pose cam = doc.at(playhead);
                int px = sx(cam.x), py = sy(cam.z);
                double a = Math.toRadians(cam.yaw);
                c.line(px, py, (float) (px - Math.sin(a) * 14), (float) (py + Math.cos(a) * 14), 2f, 0xFF66FF99);
                c.round(px - 4, py - 4, 8, 8, 2, 0xFF66FF99);
            }
            c.popClip();
            String help = "Тяните кадры и их стрелки взгляда · ПКМ — двигать карту · колёсико — масштаб · двойной клик — кадр здесь";
            c.textEllipsized(help, x + 6, y + h - 12, w - 12, 0x77FFFFFF);
            c.text("Вид сверху (X → , Z ↓)", x + 6, y + 5, 0x88FFFFFF);
        }

        int keyAt(double mx, double my) {
            for (int i = doc.keys.size() - 1; i >= 0; i--) {
                CutsceneDoc.Key k = doc.keys.get(i);
                if (Math.abs(mx - sx(k.x)) <= 7 && Math.abs(my - sy(k.z)) <= 7) return i;
            }
            return -1;
        }

        long lastClick;

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            if (button == 1 || button == 2) {
                panning = true;
                return true;
            }
            if (button != 0) return false;
            CutsceneDoc.Key s = selected();
            if (s != null) {
                int[] tip = yawTip(s);
                if (Math.abs(mx - tip[0]) <= 5 && Math.abs(my - tip[1]) <= 5) {
                    snapshot();
                    dragYaw = sel;
                    return true;
                }
            }
            int k = keyAt(mx, my);
            long now = System.currentTimeMillis();
            if (k < 0 && now - lastClick < 350) {
                // a frame here: height and view of the selected (or last) frame
                snapshot();
                CutsceneDoc.Key base = s != null ? s : doc.keys.isEmpty() ? null : doc.keys.get(doc.keys.size() - 1);
                CutsceneDoc.Key nk = base != null ? base.copy() : new CutsceneDoc.Key();
                if (base == null) {
                    LocalPlayer p = Minecraft.getInstance().player;
                    nk.y = p != null ? round(p.getEyeY()) : 70;
                }
                nk.x = round(wx(mx));
                nk.z = round(wz(my));
                nk.t = doc.keys.isEmpty() ? 0 : doc.keys.get(doc.keys.size() - 1).t + 2;
                nk.mark = "";
                doc.keys.add(nk);
                resort();
                select(doc.keys.indexOf(nk));
                return true;
            }
            lastClick = now;
            if (k >= 0) {
                if (k != sel) select(k);
                snapshot();
                dragKey = k;
            }
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (panning) {
                cx -= dx / scale;
                cz -= dy / scale;
                return true;
            }
            if (dragYaw >= 0 && dragYaw < doc.keys.size()) {
                CutsceneDoc.Key k = doc.keys.get(dragYaw);
                double ax = mx - sx(k.x), az = my - sy(k.z);
                k.yaw = (float) round(Math.toDegrees(Math.atan2(-ax, az)));
                return true;
            }
            if (dragKey >= 0 && dragKey < doc.keys.size()) {
                CutsceneDoc.Key k = doc.keys.get(dragKey);
                double snap = net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 0.5 : 0.05;
                k.x = Math.round(wx(mx) / snap) * snap;
                k.z = Math.round(wz(my) / snap) * snap;
                k.x = round(k.x);
                k.z = round(k.z);
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            panning = false;
            dragKey = -1;
            dragYaw = -1;
            return true;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            double bx = wx(mx), bz = wz(my);
            scale = Math.max(0.5, Math.min(80, scale * (delta > 0 ? 1.2 : 1 / 1.2)));
            cx = bx - (mx - x - w / 2.0) / scale;
            cz = bz - (my - y - h / 2.0) / scale;
            return true;
        }
    }

    // ================================================================== timeline

    private final class Timeline extends Widget {
        int dragKey = -1;
        boolean scrubbing;

        double span() {
            return Math.max(5, Math.ceil(doc.duration() + 2));
        }

        int tx(double t) {
            return (int) Math.round(x + 14 + t / span() * (w - 28));
        }

        double tAt(double px) {
            return Math.max(0, (px - x - 14) / (w - 28) * span());
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.fill(x, y, w, h, Theme.PANEL);
            c.fill(x, y, w, 1, Theme.BORDER);
            double span = span();
            double step = span <= 12 ? 1 : span <= 40 ? 5 : 10;
            for (double t = 0; t <= span + 1e-6; t += step) {
                int px = tx(t);
                c.fill(px, y + 14, 1, 6, Theme.BORDER_STRONG);
                c.text(fmt(t) + "с", px + 2, y + 5, Theme.TEXT_MUTED);
            }
            int ly = y + 30;
            c.fill(x + 14, ly + 6, w - 28, 2, 0xFF2A2C38);
            // captions as bars between frames
            for (int i = 0; i + 1 < doc.keys.size(); i++) {
                CutsceneDoc.Key a = doc.keys.get(i), b = doc.keys.get(i + 1);
                if (!a.caption.isEmpty()) {
                    c.round(tx(a.t), ly + 18, Math.max(2, tx(b.t) - tx(a.t)), 12, 2, 0xFF3A3F5A);
                    c.textEllipsized(a.caption, tx(a.t) + 3, ly + 20, tx(b.t) - tx(a.t) - 5, Theme.TEXT_DIM);
                }
            }
            for (int i = 0; i < doc.keys.size(); i++) {
                CutsceneDoc.Key k = doc.keys.get(i);
                int px = tx(k.t);
                boolean s = i == sel;
                // a diamond
                for (int d = 0; d <= 5; d++) c.fill(px - d, ly + 7 - (5 - d), d * 2 + 1, 1, s ? Theme.ACCENT : 0xFFB0B7C9);
                for (int d = 0; d <= 5; d++) c.fill(px - d, ly + 7 + (5 - d), d * 2 + 1, 1, s ? Theme.ACCENT : 0xFFB0B7C9);
                if (!k.mark.isEmpty()) c.text("⚑", px - 3, ly - 8, 0xFFFF8F5A);
            }
            int ph = tx(playhead);
            c.fill(ph, y + 12, 1, h - 14, 0xFF66FF99);
            String info = fmt(playhead) + " / " + fmt(doc.duration()) + " с   ·   пробел — проиграть здесь";
            c.text(info, x + w - c.width(info) - 8, y + 5, Theme.TEXT_MUTED);
        }

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            if (button != 0) return false;
            for (int i = 0; i < doc.keys.size(); i++) {
                if (Math.abs(mx - tx(doc.keys.get(i).t)) <= 6 && my >= y + 24 && my <= y + 44) {
                    if (i != sel) select(i);
                    snapshot();
                    dragKey = i;
                    return true;
                }
            }
            scrubbing = true;
            playStarted = -1;
            playhead = Math.min(doc.duration(), tAt(mx));
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (dragKey >= 0 && dragKey < doc.keys.size()) {
                double snap = net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 0.5 : 0.05;
                doc.keys.get(dragKey).t = (float) (Math.round(tAt(mx) / snap) * snap);
                CutsceneDoc.Key k = doc.keys.get(dragKey);
                resort();
                dragKey = doc.keys.indexOf(k);
                return true;
            }
            if (scrubbing) {
                playhead = Math.min(doc.duration(), tAt(mx));
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            if (dragKey >= 0) buildForm();
            dragKey = -1;
            scrubbing = false;
            return true;
        }
    }

    // ================================================================== tab

    @Override
    public Icon icon() {
        return Icon.FILM;
    }

    @Override
    public int iconColor() {
        return 0xFFFF8F5A;
    }

    @Override
    public boolean isDirty() {
        return !doc.toJson().equals(saved);
    }

    @Override
    public void save() throws IOException {
        doc.sort();
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
