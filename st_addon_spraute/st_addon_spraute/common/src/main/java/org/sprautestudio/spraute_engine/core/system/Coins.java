package org.sprautestudio.spraute_engine.core.system;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.sprautestudio.spraute_engine.net.Packets;
import org.sprautestudio.spraute_engine.net.SprauteNetwork;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Money: a purse for each player, kept with the world. The client knows its own and how much it is told it needs. */
public final class Coins {
    private Coins() {
    }

    private static final String DATA = "spraute_coins";
    private static int shown, shownNeed = -1;

    private static final class Saved extends SavedData {
        final Map<UUID, Integer> purses = new HashMap<>();

        @Override
        public CompoundTag save(CompoundTag tag) {
            purses.forEach((id, n) -> tag.putInt(id.toString(), n));
            return tag;
        }

        static Saved load(CompoundTag tag) {
            Saved s = new Saved();
            for (String k : tag.getAllKeys()) s.purses.put(UUID.fromString(k), tag.getInt(k));
            return s;
        }
    }

    private static Saved saved(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Saved::load, Saved::new, DATA);
    }

    public static int get(ServerPlayer p) {
        return p == null ? 0 : saved(p.server).purses.getOrDefault(p.getUUID(), 0);
    }

    public static void set(ServerPlayer p, int n) {
        if (p == null) return;
        Saved d = saved(p.server);
        d.purses.put(p.getUUID(), Math.max(0, n));
        d.setDirty();
        sync(p, -2);
    }

    public static void add(ServerPlayer p, int n) {
        set(p, get(p) + n);
    }

    /** "Needs this much" is shown beside the money (-1 — nothing). */
    public static void need(ServerPlayer p, int n) {
        if (p != null) sync(p, n);
    }

    private static final Map<UUID, Integer> NEEDS = new HashMap<>();

    /** Sends the player's money; -2 keeps what it was told it needs. */
    public static void sync(ServerPlayer p, int need) {
        if (need != -2) NEEDS.put(p.getUUID(), need);
        SprauteNetwork.sendTo(p, new Packets.CoinsSync(get(p), NEEDS.getOrDefault(p.getUUID(), -1)));
    }

    public static int shown() {
        return shown;
    }

    public static int shownNeed() {
        return shownNeed;
    }

    public static void receive(int n, int need) {
        shown = n;
        shownNeed = need;
    }
}
