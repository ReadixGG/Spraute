package org.sprautestudio.spraute_engine.editor.visual;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scratch-style visual script: free-floating stacks of blocks on a canvas. The stack starting
 * with the "start" hat block is the story; it is compiled to .sst text.
 */
public final class VisualScript {
    public static final class Block {
        public String type;
        public Map<String, String> fields = new LinkedHashMap<>();
        /** Nested block lists of C-blocks (if/else/loops/choice answers). */
        public List<List<Block>> slots = new ArrayList<>();
        /** Answer texts of a choice block (one slot per answer). */
        public List<String> options = new ArrayList<>();
        /** Value blocks plugged into fields (field name -> reporter). */
        public Map<String, Block> inputs = new LinkedHashMap<>();
        /** Fields switched to "socket" mode with the ‹› toggle. */
        public List<String> sockets = new ArrayList<>();
        /** Folded to its first words (a C-block hides its insides). */
        public boolean collapsed;
        /** Fields spread over several lines instead of one long row. */
        public boolean multiline;

        // layout cache (not saved)
        transient int lx, ly, lw, lh;
        transient int headerH;
        transient int[] slotTop, slotH;
        transient int[] partX;
        transient int[] partW;
        /** Top of each part's line (from the block's top) and the line's height. */
        transient int[] partY, partRH;
        transient boolean error;
        transient String errorText;

        public BlockTypes.Type typeInfo() {
            return BlockTypes.get(type);
        }

        public String field(String name) {
            String v = fields.get(name);
            return v == null ? "" : v;
        }

        public static Block create(String type) {
            Block b = new Block();
            b.type = type;
            BlockTypes.Type t = BlockTypes.get(type);
            if (t != null) {
                for (BlockTypes.Field f : t.fields()) if (f.def() != null && !f.def().isEmpty()) b.fields.put(f.name(), f.def());
                if (type.equals("choice")) {
                    b.options.add("Да");
                    b.options.add("Нет");
                    b.slots.add(new ArrayList<>());
                    b.slots.add(new ArrayList<>());
                } else if (type.equals("switch")) {
                    b.options.add("\"yes\"");
                    b.options.add("\"no\"");
                    b.slots.add(new ArrayList<>());
                    b.slots.add(new ArrayList<>());
                    b.slots.add(new ArrayList<>()); // else
                } else {
                    for (int i = 0; i < t.slotLabels().size(); i++) b.slots.add(new ArrayList<>());
                }
            }
            return b;
        }

        public Block deepCopy() {
            Block b = new Block();
            b.type = type;
            b.fields = new LinkedHashMap<>(fields);
            b.options = new ArrayList<>(options);
            b.sockets = new ArrayList<>(sockets);
            b.collapsed = collapsed;
            b.multiline = multiline;
            for (Map.Entry<String, Block> e : inputs.entrySet()) b.inputs.put(e.getKey(), e.getValue().deepCopy());
            for (List<Block> slot : slots) {
                List<Block> copy = new ArrayList<>();
                for (Block c : slot) copy.add(c.deepCopy());
                b.slots.add(copy);
            }
            return b;
        }
    }

    public static final class Stack {
        public double x, y;
        public List<Block> blocks = new ArrayList<>();
        transient boolean dirty = true;
        transient int bw, bh;
    }

    public int version = 2;
    public List<Stack> stacks = new ArrayList<>();
    public double viewX = 0, viewY = 0, zoom = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static VisualScript fromJson(String json) {
        VisualScript s = null;
        try {
            s = GSON.fromJson(json, VisualScript.class);
        } catch (RuntimeException ignored) {
        }
        if (s == null) s = new VisualScript();
        if (s.stacks == null) s.stacks = new ArrayList<>();
        for (Stack st : s.stacks) {
            if (st.blocks == null) st.blocks = new ArrayList<>();
            st.dirty = true;
            fix(st.blocks);
        }
        if (s.zoom <= 0) s.zoom = 1;
        return s;
    }

    private static void fix(List<Block> blocks) {
        blocks.removeIf(b -> b == null || b.type == null || BlockTypes.get(b.type) == null);
        for (Block b : blocks) {
            if (b.fields == null) b.fields = new LinkedHashMap<>();
            if (b.options == null) b.options = new ArrayList<>();
            if (b.slots == null) b.slots = new ArrayList<>();
            if (b.sockets == null) b.sockets = new ArrayList<>();
            if (b.inputs == null) b.inputs = new LinkedHashMap<>();
            b.inputs.values().removeIf(r -> r == null || r.type == null || BlockTypes.get(r.type) == null);
            fix(new ArrayList<>(b.inputs.values()));
            int need = b.type.equals("choice") ? b.options.size() : b.type.equals("switch") ? b.options.size() + 1 : b.typeInfo().slotLabels().size();
            while (b.slots.size() < need) b.slots.add(new ArrayList<>());
            for (List<Block> slot : b.slots) fix(slot);
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public Stack mainStack() {
        for (Stack s : stacks) if (!s.blocks.isEmpty() && s.blocks.get(0).type.equals("start")) return s;
        return null;
    }

    /** New script with a start hat and a couple of example blocks. */
    public static VisualScript create(int id, String title) {
        VisualScript s = new VisualScript();
        Stack st = new Stack();
        st.x = 40;
        st.y = 40;
        Block start = Block.create("start");
        start.fields.put("id", String.valueOf(id));
        start.fields.put("title", title);
        st.blocks.add(start);
        Block ch = Block.create("character");
        st.blocks.add(ch);
        Block say = Block.create("cmd:say");
        say.fields.put("who", "alice");
        say.fields.put("text", "Привет!");
        st.blocks.add(say);
        s.stacks.add(st);
        return s;
    }
}
