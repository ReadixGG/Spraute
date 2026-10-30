package org.sprautestudio.st_addon_spraute.runtime.models;

import org.sprautestudio.st_addon_spraute.core.entity.AnimatedEntity;
import org.sprautestudio.st_addon_spraute.core.anim.MovementActionType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.sprautestudio.st_addon_spraute.core.anim.Animation;
import org.sprautestudio.st_addon_spraute.core.anim.ConfiguredAnimation;
import org.sprautestudio.st_addon_spraute.core.anim.LoopMode;

import java.util.Optional;

/**
 * A character of a Spraute entity type (or a plain project model). The type's idle / walk / run
 * animations (or the model's animations of those names) play on their own.
 */
public class SprauteModelEntity extends AnimatedEntity {
    private static final EntityDataAccessor<String> MODEL = SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.STRING);
    /** Parts the story switched off ("head,tail"): both sides must know, or the client would still aim at them. */
    private static final EntityDataAccessor<String> PARTS_OFF = SynchedEntityData.defineId(SprauteModelEntity.class, EntityDataSerializers.STRING);
    private String appliedFor = "";

    /** Most extra hitboxes a type can have. Every body keeps this many parts; those its type does not use stay off. */
    public static final int MAX_PARTS = 8;
    private final SprautePart[] parts = new SprautePart[MAX_PARTS];

    public SprauteModelEntity(EntityType<? extends AnimatedEntity> type, Level level) {
        super(type, level);
        for (int i = 0; i < MAX_PARTS; i++) parts[i] = new SprautePart(this, i);
        // the parts take the ids right after the body (as the ender dragon's do), so the client finds them by id
        setId(idCounter().getAndAdd(MAX_PARTS + 1) + 1);
    }

    private static java.util.concurrent.atomic.AtomicInteger counter;

    private static java.util.concurrent.atomic.AtomicInteger idCounter() {
        if (counter == null) counter = net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
                net.minecraft.world.entity.Entity.class, null, "f_19843_");
        return counter;
    }

    /** A solo story's character is sent to its player alone. */
    @Override
    public boolean broadcastToPlayer(net.minecraft.server.level.ServerPlayer p) {
        return org.sprautestudio.st_addon_spraute.runtime.SprauteRuntime.sees(this, p) && super.broadcastToPlayer(p);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < MAX_PARTS; i++) if (parts[i] != null) parts[i].setId(id + i + 1);
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public net.minecraftforge.entity.PartEntity<?>[] getParts() {
        return parts;
    }

    // ================================================================== extra hitboxes

    private java.util.List<EntityLibrary.Def.Part> partDefs() {
        EntityLibrary.Look l = look();
        return l != null && l.def() != null ? l.def().parts : java.util.List.of();
    }

    private boolean off(String name) {
        String o = entityData.get(PARTS_OFF);
        if (o.isEmpty()) return false;
        for (String s : o.split(",")) if (s.equals(name)) return true;
        return false;
    }

    /** Story overrides of a part's damage multiplier (server only). */
    private final java.util.Map<String, Float> damageOverride = new java.util.HashMap<>();
    private String lastPart = "";
    private boolean inPartHit;

    /** Names of the type's parts, in order. */
    public java.util.List<String> partNames() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (EntityLibrary.Def.Part p : partDefs()) out.add(p.name);
        return out;
    }

    /** The part's hitbox now, or null when there is no such part. */
    public net.minecraft.world.phys.AABB partBox(String name) {
        java.util.List<EntityLibrary.Def.Part> defs = partDefs();
        for (int i = 0; i < defs.size() && i < MAX_PARTS; i++) if (defs.get(i).name.equals(name)) return parts[i].getBoundingBox();
        return null;
    }

    public boolean partEnabled(String name) {
        for (EntityLibrary.Def.Part p : partDefs()) if (p.name.equals(name)) return p.enabled && !off(name);
        return false;
    }

    /** Switches a part on or off for this body (a shield that drops, a weak spot that opens). */
    public void setPartEnabled(String name, boolean on) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        String o = entityData.get(PARTS_OFF);
        if (!o.isEmpty()) set.addAll(java.util.Arrays.asList(o.split(",")));
        if (on) set.remove(name);
        else set.add(name);
        entityData.set(PARTS_OFF, String.join(",", set));
    }

    /** Contact damage of parts (part … attack N), server only. */
    private final java.util.Map<String, Float> partAttack = new java.util.HashMap<>();

    public void setPartAttack(String name, float damage) {
        if (damage <= 0) partAttack.remove(name);
        else partAttack.put(name, damage);
    }

    public float partAttack(String name) {
        return partAttack.getOrDefault(name, 0f);
    }

    public void setPartDamage(String name, float mult) {
        damageOverride.put(name, Math.max(0, mult));
    }

    public float partDamage(String name) {
        Float o = damageOverride.get(name);
        if (o != null) return o;
        for (EntityLibrary.Def.Part p : partDefs()) if (p.name.equals(name)) return p.damage;
        return 1;
    }

    /** The part hit last ("body" — the main hitbox), "" — not hit yet. */
    public String lastPart() {
        return lastPart;
    }

    // ------------------------------------------------------------------ bones the parts ride on

    /**
     * Where the model's bones are now (pivot and turn, in the body's space). The client fills it while drawing;
     * the server gets it from the client (Packets.PartPoses), since only the client animates bones.
     */
    public final java.util.Map<String, PartShape.BonePose> bonePoses = new java.util.concurrent.ConcurrentHashMap<>();
    /** The pose stack's matrix at the body's feet, at the start of drawing it (client). */
    public org.joml.Matrix4f renderBase;
    /** The turned boxes of the parts this tick (null — off). */
    private final PartShape[] shapes = new PartShape[MAX_PARTS];

    /** The type's parts as drawn and hit now. */
    public PartShape shape(int i) {
        return i >= 0 && i < MAX_PARTS ? shapes[i] : null;
    }

    public java.util.List<EntityLibrary.Def.Part> partList() {
        return partDefs();
    }

    /** The pose of the bone a part rides on, if it is known and fresh (else the part stands from the feet). */
    public PartShape.BonePose freshPose(String bone) {
        if (bone == null || bone.isEmpty()) return null;
        PartShape.BonePose bp = bonePoses.get(bone);
        return bp != null && System.currentTimeMillis() - bp.at() < 2000 ? bp : null;
    }

    /** Puts the parts where the type says, turned with the body or carried by their bones. */
    private void placeParts() {
        java.util.List<EntityLibrary.Def.Part> defs = partDefs();
        float s = lookScale();
        for (int i = 0; i < MAX_PARTS; i++) {
            SprautePart part = parts[i];
            EntityLibrary.Def.Part d = i < defs.size() ? defs.get(i) : null;
            part.xo = part.getX();
            part.yo = part.getY();
            part.zo = part.getZ();
            part.xOld = part.getX();
            part.yOld = part.getY();
            part.zOld = part.getZ();
            if (d == null || !d.enabled || off(d.name)) {
                part.active = false;
                shapes[i] = null;
                part.resize(0.01f, 0.01f);
                part.setPos(getX(), getY(), getZ());
                continue;
            }
            part.active = true;
            PartShape sh = PartShape.of(d, s, yBodyRot, freshPose(d.bone));
            shapes[i] = sh;
            // the game picks by an upright box: the one around the turned box
            net.minecraft.world.phys.AABB b = sh.bounds(getX(), getY(), getZ());
            part.resize((float) Math.max(0.02, Math.max(b.getXsize(), b.getZsize())), (float) Math.max(0.02, b.getYsize()));
            part.setPos((b.minX + b.maxX) / 2, b.minY, (b.minZ + b.maxZ) / 2);
        }
    }

    /**
     * The line a blow came along, in the body's space: an arrow or a cast along its flight, a hand from the
     * hitter's eyes along its look. Null when it cannot be told.
     */
    private net.minecraft.world.phys.Vec3[] blowLine(net.minecraft.world.damagesource.DamageSource source) {
        net.minecraft.world.entity.Entity direct = source.getDirectEntity();
        if (direct == null) return null;
        net.minecraft.world.phys.Vec3 me = position(), from, to;
        if (direct instanceof net.minecraft.world.entity.LivingEntity le) {
            from = le.getEyePosition();
            to = from.add(le.getViewVector(1).scale(8));
        } else {
            net.minecraft.world.phys.Vec3 v = direct.getDeltaMovement();
            if (v.lengthSqr() < 1e-6) return null;
            from = direct.position().subtract(v.scale(2));
            to = direct.position().add(v.scale(2));
        }
        return new net.minecraft.world.phys.Vec3[]{from.subtract(me), to.subtract(me)};
    }

    /** Which box the blow really met first along its line: a part index, -1 — the main hitbox, -2 — none of them. */
    private int firstHit(net.minecraft.world.damagesource.DamageSource source) {
        net.minecraft.world.phys.Vec3[] line = blowLine(source);
        if (line == null) return -2;
        int best = -2;
        double bestT = Double.MAX_VALUE;
        for (int i = 0; i < MAX_PARTS; i++) {
            if (shapes[i] == null || !parts[i].active) continue;
            double t = shapes[i].hit(line[0], line[1]);
            if (t >= 0 && t < bestT) {
                bestT = t;
                best = i;
            }
        }
        net.minecraft.world.phys.AABB body = getBoundingBox().move(-getX(), -getY(), -getZ());
        java.util.Optional<net.minecraft.world.phys.Vec3> bh = body.clip(line[0], line[1]);
        if (body.contains(line[0])) bh = java.util.Optional.of(line[0]);
        if (bh.isPresent()) {
            double t = bh.get().distanceTo(line[0]) / Math.max(1e-6, line[1].distanceTo(line[0]));
            if (t < bestT) best = -1;
        }
        return best;
    }

    /** A blow that landed on a part: remembered, told to the stories, multiplied and passed to the body. */
    boolean hurtPart(SprautePart part, net.minecraft.world.damagesource.DamageSource source, float amount) {
        java.util.List<EntityLibrary.Def.Part> defs = partDefs();
        if (part.index >= defs.size()) return false;
        // the game picked the upright box around a turned part: which box the blow really met
        int real = level().isClientSide ? part.index : firstHit(source);
        if (real == -1) return hurt(source, amount);
        if (real >= 0 && real < defs.size()) part = parts[real];
        String name = defs.get(part.index).name;
        float dmg = amount * partDamage(name);
        report(name, source, dmg, part.getBoundingBox().getCenter());
        if (dmg <= 0) {
            // a part that takes no harm still flinches the blow off with a sound
            level().playSound(null, part.getX(), part.getY(), part.getZ(), net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                    net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 1.2f);
            return false;
        }
        inPartHit = true;
        try {
            return hurt(source, dmg);
        } finally {
            inPartHit = false;
        }
    }

    private void report(String name, net.minecraft.world.damagesource.DamageSource source, float dmg, net.minecraft.world.phys.Vec3 at) {
        lastPart = name;
        if (level().isClientSide) return;
        java.util.Map<String, Object> d = new java.util.LinkedHashMap<>();
        d.put("part", name);
        d.put("damage", Math.round(dmg * 10) / 10.0);
        net.minecraft.world.entity.Entity by = source.getEntity();
        d.put("by", by instanceof net.minecraft.world.entity.player.Player ? "player" : by != null ? by.getName().getString() : "");
        d.put("entity", "spraute:" + model());
        d.put("name", getName().getString());
        d.put("x", Math.floor(at.x));
        d.put("y", Math.floor(at.y));
        d.put("z", Math.floor(at.z));
        d.put("__entity", this);
        org.sprautestudio.st_addon_spraute.runtime.GameEvents.post("parthit", by instanceof net.minecraft.world.entity.player.Player byPlayer ? byPlayer : null, d);
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        // straight into the main hitbox: with parts around it, that is the "body" part
        if (!inPartHit && !level().isClientSide && !partDefs().isEmpty() && !isInvulnerableTo(source)) {
            report("body", source, amount, getBoundingBox().getCenter());
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(MODEL, "");
        entityData.define(PARTS_OFF, "");
    }

    /** Key of the entity type or project model ("project/name"). */
    public String model() {
        return entityData.get(MODEL);
    }

    public void setModel(String key) {
        if (key == null) key = "";
        if (!key.equals(model())) entityData.set(MODEL, key);
        applyMovement();
        refreshDimensions();
    }

    /** What the hitbox was last sized for: a big character gets a big hitbox, not a person-sized one in its feet. */
    private String sizedFor = "";

    private float lookScale() {
        EntityLibrary.Look l = look();
        float s = l != null ? l.scale() : 1;
        return s > 0 ? s : 1;
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        net.minecraft.world.entity.EntityDimensions d = super.getDimensions(pose);
        EntityLibrary.Look l = look();
        // the type may give its own main hitbox (smaller, so the head part above it gets the head hits)
        if (l != null && l.def() != null && (l.def().width > 0 || l.def().height > 0)) {
            d = net.minecraft.world.entity.EntityDimensions.scalable(l.def().width > 0 ? l.def().width : d.width,
                    l.def().height > 0 ? l.def().height : d.height);
        }
        float s = lookScale();
        return s != 1 ? d.scale(s) : d;
    }

    /** What the hitbox was last sized for: scale and the type's own width and height. */
    private String sizeKey() {
        EntityLibrary.Look l = look();
        return lookScale() + "/" + (l != null && l.def() != null ? l.def().width + "x" + l.def().height : "");
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (MODEL.equals(key)) refreshDimensions();
    }

    public EntityLibrary.Look look() {
        return EntityLibrary.look(model());
    }

    /** The project model behind the look, if any. */
    public ModelLibrary.Model modelInfo() {
        EntityLibrary.Look l = look();
        return l != null ? l.model() : null;
    }

    /** The setting may name several clips ("walk_a;walk_b"): one is taken at random. */
    private Optional<Animation> movement(EntityLibrary.Look l, String setting, String fallbackName) {
        if (l.def() != null && setting != null && !setting.isBlank()) {
            java.util.List<Animation> found = new java.util.ArrayList<>();
            for (String one : setting.split("[;,]")) l.animation(one.trim()).ifPresent(found::add);
            if (!found.isEmpty()) return Optional.of(found.get(random.nextInt(found.size())));
        }
        return l.animation(fallbackName);
    }

    private void applyMovement() {
        EntityLibrary.Look l = look();
        if (l == null || level().isClientSide || appliedFor.equals(model())) return;
        appliedFor = model();
        EntityLibrary.Def d = l.def();
        if (d != null && d.speed > 0) {
            try {
                var attr = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                if (attr != null) attr.setBaseValue(0.25 * d.speed);
            } catch (Throwable ignored) {
            }
        }
        if (d != null && d.health > 0) {
            try {
                var hp = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (hp != null) hp.setBaseValue(d.health);
                setHealth(d.health);
            } catch (Throwable ignored) {
            }
        }
        if (d != null && !d.hitbox.isBlank()) {
            try {
                org.sprautestudio.st_addon_spraute.hitbox.Hitboxes.set(this,
                        org.sprautestudio.st_addon_spraute.hitbox.Hitboxes.parse(0, java.util.List.of(d.hitbox)));
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    protected String movementKey() {
        return model();
    }

    @Override
    protected ConfiguredAnimation turnAnimation(boolean left) {
        var a = org.sprautestudio.st_addon_spraute.runtime.Anims.gesture(this, left ? "turn_left" : "turn_right");
        return a != null ? a.configure() : null;
    }

    @Override
    protected ConfiguredAnimation blinkAnimation() {
        EntityLibrary.Look l = look();
        if (l == null) return null;
        Optional<Animation> a = l.animation("blinking");
        if (a.isEmpty()) a = l.animation("blink");
        return a.map(Animation::configure).orElse(null);
    }

    @Override
    protected ConfiguredAnimation movementAnimation(MovementActionType type) {
        EntityLibrary.Look l = look();
        if (l == null) return null;
        EntityLibrary.Def d = l.def();
        Optional<Animation> a;
        if (isSitting() && type == MovementActionType.IDLE) {
            a = l.animation("sit");
            if (a.isPresent()) return a.get().configure();
        }
        switch (type) {
            case WALKING -> a = movement(l, d != null ? d.walk : "", "walk");
            case FAST_WALKING -> {
                // its own clip if the model has one; else the walk, quicker
                a = l.animation("fast_walk");
                if (a.isPresent() && (d == null || d.walk.isBlank())) return a.get().configure();
                a = movement(l, d != null ? d.walk : "", "walk");
                if (a.isPresent()) return a.get().configure().speed(1.3f);
            }
            case RUNNING -> {
                a = movement(l, d != null ? d.run : "", "run");
                if (a.isEmpty()) a = movement(l, d != null ? d.walk : "", "walk");
            }
            default -> a = movement(l, d != null ? d.idle : "", "idle");
        }
        return a.map(Animation::configure).orElse(null);
    }

    @Override
    public void tick() {
        // the entity type's size may change while it lives (the project was synced)
        String sk = sizeKey();
        if (!sk.equals(sizedFor)) {
            sizedFor = sk;
            refreshDimensions();
        }
        super.tick();
        placeParts();
        if (!level().isClientSide && !partAttack.isEmpty()) org.sprautestudio.st_addon_spraute.runtime.AttackBoxes.contact(this);
        if (!level().isClientSide && !appliedFor.equals(model()) && tickCount % 20 == 0) applyMovement();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SprauteModel", model());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SprauteModel")) setModel(tag.getString("SprauteModel"));
    }
}
