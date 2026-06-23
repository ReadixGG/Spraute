package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptExecutor;
import org.zonarstudio.spraute_engine.structure.StructureLoader;

import java.util.List;

/**
 * placeStructure(name, x, y, z)
 * placeStructure(name, x, y, z, mirror)           NONE | LEFT_RIGHT | FRONT_BACK
 * placeStructure(name, x, y, z, mirror, rotation)  0 | 90 | 180 | 270
 *
 * Supports: .nbt, .schem (WorldEdit/Sponge), .litematic, .schematic (legacy)
 * Files live in: spraute_engine/structures/
 */
public class PlaceStructureFunction implements ScriptFunction {

    @Override public String getName()      { return "placeStructure"; }
    @Override public int    getArgCount()  { return 4; }
    @Override public Class<?>[] getArgTypes() {
        return new Class<?>[]{String.class, Number.class, Number.class, Number.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < 4) return null;
        ServerLevel level = source.getLevel();
        if (level == null) return null;

        String name = String.valueOf(args.get(0));
        int x = ((Number) args.get(1)).intValue();
        int y = ((Number) args.get(2)).intValue();
        int z = ((Number) args.get(3)).intValue();

        Mirror mirror = Mirror.NONE;
        Rotation rotation = Rotation.NONE;
        String dimensionId = null;

        // Args after x/y/z: mirror, rotation, dimension (any order, but dimension is last)
        for (int i = 4; i < args.size(); i++) {
            Object a = args.get(i);
            if (a instanceof Number n) {
                rotation = switch (n.intValue()) {
                    case 90  -> Rotation.CLOCKWISE_90;
                    case 180 -> Rotation.CLOCKWISE_180;
                    case 270 -> Rotation.COUNTERCLOCKWISE_90;
                    default  -> Rotation.NONE;
                };
            } else if (a instanceof String s) {
                String up = s.toUpperCase();
                if (up.equals("LEFT_RIGHT") || up.equals("FRONT_BACK") || up.equals("NONE")) {
                    mirror = switch (up) {
                        case "LEFT_RIGHT" -> Mirror.LEFT_RIGHT;
                        case "FRONT_BACK" -> Mirror.FRONT_BACK;
                        default           -> Mirror.NONE;
                    };
                } else {
                    dimensionId = s; // dimension id
                }
            }
        }

        if (dimensionId != null) {
            ServerLevel dim = ScriptExecutor.resolveLevel(level.getServer(), dimensionId);
            if (dim != null) level = dim;
        }

        StructureLoader.place(level, name, x, y, z, mirror, rotation);
        return null;
    }
}
