package org.sprautestudio.st_addon_spraute.runtime;

import org.sprautestudio.st_addon_spraute.core.script.Character;
import org.sprautestudio.st_addon_spraute.core.world.Location;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.sprautestudio.st_addon_spraute.lang.Ast.*;
import org.sprautestudio.st_addon_spraute.lang.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runtime evaluator for .sst expressions. Values are Double, String or Boolean. */
public final class Eval {
    private static final Pattern INTERPOLATION = Pattern.compile("(?<!\\\\)\\{([^{}]*)}");

    private final SprauteScriptInstance script;

    Eval(SprauteScriptInstance script) {
        this.script = script;
    }

    public Object eval(Expr e) {
        if (e instanceof NumberLit n) return n.value;
        if (e instanceof DurationLit d) return (double) d.ticks;
        if (e instanceof StringLit s) return interpolate(s.value);
        if (e instanceof BoolLit b) return b.value;
        if (e instanceof Ident id) return ident(id.name);
        if (e instanceof Unary u) {
            Object v = eval(u.operand);
            return u.op.equals("-") ? -num(v) : !truthy(v);
        }
        if (e instanceof Binary b) return binary(b);
        if (e instanceof Call c) return call(c);
        if (e instanceof Tuple t) {
            List<Object> items = new ArrayList<>();
            for (Expr x : t.items) items.add(eval(x));
            return items;
        }
        if (e instanceof ListLit l) {
            List<Object> items = new ArrayList<>();
            for (Expr x : l.items) items.add(eval(x));
            return items;
        }
        if (e instanceof MapLit ml) {
            java.util.Map<String, Object> map = Values.map();
            for (int i = 0; i < ml.keys.size(); i++) map.put(str(eval(ml.keys.get(i))), eval(ml.values.get(i)));
            return map;
        }
        if (e instanceof Index ix) return Values.index(eval(ix.target), eval(ix.index));
        if (e instanceof Member m) {
            Object target = ref(m.target);
            List<Object> args = null;
            if (m.args != null) {
                args = new ArrayList<>();
                for (Expr x : m.args) args.add(ref(x));
            }
            if (target instanceof EntityRef er) return args == null ? WorldApi.get(er, m.name) : WorldApi.call(er, m.name, args);
            if (args == null) return Values.property(target, m.name);
            return Values.method(target, m.name, args);
        }
        throw new SprauteRuntimeException("Неподдерживаемое выражение");
    }

    /**
     * A value where an entity or a point fits: player and characters are their entities, locations
     * their coordinates; anything else is evaluated as usual.
     */
    public Object ref(Expr e) {
        if (e instanceof Ident id) {
            if (id.name.equals("player")) {
                ServerPlayer p = SprauteRuntime.player();
                if (p == null) throw new SprauteRuntimeException("Игрока нет в мире");
                return new EntityRef(p);
            }
            Character<?> ch = script.characters.get(id.name);
            if (ch != null) {
                Entity en = script.optional(ch).orElse(null);
                if (en == null) throw new SprauteRuntimeException("Персонаж " + id.name + " сейчас не в мире (spawn " + id.name + " at ...)");
                return new EntityRef(en);
            }
            if (!script.vars.containsKey(id.name) && script.locationDecls.containsKey(id.name)) return WorldApi.list(script.location(id.name));
        }
        return eval(e);
    }

    private Object ident(String name) {
        if (name.equals("player")) {
            ServerPlayer p = SprauteRuntime.player();
            return p != null ? p.getGameProfile().getName() : "";
        }
        if (script.vars.containsKey(name)) return script.vars.get(name);
        if (script.characters.containsKey(name)) return script.characters.get(name).getName();
        if (script.locationDecls.containsKey(name)) return script.location(name).toString();
        throw new SprauteRuntimeException("Неизвестное имя '" + name + "'");
    }

