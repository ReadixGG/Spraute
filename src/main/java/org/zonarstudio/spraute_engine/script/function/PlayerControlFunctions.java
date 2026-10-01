package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.script.CameraScriptUtil;
import org.zonarstudio.spraute_engine.script.PlayerControlOverrides;
import org.zonarstudio.spraute_engine.script.PlayerMoveDirs;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.util.EntityFacingUtil;

import java.util.List;

/**
 * Independent player move/look locks and forced facing (not tied to cinematic camera).
 * <pre>
 * lockPlayerMovement(player, true|false)
 * lockPlayerMoveDir(player, dir, [locked])
 * lockPlayerMoveDirs(player, dirs | forward, back, left, right, [north, south, east, west])
 * lockPlayerLook(player, true|false)
 * lockPlayerPerspective(player, true|false)
 * setPlayerLook(player, yaw, pitch)
 * setPlayerLookAt(player, x, y, z)
 * setPlayerLookAt(player, entity)
 * setPlayerLookDir(player, dx, dy, dz)
 * setPlayerPos(player, x, y, z)
 * isPlayerMovementLocked(player)
 * isPlayerMoveDirLocked(player, dir)
 * isPlayerLookLocked(player)
 * isPlayerPerspectiveLocked(player)
 * </pre>
 */
public final class PlayerControlFunctions {
    private PlayerControlFunctions() {}

