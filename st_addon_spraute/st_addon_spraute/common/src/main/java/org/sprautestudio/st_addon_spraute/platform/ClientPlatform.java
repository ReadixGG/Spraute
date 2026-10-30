package org.sprautestudio.st_addon_spraute.platform;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** What the engine asks of the mod loader on the client. A client class: only client code may touch it. */
public interface ClientPlatform {
    final class Holder {
        private static ClientPlatform instance;

        private Holder() {
        }
    }

    static ClientPlatform get() {
        return Holder.instance;
    }

    static void install(ClientPlatform platform) {
        Holder.instance = platform;
    }

    /** A key that only acts in the game itself, not over screens; another key may share it elsewhere. */
    KeyMapping gameKey(String name, int glfwKey, String category);

    /** What a key is bound to now. */
    InputConstants.Key boundKey(KeyMapping key);
}
