package org.zonarstudio.spraute_engine.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.GuiComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.network.SyncDebugStatePacket;

import net.minecraftforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = Spraute_engine.MODID, value = Dist.CLIENT)
public class DebugOverlay {

    private static List<SyncDebugStatePacket.ScriptDebugData> debugData = new ArrayList<>();
    private static List<String> allScripts = new ArrayList<>();
    private static int currentIndex = 0;
    private static long lastUpdateTime = 0;

    // GUI State
    private static double scrollOffset = 0;
    private static final int ITEM_HEIGHT = 24;
    private static boolean isDraggingScroll = false;

    private static int reloadButtonHeight(Font font) {
        return font.lineHeight * 2 + 6;
    }

    private static int debugReloadButtonY(int panelY) {
        return panelY + 25;
    }

    public static void updateState(List<SyncDebugStatePacket.ScriptDebugData> scripts, List<String> all) {
        debugData = scripts;
        allScripts = all;
        lastUpdateTime = System.currentTimeMillis();
        if (!debugData.isEmpty()) {
            if (currentIndex >= debugData.size()) {
                currentIndex = 0;
            } else if (currentIndex < 0) {
                currentIndex = debugData.size() - 1;
            }
        }
    }

    public static boolean isDebugActive() {
        Minecraft mc = Minecraft.getInstance();
        boolean isPausedMenu = mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen;
        return System.currentTimeMillis() - lastUpdateTime <= 2000 || (isPausedMenu && !allScripts.isEmpty());
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) return;
            
