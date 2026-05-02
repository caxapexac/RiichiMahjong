package com.riichimahjongforge.menu;

import com.riichimahjongforge.nbt.MahjongMatchDefinitionNbt;
import com.riichimahjongforge.MahjongTableBlockEntity;
import com.riichimahjongforge.RiichiMahjongForgeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class MahjongTableSettingsMenu extends AbstractContainerMenu {

    public static final int BTN_HANCHAN = 0;
    public static final int BTN_RESET_LOBBY = 1;
    public static final int BTN_AKA = 2;
    public static final int BTN_OPEN_TANYAO = 3;
    public static final int BTN_ALLOW_GAMEPLAY = 4;
    public static final int BTN_STARTING_POINTS = 5;
    public static final int BTN_ALLOW_EDIT_IN_MATCH = 8;
    public static final int BTN_FILL_TABLE_TILES = 9;
    public static final int BTN_FILL_NON_TABLE_TILES = 10;
    public static final int BTN_FILL_TABLE_TILES_53 = 11;
    public static final int BTN_END_MATCH = 12;
    public static final int BTN_NORMALIZE_FOV_IN_RADIUS = 14;
    public static final int BTN_PASSIVE_BOTS = 15;
    public static final int BTN_ALLOW_CUSTOM_TILE_PACK = 17;

    private static final int[] STARTING_POINT_CYCLE = {25_000, 30_000, 36_000};

    private final BlockPos tablePos;

    /** Server-side */
    public MahjongTableSettingsMenu(int containerId, Inventory inv, MahjongTableBlockEntity table) {
        super(RiichiMahjongForgeMod.MAHJONG_TABLE_SETTINGS.get(), containerId);
        this.tablePos = table.getBlockPos();
    }

    /** Client-side */
    public MahjongTableSettingsMenu(int containerId, Inventory inv, BlockPos tablePos) {
        super(RiichiMahjongForgeMod.MAHJONG_TABLE_SETTINGS.get(), containerId);
        this.tablePos = tablePos;
    }

    public static MahjongTableSettingsMenu fromNetwork(int windowId, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        MahjongMatchDefinitionNbt.read(buf);
        return new MahjongTableSettingsMenu(windowId, inv, pos);
    }

    public BlockPos getTablePos() {
        return tablePos;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(tablePos) instanceof MahjongTableBlockEntity
                && player.distanceToSqr(
                        tablePos.getX() + 0.5, tablePos.getY() + 0.5, tablePos.getZ() + 0.5) <= 8.0 * 8.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        BlockEntity be = player.level().getBlockEntity(tablePos);
        if (!(be instanceof MahjongTableBlockEntity table)) {
            return false;
        }
        switch (id) {
            case BTN_HANCHAN -> table.updateRules(r -> r.withHanchan(!r.hanchan()));
            case BTN_RESET_LOBBY -> {
                if (player instanceof ServerPlayer sp) {
                    table.resetAll(sp);
                }
            }
            case BTN_AKA -> table.updateRules(r -> r.withAkaDora(!r.akaDora()));
            case BTN_OPEN_TANYAO -> table.updateRules(r -> r.withOpenTanyao(!r.openTanyao()));
            case BTN_ALLOW_GAMEPLAY -> table.setAllowGameplay(!table.allowGameplay());
            case BTN_ALLOW_CUSTOM_TILE_PACK -> table.setAllowCustomTilePack(!table.allowCustomTilePack());
            case BTN_STARTING_POINTS -> cycleStartingPoints(table);
            case BTN_ALLOW_EDIT_IN_MATCH -> table.setAllowInventoryEditWhileInMatch(!table.allowInventoryEditWhileInMatch());
            case BTN_NORMALIZE_FOV_IN_RADIUS -> table.setNormalizeFovInRadius(!table.normalizeFovInRadius());
            case BTN_PASSIVE_BOTS -> table.setPassiveBots(!table.passiveBots());
            case BTN_FILL_TABLE_TILES -> {
                if (player instanceof ServerPlayer sp) {
                    table.fillTableWithFullTileSetCreativeOnly(sp);
                }
            }
            case BTN_FILL_NON_TABLE_TILES -> {
                if (player instanceof ServerPlayer sp) {
                    table.fillNonTableSlotsWithRandomTilesCreativeOnly(sp);
                }
            }
            case BTN_FILL_TABLE_TILES_53 -> {
                if (player instanceof ServerPlayer sp) {
                    table.fillTableWith53RandomTilesCreativeOnly(sp);
                }
            }
            case BTN_END_MATCH -> {
                if (player instanceof ServerPlayer sp) {
                    table.endMatchToWaitingKeepTiles(sp);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void cycleStartingPoints(MahjongTableBlockEntity table) {
        int cur = table.getRules().startingPoints();
        for (int i = 0; i < STARTING_POINT_CYCLE.length; i++) {
            if (STARTING_POINT_CYCLE[i] == cur) {
                int next = STARTING_POINT_CYCLE[(i + 1) % STARTING_POINT_CYCLE.length];
                table.updateRules(r -> r.withStartingPoints(next));
                return;
            }
        }
        table.updateRules(r -> r.withStartingPoints(STARTING_POINT_CYCLE[0]));
    }
}
