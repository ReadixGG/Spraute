package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.Entity;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
import org.zonarstudio.spraute_engine.script.EntityScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public final class NpcItemFunctions {

    private NpcItemFunctions() {}

    private static SprauteNpcEntity resolveNpc(Object ref, CommandSourceStack source) {
        Entity e = EntityScriptUtil.resolveEntity(ref, source);
        return e instanceof SprauteNpcEntity npc && npc.isAlive() ? npc : null;
    }

    /** npcThrowItem(npc, itemId, [count]) — throw item in NPC look direction */
    public static class NpcThrowItem implements ScriptFunction {
        @Override public String getName() { return "npcThrowItem"; }
        @Override public int getArgCount() { return -1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[0]; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return false;
            SprauteNpcEntity npc = resolveNpc(args.get(0), source);
            if (npc == null) return false;
            String itemId = String.valueOf(args.get(1));
            int count = args.size() >= 3 && args.get(2) instanceof Number n ? n.intValue() : 1;
            return npc.throwItem(itemId, count);
        }
    }
}
