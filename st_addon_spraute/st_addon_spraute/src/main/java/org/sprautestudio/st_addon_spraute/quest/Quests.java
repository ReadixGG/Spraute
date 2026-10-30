package org.sprautestudio.st_addon_spraute.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.sprautestudio.st_addon_spraute.core.system.Coins;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.net.Packets;
import org.sprautestudio.st_addon_spraute.net.SprauteNetwork;
import org.sprautestudio.st_addon_spraute.runtime.GameEvents;
import org.sprautestudio.st_addon_spraute.runtime.SprauteRuntime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * The quests of the world on the server: who has which (by player uuid), how far each task got,
 * saved in {@code <world>/spraute/quests.json}. Every change sends the player their journal.
 */
public final class Quests {
    private Quests() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final class QState {
        /** active, completed, failed. */
        String state = "active";
        long order;
        Map<String, Integer> progress = new LinkedHashMap<>();
        Set<String> revealed = new LinkedHashSet<>();
    }

    static final class PlayerState {
        Map<String, QState> quests = new LinkedHashMap<>();
        String tracked = "";
    }

    private static Map<String, PlayerState> players;
    private static long counter;

    // ------------------------------------------------------------------ storage

    public static String key(String project, String name) {
        return project + "/" + name;
    }

    public static QuestDef def(String project, String name) {
        String json = SprauteRuntime.get().gui(project, "quest:" + name);
        return json == null ? null : QuestDef.fromJson(json);
    }

    /**
     * Which quest a story means: {project, file name}. The file name as written, else the same name in
     * other letters case, else a quest whose title is that ("Секретный подвал" for a file "подвал"),
     * first in the story's own project, then — when only one matches — in the other projects.
     */
    public static String[] resolve(String project, String name) {
        String n = name == null ? "" : name.trim();
        if (def(project, n) != null) return new String[]{project, n};
        var all = SprauteRuntime.get().allGuis();
        List<String[]> own = new ArrayList<>(), other = new ArrayList<>();
        for (var pe : all.entrySet()) {
            for (var ge : pe.getValue().entrySet()) {
                if (!ge.getKey().startsWith("quest:")) continue;
                String file = ge.getKey().substring(6);
                boolean match = file.equalsIgnoreCase(n);
                if (!match) {
                    QuestDef d = QuestDef.fromJson(ge.getValue());
                    match = d.title.trim().equalsIgnoreCase(n);
                }
                if (match) (pe.getKey().equals(project) ? own : other).add(new String[]{pe.getKey(), file});
            }
        }
        if (!own.isEmpty()) return own.get(0);
        if (other.size() == 1) return other.get(0);
        return new String[]{project, n};
    }

    /** The quests a project has (file names), for messages. */
    public static List<String> names(String project) {
        List<String> out = new ArrayList<>();
        var m = SprauteRuntime.get().allGuis().get(project);
        if (m != null) for (String k : m.keySet()) if (k.startsWith("quest:")) out.add(k.substring(6));
        return out;
    }

    private static QuestDef def(String key) {
        int i = key.indexOf('/');
        return i < 0 ? null : def(key.substring(0, i), key.substring(i + 1));
    }

