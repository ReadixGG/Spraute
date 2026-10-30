package org.sprautestudio.st_addon_spraute.runtime;

import com.mojang.brigadier.StringReader;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ways out of the story into the rest of the game and other mods, without code: NBT of entities and
 * block entities, attributes, the scoreboard, any command with its result, which mods are there.
 */
public final class Bridges {
    private Bridges() {
    }

    // ================================================================== NBT

    private static NbtPathArgument.NbtPath path(String p) {
        try {
            return new NbtPathArgument().parse(new StringReader(p));
        } catch (Exception e) {
            throw new SprauteRuntimeException("Неверный путь NBT «" + p + "»: " + e.getMessage());
        }
    }

    /** An entity's or a block entity's (target is a point) data. */
    private static CompoundTag data(Object target) {
        if (target instanceof EntityRef r) {
            Entity e = r.require();
            return e.saveWithoutId(new CompoundTag());
        }
        BlockEntity be = blockEntity(target);
        return be.saveWithoutMetadata();
    }

    private static BlockEntity blockEntity(Object target) {
        var v = WorldApi.vec(target);
        BlockPos pos = BlockPos.containing(v.x, v.y, v.z);
        BlockEntity be = WorldApi.level().getBlockEntity(pos);
        if (be == null) throw new SprauteRuntimeException("В " + pos.toShortString() + " нет блока с данными (сундука, печи…)");
        return be;
    }

    /** nbt(target, "Health") / nbt((x,y,z), "Items[0].id"): a number, text, or the SNBT of what is there; "" — nothing. */
    public static Object nbt(Object target, String p) {
        CompoundTag tag = data(target);
        if (p == null || p.isBlank()) return tag.toString();
        List<Tag> found;
        try {
            found = path(p).get(tag);
        } catch (Exception e) {
            return "";
        }
        if (found.isEmpty()) return "";
        return value(found.get(0));
    }

    static Object value(Tag t) {
        if (t instanceof NumericTag n) return n.getAsDouble();
        if (t instanceof StringTag s) return s.getAsString();
        return t.toString();
    }

    /** A story value as NBT: numbers (whole ones as int), text as SNBT when it is ("5b", "{a:1}", "[1,2]"), else as text. */
    static Tag tag(Object v) {
        if (v instanceof Boolean b) return ByteTag.valueOf(b);
        if (v instanceof Number n) {
            double d = n.doubleValue();
            return d == Math.rint(d) && Math.abs(d) < Integer.MAX_VALUE ? IntTag.valueOf((int) d) : DoubleTag.valueOf(d);
        }
        String s = Eval.str(v);
        try {
            CompoundTag wrap = TagParser.parseTag("{v:" + s + "}");
            Tag t = wrap.get("v");
            if (t != null) return t;
        } catch (Exception ignored) {
        }
        return StringTag.valueOf(s);
    }

    /** setnbt target "path" value — like /data modify … set value. */
    public static void setNbt(Object target, String p, Object value) {
        NbtPathArgument.NbtPath path = path(p);
        if (target instanceof EntityRef r) {
            Entity e = r.require();
            if (e instanceof Player) throw new SprauteRuntimeException("NBT игрока менять нельзя — используйте команды и функции (give, effect, health…)");
            CompoundTag tag = e.saveWithoutId(new CompoundTag());
            try {
                path.set(tag, tag(value));
            } catch (Exception ex) {
                throw new SprauteRuntimeException("NBT: " + ex.getMessage());
            }
            UUID id = e.getUUID();
            e.load(tag);
            e.setUUID(id);
            return;
        }
        BlockEntity be = blockEntity(target);
        CompoundTag tag = be.saveWithoutMetadata();
        try {
            path.set(tag, tag(value));
        } catch (Exception ex) {
            throw new SprauteRuntimeException("NBT: " + ex.getMessage());
        }
        be.load(tag);
        be.setChanged();
        var lv = WorldApi.level();
        lv.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
    }

    // ================================================================== attributes

    private static Attribute attribute(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
        Attribute a = rl == null ? null : BuiltInRegistries.ATTRIBUTE.get(rl);
        if (a == null) throw new SprauteRuntimeException("Нет атрибута «" + id + "» (например minecraft:generic.max_health, generic.attack_damage)");
        return a;
    }

    private static LivingEntity living(Object target) {
        if (target instanceof EntityRef r && r.require() instanceof LivingEntity le) return le;
        throw new SprauteRuntimeException("Атрибуты есть только у живых существ и игрока");
    }

    public static double attr(Object target, String id) {
        var inst = living(target).getAttribute(attribute(id));
        return inst == null ? 0 : inst.getValue();
    }

    public static void setAttr(Object target, String id, double value) {
        var inst = living(target).getAttribute(attribute(id));
        if (inst == null) throw new SprauteRuntimeException("У этого существа нет атрибута «" + id + "»");
        inst.setBaseValue(value);
    }

    // ================================================================== scoreboard

    private static String holder(Object target) {
        if (target instanceof EntityRef r) return r.require().getScoreboardName();
        return Eval.str(target);
    }

    private static Objective objective(String name, boolean create) {
        Scoreboard sb = server().getScoreboard();
        Objective o = sb.getObjective(name);
        if (o == null && create) {
            o = sb.addObjective(name, net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY, Component.literal(name),
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER);
        }
        return o;
    }

    /** score(who, "kills"): who — the player, a character, or a name. 0 when there is no such score. */
    public static double score(Object target, String objective) {
        Objective o = objective(objective, false);
        if (o == null) return 0;
        Scoreboard sb = server().getScoreboard();
        String h = holder(target);
        return sb.hasPlayerScore(h, o) ? sb.getOrCreatePlayerScore(h, o).getScore() : 0;
    }

    /** The objective is made (dummy) when there is none. */
    public static void setScore(Object target, String objective, double value, boolean add) {
        Objective o = objective(objective, true);
        var s = server().getScoreboard().getOrCreatePlayerScore(holder(target), o);
        int v = (int) Math.round(value);
        if (add) s.add(v);
        else s.setScore(v);
    }

    // ================================================================== commands and mods

    private static MinecraftServer server() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) throw new SprauteRuntimeException("Мир не запущен");
        return s;
    }

    public record CommandResult(int value, String output) {
    }

    /** Runs a command (as the player when there is one, with full rights) and catches what it says. */
    public static CommandResult run(String command) {
        MinecraftServer server = server();
        String line = command.startsWith("/") ? command.substring(1) : command;
        List<String> out = new ArrayList<>();
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                out.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        ServerPlayer p = SprauteRuntime.player();
        CommandSourceStack src = (p != null ? p.createCommandSourceStack() : server.createCommandSourceStack()).withSource(capture).withPermission(4);
        int[] result = {0};
        src = src.withCallback((ctx, ok, value) -> result[0] = ok ? value : 0);
        int r = server.getCommands().performPrefixedCommand(src, line);
        return new CommandResult(result[0] != 0 ? result[0] : r, String.join("\n", out));
    }

    public static boolean hasMod(String id) {
        return ModList.get().isLoaded(id.trim().toLowerCase());
    }
}
