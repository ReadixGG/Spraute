package org.zonarstudio.spraute_engine.resource;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Unpacks {@code /bundled_plugins/**} from the mod jar into the workspace and
 * mirrors {@code plugins/<name>/scripts} → {@code scripts/plugins/<name>}.
 * Safe on dedicated servers (no Minecraft client).
 */
public final class BundledPluginUnpacker {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BundledPluginUnpacker() {}

    /** @return folder names of bundled plugins (e.g. {@code procode}) */
    public static Set<String> unpackInto(Path workspaceRoot) {
        Set<String> bundledNames = new HashSet<>();
        if (workspaceRoot == null) return bundledNames;
        Path pluginsDir = workspaceRoot.resolve("plugins");
        try {
            Files.createDirectories(pluginsDir);
        } catch (IOException e) {
            LOGGER.warn("[Spraute Engine] Cannot create plugins dir: {}", e.getMessage());
            return bundledNames;
        }
        try (InputStream idx = BundledPluginUnpacker.class.getResourceAsStream("/bundled_plugins/index.txt")) {
            if (idx == null) {
                LOGGER.warn("[Spraute Engine] No bundled plugins index in mod jar");
                return bundledNames;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(idx, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String rel = line.trim().replace('\\', '/');
                    if (rel.isEmpty()) continue;
                    int slash = rel.indexOf('/');
                    if (slash > 0) bundledNames.add(rel.substring(0, slash).toLowerCase(Locale.ROOT));
                    Path dest = pluginsDir.resolve(rel);
                    boolean overwrite = (rel.startsWith("procode/") && !rel.endsWith("plugin.json"))
                            || rel.endsWith("/lang/en.json")
                            || rel.endsWith("/lang/en_us.json");
                    if (Files.exists(dest) && !overwrite) continue;
                    try (InputStream in = BundledPluginUnpacker.class.getResourceAsStream("/bundled_plugins/" + rel)) {
                        if (in == null) continue;
                        Files.createDirectories(dest.getParent());
                        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[Spraute Engine] Failed to unpack bundled plugins: {}", e.toString());
        }
        for (String folder : bundledNames) {
            installPluginScripts(workspaceRoot, folder, false);
        }
        return bundledNames;
    }

    public static void installPluginScripts(Path workspaceRoot, String folder, boolean overwrite) {
        if (workspaceRoot == null || folder == null || folder.isBlank()) return;
        Path src = workspaceRoot.resolve("plugins").resolve(folder).resolve("scripts");
        if (!Files.isDirectory(src)) return;
        Path dest = workspaceRoot.resolve("scripts").resolve("plugins").resolve(folder);
        try {
            Files.createDirectories(dest);
            try (var stream = Files.walk(src)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    Path target = dest.resolve(src.relativize(file).toString()).normalize();
                    if (!target.startsWith(dest)) continue;
                    if (!overwrite && Files.exists(target)) continue;
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[Spraute Engine] Failed to install plugin scripts '{}': {}", folder, e.toString());
        }
    }
}
