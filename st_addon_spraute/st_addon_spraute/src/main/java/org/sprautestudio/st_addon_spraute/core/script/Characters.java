package org.sprautestudio.st_addon_spraute.core.script;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.sprautestudio.st_addon_spraute.core.entity.AnimatedEntity;
import org.sprautestudio.st_addon_spraute.core.world.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The bodies of story characters in the world: character id -> entity. */
public final class Characters {
    private Characters() {
    }

    private static final Map<String, UUID> BODIES = new HashMap<>();

    @SuppressWarnings("unchecked")
    public static <T extends Entity> Optional<T> find(Character<T> c) {
        UUID id = c == null ? null : BODIES.get(c.getId());
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (id == null || s == null) return Optional.empty();
        for (ServerLevel l : s.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e != null) return e.isAlive() ? Optional.of((T) e) : Optional.empty();
        }
        return Optional.empty();
    }

    /** Puts the character at a point: moved there if it has a body, else one appears. */
    public static <T extends Entity> void place(Character<T> c, Location at) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null || at == null) return;
        ServerLevel level = s.getLevel(at.dimension);
        if (level == null) level = s.overworld();
        Optional<T> old = find(c);
        if (old.isPresent()) {
            T e = old.get();
            if (e.level() == level) {
                e.teleportTo(at.x, at.y, at.z);
                e.setYRot(at.yaw);
                e.setYHeadRot(at.yaw);
            } else {
                e.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), at.yaw, at.pitch);
            }
            return;
        }
        T e = c.getType().create(level, null, null, BlockPos.containing(at), MobSpawnType.COMMAND, false, false);
        if (e == null) throw new IllegalStateException("Сущность " + c.getId() + " не создаётся");
        e.moveTo(at.x, at.y, at.z, at.yaw, at.pitch);
        e.setYHeadRot(at.yaw);
        e.setCustomName(Component.literal(c.getName()));
        e.setCustomNameVisible(false);
        if (e instanceof AnimatedEntity ae && c.texture() != null) ae.setTextureOverride(c.texture());
        if (e instanceof LivingEntity le && c.speed() != null) {
            var a = le.getAttribute(Attributes.MOVEMENT_SPEED);
            if (a != null) a.setBaseValue(0.25 * c.speed());
        }
        level.addFreshEntity(e);
        BODIES.put(c.getId(), e.getUUID());
    }

    public static void remove(Character<?> c) {
        find(c).ifPresent(Entity::discard);
        if (c != null) BODIES.remove(c.getId());
    }

    /** The story lets go of the body: it stays in the world, nobody's character any more. */
    public static void free(Character<?> c) {
        if (c != null) BODIES.remove(c.getId());
    }

    static CompoundTag save() {
        CompoundTag t = new CompoundTag();
        BODIES.forEach(t::putUUID);
        return t;
    }

    static void load(CompoundTag t) {
        BODIES.clear();
        for (String k : t.getAllKeys()) BODIES.put(k, t.getUUID(k));
    }

    static void clear() {
        BODIES.clear();
    }
}
