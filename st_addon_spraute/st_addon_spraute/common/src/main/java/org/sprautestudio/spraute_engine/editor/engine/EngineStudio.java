package org.sprautestudio.spraute_engine.editor.engine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.spraute_engine.editor.ClientState;
import org.sprautestudio.spraute_engine.editor.project.Project;
import org.sprautestudio.spraute_engine.editor.tabs.EditorTab;
import org.sprautestudio.spraute_engine.lang.Analyzer;
import org.sprautestudio.spraute_engine.engine.EngineConfig;
import org.sprautestudio.spraute_engine.gui.GuiDoc;
import org.sprautestudio.spraute_engine.gui.GuiSchema;
import org.sprautestudio.spraute_engine.gui.client.GuiView;
import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.Widget;
import org.sprautestudio.spraute_engine.ui.widgets.Button;
import org.sprautestudio.spraute_engine.ui.widgets.Checkbox;
import org.sprautestudio.spraute_engine.ui.widgets.ColorPicker;
import org.sprautestudio.spraute_engine.ui.widgets.Dropdown;
import org.sprautestudio.spraute_engine.ui.widgets.PopupMenu;
import org.sprautestudio.spraute_engine.ui.widgets.ScrollView;
import org.sprautestudio.spraute_engine.ui.widgets.TextField;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The engine studio: the engine's settings as an application of their own. In the middle — the game's screen (the
 * game as it was when the editor opened, behind it) with the scene picked on the left: the HUD while playing, the
 * choice menu, a quest notice, a zone's name, the chat history. Every part of the scene's window is dragged,
 * resized and anchored right there, with guides to the screen's centre and edges and to the other parts; the
 * inspector on the right edits the picked part and, with nothing picked, the window's own behaviour. Screens and
 * systems (journal, shop, systems, speech) have their pages. Saved to the project's engine.json, applied to the
 * world with one button.
 */
public final class EngineStudio extends EditorTab {
    private static final int TOP = 46, LEFT = 236, RIGHT = 290, STATUS = 24;

    // ================================================================== scenes

    private record Scene(String key, String title, String group, Icon icon, String doc) {
    }

    private static final List<Scene> SCENES = List.of(
            new Scene("hud", "Игра (HUD)", "СЦЕНЫ", Icon.USER, "Что видно во время игры"),
            new Scene("choice", "Меню выбора", "СЦЕНЫ", Icon.LIST, "Вопрос персонажа и ответы"),
            new Scene("toast", "Уведомление квеста", "СЦЕНЫ", Icon.FLAG, "«Новый квест», «Задача выполнена»…"),
            new Scene("zone", "Название зоны", "СЦЕНЫ", Icon.PIN, "Надпись при входе в зону"),
            new Scene("chatlog", "История чата", "СЦЕНЫ", Icon.NOTE, "Фон и рамка истории сообщений"),
            new Scene("quest", "Журнал квестов", "ЭКРАНЫ", Icon.BOOK, "Журнал (J): вкладки, список, страница квеста"),
            new Scene("shop", "Магазин", "ЭКРАНЫ", Icon.TAG, "Окно торговли: товары, выбранный товар, цена, кнопка"),
            new Scene("systems", "Системы и время", "СИСТЕМЫ", Icon.GEAR, "Здоровье и монеты Spraute, инвентарь, слоты, чат, время суток"),
            new Scene("speech", "Речь", "СИСТЕМЫ", Icon.USER, "Имена говорящих, подсказка F8, голова игрока, кого показывает меню выбора"));

    /** Screen formats to try a layout on: the game's GUI size of that screen. */
    private record Device(String title, int w, int h) {
    }

    private static final List<Device> DEVICES = List.of(new Device("16:9 · 1920×1080", 480, 270), new Device("16:10 · 1920×1200", 480, 300),
            new Device("4:3 · 1600×1200", 400, 300), new Device("21:9 · 2560×1080", 640, 270), new Device("16:9 · мелкий интерфейс", 640, 360));

    private static boolean isWindow(String key) {
        return EngineWindows.ROLES.contains(key);
    }

    private static Scene sceneOf(String key) {
        for (Scene s : SCENES) if (s.key().equals(key)) return s;
        return SCENES.get(0);
    }

    // ================================================================== state

    /** This window's rectangle (its parts are laid out in it). */
    private int ox, oy, vw, vh;
    private final EngineConfig cfg;
    private String savedJson;
    private String scene = "hud";
    private Device device = devices().get(0);

    /** The game window as it is now (in the game's GUI units), then the usual formats. */
    private static List<Device> devices() {
        List<Device> out = new ArrayList<>();
        var win = Minecraft.getInstance().getWindow();
        int gw = Math.max(1, win.getGuiScaledWidth()), gh = Math.max(1, win.getGuiScaledHeight());
        out.add(new Device("Ваш экран · " + win.getWidth() + "×" + win.getHeight(), gw, gh));
        out.addAll(DEVICES);
        return out;
    }

    private final Map<String, GuiDoc> docs = new HashMap<>();
    private final Map<String, GuiView> views = new HashMap<>();
    private final Map<String, Deque<String>> undo = new HashMap<>(), redo = new HashMap<>();
    private final Map<String, EngineTab> pages = new HashMap<>();
    private GuiDoc.Element selected;

    private final Button applyBtn, saveBtn, texturesBtn, closeBtn, undoBtn, redoBtn;
    private final Dropdown<Device> deviceBox;
    private final Left left = new Left();
    private final Viewport viewport = new Viewport();
    private final Inspector inspector = new Inspector();
    private final ScrollView inspectorScroll;

    public EngineStudio(Project project) {
        super(project, null);
        cfg = EngineTab.load(project);
        cfg.project = project.name();
        savedJson = cfg.toJson();

        applyBtn = new Button("Сохранить и применить", this::apply).icon(Icon.UPLOAD).style(Button.Style.PRIMARY);
        applyBtn.tooltip("Ctrl+S — в проект (engine.json) и сразу в мир, всем игрокам");
        saveBtn = new Button("Сохранить", this::saveOnly).icon(Icon.SAVE).style(Button.Style.DEFAULT);
        saveBtn.tooltip("Только в проект");
        texturesBtn = new Button("Текстуры", this::exportTextures).icon(Icon.IMAGE).style(Button.Style.GHOST);
        texturesBtn.tooltip("Архив всех картинок движка по папкам и полям — рисовать свои поверх них");
        closeBtn = new Button("Стандартный вид", this::standardLook).icon(Icon.REFRESH).style(Button.Style.GHOST);
        closeBtn.tooltip("Вернуть окну сцены стандартную раскладку (Ctrl+Z — обратно)");
        undoBtn = Button.icon(Icon.UNDO, "Отменить (Ctrl+Z)", this::undoScene);
        redoBtn = Button.icon(Icon.REDO, "Повторить (Ctrl+Y)", this::redoScene);
        for (Button b : List.of(applyBtn, saveBtn, texturesBtn, closeBtn)) b.autoWidth();
        deviceBox = new Dropdown<>(EngineStudio::devices, () -> device, Device::title, d -> {
            device = d;
            relayout();
        });
        deviceBox.tooltip("Формат экрана: посмотреть раскладку на других мониторах");
        inspectorScroll = new ScrollView(inspector) {
            @Override
            protected int contentHeight() {
                return inspector.prefH;
            }
        };
        add(new Chrome());
        openScene("hud");
    }

