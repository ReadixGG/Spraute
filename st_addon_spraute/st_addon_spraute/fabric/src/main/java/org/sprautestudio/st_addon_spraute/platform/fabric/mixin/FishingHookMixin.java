package org.sprautestudio.st_addon_spraute.platform.fabric.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.sprautestudio.st_addon_spraute.platform.Hooks;
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
    @Redirect(method = "retrieve", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
    private ObjectArrayList<ItemStack> spraute$fished(LootTable table, LootParams params) {
        ObjectArrayList<ItemStack> drops = table.getRandomItems(params);
        Player player = ((FishingHook) (Object) this).getPlayerOwner();
        if (player != null) {
            NonNullList<ItemStack> list = NonNullList.create();
            list.addAll(drops);
            Hooks.ITEM_FISHED.fire(new Hooks.ItemFished(player, list));
        }
        return drops;
    }
}
