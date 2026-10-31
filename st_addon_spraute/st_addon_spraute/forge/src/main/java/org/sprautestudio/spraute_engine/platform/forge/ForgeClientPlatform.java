package org.sprautestudio.spraute_engine.platform.forge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.sprautestudio.spraute_engine.platform.ClientPlatform;

final class ForgeClientPlatform implements ClientPlatform {
    static void install() {
        ClientPlatform.install(new ForgeClientPlatform());
    }

    @Override
    public KeyMapping gameKey(String name, int glfwKey, String category) {
        return new KeyMapping(name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, glfwKey, category);
    }

    @Override
    public InputConstants.Key boundKey(KeyMapping key) {
        return key.getKey();
    }
}