    // ================================================================== a window of the editor

    @Override
    public String title() {
        return project.name();
    }

    @Override
    public Icon icon() {
        return Icon.GEAR;
    }

    @Override
    public int iconColor() {
        return 0xFFB18CFF;
    }

    @Override
    public boolean isDirty() {
        return dirty();
    }

    @Override
    public void save() {
        write();
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
        viewport.requestFocus();
    }

    @Override
    public void undo() {
        undoScene();
    }

    @Override
    public void redo() {
        redoScene();
    }

    private void relayout() {
        UI ui = UI.get();
        if (ui != null) ui.relayout();
    }

    private static void toastInfo(String t) {
        if (UI.get() != null) UI.get().toastInfo(t);
    }

    private static void toastSuccess(String t) {
        if (UI.get() != null) UI.get().toastSuccess(t);
    }

    private static void toastError(String t) {
        if (UI.get() != null) UI.get().toastError(t);
    }

    @Override
    public void layout() {
        ox = x;
        oy = y;
        vw = w;
        vh = h;
        for (Widget c : children()) c.bounds(x, y, w, h);
        super.layout();
    }

    // ================================================================== documents

    /** The window's layout: from the settings, else the standard one built from them (and kept in them). */
    private GuiDoc doc(String role) {
        return docs.computeIfAbsent(role, r -> {
            String json = cfg.layout(r);
            if (json == null) {
                json = EngineWindows.build(r, cfg);
                cfg.layoutJson.put(r, json);
            }
            return GuiDoc.fromJson(json);
        });
    }

    private GuiView view(String role) {
        return views.computeIfAbsent(role, r -> {
            GuiView v = new GuiView(doc(r), project.name());
            v.interactive = false;
            v.playAppear = false;
            v.drawBackground = false;
            return v;
        });
    }

    /** A change of the scene's window: kept in the settings, laid out again. */
    private void changed(String role) {
        cfg.layoutJson.put(role, doc(role).toJson());
        view(role).layoutTree();
        inspector.refreshValues();
    }

    private void snapshot(String role) {
        undo.computeIfAbsent(role, k -> new ArrayDeque<>()).push(doc(role).toJson());
        if (undo.get(role).size() > 200) undo.get(role).removeLast();
        redo.computeIfAbsent(role, k -> new ArrayDeque<>()).clear();
    }

    private void restore(String role, String json) {
        String id = selected != null ? selected.id : null;
        GuiDoc d = GuiDoc.fromJson(json);
        docs.put(role, d);
        views.remove(role);
        cfg.layoutJson.put(role, json);
        selected = id != null ? d.element(id) : null;
        inspector.rebuild();
    }

    private void undoScene() {
        if (!isWindow(scene)) return;
        Deque<String> u = undo.get(scene);
        if (u == null || u.isEmpty()) return;
        redo.computeIfAbsent(scene, k -> new ArrayDeque<>()).push(doc(scene).toJson());
        restore(scene, u.pop());
    }

    private void redoScene() {
        if (!isWindow(scene)) return;
        Deque<String> r = redo.get(scene);
        if (r == null || r.isEmpty()) return;
        undo.computeIfAbsent(scene, k -> new ArrayDeque<>()).push(doc(scene).toJson());
        restore(scene, r.pop());
    }

    private void openScene(String key) {
        scene = key;
        selected = null;
        if (!isWindow(key)) pages.computeIfAbsent(key, k -> new EngineTab(project, cfg, k));
        inspector.rebuild();
        relayout();
    }

    // ================================================================== saving

    private boolean dirty() {
        return !cfg.toJson().equals(savedJson);
    }

    private boolean write() {
        cfg.project = project.name();
        // only the engine's windows have layouts
        cfg.layoutJson.keySet().removeIf(k -> !isWindow(k));
        String json = cfg.toJson();
        try {
            Files.writeString(EngineTab.file(project), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            toastError("Не удалось сохранить: " + e.getMessage());
            return false;
        }
        savedJson = json;
        return true;
    }

    private void saveOnly() {
        if (write()) toastSuccess("Сохранено в проект");
    }

    private void apply() {
        if (!write()) return;
        if (Minecraft.getInstance().getConnection() == null) {
            toastInfo("Сохранено. В мир применится, когда вы в него войдёте");
            return;
        }
        ClientState.get().engine(savedJson);
        toastSuccess("Применено в мире");
    }

    private void exportTextures() {
        try {
            Path zip = TextureExport.export(project.dir);
            net.minecraft.Util.getPlatform().openFile(project.dir.toFile());
            toastSuccess("Сохранено: " + zip.getFileName());
        } catch (Exception e) {
            toastError("Не удалось сохранить архив: " + e.getMessage());
        }
    }

    /** The scene's window as the engine draws it with the standard settings. */
    private void standardLook() {
        if (!isWindow(scene)) return;
        snapshot(scene);
        restore(scene, EngineWindows.build(scene, EngineConfig.fromJson("{}")));
        toastInfo("Стандартный вид. Ctrl+Z — вернуть как было");
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0, shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (selected != null) {
                select(null);
                return true;
            }
            return false;
        }
        if (ctrl && key == GLFW.GLFW_KEY_S) {
            apply();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            if (shift) redoScene();
            else undoScene();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Y) {
            redoScene();
            return true;
        }
        if (!isWindow(scene) || selected == null || !viewport.isFocused()) return false;
        if (key == GLFW.GLFW_KEY_DELETE) {
            deleteSelected();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_D) {
            duplicateSelected();
            return true;
        }
        int step = shift ? 10 : 1;
        int dx = key == GLFW.GLFW_KEY_LEFT ? -step : key == GLFW.GLFW_KEY_RIGHT ? step : 0;
        int dy = key == GLFW.GLFW_KEY_UP ? -step : key == GLFW.GLFW_KEY_DOWN ? step : 0;
        if (dx != 0 || dy != 0) {
            snapshot(scene);
            selected.set("x", String.valueOf(Math.round(GuiView.dim(selected.get("x"), 0)) + dx));
            selected.set("y", String.valueOf(Math.round(GuiView.dim(selected.get("y"), 0)) + dy));
            changed(scene);
            return true;
        }
        return false;
    }

    // ================================================================== elements

    private void select(GuiDoc.Element e) {
        selected = e;
        inspector.rebuild();
    }

    private static String freshId(GuiDoc d, String base) {
        String b = base.replace("hud_", "");
        if (d.element(b) == null) return b;
        for (int i = 2; ; i++) if (d.element(b + i) == null) return b + i;
    }

