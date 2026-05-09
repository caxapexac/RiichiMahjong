package com.riichimahjong.mahjongaltar;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * 1.21 {@link RecipeInput} wrapper for the altar's 9-slot inventory. The altar passes
 * the non-empty stacks to the recipe; the recipe matches ingredients order-insensitively
 * via {@link MahjongAltarRecipe#matchIngredientsRecursive}.
 */
public record AltarRecipeInput(List<ItemStack> items) implements RecipeInput {
    @Override
    public ItemStack getItem(int slot) {
        return slot < 0 || slot >= items.size() ? ItemStack.EMPTY : items.get(slot);
    }

    @Override
    public int size() {
        return items.size();
    }
}