    private static boolean asBool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.doubleValue() != 0;
        return Boolean.parseBoolean(String.valueOf(o));
    }

    public static class LockPlayerMovement implements ScriptFunction {
        @Override public String getName() { return "lockPlayerMovement"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player == null) return null;
            PlayerControlOverrides.setMoveLocked(player, asBool(args.get(1)));
            return null;
        }
    }

    public static class LockPlayerLook implements ScriptFunction {
        @Override public String getName() { return "lockPlayerLook"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player == null) return null;
            PlayerControlOverrides.setLookLocked(player, asBool(args.get(1)));
            return null;
        }
    }

    public static class LockPlayerPerspective implements ScriptFunction {
        @Override public String getName() { return "lockPlayerPerspective"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player == null) return null;
            PlayerControlOverrides.setPerspectiveLocked(player, asBool(args.get(1)));
            return null;
        }
    }

    public static class IsPlayerMovementLocked implements ScriptFunction {
        @Override public String getName() { return "isPlayerMovementLocked"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return false;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            return player != null && PlayerControlOverrides.isMoveLocked(player);
        }
    }

    public static class IsPlayerLookLocked implements ScriptFunction {
        @Override public String getName() { return "isPlayerLookLocked"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return false;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            return player != null && PlayerControlOverrides.isLookLocked(player);
        }
    }

    public static class IsPlayerPerspectiveLocked implements ScriptFunction {
        @Override public String getName() { return "isPlayerPerspectiveLocked"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return false;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            return player != null && PlayerControlOverrides.isPerspectiveLocked(player);
        }
    }

    /**
     * lockPlayerMoveDir(player, dir, [locked=true])
     * dir: "forward"|"back"|"left"|"right"|"north"|"south"|"east"|"west" or list / comma-string.
     */
    public static class LockPlayerMoveDir implements ScriptFunction {
        @Override public String getName() { return "lockPlayerMoveDir"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player == null) return null;
            int bits = PlayerMoveDirs.parse(args.get(1));
            if (bits == 0) return null;
            boolean locked = args.size() < 3 || PlayerMoveDirs.asBool(args.get(2));
            PlayerControlOverrides.setMoveDir(player, bits, locked);
            return null;
        }
    }

    /**
     * lockPlayerMoveDirs(player) — снять все стороны
     * lockPlayerMoveDirs(player, "forward,north") / список / маска
     * lockPlayerMoveDirs(player, fwd, back, left, right)
     * lockPlayerMoveDirs(player, fwd, back, left, right, north, south, east, west)
     */
    public static class LockPlayerMoveDirs implements ScriptFunction {
        @Override public String getName() { return "lockPlayerMoveDirs"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return null;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player == null) return null;
            if (args.size() == 1) {
                PlayerControlOverrides.setMoveDirMask(player, 0);
                return null;
            }
            if (args.size() >= 5 && PlayerMoveDirs.isBoolish(args.get(1))) {
                int mask = 0;
                if (PlayerMoveDirs.asBool(args.get(1))) mask |= PlayerMoveDirs.FORWARD;
                if (PlayerMoveDirs.asBool(args.get(2))) mask |= PlayerMoveDirs.BACK;
                if (PlayerMoveDirs.asBool(args.get(3))) mask |= PlayerMoveDirs.LEFT;
                if (PlayerMoveDirs.asBool(args.get(4))) mask |= PlayerMoveDirs.RIGHT;
                if (args.size() >= 9) {
                    if (PlayerMoveDirs.asBool(args.get(5))) mask |= PlayerMoveDirs.NORTH;
                    if (PlayerMoveDirs.asBool(args.get(6))) mask |= PlayerMoveDirs.SOUTH;
                    if (PlayerMoveDirs.asBool(args.get(7))) mask |= PlayerMoveDirs.EAST;
                    if (PlayerMoveDirs.asBool(args.get(8))) mask |= PlayerMoveDirs.WEST;
                } else {
                    mask |= PlayerControlOverrides.moveDirMask(player) & PlayerMoveDirs.WORLD;
                }
                PlayerControlOverrides.setMoveDirMask(player, mask);
                return null;
            }
            PlayerControlOverrides.setMoveDirMask(player, PlayerMoveDirs.parse(args.get(1)));
            return null;
        }
    }

    public static class IsPlayerMoveDirLocked implements ScriptFunction {
        @Override public String getName() { return "isPlayerMoveDirLocked"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            int bits = PlayerMoveDirs.parse(args.get(1));
            return player != null && PlayerControlOverrides.isMoveDirLocked(player, bits);
        }
    }

    /** setPlayerLook(player, yaw, pitch) — degrees. */
    public static class SetPlayerLook implements ScriptFunction {
        @Override public String getName() { return "setPlayerLook"; }
        @Override public int getArgCount() { return 3; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Number.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return null;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player == null) return null;
            float yaw = ((Number) args.get(1)).floatValue();
            float pitch = ((Number) args.get(2)).floatValue();
            PlayerControlOverrides.setLook(player, yaw, pitch);
            return null;
        }
    }

    /**
     * setPlayerLookAt(player, x, y, z)
     * setPlayerLookAt(player, entity)
     */
    public static class SetPlayerLookAt implements ScriptFunction {
        @Override public String getName() { return "setPlayerLookAt"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player == null) return null;

            double tx, ty, tz;
            if (args.size() >= 4
                    && args.get(1) instanceof Number
                    && args.get(2) instanceof Number
                    && args.get(3) instanceof Number) {
                tx = ((Number) args.get(1)).doubleValue();
                ty = ((Number) args.get(2)).doubleValue();
                tz = ((Number) args.get(3)).doubleValue();
            } else {
                Entity target = CameraScriptUtil.resolveEntity(args.get(1), source);
                if (target == null) return null;
                tx = target.getX();
                ty = target.getEyeY();
                tz = target.getZ();
            }

            Vec3 eye = player.getEyePosition();
            double dx = tx - eye.x;
            double dy = ty - eye.y;
            double dz = tz - eye.z;
            float[] yp = EntityFacingUtil.yawPitchFromDir(dx, dy, dz);
            if (yp == null) return null;
            PlayerControlOverrides.setLook(player, yp[0], yp[1]);
            return null;
        }
    }

    /** setPlayerLookDir(player, dx, dy, dz) — направление взгляда (вектор, не точка). */
    public static class SetPlayerLookDir implements ScriptFunction {
        @Override public String getName() { return "setPlayerLookDir"; }
        @Override public int getArgCount() { return 4; }
        @Override public Class<?>[] getArgTypes() {
            return new Class<?>[]{Object.class, Number.class, Number.class, Number.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 4) return null;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player == null) return null;
            if (!(args.get(1) instanceof Number dx)
                    || !(args.get(2) instanceof Number dy)
                    || !(args.get(3) instanceof Number dz)) {
                return null;
            }
            float[] yp = EntityFacingUtil.yawPitchFromDir(dx.doubleValue(), dy.doubleValue(), dz.doubleValue());
            if (yp == null) return null;
            PlayerControlOverrides.setLook(player, yp[0], yp[1]);
            return null;
        }
    }

    /** setPlayerPos(player, x, y, z) — телепорт в текущем мире. */
    public static class SetPlayerPos implements ScriptFunction {
        @Override public String getName() { return "setPlayerPos"; }
        @Override public int getArgCount() { return 4; }
        @Override public Class<?>[] getArgTypes() {
            return new Class<?>[]{Object.class, Number.class, Number.class, Number.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 4) return null;
            ServerPlayer player = CameraScriptUtil.resolvePlayer(args.get(0), source);
            if (player == null) return null;
            if (!(args.get(1) instanceof Number x)
                    || !(args.get(2) instanceof Number y)
                    || !(args.get(3) instanceof Number z)) {
                return null;
            }
            PlayerControlOverrides.setPos(player, x.doubleValue(), y.doubleValue(), z.doubleValue());
            return null;
        }
    }
}
