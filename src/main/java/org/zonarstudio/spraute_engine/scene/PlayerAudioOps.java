package org.zonarstudio.spraute_engine.scene;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.PlayScriptSoundPacket;
import org.zonarstudio.spraute_engine.network.PlayerAudioSettingsPacket;
import org.zonarstudio.spraute_engine.script.CameraScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptExecutor;
import org.zonarstudio.spraute_engine.script.ScriptSoundTracker;

import java.util.List;
import java.util.Locale;

/** AUDIO-node script API (same surface as {@code player.audio}). */
public final class PlayerAudioOps {
    private PlayerAudioOps() {}

    public static Object get(SceneNode node, String prop) {
        if (node == null || node.type != SceneNodeType.AUDIO || prop == null) return null;
        return switch (prop) {
            case "volume", "vol", "master", "mastervolume", "master_volume" -> (double) node.audioVolume;
            case "pitch", "masterpitch", "master_pitch" -> (double) node.audioPitch;
            case "enabled", "on", "unmuted" -> node.audioEnabled;
            case "mute", "muted", "silent" -> !node.audioEnabled;
            case "sound", "id", "soundid", "sound_id" -> node.audioSound == null ? "" : node.audioSound;
            case "loop", "looping" -> node.audioLoop;
            default -> null;
        };
    }

    public static boolean isAudioProperty(String prop) {
        if (prop == null) return false;
        return switch (prop) {
            case "volume", "vol", "master", "mastervolume", "master_volume",
                 "pitch", "masterpitch", "master_pitch",
                 "enabled", "on", "unmuted", "mute", "muted", "silent",
                 "sound", "id", "soundid", "sound_id",
                 "loop", "looping" -> true;
            default -> false;
        };
    }

    public static boolean set(SceneActiveState state, SceneNode node, String prop, Object value,
                              CommandSourceStack source) {
        if (node == null || node.type != SceneNodeType.AUDIO || !isAudioProperty(prop)) return false;
        switch (prop) {
            case "volume", "vol", "master", "mastervolume", "master_volume" -> {
                if (!(value instanceof Number n)) return false;
                node.audioVolume = Math.max(0f, n.floatValue());
            }
            case "pitch", "masterpitch", "master_pitch" -> {
                if (!(value instanceof Number n)) return false;
                node.audioPitch = Math.max(0.01f, n.floatValue());
            }
            case "enabled", "on", "unmuted" -> node.audioEnabled = truthy(value);
            case "mute", "muted", "silent" -> node.audioEnabled = !truthy(value);
            case "sound", "id", "soundid", "sound_id" ->
                    node.audioSound = value == null ? "" : String.valueOf(value).trim();
            case "loop", "looping" -> node.audioLoop = truthy(value);
            default -> {
                return false;
            }
        }
        pushSettings(state, node, source);
        return true;
    }

    public static Object invoke(SceneActiveState state, SceneNode node, String method, List<Object> args,
                                CommandSourceStack source) {
        if (node == null || node.type != SceneNodeType.AUDIO || method == null) {
            return SceneNodeView.UNHANDLED;
        }
        return switch (method) {
            case "play", "playsound", "play_sound" -> {
                play(state, node, args, source);
                yield SceneNodeView.HANDLED_NULL;
            }
            case "stop", "stopsound", "stop_sound" -> {
                stop(state, node, args, source);
                yield SceneNodeView.HANDLED_NULL;
            }
            case "stopall", "stop_all", "silence" -> {
                stopAll(state, node, source);
                yield SceneNodeView.HANDLED_NULL;
            }
            case "mute" -> {
                node.audioEnabled = false;
                pushSettings(state, node, source);
                yield SceneNodeView.HANDLED_NULL;
            }
            case "unmute" -> {
                node.audioEnabled = true;
                pushSettings(state, node, source);
                yield SceneNodeView.HANDLED_NULL;
            }
            case "setvolume", "set_volume", "volume" -> {
                if (args != null && !args.isEmpty() && args.get(0) instanceof Number n) {
                    node.audioVolume = Math.max(0f, n.floatValue());
                    pushSettings(state, node, source);
                }
                yield SceneNodeView.HANDLED_NULL;
            }
            case "setpitch", "set_pitch", "pitch" -> {
                if (args != null && !args.isEmpty() && args.get(0) instanceof Number n) {
                    node.audioPitch = Math.max(0.01f, n.floatValue());
                    pushSettings(state, node, source);
                }
                yield SceneNodeView.HANDLED_NULL;
            }
            default -> SceneNodeView.UNHANDLED;
        };
    }

    /** {@code playSound(player, …)} and {@code player.audio.play(…)}. */
    public static void playForPlayer(ServerPlayer player, Object soundRaw, float volume, float pitch, boolean loop) {
        if (player == null) return;
        SceneNodeView view = PlayerAudioNodes.viewFor(player);
        SceneNode audio = view == null ? null : view.node();
        if (audio == null || audio.type != SceneNodeType.AUDIO) {
            // No live scene — still play via client packet with identity master.
            sendPlay(player, soundRaw, volume, pitch, loop, 1f, 1f, true);
            return;
        }
        playOn(audio, player, soundRaw, volume, pitch, loop);
    }

