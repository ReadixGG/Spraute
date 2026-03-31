package org.zonarstudio.spraute_engine.script;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Tracks which players have joined this world before (for on_first_join trigger). */
public class FirstJoinData extends SavedData {

    private static final String DATA_NAME = "spraute_engine_first_join";
    private final Set<UUID> joinedPlayers = new HashSet<>();

    public FirstJoinData() {
        super();
    }

    public static FirstJoinData load(CompoundTag tag) {
        FirstJoinData data = new FirstJoinData();
        ListTag list = tag.getList("uuids", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                data.joinedPlayers.add(UUID.fromString(list.getString(i)));
            } catch (Exception ignored) {}
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (UUID uuid : joinedPlayers) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("uuids", list);
        return tag;
    }

    public static FirstJoinData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                FirstJoinData::load,
                FirstJoinData::new,
                DATA_NAME
        );
    }

    public boolean isFirstJoin(UUID playerUuid) {
        return !joinedPlayers.contains(playerUuid);
    }

    public void markJoined(UUID playerUuid) {
        joinedPlayers.add(playerUuid);
        setDirty();
    }
}
