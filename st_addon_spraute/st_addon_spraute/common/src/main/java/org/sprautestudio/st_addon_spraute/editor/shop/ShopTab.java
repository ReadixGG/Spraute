package org.sprautestudio.st_addon_spraute.editor.shop;

import net.minecraft.client.Minecraft;
import org.sprautestudio.st_addon_spraute.editor.assets.AssetPicker;
import org.sprautestudio.st_addon_spraute.editor.project.Project;
import org.sprautestudio.st_addon_spraute.editor.tabs.EditorTab;
import org.sprautestudio.st_addon_spraute.engine.client.EngineClient;
import org.sprautestudio.st_addon_spraute.lang.Analyzer;
import org.sprautestudio.st_addon_spraute.lang.ParamType;
import org.sprautestudio.st_addon_spraute.shop.ShopDef;
import org.sprautestudio.st_addon_spraute.shop.ShopView;
import org.sprautestudio.st_addon_spraute.shop.client.ShopPanel;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;
import org.sprautestudio.st_addon_spraute.ui.widgets.Button;
import org.sprautestudio.st_addon_spraute.ui.widgets.Checkbox;
import org.sprautestudio.st_addon_spraute.ui.widgets.Dropdown;
import org.sprautestudio.st_addon_spraute.ui.widgets.ScrollView;
import org.sprautestudio.st_addon_spraute.ui.widgets.TextField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Shop editor: tabs and offers on the left, the shop window as the player will see it in the middle
 * (click its tabs and offers to edit them), the settings of the shop, a tab or an offer on the right.
 */
public class ShopTab extends EditorTab {
    private ShopDef doc;
    private String saved;
    /** What the inspector shows: "" — the shop, "tab:ID", "offer:ID". */
    private String sel = "";

    private final Form form = new Form();
    private final ScrollView inspector;
    private final OfferList list = new OfferList();
    private final Preview preview = new Preview();
    private final List<Button> tools = new ArrayList<>();
    private final ShopPanel panel;

    public ShopTab(Project project, Project.FileRef file, String content) {
        super(project, file);
        doc = ShopDef.fromJson(content);
        saved = doc.toJson();
        panel = new ShopPanel(ShopView.preview(doc));
        add(list);
        add(preview);
        inspector = add(new ScrollView(form));
        tool("Товар", Icon.PLUS, Button.Style.PRIMARY, "Новый товар во вкладке, где вы сейчас", this::addOffer);
        tool("Копия", Icon.COPY, Button.Style.GHOST, "Копия выбранного товара", this::duplicate);
        tool("Удалить", Icon.TRASH, Button.Style.GHOST, "Удалить выбранный товар или вкладку", this::delete);
        tool("Магазин", Icon.GEAR, Button.Style.GHOST, "Название и деньги магазина", () -> select(""));
        buildForm();
    }

    private void tool(String text, Icon icon, Button.Style style, String tip, Runnable r) {
        Button b = add(new Button(text, r).icon(icon).style(style));
        b.tooltip(tip);
        b.autoWidth();
        tools.add(b);
    }

    // ================================================================== selection and changes

    private ShopDef.Offer offer() {
        return sel.startsWith("offer:") ? doc.offer(sel.substring(6)) : null;
    }

    private ShopDef.Tab tab() {
        if (!sel.startsWith("tab:")) return null;
        for (ShopDef.Tab t : doc.tabs) if (t.id.equals(sel.substring(4))) return t;
        return null;
    }

    private void select(String s) {
        sel = s;
        ShopDef.Offer o = offer();
        if (o != null) panel.selected = o.id;
        buildForm();
    }

    /** Shows the change in the window at once. */
    private void changed() {
        doc.fix();
        panel.setView(ShopView.preview(doc));
    }

    private String uniqueId(String base) {
        String id = base;
        int n = 2;
        while (doc.offer(id) != null) id = base + n++;
        return id;
    }

    private String currentTab() {
        ShopDef.Offer o = offer();
        if (o != null) return o.tab;
        ShopDef.Tab t = tab();
        if (t != null) return t.id;
        return doc.tabs.get(0).id;
    }

    private void addOffer() {
        ShopDef.Offer o = new ShopDef.Offer();
        o.id = uniqueId("item");
        o.tab = currentTab();
        doc.offers.add(o);
        changed();
        select("offer:" + o.id);
    }

