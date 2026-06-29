package org.zonarstudio.spraute_engine.client.cameraroute;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteEndMode;
import org.zonarstudio.spraute_engine.cameraroute.CameraRouteWaypoint;

import java.util.List;

public class CameraRouteScreen extends Screen {
    private EditBox nameBox;
    private Button flightTypeButton;
    private AbstractSliderButton durationSlider;

    // Scroll state for waypoints list (read-only display)
    private int listScrollOffset = 0;
    private static final int ROW_HEIGHT = 18;
    private static final int LIST_ROWS_VISIBLE = 5;
    private int listAreaY = 0;

    public CameraRouteScreen() {
        super(Component.translatable("spraute_engine.camera_route.screen.title"));
    }

    @Override
    protected void init() {
        listScrollOffset = 0;
        int cx = this.width / 2;
        int y = 36;

        nameBox = new EditBox(this.font, cx - 100, y, 200, 20,
                Component.translatable("spraute_engine.camera_route.screen.name"));
        nameBox.setMaxLength(48);
        nameBox.setValue("route_" + CameraRouteManager.getWaypoints().size());
        this.addRenderableWidget(nameBox);
        y += 28;

        //? if >=1.20.1 {
        flightTypeButton = Button.builder(
                Component.translatable("spraute_engine.camera_route.screen.flight_type",
                        CameraRouteManager.getFlightType().displayName()),
                b -> {
                    CameraRouteManager.setFlightType(CameraRouteManager.getFlightType().next());
                    b.setMessage(Component.translatable("spraute_engine.camera_route.screen.flight_type",
                            CameraRouteManager.getFlightType().displayName()));
                }).bounds(cx - 100, y, 200, 20).build();
        //?} else {
        /*flightTypeButton = new Button(cx - 100, y, 200, 20,
                Component.translatable("spraute_engine.camera_route.screen.flight_type",
                        CameraRouteManager.getFlightType().displayName()),
                b -> {
                    CameraRouteManager.setFlightType(CameraRouteManager.getFlightType().next());
                    b.setMessage(Component.translatable("spraute_engine.camera_route.screen.flight_type",
                            CameraRouteManager.getFlightType().displayName()));
                });
        *///?}
        this.addRenderableWidget(flightTypeButton);
        y += 24;

        durationSlider = new AbstractSliderButton(cx - 100, y, 200, 20,
                Component.translatable("spraute_engine.camera_route.screen.total",
                        String.format("%.1f", CameraRouteManager.getTotalDuration())),
                (CameraRouteManager.getTotalDuration() - 0.5f) / 59.5f) {
            @Override
            protected void updateMessage() {
                float sec = 0.5f + (float) this.value * 59.5f;
                setMessage(Component.translatable("spraute_engine.camera_route.screen.total",
                        String.format("%.1f", sec)));
            }
            @Override
            protected void applyValue() {
                CameraRouteManager.setTotalDuration(0.5f + (float) this.value * 59.5f);
            }
        };
        this.addRenderableWidget(durationSlider);
        y += 24;

        //? if >=1.20.1 {
        this.addRenderableWidget(Button.builder(
                Component.translatable("spraute_engine.camera_route.screen.end_mode",
                        Component.translatable(CameraRouteManager.getEndMode() == CameraRouteEndMode.RETURN_TO_PLAYER
                                ? "spraute_engine.camera_route.end_mode.return"
                                : "spraute_engine.camera_route.end_mode.stay")),
                b -> {
                    CameraRouteEndMode next = CameraRouteManager.getEndMode() == CameraRouteEndMode.RETURN_TO_PLAYER
                            ? CameraRouteEndMode.STAY_AT_LAST : CameraRouteEndMode.RETURN_TO_PLAYER;
                    CameraRouteManager.setEndMode(next);
                    b.setMessage(Component.translatable("spraute_engine.camera_route.screen.end_mode",
                            Component.translatable(next == CameraRouteEndMode.RETURN_TO_PLAYER
                                    ? "spraute_engine.camera_route.end_mode.return"
                                    : "spraute_engine.camera_route.end_mode.stay")));
                }).bounds(cx - 100, y, 200, 20).build());
        //?} else {
        /*this.addRenderableWidget(new Button(cx - 100, y, 200, 20,
                Component.translatable("spraute_engine.camera_route.screen.end_mode",
                        Component.translatable(CameraRouteManager.getEndMode() == CameraRouteEndMode.RETURN_TO_PLAYER
                                ? "spraute_engine.camera_route.end_mode.return"
                                : "spraute_engine.camera_route.end_mode.stay")),
                b -> {
                    CameraRouteEndMode next = CameraRouteManager.getEndMode() == CameraRouteEndMode.RETURN_TO_PLAYER
                            ? CameraRouteEndMode.STAY_AT_LAST : CameraRouteEndMode.RETURN_TO_PLAYER;
                    CameraRouteManager.setEndMode(next);
                    b.setMessage(Component.translatable("spraute_engine.camera_route.screen.end_mode",
                            Component.translatable(next == CameraRouteEndMode.RETURN_TO_PLAYER
                                    ? "spraute_engine.camera_route.end_mode.return"
                                    : "spraute_engine.camera_route.end_mode.stay")));
                }));
        *///?}
        y += 24;

        // ---- Action buttons ----
        //? if >=1.20.1 {
        this.addRenderableWidget(Button.builder(Component.translatable("spraute_engine.camera_route.screen.preview"), b -> {
            if (CameraRoutePreview.isPlaying()) { CameraRoutePreview.stop(); }
            else { onClose(); CameraRoutePreview.start(); }
        }).bounds(cx - 100, y, 96, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("spraute_engine.camera_route.screen.save"),
                b -> saveRoute()).bounds(cx + 4, y, 96, 20).build());
        y += 24;
        this.addRenderableWidget(Button.builder(Component.translatable("spraute_engine.camera_route.screen.clear"), b -> {
            CameraRouteManager.clear();
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(Component.translatable("spraute_engine.camera_route.cleared"), true);
        }).bounds(cx - 100, y, 96, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(cx + 4, y, 96, 20).build());
        //?} else {
        /*this.addRenderableWidget(new Button(cx - 100, y, 96, 20, Component.translatable("spraute_engine.camera_route.screen.preview"), b -> {
            if (CameraRoutePreview.isPlaying()) { CameraRoutePreview.stop(); }
            else { onClose(); CameraRoutePreview.start(); }
        }));
        this.addRenderableWidget(new Button(cx + 4, y, 96, 20, Component.translatable("spraute_engine.camera_route.screen.save"), b -> saveRoute()));
        y += 24;
        this.addRenderableWidget(new Button(cx - 100, y, 96, 20, Component.translatable("spraute_engine.camera_route.screen.clear"), b -> {
            CameraRouteManager.clear();
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(Component.translatable("spraute_engine.camera_route.cleared"), true);
        }));
        this.addRenderableWidget(new Button(cx + 4, y, 96, 20, Component.translatable("gui.done"), b -> onClose()));
        *///?}
        y += 26;

        listAreaY = y;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        List<CameraRouteWaypoint> pts = CameraRouteManager.getWaypoints();
        int listAreaH = LIST_ROWS_VISIBLE * ROW_HEIGHT;
        if (my >= listAreaY && my <= listAreaY + listAreaH && pts.size() > LIST_ROWS_VISIBLE) {
            int maxScroll = Math.max(0, pts.size() - LIST_ROWS_VISIBLE);
            listScrollOffset = (int) Math.max(0, Math.min(maxScroll, listScrollOffset - delta));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private void saveRoute() {
        String name = nameBox.getValue();
        try {
            CameraRouteManager.saveRoute(name);
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(
                        Component.translatable("spraute_engine.camera_route.saved", name), true);
        } catch (Exception e) {
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(
                        Component.translatable("spraute_engine.camera_route.save_failed", e.getMessage()), true);
        }
    }

    @Override
    public void render(
            //? if >=1.20.1 {
            net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick
            //?} else {
            /*com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick
            *///?}
    ) {
        //? if >=1.20.1 {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("spraute_engine.camera_route.screen.points", CameraRouteManager.getWaypoints().size()),
                this.width / 2, 24, 0xAAAAAA);

        // Waypoints list (read-only)
        List<CameraRouteWaypoint> pts = CameraRouteManager.getWaypoints();
        int cx = this.width / 2;
        if (!pts.isEmpty()) {
            graphics.drawString(this.font,
                    Component.literal("§7Точки маршрута:"),
                    cx - 100, listAreaY - 10, 0x888888);
        }
        for (int i = listScrollOffset; i < Math.min(pts.size(), listScrollOffset + LIST_ROWS_VISIBLE); i++) {
            int rowY = listAreaY + (i - listScrollOffset) * ROW_HEIGHT + 3;
            CameraRouteWaypoint wp = pts.get(i);
            String label = String.format("§7#%d §f(%.0f, %.0f, %.0f)", i + 1, wp.x(), wp.y(), wp.z());
            graphics.drawString(this.font, Component.literal(label), cx - 100, rowY, 0xFFFFFF);
        }
        if (pts.size() > LIST_ROWS_VISIBLE) {
            int maxScroll = pts.size() - LIST_ROWS_VISIBLE;
            graphics.drawString(this.font,
                    Component.literal("▲▼ " + (listScrollOffset + 1) + "–" + Math.min(pts.size(), listScrollOffset + LIST_ROWS_VISIBLE) + "/" + pts.size()),
                    cx + 104, listAreaY + LIST_ROWS_VISIBLE * ROW_HEIGHT / 2, 0x666666);
        }

        if (CameraRouteKeybinds.OPEN_MENU != null) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("spraute_engine.camera_route.screen.key_hint",
                            CameraRouteKeybinds.OPEN_MENU.getTranslatedKeyMessage()),
                    this.width / 2, this.height - 30, 0x555555);
        }
        graphics.drawCenteredString(this.font,
                Component.literal(CameraRouteManager.routesDirectory().toString()),
                this.width / 2, this.height - 18, 0x444444);
        super.render(graphics, mouseX, mouseY, partialTick);
        //?} else {
        /*this.renderBackground(poseStack);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        drawCenteredString(poseStack, this.font,
                Component.translatable("spraute_engine.camera_route.screen.points", CameraRouteManager.getWaypoints().size()),
                this.width / 2, 24, 0xAAAAAA);
        List<CameraRouteWaypoint> pts = CameraRouteManager.getWaypoints();
        int cx = this.width / 2;
        for (int i = listScrollOffset; i < Math.min(pts.size(), listScrollOffset + LIST_ROWS_VISIBLE); i++) {
            int rowY = listAreaY + (i - listScrollOffset) * ROW_HEIGHT + 3;
            CameraRouteWaypoint wp = pts.get(i);
            String label = String.format("#%d (%.0f, %.0f, %.0f)", i + 1, wp.x(), wp.y(), wp.z());
            drawString(poseStack, this.font, Component.literal(label), cx - 100, rowY, 0xFFFFFF);
        }
        if (CameraRouteKeybinds.OPEN_MENU != null) {
            drawCenteredString(poseStack, this.font,
                    Component.translatable("spraute_engine.camera_route.screen.key_hint",
                            CameraRouteKeybinds.OPEN_MENU.getTranslatedKeyMessage()),
                    this.width / 2, this.height - 30, 0x555555);
        }
        drawCenteredString(poseStack, this.font,
                Component.literal(CameraRouteManager.routesDirectory().toString()),
                this.width / 2, this.height - 18, 0x444444);
        super.render(poseStack, mouseX, mouseY, partialTick);
        *///?}
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
