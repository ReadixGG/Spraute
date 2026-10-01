package org.zonarstudio.spraute_engine.client;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** First-class style/effect description shared by all script UI text widgets. */
public record UiTextStyle(
        boolean bold, boolean italic, boolean underline, boolean strikethrough, boolean code,
        boolean shadow, boolean outline, boolean glow, boolean rainbow,
        boolean pulse, boolean wave, boolean shake, boolean wiggle,
        boolean pixelShadow, boolean pixelOutline, boolean pixelGlow,
        int outlineColor, int glowColor, int[] gradient,
        float speed, float amount, float glowSize, float outlineWidth,
        int underlineColor, float underlineThickness, float underlineOffset,
        int shadowColor, float shadowOffsetX, float shadowOffsetY, float shadowSize
) {
    public static final UiTextStyle NONE = new UiTextStyle(false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            true, true, false,
            0xFF000000, 0x66FFFFFF, null, 1f, 1f, 3f, 1f,
            0, 1f, 0f, 0, 1f, 1f, 2f);
    static final ResourceLocation UNIFORM_FONT = new ResourceLocation("minecraft", "uniform");

    public static UiTextStyle fromJson(JsonObject object) {
        if (object == null) return NONE;
        boolean hasNestedStyle = object.has("textStyle") && object.get("textStyle").isJsonObject();
        JsonObject nested = hasNestedStyle ? object.getAsJsonObject("textStyle") : object;
        Set<String> effects = parseEffects(string(nested, "effects", string(object, "effects", "")));
        addFlag(effects, nested, "bold");
        addFlag(effects, nested, "italic");
        addFlag(effects, nested, "underline");
        addFlag(effects, nested, "strikethrough");
        addFlag(effects, nested, "code");
        addFlag(effects, nested, "shadow");
        addFlag(effects, nested, "outline");
        addFlag(effects, nested, "glow");
        addFlag(effects, nested, "rainbow");
        addFlag(effects, nested, "pulse");
        addFlag(effects, nested, "wave");
        addFlag(effects, nested, "shake");
        addFlag(effects, nested, "wiggle");
        addFlag(effects, nested, "glow_pixel");
        addFlag(effects, nested, "glow_soft");
        addFlag(effects, nested, "outline_pixel");
        addFlag(effects, nested, "outline_soft");
        addFlag(effects, nested, "shadow_pixel");
        addFlag(effects, nested, "shadow_soft");
        String gradientRaw = hasNestedStyle
                ? string(nested, "gradient", string(object, "textGradient", ""))
                : string(object, "textGradient", "");
        return new UiTextStyle(
                hasNamed(effects, "bold"), hasNamed(effects, "italic"), hasNamed(effects, "underline"),
                hasNamed(effects, "strikethrough") || effects.contains("strike"), hasNamed(effects, "code"),
                hasNamed(effects, "shadow"), hasNamed(effects, "outline"), hasNamed(effects, "glow"),
                hasNamed(effects, "rainbow"), hasNamed(effects, "pulse"), hasNamed(effects, "wave"),
                hasNamed(effects, "shake"), hasNamed(effects, "wiggle"),
                pixelMode(effects, "shadow", true),
                pixelMode(effects, "outline", true),
                pixelMode(effects, "glow", false),
                hasNestedStyle
                        ? color(nested, "outlineColor", color(object, "textOutlineColor", 0xFF000000))
                        : color(object, "textOutlineColor", 0xFF000000),
                color(nested, "glowColor", color(object, "textGlowColor", 0x66FFFFFF)),
                parseGradient(gradientRaw),
                number(nested, "effectSpeed", number(nested, "speed",
                        number(object, "textEffectSpeed", 1f))),
                number(nested, "effectAmount", number(nested, "amount",
                        number(object, "textEffectAmount", 1f))),
                number(nested, "glowSize", number(nested, "textGlowSize",
                        number(object, "textGlowSize", 3f))),
                number(nested, "outlineWidth", number(nested, "textOutlineWidth",
                        number(object, "textOutlineWidth", 1f))),
                color(nested, "underlineColor", color(object, "textUnderlineColor", 0)),
                number(nested, "underlineThickness", number(object, "textUnderlineThickness", 1f)),
                number(nested, "underlineOffset", number(object, "textUnderlineOffset", 0f)),
                color(nested, "shadowColor", color(object, "textShadowColor", 0)),
                number(nested, "shadowOffsetX", number(object, "textShadowOffsetX", 1f)),
                number(nested, "shadowOffsetY", number(object, "textShadowOffsetY", 1f)),
                number(nested, "shadowSize", number(object, "textShadowSize", 2f))
        );
    }

    public MutableComponent component(String text) {
        return UiTextMarkup.component(text, this);
    }

    /** Widget style plus inline span flags (OR). Span glow/outline/gradient override when set. */
    public UiTextStyle overlay(UiTextStyle extra) {
        if (extra == null || extra == NONE) return this;
        boolean extraGlow = extra.glow();
        boolean extraOutline = extra.outline();
        boolean extraShadow = extra.shadow();
        return new UiTextStyle(
                bold || extra.bold(), italic || extra.italic(),
                underline || extra.underline(), strikethrough || extra.strikethrough(),
                code || extra.code(),
                shadow || extraShadow, outline || extraOutline, glow || extraGlow,
                rainbow || extra.rainbow(), pulse || extra.pulse(),
                wave || extra.wave(), shake || extra.shake(), wiggle || extra.wiggle(),
                extraShadow ? extra.pixelShadow() : pixelShadow,
                extraOutline ? extra.pixelOutline() : pixelOutline,
                extraGlow ? extra.pixelGlow() : pixelGlow,
                extraOutline ? extra.outlineColor() : outlineColor,
                extraGlow ? extra.glowColor() : glowColor,
                extra.gradient() != null ? extra.gradient() : gradient,
                extra.speed() != 1f ? extra.speed() : speed,
                extra.amount() != 1f ? extra.amount() : amount,
                extraGlow && extra.glowSize() != 3f ? extra.glowSize() : glowSize,
                extraOutline && extra.outlineWidth() != 1f ? extra.outlineWidth() : outlineWidth,
                extra.underlineColor() != 0 ? extra.underlineColor() : underlineColor,
                extra.underlineColor() != 0 ? extra.underlineThickness() : underlineThickness,
                extra.underlineColor() != 0 ? extra.underlineOffset() : underlineOffset,
                extraShadow && extra.shadowColor() != 0 ? extra.shadowColor() : shadowColor,
                extraShadow ? extra.shadowOffsetX() : shadowOffsetX,
                extraShadow ? extra.shadowOffsetY() : shadowOffsetY,
                extraShadow ? extra.shadowSize() : shadowSize
        );
    }

    static UiTextStyle fromEffectSet(Set<String> effects, int outlineColor, int glowColor,
                                     int[] gradient, float speed, float amount,
                                     float glowSize, float outlineWidth,
                                     int underlineColor, float underlineThickness, float underlineOffset,
                                     int shadowColor, float shadowOffsetX, float shadowOffsetY, float shadowSize) {
        if (effects == null) effects = Set.of();
        return new UiTextStyle(
                hasNamed(effects, "bold"), hasNamed(effects, "italic"), hasNamed(effects, "underline"),
                hasNamed(effects, "strikethrough") || effects.contains("strike"), hasNamed(effects, "code"),
                hasNamed(effects, "shadow"), hasNamed(effects, "outline"), hasNamed(effects, "glow"),
                hasNamed(effects, "rainbow"), hasNamed(effects, "pulse"), hasNamed(effects, "wave"),
                hasNamed(effects, "shake"), hasNamed(effects, "wiggle"),
                pixelMode(effects, "shadow", true),
                pixelMode(effects, "outline", true),
                pixelMode(effects, "glow", false),
                outlineColor, glowColor, gradient, speed, amount, glowSize, outlineWidth,
                underlineColor, underlineThickness, underlineOffset,
                shadowColor, shadowOffsetX, shadowOffsetY, shadowSize
        );
    }

    public boolean animatedOrGradient() {
        return rainbow || gradient != null || pulse || wave || shake || wiggle;
    }

    public boolean hasBackdrop() {
        return code;
    }

    public static boolean hasNamed(Set<String> effects, String base) {
        if (effects == null || base == null) return false;
        return effects.contains(base)
                || effects.contains(base + "_pixel")
                || effects.contains(base + "_soft");
    }

    /**
     * Pixel vs soft for shadow/outline/glow. Defaults: shadow and outline are pixel,
     * glow is soft (pixel glow looks like a chunky silhouette).
     */
    public static boolean pixelMode(Set<String> effects, String base, boolean defaultPixel) {
        if (effects == null || base == null) return defaultPixel;
        if (effects.contains(base + "_pixel")) return true;
        if (effects.contains(base + "_soft")) return false;
        return defaultPixel;
    }

    public static Set<String> parseEffects(String value) {
        Set<String> out = new LinkedHashSet<>();
        if (value == null) return out;
        for (String part : value.toLowerCase(Locale.ROOT).split("[,;\\s]+")) {
            if (part.isBlank() || "none".equals(part) || "нет".equals(part)) continue;
            String token = part.trim();
            if ("strike".equals(token)) token = "strikethrough";
            out.add(token);
        }
        return out;
    }

    /** Canonical token written to {@code effects} for a shadow/outline/glow mode. */
    public static String modeToken(String base, boolean enabled, boolean pixel) {
        if (!enabled) return null;
        if ("glow".equals(base)) return pixel ? "glow_pixel" : "glow";
        return pixel ? base : base + "_soft";
    }

    private static void addFlag(Set<String> effects, JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isBoolean()) return;
        if (o.get(key).getAsBoolean()) effects.add(key);
        else effects.remove(key);
    }

    private static String string(JsonObject o, String key, String fallback) {
        try { return o.has(key) ? o.get(key).getAsString() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static float number(JsonObject o, String key, float fallback) {
        try { return o.has(key) ? o.get(key).getAsFloat() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static int color(JsonObject o, String key, int fallback) {
        if (!o.has(key)) return fallback;
        String s;
        try { s = o.get(key).getAsString().trim(); } catch (Exception e) { return fallback; }
        try {
            if (s.startsWith("#")) {
                String h = s.substring(1);
                if (h.length() == 6) return 0xFF000000 | Integer.parseUnsignedInt(h, 16);
                if (h.length() == 8) return (int) Long.parseLong(h, 16);
            }
            return (int) Long.decode(s).longValue();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int[] parseGradient(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(",");
        java.util.List<Integer> colors = new java.util.ArrayList<>();
        JsonObject holder = new JsonObject();
        for (String part : parts) {
            holder.addProperty("c", part.trim());
            int parsed = color(holder, "c", Integer.MIN_VALUE);
            if (parsed != Integer.MIN_VALUE) colors.add(parsed);
        }
        if (colors.size() < 2) return null;
        return colors.stream().mapToInt(Integer::intValue).toArray();
    }
}
