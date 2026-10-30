package org.sprautestudio.st_addon_spraute.core.system;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;

/** The world's money: one purse, kept with the world, known to the clients. */
public final class Coins {
    private Coins() {
    }

    private static final String DATA = "spraute_coins";
    private static int value;

    private static final class Saved extends SavedData {
        int coins;

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putInt("coins", coins);
            return tag;
        }

        static Saved load(CompoundTag tag) {
            Saved s = new Saved();
            s.coins = tag.getInt("coins");
            return s;
        }
    }

    private static Saved saved(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Saved::load, Saved::new, DATA);
    }

    public static int get() {
        return value;
    }

    public static void set(int n) {
        value = Math.max(0, n);
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return;
        Saved d = saved(s);
        d.coins = value;
        d.setDirty();
        SprauteNetwork.sendToAll(new Packets.CoinsSync(value));
    }

    public static void add(int n) {
        set(value + n);
    }

    /** The world opened: its purse. */
    public static void load(MinecraftServer s) {
        value = saved(s).coins;
    }

    public static void receive(int n) {
        value = n;
    }
}
