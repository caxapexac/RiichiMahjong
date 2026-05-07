package com.riichimahjongforge.mahjongtable;

import com.riichimahjongforge.RiichiMahjongForgeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkHooks;

/**
 * Chest-style menu over the table's flat 136-slot inventory (17 cols × 8 rows).
 * No section buttons — there is only one section.
 */
public class MahjongTableMenu extends AbstractContainerMenu {

    /** Sent by the Settings button — server reopens this menu as the settings screen. */
    public static final int BTN_OPEN_SETTINGS = 0;

    public static final int GRID_COLS = 17;
    public static final int GRID_ROWS = 8;
    public static final int GRID_CAPACITY = GRID_COLS * GRID_ROWS;
    public static final int SLOT_SIZE = 18;
    public static final int GRID_LEFT = 8;
    public static final int GRID_TOP = 24;
    public static final int PLAYER_INV_TOP_GAP = 14;
    public static final int HOTBAR_Y_OFFSET = 58;
    public static final int HOTBAR_HEIGHT = 18;

    private static final int PLAYER_INV_COLS = 9;
    private static final int PLAYER_INV_ROWS = 3;
    private static final int PLAYER_INV_INDEX_OFFSET = 9;

    static {
        if (GRID_CAPACITY != MahjongTableBlockEntity.INVENTORY_SIZE) {
            throw new IllegalStateException("grid capacity must match inventory size");
        }
    }

    private final BlockPos tablePos;
    private final Container tableInventory;

    public MahjongTableMenu(int containerId, Inventory playerInv, MahjongTableBlockEntity table) {
        this(containerId, playerInv, table.getBlockPos(), table);
    }

    public MahjongTableMenu(int containerId, Inventory playerInv, BlockPos pos) {
        this(containerId, playerInv, pos, new SimpleContainer(MahjongTableBlockEntity.INVENTORY_SIZE));
    }

    private MahjongTableMenu(int containerId, Inventory playerInv, BlockPos pos, Container tableInventory) {
        super(RiichiMahjongForgeMod.MAHJONG_TABLE_MENU.get(), containerId);
        this.tablePos = pos;
        this.tableInventory = tableInventory;
        addTableSlots();
        addPlayerInventory(playerInv);
    }

    public static MahjongTableMenu fromNetwork(int windowId, Inventory playerInv, FriendlyByteBuf buf) {
        return new MahjongTableMenu(windowId, playerInv, buf.readBlockPos());
    }

    private void addTableSlots() {
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int slot = col + row * GRID_COLS;
                addSlot(new Slot(
                        tableInventory,
                        slot,
                        GRID_LEFT + col * SLOT_SIZE,
                        GRID_TOP + row * SLOT_SIZE));
            }
        }
    }

    private void addPlayerInventory(Inventory playerInv) {
        int yBase = GRID_TOP + GRID_ROWS * SLOT_SIZE + PLAYER_INV_TOP_GAP;
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
        if (id != BTN_OPEN_SETTINGS) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        BlockEntity be = player.level().getBlockEntity(tablePos);
        if (!(be instanceof MahjongTableBlockEntity table)) return false;
        NetworkHooks.openScreen(
                serverPlayer,
                new SimpleMenuProvider(
                        (windowId, inv, p) -> new MahjongTableSettingsMenu(windowId, inv, table),
                        Component.translatable("riichi_mahjong_forge.screen.table.settings.title")),
                tablePos);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        if (tableInventory instanceof MahjongTableBlockEntity table) {
            return table.stillValid(player);
        }
        BlockEntity be = player.level().getBlockEntity(tablePos);
        return be instanceof MahjongTableBlockEntity table && table.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot source = slots.get(index);
        if (source == null || !source.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack sourceStack = source.getItem();
        ItemStack result = sourceStack.copy();
        if (index < GRID_CAPACITY) {
            if (!moveItemStackTo(sourceStack, GRID_CAPACITY, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(sourceStack, 0, GRID_CAPACITY, false)) {
            return ItemStack.EMPTY;
        }
        if (sourceStack.isEmpty()) {
            source.set(ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        return result;
    }
}
