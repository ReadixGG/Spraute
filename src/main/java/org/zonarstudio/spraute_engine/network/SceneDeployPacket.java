package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zonarstudio.spraute_engine.resource.SprautePaths;
import org.zonarstudio.spraute_engine.scene.SceneRepository;
import org.zonarstudio.spraute_engine.scene.SceneRuntime;
import org.zonarstudio.spraute_engine.script.ScriptManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * C2S (OP): write scene JSON + referenced {@code .spr} files into the <b>server</b>
 * workspace, then load the scene. Used by the in-game editor Play button on dedicated servers.
 */
public final class SceneDeployPacket {
    public static final int MAX_SCENE_BYTES = 1_000_000;
    public static final int MAX_SCRIPT_BYTES = 256_000;
    public static final int MAX_SCRIPTS = 64;

    private final String sceneId;
    private final String sceneJson;
    private final Map<String, String> scripts;

    public SceneDeployPacket(String sceneId, String sceneJson, Map<String, String> scripts) {
        this.sceneId = sceneId == null ? "" : sceneId;
        this.sceneJson = sceneJson == null ? "" : sceneJson;
        this.scripts = scripts == null ? Map.of() : new LinkedHashMap<>(scripts);
    }

    public static void encode(SceneDeployPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.sceneId, 256);
        byte[] scene = msg.sceneJson.getBytes(StandardCharsets.UTF_8);
        buf.writeVarInt(scene.length);
        buf.writeBytes(scene);
        buf.writeVarInt(msg.scripts.size());
        for (var e : msg.scripts.entrySet()) {
            buf.writeUtf(e.getKey(), 256);
            byte[] body = (e.getValue() == null ? "" : e.getValue()).getBytes(StandardCharsets.UTF_8);
            buf.writeVarInt(body.length);
            buf.writeBytes(body);
        }
    }

    public static SceneDeployPacket decode(FriendlyByteBuf buf) {
        String sceneId = buf.readUtf(256);
        int sceneLen = buf.readVarInt();
        if (sceneLen < 0 || sceneLen > MAX_SCENE_BYTES) {
            throw new IllegalArgumentException("scene json too large: " + sceneLen);
        }
        byte[] scene = new byte[sceneLen];
        buf.readBytes(scene);
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_SCRIPTS) {
            throw new IllegalArgumentException("too many scripts: " + n);
        }
        Map<String, String> scripts = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String name = buf.readUtf(256);
            int len = buf.readVarInt();
            if (len < 0 || len > MAX_SCRIPT_BYTES) {
                throw new IllegalArgumentException("script too large: " + name);
            }
            byte[] body = new byte[len];
            buf.readBytes(body);
            scripts.put(name, new String(body, StandardCharsets.UTF_8));
        }
        return new SceneDeployPacket(sceneId, new String(scene, StandardCharsets.UTF_8), scripts);
    }

    public static void handle(SceneDeployPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.hasPermissions(2) || msg.sceneId.isBlank()) return;
            try {
                writeWorkspace(msg);
            } catch (Exception e) {
                player.sendSystemMessage(Component.literal(
                        "§c[Spraute]§r Не удалось записать файлы на сервер: " + e.getMessage()));
                return;
            }
            ScriptManager scripts = ScriptManager.getInstance();
            if (scripts != null) scripts.refreshFromDisk();
            SceneRuntime.load(msg.sceneId, player.createCommandSourceStack());
            org.zonarstudio.spraute_engine.resource.WorkspaceBrowserServer.notifyPeersFileChanged(
                    player, "scenes/" + sanitizeSceneId(msg.sceneId) + ".scene.json");
        });
        context.setPacketHandled(true);
    }

    private static void writeWorkspace(SceneDeployPacket msg) throws Exception {
        Path gameDir = SprautePaths.gameDir();
        Path sceneFile = SceneRepository.scenePath(gameDir, sanitizeSceneId(msg.sceneId));
        Files.createDirectories(sceneFile.getParent());
        Files.writeString(sceneFile, msg.sceneJson, StandardCharsets.UTF_8);

        Path scriptsRoot = SprautePaths.scriptsDir().toAbsolutePath().normalize();
        Files.createDirectories(scriptsRoot);
        for (var e : msg.scripts.entrySet()) {
            Path target = resolveScriptPath(scriptsRoot, e.getKey());
            if (target == null) continue;
            Files.createDirectories(target.getParent());
            Files.writeString(target, e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8);
        }
    }

    private static String sanitizeSceneId(String id) {
        StringBuilder out = new StringBuilder();
        for (char c : id.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') out.append(c);
        }
        return out.length() == 0 ? "scene" : out.toString();
    }

    /** Relative script name without {@code .spr}, e.g. {@code story_dialog_test} or {@code plugins/procode/foo}. */
    public static Path resolveScriptPath(Path scriptsRoot, String name) {
        if (name == null || name.isBlank()) return null;
        String rel = name.replace('\\', '/');
        if (rel.endsWith(".spr")) rel = rel.substring(0, rel.length() - 4);
        if (rel.contains("..") || rel.startsWith("/")) return null;
        Path target = scriptsRoot.resolve(rel + ".spr").normalize();
        if (!target.startsWith(scriptsRoot)) return null;
        return target;
    }

    public static List<String> validateSizes(String sceneJson, Map<String, String> scripts) {
        List<String> errors = new ArrayList<>();
        int sceneBytes = sceneJson == null ? 0 : sceneJson.getBytes(StandardCharsets.UTF_8).length;
        if (sceneBytes > MAX_SCENE_BYTES) {
            errors.add("сцена слишком большая (" + sceneBytes + " байт)");
        }
        if (scripts != null && scripts.size() > MAX_SCRIPTS) {
            errors.add("слишком много скриптов: " + scripts.size());
        }
        if (scripts != null) {
            for (var e : scripts.entrySet()) {
                int n = e.getValue() == null ? 0 : e.getValue().getBytes(StandardCharsets.UTF_8).length;
                if (n > MAX_SCRIPT_BYTES) errors.add("скрипт " + e.getKey() + " слишком большой");
            }
        }
        return errors;
    }
}
