package org.zonarstudio.spraute_engine.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;
import org.zonarstudio.spraute_engine.registry.CustomProjectileRegistry;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SprauteProjectileEntity extends Entity {
    private static final EntityDataAccessor<String> DATA_PROJECTILE_ID = SynchedEntityData.defineId(SprauteProjectileEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_TEXTURE = SynchedEntityData.defineId(SprauteProjectileEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_WIDTH = SynchedEntityData.defineId(SprauteProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData.defineId(SprauteProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ALPHA = SynchedEntityData.defineId(SprauteProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SEE_THROUGH = SynchedEntityData.defineId(SprauteProjectileEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_GLOW = SynchedEntityData.defineId(SprauteProjectileEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BILLBOARD = SynchedEntityData.defineId(SprauteProjectileEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_VISUAL_ROTATION = SynchedEntityData.defineId(SprauteProjectileEntity.class, EntityDataSerializers.FLOAT);

    public int age;
    private float gravity;
    private float drag = 1.0f;
    private int lifetime = 200;
    private float damage;
    private int pierceRemaining;
    private float knockback;
    private float rotationSpeed;
    private boolean collideBlocks = true;
    private boolean collideEntities = true;
    private UUID ownerUuid;
    private final Set<Integer> hitEntityIds = new HashSet<>();

    public SprauteProjectileEntity(EntityType<? extends SprauteProjectileEntity> type, Level level) {
        super(type, level);
    }

    public SprauteProjectileEntity(Level level, String projectileId, double x, double y, double z, Vec3 velocity, Entity owner) {
        this(ModEntities.SPRAUTE_PROJECTILE.get(), level);
        setPos(x, y, z);
        setDeltaMovement(velocity);
        if (owner != null) ownerUuid = owner.getUUID();
        applyDef(CustomProjectileRegistry.get(projectileId), projectileId);
    }

    public void applyDef(CustomProjectileRegistry.ProjectileDef def, String projectileId) {
        setProjectileId(projectileId != null ? projectileId : "");
        if (def == null) return;
        setTexture(def.texture);
        setProjectileWidth(def.width);
        setProjectileHeight(def.height);
        setAlpha(def.alpha);
        setSeeThrough(def.seeThrough);
        setGlow(def.glow);
        setBillboard(def.billboard);
        gravity = def.gravity;
        drag = def.drag;
        lifetime = def.lifetime;
        damage = def.damage;
        pierceRemaining = def.pierce;
        knockback = def.knockback;
        rotationSpeed = def.rotation;
        collideBlocks = def.collideBlocks;
        collideEntities = def.collideEntities;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_PROJECTILE_ID, "");
        entityData.define(DATA_TEXTURE, "");
        entityData.define(DATA_WIDTH, 0.5f);
        entityData.define(DATA_HEIGHT, 0.5f);
        entityData.define(DATA_ALPHA, 255);
        entityData.define(DATA_SEE_THROUGH, false);
        entityData.define(DATA_GLOW, false);
        entityData.define(DATA_BILLBOARD, true);
        entityData.define(DATA_VISUAL_ROTATION, 0f);
    }

    public String getProjectileId() { return entityData.get(DATA_PROJECTILE_ID); }
    public void setProjectileId(String id) { entityData.set(DATA_PROJECTILE_ID, id != null ? id : ""); }

    public String getTexture() { return entityData.get(DATA_TEXTURE); }
    public void setTexture(String texture) { entityData.set(DATA_TEXTURE, texture != null ? texture : ""); }

    public float getProjectileWidth() { return entityData.get(DATA_WIDTH); }
    public void setProjectileWidth(float width) {
        entityData.set(DATA_WIDTH, Math.max(0.05f, width));
        refreshDimensions();
    }

    public float getProjectileHeight() { return entityData.get(DATA_HEIGHT); }
    public void setProjectileHeight(float height) {
        entityData.set(DATA_HEIGHT, Math.max(0.05f, height));
        refreshDimensions();
    }

    public int getAlpha() { return entityData.get(DATA_ALPHA); }
    public void setAlpha(int alpha) { entityData.set(DATA_ALPHA, Math.min(255, Math.max(0, alpha))); }

    public boolean isSeeThrough() { return entityData.get(DATA_SEE_THROUGH); }
    public void setSeeThrough(boolean v) { entityData.set(DATA_SEE_THROUGH, v); }

    public boolean isGlow() { return entityData.get(DATA_GLOW); }
    public void setGlow(boolean v) { entityData.set(DATA_GLOW, v); }

    public boolean isBillboard() { return entityData.get(DATA_BILLBOARD); }
    public void setBillboard(boolean v) { entityData.set(DATA_BILLBOARD, v); }

    public float getVisualRotation() { return entityData.get(DATA_VISUAL_ROTATION); }

    public void setOwnerEntity(Entity owner) {
        ownerUuid = owner != null ? owner.getUUID() : null;
    }

    public Entity getOwnerEntity() {
        if (ownerUuid == null || SprauteEntityCompat.level(this).isClientSide) return null;
        if (SprauteEntityCompat.level(this) instanceof ServerLevel sl) {
            return sl.getEntity(ownerUuid);
        }
        return null;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(getProjectileWidth(), getProjectileHeight());
    }

    @Override
    public void tick() {
        super.tick();

        if (!SprauteEntityCompat.level(this).isClientSide) {
            age++;
            if (age >= lifetime) {
                discard();
                return;
            }

            Vec3 motion = getDeltaMovement();
            if (motion.lengthSqr() < 1.0E-8 && gravity <= 0 && age > 2) {
                discard();
                return;
            }

            Vec3 start = position();
            Vec3 end = start.add(motion);

            if (collideBlocks) {
                BlockHitResult blockHit = SprauteEntityCompat.level(this).clip(
                        new net.minecraft.world.level.ClipContext(start, end,
                                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                                net.minecraft.world.level.ClipContext.Fluid.NONE, this));
                if (blockHit.getType() == HitResult.Type.BLOCK) {
                    setPos(blockHit.getLocation());
                    onBlockImpact(blockHit);
                    return;
                }
            }

            if (collideEntities) {
                EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                        SprauteEntityCompat.level(this), this, start, end,
                        getBoundingBox().expandTowards(motion).inflate(0.5),
                        e -> canHitEntity(e));
                if (entityHit != null) {
                    onEntityHit(entityHit);
                    if (isRemoved()) return;
                }
            }

            setPos(end.x, end.y, end.z);
            move(MoverType.SELF, Vec3.ZERO);
            setDeltaMovement(motion.scale(drag).subtract(0, gravity, 0));
            entityData.set(DATA_VISUAL_ROTATION, getVisualRotation() + rotationSpeed);
        } else {
            entityData.set(DATA_VISUAL_ROTATION, getVisualRotation() + rotationSpeed);
        }
    }

    private boolean canHitEntity(Entity target) {
        if (!target.isPickable() || target.isSpectator()) return false;
        if (ownerUuid != null && target.getUUID().equals(ownerUuid)) return false;
        if (hitEntityIds.contains(target.getId())) return false;
        return true;
    }

    private void onEntityHit(EntityHitResult hit) {
        Entity target = hit.getEntity();
        hitEntityIds.add(target.getId());
        setPos(hit.getLocation());

        notifyProjectileHit("entity", hit.getLocation(), target);

        if (damage > 0 && target instanceof LivingEntity living) {
            Entity owner = getOwnerEntity();
            //? if >=1.20.1 {
            DamageSource source = owner instanceof LivingEntity le
                    ? SprauteEntityCompat.level(this).damageSources().thrown(this, le)
                    : SprauteEntityCompat.level(this).damageSources().generic();
            living.hurt(source, damage);
            //?} else {
            /*Entity src = owner != null ? owner : this;
            living.hurt(new net.minecraft.world.damagesource.EntityDamageSource("generic", src), damage);
            *///?}
            if (knockback > 0) {
                Vec3 kb = getDeltaMovement().normalize();
                living.knockback(knockback, kb.x, kb.z);
            }
        }

        if (pierceRemaining <= 0) {
            discard();
        } else {
            pierceRemaining--;
        }
    }

    private void onBlockImpact(BlockHitResult hit) {
        notifyProjectileHit("block", hit.getLocation(), null);
        discard();
    }

    private void notifyProjectileHit(String hitType, Vec3 pos, Entity target) {
        if (SprauteEntityCompat.level(this).isClientSide) return;
        org.zonarstudio.spraute_engine.script.ScriptManager.getInstance().onProjectileHit(
                getProjectileId(), hitType, pos.x, pos.y, pos.z, target, getOwnerEntity());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        if (tag.contains("ProjectileId")) setProjectileId(tag.getString("ProjectileId"));
        if (tag.contains("Texture")) setTexture(tag.getString("Texture"));
        if (tag.contains("Width")) setProjectileWidth(tag.getFloat("Width"));
        if (tag.contains("Height")) setProjectileHeight(tag.getFloat("Height"));
        if (tag.contains("Alpha")) setAlpha(tag.getInt("Alpha"));
        if (tag.contains("SeeThrough")) setSeeThrough(tag.getBoolean("SeeThrough"));
        if (tag.contains("Glow")) setGlow(tag.getBoolean("Glow"));
        if (tag.contains("Billboard")) setBillboard(tag.getBoolean("Billboard"));
        if (tag.contains("Gravity")) gravity = tag.getFloat("Gravity");
        if (tag.contains("Drag")) drag = tag.getFloat("Drag");
        if (tag.contains("Lifetime")) lifetime = tag.getInt("Lifetime");
        if (tag.contains("Damage")) damage = tag.getFloat("Damage");
        if (tag.contains("Pierce")) pierceRemaining = tag.getInt("Pierce");
        if (tag.contains("Knockback")) knockback = tag.getFloat("Knockback");
        if (tag.contains("RotationSpeed")) rotationSpeed = tag.getFloat("RotationSpeed");
        if (tag.contains("CollideBlocks")) collideBlocks = tag.getBoolean("CollideBlocks");
        if (tag.contains("CollideEntities")) collideEntities = tag.getBoolean("CollideEntities");
        if (tag.hasUUID("Owner")) ownerUuid = tag.getUUID("Owner");
        if (tag.contains("VisualRotation")) entityData.set(DATA_VISUAL_ROTATION, tag.getFloat("VisualRotation"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putString("ProjectileId", getProjectileId());
        tag.putString("Texture", getTexture());
        tag.putFloat("Width", getProjectileWidth());
        tag.putFloat("Height", getProjectileHeight());
        tag.putInt("Alpha", getAlpha());
        tag.putBoolean("SeeThrough", isSeeThrough());
        tag.putBoolean("Glow", isGlow());
        tag.putBoolean("Billboard", isBillboard());
        tag.putFloat("Gravity", gravity);
        tag.putFloat("Drag", drag);
        tag.putInt("Lifetime", lifetime);
        tag.putFloat("Damage", damage);
        tag.putInt("Pierce", pierceRemaining);
        tag.putFloat("Knockback", knockback);
        tag.putFloat("RotationSpeed", rotationSpeed);
        tag.putBoolean("CollideBlocks", collideBlocks);
        tag.putBoolean("CollideEntities", collideEntities);
        if (ownerUuid != null) tag.putUUID("Owner", ownerUuid);
        tag.putFloat("VisualRotation", getVisualRotation());
    }

    @Override
    //? if >=1.20.1 {
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
    //?} else {
    /*public Packet<?> getAddEntityPacket() {
    *///?}
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public static void removeAllInLevel(ServerLevel level, String projectileId) {
        for (Entity e : level.getAllEntities()) {
            if (e instanceof SprauteProjectileEntity proj) {
                if (projectileId == null || projectileId.isBlank()
                        || projectileId.equals(proj.getProjectileId())) {
                    proj.discard();
                }
            }
        }
    }
}