    private Object binary(Binary b) {
        switch (b.op) {
            case "and":
                return truthy(eval(b.left)) && truthy(eval(b.right));
            case "or":
                return truthy(eval(b.left)) || truthy(eval(b.right));
        }
        Object l = eval(b.left), r = eval(b.right);
        switch (b.op) {
            case "+":
                if (l instanceof String || r instanceof String) return str(l) + str(r);
                if (l instanceof List<?> a && r instanceof List<?> c) {
                    List<Object> out = new ArrayList<>(a);
                    out.addAll(c);
                    return out;
                }
                return num(l) + num(r);
            case "-":
                return num(l) - num(r);
            case "*":
                return num(l) * num(r);
            case "/": {
                double d = num(r);
                if (d == 0) throw new SprauteRuntimeException("Деление на ноль");
                return num(l) / d;
            }
            case "%": {
                double d = num(r);
                if (d == 0) throw new SprauteRuntimeException("Деление на ноль");
                return num(l) % d;
            }
            case "==":
                return equal(l, r);
            case "!=":
                return !equal(l, r);
            case "<":
                return compare(l, r) < 0;
            case "<=":
                return compare(l, r) <= 0;
            case ">":
                return compare(l, r) > 0;
            case ">=":
                return compare(l, r) >= 0;
            default:
                throw new SprauteRuntimeException("Неизвестный оператор " + b.op);
        }
    }

    /** Like ref, but a character that is not in the world now is just nothing (for questions about it). */
    private Object refOrNull(Expr e) {
        try {
            return ref(e);
        } catch (SprauteRuntimeException ex) {
            return null;
        }
    }

