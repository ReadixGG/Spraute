package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.registry.CustomWorldRegistry;
import org.zonarstudio.spraute_engine.script.ItemStackScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptExecutor;
import org.zonarstudio.spraute_engine.script.DimensionScriptUtil;
import org.zonarstudio.spraute_engine.util.SprauteResourcePath;

import java.util.List;

public final class WorldFunctions {

    private WorldFunctions() {}

    private static ServerPlayer requireServerPlayer(Object target, CommandSourceStack source) {
        Player player = ItemStackScriptUtil.resolvePlayer(target, source);
        return player instanceof ServerPlayer sp ? sp : null;
    }

    /** Resolves script world key to full dimension id; sends failure to source on error. */
    private static String resolveTeleportWorldId(String worldId, CommandSourceStack source) {
        if (worldId == null || worldId.isBlank()) return null;
        String trimmed = worldId.trim();
        if (trimmed.contains(":")) return trimmed;

        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("overworld") || lower.equals("nether") || lower.equals("the_nether")
                || lower.equals("end") || lower.equals("the_end")) {
            return DimensionScriptUtil.normalizeDimensionId(trimmed);
        }

        SprauteResourcePath.Result idCheck =
                SprauteResourcePath.validateSimpleId(trimmed, SprauteResourcePath.Kind.WORLD);
        if (!idCheck.ok()) {
            source.sendFailure(net.minecraft.network.chat.Component.translatable(
                    "spraute_engine.error.invalid_world_id", trimmed, idCheck.invalidChars()));
            return null;
        }
        if (!CustomWorldRegistry.hasWorld(trimmed)) {
            source.sendFailure(net.minecraft.network.chat.Component.translatable(
                    "spraute_engine.error.world_not_found", trimmed));
            return null;
        }
        return CustomWorldRegistry.dimensionId(trimmed);
    }

    private static boolean teleportPlayerTo(ServerPlayer player, CommandSourceStack source,
                                            String worldIdOrNull, double x, double y, double z) {
        if (player == null || source.getServer() == null) return false;

        if (worldIdOrNull == null || worldIdOrNull.isBlank()) {
            player.teleportTo(x, y, z);
            return true;
        }

        String worldId = resolveTeleportWorldId(worldIdOrNull, source);
        if (worldId == null) return false;

        ServerLevel targetLevel = ScriptExecutor.resolveLevel(source.getServer(), worldId);
        if (targetLevel == null) {
            source.sendFailure(net.minecraft.network.chat.Component.translatable(
                    "spraute_engine.error.dimension_not_loaded", worldId));
            return false;
        }

        String fromDim = org.zonarstudio.spraute_engine.compat.SprauteEntityCompat.serverLevel(player)
                .dimension().location().toString();
        player.teleportTo(targetLevel, x, y, z, player.getYRot(), player.getXRot());
        String toDim = targetLevel.dimension().location().toString();
        if (!fromDim.equals(toDim)) {
            org.zonarstudio.spraute_engine.script.ScriptManager.getInstance()
                    .onPlayerDimensionChange(player, fromDim, toDim);
        }
        return true;
    }

    /**
     * teleportPlayer(player, x, y, z) — в текущем измерении.
     * teleportPlayer(player, worldId, x, y, z) — с переходом в другое измерение.
     */
    public static class TeleportPlayer implements ScriptFunction {
        @Override public String getName() { return "teleportPlayer"; }
        @Override public int getArgCount() { return 4; }
        @Override public Class<?>[] getArgTypes() {
            return new Class<?>[]{Object.class, Object.class, Number.class, Number.class, Number.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 4) return false;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return false;

            String worldId = null;
            int coordStart;
            if (args.size() >= 5 && !(args.get(1) instanceof Number)) {
                worldId = String.valueOf(args.get(1));
                coordStart = 2;
            } else {
                coordStart = 1;
            }
            if (args.size() < coordStart + 3) return false;
            if (!(args.get(coordStart) instanceof Number)
                    || !(args.get(coordStart + 1) instanceof Number)
                    || !(args.get(coordStart + 2) instanceof Number)) {
                return false;
            }

            double x = ((Number) args.get(coordStart)).doubleValue();
            double y = ((Number) args.get(coordStart + 1)).doubleValue();
            double z = ((Number) args.get(coordStart + 2)).doubleValue();
            return teleportPlayerTo(player, source, worldId, x, y, z);
        }
    }

    /** teleportToWorld(player, worldId, x, y, z) — телепорт в create world измерение. */
    public static class TeleportToWorld implements ScriptFunction {
        @Override public String getName() { return "teleportToWorld"; }
        @Override public int getArgCount() { return 5; }
        @Override public Class<?>[] getArgTypes() {
            return new Class<?>[]{Object.class, String.class, Number.class, Number.class, Number.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 5) return false;
            ServerPlayer player = requireServerPlayer(args.get(0), source);
            if (player == null) return false;

            String worldId = String.valueOf(args.get(1));
            double x = ((Number) args.get(2)).doubleValue();
            double y = ((Number) args.get(3)).doubleValue();
            double z = ((Number) args.get(4)).doubleValue();
            return teleportPlayerTo(player, source, worldId, x, y, z);
        }
    }

    /** getWorldId(worldKey) → "spraute_engine:lobby" */
    public static class GetWorldId implements ScriptFunction {
        @Override public String getName() { return "getWorldId"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return "";
            String raw = String.valueOf(args.get(0));
            SprauteResourcePath.Result idCheck =
                    SprauteResourcePath.validateSimpleId(raw, SprauteResourcePath.Kind.WORLD);
            if (!idCheck.ok()) {
                source.sendFailure(net.minecraft.network.chat.Component.translatable(
                        "spraute_engine.error.invalid_world_id", raw, idCheck.invalidChars()));
                return "";
            }
            return CustomWorldRegistry.dimensionId(raw);
        }
    }

    /** getPlayerWorldId(player) → "minecraft:overworld" / "spraute_engine:lobby" */
    public static class GetPlayerWorldId implements ScriptFunction {
        @Override public String getName() { return "getPlayerWorldId"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return "";
            net.minecraft.world.entity.player.Player player = ItemStackScriptUtil.resolvePlayer(args.get(0), source);
            return DimensionScriptUtil.getDimensionId(player);
        }
    }
}
