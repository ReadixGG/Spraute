package org.sprautestudio.st_addon_spraute.editor.gui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.editor.SprauteScreen;
import org.sprautestudio.st_addon_spraute.editor.assets.AssetIndex;
import org.sprautestudio.st_addon_spraute.editor.assets.AssetPicker;
import org.sprautestudio.st_addon_spraute.editor.project.Project;
import org.sprautestudio.st_addon_spraute.editor.tabs.EditorTab;
import org.sprautestudio.st_addon_spraute.gui.GuiDoc;
import org.sprautestudio.st_addon_spraute.gui.GuiDoc.Element;
import org.sprautestudio.st_addon_spraute.gui.GuiSchema;
import org.sprautestudio.st_addon_spraute.gui.client.GuiView;
import org.sprautestudio.st_addon_spraute.gui.client.SprauteGuiScreen;
import org.sprautestudio.st_addon_spraute.lang.Analyzer;
import org.sprautestudio.st_addon_spraute.lang.ParamType;
import org.sprautestudio.st_addon_spraute.ui.Box;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;
import org.sprautestudio.st_addon_spraute.ui.widgets.Button;
import org.sprautestudio.st_addon_spraute.ui.widgets.Checkbox;
import org.sprautestudio.st_addon_spraute.ui.widgets.ColorPicker;
import org.sprautestudio.st_addon_spraute.ui.widgets.Dialog;
import org.sprautestudio.st_addon_spraute.ui.widgets.Dropdown;
import org.sprautestudio.st_addon_spraute.ui.widgets.ListView;
import org.sprautestudio.st_addon_spraute.ui.widgets.PopupMenu;
import org.sprautestudio.st_addon_spraute.ui.widgets.ScrollView;
import org.sprautestudio.st_addon_spraute.ui.widgets.TextField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Visual editor of a Spraute GUI (.sgui): canvas with multi-selection, a layer tree with folders,
 * inspector, alignment tools and undo.
 */
public class GuiTab extends EditorTab {
    private static final int LEFT_W = 168, RIGHT_W = 236, TOOLBAR = 28;
    private static final Gson GSON = new Gson();

    private GuiDoc doc;
    /** Selected ids in selection order; the last one is the primary (shown in the inspector). */
    private final LinkedHashSet<String> selection = new LinkedHashSet<>();
    private boolean dirty;
    private final List<String> undo = new ArrayList<>(), redo = new ArrayList<>();
    private String editingKey;
    private static String clipboard;

    private final Box toolbar = Box.row().gap(3).pad(6, 4).background(Theme.PANEL);
    private final EditCanvas canvas;
    private final ListView<GuiSchema.Type> palette;
    private final LayerTree layers = new LayerTree();
    private final Inspector inspector = new Inspector();
    private final ScrollView inspectorScroll;
    private boolean showGrid = true, snap = true;
    private int grid = 8;

    /** What the palette and the canvas menu offer. */
    private List<GuiSchema.Type> types = GuiSchema.uiTypes();
    /** Embedded (the engine settings): no file — the owner takes the text, and its own title. */
    private String ownTitle;
    private Runnable ownSave;

    /**
     * An editor of an interface that lives somewhere else (an engine window in the engine settings): no file, its
     * own element set; {@link #json()} gives the text, onDirtyChanged tells about changes, save goes to the owner.
     */
    public GuiTab(Project project, String json, String title, List<GuiSchema.Type> types, Runnable save) {
        this(project, null, json);
        this.ownTitle = title;
        this.ownSave = save;
        this.types = types;
        palette.setItems(types);
    }

    /** The interface's text now. */
    public String json() {
        return doc.toJson();
    }

    /** The owner saved the text: nothing unsaved here any more. */
    public void markSaved() {
        dirty = false;
        if (onDirtyChanged != null) onDirtyChanged.run();
    }

    public GuiTab(Project project, Project.FileRef file, String json) {
        super(project, file);
        doc = GuiDoc.fromJson(json);
        canvas = new EditCanvas();
        add(canvas);

        palette = add(new ListView<>((c, t, x, y, w, h, sel, hov) -> {
            c.icon(iconOf(t.key()), x + 8, y + (h - 9) / 2, colorOf(t.key()));
            c.text(t.title(), x + 22, y + (h - 8) / 2, Theme.TEXT);
        }));
        palette.rowHeight = 16;
        palette.background = Theme.PANEL;
        palette.setItems(types);
        palette.activateOnSingleClick = true;
        palette.onActivate = t -> addElement(t.key());
        palette.tooltip("Клик — добавить элемент. Если выбрана папка, кроп-зона или прокрутка — элемент попадёт внутрь");

        add(layers);
        inspectorScroll = add(new ScrollView(inspector) {
            @Override
            protected int contentHeight() {
                return inspector.prefH;
            }
        });

        buildToolbar();
        add(toolbar);
        layers.rebuild();
        inspector.rebuild();
    }

    // ================================================================ toolbar

    private Button tool(Icon icon, String tip, Runnable action) {
        Button b = Button.icon(icon, tip, action);
        b.prefW = 20;
        toolbar.add(b);
        return b;
    }

    private Button toolText(String text, Icon icon, String tip, Runnable action) {
        Button b = new Button(text, action).icon(icon).style(Button.Style.GHOST);
        b.autoWidth();
        b.tooltip(tip);
        toolbar.add(b);
        return b;
    }

    private void sep() {
        Widget s = new Widget();
        s.prefW = 8;
        toolbar.add(s);
    }

    private void buildToolbar() {
        tool(Icon.UNDO, "Отменить (Ctrl+Z)", this::undo);
        tool(Icon.REDO, "Повторить (Ctrl+Y)", this::redo);
        sep();
        tool(Icon.LIST, "Сетка", () -> showGrid = !showGrid).toggle(() -> showGrid);
        tool(Icon.PIN, "Привязка к сетке и элементам (Alt при перетаскивании — без привязки)", () -> snap = !snap).toggle(() -> snap);
        tool(Icon.SEARCH, "Вписать макет в окно", canvas::fit);
        sep();
        tool(Icon.ARROW_RIGHT, "По левому краю (родителя или выделения)", () -> align(0, -1));
        tool(Icon.DOT, "По центру по горизонтали", () -> align(0, 0));
        tool(Icon.CHEVRON_RIGHT, "По правому краю", () -> align(0, 1));
        tool(Icon.CHEVRON_UP, "По верхнему краю", () -> align(1, -1));
        tool(Icon.MENU, "По центру по вертикали", () -> align(1, 0));
        tool(Icon.CHEVRON_DOWN, "По нижнему краю", () -> align(1, 1));
        tool(Icon.SKIP, "Распределить по горизонтали (от 3 элементов)", () -> distribute(0));
        tool(Icon.LIST, "Распределить по вертикали (от 3 элементов)", () -> distribute(1));
        sep();
        tool(Icon.FOLDER, "Сгруппировать в папку (Ctrl+G)", this::groupSelection);
        tool(Icon.CHEVRON_UP, "Вперёд (PageUp)", () -> move(1));
        tool(Icon.CHEVRON_DOWN, "Назад (PageDown)", () -> move(-1));
        tool(Icon.COPY, "Дублировать (Ctrl+D)", this::duplicate);
        tool(Icon.TRASH, "Удалить (Delete)", this::deleteSelected).iconColor(Theme.ERROR);
        sep();
        toolText("Анимация", Icon.PLAY, "Проиграть анимации появления на макете", () -> {
            canvas.playAppear = true;
            canvas.restartAppear();
            canvas.playUntil = System.currentTimeMillis() + 4000;
        });
        toolText("Открыть в игре", Icon.UPLOAD, "Показать интерфейс в игре, как его увидит игрок (Esc — назад)", this::testInGame).style(Button.Style.SUCCESS);
    }

