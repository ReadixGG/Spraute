package org.sprautestudio.spraute_engine.platform.forge;

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
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.platform.PacketChannel;
import org.sprautestudio.spraute_engine.platform.Platform;
import org.sprautestudio.spraute_engine.runtime.models.BodyPart;
import org.sprautestudio.spraute_engine.runtime.models.SprauteModelEntity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class ForgePlatform implements Platform {
    private final DeferredRegister<EntityType<?>> entities = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SprauteEngine.MODID);
    private final List<Consumer<EntityAttributeCreationEvent>> attributes = new ArrayList<>();

    /** What was registered before the mod's bus could be given. */
    void registerOn(IEventBus modBus) {
        entities.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> attributes.forEach(a -> a.accept(e)));
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }

    @Override
    public boolean isModLoaded(String id) {
        return ModList.get().isLoaded(id);
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path modResource(String... path) {
        return ModList.get().getModFileById(SprauteEngine.MODID).getFile().findResource(path);
    }

    @Override
    public CompoundTag persistentData(Entity entity) {
        return entity.getPersistentData();
    }

    @Override
    public MinecraftServer server() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    @Override
    public PacketChannel channel(ResourceLocation id, String protocol) {
        return new ForgeChannel(id, protocol);
    }

    @Override
    public <T extends Entity> Supplier<EntityType<T>> entityType(String name, Supplier<EntityType<T>> type) {
        return entities.register(name, type);
    }

    @Override
    public <T extends LivingEntity> void entityAttributes(Supplier<EntityType<T>> type, Supplier<AttributeSupplier> made) {
        attributes.add(e -> e.put(type.get(), made.get()));
    }

    @Override
    public ServerPlayer fakePlayer(ServerLevel level, GameProfile profile) {
        return FakePlayerFactory.get(level, profile);
    }

    @Override
    public SprauteModelEntity modelEntity(EntityType<SprauteModelEntity> type, Level level) {
        return new ForgeModelEntity(type, level);
    }

    @Override
    public BodyPart bodyPart(SprauteModelEntity body, int index) {
        return new ForgePart(body, index);
    }

    @Override
    public void listenLoaderEvent(String name) {
        ForgeBridge.listen(name);
    }
}
