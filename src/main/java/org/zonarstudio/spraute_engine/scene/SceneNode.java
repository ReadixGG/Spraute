package org.zonarstudio.spraute_engine.scene;

import org.zonarstudio.spraute_engine.scene.resource.SceneBlockDef;
import org.zonarstudio.spraute_engine.scene.resource.SceneCraftDef;
import org.zonarstudio.spraute_engine.scene.resource.SceneDropDef;
import org.zonarstudio.spraute_engine.scene.resource.SceneItemDef;
import org.zonarstudio.spraute_engine.scene.resource.SceneParticleDef;
import org.zonarstudio.spraute_engine.scene.resource.SceneProjectileDef;
import org.zonarstudio.spraute_engine.scene.resource.SceneTabDef;
import org.zonarstudio.spraute_engine.scene.resource.SceneWorldDef;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A single node in the scene hierarchy (scene root, NPC, future types). */
public final class SceneNode {
    /** Assigned by {@link SceneNodeIds} when added in the editor ({@code npc1}, …). */
    public String id = "";
    public SceneNodeType type = SceneNodeType.NPC;
    public String name = "";
    /** Lowercase tags for script lookup ({@code hasTag}, {@code findByTag}). */
    public List<String> tags = new ArrayList<>();
    public SceneTransform transform = new SceneTransform();
    public List<SceneNode> children = new CopyOnWriteArrayList<>();
    /** Runtime-only identity for a dynamic PLAYER node. */
    public transient String playerUuid = "";
    public transient boolean dynamicPlayer;
    /**
     * Runtime-only: set on a per-player clone of a Local World template node (and its
     * whole cloned subtree) to the owning player's UUID string. Never persisted (transient) —
     * clones live only under that player's live PLAYER node, never in the saved document.
     * {@link #LOCAL_OWNER_TAG_PREFIX} + this value is the entity tag that
     * {@code PlayerEvent.StartTracking} uses to hide the spawned entity from every other player.
     */
    public transient String ownerPlayerUuid;
    /** Entity tag prefix for per-player Local World visibility (see {@link #ownerPlayerUuid}). */
    public static final String LOCAL_OWNER_TAG_PREFIX = "spraute_local_owner_";

    /**
     * Default {@code player.data} keys (session). Applied on join / scene start if missing.
     * Stored on the PLAYERS node.
     */
    public java.util.Map<String, Object> playerDataDefaults = new java.util.LinkedHashMap<>();
    /**
     * Default {@code player.savedData} keys (world save). Applied on join / scene start if missing.
     * Stored on the PLAYERS node.
     */
    public java.util.Map<String, Object> playerSavedDefaults = new java.util.LinkedHashMap<>();

    /**
     * Default CAMERA rest pose stored on the PLAYERS node (persisted).
     * CAMERA children copy these on spawn; authoring edits write back here.
     */
    public float cameraRestX;
    public float cameraRestY;
    public float cameraRestZ;
    public float cameraRestYaw;
    public float cameraRestPitch;
    /** 0 = vanilla FOV. Used by CAMERA nodes and PLAYERS defaults. */
    public float cameraFov;
    /** Extra boom along look (blocks). 4 ≈ vanilla third person. */
    public float cameraDistance;
    /** Freeze an axis at its rest angle instead of following the player's look. */
    public boolean cameraLockYaw;
    public boolean cameraLockPitch;
    /**
     * When true (default), camera rides with the player (eye-relative offset).
     * When false, {@code transform}/{@code cameraRest*} are world XYZ and absolute look.
     */
    public boolean cameraFollowPlayer = true;
    /**
     * While following: freeze this world axis instead of tracking the player.
     * {@link #cameraWorldX}/{@link #cameraWorldY}/{@link #cameraWorldZ} are the pin.
     */
    public boolean cameraLockX;
    public boolean cameraLockY;
    public boolean cameraLockZ;
    public double cameraWorldX;
    public double cameraWorldY;
    public double cameraWorldZ;
    /** Orbit: hang the boom on a pivot near the player and keep aiming back at it. */
    public boolean cameraOrbit;
    public float cameraOrbitYaw;
    public float cameraOrbitPitch;
    /** Orbit pivot offset from the eyes (X right, Y up, Z back). */
    public float cameraPivotX;
    public float cameraPivotY;
    public float cameraPivotZ;
    /** Stop in front of blocks instead of clipping through them. */
    public boolean cameraCollide;
    /**
     * When the camera is offset / boomed out, still draw the first-person hand
     * and held items. Off by default (vanilla third-person behaviour).
     */
    public boolean cameraShowHand;
    /**
     * Persistent HUD chrome for this camera (not a cutscene override).
     * {@link org.zonarstudio.spraute_engine.network.CameraPacket#HIDE_NONE} /
     * {@link org.zonarstudio.spraute_engine.network.CameraPacket#HIDE_MINECRAFT} /
     * {@link org.zonarstudio.spraute_engine.network.CameraPacket#HIDE_ALL}.
     */
    public byte cameraHideGui;
    /**
     * WASD is applied in the camera's yaw, not the player's body yaw.
     * When the view is not driven by the mouse, the body also faces the walk
     * direction (standard third-person).
     */
    public boolean cameraMoveRelative;
    /**
     * Mouse turns the camera, not the player. The body faces the walk direction
     * and WASD is applied in camera space (third-person action camera).
     */
    public boolean cameraSeparateTurn;
    /**
     * How softly the body turns when {@link #cameraSeparateTurn} (or look-at-interact)
     * rotates the player. {@code 0} = snap; {@code 1} = slow blend.
     */
    public float cameraTurnSmooth;
    /**
     * Break / place / attack pick follows the screen centre (camera look),
     * not the player's body facing.
     */
    public boolean cameraScreenRaycast;
    /**
     * While mining, attacking or using, the player body looks at that target
     * (camera can stay independent via {@link #cameraSeparateTurn}).
     */
    public boolean cameraLookAtInteract;
    /**
     * Client-only: hide (or ghost) solid blocks between the camera and the look
     * subject so walls do not cover the shot.
     */
    public boolean cameraClearOcclusion;
    /**
     * Ghost opacity of cleared blocks, 0…1. {@code 0} = fully invisible,
     * {@code 0.35} ≈ 35% see-through cubes. Ignored when {@link #cameraClearOcclusion} is false.
     */
    public float cameraOcclusionAlpha;
    /**
     * How far off the camera→subject line (in blocks) to also hide.
     * {@code 0} = the line only; {@code 1.5} ≈ a player-wide corridor.
     */
    public float cameraOcclusionRadius;
    /**
     * Kill vanilla view bob, hurt-cam tilt and portal nausea on this player's camera.
     * Motion effects (walk bob, shake, punch) are a separate stack — like Unreal
     * CameraModifier / Cinemachine Impulse, not post-process.
     */
    public boolean cameraDisableEffects;
    /** Procedural head bob while walking / landing, replaces vanilla bob when on. */
    public boolean cameraWalkBob;
    /** 0…2 scale for {@link #cameraWalkBob}. {@code 1} = default. */
    public float cameraWalkBobAmount = 1f;

    // Script payload. Script nodes reference files in spraute_engine/scripts.
    public String script = "";
    public boolean runOnSceneStart = true;

    // Generic route payload. ROUTE_POINT may also store authored look (yaw/pitch).
    public String routeFile = "";
    /** Always {@code free}. Kept for file compatibility; legacy {@code blocks} is ignored. */
    public String routeMode = "free";
    public boolean routeClosed;
    /**
     * How free-mode segments connect: {@code linear}, {@code smooth} (Catmull-Rom XYZ),
     * {@code smooth_camera} (also smooth look-along for camera flights).
     */
    public String routeCurve = "linear";
    /** 0 = straight corners, 1 = full Catmull-Rom. Ignored for {@code linear}. */
    public float routeSmoothness = 1f;
    /** Distance along the route between ordered spatial children. */
    public float routeLayoutSpacing = 2f;
    /** Additional distance from the chosen start/center/end anchor. */
    public float routeLayoutOffset;
    /** start / center / end alignment of the whole child row on the route. */
    public String routeLayoutAnchor = "start";
    /** clamp / loop / extend behaviour when children do not fit on an open route. */
    public String routeLayoutOverflow = "clamp";
    /** Lay children from the final route point toward the first. */
    public boolean routeLayoutReverse;
    /** Rotate children toward the route tangent; pitch remains optional for upright objects. */
    public boolean routeLayoutAlignYaw = true;
    public boolean routeLayoutAlignPitch;
    /**
     * ROUTE_POINT: when true, {@link SceneTransform#ry}/{@link SceneTransform#rx} are an authored
     * look direction for camera flights ({@code lookAlongPoints}).
     */
    public boolean routeHasLook;

