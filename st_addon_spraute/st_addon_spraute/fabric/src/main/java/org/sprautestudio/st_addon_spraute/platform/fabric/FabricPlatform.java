package org.sprautestudio.st_addon_spraute.platform.fabric;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
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
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.platform.PacketChannel;
import org.sprautestudio.st_addon_spraute.platform.Platform;
import org.sprautestudio.st_addon_spraute.runtime.models.BodyPart;
import org.sprautestudio.st_addon_spraute.runtime.models.SprauteModelEntity;

import java.nio.file.Path;
import java.util.function.Supplier;

final class FabricPlatform implements Platform {
    private MinecraftServer server;

    FabricPlatform() {
        ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
        // after the engine's own listeners of this moment, which still ask for the server
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
    }

    /** Fabric's first listener of the server's start runs before the engine's, which ask for it. */
    void setServer(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public boolean isModLoaded(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path modResource(String... path) {
        return FabricLoader.getInstance().getModContainer(St_addon_spraute.MODID).orElseThrow()
                .findPath(String.join("/", path)).orElseGet(() -> gameDir().resolve("spraute").resolve(".missing").resolve(String.join("_", path)));
    }

    @Override
    public CompoundTag persistentData(Entity entity) {
        return ((EntityData) entity).spraute$data();
    }

    @Override
    public MinecraftServer server() {
        return server;
    }

    @Override
    public PacketChannel channel(ResourceLocation id, String protocol) {
        return new FabricChannel(new ResourceLocation(id.getNamespace(), id.getPath() + "_" + protocol));
    }

    @Override
    public <T extends Entity> Supplier<EntityType<T>> entityType(String name, Supplier<EntityType<T>> type) {
        EntityType<T> made = Registry.register(BuiltInRegistries.ENTITY_TYPE, new ResourceLocation(St_addon_spraute.MODID, name), type.get());
        return () -> made;
    }

    @Override
    public <T extends LivingEntity> void entityAttributes(Supplier<EntityType<T>> type, Supplier<AttributeSupplier> attributes) {
        FabricDefaultAttributeRegistry.register(type.get(), attributes.get());
    }

    @Override
    public ServerPlayer fakePlayer(ServerLevel level, GameProfile profile) {
        return FakePlayer.get(level, profile);
    }

    @Override
    public SprauteModelEntity modelEntity(EntityType<SprauteModelEntity> type, Level level) {
        return new SprauteModelEntity(type, level);
    }

    @Override
    public BodyPart bodyPart(SprauteModelEntity body, int index) {
        return new FabricPart(body, index);
    }
}
