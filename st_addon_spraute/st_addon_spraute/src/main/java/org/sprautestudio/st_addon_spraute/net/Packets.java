package org.sprautestudio.st_addon_spraute.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** All Spraute network messages (plain data; handlers live in {@link SprauteNetwork}). */
public final class Packets {
    private Packets() {}

    private static final int MAX_SOURCE = 1 << 20;

    // ------------------------------------------------------------ client→server

    /** Compile and register a script; optionally start it right away. Carries the project's GUIs. */
    /** The project's interfaces, cutscenes, shops, quests, zones… saved in the editor: the world takes them at once. */
    public record SourcesSet(String project, java.util.Map<String, String> guis) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(project, 256);
            buf.writeVarInt(guis.size());
            guis.forEach((k, v) -> {
                buf.writeUtf(k, 256);
                buf.writeUtf(v, MAX_SOURCE);
            });
        }

        public static SourcesSet read(FriendlyByteBuf buf) {
            String project = buf.readUtf(256);
            int n = buf.readVarInt();
            java.util.Map<String, String> guis = new java.util.LinkedHashMap<>();
            for (int i = 0; i < n && i < 256; i++) guis.put(buf.readUtf(256), buf.readUtf(MAX_SOURCE));
            return new SourcesSet(project, guis);
        }
    }

    public record Deploy(String project, String source, boolean start, java.util.Map<String, String> guis) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(project, 256);
            buf.writeUtf(source, MAX_SOURCE);
            buf.writeBoolean(start);
            buf.writeVarInt(guis.size());
            guis.forEach((k, v) -> {
                buf.writeUtf(k, 256);
                buf.writeUtf(v, MAX_SOURCE);
            });
        }

        public static Deploy read(FriendlyByteBuf buf) {
            String project = buf.readUtf(256), source = buf.readUtf(MAX_SOURCE);
            boolean start = buf.readBoolean();
            int n = buf.readVarInt();
            java.util.Map<String, String> guis = new java.util.LinkedHashMap<>();
            for (int i = 0; i < n && i < 256; i++) guis.put(buf.readUtf(256), buf.readUtf(MAX_SOURCE));
            return new Deploy(project, source, start, guis);
        }
    }

    /** Player pressed a GUI button or closed the GUI (kind "click" / "close"); inputs are the text fields. */
    public record GuiEvent(int scriptId, String gui, String element, String kind, java.util.Map<String, String> inputs) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId);
            buf.writeUtf(gui, 256);
            buf.writeUtf(element, 256);
            buf.writeUtf(kind, 32);
            buf.writeVarInt(inputs.size());
            inputs.forEach((k, v) -> {
                buf.writeUtf(k, 256);
                buf.writeUtf(v, 4096);
            });
        }

        public static GuiEvent read(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            String gui = buf.readUtf(256), el = buf.readUtf(256), kind = buf.readUtf(32);
            int n = buf.readVarInt();
            java.util.Map<String, String> inputs = new java.util.LinkedHashMap<>();
            for (int i = 0; i < n && i < 128; i++) inputs.put(buf.readUtf(256), buf.readUtf(4096));
            return new GuiEvent(id, gui, el, kind, inputs);
        }
    }

    public record Undeploy(int id) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
        }

        public static Undeploy read(FriendlyByteBuf buf) {
            return new Undeploy(buf.readVarInt());
        }
    }

    /** Runs a server command (without the leading slash) and returns its output as log lines. */
    public record RunCommand(String command) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(command, 4096);
        }

        public static RunCommand read(FriendlyByteBuf buf) {
            return new RunCommand(buf.readUtf(4096));
        }
    }

    public record RequestStatus() {
        public void write(FriendlyByteBuf buf) {
        }

        public static RequestStatus read(FriendlyByteBuf buf) {
            return new RequestStatus();
        }
    }

    // ------------------------------------------------------------ server→client

    public record GuiOpen(int scriptId, String project, String name, String json) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId);
            buf.writeUtf(project, 256);
            buf.writeUtf(name, 256);
            buf.writeUtf(json, MAX_SOURCE);
        }

        public static GuiOpen read(FriendlyByteBuf buf) {
            return new GuiOpen(buf.readVarInt(), buf.readUtf(256), buf.readUtf(256), buf.readUtf(MAX_SOURCE));
        }
    }

    public record GuiUpdate(int scriptId, String element, String prop, String value) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId);
            buf.writeUtf(element, 256);
            buf.writeUtf(prop, 64);
            buf.writeUtf(value, 32767);
        }

        public static GuiUpdate read(FriendlyByteBuf buf) {
            return new GuiUpdate(buf.readVarInt(), buf.readUtf(256), buf.readUtf(64), buf.readUtf(32767));
        }
    }

    /** Which entity the head icon of a story character shows. */
    /** The body behind a character's head icon: its entity (-1 — not in the world) and its Spraute model ("" — none). */
    public record SpeechHead(int scriptId, String character, int entityId, String model) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId);
            buf.writeUtf(character, 256);
            buf.writeVarInt(entityId + 1);
            buf.writeUtf(model, 512);
        }

        public static SpeechHead read(FriendlyByteBuf buf) {
            return new SpeechHead(buf.readVarInt(), buf.readUtf(256), buf.readVarInt() - 1, buf.readUtf(512));
        }
    }

    /** Plays a cutscene on the client; looks maps look-target names to entity ids. */
    public record CameraPlay(int scriptId, String name, String json, double ox, double oy, double oz, java.util.Map<String, Integer> looks) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId);
            buf.writeUtf(name, 256);
            buf.writeUtf(json, MAX_SOURCE);
            buf.writeDouble(ox);
            buf.writeDouble(oy);
            buf.writeDouble(oz);
            buf.writeVarInt(looks.size());
            looks.forEach((k, v) -> {
                buf.writeUtf(k, 256);
                buf.writeVarInt(v);
            });
        }

        public static CameraPlay read(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            String name = buf.readUtf(256), json = buf.readUtf(MAX_SOURCE);
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            int n = buf.readVarInt();
            java.util.Map<String, Integer> looks = new java.util.HashMap<>();
            for (int i = 0; i < n && i < 256; i++) looks.put(buf.readUtf(256), buf.readVarInt());
            return new CameraPlay(id, name, json, x, y, z, looks);
        }
    }

    /** Opens the engine's choice menu. entityId: the asking character (-1 — none). */
    public record ChoiceOpen(int scriptId, int choiceId, String speaker, String question, List<String> answers, int entityId,
                             List<Boolean> locked, List<String> emotions, String emotion, List<Boolean> centered, String portrait) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId);
            buf.writeVarInt(choiceId);
            buf.writeUtf(speaker, 256);
            buf.writeUtf(question, 8192);
            buf.writeVarInt(answers.size());
            for (int i = 0; i < answers.size(); i++) {
                buf.writeUtf(answers.get(i), 1024);
                buf.writeBoolean(locked.get(i));
                buf.writeUtf(emotions.get(i), 64);
                buf.writeBoolean(centered.get(i));
            }
            buf.writeVarInt(entityId + 1);
            buf.writeUtf(emotion, 64);
            buf.writeUtf(portrait, 512);
        }

        public static ChoiceOpen read(FriendlyByteBuf buf) {
            int s = buf.readVarInt(), c = buf.readVarInt();
            String sp = buf.readUtf(256), q = buf.readUtf(8192);
            int n = buf.readVarInt();
            List<String> answers = new ArrayList<>(), emotions = new ArrayList<>();
            List<Boolean> locked = new ArrayList<>(), centered = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                answers.add(buf.readUtf(1024));
                locked.add(buf.readBoolean());
                emotions.add(buf.readUtf(64));
                centered.add(buf.readBoolean());
            }
            int entity = buf.readVarInt() - 1;
            return new ChoiceOpen(s, c, sp, q, answers, entity, locked, emotions, buf.readUtf(64), centered, buf.readUtf(512));
        }
    }

    /** From the client: the answer picked in a choice menu (1-based). */
    public record ChoiceAnswer(int scriptId, int choiceId, int answer) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId);
            buf.writeVarInt(choiceId);
            buf.writeVarInt(answer);
        }

        public static ChoiceAnswer read(FriendlyByteBuf buf) {
            return new ChoiceAnswer(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }

    /** Plays an effect at a point (following entity, or -1). Same id replaces the running one. */
    public record FxPlay(String id, String project, String json, double x, double y, double z, int entity, float scale, int scriptId) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId + 1);
            buf.writeUtf(id, 256);
            buf.writeUtf(project, 256);
            buf.writeUtf(json, MAX_SOURCE);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeVarInt(entity + 1);
            buf.writeFloat(scale);
        }

        public static FxPlay read(FriendlyByteBuf buf) {
            int script = buf.readVarInt() - 1;
            return new FxPlay(buf.readUtf(256), buf.readUtf(256), buf.readUtf(MAX_SOURCE), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readVarInt() - 1, buf.readFloat(), script);
        }
    }

    /** A key the player pressed in the game ("jump", "sneak", "sprint" or the key's name). */
    /**
     * Where the bones that carry hitbox parts are now, measured by the client while drawing a body (only the
     * client animates bones): per bone its name, pivot (x y z) and turn (quaternion x y z w), in the body's space.
     */
    public record PartPoses(int entity, List<String> bones, float[] data) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(entity);
            buf.writeVarInt(bones.size());
            for (int i = 0; i < bones.size(); i++) {
                buf.writeUtf(bones.get(i), 128);
                for (int k = 0; k < 7; k++) buf.writeFloat(data[i * 7 + k]);
            }
        }

        public static PartPoses read(FriendlyByteBuf buf) {
            int entity = buf.readVarInt();
            int n = Math.min(32, buf.readVarInt());
            List<String> bones = new java.util.ArrayList<>();
            float[] data = new float[n * 7];
            for (int i = 0; i < n; i++) {
                bones.add(buf.readUtf(128));
                for (int k = 0; k < 7; k++) data[i * 7 + k] = buf.readFloat();
            }
            return new PartPoses(entity, bones, data);
        }
    }

    public record KeyPress(String key) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(key, 64);
        }

        public static KeyPress read(FriendlyByteBuf buf) {
            return new KeyPress(buf.readUtf(64));
        }
    }

    /** An effect flying from a point: speed and drop per tick (a cast). */
    public record FxLaunch(String id, String project, String json, double x, double y, double z, double vx, double vy, double vz, double g,
                           float scale, int scriptId) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId + 1);
            buf.writeUtf(id, 256);
            buf.writeUtf(project, 256);
            buf.writeUtf(json, MAX_SOURCE);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeDouble(vx);
            buf.writeDouble(vy);
            buf.writeDouble(vz);
            buf.writeDouble(g);
            buf.writeFloat(scale);
        }

        public static FxLaunch read(FriendlyByteBuf buf) {
            int script = buf.readVarInt() - 1;
            return new FxLaunch(buf.readUtf(256), buf.readUtf(256), buf.readUtf(MAX_SOURCE), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), script);
        }
    }

    /** The shop window: a view as JSON ("" — close it); refresh — the answer to a deal. */
    public record ShopOpen(String json, boolean refresh) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(json, MAX_SOURCE);
            buf.writeBoolean(refresh);
        }

        public static ShopOpen read(FriendlyByteBuf buf) {
            return new ShopOpen(buf.readUtf(MAX_SOURCE), buf.readBoolean());
        }
    }

    /** How an entity's body behaves (Hitboxes flags) — collisions are worked out on the client too. */
    public record HitboxSync(int entityId, int flags) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeVarInt(flags);
        }

        public static HitboxSync read(FriendlyByteBuf buf) {
            return new HitboxSync(buf.readVarInt(), buf.readVarInt());
        }
    }

    /** Zones every player sees in the world: JSON key -> definition. */
    public record ZoneShow(String json) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(json, MAX_SOURCE);
        }

        public static ZoneShow read(FriendlyByteBuf buf) {
            return new ZoneShow(buf.readUtf(MAX_SOURCE));
        }
    }

    /** A zone's name shown big on the screen as the player comes in. */
    /** A notification; its icon — a picture (of the project or the game) or an item. */
    public record Notify(String text, String icon, String item, String project, boolean sound) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(text, 1024);
            buf.writeUtf(icon, 512);
            buf.writeUtf(item, 256);
            buf.writeUtf(project, 256);
            buf.writeBoolean(sound);
        }

        public static Notify read(FriendlyByteBuf buf) {
            return new Notify(buf.readUtf(1024), buf.readUtf(512), buf.readUtf(256), buf.readUtf(256), buf.readBoolean());
        }
    }

    /** A project's own sound: in the world at x, y, z (positional) or at the listener; looped ones by id. */
    public record SoundPlay(String project, String name, double x, double y, double z, boolean positional, float volume, float pitch, boolean loop,
                            String id) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(project, 256);
            buf.writeUtf(name, 256);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeBoolean(positional);
            buf.writeFloat(volume);
            buf.writeFloat(pitch);
            buf.writeBoolean(loop);
            buf.writeUtf(id, 256);
        }

        public static SoundPlay read(FriendlyByteBuf buf) {
            return new SoundPlay(buf.readUtf(256), buf.readUtf(256), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean(),
                    buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readUtf(256));
        }
    }

    /** Stops a project's sound by its id ("" — all looped ones). */
    public record SoundStop(String id) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(id, 256);
        }

        public static SoundStop read(FriendlyByteBuf buf) {
            return new SoundStop(buf.readUtf(256));
        }
    }

    public record ZoneTitle(String title, String subtitle) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(title, 512);
            buf.writeUtf(subtitle, 512);
        }

        public static ZoneTitle read(FriendlyByteBuf buf) {
            return new ZoneTitle(buf.readUtf(512), buf.readUtf(512));
        }
    }

    /** The player's quest journal as JSON; open — show the journal window. */
    public record QuestSync(String json, boolean open) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(json, MAX_SOURCE);
            buf.writeBoolean(open);
        }

        public static QuestSync read(FriendlyByteBuf buf) {
            return new QuestSync(buf.readUtf(MAX_SOURCE), buf.readBoolean());
        }
    }

    /** From the client: follow this quest on the screen ("" — none; the same again — stop). */
    public record QuestTrack(String key) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(key, 512);
        }

        public static QuestTrack read(FriendlyByteBuf buf) {
            return new QuestTrack(buf.readUtf(512));
        }
    }

    /** From the client: do an offer count times. */
    public record ShopDeal(int scriptId, String key, String offer, int count, int entityId) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId + 1);
            buf.writeUtf(key, 512);
            buf.writeUtf(offer, 256);
            buf.writeVarInt(count);
            buf.writeVarInt(entityId + 1);
        }

        public static ShopDeal read(FriendlyByteBuf buf) {
            return new ShopDeal(buf.readVarInt() - 1, buf.readUtf(512), buf.readUtf(256), buf.readVarInt(), buf.readVarInt() - 1);
        }
    }

    /** Fades out the effects with this id ("" — all) of a story (-1 — of any). */
    public record FxStop(String id, int scriptId) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(id, 256);
            buf.writeVarInt(scriptId + 1);
        }

        public static FxStop read(FriendlyByteBuf buf) {
            return new FxStop(buf.readUtf(256), buf.readVarInt() - 1);
        }
    }

    /** A camera command (JSON: op and its parameters). */
    public record CameraOp(int scriptId, String json) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId);
            buf.writeUtf(json, 8192);
        }

        public static CameraOp read(FriendlyByteBuf buf) {
            return new CameraOp(buf.readVarInt(), buf.readUtf(8192));
        }
    }

    /** From the client: a cutscene ended, was skipped or reached a mark. */
    public record CameraEvent(int scriptId, String kind, String name) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId);
            buf.writeUtf(kind, 32);
            buf.writeUtf(name, 256);
        }

        public static CameraEvent read(FriendlyByteBuf buf) {
            return new CameraEvent(buf.readVarInt(), buf.readUtf(32), buf.readUtf(256));
        }
    }

    /** Shows an interface as an overlay on top of the game. */
    public record OverlayShow(int scriptId, String project, String name, String json) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId);
            buf.writeUtf(project, 256);
            buf.writeUtf(name, 256);
            buf.writeUtf(json, MAX_SOURCE);
        }

        public static OverlayShow read(FriendlyByteBuf buf) {
            return new OverlayShow(buf.readVarInt(), buf.readUtf(256), buf.readUtf(256), buf.readUtf(MAX_SOURCE));
        }
    }

    /** Hides an overlay of a story ("" hides all of them). */
    public record OverlayHide(int scriptId, String name) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId);
            buf.writeUtf(name, 256);
        }

        public static OverlayHide read(FriendlyByteBuf buf) {
            return new OverlayHide(buf.readVarInt(), buf.readUtf(256));
        }
    }

    /** Engine settings of the world, server to client. */
    public record EngineSync(String json) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(json, MAX_SOURCE);
        }

        public static EngineSync read(FriendlyByteBuf buf) {
            return new EngineSync(buf.readUtf(MAX_SOURCE));
        }
    }

    /** New engine settings from the IDE, client to server. */
    public record EngineSet(String json) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(json, MAX_SOURCE);
        }

        public static EngineSet read(FriendlyByteBuf buf) {
            return new EngineSet(buf.readUtf(MAX_SOURCE));
        }
    }

    public record GuiClose(int scriptId) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId);
        }

        public static GuiClose read(FriendlyByteBuf buf) {
            return new GuiClose(buf.readVarInt());
        }
    }

    public static final class ScriptEntry {
        public int id;
        public String title = "";
        public String project = "";
        public boolean spraute, active, paused;
        public String stage = "";
        public int actionIndex, actionCount;
        /** What the current step is doing, in words. */
        public String doing = "";
        public final List<String> labels = new ArrayList<>();

        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeUtf(title, 512);
            buf.writeUtf(project, 256);
            buf.writeBoolean(spraute);
            buf.writeBoolean(active);
            buf.writeBoolean(paused);
            buf.writeUtf(stage, 512);
            buf.writeVarInt(actionIndex);
            buf.writeVarInt(actionCount);
            buf.writeUtf(doing, 512);
            buf.writeVarInt(labels.size());
            for (String l : labels) buf.writeUtf(l, 256);
        }

        static ScriptEntry read(FriendlyByteBuf buf) {
            ScriptEntry e = new ScriptEntry();
            e.id = buf.readVarInt();
            e.title = buf.readUtf(512);
            e.project = buf.readUtf(256);
            e.spraute = buf.readBoolean();
            e.active = buf.readBoolean();
            e.paused = buf.readBoolean();
            e.stage = buf.readUtf(512);
            e.actionIndex = buf.readVarInt();
            e.actionCount = buf.readVarInt();
            e.doing = buf.readUtf(512);
            int n = buf.readVarInt();
            for (int i = 0; i < n; i++) e.labels.add(buf.readUtf(256));
            return e;
        }
    }

    public record Status(boolean ready, List<ScriptEntry> scripts, List<String> categories) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(ready);
            buf.writeVarInt(scripts.size());
            for (ScriptEntry e : scripts) e.write(buf);
            buf.writeVarInt(categories.size());
            for (String c : categories) buf.writeUtf(c, 256);
        }

        public static Status read(FriendlyByteBuf buf) {
            boolean ready = buf.readBoolean();
            int n = buf.readVarInt();
            List<ScriptEntry> list = new ArrayList<>();
            for (int i = 0; i < n; i++) list.add(ScriptEntry.read(buf));
            int c = buf.readVarInt();
            List<String> cats = new ArrayList<>();
            for (int i = 0; i < c; i++) cats.add(buf.readUtf(256));
            return new Status(ready, list, cats);
        }
    }

    public record Log(int level, String source, String text) {
        public static final int INFO = 0, SUCCESS = 1, WARNING = 2, ERROR = 3, OUTPUT = 4;

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(level);
            buf.writeUtf(source, 256);
            buf.writeUtf(text, 32767);
        }

        public static Log read(FriendlyByteBuf buf) {
            return new Log(buf.readVarInt(), buf.readUtf(256), buf.readUtf(32767));
        }
    }

    public record DeployResult(boolean ok, int id, List<String> messages) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(ok);
            buf.writeVarInt(id);
            buf.writeVarInt(messages.size());
            for (String m : messages) buf.writeUtf(m, 4096);
        }

        public static DeployResult read(FriendlyByteBuf buf) {
            boolean ok = buf.readBoolean();
            int id = buf.readVarInt();
            int n = buf.readVarInt();
            List<String> list = new ArrayList<>();
            for (int i = 0; i < n; i++) list.add(buf.readUtf(4096));
            return new DeployResult(ok, id, list);
        }
    }

    /** A line of speech to show. */
    public record Speech(int scriptId, String name, int color, String icon, String text, String hint, boolean think, boolean waits, boolean historyOnly) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId);
            buf.writeUtf(name, 256);
            buf.writeInt(color);
            buf.writeUtf(icon, 512);
            buf.writeUtf(text, MAX_SOURCE);
            buf.writeUtf(hint, 64);
            buf.writeBoolean(think);
            buf.writeBoolean(waits);
            buf.writeBoolean(historyOnly);
        }

        public static Speech read(FriendlyByteBuf buf) {
            return new Speech(buf.readVarInt(), buf.readUtf(256), buf.readInt(), buf.readUtf(512), buf.readUtf(MAX_SOURCE), buf.readUtf(64), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        }
    }

    /** The speech window and its history empty. */
    public record SpeechClear(int scriptId) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId);
        }

        public static SpeechClear read(FriendlyByteBuf buf) {
            return new SpeechClear(buf.readVarInt());
        }
    }

    /** The newest running story (-1 — none): the speech history shows its record. */
    public record SpeechNewest(int scriptId) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId + 1);
        }

        public static SpeechNewest read(FriendlyByteBuf buf) {
            return new SpeechNewest(buf.readVarInt() - 1);
        }
    }

    /** A blackout, flash, closing eyes or a colour over the screen. */
    public record ScreenFx(String kind, int in, int hold, int out, int argb) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(kind, 16);
            buf.writeVarInt(in);
            buf.writeVarInt(hold);
            buf.writeVarInt(out);
            buf.writeInt(argb);
        }

        public static ScreenFx read(FriendlyByteBuf buf) {
            return new ScreenFx(buf.readUtf(16), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt());
        }
    }

    /** How much money the world has. */
    public record CoinsSync(int value, int need) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(value);
            buf.writeVarInt(need + 1);
        }

        public static CoinsSync read(FriendlyByteBuf buf) {
            return new CoinsSync(buf.readVarInt(), buf.readVarInt() - 1);
        }
    }

    /** The texture the player is drawn with ("" — their own skin). */
    public record PlayerSkin(String texture) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(texture, 512);
        }

        public static PlayerSkin read(FriendlyByteBuf buf) {
            return new PlayerSkin(buf.readUtf(512));
        }
    }

    /** Where the client's camera is; the server follows it (chunks, the event of a flight's end). */
    public record CameraPos(double x, double y, double z, boolean active) {
        public void write(FriendlyByteBuf buf) {
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeBoolean(active);
        }

        public static CameraPos read(FriendlyByteBuf buf) {
            return new CameraPos(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean());
        }
    }

    /** The player went on to the next paragraph of a dialog. */
    public record ChoiceParagraph(int scriptId, int choiceId, int index) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(scriptId);
            buf.writeVarInt(choiceId);
            buf.writeVarInt(index);
        }

        public static ChoiceParagraph read(FriendlyByteBuf buf) {
            return new ChoiceParagraph(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }
}