    // NPC payload
    public String model = "";
    public String texture = "";
    /**
     * Outer-skin parts to hide (hat / jacket / sleeves / pants / cape).
     * PLAYER: original or installed base skin. SKIN_OVERLAY: that overlay only.
     * Bits match {@link net.minecraft.world.entity.player.PlayerModelPart#getMask()}.
     */
    public int hiddenSkinLayers;
    public String animation = "";
    public boolean previewEnabled = true;
    public boolean nameVisible = true;
    public float maxHealth = 20f;
    public float movementSpeed = 0.3f;
    public float flyingSpeed = 0.4f;
    public float flySmoothing = 0.35f;
    /** Body yaw turn rate while walking (°/tick). */
    public float walkTurnSpeed = 6f;
    /** Body yaw turn rate while standing / looking (°/tick). */
    public float idleTurnSpeed = 6f;
    /** Horizontal arrive radius for ground pathfinding (blocks). */
    public float pathArriveRadius = 0.1f;
    public float pathArriveVert = 1.5f;
    /** Max vertical rise without jumping (stairs, slabs). */
    public float pathStepHeight = 0.6f;
    public int pathMaxJump = 1;
    public int pathMaxDrop = 3;
    public float pathMaxRange = 48f;
    public boolean pathDiagonal = true;
    public boolean pathAvoidWater = true;
    public boolean pathAvoidLava = true;
    public int pathMaxNodes = 8192;
    public boolean pathSnapOnArrive = true;
    public float flyArriveRadius = 0.1f;
    public float flyMaxRange = 48f;
    public boolean flyDiagonal = true;
    public boolean flyAvoidWater;
    public boolean flyAvoidLava = true;
    public int flyMaxNodes = 8192;
    public boolean flySnapOnArrive = true;
    public boolean invulnerable;
    public boolean silent;
    public boolean glowing;
    /**
     * NPC / MODEL: blend PNG alpha ({@code entityTranslucent}). Off = cutout (hard holes).
     * Default on so glass / fade textures work without extra setup.
     */
    public boolean translucent = true;
    /** NPC / MODEL: vertex opacity 0–1 (forces translucent when &lt; 1). */
    public float opacity = 1f;
    /** NPC / MODEL: screen-space geometry/animation LOD. */
    public boolean lodEnabled = true;
    /** NPC / MODEL: 0 uses the client default; otherwise camera-relative blocks. */
    public float renderDistance;
    /** NPC / MODEL: bypass distance/LOD hiding for important cinematic actors. */
    public boolean forceVisible;
    /** NPC: keep nearby chunks loaded so pathfinding continues when players leave. */
    public boolean loadChunks;
    public boolean noGravity;
    public boolean collision = true;
    public boolean flying;
    public boolean swimming;
    public String group = "";
    public String playerSkinOverlay = "";
    public String headBone = "Head";
    public boolean unlimitedHeadLook;
    public float nameYOffset;
    public float hitboxWidth = 0.6f;
    public float hitboxHeight = 1.8f;
    public float hitboxDepth = 0.6f;
    public float hitboxOffsetX;
    public float hitboxOffsetY;
    public float hitboxOffsetZ;
    public boolean hitboxSolid = true;
    public boolean hitboxTrigger;
    /**
     * Logical trigger id for {@code on hitboxTouch} / {@code await hitboxTouch}
     * (falls back to node id/name when blank).
     */
    public String hitboxTriggerId = "";
    public boolean showHitbox;
    public String idleAnimation = "idle";
    public String walkAnimation = "walk";
    public String flyIdleAnimation = "fly_idle";
    public String flyAnimation = "fly";
    public String swimIdleAnimation = "swim_idle";
    public String swimAnimation = "swim";
    public String deathAnimation = "death";
    public float attackRange = 2.5f;
    public int attackCooldown = 20;
    public int attackHitDelay = 8;
    public String attackHand = "right";
    public String attackAnimations = "attack";
    /** Death drops (item + min/max + chance). Same shape as create npc dropItem/drops. */
    public List<org.zonarstudio.spraute_engine.scene.resource.SceneDropRule> drops = new ArrayList<>();
    /** Experience orbs awarded when this NPC dies. */
    public int dropXp;

    // Billboard payload (texture is shared with the NPC/MODEL field above)
    public float billboardWidth = 1f;
    public float billboardHeight = 1f;
    public boolean billboardSeeThrough;

    // Particle emitter payload (world-space VFX attached to the scene graph)
    public String particleType = "minecraft:flame";
    /** point | line | sphere | spiral */
    public String particleShape = "point";
    /** burst (explosion) | directional (one way / cone) */
    public String particleEmitMode = "directional";
    public int particleCount = 12;
    /** Spawn volume jitter around each emit point (blocks). */
    public float particleSpreadX = 0.1f;
    public float particleSpreadY = 0.1f;
    public float particleSpreadZ = 0.1f;
    /** Initial launch speed (blocks/tick scale used by particle engine). */
    public float particleSpeed = 0.2f;
    /** Random ± fraction of speed (0 = fixed, 0.5 = ±50%). */
    public float particleSpeedSpread;
    /**
     * Gravity factor applied after launch (Particle.gravity). Positive = fall over time.
     * Does not subtract from initial velocity.
     */
    public float particleGravity;
    /**
     * When true, particles collide with blocks ({@code Particle.hasPhysics}).
     * Default false — bursts near the ground otherwise vanish on the first hit.
     */
    public boolean particleCollide;
    /** Local emit direction for directional mode (rotated by node transform). */
    public float particleDirX;
    public float particleDirY = 1f;
    public float particleDirZ;
    /** Cone half-angle in degrees for directional mode (0 = perfect beam). */
    public float particleCone = 25f;
    /** Particle lifetime in ticks. {@code 0} = leave particle-type default. */
    public int particleLifetime;
    /**
     * Ticks of alpha fade-out at the end of life. {@code 0} = hard cut;
     * typical 8–15 for soft landings on the ground.
     */
    public int particleFade = 10;
    public boolean particleLoop = true;
    /** Ticks between bursts when looping (also used for editor preview). */
    public int particleInterval = 5;
    public float particleRadius = 1f;
    public float particleHeight = 2f;
    /** Line end offset in local space (from node origin). */
    public float particleLineX;
    public float particleLineY = 2f;
    public float particleLineZ;
    /**
     * Complete inline custom Particle System. Null means a legacy node that still uses
     * {@link #particleType} and Minecraft's particle engine.
     */
    public org.zonarstudio.spraute_engine.particle.ParticleSystemSpec particleSystem;
    /** Transitional RES_PARTICLE schema retained for old scene snapshots. */
    public org.zonarstudio.spraute_engine.particle.ParticleEffectDef particleEffect;

    // Light payload (places minecraft:light — vanilla white light 0–15)
    /** Block light level 0–15. */
    public int lightLevel = 15;
    /** When false, the light block is removed even if the node is active. */
    public boolean lightEnabled = true;
    /** Editor gizmo visibility. */
    public boolean showLight = true;

    // Audio source payload (positional looping sound with falloff)
    /** Sound id, e.g. {@code minecraft:ambient.cave} or {@code spraute_engine:mysound}. */
    public String audioSound = "minecraft:ambient.cave";
    /** {@code sphere} or {@code cube}; extents scaled by transform sx/sy/sz. */
    public String audioShape = "sphere";
    /** Distance (blocks, before scale) where volume is still 100% of {@link #audioVolume}. */
    public float audioFullRadius = 4f;
    /** Outer reach (blocks, before scale): volume falls to 0 at this distance. */
    public float audioMaxDistance = 16f;
    /** Base volume at/inside the full-radius zone (0–2 typical). */
    public float audioVolume = 1f;
    public float audioPitch = 1f;
    public boolean audioLoop = true;
    /**
     * When true, playback restarts each time the listening player enters the audible zone
     * (volume goes from 0 → &gt;0), useful for one-shots and stingers.
     */
    public boolean audioReplayOnEnter = false;
    public boolean audioEnabled = true;
    /** Always draw falloff gizmo in editor (selection always draws it). */
    public boolean showAudio = true;

