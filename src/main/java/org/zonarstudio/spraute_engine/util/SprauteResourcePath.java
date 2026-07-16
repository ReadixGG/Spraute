package org.zonarstudio.spraute_engine.util;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.zonarstudio.spraute_engine.Spraute_engine;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Validates Spraute asset paths (models, textures, animations) before building {@link ResourceLocation}.
 * Invalid paths must not crash the game — callers show a translated chat message instead.
 */
public final class SprauteResourcePath {

    public enum Kind {
        MODEL("spraute_engine.error.resource_kind.model"),
        TEXTURE("spraute_engine.error.resource_kind.texture"),
        ANIMATION("spraute_engine.error.resource_kind.animation"),
        WORLD("spraute_engine.error.resource_kind.world"),
        RESOURCE("spraute_engine.error.resource_kind.resource");

        private final String labelKey;

        Kind(String labelKey) {
            this.labelKey = labelKey;
        }

        public String labelKey() {
            return labelKey;
        }
    }

    private static final Pattern ALLOWED = Pattern.compile("[a-z0-9/._-]+");
    private static final Pattern SIMPLE_ID = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Set<String> SERVER_WARNED = ConcurrentHashMap.newKeySet();

    private static boolean isLenientAssetKind(Kind kind) {
        return kind == Kind.MODEL || kind == Kind.TEXTURE || kind == Kind.ANIMATION || kind == Kind.RESOURCE;
    }

    /**
     * Normalizes Spraute asset paths: lowercase, removes spaces and other disallowed characters.
     * Keeps {@code a-z}, {@code 0-9}, {@code /}, {@code .}, {@code _}, {@code -} and an optional namespace prefix.
     */
    public static String sanitizeAssetPath(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";

        String namespace = null;
        String pathPart = trimmed;
        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            namespace = sanitizePathSegment(trimmed.substring(0, colon), false);
            pathPart = trimmed.substring(colon + 1);
        }
        pathPart = sanitizePathSegment(pathPart, true);
        while (pathPart.startsWith("/")) pathPart = pathPart.substring(1);
        while (pathPart.endsWith("/")) pathPart = pathPart.substring(0, pathPart.length() - 1);

