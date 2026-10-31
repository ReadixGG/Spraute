package org.sprautestudio.spraute_engine.quest;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/** The player's quests as the journal and the tracker show them (sent by the server on every change). */
public class QuestJournal {
    private static final Gson GSON = new Gson();

    public List<Entry> quests = new ArrayList<>();
    /** "project/name" of the quest followed on the screen ("" — none). */
    public String tracked = "";
    /** A short notice to show once ("Новый квест: …"), and its kind: start, complete, fail, task. */
    public String toast = "", toastKind = "";

    public static class Entry {
        public String key, title, giver, description, reward;
        /** active, completed, failed. */
        public String state = "active";
        public long order;
        public List<Task> tasks = new ArrayList<>();
        /** The page's parts in order (null — description, tasks, reward). */
        public List<QuestDef.Section> layout;
    }

    public static class Task {
        public String id, text;
        public int count = 1, progress;
        public boolean optional;
        public boolean done() {
            return progress >= count;
        }
    }

    public Entry entry(String key) {
        for (Entry e : quests) if (e.key.equals(key)) return e;
        return null;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static QuestJournal fromJson(String json) {
        QuestJournal j = null;
        try {
            j = GSON.fromJson(json, QuestJournal.class);
        } catch (Exception ignored) {
        }
        return j != null ? j : new QuestJournal();
    }

    /** The reward as a line: "50 монет, 30 опыта, Железный меч". */
    public static String rewardText(QuestDef.Reward r, java.util.function.Function<QuestDef.Item, String> itemName) {
        if (!r.text.isEmpty()) return r.text;
        List<String> parts = new ArrayList<>();
        if (r.coins > 0) parts.add(r.coins + " монет");
        if (r.xp > 0) parts.add(r.xp + " опыта");
        for (QuestDef.Item i : r.items) parts.add(itemName.apply(i) + (i.count > 1 ? " ×" + i.count : ""));
        return String.join(", ", parts);
    }

    /** A journal of one quest for previews: in progress, the first task done. */
    public static QuestJournal preview(QuestDef d, String key) {
        QuestJournal j = new QuestJournal();
        Entry e = new Entry();
        e.key = key;
        e.title = d.title;
        e.giver = d.giver;
        e.description = d.description;
        e.reward = rewardText(d.reward, i -> org.sprautestudio.spraute_engine.lang.ItemText.id(i.item).replace("minecraft:", ""));
        e.layout = d.layout;
        boolean before = true;
        for (QuestDef.Task t : d.tasks) {
            if (t.hidden) continue;
            Task x = new Task();
            x.id = t.id;
            x.text = t.text;
            x.count = t.count;
            x.optional = t.optional;
            x.progress = e.tasks.isEmpty() ? t.count : 0;
            if (d.sequential && !before) break;
            if (!x.done()) before = false;
            e.tasks.add(x);
        }
        j.quests.add(e);
        Entry done = new Entry();
        done.key = "example/done";
        done.title = "Первые шаги";
        done.giver = "";
        done.description = "Пример выполненного квеста.";
        done.reward = "";
        done.state = "completed";
        j.quests.add(done);
        j.tracked = key;
        return j;
    }
}
