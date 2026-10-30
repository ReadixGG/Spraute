package org.sprautestudio.st_addon_spraute.engine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.runtime.Images;

import java.util.ArrayList;
import java.util.List;

/**
 * Notifications from a story: lines sliding in at the top left, one under another; the icon is a project
 * picture, a game texture, or an item.
 */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID, value = Dist.CLIENT)
public final class Notices {
    private Notices() {
    }

    private static final int LIFE = 100, SLIDE = 10, H = 22, W = 180;

    private static final class Notice {
        final String text;
        final ResourceLocation icon;
        final ItemStack item;
        int age;

        Notice(String text, ResourceLocation icon, ItemStack item) {
            this.text = text;
            this.icon = icon;
            this.item = item;
        }
    }

    private static final List<Notice> SHOWN = new ArrayList<>();

    public static void show(String text, String icon, String item, String project, boolean sound) {
        ItemStack stack = ItemStack.EMPTY;
        ResourceLocation tex = null;
        if (!item.isEmpty()) {
            Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(item.contains(":") ? item : "minecraft:" + item));
            if (it != null) stack = new ItemStack(it);
        } else if (!icon.isEmpty()) {
            tex = texture(icon, project);
        }
        SHOWN.add(new Notice(text, tex, stack));
        if (sound) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f));
    }

    /** "моя картинка" — a picture of the project; "minecraft:textures/item/diamond.png" — any texture of the game. */
    private static ResourceLocation texture(String icon, String project) {
        if (icon.contains(":") || icon.contains("/")) {
            String id = icon.contains(":") ? icon : "minecraft:" + icon;
            if (!id.endsWith(".png")) id += ".png";
            return ResourceLocation.tryParse(id);
        }
        return Images.texture(project, icon);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || SHOWN.isEmpty() || Minecraft.getInstance().isPaused()) return;
        SHOWN.removeIf(n -> ++n.age > LIFE);
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post e) {
        if (SHOWN.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        GuiGraphics g = e.getGuiGraphics();
        int y = 6;
        for (Notice n : SHOWN) {
            float t = n.age + e.getPartialTick();
            float in = Math.min(1, t / SLIDE), out = Math.min(1, (LIFE - t) / SLIDE);
            int x = Math.round(-W * (1 - Math.min(in, Math.max(0, out))));
            g.fill(x, y, x + W, y + H, 0xD0101218);
            int tx = x + 6;
            if (!n.item.isEmpty()) {
                g.renderItem(n.item, x + 3, y + 3);
                tx = x + 23;
            } else if (n.icon != null) {
                g.blit(n.icon, x + 3, y + 3, 0, 0, 16, 16, 16, 16);
                tx = x + 23;
            }
            g.drawString(mc.font, n.text, tx, y + 7, 0xFFFFFFFF, false);
            y += H + 2;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        SHOWN.clear();
    }
}
