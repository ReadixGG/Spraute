package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.zonarstudio.spraute_engine.entity.NpcManager;
import org.zonarstudio.spraute_engine.network.BoneParticlePacket;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;
import java.util.UUID;

public class ParticleFunctions {

    private static ParticleOptions getParticle(String id) {
        ResourceLocation rl = new ResourceLocation(id.contains(":") ? id : "minecraft:" + id);
        ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(rl);
        if (type instanceof ParticleOptions options) {
            return options;
        }
        return null;
    }

    private static double getDouble(Object obj) {
        if (obj instanceof Number num) return num.doubleValue();
        return 0.0;
    }

    private static float getFloat(Object obj) {
        if (obj instanceof Number num) return num.floatValue();
        return 0.0f;
    }

    private static int getInt(Object obj) {
        if (obj instanceof Number num) return num.intValue();
        return 0;
    }

    public static class Spawn implements ScriptFunction {
        @Override public String getName() { return "particleSpawn"; }
        @Override public int getArgCount() { return 9; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class}; }
        
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            ServerLevel level = source.getLevel();
            ParticleOptions opt = getParticle((String) args.get(0));
            if (opt == null) return null;
            level.sendParticles(opt, getDouble(args.get(1)), getDouble(args.get(2)), getDouble(args.get(3)),
                    getInt(args.get(4)), getDouble(args.get(5)), getDouble(args.get(6)), getDouble(args.get(7)), getDouble(args.get(8)));
            return null;
        }
    }

    public static class Line implements ScriptFunction {
        @Override public String getName() { return "particleLine"; }
        @Override public int getArgCount() { return 12; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class}; }
        
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            ServerLevel level = source.getLevel();
            ParticleOptions opt = getParticle((String) args.get(0));
            if (opt == null) return null;
            
            double x1 = getDouble(args.get(1)), y1 = getDouble(args.get(2)), z1 = getDouble(args.get(3));
            double x2 = getDouble(args.get(4)), y2 = getDouble(args.get(5)), z2 = getDouble(args.get(6));
            int count = getInt(args.get(7));
            double dx = getDouble(args.get(8)), dy = getDouble(args.get(9)), dz = getDouble(args.get(10)), speed = getDouble(args.get(11));

            for (int i = 0; i <= count; i++) {
                double t = count == 0 ? 0 : (double) i / count;
                double px = x1 + (x2 - x1) * t;
                double py = y1 + (y2 - y1) * t;
                double pz = z1 + (z2 - z1) * t;
                level.sendParticles(opt, px, py, pz, 1, dx, dy, dz, speed);
            }
            return null;
        }
    }

    public static class Circle implements ScriptFunction {
        @Override public String getName() { return "particleCircle"; }
        @Override public int getArgCount() { return 10; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class}; }
        
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            ServerLevel level = source.getLevel();
            ParticleOptions opt = getParticle((String) args.get(0));
            if (opt == null) return null;

            double cx = getDouble(args.get(1)), cy = getDouble(args.get(2)), cz = getDouble(args.get(3));
            double radius = getDouble(args.get(4));
            int count = getInt(args.get(5));
            double dx = getDouble(args.get(6)), dy = getDouble(args.get(7)), dz = getDouble(args.get(8)), speed = getDouble(args.get(9));

            for (int i = 0; i < count; i++) {
                double angle = 2 * Math.PI * i / count;
                double px = cx + radius * Math.cos(angle);
                double pz = cz + radius * Math.sin(angle);
                level.sendParticles(opt, px, cy, pz, 1, dx, dy, dz, speed);
            }
            return null;
        }
    }

    public static class Spiral implements ScriptFunction {
        @Override public String getName() { return "particleSpiral"; }
        @Override public int getArgCount() { return 11; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class}; }
        
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            ServerLevel level = source.getLevel();
            ParticleOptions opt = getParticle((String) args.get(0));
            if (opt == null) return null;

            double cx = getDouble(args.get(1)), cy = getDouble(args.get(2)), cz = getDouble(args.get(3));
            double radius = getDouble(args.get(4));
            double height = getDouble(args.get(5));
            int count = getInt(args.get(6));
            double dx = getDouble(args.get(7)), dy = getDouble(args.get(8)), dz = getDouble(args.get(9)), speed = getDouble(args.get(10));

            for (int i = 0; i < count; i++) {
                double t = count == 1 ? 0 : (double) i / (count - 1);
                double angle = 4 * Math.PI * t; // 2 full turns
                double px = cx + radius * Math.cos(angle);
                double py = cy + height * t;
                double pz = cz + radius * Math.sin(angle);
                level.sendParticles(opt, px, py, pz, 1, dx, dy, dz, speed);
            }
            return null;
        }
    }

    public static class StartBone implements ScriptFunction {
        @Override public String getName() { return "particleStartBone"; }
        @Override public int getArgCount() { return 9; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class, String.class, String.class, String.class, Object.class, Object.class, Object.class, Object.class, Object.class}; }
        
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            String taskId = (String) args.get(0);
            String npcNameOrUuid = (String) args.get(1);
            String boneName = (String) args.get(2);
            String particleType = (String) args.get(3);
            int count = getInt(args.get(4));
            float dx = getFloat(args.get(5));
            float dy = getFloat(args.get(6));
            float dz = getFloat(args.get(7));
            float speed = getFloat(args.get(8));

            Entity entity = null;
            try {
                UUID uuid = UUID.fromString(npcNameOrUuid);
                entity = source.getLevel().getEntity(uuid);
            } catch (Exception e) {
                entity = NpcManager.getEntity(npcNameOrUuid, source.getLevel());
            }

            if (entity != null) {
                BoneParticlePacket pkt = new BoneParticlePacket(0, taskId, entity.getId(), boneName, particleType, count, dx, dy, dz, speed);
                ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), pkt);
            }
            return taskId;
        }
    }

    public static class StopBone implements ScriptFunction {
        @Override public String getName() { return "particleStopBone"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{String.class}; }
        
        @Override public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            String taskId = (String) args.get(0);
            BoneParticlePacket pkt = new BoneParticlePacket(1, taskId, 0, "", "", 0, 0, 0, 0, 0);
            ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), pkt);
            return null;
        }
    }
}
