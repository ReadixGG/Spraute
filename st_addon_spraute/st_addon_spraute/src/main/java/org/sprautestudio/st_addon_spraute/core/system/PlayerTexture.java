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
        final java.util.Map<String, String> textures = new java.util.HashMap<>();

        @Override
        public CompoundTag save(CompoundTag tag) {
            textures.forEach(tag::putString);
            return tag;
        }

        static Saved load(CompoundTag tag) {
            Saved s = new Saved();
            for (String k : tag.getAllKeys()) if (!k.equals("texture")) s.textures.put(k, tag.getString(k));
            return s;
        }
    }

    private static Saved saved(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Saved::load, Saved::new, "spraute_player_texture");
    }

    /** Null texture: back to the player's own. Everyone sees it on that player. */
    public static void set(ServerPlayer p, ResourceLocation texture) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null || p == null) return;
        Saved d = saved(s);
        String id = p.getUUID().toString();
        if (texture == null) d.textures.remove(id);
        else d.textures.put(id, texture.toString());
        d.setDirty();
        SprauteNetwork.sendToAll(new Packets.PlayerSkin(id, texture == null ? "" : texture.toString()));
    }

    /** A player came: everyone's story skins. */
    public static void sendTo(ServerPlayer p) {
        saved(p.server).textures.forEach((id, tex) -> SprauteNetwork.sendTo(p, new Packets.PlayerSkin(id, tex)));
    }
}
