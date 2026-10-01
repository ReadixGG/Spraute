package org.zonarstudio.spraute_engine.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.mixin.HttpTextureAccess;
import org.zonarstudio.spraute_engine.scene.PlayerSkinLayers;
import org.zonarstudio.spraute_engine.scene.PlayerSkinState;
import org.zonarstudio.spraute_engine.script.function.PlayerSkinFunctions;
import org.zonarstudio.spraute_engine.util.SprauteResourcePath;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Composites a base player skin with full-skin overlay textures and exposes
 * the result through {@link AbstractClientPlayer#getSkinTextureLocation()}
 * (third person and first-person arm).
 */
@OnlyIn(Dist.CLIENT)
public final class PlayerSkinComposer {
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "spraute-skin-compose");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<UUID, PlayerSkinState.Appearance> APPEARANCES = new ConcurrentHashMap<>();
    private static final Map<UUID, ResourceLocation> LOCATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, DynamicTexture> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<UUID, String> READY = new ConcurrentHashMap<>();
    private static final Map<UUID, String> IN_FLIGHT = new ConcurrentHashMap<>();
    /** Texture identity used as the base when we last composed (id ^ width ^ height). */
    private static final Map<UUID, Integer> BASE_STAMP = new ConcurrentHashMap<>();
    private static final Map<String, NativeImage> IMAGE_CACHE = new ConcurrentHashMap<>();
    private static int ticks;

    private PlayerSkinComposer() {}

    public static void apply(String playerUuid, PlayerSkinState.Appearance appearance) {
        UUID uuid = parseUuid(playerUuid);
        if (uuid == null) return;
        PlayerSkinState.Appearance value = appearance == null ? PlayerSkinState.VANILLA : appearance;
        if (value.isVanilla()) {
            APPEARANCES.remove(uuid);
            onLayersChanged(playerUuid);
            org.zonarstudio.spraute_engine.client.editor.scene.ClientScenePlayerNodes.applySkin(
                    playerUuid, value);
            return;
        }
        APPEARANCES.put(uuid, value);
        schedule(uuid, value);
        org.zonarstudio.spraute_engine.client.editor.scene.ClientScenePlayerNodes.applySkin(
                playerUuid, value);
    }

    /** Rebake when {@code hideHat} / clothing bits change on the original skin. */
    public static void onLayersChanged(String playerUuid) {
        UUID uuid = parseUuid(playerUuid);
        if (uuid == null) return;
        PlayerSkinState.Appearance appearance = APPEARANCES.getOrDefault(uuid, PlayerSkinState.VANILLA);
        if (appearance.isVanilla() && clothingHide(uuid) == 0) {
            READY.remove(uuid);
            IN_FLIGHT.remove(uuid);
            BASE_STAMP.remove(uuid);
            release(uuid);
            return;
        }
        schedule(uuid, appearance);
    }

    public static ResourceLocation locationFor(AbstractClientPlayer player) {
        if (player == null) return null;
        UUID uuid = player.getUUID();
        PlayerSkinState.Appearance appearance = APPEARANCES.getOrDefault(uuid, PlayerSkinState.VANILLA);
        if (appearance.isVanilla() && clothingHide(uuid) == 0) return null;
        String ready = READY.get(uuid);
        if (ready == null || !ready.equals(composeKey(uuid, appearance))) return null;
        return LOCATIONS.get(uuid);
    }

    public static PlayerSkinState.Appearance appearanceOf(UUID uuid) {
        if (uuid == null) return PlayerSkinState.VANILLA;
        PlayerSkinState.Appearance appearance = APPEARANCES.get(uuid);
        return appearance == null ? PlayerSkinState.VANILLA : appearance;
    }

    public static void tick() {
        if ((++ticks % 10) != 0) return;
        java.util.Set<UUID> ids = new java.util.HashSet<>(APPEARANCES.keySet());
        for (UUID uuid : PlayerSkinLayersClient.hiddenPlayers()) {
            if (clothingHide(uuid) != 0) ids.add(uuid);
        }
        for (UUID uuid : ids) {
            PlayerSkinState.Appearance appearance = APPEARANCES.getOrDefault(uuid, PlayerSkinState.VANILLA);
            String fingerprint = composeKey(uuid, appearance);
            if (fingerprint.equals(IN_FLIGHT.get(uuid))) continue;
            if (fingerprint.equals(READY.get(uuid))) {
                int live = liveSkinStamp(uuid);
                Integer baked = BASE_STAMP.get(uuid);
                if (baked != null && live != 0 && live != baked) {
                    READY.remove(uuid);
                    schedule(uuid, appearance);
                }
                continue;
            }
            schedule(uuid, appearance);
        }
    }

    public static void clear() {
        APPEARANCES.clear();
        READY.clear();
        IN_FLIGHT.clear();
        BASE_STAMP.clear();
        for (UUID uuid : new ArrayList<>(TEXTURES.keySet())) release(uuid);
        dropImageCache();
    }

    /** Drop decoded workspace skins so a live texture save can rebake overlays. */
    public static void invalidateWorkspaceImages() {
        dropImageCache();
        READY.clear();
    }

    private static void dropImageCache() {
        for (NativeImage image : IMAGE_CACHE.values()) {
            try {
                image.close();
            } catch (Exception ignored) {
            }
        }
        IMAGE_CACHE.clear();
    }

    private static void schedule(UUID uuid, PlayerSkinState.Appearance appearance) {
        String fingerprint = composeKey(uuid, appearance);
        IN_FLIGHT.put(uuid, fingerprint);
        int stamp = liveSkinStamp(uuid);
        NativeImage snapshot = snapshotLoadedSkin(uuid);
        boolean slim = playerIsSlim(uuid);
        LOADER.execute(() -> {
            try {
                NativeImage composed = compose(uuid, appearance, snapshot, slim);
                if (composed == null) {
                    IN_FLIGHT.remove(uuid, fingerprint);
                    return;
                }
                Minecraft.getInstance().execute(() -> upload(uuid, composed, fingerprint, stamp));
            } catch (Exception ignored) {
                IN_FLIGHT.remove(uuid, fingerprint);
            }
        });
    }

    private static void upload(UUID uuid, NativeImage image, String fingerprint, int baseStamp) {
        PlayerSkinState.Appearance current = APPEARANCES.getOrDefault(uuid, PlayerSkinState.VANILLA);
        if (!fingerprint.equals(composeKey(uuid, current))) {
            image.close();
            return;
        }
        ResourceLocation location = LOCATIONS.computeIfAbsent(uuid, PlayerSkinComposer::location);
        DynamicTexture existing = TEXTURES.get(uuid);
        Minecraft mc = Minecraft.getInstance();
        if (existing != null && existing.getPixels() != null
                && existing.getPixels().getWidth() == image.getWidth()
                && existing.getPixels().getHeight() == image.getHeight()) {
            copyInto(image, existing.getPixels());
            existing.upload();
            image.close();
        } else {
            if (existing != null) {
                mc.getTextureManager().release(location);
            }
            DynamicTexture texture = new DynamicTexture(image);
            mc.getTextureManager().register(location, texture);
            TEXTURES.put(uuid, texture);
        }
        READY.put(uuid, fingerprint);
        BASE_STAMP.put(uuid, baseStamp);
        IN_FLIGHT.remove(uuid, fingerprint);
    }

    private static NativeImage compose(UUID uuid, PlayerSkinState.Appearance appearance,
                                       NativeImage vanillaSnapshot, boolean slim) {
        NativeImage snapshot = vanillaSnapshot;
        NativeImage base = loadBase(appearance.baseSkin(), uuid, snapshot);
        if (base != snapshot && snapshot != null) {
            snapshot.close();
        }
        if (base == null) return null;
        base = modernize(base);
        PlayerSkinLayout.clearOuter(base, clothingHide(uuid));
        for (PlayerSkinState.Overlay overlay : appearance.overlays()) {
            if (!overlay.visible()) continue;
            NativeImage layer = loadLayer(overlay.texture(), uuid, false);
            if (layer == null) continue;
            layer = modernize(layer);
            NativeImage scaled = scaleTo(layer, base.getWidth(), base.getHeight());
            if (scaled != layer) layer.close();
            NativeImage matched = PlayerSkinLayout.matchModel(scaled, slim);
            if (matched != scaled) scaled.close();
            PlayerSkinLayout.clearOuter(matched, overlay.hiddenSkinLayers());
            stamp(base, matched);
            if (matched != base) matched.close();
        }
        return base;
    }

    private static String composeKey(UUID uuid, PlayerSkinState.Appearance appearance) {
        PlayerSkinState.Appearance value = appearance == null ? PlayerSkinState.VANILLA : appearance;
        return value.fingerprint() + "\nhide=" + clothingHide(uuid);
    }

    private static int clothingHide(UUID uuid) {
        return PlayerSkinLayersClient.mask(uuid) & PlayerSkinLayers.CLOTHING;
    }

    /**
     * Bottom layer, then overlays stamp on top.
     * Replaced {@code player.skin} if set; otherwise whatever the client
     * is already drawing (launcher / Mojang / default).
     */
    private static NativeImage loadBase(String spec, UUID uuid, NativeImage snapshot) {
        String path = spec == null ? "" : spec.trim();
        if (!path.isEmpty()) {
            NativeImage custom = loadLayer(path, uuid, false);
            if (custom != null) return custom;
        }
        if (snapshot != null) return snapshot;
        NativeImage downloaded = downloadVanillaUrl(uuid);
        if (downloaded != null) return downloaded;
        return loadVanillaFallback(uuid);
    }

    private static NativeImage loadLayer(String spec, UUID playerUuid, boolean vanillaFallback) {
        String path = spec == null ? "" : spec.trim();
        if (path.isEmpty()) {
            return vanillaFallback ? loadVanilla(playerUuid) : null;
        }
        NativeImage cached = IMAGE_CACHE.get(path);
        if (cached != null) return copy(cached);
        NativeImage loaded = readSpec(path, playerUuid);
        if (loaded == null) {
            return vanillaFallback ? loadVanilla(playerUuid) : null;
        }
        IMAGE_CACHE.put(path, copy(loaded));
        return loaded;
    }

    private static NativeImage readSpec(String spec, UUID playerUuid) {
        if (spec.startsWith("http://") || spec.startsWith("https://")) {
            return download(spec);
        }
        if (spec.startsWith("player_skin:")) {
            UUID other = parseUuid(spec.substring("player_skin:".length()));
            return other == null ? null : loadVanilla(other);
        }
        NativeImage fromFile = readWorkspace(spec);
        if (fromFile != null) return fromFile;
        SprauteResourcePath.Result parsed = SprauteResourcePath.parse(spec, SprauteResourcePath.Kind.TEXTURE);
        if (parsed.ok()) {
            NativeImage fromResource = readResource(parsed.location());
            if (fromResource != null) return fromResource;
        }
        return null;
    }

    /**
     * The skin the player is wearing right now — launcher, Mojang, or default.
     * Must run on the client thread. Never reads our composed texture.
     */
    private static NativeImage snapshotLoadedSkin(UUID uuid) {
        ResourceLocation location = vanillaSkinLocation(uuid);
        if (location == null || isComposedLocation(location)) return null;
        AbstractTexture texture = boundSkinTexture(location);
        if (texture == null) return null;
        if (texture instanceof DynamicTexture dynamic && dynamic.getPixels() != null) {
            NativeImage pixels = dynamic.getPixels();
            if (pixels.getWidth() >= 64 && pixels.getHeight() >= 32) return copy(pixels);
        }
        NativeImage fromHttpCache = readHttpCache(texture);
        if (fromHttpCache != null) return fromHttpCache;
        NativeImage fromPack = readResource(location);
        if (fromPack != null) return fromPack;
        return downloadGpu(texture);
    }

    private static AbstractTexture boundSkinTexture(ResourceLocation location) {
        if (location == null) return null;
        try {
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(location);
            if (texture == null || texture == MissingTextureAtlasSprite.getTexture()) return null;
            if (TEXTURES.containsValue(texture)) return null;
            return texture;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static NativeImage readHttpCache(AbstractTexture texture) {
        if (!(texture instanceof HttpTexture http)) return null;
        File file;
        try {
            file = ((HttpTextureAccess) http).spraute$getCacheFile();
        } catch (Exception ignored) {
            return null;
        }
        if (file == null || !file.isFile()) return null;
        try (InputStream stream = Files.newInputStream(file.toPath())) {
            return NativeImage.read(stream);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Already-uploaded GPU pixels — works for launcher skins that never hit Mojang. */
    private static NativeImage downloadGpu(AbstractTexture texture) {
        if (texture == null) return null;
        try {
            texture.bind();
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (width < 64 || height < 32 || width > 2048 || height > 2048) return null;
            NativeImage image = new NativeImage(width, height, true);
            image.downloadTexture(0, false);
            return image;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int liveSkinStamp(UUID uuid) {
        ResourceLocation location = vanillaSkinLocation(uuid);
        AbstractTexture texture = boundSkinTexture(location);
        if (texture == null) return 0;
        try {
            texture.bind();
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            return System.identityHashCode(texture) ^ (width << 16) ^ height;
        } catch (Throwable ignored) {
            return texture.getId();
        }
    }

    private static boolean isComposedLocation(ResourceLocation location) {
        return location != null
                && Spraute_engine.MODID.equals(location.getNamespace())
                && location.getPath().startsWith("skins/");
    }

    private static boolean playerIsSlim(UUID uuid) {
        PlayerInfo info = playerInfo(uuid);
        if (info != null) {
            String model = info.getModelName();
            if (model != null && !model.isBlank()) return "slim".equalsIgnoreCase(model);
            if (PlayerSkinFunctions.extractSkinInfo(info.getProfile()).slim()) return true;
        }
        return "slim".equalsIgnoreCase(DefaultPlayerSkin.getSkinModelName(uuid));
    }

    private static NativeImage downloadVanillaUrl(UUID uuid) {
        PlayerInfo info = playerInfo(uuid);
        if (info == null) return null;
        String url = PlayerSkinFunctions.extractSkinUrl(info.getProfile());
        if (url == null || url.isBlank()) return null;
        return download(url);
    }

    private static NativeImage loadVanilla(UUID uuid) {
        NativeImage downloaded = downloadVanillaUrl(uuid);
        if (downloaded != null) return downloaded;
        return loadVanillaFallback(uuid);
    }

    private static NativeImage loadVanillaFallback(UUID uuid) {
        NativeImage fromPack = readResource(vanillaSkinLocation(uuid));
        if (fromPack != null) return fromPack;
        return readResource(DefaultPlayerSkin.getDefaultSkin(uuid));
    }

    private static ResourceLocation vanillaSkinLocation(UUID uuid) {
        PlayerInfo info = playerInfo(uuid);
        if (info != null) return info.getSkinLocation();
        return DefaultPlayerSkin.getDefaultSkin(uuid);
    }

    private static PlayerInfo playerInfo(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (uuid == null || mc.getConnection() == null) return null;
        return mc.getConnection().getPlayerInfo(uuid);
    }

    private static NativeImage readResource(ResourceLocation location) {
        if (location == null) return null;
        try {
            var optional = Minecraft.getInstance().getResourceManager().getResource(location);
            if (optional.isEmpty()) return null;
            Resource resource = optional.get();
            try (InputStream stream = resource.open()) {
                return NativeImage.read(stream);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static NativeImage readWorkspace(String spec) {
        String key = spec.replace('\\', '/');
        int colon = key.indexOf(':');
        if (colon >= 0) key = key.substring(colon + 1);
        while (key.startsWith("/")) key = key.substring(1);
        Path root = Minecraft.getInstance().gameDirectory.toPath().resolve("spraute_engine");
        Path file = root.resolve(key);
        if (!Files.isRegularFile(file)) {
            Path alt = root.resolve("textures").resolve(key);
            if (Files.isRegularFile(alt)) file = alt;
            else return null;
        }
        try (InputStream stream = Files.newInputStream(file)) {
            return NativeImage.read(stream);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static NativeImage download(String url) {
        NativeImage cached = IMAGE_CACHE.get(url);
        if (cached != null) return copy(cached);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", "SprauteEngine/1.7");
            connection.connect();
            if (connection.getResponseCode() / 100 != 2) return null;
            try (InputStream stream = connection.getInputStream()) {
                NativeImage image = NativeImage.read(stream);
                IMAGE_CACHE.put(url, copy(image));
                return image;
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static NativeImage modernize(NativeImage image) {
        if (image == null) return null;
        if (image.getWidth() == 64 && image.getHeight() == 32) {
            NativeImage modern = new NativeImage(64, 64, true);
            copyRegion(image, modern, 0, 0, 64, 32, 0, 0);
            image.close();
            return modern;
        }
        return image;
    }

    private static NativeImage scaleTo(NativeImage source, int width, int height) {
        if (source.getWidth() == width && source.getHeight() == height) return source;
        NativeImage scaled = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) {
            int sy = y * source.getHeight() / height;
            for (int x = 0; x < width; x++) {
                int sx = x * source.getWidth() / width;
                scaled.setPixelRGBA(x, y, source.getPixelRGBA(sx, sy));
            }
        }
        return scaled;
    }

    private static void stamp(NativeImage base, NativeImage overlay) {
        int width = Math.min(base.getWidth(), overlay.getWidth());
        int height = Math.min(base.getHeight(), overlay.getHeight());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int src = overlay.getPixelRGBA(x, y);
                int alpha = (src >> 24) & 0xFF;
                if (alpha == 0) continue;
                if (alpha == 255) {
                    base.setPixelRGBA(x, y, src);
                    continue;
                }
                base.setPixelRGBA(x, y, mix(base.getPixelRGBA(x, y), src, alpha));
            }
        }
    }

    private static int mix(int dst, int src, int srcAlpha) {
        int da = (dst >> 24) & 0xFF;
        int db = (dst >> 16) & 0xFF;
        int dg = (dst >> 8) & 0xFF;
        int dr = dst & 0xFF;
        int sb = (src >> 16) & 0xFF;
        int sg = (src >> 8) & 0xFF;
        int sr = src & 0xFF;
        int inv = 255 - srcAlpha;
        int outA = srcAlpha + da * inv / 255;
        int outB = (sb * srcAlpha + db * inv) / 255;
        int outG = (sg * srcAlpha + dg * inv) / 255;
        int outR = (sr * srcAlpha + dr * inv) / 255;
        return (outA << 24) | (outB << 16) | (outG << 8) | outR;
    }

    private static NativeImage copy(NativeImage source) {
        NativeImage copy = new NativeImage(source.getWidth(), source.getHeight(), true);
        copyInto(source, copy);
        return copy;
    }

    private static void copyInto(NativeImage source, NativeImage dest) {
        int width = Math.min(source.getWidth(), dest.getWidth());
        int height = Math.min(source.getHeight(), dest.getHeight());
        copyRegion(source, dest, 0, 0, width, height, 0, 0);
    }

    private static void copyRegion(NativeImage source, NativeImage dest,
                                   int sx, int sy, int w, int h, int dx, int dy) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dest.setPixelRGBA(dx + x, dy + y, source.getPixelRGBA(sx + x, sy + y));
            }
        }
    }

    private static void release(UUID uuid) {
        ResourceLocation location = LOCATIONS.remove(uuid);
        DynamicTexture texture = TEXTURES.remove(uuid);
        if (location != null) {
            Minecraft.getInstance().getTextureManager().release(location);
        } else if (texture != null) {
            texture.close();
        }
    }

    private static ResourceLocation location(UUID uuid) {
        return new ResourceLocation(Spraute_engine.MODID, "skins/" + uuid.toString().replace("-", ""));
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
