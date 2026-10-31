package org.sprautestudio.spraute_engine.editor.tabs;

import org.sprautestudio.spraute_engine.editor.ClientState;
import org.sprautestudio.spraute_engine.editor.SstLanguage;
import org.sprautestudio.spraute_engine.editor.project.Project;
import org.sprautestudio.spraute_engine.editor.project.Workspace;
import org.sprautestudio.spraute_engine.lang.Analyzer;
import org.sprautestudio.spraute_engine.ui.Icon;
import org.sprautestudio.spraute_engine.ui.Theme;
import org.sprautestudio.spraute_engine.ui.code.CodeEditor;
import org.sprautestudio.spraute_engine.ui.code.LanguageSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Text editor tab for a .sst script. */
public class ScriptTab extends EditorTab {
    public final CodeEditor editor;
    private final SstLanguage language = new SstLanguage();
    private boolean lastDirty;

    public ScriptTab(Project project, Project.FileRef file, String text, Supplier<String> position, Runnable onSave) {
        super(project, file);
        language.positionProvider = position;
        language.knownScripts = () -> ClientState.get().scriptIds();
        editor = add(new CodeEditor(text, language));
        editor.positionProvider = position;
        editor.chipProvider = this::lineChips;
        editor.contextExtras = pm -> {
            valueMenu(pm);
            int ln = editor.caretLine();
            String line = editor.lineText(ln);
            String t = line.trim();
            if (t.startsWith("namecolor ")) {
                pm.separator();
                pm.item("Выбрать цвет…", org.sprautestudio.spraute_engine.ui.Icon.EDIT, null, () -> colorEditor(ln, line, false));
                return;
            }
            if (!t.startsWith("character ")) return;
            pm.separator();
            pm.item("Голова в чате…", org.sprautestudio.spraute_engine.ui.Icon.USER, null, () -> headEditor(ln, line));
            pm.item("Цвет имени…", org.sprautestudio.spraute_engine.ui.Icon.EDIT, null, () -> colorEditor(ln, line, true));
        };
        editor.showMinimap = Workspace.settings().minimap;
        editor.onSave = onSave;
        editor.onChange = e -> {
            if (e.isDirty() != lastDirty) {
                lastDirty = e.isDirty();
                if (onDirtyChanged != null) onDirtyChanged.run();
            }
        };
    }

    @Override
    public void layout() {
        editor.bounds(x, y, w, h);
        editor.layout();
    }

    @Override
    public Icon icon() {
        return Icon.CODE;
    }

    @Override
    public int iconColor() {
        return Theme.CODE_COMMAND;
    }

    @Override
    public boolean isDirty() {
        return editor.isDirty();
    }

    @Override
    public void save() throws IOException {
        project.write(file, editor.text());
        editor.markSaved();
        lastDirty = false;
        if (onDirtyChanged != null) onDirtyChanged.run();
    }

    @Override
    public String source() {
        return editor.text();
    }

    @Override
    public int scriptId() {
        Analyzer.Result r = analysis();
        return r != null && r.program().header != null ? r.program().header.id : -1;
    }

    @Override
    public List<Problem> problems() {
        List<Problem> list = new ArrayList<>();
        var doc = editor.document();
        for (LanguageSupport.Problem p : editor.problems()) {
            int line = doc.lineOf(Math.min(p.start(), doc.length()));
            int col = Math.min(p.start(), doc.length()) - doc.lineStart(line);
            list.add(new Problem(p.severity(), p.message(), title() + ":" + (line + 1) + ":" + (col + 1),
                    () -> editor.gotoOffset(p.start(), Math.max(0, p.end() - p.start()))));
        }
        return list;
    }

    @Override
    public Analyzer.Result analysis() {
        return editor.analysis() instanceof SstLanguage.SstAnalysis a ? a.result : null;
    }

    @Override
    public void reveal(int offset) {
        editor.gotoOffset(offset, 0);
    }

    @Override
    public void focusContent() {
        editor.requestFocus();
    }

    @Override
    public void undo() {
        editor.undo();
    }

    @Override
    public void redo() {
        editor.redo();
    }

    // ------------------------------------------------------------------ the little buttons at the ends of lines

