package org.sprautestudio.spraute_engine.platform;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.Input;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/** What happens on the client. Only for code that runs on the client: this class names client classes. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static final Hook<ClientTick> CLIENT_TICK = new Hook<>();
    public static final Hook<RenderTick> RENDER_TICK = new Hook<>();
    public static final Hook<LoggingIn> LOGGING_IN = new Hook<>();
    public static final Hook<LoggingOut> LOGGING_OUT = new Hook<>();
    public static final Hook<ChatFromPlayer> CHAT_FROM_PLAYER = new Hook<>();
    public static final Hook<InteractionKey> INTERACTION_KEY = new Hook<>();
    public static final Hook<Key> KEY = new Hook<>();
    public static final Hook<MouseButton> MOUSE_BUTTON = new Hook<>();
    public static final Hook<MouseScroll> MOUSE_SCROLL = new Hook<>();
    public static final Hook<MovementInput> MOVEMENT_INPUT = new Hook<>();
    public static final Hook<ClientCommands> CLIENT_COMMANDS = new Hook<>();
    public static final Hook<RenderGui> RENDER_GUI = new Hook<>();
    public static final Hook<RenderHand> RENDER_HAND = new Hook<>();
    public static final Hook<RenderPlayer> RENDER_PLAYER = new Hook<>();
    public static final Hook<ScreenOpening> SCREEN_OPENING = new Hook<>();
    public static final Hook<CameraAngles> CAMERA_ANGLES = new Hook<>();
    public static final Hook<ComputeFov> COMPUTE_FOV = new Hook<>();
    public static final Hook<LeftClickEmpty> LEFT_CLICK_EMPTY = new Hook<>();
    public static final Hook<ClientSetup> CLIENT_SETUP = new Hook<>();
    public static final Hook<RegisterKeys> REGISTER_KEYS = new Hook<>();
    public static final Hook<RegisterHud> REGISTER_HUD = new Hook<>();
    public static final Hook<RegisterRenderers> REGISTER_RENDERERS = new Hook<>();
    public static final Hook<RenderHudPart> RENDER_HUD_PART = new Hook<>();
    public static final Hook<RenderLevel> RENDER_LEVEL = new Hook<>();

    public static final class ClientTick {
        public final TickPhase phase;

        public ClientTick(TickPhase phase) {
            this.phase = phase;
        }
    }

    public static final class RenderTick {
        public final TickPhase phase;
        public final float renderTickTime;

        public RenderTick(TickPhase phase, float renderTickTime) {
            this.phase = phase;
            this.renderTickTime = renderTickTime;
        }
    }

    public static final class LoggingIn {

        public LoggingIn() {
        }
    }

    public static final class LoggingOut {

        public LoggingOut() {
        }
    }

    public static final class ChatFromPlayer extends Cancellable {

        public ChatFromPlayer() {
        }
    }

    public static final class InteractionKey extends Cancellable {
        private boolean swingHand;

        public InteractionKey(boolean swingHand) {
            this.swingHand = swingHand;
        }

        public boolean shouldSwingHand() {
            return swingHand;
        }

        public void setSwingHand(boolean value) {
            swingHand = value;
        }
    }

    public static final class Key {
        private final int key;
        private final int scanCode;
        private final int action;
        private final int modifiers;

        public Key(int key, int scanCode, int action, int modifiers) {
            this.key = key;
            this.scanCode = scanCode;
            this.action = action;
            this.modifiers = modifiers;
        }

        public int getKey() {
            return key;
        }

        public int getScanCode() {
            return scanCode;
        }

        public int getAction() {
            return action;
        }

        public int getModifiers() {
            return modifiers;
        }
    }

    public static final class MouseButton extends Cancellable {
        private final int button;
        private final int action;

        public MouseButton(int button, int action) {
            this.button = button;
            this.action = action;
        }

        public int getButton() {
            return button;
        }

        public int getAction() {
            return action;
        }
    }

    public static final class MouseScroll extends Cancellable {
        private final double scrollDelta;

        public MouseScroll(double scrollDelta) {
            this.scrollDelta = scrollDelta;
        }

        public double getScrollDelta() {
            return scrollDelta;
        }
    }

    public static final class MovementInput {
        private final Input input;

        public MovementInput(Input input) {
            this.input = input;
        }

        public Input getInput() {
            return input;
        }
    }

    public static final class ClientCommands {
        private final CommandDispatcher<CommandSourceStack> dispatcher;

        public ClientCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
            this.dispatcher = dispatcher;
        }

        public CommandDispatcher<CommandSourceStack> getDispatcher() {
            return dispatcher;
        }
    }

    public static final class RenderGui {
        private final GuiGraphics guiGraphics;
        private final float partialTick;
        private final Window window;

        public RenderGui(GuiGraphics guiGraphics, float partialTick, Window window) {
            this.guiGraphics = guiGraphics;
            this.partialTick = partialTick;
            this.window = window;
        }

        public GuiGraphics getGuiGraphics() {
            return guiGraphics;
        }

        public float getPartialTick() {
            return partialTick;
        }

        public Window getWindow() {
            return window;
        }
    }

    public static final class RenderHand extends Cancellable {

        public RenderHand() {
        }
    }

    public static final class RenderPlayer extends Cancellable {
        private final Player entity;
        private final float partialTick;

        public RenderPlayer(Player entity, float partialTick) {
            this.entity = entity;
            this.partialTick = partialTick;
        }

        public Player getEntity() {
            return entity;
        }

        public float getPartialTick() {
            return partialTick;
        }
    }

    public static final class ScreenOpening {
        private Screen newScreen;

        public ScreenOpening(Screen newScreen) {
            this.newScreen = newScreen;
        }

        public Screen getNewScreen() {
            return newScreen;
        }

        public void setNewScreen(Screen value) {
            newScreen = value;
        }
    }

    public static final class CameraAngles {
        private float yaw;
        private float pitch;
        private float roll;

        public CameraAngles(float yaw, float pitch, float roll) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }

        public float getYaw() {
            return yaw;
        }

        public void setYaw(float value) {
            yaw = value;
        }

        public float getPitch() {
            return pitch;
        }

        public void setPitch(float value) {
            pitch = value;
        }

        public float getRoll() {
            return roll;
        }

        public void setRoll(float value) {
            roll = value;
        }
    }

    public static final class ComputeFov {
        private double fov;

        public ComputeFov(double fov) {
            this.fov = fov;
        }

        public double getFOV() {
            return fov;
        }

        public void setFOV(double value) {
            fov = value;
        }
    }

    public static final class LeftClickEmpty {

        public LeftClickEmpty() {
        }
    }
    /** The parts of the game's own HUD, as {@link RenderHudPart#id()} names them. */
    public static final class HudParts {
        private HudParts() {
        }

        public static final ResourceLocation PLAYER_HEALTH = new ResourceLocation("minecraft", "player_health");
        public static final ResourceLocation ARMOR_LEVEL = new ResourceLocation("minecraft", "armor_level");
        public static final ResourceLocation FOOD_LEVEL = new ResourceLocation("minecraft", "food_level");
        public static final ResourceLocation AIR_LEVEL = new ResourceLocation("minecraft", "air_level");
        public static final ResourceLocation EXPERIENCE_BAR = new ResourceLocation("minecraft", "experience_bar");
        public static final ResourceLocation HOTBAR = new ResourceLocation("minecraft", "hotbar");
        public static final ResourceLocation CHAT_PANEL = new ResourceLocation("minecraft", "chat_panel");
    }

    public static final class ClientSetup {
    }

    public interface RegisterKeys {
        void register(KeyMapping key);
    }

    /** A part of the HUD the engine adds. */
    @FunctionalInterface
    public interface HudLayer {
        void draw(GuiGraphics g, float partialTick, int width, int height);
    }

    public interface RegisterHud {
        void registerAbove(ResourceLocation above, String name, HudLayer layer);
    }

    public interface RegisterRenderers {
        <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> renderer);
    }

    /** A part of the HUD about to be drawn; canceled, it is not drawn. */
    public abstract static class RenderHudPart extends Cancellable {
        private final ResourceLocation id;
        private final GuiGraphics guiGraphics;
        private final float partialTick;
        private final com.mojang.blaze3d.platform.Window window;

        protected RenderHudPart(ResourceLocation id, GuiGraphics guiGraphics, float partialTick, com.mojang.blaze3d.platform.Window window) {
            this.id = id;
            this.guiGraphics = guiGraphics;
            this.partialTick = partialTick;
            this.window = window;
        }

        public ResourceLocation id() {
            return id;
        }

        public GuiGraphics getGuiGraphics() {
            return guiGraphics;
        }

        public float getPartialTick() {
            return partialTick;
        }

        public com.mojang.blaze3d.platform.Window getWindow() {
            return window;
        }

        /** Whether {@link #drawOriginal} can draw the part where the pose says. */
        public abstract boolean canDrawOriginal();

        public abstract void drawOriginal(int width, int height);
    }

    public enum LevelStage {AFTER_PARTICLES, AFTER_WEATHER}

    public static final class RenderLevel {
        private final LevelStage stage;
        private final PoseStack poseStack;
        private final Matrix4f projectionMatrix;
        private final float partialTick;
        private final Camera camera;

        public RenderLevel(LevelStage stage, PoseStack poseStack, Matrix4f projectionMatrix, float partialTick, Camera camera) {
            this.stage = stage;
            this.poseStack = poseStack;
            this.projectionMatrix = projectionMatrix;
            this.partialTick = partialTick;
            this.camera = camera;
        }

        public LevelStage getStage() {
            return stage;
        }

        public PoseStack getPoseStack() {
            return poseStack;
        }

        public Matrix4f getProjectionMatrix() {
            return projectionMatrix;
        }

        public float getPartialTick() {
            return partialTick;
        }

        public Camera getCamera() {
            return camera;
        }
    }
}