            if (event.getKey() == GLFW.GLFW_KEY_LEFT && InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_K)) {
                if (!debugData.isEmpty()) {
                    currentIndex--;
                    if (currentIndex < 0) currentIndex = debugData.size() - 1;
                }
            } else if (event.getKey() == GLFW.GLFW_KEY_RIGHT && InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_K)) {
                if (!debugData.isEmpty()) {
                    currentIndex++;
                    if (currentIndex >= debugData.size()) currentIndex = 0;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onScreenInit(net.minecraftforge.client.event.ScreenEvent.Init.Post event) {
        if (!isDebugActive() || !(event.getScreen() instanceof net.minecraft.client.gui.screens.PauseScreen)) return;

        // Reset scroll when opening menu
        scrollOffset = 0;
        
        // We will render the UI in ScreenEvent.Render and handle clicks in Mouse events.
        // We don't add real widgets to the screen so we can freely clip and scroll them without overlapping.
    }

    @SubscribeEvent
    public static void onScreenRender(net.minecraftforge.client.event.ScreenEvent.Render.Post event) {
        if (!isDebugActive() || !(event.getScreen() instanceof net.minecraft.client.gui.screens.PauseScreen)) return;
        
        Minecraft mc = Minecraft.getInstance();
        PoseStack poseStack = event.getPoseStack();
        Font font = mc.font;
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();

        int panelWidth = 130;
        int panelHeight = event.getScreen().height - 40;
        int panelX = 10;
        int panelY = 20;
        
        // Draw main background
        GuiComponent.fill(poseStack, panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xD0000000);
        GuiComponent.fill(poseStack, panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF44AAFF); // Top border
        GuiComponent.fill(poseStack, panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF44AAFF); // Bottom border
        GuiComponent.fill(poseStack, panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF44AAFF); // Left border
        GuiComponent.fill(poseStack, panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF44AAFF); // Right border

        // Title
        font.drawShadow(poseStack, "§l" + I18n.get("spraute_engine.debug.title") + "§r", panelX + 10, panelY + 10, 0xFFFFFF);

        // Reload all button (two lines, localized via en_us / ru_ru)
        int reloadBtnX = panelX + 10;
        int reloadBtnY = debugReloadButtonY(panelY);
        int reloadBtnW = panelWidth - 20;
        int reloadBtnH = reloadButtonHeight(font);
        boolean reloadHovered = mouseX >= reloadBtnX && mouseX < reloadBtnX + reloadBtnW && mouseY >= reloadBtnY && mouseY < reloadBtnY + reloadBtnH;
        GuiComponent.fill(poseStack, reloadBtnX, reloadBtnY, reloadBtnX + reloadBtnW, reloadBtnY + reloadBtnH, reloadHovered ? 0xFF666666 : 0xFF444444);
        int reloadTextBlockH = font.lineHeight * 2;
        int reloadTextY = reloadBtnY + (reloadBtnH - reloadTextBlockH) / 2;
        GuiComponent.drawCenteredString(poseStack, font, "§e" + I18n.get("spraute_engine.debug.reload.1"), reloadBtnX + reloadBtnW / 2, reloadTextY, 0xFFFFFF);
        GuiComponent.drawCenteredString(poseStack, font, "§e" + I18n.get("spraute_engine.debug.reload.2"), reloadBtnX + reloadBtnW / 2, reloadTextY + font.lineHeight, 0xFFFFFF);

        int listY = reloadBtnY + reloadBtnH + 10;
        int listHeight = panelHeight - (listY - panelY) - 10;
        
        int totalContentHeight = allScripts.size() * ITEM_HEIGHT;
        double maxScroll = Math.max(0, totalContentHeight - listHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        // Scrollbar rendering
        boolean needsScrollbar = maxScroll > 0;
        int scrollWidth = 4;
        int scrollX = panelX + panelWidth - 6;
        if (needsScrollbar) {
            GuiComponent.fill(poseStack, scrollX, listY, scrollX + scrollWidth, listY + listHeight, 0xFF222222);
            int scrollBarHeight = Math.max(20, (int)((listHeight / (float)totalContentHeight) * listHeight));
            int scrollBarY = listY + (int)((scrollOffset / maxScroll) * (listHeight - scrollBarHeight));
            boolean scrollHovered = mouseX >= scrollX && mouseX < scrollX + scrollWidth && mouseY >= listY && mouseY < listY + listHeight;
            GuiComponent.fill(poseStack, scrollX, scrollBarY, scrollX + scrollWidth, scrollBarY + scrollBarHeight, scrollHovered || isDraggingScroll ? 0xFFAAAAAA : 0xFF666666);
        }

        // List rendering (with scissors)
        double scale = mc.getWindow().getGuiScale();
        int scissorX = (int)(panelX * scale);
        int scissorY = (int)(mc.getWindow().getHeight() - (listY + listHeight) * scale);
        int scissorW = (int)((panelWidth - (needsScrollbar ? 8 : 0)) * scale);
        int scissorH = (int)(listHeight * scale);
        
        com.mojang.blaze3d.systems.RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);

        int y = listY - (int)scrollOffset;
        for (String script : allScripts) {
            if (y + ITEM_HEIGHT > listY && y < listY + listHeight) {
                boolean running = debugData.stream().anyMatch(d -> d.name.equals(script));
                
                // Item background
                boolean itemHovered = mouseX >= panelX + 5 && mouseX < scrollX - 2 && mouseY >= y && mouseY < y + ITEM_HEIGHT;
                GuiComponent.fill(poseStack, panelX + 5, y, scrollX - 2, y + ITEM_HEIGHT - 2, itemHovered ? 0x40FFFFFF : 0x20FFFFFF);
                
                // Status Indicator
                font.drawShadow(poseStack, running ? "§a●" : "§7●", panelX + 8, y + 6, 0xFFFFFF);

                // Script Name
                int btnW = 32;
                int maxNameW = scrollX - (panelX + 20) - btnW - 4;
                String displayScript = script;
                if (font.width(displayScript) > maxNameW) {
                    displayScript = font.plainSubstrByWidth(displayScript, maxNameW - font.width("...")) + "...";
                }
                font.drawShadow(poseStack, displayScript, panelX + 20, y + 6, 0xFFFFFF);
                
                // Start/Stop Button
                int btnH = 14;
                int btnX = scrollX - 4 - btnW;
                int btnY = y + 4;
                boolean btnHovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
                
                // Must ensure mouse is within the scissor list area
                boolean listHovered = mouseX >= panelX && mouseX < panelX + panelWidth && mouseY >= listY && mouseY < listY + listHeight;
                btnHovered = btnHovered && listHovered;
                
                int btnColor = running ? (btnHovered ? 0xFFFF5555 : 0xFFAA0000) : (btnHovered ? 0xFF55FF55 : 0xFF00AA00);
                GuiComponent.fill(poseStack, btnX, btnY, btnX + btnW, btnY + btnH, btnColor);
                GuiComponent.drawCenteredString(poseStack, font, I18n.get(running ? "spraute_engine.debug.stop" : "spraute_engine.debug.start"), btnX + btnW / 2, btnY + 3, 0xFFFFFF);
            }
            y += ITEM_HEIGHT;
        }

        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
    }

    @SubscribeEvent
    public static void onMouseScroll(net.minecraftforge.client.event.ScreenEvent.MouseScrolled.Pre event) {
        if (!isDebugActive() || !(event.getScreen() instanceof net.minecraft.client.gui.screens.PauseScreen)) return;
        
        int panelWidth = 130;
        int panelHeight = event.getScreen().height - 40;
        int panelX = 10;
        int panelY = 20;
        
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        if (mouseX >= panelX && mouseX < panelX + panelWidth && mouseY >= panelY && mouseY < panelY + panelHeight) {
            scrollOffset -= event.getScrollDelta() * 20;
            Font font = Minecraft.getInstance().font;
            int listY = debugReloadButtonY(panelY) + reloadButtonHeight(font) + 10;
            int listHeight = panelHeight - (listY - panelY) - 10;
            int totalContentHeight = allScripts.size() * ITEM_HEIGHT;
            double maxScroll = Math.max(0, totalContentHeight - listHeight);
            
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseClick(net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre event) {
        if (!isDebugActive() || !(event.getScreen() instanceof net.minecraft.client.gui.screens.PauseScreen)) return;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        int panelWidth = 130;
        int panelHeight = event.getScreen().height - 40;
        int panelX = 10;
        int panelY = 20;

        // Reload button
        Font font = Minecraft.getInstance().font;
        int reloadBtnX = panelX + 10;
        int reloadBtnY = debugReloadButtonY(panelY);
        int reloadBtnW = panelWidth - 20;
        int reloadBtnH = reloadButtonHeight(font);
        if (mouseX >= reloadBtnX && mouseX < reloadBtnX + reloadBtnW && mouseY >= reloadBtnY && mouseY < reloadBtnY + reloadBtnH) {
            org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.sendToServer(
                    new org.zonarstudio.spraute_engine.network.DebugActionPacket("RELOAD", "")
            );
            event.setCanceled(true);
            return;
        }

        int listY = reloadBtnY + reloadBtnH + 10;
        int listHeight = panelHeight - (listY - panelY) - 10;
        int scrollX = panelX + panelWidth - 6;

        // Scrollbar click
        int totalContentHeight = allScripts.size() * ITEM_HEIGHT;
        double maxScroll = Math.max(0, totalContentHeight - listHeight);
        if (maxScroll > 0 && mouseX >= scrollX && mouseX < scrollX + 4 && mouseY >= listY && mouseY < listY + listHeight) {
            isDraggingScroll = true;
            event.setCanceled(true);
            return;
        }

        // List items
        if (mouseX >= panelX && mouseX < panelX + panelWidth && mouseY >= listY && mouseY < listY + listHeight) {
            int y = listY - (int)scrollOffset;
            for (String script : allScripts) {
                if (y + ITEM_HEIGHT > listY && y < listY + listHeight) {
                    int btnW = 32;
                    int btnH = 14;
                    int btnX = scrollX - 4 - btnW;
                    int btnY = y + 4;
                    if (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                        boolean running = debugData.stream().anyMatch(d -> d.name.equals(script));
                        org.zonarstudio.spraute_engine.network.ModNetwork.CHANNEL.sendToServer(
                                new org.zonarstudio.spraute_engine.network.DebugActionPacket(running ? "STOP" : "START", script)
                        );
                        // Instant visual feedback
                        if (running) {
                            debugData.removeIf(d -> d.name.equals(script));
                        } else {
                            debugData.add(new SyncDebugStatePacket.ScriptDebugData(script, new ArrayList<>()));
                        }
                        event.setCanceled(true);
                        return;
                    }
                }
                y += ITEM_HEIGHT;
            }
            // Eat clicks inside the panel so they don't interact with the background
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseRelease(net.minecraftforge.client.event.ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            isDraggingScroll = false;
        }
    }

    @SubscribeEvent
    public static void onMouseDrag(net.minecraftforge.client.event.ScreenEvent.MouseDragged.Pre event) {
        if (!isDebugActive() || !(event.getScreen() instanceof net.minecraft.client.gui.screens.PauseScreen)) return;
        
        if (isDraggingScroll) {
            int panelY = 20;
            int panelHeight = event.getScreen().height - 40;
            Font font = Minecraft.getInstance().font;
            int listY = debugReloadButtonY(panelY) + reloadButtonHeight(font) + 10;
            int listHeight = panelHeight - (listY - panelY) - 10;
            int totalContentHeight = allScripts.size() * ITEM_HEIGHT;
            double maxScroll = Math.max(0, totalContentHeight - listHeight);
            
            if (maxScroll > 0) {
                int scrollBarHeight = Math.max(20, (int)((listHeight / (float)totalContentHeight) * listHeight));
                double trackLength = listHeight - scrollBarHeight;
                double dragDelta = event.getDragY();
                
                scrollOffset += (dragDelta / trackLength) * maxScroll;
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) return;

        if (!isDebugActive()) {
            debugData.clear();
            allScripts.clear();
            return;
        }

        if (debugData.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        PoseStack poseStack = event.getPoseStack();

        SyncDebugStatePacket.ScriptDebugData currentScript = debugData.get(currentIndex);

        List<String> lines = new ArrayList<>();
        lines.add("§6[DEBUG] Script: §f" + currentScript.name + " (" + (currentIndex + 1) + "/" + debugData.size() + ")");
        for (SyncDebugStatePacket.TaskDebugData task : currentScript.tasks) {
            boolean isRunning = task.status.equals("Running");
            String dot = isRunning ? "§a●§r" : "§e●§r";
            
            if (isRunning) {
                lines.add(dot + " Running: " + task.name);
            } else {
                // If status is "Awaiting: ...", split it if we want, or just print it.
                // task.status is for example "Awaiting: move_to (5, -60, 15)"
                if (task.status.startsWith("Awaiting: ")) {
                    String detail = task.status.substring("Awaiting: ".length());
                    lines.add(dot + " Awaiting: " + task.name + " (" + detail + ")");
                } else {
                    lines.add(dot + " " + task.name + " (" + task.status + ")");
                }
            }
        }

        int width = 0;
        for (String line : lines) {
            int w = font.width(line);
            if (w > width) width = w;
        }

        int startX = event.getWindow().getGuiScaledWidth() - width - 10;
        int startY = 10;

        // Draw background
        GuiComponent.fill(poseStack, startX - 5, startY - 5, startX + width + 5, startY + (lines.size() * font.lineHeight) + 5, 0x80000000);

        for (int i = 0; i < lines.size(); i++) {
            font.drawShadow(poseStack, lines.get(i), startX, startY + (i * font.lineHeight), 0xFFFFFF);
        }
    }
}