    // Post-process volume (Unity Volume / Unreal Post Process Volume)
    /**
     * POST_PROCESS node payload, and a PLAYERS-node save snapshot (PLAYER trees are stripped).
     * Live cameras do not copy this onto every player — post stays per CAMERA.
     */
    public PostProcessSettings post = new PostProcessSettings();
    /** Always draw the local box gizmo in editor (selection always draws it). */
    public boolean showPost = true;

    public PostProcessSettings post() {
        if (post == null) post = new PostProcessSettings();
        return post;
    }

    // Animator payload: .animation.json file (workspace path) + active clip name
    public String animatorFile = "";
    public String animatorClip = "";

    // GUI payload (CANVAS + UI_* nodes).
    // For UI nodes transform.position.x/y = pixels on the canvas, rotation.z = degrees,
    // scale.x = uniform widget scale multiplier.
    public int uiW = 100;                        // widget size (canvas: uses uiCanvasW/H)
    public int uiH = 22;
    public int uiCanvasW = 300;                  // canvas panel size in GUI pixels
    public int uiCanvasH = 180;
    public String uiColor = "#C0101010";         // fill / text color / canvas background
    public String uiHoverColor = "#66447799";    // button hover
    public String uiGradient = "";               // last gradient color ("" = none)
    public String uiGradientStart = "";          // first gradient color ("" = legacy: use uiColor)
    public String uiGradientDir = "v";           // legacy: v | h (kept for old scenes)
    public float uiGradientAngle = -1f;          // gradient direction in degrees (0 = →, 90 = ↓); -1 = legacy uiGradientDir
    public String uiGradientStops = "";          // extra mid colors, comma-separated (#AARRGGBB,...)
    public float uiRadius;                       // corner rounding, px
    public float uiBorder;                       // inner border width, px (drawn inward)
    public String uiBorderColor = "#66FFFFFF";
    public String uiBorderGradient = "";         // border gradient stops, comma-separated ("" = solid color)
    public float uiBorderGradientAngle = 90f;    // border gradient direction, degrees
    public float uiOuterBorder;                  // outer outline width, px (drawn outside the rect)
    public String uiOuterBorderColor = "#66FFFFFF";
    public String uiOuterBorderGradient = "";    // outer outline gradient stops ("" = solid color)
    public float uiOuterBorderGradientAngle = 90f;
    public float uiShadow;                       // soft shadow size, px
    public String uiText = "";                   // text / button label / input initial text
    public float uiTextScale = 1f;
    /** Internal label offset inside UI_BUTTON (px, relative to default placement). */
    public float uiTextOffsetX;
    public float uiTextOffsetY;
    /** Comma-separated text effects: bold, italic, underline, strikethrough, code, shadow,
     * outline, glow, rainbow, pulse, wave, shake, wiggle. Shadow/outline/glow may use
     * {@code _pixel} / {@code _soft} suffixes ({@code glow} is soft by default). */
    public String uiTextEffects = "";
    public String uiTextGradient = "";
    public String uiTextOutlineColor = "#FF000000";
    public String uiTextGlowColor = "#66FFFFFF";
    public float uiTextEffectSpeed = 1f;
    public float uiTextEffectAmount = 1f;
    public float uiTextGlowSize = 3f;
    public float uiTextOutlineWidth = 1f;
    public String uiTextUnderlineColor = "";
    public float uiTextUnderlineThickness = 1f;
    public float uiTextUnderlineOffset;
    public String uiTextShadowColor = "";
    public float uiTextShadowOffsetX = 1f;
    public float uiTextShadowOffsetY = 1f;
    public float uiTextShadowSize = 2f;
    public String uiAlign = "left";              // text align: left | center | right
    public int uiWrap;                           // text wrap width, px (0 = off / use auto)
    public boolean uiAutoWrap;                   // wrap to widget width when text overflows
    public String uiWrapMode = "word";           // "word" | "char"
    public String uiTexture = "";                // image / button texture (workspace path)
    public String uiVideo = "";                  // MP4 under workspace videos/
    public boolean uiVideoEnabled = true;        // decoder/player may be disabled at runtime
    public boolean uiVideoPlaying = true;        // initial play/pause state
    public boolean uiVideoLoop = true;
    /** SVG stroke override ({@code #AARRGGBB}); empty = file / currentColor. */
    public String uiSvgStroke = "";
    /** 0 = stroke-width from the SVG file. */
    public float uiSvgStrokeWidth;
    /** Replace every non-none fill with {@link #uiColor}. */
    public boolean uiSvgRecolor;
    public String uiPlaceholder = "";            // input placeholder
    /** In-game hover tooltip on the widget (create ui {@code tooltip}). */
    public String uiTooltip = "";
    public int uiMaxChars = 32;                  // input max characters
    // Units: interpret transform.px/py and uiW/uiH as percent of parent (canvas) instead of px.
    public boolean uiPercentX;
    public boolean uiPercentY;
    public boolean uiPercentW;
    public boolean uiPercentH;
    public int uiContentH = 200;                 // UI_SCROLL inner content height, px
    public boolean uiScrollbar = true;           // UI_SCROLL scrollbar visible
    public String uiItem = "minecraft:diamond";  // UI_ITEM / UI_BLOCK registry id
    public String uiEntityModel = "";            // UI_ENTITY geo model (workspace path)
    public String uiEntityTexture = "";          // UI_ENTITY texture
    public String uiEntityAnim = "";             // UI_ENTITY idle animation
    public float uiEntityScale = 1f;             // UI_ENTITY render scale
    public String uiEntityMode = "player";       // UI_ENTITY source: player | npc | mob
    public String uiEntityRef = "";              // npc node id or mob registry id
    /** UI_ENTITY: keep rendering from model even if the NPC is dead or not spawned. */
    public boolean uiEntityAlways = true;
    /** UI_ENTITY anchor in the widget box: X 0..1, Y unrestricted (-1 = legacy feetCrop). */
    public float uiEntityAnchorX = 0.5f;
    public float uiEntityAnchorY = 0.5f;
    public float uiEntityFeetCrop = 0.38f;        // used only when anchorY < 0 (legacy)
    // Unity-style active flag: an inactive node (and its whole subtree) does
    // nothing at runtime — no spawn, no render, no scripts, no hitboxes.
    // The editor still shows and edits inactive nodes.
    public boolean active = true;

