package org.zonarstudio.spraute_engine.scene;

import java.util.ArrayList;
import java.util.List;

/** Authoritative appearance for one player: base skin + ordered full-skin overlays. */
public final class PlayerSkinState {
    private PlayerSkinState() {}

    public record Overlay(String id, String name, String texture, boolean active, int hiddenSkinLayers) {
        public Overlay {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
            texture = texture == null ? "" : texture;
            hiddenSkinLayers = hiddenSkinLayers & PlayerSkinLayers.CLOTHING;
        }

        public Overlay(String id, String name, String texture, boolean active) {
            this(id, name, texture, active, 0);
        }

        public boolean visible() {
            return active && texture != null && !texture.isBlank();
        }
    }

    public record Appearance(String baseSkin, List<Overlay> overlays) {
        public Appearance {
            baseSkin = baseSkin == null ? "" : baseSkin;
            overlays = overlays == null ? List.of() : List.copyOf(overlays);
        }

        public boolean isVanilla() {
            if (baseSkin != null && !baseSkin.isBlank()) return false;
            return overlays == null || overlays.isEmpty();
        }

        public String fingerprint() {
            StringBuilder out = new StringBuilder(baseSkin == null ? "" : baseSkin);
            for (Overlay overlay : overlays) {
                out.append('\n').append(overlay.id()).append('\t')
                        .append(overlay.active()).append('\t')
                        .append(overlay.hiddenSkinLayers()).append('\t')
                        .append(overlay.texture());
            }
            return out.toString();
        }
    }

    public static final Appearance VANILLA = new Appearance("", List.of());

    public static Appearance fromPlayerNode(SceneNode player) {
        if (player == null || player.type != SceneNodeType.PLAYER) return VANILLA;
        List<Overlay> overlays = new ArrayList<>();
        for (SceneNode child : player.children) {
            if (child.type != SceneNodeType.SKIN_OVERLAY) continue;
            overlays.add(new Overlay(child.id, child.name, child.texture, child.active,
                    child.hiddenSkinLayers));
        }
        return new Appearance(player.texture == null ? "" : player.texture, overlays);
    }
}
