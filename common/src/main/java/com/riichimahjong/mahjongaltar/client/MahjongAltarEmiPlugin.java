package com.riichimahjong.mahjongaltar.client;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.mahjongaltar.MahjongAltarRecipe;
import com.riichimahjong.registry.ModBlocks;
import com.riichimahjong.registry.ModRecipeTypes;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

@EmiEntrypoint
public final class MahjongAltarEmiPlugin implements EmiPlugin {
    private static final EmiRecipeCategory MAHJONG_ALTAR_CATEGORY = new EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "mahjong_altar"),
            EmiStack.of(ModBlocks.MAHJONG_ALTAR_ITEM.get()));

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(MAHJONG_ALTAR_CATEGORY);
        registry.addWorkstation(MAHJONG_ALTAR_CATEGORY, EmiStack.of(ModBlocks.MAHJONG_ALTAR_ITEM.get()));
        for (RecipeHolder<MahjongAltarRecipe> holder :
                registry.getRecipeManager().getAllRecipesFor(ModRecipeTypes.MAHJONG_ALTAR.get())) {
            registry.addRecipe(new MahjongAltarEmiRecipe(MAHJONG_ALTAR_CATEGORY, holder.id(), holder.value()));
        }
    }
}
