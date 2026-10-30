package org.sprautestudio.st_addon_spraute.core.anim;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Named emotions: each is a face clip. A model's own clip "emotion_<name>" comes first, then the default model's. */
public final class Emotions {
    private Emotions() {
    }

    private static final Map<String, String> CLIPS = new LinkedHashMap<>();

    static {
        CLIPS.put("NEUTRAL", "");
        CLIPS.put("ANGER", "angry");
        CLIPS.put("MILD_ANGER", "emo_mild_anger");
        CLIPS.put("JOY", "happy");
        CLIPS.put("LAUGH", "laugh");
        CLIPS.put("GIGGLE", "emo_giggle");
        CLIPS.put("SAD", "sad");
        CLIPS.put("VERY_SAD", "emo_very_sad");
        CLIPS.put("DISGUST", "disgust");
        CLIPS.put("DISTRUST", "issue");
        CLIPS.put("SUSPICION", "emo_suspicion");
        CLIPS.put("SHAME", "emo_shame");
        CLIPS.put("FEAR", "emo_fear");
        CLIPS.put("SHOCK", "gasp");
        CLIPS.put("SURPRISE", "emo_surprise");
        CLIPS.put("CURIOSITY", "emo_curious");
        CLIPS.put("THOUGHT", "emo_thought");
        CLIPS.put("REALIZATION", "emo_realize");
        CLIPS.put("RESENTMENT", "emo_resentment");
        CLIPS.put("OFFENDED", "emo_offended");
        CLIPS.put("PRIDE", "emo_pride");
        CLIPS.put("PLAYFUL", "emo_playful");
        CLIPS.put("CUTE", "emo_cute");
        CLIPS.put("MAD", "emo_mad");
    }

    public static final List<String> NAMES = List.copyOf(CLIPS.keySet());

    /** The clip name in the default model, "" — no clip; null — no such emotion. */
    public static String clip(String name) {
        return CLIPS.get(name.toUpperCase(java.util.Locale.ROOT));
    }
}