    private Object call(Call c) {
        List<Expr> a = c.args;
        switch (c.name) {
            case "random": {
                int n = (int) num(eval(a.get(0)));
                return n <= 0 ? 0.0 : (double) script.getRandom().nextInt(n);
            }
            case "chance":
                return script.getRandom().nextDouble() * 100 < num(eval(a.get(0)));
            case "flag":
                return script.getFlags().has(str(eval(a.get(0))));
            case "js":
                return JsEngine.run(script, str(eval(a.get(0))));
            case "nbt":
                return Bridges.nbt(ref(a.get(0)), a.size() > 1 ? str(eval(a.get(1))) : "");
            case "attr":
                return Bridges.attr(ref(a.get(0)), str(eval(a.get(1))));
            case "score":
                return Bridges.score(ref(a.get(0)), str(eval(a.get(1))));
            case "cmdresult":
                return Bridges.run(str(eval(a.get(0)))).output();
            case "cmdvalue":
                return (double) Bridges.run(str(eval(a.get(0)))).value();
            case "hasmod":
                return Bridges.hasMod(str(eval(a.get(0))));
            case "walking": {
                Object w = ref(a.get(0));
                return w instanceof EntityRef r && org.sprautestudio.st_addon_spraute.path.Walkers.walking(r.get());
            }
            case "global":
                return Globals.get(str(eval(a.get(0))), a.size() > 1 ? eval(a.get(1)) : 0.0);
            case "hasglobal":
                return Globals.has(str(eval(a.get(0))));
            case "flagvalue": {
                String v = script.getFlags().get(str(eval(a.get(0))));
                return v == null ? "" : v;
            }
            case "answer":
                return (double) script.lastAnswer;
            case "near": {
                ServerPlayer p = SprauteRuntime.player();
                Location loc = position(a.get(0));
                return p != null && loc != null && p.position().distanceTo(loc) <= num(eval(a.get(1)));
            }
            case "dist": {
                ServerPlayer p = SprauteRuntime.player();
                Location loc = position(a.get(0));
                return p != null && loc != null ? p.position().distanceTo(loc) : -1.0;
            }
            case "health": {
                ServerPlayer p = SprauteRuntime.player();
                return p != null ? (double) p.getHealth() : 0.0;
            }
            case "daytime": {
                ServerPlayer p = SprauteRuntime.player();
                return p != null ? (double) (p.level().getDayTime() % 24000) : 0.0;
            }
            case "button":
                return script.lastButton;
            case "input":
                return script.guiInputs.getOrDefault(str(eval(a.get(0))), "");
            case "guiopen":
                return script.openGui != null;
            case "event": {
                Object v = script.event.get(str(eval(a.get(0))));
                return v == null ? "" : v;
            }
            case "incutscene":
                return !script.cutscene.isEmpty();
            case "coins":
                try {
                    return (double) org.sprautestudio.st_addon_spraute.core.system.Coins.get();
                } catch (Throwable t) {
                    return 0.0;
                }
            case "slotitem":
                return GuiSlots.id(script.guiSlots.get(str(eval(a.get(0)))));
            case "slotcount": {
                net.minecraft.world.item.ItemStack st = script.guiSlots.get(str(eval(a.get(0))));
                return st == null ? 0.0 : (double) st.getCount();
            }
            case "lastslot":
                return script.lastSlot;
            case "system":
                return org.sprautestudio.st_addon_spraute.engine.EngineServer.enabled(str(eval(a.get(0))));
            case "round":
                return (double) Math.round(num(eval(a.get(0))));
            case "floor":
                return Math.floor(num(eval(a.get(0))));
            case "randint": {
                int lo = (int) Math.round(num(eval(a.get(0)))), hi = (int) Math.round(num(eval(a.get(1))));
                if (hi < lo) {
                    int t = lo;
                    lo = hi;
                    hi = t;
                }
                return (double) (lo + script.getRandom().nextInt(hi - lo + 1));
            }
            case "len":
                return Values.property(eval(a.get(0)), "len");
            case "day": {
                ServerPlayer p = SprauteRuntime.player();
                return p != null ? (double) Math.floorDiv(p.level().getDayTime(), 24000L) : 0.0;
            }
            case "bought":
                return (double) org.sprautestudio.st_addon_spraute.shop.Shops.bought(script.deployed.project, str(eval(a.get(0))), str(eval(a.get(1))));
            case "stock":
                return (double) org.sprautestudio.st_addon_spraute.shop.Shops.stock(script.deployed.project, str(eval(a.get(0))), str(eval(a.get(1))));
            case "hitbox": {
                Object v = ref(a.get(0));
                return v instanceof EntityRef r ? org.sprautestudio.st_addon_spraute.hitbox.Hitboxes.describe(org.sprautestudio.st_addon_spraute.hitbox.Hitboxes.flags(r.require())) : "";
            }
            case "fighthp": {
                Object v = refOrNull(a.get(0));
                return v instanceof EntityRef r ? org.sprautestudio.st_addon_spraute.fight.Fights.hp(r.require()) : 0.0;
            }
            case "fighting": {
                Object v = refOrNull(a.get(0));
                return v instanceof EntityRef r && org.sprautestudio.st_addon_spraute.fight.Fights.fighting(r.require());
            }
            case "defeated": {
                Object v = refOrNull(a.get(0));
                return v instanceof EntityRef r && org.sprautestudio.st_addon_spraute.fight.Fights.defeated(r.require());
            }
            case "cooldown": {
                // true — ready (and starts the wait again), false — still cooling down
                String key = str(eval(a.get(0)));
                int now = Mechanics.now();
                Integer until = script.cooldowns.get(key);
                if (until != null && until > now) return false;
                script.cooldowns.put(key, now + (int) Math.round(num(eval(a.get(1))) * 20));
                return true;
            }
            case "cooldownleft": {
                Integer until = script.cooldowns.get(str(eval(a.get(0))));
                return until == null ? 0.0 : Math.max(0, (until - Mechanics.now()) / 20.0);
            }
            case "ticks":
                return (double) Mechanics.now();
            case "infront": {
                // a point N blocks ahead of someone (flat), standing on the ground there
                Object who = ref(a.get(0));
                if (!(who instanceof EntityRef r)) throw new SprauteRuntimeException("infront: нужен игрок или персонаж");
                net.minecraft.world.entity.Entity en = r.require();
                double d = a.size() > 1 ? num(eval(a.get(1))) : 3;
                net.minecraft.world.phys.Vec3 look = en.getViewVector(1);
                net.minecraft.world.phys.Vec3 flat = new net.minecraft.world.phys.Vec3(look.x, 0, look.z);
                flat = flat.lengthSqr() < 1e-6 ? new net.minecraft.world.phys.Vec3(0, 0, 1) : flat.normalize();
                return WorldApi.list(WorldApi.ground(en.position().add(flat.scale(d))));
            }
            case "hp":
            case "maxhp":
            case "hpfrac": {
                Object v = ref(a.get(0));
                net.minecraft.world.entity.Entity en = v instanceof EntityRef r ? r.get() : null;
                return c.name.equals("hp") ? BossMoves.hp(en) : c.name.equals("maxhp") ? BossMoves.maxHp(en) : BossMoves.frac(en);
            }
            case "parts":
            case "lastpart":
            case "parton":
            case "partpos": {
                Object v = ref(a.get(0));
                net.minecraft.world.entity.Entity en = v instanceof EntityRef r ? r.get() : null;
                var me = en instanceof org.sprautestudio.st_addon_spraute.runtime.models.SprauteModelEntity m ? m : null;
                switch (c.name) {
                    case "parts":
                        return me == null ? new java.util.ArrayList<Object>() : new java.util.ArrayList<Object>(me.partNames());
                    case "lastpart":
                        return me == null ? "" : me.lastPart();
                    case "parton":
                        return me != null && me.partEnabled(str(eval(a.get(1))));
                    default: {
                        String n = str(eval(a.get(1)));
                        var box = me != null ? me.partBox(n) : null;
                        if (box == null) throw new SprauteRuntimeException("partpos: нет части «" + n + "»");
                        return WorldApi.list(box.getCenter());
                    }
                }
            }
            case "moving": {
                Object v = ref(a.get(0));
                return v instanceof EntityRef r && BossMoves.moving(r.get());
            }
            case "attacking": {
                Object v = refOrNull(a.get(0));
                return v instanceof EntityRef r && r.get() != null && AttackBoxes.active(r.get());
            }
            case "attackhits": {
                Object v = refOrNull(a.get(0));
                return v instanceof EntityRef r && r.get() != null ? (double) AttackBoxes.hits(r.get()) : 0.0;
            }
            case "beaming": {
                Object v = ref(a.get(0));
                return v instanceof EntityRef r && r.get() != null && BossMoves.beaming(r.get());
            }
            case "behind": {
                // N blocks behind someone (flat), standing on the ground there
                Object who = ref(a.get(0));
                if (!(who instanceof EntityRef r)) throw new SprauteRuntimeException("behind: нужен игрок или персонаж");
                net.minecraft.world.entity.Entity en = r.require();
                double d = a.size() > 1 ? num(eval(a.get(1))) : 3;
                net.minecraft.world.phys.Vec3 look = en.getViewVector(1);
                net.minecraft.world.phys.Vec3 flat = new net.minecraft.world.phys.Vec3(look.x, 0, look.z);
                flat = flat.lengthSqr() < 1e-6 ? new net.minecraft.world.phys.Vec3(0, 0, 1) : flat.normalize();
                return WorldApi.list(WorldApi.ground(en.position().subtract(flat.scale(d))));
            }
            case "around": {
                net.minecraft.world.phys.Vec3 ctr = WorldApi.vec(ref(a.get(0)));
                double r = num(eval(a.get(1))), ang = Math.toRadians(a.size() > 2 ? num(eval(a.get(2))) : 0);
                return WorldApi.list(WorldApi.ground(ctr.add(Math.cos(ang) * r, 0, Math.sin(ang) * r)));
            }
            case "randaround": {
                net.minecraft.world.phys.Vec3 ctr = WorldApi.vec(ref(a.get(0)));
                double r = num(eval(a.get(1)));
                double ang = script.getRandom().nextDouble() * Math.PI * 2, rr = r * Math.sqrt(script.getRandom().nextDouble());
                return WorldApi.list(WorldApi.ground(ctr.add(Math.cos(ang) * rr, 0, Math.sin(ang) * rr)));
            }
            case "dirto": {
                net.minecraft.world.phys.Vec3 f = WorldApi.vec(ref(a.get(0))), t = WorldApi.vec(ref(a.get(1)));
                net.minecraft.world.phys.Vec3 d = new net.minecraft.world.phys.Vec3(t.x - f.x, 0, t.z - f.z);
                d = d.lengthSqr() < 1e-6 ? new net.minecraft.world.phys.Vec3(0, 0, 1) : d.normalize();
                return WorldApi.list(d);
            }
            case "ground":
                return WorldApi.list(WorldApi.ground(WorldApi.vec(ref(a.get(0)))));
            case "aimpoint": {
                Object who = ref(a.get(0));
                if (!(who instanceof EntityRef r)) throw new SprauteRuntimeException("aimpoint: нужен игрок или персонаж");
                return WorldApi.list(Mechanics.aimPoint(r.require(), a.size() > 1 ? num(eval(a.get(1))) : 48));
            }
            case "raycast": {
                Object from = ref(a.get(0));
                Object to = a.size() > 1 ? ref(a.get(1)) : null;
                return Raycast.raycast(from, to, a.size() > 2 ? num(eval(a.get(2))) : 64);
            }
            case "lookblock":
                return Raycast.lookBlock(ref(a.get(0)), a.size() > 1 ? num(eval(a.get(1))) : 64);
            case "lookentity":
                return Raycast.lookEntity(ref(a.get(0)), a.size() > 1 ? num(eval(a.get(1))) : 64);
            case "cansee":
                return Raycast.canSee(ref(a.get(0)), ref(a.get(1)));
            case "lookingat":
                return Raycast.looksAt(ref(a.get(0)), ref(a.get(1)), a.size() > 2 ? num(eval(a.get(2))) : 64);
            case "inzone": {
                String zn = str(eval(a.get(0)));
                if (a.size() < 2) return org.sprautestudio.st_addon_spraute.zone.Zones.playerIn(SprauteRuntime.player(), script.deployed.project, zn);
                return org.sprautestudio.st_addon_spraute.zone.Zones.contains(script.deployed.project, zn, WorldApi.vec(ref(a.get(1))));
            }
            case "zonepoint":
                return WorldApi.list(org.sprautestudio.st_addon_spraute.zone.Zones.randomPoint(script.deployed.project, str(eval(a.get(0)))));
            case "zonecenter":
                return WorldApi.list(org.sprautestudio.st_addon_spraute.zone.Zones.require(script.deployed.project, str(eval(a.get(0)))).center());
            case "zones":
                return new java.util.ArrayList<Object>(org.sprautestudio.st_addon_spraute.zone.Zones.zonesOf(SprauteRuntime.player(), script.deployed.project));
            case "queststate":
                return org.sprautestudio.st_addon_spraute.quest.Quests.state(SprauteRuntime.player(), script.deployed.project, str(eval(a.get(0))));
            case "questprogress":
                return (double) org.sprautestudio.st_addon_spraute.quest.Quests.progressOf(SprauteRuntime.player(), script.deployed.project, str(eval(a.get(0))), str(eval(a.get(1))));
            case "summon":
                return WorldApi.summon(str(eval(a.get(0))), ref(a.get(1)));
            case "entities":
                return WorldApi.entities(str(eval(a.get(0))), ref(a.get(1)), a.size() > 2 ? num(eval(a.get(2))) : 16);
            case "nearest":
                return WorldApi.nearest(str(eval(a.get(0))), ref(a.get(1)), a.size() > 2 ? num(eval(a.get(2))) : 32);
            case "alive": {
                // not spawned (or gone) is simply "not alive"
                Object v = refOrNull(a.get(0));
                return v instanceof EntityRef r && r.alive();
            }
            case "block":
                return WorldApi.block(ref(a.get(0)));
            case "blockprop":
                return WorldApi.blockProp(ref(a.get(0)), str(eval(a.get(1))));
            case "inbox":
                return WorldApi.inBox(ref(a.get(0)), ref(a.get(1)), ref(a.get(2)));
            case "range": {
                double x = num(eval(a.get(0)));
                if (a.size() == 1) return Values.range(0, x, 1);
                return Values.range(x, num(eval(a.get(1))), a.size() > 2 ? num(eval(a.get(2))) : 1);
            }
            case "list": {
                List<Object> out = new ArrayList<>();
                for (Expr x : a) out.add(eval(x));
                return out;
            }
            case "has":
                return Values.method(eval(a.get(0)), "has", List.of(eval(a.get(1))));
            case "indexof":
                return Values.method(eval(a.get(0)), "indexOf", List.of(eval(a.get(1))));
            case "keys":
                return Values.property(eval(a.get(0)), "keys");
            case "values":
                return Values.property(eval(a.get(0)), "values");
            case "sum":
                return Values.method(eval(a.get(0)), "sum", List.of());
            case "join":
                return Values.join(Values.iterable(eval(a.get(0))), a.size() > 1 ? str(eval(a.get(1))) : ", ");
            case "split":
                return Values.split(str(eval(a.get(0))), a.size() > 1 ? str(eval(a.get(1))) : " ");
            case "sorted": {
                List<Object> out = Values.iterable(eval(a.get(0)));
                out.sort(Values::compare);
                return out;
            }
            case "pick": {
                List<Object> l = Values.iterable(eval(a.get(0)));
                return l.isEmpty() ? "" : l.get(script.getRandom().nextInt(l.size()));
            }
            case "vadd":
                return Values.vecOp(ref(a.get(0)), ref(a.get(1)), '+');
            case "vsub":
                return Values.vecOp(ref(a.get(0)), ref(a.get(1)), '-');
            case "vmul":
                return Values.vecOp(ref(a.get(0)), eval(a.get(1)), '*');
            case "vlen":
                return Values.length(ref(a.get(0)));
            case "vdist":
                return Values.length(Values.vecOp(ref(a.get(0)), ref(a.get(1)), '-'));
            case "vnorm": {
                Object v = ref(a.get(0));
                double len = Values.length(v);
                return len == 0 ? v : Values.vecOp(v, 1 / len, '*');
            }
            case "spawn", "tp": {
                Character<?> ch = characterArg(a.get(0), c.name);
                Location loc = position(a.get(1));
                if (loc == null) throw new SprauteRuntimeException(c.name + "(): точка не найдена");
                org.sprautestudio.st_addon_spraute.core.script.Characters.place(ch, loc);
                if (c.name.equals("spawn")) afterSpawn(((Ident) a.get(0)).name, ch);
                return true;
            }
            case "despawn": {
                Character<?> ch = characterArg(a.get(0), c.name);
                org.sprautestudio.st_addon_spraute.core.script.Characters.remove(ch);
                return true;
            }
            case "give":
                return WorldApi.give(str(eval(a.get(0))), a.size() > 1 ? (int) num(eval(a.get(1))) : 1);
            case "take":
                return WorldApi.take(str(eval(a.get(0))), a.size() > 1 ? (int) num(eval(a.get(1))) : 1);
            case "teleport": {
                ServerPlayer p = SprauteRuntime.player();
                Location loc = position(a.get(0));
                if (p == null || loc == null) return false;
                p.teleportTo(loc.x, loc.y, loc.z);
                return true;
            }
            case "effect": {
                ServerPlayer p = SprauteRuntime.player();
                if (p == null) return false;
                List<Object> args = new java.util.ArrayList<>();
                for (Expr x : a) args.add(eval(x));
                WorldApi.call(new EntityRef(p), "effect", args);
                return true;
            }
            case "setblock": {
                Object at = ref(a.get(0));
                String was = WorldApi.block(at);
                WorldApi.setBlock(at, str(eval(a.get(1))));
                return was;
            }
            case "explode":
                Mechanics.explode(WorldApi.level(), WorldApi.vec(ref(a.get(0))), (float) (a.size() > 1 ? num(eval(a.get(1))) : 3), false, false, null);
                return true;
            case "lightning":
                Mechanics.lightning(WorldApi.level(), WorldApi.vec(ref(a.get(0))), false);
                return true;
            case "message": {
                net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal(str(eval(a.get(0))));
                for (ServerPlayer p : SprauteRuntime.players()) p.sendSystemMessage(msg);
                return true;
            }
            case "actionbar":
                Mechanics.actionbar(str(eval(a.get(0))));
                return true;
            case "command":
                return Bridges.run(str(eval(a.get(0)))).output();
            case "itemcount":
                return WorldApi.itemCount(str(eval(a.get(0))));
            case "hasitem":
                return WorldApi.itemCount(str(eval(a.get(0)))) > 0;
            case "helditem":
                return WorldApi.heldItem(ref(a.get(0)));
            case "haseffect":
                return WorldApi.effect(ref(a.get(0)), str(eval(a.get(1)))) != null;
            case "effectlevel": {
                net.minecraft.world.effect.MobEffectInstance m = WorldApi.effect(ref(a.get(0)), str(eval(a.get(1))));
                return m == null ? 0.0 : (double) (m.getAmplifier() + 1);
            }
            case "effecttime": {
                net.minecraft.world.effect.MobEffectInstance m = WorldApi.effect(ref(a.get(0)), str(eval(a.get(1))));
                return m == null ? 0.0 : m.isInfiniteDuration() ? 999999.0 : m.getDuration() / 20.0;
            }
            case "xplevel": {
                ServerPlayer p = SprauteRuntime.player();
                return p != null ? (double) p.experienceLevel : 0.0;
            }
            case "food": {
                ServerPlayer p = SprauteRuntime.player();
                return p != null ? (double) p.getFoodData().getFoodLevel() : 0.0;
            }
            case "gamemode": {
                ServerPlayer p = SprauteRuntime.player();
                return p != null ? p.gameMode.getGameModeForPlayer().getName() : "";
            }
            case "dimension": {
                Object w = refOrNull(a.get(0));
                return w instanceof EntityRef r && r.alive() ? r.require().level().dimension().location().toString() : "";
            }
            case "weather":
                return WorldApi.weather();
            case "biome":
                return WorldApi.biome(ref(a.get(0)));
            case "light":
                return WorldApi.light(ref(a.get(0)));
            case "sky":
                return WorldApi.sky(ref(a.get(0)));
            case "posof":
                return WorldApi.list(WorldApi.vec(ref(a.get(0))));
            case "charname": {
                Object w = refOrNull(a.get(0));
                return w instanceof EntityRef r && r.alive() ? r.require().getName().getString() : "";
            }
            case "targetof": {
                Object w = refOrNull(a.get(0));
                return w instanceof EntityRef r && r.alive() ? WorldApi.get(r, "target") : "";
            }
            case "contains":
                return str(eval(a.get(0))).toLowerCase().contains(str(eval(a.get(1))).toLowerCase());
            case "upper":
                return str(eval(a.get(0))).toUpperCase();
            case "lower":
                return str(eval(a.get(0))).toLowerCase();
            case "num": {
                try {
                    return Double.parseDouble(str(eval(a.get(0))).trim().replace(',', '.'));
                } catch (NumberFormatException ex) {
                    return 0.0;
                }
            }
            case "running":
                return truthy(script.vars.get(Emitter.runningFlag(str(eval(a.get(0))))));
            case "active":
                return script.env.scriptInstances.containsKey((int) num(eval(a.get(0))));
            case "playername": {
                ServerPlayer p = SprauteRuntime.player();
                return p != null ? p.getGameProfile().getName() : "";
            }
            case "int":
                return Math.floor(num(eval(a.get(0))));
            case "str":
                return str(eval(a.get(0)));
            case "min":
                if (a.size() == 1) return Values.method(eval(a.get(0)), "min", List.of());
                return Math.min(num(eval(a.get(0))), num(eval(a.get(1))));
            case "max":
                if (a.size() == 1) return Values.method(eval(a.get(0)), "max", List.of());
                return Math.max(num(eval(a.get(0))), num(eval(a.get(1))));
            case "abs":
                return Math.abs(num(eval(a.get(0))));
            default:
                throw new SprauteRuntimeException("Неизвестная функция " + c.name);
        }
    }

