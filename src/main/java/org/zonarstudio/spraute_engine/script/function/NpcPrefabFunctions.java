package org.zonarstudio.spraute_engine.script.function;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptExecutor;

import java.util.List;
import java.util.Map;

public final class NpcPrefabFunctions {
    private static final Logger LOGGER = LogUtils.getLogger();

    private NpcPrefabFunctions() {}

    public static class SpawnNpcPrefab implements ScriptFunction {
        @Override
        public String getName() {
            return "spawnNpcPrefab";
        }

        @Override
        public int getArgCount() {
            return -1;
        }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[]{String.class, String.class, Number.class, Number.class, Number.class, Map.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 5) {
                LOGGER.warn("[spawnNpcPrefab] expected at least 5 args: prefabId, instanceId, x, y, z");
                return null;
            }
            String prefabId = String.valueOf(args.get(0));
            String instanceId = String.valueOf(args.get(1));
            double x = ((Number) args.get(2)).doubleValue();
            double y = ((Number) args.get(3)).doubleValue();
            double z = ((Number) args.get(4)).doubleValue();
            Map<String, Object> overrides = null;
            if (args.size() >= 6 && args.get(5) instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) m;
                overrides = cast;
            }
            return ScriptExecutor.spawnNpcPrefabForActive(prefabId, instanceId, x, y, z, overrides);
        }
    }
}
