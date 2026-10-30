package org.sprautestudio.st_addon_spraute.core.script;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.sprautestudio.st_addon_spraute.core.anim.Animation;
import org.sprautestudio.st_addon_spraute.core.anim.ConfiguredAnimation;
import org.sprautestudio.st_addon_spraute.core.anim.MovementActionType;
import org.sprautestudio.st_addon_spraute.core.entity.AnimatedEntity;
import org.sprautestudio.st_addon_spraute.core.world.Location;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;
import org.sprautestudio.st_addon_spraute.runtime.Anims;
import org.sprautestudio.st_addon_spraute.runtime.SprauteRuntime;

import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Writes a performer's steps, one call per step. */
public final class ScriptPerformerBuilder {
    private final ScriptInstance script;
    private final ScriptPerformer performer;

    private ScriptPerformerBuilder(ScriptInstance script) {
        this.script = script;
        this.performer = new ScriptPerformer(script);
    }

    public static ScriptPerformerBuilder builder(ScriptInstance script) {
        return new ScriptPerformerBuilder(script);
    }

    public ScriptPerformer build() {
        return performer;
    }

    public void build(Consumer<ScriptPerformer> to) {
        to.accept(performer);
    }

    public ScriptPerformerBuilder append(Function<ScriptPerformer, ScriptPerformerAction> make) {
        performer.actions.add(make.apply(performer));
        return this;
    }

    private static final class Run extends ScriptPerformerAction {
        private final Runnable r;

        Run(ScriptPerformer p, Runnable r) {
            super(p);
            this.r = r;
        }

        @Override
        public boolean update() {
            r.run();
            return true;
        }
    }

    private static final class Wait extends ScriptPerformerAction {
        private final Runnable begin;
        private final BooleanSupplier done;

        Wait(ScriptPerformer p, Runnable begin, BooleanSupplier done) {
            super(p);
            this.begin = begin;
            this.done = done;
        }

        @Override
        public void start() {
            if (begin != null) begin.run();
        }

        @Override
        public boolean update() {
            return done.getAsBoolean();
        }
    }

    // ================================================================== flow

    public void thenRun(Runnable r) {
        append(p -> new Run(p, r));
    }

    public void label(String name) {
        performer.labels.put(name, performer.actions.size());
        performer.labelPointsNames.add(name);
    }

    public void gotoLabel(String label, boolean ignored) {
        thenRun(() -> performer.jumpTo(label));
    }

    public void gotoLabel(Supplier<String> label, boolean ignored) {
        thenRun(() -> performer.jumpTo(label.get()));
    }

    public void thenWaitUntil(BooleanSupplier done) {
        append(p -> new Wait(p, null, done));
    }

    private void waitWith(Runnable begin, BooleanSupplier done) {
        append(p -> new Wait(p, begin, done));
    }

    public void thenDelay(int ticks) {
        thenDelay(() -> ticks);
    }

    public void thenDelay(IntSupplier ticks) {
        int[] left = {0};
        waitWith(() -> left[0] = ticks.getAsInt(), () -> left[0]-- <= 0);
    }

    public void thenDelayRandom(int min, int max, Random random) {
        thenDelay(() -> max <= min ? min : min + random.nextInt(max - min + 1));
    }

    /** Ends the whole story. */
    public void finish(boolean ignored) {
        thenRun(() -> script.env.stop(script.getId()));
        // the steps after this one do not run: the story is gone by the next tick
        thenWaitUntil(() -> false);
    }

    public void createParallelPerformer(String name, ScriptPerformer sub) {
        script.known.put(name, sub);
        thenRun(() -> {
            sub.restart();
            script.running.put(name, sub);
        });
    }

    public void removeParallelPerformer(String name) {
        thenRun(() -> script.running.remove(name));
    }

    public void saveState(String name) {
        label("__state_" + name);
    }

    public void gotoSaveState(String name) {
        gotoLabel("__state_" + name, true);
    }

    public void ensureScriptActive(int[] ids) {
        thenRun(() -> {
            for (int id : ids) script.env.start(id);
        });
    }

    public void ensureScriptInactive(int[] ids) {
        thenRun(() -> {
            for (int id : ids) script.env.stop(id);
        });
    }

    // ================================================================== waits

    public void thenWaitForFlag(String name) {
        thenWaitUntil(() -> script.getFlags().has(name));
    }

