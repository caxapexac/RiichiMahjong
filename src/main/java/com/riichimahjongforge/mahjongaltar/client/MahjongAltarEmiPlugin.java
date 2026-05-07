package com.riichimahjongforge.mahjongaltar.client;

import com.riichimahjongforge.RiichiMahjongForgeMod;
import com.riichimahjongforge.mahjongaltar.MahjongAltarRecipe;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;

@EmiEntrypoint
public final class MahjongAltarEmiPlugin implements EmiPlugin {
    private static final EmiRecipeCategory MAHJONG_ALTAR_CATEGORY = new EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(RiichiMahjongForgeMod.MODID, "mahjong_altar"),
            EmiStack.of(RiichiMahjongForgeMod.MAHJONG_ALTAR_ITEM.get()));

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(MAHJONG_ALTAR_CATEGORY);
        registry.addWorkstation(MAHJONG_ALTAR_CATEGORY, EmiStack.of(RiichiMahjongForgeMod.MAHJONG_ALTAR_ITEM.get()));
        for (MahjongAltarRecipe recipe : registry.getRecipeManager().getAllRecipesFor(MahjongAltarRecipe.TYPE)) {
            registry.addRecipe(new MahjongAltarEmiRecipe(MAHJONG_ALTAR_CATEGORY, recipe));
        }
    }
}
