package org.zonarstudio.spraute_engine.cameraroute;

public enum CameraFlightType {
    LINEAR("linear"),
    SMOOTH("smooth"),
    /** Catmull-Rom for XYZ position AND yaw/pitch — no abrupt rotation changes at waypoints. */
    SMOOTH_CAMERA("smooth_camera");

    private final String id;

    CameraFlightType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static CameraFlightType fromId(String raw) {
        if (raw == null) return SMOOTH;
        return switch (raw.trim().toLowerCase()) {
            case "linear", "line", "lines" -> LINEAR;
            case "smooth_camera", "smoothcamera", "smooth_cam", "smoothcam" -> SMOOTH_CAMERA;
            default -> SMOOTH;
        };
    }

    public CameraFlightType next() {
        return switch (this) {
            case LINEAR -> SMOOTH;
            case SMOOTH -> SMOOTH_CAMERA;
            case SMOOTH_CAMERA -> LINEAR;
        };
    }

    public String displayName() {
        return switch (this) {
            case LINEAR -> "По линиям";
            case SMOOTH -> "Плавная кривая";
            case SMOOTH_CAMERA -> "Плавная камера";
        };
    }
}
