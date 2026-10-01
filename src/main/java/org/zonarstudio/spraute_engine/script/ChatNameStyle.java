package org.zonarstudio.spraute_engine.script;

import org.zonarstudio.spraute_engine.config.SprauteConfig;

/** Name format / color from engine settings, with optional per-script override. */
public final class ChatNameStyle {
    private ChatNameStyle() {}

    public static String formatTemplate(ScriptContext context) {
        if (context != null && context.getNameFormat() != null && !context.getNameFormat().isBlank()) {
            return context.getNameFormat();
        }
        SprauteConfig config = SprauteConfig.get();
        config.normalize();
        return config.nameFormat;
    }

    public static String color(ScriptContext context) {
        if (context != null && context.getNameColor() != null && !context.getNameColor().isBlank()) {
            return context.getNameColor();
        }
        SprauteConfig config = SprauteConfig.get();
        config.normalize();
        return config.nameColor;
    }

    public static String format(String name, ScriptContext context) {
        String n = name == null ? "" : name.replace("[", "[[");
        return formatTemplate(context).replace("$Name", n);
    }
}
