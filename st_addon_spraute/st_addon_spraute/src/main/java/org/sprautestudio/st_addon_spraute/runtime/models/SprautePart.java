package org.sprautestudio.st_addon_spraute.runtime.models;

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

/**
 * One extra hitbox of a Spraute character (a head, a tail, a weak spot), like the parts of the ender
 * dragon: the game picks it for hands, arrows and casts on its own, and hits on it go to the body with
 * the part's name and damage multiplier. Not saved and not sent: both sides build the same parts.
 */
public class SprautePart extends PartEntity<SprauteModelEntity> {
    final int index;
    private EntityDimensions size = EntityDimensions.scalable(0.01f, 0.01f);
    /** Placed by the body this tick (a part of a type without that many parts stays off). */
    boolean active;

    public SprautePart(SprauteModelEntity body, int index) {
        super(body);
        this.index = index;
        this.noPhysics = true;
        refreshDimensions();
    }

    void resize(float w, float h) {
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