    public void thenWaitForKey(String key) {
        long[] from = {0};
        waitWith(() -> from[0] = script.env.keyCount(key), () -> script.env.keyCount(key) > from[0]);
    }

    public void thenWaitForTrigger() {
        int[] from = {0};
        waitWith(() -> from[0] = script.triggerSeq, () -> script.triggerSeq > from[0]);
    }

    public void thenAwaitEntityClick(Character<?> c) {
        long[] from = {0};
        waitWith(() -> from[0] = Behaviours.clicks(script.optional(c).orElse(null)), () -> Behaviours.clicks(script.optional(c).orElse(null)) > from[0]);
    }

    public void thenAwaitBlockClick(BlockPos pos) {
        long[] from = {0};
        waitWith(() -> from[0] = Behaviours.clicks(pos), () -> Behaviours.clicks(pos) > from[0]);
    }

    public void thenWhenPlayerCloseTo(Character<?> c, double radius) {
        thenWaitUntil(() -> {
            ServerPlayer p = SprauteRuntime.player();
            Entity e = script.optional(c).orElse(null);
            return p != null && e != null && p.level() == e.level() && p.distanceToSqr(e) <= radius * radius;
        });
    }

    public void thenWhenPlayerCloseTo(Supplier<Location> at, double radius) {
        thenWaitUntil(() -> {
            ServerPlayer p = SprauteRuntime.player();
            Location l = at.get();
            return p != null && l != null && p.distanceToSqr(l.x, l.y, l.z) <= radius * radius;
        });
    }

    public void thenWhenPlayerEnterBox(AABB box) {
        thenWaitUntil(() -> {
            ServerPlayer p = SprauteRuntime.player();
            return p != null && box.contains(p.position());
        });
    }

    public void thenWhenPlayerExitBox(AABB box) {
        thenWaitUntil(() -> {
            ServerPlayer p = SprauteRuntime.player();
            return p != null && !box.contains(p.position());
        });
    }

    /** The player sees the character: on the screen and nothing in between. */
    public void thenWaitCharacterVisible(Character<?> c) {
        thenWaitUntil(() -> {
            ServerPlayer p = SprauteRuntime.player();
            Entity e = script.optional(c).orElse(null);
            if (p == null || e == null || p.level() != e.level() || !p.hasLineOfSight(e)) return false;
            Vec3 to = e.getEyePosition().subtract(p.getEyePosition()).normalize();
            return p.getViewVector(1).dot(to) > 0.5;
        });
    }

    // ================================================================== speech

    private void sendSpeech(String name, Integer color, String icon, String text, String hint, boolean think, boolean wait) {
        SprauteNetwork.sendToAll(new Packets.Speech(script.getId(), name, color != null ? color : -1, icon, text, hint, think, wait));
    }

    public void thenSetSpeechEntity(int slot, String name) {
        thenRun(() -> performer.speech().setEntityName(slot, name, "generic_man"));
    }

    public void thenSpeechMessage(int slot, Supplier<Component> hint, Supplier<Component> text, boolean wait) {
        thenRun(() -> {
            SpeechState.Speaker s = performer.speech().get(slot);
            sendSpeech(s.name(), s.color(), s.icon(), text.get().getString(), hint != null ? hint.get().getString() : "", false, wait);
        });
        if (wait) thenWaitForKey("f8");
    }

    public void thenThink(Supplier<Component> text, boolean wait) {
        thenRun(() -> {
            ServerPlayer p = SprauteRuntime.player();
            sendSpeech(p != null ? p.getGameProfile().getName() : "", null, "spraute/head/player/100/0/0", text.get().getString(), "", true, wait);
        });
        if (wait) thenWaitForKey("f8");
    }

    public void clearChat() {
        thenRun(() -> SprauteNetwork.sendToAll(new Packets.SpeechClear()));
    }

