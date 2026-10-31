package org.sprautestudio.spraute_engine.fx;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Built-in effects, played by name ({@code fx play "nuke" at player}) and offered as templates for
 * new project effects. Sizes are for scale 1.
 */
public final class FxPresets {
    private FxPresets() {
    }

    public record Preset(String name, String title, String doc, Supplier<FxDef> make) {
    }

    public static final Map<String, Preset> ALL = new LinkedHashMap<>();

    public static FxDef get(String name) {
        Preset p = ALL.get(name);
        return p != null ? p.make().get() : null;
    }

    public static List<String> names() {
        return List.copyOf(ALL.keySet());
    }

    private static void preset(String name, String title, String doc, Supplier<FxDef> make) {
        ALL.put(name, new Preset(name, title, doc, make));
    }

    // ================================================================== a small builder

    private static final class L {
        final FxDef.Layer l;

        L(String name) {
            l = FxDef.layer(name);
        }

        L shape(String s, float radius) {
            l.shape = s;
            l.radius = radius;
            return this;
        }

        L inner(float r) {
            l.radiusInner = r;
            return this;
        }

        L height(float h) {
            l.height = h;
            return this;
        }

        L angle(float a) {
            l.angle = a;
            return this;
        }

        L at(float x, float y, float z) {
            l.offsetX = x;
            l.offsetY = y;
            l.offsetZ = z;
            return this;
        }

        L even() {
            l.even = true;
            return this;
        }

        L burst(int n) {
            l.burst = n;
            return this;
        }

        L every(float s) {
            l.burstEvery = s;
            return this;
        }

        L rate(float r) {
            l.rate = r;
            l.burst = 0;
            return this;
        }

        L max(int m) {
            l.max = m;
            return this;
        }

        L delay(float d) {
            l.delay = d;
            return this;
        }

        L time(float t) {
            l.time = t;
            return this;
        }

        L dir(String d, float speed, float random) {
            l.direction = d;
            l.speed = speed;
            l.speedRandom = random;
            return this;
        }

        L gravity(float g) {
            l.gravity = g;
            return this;
        }

        L drag(float d) {
            l.drag = d;
            return this;
        }

        L orbit(float o) {
            l.orbit = o;
            return this;
        }

        L turb(float t) {
            l.turbulence = t;
            return this;
        }

        L attract(float a) {
            l.attract = a;
            return this;
        }

        L world() {
            l.local = false;
            return this;
        }

        L tex(String t) {
            l.texture = t;
            return this;
        }

        L smoke() {
            l.texture = "smoke";
            l.blend = "alpha";
            return this;
        }

        L rot(float x, float y, float z) {
            l.rotX = x;
            l.rotY = y;
            l.rotZ = z;
            return this;
        }

        L blend(String b) {
            l.blend = b;
            return this;
        }

        L render(String r) {
            l.render = r;
            return this;
        }

        L life(float life, float random) {
            l.life = life;
            l.lifeRandom = random;
            return this;
        }

        L size(float... k) {
            l.size = k;
            return this;
        }

        L sizeRandom(float r) {
            l.sizeRandom = r;
            return this;
        }

        L alpha(float... k) {
            l.alpha = k;
            return this;
        }

        L color(String... c) {
            l.color = c;
            return this;
        }

        L colorRandom() {
            l.colorRandom = true;
            return this;
        }

        L glow(float i) {
            l.intensity = i;
            return this;
        }

        L spin(float s, float random) {
            l.spin = s;
            l.spinRandom = random;
            return this;
        }

        L stretch(float s) {
            l.render = "stretch";
            l.stretch = s;
            return this;
        }

        L beam(float length) {
            l.render = "beam";
            l.beamLength = length;
            return this;
        }
    }

    private static FxDef.Layer flash(String color, float strength, float time, float range) {
        FxDef.Layer l = FxDef.layer("Вспышка экрана");
        l.type = "flash";
        l.flashColor = color;
        l.strength = strength;
        l.time = time;
        l.range = range;
        return l;
    }

    private static FxDef.Layer shake(float strength, float time, float range, float delay) {
        FxDef.Layer l = FxDef.layer("Тряска камеры");
        l.type = "shake";
        l.strength = strength;
        l.time = time;
        l.range = range;
        l.delay = delay;
        return l;
    }

