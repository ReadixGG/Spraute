package org.sprautestudio.st_addon_spraute.runtime;

import org.sprautestudio.st_addon_spraute.core.script.ScriptInstance;
import org.sprautestudio.st_addon_spraute.core.script.ScriptPerformerBuilder;
import org.sprautestudio.st_addon_spraute.core.script.ScriptEnvironment;
import org.sprautestudio.st_addon_spraute.core.script.Character;
import org.sprautestudio.st_addon_spraute.core.world.Location;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.sprautestudio.st_addon_spraute.lang.Ast;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A script instance whose performer is generated from a deployed .sst program.
 * Registered in {@link ScriptEnvironment#constructors} under the program's id.
 */
public class SprauteScriptInstance extends ScriptInstance {
    final SprauteRuntime.Deployed deployed;
    final Eval eval = new Eval(this);

    public Eval eval() {
        return eval;
    }

    /** The story stopped (by hand, by finishing or by the IDE): what it showed in the world goes too. */
    @Override
    public void handleStop() {
        super.handleStop();
        try {
            org.sprautestudio.st_addon_spraute.net.SprauteNetwork.sendToAll(new org.sprautestudio.st_addon_spraute.net.Packets.FxStop("", getId()));
            org.sprautestudio.st_addon_spraute.net.SprauteNetwork.sendToAll(new org.sprautestudio.st_addon_spraute.net.Packets.OverlayHide(getId(), ""));
            org.sprautestudio.st_addon_spraute.fight.Fights.stopScript(getId());
            org.sprautestudio.st_addon_spraute.fx.Casts.stopScript(getId());
            BossMoves.stopScript(getId());
        } catch (Throwable ignored) {
        }
    }
    final Map<String, Object> vars = new LinkedHashMap<>();
    final Map<String, Character<?>> characters = new HashMap<>();
    final Map<String, Ast.LocationDecl> locationDecls = new HashMap<>();
    /** Speech icon of each declared character. */
    final Map<String, String> speechIcons = new HashMap<>();
    /** Name colours in speech: character name (or "player") -> 0xRRGGBB. */
    final Map<String, Integer> nameColors = new HashMap<>();
    /** cooldown("name", seconds): when each one is ready again (server tick). */
    final Map<String, Integer> cooldowns = new HashMap<>();
    /** Names changed by the story ("name alice …"): character (or "player") -> name in speech. */
    final Map<String, String> speechNames = new HashMap<>();
    /** Custom dialog face of a character (texture), if it has one. */
    final Map<String, net.minecraft.resources.ResourceLocation> portraits = new HashMap<>();
    /** Return labels of function calls, per performer. */
    final Map<String, java.util.Deque<String>> callStacks = new HashMap<>();
    int lastAnswer;
    /** Number of the choice menu waiting for an answer (answers to older menus are ignored). */
    int choiceSeq;

    /** An answer from the engine's choice menu. */
    public static void answer(int scriptId, int choiceId, int answer) {
        SprauteRuntime.env().ifPresent(env -> {
            if (env.scriptInstances.get(scriptId) instanceof SprauteScriptInstance s && s.choiceSeq == choiceId && s.lastAnswer == 0) s.lastAnswer = answer;
        });
    }
    /** GUI state: open interface, last pressed button, counters for waits and text-field values. */
    String openGui;
    String lastButton = "";
    int buttonSeq, guiCloseSeq;
    final Map<String, String> guiInputs = new HashMap<>();
    /** Contents of GUI slots by element id; the open GUI's layout (for slot rules). */
    final Map<String, net.minecraft.world.item.ItemStack> guiSlots = new HashMap<>();
    org.sprautestudio.st_addon_spraute.gui.GuiDoc openGuiDoc;
    String lastSlot = "";
    /** Game events seen by this story and the data of the one being handled. */
    final EventLog events = new EventLog();
    /** Characters with a model from the project: character -> model key. */
    final Map<String, String> characterModels = new HashMap<>();

    /** Name of the cutscene being played, or "". */
    String cutscene = "";
    Map<String, Object> event = new java.util.HashMap<>();
    int slotSeq;

    public SprauteScriptInstance(ScriptEnvironment env, int id) {
        super(env, id);
        this.deployed = SprauteRuntime.get().deployed(id);
        if (deployed == null) throw new IllegalStateException("Spraute script #" + id + " is not deployed");
        Ast.Program program = deployed.program;
        for (Ast.LocationDecl l : program.locations) locationDecls.put(l.name, l);
        for (Ast.CharacterDecl c : program.characters) {
            Character<?> ch = createCharacter(c);
            if (ch != null) characters.put(c.name, ch);
            speechIcons.put(c.name, speechIcon(c));
            if (c.color != null) {
                try {
                    Integer col = org.sprautestudio.st_addon_spraute.runtime.Colors.parse(Eval.str(eval.eval(c.color)));
                    if (col != null) nameColors.put(c.name, col);
                } catch (RuntimeException e) {
                    SprauteRuntime.get().report(getId(), "Персонаж " + c.name + ": " + e.getMessage());
                }
            }
            try {
                if (c.portrait != null) portraits.put(c.name, Images.portrait(deployed.project, Eval.str(eval.eval(c.portrait))));
                else if (c.icon != null && !"head".equals(Eval.str(eval.eval(c.icon))))
                    portraits.put(c.name, Images.portrait(deployed.project, Eval.str(eval.eval(c.icon))));
            } catch (RuntimeException ignored) {
            }
        }
        for (Ast.VarDecl v : program.vars) {
            try {
                vars.put(v.name, eval.eval(v.init));
            } catch (RuntimeException e) {
                vars.put(v.name, 0.0);
                SprauteRuntime.get().report(id, "Не удалось вычислить начальное значение " + v.name + ": " + e.getMessage());
            }
        }
        ScriptPerformerBuilder builder = ScriptPerformerBuilder.builder(this);
        try {
            new Emitter(this).emitProgram(builder, program.body);
        } catch (RuntimeException e) {
            // a broken command must not take the whole story down: report and run nothing
            SprauteRuntime.get().report(id, "Сюжет не собран: " + e.getMessage());
            builder = ScriptPerformerBuilder.builder(this);
        }
        builder.build(this::registerMainPerformer);
    }

    private Character<?> createCharacter(Ast.CharacterDecl c) {
        try {
            if (c.existing != null) {
                String ref = Eval.str(eval.eval(c.existing));
                Character<?> found = Character.getById(ref);
                if (found == null) throw new SprauteRuntimeException("Персонаж '" + ref + "' не найден");
                return found;
            }
            String type = Eval.str(eval.eval(c.type));
            if (type.equals("spraute:npc") || type.startsWith("storytelling:")) {
                // the default body; stories written for Spraute's characters get it too
                if (type.startsWith("storytelling:")) SprauteRuntime.get().report(getId(), "Персонаж " + c.name + ": сущности " + type + " больше нет, показана стандартная модель (spraute:npc)");
                characterModels.put(c.name, org.sprautestudio.st_addon_spraute.runtime.models.EntityLibrary.BUILTIN);
                type = org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID + ":model";
            } else if (type.startsWith("spraute:")) {
                var et = org.sprautestudio.st_addon_spraute.runtime.models.EntityLibrary.find(deployed.project, type);
                if (et != null) {
                    characterModels.put(c.name, et.key());
                } else {
                    var model = org.sprautestudio.st_addon_spraute.runtime.models.ModelLibrary.find(deployed.project, type);
                    if (model == null) throw new SprauteRuntimeException("Сущность «" + type.substring(8) + "» не найдена (папка «Сущности» проекта)");
                    characterModels.put(c.name, model.key());
                }
                type = org.sprautestudio.st_addon_spraute.St_addon_spraute.MODID + ":model";
            }
            String display = c.displayName != null ? Eval.str(eval.eval(c.displayName)) : c.name;
            Double speed = c.speed != null ? Eval.num(eval.eval(c.speed)) : null;
            String skin = c.skin != null ? Eval.str(eval.eval(c.skin)) : null;
            String refId = "spraute_" + getId() + "_" + c.name;
            String entityType = type;
            String key = refId + "|" + type + "|" + display + "|" + speed + "|" + skin;
            return SprauteRuntime.get().characterCache.computeIfAbsent(key, k -> {
                Character.CharacterBuilder<?> b = Character.builder(refId, new ResourceLocation(entityType));
                b.name(display);
                if (speed != null) b.standardMoveSpeed(speed);
                if (skin != null) b.textured(new ResourceLocation(skin));
                return b.build();
            });
        } catch (RuntimeException e) {
            SprauteRuntime.get().report(getId(), "Персонаж " + c.name + ": " + e.getMessage());
            return null;
        }
    }

    /** A picture, or (no picture or icon "head") the character's own head drawn by the client. */
    private String speechIcon(Ast.CharacterDecl c) {
        try {
            if (c.icon != null) {
                String v = Eval.str(eval.eval(c.icon));
                if (!v.equals("head")) return Images.speechIcon(deployed.project, v);
            }
            return headIcon(c.name, c.headScale != null ? Eval.num(eval.eval(c.headScale)) : 1,
                    c.headX != null ? Eval.num(eval.eval(c.headX)) : 0, c.headY != null ? Eval.num(eval.eval(c.headY)) : 0,
                    c.headYaw != null ? Eval.num(eval.eval(c.headYaw)) : 0, c.headBone != null ? Eval.str(eval.eval(c.headBone)) : "auto");
        } catch (RuntimeException ignored) {
        }
        return "generic_man";
    }

    /** spraute/head/s<script>/<character>/<scale%>/<shift x %>/<shift y %>/<turn°> — drawn by the client. */
    String headIcon(String character, double scale, double dx, double dy, double yaw, String bone) {
        String b = bone == null || bone.isBlank() ? "auto" : bone.equals("auto") || bone.equals("all") ? bone : Images.slug(bone);
        return "spraute/head/s" + getId() + "/" + Images.slug(character) + "/" + Math.max(10, Math.round(scale * 100))
                + "/" + Math.round(dx) + "/" + Math.round(dy) + "/" + Math.round(yaw) + "/" + b;
    }

    boolean headSpeaker(String character) {
        return speechIcons.getOrDefault(character, "").startsWith("spraute/head/");
    }

    Location location(String name) {
        Ast.LocationDecl d = locationDecls.get(name);
        Location base = eval.position(d.pos);
        if (d.yaw == null && d.pitch == null && d.dimension == null) return base;
        float yaw = d.yaw != null ? (float) Eval.num(eval.eval(d.yaw)) : 0f;
        float pitch = d.pitch != null ? (float) Eval.num(eval.eval(d.pitch)) : 0f;
        ResourceKey<Level> dim = d.dimension != null
                ? ResourceKey.create(Registries.DIMENSION, new ResourceLocation(Eval.str(eval.eval(d.dimension))))
                : Level.OVERWORLD;
        return new Location(dim, base.x, base.y, base.z, yaw, pitch);
    }

    public SprauteRuntime.Deployed getDeployed() {
        return deployed;
    }

    // --------------------------------------------------------------- persistence

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = super.serializeNBT();
        CompoundTag data = new CompoundTag();
        CompoundTag varsTag = new CompoundTag();
        vars.forEach((k, v) -> {
            if (v instanceof Double d) varsTag.putDouble(k, d);
            else if (v instanceof Boolean b) varsTag.putBoolean(k, b);
            else if (v instanceof java.util.List<?> || v instanceof java.util.Map<?, ?> || v instanceof EntityRef) varsTag.putString(k, Values.toJson(v));
            else varsTag.putString(k, String.valueOf(v));
        });
        data.put("vars", varsTag);
        CompoundTag calls = new CompoundTag();
        callStacks.forEach((k, d) -> {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (String s : d) list.add(net.minecraft.nbt.StringTag.valueOf(s));
            calls.put(k, list);
        });
        data.put("calls", calls);
        data.putInt("answer", lastAnswer);
        CompoundTag slots = new CompoundTag();
        guiSlots.forEach((k, st) -> slots.put(k, st.save(new CompoundTag())));
        data.put("slots", slots);
        tag.put("spraute", data);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        super.deserializeNBT(tag);
        CompoundTag data = tag.getCompound("spraute");
        CompoundTag varsTag = data.getCompound("vars");
        for (String k : varsTag.getAllKeys()) {
            Tag t = varsTag.get(k);
            if (t == null) continue;
            switch (t.getId()) {
                case Tag.TAG_DOUBLE -> vars.put(k, varsTag.getDouble(k));
                case Tag.TAG_BYTE -> vars.put(k, varsTag.getBoolean(k));
                default -> {
                    String s = varsTag.getString(k);
                    vars.put(k, s.startsWith(Values.JSON_MARK) ? Values.fromJson(s) : s);
                }
            }
        }
        lastAnswer = data.getInt("answer");
        CompoundTag slots = data.getCompound("slots");
        guiSlots.clear();
        for (String k : slots.getAllKeys()) guiSlots.put(k, net.minecraft.world.item.ItemStack.of(slots.getCompound(k)));
        CompoundTag calls = data.getCompound("calls");
        callStacks.clear();
        for (String k : calls.getAllKeys()) {
            java.util.Deque<String> d = new java.util.ArrayDeque<>();
            net.minecraft.nbt.ListTag list = calls.getList(k, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) d.addLast(list.getString(i));
            callStacks.put(k, d);
        }
    }
}