    private void addElement(String type) {
        GuiDoc d = doc(scene);
        snapshot(scene);
        GuiDoc.Element e = new GuiDoc.Element();
        e.type = type;
        e.id = freshId(d, type);
        // inside the picked group, else on the screen
        if (selected != null && selected.type.equals("group")) selected.children.add(e);
        else d.elements.add(e);
        changed(scene);
        select(e);
    }

    private static List<GuiDoc.Element> parentList(GuiDoc d, GuiDoc.Element e) {
        if (d.elements.contains(e)) return d.elements;
        for (GuiDoc.Element p : d.all()) if (p.children.contains(e)) return p.children;
        return d.elements;
    }

    private void deleteSelected() {
        if (selected == null) return;
        snapshot(scene);
        parentList(doc(scene), selected).remove(selected);
        selected = null;
        changed(scene);
        inspector.rebuild();
    }

    private void duplicateSelected() {
        if (selected == null) return;
        GuiDoc d = doc(scene);
        snapshot(scene);
        GuiDoc.Element c = selected.copy();
        c.id = freshId(d, selected.id);
        c.set("x", String.valueOf(Math.round(GuiView.dim(c.get("x"), 0)) + 8));
        c.set("y", String.valueOf(Math.round(GuiView.dim(c.get("y"), 0)) + 8));
        List<GuiDoc.Element> l = parentList(d, selected);
        l.add(l.indexOf(selected) + 1, c);
        // children ids stay unique in the window
        for (GuiDoc.Element ch : new ArrayList<>(d.all())) {
            if (ch == c || !c.children.contains(ch)) continue;
            ch.id = freshId(d, ch.id);
        }
        changed(scene);
        select(c);
    }

    private void move(int dir) {
        if (selected == null) return;
        List<GuiDoc.Element> l = parentList(doc(scene), selected);
        int i = l.indexOf(selected), j = i + dir;
        if (j < 0 || j >= l.size()) return;
        snapshot(scene);
        l.remove(i);
        l.add(j, selected);
        changed(scene);
    }

    private static String typeTitle(String type) {
        GuiSchema.Type t = GuiSchema.type(type);
        return t != null ? t.title() : type;
    }

    private void addMenu(int mx, int my) {
        PopupMenu pm = new PopupMenu();
        for (GuiSchema.Type t : GuiSchema.windowTypes(scene)) pm.item(t.title(), () -> addElement(t.key()));
        pm.show(mx, my);
    }

    // ================================================================== the frame of the window

    private final class Chrome extends Widget {
        Chrome() {
            for (Widget b : List.of(applyBtn, saveBtn, texturesBtn, closeBtn, undoBtn, redoBtn, deviceBox)) add(b);
            add(left);
            add(viewport);
            add(inspectorScroll);
        }

        private Widget body() {
            return isWindow(scene) ? null : pages.get(scene);
        }

        @Override
        public void layout() {
            int bx = ox + vw - 12;
            for (Button b : List.of(closeBtn, applyBtn, saveBtn, texturesBtn)) {
                bx -= b.prefW;
                b.bounds(bx, oy + (TOP - 24) / 2, b.prefW, 24);
                bx -= 6;
            }
            boolean win = isWindow(scene);
            int cx = ox + LEFT + 12;
            closeBtn.visible = win;
            undoBtn.bounds(win ? cx : -10000, oy + (TOP - 22) / 2, 22, 22);
            redoBtn.bounds(win ? cx + 24 : -10000, oy + (TOP - 22) / 2, 22, 22);
            deviceBox.bounds(win ? cx + 56 : -10000, oy + (TOP - 20) / 2, 180, 20);
            left.bounds(ox, oy + TOP, LEFT, vh - TOP - STATUS);
            Widget body = body();
            for (Widget c : new ArrayList<>(children())) if (c instanceof EngineTab && c != body) remove(c);
            if (body != null) {
                if (body.parent() != this) add(body);
                body.bounds(ox + LEFT + 1, oy + TOP + 1, vw - LEFT - 1, vh - TOP - STATUS - 1);
                viewport.bounds(-10000, 0, 10, 10);
                inspectorScroll.bounds(-10000, 0, 10, 10);
            } else {
                viewport.bounds(ox + LEFT + 1, oy + TOP + 1, vw - LEFT - RIGHT - 2, vh - TOP - STATUS - 1);
                inspectorScroll.bounds(ox + vw - RIGHT, oy + TOP + 1, RIGHT, vh - TOP - STATUS - 1);
            }
            super.layout();
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            int X = ox, Y = oy;
            c.fill(X, Y, vw, vh, Theme.BG);
            c.fill(X, Y, vw, TOP, Theme.PANEL);
            c.fill(X, Y + TOP, vw, 1, Theme.BORDER);
            c.text(sceneOf(scene).title(), X + 14, Y + 12, Theme.TEXT);
            c.text(sceneOf(scene).doc(), X + 14, Y + 25, Theme.TEXT_MUTED);
            if (isWindow(scene)) {
                c.fill(X + LEFT + 1, Y + 8, 1, TOP - 16, Theme.BORDER);
                c.fill(X + vw - RIGHT - 1, Y + TOP, 1, vh - TOP - STATUS, Theme.BORDER);
            }
            int sy = Y + vh - STATUS;
            c.fill(X, sy, vw, STATUS, Theme.PANEL);
            c.fill(X, sy, vw, 1, Theme.BORDER);
            boolean d = dirty();
            c.round(X + 12, sy + 9, 7, 7, 3, d ? Theme.WARNING : Theme.SUCCESS);
            c.text(d ? "Есть несохранённые изменения · Ctrl+S — сохранить и применить" : "Всё сохранено", X + 26, sy + 8, Theme.TEXT_DIM);
            if (isWindow(scene)) {
                String hint = "Тащите части · за углы — размер · Alt — без прилипания · стрелки — сдвиг (Shift ×10) · Del · Ctrl+D · Ctrl+Z · ПКМ — меню";
                c.textEllipsized(hint, X + vw / 2, sy + 8, vw / 2 - 12, Theme.TEXT_MUTED);
            }
        }
    }

    // ================================================================== left: scenes and the scene's parts