    private static final java.util.Map<String, String> CHIP_WORDS = java.util.Map.ofEntries(
            java.util.Map.entry("item", "предмет…"), java.util.Map.entry("entity", "модель…"), java.util.Map.entry("anim", "анимация…"),
            java.util.Map.entry("sound", "звук…"), java.util.Map.entry("image", "картинка…"), java.util.Map.entry("cutscene", "катсцена…"),
            java.util.Map.entry("gui", "интерфейс…"), java.util.Map.entry("vfx", "эффект…"), java.util.Map.entry("shop", "магазин…"),
            java.util.Map.entry("quest", "квест…"), java.util.Map.entry("zone", "зона…"), java.util.Map.entry("fight", "стиль…"),
            java.util.Map.entry("hitbox", "хитбокс…"));

    /** Buttons for a line: a swatch for colours, a picker for every value it knows, the head of a character. */
    private java.util.List<org.sprautestudio.spraute_engine.ui.code.CodeEditor.Chip> lineChips(String text, int ln) {
        java.util.List<org.sprautestudio.spraute_engine.ui.code.CodeEditor.Chip> out = new java.util.ArrayList<>();
        int ls = 0;
        for (int i = 0; i < ln; i++) {
            int n = text.indexOf('\n', ls);
            if (n < 0) return out;
            ls = n + 1;
        }
        int le = text.indexOf('\n', ls);
        if (le < 0) le = text.length();
        String line = text.substring(ls, le);
        String t = line.trim();
        if (t.startsWith("//")) return out;
        int open = -1;
        for (int i = ls; i < le; i++) {
            if (text.charAt(i) != '"') continue;
            if (open < 0) {
                open = i;
                continue;
            }
            int q = open, close = i, qcol = open - ls;
            open = -1;
            String kind = org.sprautestudio.spraute_engine.editor.SstLanguage.valueKindAt(text, q);
            if (kind == null || !KINDS.containsKey(kind)) continue;
            String cur = text.substring(q + 1, close);
            java.util.function.BiConsumer<Integer, Integer> act = (cx, cy) -> {
                // the value may have moved since: find it again on this line
                String now = editor.text();
                int[] r = quoteAt(now, ln, qcol);
                if (r == null) return;
                String value = now.substring(r[0] + 1, r[1]);
                int[] range = {r[0] + 1, r[1]};
                pick(kind, value, v -> {
                    editor.replaceText(range[0], range[1], v);
                    range[1] = range[0] + v.length();
                });
            };
            if (kind.equals("color")) {
                int sw = 0xFF808080;
                try {
                    Integer col = org.sprautestudio.spraute_engine.runtime.Colors.parse(cur);
                    if (col != null) sw = 0xFF000000 | col;
                } catch (RuntimeException ignored) {
                }
                out.add(new org.sprautestudio.spraute_engine.ui.code.CodeEditor.Chip("", sw, 0, act));
            } else {
                out.add(new org.sprautestudio.spraute_engine.ui.code.CodeEditor.Chip(CHIP_WORDS.getOrDefault(kind, "выбрать…"), 0, 0xFF8FA0C8, act));
            }
        }
        if (t.startsWith("character ")) {
            out.add(new org.sprautestudio.spraute_engine.ui.code.CodeEditor.Chip("голова…", 0, 0xFF7BE08A, (cx, cy) -> headEditor(ln, editor.lineText(ln))));
            if (!line.matches(".*\\scolor\\s+\".*")) out.add(new org.sprautestudio.spraute_engine.ui.code.CodeEditor.Chip("+ цвет имени", 0, 0xFF8FA0C8,
                    (cx, cy) -> colorEditor(ln, editor.lineText(ln), true)));
            if (!line.matches(".*\\shitbox\\s+\".*")) out.add(new org.sprautestudio.spraute_engine.ui.code.CodeEditor.Chip("+ хитбокс", 0, 0xFF8FA0C8,
                    (cx, cy) -> {
                        var m = new org.sprautestudio.spraute_engine.ui.widgets.PopupMenu();
                        for (String w : java.util.List.of("solid", "fixed", "ghost", "damage", "mortal", "nopick"))
                            m.item(w, () -> {
                                String l = editor.lineText(ln);
                                editor.replaceLine(ln, l.replaceAll("\\s+$", "") + " hitbox \"" + w + "\"");
                            });
                        m.show(cx, cy);
                    }));
        }
        return out;
    }

