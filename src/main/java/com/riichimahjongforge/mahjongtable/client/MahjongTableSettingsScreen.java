package com.riichimahjongforge.mahjongtable.client;

import com.riichimahjongforge.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjongforge.mahjongtable.MahjongTableSettingsMenu;
import com.riichimahjongforge.mahjongtable.RuleSetPreset;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Settings screen for the mahjong table — five preset buttons in a column,
 * with the currently-selected preset highlighted, plus a Back button. Reads the
 * stored preset from the local table BE for the highlight.
 */
public class MahjongTableSettingsScreen extends AbstractContainerScreen<MahjongTableSettingsMenu> {

    private static final int PANEL_PADDING = 8;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_FROM_BOTTOM = 94;
    private static final int OUTER_BG = 0xC0101010;
    private static final int INNER_BG = 0xFF2C2C2C;
    private static final int LABEL_COLOR = 0xE0E0E0;

    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_GAP = 4;
    private static final int BUTTONS_TOP = MahjongTableSettingsMenu.CONTENT_TOP;
    private static final int BACK_BUTTON_WIDTH = 60;

    public MahjongTableSettingsScreen(
            MahjongTableSettingsMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = PANEL_PADDING + BUTTON_WIDTH + PANEL_PADDING;
        this.imageHeight = MahjongTableSettingsMenu.CONTENT_TOP
                + MahjongTableSettingsMenu.CONTENT_HEIGHT
                + MahjongTableSettingsMenu.PLAYER_INV_TOP_GAP
                + MahjongTableSettingsMenu.HOTBAR_Y_OFFSET
                + MahjongTableSettingsMenu.HOTBAR_HEIGHT;
        this.titleLabelX = TITLE_X;
        this.titleLabelY = TITLE_Y;
        this.inventoryLabelY = this.imageHeight - INVENTORY_LABEL_FROM_BOTTOM;
    }

    @Override
    protected void init() {
        super.init();
        RuleSetPreset[] presets = RuleSetPreset.values();
        RuleSetPreset current = currentPreset();
        int x = leftPos + PANEL_PADDING;

        for (int i = 0; i < presets.length; i++) {
            int presetIdx = i;
            RuleSetPreset preset = presets[i];
            int y = topPos + BUTTONS_TOP + i * (BUTTON_HEIGHT + BUTTON_GAP);
            String prefix = preset == current ? "▶ " : "   ";
            addRenderableWidget(Button.builder(
                            Component.literal(prefix).append(Component.translatable(preset.langKey())),
                            b -> sendButton(MahjongTableSettingsMenu.BTN_PRESET_BASE + presetIdx))
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
        }

        int backY = topPos + BUTTONS_TOP + presets.length * (BUTTON_HEIGHT + BUTTON_GAP) + BUTTON_GAP;
        addRenderableWidget(Button.builder(
                        Component.translatable("riichi_mahjong_forge.button.table.back"),
                        b -> sendButton(MahjongTableSettingsMenu.BTN_BACK))
                .bounds(x, backY, BACK_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private RuleSetPreset currentPreset() {
        if (minecraft == null || minecraft.level == null) return RuleSetPreset.MAHJONG_SOUL_4P;
        var be = minecraft.level.getBlockEntity(menu.getTablePos());
        if (be instanceof MahjongTableBlockEntity table) return table.preset();
        return RuleSetPreset.MAHJONG_SOUL_4P;
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, OUTER_BG);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, INNER_BG);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, LABEL_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
