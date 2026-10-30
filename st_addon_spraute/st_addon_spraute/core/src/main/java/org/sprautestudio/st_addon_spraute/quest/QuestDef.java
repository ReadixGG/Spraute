package org.sprautestudio.st_addon_spraute.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * A quest of a project: a title, who gave it, a description, tasks (each done once or N times,
 * optional or hidden until reached) and the reward. The story starts, advances and ends it; it can also
 * end itself when all its tasks are done.
 */
public class QuestDef {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String title = "Новый квест";
    /** Who gave it ("" — nobody named). */
    public String giver = "";
    public String description = "";
    public List<Task> tasks = new ArrayList<>();
    /** Tasks show one by one: the next appears when the one before is done. */
    public boolean sequential;
    /** Done by itself when every required task is done (the reward is given then). */
    public boolean autoComplete = true;
    public Reward reward = new Reward();
    /**
     * What the journal page shows and in what order: the description, the tasks, the reward and texts of
     * one's own. A part left out is not shown (a reward not shown is still given).
     */
    public List<Section> layout;

    public static class Section {
        /** description, tasks, reward or text. */
        public String kind = "text";
        /** A heading above it ("" — the usual one: ЗАДАЧИ, НАГРАДА; none for texts). */
        public String header = "";
        /** For kind text: what it says. */
        public String text = "";
        /** For kind text: colour ("" — the usual one), e.g. #FFD25A. */
        public String color = "";

        public Section() {
        }

        public Section(String kind) {
            this.kind = kind;
        }
    }

    public static List<Section> defaultLayout() {
        List<Section> l = new ArrayList<>();
        l.add(new Section("description"));
        l.add(new Section("tasks"));
        l.add(new Section("reward"));
        return l;
    }

    public static class Task {
        public String id = "task";
        public String text = "Сделать что-то";
        /** How many times (kill 5 zombies: 5); 1 — just done or not. */
        public int count = 1;
        /** Not needed for the quest to be done. */
        public boolean optional;
        /** Unseen until the story reveals it (quest reveal) or it gets progress. */
        public boolean hidden;
    }

    public static class Reward {
        public int coins;
        public int xp;
        public List<Item> items = new ArrayList<>();
        /** A line shown in the journal ("" — made from the above). */
        public String text = "";
    }

    public static class Item {
        public String item = "minecraft:emerald";
        public int count = 1;

        public Item() {
        }

        public Item(String item, int count) {
            this.item = item;
            this.count = count;
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static QuestDef fromJson(String json) {
        QuestDef d = null;
        try {
            d = GSON.fromJson(json, QuestDef.class);
        } catch (Exception ignored) {
        }
        if (d == null) d = new QuestDef();
        d.fix();
        return d;
    }

    public void fix() {
        if (title == null) title = "";
        if (giver == null) giver = "";
        if (description == null) description = "";
        if (tasks == null) tasks = new ArrayList<>();
        if (reward == null) reward = new Reward();
        if (reward.items == null) reward.items = new ArrayList<>();
        if (reward.text == null) reward.text = "";
        // files made before the layout existed: description, tasks, reward
        if (layout == null) layout = defaultLayout();
        layout.removeIf(java.util.Objects::isNull);
        for (Section s : layout) {
            if (s.kind == null) s.kind = "text";
            if (s.header == null) s.header = "";
            if (s.text == null) s.text = "";
            if (s.color == null) s.color = "";
        }
        for (Task t : tasks) {
            if (t.id == null || t.id.isBlank()) t.id = "task";
            if (t.text == null) t.text = "";
            t.count = Math.max(1, t.count);
        }
    }

    public Task task(String id) {
        for (Task t : tasks) if (t.id.equals(id)) return t;
        return null;
    }

    public static QuestDef example(String title) {
        QuestDef d = new QuestDef();
        d.layout = defaultLayout();
        d.title = title;
        d.giver = "Старейшина";
        d.description = "Деревню каждую ночь тревожат зомби. Старейшина просит помочь: разобраться с ними и укрепить ворота.";
        d.tasks.add(task("talk", "Поговорить со старейшиной", 1, false, false));
        d.tasks.add(task("zombies", "Убить зомби у деревни", 5, false, false));
        d.tasks.add(task("planks", "Принести доски для ворот", 16, false, false));
        d.tasks.add(task("secret", "Найти, откуда они приходят", 1, true, true));
        d.reward.coins = 50;
        d.reward.xp = 30;
        d.reward.items.add(new Item("minecraft:iron_sword", 1));
        return d;
    }

    private static Task task(String id, String text, int count, boolean optional, boolean hidden) {
        Task t = new Task();
        t.id = id;
        t.text = text;
        t.count = count;
        t.optional = optional;
        t.hidden = hidden;
        return t;
    }
}
