package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.ChunkPos;
import org.zonarstudio.spraute_engine.core.WorldSnapshotManager;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.ArrayList;
import java.util.List;

public class SnapshotFunctions {

    public static class SaveSnapshot implements ScriptFunction {
        @Override
        public String getName() {
            return "save_snapshot";
        }

        @Override
        public int getArgCount() { return 1; }

        @Override
        public Class<?>[] getArgTypes() { return new Class<?>[]{ String.class }; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return null;
            String name = String.valueOf(args.get(0));
            
            int radius = 0;
            if (args.size() > 1 && args.get(1) instanceof Number n) {
                radius = n.intValue();
            }

            net.minecraft.server.level.ServerLevel level = source.getLevel();
            if (level == null) return null;

            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(source.getPosition());
            ChunkPos center = new ChunkPos(pos);
            
            List<ChunkPos> chunksToSave = new ArrayList<>();
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    chunksToSave.add(new ChunkPos(center.x + x, center.z + z));
                }
            }

            WorldSnapshotManager.saveSnapshot(level, name, chunksToSave);
            return null;
        }
    }

    public static class LoadSnapshot implements ScriptFunction {
        @Override
        public String getName() {
            return "load_snapshot";
        }

        @Override
        public int getArgCount() { return 1; }

        @Override
        public Class<?>[] getArgTypes() { return new Class<?>[]{ String.class }; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return null;
            String name = String.valueOf(args.get(0));

            net.minecraft.server.level.ServerLevel level = source.getLevel();
            if (level == null) return null;

            WorldSnapshotManager.loadSnapshot(level, name);
            return null;
        }
    }
}