    public void thenTitle(Title t) {
        thenRun(() -> {
            for (ServerPlayer p : SprauteRuntime.players()) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(t.fadeIn(), t.stay(), t.fadeOut()));
                if (!t.subtitle().isEmpty()) p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(t.subtitle())));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(t.text())));
            }
        });
    }

    /** A line in the chat. */
    public void sendWebMessage(String text, boolean ignored) {
        thenRun(() -> {
            for (ServerPlayer p : SprauteRuntime.players()) p.sendSystemMessage(Component.literal(text));
        });
    }

    public void sendWebWarning(String text) {
        thenRun(() -> {
            for (ServerPlayer p : SprauteRuntime.players()) p.sendSystemMessage(Component.literal(text).withStyle(net.minecraft.ChatFormatting.YELLOW));
        });
    }

    // ================================================================== characters

    private void withBody(Character<?> c, Consumer<Entity> action) {
        thenRun(() -> script.optional(c).ifPresent(action));
    }

    private void withAnimated(Character<?> c, Consumer<AnimatedEntity> action) {
        withBody(c, e -> {
            if (e instanceof AnimatedEntity ae) action.accept(ae);
        });
    }

    public void positionEntity(Character<?> c, Supplier<Location> at) {
        thenRun(() -> Characters.place(c, at.get()));
    }

    public void removeEntity(Character<?> c) {
        thenRun(() -> Characters.remove(c));
    }

    public void freeEntity(Character<?> c) {
        thenRun(() -> {
            script.optional(c).ifPresent(Behaviours::clear);
            Characters.free(c);
        });
    }

    /** The character stops going anywhere. */
    public void thenClearMovement(Character<?> c) {
        withBody(c, Behaviours::clear);
    }

    public void entityLookAt(Character<?> c, Supplier<Location> at) {
        withBody(c, e -> {
            Location l = at.get();
            if (l == null) return;
            Vec3 target = new Vec3(l.x, l.y, l.z);
            Behaviours.lookOnce(e, target);
            if (e instanceof AnimatedEntity ae) ae.setLookTarget(() -> target);
        });
    }

    public void entityLookAt(Character<?> c, Supplier<Location> at, boolean ignoredA, boolean ignoredB) {
        withBody(c, e -> {
            Location l = at.get();
            if (l != null) Behaviours.lookOnce(e, new Vec3(l.x, l.y, l.z));
        });
    }

    public void thenEntityFollowEntity(Character<?> who, Character<?> target, double speed, double min) {
        withBody(who, e -> Behaviours.follow(e, () -> script.optional(target).orElse(null), speed, min, 1e9));
    }

    public void thenEntityFollowPlayer(Character<?> who, double speed, double min, double max) {
        withBody(who, e -> Behaviours.follow(e, SprauteRuntime::player, speed, min, max));
    }

    public void thenEntityRandomWalk(Character<?> who, double speed, int ignored, double radius) {
        withBody(who, e -> Behaviours.wander(e, speed, radius));
    }

    public void entityMoveSpeed(Character<?> who, double speed) {
        withBody(who, e -> {
            if (e instanceof LivingEntity le) {
                var a = le.getAttribute(Attributes.MOVEMENT_SPEED);
                if (a != null) a.setBaseValue(0.25 * speed);
            }
        });
    }

    public void entitySit(Character<?> who) {
        withAnimated(who, ae -> ae.setSitting(true));
    }

    public void entityStopSit(Character<?> who) {
        withAnimated(who, ae -> ae.setSitting(false));
    }

    public void entityNameOverride(Character<?> who, Supplier<String> name) {
        withBody(who, e -> e.setCustomName(Component.literal(name.get())));
    }

    public void thenSetHeldItem(Character<?> who, ItemStack stack) {
        withBody(who, e -> {
            if (e instanceof LivingEntity le) le.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
        });
    }

    public void setMaxHealth(Character<?> who, float hp) {
        withBody(who, e -> {
            if (e instanceof AnimatedEntity ae) ae.setMaxHealth(hp);
            else if (e instanceof LivingEntity le) {
                var a = le.getAttribute(Attributes.MAX_HEALTH);
                if (a != null) a.setBaseValue(hp);
                le.setHealth(hp);
            }
        });
    }

    public void entityTextureOverride(Character<?> who, ResourceLocation texture) {
        withAnimated(who, ae -> ae.setTextureOverride(texture));
    }

    // ================================================================== animations

    public void setAnimation(Character<?> who, String layer, ConfiguredAnimation ca) {
        withAnimated(who, ae -> ae.startAnimation(layer, ca));
    }

    public void clearAnimations(Character<?> who, float fade) {
        withAnimated(who, ae -> {
            for (String l : ae.getAnimationSystem().getLayerNames()) if (!l.equals("movement")) ae.getAnimationSystem().stopAnimation(l, fade);
        });
    }

    public void overridePredefinedAnimation(Character<?> who, MovementActionType type, ConfiguredAnimation ca) {
        withAnimated(who, ae -> ae.setAnimationOverride(type, ca));
    }

    public void removeOverridePredefined(Character<?> who) {
        withAnimated(who, AnimatedEntity::clearAnimationOverrides);
    }

    /** A built-in gesture (it goes on the head and the body), or the model's own animation of this name. */
    private void gesture(Character<?> who, String name, int times, float speed) {
        withAnimated(who, ae -> {
            Animation a = Anims.gesture(ae, name);
            if (a == null) return;
            ConfiguredAnimation ca = a.configure().transitionTime(0.1f).speed(speed);
            if (times > 1) ca.loopMode(org.sprautestudio.st_addon_spraute.core.anim.LoopMode.LOOP);
            ae.startAnimation("action", ca);
            if (times > 1) Behaviours.afterTicks((int) Math.ceil(a.lengthSeconds() * 20 * times / speed), () -> ae.getAnimationSystem().stopAnimation("action", 0.2f));
        });
    }

    public void nod(Character<?> who, int times) {
        gesture(who, "nod", times, 1);
    }

    public void nope(Character<?> who, int times) {
        gesture(who, "nope", times, 1);
    }

    public void laugh(Character<?> who) {
        gesture(who, "laugh", 1, 1);
    }

    public void laugh(Character<?> who, int times, float speed) {
        gesture(who, "laugh", times, speed);
    }

    public void sigh(Character<?> who) {
        gesture(who, "sigh", 1, 1);
    }

    /** Trembles for this long. */
    public void shake(Character<?> who, int ticks) {
        withAnimated(who, ae -> {
            Animation a = Anims.gesture(ae, "shake");
            if (a == null) return;
            ae.startAnimation("action", a.configure().transitionTime(0.1f).loopMode(org.sprautestudio.st_addon_spraute.core.anim.LoopMode.LOOP));
            Behaviours.afterTicks(ticks, () -> ae.getAnimationSystem().stopAnimation("action", 0.2f));
        });
    }

    // ================================================================== the player and the screen

    public void teleportPlayer(Supplier<Location> at) {
        thenRun(() -> {
            ServerPlayer p = SprauteRuntime.player();
            Location l = at.get();
            if (p == null || l == null) return;
            var level = p.server.getLevel(l.dimension);
            p.teleportTo(level != null ? level : p.serverLevel(), l.x, l.y, l.z, l.yaw, l.pitch);
        });
    }

    public void thenAddEffect(MobEffectInstance effect) {
        thenRun(() -> {
            ServerPlayer p = SprauteRuntime.player();
            if (p != null) p.addEffect(new MobEffectInstance(effect));
        });
    }

    public void playSound(Supplier<BlockPos> at, SoundEvent sound) {
        thenRun(() -> {
            ServerPlayer p = SprauteRuntime.player();
            if (p != null) p.level().playSound(null, at.get(), sound, SoundSource.MASTER, 1, 1);
        });
    }

    public void playerTextureOverride(ResourceLocation texture) {
        thenRun(() -> SprauteNetwork.sendToAll(new Packets.PlayerSkin(texture.toString())));
    }

    public void playerTextureReset() {
        thenRun(() -> SprauteNetwork.sendToAll(new Packets.PlayerSkin("")));
    }

    private void screen(String kind, int a, int b, int c, int argb) {
        thenRun(() -> SprauteNetwork.sendToAll(new Packets.ScreenFx(kind, a, b, c, argb)));
    }

    /** The screen goes black and comes back over this long. */
    public void blackout(int ticks) {
        screen("pulse", ticks / 3, ticks / 3, ticks - ticks / 3 * 2, 0xFF000000);
    }

    public void flash(int ticks) {
        screen("pulse", 0, 0, ticks, 0xFFFFFFFF);
    }

    /** A colour over the screen until changed (alpha 0 — none). */
    public void screenColor(int r, int g, int b, int a) {
        screen("color", 0, 0, 0, (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | b & 255);
    }

    public void close_eyes(int close, int hold, int open) {
        screen("eyes", close, hold, open, 0xFF000000);
    }
}
