package org.sprautestudio.spraute_engine.engine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.spraute_engine.cutscene.client.CameraDirector;
import org.sprautestudio.spraute_engine.engine.EngineConfig;
import org.sprautestudio.spraute_engine.net.Packets;
import org.sprautestudio.spraute_engine.net.SprauteNetwork;

import java.util.List;

/**
 * The story's choice menu, built like Spraute's dialog window (see {@link ChoiceLayout}): the
 * camera flies to the one asking, the question is typed out at the bottom, the answers appear at the
 * top once it is read. The one asking (or the player, a mob — engine settings) stands in the portrait.
 */
public class ChoiceScreen extends Screen {
    private final int scriptId, choiceId, entityId;
    private final String speaker;
    /** Paragraphs of the text (split by an empty line); F8 shows the next once the last one is typed out. */
    private final List<String> paragraphs;
    private final List<String> answers;
    private final List<Boolean> locked;
    private final List<String> emotions;
    private final String emotion;
    private final List<Boolean> centered;
    private final ResourceLocation portrait;
    private int selected = -1, typed, paragraph, lastHover = -1;
    /** The wheel's place in the text; -1 — following the newest line. */
    private int textScroll = -1;
    private boolean answered;
    private static LivingEntity mobCache;
    private static String mobCacheId = "";

    public ChoiceScreen(Packets.ChoiceOpen msg) {
        super(Component.literal(msg.question()));
        this.scriptId = msg.scriptId();
        this.choiceId = msg.choiceId();
        this.entityId = msg.entityId();
        this.speaker = msg.speaker();
        this.paragraphs = List.of(msg.question().split("\\n\\s*\\n"));
        this.answers = msg.answers();
        this.locked = msg.locked();
        this.emotions = msg.emotions();
        this.emotion = msg.emotion();
        this.centered = msg.centered();
        this.portrait = msg.portrait().isEmpty() ? null : ResourceLocation.tryParse(msg.portrait());
    }

