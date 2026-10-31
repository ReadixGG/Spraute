package org.sprautestudio.spraute_engine.core.client;

import net.minecraft.resources.ResourceLocation;

/** The texture the story draws the player with ("skin player …"). */
public final class PlayerSkins {
    private PlayerSkins() {
    }

    private static final java.util.Map<java.util.UUID, ResourceLocation> OVERRIDES = new java.util.HashMap<>();

    public static void set(String player, String texture) {
        java.util.UUID id = java.util.UUID.fromString(player);
        if (texture.isEmpty()) OVERRIDES.remove(id);
        else OVERRIDES.put(id, ResourceLocation.tryParse(texture));
    }

    /** Null: the player's own skin. */
    public static ResourceLocation of(java.util.UUID player) {
        return OVERRIDES.get(player);
    }

    public static void clear() {
        OVERRIDES.clear();
    }
}