    /** The quoted value starting near column {@code col} of a line: {open, close} offsets in the text. */
    private static int[] quoteAt(String text, int ln, int col) {
        int ls = 0;
        for (int i = 0; i < ln; i++) {
            int n = text.indexOf('\n', ls);
            if (n < 0) return null;
            ls = n + 1;
        }
        int le = text.indexOf('\n', ls);
        if (le < 0) le = text.length();
        int q = ls + col;
        if (q >= le || text.charAt(q) != '"') return null;
        int close = text.indexOf('"', q + 1);
        return close < 0 || close > le ? null : new int[]{q, close};
    }

    // ------------------------------------------------------------------ pick a value instead of typing it

    private static final java.util.LinkedHashMap<String, String> KINDS = new java.util.LinkedHashMap<>();
    static {
        KINDS.put("color", "Цвет");
        KINDS.put("item", "Предмет");
        KINDS.put("entity", "Сущность / модель");
        KINDS.put("anim", "Анимация");
        KINDS.put("sound", "Звук");
        KINDS.put("image", "Картинка проекта");
        KINDS.put("cutscene", "Катсцена");
        KINDS.put("gui", "Интерфейс");
        KINDS.put("vfx", "Эффект частиц");
        KINDS.put("shop", "Магазин");
        KINDS.put("quest", "Квест");
        KINDS.put("zone", "Зона");
        KINDS.put("fight", "Стиль боя");
        KINDS.put("hitbox", "Хитбокс");
    }

    /** The quoted value around the caret: {open quote, close quote} or null. */
    private int[] stringAtCaret() {
        String text = editor.text();
        int caret = editor.caret();
        int ls = text.lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
        int le = text.indexOf('\n', caret);
        if (le < 0) le = text.length();
        int open = -1;
        for (int i = ls; i < le; i++) {
            if (text.charAt(i) != '"') continue;
            if (open < 0) open = i;
            else {
                if (caret > open && caret <= i) return new int[]{open, i};
                open = -1;
            }
        }
        return null;
    }

    private void valueMenu(org.sprautestudio.spraute_engine.ui.widgets.PopupMenu pm) {
        int[] str = stringAtCaret();
        pm.separator();
        if (str != null) {
            String kind = org.sprautestudio.spraute_engine.editor.SstLanguage.valueKindAt(editor.text(), str[0]);
            if (kind != null && KINDS.containsKey(kind)) {
                String cur = editor.text().substring(str[0] + 1, str[1]);
                int[] range = {str[0] + 1, str[1]};
                pm.item("Выбрать: " + KINDS.get(kind).toLowerCase() + "…", org.sprautestudio.spraute_engine.ui.Icon.EDIT, null,
                        () -> pick(kind, cur, v -> {
                            editor.replaceText(range[0], range[1], v);
                            range[1] = range[0] + v.length();
                        })).color(0xFF4FC3F7);
            }
        }
        pm.item("Вставить значение…", org.sprautestudio.spraute_engine.ui.Icon.PLUS, null, () -> {
            var m = new org.sprautestudio.spraute_engine.ui.widgets.PopupMenu();
            for (var e : KINDS.entrySet()) m.item(e.getValue(), () -> {
                // the quotes first, then the picker fills them (and refills while a colour is being picked)
                int at = editor.caret();
                editor.insertAtCaret("\"\"");
                int[] range = {at + 1, at + 1};
                pick(e.getKey(), "", v -> {
                    editor.replaceText(range[0], range[1], v);
                    range[1] = range[0] + v.length();
                });
            });
            m.separator();
            m.item("Блок под прицелом (x, y, z)", org.sprautestudio.spraute_engine.ui.Icon.PIN, null, () -> {
                var h = net.minecraft.client.Minecraft.getInstance().hitResult;
                if (h instanceof net.minecraft.world.phys.BlockHitResult b && h.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                    var p = b.getBlockPos();
                    editor.insertAtCaret("(" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")");
                }
            });
            m.show(editor.menuX, editor.menuY);
        });
    }

