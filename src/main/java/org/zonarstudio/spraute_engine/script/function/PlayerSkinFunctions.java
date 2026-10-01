package org.zonarstudio.spraute_engine.script.function;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.entity.NpcManager;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public final class PlayerSkinFunctions {

    private PlayerSkinFunctions() {}

    public record SkinTextureInfo(String url, String model) {
        public SkinTextureInfo {
            url = url == null ? "" : url;
            model = model == null ? "" : model;
        }

        public boolean slim() {
            return "slim".equalsIgnoreCase(model);
        }
    }

    /** URL скина из GameProfile (textures.minecraft.net/...), или пустая строка. */
    public static String extractSkinUrl(com.mojang.authlib.GameProfile profile) {
        return extractSkinInfo(profile).url();
    }

    /**
     * Mojang textures payload is {@code { textures: { SKIN: { url, metadata.model } } }}.
     * Older code looked for a top-level {@code SKIN} and always missed the real URL.
     */
    public static SkinTextureInfo extractSkinInfo(com.mojang.authlib.GameProfile profile) {
        if (profile == null) return new SkinTextureInfo("", "");
        var props = profile.getProperties().get("textures");
        if (props == null || props.isEmpty()) return new SkinTextureInfo("", "");
        for (Property p : props) {
            try {
                String decoded = new String(Base64.getDecoder().decode(p.getValue()), StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(decoded).getAsJsonObject();
                JsonObject skin = skinObject(json);
                if (skin == null) continue;
                String url = skin.has("url") ? skin.get("url").getAsString() : "";
                String model = "";
                if (skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")) {
                    model = skin.getAsJsonObject("metadata").get("model").getAsString();
                }
                if (!url.isBlank() || !model.isBlank()) return new SkinTextureInfo(url, model);
            } catch (Exception ignored) {
            }
        }
        return new SkinTextureInfo("", "");
    }

    private static JsonObject skinObject(JsonObject json) {
        if (json.has("textures") && json.get("textures").isJsonObject()) {
            JsonObject textures = json.getAsJsonObject("textures");
            if (textures.has("SKIN") && textures.get("SKIN").isJsonObject()) {
                return textures.getAsJsonObject("SKIN");
            }
        }
        if (json.has("SKIN") && json.get("SKIN").isJsonObject()) {
            return json.getAsJsonObject("SKIN");
        }
        if (json.has("url")) return json;
        return null;
    }

    private static ServerPlayer resolveServerPlayer(Object target, CommandSourceStack source) {
        if (target instanceof ServerPlayer sp) return sp;
        if (target instanceof Player p && p instanceof ServerPlayer sp) return sp;
        if (target instanceof String name && source.getLevel() != null) {
            return source.getLevel().getServer().getPlayerList().getPlayerByName(name);
        }
        if (target instanceof Entity e && e instanceof ServerPlayer sp) return sp;
        return null;
    }

    private static SprauteNpcEntity resolveNpc(Object target, CommandSourceStack source) {
        if (target instanceof SprauteNpcEntity npc) return npc;
        if (target instanceof String id && source.getLevel() != null) {
            Entity e = NpcManager.getEntity(id, source.getLevel());
            if (e instanceof SprauteNpcEntity npc) return npc;
        }
        if (target instanceof Entity e && e instanceof SprauteNpcEntity npc) return npc;
        return null;
    }

    /** getPlayerSkinUrl(player) → URL текстуры или "" */
    public static class GetPlayerSkinUrl implements ScriptFunction {
        @Override public String getName() { return "getPlayerSkinUrl"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return "";
            ServerPlayer sp = resolveServerPlayer(args.get(0), source);
            if (sp == null) return "";
            String url = extractSkinUrl(sp.getGameProfile());
            return url != null ? url : "";
        }
    }

    /**
     * getPlayerSkinTexture(player) → идентификатор для клиента (player_skin:uuid).
     * На сервере нет файла скина — клиент подставляет скин по UUID игрока.
     */
    public static class GetPlayerSkinTexture implements ScriptFunction {
        @Override public String getName() { return "getPlayerSkinTexture"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return "";
            ServerPlayer sp = resolveServerPlayer(args.get(0), source);
            if (sp == null) return "";
            return "player_skin:" + sp.getUUID();
        }
    }

    /** npcSetPlayerSkin(npc, player) — наложить скин игрока на модель НПС */
    public static class NpcSetPlayerSkin implements ScriptFunction {
        @Override public String getName() { return "npcSetPlayerSkin"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            SprauteNpcEntity npc = resolveNpc(args.get(0), source);
            ServerPlayer sp = resolveServerPlayer(args.get(1), source);
            if (npc != null && sp != null) {
                npc.setPlayerSkinOverlay(sp.getUUID());
            }
            return null;
        }
    }

    /** npcClearPlayerSkin(npc) */
    public static class NpcClearPlayerSkin implements ScriptFunction {
        @Override public String getName() { return "npcClearPlayerSkin"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return null;
            SprauteNpcEntity npc = resolveNpc(args.get(0), source);
            if (npc != null) npc.clearPlayerSkinOverlay();
            return null;
        }
    }
}
