package org.sprautestudio.st_addon_spraute.gui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.editor.project.Project;
import org.sprautestudio.st_addon_spraute.gui.GuiDoc;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;
import org.sprautestudio.st_addon_spraute.ui.UI;
import org.sprautestudio.st_addon_spraute.ui.Widget;

import java.util.function.BiConsumer;

/**
 * Shows a Spraute GUI in the game. Opened by a story ({@code gui open}) or from the editor as a
 * test; button clicks and closing are reported to the story through the network.
 */
public final class SprauteGuiScreen extends UI {
    private final GuiView view;
    private final int scriptId;
    private final String guiName;
    /** Local test from the editor: events go here instead of the server. */
    private final BiConsumer<String, String> localListener;
    private final Runnable afterClose;
    private boolean closedReported;

    private static SprauteGuiScreen current;

    private SprauteGuiScreen(GuiDoc doc, String project, Project local, int scriptId, String guiName,
                             BiConsumer<String, String> localListener, Runnable afterClose) {
        super(Component.literal(doc.get("title")));
        this.scriptId = scriptId;
        this.guiName = guiName;
        this.localListener = localListener;
        this.afterClose = afterClose;
        view = new GuiView(doc, project);
        view.localProject = local;
        view.listener = this::onEvent;
        root.add(new Widget() {
            @Override
            public void layout() {
                view.bounds(0, 0, vw, vh);
            }
        });
        root.add(view);
    }

    /** Opened by a story. */
    public static void openFromServer(Packets.GuiOpen msg) {
        GuiDoc doc = GuiDoc.fromJson(msg.json());
        SprauteGuiScreen s = new SprauteGuiScreen(doc, msg.project(), null, msg.scriptId(), msg.name(), null, null);
        s.show();
    }

    /** Test from the editor. */
    public static void openLocal(GuiDoc doc, Project project, BiConsumer<String, String> events, Runnable afterClose) {
        SprauteGuiScreen s = new SprauteGuiScreen(GuiDoc.fromJson(doc.toJson()), project.name(), project, -1, doc.get("title"), events, afterClose);
        s.show();
    }

    private void show() {
        current = this;
        Minecraft.getInstance().setScreen(this);
        String sound = view.doc.get("openSound");
        org.sprautestudio.st_addon_spraute.engine.client.ProjectSounds.ui(view.project, sound);
    }

    public static SprauteGuiScreen current() {
        return current != null && Minecraft.getInstance().screen == current ? current : null;
    }

    public static void applyUpdate(Packets.GuiUpdate msg) {
        SprauteGuiScreen s = current();
        if (s == null || s.scriptId != msg.scriptId()) return;
        s.view.update(msg.element(), msg.prop(), msg.value());
    }

    public static void closeFromServer(Packets.GuiClose msg) {
        SprauteGuiScreen s = current();
        if (s == null || (msg.scriptId() >= 0 && s.scriptId != msg.scriptId())) return;
        s.closedReported = true; // the story closed it itself
        s.onClose();
    }

    private void onEvent(String element, String kind) {
        if (kind.equals("close")) {
            onClose();
            return;
        }
        if (localListener != null) localListener.accept(element, kind);
        else SprauteNetwork.sendToServer(new Packets.GuiEvent(scriptId, guiName, element, kind, view.inputs()));
    }

    @Override
    public boolean isPauseScreen() {
        return GuiDoc.toBool(view.doc.get("pause"));
    }

    @Override
    protected boolean onShortcut(int key, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE && GuiDoc.toBool(view.doc.get("closeOnEsc"))) {
            onClose();
            return true;
        }
        return view.keyPressed(key, 0, mods);
    }

    @Override
    public void onClose() {
        if (!closedReported) {
            closedReported = true;
            if (localListener != null) localListener.accept("", "close");
            else SprauteNetwork.sendToServer(new Packets.GuiEvent(scriptId, guiName, "", "close", view.inputs()));
        }
        super.onClose();
        if (current == this) current = null;
        if (afterClose != null) afterClose.run();
    }
}
