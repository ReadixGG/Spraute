package org.zonarstudio.spraute_engine.client.editor.icons;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal Lucide SVG → raster converter (path/rect/circle/line + stroke/fill).
 * No external SVG dependency — Lucide icons are a tiny stroke-based subset.
 */
public final class LucideSvgRasterizer {
    private static final Pattern VIEW_BOX = Pattern.compile(
            "viewBox\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern STROKE_WIDTH = Pattern.compile(
            "stroke-width\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern STROKE_LINECAP = Pattern.compile(
            "stroke-linecap\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern STROKE_LINEJOIN = Pattern.compile(
            "stroke-linejoin\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ELEMENT = Pattern.compile(
            "<(path|rect|circle|line|polyline|polygon|ellipse)\\b([^>]*)/?>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SVG_TAG = Pattern.compile(
            "<svg\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile(
            "([a-zA-Z_:][\\w:.-]*)\\s*=\\s*\"([^\"]*)\"");

    /**
     * Paint for GUI SVG widgets. Lucide editor icons use {@link #LUCIDE}
     * (white, square, original stroke defaults).
     */
    public record Paint(Color current, Color strokeOverride, Float strokeWidth,
                        boolean recolor, boolean lucideCompat) {
        public static final Paint LUCIDE = new Paint(Color.WHITE, null, null, false, true);

        public static Paint gui(Color current, Color stroke, Float strokeWidth, boolean recolor) {
            return new Paint(current != null ? current : Color.WHITE, stroke, strokeWidth, recolor, false);
        }
    }

    private LucideSvgRasterizer() {}

    public static BufferedImage rasterize(String svg, int pixelSize) {
        return rasterize(svg, pixelSize, pixelSize, Paint.LUCIDE);
    }

