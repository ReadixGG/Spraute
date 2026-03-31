package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.zonarstudio.spraute_engine.config.SprauteConfig;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public class SayFunction implements ScriptFunction {

    @Override
    public String getName() {
        return "say";
    }

    @Override
    public int getArgCount() {
        return 2;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[]{Object.class, String.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < 2) {
            source.sendFailure(Component.literal("Usage: say(name, message)"));
            return null;
        }

        // Resolve NPC ID to display name if possible
        String name;
        Object firstArg = args.get(0);
        if (firstArg instanceof net.minecraft.world.entity.Entity entity) {
            if (entity.hasCustomName()) {
                name = entity.getCustomName().getString();
            } else {
                name = entity.getName().getString();
            }
        } else if (firstArg instanceof String npcId) {
            java.util.UUID uuid = org.zonarstudio.spraute_engine.entity.NpcManager.get(npcId);
            if (uuid != null && source.getLevel() != null) {
                net.minecraft.world.entity.Entity npcEntity = source.getLevel().getEntity(uuid);
                if (npcEntity != null && npcEntity.hasCustomName()) {
                    name = npcEntity.getCustomName().getString();
                } else {
                    name = npcId;
                }
            } else {
                name = npcId;
            }
        } else {
            name = String.valueOf(firstArg);
        }
        String message = String.valueOf(args.get(1));

        SprauteConfig config = SprauteConfig.get();

        // Format: context overrides config
        String formatTemplate = (context != null && context.getNameFormat() != null)
                ? context.getNameFormat()
                : config.nameFormat;

        String colorStr = (context != null && context.getNameColor() != null)
                ? context.getNameColor()
                : config.nameColor;

        String formattedNameStr = formatTemplate.replace("$Name", name);

        // Apply color (supports both hex #RRGGBB and named colors like "red", "gold")
        Style nameStyle = Style.EMPTY;
        try {
            TextColor color = TextColor.parseColor(colorStr);
            if (color != null) {
                nameStyle = nameStyle.withColor(color);
            }
        } catch (Exception ignored) {}

        MutableComponent formattedName = Component.literal(formattedNameStr).withStyle(nameStyle);

        MutableComponent fullMessage = Component.empty()
                .append(formattedName)
                .append(Component.literal(" " + message));

        source.getServer().getPlayerList().broadcastSystemMessage(fullMessage, false);
        return null;
    }
}
