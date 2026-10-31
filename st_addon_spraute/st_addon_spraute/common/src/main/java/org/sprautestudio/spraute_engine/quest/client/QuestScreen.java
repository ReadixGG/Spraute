package org.sprautestudio.spraute_engine.quest.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.sprautestudio.spraute_engine.quest.QuestJournal;

import java.util.ArrayList;
import java.util.List;

/**
 * The quest journal in Spraute's look (its textures/gui/quests): three tabs — current, done,
 * failed — the quests of the tab on the left, the chosen one on the right with its tasks and reward.
 * A click on the chosen quest's title follows it on the screen.
 */
public class QuestScreen extends Screen {
    public static final int W = 392, H = 230;

    private static ResourceLocation tex(String n) {
        return new ResourceLocation(org.sprautestudio.spraute_engine.SprauteEngine.MODID, "textures/gui/journal/" + n + ".png");
    }

    private static final ResourceLocation BG = tex("card"), BG_2 = tex("list"), BUTTON = tex("entry"), CATEGORIES = tex("icons"),
            LINE = tex("divider"), TASK_LINE = tex("task_strip");
    private static final ResourceLocation[] TABS = {tex("tab_current"), tex("tab_completed"), tex("tab_failed")};
    private static final String[] STATES = {"active", "completed", "failed"};
    private static final String[] TAB_NAMES = {"Текущие", "Выполненные", "Проваленные"};
    /** The colours of the engine settings (set on every frame). */
    private int WHITE = 0xFFFFFFFF, MUTED = 0xFF9AA4B4, GOLD = 0xFFFFD25A, GREEN = 0xFF7BE08A, RED = 0xFFFF6B6B, BLUE = 0xFF6EC3FF;
    /** The settings drawn with (the editor's preview gives its own, unsaved). */
    public org.sprautestudio.spraute_engine.engine.EngineConfig styleFrom;
    private org.sprautestudio.spraute_engine.engine.EngineConfig.Quests st;
    private String project = "";

    private org.sprautestudio.spraute_engine.engine.EngineConfig cfg() {
        return styleFrom != null ? styleFrom : org.sprautestudio.spraute_engine.engine.client.EngineClient.config();
    }

    /** A texture of the settings, else Spraute's (a part of a sheet: u, v, sheet size). */
    private void skin(GuiGraphics g, org.sprautestudio.spraute_engine.engine.EngineConfig.Skin s, ResourceLocation def, int x, int y, int w, int h,
                      int u, int v, int tw, int th) {
        if (s != null && s.isSet()) org.sprautestudio.spraute_engine.engine.client.Skins.draw(g, project, s, x, y, w, h);
        else g.blit(def, x, y, u, v, w, h, tw, th);
    }

    private QuestJournal journal;
    private int tab;
    private String selected;
    private double listScroll, textScroll;
    private float scale = 1;
    private int x, y;
    private final long openedAt = System.currentTimeMillis();
    private boolean hidGui;

    /** Drawn inside the editor: no game around, the window at the corner, nothing sent to the server. */
    private boolean preview;

    /** The journal of the engine studio's layout (when the project has one). */
    private final JournalParts.State live;

    /** The studio's layout of the journal, or null (the classic look below). */
    private org.sprautestudio.spraute_engine.gui.client.GuiView studioLayout() {
        return preview ? null : org.sprautestudio.spraute_engine.engine.client.EngineClient.window("quest");
    }

    public QuestScreen(QuestJournal j) {
        super(Component.literal("Журнал квестов"));
        this.font = Minecraft.getInstance().font;
        this.journal = j;
        this.live = new JournalParts.State(j);
        if (!j.tracked.isEmpty() && j.entry(j.tracked) != null) select(j.tracked);
    }

    public void update(QuestJournal j) {
        this.journal = j;
        live.journal = j;
        if (live.selected != null && j.entry(live.selected) == null) live.selected = null;
        if (selected != null && j.entry(selected) == null) selected = null;
    }

    private void select(String key) {
        QuestJournal.Entry e = journal.entry(key);
        if (e == null) return;
        selected = key;
        for (int i = 0; i < STATES.length; i++) if (STATES[i].equals(e.state)) tab = i;
        textScroll = 0;
    }

