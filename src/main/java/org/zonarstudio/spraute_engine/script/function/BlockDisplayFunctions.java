package org.zonarstudio.spraute_engine.script.function;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.zonarstudio.spraute_engine.registry.CustomGeoBlockEntity;
import org.zonarstudio.spraute_engine.script.ScriptContext;
import org.zonarstudio.spraute_engine.ui.SprauteUiJson;
import org.zonarstudio.spraute_engine.ui.UiTemplate;

import java.util.List;

public class BlockDisplayFunctions {

    public static class SetBlockDisplay implements ScriptFunction {
        @Override
        public String getName() {
            return "setBlockDisplay";
        }

        @Override
        public int getArgCount() {
            return 12;
        }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[]{
                Object.class, Object.class, Object.class, // x, y, z
                Object.class, Object.class, // id, item_id
                Object.class, Object.class, Object.class, // ox, oy, oz
                Object.class, Object.class, Object.class, // rx, ry, rz
                Object.class // scale
            };
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 12 || source.getLevel() == null) return null;
            try {
                int x = ((Number) args.get(0)).intValue();
                int y = ((Number) args.get(1)).intValue();
                int z = ((Number) args.get(2)).intValue();
                String id = String.valueOf(args.get(3));
                String itemId = String.valueOf(args.get(4));
                float ox = ((Number) args.get(5)).floatValue();
                float oy = ((Number) args.get(6)).floatValue();
                float oz = ((Number) args.get(7)).floatValue();
                float rx = ((Number) args.get(8)).floatValue();
                float ry = ((Number) args.get(9)).floatValue();
                float rz = ((Number) args.get(10)).floatValue();
                float scale = ((Number) args.get(11)).floatValue();

                BlockEntity be = source.getLevel().getBlockEntity(new BlockPos(x, y, z));
                if (be instanceof CustomGeoBlockEntity cgbe) {
                    cgbe.displays.put(id, new CustomGeoBlockEntity.BlockDisplay(0, itemId, "", ox, oy, oz, rx, ry, rz, scale));
                    cgbe.setChanged();
                    source.getLevel().sendBlockUpdated(cgbe.getBlockPos(), cgbe.getBlockState(), cgbe.getBlockState(), 3);
                }
            } catch (Exception e) {}
            return null;
        }
    }

    public static class SetBlockDisplayModel implements ScriptFunction {
        @Override
        public String getName() {
            return "setBlockDisplayModel";
        }

        @Override
        public int getArgCount() {
            return 13;
        }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[]{
                Object.class, Object.class, Object.class, // x, y, z
                Object.class, Object.class, Object.class, // id, model_path, texture_path
                Object.class, Object.class, Object.class, // ox, oy, oz
                Object.class, Object.class, Object.class, // rx, ry, rz
                Object.class // scale
            };
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 13 || source.getLevel() == null) return null;
            try {
                int x = ((Number) args.get(0)).intValue();
                int y = ((Number) args.get(1)).intValue();
                int z = ((Number) args.get(2)).intValue();
                String id = String.valueOf(args.get(3));
                String modelPath = String.valueOf(args.get(4));
                String texturePath = String.valueOf(args.get(5));
                float ox = ((Number) args.get(6)).floatValue();
                float oy = ((Number) args.get(7)).floatValue();
                float oz = ((Number) args.get(8)).floatValue();
                float rx = ((Number) args.get(9)).floatValue();
                float ry = ((Number) args.get(10)).floatValue();
                float rz = ((Number) args.get(11)).floatValue();
                float scale = ((Number) args.get(12)).floatValue();

                BlockEntity be = source.getLevel().getBlockEntity(new BlockPos(x, y, z));
                if (be instanceof CustomGeoBlockEntity cgbe) {
                    cgbe.displays.put(id, new CustomGeoBlockEntity.BlockDisplay(1, modelPath, texturePath, ox, oy, oz, rx, ry, rz, scale));
                    cgbe.setChanged();
                    source.getLevel().sendBlockUpdated(cgbe.getBlockPos(), cgbe.getBlockState(), cgbe.getBlockState(), 3);
                }
            } catch (Exception e) {}
            return null;
        }
    }

    public static class SetBlockDisplayBlock implements ScriptFunction {
        @Override
        public String getName() {
            return "setBlockDisplayBlock";
        }

        @Override
        public int getArgCount() {
            return 12;
        }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[]{
                Object.class, Object.class, Object.class, // x, y, z
                Object.class, Object.class, // id, block_id
                Object.class, Object.class, Object.class, // ox, oy, oz
                Object.class, Object.class, Object.class, // rx, ry, rz
                Object.class // scale
            };
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 12 || source.getLevel() == null) return null;
            try {
                int x = ((Number) args.get(0)).intValue();
                int y = ((Number) args.get(1)).intValue();
                int z = ((Number) args.get(2)).intValue();
                String id = String.valueOf(args.get(3));
                String blockId = String.valueOf(args.get(4));
                float ox = ((Number) args.get(5)).floatValue();
                float oy = ((Number) args.get(6)).floatValue();
                float oz = ((Number) args.get(7)).floatValue();
                float rx = ((Number) args.get(8)).floatValue();
                float ry = ((Number) args.get(9)).floatValue();
                float rz = ((Number) args.get(10)).floatValue();
                float scale = ((Number) args.get(11)).floatValue();

                BlockEntity be = source.getLevel().getBlockEntity(new BlockPos(x, y, z));
                if (be instanceof CustomGeoBlockEntity cgbe) {
                    cgbe.displays.put(id, new CustomGeoBlockEntity.BlockDisplay(2, blockId, "", ox, oy, oz, rx, ry, rz, scale));
                    cgbe.setChanged();
                    source.getLevel().sendBlockUpdated(cgbe.getBlockPos(), cgbe.getBlockState(), cgbe.getBlockState(), 3);
                }
            } catch (Exception e) {}
            return null;
        }
    }

    public static class RemoveBlockDisplay implements ScriptFunction {
        @Override
        public String getName() {
            return "removeBlockDisplay";
        }

        @Override
        public int getArgCount() {
            return 4;
        }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[]{Object.class, Object.class, Object.class, Object.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 4 || source.getLevel() == null) return null;
            try {
                int x = ((Number) args.get(0)).intValue();
                int y = ((Number) args.get(1)).intValue();
                int z = ((Number) args.get(2)).intValue();
                String id = String.valueOf(args.get(3));

                BlockEntity be = source.getLevel().getBlockEntity(new BlockPos(x, y, z));
                if (be instanceof CustomGeoBlockEntity cgbe) {
                    cgbe.displays.remove(id);
                    cgbe.setChanged();
                    source.getLevel().sendBlockUpdated(cgbe.getBlockPos(), cgbe.getBlockState(), cgbe.getBlockState(), 3);
                }
            } catch (Exception e) {}
            return null;
        }
    }

    public static class GetBlockSlot implements ScriptFunction {
        @Override
        public String getName() {
            return "getBlockSlot";
        }

        @Override
        public int getArgCount() {
            return 4;
        }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[]{Object.class, Object.class, Object.class, Object.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 4 || source.getLevel() == null) return "";
            try {
                int x = ((Number) args.get(0)).intValue();
                int y = ((Number) args.get(1)).intValue();
                int z = ((Number) args.get(2)).intValue();
                int slot = ((Number) args.get(3)).intValue();

                BlockEntity be = source.getLevel().getBlockEntity(new BlockPos(x, y, z));
                if (be instanceof CustomGeoBlockEntity cgbe) {
                    if (slot >= 0 && slot < cgbe.inventory.getContainerSize()) {
                        net.minecraft.world.item.ItemStack stack = cgbe.inventory.getItem(slot);
                        if (!stack.isEmpty()) {
                            return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                        }
                    }
                }
            } catch (Exception e) {}
            return "";
        }
    }

    public static class OpenBlockUi implements ScriptFunction {
        @Override
        public String getName() {
            return "openBlockUi";
        }

        @Override
        public int getArgCount() {
            return 5;
        }

        @Override
        public Class<?>[] getArgTypes() {
            return new Class<?>[]{Object.class, Object.class, Object.class, Object.class, Object.class};
        }

        @Override
        public Object execute(List<Object> args, CommandSourceStack source, ScriptContext context) {
            if (args.size() < 5 || source.getLevel() == null) return null;
            Player player = resolvePlayer(args.get(0), source);
            if (!(player instanceof ServerPlayer sp)) return null;

            try {
                int x = ((Number) args.get(1)).intValue();
                int y = ((Number) args.get(2)).intValue();
                int z = ((Number) args.get(3)).intValue();
                
                String json;
                if (args.get(4) instanceof UiTemplate ut) {
                    json = ut.getJson();
                } else {
                    json = String.valueOf(args.get(4));
                }

                String prepared = SprauteUiJson.prepareAndSerialize(source.getLevel(), source, json);
                
                BlockEntity be = source.getLevel().getBlockEntity(new BlockPos(x, y, z));
                net.minecraft.world.SimpleContainer container = null;
                if (be instanceof CustomGeoBlockEntity cgbe) {
                    container = cgbe.inventory;
                }

                final net.minecraft.world.SimpleContainer finalContainer = container;

                net.minecraftforge.network.NetworkHooks.openScreen(sp, new net.minecraft.world.MenuProvider() {
                    @Override
                    public net.minecraft.network.chat.Component getDisplayName() {
                        return net.minecraft.network.chat.Component.empty();
                    }

                    @Override
                    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv, Player player) {
                        return new org.zonarstudio.spraute_engine.ui.SprauteContainerMenu(id, inv, prepared, finalContainer);
                    }
                }, buf -> buf.writeUtf(prepared));

            } catch (Exception e) {}
            return null;
        }
        
        private Player resolvePlayer(Object target, CommandSourceStack source) {
            if (target instanceof Player p) return p;
            if (target instanceof String name && source.getLevel() != null) {
                return source.getLevel().getServer().getPlayerList().getPlayerByName(name);
            }
            return null;
        }
    }
}