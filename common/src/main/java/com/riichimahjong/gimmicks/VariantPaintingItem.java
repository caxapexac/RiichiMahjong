package com.riichimahjong.gimmicks;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Painting item that, on use, picks one of {@code variantIds} at random and writes it
 * onto the placed painting via {@link DataComponents#ENTITY_DATA} (NBT key {@code variant}).
 *
 * <p>1.21.1 painting model: variant is a {@code Holder<PaintingVariant>} (datapack-driven
 * registry; see {@code data/<ns>/painting_variant/*.json}). Vanilla's
 * {@code EntityType.updateCustomEntityTag} applies the ENTITY_DATA tag after the painting
 * is spawned, and {@code Painting.readAdditionalSaveData} parses the {@code "variant"}
 * string via the painting-variant codec.
 *
 * <p>Note: {@code DataComponents.PAINTING_VARIANT} (a typed {@code Holder<PaintingVariant>}
 * component) was added in 1.21.2 — not available here. ENTITY_DATA + "variant" string is
 * the 1.21.1-compatible path.
 */
public class VariantPaintingItem extends HangingEntityItem {
    private final List<ResourceLocation> variantIds;

    public VariantPaintingItem(Properties properties, ResourceLocation... variantIds) {
        super(EntityType.PAINTING, properties);
        this.variantIds = List.of(variantIds);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ResourceLocation pick = variantIds.get(ThreadLocalRandom.current().nextInt(variantIds.size()));
        ItemStack stack = context.getItemInHand();
        // ENTITY_DATA requires "id" naming the entity type — vanilla ItemStack.save
        // throws "Missing id for entity" otherwise. The painting reads the variant
        // string from the same tag in readAdditionalSaveData.
        CustomData.update(DataComponents.ENTITY_DATA, stack, tag -> {
            tag.putString("id", "minecraft:painting");
            tag.putString("variant", pick.toString());
        });
        return super.useOn(context);
    }
}
