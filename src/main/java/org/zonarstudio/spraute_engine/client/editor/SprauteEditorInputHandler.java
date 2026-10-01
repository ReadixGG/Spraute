package org.zonarstudio.spraute_engine.client.editor;

import imgui.ImGui;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.client.imgui.SprauteImGuiBackend;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class SprauteEditorInputHandler {
    private SprauteEditorInputHandler() {}

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getKey() != GLFW.GLFW_KEY_N) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (mc.screen instanceof SprauteEngineEditorScreen screen) {
            // Typing N/n into any ImGui text field must not toggle the editor.
            if (SprauteImGuiBackend.isInitialized() && ImGui.getIO().getWantTextInput()) return;
            screen.onClose();
            return;
        }

        // Editor is an OP tool — without permission it would be a free client cheat.
        if (!mc.player.hasPermissions(2)) {
            if (mc.screen == null) {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                                "§7[Spraute]§c Редактор: нужны права оператора (OP 2+)"),
                        true);
            }
            return;
        }

        if (SprauteEditorState.isActive() && mc.screen == null) {
            SprauteEditorState.open();
        }
    }

    @SubscribeEvent
    public static void onEscape(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getKey() != GLFW.GLFW_KEY_ESCAPE) return;
        if (org.zonarstudio.spraute_engine.client.editor.scene.RouteFastPlace.isActive()) {
            org.zonarstudio.spraute_engine.client.editor.scene.RouteFastPlace.stop();
            return;
        }
        if (org.zonarstudio.spraute_engine.client.editor.scene.EditorViewportInput.isCaptured()) {
            org.zonarstudio.spraute_engine.client.editor.scene.EditorViewportInput.endCapture();
            return;
        }
        if (!SprauteEditorState.isFlightMode()) return;
        SprauteEditorState.returnToEditor();
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!org.zonarstudio.spraute_engine.client.editor.scene.EditorViewportInput.isCaptured()) return;
        org.zonarstudio.spraute_engine.client.editor.scene.EditorViewportInput.addSpeedFromWheel(event.getScrollDelta());
        event.setCanceled(true);
    }

    /** In free-flight mode a left click raycasts the crosshair against NPC previews. */
    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (!SprauteEditorState.isFlightMode()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        String picked = org.zonarstudio.spraute_engine.client.editor.scene.EditorGizmo.pickNode(
                mc.getWindow().getGuiScaledWidth() / 2.0,
                mc.getWindow().getGuiScaledHeight() / 2.0);
        if (picked != null) {
            org.zonarstudio.spraute_engine.client.editor.scene.SceneEditorSession.select(picked);
            org.zonarstudio.spraute_engine.client.editor.scene.AnimatorTimelineSession.selectBone(null);
            event.setCanceled(true);
        } else {
            org.zonarstudio.spraute_engine.client.editor.scene.SceneEditorSession.select(null);
            org.zonarstudio.spraute_engine.client.editor.scene.AnimatorTimelineSession.selectBone(null);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            SprauteEditorState.tick();
            org.zonarstudio.spraute_engine.client.editor.scene.EditorViewportInput.tick();
            org.zonarstudio.spraute_engine.client.editor.scene.ScenePreviewManager.tickParticlePreviews();
            org.zonarstudio.spraute_engine.client.editor.scene.ScenePreviewManager.tickLightPreviews();
        }
    }

    /** Before world render — fly look/move must affect this frame's viewport capture. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        org.zonarstudio.spraute_engine.client.editor.scene.EditorViewportInput.updateFrame();
    }
}
