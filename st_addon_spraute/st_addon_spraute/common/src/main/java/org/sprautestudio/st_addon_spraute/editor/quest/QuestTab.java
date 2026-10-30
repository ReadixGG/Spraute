package org.sprautestudio.st_addon_spraute.editor.quest;

import org.sprautestudio.st_addon_spraute.editor.assets.AssetPicker;
import org.sprautestudio.st_addon_spraute.editor.project.Project;
import org.sprautestudio.st_addon_spraute.editor.tabs.EditorTab;
import org.sprautestudio.st_addon_spraute.lang.Analyzer;
import org.sprautestudio.st_addon_spraute.lang.ParamType;
import org.sprautestudio.st_addon_spraute.quest.QuestDef;
import org.sprautestudio.st_addon_spraute.quest.QuestJournal;
import org.sprautestudio.st_addon_spraute.quest.client.QuestScreen;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;
import org.sprautestudio.st_addon_spraute.ui.widgets.Button;
import org.sprautestudio.st_addon_spraute.ui.widgets.Checkbox;
import org.sprautestudio.st_addon_spraute.ui.widgets.ScrollView;
import org.sprautestudio.st_addon_spraute.ui.widgets.TextField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Quest editor: the tasks on the left, the journal as the player will see it in the middle,
 * the settings of the quest or of the chosen task on the right.
 */
public class QuestTab extends EditorTab {
    private QuestDef doc;
    private String saved;
    /** What the inspector shows: "" — the quest, else a task id. */
    private String sel = "";

    private final Form form = new Form();
    private final ScrollView inspector;
    private final TaskList list = new TaskList();
    private final Preview preview = new Preview();
    private final List<Button> tools = new ArrayList<>();
    private QuestScreen journal;

    public QuestTab(Project project, Project.FileRef file, String content) {
        super(project, file);
        doc = QuestDef.fromJson(content);
        saved = doc.toJson();
        add(list);
        add(preview);
        inspector = add(new ScrollView(form));
        tool("Задача", Icon.PLUS, Button.Style.PRIMARY, "Новая задача квеста", this::addTask);
        tool("Выше", Icon.CHEVRON_UP, Button.Style.GHOST, "Поднять задачу", () -> move(-1));
        tool("Ниже", Icon.CHEVRON_DOWN, Button.Style.GHOST, "Опустить задачу", () -> move(1));
        tool("Удалить", Icon.TRASH, Button.Style.GHOST, "Удалить выбранную задачу", this::delete);
        tool("Квест", Icon.GEAR, Button.Style.GHOST, "Название, описание и награда", () -> select(""));
        changed();
        buildForm();
    }

    private void tool(String text, Icon icon, Button.Style style, String tip, Runnable r) {
        Button b = add(new Button(text, r).icon(icon).style(style));
        b.tooltip(tip);
        b.autoWidth();
        tools.add(b);
    }

    private QuestDef.Task task() {
        return sel.isEmpty() ? null : doc.task(sel);
    }

    private void select(String s) {
        sel = s;
        buildForm();
    }

    private String key() {
        return "preview/" + (file != null ? file.name() : "quest");
    }

    /** Shows the change in the journal at once. */
    private void changed() {
        doc.fix();
        QuestJournal j = QuestJournal.preview(doc, key());
        if (journal == null) journal = new QuestScreen(j);
        else journal.update(j);
    }

    private void addTask() {
        QuestDef.Task t = new QuestDef.Task();
        String id = "task";
        int n = 2;
        while (doc.task(id) != null) id = "task" + n++;
        t.id = id;
        QuestDef.Task cur = task();
        doc.tasks.add(cur != null ? doc.tasks.indexOf(cur) + 1 : doc.tasks.size(), t);
        changed();
        select(id);
    }

    private void move(int d) {
        QuestDef.Task t = task();
        if (t == null) return;
        int i = doc.tasks.indexOf(t), j = i + d;
        if (j < 0 || j >= doc.tasks.size()) return;
        doc.tasks.remove(i);
        doc.tasks.add(j, t);
        changed();
    }

    private void delete() {
        QuestDef.Task t = task();
        if (t == null) return;
        doc.tasks.remove(t);
        changed();
        select("");
    }

    // ================================================================== inspector

    private void buildForm() {
        form.clear();
        QuestDef.Task t = task();
        if (t != null) taskForm(t);
        else questForm();
        if (w > 0) layout();
    }

