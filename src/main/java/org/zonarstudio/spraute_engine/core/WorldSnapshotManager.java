package org.zonarstudio.spraute_engine.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.storage.LevelResource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WorldSnapshotManager {

    private static final Logger LOGGER = LogManager.getLogger();

    private static File getSnapshotFile(ServerLevel level, String name) {
        File dir = new File(level.getServer().getWorldPath(LevelResource.ROOT).toFile(), "spraute_snapshots");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, name + ".snbt");
    }

    public static void saveSnapshot(ServerLevel level, String name, List<ChunkPos> chunksToSave) {
        CompoundTag root = new CompoundTag();
        ListTag chunksTag = new ListTag();

        for (ChunkPos pos : chunksToSave) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk != null) {
                CompoundTag chunkTag = ChunkSerializer.write(level, chunk);
                
                CompoundTag wrapper = new CompoundTag();
                wrapper.putInt("x", pos.x);
                wrapper.putInt("z", pos.z);
                wrapper.put("ChunkData", chunkTag);
                
                ListTag entitiesTag = new ListTag();
                net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                    pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                    pos.getMaxBlockX(), level.getMaxBuildHeight(), pos.getMaxBlockZ()
                );
                
                List<Entity> entities = level.getEntities((Entity) null, box, e -> !(e instanceof net.minecraft.world.entity.player.Player));
                for (Entity e : entities) {
                    CompoundTag eTag = new CompoundTag();
                    if (e.saveAsPassenger(eTag)) {
                        entitiesTag.add(eTag);
                    }
                }
                wrapper.put("Entities", entitiesTag);
                chunksTag.add(wrapper);
            }
        }
        root.put("Chunks", chunksTag);
        
        try {
            NbtIo.write(root, getSnapshotFile(level, name));
        } catch (Exception e) {
            LOGGER.error("Failed to save snapshot " + name, e);
        }
    }

    public static void loadSnapshot(ServerLevel level, String name) {
        File file = getSnapshotFile(level, name);
        if (!file.exists()) {
            LOGGER.warn("Snapshot " + name + " not found");
            return;
        }
        try {
            CompoundTag root = NbtIo.read(file);
            if (root == null) return;
            ListTag chunksTag = root.getList("Chunks", 10);
            for (int i = 0; i < chunksTag.size(); i++) {
                CompoundTag wrapper = chunksTag.getCompound(i);
                int cx = wrapper.getInt("x");
                int cz = wrapper.getInt("z");
                ChunkPos pos = new ChunkPos(cx, cz);
                
                // Force load the chunk if it's not loaded
                LevelChunk existingChunk = level.getChunk(cx, cz);
                if (existingChunk == null) continue;
                
                CompoundTag chunkData = wrapper.getCompound("ChunkData");
                ProtoChunk proto = ChunkSerializer.read(level, level.getPoiManager(), pos, chunkData);

                // 1. Remove existing entities
                net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                    pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                    pos.getMaxBlockX(), level.getMaxBuildHeight(), pos.getMaxBlockZ()
                );
                List<Entity> existingEntities = level.getEntities((Entity) null, box, e -> !(e instanceof net.minecraft.world.entity.player.Player));
                for (Entity e : existingEntities) {
                    e.discard();
                }

                // 2. Restore blocks (Flags: 16 = no neighbor updates, 32 = no drops, 64 = no block entity updates/drops)
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                            BlockPos bpos = new BlockPos(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                            BlockState savedState = proto.getBlockState(bpos);
                            BlockState currentState = existingChunk.getBlockState(bpos);
                            if (savedState != currentState) {
                                level.setBlock(bpos, savedState, 2 | 16 | 32 | 64);
                            }
                        }
                    }
                }

                // 3. Restore block entities
                for (BlockPos bePos : proto.getBlockEntitiesPos()) {
                    CompoundTag beTag = proto.getBlockEntityNbtForSaving(bePos);
                    if (beTag != null) {
                        BlockEntity be = BlockEntity.loadStatic(bePos, proto.getBlockState(bePos), beTag);
                        if (be != null) {
                            level.setBlockEntity(be);
                        }
                    }
                }

                // 4. Restore entities
                ListTag entitiesTag = wrapper.getList("Entities", 10);
                for (int j = 0; j < entitiesTag.size(); j++) {
                    CompoundTag eTag = entitiesTag.getCompound(j);
                    net.minecraft.world.entity.EntityType.create(eTag, level).ifPresent(e -> {
                        // Avoid adding duplicate players or entities that already exist
                        if (!(e instanceof net.minecraft.world.entity.player.Player) && level.getEntity(e.getUUID()) == null) {
                            level.addFreshEntity(e);
                        }
                    });
                }

                // 5. Send update to clients
                level.getChunkSource().chunkMap.getPlayers(pos, false).forEach(p -> {
                    p.connection.send(new ClientboundLevelChunkWithLightPacket(existingChunk, level.getLightEngine(), null, null, true));
                });
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load snapshot " + name, e);
        }
    }
}