    /**
     * The entity type of the character the caret's line is about: the first word of the line that is a declared
     * character (anim alice "…" → alice's "spraute_engine:…"); null — none.
     */
    private String characterTypeOnLine() {
        String text = editor.text();
        java.util.Map<String, String> types = new java.util.HashMap<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^\\s*character\\s+([\\p{L}\\w]+)\\s*=\\s*\"([^\"]+)\"").matcher(text);
        while (m.find()) types.put(m.group(1), m.group(2));
        if (types.isEmpty()) return null;
        int line = editor.caretLine();
        String[] lines = text.split("\n", -1);
        if (line < 0 || line >= lines.length) return null;
        String l = lines[line];
        java.util.regex.Matcher w = java.util.regex.Pattern.compile("[\\p{L}\\w]+").matcher(l.replaceAll("\"[^\"]*\"", " "));
        while (w.find()) if (types.containsKey(w.group())) return types.get(w.group());
        // the only character of the story
        return types.size() == 1 ? types.values().iterator().next() : null;
    }

    /** Opens the picker for a kind of value; the choice (and, for colours, every change) goes to {@code set}. */
    private void pick(String kind, String cur, java.util.function.Consumer<String> set) {
        int mx = editor.menuX, my = editor.menuY;
        switch (kind) {
            case "color" -> {
                int argb = 0xFFFFAA00;
                try {
                    Integer c = org.sprautestudio.spraute_engine.runtime.Colors.parse(cur);
                    if (c != null) argb = 0xFF000000 | c;
                } catch (RuntimeException ignored) {
                }
                org.sprautestudio.spraute_engine.ui.widgets.ColorPicker.open(argb, mx, my, v -> set.accept(String.format("#%06X", v & 0xFFFFFF)));
            }
            case "item" -> org.sprautestudio.spraute_engine.editor.assets.AssetPicker.open(org.sprautestudio.spraute_engine.lang.ParamType.ITEM, cur, set);
            case "entity" -> org.sprautestudio.spraute_engine.editor.assets.AssetPicker.open(org.sprautestudio.spraute_engine.lang.ParamType.ENTITY, cur, set);
            case "anim" -> org.sprautestudio.spraute_engine.editor.assets.AssetPicker.open(org.sprautestudio.spraute_engine.lang.ParamType.ANIM, cur, set,
                    characterTypeOnLine());
            case "sound" -> org.sprautestudio.spraute_engine.editor.assets.AssetPicker.open(org.sprautestudio.spraute_engine.lang.ParamType.SOUND, cur, set);
            default -> {
                java.util.List<String> list = switch (kind) {
                    case "image" -> org.sprautestudio.spraute_engine.editor.assets.AssetIndex.imageChoices();
                    case "cutscene" -> org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectCutscenes.get();
                    case "gui" -> new java.util.ArrayList<>(org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectGuis.get().keySet());
                    case "vfx" -> org.sprautestudio.spraute_engine.editor.assets.AssetIndex.vfxChoices();
                    case "shop" -> org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectShops.get();
                    case "quest" -> org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectQuests.get();
                    case "zone" -> org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectZones.get();
                    case "path" -> org.sprautestudio.spraute_engine.editor.assets.AssetIndex.projectPaths.get();
                    case "fight" -> org.sprautestudio.spraute_engine.editor.assets.AssetIndex.fightChoices();
                    case "hitbox" -> java.util.List.of("normal", "solid", "fixed", "ghost", "damage", "nodamage", "mortal", "immortal", "nopick", "pick");
                    default -> java.util.List.<String>of();
                };
                var m = new org.sprautestudio.spraute_engine.ui.widgets.PopupMenu();
                if (list.isEmpty()) m.item("(в проекте пока нет)", () -> {
                });
                for (String v : list) m.item(v, () -> set.accept(v)).checked(() -> v.equals(cur));
                m.show(mx, my);
            }
        }
    }

    // ------------------------------------------------------------------ colour of a name

    private static final java.util.regex.Pattern COLOR_OPT = java.util.regex.Pattern.compile("\\s+color\\s+\"[^\"]*\"");
    private static final java.util.regex.Pattern LAST_STRING = java.util.regex.Pattern.compile("\"([^\"]*)\"\\s*$");

    /** The colour picker for a character's "color" (or the colour of a namecolor line); writes it back as it changes. */
    private void colorEditor(int ln, String line, boolean declaration) {
        String cur = "";
        java.util.regex.Matcher m = declaration ? java.util.regex.Pattern.compile("color\\s+\"([^\"]*)\"").matcher(line) : LAST_STRING.matcher(line);
        if (m.find()) cur = m.group(1);
        int argb = 0xFFFFAA00;
        try {
            Integer c = org.sprautestudio.spraute_engine.runtime.Colors.parse(cur);
            if (c != null) argb = 0xFF000000 | c;
        } catch (RuntimeException ignored) {
        }
        org.sprautestudio.spraute_engine.ui.widgets.ColorPicker.open(argb, editor.x + editor.w / 2 - 90, editor.y + 40, v -> {
            String hex = String.format("\"#%06X\"", v & 0xFFFFFF);
            String nl;
            if (declaration) {
                String base = COLOR_OPT.matcher(line).replaceAll("");
                // right after the name, if there is one
                java.util.regex.Matcher nm = java.util.regex.Pattern.compile("\\sname\\s+\"[^\"]*\"").matcher(base);
                nl = nm.find() ? base.substring(0, nm.end()) + " color " + hex + base.substring(nm.end()) : base.replaceAll("\\s+$", "") + " color " + hex;
            } else {
                java.util.regex.Matcher lm = LAST_STRING.matcher(line);
                nl = lm.find() ? line.substring(0, lm.start()) + hex : line.replaceAll("\\s+$", "") + " " + hex;
            }
            editor.replaceLine(ln, nl);
        });
    }

    // ------------------------------------------------------------------ head in chat

    private static final java.util.regex.Pattern HEAD_OPT = java.util.regex.Pattern.compile(
            "\\s+(headscale|headx|heady|headyaw)\\s+\\(?\\s*-?[\\d.]+\\s*\\)?|\\s+headbone\\s+\\(?\\s*\"[^\"]*\"\\s*\\)?");

    /** Opens the visual head editor for a character declaration line and writes the result back. */
    private void headEditor(int ln, String line) {
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("=\\s*\"([^\"]+)\"").matcher(line);
        String entity = type.find() ? type.group(1) : "";
        float scale = 1;
        int dx = 0, dy = 0, yaw = 0;
        String bone = "auto";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(headscale|headx|heady|headyaw)\\s+\\(?\\s*(-?[\\d.]+)").matcher(line);
        while (m.find()) {
            float v;
            try {
                v = Float.parseFloat(m.group(2));
            } catch (NumberFormatException e) {
                continue;
            }
            switch (m.group(1)) {
                case "headscale" -> scale = v;
                case "headx" -> dx = Math.round(v);
                case "heady" -> dy = Math.round(v);
                default -> yaw = Math.round(v);
            }
        }
        java.util.regex.Matcher bm = java.util.regex.Pattern.compile("headbone\\s+\"([^\"]*)\"").matcher(line);
        if (bm.find()) bone = bm.group(1);
        org.sprautestudio.spraute_engine.editor.assets.HeadEditor.open(entity,
                new org.sprautestudio.spraute_engine.engine.client.ChatHeads.Params(scale, dx, dy, yaw, bone), p -> {
                    String base = HEAD_OPT.matcher(line).replaceAll("").replaceAll("\\s+$", "");
                    StringBuilder sb = new StringBuilder(base);
                    if (p.scale() != 1) sb.append(" headscale ").append(p.scale());
                    if (p.dx() != 0) sb.append(" headx ").append(p.dx());
                    if (p.dy() != 0) sb.append(" heady ").append(p.dy());
                    if (p.yaw() != 0) sb.append(" headyaw ").append(p.yaw());
                    if (!p.bone().equals("auto")) sb.append(" headbone \"").append(p.bone()).append('"');
                    editor.replaceLine(ln, sb.toString());
                });
    }
}