    public static BufferedImage rasterize(String svg, int width, int height, Paint paint) {
        if (svg == null || svg.isBlank() || width < 4 || height < 4) return null;
        Paint style = paint != null ? paint : Paint.LUCIDE;
        float[] view = parseViewBox(svg);
        float vbX = view[0], vbY = view[1], vbW = view[2], vbH = view[3];
        Attrs root = parseSvgRoot(svg);
        float strokeW = style.strokeWidth != null && style.strokeWidth > 0f
                ? style.strokeWidth
                : root.f("stroke-width", parseFloatAttr(STROKE_WIDTH, svg, 2f));
        int cap = switch (root.get("stroke-linecap",
                parseAttr(STROKE_LINECAP, svg, "round")).toLowerCase(Locale.ROOT)) {
            case "butt" -> BasicStroke.CAP_BUTT;
            case "square" -> BasicStroke.CAP_SQUARE;
            default -> BasicStroke.CAP_ROUND;
        };
        int join = switch (root.get("stroke-linejoin",
                parseAttr(STROKE_LINEJOIN, svg, "round")).toLowerCase(Locale.ROOT)) {
            case "miter" -> BasicStroke.JOIN_MITER;
            case "bevel" -> BasicStroke.JOIN_BEVEL;
            default -> BasicStroke.JOIN_ROUND;
        };

        int pad = Math.max(2, Math.round(Math.min(width, height) * 0.06f));
        int rw = Math.max(8, width);
        int rh = Math.max(8, height);
        BufferedImage image = new BufferedImage(rw, rh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (style.lucideCompat && rw == rh) {
                // Faithful Lucide weight (stroke-width≈2). Readability at 12–16px comes from
                // hi-res raster + linear filtering — not from thickening / dilating strokes.
                float scale = (rw - pad * 2f) / Math.max(vbW, vbH);
                g.translate(pad - vbX * scale, pad - vbY * scale);
                g.scale(scale, scale);
            } else {
                float innerW = Math.max(1f, rw - pad * 2f);
                float innerH = Math.max(1f, rh - pad * 2f);
                float scale = Math.min(innerW / Math.max(1f, vbW), innerH / Math.max(1f, vbH));
                g.translate(pad + (innerW - vbW * scale) * 0.5f - vbX * scale,
                        pad + (innerH - vbH * scale) * 0.5f - vbY * scale);
                g.scale(scale, scale);
            }
            g.setStroke(new BasicStroke(strokeW, cap, join, 10f));
            g.setColor(style.current != null ? style.current : Color.WHITE);

            String defFill = root.get("fill", "none");
            String defStroke = root.get("stroke", "currentColor");
            Matcher m = ELEMENT.matcher(svg);
            while (m.find()) {
                String tag = m.group(1).toLowerCase(Locale.ROOT);
                Attrs attrs = Attrs.parse(m.group(2));
                attrs.applyCssStyle();
                if (style.lucideCompat) {
                    drawLucideElement(g, tag, attrs, strokeW, cap, join);
                } else {
                    drawStyledElement(g, tag, attrs, strokeW, cap, join, style, defFill, defStroke);
                }
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void drawLucideElement(Graphics2D g, String tag, Attrs a,
                                          float strokeW, int cap, int join) {
        boolean stroke = !"none".equalsIgnoreCase(a.get("stroke", "currentColor"));
        String fill = a.get("fill", "none");
        boolean doFill = fill != null && !"none".equalsIgnoreCase(fill);

        switch (tag) {
            case "path" -> {
                Path2D path = parsePath(a.get("d", ""));
                if (path == null) return;
                if (doFill) {
                    g.fill(path);
                }
                if (stroke) g.draw(path);
            }
            case "rect" -> {
                float x = a.f("x", 0);
                float y = a.f("y", 0);
                float w = a.f("width", 0);
                float h = a.f("height", 0);
                float rx = a.f("rx", a.f("ry", 0));
                float ry = a.f("ry", rx);
                RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, w, h, rx * 2f, ry * 2f);
                if (doFill) g.fill(shape);
                if (stroke) g.draw(shape);
            }
            case "circle" -> {
                float cx = a.f("cx", 0);
                float cy = a.f("cy", 0);
                float r = a.f("r", 0);
                Ellipse2D shape = new Ellipse2D.Float(cx - r, cy - r, r * 2f, r * 2f);
                // Lucide palette dots: fill="currentColor" with tiny radius.
                boolean fillDot = doFill || "currentColor".equalsIgnoreCase(fill);
                if (fillDot) g.fill(shape);
                if (stroke && !fillDot) g.draw(shape);
                else if (stroke && r > 1.2f) g.draw(shape);
            }
            case "ellipse" -> {
                float cx = a.f("cx", 0);
                float cy = a.f("cy", 0);
                float rx = a.f("rx", 0);
                float ry = a.f("ry", 0);
                Ellipse2D shape = new Ellipse2D.Float(cx - rx, cy - ry, rx * 2f, ry * 2f);
                if (doFill) g.fill(shape);
                if (stroke) g.draw(shape);
            }
            case "line" -> {
                if (stroke) {
                    g.draw(new Line2D.Float(a.f("x1", 0), a.f("y1", 0), a.f("x2", 0), a.f("y2", 0)));
                }
            }
            case "polyline", "polygon" -> {
                Path2D path = parsePoints(a.get("points", ""), tag.equals("polygon"));
                if (path == null) return;
                if (doFill) g.fill(path);
                if (stroke) g.draw(path);
            }
            default -> {}
        }
        // Restore stroke in case fills changed paint state elsewhere.
        g.setStroke(new BasicStroke(strokeW, cap, join, 4f));
        g.setColor(Color.WHITE);
    }

    private static void drawStyledElement(Graphics2D g, String tag, Attrs a,
                                          float defaultStrokeW, int cap, int join,
                                          Paint paint, String defFill, String defStroke) {
        Color fillColor = resolvePaint(a.get("fill", defFill), paint, false);
        Color strokeColor = resolvePaint(a.get("stroke", defStroke), paint, true);
        float sw = a.f("stroke-width", defaultStrokeW);
        if (paint.strokeWidth != null && paint.strokeWidth > 0f) sw = paint.strokeWidth;
        java.awt.Shape shape = switch (tag) {
            case "path" -> parsePath(a.get("d", ""));
            case "rect" -> {
                float x = a.f("x", 0);
                float y = a.f("y", 0);
                float w = a.f("width", 0);
                float h = a.f("height", 0);
                float rx = a.f("rx", a.f("ry", 0));
                float ry = a.f("ry", rx);
                yield new RoundRectangle2D.Float(x, y, w, h, rx * 2f, ry * 2f);
            }
            case "circle" -> {
                float cx = a.f("cx", 0);
                float cy = a.f("cy", 0);
                float r = a.f("r", 0);
                yield new Ellipse2D.Float(cx - r, cy - r, r * 2f, r * 2f);
            }
            case "ellipse" -> {
                float cx = a.f("cx", 0);
                float cy = a.f("cy", 0);
                float rx = a.f("rx", 0);
                float ry = a.f("ry", 0);
                yield new Ellipse2D.Float(cx - rx, cy - ry, rx * 2f, ry * 2f);
            }
            case "line" -> new Line2D.Float(a.f("x1", 0), a.f("y1", 0), a.f("x2", 0), a.f("y2", 0));
            case "polyline" -> parsePoints(a.get("points", ""), false);
            case "polygon" -> parsePoints(a.get("points", ""), true);
            default -> null;
        };
        if (shape == null) return;
        if (fillColor != null && !(shape instanceof Line2D)) {
            g.setColor(fillColor);
            g.fill(shape);
        }
        if (strokeColor != null && sw > 0f) {
            g.setStroke(new BasicStroke(sw, cap, join, 10f));
            g.setColor(strokeColor);
            g.draw(shape);
        }
    }

    private static Color resolvePaint(String raw, Paint paint, boolean isStroke) {
        String v = raw == null ? "" : raw.trim();
        Color current = isStroke && paint.strokeOverride != null ? paint.strokeOverride
                : (paint.current != null ? paint.current : Color.WHITE);
        if (v.isEmpty()) {
            return isStroke ? current : null;
        }
        if ("none".equalsIgnoreCase(v) || "transparent".equalsIgnoreCase(v)) return null;
        if ("currentcolor".equalsIgnoreCase(v) || "inherit".equalsIgnoreCase(v)) {
            return current;
        }
        if (paint.recolor) return current;
        Color parsed = parseSvgColor(v);
        return parsed != null ? parsed : current;
    }

    /** Widget / Inspector color: {@code #RGB}, {@code #RRGGBB}, {@code #AARRGGBB}. */
    public static Color parseMcColor(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String hex = stripColor(raw);
            if (hex.length() == 3) {
                int r = nibble(hex.charAt(0));
                int g = nibble(hex.charAt(1));
                int b = nibble(hex.charAt(2));
                return new Color(r, g, b);
            }
            if (hex.length() == 6) {
                int rgb = (int) Long.parseLong(hex, 16);
                return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            }
            if (hex.length() == 8) {
                int argb = (int) Long.parseLong(hex, 16);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                return new Color(r, g, b, a);
            }
        } catch (NumberFormatException ignored) {
        }
        return parseSvgColor(raw);
    }

    /** SVG paint: {@code #RGB}, {@code #RRGGBB}, {@code #RRGGBBAA}, {@code rgb()}/{@code rgba()}. */
    public static Color parseSvgColor(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim();
        if (v.toLowerCase(Locale.ROOT).startsWith("rgb")) return parseCssRgb(v);
        try {
            String hex = stripColor(v);
            if (hex.length() == 3) {
                int r = nibble(hex.charAt(0));
                int g = nibble(hex.charAt(1));
                int b = nibble(hex.charAt(2));
                return new Color(r, g, b);
            }
            if (hex.length() == 6) {
                int rgb = (int) Long.parseLong(hex, 16);
                return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            }
            if (hex.length() == 8) {
                int rgba = (int) Long.parseLong(hex, 16);
                int r = (rgba >>> 24) & 0xFF;
                int g = (rgba >> 16) & 0xFF;
                int b = (rgba >> 8) & 0xFF;
                int a = rgba & 0xFF;
                return new Color(r, g, b, a);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static Color parseCssRgb(String raw) {
        int open = raw.indexOf('(');
        int close = raw.indexOf(')');
        if (open < 0 || close < open) return null;
        String[] parts = raw.substring(open + 1, close).split("[,\\s]+");
        if (parts.length < 3) return null;
        try {
            int r = clampByte(parseCssChannel(parts[0]));
            int g = clampByte(parseCssChannel(parts[1]));
            int b = clampByte(parseCssChannel(parts[2]));
            int a = parts.length >= 4 ? clampByte(Math.round(parseCssAlpha(parts[3]) * 255f)) : 255;
            return new Color(r, g, b, a);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static float parseCssChannel(String s) {
        String v = s.trim();
        if (v.endsWith("%")) return Float.parseFloat(v.substring(0, v.length() - 1)) * 2.55f;
        return Float.parseFloat(v);
    }

    private static float parseCssAlpha(String s) {
        String v = s.trim();
        if (v.endsWith("%")) return Float.parseFloat(v.substring(0, v.length() - 1)) / 100f;
        float n = Float.parseFloat(v);
        return n > 1f ? n / 255f : n;
    }

    private static int clampByte(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    private static int nibble(char c) {
        int n = Character.digit(c, 16);
        if (n < 0) return 0;
        return n * 16 + n;
    }

    private static String stripColor(String raw) {
        String s = raw.trim();
        if (s.startsWith("#")) s = s.substring(1);
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) break;
            i++;
        }
        return s.substring(0, i);
    }

    private static Attrs parseSvgRoot(String svg) {
        Matcher m = SVG_TAG.matcher(svg);
        if (!m.find()) return new Attrs();
        Attrs a = Attrs.parse(m.group(1));
        a.applyCssStyle();
        return a;
    }

    private static Path2D parsePoints(String points, boolean close) {
        List<Float> nums = readNumbers(points);
        if (nums.size() < 4) return null;
        Path2D.Float path = new Path2D.Float();
        path.moveTo(nums.get(0), nums.get(1));
        for (int i = 2; i + 1 < nums.size(); i += 2) {
            path.lineTo(nums.get(i), nums.get(i + 1));
        }
        if (close) path.closePath();
        return path;
    }

    private static Path2D parsePath(String d) {
        if (d == null || d.isBlank()) return null;
        Path2D.Float path = new Path2D.Float(Path2D.WIND_NON_ZERO);
        List<Float> nums = new ArrayList<>();
        int i = 0;
        char cmd = 0;
        float cx = 0, cy = 0, startX = 0, startY = 0;
        float prevCtrlX = 0, prevCtrlY = 0;
        char prevCmd = 0;
        while (i < d.length()) {
            char c = d.charAt(i);
            if (Character.isLetter(c)) {
                cmd = c;
                i++;
            } else if (c == ',' || Character.isWhitespace(c)) {
                i++;
                continue;
            } else if (cmd == 0) {
                i++;
                continue;
            }
            nums.clear();
            i = readNumberList(d, i, nums);
            boolean rel = Character.isLowerCase(cmd);
            char op = Character.toUpperCase(cmd);
            int p = 0;
            switch (op) {
                case 'M' -> {
                    if (nums.size() < 2) break;
                    float x = nums.get(p++);
                    float y = nums.get(p++);
                    if (rel) { x += cx; y += cy; }
                    path.moveTo(x, y);
                    cx = startX = x;
                    cy = startY = y;
                    while (p + 1 < nums.size()) {
                        x = nums.get(p++);
                        y = nums.get(p++);
                        if (rel) { x += cx; y += cy; }
                        path.lineTo(x, y);
                        cx = x;
                        cy = y;
                    }
                    prevCmd = 'L';
                }
                case 'L' -> {
                    while (p + 1 < nums.size()) {
                        float x = nums.get(p++);
                        float y = nums.get(p++);
                        if (rel) { x += cx; y += cy; }
                        path.lineTo(x, y);
                        cx = x;
                        cy = y;
                    }
                    prevCmd = 'L';
                }
                case 'H' -> {
                    while (p < nums.size()) {
                        float x = nums.get(p++);
                        if (rel) x += cx;
                        path.lineTo(x, cy);
                        cx = x;
                    }
                    prevCmd = 'L';
                }
                case 'V' -> {
                    while (p < nums.size()) {
                        float y = nums.get(p++);
                        if (rel) y += cy;
                        path.lineTo(cx, y);
                        cy = y;
                    }
                    prevCmd = 'L';
                }
                case 'C' -> {
                    while (p + 5 < nums.size()) {
                        float x1 = nums.get(p++), y1 = nums.get(p++);
                        float x2 = nums.get(p++), y2 = nums.get(p++);
                        float x = nums.get(p++), y = nums.get(p++);
                        if (rel) {
                            x1 += cx; y1 += cy;
                            x2 += cx; y2 += cy;
                            x += cx; y += cy;
                        }
                        path.curveTo(x1, y1, x2, y2, x, y);
                        prevCtrlX = x2;
                        prevCtrlY = y2;
                        cx = x;
                        cy = y;
                    }
                    prevCmd = 'C';
                }
                case 'S' -> {
                    while (p + 3 < nums.size()) {
                        float x2 = nums.get(p++), y2 = nums.get(p++);
                        float x = nums.get(p++), y = nums.get(p++);
                        float x1, y1;
                        if (prevCmd == 'C' || prevCmd == 'S') {
                            x1 = cx * 2f - prevCtrlX;
                            y1 = cy * 2f - prevCtrlY;
                        } else {
                            x1 = cx;
                            y1 = cy;
                        }
                        if (rel) {
                            x2 += cx; y2 += cy;
                            x += cx; y += cy;
                        }
                        path.curveTo(x1, y1, x2, y2, x, y);
                        prevCtrlX = x2;
                        prevCtrlY = y2;
                        cx = x;
                        cy = y;
                    }
                    prevCmd = 'S';
                }
                case 'Q' -> {
                    while (p + 3 < nums.size()) {
                        float x1 = nums.get(p++), y1 = nums.get(p++);
                        float x = nums.get(p++), y = nums.get(p++);
                        if (rel) {
                            x1 += cx; y1 += cy;
                            x += cx; y += cy;
                        }
                        path.quadTo(x1, y1, x, y);
                        prevCtrlX = x1;
                        prevCtrlY = y1;
                        cx = x;
                        cy = y;
                    }
                    prevCmd = 'Q';
                }
                case 'T' -> {
                    while (p + 1 < nums.size()) {
                        float x = nums.get(p++), y = nums.get(p++);
                        float x1, y1;
                        if (prevCmd == 'Q' || prevCmd == 'T') {
                            x1 = cx * 2f - prevCtrlX;
                            y1 = cy * 2f - prevCtrlY;
                        } else {
                            x1 = cx;
                            y1 = cy;
                        }
                        if (rel) { x += cx; y += cy; }
                        path.quadTo(x1, y1, x, y);
                        prevCtrlX = x1;
                        prevCtrlY = y1;
                        cx = x;
                        cy = y;
                    }
                    prevCmd = 'T';
                }
                case 'A' -> {
                    while (p + 6 < nums.size()) {
                        float rx = nums.get(p++), ry = nums.get(p++);
                        float xAxisRot = nums.get(p++);
                        float large = nums.get(p++), sweep = nums.get(p++);
                        float x = nums.get(p++), y = nums.get(p++);
                        if (rel) { x += cx; y += cy; }
                        arcTo(path, cx, cy, rx, ry, xAxisRot, large != 0, sweep != 0, x, y);
                        cx = x;
                        cy = y;
                    }
                    prevCmd = 'A';
                }
                case 'Z' -> {
                    path.closePath();
                    cx = startX;
                    cy = startY;
                    prevCmd = 'Z';
                }
                default -> {}
            }
            if (op != 'C' && op != 'S' && op != 'Q' && op != 'T') {
                // keep prevCtrl for smooth curves only when relevant; already set above
            }
        }
        return path;
    }

    /** SVG elliptical arc → cubic beziers (W3C endpoint parameterization). */
    private static void arcTo(Path2D path, float x1, float y1, float rx, float ry,
                              float angleDeg, boolean largeArc, boolean sweep,
                              float x2, float y2) {
        if (rx == 0 || ry == 0) {
            path.lineTo(x2, y2);
            return;
        }
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        double phi = Math.toRadians(angleDeg % 360f);
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);
        double dx2 = (x1 - x2) / 2.0;
        double dy2 = (y1 - y2) / 2.0;
        double x1p = cosPhi * dx2 + sinPhi * dy2;
        double y1p = -sinPhi * dx2 + cosPhi * dy2;
        double rxs = rx * rx;
        double rys = ry * ry;
        double x1ps = x1p * x1p;
        double y1ps = y1p * y1p;
        double lambda = x1ps / rxs + y1ps / rys;
        if (lambda > 1) {
            double s = Math.sqrt(lambda);
            rx *= (float) s;
            ry *= (float) s;
            rxs = rx * rx;
            rys = ry * ry;
        }
        double sign = (largeArc == sweep) ? -1.0 : 1.0;
        double sq = (rxs * rys - rxs * y1ps - rys * x1ps) / (rxs * y1ps + rys * x1ps);
        sq = (sq < 0) ? 0 : sq;
        double coef = sign * Math.sqrt(sq);
        double cxp = coef * (rx * y1p) / ry;
        double cyp = coef * -(ry * x1p) / rx;
        double cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2.0;
        double cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2.0;
        double theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry);
        double dtheta = angle((x1p - cxp) / rx, (y1p - cyp) / ry,
                (-x1p - cxp) / rx, (-y1p - cyp) / ry);
        if (!sweep && dtheta > 0) dtheta -= Math.PI * 2;
        else if (sweep && dtheta < 0) dtheta += Math.PI * 2;

        int segments = Math.max(1, (int) Math.ceil(Math.abs(dtheta) / (Math.PI / 2)));
        double delta = dtheta / segments;
        double t = (4.0 / 3.0) * Math.tan(delta / 4.0);
        double a = theta1;
        for (int i = 0; i < segments; i++) {
            double a1 = a;
            double a2 = a + delta;
            double cos1 = Math.cos(a1), sin1 = Math.sin(a1);
            double cos2 = Math.cos(a2), sin2 = Math.sin(a2);
            double ep1x = cx + cosPhi * rx * cos1 - sinPhi * ry * sin1;
            double ep1y = cy + sinPhi * rx * cos1 + cosPhi * ry * sin1;
            double ep2x = cx + cosPhi * rx * cos2 - sinPhi * ry * sin2;
            double ep2y = cy + sinPhi * rx * cos2 + cosPhi * ry * sin2;
            double q1x = ep1x + t * (-cosPhi * rx * sin1 - sinPhi * ry * cos1);
            double q1y = ep1y + t * (-sinPhi * rx * sin1 + cosPhi * ry * cos1);
            double q2x = ep2x + t * (cosPhi * rx * sin2 + sinPhi * ry * cos2);
            double q2y = ep2y + t * (sinPhi * rx * sin2 - cosPhi * ry * cos2);
            if (i == 0 && path.getCurrentPoint() == null) {
                path.moveTo(ep1x, ep1y);
            }
            path.curveTo(q1x, q1y, q2x, q2y, ep2x, ep2y);
            a = a2;
        }
    }

    private static double angle(double ux, double uy, double vx, double vy) {
        double sign = (ux * vy - uy * vx) < 0 ? -1.0 : 1.0;
        double dot = ux * vx + uy * vy;
        double len = Math.sqrt(ux * ux + uy * uy) * Math.sqrt(vx * vx + vy * vy);
        if (len == 0) return 0;
        double cos = Math.max(-1, Math.min(1, dot / len));
        return sign * Math.acos(cos);
    }

    private static int readNumberList(String s, int i, List<Float> out) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) break;
            if (c == ',' || Character.isWhitespace(c)) {
                i++;
                continue;
            }
            int start = i;
            if (c == '+' || c == '-') i++;
            boolean seenDot = false;
            boolean seenExp = false;
            while (i < s.length()) {
                char ch = s.charAt(i);
                if (ch >= '0' && ch <= '9') {
                    i++;
                } else if (ch == '.' && !seenDot && !seenExp) {
                    seenDot = true;
                    i++;
                } else if ((ch == 'e' || ch == 'E') && !seenExp) {
                    seenExp = true;
                    i++;
                    if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                } else break;
            }
            if (i > start) {
                try {
                    out.add(Float.parseFloat(s.substring(start, i)));
                } catch (NumberFormatException ignored) {
                    break;
                }
            } else break;
        }
        return i;
    }

