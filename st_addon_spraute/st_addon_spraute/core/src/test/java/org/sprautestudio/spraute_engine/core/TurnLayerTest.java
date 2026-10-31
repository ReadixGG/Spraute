package org.sprautestudio.spraute_engine.core;

import org.junit.jupiter.api.Test;
import org.sprautestudio.spraute_engine.core.anim.Animation;
import org.sprautestudio.spraute_engine.core.anim.AnimationSystem;
import org.sprautestudio.spraute_engine.core.model.SpModelInstance;
import org.sprautestudio.spraute_engine.core.parser.SpAnimationParser;
import org.sprautestudio.spraute_engine.core.parser.SpGeoParser;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TurnLayerTest {
    @Test
    void turningLegsRemainVisibleDuringAnAction() {
        var geometry = SpGeoParser.parse("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.turn","texture_width":16,"texture_height":16},
                  "bones":[{"name":"LeftLeg","pivot":[0,8,0]}]}]}
                """);
        var clips = SpAnimationParser.parse("""
                {"format_version":"1.8.0","animations":{
                  "action":{"animation_length":1,"bones":{"LeftLeg":{"rotation":[0,0,0]}}},
                  "turn":{"loop":true,"animation_length":1,"bones":{"LeftLeg":{"rotation":[0,15,0]}}}}}
                """);
        var system = new AnimationSystem();
        system.startAnimationAt("action", new Animation("action", clips.get("action")).configure().transitionTime(0));
        system.startAnimationAt("turn", new Animation("turn", clips.get("turn")).configure().transitionTime(0));
        var model = new SpModelInstance(geometry);

        system.apply(model);

        assertEquals(15, model.animRotation("LeftLeg").y, 0.001);
    }
}
