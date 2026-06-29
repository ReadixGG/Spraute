package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.registry.CustomWorldRegistry;
import org.zonarstudio.spraute_engine.script.ItemStackScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptExecutor;

import java.util.List;

public final class WorldFunctions {

    private WorldFunctions() {}

    private static ServerPlayer requireServerPlayer(Object target, CommandSourceStack source) {
        Player player = ItemStackScriptUtil.resolvePlayer(target, source);
        return player instanceof ServerPlayer sp ? sp : null;
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
            if (player == null || source.getServer() == null) return false;

            String worldId = String.valueOf(args.get(1));
            if (!worldId.contains(":")) {
                worldId = CustomWorldRegistry.dimensionId(worldId);
            }
            ServerLevel targetLevel = ScriptExecutor.resolveLevel(source.getServer(), worldId);
            if (targetLevel == null) return false;

            double x = ((Number) args.get(2)).doubleValue();
            double y = ((Number) args.get(3)).doubleValue();
            double z = ((Number) args.get(4)).doubleValue();
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
    }

    /** getWorldId(worldKey) → "spraute_engine:lobby" */
    public static class GetWorldId implements ScriptFunction {
        @Override public String getName() { return "getWorldId"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return "";
            return CustomWorldRegistry.dimensionId(String.valueOf(args.get(0)));
        }
    }
}
