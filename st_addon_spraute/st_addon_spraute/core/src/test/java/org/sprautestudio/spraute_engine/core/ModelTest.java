package org.sprautestudio.spraute_engine.core;

import org.junit.jupiter.api.Test;
import org.sprautestudio.spraute_engine.core.math.SpQuaternion;
import org.sprautestudio.spraute_engine.core.math.SpVec3;
import org.sprautestudio.spraute_engine.core.model.SpGeoModel;
import org.sprautestudio.spraute_engine.core.parser.SpAnimationParser;
import org.sprautestudio.spraute_engine.core.parser.SpGeoParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelTest {
    private static final String GEO = """
            {"format_version":"1.12.0","minecraft:geometry":[{
              "description":{"identifier":"geometry.t","texture_width":64,"texture_height":32},
              "bones":[
                {"name":"root","pivot":[0,0,0]},
                {"name":"arm","parent":"root","pivot":[4,22,0],"cubes":[{"origin":[4,12,-2],"size":[4,12,4],"uv":[40,16]}]}
              ]}]}
            """;

    private static final String ANIM = """
            {"format_version":"1.8.0","animations":{"walk":{"loop":true,"animation_length":1.0,"bones":{
              "arm":{"rotation":{"0.0":[0,0,0],"1.0":[90,0,0]}}}}}}
            """;

    @Test
    void aGeoBecomesABoneTree() {
        SpGeoModel m = SpGeoParser.parse(GEO);
        assertEquals(64, m.textureWidth);
        assertEquals(32, m.textureHeight);
        assertEquals(1, m.rootBones.size());
        assertTrue(m.boneMap.containsKey("arm"));
        assertNotNull(m.baked);
    }

    @Test
    void clipsAreFoundWhateverTheCase() {
        SpAnimationParser.AnimationSet set = SpAnimationParser.parse(ANIM);
        assertNotNull(set.get("walk"));
        assertNotNull(set.get("WALK"));
        assertNull(set.get(""));
    }

    @Test
    void aQuarterTurnAroundYTurnsXToMinusZ() {
        SpVec3 v = new SpQuaternion().setAxisAngle(0, 1, 0, 90).rotate(new SpVec3(1, 0, 0));
        assertEquals(0, v.x, 1e-5);
        assertEquals(0, v.y, 1e-5);
        assertEquals(-1, v.z, 1e-5);
    }
}
