package org.sprautestudio.st_addon_spraute.editor.zone;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.sprautestudio.st_addon_spraute.editor.project.Project;
import org.sprautestudio.st_addon_spraute.editor.project.Workspace;
import org.sprautestudio.st_addon_spraute.editor.tabs.EditorTab;
import org.sprautestudio.st_addon_spraute.lang.Analyzer;
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
import org.sprautestudio.st_addon_spraute.zone.ZoneDef;
import org.sprautestudio.st_addon_spraute.zone.client.ZoneClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Zone editor: a map from above in the middle (the zone, the player, handles to drag its corners or
 * its edge), the settings on the right. Buttons put a corner or the centre where the player stands or
 * looks. While it is open the zone is drawn in the world (editor mode) as it is now, unsaved.
 */
public class ZoneTab extends EditorTab {
    private ZoneDef doc;
    private String saved;

    private final Form form = new Form();
    private final ScrollView inspector;
    private final MapView map = new MapView();
    private final List<Button> tools = new ArrayList<>();

    public ZoneTab(Project project, Project.FileRef file, String content) {
        super(project, file);
        doc = ZoneDef.fromJson(content);
        saved = doc.toJson();
        add(map);
        inspector = add(new ScrollView(form));
        changed();
        rebuildAll();
    }


    private void tool(String text, Icon icon, String tip, Runnable r) {
        Button b = add(new Button(text, r).icon(icon).style(Button.Style.GHOST));
        b.tooltip(tip);
        b.autoWidth();
        tools.add(b);
    }

    private String name() {
        return file != null ? file.name() : "зона";
    }

    private void changed() {
        doc.fix();
        ZoneClient.LIVE.put(name(), doc);
        ZoneClient.highlighted = name();
    }

    // ================================================================== from the world

    private static net.minecraft.core.BlockPos standing() {
        var p = Minecraft.getInstance().player;
        return p == null ? null : p.blockPosition();
    }

    private static net.minecraft.core.BlockPos looking() {
        HitResult h = Minecraft.getInstance().hitResult;
        return h instanceof BlockHitResult b && h.getType() == HitResult.Type.BLOCK ? b.getBlockPos() : null;
    }

    private void corner(int which, boolean look) {
        var pos = look ? looking() : standing();
        if (pos == null) return;
        if (which == 1) {
            doc.x1 = pos.getX();
            doc.y1 = pos.getY();
            doc.z1 = pos.getZ();
        } else {
            doc.x2 = pos.getX();
            doc.y2 = pos.getY();
            doc.z2 = pos.getZ();
        }
        changed();
        buildForm();
    }

    private void centerHere() {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        if (doc.shape.equals("box")) {
            int w = Math.abs(doc.x2 - doc.x1), h = Math.abs(doc.y2 - doc.y1), d = Math.abs(doc.z2 - doc.z1);
            var b = p.blockPosition();
            doc.x1 = b.getX() - w / 2;
            doc.x2 = doc.x1 + w;
            doc.y1 = b.getY() - 1;
            doc.y2 = doc.y1 + h;
            doc.z1 = b.getZ() - d / 2;
            doc.z2 = doc.z1 + d;
        } else {
            doc.cx = Math.floor(p.getX()) + 0.5;
            doc.cy = Math.floor(p.getY());
            doc.cz = Math.floor(p.getZ()) + 0.5;
        }
        if (doc.dimension.isEmpty() && p.level() != null) doc.dimension = p.level().dimension().location().toString();
        changed();
        buildForm();
    }

    // ================================================================== inspector

    private static final List<String> SHAPES = List.of("box", "sphere", "cylinder");
    private static final List<String> TITLE_MODES = List.of("first", "always", "never");
    private static final List<String> WALLS = List.of("none", "in", "out");

