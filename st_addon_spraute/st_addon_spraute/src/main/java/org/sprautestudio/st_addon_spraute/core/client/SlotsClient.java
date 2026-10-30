package org.sprautestudio.st_addon_spraute.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.engine.client.EngineClient;

/** Limits of the hotbar and the second hand, by the engine settings; creative and spectator players have none. */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID, value = Dist.CLIENT)
public final class SlotsClient {
    private SlotsClient() {
    }

    private static int lastAllowed;

    /** How many hotbar slots this player has now. */
    public static int hotbarSize() {
        Minecraft mc = Minecraft.getInstance();
        if (!EngineClient.config().slots || mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) return 9;
        return Math.max(1, Math.min(9, EngineClient.config().hotbarSize));
    }

    public static boolean offhandAllowed() {
        Minecraft mc = Minecraft.getInstance();
        if (!EngineClient.config().slots || mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) return true;
        return !EngineClient.config().offhand.equals("off");
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (e.phase == TickEvent.Phase.START) {
            // before the game reads its keys: the hands are not swapped
            if (!offhandAllowed()) while (mc.options.keySwapOffhand.consumeClick()) ;
            return;
        }
        var inv = mc.player.getInventory();
        int size = hotbarSize();
        if (inv.selected >= size) inv.selected = Math.min(lastAllowed, size - 1);
        else lastAllowed = inv.selected;
    }

    /** The wheel goes round the open slots only. */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        int size = hotbarSize();
        if (size >= 9 || mc.player == null || mc.screen != null || e.getScrollDelta() == 0) return;
        var inv = mc.player.getInventory();
        inv.selected = Math.floorMod(inv.selected - (int) Math.signum(e.getScrollDelta()), size);
        lastAllowed = inv.selected;
        e.setCanceled(true);
    }

    /** The own inventory screen, when the settings ask for it. */
    @SubscribeEvent
    public static void onOpen(ScreenEvent.Opening e) {
        if (e.getNewScreen() instanceof InventoryScreen && EngineClient.config().inventory) e.setNewScreen(new SlotInventoryScreen());
    }
}
