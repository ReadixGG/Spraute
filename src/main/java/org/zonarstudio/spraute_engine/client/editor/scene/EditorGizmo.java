package org.zonarstudio.spraute_engine.client.editor.scene;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.zonarstudio.spraute_engine.scene.SceneDocument;
import org.zonarstudio.spraute_engine.scene.SceneNode;
import org.zonarstudio.spraute_engine.scene.SceneNodeType;
import org.zonarstudio.spraute_engine.scene.SceneTransform;

import java.util.HashMap;
import java.util.Map;

/**
 * Viewport gizmo: mouse-ray math, axis picking and Move/Rotate/Scale drags.
 * Multi-selection MOVE/ROTATE/SCALE applies the same world delta to all movable selected nodes.
 */
public final class EditorGizmo {
    public enum Mode { SELECT, MOVE, ROTATE, SCALE }
    public enum Axis { X, Y, Z, UNIFORM }

    private static Mode mode = Mode.SELECT;
    private static boolean localSpace;

    private static Axis dragAxis;
    private static String dragNodeId;
    private static String dragBoneName;
    private static float[] dragBoneTransform;
    private static float[] dragBoneSize;
    private static boolean dragRestBone;
    private static Vec3 dragStartOrigin;
    private static SceneTransform dragStartWorld;
    /** Multi-select: start world transforms for every movable selected node (MOVE/ROTATE/SCALE). */
    private static final Map<String, SceneTransform> dragStartWorlds = new HashMap<>();
    private static double dragStartParam;
    private static double dragStartMouseX, dragStartMouseY;
    private static double dragScreenDirX, dragScreenDirY;
    private static double dragWorldUnitsPerPixel;
    private static boolean screenAxisDrag;
    private static Axis hoverAxis;
    /** Frozen player look/eyes for CAMERA gizmo drags (dedicated look jitter). */
    private static Vec3 dragCameraEyes;
    private static Float dragCameraLookYaw;
    private static Float dragCameraLookPitch;

    private static float viewportX, viewportY, viewportW, viewportH;
    private static boolean viewportActive;

    private EditorGizmo() {}

    public static Mode mode() {
        SceneNode selected = SceneEditorSession.selectedNode();
        if (selected != null && selected.type == SceneNodeType.CAMERA && mode == Mode.SCALE) {
            return Mode.MOVE;
        }
        if (selected != null && selected.type == SceneNodeType.ROUTE_POINT && mode == Mode.SCALE) {
            return Mode.MOVE;
        }
        return mode;
    }
    public static void setMode(Mode m) {
        SceneNode selected = SceneEditorSession.selectedNode();
        if (selected != null && selected.type == SceneNodeType.ROUTE_POINT && m == Mode.SCALE) {
            m = Mode.MOVE;
        }
        if (selected != null && selected.type == SceneNodeType.CAMERA && m == Mode.SCALE) {
            m = Mode.MOVE;
        }
        if (mode != m) {
            EditorMarqueeSelect.cancel();
        }
        mode = m;
    }
    public static boolean isLocalSpace() { return localSpace; }
    public static void setLocalSpace(boolean local) { localSpace = local; }
    public static Axis hoverAxis() { return hoverAxis; }
    public static Axis dragAxis() { return dragAxis; }
    public static boolean isDragging() { return dragAxis != null; }

    public static boolean gizmosEnabled() {
        return org.zonarstudio.spraute_engine.config.SprauteConfig.get().isEditorShowGizmos();
    }

    public static boolean playerCameraGizmosEnabled() {
        return org.zonarstudio.spraute_engine.config.SprauteConfig.get().isEditorShowPlayerCameraGizmos();
    }

    public static boolean overlaysEnabled() {
        return org.zonarstudio.spraute_engine.config.SprauteConfig.get().isEditorShowOverlays();
    }

    public static void setViewportRect(float x, float y, float w, float h) {
        viewportX = x;
        viewportY = y;
        viewportW = w;
        viewportH = h;
        viewportActive = w > 4f && h > 4f;
    }

    /** Mouse ray in world space from GUI-scaled screen coordinates. */
    public static Vec3[] mouseRay(double guiMouseX, double guiMouseY) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        double ndcX;
        double ndcY;
        double aspect;
        if (viewportActive && containsViewport(guiMouseX, guiMouseY)) {
            ndcX = 2.0 * (guiMouseX - viewportX) / viewportW - 1.0;
            ndcY = 1.0 - 2.0 * (guiMouseY - viewportY) / viewportH;
            aspect = viewportW / viewportH;
        } else {
            double guiW = mc.getWindow().getGuiScaledWidth();
            double guiH = mc.getWindow().getGuiScaledHeight();
            ndcX = 2.0 * guiMouseX / guiW - 1.0;
            ndcY = 1.0 - 2.0 * guiMouseY / guiH;
            aspect = guiW / guiH;
        }

        float yaw = camera.getYRot();
        float pitch = camera.getXRot();
        Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
        Vec3 right = Vec3.directionFromRotation(0, yaw + 90f);
        Vec3 up = forward.cross(right).scale(-1);

