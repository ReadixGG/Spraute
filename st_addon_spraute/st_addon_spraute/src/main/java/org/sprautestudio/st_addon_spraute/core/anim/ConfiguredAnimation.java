package org.sprautestudio.st_addon_spraute.core.anim;

/** An animation with the way to play it: fade, speed, what happens at its end. */
public final class ConfiguredAnimation {
    private final Animation animation;
    private float transition = 0.25f;
    private float speed = 1;
    private LoopMode loop;

    ConfiguredAnimation(Animation animation) {
        this.animation = animation;
        this.loop = animation.loops() ? LoopMode.LOOP : LoopMode.ONCE;
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
