package org.zonarstudio.spraute_engine.client.editor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;

/**
 * Keeps the ImGui editor visible while Minecraft owns the captured mouse.
 * The editor Screen instance remains alive; only Minecraft's current screen is
 * cleared so normal camera and movement input continue to work.
 */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public final class SprauteEditorOverlayRenderer {
    private SprauteEditorOverlayRenderer() {}

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) return;
        Minecraft mc = Minecraft.getInstance();
        if (!SprauteEditorState.isActive() || mc.options.hideGui) return;

        if (SprauteEditorState.isFlightMode()) {
            SprauteEngineEditorScreen editor = SprauteEditorState.editorScreen();
            if (editor != null) editor.renderOverlay();
            return;
        }
        if (!(mc.screen instanceof SprauteEngineEditorScreen)) {
            Component hint = Component.literal(EditorI18n.t("N — войти в редактор"));
            //? if >=1.20.1 {
            event.getGuiGraphics().drawString(mc.font, hint, 8, 8, 0x66CCFF, true);
            //?} else {
            /*PoseStack pose = event.getPoseStack();
            mc.font.drawShadow(pose, hint, 8, 8, 0x66CCFF);
            *///?}
        }
    }
}
