package org.sprautestudio.spraute_engine.shop.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.sprautestudio.spraute_engine.engine.client.EngineClient;
import org.sprautestudio.spraute_engine.net.Packets;
import org.sprautestudio.spraute_engine.net.SprauteNetwork;
import org.sprautestudio.spraute_engine.shop.ShopView;

/** The shop a story opened: deals go to the server, which answers with the updated shop. */
public class ShopScreen extends Screen {
    private final ShopPanel panel;
    /** The shop of the engine studio's layout (when the project has one). */
    private final ShopParts.State live;

    private static org.sprautestudio.spraute_engine.gui.client.GuiView layout() {
        return EngineClient.window("shop");
    }

    public ShopScreen(ShopView view) {
        super(Component.literal(view.title));
        panel = new ShopPanel(view);
        live = new ShopParts.State(panel);
    }

    /** A view from the server: opens the shop, or (after a deal) refreshes the open one. */
    public static void show(String json, boolean refresh) {
        Minecraft mc = Minecraft.getInstance();
        if (json == null || json.isEmpty()) {
            if (mc.screen instanceof ShopScreen) mc.setScreen(null);
            return;
        }
        ShopView v = ShopView.fromJson(json);
        if (mc.screen instanceof ShopScreen s && s.panel.view.key.equals(v.key)) {
            s.panel.setView(v);
            if (refresh) mc.getSoundManager().play(SimpleSoundInstance.forUI(v.ok ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO,
                    v.ok ? 1.2f : 1f, 0.6f));
            return;
        }
        if (!refresh) mc.setScreen(new ShopScreen(v));
    }

    private boolean hidGui;

    /** As Spraute's shop: the game's HUD hides while the shop is open. */
    @Override
    protected void init() {
        super.init();
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.hideGui) {
            mc.options.hideGui = true;
            hidGui = true;
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (hidGui) Minecraft.getInstance().options.hideGui = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        var lay = layout();
        if (lay != null) {
            g.fill(0, 0, width, height, org.sprautestudio.spraute_engine.gui.GuiDoc.toColor(lay.doc.get("background")));
            lay.liveState = live;
            ShopParts.vars(lay);
            EngineClient.drawWindow(g, lay, mx, my);
            if (live.hovered != null && !live.hovered.isEmpty()) g.renderTooltip(font, live.hovered, mx, my);
            return;
        }
        // Spraute's warm dark shade
        g.fill(0, 0, width, height, 0xA6191513);
        panel.layout(width, height, Minecraft.getInstance().getWindow().getGuiScale());
        panel.render(g, font, EngineClient.config(), panel.wx(mx), panel.wy(my));
        if (panel.hoveredStack != null && !panel.hoveredStack.isEmpty()) g.renderTooltip(font, panel.hoveredStack, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        var lay = layout();
        float dx = lay != null ? lay.toDesignX(mx) : 0, dy = lay != null ? lay.toDesignY(my) : 0;
        boolean deal = lay != null ? ShopParts.click(live, dx, dy) : panel.click(panel.wx(mx), panel.wy(my));
        if ((lay != null ? ShopParts.hit(live, dx, dy) : panel.hit(panel.wx(mx), panel.wy(my))) != null) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.4f));
        }
        if (deal && panel.selected != null) {
            ShopView v = panel.view;
            SprauteNetwork.sendToServer(new Packets.ShopDeal(v.scriptId, v.key, panel.selected, Math.max(1, panel.qty), v.entityId));
            panel.qty = 1;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (layout() != null) ShopParts.scroll(live, delta);
        else panel.scroll(delta);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (Minecraft.getInstance().options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