    /** Position of a target expression: location name, character, coordinates or player. */
    /** The character a command-function works with: its name. */
    private Character<?> characterArg(Expr e, String fn) {
        Character<?> ch = e instanceof Ident id ? script.characters.get(id.name) : null;
        if (ch == null) throw new SprauteRuntimeException(fn + "(): первым нужен персонаж, объявленный через character");
        return ch;
    }

    /** What the spawn command does once the body is in the world: health and hitbox of the declaration. */
    private void afterSpawn(String name, Character<?> ch) {
        for (org.sprautestudio.st_addon_spraute.lang.Ast.CharacterDecl d : script.deployed.program.characters) {
            if (!d.name.equals(name)) continue;
            if (d.health != null) {
                float hp = (float) Math.max(1, num(eval(d.health)));
                WhenReady.run(() -> script.optional(ch).orElse(null), en -> {
                    if (en instanceof org.sprautestudio.st_addon_spraute.core.entity.AnimatedEntity ae) ae.setMaxHealth(hp);
                    else if (en instanceof net.minecraft.world.entity.LivingEntity le) {
                        var at = le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                        if (at != null) at.setBaseValue(hp);
                        le.setHealth(hp);
                    }
                });
            }
            if (d.hitbox != null) {
                String hb = str(eval(d.hitbox));
                org.sprautestudio.st_addon_spraute.hitbox.Hitboxes.applyWhenReady(() -> script.optional(ch).orElse(null), List.of(hb));
            }
        }
    }

