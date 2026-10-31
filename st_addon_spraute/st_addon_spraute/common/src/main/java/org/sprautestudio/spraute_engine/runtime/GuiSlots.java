package org.sprautestudio.spraute_engine.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.sprautestudio.spraute_engine.gui.GuiDoc;
import org.sprautestudio.spraute_engine.net.Packets;
import org.sprautestudio.spraute_engine.net.SprauteNetwork;

/**
 * Story-side contents of GUI slots. The server owns them: the client only asks to put an item from
 * the player's inventory or to take one, and the server checks the slot's mode, filter and size.
 */
public final class GuiSlots {
    private GuiSlots() {}

    public static ItemStack stack(String id, int count) {
        return id == null || id.isBlank() ? ItemStack.EMPTY : ItemSpec.stack(id, count);
    }

    public static String id(ItemStack st) {
        return st == null || st.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
    }

    /** On opening a GUI: slots never touched get their starting item, and the client gets the current contents. */
    static void opened(SprauteScriptInstance s, GuiDoc doc, ServerPlayer p) {
        s.openGuiDoc = doc;
        for (GuiDoc.Element e : doc.all()) {
            if (!e.type.equals("slot")) continue;
            if (!s.guiSlots.containsKey(e.id)) s.guiSlots.put(e.id, stack(e.get("item"), e.i("count")));
            send(s, p, e.id);
        }
    }

    static void send(SprauteScriptInstance s, ServerPlayer p, String slot) {
        if (p == null) return;
        ItemStack st = s.guiSlots.getOrDefault(slot, ItemStack.EMPTY);
        SprauteNetwork.sendTo(p, new Packets.GuiUpdate(s.getId(), slot, "item", ItemSpec.of(st)));
        SprauteNetwork.sendTo(p, new Packets.GuiUpdate(s.getId(), slot, "count", String.valueOf(Math.max(1, st.getCount()))));
    }

    /** Sets a slot from the story ({@code gui slot}). */
    static void set(SprauteScriptInstance s, String slot, ItemStack st) {
        s.guiSlots.put(slot, st.copy());
        send(s, SprauteRuntime.player(), slot);
    }

    private static void changed(SprauteScriptInstance s, ServerPlayer p, String slot) {
        s.lastSlot = slot;
        s.slotSeq++;
        send(s, p, slot);
    }

    /** The player puts up to {@code count} items from inventory index {@code inv} into a slot. */
    static void put(SprauteScriptInstance s, ServerPlayer p, String slot, int inv, int count) {
        GuiDoc.Element e = s.openGuiDoc == null ? null : s.openGuiDoc.element(slot);
        if (e == null || !e.type.equals("slot")) return;
        String mode = e.get("mode");
        if (!mode.equals("storage") && !mode.equals("put")) return;
        if (inv < 0 || inv >= p.getInventory().items.size()) return;
        ItemStack src = p.getInventory().getItem(inv);
        if (src.isEmpty()) return;
        String filter = e.get("filter").trim();
        if (!filter.isEmpty() && !ItemSpec.matches(src, filter)) {
            send(s, p, slot);
            return;
        }
        ItemStack cur = s.guiSlots.getOrDefault(slot, ItemStack.EMPTY);
        if (!cur.isEmpty() && !ItemStack.isSameItemSameTags(cur, src)) {
            // a different item: swap when the slot can be emptied back, else refuse
            if (!mode.equals("storage")) return;
            ItemStack back = cur.copy();
            s.guiSlots.put(slot, src.copy());
            p.getInventory().setItem(inv, back);
            changed(s, p, slot);
            return;
        }
        int max = Math.max(1, Math.min(e.i("max") > 0 ? e.i("max") : 64, src.getMaxStackSize()));
        int n = Math.min(Math.min(count, src.getCount()), max - cur.getCount());
        if (n <= 0) return;
        ItemStack next = cur.isEmpty() ? src.copyWithCount(n) : cur.copyWithCount(cur.getCount() + n);
        src.shrink(n);
        p.getInventory().setChanged();
        s.guiSlots.put(slot, next);
        changed(s, p, slot);
    }

    /** The player takes up to {@code count} items out of a slot. */
    static void take(SprauteScriptInstance s, ServerPlayer p, String slot, int count) {
        GuiDoc.Element e = s.openGuiDoc == null ? null : s.openGuiDoc.element(slot);
        if (e == null || !e.type.equals("slot")) return;
        String mode = e.get("mode");
        if (!mode.equals("storage") && !mode.equals("take")) return;
        ItemStack cur = s.guiSlots.getOrDefault(slot, ItemStack.EMPTY);
        if (cur.isEmpty()) return;
        int n = Math.min(count, cur.getCount());
        ItemStack give = cur.copyWithCount(n);
        ItemStack rest = cur.copyWithCount(cur.getCount() - n);
        s.guiSlots.put(slot, rest.getCount() > 0 ? rest : ItemStack.EMPTY);
        if (!p.getInventory().add(give)) p.drop(give, false);
        p.getInventory().setChanged();
        changed(s, p, slot);
    }
}