    private String name() {
        return file != null ? file.name() : "квест";
    }

    private void moveSection(QuestDef.Section s, int d) {
        int i = doc.layout.indexOf(s), j = i + d;
        if (i < 0 || j < 0 || j >= doc.layout.size()) return;
        java.util.Collections.swap(doc.layout, i, j);
        changed();
        buildForm();
    }

    private void questForm() {
        form.header("Квест");
        form.text("Название", null, () -> doc.title, v -> doc.title = v);
        form.text("Кто выдал", "Пусто — не показывать", () -> doc.giver, v -> doc.giver = v);
        form.text("Описание", "История квеста в журнале", () -> doc.description, v -> doc.description = v);
        form.check("По одной задаче", "Следующая задача появляется, когда сделана предыдущая", () -> doc.sequential, v -> doc.sequential = v);
        form.check("Завершать сам", "Квест выполнен (и награда выдана), как только сделаны все обязательные задачи", () -> doc.autoComplete,
                v -> doc.autoComplete = v);
        form.header("Страница в журнале");
        form.note("Порядок разделов сверху вниз. ▲▼ — переставить, ✕ — убрать со страницы");
        for (int i = 0; i < doc.layout.size(); i++) form.section(i);
        form.button("+ свой текст", () -> {
            QuestDef.Section s = new QuestDef.Section("text");
            s.text = "Новый текст";
            doc.layout.add(s);
            changed();
            buildForm();
        });
        for (String k : new String[]{"description", "tasks", "reward"}) {
            if (doc.layout.stream().anyMatch(s -> s.kind.equals(k))) continue;
            form.button("+ вернуть: " + sectionTitle(k).toLowerCase(), () -> {
                doc.layout.add(new QuestDef.Section(k));
                changed();
                buildForm();
            });
        }
        form.header("Награда");
        if (doc.layout.stream().noneMatch(s -> s.kind.equals("reward")))
            form.note("Раздел награды скрыт со страницы, но награда всё равно выдаётся");
        form.integer("Монеты", null, () -> doc.reward.coins, v -> doc.reward.coins = Math.max(0, v));
        form.integer("Опыт", null, () -> doc.reward.xp, v -> doc.reward.xp = Math.max(0, v));
        for (int i = 0; i < doc.reward.items.size(); i++) {
            QuestDef.Item item = doc.reward.items.get(i);
            form.rewardItem(i);
            form.text("NBT", "Необязательно. Как в командах: {Damage:5,display:{Name:'{\"text\":\"Меч\"}'}}", () -> org.sprautestudio.st_addon_spraute.runtime.ItemSpec.nbt(item.item), v -> item.item = org.sprautestudio.st_addon_spraute.runtime.ItemSpec.with(item.item, v));
        }
        form.button("+ предмет в награду", () -> {
            doc.reward.items.add(new QuestDef.Item("minecraft:emerald", 1));
            changed();
            buildForm();
        });
        form.text("Своя строка", "Как описать награду в журнале (пусто — составится сама)", () -> doc.reward.text, v -> doc.reward.text = v);
        form.header("В сюжете");
        form.note("quest start \"" + name() + "\"");
        form.note("quest progress \"" + name() + "\" \"zombies\"   (+1)");
        form.note("quest done \"" + name() + "\"   ·   quest fail \"" + name() + "\"");
        form.note("if queststate(\"" + name() + "\") == \"completed\" { … }");
        form.note("on questdone \"" + name() + "\" { … }");
        form.note("Выберите задачу слева, чтобы править её");
    }

    private void taskForm(QuestDef.Task t) {
        form.header("Задача");
        form.text("Id", "Имя для сюжета: quest progress \"" + name() + "\" \"id\"", () -> t.id, v -> {
            String nv = v.trim();
            if (nv.isEmpty() || (doc.task(nv) != null && doc.task(nv) != t)) return;
            t.id = nv;
            sel = nv;
        });
        form.text("Текст", "Как задача видна в журнале", () -> t.text, v -> t.text = v);
        form.integer("Сколько раз", "1 — просто сделать; 5 — счётчик 0/5", () -> t.count, v -> t.count = Math.max(1, v));
        form.check("Необязательная", "Квест можно выполнить и без неё", () -> t.optional, v -> t.optional = v);
        form.check("Скрытая", "Не видна, пока сюжет её не откроет (quest reveal) или она не сдвинется", () -> t.hidden, v -> t.hidden = v);
        form.header("В сюжете");
        form.note("quest progress \"" + name() + "\" \"" + t.id + "\"" + (t.count > 1 ? " 2" : ""));
        form.note("quest set \"" + name() + "\" \"" + t.id + "\" " + t.count + "   (выполнить)");
        if (t.hidden) form.note("quest reveal \"" + name() + "\" \"" + t.id + "\"");
        form.note("questprogress(\"" + name() + "\", \"" + t.id + "\") — сколько сделано");
    }

