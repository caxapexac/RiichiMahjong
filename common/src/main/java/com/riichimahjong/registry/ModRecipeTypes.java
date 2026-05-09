package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.mahjongaltar.MahjongAltarRecipe;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeType;

public final class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(RiichiMahjong.MOD_ID, Registries.RECIPE_TYPE);

    public static final RegistrySupplier<RecipeType<MahjongAltarRecipe>> MAHJONG_ALTAR =
            RECIPE_TYPES.register("mahjong_altar",
                    () -> new RecipeType<MahjongAltarRecipe>() {
                        @Override public String toString() { return "riichi_mahjong:mahjong_altar"; }
                    });

    private ModRecipeTypes() {}
}
