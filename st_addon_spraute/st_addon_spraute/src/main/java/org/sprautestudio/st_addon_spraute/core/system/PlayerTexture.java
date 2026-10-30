package org.sprautestudio.st_addon_spraute.core.system;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;

/** The story's texture for the player, kept with the world. */
public final class PlayerTexture {
    private PlayerTexture() {
    }

    private static final class Saved extends SavedData {
        String texture = "";

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putString("texture", texture);
            return tag;
        }

        static Saved load(CompoundTag tag) {
            Saved s = new Saved();
            s.texture = tag.getString("texture");
            return s;
        }
    }

    private static Saved saved(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Saved::load, Saved::new, "spraute_player_texture");
    }

    /** Null: back to the player's own. */
    public static void set(ResourceLocation texture) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return;
        Saved d = saved(s);
        d.texture = texture == null ? "" : texture.toString();
        d.setDirty();
        SprauteNetwork.sendToAll(new Packets.PlayerSkin(d.texture));
    }

    public static void sendTo(ServerPlayer p) {
        SprauteNetwork.sendTo(p, new Packets.PlayerSkin(saved(p.server).texture));
    }
}
