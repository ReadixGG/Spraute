package org.sprautestudio.spraute_engine.runtime;

import net.minecraft.resources.ResourceLocation;
import org.sprautestudio.spraute_engine.lang.Specs;

/**
 * Naming of project pictures. A picture "Лицо Алисы" of project "Мой сюжет" becomes the texture
 * {@code spraute_engine:textures/gui/chat/spraute/moy_syuzhet/litso_alisy.png}: the client registers
 * it as a dynamic texture and Spraute finds it by that path (speech icons use
 * {@code textures/gui/chat/<icon>.png}, so the icon string is {@code spraute/<project>/<picture>}).
 */
public final class Images {
    private Images() {}

    private static final String[] CYRILLIC = {
            "a", "b", "v", "g", "d", "e", "zh", "z", "i", "y", "k", "l", "m", "n", "o", "p", "r", "s", "t", "u", "f", "h", "c",
            "ch", "sh", "sch", "", "y", "", "e", "yu", "ya"};

    /** Lowercase [a-z0-9_] form of a name, stable on every side. */
    public static String slug(String name) {
        StringBuilder sb = new StringBuilder();
        for (char ch : name.trim().toLowerCase().toCharArray()) {
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_') sb.append(ch);
            else if (ch >= 'а' && ch <= 'я') sb.append(CYRILLIC[ch - 'а']);
            else if (ch == 'ё') sb.append("e");
            else if (ch == ' ' || ch == '-' || ch == '.') sb.append('_');
            else sb.append('x').append(Integer.toHexString(ch));
        }
        return sb.length() == 0 ? "image" : sb.toString();
    }

    /** Icon string for Spraute's speech window. */
    public static String iconPath(String project, String picture) {
        return "spraute/" + slug(project) + "/" + slug(picture);
    }

    public static ResourceLocation texture(String project, String picture) {
        return new ResourceLocation(org.sprautestudio.spraute_engine.SprauteEngine.MODID, "textures/gui/chat/" + iconPath(project, picture) + ".png");
    }

    /** Built-in Spraute icons are used as is; anything else is a picture of the project. */
    public static String speechIcon(String project, String value) {
        return Specs.SPEECH_ICONS.contains(value) ? value : iconPath(project, value);
    }

    /** Texture for a dialog portrait: a project picture or a built-in chat icon. */
    public static ResourceLocation portrait(String project, String value) {
        if (Specs.SPEECH_ICONS.contains(value)) return new ResourceLocation(org.sprautestudio.spraute_engine.SprauteEngine.MODID, "textures/gui/chat/" + value + ".png");
        return texture(project, value);
    }
}