    private void addTab() {
        String id = "tab";
        int n = 2;
        while (true) {
            String cand = id;
            if (doc.tabs.stream().noneMatch(t -> t.id.equals(cand))) break;
            id = "tab" + n++;
        }
        doc.tabs.add(new ShopDef.Tab(id, "Вкладка " + doc.tabs.size(), "minecraft:chest"));
        changed();
        select("tab:" + id);
    }

    private void duplicate() {
        ShopDef.Offer o = offer();
        if (o == null) return;
        ShopDef tmp = new ShopDef();
        tmp.offers.add(o);
        ShopDef.Offer c = ShopDef.fromJson(tmp.toJson()).offers.get(0);
        c.id = uniqueId(o.id);
        doc.offers.add(doc.offers.indexOf(o) + 1, c);
        changed();
        select("offer:" + c.id);
    }

    private void delete() {
        ShopDef.Offer o = offer();
        if (o != null) {
            doc.offers.remove(o);
            changed();
            select("");
            return;
        }
        ShopDef.Tab t = tab();
        if (t != null && doc.tabs.size() > 1) {
            doc.tabs.remove(t);
            String to = doc.tabs.get(0).id;
            for (ShopDef.Offer x : doc.offers) if (x.tab.equals(t.id)) x.tab = to;
            changed();
            select("");
        }
    }

    // ================================================================== inspector

    private static final List<String> TYPES = List.of("buy", "sell", "trade");
    private static final List<String> RESTOCK = List.of("never", "daily");

    private static String typeTitle(String t) {
        return switch (t) {
            case "sell" -> "игрок продаёт (скупка)";
            case "trade" -> "обмен предметов";
            default -> "игрок покупает";
        };
    }

    private List<String> currencies() {
        List<String> out = new ArrayList<>(List.of("coins", "minecraft:emerald", "minecraft:gold_ingot", "minecraft:diamond"));
        if (!out.contains(doc.currency)) out.add(doc.currency);
        return out;
    }

    private void buildForm() {
        form.clear();
        ShopDef.Offer o = offer();
        ShopDef.Tab t = tab();
        if (o != null) offerForm(o);
        else if (t != null) tabForm(t);
        else shopForm();
        if (w > 0) layout();
    }

    private void shopForm() {
        form.header("Магазин");
        form.text("Название", "Заголовок окна", () -> doc.title, v -> doc.title = v);
        form.choice("Деньги", "Монеты движка или любой предмет как деньги", this::currencies, () -> doc.currency,
                v -> v.equals("coins") ? "монеты движка" : v.replace("minecraft:", ""), v -> doc.currency = v);
        form.item("Свой предмет-деньги", () -> doc.currency.equals("coins") ? "" : doc.currency, v -> doc.currency = v);
        form.text("Как называть", "«монет», «изумрудов»… (пусто — само)", () -> doc.currencyName, v -> doc.currencyName = v);
        form.note("В сюжете: shop open \"" + (file != null ? file.name() : "имя") + "\" with торговец");
        form.note("Покупки: on purchase \"" + (file != null ? file.name() : "имя") + "\" { event(\"offer\") }");
        form.note("Выберите товар слева (или щёлкните его в окне)");
    }

    private void tabForm(ShopDef.Tab t) {
        form.header("Вкладка");
        form.text("Название", null, () -> t.name, v -> t.name = v);
        form.item("Значок", () -> t.icon, v -> t.icon = v);
        form.text("Id", "Для товаров (поле «Вкладка»)", () -> t.id, v -> {
            String nv = v.trim();
            if (nv.isEmpty()) return;
            for (ShopDef.Offer x : doc.offers) if (x.tab.equals(t.id)) x.tab = nv;
            t.id = nv;
            sel = "tab:" + nv;
        });
    }