    // Editor lock: a locked node cannot be picked/edited through the viewport
    // (3D world or GUI canvas), only via the Hierarchy panel. Runtime ignores it.
    public boolean locked;
    /**
     * Prefab asset id this instance was spawned from ({@code spraute_engine/prefabs/<id>.prefab.json}).
     * Used by the hierarchy “Save prefab” action to overwrite that asset with current edits.
     */
    public String prefabId = "";
    public float uiAlpha = 1f;                   // UI_GROUP: opacity multiplier for children
    public int uiSliceBorders;                   // UI_IMAGE/UI_BUTTON 9-slice border, px (0 = off)
    public float uiSliceScale = 1f;              // 9-slice border scale on screen
    /** Sprite rect in texture pixels; {@code uiSrcW <= 0} = whole texture. */
    public int uiSrcU;
    public int uiSrcV;
    public int uiSrcW;
    public int uiSrcH;
    /**
     * Crop silhouette: {@code rect} (default) or {@code circle} (ellipse inscribed in the widget / src).
     */
    public String uiCropShape = "rect";
    /**
     * Soft crop edges (GUI {@code UI_CROP}): fade radius in pixels. {@code 0} = hard scissor.
     */
    public float uiCropFeather;
    /** Per-side multipliers 0…1 for {@link #uiCropFeather} (left / top / right / bottom). */
    public float uiCropFeatherL = 1f;
    public float uiCropFeatherT = 1f;
    public float uiCropFeatherR = 1f;
    public float uiCropFeatherB = 1f;
    /** Smoothstep falloff instead of a linear fade. */
    public boolean uiCropFeatherSmooth = true;
    /**
     * Image/panel fill (Unity Image Filled / Godot TextureProgress):
     * none | ltr | rtl | ttb | btt | clockwise | counterclockwise
     */
    public String uiFill = "";
    /** 0..1 visible portion. */
    public float uiFillAmount = 1f;
    /** Radial start: left | right | top | bottom. */
    public String uiFillOrigin = "bottom";
    /**
     * Where children attach inside a container (UI_GROUP / UI_RECT / UI_SCROLL / UI_CROP / UI_DRAG / UI_DROPTARGET).
     * top_left | top | top_right | left | center | right | bottom_left | bottom | bottom_right
     */
    public String uiChildAlign = "top_left";
    /** UI_DRAG: can the player pick this container up. */
    public boolean uiDragEnabled = true;
    /** UI_DRAG: {@code xy} | {@code x} | {@code y}. */
    public String uiDragAxis = "xy";
    /** UI_DRAG: drop-group token; empty matches any drop zone. */
    public String uiDragGroup = "";
    /** UI_DRAG: string sent with {@code on uiDrop} as {@code _eventPayload}. */
    public String uiDragPayload = "";
    /** UI_DRAG: clamp movement to the parent widget. */
    public boolean uiDragConstrain = true;
    /** UI_DRAG: snap back if released outside a matching drop zone. */
    public boolean uiDragRevert = true;
    /** UI_DRAG: pixels before a press becomes a drag (click-through below this). */
    public int uiDragThreshold = 4;
    /** UI_DROPTARGET: accepts drops. */
    public boolean uiDropEnabled = true;
    /** UI_DROPTARGET: required drag group; empty accepts any. */
    public String uiDropGroup = "";
    public boolean canvasOverlay = true;         // HUD overlay (true) or full screen (false)
    /** Z-order: canvas vs other HUD canvases (and vanilla HUD if overlay);
     *  widgets vs siblings inside the same canvas. Sorted ascending. */
    public int canvasLayer;
    public boolean canvasAutoShow = true;        // show to players when scene loads
    public boolean canvasCanClose = true;        // ESC closes full-screen canvas
    public boolean canvasDim;                    // vanilla background dim (full screen only)
    // ---- CANVAS render mode: screen space (default) or world space ("3D canvas") -------------
    /**
     * World space canvas: the exact same widget tree, drawn as a plane in the world instead of on
     * the player's screen (Unity's Canvas → Render Mode: World Space). The node's transform is
     * then a world position/rotation in blocks/degrees instead of screen pixels, and
     * {@link #uiCanvasW}/{@link #uiCanvasH} are the panel's own pixel resolution.
     */
    public boolean canvasWorldSpace;
    /** World width in blocks (scaled by the node's X scale). */
    public float canvasWorldWidth = 2f;
    /** World height in blocks; {@code <= 0} derives it from the pixel aspect ratio. */
    public float canvasWorldHeight;
    /** billboard (face camera) | yaw (face camera around Y only) | fixed (node rotation). */
    public String canvasWorldOrientation = "billboard";
    /** Supersampling of the off-screen texture: 1 = GUI pixels, 2 = twice as sharp… */
    public float canvasWorldResolution = 2f;
    /** Past this distance (blocks) the panel is not drawn at all. */
    public float canvasWorldMaxDistance = 48f;
    /** How often (ticks) the widget tree is rebuilt into the panel texture. */
    public int canvasWorldRefresh = 5;
    // UI_GRID layout: children are auto-placed into cells left-to-right, top-to-bottom.
    public int gridColumns = 2;
    public int gridCellW = 100;
    public int gridCellH = 24;
    public int gridGapX = 4;
    public int gridGapY = 4;
    public boolean gridStretchCells = true;      // force children to cell size
    /** grid | row | column — row/column flow using child sizes when {@link #gridHugContent}. */
    public String gridMode = "grid";
    /** Pack / cell alignment (same tokens as {@link #uiChildAlign}). */
    public String gridAlign = "top_left";
    /** row: ltr|rtl · column: ttb|btt · grid: ignored */
    public String gridFlow = "ltr";
    /** When true, row/column (and grid cells) size from children instead of fixed cellW/H. */
    public boolean gridHugContent;

    // Register-time resource definitions (RES_*)
    public SceneBlockDef blockDef;
    public SceneItemDef itemDef;
    public SceneCraftDef craftDef;
    public SceneTabDef tabDef;
    public SceneDropDef dropDef;
    public SceneWorldDef worldDef;
    public SceneParticleDef particleDef;
    public SceneProjectileDef projectileDef;

    /** CUSTOM node: schema type id from {@code declare node type}. */
    public String customTypeId = "";
    /** CUSTOM / {@code declare inspector}: schema-driven data (strings/numbers/bools/lists of maps). */
    public java.util.Map<String, Object> customData = new java.util.LinkedHashMap<>();

    /** Fresh node of this type with factory defaults (inspector reset target). */
    public static SceneNode typeTemplate(SceneNodeType type) {
        if (type == null) return new SceneNode();
        if (type.isUiWidget()) return uiWidget(type, "");
        return switch (type) {
            case SCENE -> sceneRoot("");
            case PLAYERS -> playersRoot();
            case GLOBAL_WORLD -> globalWorldRoot();
            case LOCAL_WORLD -> localWorldRoot();
            case PLAYER -> livePlayer("", "");
            case CAMERA -> liveCamera("");
            case AUDIO -> liveAudio("");
            case SKIN_OVERLAY -> skinOverlay("");
            case EMPTY -> empty("");
            case NPC -> npc("");
            case MODEL -> model("");
            case BILLBOARD -> billboard("");
            case HITBOX -> hitbox("");
            case PARTICLE -> particle("");
            case LIGHT -> light("");
            case AUDIO_SOURCE -> audioSource("");
            case POST_PROCESS -> postProcess("");
            case SCRIPT -> script("", "");
            case VISUAL_SCRIPT -> visualScript("", "");
            case ANIMATOR -> animator("");
            case ROUTE -> route("");
            case ROUTE_POINT -> routePoint("", 0, 0, 0);
            case CANVAS -> canvas("");
            case CUSTOM -> custom("", "");
            case RES_BLOCK -> resBlock("");
            case RES_ITEM -> resItem("");
            case RES_CRAFT -> resCraft("");
            case RES_TAB -> resTab("");
            case RES_DROP -> resDrop("");
            case RES_WORLD -> resWorld("");
            case RES_PARTICLE -> resParticle("");
            case RES_PROJECTILE -> resProjectile("");
            default -> {
                SceneNode n = new SceneNode();
                n.type = type;
                yield n;
            }
        };
    }

    public static SceneNode sceneRoot(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.SCENE;
        n.name = name;
        n.id = "scene";
        return n;
    }

    public static SceneNode playersRoot() {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.PLAYERS;
        n.name = "Players";
        n.id = "players";
        n.locked = true;
        ensurePlayerVarMaps(n);
        return n;
    }

    public static SceneNode globalWorldRoot() {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.GLOBAL_WORLD;
        n.name = "Глобальный мир";
        n.id = "global_world";
        n.locked = true;
        return n;
    }

    public static SceneNode localWorldRoot() {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.LOCAL_WORLD;
        n.name = "Локальный мир";
        n.id = "local_world";
        n.locked = true;
        return n;
    }

    public static void ensurePlayerVarMaps(SceneNode node) {
        if (node == null) return;
        if (node.playerDataDefaults == null) node.playerDataDefaults = new java.util.LinkedHashMap<>();
        if (node.playerSavedDefaults == null) node.playerSavedDefaults = new java.util.LinkedHashMap<>();
    }

    public static SceneNode livePlayer(String uuid, String name) {
        return livePlayer(uuid, name, null);
    }

