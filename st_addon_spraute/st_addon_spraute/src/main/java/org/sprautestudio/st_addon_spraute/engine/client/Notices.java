package org.sprautestudio.st_addon_spraute.engine.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Notifications from a story, top left: a 16×16 icon (an item, a picture or nothing) and white text. Each comes out
 * from above and sinks slowly, gone in five seconds; a new one starts at most every 0.6 s, and waits while a screen is open.
 */
@Mod.EventBusSubscriber(modid = St_addon_spraute.MODID, value = Dist.CLIENT)
public final class Notices {
    private Notices() {
    }

    private static final int LIFE = 100, FADE = 20, EVERY = 12, ICON = 16;
    private static final float TRAVEL = 150, START_Y = -16;

    private static final class Notice {
        final String text;
        final ResourceLocation icon;
        final ItemStack item;
        final boolean sound;
        int age;

        Notice(String text, ResourceLocation icon, ItemStack item, boolean sound) {
            this.text = text;
            this.icon = icon;
            this.item = item;
            this.sound = sound;
        }
    }

    private static final Deque<Notice> WAITING = new ArrayDeque<>();
    private static final List<Notice> SHOWN = new ArrayList<>();
    private static int cooldown;

    public static void show(String text, String icon, String item, String project, boolean sound) {
        ItemStack stack = ItemStack.EMPTY;
        ResourceLocation tex = null;
        if (!item.isEmpty()) {
            Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(item.contains(":") ? item : "minecraft:" + item));
            if (it != null) stack = new ItemStack(it);
        } else if (!icon.isEmpty()) {
            tex = texture(icon, project);
        }
        WAITING.addLast(new Notice(text, tex, stack, sound));
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
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.isPaused()) return;
        SHOWN.removeIf(n -> ++n.age > LIFE);
        if (cooldown > 0) cooldown--;
        if (cooldown == 0 && mc.screen == null && !WAITING.isEmpty()) {
            Notice n = WAITING.pollFirst();
            SHOWN.add(n);
            cooldown = EVERY;
            if (n.sound) ProjectSounds.playBuiltin("notice");
        }
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post e) {
        if (SHOWN.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        GuiGraphics g = e.getGuiGraphics();
        for (Notice n : SHOWN) {
            float t = n.age + (mc.isPaused() ? 0 : e.getPartialTick());
            float y = START_Y + TRAVEL * (1 - (1 - t / LIFE) * (1 - t / LIFE));
            float alpha = Math.max(0, Math.min(1, (LIFE - t) / FADE));
            if (alpha <= 0) continue;
            g.pose().pushPose();
            g.pose().translate(4, y, 0);
            int tx = 0;
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1, 1, 1, alpha);
            if (!n.item.isEmpty()) {
                g.renderItem(n.item, 0, 0);
                tx = ICON + 4;
            } else if (n.icon != null) {
                g.blit(n.icon, 0, 0, 0, 0, ICON, ICON, ICON, ICON);
                tx = ICON + 4;
            }
            RenderSystem.setShaderColor(1, 1, 1, 1);
            g.drawString(mc.font, n.text, tx, (ICON - mc.font.lineHeight) / 2 + 1, Math.max(4, Math.round(alpha * 255)) << 24 | 0xFFFFFF, true);
            g.pose().popPose();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        SHOWN.clear();
        WAITING.clear();
    }
}
