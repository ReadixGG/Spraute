package org.sprautestudio.st_addon_spraute.ui.widgets;

import org.lwjgl.glfw.GLFW;
import org.sprautestudio.st_addon_spraute.ui.Icon;
import org.sprautestudio.st_addon_spraute.ui.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Expandable tree built on {@link ListView}. */
public class TreeView extends ListView<TreeView.Row> {
    public static class Node {
        public String label;
        public Icon icon;
        public int iconColor = Theme.TEXT_DIM;
        public int textColor = Theme.TEXT;
        public String badge;
        public int badgeColor = Theme.TEXT_MUTED;
        public Object data;
        public boolean expanded = true;
        public final List<Node> children = new ArrayList<>();

        public Node(String label, Icon icon, Object data) {
            this.label = label;
            this.icon = icon;
            this.data = data;
        }

        public Node add(Node child) {
            children.add(child);
            return child;
        }

        public boolean isLeaf() {
            return children.isEmpty() && !(data instanceof Folder);
        }
    }

    /** Marker for nodes that are folders even when empty. */
    public interface Folder {}

    public record Row(Node node, int depth) {}

    private final List<Node> roots = new ArrayList<>();
    public Consumer<Node> onOpen;
    public NodeContext onNodeContext;

    public interface NodeContext {
        void open(Node node, int mx, int my);
    }

    public TreeView() {
        super(null);
        rowHeight = 17;
        painter = (c, row, x, y, w, h, sel, hov) -> {
            Node n = row.node();
            int ix = x + 4 + row.depth() * 10;
            if (!n.isLeaf()) c.icon(n.expanded ? Icon.CHEVRON_DOWN : Icon.CHEVRON_RIGHT, ix, y + (h - 9) / 2, Theme.TEXT_MUTED);
            ix += 11;
            if (n.icon != null) {
                c.icon(n.icon, ix, y + (h - 9) / 2, n.iconColor);
                ix += 13;
            }
            int badgeW = n.badge != null ? c.width(n.badge) + 6 : 0;
            c.textEllipsized(n.label, ix, y + (h - 8) / 2, w - (ix - x) - badgeW - 4, sel ? Theme.TEXT : n.textColor);
            if (n.badge != null) c.text(n.badge, x + w - badgeW, y + (h - 8) / 2, n.badgeColor);
        };
        onActivate = row -> {
            Node n = row.node();
            if (!n.isLeaf()) {
                n.expanded = !n.expanded;
                rebuild();
            } else if (onOpen != null) {
                onOpen.accept(n);
            }
        };
        onContext = (row, mx, my) -> {
            if (onNodeContext != null) onNodeContext.open(row == null ? null : row.node(), mx, my);
        };
    }

    public List<Node> roots() {
        return roots;
    }

    public void setRoots(List<Node> nodes) {
        roots.clear();
        roots.addAll(nodes);
        rebuild();
    }

    public void rebuild() {
        List<Row> rows = new ArrayList<>();
        for (Node n : roots) flatten(n, 0, rows);
        setItems(rows);
    }

    private void flatten(Node n, int depth, List<Row> out) {
        out.add(new Row(n, depth));
        if (n.expanded) for (Node c : n.children) flatten(c, depth + 1, out);
    }

    public Node selectedNode() {
        Row r = selectedItem();
        return r == null ? null : r.node();
    }

    @Override
    public boolean mousePressed(double mx, double my, int button) {
        boolean r = super.mousePressed(mx, my, button);
        Row row = selectedItem();
        if (button == 0 && row != null && !row.node().isLeaf()) {
            int chevronX = x + 4 + row.depth() * 10;
            if (mx >= chevronX - 2 && mx <= chevronX + 11) {
                row.node().expanded = !row.node().expanded;
                rebuild();
            }
        }
        return r;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        Row row = selectedItem();
        if (row != null && key == GLFW.GLFW_KEY_RIGHT && !row.node().isLeaf()) {
            row.node().expanded = true;
            rebuild();
            return true;
        }
        if (row != null && key == GLFW.GLFW_KEY_LEFT && !row.node().isLeaf()) {
            row.node().expanded = false;
            rebuild();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
