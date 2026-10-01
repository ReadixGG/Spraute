package org.zonarstudio.spraute_engine.scene;

import net.minecraft.world.entity.Entity;
import org.zonarstudio.spraute_engine.entity.SprauteNpcEntity;
import org.zonarstudio.spraute_engine.script.GeoEntityScriptUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps every inspector / {@link SceneNode} field to script property names
 * ({@code _node.flying = true}, {@code _node.uiText = "Hi"}, …).
 */
public final class SceneNodeScriptProps {
    private SceneNodeScriptProps() {}

    public static Object get(SceneNode node, String prop) {
        if (node == null || prop == null) return null;
        return switch (prop) {
            case "active" -> node.active;
            case "locked" -> node.locked;
            case "preview", "previewenabled", "preview_enabled" -> node.previewEnabled;

            case "flyingspeed", "flying_speed" -> node.flyingSpeed;
            case "flysmoothing", "fly_smoothing" -> node.flySmoothing;
            case "flying" -> node.flying;
            case "swimming" -> node.swimming;
            case "group" -> node.type == SceneNodeType.UI_DRAG ? nullToEmpty(node.uiDragGroup)
                    : node.type == SceneNodeType.UI_DROPTARGET ? nullToEmpty(node.uiDropGroup)
                    : nullToEmpty(node.group);
            case "playerskinoverlay", "player_skin_overlay", "skinoverlay" -> nullToEmpty(node.playerSkinOverlay);
            case "headbone", "head_bone" -> nullToEmpty(node.headBone);
            case "unlimitedheadlook", "unlimited_head_look" -> node.unlimitedHeadLook;
            case "nameyoffset", "name_y_offset" -> node.nameYOffset;
            case "showhitbox", "show_hitbox" -> node.showHitbox;

            case "flyidleanim", "fly_idle_anim" -> nullToEmpty(node.flyIdleAnimation);
            case "flywalkanim", "fly_walk_anim", "flyanim", "fly_anim" -> nullToEmpty(node.flyAnimation);
            case "swimidleanim", "swim_idle_anim" -> nullToEmpty(node.swimIdleAnimation);
            case "swimwalkanim", "swim_walk_anim", "swimanimation", "swim_anim" -> nullToEmpty(node.swimAnimation);
            case "deathanim", "death_anim" -> nullToEmpty(node.deathAnimation);

            case "attackrange", "attack_range" -> node.attackRange;
            case "attackcooldown", "attack_cooldown" -> node.attackCooldown;
            case "attackhitdelay", "attack_hit_delay" -> node.attackHitDelay;
            case "attackhand", "attack_hand" -> nullToEmpty(node.attackHand);
            case "attackanimations", "attack_animations", "attackanims" -> nullToEmpty(node.attackAnimations);
            case "drops" -> node.drops == null ? List.of() : node.drops;
            case "dropxp", "drop_xp" -> node.dropXp;

            case "billboardwidth", "billboard_width" -> node.billboardWidth;
            case "billboardheight", "billboard_height" -> node.billboardHeight;
            case "seethrough", "see_through", "billboardseethrough", "billboard_see_through" -> node.billboardSeeThrough;
            case "translucent", "transparent" -> node.translucent;
            case "opacity" -> node.opacity;
            case "lod", "lodenabled", "lod_enabled" -> node.lodEnabled;
            case "renderdistance", "render_distance" -> node.renderDistance;
            case "forcevisible", "force_visible" -> node.forceVisible;
            case "loadchunks", "load_chunks", "keepchunks", "keep_chunks", "keeploaded", "keep_loaded" -> node.loadChunks;
            case "nogravity", "no_gravity" -> node.noGravity;

            case "particletype", "particle_type", "particle" -> nullToEmpty(node.particleType);
            case "particleshape", "particle_shape" -> nullToEmpty(node.particleShape);
            case "particleemitmode", "particle_emit_mode", "emitmode", "emit_mode" -> nullToEmpty(node.particleEmitMode);
            case "particlecount", "particle_count" -> node.particleCount;
            case "particlespreadx", "particle_spread_x" -> node.particleSpreadX;
            case "particlespready", "particle_spread_y" -> node.particleSpreadY;
            case "particlespreadz", "particle_spread_z" -> node.particleSpreadZ;
            case "particlespeed", "particle_speed" -> node.particleSpeed;
            case "particlespeedspread", "particle_speed_spread" -> node.particleSpeedSpread;
            case "particlegravity", "particle_gravity" -> node.particleGravity;
            case "particlecollide", "particle_collide", "collideblocks", "collide_blocks",
                 "hasphysics", "has_physics" -> node.particleCollide;
            case "particledirx", "particle_dir_x" -> node.particleDirX;
            case "particlediry", "particle_dir_y" -> node.particleDirY;
            case "particledirz", "particle_dir_z" -> node.particleDirZ;
            case "particlecone", "particle_cone" -> node.particleCone;
            case "particlelifetime", "particle_lifetime" -> node.particleLifetime;
            case "particlefade", "particle_fade", "fade", "fadeout", "fade_out" -> node.particleFade;
            case "particleloop", "particle_loop" -> node.particleLoop;
            case "particleinterval", "particle_interval" -> node.particleInterval;
            case "particleradius", "particle_radius" -> node.particleRadius;
            case "particleheight", "particle_height" -> node.particleHeight;
            case "particlelinex", "particle_line_x" -> node.particleLineX;
            case "particleliney", "particle_line_y" -> node.particleLineY;
            case "particlelinez", "particle_line_z" -> node.particleLineZ;
            case "particles", "particlesystem", "particle_system", "effect" -> node.particles(true);

            case "animatorfile", "animator_file", "animfile" -> nullToEmpty(node.animatorFile);
            case "animatorclip", "animator_clip", "animclip" -> nullToEmpty(node.animatorClip);

            case "uiw", "ui_w", "uiwidth", "ui_width" -> node.type == SceneNodeType.CANVAS ? node.uiCanvasW : node.uiW;
            case "uih", "ui_h", "uiheight", "ui_height" -> node.type == SceneNodeType.CANVAS ? node.uiCanvasH : node.uiH;
            case "uicanvasw", "ui_canvas_w", "canvasw", "canvas_w" -> node.uiCanvasW;
            case "uicanvash", "ui_canvas_h", "canvash", "canvas_h" -> node.uiCanvasH;
            case "uicolor", "ui_color", "color" -> nullToEmpty(node.uiColor);
            case "uihovercolor", "ui_hover_color", "hovercolor" -> nullToEmpty(node.uiHoverColor);
            case "uigradient", "ui_gradient", "gradient" -> nullToEmpty(node.uiGradient);
            case "uigradientstart", "ui_gradient_start", "gradientstart" -> nullToEmpty(node.uiGradientStart);
            case "uigradientdir", "ui_gradient_dir", "gradientdir" -> nullToEmpty(node.uiGradientDir);
            case "uigradientangle", "ui_gradient_angle", "gradientangle" -> node.uiGradientAngle;
            case "uigradientstops", "ui_gradient_stops", "gradientstops" -> nullToEmpty(node.uiGradientStops);
            case "uiradius", "ui_radius", "radius" -> node.uiRadius;
            case "uiborder", "ui_border", "border" -> node.uiBorder;
            case "uibordercolor", "ui_border_color", "bordercolor" -> nullToEmpty(node.uiBorderColor);
            case "uibordergradient", "ui_border_gradient", "bordergradient" -> nullToEmpty(node.uiBorderGradient);
            case "uibordergradientangle", "ui_border_gradient_angle" -> node.uiBorderGradientAngle;
            case "uiouterborder", "ui_outer_border", "outerborder" -> node.uiOuterBorder;
            case "uiouterbordercolor", "ui_outer_border_color", "outerbordercolor" -> nullToEmpty(node.uiOuterBorderColor);
            case "uiouterbordergradient", "ui_outer_border_gradient", "outerbordergradient" -> nullToEmpty(node.uiOuterBorderGradient);
            case "uiouterbordergradientangle", "ui_outer_border_gradient_angle" -> node.uiOuterBorderGradientAngle;
            case "uishadow", "ui_shadow", "shadow" -> node.uiShadow;
            case "uitext", "ui_text", "text", "label" -> nullToEmpty(node.uiText);
            case "uitextscale", "ui_text_scale", "textscale" -> node.uiTextScale;
            case "uitextoffsetx", "ui_text_offset_x", "textoffsetx", "text_offset_x", "labeloffsetx", "label_offset_x" -> node.uiTextOffsetX;
            case "uitextoffsety", "ui_text_offset_y", "textoffsety", "text_offset_y", "labeloffsety", "label_offset_y" -> node.uiTextOffsetY;
            case "uitexteffects", "ui_text_effects", "texteffects", "effects" -> nullToEmpty(node.uiTextEffects);
            case "uitextgradient", "ui_text_gradient", "textgradient" -> nullToEmpty(node.uiTextGradient);
            case "uitextoutlinecolor", "ui_text_outline_color", "textoutlinecolor" -> nullToEmpty(node.uiTextOutlineColor);
            case "uitextglowcolor", "ui_text_glow_color", "textglowcolor" -> nullToEmpty(node.uiTextGlowColor);
            case "uitexteffectspeed", "ui_text_effect_speed", "texteffectspeed" -> node.uiTextEffectSpeed;
            case "uitexteffectamount", "ui_text_effect_amount", "texteffectamount" -> node.uiTextEffectAmount;
            case "uitextglowsize", "ui_text_glow_size", "textglowsize", "glowsize" -> node.uiTextGlowSize;
            case "uitextoutlinewidth", "ui_text_outline_width", "textoutlinewidth", "outlinewidth" -> node.uiTextOutlineWidth;
            case "uitextunderlinecolor", "ui_text_underline_color", "textunderlinecolor", "underlinecolor" -> nullToEmpty(node.uiTextUnderlineColor);
            case "uitextunderlinethickness", "ui_text_underline_thickness", "textunderlinethickness", "underlinethickness" -> node.uiTextUnderlineThickness;
            case "uitextunderlineoffset", "ui_text_underline_offset", "textunderlineoffset", "underlineoffset" -> node.uiTextUnderlineOffset;
            case "uitextshadowcolor", "ui_text_shadow_color", "textshadowcolor", "shadowcolor" -> nullToEmpty(node.uiTextShadowColor);
            case "uitextshadowoffsetx", "ui_text_shadow_offset_x", "textshadowoffsetx", "shadowoffsetx" -> node.uiTextShadowOffsetX;
            case "uitextshadowoffsety", "ui_text_shadow_offset_y", "textshadowoffsety", "shadowoffsety" -> node.uiTextShadowOffsetY;
            case "uitextshadowsize", "ui_text_shadow_size", "textshadowsize", "shadowtextsize" -> node.uiTextShadowSize;
            case "uialign", "ui_align", "align" -> nullToEmpty(node.uiAlign);
            case "uiwrap", "ui_wrap", "wrap" -> node.uiWrap;
            case "uiautowrap", "ui_auto_wrap", "autowrap" -> node.uiAutoWrap;
            case "uiwrapmode", "ui_wrap_mode", "wrapmode" -> nullToEmpty(node.uiWrapMode);
            case "uitexture", "ui_texture" -> nullToEmpty(node.uiTexture);
            case "svg", "uisvg", "ui_svg" ->
                    node.type == SceneNodeType.UI_SVG ? nullToEmpty(node.uiTexture) : null;
            case "uisvgstroke", "ui_svg_stroke", "stroke" ->
                    node.type == SceneNodeType.UI_SVG ? nullToEmpty(node.uiSvgStroke) : null;
            case "uisvgstrokewidth", "ui_svg_stroke_width", "strokewidth", "stroke_width" ->
                    node.type == SceneNodeType.UI_SVG ? node.uiSvgStrokeWidth : null;
            case "uisvgrecolor", "ui_svg_recolor", "recolor" ->
                    node.type == SceneNodeType.UI_SVG ? node.uiSvgRecolor : null;
            case "uiplaceholder", "ui_placeholder", "placeholder" -> nullToEmpty(node.uiPlaceholder);
            case "uitooltip", "ui_tooltip", "tooltip" -> nullToEmpty(node.uiTooltip);
            case "uimaxchars", "ui_max_chars", "maxchars" -> node.uiMaxChars;
            case "uipercentx", "ui_percent_x", "percentx" -> node.uiPercentX;
            case "uipercenty", "ui_percent_y", "percenty" -> node.uiPercentY;
            case "uipercentw", "ui_percent_w", "percentw" -> node.uiPercentW;
            case "uipercenth", "ui_percent_h", "percenth" -> node.uiPercentH;
            case "uicontenth", "ui_content_h", "contenth" -> node.uiContentH;
            case "uiscrollbar", "ui_scrollbar", "scrollbar" -> node.uiScrollbar;
            case "uiitem", "ui_item", "item" -> nullToEmpty(node.uiItem);
            case "uientitymodel", "ui_entity_model" -> nullToEmpty(node.uiEntityModel);
            case "uientitytexture", "ui_entity_texture" -> nullToEmpty(node.uiEntityTexture);
            case "uientityanim", "ui_entity_anim" -> nullToEmpty(node.uiEntityAnim);
            case "uientityscale", "ui_entity_scale" -> node.uiEntityScale;
            case "uientitymode", "ui_entity_mode" -> nullToEmpty(node.uiEntityMode);
            case "uientityref", "ui_entity_ref" -> nullToEmpty(node.uiEntityRef);
            case "uientityalways", "ui_entity_always" -> node.uiEntityAlways;
            case "uientityanchorx", "ui_entity_anchor_x" -> node.uiEntityAnchorX;
            case "uientityanchory", "ui_entity_anchor_y" -> node.uiEntityAnchorY;
            case "uientityfeetcrop", "ui_entity_feet_crop" -> node.uiEntityFeetCrop;
            case "uialpha", "ui_alpha", "alpha" -> node.uiAlpha;
            case "uisliceborders", "ui_slice_borders", "sliceborders" -> node.uiSliceBorders;
            case "uislicescale", "ui_slice_scale", "slicescale" -> node.uiSliceScale;
            case "uisrcu", "ui_src_u", "srcu" -> node.uiSrcU;
            case "uisrcv", "ui_src_v", "srcv" -> node.uiSrcV;
            case "uisrcw", "ui_src_w", "srcw" -> node.uiSrcW;
            case "uisrch", "ui_src_h", "srch" -> node.uiSrcH;
            case "src" -> java.util.List.of(node.uiSrcU, node.uiSrcV, node.uiSrcW, node.uiSrcH);
            case "uicropshape", "ui_crop_shape", "cropshape", "crop_shape", "srcshape", "src_shape" -> nullToEmpty(node.uiCropShape);
            case "uicropfeather", "ui_crop_feather", "cropfeather", "crop_feather", "feather", "edgeblur", "edge_blur" ->
                    node.uiCropFeather;
            case "uicropfeatherl", "ui_crop_feather_l", "featherl", "feather_l", "featherleft", "feather_left" ->
                    node.uiCropFeatherL;
            case "uicropfeathert", "ui_crop_feather_t", "feathert", "feather_t", "feathertop", "feather_top" ->
                    node.uiCropFeatherT;
            case "uicropfeatherr", "ui_crop_feather_r", "featherr", "feather_r", "featherright", "feather_right" ->
                    node.uiCropFeatherR;
            case "uicropfeatherb", "ui_crop_feather_b", "featherb", "feather_b", "featherbottom", "feather_bottom" ->
                    node.uiCropFeatherB;
            case "uicropfeathersmooth", "ui_crop_feather_smooth", "feathersmooth", "feather_smooth" ->
                    node.uiCropFeatherSmooth;
            case "uifill", "ui_fill", "fill", "fillmode", "fill_mode" -> nullToEmpty(node.uiFill);
            case "uifillamount", "ui_fill_amount", "fillamount", "fill_amount" -> node.uiFillAmount;
            case "uifillorigin", "ui_fill_origin", "fillorigin", "fill_origin" -> nullToEmpty(node.uiFillOrigin);
            case "uivideo", "ui_video", "video" ->
                    node.type == SceneNodeType.UI_VIDEO ? nullToEmpty(node.uiVideo) : null;
            case "source" -> {
                if (node.type == SceneNodeType.UI_VIDEO) yield nullToEmpty(node.uiVideo);
                if (node.type == SceneNodeType.UI_SVG) yield nullToEmpty(node.uiTexture);
                yield null;
            }
            case "uivideoenabled", "ui_video_enabled", "playerenabled", "player_enabled" ->
                    node.type == SceneNodeType.UI_VIDEO ? node.uiVideoEnabled : null;
            case "uivideoplaying", "ui_video_playing", "playing", "autoplay" ->
                    node.type == SceneNodeType.UI_VIDEO ? node.uiVideoPlaying : null;
            case "uivideoloop", "ui_video_loop", "videoloop" ->
                    node.type == SceneNodeType.UI_VIDEO ? node.uiVideoLoop : null;

            case "canvasoverlay", "canvas_overlay", "overlay" -> node.canvasOverlay;
            case "canvaslayer", "canvas_layer", "layer" -> node.canvasLayer;
            case "canvasautoshow", "canvas_auto_show", "autoshow", "auto_show" -> node.canvasAutoShow;
            case "canvascanclose", "canvas_can_close", "canclose", "can_close" -> node.canvasCanClose;
            case "canvasdim", "canvas_dim", "dim" -> node.canvasDim;
            case "canvasworldspace", "canvas_world_space", "worldspace", "world_space", "world3d" -> node.canvasWorldSpace;
            case "canvasworldwidth", "canvas_world_width", "worldwidth", "world_width" -> node.canvasWorldWidth;
            case "canvasworldheight", "canvas_world_height", "worldheight", "world_height" -> node.canvasWorldHeight;
            case "canvasworldorientation", "canvas_world_orientation", "orientation" ->
                    node.canvasWorldOrientation == null ? "billboard" : node.canvasWorldOrientation;
            case "canvasworldresolution", "canvas_world_resolution", "resolution" -> node.canvasWorldResolution;
            case "canvasworldmaxdistance", "canvas_world_max_distance", "maxdistance", "max_distance" -> node.canvasWorldMaxDistance;
            case "canvasworldrefresh", "canvas_world_refresh", "refresh" -> node.canvasWorldRefresh;

            case "gridcolumns", "grid_columns", "columns" -> node.gridColumns;
            case "gridcellw", "grid_cell_w", "cellw" -> node.gridCellW;
            case "gridcellh", "grid_cell_h", "cellh" -> node.gridCellH;
            case "gridgapx", "grid_gap_x", "gapx" -> node.gridGapX;
            case "gridgapy", "grid_gap_y", "gapy" -> node.gridGapY;
            case "gridstretchcells", "grid_stretch_cells", "stretchcells" -> node.gridStretchCells;
            case "gridmode", "grid_mode" -> node.gridMode;
            case "gridalign", "grid_align" -> node.gridAlign;
            case "gridflow", "grid_flow", "flow" -> node.gridFlow;
            case "gridhugcontent", "grid_hug_content", "hug", "hugcontent", "hug_content" -> node.gridHugContent;
            case "uichildalign", "ui_child_align", "childalign", "child_align", "alignchildren", "align_children" ->
                    node.uiChildAlign;
            case "uidragenabled", "ui_drag_enabled", "dragenabled", "drag_enabled" -> node.uiDragEnabled;
            case "uidragaxis", "ui_drag_axis", "dragaxis", "drag_axis", "axis" ->
                    node.type == SceneNodeType.UI_DRAG ? nullToEmpty(node.uiDragAxis) : null;
            case "uidraggroup", "ui_drag_group", "draggroup", "drag_group" -> nullToEmpty(node.uiDragGroup);
            case "uidragpayload", "ui_drag_payload", "dragpayload", "drag_payload", "payload" ->
                    node.type == SceneNodeType.UI_DRAG ? nullToEmpty(node.uiDragPayload) : null;
            case "uidragconstrain", "ui_drag_constrain", "dragconstrain", "drag_constrain", "constrain" ->
                    node.type == SceneNodeType.UI_DRAG ? node.uiDragConstrain : null;
            case "uidragrevert", "ui_drag_revert", "dragrevert", "drag_revert", "revert" ->
                    node.type == SceneNodeType.UI_DRAG ? node.uiDragRevert : null;
            case "uidragthreshold", "ui_drag_threshold", "dragthreshold", "drag_threshold", "threshold" ->
                    node.type == SceneNodeType.UI_DRAG ? node.uiDragThreshold : null;
            case "uidropenabled", "ui_drop_enabled", "dropenabled", "drop_enabled" -> node.uiDropEnabled;
            case "uidropgroup", "ui_drop_group", "dropgroup", "drop_group" -> nullToEmpty(node.uiDropGroup);

            case "blockdef", "block_def", "block" -> node.blockDef;
            case "itemdef", "item_def" -> node.itemDef;
            case "craftdef", "craft_def", "craft" -> node.craftDef;
            case "tabdef", "tab_def", "tab" -> node.tabDef;
            case "dropdef", "drop_def" -> node.dropDef;
            case "worlddef", "world_def", "world" -> node.worldDef;

            case "datadefaults", "data_defaults", "playerdatadefaults", "player_data_defaults" -> {
                SceneNode.ensurePlayerVarMaps(node);
                yield node.playerDataDefaults;
            }
            case "saveddatadefaults", "saved_data_defaults", "playersaveddefaults",
                    "player_saved_defaults" -> {
                SceneNode.ensurePlayerVarMaps(node);
                yield node.playerSavedDefaults;
            }

            default -> null;
        };
    }

