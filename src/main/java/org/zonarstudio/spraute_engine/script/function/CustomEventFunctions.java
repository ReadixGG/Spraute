package org.zonarstudio.spraute_engine.script.function;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptCustomEvents;
import org.zonarstudio.spraute_engine.script.ScriptManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Script API for custom {@code on}/{@code await} events:
 * {@code registerEvent("tradeBuy")}, {@code fireEvent("tradeBuy", player, itemId, price)}.
 */
public final class CustomEventFunctions {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CustomEventFunctions() {}

    public static class RegisterEvent implements ScriptFunction {
        @Override public String getName() { return "registerEvent"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty() || args.get(0) == null) return false;
            String name = String.valueOf(args.get(0)).trim();
            if (name.isEmpty()) return false;
            if (ScriptCustomEvents.isReserved(name)) {
                LOGGER.warn("[registerEvent] '{}' is a built-in engine event and cannot be registered", name);
                return false;
            }
            boolean ok = ScriptCustomEvents.register(name);
            if (ok) {
                LOGGER.info("[registerEvent] registered custom event '{}'", name);
            }
            return ok;
        }
    }

    public static class FireEvent implements ScriptFunction {
        @Override public String getName() { return "fireEvent"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty() || args.get(0) == null) return null;
            String name = String.valueOf(args.get(0)).trim();
            if (name.isEmpty()) return null;
            if (ScriptCustomEvents.isReserved(name)) {
                LOGGER.warn("[fireEvent] '{}' is a built-in engine event — use the engine path, not fireEvent", name);
                return null;
            }
            List<Object> payload = new ArrayList<>();
            for (int i = 1; i < args.size(); i++) {
                payload.add(args.get(i));
            }
            ScriptManager.getInstance().onCustomEvent(name, payload);
            return null;
        }
    }

    public static class IsEventRegistered implements ScriptFunction {
        @Override public String getName() { return "isEventRegistered"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty() || args.get(0) == null) return false;
            return ScriptCustomEvents.isRegistered(String.valueOf(args.get(0)));
        }
    }
}
