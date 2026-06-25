package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.zonarstudio.spraute_engine.entity.NpcManager;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.util.EntityFacingUtil;

import java.util.List;
import java.util.UUID;

public class PlayerDirectionFunctions {

    private static LivingEntity resolveLiving(Object arg, CommandSourceStack source) {
        if (arg instanceof LivingEntity living) return living;
        if (source.getLevel() == null) return null;
        if (arg instanceof Entity e && e instanceof LivingEntity livingEnt) return livingEnt;
        if (arg instanceof String s) {
            ServerPlayer byName = source.getServer() != null
                    ? source.getServer().getPlayerList().getPlayerByName(s) : null;
            if (byName != null) return byName;
            UUID uuid = NpcManager.get(s);
            if (uuid != null) {
                Entity ent = source.getLevel().getEntity(uuid);
                if (ent instanceof LivingEntity living) return living;
            }
        }
        return null;
    }

    /** getPlayerFacing(player) → {yaw, pitch, direction, horizontal, dx, dy, dz, hx, hz} */
    public static class GetPlayerFacing implements ScriptFunction {
        @Override public String getName() { return "getPlayerFacing"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return null;
            LivingEntity entity = resolveLiving(args.get(0), source);
            return entity != null ? EntityFacingUtil.buildFacing(entity) : null;
        }
    }
}
