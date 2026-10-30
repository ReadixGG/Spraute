package org.sprautestudio.st_addon_spraute.core.script;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/** A story character: what to spawn and under what name. Its body in the world is found through {@link Characters}. */
public final class Character<T extends Entity> {
    private static final Map<String, Character<?>> BY_ID = new HashMap<>();

    private final String id;
    private final EntityType<T> type;
    private final String name;
    private final Double speed;
    private final ResourceLocation texture;

    private Character(String id, EntityType<T> type, String name, Double speed, ResourceLocation texture) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.speed = speed;
        this.texture = texture;
    }

    public static Character<?> getById(String id) {
        return BY_ID.get(id);
    }

    public static CharacterBuilder<?> builder(String id, ResourceLocation type) {
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(type)) throw new IllegalArgumentException("Сущность " + type + " не найдена");
        return new CharacterBuilder<>(id, BuiltInRegistries.ENTITY_TYPE.get(type));
    }

    public String getId() {
        return id;
    }

    public EntityType<T> getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public Double speed() {
        return speed;
    }

    public ResourceLocation texture() {
        return texture;
    }

    public static final class CharacterBuilder<T extends Entity> {
        private final String id;
        private final EntityType<T> type;
        private String name;
        private Double speed;
        private ResourceLocation texture;

        private CharacterBuilder(String id, EntityType<T> type) {
            this.id = id;
            this.type = type;
            this.name = id;
        }

        public CharacterBuilder<T> name(String n) {
            name = n;
            return this;
        }

        public CharacterBuilder<T> standardMoveSpeed(double s) {
            speed = s;
            return this;
        }

        public CharacterBuilder<T> textured(ResourceLocation t) {
            texture = t;
            return this;
        }

        public Character<T> build() {
            Character<T> c = new Character<>(id, type, name, speed, texture);
            BY_ID.put(id, c);
            return c;
        }
    }
}
