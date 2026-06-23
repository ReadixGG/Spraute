package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.structure.StructureLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * saveStructure(name, x1, y1, z1, x2, y2, z2)
 *
 * Saves the region [min..max] to spraute_engine/structures/<name>.nbt
 * Returns true on success, null on failure.
 */
public class SaveStructureFunction implements ScriptFunction {

    private static final Logger LOGGER = LoggerFactory.getLogger("Spraute SaveStructure");

    @Override public String getName()     { return "saveStructure"; }
    @Override public int    getArgCount() { return 7; }
    @Override public Class<?>[] getArgTypes() {
        return new Class<?>[]{String.class,
                Number.class, Number.class, Number.class,
                Number.class, Number.class, Number.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < 7) return null;
        ServerLevel level = source.getLevel();
        if (level == null) return null;

        String name = String.valueOf(args.get(0));
        int x1 = ((Number) args.get(1)).intValue();
        int y1 = ((Number) args.get(2)).intValue();
        int z1 = ((Number) args.get(3)).intValue();
        int x2 = ((Number) args.get(4)).intValue();
        int y2 = ((Number) args.get(5)).intValue();
        int z2 = ((Number) args.get(6)).intValue();

        // Normalise corners
        BlockPos corner = new BlockPos(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2));
        Vec3i size = new Vec3i(
                Math.abs(x2 - x1) + 1,
                Math.abs(y2 - y1) + 1,
                Math.abs(z2 - z1) + 1);

        try {
            StructureTemplate template = new StructureTemplate();
            template.fillFromWorld(level, corner, size, false, Blocks.STRUCTURE_VOID);

            // Ensure directory exists
            File dir = StructureLoader.STRUCTURES_DIR.toFile();
            dir.mkdirs();

            String fileName = name.endsWith(".nbt") ? name : name + ".nbt";
            File outFile = new File(dir, fileName);

            CompoundTag nbt = template.save(new CompoundTag());
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                NbtIo.writeCompressed(nbt, fos);
            }

            LOGGER.info("[Script] Saved structure '{}' ({}x{}x{}) to {}",
                    name, size.getX(), size.getY(), size.getZ(), outFile.getAbsolutePath());
            return true;

        } catch (Exception e) {
            LOGGER.error("[Script] Failed to save structure '{}': {}", name, e.getMessage(), e);
            return null;
        }
    }
}
