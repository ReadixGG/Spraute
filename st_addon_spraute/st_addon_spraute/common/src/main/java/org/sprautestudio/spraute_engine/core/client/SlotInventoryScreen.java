package org.sprautestudio.spraute_engine.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/** The inventory of a story: the player's model in the middle and only the slots open to them. */
public final class SlotInventoryScreen extends Screen {
    /** Numbers of the hotbar and the second hand in the player's inventory menu. */
    private static final int MENU_HOTBAR = 36, MENU_OFFHAND = 45;
    private static final int CELL = 18, CELL_GAP = 2, MODEL_SCALE = 40, MODEL_H = 90;

    public SlotInventoryScreen() {
        super(Component.literal("Инвентарь"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int[] cell(int i) {
        int n = SlotsClient.hotbarSize();
        int total = n * (CELL + CELL_GAP) - CELL_GAP;
        return new int[]{(width - total) / 2 + i * (CELL + CELL_GAP), height / 2 + MODEL_H / 2 + 8};
    }

    private int[] offhandCell() {
        int[] last = cell(SlotsClient.hotbarSize());
        return new int[]{last[0] + CELL, last[1]};
    }

    private int slotAt(double mx, double my) {
        for (int i = 0; i < SlotsClient.hotbarSize(); i++) {
            int[] c = cell(i);
            if (mx >= c[0] && mx < c[0] + CELL && my >= c[1] && my < c[1] + CELL) return MENU_HOTBAR + i;
        }
        if (SlotsClient.offhandAllowed()) {
            int[] c = offhandCell();
            if (mx >= c[0] && mx < c[0] + CELL && my >= c[1] && my < c[1] + CELL) return MENU_OFFHAND;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int slot = slotAt(mx, my);
        if (slot >= 0 && minecraft != null && minecraft.player != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryMouseClick(minecraft.player.inventoryMenu.containerId, slot, button, ClickType.PICKUP, minecraft.player);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void onClose() {
        // what was left on the cursor goes back where the game puts it
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
        }
        super.onClose();
    }

    private void drawCell(GuiGraphics g, int[] c, ItemStack st, boolean hot) {
        g.fill(c[0], c[1], c[0] + CELL, c[1] + CELL, hot ? 0xC0606A80 : 0xC0202530);
        if (st.isEmpty()) return;
        g.renderItem(st, c[0] + 1, c[1] + 1);
        g.renderItemDecorations(font, st, c[0] + 1, c[1] + 1);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        if (minecraft == null || minecraft.player == null) return;
        var p = minecraft.player;
        int feet = height / 2 + MODEL_H / 2;
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, width / 2, feet, MODEL_SCALE, width / 2f - mx, feet - MODEL_H * 0.7f - my, p);
        int hover = slotAt(mx, my);
        for (int i = 0; i < SlotsClient.hotbarSize(); i++) drawCell(g, cell(i), p.getInventory().getItem(i), hover == MENU_HOTBAR + i);
        if (SlotsClient.offhandAllowed()) drawCell(g, offhandCell(), p.getOffhandItem(), hover == MENU_OFFHAND);
        ItemStack carried = p.inventoryMenu.getCarried();
        if (!carried.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.renderItem(carried, mx - 8, my - 8);
            g.renderItemDecorations(font, carried, mx - 8, my - 8);
            g.pose().popPose();
        } else if (hover >= 0) {
            ItemStack st = hover == MENU_OFFHAND ? p.getOffhandItem() : p.getInventory().getItem(hover - MENU_HOTBAR);
            if (!st.isEmpty()) g.renderTooltip(font, st, mx, my);
        }
    }
}