    private static Path file() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null ? null : s.getWorldPath(LevelResource.ROOT).resolve("spraute").resolve("quests.json");
    }

    private static Map<String, PlayerState> players() {
        if (players != null) return players;
        players = new LinkedHashMap<>();
        Path f = file();
        if (f != null && Files.exists(f)) {
            try {
                Map<String, PlayerState> m = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), new TypeToken<Map<String, PlayerState>>() {}.getType());
                if (m != null) players.putAll(m);
            } catch (Exception e) {
                St_addon_spraute.LOGGER.warn("Spraute: cannot read quests", e);
            }
        }
        for (PlayerState ps : players.values()) for (QState q : ps.quests.values()) counter = Math.max(counter, q.order);
        return players;
    }

    private static void save() {
        Path f = file();
        if (f == null || players == null) return;
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(players), StandardCharsets.UTF_8);
        } catch (IOException e) {
            St_addon_spraute.LOGGER.warn("Spraute: cannot save quests", e);
        }
    }

    public static void unload() {
        players = null;
        counter = 0;
    }

    private static PlayerState of(ServerPlayer p) {
        return players().computeIfAbsent(p.getUUID().toString(), k -> new PlayerState());
    }

    // ------------------------------------------------------------------ the story's commands

    /** Gives the quest (again, from the start, if it was done or failed). False — no such quest. */
    public static boolean start(ServerPlayer p, String project, String name) {
        QuestDef d = def(project, name);
        if (d == null) return false;
        String key = key(project, name);
        PlayerState ps = of(p);
        QState q = ps.quests.get(key);
        if (q != null && q.state.equals("active")) return true;
        q = new QState();
        q.order = ++counter;
        ps.quests.put(key, q);
        if (ps.tracked.isEmpty() || !"active".equals(stateOf(ps, ps.tracked))) ps.tracked = key;
        changed(p, "Новый квест", d.title, "start");
        post(p, "queststart", name, "");
        return true;
    }

    private static String stateOf(PlayerState ps, String key) {
        QState q = ps.quests.get(key);
        return q == null ? "none" : q.state;
    }

    /** Adds to a task (amount may be below zero); the task becomes seen. */
    public static boolean progress(ServerPlayer p, String project, String name, String task, int amount, boolean set) {
        QuestDef d = def(project, name);
        if (d == null) return false;
        QuestDef.Task t = d.task(task);
        if (t == null) throw new IllegalArgumentException("В квесте «" + name + "» нет задачи «" + task + "» (есть: "
                + String.join(", ", d.tasks.stream().map(x -> x.id).toList()) + ")");
        QState q = of(p).quests.get(key(project, name));
        if (q == null || !q.state.equals("active")) return true;
        int before = q.progress.getOrDefault(task, 0);
        int now = Math.max(0, Math.min(t.count, set ? amount : before + amount));
        if (now == before) return true;
        q.progress.put(task, now);
        q.revealed.add(task);
        if (now >= t.count && before < t.count) {
            post(p, "questtask", name, task);
            if (d.autoComplete && allDone(d, q)) {
                finish(p, project, name, d, q, true);
                return true;
            }
            changed(p, "Задача выполнена", t.text, "task");
        } else changed(p, "", "", "");
        return true;
    }

    private static boolean allDone(QuestDef d, QState q) {
        for (QuestDef.Task t : d.tasks) if (!t.optional && q.progress.getOrDefault(t.id, 0) < t.count) return false;
        return true;
    }

    public static boolean reveal(ServerPlayer p, String project, String name, String task) {
        QuestDef d = def(project, name);
        if (d == null) return false;
        QState q = of(p).quests.get(key(project, name));
        if (q != null && q.revealed.add(task)) changed(p, "Новая задача", d.task(task) != null ? d.task(task).text : task, "reveal");
        return true;
    }

    /** Ends the quest: done (the reward is given) or failed. */
    public static boolean end(ServerPlayer p, String project, String name, boolean done) {
        QuestDef d = def(project, name);
        if (d == null) return false;
        QState q = of(p).quests.get(key(project, name));
        if (q == null) {
            // ending one never given still counts: it shows in the journal as such
            q = new QState();
            q.order = ++counter;
            of(p).quests.put(key(project, name), q);
        }
        if (!q.state.equals("active")) return true;
        finish(p, project, name, d, q, done);
        return true;
    }

    private static void finish(ServerPlayer p, String project, String name, QuestDef d, QState q, boolean done) {
        q.state = done ? "completed" : "failed";
        if (done) {
            for (QuestDef.Task t : d.tasks) if (!t.optional) q.progress.put(t.id, t.count);
            reward(p, d.reward);
        }
        PlayerState ps = of(p);
        if (ps.tracked.equals(key(project, name))) {
            ps.tracked = "";
            for (Map.Entry<String, QState> e : ps.quests.entrySet())
                if (e.getValue().state.equals("active")) ps.tracked = e.getKey();
        }
        changed(p, done ? "Квест выполнен" : "Квест провален", d.title, done ? "complete" : "fail");
        post(p, done ? "questdone" : "questfail", name, "");
    }

    /** Forgets the quest, as if never given. */
    public static void reset(ServerPlayer p, String project, String name) {
        PlayerState ps = of(p);
        if (ps.quests.remove(key(project, name)) != null) {
            if (ps.tracked.equals(key(project, name))) ps.tracked = "";
            changed(p, "", "", "");
        }
    }

    public static void track(ServerPlayer p, String key) {
        PlayerState ps = of(p);
        if (key.isEmpty() || ps.quests.containsKey(key)) {
            ps.tracked = ps.tracked.equals(key) ? "" : key;
            changed(p, "", "", "");
        }
    }

    // ------------------------------------------------------------------ queries

    /** "none", "active", "completed" or "failed". */
    public static String state(ServerPlayer p, String project, String name) {
        String[] r = resolve(project, name);
        project = r[0];
        name = r[1];
        if (p == null) return "none";
        return stateOf(of(p), key(project, name));
    }

    public static int progressOf(ServerPlayer p, String project, String name, String task) {
        String[] r = resolve(project, name);
        project = r[0];
        name = r[1];
        if (p == null) return 0;
        QState q = of(p).quests.get(key(project, name));
        return q == null ? 0 : q.progress.getOrDefault(task, 0);
    }

    // ------------------------------------------------------------------ the reward

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
        Item it = rl == null ? null : BuiltInRegistries.ITEM.get(rl);
        return it == null ? Items.AIR : it;
    }

    private static void reward(ServerPlayer p, QuestDef.Reward r) {
        if (r.coins > 0) Coins.add(r.coins);
        if (r.xp > 0) p.giveExperiencePoints(r.xp);
        for (QuestDef.Item i : r.items) {
            Item it = item(i.item);
            if (it == Items.AIR) continue;
            int left = Math.max(1, i.count);
            while (left > 0) {
                ItemStack s = new ItemStack(it, Math.min(left, it.getMaxStackSize()));
                left -= s.getCount();
                if (!p.addItem(s)) p.drop(s, false);
            }
        }
    }

    // ------------------------------------------------------------------ the journal

    private static void post(ServerPlayer p, String type, String quest, String task) {
        Map<String, Object> data = new HashMap<>();
        data.put("quest", quest);
        data.put("task", task);
        GameEvents.post(type, p, data);
    }

    private static void changed(ServerPlayer p, String toastTitle, String toastText, String kind) {
        save();
        QuestJournal j = journal(p);
        if (!kind.isEmpty()) {
            j.toast = toastTitle + "\n" + toastText;
            j.toastKind = kind;
        }
        SprauteNetwork.sendTo(p, new Packets.QuestSync(j.toJson(), false));
    }

    /** Sends the journal (the player joined, or asked to open it). */
    public static void sync(ServerPlayer p, boolean open) {
        SprauteNetwork.sendTo(p, new Packets.QuestSync(journal(p).toJson(), open));
    }

    public static QuestJournal journal(ServerPlayer p) {
        PlayerState ps = of(p);
        QuestJournal j = new QuestJournal();
        j.tracked = ps.tracked;
        for (Map.Entry<String, QState> e : ps.quests.entrySet()) {
            QuestDef d = def(e.getKey());
            if (d == null) continue;
            QState q = e.getValue();
            QuestJournal.Entry en = new QuestJournal.Entry();
            en.key = e.getKey();
            en.title = d.title;
            en.giver = d.giver;
            en.description = d.description;
            en.layout = d.layout;
            en.state = q.state;
            en.order = q.order;
            en.reward = QuestJournal.rewardText(d.reward, i -> new ItemStack(item(i.item)).getHoverName().getString());
            for (QuestDef.Task t : d.tasks) {
                int prog = q.progress.getOrDefault(t.id, 0);
                if (t.hidden && !q.revealed.contains(t.id) && prog == 0) continue;
                QuestJournal.Task x = new QuestJournal.Task();
                x.id = t.id;
                x.text = t.text;
                x.count = t.count;
                x.optional = t.optional;
                x.progress = prog;
                en.tasks.add(x);
                // one by one: what comes after an unfinished task stays unseen
                if (d.sequential && prog < t.count && !t.optional && q.state.equals("active")) break;
            }
            j.quests.add(en);
        }
        j.quests.sort(Comparator.comparingLong((QuestJournal.Entry x) -> -x.order));
        return j;
    }
}
