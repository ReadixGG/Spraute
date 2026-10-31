package org.sprautestudio.spraute_engine.core.script;

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
import org.sprautestudio.spraute_engine.core.anim.Animation;
import org.sprautestudio.spraute_engine.core.anim.ConfiguredAnimation;
import org.sprautestudio.spraute_engine.core.anim.MovementActionType;
import org.sprautestudio.spraute_engine.core.entity.AnimatedEntity;
import org.sprautestudio.spraute_engine.core.world.Location;
import org.sprautestudio.spraute_engine.net.Packets;
import org.sprautestudio.spraute_engine.net.SprauteNetwork;
import org.sprautestudio.spraute_engine.runtime.Anims;
import org.sprautestudio.spraute_engine.runtime.SprauteRuntime;

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

    private static class Wait extends ScriptPerformerAction {
        private final Runnable begin;
        private final BooleanSupplier done;
        private final String words;

        Wait(ScriptPerformer p, Runnable begin, BooleanSupplier done, String words) {
            super(p);
            this.begin = begin;
            this.done = done;
            this.words = words;
        }

        @Override
        public String describe() {
            return words;
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

    /** From here on this performer's steps are about this player; null — the story's own again. */
    public void thenAs(Supplier<ServerPlayer> who) {
        append(p -> new Run(p, () -> {
            ServerPlayer player = who.get();
            p.asPlayer = player != null ? player.getUUID() : null;
            SprauteRuntime.setAs(p.asPlayer);
        }));
    }

    public void label(String name) {
        if (name.equals(ScriptPerformer.LABEL_START) || name.equals(ScriptPerformer.LABEL_END))
            throw new IllegalArgumentException("Имя метки «" + name + "» зарезервировано");
        if (performer.labels.containsKey(name)) throw new IllegalArgumentException("Метка «" + name + "» объявлена дважды");
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
        append(p -> new Wait(p, null, done, "Ждёт условие"));
    }

    private void waitWith(String words, Runnable begin, BooleanSupplier done) {
        append(p -> new Wait(p, begin, done, words));
    }

    /** A step the performer does not wait for: it keeps going while the next steps run, until a later one finishes. */
    public void thenPassing(Runnable begin, BooleanSupplier done) {
        append(p -> new Wait(p, begin, done, "Идёт параллельно") {
            @Override
            public boolean blocking() {
                return false;
            }
        });
    }

    public void thenDelay(int ticks) {
        thenDelay(() -> ticks);
    }

    public void thenDelay(IntSupplier ticks) {
        int[] left = {0};
        waitWith("Ждёт время", () -> left[0] = ticks.getAsInt(), () -> left[0]-- <= 0);
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
        thenRun(() -> {
            ScriptPerformer p = script.running.remove(name);
            if (p != null) p.stop();
        });
    }

    /** A checkpoint: a reload comes back here. */
    public void saveState(String name) {
        label("__state_" + name);
        thenRun(() -> script.passCheckpoint(name, performer));
    }

    /** Back to a checkpoint: what the story's characters are doing is undone. */
    public void gotoSaveState(String name) {
        thenRun(() -> Characters.rollback(script.getId()));
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
        append(p -> new Wait(p, null, () -> script.getFlags().has(name), "Ждёт флаг " + name));
    }

    public void thenWaitForKey(String key) {
        long[] from = {0};
        waitWith("Ждёт нажатия " + key.toUpperCase(java.util.Locale.ROOT), () -> from[0] = script.keyCount(key), () -> script.keyCount(key) > from[0]);
    }

    /** For a handler: each press is taken once, also those made while its body was running. */
    public void thenWaitForEachKey(String key) {
        long[] seen = {-1};
        waitWith("Ждёт нажатия " + key.toUpperCase(java.util.Locale.ROOT), () -> {
            if (seen[0] < 0) seen[0] = script.keyCount(key);
        }, () -> {
            if (script.keyCount(key) <= seen[0]) return false;
            seen[0]++;
            return true;
        });
    }

    public void thenWaitForTrigger() {
        int[] from = {0};
        waitWith("Ждёт триггер", () -> from[0] = script.triggerSeq, () -> script.triggerSeq > from[0]);
    }

    public void thenAwaitEntityClick(Character<?> c) {
        long[] from = {0};
        waitWith("Ждёт клика по персонажу " + c.getName(), () -> from[0] = Behaviours.clicks(script.optional(c).orElse(null)), () -> Behaviours.clicks(script.optional(c).orElse(null)) > from[0]);
    }

    public void thenAwaitBlockClick(BlockPos pos) {
        long[] from = {0};
        waitWith("Ждёт клика по блоку", () -> from[0] = Behaviours.clicks(pos), () -> Behaviours.clicks(pos) > from[0]);
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

    public void thenWhenPlayerInDimension(net.minecraft.resources.ResourceLocation dim) {
        thenWaitUntil(() -> {
            ServerPlayer p = SprauteRuntime.player();
            return p != null && p.level().dimension().location().equals(dim);
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
        thenWaitCharacterVisible(c, true);
    }

    public void thenWaitCharacterVisible(Character<?> c, boolean lineOfSight) {
        thenWaitUntil(() -> {
            ServerPlayer p = SprauteRuntime.player();
            Entity e = script.optional(c).orElse(null);
            if (p == null || e == null || p.level() != e.level() || lineOfSight && !p.hasLineOfSight(e)) return false;
            Vec3 to = e.getEyePosition().subtract(p.getEyePosition()).normalize();
            return p.getViewVector(1).dot(to) > 0.5;
        });
    }

    // ================================================================== speech

    private void sendSpeech(String name, Integer color, String icon, String text, String hint, boolean think, boolean wait) {
        script.journal.add(new ScriptInstance.Line(name, color != null ? color : -1, icon, text, think));
        SprauteNetwork.sendToAll(new Packets.Speech(script.getId(), name, color != null ? color : -1, icon, text, hint, think, wait, false));
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
        thenRun(() -> {
            script.journal.clear();
            SprauteNetwork.sendToAll(new Packets.SpeechClear(script.getId()));
        });
    }

    public void thenTitle(Title t) {
        thenRun(() -> {
            for (ServerPlayer p : SprauteRuntime.audience()) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(t.fadeIn(), t.stay(), t.fadeOut()));
                if (!t.subtitle().isEmpty()) p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(t.subtitle())));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(t.text())));
            }
        });
    }

    /** A line in the chat. */
    public void sendWebMessage(String text, boolean ignored) {
        thenRun(() -> {
            for (ServerPlayer p : SprauteRuntime.audience()) p.sendSystemMessage(Component.literal(text));
        });
    }

    public void sendWebWarning(String text) {
        thenRun(() -> {
            for (ServerPlayer p : SprauteRuntime.audience()) p.sendSystemMessage(Component.literal(text).withStyle(net.minecraft.ChatFormatting.YELLOW));
        });
    }

    // ================================================================== characters

    /** A part of the character's state: kept, and put back on a new body or when this story takes the body over. */
    private void state(Character<?> c, String key, Consumer<Entity> action) {
        thenRun(() -> keep(c, key, action));
    }

    private void keep(Character<?> c, String key, Consumer<Entity> action) {
        script.note(c.getId() + "|" + key, performer);
        Characters.state(script, c, key, action);
    }

    private void stateAnimated(Character<?> c, String key, Consumer<AnimatedEntity> action) {
        state(c, key, e -> {
            if (e instanceof AnimatedEntity ae) action.accept(ae);
        });
    }

    /** Something done once, if this story acts on the body now. */
    private void act(Character<?> c, Consumer<Entity> action) {
        thenRun(() -> Characters.act(script, c, action));
    }

    private void actAnimated(Character<?> c, Consumer<AnimatedEntity> action) {
        act(c, e -> {
            if (e instanceof AnimatedEntity ae) action.accept(ae);
        });
    }

    public void positionEntity(Character<?> c, Supplier<Location> at) {
        thenRun(() -> Characters.place(script, c, at.get()));
    }

    public void removeEntity(Character<?> c) {
        thenRun(() -> Characters.remove(script, c));
    }

    public void freeEntity(Character<?> c) {
        thenRun(() -> Characters.free(c));
    }

    /** The character stops going anywhere. */
    public void thenClearMovement(Character<?> c) {
        thenRun(() -> {
            Characters.forget(script, c, "move");
            Characters.act(script, c, e -> Behaviours.stop(e, Behaviours.Type.MOVEMENT));
        });
    }

    /** The behaviour of this type ends. */
    public void thenClearBehaviour(Character<?> c, Behaviours.Type type) {
        thenRun(() -> {
            if (type == Behaviours.Type.MOVEMENT) Characters.forget(script, c, "move");
            if (type == Behaviours.Type.LOOK) Characters.forget(script, c, "look");
            Characters.act(script, c, e -> Behaviours.stop(e, type));
        });
    }

    /** Looks at the target and keeps looking until told otherwise; a target that is gone just changes nothing. */
    public void entityLookAt(Character<?> c, Supplier<Location> at, boolean instant) {
        state(c, "look", e -> {
            Supplier<Vec3> target = () -> {
                Location l = at.get();
                return l == null ? null : new Vec3(l.x, l.y, l.z);
            };
            if (e instanceof AnimatedEntity ae) ae.setLookTarget(target, instant);
            else {
                Vec3 v = target.get();
                if (v != null) Behaviours.lookOnce(e, v);
            }
            org.sprautestudio.spraute_engine.path.Walkers.lookAt(e, target);
        });
    }

    /** Waits until the head has turned to the target. */
    public void thenWaitLook(Character<?> c) {
        int[] waited = {0};
        waitWith("Поворачивается к цели", () -> waited[0] = 0, () -> ++waited[0] > 60
                || !(script.optional(c).orElse(null) instanceof AnimatedEntity ae) || ae.lookSettled());
    }

    public void entityStopLook(Character<?> c) {
        thenRun(() -> {
            Characters.forget(script, c, "look");
            Characters.act(script, c, e -> {
                if (e instanceof AnimatedEntity ae) ae.setLookTarget(null, false);
                org.sprautestudio.spraute_engine.path.Walkers.lookAt(e, null);
            });
        });
    }

    public void thenEntityFollowEntity(Character<?> who, Character<?> target, double speed, double min) {
        state(who, "move", e -> Behaviours.follow(e, () -> script.optional(target).orElse(null), speed, min, -1));
    }

    public void thenEntityFollowPlayer(Character<?> who, double speed, double min, double max) {
        state(who, "move", e -> Behaviours.follow(e, SprauteRuntime::player, speed, min, max));
    }

    public void thenEntityRandomWalk(Character<?> who, double speed, int ignored, double radius) {
        state(who, "move", e -> Behaviours.wander(e, speed, radius));
    }

    public void entityMoveSpeed(Character<?> who, double speed) {
        state(who, "speed", e -> {
            if (e instanceof LivingEntity le) {
                var a = le.getAttribute(Attributes.MOVEMENT_SPEED);
                if (a != null) a.setBaseValue(0.25 * speed);
            }
        });
    }

    /** Sits on an invisible seat that comes and goes by itself; at a point (with the body's turn locked) or where it stands. */
    public void entitySit(Character<?> who, Supplier<Location> at) {
        state(who, "sit", e -> Seats.sit(e, at != null ? at.get() : null));
    }

    public void entityStopSit(Character<?> who) {
        thenRun(() -> {
            Characters.forget(script, who, "sit");
            Characters.act(script, who, Seats::stand);
        });
    }

    public void entityNameOverride(Character<?> who, Supplier<String> name) {
        state(who, "name", e -> e.setCustomName(Component.literal(name.get())));
    }

    public void thenSetHeldItem(Character<?> who, ItemStack stack) {
        state(who, "hand", e -> {
            if (e instanceof LivingEntity le) le.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
        });
    }

    public void setMaxHealth(Character<?> who, float hp) {
        state(who, "health", e -> {
            if (e instanceof AnimatedEntity ae) ae.setMaxHealth(hp);
            else if (e instanceof LivingEntity le) {
                var a = le.getAttribute(Attributes.MAX_HEALTH);
                if (a != null) a.setBaseValue(hp);
                le.setHealth(hp);
            }
        });
    }

    public void entityTextureOverride(Character<?> who, ResourceLocation texture) {
        stateAnimated(who, "texture", ae -> ae.setTextureOverride(texture));
    }

    // ================================================================== animations

    public void setAnimation(Character<?> who, String layer, ConfiguredAnimation ca) {
        boolean stays = ca.loopMode() != org.sprautestudio.spraute_engine.core.anim.LoopMode.ONCE;
        thenRun(() -> {
            Characters.forget(script, who, "anim:" + layer);
            Characters.act(script, who, e -> {
                if (e instanceof AnimatedEntity ae) ae.startAnimation(layer, ca);
            });
            // what stays on a layer is part of the state; a clip that plays once is not
            if (stays && !ca.animation().isNull()) keep(who, "anim:" + layer, e -> {
                if (e instanceof AnimatedEntity ae && !ae.getAnimationSystem().getLayer(layer).activeAnimation().id().equals(ca.animation().id())) ae.startAnimation(layer, ca);
            });
        });
    }

    public void clearAnimations(Character<?> who, float fade) {
        thenRun(() -> {
            Characters.forget(script, who, "anim:");
            Characters.act(script, who, e -> {
                if (e instanceof AnimatedEntity ae) for (String l : ae.getAnimationSystem().getLayerNames()) if (!l.equals("movement")) ae.getAnimationSystem().stopAnimation(l, fade);
            });
        });
    }

    public void overridePredefinedAnimation(Character<?> who, MovementActionType type, ConfiguredAnimation ca) {
        stateAnimated(who, "gait:" + type, ae -> ae.setAnimationOverride(type, ca));
    }

    public void removeOverridePredefined(Character<?> who) {
        thenRun(() -> {
            Characters.forget(script, who, "gait:");
            Characters.act(script, who, e -> {
                if (e instanceof AnimatedEntity ae) ae.clearAnimationOverrides();
            });
        });
    }

    /** A built-in gesture (it goes on the head and the body), or the model's own animation of this name. */
    private void gesture(Character<?> who, String name, int times, float speed) {
        actAnimated(who, ae -> {
            Animation a = Anims.gesture(ae, name);
            if (a == null) return;
            ConfiguredAnimation ca = a.configure().transitionTime(0.1f).speed(speed);
            if (times > 1) ca.loopMode(org.sprautestudio.spraute_engine.core.anim.LoopMode.LOOP);
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

    public void refuse(Character<?> who, int times) {
        gesture(who, "refuse", times, 1);
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
        actAnimated(who, ae -> {
            Animation a = Anims.gesture(ae, "shake");
            if (a == null) return;
            ae.startAnimation("action", a.configure().transitionTime(0.1f).loopMode(org.sprautestudio.spraute_engine.core.anim.LoopMode.LOOP));
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
        thenRun(() -> org.sprautestudio.spraute_engine.core.system.PlayerTexture.set(SprauteRuntime.player(), texture));
    }

    public void playerTextureReset() {
        thenRun(() -> org.sprautestudio.spraute_engine.core.system.PlayerTexture.set(SprauteRuntime.player(), null));
    }

    private void screen(String kind, int a, int b, int c, int argb) {
        thenRun(() -> SprauteNetwork.sendToAll(new Packets.ScreenFx(kind, a, b, c, argb)));
    }

    /** Black at once, held this long, then a second to fade away. */
    public void blackout(int ticks) {
        screen("pulse", 0, ticks, 20, 0xFF000000);
    }

    /** White: up and away, about a second each unless told otherwise. */
    public void flash(int ticks) {
        screen("pulse", ticks / 2, 0, ticks - ticks / 2, 0xFFFFFFFF);
    }

    /** A filled screen: its colour, how it comes, stays and goes; under the chat, speech stays readable. */
    public void screenFill(int argb, int in, int hold, int out, boolean underChat) {
        screen(underChat ? "pulse_under" : "pulse", in, hold, out, argb);
    }

    /** A colour over the screen until changed (alpha 0 — none). */
    public void screenColor(int r, int g, int b, int a) {
        screen("color", 0, 0, 0, (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | b & 255);
    }

    public void close_eyes(int close, int hold, int open) {
        screen("eyes", close, hold, open, 0xFF000000);
    }
}
