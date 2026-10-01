package org.zonarstudio.spraute_engine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.script.PlayerJumpStrengthOverrides;

/** Клиентская копия силы прыжка и множителя копания. */
public final class ClientPlayerExtrasHolder {
    private static float jumpPower = PlayerJumpStrengthOverrides.VANILLA;
    private static boolean jumpCustom;
    private static double digSpeed = 1.0;
    private static boolean digCustom;

    static {
        PlayerJumpStrengthOverrides.clientJump = ClientPlayerExtrasHolder::jumpOverride;
    }

    private ClientPlayerExtrasHolder() {}

    public static void apply(float jump, double dig) {
        jumpPower = jump;
        jumpCustom = true;
        digSpeed = dig;
        digCustom = true;
    }

    public static float jumpPower() {
        return jumpPower;
    }

    public static double digSpeed() {
        return digCustom ? digSpeed : 1.0;
    }

    public static Float jumpOverride(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (!jumpCustom || mc.player != player) return null;
        return jumpPower;
    }

    public static void clear() {
        jumpPower = PlayerJumpStrengthOverrides.VANILLA;
        jumpCustom = false;
        digSpeed = 1.0;
        digCustom = false;
    }
}
