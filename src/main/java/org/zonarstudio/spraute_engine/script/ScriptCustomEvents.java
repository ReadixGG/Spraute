package org.zonarstudio.spraute_engine.script;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Script-declared custom {@code on}/{@code await} event names.
 * Built-in engine events (interact, uiClick, …) cannot be re-registered.
 */
public final class ScriptCustomEvents {
    private static final Set<String> REGISTERED = ConcurrentHashMap.newKeySet();

    /** Engine-owned event names — registerEvent rejects these. */
    private static final Set<String> RESERVED = Set.of(
            "interact", "next", "keybind", "death", "kill", "pickup",
            "orbpickup", "orb_pickup", "projectilehit", "projectile_hit",
            "uiclick", "uiclose", "uiinput", "uitouch", "uioverlap",
            "uidragstart", "uidrop", "uidragend",
            "position", "inventory", "hasitem", "clickblock", "breakblock",
            "placeblock", "openchest", "opendoor", "useblock", "chat", "jump",
            "action", "playeraction", "dimension", "join", "firstjoin", "first_join",
            "leave", "logout", "playerleave", "player_leave",
            "hitboxtouch", "hitbox_touch", "hitboxenter", "hitbox_enter",
            "hitboxexit", "hitbox_exit", "hitboxleave", "hitbox_leave",
            "cameraroute", "camera_route", "playcameraroute", "task", "time",
            "relgift", "rel_gift", "relrepchange", "rel_rep_change",
            "reltalk", "rel_talk", "relbutton", "rel_button",
            "assetsallow", "assetsallowed", "assetsready", "assetsdone",
            "assetsdeny", "assetsdenied"
    );

    private ScriptCustomEvents() {}

    public static String normalize(String name) {
        if (name == null) return "";
        String n = name.trim();
        if (n.isEmpty()) return "";
        // trade_buy → tradeBuy style for lookup: keep original for display, compare case-insensitive
        return n;
    }

    public static String key(String name) {
        return normalize(name).toLowerCase(Locale.ROOT).replace("_", "");
    }

    public static boolean isReserved(String name) {
        return RESERVED.contains(key(name));
    }

    /** Built-in events that still use the custom on/await pipeline. */
    public static boolean canAwaitAsCustom(String name) {
        return org.zonarstudio.spraute_engine.resource.AssetSyncTracker.isEngineEvent(name)
                || !isReserved(name);
    }

    /** @return true if newly registered */
    public static boolean register(String name) {
        String n = normalize(name);
        if (n.isEmpty()) return false;
        if (isReserved(n)) {
            return false;
        }
        return REGISTERED.add(key(n));
    }

    public static boolean isRegistered(String name) {
        String k = key(name);
        return !k.isEmpty() && REGISTERED.contains(k);
    }

    public static Set<String> registeredKeys() {
        return Collections.unmodifiableSet(REGISTERED);
    }

    public static void clear() {
        REGISTERED.clear();
    }
}
