package org.zonarstudio.spraute_engine.client.editor.scene;

import org.zonarstudio.spraute_engine.client.editor.texture.TextureEditorSession;

/**
 * Routes Ctrl+Z / Ctrl+Y by <em>what is being edited</em>, not by whichever
 * panel last happened to have focus.
 * <ul>
 *   <li>Texture editor — while that tab is focused.</li>
 *   <li>Visual graph — its own tab.</li>
 *   <li>Rest pose — {@code .geo.json} (bind pose of the model).</li>
 *   <li>Open timeline, not rest pose — {@code .animation.json} (clips / keys /
 *       animated properties). The scene JSON is not the animation.</li>
 *   <li>Otherwise — scene document.</li>
 * </ul>
 */
public final class EditorUndo {
    public enum Domain { SCENE, ANIM, GEO }

    private static Domain last = Domain.SCENE;

    private EditorUndo() {}

    public static void mark(Domain domain) {
        if (domain != null) last = domain;
    }

    public static boolean canUndo() {
        if (TextureEditorSession.claimsEditHotkeys()) {
            return TextureEditorSession.canUndoActive();
        }
        if (VisualScriptEditor.claimsEditHotkeys()) return VisualScriptEditor.canUndoActiveTab();
        if (AnimatorTimelineSession.isRestPoseEditing()) {
            return GeoBoneEditor.canUndo() || SceneEditorSession.canUndo();
        }
        if (AnimatorTimelineSession.isAuthoring()) {
            return AnimatorTimelineSession.canUndo() || SceneEditorSession.canUndo();
        }
        return GeoBoneEditor.canUndo() || AnimatorTimelineSession.canUndo() || SceneEditorSession.canUndo();
    }

    public static boolean canRedo() {
        if (TextureEditorSession.claimsEditHotkeys()) {
            return TextureEditorSession.canRedoActive();
        }
        if (VisualScriptEditor.claimsEditHotkeys()) return VisualScriptEditor.canRedoActiveTab();
        if (AnimatorTimelineSession.isRestPoseEditing()) {
            return GeoBoneEditor.canRedo() || SceneEditorSession.canRedo();
        }
        if (AnimatorTimelineSession.isAuthoring()) {
            return AnimatorTimelineSession.canRedo() || SceneEditorSession.canRedo();
        }
        return GeoBoneEditor.canRedo() || AnimatorTimelineSession.canRedo() || SceneEditorSession.canRedo();
    }

    public static void undo() {
        if (TextureEditorSession.claimsEditHotkeys()) {
            TextureEditorSession.undoActive();
            return;
        }
        if (VisualScriptEditor.claimsEditHotkeys()) {
            VisualScriptEditor.undoActiveTab();
            return;
        }
        if (AnimatorTimelineSession.isRestPoseEditing()) {
            if (GeoBoneEditor.canUndo()) {
                GeoBoneEditor.undo();
                return;
            }
            SceneEditorSession.undo();
            return;
        }
        if (AnimatorTimelineSession.isAuthoring()) {
            if (preferSceneWhileAuthoring(false)) {
                SceneEditorSession.undo();
                return;
            }
            if (AnimatorTimelineSession.canUndo()) {
                AnimatorTimelineSession.undo();
                return;
            }
            SceneEditorSession.undo();
            return;
        }
        if (last == Domain.GEO && GeoBoneEditor.canUndo()) {
            GeoBoneEditor.undo();
            return;
        }
        if (last == Domain.ANIM && AnimatorTimelineSession.canUndo()) {
            AnimatorTimelineSession.undo();
            return;
        }
        if (GeoBoneEditor.canUndo()) {
            GeoBoneEditor.undo();
            return;
        }
        SceneEditorSession.undo();
    }

    public static void redo() {
        if (TextureEditorSession.claimsEditHotkeys()) {
            TextureEditorSession.redoActive();
            return;
        }
        if (VisualScriptEditor.claimsEditHotkeys()) {
            VisualScriptEditor.redoActiveTab();
            return;
        }
        if (AnimatorTimelineSession.isRestPoseEditing()) {
            if (GeoBoneEditor.canRedo()) {
                GeoBoneEditor.redo();
                return;
            }
            SceneEditorSession.redo();
            return;
        }
        if (AnimatorTimelineSession.isAuthoring()) {
            if (preferSceneWhileAuthoring(true)) {
                SceneEditorSession.redo();
                return;
            }
            if (AnimatorTimelineSession.canRedo()) {
                AnimatorTimelineSession.redo();
                return;
            }
            SceneEditorSession.redo();
            return;
        }
        if (last == Domain.GEO && GeoBoneEditor.canRedo()) {
            GeoBoneEditor.redo();
            return;
        }
        if (last == Domain.ANIM && AnimatorTimelineSession.canRedo()) {
            AnimatorTimelineSession.redo();
            return;
        }
        if (GeoBoneEditor.canRedo()) {
            GeoBoneEditor.redo();
            return;
        }
        SceneEditorSession.redo();
    }

    /**
     * Timeline is open, but the last mutation was a real scene edit (NPC
     * transform, etc.) after leaving the bone / dope sheet. Then Ctrl+Z
     * should revert that scene step, not a previous key.
     */
    private static boolean preferSceneWhileAuthoring(boolean redo) {
        if (last != Domain.SCENE) return false;
        if (AnimatorTimelineSession.selectedBone() != null) return false;
        if (AnimatorTimelineSession.isTimelineFocused()) return false;
        return redo ? SceneEditorSession.canRedo() : SceneEditorSession.canUndo();
    }
}
