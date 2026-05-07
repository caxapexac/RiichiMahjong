package com.riichimahjongforge.gimmicks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class VariantPaintingItem extends HangingEntityItem {
    private final List<ResourceLocation> variantIds;

    public VariantPaintingItem(Properties properties, ResourceLocation... variantIds) {
        super(EntityType.PAINTING, properties);
        this.variantIds = List.of(variantIds);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ResourceLocation pick = variantIds.get(ThreadLocalRandom.current().nextInt(variantIds.size()));
        CompoundTag entityTag = context.getItemInHand().getOrCreateTagElement("EntityTag");
        entityTag.putString("variant", pick.toString());
        return super.useOn(context);
    }
}