    private void offerForm(ShopDef.Offer o) {
        form.header("Товар");
        form.text("Id", "Имя товара для сюжета: bought(\"лавка\", \"id\")", () -> o.id, v -> {
            String nv = v.trim();
            if (nv.isEmpty() || (doc.offer(nv) != null && doc.offer(nv) != o)) return;
            o.id = nv;
            sel = "offer:" + nv;
        });
        form.choice("Сделка", null, () -> TYPES, () -> o.type, ShopTab::typeTitle, v -> {
            o.type = v;
            buildForm();
        });
        form.item("Предмет", () -> o.item, v -> o.item = v);
        form.integer("Сколько штук", "За одну сделку", () -> o.count, v -> o.count = Math.max(1, v));
        form.text("Название", "Пусто — название предмета", () -> o.name, v -> o.name = v);
        form.text("Описание", null, () -> o.description, v -> o.description = v);
        form.header(o.type.equals("sell") ? "Сколько платит торговец" : "Цена");
        form.integer(o.type.equals("trade") ? "Плюс денег" : "Денег", o.type.equals("trade") ? "0 — только предметы" : null,
                () -> o.price, v -> o.price = Math.max(0, v));
        form.number("Скидка (доля)", "1 — полная цена, 0.8 — скидка 20%", () -> o.discount, v -> o.discount = (float) Math.max(0.01, v));
        form.number("Рост цены", "Каждая покупка дороже на эту долю: 0.25 — +25%", () -> o.priceGrowth, v -> o.priceGrowth = (float) Math.max(0, v));
        form.note(o.type.equals("sell") ? "Предметы, которые торговец берёт сверху:" : "Предметы, которые нужно отдать:");
        for (int i = 0; i < o.costs.size(); i++) {
            ShopDef.Stack cost = o.costs.get(i);
            form.cost(o, i);
            form.nbt(() -> cost.item, v -> cost.item = v);
        }
        form.button("+ предмет в цену", () -> {
            o.costs.add(new ShopDef.Stack("minecraft:emerald", 1));
            changed();
            buildForm();
        });
        form.header("Запас и условия");
        form.integer("Запас", "Сколько раз можно купить, -1 — без конца", () -> o.stock, v -> o.stock = Math.max(-1, v));
        if (o.stock >= 0) form.choice("Пополняется", null, () -> RESTOCK, () -> o.restock,
                v -> v.equals("daily") ? "каждый новый день" : "только командой shop restock", v -> o.restock = v);
        form.integer("Лимит на игрока", "-1 — без лимита", () -> o.limit, v -> o.limit = Math.max(-1, v));
        form.text("Виден, если", "Выражение сюжета: flag(\"met\"), level >= 3 (пусто — всегда)", () -> o.condition, v -> o.condition = v.trim());
        form.text("Открыт, если", "Пока неверно — товар виден, но закрыт", () -> o.unlock, v -> o.unlock = v.trim());
        if (!o.unlock.isEmpty()) form.text("Почему закрыт", null, () -> o.lockText, v -> o.lockText = v);
    }

    private static final int LABEL_W = 122;

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

        void choice(String label, String doc, Supplier<List<String>> options, Supplier<String> get, java.util.function.Function<String, String> labeler,
                    Consumer<String> set) {
            Row r = row(label, doc);
            r.editor = r.add(new Dropdown<>(options, get, labeler, v -> {
                set.accept(v);
                changed();
            }));
        }

        /** An item: its id on a button that opens the visual item picker. */
        void item(String label, Supplier<String> get, Consumer<String> set) {
            Row r = row(label, "Нажмите, чтобы выбрать предмет");
            r.editor = r.add(new Button(() -> get.get().isEmpty() ? "выбрать…" : org.sprautestudio.st_addon_spraute.runtime.ItemSpec.id(get.get()).replace("minecraft:", ""),
                    () -> AssetPicker.open(ParamType.ITEM, org.sprautestudio.st_addon_spraute.runtime.ItemSpec.id(get.get()), v -> {
                        set.accept(org.sprautestudio.st_addon_spraute.runtime.ItemSpec.with(v, org.sprautestudio.st_addon_spraute.runtime.ItemSpec.nbt(get.get())));
                        changed();
                        buildForm();
                    })).icon(Icon.CUBE).style(Button.Style.GHOST));
            if (!get.get().isEmpty()) nbt(get, set);
        }

        /** The item's NBT, kept in the same text as its id. */
        void nbt(Supplier<String> get, Consumer<String> set) {
            text("NBT", "Необязательно. Как в командах: {Damage:5,display:{Name:'{\"text\":\"Меч\"}'}}", () -> org.sprautestudio.st_addon_spraute.runtime.ItemSpec.nbt(get.get()), v -> set.accept(org.sprautestudio.st_addon_spraute.runtime.ItemSpec.with(get.get(), v)));
        }

