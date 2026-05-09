package com.riichimahjong.mahjongtable.client;

import com.riichimahjong.mahjongtable.MahjongTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Minimal flat 17×8 grid screen for the mahjong table inventory.
 */
public class MahjongTableScreen extends AbstractContainerScreen<MahjongTableMenu> {

    private static final int PANEL_PADDING = 8;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_FROM_BOTTOM = 94;
    private static final int OUTER_BG = 0xC0101010;
    private static final int INNER_BG = 0xFF2C2C2C;
    private static final int LABEL_COLOR = 0xE0E0E0;
    private static final int SETTINGS_BTN_WIDTH = 60;
    private static final int SETTINGS_BTN_HEIGHT = 14;
    private static final int SETTINGS_BTN_INSET = 4;

    public MahjongTableScreen(MahjongTableMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = PANEL_PADDING + MahjongTableMenu.GRID_COLS * MahjongTableMenu.SLOT_SIZE + PANEL_PADDING;
        this.imageHeight = MahjongTableMenu.GRID_TOP
                + MahjongTableMenu.GRID_ROWS * MahjongTableMenu.SLOT_SIZE
                + MahjongTableMenu.PLAYER_INV_TOP_GAP
                + MahjongTableMenu.HOTBAR_Y_OFFSET
                + MahjongTableMenu.HOTBAR_HEIGHT;
        this.titleLabelX = TITLE_X;
        this.titleLabelY = TITLE_Y;
        this.inventoryLabelY = this.imageHeight - INVENTORY_LABEL_FROM_BOTTOM;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(
                        Component.translatable("riichi_mahjong.button.table.settings"),
                        b -> {
                            if (minecraft != null && minecraft.gameMode != null) {
                                minecraft.gameMode.handleInventoryButtonClick(
                                        menu.containerId, MahjongTableMenu.BTN_OPEN_SETTINGS);
                            }
                        })
                .bounds(
                        leftPos + imageWidth - SETTINGS_BTN_WIDTH - SETTINGS_BTN_INSET,
                        topPos + SETTINGS_BTN_INSET,
                        SETTINGS_BTN_WIDTH,
                        SETTINGS_BTN_HEIGHT)
                .build());
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
        // 1.21: AbstractContainerScreen.render() invokes renderBackground itself.
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
