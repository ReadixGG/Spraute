package org.zonarstudio.spraute_engine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncDebugStatePacket {
    public final List<ScriptDebugData> runningScripts;
    public final List<String> allScripts;

    public SyncDebugStatePacket(List<ScriptDebugData> runningScripts, List<String> allScripts) {
        this.runningScripts = runningScripts;
        this.allScripts = allScripts;
    }

    public static void encode(SyncDebugStatePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.runningScripts.size());
        for (ScriptDebugData script : msg.runningScripts) {
            buf.writeUtf(script.name);
            buf.writeInt(script.tasks.size());
            for (TaskDebugData task : script.tasks) {
                buf.writeUtf(task.name);
                buf.writeUtf(task.status);
            }
        }
        buf.writeInt(msg.allScripts.size());
        for (String script : msg.allScripts) {
            buf.writeUtf(script);
        }
    }

    public static SyncDebugStatePacket decode(FriendlyByteBuf buf) {
        int scriptCount = buf.readInt();
        List<ScriptDebugData> scripts = new ArrayList<>(scriptCount);
        for (int i = 0; i < scriptCount; i++) {
            String scriptName = buf.readUtf(32767);
            int taskCount = buf.readInt();
            List<TaskDebugData> tasks = new ArrayList<>(taskCount);
            for (int j = 0; j < taskCount; j++) {
                tasks.add(new TaskDebugData(buf.readUtf(32767), buf.readUtf(32767)));
            }
            scripts.add(new ScriptDebugData(scriptName, tasks));
        }
        
        int allScriptCount = buf.readInt();
        List<String> allScripts = new ArrayList<>(allScriptCount);
        for (int i = 0; i < allScriptCount; i++) {
            allScripts.add(buf.readUtf(32767));
        }
        
        return new SyncDebugStatePacket(scripts, allScripts);
    }

    public static void handle(SyncDebugStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                ClientHandler.handle(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static class ClientHandler {
        public static void handle(SyncDebugStatePacket msg) {
            org.zonarstudio.spraute_engine.client.DebugOverlay.updateState(msg.runningScripts, msg.allScripts);
        }
    }

    public static class ScriptDebugData {
        public final String name;
        public final List<TaskDebugData> tasks;

        public ScriptDebugData(String name, List<TaskDebugData> tasks) {
            this.name = name;
            this.tasks = tasks;
        }
    }

    public static class TaskDebugData {
        public final String name;
        public final String status;

        public TaskDebugData(String name, String status) {
            this.name = name;
            this.status = status;
        }
    }
}
