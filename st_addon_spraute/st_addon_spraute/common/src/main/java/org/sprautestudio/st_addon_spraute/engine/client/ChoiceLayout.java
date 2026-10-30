package org.sprautestudio.st_addon_spraute.engine.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import org.sprautestudio.st_addon_spraute.engine.EngineConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The choice menu laid out exactly like Spraute's dialog window (its textures, sizes and
 * places), each part shifted by its offset from the engine settings. The game and the settings
 * preview draw it with this class.
 */
public final class ChoiceLayout {
    private ChoiceLayout() {
    }

    public static final List<String> PARTS = List.of("answers", "panel", "name", "text", "portrait");

    public static String title(String part) {
        return switch (part) {
            case "answers" -> "Ответы";
            case "panel" -> "Панель";
            case "name" -> "Имя";
            case "text" -> "Вопрос";
            default -> "Портрет";
        };
    }

    private static ResourceLocation st(String path) {
        return new ResourceLocation(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID, "textures/" + path + ".png");
    }

    private static final ResourceLocation SHADOW = st("gui/talk/fade"), NAMEPLATE = st("gui/talk/divider"),
            TOP = st("gui/talk/answer_top"), MID = st("gui/talk/answer_mid"), BOTTOM = st("gui/talk/answer_bottom"),
            TOP_SEL = st("gui/talk/answer_top_hot"), MID_SEL = st("gui/talk/answer_mid_hot"),
            BOTTOM_SEL = st("gui/talk/answer_bottom_hot");

    /** Spraute's sizes: button 166 wide with 16-pixel caps, text 15 from the left and 12 from the right. */
    private static final int BTN_W = 166, CAP = 16, TEXT_LEFT = 15, TEXT_RIGHT = 12, GAP = 5;

    public static int[] offset(EngineConfig.Choice c, String part) {
        int[] o = c.offsets.get(part);
        return o != null && o.length == 2 ? o : new int[]{0, 0};
    }

    /** Width of an answer button: Spraute's, or its text's (between the set min and max). */
    private static int answerW(EngineConfig.Choice c, Font font, String text) {
        if (!c.answerWidth.equals("fit")) return BTN_W;
        int tw = (int) Math.ceil(font.width(text) * c.answerTextScale);
        return Math.max(c.answerMinW, Math.min(c.answerMaxW, tw + TEXT_LEFT + TEXT_RIGHT + 2));
    }

    private static List<FormattedCharSequence> answerLines(EngineConfig.Choice c, Font font, String text) {
        int bw = answerW(c, font, text);
        return font.split(Component.literal(text), Math.max(10, (int) ((bw - TEXT_LEFT - TEXT_RIGHT) / c.answerTextScale)));
    }

    private static int answerHeight(EngineConfig.Choice c, Font font, String text) {
        return Math.round(answerLines(c, font, text).size() * 9 * c.answerTextScale) - 2 + CAP * 2;
    }

    /** A Spraute button texture stretched to a width: its left and right ends kept, the middle stretched. */
    private static void blit3(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int srcH) {
        if (w == BTN_W) {
            g.blit(tex, x, y, w, h, 0, 0, BTN_W, srcH, BTN_W, srcH);
            return;
        }
        int edge = Math.min(24, w / 2);
        g.blit(tex, x, y, edge, h, 0, 0, edge, srcH, BTN_W, srcH);
        g.blit(tex, x + edge, y, Math.max(0, w - edge * 2), h, edge, 0, BTN_W - edge * 2, srcH, BTN_W, srcH);
        g.blit(tex, x + w - edge, y, edge, h, BTN_W - edge, 0, edge, srcH, BTN_W, srcH);
    }

    /** Rectangles of the parts (x, y, w, h); "answer0".. for each answer button. */
    public static Map<String, int[]> rects(EngineConfig.Choice c, Font font, int w, int h, List<String> answers) {
        Map<String, int[]> r = new LinkedHashMap<>();
        int[] o = offset(c, "panel");
        r.put("panel", new int[]{o[0], h - 270 + o[1], w, 270});
        o = offset(c, "name");
        r.put("name", new int[]{28 + o[0], h - 74 + o[1], 104, 15});
        o = offset(c, "text");
        r.put("text", new int[]{36 + o[0], h - 50 + o[1], Math.max(40, w - 25 * 2 - 115), 45});
        o = offset(c, "portrait");
        r.put("portrait", new int[]{Math.round(w * 0.8625f - 49) + o[0], h - 62 - 85 + o[1], 98, 98});
        o = offset(c, "answers");
        int ay = 5 + o[1], total = 0, widest = BTN_W;
        if (c.answerWidth.equals("fit")) widest = 0;
        for (int i = 0; i < answers.size(); i++) {
            int bh = answerHeight(c, font, answers.get(i)), bw = answerW(c, font, answers.get(i));
            r.put("answer" + i, new int[]{5 + o[0], ay, bw, bh});
            widest = Math.max(widest, bw);
            ay += bh + GAP;
            total += bh + (i > 0 ? GAP : 0);
        }
        r.put("answers", new int[]{5 + o[0], 5 + o[1], Math.max(40, widest), Math.max(CAP * 2, total)});
        return r;
    }