    private void testInGame() {
        if (Minecraft.getInstance().level == null) {
            UI ui = UI.get();
            if (ui != null) ui.toastError("Войдите в мир, чтобы открыть интерфейс");
            return;
        }
        save0();
        SprauteGuiScreen.openLocal(doc, project, (el, kind) -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal(kind.equals("click")
                        ? "[Spraute] нажата кнопка «" + el + "» → button() = \"" + el + "\"" : "[Spraute] интерфейс закрыт"), true);
            }
        }, reopen);
    }

    /** Where to come back after trying the interface in the game. */
    public Runnable reopen = SprauteScreen::open;

    // ================================================================ selection

    private Element primary() {
        String last = null;
        for (String s : selection) last = s;
        return last == null ? null : doc.element(last);
    }

    private List<Element> selected() {
        List<Element> list = new ArrayList<>();
        for (String id : selection) {
            Element e = doc.element(id);
            if (e != null) list.add(e);
        }
        return list;
    }

    /** Selected elements without those whose ancestor is selected too. */
    private List<Element> selectedRoots() {
        List<Element> all = selected(), out = new ArrayList<>();
        for (Element e : all) {
            boolean nested = false;
            for (Element o : all) if (o != e && GuiDoc.contains(o, e)) nested = true;
            if (!nested) out.add(e);
        }
        return out;
    }

    private void select(String id) {
        if (selection.size() == 1 && id != null && selection.contains(id)) return;
        selection.clear();
        if (id != null) selection.add(id);
        selectionChanged();
    }

    private void toggleSelect(String id) {
        if (!selection.remove(id)) selection.add(id);
        selectionChanged();
    }

    private void selectionChanged() {
        selection.removeIf(id -> doc.element(id) == null);
        editingKey = null;
        inspector.rebuild();
        layers.reveal(primary());
    }

    // ================================================================ undo

    void snapshot() {
        String json = doc.toJson();
        if (!undo.isEmpty() && undo.get(undo.size() - 1).equals(json)) return;
        undo.add(json);
        if (undo.size() > 200) undo.remove(0);
        redo.clear();
    }

    private void changed() {
        if (!dirty) {
            dirty = true;
            if (onDirtyChanged != null) onDirtyChanged.run();
        }
        canvas.layoutTreeSafe();
        layers.rebuild();
    }

    private void restore(String json) {
        doc = GuiDoc.fromJson(json);
        canvas.doc = doc;
        selection.removeIf(id -> doc.element(id) == null);
        inspector.rebuild();
        changed();
    }

    @Override
    public void undo() {
        if (undo.isEmpty()) return;
        redo.add(doc.toJson());
        restore(undo.remove(undo.size() - 1));
    }

    @Override
    public void redo() {
        if (redo.isEmpty()) return;
        undo.add(doc.toJson());
        restore(redo.remove(redo.size() - 1));
    }

    // ================================================================ tree operations

    /** Where new and pasted elements go: inside a selected container, else next to the selection, else the root. */
    private List<Element> targetList() {
        Element p = primary();
        if (p == null) return doc.elements;
        if (p.isContainer()) return p.children;
        List<Element> l = doc.listOf(p);
        return l != null ? l : doc.elements;
    }

    private void addElement(String type) {
        snapshot();
        Element e = new Element();
        e.type = type;
        e.id = doc.freshId(type);
        List<Element> list = targetList();
        list.add(e);
        if (list == doc.elements) {
            int n = 0;
            for (Element o : doc.elements) if (o != e && o.get("anchor").equals("center") && o.i("x") == n * 8 && o.i("y") == n * 8) n++;
            e.set("x", String.valueOf(n * 8));
            e.set("y", String.valueOf(n * 8));
        } else {
            e.set("anchor", "top_left");
            e.set("x", "6");
            e.set("y", String.valueOf(6 + (list.size() - 1) * 4));
        }
        changed();
        select(e.id);
    }

    private void deleteSelected() {
        List<Element> roots = selectedRoots();
        if (roots.isEmpty()) return;
        snapshot();
        for (Element e : roots) {
            List<Element> l = doc.listOf(e);
            if (l != null) l.remove(e);
        }
        selection.clear();
        selectionChanged();
        changed();
    }

    private Element copyWithFreshIds(Element e) {
        Element c = e.copy();
        renameAll(c, new HashSet<>());
        return c;
    }

    private void renameAll(Element e, Set<String> taken) {
        String id = e.id;
        int n = 2;
        while (doc.element(id) != null || taken.contains(id)) id = e.id.replaceAll("\\d+$", "") + n++;
        e.id = id;
        taken.add(id);
        for (Element c : e.children) renameAll(c, taken);
    }

    private void duplicate() {
        List<Element> roots = selectedRoots();
        if (roots.isEmpty()) return;
        snapshot();
        List<String> fresh = new ArrayList<>();
        for (Element e : roots) {
            List<Element> l = doc.listOf(e);
            if (l == null) continue;
            Element copy = copyWithFreshIds(e);
            copy.set("x", String.valueOf(e.i("x") + 8));
            copy.set("y", String.valueOf(e.i("y") + 8));
            copy.locked = false;
            l.add(l.indexOf(e) + 1, copy);
            fresh.add(copy.id);
        }
        selection.clear();
        selection.addAll(fresh);
        changed();
        selectionChanged();
    }

    private void copySelected() {
        List<Element> roots = selectedRoots();
        if (!roots.isEmpty()) clipboard = GSON.toJson(roots);
    }

    private void paste() {
        if (clipboard == null) return;
        List<Element> items;
        try {
            items = GSON.fromJson(clipboard, new TypeToken<List<Element>>() {}.getType());
        } catch (RuntimeException e) {
            return;
        }
        if (items == null || items.isEmpty()) return;
        snapshot();
        GuiDoc tmp = GuiDoc.fromJson(GSON.toJson(java.util.Map.of("elements", items)));
        List<Element> list = targetList();
        List<String> fresh = new ArrayList<>();
        for (Element e : tmp.elements) {
            Element c = copyWithFreshIds(e);
            list.add(c);
            fresh.add(c.id);
        }
        selection.clear();
        selection.addAll(fresh);
        changed();
        selectionChanged();
    }

    /** Moves an element into another list keeping where it is on screen. */
    private void reparent(Element e, List<Element> target, int index) {
        int[] abs = canvas.rect(e).clone();
        List<Element> from = doc.listOf(e);
        if (from == null) return;
        int old = from.indexOf(e);
        from.remove(e);
        if (from == target && old < index) index--;
        target.add(Math.max(0, Math.min(target.size(), index)), e);
        canvas.layoutTreeSafe();
        applyRect(e, abs);
        canvas.layoutTreeSafe();
    }

    private Element containerOf(List<Element> list) {
        for (Element p : doc.all()) if (p.children == list) return p;
        return null;
    }

    private void groupSelection() {
        List<Element> roots = selectedRoots();
        if (roots.isEmpty()) return;
        List<Element> list = doc.listOf(roots.get(roots.size() - 1));
        if (list == null) return;
        roots.removeIf(e -> doc.listOf(e) != list);
        snapshot();
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        Map<Element, int[]> abs = new IdentityHashMap<>();
        int at = 0;
        for (Element e : roots) {
            int[] r = canvas.rect(e).clone();
            abs.put(e, r);
            x0 = Math.min(x0, r[0]);
            y0 = Math.min(y0, r[1]);
            x1 = Math.max(x1, r[0] + r[2]);
            y1 = Math.max(y1, r[1] + r[3]);
            at = Math.max(at, list.indexOf(e));
        }
        Element g = new Element();
        g.type = "group";
        g.id = doc.freshId("group");
        g.set("anchor", "top_left");
        list.add(at + 1, g);
        canvas.layoutTreeSafe();
        applyRect(g, new int[]{x0, y0, x1 - x0, y1 - y0});
        canvas.layoutTreeSafe();
        List<Element> ordered = new ArrayList<>(list);
        ordered.retainAll(roots);
        for (Element e : ordered) {
            list.remove(e);
            g.children.add(e);
        }
        canvas.layoutTreeSafe();
        for (Element e : ordered) {
            e.set("anchor", "top_left");
            applyRect(e, abs.get(e));
        }
        selection.clear();
        selection.add(g.id);
        changed();
        selectionChanged();
    }

    private void ungroup() {
        List<Element> roots = selectedRoots();
        List<String> released = new ArrayList<>();
        boolean any = false;
        for (Element g : roots) if (g.isContainer()) any = true;
        if (!any) return;
        snapshot();
        for (Element g : roots) {
            if (!g.isContainer()) continue;
            List<Element> list = doc.listOf(g);
            if (list == null) continue;
            int at = list.indexOf(g);
            Map<Element, int[]> abs = new IdentityHashMap<>();
            for (Element c : g.children) abs.put(c, canvas.rect(c).clone());
            List<Element> kids = new ArrayList<>(g.children);
            g.children.clear();
            list.remove(g);
            for (int i = 0; i < kids.size(); i++) list.add(at + i, kids.get(i));
            canvas.layoutTreeSafe();
            for (Element c : kids) {
                applyRect(c, abs.get(c));
                released.add(c.id);
            }
        }
        selection.clear();
        selection.addAll(released);
        changed();
        selectionChanged();
    }

    /** Moves the selection one step to the front (1) or back (-1); 2 / -2 to the very front / back. */
    private void move(int dir) {
        List<Element> roots = selectedRoots();
        if (roots.isEmpty()) return;
        snapshot();
        for (Element e : dir > 0 ? reversed(roots) : roots) {
            List<Element> l = doc.listOf(e);
            if (l == null) continue;
            int i = l.indexOf(e);
            int j = dir == 2 ? l.size() - 1 : dir == -2 ? 0 : Math.max(0, Math.min(l.size() - 1, i + dir));
            if (i == j) continue;
            l.remove(i);
            l.add(j, e);
        }
        changed();
    }

    private static <T> List<T> reversed(List<T> l) {
        List<T> r = new ArrayList<>(l);
        java.util.Collections.reverse(r);
        return r;
    }

    /** axis 0 = horizontal, 1 = vertical; where -1 start, 0 centre, 1 end. One element aligns to its parent, several to their bounds. */
    private void align(int axis, int where) {
        List<Element> els = selectedRoots();
        if (els.isEmpty()) return;
        snapshot();
        float b0, b1;
        if (els.size() == 1) {
            float[] ar = canvas.areaOf(els.get(0));
            b0 = ar[axis];
            b1 = ar[axis] + ar[axis + 2];
        } else {
            b0 = Float.MAX_VALUE;
            b1 = -Float.MAX_VALUE;
            for (Element e : els) {
                int[] r = canvas.rect(e);
                b0 = Math.min(b0, r[axis]);
                b1 = Math.max(b1, r[axis] + r[axis + 2]);
            }
        }
        for (Element e : els) {
            int[] r = canvas.rect(e).clone();
            r[axis] = where < 0 ? Math.round(b0) : where == 0 ? Math.round((b0 + b1 - r[axis + 2]) / 2) : Math.round(b1 - r[axis + 2]);
            applyRect(e, r);
        }
        changed();
    }

    private void distribute(int axis) {
        List<Element> els = selectedRoots();
        if (els.size() < 3) {
            UI ui = UI.get();
            if (ui != null) ui.toastInfo("Выберите хотя бы 3 элемента (Shift+клик)");
            return;
        }
        snapshot();
        els.sort((a, b) -> Integer.compare(canvas.rect(a)[axis], canvas.rect(b)[axis]));
        int[] first = canvas.rect(els.get(0)), last = canvas.rect(els.get(els.size() - 1));
        int total = 0;
        for (Element e : els) total += canvas.rect(e)[axis + 2];
        float gap = (last[axis] + last[axis + 2] - first[axis] - total) / (float) (els.size() - 1);
        float pos = first[axis];
        for (Element e : els) {
            int[] r = canvas.rect(e).clone();
            r[axis] = Math.round(pos);
            pos += r[axis + 2] + gap;
            applyRect(e, r);
        }
        changed();
    }

    void applyRect(Element e, int[] r) {
        int[] off = canvas.offsetFor(e, r[0], r[1], r[2], r[3]);
        float[] ar = canvas.areaOf(e);
        // values typed in percent stay in percent
        e.set("x", keepUnit(e.get("x"), off[0], ar[2]));
        e.set("y", keepUnit(e.get("y"), off[1], ar[3]));
        e.set("w", keepUnit(e.get("w"), Math.max(1, r[2]), ar[2]));
        e.set("h", keepUnit(e.get("h"), Math.max(1, r[3]), ar[3]));
    }

    private static String keepUnit(String old, int px, float base) {
        if (old != null && old.trim().endsWith("%") && base > 0) {
            float pct = Math.round(px * 1000f / base) / 10f;
            return (pct == (int) pct ? String.valueOf((int) pct) : String.valueOf(pct)) + "%";
        }
        return String.valueOf(px);
    }

    private void renameElement(Element e) {
        Dialog.prompt("Имя элемента", "Id (по нему сюжет обращается к элементу):", e.id, v -> {
            if (v.isBlank()) return "Введите имя";
            if (!v.matches("[A-Za-z0-9_\\-а-яА-ЯёЁ]+")) return "Только буквы, цифры, _ и -";
            Element other = doc.element(v);
            return other != null && other != e ? "Такой id уже есть" : null;
        }, v -> {
            snapshot();
            boolean was = selection.remove(e.id);
            e.id = v;
            if (was) selection.add(v);
            inspector.rebuild();
            changed();
        });
    }

    private void elementMenu(Element e, int mx, int my) {
        if (!selection.contains(e.id)) select(e.id);
        PopupMenu pm = new PopupMenu();
        pm.item("Переименовать…", Icon.EDIT, "F2", () -> renameElement(e));
        pm.item("Копировать id", Icon.COPY, null, () -> Minecraft.getInstance().keyboardHandler.setClipboard(e.id));
        pm.separator();
        pm.item(e.b("visible") ? "Скрыть" : "Показать", Icon.SEARCH, "Ctrl+H", () -> toggleVisible(e));
        pm.item(e.locked ? "Разблокировать" : "Заблокировать на холсте", Icon.FLAG, "Ctrl+L", () -> {
            e.locked = !e.locked;
            changed();
        });
        pm.separator();
        pm.item("Сгруппировать в папку", Icon.FOLDER, "Ctrl+G", this::groupSelection);
        if (e.isContainer()) pm.item("Разгруппировать", Icon.FOLDER, "Ctrl+Shift+G", this::ungroup);
        List<Element> containers = new ArrayList<>();
        for (Element c : doc.all()) if (c.isContainer() && !GuiDoc.contains(e, c)) containers.add(c);
        if (!containers.isEmpty() || doc.listOf(e) != doc.elements) {
            pm.item("Переместить в…", Icon.ARROW_RIGHT, null, () -> {
                PopupMenu sub = new PopupMenu();
                sub.item("Корень интерфейса", Icon.HOME, null, () -> moveSelectionInto(doc.elements));
                for (Element c : containers) sub.item(c.id + "  (" + c.schema().title() + ")", iconOf(c.type), null, () -> moveSelectionInto(c.children));
                sub.show(mx + 20, my);
            });
        }
        pm.separator();
        pm.item("На передний план", Icon.CHEVRON_UP, "Ctrl+PageUp", () -> move(2));
        pm.item("На задний план", Icon.CHEVRON_DOWN, "Ctrl+PageDown", () -> move(-2));
        pm.item("Дублировать", Icon.COPY, "Ctrl+D", this::duplicate);
        pm.item("Удалить", Icon.TRASH, "Delete", this::deleteSelected).color(Theme.ERROR);
        pm.show(mx, my);
    }

    private void moveSelectionInto(List<Element> target) {
        snapshot();
        for (Element e : selectedRoots()) {
            Element owner = containerOf(target);
            if (owner != null && GuiDoc.contains(e, owner)) continue;
            reparent(e, target, target.size());
        }
        changed();
    }

    private void toggleVisible(Element e) {
        snapshot();
        e.set("visible", e.b("visible") ? "false" : "true");
        changed();
    }

    static Icon iconOf(String type) {
        return switch (type) {
            case "group" -> Icon.FOLDER;
            case "clip" -> Icon.SKIP;
            case "scroll" -> Icon.LIST;
            case "panel" -> Icon.FILE;
            case "image" -> Icon.IMAGE;
            case "text" -> Icon.EDIT;
            case "button" -> Icon.PLAY;
            case "entity" -> Icon.USER;
            case "item" -> Icon.CUBE;
            case "slot" -> Icon.CUBE;
            case "inventory" -> Icon.LIST;
            case "progress" -> Icon.MENU;
            case "input" -> Icon.CODE;
            default -> Icon.DOT;
        };
    }

    static int colorOf(String type) {
        return switch (type) {
            case "group" -> Theme.WARNING;
            case "clip" -> 0xFFFFCB6B;
            case "scroll" -> 0xFF5DA9F6;
            case "panel" -> 0xFFB0B7C9;
            case "image" -> Theme.INFO;
            case "text" -> Theme.TEXT;
            case "button" -> Theme.ACCENT;
            case "entity" -> Theme.ACCENT_2;
            case "item" -> Theme.WARNING;
            case "slot" -> 0xFFFF8F5A;
            case "inventory" -> 0xFFFF8F5A;
            case "progress" -> Theme.SUCCESS;
            case "input" -> Theme.CODE_STRING;
            default -> Theme.TEXT_DIM;
        };
    }

    private static boolean key(int k) {
        return GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), k) == GLFW.GLFW_PRESS;
    }

    private static boolean shiftDown() {
        return key(GLFW.GLFW_KEY_LEFT_SHIFT) || key(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static boolean ctrlDown() {
        return key(GLFW.GLFW_KEY_LEFT_CONTROL) || key(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    // ================================================================ canvas

    private final class EditCanvas extends GuiView implements UI.CursorProvider {
        private float zoom = 2;
        private float panX, panY;
        private boolean fitted;
        long playUntil;
        private int mode; // 0 none, 1 move, 2 resize, 3 pan, 4 marquee
        private int handle;
        private final Map<Element, int[]> startRects = new IdentityHashMap<>();
        private double startX, startY, curX, curY;
        private final List<float[]> guides = new ArrayList<>();

        EditCanvas() {
            super(GuiTab.this.doc, GuiTab.this.project.name());
            interactive = false;
            playAppear = false;
            localProject = GuiTab.this.project;
        }

        void layoutTreeSafe() {
            placeArea();
            layoutTree();
        }

        void fit() {
            float aw = doc.width(), ah = doc.height();
            zoom = Math.max(0.25f, Math.min((w - 40) / aw, (h - 40) / ah));
            panX = (w - aw * zoom) / 2;
            panY = (h - ah * zoom) / 2;
            fitted = true;
        }

        @Override
        public void layout() {
            super.layout();
            if (!fitted && w > 0 && h > 0) fit();
        }

        @Override
        protected void placeArea() {
            areaW = doc.width();
            areaH = doc.height();
            areaScale = zoom;
            areaX = x + panX;
            areaY = y + panY;
        }

        @Override
        protected boolean showHidden() {
            return true;
        }

        @Override
        protected float time() {
            if (playAppear && System.currentTimeMillis() > playUntil) playAppear = false;
            return super.time();
        }

        /**
         * Checkerboard, background and grid, only for the visible part of the design area. The grid
         * step doubles while lines would be closer than 8 px, every 4th line is brighter.
         */
        @Override
        protected void paintBackdrop(Canvas c) {
            c.fill(x, y, w, h, 0xFF0D0E12);
            int ax = Math.round(areaX), ay = Math.round(areaY), aw = Math.round(areaW * areaScale), ah = Math.round(areaH * areaScale);
            int vx0 = Math.max(ax, x), vy0 = Math.max(ay, y), vx1 = Math.min(ax + aw, x + w), vy1 = Math.min(ay + ah, y + h);
            if (vx1 > vx0 && vy1 > vy0) {
                c.fill(vx0, vy0, vx1 - vx0, vy1 - vy0, 0xFF344434);
                int cell = 24;
                int cx0 = ax + Math.floorDiv(vx0 - ax, cell) * cell, cy0 = ay + Math.floorDiv(vy0 - ay, cell) * cell;
                for (int yy = cy0; yy < vy1; yy += cell) {
                    int row = (yy - ay) / cell;
                    for (int xx = cx0 + (((row + (xx(cx0, ax, cell))) & 1) == 0 ? 0 : cell); xx < vx1; xx += cell * 2) {
                        int fx = Math.max(xx, vx0), fy = Math.max(yy, vy0);
                        c.fill(fx, fy, Math.min(xx + cell, vx1) - fx, Math.min(yy + cell, vy1) - fy, 0xFF3A4A3A);
                    }
                }
                int bg = GuiDoc.toColor(doc.get("background"));
                if ((bg >>> 24) != 0) c.fill(vx0, vy0, vx1 - vx0, vy1 - vy0, bg);
                if (showGrid && grid > 0) {
                    int step = grid;
                    while (step * areaScale < 8) step *= 2;
                    int gx0 = (int) Math.ceil(Math.max(0, toDesignX(vx0)) / step) * step;
                    int gy0 = (int) Math.ceil(Math.max(0, toDesignY(vy0)) / step) * step;
                    for (int gx = gx0; gx < areaW && areaX + gx * areaScale < vx1; gx += step) {
                        c.fill(Math.round(areaX + gx * areaScale), vy0, 1, vy1 - vy0, (gx / step) % 4 == 0 ? 0x26FFFFFF : 0x12FFFFFF);
                    }
                    for (int gy = gy0; gy < areaH && areaY + gy * areaScale < vy1; gy += step) {
                        c.fill(vx0, Math.round(areaY + gy * areaScale), vx1 - vx0, 1, (gy / step) % 4 == 0 ? 0x26FFFFFF : 0x12FFFFFF);
                    }
                }
                c.fill(ax + aw / 2, vy0, 1, vy1 - vy0, 0x22A08AFF);
                c.fill(vx0, ay + ah / 2, vx1 - vx0, 1, 0x22A08AFF);
            }
            c.outline(ax - 1, ay - 1, aw + 2, ah + 2, Theme.BORDER_STRONG);
            String size = doc.width() + " × " + doc.height() + (doc.get("layout").equals("screen") ? " (экран)" : "") + "   " + Math.round(zoom * 100) + "%";
            c.text(size, ax, ay - 11, Theme.TEXT_MUTED);
        }

        private static int xx(int cx0, int ax, int cell) {
            return Math.floorDiv(cx0 - ax, cell);
        }

        private int[] screenRect(Element e) {
            int[] r = rect(e);
            return new int[]{Math.round(areaX + r[0] * areaScale), Math.round(areaY + r[1] * areaScale),
                    Math.round(r[2] * areaScale), Math.round(r[3] * areaScale)};
        }

        private int[][] handles(int[] s) {
            int x0 = s[0], y0 = s[1], x1 = s[0] + s[2], y1 = s[1] + s[3], xm = (x0 + x1) / 2, ym = (y0 + y1) / 2;
            return new int[][]{{x0, y0}, {xm, y0}, {x1, y0}, {x1, ym}, {x1, y1}, {xm, y1}, {x0, y1}, {x0, ym}};
        }

        @Override
        protected void paintOver(Canvas c, int mx, int my, float dt) {
            c.pushClip(x, y, w, h);
            if (mode == 0) {
                Element hov = target(mx, my, false);
                if (hov != null && !selection.contains(hov.id)) {
                    int[] s = screenRect(hov);
                    c.outline(s[0], s[1], s[2], s[3], 0x88A08AFF);
                }
            }
            // groups are invisible: show their boxes faintly
            for (Element e : doc.all()) {
                if (!e.type.equals("group") || selection.contains(e.id)) continue;
                int[] s = screenRect(e);
                dashed(c, s, 0x33FFB84B);
            }
            List<Element> sel = selected();
            for (Element e : sel) {
                int[] s = screenRect(e);
                c.outline(s[0] - 1, s[1] - 1, s[2] + 2, s[3] + 2, Theme.ACCENT);
            }
            Element p = primary();
            if (p != null && sel.size() == 1 && !p.locked) {
                for (int[] hp : handles(screenRect(p))) {
                    c.fill(hp[0] - 3, hp[1] - 3, 7, 7, Theme.ACCENT);
                    c.fill(hp[0] - 2, hp[1] - 2, 5, 5, 0xFFFFFFFF);
                }
            }
            if (p != null) {
                int[] s = screenRect(p);
                String label = (sel.size() > 1 ? sel.size() + " эл.  " : "") + p.id + "  " + canvas.rect(p)[2] + "×" + canvas.rect(p)[3];
                int lw = c.width(label) + 8;
                c.round(s[0], s[1] - 13, lw, 12, 3, Theme.ACCENT);
                c.text(label, s[0] + 4, s[1] - 11, 0xFFFFFFFF);
            }
            for (float[] g : guides) {
                if (g[0] == 0) c.fill(Math.round(areaX + g[1] * areaScale), y, 1, h, 0xFFFF5AA0);
                else c.fill(x, Math.round(areaY + g[1] * areaScale), w, 1, 0xFFFF5AA0);
            }
            if (mode == 4) {
                int x0 = (int) Math.min(startX, curX), y0 = (int) Math.min(startY, curY);
                int ww = (int) Math.abs(curX - startX), hh = (int) Math.abs(curY - startY);
                c.fill(x0, y0, ww, hh, 0x228B6CFF);
                c.outline(x0, y0, ww, hh, Theme.ACCENT);
            }
            c.popClip();
            if (doc.elements.isEmpty()) c.textCentered("Добавьте элементы из палитры слева", x + w / 2, y + h / 2 - 4, Theme.TEXT_MUTED);
        }

        private void dashed(Canvas c, int[] s, int color) {
            for (int i = 0; i < s[2]; i += 6) {
                c.fill(s[0] + i, s[1], Math.min(3, s[2] - i), 1, color);
                c.fill(s[0] + i, s[1] + s[3] - 1, Math.min(3, s[2] - i), 1, color);
            }
            for (int i = 0; i < s[3]; i += 6) {
                c.fill(s[0], s[1] + i, 1, Math.min(3, s[3] - i), color);
                c.fill(s[0] + s[2] - 1, s[1] + i, 1, Math.min(3, s[3] - i), color);
            }
        }

        /** Root-first chain of containers down to the deepest unlocked element under the point. */
        private List<Element> chainAt(double mx, double my) {
            Element deep = pick(toDesignX(mx), toDesignY(my), e -> !e.locked);
            List<Element> chain = new ArrayList<>();
            for (Element e = deep; e != null; e = doc.parentOf(e)) chain.add(0, e);
            return chain;
        }

        /**
         * What a click selects, Figma-style: the top-level element, or a sibling of the current
         * selection when it is inside a group; {@code deeper} steps one level in (double click).
         */
        private Element target(double mx, double my, boolean deeper) {
            List<Element> chain = chainAt(mx, my);
            if (chain.isEmpty()) return null;
            int level = 0;
            for (Element s : selected()) {
                int idx = chain.indexOf(s);
                if (idx >= 0) level = Math.max(level, deeper ? idx + 1 : idx);
                else {
                    int pi = chain.indexOf(doc.parentOf(s));
                    if (pi >= 0) level = Math.max(level, pi + 1);
                }
            }
            return chain.get(Math.min(level, chain.size() - 1));
        }

        private int handleAt(double mx, double my) {
            Element e = primary();
            if (e == null || e.locked || selection.size() != 1) return -1;
            int[][] hs = handles(screenRect(e));
            for (int i = 0; i < hs.length; i++) if (Math.abs(mx - hs[i][0]) <= 4 && Math.abs(my - hs[i][1]) <= 4) return i;
            return -1;
        }

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            computeArea();
            requestFocus();
            startX = curX = mx;
            startY = curY = my;
            if (button == 2 || (button == 0 && key(GLFW.GLFW_KEY_SPACE))) {
                mode = 3;
                return true;
            }
            if (button == 1) {
                Element e = target(mx, my, false);
                if (e != null) elementMenu(e, (int) mx, (int) my);
                else docMenu((int) mx, (int) my);
                return true;
            }
            int hd = handleAt(mx, my);
            if (hd >= 0) {
                snapshot();
                mode = 2;
                handle = hd;
                startRects.clear();
                startRects.put(primary(), rect(primary()).clone());
                return true;
            }
            Element e = target(mx, my, false);
            boolean additive = shiftDown() || ctrlDown();
            if (e == null) {
                if (!additive) select(null);
                mode = 4; // rubber band selection
                return true;
            }
            if (additive) toggleSelect(e.id);
            else if (!selection.contains(e.id)) select(e.id);
            else {
                // keep the multi-selection, make e primary
                selection.remove(e.id);
                selection.add(e.id);
                inspector.rebuild();
            }
            snapshot();
            mode = 1;
            startRects.clear();
            for (Element s : selectedRoots()) if (!s.locked) startRects.put(s, rect(s).clone());
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double ddx, double ddy) {
            curX = mx;
            curY = my;
            if (mode == 3) {
                panX += (float) ddx;
                panY += (float) ddy;
                return true;
            }
            if (mode == 4) return true;
            Element p = primary();
            if (p == null || startRects.isEmpty()) return true;
            float dx = (float) ((mx - startX) / areaScale), dy = (float) ((my - startY) / areaScale);
            guides.clear();
            boolean useSnap = snap && !key(GLFW.GLFW_KEY_LEFT_ALT);
            if (mode == 1) {
                int[] base = startRects.getOrDefault(p, startRects.values().iterator().next());
                int[] moved = base.clone();
                moved[0] = Math.round(base[0] + dx);
                moved[1] = Math.round(base[1] + dy);
                if (useSnap) snapMove(moved, startRects.keySet());
                int sx = moved[0] - base[0], sy = moved[1] - base[1];
                for (Map.Entry<Element, int[]> en : startRects.entrySet()) {
                    int[] r = en.getValue().clone();
                    r[0] += sx;
                    r[1] += sy;
                    applyRect(en.getKey(), r);
                }
            } else if (mode == 2) {
                int[] s = startRects.get(p);
                int x0 = s[0], y0 = s[1], x1 = x0 + s[2], y1 = y0 + s[3];
                boolean left = handle == 0 || handle == 6 || handle == 7, right = handle == 2 || handle == 3 || handle == 4;
                boolean top = handle == 0 || handle == 1 || handle == 2, bottom = handle == 4 || handle == 5 || handle == 6;
                if (left) x0 = snapEdge(p, Math.round(s[0] + dx), 0, useSnap);
                if (right) x1 = snapEdge(p, Math.round(s[0] + s[2] + dx), 0, useSnap);
                if (top) y0 = snapEdge(p, Math.round(s[1] + dy), 1, useSnap);
                if (bottom) y1 = snapEdge(p, Math.round(s[1] + s[3] + dy), 1, useSnap);
                if (x1 - x0 < 2) {
                    if (left) x0 = x1 - 2;
                    else x1 = x0 + 2;
                }
                if (y1 - y0 < 2) {
                    if (top) y0 = y1 - 2;
                    else y1 = y0 + 2;
                }
                if (shiftDown() && s[3] > 0) {
                    float ratio = s[2] / (float) s[3];
                    int nh = Math.round((x1 - x0) / ratio);
                    if (top) y0 = y1 - nh;
                    else y1 = y0 + nh;
                }
                applyRect(p, new int[]{x0, y0, x1 - x0, y1 - y0});
            }
            layoutTree();
            dirtyQuiet();
            return true;
        }

        private void dirtyQuiet() {
            if (!dirty) {
                dirty = true;
                if (onDirtyChanged != null) onDirtyChanged.run();
            }
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            if (mode == 4) {
                float dx0 = toDesignX(Math.min(startX, curX)), dy0 = toDesignY(Math.min(startY, curY));
                float dx1 = toDesignX(Math.max(startX, curX)), dy1 = toDesignY(Math.max(startY, curY));
                if (dx1 - dx0 > 1 || dy1 - dy0 > 1) {
                    if (!shiftDown() && !ctrlDown()) selection.clear();
                    // select among the siblings of the current scope (top level by default)
                    for (Element e : doc.elements) {
                        if (e.locked || (!e.b("visible"))) continue;
                        int[] r = rect(e);
                        if (r[0] < dx1 && r[0] + r[2] > dx0 && r[1] < dy1 && r[1] + r[3] > dy0) selection.add(e.id);
                    }
                    selectionChanged();
                }
            }
            if (mode == 1 || mode == 2) {
                inspector.rebuild();
                layers.rebuild();
            }
            mode = 0;
            guides.clear();
            startRects.clear();
            return true;
        }

        private float threshold() {
            return 5 / areaScale;
        }

        /** Snapping lines on an axis: the parent area, siblings' and other elements' edges and centres. */
        private List<Float> lines(Set<Element> moving, Element ref, int axis) {
            List<Float> list = new ArrayList<>();
            float[] ar = areaOf(ref);
            list.add(ar[axis]);
            list.add(ar[axis] + ar[axis + 2] / 2);
            list.add(ar[axis] + ar[axis + 2]);
            for (Element o : doc.all()) {
                boolean skip = false;
                for (Element m : moving) if (GuiDoc.contains(m, o)) skip = true;
                if (skip || !o.b("visible")) continue;
                int[] r = rect(o);
                int pos = r[axis], s = r[axis + 2];
                list.add((float) pos);
                list.add(pos + s / 2f);
                list.add((float) (pos + s));
            }
            return list;
        }

        private void snapMove(int[] r, Set<Element> moving) {
            Element ref = primary();
            for (int axis = 0; axis < 2; axis++) {
                int pos = r[axis], size = r[axis + 2];
                float best = threshold() + 1, shift = 0, at = 0;
                for (float line : lines(moving, ref, axis)) {
                    for (float edge : new float[]{pos, pos + size / 2f, pos + size}) {
                        float d = Math.abs(line - edge);
                        if (d < best) {
                            best = d;
                            shift = line - edge;
                            at = line;
                        }
                    }
                }
                if (best <= threshold()) {
                    r[axis] = Math.round(pos + shift);
                    guides.add(new float[]{axis, at});
                } else if (grid > 0) {
                    float origin = areaOf(ref)[axis];
                    r[axis] = Math.round(origin + Math.round((pos - origin) / (float) grid) * grid);
                }
            }
        }

        private int snapEdge(Element e, int value, int axis, boolean useSnap) {
            if (!useSnap) return value;
            float best = threshold() + 1, at = value;
            for (float line : lines(Set.of(e), e, axis)) {
                float d = Math.abs(line - value);
                if (d < best) {
                    best = d;
                    at = line;
                }
            }
            if (best <= threshold()) {
                guides.add(new float[]{axis, at});
                return Math.round(at);
            }
            if (grid <= 0) return value;
            float origin = areaOf(e)[axis];
            return Math.round(origin + Math.round((value - origin) / (float) grid) * grid);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            computeArea();
            if (ctrlDown() || !shiftDown()) {
                float before = zoom;
                zoom = Math.max(0.25f, Math.min(8f, zoom * (delta > 0 ? 1.15f : 1 / 1.15f)));
                float k = zoom / before;
                panX = (float) (mx - x - (mx - x - panX) * k);
                panY = (float) (my - y - (my - y - panY) * k);
            } else {
                // Shift+wheel scrolls a scroll panel on the canvas to preview its content
                scrollAt(toDesignX(mx), toDesignY(my), delta, false);
            }
            return true;
        }

        @Override
        public boolean mouseDoubleClicked(double mx, double my, int button) {
            Element e = target(mx, my, true);
            if (e == null) return true;
            if (!selection.contains(e.id) || selection.size() > 1) {
                select(e.id);
                return true;
            }
            inspector.focus("text");
            return true;
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0, shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
            Element e = primary();
            switch (key) {
                case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> deleteSelected();
                case GLFW.GLFW_KEY_D -> {
                    if (!ctrl) return false;
                    duplicate();
                }
                case GLFW.GLFW_KEY_C -> {
                    if (!ctrl) return false;
                    copySelected();
                }
                case GLFW.GLFW_KEY_X -> {
                    if (!ctrl) return false;
                    copySelected();
                    deleteSelected();
                }
                case GLFW.GLFW_KEY_V -> {
                    if (!ctrl) return false;
                    paste();
                }
                case GLFW.GLFW_KEY_A -> {
                    if (!ctrl) return false;
                    selection.clear();
                    List<Element> scope = e != null ? doc.listOf(e) : doc.elements;
                    for (Element o : scope != null ? scope : doc.elements) selection.add(o.id);
                    selectionChanged();
                }
                case GLFW.GLFW_KEY_G -> {
                    if (!ctrl) return false;
                    if (shift) ungroup();
                    else groupSelection();
                }
                case GLFW.GLFW_KEY_H -> {
                    if (!ctrl) return false;
                    snapshot();
                    for (Element s : selected()) s.set("visible", s.b("visible") ? "false" : "true");
                    changed();
                }
                case GLFW.GLFW_KEY_L -> {
                    if (!ctrl) return false;
                    for (Element s : selected()) s.locked = !s.locked;
                    changed();
                }
                case GLFW.GLFW_KEY_F2 -> {
                    if (e != null) renameElement(e);
                }
                case GLFW.GLFW_KEY_PAGE_UP -> move(ctrl ? 2 : 1);
                case GLFW.GLFW_KEY_PAGE_DOWN -> move(ctrl ? -2 : -1);
                case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                    List<Element> roots = selectedRoots();
                    if (roots.isEmpty()) return true;
                    int step = shift ? 10 : 1;
                    snapshot();
                    for (Element s : roots) {
                        if (s.locked) continue;
                        int[] r = rect(s).clone();
                        if (key == GLFW.GLFW_KEY_LEFT) r[0] -= step;
                        if (key == GLFW.GLFW_KEY_RIGHT) r[0] += step;
                        if (key == GLFW.GLFW_KEY_UP) r[1] -= step;
                        if (key == GLFW.GLFW_KEY_DOWN) r[1] += step;
                        applyRect(s, r);
                    }
                    layoutTree();
                    dirtyQuiet();
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    Element p = primary();
                    Element parent = p != null ? doc.parentOf(p) : null;
                    select(parent != null ? parent.id : null); // step out of a group, then deselect
                }
                default -> {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int cursor(int mx, int my) {
            if (mode == 3) return UI.CURSOR_MOVE;
            int hd = handleAt(mx, my);
            if (hd == 1 || hd == 5) return UI.CURSOR_VRESIZE;
            if (hd == 3 || hd == 7) return UI.CURSOR_HRESIZE;
            if (hd >= 0) return UI.CURSOR_MOVE;
            return target(mx, my, false) != null ? UI.CURSOR_HAND : UI.CURSOR_ARROW;
        }
    }

    private void docMenu(int mx, int my) {
        PopupMenu pm = new PopupMenu();
        for (GuiSchema.Type t : types) pm.item("Добавить: " + t.title(), iconOf(t.key()), null, () -> addElement(t.key())).color(colorOf(t.key()));
        pm.separator();
        pm.item("Вставить", Icon.COPY, "Ctrl+V", this::paste).enabled(() -> clipboard != null);
        pm.item("Вписать в окно", Icon.SEARCH, null, canvas::fit);
        pm.show(mx, my);
    }

    // ================================================================ layer tree

    /**
     * Layers as a tree, front-most first (like Figma). Click — select (Shift/Ctrl add), eye and lock
     * toggles, arrow folds folders, drag to reorder or drop onto a container to put inside,
     * double click — rename.
     */
    private final class LayerTree extends Widget implements UI.CursorProvider {
        private static final int ROW = 17;

        private record Row(Element e, int depth) {}

        private final List<Row> rows = new ArrayList<>();
        private final Set<String> collapsed = new HashSet<>();
        private double scroll, target;
        private Element pressEl, dragEl;
        private double pressY;
        private int dropRow = -1, dropMode; // 0 above (in front), 1 below (behind), 2 inside

        LayerTree() {
            focusable = true; // rows are built once the document is loaded
        }

        void rebuild() {
            rows.clear();
            flatten(doc.elements, 0);
        }

        private void flatten(List<Element> list, int depth) {
            for (int i = list.size() - 1; i >= 0; i--) {
                Element e = list.get(i);
                rows.add(new Row(e, depth));
                if (e.isContainer() && !collapsed.contains(e.id)) flatten(e.children, depth + 1);
            }
        }

        void reveal(Element e) {
            if (e == null) return;
            boolean changedTree = false;
            for (Element p = doc.parentOf(e); p != null; p = doc.parentOf(p)) changedTree |= collapsed.remove(p.id);
            if (changedTree) rebuild();
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).e() != e) continue;
                int top = i * ROW, view = h;
                if (top < target) target = top;
                else if (top + ROW > target + view) target = top + ROW - view;
            }
        }

        private int maxScroll() {
            return Math.max(0, rows.size() * ROW - h + 4);
        }

        private int rowAt(double my) {
            int i = (int) Math.floor((my - y + scroll) / ROW);
            return i >= 0 && i < rows.size() ? i : -1;
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            target = Math.max(0, Math.min(maxScroll(), target));
            scroll += (target - scroll) * Math.min(1, dt * 18);
            if (Math.abs(target - scroll) < 0.5) scroll = target;
            c.fill(x, y, w, h, Theme.PANEL);
            c.pushClip(x, y, w, h);
            int first = Math.max(0, (int) (scroll / ROW)), last = Math.min(rows.size(), first + h / ROW + 2);
            int hoverRow = isHovered() ? rowAt(my) : -1;
            for (int i = first; i < last; i++) {
                Row r = rows.get(i);
                Element e = r.e();
                int ry = y + i * ROW - (int) scroll;
                boolean sel = selection.contains(e.id);
                if (sel) c.fill(x, ry, w, ROW, e == primary() ? 0x553D6BD8 : 0x333D6BD8);
                else if (i == hoverRow) c.fill(x, ry, w, ROW, Theme.HOVER);
                int ix = x + 6 + r.depth() * 10;
                for (int d = 0; d < r.depth(); d++) c.fill(x + 10 + d * 10, ry, 1, ROW, 0x14FFFFFF);
                if (e.isContainer()) {
                    c.icon(collapsed.contains(e.id) ? Icon.CHEVRON_RIGHT : Icon.CHEVRON_DOWN, ix, ry + 4, Theme.TEXT_MUTED);
                }
                ix += 10;
                boolean vis = e.b("visible");
                c.icon(iconOf(e.type), ix, ry + 4, vis ? colorOf(e.type) : Theme.TEXT_MUTED);
                int tx = ix + 13;
                c.textEllipsized(e.id, tx, ry + 5, w - (tx - x) - 32, vis ? (sel ? Theme.TEXT : Theme.TEXT_DIM) : Theme.TEXT_MUTED);
                // eye and lock toggles (dim unless set or hovered)
                boolean rowHover = i == hoverRow;
                if (!vis || rowHover) {
                    int ex = x + w - 28;
                    c.round(ex, ry + 6, 9, 5, 2, vis ? 0x99FFFFFF : 0x44FFFFFF);
                    c.fill(ex + 4, ry + 7, 2, 3, vis ? 0xFF1C1E26 : 0x44FFFFFF);
                    if (!vis) c.fill(ex - 1, ry + 8, 11, 1, Theme.ERROR);
                }
                if (e.locked || rowHover) c.icon(Icon.FLAG, x + w - 14, ry + 4, e.locked ? Theme.WARNING : 0x55FFFFFF);
            }
            // drop indicator
            if (dragEl != null && dropRow >= 0 && dropRow < rows.size()) {
                Row r = rows.get(dropRow);
                int ry = y + dropRow * ROW - (int) scroll;
                int ix = x + 6 + r.depth() * 10;
                if (dropMode == 2) c.outline(x + 1, ry, w - 2, ROW, Theme.ACCENT);
                else c.fill(ix, dropMode == 0 ? ry : ry + ROW - 1, w - (ix - x) - 4, 2, Theme.ACCENT);
            }
            c.popClip();
            if (rows.isEmpty()) c.textCentered("Нет элементов", x + w / 2, y + 10, Theme.TEXT_MUTED);
        }

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            requestFocus();
            int i = rowAt(my);
            if (i < 0) {
                if (button == 0) select(null);
                return true;
            }
            Row r = rows.get(i);
            Element e = r.e();
            if (button == 1) {
                elementMenu(e, (int) mx, (int) my);
                return true;
            }
            int ix = x + 6 + r.depth() * 10;
            if (e.isContainer() && mx >= ix - 2 && mx < ix + 10) {
                if (!collapsed.remove(e.id)) collapsed.add(e.id);
                rebuild();
                return true;
            }
            if (mx >= x + w - 30 && mx < x + w - 17) {
                toggleVisible(e);
                return true;
            }
            if (mx >= x + w - 16) {
                e.locked = !e.locked;
                changed();
                return true;
            }
            if (shiftDown() || ctrlDown()) toggleSelect(e.id);
            else if (!selection.contains(e.id)) select(e.id);
            pressEl = e;
            pressY = my;
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (pressEl == null) return true;
            if (dragEl == null && Math.abs(my - pressY) > 3) dragEl = pressEl;
            if (dragEl == null) return true;
            int i = rowAt(my);
            if (my < y) target -= 4;
            if (my > y + h) target += 4;
            if (i < 0) {
                dropRow = rows.isEmpty() ? -1 : rows.size() - 1;
                dropMode = 1;
                return true;
            }
            Row r = rows.get(i);
            double local = (my - (y + i * ROW - scroll)) / ROW;
            dropRow = i;
            if (r.e().isContainer() && local > 0.28 && local < 0.72) dropMode = 2;
            else dropMode = local < 0.5 ? 0 : 1;
            if (GuiDoc.contains(dragEl, r.e())) dropRow = -1; // not into itself
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            if (dragEl != null && dropRow >= 0 && dropRow < rows.size()) {
                Element target = rows.get(dropRow).e();
                snapshot();
                List<Element> moving = selection.contains(dragEl.id) ? selectedRoots() : List.of(dragEl);
                for (Element m : moving) {
                    if (GuiDoc.contains(m, target)) continue;
                    if (dropMode == 2) {
                        reparent(m, target.children, target.children.size());
                        collapsed.remove(target.id);
                    } else {
                        List<Element> list = doc.listOf(target);
                        if (list == null) continue;
                        int idx = list.indexOf(target) + (dropMode == 0 ? 1 : 0);
                        reparent(m, list, idx);
                    }
                }
                changed();
            }
            pressEl = null;
            dragEl = null;
            dropRow = -1;
            return true;
        }

        @Override
        public boolean mouseDoubleClicked(double mx, double my, int button) {
            int i = rowAt(my);
            if (i >= 0 && mx < x + w - 30) renameElement(rows.get(i).e());
            return true;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            target = Math.max(0, Math.min(maxScroll(), target - delta * ROW * 2));
            return true;
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            return canvas.keyPressed(key, scan, mods);
        }

        @Override
        public int cursor(int mx, int my) {
            return dragEl != null ? UI.CURSOR_MOVE : UI.CURSOR_HAND;
        }
    }

    // ================================================================ inspector

    private final class Inspector extends Widget {
        private final List<Row> rows = new ArrayList<>();
        private final List<Runnable> refreshers = new ArrayList<>();
        private final Map<String, TextField> fieldsByKey = new HashMap<>();

        abstract class Row extends Widget {
            final String label;

            Row(String label) {
                this.label = label;
                prefH = 20;
            }
        }

        final class Header extends Row {
            Header(String text) {
                super(text);
                prefH = 22;
            }

            @Override
            protected void paint(Canvas c, int mx, int my, float dt) {
                c.fill(x, y + 4, w, 1, Theme.BORDER);
                c.text(label.toUpperCase(), x + 8, y + 9, Theme.TEXT_MUTED);
            }
        }

        final class PropRow extends Row {
            final Widget editor;
            final Widget extra;

            PropRow(String label, String doc, Widget editor, Widget extra) {
                super(label);
                this.editor = add(editor);
                this.extra = extra != null ? add(extra) : null;
                tooltip(doc == null || doc.isEmpty() ? null : doc);
            }

            @Override
            public void layout() {
                int lw = Math.min(92, w * 42 / 100);
                int ex = extra != null ? 20 : 0;
                editor.bounds(x + lw, y + 1, w - lw - 6 - ex, 18);
                if (extra != null) extra.bounds(x + w - 6 - 18, y + 1, 18, 18);
                editor.layout();
            }

            @Override
            protected void paint(Canvas c, int mx, int my, float dt) {
                int lw = Math.min(92, w * 42 / 100);
                c.textEllipsized(label, x + 8, y + 6, lw - 10, Theme.TEXT_DIM);
            }
        }

        void focus(String key) {
            TextField f = fieldsByKey.get(key);
            if (f != null) {
                f.requestFocus();
                f.selectAll();
            }
        }

        void rebuild() {
            clear();
            rows.clear();
            refreshers.clear();
            fieldsByKey.clear();
            Element e = primary();
            if (e == null) {
                addRow(new Header("Интерфейс"));
                String group = null;
                for (GuiSchema.Prop p : GuiSchema.docProps()) {
                    if (!p.group().equals(group)) {
                        group = p.group();
                        if (!group.equals("Интерфейс")) addRow(new Header(group));
                    }
                    addRow(propRow(p, () -> doc.get(p.key()), v -> doc.set(p.key(), v)));
                }
                addRow(new Header("Подсказка"));
                Row hint = new Row("") {
                    @Override
                    protected void paint(Canvas c, int mx, int my, float dt) {
                        c.wrapped("Клик — выбрать (в группе: двойной клик — внутрь, Esc — наружу), Shift/Ctrl — добавить, рамка — выделить несколько.\n"
                                + "Ctrl+G — в папку, Ctrl+Shift+G — разгруппировать, Alt — без привязки.\n"
                                + "Сюжет: gui open \"" + (file != null ? file.name() : "имя") + "\", wait button \"id\".", x + 8, y + 4, w - 16, Theme.TEXT_MUTED, 2);
                    }
                };
                hint.prefH = 92;
                addRow(hint);
            } else {
                GuiSchema.Type t = e.schema();
                int count = selection.size();
                Row head = new Row("") {
                    @Override
                    protected void paint(Canvas c, int mx, int my, float dt) {
                        c.icon(iconOf(t.key()), x + 8, y + 6, colorOf(t.key()));
                        c.text(t.title() + (count > 1 ? "   · выбрано " + count : ""), x + 22, y + 6, Theme.TEXT);
                        c.textEllipsized(count > 1 ? "Изменения применяются ко всем выбранным, у кого есть это свойство" : t.doc(),
                                x + 8, y + 20, w - 16, Theme.TEXT_MUTED);
                    }
                };
                head.prefH = 32;
                addRow(head);
                TextField id = new TextField();
                id.setText(e.id);
                id.onSubmit = v -> commitId(e, v, id);
                addRow(new PropRow("id", "Имя элемента для сюжета: gui text \"" + e.id + "\" \"...\". Enter — применить", id, null));
                String group = null;
                for (GuiSchema.Prop p : t.props()) {
                    if (!p.group().equals(group)) {
                        group = p.group();
                        addRow(new Header(group));
                    }
                    String key = p.key();
                    addRow(propRow(p, () -> e.get(key), v -> {
                        for (Element s : selected()) if (s.schema().prop(key) != null) s.set(key, v);
                    }));
                }
            }
            int hsum = 4;
            for (Row r : rows) hsum += r.prefH;
            prefH = hsum + 10;
            GuiTab.this.layout();
        }

        private void commitId(Element e, String v, TextField field) {
            v = v.trim();
            Element other = doc.element(v);
            UI ui = UI.get();
            if (v.isEmpty() || (other != null && other != e)) {
                if (ui != null) ui.toastError(v.isEmpty() ? "Пустой id" : "Такой id уже есть");
                field.setText(e.id);
                return;
            }
            snapshot();
            selection.remove(e.id);
            e.id = v;
            selection.add(v);
            changed();
        }

        private void addRow(Row r) {
            rows.add(r);
            add(r);
        }

        /** Applies an edit; consecutive edits of the same property share one undo step. */
        private void apply(String key, Consumer<String> setter, String value) {
            if (!key.equals(editingKey)) {
                snapshot();
                editingKey = key;
            }
            setter.accept(value);
            changed();
        }

        private Row propRow(GuiSchema.Prop p, Supplier<String> getter, Consumer<String> setter) {
            String key = p.key();
            switch (p.kind()) {
                case BOOL -> {
                    Checkbox cb = new Checkbox("", () -> GuiDoc.toBool(getter.get()), v -> apply(key + System.nanoTime(), setter, v ? "true" : "false"));
                    return new PropRow(p.label(), p.doc(), cb, null);
                }
                case CHOICE -> {
                    Dropdown<String> dd = new Dropdown<>(p::choices, getter, s -> s, v -> apply(key + System.nanoTime(), setter, v));
                    return new PropRow(p.label(), p.doc(), dd, null);
                }
                case COLOR -> {
                    TextField tf = field(key, getter, setter);
                    Widget swatch = new Widget() {
                        @Override
                        protected void paint(Canvas c, int mx, int my, float dt) {
                            c.fill(x + 1, y + 1, w - 2, h - 2, 0xFF888888);
                            c.fill(x + 1, y + 1, (w - 2) / 2, (h - 2) / 2, 0xFFBBBBBB);
                            c.fill(x + 1 + (w - 2) / 2, y + 1 + (h - 2) / 2, w - 2 - (w - 2) / 2, h - 2 - (h - 2) / 2, 0xFFBBBBBB);
                            c.fill(x + 1, y + 1, w - 2, h - 2, GuiDoc.toColor(getter.get()));
                            c.outline(x, y, w, h, isHovered() ? Theme.ACCENT : Theme.BORDER_STRONG);
                        }

                        @Override
                        public boolean mousePressed(double mx, double my, int button) {
                            snapshot();
                            editingKey = key + ":picker";
                            ColorPicker.open(GuiDoc.toColor(getter.get()), x - 150, y + h + 2, v -> {
                                setter.accept(GuiDoc.colorString(v));
                                tf.setText(getter.get());
                                changed();
                            });
                            return true;
                        }
                    };
                    swatch.tooltip("Палитра");
                    return new PropRow(p.label(), p.doc(), tf, swatch);
                }
                case IMAGE -> {
                    TextField tf = field(key, getter, setter);
                    Button pick = Button.icon(Icon.IMAGE, "Картинка проекта", () -> {
                        PopupMenu pm = new PopupMenu();
                        List<String> names = AssetIndex.projectImages.get();
                        if (names.isEmpty()) pm.item("В проекте нет картинок — добавьте в «Картинки»", null).enabled(() -> false);
                        for (String n : names) pm.item(n, Icon.IMAGE, null, () -> {
                            apply(key + System.nanoTime(), setter, n);
                            tf.setText(n);
                        }).checked(() -> getter.get().equals(n));
                        pm.separator();
                        pm.item("Без картинки", Icon.CLOSE, null, () -> {
                            apply(key + System.nanoTime(), setter, "");
                            tf.setText("");
                        });
                        UI ui = UI.get();
                        pm.show(ui != null ? ui.mouseX() - 120 : 0, ui != null ? ui.mouseY() + 8 : 0);
                    });
                    return new PropRow(p.label(), p.doc(), tf, pick);
                }
                case ENTITY, ANIM, SOUND, ITEM -> {
                    TextField tf = field(key, getter, setter);
                    ParamType pt = switch (p.kind()) {
                        case ENTITY -> ParamType.ENTITY;
                        case ANIM -> ParamType.ANIM;
                        case ITEM -> ParamType.ITEM;
                        default -> ParamType.SOUND;
                    };
                    Button pick = Button.icon(Icon.SEARCH, "Выбрать…", () -> AssetPicker.open(pt, getter.get(), v -> {
                        apply(key + System.nanoTime(), setter, v);
                        tf.setText(v);
                    }));
                    return new PropRow(p.label(), p.doc(), tf, pick);
                }
                default -> {
                    return new PropRow(p.label(), p.doc(), field(key, getter, setter), null);
                }
            }
        }

        private TextField field(String key, Supplier<String> getter, Consumer<String> setter) {
            TextField tf = new TextField();
            tf.setText(getter.get());
            tf.onChange = v -> apply(key, setter, v);
            fieldsByKey.put(key, tf);
            refreshers.add(() -> {
                if (!tf.isFocused() && !tf.text().equals(getter.get())) tf.setText(getter.get());
            });
            return tf;
        }

        @Override
        public void tick() {
            super.tick();
            for (Runnable r : refreshers) r.run();
        }

        @Override
        public void layout() {
            int yy = y + 2;
            for (Row r : rows) {
                r.bounds(x, yy, w, r.prefH);
                r.layout();
                yy += r.prefH;
            }
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.fill(x, y, w, Math.max(h, prefH), Theme.PANEL);
        }
    }

    // ================================================================ layout

    @Override
    public void layout() {
        toolbar.bounds(x, y, w, TOOLBAR);
        toolbar.layout();
        int top = y + TOOLBAR;
        int bodyH = h - TOOLBAR;
        int paletteH = Math.min(types.size() * 16 + 4, bodyH * 2 / 5);
        palette.bounds(x, top + 16, LEFT_W, paletteH);
        layers.bounds(x, top + 16 + paletteH + 16, LEFT_W, bodyH - paletteH - 32);
        canvas.bounds(x + LEFT_W + 1, top, w - LEFT_W - RIGHT_W - 2, bodyH);
        inspectorScroll.bounds(x + w - RIGHT_W, top, RIGHT_W, bodyH);
        super.layout();
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.fill(x, y, w, h, Theme.PANEL);
        int top = y + TOOLBAR;
        c.fill(x, top - 1, w, 1, Theme.BORDER);
        c.fill(x + LEFT_W, top, 1, h - TOOLBAR, Theme.BORDER);
        c.fill(x + w - RIGHT_W - 1, top, 1, h - TOOLBAR, Theme.BORDER);
        c.text("ЭЛЕМЕНТЫ", x + 8, top + 5, Theme.TEXT_MUTED);
        c.text("СЛОИ  ·  сверху — ближе", x + 8, palette.y + palette.h + 5, Theme.TEXT_MUTED);
    }

    // ================================================================ EditorTab

    public List<String> elementIds() {
        return doc.ids();
    }

    public String title() {
        return ownTitle != null ? ownTitle : file != null ? file.name() : "Интерфейс";
    }

    private void save0() {
        try {
            save();
        } catch (IOException e) {
            UI ui = UI.get();
            if (ui != null) ui.toastError("Ошибка сохранения: " + e.getMessage());
        }
    }

    @Override
    public Icon icon() {
        return Icon.MENU;
    }

    @Override
    public int iconColor() {
        return 0xFF5DA9F6;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void save() throws IOException {
        if (file == null) {
            if (ownSave != null) ownSave.run();
        } else project.write(file, doc.toJson());
        dirty = false;
        if (onDirtyChanged != null) onDirtyChanged.run();
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
        canvas.requestFocus();
    }
}
