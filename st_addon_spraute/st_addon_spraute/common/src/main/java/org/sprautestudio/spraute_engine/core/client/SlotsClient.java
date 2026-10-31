package org.sprautestudio.spraute_engine.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.engine.client.EngineClient;
import org.sprautestudio.spraute_engine.platform.ClientHooks;
import org.sprautestudio.spraute_engine.platform.TickPhase;

/** Limits of the hotbar and the second hand, by the engine settings; creative and spectator players have none. */
public final class SlotsClient {
    public static void registerHooks() {
        ClientHooks.CLIENT_TICK.add(SlotsClient::onTick);
        ClientHooks.MOUSE_SCROLL.add(SlotsClient::onScroll);
        ClientHooks.SCREEN_OPENING.add(SlotsClient::onOpen);
    }

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

    public static void onTick(ClientHooks.ClientTick e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (e.phase == TickPhase.START) {
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
    public static void onScroll(ClientHooks.MouseScroll e) {
        Minecraft mc = Minecraft.getInstance();
        int size = hotbarSize();
        if (size >= 9 || mc.player == null || mc.screen != null || e.getScrollDelta() == 0) return;
        var inv = mc.player.getInventory();
        inv.selected = Math.floorMod(inv.selected - (int) Math.signum(e.getScrollDelta()), size);
        lastAllowed = inv.selected;
        e.setCanceled(true);
    }

    /** The own inventory screen, when the settings ask for it. */
    public static void onOpen(ClientHooks.ScreenOpening e) {
        if (e.getNewScreen() instanceof InventoryScreen && EngineClient.config().inventory) e.setNewScreen(new SlotInventoryScreen());
    }
}
