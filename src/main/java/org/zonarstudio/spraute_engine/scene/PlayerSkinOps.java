package org.zonarstudio.spraute_engine.scene;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.PlayerSkinSyncPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server source of truth for player skins and overlay nodes.
 * PLAYER.texture / SKIN_OVERLAY children stay in sync with this map (node = code).
 */
public final class PlayerSkinOps {
    public static final Object UNHANDLED = new Object();
    public static final Object HANDLED_NULL = new Object();

    private static final Map<UUID, PlayerSkinState.Appearance> STATES = new ConcurrentHashMap<>();

    private PlayerSkinOps() {}

    public static PlayerSkinState.Appearance get(UUID uuid) {
        if (uuid == null) return PlayerSkinState.VANILLA;
        PlayerSkinState.Appearance appearance = STATES.get(uuid);
        return appearance == null ? PlayerSkinState.VANILLA : appearance;
    }

    public static String baseSkin(UUID uuid) {
        return get(uuid).baseSkin();
    }

    public static void setBaseSkin(UUID uuid, String skin) {
        PlayerSkinState.Appearance current = get(uuid);
        replace(uuid, new PlayerSkinState.Appearance(normalize(skin), current.overlays()));
    }

    public static void replace(UUID uuid, PlayerSkinState.Appearance appearance) {
        if (uuid == null) return;
        PlayerSkinState.Appearance next = appearance == null ? PlayerSkinState.VANILLA : appearance;
        if (next.isVanilla()) STATES.remove(uuid);
        else STATES.put(uuid, next);
        syncNodes(uuid, next);
        broadcast(uuid, next);
    }

    public static void clear(UUID uuid) {
        replace(uuid, PlayerSkinState.VANILLA);
    }

    public static void applyFromPlayerNode(SceneNode player) {
        UUID uuid = parseUuid(player == null ? null : player.playerUuid);
        if (uuid == null) return;
        replace(uuid, PlayerSkinState.fromPlayerNode(player));
    }

    public static void applyToPlayerNode(SceneNode player) {
        UUID uuid = parseUuid(player == null ? null : player.playerUuid);
        if (uuid == null) return;
        applyToPlayerNode(player, get(uuid));
    }

    public static void applyToPlayerNode(SceneNode player, PlayerSkinState.Appearance appearance) {
        if (player == null || player.type != SceneNodeType.PLAYER) return;
        PlayerSkinState.Appearance value = appearance == null ? PlayerSkinState.VANILLA : appearance;
        player.texture = value.baseSkin();

        List<SceneNode> cameras = new ArrayList<>();
        List<SceneNode> audios = new ArrayList<>();
        List<SceneNode> other = new ArrayList<>();
        Map<String, SceneNode> overlays = new java.util.LinkedHashMap<>();
        for (SceneNode child : player.children) {
            if (child.type == SceneNodeType.CAMERA) cameras.add(child);
            else if (child.type == SceneNodeType.AUDIO) audios.add(child);
            else if (child.type == SceneNodeType.SKIN_OVERLAY) overlays.put(child.id, child);
            else other.add(child);
        }

        List<SceneNode> next = new ArrayList<>(cameras);
        next.addAll(audios);
        for (PlayerSkinState.Overlay overlay : value.overlays()) {
            String id = overlay.id() == null || overlay.id().isBlank()
                    ? "overlay_" + Integer.toHexString(System.identityHashCode(overlay))
                    : overlay.id();
            SceneNode node = overlays.remove(id);
            if (node == null) {
                node = SceneNode.skinOverlay(overlay.name());
                node.id = id;
            }
            if (overlay.name() != null && !overlay.name().isBlank()) node.name = overlay.name();
            node.texture = overlay.texture();
            node.active = overlay.active();
            node.hiddenSkinLayers = overlay.hiddenSkinLayers();
            next.add(node);
        }
        next.addAll(other);
        player.children.clear();
        player.children.addAll(next);
    }

