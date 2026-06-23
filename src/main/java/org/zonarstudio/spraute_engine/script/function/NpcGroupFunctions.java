package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import org.zonarstudio.spraute_engine.entity.NpcGroup;
import org.zonarstudio.spraute_engine.entity.NpcGroupManager;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;
import java.util.UUID;

public class NpcGroupFunctions {

    /** createGroup("guards") → NpcGroup */
    public static class CreateGroup implements ScriptFunction {
        @Override public String getName() { return "createGroup"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            return NpcGroupManager.getOrCreate(String.valueOf(args.get(0)));
        }
    }

    /** getGroup("guards") → NpcGroup (или создаёт новую) */
    public static class GetGroup implements ScriptFunction {
        @Override public String getName() { return "getGroup"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            return NpcGroupManager.getOrCreate(String.valueOf(args.get(0)));
        }
    }

    /** groupAdd(group, npc) — добавить НПС в группу */
    public static class GroupAdd implements ScriptFunction {
        @Override public String getName() { return "groupAdd"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            NpcGroup group = resolveGroup(args.get(0));
            SprauteNpcEntity npc = resolveNpc(args.get(1), source);
            if (group != null && npc != null) {
                group.add(npc.getUUID());
                npc.setGroupName(group.getName());
            }
            return null;
        }
    }

    /** groupRemove(group, npc) — убрать НПС из группы */
    public static class GroupRemove implements ScriptFunction {
        @Override public String getName() { return "groupRemove"; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            NpcGroup group = resolveGroup(args.get(0));
            SprauteNpcEntity npc = resolveNpc(args.get(1), source);
            if (group != null && npc != null) {
                group.remove(npc.getUUID());
                npc.setGroupName(null);
            }
            return null;
        }
    }

    /** groupClear(group) — очистить группу */
    public static class GroupClear implements ScriptFunction {
        @Override public String getName() { return "groupClear"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            NpcGroup group = resolveGroup(args.get(0));
            if (group == null || source.getLevel() == null) return null;
            if (source.getLevel() != null && !source.getLevel().isClientSide) {
                ServerLevel sl = (ServerLevel) source.getLevel();
                for (UUID uuid : group.getMembers()) {
                    net.minecraft.world.entity.Entity e = sl.getEntity(uuid);
                    if (e instanceof SprauteNpcEntity npc) npc.setGroupName(null);
                }
            }
            group.clear();
            return null;
        }
    }

    /** groupSize(group) → int */
    public static class GroupSize implements ScriptFunction {
        @Override public String getName() { return "groupSize"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }
        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            NpcGroup group = resolveGroup(args.get(0));
            return group == null ? 0 : group.getMembers().size();
        }
    }

    // ========== Helpers ==========

    public static NpcGroup resolveGroup(Object arg) {
        if (arg instanceof NpcGroup g) return g;
        if (arg instanceof String s) return NpcGroupManager.getOrCreate(s);
        return null;
    }

    public static SprauteNpcEntity resolveNpc(Object arg, CommandSourceStack source) {
        if (arg instanceof SprauteNpcEntity npc) return npc;
        if (arg instanceof String name && source.getLevel() != null) {
            UUID uuid = org.zonarstudio.spraute_engine.entity.NpcManager.get(name);
            if (uuid != null) {
                net.minecraft.world.entity.Entity e = source.getLevel().getEntity(uuid);
                if (e instanceof SprauteNpcEntity npc) return npc;
            }
        }
        return null;
    }
}
