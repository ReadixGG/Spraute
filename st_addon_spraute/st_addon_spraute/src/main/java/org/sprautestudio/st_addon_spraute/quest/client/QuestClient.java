package org.sprautestudio.st_addon_spraute.quest.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;
import org.sprautestudio.st_addon_spraute.quest.QuestJournal;

/**
 * The player's side of the quests: the journal (J), the followed quest in the corner of the screen
 * and the notices "Новый квест", "Квест выполнен".
 */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID, value = Dist.CLIENT)
public final class QuestClient {
    private QuestClient() {
    }

    public static final KeyMapping JOURNAL = new KeyMapping("key.st_addon_spraute.journal", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.st_addon_spraute");

    private static final ResourceLocation TASK_LINE = new ResourceLocation(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID, "textures/gui/journal/task_strip.png");
    private static final ResourceLocation CATEGORIES = new ResourceLocation(org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID, "textures/gui/journal/icons.png");

    private static QuestJournal journal = new QuestJournal();
    private static String toastTitle = "", toastText = "", toastKind = "";
    private static long toastAt;

    public static QuestJournal journal() {
        return journal;
    }

    /** From the server: the journal changed (or open it). */
    public static void receive(String json, boolean open) {
        journal = QuestJournal.fromJson(json);
        if (!journal.toast.isEmpty()) {
            String[] p = journal.toast.split("\n", 2);
            showToast(journal.toastKind, p[0], p.length > 1 ? p[1] : "");
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof QuestScreen s) s.update(journal);
        else if (open) mc.setScreen(new QuestScreen(journal));
    }

    private static org.sprautestudio.st_addon_spraute.engine.EngineConfig.Quests.Toast toastCfg() {
        return org.sprautestudio.st_addon_spraute.engine.client.EngineClient.config().quests.toast;
    }

    /** Shows the quest notice of this kind (start, task, reveal, complete, fail) — if the settings let it. */
    public static void showToast(String kind, String title, String text) {
        var c = toastCfg();
        boolean on = c.enabled && switch (kind) {
            case "start" -> c.onStart;
            case "task" -> c.onTask;
            case "reveal" -> c.onReveal;
            case "complete" -> c.onComplete;
            case "fail" -> c.onFail;
            default -> true;
        };
        if (!on) return;
        String own = switch (kind) {
            case "start" -> c.startTitle;
            case "task" -> c.taskTitle;
            case "reveal" -> c.revealTitle;
            case "complete" -> c.completeTitle;
            case "fail" -> c.failTitle;
            default -> "";
        };
        toastTitle = own != null && !own.isBlank() ? own : title;
        toastText = text;
        toastKind = kind;
        toastAt = System.currentTimeMillis();
        var lay = org.sprautestudio.st_addon_spraute.engine.client.EngineClient.window("toast");
        if (lay != null) lay.restartAppear();
        if (!c.sound) return;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(switch (toastKind) {
            case "complete" -> SoundEvents.PLAYER_LEVELUP;
            case "fail" -> SoundEvents.NOTE_BLOCK_BASS.value();
            default -> SoundEvents.EXPERIENCE_ORB_PICKUP;
        }, toastKind.equals("complete") ? 1f : 1.2f, toastKind.equals("complete") ? 0.6f : 0.8f));
    }

    public static void track(String key) {
        SprauteNetwork.CHANNEL.sendToServer(new Packets.QuestTrack(key));
    }

