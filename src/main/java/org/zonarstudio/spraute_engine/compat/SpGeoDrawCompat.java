package org.zonarstudio.spraute_engine.compat;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.ShaderInstance;
//? if >=1.20.1 {
import org.joml.Matrix4f;
//?} else {
/*import com.mojang.math.Matrix4f;
*///?}

/** The only version-sensitive surface used by the static geo VBO backend. */
public final class SpGeoDrawCompat {
    private SpGeoDrawCompat() {}

    public static void upload(VertexBuffer buffer, BufferBuilder.RenderedBuffer data) {
        buffer.bind();
        buffer.upload(data);
        VertexBuffer.unbind();
    }

    public static void draw(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection,
                            ShaderInstance shader) {
        buffer.bind();
        buffer.drawWithShader(modelView,projection,shader);
        VertexBuffer.unbind();
    }
}
