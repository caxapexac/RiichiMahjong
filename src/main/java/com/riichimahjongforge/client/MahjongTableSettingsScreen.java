package com.riichimahjongforge.client;

import com.mahjongcore.MahjongMatchDefinition;
import com.riichimahjongforge.MahjongTableBlockEntity;
import com.riichimahjongforge.menu.MahjongTableSettingsMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class MahjongTableSettingsScreen extends AbstractContainerScreen<MahjongTableSettingsMenu> {

    private Button matchLengthButton;
    private Button akaButton;
    private Button openTanButton;
    private Button allowGameplayButton;
    private Button allowCustomTilePackButton;
    private Button allowEditInMatchButton;
    private Button normalizeFovInRadiusButton;
    private Button passiveBotsButton;
    private Button startingPointsButton;
    private Button fillTableTilesButton;
    private Button fillNonTableTilesButton;
    private Button fill53TilesButton;
    private Button endMatchButton;
    private Button resetButton;

    public MahjongTableSettingsScreen(MahjongTableSettingsMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 268;
        this.imageWidth = 420;
        this.inventoryLabelY = 3000;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        int outerPad = 10;
        int colGap = 8;
        int leftX = leftPos + outerPad;
        int colWidth = (imageWidth - (outerPad * 2) - colGap) / 2;
        int rightX = leftX + colWidth + colGap;
        int leftY = topPos + 18;
        int rightY = topPos + 18;
        int h = 20;
        int gap = 22;

        matchLengthButton = Button.builder(Component.empty(), b -> sendMenuButton(MahjongTableSettingsMenu.BTN_HANCHAN))
                .bounds(leftX, leftY, colWidth, h)
                .build();
        addRenderableWidget(matchLengthButton);
        leftY += gap;

        akaButton = Button.builder(Component.empty(), b -> sendMenuButton(MahjongTableSettingsMenu.BTN_AKA))
                .bounds(leftX, leftY, colWidth, h)
                .build();
        addRenderableWidget(akaButton);
        leftY += gap;

        openTanButton = Button.builder(Component.empty(), b -> sendMenuButton(MahjongTableSettingsMenu.BTN_OPEN_TANYAO))
                .bounds(leftX, leftY, colWidth, h)
                .build();
        addRenderableWidget(openTanButton);
        leftY += gap;

        allowGameplayButton =
                Button.builder(
                                Component.empty(),
                                b -> sendMenuButton(MahjongTableSettingsMenu.BTN_ALLOW_GAMEPLAY))
                        .bounds(leftX, leftY, colWidth, h)
                        .build();
        addRenderableWidget(allowGameplayButton);
        leftY += gap;

        allowCustomTilePackButton =
                Button.builder(
                                Component.empty(),
                                b -> sendMenuButton(MahjongTableSettingsMenu.BTN_ALLOW_CUSTOM_TILE_PACK))
                        .bounds(leftX, leftY, colWidth, h)
                        .build();
        addRenderableWidget(allowCustomTilePackButton);
        leftY += gap;

        allowEditInMatchButton =
                Button.builder(
                                Component.empty(),
                                b -> sendMenuButton(MahjongTableSettingsMenu.BTN_ALLOW_EDIT_IN_MATCH))
                        .bounds(leftX, leftY, colWidth, h)
                        .build();
        addRenderableWidget(allowEditInMatchButton);
        leftY += gap;

        normalizeFovInRadiusButton =
                Button.builder(
                                Component.empty(),
                                b -> sendMenuButton(MahjongTableSettingsMenu.BTN_NORMALIZE_FOV_IN_RADIUS))
                        .bounds(leftX, leftY, colWidth, h)
                        .build();
        addRenderableWidget(normalizeFovInRadiusButton);
        leftY += gap;

        passiveBotsButton =
                Button.builder(
                                Component.empty(),
                                b -> sendMenuButton(MahjongTableSettingsMenu.BTN_PASSIVE_BOTS))
                        .bounds(leftX, leftY, colWidth, h)
                        .build();
        addRenderableWidget(passiveBotsButton);
        leftY += gap;

        startingPointsButton =
                Button.builder(Component.empty(), b -> sendMenuButton(MahjongTableSettingsMenu.BTN_STARTING_POINTS))
                        .bounds(leftX, leftY, colWidth, h)
                        .build();
        addRenderableWidget(startingPointsButton);
        leftY += gap;

        endMatchButton =
                Button.builder(
                                Component.translatable("riichi_mahjong_forge.screen.table_settings.end_match"),
                                b -> sendMenuButton(MahjongTableSettingsMenu.BTN_END_MATCH))
                        .bounds(leftX, leftY, colWidth, h)
                        .build();
        addRenderableWidget(endMatchButton);
        leftY += gap;

        resetButton = Button.builder(Component.translatable("riichi_mahjong_forge.screen.table_settings.reset_lobby"), b -> sendMenuButton(MahjongTableSettingsMenu.BTN_RESET_LOBBY))
                .bounds(leftX, leftY, colWidth, h)
                .build();
        addRenderableWidget(resetButton);

        fillTableTilesButton =
                Button.builder(
                                Component.translatable("riichi_mahjong_forge.screen.table_settings.fill_table_tiles"),
                                b -> sendMenuButton(MahjongTableSettingsMenu.BTN_FILL_TABLE_TILES))
                        .bounds(rightX, rightY, colWidth, h)
                        .build();
        addRenderableWidget(fillTableTilesButton);
        rightY += gap;

        fillNonTableTilesButton =
                Button.builder(
                                Component.translatable("riichi_mahjong_forge.screen.table_settings.fill_non_table_tiles"),
                                b -> sendMenuButton(MahjongTableSettingsMenu.BTN_FILL_NON_TABLE_TILES))
                        .bounds(rightX, rightY, colWidth, h)
                        .build();
        addRenderableWidget(fillNonTableTilesButton);
        rightY += gap;

        if (minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild) {
            fill53TilesButton =
                    Button.builder(
                                    Component.translatable("riichi_mahjong_forge.screen.table_settings.fill_table_tiles_53"),
                                    b -> sendMenuButton(MahjongTableSettingsMenu.BTN_FILL_TABLE_TILES_53))
                            .bounds(rightX, rightY, colWidth, h)
                            .build();
            addRenderableWidget(fill53TilesButton);
            rightY += gap;
        }
    }

    private void sendMenuButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private MahjongMatchDefinition viewRules() {
        if (minecraft != null
                && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.getTablePos()) instanceof MahjongTableBlockEntity t) {
            return t.getRules();
        }
        return MahjongMatchDefinition.DEFAULT;
    }

    private boolean viewAllowEditInMatch() {
        if (minecraft != null
                && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.getTablePos()) instanceof MahjongTableBlockEntity t) {
            return t.allowInventoryEditWhileInMatch();
        }
        return false;
    }

    private boolean viewAllowGameplay() {
        if (minecraft != null
                && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.getTablePos()) instanceof MahjongTableBlockEntity t) {
            return t.allowGameplay();
        }
        return true;
    }

    private boolean viewNormalizeFovInRadius() {
        if (minecraft != null
                && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.getTablePos()) instanceof MahjongTableBlockEntity t) {
            return t.normalizeFovInRadius();
        }
        return false;
    }

    private boolean viewAllowCustomTilePack() {
        if (minecraft != null
                && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.getTablePos()) instanceof MahjongTableBlockEntity t) {
            return t.allowCustomTilePack();
        }
        return true;
    }

    private boolean viewPassiveBots() {
        if (minecraft != null
                && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.getTablePos()) instanceof MahjongTableBlockEntity t) {
            return t.passiveBots();
        }
        return false;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        MahjongMatchDefinition r = viewRules();
        matchLengthButton.setMessage(Component.translatable(
                r.hanchan()
                        ? "riichi_mahjong_forge.screen.table_settings.mode_hanchan"
                        : "riichi_mahjong_forge.screen.table_settings.mode_east"));
        akaButton.setMessage(labelToggle(
                "riichi_mahjong_forge.screen.table_settings.aka", r.akaDora()));
        openTanButton.setMessage(labelToggle(
                "riichi_mahjong_forge.screen.table_settings.open_tanyao", r.openTanyao()));
        allowGameplayButton.setMessage(labelToggle(
                "riichi_mahjong_forge.screen.table_settings.allow_gameplay", viewAllowGameplay()));
        allowCustomTilePackButton.setMessage(labelToggle(
                "riichi_mahjong_forge.screen.table_settings.allow_custom_tile_pack", viewAllowCustomTilePack()));
        allowEditInMatchButton.setMessage(labelToggle(
                "riichi_mahjong_forge.screen.table_settings.allow_edit_in_match", viewAllowEditInMatch()));
        normalizeFovInRadiusButton.setMessage(labelToggle(
                "riichi_mahjong_forge.screen.table_settings.normalize_fov_in_radius", viewNormalizeFovInRadius()));
        passiveBotsButton.setMessage(labelToggle(
                "riichi_mahjong_forge.screen.table_settings.passive_bots", viewPassiveBots()));
        startingPointsButton.setMessage(Component.translatable(
                "riichi_mahjong_forge.screen.table_settings.starting_points", r.startingPoints()));
    }

    private static Component labelToggle(String baseKey, boolean on) {
        return Component.translatable(
                "riichi_mahjong_forge.screen.table_settings.toggle_fmt",
                Component.translatable(baseKey),
                Component.translatable(
                        on
                                ? "riichi_mahjong_forge.screen.table_settings.on"
                                : "riichi_mahjong_forge.screen.table_settings.off"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int i = leftPos;
        int j = topPos;
        graphics.fill(i, j, i + imageWidth, j + imageHeight, 0xC0101010);
        graphics.fill(i + 1, j + 1, i + imageWidth - 1, j + imageHeight - 1, 0xFF303030);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
    }
}