    private static FxDef fx(float duration, String sound, float volume, float pitch, Object... layers) {
        FxDef d = new FxDef();
        d.duration = duration;
        d.sound = sound;
        d.soundVolume = volume;
        d.soundPitch = pitch;
        for (Object o : layers) d.layers.add(o instanceof L b ? b.l : (FxDef.Layer) o);
        return d;
    }

    // ================================================================== the effects

    static {
        preset("nuke", "Ядерный взрыв", "Вспышка, огненный шар, гриб до неба, ударная волна по земле, искры и пыль. Масштаб 1 — гриб высотой около 35 блоков.",
                () -> fx(14, "minecraft:entity.generic.explode", 6, 0.35f,
                        flash("#FFF6E0", 1, 1.6f, 260),
                        shake(1, 5, 220, 0.15f),
                        new L("Ядро вспышки").burst(1).max(1).dir("none", 0, 0).tex("flare").life(1.6f, 0).size(4, 70, 50).sizeRandom(0)
                                .alpha(1, 0.7f, 0).color("#FFFFFF", "#FFF0B0").glow(2),
                        new L("Огненный шар").shape("sphere", 3).burst(160).dir("out", 7, 0.5f).drag(1.6f).gravity(-1.2f).turb(1.5f)
                                .tex("smoke").life(3.5f, 0.3f).size(4, 9, 11).alpha(1, 0.9f, 0)
                                .color("#FFFFFF", "#FFE08A", "#FF8A2A", "#B8380F", "#401008").glow(1.6f),
                        new L("Ножка гриба").shape("disc", 2.5f).rate(70).time(4.5f).delay(0.3f).dir("up", 9, 0.3f).drag(0.25f).turb(0.8f)
                                .smoke().life(4.5f, 0.3f).size(3, 5, 6).alpha(0, 0.95f, 0.8f, 0).max(500)
                                .color("#FFB060", "#A05A30", "#5A4036", "#3A3634"),
                        new L("Шляпка гриба").shape("shell", 7).inner(3).at(0, 30, 0).rate(130).time(4).delay(1.2f).dir("out", 2.5f, 0.6f)
                                .orbit(16).gravity(-0.4f).drag(0.4f).turb(1.2f).smoke().life(7, 0.3f).size(6, 11, 13).alpha(0, 0.95f, 0.9f, 0)
                                .max(700).color("#FFC27A", "#C26A38", "#6E4A3C", "#3C3836", "#2A2828"),
                        new L("Огонь в шляпке").shape("shell", 5).at(0, 30, 0).rate(70).time(3.5f).delay(1.2f).dir("out", 1.5f, 0.6f).orbit(16)
                                .turb(1).tex("smoke").life(2.5f, 0.3f).size(4, 7).alpha(0, 0.8f, 0).color("#FFE6A0", "#FF8A2A", "#802010").glow(1.4f),
                        new L("Ударная волна").burst(1).max(1).at(0, 0.4f, 0).dir("none", 0, 0).tex("ring").render("flat").life(2.6f, 0)
                                .size(3, 140).sizeRandom(0).alpha(1, 0.8f, 0).color("#FFFFFF", "#FFE2B8").glow(1.5f),
                        new L("Кольцо конденсации").burst(1).max(1).at(0, 18, 0).delay(1.4f).dir("none", 0, 0).tex("ring").render("flat")
                                .life(3, 0).size(6, 46).sizeRandom(0).alpha(0, 0.7f, 0).color("#FFFFFF").glow(1).spin(0, 0),
                        new L("Пыль по земле").shape("ring", 3).burst(320).dir("out", 26, 0.4f).drag(1.1f).gravity(0).smoke()
                                .life(4.5f, 0.3f).size(3, 7, 9).alpha(0, 0.9f, 0).color("#B09070", "#6E5E50", "#4A4440").at(0, 0.6f, 0),
                        new L("Искры").shape("sphere", 2).burst(450).dir("out", 32, 0.6f).gravity(10).drag(0.7f).tex("spark").stretch(0.12f)
                                .life(2.6f, 0.4f).size(0.5f, 0.15f).alpha(1, 1, 0).color("#FFFFFF", "#FFC040", "#FF5010").glow(2),
                        new L("Обломки").shape("sphere", 1.5f).burst(120).dir("cone", 22, 0.5f).angle(70).gravity(14).drag(0.3f).tex("square")
                                .blend("alpha").life(3, 0.3f).size(0.35f, 0.3f).spin(0, 400).alpha(1, 1, 0).color("#3A2A20", "#1E1A18").l));

        preset("orbit_orbs", "Орбы вокруг игрока", "Светящиеся сферы кружат вокруг цели, за ними светящееся кольцо и искорки. "
                        + "Играйте с follow, останавливайте fx stop.",
                () -> fx(0, "minecraft:block.amethyst_block.chime", 1, 1.4f,
                        new L("Орбы").shape("ring", 1.4f).even().at(0, 1.1f, 0).burst(5).max(5).dir("none", 0, 0).orbit(110).tex("flare")
                                .life(1e6f, 0).size(0.55f).sizeRandom(0).alpha(1).color("#8FE3FF").glow(1.8f),
                        new L("Кольцо света").shape("ring", 1.4f).at(0, 1.1f, 0).rate(70).dir("none", 0, 0).orbit(110).tex("glow")
                                .life(0.7f, 0.2f).size(0.22f, 0).alpha(0, 0.8f, 0).color("#8FE3FF", "#A070FF").glow(1.3f),
                        new L("Искорки").shape("disc", 1.3f).rate(14).dir("up", 0.7f, 0.4f).drag(0.2f).tex("star").life(1.6f, 0.3f)
                                .size(0.18f, 0).spin(0, 180).alpha(0, 1, 0).color("#FFFFFF", "#8FE3FF")));

        preset("sky_beam", "Луч в небо", "Столб света уходит в небо, вокруг него спираль, у основания волны и искры.",
                () -> fx(6, "minecraft:block.beacon.activate", 2, 1.4f,
                        flash("#BFF1FF", 0.35f, 0.5f, 60),
                        shake(0.35f, 1.2f, 50, 0),
                        new L("Луч").burst(1).max(1).dir("up", 0, 0).tex("beam").beam(220).life(6, 0).size(0, 3.2f, 2.6f, 2.6f, 0).sizeRandom(0)
                                .alpha(1).color("#7FDFFF", "#BFF1FF").glow(1.6f),
                        new L("Ядро луча").burst(1).max(1).dir("up", 0, 0).tex("beam").beam(220).life(6, 0).size(0, 1.2f, 0.9f, 0.9f, 0)
                                .sizeRandom(0).alpha(1).color("#FFFFFF").glow(2.5f),
                        new L("Спираль").shape("ring", 1.3f).rate(90).time(5).dir("up", 9, 0.1f).drag(0).orbit(220).tex("glow")
                                .life(2.6f, 0.2f).size(0.35f, 0.1f).alpha(0, 1, 0).color("#BFF1FF", "#3FA0FF").glow(1.5f),
                        new L("Волны у земли").burst(1).every(0.7f).time(5).max(10).at(0, 0.15f, 0).dir("none", 0, 0).tex("ring").render("flat")
                                .life(1.3f, 0).size(1, 9).sizeRandom(0).alpha(0.9f, 0).color("#8FE3FF").glow(1.3f),
                        new L("Искры у основания").shape("disc", 1.4f).rate(70).time(5).dir("out", 4, 0.5f).gravity(-3).drag(0.6f).tex("spark")
                                .stretch(0.2f).life(0.9f, 0.3f).size(0.25f, 0.05f).alpha(1, 0).color("#FFFFFF", "#7FDFFF").glow(1.8f)));

        preset("fire", "Огонь", "Костёр: языки пламени, искры-угольки, дым и тёплое свечение. Горит, пока не остановят (fx stop).",
                () -> fx(0, "minecraft:block.campfire.crackle", 1, 1,
                        new L("Свечение").burst(1).max(1).at(0, 0.5f, 0).dir("none", 0, 0).tex("flare").life(1e6f, 0).size(2.6f).sizeRandom(0)
                                .alpha(0.45f).color("#FF8A30").glow(1),
                        new L("Пламя").shape("disc", 0.35f).rate(70).dir("up", 1.4f, 0.4f).drag(0.6f).gravity(-1.5f).turb(1.6f).tex("smoke")
                                .life(0.75f, 0.3f).size(0.55f, 0.75f, 0.15f).spin(0, 120).alpha(0, 1, 0.8f, 0)
                                .color("#FFFFFF", "#FFE07A", "#FF9A2A", "#E04010", "#401008").glow(1.6f),
                        new L("Ядро").shape("disc", 0.2f).rate(40).dir("up", 0.8f, 0.3f).turb(0.8f).tex("glow").life(0.45f, 0.3f)
                                .size(0.45f, 0.1f).alpha(0, 1, 0).color("#FFFFFF", "#FFD060").glow(2),
                        new L("Угольки").shape("disc", 0.4f).rate(6).dir("up", 2.2f, 0.6f).gravity(-0.5f).drag(0.4f).turb(2.5f).tex("spark")
                                .stretch(0.25f).life(1.6f, 0.5f).size(0.09f, 0.03f).alpha(1, 1, 0).color("#FFE6A0", "#FF7A20").glow(2),
                        new L("Дым").shape("disc", 0.3f).at(0, 1.1f, 0).rate(8).dir("up", 0.9f, 0.4f).drag(0.2f).turb(0.8f).smoke()
                                .life(3, 0.4f).size(0.6f, 2).spin(0, 30).alpha(0, 0.35f, 0).color("#5A5450", "#3A3836")));

        preset("standing_portal", "Портал (стоячий)", "Вертикальное кольцо-портал: пример поворота слоя (Поворот X 90).",
                () -> fx(0, "minecraft:block.portal.ambient", 0.8f, 1,
                        new L("Рамка").shape("ring", 1.5f).even().rot(90, 0, 0).at(0, 1.6f, 0).burst(24).max(24).dir("none", 0, 0).orbit(70)
                                .tex("glow").life(1e6f, 0).size(0.35f).sizeRandom(0).alpha(1).color("#C08AFF").glow(1.6f),
                        new L("Вихрь").shape("ring", 1.4f).inner(0.2f).rot(90, 0, 0).at(0, 1.6f, 0).rate(90).dir("tangent", 1.5f, 0.3f)
                                .attract(3).orbit(200).tex("glow").life(1, 0.3f).size(0.22f, 0.05f).alpha(0, 1, 0).color("#F0C0FF", "#6A2AFF").glow(1.4f),
                        new L("Плёнка").burst(1).max(1).rot(90, 0, 0).at(0, 1.6f, 0).dir("none", 0, 0).tex("glow").render("flat").life(1e6f, 0)
                                .size(3.2f).sizeRandom(0).spin(40, 0).alpha(0.55f).color("#7A3AFF").glow(1)));

        preset("explosion", "Взрыв", "Обычный взрыв: огонь, дым, искры и волна.",
                () -> fx(4, "minecraft:entity.generic.explode", 2, 0.9f,
                        flash("#FFE8C0", 0.35f, 0.35f, 24),
                        shake(0.55f, 0.9f, 30, 0),
                        new L("Огонь").shape("sphere", 0.6f).burst(50).dir("out", 4, 0.5f).drag(2.5f).gravity(-1.5f).tex("smoke")
                                .life(0.9f, 0.3f).size(1.2f, 2.6f).alpha(1, 0).color("#FFFFFF", "#FFC060", "#FF5A1A", "#601808").glow(1.5f),
                        new L("Дым").shape("sphere", 0.8f).burst(40).dir("out", 2, 0.5f).drag(1.5f).gravity(-1).turb(1).smoke().delay(0.1f)
                                .life(2.6f, 0.4f).size(1.5f, 3.4f).alpha(0, 0.8f, 0).color("#5A5450", "#2E2C2A"),
                        new L("Искры").burst(70).dir("random", 9, 0.6f).gravity(9).drag(0.6f).tex("spark").stretch(0.12f).life(1, 0.4f)
                                .size(0.22f, 0.05f).alpha(1, 0).color("#FFFFFF", "#FFB040").glow(1.8f),
                        new L("Волна").burst(1).max(1).at(0, 0.1f, 0).dir("none", 0, 0).tex("ring").render("flat").life(0.6f, 0)
                                .size(1, 9).sizeRandom(0).alpha(0.9f, 0).color("#FFE6C0")));

        preset("magic_circle", "Магический круг", "Вращающийся круг на земле, руны-звёзды и поднимающийся свет.",
                () -> fx(5, "minecraft:block.enchantment_table.use", 1.5f, 0.8f,
                        new L("Круг").burst(1).max(1).at(0, 0.05f, 0).dir("none", 0, 0).tex("ring").render("flat").life(5, 0).size(0, 5, 5, 5, 0)
                                .sizeRandom(0).spin(40, 0).alpha(0, 1, 1, 0).color("#C08AFF").glow(1.5f),
                        new L("Внутренний круг").burst(1).max(1).at(0, 0.06f, 0).dir("none", 0, 0).tex("ring").render("flat").life(5, 0)
                                .size(0, 3, 3, 3, 0).sizeRandom(0).spin(-70, 0).alpha(0, 1, 1, 0).color("#FF9AF0").glow(1.5f),
                        new L("Руны").shape("ring", 2.2f).even().at(0, 0.3f, 0).burst(8).max(8).dir("none", 0, 0).orbit(40).tex("star").life(5, 0)
                                .size(0, 0.6f, 0.6f, 0).sizeRandom(0).spin(90, 0).alpha(0, 1, 1, 0).color("#FFFFFF", "#C08AFF").glow(1.6f),
                        new L("Свет вверх").shape("disc", 2.3f).rate(60).time(4.5f).dir("up", 1.6f, 0.4f).drag(0.1f).tex("glow")
                                .life(1.8f, 0.3f).size(0.25f, 0).alpha(0, 1, 0).color("#FFB8F8", "#8A5AFF").glow(1.4f)));

        preset("portal", "Портал", "Фиолетовый вихрь, затягивающий частицы к центру.",
                () -> fx(0, "minecraft:block.portal.ambient", 1, 1,
                        new L("Вихрь").shape("ring", 2.6f).inner(1.5f).at(0, 1.4f, 0).rate(140).dir("tangent", 2, 0.3f).attract(5).orbit(160)
                                .drag(0.4f).tex("glow").life(1.2f, 0.3f).size(0.3f, 0.05f).alpha(0, 1, 0).color("#E0A0FF", "#7A2AFF", "#2A0A60").glow(1.4f),
                        new L("Центр").burst(1).max(1).at(0, 1.4f, 0).dir("none", 0, 0).tex("flare").life(1e6f, 0).size(2.4f).sizeRandom(0)
                                .alpha(0.9f).color("#B060FF").glow(1.5f),
                        new L("Кольцо").burst(1).max(1).at(0, 1.4f, 0).dir("none", 0, 0).tex("ring").life(1e6f, 0).size(5.6f).sizeRandom(0)
                                .spin(60, 0).alpha(0.8f).color("#C080FF")));

        preset("fire_tornado", "Огненный смерч", "Крутящийся столб огня и дыма.",
                () -> fx(6, "minecraft:item.firecharge.use", 1.5f, 0.6f,
                        new L("Огонь").shape("ring", 0.6f).rate(180).dir("up", 5, 0.3f).orbit(360).drag(0).tex("smoke").life(1.6f, 0.3f)
                                .size(0.8f, 1.6f, 0.4f).alpha(0, 1, 0).color("#FFFFFF", "#FFC040", "#FF4A10", "#401008").glow(1.5f),
                        new L("Дым").shape("ring", 1.2f).at(0, 6, 0).rate(40).dir("up", 2, 0.5f).orbit(200).turb(1).smoke().life(2.5f, 0.3f)
                                .size(1.5f, 3.5f).alpha(0, 0.7f, 0).color("#3A3634", "#1E1C1C"),
                        new L("Искры").shape("disc", 1.5f).rate(60).dir("up", 7, 0.5f).orbit(300).gravity(2).tex("spark").stretch(0.15f)
                                .life(1.2f, 0.4f).size(0.2f, 0.05f).alpha(1, 0).color("#FFFFFF", "#FF9A30").glow(1.8f)));

        preset("lightning", "Удар молнии", "Разряд с неба, вспышка, гром и искры.",
                () -> fx(2.5f, "minecraft:entity.lightning_bolt.thunder", 3, 1,
                        flash("#E6F0FF", 0.75f, 0.45f, 80),
                        shake(0.6f, 0.8f, 60, 0),
                        new L("Разряд").burst(1).max(1).at(0, 0, 0).dir("up", 0, 0).tex("beam").beam(90).life(0.45f, 0).size(2.2f, 1.6f, 0)
                                .sizeRandom(0).alpha(1, 0).color("#FFFFFF", "#9FC8FF").glow(3),
                        new L("Свечение").burst(1).max(1).at(0, 0.5f, 0).dir("none", 0, 0).tex("flare").life(0.7f, 0).size(10, 4).sizeRandom(0)
                                .alpha(1, 0).color("#CFE2FF").glow(2),
                        new L("Искры").burst(90).dir("cone", 10, 0.6f).angle(80).gravity(12).drag(0.5f).tex("spark").stretch(0.1f).life(1, 0.4f)
                                .size(0.2f, 0.05f).alpha(1, 0).color("#FFFFFF", "#8FB8FF").glow(2)));

        preset("fireworks", "Салют", "Разноцветный залп с хвостами и мерцанием.",
                () -> fx(3.5f, "minecraft:entity.firework_rocket.large_blast", 2, 1,
                        new L("Залп").at(0, 12, 0).burst(260).dir("random", 11, 0.15f).gravity(4).drag(1.4f).tex("spark").stretch(0.18f)
                                .life(2.4f, 0.4f).size(0.35f, 0.1f).alpha(1, 1, 0).color("#FF4A6A", "#FFD040", "#40E0FF", "#9A6AFF", "#60FF80")
                                .colorRandom().glow(2),
                        new L("Мерцание").at(0, 12, 0).shape("sphere", 9).delay(1).burst(120).dir("down", 0.6f, 0.5f).tex("star").life(1.2f, 0.5f)
                                .size(0.25f, 0).spin(0, 300).alpha(0, 1, 0, 1, 0).color("#FFFFFF", "#FFE8A0").glow(1.5f),
                        new L("Вспышка").at(0, 12, 0).burst(1).max(1).dir("none", 0, 0).tex("flare").life(0.5f, 0).size(9, 3).sizeRandom(0)
                                .alpha(1, 0).color("#FFFFFF")));

        preset("heal", "Лечение", "Зелёное свечение и плюсы-искорки поднимаются вокруг цели.",
                () -> fx(2.5f, "minecraft:entity.player.levelup", 0.8f, 1.6f,
                        new L("Свет").shape("ring", 0.9f).rate(80).time(1.6f).dir("up", 1.4f, 0.4f).drag(0.3f).orbit(90).tex("glow")
                                .life(1.3f, 0.3f).size(0.28f, 0).alpha(0, 1, 0).color("#C8FFC0", "#40E060").glow(1.4f),
                        new L("Кресты").shape("disc", 0.9f).rate(16).time(1.6f).at(0, 0.5f, 0).dir("up", 0.9f, 0.3f).tex("star").life(1.4f, 0.3f)
                                .size(0.35f, 0).alpha(0, 1, 0).color("#FFFFFF", "#7AFF8A").glow(1.5f),
                        new L("Кольцо").burst(1).max(1).at(0, 0.1f, 0).dir("none", 0, 0).tex("ring").render("flat").life(1, 0).size(0.5f, 3.5f)
                                .sizeRandom(0).alpha(1, 0).color("#7AFF8A").glow(1.2f)));

        preset("aura", "Аура", "Пламя силы вокруг персонажа (с follow, до fx stop).",
                () -> fx(0, "", 1, 1,
                        new L("Пламя").shape("ring", 0.55f).rate(90).dir("up", 1.8f, 0.4f).drag(0.4f).turb(1.2f).tex("smoke").life(1, 0.3f)
                                .size(0.6f, 0.9f, 0.1f).alpha(0, 0.8f, 0).color("#FFFFFF", "#7FD0FF", "#3A60FF").glow(1.4f),
                        new L("Искры").shape("ring", 0.7f).rate(20).dir("up", 3, 0.5f).tex("spark").stretch(0.2f).life(0.8f, 0.4f)
                                .size(0.15f, 0.03f).alpha(1, 0).color("#FFFFFF", "#7FD0FF").glow(1.8f)));

        preset("smoke", "Клуб дыма", "Густой дым, медленно рассеивается.",
                () -> fx(4, "minecraft:block.fire.extinguish", 1, 0.6f,
                        new L("Дым").shape("sphere", 1).burst(60).dir("out", 1.2f, 0.5f).drag(0.8f).gravity(-0.5f).turb(0.8f).smoke()
                                .life(3.5f, 0.4f).size(1.4f, 3.6f).alpha(0, 0.85f, 0).color("#8A8682", "#4A4846")));

        preset("fireball", "Огненный шар", "Шар огня для снарядов: ядро, пламя и искры остаются следом в воздухе. cast \"fireball\".",
                () -> fx(0, "minecraft:item.firecharge.use", 1, 1,
                        new L("Ядро").burst(1).max(1).dir("none", 0, 0).tex("flare").life(1e6f, 0).size(0.9f).sizeRandom(0).alpha(1).color("#FFD27A").glow(2),
                        new L("Пламя").shape("sphere", 0.25f).rate(90).dir("random", 0.6f, 0.5f).world().turb(1).tex("smoke").life(0.45f, 0.3f)
                                .size(0.6f, 0.1f).alpha(1, 0).color("#FFFFFF", "#FFB040", "#E04010").glow(1.6f),
                        new L("Искры").shape("sphere", 0.2f).rate(25).dir("random", 2, 0.5f).world().gravity(3).tex("spark").stretch(0.12f)
                                .life(0.6f, 0.4f).size(0.1f, 0.02f).alpha(1, 0).color("#FFE6A0", "#FF7A20").glow(2)));

        preset("magic_orb", "Магическая сфера", "Сияющая сфера со светящимся следом и кружащими искрами — для снарядов и парящих шаров.",
                () -> fx(0, "minecraft:block.amethyst_block.chime", 1, 1.6f,
                        new L("Сфера").burst(1).max(1).dir("none", 0, 0).tex("flare").life(1e6f, 0).size(0.75f).sizeRandom(0).alpha(1).color("#9FE6FF").glow(2.2f),
                        new L("Ядро").burst(1).max(1).dir("none", 0, 0).tex("glow").life(1e6f, 0).size(0.35f).sizeRandom(0).alpha(1).color("#FFFFFF").glow(2.5f),
                        new L("След").shape("sphere", 0.15f).rate(70).dir("none", 0, 0).world().tex("glow").life(0.5f, 0.2f).size(0.4f, 0)
                                .alpha(0.9f, 0).color("#BFF1FF", "#7A5AFF").glow(1.6f),
                        new L("Искры").shape("ring", 0.45f).rate(30).dir("none", 0, 0).orbit(360).tex("star").life(0.5f, 0.3f).size(0.14f, 0)
                                .spin(0, 300).alpha(0, 1, 0).color("#FFFFFF", "#8FE3FF").glow(1.8f)));

        preset("magic_hit", "Вспышка попадания", "Короткая вспышка: искры во все стороны и кольцо — на место, куда попал снаряд.",
                () -> fx(1.2f, "minecraft:block.amethyst_cluster.break", 1, 1.3f,
                        new L("Вспышка").burst(1).max(1).dir("none", 0, 0).tex("flare").life(0.25f, 0).size(2.2f, 0).sizeRandom(0).alpha(1, 0)
                                .color("#FFFFFF").glow(2.2f),
                        new L("Искры").burst(40).dir("random", 6, 0.6f).drag(2).gravity(4).tex("spark").stretch(0.12f).life(0.6f, 0.4f)
                                .size(0.16f, 0.03f).alpha(1, 0).color("#FFFFFF", "#8FE3FF", "#B070FF").glow(1.8f),
                        new L("Кольцо").burst(1).max(1).dir("none", 0, 0).tex("ring").life(0.4f, 0).size(0.3f, 2.4f).sizeRandom(0).alpha(1, 0)
                                .color("#BFF1FF").glow(1.6f)));

        preset("sparkles", "Блёстки", "Мерцающие звёздочки в воздухе.",
                () -> fx(3, "minecraft:block.amethyst_block.chime", 1, 1.2f,
                        new L("Блёстки").shape("sphere", 1.8f).at(0, 1, 0).rate(40).time(2.4f).dir("random", 0.3f, 0.5f).tex("star")
                                .life(1, 0.4f).size(0.3f, 0).spin(0, 200).alpha(0, 1, 0).color("#FFFFFF", "#FFE88A", "#FFB8F0").colorRandom().glow(1.6f)));
    }
}