    private void buildForm() {
        form.clear();
        form.header("Зона");
        form.choice("Форма", null, () -> SHAPES, () -> doc.shape, v -> switch (v) {
            case "sphere" -> "шар";
            case "cylinder" -> "цилиндр (столб)";
            default -> "коробка из блоков";
        }, this::setShape);
        form.note(doc.size());
        form.header("Части зоны — " + doc.pieces());
        form.note("Зона — все части вместе (вход в любую = вход в зону)");
        form.pieces();
        form.header("Часть " + (doc.editIndex + 1) + " — форма и размер");
        if (doc.shape.equals("box")) {
            form.integers("Угол 1", () -> new int[]{doc.x1, doc.y1, doc.z1}, v -> {
                doc.x1 = v[0];
                doc.y1 = v[1];
                doc.z1 = v[2];
            });
            form.integers("Угол 2", () -> new int[]{doc.x2, doc.y2, doc.z2}, v -> {
                doc.x2 = v[0];
                doc.y2 = v[1];
                doc.z2 = v[2];
            });
        } else {
            form.integers("Центр", () -> new int[]{(int) Math.floor(doc.cx), (int) Math.floor(doc.cy), (int) Math.floor(doc.cz)}, v -> {
                doc.cx = v[0] + 0.5;
                doc.cy = v[1];
                doc.cz = v[2] + 0.5;
            });
            form.number("Радиус", null, () -> doc.radius, v -> doc.radius = v);
            if (doc.shape.equals("cylinder")) form.number("Высота", "Вверх от центра", () -> doc.height, v -> doc.height = v);
        }
        form.text("Мир", "Пусто — в любом. minecraft:overworld, minecraft:the_nether…", () -> doc.dimension, v -> doc.dimension = v.trim());
        form.check("Включена", "Выключенная зона ничего не слышит (в сюжете: zone on / zone off)", () -> doc.enabled, v -> doc.enabled = v);
        form.check("Скрывать в игре", "Включено — контур виден только в 3D-редакторе. Выключено — все игроки видят границу зоны в мире",
                () -> doc.hideInGame, v -> doc.hideInGame = v);
        form.color("Цвет контура", () -> doc.color, v -> doc.color = v);

        form.header("Название на экране");
        form.text("Название", "Крупно посреди экрана при входе (пусто — не показывать)", () -> doc.title, v -> doc.title = v);
        form.text("Подпись", null, () -> doc.subtitle, v -> doc.subtitle = v);
        form.choice("Когда", null, () -> TITLE_MODES, () -> doc.titleMode, v -> switch (v) {
            case "always" -> "при каждом входе";
            case "never" -> "никогда";
            default -> "только в первый раз";
        }, v -> doc.titleMode = v);

        form.header("Стена");
        form.choice("Граница", "Невидимая стена по краю зоны (в сюжете: zone wall)", () -> WALLS, () -> doc.wall, v -> switch (v) {
            case "in" -> "не выпускать изнутри";
            case "out" -> "не пускать внутрь";
            default -> "нет — проходи свободно";
        }, v -> {
            doc.wall = v;
            buildForm();
        });
        if (!doc.wall.equals("none")) form.text("Сообщение", "Над хотбаром, когда упёрся", () -> doc.wallText, v -> doc.wallText = v);

        String n = name();
        form.header("В сюжете");
        form.note("on zoneenter \"" + n + "\" { … }   ·   on zoneleave \"" + n + "\" { … }");
        form.note("wait zone \"" + n + "\"   ·   wait outside \"" + n + "\"");
        form.note("if inzone(\"" + n + "\") { … }   ·   inzone(\"" + n + "\", alice)");
        form.note("zone off \"" + n + "\"   ·   zone wall \"" + n + "\" \"in\"");
        form.note("summon(\"minecraft:zombie\", zonepoint(\"" + n + "\"))");
        if (w > 0) layout();
    }

    /** Another shape in the same place. */
    private void setShape(String v) {
        if (!doc.shape.equals(v)) {
            var c = doc.center();
            double half = Math.max(doc.maxX() - doc.minX(), doc.maxZ() - doc.minZ()) / 2;
            doc.shape = v;
            if (v.equals("box")) {
                int r = (int) Math.max(1, Math.round(half));
                doc.x1 = (int) Math.floor(c.x) - r;
                doc.x2 = (int) Math.floor(c.x) + r;
                doc.z1 = (int) Math.floor(c.z) - r;
                doc.z2 = (int) Math.floor(c.z) + r;
                doc.y1 = (int) Math.floor(c.y);
                doc.y2 = doc.y1 + 5;
            } else {
                doc.cx = c.x;
                doc.cz = c.z;
                doc.cy = c.y;
                doc.radius = Math.max(1, half);
            }
        }
        changed();
        rebuildAll();
    }

