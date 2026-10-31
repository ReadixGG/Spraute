package org.sprautestudio.spraute_engine.hitbox;

import java.util.ArrayList;
import java.util.List;

/** The words of hitbox settings and their flags — no game classes, so the editor's checks can use it. */
public final class HitboxFlags {
    private HitboxFlags() {
    }

    public static final int SOLID = 1, FIXED = 2, GHOST = 4, NOPICK = 8, DAMAGE = 16, NODAMAGE = 32, MORTAL = 64;

    /** Words to flags, starting from the current ones: "solid", "ghost", "damage"… Unknown word — exception. */
    public static int parse(int flags, List<String> words) {
        for (String raw : words) {
            for (String w : raw.toLowerCase(java.util.Locale.ROOT).trim().split("[\\s,]+")) {
                if (w.isEmpty()) continue;
                switch (w) {
                    case "normal", "обычный", "reset" -> flags &= ~(SOLID | FIXED | GHOST | NOPICK);
                    case "solid", "твёрдый", "твердый", "стена" -> flags = (flags & ~(FIXED | GHOST)) | SOLID;
                    case "fixed", "неподвижный", "nopush" -> flags = (flags & ~(SOLID | GHOST)) | FIXED;
                    case "push", "pushable", "толкается" -> flags &= ~(SOLID | FIXED | GHOST);
                    case "ghost", "призрак", "сквозной", "passthrough" -> flags = (flags & ~(SOLID | FIXED)) | GHOST;
                    case "pick", "clickable" -> flags &= ~NOPICK;
                    case "nopick", "unclickable", "невыделяемый" -> flags |= NOPICK;
                    case "damage", "урон", "vulnerable" -> flags = (flags & ~NODAMAGE) | DAMAGE;
                    case "nodamage", "безурона", "invulnerable", "неуязвимый" -> flags = (flags & ~(DAMAGE | MORTAL)) | NODAMAGE;
                    case "mortal", "смертный", "candie" -> flags = (flags & ~NODAMAGE) | DAMAGE | MORTAL;
                    case "immortal", "бессмертный" -> flags &= ~MORTAL;
                    default -> throw new IllegalArgumentException("Хитбокс: неизвестное слово «" + w
                            + "» (есть: normal, solid, fixed, push, ghost, nopick, pick, damage, nodamage, mortal, immortal)");
                }
            }
        }
        return flags;
    }

    public static String describe(int f) {
        List<String> out = new ArrayList<>();
        out.add((f & SOLID) != 0 ? "solid" : (f & FIXED) != 0 ? "fixed" : (f & GHOST) != 0 ? "ghost" : "normal");
        if ((f & NOPICK) != 0) out.add("nopick");
        if ((f & NODAMAGE) != 0) out.add("nodamage");
        else if ((f & MORTAL) != 0) out.add("mortal");
        else if ((f & DAMAGE) != 0) out.add("damage");
        return String.join(" ", out);
    }

}