    /** What is drawn: who, what is said and how far the text has been typed. */
    public record Content(String name, String question, int typed, List<String> answers, int selected, LivingEntity subject,
                          float mouseX, float mouseY, List<Boolean> locked, int textScroll, List<Boolean> centered, ResourceLocation portrait) {
        public Content(String name, String question, int typed, List<String> answers, int selected, LivingEntity subject, float mouseX, float mouseY) {
            this(name, question, typed, answers, selected, subject, mouseX, mouseY, List.of(), -1, List.of(), null);
        }
    }

    /** Lines over the text area's height (the screen scrolls by this much at most). */
    public static int textOverflow(EngineConfig.Choice c, Font font, int w, int h, List<String> answers, String text) {
        int[] t = rects(c, font, w, h, answers).get("text");
        return Math.max(0, font.split(Component.literal(text), t[2]).size() * 9 - t[3]);
    }

    public static void draw(GuiGraphics g, Font font, EngineConfig cfg, int w, int h, Content ct) {
        EngineConfig.Choice c = cfg.choice;
        String project = cfg.project;
        Map<String, int[]> r = rects(c, font, w, h, ct.answers());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int[] p = r.get("panel");
        if (c.panel.isSet()) Skins.draw(g, project, c.panel, p[0], p[1], p[2], p[3]);
        else g.blit(SHADOW, p[0], p[1], p[2], p[3], 0, 0, 480, 270, 480, 270);

        // portrait: the frame is part of the panel; the one asking stands in it
        int[] f = r.get("portrait");
        if (c.portrait.isSet()) Skins.draw(g, project, c.portrait, f[0], f[1], f[2], f[3]);
        LivingEntity e = ct.subject();
        if (ct.portrait() != null) {
            RenderSystem.enableBlend();
            g.blit(ct.portrait(), f[0], f[1], 0, 0, f[2], f[3], f[2], f[3]);
        } else if (e != null && !c.show.equals("none")) {
            // the crop: what of the frame shows the figure
            int cl = Math.max(0, c.cropLeft), cr = Math.max(0, c.cropRight), ctp = Math.max(0, c.cropTop), cb = Math.max(0, c.cropBottom);
            int[] clip = ChatHeads.screenRect(g, f[0] + cl, f[1] + ctp, Math.max(1, f[2] - cl - cr), Math.max(1, f[3] - ctp - cb));
            g.enableScissor(clip[0], clip[1], clip[2], clip[3]);
            // well in front: a big figure reaches back past the panel drawn before it
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            try {
                int ex = f[0] + c.entityX, ey = f[1] + c.entityY;
                if (c.framing.equals("head")) ChatHeads.render(g, e, ex, ey, f[2], f[3], new ChatHeads.Params(c.entityScale, 0, 0, 0, "auto"));
                else {
                    float eh = Math.max(0.5f, e.getBbHeight()), ew = Math.max(0.5f, e.getBbWidth());
                    int size = (int) Math.max(4, Math.min((f[3] - 14) / eh, (f[2] - 14) / (ew * 1.4f)) * c.entityScale);
                    InventoryScreen.renderEntityInInventoryFollowsMouse(g, ex + f[2] / 2, ey + f[3] - 8, size,
                            ex + f[2] / 2f - ct.mouseX(), ey + f[3] / 3f - ct.mouseY(), e);
                }
            } catch (Throwable ignored) {
            } finally {
                g.pose().popPose();
                g.disableScissor();
            }
        }

        int[] n = r.get("name");
        if (c.nameLine) {
            if (c.nameplate.isSet()) Skins.draw(g, project, c.nameplate, n[0], n[1] + 12, 103, 3);
            else g.blit(NAMEPLATE, n[0], n[1] + 12, 103, 3, 0, 0, 80, 3, 80, 3);
        }
        if (!ct.name().isEmpty()) g.drawCenteredString(font, ct.name(), n[0] + 52, n[1] + 2, c.nameColor);

        // the question, typed out; the unread rest is a flickering letter, as in Spraute
        int[] t = r.get("text");
        String q = ct.question();
        int typed = Math.max(0, Math.min(q.length(), ct.typed()));
        MutableComponent shown = Component.literal(q.substring(0, typed));
        if (typed < q.length()) shown.append(Component.literal("d").withStyle(ChatFormatting.OBFUSCATED));
        List<FormattedCharSequence> lines = font.split(shown, t[2]);
        int most = Math.max(0, lines.size() * 9 - t[3]);
        // new lines scroll into view by themselves; the wheel looks back
        int scroll = ct.textScroll() < 0 ? most : Math.min(most, ct.textScroll());
        int[] clip = ChatHeads.screenRect(g, t[0], t[1], t[2] + 4, t[3]);
        g.enableScissor(clip[0], clip[1], clip[2], clip[3]);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), t[0], t[1] + 9 * i - scroll, c.textColor, false);
        g.disableScissor();
        if (most > 0) {
            int bar = Math.max(6, t[3] * t[3] / (lines.size() * 9));
            int by = t[1] + (t[3] - bar) * scroll / most;
            g.fill(t[0] + t[2] + 2, by, t[0] + t[2] + 4, by + bar, 0xA0FFFFFF);
        }

        if (ct.selected() < -1) return;
        for (int i = 0; i < ct.answers().size(); i++) {
            int[] b = r.get("answer" + i);
            boolean off = i < ct.locked().size() && ct.locked().get(i);
            boolean hot = i == ct.selected() && !off;
            EngineConfig.Skin skin = hot && c.buttonHover.isSet() ? c.buttonHover : c.button;
            if (skin.isSet()) Skins.draw(g, project, skin, b[0], b[1], b[2], b[3]);
            else {
                int mid = Math.max(0, b[3] - CAP * 2);
                blit3(g, hot ? TOP_SEL : TOP, b[0], b[1], b[2], CAP, CAP);
                blit3(g, hot ? MID_SEL : MID, b[0], b[1] + CAP, b[2], mid, 8);
                blit3(g, hot ? BOTTOM_SEL : BOTTOM, b[0], b[1] + CAP + mid, b[2], CAP, CAP);
            }
            String answerText = ct.answers().get(i);
            // a closed answer: crossed out while Shift is held
            List<FormattedCharSequence> al = off && net.minecraft.client.gui.screens.Screen.hasShiftDown()
                    ? font.split(Component.literal(answerText).withStyle(ChatFormatting.STRIKETHROUGH), Math.max(10, (int) ((answerW(c, font, answerText) - TEXT_LEFT - TEXT_RIGHT) / c.answerTextScale)))
                    : answerLines(c, font, answerText);
            float ts = c.answerTextScale;
            for (int k = 0; k < al.size(); k++) {
                g.pose().pushPose();
                boolean middle = i < ct.centered().size() && ct.centered().get(i);
                g.pose().translate(middle ? b[0] + (b[2] - font.width(al.get(k)) * ts) / 2 : b[0] + TEXT_LEFT, b[1] + CAP - 1 + 9 * k * ts, 0);
                g.pose().scale(ts, ts, 1);
                g.drawString(font, al.get(k), 0, 0, off ? 0xFF808080 : hot ? c.answerHoverColor : c.answerColor, false);
                g.pose().popPose();
            }
        }
    }

    /** Spraute's answer button (top, stretched middle, bottom) in a rectangle. */
    public static void drawButton(GuiGraphics g, boolean hot, int x, int y, int w, int h) {
        int mid = Math.max(0, h - CAP * 2);
        blit3(g, hot ? TOP_SEL : TOP, x, y, w, CAP, CAP);
        blit3(g, hot ? MID_SEL : MID, x, y + CAP, w, mid, 8);
        blit3(g, hot ? BOTTOM_SEL : BOTTOM, x, y + CAP + mid, w, CAP, CAP);
    }

    /** Index of the answer under a point, or -1. */
    public static int answerAt(EngineConfig.Choice c, Font font, int w, int h, List<String> answers, double mx, double my) {
        Map<String, int[]> r = rects(c, font, w, h, answers);
        for (int i = 0; i < answers.size(); i++) {
            int[] b = r.get("answer" + i);
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) return i;
        }
        return -1;
    }

    /** The part under a point (the smaller ones first), or null. */
    public static String partAt(EngineConfig.Choice c, Font font, int w, int h, List<String> answers, double mx, double my) {
        Map<String, int[]> r = rects(c, font, w, h, answers);
        List<String> order = new ArrayList<>(List.of("answers", "name", "portrait", "text", "panel"));
        for (String part : order) {
            int[] b = r.get(part);
            if (part.equals("panel")) {
                // only its visible band at the bottom
                if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] + 190 && my < b[1] + b[3]) return part;
                continue;
            }
            if (mx >= b[0] - 2 && mx < b[0] + b[2] + 2 && my >= b[1] - 2 && my < b[1] + b[3] + 2) return part;
        }
        return null;
    }
}
