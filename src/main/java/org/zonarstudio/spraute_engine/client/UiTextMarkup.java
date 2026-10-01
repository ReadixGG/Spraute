package org.zonarstudio.spraute_engine.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Godot/TMP-style inline markup stored in the text string (node = code).
 * <pre>
 *   Привет, [glow]герой[/glow]! [#FFFF5555]цвет[/#] [wave,rainbow]вау[/]
 * </pre>
 * {@code [[} emits a literal {@code [}. Widget-level effects still apply; tags add on top.
 */
public final class UiTextMarkup {
    static final String MARK = "\u001Ese:";

    public record Caret(String text, int cursor, int selStart, int selEnd) {}

    private UiTextMarkup() {}

    public static boolean hasTags(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '[' && markupTagClose(raw, i) >= 0) return true;
            if (c == '§' && i + 1 < raw.length()) return true;
        }
        return false;
    }

    /** Text shown by the visual inspector mode, without markup syntax. */
    public static String plainText(String raw) {
        StringBuilder out = new StringBuilder();
        if (raw == null) return "";
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '[' && i + 1 < raw.length() && raw.charAt(i + 1) == '[') {
                out.append('[');
                i += 2;
            } else if (c == '[') {
                int close = markupTagClose(raw, i);
                if (close < 0) {
                    out.append(c);
                    i++;
                } else {
                    i = close + 1;
                }
            } else if (c == '§' && i + 1 < raw.length()
                    && ChatFormatting.getByCode(raw.charAt(i + 1)) != null) {
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Converts an index in {@link #plainText(String)} back to the source markup index. */
    public static int rawIndexForPlain(String raw, int plainIndex) {
        if (raw == null || plainIndex <= 0) return 0;
        int visible = 0;
        int i = 0;
        while (i < raw.length() && visible < plainIndex) {
            char c = raw.charAt(i);
            if (c == '[' && i + 1 < raw.length() && raw.charAt(i + 1) == '[') {
                i += 2;
                visible++;
            } else if (c == '[') {
                int close = markupTagClose(raw, i);
                if (close < 0) {
                    i++;
                    visible++;
                } else {
                    i = close + 1;
                }
            } else if (c == '§' && i + 1 < raw.length()
                    && ChatFormatting.getByCode(raw.charAt(i + 1)) != null) {
                i += 2;
            } else {
                int cp = raw.codePointAt(i);
                i += Character.charCount(cp);
                visible += Character.charCount(cp);
            }
        }
        return i;
    }

    /** Start-biased variant: enters opening tags located at the same visual position. */
    public static int rawStartForPlain(String raw, int plainIndex) {
        int i = rawIndexForPlain(raw, plainIndex);
        if (raw == null) return i;
        while (i < raw.length() && raw.charAt(i) == '['
                && !(i + 1 < raw.length() && raw.charAt(i + 1) == '[')) {
            int close = markupTagClose(raw, i);
            if (close < 0) break;
            String body = raw.substring(i + 1, close).trim();
            if (body.startsWith("/")) break;
            i = close + 1;
        }
        return i;
    }

    public static int plainIndexForRaw(String raw, int rawIndex) {
        if (raw == null || rawIndex <= 0) return 0;
        return plainText(raw.substring(0, Math.min(rawIndex, raw.length()))).length();
    }

    /**
     * Applies an edit made in visual/plain mode back to the markup source while
     * retaining tags outside the changed range.
     */
    public static String replacePlain(String raw, String oldPlain, String newPlain) {
        if (raw == null) raw = "";
        if (oldPlain == null) oldPlain = plainText(raw);
        if (newPlain == null) newPlain = "";
        int prefix = 0;
        int maxPrefix = Math.min(oldPlain.length(), newPlain.length());
        while (prefix < maxPrefix && oldPlain.charAt(prefix) == newPlain.charAt(prefix)) prefix++;
        int oldEnd = oldPlain.length();
        int newEnd = newPlain.length();
        while (oldEnd > prefix && newEnd > prefix
                && oldPlain.charAt(oldEnd - 1) == newPlain.charAt(newEnd - 1)) {
            oldEnd--;
            newEnd--;
        }
        int rawStart = rawStartForPlain(raw, prefix);
        int rawEnd = rawIndexForPlain(raw, oldEnd);
        return raw.substring(0, rawStart) + newPlain.substring(prefix, newEnd) + raw.substring(rawEnd);
    }

    public static MutableComponent component(String raw, UiTextStyle base) {
        UiTextStyle style = base == null ? UiTextStyle.NONE : base;
        List<Run> runs = parse(raw);
        if (runs.isEmpty()) return Component.literal("");
        MutableComponent root = Component.empty();
        for (Run run : runs) {
            if (run.text.isEmpty()) continue;
            root.append(Component.literal(run.text).withStyle(mc -> toMc(mc, style, run)));
        }
        return root;
    }

    public static UiTextStyle fromGlyph(UiTextStyle base, Style mcStyle) {
        UiTextStyle style = base == null ? UiTextStyle.NONE : base;
        if (mcStyle == null) return style;
        String ins = mcStyle.getInsertion();
        if (ins == null || !ins.startsWith(MARK)) return style;
        return style.overlay(decode(ins.substring(MARK.length()), style));
    }

    public static boolean hasSpanEffects(net.minecraft.util.FormattedCharSequence line) {
        if (line == null) return false;
        boolean[] hit = {false};
        line.accept((index, style, codePoint) -> {
            String ins = style == null ? null : style.getInsertion();
            if (ins != null && ins.startsWith(MARK)) {
                hit[0] = true;
                return false;
            }
            return true;
        });
        return hit[0];
    }

    public static int glyphColor(Style mcStyle, int fallback) {
        if (mcStyle == null) return fallback;
        TextColor tc = mcStyle.getColor();
        if (tc == null) return fallback;
        int rgb = tc.getValue() & 0xFFFFFF;
        int a = fallback >>> 24;
        return (a << 24) | rgb;
    }

    public static Caret toggle(String text, int start, int end, String tag) {
        if (text == null) text = "";
        int a = clamp(Math.min(start, end), 0, text.length());
        int b = clamp(Math.max(start, end), 0, text.length());
        String open = openToken(tag);
        String close = closeToken(tag);
        TagRange enclosing = findEnclosing(text, a, b, tagBase(tag));
        if (enclosing != null) {
            int contentStart = enclosing.openEnd;
            int contentEnd = enclosing.closeStart;
            String next = text.substring(0, enclosing.openStart)
                    + text.substring(contentStart, contentEnd)
                    + text.substring(enclosing.closeEnd);
            int na = Math.max(enclosing.openStart, a - (contentStart - enclosing.openStart));
            int nb = Math.max(na, b - (contentStart - enclosing.openStart));
            nb = Math.min(enclosing.openStart + (contentEnd - contentStart), nb);
            return new Caret(next, nb, na, nb);
        }
        if (a >= open.length() && b + close.length() <= text.length()
                && text.startsWith(open, a - open.length()) && text.startsWith(close, b)) {
            String next = text.substring(0, a - open.length()) + text.substring(a, b)
                    + text.substring(b + close.length());
            int na = a - open.length();
            int nb = na + (b - a);
            return new Caret(next, nb, na, nb);
        }
        if (b - a >= open.length() + close.length()
                && text.startsWith(open, a) && text.startsWith(close, b - close.length())) {
            String inner = text.substring(a + open.length(), b - close.length());
            String next = text.substring(0, a) + inner + text.substring(b);
            return new Caret(next, a + inner.length(), a, a + inner.length());
        }
        String inner = text.substring(a, b);
        String next = text.substring(0, a) + open + inner + close + text.substring(b);
        int na = a + open.length();
        int nb = na + inner.length();
        int caret = b == a ? na : nb;
        return new Caret(next, caret, na, nb);
    }

    /** Replaces parameters of an enclosing effect, or inserts it when absent. */
    public static Caret configure(String text, int start, int end, String tag) {
        if (text == null) text = "";
        int a = clamp(Math.min(start, end), 0, text.length());
        int b = clamp(Math.max(start, end), 0, text.length());
        TagRange enclosing = findEnclosing(text, a, b, tagBase(tag));
        if (enclosing == null) return toggle(text, a, b, tag);
        String open = openToken(tag);
        String next = text.substring(0, enclosing.openStart) + open + text.substring(enclosing.openEnd);
        int delta = open.length() - (enclosing.openEnd - enclosing.openStart);
        int na = a + delta;
        int nb = b + delta;
        return new Caret(next, nb, na, nb);
    }

    public static boolean selectionWrapped(String text, int start, int end, String tag) {
        if (text == null) return false;
        int a = clamp(Math.min(start, end), 0, text.length());
        int b = clamp(Math.max(start, end), 0, text.length());
        if (findEnclosing(text, a, b, tagBase(tag)) != null) return true;
        String open = openToken(tag);
        String close = closeToken(tag);
        if (a >= open.length() && b + close.length() <= text.length()
                && text.startsWith(open, a - open.length()) && text.startsWith(close, b)) {
            return true;
        }
        return b - a >= open.length() + close.length()
                && text.startsWith(open, a) && text.startsWith(close, b - close.length());
    }

    private record TagRange(int openStart, int openEnd, int closeStart, int closeEnd) {}

    private static TagRange findEnclosing(String text, int start, int end, String wantedBase) {
        if (text == null || wantedBase.isEmpty()) return null;
        record Open(String base, int start, int end) {}
        Deque<Open> stack = new ArrayDeque<>();
        TagRange best = null;
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) != '[' || (i + 1 < text.length() && text.charAt(i + 1) == '[')) {
                i += text.charAt(i) == '[' ? 2 : 1;
                continue;
            }
            int close = markupTagClose(text, i);
            if (close < 0) {
                i++;
                continue;
            }
            String body = text.substring(i + 1, close).trim();
            if (body.startsWith("/")) {
                String closing = tagBase(body.substring(1));
                Open matched = null;
                while (!stack.isEmpty()) {
                    Open candidate = stack.pop();
                    if (closing.isEmpty() || closing.equals(candidate.base)
                            || ("#".equals(closing) && "color".equals(candidate.base))) {
                        matched = candidate;
                        break;
                    }
                }
                if (matched != null && wantedBase.equals(matched.base)
                        && matched.end <= start && i >= end) {
                    TagRange range = new TagRange(matched.start, matched.end, i, close + 1);
                    if (best == null || range.openStart > best.openStart) best = range;
                }
            } else {
                stack.push(new Open(tagBase(body), i, close + 1));
            }
            i = close + 1;
        }
        return best;
    }

    private static String tagBase(String tag) {
        if (tag == null) return "";
        String body = tag.trim();
        if (body.startsWith("/")) body = body.substring(1).trim();
        if (body.startsWith("#")) return "color";
        int split = body.length();
        for (char delimiter : new char[]{'=', ':', ',', ' '}) {
            int p = body.indexOf(delimiter);
            if (p >= 0) split = Math.min(split, p);
        }
        return canonical(body.substring(0, split));
    }

    static String openToken(String tag) {
        if (tag == null) tag = "";
        return "[" + tag + "]";
    }

    static String closeToken(String tag) {
        if (tag == null) tag = "";
        if (tag.startsWith("#")) return "[/#]";
        if (tag.startsWith("gradient")) return "[/gradient]";
        int eq = tag.indexOf('=');
        String name = eq < 0 ? tag : tag.substring(0, eq);
        int comma = name.indexOf(',');
        if (comma > 0) name = name.substring(0, comma);
        return "[/" + name + "]";
    }

    private record Run(String text, Set<String> effects, int color, int[] gradient,
                       int glowColor, int outlineColor, float speed, float amount,
                       float glowSize, float outlineWidth,
                       int underlineColor, float underlineThickness, float underlineOffset,
                       int shadowColor, float shadowOffsetX, float shadowOffsetY, float shadowSize) {}

    private static final class Frame {
        final Set<String> effects;
        int color;
        int[] gradient;
        int glowColor;
        int outlineColor;
        float speed;
        float amount;
        float glowSize;
        float outlineWidth;
        int underlineColor;
        float underlineThickness;
        float underlineOffset;
        int shadowColor;
        float shadowOffsetX;
        float shadowOffsetY;
        float shadowSize;

        Frame() {
            effects = new LinkedHashSet<>();
            color = 0;
            glowColor = 0;
            outlineColor = 0;
            speed = 1f;
            amount = 1f;
            glowSize = Float.NaN;
            outlineWidth = Float.NaN;
            underlineColor = 0;
            underlineThickness = 1f;
            underlineOffset = 0f;
            shadowColor = 0;
            shadowOffsetX = 1f;
            shadowOffsetY = 1f;
            shadowSize = 2f;
        }

        Frame(Frame o) {
            effects = new LinkedHashSet<>(o.effects);
            color = o.color;
            gradient = o.gradient;
            glowColor = o.glowColor;
            outlineColor = o.outlineColor;
            speed = o.speed;
            amount = o.amount;
            glowSize = o.glowSize;
            outlineWidth = o.outlineWidth;
            underlineColor = o.underlineColor;
            underlineThickness = o.underlineThickness;
            underlineOffset = o.underlineOffset;
            shadowColor = o.shadowColor;
            shadowOffsetX = o.shadowOffsetX;
            shadowOffsetY = o.shadowOffsetY;
            shadowSize = o.shadowSize;
        }

        boolean sameLook(Frame o) {
            return effects.equals(o.effects) && color == o.color
                    && java.util.Arrays.equals(gradient, o.gradient)
                    && glowColor == o.glowColor && outlineColor == o.outlineColor
                    && Float.compare(speed, o.speed) == 0 && Float.compare(amount, o.amount) == 0
                    && Float.compare(glowSize, o.glowSize) == 0
                    && Float.compare(outlineWidth, o.outlineWidth) == 0
                    && underlineColor == o.underlineColor
                    && Float.compare(underlineThickness, o.underlineThickness) == 0
                    && Float.compare(underlineOffset, o.underlineOffset) == 0
                    && shadowColor == o.shadowColor
                    && Float.compare(shadowOffsetX, o.shadowOffsetX) == 0
                    && Float.compare(shadowOffsetY, o.shadowOffsetY) == 0
                    && Float.compare(shadowSize, o.shadowSize) == 0;
        }
    }

    private static List<Run> parse(String raw) {
        List<Run> runs = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return runs;
        Deque<Frame> stack = new ArrayDeque<>();
        Frame cur = new Frame();
        StringBuilder buf = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '[' && i + 1 < raw.length() && raw.charAt(i + 1) == '[') {
                buf.append('[');
                i += 2;
                continue;
            }
            if (c == '[') {
                int close = markupTagClose(raw, i);
                if (close > i) {
                    String body = raw.substring(i + 1, close).trim();
                    flush(runs, buf, cur);
                    if (body.startsWith("/")) {
                        cur = applyClose(stack, cur, body.substring(1).trim());
                    } else {
                        stack.push(new Frame(cur));
                        applyOpen(cur, body);
                    }
                    i = close + 1;
                    continue;
                }
            }
            if (c == '§' && i + 1 < raw.length()) {
                ChatFormatting fmt = ChatFormatting.getByCode(raw.charAt(i + 1));
                if (fmt != null) {
                    flush(runs, buf, cur);
                    applyLegacy(cur, fmt);
                    i += 2;
                    continue;
                }
            }
            buf.append(c);
            i++;
        }
        flush(runs, buf, cur);
        return runs;
    }

    private static void flush(List<Run> runs, StringBuilder buf, Frame cur) {
        if (buf.length() == 0) return;
        if (!runs.isEmpty()) {
            Run last = runs.get(runs.size() - 1);
            Frame lastF = frameFromRun(last);
            if (lastF.sameLook(cur)) {
                runs.set(runs.size() - 1, new Run(last.text + buf, last.effects, last.color,
                        last.gradient, last.glowColor, last.outlineColor, last.speed, last.amount,
                        last.glowSize, last.outlineWidth,
                        last.underlineColor, last.underlineThickness, last.underlineOffset,
                        last.shadowColor, last.shadowOffsetX, last.shadowOffsetY, last.shadowSize));
                buf.setLength(0);
                return;
            }
        }
        runs.add(new Run(buf.toString(), new LinkedHashSet<>(cur.effects), cur.color, cur.gradient,
                cur.glowColor, cur.outlineColor, cur.speed, cur.amount, cur.glowSize, cur.outlineWidth,
                cur.underlineColor, cur.underlineThickness, cur.underlineOffset,
                cur.shadowColor, cur.shadowOffsetX, cur.shadowOffsetY, cur.shadowSize));
        buf.setLength(0);
    }

    /**
     * Closing {@code ]} of a real effect tag at {@code i} ({@code '['}), or -1.
     * Unknown spans like {@code [Steve]} from chat format {@code [$Name]:} stay visible.
     */
    private static int markupTagClose(String raw, int i) {
        if (raw == null || i < 0 || i >= raw.length() || raw.charAt(i) != '[') return -1;
        if (i + 1 < raw.length() && raw.charAt(i + 1) == '[') return -1;
        int close = raw.indexOf(']', i + 1);
        if (close <= i) return -1;
        if (!isMarkupTag(raw.substring(i + 1, close))) return -1;
        return close;
    }

    private static boolean isMarkupTag(String body) {
        if (body == null) return false;
        String t = body.trim();
        if (t.isEmpty()) return false;
        if (t.startsWith("/")) {
            String rest = t.substring(1).trim();
            if (rest.isEmpty() || rest.startsWith("#") || "color".equalsIgnoreCase(rest)) return true;
            int eq = indexOfEq(rest);
            String name = canonical(eq < 0 ? rest : rest.substring(0, eq));
            return isKnownEffectName(name);
        }
        if (t.charAt(0) == '#') return parseColor(t, 0) != 0;
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.startsWith("gradient") || lower.startsWith("g=") || lower.startsWith("g:")) return true;
        List<String> tokens = splitEffects(t);
        if (tokens.isEmpty()) return false;
        for (String token : tokens) {
            if (!isKnownEffectToken(token)) return false;
        }
        return true;
    }

    private static boolean isKnownEffectToken(String token) {
        if (token == null || token.isBlank()) return false;
        String t = token.trim();
        if (t.charAt(0) == '#') return parseColor(t, 0) != 0;
        int eq = indexOfEq(t);
        return isKnownEffectName(canonical(eq < 0 ? t : t.substring(0, eq)));
    }

    private static boolean isKnownEffectName(String name) {
        if (name == null || name.isEmpty()) return false;
        return switch (name) {
            case "bold", "italic", "underline", "strikethrough", "code",
                    "glow", "glow_pixel", "glow_soft",
                    "outline", "outline_pixel", "outline_soft",
                    "shadow", "shadow_pixel", "shadow_soft",
                    "rainbow", "pulse", "wave", "shake", "wiggle",
                    "color", "gradient", "#" -> true;
            default -> name.startsWith("#");
        };
    }

    private static Frame frameFromRun(Run run) {
        Frame f = new Frame();
        f.effects.addAll(run.effects);
        f.color = run.color;
        f.gradient = run.gradient;
        f.glowColor = run.glowColor;
        f.outlineColor = run.outlineColor;
        f.speed = run.speed;
        f.amount = run.amount;
        f.glowSize = run.glowSize;
        f.outlineWidth = run.outlineWidth;
        f.underlineColor = run.underlineColor;
        f.underlineThickness = run.underlineThickness;
        f.underlineOffset = run.underlineOffset;
        f.shadowColor = run.shadowColor;
        f.shadowOffsetX = run.shadowOffsetX;
        f.shadowOffsetY = run.shadowOffsetY;
        f.shadowSize = run.shadowSize;
        return f;
    }

    private static void applyOpen(Frame cur, String body) {
        if (body.isEmpty()) return;
        if (body.charAt(0) == '#') {
            int col = parseColor(body, 0);
            if (col != 0) cur.color = col;
            return;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        if (lower.startsWith("gradient") || lower.startsWith("g=") || lower.startsWith("g:")) {
            int eq = indexOfEq(body);
            String spec = eq < 0 ? body : body.substring(eq + 1);
            int[] g = parseGradient(spec);
            if (g != null) {
                cur.effects.add("gradient");
                cur.gradient = g;
            }
            return;
        }
        for (String token : splitEffects(body)) {
            applyToken(cur, token);
        }
    }

    private static Frame applyClose(Deque<Frame> stack, Frame cur, String name) {
        if (stack.isEmpty()) return cur;
        String key = canonical(name);
        if (key.isEmpty() || "#".equals(key) || "color".equals(key)) {
            return stack.pop();
        }
        while (!stack.isEmpty()) {
            Frame prev = stack.pop();
            if (cur.effects.contains(key) && !prev.effects.contains(key)) return prev;
            if (("gradient".equals(key) && cur.gradient != null && prev.gradient == null)) return prev;
            cur = prev;
        }
        return cur;
    }

    private static void applyToken(Frame cur, String token) {
        if (token == null || token.isBlank()) return;
        String t = token.trim();
        int eq = indexOfEq(t);
        String name = canonical(eq < 0 ? t : t.substring(0, eq));
        String value = eq < 0 ? "" : t.substring(eq + 1).trim();
        switch (name) {
            case "color" -> {
                int col = parseColor(value, 0);
                if (col != 0) cur.color = col;
            }
            case "glow" -> {
                cur.effects.add("glow");
                String[] p = value.split("/");
                if (p.length > 0 && p[0].startsWith("#")) cur.glowColor = parseColor(p[0], cur.glowColor);
                if (p.length > 1) cur.glowSize = parseFloat(p[1], cur.glowSize);
                if (p.length > 2) {
                    cur.effects.remove("glow_pixel");
                    cur.effects.remove("glow_soft");
                    cur.effects.add("pixel".equalsIgnoreCase(p[2]) ? "glow_pixel" : "glow_soft");
                }
            }
            case "glow_pixel", "glow_soft" -> cur.effects.add(name);
            case "outline" -> {
                cur.effects.add("outline");
                String[] p = value.split("/");
                if (p.length > 0 && p[0].startsWith("#")) cur.outlineColor = parseColor(p[0], cur.outlineColor);
                if (p.length > 1) cur.outlineWidth = parseFloat(p[1], cur.outlineWidth);
                if (p.length > 2) {
                    cur.effects.remove("outline_pixel");
                    cur.effects.remove("outline_soft");
                    cur.effects.add("soft".equalsIgnoreCase(p[2]) ? "outline_soft" : "outline_pixel");
                }
            }
            case "outline_pixel", "outline_soft" -> cur.effects.add(name);
            case "shadow" -> {
                cur.effects.add("shadow");
                String[] p = value.split("/");
                if (p.length > 0 && p[0].startsWith("#")) cur.shadowColor = parseColor(p[0], cur.shadowColor);
                if (p.length > 1) cur.shadowOffsetX = parseFloat(p[1], cur.shadowOffsetX);
                if (p.length > 2) cur.shadowOffsetY = parseFloat(p[2], cur.shadowOffsetY);
                if (p.length > 3) cur.shadowSize = parseFloat(p[3], cur.shadowSize);
                if (p.length > 4) {
                    cur.effects.add("soft".equalsIgnoreCase(p[4]) ? "shadow_soft" : "shadow_pixel");
                }
            }
            case "underline" -> {
                cur.effects.add("underline");
                String[] p = value.split("/");
                if (p.length > 0 && p[0].startsWith("#")) cur.underlineColor = parseColor(p[0], cur.underlineColor);
                if (p.length > 1) cur.underlineThickness = parseFloat(p[1], cur.underlineThickness);
                if (p.length > 2) cur.underlineOffset = parseFloat(p[2], cur.underlineOffset);
            }
            case "rainbow", "pulse", "wave", "shake", "wiggle" -> {
                cur.effects.add(name);
                String[] p = value.split("/");
                if (p.length > 0 && !p[0].isEmpty()) cur.speed = parseFloat(p[0], cur.speed);
                if (p.length > 1) cur.amount = parseFloat(p[1], cur.amount);
            }
            case "shadow_pixel", "shadow_soft",
                    "bold", "italic", "strikethrough", "code" -> cur.effects.add(name);
            default -> {
                if (name.startsWith("#")) {
                    int col = parseColor(name, 0);
                    if (col != 0) cur.color = col;
                }
            }
        }
    }

    private static void applyLegacy(Frame cur, ChatFormatting fmt) {
        if (fmt == ChatFormatting.RESET) {
            cur.effects.clear();
            cur.color = 0;
            cur.gradient = null;
            cur.glowColor = 0;
            cur.outlineColor = 0;
            cur.speed = 1f;
            cur.amount = 1f;
            cur.glowSize = Float.NaN;
            cur.outlineWidth = Float.NaN;
            cur.underlineColor = 0;
            cur.underlineThickness = 1f;
            cur.underlineOffset = 0f;
            cur.shadowColor = 0;
            cur.shadowOffsetX = 1f;
            cur.shadowOffsetY = 1f;
            cur.shadowSize = 2f;
            return;
        }
        if (fmt.isColor()) {
            Integer rgb = fmt.getColor();
            if (rgb != null) cur.color = 0xFF000000 | rgb;
            return;
        }
        switch (fmt) {
            case BOLD -> cur.effects.add("bold");
            case ITALIC -> cur.effects.add("italic");
            case UNDERLINE -> cur.effects.add("underline");
            case STRIKETHROUGH -> cur.effects.add("strikethrough");
            default -> {}
        }
    }

    private static String canonical(String raw) {
        if (raw == null) return "";
        String n = raw.toLowerCase(Locale.ROOT).trim();
        return switch (n) {
            case "b" -> "bold";
            case "i" -> "italic";
            case "u" -> "underline";
            case "s", "strike" -> "strikethrough";
            case "c" -> "color";
            case "g" -> "gradient";
            case "#" -> "#";
            default -> n;
        };
    }

    private static List<String> splitEffects(String body) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= body.length(); i++) {
            if (i == body.length() || body.charAt(i) == ',') {
                String part = body.substring(start, i).trim();
                if (!part.isEmpty()) out.add(part);
                start = i + 1;
            }
        }
        return out;
    }

    private static int indexOfEq(String s) {
        int a = s.indexOf('=');
        int b = s.indexOf(':');
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    private static Style toMc(Style mc, UiTextStyle base, Run run) {
        boolean bold = base.bold() || run.effects.contains("bold");
        boolean italic = base.italic() || run.effects.contains("italic");
        boolean under = (base.underline() || run.effects.contains("underline"))
                && run.underlineColor == 0 && base.underlineColor() == 0;
        boolean strike = base.strikethrough() || run.effects.contains("strikethrough");
        boolean code = base.code() || run.effects.contains("code");
        Style s = mc.withBold(bold).withItalic(italic).withUnderlined(under).withStrikethrough(strike);
        if (code) s = s.withFont(UiTextStyle.UNIFORM_FONT);
        if (run.color != 0) s = s.withColor(run.color & 0xFFFFFF);
        String encoded = encode(run);
        if (encoded != null) s = s.withInsertion(MARK + encoded);
        return s;
    }

    private static String encode(Run run) {
        boolean extra = UiTextStyle.hasNamed(run.effects, "shadow")
                || UiTextStyle.hasNamed(run.effects, "outline")
                || UiTextStyle.hasNamed(run.effects, "glow")
                || run.effects.contains("rainbow") || run.effects.contains("pulse")
                || run.effects.contains("wave") || run.effects.contains("shake")
                || run.effects.contains("wiggle") || run.gradient != null
                || run.underlineColor != 0 || run.shadowColor != 0;
        if (!extra) return null;
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String e : run.effects) {
            if ("gradient".equals(e) || "bold".equals(e) || "italic".equals(e)
                    || "strikethrough".equals(e) || "code".equals(e)
                    || "color".equals(e)) continue;
            if (!first) sb.append(',');
            sb.append(e);
            first = false;
        }
        if (run.glowColor != 0) sb.append("|g=").append(hex(run.glowColor));
        if (run.outlineColor != 0) sb.append("|o=").append(hex(run.outlineColor));
        if (run.gradient != null && run.gradient.length >= 2) {
            sb.append("|gr=");
            for (int i = 0; i < run.gradient.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(hex(run.gradient[i]));
            }
        }
        if (run.speed != 1f) sb.append("|sp=").append(run.speed);
        if (run.amount != 1f) sb.append("|am=").append(run.amount);
        if (!Float.isNaN(run.glowSize)) sb.append("|gs=").append(run.glowSize);
        if (!Float.isNaN(run.outlineWidth)) sb.append("|ow=").append(run.outlineWidth);
        if (run.underlineColor != 0) sb.append("|uc=").append(hex(run.underlineColor));
        if (run.underlineThickness != 1f) sb.append("|ut=").append(run.underlineThickness);
        if (run.underlineOffset != 0f) sb.append("|uo=").append(run.underlineOffset);
        if (run.shadowColor != 0) sb.append("|sc=").append(hex(run.shadowColor));
        if (run.shadowOffsetX != 1f) sb.append("|sx=").append(run.shadowOffsetX);
        if (run.shadowOffsetY != 1f) sb.append("|sy=").append(run.shadowOffsetY);
        if (run.shadowSize != 2f) sb.append("|ss=").append(run.shadowSize);
        return sb.toString();
    }

    private static UiTextStyle decode(String payload, UiTextStyle base) {
        Set<String> effects = new LinkedHashSet<>();
        int glowColor = base.glowColor();
        int outlineColor = base.outlineColor();
        int[] gradient = null;
        float speed = 1f;
        float amount = 1f;
        float glowSize = base.glowSize();
        float outlineWidth = base.outlineWidth();
        int underlineColor = 0;
        float underlineThickness = 1f;
        float underlineOffset = 0f;
        int shadowColor = 0;
        float shadowOffsetX = 1f;
        float shadowOffsetY = 1f;
        float shadowSize = 2f;
        if (payload == null) payload = "";
        for (String part : payload.split("\\|")) {
            if (part.isEmpty()) continue;
            if (part.startsWith("g=")) glowColor = parseColor(part.substring(2), glowColor);
            else if (part.startsWith("o=")) outlineColor = parseColor(part.substring(2), outlineColor);
            else if (part.startsWith("gr=")) gradient = parseGradient(part.substring(3));
            else if (part.startsWith("sp=")) speed = parseFloat(part.substring(3), speed);
            else if (part.startsWith("am=")) amount = parseFloat(part.substring(3), amount);
            else if (part.startsWith("gs=")) {
                glowSize = parseFloat(part.substring(3), glowSize);
            } else if (part.startsWith("ow=")) {
                outlineWidth = parseFloat(part.substring(3), outlineWidth);
            } else if (part.startsWith("uc=")) {
                underlineColor = parseColor(part.substring(3), underlineColor);
            } else if (part.startsWith("ut=")) {
                underlineThickness = parseFloat(part.substring(3), underlineThickness);
            } else if (part.startsWith("uo=")) {
                underlineOffset = parseFloat(part.substring(3), underlineOffset);
            } else if (part.startsWith("sc=")) {
                shadowColor = parseColor(part.substring(3), shadowColor);
            } else if (part.startsWith("sx=")) {
                shadowOffsetX = parseFloat(part.substring(3), shadowOffsetX);
            } else if (part.startsWith("sy=")) {
                shadowOffsetY = parseFloat(part.substring(3), shadowOffsetY);
            } else if (part.startsWith("ss=")) {
                shadowSize = parseFloat(part.substring(3), shadowSize);
            } else {
                for (String e : part.split(",")) {
                    if (!e.isBlank()) effects.add(e.trim());
                }
            }
        }
        return UiTextStyle.fromEffectSet(effects, outlineColor, glowColor, gradient,
                speed, amount, glowSize, outlineWidth,
                underlineColor, underlineThickness, underlineOffset,
                shadowColor, shadowOffsetX, shadowOffsetY, shadowSize);
    }

    private static int[] parseGradient(String spec) {
        if (spec == null || spec.isBlank()) return null;
        List<Integer> colors = new ArrayList<>();
        for (String part : spec.split(",")) {
            int c = parseColor(part.trim(), 0);
            if (c != 0) colors.add(c);
        }
        if (colors.size() < 2) return null;
        return colors.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int parseColor(String raw, int fallback) {
        if (raw == null) return fallback;
        String s = raw.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 6) return 0xFF000000 | Integer.parseUnsignedInt(s, 16);
            if (s.length() == 8) return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException ignored) {}
        return fallback;
    }

    private static float parseFloat(String raw, float fallback) {
        try {
            return Float.parseFloat(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String hex(int color) {
        return String.format(Locale.ROOT, "#%08X", color);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
