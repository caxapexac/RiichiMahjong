package com.riichimahjongforge.menu;

import com.riichimahjongforge.MahjongTableBlockEntity;
import com.riichimahjongforge.RiichiMahjongForgeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

public class MahjongTableInventoryMenu extends AbstractContainerMenu {
    public static final int BTN_OPEN_TABLE_STORAGE = 0;
    public static final int BTN_OPEN_HANDS_STORAGE = 1;
    public static final int BTN_OPEN_WALL_STORAGE = 2;
    public static final int BTN_OPEN_DEAD_WALL_STORAGE = 3;
    public static final int BTN_OPEN_OPEN_MELDS_STORAGE = 5;
    public static final int BTN_OPEN_DISCARDS_STORAGE = 6;

    public static final int GRID_COLS = 17;
    public static final int GRID_ROWS = 8;
    public static final int GRID_CAPACITY = GRID_COLS * GRID_ROWS;
    public static final int GRID_TOP = 42;
    public static final int SLOT_SIZE = 18;
    public static final int GRID_LEFT = 8;
    private static final int SEAT_BLOCK_GAP = 14;
    private static final int SEAT_BLOCK_ROW_GAP = 18;
    private static final int SEAT_BLOCK_TITLE_GAP = 12;
    private static final int HAND_BLOCK_COLS = 7;
    private static final int HAND_BLOCK_ROWS = 2;
    private static final int MELD_BLOCK_COLS = 8;
    private static final int MELD_BLOCK_ROWS = 2;
    private static final int PLAYER_INV_COLS = 9;
    private static final int PLAYER_INV_ROWS = 3;
    private static final int PLAYER_INV_INDEX_OFFSET = 9;
    public static final int PLAYER_INV_TOP_GAP = 14;
    public static final int HOTBAR_Y_OFFSET = 58;
    public static final int HOTBAR_HEIGHT = 18;
    private static final String INVENTORY_TITLE_KEY = "riichi_mahjong_forge.screen.table_inventory.title";

    static {
        if (HAND_BLOCK_COLS * HAND_BLOCK_ROWS != MahjongTableBlockEntity.PLAYER_ZONE_SLOTS_PER_SEAT) {
            throw new IllegalStateException("hand block layout must match PLAYER_ZONE_SLOTS_PER_SEAT");
        }
        if (MELD_BLOCK_COLS * MELD_BLOCK_ROWS != MahjongTableBlockEntity.OPEN_MELD_SLOTS_PER_SEAT) {
            throw new IllegalStateException("meld block layout must match OPEN_MELD_SLOTS_PER_SEAT");
        }
    }

    public enum Section {
        TABLE_TILES(MahjongTableBlockEntity.INV_TILES_IN_TABLE_START, MahjongTableBlockEntity.TILES_IN_TABLE_SLOTS),
        HANDS(MahjongTableBlockEntity.playerZoneSectionStart(), MahjongTableBlockEntity.PLAYER_ZONE_TOTAL_SLOTS),
        OPEN_MELDS(MahjongTableBlockEntity.INV_OPEN_MELD_START, MahjongTableBlockEntity.OPEN_MELD_TOTAL_SLOTS),
        WALL(MahjongTableBlockEntity.INV_WALL_START, MahjongTableBlockEntity.WALL_SLOTS),
        DEAD_WALL(MahjongTableBlockEntity.INV_DEAD_WALL_START, MahjongTableBlockEntity.DEAD_WALL_SLOTS),
        DISCARDS(MahjongTableBlockEntity.discardSectionStart(), MahjongTableBlockEntity.DISCARDS_TOTAL_SLOTS);

        private final int start;
        private final int count;

        Section(int start, int count) {
            this.start = start;
            this.count = count;
        }
    }

    private final BlockPos tablePos;
    private final Container tableInventory;
    private final Section section;
    private final int visibleSlots;

    public MahjongTableInventoryMenu(int containerId, Inventory playerInv, MahjongTableBlockEntity table) {
        this(containerId, playerInv, table.getBlockPos(), table, Section.TABLE_TILES);
    }

    public MahjongTableInventoryMenu(int containerId, Inventory playerInv, MahjongTableBlockEntity table, Section section) {
        this(containerId, playerInv, table.getBlockPos(), table, section);
    }

