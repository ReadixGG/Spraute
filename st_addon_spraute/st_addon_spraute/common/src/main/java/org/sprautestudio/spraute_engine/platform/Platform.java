package org.sprautestudio.spraute_engine.platform;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import org.sprautestudio.spraute_engine.runtime.models.BodyPart;
import org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * All the engine asks of the mod loader. One implementation per loader, installed first thing at mod start;
 * the events of the game are in {@link Hooks} and {@link ClientHooks}.
 */
public interface Platform {
    final class Holder {
        private static Platform instance;

        private Holder() {
        }
    }

    static Platform get() {
        return Holder.instance;
    }

    static void install(Platform platform) {
        Holder.instance = platform;
    }

    /** The physical side: true also in the host's own game, whose integrated server runs here. */
    boolean isClient();

    boolean isModLoaded(String id);

    /** The folder of the game. */
    Path gameDir();

    /** A file inside the mod itself; it may not exist. */
    Path modResource(String... path);

    /** Data of the engine's own kept on an entity and saved with it. */
    CompoundTag persistentData(Entity entity);

    /** The running server; null when there is none. */
    MinecraftServer server();

    PacketChannel channel(ResourceLocation id, String protocol);

    /** Registers a type of entity; the type exists once the game has made its registries. */
    <T extends Entity> Supplier<EntityType<T>> entityType(String name, Supplier<EntityType<T>> type);

    <T extends LivingEntity> void entityAttributes(Supplier<EntityType<T>> type, Supplier<AttributeSupplier> attributes);

    /** A player that exists on no client, to act as a character does: the game has no other way to use an item or a block. */
    ServerPlayer fakePlayer(ServerLevel level, GameProfile profile);

    /** A character of a project; its class differs per loader only in how the game finds its extra hitboxes. */
    SprauteModelEntity modelEntity(EntityType<SprauteModelEntity> type, Level level);

    BodyPart bodyPart(SprauteModelEntity body, int index);

    /** {@code on forge "..."} of a story: an event of the loader or of another mod, by name. */
    default void listenLoaderEvent(String name) {
        throw new UnsupportedOperationException("This loader has no events by name");
    }
}
