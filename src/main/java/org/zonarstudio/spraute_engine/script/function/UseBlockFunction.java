package org.zonarstudio.spraute_engine.script.function;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.compat.SprauteEntityCompat;
import org.zonarstudio.spraute_engine.script.BlockUseUtil;
import org.zonarstudio.spraute_engine.script.EntityScriptUtil;
import org.zonarstudio.spraute_engine.script.ItemStackScriptUtil;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/**
 * useBlock(x, y, z) — ПКМ по блоку без актора (дверь/рычаг откроется напрямую).
 * useBlock(npc, x, y, z) — от лица НИПа или игрока.
 * useBlock(x, y, z, "off") — левая рука (только без актора).
 */
public class UseBlockFunction implements ScriptFunction {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String getName() {
        return "useBlock";
    }

    @Override
    public int getArgCount() {
        return -1;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[0];
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < 3 || source.getLevel() == null) return false;

        int idx = 0;
        LivingEntity actor = null;
        Level level = source.getLevel();

        if (isCoordTriple(args, 0)) {
            // useBlock(x, y, z [, hand])
        } else if (args.size() >= 4 && isCoordTriple(args, 1)) {
            actor = resolveActor(args.get(0), source);
            if (actor == null) {
                LOGGER.warn("[Script] useBlock: unknown actor '{}'", args.get(0));
                return false;
            }
            level = SprauteEntityCompat.level(actor);
            idx = 1;
        } else {
            LOGGER.warn("[Script] useBlock: expected useBlock(x,y,z) or useBlock(actor,x,y,z)");
            return false;
        }

        int x = ((Number) args.get(idx)).intValue();
        int y = ((Number) args.get(idx + 1)).intValue();
        int z = ((Number) args.get(idx + 2)).intValue();

        InteractionHand hand = InteractionHand.MAIN_HAND;
        if (args.size() > idx + 3) {
            hand = parseHand(String.valueOf(args.get(idx + 3)));
        }

        if (level == null || !(level instanceof ServerLevel)) {
            LOGGER.warn("[Script] useBlock: invalid level for interaction");
            return false;
        }

        return BlockUseUtil.useBlock(level, x, y, z, actor, hand);
    }

    private static LivingEntity resolveActor(Object arg, CommandSourceStack source) {
        if (arg instanceof ServerPlayer sp) return sp;
        if (arg instanceof LivingEntity living) return living;
        if (arg instanceof String s) {
            Player player = ItemStackScriptUtil.resolvePlayer(s, source);
            if (player instanceof ServerPlayer sp) return sp;
            Entity entity = EntityScriptUtil.resolveEntity(s, source);
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }

    private static boolean isCoordTriple(List<Object> args, int start) {
        return args.size() >= start + 3
                && args.get(start) instanceof Number
                && args.get(start + 1) instanceof Number
                && args.get(start + 2) instanceof Number;
    }

    private static InteractionHand parseHand(String raw) {
        String s = raw != null ? raw.trim().toLowerCase() : "";
        return switch (s) {
            case "off", "offhand", "left" -> InteractionHand.OFF_HAND;
            default -> InteractionHand.MAIN_HAND;
        };
    }
}
