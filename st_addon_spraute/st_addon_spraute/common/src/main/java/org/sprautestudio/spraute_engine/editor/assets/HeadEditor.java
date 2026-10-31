package org.sprautestudio.spraute_engine.editor.assets;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.sprautestudio.spraute_engine.engine.client.ChatHeads;
import org.sprautestudio.spraute_engine.ui.Canvas;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.UI;
import org.sprautestudio.spraute_engine.ui.Widget;
import org.sprautestudio.spraute_engine.ui.widgets.Button;
import org.sprautestudio.spraute_engine.ui.widgets.Dialog;
import org.sprautestudio.spraute_engine.ui.widgets.Dropdown;
import org.sprautestudio.spraute_engine.ui.widgets.TextField;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Setting up a character's head in the speech window: a preview of the window with the head in its
 * frame, dragged with the mouse and zoomed with the wheel, the bone to show, its turn.
 */
public final class HeadEditor {
    private HeadEditor() {}

    public static void open(String entityType, ChatHeads.Params start, Consumer<ChatHeads.Params> onApply) {
        UI ui = UI.get();
        if (ui == null) return;
        String shown = entityType == null ? "" : entityType.replace("\"", "").trim();
        Dialog d = new Dialog("Голова в чате" + (shown.isEmpty() ? "" : " — " + shown), Icon.USER).width(Math.min(560, ui.vw - 30));
        Pane pane = new Pane(entityType, start);
        pane.prefH = Math.min(300, ui.vh - 130);
        d.body.add(pane);
        d.addButton("Сбросить", Button.Style.GHOST, pane::reset);
        d.addButton("Отмена", Button.Style.DEFAULT, d::close);
        d.addButton("Применить", Button.Style.PRIMARY, () -> {
            d.close();
            onApply.accept(pane.params());
        });
        d.open();
    }

    private static final class Pane extends Widget {
        final LivingEntity entity;
        final String typeId;
        float scale;
        int dx, dy, yaw;
        String bone;
        final Dropdown<String> boneBox;
        final TextField scaleF, dxF, dyF, yawF;
        boolean dragging;
        double dragX, dragY;
        int startDx, startDy;
        int fx, fy, fs;

        Pane(String typeId, ChatHeads.Params p) {
            this.typeId = typeId == null ? "" : typeId.replace("\"", "").trim();
            ResourceLocation rl = ResourceLocation.tryParse(this.typeId.contains(":") ? this.typeId : "minecraft:" + this.typeId);
            entity = this.typeId.startsWith("spraute:") ? ChatHeads.ofSprauteType(this.typeId)
                    : rl == null ? null : ChatHeads.ofType(BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null));
            set(p);
            List<String> bones = new ArrayList<>();
            bones.add("auto");
            bones.add("all");
            if (entity != null) bones.addAll(ChatHeads.bones(entity));
            boneBox = add(new Dropdown<>(() -> bones, () -> bone, b -> b.equals("auto") ? "авто (голова)" : b.equals("all") ? "вся модель" : b, b -> bone = b));
            scaleF = add(field(() -> fmt(scale), v -> scale = (float) Math.max(0.2, Math.min(8, v))));
            dxF = add(field(() -> String.valueOf(dx), v -> dx = (int) Math.round(v)));
            dyF = add(field(() -> String.valueOf(dy), v -> dy = (int) Math.round(v)));
            yawF = add(field(() -> String.valueOf(yaw), v -> yaw = (int) Math.round(v)));
        }

        void set(ChatHeads.Params p) {
            scale = p.scale();
            dx = p.dx();
            dy = p.dy();
            yaw = p.yaw();
            bone = p.bone() == null || p.bone().isEmpty() ? "auto" : p.bone();
        }

        void reset() {
            set(ChatHeads.Params.DEFAULT);
        }

        ChatHeads.Params params() {
            return new ChatHeads.Params(Math.round(scale * 100) / 100f, dx, dy, yaw, bone);
        }

        static String fmt(float v) {
            return v == (int) v ? String.valueOf((int) v) : String.valueOf(Math.round(v * 100) / 100.0);
        }

        TextField field(java.util.function.Supplier<String> get, java.util.function.DoubleConsumer set) {
            TextField f = new TextField() {
                @Override
                public void tick() {
                    super.tick();
                    if (!isFocused() && !get.get().equals(text())) setText(get.get());
                }
            };
            f.setText(get.get());
            f.filter = ch -> Character.isDigit(ch) || ch == '-' || ch == '.';
            f.onChange = s -> {
                try {
                    set.accept(Double.parseDouble(s.trim()));
                } catch (NumberFormatException ignored) {
                }
            };
            return f;
        }

