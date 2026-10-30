package org.sprautestudio.st_addon_spraute.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * A story value that names one entity (a summoned zombie, the player, a character): found again by
 * its UUID in any dimension, so it survives saving. Prints as the entity's name.
 */
public final class EntityRef {
    public final UUID uuid;
    public final String type;
    private Entity cache;

    public EntityRef(Entity e) {
        this.uuid = e.getUUID();
        this.type = e instanceof Player ? "player" : String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
        this.cache = e;
    }

    public EntityRef(UUID uuid, String type) {
        this.uuid = uuid;
        this.type = type;
    }

    /** The entity, or null when it is gone (killed, unloaded). */
    public Entity get() {
        if (cache != null && !cache.isRemoved()) return cache;
        cache = null;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e != null) {
                cache = e;
                return e;
            }
        }
        return null;
    }

    /** The entity, or an error saying it is gone. */
    public Entity require() {
        Entity e = get();
        if (e == null) throw new SprauteRuntimeException("Сущность " + type + " пропала (убита или далеко)");
        return e;
    }

    public boolean alive() {
        Entity e = get();
        return e != null && e.isAlive();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EntityRef r && r.uuid.equals(uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        Entity e = get();
        return e != null ? e.getName().getString() : type;
    }
}