    /** @return true if the property was recognized and applied to the node document. */
    public static boolean set(SceneNode node, String prop, Object value, Entity entity) {
        if (node == null || prop == null) return false;
        switch (prop) {
            case "active" -> { node.active = truthy(value); return true; }
            case "locked" -> { node.locked = truthy(value); return true; }
            case "datadefaults", "data_defaults", "playerdatadefaults", "player_data_defaults" -> {
                SceneNode.ensurePlayerVarMaps(node);
                node.playerDataDefaults.clear();
                if (value instanceof Map<?, ?> map) {
                    for (var e : map.entrySet()) {
                        if (e.getKey() != null) node.playerDataDefaults.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
                return true;
            }
            case "saveddatadefaults", "saved_data_defaults", "playersaveddefaults",
                    "player_saved_defaults" -> {
                SceneNode.ensurePlayerVarMaps(node);
                node.playerSavedDefaults.clear();
                if (value instanceof Map<?, ?> map) {
                    for (var e : map.entrySet()) {
                        if (e.getKey() != null) node.playerSavedDefaults.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
                return true;
            }
            case "preview", "previewenabled", "preview_enabled" -> {
                node.previewEnabled = truthy(value); return true;
            }

            case "invulnerable" -> {
                node.invulnerable = truthy(value);
                if (entity != null) entity.setInvulnerable(node.invulnerable);
                return true;
            }
            case "silent" -> {
                node.silent = truthy(value);
                if (entity != null) entity.setSilent(node.silent);
                return true;
            }
            case "glowing" -> {
                node.glowing = truthy(value);
                if (entity != null) entity.setGlowingTag(node.glowing);
                return true;
            }
            case "translucent", "transparent" -> {
                node.translucent = truthy(value);
                if (entity != null) GeoEntityScriptUtil.setTranslucent(entity, node.translucent);
                return true;
            }
            case "opacity" -> {
                if (!(value instanceof Number n)) return false;
                node.opacity = Math.max(0f, Math.min(1f, n.floatValue()));
                if (entity != null) GeoEntityScriptUtil.setOpacity(entity, node.opacity);
                return true;
            }
            case "lod", "lodenabled", "lod_enabled" -> {
                node.lodEnabled = !(value instanceof String s && "off".equalsIgnoreCase(s)) && truthy(value);
                if (entity != null) GeoEntityScriptUtil.setLodEnabled(entity, node.lodEnabled);
                return true;
            }
            case "renderdistance", "render_distance" -> {
                if (!(value instanceof Number n)) return false;
                node.renderDistance = Math.max(0f, n.floatValue());
                if (entity != null) GeoEntityScriptUtil.setRenderDistance(entity, node.renderDistance);
                return true;
            }
            case "forcevisible", "force_visible" -> {
                node.forceVisible = truthy(value);
                if (entity != null) GeoEntityScriptUtil.setForceVisible(entity, node.forceVisible);
                return true;
            }
            case "loadchunks", "load_chunks", "keepchunks", "keep_chunks", "keeploaded", "keep_loaded" -> {
                node.loadChunks = truthy(value);
                if (entity instanceof SprauteNpcEntity npc) npc.setLoadChunks(node.loadChunks);
                return true;
            }
            case "nogravity", "no_gravity" -> {
                node.noGravity = truthy(value);
                if (entity != null) entity.setNoGravity(node.noGravity);
                return true;
            }
            case "flyingspeed", "flying_speed" -> {
                if (!(value instanceof Number n)) return false;
                node.flyingSpeed = n.floatValue();
                if (entity instanceof SprauteNpcEntity npc) {
                    var attr = npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED);
                    if (attr != null) attr.setBaseValue(n.doubleValue());
                }
                return true;
            }
            case "flysmoothing", "fly_smoothing" -> {
                if (!(value instanceof Number n)) return false;
                node.flySmoothing = n.floatValue();
                if (entity instanceof SprauteNpcEntity npc) npc.setFlySmoothing(node.flySmoothing);
                return true;
            }
            case "flying" -> {
                node.flying = truthy(value);
                if (entity instanceof SprauteNpcEntity npc) npc.setFlying(node.flying);
                return true;
            }
            case "swimming" -> {
                node.swimming = truthy(value);
                if (entity instanceof SprauteNpcEntity npc) npc.setSwimmingScript(node.swimming);
                return true;
            }
            case "group" -> {
                if (node.type == SceneNodeType.UI_DRAG) {
                    node.uiDragGroup = str(value);
                    return true;
                }
                if (node.type == SceneNodeType.UI_DROPTARGET) {
                    node.uiDropGroup = str(value);
                    return true;
                }
                node.group = str(value);
                if (entity instanceof SprauteNpcEntity npc) npc.setGroupName(node.group);
                return true;
            }
            case "playerskinoverlay", "player_skin_overlay", "skinoverlay" -> {
                node.playerSkinOverlay = str(value);
                if (entity instanceof SprauteNpcEntity npc) {
                    if (node.playerSkinOverlay.isBlank()) npc.clearPlayerSkinOverlay();
                    else {
                        try {
                            npc.setPlayerSkinOverlay(java.util.UUID.fromString(node.playerSkinOverlay.trim()));
                        } catch (IllegalArgumentException e) {
                            npc.clearPlayerSkinOverlay();
                        }
                    }
                }
                return true;
            }
            case "headbone", "head_bone" -> {
                node.headBone = str(value);
                if (entity instanceof SprauteNpcEntity npc) npc.setHeadBone(node.headBone);
                return true;
            }
            case "unlimitedheadlook", "unlimited_head_look" -> {
                node.unlimitedHeadLook = truthy(value);
                if (entity instanceof SprauteNpcEntity npc) npc.setHeadLookUnlimited(node.unlimitedHeadLook);
                return true;
            }
            case "nameyoffset", "name_y_offset" -> {
                if (!(value instanceof Number n)) return false;
                node.nameYOffset = n.floatValue();
                if (entity instanceof SprauteNpcEntity npc) npc.setNameYOffset(node.nameYOffset);
                return true;
            }
            case "showhitbox", "show_hitbox" -> {
                node.showHitbox = truthy(value);
                if (entity instanceof SprauteNpcEntity npc) npc.setShowHitboxDebug(node.showHitbox);
                return true;
            }

            case "flyidleanim", "fly_idle_anim" -> {
                node.flyIdleAnimation = str(value);
                if (entity instanceof SprauteNpcEntity npc) npc.setFlyIdleAnim(node.flyIdleAnimation);
                return true;
            }
            case "flywalkanim", "fly_walk_anim", "flyanim", "fly_anim" -> {
                node.flyAnimation = str(value);
                if (entity instanceof SprauteNpcEntity npc) npc.setFlyWalkAnim(node.flyAnimation);
                return true;
            }
            case "swimidleanim", "swim_idle_anim" -> {
                node.swimIdleAnimation = str(value);
                if (entity instanceof SprauteNpcEntity npc) npc.setSwimIdleAnim(node.swimIdleAnimation);
                return true;
            }
            case "swimwalkanim", "swim_walk_anim", "swimanimation", "swim_anim" -> {
                node.swimAnimation = str(value);
                if (entity instanceof SprauteNpcEntity npc) npc.setSwimWalkAnim(node.swimAnimation);
                return true;
            }
            case "deathanim", "death_anim" -> {
                node.deathAnimation = str(value);
                if (entity instanceof SprauteNpcEntity npc) npc.setDeathAnim(node.deathAnimation);
                return true;
            }

            case "attackrange", "attack_range" -> {
                if (!(value instanceof Number n)) return false;
                node.attackRange = n.floatValue();
                if (entity instanceof SprauteNpcEntity npc) npc.setAttackRange(node.attackRange);
                return true;
            }
            case "attackcooldown", "attack_cooldown" -> {
                if (!(value instanceof Number n)) return false;
                node.attackCooldown = n.intValue();
                if (entity instanceof SprauteNpcEntity npc) npc.setAttackCooldown(node.attackCooldown);
                return true;
            }
            case "attackhitdelay", "attack_hit_delay" -> {
                if (!(value instanceof Number n)) return false;
                node.attackHitDelay = n.intValue();
                if (entity instanceof SprauteNpcEntity npc) npc.setAttackHitDelay(node.attackHitDelay);
                return true;
            }
            case "attackhand", "attack_hand" -> {
                node.attackHand = str(value);
                if (entity instanceof SprauteNpcEntity npc) npc.setAttackHand(node.attackHand);
                return true;
            }
            case "attackanimations", "attack_animations", "attackanims" -> {
                node.attackAnimations = str(value);
                if (entity instanceof SprauteNpcEntity npc) {
                    npc.setAttackAnims(java.util.Arrays.stream(node.attackAnimations.split(","))
                            .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new));
                }
                return true;
            }
            case "drops" -> {
                node.drops = copyDropList(value);
                return true;
            }
            case "dropxp", "drop_xp" -> {
                if (!(value instanceof Number n)) return false;
                node.dropXp = Math.max(0, n.intValue());
                if (entity instanceof SprauteNpcEntity npc) npc.setDropXp(node.dropXp);
                return true;
            }

            case "billboardwidth", "billboard_width" -> {
                if (!(value instanceof Number n)) return false;
                node.billboardWidth = n.floatValue();
                return true;
            }
            case "billboardheight", "billboard_height" -> {
                if (!(value instanceof Number n)) return false;
                node.billboardHeight = n.floatValue();
                return true;
            }
            case "seethrough", "see_through", "billboardseethrough", "billboard_see_through" -> {
                node.billboardSeeThrough = truthy(value);
                return true;
            }

            case "particletype", "particle_type", "particle" -> { node.particleType = str(value); return true; }
            case "particleshape", "particle_shape" -> { node.particleShape = str(value); return true; }
            case "particleemitmode", "particle_emit_mode", "emitmode", "emit_mode" -> {
                node.particleEmitMode = str(value); return true;
            }
            case "particlecount", "particle_count" -> {
                if (!(value instanceof Number n)) return false;
                node.particleCount = n.intValue(); return true;
            }
            case "particlespreadx", "particle_spread_x" -> {
                if (!(value instanceof Number n)) return false;
                node.particleSpreadX = n.floatValue(); return true;
            }
            case "particlespready", "particle_spread_y" -> {
                if (!(value instanceof Number n)) return false;
                node.particleSpreadY = n.floatValue(); return true;
            }
            case "particlespreadz", "particle_spread_z" -> {
                if (!(value instanceof Number n)) return false;
                node.particleSpreadZ = n.floatValue(); return true;
            }
            case "particlespeed", "particle_speed" -> {
                if (!(value instanceof Number n)) return false;
                node.particleSpeed = n.floatValue(); return true;
            }
            case "particlespeedspread", "particle_speed_spread" -> {
                if (!(value instanceof Number n)) return false;
                node.particleSpeedSpread = n.floatValue(); return true;
            }
            case "particlegravity", "particle_gravity" -> {
                if (!(value instanceof Number n)) return false;
                node.particleGravity = n.floatValue(); return true;
            }
            case "particlecollide", "particle_collide", "collideblocks", "collide_blocks",
                 "hasphysics", "has_physics" -> {
                node.particleCollide = truthy(value); return true;
            }
            case "particledirx", "particle_dir_x" -> {
                if (!(value instanceof Number n)) return false;
                node.particleDirX = n.floatValue(); return true;
            }
            case "particlediry", "particle_dir_y" -> {
                if (!(value instanceof Number n)) return false;
                node.particleDirY = n.floatValue(); return true;
            }
            case "particledirz", "particle_dir_z" -> {
                if (!(value instanceof Number n)) return false;
                node.particleDirZ = n.floatValue(); return true;
            }
            case "particlecone", "particle_cone" -> {
                if (!(value instanceof Number n)) return false;
                node.particleCone = n.floatValue(); return true;
            }
            case "particlelifetime", "particle_lifetime" -> {
                if (!(value instanceof Number n)) return false;
                node.particleLifetime = n.intValue(); return true;
            }
            case "particlefade", "particle_fade", "fade", "fadeout", "fade_out" -> {
                if (!(value instanceof Number n)) return false;
                node.particleFade = Math.max(0, n.intValue()); return true;
            }
            case "particleloop", "particle_loop" -> { node.particleLoop = truthy(value); return true; }
            case "particleinterval", "particle_interval" -> {
                if (!(value instanceof Number n)) return false;
                node.particleInterval = n.intValue(); return true;
            }
            case "particleradius", "particle_radius" -> {
                if (!(value instanceof Number n)) return false;
                node.particleRadius = n.floatValue(); return true;
            }
            case "particleheight", "particle_height" -> {
                if (!(value instanceof Number n)) return false;
                node.particleHeight = n.floatValue(); return true;
            }
            case "particlelinex", "particle_line_x" -> {
                if (!(value instanceof Number n)) return false;
                node.particleLineX = n.floatValue(); return true;
            }
            case "particleliney", "particle_line_y" -> {
                if (!(value instanceof Number n)) return false;
                node.particleLineY = n.floatValue(); return true;
            }
            case "particlelinez", "particle_line_z" -> {
                if (!(value instanceof Number n)) return false;
                node.particleLineZ = n.floatValue(); return true;
            }
            case "particles", "particlesystem", "particle_system", "effect" -> {
                if (!(value instanceof org.zonarstudio.spraute_engine.particle.ParticleSystemSpec spec)) return false;
                node.particleSystem = spec.copy();
                return true;
            }

            case "animatorfile", "animator_file", "animfile" -> { node.animatorFile = str(value); return true; }
            case "animatorclip", "animator_clip", "animclip" -> { node.animatorClip = str(value); return true; }

            case "uiw", "ui_w", "uiwidth", "ui_width" -> {
                if (!(value instanceof Number n)) return false;
                if (node.type == SceneNodeType.CANVAS) node.uiCanvasW = n.intValue();
                else node.uiW = n.intValue();
                return true;
            }
            case "uih", "ui_h", "uiheight", "ui_height" -> {
                if (!(value instanceof Number n)) return false;
                if (node.type == SceneNodeType.CANVAS) node.uiCanvasH = n.intValue();
                else node.uiH = n.intValue();
                return true;
            }
            case "uicanvasw", "ui_canvas_w", "canvasw", "canvas_w" -> {
                if (!(value instanceof Number n)) return false;
                node.uiCanvasW = n.intValue(); return true;
            }
            case "uicanvash", "ui_canvas_h", "canvash", "canvas_h" -> {
                if (!(value instanceof Number n)) return false;
                node.uiCanvasH = n.intValue(); return true;
            }
            case "uicolor", "ui_color", "color" -> { node.uiColor = str(value); return true; }
            case "uihovercolor", "ui_hover_color", "hovercolor" -> { node.uiHoverColor = str(value); return true; }
            case "uigradient", "ui_gradient", "gradient" -> { node.uiGradient = str(value); return true; }
            case "uigradientstart", "ui_gradient_start", "gradientstart" -> {
                node.uiGradientStart = str(value); return true;
            }
            case "uigradientdir", "ui_gradient_dir", "gradientdir" -> {
                node.uiGradientDir = str(value); return true;
            }
            case "uigradientangle", "ui_gradient_angle", "gradientangle" -> {
                if (!(value instanceof Number n)) return false;
                node.uiGradientAngle = n.floatValue(); return true;
            }
            case "uigradientstops", "ui_gradient_stops", "gradientstops" -> {
                node.uiGradientStops = str(value); return true;
            }
            case "uiradius", "ui_radius", "radius" -> {
                if (!(value instanceof Number n)) return false;
                node.uiRadius = n.floatValue(); return true;
            }
            case "uiborder", "ui_border", "border" -> {
                if (!(value instanceof Number n)) return false;
                node.uiBorder = n.floatValue(); return true;
            }
            case "uibordercolor", "ui_border_color", "bordercolor" -> {
                node.uiBorderColor = str(value); return true;
            }
            case "uibordergradient", "ui_border_gradient", "bordergradient" -> {
                node.uiBorderGradient = str(value); return true;
            }
            case "uibordergradientangle", "ui_border_gradient_angle" -> {
                if (!(value instanceof Number n)) return false;
                node.uiBorderGradientAngle = n.floatValue(); return true;
            }
            case "uiouterborder", "ui_outer_border", "outerborder" -> {
                if (!(value instanceof Number n)) return false;
                node.uiOuterBorder = n.floatValue(); return true;
            }
            case "uiouterbordercolor", "ui_outer_border_color", "outerbordercolor" -> {
                node.uiOuterBorderColor = str(value); return true;
            }
            case "uiouterbordergradient", "ui_outer_border_gradient", "outerbordergradient" -> {
                node.uiOuterBorderGradient = str(value); return true;
            }
            case "uiouterbordergradientangle", "ui_outer_border_gradient_angle" -> {
                if (!(value instanceof Number n)) return false;
                node.uiOuterBorderGradientAngle = n.floatValue(); return true;
            }
            case "uishadow", "ui_shadow", "shadow" -> {
                if (!(value instanceof Number n)) return false;
                node.uiShadow = n.floatValue(); return true;
            }
            case "uitext", "ui_text", "text", "label" -> { node.uiText = str(value); return true; }
            case "uitextscale", "ui_text_scale", "textscale" -> {
                if (!(value instanceof Number n)) return false;
                node.uiTextScale = n.floatValue(); return true;
            }
            case "uitextoffsetx", "ui_text_offset_x", "textoffsetx", "text_offset_x", "labeloffsetx", "label_offset_x" -> {
                if (!(value instanceof Number n)) return false;
                node.uiTextOffsetX = n.floatValue(); return true;
            }
            case "uitextoffsety", "ui_text_offset_y", "textoffsety", "text_offset_y", "labeloffsety", "label_offset_y" -> {
                if (!(value instanceof Number n)) return false;
                node.uiTextOffsetY = n.floatValue(); return true;
            }
            case "uitexteffects", "ui_text_effects", "texteffects", "effects" -> {
                node.uiTextEffects = str(value); return true;
            }
            case "uitextgradient", "ui_text_gradient", "textgradient" -> {
                node.uiTextGradient = str(value); return true;
            }
            case "uitextoutlinecolor", "ui_text_outline_color", "textoutlinecolor" -> {
                node.uiTextOutlineColor = str(value); return true;
            }
            case "uitextglowcolor", "ui_text_glow_color", "textglowcolor" -> {
                node.uiTextGlowColor = str(value); return true;
            }
            case "uitexteffectspeed", "ui_text_effect_speed", "texteffectspeed" -> {
                if (!(value instanceof Number n)) return false;
                node.uiTextEffectSpeed = n.floatValue(); return true;
            }
            case "uitexteffectamount", "ui_text_effect_amount", "texteffectamount" -> {
                if (!(value instanceof Number n)) return false;
                node.uiTextEffectAmount = n.floatValue(); return true;
            }
            case "uitextglowsize", "ui_text_glow_size", "textglowsize", "glowsize" -> {
                if (!(value instanceof Number n)) return false;
                node.uiTextGlowSize = n.floatValue(); return true;
            }
            case "uitextoutlinewidth", "ui_text_outline_width", "textoutlinewidth", "outlinewidth" -> {
                if (!(value instanceof Number n)) return false;
                node.uiTextOutlineWidth = n.floatValue(); return true;
            }
            case "uitextunderlinecolor", "ui_text_underline_color", "textunderlinecolor", "underlinecolor" -> {
                node.uiTextUnderlineColor = str(value); return true;
            }
            case "uitextunderlinethickness", "ui_text_underline_thickness", "textunderlinethickness", "underlinethickness" -> {
                if (!(value instanceof Number n)) return false;
                node.uiTextUnderlineThickness = n.floatValue(); return true;
            }
            case "uitextunderlineoffset", "ui_text_underline_offset", "textunderlineoffset", "underlineoffset" -> {
                if (!(value instanceof Number n)) return false;
                node.uiTextUnderlineOffset = n.floatValue(); return true;
            }
            case "uitextshadowcolor", "ui_text_shadow_color", "textshadowcolor", "shadowcolor" -> {
                node.uiTextShadowColor = str(value); return true;
            }
            case "uitextshadowoffsetx", "ui_text_shadow_offset_x", "textshadowoffsetx", "shadowoffsetx" -> {
                if (!(value instanceof Number n)) return false;
                node.uiTextShadowOffsetX = n.floatValue(); return true;
            }
            case "uitextshadowoffsety", "ui_text_shadow_offset_y", "textshadowoffsety", "shadowoffsety" -> {
                if (!(value instanceof Number n)) return false;
                node.uiTextShadowOffsetY = n.floatValue(); return true;
            }
            case "uitextshadowsize", "ui_text_shadow_size", "textshadowsize", "shadowtextsize" -> {
                if (!(value instanceof Number n)) return false;
                node.uiTextShadowSize = n.floatValue(); return true;
            }
            case "uialign", "ui_align", "align" -> { node.uiAlign = str(value); return true; }
            case "uiwrap", "ui_wrap", "wrap" -> {
                if (!(value instanceof Number n)) return false;
                node.uiWrap = n.intValue(); return true;
            }
            case "uiautowrap", "ui_auto_wrap", "autowrap" -> { node.uiAutoWrap = truthy(value); return true; }
            case "uiwrapmode", "ui_wrap_mode", "wrapmode" -> { node.uiWrapMode = str(value); return true; }
            case "uitexture", "ui_texture" -> { node.uiTexture = str(value); return true; }
            case "svg", "uisvg", "ui_svg" -> {
                if (node.type != SceneNodeType.UI_SVG) return false;
                node.uiTexture = str(value); return true;
            }
            case "uisvgstroke", "ui_svg_stroke", "stroke" -> {
                if (node.type != SceneNodeType.UI_SVG) return false;
                node.uiSvgStroke = str(value); return true;
            }
            case "uisvgstrokewidth", "ui_svg_stroke_width", "strokewidth", "stroke_width" -> {
                if (node.type != SceneNodeType.UI_SVG) return false;
                if (value instanceof Number n) {
                    node.uiSvgStrokeWidth = n.floatValue();
                    return true;
                }
                try {
                    node.uiSvgStrokeWidth = Float.parseFloat(str(value));
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            case "uisvgrecolor", "ui_svg_recolor", "recolor" -> {
                if (node.type != SceneNodeType.UI_SVG) return false;
                node.uiSvgRecolor = truthy(value); return true;
            }
            case "uiplaceholder", "ui_placeholder", "placeholder" -> {
                node.uiPlaceholder = str(value); return true;
            }
            case "uitooltip", "ui_tooltip", "tooltip" -> {
                node.uiTooltip = str(value); return true;
            }
            case "uimaxchars", "ui_max_chars", "maxchars" -> {
                if (!(value instanceof Number n)) return false;
                node.uiMaxChars = n.intValue(); return true;
            }
            case "uipercentx", "ui_percent_x", "percentx" -> { node.uiPercentX = truthy(value); return true; }
            case "uipercenty", "ui_percent_y", "percenty" -> { node.uiPercentY = truthy(value); return true; }
            case "uipercentw", "ui_percent_w", "percentw" -> { node.uiPercentW = truthy(value); return true; }
            case "uipercenth", "ui_percent_h", "percenth" -> { node.uiPercentH = truthy(value); return true; }
            case "uicontenth", "ui_content_h", "contenth" -> {
                if (!(value instanceof Number n)) return false;
                node.uiContentH = n.intValue(); return true;
            }
            case "uiscrollbar", "ui_scrollbar", "scrollbar" -> { node.uiScrollbar = truthy(value); return true; }
            case "uiitem", "ui_item", "item" -> { node.uiItem = str(value); return true; }
            case "uientitymodel", "ui_entity_model" -> { node.uiEntityModel = str(value); return true; }
            case "uientitytexture", "ui_entity_texture" -> { node.uiEntityTexture = str(value); return true; }
            case "uientityanim", "ui_entity_anim" -> { node.uiEntityAnim = str(value); return true; }
            case "uientityscale", "ui_entity_scale" -> {
                if (!(value instanceof Number n)) return false;
                node.uiEntityScale = n.floatValue(); return true;
            }
            case "uientitymode", "ui_entity_mode" -> { node.uiEntityMode = str(value); return true; }
            case "uientityref", "ui_entity_ref" -> { node.uiEntityRef = str(value); return true; }
            case "uientityalways", "ui_entity_always" -> { node.uiEntityAlways = truthy(value); return true; }
            case "uientityanchorx", "ui_entity_anchor_x" -> {
                if (!(value instanceof Number n)) return false;
                node.uiEntityAnchorX = n.floatValue(); return true;
            }
            case "uientityanchory", "ui_entity_anchor_y" -> {
                if (!(value instanceof Number n)) return false;
                node.uiEntityAnchorY = n.floatValue(); return true;
            }
            case "uientityfeetcrop", "ui_entity_feet_crop" -> {
                if (!(value instanceof Number n)) return false;
                node.uiEntityFeetCrop = n.floatValue(); return true;
            }
            case "uialpha", "ui_alpha", "alpha" -> {
                if (!(value instanceof Number n)) return false;
                node.uiAlpha = n.floatValue(); return true;
            }
            case "uisliceborders", "ui_slice_borders", "sliceborders" -> {
                if (!(value instanceof Number n)) return false;
                node.uiSliceBorders = n.intValue(); return true;
            }
            case "uislicescale", "ui_slice_scale", "slicescale" -> {
                if (!(value instanceof Number n)) return false;
                node.uiSliceScale = n.floatValue(); return true;
            }
            case "uisrcu", "ui_src_u", "srcu" -> {
                if (!(value instanceof Number n)) return false;
                node.uiSrcU = n.intValue(); return true;
            }
            case "uisrcv", "ui_src_v", "srcv" -> {
                if (!(value instanceof Number n)) return false;
                node.uiSrcV = n.intValue(); return true;
            }
            case "uisrcw", "ui_src_w", "srcw" -> {
                if (!(value instanceof Number n)) return false;
                node.uiSrcW = n.intValue(); return true;
            }
            case "uisrch", "ui_src_h", "srch" -> {
                if (!(value instanceof Number n)) return false;
                node.uiSrcH = n.intValue(); return true;
            }
            case "src" -> { applySrc(node, value); return true; }
            case "uicropshape", "ui_crop_shape", "cropshape", "crop_shape", "srcshape", "src_shape" -> {
                node.uiCropShape = str(value); return true;
            }
            case "uicropfeather", "ui_crop_feather", "cropfeather", "crop_feather", "feather", "edgeblur", "edge_blur" -> {
                if (!(value instanceof Number n)) return false;
                node.uiCropFeather = Math.max(0f, n.floatValue()); return true;
            }
            case "uicropfeatherl", "ui_crop_feather_l", "featherl", "feather_l", "featherleft", "feather_left" -> {
                if (!(value instanceof Number n)) return false;
                node.uiCropFeatherL = clamp01f(n.floatValue()); return true;
            }
            case "uicropfeathert", "ui_crop_feather_t", "feathert", "feather_t", "feathertop", "feather_top" -> {
                if (!(value instanceof Number n)) return false;
                node.uiCropFeatherT = clamp01f(n.floatValue()); return true;
            }
            case "uicropfeatherr", "ui_crop_feather_r", "featherr", "feather_r", "featherright", "feather_right" -> {
                if (!(value instanceof Number n)) return false;
                node.uiCropFeatherR = clamp01f(n.floatValue()); return true;
            }
            case "uicropfeatherb", "ui_crop_feather_b", "featherb", "feather_b", "featherbottom", "feather_bottom" -> {
                if (!(value instanceof Number n)) return false;
                node.uiCropFeatherB = clamp01f(n.floatValue()); return true;
            }
            case "uicropfeathersmooth", "ui_crop_feather_smooth", "feathersmooth", "feather_smooth" -> {
                node.uiCropFeatherSmooth = truthy(value); return true;
            }
            case "uifill", "ui_fill", "fill", "fillmode", "fill_mode" -> {
                node.uiFill = str(value); return true;
            }
            case "uifillamount", "ui_fill_amount", "fillamount", "fill_amount" -> {
                if (!(value instanceof Number n)) return false;
                node.uiFillAmount = Math.max(0f, Math.min(1f, n.floatValue())); return true;
            }
            case "uifillorigin", "ui_fill_origin", "fillorigin", "fill_origin" -> {
                node.uiFillOrigin = str(value); return true;
            }
            case "uivideo", "ui_video", "video" -> {
                if (node.type != SceneNodeType.UI_VIDEO) return false;
                node.uiVideo = str(value); return true;
            }
            case "source" -> {
                if (node.type == SceneNodeType.UI_VIDEO) {
                    node.uiVideo = str(value); return true;
                }
                if (node.type == SceneNodeType.UI_SVG) {
                    node.uiTexture = str(value); return true;
                }
                return false;
            }
            case "uivideoenabled", "ui_video_enabled", "playerenabled", "player_enabled" -> {
                if (node.type != SceneNodeType.UI_VIDEO) return false;
                node.uiVideoEnabled = truthy(value); return true;
            }
            case "uivideoplaying", "ui_video_playing", "playing", "autoplay" -> {
                if (node.type != SceneNodeType.UI_VIDEO) return false;
                node.uiVideoPlaying = truthy(value); return true;
            }
            case "uivideoloop", "ui_video_loop", "videoloop" -> {
                if (node.type != SceneNodeType.UI_VIDEO) return false;
                node.uiVideoLoop = truthy(value); return true;
            }

            case "canvasoverlay", "canvas_overlay", "overlay" -> {
                node.canvasOverlay = truthy(value); return true;
            }
            case "canvaslayer", "canvas_layer", "layer" -> {
                if (!(value instanceof Number n)) return false;
                node.canvasLayer = n.intValue(); return true;
            }
            case "canvasautoshow", "canvas_auto_show", "autoshow", "auto_show" -> {
                node.canvasAutoShow = truthy(value); return true;
            }
            case "canvascanclose", "canvas_can_close", "canclose", "can_close" -> {
                node.canvasCanClose = truthy(value); return true;
            }
            case "canvasdim", "canvas_dim", "dim" -> { node.canvasDim = truthy(value); return true; }
            case "canvasworldspace", "canvas_world_space", "worldspace", "world_space", "world3d" -> {
                node.canvasWorldSpace = truthy(value); return true;
            }
            case "canvasworldwidth", "canvas_world_width", "worldwidth", "world_width" -> {
                if (!(value instanceof Number n)) return false;
                node.canvasWorldWidth = n.floatValue(); return true;
            }
            case "canvasworldheight", "canvas_world_height", "worldheight", "world_height" -> {
                if (!(value instanceof Number n)) return false;
                node.canvasWorldHeight = n.floatValue(); return true;
            }
            case "canvasworldorientation", "canvas_world_orientation", "orientation" -> {
                node.canvasWorldOrientation = value == null ? "billboard" : String.valueOf(value);
                return true;
            }
            case "canvasworldresolution", "canvas_world_resolution", "resolution" -> {
                if (!(value instanceof Number n)) return false;
                node.canvasWorldResolution = n.floatValue(); return true;
            }
            case "canvasworldmaxdistance", "canvas_world_max_distance", "maxdistance", "max_distance" -> {
                if (!(value instanceof Number n)) return false;
                node.canvasWorldMaxDistance = n.floatValue(); return true;
            }
            case "canvasworldrefresh", "canvas_world_refresh", "refresh" -> {
                if (!(value instanceof Number n)) return false;
                node.canvasWorldRefresh = n.intValue(); return true;
            }

            case "gridcolumns", "grid_columns", "columns" -> {
                if (!(value instanceof Number n)) return false;
                node.gridColumns = n.intValue(); return true;
            }
            case "gridcellw", "grid_cell_w", "cellw" -> {
                if (!(value instanceof Number n)) return false;
                node.gridCellW = n.intValue(); return true;
            }
            case "gridcellh", "grid_cell_h", "cellh" -> {
                if (!(value instanceof Number n)) return false;
                node.gridCellH = n.intValue(); return true;
            }
            case "gridgapx", "grid_gap_x", "gapx" -> {
                if (!(value instanceof Number n)) return false;
                node.gridGapX = n.intValue(); return true;
            }
            case "gridgapy", "grid_gap_y", "gapy" -> {
                if (!(value instanceof Number n)) return false;
                node.gridGapY = n.intValue(); return true;
            }
            case "gridstretchcells", "grid_stretch_cells", "stretchcells" -> {
                node.gridStretchCells = truthy(value); return true;
            }
            case "gridmode", "grid_mode" -> {
                node.gridMode = str(value); return true;
            }
            case "gridalign", "grid_align" -> {
                node.gridAlign = str(value); return true;
            }
            case "gridflow", "grid_flow", "flow" -> {
                node.gridFlow = str(value); return true;
            }
            case "gridhugcontent", "grid_hug_content", "hug", "hugcontent", "hug_content" -> {
                node.gridHugContent = truthy(value); return true;
            }
            case "uichildalign", "ui_child_align", "childalign", "child_align", "alignchildren", "align_children" -> {
                node.uiChildAlign = str(value); return true;
            }
            case "uidragenabled", "ui_drag_enabled", "dragenabled", "drag_enabled" -> {
                node.uiDragEnabled = truthy(value); return true;
            }
            case "uidragaxis", "ui_drag_axis", "dragaxis", "drag_axis", "axis" -> {
                node.uiDragAxis = str(value); return true;
            }
            case "uidraggroup", "ui_drag_group", "draggroup", "drag_group" -> {
                node.uiDragGroup = str(value); return true;
            }
            case "uidragpayload", "ui_drag_payload", "dragpayload", "drag_payload", "payload" -> {
                node.uiDragPayload = str(value); return true;
            }
            case "uidragconstrain", "ui_drag_constrain", "dragconstrain", "drag_constrain", "constrain" -> {
                node.uiDragConstrain = truthy(value); return true;
            }
            case "uidragrevert", "ui_drag_revert", "dragrevert", "drag_revert", "revert" -> {
                node.uiDragRevert = truthy(value); return true;
            }
            case "uidragthreshold", "ui_drag_threshold", "dragthreshold", "drag_threshold", "threshold" -> {
                if (!(value instanceof Number n)) return false;
                node.uiDragThreshold = Math.max(0, n.intValue()); return true;
            }
            case "uidropenabled", "ui_drop_enabled", "dropenabled", "drop_enabled" -> {
                node.uiDropEnabled = truthy(value); return true;
            }
            case "uidropgroup", "ui_drop_group", "dropgroup", "drop_group" -> {
                node.uiDropGroup = str(value); return true;
            }

            case "script" -> { node.script = str(value); return true; }
            case "runonscenestart", "run_on_scene_start" -> {
                node.runOnSceneStart = truthy(value); return true;
            }

            default -> {
                return false;
            }
        }
    }

    /** Billboard width/height aliases when node is a billboard (avoids clash with hitbox w/h). */
    static Object getBillboardSizeAlias(SceneNode node, String prop) {
        if (node == null || node.type != SceneNodeType.BILLBOARD) return null;
        return switch (prop) {
            case "w", "width" -> node.billboardWidth;
            case "h", "height" -> node.billboardHeight;
            default -> null;
        };
    }

    static boolean setBillboardSizeAlias(SceneNode node, String prop, Object value) {
        if (node == null || node.type != SceneNodeType.BILLBOARD) return false;
        if (!(value instanceof Number n)) return false;
        return switch (prop) {
            case "w", "width" -> { node.billboardWidth = n.floatValue(); yield true; }
            case "h", "height" -> { node.billboardHeight = n.floatValue(); yield true; }
            default -> false;
        };
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        if (value instanceof String s) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            return t.equals("true") || t.equals("1") || t.equals("yes") || t.equals("on");
        }
        return value != null;
    }

    private static float clamp01f(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    @SuppressWarnings("unchecked")
    private static List<org.zonarstudio.spraute_engine.scene.resource.SceneDropRule> copyDropList(Object value) {
        List<org.zonarstudio.spraute_engine.scene.resource.SceneDropRule> out = new ArrayList<>();
        if (!(value instanceof List<?> list)) return out;
        for (Object item : list) {
            if (item instanceof org.zonarstudio.spraute_engine.scene.resource.SceneDropRule rule) {
                out.add(rule.copy());
            } else if (item instanceof Map<?, ?> map) {
                var rule = new org.zonarstudio.spraute_engine.scene.resource.SceneDropRule();
                Object id = map.get("item");
                if (id == null) id = map.get("itemId");
                rule.itemId = id == null ? "" : String.valueOf(id);
                Object min = map.get("min");
                Object max = map.get("max");
                Object chance = map.get("chance");
                if (min instanceof Number n) rule.min = n.intValue();
                if (max instanceof Number n) rule.max = n.intValue();
                if (chance instanceof Number n) rule.chance = n.intValue();
                out.add(rule);
            }
        }
        return out;
    }

    private static void applySrc(SceneNode node, Object value) {
        int[] r = parseFourInts(value);
        if (r == null) return;
        node.uiSrcU = r[0];
        node.uiSrcV = r[1];
        node.uiSrcW = r[2];
        node.uiSrcH = r[3];
    }

    private static int[] parseFourInts(Object value) {
        if (value instanceof List<?> list && list.size() >= 4) {
            return new int[]{toInt(list.get(0)), toInt(list.get(1)), toInt(list.get(2)), toInt(list.get(3))};
        }
        if (value instanceof String s) {
            String[] parts = s.replace('[', ' ').replace(']', ' ').split("[,;\\s]+");
            java.util.List<Integer> nums = new ArrayList<>();
            for (String p : parts) {
                if (p.isBlank()) continue;
                try { nums.add((int) Double.parseDouble(p.trim())); } catch (NumberFormatException ignored) {}
            }
            if (nums.size() >= 4) return new int[]{nums.get(0), nums.get(1), nums.get(2), nums.get(3)};
        }
        return null;
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try { return (int) Double.parseDouble(String.valueOf(o).trim()); } catch (Exception e) { return 0; }
    }
}
