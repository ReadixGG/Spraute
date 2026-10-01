package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.script.ItemStackScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.script.ScriptPropertyAccess;

import java.util.LinkedHashMap;
import java.util.List;

/** Чтение характеристик игрока (HP, голод, слот хотбара, …). Свойства: {@code player.hp}. */
public final class PlayerStatsFunctions {
    private PlayerStatsFunctions() {}

    private static Player resolvePlayer(Object arg, CommandSourceStack source) {
        return ItemStackScriptUtil.resolvePlayer(arg, source);
    }

    /** getPlayerStats(player) → словарь всех характеристик */
    public static class GetPlayerStats implements ScriptFunction {
        @Override public String getName() { return "getPlayerStats"; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return new LinkedHashMap<>();
            Player player = resolvePlayer(args.get(0), source);
            if (player == null) return new LinkedHashMap<>();
            return ScriptPropertyAccess.playerStats(player);
        }
    }
}
