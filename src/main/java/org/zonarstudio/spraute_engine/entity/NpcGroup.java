package org.zonarstudio.spraute_engine.entity;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Именованная группа НПС. Хранит UUID участников и позволяет применять
 * действия ко всем НПС группы сразу.
 */
public class NpcGroup {

    private final String name;
    /** UUIDs участников. LinkedHashSet — сохраняет порядок добавления. */
    private final Set<UUID> members = new LinkedHashSet<>();

    public NpcGroup(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public void add(UUID uuid) { members.add(uuid); }

    public void remove(UUID uuid) { members.remove(uuid); }

    public void clear() { members.clear(); }

    public Set<UUID> getMembers() { return members; }

    /**
     * Возвращает живые НПС из этой группы в указанном уровне.
     * Автоматически удаляет UUID мёртвых/выгруженных сущностей.
     */
    public List<SprauteNpcEntity> resolveNpcs(ServerLevel level) {
        List<SprauteNpcEntity> result = new ArrayList<>();
        List<UUID> dead = new ArrayList<>();
        for (UUID uuid : members) {
            net.minecraft.world.entity.Entity e = level.getEntity(uuid);
            if (e instanceof SprauteNpcEntity npc && npc.isAlive()) {
                result.add(npc);
            } else if (e == null || !e.isAlive()) {
                dead.add(uuid);
            }
        }
        members.removeAll(dead);
        return result;
    }

    @Override
    public String toString() { return "NpcGroup(" + name + ", " + members.size() + " members)"; }
}
