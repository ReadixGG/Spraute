package org.zonarstudio.spraute_engine.script;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Localize script parser/compiler error details for the editor console.
 * Source messages stay English in code; UI language picks RU/EN wording.
 */
public final class ScriptErrorI18n {
    private static final Map<String, String> EN_TO_RU = new LinkedHashMap<>();
    private static final String[][] PREFIX_EN_RU = {
            {"Expected expression, found ", "Ожидалось выражение, найдено "},
            {"Expected property name after '.' (got ", "Ожидалось имя свойства после '.' (получено "},
            {"Unknown await trigger: ", "Неизвестный триггер await: "},
            {"Expected '(', '=', or '.' after '", "Ожидалось '(', '=' или '.' после '"},
            {"Expected '(', '=', or '{' after '", "Ожидалось '(', '=' или '{' после '"},
            {" (got EOF)", " (конец файла)"},
            {" (got ", " (получено "},
            {"Invalid assignment target", "Недопустимая цель присваивания"},
            {"Expected script name after 'import'", "Ожидалось имя скрипта после 'import'"},
            {"Expected expression, found ", "Ожидалось выражение, найдено "},
            {"Expected identifier after 'create ", "Ожидался идентификатор после 'create "},
            {"Expected '{' to start ", "Ожидалось '{' для начала блока "},
            {"Expected property name, widget, or statement in create ui block", "Ожидалось property, widget или statement в create ui block"},
            {"await tradeSell(player, [item_id]) requires player", "await tradeSell(player, [item_id]) требует player"},
            {"await tradeBuy(player, [item_id]) requires player", "await tradeBuy(player, [item_id]) требует player"},
            {"Line ", "Строка "},
    };

