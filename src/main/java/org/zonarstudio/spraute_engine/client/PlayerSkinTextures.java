package org.zonarstudio.spraute_engine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class PlayerSkinTextures {
    private PlayerSkinTextures() {}

    public static ResourceLocation resolve(UUID playerUuid) {
        if (playerUuid == null) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity e = mc.level.getPlayerByUUID(playerUuid);
        if (e instanceof AbstractClientPlayer acp) {
            return acp.getSkinTextureLocation();
        }
        if (mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(playerUuid);
            if (info != null) {
                return info.getSkinLocation();
            }
        }
        return null;
    }

    public static ResourceLocation resolveFromTextureKey(String textureKey) {
        if (textureKey == null || textureKey.isEmpty()) return null;
        if (textureKey.startsWith("player_skin:")) {
            try {
                return resolve(UUID.fromString(textureKey.substring("player_skin:".length())));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
