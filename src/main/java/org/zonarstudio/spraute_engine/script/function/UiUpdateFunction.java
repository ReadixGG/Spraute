package org.zonarstudio.spraute_engine.script.function;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.zonarstudio.spraute_engine.network.ModNetwork;
import org.zonarstudio.spraute_engine.network.UpdateSprauteUiWidgetPacket;
import org.zonarstudio.spraute_engine.script.ScriptContext;

import java.util.List;

/**
 * ui_update(player, widget_id, field, value) — обновить свойство виджета на клиенте (S2C).
 * Поля: text, color, scale (text); label, color, hover, texture (button); color, fill, fillAmount, fillOrigin, cropShape (rect/panel);
 * texture, src, cropShape, fill, fillAmount, fillOrigin (image); scale, feet_crop, crop, anchor_x, anchor_y, viewport (entity);
 * feather, featherL/T/R/B, featherSmooth (clip / UI_CROP);
 * axis, group, payload, constrain, revert, threshold, enabled (drag / drop).
 * source, playerEnabled, playing, loop, action (video);
 * source, color, stroke, strokeWidth, recolor (svg).
 */
public class UiUpdateFunction implements ScriptFunction {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String getName() {
        return "uiUpdate";
    }

    @Override
    public int getArgCount() {
        return 4;
    }

    @Override
    public Class<?>[] getArgTypes() {
        return new Class<?>[]{Object.class, Object.class, String.class, String.class};
    }

    @Override
    public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
        if (args.size() < 4 || source.getLevel() == null) return null;
        Player player = resolvePlayer(args.get(0), source);
        if (!(player instanceof ServerPlayer sp)) {
            LOGGER.warn("[Script] ui_update: not a server player");
            return null;
        }
        String widgetId = widgetIdOf(args.get(1));
        String field = args.get(2) == null ? "" : String.valueOf(args.get(2));
        String value = args.get(3) == null ? "" : String.valueOf(args.get(3));
        if (widgetId.isEmpty()) return null;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                new UpdateSprauteUiWidgetPacket(widgetId, field, value));
        return null;
    }

    /** UI-нода сцены отдаёт свой widget id (= идентификатор ноды), иначе — строка как есть. */
    static String widgetIdOf(Object arg) {
        if (arg instanceof org.zonarstudio.spraute_engine.scene.SceneNodeView view) {
            return view.getNodeId();
        }
        return arg == null ? "" : String.valueOf(arg);
    }

    private static Player resolvePlayer(Object target, CommandSourceStack source) {
        if (target instanceof Player p) return p;
        if (target instanceof String name && source.getLevel() != null) {
            return source.getLevel().getServer().getPlayerList().getPlayerByName(name);
        }
        return null;
    }
}
