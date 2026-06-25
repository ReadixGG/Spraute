package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/**
 * breakBlockAt(x, y, z, [player]) — сломать блок с дропом (как игрок).
 */
public class BreakBlockAtFunction implements ScriptFunction {

    @Override
    public String getName() {
        return "breakBlockAt";
    }

    @Override
    public int getArgCount() {
        return -1;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[0];
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < 3 || source.getLevel() == null) return false;
        int x = ((Number) args.get(0)).intValue();
        int y = ((Number) args.get(1)).intValue();
        int z = ((Number) args.get(2)).intValue();
        ServerLevel level = source.getLevel();
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;

        Player breaker = null;
        if (args.size() >= 4) {
            Object pArg = args.get(3);
            if (pArg instanceof Player pl) breaker = pl;
            else if (pArg instanceof String name && source.getServer() != null) {
                breaker = source.getServer().getPlayerList().getPlayerByName(name);
            }
        }

        if (breaker instanceof ServerPlayer sp) {
            return level.destroyBlock(pos, true, sp);
        }
        return level.destroyBlock(pos, true);
    }
}
