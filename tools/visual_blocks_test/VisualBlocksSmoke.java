import org.zonarstudio.spraute_engine.client.editor.visual.VisualBlockCodegen;
import org.zonarstudio.spraute_engine.client.editor.visual.VisualBlockDef;
import org.zonarstudio.spraute_engine.client.editor.visual.VisualBlockDslParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Offline smoke: parse bundled procode blocks and generate code for a few of them. */
public final class VisualBlocksSmoke {
    public static void main(String[] args) throws Exception {
        Path blocksDir = Path.of("app/bundled_plugins/procode/blocks");
        int total = 0, files = 0;
        try (var stream = Files.list(blocksDir)) {
            for (Path f : stream.filter(p -> p.toString().endsWith(".spr")).sorted().toList()) {
                var defs = VisualBlockDslParser.parse(Files.readString(f), "procode");
                total += defs.size();
                files++;
            }
        }
        System.out.println("Parsed " + total + " block defs from " + files + " files");
        if (total < 100) throw new IllegalStateException("Expected 100+ defs, got " + total);

        // template block with conditions
        var vd = VisualBlockDslParser.parse(Files.readString(blocksDir.resolve("variables.spr")), "procode");
        VisualBlockDef varDecl = vd.stream().filter(d -> d.rawId.equals("var_declare")).findFirst().orElseThrow();
        Map<String, String> fields = new HashMap<>(Map.of("scope", "world", "kind", "var", "name", "hp", "init", ""));
        String code = VisualBlockCodegen.generate(varDecl, resolver(fields, Map.of("init", "10")));
        System.out.println("var_declare => " + code.trim());
        if (!code.trim().equals("world var hp = 10")) throw new IllegalStateException("template gen failed: " + code);

        // JS code block
        var nc = VisualBlockDslParser.parse(Files.readString(blocksDir.resolve("npc_create.spr")), "procode");
        VisualBlockDef npcCreate = nc.get(0);
        Map<String, String> nf = new HashMap<>();
        nf.put("id", "bob");
        nf.put("name", "Боб");
        nf.put("hp", "20");
        String npcCode = VisualBlockCodegen.generate(npcCreate, resolver(nf, Map.of()));
        System.out.println("npc_create =>\n" + npcCode);
        if (!npcCode.contains("create npc bob {") || !npcCode.contains("name = \"Боб\"")) {
            throw new IllegalStateException("JS codegen failed: " + npcCode);
        }

        // wrapper with statement input
        var oa = VisualBlockDslParser.parse(Files.readString(blocksDir.resolve("on_action.spr")), "procode");
        VisualBlockDef onAction = oa.get(0);
        Map<String, String> of = new HashMap<>(Map.of("action", "use", "target", "no", "handler", "h1"));
        String wrapped = VisualBlockCodegen.generate(onAction, new VisualBlockCodegen.ValueResolver() {
            @Override public String getVal(String name) {
                if (name.equals("DO")) return "    chat(\"hi\")";
                if (name.equals("player")) return "players.any";
                return of.getOrDefault(name, "");
            }
            @Override public boolean isFilled(String name) { return name.equals("DO") || name.equals("player"); }
            @Override public Map<String, String> fieldValues() { return of; }
        });
        System.out.println("on_action =>\n" + wrapped);
        if (!wrapped.contains("on action(players.any, \"use\") -> h1 {")) {
            throw new IllegalStateException("wrapper gen failed: " + wrapped);
        }
        System.out.println("SMOKE OK");
    }

    private static VisualBlockCodegen.ValueResolver resolver(Map<String, String> fields, Map<String, String> sockets) {
        return new VisualBlockCodegen.ValueResolver() {
            @Override public String getVal(String name) {
                if (sockets.containsKey(name)) return sockets.get(name);
                return fields.getOrDefault(name, "");
            }
            @Override public boolean isFilled(String name) {
                if (sockets.containsKey(name)) return true;
                String v = fields.get(name);
                return v != null && !v.isBlank();
            }
            @Override public Map<String, String> fieldValues() { return fields; }
        };
    }
}
