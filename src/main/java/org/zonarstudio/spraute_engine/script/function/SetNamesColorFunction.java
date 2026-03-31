package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/**
 * Function: set_names_color(color)
 *
 * Sets the NPC name bracket color for all subsequent say() calls in this script.
 * Does NOT change the global config.
 *
 * Supported formats:
 *   - Hex:   set_names_color("#FF5500")
 *   - Named: set_names_color("gold"), set_names_color("red"), etc.
 *   - Reset: set_names_color("reset") -> falls back to config default
 */
public class SetNamesColorFunction implements ScriptFunction {

    @Override
    public String getName() {
        return "set_names_color";
    }

    @Override
    public int getArgCount() {
        return 1;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[]{String.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.isEmpty() || context == null) return null;

        String color = String.valueOf(args.get(0));

        if (color.equalsIgnoreCase("reset")) {
            context.setNameColor(null);
        } else {
            context.setNameColor(color);
        }

        return null;
    }
}
