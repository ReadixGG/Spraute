package org.zonarstudio.spraute_engine.scene;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.PlayerSkinLayersPacket;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Script/editor hide mask for the player's original/installed outer skin
 * (hat, jacket, sleeves, pants, cape). Overlays use {@link SceneNode#hiddenSkinLayers}
 * on {@link SceneNodeType#SKIN_OVERLAY} instead. Node = code.
 */
public final class PlayerSkinLayers {
    public static final int HAT = PlayerModelPart.HAT.getMask();
    public static final int CAPE = PlayerModelPart.CAPE.getMask();
    public static final int JACKET = PlayerModelPart.JACKET.getMask();
    public static final int LEFT_SLEEVE = PlayerModelPart.LEFT_SLEEVE.getMask();
    public static final int RIGHT_SLEEVE = PlayerModelPart.RIGHT_SLEEVE.getMask();
    public static final int LEFT_PANTS = PlayerModelPart.LEFT_PANTS_LEG.getMask();
    public static final int RIGHT_PANTS = PlayerModelPart.RIGHT_PANTS_LEG.getMask();
    public static final int SLEEVES = LEFT_SLEEVE | RIGHT_SLEEVE;
    /** Jacket + both sleeves (second layer of the upper body). */
    public static final int TORSO = JACKET | SLEEVES;
    public static final int PANTS = LEFT_PANTS | RIGHT_PANTS;
    public static final int CLOTHING = HAT | JACKET | SLEEVES | PANTS;
    public static final int ALL = CLOTHING | CAPE;

    private static final Map<UUID, Integer> HIDDEN = new ConcurrentHashMap<>();

    private PlayerSkinLayers() {}

    public static int get(UUID uuid) {
        if (uuid == null) return 0;
        return HIDDEN.getOrDefault(uuid, 0);
    }

    public static boolean has(UUID uuid) {
        return uuid != null && HIDDEN.containsKey(uuid);
    }

    public static boolean isHidden(UUID uuid, int partMask) {
        if (partMask == 0) return false;
        int hide = get(uuid);
        return (hide & partMask) == partMask;
    }

    public static void replace(UUID uuid, int mask) {
        if (uuid == null) return;
        int next = mask & ALL;
        if (next == 0) HIDDEN.remove(uuid);
        else HIDDEN.put(uuid, next);
        syncNode(uuid, next);
        broadcast(uuid, next);
    }

    public static void setBits(UUID uuid, int partMask, boolean hidden) {
        if (uuid == null || partMask == 0) return;
        int bits = partMask & ALL;
        int current = get(uuid);
        replace(uuid, hidden ? (current | bits) : (current & ~bits));
    }

    public static void clear(UUID uuid) {
        replace(uuid, 0);
    }

    public static void sendAllTo(ServerPlayer joiner) {
        if (joiner == null) return;
        for (var entry : HIDDEN.entrySet()) {
            sendTo(joiner, entry.getKey(), entry.getValue());
        }
    }

    public static void applyToPlayerNode(SceneNode player) {
        UUID uuid = PlayerSkinOps.parseUuid(player == null ? null : player.playerUuid);
        if (uuid == null) return;
        player.hiddenSkinLayers = get(uuid);
    }

    public static void applyFromPlayerNode(SceneNode player) {
        UUID uuid = PlayerSkinOps.parseUuid(player == null ? null : player.playerUuid);
        if (uuid == null) return;
        replace(uuid, player.hiddenSkinLayers);
    }

    /** {@code hideHat} / {@code hide_hat} after {@code playerKey} (no underscores). */
    public static boolean isProperty(String key) {
        return partMaskForProperty(key) != 0;
    }

    public static Object getProperty(UUID uuid, String key) {
        int bits = partMaskForProperty(key);
        if (bits == 0) return null;
        return isHidden(uuid, bits);
    }

    public static boolean setProperty(UUID uuid, String key, boolean hidden) {
        int bits = partMaskForProperty(key);
        if (bits == 0) return false;
        setBits(uuid, bits, hidden);
        return true;
    }

    public static int parseParts(String name) {
        if (name == null) return 0;
        String key = name.trim().toLowerCase(Locale.ROOT).replace("-", "_");
        return switch (key) {
            case "hat", "head", "helmet", "hat_overlay", "head_overlay" -> HAT;
            case "jacket", "shirt" -> JACKET;
            case "torso", "body", "chest", "upper" -> TORSO;
            case "left_sleeve", "leftsleeve", "left_arm", "leftarm" -> LEFT_SLEEVE;
            case "right_sleeve", "rightsleeve", "right_arm", "rightarm" -> RIGHT_SLEEVE;
            case "sleeves", "arms", "hands", "sleeves_both" -> SLEEVES;
            case "left_pants", "leftpants", "left_leg", "leftleg", "left_pants_leg" -> LEFT_PANTS;
            case "right_pants", "rightpants", "right_leg", "rightleg", "right_pants_leg" -> RIGHT_PANTS;
            case "pants", "legs", "trousers" -> PANTS;
            case "cape", "cloak" -> CAPE;
            case "clothing", "outer", "layers", "all_clothing" -> CLOTHING;
            case "all", "everything" -> ALL;
            default -> 0;
        };
    }

    public static Object getOverlayProperty(SceneNode overlay, String key) {
        if (overlay == null || overlay.type != SceneNodeType.SKIN_OVERLAY) return null;
        int bits = partMaskForProperty(key) & CLOTHING;
        if (bits == 0) return null;
        return (overlay.hiddenSkinLayers & bits) == bits;
    }

    public static boolean setOverlayProperty(SceneNode overlay, String key, boolean hidden) {
        if (overlay == null || overlay.type != SceneNodeType.SKIN_OVERLAY) return false;
        int bits = partMaskForProperty(key) & CLOTHING;
        if (bits == 0) return false;
        overlay.hiddenSkinLayers = hidden
                ? (overlay.hiddenSkinLayers | bits)
                : (overlay.hiddenSkinLayers & ~bits);
        return true;
    }

    public static void applyOverlayProps(SceneNode overlay, java.util.Map<String, java.util.List<Object>> props) {
        if (overlay == null || overlay.type != SceneNodeType.SKIN_OVERLAY || props == null) return;
        int mask = overlay.hiddenSkinLayers;
        for (var entry : props.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT).replace("_", "");
            java.util.List<Object> values = entry.getValue();
            if (values == null || values.isEmpty()) continue;
            if ("hideskinlayer".equals(key) || "hideouterlayer".equals(key)) {
                int bits = parseParts(String.valueOf(values.get(0))) & CLOTHING;
                if (bits != 0) mask |= bits;
                continue;
            }
            int bits = partMaskForProperty(key) & CLOTHING;
            if (bits == 0) continue;
            mask = truthy(values.get(0)) ? (mask | bits) : (mask & ~bits);
        }
        overlay.hiddenSkinLayers = mask;
    }

    public static Object invokeOverlay(SceneNode overlay, String method, java.util.List<Object> args) {
        if (overlay == null || overlay.type != SceneNodeType.SKIN_OVERLAY || method == null) {
            return PlayerSkinOps.UNHANDLED;
        }
        String m = method.toLowerCase(Locale.ROOT).replace("_", "");
        return switch (m) {
            case "hideskinlayer", "hideouterlayer", "hidejacketlayer" -> {
                int bits = args.isEmpty() ? CLOTHING : parseParts(String.valueOf(args.get(0))) & CLOTHING;
                if (bits == 0) bits = CLOTHING;
                overlay.hiddenSkinLayers |= bits;
                PlayerSkinOps.onNodeChanged(overlay);
                yield PlayerSkinOps.HANDLED_NULL;
            }
            case "showskinlayer", "showouterlayer" -> {
                int bits = args.isEmpty() ? CLOTHING : parseParts(String.valueOf(args.get(0))) & CLOTHING;
                if (bits == 0) bits = CLOTHING;
                overlay.hiddenSkinLayers &= ~bits;
                PlayerSkinOps.onNodeChanged(overlay);
                yield PlayerSkinOps.HANDLED_NULL;
            }
            case "setskinlayer", "setouterlayer" -> {
                if (args.isEmpty()) yield PlayerSkinOps.HANDLED_NULL;
                int bits = parseParts(String.valueOf(args.get(0))) & CLOTHING;
                boolean visible = args.size() < 2 || truthy(args.get(1));
                if (bits != 0) {
                    overlay.hiddenSkinLayers = visible
                            ? (overlay.hiddenSkinLayers & ~bits)
                            : (overlay.hiddenSkinLayers | bits);
                    PlayerSkinOps.onNodeChanged(overlay);
                }
                yield PlayerSkinOps.HANDLED_NULL;
            }
            case "resetskinlayers", "showskinlayers", "clearskinlayers" -> {
                overlay.hiddenSkinLayers = 0;
                PlayerSkinOps.onNodeChanged(overlay);
                yield PlayerSkinOps.HANDLED_NULL;
            }
            case "isskinlayerhidden", "skinlayerhidden" -> {
                int bits = args.isEmpty() ? CLOTHING : parseParts(String.valueOf(args.get(0))) & CLOTHING;
                yield bits != 0 && (overlay.hiddenSkinLayers & bits) == bits;
            }
            default -> PlayerSkinOps.UNHANDLED;
        };
    }

    public static Object invokePlayer(Player player, String method, java.util.List<Object> args) {
        if (player == null || method == null) return PlayerSkinOps.UNHANDLED;
        String m = method.toLowerCase(Locale.ROOT).replace("_", "");
        UUID uuid = player.getUUID();
        return switch (m) {
            case "hideskinlayer", "hideouterlayer", "hidejacketlayer" -> {
                int bits = args.isEmpty() ? ALL : parseParts(String.valueOf(args.get(0)));
                if (bits == 0) bits = ALL;
                setBits(uuid, bits, true);
                yield PlayerSkinOps.HANDLED_NULL;
            }
            case "showskinlayer", "showouterlayer" -> {
                int bits = args.isEmpty() ? ALL : parseParts(String.valueOf(args.get(0)));
                if (bits == 0) bits = ALL;
                setBits(uuid, bits, false);
                yield PlayerSkinOps.HANDLED_NULL;
            }
            case "setskinlayer", "setouterlayer" -> {
                if (args.isEmpty()) yield PlayerSkinOps.HANDLED_NULL;
                int bits = parseParts(String.valueOf(args.get(0)));
                boolean visible = args.size() < 2 || truthy(args.get(1));
                if (bits != 0) setBits(uuid, bits, !visible);
                yield PlayerSkinOps.HANDLED_NULL;
            }
            case "resetskinlayers", "showskinlayers", "clearskinlayers" -> {
                clear(uuid);
                yield PlayerSkinOps.HANDLED_NULL;
            }
            case "isskinlayerhidden", "skinlayerhidden" -> {
                int bits = args.isEmpty() ? ALL : parseParts(String.valueOf(args.get(0)));
                yield bits != 0 && isHidden(uuid, bits);
            }
            default -> PlayerSkinOps.UNHANDLED;
        };
    }

    private static int partMaskForProperty(String key) {
        if (key == null || key.isEmpty()) return 0;
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "");
        return switch (normalized) {
            case "hidehat", "hideskinhat", "hidehatlayer" -> HAT;
            case "hidejacket", "hideskinjacket", "hidejacketlayer" -> JACKET;
            case "hidetorso", "hidebody", "hideupper" -> TORSO;
            case "hideleftsleeve", "hideleftarm" -> LEFT_SLEEVE;
            case "hiderightsleeve", "hiderightarm" -> RIGHT_SLEEVE;
            case "hidesleeves", "hidearms" -> SLEEVES;
            case "hideleftpants", "hideleftleg" -> LEFT_PANTS;
            case "hiderightpants", "hiderightleg" -> RIGHT_PANTS;
            case "hidepants", "hidelegs" -> PANTS;
            case "hidecape", "hidecloak" -> CAPE;
            case "hideouter", "hideouterlayer", "hideskinlayers", "hideclothing" -> CLOTHING;
            case "hidealllayers" -> ALL;
            default -> 0;
        };
    }

    private static void syncNode(UUID uuid, int mask) {
        SceneNode node = PlayerSkinOps.findPlayerNode(uuid);
        if (node != null) node.hiddenSkinLayers = mask;
    }

    private static void broadcast(UUID uuid, int mask) {
        ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new PlayerSkinLayersPacket(uuid.toString(), mask));
    }

    private static void sendTo(ServerPlayer player, UUID uuid, int mask) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PlayerSkinLayersPacket(uuid.toString(), mask));
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number number) return number.doubleValue() != 0;
        if (value instanceof String text) {
            String t = text.trim();
            return t.equalsIgnoreCase("true") || t.equals("1") || t.equalsIgnoreCase("yes");
        }
        return false;
    }
}
