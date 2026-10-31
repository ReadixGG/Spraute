package org.sprautestudio.spraute_engine.editor.assets;

import org.sprautestudio.spraute_engine.editor.project.Project;
import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.Widget;
import org.sprautestudio.spraute_engine.ui.widgets.Button;
import org.sprautestudio.spraute_engine.ui.widgets.Dialog;
import org.sprautestudio.spraute_engine.ui.widgets.Dropdown;
import org.sprautestudio.spraute_engine.ui.widgets.Label;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Making a Spraute texture from a Minecraft skin, with a choice of how its eyes are drawn (2×2,
 * 2×1, how high, one eye staying painted) and what brows to make, and a picture of the result.
 */
public final class FaceSkinDialog {
    private FaceSkinDialog() {
    }

    public static void open(Project project, Path source, String name, Consumer<Project.ImageRef> done) {
        BufferedImage src;
        try {
            src = FaceSkin.read(source);
        } catch (IOException e) {
            UI ui = UI.get();
            if (ui != null) ui.toastError(e.getMessage());
            return;
        }
        FaceSkin.Options o = new FaceSkin.Options();
        if (src.getWidth() == 128) {
            o.target = FaceSkin.Target.SUPER_RIG;
            o.eraseFace = false;
        }
        BufferedImage[] result = {FaceSkin.convert(src, o)};
        Runnable redo = () -> result[0] = FaceSkin.convert(src, o);

        Dialog d = new Dialog("Текстура персонажа из скина", Icon.USER).width(Math.min(580, UI.get() != null ? UI.get().vw - 30 : 580));
        d.body.add(new Label("Глаза, брови и рот модель рисует и анимирует. Скажите, какие глаза на скине — "
                + "или оставьте «сами найдутся».").color(Theme.TEXT_DIM));
        d.body.add(new Label("Формат модели").color(Theme.TEXT_MUTED));
        d.body.add(new Dropdown<>(() -> List.of(FaceSkin.Target.values()), () -> o.target, v -> v.title, v -> {
            o.target = v;
            redo.run();
        }));

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
        d.body.add(new Dropdown<>(() -> List.of(FaceSkin.Brows.values()), () -> o.brows, b -> b.title, v -> {
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
                boolean rig = o.target == FaceSkin.Target.SUPER_RIG;
                int count = rig ? 5 : 3;
                int k = Math.max(4, Math.min(9, (w - (count - 1) * 12 - 20) / (count * 8)));
                int x0 = x + (w - (k * 8 * count + (count - 1) * 12)) / 2, y0 = y + 12;
                c.text("скин", x0, y, Theme.TEXT_MUTED);
                c.text("лицо", x0 + k * 8 + 12, y, Theme.TEXT_MUTED);
                c.text(rig ? "брови/рот" : "глаза/брови", x0 + (k * 8 + 12) * 2, y, Theme.TEXT_MUTED);
                block(c, src, 8, 8, x0, y0, k);
                block(c, result[0], 8, 8, x0 + k * 8 + 12, y0, k);
                block(c, result[0], 0, 0, x0 + (k * 8 + 12) * 2, y0, k);
                if (rig) {
                    c.text("зрачки", x0 + (k * 8 + 12) * 3, y, Theme.TEXT_MUTED);
                    c.text("белки", x0 + (k * 8 + 12) * 4, y, Theme.TEXT_MUTED);
                    block(c, result[0], 120, 0, x0 + (k * 8 + 12) * 3, y0, k);
                    block(c, result[0], 116, 60, x0 + (k * 8 + 12) * 4, y0, k);
                }
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
                Path out = FaceSkin.convertFile(source, project.imagesDir(), Project.sanitize(name)
                        + (o.target == FaceSkin.Target.SUPER_RIG ? " Super Rig" : " лицо"), o.copy());
                Project.ImageRef ref = new Project.ImageRef(out);
                ProjectImages.load(project, ref);
                UI ui = UI.get();
                if (ui != null) ui.toastSuccess("Текстура " + o.target.title + " «" + ref.name() + "» готова");
                done.accept(ref);
            } catch (IOException e) {
                UI ui = UI.get();
                if (ui != null) ui.toastError(e.getMessage());
            }
        });
        d.open();
    }
}
