package org.sprautestudio.st_addon_spraute.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.lang.ItemText;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An item as written anywhere in a project: an id with NBT after it if wanted, as in Minecraft's own commands:
 * {@code minecraft:diamond_sword{Damage:5,Enchantments:[{id:"sharpness",lvl:3s}]}}.
 * When an item is looked for in an inventory, the NBT written must be on it; without NBT any such item counts.
 */
public final class ItemSpec {
    private ItemSpec() {
    }

    private record Parsed(String id, String nbtText, CompoundTag nbt, String error) {
    }

    private static final Map<String, Parsed> CACHE = new ConcurrentHashMap<>();

    /** Gives the language, which knows nothing of the game, the game's reading of NBT. */
    public static void install() {
        ItemText.setNbtCheck(text -> {
            try {
                TagParser.parseTag(text);
                return null;
            } catch (Exception e) {
                return e.getMessage();
            }
        });
    }

    private static Parsed parse(String spec) {
        String key = spec == null ? "" : spec.trim();
        Parsed have = CACHE.get(key);
        if (have != null) return have;
        String id = ItemText.id(key), text = ItemText.nbt(key);
        CompoundTag nbt = null;
        String error = null;
        if (!text.isEmpty()) {
            try {
                nbt = TagParser.parseTag(text);
            } catch (Exception e) {
                error = e.getMessage();
                St_addon_spraute.LOGGER.warn("Spraute: bad NBT of item {}: {}", key, error);
            }
        }
        Parsed p = new Parsed(id, text, nbt, error);
        if (CACHE.size() > 1024) CACHE.clear();
        CACHE.put(key, p);
        return p;
    }

    /** The id alone, with its namespace; "" when nothing is written. */
    public static String id(String spec) {
        return parse(spec).id;
    }

    /** The NBT as written; "" when there is none. */
    public static String nbt(String spec) {
        return parse(spec).nbtText;
    }

    /** Why the NBT cannot be read; null when it can (or there is none). */
    public static String error(String spec) {
        return parse(spec).error;
    }

    public static String with(String id, String nbt) {
        String text = nbt == null ? "" : nbt.trim();
        return text.isEmpty() ? id(id) : id(id) + text;
    }

    /** The item itself; air when there is no such item. */
    public static Item item(String spec) {
        ResourceLocation rl = ResourceLocation.tryParse(id(spec));
        return rl == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(rl).orElse(Items.AIR);
    }

    /** The stack; empty when there is no such item. */
    public static ItemStack stack(String spec, int count) {
        Item item = item(spec);
        if (item == Items.AIR) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(item, Math.max(1, count));
        CompoundTag nbt = parse(spec).nbt;
        if (nbt != null) s.setTag(nbt.copy());
        return s;
    }

    /** A stack written back as a spec, to send to a client or keep as text. */
    public static String of(ItemStack st) {
        if (st == null || st.isEmpty()) return "";
        String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
        return st.hasTag() ? id + st.getTag() : id;
    }

    public static boolean matches(ItemStack st, String spec) {
        if (st == null || st.isEmpty() || !st.is(item(spec))) return false;
        CompoundTag nbt = parse(spec).nbt;
        return nbt == null || NbtUtils.compareNbt(nbt, st.getTag(), true);
    }

    public static int count(Player p, String spec) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (matches(s, spec)) n += s.getCount();
        }
        return n;
    }

    /** Takes up to {@code n} from the player; how many were taken. */
    public static int take(Player p, String spec, int n) {
        int left = n;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (matches(s, spec)) {
                int k = Math.min(left, s.getCount());
                s.shrink(k);
                left -= k;
            }
        }
        inv.setChanged();
        return n - left;
    }
}
