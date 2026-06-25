package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.zonarstudio.spraute_engine.script.ChestLootManager;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public class ChestLootFunctions {

    /** setChestLoot(x, y, z, slot, item_id, [count]) — лут ванильного сундука/бочки по слотам (мир, при первом открытии) */
    public static class SetChestLoot implements ScriptFunction {
        @Override public String getName() { return "setChestLoot"; }
        @Override public int getArgCount() { return 5; }
        @Override public Class<?>[] getArgTypes() {
            return new Class<?>[]{Number.class, Number.class, Number.class, Number.class, Object.class, Number.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 5 || !(source.getLevel() instanceof ServerLevel)) return null;
            ServerLevel level = (ServerLevel) source.getLevel();
            int x = ((Number) args.get(0)).intValue();
            int y = ((Number) args.get(1)).intValue();
            int z = ((Number) args.get(2)).intValue();
            int slot = ((Number) args.get(3)).intValue();
            String itemId = String.valueOf(args.get(4));
            int count = args.size() > 5 && args.get(5) instanceof Number n ? n.intValue() : 1;
            ChestLootManager.setSlot(level, level.dimension().location().toString(), new BlockPos(x, y, z), slot, itemId, count);
            return null;
        }
    }

    /** clearChestLoot(x, y, z) — убрать настроенный лут с позиции */
    public static class ClearChestLoot implements ScriptFunction {
        @Override public String getName() { return "clearChestLoot"; }
        @Override public int getArgCount() { return 3; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Number.class, Number.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3 || !(source.getLevel() instanceof ServerLevel)) return null;
            ServerLevel level = (ServerLevel) source.getLevel();
            ChestLootManager.clear(level, level.dimension().location().toString(),
                    new BlockPos(((Number) args.get(0)).intValue(), ((Number) args.get(1)).intValue(), ((Number) args.get(2)).intValue()));
            return null;
        }
    }

    /** setBlockSlot(x, y, z, slot, item_id, [count]) — предмет в слот визуального блока (CustomGeoBlock) */
    public static class SetBlockSlot implements ScriptFunction {
        @Override public String getName() { return "setBlockSlot"; }
        @Override public int getArgCount() { return 5; }
        @Override public Class<?>[] getArgTypes() {
            return new Class<?>[]{Number.class, Number.class, Number.class, Number.class, Object.class, Number.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 5 || !(source.getLevel() instanceof ServerLevel)) return null;
            ServerLevel level = (ServerLevel) source.getLevel();
            int x = ((Number) args.get(0)).intValue();
            int y = ((Number) args.get(1)).intValue();
            int z = ((Number) args.get(2)).intValue();
            int slot = ((Number) args.get(3)).intValue();
            String itemId = String.valueOf(args.get(4));
            int count = args.size() > 5 && args.get(5) instanceof Number n ? n.intValue() : 1;
            ChestLootManager.setBlockSlot(level, new BlockPos(x, y, z), slot, itemId, count);
            return null;
        }
    }
}
