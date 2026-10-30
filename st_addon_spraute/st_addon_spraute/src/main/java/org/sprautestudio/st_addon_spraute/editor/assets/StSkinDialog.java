package org.sprautestudio.st_addon_spraute.editor.assets;

import org.sprautestudio.st_addon_spraute.editor.project.Project;
import org.sprautestudio.st_addon_spraute.ui.Canvas;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;
import org.sprautestudio.st_addon_spraute.ui.widgets.Button;
import org.sprautestudio.st_addon_spraute.ui.widgets.Dialog;
import org.sprautestudio.st_addon_spraute.ui.widgets.Dropdown;
import org.sprautestudio.st_addon_spraute.ui.widgets.Label;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Making a Spraute texture from a Minecraft skin, with a choice of how its eyes are drawn (2×2,
 * 2×1, how high, one eye staying painted) and what brows to make, and a picture of the result.
 */
public final class StSkinDialog {
    private StSkinDialog() {
    }

    public static void open(Project project, Path source, String name, Consumer<Project.ImageRef> done) {
        BufferedImage src;
        try {
            src = StSkin.read(source);
        } catch (IOException e) {
            UI ui = UI.get();
            if (ui != null) ui.toastError(e.getMessage());
            return;
        }
        StSkin.Options o = new StSkin.Options();
        BufferedImage[] result = {StSkin.convert(src, o)};
        Runnable redo = () -> result[0] = StSkin.convert(src, o);

        Dialog d = new Dialog("Текстура Spraute из скина", Icon.USER).width(Math.min(460, UI.get() != null ? UI.get().vw - 30 : 460));
        d.body.add(new Label("Глаза, брови и рот Spraute рисует сам и анимирует. Скажите, какие глаза на скине — "
                + "или оставьте «сами найдутся».").color(Theme.TEXT_DIM));

        d.body.add(new Label("Глаза на скине").color(Theme.TEXT_MUTED));
        d.body.add(new Dropdown<>(() -> List.of(0, 2, 1), () -> o.eyeH,
                v -> v == 0 ? "сами найдутся" : v == 2 ? "2×2 (белок и зрачок, по 2 в высоту)" : "2×1 (в одну строку)", v -> {
                    o.eyeH = v;
                    redo.run();
                }));
        d.body.add(new Label("Отступ глаз от низа лица").color(Theme.TEXT_MUTED));
        d.body.add(new Dropdown<>(() -> List.of(-1, 0, 1, 2, 3, 4), () -> o.offset,
                v -> v < 0 ? "сам найдётся" : v + (v == 1 ? " пиксель" : v == 0 ? " пикселей (у самого низа)" : " пикселя"), v -> {
                    o.offset = v;
                    redo.run();
                }));
        d.body.add(new Label("Неподвижный глаз (остаётся нарисованным на лице)").color(Theme.TEXT_MUTED));
        d.body.add(new Dropdown<>(() -> List.of(0, 1, 2), () -> o.still,
                v -> v == 0 ? "нет — двигаются оба" : v == 1 ? "левый (как смотрим на лицо)" : "правый", v -> {
                    o.still = v;
                    redo.run();
                }));
        d.body.add(new Label("Брови").color(Theme.TEXT_MUTED));
        d.body.add(new Dropdown<>(() -> List.of(StSkin.Brows.values()), () -> o.brows, b -> b.title, v -> {
            o.brows = v;
            redo.run();
        }));
        d.body.add(new Dropdown<>(() -> List.of(true, false), () -> o.eraseFace,
                v -> v ? "Стереть с лица то, что рисует игра (рекомендуется)" : "Оставить лицо как есть", v -> {
                    o.eraseFace = v;
                    redo.run();
                }));

        Widget preview = new Widget() {
            @Override
            protected void paint(Canvas c, int mx, int my, float dt) {
                int k = Math.max(4, Math.min(10, (w - 40) / 24));
                int x0 = x + (w - (k * 24 + 40)) / 2, y0 = y + 12;
                c.text("скин", x0, y, Theme.TEXT_MUTED);
                c.text("лицо после", x0 + k * 8 + 20, y, Theme.TEXT_MUTED);
                c.text("глаза, брови, рот", x0 + k * 16 + 40, y, Theme.TEXT_MUTED);
                block(c, src, 8, 8, x0, y0, k);
                block(c, result[0], 8, 8, x0 + k * 8 + 20, y0, k);
                block(c, result[0], 0, 0, x0 + k * 16 + 40, y0, k);
            }

            private void block(Canvas c, BufferedImage img, int sx, int sy, int px, int py, int k) {
                for (int yy = 0; yy < 8; yy++) {
                    for (int xx = 0; xx < 8; xx++) {
                        int argb = img.getRGB(sx + xx, sy + yy);
                        int bg = ((xx + yy) & 1) == 0 ? 0xFF2A2D33 : 0xFF22252A;
                        c.fill(px + xx * k, py + yy * k, k, k, (argb >>> 24) == 0 ? bg : argb | 0xFF000000);
                    }
                }
            }
        };
        preview.prefH = 96;
        d.body.add(preview);

        d.addButton("Отмена", Button.Style.DEFAULT, d::close);
        d.addButton("Создать", Button.Style.PRIMARY, () -> {
            d.close();
            try {
                Path out = StSkin.convertFile(source, project.imagesDir(), Project.sanitize(name) + " ST", o.copy());
                Project.ImageRef ref = new Project.ImageRef(out);
                ProjectImages.load(project, ref);
                UI ui = UI.get();
                if (ui != null) ui.toastSuccess("Текстура Spraute «" + ref.name() + "» готова");
                done.accept(ref);
            } catch (IOException e) {
                UI ui = UI.get();
                if (ui != null) ui.toastError(e.getMessage());
            }
        });
        d.open();
    }
}
