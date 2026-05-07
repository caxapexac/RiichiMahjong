package com.riichimahjongforge.mahjongaltar.client;

import com.riichimahjongforge.mahjongaltar.MahjongAltarRecipe;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import net.minecraft.network.chat.Component;

public final class MahjongAltarEmiRecipe extends BasicEmiRecipe {
    private static final int GRID_COLUMNS = 3;
    private static final int SLOT_STEP = 18;
    private static final int ARROW_X = 62;
    private static final int ARROW_Y = 19;
    private static final int OUTPUT_X = 94;
    private static final int OUTPUT_Y = 19;
    private static final int TEXT_Y = 57;

    private final MahjongAltarRecipe recipe;

    public MahjongAltarEmiRecipe(EmiRecipeCategory category, MahjongAltarRecipe recipe) {
        super(category, recipe.getId(), 112, 68);
        this.recipe = recipe;
        this.inputs = recipe.getIngredients().stream()
                .map(EmiIngredient::of)
                .toList();
        this.outputs = List.of(EmiStack.of(recipe.displayResult()));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        for (int i = 0; i < inputs.size(); i++) {
            int x = (i % GRID_COLUMNS) * SLOT_STEP;
            int y = (i / GRID_COLUMNS) * SLOT_STEP;
            widgets.addSlot(inputs.get(i), x, y);
        }
        widgets.addTexture(EmiTexture.EMPTY_ARROW, ARROW_X, ARROW_Y);
        widgets.addSlot(outputs.get(0), OUTPUT_X, OUTPUT_Y).recipeContext(this);
        widgets.addText(
                Component.translatable("riichi_mahjong_forge.emi.mahjong_altar.min_han", recipe.minHan()),
                0,
                TEXT_Y,
                0x404040,
                false);
    }
}