    static {
        EN_TO_RU.put("await cameraRoute(player, routeName, [lockMovement, hideGui, returnSmooth, totalSeconds, afterEnd, lookMode, lookX, lookY, lookZ]) requires player and route", "await cameraRoute(player, routeName, [lockMovement, hideGui, returnSmooth, totalSeconds, afterEnd, lookMode, lookX, lookY, lookZ]) требует игрока и route");
        EN_TO_RU.put("Expected 'npc', 'model', 'hitbox', 'empty', 'particleEmitter', 'light', 'audioSource', 'postProcess', 'ui', 'command', custom type, or a static data block after 'create'", "Ожидалось 'npc', 'model', 'hitbox', 'empty', 'particleEmitter', 'light', 'audioSource', 'postProcess', 'ui', 'command', свой тип или блок данных после 'create'");
        EN_TO_RU.put("await chat(player, message, [ignore_case], [ignore_punctuation]) requires player and message", "await chat(player, message, [ignore_case], [ignore_punctuation]) требует игрока и message");
        EN_TO_RU.put("await pickup(npc_id, amount, item_id, nbt?) requires at least npc_id, amount, item_id", "await pickup(npc_id, amount, item_id, nbt?) требует как минимум npc_id, amount, item_id");
        EN_TO_RU.put("await orbPickup(player, amount, texture?) requires at least player and amount", "await orbPickup(player, amount, texture?) требует как минимум игрока и amount");
        EN_TO_RU.put("await action(player, action_type, [target]) requires player and action_type", "await action(player, action_type, [target]) требует игрока и action_type");
        EN_TO_RU.put("await position(player, x, y, z, [radius]) requires at least 4 arguments", "await position(player, x, y, z, [radius]) требует как минимум 4 аргументов");
        EN_TO_RU.put("await inventory(player, item_id, [count]) requires player and item_id", "await inventory(player, item_id, [count]) требует игрока и item_id");
        EN_TO_RU.put("await projectileHit(projectile_id, [hit_type]) requires projectile id", "await projectileHit(projectile_id, [hit_type]) требует id снаряда");
        EN_TO_RU.put("await dimension(player, dimensionId) requires player and dimension", "await dimension(player, dimensionId) требует игрока и dimension");
        EN_TO_RU.put("Expected property name, widget, or statement in create ui block", "Ожидалось property name, widget, или statement в create ui block");
        EN_TO_RU.put("await relGift(player, [npc_or_item], [item]) requires player", "await relGift(player, [npc_or_item], [item]) требует player");
        EN_TO_RU.put("await breakBlock(player, [block_id/coords]) requires player", "await breakBlock(player, [block_id/coords]) требует player");
        EN_TO_RU.put("await clickBlock(player, [block_id/coords]) requires player", "await clickBlock(player, [block_id/coords]) требует player");
        EN_TO_RU.put("await placeBlock(player, [block_id/coords]) requires player", "await placeBlock(player, [block_id/coords]) требует player");
        EN_TO_RU.put("await openChest(player, [block_id/coords]) requires player", "await openChest(player, [block_id/coords]) требует player");
        EN_TO_RU.put("await openDoor(player, [block_id/coords]) requires player", "await openDoor(player, [block_id/coords]) требует player");
        EN_TO_RU.put("await useBlock(player, [block_id/coords]) requires player", "await useBlock(player, [block_id/coords]) требует player");
        EN_TO_RU.put("await uiTouch(player, id1, id2) requires player, id1, id2", "await uiTouch(player, id1, id2) требует player, id1, id2");
        EN_TO_RU.put("await relButton(player, [btn_id], [npc]) requires player", "await relButton(player, [btn_id], [npc]) требует player");
        EN_TO_RU.put("Expected property, widget, or statement in widget block", "Ожидалось property, widget, или statement в widget block");
        EN_TO_RU.put("await kill(killer, [victim]) requires killer filter", "await kill(killer, [victim]) требует фильтр убийцы");
        EN_TO_RU.put("Expected identifier after 'create particleEmitter'", "Ожидалось identifier после 'create particleEmitter'");
        EN_TO_RU.put("Expected identifier after 'create particle'", "Ожидалось identifier после 'create particle'");
        EN_TO_RU.put("Expected '{' to start particle block", "Ожидалось '{' после create particle");
        EN_TO_RU.put("Expected identifier after 'create projectile'", "Ожидалось identifier после 'create projectile'");
        EN_TO_RU.put("Expected '{' to start projectile block", "Ожидалось '{' после create projectile");
        EN_TO_RU.put("await tradeSell(player, [item_id]) requires player", "await tradeSell(player, [item_id]) требует player");
        EN_TO_RU.put("await uiInput(player, [widget_id]) requires player", "await uiInput(player, [widget_id]) требует player");
        EN_TO_RU.put("Expected 'val' or 'var' after 'global' or 'world'", "Ожидалось 'val' или 'var' после 'global' или 'world'");
        EN_TO_RU.put("await relRepChange(player, [npc]) requires player", "await relRepChange(player, [npc]) требует player");
        EN_TO_RU.put("await tradeBuy(player, [item_id]) requires player", "await tradeBuy(player, [item_id]) требует player");
        EN_TO_RU.put("Expected function or method call after 'await'", "Ожидался вызов функции или метода после 'await'");
        EN_TO_RU.put("Expected model identifier after 'create model'", "Ожидалось model identifier после 'create model'");
        EN_TO_RU.put("Expected prefab id after 'create npc_prefab'", "Ожидалось prefab id после 'create npc_prefab'");
        EN_TO_RU.put("await relTalk(player, [npc]) requires player", "await relTalk(player, [npc]) требует player");
        EN_TO_RU.put("Expected '{' to start particleEmitter block", "Ожидалось '{' для начала particleEmitter block");
        EN_TO_RU.put("Expected NPC identifier after 'create npc'", "Ожидалось NPC identifier после 'create npc'");
        EN_TO_RU.put("Expected property name after '.' (got EOF)", "Ожидалось имя свойства после '.' (конец файла)");
        EN_TO_RU.put("Expected camera identifier after 'camera'", "Ожидалось camera identifier после 'camera'");
        EN_TO_RU.put("Expected identifier after 'create hitbox'", "Ожидалось identifier после 'create hitbox'");
        EN_TO_RU.put("Expected identifier after 'create empty'", "Ожидалось identifier после 'create empty'");
        EN_TO_RU.put("Expected identifier after 'create skin_overlay'", "Ожидалось identifier после 'create skin_overlay'");
        EN_TO_RU.put("Expected '{' to start skin_overlay block", "Ожидалось '{' для начала skin_overlay block");
        EN_TO_RU.put("Expected identifier after 'create light'", "Ожидалось identifier после 'create light'");
        EN_TO_RU.put("Expected identifier after 'create audioSource'", "Ожидалось identifier после 'create audioSource'");
        EN_TO_RU.put("Expected '{' to start audioSource block", "Ожидалось '{' для начала audioSource block");
        EN_TO_RU.put("Expected identifier after 'create postProcess'", "Ожидалось identifier после 'create postProcess'");
        EN_TO_RU.put("Expected '{' to start postProcess block", "Ожидалось '{' для начала postProcess block");
        EN_TO_RU.put("Expected '{' to start npc_prefab block", "Ожидалось '{' для начала npc_prefab block");
        EN_TO_RU.put("Expected variable name for catch block", "Ожидалось variable name для catch block");
        EN_TO_RU.put("Expected '->' after event declaration", "Ожидалось '->' после event declaration");
        EN_TO_RU.put("Expected 'in' after for loop variable", "Ожидалось 'in' после для loop variable");
        EN_TO_RU.put("Expected '}' to close create ui block", "Ожидалось '}' для закрытия create ui block");
        EN_TO_RU.put("await uiClick(player) requires player", "await uiClick(player) требует player");
        EN_TO_RU.put("await uiDrop(player) requires player", "await uiDrop(player) требует player");
        EN_TO_RU.put("await uiClose(player) requires player", "await uiClose(player) требует player");
        EN_TO_RU.put("Expected ')' after else if condition", "Ожидалось ')' после else if condition");
        EN_TO_RU.put("Expected ')' after for loop iterable", "Ожидалось ')' после для loop iterable");
        EN_TO_RU.put("Expected function call after 'await'", "Ожидался вызов функции после 'await'");
        EN_TO_RU.put("Expected name after 'create command'", "Ожидалось name после 'create command'");
        EN_TO_RU.put("range() requires at least 1 argument", "range() требует хотя бы 1 аргумент");
        EN_TO_RU.put("Expected script name after 'import'", "Ожидалось имя скрипта после 'import'");
        EN_TO_RU.put("await interact() requires entity id", "await interact() требует id сущности");
        EN_TO_RU.put("Expected ')' after event arguments", "Ожидалось ')' после event arguments");
        EN_TO_RU.put("Expected ')' after while condition", "Ожидалось ')' после while condition");
        EN_TO_RU.put("Expected ']' to close list literal", "Ожидалось ']' для закрытия list literal");
        EN_TO_RU.put("Expected '{' to start camera block", "Ожидалось '{' для начала camera block");
        EN_TO_RU.put("Expected '{' to start hitbox block", "Ожидалось '{' для начала hitbox block");
        EN_TO_RU.put("Expected '}' to close widget block", "Ожидалось '}' для закрытия widget block");
        EN_TO_RU.put("Expected function name after 'fun'", "Ожидалось function name после 'fun'");
        EN_TO_RU.put("Expected variable name in for loop", "Ожидалось variable name в для loop");
        EN_TO_RU.put("await jump(player) requires player", "await jump(player) требует player");
        EN_TO_RU.put("Expected ')' after catch variable", "Ожидалось ')' после catch variable");
        EN_TO_RU.put("Expected '{' after create ui name", "Ожидалось '{' после create ui name");
        EN_TO_RU.put("Expected '{' to start empty block", "Ожидалось '{' для начала empty block");
        EN_TO_RU.put("Expected '{' to start light block", "Ожидалось '{' для начала light block");
        EN_TO_RU.put("Expected '{' to start model block", "Ожидалось '{' для начала model block");
        EN_TO_RU.put("Expected '}' to close event block", "Ожидалось '}' для закрытия event block");
        EN_TO_RU.put("Expected '}' to close map literal", "Ожидалось '}' для закрытия map literal");
        EN_TO_RU.put("await keybind() requires key name", "await keybind() требует имя клавиши");
        EN_TO_RU.put("Expected '(' after function name", "Ожидалось '(' после function name");
        EN_TO_RU.put("Expected '=' after property name", "Ожидалось '=' после property name");
        EN_TO_RU.put("Expected '=' after variable name", "Ожидалось '=' после variable name");
        EN_TO_RU.put("await death() requires entity id", "await death() требует id сущности");
        EN_TO_RU.put("Expected ')' after if condition", "Ожидалось ')' после if condition");
        EN_TO_RU.put("Expected '{' to start NPC block", "Ожидалось '{' для начала NPC block");
        EN_TO_RU.put("Expected name after 'create ui'", "Ожидалось name после 'create ui'");
        EN_TO_RU.put("Expected ')' after script name", "Ожидалось ')' после script name");
        EN_TO_RU.put("Expected '->' after every(...)", "Ожидалось '->' после every(...)");
        EN_TO_RU.put("Expected event name after 'on'", "Ожидалось event name после 'on'");
        EN_TO_RU.put("Expected handler ID after '->'", "Ожидалось handler ID после '->'");
        EN_TO_RU.put("Expected '(' after event name", "Ожидалось '(' после event name");
        EN_TO_RU.put("Expected ')' after expression", "Ожидалось ')' после expression");
        EN_TO_RU.put("Expected ')' after handler ID", "Ожидалось ')' после handler ID");
        EN_TO_RU.put("Expected ')' after parameters", "Ожидалось ')' после parameters");
        EN_TO_RU.put("await task() requires task id", "await task() требует id задачи");
        EN_TO_RU.put("await time() requires seconds", "await time() требует секунды");
        EN_TO_RU.put("Expected '(' after 'else if'", "Ожидалось '(' после 'else if'");
        EN_TO_RU.put("Expected ')' after arguments", "Ожидалось ')' после arguments");
        EN_TO_RU.put("stop_task() requires task id", "stop_task() требует id задачи");
        EN_TO_RU.put("Expected ')' after interval", "Ожидалось ')' после interval");
        EN_TO_RU.put("Expected key in map literal", "Ожидалось key в map literal");
        EN_TO_RU.put("Unterminated string literal", "Незакрытая строковая константа");
        EN_TO_RU.put("Expected '(' after 'every'", "Ожидалось '(' после 'every'");
        EN_TO_RU.put("Expected '(' after 'while'", "Ожидалось '(' после 'while'");
        EN_TO_RU.put("Expected '=' after map key", "Ожидалось '=' после map key");
        EN_TO_RU.put("Expected '(' after 'stop'", "Ожидалось '(' после 'stop'");
        EN_TO_RU.put("Expected '(' after 'task'", "Ожидалось '(' после 'task'");
        EN_TO_RU.put("Invalid assignment target", "Недопустимая цель присваивания");
        EN_TO_RU.put("Expected '(' after 'for'", "Ожидалось '(' после 'for'");
        EN_TO_RU.put("Expected ']' after index", "Ожидалось ']' после index");
        EN_TO_RU.put("Expected '(' after 'if'", "Ожидалось '(' после 'if'");
        EN_TO_RU.put("Expected parameter name", "Ожидалось parameter name");
        EN_TO_RU.put("Expected property name", "Ожидалось property name");
        EN_TO_RU.put("Expected variable name", "Ожидалось variable name");
        EN_TO_RU.put("Expected handler ID", "Ожидалось handler ID");
        EN_TO_RU.put("Expected '='", "Ожидалось '='");
        EN_TO_RU.put("Expected '{'", "Ожидалось '{'");
        EN_TO_RU.put("Expected '}'", "Ожидалось '}'");
    }

