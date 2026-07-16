package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptManager;

import java.util.List;

/**
 * fireAction(player, action_type, [target]) — уведомляет активные скрипты (on action / await action).
 */
public final class ActionEventFunctions {

    private ActionEventFunctions() {}

    private static ServerPlayer resolvePlayer(Object target, CommandSourceStack source) {
        if (target instanceof ServerPlayer sp) return sp;
        if (target instanceof Player p && p instanceof ServerPlayer sp) return sp;
        if (target instanceof String name && source.getLevel() != null) {
            return source.getLevel().getServer().getPlayerList().getPlayerByName(name);
        }
        return null;
    }

    public static class FireAction implements ScriptFunction {
        @Override
        public String getName() { return "fireAction"; }

        @Override
        public int getArgCount() { return -1; }

        @Override
        public Class<?>[] getArgTypes() { return new Class[]{Object.class, String.class, Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            ServerPlayer player = resolvePlayer(args.get(0), source);
            if (player == null) return null;
            String actionType = String.valueOf(args.get(1));
            Object target = args.size() > 2 ? args.get(2) : null;
            ScriptManager.getInstance().onPlayerAction(player, actionType, target);
            return null;
        }
    }

    /** fireRelGift(player, npc_id, item_id, rep_delta) — on relGift / await relGift */
    public static class FireRelGift implements ScriptFunction {
        @Override
        public String getName() { return "fireRelGift"; }

        @Override
        public int getArgCount() { return -1; }

        @Override
        public Class<?>[] getArgTypes() { return new Class[]{Object.class, String.class, String.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 4) return null;
            ServerPlayer player = resolvePlayer(args.get(0), source);
            if (player == null) return null;
            String npcId = String.valueOf(args.get(1));
            String itemId = String.valueOf(args.get(2));
            int rep = args.get(3) instanceof Number n ? n.intValue() : 0;
            ScriptManager.getInstance().onRelGift(player, npcId, itemId, rep);
            return null;
        }
    }

    public static class FireRelRepChange implements ScriptFunction {
        @Override
        public String getName() { return "fireRelRepChange"; }

        @Override
        public int getArgCount() { return -1; }

        @Override
        public Class<?>[] getArgTypes() { return new Class[]{Object.class, String.class, Number.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 4) return null;
            ServerPlayer player = resolvePlayer(args.get(0), source);
            if (player == null) return null;
            String npcId = String.valueOf(args.get(1));
            int oldRep = args.get(2) instanceof Number n ? n.intValue() : 0;
            int newRep = args.get(3) instanceof Number n ? n.intValue() : 0;
            ScriptManager.getInstance().onRelRepChange(player, npcId, oldRep, newRep);
            return null;
        }
    }

    public static class FireRelTalk implements ScriptFunction {
        @Override
        public String getName() { return "fireRelTalk"; }

        @Override
        public int getArgCount() { return -1; }

        @Override
        public Class<?>[] getArgTypes() { return new Class[]{Object.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            ServerPlayer player = resolvePlayer(args.get(0), source);
            if (player == null) return null;
            String npcId = String.valueOf(args.get(1));
            ScriptManager.getInstance().onRelTalk(player, npcId);
            return null;
        }
    }

    public static class FireRelButton implements ScriptFunction {
        @Override
        public String getName() { return "fireRelButton"; }

        @Override
        public int getArgCount() { return -1; }

        @Override
        public Class<?>[] getArgTypes() { return new Class[]{Object.class, String.class, String.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 3) return null;
            ServerPlayer player = resolvePlayer(args.get(0), source);
            if (player == null) return null;
            String npcId = String.valueOf(args.get(1));
            String btnId = String.valueOf(args.get(2));
            ScriptManager.getInstance().onRelButton(player, npcId, btnId);
            return null;
        }
    }
}