    // ================================================================== for the world editor

    private final java.util.ArrayDeque<String> history = new java.util.ArrayDeque<>();

    ZoneDef doc() {
        return doc;
    }

    void snapshot() {
        history.push(doc.toJson());
        while (history.size() > 100) history.removeLast();
    }

    boolean undoZone() {
        if (history.isEmpty()) return false;
        ZoneDef old = ZoneDef.fromJson(history.pop());
        boolean shape = !old.shape.equals(doc.shape);
        doc = old;
        changed();
        if (shape) rebuildAll();
        else buildForm();
        return true;
    }

    /** The world editor changed the zone; rebuild — refresh the fields too (not on every tick of a drag). */
    void edited(boolean rebuild) {
        changed();
        if (rebuild) buildForm();
    }

    void cycleShape() {
        setShape(switch (doc.shape) {
            case "box" -> "sphere";
            case "sphere" -> "cylinder";
            default -> "box";
        });
    }

    void saveNow() {
        try {
            save();
            org.sprautestudio.st_addon_spraute.editor.ClientState.get().sources(project.name(), project.deploySources());
        } catch (IOException ignored) {
        }
    }

    // ================================================================== pieces

    void newPiece() {
        snapshot();
        doc.addPiece();
        changed();
        rebuildAll();
    }

    void nextPiece() {
        if (doc.more.isEmpty()) return;
        doc.nextPiece();
        changed();
        rebuildAll();
    }

    boolean removePiece() {
        snapshot();
        boolean ok = doc.removePiece();
        changed();
        rebuildAll();
        return ok;
    }

    /** Edits piece k (in their order). */
    private void selectPiece(int k) {
        if (k == doc.editIndex) return;
        doc.editPiece(k);
        changed();
        rebuildAll();
    }