    private final class Left extends Widget implements UI.CursorProvider {
        private final List<int[]> sceneRows = new ArrayList<>();
        private final List<String> sceneKeys = new ArrayList<>();
        private final List<int[]> partRows = new ArrayList<>();
        private final List<GuiDoc.Element> partEls = new ArrayList<>();
        private int addY = -1;

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.fill(x, y, w, h, Theme.PANEL_ALT);
            c.fill(x + w - 1, y, 1, h, Theme.BORDER);
            sceneRows.clear();
            sceneKeys.clear();
            partRows.clear();
            partEls.clear();
            addY = -1;
            int yy = y + 10;
            String group = "";
            for (Scene s : SCENES) {
                if (!s.group().equals(group)) {
                    group = s.group();
                    if (yy > y + 10) yy += 4;
                    c.text(group, x + 14, yy, Theme.TEXT_MUTED);
                    yy += 13;
                }
                boolean sel = s.key().equals(scene), hov = mx >= x && mx < x + w && my >= yy && my < yy + 22;
                if (sel) {
                    c.round(x + 6, yy, w - 12, 22, 4, Theme.SELECTION);
                    c.fill(x + 6, yy + 5, 2, 12, Theme.ACCENT);
                } else if (hov) c.round(x + 6, yy, w - 12, 22, 4, Theme.HOVER);
                c.icon(s.icon(), x + 16, yy + 7, sel ? Theme.ACCENT : Theme.TEXT_DIM);
                c.text(s.title(), x + 32, yy + 7, sel ? Theme.TEXT : Theme.TEXT_DIM);
                sceneRows.add(new int[]{yy, 22});
                sceneKeys.add(s.key());
                yy += 24;
            }
            if (!isWindow(scene)) return;
            yy += 8;
            c.fill(x + 10, yy, w - 20, 1, Theme.BORDER);
            yy += 10;
            c.text("ЧАСТИ ОКНА", x + 14, yy + 2, Theme.TEXT_MUTED);
            addY = yy - 3;
            boolean hovAdd = mx >= x + w - 88 && mx < x + w - 10 && my >= addY && my < addY + 16;
            c.round(x + w - 88, addY, 78, 16, 3, hovAdd ? Theme.ACCENT_HOVER : Theme.ACCENT);
            c.icon(Icon.PLUS, x + w - 83, addY + 4, Theme.TEXT_ON_ACCENT);
            c.text("Добавить", x + w - 71, addY + 4, Theme.TEXT_ON_ACCENT);
            yy += 18;
            c.pushClip(x, yy, w, Math.max(0, y + h - yy));
            int end = parts(c, doc(scene).elements, 0, yy, mx, my);
            c.popClip();
            if (doc(scene).elements.isEmpty()) c.text("Пусто — «Добавить»", x + 16, end + 4, Theme.TEXT_MUTED);
        }

