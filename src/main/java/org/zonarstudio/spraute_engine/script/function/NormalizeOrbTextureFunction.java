package org.zonarstudio.spraute_engine.script.function;

import net.minecraft.commands.CommandSourceStack;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.util.SprauteOrbTextures;

import java.util.List;

public class NormalizeOrbTextureFunction implements ScriptFunction {
    @Override
    public String getName() {
        return "normalizeOrbTexture";
    }

    @Override
    public int getArgCount() {
        return 1;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[]{Object.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.isEmpty() || args.get(0) == null) return "";
        return SprauteOrbTextures.normalize(String.valueOf(args.get(0)));
    }
}
