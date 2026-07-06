package org.zonarstudio.spraute_engine.script;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Filter for player-targeted {@code on}/{@code await} handlers.
 * Supports a single player, a list of players, or {@code any} (all online players).
 */
public final class PlayerFilter {

    private static final PlayerFilter ANY = new PlayerFilter(true, Set.of());

    private final boolean any;
    private final Set<UUID> uuids;

    private PlayerFilter(boolean any, Set<UUID> uuids) {
        this.any = any;
        this.uuids = uuids;
    }

    public static PlayerFilter any() {
        return ANY;
    }

    public static PlayerFilter of(UUID uuid) {
        if (uuid == null) return null;
        return new PlayerFilter(false, Set.of(uuid));
    }

    public static PlayerFilter of(Collection<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) return null;
        return new PlayerFilter(false, Set.copyOf(uuids));
    }

    public boolean isAny() {
        return any;
    }

    public boolean matches(UUID playerUuid) {
        if (playerUuid == null) return false;
        if (any) return true;
        return uuids.contains(playerUuid);
    }

    public UUID firstOrNull() {
        if (any || uuids.isEmpty()) return null;
        return uuids.iterator().next();
    }

    /**
     * @return filter or {@code null} when nothing could be resolved
     */
    public static PlayerFilter from(
            Object arg,
            Function<Object, ServerPlayer> serverPlayerResolver,
            Function<Object, Entity> entityResolver) {
        if (arg == null) return null;
        if (arg instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) return null;
            if (isAnyKeyword(trimmed)) return any();
        }

        if (arg instanceof Collection<?> collection) {
            if (collection.isEmpty()) return any();
            Set<UUID> out = new HashSet<>();
            for (Object item : collection) {
                collectUuid(item, out, serverPlayerResolver, entityResolver);
            }
            return out.isEmpty() ? null : of(out);
        }

        Set<UUID> one = new HashSet<>();
        collectUuid(arg, one, serverPlayerResolver, entityResolver);
        return one.isEmpty() ? null : of(one);
    }

    public static boolean matchesEventArg(
            Object arg,
            UUID playerUuid,
            Function<Object, ServerPlayer> serverPlayerResolver,
            Function<Object, Entity> entityResolver) {
        if (arg == null) return true;
        PlayerFilter filter = from(arg, serverPlayerResolver, entityResolver);
        if (filter == null) return false;
        return filter.matches(playerUuid);
    }

    private static void collectUuid(
            Object item,
            Set<UUID> out,
            Function<Object, ServerPlayer> serverPlayerResolver,
            Function<Object, Entity> entityResolver) {
        if (item == null) return;
        if (item instanceof String s && isAnyKeyword(s.trim())) return;
        ServerPlayer sp = serverPlayerResolver.apply(item);
        if (sp != null) {
            out.add(sp.getUUID());
            return;
        }
        Entity e = entityResolver.apply(item);
        if (e != null) out.add(e.getUUID());
    }

    private static boolean isAnyKeyword(String value) {
        return "any".equalsIgnoreCase(value)
                || "all".equalsIgnoreCase(value)
                || "*".equals(value)
                || "любой".equalsIgnoreCase(value);
    }
}
