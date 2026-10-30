package org.sprautestudio.st_addon_spraute.engine;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.network.PacketDistributor;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The world's engine settings: stored in {@code <world>/spraute/engine.json}, pushed to clients. */
public final class EngineServer {
    private EngineServer() {}

    private static EngineConfig config = new EngineConfig();

    public static EngineConfig config() {
        return config;
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("spraute").resolve("engine.json");
    }

    public static void load(MinecraftServer server) {
        Path f = file(server);
        EngineConfig c = new EngineConfig();
        if (Files.exists(f)) {
            try {
                c = EngineConfig.fromJson(Files.readString(f, StandardCharsets.UTF_8));
            } catch (IOException e) {
                St_addon_spraute.LOGGER.warn("Spraute: cannot read {}", f, e);
            }
        }
        config = c;
        applyClock(server);
    }

    // ------------------------------------------------------------------ time of day

    private static double speedCarry;
    private static boolean clockPending;

    /**
     * Puts the day cycle as the settings say.
     */
    public static void applyClock(MinecraftServer server) {
        // the worlds do not exist yet while the server is starting: done on its first tick
        if (server.overworld() == null) {
            clockPending = true;
            return;
        }
        clockPending = false;
        if (config.time) return;
        EngineConfig.Clock k = config.clock;
        if (!k.manage) return;
        boolean run = k.cycle && k.fixed < 0;
        server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(run, server);
        if (k.fixed >= 0) setTimeOfDay(server, k.fixed);
    }

    /** Sets the time of day keeping the day number. */
    public static void setTimeOfDay(MinecraftServer server, long timeOfDay) {
        if (config.time) {
            org.sprautestudio.st_addon_spraute.core.system.WorldClock.setTimeOfDay(server, timeOfDay);
            return;
        }
        net.minecraft.server.level.ServerLevel level = server.overworld();
        if (level == null) return;
        long day = Math.floorDiv(level.getDayTime(), 24000L);
        long t = day * 24000L + Math.floorMod(timeOfDay, 24000L);
        for (net.minecraft.server.level.ServerLevel l : server.getAllLevels()) l.setDayTime(t);
        syncTime(server);
    }

    public static void addTime(MinecraftServer server, long ticks) {
        if (config.time) {
            org.sprautestudio.st_addon_spraute.core.system.WorldClock.add(server, ticks);
            return;
        }
        long now = server.overworld().getDayTime();
        for (net.minecraft.server.level.ServerLevel l : server.getAllLevels()) l.setDayTime(now + ticks);
        syncTime(server);
    }

    private static void syncTime(MinecraftServer server) {
        boolean tick = server.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            net.minecraft.server.level.ServerLevel l = p.serverLevel();
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(l.getGameTime(), l.getDayTime(), tick));
        }
    }

    /** Every server tick: a faster or slower day, or the world's own clock. */
    public static void tick(MinecraftServer server) {
        if (config.time && server.overworld() != null) org.sprautestudio.st_addon_spraute.core.system.WorldClock.tick(server);
        if (clockPending) {
            try {
                applyClock(server);
            } catch (RuntimeException e) {
                clockPending = false;
                St_addon_spraute.LOGGER.warn("Spraute: cannot apply the time settings", e);
            }
        }
        EngineConfig.Clock k = config.clock;
        if (config.time || !k.manage || !k.cycle || k.fixed >= 0 || k.speed == 1) return;
        if (!server.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) return;
        speedCarry += k.speed - 1;
        long whole = (long) speedCarry;
        if (whole == 0) return;
        speedCarry -= whole;
        for (net.minecraft.server.level.ServerLevel l : server.getAllLevels()) l.setDayTime(l.getDayTime() + whole);
        if (server.getTickCount() % 4 == 0) syncTime(server);
    }

    /** Back to defaults when the world closes, so the next world starts clean. */
    public static void unload() {
        config = new EngineConfig();
    }

    public static void set(MinecraftServer server, EngineConfig c) {
        if (config.time && !c.time) org.sprautestudio.st_addon_spraute.core.system.WorldClock.release(server);
        config = c;
        applyClock(server);
        Path f = file(server);
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, c.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            St_addon_spraute.LOGGER.warn("Spraute: cannot save {}", f, e);
        }
        SprauteNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new Packets.EngineSync(c.toJson()));
    }

    public static boolean enabled(String system) {
        return switch (system.toLowerCase()) {
            case "health" -> config.health;
            case "coins" -> config.currency;
            case "time" -> config.time;
            case "inventory" -> config.inventory;
            case "slots" -> config.slots;
            case "chat" -> config.chat;
            default -> false;
        };
    }

    /** Switches one system from a story; saved and synced like the IDE settings. */
    public static void setSystem(String system, boolean on) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || enabled(system) == on) return;
        EngineConfig c = config.copy();
        switch (system.toLowerCase()) {
            case "health" -> c.health = on;
            case "coins" -> c.currency = on;
            case "time" -> c.time = on;
            case "inventory" -> c.inventory = on;
            case "slots" -> c.slots = on;
            case "chat" -> c.chat = on;
            default -> {
                return;
            }
        }
        set(server, c);
    }

    public static void sync(ServerPlayer player) {
        SprauteNetwork.sendTo(player, new Packets.EngineSync(config.toJson()));
    }
}
