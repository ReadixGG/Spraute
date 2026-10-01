package org.zonarstudio.spraute_engine.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zonarstudio.spraute_engine.Spraute_engine;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;
import org.zonarstudio.spraute_engine.scene.PlayerSkinLayers;
import org.zonarstudio.spraute_engine.scene.PlayerSkinOps;
import org.zonarstudio.spraute_engine.scene.PlayerSkinState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Worn clothing overlay + second-layer hide, driven by the item (node = code). */
@Mod.EventBusSubscriber(modid = Spraute_engine.MODID)
public final class ClothingArmorRuntime {
    private static final Map<UUID, Integer> HIDDEN = new ConcurrentHashMap<>();

    private ClothingArmorRuntime() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (SprauteEntityCompat.level(event.getEntity()).isClientSide) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            apply(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HIDDEN.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onEquipment(LivingEquipmentChangeEvent event) {
        if (SprauteEntityCompat.level(event.getEntity()).isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        EquipmentSlot slot = event.getSlot();
        if (slot == null || slot.getType() != EquipmentSlot.Type.ARMOR) return;
        apply(player);
    }

    public static void apply(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        int wantHide = 0;
        Set<String> wornOverlays = new HashSet<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ScriptCustomArmorItem armor)) continue;
            String tex = resolveOverlayTexture(armor);
            if (tex != null && !tex.isBlank()) {
                String ovId = armor.clothingOverlayId();
                wornOverlays.add(ovId);
                PlayerSkinOps.addOverlay(player, ovId, tex);
            }
            wantHide |= armor.resolveHideMask() & PlayerSkinLayers.CLOTHING;
        }

        PlayerSkinState.Appearance appearance = PlayerSkinOps.get(uuid);
        for (PlayerSkinState.Overlay overlay : appearance.overlays()) {
            if (!overlay.id().startsWith("clothes_")) continue;
            if (!wornOverlays.contains(overlay.id()) && overlay.active()) {
                PlayerSkinOps.setOverlayActive(uuid, overlay.id(), false);
            }
        }

        int prev = HIDDEN.getOrDefault(uuid, 0);
        int show = prev & ~wantHide;
        int hide = wantHide & ~prev;
        if (show != 0) PlayerSkinLayers.setBits(uuid, show, false);
        if (hide != 0) PlayerSkinLayers.setBits(uuid, hide, true);
        if (wantHide == 0) HIDDEN.remove(uuid);
        else HIDDEN.put(uuid, wantHide);
    }

    private static String resolveOverlayTexture(ScriptCustomArmorItem armor) {
        if (armor.hasSkinOverlay()) return armor.skinOverlay();
        String id = armor.clothingOverlayId();
        if (id.startsWith("clothes_")) id = id.substring("clothes_".length());
        var def = org.zonarstudio.spraute_engine.registry.CustomBlockRegistry.ITEMS.get(id);
        if (def == null || def.skinOverlay == null) return "";
        return def.skinOverlay.trim();
    }
}
