package org.zonarstudio.spraute_engine.scene;

import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.cameraroute.CameraRoutePath;

import java.util.ArrayList;
import java.util.List;

/** Places ordered spatial children along a ROUTE without overwriting their local offsets. */
public final class SceneRouteLayout {
    public record Sample(Vec3 position, Vec3 tangent) {}

    private SceneRouteLayout() {}

    public static boolean isLayoutChild(SceneNode node) {
        return node != null && node.type != SceneNodeType.ROUTE_POINT && node.type.hasTransform()
                && !node.type.isUi();
    }

    public static List<SceneNode> children(SceneNode route) {
        if (route == null || route.type != SceneNodeType.ROUTE) return List.of();
        return route.children.stream().filter(SceneRouteLayout::isLayoutChild).toList();
    }

    public static SceneTransform slotTransform(SceneDocument document, SceneNode route, SceneNode child) {
        if (document == null || route == null || child == null || route.type != SceneNodeType.ROUTE) return null;
        List<SceneNode> layout = children(route);
        int index = layout.indexOf(child);
        if (index < 0) return null;

        SceneTransform inherited = document.parentWorldTransform(route);
        List<Vec3> path = new ArrayList<>(SceneRouteOps.samplePolyline(document, route, 48));
        if (route.routeClosed && path.size() > 1
                && path.get(0).distanceToSqr(path.get(path.size() - 1)) > 1e-10) {
            path.add(path.get(0));
        }
        if (path.isEmpty()) return inherited;
        if (path.size() == 1) {
            SceneTransform only = inherited == null ? new SceneTransform() : inherited.copy();
            only.px = path.get(0).x;
            only.py = path.get(0).y;
            only.pz = path.get(0).z;
            return only;
        }

        double spacing = Math.max(0d, route.routeLayoutSpacing);
        double length = length(path);
        double span = Math.max(0, layout.size() - 1) * spacing;
        double anchor = switch (normalizeAnchor(route.routeLayoutAnchor)) {
            case "center" -> (length - span) * 0.5;
            case "end" -> length - span;
            default -> 0d;
        };
        double progression = anchor + route.routeLayoutOffset + index * spacing;
        double distance = route.routeLayoutReverse ? length - progression : progression;
        String overflow = normalizeOverflow(route.routeLayoutOverflow);
        if ("loop".equals(overflow) && length > 1e-7) {
            distance = positiveModulo(distance, length);
        } else if (!"extend".equals(overflow)) {
            distance = Math.max(0d, Math.min(length, distance));
        }

        Sample sample = sample(path, distance, "extend".equals(overflow));
        Vec3 tangent = route.routeLayoutReverse ? sample.tangent.scale(-1) : sample.tangent;
        SceneTransform slot = inherited == null ? new SceneTransform() : inherited.copy();
        slot.px = sample.position.x;
        slot.py = sample.position.y;
        slot.pz = sample.position.z;
        if (route.routeLayoutAlignYaw || route.routeLayoutAlignPitch) {
            float[] look = CameraRoutePath.lookAtAngles(0, 0, 0, tangent.x, tangent.y, tangent.z);
            if (route.routeLayoutAlignYaw) slot.ry = look[0];
            if (route.routeLayoutAlignPitch) slot.rx = look[1];
        }
        return slot;
    }

    public static String normalizeAnchor(String value) {
        if (value == null) return "start";
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "center", "centre", "middle" -> "center";
            case "end", "finish" -> "end";
            default -> "start";
        };
    }

    public static String normalizeOverflow(String value) {
        if (value == null) return "clamp";
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "loop", "wrap", "repeat" -> "loop";
            case "extend", "continue" -> "extend";
            default -> "clamp";
        };
    }

    private static Sample sample(List<Vec3> path, double distance, boolean extend) {
        List<Double> lengths = new ArrayList<>(path.size() - 1);
        double total = 0d;
        for (int i = 1; i < path.size(); i++) {
            double segment = path.get(i).distanceTo(path.get(i - 1));
            lengths.add(segment);
            total += segment;
        }
        if (extend && distance < 0d) {
            Vec3 tangent = direction(path.get(0), path.get(1));
            return new Sample(path.get(0).add(tangent.scale(distance)), tangent);
        }
        if (extend && distance > total) {
            Vec3 tangent = direction(path.get(path.size() - 2), path.get(path.size() - 1));
            return new Sample(path.get(path.size() - 1).add(tangent.scale(distance - total)), tangent);
        }

        double cursor = 0d;
        for (int i = 0; i < lengths.size(); i++) {
            double segment = lengths.get(i);
            if (distance <= cursor + segment || i == lengths.size() - 1) {
                Vec3 from = path.get(i);
                Vec3 to = path.get(i + 1);
                Vec3 tangent = direction(from, to);
                double t = segment < 1e-7 ? 0d : Math.max(0d, Math.min(1d, (distance - cursor) / segment));
                return new Sample(from.add(to.subtract(from).scale(t)), tangent);
            }
            cursor += segment;
        }
        Vec3 last = path.get(path.size() - 1);
        return new Sample(last, direction(path.get(path.size() - 2), last));
    }

    private static double length(List<Vec3> path) {
        double total = 0d;
        for (int i = 1; i < path.size(); i++) total += path.get(i).distanceTo(path.get(i - 1));
        return total;
    }

    private static Vec3 direction(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        return delta.lengthSqr() < 1e-10 ? new Vec3(0, 0, 1) : delta.normalize();
    }

    private static double positiveModulo(double value, double divisor) {
        double result = value % divisor;
        return result < 0d ? result + divisor : result;
    }
}