        /** Front-most first, like the layers of a drawing program. */
        private int parts(Canvas c, List<GuiDoc.Element> list, int depth, int yy, int mx, int my) {
            for (int i = list.size() - 1; i >= 0; i--) {
                GuiDoc.Element e = list.get(i);
                boolean sel = e == selected, hov = mx >= x && mx < x + w && my >= yy && my < yy + 18;
                if (sel) c.round(x + 6, yy, w - 12, 18, 3, Theme.SELECTION);
                else if (hov) c.round(x + 6, yy, w - 12, 18, 3, Theme.HOVER);
                int ix = x + 14 + depth * 12;
                boolean vis = e.b("visible");
                c.text(e.id, ix, yy + 5, vis ? (sel ? Theme.TEXT : Theme.TEXT_DIM) : Theme.TEXT_MUTED);
                int tx = ix + c.width(e.id) + 6;
                c.textEllipsized(typeTitle(e.type), tx, yy + 5, Math.max(10, x + w - 30 - tx), Theme.TEXT_MUTED);
                c.icon(vis ? Icon.CHECK : Icon.CLOSE, x + w - 22, yy + 5, vis ? Theme.TEXT_DIM : Theme.TEXT_MUTED);
                partRows.add(new int[]{yy, 18});
                partEls.add(e);
                yy += 19;
                if (!e.children.isEmpty()) yy = parts(c, e.children, depth + 1, yy, mx, my);
            }
            return yy;
        }

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            for (int i = 0; i < sceneRows.size(); i++) {
                int[] r = sceneRows.get(i);
                if (my >= r[0] && my < r[0] + r[1]) {
                    openScene(sceneKeys.get(i));
                    return true;
                }
            }
            if (isWindow(scene) && addY >= 0 && mx >= x + w - 88 && mx < x + w - 10 && my >= addY && my < addY + 16) {
                addMenu((int) mx, (int) my + 12);
                return true;
            }
            for (int i = 0; i < partRows.size(); i++) {
                int[] r = partRows.get(i);
                if (my >= r[0] && my < r[0] + r[1]) {
                    GuiDoc.Element e = partEls.get(i);
                    if (mx >= x + w - 28) {
                        // the mark at the right: shown or hidden
                        snapshot(scene);
                        e.set("visible", String.valueOf(!e.b("visible")));
                        changed(scene);
                    } else select(e);
                    return true;
                }
            }
            return true;
        }

        @Override
        public int cursor(int mx, int my) {
            return UI.CURSOR_HAND;
        }
    }

    // ================================================================== the game's screen

    private final class Viewport extends Widget implements UI.CursorProvider {
        /** The game's screen on this widget: left, top, scale. */
        private float gx, gy, k = 1;
        private int dragMode; // 0 none, 1 move, 2..9 handles
        private double pressX, pressY;
        private int[] startRect;
        private String before;
        private boolean moved;
        private final List<float[]> guides = new ArrayList<>();

        Viewport() {
            focusable = true;
        }

        private void place() {
            int pad = 24;
            k = Math.max(0.2f, Math.min((w - pad * 2) / (float) device.w(), (h - pad * 2 - 18) / (float) device.h()));
            gx = x + (w - device.w() * k) / 2f;
            gy = y + 18 + (h - 18 - device.h() * k) / 2f;
        }

        /** A rectangle in a window's design units → on this widget. */
        private float[] onScreen(GuiView v, int[] r) {
            float sx = gx + (v.areaX() + r[0] * v.areaScale()) * k, sy = gy + (v.areaY() + r[1] * v.areaScale()) * k;
            return new float[]{sx, sy, r[2] * v.areaScale() * k, r[3] * v.areaScale() * k};
        }

        private void drawView(Canvas c, GuiView v, float opacity) {
            v.opacity = opacity;
            v.bounds(0, 0, device.w(), device.h());
            v.layout();
            c.pushTransform(gx, gy, k);
            try {
                v.render(c, -10000, -10000, 0.05f);
            } catch (RuntimeException ignored) {
            }
            c.popTransform();
        }

        private boolean owns(GuiDoc.Element e) {
            return e != null && doc(scene).all().contains(e);
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            if (!isWindow(scene)) return;
            place();
            c.fill(x, y, w, h, Theme.BG_DEEP);
            int gw = Math.round(device.w() * k), gh = Math.round(device.h() * k);
            int ix = Math.round(gx), iy = Math.round(gy);
            c.text(device.title() + "  ·  экран интерфейса " + device.w() + "×" + device.h(), ix, iy - 14, Theme.TEXT_MUTED);
            c.shadow(ix, iy, gw, gh, 4, 10);
            c.pushClip(ix, iy, gw, gh);
            GameShot.draw(c, ix, iy, gw, gh);
            // the scene: the HUD stays under the windows that come over the game (the journal and the shop hide it)
            boolean screen = scene.equals("quest") || scene.equals("shop");
            if (!scene.equals("chatlog") && !screen) drawView(c, view("hud"), scene.equals("hud") ? 1f : 0.5f);
            if (scene.equals("chatlog")) c.fill(ix, iy, gw, gh, 0x99000000);
            if (screen) {
                c.fill(ix, iy, gw, gh, GuiDoc.toColor(doc(scene).get("background")));
                if (scene.equals("quest")) org.sprautestudio.spraute_engine.quest.client.JournalParts.vars(view(scene));
                else org.sprautestudio.spraute_engine.shop.client.ShopParts.vars(view(scene));
            }
            if (scene.equals("choice")) c.fill(ix, iy, gw, gh, 0x33000000);
            if (!scene.equals("hud")) drawView(c, view(scene), 1f);
            c.flush();
            c.popClip();
            c.outline(ix - 1, iy - 1, gw + 2, gh + 2, Theme.BORDER_STRONG);

            GuiView v = view(scene);
            GuiDoc.Element hov = dragMode == 0 ? at(mx, my) : null;
            if (hov != null && hov != selected) {
                float[] r = onScreen(v, v.rect(hov));
                c.outline(Math.round(r[0]), Math.round(r[1]), Math.round(r[2]), Math.round(r[3]), 0x88FFFFFF);
            }
            for (float[] gl : guides) c.line(gl[0], gl[1], gl[2], gl[3], 1, 0xFFFF4FD8);
            if (owns(selected)) {
                float[] r = onScreen(v, v.rect(selected));
                c.outline(Math.round(r[0]), Math.round(r[1]), Math.round(r[2]), Math.round(r[3]), Theme.ACCENT);
                for (float[] hd : handles(r)) {
                    c.fill(Math.round(hd[0]) - 3, Math.round(hd[1]) - 3, 7, 7, 0xFF000000);
                    c.fill(Math.round(hd[0]) - 2, Math.round(hd[1]) - 2, 5, 5, Theme.ACCENT);
                }
                // the anchor: its point on the screen (or in the group) and the line to the part's matching point
                float[] ar = v.areaOf(selected);
                String a = selected.get("anchor");
                float fx = a.endsWith("left") ? 0 : a.endsWith("right") ? 1 : 0.5f, fy = a.startsWith("top") ? 0 : a.startsWith("bottom") ? 1 : 0.5f;
                float ax = gx + (v.areaX() + (ar[0] + fx * ar[2]) * v.areaScale()) * k, ay = gy + (v.areaY() + (ar[1] + fy * ar[3]) * v.areaScale()) * k;
                float px = r[0] + fx * r[2], py = r[1] + fy * r[3];
                c.line(ax, ay, px, py, 1, 0xCC6EC3FF);
                c.round(Math.round(ax) - 4, Math.round(ay) - 4, 8, 8, 4, 0xFF6EC3FF);
                int[] dr = v.rect(selected);
                String lab = selected.id + "  " + dr[2] + "×" + dr[3];
                int lw = c.width(lab) + 8;
                int ly = Math.round(r[1]) - 13 < iy ? Math.round(r[1] + r[3]) + 2 : Math.round(r[1]) - 13;
                c.round(Math.round(r[0]), ly, lw, 12, 2, Theme.ACCENT);
                c.text(lab, Math.round(r[0]) + 4, ly + 2, Theme.TEXT_ON_ACCENT);
            }
        }

        private float[][] handles(float[] r) {
            float x0 = r[0], y0 = r[1], x1 = r[0] + r[2], y1 = r[1] + r[3], xm = (x0 + x1) / 2, ym = (y0 + y1) / 2;
            return new float[][]{{x0, y0}, {xm, y0}, {x1, y0}, {x1, ym}, {x1, y1}, {xm, y1}, {x0, y1}, {x0, ym}};
        }

        /** The front-most part under a point of this widget. */
        private GuiDoc.Element at(double mx, double my) {
            GuiView v = view(scene);
            List<GuiDoc.Element> all = doc(scene).all();
            for (int i = all.size() - 1; i >= 0; i--) {
                GuiDoc.Element e = all.get(i);
                if (e.locked || !e.b("visible")) continue;
                float[] r = onScreen(v, v.rect(e));
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) return e;
            }
            return null;
        }

        private int handleAt(double mx, double my) {
            if (!owns(selected)) return -1;
            GuiView v = view(scene);
            float[][] hs = handles(onScreen(v, v.rect(selected)));
            for (int i = 0; i < hs.length; i++) if (Math.abs(mx - hs[i][0]) <= 5 && Math.abs(my - hs[i][1]) <= 5) return i;
            return -1;
        }

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            requestFocus();
            guides.clear();
            if (button != 0) {
                GuiDoc.Element e = at(mx, my);
                if (e != null) select(e);
                PopupMenu pm = new PopupMenu();
                if (selected != null) {
                    pm.item("Дублировать", Icon.COPY, "Ctrl+D", EngineStudio.this::duplicateSelected);
                    pm.item("На передний план", Icon.CHEVRON_UP, null, () -> move(1));
                    pm.item("На задний план", Icon.CHEVRON_DOWN, null, () -> move(-1));
                    pm.item("Удалить", Icon.TRASH, "Del", EngineStudio.this::deleteSelected);
                    pm.separator();
                }
                for (GuiSchema.Type t : GuiSchema.windowTypes(scene)) pm.item("Добавить: " + t.title(), () -> addElement(t.key()));
                pm.show((int) mx, (int) my);
                return true;
            }
            dragMode = 0;
            int hi = handleAt(mx, my);
            if (hi >= 0) dragMode = 2 + hi;
            else {
                GuiDoc.Element e = at(mx, my);
                if (e != selected) select(e);
                if (e != null) dragMode = 1;
            }
            if (dragMode != 0) {
                pressX = mx;
                pressY = my;
                startRect = view(scene).rect(selected).clone();
                before = doc(scene).toJson();
                moved = false;
            }
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double ddx, double ddy) {
            if (dragMode == 0 || selected == null || startRect == null) return true;
            GuiView v = view(scene);
            float unit = Math.max(0.01f, v.areaScale() * k);
            if (!moved && Math.abs(mx - pressX) + Math.abs(my - pressY) < 2) return true;
            moved = true;
            int dx = (int) Math.round((mx - pressX) / unit), dy = (int) Math.round((my - pressY) / unit);
            int rx = startRect[0], ry = startRect[1], rw = startRect[2], rh = startRect[3];
            guides.clear();
            if (dragMode == 1) {
                rx += dx;
                ry += dy;
                if (!Screen.hasAltDown()) {
                    int[] s = snapMove(v, rx, ry, rw, rh);
                    rx = s[0];
                    ry = s[1];
                }
            } else {
                int hi = dragMode - 2;
                boolean left = hi == 0 || hi == 6 || hi == 7, right = hi == 2 || hi == 3 || hi == 4;
                boolean top = hi == 0 || hi == 1 || hi == 2, bottom = hi == 4 || hi == 5 || hi == 6;
                if (left) {
                    rx = Math.min(startRect[0] + startRect[2] - 2, startRect[0] + dx);
                    rw = startRect[0] + startRect[2] - rx;
                }
                if (right) rw = Math.max(2, startRect[2] + dx);
                if (top) {
                    ry = Math.min(startRect[1] + startRect[3] - 2, startRect[1] + dy);
                    rh = startRect[1] + startRect[3] - ry;
                }
                if (bottom) rh = Math.max(2, startRect[3] + dy);
                String st = selected.get("stretch");
                boolean sw = st.equals("width") || st.equals("both"), sh = st.equals("height") || st.equals("both");
                if (sw && (left || right)) sw = false;
                if (sh && (top || bottom)) sh = false;
                selected.set("stretch", sw && sh ? "both" : sw ? "width" : sh ? "height" : "none");
            }
            selected.set("w", String.valueOf(rw));
            selected.set("h", String.valueOf(rh));
            v.layoutTree();
            int[] off = v.offsetFor(selected, rx, ry, rw, rh);
            selected.set("x", String.valueOf(off[0]));
            selected.set("y", String.valueOf(off[1]));
            v.layoutTree();
            cfg.layoutJson.put(scene, doc(scene).toJson());
            inspector.refreshValues();
            return true;
        }

        /** Sticks the moved part to its area's centre and edges and to the other parts' edges and centres. */
        private int[] snapMove(GuiView v, int rx, int ry, int rw, int rh) {
            float[] area = v.areaOf(selected);
            List<Float> xs = new ArrayList<>(List.of(area[0], area[0] + area[2] / 2, area[0] + area[2]));
            List<Float> ys = new ArrayList<>(List.of(area[1], area[1] + area[3] / 2, area[1] + area[3]));
            for (GuiDoc.Element o : doc(scene).all()) {
                if (o == selected || !o.b("visible") || selected.children.contains(o)) continue;
                int[] r = v.rect(o);
                xs.add((float) r[0]);
                xs.add(r[0] + r[2] / 2f);
                xs.add((float) r[0] + r[2]);
                ys.add((float) r[1]);
                ys.add(r[1] + r[3] / 2f);
                ys.add((float) r[1] + r[3]);
            }
            float th = 5 / Math.max(0.01f, v.areaScale() * k);
            float bestX = Float.MAX_VALUE, lineX = 0;
            for (float m : new float[]{rx, rx + rw / 2f, rx + rw})
                for (float t : xs)
                    if (Math.abs(t - m) < Math.abs(bestX)) {
                        bestX = t - m;
                        lineX = t;
                    }
            float bestY = Float.MAX_VALUE, lineY = 0;
            for (float m : new float[]{ry, ry + rh / 2f, ry + rh})
                for (float t : ys)
                    if (Math.abs(t - m) < Math.abs(bestY)) {
                        bestY = t - m;
                        lineY = t;
                    }
            if (Math.abs(bestX) <= th) {
                rx += Math.round(bestX);
                float sx = gx + (v.areaX() + lineX * v.areaScale()) * k;
                guides.add(new float[]{sx, gy, sx, gy + device.h() * k});
            }
            if (Math.abs(bestY) <= th) {
                ry += Math.round(bestY);
                float sy = gy + (v.areaY() + lineY * v.areaScale()) * k;
                guides.add(new float[]{gx, sy, gx + device.w() * k, sy});
            }
            return new int[]{rx, ry};
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            if (dragMode >= 2 && moved && selected != null && doc(scene).elements.contains(selected)) {
                // pulled from edge to edge of the layout: on any screen it goes to the screen's edges
                GuiView v = view(scene);
                int[] rr = v.rect(selected);
                float[] ar = v.areaOf(selected);
                String st = selected.get("stretch");
                boolean sw = st.equals("width") || st.equals("both"), sh = st.equals("height") || st.equals("both");
                if (rr[0] <= ar[0] + 1 && rr[0] + rr[2] >= ar[0] + ar[2] - 1) sw = true;
                if (rr[1] <= ar[1] + 1 && rr[1] + rr[3] >= ar[1] + ar[3] - 1) sh = true;
                String ns = sw && sh ? "both" : sw ? "width" : sh ? "height" : "none";
                if (!ns.equals(st)) {
                    selected.set("stretch", ns);
                    v.layoutTree();
                    toastInfo(sw && sh ? "Растянуто на весь экран" : sw ? "Растянуто на всю ширину экрана" : "Растянуто на всю высоту экрана");
                }
            }
            if (dragMode != 0 && moved && before != null) {
                undo.computeIfAbsent(scene, kk -> new ArrayDeque<>()).push(before);
                redo.computeIfAbsent(scene, kk -> new ArrayDeque<>()).clear();
                changed(scene);
            }
            dragMode = 0;
            before = null;
            guides.clear();
            return true;
        }

        @Override
        public int cursor(int mx, int my) {
            int hi = handleAt(mx, my);
            if (hi >= 0) return hi == 1 || hi == 5 ? UI.CURSOR_VRESIZE : UI.CURSOR_HRESIZE;
            return at(mx, my) != null ? UI.CURSOR_MOVE : UI.CURSOR_ARROW;
        }
    }

    // ================================================================== right: the inspector

    /** One line of the inspector: a label on the left and its editor, or a whole-width widget. */
    private static final class Row extends Widget {
        final String label;
        final Widget editor;
        final boolean wide;

        Row(String label, Widget editor, int height, boolean wide) {
            this.label = label;
            this.editor = editor;
            this.wide = wide;
            prefH = height;
            if (editor != null) add(editor);
        }

        @Override
        public void layout() {
            if (editor != null) {
                if (wide) editor.bounds(x + 12, y + 1, w - 24, h - 2);
                else editor.bounds(x + 108, y + 1, w - 120, 20);
            }
            super.layout();
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            if (label != null && !wide) c.textEllipsized(label, x + 12, y + 7, 92, Theme.TEXT_DIM);
        }
    }

    private final class Inspector extends Widget {
        private final List<Runnable> refreshers = new ArrayList<>();

        void refreshValues() {
            for (Runnable r : refreshers) r.run();
        }

        void rebuild() {
            clear();
            refreshers.clear();
            if (isWindow(scene)) {
                if (selected != null && doc(scene).all().contains(selected)) element(selected);
                else window();
            }
            int hgt = 8;
            for (Widget c : children()) hgt += c.prefH + 2;
            prefH = hgt;
            relayout();
        }

        @Override
        public void layout() {
            int yy = y + 8;
            for (Widget c : children()) {
                c.bounds(x, yy, w, c.prefH);
                yy += c.prefH + 2;
            }
            super.layout();
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.fill(x, y, w, Math.max(h, prefH), Theme.PANEL);
        }

        // ---------------------------------------------------------- rows

        private void header(String text) {
            String t = text.toUpperCase();
            add(new Widget() {
                {
                    prefH = 22;
                }

                @Override
                protected void paint(Canvas c, int mx, int my, float dt) {
                    c.text(t, x + 12, y + 9, Theme.ACCENT);
                    int tw = c.width(t);
                    c.fill(x + 18 + tw, y + 13, Math.max(0, w - 30 - tw), 1, Theme.BORDER);
                }
            });
        }

        private void note(String text) {
            int lines = Math.max(1, (int) Math.ceil(Minecraft.getInstance().font.width(text) / (double) (RIGHT - 34)) + 1);
            add(new Widget() {
                {
                    prefH = lines * 10 + 4;
                }

                @Override
                protected void paint(Canvas c, int mx, int my, float dt) {
                    c.wrapped(text, x + 12, y + 2, w - 24, Theme.TEXT_MUTED, 1);
                }
            });
        }

        private void row(String label, Widget editor) {
            add(new Row(label, editor, 22, false));
        }

        private void text(String label, Supplier<String> get, Consumer<String> set) {
            TextField f = new TextField();
            f.setText(get.get());
            f.onChange = set::accept;
            refreshers.add(() -> {
                if (!f.text().equals(get.get()) && UI.get() != null && !isFocusedField(f)) f.setText(get.get());
            });
            row(label, f);
        }

        private void number(String label, Supplier<String> get, Consumer<String> set) {
            TextField f = new TextField();
            f.setText(get.get());
            f.filter = ch -> Character.isDigit(ch) || ch == '-' || ch == '.' || ch == '%';
            f.onChange = set::accept;
            refreshers.add(() -> {
                if (!f.text().equals(get.get()) && !isFocusedField(f)) f.setText(get.get());
            });
            row(label, f);
        }

        private boolean isFocusedField(Widget f) {
            return f.isFocused();
        }

        private void check(String label, Supplier<Boolean> get, Consumer<Boolean> set) {
            row(label, new Checkbox("", get::get, set));
        }

        private <T> void choose(String label, Supplier<List<T>> options, Supplier<T> get, java.util.function.Function<T, String> title, Consumer<T> set) {
            row(label, new Dropdown<>(options, get, title, set));
        }

        private void color(String label, Supplier<Integer> get, Consumer<Integer> set) {
            row(label, new Widget() {
                @Override
                protected void paint(Canvas c, int mx, int my, float dt) {
                    int v = get.get();
                    c.roundBorder(x, y, 30, h, 3, 0xFF000000 | v, Theme.BORDER_STRONG);
                    c.text(String.format("#%08X", v), x + 36, y + 6, Theme.TEXT_DIM);
                }

                @Override
                public boolean mousePressed(double mx, double my, int button) {
                    ColorPicker.open(get.get(), x, y + h + 2, set::accept);
                    return true;
                }
            });
        }

        private void button(String text, Icon icon, Runnable action) {
            Button b = new Button(text, action).icon(icon).style(Button.Style.DEFAULT);
            add(new Row(null, b, 24, true));
        }

        // ---------------------------------------------------------- a part of the window

        private void prop(GuiDoc.Element e, GuiSchema.Prop p) {
            Supplier<String> get = () -> e.get(p.key());
            Consumer<String> set = v -> {
                if (v.equals(e.get(p.key()))) return;
                snapshot(scene);
                e.set(p.key(), v);
                changed(scene);
            };
            String label = p.label();
            switch (p.kind()) {
                case BOOL -> check(label, () -> GuiDoc.toBool(get.get()), b -> set.accept(String.valueOf(b)));
                case COLOR -> color(label, () -> GuiDoc.toColor(get.get()), c -> set.accept(String.format("#%08X", c)));
                case CHOICE -> choose(label, p::choices, get, v -> v, set);
                case INT, NUMBER -> number(label, get, set);
                case IMAGE -> choose(label, () -> {
                    List<String> out = new ArrayList<>(List.of(""));
                    for (Project.ImageRef img : project.images()) out.add(img.name());
                    String cur = get.get();
                    if (!out.contains(cur)) out.add(cur);
                    return out;
                }, get, v -> v.isEmpty() ? "— нет —" : v, set);
                default -> text(label, get, set);
            }
        }

        private void element(GuiDoc.Element e) {
            header(typeTitle(e.type));
            text("id", () -> e.id, v -> {
                String n = v.trim();
                if (n.isEmpty() || n.equals(e.id) || doc(scene).element(n) != null) return;
                snapshot(scene);
                e.id = n;
                changed(scene);
            });
            GuiSchema.Type t = GuiSchema.type(e.type);
            if (t != null && !t.doc().isEmpty()) note(t.doc());
            header("Привязка и место");
            add(new Row(null, new AnchorPicker(e), 48, true));
            button("На всю ширину экрана", Icon.ARROW_RIGHT, () -> stretch(e, "width"));
            button("На всю высоту экрана", Icon.CHEVRON_DOWN, () -> stretch(e, "height"));
            button("На весь экран", Icon.CUBE, () -> stretch(e, "both"));
            if (!e.get("stretch").equals("none")) button("Не растягивать", Icon.CLOSE, () -> stretch(e, "none"));
            for (String k : List.of("x", "y", "w", "h")) {
                GuiSchema.Prop p = e.schema().prop(k);
                if (p != null) prop(e, p);
            }
            String group = "";
            for (GuiSchema.Prop p : e.schema().props()) {
                if (List.of("anchor", "x", "y", "w", "h").contains(p.key())) continue;
                if (!p.group().equals(group)) {
                    group = p.group();
                    if (!group.equals("Положение")) header(group);
                }
                prop(e, p);
            }
            header("Действия");
            button("Дублировать  (Ctrl+D)", Icon.COPY, EngineStudio.this::duplicateSelected);
            button("На передний план", Icon.CHEVRON_UP, () -> move(1));
            button("На задний план", Icon.CHEVRON_DOWN, () -> move(-1));
            button("Удалить  (Del)", Icon.TRASH, EngineStudio.this::deleteSelected);
        }

        // ---------------------------------------------------------- the window itself

        private void window() {
            header(EngineConfig.windowTitle(scene));
            note(EngineWindows.help(scene));
            GuiDoc d = doc(scene);
            header("Окно");
            choose("масштаб", () -> List.of("screen", "fit"), () -> d.get("layout"),
                    v -> v.equals("screen") ? "к краям экрана" : "вписать целиком", v -> {
                        snapshot(scene);
                        d.set("layout", v);
                        changed(scene);
                    });
            number("ширина макета", () -> d.get("width"), v -> {
                d.set("width", v);
                changed(scene);
            });
            number("высота макета", () -> d.get("height"), v -> {
                d.set("height", v);
                changed(scene);
            });
            switch (scene) {
                case "choice" -> {
                    header("Поведение");
                    check("камера к говорящему", () -> cfg.choice.camera, v -> cfg.choice.camera = v);
                    choose("«Говорящий»", () -> List.of("speaker", "player", "mob", "none"), () -> cfg.choice.show,
                            v -> switch (v) {
                                case "speaker" -> "кто спрашивает";
                                case "player" -> "игрок";
                                case "mob" -> "моб";
                                default -> "никто";
                            }, v -> {
                                cfg.choice.show = v;
                                rebuild();
                            });
                    if (cfg.choice.show.equals("mob")) text("моб", () -> cfg.choice.mob, v -> cfg.choice.mob = v.trim());
                }
                case "toast" -> {
                    EngineConfig.Quests.Toast t = cfg.quests.toast;
                    header("Когда показывать");
                    check("показывать", () -> t.enabled, v -> t.enabled = v);
                    check("новый квест", () -> t.onStart, v -> t.onStart = v);
                    text("  надпись", () -> t.startTitle, v -> t.startTitle = v);
                    check("задача выполнена", () -> t.onTask, v -> t.onTask = v);
                    text("  надпись", () -> t.taskTitle, v -> t.taskTitle = v);
                    check("новая задача", () -> t.onReveal, v -> t.onReveal = v);
                    text("  надпись", () -> t.revealTitle, v -> t.revealTitle = v);
                    check("квест выполнен", () -> t.onComplete, v -> t.onComplete = v);
                    text("  надпись", () -> t.completeTitle, v -> t.completeTitle = v);
                    check("квест провален", () -> t.onFail, v -> t.onFail = v);
                    text("  надпись", () -> t.failTitle, v -> t.failTitle = v);
                    number("секунд", () -> String.valueOf(t.seconds), v -> {
                        try {
                            t.seconds = Math.max(0.5f, Math.min(30, Float.parseFloat(v)));
                        } catch (NumberFormatException ignored) {
                        }
                    });
                    check("звук", () -> t.sound, v -> t.sound = v);
                    button("Показать в игре", Icon.PLAY, () -> org.sprautestudio.spraute_engine.quest.client.QuestClient.showToast("task", "Задача выполнена", "Пример задачи"));
                }
                case "zone" -> {
                    header("Поведение");
                    number("секунд", () -> String.valueOf(cfg.zoneTitle.seconds), v -> {
                        try {
                            cfg.zoneTitle.seconds = Math.max(0.5f, Math.min(30, Float.parseFloat(v)));
                        } catch (NumberFormatException ignored) {
                        }
                    });
                    text("звук", () -> cfg.zoneTitle.sound, v -> cfg.zoneTitle.sound = v.trim());
                }
                case "quest", "shop" -> {
                    header("Вид");
                    color("затемнение", () -> GuiDoc.toColor(d.get("background")), v -> {
                        snapshot(scene);
                        d.set("background", String.format("#%08X", v));
                        changed(scene);
                    });
                    if (scene.equals("quest")) note("Цели на экране — часть «HUD · Цели квеста» в сцене «Игра (HUD)». Плашки событий — сцена «Уведомление квеста»");
                }
                case "hud" -> {
                    header("Системы");
                    check("здоровье Spraute", () -> cfg.health, v -> cfg.health = v);
                    check("монеты Spraute", () -> cfg.currency, v -> cfg.currency = v);
                    note("«HUD · Здоровье» рисует круглую шкалу Spraute, если она включена, иначе сердца");
                }
                default -> {
                }
            }
            header("Раскладка");
            button("Добавить часть…", Icon.PLUS, () -> addMenu(ox + vw - RIGHT + 12, oy + 120));
            button("Стандартный вид", Icon.REFRESH, EngineStudio.this::standardLook);
        }
    }

    /** Stretches the part to the screen's edges (or stops it); the inspector shows the new state. */
    private void stretch(GuiDoc.Element e, String how) {
        snapshot(scene);
        String st = e.get("stretch");
        boolean sw = st.equals("width") || st.equals("both"), sh = st.equals("height") || st.equals("both");
        switch (how) {
            case "width" -> sw = true;
            case "height" -> sh = true;
            case "both" -> sw = sh = true;
            default -> sw = sh = false;
        }
        e.set("stretch", sw && sh ? "both" : sw ? "width" : sh ? "height" : "none");
        changed(scene);
        inspector.rebuild();
    }

    // ================================================================== the 3×3 anchor picker

    private static final String[][] GRID = {{"top_left", "top", "top_right"}, {"left", "center", "right"}, {"bottom_left", "bottom", "bottom_right"}};

    private final class AnchorPicker extends Widget implements UI.CursorProvider {
        private final GuiDoc.Element e;

        AnchorPicker(GuiDoc.Element e) {
            this.e = e;
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            int s = 46;
            c.roundBorder(x, y, s, s, 4, Theme.INPUT, Theme.BORDER_STRONG);
            String cur = e.get("anchor");
            for (int r = 0; r < 3; r++)
                for (int col = 0; col < 3; col++) {
                    // the centre of the cell: the square is cut in three equal parts
                    int cx = x + Math.round((col + 0.5f) * s / 3f), cyy = y + Math.round((r + 0.5f) * s / 3f);
                    boolean on = GRID[r][col].equals(cur);
                    boolean hov = mx >= x + col * s / 3 && mx < x + (col + 1) * s / 3 && my >= y + r * s / 3 && my < y + (r + 1) * s / 3;
                    int d = on ? 9 : 7;
                    c.round(cx - d / 2, cyy - d / 2, d, d, d / 2, on ? Theme.ACCENT : hov ? Theme.TEXT_DIM : Theme.TEXT_MUTED);
                }
            c.text("Привязка: " + anchorTitle(cur), x + s + 10, y + 6, Theme.TEXT);
            c.wrapped("Точка экрана (или группы), от которой считаются X и Y. Сменить — часть останется на месте",
                    x + s + 10, y + 18, w - s - 12, Theme.TEXT_MUTED, 0);
        }

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            int col = (int) ((mx - x) / (46 / 3f)), r = (int) ((my - y) / (46 / 3f));
            if (mx > x + 46 || col < 0 || col > 2 || r < 0 || r > 2) return true;
            String a = GRID[r][col];
            if (a.equals(e.get("anchor"))) return true;
            // the part stays where it is: only what its X and Y are counted from changes
            GuiView v = view(scene);
            int[] rect = v.rect(e).clone();
            snapshot(scene);
            e.set("anchor", a);
            v.layoutTree();
            int[] off = v.offsetFor(e, rect[0], rect[1], rect[2], rect[3]);
            e.set("x", String.valueOf(off[0]));
            e.set("y", String.valueOf(off[1]));
            changed(scene);
            return true;
        }

        @Override
        public int cursor(int mx, int my) {
            return UI.CURSOR_HAND;
        }
    }

    private static String anchorTitle(String a) {
        return switch (a) {
            case "top_left" -> "левый верх";
            case "top" -> "верх, центр";
            case "top_right" -> "правый верх";
            case "left" -> "левый край";
            case "right" -> "правый край";
            case "bottom_left" -> "левый низ";
            case "bottom" -> "низ, центр";
            case "bottom_right" -> "правый низ";
            default -> "центр";
        };
    }
}
