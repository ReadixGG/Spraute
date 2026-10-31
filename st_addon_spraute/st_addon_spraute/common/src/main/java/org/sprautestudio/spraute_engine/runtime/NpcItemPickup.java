package org.sprautestudio.spraute_engine.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.sprautestudio.spraute_engine.core.entity.AnimatedEntity;

import java.util.ArrayList;
import java.util.List;

/** Items a story character collects when it walks over them. */
public final class NpcItemPickup {
    private final AnimatedEntity owner;
    private final List<ItemStack> items = new ArrayList<>();
    private List<String> filters = List.of();
    private boolean active;
    private int limit = -1, collected;

    public NpcItemPickup(AnimatedEntity owner) {
        this.owner = owner;
    }

    /** Empty filters mean any item; a negative limit means no quantity limit. */
    public void configure(List<String> filters, int limit) {
        this.filters = List.copyOf(filters);
        this.limit = limit < 0 ? -1 : limit;
        collected = 0;
        active = true;
    }

    public void stop() {
        active = false;
    }

    public int count(String spec) {
        int n = 0;
        for (ItemStack stack : items) if (spec == null || spec.isEmpty() || ItemSpec.matches(stack, spec)) n += stack.getCount();
        return n;
    }

    public void tick() {
        if (!active || owner.level().isClientSide || owner.tickCount % 2 != 0 || limit >= 0 && collected >= limit) return;
        for (ItemEntity entity : owner.level().getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(.35, .25, .35))) {
            if (entity.isRemoved() || entity.hasPickUpDelay()) continue;
            ItemStack ground = entity.getItem();
            if (ground.isEmpty() || !allowed(ground)) continue;
            int available = ground.getCount();
            if (limit >= 0) available = Math.min(available, limit - collected);
            if (available <= 0) continue;
            ItemStack taken = ground.copy();
            taken.setCount(available);
            store(taken);
            ground.shrink(available);
            owner.take(entity, available);
            if (ground.isEmpty()) entity.discard();
            else entity.setItem(ground);
            collected += available;
            if (limit >= 0 && collected >= limit) break;
        }
    }

    private boolean allowed(ItemStack stack) {
        if (filters.isEmpty()) return true;
        for (String filter : filters) if (ItemSpec.matches(stack, filter)) return true;
        return false;
    }

    private void store(ItemStack stack) {
        int left = stack.getCount();
        for (ItemStack stored : items) {
            if (!ItemStack.isSameItemSameTags(stored, stack)) continue;
            int add = Math.min(left, stored.getMaxStackSize() - stored.getCount());
            if (add > 0) stored.grow(add);
            left -= add;
            if (left == 0) return;
        }
        while (left > 0) {
            int add = Math.min(left, stack.getMaxStackSize());
            ItemStack slot = stack.copy();
            slot.setCount(add);
            items.add(slot);
            left -= add;
        }
    }

    public void save(CompoundTag tag) {
        tag.putBoolean("Active", active);
        tag.putInt("Limit", limit);
        tag.putInt("Collected", collected);
        ListTag allowed = new ListTag();
        for (String filter : filters) allowed.add(StringTag.valueOf(filter));
        tag.put("Filters", allowed);
        ListTag inventory = new ListTag();
        for (ItemStack item : items) inventory.add(item.save(new CompoundTag()));
        tag.put("Items", inventory);
    }

    public void load(CompoundTag tag) {
        active = tag.getBoolean("Active");
        limit = tag.contains("Limit") ? tag.getInt("Limit") : -1;
        collected = Math.max(0, tag.getInt("Collected"));
        List<String> allowed = new ArrayList<>();
        ListTag savedFilters = tag.getList("Filters", Tag.TAG_STRING);
        for (int i = 0; i < savedFilters.size(); i++) allowed.add(savedFilters.getString(i));
        filters = List.copyOf(allowed);
        items.clear();
        ListTag inventory = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack item = ItemStack.of(inventory.getCompound(i));
            if (!item.isEmpty()) items.add(item);
        }
    }
}
