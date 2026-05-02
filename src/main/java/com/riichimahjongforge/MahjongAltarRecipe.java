package com.riichimahjongforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.core.NonNullList;

public class MahjongAltarRecipe implements Recipe<Container> {
    public static final RecipeType<MahjongAltarRecipe> TYPE = new RecipeType<>() {
        @Override
        public String toString() {
            return RiichiMahjongForgeMod.MODID + ":mahjong_altar";
        }
    };

    private final ResourceLocation id;
    private final NonNullList<Ingredient> ingredients;
    private final ItemStack result;
    private final int minHan;

    public MahjongAltarRecipe(ResourceLocation id, NonNullList<Ingredient> ingredients, ItemStack result, int minHan) {
        this.id = id;
        this.ingredients = ingredients;
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
    public boolean matches(Container container, Level level) {
        ArrayList<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
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
            if (used[i]) {
                continue;
            }
            if (!ingredient.test(inputs.get(i))) {
                continue;
            }
            used[i] = true;
            if (matchIngredientsRecursive(ingredientIndex + 1, inputs, used)) {
                return true;
            }
            used[i] = false;
        }
        return false;
    }

    @Override
    public ItemStack assemble(Container container, net.minecraft.core.RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(net.minecraft.core.RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RiichiMahjongForgeMod.MAHJONG_ALTAR_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return TYPE;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return ingredients;
    }

    public static class Serializer implements RecipeSerializer<MahjongAltarRecipe> {
        public static final String INGREDIENTS_KEY = "ingredients";
        public static final String RESULT_KEY = "result";
        public static final String MIN_HAN_KEY = "min_han";

        @Override
        public MahjongAltarRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            JsonArray ingredientsJson = GsonHelper.getAsJsonArray(json, INGREDIENTS_KEY);
            NonNullList<Ingredient> ingredients = NonNullList.create();
            for (JsonElement element : ingredientsJson) {
                Ingredient ingredient = Ingredient.fromJson(element);
                if (!ingredient.isEmpty()) {
                    ingredients.add(ingredient);
                }
            }
            if (ingredients.isEmpty()) {
                throw new IllegalArgumentException("Mahjong altar recipe " + recipeId + " has no ingredients");
            }
            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, RESULT_KEY));
            int minHan = Math.max(0, GsonHelper.getAsInt(json, MIN_HAN_KEY, 0));
            return new MahjongAltarRecipe(recipeId, ingredients, result, minHan);
        }

        @Override
        public MahjongAltarRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            int minHan = Math.max(0, buffer.readVarInt());
            int ingredientCount = Math.max(0, buffer.readVarInt());
            NonNullList<Ingredient> ingredients = NonNullList.withSize(ingredientCount, Ingredient.EMPTY);
            for (int i = 0; i < ingredientCount; i++) {
                ingredients.set(i, Ingredient.fromNetwork(buffer));
            }
            ItemStack result = buffer.readItem();
            return new MahjongAltarRecipe(recipeId, ingredients, result, minHan);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, MahjongAltarRecipe recipe) {
            buffer.writeVarInt(recipe.minHan());
            buffer.writeVarInt(recipe.ingredients.size());
            for (Ingredient ingredient : recipe.ingredients) {
                ingredient.toNetwork(buffer);
            }
            buffer.writeItem(recipe.result);
        }
    }
}
