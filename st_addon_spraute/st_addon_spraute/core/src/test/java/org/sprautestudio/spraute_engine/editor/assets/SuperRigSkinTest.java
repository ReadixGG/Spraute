package org.sprautestudio.spraute_engine.editor.assets;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SuperRigSkinTest {
    @Test
    void convertsMinecraftSkinIntoSuperRigAtlas() {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) skin.setRGB(x, y, 0xFFCC9977);
        skin.setRGB(9, 12, 0xFFFFFFFF);
        skin.setRGB(10, 12, 0xFF223344);
        skin.setRGB(13, 12, 0xFF445566);
        skin.setRGB(14, 12, 0xFFFFFFFF);
        skin.setRGB(9, 11, 0xFF332211);
        skin.setRGB(20, 20, 0xFF778899);
        FaceSkin.Options options = new FaceSkin.Options();
        options.target = FaceSkin.Target.SUPER_RIG;
        options.eyeH = 1;
        options.offset = 3;

        BufferedImage result = FaceSkin.convert(skin, options);

        assertEquals(128, result.getWidth());
        assertEquals(128, result.getHeight());
        assertEquals(0xFF778899, result.getRGB(20, 20));
        assertEquals(0xFFFFFFFF, result.getRGB(117, 62));
        assertEquals(0xFF445566, result.getRGB(121, 1));
        assertEquals(0xFF223344, result.getRGB(125, 1));
        assertEquals(0xFF332211, result.getRGB(0, 0));
        assertNotEquals(0, result.getRGB(4, 6));
        assertEquals(0xFFCC9977, result.getRGB(9, 12));
    }

    @Test
    void preservesExistingSuperRigFaceAtlas() {
        BufferedImage skin = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) skin.setRGB(x, y, 0xFFCC9977);
        skin.setRGB(117, 62, 0xFFABCDEF);
        skin.setRGB(121, 1, 0xFF102030);
        skin.setRGB(0, 0, 0xFF123456);
        skin.setRGB(4, 6, 0xFF654321);
        FaceSkin.Options options = new FaceSkin.Options();
        options.target = FaceSkin.Target.SUPER_RIG;
        options.eraseFace = false;

        BufferedImage result = FaceSkin.convert(skin, options);

        assertEquals(0xFFCC9977, result.getRGB(9, 12));
        assertEquals(0xFFABCDEF, result.getRGB(117, 62));
        assertEquals(0xFF102030, result.getRGB(121, 1));
        assertEquals(0xFF123456, result.getRGB(0, 0));
        assertEquals(0xFF654321, result.getRGB(4, 6));
    }
}