    @Mod.EventBusSubscriber(modid = St_addon_spraute.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void keys(net.minecraftforge.client.event.RegisterKeyMappingsEvent e) {
            e.register(JOURNAL);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (JOURNAL.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new QuestScreen(journal));
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        journal = new QuestJournal();
        toastAt = 0;
    }

    // ------------------------------------------------------------------ on the screen

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen instanceof QuestScreen || org.sprautestudio.st_addon_spraute.cutscene.client.CameraDirector.hidesCustom()) return;
        GuiGraphics g = e.getGuiGraphics();
        Font font = mc.font;
        int sw = g.guiWidth();
        tracker(g, font, sw);
        toast(g, font, sw);
    }

    /** The followed quest at the right edge: its title and what is left to do. */
    private static void tracker(GuiGraphics g, Font font, int sw) {
        if (journal.tracked.isEmpty() || !org.sprautestudio.st_addon_spraute.engine.client.EngineClient.config().quests.tracker
                || org.sprautestudio.st_addon_spraute.engine.client.EngineClient.hudLayout() != null) return;
        QuestJournal.Entry q = journal.entry(journal.tracked);
        if (q == null || !q.state.equals("active")) return;
        int w = 150, x = sw - w - 6;
        float y = 46;
        String title = font.plainSubstrByWidth(q.title, w);
        g.drawString(font, Component.literal(title).withStyle(ChatFormatting.BOLD), sw - 6 - font.width(Component.literal(title).withStyle(ChatFormatting.BOLD)), (int) y,
                0xFFFFD25A, true);
        y += 11;
        int shown = 0;
        for (QuestJournal.Task t : q.tasks) {
            if (shown >= 5) break;
            boolean done = t.done();
            String s = (done ? "✔ " : "◇ ") + t.text + (t.count > 1 ? "  " + t.progress + "/" + t.count : "");
            s = font.plainSubstrByWidth(s, (int) (w / 0.8f));
            g.pose().pushPose();
            g.pose().scale(0.8f, 0.8f, 1);
            g.drawString(font, s, (sw - 6 - font.width(s) * 0.8f) / 0.8f, y / 0.8f, done ? 0xFF7BE08A : t.optional ? 0xFFB8C0CC : 0xFFFFFFFF, true);
            g.pose().popPose();
            y += 9;
            shown++;
        }
    }

    /** "Новый квест · Title" sliding in at the top for a few seconds. */
    private static void toast(GuiGraphics g, Font font, int sw) {
        var c = toastCfg();
        var q = org.sprautestudio.st_addon_spraute.engine.client.EngineClient.config().quests;
        long life = (long) (c.seconds * 1000);
        long t = System.currentTimeMillis() - toastAt;
        if (toastAt == 0 || t > life) return;
        float in = Math.min(1, t / 300f), out = Math.min(1, (life - t) / 400f);
        float a = Math.min(in, out);
        var lay = org.sprautestudio.st_addon_spraute.engine.client.EngineClient.window("toast");
        if (lay != null) {
            // the notice from the interface editor: its own appear animations, faded out at the end
            lay.vars.put("title", toastTitle);
            lay.vars.put("text", toastText);
            lay.vars.put("kind", toastKind);
            lay.opacity = out;
            org.sprautestudio.st_addon_spraute.engine.client.EngineClient.drawWindow(g, lay, -10000, -10000);
            return;
        }
        int w = c.width, h = 16;
        float k = c.scale;
        float x = (sw - w * k) / 2f;
        float y = c.y - (1 - in) * 16;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1);
        int bgA = (c.background >>> 24) & 0xFF;
        g.fill(0, -12, w, h + 2, ((int) (a * bgA) << 24) | (c.background & 0xFFFFFF));
        RenderSystem.setShaderColor(1, 1, 1, a);
        // the journal's own textures from the engine settings: the task strip and the icons
        String project = org.sprautestudio.st_addon_spraute.engine.client.EngineClient.config().project;
        if (q.taskLine.isSet()) org.sprautestudio.st_addon_spraute.engine.client.Skins.draw(g, project, q.taskLine, 0, 2, w, h);
        else g.blit(TASK_LINE, 0, 2, 0, 0, w, h, w, h);
        int tx = 6;
        if (c.icon) {
            int icon = toastKind.equals("complete") ? 1 : toastKind.equals("fail") ? 2 : 0;
            org.sprautestudio.st_addon_spraute.engine.client.Skins.drawSheet(g, project, q.icons, CATEGORIES, 3, -10, 21, 23, icon * 21, 0, 63, 46);
            tx = 28;
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        int alpha = Math.max(4, (int) (a * 255)) << 24;
        int col = (toastKind.equals("complete") ? q.doneColor : toastKind.equals("fail") ? q.failColor : q.accentColor) & 0xFFFFFF;
        g.drawString(font, Component.literal(toastTitle.toUpperCase()).withStyle(ChatFormatting.BOLD), tx, -8, alpha | col, true);
        g.drawString(font, font.plainSubstrByWidth(toastText, w - tx - 4), tx, 6, alpha | (q.textColor & 0xFFFFFF), true);
        g.pose().popPose();
    }
}
