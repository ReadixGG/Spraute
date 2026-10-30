package org.sprautestudio.st_addon_spraute.platform.forge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.entity.PartEntity;
import org.sprautestudio.st_addon_spraute.runtime.models.BodyPart;
import org.sprautestudio.st_addon_spraute.runtime.models.SprauteModelEntity;

/** A character's extra hitbox as Forge has it. */
final class ForgePart extends PartEntity<SprauteModelEntity> implements BodyPart {
    private final int index;
    private EntityDimensions size = EntityDimensions.scalable(0.01f, 0.01f);
    private boolean active;

    ForgePart(SprauteModelEntity body, int index) {
        super(body);
        this.index = index;
        this.noPhysics = true;
        refreshDimensions();
    }

    @Override
    public SprauteModelEntity body() {
        return getParent();
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public boolean active() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public void resize(float w, float h) {
        if (Math.abs(size.width - w) < 1e-4 && Math.abs(size.height - h) < 1e-4) return;
        size = EntityDimensions.scalable(w, h);
        refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    @Override
    public boolean isPickable() {
        return active && getParent().isPickable();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!active || isInvulnerableTo(source)) return false;
        return getParent().hurtPart(this, source, amount);
    }

    /** A click on a part is a click on the character (dialogs, "wait click"). */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return getParent().interact(player, hand);
    }

    @Override
    public boolean is(Entity e) {
        return this == e || getParent() == e;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        throw new UnsupportedOperationException();
    }
}
