package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public class SetBlockFunction implements ScriptFunction {

    @Override
    public String getName() {
        return "setBlock";
    }

    @Override
    public int getArgCount() {
        return 4;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[]{Number.class, Number.class, Number.class, Object.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < 4 || source.getLevel() == null) return null;
        
        int x = ((Number) args.get(0)).intValue();
        int y = ((Number) args.get(1)).intValue();
        int z = ((Number) args.get(2)).intValue();
        String blockId = String.valueOf(args.get(3));
        
        ResourceLocation rl = blockId.contains(":") ? new ResourceLocation(blockId) : new ResourceLocation("minecraft", blockId);
        Block block = ForgeRegistries.BLOCKS.getValue(rl);
        if (block != null && block != net.minecraft.world.level.block.Blocks.AIR || blockId.endsWith(":air") || blockId.equals("air")) {
            BlockPos pos = new BlockPos(x, y, z);
            source.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        } else {
            org.slf4j.LoggerFactory.getLogger("Spraute Engine").warn("[Script] Unknown block: {}", blockId);
        }

        return null;
    }
}
