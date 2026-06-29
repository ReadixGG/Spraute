package org.zonarstudio.spraute_engine.cameraroute;

public enum CameraRouteEndMode {
    RETURN_TO_PLAYER("return"),
    STAY_AT_LAST("stay");

    private final String id;

    CameraRouteEndMode(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static CameraRouteEndMode fromId(String id) {
        if (id == null) return RETURN_TO_PLAYER;
        return switch (id.trim().toLowerCase()) {
            case "stay", "stop", "hold", "last", "остаться", "остановиться" -> STAY_AT_LAST;
            default -> RETURN_TO_PLAYER;
        };
    }

    public static CameraRouteEndMode parse(Object value) {
        if (value == null) return RETURN_TO_PLAYER;
        if (value instanceof Boolean b) return b ? RETURN_TO_PLAYER : STAY_AT_LAST;
        if (value instanceof Number n) return n.intValue() != 0 ? RETURN_TO_PLAYER : STAY_AT_LAST;
        return fromId(String.valueOf(value));
    }
}
