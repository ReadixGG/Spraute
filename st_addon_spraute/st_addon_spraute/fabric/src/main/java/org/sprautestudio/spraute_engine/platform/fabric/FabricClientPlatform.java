package org.sprautestudio.spraute_engine.platform.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.sprautestudio.spraute_engine.platform.ClientPlatform;

final class FabricClientPlatform implements ClientPlatform {
    static void install() {
        ClientPlatform.install(new FabricClientPlatform());
    }

    @Override
    public KeyMapping gameKey(String name, int glfwKey, String category) {
        return new KeyMapping(name, InputConstants.Type.KEYSYM, glfwKey, category);
    }

    @Override
    public InputConstants.Key boundKey(KeyMapping key) {
        return KeyBindingHelper.getBoundKeyOf(key);
    }
}
