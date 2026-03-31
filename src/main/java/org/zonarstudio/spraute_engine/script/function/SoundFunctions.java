package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

public class SoundFunctions {

    private static Player resolvePlayer(Object target, CommandSourceStack source) {
        if (target instanceof Player p) return p;
        if (target instanceof String name && source.getLevel() != null) {
            return source.getLevel().getServer().getPlayerList().getPlayerByName(name);
        }
        return null;
    }

    public static class PlaySound implements ScriptFunction {
        @Override
        public String getName() {
            return "playSound";
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
            if (args.size() < 2) return null;
            Player player = resolvePlayer(args.get(0), source);
            if (!(player instanceof ServerPlayer sp)) return null;

            String soundId = String.valueOf(args.get(1));
            ResourceLocation rl = soundId.contains(":") ? new ResourceLocation(soundId) : new ResourceLocation("minecraft", soundId);
            
            float volume = 1.0f;
            float pitch = 1.0f;
            if (args.size() > 2 && args.get(2) instanceof Number n) volume = n.floatValue();
            if (args.size() > 3 && args.get(3) instanceof Number n) pitch = n.floatValue();

            // Using reflection to bypass Registry requirements in 1.19.2 for custom sounds,
            // or we just construct a new SoundEvent (in 1.19.2 SoundEvent(ResourceLocation) is public)
            SoundEvent event = new SoundEvent(rl);
            
            // Sending directly using network packet or player method
            // In 1.19.2, playNotifySound works well
            sp.playNotifySound(event, SoundSource.MASTER, volume, pitch);
            return null;
        }
    }

    public static class StopSound implements ScriptFunction {
        @Override
        public String getName() {
            return "stopSound";
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
            if (args.isEmpty()) return null;
            Player player = resolvePlayer(args.get(0), source);
            if (!(player instanceof ServerPlayer sp)) return null;

            if (args.size() >= 2) {
                String soundId = String.valueOf(args.get(1));
                ResourceLocation rl = soundId.contains(":") ? new ResourceLocation(soundId) : new ResourceLocation("minecraft", soundId);
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(rl, SoundSource.MASTER));
            } else {
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(null, null));
            }
            return null;
        }
    }
}
