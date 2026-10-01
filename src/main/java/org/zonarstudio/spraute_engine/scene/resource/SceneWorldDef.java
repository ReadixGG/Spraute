package org.zonarstudio.spraute_engine.scene.resource;

/**
 * Scene payload mirroring {@code create world} — custom void dimension with sky/fog/clouds.
 */
public final class SceneWorldDef {
    public String id = "";
    /** Hex sky color, e.g. {@code #1b2838} or {@code 1b2838}. */
    public String sky = "#87CEEB";
    /** Hex fog color; blank = auto-darken from sky. */
    public String fog = "";
    /** Ambient light 0–1 (dimension type). */
    public float ambient = 0.4f;
    /** Fixed time tick 0–23999; {@code -1} = day/night cycle. */
    public long fixedTime = 6000L;
    public boolean skylight = true;
    /** When false — no clouds. */
    public boolean clouds = true;

    public SceneWorldDef copy() {
        SceneWorldDef c = new SceneWorldDef();
        c.id = id;
        c.sky = sky;
        c.fog = fog;
        c.ambient = ambient;
        c.fixedTime = fixedTime;
        c.skylight = skylight;
        c.clouds = clouds;
        return c;
    }
}
