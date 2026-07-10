package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.Entity;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
import org.zonarstudio.spraute_engine.script.EntityScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public final class NpcCombatFunctions {

    private NpcCombatFunctions() {}

    private static SprauteNpcEntity resolveNpc(Object ref, CommandSourceStack source) {
        Entity e = EntityScriptUtil.resolveEntity(ref, source);
        return e instanceof SprauteNpcEntity npc && npc.isAlive() ? npc : null;
    }

    /** npcAttack(npc, target, [hand], [speed], [range]) */
    public static class NpcAttack implements ScriptFunction {
        @Override public String getName() { return "npcAttack"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            SprauteNpcEntity npc = resolveNpc(args.get(0), source);
            Entity target = EntityScriptUtil.resolveEntity(args.get(1), source);
            if (npc == null || target == null) return false;

            int idx = 2;
            if (args.size() > idx && isHandArg(args.get(idx))) {
                npc.setAttackHand(String.valueOf(args.get(idx)));
                idx++;
            }
            double speed = 1.0;
            double range = npc.getAttackRange();
            if (args.size() > idx && args.get(idx) instanceof Number n) {
                speed = n.doubleValue();
                idx++;
            }
            if (args.size() > idx && args.get(idx) instanceof Number n) {
                range = n.doubleValue();
            }
            npc.attackEntity(target, speed, range);
            return true;
        }

        private static boolean isHandArg(Object arg) {
            if (arg instanceof Number) return false;
            String s = String.valueOf(arg).trim().toLowerCase();
            return "right".equals(s) || "left".equals(s) || "main".equals(s) || "offhand".equals(s)
                    || "правая".equals(s) || "левая".equals(s);
        }
    }
}
