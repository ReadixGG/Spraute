package org.sprautestudio.st_addon_spraute.editor.path;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.sprautestudio.st_addon_spraute.editor.ClientState;
import org.sprautestudio.st_addon_spraute.editor.project.Project;
import org.sprautestudio.st_addon_spraute.editor.tabs.EditorTab;
import org.sprautestudio.st_addon_spraute.lang.Analyzer;
import org.sprautestudio.st_addon_spraute.lang.ParamType;
import org.sprautestudio.st_addon_spraute.path.PathDef;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;
import org.sprautestudio.st_addon_spraute.ui.widgets.Button;
import org.sprautestudio.st_addon_spraute.ui.widgets.Dropdown;
import org.sprautestudio.st_addon_spraute.ui.widgets.ScrollView;
import org.sprautestudio.st_addon_spraute.ui.widgets.TextField;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Path editor: the route on a map from above (drag points, right click adds one on the ground, the
 * player shown), the path's settings and the chosen point on the right — its place, pause, speed,
 * animation and facing. "Edit in the world" flies a camera to put points on blocks.
 */
public class PathTab extends EditorTab {
    private PathDef doc;
    private String saved;
    /** The chosen point (-1 — none). */
    int sel = 0;
    private final Deque<String> history = new ArrayDeque<>();

    private final Form form = new Form();
    private final ScrollView inspector;
    private final MapView map = new MapView();
    private final List<Button> tools = new ArrayList<>();

    /** Paths being edited, drawn in the world by the world editor. */
    public PathTab(Project project, Project.FileRef file, String content) {
        super(project, file);
        doc = PathDef.fromJson(content);
        saved = doc.toJson();
        add(map);
        inspector = add(new ScrollView(form));
        Button world = add(new Button("Редактировать в мире", () -> PathWorldEditor.start(this)).icon(Icon.PLAY).style(Button.Style.PRIMARY));
        world.tooltip("Свободная камера: ПКМ по блоку — новая точка, ЛКМ по точке — взять и перенести");
        world.autoWidth();
        tools.add(world);
        tool("Точка — где я", Icon.USER, "Добавить точку там, где вы стоите (после выбранной)", () -> addPoint(standing()));
        tool("Точка — прицел", Icon.PIN, "Добавить точку на блок, на который вы смотрите", () -> addPoint(looking()));
        tool("Перевернуть", Icon.REFRESH, "Идти в обратном порядке", () -> {
            snapshot();
            java.util.Collections.reverse(doc.points);
            sel = doc.points.isEmpty() ? -1 : doc.points.size() - 1 - Math.max(0, sel);
            edited(true);
        });
        buildForm();
    }

    PathDef doc() {
        return doc;
    }

    private void tool(String text, Icon icon, String tip, Runnable r) {
        Button b = add(new Button(text, r).icon(icon).style(Button.Style.GHOST));
        b.tooltip(tip);
        b.autoWidth();
        tools.add(b);
    }

    String name() {
        return file != null ? file.name() : "путь";
    }

    // ================================================================== editing

    void snapshot() {
        history.push(doc.toJson());
        while (history.size() > 100) history.removeLast();
    }

    boolean undoPath() {
        if (history.isEmpty()) return false;
        doc = PathDef.fromJson(history.pop());
        sel = Math.min(sel, doc.points.size() - 1);
        buildForm();
        return true;
    }

    void edited(boolean rebuild) {
        doc.fix();
        if (rebuild) buildForm();
    }

    void saveNow() {
        try {
            save();
            ClientState.get().sources(project.name(), project.deploySources());
        } catch (IOException ignored) {
        }
    }

    /** A point after the chosen one (or at the end). */
    void addPoint(net.minecraft.world.phys.Vec3 at) {
        if (at == null) return;
        snapshot();
        PathDef.Point p = new PathDef.Point(Math.floor(at.x) + 0.5, Math.floor(at.y), Math.floor(at.z) + 0.5);
        int i = sel >= 0 && sel < doc.points.size() ? sel + 1 : doc.points.size();
        doc.points.add(i, p);
        sel = i;
        var pl = Minecraft.getInstance().player;
        if (doc.dimension.isEmpty() && pl != null) doc.dimension = pl.level().dimension().location().toString();
        edited(true);
    }