    public Location position(Expr e) {
        if (e instanceof Ident id) {
            if (id.name.equals("player")) {
                ServerPlayer p = SprauteRuntime.player();
                return p != null ? new Location(p) : null;
            }
            if (script.locationDecls.containsKey(id.name)) return script.location(id.name);
            Character<?> ch = script.characters.get(id.name);
            if (ch != null) {
                Entity entity = script.optional(ch).orElse(null);
                return entity != null ? new Location(entity) : null;
            }
            if (script.vars.containsKey(id.name)) {
                Location l = toLocation(script.vars.get(id.name));
                if (l != null) return l;
            }
            throw new SprauteRuntimeException("'" + id.name + "' — не точка и не персонаж");
        }
        if (e instanceof Member || e instanceof Call || e instanceof Index) {
            Location l = toLocation(ref(e));
            if (l != null) return l;
        }
        if (e instanceof Tuple t && t.items.size() == 3) {
            return new Location(num(eval(t.items.get(0))), num(eval(t.items.get(1))), num(eval(t.items.get(2))));
        }
        if (e instanceof StringLit s) {
            Entity mob = nearestMob(s.value);
            if (mob == null) throw new SprauteRuntimeException("Рядом с игроком нет моба " + s.value);
            return new Location(mob);
        }
        throw new SprauteRuntimeException("Ожидались координаты");
    }