    public static void onNodeChanged(SceneNode node) {
        if (node == null) return;
        if (node.type == SceneNodeType.PLAYER) {
            applyFromPlayerNode(node);
            return;
        }
        if (node.type != SceneNodeType.SKIN_OVERLAY) return;
        SceneNode parent = findParent(node);
        if (parent != null && parent.type == SceneNodeType.PLAYER) applyFromPlayerNode(parent);
    }

    public static SceneNodeView viewFor(Player player) {
        SceneNode node = findPlayerNode(player == null ? null : player.getUUID());
        if (node == null) return null;
        for (String sceneId : SceneRuntime.listLoaded()) {
            SceneActiveState state = SceneRuntime.getActiveState(sceneId);
            if (state != null && state.document.findNode(node.id) == node) {
                return state.nodeView(node);
            }
        }
        return null;
    }

    public static SceneNode findPlayerNode(UUID uuid) {
        if (uuid == null) return null;
        String id = uuid.toString();
        for (String sceneId : SceneRuntime.listLoaded()) {
            SceneActiveState state = SceneRuntime.getActiveState(sceneId);
            if (state == null) continue;
            SceneNode players = state.document.ensurePlayersNode();
            for (SceneNode child : players.children) {
                if (child.type == SceneNodeType.PLAYER && id.equals(child.playerUuid)) return child;
            }
        }
        return null;
    }

    public static UUID uuidOf(Object target) {
        if (target instanceof Player player) return player.getUUID();
        if (target instanceof SceneNodeView view) {
            SceneNode node = view.node();
            if (node != null && node.type == SceneNodeType.PLAYER) return parseUuid(node.playerUuid);
            Entity entity = view.getEntity(null);
            if (entity instanceof Player player) return player.getUUID();
        }
        if (target instanceof SceneNode node && node.type == SceneNodeType.PLAYER) {
            return parseUuid(node.playerUuid);
        }
        if (target instanceof String text) return parseUuid(text);
        return null;
    }

    public static Object invokePlayer(Player player, String method, List<Object> args) {
        if (player == null || method == null) return UNHANDLED;
        Object layers = PlayerSkinLayers.invokePlayer(player, method, args);
        if (layers != UNHANDLED) return layers;
        String m = method.toLowerCase(Locale.ROOT).replace("_", "");
        return switch (m) {
            case "addoverlay", "addskinoverlay" -> {
                String id = "";
                String texture = "";
                if (args.size() >= 2) {
                    id = String.valueOf(args.get(0));
                    texture = String.valueOf(args.get(1));
                } else if (!args.isEmpty()) {
                    texture = String.valueOf(args.get(0));
                }
                yield addOverlay(player, id, texture);
            }
            case "overlay", "skinoverlay" -> {
                if (args.isEmpty()) yield null;
                yield overlayOf(player.getUUID(), String.valueOf(args.get(0)));
            }
            case "resetskin", "clearskin" -> {
                setBaseSkin(player.getUUID(), "");
                yield HANDLED_NULL;
            }
            default -> UNHANDLED;
        };
    }

    public static SceneNodeView addOverlay(Player player, String id, String texture) {
        return player == null ? null : addOverlay(player.getUUID(), id, texture);
    }

    public static void setOverlayActive(UUID uuid, String id, boolean active) {
        if (uuid == null || id == null || id.isBlank()) return;
        PlayerSkinState.Appearance current = get(uuid);
        List<PlayerSkinState.Overlay> overlays = new ArrayList<>(current.overlays());
        boolean changed = false;
        for (int i = 0; i < overlays.size(); i++) {
            PlayerSkinState.Overlay overlay = overlays.get(i);
            if (overlay.id().equals(id) && overlay.active() != active) {
                overlays.set(i, new PlayerSkinState.Overlay(
                        overlay.id(), overlay.name(), overlay.texture(), active, overlay.hiddenSkinLayers()));
                changed = true;
            }
        }
        if (changed) {
            replace(uuid, new PlayerSkinState.Appearance(current.baseSkin(), overlays));
        }
    }

