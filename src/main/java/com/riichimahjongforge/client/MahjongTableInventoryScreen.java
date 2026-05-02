package com.riichimahjongforge.client;

import com.riichimahjongforge.MahjongTableBlockEntity;
import com.riichimahjongforge.menu.MahjongTableInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class MahjongTableInventoryScreen extends AbstractContainerScreen<MahjongTableInventoryMenu> {
    private static final int PANEL_PADDING = 8;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_FROM_BOTTOM = 94;
    private static final int BUTTON_ROW_Y = 16;
    private static final int BUTTON_START_X = 6;
    private static final int BUTTON_WIDTH = 36;
    private static final int BUTTON_HEIGHT = 12;
    private static final int BUTTON_GAP = 2;
    private static final int SECTION_LABEL_X = 8;
    private static final int SECTION_LABEL_Y = 32;
    private static final int PLAYER_LABEL_COLOR = 0xFFD080;
    private static final int OUTER_BG = 0xC0101010;
    private static final int INNER_BG = 0xFF2C2C2C;
    private static final int LABEL_COLOR = 0xE0E0E0;
    private static final int SUBLABEL_COLOR = 0xC0C0C0;
    private Button tableButton;
    private Button handsButton;
    private Button wallButton;
    private Button deadWallButton;
    private Button openMeldsButton;
    private Button discardsButton;

    public MahjongTableInventoryScreen(MahjongTableInventoryMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = PANEL_PADDING + MahjongTableInventoryMenu.GRID_COLS * MahjongTableInventoryMenu.SLOT_SIZE + PANEL_PADDING;
        this.imageHeight = MahjongTableInventoryMenu.GRID_TOP
                + MahjongTableInventoryMenu.GRID_ROWS * MahjongTableInventoryMenu.SLOT_SIZE
                + MahjongTableInventoryMenu.PLAYER_INV_TOP_GAP
                + MahjongTableInventoryMenu.HOTBAR_Y_OFFSET
                + MahjongTableInventoryMenu.HOTBAR_HEIGHT;
        this.titleLabelX = TITLE_X;
        this.titleLabelY = TITLE_Y;
        this.inventoryLabelY = this.imageHeight - INVENTORY_LABEL_FROM_BOTTOM;
    }

    @Override
    protected void init() {
        super.init();
        int y = topPos + BUTTON_ROW_Y;
        int x = leftPos + BUTTON_START_X;
        tableButton = Button.builder(Component.literal("Table"), b -> sendButton(MahjongTableInventoryMenu.BTN_OPEN_TABLE_STORAGE))
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        handsButton = Button.builder(Component.literal("Hands"), b -> sendButton(MahjongTableInventoryMenu.BTN_OPEN_HANDS_STORAGE))
                .bounds(x + (BUTTON_WIDTH + BUTTON_GAP), y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        wallButton = Button.builder(Component.literal("Wall"), b -> sendButton(MahjongTableInventoryMenu.BTN_OPEN_WALL_STORAGE))
                .bounds(x + (BUTTON_WIDTH + BUTTON_GAP) * 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        deadWallButton = Button.builder(Component.literal("Dead"), b -> sendButton(MahjongTableInventoryMenu.BTN_OPEN_DEAD_WALL_STORAGE))
                .bounds(x + (BUTTON_WIDTH + BUTTON_GAP) * 3, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        openMeldsButton =
                Button.builder(Component.literal("Melds"), b -> sendButton(MahjongTableInventoryMenu.BTN_OPEN_OPEN_MELDS_STORAGE))
                        .bounds(x + (BUTTON_WIDTH + BUTTON_GAP) * 4, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                        .build();
        discardsButton =
                Button.builder(Component.literal("Disc"), b -> sendButton(MahjongTableInventoryMenu.BTN_OPEN_DISCARDS_STORAGE))
                        .bounds(x + (BUTTON_WIDTH + BUTTON_GAP) * 5, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                        .build();
        addRenderableWidget(tableButton);
        addRenderableWidget(handsButton);
        addRenderableWidget(wallButton);
        addRenderableWidget(deadWallButton);
        addRenderableWidget(openMeldsButton);
        addRenderableWidget(discardsButton);
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        MahjongTableInventoryMenu.Section section = menu.getSection();
        tableButton.active = section != MahjongTableInventoryMenu.Section.TABLE_TILES;
        handsButton.active = section != MahjongTableInventoryMenu.Section.HANDS;
        wallButton.active = section != MahjongTableInventoryMenu.Section.WALL;
        deadWallButton.active = section != MahjongTableInventoryMenu.Section.DEAD_WALL;
        openMeldsButton.active = section != MahjongTableInventoryMenu.Section.OPEN_MELDS;
        discardsButton.active = section != MahjongTableInventoryMenu.Section.DISCARDS;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, OUTER_BG);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, INNER_BG);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, LABEL_COLOR, false);
        MahjongTableInventoryMenu.Section section = menu.getSection();
        graphics.drawString(
                font,
                Component.literal(labelForSection(section) + " (" + menu.getVisibleSlots() + " slots)"),
                SECTION_LABEL_X,
                SECTION_LABEL_Y,
                SUBLABEL_COLOR,
                false);
        drawSeatSplitLabels(graphics, section);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void drawSeatSplitLabels(GuiGraphics graphics, MahjongTableInventoryMenu.Section section) {
        if (section != MahjongTableInventoryMenu.Section.HANDS
                && section != MahjongTableInventoryMenu.Section.OPEN_MELDS) {
            return;
        }
        String suffix = section == MahjongTableInventoryMenu.Section.HANDS ? "hand" : "melds";
        for (int seat = 0; seat < MahjongTableBlockEntity.SEAT_COUNT; seat++) {
            int[] o = MahjongTableInventoryMenu.seatBlockOrigin(section, seat);
            int x = o[0];
            int y = o[1] - 10;
            graphics.drawString(
                    font,
                    Component.literal("MahjongPlayer " + (seat + 1) + " " + suffix),
                    x,
                    y,
                    PLAYER_LABEL_COLOR,
                    false);
        }
    }

    private static String labelForSection(MahjongTableInventoryMenu.Section section) {
        return switch (section) {
            case TABLE_TILES -> "Table";
            case HANDS -> "Hands";
            case WALL -> "Wall";
            case DEAD_WALL -> "Dead";
            case OPEN_MELDS -> "Melds";
            case DISCARDS -> "Disc";
        };
    }
}