    void removePoint(int i) {
        if (i < 0 || i >= doc.points.size()) return;
        snapshot();
        doc.points.remove(i);
        sel = Math.min(i, doc.points.size() - 1);
        edited(true);
    }

    private void movePoint(int i, int d) {
        int j = i + d;
        if (i < 0 || j < 0 || j >= doc.points.size()) return;
        snapshot();
        java.util.Collections.swap(doc.points, i, j);
        sel = j;
        edited(true);
    }

    private static net.minecraft.world.phys.Vec3 standing() {
        var p = Minecraft.getInstance().player;
        return p == null ? null : new net.minecraft.world.phys.Vec3(p.getX(), Math.floor(p.getY() + 0.01), p.getZ());
    }

    private static net.minecraft.world.phys.Vec3 looking() {
        HitResult h = Minecraft.getInstance().hitResult;
        if (!(h instanceof BlockHitResult b) || h.getType() != HitResult.Type.BLOCK) return null;
        var up = b.getBlockPos().above();
        return new net.minecraft.world.phys.Vec3(up.getX(), up.getY(), up.getZ());
    }

    /** The ground at a column (top block + 1), the player's height when not loaded. */
    static double groundAt(double x, double z, double fallback) {
        var lv = Minecraft.getInstance().level;
        if (lv == null) return fallback;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!lv.hasChunk(bx >> 4, bz >> 4)) return fallback;
        return lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
    }

    // ================================================================== inspector

    private static final List<String> MODES = List.of("once", "loop", "pingpong");

    void buildForm() {
        form.clear();
        form.header("Путь");
        form.choice("Как идти", "До конца, по кругу или туда-обратно", () -> MODES, () -> doc.mode, v -> switch (v) {
            case "loop" -> "по кругу (без конца)";
            case "pingpong" -> "туда и обратно (без конца)";
            default -> "один раз до конца";
        }, v -> doc.mode = v);
        form.number("Скорость", "Одна для всех персонажей: 1 — обычный шаг (2.6 бл/с), 0.5 — медленно, от 1.6 — быстрый шаг, от 2 — бег", () -> doc.speed, v -> doc.speed = Math.max(0.05, v));
        form.note(doc.points.size() + " точек · длина " + fmt(doc.length()) + " блоков");
        form.header("Точки — " + doc.points.size());
        form.points();
        if (sel >= 0 && sel < doc.points.size()) {
            PathDef.Point p = doc.points.get(sel);
            form.header("Точка " + (sel + 1));
            form.number("X", null, () -> p.x, v -> p.x = v);
            form.number("Y", "Высота (пол под ногами)", () -> p.y, v -> p.y = v);
            form.number("Z", null, () -> p.z, v -> p.z = v);
            form.number("Постоять, сек", "Сколько стоять на точке, прежде чем идти дальше", () -> p.wait, v -> p.wait = Math.max(0, v));
            form.number("Скорость к ней", "0 — как у пути; 1 — обычный шаг", () -> p.speed, v -> p.speed = Math.max(0, v));
            form.anim("Анимация стоя", () -> p.anim, v -> p.anim = v);
            form.number("Смотреть (угол)", "Куда повернуться стоя: 0 — юг, 90 — запад, 180 — север, -90 — восток. Пусто/NaN — как пришёл",
                    () -> p.yaw == null ? Double.NaN : p.yaw, v -> p.yaw = Double.isNaN(v) ? null : v);
            form.button("Повернуть как я смотрю", () -> {
                var pl = Minecraft.getInstance().player;
                if (pl == null) return;
                p.yaw = (double) Math.round(pl.getYRot());
                edited(true);
            });
            form.button("Перенести сюда (где я стою)", () -> {
                var at = standing();
                if (at == null) return;
                snapshot();
                p.x = Math.floor(at.x) + 0.5;
                p.y = at.y;
                p.z = Math.floor(at.z) + 0.5;
                edited(true);
            });
        }
        form.header("В сюжете");
        form.note("walk alice \"" + name() + "\"   — пройти (сюжет ждёт конца)");
        form.note("walk alice \"" + name() + "\" nowait   — идти, а сюжет дальше");
        form.note("walk alice \"" + name() + "\" loop   ·   pingpong   ·   speed 1.4");
        form.note("stopwalk alice   ·   on pathend \"" + name() + "\" { … }");
        if (w > 0) layout();
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

        void number(String label, String doc, DoubleSupplier get, DoubleConsumer set) {
            Row r = row(label, doc);
            TextField f = new TextField();
            double v0 = get.getAsDouble();
            f.setText(Double.isNaN(v0) ? "" : fmt(v0));
            f.filter = ch -> Character.isDigit(ch) || ch == '-' || ch == '.';
            f.onChange = s -> {
                String t = s.trim();
                try {
                    set.accept(t.isEmpty() ? Double.NaN : Double.parseDouble(t));
                    edited(false);
                } catch (NumberFormatException ignored) {
                }
            };
            r.editor = r.add(f);
        }

        void anim(String label, Supplier<String> get, Consumer<String> set) {
            Row r = row(label, "Анимация, пока стоит на точке");
            r.editor = r.add(new Widget() {
                final TextField f = add(new TextField());
                final Button pick = add(Button.icon(Icon.SEARCH, "Выбрать анимацию…", () -> org.sprautestudio.st_addon_spraute.editor.assets.AssetPicker.open(ParamType.ANIM,
                        get.get(), v -> {
                            set.accept(v);
                            f.setText(v);
                            edited(false);
                        })));

                {
                    f.setText(get.get());
                    f.placeholder("нет");
                    f.onChange = s -> {
                        set.accept(s.trim());
                        edited(false);
                    };
                }

                @Override
                public void layout() {
                    f.bounds(x, y, w - 22, h);
                    pick.bounds(x + w - 20, y, 20, h);
                    super.layout();
                }
            });
        }

        void choice(String label, String doc, Supplier<List<String>> options, Supplier<String> get, java.util.function.Function<String, String> labeler,
                    Consumer<String> set) {
            Row r = row(label, doc);
            r.editor = r.add(new Dropdown<>(options, get, labeler, v -> {
                set.accept(v);
                edited(true);
            }));
        }

        void button(String text, Runnable run) {
            Row row = row("", null);
            row.editor = row.add(new Button(text, run).style(Button.Style.GHOST));
        }

        /** The points in order: click one to choose it; ▲▼✕ on the chosen. */
        void points() {
            add(new Widget() {
                {
                    prefH = Math.max(1, doc.points.size()) * 20 + 6;
                }

                @Override
                protected void paint(Canvas c, int mx, int my, float dt) {
                    if (doc.points.isEmpty()) {
                        c.text("Точек нет — добавьте кнопками сверху или ПКМ по карте", x + 10, y + 6, Theme.TEXT_MUTED);
                        return;
                    }
                    for (int i = 0; i < doc.points.size(); i++) {
                        PathDef.Point p = doc.points.get(i);
                        int ry = y + 3 + i * 20;
                        boolean on = i == sel, hov = isHovered() && my >= ry && my < ry + 18;
                        c.round(x + 8, ry, w - 16, 18, 4, on ? Theme.ACCENT_DIM : hov ? Theme.RAISED : Theme.INPUT);
                        c.text(String.valueOf(i + 1), x + 14, ry + 5, on ? Theme.TEXT : Theme.TEXT_DIM);
                        String s = fmt(p.x) + "  " + fmt(p.y) + "  " + fmt(p.z) + (p.wait > 0 ? "   ⏸ " + fmt(p.wait) + "с" : "") + (p.anim.isEmpty() ? "" : "   ♪");
                        c.textEllipsized(s, x + 34, ry + 5, w - 110, Theme.TEXT_MUTED);
                        if (on) {
                            String[] b = {"▲", "▼", "✕"};
                            for (int k = 0; k < 3; k++) c.text(b[k], x + w - 60 + k * 16, ry + 5, k == 2 ? Theme.ERROR : Theme.TEXT);
                        }
                    }
                }

                @Override
                public boolean mousePressed(double mx, double my, int button) {
                    int i = (int) Math.floor((my - y - 3) / 20);
                    if (i < 0 || i >= doc.points.size()) return true;
                    if (i == sel && mx >= x + w - 62) {
                        int k = (int) ((mx - (x + w - 62)) / 16);
                        if (k == 0) movePoint(i, -1);
                        else if (k == 1) movePoint(i, 1);
                        else removePoint(i);
                        return true;
                    }
                    sel = i;
                    buildForm();
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

    static String fmt(double v) {
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
        private double k = 4, ox, oz, zoom = 1;
        private int drag = -1;
        private boolean panning, userView;
        private double panFromX, panFromZ;
        private final org.sprautestudio.st_addon_spraute.editor.MiniMap minimap = new org.sprautestudio.st_addon_spraute.editor.MiniMap();

        private void fit() {
            double x0, x1, z0, z1;
            var p = Minecraft.getInstance().player;
            if (doc.points.isEmpty()) {
                double cx = p != null ? p.getX() : 0, cz = p != null ? p.getZ() : 0;
                x0 = cx - 10;
                x1 = cx + 10;
                z0 = cz - 10;
                z1 = cz + 10;
            } else {
                x0 = z0 = Double.MAX_VALUE;
                x1 = z1 = -Double.MAX_VALUE;
                for (PathDef.Point q : doc.points) {
                    x0 = Math.min(x0, q.x);
                    x1 = Math.max(x1, q.x);
                    z0 = Math.min(z0, q.z);
                    z1 = Math.max(z1, q.z);
                }
            }
            double span = Math.max(x1 - x0, z1 - z0) + 10;
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

        private int pointAt(double mx, double my) {
            for (int i = doc.points.size() - 1; i >= 0; i--) {
                PathDef.Point p = doc.points.get(i);
                if (Math.abs(mx - sx(p.x)) < 7 && Math.abs(my - sy(p.z)) < 7) return i;
            }
            return -1;
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            c.fill(x, y, w, h, 0xFF101219);
            if (drag < 0 && !panning && !userView) fit();
            c.pushClip(x, y, w, h);
            minimap.draw(c, x, y, w, h, ox, oz, k);
            int step = k >= 6 ? 1 : k >= 1.5 ? 8 : 16;
            for (double gx = Math.floor(wx(x) / step) * step; gx <= wx(x + w); gx += step)
                c.fill(Math.round(sx(gx)), y, 1, h, Math.floorMod((long) gx, 16) == 0 ? 0x40FFFFFF : 0x16FFFFFF);
            for (double gz = Math.floor(wz(y) / step) * step; gz <= wz(y + h); gz += step)
                c.fill(x, Math.round(sy(gz)), w, 1, Math.floorMod((long) gz, 16) == 0 ? 0x40FFFFFF : 0x16FFFFFF);
            int col = doc.color | 0xFF000000;
            // the route
            List<PathDef.Point> pts = doc.points;
            for (int i = 1; i < pts.size(); i++) {
                PathDef.Point a = pts.get(i - 1), b = pts.get(i);
                c.line(sx(a.x), sy(a.z), sx(b.x), sy(b.z), 2, col);
                arrow(c, sx(a.x), sy(a.z), sx(b.x), sy(b.z), col);
            }
            if (doc.mode.equals("loop") && pts.size() > 2) {
                PathDef.Point a = pts.get(pts.size() - 1), b = pts.get(0);
                c.line(sx(a.x), sy(a.z), sx(b.x), sy(b.z), 1, (col & 0xFFFFFF) | 0x88000000);
            }
            for (int i = 0; i < pts.size(); i++) {
                PathDef.Point p = pts.get(i);
                int px = Math.round(sx(p.x)), pz = Math.round(sy(p.z));
                boolean on = i == sel, hov = Math.abs(mx - px) < 7 && Math.abs(my - pz) < 7;
                c.round(px - 6, pz - 6, 12, 12, 6, on ? 0xFFFFE07A : hov ? 0xFFFFFFFF : 0xFF22252E);
                c.round(px - 5, pz - 5, 10, 10, 5, i == 0 ? 0xFF7BE08A : i == pts.size() - 1 ? 0xFFFF6B6B : col);
                c.text(String.valueOf(i + 1), px + 8, pz - 4, 0xFFFFFFFF);
                if (p.wait > 0) c.text("⏸" + fmt(p.wait), px + 8, pz + 5, 0xFFB0B8C8);
            }
            // the player
            var pl = Minecraft.getInstance().player;
            if (pl != null) {
                float px = sx(pl.getX()), pz = sy(pl.getZ());
                double yaw = Math.toRadians(pl.getYRot());
                c.line(px, pz, px - (float) Math.sin(yaw) * 10, pz + (float) Math.cos(yaw) * 10, 1.5f, 0xFFFFFFFF);
                c.round(Math.round(px) - 3, Math.round(pz) - 3, 6, 6, 3, 0xFF55C1FF);
            }
            c.popClip();
            c.text(pts.size() + " точек · " + fmt(doc.length()) + " блоков", x + 8, y + 8, Theme.TEXT_DIM);
            c.textEllipsized("Тяните точки · ПКМ — новая точка на земле · средняя кнопка — двигать вид · колесо — масштаб · зелёная — начало, красная — конец",
                    x + 8, y + h - 14, w - 16, Theme.TEXT_MUTED);
        }

        private void arrow(Canvas c, float x0, float y0, float x1, float y1, int col) {
            float dx = x1 - x0, dy = y1 - y0, len = (float) Math.hypot(dx, dy);
            if (len < 24) return;
            float mx = (x0 + x1) / 2, my = (y0 + y1) / 2, ux = dx / len, uy = dy / len;
            c.line(mx, my, mx - ux * 6 - uy * 4, my - uy * 6 + ux * 4, 2, col);
            c.line(mx, my, mx - ux * 6 + uy * 4, my - uy * 6 - ux * 4, 2, col);
        }

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            if (button == 2) {
                panning = userView = true;
                panFromX = wx(mx);
                panFromZ = wz(my);
                return true;
            }
            int i = pointAt(mx, my);
            if (button == 1) {
                if (i >= 0) {
                    removePoint(i);
                    return true;
                }
                double wxx = wx(mx), wzz = wz(my);
                var pl = Minecraft.getInstance().player;
                double fallback = sel >= 0 && sel < doc.points.size() ? doc.points.get(sel).y : pl != null ? Math.floor(pl.getY()) : 64;
                addPoint(new net.minecraft.world.phys.Vec3(wxx, groundAt(wxx, wzz, fallback), wzz));
                return true;
            }
            if (i >= 0) {
                snapshot();
                drag = i;
                sel = i;
                buildForm();
            }
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (panning) {
                ox -= wx(mx) - panFromX;
                oz -= wz(my) - panFromZ;
                return true;
            }
            if (drag < 0 || drag >= doc.points.size()) return true;
            PathDef.Point p = doc.points.get(drag);
            p.x = Math.floor(wx(mx)) + 0.5;
            p.z = Math.floor(wz(my)) + 0.5;
            p.y = groundAt(p.x, p.z, p.y);
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            panning = false;
            if (drag >= 0) {
                drag = -1;
                buildForm();
            }
            return true;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            double f = delta > 0 ? 1.2 : 1 / 1.2;
            zoom = Math.max(0.2, Math.min(8, zoom * f));
            k = Math.max(0.1, Math.min(80, k * f));
            userView = userView || drag >= 0;
            return true;
        }

        @Override
        public int cursor(int mx, int my) {
            return pointAt(mx, my) >= 0 ? UI.CURSOR_HAND : UI.CURSOR_ARROW;
        }
    }

    // ================================================================== layout and tab

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
        int iw = Math.min(340, w / 2);
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
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (UI.ctrl(mods) && key == org.lwjgl.glfw.GLFW.GLFW_KEY_Z) {
            return undoPath();
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE && sel >= 0) {
            removePoint(sel);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    public void closed() {
        if (PathWorldEditor.tab() == this) PathWorldEditor.stopEditing();
    }

    @Override
    public String title() {
        return file != null ? file.name() : "Путь";
    }

    @Override
    public Icon icon() {
        return Icon.ARROW_RIGHT;
    }

    @Override
    public int iconColor() {
        return 0xFFFFAA33;
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