    private void rebuildAll() {
        // the tool buttons depend on the shape: reopen-like rebuild
        for (Button b : tools) remove(b);
        tools.clear();
        Button world = add(new Button("Редактировать в мире", () -> ZoneWorldEditor.start(this)).icon(Icon.PLAY).style(Button.Style.PRIMARY));
        world.tooltip("Свободная камера в мире: ПКМ по блокам — углы, ЛКМ по меткам — тянуть грани, G — перенести зону");
        world.autoWidth();
        tools.add(world);
        if (doc.shape.equals("box")) {
            tool("Угол 1 — я", Icon.USER, "Первый угол — блок, где вы стоите", () -> corner(1, false));
            tool("Угол 2 — я", Icon.USER, "Второй угол — блок, где вы стоите", () -> corner(2, false));
            tool("Угол 1 — прицел", Icon.PIN, "Первый угол — блок, на который вы смотрите", () -> corner(1, true));
            tool("Угол 2 — прицел", Icon.PIN, "Второй угол — блок, на который вы смотрите", () -> corner(2, true));
        }
        tool("Центр — я", Icon.HOME, "Центр зоны там, где вы стоите", this::centerHere);
        buildForm();
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

        /** Three whole numbers in a row: x y z. */
        void integers(String label, Supplier<int[]> get, Consumer<int[]> set) {
            Row r = row(label, "x  y  z");
            r.editor = r.add(new Widget() {
                final TextField[] f = {add(new TextField()), add(new TextField()), add(new TextField())};

                {
                    int[] v = get.get();
                    for (int i = 0; i < 3; i++) {
                        f[i].setText(String.valueOf(v[i]));
                        f[i].filter = ch -> Character.isDigit(ch) || ch == '-';
                        f[i].onChange = s -> {
                            try {
                                int[] nv = new int[3];
                                for (int k = 0; k < 3; k++) nv[k] = Integer.parseInt(f[k].text().trim());
                                set.accept(nv);
                                changed();
                            } catch (NumberFormatException ignored) {
                            }
                        };
                    }
                }

                @Override
                public void layout() {
                    int cw = (w - 8) / 3;
                    for (int i = 0; i < 3; i++) f[i].bounds(x + i * (cw + 4), y, cw, h);
                    super.layout();
                }
            });
        }

        /** The list of pieces: click one to edit it; add / remove under it. */
        void pieces() {
            add(new Widget() {
                {
                    prefH = doc.pieces() * 22 + 6 + 26;
                }

                private int rowAt(double my) {
                    return (int) Math.floor((my - y - 3) / 22);
                }

                @Override
                protected void paint(Canvas c, int mx, int my, float dt) {
                    var all = doc.ordered();
                    int col = doc.color | 0xFF000000;
                    for (int i = 0; i < all.size(); i++) {
                        int ry = y + 3 + i * 22;
                        boolean on = i == doc.editIndex, hov = isHovered() && rowAt(my) == i && my < y + 3 + all.size() * 22;
                        c.round(x + 8, ry, w - 16, 20, 4, on ? Theme.ACCENT_DIM : hov ? Theme.RAISED : Theme.INPUT);
                        if (on) c.fill(x + 8, ry, 3, 20, Theme.ACCENT);
                        c.round(x + 16, ry + 6, 8, 8, 2, on ? col : (col & 0xFFFFFF) | 0x88000000);
                        c.text("Часть " + (i + 1), x + 30, ry + 6, on ? Theme.TEXT : Theme.TEXT_DIM);
                        String sz = all.get(i).size();
                        int tagW = on ? c.width("правится") + 10 : 0;
                        c.textEllipsized(sz, x + 84, ry + 6, w - 100 - tagW, Theme.TEXT_MUTED);
                        if (on) c.text("правится", x + w - 14 - c.width("правится"), ry + 6, Theme.ACCENT);
                    }
                    // the buttons under the list
                    int by = y + 6 + all.size() * 22;
                    int bw = (w - 24) / 2;
                    boolean hovAdd = isHovered() && my >= by && my < by + 20 && mx < x + 8 + bw;
                    boolean hovDel = isHovered() && my >= by && my < by + 20 && mx >= x + 16 + bw;
                    c.round(x + 8, by, bw, 20, 4, hovAdd ? Theme.ACCENT : Theme.ACCENT_DIM);
                    c.text("+ Добавить часть", x + 8 + (bw - c.width("+ Добавить часть")) / 2, by + 6, Theme.TEXT);
                    boolean can = doc.pieces() > 1;
                    c.round(x + 16 + bw, by, bw, 20, 4, can ? (hovDel ? 0xFF8A3030 : 0xFF5A2626) : Theme.INPUT);
                    c.text("Удалить эту", x + 16 + bw + (bw - c.width("Удалить эту")) / 2, by + 6, can ? Theme.TEXT : Theme.TEXT_MUTED);
                }

                @Override
                public boolean mousePressed(double mx, double my, int button) {
                    int n = doc.pieces();
                    int by = y + 6 + n * 22;
                    int bw = (w - 24) / 2;
                    if (my >= by && my < by + 20) {
                        if (mx < x + 8 + bw) newPiece();
                        else if (mx >= x + 16 + bw && n > 1) removePiece();
                        return true;
                    }
                    int i = rowAt(my);
                    if (i >= 0 && i < n) selectPiece(i);
                    return true;
                }
            }).tooltip("Щёлкните часть, чтобы править её. На карте и в мире правимая часть яркая, остальные бледные");
        }

        void button(String text, Runnable r) {
            Row row = row("", null);
            row.editor = row.add(new Button(text, r).style(Button.Style.GHOST));
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

        private static final int[] COLORS = {0xFF4FC3F7, 0xFF7BE08A, 0xFFFFD25A, 0xFFFF8F5A, 0xFFFF6B6B, 0xFFC792EA, 0xFFFFFFFF};

        void color(String label, java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
            Row r = row(label, null);
            r.editor = r.add(new Widget() {
                @Override
                protected void paint(Canvas c, int mx, int my, float dt) {
                    for (int i = 0; i < COLORS.length; i++) {
                        int sx = x + i * 20;
                        if ((get.getAsInt() | 0xFF000000) == COLORS[i]) c.round(sx - 1, y - 1, 18, h + 2, 3, Theme.TEXT);
                        c.round(sx + 1, y + 1, 14, h - 2, 2, COLORS[i]);
                    }
                }

                @Override
                public boolean mousePressed(double mx, double my, int button) {
                    int i = (int) ((mx - x) / 20);
                    if (i >= 0 && i < COLORS.length) {
                        set.accept(COLORS[i]);
                        changed();
                    }
                    return true;
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

    // ================================================================== the map from above

    private final class MapView extends Widget implements UI.CursorProvider {
        /** Blocks per pixel and the world point at the middle of the view. */
        private double k = 1, ox, oz;
        private String drag;
        private double grabX, grabZ;
        private double zoom = 1;
        private final org.sprautestudio.st_addon_spraute.editor.MiniMap minimap = new org.sprautestudio.st_addon_spraute.editor.MiniMap();

        private void fit() {
            double x0 = doc.minX(), x1 = doc.maxX(), z0 = doc.minZ(), z1 = doc.maxZ();
            var p = Minecraft.getInstance().player;
            if (p != null && Math.abs(p.getX() - (x0 + x1) / 2) < 120 && Math.abs(p.getZ() - (z0 + z1) / 2) < 120) {
                x0 = Math.min(x0, p.getX() - 2);
                x1 = Math.max(x1, p.getX() + 2);
                z0 = Math.min(z0, p.getZ() - 2);
                z1 = Math.max(z1, p.getZ() + 2);
            }
            double span = Math.max(x1 - x0, z1 - z0) + 8;
            k = Math.max(0.5, Math.min(40, Math.min(w - 20, h - 40) / span)) * zoom;
            ox = (x0 + x1) / 2;
            oz = (z0 + z1) / 2;
        }

        private float sx(double wx) {
            return (float) (x + w / 2.0 + (wx - ox) * k);
        }

        private float sy(double wz) {
            return (float) (y + h / 2.0 + (wz - oz) * k);
        }

        private double wx(double px) {
            return ox + (px - x - w / 2.0) / k;
        }

        private double wz(double py) {
            return oz + (py - y - h / 2.0) / k;
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.fill(x, y, w, h, 0xFF101219);
            if (drag == null) fit();
            // the world from above under everything
            c.pushClip(x, y, w, h);
            minimap.draw(c, x, y, w, h, ox, oz, k);
            c.popClip();
            // grid: every block when close, every 8 / 16 when far (see-through over the map)
            int step = k >= 6 ? 1 : k >= 1.5 ? 8 : 16;
            for (double gx = Math.floor(wx(x) / step) * step; gx <= wx(x + w); gx += step) {
                int col = Math.floorMod((long) gx, 16) == 0 ? 0x40FFFFFF : 0x16FFFFFF;
                c.fill(Math.round(sx(gx)), y, 1, h, col);
            }
            for (double gz = Math.floor(wz(y) / step) * step; gz <= wz(y + h); gz += step) {
                int col = Math.floorMod((long) gz, 16) == 0 ? 0x40FFFFFF : 0x16FFFFFF;
                c.fill(x, Math.round(sy(gz)), w, 1, col);
            }
            int col = doc.color | 0xFF000000, fillc = (doc.color & 0xFFFFFF) | 0x33000000;
            // the other pieces, faint
            int dim = (doc.color & 0xFFFFFF) | 0x88000000, dimFill = (doc.color & 0xFFFFFF) | 0x18000000;
            for (var sh : doc.more) {
                if (sh.shape.equals("box")) {
                    int ax = Math.round(sx(sh.minX())), az = Math.round(sy(sh.minZ())), bx = Math.round(sx(sh.maxX())), bz = Math.round(sy(sh.maxZ()));
                    c.fill(ax, az, bx - ax, bz - az, dimFill);
                    c.outline(ax, az, bx - ax, bz - az, dim);
                } else {
                    float cx = sx(sh.cx), cz = sy(sh.cz), r = (float) (sh.radius * k);
                    int n = Math.max(32, (int) (r * 0.8));
                    for (int i = 0; i < n; i++) {
                        double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                        c.line(cx + (float) Math.cos(a0) * r, cz + (float) Math.sin(a0) * r, cx + (float) Math.cos(a1) * r, cz + (float) Math.sin(a1) * r, 1, dim);
                    }
                }
            }
            if (doc.shape.equals("box")) {
                int ax = Math.round(sx(doc.minX())), az = Math.round(sy(doc.minZ())), bx = Math.round(sx(doc.maxX())), bz = Math.round(sy(doc.maxZ()));
                c.fill(ax, az, bx - ax, bz - az, fillc);
                c.outline(ax, az, bx - ax, bz - az, col);
                handle(c, sx(doc.x1 + 0.5), sy(doc.z1 + 0.5), "1", mx, my);
                handle(c, sx(doc.x2 + 0.5), sy(doc.z2 + 0.5), "2", mx, my);
            } else {
                float cx = sx(doc.cx), cz = sy(doc.cz), r = (float) (doc.radius * k);
                for (int row = (int) -r; row <= r; row++) {
                    int half = (int) Math.sqrt(Math.max(0, r * r - row * row));
                    c.fill(Math.round(cx - half), Math.round(cz + row), half * 2, 1, fillc);
                }
                int n = Math.max(32, (int) (r * 0.8));
                for (int i = 0; i < n; i++) {
                    double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                    c.line(cx + (float) Math.cos(a0) * r, cz + (float) Math.sin(a0) * r, cx + (float) Math.cos(a1) * r, cz + (float) Math.sin(a1) * r, 1, col);
                }
                handle(c, cx, cz, "•", mx, my);
                handle(c, cx + r, cz, "R", mx, my);
            }
            // the player
            var p = Minecraft.getInstance().player;
            if (p != null) {
                float px = sx(p.getX()), pz = sy(p.getZ());
                if (px > x && px < x + w && pz > y && pz < y + h) {
                    double yaw = Math.toRadians(p.getYRot());
                    c.line(px, pz, px - (float) Math.sin(yaw) * 10, pz + (float) Math.cos(yaw) * 10, 1.5f, 0xFFFFFFFF);
                    c.round(Math.round(px) - 3, Math.round(pz) - 3, 6, 6, 3, 0xFFFFFFFF);
                    c.round(Math.round(px) - 2, Math.round(pz) - 2, 4, 4, 2, 0xFF55C1FF);
                }
                boolean in = doc.holds(p.getX(), p.getY(), p.getZ())
                        && (doc.dimension.isEmpty() || doc.dimension.equals(p.level().dimension().location().toString()));
                String where = "Вы: " + p.blockPosition().getX() + " " + p.blockPosition().getY() + " " + p.blockPosition().getZ()
                        + (in ? "  — внутри зоны" : "  — снаружи");
                c.text(where, x + 8, y + 8, in ? 0xFF7BE08A : Theme.TEXT_MUTED);
            }
            c.text("Высота: " + fmt(doc.minY()) + " … " + fmt(doc.maxY()) + "   ·   " + doc.size(), x + 8, y + 20, Theme.TEXT_MUTED);
            c.textEllipsized("Вид сверху: север вверху. Тяните метки, колесо — масштаб. В мире зона видна при /editor on", x + 8, y + h - 14, w - 16,
                    Theme.TEXT_MUTED);
        }

        private void handle(Canvas c, float hx, float hy, String label, int mx, int my) {
            boolean hov = Math.abs(mx - hx) < 6 && Math.abs(my - hy) < 6;
            c.round(Math.round(hx) - 5, Math.round(hy) - 5, 10, 10, 2, hov || label.equals(drag) ? 0xFFFFE07A : 0xFFFFFFFF);
            c.round(Math.round(hx) - 4, Math.round(hy) - 4, 8, 8, 2, doc.color | 0xFF000000);
            c.text(label, Math.round(hx) + 7, Math.round(hy) - 4, 0xFFFFFFFF);
        }

        private String handleAt(double mx, double my) {
            List<Object[]> hs = new ArrayList<>();
            if (doc.shape.equals("box")) {
                hs.add(new Object[]{"1", sx(doc.x1 + 0.5), sy(doc.z1 + 0.5)});
                hs.add(new Object[]{"2", sx(doc.x2 + 0.5), sy(doc.z2 + 0.5)});
            } else {
                hs.add(new Object[]{"R", sx(doc.cx) + (float) (doc.radius * k), sy(doc.cz)});
                hs.add(new Object[]{"•", sx(doc.cx), sy(doc.cz)});
            }
            for (Object[] h : hs) if (Math.abs(mx - (float) h[1]) < 7 && Math.abs(my - (float) h[2]) < 7) return (String) h[0];
            // the inside moves the whole zone
            return doc.containsMain(wx(mx), doc.minY() + 0.01, wz(my)) || (!doc.shape.equals("sphere") ? false
                    : doc.containsMain(wx(mx), doc.cy, wz(my))) ? "move" : null;
        }

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            drag = handleAt(mx, my);
            if (drag == null) {
                // a faint piece: edit it
                double px = wx(mx), pz = wz(my);
                for (int i = 0; i < doc.more.size(); i++) {
                    var sh = doc.more.get(i);
                    if (px >= sh.minX() && px < sh.maxX() && pz >= sh.minZ() && pz < sh.maxZ()) {
                        selectPiece(doc.orderOfMore(i));
                        return true;
                    }
                }
            }
            grabX = wx(mx);
            grabZ = wz(my);
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (drag == null) return true;
            double px = wx(mx), pz = wz(my);
            switch (drag) {
                case "1" -> {
                    doc.x1 = (int) Math.floor(px);
                    doc.z1 = (int) Math.floor(pz);
                }
                case "2" -> {
                    doc.x2 = (int) Math.floor(px);
                    doc.z2 = (int) Math.floor(pz);
                }
                case "R" -> doc.radius = Math.max(1, Math.round(Math.hypot(px - doc.cx, pz - doc.cz) * 2) / 2.0);
                case "•" -> {
                    doc.cx = Math.floor(px) + 0.5;
                    doc.cz = Math.floor(pz) + 0.5;
                }
                case "move" -> {
                    int mdx = (int) Math.round(px - grabX), mdz = (int) Math.round(pz - grabZ);
                    if (mdx == 0 && mdz == 0) return true;
                    doc.x1 += mdx;
                    doc.x2 += mdx;
                    doc.z1 += mdz;
                    doc.z2 += mdz;
                    doc.cx += mdx;
                    doc.cz += mdz;
                    grabX += mdx;
                    grabZ += mdz;
                }
                default -> {
                }
            }
            changed();
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            if (drag != null) {
                drag = null;
                buildForm();
            }
            return true;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            zoom = Math.max(0.2, Math.min(5, zoom * (delta > 0 ? 1.2 : 1 / 1.2)));
            return true;
        }

        @Override
        public int cursor(int mx, int my) {
            return handleAt(mx, my) != null ? UI.CURSOR_HAND : UI.CURSOR_ARROW;
        }
    }

    // ================================================================== layout and tab

    private static final int INSPECTOR_W = 340;
    private int toolbarH = 28;

    @Override
    public void layout() {
        int bx = x + 6, by = y + 4;
        List<Button> all = new ArrayList<>(tools);
        for (Button b : all) {
            if (bx + b.prefW > x + w - 6 && bx > x + 6) {
                bx = x + 6;
                by += 24;
            }
            b.bounds(bx, by, b.prefW, 20);
            bx += b.prefW + 4;
        }
        toolbarH = by - y + 24;
        int iw = Math.min(INSPECTOR_W, w / 2);
        map.bounds(x, y + toolbarH, w - iw, h - toolbarH);
        inspector.bounds(x + w - iw, y + toolbarH, iw, h - toolbarH);
        form.layout();
        super.layout();
    }

    @Override
    protected void paint(Canvas c, int mx, int my, float dt) {
        c.fill(x, y, w, h, Theme.CODE_BG);
        c.fill(x, y, w, toolbarH, Theme.PANEL);
        c.fill(x, y + toolbarH - 1, w, 1, Theme.BORDER);
        // keep this zone lit in the world while its editor is the one in use
        ZoneClient.LIVE.put(name(), doc);
        ZoneClient.highlighted = name();
    }

    /** The editor window closed: the world draws the saved zone again. */
    public void closed() {
        ZoneClient.LIVE.remove(name());
        if (ZoneClient.highlighted.equals(name())) ZoneClient.highlighted = "";
    }

    @Override
    public String title() {
        return file != null ? file.name() : "Зона";
    }

    @Override
    public Icon icon() {
        return Icon.PIN;
    }

    @Override
    public int iconColor() {
        return 0xFF4FC3F7;
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