        if (pathPart.isEmpty()) {
            return namespace != null && !namespace.isEmpty() ? namespace + ":" : "";
        }
        if (namespace != null && !namespace.isEmpty()) {
            return namespace + ":" + pathPart;
        }
        return pathPart;
    }

    private static String sanitizePathSegment(String segment, boolean allowSlash) {
        if (segment == null || segment.isEmpty()) return "";
        StringBuilder out = new StringBuilder(segment.length());
        for (int i = 0; i < segment.length(); i++) {
            char c = Character.toLowerCase(segment.charAt(i));
            if (allowSlash && c == '/') {
                if (!out.isEmpty() && out.charAt(out.length() - 1) == '/') continue;
                out.append('/');
            } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-') {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Path stored on NPC / in scripts (relative under mod namespace when possible). */
    public static String toStoragePath(Result result) {
        if (result == null || !result.ok()) return "";
        ResourceLocation loc = result.location();
        if (Spraute_engine.MODID.equals(loc.getNamespace())) {
            return loc.getPath();
        }
        return loc.getNamespace() + ":" + loc.getPath();
    }

    private SprauteResourcePath() {}

    public record Result(ResourceLocation location, Kind kind, String rawPath, String invalidChars, String detail) {
        public boolean ok() {
            return location != null;
        }

        public Component toChatComponent() {
            if (ok()) return Component.empty();
            String bad = invalidChars != null && !invalidChars.isEmpty()
                    ? invalidChars
                    : (detail != null ? detail : "?");
            return Component.translatable("spraute_engine.error.invalid_resource_path",
                    Component.translatable(kind.labelKey()),
                    rawPath != null ? rawPath : "",
                    bad);
        }
    }

    /** Lowercase id for world/block keys: {@code [a-z][a-z0-9_]*}. */
    public static String canonicalSimpleId(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /** Validates a single-segment id (world key, no slashes). Uppercase is reported as invalid chars. */
    public static Result validateSimpleId(String rawId, Kind kind) {
        if (rawId == null || rawId.isBlank()) {
            return new Result(null, kind, rawId, "", "empty");
        }
        String trimmed = rawId.trim();
        String bad = collectInvalidChars(trimmed);
        if (!bad.isEmpty()) {
            return new Result(null, kind, trimmed, bad, null);
        }
        String canon = canonicalSimpleId(trimmed);
        if (!SIMPLE_ID.matcher(canon).matches()) {
            String detail = canon.isEmpty() ? "empty" : "invalid_format";
            return new Result(null, kind, trimmed, bad, detail);
        }
        if (!trimmed.equals(canon)) {
            return new Result(null, kind, trimmed, collectUppercaseLetters(trimmed), "uppercase");
        }
        return new Result(new ResourceLocation(Spraute_engine.MODID, canon), kind, trimmed, "", null);
    }

    /** Safe {@link ResourceLocation} for namespace + path; never throws. */
    public static ResourceLocation tryCreateLocation(String namespace, String path) {
        if (namespace == null || namespace.isBlank() || path == null || path.isBlank()) return null;
        Result parsed = parse(namespace + ":" + path, Kind.RESOURCE, namespace);
        return parsed.ok() ? parsed.location() : null;
    }

    public static Result parse(String rawPath, Kind kind) {
        return parse(rawPath, kind, Spraute_engine.MODID);
    }

    public static Result parse(String rawPath, Kind kind, String defaultNamespace) {
        if (rawPath == null || rawPath.isBlank()) {
            return new Result(null, kind, rawPath, "", "empty");
        }

        String original = rawPath.trim();
        String trimmed = original;
        if (isLenientAssetKind(kind)) {
            trimmed = sanitizeAssetPath(original);
            if (trimmed.isBlank()) {
                return new Result(null, kind, original, "", "empty");
            }
        }

        String namespace;
        String pathPart;
        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            namespace = trimmed.substring(0, colon);
            pathPart = trimmed.substring(colon + 1);
        } else {
            namespace = defaultNamespace != null ? defaultNamespace : Spraute_engine.MODID;
            pathPart = trimmed;
        }

        String nsBad = collectInvalidChars(namespace);
        String pathBad = collectInvalidChars(pathPart);
        if (!nsBad.isEmpty() || !pathBad.isEmpty()) {
            return new Result(null, kind, original, nsBad + pathBad, null);
        }

        if (namespace.isEmpty() || pathPart.isEmpty()) {
            return new Result(null, kind, original, "", "empty_part");
        }

        try {
            ResourceLocation loc = new ResourceLocation(
                    namespace.toLowerCase(Locale.ROOT),
                    pathPart.toLowerCase(Locale.ROOT));
            return new Result(loc, kind, original, "", null);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new Result(null, kind, original, collectInvalidChars(trimmed), msg);
        }
    }

    private static String collectUppercaseLetters(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder bad = new StringBuilder();
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c) && seen.add(c)) {
                bad.append(c);
            }
        }
        return bad.toString();
    }

    /** Characters outside Minecraft resource id rules: a-z, 0-9, / . _ - */
    public static String collectInvalidChars(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder bad = new StringBuilder();
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ':') continue;
            if (!ALLOWED.matcher(String.valueOf(c).toLowerCase(Locale.ROOT)).matches() && seen.add(c)) {
                bad.append(c);
            }
        }
        return bad.toString();
    }

    public static void warnServerPlayersOnce(Level level, Result result) {
        if (result == null || result.ok() || level == null || level.isClientSide()) return;
        String key = result.kind().name() + "|" + result.rawPath();
        if (!SERVER_WARNED.add(key)) return;
        Component msg = result.toChatComponent();
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (ServerPlayer player : serverLevel.players()) {
            player.sendSystemMessage(msg);
        }
    }

    public static void warnServerPlayersOnce(Level level, Result result, String dedupeSuffix) {
        if (result == null || result.ok() || level == null || level.isClientSide()) return;
        String key = result.kind().name() + "|" + result.rawPath() + "|" + dedupeSuffix;
        if (!SERVER_WARNED.add(key)) return;
        Component msg = result.toChatComponent();
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (ServerPlayer player : serverLevel.players()) {
            player.sendSystemMessage(msg);
        }
    }

    public static void warnClientPlayerOnce(String dedupeKey, Result result) {
        if (result == null || result.ok()) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        if (!CLIENT_WARNED.add(dedupeKey != null ? dedupeKey : result.rawPath())) return;
        mc.player.displayClientMessage(result.toChatComponent(), false);
    }

    private static final Set<String> CLIENT_WARNED = ConcurrentHashMap.newKeySet();

    public static void clearClientWarnings() {
        CLIENT_WARNED.clear();
    }
}
