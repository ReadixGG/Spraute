package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public class JavaFunctions {

    public static class JavaClassFunction implements ScriptFunction {
        @Override
        public String getName() {
            return "java_class";
        }

        @Override
        public int getArgCount() {
            return 1;
        }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[] { String.class };
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty() || !(args.get(0) instanceof String className)) return null;
            try {
                Class<?> clazz = Class.forName(className);
                if (!org.zonarstudio.spraute_engine.script.util.ForgeReflection.isAllowedClass(clazz)) {
                    com.mojang.logging.LogUtils.getLogger().warn("[Script Security] Attempted to access blacklisted class: {}", className);
                    return null;
                }
                return clazz;
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    }

    public static class JavaNewFunction implements ScriptFunction {
        @Override
        public String getName() {
            return "java_new";
        }

        @Override
        public int getArgCount() {
            return -1; // varargs
        }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[0];
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty() || !(args.get(0) instanceof Class<?> clazz)) return null;

            if (!org.zonarstudio.spraute_engine.script.util.ForgeReflection.isAllowedClass(clazz)) {
                com.mojang.logging.LogUtils.getLogger().warn("[Script Security] Attempted to instantiate blacklisted class: {}", clazz.getName());
                return null;
            }

            List<Object> constructorArgs = args.subList(1, args.size());
            for (java.lang.reflect.Constructor<?> constructor : clazz.getConstructors()) {
                if (constructor.getParameterCount() == constructorArgs.size()) {
                    try {
                        Object[] coercedArgs = coerceArgs(constructorArgs, constructor.getParameterTypes());
                        if (coercedArgs != null) {
                            constructor.setAccessible(true);
                            return constructor.newInstance(coercedArgs);
                        }
                    } catch (Exception e) {
                        // ignore and try next
                    }
                }
            }
            return null;
        }

        private Object[] coerceArgs(List<Object> args, Class<?>[] parameterTypes) {
            Object[] coerced = new Object[args.size()];
            for (int i = 0; i < args.size(); i++) {
                Object val = coerceSingle(args.get(i), parameterTypes[i]);
                if (val == null && args.get(i) != null && parameterTypes[i].isPrimitive()) {
                    return null;
                }
                coerced[i] = val;
            }
            return coerced;
        }

        private Object coerceSingle(Object obj, Class<?> targetType) {
            if (obj == null) return null;
            if (targetType.isAssignableFrom(obj.getClass())) return obj;

            if (obj instanceof Number num) {
                if (targetType == int.class || targetType == Integer.class) return num.intValue();
                if (targetType == float.class || targetType == Float.class) return num.floatValue();
                if (targetType == double.class || targetType == Double.class) return num.doubleValue();
                if (targetType == long.class || targetType == Long.class) return num.longValue();
                if (targetType == short.class || targetType == Short.class) return num.shortValue();
                if (targetType == byte.class || targetType == Byte.class) return num.byteValue();
            }

            if (obj instanceof String str) {
                if (targetType == java.util.UUID.class) {
                    try { return java.util.UUID.fromString(str); } catch (Exception e) { return null; }
                }
                if (targetType == int.class || targetType == Integer.class) {
                    try { return Integer.parseInt(str); } catch (Exception e) { return null; }
                }
                if (targetType == double.class || targetType == Double.class) {
                    try { return Double.parseDouble(str); } catch (Exception e) { return null; }
                }
                if (targetType == float.class || targetType == Float.class) {
                    try { return Float.parseFloat(str); } catch (Exception e) { return null; }
                }
                if (targetType == boolean.class || targetType == Boolean.class) {
                    return Boolean.parseBoolean(str);
                }
            }

            if (targetType == String.class) {
                return String.valueOf(obj);
            }

            return obj;
        }
    }

    public static class SendPacketFunction implements ScriptFunction {
        @Override
        public String getName() {
            return "sendPacket";
        }

        @Override
        public int getArgCount() {
            return 2;
        }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[] { Object.class, Object.class };
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            Object playerObj = args.get(0);
            Object packetObj = args.get(1);

            net.minecraft.world.entity.Entity entity = null;
            if (playerObj instanceof net.minecraft.world.entity.Entity e) {
                entity = e;
            } else if (playerObj instanceof String id) {
                if (source.getLevel() != null) {
                    entity = source.getLevel().getServer().getPlayerList().getPlayerByName(id);
                }
            }

            if (entity instanceof net.minecraft.server.level.ServerPlayer player && packetObj instanceof net.minecraft.network.protocol.Packet<?> packet) {
                player.connection.send(packet);
                return true;
            }
            return false;
        }
    }
}
