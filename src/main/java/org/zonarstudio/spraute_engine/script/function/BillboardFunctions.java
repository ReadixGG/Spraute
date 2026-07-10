package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;
import org.zonarstudio.spraute_engine.entity.BillboardManager;
import org.zonarstudio.spraute_engine.entity.SprauteBillboardEntity;
import org.zonarstudio.spraute_engine.script.EntityScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public final class BillboardFunctions {

    private BillboardFunctions() {}

    public static ServerLevel resolveLevel(CommandSourceStack source, String dimensionId) {
        ServerLevel level = source.getLevel();
        if (level == null || dimensionId == null || dimensionId.isBlank()) return level;
        String dim = dimensionId.contains(":") ? dimensionId : "minecraft:" + dimensionId;
        //? if >=1.20.1 {
        ResourceKey<Level> resKey = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                new ResourceLocation(dim));
        //?} else {
        /*ResourceKey<Level> resKey = ResourceKey.create(
                net.minecraft.core.Registry.DIMENSION_REGISTRY,
                new ResourceLocation(dim));
        *///?}
        ServerLevel dimLevel = level.getServer().getLevel(resKey);
        return dimLevel != null ? dimLevel : level;
    }

    public static SprauteBillboardEntity spawn(
            ServerLevel level, String texture, double x, double y, double z,
            float width, float height, boolean seeThrough) {
        if (level == null) return null;
        SprauteBillboardEntity billboard = new SprauteBillboardEntity(level, x, y, z, texture, width, height, seeThrough);
        level.addFreshEntity(billboard);
        return billboard;
    }

    private static SprauteBillboardEntity resolveBillboard(Object ref, CommandSourceStack source) {
        Entity e = EntityScriptUtil.resolveEntity(ref, source);
        return e instanceof SprauteBillboardEntity bb ? bb : null;
    }

    /** spawnBillboardNamed(id, texture, x, y, z, w, h, seeThrough, [dimension]) */
    public static class SpawnBillboardNamed implements ScriptFunction {
        @Override public String getName() { return "spawnBillboardNamed"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 8) return null;
            String id = String.valueOf(args.get(0));
            String texture = String.valueOf(args.get(1));
            double x = ((Number) args.get(2)).doubleValue();
            double y = ((Number) args.get(3)).doubleValue();
            double z = ((Number) args.get(4)).doubleValue();
            float w = ((Number) args.get(5)).floatValue();
            float h = ((Number) args.get(6)).floatValue();
            boolean seeThrough = args.get(7) instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(args.get(7)));
            String dimensionId = args.size() >= 9 ? String.valueOf(args.get(8)) : null;
            ServerLevel level = resolveLevel(source, dimensionId);
            SprauteBillboardEntity bb = spawn(level, texture, x, y, z, w, h, seeThrough);
            if (bb == null) return null;
            BillboardManager.track(id, bb.getUUID());
            return id;
        }
    }

    /** spawnBillboardNear(id, player, texture, ox, oy, oz, w, h, seeThrough) */
    public static class SpawnBillboardNear implements ScriptFunction {
        @Override public String getName() { return "spawnBillboardNear"; }
        @Override public int getArgCount() { return 9; }
        @Override public Class<?>[] getArgTypes() {
            return new Class<?>[]{String.class, Object.class, String.class, Number.class, Number.class, Number.class, Number.class, Number.class, Boolean.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 9) return null;
            String id = String.valueOf(args.get(0));
            Entity anchor = EntityScriptUtil.resolveEntity(args.get(1), source);
            if (anchor == null) return null;
            String texture = String.valueOf(args.get(2));
            double ox = ((Number) args.get(3)).doubleValue();
            double oy = ((Number) args.get(4)).doubleValue();
            double oz = ((Number) args.get(5)).doubleValue();
            float w = ((Number) args.get(6)).floatValue();
            float h = ((Number) args.get(7)).floatValue();
            boolean seeThrough = args.get(8) instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(args.get(8)));
            if (!(SprauteEntityCompat.level(anchor) instanceof ServerLevel serverLevel)) return null;
            ServerLevel level = serverLevel;
            SprauteBillboardEntity bb = spawn(level, texture, anchor.getX() + ox, anchor.getY() + oy, anchor.getZ() + oz, w, h, seeThrough);
            if (bb == null) return null;
            BillboardManager.track(id, bb.getUUID());
            return id;
        }
    }

    /** getBillboard(id) — living billboard entity or null */
    public static class GetBillboard implements ScriptFunction {
        @Override public String getName() { return "getBillboard"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty() || source.getLevel() == null) return null;
            String id = String.valueOf(args.get(0));
            Entity e = BillboardManager.getEntity(id, source.getLevel());
            return e != null && e.isAlive() ? e : null;
        }
    }

    /** removeBillboard(id) */
    public static class RemoveBillboard implements ScriptFunction {
        @Override public String getName() { return "removeBillboard"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty() || source.getLevel() == null) return null;
            String id = String.valueOf(args.get(0));
            Entity e = BillboardManager.getEntity(id, source.getLevel());
            if (e != null) e.remove(Entity.RemovalReason.DISCARDED);
            BillboardManager.remove(id);
            return null;
        }
    }

    /** setBillboardSize(ref, width, height) — ref: id or UUID */
    public static class SetBillboardSize implements ScriptFunction {
        @Override public String getName() { return "setBillboardSize"; }
        @Override public int getArgCount() { return 3; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Number.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return null;
            SprauteBillboardEntity bb = resolveBillboard(args.get(0), source);
            if (bb == null) return null;
            bb.setBillboardWidth(((Number) args.get(1)).floatValue());
            bb.setBillboardHeight(((Number) args.get(2)).floatValue());
            return null;
        }
    }

    /** setBillboardSeeThrough(ref, true/false) */
    public static class SetBillboardSeeThrough implements ScriptFunction {
        @Override public String getName() { return "setBillboardSeeThrough"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Boolean.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            SprauteBillboardEntity bb = resolveBillboard(args.get(0), source);
            if (bb == null) return null;
            boolean seeThrough = args.get(1) instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(args.get(1)));
            bb.setSeeThrough(seeThrough);
            return null;
        }
    }

    /** teleportBillboard(ref, x, y, z) */
    public static class TeleportBillboard implements ScriptFunction {
        @Override public String getName() { return "teleportBillboard"; }
        @Override public int getArgCount() { return 4; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Number.class, Number.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 4) return null;
            SprauteBillboardEntity bb = resolveBillboard(args.get(0), source);
            if (bb == null) return null;
            bb.teleportTo(
                    ((Number) args.get(1)).doubleValue(),
                    ((Number) args.get(2)).doubleValue(),
                    ((Number) args.get(3)).doubleValue());
            return null;
        }
    }
}