    private ScriptErrorI18n() {}

    /** True when Minecraft client language is English. Safe off-thread / dedicated. */
    public static boolean englishUi() {
        try {
            Class<?> c = Class.forName("org.zonarstudio.spraute_engine.client.editor.EditorI18n");
            Object v = c.getMethod("english").invoke(null);
            return Boolean.TRUE.equals(v);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Localize a compile/runtime detail for the current UI language. */
    public static String localize(String message) {
        return localize(message, englishUi());
    }

    public static String localize(String message, boolean english) {
        if (message == null || message.isEmpty()) return message;
        if (english) return toEnglish(message);
        return toRussian(message);
    }

    public static String toRussian(String message) {
        if (message == null || message.isEmpty()) return message;
        String exact = EN_TO_RU.get(message);
        if (exact != null) return exact;
        String out = message;
        for (String[] pair : PREFIX_EN_RU) {
            String en = pair[0];
            String ru = pair[1];
            int idx = out.indexOf(en);
            if (idx >= 0) {
                out = out.substring(0, idx) + ru + out.substring(idx + en.length());
            }
        }
        // Longest exact phrases inside the string
        for (var e : EN_TO_RU.entrySet()) {
            String en = e.getKey();
            if (en.length() < 8) continue;
            int idx = out.indexOf(en);
            if (idx >= 0) {
                out = out.substring(0, idx) + e.getValue() + out.substring(idx + en.length());
            }
        }
        return out;
    }

    public static String toEnglish(String message) {
        if (message == null || message.isEmpty()) return message;
        for (var e : EN_TO_RU.entrySet()) {
            if (e.getValue().equals(message)) return e.getKey();
        }
        String out = message;
        for (String[] pair : PREFIX_EN_RU) {
            String en = pair[0];
            String ru = pair[1];
            int idx = out.indexOf(ru);
            if (idx >= 0) {
                out = out.substring(0, idx) + en + out.substring(idx + ru.length());
            }
        }
        for (var e : EN_TO_RU.entrySet()) {
            String ru = e.getValue();
            if (ru.length() < 8) continue;
            int idx = out.indexOf(ru);
            if (idx >= 0) {
                out = out.substring(0, idx) + e.getKey() + out.substring(idx + ru.length());
            }
        }
        return out;
    }
}
