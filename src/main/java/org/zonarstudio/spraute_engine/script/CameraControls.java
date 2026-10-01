package org.zonarstudio.spraute_engine.script;

import org.zonarstudio.spraute_engine.network.CameraPacket;

import java.util.List;
import java.util.Map;

/** Параметры кинематографической камеры на клиенте. */
public final class CameraControls {
    private CameraControls() {}

    public static final boolean DEFAULT_LOCK_MOVEMENT = true;
    public static final boolean DEFAULT_LOCK_BLOCKS = true;
    public static final byte DEFAULT_HIDE_GUI = CameraPacket.HIDE_MINECRAFT;

    public record Settings(boolean lockMovement, byte hideGui, boolean lockBlocks) {
        public static Settings defaults() {
            return new Settings(DEFAULT_LOCK_MOVEMENT, DEFAULT_HIDE_GUI, DEFAULT_LOCK_BLOCKS);
        }

        /** Backward-compat: lockBlocks defaults on. */
        public Settings(boolean lockMovement, byte hideGui) {
            this(lockMovement, hideGui, DEFAULT_LOCK_BLOCKS);
        }
    }

    public static byte parseHideGui(Object value) {
        if (value == null) return DEFAULT_HIDE_GUI;
        if (value instanceof Boolean b) return b ? CameraPacket.HIDE_MINECRAFT : CameraPacket.HIDE_NONE;
        if (value instanceof Number n) {
            int i = n.intValue();
            if (i == CameraPacket.HIDE_NONE || i == CameraPacket.HIDE_MINECRAFT || i == CameraPacket.HIDE_ALL) {
                return (byte) i;
            }
            return i != 0 ? CameraPacket.HIDE_MINECRAFT : CameraPacket.HIDE_NONE;
        }
        String s = String.valueOf(value).trim().toLowerCase();
        return switch (s) {
            case "false", "0", "none", "off", "no" -> CameraPacket.HIDE_NONE;
            case "all", "everything", "full" -> CameraPacket.HIDE_ALL;
            default -> CameraPacket.HIDE_MINECRAFT;
        };
    }

    public static String hideGuiId(byte mode) {
        return switch (mode) {
            case CameraPacket.HIDE_ALL -> "all";
            case CameraPacket.HIDE_MINECRAFT -> "minecraft";
            default -> "none";
        };
    }

    public static boolean parseLockMovement(Object value) {
        return parseBool(value, DEFAULT_LOCK_MOVEMENT);
    }

    public static boolean parseLockBlocks(Object value) {
        return parseBool(value, DEFAULT_LOCK_BLOCKS);
    }

    private static boolean parseBool(Object value, boolean defaultVal) {
        if (value == null) return defaultVal;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        String s = String.valueOf(value).trim().toLowerCase();
        return !("false".equals(s) || "0".equals(s) || "off".equals(s) || "no".equals(s));
    }

    /**
     * Trailing controls: {@code lockMovement}, {@code hideGui}, optional {@code lockBlocks}.
     * Indices are absolute positions in the args list.
     */
    public static Settings parseTrailing(List<Object> args, int lockIndex, int hideIndex) {
        return parseTrailing(args, lockIndex, hideIndex, hideIndex + 1);
    }

    public static Settings parseTrailing(List<Object> args, int lockIndex, int hideIndex, int lockBlocksIndex) {
        boolean lock = args.size() > lockIndex ? parseLockMovement(args.get(lockIndex)) : DEFAULT_LOCK_MOVEMENT;
        byte hide = args.size() > hideIndex ? parseHideGui(args.get(hideIndex)) : DEFAULT_HIDE_GUI;
        boolean blocks = args.size() > lockBlocksIndex ? parseLockBlocks(args.get(lockBlocksIndex)) : DEFAULT_LOCK_BLOCKS;
        return new Settings(lock, hide, blocks);
    }

    public static Settings fromProps(Map<String, List<Object>> props) {
        boolean lock = DEFAULT_LOCK_MOVEMENT;
        byte hide = DEFAULT_HIDE_GUI;
        boolean blocks = DEFAULT_LOCK_BLOCKS;
        if (props.containsKey("lockmovement") || props.containsKey("lock_movement")) {
            List<Object> v = props.getOrDefault("lockmovement", props.get("lock_movement"));
            if (v != null && !v.isEmpty()) lock = parseLockMovement(v.get(0));
        }
        if (props.containsKey("hidegui") || props.containsKey("hide_gui") || props.containsKey("hidehud")) {
            List<Object> v = props.containsKey("hidegui") ? props.get("hidegui")
                    : props.containsKey("hide_gui") ? props.get("hide_gui") : props.get("hidehud");
            if (v != null && !v.isEmpty()) hide = parseHideGui(v.get(0));
        }
        if (props.containsKey("lockblocks") || props.containsKey("lock_blocks")
                || props.containsKey("lockblockinteraction") || props.containsKey("lock_block_interaction")) {
            List<Object> v = props.containsKey("lockblocks") ? props.get("lockblocks")
                    : props.containsKey("lock_blocks") ? props.get("lock_blocks")
                    : props.containsKey("lockblockinteraction") ? props.get("lockblockinteraction")
                    : props.get("lock_block_interaction");
            if (v != null && !v.isEmpty()) blocks = parseLockBlocks(v.get(0));
        }
        return new Settings(lock, hide, blocks);
    }
}
