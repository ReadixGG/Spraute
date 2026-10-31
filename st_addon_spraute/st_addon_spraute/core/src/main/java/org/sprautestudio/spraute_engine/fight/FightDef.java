package org.sprautestudio.spraute_engine.fight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A fighting style: how much a fighter takes, how it moves, its attacks (each an animation with the
 * moment of the hit, damage, reach, pause after), how it blocks, dodges, flinches and falls.
 * Empty animations are simply not played — the fight works with whatever the model has.
 * One style serves any number of characters: fight alice style "мечник".
 */
public class FightDef {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String title = "Боец";
    /** Hit points of the fight (not the entity's own health). */
    public float health = 20;
    /** Share of every hit taken away: 0 — none, 0.5 — half. */
    public float armor = 0;
    /** Walking speed while chasing: 1 — usual, 1.5 — runs. */
    public float speed = 1.3f;
    /** Gives up when the target is this far. */
    public float giveUp = 40;
    /** Combat stance, looped between attacks while standing ("" — none). */
    public String idle = "";
    /** Keep the stance while walking too (off — the usual walk shows on the move). */
    public boolean stanceMoving = false;
    public List<Attack> attacks = new ArrayList<>();
    public Defense block = new Defense(), dodge = new Defense();
    /** Flinch when hit; staggerChance — how often a hit breaks its attack. */
    public String hurt = "";
    public float staggerChance = 0.4f;
    /** Falls with this when beaten. */
    public String defeat = "";
    /** knockout — lies there (the story decides), die — disappears, stand — just stops fighting. */
    public String defeatMode = "knockout";
    /** Runs away when health is below this share (0 — never). */
    public float fleeAt = 0;
    /** A bar at the top of the screen with the fighter's health. */
    public boolean bossBar = false;
    public String bossName = "";
    public String hitSound = "minecraft:entity.player.attack.strong";

    public static class Attack {
        public String name = "Удар";
        public String anim = "";
        public float damage = 3;
        /** Reach in blocks. */
        public float range = 2.2f;
        /** Ticks from the start of the animation to the hit (0 — 45% of the animation). */
        public int windup = 0;
        /** Ticks the attack lasts (0 — the length of the animation). */
        public int duration = 0;
        /** Ticks of rest before the next attack. */
        public int cooldown = 12;
        public float knockback = 0.4f;
        /** How often it is chosen against the others. */
        public int weight = 1;
        /** A step forward at the hit (blocks). */
        public float lunge = 0;
        public String sound = "";

        public Attack() {
        }

        public Attack(String name, String anim, float damage, float range, int windup, int cooldown, int weight) {
            this.name = name;
            this.anim = anim;
            this.damage = damage;
            this.range = range;
            this.windup = windup;
            this.cooldown = cooldown;
            this.weight = weight;
        }
    }

    public static class Defense {
        public String anim = "";
        /** Chance when hit (0..1). */
        public float chance = 0;
        /** Block: share of the hit stopped; dodge: blocks of the jump aside. */
        public float power = 0.8f;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static FightDef fromJson(String json) {
        FightDef d = null;
        try {
            d = GSON.fromJson(json, FightDef.class);
        } catch (Exception ignored) {
        }
        if (d == null) d = new FightDef();
        d.fix();
        return d;
    }

    public FightDef copy() {
        return fromJson(toJson());
    }

    public void fix() {
        if (title == null) title = "";
        if (idle == null) idle = "";
        if (hurt == null) hurt = "";
        if (defeat == null) defeat = "";
        if (defeatMode == null) defeatMode = "knockout";
        if (bossName == null) bossName = "";
        if (hitSound == null) hitSound = "";
        if (attacks == null) attacks = new ArrayList<>();
        if (block == null) block = new Defense();
        if (dodge == null) dodge = new Defense();
        if (block.anim == null) block.anim = "";
        if (dodge.anim == null) dodge.anim = "";
        health = Math.max(1, health);
        armor = Math.max(0, Math.min(0.95f, armor));
        for (Attack a : attacks) {
            if (a.name == null) a.name = "Удар";
            if (a.anim == null) a.anim = "";
            if (a.sound == null) a.sound = "";
            a.weight = Math.max(1, a.weight);
            a.range = Math.max(0.5f, a.range);
        }
        if (attacks.isEmpty()) attacks.add(new Attack("Удар", "", 2, 2.2f, 0, 12, 1));
    }

