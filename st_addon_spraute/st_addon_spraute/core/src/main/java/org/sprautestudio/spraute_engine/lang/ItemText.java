package org.sprautestudio.spraute_engine.lang;

import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An item as written in a story, read as text only: the id with its namespace, and the NBT after it.
 * The game's own reading of it is in {@code runtime.ItemSpec}.
 */
public final class ItemText {
    private ItemText() {
    }

    /** Where NBT starts: "{key:" or "{}". A plain "{name}" is a story's variable in the id. */
    private static final Pattern NBT_START = Pattern.compile("\\{\\s*(?:[\\w.+-]+\\s*:|\"|})");

    /** NBT text -> why it cannot be read, or null. Only the game knows its own NBT: without it any NBT passes. */
    private static UnaryOperator<String> nbtCheck = nbt -> null;

    public static void setNbtCheck(UnaryOperator<String> check) {
        nbtCheck = check;
    }

    /** The id alone, with its namespace; "" when nothing is written. */
    public static String id(String spec) {
        String key = spec == null ? "" : spec.trim();
        Matcher m = NBT_START.matcher(key);
        String id = m.find() ? key.substring(0, m.start()).trim() : key;
        return !id.isEmpty() && !id.contains(":") ? "minecraft:" + id : id;
    }

    /** The NBT as written; "" when there is none. */
    public static String nbt(String spec) {
        String key = spec == null ? "" : spec.trim();
        Matcher m = NBT_START.matcher(key);
        return m.find() ? key.substring(m.start()).trim() : "";
    }

    /** Why the NBT cannot be read; null when it can (or there is none). */
    public static String error(String spec) {
        String text = nbt(spec);
        return text.isEmpty() ? null : nbtCheck.apply(text);
    }
}
