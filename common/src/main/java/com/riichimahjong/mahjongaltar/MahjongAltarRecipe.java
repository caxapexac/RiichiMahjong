package com.riichimahjong.mahjongaltar;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.riichimahjong.registry.ModRecipeSerializers;
import com.riichimahjong.registry.ModRecipeTypes;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Mahjong altar recipe — N ingredients consumed in any order, produces a single result
 * stack, conditional on a minimum {@code han} count from the {@code MahjongRoundResolved}
 * event firing nearby.
 *
 * <p>1.21 recipe API: {@code Recipe<I extends RecipeInput>}, {@code MapCodec} for JSON,
 * {@code StreamCodec} for network. {@code getId()} is gone — recipes don't track their
 * own id (the registry holder does).
 */
public class MahjongAltarRecipe implements Recipe<AltarRecipeInput> {

    private final List<Ingredient> ingredients;
    private final ItemStack result;
    private final int minHan;

    public MahjongAltarRecipe(List<Ingredient> ingredients, ItemStack result, int minHan) {
        this.ingredients = List.copyOf(ingredients);
        this.result = result;
        this.minHan = Math.max(0, minHan);
    }

    public int minHan() {
        return minHan;
    }

    public ItemStack displayResult() {
        return result.copy();
    }

    @Override
    public boolean matches(AltarRecipeInput input, Level level) {
        ArrayList<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                inputs.add(stack);
            }
        }
        return matchesInputs(inputs);
    }

    public boolean matchesInputs(List<ItemStack> inputs) {
        if (inputs.size() != ingredients.size()) {
            return false;
        }
        boolean[] used = new boolean[inputs.size()];
        return matchIngredientsRecursive(0, inputs, used);
    }

    private boolean matchIngredientsRecursive(int ingredientIndex, List<ItemStack> inputs, boolean[] used) {
        if (ingredientIndex >= ingredients.size()) {
            return true;
        }
        Ingredient ingredient = ingredients.get(ingredientIndex);
        for (int i = 0; i < inputs.size(); i++) {
            if (used[i]) continue;
            if (!ingredient.test(inputs.get(i))) continue;
            used[i] = true;
            if (matchIngredientsRecursive(ingredientIndex + 1, inputs, used)) {
                return true;
            }
            used[i] = false;
        }
        return false;
    }

    @Override
    public ItemStack assemble(AltarRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    /**
     * Skips the vanilla recipe-book pipeline. Without this, {@code ClientRecipeBook}
     * tries to slot every altar recipe into a {@code RecipeBookCategories} and warns
     * "Unknown recipe category" because our custom {@code RecipeType} isn't mapped
     * to any vanilla recipe-book tab. The altar has its own UI and never surfaces in
     * the player's recipe book anyway.
     */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.MAHJONG_ALTAR_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.MAHJONG_ALTAR.get();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> out = NonNullList.create();
        out.addAll(ingredients);
        return out;
    }

    public static final class Serializer implements RecipeSerializer<MahjongAltarRecipe> {

        public static final MapCodec<MahjongAltarRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients")
                                .forGetter(r -> r.ingredients),
                        ItemStack.STRICT_CODEC.fieldOf("result")
                                .forGetter(r -> r.result),
                        com.mojang.serialization.Codec.INT.optionalFieldOf("min_han", 0)
                                .forGetter(MahjongAltarRecipe::minHan)
                ).apply(instance, MahjongAltarRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, MahjongAltarRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),
                        r -> r.ingredients,
                        ItemStack.STREAM_CODEC,
                        r -> r.result,
                        ByteBufCodecs.VAR_INT,
                        MahjongAltarRecipe::minHan,
                        MahjongAltarRecipe::new);

        @Override
        public MapCodec<MahjongAltarRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MahjongAltarRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
