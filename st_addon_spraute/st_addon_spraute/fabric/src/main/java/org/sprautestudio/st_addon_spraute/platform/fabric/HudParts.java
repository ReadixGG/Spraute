package org.sprautestudio.st_addon_spraute.platform.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.sprautestudio.st_addon_spraute.platform.ClientHooks;

/** The HUD by parts. The game draws it as one method: the mixin of Gui splits it up, with the names Forge gives the parts. */
public final class HudParts {
    private HudParts() {
    }

    /** Drawing a part anew at another place takes Forge's split of the HUD: here it stays at the game's place. */
    public static ClientHooks.RenderHudPart part(ResourceLocation id, GuiGraphics g, float partialTick) {
        return new ClientHooks.RenderHudPart(id, g, partialTick, Minecraft.getInstance().getWindow()) {
            @Override
            public boolean canDrawOriginal() {
                return false;
            }

            @Override
            public void drawOriginal(int width, int height) {
            }
        };
    }

    /** Whether the part is to be drawn. */
    public static boolean allowed(String name, GuiGraphics g, float partialTick) {
        ClientHooks.RenderHudPart part = part(new ResourceLocation("minecraft", name), g, partialTick);
        ClientHooks.RENDER_HUD_PART.fire(part);
        return !part.isCanceled();
    }
}