    private static List<Float> readNumbers(String s) {
        List<Float> out = new ArrayList<>();
        readNumberList(s, 0, out);
        return out;
    }

    private static float[] parseViewBox(String svg) {
        Matcher m = VIEW_BOX.matcher(svg);
        if (m.find()) {
            List<Float> n = readNumbers(m.group(1));
            if (n.size() >= 4) return new float[]{n.get(0), n.get(1), n.get(2), n.get(3)};
        }
        return new float[]{0, 0, 24, 24};
    }

    private static String parseAttr(Pattern p, String svg, String def) {
        Matcher m = p.matcher(svg);
        return m.find() ? m.group(1) : def;
    }

    private static float parseFloatAttr(Pattern p, String svg, float def) {
        try {
            return Float.parseFloat(parseAttr(p, svg, Float.toString(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static final class Attrs {
        private final java.util.Map<String, String> map = new java.util.HashMap<>();

        static Attrs parse(String raw) {
            Attrs a = new Attrs();
            Matcher m = ATTR.matcher(raw == null ? "" : raw);
            while (m.find()) a.map.put(m.group(1).toLowerCase(Locale.ROOT), m.group(2));
            return a;
        }

        String get(String key, String def) {
            return map.getOrDefault(key.toLowerCase(Locale.ROOT), def);
        }

        void applyCssStyle() {
            String style = map.get("style");
            if (style == null || style.isBlank()) return;
            for (String part : style.split(";")) {
                int colon = part.indexOf(':');
                if (colon <= 0) continue;
                String key = part.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String val = part.substring(colon + 1).trim();
                if (key.isEmpty() || val.isEmpty()) continue;
                map.putIfAbsent(key, val);
            }
        }

        float f(String key, float def) {
            String v = map.get(key.toLowerCase(Locale.ROOT));
            if (v == null || v.isBlank()) return def;
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                return def;
            }
        }
    }
}
