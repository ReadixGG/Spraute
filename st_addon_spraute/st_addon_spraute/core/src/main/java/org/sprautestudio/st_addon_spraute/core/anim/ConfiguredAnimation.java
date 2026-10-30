package org.sprautestudio.st_addon_spraute.core.anim;

/** An animation with the way to play it: fade, speed, what happens at its end. */
public final class ConfiguredAnimation {
    private final Animation animation;
    private float transition = 0.25f;
    private float speed = 1;
    private LoopMode loop;
    private float startTime;

    ConfiguredAnimation(Animation animation) {
        this.animation = animation;
        this.loop = animation.loops() ? LoopMode.LOOP : animation.holdsLast() ? LoopMode.HOLD_ON_LAST_FRAME : LoopMode.ONCE;
    }

    public Animation animation() {
        return animation;
    }

    public ConfiguredAnimation transitionTime(float seconds) {
        transition = Math.max(0, seconds);
        return this;
    }

    public float transitionTime() {
        return transition;
    }

    /** Seconds into the clip it starts from. */
    public ConfiguredAnimation startTime(float seconds) {
        startTime = Math.max(0, seconds);
        return this;
    }

    public float startTime() {
        return startTime;
    }

    public ConfiguredAnimation speed(float s) {
        speed = s > 0 ? s : 1;
        return this;
    }

    public float speed() {
        return speed;
    }

    public ConfiguredAnimation loopMode(LoopMode mode) {
        loop = mode;
        return this;
    }

    public LoopMode loopMode() {
        return loop;
    }
}
