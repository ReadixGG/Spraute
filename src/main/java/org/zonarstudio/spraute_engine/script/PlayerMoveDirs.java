package org.zonarstudio.spraute_engine.script;

import net.minecraft.util.Mth;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Bitmask of blocked player walk directions.
 * Relative bits are WASD in look/body space; world bits are Minecraft axes
 * (north = −Z, south = +Z, west = −X, east = +X).
 */
public final class PlayerMoveDirs {
    public static final int FORWARD = 1;
    public static final int BACK = 1 << 1;
    public static final int LEFT = 1 << 2;
    public static final int RIGHT = 1 << 3;
    public static final int NORTH = 1 << 4;
    public static final int SOUTH = 1 << 5;
    public static final int EAST = 1 << 6;
    public static final int WEST = 1 << 7;

    public static final int RELATIVE = FORWARD | BACK | LEFT | RIGHT;
    public static final int WORLD = NORTH | SOUTH | EAST | WEST;

    private PlayerMoveDirs() {}

    public static boolean has(int mask, int bit) {
        return (mask & bit) != 0;
    }

    public static int with(int mask, int bits, boolean locked) {
        return locked ? (mask | bits) : (mask & ~bits);
    }

    /** Parse a name, list, comma-string, or integer bitmask. Unknown tokens are ignored. */
    public static int parse(Object raw) {
        if (raw == null) return 0;
        if (raw instanceof Number n) return n.intValue() & (RELATIVE | WORLD);
        if (raw instanceof Boolean) return 0;
        if (raw instanceof Collection<?> list) {
            int mask = 0;
            for (Object item : list) mask |= parse(item);
            return mask;
        }
        if (raw instanceof Map<?, ?> map) {
            int mask = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (asBool(e.getValue())) mask |= parse(e.getKey());
            }
            return mask;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) return 0;
        int mask = 0;
        for (String part : text.split("[,;|/\\s]+")) {
            mask |= parseOne(part);
        }
        return mask;
    }

    public static int parseOne(String raw) {
        if (raw == null) return 0;
        String key = raw.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (key.isEmpty()) return 0;
        return switch (key) {
            case "forward", "fwd", "w", "вперёд", "вперед" -> FORWARD;
            case "back", "backward", "backwards", "s", "назад" -> BACK;
            case "left", "strafeleft", "a", "влево" -> LEFT;
            case "right", "straferight", "d", "вправо" -> RIGHT;
            case "north", "n", "север", "-z" -> NORTH;
            case "south", "юг", "+z" -> SOUTH;
            case "east", "e", "восток", "+x" -> EAST;
            case "west", "запад", "-x" -> WEST;
            case "wasd", "relative", "local", "look" -> RELATIVE;
            case "world", "axes", "nesw", "мир" -> WORLD;
            case "all", "any", "все", "всё" -> RELATIVE | WORLD;
            default -> 0;
        };
    }

    public static String toNames(int mask) {
        if (mask == 0) return "";
        StringBuilder sb = new StringBuilder();
        append(sb, mask, FORWARD, "forward");
        append(sb, mask, BACK, "back");
        append(sb, mask, LEFT, "left");
        append(sb, mask, RIGHT, "right");
        append(sb, mask, NORTH, "north");
        append(sb, mask, SOUTH, "south");
        append(sb, mask, EAST, "east");
        append(sb, mask, WEST, "west");
        return sb.toString();
    }

    /**
     * Zero forbidden WASD components, then zero world-axis components of the
     * resulting walk vector. {@code out} is {@code [leftImpulse, forwardImpulse]}.
     */
    public static void clamp(float left, float forward, float yRotDeg, int mask, float[] out) {
        if (out == null || out.length < 2) return;
        if (mask == 0) {
            out[0] = left;
            out[1] = forward;
            return;
        }
        if ((mask & FORWARD) != 0 && forward > 0f) forward = 0f;
        if ((mask & BACK) != 0 && forward < 0f) forward = 0f;
        if ((mask & LEFT) != 0 && left > 0f) left = 0f;
        if ((mask & RIGHT) != 0 && left < 0f) left = 0f;

        int world = mask & WORLD;
        if (world != 0) {
            float yaw = yRotDeg * ((float) Math.PI / 180f);
            float sin = Mth.sin(yaw);
            float cos = Mth.cos(yaw);
            float wx = left * cos - forward * sin;
            float wz = forward * cos + left * sin;
            if ((world & EAST) != 0 && wx > 0f) wx = 0f;
            if ((world & WEST) != 0 && wx < 0f) wx = 0f;
            if ((world & SOUTH) != 0 && wz > 0f) wz = 0f;
            if ((world & NORTH) != 0 && wz < 0f) wz = 0f;
            left = wx * cos + wz * sin;
            forward = wz * cos - wx * sin;
        }
        out[0] = left;
        out[1] = forward;
    }

    public static boolean asBool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.doubleValue() != 0;
        if (o == null) return false;
        String t = String.valueOf(o).trim();
        return "true".equalsIgnoreCase(t) || "1".equals(t) || "yes".equalsIgnoreCase(t)
                || "да".equalsIgnoreCase(t);
    }

    public static boolean isBoolish(Object o) {
        if (o instanceof Boolean || o instanceof Number) return true;
        if (o == null) return false;
        String t = String.valueOf(o).trim();
        return "true".equalsIgnoreCase(t) || "false".equalsIgnoreCase(t)
                || "1".equals(t) || "0".equals(t)
                || "yes".equalsIgnoreCase(t) || "no".equalsIgnoreCase(t)
                || "да".equalsIgnoreCase(t) || "нет".equalsIgnoreCase(t);
    }

    private static void append(StringBuilder sb, int mask, int bit, String name) {
        if ((mask & bit) == 0) return;
        if (!sb.isEmpty()) sb.append(',');
        sb.append(name);
    }
}