    private List<QuestJournal.Entry> list() {
        List<QuestJournal.Entry> out = new ArrayList<>();
        for (QuestJournal.Entry e : journal.quests) if (e.state.equals(STATES[tab])) out.add(e);
        return out;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.hideGui) {
            mc.options.hideGui = true;
            hidGui = true;
        }
    }

    @Override
    public void removed() {
        if (hidGui) Minecraft.getInstance().options.hideGui = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The editor's preview: the window at (0, 0) zoomed by k; mouse in the same space as the drawing. */
    public void paintPreview(GuiGraphics g, float k, double mx, double my) {
        preview = true;
        scale = k;
        x = 0;
        y = 0;
        width = Math.round(W * k);
        height = Math.round(H * k);
        render(g, (int) Math.round(mx), (int) Math.round(my), 0);
    }

    public boolean clickPreview(double mx, double my) {
        preview = true;
        return mouseClicked(mx, my, 0);
    }

    public boolean scrollPreview(double mx, double my, double delta) {
        return mouseScrolled(mx, my, delta);
    }

    public String selected() {
        return selected;
    }

    private void layout() {
        if (preview) return;
        // the whole screen (keeping the journal's shape), times the settings' size
        scale = (float) (Math.min((width - 16) / (double) W, (height - 16) / (double) H) * st.scale);
        x = Math.round((width / scale - W) / 2);
        y = Math.round((height / scale - H) / 2);
    }

    // ------------------------------------------------------------------ places (window coordinates)

    private int[] tabRect(int i) {
        return new int[]{x + 7 + i * 26, y + 5, 23, 23};
    }

    private int[] entryRect(int i) {
        return new int[]{x + 6, (int) Math.round(y + 33 + i * 37 - listScroll), 131, 35};
    }

    private static final int LIST_TOP = 31, LIST_H = 195;

    private int[] trackRect() {
        return new int[]{x + 158, y + 18, 224, 12};
    }

    private static boolean in(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    // ------------------------------------------------------------------ drawing

    private void text(GuiGraphics g, String s, float tx, float ty, float sc, int color, boolean shadow) {
        g.pose().pushPose();
        g.pose().scale(sc, sc, 1);
        org.sprautestudio.spraute_engine.ui.GuiText.draw(g, font, s, tx / sc, ty / sc, color, shadow);
        g.pose().popPose();
    }

    private void text(GuiGraphics g, FormattedCharSequence s, float tx, float ty, float sc, int color) {
        g.pose().pushPose();
        g.pose().scale(sc, sc, 1);
        org.sprautestudio.spraute_engine.ui.GuiText.draw(g, font, s, tx / sc, ty / sc, color, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int smx, int smy, float pt) {
        var lay = studioLayout();
        if (lay != null) {
            g.fill(0, 0, width, height, org.sprautestudio.spraute_engine.gui.GuiDoc.toColor(lay.doc.get("background")));
            lay.liveState = live;
            JournalParts.vars(lay);
            org.sprautestudio.spraute_engine.engine.client.EngineClient.drawWindow(g, lay, smx, smy);
            return;
        }
        var cfg = cfg();
        st = cfg.quests;
        project = cfg.project;
        WHITE = st.textColor;
        MUTED = st.mutedColor;
        GOLD = st.accentColor;
        GREEN = st.doneColor;
        RED = st.failColor;
        BLUE = st.headerColor;
        if (!preview) g.fill(0, 0, width, height, st.shadeColor);
        layout();
        double wmx = smx / scale, wmy = smy / scale;
        int[] ol = st.off("list"), oc = st.off("card");
        // the mouse in the list's and in the card's own places
        double mx = wmx - ol[0], my = wmy - ol[1];
        double cmx = wmx - oc[0], cmy = wmy - oc[1];
        float k = Math.min(1, (System.currentTimeMillis() - openedAt) / 400f);
        float ease = 1 - (1 - k) * (1 - k);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        g.pose().translate(0, -(1 - ease) * 30, 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, ease);
        skin(g, st.list, BG_2, x + ol[0], y + ol[1], 144, 230, 0, 0, 144, 230);
        skin(g, st.card, BG, x + 150 + oc[0], y + 2 + oc[1], 240, 225, 0, 0, 240, 225);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        g.pose().pushPose();
        g.pose().translate(ol[0], ol[1], 0);

        // the tabs: current, done, failed
        for (int i = 0; i < 3; i++) {
            int[] r = tabRect(i);
            int v = tab == i ? 46 : in(r, mx, my) ? 23 : 0;
            var ts = i == 0 ? st.tabCurrent : i == 1 ? st.tabDone : st.tabFailed;
            org.sprautestudio.spraute_engine.engine.client.Skins.drawSheet(g, project, ts, TABS[i], r[0], r[1], 23, 23, 0, v, 23, 69);
        }
        long n = journal.quests.stream().filter(e -> e.state.equals(STATES[tab])).count();
        text(g, TAB_NAMES[tab] + " · " + n, x + 88, y + 13, 0.75f, MUTED, false);

        // the quests of the tab
        List<QuestJournal.Entry> ls = list();
        int[] clip = org.sprautestudio.spraute_engine.engine.client.ChatHeads.screenRect(g, x + 4, y + LIST_TOP, 136, LIST_H);
        g.enableScissor(clip[0], clip[1], clip[2], clip[3]);
        for (int i = 0; i < ls.size(); i++) {
            QuestJournal.Entry e = ls.get(i);
            int[] r = entryRect(i);
            if (r[1] + r[3] < y + LIST_TOP || r[1] > y + LIST_TOP + LIST_H) continue;
            boolean hov = in(r, mx, my) && my >= y + LIST_TOP && my < y + LIST_TOP + LIST_H;
            int v = e.key.equals(selected) ? 70 : hov ? 35 : 0;
            var bs = e.key.equals(selected) ? st.buttonOn : hov ? st.buttonHover : st.button;
            if (!bs.isSet() && (e.key.equals(selected) || hov)) bs = st.button.isSet() ? st.button : bs;
            skin(g, bs, BUTTON, r[0], r[1], 131, 35, 0, v, 131, 105);
            org.sprautestudio.spraute_engine.engine.client.Skins.drawSheet(g, project, st.icons, CATEGORIES, r[0] + 5, r[1] + 5, 21, 23,
                    tab * 21, hov ? 23 : 0, 63, 46);
            String t = font.plainSubstrByWidth(e.title, (int) (96 / 0.75f));
            text(g, t, r[0] + 30, r[1] + 8, 0.75f, e.key.equals(journal.tracked) ? GOLD : WHITE, true);
            String sub;
            if (e.state.equals("active")) {
                int done = 0, all = 0;
                for (QuestJournal.Task x2 : e.tasks) if (!x2.optional) {
                    all++;
                    if (x2.done()) done++;
                }
                sub = (e.giver.isEmpty() ? "" : e.giver + " · ") + done + "/" + all;
            } else sub = e.giver;
            text(g, font.plainSubstrByWidth(sub, (int) (96 / 0.6f)), r[0] + 30, r[1] + 19, 0.6f, MUTED, false);
        }
        g.disableScissor();
        if (ls.isEmpty()) text(g, tab == 0 ? "Нет текущих квестов" : tab == 1 ? "Пока ничего не выполнено" : "Ничего не провалено",
                x + 12, y + 40, 0.75f, MUTED, false);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(oc[0], oc[1], 0);

        // the chosen quest
        QuestJournal.Entry e = selected != null ? journal.entry(selected) : null;
        if (e == null || !e.state.equals(STATES[tab])) e = ls.isEmpty() ? null : ls.get(0);
        if (e != null) {
            selected = e.key;
            drawQuest(g, e, cmx, cmy);
        } else text(g, "Выберите квест слева", x + 162, y + 24, 0.75f, MUTED, false);

        if (!preview) text(g, QuestClient.JOURNAL.getTranslatedKeyMessage().getString() + " — закрыть", x + 155, y + 229, 0.5f, 0x80FFFFFF, false);
        g.pose().popPose();
        g.pose().popPose();
    }

    private void drawQuest(GuiGraphics g, QuestJournal.Entry e, double mx, double my) {
        int lx = x + 160, top = y + 17;
        // the title; clicking it follows the quest
        boolean tracked = e.key.equals(journal.tracked);
        int[] tr = trackRect();
        boolean hov = e.state.equals("active") && in(tr, mx, my);
        Component title = Component.literal(e.title).withStyle(ChatFormatting.BOLD);
        float ts = Math.min(1, 220f / Math.max(1, font.width(title)));
        g.pose().pushPose();
        g.pose().scale(ts, ts, 1);
        g.drawString(font, title, (int) (lx / ts), (int) ((top + 2) / ts), tracked ? GOLD : hov ? BLUE : WHITE, true);
        g.pose().popPose();
        if (e.state.equals("active")) text(g, tracked ? "★ отслеживается" : hov ? "☆ отслеживать" : "", x + 382 - font.width(tracked ? "★ отслеживается" : "☆ отслеживать") * 0.6f,
                top - 8, 0.6f, tracked ? GOLD : BLUE, false);
        float cy = top + 13;
        if (!e.giver.isEmpty()) {
            text(g, "Выдал: " + e.giver, lx, cy, 0.6f, MUTED, false);
            cy += 7;
        }
        org.sprautestudio.spraute_engine.engine.client.Skins.drawSheet(g, project, st.divider, LINE, lx, (int) cy + 1, 129, 2, 0, 0, 129, 2);
        cy += 6;

        int clipTop = (int) cy, clipBottom = y + 207;
        int[] clip = org.sprautestudio.spraute_engine.engine.client.ChatHeads.screenRect(g, lx - 4, clipTop, 230, clipBottom - clipTop);
        g.enableScissor(clip[0], clip[1], clip[2], clip[3]);
        float sy = (float) (cy - textScroll);
        List<org.sprautestudio.spraute_engine.quest.QuestDef.Section> parts = e.layout != null ? e.layout
                : org.sprautestudio.spraute_engine.quest.QuestDef.defaultLayout();
        boolean first = true;
        for (var part : parts) {
            if (!first) sy += 4;
            first = false;
            switch (part.kind) {
                case "description" -> {
                    if (!part.header.isEmpty()) {
                        text(g, part.header.toUpperCase(), lx, sy, 0.6f, BLUE, false);
                        sy += 8;
                    }
                    sy = paragraph(g, e.description, lx, sy, 0xFFD8DEE8);
                }
                case "tasks" -> sy = drawTasks(g, e, part.header.isEmpty() ? "ЗАДАЧИ" : part.header.toUpperCase(), lx, sy);
                case "reward" -> {
                    if (e.reward == null || e.reward.isEmpty()) break;
                    text(g, part.header.isEmpty() ? "НАГРАДА" : part.header.toUpperCase(), lx, sy, 0.6f, GOLD, false);
                    sy += 8;
                    sy = paragraph(g, e.reward, lx, sy, e.state.equals("completed") ? GREEN : 0xFFFFE7A3);
                }
                default -> {
                    if (!part.header.isEmpty()) {
                        text(g, part.header.toUpperCase(), lx, sy, 0.6f, BLUE, false);
                        sy += 8;
                    }
                    int col = 0xFFD8DEE8;
                    try {
                        if (!part.color.isEmpty()) col = 0xFF000000 | Integer.parseInt(part.color.replace("#", ""), 16);
                    } catch (NumberFormatException ignored) {
                    }
                    sy = paragraph(g, part.text, lx, sy, col);
                }
            }
        }
        g.disableScissor();
        maxText = Math.max(0, (sy + textScroll) - clipBottom + 4);
        textScroll = Math.min(textScroll, maxText);
        // a scroll bar when the page is longer than the window
        if (maxText > 0) {
            float viewH = clipBottom - clipTop, all = (float) (viewH + maxText);
            int barH = Math.max(12, (int) (viewH * viewH / all));
            int barY = clipTop + (int) ((viewH - barH) * (textScroll / maxText));
            g.fill(x + 384, clipTop, x + 386, clipBottom, 0x30FFFFFF);
            g.fill(x + 384, barY, x + 386, barY + barH, 0xB0FFFFFF);
        }
        String stt = switch (e.state) {
            case "completed" -> "Выполнен";
            case "failed" -> "Провален";
            default -> "";
        };
        if (!stt.isEmpty()) text(g, stt, x + 382 - font.width(stt) * 0.75f, y + 213, 0.75f, e.state.equals("completed") ? GREEN : RED, true);
    }

    private float paragraph(GuiGraphics g, String s, int lx, float sy, int color) {
        if (s == null || s.isEmpty()) return sy;
        for (FormattedCharSequence line : font.split(Component.literal(s), (int) (220 / 0.7f))) {
            text(g, line, lx, sy, 0.7f, color);
            sy += 7.5f;
        }
        return sy;
    }

    private float drawTasks(GuiGraphics g, QuestJournal.Entry e, String header, int lx, float sy) {
        text(g, header, lx, sy, 0.6f, BLUE, false);
        sy += 8;
        for (QuestJournal.Task t : e.tasks) {
            boolean done = t.done();
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1, 1, 1, done ? 0.35f : 0.8f);
            skin(g, st.taskLine, TASK_LINE, lx - 3, (int) sy, 225, 16, 0, 0, 225, 16);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            String mark = done ? "✔" : e.state.equals("failed") ? "✘" : "◇";
            text(g, mark, lx + 1, sy + 4.5f, 0.75f, done ? GREEN : e.state.equals("failed") ? RED : WHITE, true);
            String txt = t.text + (t.optional ? " (необязательно)" : "");
            String prog = t.count > 1 ? t.progress + "/" + t.count : "";
            float pw = font.width(prog) * 0.75f;
            text(g, font.plainSubstrByWidth(txt, (int) ((205 - pw) / 0.75f)), lx + 10, sy + 4.5f, 0.75f, done ? 0xFFB8C0CC : WHITE, true);
            if (!prog.isEmpty()) text(g, prog, lx + 218 - pw, sy + 4.5f, 0.75f, done ? GREEN : GOLD, true);
            if (done) g.fill(lx + 10, (int) sy + 8, (int) (lx + 10 + Math.min(205 - pw, font.width(txt) * 0.75f)), (int) sy + 9, 0x99B8C0CC);
            sy += 18;
        }
        return sy;
    }

    private double maxText;

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double smx, double smy, int button) {
        if (button != 0) return false;
        var lay = studioLayout();
        if (lay != null) return JournalParts.click(live, lay.toDesignX(smx), lay.toDesignY(smy));
        if (st == null) st = cfg().quests;
        int[] ol = st.off("list"), oc = st.off("card");
        double mx = smx / scale - ol[0], my = smy / scale - ol[1];
        double cmx = smx / scale - oc[0], cmy = smy / scale - oc[1];
        for (int i = 0; i < 3; i++) if (in(tabRect(i), mx, my)) {
            if (tab != i) {
                tab = i;
                selected = null;
                listScroll = 0;
                textScroll = 0;
                click();
            }
            return true;
        }
        List<QuestJournal.Entry> ls = list();
        if (my >= y + LIST_TOP && my < y + LIST_TOP + LIST_H) for (int i = 0; i < ls.size(); i++) if (in(entryRect(i), mx, my)) {
            if (!ls.get(i).key.equals(selected)) {
                selected = ls.get(i).key;
                textScroll = 0;
                click();
            }
            return true;
        }
        QuestJournal.Entry e = selected != null ? journal.entry(selected) : null;
        if (e != null && e.state.equals("active") && in(trackRect(), cmx, cmy)) {
            if (preview) journal.tracked = journal.tracked.equals(e.key) ? "" : e.key;
            else QuestClient.track(e.key);
            click();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double smx, double smy, double delta) {
        var lay = studioLayout();
        if (lay != null) {
            JournalParts.scroll(live, lay.toDesignX(smx), lay.toDesignY(smy), delta);
            return true;
        }
        if (st == null) st = cfg().quests;
        double mx = smx / scale - st.off("list")[0];
        if (mx < x + 145) {
            double max = Math.max(0, list().size() * 37 - LIST_H + 4);
            listScroll = Math.max(0, Math.min(max, listScroll - delta * 20));
        } else textScroll = Math.max(0, Math.min(maxText, textScroll - delta * 15));
        return true;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1f));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (QuestClient.JOURNAL.matches(key, scan) || Minecraft.getInstance().options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