        double tanHalf = Math.tan(Math.toRadians(renderFovDegrees()) / 2.0);
        Vec3 dir = forward
                .add(right.scale(ndcX * tanHalf * aspect))
                .add(up.scale(ndcY * tanHalf))
                .normalize();
        return new Vec3[]{camera.getPosition(), dir};
    }

    /** FOV the frame was actually projected with — the option alone drifts under water, effects and camera nodes. */
    private static double renderFovDegrees() {
        return org.zonarstudio.spraute_engine.client.CameraHandler.renderFov();
    }

    public static boolean containsViewport(double guiMouseX, double guiMouseY) {
        return viewportActive
                && guiMouseX >= viewportX && guiMouseX <= viewportX + viewportW
                && guiMouseY >= viewportY && guiMouseY <= viewportY + viewportH;
    }

    /** True when the world gizmo may move this node (GUI nodes are 2D, world canvases are not). */
    public static boolean worldEditable(SceneNode node) {
        return node != null
                && (!node.type.isUi()
                || org.zonarstudio.spraute_engine.scene.SceneWorldCanvasRuntime.isWorldCanvas(node));
    }

    public static Vec3 gizmoOrigin(SceneDocument doc, SceneNode node) {
        String bone = AnimatorTimelineSession.selectedBone();
        if (bone != null && (AnimatorTimelineSession.appliesTo(node.id) || isRestBoneSelected(node))) {
            Vec3 boneOrigin = EditorBonePicker.boneWorldPosition(node, bone);
            if (boneOrigin != null) return boneOrigin;
        }
        SceneTransform world = doc.worldTransform(node.id);
        return world == null ? null : new Vec3(world.px, world.py, world.pz);
    }

    public static boolean isRestBoneSelected(SceneNode node) {
        return node != null
                && AnimatorTimelineSession.selectedBone() != null
                && AnimatorTimelineSession.isRestPoseEditing()
                && node.id.equals(SceneEditorSession.selectedNodeId());
    }

    public static float gizmoLength(Vec3 origin) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return (float) Math.max(0.8, cam.distanceTo(origin) * 0.18);
    }

    public static Vec3 axisDirection(Axis axis, SceneTransform world) {
        if (axis == Axis.UNIFORM) return new Vec3(1, 1, 1);
        if (!localSpace || world == null || axis == Axis.Y) {
            return switch (axis) {
                case X -> new Vec3(1, 0, 0);
                case Y -> new Vec3(0, 1, 0);
                case Z -> new Vec3(0, 0, 1);
                default -> new Vec3(1, 1, 1);
            };
        }
        double rad = Math.toRadians(-world.ry);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return axis == Axis.X ? new Vec3(cos, 0, -sin) : new Vec3(sin, 0, cos);
    }

    public static Axis updateHover(double guiMouseX, double guiMouseY) {
        hoverAxis = null;
        SceneDocument doc = SceneEditorSession.document();
        SceneNode node = SceneEditorSession.selectedNode();
        // GUI nodes are edited by the 2D canvas gizmo, never by the world gizmo — except a
        // world-space canvas, which is a real plane in the world and moves like any object.
        if (!gizmosEnabled()) return null;
        if (doc == null || node == null || !node.hasTransform() || !worldEditable(node)
                || mode == Mode.SELECT) return null;
        Vec3 origin = gizmoOrigin(doc, node);
        if (origin == null) return null;
        Vec3[] ray = mouseRay(guiMouseX, guiMouseY);
        float len = gizmoLength(origin);
        SceneTransform world = doc.worldTransform(node.id);

        double best = Double.MAX_VALUE;
        if (mode == Mode.MOVE) {
            // Center cube: free move + snap to surface (node transform only, not bones).
            boolean boneEdit = AnimatorTimelineSession.selectedBone() != null
                    && (AnimatorTimelineSession.appliesTo(node.id) || isRestBoneSelected(node));
            if (!boneEdit) {
                double centerDist = rayToPointDistance(ray[0], ray[1], origin);
                if (centerDist < len * 0.14) {
                    hoverAxis = Axis.UNIFORM;
                    return hoverAxis;
                }
            }
            for (Axis axis : new Axis[]{Axis.X, Axis.Y, Axis.Z}) {
                Vec3 dir = axisDirection(axis, world);
                double dist = rayToSegmentDistance(ray[0], ray[1], origin, origin.add(dir.scale(len)));
                if (dist < len * 0.14 && dist < best) {
                    best = dist;
                    hoverAxis = axis;
                }
            }
        } else if (mode == Mode.ROTATE) {
            for (Axis axis : new Axis[]{Axis.X, Axis.Y, Axis.Z}) {
                // Scene hitboxes are yaw-oriented prisms. Minecraft player
                // collision has no meaningful pitched/rolled box equivalent.
                if (node.type == SceneNodeType.HITBOX && axis != Axis.Y) continue;
                Vec3 normal = axisDirection(axis, world);
                double dist = rayToCircleDistance(ray[0], ray[1], origin, normal, len);
                if (dist < len * 0.14 && dist < best) {
                    best = dist;
                    hoverAxis = axis;
                }
            }
        } else if (mode == Mode.SCALE) {
            double centerDist = rayToPointDistance(ray[0], ray[1], origin);
            if (centerDist < len * 0.12 && centerDist < best) {
                best = centerDist;
                hoverAxis = Axis.UNIFORM;
            }
            for (Axis axis : new Axis[]{Axis.X, Axis.Y, Axis.Z}) {
                Vec3 dir = axisDirection(axis, world);
                Vec3 tip = origin.add(dir.scale(len));
                double dist = rayToPointDistance(ray[0], ray[1], tip);
                if (dist < len * 0.14 && dist < best) {
                    best = dist;
                    hoverAxis = axis;
                }
            }
        }
        return hoverAxis;
    }

    public static boolean beginDrag(double guiMouseX, double guiMouseY) {
        Axis axis = updateHover(guiMouseX, guiMouseY);
        SceneDocument doc = SceneEditorSession.document();
        SceneNode node = SceneEditorSession.selectedNode();
        if (axis == null || doc == null || node == null) return false;

        dragAxis = axis;
        dragNodeId = node.id;
        dragRestBone = isRestBoneSelected(node);
        dragBoneName = (AnimatorTimelineSession.appliesTo(node.id) || dragRestBone)
                ? AnimatorTimelineSession.selectedBone() : null;
        dragBoneTransform = dragBoneName == null ? null
                : dragRestBone
                ? GeoBoneEditor.readBone(node.model, dragBoneName)
                : AnimatorTimelineSession.sampleBoneTransform(dragBoneName);
        dragBoneSize = dragRestBone ? GeoBoneEditor.readBoneSize(node.model, dragBoneName) : null;
        if (dragBoneName != null && dragBoneTransform == null) {
            dragAxis = null;
            dragNodeId = null;
            dragRestBone = false;
            return false;
        }
        dragStartWorld = doc.worldTransform(node.id);
        dragStartOrigin = gizmoOrigin(doc, node);
        captureCameraDragBasis(doc, node);
        dragStartMouseX = guiMouseX;
        dragStartMouseY = guiMouseY;
        screenAxisDrag = mode == Mode.MOVE && dragAxis != Axis.UNIFORM && configureScreenAxisDrag(doc, node);
        dragStartParam = screenAxisDrag ? 0.0 : currentDragParam(doc, node, guiMouseX, guiMouseY);
        dragStartWorlds.clear();
        if (dragBoneName == null) {
            for (SceneNode n : SceneEditorSession.selectedMovableNodes()) {
                SceneTransform w = doc.worldTransform(n.id);
                if (w != null) dragStartWorlds.put(n.id, w.copy());
            }
            if (!dragStartWorlds.containsKey(node.id) && dragStartWorld != null) {
                dragStartWorlds.put(node.id, dragStartWorld.copy());
            }
            SceneEditorSession.pushUndo();
        } else if (!dragRestBone) {
            AnimatorTimelineSession.pushUndo();
        } else {
            GeoBoneEditor.pushUndo(node.model);
        }
        return true;
    }

    public static void updateDrag(double guiMouseX, double guiMouseY) {
        SceneDocument doc = SceneEditorSession.document();
        if (dragAxis == null || doc == null || dragNodeId == null) return;
        SceneNode node = doc.findNode(dragNodeId);
        if (node == null) return;

        double param = currentDragParam(doc, node, guiMouseX, guiMouseY);
        if (dragBoneName != null && dragBoneTransform != null) {
            if (dragRestBone) updateRestBoneDrag(node, param);
            else updateBoneDrag(node, param);
            return;
        }

        // Center handle (MOVE): place on world surface under the cursor.
        if (mode == Mode.MOVE && dragAxis == Axis.UNIFORM) {
            if (dragStartWorlds.size() > 1) {
                updateMultiSurfaceDrag(doc, node, guiMouseX, guiMouseY);
            } else {
                SceneTransform world = dragStartWorld.copy();
                applySurfaceMove(world, node, guiMouseX, guiMouseY);
                writeWorldLocal(doc, node, world);
                SceneEditorSession.markDirty();
                SceneEditorSession.syncLiveNode(node.id);
            }
            return;
        }

        // Multi-select: apply the same world delta to every movable selected node.
        if (dragStartWorlds.size() > 1 && mode != Mode.SELECT) {
            updateMultiDrag(doc, param);
            return;
        }

        SceneTransform world = dragStartWorld.copy();
        applyDragToWorld(world, param);
        writeWorldLocal(doc, node, world);
        SceneEditorSession.markDirty();
        SceneEditorSession.syncLiveNode(node.id);
    }

    private static void updateMultiSurfaceDrag(SceneDocument doc, SceneNode primary,
                                               double guiMouseX, double guiMouseY) {
        SceneTransform primaryStart = dragStartWorlds.getOrDefault(dragNodeId, dragStartWorld);
        if (primaryStart == null) return;
        SceneTransform placed = primaryStart.copy();
        applySurfaceMove(placed, primary, guiMouseX, guiMouseY);
        double dpx = placed.px - primaryStart.px;
        double dpy = placed.py - primaryStart.py;
        double dpz = placed.pz - primaryStart.pz;
        for (var entry : dragStartWorlds.entrySet()) {
            SceneNode n = doc.findNode(entry.getKey());
            if (n == null) continue;
            SceneTransform start = entry.getValue();
            SceneTransform world = start.copy();
            world.px = start.px + dpx;
            world.py = start.py + dpy;
            world.pz = start.pz + dpz;
            EditorGrid.snapTransformMove(world);
            writeWorldLocal(doc, n, world);
            SceneEditorSession.syncLiveNode(n.id);
        }
        if (!SceneEditorSession.isLiveRuntime()) {
            SceneEditorSession.markDirty();
        }
    }

    /**
     * Free move from the gizmo center: raycast the world and seat the node on the
     * hit surface (AABB touches the face from outside). Miss → camera-plane drag.
     */
    private static void applySurfaceMove(SceneTransform world, SceneNode node,
                                         double guiMouseX, double guiMouseY) {
        if (dragStartWorld == null) return;
        Vec3[] ray = mouseRay(guiMouseX, guiMouseY);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Vec3 end = ray[0].add(ray[1].scale(256));
            var entity = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
            BlockHitResult hit = mc.level.clip(new ClipContext(
                    ray[0], end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    entity));
            if (hit.getType() == HitResult.Type.BLOCK) {
                seatOnSurface(world, node, hit);
                EditorGrid.snapTransformMove(world);
                return;
            }
        }
        // Sky / miss: slide on the camera-facing plane through the start origin.
        Vec3 look = new Vec3(mc.gameRenderer.getMainCamera().getLookVector());
        Vec3 planeHit = rayPlaneIntersect(ray[0], ray[1], dragStartOrigin, look);
        if (planeHit != null) {
            world.px = planeHit.x;
            world.py = planeHit.y;
            world.pz = planeHit.z;
        }
        EditorGrid.snapTransformMove(world);
    }

    /** Moves {@code world} so the node's AABB rests against the block face at {@code hit}. */
    private static void seatOnSurface(SceneTransform world, SceneNode node, BlockHitResult hit) {
        AABB bounds = nodeBounds(node, dragStartWorld);
        double ox = dragStartWorld.px;
        double oy = dragStartWorld.py;
        double oz = dragStartWorld.pz;
        Vec3 loc = hit.getLocation();
        Direction face = hit.getDirection();
        world.px = loc.x;
        world.py = loc.y;
        world.pz = loc.z;
        switch (face) {
            case UP -> world.py = loc.y + (oy - bounds.minY);
            case DOWN -> world.py = loc.y - (bounds.maxY - oy);
            case EAST -> world.px = loc.x + (ox - bounds.minX);
            case WEST -> world.px = loc.x - (bounds.maxX - ox);
            case SOUTH -> world.pz = loc.z + (oz - bounds.minZ);
            case NORTH -> world.pz = loc.z - (bounds.maxZ - oz);
        }
    }

    private static void updateMultiDrag(SceneDocument doc, double param) {
        SceneTransform primaryStart = dragStartWorlds.getOrDefault(dragNodeId, dragStartWorld);
        if (primaryStart == null) return;

        double dpx = 0, dpy = 0, dpz = 0;
        double drx = 0, dry = 0, drz = 0;
        double fsx = 1, fsy = 1, fsz = 1;
        boolean move = mode == Mode.MOVE;
        boolean rotate = mode == Mode.ROTATE;
        boolean scale = mode == Mode.SCALE;

        if (move) {
            SceneTransform tmp = primaryStart.copy();
            applyDragToWorld(tmp, param);
            dpx = tmp.px - primaryStart.px;
            dpy = tmp.py - primaryStart.py;
            dpz = tmp.pz - primaryStart.pz;
        } else if (rotate) {
            SceneTransform tmp = primaryStart.copy();
            applyDragToWorld(tmp, param);
            drx = tmp.rx - primaryStart.rx;
            dry = tmp.ry - primaryStart.ry;
            drz = tmp.rz - primaryStart.rz;
        } else if (scale) {
            SceneTransform tmp = primaryStart.copy();
            applyDragToWorld(tmp, param);
            fsx = primaryStart.sx != 0 ? tmp.sx / primaryStart.sx : 1;
            fsy = primaryStart.sy != 0 ? tmp.sy / primaryStart.sy : 1;
            fsz = primaryStart.sz != 0 ? tmp.sz / primaryStart.sz : 1;
        }

        for (var entry : dragStartWorlds.entrySet()) {
            SceneNode n = doc.findNode(entry.getKey());
            if (n == null) continue;
            SceneTransform start = entry.getValue();
            SceneTransform world = start.copy();
            if (move) {
                world.px = start.px + dpx;
                world.py = start.py + dpy;
                world.pz = start.pz + dpz;
                EditorGrid.snapTransformMove(world);
            } else if (rotate) {
                world.rx = start.rx + drx;
                world.ry = start.ry + dry;
                world.rz = start.rz + drz;
                EditorGrid.snapTransformRotate(world);
            } else if (scale) {
                world.sx = EditorGrid.snapSize(start.sx * fsx);
                world.sy = EditorGrid.snapSize(start.sy * fsy);
                world.sz = EditorGrid.snapSize(start.sz * fsz);
            }
            writeWorldLocal(doc, n, world);
            SceneEditorSession.syncLiveNode(n.id);
        }
        if (!SceneEditorSession.isLiveRuntime()) {
            SceneEditorSession.markDirty();
        } else {
            // markDirty would only sync primary; we already synced each.
            // Still need dirty flag off for live.
        }
    }

    private static void applyDragToWorld(SceneTransform world, double param) {
        switch (mode) {
            case MOVE -> {
                Vec3 dir = axisDirection(dragAxis, dragStartWorld);
                double delta = param - dragStartParam;
                world.px += dir.x * delta;
                world.py += dir.y * delta;
                world.pz += dir.z * delta;
                EditorGrid.snapTransformMove(world);
            }
            case ROTATE -> {
                double deltaDeg = Math.toDegrees(param - dragStartParam);
                switch (dragAxis) {
                    case X -> world.rx = dragStartWorld.rx + deltaDeg;
                    case Y -> world.ry = dragStartWorld.ry + deltaDeg;
                    case Z -> world.rz = dragStartWorld.rz + deltaDeg;
                    default -> { }
                }
                EditorGrid.snapTransformRotate(world);
            }
            case SCALE -> {
                if (dragAxis == Axis.UNIFORM) {
                    double factor = dragStartParam > 1e-4 ? param / dragStartParam : 1.0;
                    factor = Math.max(0.01, factor);
                    world.sx = EditorGrid.snapSize(dragStartWorld.sx * factor);
                    world.sy = EditorGrid.snapSize(dragStartWorld.sy * factor);
                    world.sz = EditorGrid.snapSize(dragStartWorld.sz * factor);
                } else {
                    double factor = dragStartParam > 1e-4 ? param / dragStartParam : 1.0;
                    factor = Math.max(0.01, factor);
                    switch (dragAxis) {
                        case X -> world.sx = EditorGrid.snapSize(dragStartWorld.sx * factor);
                        case Y -> world.sy = EditorGrid.snapSize(dragStartWorld.sy * factor);
                        case Z -> world.sz = EditorGrid.snapSize(dragStartWorld.sz * factor);
                        default -> { }
                    }
                }
            }
            default -> { }
        }
    }

    private static void writeWorldLocal(SceneDocument doc, SceneNode node, SceneTransform world) {
        if (node.type == SceneNodeType.CAMERA) {
            if (dragCameraEyes != null && dragCameraLookYaw != null && dragCameraLookPitch != null) {
                org.zonarstudio.spraute_engine.scene.PlayerCameraPose.writeLocalFromWorld(
                        dragCameraEyes, dragCameraLookYaw, dragCameraLookPitch, node, world);
            } else {
                org.zonarstudio.spraute_engine.scene.PlayerCameraPose.writeLocalFromWorld(doc, node, world);
            }
            ClientScenePlayerNodes.pushAuthoring(doc, node);
            return;
        }
        SceneTransform parentWorld = doc.parentWorldTransform(node);
        node.transform = SceneTransform.toLocal(parentWorld, world);
        if (node.type == SceneNodeType.ROUTE_POINT && mode == Mode.ROTATE) {
            node.routeHasLook = true;
        }
    }

    public static void endDrag() {
        if (dragNodeId != null) {
            SceneDocument doc = SceneEditorSession.document();
            SceneNode node = doc == null ? null : doc.findNode(dragNodeId);
            if (node != null && node.type == SceneNodeType.ROUTE_POINT) {
                org.zonarstudio.spraute_engine.scene.SceneRouteOps.saveOwningRoute(doc, node);
            }
        }
        dragAxis = null;
        dragNodeId = null;
        dragBoneName = null;
        dragBoneTransform = null;
        dragBoneSize = null;
        dragRestBone = false;
        dragStartOrigin = null;
        dragStartWorld = null;
        dragStartWorlds.clear();
        screenAxisDrag = false;
        dragCameraEyes = null;
        dragCameraLookYaw = null;
        dragCameraLookPitch = null;
    }

    private static void captureCameraDragBasis(SceneDocument doc, SceneNode node) {
        dragCameraEyes = null;
        dragCameraLookYaw = null;
        dragCameraLookPitch = null;
        if (node == null || node.type != SceneNodeType.CAMERA) return;
        var player = org.zonarstudio.spraute_engine.scene.PlayerCameraPose.findPlayer(doc, node);
        if (player == null) return;
        var rest = org.zonarstudio.spraute_engine.scene.PlayerCameraPose.Rest.from(node);
        dragCameraEyes = player.getEyePosition(1f);
        dragCameraLookYaw = org.zonarstudio.spraute_engine.scene.PlayerCameraPose.lookBaseYaw(player, rest, 1f);
        dragCameraLookPitch = org.zonarstudio.spraute_engine.scene.PlayerCameraPose.lookBasePitch(player, rest, 1f);
    }

    /** Write gizmo edits as animation keys on the current frame. */
    private static void updateBoneDrag(SceneNode owner, double param) {
        double delta = param - dragStartParam;
        switch (mode) {
            case MOVE -> {
                // Animation positions use Bedrock model pixels (16 px = 1 block).
                double scale = switch (dragAxis) {
                    case X -> Math.max(0.0001, Math.abs(dragStartWorld.sx));
                    case Y -> Math.max(0.0001, Math.abs(dragStartWorld.sy));
                    case Z -> Math.max(0.0001, Math.abs(dragStartWorld.sz));
                    default -> 1.0;
                };
                float pixels = (float) (delta * 16.0 / scale);
                float x = dragBoneTransform[0];
                float y = dragBoneTransform[1];
                float z = dragBoneTransform[2];
                if (dragAxis == Axis.X) x += pixels;
                if (dragAxis == Axis.Y) y += pixels;
                if (dragAxis == Axis.Z) z += pixels;
                AnimatorTimelineSession.putBonePosition(dragBoneName, x, y, z);
            }
            case ROTATE -> {
                float degrees = (float) Math.toDegrees(delta);
                float x = dragBoneTransform[3];
                float y = dragBoneTransform[4];
                float z = dragBoneTransform[5];
                if (dragAxis == Axis.X) x += degrees;
                if (dragAxis == Axis.Y) y += degrees;
                if (dragAxis == Axis.Z) z += degrees;
                AnimatorTimelineSession.putBoneRotation(dragBoneName, x, y, z);
            }
            case SCALE -> {
                float factor = (float) Math.max(0.01,
                        dragStartParam > 1e-4 ? param / dragStartParam : 1.0);
                float x = dragBoneTransform[6];
                float y = dragBoneTransform[7];
                float z = dragBoneTransform[8];
                if (dragAxis == Axis.UNIFORM) {
                    x *= factor;
                    y *= factor;
                    z *= factor;
                } else if (dragAxis == Axis.X) x *= factor;
                else if (dragAxis == Axis.Y) y *= factor;
                else if (dragAxis == Axis.Z) z *= factor;
                AnimatorTimelineSession.putBoneScale(dragBoneName, x, y, z);
            }
            default -> {
            }
        }
    }

    /** Persist a rest-pose bone move/rotation directly into its .geo.json file. */
    private static void updateRestBoneDrag(SceneNode owner, double param) {
        if (owner.model == null || dragBoneTransform == null) return;
        double delta = param - dragStartParam;
        float[] pivot = {
                dragBoneTransform[0], dragBoneTransform[1], dragBoneTransform[2]
        };
        float[] rotation = {
                dragBoneTransform[3], dragBoneTransform[4], dragBoneTransform[5]
        };
        if (mode == Mode.MOVE) {
            double ownerScale = switch (dragAxis) {
                case X -> Math.max(0.0001, Math.abs(dragStartWorld.sx));
                case Y -> Math.max(0.0001, Math.abs(dragStartWorld.sy));
                case Z -> Math.max(0.0001, Math.abs(dragStartWorld.sz));
                default -> 1.0;
            };
            float pixels = (float) (delta * 16.0 / ownerScale);
            if (dragAxis == Axis.X) pivot[0] -= pixels;
            if (dragAxis == Axis.Y) pivot[1] += pixels;
            if (dragAxis == Axis.Z) pivot[2] += pixels;
        } else if (mode == Mode.ROTATE) {
            float degrees = (float) Math.toDegrees(delta);
            if (dragAxis == Axis.X) rotation[0] += degrees;
            if (dragAxis == Axis.Y) rotation[1] += degrees;
            if (dragAxis == Axis.Z) rotation[2] += degrees;
        } else if (mode == Mode.SCALE && dragBoneSize != null) {
            float factor = (float) Math.max(0.01,
                    dragStartParam > 1e-4 ? param / dragStartParam : 1.0);
            float[] base = dragBoneSize.clone();
            for (int i = 0; i < 3; i++) {
                if (base[i] < 0.0001f) base[i] = 8f;
            }
            float[] size = base.clone();
            if (dragAxis == Axis.UNIFORM) {
                for (int i = 0; i < 3; i++) {
                    size[i] = EditorGrid.snapModelPixels(base[i] * factor, true);
                }
            } else if (dragAxis == Axis.X) {
                size[0] = EditorGrid.snapModelPixels(base[0] * factor, true);
            } else if (dragAxis == Axis.Y) {
                size[1] = EditorGrid.snapModelPixels(base[1] * factor, true);
            } else if (dragAxis == Axis.Z) {
                size[2] = EditorGrid.snapModelPixels(base[2] * factor, true);
            }
            GeoBoneEditor.writeBoneSize(owner.model, dragBoneName, size);
            return;
        } else {
            return;
        }
        GeoBoneEditor.writeBone(owner.model, dragBoneName, pivot, rotation);
    }

    private static double currentDragParam(SceneDocument doc, SceneNode node, double guiMouseX, double guiMouseY) {
        if (mode == Mode.MOVE && screenAxisDrag) {
            double mouseDeltaX = guiMouseX - dragStartMouseX;
            double mouseDeltaY = guiMouseY - dragStartMouseY;
            return (mouseDeltaX * dragScreenDirX + mouseDeltaY * dragScreenDirY)
                    * dragWorldUnitsPerPixel;
        }
        Vec3 origin = dragStartOrigin != null ? dragStartOrigin : gizmoOrigin(doc, node);
        Vec3[] ray = mouseRay(guiMouseX, guiMouseY);
        SceneTransform world = dragStartWorld != null ? dragStartWorld : doc.worldTransform(node.id);
        if (dragAxis == Axis.UNIFORM) {
            Vec3 look = new Vec3(Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector());
            Vec3 hit = rayPlaneIntersect(ray[0], ray[1], origin, look);
            if (hit == null) return 1.0;
            return Math.max(0.01, hit.distanceTo(origin));
        }
        Vec3 axis = axisDirection(dragAxis != null ? dragAxis : hoverAxis, world);

        if (mode == Mode.ROTATE) {
            Vec3 hit = rayPlaneIntersect(ray[0], ray[1], origin, axis);
            if (hit == null) return 0;
            Vec3 v = hit.subtract(origin);
            Vec3 refA = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0).cross(axis).normalize();
            Vec3 refB = axis.cross(refA).normalize();
            return Math.atan2(v.dot(refB), v.dot(refA));
        }
        return closestParamOnLine(ray[0], ray[1], origin, axis);
    }

    /**
     * Projects one world unit of the selected axis into viewport pixels. Dragging
     * then follows the visible arrow exactly instead of relying on closest points
     * between two perspective 3D lines.
     */
    private static boolean configureScreenAxisDrag(SceneDocument doc, SceneNode node) {
        Vec3 origin = gizmoOrigin(doc, node);
        if (origin == null || dragStartWorld == null || dragAxis == null) return false;
        Vec3 axis = axisDirection(dragAxis, dragStartWorld).normalize();
        double[] a = projectToGui(origin);
        double[] b = projectToGui(origin.add(axis));
        if (a == null || b == null) return false;
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double pixelsPerUnit = Math.sqrt(dx * dx + dy * dy);
        if (pixelsPerUnit < 0.5) return false;
        dragScreenDirX = dx / pixelsPerUnit;
        dragScreenDirY = dy / pixelsPerUnit;
        dragWorldUnitsPerPixel = 1.0 / pixelsPerUnit;
        return true;
    }

    /** Project a world point into GUI-scaled viewport coordinates, or null if behind the camera. */
    public static double[] projectWorldToGui(Vec3 worldPoint) {
        return projectToGui(worldPoint);
    }

    private static double[] projectToGui(Vec3 worldPoint) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 forward = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot());
        Vec3 right = Vec3.directionFromRotation(0, camera.getYRot() + 90f);
        Vec3 up = forward.cross(right).scale(-1);
        Vec3 relative = worldPoint.subtract(camera.getPosition());
        double depth = relative.dot(forward);
        if (depth <= 0.01) return null;

        double aspect;
        double x;
        double y;
        double width;
        double height;
        if (viewportActive) {
            x = viewportX;
            y = viewportY;
            width = viewportW;
            height = viewportH;
            aspect = width / height;
        } else {
            x = 0;
            y = 0;
            width = mc.getWindow().getGuiScaledWidth();
            height = mc.getWindow().getGuiScaledHeight();
            aspect = width / height;
        }
        double tanHalf = Math.tan(Math.toRadians(renderFovDegrees()) / 2.0);
        double ndcX = relative.dot(right) / (depth * tanHalf * aspect);
        double ndcY = relative.dot(up) / (depth * tanHalf);
        return new double[]{
                x + (ndcX + 1.0) * 0.5 * width,
                y + (1.0 - ndcY) * 0.5 * height
        };
    }

    private static double closestParamOnLine(Vec3 rayOrigin, Vec3 rayDir, Vec3 lineOrigin, Vec3 lineDir) {
        Vec3 w0 = rayOrigin.subtract(lineOrigin);
        double b = rayDir.dot(lineDir);
        double d = rayDir.dot(w0);
        double e = lineDir.dot(w0);
        double denom = 1.0 - b * b;
        if (Math.abs(denom) < 1e-6) return e;
        return (e - b * d) / denom;
    }

    private static double rayToSegmentDistance(Vec3 rayOrigin, Vec3 rayDir, Vec3 segA, Vec3 segB) {
        Vec3 segDir = segB.subtract(segA);
        double segLen = segDir.length();
        if (segLen < 1e-6) return rayOrigin.subtract(segA).cross(rayDir).length();
        segDir = segDir.scale(1.0 / segLen);
        double t = Math.max(0, Math.min(segLen, closestParamOnLine(rayOrigin, rayDir, segA, segDir)));
        Vec3 pointOnSeg = segA.add(segDir.scale(t));
        double s = Math.max(0, pointOnSeg.subtract(rayOrigin).dot(rayDir));
        Vec3 pointOnRay = rayOrigin.add(rayDir.scale(s));
        return pointOnSeg.distanceTo(pointOnRay);
    }

    private static double rayToCircleDistance(Vec3 rayOrigin, Vec3 rayDir, Vec3 center, Vec3 normal, double radius) {
        Vec3 hit = rayPlaneIntersect(rayOrigin, rayDir, center, normal);
        if (hit == null) return Double.MAX_VALUE;
        Vec3 n = normal.normalize();
        Vec3 radial = hit.subtract(center).subtract(n.scale(hit.subtract(center).dot(n)));
        return Math.abs(radial.length() - radius);
    }

    private static double rayToPointDistance(Vec3 rayOrigin, Vec3 rayDir, Vec3 point) {
        double t = Math.max(0, point.subtract(rayOrigin).dot(rayDir));
        Vec3 closest = rayOrigin.add(rayDir.scale(t));
        return closest.distanceTo(point);
    }

    private static Vec3 rayPlaneIntersect(Vec3 rayOrigin, Vec3 rayDir, Vec3 planePoint, Vec3 planeNormal) {
        double denom = rayDir.dot(planeNormal);
        if (Math.abs(denom) < 1e-6) return null;
        double t = planePoint.subtract(rayOrigin).dot(planeNormal) / denom;
        if (t < 0) return null;
        return rayOrigin.add(rayDir.scale(t));
    }

    public static String pickNode(double guiMouseX, double guiMouseY) {
        SceneDocument doc = SceneEditorSession.document();
        if (doc == null) return null;
        Vec3[] ray = mouseRay(guiMouseX, guiMouseY);
        Vec3 end = ray[0].add(ray[1].scale(256));

        final String[] bestId = {null};
        final double[] bestDist = {Double.MAX_VALUE};
        doc.walk((node, parentWorld) -> {
            if ((node.type != SceneNodeType.NPC && node.type != SceneNodeType.MODEL
                    && node.type != SceneNodeType.HITBOX
                    && node.type != SceneNodeType.PARTICLE
                    && node.type != SceneNodeType.LIGHT
                    && node.type != SceneNodeType.AUDIO_SOURCE
                    && node.type != SceneNodeType.BILLBOARD) || !node.previewEnabled) return;
            // Locked nodes (or children of locked parents) are only selectable via Hierarchy.
            if (doc.isEffectivelyLocked(node)) return;
            SceneTransform world = SceneTransform.composeWorld(parentWorld, node.transform);
            AABB box = nodeBounds(node, world);
            var clip = box.clip(ray[0], end);
            if (clip.isPresent()) {
                double dist = clip.get().distanceTo(ray[0]);
                if (dist < bestDist[0]) {
                    bestDist[0] = dist;
                    bestId[0] = node.id;
                }
            }
        });
        return bestId[0];
    }

    public static AABB nodeBounds(SceneTransform world) {
        double hw = 0.5 * Math.max(0.2, Math.max(Math.abs(world.sx), Math.abs(world.sz)));
        double h = 2.0 * Math.max(0.1, Math.abs(world.sy));
        return new AABB(world.px - hw, world.py, world.pz - hw, world.px + hw, world.py + h, world.pz + hw);
    }

    public static AABB nodeBounds(SceneNode node, SceneTransform world) {
        if (node.type == SceneNodeType.CAMERA) {
            double r = 0.18;
            return new AABB(world.px - r, world.py - r, world.pz - r,
                    world.px + r, world.py + r, world.pz + r);
        }
        if (node.type == SceneNodeType.HITBOX) {
            return org.zonarstudio.spraute_engine.scene.SceneHitboxUtil.worldAabb(node, world);
        }
        if (node.type == SceneNodeType.BILLBOARD) {
            double hw = Math.max(0.1, node.billboardWidth * Math.abs(world.sx)) * 0.5;
            double h = Math.max(0.1, node.billboardHeight * Math.abs(world.sy));
            return new AABB(world.px - hw, world.py, world.pz - hw,
                    world.px + hw, world.py + h, world.pz + hw);
        }
        if (node.type == SceneNodeType.PARTICLE) {
            String shape = org.zonarstudio.spraute_engine.scene.SceneParticleEmitter.normalizeShape(node.particleShape);
            double r = switch (shape) {
                case "sphere", "spiral" -> Math.max(0.25, node.particleRadius);
                case "line" -> Math.max(0.25, Math.max(Math.abs(node.particleLineX),
                        Math.max(Math.abs(node.particleLineY), Math.abs(node.particleLineZ))) * 0.5);
                default -> 0.35;
            };
            double h = "spiral".equals(shape) ? Math.max(r, Math.abs(node.particleHeight)) : r * 2;
            return new AABB(world.px - r, world.py - 0.1, world.pz - r,
                    world.px + r, world.py + h, world.pz + r);
        }
        if (node.type == SceneNodeType.LIGHT) {
            double r = 0.35;
            return new AABB(world.px - r, world.py - r, world.pz - r,
                    world.px + r, world.py + r, world.pz + r);
        }
        if (node.type == SceneNodeType.AUDIO_SOURCE) {
            float[] e = org.zonarstudio.spraute_engine.scene.SceneAudioAttenuation.outerExtents(node, world);
            return new AABB(world.px - e[0], world.py - e[1], world.pz - e[2],
                    world.px + e[0], world.py + e[1], world.pz + e[2]);
        }
        if (node.type != SceneNodeType.NPC) return nodeBounds(world);
        double yaw = Math.toRadians(world.ry);
        double ox = node.hitboxOffsetX * world.sx;
        double oz = node.hitboxOffsetZ * world.sz;
        double centerX = world.px + ox * Math.cos(yaw) + oz * Math.sin(yaw);
        double centerZ = world.pz - ox * Math.sin(yaw) + oz * Math.cos(yaw);
        double bottomY = world.py + node.hitboxOffsetY * world.sy;
        double halfWidth = Math.max(0.01, node.hitboxWidth)
                * Math.max(Math.abs(world.sx), Math.abs(world.sz)) * 0.5;
        double height = Math.max(0.01, node.hitboxHeight) * Math.abs(world.sy);
        return new AABB(centerX - halfWidth, bottomY, centerZ - halfWidth,
                centerX + halfWidth, bottomY + height, centerZ + halfWidth);
    }
}
