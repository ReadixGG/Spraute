package org.sprautestudio.spraute_engine.editor;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.sprautestudio.spraute_engine.SprauteEngine;
import org.sprautestudio.spraute_engine.editor.project.Workspace;
import org.sprautestudio.spraute_engine.platform.ClientHooks;
import org.sprautestudio.spraute_engine.platform.ClientPlatform;
import org.sprautestudio.spraute_engine.platform.TickPhase;

import java.util.List;

/**
 * Client hooks. {@code /editor on} enables editor mode; while it is on, the editor key (N by
 * default, configurable in Controls) opens and closes the editor. {@code /editor} always opens it.
 */
public final class ClientEvents {
    private ClientEvents() {}

    public static final KeyMapping OPEN_EDITOR = ClientPlatform.get().gameKey("key.spraute_engine.open", GLFW.GLFW_KEY_N, "key.categories.spraute_engine");

    private static boolean openNextTick;
    private static boolean hintShown;

    public static boolean editorMode() {
        return Workspace.settings().editorMode;
    }

    public static void setEditorMode(boolean on) {
        Workspace.settings().editorMode = on;
        Workspace.saveSettings();
    }

    /** True when {@code key} (a GLFW key code) is the editor key binding. */
    public static boolean isEditorKey(int key) {
        InputConstants.Key k = ClientPlatform.get().boundKey(OPEN_EDITOR);
        return k.getType() == InputConstants.Type.KEYSYM && k.getValue() == key;
    }

    private static Component keyName() {
        return OPEN_EDITOR.getTranslatedKeyMessage();
    }

    public static final class ModBus {
        public static void registerHooks() {
            ClientHooks.REGISTER_KEYS.add(ModBus::registerKeys);
        }

        public static void registerKeys(ClientHooks.RegisterKeys event) {
            event.register(OPEN_EDITOR);
            event.register(org.sprautestudio.spraute_engine.editor.cutscene.CutsceneRecorder.FRAME);
        }
    }

    public static final class ForgeBus {
        public static void registerHooks() {
            ClientHooks.CLIENT_TICK.add(ForgeBus::clientTick);
            ClientHooks.LOGGING_IN.add(ForgeBus::loggedIn);
            ClientHooks.CLIENT_COMMANDS.add(ForgeBus::clientCommands);
        }

        public static void clientTick(ClientHooks.ClientTick event) {
            if (event.phase != TickPhase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (openNextTick) {
                openNextTick = false;
                SprauteScreen.open();
                return;
            }
            while (OPEN_EDITOR.consumeClick()) {
                if (mc.screen != null) continue;
                if (editorMode()) {
                    SprauteScreen.open();
                } else if (!hintShown && mc.player != null) {
                    hintShown = true;
                    mc.player.displayClientMessage(Component.literal("Режим редактора Spraute выключен. Включите: /editor on")
                            .withStyle(ChatFormatting.GRAY), true);
                }
            }
        }

        public static void loggedIn(ClientHooks.LoggingIn event) {
            // stories may run without the editor ever being opened: load every project's pictures
            org.sprautestudio.spraute_engine.editor.assets.ProjectImages.registerAll();
            org.sprautestudio.spraute_engine.runtime.models.ModelLibrary.scan();
        }

        public static void clientCommands(ClientHooks.ClientCommands event) {
            event.getDispatcher().register(Commands.literal("editor")
                    .executes(ctx -> {
                        openNextTick = true; // the chat screen closes after the command, open afterwards
                        return 1;
                    })
                    .then(Commands.literal("status").executes(ctx -> {
                        status();
                        return 1;
                    }))
                    .then(Commands.literal("mcp")
                            .executes(ctx -> {
                                mcpStatus();
                                return 1;
                            })
                            .then(Commands.argument("state", StringArgumentType.word())
                                    .suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("on", "off"), b))
                                    .executes(ctx -> {
                                        String st = StringArgumentType.getString(ctx, "state").toLowerCase();
                                        boolean on = st.equals("on") || st.equals("вкл");
                                        if (!on && !st.equals("off") && !st.equals("выкл")) {
                                            say(Component.literal("Используйте /editor mcp on или /editor mcp off").withStyle(ChatFormatting.RED));
                                            return 0;
                                        }
                                        Workspace.settings().mcp = on;
                                        Workspace.saveSettings();
                                        if (on) org.sprautestudio.spraute_engine.mcp.McpServer.start(Workspace.settings().mcpPort);
                                        else org.sprautestudio.spraute_engine.mcp.McpServer.stop();
                                        mcpStatus();
                                        return 1;
                                    })))
                    .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("on", "off", "status", "mcp"), b))
                            .executes(ctx -> {
                                String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
                                switch (mode) {
                                    case "on", "вкл" -> {
                                        setEditorMode(true);
                                        say(Component.literal("Режим редактора включён. ").withStyle(ChatFormatting.GREEN)
                                                .append(Component.literal("Клавиша ").withStyle(ChatFormatting.GRAY))
                                                .append(keyName().copy().withStyle(ChatFormatting.YELLOW))
                                                .append(Component.literal(" открывает и закрывает редактор (сменить — в Управлении).").withStyle(ChatFormatting.GRAY)));
                                    }
                                    case "off", "выкл" -> {
                                        setEditorMode(false);
                                        say(Component.literal("Режим редактора выключен. /editor по-прежнему открывает редактор.").withStyle(ChatFormatting.GRAY));
                                    }
                                    default -> {
                                        say(Component.literal("Используйте /editor, /editor on | off, /editor status или /editor mcp").withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                }
                                return 1;
                            })));
        }

        private static void mcpStatus() {
            boolean run = org.sprautestudio.spraute_engine.mcp.McpServer.running();
            int port = Workspace.settings().mcpPort;
            String url = "http://127.0.0.1:" + port + "/mcp";
            Component line = Component.literal("MCP для ИИ-ассистента: ").withStyle(ChatFormatting.GRAY)
                    .append(run ? Component.literal("работает").withStyle(ChatFormatting.GREEN) : Component.literal("выключен").withStyle(ChatFormatting.RED))
                    .append(Component.literal(run ? " на " + url : " — /editor mcp on").withStyle(ChatFormatting.GRAY));
            say(line);
            if (run) {
                String cmd = "claude mcp add --transport http spraute " + url;
                say(Component.literal("Подключить Claude Code: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(cmd).withStyle(s -> s.withColor(ChatFormatting.YELLOW)
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.COPY_TO_CLIPBOARD, cmd))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Нажмите, чтобы скопировать"))))));
                String last = org.sprautestudio.spraute_engine.mcp.McpServer.lastClient();
                long ago = System.currentTimeMillis() - org.sprautestudio.spraute_engine.mcp.McpServer.lastCall();
                if (!last.isEmpty()) say(Component.literal("Подключён: " + last + ", последний запрос " + (ago / 1000) + " с назад").withStyle(ChatFormatting.GRAY));
            }
        }

        private static void status() {
            say(Component.literal("Режим редактора: ").withStyle(ChatFormatting.GRAY)
                    .append(editorMode() ? Component.literal("включён").withStyle(ChatFormatting.GREEN)
                            : Component.literal("выключен").withStyle(ChatFormatting.RED))
                    .append(Component.literal(" — /editor on | off, /editor mcp, клавиша ").withStyle(ChatFormatting.GRAY))
                    .append(keyName().copy().withStyle(ChatFormatting.YELLOW)));
        }

        private static void say(Component c) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.displayClientMessage(c, false);
        }
    }
}
