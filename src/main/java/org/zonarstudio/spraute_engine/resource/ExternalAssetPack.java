package org.zonarstudio.spraute_engine.resource;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.ResourcePackFileNotFoundException;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A custom resource pack that loads assets from an external folder
 * (e.g. run/spraute_engine/) mapped to the "spraute_engine" namespace.
 *
 * This allows the mod to resolve ResourceLocation("spraute_engine", "textures/entity/...")
 * to files at run/spraute_engine/textures/entity/...
 */
public class ExternalAssetPack extends AbstractPackResources {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAMESPACE = "spraute_engine";

    private final Path rootDir;

    /**
     * @param rootDir the root directory containing assets, e.g. "run/spraute_engine/"
     */
    public ExternalAssetPack(Path rootDir) {
        super(new File(rootDir.toUri()));
        this.rootDir = rootDir;
    }

    private InputStream getFixedJsonStream(Path file) throws IOException {
        if (file.toString().endsWith(".json")) {
            String content = Files.readString(file);
            content = content.replaceAll("\"post\"\\s*:\\s*\\[", "\"vector\": [");
            content = content.replaceAll("\"pre\"\\s*:\\s*\\[", "\"vector\": [");
            return new ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return new BufferedInputStream(Files.newInputStream(file));
    }

    private InputStream generateSoundsJson() {
        Path soundsDir = rootDir.resolve("sounds");
        if (!Files.isDirectory(soundsDir)) {
            return new ByteArrayInputStream("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        StringBuilder sb = new StringBuilder("{\n");
        boolean first = true;
        try (Stream<Path> stream = Files.walk(soundsDir)) {
            List<Path> oggFiles = stream.filter(p -> p.toString().endsWith(".ogg")).collect(Collectors.toList());
            for (Path p : oggFiles) {
                String relative = soundsDir.relativize(p).toString().replace('\\', '/');
                // Remove .ogg extension for the sound event name and the sound path in sounds.json
                String name = relative.substring(0, relative.length() - 4);
                
                if (!first) {
                    sb.append(",\n");
                }
                first = false;
                
                sb.append("  \"").append(name).append("\": {\n");
                sb.append("    \"category\": \"master\",\n");
                sb.append("    \"sounds\": [\n");
                sb.append("      {\n");
                sb.append("        \"name\": \"").append(NAMESPACE).append(":").append(name).append("\",\n");
                sb.append("        \"stream\": true\n");
                sb.append("      }\n");
                sb.append("    ]\n");
                sb.append("  }");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan sounds directory", e);
        }
        sb.append("\n}");
        return new ByteArrayInputStream(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    protected InputStream getResource(String resourcePath) throws IOException {
        if (resourcePath.equals("assets/" + NAMESPACE + "/sounds.json")) {
            return generateSoundsJson();
        }
        Path resolved = rootDir.resolve(resourcePath);
        if (Files.exists(resolved)) {
            return getFixedJsonStream(resolved);
        }
        throw new ResourcePackFileNotFoundException(rootDir.toFile(), resourcePath);
    }

    @Override
    protected boolean hasResource(String resourcePath) {
        if (resourcePath.equals("assets/" + NAMESPACE + "/sounds.json")) return true;
        return Files.exists(rootDir.resolve(resourcePath));
    }

    @Override
    public InputStream getResource(PackType type, ResourceLocation location) throws IOException {
        // We only serve assets (CLIENT_RESOURCES) for our namespace
        if (type == PackType.CLIENT_RESOURCES && location.getNamespace().equals(NAMESPACE)) {
            if (location.getPath().equals("sounds.json")) {
                return generateSoundsJson();
            }
            Path file = rootDir.resolve(location.getPath());
            if (Files.exists(file)) {
                return getFixedJsonStream(file);
            }
        }
        throw new ResourcePackFileNotFoundException(rootDir.toFile(), String.format("%s/%s/%s", type.getDirectory(), location.getNamespace(), location.getPath()));
    }

    @Override
    public boolean hasResource(PackType type, ResourceLocation location) {
        if (type == PackType.CLIENT_RESOURCES && location.getNamespace().equals(NAMESPACE)) {
            if (location.getPath().equals("sounds.json")) return true;
            return Files.exists(rootDir.resolve(location.getPath()));
        }
        return false;
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type == PackType.CLIENT_RESOURCES && Files.isDirectory(rootDir)) {
            return Set.of(NAMESPACE);
        }
        return Collections.emptySet();
    }

    @Override
    public Collection<ResourceLocation> getResources(PackType type, String namespace, String pathPrefix, Predicate<ResourceLocation> filter) {
        if (type != PackType.CLIENT_RESOURCES || !namespace.equals(NAMESPACE)) {
            return Collections.emptyList();
        }

        Path searchDir = rootDir.resolve(pathPrefix);
        if (!Files.isDirectory(searchDir)) {
            return Collections.emptyList();
        }

        try (Stream<Path> stream = Files.walk(searchDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> {
                        String relative = rootDir.relativize(path).toString().replace('\\', '/');
                        return new ResourceLocation(NAMESPACE, relative);
                    })
                    .filter(filter)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.warn("[Spraute Engine] Error scanning external assets in {}: {}", searchDir, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void close() {
        // Nothing to close
    }

    @Override
    public String getName() {
        return "Spraute Engine External Assets";
    }
}
