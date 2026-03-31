package org.zonarstudio.spraute_engine.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.zonarstudio.spraute_engine.entity.NpcManager;

import java.util.UUID;

/**
 * Builds JSON for {@link org.zonarstudio.spraute_engine.network.OpenSprauteUiPacket}.
 * Server expands {@code entity} widgets with UUID / type for the client renderer.
 */
public final class SprauteUiJson {

    private SprauteUiJson() {}

    public static final int MAX_JSON_LENGTH = 120_000;

    /**
     * Validates and enriches UI JSON on the server (entity resolution).
     */
    public static String prepareAndSerialize(ServerLevel level, CommandSourceStack source, String json) {
        if (json == null) json = "{}";
        if (json.length() > MAX_JSON_LENGTH) {
            throw new IllegalArgumentException("UI JSON too large (max " + MAX_JSON_LENGTH + " chars)");
        }
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("w")) root.addProperty("w", 200);
        if (!root.has("h")) root.addProperty("h", 150);
        if (!root.has("bg")) root.addProperty("bg", "#C0101010");
        if (!root.has("widgets")) root.add("widgets", new JsonArray());

        JsonArray widgets = root.getAsJsonArray("widgets");
        for (JsonElement el : widgets) {
            if (!el.isJsonObject()) continue;
            JsonObject w = el.getAsJsonObject();
            String type = w.has("type") ? w.get("type").getAsString() : "";
            if ("entity".equalsIgnoreCase(type) && w.has("entity")) {
                Entity resolved = resolveEntity(level, source, w.get("entity").getAsString());
                if (resolved != null) {
                    w.addProperty("entityUuid", resolved.getUUID().toString());
                    w.addProperty("entityType", net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(resolved.getType()).toString());
                    if (resolved.hasCustomName()) {
                        w.addProperty("entityName", resolved.getCustomName().getString());
                    }
                }
            }
        }
        return root.toString();
    }

    private static Entity resolveEntity(ServerLevel level, CommandSourceStack source, String ref) {
        if (ref == null || ref.isEmpty()) return null;
        try {
            UUID u = UUID.fromString(ref);
            return level.getEntity(u);
        } catch (IllegalArgumentException ignored) {}

        if (ref.startsWith("npc:")) {
            String id = ref.substring(4);
            return NpcManager.getEntity(id, level);
        }
        if ("player".equalsIgnoreCase(ref)) {
            Entity origin = source.getEntity();
            if (origin != null) return level.getNearestPlayer(origin, 64.0);
            return level.getNearestPlayer(source.getPosition().x, source.getPosition().y, source.getPosition().z, 64.0, false);
        }
        if ("npc".equalsIgnoreCase(ref)) {
            return findNearest(level, source, e -> e instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity);
        }
        if ("mob".equalsIgnoreCase(ref)) {
            return findNearest(level, source, e ->
                    e instanceof net.minecraft.world.entity.LivingEntity
                            && !(e instanceof net.minecraft.world.entity.player.Player)
                            && !(e instanceof org.zonarstudio.spraute_engine.entity.SprauteNpcEntity));
        }
        if ("any".equalsIgnoreCase(ref)) {
            return findNearest(level, source, e -> e instanceof net.minecraft.world.entity.LivingEntity);
        }
        Entity byNpc = NpcManager.getEntity(ref, level);
        if (byNpc != null) return byNpc;
        return level.getServer().getPlayerList().getPlayerByName(ref);
    }

    private static Entity findNearest(ServerLevel level, CommandSourceStack source,
                                      java.util.function.Predicate<Entity> filter) {
        Entity origin = source.getEntity();
        if (origin != null) {
            Entity nearest = null;
            double best = Double.MAX_VALUE;
            for (Entity e : level.getEntities(origin, origin.getBoundingBox().inflate(64.0),
                    ex -> ex != null && ex.isAlive() && filter.test(ex))) {
                double d = origin.distanceToSqr(e);
                if (d < best) {
                    best = d;
                    nearest = e;
                }
            }
            return nearest;
        }
        net.minecraft.world.phys.Vec3 pos = source.getPosition();
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pos, pos).inflate(64.0);
        Entity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity e : level.getEntitiesOfClass(Entity.class, box, ex -> ex != null && ex.isAlive() && filter.test(ex))) {
            double d = e.distanceToSqr(pos);
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        return nearest;
    }

    public static ResourceLocation textureRl(String path) {
        if (path == null || path.isEmpty()) {
            return new ResourceLocation("minecraft", "textures/misc/unknown_pack.png");
        }
        if (path.contains(":")) {
            String[] p = path.split(":", 2);
            return new ResourceLocation(p[0], p[1]);
        }
        return new ResourceLocation("minecraft", path);
    }
}
