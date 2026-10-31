package org.sprautestudio.spraute_engine.editor.visual;

import org.sprautestudio.spraute_engine.lang.Analyzer;
import org.sprautestudio.spraute_engine.lang.Ast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Turns speaker lines into the same story commands for text and visual scripts. */
public final class DialogueImport {
    private DialogueImport() {}

    public enum Trigger { F8, KEY, NPC }

    public record Line(String speaker, String text) {}
    public record Transition(Trigger trigger, String value) {}
    public record Plan(String text, List<VisualScript.Block> blocks) {}

    public static List<Line> parse(String input) {
        List<Line> lines = new ArrayList<>();
        String[] raw = input.split("\\R", -1);
        for (int i = 0; i < raw.length; i++) {
            if (raw[i].isBlank()) continue;
            int colon = raw[i].indexOf(':');
            if (colon < 1 || raw[i].substring(colon + 1).isBlank())
                throw new IllegalArgumentException("Строка " + (i + 1) + ": нужно «Имя: реплика»");
            String speaker = raw[i].substring(0, colon).trim();
            String text = raw[i].substring(colon + 1).trim();
            if (speaker.isEmpty()) throw new IllegalArgumentException("Строка " + (i + 1) + ": укажите имя персонажа");
            lines.add(new Line(speaker, text));
        }
        if (lines.isEmpty()) throw new IllegalArgumentException("Добавьте хотя бы одну реплику");
        return lines;
    }

    public static Plan plan(String existingSource, List<Line> lines, List<Transition> transitions) {
        if (lines.isEmpty() || transitions.size() != lines.size() - 1)
            throw new IllegalArgumentException("Для каждой пары реплик нужен переход");
        Ast.Program program = Analyzer.analyze(existingSource).program();
        if (program.header == null) throw new IllegalArgumentException("Сначала откройте скрипт с заголовком script");

        Map<String, String> speakers = new LinkedHashMap<>();
        Set<String> used = new LinkedHashSet<>();
        for (Ast.CharacterDecl character : program.characters) {
            used.add(character.name.toLowerCase(Locale.ROOT));
            speakers.putIfAbsent(key(character.name), character.name);
            if (character.displayName instanceof Ast.StringLit display)
                speakers.putIfAbsent(key(display.value), character.name);
        }

        List<VisualScript.Block> declarations = new ArrayList<>();
        List<VisualScript.Block> body = new ArrayList<>();
        StringBuilder declText = new StringBuilder();
        StringBuilder bodyText = new StringBuilder();
        int[] next = {1};
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            String who = speaker(line.speaker(), speakers, used, declarations, declText, next);
            Transition transition = i < transitions.size() ? transitions.get(i) : null;
            boolean customWait = transition != null && transition.trigger() != Trigger.F8;

            VisualScript.Block say = VisualScript.Block.create("cmd:say");
            say.fields.put("who", who);
            say.fields.put("text", line.text());
            if (customWait) say.fields.put("nowait", "да");
            body.add(say);
            bodyText.append("say ").append(who).append(' ').append(quote(line.text()));
            if (customWait) bodyText.append(" nowait");
            bodyText.append('\n');

            if (customWait) {
                if (transition.trigger() == Trigger.KEY) {
                    String key = transition.value() == null ? "" : transition.value().trim();
                    if (key.isEmpty()) throw new IllegalArgumentException("После реплики " + (i + 1) + ": укажите клавишу");
                    VisualScript.Block wait = VisualScript.Block.create("cmd:wait key");
                    wait.fields.put("key", key);
                    body.add(wait);
                    bodyText.append("wait key ").append(key).append('\n');
                } else {
                    String npc = transition.value() == null ? "" : transition.value().trim();
                    if (npc.isEmpty()) throw new IllegalArgumentException("После реплики " + (i + 1) + ": укажите NPC");
                    String target = speaker(npc, speakers, used, declarations, declText, next);
                    VisualScript.Block wait = VisualScript.Block.create("cmd:wait click");
                    wait.fields.put("who", target);
                    body.add(wait);
                    bodyText.append("wait click ").append(target).append('\n');
                }
            }
        }

        StringBuilder text = new StringBuilder(existingSource);
        if (!declText.isEmpty()) text.insert(program.header.end, "\n" + declText);
        if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') text.append('\n');
        text.append('\n').append(bodyText);
        List<VisualScript.Block> blocks = new ArrayList<>(declarations);
        blocks.addAll(body);
        return new Plan(text.toString(), blocks);
    }

    private static String speaker(String name, Map<String, String> speakers, Set<String> used,
                                  List<VisualScript.Block> declarations, StringBuilder text, int[] next) {
        if (name.equalsIgnoreCase("player") || name.equalsIgnoreCase("игрок")) return "player";
        String match = speakers.get(key(name));
        if (match != null) return match;
        String alias;
        do alias = "speaker_" + next[0]++; while (!used.add(alias));
        speakers.put(key(name), alias);
        VisualScript.Block block = VisualScript.Block.create("character");
        block.fields.put("name", alias);
        block.fields.put("type", "spraute:npc");
        block.fields.put("display", name);
        declarations.add(block);
        text.append("character ").append(alias).append(" = \"spraute:npc\" name ").append(quote(name)).append('\n');
        return alias;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
