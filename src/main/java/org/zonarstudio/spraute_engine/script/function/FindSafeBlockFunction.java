package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.entity.NpcManager;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FindSafeBlockFunction implements ScriptFunction {

    @Override
    public String getName() {
        return "findSafeBlock";
    }

    @Override
    public int getArgCount() {
        return 3;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[] { Object.class, Number.class, Number.class };
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < 3 || source.getLevel() == null) return null;
        
        Entity anchor = resolveAnchor(source, args.get(0));
        if (anchor == null) return null;
        
        int radius = ((Number) args.get(1)).intValue();
        int maxHeight = ((Number) args.get(2)).intValue();
        
        Level level = source.getLevel();
        BlockPos center = anchor.blockPosition();
        
        List<BlockPos> safePositions = new ArrayList<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -maxHeight; y <= maxHeight; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (pos.equals(center)) continue;
                    
                    BlockState state = level.getBlockState(pos);
                    BlockState stateAbove = level.getBlockState(pos.above());
                    BlockState stateBelow = level.getBlockState(pos.below());
                    
                    //? if >=1.20.1 {
                    if (!state.isSolid() &&
                        !stateAbove.isSolid() &&
                        stateBelow.isSolid()) {
                    //?} else {
                    /*if (!state.getMaterial().isSolid() &&
                        !stateAbove.getMaterial().isSolid() &&
                        stateBelow.getMaterial().isSolid()) {
                    *///?}
                        safePositions.add(pos);
                    }
                }
            }
        }
        
        if (safePositions.isEmpty()) return null;
        
        // Return a random safe position
        BlockPos chosen = safePositions.get(level.random.nextInt(safePositions.size()));
        
        List<Double> result = new ArrayList<>();
        result.add(chosen.getX() + 0.5);
        result.add((double) chosen.getY());
        result.add(chosen.getZ() + 0.5);
        
        return result;
    }

    private static Entity resolveAnchor(CommandSourceStack source, Object target) {
        if (target instanceof Entity e) return e;
        if (source.getLevel() == null) return null;

        String id = String.valueOf(target);
        if ("player".equalsIgnoreCase(id)) {
            Entity origin = source.getEntity();
            if (origin instanceof ServerPlayer sp) return sp;
            if (origin != null) return source.getLevel().getNearestPlayer(origin, 64.0);
            Vec3 pos = source.getPosition();
            return source.getLevel().getNearestPlayer(pos.x, pos.y, pos.z, 64.0, false);
        }

        UUID uuid = NpcManager.get(id);
        if (uuid != null) {
            Entity npc = source.getLevel().getEntity(uuid);
            if (npc != null) return npc;
        }

        ServerPlayer byName = source.getLevel().getServer().getPlayerList().getPlayerByName(id);
        if (byName != null) return byName;

        return null;
    }
}
