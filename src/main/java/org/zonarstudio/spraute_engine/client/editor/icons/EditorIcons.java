package org.zonarstudio.spraute_engine.client.editor.icons;

import com.mojang.blaze3d.platform.NativeImage;
import imgui.ImGui;
import imgui.ImDrawList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lucide SVG icons from {@code assets/spraute_engine/textures/editor/icons/*.svg},
 * rasterized once to white GL textures and tinted in ImGui.
 */
public final class EditorIcons {
    public record Tex(int glId, int size) {}

    /** Hi-res white raster; drawn with linear filtering. Keep Lucide stroke weight faithful. */
    private static final int RASTER_SIZE = 96;
    /** Bump to invalidate in-memory GL cache after bake changes. */
    private static final int BAKE_VERSION = 7;
    private static int loadedBakeVersion = -1;
    private static final Map<String, Tex> CACHE = new HashMap<>();
    private static final Map<String, DynamicTexture> KEEP_ALIVE = new HashMap<>();
    private static final Tex MISSING = new Tex(-1, 0);

    private EditorIcons() {}

    private static void ensureBakeVersion() {
        if (loadedBakeVersion == BAKE_VERSION) return;
        clear();
        loadedBakeVersion = BAKE_VERSION;
    }
    public static String forNodeType(SceneNodeType type) {
        return switch (type) {
            case SCENE -> "scene";
            case PLAYERS -> "users";
            case GLOBAL_WORLD -> "world";
            case LOCAL_WORLD -> "house";
            case PLAYER -> "user";
            case CAMERA -> "camera";
            case AUDIO -> "radio";
            case SKIN_OVERLAY -> "skin_overlay";
            case NPC -> "npc";
            case MODEL -> "model";
            case BILLBOARD -> "billboard";
            case HITBOX -> "square-dashed";
            case PARTICLE -> "wand-sparkles";
            case LIGHT -> "sun";
            case AUDIO_SOURCE -> "radio";
            case POST_PROCESS -> "palette";
            case CUSTOM -> "plugin";
            case RES_BLOCK -> "box";
            case RES_ITEM -> "gem";
            case RES_CRAFT -> "craft";
            case RES_TAB -> "tab";
            case RES_DROP -> "drop";
            case RES_WORLD -> "world";
            case RES_PARTICLE -> "sparkles";
            case RES_PROJECTILE -> "crosshair";
            case SCRIPT -> "scroll-text";
            case VISUAL_SCRIPT -> "blocks";
            case ANIMATOR -> "anim";
            case ROUTE -> "spline";
            case ROUTE_POINT -> "route_point";
            case EMPTY -> "square-minus";
            case CANVAS -> "rect";
            case UI_GRID -> "grid-3x3";
            case UI_TEXT -> "text";
            case UI_BUTTON -> "mouse-pointer-2";
            case UI_IMAGE -> "image";
            case UI_VIDEO -> "video";
            case UI_SVG -> "spline";
            case UI_RECT -> "rect";
            case UI_INPUT -> "text-cursor-input";
            case UI_SCROLL -> "scroll";
            case UI_DIVIDER -> "line";
            case UI_ENTITY -> "entity";
            case UI_BLOCK -> "block";
            case UI_ITEM -> "item";
            case UI_CROP -> "crop";
            case UI_GROUP -> "group";
            case UI_DRAG -> "move";
            case UI_DROPTARGET -> "squares-intersect";
            case UI_SLOT -> "slot";
            case UI_INVENTORY -> "inventory";
        };
    }

    public static String forProjectFile(String kind) {
        return switch (kind) {
            case "scene" -> "scene";
            case "prefab" -> "plugin";
            case "script" -> "scroll-text";
            case "vscript" -> "blocks";
            case "model" -> "model_file";
            case "anim" -> "anim";
            case "texture" -> "image_file";
            case "svg" -> "spline";
            case "video" -> "file-play";
            case "route" -> "spline";
            case "text" -> "file-text";
            default -> "file";
        };
    }

    /** File kind used by the project explorer / export tree. */
    public static String projectFileKind(java.nio.file.Path file) {
        if (file == null) return "file";
        String lower = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".scene.json")) return "scene";
        if (lower.endsWith(".prefab.json")) return "prefab";
        if (lower.endsWith(".sprv")) return "vscript";
        if (lower.endsWith(".spr")) return "script";
        if (lower.endsWith(".geo.json")) return "model";
        if (lower.endsWith(".animation.json")) return "anim";
        if (lower.endsWith(".mp4")) return "video";
        if (lower.endsWith(".svg")) return "svg";
        if (lower.endsWith(".route.json")) return "route";
        if (isInWorkspaceFolder(file, "routes") && lower.endsWith(".json")) return "route";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "texture";
        if (lower.endsWith(".json") || lower.endsWith(".md") || lower.endsWith(".txt")
                || lower.endsWith(".toml") || lower.endsWith(".cfg")) return "text";
        return "file";
    }

    public static float[] tintForProjectFile(String kind) {
        return switch (kind == null ? "" : kind) {
            case "scene" -> new float[]{0.41f, 0.66f, 1f, 1f};
            case "script" -> new float[]{0.72f, 0.58f, 0.96f, 1f};
            case "vscript" -> new float[]{0.80f, 0.40f, 1f, 1f};
            case "prefab" -> new float[]{0.45f, 0.90f, 0.60f, 1f};
            case "model" -> new float[]{0.95f, 0.72f, 0.38f, 1f};
            case "anim" -> new float[]{0.98f, 0.62f, 0.85f, 1f};
            case "texture" -> new float[]{0.45f, 0.85f, 0.95f, 1f};
            case "svg" -> new float[]{0.72f, 0.62f, 1f, 1f};
            case "video" -> new float[]{0.45f, 0.78f, 1f, 1f};
            case "route" -> new float[]{0.55f, 0.82f, 1f, 1f};
            default -> new float[]{0.78f, 0.80f, 0.86f, 1f};
        };
    }

    /** Draw a tinted icon over a selectable/file row (left padding like the project panel). */
    public static void drawOverSelectableLabel(String name, float size, float[] rgba) {
        Tex tex = get(name);
        if (tex == null) return;
        float x = ImGui.getItemRectMinX() + 2f;
        float y = ImGui.getItemRectMinY() + (ImGui.getItemRectSizeY() - size) * 0.5f;
        ImGui.getWindowDrawList().addImage(tex.glId(), x, y, x + size, y + size,
                0f, 0f, 1f, 1f, packAbgr(rgba));
    }

    /** Special workspace folders → same icons/tints as their file kinds. */
    public static String forProjectFolder(String folderName) {
        if (folderName == null) return "folder";
        return switch (folderName.toLowerCase(java.util.Locale.ROOT)) {
            case "scenes" -> "scene";
            case "prefabs" -> "folder-prefab";
            case "scripts" -> "scroll-text";
            case "geo", "models" -> "model_file";
            case "animations", "animation" -> "anim";
            case "textures", "texture" -> "image_file";
            case "videos", "video" -> "video";
            case "routes", "route" -> "spline";
            default -> "folder";
        };
    }

    public static float[] tintForProjectFolder(String folderName) {
        if (folderName == null) return new float[]{0.86f, 0.78f, 0.45f, 1f};
        return switch (folderName.toLowerCase(java.util.Locale.ROOT)) {
            case "scenes" -> new float[]{0.41f, 0.66f, 1f, 1f};
            case "prefabs" -> new float[]{0.45f, 0.90f, 0.60f, 1f};
            case "scripts" -> new float[]{0.72f, 0.58f, 0.96f, 1f};
            case "geo", "models" -> new float[]{0.95f, 0.72f, 0.38f, 1f};
            case "animations", "animation" -> new float[]{0.98f, 0.62f, 0.85f, 1f};
            case "textures", "texture" -> new float[]{0.45f, 0.85f, 0.95f, 1f};
            case "videos", "video" -> new float[]{0.45f, 0.78f, 1f, 1f};
            case "routes", "route" -> new float[]{0.55f, 0.82f, 1f, 1f};
            default -> new float[]{0.86f, 0.78f, 0.45f, 1f};
        };
    }

    public static boolean isSpecialProjectFolder(String folderName) {
        if (folderName == null) return false;
        return switch (folderName.toLowerCase(java.util.Locale.ROOT)) {
            case "scenes", "prefabs", "scripts", "geo", "models",
                    "animations", "animation", "textures", "texture",
                    "videos", "video", "routes", "route" -> true;
            default -> false;
        };
    }

    private static boolean isInWorkspaceFolder(java.nio.file.Path file, String folderName) {
        if (file == null || folderName == null || folderName.isBlank()) return false;
        String want = folderName.toLowerCase(java.util.Locale.ROOT);
        for (java.nio.file.Path p = file.getParent(); p != null; p = p.getParent()) {
            java.nio.file.Path name = p.getFileName();
            if (name != null && want.equals(name.toString().toLowerCase(java.util.Locale.ROOT))) return true;
        }
        return false;
    }

    public static Tex get(String name) {
        if (name == null || name.isBlank()) return null;
        ensureBakeVersion();
        String key = name.endsWith(".svg") ? name.substring(0, name.length() - 4) : name;
        Tex cached = CACHE.get(key);
        if (cached != null) return cached == MISSING ? null : cached;
        Tex loaded = load(key);
        CACHE.put(key, loaded == null ? MISSING : loaded);
        return loaded;
    }

    public static void image(String name, float size) {
        image(name, size, 1f, 1f, 1f, 1f);
    }

    public static void image(String name, float size, float r, float g, float b, float a) {
        Tex tex = get(name);
        if (tex == null) {
            ImGui.dummy(size, size);
            return;
        }
        ImGui.image(tex.glId(), size, size, 0f, 0f, 1f, 1f, r, g, b, a);
    }

    public static void image(String name, float size, float[] rgba) {
        if (rgba == null || rgba.length < 4) image(name, size);
        else image(name, size, rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    /**
     * Icon aligned with ImGui {@code Text} on the same line.
     * Menu bars set CurrLineTextBaseOffset ≈ FramePadding.y for labels; a raw
     * draw at CursorPos sits above "LIVE" / other sameLine captions.
     */
    public static void imageInline(String name, float size, float r, float g, float b, float a) {
        imageInline(name, size, r, g, b, a, true);
    }

    /**
     * @param matchTextBase when true (menu bar), shift down by FramePadding.y to match Text
     */
    public static void imageInline(String name, float size, float r, float g, float b, float a,
                                   boolean matchTextBase) {
        Tex tex = get(name);
        float line = ImGui.getTextLineHeight();
        float textBase = matchTextBase ? ImGui.getStyle().getFramePaddingY() : 0f;
        float pad = textBase + Math.max(0f, (line - size) * 0.5f);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY() + pad;
        if (tex != null) {
            ImGui.getWindowDrawList().addImage(tex.glId(), x, y, x + size, y + size,
                    0f, 0f, 1f, 1f, packAbgr(r, g, b, a));
        }
        // Reserve only the icon box. A full text-line-tall dummy wraps the following
        // sameLine label onto the next row inside menu bars (icon above "LIVE").
        ImGui.dummy(size, size);
    }

    /** Draw a tinted icon over the previous ImGui item (tree row label padding). */
    public static void drawOverTreeLabel(String name, float size, float[] rgba) {
        Tex tex = get(name);
        if (tex == null) return;
        float x = ImGui.getItemRectMinX() + ImGui.getTreeNodeToLabelSpacing();
        float y = ImGui.getItemRectMinY() + (ImGui.getItemRectSizeY() - size) * 0.5f;
        int col = packAbgr(rgba);
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addImage(tex.glId(), x, y, x + size, y + size, 0f, 0f, 1f, 1f, col);
    }

    public static boolean iconButton(String name, String id, float size, String tooltip) {
        return iconButton(name, id, size, tooltip, 0.86f, 0.88f, 0.92f, 1f);
    }

    public static boolean iconButton(String name, String id, float size, String tooltip,
                                     float r, float g, float b, float a) {
        Tex tex = get(name);
        boolean clicked;
        if (tex == null) {
            clicked = ImGui.button((tooltip != null ? tooltip : name) + "##" + id, size + 8f, size + 4f);
        } else {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.16f, 0.18f, 0.22f, 0.55f);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.22f, 0.28f, 0.36f, 0.85f);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.28f, 0.38f, 0.52f, 1f);
            // size + uv + bg + tint (imgui-java 1.89)
            clicked = ImGui.imageButton("##icon-" + id, tex.glId(), size, size,
                    0f, 0f, 1f, 1f,
                    0f, 0f, 0f, 0f,
                    r, g, b, a);
            ImGui.popStyleColor(3);
        }
        if (tooltip != null && ImGui.isItemHovered()) ImGui.setTooltip(tooltip);
        return clicked;
    }

    public static boolean iconToggleButton(String name, String id, float size, String tooltip,
                                           boolean active) {
        Tex tex = get(name);
        float tr = active ? 0.96f : 0.86f;
        float tg = active ? 0.98f : 0.88f;
        float tb = 1f;
        if (active) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.25f, 0.45f, 0.68f, 1f);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.30f, 0.52f, 0.78f, 1f);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.34f, 0.58f, 0.86f, 1f);
        } else {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.16f, 0.18f, 0.22f, 0.55f);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.22f, 0.28f, 0.36f, 0.85f);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.28f, 0.38f, 0.52f, 1f);
        }
        boolean clicked;
        if (tex == null) {
            clicked = ImGui.button((tooltip != null ? tooltip : name) + "##" + id, size + 8f, size + 4f);
        } else {
            clicked = ImGui.imageButton("##icon-" + id, tex.glId(), size, size,
                    0f, 0f, 1f, 1f,
                    0f, 0f, 0f, 0f,
                    tr, tg, tb, 1f);
        }
        ImGui.popStyleColor(3);
        if (tooltip != null && ImGui.isItemHovered()) ImGui.setTooltip(tooltip);
        return clicked;
    }

    /** Icon + optional label button (label empty → icon only). */
    public static boolean iconTextButton(String name, String label, String id, float iconSize) {
        Tex tex = get(name);
        ImGui.pushID(id);
        boolean clicked;
        if (tex == null) {
            clicked = ImGui.button(label == null || label.isBlank() ? id : label);
        } else if (label == null || label.isBlank()) {
            clicked = iconButton(name, "bare", iconSize, null);
        } else {
            float h = Math.max(ImGui.getFrameHeight(), iconSize + 6f);
            float textW = ImGui.calcTextSize(label).x;
            float w = iconSize + 10f + textW;
            clicked = ImGui.button("##btn", w, h);
            float x = ImGui.getItemRectMinX() + 5f;
            float y = ImGui.getItemRectMinY() + (h - iconSize) * 0.5f;
            ImGui.getWindowDrawList().addImage(tex.glId(), x, y, x + iconSize, y + iconSize,
                    0f, 0f, 1f, 1f, packAbgr(0.90f, 0.92f, 0.96f, 1f));
            ImGui.getWindowDrawList().addText(x + iconSize + 5f,
                    ImGui.getItemRectMinY() + (h - ImGui.getTextLineHeight()) * 0.5f,
                    packAbgr(0.90f, 0.92f, 0.96f, 1f), label);
        }
        ImGui.popID();
        return clicked;
    }

    public static void clear() {
        CACHE.clear();
        KEEP_ALIVE.clear();
    }

    private static Tex load(String name) {
        String svg = readSvg(name);
        if (svg == null) return null;
        BufferedImage img = LucideSvgRasterizer.rasterize(svg, RASTER_SIZE);
        if (img == null) return null;
        try {
            NativeImage nativeImage = toNative(img);
            DynamicTexture texture = new DynamicTexture(nativeImage);
            // Default Minecraft textures use NEAREST — at 12–16px that drops Lucide strokes.
            texture.setFilter(true, false);
            KEEP_ALIVE.put(name, texture);
            return new Tex(texture.getId(), RASTER_SIZE);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Icons defined inline instead of as a resource file. Keeps them byte-exact regardless of
     * how the .svg asset on disk got there (an on-disk copy can pick up unrelated metadata from
     * outside this codebase — e.g. an image-file provenance/content-credentials rewrite some
     * delivery pipelines apply to .svg files — which a strict parser might choke on).
     */
    private static final Map<String, String> BUILTIN_SVG = Map.of(
            "house", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" "
                    + "fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" "
                    + "stroke-linejoin=\"round\"><path d=\"M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8\"/>"
                    + "<path d=\"M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9"
                    + "a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z\"/></svg>",
            "monitor", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" "
                    + "fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" "
                    + "stroke-linejoin=\"round\"><rect width=\"20\" height=\"14\" x=\"2\" y=\"3\" rx=\"2\"/>"
                    + "<line x1=\"8\" x2=\"16\" y1=\"21\" y2=\"21\"/><line x1=\"12\" x2=\"12\" y1=\"17\" y2=\"21\"/></svg>");

    private static String readSvg(String name) {
        String builtin = BUILTIN_SVG.get(name);
        if (builtin != null) return builtin;
        ResourceLocation loc = new ResourceLocation(Spraute_engine.MODID,
                "textures/editor/icons/" + name + ".svg");
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(loc);
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {}
        // Dev fallback: classpath (before resource reload / IDE runs).
        try (InputStream in = EditorIcons.class.getResourceAsStream(
                "/assets/spraute_engine/textures/editor/icons/" + name + ".svg")) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        return null;
    }

    private static NativeImage toNative(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        NativeImage ni = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                // NativeImage.setPixelRGBA expects ABGR packing.
                ni.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return ni;
    }

    private static int packAbgr(float[] rgba) {
        if (rgba == null || rgba.length < 4) return packAbgr(1f, 1f, 1f, 1f);
        return packAbgr(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    private static int packAbgr(float r, float g, float b, float a) {
        int ri = Math.min(255, Math.max(0, Math.round(r * 255f)));
        int gi = Math.min(255, Math.max(0, Math.round(g * 255f)));
        int bi = Math.min(255, Math.max(0, Math.round(b * 255f)));
        int ai = Math.min(255, Math.max(0, Math.round(a * 255f)));
        return (ai << 24) | (bi << 16) | (gi << 8) | ri;
    }
}
