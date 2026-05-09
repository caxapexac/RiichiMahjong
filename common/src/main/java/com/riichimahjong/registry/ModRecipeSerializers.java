package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.mahjongaltar.MahjongAltarRecipe;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(RiichiMahjong.MOD_ID, Registries.RECIPE_SERIALIZER);

    public static final RegistrySupplier<RecipeSerializer<MahjongAltarRecipe>> MAHJONG_ALTAR_RECIPE_SERIALIZER =
            RECIPE_SERIALIZERS.register("mahjong_altar", MahjongAltarRecipe.Serializer::new);

    private ModRecipeSerializers() {}
}
