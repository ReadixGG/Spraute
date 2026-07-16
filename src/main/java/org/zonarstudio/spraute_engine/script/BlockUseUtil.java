package org.zonarstudio.spraute_engine.script;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Server-side simulation of a right-click on a block (door, lever, button, chest, etc.).
 * Actor is optional: player, NPC, or anonymous (fake player at the block).
 */
public final class BlockUseUtil {
    private static final UUID ANON_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private BlockUseUtil() {}

    public static boolean useBlock(Level level, int x, int y, int z) {
        return useBlock(level, x, y, z, null, InteractionHand.MAIN_HAND);
    }

    public static boolean useBlock(Level level, int x, int y, int z, @Nullable LivingEntity actor, InteractionHand hand) {
        return useBlock(level, new BlockPos(x, y, z), actor, hand);
    }

    public static boolean useBlock(Level level, BlockPos pos, @Nullable LivingEntity actor, InteractionHand hand) {
        if (level == null || pos == null || hand == null) return false;
        if (!(level instanceof ServerLevel serverLevel)) return false;

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;

        ServerPlayer interactor = resolveInteractor(serverLevel, pos, actor);
        BlockHitResult hit = buildHit(interactor, level, pos);
        InteractionResult result = state.use(level, interactor, hand, hit);

        if (result.consumesAction() && BlockInteractionUtil.isChestLike(state.getBlock())) {
            ChestLootManager.applyOnOpen(serverLevel, pos, state);
        }

        return result.consumesAction();
    }

    private static ServerPlayer resolveInteractor(ServerLevel level, BlockPos pos, @Nullable LivingEntity actor) {
        if (actor instanceof ServerPlayer sp) return sp;

        GameProfile profile;
        if (actor != null) {
            String name = actor.getName().getString();
            if (name == null || name.isEmpty()) name = "SprauteActor";
            profile = new GameProfile(actor.getUUID(), name);
        } else {
            profile = new GameProfile(ANON_UUID, "[Spraute]");
        }

        ServerPlayer fake = FakePlayerFactory.get(level, profile);
        if (actor != null) {
            fake.setPos(actor.getX(), actor.getEyeY(), actor.getZ());
            fake.setYRot(actor.getYRot());
            fake.setXRot(actor.getXRot());
        } else {
            Vec3 center = Vec3.atCenterOf(pos);
            fake.setPos(center.x, center.y, center.z + 1.5);
            fake.setYRot(180f);
            fake.setXRot(0f);
        }
        return fake;
    }

    private static BlockHitResult buildHit(ServerPlayer interactor, Level level, BlockPos pos) {
        Vec3 eyes = interactor.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        HitResult traced = level.clip(new ClipContext(
                eyes, center,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                interactor));
        if (traced instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos)) {
            return blockHit;
        }

        Vec3 toInteractor = eyes.subtract(center);
        Direction face = Direction.getNearest(toInteractor.x, toInteractor.y, toInteractor.z);
        if (face == Direction.DOWN && toInteractor.y > 0) face = Direction.UP;
        return new BlockHitResult(center, face, pos, false);
    }
}