    // ================================================================== ready styles on Spraute's animations

    public static final Map<String, String> PRESET_TITLES = new LinkedHashMap<>();
    static {
        PRESET_TITLES.put("fists", "Кулачный бой");
        PRESET_TITLES.put("sword", "Мечник");
        PRESET_TITLES.put("brute", "Громила");
        PRESET_TITLES.put("weak", "Слабак");
    }

    public static FightDef preset(String name) {
        FightDef d = new FightDef();
        switch (name == null ? "" : name) {
            case "fists" -> {
                d.title = "Кулачный бой";
                d.idle = "entity/humanoid!fight_idle";
                d.attacks.add(new Attack("Левой", "entity/humanoid!punch_left", 2, 2.2f, 0, 10, 3));
                d.attacks.add(new Attack("Правой", "entity/humanoid!punch_right", 2.5f, 2.2f, 0, 10, 3));
                Attack two = new Attack("Двойной", "entity/humanoid!punch_combo", 4, 2.3f, 0, 18, 1);
                two.knockback = 0.8f;
                d.attacks.add(two);
                d.block.anim = "entity/humanoid!block_hit";
                d.block.chance = 0.25f;
                d.block.power = 0.8f;
                d.dodge.anim = "entity/humanoid!dodge_left";
                d.dodge.chance = 0.1f;
                d.dodge.power = 2;
                d.hurt = "entity/humanoid!hit_front";
                d.defeat = "entity/humanoid!knockdown";
            }
            case "sword" -> {
                d.title = "Мечник";
                d.health = 30;
                d.armor = 0.15f;
                d.idle = "entity/humanoid!fight_idle_sword";
                d.attacks.add(new Attack("Взмах", "entity/humanoid!slash_right", 5, 2.8f, 0, 14, 3));
                Attack heavy = new Attack("Сильный удар", "entity/humanoid!slash_right", 8, 3, 0, 24, 1);
                heavy.knockback = 1;
                heavy.lunge = 1;
                d.attacks.add(heavy);
                d.block.anim = "entity/humanoid!block_hit";
                d.block.chance = 0.35f;
                d.block.power = 0.9f;
                d.dodge.anim = "entity/humanoid!dodge_left";
                d.dodge.chance = 0.1f;
                d.dodge.power = 2.5f;
                d.hurt = "entity/humanoid!hit_front";
                d.staggerChance = 0.25f;
                d.defeat = "entity/humanoid!knockdown";
                d.hitSound = "minecraft:entity.player.attack.sweep";
            }
            case "brute" -> {
                d.title = "Громила";
                d.health = 45;
                d.armor = 0.25f;
                d.speed = 1.05f;
                d.idle = "entity/humanoid!fight_idle";
                Attack swing = new Attack("Размах", "entity/humanoid!slash_right", 6, 2.6f, 0, 20, 2);
                swing.knockback = 1.2f;
                d.attacks.add(swing);
                Attack spin = new Attack("С разворота", "entity/humanoid!slash_right", 9, 2.8f, 0, 30, 1);
                spin.knockback = 1.6f;
                d.attacks.add(spin);
                d.hurt = "entity/humanoid!hit_front";
                d.staggerChance = 0.1f;
                d.defeat = "entity/humanoid!knockdown";
            }
            case "weak" -> {
                d.title = "Слабак";
                d.health = 8;
                d.speed = 1.15f;
                d.idle = "entity/humanoid!fight_idle";
                d.attacks.add(new Attack("Тычок", "entity/humanoid!punch_right", 1, 2, 0, 18, 1));
                d.dodge.anim = "entity/humanoid!dodge_left";
                d.dodge.chance = 0.15f;
                d.dodge.power = 2;
                d.hurt = "entity/humanoid!hit_front";
                d.staggerChance = 0.8f;
                d.fleeAt = 0.25f;
                d.defeat = "entity/humanoid!knockdown";
            }
            default -> {
                return null;
            }
        }
        d.fix();
        return d;
    }

    /** A new style file starts as the fist fighter. */
    public static FightDef example(String title) {
        FightDef d = preset("fists");
        d.title = title;
        return d;
    }
}