    public static void open(Packets.ChoiceOpen msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ChoiceScreen(msg));
        ProjectSounds.playBuiltin("dialog_open");
        if (cfg().camera && msg.entityId() >= 0) CameraDirector.dialogFlyTo(msg.entityId());
    }

    private static EngineConfig.Choice cfg() {
        return EngineClient.config().choice;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** All the paragraphs shown so far. */
    private String shownText() {
        return String.join("\n\n", paragraphs.subList(0, paragraph + 1));
    }

    private boolean typedOut() {
        return paragraph >= paragraphs.size() - 1 && typed >= shownText().length();
    }

    private boolean paragraphTyped() {
        return typed >= shownText().length();
    }

    /** The answers the player sees: none until all the text is out. */
    private List<String> shownAnswers() {
        return typedOut() ? answers : List.of();
    }

    private boolean isLocked(int i) {
        return i >= 0 && i < locked.size() && locked.get(i);
    }

    /** The face of the portrait follows the answer under the mouse. */
    private void hoverFace(int hover) {
        if (hover == lastHover) return;
        lastHover = hover;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(entityId) instanceof org.sprautestudio.spraute_engine.core.entity.AnimatedEntity ae)) return;
        String face = hover >= 0 && hover < emotions.size() && !emotions.get(hover).isEmpty() ? emotions.get(hover) : emotion;
        org.sprautestudio.spraute_engine.runtime.Anims.showEmotion(ae, face);
    }

    /** Spraute's pace: two or three letters a tick, once the camera has arrived. */
    @Override
    public void tick() {
        if (CameraDirector.dialogArrived() && !paragraphTyped()) {
            typed = Math.min(shownText().length(), typed + Math.round(2 + Minecraft.getInstance().getFrameTime()));
        }
    }

    // ================================================================== who is drawn

    static LivingEntity subject(EngineConfig.Choice c, int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        switch (c.show) {
            case "player" -> {
                return mc.player;
            }
            case "mob" -> {
                if (!c.mob.equals(mobCacheId) || mobCache == null || mobCache.level() != mc.level) {
                    mobCacheId = c.mob;
                    mobCache = null;
                    ResourceLocation rl = ResourceLocation.tryParse(c.mob.contains(":") ? c.mob : "minecraft:" + c.mob);
                    var type = rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
                    if (type != null) {
                        try {
                            if (type.create(mc.level) instanceof LivingEntity le) mobCache = le;
                        } catch (Throwable ignored) {
                        }
                    }
                }
                return mobCache;
            }
            case "none" -> {
                return null;
            }
            default -> {
                Entity e = entityId >= 0 ? mc.level.getEntity(entityId) : null;
                return e instanceof LivingEntity le ? le : null;
            }
        }
    }

    /** Who stands in the portrait of the settings preview: the player for "speaker". */
    public static LivingEntity previewSubject(EngineConfig.Choice c) {
        return c.show.equals("mob") ? subject(c, -1) : Minecraft.getInstance().player;
    }

    // ================================================================== drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        // like Spraute: the window shows up once the camera is in front of the speaker
        if (!CameraDirector.dialogArrived()) return;
        EngineConfig cfg = EngineClient.config();
        var lay = EngineClient.window("choice");
        if (lay != null) {
            // the menu from the interface editor: the same question, answers and keys
            lay.vars.put("name", speaker);
            lay.vars.put("question", shownText().substring(0, Math.min(shownText().length(), typed)));
            lay.answers = typedOut() ? answers : List.of();
            lay.speaker = subject(cfg.choice, entityId);
            int h = typedOut() ? lay.answerAt(mx, my) : -1;
            if (h >= 0) selected = h;
            lay.answerHover = selected;
            EngineClient.drawWindow(g, lay, mx, my);
            return;
        }
        float k = zoom(cfg.choice);
        int vw = Math.round(width / k), vh = Math.round(height / k);
        double vx = mx / k, vy = my / k;
        int hover = typedOut() ? ChoiceLayout.answerAt(cfg.choice, font, vw, vh, answers, vx, vy) : -1;
        hoverFace(hover);
        if (hover >= 0) selected = hover;
        g.pose().pushPose();
        g.pose().scale(k, k, 1);
        ChoiceLayout.draw(g, font, cfg, vw, vh, new ChoiceLayout.Content(speaker, shownText(), typed, shownAnswers(),
                typedOut() ? selected : -2, subject(cfg.choice, entityId), (float) vx, (float) vy, locked, textScroll, centered, portrait));
        g.pose().popPose();
    }

    /**
     * The menu is laid out on a 270-high screen (Spraute's window at 1080p), then zoomed to the real one —
     * so it takes the same share of any screen, whatever the GUI scale.
     */
    private float zoom(EngineConfig.Choice c) {
        return Math.max(0.25f, height / 270f * c.uiScale);
    }

    // ================================================================== input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !CameraDirector.dialogArrived()) return true;
        if (!typedOut()) {
            typed = shownText().length();
            return true;
        }
        var lay = EngineClient.window("choice");
        int i;
        if (lay != null) i = lay.answerAt(mx, my);
        else {
            float k = zoom(cfg());
            i = ChoiceLayout.answerAt(cfg(), font, Math.round(width / k), Math.round(height / k), answers, mx / k, my / k);
        }
        if (i >= 0 && !isLocked(i)) answer(i);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        float k = zoom(cfg());
        int most = ChoiceLayout.textOverflow(cfg(), font, Math.round(width / k), Math.round(height / k), shownAnswers(), shownText());
        int from = textScroll < 0 ? most : textScroll;
        int to = from - (int) (delta * 9);
        textScroll = to >= most ? -1 : Math.max(0, to);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (!CameraDirector.dialogArrived()) return true;
        if (org.sprautestudio.spraute_engine.core.client.SpeechClient.ADVANCE.matches(key, scan) && paragraphTyped() && paragraph < paragraphs.size() - 1) {
            paragraph++;
            textScroll = -1;
            if (scriptId >= 0) SprauteNetwork.sendToServer(new Packets.ChoiceParagraph(scriptId, choiceId, paragraph));
            return true;
        }
        if (!typedOut()) {
            if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) typed = shownText().length();
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9 && key - GLFW.GLFW_KEY_1 < answers.size()) {
            if (!isLocked(key - GLFW.GLFW_KEY_1)) answer(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) selected = (selected + 1 + answers.size()) % answers.size();
        else if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) selected = (Math.max(0, selected) - 1 + answers.size()) % answers.size();
        else if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) && selected >= 0 && !isLocked(selected)) answer(selected);
        return true;
    }

    private void answer(int index) {
        if (answered) return;
        answered = true;
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
        if (scriptId >= 0) SprauteNetwork.sendToServer(new Packets.ChoiceAnswer(scriptId, choiceId, index + 1));
        CameraDirector.dialogFlyBack();
        onClose();
    }

    /** Esc: no answer; the story goes on past the menu and the camera flies back. */
    @Override
    public void onClose() {
        if (!answered) {
            answered = true;
            if (scriptId >= 0) SprauteNetwork.sendToServer(new Packets.ChoiceAnswer(scriptId, choiceId, -1));
            CameraDirector.dialogFlyBack();
        }
        Minecraft.getInstance().setScreen(null);
    }
}
