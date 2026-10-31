package org.sprautestudio.spraute_engine.core.anim;

import org.sprautestudio.spraute_engine.core.parser.SpAnimationParser;

/** One clip of an animation file, known by an id both sides can resolve (see Anims.resolve). */
public final class Animation {
    /** Nothing: starting it on a layer lets the layer go. */
    public static final Animation NULL = new Animation("", null);

    private final String id;
    private final SpAnimationParser.AnimationClip clip;

    public Animation(String id, SpAnimationParser.AnimationClip clip) {
        this.id = id;
        this.clip = clip;
    }

    public String id() {
        return id;
    }

    public SpAnimationParser.AnimationClip clip() {
        return clip;
    }

    public boolean isNull() {
        return clip == null;
    }

    public double lengthSeconds() {
        return clip != null ? clip.getLengthSec() : 0;
    }

    public boolean loops() {
        return clip != null && clip.isLoop();
    }

    public boolean holdsLast() {
        return clip != null && clip.holdsLast;
    }

    public ConfiguredAnimation configure() {
        return new ConfiguredAnimation(this);
    }
}