    public static SceneNodeView addOverlay(UUID uuid, String id, String texture) {
        if (uuid == null) return null;
        PlayerSkinState.Appearance current = get(uuid);
        List<PlayerSkinState.Overlay> overlays = new ArrayList<>(current.overlays());
        String overlayId = id == null ? "" : id.trim();
        if (overlayId.isEmpty()) {
            overlayId = nextOverlayId(overlays);
        }
        final String resolvedId = overlayId;
        String name = resolvedId;
        int hide = 0;
        for (PlayerSkinState.Overlay overlay : overlays) {
            if (overlay.id().equals(resolvedId)) {
                hide = overlay.hiddenSkinLayers();
                break;
            }
        }
        overlays.removeIf(overlay -> overlay.id().equals(resolvedId));
        overlays.add(new PlayerSkinState.Overlay(resolvedId, name, normalize(texture), true, hide));
        replace(uuid, new PlayerSkinState.Appearance(current.baseSkin(), overlays));
        return overlayOf(uuid, resolvedId);
    }

    public static void sendAllTo(ServerPlayer joiner) {
        if (joiner == null) return;
        for (var entry : STATES.entrySet()) {
            sendTo(joiner, entry.getKey(), entry.getValue());
        }
    }

    public static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String text = raw.trim();
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ignored) {
        }
        if (text.length() == 32) {
            try {
                return UUID.fromString(text.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                        "$1-$2-$3-$4-$5"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    public static SceneNodeView overlayOf(UUID uuid, String idOrName) {
        SceneNode playerNode = findPlayerNode(uuid);
        if (playerNode == null || idOrName == null) return null;
        String key = idOrName.trim();
        for (String sceneId : SceneRuntime.listLoaded()) {
            SceneActiveState state = SceneRuntime.getActiveState(sceneId);
            if (state == null || state.document.findNode(playerNode.id) != playerNode) continue;
            for (SceneNode child : playerNode.children) {
                if (child.type != SceneNodeType.SKIN_OVERLAY) continue;
                if (key.equals(child.id) || key.equalsIgnoreCase(child.name)) {
                    return state.nodeView(child);
                }
            }
        }
        return null;
    }

    private static String nextOverlayId(List<PlayerSkinState.Overlay> overlays) {
        int n = 1;
        while (true) {
            String candidate = "overlay" + n;
            boolean used = false;
            for (PlayerSkinState.Overlay overlay : overlays) {
                if (candidate.equals(overlay.id())) {
                    used = true;
                    break;
                }
            }
            if (!used) return candidate;
            n++;
        }
    }

    private static void syncNodes(UUID uuid, PlayerSkinState.Appearance appearance) {
        String id = uuid.toString();
        for (String sceneId : SceneRuntime.listLoaded()) {
            SceneActiveState state = SceneRuntime.getActiveState(sceneId);
            if (state == null) continue;
            SceneNode players = state.document.ensurePlayersNode();
            for (SceneNode child : players.children) {
                if (child.type == SceneNodeType.PLAYER && id.equals(child.playerUuid)) {
                    applyToPlayerNode(child, appearance);
                }
            }
        }
    }

    private static SceneNode findParent(SceneNode node) {
        if (node == null) return null;
        for (String sceneId : SceneRuntime.listLoaded()) {
            SceneActiveState state = SceneRuntime.getActiveState(sceneId);
            if (state == null) continue;
            SceneNode parent = state.document.findParent(node.id);
            if (parent != null) return parent;
        }
        return null;
    }

    private static void broadcast(UUID uuid, PlayerSkinState.Appearance appearance) {
        ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new PlayerSkinSyncPacket(uuid.toString(), appearance));
    }

    private static void sendTo(ServerPlayer player, UUID uuid, PlayerSkinState.Appearance appearance) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PlayerSkinSyncPacket(uuid.toString(), appearance));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
