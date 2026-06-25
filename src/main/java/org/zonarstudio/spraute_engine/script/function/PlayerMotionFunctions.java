package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.script.PlayerMotionContext;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Player velocity (delta movement) in blocks/tick — {@link net.minecraft.world.entity.Entity#getDeltaMovement()}.
 */
public class PlayerMotionFunctions {

    private static ServerPlayer resolvePlayer(Object arg, CommandSourceStack source) {
        if (arg instanceof ServerPlayer sp) return sp;
        if (arg instanceof net.minecraft.world.entity.player.Player p && source.getServer() != null) {
            ServerPlayer byUuid = source.getServer().getPlayerList().getPlayer(p.getUUID());
            if (byUuid != null) return byUuid;
        }
        if (source.getServer() == null) return null;
        return source.getServer().getPlayerList().getPlayerByName(String.valueOf(arg));
    }

    private static List<Object> vecToList(Vec3 v) {
        List<Object> out = new ArrayList<>(3);
        out.add(v.x);
        out.add(v.y);
        out.add(v.z);
        return out;
    }

    /** [x, y, z] or x, y, z starting at offset. */
    private static Vec3 parseVec(List<Object> args, int offset) {
        if (args.size() >= offset + 3
                && args.get(offset) instanceof Number
                && args.get(offset + 1) instanceof Number
                && args.get(offset + 2) instanceof Number) {
            return new Vec3(
                    ((Number) args.get(offset)).doubleValue(),
                    ((Number) args.get(offset + 1)).doubleValue(),
                    ((Number) args.get(offset + 2)).doubleValue()
            );
        }
        if (args.size() > offset && args.get(offset) instanceof List<?> list && list.size() >= 3) {
            Object ax = list.get(0);
            Object ay = list.get(1);
            Object az = list.get(2);
            if (ax instanceof Number && ay instanceof Number && az instanceof Number) {
                return new Vec3(
                        ((Number) ax).doubleValue(),
                        ((Number) ay).doubleValue(),
                        ((Number) az).doubleValue()
                );
            }
        }
        return null;
    }

    /** getPlayerMotion(player) → [vx, vy, vz] */
    public static class GetPlayerMotion implements ScriptFunction {
        @Override public String getName() { return "getPlayerMotion"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return null;
            ServerPlayer player = resolvePlayer(args.get(0), source);
            if (player == null) return null;
            return vecToList(player.getDeltaMovement());
        }
    }

    /** setPlayerMotion(player, vx, vy, vz) or setPlayerMotion(player, [vx, vy, vz]) */
    public static class SetPlayerMotion implements ScriptFunction {
        @Override public String getName() { return "setPlayerMotion"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            ServerPlayer player = resolvePlayer(args.get(0), source);
            if (player == null) return null;
            Vec3 motion = parseVec(args, 1);
            if (motion == null) return null;
            player.setDeltaMovement(motion);
            player.hurtMarked = true;
            return null;
        }
    }

    /** addPlayerMotion(player, dx, dy, dz) — добавить к текущей скорости, не заменяя её */
    public static class AddPlayerMotion implements ScriptFunction {
        @Override public String getName() { return "addPlayerMotion"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            ServerPlayer player = resolvePlayer(args.get(0), source);
            if (player == null) return null;
            Vec3 delta = parseVec(args, 1);
            if (delta == null) return null;
            Vec3 cur = player.getDeltaMovement();
            double nx = cur.x;
            double nz = cur.z;
            boolean verticalOnly = Math.abs(delta.x) < 1.0e-9 && Math.abs(delta.z) < 1.0e-9;
            if (verticalOnly) {
                double[] preserved = PlayerMotionContext.consumePreservedHorizontal(player.getUUID());
                if (preserved != null) {
                    nx = preserved[0];
                    nz = preserved[1];
                }
            } else {
                nx = cur.x + delta.x;
                nz = cur.z + delta.z;
            }
            double ny = cur.y + delta.y;
            player.setDeltaMovement(nx, ny, nz);
            player.hurtMarked = true;
            return null;
        }
    }
}