    public static SceneNode livePlayer(String uuid, String name, SceneDocument document) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.PLAYER;
        n.playerUuid = uuid == null ? "" : uuid;
        n.name = name == null ? "" : name;
        n.id = document != null
                ? SceneNodeIds.allocateLive(document, SceneNodeType.PLAYER, n.name)
                : fallbackLiveId("player", n.playerUuid);
        n.locked = true;
        n.dynamicPlayer = true;
        return n;
    }

    public static SceneNode liveCamera(String playerUuid) {
        return liveCamera(playerUuid, null, null);
    }

    public static SceneNode liveCamera(String playerUuid, String playerName, SceneDocument document) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.CAMERA;
        n.name = "Camera";
        n.id = document != null
                ? SceneNodeIds.allocateLive(document, SceneNodeType.CAMERA, playerName)
                : fallbackLiveId("camera", playerUuid);
        n.locked = false;
        return n;
    }

    /** Personal non-positional audio bus under a live PLAYER. */
    public static SceneNode liveAudio(String playerUuid) {
        return liveAudio(playerUuid, null, null);
    }

    public static SceneNode liveAudio(String playerUuid, String playerName, SceneDocument document) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.AUDIO;
        n.name = "Audio";
        n.id = document != null
                ? SceneNodeIds.allocateLive(document, SceneNodeType.AUDIO, playerName)
                : fallbackLiveId("paudio", playerUuid);
        n.locked = false;
        n.audioVolume = 1f;
        n.audioPitch = 1f;
        n.audioEnabled = true;
        n.audioLoop = false;
        n.audioSound = "";
        return n;
    }

    private static String fallbackLiveId(String prefix, String uuid) {
        String compact = uuid == null ? "" : uuid.replace("-", "");
        return prefix + "_" + compact;
    }

    public static SceneNode skinOverlay(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.SKIN_OVERLAY;
        n.name = name == null || name.isBlank() ? "Overlay" : name;
        n.locked = false;
        return n;
    }

    public static SceneNode route(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.ROUTE;
        n.name = name;
        return n;
    }

    public static SceneNode routePoint(String name, double x, double y, double z) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.ROUTE_POINT;
        n.name = name;
        n.transform.px = x;
        n.transform.py = y;
        n.transform.pz = z;
        n.routeHasLook = false;
        return n;
    }

    public static SceneNode resBlock(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.RES_BLOCK;
        n.name = name;
        n.blockDef = new SceneBlockDef();
        return n;
    }

    public static SceneNode resItem(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.RES_ITEM;
        n.name = name;
        n.itemDef = new SceneItemDef();
        return n;
    }

    public static SceneNode resCraft(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.RES_CRAFT;
        n.name = name;
        n.craftDef = new SceneCraftDef();
        return n;
    }

    public static SceneNode resTab(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.RES_TAB;
        n.name = name;
        n.tabDef = new SceneTabDef();
        return n;
    }

    public static SceneNode resDrop(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.RES_DROP;
        n.name = name;
        n.dropDef = new SceneDropDef();
        return n;
    }

    public static SceneNode resWorld(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.RES_WORLD;
        n.name = name;
        n.worldDef = new SceneWorldDef();
        return n;
    }

    public static SceneNode resParticle(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.RES_PARTICLE;
        n.name = name;
        n.particleDef = new SceneParticleDef();
        return n;
    }

    public static SceneNode resProjectile(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.RES_PROJECTILE;
        n.name = name;
        n.projectileDef = new SceneProjectileDef();
        return n;
    }

    public static SceneNode npc(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.NPC;
        n.name = name;
        return n;
    }

    public static SceneNode model(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.MODEL;
        n.name = name;
        return n;
    }

    public static SceneNode billboard(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.BILLBOARD;
        n.name = name;
        return n;
    }

    public static SceneNode hitbox(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.HITBOX;
        n.name = name;
        n.hitboxWidth = 1f;
        n.hitboxHeight = 1f;
        n.hitboxDepth = 1f;
        n.showHitbox = true;
        return n;
    }

    public static SceneNode particle(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.PARTICLE;
        n.name = name;
        n.particleType = "minecraft:flame";
        n.particleShape = "point";
        n.particleEmitMode = "directional";
        n.particleCount = 12;
        n.particleSpeed = 0.2f;
        n.particleSpeedSpread = 0.25f;
        n.particleDirY = 1f;
        n.particleCone = 25f;
        n.particleLifetime = 40;
        n.particleFade = 10;
        n.particleCollide = false;
        n.particleLoop = true;
        n.particleInterval = 5;
        n.particleRadius = 1f;
        n.particleHeight = 2f;
        n.particleLineY = 2f;
        n.previewEnabled = true;
        n.particleSystem = org.zonarstudio.spraute_engine.particle.ParticleSystemSpec.defaults();
        n.particleSystem.renderer.texture = "minecraft:textures/particle/generic_0.png";
        n.particleSystem.emission.loop = true;
        n.particleSystem.emission.rate = 20f;
        return n;
    }

    /** Inline custom spec, optionally upgrading this node's legacy fields. */
    public org.zonarstudio.spraute_engine.particle.ParticleSystemSpec particles(boolean migrateLegacy) {
        if (particleSystem != null) return particleSystem.harden();
        if (!migrateLegacy) return null;
        var spec = org.zonarstudio.spraute_engine.particle.ParticleSystemSpec.defaults();
        spec.custom = false;
        spec.emission.loop = particleLoop;
        spec.emission.rate = 20f / Math.max(1, particleInterval) * Math.max(1, particleCount);
        spec.shape.type = particleShape;
        spec.shape.radius = particleRadius;
        spec.shape.height = particleHeight;
        spec.shape.lineX = particleLineX;
        spec.shape.lineY = particleLineY;
        spec.shape.lineZ = particleLineZ;
        spec.shape.dirX = particleDirX;
        spec.shape.dirY = particleDirY;
        spec.shape.dirZ = particleDirZ;
        spec.shape.angle = particleCone;
        spec.shape.spreadX = particleSpreadX;
        spec.shape.spreadY = particleSpreadY;
        spec.shape.spreadZ = particleSpreadZ;
        float speedSpread = Math.max(0f, particleSpeedSpread);
        spec.initial.speedMin = Math.max(0f, particleSpeed * (1f - speedSpread));
        spec.initial.speedMax = Math.max(spec.initial.speedMin, particleSpeed * (1f + speedSpread));
        float life = particleLifetime > 0 ? particleLifetime / 20f : 1.5f;
        spec.initial.lifetimeMin = life;
        spec.initial.lifetimeMax = life;
        spec.motion.gravityY = -Math.max(0f, particleGravity) * 0.04f;
        spec.collision.enabled = particleCollide;
        particleSystem = spec;
        return spec;
    }

    public static SceneNode light(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.LIGHT;
        n.name = name;
        n.lightLevel = 15;
        n.lightEnabled = true;
        n.showLight = true;
        return n;
    }

    public static SceneNode audioSource(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.AUDIO_SOURCE;
        n.name = name;
        n.audioSound = "minecraft:ambient.cave";
        n.audioShape = "sphere";
        n.audioFullRadius = 4f;
        n.audioMaxDistance = 16f;
        n.audioVolume = 1f;
        n.audioPitch = 1f;
        n.audioLoop = true;
        n.audioReplayOnEnter = false;
        n.audioEnabled = true;
        n.showAudio = true;
        n.previewEnabled = true;
        return n;
    }

    public static SceneNode postProcess(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.POST_PROCESS;
        n.name = name;
        n.post = new PostProcessSettings();
        n.post.global = true;
        n.showPost = false;
        n.previewEnabled = true;
        return n;
    }

    /**
     * Per-node transform check. Same as {@code type.hasTransform()} for every built-in type; a
     * CUSTOM node additionally has a real world transform when its Java-declared
     * {@link org.zonarstudio.spraute_engine.scene.custom.GenericNodeType} opts in via
     * {@code usesTransform()} — so position/rotation/scale, the editor gizmo, the Inspector
     * transform section, child world composition and script {@code node.x = …} all work for it
     * with no per-type code.
     */
    public boolean hasTransform() {
        if (type == SceneNodeType.CUSTOM) {
            return org.zonarstudio.spraute_engine.scene.custom.GenericNodeTypeRegistry.usesTransform(customTypeId);
        }
        return type != null && type.hasTransform();
    }

    public static SceneNode custom(String typeId, String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.CUSTOM;
        n.customTypeId = typeId == null ? "" : typeId;
        n.name = name;
        var def = org.zonarstudio.spraute_engine.scene.custom.CustomNodeRegistry.get(typeId);
        if (def != null) {
            n.customData = new java.util.LinkedHashMap<>(def.defaultData());
            if (name == null || name.isBlank()) n.name = def.displayLabel();
        } else {
            n.customData = new java.util.LinkedHashMap<>();
        }
        return n;
    }

    public static SceneNode empty(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.EMPTY;
        n.name = name;
        return n;
    }

    public static SceneNode animator(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.ANIMATOR;
        n.name = name;
        return n;
    }

    public static SceneNode canvas(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.CANVAS;
        n.name = name;
        n.uiColor = "#C0101010";
        // Canvases default to percent-of-screen units, like widgets default
        // to percent-of-canvas. Position 0/0 keeps the "centered" semantics.
        n.uiPercentX = true;
        n.uiPercentY = true;
        n.uiPercentW = true;
        n.uiPercentH = true;
        n.uiCanvasW = 70;
        n.uiCanvasH = 70;
        return n;
    }

    /**
     * A canvas drawn as a plane in the world ("3D canvas"): same node, same widgets, world
     * render mode. Unlike a screen canvas it is sized in fixed pixels (a world panel must not
     * depend on each player's window size), never auto-opens on anyone's screen, and its
     * transform is a world position rather than a screen offset.
     */
    public static SceneNode worldCanvas(String name) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.CANVAS;
        n.name = name;
        n.uiColor = "#C0101010";
        n.uiPercentX = false;
        n.uiPercentY = false;
        n.uiPercentW = false;
        n.uiPercentH = false;
        n.uiCanvasW = 320;
        n.uiCanvasH = 180;
        n.canvasWorldSpace = true;
        n.canvasAutoShow = false;
        return n;
    }

    public static SceneNode uiWidget(SceneNodeType type, String name) {
        SceneNode n = new SceneNode();
        n.type = type;
        n.name = name;
        // GUI works in percent-of-parent by default (px available via toggles).
        n.uiPercentX = true;
        n.uiPercentY = true;
        n.uiPercentW = true;
        n.uiPercentH = true;
        switch (type) {
            case UI_TEXT -> { n.uiText = "Текст"; n.uiColor = "#FFEAEAEA"; n.uiW = 40; n.uiH = 12; n.uiAutoWrap = true; n.uiWrapMode = "word"; }
            case UI_BUTTON -> { n.uiText = "Кнопка"; n.uiColor = "#55336688"; n.uiW = 33; n.uiH = 12; n.uiAutoWrap = true; n.uiWrapMode = "word"; n.uiAlign = "center"; }
            case UI_IMAGE -> { n.uiW = 11; n.uiH = 18; }
            case UI_VIDEO -> { n.uiW = 53; n.uiH = 50; }
            case UI_SVG -> { n.uiW = 11; n.uiH = 18; n.uiColor = "#FFFFFFFF"; }
            case UI_RECT -> { n.uiColor = "#80FFFFFF"; n.uiW = 21; n.uiH = 18; }
            case UI_INPUT -> { n.uiColor = "#FFFFFFFF"; n.uiW = 33; n.uiH = 9; }
            case UI_GRID -> { n.uiW = 70; n.uiH = 30; n.gridMode = "grid"; n.gridAlign = "top_left"; }
            case UI_SCROLL -> { n.uiColor = "#40000000"; n.uiW = 47; n.uiH = 50; n.uiContentH = 200; }
            case UI_DIVIDER -> { n.uiColor = "#44FFFFFF"; n.uiW = 40; n.uiH = 1; n.uiPercentH = false; }
            case UI_ITEM -> { n.uiW = 5; n.uiH = 9; }
            case UI_BLOCK -> { n.uiItem = "minecraft:stone"; n.uiW = 5; n.uiH = 9; }
            case UI_ENTITY -> { n.uiW = 21; n.uiH = 53; n.uiEntityAnchorX = 0.5f; n.uiEntityAnchorY = 0.5f; }
            case UI_CROP -> { n.uiW = 47; n.uiH = 50; }
            case UI_GROUP -> { n.uiW = 47; n.uiH = 50; }
            case UI_DRAG -> { n.uiW = 47; n.uiH = 50; }
            case UI_DROPTARGET -> { n.uiW = 47; n.uiH = 50; }
            // Slots are fixed 18x18 px (vanilla slot size); the inventory block
            // is 9×18 × (3×18+4+18) px. Percent position/size would bake against
            // REF_SCREEN and drift from the editor gizmo on real resolutions.
            case UI_SLOT -> {
                n.uiW = 18; n.uiH = 18;
                n.uiPercentX = false; n.uiPercentY = false;
                n.uiPercentW = false; n.uiPercentH = false;
            }
            case UI_INVENTORY -> {
                n.uiW = 162; n.uiH = 76;
                n.uiPercentX = false; n.uiPercentY = false;
                n.uiPercentW = false; n.uiPercentH = false;
            }
            default -> {}
        }
        return n;
    }

    /** Horizontal flow layout (UI_GRID in row mode, sizes from children). */
    public static SceneNode uiRow(String name) {
        SceneNode n = uiWidget(SceneNodeType.UI_GRID, name);
        n.gridMode = "row";
        n.gridHugContent = true;
        n.gridStretchCells = false;
        n.gridColumns = 1;
        n.gridFlow = "ltr";
        n.gridAlign = "top_left";
        n.uiW = 70;
        n.uiH = 14;
        return n;
    }

    /** Vertical flow layout (UI_GRID in column mode, sizes from children). */
    public static SceneNode uiColumn(String name) {
        SceneNode n = uiWidget(SceneNodeType.UI_GRID, name);
        n.gridMode = "column";
        n.gridHugContent = true;
        n.gridStretchCells = false;
        n.gridColumns = 1;
        n.gridFlow = "ttb";
        n.gridAlign = "top_left";
        n.uiW = 30;
        n.uiH = 50;
        return n;
    }

    public static SceneNode script(String name, String scriptName) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.SCRIPT;
        n.name = name;
        n.script = scriptName == null ? "" : scriptName;
        return n;
    }

    /** Visual script node: opens {@code .sprv} in the editor, runs sibling {@code .spr} at runtime. */
    public static SceneNode visualScript(String name, String scriptName) {
        SceneNode n = new SceneNode();
        n.type = SceneNodeType.VISUAL_SCRIPT;
        n.name = name;
        n.script = scriptName == null ? "" : scriptName;
        return n;
    }

    /**
     * Gson deserializes {@link #children} as {@link ArrayList}; live scenes share the tree
     * with the server tick (player sync) and the client render thread, so every list must
     * be a {@link CopyOnWriteArrayList} or walks throw {@link java.util.ConcurrentModificationException}.
     */
    public static void hardenChildrenLists(SceneNode node) {
        if (node == null) return;
        if (!(node.children instanceof CopyOnWriteArrayList)) {
            node.children = new CopyOnWriteArrayList<>(
                    node.children == null ? List.of() : node.children);
        }
        if (node.type == SceneNodeType.PLAYERS) ensurePlayerVarMaps(node);
        for (SceneNode child : node.children) {
            hardenChildrenLists(child);
        }
    }

    public SceneNode deepCopyWithNewIds() {
        SceneNode c = new SceneNode();
        c.type = type;
        c.name = name;
        c.tags = new ArrayList<>(tags == null ? List.of() : tags);
        c.transform = transform.copy();
        c.script = script;
        c.runOnSceneStart = runOnSceneStart;
        c.routeFile = type == SceneNodeType.ROUTE ? "" : routeFile;
        c.routeMode = routeMode;
        c.routeClosed = routeClosed;
        c.routeCurve = routeCurve;
        c.routeSmoothness = routeSmoothness;
        c.routeLayoutSpacing = routeLayoutSpacing;
        c.routeLayoutOffset = routeLayoutOffset;
        c.routeLayoutAnchor = routeLayoutAnchor;
        c.routeLayoutOverflow = routeLayoutOverflow;
        c.routeLayoutReverse = routeLayoutReverse;
        c.routeLayoutAlignYaw = routeLayoutAlignYaw;
        c.routeLayoutAlignPitch = routeLayoutAlignPitch;
        c.routeHasLook = routeHasLook;
        c.model = model;
        c.texture = texture;
        c.hiddenSkinLayers = hiddenSkinLayers;
        c.animation = animation;
        c.previewEnabled = previewEnabled;
        c.nameVisible = nameVisible;
        c.maxHealth = maxHealth;
        c.movementSpeed = movementSpeed;
        c.flyingSpeed = flyingSpeed;
        c.flySmoothing = flySmoothing;
        c.walkTurnSpeed = walkTurnSpeed;
        c.idleTurnSpeed = idleTurnSpeed;
        c.pathArriveRadius = pathArriveRadius;
        c.pathArriveVert = pathArriveVert;
        c.pathStepHeight = pathStepHeight;
        c.pathMaxJump = pathMaxJump;
        c.pathMaxDrop = pathMaxDrop;
        c.pathMaxRange = pathMaxRange;
        c.pathDiagonal = pathDiagonal;
        c.pathAvoidWater = pathAvoidWater;
        c.pathAvoidLava = pathAvoidLava;
        c.pathMaxNodes = pathMaxNodes;
        c.pathSnapOnArrive = pathSnapOnArrive;
        c.flyArriveRadius = flyArriveRadius;
        c.flyMaxRange = flyMaxRange;
        c.flyDiagonal = flyDiagonal;
        c.flyAvoidWater = flyAvoidWater;
        c.flyAvoidLava = flyAvoidLava;
        c.flyMaxNodes = flyMaxNodes;
        c.flySnapOnArrive = flySnapOnArrive;
        c.invulnerable = invulnerable;
        c.silent = silent;
        c.glowing = glowing;
        c.translucent = translucent;
        c.opacity = opacity;
        c.lodEnabled = lodEnabled;
        c.renderDistance = renderDistance;
        c.forceVisible = forceVisible;
        c.loadChunks = loadChunks;
        c.noGravity = noGravity;
        c.collision = collision;
        c.flying = flying;
        c.swimming = swimming;
        c.group = group;
        c.playerSkinOverlay = playerSkinOverlay;
        c.headBone = headBone;
        c.unlimitedHeadLook = unlimitedHeadLook;
        c.nameYOffset = nameYOffset;
        c.hitboxWidth = hitboxWidth;
        c.hitboxHeight = hitboxHeight;
        c.hitboxDepth = hitboxDepth;
        c.hitboxOffsetX = hitboxOffsetX;
        c.hitboxOffsetY = hitboxOffsetY;
        c.hitboxOffsetZ = hitboxOffsetZ;
        c.hitboxSolid = hitboxSolid;
        c.hitboxTrigger = hitboxTrigger;
        c.hitboxTriggerId = hitboxTriggerId;
        c.showHitbox = showHitbox;
        c.idleAnimation = idleAnimation;
        c.walkAnimation = walkAnimation;
        c.flyIdleAnimation = flyIdleAnimation;
        c.flyAnimation = flyAnimation;
        c.swimIdleAnimation = swimIdleAnimation;
        c.swimAnimation = swimAnimation;
        c.deathAnimation = deathAnimation;
        c.attackRange = attackRange;
        c.attackCooldown = attackCooldown;
        c.attackHitDelay = attackHitDelay;
        c.attackHand = attackHand;
        c.attackAnimations = attackAnimations;
        c.drops = new ArrayList<>();
        if (drops != null) {
            for (var r : drops) {
                if (r != null) c.drops.add(r.copy());
            }
        }
        c.dropXp = dropXp;
        c.billboardWidth = billboardWidth;
        c.billboardHeight = billboardHeight;
        c.billboardSeeThrough = billboardSeeThrough;
        c.particleType = particleType;
        c.particleShape = particleShape;
        c.particleEmitMode = particleEmitMode;
        c.particleCount = particleCount;
        c.particleSpreadX = particleSpreadX;
        c.particleSpreadY = particleSpreadY;
        c.particleSpreadZ = particleSpreadZ;
        c.particleSpeed = particleSpeed;
        c.particleSpeedSpread = particleSpeedSpread;
        c.particleGravity = particleGravity;
        c.particleCollide = particleCollide;
        c.particleDirX = particleDirX;
        c.particleDirY = particleDirY;
        c.particleDirZ = particleDirZ;
        c.particleCone = particleCone;
        c.particleLifetime = particleLifetime;
        c.particleFade = particleFade;
        c.particleLoop = particleLoop;
        c.particleInterval = particleInterval;
        c.particleRadius = particleRadius;
        c.particleHeight = particleHeight;
        c.particleLineX = particleLineX;
        c.particleLineY = particleLineY;
        c.particleLineZ = particleLineZ;
        c.particleSystem = particleSystem == null ? null : particleSystem.copy();
        c.particleEffect = particleEffect == null ? null : particleEffect.copy();
        c.lightLevel = lightLevel;
        c.lightEnabled = lightEnabled;
        c.showLight = showLight;
        c.audioSound = audioSound == null ? "" : audioSound;
        c.audioShape = audioShape == null || audioShape.isBlank() ? "sphere" : audioShape;
        c.audioFullRadius = audioFullRadius;
        c.audioMaxDistance = audioMaxDistance;
        c.audioVolume = audioVolume;
        c.audioPitch = audioPitch;
        c.audioLoop = audioLoop;
        c.audioReplayOnEnter = audioReplayOnEnter;
        c.audioEnabled = audioEnabled;
        c.showAudio = showAudio;
        ensurePlayerVarMaps(this);
        c.playerDataDefaults = new java.util.LinkedHashMap<>(playerDataDefaults);
        c.playerSavedDefaults = new java.util.LinkedHashMap<>(playerSavedDefaults);
        c.cameraRestX = cameraRestX;
        c.cameraRestY = cameraRestY;
        c.cameraRestZ = cameraRestZ;
        c.cameraRestYaw = cameraRestYaw;
        c.cameraRestPitch = cameraRestPitch;
        c.cameraFov = cameraFov;
        c.cameraDistance = cameraDistance;
        c.cameraLockYaw = cameraLockYaw;
        c.cameraLockPitch = cameraLockPitch;
        c.cameraFollowPlayer = cameraFollowPlayer;
        c.cameraLockX = cameraLockX;
        c.cameraLockY = cameraLockY;
        c.cameraLockZ = cameraLockZ;
        c.cameraWorldX = cameraWorldX;
        c.cameraWorldY = cameraWorldY;
        c.cameraWorldZ = cameraWorldZ;
        c.cameraOrbit = cameraOrbit;
        c.cameraOrbitYaw = cameraOrbitYaw;
        c.cameraOrbitPitch = cameraOrbitPitch;
        c.cameraPivotX = cameraPivotX;
        c.cameraPivotY = cameraPivotY;
        c.cameraPivotZ = cameraPivotZ;
        c.cameraCollide = cameraCollide;
        c.cameraShowHand = cameraShowHand;
        c.cameraHideGui = cameraHideGui;
        c.cameraMoveRelative = cameraMoveRelative;
        c.cameraSeparateTurn = cameraSeparateTurn;
        c.cameraTurnSmooth = cameraTurnSmooth;
        c.cameraScreenRaycast = cameraScreenRaycast;
        c.cameraLookAtInteract = cameraLookAtInteract;
        c.cameraClearOcclusion = cameraClearOcclusion;
        c.cameraOcclusionAlpha = cameraOcclusionAlpha;
        c.cameraOcclusionRadius = cameraOcclusionRadius;
        c.cameraDisableEffects = cameraDisableEffects;
        c.cameraWalkBob = cameraWalkBob;
        c.cameraWalkBobAmount = cameraWalkBobAmount;
        c.post = post().copy();
        c.showPost = showPost;
        c.blockDef = blockDef == null ? null : blockDef.copy();
        c.itemDef = itemDef == null ? null : itemDef.copy();
        c.craftDef = craftDef == null ? null : craftDef.copy();
        c.tabDef = tabDef == null ? null : tabDef.copy();
        c.dropDef = dropDef == null ? null : dropDef.copy();
        c.worldDef = worldDef == null ? null : worldDef.copy();
        c.particleDef = particleDef == null ? null : particleDef.copy();
        c.projectileDef = projectileDef == null ? null : projectileDef.copy();
        c.customTypeId = customTypeId;
        c.customData = deepCopyCustomData(customData);
        c.animatorFile = animatorFile;
        c.animatorClip = animatorClip;
        c.uiW = uiW;
        c.uiH = uiH;
        c.uiCanvasW = uiCanvasW;
        c.uiCanvasH = uiCanvasH;
        c.uiColor = uiColor;
        c.uiHoverColor = uiHoverColor;
        c.uiGradient = uiGradient;
        c.uiGradientStart = uiGradientStart;
        c.uiGradientDir = uiGradientDir;
        c.uiGradientAngle = uiGradientAngle;
        c.uiGradientStops = uiGradientStops;
        c.uiRadius = uiRadius;
        c.uiBorder = uiBorder;
        c.uiBorderColor = uiBorderColor;
        c.uiBorderGradient = uiBorderGradient;
        c.uiBorderGradientAngle = uiBorderGradientAngle;
        c.uiOuterBorder = uiOuterBorder;
        c.uiOuterBorderColor = uiOuterBorderColor;
        c.uiOuterBorderGradient = uiOuterBorderGradient;
        c.uiOuterBorderGradientAngle = uiOuterBorderGradientAngle;
        c.uiShadow = uiShadow;
        c.uiText = uiText;
        c.uiTextScale = uiTextScale;
        c.uiTextOffsetX = uiTextOffsetX;
        c.uiTextOffsetY = uiTextOffsetY;
        c.uiTextEffects = uiTextEffects;
        c.uiTextGradient = uiTextGradient;
        c.uiTextOutlineColor = uiTextOutlineColor;
        c.uiTextGlowColor = uiTextGlowColor;
        c.uiTextEffectSpeed = uiTextEffectSpeed;
        c.uiTextEffectAmount = uiTextEffectAmount;
        c.uiTextGlowSize = uiTextGlowSize;
        c.uiTextOutlineWidth = uiTextOutlineWidth;
        c.uiTextUnderlineColor = uiTextUnderlineColor;
        c.uiTextUnderlineThickness = uiTextUnderlineThickness;
        c.uiTextUnderlineOffset = uiTextUnderlineOffset;
        c.uiTextShadowColor = uiTextShadowColor;
        c.uiTextShadowOffsetX = uiTextShadowOffsetX;
        c.uiTextShadowOffsetY = uiTextShadowOffsetY;
        c.uiTextShadowSize = uiTextShadowSize;
        c.uiAlign = uiAlign;
        c.uiWrap = uiWrap;
        c.uiAutoWrap = uiAutoWrap;
        c.uiWrapMode = uiWrapMode;
        c.uiTexture = uiTexture;
        c.uiVideo = uiVideo;
        c.uiVideoEnabled = uiVideoEnabled;
        c.uiVideoPlaying = uiVideoPlaying;
        c.uiVideoLoop = uiVideoLoop;
        c.uiSvgStroke = uiSvgStroke;
        c.uiSvgStrokeWidth = uiSvgStrokeWidth;
        c.uiSvgRecolor = uiSvgRecolor;
        c.uiPlaceholder = uiPlaceholder;
        c.uiTooltip = uiTooltip;
        c.uiMaxChars = uiMaxChars;
        c.uiPercentX = uiPercentX;
        c.uiPercentY = uiPercentY;
        c.uiPercentW = uiPercentW;
        c.uiPercentH = uiPercentH;
        c.uiContentH = uiContentH;
        c.uiScrollbar = uiScrollbar;
        c.uiItem = uiItem;
        c.uiEntityModel = uiEntityModel;
        c.uiEntityTexture = uiEntityTexture;
        c.uiEntityAnim = uiEntityAnim;
        c.uiEntityScale = uiEntityScale;
        c.uiEntityMode = uiEntityMode;
        c.uiEntityRef = uiEntityRef;
        c.uiEntityAlways = uiEntityAlways;
        c.uiEntityAnchorX = uiEntityAnchorX;
        c.uiEntityAnchorY = uiEntityAnchorY;
        c.uiEntityFeetCrop = uiEntityFeetCrop;
        c.active = active;
        c.locked = locked;
        c.prefabId = prefabId == null ? "" : prefabId;
        c.uiAlpha = uiAlpha;
        c.uiSliceBorders = uiSliceBorders;
        c.uiSliceScale = uiSliceScale;
        c.uiSrcU = uiSrcU;
        c.uiSrcV = uiSrcV;
        c.uiSrcW = uiSrcW;
        c.uiSrcH = uiSrcH;
        c.uiCropShape = uiCropShape == null || uiCropShape.isBlank() ? "rect" : uiCropShape;
        c.uiCropFeather = uiCropFeather;
        c.uiCropFeatherL = uiCropFeatherL;
        c.uiCropFeatherT = uiCropFeatherT;
        c.uiCropFeatherR = uiCropFeatherR;
        c.uiCropFeatherB = uiCropFeatherB;
        c.uiCropFeatherSmooth = uiCropFeatherSmooth;
        c.uiFill = uiFill == null ? "" : uiFill;
        c.uiFillAmount = uiFillAmount;
        c.uiFillOrigin = uiFillOrigin == null || uiFillOrigin.isBlank() ? "bottom" : uiFillOrigin;
        c.uiChildAlign = uiChildAlign == null || uiChildAlign.isBlank() ? "top_left" : uiChildAlign;
        c.uiDragEnabled = uiDragEnabled;
        c.uiDragAxis = uiDragAxis == null || uiDragAxis.isBlank() ? "xy" : uiDragAxis;
        c.uiDragGroup = uiDragGroup == null ? "" : uiDragGroup;
        c.uiDragPayload = uiDragPayload == null ? "" : uiDragPayload;
        c.uiDragConstrain = uiDragConstrain;
        c.uiDragRevert = uiDragRevert;
        c.uiDragThreshold = uiDragThreshold;
        c.uiDropEnabled = uiDropEnabled;
        c.uiDropGroup = uiDropGroup == null ? "" : uiDropGroup;
        c.canvasOverlay = canvasOverlay;
        c.canvasLayer = canvasLayer;
        c.canvasAutoShow = canvasAutoShow;
        c.canvasCanClose = canvasCanClose;
        c.canvasDim = canvasDim;
        c.canvasWorldSpace = canvasWorldSpace;
        c.canvasWorldWidth = canvasWorldWidth;
        c.canvasWorldHeight = canvasWorldHeight;
        c.canvasWorldOrientation = canvasWorldOrientation == null || canvasWorldOrientation.isBlank()
                ? "billboard" : canvasWorldOrientation;
        c.canvasWorldResolution = canvasWorldResolution;
        c.canvasWorldMaxDistance = canvasWorldMaxDistance;
        c.canvasWorldRefresh = canvasWorldRefresh;
        c.gridColumns = gridColumns;
        c.gridCellW = gridCellW;
        c.gridCellH = gridCellH;
        c.gridGapX = gridGapX;
        c.gridGapY = gridGapY;
        c.gridStretchCells = gridStretchCells;
        c.gridMode = gridMode == null || gridMode.isBlank() ? "grid" : gridMode;
        c.gridAlign = gridAlign == null || gridAlign.isBlank() ? "top_left" : gridAlign;
        c.gridFlow = gridFlow == null || gridFlow.isBlank() ? "ltr" : gridFlow;
        c.gridHugContent = gridHugContent;
        for (SceneNode child : children) {
            c.children.add(child.deepCopyWithNewIds());
        }
        return c;
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> deepCopyCustomData(java.util.Map<String, Object> src) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (src == null) return out;
        for (var e : src.entrySet()) {
            Object v = e.getValue();
            if (v instanceof java.util.Map<?, ?> map) {
                java.util.Map<String, Object> nested = new java.util.LinkedHashMap<>();
                for (var ne : map.entrySet()) {
                    nested.put(String.valueOf(ne.getKey()), ne.getValue());
                }
                out.put(e.getKey(), nested);
            } else if (v instanceof java.util.List<?> list) {
                java.util.List<Object> copy = new java.util.ArrayList<>();
                for (Object item : list) {
                    if (item instanceof java.util.Map<?, ?> im) {
                        java.util.Map<String, Object> nested = new java.util.LinkedHashMap<>();
                        for (var ne : im.entrySet()) {
                            nested.put(String.valueOf(ne.getKey()), ne.getValue());
                        }
                        copy.add(nested);
                    } else {
                        copy.add(item);
                    }
                }
                out.put(e.getKey(), copy);
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }
}
