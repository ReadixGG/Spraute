package org.zonarstudio.spraute_engine.client;

import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import org.zonarstudio.spraute_engine.script.HudHideParts;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side hidden vanilla HUD parts. Independent of cinematic {@link CameraHandler}. */
public final class HudHideClient {
    private static final Set<String> HIDDEN = ConcurrentHashMap.newKeySet();

    private HudHideClient() {}

    public static void replace(Collection<String> parts) {
        HIDDEN.clear();
        if (parts != null) HIDDEN.addAll(parts);
    }

    public static void clear() {
        HIDDEN.clear();
    }

    public static boolean hideHand() {
        return HIDDEN.contains(HudHideParts.HAND) || HIDDEN.contains(HudHideParts.VANILLA);
    }

    public static boolean isHidden(NamedGuiOverlay overlay) {
        if (overlay == null || overlay.id() == null) return false;
        if (HIDDEN.contains(HudHideParts.VANILLA) && "minecraft".equals(overlay.id().getNamespace())) {
            return true;
        }
        String path = overlay.id().getPath();
        return HIDDEN.contains(path) || HIDDEN.contains(overlay.id().toString());
    }
}
