package org.zonarstudio.spraute_engine.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.StepHeightSyncPacket;

import java.util.UUID;

/**
 * Высота шага: Forge-атрибут на 1.20.1+, поле maxUpStep + пакет на клиент для 1.19.2.
 */
public final class SprauteStepHeightCompat {
    private static final UUID STEP_MODIFIER_UUID = UUID.fromString("8f3c2a10-4b5e-4d6f-9a1b-2c3d4e5f6071");

    private SprauteStepHeightCompat() {}

    public static void set(ServerPlayer player, float totalBlocks) {
        if (player == null) return;
        float addition = Math.max(0f, totalBlocks - 0.6f);
        //? if >=1.20.1 {
        net.minecraft.world.entity.ai.attributes.AttributeInstance inst =
                player.getAttribute(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (inst == null) {
            SprautePlayerCompat.setStepHeight(player, totalBlocks);
            return;
        }
        net.minecraft.world.entity.ai.attributes.AttributeModifier existing = inst.getModifier(STEP_MODIFIER_UUID);
        if (existing != null && Math.abs((float) existing.getAmount() - addition) < 1.0e-4f) {
            return;
        }
        inst.removeModifier(STEP_MODIFIER_UUID);
        if (addition > 1.0e-4f) {
            inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    STEP_MODIFIER_UUID, "spraute_step", addition,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        }
        //?} else {
        /*SprautePlayerCompat.setStepHeight(player, totalBlocks);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StepHeightSyncPacket(totalBlocks));
        *///?}
    }

    /** Переприменить каждый тик (1.19.2 — ванила сбрасывает поле). */
    public static void applyTick(ServerPlayer player, float totalBlocks) {
        if (player == null) return;
        //? if >=1.20.1 {
        // Атрибут Forge сам обновляет maxUpStep при изменении; повторная установка не нужна.
        //?} else {
        /*SprautePlayerCompat.setStepHeight(player, totalBlocks);
        *///?}
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        set(player, 0.6f);
    }
}
