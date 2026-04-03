package org.zonarstudio.spraute_engine.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.zonarstudio.spraute_engine.script.ScriptManager;

public class SprauteOrbEntity extends Entity {
    private static final EntityDataAccessor<String> DATA_TEXTURE = SynchedEntityData.defineId(SprauteOrbEntity.class, EntityDataSerializers.STRING);
    
    public int value;
    public int age;
    private int pickDelay;
    private Player targetPlayer;
    private int targetTime;

    public SprauteOrbEntity(EntityType<? extends SprauteOrbEntity> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.pickDelay = 20;
    }

    public SprauteOrbEntity(Level pLevel, double x, double y, double z, int value, String texture) {
        this(ModEntities.SPRAUTE_ORB.get(), pLevel);
        this.setPos(x, y, z);
        this.setYRot((float)(this.random.nextDouble() * 360.0D));
        this.setDeltaMovement((this.random.nextDouble() * 0.2D - 0.1D) * 2.0D, this.random.nextDouble() * 0.2D * 2.0D, (this.random.nextDouble() * 0.2D - 0.1D) * 2.0D);
        this.value = value;
        this.setTexture(texture);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TEXTURE, "");
    }

    public String getTexture() {
        return this.entityData.get(DATA_TEXTURE);
    }

    public void setTexture(String texture) {
        this.entityData.set(DATA_TEXTURE, texture);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.pickDelay > 0) {
            this.pickDelay--;
        }

        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.03D, 0.0D));

        if (this.level.isClientSide) {
            this.noPhysics = false;
        } else {
            this.noPhysics = !this.level.noCollision(this, this.getBoundingBox().deflate(1.0E-7D));
            if (this.noPhysics) {
                this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0D, this.getZ());
            }
        }

        if (!this.level.noCollision(this.getBoundingBox())) {
            this.move(MoverType.SELF, this.getDeltaMovement());
            float f = 0.98F;
            if (this.onGround) {
                f = this.level.getBlockState(new BlockPos(this.getX(), this.getY() - 1.0D, this.getZ())).getBlock().getFriction() * 0.98F;
            }

            this.setDeltaMovement(this.getDeltaMovement().multiply((double)f, 0.98D, (double)f));
            if (this.onGround) {
                this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, -0.9D, 1.0D));
            }
        }

        this.age++;
        if (this.age >= 6000) {
            this.discard();
        }

        if (!this.level.isClientSide) {
            if (this.targetTime <= 0 || this.targetPlayer == null || this.targetPlayer.isRemoved()) {
                this.targetTime = 20;
                this.targetPlayer = this.level.getNearestPlayer(this, 8.0D);
            } else {
                this.targetTime--;
            }

            if (this.targetPlayer != null) {
                Vec3 vec3 = new Vec3(this.targetPlayer.getX() - this.getX(), this.targetPlayer.getY() + (double)this.targetPlayer.getEyeHeight() / 2.0D - this.getY(), this.targetPlayer.getZ() - this.getZ());
                double d0 = vec3.lengthSqr();
                if (d0 < 64.0D) {
                    double d1 = 1.0D - Math.sqrt(d0) / 8.0D;
                    this.setDeltaMovement(this.getDeltaMovement().add(vec3.normalize().scale(d1 * d1 * 0.1D)));
                }
            }
        }
    }

    @Override
    public void playerTouch(Player player) {
        if (!this.level.isClientSide && this.pickDelay == 0) {
            player.take(this, 1);
            this.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.1F, 0.5F * ((this.random.nextFloat() - this.random.nextFloat()) * 0.7F + 1.8F));
            
            if (player instanceof ServerPlayer serverPlayer) {
                ScriptManager.getInstance().onOrbPickup(serverPlayer, this.getTexture(), this.value);
            }
            
            this.discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag pCompound) {
        this.age = pCompound.getInt("Age");
        this.value = pCompound.getInt("Value");
        this.setTexture(pCompound.getString("Texture"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag pCompound) {
        pCompound.putInt("Age", this.age);
        pCompound.putInt("Value", this.value);
        pCompound.putString("Texture", this.getTexture());
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}