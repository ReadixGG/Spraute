package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.resource.AssetSyncTracker;
import org.zonarstudio.spraute_engine.script.ItemStackScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;
import java.util.UUID;

/** Script queries for in-band asset sync. */
public final class AssetSyncFunctions {
    private AssetSyncFunctions() {}

    private static UUID playerId(Object target, CommandSourceStack source) {
        Player player = ItemStackScriptUtil.resolvePlayer(target, source);
        if (player instanceof ServerPlayer sp) return sp.getUUID();
        if (player != null) return player.getUUID();
        return null;
    }

    public static final class AssetsAllowed implements ScriptFunction {
        @Override public String getName() { return "assetsAllowed"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return false;
            UUID id = playerId(args.get(0), source);
            return id != null && AssetSyncTracker.isAllowed(id);
        }
    }

    public static final class AssetsReady implements ScriptFunction {
        @Override public String getName() { return "assetsReady"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return false;
            UUID id = playerId(args.get(0), source);
            return id != null && AssetSyncTracker.isReady(id);
        }
    }

    public static final class AssetsDenied implements ScriptFunction {
        @Override public String getName() { return "assetsDenied"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return false;
            UUID id = playerId(args.get(0), source);
            return id != null && AssetSyncTracker.isDenied(id);
        }
    }
}
