package org.sprautestudio.st_addon_spraute.editor;

import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Client-side mirror of the server's story state plus the editor console log. */
public final class ClientState {
    private static final ClientState INSTANCE = new ClientState();

    public static ClientState get() {
        return INSTANCE;
    }

    public record LogLine(long time, int level, String source, String text) {}

    private Packets.Status status = new Packets.Status(false, List.of(), List.of());
    private long statusTime;
    private final List<LogLine> log = new ArrayList<>();
    private int logVersion;
    private int statusVersion;
    public Consumer<Packets.DeployResult> deployListener;

    private ClientState() {}

    public Packets.Status status() {
        return status;
    }

    public long statusAge() {
        return statusTime == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - statusTime;
    }

    public int statusVersion() {
        return statusVersion;
    }

    public Packets.ScriptEntry script(int id) {
        for (Packets.ScriptEntry e : status.scripts()) if (e.id == id) return e;
        return null;
    }

    public List<Integer> scriptIds() {
        List<Integer> ids = new ArrayList<>();
        for (Packets.ScriptEntry e : status.scripts()) ids.add(e.id);
        return ids;
    }

    public List<LogLine> log() {
        return log;
    }

    public int logVersion() {
        return logVersion;
    }

    public void clearLog() {
        log.clear();
        logVersion++;
    }

    public void addLocal(int level, String source, String text) {
        for (String line : text.split("\n")) log.add(new LogLine(System.currentTimeMillis(), level, source, line));
        while (log.size() > 2000) log.remove(0);
        logVersion++;
    }

    // ---------------------------------------------------------------- packets

    public void onStatus(Packets.Status s) {
        status = s;
        statusTime = System.currentTimeMillis();
        statusVersion++;
    }

    public void onLog(Packets.Log l) {
        addLocal(l.level(), l.source(), l.text());
    }

    public void onDeployResult(Packets.DeployResult r) {
        for (String m : r.messages()) addLocal(r.ok() ? Packets.Log.SUCCESS : Packets.Log.ERROR, "Деплой #" + r.id(), m);
        if (deployListener != null) deployListener.accept(r);
    }

    // ---------------------------------------------------------------- actions

    public void requestStatus() {
        if (net.minecraft.client.Minecraft.getInstance().getConnection() == null) return;
        SprauteNetwork.sendToServer(new Packets.RequestStatus());
    }

    public void command(String command) {
        if (net.minecraft.client.Minecraft.getInstance().getConnection() == null) {
            addLocal(Packets.Log.ERROR, "Spraute", "Нет подключения к миру");
            return;
        }
        SprauteNetwork.sendToServer(new Packets.RunCommand(command));
    }

    public void deploy(String project, String source, boolean start, java.util.Map<String, String> guis) {
        if (net.minecraft.client.Minecraft.getInstance().getConnection() == null) {
            addLocal(Packets.Log.ERROR, "Spraute", "Нет подключения к миру");
            return;
        }
        SprauteNetwork.sendToServer(new Packets.Deploy(project, source, start, guis));
    }

    /** Sends the project's interfaces, shops, quests, zones… to the world (after saving one). */
    public void sources(String project, java.util.Map<String, String> guis) {
        if (net.minecraft.client.Minecraft.getInstance().getConnection() == null) return;
        SprauteNetwork.sendToServer(new Packets.SourcesSet(project, guis));
    }

    /** Sends the project's engine settings to the world. */
    public void engine(String json) {
        if (net.minecraft.client.Minecraft.getInstance().getConnection() == null) return;
        SprauteNetwork.sendToServer(new Packets.EngineSet(json));
    }

    public void undeploy(int id) {
        SprauteNetwork.sendToServer(new Packets.Undeploy(id));
    }
}