    private static final int LABEL_W = 122;

    private static String sectionTitle(String kind) {
        return switch (kind) {
            case "description" -> "Описание";
            case "tasks" -> "Задачи";
            case "reward" -> "Награда";
            default -> "Текст";
        };
    }

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

        void integer(String label, String doc, IntSupplier get, IntConsumer set) {
            Row r = row(label, doc);
            TextField f = new TextField();
            f.setText(String.valueOf(get.getAsInt()));
            f.filter = ch -> Character.isDigit(ch) || ch == '-';
            f.onChange = s -> {
                try {
                    set.accept(Integer.parseInt(s.trim()));
                    changed();
                } catch (NumberFormatException ignored) {
                }
            };
            r.editor = r.add(f);
        }

        void check(String label, String doc, BooleanSupplier get, Consumer<Boolean> set) {
            Row r = row(label, doc);
            r.editor = r.add(new Checkbox("", get, v -> {
                set.accept(v);
                changed();
                buildForm();
            }));
        }

        void rewardItem(int i) {
            QuestDef.Item st = doc.reward.items.get(i);
            Row r = row("Предмет " + (i + 1), null);
            r.editor = r.add(new Widget() {
                final Button pick = add(new Button(() -> org.sprautestudio.st_addon_spraute.runtime.ItemSpec.id(st.item).replace("minecraft:", ""), () -> AssetPicker.open(ParamType.ITEM, org.sprautestudio.st_addon_spraute.runtime.ItemSpec.id(st.item), v -> {
                    st.item = org.sprautestudio.st_addon_spraute.runtime.ItemSpec.with(v, org.sprautestudio.st_addon_spraute.runtime.ItemSpec.nbt(st.item));
                    changed();
                })).icon(Icon.CUBE).style(Button.Style.GHOST));
                final TextField n = add(new TextField());
                final Button del = add(Button.icon(Icon.CLOSE, "Убрать", () -> {
                    doc.reward.items.remove(st);
                    changed();
                    buildForm();
                }));

                {
                    n.setText(String.valueOf(st.count));
                    n.filter = Character::isDigit;
                    n.onChange = s -> {
                        try {
                            st.count = Math.max(1, Integer.parseInt(s.trim()));
                            changed();
                        } catch (NumberFormatException ignored) {
                        }
                    };
                }

                @Override
                public void layout() {
                    pick.bounds(x, y, w - 64, h);
                    n.bounds(x + w - 60, y, 36, h);
                    del.bounds(x + w - 20, y, 20, h);
                    super.layout();
                }
            });
        }

        /** One part of the journal page: its text (for own texts), heading, and buttons to move and drop it. */
        void section(int i) {
            QuestDef.Section s = doc.layout.get(i);
            Row r = row((i + 1) + ". " + sectionTitle(s.kind), s.kind.equals("text") ? "Свой текст на странице квеста" : null);
            r.editor = r.add(new Widget() {
                final TextField f = add(new TextField());
                final Button up = add(Button.icon(Icon.CHEVRON_UP, "Выше", () -> moveSection(s, -1)));
                final Button down = add(Button.icon(Icon.CHEVRON_DOWN, "Ниже", () -> moveSection(s, 1)));
                final Button del = add(Button.icon(Icon.CLOSE, s.kind.equals("text") ? "Удалить текст" : "Убрать со страницы", () -> {
                    doc.layout.remove(s);
                    changed();
                    buildForm();
                }));

                {
                    boolean own = s.kind.equals("text");
                    f.placeholder(own ? "Текст" : s.kind.equals("description") ? "Заголовок (пусто — без него)" : "Заголовок (пусто — " + sectionTitle(s.kind).toUpperCase() + ")");
                    f.setText(own ? s.text : s.header);
                    f.onChange = v -> {
                        if (own) s.text = v;
                        else s.header = v;
                        changed();
                    };
                }

                @Override
                public void layout() {
                    f.bounds(x, y, w - 66, h);
                    up.bounds(x + w - 62, y, 20, h);
                    down.bounds(x + w - 41, y, 20, h);
                    del.bounds(x + w - 20, y, 20, h);
                    super.layout();
                }
            });
            if (s.kind.equals("text")) {
                text("   заголовок", "Над текстом (пусто — без заголовка)", () -> s.header, v -> s.header = v);
                text("   цвет", "Например #FFD25A (пусто — обычный)", () -> s.color, v -> s.color = v);
            }
        }