        @Override
        public void layout() {
            int cx = x + w - 200;
            int[] ys = {y + 22, y + 66, y + 104, y + 142, y + 180};
            boneBox.bounds(cx + 70, ys[0], 128, 18);
            scaleF.bounds(cx + 70, ys[1], 128, 18);
            dxF.bounds(cx + 70, ys[2], 128, 18);
            dyF.bounds(cx + 70, ys[3], 128, 18);
            yawF.bounds(cx + 70, ys[4], 128, 18);
            super.layout();
        }

        @Override
        protected void paint(Canvas c, int mx, int my, float dt) {
            int cx = x + w - 200;
            c.text("Кость", cx, y + 27, Theme.TEXT_DIM);
            c.text("Что видно в рамке", cx, y + 8, Theme.TEXT_MUTED);
            c.text("Размер", cx, y + 71, Theme.TEXT_DIM);
            c.text("Сдвиг X, %", cx, y + 109, Theme.TEXT_DIM);
            c.text("Сдвиг Y, %", cx, y + 147, Theme.TEXT_DIM);
            c.text("Поворот, °", cx, y + 185, Theme.TEXT_DIM);
            c.textEllipsized("Тяните голову мышью, колёсико — размер", cx, y + 214, 198, Theme.TEXT_MUTED);
            c.textEllipsized(typeId, cx, y + 228, 198, Theme.TEXT_MUTED);
            // the speech window as in the game, large
            int pw = w - 214, ph = Math.min(h - 10, 150);
            int bx = x, by = y + 4;
            c.fill(bx, by, pw, ph, 0x88000000);
            fs = Math.min(ph - 24, 104);
            fx = bx + 12;
            fy = by + (ph - fs) / 2;
            c.fill(fx, fy, fs, fs, 0x33FFFFFF);
            c.text("Алиса:", fx + fs + 12, fy + 6, 0xFFFFCB6B);
            c.text("Ты наконец проснулся?", fx + fs + 12, fy + 20, 0xFFFFFFFF);
            c.text("Пойдём, покажу дорогу.", fx + fs + 12, fy + 32, 0xFFFFFFFF);
            drawHead(c, fx, fy, fs);
            c.outline(fx - 1, fy - 1, fs + 2, fs + 2, dragging ? Theme.ACCENT : 0x66FFFFFF);
            // real size, as in the game (20 x 20)
            int sy = by + ph + 10;
            c.text("В игре:", bx, sy + 6, Theme.TEXT_MUTED);
            c.fill(bx + 44, sy, 80, 22, 0x88000000);
            drawHead(c, bx + 46, sy + 1, 20);
            c.text("Алиса: …", bx + 70, sy + 7, 0xFFFFFFFF);
            if (entity == null) c.textCentered(Minecraft.getInstance().level == null ? "войдите в мир" : "нет модели " + typeId,
                    fx + fs / 2, fy + fs / 2 - 4, Theme.ERROR);
        }

        private void drawHead(Canvas c, int x0, int y0, int size) {
            if (entity == null) return;
            c.flush();
            var g = c.g;
            int[] r = ChatHeads.screenRect(g, x0, y0, size, size);
            g.enableScissor(r[0], r[1], r[2], r[3]);
            try {
                ChatHeads.render(g, entity, x0, y0, size, size, params());
            } finally {
                g.disableScissor();
            }
        }

        boolean inFrame(double mx, double my) {
            return mx >= fx && my >= fy && mx < fx + fs && my < fy + fs;
        }

        @Override
        public boolean mousePressed(double mx, double my, int button) {
            if (super.mousePressed(mx, my, button)) return true;
            if (button != 0 || !inFrame(mx, my)) return false;
            dragging = true;
            dragX = mx;
            dragY = my;
            startDx = dx;
            startDy = dy;
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double ddx, double ddy) {
            if (!dragging) return super.mouseDragged(mx, my, button, ddx, ddy);
            dx = startDx + (int) Math.round((mx - dragX) * 100 / Math.max(1, fs));
            dy = startDy + (int) Math.round((my - dragY) * 100 / Math.max(1, fs));
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            dragging = false;
            return super.mouseReleased(mx, my, button);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            if (!inFrame(mx, my)) return super.mouseScrolled(mx, my, delta);
            scale = (float) Math.max(0.2, Math.min(8, scale * (delta > 0 ? 1.08 : 1 / 1.08)));
            return true;
        }
    }
}