    public MahjongTableInventoryMenu(int containerId, Inventory playerInv, BlockPos pos, Section section) {
        this(containerId, playerInv, pos, new SimpleContainer(MahjongTableBlockEntity.INVENTORY_SIZE), section);
    }

    private MahjongTableInventoryMenu(
            int containerId, Inventory playerInv, BlockPos pos, Container tableInventory, Section section) {
        super(RiichiMahjongForgeMod.MAHJONG_TABLE_INVENTORY_MENU.get(), containerId);
        this.tablePos = pos;
        this.tableInventory = tableInventory;
        this.section = section;
        this.visibleSlots = Math.min(section.count, GRID_CAPACITY);
        buildTableSlots();
        addPlayerInventory(playerInv);
    }

    public static MahjongTableInventoryMenu fromNetwork(int windowId, Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int ord = buf.readVarInt();
        Section[] sections = Section.values();
        Section section = ord >= 0 && ord < sections.length ? sections[ord] : Section.TABLE_TILES;
        return new MahjongTableInventoryMenu(windowId, playerInv, pos, section);
    }

    private void buildTableSlots() {
        SectionContainer sectionContainer = new SectionContainer();
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int local = col + row * GRID_COLS;
                int[] xy = slotPositionForLocal(local);
                Slot slot = new Slot(
                        sectionContainer,
                        local,
                        xy[0],
                        xy[1]) {
                    @Override
                    public boolean isActive() {
                        return this.index < visibleSlots;
                    }
                };
                addSlot(slot);
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

    public static int[] seatBlockOrigin(Section section, int seat) {
        if (seat < 0 || seat >= MahjongTableBlockEntity.SEAT_COUNT) {
            return new int[] {GRID_LEFT, GRID_TOP};
        }
        int cols =
                section == Section.HANDS ? HAND_BLOCK_COLS : section == Section.OPEN_MELDS ? MELD_BLOCK_COLS : GRID_COLS;
        int rows =
                section == Section.HANDS ? HAND_BLOCK_ROWS : section == Section.OPEN_MELDS ? MELD_BLOCK_ROWS : GRID_ROWS;
        int blockW = cols * SLOT_SIZE;
        int blockH = rows * SLOT_SIZE;
        int col2 = seat % 2;
        int row2 = seat / 2;
        int x = GRID_LEFT + col2 * (blockW + SEAT_BLOCK_GAP);
        int y = GRID_TOP + SEAT_BLOCK_TITLE_GAP + row2 * (blockH + SEAT_BLOCK_ROW_GAP);
        return new int[] {x, y};
    }

    private int[] slotPositionForLocal(int local) {
        if (section == Section.HANDS && local < MahjongTableBlockEntity.PLAYER_ZONE_TOTAL_SLOTS) {
            int seat = local / MahjongTableBlockEntity.PLAYER_ZONE_SLOTS_PER_SEAT;
            int within = local % MahjongTableBlockEntity.PLAYER_ZONE_SLOTS_PER_SEAT;
            int[] o = seatBlockOrigin(section, seat);
            int x = o[0] + (within % HAND_BLOCK_COLS) * SLOT_SIZE;
            int y = o[1] + (within / HAND_BLOCK_COLS) * SLOT_SIZE;
            return new int[] {x, y};
        }
        if (section == Section.OPEN_MELDS && local < MahjongTableBlockEntity.OPEN_MELD_TOTAL_SLOTS) {
            int seat = local / MahjongTableBlockEntity.OPEN_MELD_SLOTS_PER_SEAT;
            int within = local % MahjongTableBlockEntity.OPEN_MELD_SLOTS_PER_SEAT;
            int[] o = seatBlockOrigin(section, seat);
            int x = o[0] + (within % MELD_BLOCK_COLS) * SLOT_SIZE;
            int y = o[1] + (within / MELD_BLOCK_COLS) * SLOT_SIZE;
            return new int[] {x, y};
        }
        int col = local % GRID_COLS;
        int row = local / GRID_COLS;
        return new int[] {GRID_LEFT + col * SLOT_SIZE, GRID_TOP + row * SLOT_SIZE};
    }


    public int getVisibleSlots() {
        return visibleSlots;
    }

    public Section getSection() {
        return section;
    }

    public BlockPos getTablePos() {
        return tablePos;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        Section selected = switch (id) {
            case BTN_OPEN_TABLE_STORAGE -> Section.TABLE_TILES;
            case BTN_OPEN_HANDS_STORAGE -> Section.HANDS;
            case BTN_OPEN_WALL_STORAGE -> Section.WALL;
            case BTN_OPEN_DEAD_WALL_STORAGE -> Section.DEAD_WALL;
            case BTN_OPEN_OPEN_MELDS_STORAGE -> Section.OPEN_MELDS;
            case BTN_OPEN_DISCARDS_STORAGE -> Section.DISCARDS;
            default -> null;
        };
        if (selected == null || selected == section || !(player instanceof ServerPlayer sp)) {
            return false;
        }
        BlockEntity be = player.level().getBlockEntity(tablePos);
        if (!(be instanceof MahjongTableBlockEntity table)) {
            return false;
        }
        NetworkHooks.openScreen(
                sp,
                new SimpleMenuProvider(
                        (windowId, inv, p) -> new MahjongTableInventoryMenu(windowId, inv, table, selected),
                        Component.translatable(INVENTORY_TITLE_KEY)),
                buf -> {
                    buf.writeBlockPos(tablePos);
                    buf.writeVarInt(selected.ordinal());
                });
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
        int tableSlotCount = visibleSlots;
        if (index < tableSlotCount) {
            if (!moveItemStackTo(sourceStack, tableSlotCount, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(sourceStack, 0, tableSlotCount, false)) {
            return ItemStack.EMPTY;
        }
        if (sourceStack.isEmpty()) {
            source.set(ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        return result;
    }

    private final class SectionContainer implements Container {
        @Override
        public int getContainerSize() {
            return GRID_CAPACITY;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < visibleSlots; i++) {
                if (!getItem(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            int absolute = toAbsoluteSlot(slot);
            return absolute < 0 ? ItemStack.EMPTY : tableInventory.getItem(absolute);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            int absolute = toAbsoluteSlot(slot);
            return absolute < 0 ? ItemStack.EMPTY : tableInventory.removeItem(absolute, amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            int absolute = toAbsoluteSlot(slot);
            return absolute < 0 ? ItemStack.EMPTY : tableInventory.removeItemNoUpdate(absolute);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            int absolute = toAbsoluteSlot(slot);
            if (absolute >= 0) {
                tableInventory.setItem(absolute, stack);
            }
        }

        @Override
        public void setChanged() {
            tableInventory.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return MahjongTableInventoryMenu.this.stillValid(player);
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < visibleSlots; i++) {
                setItem(i, ItemStack.EMPTY);
            }
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            int absolute = toAbsoluteSlot(slot);
            if (section == Section.OPEN_MELDS) {
                return false;
            }
            return absolute >= 0 && tableInventory.canPlaceItem(absolute, stack);
        }

        @Override
        public boolean canTakeItem(Container target, int slot, ItemStack stack) {
            int absolute = toAbsoluteSlot(slot);
            if (section == Section.OPEN_MELDS) {
                return false;
            }
            return absolute >= 0 && tableInventory.canTakeItem(tableInventory, absolute, stack);
        }

        private int toAbsoluteSlot(int localSlot) {
            if (localSlot < 0 || localSlot >= visibleSlots) {
                return -1;
            }
            int absolute;
            if (section == Section.HANDS) {
                int seat = localSlot / MahjongTableBlockEntity.PLAYER_ZONE_SLOTS_PER_SEAT;
                int slotInSeat = localSlot % MahjongTableBlockEntity.PLAYER_ZONE_SLOTS_PER_SEAT;
                absolute = MahjongTableBlockEntity.playerZoneAbsolute(seat, slotInSeat);
            } else if (section == Section.DISCARDS) {
                int seat = localSlot / MahjongTableBlockEntity.DISCARDS_SLOTS_PER_SEAT;
                int slotInSeat = localSlot % MahjongTableBlockEntity.DISCARDS_SLOTS_PER_SEAT;
                absolute = MahjongTableBlockEntity.discardAbsolute(seat, slotInSeat);
            } else {
                absolute = section.start + localSlot;
                int sectionEnd = section.start + section.count;
                if (absolute < section.start || absolute >= sectionEnd) {
                    return -1;
                }
            }
            return absolute >= 0 && absolute < tableInventory.getContainerSize() ? absolute : -1;
        }
    }
}
