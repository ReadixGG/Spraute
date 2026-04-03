package org.zonarstudio.spraute_engine.script.util;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ForgeReflection {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    private static final List<String> BLACKLISTED_PACKAGES = List.of(
        "java.lang.System",
        "java.lang.Runtime",
        "java.lang.Thread",
        "java.lang.Process",
        "java.lang.ProcessBuilder",
        "java.net.",
        "java.lang.reflect.",
        "javax.net.",
        "sun.",
        "com.sun."
    );

    public static boolean isAllowedClass(Class<?> clazz) {
        if (clazz == null) return false;
        String name = clazz.getName();
        for (String blacklisted : BLACKLISTED_PACKAGES) {
            if (name.equals(blacklisted) || name.startsWith(blacklisted)) {
                return false;
            }
        }
        return true;
    }

    public static Object invokeMethod(Object obj, String methodName, List<Object> args) {
        if (obj == null) return null;
        Class<?> clazz = obj instanceof Class<?> c ? c : obj.getClass();
        
        if (!isAllowedClass(clazz)) {
            LOGGER.warn("[Script Security] Attempted to access blacklisted class: {}", clazz.getName());
            return null;
        }

        String cacheKey = clazz.getName() + "#" + methodName + "(" + args.size() + ")";
        Method bestMethod = METHOD_CACHE.get(cacheKey);
        Object[] bestCoercedArgs = null;

        if (bestMethod != null) {
            bestCoercedArgs = coerceArgs(args, bestMethod.getParameterTypes());
            if (bestCoercedArgs == null) {
                bestMethod = null; // Coercion failed, fallback to search
            }
        }

        if (bestMethod == null) {
            for (Method method : clazz.getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == args.size()) {
                    Object[] coercedArgs = coerceArgs(args, method.getParameterTypes());
                    if (coercedArgs != null) {
                        bestMethod = method;
                        bestCoercedArgs = coercedArgs;
                        METHOD_CACHE.put(cacheKey, method);
                        break;
                    }
                }
            }
        }
        
        if (bestMethod != null) {
            try {
                bestMethod.setAccessible(true);
                return bestMethod.invoke(obj instanceof Class<?> ? null : obj, bestCoercedArgs);
            } catch (Exception e) {
                LOGGER.warn("[Script Reflection] Invocation failed on method '{}' of class '{}': {}", methodName, clazz.getName(), e.getMessage());
            }
        } else {
            LOGGER.warn("[Script Reflection] Method '{}' with {} args not found or failed argument coercion on class '{}'", methodName, args.size(), clazz.getName());
        }
        return null;
    }

    public static Object getField(Object obj, String fieldName) {
        if (obj == null) return null;
        Class<?> clazz = obj instanceof Class<?> c ? c : obj.getClass();
        
        if (!isAllowedClass(clazz)) {
            LOGGER.warn("[Script Security] Attempted to access blacklisted class: {}", clazz.getName());
            return null;
        }

        try {
            Field field = clazz.getField(fieldName);
            field.setAccessible(true);
            return field.get(obj instanceof Class<?> ? null : obj);
        } catch (Exception e) {
            LOGGER.warn("[Script Reflection] Field '{}' not found or inaccessible on class '{}'", fieldName, clazz.getName());
            return null;
        }
    }

    public static void setField(Object obj, String fieldName, Object value) {
        if (obj == null) return;
        Class<?> clazz = obj instanceof Class<?> c ? c : obj.getClass();
        
        if (!isAllowedClass(clazz)) {
            LOGGER.warn("[Script Security] Attempted to access blacklisted class: {}", clazz.getName());
            return;
        }

        try {
            Field field = clazz.getField(fieldName);
            field.setAccessible(true);
            Object coercedValue = coerceSingle(value, field.getType());
            if (coercedValue == null && value != null && field.getType().isPrimitive()) {
                LOGGER.warn("[Script Reflection] Cannot coerce to primitive field '{}' on class '{}'", fieldName, clazz.getName());
                return;
            }
            field.set(obj instanceof Class<?> ? null : obj, coercedValue);
        } catch (Exception e) {
            LOGGER.warn("[Script Reflection] Failed to set field '{}' on class '{}': {}", fieldName, clazz.getName(), e.getMessage());
        }
    }

    private static Object[] coerceArgs(List<Object> args, Class<?>[] parameterTypes) {
        Object[] coerced = new Object[args.size()];
        for (int i = 0; i < args.size(); i++) {
            Object val = coerceSingle(args.get(i), parameterTypes[i]);
            if (val == null && args.get(i) != null && parameterTypes[i].isPrimitive()) {
                return null; // Cannot coerce to primitive
            }
            coerced[i] = val;
        }
        return coerced;
    }

    private static Object coerceSingle(Object obj, Class<?> targetType) {
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

        // Add string representation if target is String
        if (targetType == String.class) {
            return String.valueOf(obj);
        }

        return obj;
    }
}