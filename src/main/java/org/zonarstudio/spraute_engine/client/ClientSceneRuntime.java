package org.zonarstudio.spraute_engine.client;

import org.zonarstudio.spraute_engine.scene.PlayerCameraPose;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Client mirror of {@code SceneRuntime} active scene ids.
 * On dedicated servers the real map lives only in the server JVM.
 */
public final class ClientSceneRuntime {
    private static final Set<String> ACTIVE = new LinkedHashSet<>();

    private ClientSceneRuntime() {}

    public static void setActive(List<String> sceneIds) {
        ACTIVE.clear();
        if (sceneIds != null) {
            for (String id : sceneIds) {
                if (id != null && !id.isBlank()) ACTIVE.add(id);
            }
        }
        // Belt-and-suspenders: server already pushes VANILLA on unload, but if the
        // client ever sees an empty active list, rest pose must not linger.
        if (ACTIVE.isEmpty()) {
            CameraHandler.setRestPose(PlayerCameraPose.VANILLA);
            CameraOcclusionClient.clearAll();
        }
        LoadScreenOverlay.onActiveScenesChanged(ACTIVE);
    }

    public static void clear() {
        ACTIVE.clear();
        CameraHandler.setRestPose(PlayerCameraPose.VANILLA);
        CameraOcclusionClient.clearAll();
        LoadScreenOverlay.cancel();
    }

    public static boolean isActive(String sceneId) {
        return sceneId != null && ACTIVE.contains(sceneId);
    }

    public static Set<String> listActive() {
        return Collections.unmodifiableSet(ACTIVE);
    }
}
