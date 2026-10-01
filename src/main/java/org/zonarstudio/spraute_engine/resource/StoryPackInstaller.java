package org.zonarstudio.spraute_engine.resource;

import java.nio.file.Path;

/**
 * @deprecated use {@link StoryPackRegistry#boot(Path)} — packs are mounted, not unpacked.
 */
@Deprecated
public final class StoryPackInstaller {
    private StoryPackInstaller() {}

    public static void installAll(Path gameDir) {
        StoryPackRegistry.boot(gameDir);
    }
}
