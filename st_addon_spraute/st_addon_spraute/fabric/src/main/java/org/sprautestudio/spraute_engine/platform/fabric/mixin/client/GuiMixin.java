package org.sprautestudio.spraute_engine.platform.fabric.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.scores.Objective;
import org.sprautestudio.spraute_engine.platform.fabric.HudParts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Unique
    private float spraute$partial;
    /** The part of the health bars being drawn is off. */
    @Unique
    private boolean spraute$skip;
    @Unique
    private GuiGraphics spraute$graphics;

    @Inject(method = "render", at = @At("HEAD"))
    private void spraute$frame(GuiGraphics g, float partial, CallbackInfo ci) {
        spraute$partial = partial;
    }

    @Unique
    private void spraute$gate(String part, GuiGraphics g, CallbackInfo ci) {
        if (!HudParts.allowed(part, g, spraute$partial)) ci.cancel();
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void spraute$hotbar(float partial, GuiGraphics g, CallbackInfo ci) {
        spraute$gate("hotbar", g, ci);
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void spraute$exp(GuiGraphics g, int x, CallbackInfo ci) {
        spraute$gate("experience_bar", g, ci);
    }

    @Inject(method = "renderJumpMeter", at = @At("HEAD"), cancellable = true)
    private void spraute$jump(PlayerRideableJumping mount, GuiGraphics g, int x, CallbackInfo ci) {
        spraute$gate("jump_bar", g, ci);
    }

    @Inject(method = "renderVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void spraute$mount(GuiGraphics g, CallbackInfo ci) {
        spraute$gate("mount_health", g, ci);
    }

    @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void spraute$itemName(GuiGraphics g, CallbackInfo ci) {
        spraute$gate("item_name", g, ci);
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void spraute$crosshair(GuiGraphics g, CallbackInfo ci) {
        spraute$gate("crosshair", g, ci);
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void spraute$effects(GuiGraphics g, CallbackInfo ci) {
        spraute$gate("potion_icons", g, ci);
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void spraute$scoreboard(GuiGraphics g, Objective objective, CallbackInfo ci) {
        spraute$gate("scoreboard", g, ci);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/BossHealthOverlay;render(Lnet/minecraft/client/gui/GuiGraphics;)V"))
    private void spraute$boss(BossHealthOverlay overlay, GuiGraphics g) {
        if (HudParts.allowed("boss_event_progress", g, spraute$partial)) overlay.render(g);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;render(Lnet/minecraft/client/gui/GuiGraphics;III)V"))
    private void spraute$chat(ChatComponent chat, GuiGraphics g, int ticks, int mouseX, int mouseY) {
        if (HudParts.allowed("chat_panel", g, spraute$partial)) chat.render(g, ticks, mouseX, mouseY);
    }

    // health, armor, food and air are one method of the game: its profiler sections tell them apart

    @Unique
    private void spraute$section(String section) {
        String part = switch (section) {
            case "armor" -> "armor_level";
            case "health" -> "player_health";
            case "food" -> "food_level";
            case "air" -> "air_level";
            default -> null;
        };
        spraute$skip = part != null && !HudParts.allowed(part, spraute$graphics, spraute$partial);
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"))
    private void spraute$bars(GuiGraphics g, CallbackInfo ci) {
        spraute$graphics = g;
        spraute$skip = false;
    }

    @Inject(method = "renderPlayerHealth", at = @At("RETURN"))
    private void spraute$barsDone(GuiGraphics g, CallbackInfo ci) {
        spraute$skip = false;
    }

    @Redirect(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;push(Ljava/lang/String;)V"))
    private void spraute$push(ProfilerFiller profiler, String section) {
        profiler.push(section);
        spraute$section(section);
    }

    @Redirect(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V"))
    private void spraute$popPush(ProfilerFiller profiler, String section) {
        profiler.popPush(section);
        spraute$section(section);
    }

    @Redirect(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void spraute$blit(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int w, int h) {
        if (!spraute$skip) g.blit(texture, x, y, u, v, w, h);
    }

    @Inject(method = "renderHearts", at = @At("HEAD"), cancellable = true)
    private void spraute$hearts(CallbackInfo ci) {
        if (spraute$skip) ci.cancel();
    }
}
