package com.riichimahjong.mahjongtable;

import com.riichimahjong.registry.ModMenus;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Settings menu for the mahjong table — lets the player choose a {@link RuleSetPreset}.
 * No table slots; player inventory is shown so the screen feels consistent with the
 * inventory menu. Buttons are dispatched via {@link #clickMenuButton(Player, int)}.
 */
public class MahjongTableSettingsMenu extends AbstractContainerMenu {

    /** Button ids 0..N-1 select the corresponding {@link RuleSetPreset#values()} entry. */
    public static final int BTN_PRESET_BASE = 0;
    /** Returns the player to the inventory menu. */
    public static final int BTN_BACK = 100;

    public static final int SLOT_SIZE = 18;
    public static final int GRID_LEFT = 8;
    public static final int CONTENT_TOP = 24;
    /** Height reserved for the preset buttons row before player inventory starts. */
    public static final int CONTENT_HEIGHT = 130;
    public static final int PLAYER_INV_TOP_GAP = 14;
    public static final int HOTBAR_Y_OFFSET = 58;
    public static final int HOTBAR_HEIGHT = 18;

    private static final int PLAYER_INV_COLS = 9;
    private static final int PLAYER_INV_ROWS = 3;
    private static final int PLAYER_INV_INDEX_OFFSET = 9;

    private final BlockPos tablePos;

    public MahjongTableSettingsMenu(int containerId, Inventory playerInv, MahjongTableBlockEntity table) {
        this(containerId, playerInv, table.getBlockPos());
    }

    public MahjongTableSettingsMenu(int containerId, Inventory playerInv, BlockPos pos) {
        super(ModMenus.MAHJONG_TABLE_SETTINGS_MENU.get(), containerId);
        this.tablePos = pos;
        addPlayerInventory(playerInv);
    }

    public static MahjongTableSettingsMenu fromNetwork(int windowId, Inventory playerInv, FriendlyByteBuf buf) {
        return new MahjongTableSettingsMenu(windowId, playerInv, buf.readBlockPos());
    }

    private void addPlayerInventory(Inventory playerInv) {
        int yBase = CONTENT_TOP + CONTENT_HEIGHT + PLAYER_INV_TOP_GAP;
        for (int row = 0; row < PLAYER_INV_ROWS; row++) {
            for (int col = 0; col < PLAYER_INV_COLS; col++) {
                addSlot(new Slot(
                        playerInv,
                        col + row * PLAYER_INV_COLS + PLAYER_INV_INDEX_OFFSET,
                        GRID_LEFT + col * SLOT_SIZE,
                        yBase + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < PLAYER_INV_COLS; col++) {
            addSlot(new Slot(playerInv, col, GRID_LEFT + col * SLOT_SIZE, yBase + HOTBAR_Y_OFFSET));
        }
    }

    public BlockPos getTablePos() {
        return tablePos;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        BlockEntity be = player.level().getBlockEntity(tablePos);
        if (!(be instanceof MahjongTableBlockEntity table)) return false;

        if (id == BTN_BACK) {
            MenuRegistry.openExtendedMenu(serverPlayer, table, buf -> buf.writeBlockPos(tablePos));
            return true;
        }
        int presetIdx = id - BTN_PRESET_BASE;
        RuleSetPreset[] all = RuleSetPreset.values();
        if (presetIdx < 0 || presetIdx >= all.length) return false;
        table.selectPreset(all[presetIdx]);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        BlockEntity be = player.level().getBlockEntity(tablePos);
        return be instanceof MahjongTableBlockEntity table && table.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // No table slots — quick-move is a no-op.
        return ItemStack.EMPTY;
    }
}