        void cost(ShopDef.Offer o, int i) {
            ShopDef.Stack st = o.costs.get(i);
            Row r = row("Предмет " + (i + 1), null);
            r.editor = r.add(new Widget() {
                final Button pick = add(new Button(() -> org.sprautestudio.st_addon_spraute.runtime.ItemSpec.id(st.item).replace("minecraft:", ""), () -> AssetPicker.open(ParamType.ITEM, org.sprautestudio.st_addon_spraute.runtime.ItemSpec.id(st.item), v -> {
                    st.item = org.sprautestudio.st_addon_spraute.runtime.ItemSpec.with(v, org.sprautestudio.st_addon_spraute.runtime.ItemSpec.nbt(st.item));
                    changed();
                })).icon(Icon.CUBE).style(Button.Style.GHOST));
                final TextField n = add(new TextField());
                final Button del = add(Button.icon(Icon.CLOSE, "Убрать", () -> {
                    o.costs.remove(st);
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

    // ================================================================== list of tabs and offers

    private final class OfferList extends Widget implements UI.CursorProvider {
        private static final int ROW = 18;

        /** The rows: the offers in the order of the shop. */
        private List<String> rows() {
            List<String> out = new ArrayList<>();
            for (ShopDef.Offer o : doc.offers) out.add("offer:" + o.id);
            return out;
        }

        private int rowAt(double my) {
            return (int) Math.floor((my - y - 4) / ROW);
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.fill(x, y, w, h, Theme.PANEL);
            c.fill(x + w - 1, y, 1, h, Theme.BORDER);
            List<String> rows = rows();
            int ry = y + 4;
            for (int i = 0; i < rows.size(); i++) {
                String r = rows.get(i);
                boolean on = r.equals(sel), hov = isHovered() && rowAt(my) == i;
                c.round(x + 4, ry, w - 8, ROW - 1, 3, on ? Theme.ACCENT_DIM : hov ? Theme.RAISED : 0);
                if (r.startsWith("tab:")) {
                    ShopDef.Tab t = null;
                    for (ShopDef.Tab x2 : doc.tabs) if (x2.id.equals(r.substring(4))) t = x2;
                    c.icon(Icon.FOLDER, x + 8, ry + 5, 0xFF7BE08A);
                    c.textEllipsized(t != null ? t.name : r, x + 22, ry + 5, w - 30, Theme.TEXT);
                } else {
                    ShopDef.Offer o = doc.offer(r.substring(6));
                    if (o == null) continue;
                    int col = o.type.equals("sell") ? 0xFF7BE08A : o.type.equals("trade") ? 0xFF6E86FF : 0xFFFFD25A;
                    c.fill(x + 18, ry + 5, 3, 8, col);
                    String label = ShopPanel.title(lineOf(o)) + (o.count > 1 ? " ×" + o.count : "");
                    c.textEllipsized(label, x + 26, ry + 5, w - 60, Theme.TEXT_DIM);
                    String price = o.type.equals("trade") && o.price == 0 ? "обмен" : String.valueOf(o.price);
                    c.text(price, x + w - 8 - c.width(price), ry + 5, Theme.TEXT_MUTED);
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

    private static ShopView.Line lineOf(ShopDef.Offer o) {
        ShopView.Line l = new ShopView.Line();
        l.item = o.item;
        l.name = o.name;
        return l;
    }

    // ================================================================== the window, live

    private final class Preview extends Widget {
        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.gradient(x, y, w, h, 0xFF1D2030, 0xFF0D0E14);
            c.flush();
            // fit the window into the middle
            float k = Math.min(1f, Math.min((w - 16) / (float) ShopPanel.W, (h - 40) / (float) ShopPanel.H));
            var g = c.g;
            g.pose().pushPose();
            float ox = x + (w - ShopPanel.W * k) / 2f, oy = y + 10 + (h - 40 - ShopPanel.H * k) / 2f;
            g.pose().translate(ox, oy, 0);
            g.pose().scale(k, k, 1);
            panel.style = EngineClient.config().shop;
            panel.place(0, 0);
            try {
                panel.render(g, Minecraft.getInstance().font, EngineClient.config(), (mx - ox) / k, (my - oy) / k);
            } catch (Throwable ignored) {
            }
            g.flush();
            g.pose().popPose();
            c.textEllipsized("Так увидит игрок (деньги — для примера). Щёлкните товар, чтобы править его; колесо — листать", x + 8, y + h - 14, w - 16,
                    Theme.TEXT_MUTED);
            lastK = k;
            lastX = ox;
            lastY = oy;
        }

        float lastK = 1, lastX, lastY;

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            double px = (mx - lastX) / lastK, py = (my - lastY) / lastK;
            String h = panel.hit(px, py);
            panel.click(px, py);
            if (h != null && h.startsWith("cell:")) select("offer:" + h.substring(5));
            return true;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            panel.scroll(delta);
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
        return file != null ? file.name() : "Магазин";
    }

    @Override
    public Icon icon() {
        return Icon.TAG;
    }

    @Override
    public int iconColor() {
        return 0xFF7BE08A;
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
