package org.sprautestudio.spraute_engine.runtime;

import org.sprautestudio.spraute_engine.core.entity.AnimatedEntity;
import org.sprautestudio.spraute_engine.core.anim.MovementActionType;
import org.sprautestudio.spraute_engine.core.script.ScriptPerformer;
import org.sprautestudio.spraute_engine.core.anim.Animation;
import org.sprautestudio.spraute_engine.core.anim.LoopMode;
import org.sprautestudio.spraute_engine.core.anim.ConfiguredAnimation;
import org.sprautestudio.spraute_engine.core.script.ScriptPerformerBuilder;
import org.sprautestudio.spraute_engine.core.script.Character;
import org.sprautestudio.spraute_engine.core.script.Title;
import org.sprautestudio.spraute_engine.core.world.Location;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.sprautestudio.spraute_engine.lang.Ast.*;
import org.sprautestudio.spraute_engine.lang.Specs;
import org.sprautestudio.spraute_engine.platform.Platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Translates .sst statements into calls on {@link ScriptPerformerBuilder}.
 * Control flow (if/while/repeat/choice) is lowered into labels plus dynamic gotos.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class Emitter {
    private final SprauteScriptInstance script;
    private final Eval eval;
    private int labelCounter;
    /** Speech slot assigned to a character or a literal speaker name. */
    private final Map<String, Integer> speakerSlots = new HashMap<>();
    private int nextSlot;

    Emitter(SprauteScriptInstance script) {
        this.script = script;
        this.eval = script.eval;
    }

    void emitProgram(ScriptPerformerBuilder b, List<Stmt> body) {
        statements(b, body);
        // with event handlers the story stays alive and keeps listening, until "stop"
        if (hasHandlers(body)) b.thenWaitUntil(() -> false);
        finish(b);
    }

    /** True when an "on" handler appears anywhere in the statements. */
    private static boolean hasHandlers(List<?> stmts) {
        for (Object s : stmts) {
            if (s instanceof OnStmt) return true;
            if (s == null) continue;
            for (java.lang.reflect.Field f : s.getClass().getFields()) {
                try {
                    Object v = f.get(s);
                    if (v instanceof List<?> l && !l.isEmpty() && hasHandlers(l)) return true;
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------- functions & async

    /**
     * Per-performer state. Labels are local to a performer, so every performer gets
     * its own copy of the functions it calls, appended after its body behind a jump.
     */
    private final class Ctx {
        final String id;
        final String end = freshLabel("end");
        final java.util.LinkedHashSet<String> used = new java.util.LinkedHashSet<>();

        Ctx(String id) {
            this.id = id;
        }
    }

    private final Map<ScriptPerformerBuilder, Ctx> contexts = new java.util.IdentityHashMap<>();

    private Ctx ctx(ScriptPerformerBuilder b) {
        return contexts.computeIfAbsent(b, k -> new Ctx("p" + contexts.size()));
    }

    private static String fnLabel(String name) {
        return "__fn_" + name;
    }

    /** Appends the bodies of all functions used by this performer (transitively). */
    private void finish(ScriptPerformerBuilder b) {
        Ctx ctx = ctx(b);
        if (ctx.used.isEmpty()) return;
        b.gotoLabel(ctx.end, true);
        java.util.Set<String> emitted = new java.util.HashSet<>();
        while (true) {
            String next = null;
            for (String f : ctx.used) {
                if (!emitted.contains(f)) {
                    next = f;
                    break;
                }
            }
            if (next == null) break;
            emitted.add(next);
            FuncDecl f = script.deployed.program.func(next);
            b.label(fnLabel(next));
            java.util.ArrayDeque<String[]> outer = new java.util.ArrayDeque<>(loops);
            loops.clear();
            if (f != null) statements(b, f.body);
            loops.addAll(outer);
            returnJump(b, ctx);
        }
        b.label(ctx.end);
    }

    private void returnJump(ScriptPerformerBuilder b, Ctx ctx) {
        b.gotoLabel(() -> {
            java.util.Deque<String> d = script.callStacks.get(ctx.id);
            return d == null || d.isEmpty() ? ctx.end : d.pop();
        }, true);
    }

    private void call(ScriptPerformerBuilder b, CallStmt c) {
        FuncDecl f = script.deployed.program.func(c.name);
        if (f == null) {
            b.thenRun(() -> SprauteRuntime.get().report(script.getId(), "Функция '" + c.name + "' не объявлена"));
            return;
        }
        Ctx ctx = ctx(b);
        ctx.used.add(f.name);
        String ret = freshLabel("ret");
        List<Expr> args = c.args;
        b.thenRun(() -> {
            java.util.Deque<String> d = script.callStacks.computeIfAbsent(ctx.id, k -> new java.util.ArrayDeque<>());
            if (d.size() >= 256) {
                SprauteRuntime.get().report(script.getId(), "Слишком глубокая рекурсия в функции " + f.name);
                d.clear();
            }
            d.push(ret);
            Object[] values = new Object[f.params.size()];
            for (int i = 0; i < values.length && i < args.size(); i++) {
                try {
                    values[i] = eval.eval(args.get(i));
                } catch (SprauteRuntimeException ex) {
                    SprauteRuntime.get().report(script.getId(), "Аргумент " + f.params.get(i) + ": " + ex.getMessage());
                    values[i] = 0.0;
                }
            }
            for (int i = 0; i < values.length; i++) if (values[i] != null) script.vars.put(f.params.get(i), values[i]);
        });
        b.gotoLabel(fnLabel(f.name), true);
        b.label(ret);
    }

    static String runningFlag(String branch) {
        return "__run_" + branch;
    }

    /** Starts {@code body} as a named parallel performer and tracks whether it is still running. */
    private void branch(ScriptPerformerBuilder b, String name, java.util.function.Consumer<ScriptPerformerBuilder> body) {
        ScriptPerformerBuilder sub = ScriptPerformerBuilder.builder(script);
        // a background branch does not see the loops around it
        java.util.ArrayDeque<String[]> outer = new java.util.ArrayDeque<>(loops);
        loops.clear();
        try {
            body.accept(sub);
        } finally {
            loops.clear();
            loops.addAll(outer);
        }
        finish(sub);
        String flag = runningFlag(name);
        sub.thenRun(() -> script.vars.put(flag, false));
        ScriptPerformer performer = sub.build();
        b.thenRun(() -> script.vars.put(flag, true));
        b.createParallelPerformer(name, performer);
    }

    /** Loops being emitted: where continue and break jump ({continue, break}). */
    private final java.util.ArrayDeque<String[]> loops = new java.util.ArrayDeque<>();

    private String freshLabel(String hint) {
        return "__sp_" + hint + "_" + (labelCounter++);
    }

    private void statements(ScriptPerformerBuilder b, List<Stmt> body) {
        for (Stmt s : body) statement(b, s);
    }

    private void statement(ScriptPerformerBuilder b, Stmt s) {
        if (s instanceof LabelStmt l) {
            b.label(l.name);
        } else if (s instanceof GotoStmt g) {
            b.gotoLabel(g.name, true);
        } else if (s instanceof StopStmt) {
            b.finish(true);
        } else if (s instanceof SetStmt set && set.prop != null) {
            Ident targetId = new Ident();
            targetId.name = set.name;
            b.thenRun(guard(() -> {
                Object target = eval.ref(targetId);
                Object value = eval.ref(set.value);
                if (target instanceof EntityRef er) {
                    if (!set.op.equals("=")) {
                        Object old = WorldApi.get(er, set.prop);
                        value = set.op.equals("-=") ? Eval.num(old) - Eval.num(value) : Eval.num(old) + Eval.num(value);
                    }
                    WorldApi.set(er, set.prop, value);
                } else if (target instanceof java.util.Map<?, ?> m) {
                    @SuppressWarnings("unchecked") java.util.Map<String, Object> map = (java.util.Map<String, Object>) m;
                    if (!set.op.equals("=")) {
                        Object old = map.getOrDefault(set.prop, 0.0);
                        value = set.op.equals("-=") ? Eval.num(old) - Eval.num(value)
                                : old instanceof String || value instanceof String ? Eval.str(old) + Eval.str(value) : Eval.num(old) + Eval.num(value);
                    }
                    map.put(set.prop, value);
                } else {
                    throw new SprauteRuntimeException("set " + set.name + "." + set.prop + ": " + set.name + " — не сущность и не словарь");
                }
            }));
        } else if (s instanceof SetStmt set && set.index != null) {
            b.thenRun(guard(() -> {
                Object container = script.vars.get(set.name);
                Object key = eval.eval(set.index);
                Object value = eval.eval(set.value);
                if (!set.op.equals("=")) {
                    Object old = Values.index(container, key);
                    value = set.op.equals("-=") ? Eval.num(old) - Eval.num(value)
                            : old instanceof String || value instanceof String ? Eval.str(old) + Eval.str(value) : Eval.num(old) + Eval.num(value);
                }
                Values.setIndex(container, key, value);
            }));
        } else if (s instanceof SetStmt set) {
            b.thenRun(guard(() -> {
                Object value = eval.eval(set.value);
                switch (set.op) {
                    case "+=" -> {
                        Object old = script.vars.get(set.name);
                        value = old instanceof String || value instanceof String
                                ? Eval.str(old) + Eval.str(value) : Eval.num(old) + Eval.num(value);
                    }
                    case "-=" -> value = Eval.num(script.vars.get(set.name)) - Eval.num(value);
                    default -> {
                    }
                }
                script.vars.put(set.name, value);
            }));
        } else if (s instanceof IfStmt i) {
            ifStmt(b, i);
        } else if (s instanceof WhileStmt w) {
            String start = freshLabel("while"), body = freshLabel("do"), end = freshLabel("wend");
            b.label(start);
            b.gotoLabel(cond(w.cond, body, end), true);
            b.label(body);
            loops.push(new String[]{start, end});
            statements(b, w.body);
            loops.pop();
            b.gotoLabel(start, true);
            b.label(end);
        } else if (s instanceof RepeatStmt r) {
            String counter = "__repeat_" + (labelCounter++);
            String start = freshLabel("rep"), body = freshLabel("rdo"), end = freshLabel("rend");
            b.thenRun(guard(() -> script.vars.put(counter, Math.floor(Eval.num(eval.eval(r.count))))));
            b.label(start);
            b.gotoLabel(() -> Eval.num(script.vars.getOrDefault(counter, 0.0)) > 0 ? body : end, true);
            b.label(body);
            b.thenRun(() -> script.vars.put(counter, Eval.num(script.vars.getOrDefault(counter, 0.0)) - 1));
            loops.push(new String[]{start, end});
            statements(b, r.body);
            loops.pop();
            b.gotoLabel(start, true);
            b.label(end);
        } else if (s instanceof AsStmt as) {
            b.thenAs(() -> {
                Object who = eval.ref(as.who);
                net.minecraft.world.entity.Entity e = who instanceof EntityRef r ? r.get() : null;
                ServerPlayer p = e instanceof ServerPlayer sp ? sp : who instanceof String name ? eval.playerNamed(name) : null;
                if (p == null) throw new SprauteRuntimeException("as: нужен игрок, а не " + who);
                return p;
            });
            statements(b, as.body);
            b.thenAs(() -> null);
        } else if (s instanceof ForStmt f) {
            int n = labelCounter++;
            String items = "__for_" + n, idx = "__fori_" + n;
            String start = freshLabel("for"), body = freshLabel("fdo"), end = freshLabel("fend");
            b.thenRun(guard(() -> {
                script.vars.put(items, Values.iterable(eval.eval(f.iter)));
                script.vars.put(idx, 0.0);
            }));
            b.label(start);
            b.gotoLabel(() -> {
                Object l = script.vars.get(items);
                int i = (int) Eval.num(script.vars.getOrDefault(idx, 0.0));
                if (!(l instanceof List<?> list) || i >= list.size()) return end;
                script.vars.put(f.var, list.get(i));
                script.vars.put(idx, i + 1.0);
                return body;
            }, true);
            b.label(body);
            loops.push(new String[]{start, end});
            statements(b, f.body);
            loops.pop();
            b.gotoLabel(start, true);
            b.label(end);
            b.thenRun(() -> {
                script.vars.remove(items);
                script.vars.remove(idx);
            });
        } else if (s instanceof EveryStmt ev) {
            branch(b, "__every_" + (labelCounter++), sub -> {
                String loop = freshLabel("every");
                int[] left = {0};
                sub.label(loop);
                statements(sub, ev.body);
                sub.thenRun(() -> {
                    try {
                        left[0] = Math.max(1, (int) Math.round(Eval.num(eval.eval(ev.interval))));
                    } catch (SprauteRuntimeException ex) {
                        SprauteRuntime.get().report(script.getId(), "every: " + ex.getMessage());
                        left[0] = 20;
                    }
                });
                sub.thenWaitUntil(() -> --left[0] <= 0);
                sub.gotoLabel(loop, true);
            });
        } else if (s instanceof BreakStmt) {
            if (!loops.isEmpty()) b.gotoLabel(loops.peek()[1], true);
        } else if (s instanceof ContinueStmt) {
            if (!loops.isEmpty()) b.gotoLabel(loops.peek()[0], true);
        } else if (s instanceof MethodStmt ms) {
            Ident targetId = new Ident();
            targetId.name = ms.target;
            b.thenRun(guard(() -> {
                boolean entity = ms.target.equals("player") || script.characters.containsKey(ms.target)
                        || script.vars.get(ms.target) instanceof EntityRef;
                if (entity) {
                    List<Object> args = new ArrayList<>();
                    for (Expr x : ms.args) args.add(eval.ref(x));
                    Object t = eval.ref(targetId);
                    if (t instanceof EntityRef er) WorldApi.call(er, ms.method, args);
                    return;
                }
                Object target = script.vars.get(ms.target);
                // a variable still holding its start value becomes a list on the first add
                if (!(target instanceof List<?>) && !(target instanceof java.util.Map<?, ?>)
                        && (ms.method.equals("add") || ms.method.equals("push") || ms.method.equals("insert"))) {
                    target = new ArrayList<>();
                    script.vars.put(ms.target, target);
                }
                List<Object> args = new ArrayList<>();
                for (Expr x : ms.args) args.add(eval.ref(x));
                Values.method(target, ms.method, args);
            }));
        } else if (s instanceof ChoiceStmt c) {
            choice(b, c);
        } else if (s instanceof ParallelStmt ps) {
            String name = ps.name != null ? ps.name : "__async_" + (labelCounter++);
            branch(b, name, sub -> statements(sub, ps.body));
        } else if (s instanceof CallStmt c) {
            if (c.async) branch(b, c.as != null ? c.as : "__async_" + (labelCounter++), sub -> call(sub, c));
            else call(b, c);
        } else if (s instanceof OnStmt on) {
            // a background loop: wait for the event, run the handler, repeat
            branch(b, "__on_" + (labelCounter++), sub -> {
                String loop = freshLabel("on");
                sub.label(loop);
                handlerWait = true;
                try {
                    command(sub, on.event);
                } finally {
                    handlerWait = false;
                }
                // a press of a key is an instant: speech in its handler must not hold the next press back until F8
                boolean outer = keyHandler;
                keyHandler = on.event.key.equals("wait key");
                try {
                    statements(sub, on.body);
                } finally {
                    keyHandler = outer;
                }
                sub.thenAs(() -> null);
                sub.thenDelay(1);
                sub.gotoLabel(loop, true);
            });
        } else if (s instanceof SwitchStmt sw) {
            switchStmt(b, sw);
        } else if (s instanceof ReturnStmt) {
            returnJump(b, ctx(b));
        } else if (s instanceof AwaitStmt a) {
            List<String> names = new ArrayList<>(a.names);
            b.thenWaitUntil(() -> {
                for (String n : names) if (Eval.truthy(script.vars.get(runningFlag(n)))) return false;
                return true;
            });
        } else if (s instanceof CancelStmt cs) {
            b.removeParallelPerformer(cs.name);
            b.thenRun(() -> script.vars.put(runningFlag(cs.name), false));
        } else if (s instanceof CommandStmt cmd) {
            command(b, cmd);
        }
    }

    private void switchStmt(ScriptPerformerBuilder b, SwitchStmt sw) {
        String tmp = "__switch_" + (labelCounter++);
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < sw.cases.size(); i++) labels.add(freshLabel("case"));
        String otherwise = freshLabel("default"), end = freshLabel("esac");
        b.thenRun(guard(() -> script.vars.put(tmp, eval.eval(sw.value))));
        b.gotoLabel(() -> {
            Object v = script.vars.get(tmp);
            for (int i = 0; i < sw.cases.size(); i++) {
                for (Expr e : sw.cases.get(i).values) {
                    try {
                        if (Eval.same(v, eval.eval(e))) return labels.get(i);
                    } catch (SprauteRuntimeException ex) {
                        SprauteRuntime.get().report(script.getId(), "switch: " + ex.getMessage());
                    }
                }
            }
            return otherwise;
        }, true);
        for (int i = 0; i < sw.cases.size(); i++) {
            b.label(labels.get(i));
            statements(b, sw.cases.get(i).body);
            b.gotoLabel(end, true);
        }
        b.label(otherwise);
        if (sw.otherwise != null) statements(b, sw.otherwise);
        b.label(end);
    }

    private void ifStmt(ScriptPerformerBuilder b, IfStmt i) {
        String then = freshLabel("then"), otherwise = freshLabel("else"), end = freshLabel("fi");
        b.gotoLabel(cond(i.cond, then, otherwise), true);
        b.label(then);
        statements(b, i.then);
        b.gotoLabel(end, true);
        b.label(otherwise);
        if (i.otherwise != null) statements(b, i.otherwise);
        b.label(end);
    }

    private Supplier<String> cond(Expr e, String ifTrue, String ifFalse) {
        return () -> {
            try {
                return Eval.truthy(eval.eval(e)) ? ifTrue : ifFalse;
            } catch (SprauteRuntimeException ex) {
                SprauteRuntime.get().report(script.getId(), "Ошибка в условии: " + ex.getMessage());
                return ifFalse;
            }
        };
    }

    /** Pace of move and path without a speed: a calm walk. */
    private static final double DEFAULT_SPEED = 0.85;
    /** move / path: 1 — as fast as a player walks, the same for every character. */
    private static final double WALK_SPEED = 1;

    /** A character walks through points (one or more); the story waits until it is there. */
    private void walkRoute(ScriptPerformerBuilder b, Character ch, Supplier<List<Location>> route, Expr speedE) {
        b.thenClearMovement(ch);
        b.thenRun(guard(() -> {
            net.minecraft.world.entity.Entity e = script.optional((Character<?>) ch).orElse(null);
            if (e == null || !script.controls(ch)) return;
            var d = new org.sprautestudio.spraute_engine.path.PathDef();
            for (Location l : route.get()) if (l != null) d.points.add(new org.sprautestudio.spraute_engine.path.PathDef.Point(l.x, l.y, l.z));
            d.speed = Math.max(0.05, number(speedE, WALK_SPEED));
            org.sprautestudio.spraute_engine.path.Walkers.start(e, "", d, 0, "once", 0);
        }));
        b.thenWaitUntil(() -> !org.sprautestudio.spraute_engine.path.Walkers.walking(
                (net.minecraft.world.entity.Entity) script.optional((Character<?>) ch).orElse(null)));
    }

    private void choice(ScriptPerformerBuilder b, ChoiceStmt c) {
        ownChoice(b, c);
    }

    /** The engine's own choice menu: the client shows it and sends the answer back. */
    private void ownChoice(ScriptPerformerBuilder b, ChoiceStmt c) {
        Character<?> owner = character(c.character);
        String name = c.character;
        List<Expr> texts = new ArrayList<>();
        for (ChoiceOption o : c.options) texts.add(o.text);
        Expr nodeEmotion = c.emotion;
        List<String> branches = new ArrayList<>();
        for (int i = 0; i < c.options.size(); i++) branches.add(freshLabel("opt" + (i + 1)));
        String end = freshLabel("choice_end");
        b.thenRun(() -> {
            script.lastAnswer = 0;
            script.choiceSeq++;
            ServerPlayer p = SprauteRuntime.player();
            if (p == null) return;
            script.choicePlayer = p.getUUID();
            net.minecraft.world.entity.Entity en = script.optional(owner).orElse(null);
            String modelKey = script.characterModels.get(name);
            if (modelKey != null && en instanceof org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity me
                    && !modelKey.equals(me.model())) me.setModel(modelKey);
            List<String> answers = new ArrayList<>(), emotions = new ArrayList<>();
            List<Boolean> locked = new ArrayList<>(), centered = new ArrayList<>();
            for (Expr t : texts) answers.add(text(t));
            for (ChoiceOption o : c.options) {
                locked.add(o.locked != null && Eval.truthy(eval.eval(o.locked)));
                emotions.add(o.emotion != null ? emotionName(o.emotion) : "");
                centered.add(o.center);
            }
            var picture = script.portraits.get(name);
            if (en != null) script.openDialog(en, c.anims != null ? List.of(text(c.anims).split(";")) : List.of());
            String face = nodeEmotion != null ? emotionName(nodeEmotion) : "NEUTRAL";
            if (en instanceof AnimatedEntity ae) Anims.showEmotion(ae, face);
            org.sprautestudio.spraute_engine.net.SprauteNetwork.sendTo(p, new org.sprautestudio.spraute_engine.net.Packets.ChoiceOpen(
                    script.getId(), script.choiceSeq, owner.getName(), text(c.text), answers, en != null ? en.getId() : -1, locked, emotions, face, centered, picture != null ? picture.toString() : ""));
        });
        b.thenWaitUntil(() -> script.lastAnswer != 0);
        b.gotoLabel(() -> {
            int a = script.lastAnswer;
            return a >= 1 && a <= branches.size() ? branches.get(a - 1) : end;
        }, true);
        for (int i = 0; i < c.options.size(); i++) {
            b.label(branches.get(i));
            statements(b, c.options.get(i).body);
            b.gotoLabel(end, true);
        }
        b.label(end);
    }

    // ------------------------------------------------------------------ commands

    private void command(ScriptPerformerBuilder b, CommandStmt c) {
        switch (c.key) {
            // speech
            case "speaker" -> {
                int slot = (int) Eval.num(eval.eval(c.arg("slot")));
                Expr who = c.arg("who");
                if (who instanceof Ident id && script.characters.containsKey(id.name)) {
                    speakerSlots.put("char:" + id.name, slot);
                    speechEntity(b, slot, id.name);
                } else if (who instanceof Ident id && id.name.equals("player")) {
                    speakerSlots.put("player", slot);
                    speechPlayer(b, slot);
                } else {
                    String name = text(who);
                    speakerSlots.put("name:" + name, slot);
                    b.thenSetSpeechEntity(slot, name);
                }
            }
            case "say" -> {
                if (c.arg("who") instanceof Ident sid && script.characters.containsKey(sid.name)) headSync(b, sid.name);
                int slot = speakerSlot(b, c.arg("who"));
                Expr text = c.arg("text");
                boolean wait = c.arg("nowait") == null && !keyHandler;
                Supplier<Component> hint = wait && org.sprautestudio.spraute_engine.engine.EngineServer.config().chatLog.f8Hint
                        ? () -> Component.literal("▸ F8") : null;
                b.thenSpeechMessage(slot, hint, (Supplier<Component>) () -> Component.literal(safeText(text)), wait);
            }
            case "think" -> {
                Expr text = c.arg("text");
                b.thenThink((Supplier<Component>) () -> Component.literal(safeText(text)), c.arg("nowait") == null && !keyHandler);
            }
            case "clearchat" -> b.clearChat();
            case "title" -> {
                Title.Builder tb = Title.builder(text(c.arg("text")));
                if (c.arg("subtitle") != null) tb.subtitle(text(c.arg("subtitle")));
                if (c.arg("fadeIn") != null) tb.times(ticks(c.arg("fadeIn")), ticks(c.arg("stay")), ticks(c.arg("fadeOut")));
                b.thenTitle(tb.build());
            }
            case "message" -> {
                Expr text = c.arg("text");
                b.thenRun(guard(() -> {
                    Component msg = Component.literal(safeText(text));
                    for (ServerPlayer p : SprauteRuntime.audience()) p.sendSystemMessage(msg);
                }));
            }
            case "web" -> b.sendWebMessage(text(c.arg("text")), false);
            case "warn" -> b.sendWebWarning(text(c.arg("text")));

            // waiting
            case "wait" -> {
                Expr t = c.arg("time");
                if (t instanceof DurationLit || t instanceof NumberLit) b.thenDelay(ticks(t));
                else b.thenDelay(() -> ticksSafe(t));
            }
            case "wait random" -> b.thenDelayRandom(ticks(c.arg("min")), ticks(c.arg("max")), script.getRandom());
            case "wait until" -> {
                Expr cond = c.arg("condition");
                b.thenWaitUntil(() -> {
                    try {
                        return Eval.truthy(eval.eval(cond));
                    } catch (SprauteRuntimeException ex) {
                        SprauteRuntime.get().report(script.getId(), "Ошибка в wait until: " + ex.getMessage());
                        return true;
                    }
                });
            }
            case "wait flag" -> b.thenWaitForFlag(text(c.arg("name")));
            case "wait key" -> {
                Expr k = c.arg("key");
                String name = k instanceof Ident id ? id.name : text(k);
                if (handlerWait) b.thenWaitForEachKey(name.toLowerCase(java.util.Locale.ROOT));
                else b.thenWaitForKey(name.toLowerCase(java.util.Locale.ROOT));
            }
            case "wait trigger" -> b.thenWaitForTrigger();
            case "wait chat" -> {
                List<Expr> words = new ArrayList<>();
                for (Arg a : c.args) if ("words".equals(a.param)) words.add(a.value);
                waitLog(b, "chat", () -> {
                    List<String> ws = new ArrayList<>();
                    for (Expr w : words) ws.add(text(w).trim().toLowerCase(java.util.Locale.ROOT));
                    return d -> {
                        String msg = String.valueOf(d.get("text")).trim().toLowerCase(java.util.Locale.ROOT);
                        if (ws.isEmpty()) return true;
                        for (String w : ws) {
                            if (msg.equals(w) || (" " + msg.replaceAll("[^\\p{L}\\p{N}]+", " ") + " ").contains(" " + w + " ")) return true;
                        }
                        return false;
                    };
                });
            }
            case "wait click" -> b.thenAwaitEntityClick(character(c.arg("who")));
            case "wait clickblock" -> b.thenAwaitBlockClick(blockPos(c.arg("pos")));
            case "wait near" -> {
                Expr target = c.arg("target");
                double radius = c.arg("radius") != null ? Eval.num(eval.eval(c.arg("radius"))) : 3.0;
                if (target instanceof Ident id && script.characters.containsKey(id.name)) {
                    b.thenWhenPlayerCloseTo(character(id.name), radius);
                } else {
                    b.thenWhenPlayerCloseTo(locationSupplier(target), radius);
                }
            }
            case "wait lookat" -> {
                Expr target = c.arg("target");
                b.thenWaitUntil(() -> {
                    ServerPlayer p = SprauteRuntime.player();
                    if (p == null) return false;
                    try {
                        return Raycast.looksAt(new EntityRef(p), eval.ref(target), 64);
                    } catch (SprauteRuntimeException ex) {
                        return false; // the target is not in the world yet
                    }
                });
            }
            case "wait enter" -> b.thenWhenPlayerEnterBox(box(c.arg("from"), c.arg("to")));
            case "wait exit" -> b.thenWhenPlayerExitBox(box(c.arg("from"), c.arg("to")));
            case "wait visible" -> b.thenWaitCharacterVisible(character(c.arg("who")), c.arg("nosight") == null);

            // characters
            case "spawn" -> {
                Character ch = character(c.arg("who"));
                b.positionEntity(ch, locationSupplier(c.arg("pos")));
                // the next lines (set mage.health…) need the body: wait until it is in the world (2 s at most)
                int[] waited = {0};
                b.thenRun(() -> waited[0] = 0);
                b.thenWaitUntil(() -> script.optional((Character<?>) ch).isPresent() || ++waited[0] > 40);
                String hname = c.arg("who") instanceof Ident hid ? hid.name : "";
                for (org.sprautestudio.spraute_engine.lang.Ast.CharacterDecl d : script.deployed.program.characters) {
                    if (!d.name.equals(hname) || d.health == null) continue;
                    Expr hpE = d.health;
                    b.thenRun(guard(() -> {
                        Object en = script.optional((Character<?>) ch).orElse(null);
                        float hp = (float) Math.max(1, number(hpE, 20));
                        if (en instanceof org.sprautestudio.spraute_engine.core.entity.AnimatedEntity ae) ae.setMaxHealth(hp);
                        else if (en instanceof net.minecraft.world.entity.LivingEntity le) {
                            var at = le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                            if (at != null) at.setBaseValue(hp);
                            le.setHealth(hp);
                        }
                    }));
                }
                // the declaration's hitbox, once the body is in the world
                String cname = c.arg("who") instanceof Ident id ? id.name : "";
                for (org.sprautestudio.spraute_engine.lang.Ast.CharacterDecl d : script.deployed.program.characters) {
                    if (!d.name.equals(cname) || d.hitbox == null) continue;
                    Expr hb = d.hitbox;
                    b.thenRun(guard(() -> org.sprautestudio.spraute_engine.hitbox.Hitboxes.applyWhenReady(
                            () -> (net.minecraft.world.entity.Entity) script.optional(ch).orElse(null), List.of(text(hb)))));
                }
            }
            case "tp" -> b.positionEntity(character(c.arg("who")), locationSupplier(c.arg("pos")));
            case "despawn" -> b.removeEntity(character(c.arg("who")));
            case "move" -> {
                // the same walking as paths: one speed for everyone, slabs walked up, stuck ones put in place
                Supplier<Location> to = locationSupplier(c.arg("target"));
                walkRoute(b, character(c.arg("who")), () -> List.of(to.get()), c.arg("speed"));
            }
            case "walk" -> {
                Character ch = character(c.arg("who"));
                Expr pathE = c.arg("path"), speedE = c.arg("speed"), fromE = c.arg("from");
                boolean loop = c.arg("loop") != null, ping = c.arg("pingpong") != null, nowait = c.arg("nowait") != null;
                b.thenClearMovement(ch);
                boolean[] endless = {false};
                b.thenRun(guard(() -> {
                    net.minecraft.world.entity.Entity e = script.optional((Character<?>) ch).orElse(null);
                    if (e == null) throw new SprauteRuntimeException("Персонажа нет в мире — сначала spawn");
                    if (!script.controls(ch)) return;
                    String n = text(pathE);
                    String json = SprauteRuntime.get().gui(script.deployed.project, "path:" + n);
                    if (json == null) {
                        // the same name in other letters case
                        var m = SprauteRuntime.get().allGuis().get(script.deployed.project);
                        if (m != null) for (var en : m.entrySet()) if (en.getKey().equalsIgnoreCase("path:" + n)) json = en.getValue();
                    }
                    if (json == null) throw new SprauteRuntimeException("Путь «" + n + "» не найден в папке «Пути» проекта");
                    var d = org.sprautestudio.spraute_engine.path.PathDef.fromJson(json);
                    if (d.points.isEmpty()) throw new SprauteRuntimeException("В пути «" + n + "» нет точек");
                    String mode = loop ? "loop" : ping ? "pingpong" : "";
                    endless[0] = !(mode.isEmpty() ? d.mode : mode).equals("once");
                    int from = fromE == null ? 0 : (int) Math.round(number(fromE, 1)) - 1;
                    org.sprautestudio.spraute_engine.path.Walkers.start(e, n, d, speedE == null ? 0 : number(speedE, 0), mode, from);
                }));
                if (!nowait) b.thenWaitUntil(() -> endless[0] || !org.sprautestudio.spraute_engine.path.Walkers.walking(
                        (net.minecraft.world.entity.Entity) script.optional((Character<?>) ch).orElse(null)));
            }
            case "stopwalk" -> {
                Character ch = character(c.arg("who"));
                b.thenClearMovement(ch);
            }
            case "path" -> {
                List<Expr> points = new ArrayList<>();
                for (Arg a : c.args) if ("points".equals(a.param)) points.add(a.value);
                Supplier<List<Location>> route = () -> {
                    List<Location> list = new ArrayList<>();
                    for (Expr p : points) list.add(positionSafe(p));
                    return list;
                };
                walkRoute(b, character(c.arg("who")), route, c.arg("speed"));
            }
            case "look" -> {
                Character who = character(c.arg("who"));
                // aim at the eyes: an entity's position is at its feet
                b.entityLookAt(who, eyesSupplier(c.arg("target")), c.arg("instant") != null);
                if (c.arg("wait") != null) b.thenWaitLook(who);
            }
            case "stoplook" -> b.entityStopLook(character(c.arg("who")));
            case "follow" -> {
                Expr target = c.arg("target");
                if (target instanceof Ident id && script.characters.containsKey(id.name)) {
                    b.thenEntityFollowEntity(character(c.arg("who")), character(id.name), number(c.arg("speed"), 1.0), number(c.arg("min"), 2.0));
                } else {
                    // Spraute teleports a follower who lags behind more than max: noteleport — never
                    double max = c.arg("noteleport") != null ? 1e9 : number(c.arg("max"), 6.0);
                    b.thenEntityFollowPlayer(character(c.arg("who")), number(c.arg("speed"), 1.0), number(c.arg("min"), 2.0), max);
                }
            }
            case "enable", "disable" -> {
                Expr sys = c.arg("sys");
                String name = sys instanceof Ident id ? id.name : sys instanceof StringLit s ? s.value : "";
                boolean on = c.key.equals("enable");
                b.thenRun(() -> org.sprautestudio.spraute_engine.engine.EngineServer.setSystem(name, on));
            }
            case "walkspeed" -> b.entityMoveSpeed(character(c.arg("who")), number(c.arg("speed"), 1.0));
            case "wander" -> b.thenEntityRandomWalk(character(c.arg("who")), number(c.arg("speed"), 1.0), 10,
                    number(c.arg("radius"), 8.0));
            case "sit" -> b.entitySit(character(c.arg("who")), c.arg("pos") != null ? locationSupplier(c.arg("pos")) : null);
            case "clearbehavior" -> {
                String t = text(c.arg("type")).toUpperCase(java.util.Locale.ROOT);
                try {
                    b.thenClearBehaviour(character(c.arg("who")), org.sprautestudio.spraute_engine.core.script.Behaviours.Type.valueOf(t));
                } catch (IllegalArgumentException ex) {
                    throw new SprauteRuntimeException("Нет такого типа поведения: " + t + " (movement, look, combat, speech, action, interaction, emotion, complex)");
                }
            }
            case "fly" -> {
                Supplier<Location> to = locationSupplier(c.arg("target"));
                Character ch = character(c.arg("who"));
                b.thenRun(guard(() -> {
                    net.minecraft.world.entity.Entity e = script.optional((Character<?>) ch).orElse(null);
                    if (e != null && script.controls(ch)) org.sprautestudio.spraute_engine.path.Walkers.fly(e, to.get(), number(c.arg("speed"), WALK_SPEED));
                }));
                b.thenWaitUntil(() -> !org.sprautestudio.spraute_engine.path.Walkers.walking((net.minecraft.world.entity.Entity) script.optional((Character<?>) ch).orElse(null)));
            }
            case "refuse" -> b.refuse(character(c.arg("who")), (int) number(c.arg("times"), 1));
            case "wait dimension" -> b.thenWhenPlayerInDimension(id(c.arg("dim")));
            case "stand" -> b.entityStopSit(character(c.arg("who")));
            case "name" -> {
                Expr name = c.arg("name");
                Expr who = c.arg("who");
                b.entityNameOverride(character(who), (Supplier<String>) () -> safeText(name));
                // the speech window and the chat history: from the next line on
                String key = who instanceof Ident id ? id.name : "";
                b.thenRun(() -> script.speechNames.put(key, safeText(name)));
                Integer slot = speakerSlots.get("char:" + key);
                if (slot != null) speechEntity(b, slot, key);
            }
            case "hold" -> b.thenSetHeldItem(character(c.arg("who")), itemStack(c.arg("item"), 1));
            case "health" -> b.setMaxHealth(character(c.arg("who")), (float) number(c.arg("hp"), 20));
            case "free" -> b.freeEntity(character(c.arg("who")));
            case "nod" -> b.nod(character(c.arg("who")), (int) number(c.arg("times"), 1));
            case "nope" -> b.nope(character(c.arg("who")), (int) number(c.arg("times"), 1));
            case "laugh" -> {
                if (c.arg("times") == null && c.arg("speed") == null) b.laugh(character(c.arg("who")));
                else b.laugh(character(c.arg("who")), (int) number(c.arg("times"), 1), (float) number(c.arg("speed"), 1));
            }
            case "anim", "face" -> animation(b, c);
            case "gui open" -> {
                Expr name = c.arg("name");
                b.thenRun(guard(() -> {
                    String n = text(name);
                    String json = SprauteRuntime.get().gui(script.deployed.project, n);
                    if (json == null) {
                        SprauteRuntime.get().report(script.getId(), "Интерфейс «" + n + "» не найден в проекте " + script.deployed.project
                                + ". Загрузите сюжет из редактора ещё раз (F5)");
                        return;
                    }
                    script.openGui = n;
                    script.guiInputs.clear();
                    ServerPlayer p = SprauteRuntime.player();
                    script.guiPlayer = p != null ? p.getUUID() : null;
                    if (p != null) org.sprautestudio.spraute_engine.net.SprauteNetwork.sendTo(p,
                            new org.sprautestudio.spraute_engine.net.Packets.GuiOpen(script.getId(), script.deployed.project, n, json));
                    GuiSlots.opened(script, org.sprautestudio.spraute_engine.gui.GuiDoc.fromJson(json), p);
                }));
            }
            case "gui close" -> b.thenRun(() -> {
                script.openGui = null;
                ServerPlayer p = SprauteRuntime.player();
                if (p != null) org.sprautestudio.spraute_engine.net.SprauteNetwork.sendTo(p, new org.sprautestudio.spraute_engine.net.Packets.GuiClose(script.getId()));
            });
            case "gui text" -> guiUpdate(b, c.arg("el"), "text", c.arg("text"));
            case "gui show" -> guiUpdateConst(b, c.arg("el"), "visible", "true");
            case "gui hide" -> guiUpdateConst(b, c.arg("el"), "visible", "false");
            case "gui progress" -> guiUpdate(b, c.arg("el"), "value", c.arg("value"));
            case "gui image" -> guiUpdate(b, c.arg("el"), "image", c.arg("image"));
            case "gui scroll" -> guiUpdate(b, c.arg("el"), "scrollY", c.arg("y"));
            case "overlay show" -> {
                Expr name = c.arg("name");
                b.thenRun(guard(() -> {
                    String n = text(name);
                    String json = SprauteRuntime.get().gui(script.deployed.project, n);
                    if (json == null) throw new SprauteRuntimeException("Интерфейс «" + n + "» не найден в проекте. Загрузите сюжет ещё раз (F5)");
                    script.overlayShown(n, json);
                    org.sprautestudio.spraute_engine.net.SprauteNetwork.sendToAll(new org.sprautestudio.spraute_engine.net.Packets.OverlayShow(script.getId(), script.deployed.project, n, json));
                }));
            }
            case "overlay hide" -> {
                Expr name = c.arg("name");
                b.thenRun(guard(() -> {
                    String n = name == null ? "" : text(name);
                    script.overlayHidden(n);
                    org.sprautestudio.spraute_engine.net.SprauteNetwork.sendToAll(new org.sprautestudio.spraute_engine.net.Packets.OverlayHide(script.getId(), n));
                }));
            }
            case "gui move", "gui resize" -> {
                boolean move = c.key.equals("gui move");
                Expr el = c.arg("el"), a1 = c.arg(move ? "x" : "w"), a2 = c.arg(move ? "y" : "h"), time = c.arg("time");
                b.thenRun(guard(() -> {
                    String suffix = time != null ? "@" + ticks(time) / 20f : "";
                    String id = text(el);
                    sendGuiUpdate(id, (move ? "x" : "w") + suffix, safeText(a1));
                    sendGuiUpdate(id, (move ? "y" : "h") + suffix, safeText(a2));
                }));
            }
            case "gui animate" -> {
                Expr el = c.arg("el"), prop = c.arg("prop"), value = c.arg("value"), time = c.arg("time");
                b.thenRun(guard(() -> sendGuiUpdate(text(el), text(prop) + (time != null ? "@" + ticks(time) / 20f : ""), safeText(value))));
            }
            case "cutscene play" -> {
                Expr name = c.arg("name"), pos = c.arg("pos");
                boolean wait = c.arg("nowait") == null;
                String[] playing = {""};
                b.thenRun(guard(() -> {
                    String n = text(name);
                    playing[0] = n;
                    String json = SprauteRuntime.get().gui(script.deployed.project, "cutscene:" + n);
                    if (json == null) throw new SprauteRuntimeException("Катсцена «" + n + "» не найдена в проекте. Загрузите сюжет ещё раз (F5)");
                    ServerPlayer p = SprauteRuntime.player();
                    if (p == null) return;
                    var doc = org.sprautestudio.spraute_engine.cutscene.CutsceneDoc.fromJson(json);
                    java.util.Map<String, Integer> looks = new java.util.HashMap<>();
                    for (var k : doc.keys) {
                        if (k.look.isEmpty() || k.look.equals("player") || looks.containsKey(k.look)) continue;
                        Character<?> ch = script.characters.get(k.look);
                        net.minecraft.world.entity.Entity en = ch != null ? script.optional(ch).orElse(null) : eval.nearestMob(k.look);
                        if (en != null) looks.put(k.look, en.getId());
                    }
                    Location at = pos != null ? positionSafe(pos) : new Location(p);
                    script.cutscene = n;
                    org.sprautestudio.spraute_engine.net.SprauteNetwork.sendToAll(new org.sprautestudio.spraute_engine.net.Packets.CameraPlay(script.getId(), n, json, at.x, at.y, at.z, looks));
                }));
                if (wait) {
                    waitLog(b, "cutscene", () -> d -> playing[0].equals(d.get("id")));
                }
            }
            case "cutscene stop" -> b.thenRun(() -> camera(op("stop")));
            case "camera at" -> {
                Expr pos = c.arg("pos"), look = c.arg("look"), yaw = c.arg("yaw"), pitch = c.arg("pitch");
                CommandStmt cc = c;
                b.thenRun(guard(() -> {
                    com.google.gson.JsonObject o = op("at");
                    Location l = eval.eyes(pos);
                    o.addProperty("x", l.x);
                    o.addProperty("y", l.y);
                    o.addProperty("z", l.z);
                    if (yaw != null) o.addProperty("yaw", number(yaw, 0));
                    if (pitch != null) o.addProperty("pitch", number(pitch, 0));
                    if (look != null) lookTarget(o, look);
                    timing(o, cc);
                    camera(o);
                }));
            }
            case "camera look" -> {
                Expr target = c.arg("target");
                CommandStmt cc = c;
                b.thenRun(guard(() -> {
                    com.google.gson.JsonObject o = op("look");
                    lookTarget(o, target);
                    timing(o, cc);
                    camera(o);
                }));
            }
            case "camera turn" -> {
                Expr yaw = c.arg("yaw"), pitch = c.arg("pitch");
                CommandStmt cc = c;
                b.thenRun(guard(() -> {
                    com.google.gson.JsonObject o = op("turn");
                    o.addProperty("yaw", Eval.num(eval.eval(yaw)));
                    o.addProperty("pitch", Eval.num(eval.eval(pitch)));
                    timing(o, cc);
                    camera(o);
                }));
            }
            case "camera follow", "camera orbit" -> {
                Expr who = c.arg("who");
                CommandStmt cc = c;
                boolean follow = c.key.equals("camera follow");
                b.thenRun(guard(() -> {
                    com.google.gson.JsonObject o = op(follow ? "follow" : "orbit");
                    net.minecraft.world.entity.Entity en = entityOf(who);
                    if (en != null) o.addProperty("entity", en.getId());
                    for (String k : new String[]{"distance", "height", "angle", "radius", "speed"}) {
                        Expr v = cc.arg(k);
                        if (v != null) o.addProperty(k, number(v, 0));
                    }
                    camera(o);
                }));
            }
            case "camera reset" -> {
                CommandStmt cc = c;
                b.thenRun(guard(() -> {
                    com.google.gson.JsonObject o = op("reset");
                    o.addProperty("time", cc.arg("time") != null ? ticks(cc.arg("time")) / 20.0 : 0.8);
                    camera(o);
                }));
            }
            case "camera fov", "camera roll", "camera shake", "camera bars", "camera fade" -> {
                Expr value = c.arg("value"), color = c.arg("color");
                CommandStmt cc = c;
                String kind = c.key.substring(7);
                b.thenRun(guard(() -> {
                    com.google.gson.JsonObject o = op(kind);
                    o.addProperty("value", Eval.num(eval.eval(value)));
                    if (color != null) o.addProperty("color", text(color));
                    timing(o, cc);
                    camera(o);
                }));
            }
            case "slots" -> {
                Expr countE = c.arg("count"), offE = c.arg("offhand");
                b.thenRun(guard(() -> {
                    var server = Platform.get().server();
                    if (server == null) return;
                    var cfg = org.sprautestudio.spraute_engine.engine.EngineServer.config().copy();
                    cfg.slots = true;
                    cfg.hotbarSize = (int) Math.max(1, Math.min(9, number(countE, 9)));
                    if (offE != null) cfg.offhand = offE instanceof Ident oid ? oid.name : text(offE);
                    org.sprautestudio.spraute_engine.engine.EngineServer.set(server, cfg);
                }));
            }
            case "time task" -> timeTask(b, c);
            case "time taskstop" -> {
                Expr idE = c.arg("id");
                b.thenRun(guard(() -> org.sprautestudio.spraute_engine.core.system.WorldClock.removeTask(text(idE))));
            }
            case "camera carry" -> {
                Expr value = c.arg("value");
                b.thenRun(guard(() -> org.sprautestudio.spraute_engine.core.system.CameraState.carry(SprauteRuntime.player(), Eval.truthy(eval.eval(value)))));
            }
            case "fight tune" -> {
                Expr who = c.arg("who");
                Expr[] vals = {c.arg("armor"), c.arg("speed"), c.arg("power"), c.arg("stagger"), c.arg("block"), c.arg("dodge")};
                b.thenRun(guard(() -> {
                    net.minecraft.world.entity.Entity e = bossBody(who, "fight tune");
                    Double[] v = new Double[vals.length];
                    for (int i = 0; i < vals.length; i++) v[i] = vals[i] != null ? number(vals[i], 0) : null;
                    if (!org.sprautestudio.spraute_engine.fight.Fights.tune(e, v[0], v[1], v[2], v[3], v[4], v[5]))
                        throw new SprauteRuntimeException("fight tune: " + text(who) + " сейчас не дерётся (сначала fight)");
                }));
            }
            case "bossbar show" -> {
                Expr who = c.arg("who"), tE = c.arg("title"), colE = c.arg("color"), stE = c.arg("style");
                b.thenRun(guard(() -> BossMoves.bar(bossBody(who, "bossbar"), tE != null ? safeText(tE) : null,
                        colE != null ? text(colE) : null, stE != null ? text(stE) : null, script.getId())));
            }
            case "bossbar hide" -> {
                Expr who = c.arg("who");
                b.thenRun(guard(() -> BossMoves.hideBar(bossBody(who, "bossbar"))));
            }
            case "leap" -> {
                Expr who = c.arg("who"), toE = c.arg("to"), hE = c.arg("height"), tE = c.arg("time"), imE = c.arg("impact"),
                        rE = c.arg("radius"), dE = c.arg("damage"), pE = c.arg("push");
                boolean nowait = c.arg("nowait") != null;
                net.minecraft.world.entity.Entity[] body = new net.minecraft.world.entity.Entity[1];
                b.thenRun(guard(() -> {
                    body[0] = bossBody(who, "leap");
                    int ticks = tE != null ? Math.max(4, ticksSafe(tE)) : 16;
                    BossMoves.leap(body[0], script.getId(), WorldApi.vec(eval.ref(toE)), hE != null ? number(hE, 3) : 3, ticks, bossFx(imE),
                            rE != null ? number(rE, 0) : 0, (float) (dE != null ? number(dE, 4) : 4), pE != null ? number(pE, 1.2) : 1.2);
                }));
                if (!nowait) b.thenWaitUntil(() -> !BossMoves.moving(body[0]));
            }
            case "charge" -> {
                Expr who = c.arg("who"), toE = c.arg("to"), sE = c.arg("speed"), distE = c.arg("distance"), dE = c.arg("damage"),
                        pE = c.arg("push"), trE = c.arg("trail");
                boolean nowait = c.arg("nowait") != null;
                net.minecraft.world.entity.Entity[] body = new net.minecraft.world.entity.Entity[1];
                b.thenRun(guard(() -> {
                    body[0] = bossBody(who, "charge");
                    BossMoves.charge(body[0], script.getId(), WorldApi.vec(eval.ref(toE)), sE != null ? number(sE, 14) : 14,
                            distE != null ? number(distE, 0) : 0, (float) (dE != null ? number(dE, 4) : 4), pE != null ? number(pE, 1) : 1, bossFx(trE));
                }));
                if (!nowait) b.thenWaitUntil(() -> !BossMoves.moving(body[0]));
            }
            case "circle" -> {
                Expr who = c.arg("who"), cE = c.arg("center"), rE = c.arg("radius"), sE = c.arg("speed"), tE = c.arg("time");
                boolean nowait = c.arg("nowait") != null;
                net.minecraft.world.entity.Entity[] body = new net.minecraft.world.entity.Entity[1];
                b.thenRun(guard(() -> {
                    body[0] = bossBody(who, "circle");
                    Object center = eval.ref(cE);
                    net.minecraft.world.entity.Entity ce = center instanceof EntityRef r ? r.require() : null;
                    BossMoves.circle(body[0], script.getId(), ce, ce == null ? WorldApi.vec(center) : null, rE != null ? number(rE, 5) : 5,
                            sE != null ? number(sE, 90) : 90, tE != null ? Math.max(1, ticksSafe(tE)) : 60);
                }));
                if (!nowait) b.thenWaitUntil(() -> !BossMoves.moving(body[0]));
            }
            case "part" -> {
                Expr who = c.arg("who"), nE = c.arg("name"), onE = c.arg("enabled"), dE = c.arg("damage"), aE = c.arg("attack");
                b.thenRun(guard(() -> {
                    if (!(bossBody(who, "part") instanceof org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity me))
                        throw new SprauteRuntimeException("part: хитбоксы-части есть только у сущностей spraute:… (редактор сущности)");
                    String n = text(nE);
                    if (!me.partNames().contains(n))
                        throw new SprauteRuntimeException("part: у " + text(who) + " нет части «" + n + "». Есть: " + String.join(", ", me.partNames()));
                    if (onE != null) me.setPartEnabled(n, Eval.truthy(eval.eval(onE)));
                    if (dE != null) me.setPartDamage(n, (float) number(dE, 1));
                    if (aE != null) me.setPartAttack(n, (float) number(aE, 0));
                }));
            }
            case "attack" -> {
                Expr who = c.arg("who"), partE = c.arg("part"), wE = c.arg("w"), hE = c.arg("h"), dpE = c.arg("d"), rE = c.arg("right"),
                        uE = c.arg("up"), fE = c.arg("forward"), dlE = c.arg("delay"), tE = c.arg("time"), dE = c.arg("damage"), pE = c.arg("push"),
                        imE = c.arg("impact"), exE = c.arg("except");
                boolean show = c.arg("show") != null, nowait = c.arg("nowait") != null;
                net.minecraft.world.entity.Entity[] body = new net.minecraft.world.entity.Entity[1];
                b.thenRun(guard(() -> {
                    body[0] = bossBody(who, "attack");
                    net.minecraft.world.entity.Entity ex = exE != null && eval.ref(exE) instanceof EntityRef xr ? xr.require() : null;
                    AttackBoxes.attack(body[0], partE != null ? text(partE) : null,
                            new org.joml.Vector3f((float) number(wE, 2), (float) number(hE, 2), (float) number(dpE, 2)),
                            new org.joml.Vector3f((float) number(rE, 0), (float) number(uE, 1), (float) number(fE, 1.5)),
                            (float) (dE != null ? number(dE, 4) : 4), pE != null ? number(pE, 0.5) : 0.5,
                            dlE != null ? ticksSafe(dlE) : 0, tE != null ? ticksSafe(tE) : 6, ex, bossFx(imE), show, script.getId());
                }));
                if (!nowait) b.thenWaitUntil(() -> body[0] == null || !AttackBoxes.active(body[0]));
            }
            case "stopmove" -> {
                Expr who = c.arg("who");
                b.thenRun(guard(() -> {
                    net.minecraft.world.entity.Entity e = bossBody(who, "stopmove");
                    BossMoves.stop(e);
                    AttackBoxes.stop(e);
                }));
            }
            case "strike" -> {
                Expr atE = c.arg("pos"), rE = c.arg("radius"), dlE = c.arg("delay"), dE = c.arg("damage"), pE = c.arg("push"),
                        wE = c.arg("warn"), imE = c.arg("impact"), byE = c.arg("by"), exE = c.arg("except");
                b.thenRun(guard(() -> {
                    net.minecraft.world.entity.Entity by = byE != null && eval.ref(byE) instanceof EntityRef br ? br.require() : null;
                    net.minecraft.world.entity.Entity ex = exE != null && eval.ref(exE) instanceof EntityRef xr ? xr.require() : null;
                    BossMoves.strike(WorldApi.level(), WorldApi.vec(eval.ref(atE)), rE != null ? number(rE, 2.5) : 2.5, dlE != null ? ticksSafe(dlE) : 20,
                            (float) (dE != null ? number(dE, 4) : 4), pE != null ? number(pE, 0.8) : 0.8, bossFx(wE), bossFx(imE), by, ex, script.getId());
                }));
            }
            case "beam" -> {
                Expr fromE = c.arg("from"), toE = c.arg("to"), tE = c.arg("time"), dE = c.arg("damage"), rE = c.arg("range"), wE = c.arg("width"),
                        trE = c.arg("track"), paE = c.arg("particle"), imE = c.arg("impact");
                boolean wait = c.arg("wait") != null;
                net.minecraft.world.entity.Entity[] body = new net.minecraft.world.entity.Entity[1];
                b.thenRun(guard(() -> {
                    body[0] = bossBody(fromE, "beam");
                    Object to = eval.ref(toE);
                    net.minecraft.world.entity.Entity te = to instanceof EntityRef r ? r.require() : null;
                    BossMoves.beam(body[0], te, te == null ? WorldApi.vec(to) : null, tE != null ? ticksSafe(tE) : 40, (float) (dE != null ? number(dE, 6) : 6),
                            rE != null ? number(rE, 24) : 24, wE != null ? number(wE, 0.3) : 0.3, trE != null ? number(trE, 0) : 0,
                            paE != null ? text(paE) : null, bossFx(imE), script.getId());
                }));
                if (wait) b.thenWaitUntil(() -> body[0] == null || !BossMoves.beaming(body[0]));
            }
            case "shockwave" -> {
                Expr atE = c.arg("pos"), rE = c.arg("radius"), pE = c.arg("power"), dE = c.arg("damage"), exE = c.arg("except");
                b.thenRun(guard(() -> {
                    Object at = eval.ref(atE);
                    net.minecraft.world.entity.Entity center = at instanceof EntityRef er ? er.require() : null;
                    net.minecraft.world.phys.Vec3 pos = center != null ? center.position() : WorldApi.vec(at);
                    net.minecraft.world.entity.Entity except = exE != null && eval.ref(exE) instanceof EntityRef xr ? xr.require() : center;
                    Mechanics.shockwave(WorldApi.level(), pos, rE != null ? number(rE, 6) : 6, pE != null ? number(pE, 1.2) : 1.2,
                            (float) (dE != null ? number(dE, 0) : 0), except, center);
                }));
            }
            case "explode" -> {
                Expr atE = c.arg("pos"), pE = c.arg("power");
                boolean fire = c.arg("fire") != null, breaks = c.arg("break") != null;
                b.thenRun(guard(() -> Mechanics.explode(WorldApi.level(), WorldApi.vec(eval.ref(atE)), (float) (pE != null ? number(pE, 3) : 3), fire, breaks, null)));
            }
            case "lightning" -> {
                Expr atE = c.arg("pos");
                boolean harmless = c.arg("harmless") != null;
                b.thenRun(guard(() -> Mechanics.lightning(WorldApi.level(), WorldApi.vec(eval.ref(atE)), harmless)));
            }
            case "bar set" -> {
                Expr idE = c.arg("id"), vE = c.arg("value"), mE = c.arg("max"), tE = c.arg("title"), colE = c.arg("color");
                b.thenRun(guard(() -> Mechanics.bar(text(idE), number(vE, 0), mE != null ? number(mE, 100) : 100,
                        tE != null ? text(tE) : null, colE != null ? text(colE) : null)));
            }
            case "bar hide" -> {
                Expr idE = c.arg("id");
                b.thenRun(guard(() -> Mechanics.hideBar(text(idE))));
            }
            case "notify" -> {
                Expr tE = c.arg("text"), iconE = c.arg("icon"), itemE = c.arg("item");
                boolean silent = c.arg("silent") != null;
                b.thenRun(guard(() -> {
                    ServerPlayer p = SprauteRuntime.player();
                    if (p == null) return;
                    org.sprautestudio.spraute_engine.net.SprauteNetwork.sendTo(p, new org.sprautestudio.spraute_engine.net.Packets.Notify(safeText(tE),
                            iconE != null ? text(iconE) : "", itemE != null ? text(itemE) : "", script.deployed.project == null ? "" : script.deployed.project, !silent));
                }));
            }
            case "actionbar" -> {
                Expr tE = c.arg("text");
                b.thenRun(guard(() -> Mechanics.actionbar(safeText(tE))));
            }
            case "cast" -> {
                Expr nameE = c.arg("name"), fromE = c.arg("from"), toE = c.arg("to"), speedE = c.arg("speed"), dmgE = c.arg("damage"), rangeE = c.arg("range"),
                        gravE = c.arg("gravity"), sizeE = c.arg("size"), pushE = c.arg("push"), impactE = c.arg("impact"), scaleE = c.arg("scale"), idE = c.arg("id");
                boolean pierce = c.arg("pierce") != null, ring = c.arg("ring") != null;
                Expr countE = c.arg("count"), spreadE = c.arg("spread");
                b.thenRun(guard(() -> {
                    String n = text(nameE).trim();
                    int count = (int) Math.max(1, Math.min(64, countE != null ? number(countE, 1) : ring ? 8 : 1));
                    double spread = spreadE != null ? number(spreadE, 30) : 30;
                    for (int shot = 0; shot < count; shot++) {
                    var cast = new org.sprautestudio.spraute_engine.fx.Casts.Cast();
                    cast.fx = n;
                    cast.id = idE != null ? text(idE) : n;
                    cast.project = script.deployed.project;
                    cast.scriptId = script.getId();
                    // from: an entity's eyes (a bit forward and down, like a hand) or a point
                    Object from;
                    if (fromE != null) from = eval.ref(fromE);
                    else {
                        ServerPlayer pl = SprauteRuntime.player();
                        if (pl == null) throw new SprauteRuntimeException("cast: игрока нет в мире");
                        from = new EntityRef(pl);
                    }
                    net.minecraft.world.entity.Entity owner = from instanceof EntityRef er ? er.require() : null;
                    net.minecraft.world.phys.Vec3 start, dir;
                    if (owner != null) {
                        net.minecraft.world.phys.Vec3 look = owner.getViewVector(1);
                        start = owner.getEyePosition().add(look.scale(0.8)).add(0, -0.25, 0);
                        dir = look;
                    } else {
                        start = WorldApi.vec(from);
                        dir = new net.minecraft.world.phys.Vec3(0, 0, 1);
                    }
                    if (toE != null) {
                        Object to = eval.ref(toE);
                        net.minecraft.world.phys.Vec3 aim = to instanceof EntityRef tr ? tr.require().getBoundingBox().getCenter() : WorldApi.vec(to);
                        dir = aim.subtract(start);
                    }
                    if (dir.lengthSqr() < 1e-6) dir = new net.minecraft.world.phys.Vec3(0, 0, 1);
                    // a volley: turn this shot around the vertical — evenly round (ring) or across a fan (spread)
                    if (ring) dir = new net.minecraft.world.phys.Vec3(dir.x, 0, dir.z).yRot((float) (Math.PI * 2 * shot / count));
                    else if (count > 1) dir = dir.yRot((float) Math.toRadians(-spread / 2 + spread * shot / (count - 1)));
                    if (dir.lengthSqr() < 1e-6) dir = new net.minecraft.world.phys.Vec3(0, 0, 1);
                    double speed = Math.max(0.5, speedE != null ? number(speedE, 20) : 20) / 20.0;
                    cast.pos = start;
                    cast.vel = dir.normalize().scale(speed);
                    cast.owner = owner;
                    cast.level = owner != null && owner.level() instanceof net.minecraft.server.level.ServerLevel sl ? sl : WorldApi.level();
                    cast.damage = (float) (dmgE != null ? number(dmgE, 4) : 4);
                    cast.life = (int) Math.ceil(Math.max(1, rangeE != null ? number(rangeE, 48) : 48) / speed);
                    cast.gravity = gravE != null ? number(gravE, 0) * 0.04 : 0;
                    cast.radius = sizeE != null ? Math.max(0.05, number(sizeE, 0.4)) : 0.4;
                    cast.knockback = (float) (pushE != null ? number(pushE, 0.4) : 0.4);
                    cast.scale = scaleE != null ? (float) number(scaleE, 1) : 1f;
                    cast.pierce = pierce;
                    if (impactE != null) cast.impactJson = fxJson(text(impactE).trim());
                    org.sprautestudio.spraute_engine.fx.Casts.launch(cast, fxJson(n));
                    }
                }));
            }
            case "fx play" -> {
                Expr nameE = c.arg("name"), pos = c.arg("pos"), scaleE = c.arg("scale"), idE = c.arg("id");
                boolean follow = c.arg("follow") != null;
                b.thenRun(guard(() -> {
                    String n = text(nameE).trim();
                    String json = SprauteRuntime.get().gui(script.deployed.project, "fx:" + n);
                    if (json == null) {
                        var preset = org.sprautestudio.spraute_engine.fx.FxPresets.get(n);
                        if (preset == null) throw new SprauteRuntimeException("Эффект «" + n + "» не найден: его нет в папке «Эффекты» проекта и среди встроенных ("
                                + String.join(", ", org.sprautestudio.spraute_engine.fx.FxPresets.names()) + ")");
                        json = preset.toJson();
                    }
                    Location at = positionSafe(pos);
                    net.minecraft.world.entity.Entity en = follow ? entityOf(pos) : null;
                    float sc = scaleE != null ? (float) number(scaleE, 1) : 1f;
                    String id = idE != null ? text(idE) : "";
                    org.sprautestudio.spraute_engine.net.SprauteNetwork.sendToAll(new org.sprautestudio.spraute_engine.net.Packets.FxPlay(
                            id, script.deployed.project, json, at.x, at.y, at.z, en != null ? en.getId() : -1, sc, script.getId()));
                }));
            }
            case "skin" -> skin(b, c.arg("who"), c.arg("skin"));
            case "namecolor" -> {
                Expr who = c.arg("who"), colE = c.arg("color");
                String key;
                if (who instanceof Ident id && script.characters.containsKey(id.name)) key = id.name;
                else if (who instanceof Ident id && id.name.equals("player")) key = "player";
                else throw new SprauteRuntimeException("namecolor: нужен персонаж или player");
                if (!(colE instanceof StringLit sl)) throw new SprauteRuntimeException("namecolor: цвет в кавычках, например \"#FF8800\" или \"gold\"");
                Integer col = Colors.parse(sl.value);
                b.thenRun(() -> {
                    if (col != null) script.nameColors.put(key, col);
                    else script.nameColors.remove(key);
                });
                // already speaking: the new colour for its slot now
                Integer slot = speakerSlots.get(key.equals("player") ? "player" : "char:" + key);
                if (slot != null) {
                    if (key.equals("player")) speechPlayer(b, slot);
                    else speechEntity(b, slot, key);
                }
            }
            case "shop open" -> {
                Expr nameE = c.arg("name"), who = c.arg("who");
                b.thenRun(guard(() -> {
                    ServerPlayer p = SprauteRuntime.player();
                    if (p == null) return;
                    String n = text(nameE);
                    if (!org.sprautestudio.spraute_engine.shop.Shops.open(p, script.getId(), script.deployed.project, n, who != null ? entityOf(who) : null)) {
                        throw new SprauteRuntimeException("Магазин «" + n + "» не найден в папке «Магазины» проекта");
                    }
                }));
            }
            case "shop restock" -> {
                Expr nameE = c.arg("name");
                b.thenRun(guard(() -> org.sprautestudio.spraute_engine.shop.Shops.restock(script.deployed.project, text(nameE))));
            }
            case "shop stock" -> {
                Expr nameE = c.arg("name"), offerE = c.arg("offer"), value = c.arg("value");
                b.thenRun(guard(() -> org.sprautestudio.spraute_engine.shop.Shops.setStock(script.deployed.project, text(nameE), text(offerE),
                        (int) number(value, 0))));
            }
            case "shop close" -> b.thenRun(guard(() -> {
                ServerPlayer p = SprauteRuntime.player();
                if (p != null) org.sprautestudio.spraute_engine.net.SprauteNetwork.sendTo(p, new org.sprautestudio.spraute_engine.net.Packets.ShopOpen("", false));
            }));
            case "hitbox" -> {
                Expr who = c.arg("who");
                List<Expr> modes = new ArrayList<>();
                for (Arg a : c.args) if ("modes".equals(a.param)) modes.add(a.value);
                b.thenRun(guard(() -> {
                    Object r = eval.ref(who);
                    if (!(r instanceof EntityRef er)) throw new SprauteRuntimeException("hitbox: нужен персонаж, игрок или существо");
                    net.minecraft.world.entity.Entity e = er.require();
                    List<String> words = new ArrayList<>();
                    for (Expr m : modes) words.add(text(m));
                    try {
                        org.sprautestudio.spraute_engine.hitbox.Hitboxes.set(e, org.sprautestudio.spraute_engine.hitbox.Hitboxes.parse(org.sprautestudio.spraute_engine.hitbox.Hitboxes.flags(e), words));
                    } catch (IllegalArgumentException ex) {
                        throw new SprauteRuntimeException(ex.getMessage());
                    }
                }));
            }
            case "fight", "fight stop", "fight heal" -> {
                Expr who = c.arg("who"), styleE = c.arg("style"), targetE = c.arg("target"), value = c.arg("value");
                String name = who instanceof Ident id ? id.name : "?";
                Character ch = character(who);
                String kind = c.key;
                b.thenRun(guard(() -> {
                    net.minecraft.world.entity.Entity e = (net.minecraft.world.entity.Entity) script.optional(ch).orElse(null);
                    if (e == null) throw new SprauteRuntimeException("Персонаж " + name + " сейчас не в мире");
                    switch (kind) {
                        case "fight stop" -> org.sprautestudio.spraute_engine.fight.Fights.stop(e);
                        case "fight heal" -> org.sprautestudio.spraute_engine.fight.Fights.heal(e, value != null ? (float) number(value, -1) : -1);
                        default -> {
                            String st = styleE != null ? text(styleE) : "";
                            var def = org.sprautestudio.spraute_engine.fight.Fights.style(script.deployed.project, st);
                            if (def == null) throw new SprauteRuntimeException("Стиль боя «" + st + "» не найден: его нет в папке «Стили боя» и среди готовых (fists, sword, brute, weak)");
                            net.minecraft.world.entity.Entity t = null;
                            if (targetE != null) {
                                Object r = eval.ref(targetE);
                                if (!(r instanceof EntityRef er)) throw new SprauteRuntimeException("Цель боя — игрок, персонаж или существо");
                                t = er.require();
                            }
                            try {
                                org.sprautestudio.spraute_engine.fight.Fights.start(e, name, script.getId(), def, t);
                            } catch (IllegalArgumentException ex) {
                                throw new SprauteRuntimeException(ex.getMessage());
                            }
                        }
                    }
                }));
            }
            case "wait defeated" -> {
                Character ch = character(c.arg("who"));
                java.util.UUID[] seen = new java.util.UUID[1];
                b.thenWaitUntil(() -> {
                    net.minecraft.world.entity.Entity e = (net.minecraft.world.entity.Entity) script.optional(ch).orElse(null);
                    if (e != null) {
                        seen[0] = e.getUUID();
                        return !e.isAlive() || org.sprautestudio.spraute_engine.fight.Fights.defeated(e);
                    }
                    // not here: only unloaded far away (still fighting) — or dead / removed for good
                    return seen[0] != null && org.sprautestudio.spraute_engine.fight.Fights.gone(seen[0]);
                });
            }
            case "zone on", "zone off", "zone wall" -> {
                Expr nameE = c.arg("name"), modeE = c.arg("mode");
                String kind = c.key.substring(5);
                b.thenRun(guard(() -> {
                    String n = text(nameE), proj = script.deployed.project;
                    boolean ok = kind.equals("wall") ? org.sprautestudio.spraute_engine.zone.Zones.setWall(proj, n, text(modeE))
                            : org.sprautestudio.spraute_engine.zone.Zones.setEnabled(proj, n, kind.equals("on"));
                    if (!ok) throw new SprauteRuntimeException("Зона «" + n + "» не найдена в папке «Зоны» проекта");
                }));
            }
            case "wait zone", "wait outside" -> {
                Expr nameE = c.arg("name");
                boolean in = c.key.equals("wait zone");
                String[] n = {null};
                b.thenRun(guard(() -> {
                    n[0] = text(nameE);
                    org.sprautestudio.spraute_engine.zone.Zones.require(script.deployed.project, n[0]);
                }));
                b.thenWaitUntil(() -> n[0] != null && org.sprautestudio.spraute_engine.zone.Zones.playerIn(SprauteRuntime.player(), script.deployed.project, n[0]) == in);
            }
            case "quest start", "quest progress", "quest set", "quest reveal", "quest done", "quest fail", "quest reset", "quest track" -> {
                Expr nameE = c.arg("name"), taskE = c.arg("task"), value = c.arg("value");
                String kind = c.key.substring(6);
                b.thenRun(guard(() -> {
                    ServerPlayer p = SprauteRuntime.player();
                    if (p == null) return;
                    String asked = text(nameE);
                    String[] rq = org.sprautestudio.spraute_engine.quest.Quests.resolve(script.deployed.project, asked);
                    String n = rq[1], proj = rq[0];
                    boolean ok;
                    try {
                        ok = switch (kind) {
                            case "start" -> org.sprautestudio.spraute_engine.quest.Quests.start(p, proj, n);
                            case "progress" -> org.sprautestudio.spraute_engine.quest.Quests.progress(p, proj, n, text(taskE), value != null ? (int) Math.round(number(value, 1)) : 1, false);
                            case "set" -> org.sprautestudio.spraute_engine.quest.Quests.progress(p, proj, n, text(taskE), (int) Math.round(number(value, 0)), true);
                            case "reveal" -> org.sprautestudio.spraute_engine.quest.Quests.reveal(p, proj, n, text(taskE));
                            case "done" -> org.sprautestudio.spraute_engine.quest.Quests.end(p, proj, n, true);
                            case "fail" -> org.sprautestudio.spraute_engine.quest.Quests.end(p, proj, n, false);
                            case "reset" -> {
                                org.sprautestudio.spraute_engine.quest.Quests.reset(p, proj, n);
                                yield true;
                            }
                            default -> {
                                org.sprautestudio.spraute_engine.quest.Quests.track(p, org.sprautestudio.spraute_engine.quest.Quests.key(proj, n));
                                yield true;
                            }
                        };
                    } catch (IllegalArgumentException ex) {
                        throw new SprauteRuntimeException(ex.getMessage());
                    }
                    if (!ok) {
                        List<String> have = org.sprautestudio.spraute_engine.quest.Quests.names(script.deployed.project);
                        String pn = script.deployed.project == null || script.deployed.project.isEmpty() ? "" : " «" + script.deployed.project + "»";
                        throw new SprauteRuntimeException("Квест «" + asked + "» не найден в папке «Квесты» проекта" + pn + ". "
                                + (have.isEmpty() ? "Там нет квестов (или проект не отправлен в мир — сохраните квест или запустите сюжет из редактора)."
                                : "Есть: " + String.join(", ", have) + " (пишите имя файла квеста)."));
                    }
                }));
            }
            case "quest journal" -> b.thenRun(guard(() -> {
                ServerPlayer p = SprauteRuntime.player();
                if (p != null) org.sprautestudio.spraute_engine.quest.Quests.sync(p, true);
            }));
            case "time set", "time add", "time speed", "time freeze", "time unfreeze" -> {
                Expr value = c.arg("value");
                String kind = c.key.substring(5);
                b.thenRun(guard(() -> {
                    var server = Platform.get().server();
                    if (server == null) return;
                    if (org.sprautestudio.spraute_engine.engine.EngineServer.config().time) {
                        switch (kind) {
                            case "set" -> {
                                if (c.arg("day") != null) org.sprautestudio.spraute_engine.core.system.WorldClock.set(server, (long) number(c.arg("day"), 0), timeOfDay(value));
                                else org.sprautestudio.spraute_engine.core.system.WorldClock.setTimeOfDay(server, timeOfDay(value));
                            }
                            case "add" -> org.sprautestudio.spraute_engine.core.system.WorldClock.add(server, (long) number(value, 0));
                            case "speed" -> org.sprautestudio.spraute_engine.core.system.WorldClock.setFactor(server, number(value, 1));
                            case "freeze" -> {
                                if (value != null) org.sprautestudio.spraute_engine.core.system.WorldClock.setTimeOfDay(server, timeOfDay(value));
                                org.sprautestudio.spraute_engine.core.system.WorldClock.freeze(server);
                            }
                            default -> org.sprautestudio.spraute_engine.core.system.WorldClock.unfreeze(server, c.arg("back") != null);
                        }
                        return;
                    }
                    switch (kind) {
                        case "set" -> org.sprautestudio.spraute_engine.engine.EngineServer.setTimeOfDay(server, timeOfDay(value));
                        case "add" -> org.sprautestudio.spraute_engine.engine.EngineServer.addTime(server, (long) number(value, 0));
                        case "speed" -> {
                            var cfg = org.sprautestudio.spraute_engine.engine.EngineServer.config().copy();
                            cfg.clock.speed = (float) number(value, 1);
                            org.sprautestudio.spraute_engine.engine.EngineServer.set(server, cfg);
                        }
                        case "freeze" -> {
                            var cfg = org.sprautestudio.spraute_engine.engine.EngineServer.config().copy();
                            cfg.clock.manage = true;
                            cfg.clock.fixed = (int) Math.floorMod(value != null ? timeOfDay(value) : server.overworld().getDayTime(), 24000L);
                            org.sprautestudio.spraute_engine.engine.EngineServer.set(server, cfg);
                        }
                        default -> {
                            var cfg = org.sprautestudio.spraute_engine.engine.EngineServer.config().copy();
                            cfg.clock.manage = true;
                            cfg.clock.fixed = -1;
                            cfg.clock.cycle = true;
                            org.sprautestudio.spraute_engine.engine.EngineServer.set(server, cfg);
                        }
                    }
                }));
            }
            case "summon" -> {
                Expr typeE = c.arg("type"), pos = c.arg("pos"), into = c.arg("var");
                b.thenRun(guard(() -> {
                    EntityRef r = WorldApi.summon(text(typeE), positionSafe(pos));
                    if (into instanceof Ident v) {
                        Object old = script.vars.get(v.name);
                        if (old instanceof List<?> l) {
                            @SuppressWarnings("unchecked") List<Object> list = (List<Object>) l;
                            list.add(r);
                        } else script.vars.put(v.name, r);
                    }
                }));
            }
            case "setblock" -> {
                Expr pos = c.arg("pos"), block = c.arg("block");
                b.thenRun(guard(() -> WorldApi.setBlock(positionSafe(pos), text(block))));
            }
            case "use" -> {
                Expr who = c.arg("who"), pos = c.arg("pos");
                b.thenRun(guard(() -> {
                    net.minecraft.world.entity.Entity e = entityOf(who);
                    if (e == null) throw new SprauteRuntimeException("use: некому использовать блок, персонажа нет в мире");
                    WorldApi.useBlock(e, positionSafe(pos));
                }));
            }
            case "setprop" -> {
                Expr pos = c.arg("pos"), prop = c.arg("prop"), value = c.arg("value");
                b.thenRun(guard(() -> WorldApi.setProp(positionSafe(pos), text(prop), eval.eval(value))));
            }
            case "emit" -> {
                Expr nameE = c.arg("name"), valueE = c.arg("value");
                b.thenRun(guard(() -> {
                    java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
                    data.put("name", text(nameE));
                    data.put("value", valueE != null ? eval.eval(valueE) : "");
                    data.put("from", (double) script.getId());
                    GameEvents.fire("signal", null, data);
                }));
            }
            case "fx stop" -> {
                Expr idE = c.arg("id");
                b.thenRun(guard(() -> org.sprautestudio.spraute_engine.net.SprauteNetwork.sendToAll(
                        new org.sprautestudio.spraute_engine.net.Packets.FxStop(text(idE), -1))));
            }
            case "fx clear" -> b.thenRun(guard(() -> org.sprautestudio.spraute_engine.net.SprauteNetwork.sendToAll(
                    new org.sprautestudio.spraute_engine.net.Packets.FxStop("", -1))));
            case "camera hide" -> {
                Expr what = c.arg("what");
                b.thenRun(guard(() -> {
                    String w = text(what).trim().toLowerCase(java.util.Locale.ROOT);
                    if (!org.sprautestudio.spraute_engine.cutscene.CutsceneDoc.HIDE_MODES.contains(w))
                        throw new SprauteRuntimeException("camera hide: ожидалось all, minecraft, classic, custom или none, а не «" + w + "»");
                    com.google.gson.JsonObject o = op("hud");
                    o.addProperty("value", w);
                    camera(o);
                }));
            }
            case "camera hud", "camera lock" -> {
                Expr value = c.arg("value");
                String kind = c.key.substring(7);
                b.thenRun(guard(() -> {
                    com.google.gson.JsonObject o = op(kind);
                    o.addProperty("value", Eval.truthy(eval.eval(value)));
                    camera(o);
                }));
            }
            case "orb spawn" -> {
                Expr idE = c.arg("id"), pos = c.arg("pos"), count = c.arg("count"), value = c.arg("value"), coins = c.arg("coins"), xp = c.arg("xp"),
                        image = c.arg("image"), item = c.arg("item"), color = c.arg("color"), size = c.arg("size"), radius = c.arg("radius"),
                        life = c.arg("life"), spread = c.arg("spread");
                boolean nopickup = c.arg("nopickup") != null, floating = c.arg("float") != null;
                b.thenRun(guard(() -> {
                    ServerPlayer p = SprauteRuntime.player();
                    if (p == null) return;
                    Location at = positionSafe(pos);
                    int n = count != null ? Math.max(1, Math.min(500, (int) Eval.num(eval.eval(count)))) : 1;
                    double sp = spread != null ? number(spread, 0.5) : 0.5;
                    String look = "";
                    if (image != null) look = Images.texture(script.deployed.project, text(image)).toString();
                    else if (item != null) {
                        String iid = text(item);
                        look = "item:" + ItemSpec.with(iid, ItemSpec.nbt(iid));
                    }
                    int col = color != null ? org.sprautestudio.spraute_engine.gui.GuiDoc.toColor(text(color)) : 0xFFFFFFFF;
                    if ((col >>> 24) == 0) col |= 0xFF000000;
                    for (int i = 0; i < n; i++) {
                        var orb = new org.sprautestudio.spraute_engine.runtime.orb.SprauteOrb(org.sprautestudio.spraute_engine.runtime.orb.Orbs.ORB.get(), p.serverLevel());
                        orb.setKind(text(idE));
                        orb.setLook(look);
                        orb.setColor(col);
                        orb.setSize((float) (size != null ? number(size, 0.25) : 0.25));
                        orb.setLife(life != null ? ticks(life) : 1200);
                        orb.value = value != null ? (int) Eval.num(eval.eval(value)) : 1;
                        orb.coins = coins != null ? (int) Eval.num(eval.eval(coins)) : 0;
                        orb.xp = xp != null ? (int) Eval.num(eval.eval(xp)) : 0;
                        orb.radius = radius != null ? number(radius, 6) : 6;
                        orb.pickup = !nopickup;
                        orb.floating = floating;
                        var rnd = p.getRandom();
                        orb.setPos(at.x + (rnd.nextDouble() - 0.5) * sp, at.y + 0.5 + rnd.nextDouble() * sp * 0.5, at.z + (rnd.nextDouble() - 0.5) * sp);
                        orb.setDeltaMovement((rnd.nextDouble() - 0.5) * 0.2 * sp, 0.1 + rnd.nextDouble() * 0.15 * sp, (rnd.nextDouble() - 0.5) * 0.2 * sp);
                        p.serverLevel().addFreshEntity(orb);
                    }
                }));
            }
            case "orb clear" -> {
                Expr idE = c.arg("id");
                b.thenRun(guard(() -> {
                    ServerPlayer p = SprauteRuntime.player();
                    if (p == null) return;
                    String only = idE != null ? text(idE) : null;
                    for (var en : p.serverLevel().getAllEntities()) {
                        if (en instanceof org.sprautestudio.spraute_engine.runtime.orb.SprauteOrb o && (only == null || o.kind().equals(only))) o.discard();
                    }
                }));
            }
            case "particles" -> {
                Expr type = c.arg("type"), pos = c.arg("pos"), count = c.arg("count"), spread = c.arg("spread"), speed = c.arg("speed");
                b.thenRun(guard(() -> {
                    ServerPlayer p = SprauteRuntime.player();
                    if (p == null) return;
                    String t = text(type);
                    var rl = net.minecraft.resources.ResourceLocation.tryParse(t.contains(":") ? t : "minecraft:" + t);
                    var pt = rl == null ? null : net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(rl);
                    if (!(pt instanceof net.minecraft.core.particles.SimpleParticleType simple))
                        throw new SprauteRuntimeException("Частицы " + t + " не найдены (нужен простой тип: flame, heart, cloud, end_rod...)");
                    Location at = positionSafe(pos);
                    int n = count != null ? (int) Eval.num(eval.eval(count)) : 10;
                    double sp = spread != null ? number(spread, 0.5) : 0.5;
                    p.serverLevel().sendParticles(simple, at.x, at.y + 1, at.z, n, sp, sp, sp, speed != null ? number(speed, 0.02) : 0.02);
                }));
            }
            case "coins give", "coins take", "coins set", "coins need" -> {
                Expr amount = c.arg("amount");
                String k = c.key;
                b.thenRun(guard(() -> {
                    int n = (int) Eval.num(eval.eval(amount));
                    switch (k) {
                        case "coins give" -> org.sprautestudio.spraute_engine.core.system.Coins.add(SprauteRuntime.player(), n);
                        case "coins take" -> org.sprautestudio.spraute_engine.core.system.Coins.add(SprauteRuntime.player(), -Math.min(n, org.sprautestudio.spraute_engine.core.system.Coins.get(SprauteRuntime.player())));
                        case "coins need" -> org.sprautestudio.spraute_engine.core.system.Coins.need(SprauteRuntime.player(), n);
                        default -> org.sprautestudio.spraute_engine.core.system.Coins.set(SprauteRuntime.player(), n);
                    }
                }));
            }
            case "coins drop" -> {
                Expr amount = c.arg("amount"), pos = c.arg("pos"), count = c.arg("count");
                b.thenRun(guard(() -> {
                    ServerPlayer p = SprauteRuntime.player();
                    if (p == null) return;
                    Location at = positionSafe(pos);
                    int total = (int) Eval.num(eval.eval(amount));
                    int n = count != null ? Math.max(1, (int) Eval.num(eval.eval(count))) : 1;
                    var rnd = p.getRandom();
                    for (int i = 0; i < n; i++) {
                        int part = total / n + (i < total % n ? 1 : 0);
                        if (part <= 0) continue;
                        var e = new org.sprautestudio.spraute_engine.runtime.orb.SprauteOrb(org.sprautestudio.spraute_engine.runtime.orb.Orbs.ORB.get(), p.serverLevel());
                        e.setKind("coins");
                        // the bigger the sum, the bigger it looks
                        e.setLook(org.sprautestudio.spraute_engine.SprauteEngine.MODID + ":textures/gui/" + (part < 10 ? "coin.png" : part < 100 ? "coin_2.png" : "coin_3.png"));
                        e.setColor(0xFFFFFFFF);
                        e.setSize(part < 10 ? 0.25f : part < 100 ? 0.32f : 0.4f);
                        e.setLife(1000);
                        e.coins = part;
                        e.radius = 10;
                        e.pickup = true;
                        e.setPos(at.x + (rnd.nextDouble() - 0.5), at.y + 0.5, at.z + (rnd.nextDouble() - 0.5));
                        p.serverLevel().addFreshEntity(e);
                    }
                }));
            }
            case "gui slot" -> {
                Expr el = c.arg("el"), item = c.arg("item"), count = c.arg("count");
                b.thenRun(guard(() -> {
                    int n = count != null ? (int) Eval.num(eval.eval(count)) : 1;
                    String id = text(item);
                    ItemStack st = n <= 0 ? ItemStack.EMPTY : GuiSlots.stack(id, n);
                    if (st.isEmpty() && n > 0 && !id.isBlank()) throw new SprauteRuntimeException("Предмет " + id + " не найден");
                    GuiSlots.set(script, text(el), st);
                }));
            }
            case "gui clearslot" -> {
                Expr el = c.arg("el");
                b.thenRun(guard(() -> GuiSlots.set(script, text(el), ItemStack.EMPTY)));
            }
            case "wait slot" -> {
                List<String> ids = new ArrayList<>();
                for (Arg a : c.args) if ("slots".equals(a.param)) ids.add(text(a.value));
                int[] start = {0};
                b.thenRun(() -> start[0] = script.slotSeq);
                b.thenWaitUntil(() -> script.slotSeq > start[0] && (ids.isEmpty() || ids.contains(script.lastSlot)));
            }
            case "gui set" -> {
                Expr el = c.arg("el"), prop = c.arg("prop"), value = c.arg("value");
                b.thenRun(guard(() -> sendGuiUpdate(text(el), text(prop), safeText(value))));
            }
            case "wait button" -> {
                List<String> ids = new ArrayList<>();
                for (Arg a : c.args) if ("buttons".equals(a.param)) ids.add(text(a.value));
                int[] start = {0};
                b.thenRun(() -> start[0] = script.buttonSeq);
                b.thenWaitUntil(() -> script.buttonSeq > start[0] && (ids.isEmpty() || ids.contains(script.lastButton)));
            }
            case "wait guiclose" -> {
                int[] start = {0};
                b.thenRun(() -> start[0] = script.guiCloseSeq);
                b.thenWaitUntil(() -> script.guiCloseSeq > start[0] || script.openGui == null);
            }
            case "stopanim" -> {
                Character ch = animated(b, c);
                if (ch != null) {
                    String layer = layer(c.arg("layer"), "action");
                    ConfiguredAnimation none = Animation.NULL.configure().transitionTime(fadeSeconds(c, 0.25f));
                    b.setAnimation(ch, layer, none);
                }
            }
            case "clearanims" -> {
                Character ch = animated(b, c);
                if (ch != null) b.clearAnimations(ch, fadeSeconds(c, 0.25f));
            }
            case "moveanim" -> {
                Character ch = animated(b, c);
                if (ch != null) {
                    MovementActionType type = switch (text(c.arg("state")).toLowerCase()) {
                        case "walk" -> MovementActionType.WALKING;
                        case "fastwalk" -> MovementActionType.FAST_WALKING;
                        case "run" -> MovementActionType.RUNNING;
                        default -> MovementActionType.IDLE;
                    };
                    Animation a = anim(b, c.arg("anim"));
                    if (a != null) b.overridePredefinedAnimation(ch, type, a.configure());
                }
            }
            case "resetanim" -> {
                Character ch = animated(b, c);
                if (ch != null) b.removeOverridePredefined(ch);
            }
            case "sigh" -> b.sigh(character(c.arg("who")));
            case "shake" -> b.shake(character(c.arg("who")), ticks(c.arg("time")));

            // player & world
            case "teleport" -> b.teleportPlayer(locationSupplier(c.arg("pos")));
            case "effect" -> {
                MobEffect effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(id(c.arg("effect")));
                if (effect == null) throw new SprauteRuntimeException("Эффект " + text(c.arg("effect")) + " не найден");
                b.thenAddEffect(new MobEffectInstance(effect, ticks(c.arg("time")), (int) number(c.arg("level"), 0)));
            }
            case "sound" -> {
                String sname = text(c.arg("sound"));
                Expr at0 = c.arg("pos"), volE = c.arg("volume"), pitchE = c.arg("pitch"), sidE = c.arg("id");
                boolean loopS = c.arg("loop") != null;
                String project = script.deployed.project == null ? "" : script.deployed.project;
                java.nio.file.Path own = project.isEmpty() ? null : Platform.get().gameDir().resolve("spraute").resolve("projects").resolve(project).resolve("sounds");
                boolean projectSound = !sname.contains(":") && own != null
                        && (java.nio.file.Files.isRegularFile(own.resolve(sname + ".ogg")) || java.nio.file.Files.isRegularFile(own.resolve(sname + ".wav")));
                ResourceLocation srl = projectSound ? null : ResourceLocation.tryParse(sname.contains(":") ? sname : "minecraft:" + sname);
                SoundEvent sound = srl == null ? null : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(srl);
                if (sound == null || projectSound) {
                    // the project's own: the clients play the file
                    b.thenRun(guard(() -> {
                        float vol = volE != null ? (float) number(volE, 1) : 1f, pt = pitchE != null ? (float) number(pitchE, 1) : 1f;
                        String sid = sidE != null ? safeText(sidE) : "";
                        double sx = 0, sy = 0, sz = 0;
                        if (at0 != null) {
                            Location l = positionSafe(at0);
                            sx = l.x;
                            sy = l.y;
                            sz = l.z;
                        }
                        org.sprautestudio.spraute_engine.net.SprauteNetwork.sendToAll(new org.sprautestudio.spraute_engine.net.Packets.SoundPlay(
                                project, sname, sx, sy, sz, at0 != null, vol, pt, loopS, sid));
                    }));
                    if (!projectSound) SprauteRuntime.get().report(script.getId(), "Звук «" + sname + "» не найден ни в игре, ни в папке «Звуки» проекта " + project);
                    break;
                }
                if (volE != null || pitchE != null) {
                    Expr atV = at0;
                    b.thenRun(guard(() -> {
                        ServerPlayer p = SprauteRuntime.player();
                        if (p == null) return;
                        BlockPos bp = atV != null ? BlockPos.containing(positionSafe(atV)) : p.blockPosition();
                        p.level().playSound(null, bp, sound, net.minecraft.sounds.SoundSource.MASTER, volE != null ? (float) number(volE, 1) : 1f,
                                pitchE != null ? (float) number(pitchE, 1) : 1f);
                    }));
                    break;
                }
                Expr at = c.arg("pos");
                Supplier<BlockPos> pos = at != null
                        ? () -> BlockPos.containing(positionSafe(at))
                        : () -> {
                            ServerPlayer p = SprauteRuntime.player();
                            return p != null ? p.blockPosition() : BlockPos.ZERO;
                        };
                b.playSound(pos, sound);
            }
            case "stopsound" -> {
                Expr sidE = c.arg("id");
                b.thenRun(guard(() -> org.sprautestudio.spraute_engine.net.SprauteNetwork.sendToAll(
                        new org.sprautestudio.spraute_engine.net.Packets.SoundStop(sidE != null ? safeText(sidE) : ""))));
            }
            case "give" -> {
                ItemStack stack = itemStack(c.arg("item"), (int) number(c.arg("count"), 1));
                b.thenRun(guard(() -> {
                    ServerPlayer p = SprauteRuntime.player();
                    if (p != null && !p.getInventory().add(stack.copy())) p.drop(stack.copy(), false);
                }));
            }
            case "command" -> {
                Expr cmd = c.arg("command");
                b.thenRun(guard(() -> {
                    MinecraftServer server = Platform.get().server();
                    if (server == null) return;
                    String line = safeText(cmd);
                    if (line.startsWith("/")) line = line.substring(1);
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), line);
                }));
            }

            // screen
            case "blackout" -> b.blackout(ticks(c.arg("time")));
            case "flash" -> b.flash(ticks(c.arg("time")));
            case "screen" -> b.screenColor((int) number(c.arg("r"), 0), (int) number(c.arg("g"), 0),
                    (int) number(c.arg("b"), 0), (int) number(c.arg("a"), 255));
            case "eyes" -> b.close_eyes(ticks(c.arg("close")), ticks(c.arg("hold")), ticks(c.arg("open")));

            // logic
            case "flag set" -> {
                Expr name = c.arg("name"), value = c.arg("value");
                b.thenRun(guard(() -> {
                    if (value != null) script.getFlags().set(safeText(name), safeText(value));
                    else script.getFlags().set(safeText(name));
                }));
            }
            case "js" -> {
                Expr codeE = c.arg("code");
                b.thenRun(guard(() -> {
                    String code = codeE instanceof org.sprautestudio.spraute_engine.lang.Ast.StringLit sl ? sl.value.replace("\\{", "{") : safeText(codeE);
                    JsEngine.run(script, code);
                }));
            }
            case "setnbt" -> {
                Expr t = c.arg("target"), p = c.arg("path"), v = c.arg("value");
                b.thenRun(guard(() -> Bridges.setNbt(eval.ref(t), text(p), eval.eval(v))));
            }
            case "setattr" -> {
                Expr t = c.arg("target"), a = c.arg("attr"), v = c.arg("value");
                b.thenRun(guard(() -> Bridges.setAttr(eval.ref(t), text(a), number(v, 0))));
            }
            case "setscore" -> {
                Expr t = c.arg("target"), o = c.arg("objective"), v = c.arg("value");
                boolean add = c.arg("add") != null;
                b.thenRun(guard(() -> Bridges.setScore(eval.ref(t), text(o), number(v, 0), add)));
            }
            case "global set", "global add" -> {
                Expr name = c.arg("name"), value = c.arg("value");
                boolean add = c.key.equals("global add");
                b.thenRun(guard(() -> {
                    Object v = eval.eval(value);
                    if (add) Globals.add(safeText(name), Eval.num(v));
                    else Globals.set(safeText(name), v);
                }));
            }
            case "global clear" -> {
                Expr name = c.arg("name");
                b.thenRun(guard(() -> Globals.clear(safeText(name))));
            }
            case "flag clear" -> {
                Expr name = c.arg("name");
                b.thenRun(guard(() -> script.getFlags().remove(safeText(name))));
            }
            case "checkpoint" -> b.saveState(text(c.arg("name")));
            case "restore" -> b.gotoSaveState(text(c.arg("name")));
            case "ensure active" -> b.ensureScriptActive(scriptIds(c));
            case "ensure inactive" -> b.ensureScriptInactive(scriptIds(c));
            case "log" -> {
                Expr text = c.arg("text");
                b.thenRun(() -> SprauteRuntime.get().log(script.getId(), safeText(text)));
            }
            default -> {
                org.sprautestudio.spraute_engine.lang.EventDefs.Def ev = c.key.startsWith("wait ")
                        ? org.sprautestudio.spraute_engine.lang.EventDefs.get(c.key.substring(5)) : null;
                if (ev == null) throw new SprauteRuntimeException("Команда '" + c.key + "' не поддерживается рантаймом");
                waitEvent(b, ev, c.arg("what"));
            }
        }
    }

    // -------------------------------------------------------------------- GUIs

    // ---------------------------------------------------------------- camera

    private static com.google.gson.JsonObject op(String name) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("op", name);
        return o;
    }

    private void camera(com.google.gson.JsonObject o) {
        script.cameraOp(o);
        org.sprautestudio.spraute_engine.net.SprauteNetwork.sendToAll(new org.sprautestudio.spraute_engine.net.Packets.CameraOp(script.getId(), o.toString()));
    }

    /** time and ease of a camera command. */
    private void timing(com.google.gson.JsonObject o, CommandStmt c) {
        if (c.arg("time") != null) o.addProperty("time", ticks(c.arg("time")) / 20.0);
        Expr ease = c.arg("ease");
        if (ease != null) o.addProperty("ease", ease instanceof Ident id ? id.name : text(ease));
    }

    /** A camera look target: an entity id (player, character, nearest mob) or a point. */
    private void lookTarget(com.google.gson.JsonObject o, Expr target) {
        net.minecraft.world.entity.Entity en = entityOf(target);
        if (en != null) {
            o.addProperty("lookEntity", en.getId());
            return;
        }
        Location l = positionSafe(target);
        o.addProperty("lookX", l.x);
        o.addProperty("lookY", l.y);
        o.addProperty("lookZ", l.z);
    }

    /**
     * The texture of a skin value: "" / reset — back to the usual one; a full id
     * (spraute_engine:textures/entity/humanoid.png) as is; else a picture of the project.
     */
    private ResourceLocation skinTexture(String value) {
        String v = value.trim();
        if (v.isEmpty() || v.equals("reset") || v.equals("default") || v.equals("none")) return null;
        if (v.contains(":")) {
            ResourceLocation rl = ResourceLocation.tryParse(v);
            if (rl == null) throw new SprauteRuntimeException("Неверный id текстуры '" + v + "'");
            return rl;
        }
        return Images.texture(script.deployed.project, v);
    }

    /** skin who "texture": a character's texture or the player's skin. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void skin(ScriptPerformerBuilder b, Expr who, Expr skinE) {
        boolean fixed = skinE instanceof StringLit s && !s.value.contains("{");
        if (who instanceof Ident id && id.name.equals("player")) {
            if (fixed) {
                ResourceLocation rl = skinTexture(((StringLit) skinE).value);
                if (rl == null) b.playerTextureReset();
                else b.playerTextureOverride(rl);
            } else {
                b.thenRun(guard(() -> {
                    ResourceLocation rl = skinTexture(text(skinE));
                    org.sprautestudio.spraute_engine.core.system.PlayerTexture.set(SprauteRuntime.player(), rl);
                }));
            }
            return;
        }
        if (!(who instanceof Ident id) || !script.characters.containsKey(id.name)) {
            throw new SprauteRuntimeException("skin: укажите персонажа (character) или player");
        }
        Character ch = script.characters.get(id.name);
        if (fixed) {
            b.entityTextureOverride(ch, skinTexture(((StringLit) skinE).value));
            return;
        }
        b.thenRun(guard(() -> {
            ResourceLocation rl = skinTexture(text(skinE));
            if (script.optional((Character<?>) ch).isPresent()) org.sprautestudio.spraute_engine.core.script.Characters.state(script, ch, "texture", en -> {
                if (en instanceof AnimatedEntity ae) ae.setTextureOverride(rl);
            });
            else throw new SprauteRuntimeException("skin: персонаж " + id.name + " ещё не появился (spawn)");
        }));
    }

    /** A time of day from a number or a word (day, noon, night...). */
    private long timeOfDay(Expr e) {
        Object v = e instanceof Ident id ? id.name : eval.eval(e);
        if (v instanceof String s) {
            // "hours:minutes" on a 24-hour face; the game's day starts at six in the morning
            if (s.trim().matches("\\d{1,2}:\\d{2}")) {
                String[] hm = s.trim().split(":");
                return Math.floorMod((Integer.parseInt(hm[0]) - 6) * 1000L + Integer.parseInt(hm[1]) * 1000L / 60, 24000L);
            }
            return switch (s.trim().toLowerCase()) {
                case "sunrise", "dawn" -> 0;
                case "day", "morning" -> 1000;
                case "noon" -> 6000;
                case "sunset", "evening" -> 12000;
                case "night" -> 13000;
                case "midnight" -> 18000;
                default -> (long) Eval.num(s);
            };
        }
        return (long) Eval.num(v);
    }

    /** The entity a target names, or null for points. */
    private net.minecraft.world.entity.Entity entityOf(Expr e) {
        if (e instanceof Ident id) {
            if (id.name.equals("player")) return SprauteRuntime.player();
            if (id.name.equals("actor") && !script.vars.containsKey("actor")) return eval.actor();
            Character<?> ch = script.characters.get(id.name);
            if (ch != null) return script.optional(ch).orElse(null);
            if (script.vars.get(id.name) instanceof EntityRef r) return r.get();
            return null;
        }
        if (e instanceof StringLit s) return eval.nearestMob(s.value);
        if (e instanceof Member || e instanceof Call || e instanceof Index) {
            try {
                if (eval.ref(e) instanceof EntityRef r) return r.get();
            } catch (SprauteRuntimeException ignored) {
            }
        }
        return null;
    }

    /** Set while the event command of an "on" handler is emitted: its cursor keeps going, so no event is missed. */
    private boolean handlerWait;
    /** Set while the body of an "on key" handler is emitted. */
    private boolean keyHandler;

    private void waitEvent(ScriptPerformerBuilder b, org.sprautestudio.spraute_engine.lang.EventDefs.Def ev, Expr what) {
        // an event of Forge or a mod: start listening to it on Forge's bus
        if (ev.name().equals("forge") && what != null) b.thenRun(guard(() -> Platform.get().listenLoaderEvent(text(what))));
        waitLog(b, ev.name(), () -> eventFilter(ev, what));
    }

    /** Waits for an event of the story's event log matching a filter built when the wait starts. */
    private void waitLog(ScriptPerformerBuilder b, String type,
                         Supplier<java.util.function.Predicate<java.util.Map<String, Object>>> makeFilter) {
        boolean persistent = handlerWait;
        long[] cursor = {-1};
        java.util.function.Predicate<java.util.Map<String, Object>>[] filter = new java.util.function.Predicate[1];
        b.thenRun(() -> {
            if (!persistent || cursor[0] < 0) cursor[0] = script.events.seq();
            filter[0] = makeFilter.get();
        });
        b.thenWaitUntil(() -> {
            EventLog.Entry e = script.events.find(type, cursor[0], filter[0] == null ? d -> true : filter[0]);
            if (e == null) return false;
            cursor[0] = e.seq();
            script.event = e.data();
            return true;
        });
    }

    /** Test of an event's data against the wait's filter: ids, a story character, player or any mob. */
    private java.util.function.Predicate<java.util.Map<String, Object>> eventFilter(org.sprautestudio.spraute_engine.lang.EventDefs.Def ev, Expr what) {
        if (what == null || ev.filter() == null) return d -> true;
        if (ev.filter() == org.sprautestudio.spraute_engine.lang.ParamType.TARGET && what instanceof Ident id) {
            if (id.name.equals("player")) return d -> "player".equals(d.get("entity"));
            // a variable holding an entity: exactly that one; a list of entities: any of them
            Object held = script.vars.get(id.name);
            if (held instanceof EntityRef er) return d -> er.equals(d.get("ref"));
            if (held instanceof List<?> list) return d -> list.contains(d.get("ref"));
            return d -> id.name.equals(d.get("character"));
        }
        String v;
        try {
            v = text(what).trim();
        } catch (SprauteRuntimeException ex) {
            SprauteRuntime.get().report(script.getId(), ex.getMessage());
            return d -> false;
        }
        if (ev.filter() == org.sprautestudio.spraute_engine.lang.ParamType.TARGET && (v.equals("mob") || v.equals("any"))) return d -> !"player".equals(d.get("entity")) && !"".equals(d.get("entity"));
        String full = v.contains(":") ? v : "minecraft:" + v;
        java.util.List<String> keys = ev.matches();
        return d -> {
            for (String k : keys) {
                Object x = d.get(k);
                if (x != null && (x.toString().equals(v) || x.toString().equals(full))) return true;
            }
            return false;
        };
    }

    private void timeTask(ScriptPerformerBuilder b, CommandStmt c) {
        Expr idE = c.arg("id"), prE = c.arg("priority"), spE = c.arg("speed"), unE = c.arg("until");
        b.thenRun(guard(() -> {
            java.util.function.BooleanSupplier until = unE == null ? null : () -> {
                try {
                    return Eval.truthy(eval.eval(unE));
                } catch (SprauteRuntimeException ex) {
                    return true;
                }
            };
            org.sprautestudio.spraute_engine.core.system.WorldClock.addTask(text(idE), (int) number(prE, 1), number(spE, 1), until);
        }));
    }

    private void guiUpdate(ScriptPerformerBuilder b, Expr el, String prop, Expr value) {
        b.thenRun(guard(() -> sendGuiUpdate(text(el), prop, safeText(value))));
    }

    private void guiUpdateConst(ScriptPerformerBuilder b, Expr el, String prop, String value) {
        b.thenRun(guard(() -> sendGuiUpdate(text(el), prop, value)));
    }

    private void sendGuiUpdate(String element, String prop, String value) {
        ServerPlayer p = SprauteRuntime.player();
        if (p != null) org.sprautestudio.spraute_engine.net.SprauteNetwork.sendTo(p,
                new org.sprautestudio.spraute_engine.net.Packets.GuiUpdate(script.getId(), element, prop, value));
    }

    // ---------------------------------------------------------------- animations

    private void animation(ScriptPerformerBuilder b, CommandStmt c) {
        Character ch = animated(b, c);
        if (ch == null) return;
        Animation a = null;
        if (c.arg("who") instanceof Ident wid && script.characterModels.containsKey(wid.name)) {
            var look = org.sprautestudio.spraute_engine.runtime.models.EntityLibrary.look(script.characterModels.get(wid.name));
            if (look != null) a = look.animation(text(c.arg("anim"))).orElse(null);
        }
        if (a == null) a = anim(b, c.arg("anim"));
        if (a == null) return;
        boolean face = c.key.equals("face");
        String layer = face ? "emotion" : layer(c.arg("layer"), "action");
        double speed = number(c.arg("speed"), 1);
        int times = c.arg("times") != null ? Math.max(1, (int) number(c.arg("times"), 1)) : 0;
        ConfiguredAnimation ca = a.configure().transitionTime(fadeSeconds(c, 0.25f));
        if (c.arg("from") != null) ca.startTime(ticks(c.arg("from")) / 20f);
        if (speed != 1) ca.speed((float) speed);
        if (c.arg("loop") != null || times > 1) ca.loopMode(LoopMode.LOOP);
        else if (c.arg("hold") != null) ca.loopMode(LoopMode.HOLD_ON_LAST_FRAME);
        b.setAnimation(ch, layer, ca);
        int length = Math.max(1, Anims.lengthTicks(a, speed));
        if (times > 0) {
            // play N times, then release the layer
            b.thenDelay(length * times);
            ConfiguredAnimation none = Animation.NULL.configure().transitionTime(0.2f);
            b.setAnimation(ch, layer, none);
        } else if (c.arg("wait") != null && c.arg("loop") == null) {
            b.thenDelay(length);
        }
    }

    private static final Map<String, Boolean> ANIMATED_TYPES = new HashMap<>();

    /** The command's character if its entity has animations, otherwise reports and returns null. */
    private Character animated(ScriptPerformerBuilder b, CommandStmt c) {
        Expr who = c.arg("who");
        Character ch = character(who);
        net.minecraft.world.entity.EntityType<?> type = ch.getType();
        String key = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type));
        Boolean ok = ANIMATED_TYPES.get(key);
        if (ok == null) {
            ok = true;
            MinecraftServer server = Platform.get().server();
            if (server != null) {
                try {
                    net.minecraft.world.entity.Entity probe = type.create(server.overworld());
                    ok = probe instanceof AnimatedEntity;
                    if (probe != null) probe.discard();
                } catch (RuntimeException ignored) {
                    // unknown: let it try
                }
            }
            ANIMATED_TYPES.put(key, ok);
        }
        if (!ok) {
            String name = who instanceof Ident id ? id.name : "?";
            b.thenRun(() -> SprauteRuntime.get().report(script.getId(),
                    "Сущность персонажа " + name + " (" + key + ") не поддерживает анимации — команда " + c.key + " пропущена"));
            return null;
        }
        return ch;
    }

    /** Resolves an animation id; a missing one is reported when the line runs and the command is skipped. */
    private Animation anim(ScriptPerformerBuilder b, Expr e) {
        String id = text(e);
        Animation a = Anims.resolve(id).orElse(null);
        if (a == null) b.thenRun(() -> SprauteRuntime.get().report(script.getId(), "Анимация '" + id + "' не найдена — команда пропущена"));
        return a;
    }

    private String layer(Expr e, String fallback) {
        if (e == null) return fallback;
        String raw = e instanceof NumberLit n ? String.valueOf((int) n.value) : text(e);
        String name = Specs.layerName(raw);
        if (name == null) throw new SprauteRuntimeException("Неизвестный слой анимации '" + raw + "'");
        return name;
    }

    private float fadeSeconds(CommandStmt c, float fallback) {
        Expr f = c.arg("fade");
        return f == null ? fallback : ticks(f) / 20f;
    }

    // ------------------------------------------------------------------- helpers

    /** A step whose error stops the story: the environment reports it with the number and the cause. */
    private Runnable guard(Runnable r) {
        return r;
    }

    private int speakerSlot(ScriptPerformerBuilder b, Expr who) {
        if (who instanceof NumberLit n) return (int) n.value;
        String key;
        if (who instanceof Ident id && script.characters.containsKey(id.name)) key = "char:" + id.name;
        else if (who instanceof Ident id && id.name.equals("player")) key = "player";
        else key = "name:" + text(who);
        Integer slot = speakerSlots.get(key);
        int s;
        if (slot != null) s = slot;
        else {
            // not bound with "speaker" yet: the next slot
            s = nextSlot;
            nextSlot = (nextSlot + 1) % 2;
            speakerSlots.values().removeIf(v -> v == s);
            speakerSlots.put(key, s);
        }
        // bound again before every line, where it is said: each handler (on …), async branch and the story itself
        // has its own speech state, and a branch that bound the speaker first may never have run
        if (key.startsWith("char:")) speechEntity(b, s, key.substring(5));
        else if (key.equals("player")) speechPlayer(b, s);
        else b.thenSetSpeechEntity(s, key.substring(5));
        return s;
    }

    /**
     * Binds a character to a speech slot, with its name and icon.
     */
    private void speechEntity(ScriptPerformerBuilder b, int slot, String name) {
        Character ch = character(name);
        headSync(b, name);
        String icon = script.speechIcons.getOrDefault(name, "generic_man");
        // name and colour read when the story gets here: "name" and "namecolor" change them on the way
        b.append(p -> new SpeechNameAction((ScriptPerformer) p, slot, () -> script.speechNames.getOrDefault(name, ch.getName()), () -> nameColor(name), icon));
    }

    /** Tells the client which entity a head icon shows (the character may have respawned since). */
    private void headSync(ScriptPerformerBuilder b, String name) {
        if (!script.headSpeaker(name)) return;
        b.thenRun(() -> {
            ServerPlayer p = SprauteRuntime.player();
            Character<?> ch = script.characters.get(name);
            net.minecraft.world.entity.Entity en = ch != null ? script.optional(ch).orElse(null) : null;
            // a character that has just appeared gets its look now, before the client draws its head
            String modelKey = script.characterModels.get(name);
            if (modelKey != null && en instanceof org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity me
                    && !modelKey.equals(me.model())) me.setModel(modelKey);
            // gone or far away, the head is still drawn: from the last look the client saw, or from the model
            if (en != null || modelKey != null) org.sprautestudio.spraute_engine.net.SprauteNetwork.sendToAll(new org.sprautestudio.spraute_engine.net.Packets.SpeechHead(script.getId(), Images.slug(name), en != null ? en.getId() : -1,
                            modelKey != null ? modelKey : ""));
        });
    }

    /** The player as a speaker: their name and the face of their skin. */
    /** The character's own name colour, else the one set for everybody in the engine settings, else none. */
    private Integer nameColor(String key) {
        Integer own = script.nameColors.get(key);
        if (own != null) return own;
        int all = org.sprautestudio.spraute_engine.engine.EngineServer.config().chatLog.nameColor;
        return all < 0 ? null : all & 0xFFFFFF;
    }

    private void speechPlayer(ScriptPerformerBuilder b, int slot) {
        b.append(pf -> new SpeechNameAction((ScriptPerformer) pf, slot, () -> {
            String own = script.speechNames.get("player");
            if (own != null) return own;
            ServerPlayer p = SprauteRuntime.player();
            return p != null ? p.getGameProfile().getName() : "Игрок";
        }, () -> nameColor("player"), playerHead()));
    }

    /** The player's head icon with the engine settings' size and shift. */
    private static String playerHead() {
        var c = org.sprautestudio.spraute_engine.engine.EngineServer.config().chatLog;
        return "spraute/head/player/" + Math.round(Math.max(0.2f, c.playerHeadScale) * 100) + "/" + c.playerHeadX + "/" + c.playerHeadY;
    }

    private Character character(Expr e) {
        if (!(e instanceof Ident id)) throw new SprauteRuntimeException("Ожидался персонаж");
        return character(id.name);
    }

    private Character character(String name) {
        Character<?> c = script.characters.get(name);
        if (c == null) throw new SprauteRuntimeException("Персонаж '" + name + "' не объявлен");
        return c;
    }

    private Supplier<Location> eyesSupplier(Expr e) {
        return () -> {
            try {
                Location l = eval.eyes(e);
                if (l != null) return l;
            } catch (SprauteRuntimeException ex) {
                SprauteRuntime.get().report(script.getId(), ex.getMessage());
            }
            return positionSafe(e);
        };
    }

    private Supplier<Location> locationSupplier(Expr e) {
        return () -> positionSafe(e);
    }

    /** An effect's JSON: the project's own, else a ready one. */
    /** The body a boss command drives: a character, the player, a summoned entity in a variable. */
    private net.minecraft.world.entity.Entity bossBody(Expr who, String cmd) {
        Object v = eval.ref(who);
        if (v instanceof EntityRef r) return r.require();
        throw new SprauteRuntimeException(cmd + ": нужен персонаж, игрок или существо, а не точка");
    }

    /** An effect for a boss command, resolved now (null when not given). */
    private BossMoves.Fx bossFx(Expr e) {
        if (e == null) return null;
        return new BossMoves.Fx(fxJson(text(e).trim()), script.deployed.project, script.getId(), 1f);
    }

    private String fxJson(String n) {
        String json = SprauteRuntime.get().gui(script.deployed.project, "fx:" + n);
        if (json != null) return json;
        var preset = org.sprautestudio.spraute_engine.fx.FxPresets.get(n);
        if (preset == null) throw new SprauteRuntimeException("Эффект «" + n + "» не найден: его нет в папке «Эффекты» проекта и среди встроенных ("
                + String.join(", ", org.sprautestudio.spraute_engine.fx.FxPresets.names()) + ")");
        return preset.toJson();
    }

    private Location positionSafe(Expr e) {
        try {
            Location l = eval.position(e);
            if (l != null) return l;
        } catch (SprauteRuntimeException ex) {
            SprauteRuntime.get().report(script.getId(), ex.getMessage());
        }
        ServerPlayer p = SprauteRuntime.player();
        return p != null ? new Location(p) : Location.ZERO;
    }

    private BlockPos blockPos(Expr e) {
        return BlockPos.containing(eval.position(e));
    }

    private AABB box(Expr from, Expr to) {
        Location a = eval.position(from), b = eval.position(to);
        return new AABB(a.x, a.y, a.z, b.x, b.y, b.z);
    }

    /** Text evaluated once while building (for APIs that only take a String). */
    private String text(Expr e) {
        if (e instanceof Ident id && !script.vars.containsKey(id.name)) return id.name;
        return Eval.str(eval.eval(e));
    }

    /** Text evaluated each time it is shown. */
    private String safeText(Expr e) {
        try {
            return Eval.str(eval.eval(e));
        } catch (SprauteRuntimeException ex) {
            SprauteRuntime.get().report(script.getId(), ex.getMessage());
            return "?";
        }
    }

    private int ticks(Expr e) {
        if (e instanceof DurationLit d) return d.ticks;
        return (int) Math.round(Eval.num(eval.eval(e)));
    }

    private int ticksSafe(Expr e) {
        try {
            return ticks(e);
        } catch (SprauteRuntimeException ex) {
            SprauteRuntime.get().report(script.getId(), ex.getMessage());
            return 0;
        }
    }

    private double number(Expr e, double fallback) {
        return e == null ? fallback : Eval.num(eval.eval(e));
    }

    private ResourceLocation id(Expr e) {
        String s = text(e);
        return new ResourceLocation(s.contains(":") ? s : "minecraft:" + s);
    }

    private ItemStack itemStack(Expr e, int count) {
        String spec = text(e);
        if (ItemSpec.error(spec) != null) throw new SprauteRuntimeException("Предмет " + spec + ": ошибка в NBT: " + ItemSpec.error(spec));
        ItemStack stack = ItemSpec.stack(spec, count);
        if (stack.isEmpty()) throw new SprauteRuntimeException("Предмет " + spec + " не найден");
        return stack;
    }

    private String emotionName(Expr e) {
        return (e instanceof Ident id ? id.name : text(e)).toUpperCase(java.util.Locale.ROOT);
    }

    private int[] scriptIds(CommandStmt c) {
        List<Integer> ids = new ArrayList<>();
        for (Arg a : c.args) if ("ids".equals(a.param)) ids.add((int) Eval.num(eval.eval(a.value)));
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }
}
