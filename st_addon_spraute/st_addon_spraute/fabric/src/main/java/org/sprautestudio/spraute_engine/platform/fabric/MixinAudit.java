package org.sprautestudio.spraute_engine.platform.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * With -Dspraute.auditMixins: applies every mixin to its class and exits, before the game opens. A mixin that no
 * longer fits fails here. Classes are only loaded: starting them needs the game, and that is what this avoids.
 * Exits by halt, as an exception here would have Fabric show a window with it.
 */
public final class MixinAudit implements PreLaunchEntrypoint {
    private static final List<String> CONFIGS = List.of("spraute_engine.mixins.json", "spraute_engine.fabric.mixins.json");

    @Override
    public void onPreLaunch() {
        if (!Boolean.getBoolean("spraute.auditMixins")) return;
        int failed = 0;
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Set<String> targets = new TreeSet<>();
            for (String config : CONFIGS) targets.addAll(targets(loader, config));
            for (String target : targets) {
                try {
                    Class.forName(target, false, loader);
                } catch (Throwable t) {
                    failed++;
                    System.out.println("[Spraute] mixin audit: " + target + " failed");
                    t.printStackTrace(System.out);
                }
            }
            System.out.println("[Spraute] mixin audit: " + targets.size() + " classes, " + failed + " failed");
            if (targets.isEmpty()) failed = 1;
        } catch (Throwable t) {
            failed = 1;
            t.printStackTrace(System.out);
        }
        System.out.flush();
        Runtime.getRuntime().halt(failed == 0 ? 0 : 1);
    }

    /** What the mixins of a config apply to, read from their files: a mixin class itself can never be loaded. */
    private static Set<String> targets(ClassLoader loader, String config) throws IOException {
        Set<String> out = new TreeSet<>();
        JsonObject json;
        try (InputStream in = loader.getResourceAsStream(config)) {
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        String pkg = json.get("package").getAsString().replace('.', '/');
        for (String side : List.of("mixins", "client")) {
            if (!json.has(side)) continue;
            for (JsonElement name : json.getAsJsonArray(side)) {
                ClassNode node = new ClassNode();
                try (InputStream in = loader.getResourceAsStream(pkg + "/" + name.getAsString().replace('.', '/') + ".class")) {
                    new ClassReader(in).accept(node, ClassReader.SKIP_CODE);
                }
                for (AnnotationNode a : node.invisibleAnnotations) {
                    if (!a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
                    for (int i = 0; i < a.values.size(); i += 2) {
                        if (!a.values.get(i).equals("value")) continue;
                        for (Object t : (List<?>) a.values.get(i + 1)) out.add(((Type) t).getClassName());
                    }
                }
            }
        }
        return out;
    }
}