    public static void stopForPlayer(ServerPlayer player, Object soundRaw) {
        if (player == null) return;
        if (soundRaw == null || String.valueOf(soundRaw).trim().isEmpty()) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), PlayScriptSoundPacket.stopAll());
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                    null, net.minecraft.sounds.SoundSource.MASTER));
            return;
        }
        ResourceLocation rl = soundId(soundRaw);
        if (rl == null) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), PlayScriptSoundPacket.stopAll());
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                    null, net.minecraft.sounds.SoundSource.MASTER));
            return;
        }
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                PlayScriptSoundPacket.stopOne(rl.toString()));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                rl, net.minecraft.sounds.SoundSource.MASTER));
    }

    private static void play(SceneActiveState state, SceneNode node, List<Object> args,
                             CommandSourceStack source) {
        ServerPlayer player = resolvePlayer(state, node, source);
        if (player == null || args == null || args.isEmpty()) return;
        float volume = args.size() > 1 ? Math.max(0f, toFloat(args.get(1), 1f)) : 1f;
        float pitch = args.size() > 2 ? Math.max(0.01f, toFloat(args.get(2), 1f)) : 1f;
        boolean loop = args.size() > 3 && truthy(args.get(3));
        playOn(node, player, args.get(0), volume, pitch, loop);
    }

    private static void playOn(SceneNode node, ServerPlayer player, Object soundRaw,
                               float volume, float pitch, boolean loop) {
        String key = soundKey(soundRaw);
        if (key == null || key.isBlank()) return;
        node.audioSound = key;
        node.audioLoop = loop;
        ScriptSoundTracker.register(ScriptExecutor.currentInstanceId(), player, key, loop);
        sendPlay(player, key, volume, pitch, loop, node.audioVolume, node.audioPitch, node.audioEnabled);
    }

    private static void stop(SceneActiveState state, SceneNode node, List<Object> args,
                             CommandSourceStack source) {
        ServerPlayer player = resolvePlayer(state, node, source);
        if (player == null) return;
        if (args == null || args.isEmpty()) {
            String last = node.audioSound;
            if (last == null || last.isBlank()) {
                stopAll(state, node, source);
                return;
            }
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    PlayScriptSoundPacket.stopOne(last));
            return;
        }
        stopForPlayer(player, args.get(0));
    }

    private static void stopAll(SceneActiveState state, SceneNode node, CommandSourceStack source) {
        ServerPlayer player = resolvePlayer(state, node, source);
        if (player == null) return;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), PlayScriptSoundPacket.stopAll());
    }

    public static void pushSettings(SceneActiveState state, SceneNode node, CommandSourceStack source) {
        ServerPlayer player = resolvePlayer(state, node, source);
        if (player == null) return;
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                PlayerAudioSettingsPacket.sync(node.audioVolume, node.audioPitch, node.audioEnabled));
    }

    /** Sync bus settings when a player joins / camera-like ensure. */
    public static void syncTo(ServerPlayer player, SceneNode audio) {
        if (player == null || audio == null || audio.type != SceneNodeType.AUDIO) return;
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                PlayerAudioSettingsPacket.sync(audio.audioVolume, audio.audioPitch, audio.audioEnabled));
    }

    private static void sendPlay(ServerPlayer player, Object soundRaw, float volume, float pitch, boolean loop,
                                 float masterVolume, float masterPitch, boolean enabled) {
        String key = soundKey(soundRaw);
        if (key == null || key.isBlank()) return;
        // Relative levels; client multiplies by AUDIO bus master (and respects mute).
        float v = Math.max(0f, volume);
        float p = Math.max(0.01f, pitch);
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                PlayScriptSoundPacket.play(key, v, p, loop));
        // Keep bus masters in sync (mute / volume edits while tracks play).
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                PlayerAudioSettingsPacket.sync(masterVolume, masterPitch, enabled));
    }

    private static ServerPlayer resolvePlayer(SceneActiveState state, SceneNode audio,
                                              CommandSourceStack source) {
        if (state != null && state.document != null && audio != null) {
            SceneNode parent = state.document.findParent(audio.id);
            if (parent != null && parent.type == SceneNodeType.PLAYER && parent.playerUuid != null) {
                try {
                    var uuid = java.util.UUID.fromString(parent.playerUuid);
                    if (source != null && source.getLevel() != null && source.getLevel().getServer() != null) {
                        ServerPlayer sp = source.getLevel().getServer().getPlayerList().getPlayer(uuid);
                        if (sp != null) return sp;
                    }
                    for (String sceneId : SceneRuntime.listLoaded()) {
                        SceneActiveState st = SceneRuntime.getActiveState(sceneId);
                        if (st == null) continue;
                        Object obj = st.getRuntimeObject(parent.id);
                        if (obj instanceof ServerPlayer sp) return sp;
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return CameraScriptUtil.resolvePlayer(null, source);
    }

    private static String soundKey(Object raw) {
        ResourceLocation rl = soundId(raw);
        return rl == null ? null : rl.toString();
    }

    private static ResourceLocation soundId(Object raw) {
        String soundId = String.valueOf(raw == null ? "" : raw).trim();
        if (soundId.isEmpty()) return null;
        return soundId.contains(":")
                ? new ResourceLocation(soundId)
                : new ResourceLocation("minecraft", soundId);
    }

    private static float toFloat(Object v, float def) {
        if (v instanceof Number n) return n.floatValue();
        if (v == null) return def;
        try {
            return Float.parseFloat(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number number) return number.doubleValue() != 0;
        if (value instanceof String text) {
            String t = text.trim().toLowerCase(Locale.ROOT);
            return t.equals("true") || t.equals("1") || t.equals("yes") || t.equals("on");
        }
        return false;
    }
}
