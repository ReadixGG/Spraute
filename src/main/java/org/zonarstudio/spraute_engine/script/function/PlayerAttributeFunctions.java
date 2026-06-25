package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.zonarstudio.spraute_engine.compat.SprautePlayerCompat;
import org.zonarstudio.spraute_engine.script.PlayerDigSpeedOverrides;
import org.zonarstudio.spraute_engine.script.PlayerStepHeightOverrides;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Скорость ходьбы, прыжок, высота шага, урон и множитель скорости копания.
 */
public class PlayerAttributeFunctions {

    private static ServerPlayer resolvePlayer(Object arg, CommandSourceStack source) {
        if (arg instanceof ServerPlayer sp) return sp;
        if (arg instanceof net.minecraft.world.entity.player.Player p && source.getServer() != null) {
            ServerPlayer byUuid = source.getServer().getPlayerList().getPlayer(p.getUUID());
            if (byUuid != null) return byUuid;
        }
        if (source.getServer() == null) return null;
        return source.getServer().getPlayerList().getPlayerByName(String.valueOf(arg));
    }

    private static Double requireNumber(Object arg) {
        if (arg instanceof Number n) return n.doubleValue();
        return null;
    }

    private abstract static class AttrGet implements ScriptFunction {
        private final String name;
        private final Function<ServerPlayer, Double> reader;

        AttrGet(String name, Function<ServerPlayer, Double> reader) {
            this.name = name;
            this.reader = reader;
        }

        @Override public String getName() { return name; }
        @Override public int getArgCount() { return 1; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.isEmpty()) return null;
            ServerPlayer player = resolvePlayer(args.get(0), source);
            if (player == null) return null;
            return reader.apply(player);
        }
    }

    private abstract static class AttrSet implements ScriptFunction {
        private final String name;
        private final BiConsumer<ServerPlayer, Double> writer;

        AttrSet(String name, BiConsumer<ServerPlayer, Double> writer) {
            this.name = name;
            this.writer = writer;
        }

        @Override public String getName() { return name; }
        @Override public int getArgCount() { return 2; }
        @Override public Class<?>[] getArgTypes() { return new Class<?>[]{Object.class, Number.class}; }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 2) return null;
            ServerPlayer player = resolvePlayer(args.get(0), source);
            Double value = requireNumber(args.get(1));
            if (player == null || value == null) return null;
            writer.accept(player, value);
            return null;
        }
    }

    public static class GetMovementSpeed extends AttrGet {
        public GetMovementSpeed() { super("getPlayerMovementSpeed", p -> SprautePlayerCompat.getAttributeValue(p, SprautePlayerCompat.movementSpeedAttr())); }
    }

    public static class SetMovementSpeed extends AttrSet {
        public SetMovementSpeed() { super("setPlayerMovementSpeed", (p, v) -> SprautePlayerCompat.setAttributeBase(p, SprautePlayerCompat.movementSpeedAttr(), v)); }
    }

    public static class GetJumpStrength extends AttrGet {
        public GetJumpStrength() { super("getPlayerJumpStrength", p -> SprautePlayerCompat.getAttributeValue(p, SprautePlayerCompat.jumpStrengthAttr())); }
    }

    public static class SetJumpStrength extends AttrSet {
        public SetJumpStrength() { super("setPlayerJumpStrength", (p, v) -> SprautePlayerCompat.setAttributeBase(p, SprautePlayerCompat.jumpStrengthAttr(), v)); }
    }

    public static class GetStepHeight extends AttrGet {
        public GetStepHeight() { super("getPlayerStepHeight", p -> (double) PlayerStepHeightOverrides.get(p)); }
    }

    public static class SetStepHeight extends AttrSet {
        public SetStepHeight() { super("setPlayerStepHeight", (p, v) -> PlayerStepHeightOverrides.set(p, v.floatValue())); }
    }

    public static class GetAttackDamage extends AttrGet {
        public GetAttackDamage() { super("getPlayerAttackDamage", p -> SprautePlayerCompat.getAttributeValue(p, SprautePlayerCompat.attackDamageAttr())); }
    }

    public static class SetAttackDamage extends AttrSet {
        public SetAttackDamage() { super("setPlayerAttackDamage", (p, v) -> SprautePlayerCompat.setAttributeBase(p, SprautePlayerCompat.attackDamageAttr(), v)); }
    }

    /** Множитель скорости копания блоков (1.0 = как в ваниле). */
    public static class GetDigSpeed extends AttrGet {
        public GetDigSpeed() { super("getPlayerDigSpeed", PlayerDigSpeedOverrides::get); }
    }

    public static class SetDigSpeed extends AttrSet {
        public SetDigSpeed() { super("setPlayerDigSpeed", (p, v) -> PlayerDigSpeedOverrides.set(p, v)); }
    }
}
