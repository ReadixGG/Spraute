package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.ChatNameStyle;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/** Read chat name format / color from engine settings (editor → config.json). */
public final class ChatNameFunctions {
    private ChatNameFunctions() {}

    public static final class GetFormat implements ScriptFunction {
        @Override
        public String getName() {
            return "getChatNameFormat";
        }

        @Override
        public int getArgCount() {
            return 0;
        }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[]{};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            return ChatNameStyle.formatTemplate(context);
        }
    }

    public static final class GetColor implements ScriptFunction {
        @Override
        public String getName() {
            return "getChatNameColor";
        }

        @Override
        public int getArgCount() {
            return 0;
        }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[]{};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            return ChatNameStyle.color(context);
        }
    }

    public static final class FormatName implements ScriptFunction {
        @Override
        public String getName() {
            return "formatChatName";
        }

        @Override
        public int getArgCount() {
            return 1;
        }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[]{Object.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            String name = args.isEmpty() || args.get(0) == null ? "" : String.valueOf(args.get(0));
            return ChatNameStyle.format(name, context);
        }
    }
}
