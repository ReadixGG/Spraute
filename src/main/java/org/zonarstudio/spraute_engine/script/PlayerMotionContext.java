package org.zonarstudio.spraute_engine.script;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Горизонтальная скорость с клиента при нажатии keybind (для двойного прыжка без сброса спринта).
 */
public final class PlayerMotionContext {
    private static final ThreadLocal<Map<UUID, double[]>> PRESERVED = ThreadLocal.withInitial(HashMap::new);

    private PlayerMotionContext() {}

    public static void setPreservedHorizontal(UUID playerId, double vx, double vz) {
        if (playerId == null) return;
        PRESERVED.get().put(playerId, new double[]{vx, vz});
    }

    /** Возвращает [vx, vz] и удаляет запись. */
    public static double[] consumePreservedHorizontal(UUID playerId) {
        if (playerId == null) return null;
        return PRESERVED.get().remove(playerId);
    }

    public static void clear(UUID playerId) {
        if (playerId == null) return;
        PRESERVED.get().remove(playerId);
    }
}