        void button(String text, Runnable r) {
            Row row = row("", null);
            row.editor = row.add(new Button(text, r).style(Button.Style.GHOST));
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

    // ================================================================== list of tasks

    private final class TaskList extends Widget implements UI.CursorProvider {
        private static final int ROW = 18;

        private int rowAt(double my) {
            return (int) Math.floor((my - y - 4) / ROW);
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.fill(x, y, w, h, Theme.PANEL);
            c.fill(x + w - 1, y, 1, h, Theme.BORDER);
            int ry = y + 4;
            boolean hov0 = isHovered() && rowAt(my) == 0;
            c.round(x + 4, ry, w - 8, ROW - 1, 3, sel.isEmpty() ? Theme.ACCENT_DIM : hov0 ? Theme.RAISED : 0);
            c.icon(Icon.FLAG, x + 8, ry + 5, 0xFFFFD25A);
            c.textEllipsized(doc.title, x + 22, ry + 5, w - 30, Theme.TEXT);
            ry += ROW;
            for (int i = 0; i < doc.tasks.size(); i++) {
                QuestDef.Task t = doc.tasks.get(i);
                boolean on = t.id.equals(sel), hov = isHovered() && rowAt(my) == i + 1;
                c.round(x + 4, ry, w - 8, ROW - 1, 3, on ? Theme.ACCENT_DIM : hov ? Theme.RAISED : 0);
                c.fill(x + 14, ry + 5, 3, 8, t.hidden ? 0xFF6E86FF : t.optional ? 0xFF8C91A8 : 0xFF7BE08A);
                c.textEllipsized(t.text, x + 22, ry + 5, w - 52, Theme.TEXT_DIM);
                if (t.count > 1) {
                    String n = "×" + t.count;
                    c.text(n, x + w - 8 - c.width(n), ry + 5, Theme.TEXT_MUTED);
                }
                ry += ROW;
            }
        }

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            int i = rowAt(my);
            if (i == 0) select("");
            else if (i > 0 && i <= doc.tasks.size()) select(doc.tasks.get(i - 1).id);
            return true;
        }

        @Override
        public int cursor(int mx, int my) {
            return UI.CURSOR_HAND;
        }
    }

    // ================================================================== the journal, live

    private final class Preview extends Widget {
        float lastK = 1, lastX, lastY;

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.gradient(x, y, w, h, 0xFF1D2030, 0xFF0D0E14);
            c.flush();
            float k = Math.min(1.5f, Math.min((w - 16) / (float) QuestScreen.W, (h - 40) / (float) QuestScreen.H));
            var g = c.g;
            float ox = x + (w - QuestScreen.W * k) / 2f, oy = y + 10 + (h - 40 - QuestScreen.H * k) / 2f;
            g.pose().pushPose();
            g.pose().translate(ox, oy, 0);
            try {
                journal.paintPreview(g, k, mx - ox, my - oy);
            } catch (Throwable ignored) {
            }
            g.flush();
            g.pose().popPose();
            c.textEllipsized("Так квест выглядит в журнале (J): первая задача сделана для примера. Щёлкните название — отслеживать", x + 8, y + h - 14,
                    w - 16, Theme.TEXT_MUTED);
            lastK = k;
            lastX = ox;
            lastY = oy;
        }

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            journal.clickPreview(mx - lastX, my - lastY);
            return true;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            journal.scrollPreview(mx - lastX, my - lastY, delta);
            return true;
        }
    }

    // ================================================================== layout and tab

    private static final int LIST_W = 190, INSPECTOR_W = 330;
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
        preview.bounds(x + lw, y + toolbarH, w - lw - iw, h - toolbarH);
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
        return file != null ? file.name() : "Квест";
    }

    @Override
    public Icon icon() {
        return Icon.FLAG;
    }

    @Override
    public int iconColor() {
        return 0xFFFFD25A;
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