    /** A point from a value: an entity's feet or (x, y, z); null otherwise. */
    public static Location toLocation(Object v) {
        if (v instanceof EntityRef r) {
            Entity en = r.get();
            return en != null ? new Location(en) : null;
        }
        if (v instanceof List<?> l && l.size() == 3) {
            try {
                return new Location(num(l.get(0)), num(l.get(1)), num(l.get(2)));
            } catch (SprauteRuntimeException ex) {
                return null;
            }
        }
        return null;
    }

    /** Where to look at a target: the eyes of the player, a character or a mob; points as they are. */
    public Location eyes(Expr e) {
        Entity en = null;
        if (e instanceof Ident id) {
            if (id.name.equals("player")) en = SprauteRuntime.player();
            else if (script.characters.containsKey(id.name)) en = script.optional(script.characters.get(id.name)).orElse(null);
            else if (script.vars.get(id.name) instanceof EntityRef r) en = r.get();
        } else if (e instanceof StringLit s) {
            en = nearestMob(s.value);
        }
        if (en != null) return new Location(en.level(), en.getEyePosition());
        return position(e);
    }

    /** Nearest living non-player entity of a type ("mob"/"any" = any) within 64 blocks of the player. */
    public Entity nearestMob(String type) {
        ServerPlayer p = SprauteRuntime.player();
        if (p == null) return null;
        String id = type.contains(":") ? type : "minecraft:" + type;
        boolean any = type.equals("mob") || type.equals("any");
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity en : p.serverLevel().getEntities((Entity) null, p.getBoundingBox().inflate(64),
                x -> x instanceof net.minecraft.world.entity.LivingEntity && !(x instanceof net.minecraft.world.entity.player.Player))) {
            if (!any && !String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(en.getType())).equals(id)) continue;
            double d = en.distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = en;
            }
        }
        return best;
    }

    public String interpolate(String text) {
        if (text.indexOf('{') < 0) return text.replace("\\{", "{");
        Matcher m = INTERPOLATION.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String inner = m.group(1).trim();
            String value;
            Expr expr = inner.isEmpty() ? null : Parser.parseExpression(inner);
            if (expr == null) {
                value = m.group(0);
            } else {
                try {
                    value = str(eval(expr));
                } catch (SprauteRuntimeException ex) {
                    value = "{" + inner + "?}";
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString().replace("\\{", "{");
    }

    // ------------------------------------------------------------------ values

    public static boolean truthy(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof java.util.Collection<?> c) return !c.isEmpty();
        if (v instanceof EntityRef r) return r.alive();
        if (v instanceof java.util.Map<?, ?> m) return !m.isEmpty();
        if (v instanceof Double d) return d != 0;
        if (v instanceof String s) return !s.isEmpty();
        return v != null;
    }

    public static double num(Object v) {
        if (v instanceof Double d) return d;
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new SprauteRuntimeException("'" + s + "' — не число");
            }
        }
        throw new SprauteRuntimeException("Ожидалось число");
    }

    public static String str(Object v) {
        if (v instanceof Double d) {
            if (d == Math.rint(d) && !Double.isInfinite(d)) return String.valueOf(d.longValue());
            return String.format(Locale.ROOT, "%.2f", d);
        }
        if (v instanceof List<?> || v instanceof java.util.Map<?, ?>) return Values.format(v);
        return String.valueOf(v);
    }

    /** Equality as the == operator sees it. */
    public static boolean same(Object l, Object r) {
        return equal(l, r);
    }

    private static boolean equal(Object l, Object r) {
        if (l instanceof Double || r instanceof Double) {
            try {
                return num(l) == num(r);
            } catch (SprauteRuntimeException e) {
                return false;
            }
        }
        if (l instanceof EntityRef || r instanceof EntityRef) return java.util.Objects.equals(l, r);
        return str(l).equals(str(r));
    }

    private static int compare(Object l, Object r) {
        if (l instanceof String && r instanceof String) return ((String) l).compareTo((String) r);
        return Double.compare(num(l), num(r));
    }
}
